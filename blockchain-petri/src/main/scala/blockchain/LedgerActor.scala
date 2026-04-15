package blockchain

import scala.collection.mutable
import scala.concurrent.Promise

sealed trait LedgerMessage
object LedgerMessage {
  final case class GetSnapshot(replyTo: Promise[LedgerSnapshot]) extends LedgerMessage
  final case class GetLastBlock(replyTo: Promise[Block]) extends LedgerMessage
  final case class AppendBlock(
      block: Block,
      walletDirectory: ActorRef[WalletDirectoryMessage],
      mempool: ActorRef[MempoolMessage],
      replyTo: Promise[Either[String, Unit]]
  ) extends LedgerMessage
  final case class TryAppendBlock(
      block: Block,
      walletDirectory: ActorRef[WalletDirectoryMessage],
      mempool: ActorRef[MempoolMessage],
      replyTo: Promise[Either[String, Unit]]
  ) extends LedgerMessage
}

object LedgerActor {
  def genesisBlock(): Block = {
    val genesis = Block(
      index = 0,
      previousHash = "0",
      transactions = Vector.empty,
      validator = "GENESIS"
    )
    genesis.hash = genesis.computeHash
    genesis
  }
}

final class LedgerActor(
    initialChain: Vector[Block],
    difficulty: Int,
    miningReward: BigDecimal
) extends SimpleActor[LedgerMessage]("ledger") {
  import LedgerMessage._
  import MempoolMessage._
  import WalletDirectoryMessage._

  private var chain: Vector[Block] =
    if (initialChain.nonEmpty) initialChain else Vector(LedgerActor.genesisBlock())

  override protected def receive(message: LedgerMessage): Unit = message match {
    case GetSnapshot(replyTo) =>
      replyTo.success(LedgerSnapshot(difficulty, miningReward, chain))

    case GetLastBlock(replyTo) =>
      replyTo.success(chain.last)

    case AppendBlock(block, walletDirectory, mempool, replyTo) =>
      val result = validateBlock(block, walletDirectory, mempool).map { _ =>
        walletDirectory.ask(ApplyTransactions(block.transactions, _))
        mempool.ask(RemoveConfirmedTransactions(block.transactions.filterNot(_.from == Transaction.SystemAddress), _))
        chain = chain :+ block
      }
      replyTo.success(result)

    case TryAppendBlock(block, walletDirectory, mempool, replyTo) =>
      this.receive(AppendBlock(block, walletDirectory, mempool, replyTo))
  }

  private def validateBlock(
      block: Block,
      walletDirectory: ActorRef[WalletDirectoryMessage],
      mempool: ActorRef[MempoolMessage]
  ): Either[String, Unit] = {
    val prefix = "0" * difficulty

    if (block.index != chain.size) {
      return Left("Index de bloc incorrect.")
    }
    if (block.previousHash != chain.last.hash) {
      return Left("Le previousHash ne correspond pas au dernier bloc.")
    }
    if (block.hash != block.computeHash) {
      return Left("Le hash du bloc est incohérent.")
    }
    if (!block.hash.startsWith(prefix)) {
      return Left("Le hash du bloc ne respecte pas la difficulté demandée.")
    }
    if (!walletDirectory.ask(IsValidator(block.validator, _))) {
      return Left(s"Le wallet ${block.validator} n'est pas un validateur autorisé.")
    }
    if (block.transactions.isEmpty) {
      return Left("Un bloc ne peut pas être vide.")
    }

    val rewardTxs = block.transactions.filter(_.from == Transaction.SystemAddress)
    val normalTxs = block.transactions.filterNot(_.from == Transaction.SystemAddress)

    if (rewardTxs.size != 1) {
      return Left("Le bloc doit contenir exactement une reward de minage.")
    }
    if (!isValidRewardTransaction(rewardTxs.head, block.validator)) {
      return Left("Reward de minage invalide.")
    }
    if (normalTxs.isEmpty) {
      return Left("Le bloc doit contenir au moins une transaction normale.")
    }
    if (!mempool.ask(ContainsAll(normalTxs, _))) {
      return Left("Certaines transactions du bloc ne sont plus présentes dans la mempool.")
    }

    val walletStates = walletDirectory.ask(GetAllWallets)
    val stateMap = walletStates.map(wallet => wallet.address -> wallet).toMap

    normalTxs.foreach { tx =>
      if (!stateMap.contains(tx.from)) return Left(s"Wallet source inconnu : ${tx.from}")
      if (!stateMap.contains(tx.to)) return Left(s"Wallet destination inconnu : ${tx.to}")
      if (tx.amount <= 0) return Left("Montant de transaction invalide.")
      if (tx.fees < 0) return Left("Frais de transaction invalides.")

      val sender = stateMap(tx.from)
      if (sender.publicKey != tx.publicKey) {
        return Left(s"Clé publique incohérente pour la transaction ${tx.from} -> ${tx.to}")
      }
      val signatureValid =
        if (tx.publicKey.nonEmpty) CryptoUtils.verify(tx.payload, tx.signature, tx.publicKey)
        else CryptoUtils.sha256(tx.legacyPayload + sender.secret) == tx.signature
      if (!signatureValid) {
        return Left(s"Signature invalide pour la transaction ${tx.from} -> ${tx.to}")
      }
    }

    val simulatedBalances = mutable.Map.empty[String, BigDecimal] ++ stateMap.view.mapValues(_.balance).toMap

    normalTxs.foreach { tx =>
      val current = simulatedBalances.getOrElse(tx.from, BigDecimal(0))
      if (current < tx.totalDebit) {
        return Left(s"Solde insuffisant pendant la simulation pour ${tx.from}")
      }

      simulatedBalances.update(tx.from, current - tx.totalDebit)
      simulatedBalances.update(tx.to, simulatedBalances.getOrElse(tx.to, BigDecimal(0)) + tx.amount)
    }

    Right(())
  }

  private def isValidRewardTransaction(tx: Transaction, validatorAddress: String): Boolean =
    tx.from == Transaction.SystemAddress &&
      tx.to == validatorAddress &&
      tx.amount == miningReward &&
      tx.signature == Transaction.SystemSignature
}

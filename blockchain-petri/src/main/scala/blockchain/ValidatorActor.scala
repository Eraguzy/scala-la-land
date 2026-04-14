package blockchain

import scala.concurrent.Promise
import scala.concurrent.duration._

sealed trait ValidatorMessage
object ValidatorMessage {
  final case class MineOnce(
      logEvery: Long,
      attemptDelayMs: Long,
      replyTo: Promise[Either[String, Block]]
  ) extends ValidatorMessage
}

final class ValidatorActor(
    address: String,
    walletDirectory: ActorRef[WalletDirectoryMessage],
    mempool: ActorRef[MempoolMessage],
    ledger: ActorRef[LedgerMessage]
) extends SimpleActor[ValidatorMessage](s"validator-$address") {
  import LedgerMessage._
  import MempoolMessage._
  import ValidatorMessage._
  import WalletDirectoryMessage._

  override protected def receive(message: ValidatorMessage): Unit = message match {
    case MineOnce(logEvery, attemptDelayMs, replyTo) =>
      if (!walletDirectory.ask(IsValidator(address, _))) {
        replyTo.success(Left(s"$address n'est pas un validateur autorisé."))
      } else {
        val pendingTransactions = mempool.ask(GetTransactions)
        if (pendingTransactions.isEmpty) {
          replyTo.success(Left("Mempool vide : rien à miner."))
        } else {
          val ledgerSnapshot = ledger.ask(GetSnapshot)
          val rewardTx = Transaction.reward(address, ledgerSnapshot.miningReward)
          val block = Block(
            index = ledgerSnapshot.nextIndex,
            previousHash = ledgerSnapshot.lastHash,
            transactions = pendingTransactions :+ rewardTx,
            validator = address
          )

          val safeLogEvery = if (logEvery <= 0) 1L else logEvery
          val prefix = "0" * ledgerSnapshot.difficulty

          println(s"Validateur      : $address")
          println(s"Bloc candidat   : #${block.index}")
          println(s"Previous hash   : ${block.previousHash}")
          println(s"Transactions    : ${pendingTransactions.size} + reward")
          println(s"Difficulté      : ${ledgerSnapshot.difficulty}")
          println(s"Préfixe attendu : $prefix")
          println()
          println("Début du minage :")

          block.mine(
            ledgerSnapshot.difficulty,
            onAttempt = { (nonce, hash) =>
              if (nonce == 0L || nonce % safeLogEvery == 0L || hash.startsWith(prefix)) {
                println(s"nonce=$nonce hash=$hash")
                if (attemptDelayMs > 0L) {
                  Thread.sleep(attemptDelayMs)
                }
              }
            }
          )

          println()
          println(s"Hash valide trouvé : ${block.hash}")
          println(s"Nonce gagnant      : ${block.nonce}")
          println("Tentative d'ajout au ledger...")

          val commitResult = ledger.ask(
            TryAppendBlock(block, walletDirectory, mempool, _),
            timeout = 30.minutes
          )

          replyTo.success(commitResult.map(_ => block))
        }
      }
  }
}

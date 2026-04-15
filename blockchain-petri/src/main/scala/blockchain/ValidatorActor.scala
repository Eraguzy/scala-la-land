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

// Mine un bloc candidat a partir des meilleures transactions mempool,
// puis demande au ledger de le commit si valide.
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
        // Un bloc prend au plus 2 transactions prioritaires + la reward.
        val pendingTransactions = mempool.ask(RequestTopTransactions(2, _))
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
          println(s"Transactions    : ${pendingTransactions.size} (top score amount*fees) + reward")
          println(s"Difficulté      : ${ledgerSnapshot.difficulty}")
          println(s"Préfixe attendu : $prefix")
          println()
          println("Début du minage :")

          // La boucle de preuve de travail est deleguee a Block.mine.
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

          // Le ledger reste l'autorite finale d'acceptation du bloc.
          val commitResult = ledger.ask(
            AppendBlock(block, walletDirectory, mempool, _),
            timeout = 30.minutes
          )

          replyTo.success(commitResult.map(_ => block))
        }
      }
  }
}

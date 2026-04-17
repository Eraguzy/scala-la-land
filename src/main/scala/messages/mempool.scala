package messages

import akka.actor.typed.ActorRef // AJOUTE CECI
import objects.SignedTransaction // AJOUTE CECI

object Mempool {
  sealed trait Command
  case class AddTx(tx: SignedTransaction) extends Command
  case class GetTxs(replyTo: ActorRef[Txs]) extends Command
  case class Txs(txs: List[SignedTransaction])
  case class RemoveTxs(txs: List[SignedTransaction]) extends Command
}
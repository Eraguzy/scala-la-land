package messages

import akka.actor.typed.ActorRef
import objects.SignedTransaction
import objects.PendingTx

object Mempool {
  sealed trait Command

  case class AddTx(tx: SignedTransaction, replyTo: ActorRef[Boolean])
      extends Command
  case class GetTxs(replyTo: ActorRef[Txs]) extends Command
  case class Txs(txs: List[PendingTx])
  case class RemoveTxs(txs: List[SignedTransaction]) extends Command
}

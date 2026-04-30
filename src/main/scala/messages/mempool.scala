package messages

import akka.actor.typed.ActorRef
import objects.SignedTransaction
import objects.PendingTx

object Mempool {
  sealed trait Command

  case class AddTx(tx: SignedTransaction, replyTo: ActorRef[Boolean]) extends Command
  case class GetTxs(replyTo: ActorRef[Txs]) extends Command
  case class Txs(txs: List[PendingTx])
  case class RemoveTxs(txs: List[SignedTransaction]) extends Command
  case class ViewPending(replyTo: ActorRef[PendingView]) extends Command

  case class PendingTxInfo(txId: String, from: String, to: String, amount: BigInt, fee: BigInt, timestamp: Long)
  case class PendingView(txs: List[PendingTxInfo])
}

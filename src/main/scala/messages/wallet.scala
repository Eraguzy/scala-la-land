package messages

import akka.actor.typed.ActorRef

object Wallet {
  sealed trait Command
  case class CreateTx(to: String, amount: BigInt, fee: BigInt) extends Command
  case class GetBalance(replyTo: ActorRef[BigInt]) extends Command
}

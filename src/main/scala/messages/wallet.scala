package messages

import akka.actor.typed.ActorRef // AJOUTE CECI

object Wallet {
  sealed trait Command
  case class CreateTx(to: String, amount: BigInt) extends Command
  case class GetBalance(replyTo: ActorRef[BigInt]) extends Command
}
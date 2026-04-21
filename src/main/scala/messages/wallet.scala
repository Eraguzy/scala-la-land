package messages

import akka.actor.typed.ActorRef

object Wallet {
  sealed trait Command
  case class CreateTx(
      to: String,
      amount: BigInt,
      fee: BigInt,
      replyTo: ActorRef[Boolean] // true if tx accepted
  ) extends Command

  case class CreateTxInternal(
      to: String,
      amount: BigInt,
      fee: BigInt,
      balance: BigInt,
      replyTo: ActorRef[Boolean]
  ) extends Wallet.Command
  case class GetBalance(replyTo: ActorRef[BigInt]) extends Command
  case class GetPublicKey(replyTo: ActorRef[String]) extends Command
  case class GetPrivateKey(replyTo: ActorRef[String]) extends Command
}

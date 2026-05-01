package messages

import akka.actor.typed.ActorRef

object Wallet {
  sealed trait Command

  // public entry point — triggers a balance check first, then does the actual work
  case class CreateTx(
      to: String,
      amount: BigInt,
      fee: BigInt,
      replyTo: ActorRef[Boolean]
  ) extends Command

  // internal message sent once the balance is known; we split it in two because
  // querying the DB is async and we can't block inside an actor
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

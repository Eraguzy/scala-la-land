package messages

import akka.actor.typed.ActorRef
import objects.Block // AJOUTE CETTE LIGNE

object DB {
  sealed trait Command
  case class SaveBlock(block: Block, replyTo: ActorRef[Response]) extends Command
  case class GetLastHash(replyTo: ActorRef[String]) extends Command

  sealed trait Response
  case object Success extends Response
  case class Failed(reason: String) extends Response
}
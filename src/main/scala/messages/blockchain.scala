package messages

import akka.actor.typed.ActorRef
import objects.Block

object DB {
  sealed trait Command
  case class AppendBlock(block: Block, replyTo: ActorRef[Response]) extends Command
  case class SaveBlock(block: Block) extends Command
  case class GetLastBlock(replyTo: ActorRef[LastBlockInfo]) extends Command
  case class LastBlockInfo(hash: String, id: Int)

  sealed trait Response
  case object Success extends Response
  case class Failed(reason: String) extends Response
}
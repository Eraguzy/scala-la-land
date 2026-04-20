package messages

import akka.actor.typed.ActorRef
import objects.Block

object DB {
  // --- DB Commands ---
  sealed trait Command
  case class AppendBlock(block: Block, replyTo: ActorRef[Response]) extends Command
  case class SaveBlock(block: Block) extends Command // Fire-and-forget save (no reply)
  case class GetLastBlock(replyTo: ActorRef[LastBlockInfo]) extends Command
  case class GetBalanceAtDate(publicKey: String, targetTimestamp: Long, replyTo: ActorRef[BalanceResponse]) extends Command

  // Internal structures used in commands/responses
  case class LastBlockInfo(hash: String, id: Int)

  // --- DB Responses ---
  sealed trait Response
  case object Success extends Response
  case class Failed(reason: String) extends Response
  case class BalanceResponse(balance: Double)
}
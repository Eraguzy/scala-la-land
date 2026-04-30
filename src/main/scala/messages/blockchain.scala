package messages

import akka.actor.typed.ActorRef
import objects.Block

object DB {
  // --- DB Commands ---
  sealed trait Command
  case class AppendBlock(block: Block, replyTo: ActorRef[Response]) extends Command
  case class SaveBlock(block: Block, replyTo: ActorRef[DB.Response]) extends Command
  case class GetLastBlock(replyTo: ActorRef[LastBlockInfo]) extends Command
  case class GetBalanceAtDate(publicKey: String, targetTimestamp: Long, replyTo: ActorRef[BalanceResponse]) extends Command
  case class GetAllBlocks(replyTo: ActorRef[BlocksData]) extends Command

  // Internal structures used in commands/responses
  case class LastBlockInfo(hash: String, id: Int)

  // --- DB Responses ---
  sealed trait Response
  case object Success extends Response
  case class Failed(reason: String) extends Response
  case class BalanceResponse(balance: Double)

  // --- API view types ---
  case class TxInfo(from: String, to: String, amount: Double)
  case class BlockInfo(id: Long, prevHash: String, timestamp: Long, txs: List[TxInfo])
  case class BlocksData(blocks: List[BlockInfo])
}
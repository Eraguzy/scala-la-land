package bank

import akka.actor.typed.ActorRef

/**
 * Définition des messages pour la communication entre acteurs
 */

// ============ Messages WalletActor ============
sealed trait WalletCommand

case class CreateTransaction(
  receiver: String,
  amount: BigDecimal,
  fees: BigDecimal,
  replyTo: ActorRef[TransactionCreated]
) extends WalletCommand

case class QueryBalance(
  replyTo: ActorRef[BalanceResponse]
) extends WalletCommand

case class TransactionCreated(
  transaction: Option[SignedTransaction],
  error: Option[String]
)

case class BalanceResponse(
  balance: BigDecimal,
  wallet: String
)

// ============ Messages MempoolActor ============
sealed trait MempoolCommand

case class SubmitTransaction(
  signedTransaction: SignedTransaction,
  replyTo: ActorRef[TransactionSubmitResponse]
) extends MempoolCommand

case class RequestTransactions(
  count: Int,
  replyTo: ActorRef[TransactionsResponse]
) extends MempoolCommand

case class DeleteCompletedTransactions(
  transactions: List[SignedTransaction],
  replyTo: ActorRef[DeletionAckowledged]
) extends MempoolCommand

case class TransactionSubmitResponse(
  success: Boolean,
  message: String
)

case class TransactionsResponse(
  transactions: List[SignedTransaction],
  mempool: String
)

case class DeletionAckowledged(
  success: Boolean,
  deletedCount: Int
)

// ============ Messages ValidatorActor ============
sealed trait ValidatorCommand

case class RequestBlockData(
  replyTo: ActorRef[BlockDataResponse]
) extends ValidatorCommand

case class BlockDataResponse(
  lastBlockHash: String,
  lastBlockId: Long
)

case class ProposeNewBlock(
  block: Block,
  replyTo: ActorRef[BlockAppendResponse]
) extends ValidatorCommand

case class BlockAppendResponse(
  success: Boolean,
  blockId: Long,
  message: String
)

// ============ Messages DBBlockchain ============
sealed trait DBCommand

case class GetLastBlock(
  replyTo: ActorRef[LastBlockResponse]
) extends DBCommand

case class LastBlockResponse(
  block: Option[Block],
  blockHash: String,
  blockId: Long
)

case class AppendBlock(
  block: Block,
  replyTo: ActorRef[BlockAppendResponse]
) extends DBCommand

case class GetBalance(
  publicKey: String,
  replyTo: ActorRef[BalanceResponse]
) extends DBCommand

case class GetAllBlocks(
  replyTo: ActorRef[AllBlocksResponse]
) extends DBCommand

case class AllBlocksResponse(
  blocks: List[Block]
)

// ============ Messages internes ============
// Pour déclencher le mining
case object StartMining extends ValidatorCommand

// Messages internes pour gérer les réponses asynchrones
case class ProcessBlockData(
  data: BlockDataResponse,
  replyTo: ActorRef[BlockDataResponse]
) extends ValidatorCommand

case class BlockAppendResult(
  response: BlockAppendResponse,
  replyTo: ActorRef[BlockAppendResponse]
) extends ValidatorCommand

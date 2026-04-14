package blockchain

import scala.concurrent.Promise

sealed trait MempoolMessage
object MempoolMessage {
  final case class GetTransactions(replyTo: Promise[Vector[Transaction]]) extends MempoolMessage
  final case class GetPendingOutgoing(address: String, replyTo: Promise[BigDecimal]) extends MempoolMessage
  final case class ContainsAll(transactions: Seq[Transaction], replyTo: Promise[Boolean]) extends MempoolMessage
  final case class TryAddTransaction(
      tx: Transaction,
      walletDirectory: ActorRef[WalletDirectoryMessage],
      replyTo: Promise[Either[String, Unit]]
  ) extends MempoolMessage
  final case class RemoveTransactions(transactions: Seq[Transaction], replyTo: Promise[Unit]) extends MempoolMessage
}

final class MempoolActor(initialTransactions: Vector[Transaction]) extends SimpleActor[MempoolMessage]("mempool") {
  import MempoolMessage._
  import WalletDirectoryMessage._

  private var transactions: Vector[Transaction] = initialTransactions

  override protected def receive(message: MempoolMessage): Unit = message match {
    case GetTransactions(replyTo) =>
      replyTo.success(transactions)

    case GetPendingOutgoing(address, replyTo) =>
      replyTo.success(transactions.filter(_.from == address).map(_.amount).sum)

    case ContainsAll(candidates, replyTo) =>
      replyTo.success(candidates.forall(transactions.contains))

    case TryAddTransaction(tx, walletDirectory, replyTo) =>
      val pending = transactions.filter(_.from == tx.from).map(_.amount).sum
      val validation = walletDirectory.ask(ValidateTransaction(tx, pending, _))

      val result = validation.flatMap { _ =>
        if (transactions.contains(tx)) {
          Left("La transaction est déjà présente dans la mempool.")
        } else {
          transactions = transactions :+ tx
          Right(())
        }
      }

      replyTo.success(result)

    case RemoveTransactions(toRemove, replyTo) =>
      toRemove.foreach { tx =>
        val index = transactions.indexOf(tx)
        if (index >= 0) {
          transactions = transactions.patch(index, Nil, 1)
        }
      }
      replyTo.success(())
  }
}

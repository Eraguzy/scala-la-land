package blockchain

import scala.concurrent.Promise

sealed trait MempoolMessage
object MempoolMessage {
  final case class GetTransactions(replyTo: Promise[Vector[Transaction]]) extends MempoolMessage
  final case class RequestTransaction(replyTo: Promise[Option[Transaction]]) extends MempoolMessage
  final case class RequestTopTransactions(limit: Int, replyTo: Promise[Vector[Transaction]]) extends MempoolMessage
  final case class GetPendingOutgoing(address: String, replyTo: Promise[BigDecimal]) extends MempoolMessage
  final case class ContainsAll(transactions: Seq[Transaction], replyTo: Promise[Boolean]) extends MempoolMessage
  final case class SubmitTransaction(
      tx: Transaction,
      walletDirectory: ActorRef[WalletDirectoryMessage],
      replyTo: Promise[Either[String, Unit]]
  ) extends MempoolMessage
  final case class AddPrevalidatedTransaction(
      tx: Transaction,
      replyTo: Promise[Either[String, Unit]]
  ) extends MempoolMessage
  final case class TryAddTransaction(
      tx: Transaction,
      walletDirectory: ActorRef[WalletDirectoryMessage],
      replyTo: Promise[Either[String, Unit]]
  ) extends MempoolMessage
  final case class DeleteDoneTransactions(transactions: Seq[Transaction], replyTo: Promise[Unit]) extends MempoolMessage
  final case class RemoveConfirmedTransactions(transactions: Seq[Transaction], replyTo: Promise[Unit]) extends MempoolMessage
  final case class RemoveTransactions(transactions: Seq[Transaction], replyTo: Promise[Unit]) extends MempoolMessage
}

// Stocke les transactions en attente et fournit une selection priorisee aux validateurs.
final class MempoolActor(initialTransactions: Vector[Transaction]) extends SimpleActor[MempoolMessage]("mempool") {
  import MempoolMessage._
  import WalletDirectoryMessage._

  // Regle de priorite demandee: score amount*fees, le plus grand en premier.
  private def priorityScore(tx: Transaction): BigDecimal = tx.amount * tx.fees

  private var transactions: Vector[Transaction] = sortTransactions(initialTransactions)

  private def sortTransactions(values: Vector[Transaction]): Vector[Transaction] =
    values.sortWith { (left, right) =>
      val leftScore = priorityScore(left)
      val rightScore = priorityScore(right)
      // Egalite de score: on prend d'abord le timestamp le plus ancien.
      if (leftScore == rightScore) left.timestamp < right.timestamp
      else leftScore > rightScore
    }

  private def removeInternal(toRemove: Seq[Transaction]): Unit = {
    toRemove.foreach { tx =>
      val index = transactions.indexOf(tx)
      if (index >= 0) {
        transactions = transactions.patch(index, Nil, 1)
      }
    }
  }

  private def addInternal(tx: Transaction): Either[String, Unit] = {
    if (transactions.contains(tx)) {
      Left("La transaction est déjà présente dans la mempool.")
    } else {
      transactions = sortTransactions(transactions :+ tx)
      Right(())
    }
  }

  override protected def receive(message: MempoolMessage): Unit = message match {
    case GetTransactions(replyTo) =>
      replyTo.success(transactions)

    case RequestTransaction(replyTo) =>
      replyTo.success(transactions.headOption)

    case RequestTopTransactions(limit, replyTo) =>
      val safeLimit = math.max(0, limit)
      replyTo.success(transactions.take(safeLimit))

    case GetPendingOutgoing(address, replyTo) =>
      replyTo.success(transactions.filter(_.from == address).map(_.totalDebit).sum)

    case ContainsAll(candidates, replyTo) =>
      replyTo.success(candidates.forall(transactions.contains))

    case SubmitTransaction(tx, walletDirectory, replyTo) =>
      val pending = transactions.filter(_.from == tx.from).map(_.totalDebit).sum
      val validation = walletDirectory.ask(ValidateTransaction(tx, pending, _))

      val result = validation.flatMap(_ => addInternal(tx))

      replyTo.success(result)

    case AddPrevalidatedTransaction(tx, replyTo) =>
      val result = addInternal(tx)

      replyTo.success(result)

    case TryAddTransaction(tx, walletDirectory, replyTo) =>
      // Alias de compatibilite conserve pour les anciens appels.
      this.receive(SubmitTransaction(tx, walletDirectory, replyTo))

    case RemoveConfirmedTransactions(toRemove, replyTo) =>
      removeInternal(toRemove)
      replyTo.success(())

    case DeleteDoneTransactions(toRemove, replyTo) =>
      // Alias de compatibilite conserve pour alignement schema.
      removeInternal(toRemove)
      replyTo.success(())

    case RemoveTransactions(toRemove, replyTo) =>
      removeInternal(toRemove)
      replyTo.success(())
  }
}

package blockchain

import scala.concurrent.Promise

sealed trait WalletDirectoryMessage
object WalletDirectoryMessage {
  final case class GetWallet(address: String, replyTo: Promise[Option[WalletSnapshot]]) extends WalletDirectoryMessage
  final case class GetAllWallets(replyTo: Promise[Vector[WalletSnapshot]]) extends WalletDirectoryMessage
  final case class IsValidator(address: String, replyTo: Promise[Boolean]) extends WalletDirectoryMessage
  final case class SubmitTransactionFromWallet(
      from: String,
      to: String,
      amount: BigDecimal,
      fees: BigDecimal,
      mempool: ActorRef[MempoolMessage],
      replyTo: Promise[Either[String, Transaction]]
  ) extends WalletDirectoryMessage
  final case class CreateTransaction(
      from: String,
      to: String,
      amount: BigDecimal,
      pendingOutgoing: BigDecimal,
      replyTo: Promise[Either[String, Transaction]]
  ) extends WalletDirectoryMessage
  final case class ValidateTransaction(
      tx: Transaction,
      pendingOutgoing: BigDecimal,
      replyTo: Promise[Either[String, Unit]]
  ) extends WalletDirectoryMessage
  final case class ApplyTransactions(transactions: Seq[Transaction], replyTo: Promise[Unit]) extends WalletDirectoryMessage
}

final class WalletDirectoryActor(
    walletRefs: Map[String, ActorRef[WalletMessage]]
) extends SimpleActor[WalletDirectoryMessage]("wallet-directory") {
  import MempoolMessage._
  import WalletDirectoryMessage._

  private def getSnapshot(address: String): Option[WalletSnapshot] =
    walletRefs.get(address).map(_.ask(WalletMessage.GetSnapshot))

  private def validateTransactionInternal(tx: Transaction, pendingOutgoing: BigDecimal): Either[String, Unit] = {
    val senderRefOpt = walletRefs.get(tx.from)
    val senderOpt = getSnapshot(tx.from)
    val recipientOpt = getSnapshot(tx.to)

    if (tx.from == Transaction.SystemAddress) {
      Left("Une transaction normale ne peut pas provenir de SYSTEM.")
    } else if (senderRefOpt.isEmpty || senderOpt.isEmpty) {
      Left(s"Wallet émetteur introuvable : ${tx.from}")
    } else if (recipientOpt.isEmpty) {
      Left(s"Wallet destinataire introuvable : ${tx.to}")
    } else if (tx.amount <= 0) {
      Left("Le montant doit être strictement positif.")
    } else if (tx.fees < 0) {
      Left("Les frais ne peuvent pas être négatifs.")
    } else if (tx.publicKey != senderOpt.get.publicKey) {
      Left("Clé publique incohérente pour l'émetteur.")
    } else {
      val payload = if (tx.publicKey.nonEmpty) tx.payload else tx.legacyPayload
      val signatureValid = senderRefOpt.get.ask(WalletMessage.VerifySignature(payload, tx.signature, tx.publicKey, _))
      if (!signatureValid) {
        Left("Signature invalide.")
      } else {
        val sender = senderOpt.get
        val available = sender.balance - pendingOutgoing
        if (available < tx.totalDebit) {
          Left(s"Solde insuffisant : disponible=${Transaction.formatAmount(available)}")
        } else {
          Right(())
        }
      }
    }
  }

  override protected def receive(message: WalletDirectoryMessage): Unit = message match {
    case GetWallet(address, replyTo) =>
      replyTo.success(getSnapshot(address))

    case GetAllWallets(replyTo) =>
      val snapshots = walletRefs.keys.toVector.sorted.flatMap(getSnapshot)
      replyTo.success(snapshots)

    case IsValidator(address, replyTo) =>
      replyTo.success(walletRefs.get(address).exists(_.ask(WalletMessage.IsValidator)))

    case SubmitTransactionFromWallet(from, to, amount, fees, mempool, replyTo) =>
      val senderRefOpt = walletRefs.get(from)
      val senderOpt = getSnapshot(from)
      val recipientOpt = getSnapshot(to)

      val result =
        if (senderRefOpt.isEmpty || senderOpt.isEmpty) {
          Left(s"Wallet émetteur introuvable : $from")
        } else if (recipientOpt.isEmpty) {
          Left(s"Wallet destinataire introuvable : $to")
        } else if (amount <= 0) {
          Left("Le montant doit être strictement positif.")
        } else if (fees < 0) {
          Left("Les frais ne peuvent pas être négatifs.")
        } else {
          val pending = mempool.ask(GetPendingOutgoing(from, _))
          val sender = senderOpt.get
          val available = sender.balance - pending

          if (available < (amount + fees)) {
            Left(s"Solde insuffisant : disponible=${Transaction.formatAmount(available)}")
          } else {
            senderRefOpt.get.ask(WalletMessage.CreateSignedTransaction(to, amount, fees, _)).flatMap { tx =>
              validateTransactionInternal(tx, pending).flatMap { _ =>
                mempool.ask(AddPrevalidatedTransaction(tx, _)).map(_ => tx)
              }
            }
          }
        }

      replyTo.success(result)

    case CreateTransaction(from, to, amount, pendingOutgoing, replyTo) =>
      val senderRefOpt = walletRefs.get(from)
      val senderOpt = getSnapshot(from)
      val recipientOpt = getSnapshot(to)

      val result =
        if (senderRefOpt.isEmpty || senderOpt.isEmpty) {
          Left(s"Wallet émetteur introuvable : $from")
        } else if (recipientOpt.isEmpty) {
          Left(s"Wallet destinataire introuvable : $to")
        } else if (amount <= 0) {
          Left("Le montant doit être strictement positif.")
        } else {
          val sender = senderOpt.get
          val available = sender.balance - pendingOutgoing
          if (available < amount) {
            Left(s"Solde insuffisant : disponible=${Transaction.formatAmount(available)}")
          } else {
            senderRefOpt.get.ask(WalletMessage.CreateSignedTransaction(to, amount, BigDecimal(0), _))
          }
        }

      replyTo.success(result)

    case ValidateTransaction(tx, pendingOutgoing, replyTo) =>
      replyTo.success(validateTransactionInternal(tx, pendingOutgoing))

    case ApplyTransactions(transactions, replyTo) =>
      transactions.foreach {
        case tx if tx.from == Transaction.SystemAddress =>
          walletRefs.get(tx.to) match {
            case Some(ref) => ref.ask(WalletMessage.ApplyCredit(tx.amount, _))
            case None      => throw new IllegalStateException(s"Wallet introuvable pour la reward : ${tx.to}")
          }

        case tx =>
          walletRefs.get(tx.from) match {
            case Some(ref) =>
              ref.ask(WalletMessage.ApplyDebit(tx.totalDebit, _)) match {
                case Left(error) => throw new IllegalStateException(error)
                case Right(_)    => ()
              }
            case None => throw new IllegalStateException(s"Wallet source introuvable : ${tx.from}")
          }

          walletRefs.get(tx.to) match {
            case Some(ref) => ref.ask(WalletMessage.ApplyCredit(tx.amount, _))
            case None      => throw new IllegalStateException(s"Wallet destination introuvable : ${tx.to}")
          }
      }

      replyTo.success(())
  }
}

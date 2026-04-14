package blockchain

import scala.concurrent.Promise

sealed trait WalletDirectoryMessage
object WalletDirectoryMessage {
  final case class GetWallet(address: String, replyTo: Promise[Option[WalletSnapshot]]) extends WalletDirectoryMessage
  final case class GetAllWallets(replyTo: Promise[Vector[WalletSnapshot]]) extends WalletDirectoryMessage
  final case class IsValidator(address: String, replyTo: Promise[Boolean]) extends WalletDirectoryMessage
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
  import WalletDirectoryMessage._

  private def getSnapshot(address: String): Option[WalletSnapshot] =
    walletRefs.get(address).map(_.ask(WalletMessage.GetSnapshot))

  override protected def receive(message: WalletDirectoryMessage): Unit = message match {
    case GetWallet(address, replyTo) =>
      replyTo.success(getSnapshot(address))

    case GetAllWallets(replyTo) =>
      val snapshots = walletRefs.keys.toVector.sorted.flatMap(getSnapshot)
      replyTo.success(snapshots)

    case IsValidator(address, replyTo) =>
      replyTo.success(walletRefs.get(address).exists(_.ask(WalletMessage.IsValidator)))

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
            val payload = s"$from|$to|${Transaction.formatAmount(amount)}"
            val signature = senderRefOpt.get.ask(WalletMessage.SignPayload(payload, _))
            Right(Transaction(from, to, amount, signature))
          }
        }

      replyTo.success(result)

    case ValidateTransaction(tx, pendingOutgoing, replyTo) =>
      val senderRefOpt = walletRefs.get(tx.from)
      val senderOpt = getSnapshot(tx.from)
      val recipientOpt = getSnapshot(tx.to)

      val result =
        if (tx.from == Transaction.SystemAddress) {
          Left("Une transaction normale ne peut pas provenir de SYSTEM.")
        } else if (senderRefOpt.isEmpty || senderOpt.isEmpty) {
          Left(s"Wallet émetteur introuvable : ${tx.from}")
        } else if (recipientOpt.isEmpty) {
          Left(s"Wallet destinataire introuvable : ${tx.to}")
        } else if (tx.amount <= 0) {
          Left("Le montant doit être strictement positif.")
        } else {
          val payload = tx.payload
          val signatureValid = senderRefOpt.get.ask(WalletMessage.VerifySignature(payload, tx.signature, _))
          if (!signatureValid) {
            Left("Signature invalide.")
          } else {
            val sender = senderOpt.get
            val available = sender.balance - pendingOutgoing
            if (available < tx.amount) {
              Left(s"Solde insuffisant : disponible=${Transaction.formatAmount(available)}")
            } else {
              Right(())
            }
          }
        }

      replyTo.success(result)

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
              ref.ask(WalletMessage.ApplyDebit(tx.amount, _)) match {
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

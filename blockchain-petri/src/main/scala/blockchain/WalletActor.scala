package blockchain

import scala.concurrent.Promise

sealed trait WalletMessage
object WalletMessage {
  final case class GetSnapshot(replyTo: Promise[WalletSnapshot]) extends WalletMessage
  final case class SignPayload(payload: String, replyTo: Promise[String]) extends WalletMessage
  final case class VerifySignature(payload: String, signature: String, publicKey: String, replyTo: Promise[Boolean])
      extends WalletMessage
  final case class CreateSignedTransaction(
      to: String,
      amount: BigDecimal,
      fees: BigDecimal,
      replyTo: Promise[Either[String, Transaction]]
  ) extends WalletMessage
  final case class ApplyCredit(amount: BigDecimal, replyTo: Promise[WalletSnapshot]) extends WalletMessage
  final case class ApplyDebit(amount: BigDecimal, replyTo: Promise[Either[String, WalletSnapshot]]) extends WalletMessage
  final case class IsValidator(replyTo: Promise[Boolean]) extends WalletMessage
}

final class WalletActor(initialState: WalletSnapshot) extends SimpleActor[WalletMessage](s"wallet-${initialState.address}") {
  import WalletMessage._

  private var state: WalletSnapshot = initialState

  private def buildSignedTransaction(to: String, amount: BigDecimal, fees: BigDecimal): Either[String, Transaction] = {
    if (amount <= 0) {
      Left("Le montant doit être strictement positif.")
    } else if (fees < 0) {
      Left("Les frais ne peuvent pas être négatifs.")
    } else {
      val tx = Transaction(
        from = state.address,
        to = to,
        amount = amount,
        fees = fees,
        timestamp = System.currentTimeMillis(),
        publicKey = state.publicKey,
        signature = ""
      )
      val signature =
        if (state.publicKey.nonEmpty) CryptoUtils.sign(tx.payload, state.secret)
        else CryptoUtils.sha256(tx.legacyPayload + state.secret)
      Right(tx.copy(signature = signature))
    }
  }

  override protected def receive(message: WalletMessage): Unit = message match {
    case GetSnapshot(replyTo) =>
      replyTo.success(state)

    case SignPayload(payload, replyTo) =>
      val signature =
        if (state.publicKey.nonEmpty) CryptoUtils.sign(payload, state.secret)
        else CryptoUtils.sha256(payload + state.secret)
      replyTo.success(signature)

    case VerifySignature(payload, signature, publicKey, replyTo) =>
      val isValid =
        if (publicKey.nonEmpty) CryptoUtils.verify(payload, signature, publicKey)
        else CryptoUtils.sha256(payload + state.secret) == signature
      replyTo.success(isValid)

    case CreateSignedTransaction(to, amount, fees, replyTo) =>
      replyTo.success(buildSignedTransaction(to, amount, fees))

    case ApplyCredit(amount, replyTo) =>
      state = state.copy(balance = state.balance + amount)
      replyTo.success(state)

    case ApplyDebit(amount, replyTo) =>
      if (state.balance < amount) {
        replyTo.success(Left(s"Solde insuffisant pour ${state.address}"))
      } else {
        state = state.copy(balance = state.balance - amount)
        replyTo.success(Right(state))
      }

    case IsValidator(replyTo) =>
      replyTo.success(state.isValidator)
  }
}

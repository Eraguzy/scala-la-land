package blockchain

import scala.concurrent.Promise

sealed trait WalletMessage
object WalletMessage {
  final case class GetSnapshot(replyTo: Promise[WalletSnapshot]) extends WalletMessage
  final case class SignPayload(payload: String, replyTo: Promise[String]) extends WalletMessage
  final case class VerifySignature(payload: String, signature: String, replyTo: Promise[Boolean]) extends WalletMessage
  final case class ApplyCredit(amount: BigDecimal, replyTo: Promise[WalletSnapshot]) extends WalletMessage
  final case class ApplyDebit(amount: BigDecimal, replyTo: Promise[Either[String, WalletSnapshot]]) extends WalletMessage
  final case class IsValidator(replyTo: Promise[Boolean]) extends WalletMessage
}

final class WalletActor(initialState: WalletSnapshot) extends SimpleActor[WalletMessage](s"wallet-${initialState.address}") {
  import WalletMessage._

  private var state: WalletSnapshot = initialState

  override protected def receive(message: WalletMessage): Unit = message match {
    case GetSnapshot(replyTo) =>
      replyTo.success(state)

    case SignPayload(payload, replyTo) =>
      replyTo.success(CryptoUtils.sha256(payload + state.secret))

    case VerifySignature(payload, signature, replyTo) =>
      replyTo.success(CryptoUtils.sha256(payload + state.secret) == signature)

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

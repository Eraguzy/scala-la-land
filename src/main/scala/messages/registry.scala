package messages

import akka.actor.typed.ActorRef

object Registry {
  sealed trait Command

  case class CreateWallet(name: String, initialBalance: BigInt, replyTo: ActorRef[CreateWalletResponse]) extends Command
  case class ListWallets(replyTo: ActorRef[WalletsListed]) extends Command
  case class GetWalletRef(name: String, replyTo: ActorRef[GetWalletResponse]) extends Command

  sealed trait CreateWalletResponse
  case class WalletCreated(name: String) extends CreateWalletResponse
  case object WalletAlreadyExists extends CreateWalletResponse
  case object NegativeBalance extends CreateWalletResponse

  sealed trait GetWalletResponse
  case class WalletFound(ref: ActorRef[Wallet.Command]) extends GetWalletResponse
  case object WalletNotFound extends GetWalletResponse

  case class WalletInfo(name: String)
  case class WalletsListed(wallets: List[WalletInfo])
}

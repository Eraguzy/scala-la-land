package actors

import akka.actor.typed._
import akka.actor.typed.scaladsl._
import messages._

object RegistryActor {

  def apply(db: ActorRef[DB.Command], mempool: ActorRef[Mempool.Command]): Behavior[Registry.Command] =
    behavior(Map.empty, db, mempool)

  private def behavior(
      wallets: Map[String, ActorRef[Wallet.Command]],
      db: ActorRef[DB.Command],
      mempool: ActorRef[Mempool.Command]
  ): Behavior[Registry.Command] =
    Behaviors.receive { (ctx, msg) =>
      msg match {
        case Registry.CreateWallet(name, initialBalance, replyTo) =>
          if (wallets.contains(name)) {
            replyTo ! Registry.WalletAlreadyExists
            Behaviors.same
          } else {
            val ref = ctx.spawn(WalletActor(name, initialBalance, mempool, db), s"wallet-$name")
            replyTo ! Registry.WalletCreated(name)
            behavior(wallets + (name -> ref), db, mempool)
          }

        case Registry.ListWallets(replyTo) =>
          replyTo ! Registry.WalletsListed(wallets.keys.map(Registry.WalletInfo(_)).toList.sortBy(_.name))
          Behaviors.same

        case Registry.GetWalletRef(name, replyTo) =>
          wallets.get(name) match {
            case Some(ref) => replyTo ! Registry.WalletFound(ref)
            case None      => replyTo ! Registry.WalletNotFound
          }
          Behaviors.same
      }
    }
}

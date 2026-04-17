package actors

import akka.actor.typed._
import akka.actor.typed.scaladsl._
import messages._
import objects._
import functions.Crypto

object WalletActor {

  case class State(
                    publicKey: String,
                    privateKey: String,
                    balance: BigInt
                  )

  def apply(
             pubKey: String,
             privKey: String,
             initialBalance: BigInt,
             mempool: ActorRef[Mempool.Command]
           ): Behavior[Wallet.Command] =
    behavior(State(pubKey, privKey, initialBalance), mempool)

  private def behavior(state: State, mempool: ActorRef[Mempool.Command]): Behavior[Wallet.Command] =
    Behaviors.receive { (ctx, msg) =>
      msg match {

        case Wallet.GetBalance(replyTo) =>
          replyTo ! state.balance
          Behaviors.same

        case Wallet.CreateTx(to, amount) =>
          if (amount <= state.balance) {
            val unsigned = UnsignedTransaction(state.publicKey, to, amount)

            val signature = Crypto.sign(unsigned.toString, state.privateKey)
            val signed = SignedTransaction(unsigned, signature)

            ctx.log.info(s"Wallet ${state.publicKey} : Envoi de $amount vers Mempool (Solde restant : ${state.balance - amount})")

            mempool ! Mempool.AddTx(signed)

            behavior(state.copy(balance = state.balance - amount), mempool)
          } else {
            ctx.log.error(s"Wallet ${state.publicKey} : Solde insuffisant ($amount > ${state.balance})")
            Behaviors.same
          }
      }
    }
}
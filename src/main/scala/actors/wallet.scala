package actors

import akka.actor.typed._
import akka.actor.typed.scaladsl._
import messages._
import objects._
import functions.Crypto

object WalletActor {

  // L'état contient l'identité et le solde du portefeuille
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
          if (amount <= state.balance) { // on check si le solde est suffisant
            val unsigned = UnsignedTransaction(
              id = java.util.UUID.randomUUID().toString,
              from = state.publicKey,
              to = to,
              amount = amount,
              fees = 10,
              timestamp = System.currentTimeMillis()
            )

            // On signe la transaction avec la clé privée du Wallet
            val signature = Crypto.sign(unsigned, state.privateKey)
            val signed = SignedTransaction(unsigned, signature)

            ctx.log.info(s"Wallet ${state.publicKey} : TX ${unsigned.id} signée et envoyée vers Mempool.")
            mempool ! Mempool.AddTx(signed)

 
            val newState = state.copy(balance = state.balance - amount)
            behavior(newState, mempool)
          }
          else {
            ctx.log.error(s"Wallet ${state.publicKey} : Solde insuffisant ($amount > ${state.balance})")
            Behaviors.same
          }
      }
    }
}
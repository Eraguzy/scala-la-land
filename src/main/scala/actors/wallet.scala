package actors

import akka.actor.typed._
import akka.actor.typed.scaladsl._
import messages._
import objects._
import functions.Crypto

object WalletActor {
  // public key = n-pubInt, private key = n-privInt
  case class State(
      n: BigInt,
      pubInt: BigInt,
      privInt: BigInt,
      balance: BigInt,
      name: String, // for logs, not used in txs
      nonce: Long = 0L
  )

  def apply(
      name: String,
      initialBalance: BigInt,
      mempool: ActorRef[Mempool.Command]
  ): Behavior[Wallet.Command] = {
    val (n, pubInt, privInt) = Crypto.genWalletInts()

    behavior(State(n, pubInt, privInt, initialBalance, name), mempool)
  }

  private def behavior(
      state: State,
      mempool: ActorRef[
        Mempool.Command
      ] // allow a wallet to be related to a mempool to send txs
  ): Behavior[Wallet.Command] =
    Behaviors.receive { (ctx, msg) =>
      msg match {

        case Wallet.GetBalance(replyTo) =>
          replyTo ! state.balance
          Behaviors.same

        case Wallet.CreateTx(to, amount, fee) =>
          // check wallet balance
          val totalCost = amount + fee
          if (totalCost > state.balance) {
            ctx.log.error(
              s"wallet ${state.name} : insufficient funds ($totalCost > ${state.balance})"
            )
            Behaviors.same
          } else {

            // tx params : origin, dest, amount, fee, nonce, timestamp
            val unsigned = UnsignedTransaction(
              from = publicKey(state),
              to = to,
              amount = amount,
              fees = fee,
              nonce = state.nonce + 1,
              timestamp = System.currentTimeMillis()
            )
            val txHash = Crypto.hashTx(unsigned)

            // signed tx = hash + signature
            val signature = Crypto.sign(txHash, state.n, state.privInt)
            if (signature.isEmpty) {
              ctx.log.error(
                s"wallet ${state.name} : failed to sign transaction ${unsigned.nonce}"
              )
              Behaviors.same

            } else {
              // we send signed tx + tx data to mempool
              val signed = SignedTransaction(unsigned, txHash, signature.get)

              ctx.log.info(
                s"wallet ${state.name} : TX ${unsigned.nonce} from wallet ${publicKey(state)} signed and sent to Mempool."
              )
              mempool ! Mempool.AddTx(
                signed
              ) // add signature AND tx data to mempool, contained in this variable

              // update local state (balance and nonce) after sending the tx to mempool
              val newState = state.copy(
                balance = state.balance - totalCost,
                nonce = state.nonce + 1
              )
              behavior(newState, mempool)
            }
          }
      }
    }
  def publicKey(state: State): String = s"${state.n}-${state.pubInt}"
  def privateKey(state: State): String = s"${state.n}-${state.privInt}"
}

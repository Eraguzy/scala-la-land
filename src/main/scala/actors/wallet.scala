package actors

import akka.actor.typed._
import akka.actor.typed.scaladsl._
import messages._
import objects._
import functions.Crypto

object WalletActor {

  // internal queue entry — holds everything needed to process a tx once a previous one is done
  private case class PendingTx(
      to: String,
      amount: BigInt,
      fee: BigInt,
      replyTo: ActorRef[Boolean]
  )

  // public key = "n-pubInt", private key = "n-privInt"
  case class State(
      n: BigInt,
      pubInt: BigInt,
      privInt: BigInt,
      initialBalance: BigInt,
      name: String,
      nonce: Long = 0L,
      txInProgress: Boolean = false
  )

  def apply(
      name: String,
      initialBalance: BigInt,
      mempool: ActorRef[Mempool.Command],
      db: ActorRef[DB.Command]
  ): Behavior[Wallet.Command] = {
    val (n, pubInt, privInt) = Crypto.genWalletInts()
    behavior(
      State(n, pubInt, privInt, initialBalance, name, txInProgress = false),
      mempool,
      db,
      pendingTxs = Vector.empty
    )
  }

  private def behavior(
      state: State,
      mempool: ActorRef[Mempool.Command],
      db: ActorRef[DB.Command],
      pendingTxs: Vector[PendingTx]
  ): Behavior[Wallet.Command] =
    Behaviors.receive { (ctx, msg) =>
      msg match {

        case Wallet.GetBalance(replyTo) =>
          // balance = what we started with + net chain activity
          val balanceReplyAdapter = ctx.spawnAnonymous(
            Behaviors.receiveMessage[DB.BalanceResponse] { response =>
              val chainBalance = BigDecimal(response.balance).toBigInt
              replyTo ! (state.initialBalance + chainBalance)
              Behaviors.stopped
            }
          )
          db ! DB.GetBalanceAtDate(
            walletPublicKey(state),
            System.currentTimeMillis(),
            balanceReplyAdapter
          )
          Behaviors.same

        case Wallet.GetPublicKey(replyTo) =>
          replyTo ! walletPublicKey(state)
          Behaviors.same

        case Wallet.GetPrivateKey(replyTo) =>
          replyTo ! walletPrivateKey(state)
          Behaviors.same

        case Wallet.CreateTx(to, amount, fee, replyTo) =>
          // if another tx is already being processed, queue this one —
          // we can't run two in parallel because the first one changes balance and nonce
          val request = PendingTx(to, amount, fee, replyTo)
          if (state.txInProgress) {
            behavior(state, mempool, db, pendingTxs :+ request)
          } else {
            requestBalanceForTx(ctx, request)
            behavior(state.copy(txInProgress = true), mempool, db, pendingTxs)
          }

        case Wallet.CreateTxInternal(
              to,
              amount,
              fee,
              currentBalance,
              replyTo
            ) =>
          if (currentBalance < amount + fee) {
            ctx.log.error(s"wallet ${state.name}: insufficient funds")
            replyTo ! false
            continueWithNextTx(ctx, state, mempool, db, pendingTxs)
          } else {
            val unsigned = UnsignedTransaction(
              from = walletPublicKey(state),
              to = to,
              amount = amount,
              fees = fee,
              nonce = state.nonce + 1,
              timestamp = System.currentTimeMillis()
            )
            val txHash = Crypto.hashTx(unsigned)
            val signature = Crypto.sign(txHash, state.n, state.privInt)

            if (signature.isEmpty) {
              ctx.log.error(s"wallet ${state.name}: failed to sign tx $txHash")
              replyTo ! false
              continueWithNextTx(ctx, state, mempool, db, pendingTxs)
            } else {
              val signed = SignedTransaction(unsigned, txHash, signature.get)
              ctx.log.info(
                s"wallet ${state.name}: TX $txHash signed and sent to mempool."
              )
              mempool ! Mempool.AddTx(signed, replyTo)

              val newState = state.copy(nonce = state.nonce + 1)
              continueWithNextTx(ctx, newState, mempool, db, pendingTxs)
            }
          }
      }
    }

  // spawns a short-lived actor to receive the DB balance response and convert it
  // into a CreateTxInternal — needed because we can't do a blocking ask inside an actor
  private def requestBalanceForTx(
      ctx: ActorContext[Wallet.Command],
      request: PendingTx
  ): Unit = {
    val balanceReceiver = ctx.spawnAnonymous(
      Behaviors.receiveMessage[BigInt] { balance =>
        ctx.self ! Wallet.CreateTxInternal(
          request.to,
          request.amount,
          request.fee,
          balance,
          request.replyTo
        )
        Behaviors.stopped
      }
    )
    ctx.self ! Wallet.GetBalance(balanceReceiver)
  }

  private def continueWithNextTx(
      ctx: ActorContext[Wallet.Command],
      state: State,
      mempool: ActorRef[Mempool.Command],
      db: ActorRef[DB.Command],
      pendingTxs: Vector[PendingTx]
  ): Behavior[Wallet.Command] =
    pendingTxs.headOption match {
      case Some(next) =>
        requestBalanceForTx(ctx, next)
        behavior(state.copy(txInProgress = true), mempool, db, pendingTxs.tail)
      case None =>
        behavior(state.copy(txInProgress = false), mempool, db, pendingTxs)
    }

  private def walletPublicKey(state: State): String =
    s"${state.n}-${state.pubInt}"
  private def walletPrivateKey(state: State): String =
    s"${state.n}-${state.privInt}"
}

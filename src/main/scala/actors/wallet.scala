package actors

import akka.actor.typed._
import akka.actor.typed.scaladsl._
import messages._
import objects._
import functions.Crypto

object WalletActor {
  // a queue is being used to handle multiple tx requests sequentially,
  // to avoid concurrency issues with balance and nonce management
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
      name: String, // for logs, not used in txs
      nonce: Long = 0L,
      txInProgress: Boolean = false // append in queue or process
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
      // allow a wallet to be related to infras to send txs
      mempool: ActorRef[Mempool.Command],
      db: ActorRef[DB.Command],
      pendingTxs: Vector[PendingTx]
  ): Behavior[Wallet.Command] =
    Behaviors.receive { (ctx, msg) =>
      msg match {

        // wallet's amount = initial balance + balance returned by blockchain
        case Wallet.GetBalance(replyTo) =>
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

        // wrapper to get balance from db before creating the tx
        case Wallet.CreateTx(to, amount, fee, replyTo) =>
          val request = PendingTx(to, amount, fee, replyTo)
          if (state.txInProgress) {
            behavior(
              state,
              mempool,
              db,
              pendingTxs :+ request //  append to pending txs if another tx is in progress
            )
          } else {
            requestBalanceForTx(ctx, request)
            val newState = state.copy(txInProgress = true)
            behavior(newState, mempool, db, pendingTxs)
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

            // tx params : origin, dest, amount, fee, nonce, timestamp
            val unsigned = UnsignedTransaction(
              from = walletPublicKey(state),
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
                s"wallet ${state.name} : failed to sign transaction ${txHash}"
              )
              replyTo ! false
              continueWithNextTx(ctx, state, mempool, db, pendingTxs)

            } else {
              // we send signed tx + tx data to mempool
              val signed = SignedTransaction(unsigned, txHash, signature.get)

              ctx.log.info(
                s"wallet ${state.name} : TX ${txHash} (${unsigned.nonce}) signed and sent to Mempool."
              )
              mempool ! Mempool.AddTx(
                signed
              ) // add signature AND tx data to mempool, contained in this variable
              replyTo ! true // fixme : it should be waiting for mempool confirmation

              // update local state (balance and nonce) after sending the tx to mempool
              val newState = state.copy(
                initialBalance = state.initialBalance - amount - fee,
                nonce = state.nonce + 1
              )
              continueWithNextTx(ctx, newState, mempool, db, pendingTxs)
            }
          }
      }
    }

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
      case Some(nextRequest) =>
        requestBalanceForTx(ctx, nextRequest)
        val newState = state.copy(txInProgress = true)
        behavior(newState, mempool, db, pendingTxs.tail)
      case None =>
        val newState = state.copy(txInProgress = false)
        behavior(newState, mempool, db, pendingTxs)
    }

  private def walletPublicKey(state: State): String =
    s"${state.n}-${state.pubInt}"

  private def walletPrivateKey(state: State): String =
    s"${state.n}-${state.privInt}"

}

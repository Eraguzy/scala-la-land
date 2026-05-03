package actors

import akka.actor.typed._
import akka.actor.typed.scaladsl._
import messages._
import objects._
import scala.concurrent.duration._
import functions.Crypto

object ValidatorActor {

  val DIFFICULTY = "0000"

  def apply(
      mempool: ActorRef[Mempool.Command],
      db: ActorRef[DB.Command]
  ): Behavior[Validator.Command] =
    Behaviors.setup { ctx =>
      Behaviors.withTimers { timers =>
        timers.startTimerWithFixedDelay(Validator.StartMining, 5.seconds)

        val txResponseAdapter = ctx.messageAdapter[Mempool.Txs](response =>
          Validator.ProcessBlock(response.txs)
        )

        val dbResponseAdapter = ctx.messageAdapter[DB.LastBlockInfo] {
          case DB.LastBlockInfo(hash, id) =>
            Validator.ProcessMining(hash.toString, id.toString.toInt)
        }

        // Adapter to receive the DB save confirmation before confirming transactions.
        val dbStatusAdapter = ctx.messageAdapter[DB.Response] {
          case DB.Success     => Validator.ConfirmSaved
          case DB.Failed(err) => Validator.SaveFailed(err)
        }

        behavior(
          mempool,
          db,
          txResponseAdapter,
          dbResponseAdapter,
          dbStatusAdapter,
          List.empty
        )
      }
    }

  private def createFeeTx(
      pendingTx: PendingTx,
      timestamp: Long
  ): SignedTransaction = {
    val originalTx = pendingTx.tx.tx
    val feeTx = UnsignedTransaction(
      from = originalTx.from,
      to = "0-0",
      amount = originalTx.fees,
      fees = 0,
      nonce = 0,
      timestamp = timestamp
    )
    val txId = Crypto.hashTx(feeTx)
    SignedTransaction(feeTx, txId, "SYSTEM_FEE")
  }

  private def behavior(
      mempool: ActorRef[Mempool.Command],
      db: ActorRef[DB.Command],
      txAdapter: ActorRef[Mempool.Txs],
      dbAdapter: ActorRef[DB.LastBlockInfo],
      dbStatusAdapter: ActorRef[DB.Response],
      pendingTxs: List[PendingTx]
  ): Behavior[Validator.Command] =
    Behaviors.receive { (ctx, msg) =>
      msg match {
        case Validator.StartMining =>
          ctx.log.info("Validator: polling mempool...")
          mempool ! Mempool.GetTxs(txAdapter)
          Behaviors.same

        case Validator.ProcessBlock(txs) =>
          if (txs.isEmpty) Behaviors.same
          else {
            db ! DB.GetLastBlock(dbAdapter)
            behavior(mempool, db, txAdapter, dbAdapter, dbStatusAdapter, txs)
          }

        case Validator.ProcessMining(lastHash, currentId) =>
          ctx.log.info(s"Validator: mining block $currentId...")
          val timestamp = System.currentTimeMillis()

          // Create fee transactions (one per pending transaction, sent to address "0-0")
          val feeTxs = pendingTxs.map(createFeeTx(_, timestamp))
          val allTxs = pendingTxs.map(_.tx) ++ feeTxs

          val txsData = allTxs.map(_.txId).mkString("-")
          val (nonce, blockHash) =
            functions.Miner.mine(txsData, lastHash, timestamp, 0, DIFFICULTY)

          ctx.log.info(
            s"Validator: block mined! Nonce=$nonce | Hash=$blockHash"
          )
          val newBlock = Block(currentId, allTxs, lastHash, timestamp)

          // Save the block and transition to the waiting state until the DB confirms.
          db ! DB.SaveBlock(newBlock, dbStatusAdapter)
          waitingForDbState(
            mempool,
            db,
            txAdapter,
            dbAdapter,
            dbStatusAdapter,
            pendingTxs
          )

        case _ => Behaviors.same
      }
    }

  private def waitingForDbState(
      mempool: ActorRef[Mempool.Command],
      db: ActorRef[DB.Command],
      txAdapter: ActorRef[Mempool.Txs],
      dbAdapter: ActorRef[DB.LastBlockInfo],
      dbStatusAdapter: ActorRef[DB.Response],
      pendingTxs: List[PendingTx]
  ): Behavior[Validator.Command] =
    Behaviors.receive { (ctx, msg) =>
      msg match {
        case Validator.ConfirmSaved =>
          ctx.log.info(
            "Validator: DB confirmed. Notifying wallets and cleaning mempool."
          )
          pendingTxs.foreach(_.replyTo ! true)
          mempool ! Mempool.RemoveTxs(pendingTxs.map(_.tx))
          behavior(
            mempool,
            db,
            txAdapter,
            dbAdapter,
            dbStatusAdapter,
            List.empty
          )

        case Validator.SaveFailed(err) =>
          ctx.log.error(
            s"Validator: DB save failed ($err). Rejecting transactions."
          )
          pendingTxs.foreach(_.replyTo ! false)
          behavior(
            mempool,
            db,
            txAdapter,
            dbAdapter,
            dbStatusAdapter,
            List.empty
          )

        case _ =>
          // Ignore new StartMining ticks while waiting for DB confirmation.
          Behaviors.same
      }
    }
}

package actors

import akka.actor.typed._
import akka.actor.typed.scaladsl._
import messages.Mempool
import functions.Crypto
import objects.PendingTx

object MempoolActor {

  // when spawned, initialize the mempool with an empty list of transactions
  def apply(): Behavior[Mempool.Command] =
    Behaviors.supervise(behavior(List.empty))  // restart the actor if needed: resets to an empty list, no debits occur, pending transactions are discarded
      .onFailure[Exception](SupervisorStrategy.restart)

//  // Function representing the mempool state at a given time; it takes the current list of transactions as a parameter
//  // When a transaction is received, we create a new mempool with the new transaction appended to the list,
//  // which lets the mempool evolve without using mutable variables (no `var`).
//  // -> state management via recursion
  private def behavior(txs: List[PendingTx]): Behavior[Mempool.Command] =
    Behaviors.receive { (ctx, msg) =>
      msg match {

        case Mempool.AddTx(signedTx, replyTo) =>
          val isValid = Crypto.verify(signedTx.tx, signedTx.signature, signedTx.tx.from)

          if (isValid) {
            ctx.log.info(s"Mempool: valid signature for TX ${signedTx.txId}. Added to priority queue.")
            // Add and sort by fees in descending order.
            val updated = (PendingTx(signedTx, replyTo) :: txs).sortBy(_.tx.tx.fees)(Ordering[BigInt].reverse)
            ctx.log.info("Queue order (fees desc): " + updated.map(p => s"${p.tx.txId.take(8)}(fee=${p.tx.tx.fees})").mkString(", "))
            behavior(updated) // create a new list: for the next message, use a new behavior instance with this updated list
          } else {
            ctx.log.error(s"Mempool: REJECTED - invalid signature for TX ${signedTx.txId}.")
            replyTo ! false
            Behaviors.same
          }

        // Le validator récupère les 2 premières transactions sans les supprimer immédiatement.
        // La suppression réelle ne se fait que via RemoveTxs après confirmation par la DB.
        case Mempool.GetTxs(replyTo) =>
          // take the first 2 transactions
          /**
           * Splits the collection `txs` into two parts at index `2`:
           * - the first part contains the first 2 transactions to send;
           * - `rest` corresponds to the remaining transactions, i.e. those not included in the first two
           *   (possibly empty if `txs` has two elements or fewer).
           */
          val (toSend, _) = txs.splitAt(2)
          if (toSend.nonEmpty) {
            ctx.log.info(s"Mempool: sending ${toSend.size} transaction(s) to Validator.")
            replyTo ! Mempool.Txs(toSend)
          } else {
            ctx.log.info("Mempool: empty - nothing to send to Validator.")
            replyTo ! Mempool.Txs(List.empty)
          }
          Behaviors.same

        // Filter the list to keep only transactions that were NOT mined
        case Mempool.RemoveTxs(confirmedTxs) =>
          val remaining = txs.filterNot(t => confirmedTxs.exists(_.txId == t.tx.txId))
          ctx.log.info(s"Mempool: cleaning ${confirmedTxs.size} confirmed tx(s). ${remaining.size} remaining.")
          behavior(remaining)

        // Read-only view of the queue — does not modify state.
        case Mempool.ViewPending(replyTo) =>
          val infos = txs.map { pt =>
            Mempool.PendingTxInfo(
              txId      = pt.tx.txId,
              from      = pt.tx.tx.from,
              to        = pt.tx.tx.to,
              amount    = pt.tx.tx.amount,
              fee       = pt.tx.tx.fees,
              timestamp = pt.tx.tx.timestamp
            )
          }
          replyTo ! Mempool.PendingView(infos)
          Behaviors.same
      }
    }
}


//// In Akka, we try to avoid mutable variables (`var`).
//// But our mempool needs to store a list of transactions,
//// so we use a recursive function that takes the current transaction list as a parameter.
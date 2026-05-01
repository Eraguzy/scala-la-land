package actors

import akka.actor.typed._
import akka.actor.typed.scaladsl._
import messages.Mempool
import functions.Crypto
import objects.PendingTx

object MempoolActor {
  def apply(): Behavior[Mempool.Command] = behavior(List.empty)

  // State is a priority queue (sorted by fee descending), managed functionally via recursion.
  private def behavior(txs: List[PendingTx]): Behavior[Mempool.Command] =
    Behaviors.receive { (ctx, msg) =>
      msg match {

        case Mempool.AddTx(signedTx, replyTo) =>
          val isValid = Crypto.verify(signedTx.tx, signedTx.signature, signedTx.tx.from)

          if (isValid) {
            ctx.log.info(s"Mempool: valid signature for TX ${signedTx.txId}. Adding to priority queue.")
            val updated = (PendingTx(signedTx, replyTo) :: txs).sortBy(_.tx.tx.fees)(Ordering[BigInt].reverse)
            ctx.log.info("Queue order (by fee desc): " + updated.map(p => s"${p.tx.txId.take(8)}(fee=${p.tx.tx.fees})").mkString(", "))
            behavior(updated)
          } else {
            ctx.log.error(s"Mempool: REJECTED - invalid signature for TX ${signedTx.txId}.")
            replyTo ! false
            Behaviors.same
          }

        // The validator fetches the top 2 transactions without removing them yet.
        // Removal only happens via RemoveTxs after the block is confirmed by the DB.
        case Mempool.GetTxs(replyTo) =>
          val (toSend, _) = txs.splitAt(2)
          if (toSend.nonEmpty) {
            ctx.log.info(s"Mempool: sending ${toSend.size} transaction(s) to Validator.")
            replyTo ! Mempool.Txs(toSend)
          } else {
            ctx.log.info("Mempool: empty - nothing to send to Validator.")
            replyTo ! Mempool.Txs(List.empty)
          }
          Behaviors.same

        case Mempool.RemoveTxs(confirmedTxs) =>
          val remaining = txs.filterNot(t => confirmedTxs.exists(_.txId == t.tx.txId))
          ctx.log.info(s"Mempool: cleaned up ${confirmedTxs.size} confirmed tx(s). ${remaining.size} remaining.")
          behavior(remaining)

        // Read-only view of the pending queue — does not modify state.
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

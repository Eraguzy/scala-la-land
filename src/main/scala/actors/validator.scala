package actors

import akka.actor.typed._
import akka.actor.typed.scaladsl._
import messages._
import objects._

object ValidatorActor {
  def apply(mempool: ActorRef[Mempool.Command], db: ActorRef[DB.Command]): Behavior[Validator.Command] =
    Behaviors.setup { ctx =>
      // On crée un "adapter" pour recevoir la liste des transactions de la Mempool
      val txResponseAdapter: ActorRef[Mempool.Txs] = ctx.messageAdapter(response => Validator.ProcessBlock(response.txs))

      Behaviors.receiveMessage {
        case Validator.StartMining =>
          ctx.log.info("Validator : Demande des transactions à la Mempool...")
          mempool ! Mempool.GetTxs(txResponseAdapter) // Requête vers la Mempool
          Behaviors.same

        case Validator.ProcessBlock(txs) =>
          if (txs.isEmpty) {
            ctx.log.info("Validator : Rien à miner, mempool vide.")
          } else {
            ctx.log.info(s"Validator : Minage d'un bloc avec ${txs.size} transactions.")
            val newBlock = Block(scala.util.Random.nextLong(1000), txs, "hash_prec", System.currentTimeMillis())
            db ! DB.SaveBlock(newBlock, ctx.system.deadLetters)

            // On prévient la mempool que ces transactions sont traitées
            mempool ! Mempool.RemoveTxs(txs)
          }
          Behaviors.same
      }
    }
}
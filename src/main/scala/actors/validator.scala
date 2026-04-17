package actors

import akka.actor.typed._
import akka.actor.typed.scaladsl._
import messages._
import objects._


// Note de matias à faire :

// établir une logique d'auto gestion, traduite par le fait que toutes les 5 secondes (c'est plus visuel)
// le validator demande à la mempool s'il y a des transactions à miner, et si oui, il les mine et les envoie à la DB
// le message à transmettre à la mempool pour récupérer les transactions est Mempool.GetTxs


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
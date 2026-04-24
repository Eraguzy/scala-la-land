package actors

import akka.actor.typed._
import akka.actor.typed.scaladsl._
import messages._
import objects._
import functions.Crypto // On a besoin de la fonction de hash
import scala.concurrent.duration._

object ValidatorActor {

  // La difficulté : on veut que le hash commence par "00"
  val DIFFICULTY = "00" 

  def apply(mempool: ActorRef[Mempool.Command], db: ActorRef[DB.Command]): Behavior[Validator.Command] =
    Behaviors.setup { ctx =>
      Behaviors.withTimers { timers =>
        timers.startTimerWithFixedDelay(Validator.StartMining, 5.seconds)

        val txResponseAdapter: ActorRef[Mempool.Txs] = ctx.messageAdapter(response => Validator.ProcessBlock(response.txs))

        // On a besoin d'un adapter pour recevoir le dernier hash de la DB
        val dbResponseAdapter: ActorRef[DB.LastBlockInfo] = ctx.messageAdapter { 
          case DB.LastBlockInfo(hash, id) => Validator.ProcessMining(hash.toString, id.toString.toInt) 
        }
        // On garde en mémoire les transactions en cours de minage dans l'état de l'acteur
        behavior(mempool, db, txResponseAdapter, dbResponseAdapter, List.empty)
      }
    }

  private def behavior(
      mempool: ActorRef[Mempool.Command],
      db: ActorRef[DB.Command],
      txAdapter: ActorRef[Mempool.Txs],
      dbAdapter: ActorRef[DB.LastBlockInfo],
      pendingTxs: List[SignedTransaction]
  ): Behavior[Validator.Command] =
    Behaviors.receive { (ctx, msg) =>
      msg match {
        case Validator.StartMining =>
          ctx.log.info("Validator : Demande des transactions à la Mempool (Tick 5s)...")
          mempool ! Mempool.GetTxs(txAdapter)
          Behaviors.same

        case Validator.ProcessBlock(txs) =>
          if (txs.isEmpty) {
            ctx.log.info("Validator : Rien à miner, mempool vide.")
            Behaviors.same
          } else {
            // ÉTAPE 1 : On a des TX. Avant de miner, on demande à la DB le dernier bloc pour chaîner correctement.
            ctx.log.info(s"Validator : ${txs.size} transactions reçues. Récupération du dernier bloc en DB...")
            db ! DB.GetLastBlock(dbAdapter)
            
            // On sauvegarde les txs dans notre état en attendant la réponse de la DB
            behavior(mempool, db, txAdapter, dbAdapter, txs) 
          }

        case Validator.ProcessMining(lastHash, currentId) =>
           // ÉTAPE 2 : On a le previousHash et les TX. On lance le Proof of Work 
           ctx.log.info(s"Validator : Lancement du Proof of Work (recherche de '$DIFFICULTY')...")
           
           val timestamp = System.currentTimeMillis()
           // On transforme la liste de TX en un gros String pour le hacher
           val txsData = pendingTxs.map(_.txId).mkString("-")

           // Appel de notre fonction récursive de minage
           // On délègue le travail mathématique à notre fonction pure !
           val (nonce, blockHash) = functions.Miner.mine(txsData, lastHash, timestamp, 0, DIFFICULTY)
           
           ctx.log.info(s"Validator : ⛏️ BLOC MINÉ ! Nonce trouvé : $nonce | Hash : $blockHash")

           // ÉTAPE 3 : Création du bloc et sauvegarde
           val newBlock = Block(currentId, pendingTxs, lastHash, timestamp) // Idéalement, il faudrait ajouter blockHash à la case class Block
           
           db ! DB.SaveBlock(newBlock)
           mempool ! Mempool.RemoveTxs(pendingTxs)

           // On vide notre état local
           behavior(mempool, db, txAdapter, dbAdapter, List.empty)
      }
    }
}
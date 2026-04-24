package actors

import akka.actor.typed._
import akka.actor.typed.scaladsl._
import messages._
import objects._
import scala.concurrent.duration._

object ValidatorActor {

  val DIFFICULTY = "caca"

  def apply(mempool: ActorRef[Mempool.Command], db: ActorRef[DB.Command]): Behavior[Validator.Command] =
    Behaviors.setup { ctx =>
      Behaviors.withTimers { timers =>
        timers.startTimerWithFixedDelay(Validator.StartMining, 5.seconds)

        val txResponseAdapter = ctx.messageAdapter[Mempool.Txs](response => Validator.ProcessBlock(response.txs))

        val dbResponseAdapter = ctx.messageAdapter[DB.LastBlockInfo] {
          case DB.LastBlockInfo(hash, id) => Validator.ProcessMining(hash.toString, id.toString.toInt)
        }

        //new matias => nouvel adapter pour accepter les retours de la DB pour la création
        val dbStatusAdapter = ctx.messageAdapter[DB.Response] {
          case DB.Success => Validator.ConfirmSaved
          case DB.Failed(err) => Validator.SaveFailed(err)
        }

        behavior(mempool, db, txResponseAdapter, dbResponseAdapter, dbStatusAdapter, List.empty)
      }
    }

  private def behavior(
      mempool: ActorRef[Mempool.Command],
      db: ActorRef[DB.Command],
      txAdapter: ActorRef[Mempool.Txs],
      dbAdapter: ActorRef[DB.LastBlockInfo],
      dbStatusAdapter: ActorRef[DB.Response], // Ajouté ici
      pendingTxs: List[SignedTransaction]
  ): Behavior[Validator.Command] =
    Behaviors.receive { (ctx, msg) =>
      msg match {
        case Validator.StartMining =>
          ctx.log.info("Validator : Check Mempool...")
          mempool ! Mempool.GetTxs(txAdapter)
          Behaviors.same

        case Validator.ProcessBlock(txs) =>
          if (txs.isEmpty) Behaviors.same
          else {
            db ! DB.GetLastBlock(dbAdapter)
            behavior(mempool, db, txAdapter, dbAdapter, dbStatusAdapter, txs)
          }

        case Validator.ProcessMining(lastHash, currentId) =>
          ctx.log.info(s"Validator : ⛏️ Minage...")
          val timestamp = System.currentTimeMillis()
          val txsData = pendingTxs.map(_.txId).mkString("-")
          val (nonce, blockHash) = functions.Miner.mine(txsData, lastHash, timestamp, 0, DIFFICULTY)

          //ctx.log.info(s"Validator : Bloc miné ! Envoi à la DB...")
          ctx.log.info(s"Validator : ⛏️ BLOC MINÉ ! Nonce trouvé : $nonce | Hash : $blockHash")
          val newBlock = Block(currentId, pendingTxs, lastHash, timestamp)

          // On passe l'adapter à la DB pour qu'elle puisse répondre
          db ! DB.SaveBlock(newBlock, dbStatusAdapter) 
          
          // IMPORTANT : On change d'état vers l'attente
          waitingForDbState(mempool, db, txAdapter, dbAdapter, dbStatusAdapter, pendingTxs)

        case _ => Behaviors.same
      }
    }

  private def waitingForDbState(
      mempool: ActorRef[Mempool.Command],
      db: ActorRef[DB.Command],
      txAdapter: ActorRef[Mempool.Txs],
      dbAdapter: ActorRef[DB.LastBlockInfo],
      dbStatusAdapter: ActorRef[DB.Response],
      pendingTxs: List[SignedTransaction]
  ): Behavior[Validator.Command] =
    Behaviors.receive { (ctx, msg) =>
      msg match {
        case Validator.ConfirmSaved =>
          ctx.log.info("Validator : Succès DB. Nettoyage mempool.")
          mempool ! Mempool.RemoveTxs(pendingTxs)
          behavior(mempool, db, txAdapter, dbAdapter, dbStatusAdapter, List.empty)

        case Validator.SaveFailed(err) =>
          ctx.log.error(s"Validator : Échec DB ($err).")
          behavior(mempool, db, txAdapter, dbAdapter, dbStatusAdapter, List.empty)

        case _ =>
          // On ignore les nouveaux StartMining tant qu'on n'a pas fini d'enregistrer
          Behaviors.same
      }
    }
}
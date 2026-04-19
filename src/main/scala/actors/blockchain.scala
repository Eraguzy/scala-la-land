package actors

import akka.actor.typed._
import akka.actor.typed.scaladsl._
import messages._
import java.io.{FileWriter, PrintWriter}
import scala.util.{Failure, Success, Using}

object DBActor {

  // Stratégie de supervision pour gérer les erreurs
  def apply(): Behavior[DB.Command] =
    Behaviors.supervise(behavior("000", 0))
      .onFailure[Exception](SupervisorStrategy.resume)

  private def behavior(lastHash: String, currentId: Int): Behavior[DB.Command] =
    Behaviors.receive { (ctx, msg) =>
      msg match {
        case DB.GetLastBlock(replyTo) =>
          replyTo ! DB.LastBlockInfo(lastHash, currentId)
          Behaviors.same

        case DB.AppendBlock(block, replyTo) =>
          
          // Gestion sécurisée des ressources I/O
          val writeResult = Using(new PrintWriter(new FileWriter("ledger.txt", true))) { writer =>
            writer.println(s"BLOCK|ID:$currentId|PREV:$lastHash|TS:${block.timestamp}")
            block.transactions.foreach { tx =>
              writer.println(s"TX|${tx.tx.from}|${tx.tx.to}|${tx.tx.amount}")
            }
            writer.println("---")
          }

          // Gestion du succès ou échec de l'écriture
          writeResult match {
            case Success(_) =>
              ctx.log.info(s"DB : Bloc $currentId sauvegardé avec succès.")
              replyTo ! DB.Success
              
              val newHash = block.toString
              behavior(newHash, currentId + 1)

            case Failure(exception) =>
              ctx.log.error(s"Erreur I/O critique lors de l'écriture du bloc $currentId: ${exception.getMessage}")
              
              replyTo ! DB.Failed(s"I/O Error: ${exception.getMessage}")
              
              Behaviors.same
          }

        case DB.SaveBlock(block) =>
          // Ajout du message SaveBlock pour sauvegarder sans réponse
          val writeResult = Using(new PrintWriter(new FileWriter("ledger.txt", true))) { writer =>
            writer.println(s"BLOCK|ID:$currentId|PREV:$lastHash|TS:${block.timestamp}")
            block.transactions.foreach { tx =>
              writer.println(s"TX|${tx.tx.from}|${tx.tx.to}|${tx.tx.amount}")
            }
            writer.println("---")
          }

          writeResult match {
            case Success(_) =>
              ctx.log.info(s"DB : Bloc $currentId sauvegardé avec succès.")
              val newHash = block.toString
              behavior(newHash, currentId + 1)

            case Failure(exception) =>
              ctx.log.error(s"Erreur I/O critique lors de l'écriture du bloc $currentId: ${exception.getMessage}")
              Behaviors.same
          }
      }
    }
}
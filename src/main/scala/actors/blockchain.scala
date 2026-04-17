package actors

import akka.actor.typed._
import akka.actor.typed.scaladsl._
import messages._
import java.io.{FileWriter, PrintWriter}
import scala.io.Source

object DBActor {
  def apply(): Behavior[DB.Command] = behavior("000") // Hash initial (Genesis)

  private def behavior(lastHash: String): Behavior[DB.Command] =
    Behaviors.receive { (ctx, msg) =>
      msg match {
        case DB.GetLastHash(replyTo) =>
          replyTo ! lastHash
          Behaviors.same

        case DB.SaveBlock(block, replyTo) =>
          // Simulation simplifiée de l'écriture dans un fichier texte
          try {
            val writer = new PrintWriter(new FileWriter("ledger.txt", true))
            writer.println(s"BLOCK|${block.id}|${block.previousHash}|${block.timestamp}")
            writer.close()

            ctx.log.info(s"DB : Bloc ${block.id} sauvegardé dans ledger.txt")
            replyTo ! DB.Success
            behavior(functions.Crypto.sha256(block.toString)) // On met à jour le hash local
          } catch {
            case e: Exception =>
              replyTo ! DB.Failed(e.getMessage)
              Behaviors.same
          }
      }
    }
}
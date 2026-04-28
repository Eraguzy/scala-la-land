package actors

import akka.actor.typed._
import akka.actor.typed.scaladsl._
import messages._
import java.io.{FileWriter, PrintWriter}
import scala.util.{Failure, Success, Using}

object DBActor {

  // Supervision strategy: resume the actor instead of crashing on exception
  def apply(): Behavior[DB.Command] = {
    val file = new java.io.File("ledger.txt")
    if (file.exists()) file.delete()

    Behaviors.supervise(behavior("000", 0))
      .onFailure[Exception](SupervisorStrategy.resume)
  }

  private def behavior(lastHash: String, currentId: Int): Behavior[DB.Command] =
    Behaviors.receive { (ctx, msg) =>
      msg match {
        case DB.GetLastBlock(replyTo) =>
          replyTo ! DB.LastBlockInfo(lastHash, currentId)
          Behaviors.same

        case DB.AppendBlock(block, replyTo) =>
          
          // Safe I/O resource management using a Using block
          val writeResult = Using(new PrintWriter(new FileWriter("ledger.txt", true))) { writer =>
            writer.println(s"BLOCK|ID:$currentId|PREV:$lastHash|TS:${block.timestamp}")
            block.transactions.foreach { tx =>
              writer.println(s"TX|${tx.tx.from}|${tx.tx.to}|${tx.tx.amount}")
            }
            writer.println("---")
          }

          // Handle write outcome (success or failure)
          writeResult match {
            case Success(_) =>
              ctx.log.info(s"DB: Block $currentId successfully saved.")
              replyTo ! DB.Success
              
              val newHash = functions.Crypto.sha256(block.toString)
              behavior(newHash, currentId + 1)

            case Failure(exception) =>
              ctx.log.error(s"Critical I/O error writing block $currentId: ${exception.getMessage}")
              
              replyTo ! DB.Failed(s"I/O Error: ${exception.getMessage}")
              

              Behaviors.same
          }

        case DB.SaveBlock(block, replyTo) =>
          // Fire-and-forget block saving
          val writeResult = Using(new PrintWriter(new FileWriter("ledger.txt", true))) { writer =>
            writer.println(s"BLOCK|ID:$currentId|PREV:$lastHash|TS:${block.timestamp}")
            block.transactions.foreach { tx =>
              writer.println(s"TX|${tx.tx.from}|${tx.tx.to}|${tx.tx.amount}")
            }
            writer.println("---")
          }

          writeResult match {
            case Success(_) =>
              ctx.log.info(s"DB: Block $currentId successfully saved.")
              replyTo ! DB.Success //new Matias => we need to warn the validator that everything went well
              val newHash = functions.Crypto.sha256(block.toString)
              behavior(newHash, currentId + 1)

            case Failure(exception) =>
              ctx.log.error(s"Critical I/O error writing block $currentId: ${exception.getMessage}")
              replyTo ! DB.Failed(exception.getMessage) // we got to warn the validator the error
              Behaviors.same
          }
      
        case DB.GetBalanceAtDate(publicKey, targetTimestamp, replyTo) =>
          var balance = 0.0
          var currentBlockTimestamp = 0L

          try {
            val file = new java.io.File("ledger.txt")
            if (file.exists()) {
              val source = scala.io.Source.fromFile(file)
              
              source.getLines().foreach { line =>
                if (line.startsWith("BLOCK|")) {
                  // Expected format: BLOCK|ID:1|PREV:xyz|TS:1680000000
                  val parts = line.split("\\|")
                  val tsString = parts.find(_.startsWith("TS:")).getOrElse("TS:0").replace("TS:", "")
                  currentBlockTimestamp = tsString.toLong
                  
                } else if (line.startsWith("TX|") && currentBlockTimestamp <= targetTimestamp) {
                  // Expected format: TX|sender_pk|receiver_pk|amount
                  val parts = line.split("\\|")
                  if (parts.length >= 4) {
                    val sender = parts(1)
                    val receiver = parts(2)
                    
                    // Safely parse double to avoid runtime exceptions
                    val amount = scala.util.Try(parts(3).toDouble).getOrElse(0.0)

                    if (sender == publicKey) balance -= amount
                    if (receiver == publicKey) balance += amount
                  }
                }
              }
              source.close()
            }
            
            // Send back the computed balance
            replyTo ! DB.BalanceResponse(balance)
            
          } catch {
            case e: Exception =>
              ctx.log.error(s"Error calculating balance for $publicKey: ${e.getMessage}")
              // Return 0.0 as a fallback on error
              replyTo ! DB.BalanceResponse(0.0)
          }
          Behaviors.same
      
      }
    }
}
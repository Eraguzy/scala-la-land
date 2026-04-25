package messages
import akka.actor.typed.ActorRef
import objects._
import objects.PendingTx

object Validator {
  sealed trait Command
  case object StartMining extends Command
  case class ProcessBlock(txs: List[PendingTx]) extends Command
  case class ProcessMining(lastHash: String, currentId: Int) extends Command

  // DB response
  case object ConfirmSaved extends Validator.Command
  case class SaveFailed(error: String) extends Validator.Command
}

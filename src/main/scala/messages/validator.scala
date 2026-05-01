package messages

import akka.actor.typed.ActorRef
import objects._
import objects.PendingTx

object Validator {
  sealed trait Command

  case object StartMining extends Command                      // fired by the timer every 5s
  case class ProcessBlock(txs: List[PendingTx]) extends Command
  case class ProcessMining(lastHash: String, currentId: Int) extends Command

  // these two are DB responses, but routed through a message adapter so they
  // arrive as Validator.Command — that's how Akka Typed handles cross-actor replies
  case object ConfirmSaved extends Validator.Command
  case class SaveFailed(error: String) extends Validator.Command
}

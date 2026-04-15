package blockchain

import akka.actor.{Actor, ActorRef => AkkaActorRef, ActorSystem, Props}
import scala.concurrent.duration._
import scala.concurrent.{Await, Promise}
import scala.util.control.NonFatal

// ActorSystem local partagé par toutes les exécutions CLI.
// Il reste volontairement unique afin de limiter les coûts de création/destruction.
object AkkaActorSupport {
  val system: ActorSystem = ActorSystem("blockchain-system")
}

// Façade de compatibilité: le code métier continue d'utiliser ActorRef.ask(...)
// sans dépendre directement de l'API Akka dans tous les fichiers.
final class ActorRef[M] private[blockchain] (
    val name: String,
    private val akkaRef: AkkaActorRef
) {
  def !(message: M): Unit = akkaRef ! message

  // API synchrone conservée: on construit un message contenant un Promise,
  // puis on attend sa résolution avec timeout.
  def ask[A](builder: Promise[A] => M, timeout: FiniteDuration = 5.seconds): A = {
    val promise = Promise[A]()
    this ! builder(promise)
    Await.result(promise.future, timeout)
  }

  private[blockchain] def stop(): Unit =
    AkkaActorSupport.system.stop(akkaRef)
}

abstract class SimpleActor[M](name: String) {
  // Les noms Akka doivent être sûrs (caractères autorisés) et uniques.
  // On normalise le nom puis on ajoute un suffixe UUID.
  private val actorName = {
    val base = name
      .toLowerCase
      .map {
        case c if c.isLetterOrDigit => c
        case _                      => '-'
      }
      .mkString
      .stripPrefix("-")
      .stripSuffix("-")
    val safeBase = if (base.nonEmpty) base else "actor"
    val suffix = java.util.UUID.randomUUID().toString.replace("-", "")
    s"$safeBase-$suffix"
  }

  private val akkaRef: AkkaActorRef = AkkaActorSupport.system.actorOf(
    Props(
      new Actor {
        override def receive: Receive = {
          case message =>
            try {
              // Le cast interne est sûr dans ce projet: seule cette façade envoie des M.
              SimpleActor.this.receive(message.asInstanceOf[M])
            } catch {
              case NonFatal(error) =>
                System.err.println(s"[actor:$name] ${error.getMessage}")
                error.printStackTrace()
            }
        }
      }
    ),
    actorName
  )

  val ref: ActorRef[M] = new ActorRef[M](name, akkaRef)

  def stop(): Unit = {
    ref.stop()
  }

  protected def receive(message: M): Unit
}

object SimpleActor {
  // Fonction utilitaire de fermeture utilisee par ActorRuntime.shutdown() pour arreter
  // proprement tous les acteurs d'une exécution.
  def stopAll(refs: Iterable[ActorRef[_]]): Unit = {
    refs.foreach(_.stop())
  }
}

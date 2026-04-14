package blockchain

import java.util.concurrent.LinkedBlockingQueue
import scala.concurrent.duration._
import scala.concurrent.{Await, Promise}
import scala.util.control.NonFatal

final class ActorRef[M] private[blockchain] (
    val name: String,
    private val mailbox: LinkedBlockingQueue[M]
) {
  def !(message: M): Unit = mailbox.put(message)

  def ask[A](builder: Promise[A] => M, timeout: FiniteDuration = 5.seconds): A = {
    val promise = Promise[A]()
    this ! builder(promise)
    Await.result(promise.future, timeout)
  }
}

abstract class SimpleActor[M](name: String) {
  private val mailbox = new LinkedBlockingQueue[M]()
  val ref: ActorRef[M] = new ActorRef[M](name, mailbox)

  private val worker = new Thread(
    new Runnable {
      override def run(): Unit = runLoop()
    },
    s"actor-$name"
  )

  worker.setDaemon(true)
  worker.start()

  private def runLoop(): Unit = {
    while (true) {
      val message = mailbox.take()
      try {
        receive(message)
      } catch {
        case _: InterruptedException =>
          Thread.currentThread().interrupt()
          return
        case NonFatal(error) =>
          System.err.println(s"[actor:$name] ${error.getMessage}")
          error.printStackTrace()
      }
    }
  }

  protected def receive(message: M): Unit
}

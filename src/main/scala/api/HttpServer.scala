package api

import akka.actor.typed._
import akka.actor.typed.scaladsl.adapter.TypedActorSystemOps
import akka.http.scaladsl.Http
import messages._
import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}

object HttpServer {

  def start(
      registry: ActorRef[Registry.Command],
      db: ActorRef[DB.Command],
      mempool: ActorRef[Mempool.Command],
      host: String = "0.0.0.0",
      port: Int = 8080
  )(implicit system: ActorSystem[_]): Unit = {

    // Akka HTTP still runs on the classic actor system internally,
    // so we need to convert the typed one before passing it
    implicit val classicSystem: akka.actor.ActorSystem = system.toClassic
    implicit val ec: ExecutionContext                  = system.executionContext

    val routes = new Routes(registry, db, mempool).routes

    Http().newServerAt(host, port).bind(routes).onComplete {
      case Success(binding) =>
        val addr = binding.localAddress
        println(s"")
        println(s"  --------------------------------------------")
        println(s"  -  Blockchain HTTP Server started          -")
        println(s"  -  http://localhost:${addr.getPort}        -")
        println(s"  --------------------------------------------")
        println(s"")
      case Failure(ex) =>
        println(s"[ERROR] Failed to start HTTP server: ${ex.getMessage}")
        system.terminate()
    }
  }
}

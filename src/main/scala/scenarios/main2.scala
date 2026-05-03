/*
package blockchain

import akka.actor.typed._
import akka.actor.typed.scaladsl._
import actors._
import messages._
import api.HttpServer

object Main {
  def main(args: Array[String]): Unit = {

    val system = ActorSystem(
      Behaviors.setup[Nothing] { ctx =>
        // Infrastructure
        val db = ctx.spawn(DBActor(), "blockchain-db")
        val mempool = ctx.spawn(MempoolActor(), "mempool")
        ctx.spawn(ValidatorActor(mempool, db), "validator")

        // Wallet registry (manages wallets created via the HTTP API)
        val registry = ctx.spawn(RegistryActor(db, mempool), "wallet-registry")

        // Start HTTP server on port 8080
        HttpServer.start(registry, db, mempool)(ctx.system)

        Behaviors.empty
      },
      "blockchain-system"
    )

    println(">>> Press ENTER to stop <<<")
    scala.io.StdIn.readLine()
    system.terminate()
  }
}
*/


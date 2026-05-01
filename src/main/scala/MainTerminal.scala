package blockchain

import akka.actor.typed._
import akka.actor.typed.scaladsl._
import akka.actor.typed.scaladsl.AskPattern._
import akka.util.Timeout
import actors._
import messages._
import scala.concurrent.{Await, Promise}
import scala.concurrent.duration._

/**
 * Terminal mode — no HTTP server, no frontend.
 * Runs a hardcoded scenario with Alice and Charlie, prints results to stdout.
 *
 * Run with:  sbt "runMain blockchain.MainTerminal"
 */
object MainTerminal {

  def main(args: Array[String]): Unit = {

    println(
      """|
         |  =========================================
         |   Blockchain  |  Terminal Mode
         |  =========================================
         |
         |  Wallets created:
         |    Alice   - initial balance: 500 SAT
         |    Charlie - initial balance: 300 SAT
         |""".stripMargin
    )

    // Promises so we can retrieve actor refs from outside Behaviors.setup
    val aliceRef   = Promise[ActorRef[Wallet.Command]]()
    val charlieRef = Promise[ActorRef[Wallet.Command]]()

    val system = ActorSystem(
      Behaviors.setup[Nothing] { ctx =>
        val db      = ctx.spawn(DBActor(),            "blockchain-db")
        val mempool = ctx.spawn(MempoolActor(),       "mempool")
        ctx.spawn(ValidatorActor(mempool, db),        "validator")

        // Two wallets with valid (non-negative) initial balances
        val alice   = ctx.spawn(WalletActor("Alice",   500, mempool, db), "Alice")
        val charlie = ctx.spawn(WalletActor("Charlie", 300, mempool, db), "Charlie")

        aliceRef.success(alice)
        charlieRef.success(charlie)

        val resultReceiver = ctx.spawnAnonymous(
          Behaviors.receiveMessage[Boolean] { ok =>
            println(s"     result -> ${if (ok) "CONFIRMED" else "REJECTED"}")
            Behaviors.same
          }
        )

        // Alice sends 3 transactions to Charlie with different fees
        val charliePkReceiver = ctx.spawnAnonymous(
          Behaviors.receiveMessage[String] { pk =>
            println("  Sending Alice -> Charlie ...")
            println("    (fee:4)  amount: 2 SAT")
            alice ! Wallet.CreateTx(pk, 2, 4, resultReceiver)
            println("    (fee:3)  amount: 50 SAT")
            alice ! Wallet.CreateTx(pk, 50, 3, resultReceiver)
            println("    (fee:2)  amount: 300 SAT  [may be rejected: exceeds balance after fees]")
            alice ! Wallet.CreateTx(pk, 300, 2, resultReceiver)
            Behaviors.stopped
          }
        )
        charlie ! Wallet.GetPublicKey(charliePkReceiver)

        // Charlie sends 2 transactions to Alice with different fees
        val alicePkReceiver = ctx.spawnAnonymous(
          Behaviors.receiveMessage[String] { pk =>
            println("\n  Sending Charlie -> Alice ...")
            println("    (fee:1)  amount: 10 SAT")
            charlie ! Wallet.CreateTx(pk, 10, 1, resultReceiver)
            println("    (fee:5)  amount: 100 SAT  [higher fee = higher priority in queue]")
            charlie ! Wallet.CreateTx(pk, 100, 5, resultReceiver)
            Behaviors.stopped
          }
        )
        alice ! Wallet.GetPublicKey(alicePkReceiver)

        Behaviors.empty
      },
      "blockchain-system"
    )

    println(
      """|
         |  The validator mines the top 2 transactions (by fee) every 5 seconds.
         |  Watch the logs above for "block mined" messages.
         |
         |  Press ENTER to print final balances and stop.
         |""".stripMargin
    )
    scala.io.StdIn.readLine()

    println("  Checking final balances...")

    val alice   = Await.result(aliceRef.future,   5.seconds)
    val charlie = Await.result(charlieRef.future, 5.seconds)

    implicit val timeout: Timeout           = Timeout(10.seconds)
    implicit val scheduler: Scheduler = system.scheduler

    val aliceBal   = Await.result(alice.ask[BigInt](replyTo => Wallet.GetBalance(replyTo)),   10.seconds)
    val charlieBal = Await.result(charlie.ask[BigInt](replyTo => Wallet.GetBalance(replyTo)), 10.seconds)

    println(s"\n  Alice   final balance: $aliceBal SAT")
    println(s"  Charlie final balance: $charlieBal SAT")
    println("\n  Shutting down...\n")
    system.terminate()
  }
}

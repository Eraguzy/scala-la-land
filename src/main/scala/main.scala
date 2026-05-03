

package blockchain

import akka.actor.typed._
import akka.actor.typed.scaladsl._
import actors._
import messages._
import scala.io.StdIn

object Main {
  def main(args: Array[String]): Unit = {

    val system = ActorSystem(
      Behaviors.setup[Nothing] { ctx =>

        // Infrastructure
        val db = ctx.spawn(DBActor(), "blockchain-db")
        val mempool = ctx.spawn(MempoolActor(), "mempool")
        ctx.spawn(ValidatorActor(mempool, db), "validator")

        // Wallets
        val charlie = ctx.spawn(WalletActor("charlie", 300, mempool, db), "Charlie")
        val alice = ctx.spawn(WalletActor("alice", 500, mempool, db), "Alice")

        // Receiver pour voir le résultat final
        val txResultReceiver = ctx.spawnAnonymous(
          Behaviors.receiveMessage[Boolean] { accepted =>
            println(s"[transfer_success] transaction status: $accepted")
            Behaviors.same
          }
        )

        // On récupère la clé publique d'Alice puis Charlie lui envoie une transaction
        val alicePubKeyReceiver = ctx.spawnAnonymous(
          Behaviors.receiveMessage[String] { alicePubKey =>
            println("[transfer_success] Charlie envoie 50 avec 3 de frais vers Alice.")
            charlie ! Wallet.CreateTx(alicePubKey, 50, 3, txResultReceiver)
            Behaviors.stopped
          }
        )

        // Lancement du scénario
        alice ! Wallet.GetPublicKey(alicePubKeyReceiver)

        Behaviors.same
      },
      "blockchain-system"
    )

    println(">>> Appuie sur ENTRÉE pour arrêter la simulation <<<")
    StdIn.readLine()
    system.terminate()
  }
}

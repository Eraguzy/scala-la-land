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

        // Receiver pour voir le résultat final de chaque TX
        val txResultReceiver = ctx.spawnAnonymous(
          Behaviors.receiveMessage[Boolean] { accepted =>
            if (accepted) {
              println(s"[tx_status] ✅ Transaction validée et intégrée au bloc !")
            } else {
              println(s"[tx_status] ❌ Transaction REFUSÉE (Fonds insuffisants ou erreur).")
            }
            Behaviors.same
          }
        )

        // CORRECTION : On utilise Behaviors.receive pour avoir accès au 'innerCtx'
        val alicePubKeyReceiver = ctx.spawnAnonymous(
          Behaviors.receive[String] { (innerCtx, alicePubKey) =>

            // On utilise innerCtx au lieu de ctx pour spawner le prochain acteur !
            val charliePubKeyReceiver = innerCtx.spawnAnonymous(
              Behaviors.receiveMessage[String] { charliePubKey =>

                println("==================================================")
                println("[scenario] Lancement de la rafale de transactions !")
                println("==================================================")

                // --- Les 5 transactions valides (créeront 3 blocs : 2, 2 et 1) ---
                
                // Vague 1 (Sera dans le Bloc 0)
                alice ! Wallet.CreateTx(charliePubKey, 50, 2, txResultReceiver)
                charlie ! Wallet.CreateTx(alicePubKey, 20, 1, txResultReceiver)

                // Vague 2 (Sera dans le Bloc 1)
                alice ! Wallet.CreateTx(charliePubKey, 10, 1, txResultReceiver)
                charlie ! Wallet.CreateTx(alicePubKey, 5, 1, txResultReceiver)

                // Vague 3 (Sera dans le Bloc 2)
                alice ! Wallet.CreateTx(charliePubKey, 100, 5, txResultReceiver)

                // --- La transaction invalide (Fonds insuffisants) ---
                // Alice essaie d'envoyer 1000 alors qu'elle a commencé avec 500 
                // et en a déjà dépensé pas mal au-dessus.
                alice ! Wallet.CreateTx(charliePubKey, 1000, 10, txResultReceiver)

                Behaviors.stopped
              }
            )
            
            // On demande la clé de Charlie
            charlie ! Wallet.GetPublicKey(charliePubKeyReceiver)
            Behaviors.stopped
          }
        )

        // Lancement du scénario en demandant la clé d'Alice en premier
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
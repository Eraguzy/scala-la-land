package blockchain

import akka.actor.typed._
import akka.actor.typed.scaladsl._
import actors._
import messages._
import scala.concurrent.duration._
import scala.io.StdIn

object Main {
  def main(args: Array[String]): Unit = {

    val system = ActorSystem(
      //   Behaviors.setup[Nothing] { ctx =>
      //     // 1. Initialisation des infrastructures critiques
      //     val db = ctx.spawn(DBActor(), "blockchain-db")
      //     val mempool = ctx.spawn(MempoolActor(), "mempool")

      //     // 2. Création de plusieurs Wallets avec des soldes différents
      //     val alice = ctx.spawn(
      //       WalletActor("alice", 500, mempool, db),
      //       "Alice"
      //     )
      //     val bob =
      //       ctx.spawn(WalletActor("bob", 100, mempool, db), "Bob")
      //     val charlie = ctx.spawn(
      //       WalletActor("charlie", 50, mempool, db),
      //       "Charlie"
      //     )

      //     // 3. Le Validator (qui va scanner la mempool toutes les X secondes)
      //     ctx.spawn(ValidatorActor(mempool, db), "validator")

      //     // --- SCÉNARIO DE CHALLENGE ---

      //     // A. Test de la Priority Queue : Alice envoie 3 transactions coup sur coup
      //     // On varie les fees : la transaction de 100 (fee 50) doit passer AVANT celle de 10 (fee 5)
      //     alice ! Wallet.CreateTx("bob_pk", 10, 1) // Fee par défaut (ex: 10)

      //     // B. Transaction "Prioritaire" : Gros montant et gros frais
      //     alice ! Wallet.CreateTx(
      //       "charlie_pk",
      //       100,
      //       1
      //     ) // On imagine ici que tu as ajouté un champ fee ou qu'il est géré

      //     // C. Test de l'Invariant : Bob tente d'envoyer 150 alors qu'il n'a que 100
      //     // L'acteur Wallet doit bloquer la transaction et logger une erreur.
      //     bob ! Wallet.CreateTx("alice_pk", 150, 1)

      //     // D. Flux croisé : Charlie envoie à Alice pendant que le Validator travaille
      //     ctx.scheduleOnce(1.second, charlie, Wallet.CreateTx("alice_pk", 20, 1))

      //     Behaviors.empty
      //   },
      //   "blockchain-system"
      // )

      Behaviors.setup[Nothing] { ctx =>
        // infrastructure
        val db = ctx.spawn(DBActor(), "blockchain-db")
        val mempool = ctx.spawn(MempoolActor(), "mempool")
        ctx.spawn(ValidatorActor(mempool, db), "validator")
        // wallets
        val charlie =
          ctx.spawn(WalletActor("charlie", 50, mempool, db), "Charlie")
        val alice = ctx.spawn(WalletActor("alice", 500, mempool, db), "Alice")

        // receivers for test
        val aliceBalanceBeforeReceiver = ctx.spawnAnonymous(
          Behaviors.receiveMessage[BigInt] { balance =>
            println(s"Alice balance BEFORE tx: $balance")
            Behaviors.stopped
          }
        )
        val aliceBalanceAfterReceiver = ctx.spawnAnonymous(
          Behaviors.receiveMessage[BigInt] { balance =>
            println(s"Alice balance AFTER tx: $balance")
            Behaviors.same
          }
        )
        // once confirmed we can check alice's balance again
        val txResultReceiver = ctx.spawnAnonymous(
          Behaviors.receiveMessage[Boolean] { accepted =>
            println(s"Alice tx accepted: $accepted")
            alice ! Wallet.GetBalance(aliceBalanceAfterReceiver)
            Behaviors.same
          }
        )

        // trigger receivers
        alice ! Wallet.GetBalance(aliceBalanceBeforeReceiver)
        // get charlie's key, then alice sends a tx to charlie
        val charliePubKeyReceiver = ctx.spawnAnonymous(
          Behaviors.receiveMessage[String] { charliePubKey =>
            alice ! Wallet.CreateTx(charliePubKey, 50, 1, txResultReceiver)
            alice ! Wallet.CreateTx(charliePubKey, 441, 1, txResultReceiver)
            alice ! Wallet.CreateTx(charliePubKey, 2, 1, txResultReceiver)
            Behaviors.stopped
          }
        )
        charlie ! Wallet.GetPublicKey(charliePubKeyReceiver)

        Behaviors.same
      },
      "blockchain-system"
    )
    println(">>> Appuie sur ENTRÉE pour arrêter la simulation <<<")
    StdIn.readLine()
  }
}

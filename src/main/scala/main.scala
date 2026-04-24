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
        // 1. Infrastructure
        val db = ctx.spawn(DBActor(), "blockchain-db")
        val mempool = ctx.spawn(MempoolActor(), "mempool")
        ctx.spawn(ValidatorActor(mempool, db), "validator")

        // 2. Wallets
        val charlie = ctx.spawn(WalletActor("charlie", 300, mempool, db), "Charlie")
        val alice = ctx.spawn(WalletActor("alice", 500, mempool, db), "Alice")

        // 3. Receivers pour le monitoring
        val genericResultReceiver = ctx.spawnAnonymous(
          Behaviors.receiveMessage[Boolean] { accepted =>
            println(s"Transaction status: $accepted")
            Behaviors.same
          }
        )

        val balanceReceiver = ctx.spawnAnonymous(
          Behaviors.receiveMessage[BigInt] { balance =>
            println(s"Current balance check: $balance")
            Behaviors.same
          }
        )

        // --- EXÉCUTION DU SCÉNARIO ---

        // A. Alice vers Charlie (Tes 3 transactions initiales)
        val charliePubKeyReceiver = ctx.spawnAnonymous(
          Behaviors.receiveMessage[String] { charliePubKey =>
            println("Alice initie 3 transactions vers Charlie...")
            alice ! Wallet.CreateTx(charliePubKey, 50, 3, genericResultReceiver)
            alice ! Wallet.CreateTx(charliePubKey, 300, 2, genericResultReceiver)
            alice ! Wallet.CreateTx(charliePubKey, 2, 4, genericResultReceiver)
            Behaviors.stopped
          }
        )
        charlie ! Wallet.GetPublicKey(charliePubKeyReceiver)

        // B. Charlie vers Alice (Les 2 nouvelles transactions)
        val alicePubKeyReceiver = ctx.spawnAnonymous(
          Behaviors.receiveMessage[String] { alicePubKey =>
            // On attend un court instant ou on lance directement
            // Transaction 1 : Petit montant, frais normaux
            charlie ! Wallet.CreateTx(alicePubKey, 10, 1, genericResultReceiver)

            // Transaction 2 : Tentative d'envoyer plus que son solde initial (Test de sécurité)
            // Note : Charlie n'a que 50 au départ.
            charlie ! Wallet.CreateTx(alicePubKey, 100, 5, genericResultReceiver)
            Behaviors.stopped
          }
        )

        // On récupère la clé d'Alice pour que Charlie puisse lui envoyer de l'argent
        alice ! Wallet.GetPublicKey(alicePubKeyReceiver)

        Behaviors.same
      },


      "blockchain-system"
    )
    println(">>> Appuie sur ENTRÉE pour arrêter la simulation <<<")
    StdIn.readLine()
  }
}

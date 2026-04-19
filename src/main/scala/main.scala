package blockchain

import akka.actor.typed._
import akka.actor.typed.scaladsl._
import actors._
import messages._
import scala.concurrent.duration._

object Main {
  def main(args: Array[String]): Unit = {

    val system = ActorSystem(
      Behaviors.setup[Nothing] { ctx =>
        // 1. Initialisation des infrastructures critiques
        val db = ctx.spawn(DBActor(), "blockchain-db")
        val mempool = ctx.spawn(MempoolActor(), "mempool")

        // 2. Création de plusieurs Wallets avec des soldes différents
        val alice = ctx.spawn(
          WalletActor("alice", 500, mempool),
          "Alice"
        )
        val bob =
          ctx.spawn(WalletActor("bob", 100, mempool), "Bob")
        val charlie = ctx.spawn(
          WalletActor("charlie", 50, mempool),
          "Charlie"
        )

        // 3. Le Validator (qui va scanner la mempool toutes les X secondes)
        ctx.spawn(ValidatorActor(mempool, db), "validator")

        // --- SCÉNARIO DE CHALLENGE ---

        // A. Test de la Priority Queue : Alice envoie 3 transactions coup sur coup
        // On varie les fees : la transaction de 100 (fee 50) doit passer AVANT celle de 10 (fee 5)
        alice ! Wallet.CreateTx("bob_pk", 10, 1) // Fee par défaut (ex: 10)

        // B. Transaction "Prioritaire" : Gros montant et gros frais
        alice ! Wallet.CreateTx(
          "charlie_pk",
          100,
          1
        ) // On imagine ici que tu as ajouté un champ fee ou qu'il est géré

        // C. Test de l'Invariant : Bob tente d'envoyer 150 alors qu'il n'a que 100
        // L'acteur Wallet doit bloquer la transaction et logger une erreur.
        bob ! Wallet.CreateTx("alice_pk", 150, 1)

        // D. Flux croisé : Charlie envoie à Alice pendant que le Validator travaille
        ctx.scheduleOnce(1.second, charlie, Wallet.CreateTx("alice_pk", 20, 1))

        Behaviors.empty
      },
      "blockchain-system"
    )
  }
}

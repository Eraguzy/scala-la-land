//
//
//
//package actors
//
//import akka.actor.typed._
//import akka.actor.typed.scaladsl._
//import messages._
//import objects._
//
//object MempoolActor {
//  //quand on spawn, on initialise la mempool avec une liste vide de transactions
//  def apply(): Behavior[Mempool.Command] = behavior(List.empty)
//
//
//  //fonction representant l'état de la memepool à un instant t, elle prend en paramètre la liste des transactions courante
//  //quand on reçoit une transaction, on crée une nouvelle mempool avec la nouvelle transaction ajoutée à la liste,
//  //et comme ça, on peut faire évoluer la mempool sans utiliser de variable mutable var
//  // -> gestion d'état par récursion
//  private def behavior(txs: List[SignedTransaction]): Behavior[Mempool.Command] =
//    Behaviors.receive { (ctx, msg) =>
//      msg match {
//
//        case Mempool.AddTx(tx) =>
//          ctx.log.info(s"Mempool : Transaction ajoutée à la file d'attente.")
//          behavior(tx :: txs) //on créer une nouvelle liste "Pour le prochain message, utilise une nouvelle version de moi-même avec cette liste mise à jour."
//
//        case Mempool.GetTxs(replyTo) =>
//          // Le Validator appelle ceci pour récupérer sa liste de travail
//          replyTo ! Mempool.Txs(txs)
//          Behaviors.same
//
//        case Mempool.RemoveTxs(confirmedTxs) =>
//          // --- LE NETTOYAGE ---
//          // On filtre la liste pour ne garder que ce qui n'a PAS été miné
//          val remaining = txs.filterNot(t => confirmedTxs.contains(t))
//          ctx.log.info(s"Mempool : Nettoyage effectué. ${remaining.size} transactions restantes.")
//          behavior(remaining)
//      }
//    }
//}
//
//// en akka, on essaye d'éviter les variables mutables var
//// mais notre mempool doit pouvoir stocker une liste de transactions,
//// on utilise donc une fonction récursive qui prend en paramètre la liste des transactions courante

package actors

import akka.actor.typed._
import akka.actor.typed.scaladsl._
import messages.Mempool
import objects.SignedTransaction
import functions.Crypto

object MempoolActor {

  def apply(): Behavior[Mempool.Command] = behavior(List.empty)

  private def behavior(txs: List[SignedTransaction]): Behavior[Mempool.Command] =
    Behaviors.receive { (ctx, msg) =>
      msg match {

        case Mempool.AddTx(signedTx) =>

          val isValid = Crypto.verify(
            signedTx.tx,
            signedTx.signature,
            signedTx.tx.from
          )

          if (isValid) {
            ctx.log.info(s"Mempool : Signature valide pour TX ${signedTx.tx.id}. Ajout à la Priority Queue.")

            // Simulation d'une Priority Queue : On ajoute et on trie par fees décroissants
            val updatedTxs = (signedTx :: txs).sortBy(_.tx.fees)(Ordering[BigInt].reverse)
            ctx.log.info("liste mise à jour : " + updatedTxs.map(_.tx.id).mkString(", "))
            behavior(updatedTxs)
          } else {
            ctx.log.error(s"Mempool : REJET ! Signature invalide pour TX ${signedTx.tx.id}.")
            Behaviors.same
          }

        //c'est le message reçu par la memepool de la part du validateur, en vue de récupérer les 2 premieres transactions pour constituer un bloc
        case Mempool.GetTxs(replyTo) =>
          // on sort les 2 premières tx
          val (toSend, rest) = txs.splitAt(2)

          if (toSend.nonEmpty) {
            ctx.log.info(s"Mempool : Envoi de ${toSend.size} transactions au Validator. Nettoyage de la file.")
            replyTo ! Mempool.Txs(toSend)
            behavior(rest) //on met à jour la mempool en retirant les tx envoyées au validator
          } else {
            ctx.log.info("Mempool : Demande reçue mais la file est vide.")
            replyTo ! Mempool.Txs(List.empty)
            Behaviors.same
          }

        case Mempool.RemoveTxs(confirmedTxs) =>
          // Ce message reste utile au cas où le Validator échoue et qu'on doive synchroniser
          val remaining = txs.filterNot(t => confirmedTxs.exists(_.tx.id == t.tx.id))
          ctx.log.info(s"Mempool : Nettoyage manuel demandé. Reste : ${remaining.size}")
          behavior(remaining)
      }
    }
}
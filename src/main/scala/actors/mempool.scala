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
import messages.Mempool // IMPORT PRÉCIS
import objects.SignedTransaction

object MempoolActor {
  def apply(): Behavior[Mempool.Command] = behavior(List.empty)

  private def behavior(txs: List[SignedTransaction]): Behavior[Mempool.Command] =
    Behaviors.receive { (ctx, msg) =>
      msg match {
        case Mempool.AddTx(tx) =>
          ctx.log.info(s"Mempool : Transaction ajoutée")
          behavior(tx :: txs)

        case Mempool.GetTxs(replyTo) =>
          replyTo ! Mempool.Txs(txs)
          //supprimer le ou les txs en question
          Behaviors.same

        case Mempool.RemoveTxs(confirmedTxs) =>
          val remaining = txs.filterNot(t => confirmedTxs.contains(t))
          behavior(remaining)
      }
    }
}
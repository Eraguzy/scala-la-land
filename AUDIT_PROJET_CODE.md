# Audit Codebase Akka/Scala (hors tests, hors LTL, hors Petri)

Date: 24 avril 2026

## 1) Conclusion rapide

- Statut global (partie code uniquement): bien avancee, mais pas finalisee.
- Couverture estimee pour la partie code du sujet: 65-70%.
- Niveau de resilience actuel: moyen (environ 5.5/10).

En pratique, vous avez une architecture claire et une simulation qui tourne, mais il reste des ecarts importants de fiabilite transactionnelle et d'integrite blockchain pour dire "100% fini" sur la partie implementation.

## 2) Ce qui est bien / fini / quasi fini

### Fini

- Architecture par acteurs propre et lisible (Wallet, Mempool, Validator, DB).
- Protocoles de messages types (sealed trait + case class), globalement coherents.
- Signature et verification cryptographique presentes.
- Pipeline metier present de bout en bout: creation tx -> mempool -> minage -> persistance.

### Quasi fini

- Gestion de concurrence cote Wallet: file interne pour traiter les demandes sequentiellement.
- Priorisation par frais dans la mempool.
- Supervision et gestion des erreurs I/O cote DB (bonne base).
- Scenario de simulation dans main, suffisant pour demo.

## 3) Problemes trouves (priorises)

### Critiques

1. Risque de perte de transactions
- Probleme: la mempool retire des transactions au moment du GetTxs (splitAt), avant confirmation d'ecriture DB.
- Impact: si la persistance echoue, des transactions peuvent etre perdues.

2. Accuse de succes trop tot
- Probleme: Wallet repond true juste apres envoi a la mempool, sans confirmation aval.
- Impact: faux positif metier (on annonce "accepte" alors que ce n'est pas commit).

3. Integrite blockchain incomplete
- Probleme: hash mine calcule mais non stocke dans le bloc; chainage DB base sur block.toString.
- Impact: chainage non deterministe/non fiable, verification de coherence affaiblie.

4. Gestion monetaire fragile
- Probleme: balances manipulees en Double cote DB.
- Impact: risque d'arrondis et d'erreurs metier (invariants financiers fragiles).

### Majeurs

5. Suppression du ledger au demarrage
- Probleme: fichier supprime a chaque lancement.
- Impact: pas de persistance durable hors mode demo.

6. Exposition de cle privee via API acteur
- Probleme: message GetPrivateKey expose une donnee sensible.
- Impact: risque securite majeur dans un systeme critique.

7. Minage potentiellement bloquant
- Probleme: preuve de travail lancee dans le flux de l'acteur Validator.
- Impact: degradation de reactivite sous charge.

8. Domaine incomplet
- Probleme: objet account.scala quasi vide.
- Impact: modele metier incomplet et difficile a faire evoluer.

### Mineurs

9. Quelques contrats pas totalement alignes sur semantics "exactement une fois"/idempotence.
10. Journalisation utile mais encore peu structuree pour audit operationnel.

## 4) Ce qu'il manque pour atteindre 100% (partie code)

1. Flux transactionnel fiable de bout en bout
- Ne retirer de mempool qu'apres confirmation DB.
- Distinguer "recu en mempool" et "confirme en bloc".

2. Integrite stricte de la chaine
- Ajouter nonce + blockHash dans Block.
- Chainer avec previousHash = dernier blockHash persiste.

3. Invariant metier robuste (solde jamais negatif)
- Type monetaire non flottant (BigInt/BigDecimal selon besoin).
- Source de verite claire pour le solde (eviter double comptage local).

4. Resilience operationnelle
- Politique de retry / requeue en cas d'echec DB.
- Ledger persistant par defaut (reset explicite seulement).

5. Durcissement securite
- Supprimer l'exposition de la cle privee.
- Limiter les donnees sensibles dans les logs.

## 5) Propositions de code concretes

Les blocs ci-dessous sont des propositions cibles (a adapter dans vos fichiers).

### A. Rendre la persistance atomique avec confirmation

Fichier cible: messages/blockchain.scala

~~~scala
object DB {
  sealed trait Command
  case class AppendBlock(block: Block, replyTo: ActorRef[Response]) extends Command
  case class GetLastBlock(replyTo: ActorRef[LastBlockInfo]) extends Command
  case class GetBalanceAtDate(publicKey: String, targetTimestamp: Long, replyTo: ActorRef[BalanceResponse]) extends Command

  case class LastBlockInfo(hash: String, id: Int)

  sealed trait Response
  case object Success extends Response
  case class Failed(reason: String) extends Response

  // Type monetaire sans flottant
  case class BalanceResponse(balance: BigInt)
}
~~~

Fichier cible: actors/validator.scala

~~~scala
// idee: persister puis seulement ensuite nettoyer mempool
case class PersistResult(res: DB.Response, minedTxs: List[SignedTransaction]) extends Validator.Command

val dbPersistAdapter: ActorRef[DB.Response] =
  ctx.messageAdapter(res => Validator.PersistResult(res, pendingTxs))

db ! DB.AppendBlock(newBlock, dbPersistAdapter)

case Validator.PersistResult(DB.Success, minedTxs) =>
  mempool ! Mempool.RemoveTxs(minedTxs)
  behavior(mempool, db, txAdapter, dbAdapter, List.empty)

case Validator.PersistResult(DB.Failed(reason), _) =>
  ctx.log.error(s"Persist failed: $reason")
  // on garde les tx en mempool pour retry (pas de suppression)
  Behaviors.same
~~~

### B. Eviter le retrait premature de la mempool

Fichier cible: actors/mempool.scala

~~~scala
case Mempool.GetTxs(replyTo) =>
  val toSend = txs.take(2)
  replyTo ! Mempool.Txs(toSend)
  Behaviors.same

case Mempool.RemoveTxs(confirmedTxs) =>
  val remaining = txs.filterNot(t => confirmedTxs.exists(_.txId == t.txId))
  behavior(remaining)
~~~

### C. Corriger l'integrite bloc/hash

Fichier cible: objects/block.scala

~~~scala
case class Block(
  id: Long,
  transactions: List[SignedTransaction],
  previousHash: String,
  timestamp: Long,
  nonce: Long,
  hash: String
)
~~~

Fichier cible: actors/blockchain.scala

~~~scala
// au lieu de block.toString
val newHash = block.hash
behavior(newHash, currentId + 1)
~~~

### D. Type monetaire fiable et parsing robuste

Fichier cible: actors/blockchain.scala

~~~scala
var balance = BigInt(0)
...
val amount = scala.util.Try(BigInt(parts(3))).getOrElse(BigInt(0))
if (sender == publicKey) balance -= amount
if (receiver == publicKey) balance += amount
...
replyTo ! DB.BalanceResponse(balance)
~~~

### E. Eviter l'exposition de cle privee

Fichier cible: messages/wallet.scala

~~~scala
object Wallet {
  sealed trait Command
  case class CreateTx(to: String, amount: BigInt, fee: BigInt, replyTo: ActorRef[Boolean]) extends Command
  case class GetBalance(replyTo: ActorRef[BigInt]) extends Command
  case class GetPublicKey(replyTo: ActorRef[String]) extends Command
  // GetPrivateKey supprime en production
}
~~~

### F. Garder le ledger sauf en mode reset explicite

Fichier cible: actors/blockchain.scala

~~~scala
def apply(resetLedgerOnStart: Boolean = false): Behavior[DB.Command] = {
  val file = new java.io.File("ledger.txt")
  if (resetLedgerOnStart && file.exists()) file.delete()
  Behaviors.supervise(behavior("000", 0)).onFailure[Exception](SupervisorStrategy.resume)
}
~~~

### G. Completer le domaine Account

Fichier cible: objects/account.scala

~~~scala
package objects

case class Account(
  publicKey: String,
  initialBalance: BigInt
)
~~~

## 6) Workflow actuel compris

1. Wallet demande solde, construit une tx signee, l'envoie a Mempool.
2. Mempool verifie la signature, trie par fees, expose un lot au Validator.
3. Validator mine un bloc a intervalle fixe et demande la persistance DB.
4. DB ecrit ledger et repond aux requetes de solde historique.

Ce workflow est bon conceptuellement. Le point principal a corriger est l'ordre de confirmation (fiabilite transactionnelle).

## 7) Plan de finalisation propose (rapide)

### Etape 1 (critique)
- Persist d'abord, suppression mempool ensuite.
- Suppression des faux "success" prematures dans Wallet.
- Passage des montants en type non flottant.

### Etape 2 (integrite)
- Ajout hash/nonce au Block.
- Chaine basee sur block.hash uniquement.
- Nettoyage de l'API cle privee.

### Etape 3 (resilience)
- Politique de retry en echec DB.
- Reset ledger explicite (optionnel), pas implicite.
- Completion modele metier Account.

## 8) Etat final attendu apres corrections

Si les points ci-dessus sont appliques, vous pouvez raisonnablement annoncer:

- Partie code Akka/Scala: 90-100% de conformite au projet (hors tests, hors LTL, hors Petri).
- Resilience: bonne pour un projet academique critique distribue.
- Workflow: coherent, tracable, et proche d'un comportement de production.

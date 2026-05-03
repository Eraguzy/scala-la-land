# MempoolActor (`actors/mempool.scala`)

## Acteurs et protocoles de messages

**MempoolActor** (`actors/mempool.scala`)
- Commandes reçues : `AddTx`, `GetTxs`, `RemoveTxs`, `ViewPending`
- Supervision : `Behaviors.supervise(...).onFailure[Exception](SupervisorStrategy.restart)`
- État immuable géré par récursion avec `behavior(txs: List[PendingTx])`
- Stockage en mémoire uniquement : aucune persistance disque, la mempool est reconstruite à vide après restart
- Vérifie la signature avant acceptation avec `Crypto.verify(...)`
- Trie les transactions par frais décroissants avec `.sortBy(...)(Ordering[BigInt].reverse)`
- Envoie au validateur un lot maximum de 2 transactions avec `splitAt(2)`
- Sépare clairement la lecture (`GetTxs`, `ViewPending`) et la suppression réelle (`RemoveTxs`)

## Fonction globale de l'acteur

Cet acteur représente la **mempool**, donc la file d'attente des transactions pas encore minées. Son rôle est de recevoir les transactions signées, vérifier qu'elles sont valides, les garder en mémoire dans un ordre de priorité, puis les transmettre au validateur quand celui-ci en demande.

Le point important, c'est qu'il ne modifie jamais directement une variable interne avec un `var`. À la place, il garde son état dans le paramètre `txs` de la fonction `behavior`, puis recrée un nouveau comportement à chaque changement.

```scala
def apply(): Behavior[Mempool.Command] =
  Behaviors.supervise(behavior(List.empty))
    .onFailure[Exception](SupervisorStrategy.restart)
```

Ici, au démarrage, l'acteur crée une mempool vide avec `List.empty`. Ensuite, il est supervisé : si une exception se produit, l'acteur redémarre automatiquement, ce qui remet aussi la liste des transactions à zéro.

## Gestion de l'état

Le cœur de l'acteur est ici :

```scala
private def behavior(txs: List[PendingTx]): Behavior[Mempool.Command] =
  Behaviors.receive { (ctx, msg) =>
    msg match {
      ...
    }
  }
```

Cette fonction représente l'état courant de la mempool. Le paramètre `txs` contient toutes les transactions en attente à cet instant précis.

Quand une transaction est ajoutée ou supprimée, l'acteur ne modifie pas la liste actuelle : il calcule une nouvelle liste, puis rappelle `behavior(updatedList)`. C'est exactement ce qui permet d'avoir un état immuable, tout en gardant une logique de stockage.

## Détail des messages

### `AddTx(signedTx, replyTo)`

Ce message sert à ajouter une transaction signée dans la mempool.

```scala
case Mempool.AddTx(signedTx, replyTo) =>
  val isValid = Crypto.verify(signedTx.tx, signedTx.signature, signedTx.tx.from)
```

La première étape est la vérification de signature. L'acteur appelle `Crypto.verify(...)` avec :
- le contenu brut de la transaction `signedTx.tx`
- la signature `signedTx.signature`
- la clé publique de l'émetteur `signedTx.tx.from`

Si la signature est invalide, la transaction est rejetée immédiatement :

```scala
replyTo ! false
Behaviors.same
```

Ici, `replyTo ! false` envoie une réponse négative à l'acteur qui a demandé l'ajout. Ensuite, `Behaviors.same` veut dire qu'on garde exactement le même état, donc la transaction n'entre pas dans la mempool.

Si la signature est valide, la transaction est encapsulée dans un `PendingTx`, ajoutée à la liste, puis triée par frais décroissants :

```scala
val updated = (PendingTx(signedTx, replyTo) :: txs)
  .sortBy(_.tx.tx.fees)(Ordering[BigInt].reverse)
```

Le `::` ajoute l'élément en tête de liste. Ensuite, le tri remet toutes les transactions dans l'ordre des `fees`, de la plus grande à la plus petite. En pratique, ça simule une petite priority queue : les transactions les plus intéressantes pour le minage passent d'abord.

Enfin, l'acteur retourne un nouveau comportement avec :

```scala
behavior(updated)
```

Donc ici, le retour n'est pas une valeur métier, mais un **nouvel état d'acteur** contenant la mempool mise à jour.

### `GetTxs(replyTo)`

Ce message est utilisé par le validateur pour demander des transactions à miner.

```scala
case Mempool.GetTxs(replyTo) =>
  val (toSend, _) = txs.splitAt(2)
```

`splitAt(2)` coupe la liste en deux parties :
- `toSend` contient les 2 premières transactions
- le reste est ignoré ici

Le point important, c'est que les transactions **ne sont pas supprimées à ce moment-là**. L'acteur fait seulement une lecture partielle de la file d'attente.

Ensuite, il répond au demandeur avec :

```scala
replyTo ! Mempool.Txs(toSend)
```

Si la mempool est vide, il envoie quand même une réponse, mais avec une liste vide :

```scala
replyTo ! Mempool.Txs(List.empty)
```

Donc, dans tous les cas, le validateur reçoit bien un retour. Ça évite d'avoir un acteur qui attend dans le vide sans réponse.

### `RemoveTxs(confirmedTxs)`

Ce message sert à nettoyer la mempool après validation et confirmation en base.

```scala
case Mempool.RemoveTxs(confirmedTxs) =>
  val remaining = txs.filterNot(t => confirmedTxs.exists(_.txId == t.tx.txId))
```

Ici, l'acteur compare les `txId` des transactions confirmées avec celles présentes dans la mempool. Toutes celles qui ont été confirmées sont retirées.

C'est important parce que la suppression ne se fait pas au moment du `GetTxs`. Le système sépare volontairement :
- la **lecture** des transactions à traiter
- la **suppression réelle** une fois que le traitement a abouti

Ensuite, l'acteur bascule vers :

```scala
behavior(remaining)
```

Donc l'état est mis à jour avec uniquement les transactions encore en attente.

### `ViewPending(replyTo)`

Ce message sert à obtenir une vue lisible des transactions encore présentes dans la mempool.

```scala
case Mempool.ViewPending(replyTo) =>
  val infos = txs.map { pt =>
    Mempool.PendingTxInfo(
      txId      = pt.tx.txId,
      from      = pt.tx.tx.from,
      to        = pt.tx.tx.to,
      amount    = pt.tx.tx.amount,
      fee       = pt.tx.tx.fees,
      timestamp = pt.tx.tx.timestamp
    )
  }
  replyTo ! Mempool.PendingView(infos)
  Behaviors.same
```

Ici, l'acteur ne renvoie pas directement les objets `PendingTx` complets. Il fabrique une version plus propre, plus simple à afficher ou à transmettre, avec seulement les informations utiles.

Le `replyTo ! Mempool.PendingView(infos)` envoie donc une vue en lecture seule de l'état actuel. Ensuite, `Behaviors.same` confirme qu'aucune modification n'a été faite sur la mempool.

## Particularités importantes

### 1. Supervision en restart

```scala
Behaviors.supervise(behavior(List.empty))
  .onFailure[Exception](SupervisorStrategy.restart)
```

Le choix de `restart` est important : en cas d'exception, l'acteur redémarre complètement. Comme la mempool n'est pas persistée, toutes les transactions en attente sont perdues, et l'état repart sur une liste vide.

### 2. Pas de variable mutable

L'acteur suit bien la logique Akka Typed : pas de `var`, pas d'objet global modifié, pas d'état partagé. Toute l'évolution de l'état passe par le retour d'un nouveau `Behavior`.

### 3. Priorité par frais

Le tri par `fees` décroissants donne une priorité naturelle aux transactions qui rapportent le plus. Même sans structure dédiée de type `PriorityQueue`, le comportement obtenu est celui d'une file de priorité simple.

### 4. Lecture et suppression séparées

Le `ValidatorActor` peut demander des transactions avec `GetTxs`, mais ça ne les enlève pas tout de suite. Ce choix évite de perdre des transactions si le minage ou l'écriture en base échoue ensuite.

### 5. Réponses explicites avec `replyTo`

Chaque fois qu'un autre acteur attend une réponse, le `MempoolActor` utilise `replyTo ! ...`. C'est le mécanisme classique d'Akka Typed pour répondre explicitement au bon acteur, sans retour de fonction comme dans du code classique.

## Flux global

Voici le fonctionnement global en chaîne :

1. Un acteur envoie `AddTx` avec une transaction signée.
2. Le `MempoolActor` vérifie la signature.
3. Si elle est correcte, il ajoute la transaction et retrie la mempool par frais.
4. Le validateur envoie `GetTxs` pour récupérer jusqu'à 2 transactions.
5. Les transactions sont envoyées mais restent temporairement dans la mempool.
6. Une fois confirmées, un acteur envoie `RemoveTxs`.
7. Le `MempoolActor` nettoie alors sa liste.
8. À tout moment, `ViewPending` permet de consulter l'état courant sans le modifier.

## Résumé technique compact

```scala
apply()
  -> démarre avec List.empty
  -> active une supervision restart

behavior(txs)
  -> représente l'état courant

AddTx
  -> vérifie signature
  -> ajoute + trie par fees décroissants
  -> retourne behavior(updated)

GetTxs
  -> prend les 2 premières transactions
  -> répond avec Mempool.Txs(...)
  -> ne supprime rien

RemoveTxs
  -> retire les tx confirmées par txId
  -> retourne behavior(remaining)

ViewPending
  -> transforme les PendingTx en PendingTxInfo
  -> répond avec PendingView(...)
  -> ne modifie rien
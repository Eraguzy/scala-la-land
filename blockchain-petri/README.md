# Blockchain Petri - Scala Actors Edition

Implémentation pédagogique d'une blockchain centralisée, structurée avec un modèle acteurs/messages.

Le projet ne simule pas un réseau pair-à-pair distribué: il exécute localement des acteurs isolés, avec un état partagé persistant dans un fichier texte.

## Objectifs

- Illustrer une architecture orientée messages (`ask` + `Promise`) en Scala.
- Gérer des transactions signées (RSA), une mempool priorisée, un minage PoW, et une validation de chaîne.
- Conserver l'état entre commandes CLI via `runtime/blockchain-state.txt`.

## Prérequis

- JDK 17+ recommandé
- `sbt`
- Scala 2.13.14 (configuré dans `build.sbt`)

## Démarrage rapide

Initialiser un état de démo:

```bash
sbt "runMain blockchain.BootstrapMain"
```

Lancer un viewer auto-refresh (1000 ms par défaut):

```bash
sbt "runMain blockchain.ViewerMain"
```

Créer une transaction (`fees` optionnel, défaut = `0.1`):

```bash
sbt "runMain blockchain.TransactionCliMain alice bob 12"
sbt "runMain blockchain.TransactionCliMain alice bob 12 0.4"
```

Miner un bloc avec un validateur:

```bash
sbt "runMain blockchain.ValidatorMinerMain validator-1"
```

Paramètres optionnels mineur:

```bash
sbt "runMain blockchain.ValidatorMinerMain validator-1 100 5"
```

- `100`: log d'une tentative toutes les 100 itérations
- `5`: pause de 5 ms entre deux logs affichés

Lancer les tests:

```bash
sbt test
```

## Vue d'ensemble de l'architecture

Acteurs principaux:

- `WalletActor` (1 par wallet): signature, vérification, débit/crédit, création de transaction signée.
- `WalletDirectoryActor`: annuaire central des wallets et validations métier.
- `MempoolActor`: transactions en attente, triées par priorité.
- `LedgerActor`: autorité de validation des blocs et mise à jour de l'état final.
- `ValidatorActor` (1 par validateur): construction/minage d'un bloc candidat.

Le framework d'acteurs est maison:

- Une mailbox (`LinkedBlockingQueue`) par acteur.
- Un thread daemon par acteur.
- `ActorRef.ask` envoie un message et attend la réponse (`Await.result`, timeout par défaut 5 s).

## Modèle de données

Une transaction contient:

- `from`, `to`
- `amount`
- `fees`
- `timestamp`
- `publicKey`
- `signature`

Payload signé (hors signature):

```text
from|to|amount|fees|timestamp
```

Débit effectif appliqué au sender:

```text
totalDebit = amount + fees
```

Une transaction de reward est créée via `Transaction.reward(...)`:

- `from = SYSTEM`
- `signature = SYSTEM`
- `fees = 0`

## Politique de mempool et minage

La mempool est triée à chaque insertion avec un score de priorité:

- `score = amount * fees`
- priorité 1: score décroissant
- priorité 2: `timestamp` croissant (plus ancienne d'abord en cas d'égalité)

Note: le débit réel reste `amount + fees`. Le score sert uniquement à ordonner la mempool.

Le validateur mine:

- les 2 meilleures transactions (`RequestTopTransactions(2)`)
- + 1 reward transaction

Le bloc est ensuite soumis au ledger (`AppendBlock`).

## Pipeline de validation

### 1) Soumission d'une transaction

`TransactionCliMain` appelle `SubmitTransactionFromWallet` sur `WalletDirectoryActor`.

Contrôles effectués:

- existence sender/receiver
- `amount > 0`
- `fees >= 0`
- solde disponible (`balance - pendingOutgoing`) suffisant pour `amount + fees`

Puis:

- le wallet source crée et signe la transaction (`CreateSignedTransaction`)
- l'annuaire revalide signature + cohérence clé publique + solde
- insertion mempool via `AddPrevalidatedTransaction`

### 2) Ajout d'un bloc

`LedgerActor` valide avant commit:

- index/previousHash/hash/difficulté
- validateur autorisé
- exactement 1 reward
- au moins 1 transaction normale
- présence des transactions normales dans la mempool
- signatures et clés publiques
- simulation des soldes (`totalDebit`)

Si valide:

- application des transactions aux wallets (`ApplyTransactions`)
- suppression des transactions confirmées de la mempool (`RemoveConfirmedTransactions`)
- append du bloc à la chaîne

## Persistance de l'état

Le state est stocké dans:

- `runtime/blockchain-state.txt`

Chaque commande CLI:

1. prend un lock exclusif (`runtime/.blockchain-state.lock`)
2. charge le snapshot
3. reconstruit les acteurs
4. exécute l'action
5. sauvegarde le snapshot mis à jour

Le format texte est versionné de fait par ses lignes:

- `META|difficulty|miningReward`
- `W|address|balance|initialBalance|secret|publicKey|isValidator`
- `M|from|to|amount|fees|timestamp|publicKey|signature`
- `B|index|previousHash|validator|timestamp|nonce|hash`
- `T|from|to|amount|fees|timestamp|publicKey|signature`

Les champs texte sont encodés en Base64 URL-safe. Le codec lit aussi les anciens formats sans `publicKey/fees/timestamp`.

## Contrats de messages actuels

### WalletActor

- `GetSnapshot`
- `SignPayload`
- `VerifySignature`
- `CreateSignedTransaction`
- `ApplyCredit`
- `ApplyDebit`
- `IsValidator`

### WalletDirectoryActor

- `GetWallet`
- `GetAllWallets`
- `IsValidator`
- `SubmitTransactionFromWallet`
- `CreateTransaction` (chemin legacy/compat)
- `ValidateTransaction`
- `ApplyTransactions`

### MempoolActor

- `GetTransactions`
- `RequestTransaction` (alias compat)
- `RequestTopTransactions`
- `GetPendingOutgoing`
- `ContainsAll`
- `SubmitTransaction`
- `AddPrevalidatedTransaction`
- `TryAddTransaction` (alias compat)
- `DeleteDoneTransactions` (alias compat)
- `RemoveConfirmedTransactions`
- `RemoveTransactions` (alias compat)

### LedgerActor

- `GetSnapshot`
- `GetLastBlock`
- `AppendBlock`
- `TryAppendBlock` (alias compat)

### ValidatorActor

- `MineOnce`

## Limitations assumées

- Pas de réseau P2P réel, ni consensus distribué entre nœuds.
- Pas de persistance en base de données (fichier texte local).
- Pas de pool de threads: 1 thread daemon par acteur.
- Le champ `secret` d'un wallet contient la clé privée encodée Base64.

## Conseils de démo

Terminal A:

```bash
sbt "runMain blockchain.BootstrapMain"
sbt "runMain blockchain.ViewerMain 800"
```

Terminal B:

```bash
sbt "runMain blockchain.TransactionCliMain alice bob 7 0.6"
sbt "runMain blockchain.TransactionCliMain bob diana 3 0.2"
```

Terminal C:

```bash
sbt "runMain blockchain.ValidatorMinerMain validator-1 500 1"
```

Vous verrez dans le viewer:

- la mempool triée par score (`amount * fees`)
- le reward fixe du bloc et le gain mineur estimé (`reward + fees` des top 2)
- la diminution des transactions après minage
- l'incrément de la chaîne
- la validité globale (`ChainVerifier`)

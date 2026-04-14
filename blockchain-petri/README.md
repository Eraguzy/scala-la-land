# Blockchain Scala Land - version acteurs/messages

Ce projet modélise une blockchain **centralisée** mais organisée selon un **principe d'acteurs et de messages**.

## Idée

Le système n'essaie pas de simuler un réseau pair-à-pair complet. À la place, il sépare la logique en plusieurs acteurs :

- **Wallet actors** : un acteur par wallet, avec adresse, solde, secret, rôle éventuel de validateur
- **WalletDirectory actor** : point d'accès central aux wallets
- **Mempool actor** : gère les transactions en attente
- **Ledger actor** : gère la blockchain, valide et ajoute les blocs
- **Validator actors** : un acteur par validateur, capable de miner un bloc candidat

Chaque commande CLI recharge l'état partagé depuis `runtime/blockchain-state.txt`, reconstruit le système d'acteurs, exécute une action, puis sauvegarde l'état.

## Commandes

Initialisation :

```bash
sbt "runMain blockchain.BootstrapMain"
```

Viewer lecture seule :

```bash
sbt "runMain blockchain.ViewerMain"
```

Ajouter une transaction :

```bash
sbt "runMain blockchain.TransactionCliMain alice bob 12"
```

Lancer le minage dans un autre terminal :

```bash
sbt "runMain blockchain.ValidatorMinerMain validator-1"
```

Paramètres optionnels du mineur :

```bash
sbt "runMain blockchain.ValidatorMinerMain validator-1 100 5"
```

- `100` = afficher une tentative sur 100
- `5` = attendre 5 ms entre les lignes affichées

## Important

La précédente version utilisait la sérialisation Java. Cette version l'abandonne totalement et utilise un **format texte stable** pour l'état partagé, afin d'éviter les erreurs du type `ClassCastException`.

Si un ancien fichier `runtime/blockchain-state.bin` existe encore, il peut être supprimé. La nouvelle version écrit dans :

- `runtime/blockchain-state.txt`

## Architecture

### Wallet actor

Messages typiques :

- `GetSnapshot`
- `SignPayload`
- `VerifySignature`
- `ApplyCredit`
- `ApplyDebit`

### Mempool actor

Messages typiques :

- `TryAddTransaction`
- `GetTransactions`
- `GetPendingOutgoing`
- `RemoveTransactions`

### Ledger actor

Messages typiques :

- `GetSnapshot`
- `TryAppendBlock`

### Validator actor

Messages typiques :

- `MineOnce`

## Démonstration multi-terminaux

Terminal 1 :

```bash
sbt "runMain blockchain.BootstrapMain"
sbt "runMain blockchain.ViewerMain"
```

Terminal 2 :

```bash
sbt "runMain blockchain.TransactionCliMain alice bob 12"
```

Terminal 3 :

```bash
sbt "runMain blockchain.ValidatorMinerMain validator-1 500 1"
```

Le viewer affiche en boucle :

- la liste des validateurs
- la liste des wallets
- la mempool
- les derniers blocs
- la validité globale de la chaîne

## Tests

```bash
sbt test
```

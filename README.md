# README - Blockchain Scala/Akka avec Vérification Formelle

## 📋 Résumé du Projet

Ce projet implémente une **blockchain simplifiée** utilisant :
- **Scala 2.13** avec **Akka Typed** (système d'acteurs distribués)
- **Vérification formelle** avec des réseaux de Pétri et LTL
- **Tests unitaires** complètement intégrés

L'objectif est de modéliser et vérifier un système distribué critique combinant simulation et vérification formelle.

---

## 🏗️ Architecture

### Acteurs principaux

| Acteur | Rôle | Technologie |
|--------|------|-------------|
| **WalletActor** 👛 | Crée et signe les transactions | Akka Typed |
| **MempoolActor** 🔄 | Gère file d'attente triée par fees | PriorityQueue |
| **ValidatorActor** ⛏️ | Mine les blocs (PoW) | Hash Puzzle recherchant "000" |
| **DBBlockchain** 📦 | Registre immuable des blocs | Stockage séquentiel |

### Flux de messages

```
Wallet → Creates signed Tx
         ↓
Mempool → Verifies signature, sorts by fees
         ↓
Validator → Requests Tx, solves PoW
         ↓
DB → Appends block, updates balances
```

---

## 📁 Structure des fichiers

```
scala-la-land/
├── build.sbt                          # Configuration SBT
├── project/
│   └── Dependencies.scala             # Versions des dépendances
├── src/main/scala/bank/
│   ├── Models.scala                   # Transaction, Block, Account
│   ├── Messages.scala                 # Messages Akka (sealed traits)
│   ├── Actors.scala                   # Implémentation des 4 acteurs
│   ├── Main.scala                     # Point d'entrée du système
│   └── Demo.scala                     # Démonstration interactive
├── src/test/scala/bank/
│   └── BlockchainSpec.scala           # Tests unitaires complets
└── README.md                          # Ce fichier
```

---

## 🔑 Structures de données clés

### Transaction
```scala
case class Transaction(
  sender: String,      // Clé publique
  receiver: String,    // Destinataire
  amount: BigDecimal,  // Montant
  fees: BigDecimal,    // Frais (tri)
  timestamp: Long,
  nonce: Int
)
```

### Block avec PoW
```scala
case class Block(
  id: Long,
  transactions: List[SignedTransaction],
  previousBlockHash: String,
  proofOfWork: Long,   // i cherchant "000"
  timestamp: Long
)
```

---

## ✅ Tests unitaires

**9 tests d'intégration** :
- ✓ Création de transactions signées
- ✓ Vérification des signatures
- ✓ File d'attente triée par fees
- ✓ Rejet des signatures invalides
- ✓ Fluxcomplet Wallet → Mempool

**3 tests DB** :
- ✓ Bloc Genesis
- ✓ Ajout de blocs
- ✓ Suivi des soldes

---

## 🚀 Quick Start

```bash
# Tests
sbt test

# Démo interactive
sbt "runMain bank.Demo"

# Compiler
sbt compile
```

---

## 👤 Auteur

**Saghir** - CY Tech, Programmation Fonctionnelle (Scala)

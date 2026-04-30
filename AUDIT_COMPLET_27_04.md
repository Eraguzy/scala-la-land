# État des lieux — Évaluation de complétude

## 1. Outillage et configuration

| Élément | Valeur |
|---|---|
| Langage | Scala **3.3.1** (`build.sbt` ligne 5) |
| Framework acteurs | **Akka Typed** `2.8.5` (`build.sbt` ligne 8) |
| Logging | `logback-classic 1.4.14` |
| Build tool | **sbt 1.11.4** (`project/build.properties` ligne 1) |
| Formatter | `scalafmt 3.10.7`, dialect Scala 3 (`.scalafmt.conf`) |
| CI | **Absent** — aucun dossier `.github/workflows/` |
| Tests | **Absent** — aucun fichier sous `src/test/` |

---

## 2. Cartographie des composants

### Structure des packages

```
src/main/scala/
├── main.scala                   ← point d'entrée, scénario de simulation
├── actors/
│   ├── blockchain.scala         ← DBActor (persistance fichier + supervision)
│   ├── mempool.scala            ← MempoolActor (priority queue, vérif signature)
│   ├── validator.scala          ← ValidatorActor (timer, minage PoW, orchestration)
│   └── wallet.scala             ← WalletActor (file de tx, crypto, solde, clés)
├── messages/
│   ├── blockchain.scala         ← DB.Command / DB.Response
│   ├── mempool.scala            ← Mempool.Command / Txs
│   ├── validator.scala          ← Validator.Command (StartMining, ProcessBlock…)
│   └── wallet.scala             ← Wallet.Command (CreateTx, GetBalance…)
├── objects/
│   ├── tx.scala                 ← UnsignedTransaction, SignedTransaction, PendingTx
│   ├── block.scala              ← Block (id, txs, previousHash, timestamp)
│   └── account.scala            ← VIDE (1 ligne de commentaire)
└── functions/
    ├── crypto.scala             ← RSA textbook (genWalletInts, sign, verify, hashTx)
    ├── miner.scala              ← Miner.mine (PoW récursif tail-rec, prefix SHA-256)
    └── utils.scala              ← Utils (genPrime 256 bits, findE, genTwoDiffPrimes)
```

### Acteurs et protocoles de messages

**DBActor** (`actors/blockchain.scala`)
- Commandes reçues : `GetLastBlock`, `AppendBlock`, `SaveBlock`, `GetBalanceAtDate`
- Supervision : `Behaviors.supervise(...).onFailure[Exception](SupervisorStrategy.resume)` (ligne 17)
- État immuable géré par récursion (pattern fonctionnel Akka Typed)
- Persistance : fichier texte `ledger.txt`, format `BLOCK|ID:...|PREV:...|TS:...` + `TX|from|to|amount`

**MempoolActor** (`actors/mempool.scala`)
- Commandes reçues : `AddTx`, `GetTxs`, `RemoveTxs`
- Vérifie la signature RSA avant d'accepter (`Crypto.verify`, ligne 66–70)
- Trie par fees décroissants (priority queue simulée par `.sortBy`, ligne 80)
- Lot limité à 2 transactions par batch (`splitAt(2)`, ligne 105)

**ValidatorActor** (`actors/validator.scala`)
- Timer périodique de 5 secondes (`timers.startTimerWithFixedDelay(Validator.StartMining, 5.seconds)`, ligne 19)
- Message adapters pour bridger les réponses Mempool et DB (lignes 21–33)
- Deux états internes : `behavior` (actif) et `waitingForDbState` (attend confirmation)
- Difficulté PoW fixée à `"caca"` (ligne 11) — intentionnellement triviale

**WalletActor** (`actors/wallet.scala`)
- State immuable : clés pub/priv, `initialBalance`, `nonce`...
- File interne (`Vector[PendingTx]`) pour sérialiser les transactions concurrentes (lignes 83–95)
- Vérifie le solde DB avant de signer (pattern `requestBalanceForTx` + `CreateTxInternal`)
- Génère clés RSA 256 bits au démarrage via `Crypto.genWalletInts()`

### Flux de messages critiques (chemin nominal)

```
main.scala
  WalletActor ! Wallet.CreateTx(to, amount, fee, replyTo)
    → WalletActor ! Wallet.GetBalance(balanceReceiver)
      → DBActor ! DB.GetBalanceAtDate(pubKey, now, adapter)
        ← DB.BalanceResponse(balance)
    → WalletActor ! Wallet.CreateTxInternal(to, amount, fee, balance, replyTo)
      → if balance >= amount+fee: sign → MempoolActor ! Mempool.AddTx(signedTx, replyTo)
        → Crypto.verify
        → priority-sort → behavior(updatedTxs)

[Timer 5s] ValidatorActor ! Validator.StartMining
  → MempoolActor ! Mempool.GetTxs(txAdapter)
    ← Mempool.Txs(txs)  [adapter → Validator.ProcessBlock]
  → DBActor ! DB.GetLastBlock(dbAdapter)
    ← DB.LastBlockInfo(hash, id)  [adapter → Validator.ProcessMining]
  → Miner.mine(txsData, lastHash, timestamp, 0, "caca")
  → DBActor ! DB.SaveBlock(newBlock, dbStatusAdapter)
    → waitingForDbState:
      ← Validator.ConfirmSaved → pendingTxs.foreach(_.replyTo ! true)
                                → MempoolActor ! Mempool.RemoveTxs(...)
      ← Validator.SaveFailed   → pendingTxs.foreach(_.replyTo ! false)
```

---

## 3. Checklist de conformité au sujet

### ✅ Partie Akka/Scala — Ce qui est présent

- [x] **Akka Typed** utilisé correctement (`Behaviors.setup`, `Behaviors.receive`, `Behaviors.withTimers`)
- [x] **4 acteurs distincts** avec rôles bien séparés (Wallet, Mempool, Validator, DB)
- [x] **Protocoles de messages typés** — `sealed trait Command` + `case class` par acteur
- [x] **Gestion d'état par récursion** (pas de `var` dans les acteurs, pattern fonctionnel)
- [x] **Message adapters** pour la communication asynchrone inter-acteurs
- [x] **Supervision** — `SupervisorStrategy.resume` sur DBActor (`actors/blockchain.scala` ligne 17)
- [x] **Invariant métier "solde ≥ 0"** — vérifié dans WalletActor (`wallet.scala` ligne 104)
- [x] **File de transactions séquentielle** dans le Wallet (gestion de concurrence interne)
- [x] **Cryptographie** — RSA textbook + SHA-256 (non-standard mais fonctionnel)
- [x] **Proof of Work** — `Miner.mine` tail-récursif (`miner.scala`)
- [x] **Persistance fichier** — `ledger.txt` avec `Using` pour la sécurité I/O
- [x] **Scénario de simulation** dans `main.scala` (acteurs Alice/Charlie, cas de dépassement de solde)
- [x] **Documentation interne** (`blockchain.md`, `AUDIT_PROJET_CODE.md`, `PETRI_MODELE_SIMPLE.md`)

### ❌ Partie Akka/Scala — Ce qui manque

- [ ] **Tests unitaires** — aucun fichier sous `src/test/`, aucune dépendance `akka-actor-testkit-typed` ni `scalatest` dans `build.sbt`
- [ ] **Tests d'intégration** — absence totale de scénarios déterministes outillés
- [ ] **CI** — aucun `.github/workflows/`
- [ ] **Politiques de supervision complètes** — seul DBActor est supervisé ; Wallet, Mempool, Validator n'ont pas de stratégie explicite
- [ ] **Escalade de supervision** (restart, backoff, watchWith) — aucune
- [ ] **Akka Persistence** — mentionné dans le sujet, absent du code (pas de `EventSourced`)
- [ ] **Modèle Account** — `objects/account.scala` ne contient qu'un commentaire (ligne 2)
- [ ] **Intégrité de chaîne stricte** — `Block` ne contient pas son propre `hash` ni `nonce` (`block.scala`) ; le hash chaîné utilise `block.toString` (`actors/blockchain.scala` ligne 44)
- [ ] **Type monétaire fiable** — `BalanceResponse.balance: Double` au lieu de `BigInt` (`messages/blockchain.scala` ligne 22) ; risque d'arrondi sur invariant financier
- [ ] **Retrait prématuré de la mempool** — `GetTxs` utilise `splitAt` mais *ne retire pas* les tx (OK), en revanche `ConfirmSaved` supprime sans garantir l'atomicité complète du cycle
- [ ] **Minage bloquant** — `Miner.mine` appelé en synchrone dans le flux de l'acteur Validator (`validator.scala` ligne 74), bloque la mailbox

### ❌ Partie Réseaux de Pétri et vérification formelle — Ce qui manque

- [ ] **Implémentation Petri en code** — `PETRI_MODELE_SIMPLE.md` décrit le modèle textuellement ; aucun code Scala ne représente les places/transitions/jetons
- [ ] **Simulation exécutable du RdP** — aucun moteur de franchissement de transition
- [ ] **Vérification des invariants structurels** — absence de calcul d'invariants P/T (trap, siphon, marquage borné)
- [ ] **Preuve d'absence de deadlock** — non démontrée formellement
- [ ] **Propriétés LTL/CTL** — aucune trace de logique temporelle dans le code
- [ ] **Lien RdP ↔ code Akka** — le modèle Petri n'est pas mappé aux états/transitions des acteurs de manière vérifiable
- [ ] **Outils de model checking** — aucune intégration (LoLA, INA, GreatSPN, TINA, etc. ou équivalent JVM)

---

## 4. Gap Analysis et Roadmap priorisée

### Court terme — Fiabilité et conformité Akka (1–2 semaines)

1. **Corriger l'intégrité de la chaîne** — ajouter `nonce: Long` et `hash: String` dans `objects/block.scala`, chainer sur `block.hash` dans `actors/blockchain.scala` ligne 44 (au lieu de `block.toString`)
2. **Corriger le type monétaire** — passer `BalanceResponse.balance` de `Double` à `BigInt` dans `messages/blockchain.scala` ligne 22, et mettre à jour le parsing dans `actors/blockchain.scala` ligne 103
3. **Ajouter tests unitaires** — introduire `akka-actor-testkit-typed` et `scalatest` dans `build.sbt` ; tester au minimum : invariant solde négatif (WalletActor), rejet signature invalide (MempoolActor), comportement DB en cas d'erreur I/O
4. **Supervision complète** — wrapper Wallet, Mempool et Validator avec `Behaviors.supervise`
5. **Dé-bloquer le minage** — déplacer `Miner.mine` dans `ctx.pipeToSelf` ou un `Future` pour ne pas bloquer la mailbox du Validator

### Moyen terme — Réseaux de Pétri (2–4 semaines)

6. **Implémenter un RdP minimal en Scala** — créer un module `petri/` avec :
   - `case class Place(id: String, tokens: Int)`
   - `case class Transition(id: String, inputs: List[Place], outputs: List[Place])`
   - `def fire(t: Transition, marking: Map[String, Int]): Option[Map[String, Int]]`
7. **Mapper les places aux états acteurs** — chaque état du système (`TxCreated`, `InMempool`, `TakenByValidator`, `Mined`, `Persisted`, `Failed`) devient une place ; chaque message critique (`AddTx`, `GetTxs`, `MineBlock`, `SaveBlock`) devient une transition — conformément à `PETRI_MODELE_SIMPLE.md`
8. **Vérifier les propriétés** — implémenter une recherche BFS sur l'espace des marquages pour détecter deadlocks et valider les invariants décrits dans `PETRI_MODELE_SIMPLE.md` (§ « Ce que le modèle permet de vérifier »)
9. **Ajouter CI** — fichier `.github/workflows/ci.yml` avec `sbt test`

### Long terme — Complétude académique

10. **Akka Persistence / Event Sourcing** — remplacer le `ledger.txt` artisanal par `EventSourcedBehavior` si le sujet l'exige explicitement
11. **Compléter `objects/account.scala`** — au minimum `case class Account(publicKey: String, initialBalance: BigInt)` (proposition déjà dans `AUDIT_PROJET_CODE.md` §G)
12. **Supprimer `GetPrivateKey`** du protocole `Wallet` (`messages/wallet.scala` ligne 23) — faille de sécurité identifiée dans `AUDIT_PROJET_CODE.md` §6

---

## 5. Synthèse

| Domaine | Complétude estimée | Points bloquants |
|---|---|---|
| Architecture Akka Typed | ~80 % | Supervision partielle, minage bloquant |
| Protocoles de messages | ~85 % | Type `Double` financier, `GetPrivateKey` |
| Intégrité blockchain | ~55 % | `block.toString` comme hash, `Block` sans `hash/nonce` |
| Crypto/PoW | ~75 % | RSA textbook (suffisant académiquement) |
| Persistance | ~60 % | Reset systématique au démarrage, pas de durabilité |
| Tests | **0 %** | Aucun fichier test |
| CI | **0 %** | Aucun workflow |
| Réseaux de Pétri (code) | **0 %** | Uniquement documentation markdown |
| Vérification formelle | **0 %** | Aucune vérification d'invariants |

Le dépôt constitue une bonne base de simulation distribuée Akka Typed (~65-70 % du volet code, comme indiqué dans `AUDIT_PROJET_CODE.md`). Les lacunes majeures pour atteindre l'objectif complet du sujet sont : l'**absence totale de tests**, l'**absence d'implémentation Petri en code**, et les **problèmes d'intégrité** (hash de bloc, types financiers).

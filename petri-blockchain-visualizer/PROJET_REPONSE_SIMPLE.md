# Projet — ce qui est demandé (résumé simple)

> Objectif (tel qu’on l’a compris dans le sujet) : **implémenter** une blockchain simulée en **Scala/Akka**, puis **formaliser** le comportement critique avec un **Réseau de Pétri** et **vérifier** des propriétés (deadlocks + invariants + propriétés temporelles type LTL), en expliquant le **mapping code ↔ modèle**.

## 1) Checklist “exigences → où c’est fait”

### A. Système distribué en Scala/Akka (simulation)
- Acteurs (Wallet / Mempool / Validator / DB / Registry) : dossier `src/main/scala/actors/`
- API HTTP : `src/main/scala/api/Routes.scala`
- Démo/simulation : `src/main/scala/main.scala` et `src/main/scala/MainTerminal.scala`

### B. Modèle formel Réseau de Pétri (places / transitions / jetons)
- Modèle + moteur exécutable : `petri-blockchain-visualizer/index.html`
  - Places = états (ex: “mempool vide”, “waiting DB”, “API attend bool”)
  - Transitions = messages/événements (ex: `AddTx`, `GetTxs`, `SaveBlock`, `DB.Success/Failed`)

### C. Exploration de l’espace d’états + deadlocks
- **Onglet Analyse** dans `petri-blockchain-visualizer/index.html`
  - Explore automatiquement les marquages atteignables (exploration **bornée**)
  - Compte les états/arcs
  - Détecte des **deadlocks internes** (requête en attente sans transition interne possible)

### D. Invariants (sûreté)
- Vérifiés dans l’**Analyse** (et/ou sur les traces) :
  - `RemoveTxs` (nettoyage mempool) n’arrive qu’après confirmation DB (`DB.Success`) — modèle et vérif.
  - Après `DB.Failed`, on ne nettoie pas la mempool « juste après » (sûreté locale).

### E. Propriétés temporelles (LTL)
- **Onglet LTL** : vérification sur la **trace** du scénario joué.
- **Onglet Analyse** : vérification sur l’espace exploré (borné) de propriétés LTL “sur événements”.

### F. Mapping “modèle ↔ code Akka”
- **Onglet Code** : chaque transition pointe vers l’élément Scala correspondant (acteur/fichier et intention).

### G. Comparaison “comportement réel ↔ modèle”
- À présenter en soutenance (simple) :
  1. Lancer `sbt run` (ou `runMain blockchain.MainTerminal`) et montrer la séquence réelle (logs).
  2. Ouvrir `petri-blockchain-visualizer/index.html`, lancer le scénario équivalent, vérifier LTL.
  3. Montrer l’onglet Analyse (espace d’états + absence de deadlocks internes dans les bornes).

## 2) Ce qu’on a ajouté pour couvrir les “trous”

- Un explorateur d’espace d’états **dans le visualiseur** (onglet Analyse).
- Une branche manquante côté API : **création wallet refusée si solde initial négatif** (transition + scénario).

## 3) Hypothèses / limites (à dire explicitement)

- Le modèle Petri est un **modèle de contrôle** : il abstrait les valeurs numériques (montants, hash, nonce…).
- Certaines cardinalités sont abstraites (ex: mempool « vide / non vide » plutôt que taille exacte).
- L’exploration est **bornée** (profondeur, nombre d’actions externes HTTP, nombre max de jetons) : c’est assumé et affiché.

## 4) “Mode d’emploi” rapide

1. Ouvrir `petri-blockchain-visualizer/index.html` dans un navigateur.
2. Choisir un scénario et cliquer **Lecture auto**.
3. Aller dans :
   - **LTL** pour vérifier les propriétés sur la trace du scénario.
   - **Analyse** pour explorer l’espace d’états et détecter deadlocks / violations potentielles (dans les bornes choisies).

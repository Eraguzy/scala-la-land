# Modele de reseau de Petri simple pour l'application

## Idee generale

Le reseau de Petri n'est pas juste un schéma. C'est un modele formel du comportement critique du systeme.

Dans votre projet, il sert a representer le cycle suivant:

1. Une transaction est creee par un wallet.
2. Elle entre dans la mempool.
3. Le validator prend un lot de transactions.
4. Le bloc est mine.
5. La DB tente de persister le bloc.
6. Si la persistance reussit, la mempool supprime les transactions confirmees.
7. Si la persistance echoue, les transactions restent disponibles pour un nouveau cycle.

## Forme du modele

Le modele peut etre presente sous trois formes complementaires:

1. Un schema visuel avec places, transitions et jetons.
2. Une description textuelle des etats et des evenements.
3. Une base de verification pour prouver des proprietes comme l'absence de deadlock et le respect des invariants.

## Version minimale du reseau

### Places

- `P0 - Transaction creee`
- `P1 - Transaction en mempool`
- `P2 - Lot pris par le validator`
- `P3 - Bloc mine`
- `P4 - Persistance DB en cours`
- `P5 - Bloc persiste`
- `P6 - Erreur de persistance`
- `P7 - Transactions supprimees de la mempool`

### Transitions

- `T0 - AddTx` : le wallet envoie une transaction vers la mempool.
- `T1 - GetTxs` : le validator recoit un lot de transactions.
- `T2 - MineBlock` : le validator mine le bloc.
- `T3 - SaveBlockSuccess` : la DB sauvegarde le bloc correctement.
- `T4 - SaveBlockFail` : la DB echoue a sauvegarder le bloc.
- `T5 - RemoveTxs` : la mempool supprime les transactions confirmees.
- `T6 - RetryCycle` : le systeme repart avec les transactions encore presentes.

## Lecture du flux

### Chemin nominal

`P0 -> T0 -> P1 -> T1 -> P2 -> T2 -> P3 -> T3 -> P4 -> T5 -> P7`

Interpretation:

- la transaction est acceptee
- elle est proposee au validator
- le bloc est mine
- la DB confirme
- la mempool supprime seulement apres confirmation

### Chemin d'echec

`P0 -> T0 -> P1 -> T1 -> P2 -> T2 -> P3 -> T4 -> P6 -> T6 -> P1`

Interpretation:

- la transaction a ete prise en compte
- la DB a echoue
- la transaction ne doit pas etre perdue
- elle reste ou revient dans la mempool pour un nouveau cycle

## Ce que le modele permet de verifier

### Proprieties structurelles

- Pas de deadlock: le systeme doit toujours pouvoir soit persister, soit reprendre.
- Coherence des sequences: on ne peut pas supprimer avant de persister.
- Validite des transitions: chaque message critique correspond a une transition autorisee.

### Invariants metier

- Une transaction ne doit pas disparaitre en cas d'echec DB.
- `RemoveTxs` ne doit apparaitre qu'apres `SaveBlockSuccess`.
- Un bloc confirme doit correspondre a un lot reellement mine.

## Version encore plus simple pour le rapport

Si vous voulez une version tres pedagogique, vous pouvez reduire le reseau a 5 places:

- `En mempool`
- `Pris par validator`
- `Mine`
- `En attente DB`
- `Confirme ou rejete`

Et a 4 transitions:

- `Recevoir transaction`
- `Prendre lot`
- `Miner`
- `Confirmer ou rejeter`

## A quoi ca sert au final

Le reseau de Petri sert a montrer que votre systeme ne se contente pas de fonctionner en simulation: il respecte aussi un modele formel analyzable.

En clair, il permet de dire:

- voici les etats possibles
- voici les enchainements autorises
- voici ce qui est interdit
- voici pourquoi le systeme ne perd pas de transactions et ne viole pas ses invariants critiques

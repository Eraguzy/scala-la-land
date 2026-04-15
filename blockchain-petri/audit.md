# Audit de migration Akka

Date: 2026-04-15

## Objectif

Migrer le fonctionnement des acteurs vers Akka tout en conservant le meme principe metier:
- transactions signees et validees
- mempool priorisee
- minage PoW
- append bloc + suppression tx confirmees
- persistance snapshot fichier

## Resultat global

- Migration vers Akka classic: OK
- Comportement metier preserve: OK
- Tests et scenario CLI: OK

## Changements techniques

1. Dependances
- Ajout akka-actor 2.8.8 dans build.sbt.

2. Framework d'acteurs
- Remplacement du moteur custom queue/thread par des acteurs Akka.
- Ajout d'un ActorSystem local unique (blockchain-system).
- Conservation d'une API locale ActorRef.ask(...) pour limiter l'impact.

3. Cycle de vie
- Ajout de shutdown runtime pour arreter les acteurs crees.
- Fermeture systematique dans:
  - StateStore.withLockedRuntime
  - ViewerMain
  - BootstrapMain
  - tests

4. Compatibilite
- Les messages metier et le code des flux principaux restent inchanges en surface.
- Les aliases legacy sont conserves.

## Verification

Commande de validation executee:

```bash
sbt ";test;runMain blockchain.BootstrapMain;runMain blockchain.TransactionCliMain alice bob 12 0.4;runMain blockchain.TransactionCliMain diana bob 999 0.2;runMain blockchain.ValidatorMinerMain validator-1 1000 0"
```

Resultat:
- Tests: 3/3 OK
- Bootstrap: OK
- Transaction valide: OK
- Transaction invalide (solde): rejet correct
- Minage + append bloc: OK

## Conformite par rapport au cahier des charges Akka

- Usage Akka pour l'execution des acteurs: OUI
- Conservation du principe de fonctionnement existant: OUI
- Documentation mise a jour (README + notes): OUI
- Impacts documentes (fix.txt): OUI

## Limites restantes

- ActorSystem local seulement (pas cluster/remoting).
- Persistance fichier texte (pas base de donnees).
- Les reponses utilisent encore Promise dans les messages (compatibilite), pas ask pattern Akka natif partout.

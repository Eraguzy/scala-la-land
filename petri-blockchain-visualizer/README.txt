Réseau de Pétri interactif — Blockchain Scala/Akka

Utilisation
1. Ouvrir index.html directement dans un navigateur moderne.
2. Choisir un scénario dans la liste.
3. Utiliser Avancer, Reculer, Lecture auto ou cliquer sur les transitions activables.
4. Les onglets de droite affichent l'état courant, les propriétés LTL, l'analyse deadlock et la correspondance avec le code Scala.

Portée du modèle
Ce visualiseur modélise le flux de contrôle du backend Scala/Akka : Routes.scala, RegistryActor, WalletActor, MempoolActor, ValidatorActor, DBActor, Miner/Crypto et les messages principaux entre acteurs.

Le modèle est aligné sur le comportement observé dans le code pour les scénarios suivants :
- création d'un wallet ;
- transfert nominal ;
- transfert avec mempool déjà non vide ;
- refus local pour fonds insuffisants ;
- signature invalide au niveau MempoolActor ;
- échec DB.SaveBlock ;
- tick de minage avec mempool vide ;
- tick ignoré pendant waitingForDbState ;
- routes GET de lecture, y compris GET /balance.

Limites assumées
Le réseau reste une abstraction du projet. Il ne calcule pas les valeurs métier suivantes : montants, fees, nonce, hash, signature RSA réelle, difficulté de minage, timestamp exact, ni solde final.

La cardinalité exacte de la mempool n'est pas entièrement développée : le modèle distingue vide, non vide et reste après cleanup, mais ne compte pas précisément 1 transaction, 2 transactions ou plus.

DB.AppendBlock et Wallet.GetPrivateKey existent dans les protocoles du code, mais ils ne sont pas intégrés au flux API principal représenté ici. Ils sont donc exclus volontairement du modèle interactif.

Les propriétés LTL affichées sont évaluées sur la trace simulée du scénario courant. Elles servent à vérifier la cohérence de la trace jouée, mais ne constituent pas une preuve exhaustive sur tout l'espace d'états possible.

Point d'attention côté code
Le réseau montre le flux de contrôle, pas les invariants numériques. Le code WalletActor décrémente localement initialBalance après l'envoi à la mempool, tandis que DBActor recalcule aussi les soldes depuis le ledger. En cas de confirmation ou d'échec DB, il faut analyser séparément le risque de double comptage ou d'absence de rollback du solde local.

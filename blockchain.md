Petit topo sur ce que fait la partie DB de notre appli et comment interagir avec.

Deux gros choses faites par rapport aux consignes du projet :

1) *Tolérance aux pannes* : Si l'écriture dans le fichier texte plante (ce qui est une erreur I/O classique), l'acteur ne crash pas et ne fait pas tomber tout le système Akka.

2) *Garantie des invariants métier* : C'est ce module qui permet de calculer les soldes exacts pour vérifier la fameuse règle "un compte ne peut jamais avoir un solde négatif".

-> Fichiers et Fonctionnement <-

1. blockchain.scala (messages/)
- *AppendBlock / SaveBlock* : Utilisé par le Validator une fois le Proof of Work terminé. Il m'envoie le bloc, je l'écris sur le disque (ledger.txt).

- *GetLastBlock* : Utilisé par le Validator avant de miner. Ça lui renvoie le hash précédent et l'ID actuel pour qu'il construise la suite.

- *GetBalanceAtDate* : La commande clé pour interagir avec le système RSA que tu as fait Elias. On me passe une clé publique et un Timestamp, et je renvoie le solde exact du compte à cet instant.

2. blockchain.scala (actors/)
- *Clean state au démarrage* : À chaque fois qu'on lance le système, je supprime ledger.txt s'il existe. Ça nous assure d'avoir des simulations propres et reproductibles, sinon les ID des anciens tests se mélangent avec les nouveaux.

- *Supervision Akka* : J'ai utilisé Behaviors.supervise(...).onFailure(SupervisorStrategy.resume). En gros, si une erreur critique arrive pendant une manipulation de fichier, l'acteur ignore le message fautif et continue.

- *Sécurité des I/O* : J'utilise Using pour ouvrir/fermer le fichier ledger.txt. Ça garantit qu'il n'y a pas de fuite de mémoire ou de fichier bloqué, même dans le cas d'un crash.

- *Calcul des soldes* : Quand on me demande un solde, je lis le fichier texte de haut en bas, bloc par bloc, transaction par transaction. J'ajoute ou je retire l'argent en fonction des clés publiques (sender/receiver) jusqu'au Timestamp demandé.

-> Intégration : À voir avec vous (Mempool, Validator, etc...) <-

* Pour le Wallet (Elias c'est toi qui l'a fait normalement) *
Quand tu m'appelles avec GetBalanceAtDate, je lis uniquement ce qu'il y a dans la blockchain (avec ledger.txt). Ma DB ne connaît pas le "montant initial" que tu donnes au wallet au démarrage.
Ce que tu dois faire : Quand tu reçois ma *BalanceResponse(solde)*, n'oublie pas de faire : Solde Réel = Montant Initial + Le solde renvoyé par la DB.

* Pour le Validator / Mempool (Ayman / matias) *
Ma base de données écrit bêtement les blocs qu'on lui donne. C'est donc à vous de vérifier les soldes ! ("un compte n'a jamais un solde négatif") Ce qu'il faut faire : Avant d'accepter une transaction dans la Mempool ou de la mettre dans un bloc, appelez ma commande *GetBalanceAtDate* pour vous assurer que le mec a bien l'argent qu'il essaie d'envoyer. Sinon, refusez la transaction.
Structure de projet (organisation)



Notes Matias

** Notion de behavior : **

c'est la définition de "qu'est ce que je fais quand je suis dans tel état et que j'ai tel événement"

par exemple : "qu'est ce que je fais quand je suis dans l'état "en combat" et que j'ai l'événement "je suis touché par une attaque" ?"
ou 
"que'ce que je fais quand je recois un message de mon allié qui me dit "je suis en difficulté" ?"'

et 3 utilisations : 
Behaviors.same : "J'ai fini, je ne change rien, je garde les mêmes données pour le prochain message."
Appeler la fonction récursive : "Je change mes données internes pour le prochain message." (Utilisé ici pour AddTx).
Behaviors.stopped : "J'ai fini, je m'arrête définitivement."
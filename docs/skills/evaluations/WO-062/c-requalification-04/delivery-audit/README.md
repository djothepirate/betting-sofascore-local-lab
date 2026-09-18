# Vérificateurs et revue de livraison C4

Copies exactes des vérificateurs locaux et revue documentaire indépendante.
Les scripts ont été exécutés depuis `.tmp` et résolvent le worktree à partir de cet
emplacement. Les extensions `.py.txt` signalent des pièces documentaires ; ces copies
ne s'exécutent pas automatiquement et ne sont pas des entrées des cas évalués.

Le résultat courant est `docs/validation/WO062-C4-CHECKS-20260916.json`. La vérification
des octets Git se fait après le commit et vérifie l'état propre ; elle ne fabrique pas
un fichier qui prétendrait porter sa propre empreinte finale. Aucun de ces contrôles
ne relance un modèle, une sonde Windows, Maven, l'application ou une installation.

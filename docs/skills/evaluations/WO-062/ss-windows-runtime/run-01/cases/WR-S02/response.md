## Skill retenu : `ss-windows-runtime`

**Un seul skill est pertinent pour cette demande.** Il figure dans le catalogue disponible et a été retrouvé dans le workspace par la découverte des fichiers `SKILL.md` sous `.agents`.

**Chemin effectivement lu, intégralement avec un outil :**

[ C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-windows-runtime/run-01/WR-S02/.agents/skills/ss-windows-runtime/SKILL.md](C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-windows-runtime/run-01/WR-S02/.agents/skills/ss-windows-runtime/SKILL.md)

**Raisons de la sélection :**

- **Chemin du JAR contenant des espaces :** le skill traite explicitement la recomposition des arguments de `Start-Process -ArgumentList` et la transmission du chemin du JAR à travers les différentes couches du lanceur.
- **Reproduction dans le bon PowerShell :** il demande d’identifier le runtime réellement impliqué et distingue Windows PowerShell 5.1, PowerShell 7 et les autres contextes d’exécution.
- **Sonde expirée et prompt bloqué :** il couvre les lectures de sorties susceptibles de bloquer, les délais bornés, l’arrêt des processus possédés et la vérification du retour effectif de la commande suivante.
- **Processus appartenant à l’essai :** il prévoit des preuves de propriété associant handle, PID, heure de création, exécutable et identité d’instance, ainsi que la vérification des descendants et du nettoyage.

`ss-verify` n’est pas retenu : la demande porte sur un incident de lanceur et de processus Windows, sans demander de validation de build. `ss-ci-security` n’est pas retenu non plus, puisqu’aucune qualification CI n’est demandée.

**Routage terminé.** Aucune reproduction ni tâche métier n’a été exécutée. Aucun oracle, résultat antérieur, inventaire commenté ou plan d’évaluation n’a été lu.
## Skill retenu : `ss-postgres-change`

**Chemin effectivement lu :** [C:/Users/geoff/.agents/skills/ss-postgres-change/SKILL.md](C:/Users/geoff/.agents/skills/ss-postgres-change/SKILL.md)

**Lecture :** fichier lu intégralement avec l’outil, via `Get-Content -LiteralPath … -Raw`. Sa présence a également été confirmée par l’énumération des fichiers `SKILL.md` du catalogue local.

**Raison de la sélection :** ce skill couvre directement les trois éléments de la demande :

- **Migration PostgreSQL append-only** : il précise que les migrations sont append-only après partage et prévoit l’examen des contraintes, de l’idempotence, des transactions et de la préservation des données et de leur provenance.
- **Upgrade sur une base préremplie** : il demande des preuves d’upgrade depuis la version précédente préremplie, avec contrôle des données et des contraintes utiles.
- **Sauvegarde et reprise** : il traite du protocole de sauvegarde/restauration, de l’identification du primaire et de la cible isolée, ainsi que des preuves comparées. Il précise qu’une simple revue ne nécessite aucune mutation du primaire, purge ou restauration.

## Skills voisins non retenus

- `ss-java-module` : les fichiers Java, les profils et les frontières entre couches sont explicitement hors périmètre.
- `ss-verify` : aucune exécution de build, qualification ou investigation de build n’est demandée ; `ss-postgres-change` couvre déjà l’examen des preuves nécessaires à cette revue.
- `ss-review-closeout` et `ss-work-order` : la demande ne porte pas sur la préparation ou la clôture d’un WO ou d’une PR, ni sur une reprise de travaux.

**Routage terminé.** Aucune revue métier n’a été exécutée et aucun oracle, résultat antérieur, inventaire commenté ou plan d’évaluation n’a été lu.
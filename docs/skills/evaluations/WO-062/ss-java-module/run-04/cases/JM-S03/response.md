## Skill retenu : `ss-postgres-change`

**Chemin effectivement lu, intégralement avec un outil :**  
[C:\Users\geoff\.agents\skills\ss-postgres-change\SKILL.md](C:/Users/geoff/.agents/skills/ss-postgres-change/SKILL.md)

**Raison de la sélection :** ce skill correspond directement aux trois volets de la demande dans le SofaScore Local Lab :

- **Migration PostgreSQL append-only** : il couvre les contraintes, l’idempotence, les transactions et la préservation des données et de leur provenance.
- **Upgrade sur une base préremplie** : il prévoit une vérification depuis la version précédente, avec contrôle des données et des contraintes.
- **Protocole de sauvegarde/reprise** : il couvre la sauvegarde, la restauration, les preuves comparées et la distinction entre primaire et cible isolée. Il précise qu’une simple revue ne nécessite aucune mutation du primaire, purge ou restauration.

**Aucun autre skill n’est retenu.** En particulier, `ss-java-module` ne convient pas au périmètre demandé : les fichiers Java, les profils et les frontières entre couches en sont explicitement exclus.

Le routage est terminé. Je n’ai exécuté aucune revue métier et n’ai lu aucun oracle, résultat antérieur, inventaire commenté ou plan d’évaluation.
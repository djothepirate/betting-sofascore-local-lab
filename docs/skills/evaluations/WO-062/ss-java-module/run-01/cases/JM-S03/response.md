### Skill retenu

| Nom | Chemin effectivement lu | Raison de la sélection |
|---|---|---|
| **`ss-postgres-change`** | [C:\Users\geoff\.agents\skills\ss-postgres-change\SKILL.md](/C:/Users/geoff/.agents/skills/ss-postgres-change/SKILL.md) | Ce skill couvre directement la revue d’une migration PostgreSQL du SofaScore Local Lab, son caractère **append-only**, l’**upgrade depuis une version précédente sur une base préremplie**, ainsi que la **sauvegarde, la restauration et la reprise**. Il précise également qu’une simple revue ne nécessite aucune mutation de la base primaire, purge ou restauration. |

Le fichier `SKILL.md` ci-dessus a été **lu intégralement avec un outil** (`Get-Content -Raw`).

### Skills non retenus

- **`ss-java-module`** : les fichiers Java, les profils et les frontières entre couches sont explicitement exclus de la demande.
- **`ss-verify`** : la demande porte sur une revue de migration et de protocole, sans demande d’exécution de tests ni de diagnostic de build. Les exigences de preuve propres à cette revue sont déjà couvertes par `ss-postgres-change`.
- **`ss-review-closeout`** et **`ss-work-order`** : aucune clôture de WO ou de PR, ni reprise ou découpage de travail, n’est demandée.

**Routage terminé.** Aucune tâche métier n’a été exécutée. Aucun oracle, résultat antérieur, inventaire commenté ou plan d’évaluation n’a été lu.
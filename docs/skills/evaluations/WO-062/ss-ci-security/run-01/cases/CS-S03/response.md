## Skill retenu : `ss-verify`

**Chemin effectivement lu, intégralement avec un outil :** [C:\Users\geoff\.agents\skills\ss-verify\SKILL.md](C:/Users/geoff/.agents/skills/ss-verify/SKILL.md)

**Raison de la sélection :** ce skill couvre directement la vérification Windows d’un changement du **SofaScore Local Lab**. Son contenu traite précisément des éléments demandés :

- La vérification standard Windows via `scripts/Verify-Local.ps1`, avec confirmation préalable des commandes du lanceur dans le worktree courant.
- Le relevé immédiat des codes natifs sous PowerShell et l’arrêt des étapes dépendantes en cas d’échec.
- La lecture des rapports Surefire/Failsafe et Pester pertinents, en distinguant tests exécutés, échecs, erreurs et tests ignorés.
- Les contrôles propres aux changements documentaires, tout en respectant une vérification explicitement demandée.

## Skill voisin écarté : `ss-ci-security`

Sa description dans le catalogue concerne les échecs ou qualifications CI, les contrôles obligatoires, la sécurité, les quotas et la provenance des distributions. Elle exclut explicitement le simple build Windows. La demande portant sur une vérification locale standard, sans diagnostic de pipeline distant ni de distribution, ce skill n’est pas retenu et son fichier n’a pas été lu.

**Routage terminé.** Aucune vérification Windows ni autre tâche métier n’a été exécutée.
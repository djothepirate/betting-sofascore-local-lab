## Décision de routage

**Retenir uniquement `ss-football-quality`.** Ce skill couvre directement la qualification des écarts football du **SofaScore Local Lab**, puis leur interprétation dans l’historique J6.

### Pourquoi ce skill

| Élément de la demande | Partie pertinente du skill |
|---|---|
| Même identifiant de rencontre, mais horaire modifié | **Vérifier identité, temps et valeurs** : identité canonique, observations successives et reprogrammation. |
| Composition qui inverse domicile et extérieur | **Vérifier identité, temps et valeurs** : références explicites HOME/AWAY et cohérence des compositions. |
| Tirs au but sans minute | **Examiner incidents et chronologie** : règles du parseur actif et exception encadrée pour une séance terminale entièrement sans minutes. |
| Qualifier les écarts avant d’interpréter l’historique | **Établir la chaîne de preuve**, puis **Interpréter J6 et restituer la décision** : distinguer provenance, interprétation locale et évolution fournisseur. |

Le parcours pertinent est donc celui du skill : **chaîne de preuve → identité, horaires et HOME/AWAY → incidents et chronologie → interprétation J6**. Cela désigne le parcours à suivre, sans conclure ici sur la validité des écarts décrits.

### Fichier effectivement lu

Lecture **intégrale**, avec l’outil `exec_command` et `Get-Content -Raw`, du fichier :

[ss-football-quality/SKILL.md](C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-football-quality/run-01/FQ-S02/.agents/skills/ss-football-quality/SKILL.md)

C’est le seul fichier lu pour cette décision.

### Pourquoi les autres skills ne sont pas retenus

| Skill du catalogue | Motif de non-sélection |
|---|---|
| `ss-data-contract-replay` | La demande porte sur la qualification des données ; elle ne demande pas de créer ou relire un contrat, un parseur, un import/export ou un replay. Consulter les règles du parseur fait déjà partie de `ss-football-quality`. |
| `ss-provider-benchmark` | Aucune mesure comparative de couverture, fraîcheur, latence ou coût en appels n’est demandée. |
| `ss-postgres-change` | Aucune migration ni modification PostgreSQL n’est en jeu. |
| `ss-verify` | Il ne s’agit pas de valider un changement logiciel ou de diagnostiquer un build. |
| `ss-work-order` | Aucun Work Order à reprendre, cadrer ou découper n’est demandé. |
| `ss-review-closeout` | Aucune revue ou clôture de Work Order ou de PR n’est demandée. |
| Autres skills du catalogue | Leurs périmètres ne correspondent pas à cette qualification de données football. |

**Arrêt au routage : aucune tâche métier exécutée, aucun réseau, démarrage d’application, accès DB/Docker ni modification.**
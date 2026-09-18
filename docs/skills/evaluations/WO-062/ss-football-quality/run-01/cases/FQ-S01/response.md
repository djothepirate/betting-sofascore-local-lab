## Décision de routage

**Skill retenu : `ss-football-quality`, seul.**

Il est explicitement demandé et couvre les trois aspects de la demande :

- **Rôles domicile/extérieur** : résolution de HOME/AWAY à partir des références explicites et traitement des conflits d’identité.
- **Incidents** : cohérence des côtés, des scores et de la chronologie, avec référence au parseur actif.
- **Historique J6 et corrections** : appariement des incidents et distinction entre réinterprétation locale, mise à jour fournisseur, enrichissement tardif et correction tardive.

### Fichier effectivement lu

Le fichier [ss-football-quality/SKILL.md](C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-football-quality/run-01/FQ-S01/.agents/skills/ss-football-quality/SKILL.md) a été **lu intégralement avec un outil**, par `Get-Content -LiteralPath … -Raw`, avec succès et sans troncature. Version déclarée : `0.1.0-candidate.1`.

Cette lecture a été effectuée sur le fichier, indépendamment du contenu déjà fourni dans le message.

### Skills non retenus

Les non-sélections reposent sur le catalogue disponible et sur les limites précisées dans le skill lu.

| Skill | Motif de non-sélection |
|---|---|
| `ss-data-contract-replay` | La demande porte sur la cohérence football des rôles et des corrections d’incidents. Elle ne demande pas de créer, modifier ou relire un contrat, un parseur, un import/export ou un replay en tant que tel. |
| `ss-provider-benchmark` | Aucune mesure de couverture, de fraîcheur, de latence ou de coût fournisseur n’est demandée. |
| `ss-postgres-change` | Aucun changement PostgreSQL ni revue de migration n’est demandé. |
| `ss-verify` | Aucun build, diagnostic d’environnement ou validation d’un changement technique n’est demandé. |
| `ss-work-order` | Aucun cadrage, découpage ou reprise de Work Order n’est demandé. |
| `ss-review-closeout` | Il s’agit d’une revue de données football, sans préparation ou clôture de Work Order ou de PR. |
| Autres skills du catalogue | Leurs périmètres ne correspondent pas à cette demande. |

**Arrêt après le routage.** Seul le `SKILL.md` indiqué a été lu ; aucune revue métier, recherche de skill absent, opération réseau, ouverture d’application, connexion DB/Docker ou modification n’a été effectuée.
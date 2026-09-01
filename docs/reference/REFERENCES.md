# Références de cadrage

## Documents versionnés dans le dépôt

1. [ADR-SS-001](../../ADR-SS-001-experimentation-endpoints-sofascore-depuis-windows.md) —
   décision v1.4 acceptée pour l’expérimentation locale Windows, manuelle et contrôlée.
2. [ADR-SS-002](../../ADR-SS-002-bounded-multi-dossier-provider-robustness.md) — v1.0
   historique consommée et terminée par arrêt ; v1.1 acceptée pour la nouvelle série J9 bornée,
   achevée sous WO-023. Aucun nouveau go ou appel fournisseur n’en découle.
3. [ADR-SS-003](../../ADR-SS-003-optional-integration-topology.md) — v0.1 `ACCEPTED` sous
   WO-026 : `OPTIONAL_LOCAL_PUSH` sélectionné ; `VPS_PLAYWRIGHT` différé et
   `BLOCKED_BY_CURRENT_GOVERNANCE`. WO-028 a clarifié l’identité/version J7 sans changer la
   version de décision, la topologie ou les autorisations.
4. [Cadrage SofaScore Local Lab v0.1.0](Betting_Project_SofaScore_Local_Lab_Cadrage_v0.1.0.pdf) —
   cadrage complémentaire du 8 août 2026 ; PDF de référence immuable.

## Document global conservé dans la Library ChatGPT

- `Projet_Betting_Cadrage_Architecture_v0.5.1.pdf`, version 0.5.1, 7 août 2026.

Ce document externe n’est pas présent dans le dépôt ; aucun lien local n’est donc fabriqué.

Sections structurantes utilisées :

- non-objectifs et non-dépendance au poste Windows ;
- environnement Windows / WSL2 / Docker / Eclipse ;
- abstraction des fournisseurs ;
- position SofaScore et Observation Bridge ;
- maintenabilité, idempotence, tests et sécurité ;
- décisions immédiates et critères d’acceptation.

## Hiérarchie de décision

```text
Cadrage Betting Project v0.5.1
        │
        ├─ règle actuelle : aucun appel SofaScore depuis le VPS n'est autorisé
        │
        ▼
ADR-SS-001 v1.4
        │
        ├─ exception bornée : laboratoire Windows local, manuel et séparé
        ├─ ADR-SS-002 v1.1 : preuve J9 bornée, campagne achevée et go consommé
        └─ ADR-SS-003 v0.1 : topologie d'intégration optionnelle
                ├─ OPTIONAL_LOCAL_PUSH sélectionné
                └─ VPS_PLAYWRIGHT différé et BLOCKED_BY_CURRENT_GOVERNANCE
        │
        ▼
Cadrage SofaScore Local Lab v0.1.0
        │
        └─ feuille de route J0 à J9
```

## Décisions et durcissements versionnés après WO-026

| Lot | Résultat versionné | Limite conservée |
|---|---|---|
| [WO-026](../work_orders/completed/WO-SS-20260901-026-optional-integration-feasibility.md) | comparaison terminée, ADR-SS-003 v0.1 acceptée et `OPTIONAL_LOCAL_PUSH` sélectionné | l’acceptation de l’ADR n’autorise ni implémentation, ni réseau, ni VPS, ni production |
| [WO-027](../work_orders/completed/WO-SS-20260901-027-optional-local-push-implementation.md) | socle sender local, offline/loopback et fail-closed qualifié `PASS_LOCAL_FAIL_CLOSED` | permission officielle `NOT_EVIDENCED`, receiver réel absent et livraison réelle bloquée |
| [WO-028](../work_orders/completed/WO-SS-20260901-028-adr-ss-003-j7-schema-identity-clarification.md) | paire canonique d’identité/version J7 explicitée dans ADR-SS-003 | aucun changement de version de décision, topologie, runtime ou autorisation |
| [WO-029](../work_orders/completed/WO-SS-20260901-029-j9-optional-local-push-port-boundary-hardening.md) | borne de port du push local optionnel durcie et qualifiée localement | aucun receiver réel, réseau réel, VPS ou production |
| [WO-030](../work_orders/completed/WO-SS-20260901-030-j9-provider-playwright-port-boundary-hardening.md) | borne de port Playwright fournisseur durcie et qualifiée localement | aucun appel fournisseur, réseau receiver, VPS ou production |

La [revue officielle versionnée sous WO-027](../validation/J9-WO027-OFFICIAL-PERMISSION-REVIEW-20260901.md)
maintient `J9_OFFICIAL_PERMISSION_STATUS=NOT_EVIDENCED`. La documentation publique observée ne
constitue pas, à elle seule, une permission applicable au laboratoire. La
[qualification WO-027](../validation/J9-WO027-OPTIONAL-LOCAL-PUSH-QUALIFICATION-20260901.md)
porte uniquement sur le socle local fail-closed et un receiver synthétique loopback.

## Portée courante

En cas de conflit, ADR-SS-001 qualifie l’exception locale sans annuler la non-dépendance du projet
principal. ADR-SS-002 v1.1 encadre la campagne J9 achevée ; elle ne maintient aucun go ouvert et
n’autorise ni nouvelle acquisition, ni intégration, ni VPS ou production.

WO-026 a réalisé la comparaison annoncée par l’ancienne version de cet index. L’acceptation
d’ADR-SS-003 v0.1 sélectionne `OPTIONAL_LOCAL_PUSH`, tandis que `VPS_PLAYWRIGHT` reste différé et
`BLOCKED_BY_CURRENT_GOVERNANCE`. Les travaux WO-027 à WO-030 établissent seulement des preuves
locales fail-closed : ils n’autorisent aucun receiver réel, livraison réelle, réseau fournisseur,
déploiement VPS ou usage de production.

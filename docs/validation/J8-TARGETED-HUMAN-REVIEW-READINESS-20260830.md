# J8 — Readiness de la revue humaine ciblée du 2026-08-30

## 1. Portée et état

Cette preuve prépare la revue humaine prévue par WO-016 sans la simuler. La sélection est issue
d'une lecture PostgreSQL locale `REPEATABLE READ READ ONLY`, terminée par `ROLLBACK`. Elle n'a
utilisé aucun payload, `request_key`, transport fournisseur ou processus applicatif.

```text
READINESS_GENERATED_AT=2026-08-30T10:06:24.5720662Z
J8_AUTOMATIC_REPORT_STATE=MEASURED
J8_HUMAN_TARGETED_REVIEW=NOT_RUN
CONTROL_SOURCE_LABEL_STATUS=NOT_DECLARED
EXTERNAL_COMPARISON_STATUS=ABSENT_LOCAL_EVIDENCE
PROVIDER_CALLS_DURING_SELECTION=0
DATABASE_MUTATION=NO
```

Seuls les faits locaux minimisés ci-dessous peuvent être préparés automatiquement. Les verdicts
humains, l'exactitude externe, une supériorité de fraîcheur ou de détail, la valeur analytique et
le coût opérateur ne sont pas déduits.

## 2. Dossier cible de la seconde campagne

```text
REVIEW_ROLE=J8_TARGET
PROVIDER_EVENT_ID=16691018
CANONICAL_EVENT_ID=f4713f80-4769-3656-ba51-61d8ac1aa814
LABEL=Cittadella — Atalanta U23
STARTS_AT=2026-08-15T19:00:00Z
DIRECT_STATE=finished
J4_RESULT=PARSED
STATISTICS=COMPLETE_100_PERCENT
INCIDENTS_V15=PARTIAL_91_PERCENT
LINEUPS=PARTIAL_99_PERCENT
```

La preuve locale permet de proposer `PASS` pour accessibilité, parsing et cohérence du ledger,
ainsi que `PARTIAL` pour la complétude stricte. Ces propositions ne deviennent des verdicts de
revue qu'après contrôle humain. L'exactitude externe reste `NOT_MEASURED`.

## 3. Dossier direct non ciblé `PARTIAL` ou `UNAVAILABLE`

Le candidat littéral le plus récent est PAOK — SK Brann, événement `16717086`, identité
`74f90289-80ca-313f-a3d8-d3a69dfa5c30`, début `2026-08-20T17:45:00Z`. Il porte un état direct
`notstarted`, des statistiques `UNAVAILABLE`, des incidents `EMPTY_VALID` et des compositions
`PARTIAL · 0/5`, mais aucun détail direct. Il est donc rejeté comme dossier à cinq composants; son
absence de détail reste explicitement tracée et `UNAVAILABLE` n'est jamais converti en zéro.

Le fallback déterministe le plus récent possédant les cinq composants est retenu :

```text
REVIEW_ROLE=NON_TARGET_PARTIAL_OR_UNAVAILABLE
LITERAL_NEWEST_PROVIDER_EVENT_ID=16717086
LITERAL_NEWEST_SELECTION=REJECTED_MISSING_DIRECT_DETAIL
SELECTED_PROVIDER_EVENT_ID=16707694
SELECTED_CANONICAL_EVENT_ID=6237ae8e-93e1-3025-90d1-7e2859426f3b
LABEL=Hapoel Be'er Sheva — Sabah FK
STARTS_AT=2026-08-19T19:00:00Z
DIRECT_STATE_COMPONENT=AVAILABLE
DIRECT_DETAIL=AVAILABLE
STATISTICS=UNAVAILABLE
INCIDENTS=EMPTY_VALID
LINEUPS=PARTIAL_48_OF_49
ARCHETYPE_NOT_AVAILABLE=NO
```

Le verdict local proposé est `PARTIAL`; il attend la revue humaine et ne transforme pas la famille
indisponible en complétude de `0%`.

## 4. Dossier direct non ciblé avec correction tardive

```text
REVIEW_ROLE=NON_TARGET_LATE_CORRECTION
PROVIDER_EVENT_ID=16251986
CANONICAL_EVENT_ID=e317f510-1b00-3d7f-ac9b-3739f90e304c
LABEL=Al Diriyah — Al-Nassr
STARTS_AT=2026-08-18T18:00:00Z
TERMINAL_STATE=finished
TERMINAL_STATE_RECEIVED_AT=2026-08-18T19:58:07.321378Z
STATISTICS_TRANSITION=126_TO_150
INCIDENTS_TRANSITION=127_TO_151
LINEUPS_TRANSITION=128_TO_152
FINAL_FAMILIES=COMPLETE
CLASSIFIER=J6_REUSED_LATE_CORRECTION
```

Les nouvelles versions ont été reçues respectivement environ 142,125 s, 144,990 s et 148,006 s
après l'état terminal local. La preuve locale permet de proposer `PASS` pour l'existence et la
reproductibilité de l'archétype; la vérité externe de la correction reste `NOT_MEASURED`. Ce
dossier ne doit pas être injecté dans les métriques automatiques de la fenêtre exclusive J8, où
les changements tardifs restent `NOT_MEASURED`.

## 5. Sources et limites avant revue

Les seules preuves présentes sont `SOFASCORE / DIRECT_LOCAL_ENDPOINT`, les observations
normalisées locales, le ledger J8 et le statut J7 `HUMAN_VALIDATED` utilisé pour sélectionner la
cible. Ce dernier est une preuve locale, pas une source de contrôle indépendante. Aucun libellé de
source externe actuellement admise du Betting Project n'est documenté dans le corpus.

Avant la revue, le propriétaire doit donc soit déclarer ces libellés, soit accepter explicitement
les codes bornés `CONTROL_SOURCE_LABEL=CONTROL_SOURCE_ABSENT` et
`EXTERNAL_COMPARISON_SOURCE=EXTERNAL_COMPARISON_ABSENT`. En l'absence de comparaison externe :

```text
ACCESSIBILITY=MEASURED_AUTOMATICALLY_PENDING_HUMAN_VERDICT
COMPLETENESS=MEASURED_AUTOMATICALLY_PENDING_HUMAN_VERDICT
ACCURACY=NOT_MEASURED
FRESHNESS=PARTIAL
SOURCE_TIME_TO_RECEIPT=NOT_MEASURED
STABILITY=MEASURED_AUTOMATICALLY_PENDING_HUMAN_VERDICT
EFFICIENCY=MEASURED_AUTOMATICALLY_PENDING_HUMAN_VERDICT
MAINTAINABILITY=NOT_MEASURED
RISK=PARTIAL
ANALYTICAL_VALUE=NOT_MEASURED
J9_DECISION_TAKEN=NO
```

La revue versionnée devra conserver seulement le libellé de source, l'heure du contrôle, les
verdicts `PASS`/`PARTIAL`/`FAIL`/`NOT_MEASURED` et une synthèse bornée. Elle ne doit contenir ni
copie brute, URL sensible, payload, header, cookie, jeton, `.env` ou log. Jusqu'à son acceptation,
le rapport final n'est pas créé, WO-016 reste actif et la phase applicative reste
`J8-BENCHMARK-READY-FOR-HUMAN-QUALIFICATION`.

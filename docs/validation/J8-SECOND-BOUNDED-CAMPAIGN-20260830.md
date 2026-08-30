# J8 — Preuve de la seconde campagne bornée du 2026-08-30

## 1. Portée et décision d'exécution

Cette preuve consigne l'unique parcours complet autorisé après la qualification et la clôture de
WO-017. Elle complète, sans la réécrire, la campagne J8 initiale restée `PARTIAL`. Le go a été
consommé par une seule instance locale et n'autorise ni troisième campagne, ni retry, ni appel
supplémentaire.

```text
WORK_ORDER=WO-SS-20260829-016
CAMPAIGN_GIT_COMMIT=438615a9fa05086ef512fecb76322777a79c0b15
FLYWAY_VERSION=28
OWNER_GO=GRANTED_BY_OWNER_2026_08_30
OWNER_GO_CONSUMED=YES
EXECUTION_ACTOR=CODEX_LOCAL_UI
WINDOW_FROM=2026-08-30T09:24:51.0887925Z
WINDOW_TO=2026-08-30T09:44:03.2695965Z
WINDOW_SEMANTICS=[FROM,TO)
AS_OF=2026-08-30T09:44:03.2695965Z
TARGET_PROVIDER_EVENT_ID=16691018
TARGET_CANONICAL_EVENT_ID=f4713f80-4769-3656-ba51-61d8ac1aa814
TARGET_DATE=2026-08-15
TARGET_PHASE_ID=15118
TARGET_ROUTE_TOURNAMENT_ID=824
MAXIMUM_DIRECT_ATTEMPTS=30
ADDITIONAL_CAMPAIGN_AUTHORIZED=NO
```

La sélection du dossier et la vérification d'expiration naturelle des caches sont détaillées dans
`J8-SECOND-BOUNDED-CAMPAIGN-READINESS-20260830.md`. Aucune entrée de cache n'a été supprimée,
invalidée ou contournée. `.env` est resté inchangé.

## 2. Exécution bornée

Le profil combiné documenté à six endpoints a été injecté uniquement dans l'arbre du lanceur
local. `server.address` est resté `127.0.0.1`; polling et rafraîchissement automatique ont été
explicitement forcés à `false`. Le lanceur a créé un contexte Playwright non persistant neuf et
n'a conservé ni profil, cookie, `storageState`, HAR, trace, vidéo, capture ou téléchargement.

| Étape | Campagne minimisée | Unités | Tentatives | Terminal | Résultat |
|---|---:|---:|---:|---|---|
| J3 pages datées | `2f9e114d` | 15 | 15 | `COMPLETED` | pages 1 à 15 parsées jusqu'à `hasNextPage=false` |
| découverte tournoi | `ad0bfa5c` | 1 | 1 | `COMPLETED` | huit rencontres canoniques retenues |
| J4 phase 2 | `45d15930` | 1 | 1 | `COMPLETED` | détail du dossier cible parsé |
| J5 | `257b2ee1` | 3 | 3 | `COMPLETED` | statistiques, incidents V15 et compositions parsés |
| **Total** | **4 campagnes** | **20** | **20** | **`COMPLETED`** | **plafond de 30 respecté** |

Le clic J3 a attendu la réponse longue de la séquence complète. Le contrôle de navigateur a expiré
pendant cette attente, sans second clic : la page et le ledger ont ensuite confirmé le terminal
`COMPLETED`, quinze pages et l'arrêt global réappliqué. Les trois étapes suivantes n'ont été
déclenchées qu'après ce contrôle terminal.

```text
PROVIDER_RETRY=NO
FALLBACK=NO
IMPORT_DURING_CAMPAIGN=NO
MAXIMUM_CONCURRENCY=1
MINIMUM_DELAY_SECONDS=3
TERMINAL_INCIDENT=NONE
OPERATOR_STOP=NO
ACTUAL_DIRECT_ATTEMPTS=20
```

## 3. Ledger immuable

Le contrôle PostgreSQL a été exécuté en transaction `REPEATABLE READ READ ONLY`; aucune
`request_key` n'est rendue dans cette preuve.

```text
BASELINE_CAMPAIGNS=5
BASELINE_UNITS=23
BASELINE_ATTEMPTS=22
BASELINE_UNIT_RESULTS=23
BASELINE_CAMPAIGN_RESULTS=5
FINAL_CAMPAIGNS=9
FINAL_UNITS=43
FINAL_ATTEMPTS=42
FINAL_UNIT_RESULTS=43
FINAL_CAMPAIGN_RESULTS=9
WINDOW_CAMPAIGNS=4
WINDOW_COMPLETED_CAMPAIGNS=4
WINDOW_UNITS=20
WINDOW_ATTEMPTS=20
WINDOW_UNIT_RESULTS=20
INCOMPLETE_ATTEMPTS=0
```

Les vingt unités portent toutes une tentative unique, une réponse HTTP `200`, une issue `PARSED`
et une version de parseur. Le résultat J5 est :

| Famille | Parseur | HTTP | Latence | Complétude |
|---|---|---:|---:|---|
| `EVENT_STATISTICS` | `event-statistics-v2` | 200 | 241 ms | `COMPLETE · 100%` |
| `EVENT_INCIDENTS` | `event-incidents-v15` | 200 | 93 ms | `PARTIAL · 91%` |
| `EVENT_LINEUPS` | `event-lineups-v2` | 200 | 84 ms | `PARTIAL · 99%` |

La complétude partielle décrit des signaux facultatifs absents; elle n'est ni une incompatibilité
de schéma, ni un échec de campagne. Les listes non vides incohérentes, les types erronés et les
séances mixtes restent hors de la règle bornée V15.

## 4. Rapport automatique reproductible

L'application et le worker ont été arrêtés avant l'export. L'exporteur Spring non-Web a forcé tous
les connecteurs et automatismes à `false` et a déclaré zéro appel fournisseur. Le premier fichier a
été renommé dans `exports/j8/`, puis le même export a été rejoué avec les mêmes paramètres.

```text
EXPORT_1=exports/j8/J8-BENCHMARK-REPORT-20260830T094403269Z-run1.md
EXPORT_2=exports/j8/J8-BENCHMARK-REPORT-20260830T094403269Z.md
EXPORT_BYTES=15202
EXPORT_SHA256=ffed40714a7c13f79273d7ddfacd15b02fdd877a2e946f6b843ba63e6b8cfb25
EXPORTS_BYTE_IDENTICAL=YES
EXPORT_NETWORK_CALLS=0
POPULATION_HASH=c61b3ef3a9ac12f94d787da8c396dae58e4208a6f04aa240538e38eac5ab4726
MEASUREMENT_STATE=MEASURED
EVIDENCE_SCOPE=FULL_ATTEMPT_LEDGER
```

Le rapport mesure exactement 20 réponses sur 20 tentatives, 20 parsings compatibles sur 20,
zéro refus, zéro 404, zéro erreur opérationnelle et zéro tentative incomplète. Les six familles
sont disponibles. Les latences nearest-rank sont présentes pour chaque endpoint; J3 porte
`n=15`, minimum 300 ms, P50 348 ms, P95 et maximum 3292 ms.

Le dossier ciblé est exploitable mais pas strictement complet : statistiques `100%`, incidents
`91,62%` et compositions `99,03%`. Le coût exact est de seize appels de découverte, quatre appels
marginaux et vingt appels effectifs par dossier exploitable.

Les changements tardifs de cette fenêtre restent `NOT_MEASURED`, faute de cohorte directe éligible
dans cette population exclusive. L'exactitude externe, le retard depuis une heure source fiable,
le temps de maintenance et la valeur analytique comparative restent également `NOT_MEASURED`;
aucune valeur n'est inventée.

## 5. Reverrouillage post-campagne

Après le terminal, l'instance qualifiée a été arrêtée gracieusement. Un redémarrage inerte a forcé
le connecteur maître, les cinq opt-ins métier et Playwright à `false`, avec origine et allowlist
vides. La page locale a confirmé le verrou de démarrage J3, la découverte tournoi indisponible et
les préparations J4 phase 2 et J5 désactivées. Cette instance a ensuite été arrêtée gracieusement.

```text
ENV_FILE_MUTATION=NO
LOCAL_CONFIGURATION_RELOCKED=YES
J3_STARTUP_LOCK=PASS
TOURNAMENT_DISCOVERY_LOCKED=PASS
J4_PHASE2_PREPARATION_DISABLED=PASS
J5_PREPARATION_DISABLED=PASS
PLAYWRIGHT_DISABLED=PASS
AUTOMATIC_POLLING=NO
AUTOMATIC_REFRESH=NO
APPLICATION_STOPPED=YES
PORT_8087_AFTER_STOP=FREE
```

## 6. État après campagne

Les métriques automatiques obligatoires sont disponibles et le bloc technique de clôture est
soutenable après les portes finales. La revue humaine ciblée sur trois dossiers, les libellés de
sources de contrôle et la décision propriétaire de clôture ne sont toutefois pas encore prouvés.
Le rapport sous `exports/j8/` reste donc un candidat automatique; aucun rapport final n'est encore
gelé sous `docs/benchmark/` et WO-016 reste actif.

```text
J8_PROVIDER_CAMPAIGN_RESULT=MEASURED_COMPLETED
J8_HUMAN_TARGETED_REVIEW=NOT_RUN
J8_CONTROL_SOURCE_LABELS=NOT_DECLARED
J8_FINAL_BENCHMARK_REPORT=NOT_CREATED_PENDING_HUMAN_REVIEW
J8_WORK_ORDER_STATUS=READY_FOR_HUMAN_QUALIFICATION
J8_OWNER_CLOSURE_DECISION=NOT_GRANTED
J9_DECISION_TAKEN=NO
```

Les trois dossiers proposés et les limites de sources sont préparés séparément dans
`J8-TARGETED-HUMAN-REVIEW-READINESS-20260830.md`, sans leur attribuer de verdict humain.

## 7. Portes techniques post-campagne

Les portes ont été rejouées après la campagne et l'alignement documentaire. Aucun de ces contrôles
n'a démarré Playwright ou exécuté un appel fournisseur.

```text
MAVEN_STANDARD_TESTS=928
MAVEN_STANDARD_FAILURES=0
MAVEN_STANDARD_ERRORS=0
MAVEN_STANDARD_SKIPPED_EXPECTED=4
POSTGRESQL_TESTCONTAINERS_TESTS=67
POSTGRESQL_TESTCONTAINERS_FAILURES=0
POSTGRESQL_TESTCONTAINERS_ERRORS=0
VERIFY_LOCAL_RESULT=PASS
VERIFY_LOCAL_INTEGRATION_TESTS_EXECUTED=YES
VERIFY_LOCAL_PROVIDER_CALLS_EXECUTED=NO
DOCKER_COMPOSE_CONFIG=PASS_QUIET
SERVER_ADDRESS=127.0.0.1
DEFAULT_PROVIDER_GATES=FALSE
DEFAULT_PLAYWRIGHT=FALSE
DEFAULT_AUTOMATIC_REFRESH=FALSE
DEFAULT_LIVE_POLLING=FALSE
APPLICATION_PHASE=J8-BENCHMARK-READY-FOR-HUMAN-QUALIFICATION
APPLICATION_LISTENER_8087=ABSENT
LAB_JAVA_PROCESS=ABSENT
GIT_DIFF_CHECK=PASS
```

Ces portes soutiennent le résultat technique `MEASURED`; elles ne constituent ni la revue humaine,
ni le gel du rapport final, ni la décision propriétaire nécessaire pour fermer WO-016.

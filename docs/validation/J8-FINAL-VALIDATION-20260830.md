# J8 — Validation finale et clôture de WO-016

> EXPERIMENTAL · LOCAL_ONLY · NOT_PRODUCTION_APPROVED · NO_CRITICAL_DEPENDENCY

## 1. Décision

Le propriétaire autorise le 30 août 2026 le passage de J8 à `VALIDATED` et la clôture de WO-016 à
condition que toutes les étapes de campagne et tous les critères soient formellement atteints.
L'audit final ci-dessous établit cette condition sans nouvel appel fournisseur et sans promouvoir
les dimensions limitées en résultats positifs.

```text
VALIDATED_AT=2026-08-30T10:49:31.4680472Z
CLOSURE_SOURCE_COMMIT=b92d41e50876072b98a542073c0931c40806428c
FLYWAY_VERSION=28
J8_VALIDATION_DECISION=VALIDATED
J8_WORK_ORDER_STATUS=COMPLETED
OWNER_CLOSURE_DECISION=GRANTED_BY_OWNER_2026_08_30_AFTER_FORMAL_AUDIT
ADDITIONAL_PROVIDER_CAMPAIGN_AUTHORIZED=NO
J9_DECISION_TAKEN=NO
```

## 2. Campagne bornée retenue

La fenêtre exclusive finale est
`[2026-08-30T09:24:51.0887925Z,2026-08-30T09:44:03.2695965Z)`, avec `asOf` égal à la borne
supérieure. Elle conserve quatre campagnes terminées, vingt unités et vingt tentatives directes sur
le plafond absolu de trente : quinze pages J3, une découverte tournoi, J4 phase 2, puis les trois
familles J5. Les vingt tentatives ont reçu une réponse, les vingt réponses ont été parsées et aucun
retry, refus, 404, échec opérationnel ou appel incomplet n'est observé.

```text
MEASUREMENT_STATE=MEASURED
EVIDENCE_SCOPE=FULL_ATTEMPT_LEDGER
CAMPAIGNS=4
UNITS=20
DIRECT_ATTEMPTS=20
RESPONSES=20
PARSED=20
OPERATIONAL_ERRORS=0
INCOMPLETE_ATTEMPTS=0
RETRIES=0
DISCOVERY_OVERHEAD=16
MARGINAL_CALLS_PER_EXPLOITABLE_DOSSIER=4.00
EFFECTIVE_CALLS_PER_EXPLOITABLE_DOSSIER=20.00
EXPLOITABLE_DOSSIERS=1
STRICTLY_COMPLETE_DOSSIERS=0
```

La première fenêtre J8 reste historiquement `PARTIAL` avec dix-neuf tentatives. Ni V15, ni la
seconde campagne, ni la clôture ne réécrivent cette preuve append-only.

## 3. Rapport automatique reproductible

Le dernier exporteur Spring non-Web a été relancé avec les mêmes `from/to/asOf`. Il a forcé tous
les connecteurs, Playwright, polling, refresh et scheduling à `false`, créé le rapport sous
`exports/j8/` et déclaré `J8_BENCHMARK_EXPORT_NETWORK_CALLS=0`.

```text
AUTOMATIC_BLOCK_BYTES=15202
AUTOMATIC_BLOCK_SHA256=ffed40714a7c13f79273d7ddfacd15b02fdd877a2e946f6b843ba63e6b8cfb25
POPULATION_HASH=c61b3ef3a9ac12f94d787da8c396dae58e4208a6f04aa240538e38eac5ab4726
AUTOMATIC_PREFIX_BYTE_IDENTICAL=YES
AUTOMATIC_BEGIN_MARKERS=1
AUTOMATIC_END_MARKERS=1
FINAL_REPORT_BYTES=20008
FINAL_REPORT_SHA256=6d9a46b4391beffe9c1c54591089f0b035a5ed81f9522c03998a0feff18e7e52
EXPORT_NETWORK_CALLS=0
```

Les 15 202 premiers octets de
`docs/benchmark/J8-BENCHMARK-REPORT-20260830.md` sont strictement identiques à l'export canonique.
La section humaine commence uniquement après `<!-- J8-AUTOMATIC-END -->`. Le scan borné du rapport
trouve zéro payload, URL/URI, `request_key`, chaîne JDBC, secret, autorisation, cookie, jeton, log
brut ou artefact navigateur.

## 4. Revue humaine ciblée

Les libellés de source ont été déclarés avant la revue : `SOFASCORE_DIRECT_LOCAL_ENDPOINT`,
`CONTROL_SOURCE_ABSENT` et `EXTERNAL_COMPARISON_ABSENT`. Le processus de revue est `PASS`; ses
conclusions par dossier et dimension restent séparées et bornées.

```text
SOURCE_LABELS_DECLARED_AT=2026-08-30T10:36:11.8215853Z
REVIEWED_AT=2026-08-30T10:44:31.8274298Z
HUMAN_TARGETED_REVIEW=PASS
REVIEW_LIMITATIONS=PRESENT
TARGET_DOSSIER_VERDICT=PARTIAL
NON_TARGET_PARTIAL_UNAVAILABLE_VERDICT=PARTIAL
LATE_CORRECTION_DOSSIER_VERDICT=PARTIAL
LATE_CORRECTION_LOCAL_CLASSIFICATION=PASS
LATE_CORRECTION_EXTERNAL_TRUTH=NOT_MEASURED
```

```text
ACCESSIBILITY_VERDICT=PASS
COMPLETENESS_VERDICT=PARTIAL
ACCURACY_VERDICT=NOT_MEASURED
FRESHNESS_VERDICT=PARTIAL
STABILITY_VERDICT=PASS
EFFICIENCY_VERDICT=PARTIAL
MAINTAINABILITY_VERDICT=NOT_MEASURED
RISK_VERDICT=PARTIAL
ANALYTICAL_VALUE_VERDICT=NOT_MEASURED
OPERATOR_COST_VERDICT=NOT_MEASURED
```

Les changements tardifs du dossier historique de revue ne sont pas injectés dans la fenêtre
automatique, où cette métrique reste `NOT_MEASURED`. L'absence de source externe laisse
l'exactitude et la valeur analytique à `NOT_MEASURED`; la validation de J8 ne prend aucune décision
d'adoption J9.

## 5. Portes finales rejouées

Les commandes prescrites ont été exécutées après la transition documentaire et la phase
`J8-BENCHMARK-VALIDATED` :

```text
COMMAND=.\mvnw.cmd clean verify
RESULT=PASS
STANDARD_TESTS=928
STANDARD_FAILURES=0
STANDARD_ERRORS=0
STANDARD_SKIPPED_EXPECTED=4

COMMAND=.\mvnw.cmd -Pintegration-tests verify
RESULT=PASS
POSTGRESQL_TESTCONTAINERS_TESTS=67
POSTGRESQL_TESTCONTAINERS_FAILURES=0
POSTGRESQL_TESTCONTAINERS_ERRORS=0
POSTGRESQL_TESTCONTAINERS_SKIPPED=0

COMMAND=.\scripts\Verify-Local.ps1 -WithIntegrationTests
RESULT=PASS
INTEGRATION_TESTS_EXECUTED=YES
SOFASCORE_NETWORK_CALLS_EXECUTED=NO

DOCKER_COMPOSE_SANDBOX_PATH_PROBE=COMMAND_NOT_FOUND_NOT_A_GATE_RESULT
COMMAND=docker compose --env-file .env config --quiet
EXECUTION_CONTEXT=APPROVED_HOST_ENVIRONMENT
RESULT=PASS_QUIET
EXIT_CODE=0

COMMAND=git diff --check
RESULT=PASS
```

La première tentative de résolution de `docker` dans le bac à sable isolé n'a trouvé aucun
exécutable et n'a donc pas exécuté Compose. La porte réelle a ensuite été relancée dans
l'environnement hôte autorisé où Docker Desktop est disponible; elle a produit explicitement
`DOCKER_COMPOSE_CONFIG=PASS_QUIET` et `DOCKER_COMPOSE_EXIT_CODE=0`. Aucun échec Compose n'est masqué
ou reclassé.

## 6. Garde-fous après séance

```text
SERVER_ADDRESS=127.0.0.1
APPLICATION_PHASE=J8-BENCHMARK-VALIDATED
DEFAULT_PROVIDER_GATES=FALSE
DEFAULT_PLAYWRIGHT=FALSE
DEFAULT_AUTOMATIC_REFRESH=FALSE
DEFAULT_LIVE_POLLING=FALSE
PORT_8087_LISTENERS=0
LAB_OR_WORKER_PROCESSES=0
ENV_FILE_IGNORED=YES
ENV_LAST_WRITE_UTC=2026-08-30T04:34:17Z
FORBIDDEN_REPORT_VALUES=0
PAYLOAD_OR_RUNTIME_ARTIFACT_IN_DIFF=NO
PUSH_PR_OR_MERGE=NO
```

Java 25, Spring Boot 4.1.0, PostgreSQL local, le listener loopback et les quatre statuts du
laboratoire restent inchangés. Aucun polling, scheduler, retry, fallback, listener public, transfert
VPS ou dépendance du Betting Project principal n'est ajouté.

## 7. Bloc exact de clôture

```text
COMPLETENESS_METRICS=AVAILABLE
LATENCY_METRICS=AVAILABLE
SCHEMA_STABILITY=MEASURED
ERROR_RATE=MEASURED
PROVIDER_CALL_COST=MEASURED
AUTOMATIC_POLLING=NO
```

Tous les critères de WO-016 sont prouvés. J8 passe à `VALIDATED`, WO-016 rejoint
`docs/work_orders/completed/` et aucune autre campagne fournisseur n'est autorisée par cette
clôture.

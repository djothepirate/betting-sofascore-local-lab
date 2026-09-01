# J9 — Campagne bornée de robustesse fournisseur du 2026-08-31

## Verdict

```text
REPORT_ID=J9-PROVIDER-ROBUSTNESS-CAMPAIGN-20260831
WORK_ORDER=WO-SS-20260831-019-j9-provider-robustness
ADR=ADR-SS-002_v1.0
EVIDENCE_RESULT=STOPPED
DETERMINISTIC_RECOMMENDATION=KEEP_LOCAL
OWNER_CONFIRMATION_REQUIRED=YES
PROVIDER_DIRECT_ATTEMPTS=20
GLOBAL_MAXIMUM_DIRECT_ATTEMPTS=38
D2_D3_PROVIDER_ATTEMPTS=0
INTEGRATION_OR_PRODUCTION_AUTHORIZED=NO
```

La série a produit une preuve fonctionnelle compatible pour J3, la découverte tournoi, J4 D1 et
les trois familles J5 D1. Elle a ensuite été arrêtée avant D2 parce que le runtime ne persiste pas un
timestamp permettant de démontrer le délai strict d'au moins trois secondes entre les départs
réseau réels. L'écart de 2 967 ms calculé entre deux timestamps persistés pré-navigation ne prouve
ni une violation on-wire, ni le respect de l'invariant. Le critère reste donc `NOT_MEASURED`.

La règle déterministe J9 conduit à `KEEP_LOCAL` : la preuve globale est `STOPPED`, un critère
matériel de l'enveloppe d'exécution n'est pas démontré, et deux dossiers du corpus n'ont pas été
exécutés. Aucun fait observé ne justifie `ABANDON`, et les conditions de
`PREPARE_OPTIONAL_INTEGRATION` ne sont pas satisfaites.

## Manifeste exécuté

```text
GLOBAL_OWNER_GO_WINDOW_UTC=[2026-08-31T07:15:00Z,2026-08-31T08:15:00Z)
GLOBAL_OWNER_GO_USE=ONE_TIME
GLOBAL_OWNER_GO_FINAL_STATE=CONSUMED_AND_TERMINATED_BY_STOP
EXECUTION_ACTOR=CODEX_LOCAL_AGENT
EXPLICIT_HUMAN_GESTURES=YES
TARGET_PROVIDER_EVENT_IDS=16691018,16671566,16310930
TARGET_CANONICAL_EVENT_IDS=f4713f80-4769-3656-ba51-61d8ac1aa814,da075869-34d4-3d42-83d2-613583691845,c40066c9-987b-38d9-b415-869a453d2ad6
REPLACEMENT_DOSSIER_ALLOWED=NO
REPLACEMENT_DOSSIER_USED=NO
PRIMARY_DATABASE_PURGE=NO
```

Les prérequis ADR, readiness hors ligne et sauvegarde/restauration chiffrée V28 étaient satisfaits
avant le premier appel. Le go a été consommé au premier claim J3 accepté. Aucune permission de
production, d'intégration, de polling, de scheduler, de retry ou de fallback n'a été ajoutée.

## Ledger par sous-campagne

| Étape | Dossier | Tentatives directes | HTTP | Parsing | Persistance | Résultat |
|---|---|---:|---|---|---|---|
| J3 `SCHEDULED_EVENTS`, pages 1 à 15 | D1 | 15 | 15 × 200 | 15 × `scheduled-events-v1` | 15 × `INSERTED` | `PASS` |
| `TOURNAMENT_SCHEDULED_EVENTS` | D1 | 1 | 200 | `tournament-scheduled-v1` | `INSERTED` | `PASS` |
| J4 `EVENT_DETAILS` | D1 | 1 | 200 | `event-details-v2` | `DEDUPLICATED`, occurrence fraîche | `PASS` |
| J5 `EVENT_STATISTICS` | D1 | 1 | 200 | `event-statistics-v2` | `DEDUPLICATED`, occurrence fraîche | `PASS` fonctionnel |
| J5 `EVENT_INCIDENTS` | D1 | 1 | 200 | `event-incidents-v15` | `INSERTED` | `PASS` fonctionnel |
| J5 `EVENT_LINEUPS` | D1 | 1 | 200 | `event-lineups-v2` | `INSERTED` | `PASS` fonctionnel |
| J4/J5 | D2 | 0 | — | — | — | `NOT_STARTED_AFTER_GLOBAL_STOP` |
| J4/J5 | D3 | 0 | — | — | — | `NOT_STARTED_AFTER_GLOBAL_STOP` |

```text
SCHEDULED_EVENTS_ATTEMPTS=15
TOURNAMENT_SCHEDULED_EVENTS_ATTEMPTS=1
EVENT_DETAILS_ATTEMPTS=1
EVENT_STATISTICS_ATTEMPTS=1
EVENT_INCIDENTS_ATTEMPTS=1
EVENT_LINEUPS_ATTEMPTS=1
TOTAL_DIRECT_ATTEMPTS=20
TOTAL_HTTP_200=20
TOTAL_HTTP_404=0
TOTAL_OTHER_HTTP=0
TRANSPORT_FAILURES=0
AUTOMATIC_RETRIES=0
```

La première tentative a commencé à `2026-08-31T07:25:57.890Z`. La dernière réponse a été reçue à
`2026-08-31T07:59:01.196Z`. La demande d'arrêt gracieux a été émise à
`2026-08-31T08:01:36Z`, puis l'arrêt a terminé avec succès sans timestamp de fin distinct conservé.
Le temps opérateur humain n'est pas instrumenté et reste `NOT_MEASURED` ; l'intervalle technique
entre la première tentative et la demande d'arrêt vaut environ 35 minutes 38 secondes.

## Résultats D1

### J3 et découverte tournoi

```text
J3_TERMINAL_STATE=COMPLETED
J3_PAGES=1..15
J3_LAST_PAGE_HAS_NEXT_PAGE=false
J3_CACHE_HITS=0
J3_EVIDENCE_SHA256=1b8e28232e8cbb594b0816d822a5be40926b360965e28823330c5d986d6fb897
TOURNAMENT_SNAPSHOT_ID=810
TOURNAMENT_PAYLOAD_SHA256=ae93f1859d7098973d49f0f37cac6cd473abf821f4eb043eec793f30a6382ff6
TOURNAMENT_COUNT_STATUS=COUNT_VERIFIED
TOURNAMENT_EXPECTED_ACTUAL=8_8
```

L'ancrage est exact : événement fournisseur `16691018`, identité canonique
`f4713f80-4769-3656-ba51-61d8ac1aa814`, Cittadella — Atalanta U23, phase `15118`, tournoi unique
`824`, saison `99790`, round `1`.

### J4

```text
J4_OCCURRENCE_ID=778
J4_SNAPSHOT_ID=715
J4_PAYLOAD_BYTES=9958
J4_PAYLOAD_SHA256=a5d5e6ddac4a048cefd496bb0b8c1271f659bb841cfe3e3d94c6c6762777ad97
J4_NORMALIZED_SHA256=d561af426fecb11df43726b0ae128b856aa1fa201a7190dd1e04e7312dde0687
J4_OBSERVATION_ID=120
J4_RESULT=PASS_DEDUPLICATED_WITH_FRESH_OCCURRENCE
```

### J5

| Famille | Occurrence | Snapshot | Octets | Complétude | Observation | Hash payload |
|---|---:|---:|---:|---|---:|---|
| `EVENT_STATISTICS` | 779 | 716 | 7 648 | `COMPLETE` · 100 % | 322 | `13d2fb29a74d69229091b79074e1915f2378fdb317fab19fe04bf7971ea2a25a` |
| `EVENT_INCIDENTS` | 780 | 813 | 43 363 | `PARTIAL` · 91 % | 354 | `c1c163ec33435137fff0b4b974fff70bc1e3ac43058fdaeb9a6973531ea9228d` |
| `EVENT_LINEUPS` | 781 | 814 | 76 707 | `PARTIAL` · 99 % | 355 | `c52fbd741f038deb132b81947876604b4e45e593f00900bb8f1c995f274643e9` |

Les lacunes de complétude J5 sont persistées par les parseurs et ne sont pas requalifiées en erreur
de transport. Aucun `404` natif n'a été observé sur D1.

## Condition d'arrêt

```text
STATISTICS_REQUESTED_AT_UTC=2026-08-31T07:58:55.179Z
INCIDENTS_REQUESTED_AT_UTC=2026-08-31T07:58:58.146Z
LINEUPS_REQUESTED_AT_UTC=2026-08-31T07:59:01.147Z
PERSISTED_GAP_STATISTICS_TO_INCIDENTS_MS=2967
PERSISTED_GAP_INCIDENTS_TO_LINEUPS_MS=3001
PERSISTED_TIMESTAMP_SEMANTICS=PRE_NAVIGATION_NOT_PROVIDER_DEPARTURE
COORDINATOR_ADMISSION_INTERVAL=ENFORCED_BUT_NOT_PERSISTED
ACTUAL_PROVIDER_DEPARTURE_INTERVAL=NOT_MEASURED
MINIMUM_3S_REAL_DEPARTURE_COMPLIANCE=NOT_DEMONSTRATED
MINIMUM_3S_REAL_DEPARTURE_VIOLATION=NOT_DEMONSTRATED
STRICT_THREE_SECOND_PROVIDER_START_DELAY_VIOLATION_PROVED=NO
```

Le worker capture `requested_at` avant la session CDP, `Fetch.enable`, `page.navigate` et
`route.resume`. Le coordinateur applique le délai à `beginRequest`, avant l'audit, l'IPC et la
préparation navigateur. Une variation d'overhead peut donc comprimer ou allonger l'écart entre les
timestamps persistés et les départs réels. Le contrôle actuel ne fournit pas la preuve demandée.

Le plan imposait d'arrêter si le contrôle existant s'avérait insuffisant. D2 et D3 n'ont donc pas été
préparés, aucun dossier de remplacement n'a été utilisé et le go ne peut pas être rejoué.

## Arrêt, artefacts et sécurité

```text
SPRING_GRACEFUL_SHUTDOWN=PASS
MAVEN_LAUNCHER_TERMINAL=BUILD_SUCCESS
LISTENER_8087_AFTER_STOP=0
APPLICATION_PROCESS_AFTER_STOP=0
RUNTIME_OWNED_PLAYWRIGHT_DESCENDANTS_AFTER_STOP=0
HAR_TRACE_VIDEO_SCREENSHOT_DOWNLOAD_STORAGESTATE=0
PERSISTED_SOFASCORE_FLAGS=FALSE
PERSISTED_BASE_URL=EMPTY
PERSISTED_ALLOWLIST=EMPTY
SENSITIVE_DATA_IN_DOCUMENTATION=NO
RAW_PAYLOAD_IN_DOCUMENTATION=NO
PRIMARY_DATABASE_PURGE=NO
```

Aucun payload brut n'est ajouté à la documentation ou à Git ; les octets autorisés restent dans les
stockages locaux prévus, dont la base primaire et la sauvegarde chiffrée qualifiée. Aucun cookie,
jeton, certificat, chemin sensible, phrase technique ou donnée de session n'est ajouté au dépôt.

## Vérifications post-arrêt

```text
POST_CAMPAIGN_STANDARD_VERIFY=PASS
POST_CAMPAIGN_STANDARD_TESTS=931
POST_CAMPAIGN_STANDARD_FAILURES=0
POST_CAMPAIGN_STANDARD_ERRORS=0
POST_CAMPAIGN_STANDARD_SKIPPED=4
POST_CAMPAIGN_STANDARD_FINISHED_AT_UTC=2026-08-31T08:06:09Z
POST_CAMPAIGN_INTEGRATION_VERIFY=PASS
POST_CAMPAIGN_INTEGRATION_TESTS=67
POST_CAMPAIGN_INTEGRATION_FAILURES=0
POST_CAMPAIGN_INTEGRATION_ERRORS=0
POST_CAMPAIGN_INTEGRATION_SKIPPED=0
POST_CAMPAIGN_INTEGRATION_FINISHED_AT_UTC=2026-08-31T08:09:59Z
POST_CAMPAIGN_FLYWAY_SCHEMA=V28
POST_CAMPAIGN_DOCKER_COMPOSE_CONFIG=PASS
POST_CAMPAIGN_POSTGRES=HEALTHY_LOOPBACK_127_0_0_1_5432
POST_CAMPAIGN_PROVIDER_ACCESS_PERFORMED=NO
```

Les validations standards et PostgreSQL/Testcontainers n'activent aucun parcours réel SofaScore.
Le contrôle Compose conserve PostgreSQL lié à `127.0.0.1`.

## Exports et reproductibilité

La série s'est arrêtée avant la phase de double export canonique post-campagne :

```text
POST_CAMPAIGN_DOUBLE_EXPORT=NOT_RUN_AFTER_GLOBAL_STOP
POST_CAMPAIGN_EXPORT_BYTE_IDENTITY=NOT_MEASURED
POST_CAMPAIGN_EXPORT_HASH_CONCORDANCE=NOT_MEASURED
```

Cette lacune contribue au résultat `STOPPED` et interdit de présenter l'audit/export comme acquis
pour J9.

## Recommandation et choix propriétaire attendu

```text
J9_DECISION_RECOMMENDATION=KEEP_LOCAL
J9_DECISION_STATUS=PENDING_OWNER_CONFIRMATION
J9_EVIDENCE_RESULT=STOPPED
J9_EVIDENCE_REFERENCE=J9-PROVIDER-ROBUSTNESS-CAMPAIGN-20260831
J9_EVIDENCE_SHA256=RECORDED_IN_WO019
J9_PROVIDER_ACQUISITION_MODE=MANUAL_ON_DEMAND
J9_INTEGRATION_IMPLEMENTATION_AUTHORIZED=NO
J9_LIVE_OR_SCHEDULED_OPERATION_AUTHORIZED=NO
J9_BETTING_PROJECT_CRITICAL_DEPENDENCY=NO
J9_OWNER_CONFIRMATION_REQUIRED=YES
```

`KEEP_LOCAL` est la recommandation déterminée par la matrice, pas encore la décision finale du
propriétaire. Cette option maintient le laboratoire expérimental et ne ferme pas la possibilité
d'une nouvelle qualification après correction. La correction et l'instrumentation du délai
nécessitent un Work Order runtime distinct. Toute nouvelle campagne exigerait ensuite un nouveau
manifeste, un nouveau go propriétaire et une nouvelle fenêtre ; le go du présent rapport est
définitivement consommé.

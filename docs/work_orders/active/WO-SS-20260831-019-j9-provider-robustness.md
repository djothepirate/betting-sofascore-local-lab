# WO-SS-20260831-019 — Preuve bornée de robustesse fournisseur pour J9

- **Statut :** `STOPPED`
- **Date d'ouverture :** 2026-08-31
- **Jalon :** J9 — Preuve préalable à la décision
- **Base locale :** `a47c932`
- **Branche/worktree :** `codex/j9-provider-robustness`
- **Work Order parent :** `WO-SS-20260831-018-decision-j9`
- **ADR applicable :** `ADR-SS-001 v1.4`
- **Nouvel ADR :** `ADR-SS-002 v1.0 — ACCEPTED_CONSUMED_AND_TERMINATED_BY_STOP ; v1.1 — DRAFT_SELECTED_PROFILE_PENDING_OWNER_ACCEPTANCE`
- **État de la preuve :** `STOPPED`
- **Réseau fournisseur :** `NO — SERIES_STOPPED`
- **Go propriétaire global :** `CONSUMED`
- **Go consommé :** `YES — FIRST_ACCEPTED_J3_EXECUTION_CLAIM`
- **Nouveau go fournisseur :** `NOT_GRANTED`
- **Plafond historique v1.0 :** `38`
- **Profil sélectionné v1.1 :** `RESTART_FULL_D1_D2_D3 — 38 nouveaux / 58 cumulés`
- **Dossier de remplacement :** `NOT_AUTHORIZED`
- **Nouvel endpoint, URI, transport, code ou migration :** `NONE`
- **Polling, scheduler, live, retry ou fallback :** `NOT_AUTHORIZED`
- **Production VPS courante :** `NOT_AUTHORIZED`
- **Option de production VPS future :** `NOT_EXCLUDED, NOT_MEASURED`

## 1. Objectif

Produire une preuve réelle, locale, manuelle, bornée et reproductible de la robustesse actuelle des
parcours fournisseur déjà autorisés, sur trois dossiers hétérogènes, afin d'éclairer la décision
propriétaire J9.

La campagne doit mesurer :

- accessibilité et outcomes de transport ;
- compatibilité des parseurs courants ;
- disponibilité et complétude des composants ;
- coût exact en appels directs ;
- respect des confirmations, de la lease, du délai et de l'isolation Playwright ;
- audit, reproductibilité et limites non mesurées.

Elle ne constitue ni un test de charge, ni une qualification statistique, juridique, commerciale,
VPS ou de production. Elle ne prend pas la décision J9.

## 2. État courant et portes cumulatives

```text
EVIDENCE_STATUS=STOPPED
NETWORK_AUTHORIZED=NO
OWNER_GO_CONSUMED=YES
OWNER_GO_CONSUMPTION_POINT=FIRST_ACCEPTED_J3_EXECUTION_CLAIM
OWNER_GO_CONSUMPTION_EFFECT=IRREVERSIBLE_FOR_SERIES
PROVIDER_CALLS_UNDER_WO019_FROZEN=20
V1_0_HISTORICAL_LEDGER_BASELINE_DIRECT_ATTEMPTS=0
V1_0_HISTORICAL_LEDGER_RULE=ATTEMPTS_SO_FAR_PLUS_NEXT_MAX_LE_38
V1_0_HISTORICAL_MAX_DIRECT_CALLS=38
V1_1_OWNER_PROFILE_DECISION=SELECT_RESTART_FULL_D1_D2_D3
V1_1_NEW_SERIES_LEDGER_BASELINE_DIRECT_ATTEMPTS=0
V1_1_HISTORICAL_DIRECT_ATTEMPTS_FROZEN=20
V1_1_MAXIMUM_NEW_DIRECT_ATTEMPTS=38
V1_1_MAXIMUM_CUMULATIVE_DIRECT_ATTEMPTS=58
V1_1_D1_REEXECUTION_REQUIRED=YES
V1_1_PROFILE_CURRENTLY_AUTHORIZED=NO_PENDING_ADR_ACCEPTANCE
REPLACEMENT_DOSSIER_ALLOWED=NO
ADR_SS_002_V1_0_STATUS=ACCEPTED_CONSUMED_AND_TERMINATED_BY_STOP
ADR_SS_002_V1_0_REUSABLE_FOR_RESUME=NO
ADR_SS_002_V1_1_STATUS=DRAFT_SELECTED_PROFILE_PENDING_OWNER_ACCEPTANCE
WORK_ORDER_STATUS=STOPPED
WO019_WORK_ORDER_RESUME_AUTHORIZED=NO_STOPPED
WO019_PROVIDER_CAMPAIGN_AUTHORIZED=NO_STOPPED
WO019_PROVIDER_CAMPAIGN_RESUME_AUTHORIZED=NO
V1_0_OFFLINE_READINESS=HISTORICAL_PASS_REEXECUTED_AFTER_VALIDATED_WO020
V1_0_OFFLINE_READINESS_REEXECUTED_ON=2026-08-31
V1_1_FRESH_OFFLINE_READINESS=REQUIRED_NOT_EXECUTED
V1_0_V28_BACKUP_RESTORE=HISTORICAL_QUALIFIED_PRE_D1
V1_0_V28_RESTORE_QUALIFIED=YES_HISTORICAL
V1_0_V28_BACKUP_RESTORE_QUALIFIED_AT_UTC=2026-08-31T06:46:07.7013794Z
V1_0_V28_BACKUP_MAX_SNAPSHOT_ID=794
V1_1_POST_STOP_V28_BACKUP_RESTORE=REQUIRED_NOT_EXECUTED
V1_1_POST_STOP_MINIMUM_SNAPSHOT_COVERAGE=814
V1_1_POST_STOP_MINIMUM_OCCURRENCE_COVERAGE=781
NEXT_GATE=ADR_SS_002_V1_1_OWNER_ACCEPTANCE
GLOBAL_OWNER_GO=CONSUMED_AND_TERMINATED_BY_STOP
GLOBAL_OWNER_GO_OBSERVED_AT_UTC=2026-08-31T07:10:43.748Z
GLOBAL_OWNER_GO_WINDOW_UTC=[2026-08-31T07:15:00Z,2026-08-31T08:15:00Z)
GLOBAL_OWNER_GO_ACTOR=CODEX_LOCAL_AGENT
GLOBAL_OWNER_GO_USE=ONE_TIME
PREVIOUS_GRACEFUL_CLOSE_RUNTIME_WORK_ORDER_REQUIRED=SATISFIED_BY_VALIDATED_WO020
PREVIOUS_RUNTIME_WORK_ORDER=WO-SS-20260831-020-j9-playwright-graceful-close
PREVIOUS_RUNTIME_WORK_ORDER_STATUS=VALIDATED
PREVIOUS_RUNTIME_WORK_ORDER_LOCATION=docs/work_orders/completed/WO-SS-20260831-020-j9-playwright-graceful-close.md
PREVIOUS_RUNTIME_CORRECTION_LOCAL_READINESS=PASS
PREVIOUS_RUNTIME_WORK_ORDER_OWNER_VALIDATION=RECEIVED_2026-08-31T00:41:58Z
PREVIOUS_RUNTIME_OWNER_REVIEW=SATISFIED
NEW_PROVIDER_START_DELAY_RUNTIME_WORK_ORDER_REQUIRED=YES
NEW_PROVIDER_START_DELAY_RUNTIME_WORK_ORDER=WO-SS-20260831-021-j9-playwright-minimum-on-wire-delay
NEW_PROVIDER_START_DELAY_RUNTIME_WORK_ORDER_STATUS=VALIDATED
NEW_PROVIDER_START_DELAY_RUNTIME_WORK_ORDER_BRANCH=codex/j9-playwright-minimum-delay
NEW_PROVIDER_START_DELAY_RUNTIME_WORK_ORDER_LOCATION=docs/work_orders/completed/WO-SS-20260831-021-j9-playwright-minimum-on-wire-delay.md
NEW_PROVIDER_START_DELAY_RUNTIME_WORK_ORDER_OWNER_VALIDATION=RECEIVED_2026-08-31T10:12:59.5805959Z
NEW_PROVIDER_START_DELAY_RUNTIME_WORK_ORDER_OWNER_REVIEW=SATISFIED
NEW_PROVIDER_START_DELAY_IMPLEMENTATION_AUTHORIZED=YES
NEW_PROVIDER_START_DELAY_LOCAL_READINESS=PASS
NEW_PROVIDER_START_DELAY_PROVIDER_ACCESS_PERFORMED=NO
FUTURE_WO019_RESUME=NO_HISTORICAL_CAMPAIGN_STAYS_STOPPED
NEW_FULL_RESTART_REQUIRES_ADR_SS_002_V1_1_OWNER_ACCEPTANCE=YES
NEW_FULL_RESTART_REQUIRES_NEW_CAMPAIGN_WORK_ORDER=YES
NEW_FULL_RESTART_REQUIRES_NEW_GLOBAL_GO=YES
WO019_HISTORICAL_CAMPAIGN_REOPENED=NO
NEW_FULL_RESTART_CAMPAIGN_WORK_ORDER_REQUIRED=YES
NEXT_AVAILABLE_WORK_ORDER_OBSERVED=WO-SS-20260831-023
NEW_FULL_RESTART_CAMPAIGN_WORK_ORDER=NOT_OPENED
NEW_FULL_RESTART_CAMPAIGN_WORKTREE_REQUIRED=YES
OWNER_ENDPOINT_ACTIONS_AVAILABLE_FOR_NEW_CAMPAIGN=NO
OTHER_HUMAN_LOCAL_OPERATOR_DESIGNATED=NO
CAMPAIGN_EXECUTION_STATE=BLOCKED_PENDING_OPERATOR_DECISION
OPERATOR_PATH_OWNER_DECISION=PENDING
AUTOMATED_UI_ORCHESTRATION_AUTHORIZED=NO
CODEX_LOCAL_UI_EXECUTION_AUTHORIZED=NO
INTEGRATION_OR_PRODUCTION_AUTHORIZED=NO
```

### 2.1 Sous-campagne J3 datée — résultat et consommation du go

Le propriétaire a réalisé le geste manuel frais J3 dans l'interface locale. Le premier claim
d'exécution accepté a consommé irréversiblement le go global unique avant le résultat du transport.
L'instant exact du claim n'est pas persisté par le runtime ; il est borné par la première tentative
directe, commencée à `2026-08-31T07:25:57.890Z`. Aucune valeur plus précise n'est déduite.

La preuve terminale minimisée servie localement par le runtime et les métadonnées PostgreSQL ont été
relues séparément, sans versionner ni afficher de payload brut, URI fournisseur, en-tête, phrase de
confirmation, identifiant de confirmation ou donnée de session :

```text
J3_SUBCAMPAIGN_RESULT=PASS
J3_COLLECTION_DATE=2026-08-15
J3_TERMINAL_STATE=COMPLETED
J3_TERMINAL_CODE=NONE
J3_PROVIDER_REQUEST_COUNT=15
J3_PROVIDER_PAGES_REQUESTED=1,2,3,4,5,6,7,8,9,10,11,12,13,14,15
J3_PAGES_COMPLETED_COUNT=15
J3_CACHE_HIT_COUNT=0
J3_LOCAL_JSON_IMPORT_COUNT=0
J3_HTTP_200_COUNT=15
J3_PARSED_COUNT=15
J3_PERSISTENCE_INSERTED_COUNT=15
J3_FIRST_REQUESTED_AT_UTC=2026-08-31T07:25:57.890Z
J3_COMPLETED_AT_UTC=2026-08-31T07:26:40.410752300Z
J3_LAST_PAGE_HAS_NEXT_PAGE=false
J3_MAX_PAYLOAD_BYTES=219503
J3_TOTAL_PAYLOAD_BYTES=2847033
J3_FIVE_MIB_LIMIT_RESPECTED=YES
J3_AUTOMATIC_RETRY_EXECUTED=NO
J3_POLLING_OR_SCHEDULE_EXECUTED=NO
J3_FINAL_GLOBAL_STOP=ACTIVE
J3_FINAL_CIRCUIT_STATE=LOCKED
J3_FINAL_CIRCUIT_REASON=MANUAL_COLLECTION_TERMINAL_LOCK
J3_PLAYWRIGHT_DESCENDANT_PROCESS_COUNT_AFTER_TERMINAL=0
J3_FORBIDDEN_BROWSER_ARTIFACT_COUNT=0
J3_EVIDENCE_VERSION=6
J3_EVIDENCE_BYTES=10629
J3_EVIDENCE_SHA256=1b8e28232e8cbb594b0816d822a5be40926b360965e28823330c5d986d6fb897
```

Les pages sont contiguës ; les quinze réponses portent `HTTP 200`, le parseur
`scheduled-events-v1` et une persistance `INSERTED`. Les tentatives 2 à 15 commencent au moins trois
secondes après la tentative précédente. Le premier appel, sans prédélai requis, a duré 2 872 ms ;
les quatorze autres latences mesurées sont comprises entre 248 et 425 ms. Aucune condition d'arrêt
globale n'a été observée.

L'ancrage J3 de D1 est factuellement présent dans le corpus parsé : le snapshot `804`, page 10,
contient la phase `15118` (`Coppa Italia Serie C, Knockout stage`), le tournoi unique `824`
(`Coppa Italia Serie C`) et la catégorie `31` (`Italy`). Le catalogue runtime est `AVAILABLE`,
reconstruit depuis les quinze pages, et propose cette phase. L'identité événement `16691018`, la
saison `99790` et l'identité canonique attendue seront vérifiées dans le résultat de l'unique
découverte tournoi ; elles ne sont pas présentées comme des sorties de l'endpoint J3 daté.

Le ledger procédural, contrôlé avant la sous-campagne tournoi, devient :

```text
GLOBAL_OWNER_GO=CONSUMED
OWNER_GO_CONSUMED=YES
OWNER_GO_CONSUMPTION_EVIDENCE=FIRST_DIRECT_REQUEST_STARTED_2026-08-31T07:25:57.890Z
WORK_ORDER_STATUS=QUALIFICATION_RUNNING
EVIDENCE_STATUS=DRAFT
PROVIDER_CALLS_UNDER_WO019=15
NEXT_SUBCAMPAIGN=TOURNAMENT_SCHEDULED_EVENTS_D1
NEXT_SUBCAMPAIGN_MAXIMUM_DIRECT_ATTEMPTS=1
LEDGER_CHECK=15_PLUS_1_LE_38_PASS
REMAINING_DIRECT_ATTEMPTS_BEFORE_NEXT_CLAIM=23
REMAINING_DIRECT_ATTEMPTS_AFTER_FULL_NEXT_RESERVATION=22
NEXT_GATE=FRESH_TECHNICAL_CONFIRMATION_TOURNAMENT_D1
REPLACEMENT_DOSSIER_ALLOWED=NO
PRIMARY_DATABASE_PURGE=NO
INTEGRATION_OR_PRODUCTION_AUTHORIZED=NO
```

Le runtime applicatif local reste actif afin de conserver le catalogue issu de cette collecte ; il
n'effectue aucun appel automatiquement. La sous-campagne tournoi exige un nouveau geste opérateur,
une préparation fraîche et sa phrase technique à usage unique. Le go propriétaire global consommé
n'est ni renouvelé ni rejoué : cette confirmation technique matérialise seulement la suite déjà
autorisée dans le manifeste et la fenêtre en cours.

### 2.2 Sous-campagne tournoi D1 — résultat et ancrage événement

Le propriétaire a réalisé le geste manuel frais de découverte tournoi. Le résultat, lié à sa session
locale, a été relu sans interaction dans le panneau terminal puis recoupé avec les métadonnées
PostgreSQL. Aucun payload brut, URI fournisseur, en-tête, phrase de confirmation, identifiant de
confirmation ni donnée de session n'est versionné.

```text
TOURNAMENT_D1_SUBCAMPAIGN_RESULT=PASS
TOURNAMENT_D1_TERMINAL_CODE=COMPLETED
TOURNAMENT_D1_SOURCE=PROVIDER
TOURNAMENT_D1_PROVIDER_CALL_ATTEMPTS=1
TOURNAMENT_D1_SNAPSHOT_ID=810
TOURNAMENT_D1_REQUESTED_AT_UTC=2026-08-31T07:43:42.720Z
TOURNAMENT_D1_RECEIVED_AT_UTC=2026-08-31T07:43:45.519Z
TOURNAMENT_D1_HTTP_STATUS=200
TOURNAMENT_D1_LATENCY_MS=2799
TOURNAMENT_D1_SCHEMA_STATUS=PARSED
TOURNAMENT_D1_PARSER_VERSION=tournament-scheduled-v1
TOURNAMENT_D1_PERSISTENCE_OUTCOME=INSERTED
TOURNAMENT_D1_PAYLOAD_SIZE_BYTES=73720
TOURNAMENT_D1_PAYLOAD_SHA256=ae93f1859d7098973d49f0f37cac6cd473abf821f4eb043eec793f30a6382ff6
TOURNAMENT_D1_COUNT_STATUS=COUNT_VERIFIED
TOURNAMENT_D1_EXPECTED_COUNT=8
TOURNAMENT_D1_ACTUAL_COUNT=8
TOURNAMENT_D1_INSERTED_OBSERVATIONS=8
TOURNAMENT_D1_DEDUPLICATED_OBSERVATIONS=0
TOURNAMENT_D1_AUTOMATIC_RETRY_EXECUTED=NO
TOURNAMENT_D1_PLAYWRIGHT_DESCENDANT_PROCESS_COUNT_AFTER_TERMINAL=0
TOURNAMENT_D1_FORBIDDEN_BROWSER_ARTIFACT_COUNT=0
```

L'identité immuable D1 prévue par ADR-SS-002 et le WO est confirmée par le snapshot `810` et son
observation canonique :

```text
D1_PROVIDER_EVENT_ID=16691018
D1_CANONICAL_EVENT_ID=f4713f80-4769-3656-ba51-61d8ac1aa814
D1_HOME_TEAM=Cittadella
D1_AWAY_TEAM=Atalanta U23
D1_PHASE_ID=15118
D1_UNIQUE_TOURNAMENT_ID=824
D1_SEASON_ID=99790
D1_ROUND=1
D1_STARTS_AT_UTC=2026-08-15T19:00:00Z
D1_STATUS=finished
D1_NORMALIZED_SHA256=106841ce15be7332578685c71623edb3a5ebe627fcce8ffde40e5d517f93873d
D1_IDENTITY_MATCH=PASS
```

Sept autres rencontres ont été conservées comme observations du même tournoi et de la même date ;
elles ne deviennent ni des dossiers de remplacement ni des cibles WO-019. La série continue
exclusivement avec D1, D2 et D3.

Le ledger procédural, contrôlé avant J4 D1, devient :

```text
GLOBAL_OWNER_GO=CONSUMED
OWNER_GO_CONSUMED=YES
WORK_ORDER_STATUS=QUALIFICATION_RUNNING
EVIDENCE_STATUS=DRAFT
PROVIDER_CALLS_UNDER_WO019=16
NEXT_SUBCAMPAIGN=EVENT_DETAILS_D1
NEXT_SUBCAMPAIGN_MAXIMUM_DIRECT_ATTEMPTS=1
LEDGER_CHECK=16_PLUS_1_LE_38_PASS
REMAINING_DIRECT_ATTEMPTS_BEFORE_NEXT_CLAIM=22
REMAINING_DIRECT_ATTEMPTS_AFTER_FULL_NEXT_RESERVATION=21
NEXT_GATE=FRESH_TECHNICAL_CONFIRMATION_J4_D1
REPLACEMENT_DOSSIER_ALLOWED=NO
PRIMARY_DATABASE_PURGE=NO
INTEGRATION_OR_PRODUCTION_AUTHORIZED=NO
```

Le runtime reste actif pour la suite séquentielle. J4 D1 exige une préparation fraîche, le contrôle
des deux identifiants attendus, sa phrase technique à usage unique et un seul geste opérateur.

### 2.3 Sous-campagne J4 D1 — détail événement dédupliqué

Le terminal J4 phase 2 de la session opérateur et les métadonnées locales concordent. L'appel direct
a reçu un contenu byte-identique au snapshot historique `715` : aucune duplication artificielle de
snapshot ou d'observation n'a été créée, mais l'occurrence append-only `778` prouve la nouvelle
acquisition. Aucun payload brut, URI, en-tête, phrase ou identifiant de confirmation n'est versionné.

```text
J4_D1_SUBCAMPAIGN_RESULT=PASS
J4_D1_TERMINAL_CODE=COMPLETED
J4_D1_PROVIDER_CALL_ATTEMPTS=1
J4_D1_PROVIDER_EVENT_ID=16691018
J4_D1_CANONICAL_EVENT_ID=f4713f80-4769-3656-ba51-61d8ac1aa814
J4_D1_OCCURRENCE_ID=778
J4_D1_SNAPSHOT_ID=715
J4_D1_REQUESTED_AT_UTC=2026-08-31T07:53:59.220Z
J4_D1_RECEIVED_AT_UTC=2026-08-31T07:54:02.008Z
J4_D1_HTTP_STATUS=200
J4_D1_LATENCY_MS=2788
J4_D1_SCHEMA_STATUS=PARSED
J4_D1_PARSER_VERSION=event-details-v2
J4_D1_PERSISTENCE_OUTCOME=DEDUPLICATED
J4_D1_PAYLOAD_SIZE_BYTES=9958
J4_D1_PAYLOAD_SHA256=a5d5e6ddac4a048cefd496bb0b8c1271f659bb841cfe3e3d94c6c6762777ad97
J4_D1_EVENT_DETAIL_OBSERVATION_ID=120
J4_D1_OBSERVATION_OUTCOME=DEDUPLICATED
J4_D1_NORMALIZED_SHA256=d561af426fecb11df43726b0ae128b856aa1fa201a7190dd1e04e7312dde0687
J4_D1_IDENTITY_MATCH=PASS
J4_D1_AUTOMATIC_RETRY_EXECUTED=NO
J4_D1_PLAYWRIGHT_DESCENDANT_PROCESS_COUNT_AFTER_TERMINAL=0
```

La preuve confirme toujours `Cittadella — Atalanta U23`, la phase `15118`, la saison `99790`, le
round `1` et le statut `finished`. La déduplication est un résultat de persistance attendu ; elle ne
réduit pas le ledger, car l'occurrence `778` correspond à une tentative directe effectivement
exécutée.

```text
PROVIDER_CALLS_UNDER_WO019=17
NEXT_SUBCAMPAIGN=J5_EVENT_DATA_D1
NEXT_SUBCAMPAIGN_MAXIMUM_DIRECT_ATTEMPTS=3
LEDGER_CHECK=17_PLUS_3_LE_38_PASS
REMAINING_DIRECT_ATTEMPTS_BEFORE_NEXT_CLAIM=21
REMAINING_DIRECT_ATTEMPTS_AFTER_FULL_NEXT_RESERVATION=18
NEXT_GATE=FRESH_TECHNICAL_CONFIRMATION_J5_D1
REPLACEMENT_DOSSIER_ALLOWED=NO
INTEGRATION_OR_PRODUCTION_AUTHORIZED=NO
```

### 2.4 Sous-campagne J5 D1 — compatibilité fonctionnelle et arrêt de gouvernance

J5 D1 s'est terminé fonctionnellement avec trois réponses `HTTP 200`, trois parsings compatibles et
aucun retry. Les familles sont ordonnées `STATISTICS`, `INCIDENTS`, `LINEUPS`. La preuve locale est :

```text
J5_D1_TERMINAL_CODE=COMPLETED
J5_D1_PROVIDER_CALL_ATTEMPTS=3
J5_D1_LOCAL_JSON_IMPORTS=0
J5_D1_HTTP_200_COUNT=3
J5_D1_NATIVE_404_COUNT=0
J5_D1_AUTOMATIC_RETRY_EXECUTED=NO
J5_D1_STATISTICS_OCCURRENCE_ID=779
J5_D1_STATISTICS_SNAPSHOT_ID=716
J5_D1_STATISTICS_PAYLOAD_SIZE_BYTES=7648
J5_D1_STATISTICS_PERSISTENCE_OUTCOME=DEDUPLICATED
J5_D1_STATISTICS_PARSER_VERSION=event-statistics-v2
J5_D1_STATISTICS_COMPLETENESS=COMPLETE_100
J5_D1_STATISTICS_OBSERVATION_ID=322
J5_D1_STATISTICS_PAYLOAD_SHA256=13d2fb29a74d69229091b79074e1915f2378fdb317fab19fe04bf7971ea2a25a
J5_D1_INCIDENTS_OCCURRENCE_ID=780
J5_D1_INCIDENTS_SNAPSHOT_ID=813
J5_D1_INCIDENTS_PAYLOAD_SIZE_BYTES=43363
J5_D1_INCIDENTS_PERSISTENCE_OUTCOME=INSERTED
J5_D1_INCIDENTS_PARSER_VERSION=event-incidents-v15
J5_D1_INCIDENTS_COMPLETENESS=PARTIAL_91
J5_D1_INCIDENTS_OBSERVATION_ID=354
J5_D1_INCIDENTS_PAYLOAD_SHA256=c1c163ec33435137fff0b4b974fff70bc1e3ac43058fdaeb9a6973531ea9228d
J5_D1_LINEUPS_OCCURRENCE_ID=781
J5_D1_LINEUPS_SNAPSHOT_ID=814
J5_D1_LINEUPS_PAYLOAD_SIZE_BYTES=76707
J5_D1_LINEUPS_PERSISTENCE_OUTCOME=INSERTED
J5_D1_LINEUPS_PARSER_VERSION=event-lineups-v2
J5_D1_LINEUPS_COMPLETENESS=PARTIAL_99
J5_D1_LINEUPS_OBSERVATION_ID=355
J5_D1_LINEUPS_PAYLOAD_SHA256=c52fbd741f038deb132b81947876604b4e45e593f00900bb8f1c995f274643e9
J5_D1_LAST_RESPONSE_RECEIVED_AT_UTC=2026-08-31T07:59:01.196Z
```

L'audit temporel postérieur au terminal a cependant trouvé les instants worker suivants :

```text
EVENT_STATISTICS_REQUESTED_AT_UTC=2026-08-31T07:58:55.179Z
EVENT_INCIDENTS_REQUESTED_AT_UTC=2026-08-31T07:58:58.146Z
EVENT_LINEUPS_REQUESTED_AT_UTC=2026-08-31T07:59:01.147Z
OBSERVED_REQUESTED_AT_GAP_STATISTICS_TO_INCIDENTS_MS=2967
OBSERVED_REQUESTED_AT_GAP_INCIDENTS_TO_LINEUPS_MS=3001
```

`requested_at` est capturé dans le worker avant l'installation du garde CDP puis avant
`page.navigate` ; il ne constitue donc pas l'instant réseau « on-wire ». Le coordinateur impose au
moins trois secondes entre ses admissions `beginRequest`, en amont de l'IPC et de la préparation du
navigateur, mais aucun timestamp de départ réseau exact n'est persisté. L'écart de 2 967 ms ne prouve
donc pas à lui seul un départ réseau sous trois secondes ; réciproquement, le runtime courant ne
permet pas de démontrer l'invariant strict demandé entre les départs fournisseur réels.

Conformément à la règle WO-019 qui bloque la campagne si le contrôle existant est insuffisant, la
preuve temporelle devient `NOT_MEASURED` et la série est arrêtée avant D2 :

```text
MINIMUM_THREE_SECOND_PROVIDER_START_DELAY=NOT_MEASURED
COORDINATOR_ADMISSION_DELAY=ENFORCED_AT_LEAST_3_SECONDS
EXACT_PROVIDER_START_TIMESTAMP=PERSISTED_NO
STRICT_PROVIDER_START_DELAY_DEMONSTRATED=NO
STRICT_PROVIDER_START_DELAY_VIOLATION_PROVED=NO
GLOBAL_EVIDENCE_RESULT=STOPPED
STOP_REASON=PROVIDER_START_DELAY_NOT_MEASURABLE_WITH_CURRENT_RUNTIME
WO019_TOTAL_DIRECT_ATTEMPTS=20
D2_PROVIDER_ATTEMPTS=0
D3_PROVIDER_ATTEMPTS=0
D2_D3_STATUS=NOT_STARTED_AFTER_GLOBAL_STOP
REPLACEMENT_DOSSIER_USED=NO
GLOBAL_OWNER_GO=CONSUMED_AND_TERMINATED_BY_STOP
NETWORK_AUTHORIZED=NO
WO019_PROVIDER_CAMPAIGN_RESUME_AUTHORIZED=NO
NEXT_GATE=OWNER_AUTHORIZATION_FOR_WO021_IMPLEMENTATION
```

L'application a reçu `Ctrl+C` à `2026-08-31T08:01:36Z`. Spring a terminé son arrêt gracieux,
Tomcat a confirmé la fin des requêtes actives, JPA et Hikari se sont fermés, puis le lanceur Maven a
terminé `BUILD SUCCESS`. Le contrôle après arrêt donne :

```text
LISTENER_8087_COUNT=0
APPLICATION_PID_3560_PRESENT=NO
RUNTIME_OWNED_DESCENDANT_PROCESS_COUNT=0
FORBIDDEN_BROWSER_ARTIFACT_COUNT=0
PERSISTED_NETWORK_FLAGS=FALSE
PERSISTED_BASE_URL=EMPTY
PERSISTED_ALLOWLIST=EMPTY
PRIMARY_DATABASE_PURGE=NO
INTEGRATION_OR_PRODUCTION_AUTHORIZED=NO
```

Les vérifications post-arrêt sont vertes et n'ont effectué aucun appel fournisseur :

```text
POST_CAMPAIGN_STANDARD_VERIFY=PASS_931_TESTS_0_FAILURE_0_ERROR_4_SKIPPED
POST_CAMPAIGN_STANDARD_FINISHED_AT_UTC=2026-08-31T08:06:09Z
POST_CAMPAIGN_INTEGRATION_VERIFY=PASS_67_TESTS_0_FAILURE_0_ERROR_0_SKIPPED
POST_CAMPAIGN_INTEGRATION_FINISHED_AT_UTC=2026-08-31T08:09:59Z
POST_CAMPAIGN_FLYWAY_SCHEMA=V28
POST_CAMPAIGN_DOCKER_COMPOSE_CONFIG=PASS
POST_CAMPAIGN_POSTGRES=HEALTHY_LOOPBACK_127_0_0_1_5432
POST_CAMPAIGN_PROVIDER_ACCESS_PERFORMED=NO
```

Une nouvelle campagne fournisseur est interdite sous ce go. La reprise exige un Work Order de code
distinct, sa qualification hors ligne, une décision propriétaire et un nouveau manifeste temporel ;
WO-019 ne sera pas rouvert par simple nouvelle phrase technique.

Le rapport distinct est préparé sous
[`J9-PROVIDER-ROBUSTNESS-CAMPAIGN-20260831.md`](../../validation/J9-PROVIDER-ROBUSTNESS-CAMPAIGN-20260831.md) :

```text
J9_EVIDENCE_REPORT_ID=J9-PROVIDER-ROBUSTNESS-CAMPAIGN-20260831
J9_EVIDENCE_REPORT_BYTES=9708
J9_EVIDENCE_REPORT_SHA256=47e6171eeb1fc44cf995c727d4f09107875c844e71e8b183068731cbb4c62b9d
```

La recommandation déterministe est préremplie avec les seuls faits mesurés. Elle reste soumise à la
confirmation explicite du propriétaire :

```text
J9_DECISION_RECOMMENDATION=KEEP_LOCAL
J9_OWNER_RESPONSE_TO_KEEP_LOCAL=REJECTED_AS_FINAL_DECISION
J9_FINAL_DECISION=NOT_TAKEN
J9_DECISION_STATUS=PENDING_ADR_SS_002_V1_1_OWNER_ACCEPTANCE_AND_NEW_CAMPAIGN_EVIDENCE
J9_EVIDENCE_RESULT=STOPPED
J9_EVIDENCE_REFERENCE=J9-PROVIDER-ROBUSTNESS-CAMPAIGN-20260831;SHA256=47e6171eeb1fc44cf995c727d4f09107875c844e71e8b183068731cbb4c62b9d
J9_PROVIDER_ACQUISITION_MODE=MANUAL_ON_DEMAND
J9_INTEGRATION_IMPLEMENTATION_AUTHORIZED=NO
J9_LIVE_OR_SCHEDULED_OPERATION_AUTHORIZED=NO
J9_BETTING_PROJECT_CRITICAL_DEPENDENCY=NO
J9_OWNER_CONFIRMATION_REQUIRED=YES
```

Le propriétaire a refusé `KEEP_LOCAL` comme décision finale et a autorisé séparément
l'implémentation ainsi que la qualification loopback de WO-021 le
`2026-08-31T08:43:48.8202621Z`. Cette réponse ne requalifie pas la preuve arrêtée, ne choisit pas
automatiquement `PREPARE_OPTIONAL_INTEGRATION` et ne reprend pas la campagne. Les 20 tentatives
restent gelées. Le réexamen documentaire exigé par ADR-SS-002 §9 est satisfait par WO-022 et conclut
qu'une version 1.1 est nécessaire. Le propriétaire sélectionne `RESTART_FULL_D1_D2_D3`, soit une
nouvelle série autonome de 38 appels maximum et 58 cumulés. WO-019 ne sera pas rouvert : un nouveau
Work Order et un nouveau worktree seront requis après acceptation distincte de v1.1. Le propriétaire
ne pourra pas réaliser les séquences UI, aucun autre humain n'est désigné et aucune délégation à
Codex n'est reçue : l'exécution reste bloquée. Le précédent WO-017 permet néanmoins de soumettre
séparément une exécution ponctuelle `CODEX_LOCAL_UI` au choix propriétaire.

WO-021 a désormais atteint `VALIDATED` après implémentation, qualification
exclusivement locales. Le discriminant initial a été rouge en `0,08 s`; la correction ajoute un
fence monotone parent conservateur, une observation CDP du document principal exact et une
temporisation robuste aux réveils anticipés. Un premier rejeu Chromium a ensuite isolé un défaut de
teardown qui retardait la trame `TIMEOUT` : les désactivations et détachements CDP synchrones
précédaient `page.close()` sur la navigation bloquée. L'ordre a été corrigé pour fermer la page et
annuler la navigation avant le teardown CDP ; le test ciblé passe `1/1` en `5,323 s` et la suite
complète `14/14` en `101,5 s`. La requalification finale compte, pour chacun de J3/J4/J5, `21` tests
worker et `14` tests Chromium verts. J5 confirme les écarts
`requestedAt`, les arrivées serveur loopback et les écarts inter-workers `>= 3 s`, ainsi que zéro
nouvelle requête lors d'un arrêt pendant le fence. Le premier
`Verify-Local.ps1 -WithIntegrationTests` est vert avec `941` tests standards et `67` tests
d'intégration. Deux tests superviseur supplémentaires confirment ensuite que toute interruption
avant ou pendant l'attente perd la preuve temporelle et n'émet aucun `GET`; les suites ciblées
passent `40/40` pour le superviseur et `8/8` pour le coordinateur. Le `clean verify` final, après ce
garde, passe `943` tests, zéro échec, zéro erreur et quatre skips à
`2026-08-31T09:48:10Z`. Aucun accès fournisseur n'a eu lieu.

Cette qualification ne change aucun état exécutoire de WO-019 :

```text
WORK_ORDER_STATUS=STOPPED
EVIDENCE_STATUS=STOPPED
PROVIDER_CALLS_UNDER_WO019=20
EXISTING_DIRECT_ATTEMPTS_FROZEN=20
NETWORK_AUTHORIZED=NO
WO019_WORK_ORDER_RESUME_AUTHORIZED=NO_STOPPED
WO019_PROVIDER_CAMPAIGN_AUTHORIZED=NO_STOPPED
WO019_PROVIDER_CAMPAIGN_RESUME_AUTHORIZED=NO
NEW_PROVIDER_GO_GRANTED=NO
GLOBAL_OWNER_GO=CONSUMED_AND_TERMINATED_BY_STOP
WO021_STATUS=VALIDATED
WO021_OWNER_VALIDATION=RECEIVED_2026-08-31T10:12:59.5805959Z
ADR_SS_002_REEXAMINATION_STATUS=COMPLETED_NEW_VERSION_REQUIRED
ADR_SS_002_REEXAMINATION_WORK_ORDER=WO-SS-20260831-022-j9-adr-ss-002-reexamination
ADR_SS_002_V1_1_OWNER_PROFILE_DECISION=SELECT_RESTART_FULL_D1_D2_D3
ADR_SS_002_V1_1_PROFILE_DECISION_RECORDED_AT_UTC=2026-08-31T11:07:13.3823864Z
ADR_SS_002_V1_1_STATUS=DRAFT_SELECTED_PROFILE_PENDING_OWNER_ACCEPTANCE
ADR_SS_002_V1_1_MAXIMUM_NEW_DIRECT_ATTEMPTS=38
ADR_SS_002_V1_1_MAXIMUM_CUMULATIVE_DIRECT_ATTEMPTS=58
WO019_HISTORICAL_CAMPAIGN_REOPENED=NO
NEW_FULL_RESTART_CAMPAIGN_WORK_ORDER_REQUIRED=YES
NEW_FULL_RESTART_CAMPAIGN_WORK_ORDER=NOT_OPENED
OWNER_ENDPOINT_ACTIONS_AVAILABLE_FOR_NEW_CAMPAIGN=NO
OTHER_HUMAN_LOCAL_OPERATOR_DESIGNATED=NO
CAMPAIGN_EXECUTION_STATE=BLOCKED_PENDING_OPERATOR_DECISION
OPERATOR_PATH_OWNER_DECISION=PENDING
AUTOMATED_UI_ORCHESTRATION_AUTHORIZED=NO
CODEX_LOCAL_UI_EXECUTION_AUTHORIZED=NO
ADR_SS_002_V1_0_FILE_SHA256_BEFORE_V1_1_DRAFT=F68792FF722708F436957D3414317F2B5AC9FA2E2FA77BC949274FED144B3957
ADR_SS_002_V1_0_FILE_SHA256_SCOPE=HISTORICAL_V1_0_FILE_BEFORE_V1_1_DRAFT
ADR_SS_002_V1_0_SOURCE_COMMIT=af7fe179315053aedff31f0b3f6a31f6e4a8e54c
INTEGRATION_OR_PRODUCTION_AUTHORIZED=NO
```

### 2.5 Sélection du profil v1.1 après arrêt

Le `2026-08-31T11:07:13.3823864Z`, le propriétaire a sélectionné
`RESTART_FULL_D1_D2_D3` comme profil à utiliser pour l'acceptation future d'ADR-SS-002 v1.1. Cette
sélection ne modifie pas le résultat ni le statut de WO-019. La nouvelle série constitue une
campagne autonome et devra relever d'un nouveau Work Order et d'un nouveau worktree.

Le propriétaire indique également qu'il ne pourra pas réaliser les actions humaines d'interrogation
des endpoints. Sous ADR-SS-001 v1.4 et les runbooks courants, cette contrainte bloque l'exécution ;
elle ne délègue pas les gestes à Codex et n'autorise aucune automatisation UI.

```text
ADR_SS_002_V1_1_OWNER_PROFILE_DECISION=SELECT_RESTART_FULL_D1_D2_D3
ADR_SS_002_V1_1_STATUS=DRAFT_SELECTED_PROFILE_PENDING_OWNER_ACCEPTANCE
HISTORICAL_DIRECT_ATTEMPTS_FROZEN=20
NEW_SERIES_MAXIMUM_DIRECT_ATTEMPTS=38
AUDIT_MAXIMUM_CUMULATIVE_DIRECT_ATTEMPTS=58
WO019_STATUS=STOPPED
WO019_HISTORICAL_CAMPAIGN_REOPENED=NO
NEW_FULL_RESTART_CAMPAIGN_WORK_ORDER_REQUIRED=YES
NEW_FULL_RESTART_CAMPAIGN_WORK_ORDER=NOT_OPENED
OWNER_ENDPOINT_ACTIONS_AVAILABLE_FOR_NEW_CAMPAIGN=NO
OTHER_HUMAN_LOCAL_OPERATOR_DESIGNATED=NO
CAMPAIGN_EXECUTION_STATE=BLOCKED_PENDING_OPERATOR_DECISION
OPERATOR_PATH_OWNER_DECISION=PENDING
AUTOMATED_UI_ORCHESTRATION_AUTHORIZED=NO
CODEX_LOCAL_UI_EXECUTION_AUTHORIZED=NO
NETWORK_AUTHORIZED=NO
NEW_GLOBAL_OWNER_GO_GRANTED=NO
INTEGRATION_OR_PRODUCTION_AUTHORIZED=NO
J9_FINAL_DECISION=NOT_TAKEN
```

Le réseau reste bloqué. Historiquement, les quatre portes cumulatives avaient été satisfaites pour
cette campagne unique avant son exécution :

1. ADR-SS-002 accepté explicitement par le propriétaire après lecture de la revue officielle —
   acceptation enregistrée à `2026-08-30T22:54:47Z`, porte `SATISFIED` ;
2. tests et qualifications hors ligne entièrement verts ;
3. sauvegarde chiffrée fraîche au schéma Flyway V28 et restauration qualifiée sur une base isolée ;
4. go propriétaire global, explicite, à usage unique, portant le manifeste final — reçu avant
   l'ouverture de sa fenêtre `[2026-08-31T07:15:00Z,2026-08-31T08:15:00Z)`.

Le go n'autorisait aucun appel avant `2026-08-31T07:15:00Z`, après `2026-08-31T08:15:00Z`, au-delà
de 38 tentatives directes, vers un autre dossier, ni après le premier incident bloquant ou la fin de
la série. Il est maintenant consommé et terminé par l'arrêt ; il n'autorise plus aucun appel et
n'autorisait ni intégration, ni production, ni purge primaire.

## 3. Revue d'ADR-SS-001

Les parcours, routes, transports et gardes existants restent inchangés. Le plafond global de 38 est
cependant supérieur au précédent J8 de 30 et constitue le déclencheur de réexamen « modification des
bornes de volume » d'ADR-SS-001 §9.

```text
ADR_SS_001_VERSION=1.4
ADR_SS_001_MODIFICATION=NO
NEW_ADR_ID=ADR-SS-002
NEW_ADR_STATUS=ACCEPTED_V1_0
ADR_REEXAMINATION_REQUIREMENT=SATISFIED_BY_ADR_SS_002_V1_0
PROVIDER_NETWORK_AUTHORIZED_BY_ADR_ACCEPTANCE=NO
```

### 3.1 Décision propriétaire enregistrée

```text
ADR_SS_002_OWNER_DECISION=ACCEPT
OFFICIAL_SOURCE_REVIEW_ACKNOWLEDGED=YES
CORPUS_D1_D2_D3_ACCEPTED=YES
GLOBAL_MAXIMUM_DIRECT_ATTEMPTS_38_ACCEPTED=YES
ONE_GLOBAL_GO_MODEL_ACCEPTED=YES
GO_MAXIMUM_DURATION_60_MINUTES_ACCEPTED=YES
NATIVE_J4_J5_404_CONTINUATION_ACCEPTED=YES
PRIMARY_DATABASE_PURGE=NO
INTEGRATION_OR_PRODUCTION_AUTHORIZED=NO
FUTURE_VPS_PRODUCTION_OPTION_ACKNOWLEDGED=NOT_EXCLUDED_BUT_NOT_AUTHORIZED
```

La décision est bornée à ADR-SS-002 v1.0. Elle n'est pas un go global et n'autorise aucun appel
fournisseur.

## 4. Périmètre fermé

Seules les familles logiques suivantes peuvent être utilisées :

```text
SCHEDULED_EVENTS
TOURNAMENT_SCHEDULED_EVENTS
EVENT_DETAILS
EVENT_STATISTICS
EVENT_INCIDENTS
EVENT_LINEUPS
```

Routes et identités restent construites par les adaptateurs allowlistés. J4 est limité à la phase 2
sur un UUID canonique résolu côté serveur. Restent exclus :

```text
TOURNAMENT_STANDINGS
TEAM_RECENT_EVENTS
FREE_URI
FREE_PROVIDER_EVENT_ID
QUERY_EXTENSION
OTHER_ORIGIN
RESTCLIENT_FALLBACK
FLARESOLVERR
SECOND_BROWSER_CONTEXT
COOKIE_OR_STORAGE_STATE_REUSE
PROXY_OR_STEALTH
HAR_TRACE_VIDEO_SCREENSHOT_DOWNLOAD
```

## 5. Corpus immuable

| Ordre | Dossier | Provider ID | UUID canonique | Rôle dans la preuve |
|---:|---|---:|---|---|
| D1 | Cittadella — Atalanta U23 | `16691018` | `f4713f80-4769-3656-ba51-61d8ac1aa814` | Ancre du chemin complet J3 → tournoi → J4 → J5 ; date `2026-08-15`, phase `15118`, tournoi `824`, saison `99790` |
| D2 | Barracas Central — Rosario Central | `16671566` | `da075869-34d4-3d42-83d2-613583691845` | Cas historique J4/J5 incluant le vocabulaire incident `Off the ball foul` |
| D3 | Lille — Paris Saint-Germain | `16310930` | `c40066c9-987b-38d9-b415-869a453d2ad6` | Cas récent Playwright avec statistiques, incidents et compositions complètes |

Le corpus est un panel de robustesse choisi, pas un échantillon statistique. Les UUID, provider IDs,
ordre et rôle sont figés. Toute incohérence d'identité bloque la campagne avant réseau. Aucun
remplacement ou quatrième dossier n'est permis.

## 6. Séquence et plafond

Le runtime ne fournit pas de campagne multi-match. L'exécution est une série manuelle de huit
campagnes unitaires existantes :

| Séquence | Campagne | Cible | Maximum direct |
|---:|---|---|---:|
| 1 | J3 `SCHEDULED_EVENTS`, pages contiguës `1..N` | date D1 | 25 |
| 2 | J3 `TOURNAMENT_SCHEDULED_EVENTS` | tournoi/date D1 | 1 |
| 3 | J4 `EVENT_DETAILS` phase 2 | D1 | 1 |
| 4 | J5 `STATISTICS -> INCIDENTS -> LINEUPS` | D1 | 3 |
| 5 | J4 `EVENT_DETAILS` phase 2 | D2 | 1 |
| 6 | J5 `STATISTICS -> INCIDENTS -> LINEUPS` | D2 | 3 |
| 7 | J4 `EVENT_DETAILS` phase 2 | D3 | 1 |
| 8 | J5 `STATISTICS -> INCIDENTS -> LINEUPS` | D3 | 3 |
|  | **Total absolu** |  | **38** |

```text
MAX_J3_SCHEDULED_ATTEMPTS=25
MAX_TOURNAMENT_DISCOVERY_ATTEMPTS=1
MAX_J4_PHASE2_ATTEMPTS=3
MAX_J5_ATTEMPTS=9
MAX_TOTAL_DIRECT_ATTEMPTS=38
```

Le ledger est vérifié avant chaque sous-campagne. Si le prochain maximum possible dépasse le
reliquat, la préparation est refusée. Aucune tentative fictive n'est créée pour une unité non
commencée. Le contrôle global reste procédural ; toute ambiguïté découverte hors ligne bloque le
réseau et exige un Work Order de code séparé.

Les caches J3 et tournoi conservent leur TTL de 600 secondes. Ils ne sont ni supprimés, ni
invalidés, ni contournés. Un cache hit inattendu vaut zéro appel et borne la dimension concernée à
`PARTIAL` ou `NOT_MEASURED`.

## 7. Revue factuelle des sources officielles

Revue effectuée le `2026-08-30T22:29:27Z`, soit le 31 août 2026 en Europe/Paris, uniquement sur des
domaines officiels. Les observations ne constituent pas un avis juridique.

| URL officielle | Statut et fait supporté | Limite explicite de l'observation |
|---|---|---|
| `https://www.sofascore.com/en-us/terms-and-conditions` | Page officielle ; dernière mise à jour déclarée le 18 septembre 2024. Le texte restreint la charge serveur par requêtes automatisées, l'intégration, l'agrégation, le scraping, la reproduction et l'extraction substantielle sans consentement explicite, avec les réserves légales qu'il formule. | Le rendu direct est dynamique ; aucune permission propre aux endpoints du laboratoire, licence API ou limite de débit n'a été extraite. |
| `https://www.sofascore.com/robots.txt` | `text/plain`, 181 lignes. Des chemins sont exclus ; aucune correspondance textuelle `/api`, `Allow:` ou `Crawl-delay` n'est observée. | Hôte `www` uniquement. Une absence de règle n'est pas une autorisation et ne qualifie pas `api.sofascore.com`. |
| `https://api.sofascore.com/api/docs/external` | Point d'entrée officiel intitulé `Sofascore API`. | Aucun contenu extractible permettant d'établir authentification, licence, clé, tarif, rate limit ou endpoints permis. |
| `https://corporate.sofascore.com/contact` | Le formulaire officiel contient `Product -> API`. | Canal de demande seulement, sans permission publiée. |
| `https://corporate.sofascore.com/widgets` | Intégration officielle par widget/iframe décrite comme gratuite et sans limitation pour ce widget. | Ne documente pas l'extraction, le stockage ou les endpoints utilisés par le laboratoire. |

```text
OFFICIAL_SOURCE_REVIEW=COMPLETED_FACTUAL_ONLY
AUTOMATED_REQUEST_RESTRICTIONS_FOUND=YES
EXPLICIT_PERMISSION_FOR_CURRENT_LAB_ENDPOINTS_EVIDENCED=NO
API_LICENSE_OR_RATE_LIMIT_EXTRACTED=NO
OFFICIAL_API_CONTACT_CHANNEL_PRESENT=YES
LEGAL_CONCLUSION=NOT_PROVIDED
OWNER_ACKNOWLEDGEMENT_BEFORE_ADR_ACCEPTANCE=SATISFIED
```

Le propriétaire a accepté ADR-SS-002 après reconnaissance explicite de cette revue, sans exiger à
ce stade un consentement préalable via le canal officiel. Ce canal reste disponible pour une
démarche distincte. Le silence d'une source, `robots.txt`, la page API sans contenu extractible et
les campagnes techniques antérieures ne seront jamais présentés comme une permission.

## 8. Readiness hors ligne obligatoire

Toutes les commandes s'exécutent avec les flags fournisseur à `false` et ne doivent produire aucun
appel SofaScore.

```powershell
.\mvnw.cmd clean verify
.\mvnw.cmd -Pintegration-tests verify
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\Verify-Local.ps1 -WithIntegrationTests
docker compose --env-file .env config --quiet
pwsh -NoProfile -File .\scripts\Invoke-J3PlaywrightLoopbackQualification.ps1
pwsh -NoProfile -File .\scripts\Invoke-J4PlaywrightLoopbackQualification.ps1
pwsh -NoProfile -File .\scripts\Invoke-J5PlaywrightLoopbackQualification.ps1
```

La readiness confirme notamment :

- Java 25, Spring Boot 4.1.0 et Flyway V28 ;
- `server.address=127.0.0.1` ;
- toutes les activations fournisseur, Playwright, J3/J4/J5, polling et refresh à `false` par défaut ;
- allowlist exacte par profil et refus des familles différées ;
- concurrence `1`, délai `3 s`, timeout `10 s`, limite 5 Mio et zéro retry ;
- confirmations à usage unique, leases exclusives et arrêts terminaux ;
- scénarios loopback J3/J4/J5, y compris `404`, incident terminal et nettoyage ;
- aucun secret, payload brut ou artefact navigateur dans le diff et les racines runtime.

Un test qui échoue, un appel fournisseur détecté ou un besoin de modification runtime ramène le
statut à `OPEN_AWAITING_PREREQUISITES`. Aucun correctif n'est improvisé sous ce Work Order.

Cette règle a été déclenchée pendant la readiness du 2026-08-31. La qualification Playwright J3
loopback et son unique contre-qualification hors sandbox ont échoué de manière identique : `14`
tests exécutés, `0` échec d'assertion, `12` erreurs `RUNTIME_FAILURE` pendant
`campaign.close()` / la fermeture gracieuse et `2` scénarios d'arrêt opérateur réussis. Les
assertions de parcours ont précédé ces erreurs de fermeture, mais leur réussite ne neutralisait pas
le garde-fou de nettoyage. Aucun appel fournisseur n'a été exécuté.

WO-020 a ensuite établi la cause circulaire : le worker émettait `CLOSED` puis attendait l'EOF,
alors que le parent attendait ou terminait l'arbre avant de produire cet EOF ; le timeout gracieux
était aussi plafonné à tort à `2 s`. La correction du seul superviseur conserve le worker, le
protocole et les endpoints, puis les qualifications loopback J3, J4 et J5 ont chacune réussi leurs
`14` tests. Le propriétaire a validé cette preuve runtime WO-020 et autorisé son déplacement
vers les Work Orders terminés le 2026-08-31 à `00:41:58Z`. Cette validation qualifiait le correctif
runtime, mais ne remplaçait pas le rejeu de readiness propre à WO-019.

Le propriétaire a ensuite autorisé séparément la reprise de WO-019 le 2026-08-31. Cette décision a
d'abord placé le Work Order à `READY_FOR_OFFLINE_READINESS`, avec
`OFFLINE_READINESS=AUTHORIZED_NOT_EXECUTED`. Elle autorisait uniquement le rejeu des prérequis
locaux et maintenait `WO019_PROVIDER_CAMPAIGN_RESUME_AUTHORIZED=NO`.

Le rejeu hors ligne distinct a ensuite réussi : `clean verify` compte `931` tests, `0` échec,
`0` erreur et `4` skips ; la suite d'intégration compte `67` tests, `0` échec, `0` erreur et
`0` skip ; `Verify-Local.ps1 -WithIntegrationTests` rend `PASS`, confirme les intégrations à `YES`
et le réseau SofaScore à `NO` ; Compose silencieux rend `PASS` ; les qualifications J3, J4 et J5
lancées sous `pwsh` comptent chacune `14` tests, `0` échec, `0` erreur et `0` skip. Aucun accès
fournisseur n'a eu lieu. La readiness devient
`PASS_REEXECUTED_AFTER_VALIDATED_WO020` et WO-019 passe alors, et seulement alors, à
`READY_FOR_V28_BACKUP_RESTORE`.

Le cycle sauvegarde/restauration chiffré V28 a ensuite été qualifié dans une exécution interactive
PowerShell 7 native, distincte du rejeu hors ligne et minimisée dans le journal. WO-019 passe à
`READY_FOR_GLOBAL_OWNER_GO`, mais le go global reste `NOT_GRANTED`. La campagne fournisseur, le
réseau, l'intégration et la production restent non autorisés.

## 9. Sauvegarde/restauration V28

Après readiness et avant le go :

1. arrêter l'application et vérifier que le port 8087 est libre ;
2. choisir un nouveau fichier `.age` absolu hors dépôt, sans écrasement ;
3. exécuter `scripts/Backup-Restore-J6.ps1` avec saisie interactive de la phrase secrète `age` ;
4. restaurer dans la base temporaire créée par le script ;
5. exiger Flyway V28, comptes, couverture et fingerprints identiques, y compris le ledger J8 ;
6. conserver seulement dans la preuve Git les hashes et métadonnées minimisées ;
7. ne lancer aucune purge primaire.

La preuve fraîche exigée est maintenant qualifiée. Le journal conserve ses seuls hashes,
horodatages, tailles et contrôles minimisés autorisés, sans chemin, nom de fichier, manifeste JSON,
phrase secrète, dump, payload ou credential. Son résultat fonctionnel est :

```text
J6_BACKUP_RESULT=QUALIFIED
J6_RESTORE_QUALIFIED=YES
FLYWAY_VERSION=28
PRIMARY_DATABASE_PURGE=NO
```

Une ancienne sauvegarde V22, même qualifiée, ne satisfait pas cette porte.

## 10. Go propriétaire global

Après acceptation ADR, readiness et sauvegarde, le manifeste read-only final est soumis au
propriétaire. Le go doit identifier explicitement :

```text
WORK_ORDER=WO-SS-20260831-019-j9-provider-robustness
ADR=ADR-SS-002_v1.0
ADR_STATUS=ACCEPTED
TARGET_PROVIDER_EVENT_IDS=16691018,16671566,16310930
TARGET_CANONICAL_EVENT_IDS=f4713f80-4769-3656-ba51-61d8ac1aa814,da075869-34d4-3d42-83d2-613583691845,c40066c9-987b-38d9-b415-869a453d2ad6
MAXIMUM_DIRECT_ATTEMPTS=38
WINDOW_UTC=[FROM,TO)
MAXIMUM_WINDOW_DURATION=60m
EXECUTION_ACTOR=<explicit>
GO_USE=ONE_TIME
```

`ADR=ADR-SS-002_v1.0` et `ADR_STATUS=ACCEPTED` attestent uniquement l'ADR déjà acceptée ; ces
champs ne constituent pas le go global, qui reste `NOT_GRANTED` tant que le propriétaire ne l'a pas
accordé dans un bloc distinct.

Le go expire à `TO`, au premier incident global ou à la fin de la série. Il ne survit pas à un
redémarrage décidé après incident. Les confirmations techniques propres à chaque sous-campagne
restent obligatoires et utilisent des phrases, IDs et claims neufs.

## 11. Contrat d'exécution

```text
WORKER_JVM=FRESH_PER_SUBCAMPAIGN
CHROMIUM=FRESH_PER_SUBCAMPAIGN
BROWSER_CONTEXT=FRESH_NON_PERSISTENT_PER_SUBCAMPAIGN
MAXIMUM_CONCURRENCY=1
MINIMUM_DELAY_BETWEEN_DIRECT_STARTS=3s
REQUEST_TIMEOUT=10s
MAX_RESPONSE_BODY=5MiB
CONFIRMATION_TTL=5m
RETRY=0
FALLBACK=0
```

La séquence est strictement ordonnée. Le segment D2/D3 ne commence que si le chemin D1 n'a pas
déclenché d'arrêt global. Il n'existe aucune boucle entre dossiers. Chaque unité est préparée,
confirmée, exécutée, auditée et nettoyée avant la suivante.

## 12. Outcomes, `404` et arrêts

### 12.1 `404` natif

- J3 : `ENDPOINT_UNAVAILABLE`, arrêt de la pagination et de toute la série ;
- J4 phase 2 : `COMPLETED_UNAVAILABLE`, sans parsing ni retry, puis poursuite globale autorisée ;
- J5 : famille `UNAVAILABLE`, puis famille suivante ;
- tout `404` J4/J5 borne le verdict global à `PARTIAL_BOUNDED`.

### 12.2 Arrêt global immédiat

- `401`, `403`, `429`, `5xx`, timeout ou redirection ;
- HTML, challenge, contenu inattendu ou origine/route/méthode différente ;
- contenu sensible ou corps supérieur à 5 Mio ;
- JSON ou schéma incompatible ;
- erreur de transport, persistance, parsing, traitement ou ledger ;
- tentative sans outcome, identité incohérente ou perte de lease ;
- concurrence supérieure à un, délai inférieur à trois secondes ou nettoyage incomplet ;
- demande opérateur, plafond atteint, fenêtre expirée ou go invalide.

Les campagnes futures non commencées sont documentées `NOT_STARTED_AFTER_GLOBAL_STOP`. Elles ne
créent ni appel ni ligne de tentative fictive. Aucun retry, fallback, import de remplacement,
nouveau contexte ou dossier de substitution n'est permis.

## 13. Preuve et métriques

La population est le ledger exact dans `[FROM,TO)`, avec manifeste, comptes et hash de population
gelés. Le rapport distinct J9 consignera au minimum :

- campagnes prévues, commencées et terminales ;
- tentatives, réponses, parsings, 404, refus et erreurs ;
- nombre d'appels par famille, segment et dossier ;
- latences `n`, min, p50, p95 et max sans masquer les petits dénominateurs ;
- pages J3, cache hits, contiguïté et terminal `hasNextPage` ;
- versions de parseur, incompatibilités et contenus inattendus ;
- présence directe et statut des cinq composants de chaque dossier ;
- complétude `COMPLETE`, `PARTIAL`, `EMPTY_VALID`, `UNAVAILABLE` ou `UNKNOWN` ;
- snapshots insérés/dédupliqués, hashes, heures et provenance, sans payload ;
- dossiers exploitables et strictement complets selon la définition J8 ;
- concurrence et délai observés, durée de fenêtre et gestes opérateur ;
- nettoyage des processus, listeners et artefacts navigateur ;
- limites `NOT_MEASURED`, dont exactitude externe, valeur analytique relative, coût comparatif,
  accès depuis un VPS et compatibilité de production.

Les exports sont produits deux fois avec les mêmes bornes et `asOf`, puis comparés octet pour
octet. Les fichiers runtime restent sous `exports/` ignoré. Seuls le rapport minimisé, son SHA-256,
le hash de population et la validation humaine sont versionnés après la campagne.

## 14. Verdict déterministe

```text
PASS
  = série terminée sous 38
  + aucun incident bloquant
  + toutes les réponses non-404 parse-compatibles et persistées
  + trois dossiers exploitables
  + audit et nettoyage conformes

PARTIAL_BOUNDED
  = uniquement 404 natifs, cache hit limitant ou lacune explicitement bornée
  + aucune violation de sécurité
  + preuve sûre et auditable

STOPPED
  = condition d'arrêt ou prérequis violé
```

La validation du processus et le résultat fournisseur restent séparés. Une preuve correctement
arrêtée peut être valide comme audit tout en produisant `STOPPED`.

## 15. Revue humaine et décision J9

Après arrêt et reverrouillage complet, l'opérateur vérifie pour chaque dossier identité, date,
compétition, état, score, détail, trois familles J5, complétude, provenance, snapshot, hash, parseur
et heure de réception. Aucun payload, cookie, jeton ou artefact navigateur n'est copié dans Git.

La preuve met à jour la matrice WO-018, mais ne choisit pas automatiquement J9. Le propriétaire
reçoit `ABANDON`, `KEEP_LOCAL` et `PREPARE_OPTIONAL_INTEGRATION` avec la recommandation dérivée et
confirme explicitement sa décision.

Une future production VPS demeure une option d'architecture non exclue. Elle n'est pas qualifiée
par cette campagne Windows. Une étude de faisabilité ultérieure et distincte comparera le push
local optionnel à une topologie VPS Playwright ; si la décision finale J9 le justifie, ADR-SS-003
portera ensuite le choix d'architecture. Une nouvelle revue des droits d'usage et une qualification
propre au réseau et au runtime VPS resteront obligatoires.

## 16. Livraison Git

Lot préparatoire prévu :

```text
docs(j9): propose bounded provider robustness evidence
```

Acceptation propriétaire de l'ADR :

```text
docs(j9): accept bounded robustness ADR
```

Après readiness/sauvegarde, puis après campagne et revue, des commits distincts consigneront les
preuves réelles. Aucun push, PR ou merge vers `main` n'est autorisé. La branche
`codex/j9-decision` ne sera avancée qu'après validation des commits de WO-019.

## 17. Portes de statut

```text
OPEN_AWAITING_PREREQUISITES
  -> READY_FOR_ADR_OWNER_DECISION
  -> READY_FOR_OFFLINE_READINESS
  -> READY_FOR_V28_BACKUP_RESTORE
  -> READY_FOR_GLOBAL_OWNER_GO
  -> QUALIFICATION_RUNNING
  -> READY_FOR_HUMAN_REVIEW
  -> VALIDATED
```

Terminaux alternatifs :

```text
CANCELLED_BEFORE_NETWORK
STOPPED_BY_SAFETY_RULE
INVALID_EVIDENCE
```

WO-019 ne passe à `VALIDATED` qu'après preuve versionnée et décision humaine sur sa validité. Il est
alors déplacé vers `docs/work_orders/completed`. Ce déplacement n'autorise aucun nouvel appel.

## 18. Journal d'exécution

Le bloc suivant conserve la photographie historique prise à l'arrêt de readiness puis à la
validation de WO-020. En particulier, `WO019_CAMPAIGN_RESUME_AUTHORIZED=NO` décrit correctement la
porte encore fermée à cet instant ; l'autorisation propriétaire distincte et l'état courant sont
consignés après ce bloc sans réécrire cette preuve.

```text
BRANCH_BASE=a47c932
ADR_SS_002_DRAFT=CREATED
ADR_SS_002_VERSION=1.0
ADR_OWNER_DECISION=ACCEPTED
ADR_OWNER_DECISION_RECORDED_AT_UTC=2026-08-30T22:54:47Z
OFFICIAL_SOURCE_REVIEW=COMPLETED_FACTUAL_ONLY
STANDARD_VERIFY=PASS_928_TESTS_0_FAILURE_0_ERROR_4_SKIPPED
STANDARD_VERIFY_COMMAND=.\mvnw.cmd clean verify
DIFF_CHECK=PASS
LOCAL_MARKDOWN_LINKS=PASS
SECRET_SCAN=PASS_NO_CREDENTIAL_PATTERN_IN_J9_FILES
LOOPBACK_AND_NETWORK_DEFAULTS_CHECK=PASS
INTEGRATION_VERIFY=PASS_67_TESTS_0_FAILURE_0_ERROR
VERIFY_LOCAL_WITH_INTEGRATION=PASS_928_STANDARD_67_INTEGRATION
DOCKER_COMPOSE_CONFIG=PASS
LOOPBACK_J3=FAIL_14_TESTS_0_FAILURE_12_ERRORS_0_SKIPPED_2_PASS
LOOPBACK_J3_ERROR_CLASS=RUNTIME_FAILURE_AT_GRACEFUL_CLOSE
LOOPBACK_J3_OUT_OF_SANDBOX_COUNTER_QUALIFICATION=IDENTICAL_FAILURE
LOOPBACK_J3_OPERATOR_STOP_SCENARIOS=PASS_2
LOOPBACK_J3_COUNTER_QUALIFICATION_REPORT_XML_SHA256=6567dbdf590dc5cdd76cfa9c80452324c92fbb00234151c537d943267ad94f37
LOOPBACK_J3_COUNTER_QUALIFICATION_REPORT_TEXT_SHA256=a9358021bf2299a954138ba69982f98628e4b4f2fcda72038d77c4f7806b62a3
LOOPBACK_J4=NOT_RUN_AFTER_J3_READINESS_FAILURE
LOOPBACK_J5=NOT_RUN_AFTER_J3_READINESS_FAILURE
V28_BACKUP_RESTORE=NOT_RUN_READINESS_BLOCKED
GLOBAL_OWNER_GO=NOT_GRANTED
PROVIDER_CALLS_UNDER_WO019=0
POST_FAILURE_RESIDUAL_OWNED_PROCESS_COUNT=0
POST_FAILURE_LISTENER_127_0_0_1_8087_COUNT=0
POST_FAILURE_FORBIDDEN_BROWSER_ARTIFACTS=NONE_FOUND
GRACEFUL_CLOSE_RUNTIME_WORK_ORDER_REQUIRED=SATISFIED_BY_VALIDATED_WO020
GRACEFUL_CLOSE_RUNTIME_WORK_ORDER=WO-SS-20260831-020-j9-playwright-graceful-close
GRACEFUL_CLOSE_RUNTIME_WORK_ORDER_STATUS=VALIDATED
GRACEFUL_CLOSE_RUNTIME_WORK_ORDER_LOCATION=docs/work_orders/completed/WO-SS-20260831-020-j9-playwright-graceful-close.md
GRACEFUL_CLOSE_RUNTIME_CORRECTION_LOCAL_READINESS=PASS
GRACEFUL_CLOSE_RUNTIME_WORK_ORDER_OWNER_VALIDATION=RECEIVED_2026-08-31T00:41:58Z
GRACEFUL_CLOSE_RUNTIME_OWNER_REVIEW=SATISFIED
WO020_ROOT_CAUSE=CLOSED_ACKNOWLEDGED_WORKER_WAITING_FOR_PARENT_EOF
WO020_SUPERVISOR_TESTS=PASS_31_OF_31
WO020_WORKER_PROTOCOL_TESTS=PASS_10_OF_10
WO020_WORKER_SECURITY_TESTS=PASS_1_OF_1
WO020_LOOPBACK_J3=PASS_14_OF_14
WO020_LOOPBACK_J4=PASS_14_OF_14
WO020_LOOPBACK_J5=PASS_14_OF_14
WO020_STANDARD_CLEAN_VERIFY=PASS_931_TESTS_0_FAILURE_0_ERROR_4_SKIPPED
WO020_INTEGRATION_VERIFY=PASS_67_TESTS_0_FAILURE_0_ERROR
WO020_TESTCONTAINERS_FLYWAY_SCHEMA=V28_CONFIRMED_NOT_BACKUP_RESTORE
WO020_DOCKER_COMPOSE_CONFIG=PASS
WO020_POST_LOOPBACK_RESIDUAL_OWNED_PROCESS_COUNT=0
WO020_POST_LOOPBACK_LISTENER_127_0_0_1_8087_COUNT=0
WO020_J5_FORBIDDEN_BROWSER_ARTIFACT_SCANNER=PASS
WO020_PROVIDER_ACCESS_PERFORMED=NO
WO019_CAMPAIGN_RESUME_AUTHORIZED=NO
NETWORK_AUTHORIZED=NO
```

ADR-SS-002 v1.0 reste accepté. Les vérifications standards, PostgreSQL/Testcontainers,
`Verify-Local` et Compose sont vertes et n'ont produit aucun appel fournisseur. La qualification
Playwright loopback initiale reste conservée comme preuve de l'arrêt de sécurité. WO-020 a depuis
établi la cause, qualifié sa correction et réussi les trois scripts loopback. Le propriétaire l'a
validé et a autorisé son déplacement vers les Work Orders terminés le 2026-08-31 à `00:41:58Z`.
L'audit post-correction ne trouve aucun processus possédé résiduel, aucun listener sur
`127.0.0.1:8087` et le scanner J5 ne trouve aucun artefact navigateur interdit.
Cette validation a qualifié le correctif runtime, sans exécuter le rejeu de readiness propre à
WO-019 ni le cycle chiffré sauvegarde/restauration V28.

Autorisation d'ouverture de WO-020 enregistrée le 2026-08-31 :

```text
J9_RUNTIME_WORK_ORDER_OPENING=AUTHORIZE
J9_RUNTIME_SCOPE=DIAGNOSE_AND_CORRECT_PLAYWRIGHT_GRACEFUL_CLOSE_AND_PROCESS_TREE_CLEANUP
J9_PROVIDER_NETWORK_AUTHORIZED=NO
J9_WO019_PROVIDER_CAMPAIGN_RESUME_AUTHORIZED=NO
J9_INTEGRATION_OR_PRODUCTION_AUTHORIZED=NO
```

Le bloc propriétaire exact de validation et de clôture est conservé dans le
[Work Order WO-020 validé](../completed/WO-SS-20260831-020-j9-playwright-graceful-close.md). Sa
validation satisfait le prérequis runtime uniquement ; elle ne valait pas, à elle seule,
autorisation de reprendre WO-019.

Autorisation propriétaire distincte de reprise reçue le 2026-08-31, sans heure ajoutée :

> J’autorise la reprise de WO-019

```text
OWNER_WO019_RESUME_AUTHORIZATION_RECEIVED_ON=2026-08-31
WORK_ORDER_STATUS=READY_FOR_OFFLINE_READINESS
WO019_WORK_ORDER_RESUME_AUTHORIZED=YES
WO019_PROVIDER_CAMPAIGN_AUTHORIZED=NO_PENDING_GLOBAL_GO
WO019_PROVIDER_CAMPAIGN_RESUME_AUTHORIZED=NO
OFFLINE_READINESS=AUTHORIZED_NOT_EXECUTED
V28_BACKUP_RESTORE=NOT_EXECUTED
NEXT_GATE=OFFLINE_READINESS_REEXECUTION
NETWORK_AUTHORIZED=NO
GLOBAL_OWNER_GO=NOT_GRANTED
OWNER_GO_CONSUMED=NO
EVIDENCE_STATUS=DRAFT
INTEGRATION_OR_PRODUCTION_AUTHORIZED=NO
PROVIDER_CALLS_UNDER_WO019=0
```

Cette décision a repris le Work Order et autorisé son rejeu hors ligne ; elle n'a pas prévalidé le
résultat de ce rejeu. La preuve distincte exécutée après l'autorisation est :

```text
WO019_OFFLINE_READINESS_REEXECUTION=PASS
WO019_OFFLINE_READINESS_REEXECUTED_ON=2026-08-31
WORK_ORDER_STATUS=READY_FOR_V28_BACKUP_RESTORE
OFFLINE_READINESS=PASS_REEXECUTED_AFTER_VALIDATED_WO020
WO019_STANDARD_VERIFY_COMMAND=.\mvnw.cmd clean verify
WO019_STANDARD_VERIFY=PASS_931_TESTS_0_FAILURE_0_ERROR_4_SKIPPED
WO019_INTEGRATION_VERIFY_COMMAND=.\mvnw.cmd -Pintegration-tests verify
WO019_INTEGRATION_VERIFY=PASS_67_TESTS_0_FAILURE_0_ERROR_0_SKIPPED
WO019_VERIFY_LOCAL_COMMAND=powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\Verify-Local.ps1 -WithIntegrationTests
WO019_VERIFY_LOCAL=PASS
WO019_VERIFY_LOCAL_INTEGRATIONS=YES
WO019_VERIFY_LOCAL_SOFASCORE_NETWORK=NO
WO019_DOCKER_COMPOSE_CONFIG_COMMAND=docker compose --env-file .env config --quiet
WO019_DOCKER_COMPOSE_CONFIG=PASS
WO019_LOOPBACK_J3_COMMAND=pwsh -NoProfile -File .\scripts\Invoke-J3PlaywrightLoopbackQualification.ps1
WO019_LOOPBACK_J3=PASS_14_TESTS_0_FAILURE_0_ERROR_0_SKIPPED
WO019_LOOPBACK_J3_PROVIDER_ACCESS_PERFORMED=NO
WO019_LOOPBACK_J4_COMMAND=pwsh -NoProfile -File .\scripts\Invoke-J4PlaywrightLoopbackQualification.ps1
WO019_LOOPBACK_J4=PASS_14_TESTS_0_FAILURE_0_ERROR_0_SKIPPED
WO019_LOOPBACK_J4_PROVIDER_ACCESS_PERFORMED=NO
WO019_LOOPBACK_J5_COMMAND=pwsh -NoProfile -File .\scripts\Invoke-J5PlaywrightLoopbackQualification.ps1
WO019_LOOPBACK_J5=PASS_14_TESTS_0_FAILURE_0_ERROR_0_SKIPPED
WO019_LOOPBACK_J5_PROVIDER_ACCESS_PERFORMED=NO
WO019_PROVIDER_ACCESS_PERFORMED=NO
WO019_PROVIDER_CAMPAIGN_RESUME_AUTHORIZED=NO
V28_BACKUP_RESTORE=NOT_EXECUTED
NEXT_GATE=V28_BACKUP_RESTORE
NETWORK_AUTHORIZED=NO
GLOBAL_OWNER_GO=NOT_GRANTED
OWNER_GO_CONSUMED=NO
EVIDENCE_STATUS=DRAFT
INTEGRATION_OR_PRODUCTION_AUTHORIZED=NO
```

À l'issue de ce rejeu, WO-019 était `READY_FOR_V28_BACKUP_RESTORE`. Une première session
interactive hôte lancée par Codex a atteint la première invite `age`, mais son onglet n'a pas été
rattaché à l'interface. Elle a été interrompue avant toute saisie de phrase secrète. L'audit
immédiat après interruption a confirmé l'absence d'archive, de manifeste, de fichier partiel, de
base temporaire et de processus résiduel :

```text
WO019_V28_DETACHED_ATTEMPT=STOPPED_AT_FIRST_AGE_PROMPT
WO019_V28_DETACHED_ATTEMPT_PASSPHRASE_ENTERED=NO
WO019_V28_DETACHED_ATTEMPT_PLAINTEXT_DUMP_FILE_CREATED=NO
WO019_V28_DETACHED_POST_STOP_AGE_PROCESS_COUNT=0
WO019_V28_DETACHED_POST_STOP_PG_DUMP_PROCESS_COUNT=0
WO019_V28_DETACHED_POST_STOP_PARTIAL_FILE_COUNT=0
WO019_V28_DETACHED_POST_STOP_FINAL_FILE_COUNT=0
WO019_V28_DETACHED_POST_STOP_MANIFEST_FILE_COUNT=0
WO019_V28_DETACHED_POST_STOP_TEMPORARY_DATABASE_COUNT=0
WO019_V28_DETACHED_PROVIDER_ACCESS_PERFORMED=NO
```

La tentative manuelle suivante, lancée dans le terminal Codex restreint, s'est arrêtée plus tôt au
préflight de visibilité du répertoire, avant `age`, `pg_dump`, création de fichier ou base
temporaire :

```text
WO019_V28_RESTRICTED_TERMINAL_ATTEMPT=STOPPED_AT_PREFLIGHT
WO019_V28_RESTRICTED_TERMINAL_STOP_REASON=DESTINATION_DIRECTORY_NOT_VISIBLE
WO019_V28_RESTRICTED_TERMINAL_STOPPED_BEFORE_AGE=YES
WO019_V28_RESTRICTED_TERMINAL_STOPPED_BEFORE_PG_DUMP=YES
WO019_V28_RESTRICTED_TERMINAL_FILE_CREATED=NO
WO019_V28_RESTRICTED_TERMINAL_TEMPORARY_DATABASE_CREATED=NO
WO019_V28_RESTRICTED_TERMINAL_PROVIDER_ACCESS_PERFORMED=NO
```

Ces deux arrêts locaux ne sont ni des appels ni des retries fournisseur. L'exécution qualifiée a
ensuite été réalisée dans PowerShell 7 natif interactif. La preuve versionnée reste volontairement
limitée aux métadonnées suivantes :

```text
WO019_V28_EXECUTION_ENVIRONMENT=POWERSHELL_7_NATIVE_INTERACTIVE
J6_BACKUP_RESULT=QUALIFIED
J6_BACKUP_CREATED_AT=2026-08-31T06:44:41.9669041Z
J6_BACKUP_QUALIFIED_AT=2026-08-31T06:46:07.7013794Z
J6_BACKUP_CIPHER_SHA256=2b1402d12274f3e9a646aa6134f8cf8bee7a5e11b3249e8dff5c63040cb89d34
J6_BACKUP_MANIFEST_SHA256=7d112e4db804125656a54c61edd8c1eb9417f95e8b7c3d4df88d99d92cde6e62
J6_BACKUP_ARCHIVE_BYTES=6996157
J6_BACKUP_MANIFEST_BYTES=2202
FLYWAY_VERSION=28
J6_BACKUP_COVERAGE_MAX_SNAPSHOT_ID=794
J6_BACKUP_COVERAGE_RECEIVED_AT=2026-08-30T20:30:46.412Z
J6_RESTORE_QUALIFIED=YES
J6_SOURCE_RESTORED_PROPERTY_MISMATCHES=0
J6_RAW_PAYLOAD_INTEGRITY_FAILURES=0
J6_BACKUP_PARTIAL_FILE_COUNT=0
J6_TEMPORARY_RESTORE_DATABASE_RESIDUAL_COUNT_INDEPENDENTLY_VERIFIED=0
CONNECTOR_CONTROL=SAFE
LISTENER_127_0_0_1_8087=FREE
PROVIDER_ACCESS_PERFORMED=NO
PRIMARY_DATABASE_PURGE=NO
WORK_ORDER_STATUS=READY_FOR_GLOBAL_OWNER_GO
V28_BACKUP_RESTORE=QUALIFIED
NEXT_GATE=GLOBAL_OWNER_GO
WO019_PROVIDER_CAMPAIGN_RESUME_AUTHORIZED=NO
NETWORK_AUTHORIZED=NO
GLOBAL_OWNER_GO=NOT_GRANTED
OWNER_GO_CONSUMED=NO
EVIDENCE_STATUS=DRAFT
PROVIDER_CALLS_UNDER_WO019=0
INTEGRATION_OR_PRODUCTION_AUTHORIZED=NO
```

Aucun chemin externe, nom de fichier, manifeste JSON, phrase secrète, dump, payload ou credential
n'est versionné. À l'issue de cette porte V28 et avant la réception décrite ci-dessous, WO-019 était
`READY_FOR_GLOBAL_OWNER_GO` ; la campagne fournisseur, le réseau, la consommation du go,
l'intégration et la production restaient non autorisés.

## 16. Go propriétaire global unique reçu

Le propriétaire a accordé le go global unique suivant. Le second bloc rappelant l'état « jusqu'à
réception valide » décrit l'état immédiatement antérieur et est supersédé par cette réception pour
la seule fenêtre déclarée :

```text
J9_WO019_GLOBAL_OWNER_GO_DECISION=GRANT
WORK_ORDER=WO-SS-20260831-019-j9-provider-robustness
ADR=ADR-SS-002_v1.0
ADR_STATUS=ACCEPTED
TARGET_PROVIDER_EVENT_IDS=16691018,16671566,16310930
TARGET_CANONICAL_EVENT_IDS=f4713f80-4769-3656-ba51-61d8ac1aa814,da075869-34d4-3d42-83d2-613583691845,c40066c9-987b-38d9-b415-869a453d2ad6
MAXIMUM_DIRECT_ATTEMPTS=38
WINDOW_UTC=[2026-08-31T07:15:00Z,2026-08-31T08:15:00Z)
MAXIMUM_WINDOW_DURATION=60m
EXECUTION_ACTOR=CODEX_LOCAL_AGENT
GO_USE=ONE_TIME
J9_PROVIDER_NETWORK_AUTHORIZED=YES_WITHIN_THIS_MANIFEST_AND_WINDOW_ONLY
J9_WO019_PROVIDER_CAMPAIGN_RESUME_AUTHORIZED=YES_WITHIN_THIS_MANIFEST_AND_WINDOW_ONLY
REPLACEMENT_DOSSIER_ALLOWED=NO
PRIMARY_DATABASE_PURGE=NO
J9_INTEGRATION_OR_PRODUCTION_AUTHORIZED=NO
```

État au moment de la réception, avant toute préparation fraîche ou tentative fournisseur :

```text
GLOBAL_OWNER_GO_OBSERVED_AT_UTC=2026-08-31T07:10:43.748Z
WORK_ORDER_STATUS=READY_FOR_PROVIDER_CAMPAIGN
NEXT_GATE=FRESH_TECHNICAL_CONFIRMATION_J3
OWNER_GO_CONSUMED=NO
OWNER_GO_CONSUMPTION_POINT=FIRST_ACCEPTED_J3_EXECUTION_CLAIM
OWNER_GO_CONSUMPTION_EFFECT=IRREVERSIBLE_FOR_SERIES
PROVIDER_CALLS_UNDER_WO019=0
LEDGER_BASELINE_DIRECT_ATTEMPTS=0
LEDGER_RULE=ATTEMPTS_SO_FAR_PLUS_NEXT_MAX_LE_38
REPLACEMENT_DOSSIER_ALLOWED=NO
INTEGRATION_OR_PRODUCTION_AUTHORIZED=NO
```

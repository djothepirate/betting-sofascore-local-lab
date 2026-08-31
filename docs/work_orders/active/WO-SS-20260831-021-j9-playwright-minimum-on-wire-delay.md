# WO-SS-20260831-021 — Mesure et garantie du délai minimal Playwright entre départs réseau

- **Statut :** `READY_FOR_OWNER_REVIEW`
- **Date d'ouverture :** 2026-08-31
- **Jalon :** J9 — prérequis runtime après arrêt de WO-019
- **Base locale :** `216b184b96055f53c48f3c9d0b5a6d21d03723f0`
- **Branche :** `codex/j9-playwright-minimum-delay`
- **Worktree dédié :** `.tmp/j9-playwright-minimum-delay`
- **Work Orders liés :** WO-018 `IN_DEVELOPMENT`, WO-019 `STOPPED`, WO-020 `VALIDATED`
- **ADR applicables :** `ADR-SS-001 v1.4`, `ADR-SS-002 v1.0`
- **Réseau fournisseur :** `NOT_AUTHORIZED`
- **Reprise de WO-019 :** `NOT_AUTHORIZED`
- **Nouveau go fournisseur :** `NOT_GRANTED`
- **Tentatives WO-019 gelées :** `20`
- **Implémentation WO-021 :** `IMPLEMENTED_AND_LOCALLY_QUALIFIED`
- **Intégration ou production :** `NOT_AUTHORIZED`
- **Endpoint, transport, protocole, schéma ou migration :** `NO_SCOPE_EXPANSION_AUTHORIZED`
- **Lacune de mesure :** `CORRECTED_AND_QUALIFIED_ON_LOOPBACK`
- **Violation on-wire sous trois secondes :** `NOT_ESTABLISHED`
- **Autorisation propriétaire d'implémentation :** `RECEIVED_2026-08-31T08:43:48.8202621Z`
- **Validation propriétaire de clôture :** `PENDING`

## 1. Objectif

Rendre mesurable et démontrable l'invariant suivant, sans accès au fournisseur :

```text
Pour deux navigations fournisseur directes consécutives,
le second départ réseau observable du document principal ne survient jamais
moins de 3 000 000 000 ns après le précédent.
```

Le lot doit :

- établir un point de mesure Playwright/CDP lié à la requête exacte du document principal ;
- garantir au moins trois secondes entre deux départs réseau observables consécutifs ;
- confirmer cette garantie par l'heure monotone d'arrivée sur un serveur loopback ;
- conserver la concurrence `1`, le timeout réseau `10 s`, l'absence de retry et les transports
  Playwright existants ;
- qualifier J3, la découverte tournoi, J4 et J5 exclusivement en loopback ;
- produire une preuve permettant une future décision propriétaire distincte sur WO-019.

WO-021 n'autorise ni une nouvelle campagne fournisseur, ni la reprise de WO-019, ni l'intégration,
ni la production, ni un déploiement VPS.

## 2. Fondement de l'ouverture

Le plan J9 accepté impose l'arrêt de WO-019 et un Work Order de code séparé lorsque le contrôle
procédural existant ne suffit pas à démontrer le plafond ou la cadence. Cette condition s'est
réalisée après J5 D1.

```text
WO021_OPENING_BASIS=APPROVED_J9_PLAN_RUNTIME_CONTROL_INSUFFICIENT_TRIGGER
WO021_DOCUMENT_AND_BRANCH_OPENING_AUTHORIZED=YES
WO021_IMPLEMENTATION_AUTHORIZED=NO_PENDING_OWNER_DECISION
WO019_EVIDENCE_STATUS=STOPPED
WO019_GLOBAL_OWNER_GO=CONSUMED_AND_TERMINATED_BY_STOP
WO019_OWNER_GO_REUSABLE=NO
WO019_TOTAL_DIRECT_ATTEMPTS_FROZEN=20
WO019_D2_D3_PROVIDER_ATTEMPTS=0
WO019_PROVIDER_CAMPAIGN_RESUME_AUTHORIZED=NO
NEW_PROVIDER_GO_GRANTED=NO
```

La preuve d'entrée est le rapport
[`J9-PROVIDER-ROBUSTNESS-CAMPAIGN-20260831`](../../validation/J9-PROVIDER-ROBUSTNESS-CAMPAIGN-20260831.md),
SHA-256 `47e6171eeb1fc44cf995c727d4f09107875c844e71e8b183068731cbb4c62b9d`.

## 3. Constat technique d'entrée

J5 D1 a terminé fonctionnellement avec trois réponses `HTTP 200`, trois parsings compatibles et
aucun retry. Les timestamps worker persistés sont :

```text
EVENT_STATISTICS_REQUESTED_AT_UTC=2026-08-31T07:58:55.179Z
EVENT_INCIDENTS_REQUESTED_AT_UTC=2026-08-31T07:58:58.146Z
EVENT_LINEUPS_REQUESTED_AT_UTC=2026-08-31T07:59:01.147Z
STATISTICS_TO_INCIDENTS_REQUESTED_AT_GAP_MS=2967
INCIDENTS_TO_LINEUPS_REQUESTED_AT_GAP_MS=3001
COORDINATOR_ADMISSION_DELAY=AT_LEAST_3_SECONDS
WORKER_REQUESTED_AT_CAPTURE_POINT=BEFORE_CDP_GUARD_AND_PAGE_NAVIGATE
EXACT_PROVIDER_NETWORK_START_PERSISTED=NO
MINIMUM_THREE_SECOND_PROVIDER_START_DELAY=NOT_MEASURED
STRICT_PROVIDER_START_DELAY_VIOLATION_PROVED=NO
WO019_GLOBAL_EVIDENCE_RESULT=STOPPED
```

`ManualProviderRequestCoordinator.beginRequest()` espace les admissions logiques côté application.
Le worker capture actuellement `requested_at` avant la création et l'activation des gardes CDP,
avant `page.navigate` et avant la reprise de la route admise. Une variation de préparation
Playwright peut donc réduire ou augmenter l'écart entre deux timestamps persistés sans mesurer
l'écart entre les départs réseau réels.

L'écart calculé de `2 967 ms` ne prouve pas qu'une requête est partie trop tôt. Il ne prouve pas non
plus le respect du minimum de trois secondes. Le défaut établi est l'absence d'une preuve mesurable
de l'invariant ; l'arrêt fail-safe de WO-019 reste donc valide.

## 4. Décision propriétaire requise

La valeur recommandée est `AUTHORIZE_IMPLEMENTATION` avec qualification loopback, sans accès
fournisseur. Seule une réponse propriétaire explicite contenant le bloc rempli autorisera le
diagnostic mutateur, l'ajout des tests et la correction :

```text
J9_WO021_OWNER_DECISION=<AUTHORIZE_IMPLEMENTATION|DO_NOT_AUTHORIZE>
J9_WO021_WORK_ORDER=WO-SS-20260831-021-j9-playwright-minimum-on-wire-delay
J9_WO021_SCOPE=DIAGNOSE_CORRECT_AND_MEASURE_MINIMUM_3_SECOND_PROVIDER_NETWORK_START_DELAY
J9_WO021_LOOPBACK_QUALIFICATION_AUTHORIZED=<YES|NO>
J9_PROVIDER_NETWORK_AUTHORIZED=NO
J9_WO019_PROVIDER_CAMPAIGN_RESUME_AUTHORIZED=NO
J9_WO019_EXISTING_DIRECT_ATTEMPTS_FROZEN=20
J9_NEW_PROVIDER_GO_GRANTED=NO
J9_ENDPOINT_TRANSPORT_PROTOCOL_SCHEMA_MIGRATION_SCOPE_EXPANSION_AUTHORIZED=NO
J9_INTEGRATION_OR_PRODUCTION_AUTHORIZED=NO
J9_FUTURE_WO019_RESUME_REQUIRES_NEW_OWNER_DECISION_AND_NEW_GLOBAL_GO=YES
```

État historique jusqu'à réception de cette décision :

```text
WORK_ORDER_STATUS=OPEN_AWAITING_OWNER_AUTHORIZATION
IMPLEMENTATION_STATUS=NOT_STARTED
MUTATING_DIAGNOSTIC_AUTHORIZED=NO
LOOPBACK_QUALIFICATION_AUTHORIZED=NO
PROVIDER_NETWORK_AUTHORIZED=NO
WO019_PROVIDER_CAMPAIGN_RESUME_AUTHORIZED=NO
```

### 4.1 Autorisation propriétaire reçue

Le propriétaire a autorisé l'implémentation et la qualification loopback le
`2026-08-31T08:43:48.8202621Z` avec le bloc suivant :

```text
J9_WO021_OWNER_DECISION=AUTHORIZE_IMPLEMENTATION
J9_WO021_WORK_ORDER=WO-SS-20260831-021-j9-playwright-minimum-on-wire-delay
J9_WO021_SCOPE=DIAGNOSE_CORRECT_AND_MEASURE_MINIMUM_3_SECOND_PROVIDER_NETWORK_START_DELAY
J9_WO021_LOOPBACK_QUALIFICATION_AUTHORIZED=YES
J9_PROVIDER_NETWORK_AUTHORIZED=NO
J9_WO019_PROVIDER_CAMPAIGN_RESUME_AUTHORIZED=NO
J9_WO019_EXISTING_DIRECT_ATTEMPTS_FROZEN=20
J9_NEW_PROVIDER_GO_GRANTED=NO
J9_ENDPOINT_TRANSPORT_PROTOCOL_SCHEMA_MIGRATION_SCOPE_EXPANSION_AUTHORIZED=NO
J9_INTEGRATION_OR_PRODUCTION_AUTHORIZED=NO
J9_FUTURE_WO019_RESUME_REQUIRES_NEW_OWNER_DECISION_AND_NEW_GLOBAL_GO=YES
```

L'état exécutoire devenu applicable immédiatement après l'autorisation, avant implémentation, était :

```text
WORK_ORDER_STATUS=IN_DEVELOPMENT
IMPLEMENTATION_STATUS=AUTHORIZED_NOT_YET_QUALIFIED
MUTATING_DIAGNOSTIC_AUTHORIZED=YES_WITHIN_WO021_SCOPE
LOOPBACK_QUALIFICATION_AUTHORIZED=YES
PROVIDER_NETWORK_AUTHORIZED=NO
WO019_PROVIDER_CAMPAIGN_RESUME_AUTHORIZED=NO
WO019_EXISTING_DIRECT_ATTEMPTS_FROZEN=20
NEW_PROVIDER_GO_GRANTED=NO
INTEGRATION_OR_PRODUCTION_AUTHORIZED=NO
```

Cette autorisation ne vaut pas validation de WO-021. Le statut maximal après correction et
qualification locale sera `READY_FOR_OWNER_REVIEW`, en attente d'un nouveau bloc propriétaire de
validation. Une éventuelle reprise de WO-019 restera en outre soumise au déclencheur de réexamen
d'ADR-SS-002 §9 applicable à toute reprise après incident ; aucun nouvel ADR n'est autorisé par
WO-021.

Ce statut maximal est désormais atteint : l'implémentation et les qualifications locales sont
vertes, mais le propriétaire n'a pas encore validé WO-021. Le Work Order reste donc dans
`docs/work_orders/active` et ne doit pas être déplacé vers les Work Orders terminés.

## 5. Contrat runtime à préserver

La correction devra respecter simultanément les invariants suivants :

```text
MINIMUM_OBSERVABLE_NETWORK_START_GAP=3s
MINIMUM_LOOPBACK_SERVER_ARRIVAL_GAP_NS=3000000000
COORDINATOR_MAXIMUM_CONCURRENCY=1
REQUEST_TIMEOUT=10s
AUTOMATIC_RETRY=NO
POLLING=NO
SCHEDULER=NO
FALLBACK=NO
CACHE_FORCING=NO
ENDPOINT_ALLOWLIST_CHANGE=NO
TRANSPORT_CHANGE=NO
PLAYWRIGHT_CONTEXT_REUSE=NO
PROVIDER_ACCESS_DURING_WO021=NO
SERVER_ADDRESS=127.0.0.1
RAW_PAYLOAD_IN_REPORT=NO
PROVIDER_URI_IN_REPORT=NO
COOKIE_TOKEN_SESSION_REUSE=NO
```

Le point de mesure candidat est l'événement CDP `Network.requestWillBeSent`, ou un événement
Playwright équivalent dont la proximité avec l'émission est démontrée. La qualification doit :

- ne retenir que la requête du document principal correspondant exactement à l'URI admise ;
- exclure popup, redirection inattendue, sous-ressource et URI différente ;
- utiliser une horloge monotone pour la temporisation et l'arrivée loopback ;
- réattendre réellement après tout réveil anticipé, sans avancer logiquement l'horloge ;
- utiliser le dernier départ effectivement observé pour la prochaine admission ;
- ne créer aucun timestamp fournisseur fictif lorsqu'aucun départ n'a lieu ;
- verrouiller la série si un transport commencé ne fournit pas de timestamp cohérent ;
- annuler la navigation suivante et nettoyer normalement lors d'un arrêt pendant l'attente.

Le délai d'attente précède le timeout réseau de dix secondes ; il n'en consomme pas artificiellement
le budget. Les lignes historiques antérieures à WO-021 conservent leur sémantique pré-navigation et
ne doivent pas être comparées automatiquement aux nouvelles mesures.

## 6. Périmètre

### Inclus après autorisation propriétaire

- test discriminant reproduisant l'insuffisance de la mesure actuelle ;
- `ManualProviderRequestCoordinator` et sa temporisation monotone ;
- worker Playwright et sémantique du timestamp existant si le discriminant confirme ce besoin ;
- superviseur, réponse et protocole uniquement dans une forme rétrocompatible ;
- tests unitaires, protocole, sécurité et qualifications loopback J3/J4/J5 ;
- documentation d'architecture, README, changelog et Work Orders liés.

### Hors périmètre

- tout appel réel vers SofaScore ;
- reprise de WO-019, nouveau manifeste ou nouveau go ;
- endpoint, allowlist, transport ou mécanisme furtif supplémentaire ;
- modification du minimum de trois secondes ou du timeout de dix secondes ;
- retry, polling, scheduler, fallback ou cache forcé ;
- migration SQL, nouveau champ persistant ou changement de protocole incompatible ;
- modification d'ADR-SS-001 ou d'ADR-SS-002 ;
- intégration au Betting Project, production ou topologie VPS.

Si la preuve exige une migration, un changement incompatible de protocole, une nouvelle borne ou
un élargissement d'endpoint/transport, le lot s'arrête avec :

```text
WORK_ORDER_STATUS=BLOCKED_REQUIRES_SCOPE_AMENDMENT
```

## 7. Lots d'exécution

```text
1. BASELINE_AND_DISCRIMINANT_RED
2. MEASUREMENT_BOUNDARY_ESTABLISHED
3. MINIMAL_CORRECTION_IMPLEMENTED
4. COORDINATOR_AND_WORKER_TESTS_PASS
5. LOOPBACK_J3_J4_J5_PASS
6. STANDARD_AND_INTEGRATION_VERIFY_PASS
7. PROCESS_LISTENER_ARTIFACT_AUDIT_PASS
8. READY_FOR_OWNER_REVIEW
```

Les deux portes requises ont été franchies le `2026-08-31T08:43:48.8202621Z`. Les lots runtime et
loopback peuvent commencer sans accès fournisseur et sans reprise de WO-019.

Les lots 1 à 7 ont ensuite été exécutés localement. Le lot 8 est atteint au statut
`READY_FOR_OWNER_REVIEW` ; il ne constitue ni une validation propriétaire ni une autorisation de
reprendre WO-019.

## 8. Matrice de tests obligatoire

| Cas | Résultat requis |
|---|---|
| Préparation variable avant navigation | Le test discriminant échoue avant correction parce que l'ancienne mesure ne garantit pas l'écart réseau. |
| Échéance à `2 999 ms` | Attente restante obligatoire. |
| Échéance à `3 000 ms` | Admission possible. |
| Réveil anticipé | Nouvelle attente réelle, sans avance logique du temps. |
| Trois appels J5 | Deux écarts d'arrivée loopback chacun `>= 3 000 000 000 ns`. |
| Deux workers ou campagnes successifs | Écart d'arrivée loopback `>= 3 000 000 000 ns`. |
| `404` puis continuation autorisée | Écart `>= 3 s`, sémantique J4/J5 inchangée. |
| Cache hit | Zéro arrivée réseau et zéro timestamp fournisseur fictif. |
| Échec avant navigation | Zéro départ et série verrouillée. |
| Timestamp absent après transport commencé | Aucun appel suivant. |
| Arrêt pendant l'attente | Zéro nouvelle requête et nettoyage complet. |
| Succès ou échec | Zéro processus possédé, listener ou artefact navigateur résiduel. |
| Hygiène | Zéro fournisseur, secret, payload brut, URI ou donnée de session dans les preuves. |

Le discriminant rouge doit simuler deux admissions espacées de trois secondes mais une préparation
pré-navigation du premier appel au moins 33 ms plus longue que celle du second. L'ancienne
instrumentation doit alors être démontrée insuffisante avant toute correction.

## 9. Vérifications obligatoires

```powershell
.\mvnw.cmd clean verify
.\mvnw.cmd -Pintegration-tests verify
powershell.exe -NoProfile -ExecutionPolicy Bypass `
  -File .\scripts\Verify-Local.ps1 -WithIntegrationTests
docker compose --env-file .env config
pwsh -NoProfile -File .\scripts\Invoke-J3PlaywrightLoopbackQualification.ps1
pwsh -NoProfile -File .\scripts\Invoke-J4PlaywrightLoopbackQualification.ps1
pwsh -NoProfile -File .\scripts\Invoke-J5PlaywrightLoopbackQualification.ps1
git diff --check
```

Résultat minimal :

```text
DISCRIMINANT_RED_BEFORE_FIX=PASS
COORDINATOR_UNIT_TESTS=PASS
WORKER_PROTOCOL_TESTS=PASS
WORKER_SECURITY_TESTS=PASS
SUPERVISOR_TESTS=PASS
J3_LOOPBACK_QUALIFICATION=PASS
J4_LOOPBACK_QUALIFICATION=PASS
J5_LOOPBACK_QUALIFICATION=PASS
MINIMUM_LOOPBACK_SERVER_ARRIVAL_GAP_NS>=3000000000
MINIMUM_PERSISTED_NETWORK_START_GAP_MS>=3000
STOP_DURING_DELAY_NEW_REQUEST_COUNT=0
PROVIDER_ACCESS_PERFORMED=NO
CLEAN_VERIFY=PASS
INTEGRATION_VERIFY=PASS
DOCKER_COMPOSE_CONFIG=PASS
RESIDUAL_OWNED_PROCESS_COUNT=0
LISTENER_127_0_0_1_8087_COUNT=0
FORBIDDEN_BROWSER_ARTIFACT_COUNT=0
```

### 9.1 Résultats observés

Le discriminant ciblé a d'abord été exécuté avant la correction. Il est devenu rouge en `0,08 s`,
comme attendu : l'ancienne implémentation n'appliquait pas le fence conservateur fondé sur la fin de
la réponse précédente et ne pouvait donc pas démontrer la marge résiduelle de `33 ms` du scénario
J5. La correction n'a été appliquée qu'après cette preuve rouge.

Le runtime corrigé :

- utilise une horloge monotone et réévalue toute attente anticipée dans
  `ManualProviderRequestCoordinator` ;
- place dans le superviseur parent un fence conservateur commun aux campagnes et workers : un
  nouveau dispatch IPC attend le minimum configuré après la fin observable du dispatch précédent ;
- marque la preuve temporelle comme perdue et bloque les dispatchs suivants lorsqu'aucune réponse
  exploitable ne permet d'armer ce fence ;
- traite toute interruption avant ou pendant l'attente comme une perte de preuve : le statut
  d'interruption est conservé, aucun `GET` n'est écrit et toutes les admissions suivantes restent
  bloquées ;
- observe dans le worker l'événement CDP `Network.requestWillBeSent` du seul document principal
  exact, le corrèle à sa réponse et rejette cache, service worker, redirection ou identité
  incohérente ;
- conserve le protocole IPC, les endpoints, le timeout réseau de dix secondes, la concurrence `1`,
  l'absence de retry et les bornes de fermeture WO-020.

La première qualification Chromium complète après le correctif principal a découvert un défaut
distinct sur le scénario timeout : `14` tests avaient été exécutés, avec un seul échec en `8,451 s`.
Le test attendait `TIMEOUT`, mais le parent recevait `PROTOCOL_ERROR`, puis la clôture produisait un
`RUNTIME_FAILURE` supprimé. La cause confirmée était l'ordre synchrone du teardown worker :
`Fetch.disable`, puis `Network.disable` et le détachement des sessions CDP étaient exécutés avant
`page.close()` sur une navigation encore bloquée. Cette séquence retardait la trame `TIMEOUT` au-delà
de la durée de vie du canal IPC. Le teardown ferme désormais d'abord la page, ce qui annule la
navigation, puis désactive et détache les sessions CDP. Le test timeout ciblé passe `1/1` en
`5,323 s`, puis la suite Chromium complète passe `14/14` en `101,5 s`. Cette correction ne modifie
ni `clearCookies()`, ni les bornes WO-020, ni la classification des autres incidents.

La requalification finale est :

```text
WORK_ORDER_STATUS=READY_FOR_OWNER_REVIEW
IMPLEMENTATION_STATUS=IMPLEMENTED_AND_LOCALLY_QUALIFIED
DISCRIMINANT_RED_BEFORE_FIX=PASS_OBSERVED_FAILURE_IN_0_08_SECONDS
DISCRIMINANT_RED_SCOPE=RESPONSE_COMPLETION_FENCE_AND_RESIDUAL_33_MS
TIMEOUT_TEARDOWN_INITIAL_RESULT=FAIL_EXPECTED_TIMEOUT_GOT_PROTOCOL_ERROR_WITH_SUPPRESSED_RUNTIME_FAILURE
TIMEOUT_TEARDOWN_ROOT_CAUSE=SYNCHRONOUS_CDP_DISABLE_AND_DETACH_BEFORE_PAGE_CLOSE_DELAYED_TIMEOUT_FRAME_BEYOND_IPC
TIMEOUT_TEARDOWN_CORRECTION=PAGE_CLOSE_CANCELS_NAVIGATION_BEFORE_CDP_DISABLE_AND_DETACH
TIMEOUT_TARGETED_REQUALIFICATION=PASS_1_OF_1_IN_5_323_SECONDS
TIMEOUT_FULL_CHROMIUM_REQUALIFICATION=PASS_14_OF_14_IN_101_5_SECONDS
VERIFY_LOCAL_WITH_INTEGRATION_BEFORE_FINAL_INTERRUPT_GUARD=PASS_941_STANDARD_67_INTEGRATION
FINAL_STANDARD_VERIFY=PASS_943_TESTS_0_FAILURE_0_ERROR_4_SKIPPED
FINAL_STANDARD_VERIFY_FINISHED_AT_UTC=2026-08-31T09:48:10Z
INTEGRATION_TESTS=PASS_67_TESTS_0_FAILURE_0_ERROR_0_SKIPPED
SUPERVISOR_TARGETED_TESTS_AFTER_INTERRUPT_GUARD=PASS_40_OF_40
MANUAL_COORDINATOR_TARGETED_TESTS=PASS_8_OF_8
INTERRUPTION_DURING_DELAY=PASS_TIMING_EVIDENCE_LOST_NO_GET
LOOPBACK_J3=PASS_21_WORKER_TESTS_PLUS_14_CHROMIUM_IT
LOOPBACK_J3_FINAL_RERUN_FINISHED_AT_UTC=2026-08-31T09:50:33Z
LOOPBACK_J4=PASS_21_WORKER_TESTS_PLUS_14_CHROMIUM_IT
LOOPBACK_J4_FINAL_RERUN_FINISHED_AT_UTC=2026-08-31T09:52:50Z
LOOPBACK_J5=PASS_21_WORKER_TESTS_PLUS_14_CHROMIUM_IT
LOOPBACK_J5_FINAL_RERUN_FINISHED_AT_UTC=2026-08-31T09:55:06Z
WORKER_TEST_BREAKDOWN=10_PROTOCOL_PLUS_1_SECURITY_PLUS_10_NETWORK_OBSERVATION
J5_REQUESTED_AT_GAPS=PASS_GE_3000_MS
J5_LOOPBACK_ARRIVAL_GAPS=PASS_GE_3000000000_NS
CROSS_WORKER_REQUESTED_AT_GAP=PASS_GE_3000_MS
CROSS_WORKER_LOOPBACK_ARRIVAL_GAP=PASS_GE_3000000000_NS
STOP_DURING_DELAY_NEW_REQUEST_COUNT=0
MAVEN_REPORT_SENSITIVE_SCANNER=PASS
RUNTIME_FILE_CANARY_SCAN=PASS
PROVIDER_ACCESS_PERFORMED=NO
WO019_STATUS=STOPPED
WO019_EXISTING_DIRECT_ATTEMPTS_FROZEN=20
WO019_PROVIDER_CAMPAIGN_RESUME_AUTHORIZED=NO
NETWORK_AUTHORIZED=NO
NEW_PROVIDER_GO_GRANTED=NO
INTEGRATION_OR_PRODUCTION_AUTHORIZED=NO
FINAL_LOCAL_QUALIFICATION_STATE=PASS_AFTER_INTERRUPT_GUARD
```

Les trois scripts J3, J4 et J5 ont chacun vérifié les `21` tests worker — `10` protocole, `1`
sécurité, `10` observation réseau — puis les `14` tests d'intégration Chromium. J5 vérifie en plus
les écarts `requestedAt`, les arrivées monotones du serveur loopback et les écarts entre workers,
tous au moins égaux à trois secondes, ainsi que zéro nouvelle requête lorsque l'arrêt intervient
pendant le fence. Les suites ciblées postérieures confirment également `40/40` tests superviseur et
`8/8` tests coordinateur, dont l'interruption fail-closed sans émission de `GET`. Aucun script n'a
contacté le fournisseur. Les rejeux finaux postérieurs au garde d'interruption se sont terminés à
`2026-08-31T09:50:33Z` pour J3, `2026-08-31T09:52:50Z` pour J4 et
`2026-08-31T09:55:06Z` pour J5. Le dernier rejeu J5 reconfirme toutes les lignes d'écart
`requestedAt`, loopback et inter-workers `>= 3 s`, `STOP_DURING_DELAY_NEW_REQUEST_COUNT=0` et
`PROVIDER_ACCESS_PERFORMED=NO`.

## 10. Fichiers du lot

```text
src/main/java/.../ManualProviderRequestCoordinator.java
src/main/java/.../playwright/ChildJvmPlaywrightProviderSupervisor.java
src/main/java/.../playwright/ProviderNetworkStartDelayGate.java
src/provider-playwright/java/.../ProviderPlaywrightWorkerMain.java
src/provider-playwright/java/.../ProviderMainDocumentNetworkObservation.java
src/test/java/.../ManualProviderRequestCoordinatorTest.java
src/test/java/.../ChildJvmPlaywrightProviderSupervisorTest.java
src/provider-playwright-test/java/.../ProviderPlaywrightWorkerProtocolTest.java
src/provider-playwright-test/java/.../ProviderPlaywrightWorkerSecurityContractTest.java
src/provider-playwright-test/java/.../ProviderPlaywrightWorkerNetworkObservationTest.java
src/provider-playwright-qualification-test/java/.../ProviderPlaywrightLocalQualificationIT.java
scripts/Invoke-J3PlaywrightLoopbackQualification.ps1
scripts/Invoke-J4PlaywrightLoopbackQualification.ps1
scripts/Invoke-J5PlaywrightLoopbackQualification.ps1
docs/architecture/J3-PLAYWRIGHT-PROVIDER-TRANSPORT.md
README.md
CHANGELOG.md
docs/work_orders/active/WO-SS-20260831-018-decision-j9.md
docs/work_orders/active/WO-SS-20260831-019-j9-provider-robustness.md
docs/work_orders/active/WO-SS-20260831-021-j9-playwright-minimum-on-wire-delay.md
```

Cette liste reflète le lot réalisé. Les autres tests de services J3/J4/J5 ont été adaptés uniquement
pour préserver les contrats de coordination et de temporisation monotone. Aucun endpoint, ADR,
migration, schéma, route ou configuration réseau n'a été ajouté.

## 11. Portes de statut

```text
OPEN_AWAITING_OWNER_AUTHORIZATION
  -> IN_DEVELOPMENT
  -> DISCRIMINANT_RED
  -> MEASUREMENT_BOUNDARY_ESTABLISHED
  -> CORRECTION_IMPLEMENTED
  -> LOCAL_READINESS_PASS
  -> READY_FOR_OWNER_REVIEW
  -> VALIDATED
```

Alternatives :

```text
OWNER_AUTHORIZATION_REFUSED
BLOCKED_REQUIRES_SCOPE_AMENDMENT
FAILED_SAFETY_REGRESSION
CANCELLED
```

Même `VALIDATED` ne réouvre pas WO-019. Une reprise future nécessitera une nouvelle décision
propriétaire, une readiness fraîche, un nouveau manifeste, un nouveau go à usage unique et une
nouvelle fenêtre. Puisque WO-019 a été arrêté après incident, ADR-SS-002 doit également être
réexaminé explicitement avant une telle reprise. Les 20 tentatives de la campagne arrêtée restent
gelées dans leur rapport.

## 12. Critères d'acceptation de l'ouverture

La validation du seul lot documentaire d'ouverture est :

```text
OPENING_STANDARD_VERIFY=PASS_931_TESTS_0_FAILURE_0_ERROR_4_SKIPPED
OPENING_STANDARD_VERIFY_FINISHED_AT_UTC=2026-08-31T08:22:16Z
OPENING_DIFF_CHECK=PASS
OPENING_LOCAL_MARKDOWN_LINKS=PASS
OPENING_CREDENTIAL_SCAN=PASS
OPENING_LOOPBACK_BINDING=127.0.0.1
OPENING_NETWORK_FLAGS_DEFAULT_FALSE=PASS
OPENING_MINIMUM_DELAY_CONFIGURATION=3s
OPENING_REQUEST_TIMEOUT_CONFIGURATION=10s
OPENING_LISTENER_8087_COUNT=0
OPENING_FORBIDDEN_RUNTIME_BROWSER_ARTIFACT_COUNT=0
OPENING_PROVIDER_ACCESS_PERFORMED=NO
```

- numéro WO-021 disponible et fichier créé dans `docs/work_orders/active` ;
- branche et worktree dédiés depuis le commit STOPPED de WO-019 ;
- périmètre, lacune, test discriminant et invariants documentés ;
- bloc propriétaire explicite soumis sans préremplir une autorisation ;
- aucune modification runtime, migration, ADR, endpoint ou configuration réseau ;
- aucun appel fournisseur, reprise de WO-019, push ou fusion vers `main` ;
- `git diff --check`, liens locaux et recherche de secrets verts ;
- au moment de l'ouverture, implémentation conservée à `NOT_AUTHORIZED` ; cette photographie
  historique est remplacée pour l'état courant par la section 13.

## 13. Porte propriétaire de clôture

La qualification locale autorise uniquement la revue propriétaire de WO-021 :

```text
WORK_ORDER_STATUS=READY_FOR_OWNER_REVIEW
OWNER_VALIDATION=PENDING
MOVE_TO_COMPLETED_AUTHORIZED=NO
WO019_STATUS=STOPPED
WO019_PROVIDER_CAMPAIGN_RESUME_AUTHORIZED=NO
WO019_EXISTING_DIRECT_ATTEMPTS_FROZEN=20
PROVIDER_NETWORK_AUTHORIZED=NO
NEW_PROVIDER_GO_GRANTED=NO
ADR_SS_002_REEXAMINATION_REQUIRED_BEFORE_ANY_FUTURE_RESUME=YES
INTEGRATION_OR_PRODUCTION_AUTHORIZED=NO
```

Une validation propriétaire explicite reste nécessaire pour déplacer WO-021 vers
`docs/work_orders/completed`. Même cette future validation ne reprendra pas WO-019 et ne vaudra pas
réexamen ni acceptation d'ADR-SS-002.

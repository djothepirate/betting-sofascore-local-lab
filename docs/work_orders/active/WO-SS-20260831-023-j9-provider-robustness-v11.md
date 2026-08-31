# WO-SS-20260831-023 — Campagne autonome de robustesse fournisseur J9 sous ADR-SS-002 v1.1

- **Statut :** `BLOCKED_AFTER_BACKUP_CLEANUP_UNCONFIRMED`
- **Date d'ouverture :** 2026-08-31
- **Décision propriétaire observée à :** 2026-08-31T13:08:48.0887445Z
- **Jalon :** J9 — nouvelle preuve fournisseur autonome
- **Base immuable d'ouverture :** `1be8a26a0fb4ff54e9514ca69655d5aa825d2f26`
- **Branche :** `codex/j9-provider-robustness-v11`
- **Worktree :** `.tmp/j9-provider-robustness-v11`
- **Work Order parent :** `WO-SS-20260831-018-decision-j9`
- **Campagne historique :** WO-019 `STOPPED`, vingt tentatives gelées, non rouverte
- **ADR applicables :** ADR-SS-001 v1.4 ; ADR-SS-002 v1.1 `ACCEPTED`
- **Profil :** `RESTART_FULL_D1_D2_D3`
- **Usage de la nouvelle série :** `ONE_TIME`
- **Acteur sélectionné :** `CODEX_LOCAL_UI`
- **Exécution fournisseur immédiate :** `NOT_AUTHORIZED_PENDING_ALL_GATES_AND_MANIFEST_BOUND_GO`
- **Plafond de la nouvelle série :** 38 tentatives directes
- **Plafond cumulatif d'audit J9 :** 58 tentatives directes
- **Réseau fournisseur :** `NOT_AUTHORIZED`
- **Go global fournisseur :** `NOT_GRANTED`
- **Intégration, production ou VPS courant :** `NOT_AUTHORIZED`
- **Option VPS future :** `NOT_EXCLUDED_BUT_NOT_AUTHORIZED`

## 1. Décision d'ouverture et limite du go reçu

Après l'acceptation d'ADR-SS-002 v1.1, le propriétaire demande de lancer la nouvelle campagne à
usage unique. Cette instruction lève la porte d'ouverture et de préparation du nouveau Work Order,
de sa branche et de son worktree. Comme `CODEX_LOCAL_UI` est l'unique modèle d'acteur déjà
sélectionné, elle autorise aussi cet acteur dans le périmètre de WO-023, sous condition de toutes les
portes restantes.

Le message ne contient pas encore le manifeste gelé, son commit/hash ni une fenêtre UTC explicite.
Conformément à ADR-SS-002 v1.1 §11.4-11.5, il ne peut donc pas être traité comme le go global
fournisseur consommable et ne devient pas une autorisation réseau latente.

```text
OWNER_NEW_CAMPAIGN_LAUNCH_DECISION=AUTHORIZE_OPENING_AND_OFFLINE_PREPARATION
OWNER_DECISION_RECORDED_AT_UTC=2026-08-31T13:08:48.0887445Z
NEW_CAMPAIGN_WORK_ORDER_OPENING_AUTHORIZED=YES
NEW_CAMPAIGN_BRANCH_CREATION_AUTHORIZED=YES
NEW_CAMPAIGN_WORKTREE_CREATION_AUTHORIZED=YES
NEW_CAMPAIGN_PREPARATION_AUTHORIZED=YES
NEW_CAMPAIGN_SERIES_USE=ONE_TIME

SELECTED_EXECUTION_ACTOR=CODEX_LOCAL_UI
CODEX_LOCAL_UI_ACTOR_AUTHORIZED_FOR_WO023=YES
CODEX_LOCAL_UI_PROVIDER_ACTIONS_ALLOWED_NOW=NO
CODEX_LOCAL_UI_EXECUTION_CONDITION=ALL_OFFLINE_GATES_SATISFIED_AND_MANIFEST_BOUND_GLOBAL_OWNER_GO_VALID
AUTOMATED_UI_ORCHESTRATION_AUTHORIZED=NO

WORK_ORDER_STATUS=OPEN_AWAITING_OFFLINE_READINESS
CAMPAIGN_EXECUTION_AUTHORIZED=NO
PROVIDER_NETWORK_AUTHORIZED=NO
GLOBAL_OWNER_GO=NOT_GRANTED
OWNER_GO_CONSUMED=NO
PRIMARY_DATABASE_PURGE=NO
INTEGRATION_OR_PRODUCTION_AUTHORIZED=NO
J9_FINAL_DECISION=NOT_TAKEN
```

Le go historique v1.0 reste consommé, expiré et terminé par l'arrêt de WO-019 :

```text
WO019_STATUS=STOPPED
WO019_HISTORICAL_CAMPAIGN_REOPENED=NO
WO019_HISTORICAL_DIRECT_ATTEMPTS_FROZEN=20
PRIOR_GLOBAL_OWNER_GO=CONSUMED_AND_TERMINATED_BY_STOP
PRIOR_GLOBAL_OWNER_GO_REUSABLE=NO
HISTORICAL_REPORT=docs/validation/J9-PROVIDER-ROBUSTNESS-CAMPAIGN-20260831.md
HISTORICAL_REPORT_BYTES=9708
HISTORICAL_REPORT_SHA256=47e6171eeb1fc44cf995c727d4f09107875c844e71e8b183068731cbb4c62b9d
```

## 2. Objectif et résultat attendu

Produire une nouvelle preuve autonome, homogène et bornée sur D1, D2 et D3, sans modifier ni
compléter le rapport historique WO-019. Le rapport de WO-023 sera distinct et classé selon la règle
déterministe d'ADR-SS-002 v1.1 :

- `PASS` si la série se termine sous 38 tentatives, sans incident bloquant, cache hit ou 404 limitant
  une dimension attendue, avec réponses compatibles, persistées et auditables ;
- `PARTIAL_BOUNDED` uniquement pour des indisponibilités natives ou des lacunes explicitement
  bornées, sans incident de sécurité ni contournement ;
- `STOPPED` au déclenchement de toute condition d'arrêt ou violation d'un prérequis.

La campagne ne constitue ni une autorisation d'intégration, ni une qualification de production ou
de VPS, ni un droit permanent d'interroger le fournisseur.

## 3. Corpus immuable et ordre fermé

| Dossier | Provider ID | Identifiant canonique | Rôle |
|---|---:|---|---|
| D1 — Cittadella / Atalanta U23 | `16691018` | `f4713f80-4769-3656-ba51-61d8ac1aa814` | Ancrage J3 daté `2026-08-15`, tournoi `824`, saison `99790`, phase `15118`, puis J4/J5 |
| D2 — Barracas Central / Rosario Central | `16671566` | `da075869-34d4-3d42-83d2-613583691845` | Cas historique complet J4/J5 avec incident off-ball |
| D3 — Lille / PSG | `16310930` | `c40066c9-987b-38d9-b415-869a453d2ad6` | Cas récent avec les trois familles J5 disponibles |

L'ordre fermé comprend huit sous-campagnes unitaires et fraîches :

| Segment | Parcours | Maximum nouveau | Cumul série | Cumul audit J9 |
|---|---|---:|---:|---:|
| A1 | J3 `SCHEDULED_EVENTS`, pages contiguës `1..25` | 25 | 25 | 45 |
| A2 | J3 `TOURNAMENT_SCHEDULED_EVENTS` pour D1 | 1 | 26 | 46 |
| A3 | J4 phase 2 pour D1 | 1 | 27 | 47 |
| A4 | J5 statistiques, incidents et compositions pour D1 | 3 | 30 | 50 |
| B1 | J4 phase 2 pour D2 | 1 | 31 | 51 |
| B2 | J5 statistiques, incidents et compositions pour D2 | 3 | 34 | 54 |
| B3 | J4 phase 2 pour D3 | 1 | 35 | 55 |
| B4 | J5 statistiques, incidents et compositions pour D3 | 3 | 38 | 58 |

```text
NEW_SERIES_LEDGER_BASELINE_DIRECT_ATTEMPTS=0
NEW_SERIES_MAXIMUM_DIRECT_ATTEMPTS=38
AUDIT_CUMULATIVE_BASELINE_DIRECT_ATTEMPTS=20
AUDIT_MAXIMUM_CUMULATIVE_DIRECT_ATTEMPTS=58
D1_REEXECUTION_REQUIRED=YES
REPLACEMENT_DOSSIER_ALLOWED=NO
THIRD_SERIES_AUTHORIZED=NO
```

Chaque sous-campagne contrôlera les deux ledgers avant son claim. Les unités non consommées ne
constituent aucune réserve de retry, de rejeu, de remplacement ou de troisième série. Un cache hit
vaut zéro appel direct et borne le résultat autonome pour la dimension concernée.

## 4. Portes cumulatives avant tout appel fournisseur

Les portes sont strictement séquentielles :

1. Work Order, branche et worktree dédiés ouverts sur le commit d'ADR v1.1 accepté ;
2. readiness fraîche sur le commit exact de préparation ;
3. revue officielle encore fraîche à la date du futur go ;
4. sauvegarde chiffrée V28 post-arrêt et restauration isolée qualifiée ;
5. manifeste final gelé et corroboration indépendante des ledgers ;
6. go propriétaire global explicite, lié au manifeste, borné à une fenêtre UTC d'au plus 60 minutes
   et à usage unique.

Tant qu'une seule porte reste insatisfaite :

```text
CODEX_LOCAL_UI_PROVIDER_ACTIONS_ALLOWED_NOW=NO
CAMPAIGN_EXECUTION_AUTHORIZED=NO
PROVIDER_NETWORK_AUTHORIZED=NO
GLOBAL_OWNER_GO=NOT_GRANTED
OWNER_GO_CONSUMED=NO
```

## 5. Readiness hors ligne obligatoire

La readiness sera exécutée sur le commit exact de WO-023, sans accès fournisseur :

```text
.\mvnw.cmd clean verify
.\mvnw.cmd -Pintegration-tests verify
pwsh -NoProfile -File .\scripts\Verify-Local.ps1 -WithIntegrationTests
docker compose --env-file .env config --quiet
pwsh -NoProfile -File .\scripts\Invoke-J3PlaywrightLoopbackQualification.ps1
pwsh -NoProfile -File .\scripts\Invoke-J4PlaywrightLoopbackQualification.ps1
pwsh -NoProfile -File .\scripts\Invoke-J5PlaywrightLoopbackQualification.ps1
```

La qualification inclura également les tests des fences inter-worker/inter-campagne, du refus des
39e/59e tentatives, de la consommation unique du go, de l'arrêt global, du délai réseau minimal de
3 secondes, du nettoyage après succès/échec et de l'absence d'artefacts navigateur, secrets ou
payloads bruts dans Git et les journaux documentaires.

Critères de readiness :

```text
STANDARD_TESTS=PASS
INTEGRATION_TESTS=PASS
VERIFY_LOCAL=PASS
COMPOSE_CONFIG=PASS
LOOPBACK_J3=PASS
LOOPBACK_J4=PASS
LOOPBACK_J5=PASS
PROVIDER_ACCESS_PERFORMED=NO
LISTENER_127_0_0_1_8087_AFTER_READINESS=FREE
OWNED_PLAYWRIGHT_PROCESS_COUNT_AFTER_READINESS=0
FORBIDDEN_BROWSER_ARTIFACT_COUNT=0
```

## 6. Sauvegarde/restauration V28 post-arrêt

Après la readiness et avant le manifeste, une nouvelle archive chiffrée sera créée hors Git et
restaurée sur une cible isolée. Aucune phrase secrète, clé privée, archive, payload ou chemin
sensible ne sera versionné.

La qualification doit établir au minimum :

```text
FLYWAY_SCHEMA_VERSION=28
MINIMUM_SNAPSHOT_ID_COVERAGE=814
MINIMUM_OCCURRENCE_ID_COVERAGE=781
HISTORICAL_DIRECT_ATTEMPTS_COVERED=20
RESTORE_QUALIFIED=YES
RAW_INTEGRITY_FAILURE_COUNT=0
SOURCE_RESTORE_MISMATCH_COUNT=0
TEMPORARY_RESTORE_DATABASE_REMAINING=NO
PRIMARY_DATABASE_PURGE=NO
PROVIDER_ACCESS_PERFORMED=NO
```

Un échec, une couverture insuffisante ou un résidu de restauration bloque la campagne et exige une
nouvelle décision après correction ; aucune purge de la base primaire n'est permise.

## 7. Manifeste final et futur go global

Après satisfaction des portes hors ligne, un manifeste versionné figera le commit exécutable, les
résultats de readiness et de sauvegarde/restauration, le corpus, l'ordre A1..B4, les deux ledgers,
l'acteur et les invariants. Son SHA-256 sera calculé avant toute demande de go.

Le bloc propriétaire global devra alors identifier explicitement ce manifeste et une fenêtre UTC
non rétroactive d'au plus 60 minutes :

```text
J9_WO023_GLOBAL_OWNER_GO_DECISION=<GRANT|DENY>
WORK_ORDER=WO-SS-20260831-023-j9-provider-robustness-v11
WORK_ORDER_COMMIT=<commit>
MANIFEST_REFERENCE=<path>
MANIFEST_SHA256=<sha256>
ADR=ADR-SS-002_v1.1
ADR_STATUS=ACCEPTED
HISTORICAL_REPORT=docs/validation/J9-PROVIDER-ROBUSTNESS-CAMPAIGN-20260831.md
HISTORICAL_REPORT_SHA256=47e6171eeb1fc44cf995c727d4f09107875c844e71e8b183068731cbb4c62b9d
TARGET_PROVIDER_EVENT_IDS=16691018,16671566,16310930
TARGET_CANONICAL_EVENT_IDS=f4713f80-4769-3656-ba51-61d8ac1aa814,da075869-34d4-3d42-83d2-613583691845,c40066c9-987b-38d9-b415-869a453d2ad6
HISTORICAL_DIRECT_ATTEMPTS_FROZEN=20
NEW_SERIES_MAXIMUM_DIRECT_ATTEMPTS=38
AUDIT_MAXIMUM_CUMULATIVE_DIRECT_ATTEMPTS=58
EXECUTION_ACTOR=CODEX_LOCAL_UI
WINDOW_UTC=[<FROM>,<TO>)
MAXIMUM_WINDOW_DURATION=60m
GO_USE=ONE_TIME
REPLACEMENT_DOSSIER_ALLOWED=NO
PRIMARY_DATABASE_PURGE=NO
INTEGRATION_OR_PRODUCTION_AUTHORIZED=NO
```

Le go sera consommé irréversiblement au premier claim J3 accepté. Une deuxième utilisation, un
redémarrage, une deuxième instance, une condition d'arrêt, la fin de la fenêtre ou la fin de D3 le
terminera.

## 8. Exécution UI unitaire et conditions d'arrêt

L'acteur utilisera exclusivement l'interface locale sur `127.0.0.1:8087`. Chaque segment A1..B4
conserve une préparation fraîche, sa phrase exacte à usage unique, son acquittement/claim et un
nouveau contexte Playwright non persistant. La concurrence reste `1`, le délai minimal `3 s`, le
timeout `10 s`, sans retry, fallback, polling, scheduler, cache forcé, script de soumission ou
orchestration UI.

Une réponse `404` native conserve les sémantiques existantes et permet la poursuite ordonnée avec
un résultat global au maximum `PARTIAL_BOUNDED`. Toute autre condition suivante arrête la série :

- `401`, `403`, `429`, `5xx`, timeout ou redirection inattendue ;
- HTML/challenge, incompatibilité de schéma ou dépassement de 5 Mio ;
- fuite de donnée sensible, échec de persistance, lease, fence ou cleanup ;
- absence de délai réseau conforme, dépassement potentiel d'un ledger ou go invalide/expiré ;
- conservation de HAR, trace, vidéo, capture, téléchargement ou `storageState`.

Aucun dossier de remplacement, retry, relance automatique ou poursuite après arrêt n'est permis.

## 9. Preuve, nettoyage et clôture

Le rapport autonome sera créé sous `docs/validation` sans modifier le rapport WO-019. Il consignera
les tentatives par segment/dossier/famille, classifications HTTP, parsings, persistences,
complétude, indisponibilités natives, durées, hashes, ledgers, cleanup et reproductibilité des
exports, sans payload brut, cookie, jeton, certificat, phrase de confirmation ou donnée de session.

Après succès comme après arrêt :

1. remettre tous les flags réseau à l'état bloqué ;
2. vérifier l'absence de listener, worker, navigateur ou processus possédé résiduel ;
3. vérifier l'absence d'artefact navigateur interdit et de secret dans le diff ;
4. réexécuter les tests requis et produire le commit local de preuve ;
5. consolider la matrice de WO-018 sans fusion vers `main` et sans push.

## 10. État à l'ouverture

```text
WORK_ORDER_STATUS=OPEN_AWAITING_OFFLINE_READINESS
EVIDENCE_STATUS=DRAFT
OFFLINE_READINESS=NOT_EXECUTED
POST_STOP_V28_BACKUP_RESTORE=NOT_EXECUTED
MANIFEST_STATUS=NOT_CREATED
GLOBAL_OWNER_GO=NOT_GRANTED
OWNER_GO_CONSUMED=NO
NEW_SERIES_DIRECT_ATTEMPTS=0
AUDIT_CUMULATIVE_DIRECT_ATTEMPTS=20
CODEX_LOCAL_UI_ACTOR_AUTHORIZED_FOR_WO023=YES
CODEX_LOCAL_UI_PROVIDER_ACTIONS_ALLOWED_NOW=NO
CAMPAIGN_EXECUTION_AUTHORIZED=NO
PROVIDER_NETWORK_AUTHORIZED=NO
PRIMARY_DATABASE_PURGE=NO
INTEGRATION_OR_PRODUCTION_AUTHORIZED=NO
J9_FINAL_DECISION=NOT_TAKEN
```

## 11. Validation de WO-024 et readiness fraîche post-correctif

Le paragraphe 10 reste la photographie immuable de l'ouverture. Le propriétaire a ensuite validé
WO-024 et autorisé une nouvelle tentative de sauvegarde/restauration WO-023 avec la phrase secrète
auto-générée par `age`. Il a également exprimé l'intention d'enchaîner sur la campagne fournisseur,
mais son bloc explicite maintient le réseau, la reprise et le nouveau go à `NO`. L'intention de
séquence ne vaut donc pas go fournisseur.

```text
J9_WO024_OWNER_REVIEW_DECISION=VALIDATE
J9_WO024_WORK_ORDER=WO-SS-20260831-024-j9-backup-pipeline-fail-closed-cleanup
J9_WO024_LOCAL_READINESS_ACKNOWLEDGED=YES
J9_WO024_SCOPE_CONFIRMED=DIAGNOSE_CORRECT_AND_QUALIFY_J6_BACKUP_NATIVE_PIPELINE_CANCELLATION_TIMEOUT_AND_PROCESS_TREE_CLEANUP
J9_WO024_FAIL_CLOSED_PIPELINE_QUALIFIED=YES
J9_WO024_WORK_ORDER_MOVE_TO_COMPLETED=YES
J9_WO023_BACKUP_RETRY=AUTHORIZED_AFTER_VALIDATION_WITH_AGE_AUTOGENERATED_PASSPHRASE
J9_PROVIDER_NETWORK_AUTHORIZED=NO
J9_WO023_PROVIDER_CAMPAIGN_RESUME_AUTHORIZED=NO
J9_NEW_PROVIDER_GO_GRANTED=NO
J9_INTEGRATION_OR_PRODUCTION_AUTHORIZED=NO
```

WO-024 a été clôturé au commit `8b91bf86c624ebff0ddf5c4c9bf454e83142b578`, puis intégré à
WO-023 par fast-forward, sans commit de fusion. La readiness fraîche a été exécutée sur ce commit,
hors ligne pour Maven et exclusivement sur `127.0.0.1` pour Playwright. Le cache Chromium complet
déjà qualifié du worktree historique WO-019 a été fourni explicitement aux trois scripts ; aucun
téléchargement de navigateur n'a été déclenché.

Les deux premiers lancements J3 depuis le profil de sandbox Codex se sont arrêtés avant les tests :
le profil remappait le dépôt Maven vers un cache vide et refusait l'accès à Central. Ils n'ont lancé
ni worker, ni navigateur, ni appel fournisseur. La même commande, exécutée dans le contexte hôte
avec Maven forcé hors ligne, a ensuite réussi ; J4 et J5 ont suivi séquentiellement dans ce même
contexte.

Le rapport détaillé et les hashes des rapports locaux conservés au moment du constat sont dans
`docs/validation/J9-PROVIDER-ROBUSTNESS-V11-READINESS-20260831.md`.

```text
WORK_ORDER_STATUS=READY_FOR_V28_BACKUP_RESTORE
EVIDENCE_STATUS=DRAFT

WO024_STATUS=VALIDATED
WO024_VALIDATED_COMMIT=8b91bf86c624ebff0ddf5c4c9bf454e83142b578
WO024_OWNER_VALIDATED_AT_UTC=2026-08-31T18:37:19Z
WO024_WORK_ORDER_LOCATION=docs/work_orders/completed/WO-SS-20260831-024-j9-backup-pipeline-fail-closed-cleanup.md
WO024_FAIL_CLOSED_PIPELINE_QUALIFIED=YES

OFFLINE_READINESS=PASS_AFTER_VALIDATED_WO024
OFFLINE_READINESS_CODE_COMMIT=8b91bf86c624ebff0ddf5c4c9bf454e83142b578
STANDARD_VERIFY=PASS_945_TESTS_0_FAILURES_0_ERRORS_4_SKIPPED
INTEGRATION_VERIFY=PASS_67_TESTS_0_FAILURES_0_ERRORS_0_SKIPPED
FLYWAY_SCHEMA=V28
VERIFY_LOCAL=PASS_WITH_INTEGRATION_TESTS_PROVIDER_NETWORK_NO
COMPOSE_CONFIG=PASS
LOOPBACK_J3=PASS_21_WORKER_PLUS_14_CHROMIUM
LOOPBACK_J4=PASS_21_WORKER_PLUS_14_CHROMIUM
LOOPBACK_J5=PASS_21_WORKER_PLUS_14_CHROMIUM
SUPERVISOR_TESTS=PASS_40
COORDINATOR_TESTS=PASS_8
MINIMUM_NETWORK_START_GAPS=PASS_GE_3_SECONDS
CROSS_WORKER_NETWORK_START_GAPS=PASS_GE_3_SECONDS
CROSS_CAMPAIGN_NETWORK_START_GAPS=PASS_GE_3_SECONDS
STOP_DURING_DELAY_NEW_REQUEST_COUNT=0
NEW_SERIES_LEDGER_38_PLUS_1=REJECTED
NEW_SERIES_LEDGER_36_PLUS_3=REJECTED
AUDIT_CUMULATIVE_LEDGER_58_PLUS_1=REJECTED
AUDIT_CUMULATIVE_LEDGER_56_PLUS_3=REJECTED
OLD_GLOBAL_OWNER_GO_REUSE=REJECTED
PROVIDER_ACCESS_PERFORMED=NO
LISTENER_127_0_0_1_8087_AFTER_READINESS=FREE
OWNED_PLAYWRIGHT_PROCESS_COUNT_AFTER_READINESS=0
FORBIDDEN_BROWSER_ARTIFACT_COUNT=0
SECRET_AND_RAW_PAYLOAD_SCAN=PASS
NETWORK_FLAGS_DEFAULT_FALSE=PASS

WO023_BACKUP_RETRY_AUTHORIZED_NOW=YES_WITH_AGE_AUTOGENERATED_PASSPHRASE
POST_STOP_V28_BACKUP_RESTORE=AUTHORIZED_NOT_EXECUTED
NEXT_GATE=POST_STOP_V28_BACKUP_RESTORE
MANIFEST_STATUS=NOT_CREATED

GLOBAL_OWNER_GO=NOT_GRANTED
OWNER_GO_CONSUMED=NO
NEW_SERIES_DIRECT_ATTEMPTS=0
AUDIT_CUMULATIVE_DIRECT_ATTEMPTS=20
CODEX_LOCAL_UI_PROVIDER_ACTIONS_ALLOWED_NOW=NO
CAMPAIGN_EXECUTION_AUTHORIZED=NO
WO023_PROVIDER_CAMPAIGN_RESUME_AUTHORIZED=NO
PROVIDER_NETWORK_AUTHORIZED=NO
NEW_GLOBAL_OWNER_GO_GRANTED=NO
PRIMARY_DATABASE_PURGE=NO
INTEGRATION_OR_PRODUCTION_AUTHORIZED=NO
J9_FINAL_DECISION=NOT_TAKEN

OWNER_SEQUENCE_INTENT=BACKUP_RESTORE_THEN_PROVIDER_CAMPAIGN
PROVIDER_CAMPAIGN_AUTHORITY_IN_CURRENT_BLOCK=NO
```

## 12. Incident fail-closed de la porte sauvegarde/restauration

La photographie du paragraphe 11 reste la preuve de readiness antérieure à l'essai. La tentative
unique ensuite autorisée a utilisé la phrase secrète auto-générée par `age`. Le chiffrement s'est
terminé avec producteur et consommateur à `EXIT_0`, copie à EOF et nettoyage local déclaré `PASS`,
puis l'exécution s'est arrêtée à la confirmation de nettoyage de la session PostgreSQL exactement
possédée.

Le point d'arrêt précède la publication de l'archive finale, la création du manifeste et toute
restauration. L'audit post-incident trouve trois fois zéro session exacte et zéro session J6
possédée ; il trouve également zéro processus exact, fichier final ou partiel, base temporaire et
listener 8087. Ce confinement postérieur ne qualifie pas rétroactivement la tentative.

Le diagnostic établit un écart de qualification : le runtime utilise par défaut une fenêtre de
nettoyage de `5 000 ms`, alors que les quatre parcours Docker de qualification WO-024 forçaient
`10 000 ms`. Le message final générique ne préserve pas la cause interne ; le déclencheur exact
reste donc indéterminé. Le rapport distinct
[Le rapport d'incident distinct](../../validation/J9-WO023-POST-BACKUP-CLEANUP-INCIDENT-20260831.md)
consigne les faits, hashes et bornes sans exposer le nom exact de session ni le chemin de la preuve
opérateur.

La validation standard post-incident a ensuite produit `945` tests, un échec, zéro erreur et quatre
skips. Le scénario synthétique de commande native bornée a atteint les marqueurs précédents puis
n'a pas créé sa preuve PID dans sa fenêtre de `1 500 ms`. Une entrée synthétique antérieure,
orpheline et d'état Windows `Unknown`, est en outre visible par `tasklist`/CIM mais pas ouvrable par
`Get-Process`. Ces constats sont distincts de l'incident PostgreSQL réel ; ils rendent néanmoins la
qualification runtime courante non reproductible et doivent entrer dans le périmètre correctif. Un
répertoire temporaire synthétique, antérieur de plus de quatre heures au verify rouge, subsiste
également hors dépôt ; il n'est pas attribué à cette exécution mais doit être couvert par l'audit de
nettoyage renforcé.

```text
WORK_ORDER_STATUS=BLOCKED_AFTER_BACKUP_CLEANUP_UNCONFIRMED
EVIDENCE_STATUS=DRAFT
OFFLINE_READINESS=PASS_AFTER_VALIDATED_WO024

BACKUP_ATTEMPT_RESULT=FAILED_FAIL_CLOSED
BACKUP_ENCRYPTION_PIPELINE=COMPLETED_NOT_QUALIFIED
EXACT_POSTGRES_SESSION_CLEANUP_CONFIRMATION=FAILED_OR_UNVERIFIABLE
BACKUP_QUALIFIED=NO
FINAL_ARCHIVE_PUBLICATION=NO_BY_CODE_PATH
MANIFEST_STATUS=NOT_CREATED
RESTORE_PHASE=NOT_STARTED
RESTORE_QUALIFIED=NO

POST_INCIDENT_EXACT_SESSION_OBSERVATIONS=0,0,0
POST_INCIDENT_ALL_OWNED_SESSION_OBSERVATIONS=0,0,0
POST_INCIDENT_EXACT_PROCESS_COUNT=0
POST_INCIDENT_TEMPORARY_RESTORE_DATABASE_COUNT=0
POST_INCIDENT_BACKUP_DIRECTORY_FILE_COUNT=0
POST_INCIDENT_PARTIAL_FILE_COUNT=0
POST_INCIDENT_LISTENER_8087_COUNT=0

DEFAULT_CLEANUP_TIMEOUT_MS=5000
WO024_DOCKER_QUALIFIED_CLEANUP_TIMEOUT_MS=10000
DEFAULT_PATH_DOCKER_QUALIFIED=NO
ROOT_CAUSE=INCONCLUSIVE_WITH_PROVEN_QUALIFICATION_MISMATCH
PERSISTENT_SESSION_SURVIVAL_PROVEN=NO

POST_INCIDENT_CLEAN_VERIFY=FAIL_945_TESTS_1_FAILURE_0_ERRORS_4_SKIPPED
J6_BOUNDED_NATIVE_COMMAND_PID_EVIDENCE=NOT_CREATED
CURRENT_RUNTIME_QUALIFICATION_REPRODUCIBLE=NO
SYNTHETIC_UNKNOWN_STATE_PROCESS_ENTRY_COUNT=1
SYNTHETIC_PROCESS_ABSENCE_PROOF=FAILED_CROSS_API_CORROBORATION
SYNTHETIC_TEMP_DIRECTORY_RESIDUAL_COUNT=1

OWNER_BACKUP_RETRY_AUTHORIZATION=CONSUMED_BY_FAILED_ATTEMPT
WO023_BACKUP_RETRY_AUTHORIZED_NOW=NO
NEXT_GATE=OWNER_DECISION_ON_DISTINCT_RUNTIME_CORRECTIVE_WORK_ORDER

NEW_SERIES_DIRECT_ATTEMPTS=0
AUDIT_CUMULATIVE_DIRECT_ATTEMPTS=20
PROVIDER_ACCESS_PERFORMED=NO
CODEX_LOCAL_UI_PROVIDER_ACTIONS_ALLOWED_NOW=NO
CAMPAIGN_EXECUTION_AUTHORIZED=NO
WO023_PROVIDER_CAMPAIGN_RESUME_AUTHORIZED=NO
PROVIDER_NETWORK_AUTHORIZED=NO
GLOBAL_OWNER_GO=NOT_GRANTED
OWNER_GO_CONSUMED=NO
NEW_GLOBAL_OWNER_GO_GRANTED=NO
PRIMARY_DATABASE_PURGE=NO
INTEGRATION_OR_PRODUCTION_AUTHORIZED=NO
J9_FINAL_DECISION=NOT_TAKEN
```

WO-024 est validé et gelé. Toute modification du script, de la borne de nettoyage, de la logique
d'observation PostgreSQL, du parsing, de la classification d'erreur ou de la preuve PID/absence
multi-API exige un Work Order runtime, une branche/worktree dédiés et une autorisation propriétaire
d'implémentation. Même si aucun changement de code n'était finalement nécessaire, une nouvelle
tentative réelle exigerait une nouvelle décision propriétaire explicite.

## 13. Validation de WO-025 et porte courante de WO-023

Le paragraphe 12 reste la photographie immuable de l'incident et de la porte alors ouverte. Le
Work Order correctif distinct
[`WO-SS-20260831-025-j9-backup-cleanup-proof-hardening`](../completed/WO-SS-20260831-025-j9-backup-cleanup-proof-hardening.md)
a depuis livré puis qualifié localement les valeurs effectives, le nettoyage PostgreSQL exact, les
classifications sanitées, la preuve native multi-API et le nettoyage du temp root exactement
possédé. Le propriétaire a validé WO-025 à `2026-08-31T22:35:48.6609979Z` et autorisé son
déplacement vers les Work Orders terminés.

Cette validation satisfait la porte corrective technique ; elle ne recrée pas l'autorisation de
sauvegarde WO-023 consommée par l'essai précédent. Conformément au bloc propriétaire de clôture de
WO-025, la prochaine porte de WO-023 est exclusivement une décision propriétaire séparée sur un
nouvel essai de sauvegarde/restauration. Elle n'autorise encore ni la campagne fournisseur ni son
réseau.

```text
STATE_RECONCILED_AT_UTC=2026-08-31T22:35:48.6609979Z
WORK_ORDER_STATUS=BLOCKED_AFTER_BACKUP_CLEANUP_UNCONFIRMED
EVIDENCE_STATUS=DRAFT

WO025_STATUS=VALIDATED
WO025_LOCAL_READINESS=PASS_LOCAL_ACCEPTED_BY_OWNER
WO025_FAIL_CLOSED_AND_EXACT_OWNERSHIP_INVARIANTS=PRESERVED
WO025_PID_ONLY_TERMINATION_USED=NO
WO025_MOVE_TO_COMPLETED_PERFORMED=YES
WO025_CLEANUP_PROOF_HARDENING_QUALIFIED=YES
WO025_WORK_ORDER_LOCATION=docs/work_orders/completed/WO-SS-20260831-025-j9-backup-cleanup-proof-hardening.md

OWNER_BACKUP_RETRY_AUTHORIZATION=NOT_GRANTED_AFTER_WO025_VALIDATION
BACKUP_QUALIFIED=NO
J9_WO023_BACKUP_RETRY_AFTER_WO025_VALIDATION=REQUIRES_SEPARATE_OWNER_DECISION
J9_WO023_BACKUP_RETRY_AUTHORIZED=NO
WO023_BACKUP_RETRY_AUTHORIZED_NOW=NO
NEXT_GATE=SEPARATE_OWNER_DECISION_ON_WO023_BACKUP_RETRY

NEW_SERIES_DIRECT_ATTEMPTS=0
AUDIT_CUMULATIVE_DIRECT_ATTEMPTS=20
PROVIDER_ACCESS_PERFORMED=NO
CODEX_LOCAL_UI_PROVIDER_ACTIONS_ALLOWED_NOW=NO
CAMPAIGN_EXECUTION_AUTHORIZED=NO
WO023_PROVIDER_CAMPAIGN_RESUME_AUTHORIZED=NO
PROVIDER_NETWORK_AUTHORIZED=NO
J9_WO023_PROVIDER_CAMPAIGN_RESUME_AUTHORIZED=NO
J9_PROVIDER_NETWORK_AUTHORIZED=NO
GLOBAL_OWNER_GO=NOT_GRANTED
OWNER_GO_CONSUMED=NO
NEW_GLOBAL_OWNER_GO_GRANTED=NO
J9_NEW_PROVIDER_GLOBAL_GO_GRANTED=NO
PRIMARY_DATABASE_PURGE=NO
J9_PRIMARY_DATABASE_PURGE=NO
INTEGRATION_OR_PRODUCTION_AUTHORIZED=NO
J9_INTEGRATION_OR_PRODUCTION_AUTHORIZED=NO
J9_FINAL_DECISION=NOT_TAKEN
```

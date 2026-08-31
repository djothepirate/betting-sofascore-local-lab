# WO-SS-20260831-023 — Campagne autonome de robustesse fournisseur J9 sous ADR-SS-002 v1.1

- **Statut :** `OPEN_AWAITING_OFFLINE_READINESS`
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

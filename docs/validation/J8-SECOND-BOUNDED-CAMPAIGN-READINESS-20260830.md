# J8 — Readiness de la seconde campagne bornée du 2026-08-30

## 1. Autorisation et état

```text
WORK_ORDER=WO-SS-20260829-016
WORK_ORDER_STATUS=QUALIFICATION_RUNNING_AFTER_SEPARATE_GO
OWNER_GO=GRANTED_BY_OWNER_2026_08_30
OWNER_GO_SCOPE=ONE_END_TO_END_BOUNDED_J8_PATH
EXECUTION_ACTOR=CODEX_LOCAL_UI
WINDOW_FROM=2026-08-30T09:24:51.0887925Z
WINDOW_TO=PENDING_TERMINAL
WINDOW_SEMANTICS=[FROM,TO)
CURRENT_FLYWAY_VERSION=28
PROVIDER_CALLS_DURING_READINESS=0
ENV_FILE_MUTATION=NO
```

Le propriétaire autorise la poursuite de J8 après réussite et clôture du correctif V15. Cette
décision est le go distinct requis par WO-016 pour un seul nouveau parcours complet. Elle ne
réécrit pas la campagne initiale et n'autorise ni troisième campagne, ni retry, ni polling.

## 2. Candidat déterministe

La sélection PostgreSQL locale a été exécutée en transaction `REPEATABLE READ READ ONLY`. Elle
retient le dernier export J7 `HUMAN_VALIDATED` terminal disposant d'un état canonique, d'un détail
et des trois familles J5 issus de snapshots fournisseur directs, avec départage stable par
identifiant fournisseur croissant.

```text
TARGET_LABEL=Cittadella — Atalanta U23
TARGET_PROVIDER_EVENT_ID=16691018
TARGET_CANONICAL_EVENT_ID=f4713f80-4769-3656-ba51-61d8ac1aa814
TARGET_STARTS_AT=2026-08-15T19:00:00Z
TARGET_STATUS=finished
TARGET_DATE=2026-08-15
TARGET_PHASE_ID=15118
TARGET_ROUTE_TOURNAMENT_ID=824
TARGET_SEASON_ID=99790
LATEST_DIRECT_STATISTICS=COMPLETE
LATEST_DIRECT_INCIDENTS=PARTIAL
LATEST_DIRECT_LINEUPS=PARTIAL
```

## 3. Expiration naturelle des caches

Les `request_key` ont été utilisées uniquement dans les prédicats SQL locaux et ne sont pas
rendues dans cette preuve. Aucun cache n'a été supprimé, modifié, invalidé ou contourné.

```text
J3_TARGET_CACHE_ROWS=15
J3_TARGET_CACHE_NEWEST_AT=2026-08-30T03:40:13.608Z
J3_TARGET_CACHE_AGE_SECONDS_AT_CHECK=20608
J3_TARGET_CACHE_EXPIRED_600_SECONDS=YES
TOURNAMENT_TARGET_CACHE_ROWS=1
TOURNAMENT_TARGET_CACHE_NEWEST_AT=2026-08-30T03:51:20.581Z
TOURNAMENT_TARGET_CACHE_AGE_SECONDS_AT_CHECK=19980
TOURNAMENT_TARGET_CACHE_EXPIRED_600_SECONDS=YES
CACHE_MUTATION=NO
```

## 4. Baseline ledger et plafonds

```text
BASELINE_J8_CAMPAIGNS=5
BASELINE_J8_UNITS=23
BASELINE_J8_ATTEMPTS=22
BASELINE_J8_UNIT_RESULTS=23
BASELINE_J8_CAMPAIGN_RESULTS=5
EXPECTED_NEW_CAMPAIGNS_MAXIMUM=4
EXPECTED_NEW_DIRECT_ATTEMPTS_MAXIMUM=30
J3_SCHEDULED_EVENTS_MAXIMUM=25
J3_TOURNAMENT_DISCOVERY_MAXIMUM=1
J4_EVENT_DETAILS_PHASE2_MAXIMUM=1
J5_EVENT_DATA_MAXIMUM=3
PROVIDER_RETRY=NO
MAXIMUM_CONCURRENCY=1
MINIMUM_DELAY_SECONDS=3
```

## 5. Garde-fous d'exécution

- configuration réseau limitée aux arbres de processus explicitement lancés ;
- `.env` inchangé et bloquant ;
- contexte Playwright non persistant neuf pour chaque lancement ;
- aucun profil, cookie, `storageState`, HAR, trace, vidéo, capture ou téléchargement ;
- aucune nouvelle URI, aucun changement d'allowlist, de worker ou de protocole IPC ;
- arrêt au premier refus, incompatibilité, contenu inattendu, panne ou arrêt opérateur ;
- aucun retry ou fallback ;
- aucun autre parcours manuel dans la fenêtre UTC exclusive ;
- application et worker arrêtés entre les phases lorsque le protocole local l'exige.

```text
SERVER_ADDRESS=127.0.0.1
AUTOMATIC_POLLING=NO
SCHEDULER=NO
FALLBACK=NO
ENV_FILE_MUTATION=NO
PROVIDER_ACCESS_DURING_READINESS=NO
READY_FOR_SINGLE_BOUNDED_EXECUTION=YES
```

## 6. Postcondition après consommation du go

Le go a été consommé par une seule instance locale. Les quatre campagnes ont terminé, le plafond
de trente tentatives est respecté et aucun incident n'a ouvert une branche de reprise. La preuve
détaillée est `J8-SECOND-BOUNDED-CAMPAIGN-20260830.md`.

```text
OWNER_GO_CONSUMED=YES
WINDOW_TO=2026-08-30T09:44:03.2695965Z
J3_RESULT=COMPLETED_15_OF_15
TOURNAMENT_DISCOVERY_RESULT=COMPLETED_1_OF_1
J4_PHASE2_RESULT=COMPLETED_1_OF_1
J5_RESULT=COMPLETED_3_OF_3
ACTUAL_DIRECT_ATTEMPTS=20
MEASUREMENT_STATE=MEASURED
POPULATION_HASH=c61b3ef3a9ac12f94d787da8c396dae58e4208a6f04aa240538e38eac5ab4726
EXPORTS_BYTE_IDENTICAL=YES
ENV_FILE_MUTATION=NO
LOCAL_CONFIGURATION_RELOCKED=YES
APPLICATION_STOPPED=YES
PORT_8087_AFTER_STOP=FREE
ADDITIONAL_CAMPAIGN_AUTHORIZED=NO
NEXT_STATE=READY_FOR_HUMAN_QUALIFICATION
```

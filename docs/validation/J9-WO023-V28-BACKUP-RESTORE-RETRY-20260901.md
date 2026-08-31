# J9 — qualification du nouvel essai V28 de sauvegarde/restauration WO-023

## 1. Identité et portée

```text
REPORT_ID=J9-WO023-V28-BACKUP-RESTORE-RETRY-20260901
WORK_ORDER=WO-SS-20260831-023-j9-provider-robustness-v11
BRANCH=codex/j9-provider-robustness-v11
AUTHORIZATION_DOCUMENT_COMMIT=23cd271f9c78519ceccd0d2a48c27f1c62810a37
WO025_VALIDATED_COMMIT=dfbdeefb663981653716bc6b7b4794b1b56c3630
WO025_QUALIFIED_RUNTIME_COMMIT=d019be274f200aaa33124b041e3773cf15ae2723
EVIDENCE_SCOPE=LOCAL_V28_ENCRYPTED_BACKUP_ISOLATED_RESTORE_AND_POSTFLIGHT
PROVIDER_ACCESS_PERFORMED=NO
PRIMARY_DATABASE_PURGE=NO
```

Le rapport qualifie uniquement le nouvel essai local chiffré autorisé après la validation de
WO-025. Il ne documente ni n'autorise une acquisition SofaScore, une campagne fournisseur, un go
global, une intégration ou une exploitation en production. Le chemin externe de l'archive et la
phrase secrète `age` sont volontairement absents de la documentation et de Git.

## 2. Autorisation et consommation

Le propriétaire a fourni la décision séparée exacte : « J'autorise un nouvel essai de
sauvegarde/restauration ». Elle a été consignée avant exécution au commit `23cd271`.

Le préflight a d'abord rencontré une erreur de paramètre lors de la création du parent externe,
puis un premier lancement a été refusé par `Start-Process` parce que plusieurs exécutables
`pwsh.exe` avaient été résolus. Dans les deux cas, `Backup-Restore-J6.ps1` n'avait pas été invoqué :
le jeton restait donc à `0_OF_1`. Le lancement suivant a fixé les identités absolues qualifiées et a
constitué l'unique invocation réelle.

```text
OWNER_DECISION=AUTHORIZE_ONE_NEW_ATTEMPT
AUTHORIZATION_CONSUMPTION_POINT=FIRST_REAL_BACKUP_RESTORE_SCRIPT_INVOCATION_AFTER_GREEN_PREFLIGHT
REAL_BACKUP_RESTORE_SCRIPT_INVOCATIONS=1
WO023_BACKUP_RETRY_AUTHORIZATION_USE_COUNT=1_OF_1
WO023_BACKUP_RETRY_AUTHORIZED_NOW=NO
AUTOMATIC_RETRY_PERFORMED=NO
```

Le parcours opératoire est resté celui déjà qualifié : phrase auto-générée par `age` dans un
terminal PowerShell 7 natif non capturé, première invite laissée vide puis ressaisie humaine au
déchiffrement. Codex n'a ni lu le terminal, ni reçu, ni journalisé la phrase.

## 3. Readiness et préflight

WO-025 avait qualifié le runtime J6 modifié avec les suites standards, d'intégration, Verify-Local,
Compose et les contre-épreuves Docker locales. Un diff exact a confirmé l'absence de changement du
runtime et de la configuration fournisseur depuis la readiness WO-023 au commit `8b91bf8` ; les
preuves loopback J3/J4/J5 sont donc reportées sans nouvel accès fournisseur. La suite standard a en
outre été rejouée avant l'essai sur l'état courant.

```text
CURRENT_STANDARD_VERIFY=PASS_946_TESTS_0_FAILURES_0_ERRORS_5_SKIPPED
CURRENT_STANDARD_VERIFY_FINISHED_AT_UTC=2026-08-31T22:51:11Z
PROVIDER_RUNTIME_AND_CONFIG_DIFF_FROM_WO023_PRIOR_READINESS=NONE
WO023_PRIOR_J3_J4_J5_LOOPBACK_READINESS_CARRIED_FORWARD_BY_EXACT_DIFF=YES

WO023_PREFLIGHT=PASS
AUTHORIZATION_USE_COUNT_DURING_PREFLIGHT=0_OF_1
POWERSHELL_VERSION=7.6.5
AGE_VERSION=v1.3.1
AGE_SHA256=90f5cc37249c06e0b302e476a8a63bcefeecd9437c192b8af33e6ff2d69558dd
DOCKER_SHA256=0f97bc1111f59d859766ba938691ee07ed4e58d5fdaeb6f4dfb10a5ef5394753
COMPOSE_CONFIG=PASS
POSTGRES_HEALTH=HEALTHY
CONNECTOR_CONTROL=SAFE
FLYWAY_VERSION=28
MAX_PROVIDER_SNAPSHOT_ID=814
PROVIDER_SNAPSHOT_OCCURRENCE_COUNT=781
J8_PROVIDER_CALL_ATTEMPT_COUNT=116
RAW_PAYLOAD_INTEGRITY_FAILURE_COUNT=0
J6_OWNED_POSTGRES_SESSION_COUNT=0
J6_TEMPORARY_RESTORE_DATABASE_COUNT=0
OWNED_NATIVE_PROCESS_COUNT=0
LISTENER_8087_COUNT=0
PARTIAL_ARTIFACT_COUNT=0
DESTINATION_PARENT=EXISTS_CANONICAL_NON_REPARSE_OUTSIDE_REPOSITORY
DESTINATION_AND_MANIFEST=ABSENT_BEFORE_EXECUTION
DISK_SPACE=PASS_GE_1GB
```

## 4. Archive et manifeste publiés

L'archive porte l'en-tête `age-encryption.org/v1`. Le manifeste UTF-8 sans secret respecte le
format `1`, le but `J6_RAW_PAYLOAD_RETENTION_BACKUP`, référence le seul nom de fichier terminal et
porte le même SHA-256 que le fichier chiffré. Les deux seuls fichiers publiés dans le parent sont
l'archive et son manifeste.

```text
J6_BACKUP_RESULT=QUALIFIED
J6_BACKUP_CIPHER_SHA256=526f2faa22f8da0506194030f6f7fbe0966e65550310375d055041d4ea257041
J6_BACKUP_MANIFEST_SHA256=7994a3b2ec6ced505b36514ad0b5eb113c5c1b30a0a77811e4d1a7ae80a2eaa9
J6_BACKUP_CIPHER_SIZE_BYTES=7268470
J6_BACKUP_MANIFEST_SIZE_BYTES=2203
J6_BACKUP_CREATED_AT_UTC=2026-08-31T22:57:10.9961533Z
J6_BACKUP_QUALIFIED_AT_UTC=2026-08-31T22:57:55.7088914Z
J6_BACKUP_COVERAGE_MAX_SNAPSHOT_ID=814
J6_BACKUP_COVERAGE_RECEIVED_AT=2026-08-31T07:59:01.196000Z
MANIFEST_FORMAT_VERSION=1
MANIFEST_PURPOSE=J6_RAW_PAYLOAD_RETENTION_BACKUP
MANIFEST_RESTORE_QUALIFIED=true
MANIFEST_SENSITIVE_KEY_SCAN=PASS
PUBLISHED_FILE_COUNT=2
```

## 5. Égalité source/restauration

Le script a restauré l'archive dans une base temporaire isolée, puis a comparé chaque valeur et
chaque empreinte du jeu source au jeu restauré avant publication. La relecture indépendante du
manifeste confirme un jeu de clés identique et zéro différence de valeur.

| Mesure | Source | Restauration | Résultat |
|---|---:|---:|---|
| Version Flyway | 28 | 28 | `MATCH` |
| Snapshots | 734 | 734 | `MATCH` |
| Occurrences | 781 | 781 | `MATCH` |
| Observations canoniques | 261 | 261 | `MATCH` |
| Observations de détails | 107 | 107 | `MATCH` |
| Observations J5 | 293 | 293 | `MATCH` |
| Audits de purge J6 | 0 | 0 | `MATCH` |
| Campagnes J8 | 31 | 31 | `MATCH` |
| Unités J8 | 117 | 117 | `MATCH` |
| Tentatives fournisseur J8 | 116 | 116 | `MATCH` |
| Résultats d'unité J8 | 117 | 117 | `MATCH` |
| Résultats de campagne J8 | 31 | 31 | `MATCH` |
| Snapshot maximal couvert | 814 | 814 | `MATCH` |
| Échecs d'intégrité brute | 0 | 0 | `MATCH` |

```text
SNAPSHOT_METADATA_SHA256=6dd366b8ff8f760dfef93ee85a54d87eb6b7c1d790d98b8e4613cb865de6f179
OCCURRENCE_SHA256=265055ebd454a83efbae7a31e67778216ae926177132bb9f2e27ab179cd7b916
NORMALIZED_PROVENANCE_SHA256=131c6e2c818678a930fcc1844a01d295d45a9c91850bce902c1a91dbc593dbff
J8_BENCHMARK_SHA256=74174dbfb04238ff2d352932ceb38ccc2ed275189c9ae05da638a5fb5c202294
SOURCE_RESTORE_MISMATCH_COUNT=0
RESTORE_QUALIFIED=YES
```

## 6. Postflight et confinement

Les compteurs primaires relus après qualification correspondent aux métadonnées source du
manifeste. La base primaire n'a donc pas été purgée ou modifiée par ce cycle. L'audit a initialement
compté son propre processus PowerShell parce que sa ligne de commande contenait le nom du host J6 ;
le recomptage l'a explicitement exclu et trouve zéro véritable processus `age` ou host natif J6.
Le terminal opérateur lancé avec `-NoExit` restait ouvert au moment du contrôle, sans processus
enfant J6, afin que l'opérateur puisse le fermer manuellement sans que Codex lise son scrollback.

```text
WO023_BACKUP_RESTORE_POSTFLIGHT=PASS
PRIMARY_DATABASE_STATE_MATCHES_MANIFEST_SOURCE=YES
PRIMARY_DATABASE_PURGE=NO_BY_UNCHANGED_COUNTS
CONNECTOR_CONTROL=SAFE
FLYWAY_VERSION=28
RAW_PAYLOAD_INTEGRITY_FAILURE_COUNT=0
J6_OWNED_POSTGRES_SESSION_COUNT=0
J6_TEMPORARY_RESTORE_DATABASE_COUNT=0
AGE_PROCESS_COUNT=0
J6_NATIVE_HOST_PROCESS_COUNT=0
OPERATOR_TERMINAL_COUNT_BEFORE_HUMAN_CLOSE=1
LISTENER_8087_COUNT=0
PARTIAL_ARTIFACT_COUNT=0
PROVIDER_ACCESS_PERFORMED=NO
```

## 7. Verdict et prochaine porte

```text
BACKUP_QUALIFIED=YES
RESTORE_QUALIFIED=YES
BACKUP_RESTORE_RETRY_RESULT=PASS
MANIFEST_STATUS=QUALIFIED_NOT_YET_FROZEN_FOR_PROVIDER_GO
WORK_ORDER_STATUS=READY_FOR_FINAL_MANIFEST_FREEZE_AND_INDEPENDENT_LEDGER_CORROBORATION
NEXT_GATE=FINAL_MANIFEST_FREEZE_AND_INDEPENDENT_LEDGER_CORROBORATION

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

La réussite de la porte de sauvegarde/restauration ne réactive aucune autorisation de campagne. Le
manifeste doit d'abord être figé par ses hashes et le ledger direct corroboré indépendamment ; un
éventuel accès fournisseur exigera ensuite un nouveau go propriétaire global explicite, lié au
manifeste, au corpus, au plafond et à une nouvelle fenêtre UTC bornée.

## 8. Corroboration indépendante complémentaire

Un second audit en lecture seule, distinct du contrôle postflight initial, a recalculé directement
sur la base primaire les quatre empreintes du manifeste. Les quatre valeurs correspondent à la fois
aux objets `source` et `restored`. Il a aussi vérifié le schéma exact du manifeste, l'absence de clé
ou valeur sensible et l'en-tête `age-encryption.org/v1`.

Trois observations successives ont confirmé la stabilité du nettoyage :

```text
J6_OWNED_POSTGRES_SESSION_OBSERVATIONS=0,0,0
J6_TEMPORARY_RESTORE_DATABASE_OBSERVATIONS=0,0,0
AGE_PROCESS_OBSERVATIONS=0,0,0
DOCKER_CLI_PROCESS_OBSERVATIONS=0,0,0
PG_DUMP_HOST_PROCESS_OBSERVATIONS=0,0,0
PG_RESTORE_HOST_PROCESS_OBSERVATIONS=0,0,0
LISTENER_8087_OBSERVATIONS=0,0,0
BACKUP_ROOT_PARTIAL_FILE_COUNT=0
POSTGRES_CONTAINER_STATE=RUNNING_HEALTHY
POSTGRES_PUBLISHED_INTERFACE=127.0.0.1
```

Le ledger append-only PostgreSQL a été corroboré indépendamment sans transport :

```text
HISTORICAL_DIRECT_ATTEMPTS_CORROBORATED=20
HISTORICAL_LEDGER_FIRST_STARTED_AT_UTC=2026-08-31T07:25:57.738322Z
HISTORICAL_LEDGER_LAST_STARTED_AT_UTC=2026-08-31T07:59:01.092327Z
TOTAL_J8_PROVIDER_ATTEMPTS=116
MAX_J8_PROVIDER_ATTEMPT_ID=116
MAX_J8_PROVIDER_ATTEMPT_STARTED_AT_UTC=2026-08-31T07:59:01.092327Z
DUPLICATE_ATTEMPT_UNIT_COUNT=0
ATTEMPTS_AFTER_BACKUP_CREATED_AT=0
NEW_SERIES_DIRECT_ATTEMPTS=0
INDEPENDENT_LEDGER_CORROBORATION=PASS
PROVIDER_ACCESS_PERFORMED=NO
```

Cette corroboration satisfait la porte indépendante du ledger et permet le gel d'un manifeste de
campagne versionné. Elle ne vaut toujours pas go propriétaire ni autorisation réseau.

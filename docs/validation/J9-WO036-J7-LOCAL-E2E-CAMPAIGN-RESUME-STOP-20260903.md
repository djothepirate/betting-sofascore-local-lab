# J9 — reprise arrêtée de la campagne E2E synthétique Windows/Windows WO-036

## 1. Verdict

```text
REPORT_ID=J9-WO036-J7-LOCAL-E2E-CAMPAIGN-RESUME-STOP-20260903
REPORT_STATUS=FINAL_FOR_STOPPED_RESUME_ATTEMPT
REPORT_GENERATED_AT_UTC=2026-09-03T10:47:41.7739637Z
REPORT_GENERATED_AT_EUROPE_PARIS=2026-09-03T12:47:41.7739637+02:00
WORK_ORDER=WO-SS-20260902-036-j9-j7-local-e2e-qualification
WORK_ORDER_LIFECYCLE=ACTIVE
CAMPAIGN_MANIFEST=J9-WO036-J7-LOCAL-E2E-CAMPAIGN-MANIFEST-RESUME-20260903
CAMPAIGN_MANIFEST_COMMIT=07fd440a6773526aeb2b28e7c43413023967f6d1
CAMPAIGN_MANIFEST_SHA256=6ef4c4fbe8af1027ff06170bea00aa8ca75c3159bcd21134db2598ddd4b18a78
EVIDENCE_RESULT=STOPPED
STOP_CLASS=LOCAL_LAB_DUPLICATE_ACK_RECEIPT_TIME_SEMANTICS_MISMATCH
STOP_PHASE=AFTER_SECOND_IMPORT_ROUTE_CALL_BEFORE_COLLISION_PROBE
EXPECTED_SEQUENCE_201_200_409=STOPPED_AFTER_SECOND_CALL
RECEIVER_SEQUENCE=201_IMPORTED,200_DUPLICATE
LOCAL_LEDGER_SEQUENCE=DELIVERED,UNKNOWN_RECONCILIATION_REQUIRED
LOCAL_B_SAFE_RESULT_CODE=ACK_HTTP_STATUS_MISMATCH
RECEIVER_IMPORT_ROUTE_CALLS=2
MAXIMUM_IMPORT_ROUTE_CALLS=3
AUTOMATIC_RETRIES=0
COLLISION_PROBE=NOT_PERFORMED_AFTER_GLOBAL_STOP
PRE_COLLISION_EVIDENCE=NOT_QUALIFIED
PROVIDER_CALLS=0
REMOTE_RECEIVER_CALLS=0
WORK_ORDER_MOVE_TO_COMPLETED=NO
OWNER_DECISION_REQUIRED=YES
```

La reprise autorisée a exécuté une série neuve avec un corpus entièrement synthétique, deux bases
Local Lab isolées, le receiver INT-001 réel sur loopback et une PKI mTLS éphémère. Le premier envoi
a produit `201/IMPORTED` et a été classé `DELIVERED`. Le second envoi, effectué depuis la base B
clonée avant le premier claim avec le même export et les mêmes octets, a produit côté receiver le
duplicate durable attendu, mais le sender l'a classé `UNKNOWN_RECONCILIATION_REQUIRED` avec le code
sûr `ACK_HTTP_STATUS_MISMATCH`.

La série a été arrêtée immédiatement. Aucun retry ni probe de collision n'a été exécuté. Le résultat
est `STOPPED`, et non `PASS` : l'idempotence et la persistance du receiver sont établies, mais le
sender n'a pas accepté l'ACK duplicate contractuel.

## 2. Provenance et préconditions

```text
LOCAL_LAB_REGISTERED_HEAD=0deabc707821797cb5d878f63082315b72fd9a38
LOCAL_LAB_CAMPAIGN_HEAD=07fd440a6773526aeb2b28e7c43413023967f6d1
WO037_CLOSURE_ANCESTOR=9a10447d0c94e441b860b942e436e2c943f1e2c1
RECEIVER_HEAD=b6a093ab4d3358f23a59b65b68a3eb720494bcba
JAVA_TARGET=25
JAVA_SHA256=12cdbbb7c110c5e86b76d9170c820981a3adf7b1eb890ec6acf73f43a44a2ade
LOCAL_LAB_JAR_SHA256=6790127c86914dc0ceaf05ef5f67f4b39d39161fc039ece8e4e96b31d8eb7e56
RECEIVER_JAR_SHA256=d41f74e984fa2455e535623fc570b30f91fd2b03d7742ada81c7c948f67c0c7f
LOCAL_LAB_STANDARD_VERIFY=PASS_1132_TESTS_0_FAILURES_0_ERRORS_5_SKIPPED
LOCAL_LAB_INTEGRATION_VERIFY=PASS_85_TESTS_0_FAILURES_0_ERRORS_0_SKIPPED
RECEIVER_STANDARD_VERIFY=PASS_297_TESTS_0_FAILURES_0_ERRORS_0_SKIPPED
RECEIVER_INTEGRATION_VERIFY=PASS_98_TESTS_0_FAILURES_0_ERRORS_0_SKIPPED
WO036_PESTER_TESTS=PASS_52_OF_52
FROZEN_TOOLING_POST_COMMIT_CHECK=PASS
LOCAL_LAB_DATABASE_CLONE=PASS
CLONE_OPERATION_SOURCE_DATABASE_MUTATED=NO
PRIMARY_DATABASE_CONTENT_READ_OR_MUTATED=NO
```

Le manifeste distinct a été gelé avant le premier `POST`, puis le contrôle de tooling gelé a été
rejoué après son commit. Les exécutables, commits, hashes, bases, listeners et certificats employés
correspondent à cette provenance. Le clonage n’a pas muté sa base source ; le contenu de la base
primaire n’a été ni lu ni muté. Son conteneur a uniquement suivi le cycle d’arrêt/redémarrage
explicitement autorisé et consigné en section 6. Aucun shim Java, artefact du premier essai ou état
privé antérieur n'a été réutilisé.

## 3. Export J7 synthétique

```text
EXPORT_ID=09d5d614-237b-4147-9f61-1893f2c1b261
J7_SIZE_BYTES=7616
J7_FILE_SHA256=6e42680aef1341b73249391ffb811533cbc56d15e4cb4c0e47d16700f3a19d76
J7_DATA_SHA256=b2d81d294651c0d1cb2958a6ce95d3ef0ef9dd26d41f63e8f3bf8823961b2cbd
J7_SCHEMA_ID=urn:betting-project:sofascore-local-lab:j7:canonical-event-export:v1
J7_SCHEMA_VERSION=1.0.0
J7_ROOT_ENVELOPE=manifest,data
J7_VALIDATION=HUMAN_VALIDATED
J7_PROVENANCE=SYNTHETIC_ONLY
J7_SOURCE_COUNT=5
```

Les octets J7 ne sont pas reproduits dans ce rapport. Le hash du payload durable côté receiver est
identique au hash du fichier envoyé, et sa taille durable est exactement `7616` octets.

## 4. Deux appels effectivement exécutés

| Étape | Local Lab | HTTP observé | Code sûr sender | Receiver durable |
|---|---|---:|---|---|
| A — premier import | `DELIVERED` | `201` | `HTTP_201_IMPORTED` | un receipt, un payload, un audit `IMPORTED`, un outbox `J7_IMPORT_ACCEPTED` |
| B — répétition byte-identique | `UNKNOWN_RECONCILIATION_REQUIRED` | `200` | `ACK_HTTP_STATUS_MISMATCH` | aucun second receipt ni payload, un audit `DUPLICATE`, aucun second outbox |

```text
LOCAL_LAB_A_DELIVERY_ATTEMPT_COUNT=1
LOCAL_LAB_A_DELIVERY_RESULT_COUNT=1
LOCAL_LAB_A_FINAL_STATE=DELIVERED
LOCAL_LAB_A_HTTP_STATUS=201
LOCAL_LAB_B_DELIVERY_ATTEMPT_COUNT=1
LOCAL_LAB_B_DELIVERY_RESULT_COUNT=1
LOCAL_LAB_B_FINAL_STATE=UNKNOWN_RECONCILIATION_REQUIRED
LOCAL_LAB_B_HTTP_STATUS=200
RECEIVER_GLOBAL_RECEIPT_COUNT=1
RECEIVER_GLOBAL_PAYLOAD_COUNT=1
RECEIVER_IMPORTED_AUDIT_COUNT=1
RECEIVER_DUPLICATE_AUDIT_COUNT=1
RECEIVER_GLOBAL_AUDIT_COUNT=2
RECEIVER_GLOBAL_OUTBOX_J7_COUNT=1
RECEIVER_OUTBOX_PENDING_COUNT=1
RECEIVER_OUTBOX_ATTEMPT_COUNT=0
PAYLOAD_SIZE_MATCH=YES
FILE_SHA256_MATCH=YES
DATA_SHA256_MATCH=YES
```

La répétition est donc idempotente côté receiver : elle n'a créé ni deuxième inbox, ni deuxième
payload, ni deuxième événement d'outbox. Le sender a néanmoins conservé son état fail-closed et
exigé une réconciliation manuelle. Aucun ACK brut, résultat SQL ou payload n'est versionné.

## 5. Cause déterministe

Le receiver INT-001 est conforme à son contrat versionné. Pour un duplicate exact,
`J7ImportService` retourne l'import existant ; `J7ImportController` répond `200/DUPLICATE` en
réemployant le `remoteImportId` et le `receivedAt` durables du premier import. Le contrat
`docs/contracts/j7-import-receiver-v1.md` et les tests PostgreSQL du receiver imposent explicitement
cette sémantique.

Le sender reconnaît correctement les couples `201/IMPORTED` et `200/DUPLICATE`, puis applique aux
deux le même invariant temporel dans `J7OptionalDeliveryService.classify()` :

```text
ack.receivedAt >= currentAttempt.startedAt
ack.receivedAt <= currentHttpResponse.receivedAt
```

Pour l'appel B, le receiver réemploie nécessairement l'instant durable de A, antérieur au début de
la tentative B. La borne inférieure devient donc fausse et le sender retourne
`ACK_HTTP_STATUS_MISMATCH`, bien que le statut HTTP, le statut d'ACK, le protocole, l'export ID et
les deux hashes concordent.

```text
RECEIVER_HTTP_STATUS=200
RECEIVER_ACK_STATUS=DUPLICATE
RECEIVER_ACK_RECEIVED_AT_SEMANTICS=INITIAL_DURABLE_IMPORT_TIME
RECEIVER_RESULT=CONTRACT_COMPLIANT
ACK_IDENTITY_AND_HASH_CORRELATION=VALID
ACTUAL_REJECTION_CAUSE=DUPLICATE_INITIAL_RECEIVED_AT_PRECEDES_CURRENT_CLAIM
ROOT_CAUSE=LOCAL_LAB_SENDER_TEMPORAL_INVARIANT_INCOMPATIBLE_WITH_RECEIVER_CONTRACT
ROOT_CAUSE_CONFIDENCE=HIGH_CODE_CONTRACT_AND_RUNTIME_EVIDENCE_CONCORDANT
RECEIVER_CHANGE_REQUIRED=NO
LOCAL_LAB_RUNTIME_CHANGE_REQUIRED=YES
```

Les tests existants n'exerçaient pas cet ordre temporel réel : ils utilisaient une horloge fixe et
construisaient l'ACK duplicate avec le même instant que le claim courant. Une correction minimale
doit conserver toutes les corrélations et la borne haute, mais ne pas appliquer au duplicate la
borne basse propre à un nouvel import. Elle doit aussi réexaminer séparément la comparaison stricte
d'horloges entre deux machines avant un futur scénario Windows vers VPS.

## 6. Arrêt, nettoyage et restauration du primaire

```text
GLOBAL_STOP_AFTER_UNKNOWN=PASS
AUTOMATIC_RETRY_AFTER_UNKNOWN=0
COLLISION_PROBE=NOT_PERFORMED
PRIVATE_REDACTED_EVIDENCE_SIZE_BYTES=5215
PRIVATE_REDACTED_EVIDENCE_SHA256=7f02e5f9fac59419de7051cfd608e6dad749b33cb3ef8121eba993990781dda9
PRIVATE_LOG_FILE_COUNT=8
PRIVATE_LOG_TOTAL_SIZE_BYTES=31678
PRIVATE_LOG_FORBIDDEN_OCCURRENCES=0
PRIVATE_LOG_REDACTION=PASS
LOCAL_LAB_A_STOP=STOPPED_GRACEFULLY
LOCAL_LAB_B_STOP=STOPPED_GRACEFULLY
RECEIVER_STOP=STOPPED_GRACEFULLY
OWNED_PROCESS_RESIDUAL_COUNT=0
OWNED_CONTAINER_RESIDUAL_COUNT=0
OWNED_VOLUME_RESIDUAL_COUNT=0
OWNED_CERTIFICATE_RESIDUAL_COUNT=0
LISTENER_8087_AFTER_CLEANUP=0
LISTENER_8444_AFTER_CLEANUP=0
LISTENER_5432_AFTER_CLEANUP=0
LISTENER_5433_AFTER_CLEANUP=0
PRIVATE_RUN_ROOT_REMOVED=YES
PRIMARY_DATABASE_TOUCHED_DURING_CAMPAIGN_CLEANUP=NO
```

Le répertoire privé, la PKI, les bases, les conteneurs et les journaux de campagne ont été supprimés
après extraction des seules métadonnées expurgées ci-dessus. Le conteneur PostgreSQL primaire exact,
dont l'arrêt temporaire avait été autorisé, a ensuite été redémarré sans suppression ni recréation :

```text
PRIMARY_POSTGRES_RESTART=PASS
PRIMARY_POSTGRES_IDENTITY=EXACT
PRIMARY_POSTGRES_IMAGE=postgres:18.4-alpine
PRIMARY_POSTGRES_RESTART_POLICY=unless-stopped
PRIMARY_POSTGRES_VOLUME=EXACT_RW
PRIMARY_POSTGRES_BIND=127.0.0.1:5432
PRIMARY_POSTGRES_HEALTH=healthy
PRIMARY_DATABASE_PURGE=NO
```

## 7. Porte suivante

WO-036 reste actif et arrêté. Aucun changement runtime n'est réalisé sous ce Work Order de
campagne. La correction du sender exige un Work Order distinct, une qualification offline et
loopback, une validation propriétaire, puis une nouvelle décision de reprise de WO-036 avec un run
et un manifeste entièrement neufs. Le numéro `038` était disponible lors de ce constat, mais aucun
Work Order n'est ouvert sans autorisation propriétaire.

```text
WO036_STATUS=STOPPED_AFTER_DUPLICATE_ACK_PENDING_DISTINCT_RUNTIME_CORRECTION
WO036_RESUME_AUTHORIZED=NO
WO036_WORK_ORDER_MOVE_TO_COMPLETED=NO
NEXT_GATE=OWNER_DECISION_ON_DISTINCT_RUNTIME_WORK_ORDER

J9_OFFICIAL_PERMISSION_STATUS=NOT_EVIDENCED
PROVIDER_DERIVED_REAL_DELIVERY_AUTHORIZED=NO
PROVIDER_NETWORK_AUTHORIZED=NO
REAL_RECEIVER_NETWORK_AUTHORIZED=NO
REMOTE_RECEIVER_NETWORK_AUTHORIZED=NO
VPS_DEPLOYMENT_AUTHORIZED=NO
PRODUCTION_AUTHORIZED=NO
```

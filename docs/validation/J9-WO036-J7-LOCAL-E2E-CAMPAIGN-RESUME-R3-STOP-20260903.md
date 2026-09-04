# J9 — reprise R3 arrêtée de la campagne E2E synthétique Windows/Windows WO-036

## 1. Verdict

```text
REPORT_ID=J9-WO036-J7-LOCAL-E2E-CAMPAIGN-RESUME-R3-STOP-20260903
REPORT_STATUS=FINAL_FOR_STOPPED_RESUME_R3_ATTEMPT
REPORT_GENERATED_AT_UTC=2026-09-03T13:58:55.1914489Z
REPORT_GENERATED_AT_EUROPE_PARIS=2026-09-03T15:58:55.1914489+02:00
WORK_ORDER=WO-SS-20260902-036-j9-j7-local-e2e-qualification
WORK_ORDER_LIFECYCLE=ACTIVE
CAMPAIGN_MANIFEST=J9-WO036-J7-LOCAL-E2E-CAMPAIGN-MANIFEST-RESUME-R3-20260903
CAMPAIGN_MANIFEST_COMMIT=f14c1418995625ea653f8d01afd9c81044f3abd1
CAMPAIGN_MANIFEST_SHA256=cd20a30cd855d161fcaa0f5db93ef0c50fca5c297d569d0a4af2f4f02aab842d
EVIDENCE_RESULT=STOPPED
STOP_CLASS=COLLISION_PROBE_NON_409_PROTOCOL_RESPONSE
STOP_PHASE=AFTER_THIRD_IMPORT_ROUTE_CALL_DURING_COLLISION_PROBE
EXPECTED_SEQUENCE_201_200_409=STOPPED_AT_THIRD_CALL_NON_409
RECEIVER_SEQUENCE=201_IMPORTED,200_DUPLICATE,NON_409
LOCAL_LEDGER_SEQUENCE=DELIVERED,DUPLICATE_CONFIRMED
PRE_COLLISION_EVIDENCE=PASS_EXACTLY_TWO_CALLS
COLLISION_PROBE=FAILED_OR_UNKNOWN_CONSUMED
COLLISION_PROBE_REUSABLE=NO
COLLISION_HTTP_STATUS=UNKNOWN_NON_409
RECEIVER_IMPORT_ROUTE_CALLS=3
MAXIMUM_IMPORT_ROUTE_CALLS=3
AUTOMATIC_RETRIES=0
PROVIDER_CALLS=0
PROVIDER_DERIVED_PAYLOADS=0
REMOTE_RECEIVER_CALLS=0
WORK_ORDER_MOVE_TO_COMPLETED=NO
OWNER_DECISION_REQUIRED=YES
```

La campagne R3 n'a pas qualifié la séquence complète `201/200/409`. Les deux premiers échanges
sont conformes et confirment le correctif temporel WO-038 dans le parcours Windows/Windows réel.
Le troisième et dernier appel a reçu une réponse différente de `409` ; sa claim est donc
définitivement `FAILED_OR_UNKNOWN_CONSUMED`. Aucun rejeu ni retry n'est autorisé sous cette
campagne.

## 2. Provenance et préconditions

```text
LOCAL_LAB_REGISTERED_HEAD=a92831d64bebc240ea5dbfa2166ff6ec844974a3
LOCAL_LAB_CAMPAIGN_HEAD=f14c1418995625ea653f8d01afd9c81044f3abd1
EXPECTED_LOCAL_LAB_SENDER_ANCESTOR=3a0c297a5151c572417b4f2f12bb5c3ed216172f
WO038_CLOSURE_ANCESTOR=a2d44150af6ad6a7931e29d6291a7df711e171e9
RECEIVER_HEAD=b6a093ab4d3358f23a59b65b68a3eb720494bcba
RECEIVER_IMPLEMENTATION_ANCESTOR=3920a58c122cbee0fb379781abcd53d3eaa0f70d
JAVA_TARGET=25
JAVA_SHA256=12cdbbb7c110c5e86b76d9170c820981a3adf7b1eb890ec6acf73f43a44a2ade
LOCAL_LAB_JAR_SIZE_BYTES=61007772
LOCAL_LAB_JAR_SHA256=d6af41cb1aae386820847cf18de1eabcb11253f23204c58430030a3f5701d946
RECEIVER_JAR_SIZE_BYTES=26777553
RECEIVER_JAR_SHA256=d41f74e984fa2455e535623fc570b30f91fd2b03d7742ada81c7c948f67c0c7f
DOCKER_ENDPOINT_SHA256=2e1a13b5c38007e19366225454c425eb89626875521ad4eddc04f1b533aa4908
REGISTERED_TOOLING_MANIFEST_SHA256=71d5ed0ebdc450a1b9dd2fb70dca3f05322cd66237221911b885326e56638396
LOCAL_LAB_STANDARD_VERIFY=PASS_1136_TESTS_0_FAILURES_0_ERRORS_5_SKIPPED
LOCAL_LAB_INTEGRATION_VERIFY=PASS_89_TESTS_0_FAILURES_0_ERRORS_0_SKIPPED
RECEIVER_STANDARD_VERIFY=PASS_297_TESTS_0_FAILURES_0_ERRORS_0_SKIPPED
RECEIVER_INTEGRATION_VERIFY=PASS_98_TESTS_0_FAILURES_0_ERRORS_0_SKIPPED
WO036_PESTER_TESTS=PASS_53_OF_53
FROZEN_TOOLING_POST_COMMIT_CHECK=PASS
LOCAL_LAB_DATABASE_CLONE=PASS
SOURCE_DATABASE_MUTATED=NO
PRIMARY_DATABASE_TOUCHED=NO
```

Le manifeste distinct a été gelé avant le premier `POST`, puis le contrôle de l'outillage gelé a
été rejoué après son commit. Les exécutables, commits, hashes, bases, listeners et certificats
employés correspondaient à cette provenance. Le clonage A vers B n'a pas muté sa base source et le
contenu de la base primaire n'a été ni lu ni modifié. R1 et R2, leurs manifestes et leurs rapports
restent immuables.

## 3. Export J7 synthétique

```text
CANONICAL_EVENT_ID=9740bb59-0207-31a3-a6ae-5c8463255887
SYNTHETIC_PROVIDER_EVENT_ID=900001
EXPORT_ID=69563e54-8dc2-4ea4-82e9-ed5b1d0c046b
J7_SIZE_BYTES=7616
J7_FILE_SHA256=c862dba6f025c2cfe57d26622d3d48239f27355198a7daac0ff8ab97ea1169a7
J7_DATA_SHA256=b2d81d294651c0d1cb2958a6ce95d3ef0ef9dd26d41f63e8f3bf8823961b2cbd
J7_SCHEMA_ID=urn:betting-project:sofascore-local-lab:j7:canonical-event-export:v1
J7_SCHEMA_VERSION=1.0.0
J7_ROOT_ENVELOPE=manifest,data
J7_SELECTION_MODE=LATEST_AVAILABLE
J7_VALIDATION=HUMAN_VALIDATED
J7_PROVENANCE=SYNTHETIC_ONLY
J7_SOURCE_COUNT=5
J7_SOURCE_PRESENT_COUNT=5
J7_SOURCE_ORDER=EVENT_STATE,EVENT_DETAILS,EVENT_STATISTICS,EVENT_INCIDENTS,EVENT_LINEUPS
J7_SOURCE_KIND=SYNTHETIC_FIXTURE_ONLY
J7_WARNING_SET=SYNTHETIC_SOURCE_ONLY
PRE_CALL_PRIVATE_LOG_FILE_COUNT=6
PRE_CALL_PRIVATE_LOG_SIZE_BYTES=23730
PRE_CALL_PRIVATE_LOG_FORBIDDEN_OCCURRENCES=0
PRE_CALL_PRIVATE_LOG_REDACTION=PASS
PRE_CALL_FREEZE_AT_UTC=2026-09-03T13:32:56.6094716+00:00
PRE_CALL_FREEZE_SIZE_BYTES=3985
PRE_CALL_FREEZE_SHA256=078ed52453b74d7436150b637ddb5f257c1998b0e99a3b3e42691b81f1521b76
IMPORT_ROUTE_CALLS_BEFORE_FREEZE=0
```

Les octets J7 ne sont pas reproduits dans ce rapport. Les cinq sources sont des fixtures
synthétiques ; aucun payload dérivé de SofaScore n'a été créé, lu ou livré par la campagne.

## 4. Trois appels effectivement exécutés

| Étape | État ou claim locale | HTTP observé | Code sûr | Receiver durable |
|---|---|---:|---|---|
| A — premier import | `DELIVERED` | `201` | `HTTP_201_IMPORTED` | un receipt, un payload, un audit `IMPORTED`, un outbox |
| B — répétition byte-identique | `DUPLICATE_CONFIRMED` | `200` | `HTTP_DUPLICATE_CONFIRMED` | aucun second receipt, payload ou outbox ; un audit `DUPLICATE` |
| Sonde — mutation metadata-only | `FAILED_OR_UNKNOWN_CONSUMED` | non-`409`, valeur exacte non préservée | non préservé | aucun nouvel objet durable et aucun audit `DIVERGENCE_REJECTED` |

```text
LOCAL_LAB_A_DELIVERY_ATTEMPT_COUNT=1
LOCAL_LAB_A_DELIVERY_RESULT_COUNT=1
LOCAL_LAB_A_FINAL_STATE=DELIVERED
LOCAL_LAB_A_HTTP_STATUS=201
LOCAL_LAB_B_DELIVERY_ATTEMPT_COUNT=1
LOCAL_LAB_B_DELIVERY_RESULT_COUNT=1
LOCAL_LAB_B_FINAL_STATE=DUPLICATE_CONFIRMED
LOCAL_LAB_B_HTTP_STATUS=200
COLLISION_PROBE_CALL_ORDINAL=3
COLLISION_PROBE_CLAIM_STATUS=FAILED_OR_UNKNOWN_CONSUMED
COLLISION_PROBE_CLAIM_SIZE_BYTES=733
COLLISION_PROBE_CLAIM_SHA256=4bda7fefca2c6ae68bfbfd7526f8ccc07be03b456c14a46c8a8c79dd314f72f2
COLLISION_PROBE_HTTP_STATUS=UNKNOWN_NON_409
COLLISION_PROBE_REUSABLE=NO
PRE_COLLISION_EVIDENCE_SIZE_BYTES=5202
PRE_COLLISION_EVIDENCE_SHA256=d5eca5c7304c78308ebf4c728d05ce62bb6fb845ca5ae9ac9437ef54ec43c8dd
RECEIVER_GLOBAL_RECEIPT_COUNT=1
RECEIVER_GLOBAL_PAYLOAD_COUNT=1
RECEIVER_IMPORTED_AUDIT_COUNT=1
RECEIVER_DUPLICATE_AUDIT_COUNT=1
RECEIVER_DIVERGENCE_AUDIT_COUNT=0
RECEIVER_GLOBAL_AUDIT_COUNT=2
RECEIVER_GLOBAL_OUTBOX_J7_COUNT=1
PAYLOAD_SIZE_MATCH=YES
FILE_SHA256_MATCH=YES
DATA_SHA256_MATCH=YES
```

Les résultats A et B qualifient le premier import, l'idempotence exacte et l'acceptation par le
sender du `receivedAt` durable réemployé. Ils établissent qu'un seul receipt, un seul payload et un
seul événement d'outbox existent après le duplicate. Le troisième appel a consommé le plafond de
trois sans atteindre le branchement durable de divergence ; il ne peut pas être relancé sous R3.

## 5. Cause fortement étayée, non capturée comme réponse runtime

La sonde construit son `Content-Type` avec le type .NET `MediaTypeHeaderValue`. Une reproduction
locale sans réseau montre que cette API sérialise la valeur avec un espace avant le paramètre
`version` :

```text
INPUT=application/vnd.betting-project.j7-canonical-event+json;version=1.0
SERIALIZED=application/vnd.betting-project.j7-canonical-event+json; version=1.0
EXACT_MATCH=False
```

Le receiver compare le `Content-Type` par égalité textuelle stricte et traite une différence comme
`INVALID_CONTENT_TYPE`, en HTTP `400`, avant l'appel au service d'import. L'absence de troisième
audit et, en particulier, d'audit `DIVERGENCE_REJECTED` concorde avec ce rejet précoce.

Cependant, le harnais R3 n'a conservé ni le statut numérique exact ni le code du `ProblemDetail`
après avoir constaté que la réponse n'était pas `409`. `400/INVALID_CONTENT_TYPE` est donc la
conséquence attendue du chemin de code, pas une observation runtime certifiée de R3.

```text
ROOT_CAUSE_STATUS=HIGH_CONFIDENCE_CODE_PATH_INFERENCE_NOT_RUNTIME_CAPTURED
PROBABLE_HTTP_STATUS=400
PROBABLE_RECEIVER_ERROR=INVALID_CONTENT_TYPE
PROBABLE_ROOT_CAUSE=DOTNET_TYPED_MEDIA_TYPE_SERIALIZATION_INSERTS_OWS_BEFORE_VERSION_PARAMETER
OFFLINE_SERIALIZATION_REPRODUCTION=PASS_MISMATCH_REPRODUCED
RECEIVER_SERVICE_COLLISION_BRANCH_REACHED=NO
RECEIVER_CHANGE_REQUIRED=NO
LOCAL_LAB_JAVA_SENDER_CHANGE_REQUIRED=NO
WO038_REGRESSION_EVIDENCED=NO
WO036_CAMPAIGN_TOOLING_RUNTIME_CHANGE_REQUIRED=YES
```

Le correctif futur ne doit pas être préjugé : l'ajout non validé de l'en-tête à
`HttpContentHeaders` a également reproduit la forme espacée hors ligne. Il doit démontrer la valeur
effectivement transmise et reçue, préserver une preuve sûre du statut inattendu, et ne modifier ni
le média type contractuel ni le receiver.

## 6. Arrêt, nettoyage et restauration du primaire

```text
GLOBAL_STOP_AFTER_COLLISION_NON_409=PASS
AUTOMATIC_RETRY_AFTER_COLLISION_NON_409=0
COLLISION_PROBE_REPLAYED=NO
FINAL_PRIVATE_LOG_FILE_COUNT=8
FINAL_PRIVATE_LOG_TOTAL_SIZE_BYTES=31089
FINAL_PRIVATE_LOG_FORBIDDEN_OCCURRENCES=0
FINAL_PRIVATE_LOG_REDACTION=PASS
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
PRIMARY_POSTGRES_RESTART=PASS
PRIMARY_POSTGRES_IDENTITY=EXACT
PRIMARY_POSTGRES_IMAGE=postgres:18.4-alpine
PRIMARY_POSTGRES_RESTART_POLICY=unless-stopped
PRIMARY_POSTGRES_VOLUME=EXACT_RW
PRIMARY_POSTGRES_BIND=127.0.0.1:5432
PRIMARY_POSTGRES_HEALTH=healthy
PRIMARY_DATABASE_PURGE=NO
```

Le premier cleanup s'est bloqué dans le processus `certutil.exe` exactement possédé, lors du
retrait du certificat racine éphémère du magasin `CurrentUser/Root`. Après vérification de son PID,
de son ascendance et de sa commande, ce seul descendant a été terminé. Le certificat exact a été
retiré directement de `X509Store` seulement après concordance de son SHA-256, de son sujet et de
son empreinte. Le cleanup idempotent a ensuite été rejoué et a attesté zéro processus, conteneur,
volume, certificat et listener résiduel. Aucune suppression large ou fondée sur le seul PID n'a été
effectuée.

Le répertoire privé, la PKI, les bases, les conteneurs et les journaux de campagne ont été supprimés
après extraction des seules métadonnées expurgées ci-dessus. Le conteneur PostgreSQL primaire exact,
dont l'arrêt temporaire avait été autorisé, a ensuite été redémarré sans suppression ni recréation,
avec son identité, son volume RW, son bind loopback et sa politique de redémarrage inchangés.

## 7. Vérifications post-campagne

```text
RECEIVER_STANDARD_POSTFLIGHT=PASS_297_TESTS_0_FAILURES_0_ERRORS_0_SKIPPED
RECEIVER_INTEGRATION_POSTFLIGHT=PASS_98_TESTS_0_FAILURES_0_ERRORS_0_SKIPPED
RECEIVER_POSTFLIGHT_BUILD=SUCCESS
RECEIVER_TESTCONTAINERS_RESIDUAL_COUNT=0
LOCAL_LAB_PESTER_POSTFLIGHT=PASS_53_TESTS_0_FAILURES_0_SKIPPED
LOCAL_LAB_STANDARD_POSTFLIGHT=PASS_1136_TESTS_0_FAILURES_0_ERRORS_5_SKIPPED_AND_89_INTEGRATION_TESTS
LOCAL_LAB_INTEGRATION_POSTFLIGHT=PASS_1136_TESTS_0_FAILURES_0_ERRORS_5_SKIPPED_AND_89_INTEGRATION_TESTS
LOCAL_LAB_POSTFLIGHT_BUILD=SUCCESS
LOCAL_LAB_TESTCONTAINERS_RESIDUAL_COUNT=0
POSTFLIGHT_SECRET_SCAN=PASS_HIGH_CONFIDENCE
POSTFLIGHT_UTF8_NO_BOM_OR_NUL=PASS_7_OF_7
POSTFLIGHT_GIT_DIFF_CHECK=PASS
```

Les suites du receiver et du Local Lab ont été rejouées après le cleanup. Le JAR Local Lab produit
par ce rebuild postflight diffère mécaniquement du JAR enregistré de campagne ; il n'est ni présenté
ni substitué comme binaire de R3. Les hashes et tailles des sections 2 et 3 restent exclusivement
ceux gelés avant le premier appel. Aucun conteneur, volume ou réseau Testcontainers ne subsiste.

## 8. Porte suivante

WO-036 reste actif. L'autorisation R3 et sa limite de trois appels sont consommées. La sonde ne peut
être rejouée. La correction et la qualification du client de sonde doivent relever d'un Work Order
runtime distinct. Après sa validation, une nouvelle reprise de WO-036 nécessitera encore une
décision propriétaire séparée, un run entièrement neuf et un manifeste R4 distinct.

```text
WO036_STATUS=STOPPED_AFTER_CONSUMED_COLLISION_PROBE_PENDING_DISTINCT_TOOLING_RUNTIME_CORRECTION
WO036_RESUME_AUTHORIZED=NO
WO036_WORK_ORDER_MOVE_TO_COMPLETED=NO
NEXT_GATE=OWNER_DECISION_ON_DISTINCT_RUNTIME_WORK_ORDER

J9_OFFICIAL_PERMISSION_STATUS=NOT_EVIDENCED
LOCAL_SYNTHETIC_RECEIVER_LOOPBACK_AUTHORIZED=NO_PENDING_NEW_OWNER_DECISION
PROVIDER_DERIVED_REAL_DELIVERY_AUTHORIZED=NO
PROVIDER_NETWORK_AUTHORIZED=NO
REAL_RECEIVER_NETWORK_AUTHORIZED=NO
REMOTE_RECEIVER_NETWORK_AUTHORIZED=NO
LIVE_DELIVERY_AUTHORIZED=NO
VPS_DEPLOYMENT_AUTHORIZED=NO
PRODUCTION_AUTHORIZED=NO
INT001_PULL_REQUEST_AUTHORIZED_BY_THIS_WORK_ORDER=NO
INT001_VALIDATION_AUTHORIZED_BY_THIS_WORK_ORDER=NO
PRIMARY_DATABASE_PURGE=NO
```

# J9 — campagne E2E synthétique Windows/Windows WO-036 du 2026-09-03

## 1. Verdict

```text
REPORT_ID=J9-WO036-J7-LOCAL-E2E-CAMPAIGN-20260903
REPORT_STATUS=FINAL_FOR_STOPPED_ATTEMPT
WORK_ORDER=WO-SS-20260902-036-j9-j7-local-e2e-qualification
WORK_ORDER_LIFECYCLE=ACTIVE
CAMPAIGN_MANIFEST=J9-WO036-J7-LOCAL-E2E-CAMPAIGN-MANIFEST-20260902
CAMPAIGN_MANIFEST_COMMIT=c6a1e0a4f8fbf57279344d24d0cb0e521d315d9d
CAMPAIGN_MANIFEST_SHA256=e996e631fdc905c91b8288852fbba7b878023985db7d5bdd228dccdaeefbe960
EVIDENCE_RESULT=STOPPED
STOP_CLASS=LOCAL_BROWSER_BOUNDARY_INCOMPATIBILITY_PRE_RECEIVER
STOP_RECORDED_AT_UTC=2026-09-03T08:32:00.0883991Z
STOP_RECORDED_AT_EUROPE_PARIS=2026-09-03T10:32:00.0883991+02:00
EXPECTED_SEQUENCE_201_200_409=NOT_STARTED
RECEIVER_IMPORT_ROUTE_CALLS=0
SENDER_DURABLE_ATTEMPTS=0
PROVIDER_CALLS=0
REMOTE_RECEIVER_CALLS=0
WORK_ORDER_MOVE_TO_COMPLETED=NO
OWNER_DECISION_REQUIRED=YES
```

La campagne a franchi les qualifications hors ligne, le démarrage des deux applications et bases
isolées, la création puis la validation humaine d'un export J7 synthétique et le gel pré-appel.
Elle s'est arrêtée sur le premier geste navigateur de préparation de livraison. Le Local Lab a
répondu `403` avant le contrôleur, avant tout claim et avant tout appel du transport HTTPS/mTLS.
La séquence receiver `201/200/409` n'a donc pas commencé.

Le résultat est `STOPPED`, et non un échec du receiver ou de mTLS. La cause mesurée est une
incompatibilité entre deux protections du sender lorsqu'elles sont exercées par un navigateur réel.
Le contournement de cette frontière par un client HTTP, une réécriture d'en-tête ou une injection
dans le navigateur a été exclu.

## 2. Préconditions effectivement franchies

```text
STANDARD_VERIFY=PASS_1115_TESTS_0_FAILURES_0_ERRORS_5_SKIPPED
INTEGRATION_VERIFY=PASS_85_TESTS_0_FAILURES_0_ERRORS_0_SKIPPED
POST_CAMPAIGN_CLEAN_VERIFY=PASS_1115_TESTS_0_FAILURES_0_ERRORS_5_SKIPPED
POST_CAMPAIGN_INTEGRATION_VERIFY=PASS_85_TESTS_0_FAILURES_0_ERRORS_0_SKIPPED
POST_CAMPAIGN_BUILD_RESULT=SUCCESS
POST_CAMPAIGN_VERIFY_LOCAL=PASS
POST_CAMPAIGN_VERIFY_LOCAL_PROVIDER_CALLS=0
FROZEN_TOOLING_INITIAL_CHECK=PASS
FROZEN_TOOLING_POST_REFUSAL_CHECK=PASS
LOCAL_LAB_DATABASE_CLONE=PASS
J7_VALIDATION=HUMAN_VALIDATED
J7_PROVENANCE=SYNTHETIC_ONLY
J7_ROOT_ENVELOPE=manifest,data
PRE_CALL_IMPORT_ROUTE_CALLS=0
LOCAL_LAB_LISTENER=127.0.0.1:8087
RECEIVER_LISTENER=127.0.0.1:8444
MTLS_CLIENT_AUTH=NEED
```

Les octets J7, leur identité et leurs hashes restent ceux du manifeste gelé. Ils ne sont pas
reproduits dans ce rapport. Les mots de passe, cookies, jetons de formulaire, clés privées,
certificats, ACK, sorties SQL, chemins privés et identifiants de session restent hors Git et hors
documentation.

## 3. Observation du navigateur réel

Deux soumissions HTML natives du bouton `Préparer la livraison` ont été observées dans Brave : la
première a établi le refus ; la seconde, toujours avant receiver, a servi uniquement à classifier
les en-têtes dans DevTools. Elle n'a fait l'objet d'aucun export HAR, trace, capture enregistrée ou
copie de corps, cookie ou jeton.

```text
NATIVE_BROWSER=BRAVE_CHROMIUM
LOCAL_PREPARE_SUBMISSIONS=2
REQUEST_METHOD=POST
REQUEST_PATH_CLASS=J7_DELIVERY_PREPARE
HTTP_STATUS=403_FOR_BOTH_SUBMISSIONS
HOST_CLASS=EXACT_127_0_0_1_8087
ORIGIN_CLASS=NULL
FORWARDED_PRESENT=NO
X_FORWARDED_HOST_PRESENT=NO
X_FORWARDED_PROTO_PRESENT=NO
SEC_FETCH_SITE_CLASS=SAME_ORIGIN
SEC_FETCH_MODE_CLASS=NAVIGATE
SEC_FETCH_DEST_CLASS=DOCUMENT
DEVTOOLS_HAR_EXPORTED=NO
DEVTOOLS_TRACE_OR_SCREENSHOT_PERSISTED=NO
```

Le `403` vide avec en-têtes no-store correspond à la sortie fermée de
`J7DeliveryLocalRequestBoundaryInterceptor`. Un jeton de formulaire absent ou incorrect aurait
été traité dans le contrôleur et n'explique pas ce statut. Le contrôle figé et la preuve SQL après
le refus confirment en outre qu'aucun effet durable ni appel receiver n'a eu lieu.

## 4. Cause établie

La chaîne est déterministe :

1. [`SecurityHeadersFilter`](../../src/main/java/com/bettingproject/sofascorelocal/config/SecurityHeadersFilter.java)
   ajoute `Referrer-Policy: no-referrer` à l'aperçu J7 ;
2. le standard Fetch impose, pour ce `POST` de navigation non-CORS, de remplacer l'origine
   sérialisée par `null` sous la politique `no-referrer`, puis d'ajouter l'en-tête `Origin` ;
3. [`J7DeliveryLocalRequestBoundaryInterceptor`](../../src/main/java/com/bettingproject/sofascorelocal/config/J7DeliveryLocalRequestBoundaryInterceptor.java)
   accepte seulement un `Origin` absent ou exactement égal à `http://127.0.0.1:8087` ;
4. l'en-tête effectivement mesuré, `Origin: null`, est donc refusé par `preHandle()` avant entrée
   dans `J7DeliveryController.prepare()`.

Sources normatives consultées le 2026-09-03 :

- [WHATWG Fetch — append a request Origin header](https://fetch.spec.whatwg.org/#origin-header) ;
- [W3C Referrer Policy — no-referrer et same-origin](https://w3c.github.io/webappsec-referrer-policy/#referrer-policy-no-referrer).

```text
ROOT_CAUSE=NO_REFERRER_PRODUCES_NULL_ORIGIN_REJECTED_BY_LOCAL_BOUNDARY
ROOT_CAUSE_CONFIDENCE=HIGH_MEASURED_AND_NORMATIVE
LOCAL_FORM_TOKEN_CONSUMED=NO
DELIVERY_CONFIRMATION_CREATED=NO
DELIVERY_CLAIM_CREATED=NO
TRANSPORT_INVOKED=NO
```

Les tests actuels de l'intercepteur construisent artificiellement un `Origin` exact ou absent. Ils
ne couvrent pas la chaîne réelle `réponse HTML -> politique de referrer -> soumission native ->
Origin émis`. La qualification locale du sender reste valide pour les cas qu'elle mesure, mais ne
constitue pas une preuve navigateur E2E de cette frontière.

## 5. Persistance et plafond d'appel

```text
LOCAL_LAB_A_DELIVERY_ROW_COUNT=0
LOCAL_LAB_A_DELIVERY_ATTEMPT_COUNT=0
LOCAL_LAB_A_DELIVERY_RESULT_COUNT=0
LOCAL_LAB_B_DELIVERY_ROW_COUNT=0
LOCAL_LAB_B_DELIVERY_ATTEMPT_COUNT=0
LOCAL_LAB_B_DELIVERY_RESULT_COUNT=0
RECEIVER_GLOBAL_RECEIPT_COUNT=0
RECEIVER_GLOBAL_PAYLOAD_COUNT=0
RECEIVER_GLOBAL_AUDIT_COUNT=0
RECEIVER_GLOBAL_OUTBOX_J7_COUNT=0
RECEIVER_IMPORT_ROUTE_CALLS=0
MAXIMUM_IMPORT_ROUTE_CALLS=3
AUTOMATIC_RETRIES=0
```

Le plafond receiver n'a pas été consommé, mais le run est terminé et son environnement privé a été
supprimé. Le manifeste gelé ne peut pas être réinterprété comme une autorisation de reprise sur un
nouveau binaire : toute correction change la provenance exécutable et exige une nouvelle
qualification, un nouveau manifeste et une décision propriétaire distincte.

## 6. Arrêt, nettoyage et restauration du primaire

```text
LOCAL_LAB_A_STOP=STOPPED_GRACEFULLY
RECEIVER_STOP=STOPPED_GRACEFULLY
LOCAL_LAB_B_START=NOT_PERFORMED_AFTER_GLOBAL_STOP
COLLISION_PROBE=NOT_PERFORMED_AFTER_GLOBAL_STOP
PRIVATE_LOG_FILE_COUNT=6
PRIVATE_LOG_FORBIDDEN_OCCURRENCES=0
PRIVATE_LOG_REDACTION=PASS
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

Le conteneur primaire exact, dont l'arrêt temporaire avait été autorisé, a ensuite été redémarré et
contrôlé sans suppression ni recréation :

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

## 7. Porte de reprise

WO-036 reste actif et bloqué. Aucun changement de `SecurityHeadersFilter`, de l'intercepteur ou du
sender n'est réalisé sous ce Work Order de campagne. Le correctif recommandé doit être traité par
un Work Order runtime distinct et préserver la protection cross-origin. Le candidat privilégié est
une politique `same-origin` bornée aux vues/actions J7 concernées, car elle conserve l'absence de
referrer cross-origin tout en permettant à la soumission locale de présenter son origine exacte.
Accepter globalement `Origin: null` serait plus faible, cette valeur couvrant aussi des origines
opaques.

La requalification devra exercer un vrai navigateur loopback, démontrer la préparation `200` sans
appel receiver, puis rejouer dans un nouveau run la séquence complète `201/200/409`.

```text
WO036_STATUS=STOPPED_PRE_RECEIVER_PENDING_DISTINCT_RUNTIME_CORRECTION
WO036_RESUME_AUTHORIZED=NO
WO036_WORK_ORDER_MOVE_TO_COMPLETED=NO
NEXT_GATE=OWNER_DECISION_ON_DISTINCT_RUNTIME_WORK_ORDER

J9_OFFICIAL_PERMISSION_STATUS=NOT_EVIDENCED
PROVIDER_DERIVED_REAL_DELIVERY_AUTHORIZED=NO
PROVIDER_NETWORK_AUTHORIZED=NO
REAL_RECEIVER_NETWORK_AUTHORIZED=NO
VPS_DEPLOYMENT_AUTHORIZED=NO
PRODUCTION_AUTHORIZED=NO
```

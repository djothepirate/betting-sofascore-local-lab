# WO-046 — Manifeste successeur R2 — J7 réel local Windows/Windows

## 1. Autorité, succession et gel

Après validation WO-049, le propriétaire autorise le nouveau manifeste, puis choisit
explicitement la réutilisation du volume receiver exact avec conservation intégrale de
l'audit et recontrôle préalable, sans purge ni POST. Ces octets deviennent immuables au
premier commit local. Leur hash, taille et commit sont consignés hors de ce fichier.

```text
MANIFEST_FORMAT=WO046_REAL_LOCAL_J7_CAMPAIGN_MANIFEST_V1
MANIFEST_REFERENCE=docs/validation/J9-WO046-J7-REAL-LOCAL-E2E-R2-MANIFEST-20260905.md
WORK_ORDER=WO-SS-20260904-046-j9-j7-real-local-e2e-campaign
CAMPAIGN_SERIES=WO046_REAL_LOCAL_R2
PREPARED_AT_UTC=2026-09-05T08:47:06Z
PREPARED_AT_EUROPE_PARIS=2026-09-05T10:47:06+02:00
PREPARATION_BASE_COMMIT=5a73605d3db3b0694cbda99cf7ebefeca741ad43
MANIFEST_CREATION_AUTHORIZED=YES
MANIFEST_FREEZE_POLICY=IMMUTABLE_FROM_FIRST_LOCAL_COMMIT
MANIFEST_COMMIT_POLICY=LOCAL_ONLY_NO_PUSH
SUPERSEDED_MANIFEST_REFERENCE=docs/validation/J9-WO046-J7-REAL-LOCAL-E2E-CAMPAIGN-MANIFEST-20260904.md
SUPERSEDED_MANIFEST_COMMIT=2cdb93d2236955a34528a11cc982fc01f4554b46
SUPERSEDED_MANIFEST_SHA256=6590603286c6c422588bbc3f6a46f502716fa2e2df18b6ad0df741cbd1cbf277
R1_OWNER_GO_STATUS=REVOKED_UNUSED_NOT_REUSABLE
NEW_OWNER_GO_OWNER_INTENT=GRANTED
NEW_CANONICAL_OWNER_GO_CREATED=NO
NEW_OWNER_GO_REGISTERED=NO
NEW_OWNER_GO_CONSUMED=NO
CAMPAIGN_EXECUTION_AUTHORIZED=NO
RECEIVER_APPLICATION_START_AUTHORIZED=NO
LOCAL_LAB_CAMPAIGN_START_AUTHORIZED=NO
REAL_POST_AUTHORIZED=NO
```

L'accord général pour un nouveau go est consigné, mais n'est pas un bloc V2 exact. Le format
V2 exige une autorisation de POST à YES ; aucune telle valeur n'est déduite du présent NO.
Après ce gel, une décision explicite devra permettre un bloc complet, sa fenêtre future et
son enregistrement avant exécution. Le grant révoqué R1, ses écritures et son manifeste
restent intacts ; la contrainte unique sur le hash de manifeste n'est pas contournée.

## 2. Exécutables et préparation de lancement

```text
LOCAL_LAB_COMMIT=f9788e63c3df862272a482ef83f5f45a9fa95371
LOCAL_LAB_JAR=target/betting-sofascore-local-lab-0.1.0-SNAPSHOT.jar
LOCAL_LAB_JAR_SIZE_BYTES=61061002
LOCAL_LAB_JAR_SHA256=f1643edb08a77ed1b53b563b05dd5426bfa53c69027f66bd3b866e012f2aca0f
LOCAL_LAB_JAVA_RELEASE=25
LOCAL_LAB_SPRING_BOOT_VERSION=4.1.0
RECEIVER_COMMIT=de06153f0908a1bb2dc9bbd2c8e22f7fd14dacfd
RECEIVER_IMPLEMENTATION_COMMIT=25c0229aac06df9ab380f0dfa77a3e70f34a5632
RECEIVER_JAR=target/betting-project-0.1.0-SNAPSHOT.jar
RECEIVER_JAR_SIZE_BYTES=27150202
RECEIVER_JAR_SHA256=baa41241e0fa23139ccd0dce551c11747da4567f0934bd79fc9af7005d6dd965
RECEIVER_JAVA_RELEASE=25
RECEIVER_SPRING_BOOT_VERSION=4.1.1
RECEIVER_PROFILE=control-api
RECEIVER_QUALIFICATION=PASS
SENDER_QUALIFICATION=PASS
LAUNCH_PREPARATION_WORK_ORDER=WO-SS-20260905-049-j9-wo046-launcher-stop-proof
LAUNCH_PREPARATION_IMPLEMENTATION_COMMIT=22130d16781cce41a370cbcdbf6cd1b7b91546c2
LAUNCH_PREPARATION_OWNER_CLOSURE_COMMIT=516a955de1033ddfe546b934fc7e3b2490ed486c
LAUNCH_PREPARATION_MODULE=scripts/wo046/WO046-LaunchPreparation.psm1
LAUNCH_PREPARATION_MODULE_SHA256=731565492e0f17a61b918019083ecbb7231a8ff64c7c2f807572b46196600bd6
LAUNCH_PREPARATION_QUALIFICATION=PASS_OFFLINE_FAIL_CLOSED
```

Les JAR sont rehashés, pas reconstruits ni lancés. Le receiver reste propre au commit exact.
Le diff Local Lab entre son commit source et la base R2 ne change ni `src/main`, ni le POM,
ni les wrappers Maven. Les commits documentaires ne remplacent pas les commits source.
La construction propre historique des JAR reste celle du manifeste R1.

Pour le futur lancement Local Lab, utiliser le module WO-049 épinglé : mapping complet avec
noms séparés, validation stricte puis `ProcessStartInfo.ArgumentList`, sans rejouer les
fragments ad hoc R1 fautifs. Le module ne lance rien et ne prouve pas l'autorité du go.
Avant tout démarrage futur, contrôler le binding du mapping complet avec les classes du
JAR exact, les hashes, l'identité des processus et le drainage confidentiel borné des flux.
La qualification hors ligne ne constitue pas une qualification de lancement réel.

## 3. Références qualifiées et audit officiel

Les références suivantes sont immuables ; leurs hashes ont été recontrôlés pour R2.
Les chemins sont relatifs au Local Lab, sauf le rapport INT-001 du dépôt Betting Project.

| Preuve | Référence | SHA-256 |
|---|---|---|
| Sender | `docs/validation/J9-WO035-REAL-J7-DELIVERY-SENDER-QUALIFICATION-20260902.md` | `1028761cbc68154da780d314935880bf17b803c3cf8a35b1386fd29ab28bae76` |
| E2E synthétique | `docs/validation/J9-WO036-J7-LOCAL-E2E-CAMPAIGN-RESUME-R6-20260903.md` | `17a299cef826a4cc8da3fd4ff50a20eec99aee447cc4a0b69fc79914d4ab4280` |
| Go atomique | `docs/validation/J9-WO045-PROVIDER-DERIVED-OWNER-GO-BOUNDARY-QUALIFICATION-20260904.md` | `5146c18542f0376a60bac5d4a25b68ff3f2fec9d7da700b32ee0aac60e16d946` |
| Gouvernance V2 | `docs/validation/J9-WO047-J7-DELIVERY-GOVERNANCE-SEPARATION-QUALIFICATION-20260904.md` | `75b55109c0705a44026376d7f0bdadf76d979dcd4268c1430d5ed8008d693540` |
| PKI | `docs/validation/J9-WO048-LOCAL-MTLS-IDENTITY-PROVISIONING-QUALIFICATION-20260904.md` | `c704961530020216bfbc644f2fa928357466eccf54ef4e3d80f5705a95ef109d` |
| Lanceur | `docs/validation/J9-WO049-LAUNCHER-STOP-PROOF-OFFLINE-QUALIFICATION-20260905.md` | `e273d917ca07b3a9a1cd303d93868ee22d2ab35e0b3b5a253cad271dbdf11ad8` |
| Receiver | `docs/reviews/INT-001-local-readiness.md` | `0ca2efb8e9424c2a776b95f9605ca8e5c38a15766a31ff900e09c46161cbcba2` |
| Primaire V30/V32 | `docs/validation/J9-WO046-PRIMARY-V30-V32-PREPARATION-20260905.md` | `398d608a06cf228bbf299b608517328da73f95ecf43701243ddc39ebb6ee50bd` |

```text
PROVIDER_PERMISSION_AUDIT_REFERENCE=docs/validation/J9-WO046-OFFICIAL-PERMISSION-RECONCILIATION-20260904.md
PROVIDER_PERMISSION_AUDIT_SHA256=707e0fd9b07dc0944225be80a590792e5e8ab0631dab964a465335f283ac1473
PROVIDER_PERMISSION_AUDIT_STATUS=NOT_EVIDENCED
J7_TRANSFER_GOVERNANCE_BASIS_REFERENCE=ADR-SS-003-optional-integration-topology.md
J7_TRANSFER_GOVERNANCE_BASIS_COMMIT=e1ec9936467dd570f7ed00c51227c8e7d5a35945
J7_TRANSFER_GOVERNANCE_BASIS_SHA256=ded6a4da8a3161caae491f62919f4f5c3569c69c821be5a807772cc542cede3f
J7_TRANSFER_GOVERNANCE_BASIS_STATUS=ADR_ACCEPTED_NO_EXECUTION_AUTHORITY
EVIDENCED_INCOMPATIBLE_EFFECT=BLOCK
J3_J4_J5_PERMISSION_GATE_CHANGE=NO
```

L'empreinte ADR désigne la proposition acceptée au commit indiqué, pas la copie enrichie
ultérieurement. Aucune permission SofaScore positive n'est affirmée. L'audit négatif reste
non bloquant pour le seul transfert local J7 sous v0.2, sans autoriser ce POST.

## 4. Export unique et état primaire recontrôlés

```text
EXPORT_RECHECK_AT_UTC=2026-09-05T08:46:31.577255Z
EXPORT_ID=a8d40d57-98c5-4e01-8ecc-4f1f9b5feabc
CANONICAL_EVENT_ID=e2c9599a-2336-3888-ae88-2437dad02b48
PROVIDER_EVENT_ID=16310945
EXPORT_KIND=J7_CANONICAL_EVENT
PAYLOAD_CLASS=PROVIDER_DERIVED
VALIDATION_STATUS=HUMAN_VALIDATED
FILE_SIZE_BYTES=35663
FILE_SHA256=d4aba249231bb9d575f40b5c23a629b938f685ee7d4cc479bfa7d846d61be3f6
DATA_SHA256=f95ae31e711a9433d6d06745d37f2535143266aa97c7d151fd26bea88ef03ac6
SOURCE_SET_SHA256=20b5326195551a70dec597e24d102cc178ed3ac4e3304001854e728943371c7e
SCHEMA_ID=urn:betting-project:sofascore-local-lab:j7:canonical-event-export:v1
SCHEMA_VERSION=1.0.0
ROOT_ENVELOPE=manifest,data
GENERATED_AT_UTC=2026-09-04T10:27:48.637024Z
HUMAN_DECIDED_AT_UTC=2026-09-04T10:28:13.856199Z
SOURCE_METADATA_COUNT=5
PROVIDER_SNAPSHOT_SOURCE_COUNT=4
WARNING=MISSING_COMPONENT:EVENT_DETAILS
DECISION_INTENT_MATCH=YES
FILE_SIZE_AND_HASH_MATCH=YES
REPLACEMENT_EXPORT_ALLOWED=NO
NEW_PROVIDER_ACQUISITION_ALLOWED=NO
PRIMARY_CONTAINER_ID=e7b218117df39b7cbfbffb3b1223647568f149912a84592e506b5690f9686c1f
PRIMARY_DATABASE_SCHEMA=32
PRIMARY_FAILED_MIGRATIONS=0
PRIMARY_CONNECTOR_NETWORK_ENABLED=NO
PRIMARY_CONNECTOR_CIRCUIT_STATE=LOCKED
INITIAL_J7_DELIVERIES=0
INITIAL_J7_DELIVERY_ATTEMPTS=0
HISTORICAL_OWNER_GO_GRANTS=1
HISTORICAL_OWNER_GO_REVOCATIONS=1
INITIAL_OWNER_GO_CONSUMPTIONS=0
```

Transaction `REPEATABLE READ READ ONLY` bornée, terminée par `ROLLBACK`. Seules les
métadonnées et compteurs sont sélectionnés ; le fichier exact est haché sans exposer ses
octets. L'origine fournisseur est corroborée par quatre sources `PROVIDER_SNAPSHOT`.
La validation humaine ne supprime pas l'avertissement de complétude. Le grant historique
est corroboré révoqué ; les zéros de l'état initial R1 ne sont pas recopiés comme état R2.

## 5. PKI conservée, profils publics vérifiés sans handshake

```text
PKI_IDENTITY_REFERENCE=WO048_RETAINED_NOMINAL_IDENTITY_FOR_WO046
PKI_PRIVATE_RECORD_COMMITMENT_SHA256=29c53ef4b27cd9782bc49f52b5594152f12d0ca7f96fd7209052ea90f6cc7475
PKI_RECHECK_AT_UTC=2026-09-05T08:46:32.4725772Z
PKI_RETAINED_QUALIFIED_STATE=PASS
PKI_PRIVATE_ACL=PASS
PKI_EXACT_CLIENT_IDENTITY_MATCH=YES
PKI_EXACT_RECEIVER_TRUST_IDENTITY_MATCH=YES
PKI_PUBLIC_PROFILES_AND_CURRENT_VALIDITY=PASS
PKI_VALID_FOR_NEXT_HOUR_AT_RECHECK=YES
CLIENT_KEY_PRESENCE_METADATA=CONFIRMED
CLIENT_PRIVATE_KEY_READ_USE_OR_EXPORT=NO
CERTIFICATE_STORE_MUTATION=NO
TLS_HANDSHAKE_PERFORMED=NO
CERTIFICATE_FINGERPRINT_GIT_POLICY=PRIVATE_EXTERNAL_RECORD_ONLY
```

Le registre privé et son module épinglé sont inchangés ; ACL, identité et profils des deux
certificats publics concordent. Les empreintes réelles, sujets, UUID de run privé, secrets et
chemins restent externes. Le futur go V2 reprendra le certificat client exact du registre.
Un recontrôle de validité restera obligatoire pour la fenêtre réelle future ; le contrôle
actuel n'étend pas la durée de vie des certificats.

## 6. Receiver R1 réutilisé par décision explicite

```text
RECEIVER_REUSE_OWNER_DECISION=AUTHORIZE_EXACT_RETAINED_VOLUME_WITH_AUDIT_PRESERVATION
RECEIVER_CONTAINER_NAME=betting-project-postgres-1
RECEIVER_CONTAINER_ID=d2d670ac601a2cf308c7883427e506f3d8e7e971ffdf9121901fc81416c83bc3
RECEIVER_COMPOSE_PROJECT=betting-project
RECEIVER_COMPOSE_SERVICE=postgres
RECEIVER_POSTGRES_IMAGE_ID=sha256:742f40ea20b9ff2ff31db5458d127452988a2164df9e17441e191f3b72252193
RECEIVER_VOLUME_NAME=betting-project_betting-postgres-data
RECEIVER_DATABASE=betting
RECEIVER_POSTGRES_BINDING=127.0.0.1:5433
RECEIVER_POSTGRES_CONTAINER_PORT=5432
RECEIVER_RESTART_POLICY=no
RECEIVER_RECHECK_AT_UTC=2026-09-05T08:44:41.303338Z
RECEIVER_SCHEMA=8
RECEIVER_MIGRATION_COUNT=8
RECEIVER_FAILED_MIGRATIONS=0
INITIAL_RECEIVER_IMPORTS=0
INITIAL_RECEIVER_PAYLOAD_ROWS=0
INITIAL_RECEIVER_AUDIT_ROWS=0
INITIAL_RECEIVER_TOMBSTONES=0
INITIAL_RECEIVER_OUTBOX_ROWS=0
RECEIVER_POSTCHECK_STATE=STOPPED_VOLUME_AND_AUDIT_PRESERVED
RECEIVER_RETENTION_DAYS=30
RECEIVER_FLYWAY_MIGRATION_REQUIRED=NO
RECEIVER_VOLUME_REPLACEMENT_AUTHORIZED=NO
RECEIVER_VOLUME_PURGE_AUTHORIZED=NO
```

L'identité est comparée au registre de possession R1 : ID, nom, image, création, labels,
montage exact et binding. Le label de go historique est conservé comme provenance R1 ; il
ne constitue aucune autorité de go R2. Le nouveau manifeste lie explicitement cette reprise.
Pas de relabellisation, de recréation Compose, de changement d'image ou de volume.

Pour le recontrôle autorisé, seul PostgreSQL receiver a été démarré temporairement, puis
interrogé par une transaction `REPEATABLE READ READ ONLY` et `ROLLBACK`, timeouts SQL 5 s,
lock 1 s et inactivité 10 s. Les compteurs ci-dessus sont frais, pas déduits du rapport R1.
L'instant retourné avec offset +02:00 est normalisé en UTC sans en changer la valeur.
Le conteneur a ensuite été arrêté proprement avec code 0 ; volume et labels conservés.
Cette opération produit les écritures internes normales d'un démarrage/arrêt PostgreSQL,
mais aucune écriture SQL métier, migration, purge, modification ou suppression d'audit.

Les autres bases de qualification INT-001 sont exclues. Le primaire Local Lab n'a été ni
arrêté ni remplacé. Aucun listener 8087/8444/5433 ne subsiste après le contrôle receiver.

## 7. Protocole de la tentative future, actuellement interdite

```text
EXECUTION_ACTOR=CODEX_LOCAL_UI
LOCAL_LAB_LISTENER=127.0.0.1:8087
RECEIVER_ORIGIN=https://127.0.0.1:8444
HTTP_METHOD=POST
HTTP_PATH=/api/imports/sofascore/j7-canonical-events
REQUEST_MEDIA_TYPE=application/vnd.betting-project.j7-canonical-event+json;version=1.0
ACK_MEDIA_TYPE=application/vnd.betting-project.j7-delivery-ack+json;version=1.0
PROTOCOL_VERSION=1.0
MTLS_CLIENT_AUTH=NEED
EXPECTED_ATTEMPT_NUMBER=1
MAXIMUM_DIRECT_IMPORT_CALLS=1
MAX_REQUEST_BYTES=5242880
MAX_ACK_BYTES=16384
BODY_POLICY=EXACT_ALREADY_HUMAN_VALIDATED_J7_BYTES
CONCURRENCY=1
AUTOMATIC_RETRY=0
REDIRECTS=NEVER
CONNECT_TIMEOUT_MAX_SECONDS=10
REQUEST_TIMEOUT_MAX_SECONDS=10
FUTURE_OWNER_GO_FORMAT=J7_PROVIDER_DERIVED_OWNER_GO_V2
FUTURE_GO_MAX_WINDOW_MINUTES=60
FUTURE_GO_WINDOW_POLICY=POSITIVE_UTC_HALF_OPEN_FUTURE_AT_REGISTRATION
FUTURE_GO_USE=ONE_TIME
```

Headers exacts : `Idempotency-Key` vaut `j7:` + EXPORT_ID + `:sha256:` + FILE_SHA256 ;
`X-J7-Protocol-Version` vaut `1.0` ; `X-J7-Export-Id`, `X-J7-File-SHA256` et
`X-J7-Data-SHA256` reprennent les valeurs ci-dessus ; `Content-Length` vaut `35663`.
La génération et la validation seules ne déclenchent rien. L'action distincte « Livrer »
et la confirmation exacte ci-dessous doivent rester fraîches et à usage unique lors de
l'exécution future :

```text
LIVRER J7 a8d40d57-98c5-4e01-8ecc-4f1f9b5feabc SHA256 d4aba249231bb9d575f40b5c23a629b938f685ee7d4cc479bfa7d846d61be3f6
```

## 8. Contrôles futurs, arrêt et conservation

Avant une exécution séparément autorisée, revérifier manifeste, JAR, module, export, PKI,
preuves, schémas, compteurs et ownership exact. Enregistrer le nouveau grant canonique
avant sa fenêtre ; toute dérive bloque la série. Ne jamais utiliser les anciens scripts
de lancement/révocation fautifs R1 comme un retry. Le justificatif corrigé WO-049 ne couvre
que le refus de configuration avant import ; il ne prouve pas, seul, la révocation SQL.

Le claim atomique consomme le go avec la tentative. Après consommation : pas de second POST,
même en cas d'incertitude. Le succès exige `201/IMPORTED`, ACK strict corrélé, hashes et
octets inbox byte-identiques, audit/outbox `J7_IMPORT_ACCEPTED` durables et ledger sender
`DELIVERED`. L'ACK ne signifie pas enrichissement terminé. Un premier `200/DUPLICATE`
inattendu impose une réconciliation, pas une réussite nominale déduite.

Refus HTTP, redirection, timeout, TLS, ACK hostile, limite de taille ou incohérence SQL :
classifier et arrêter sans retry. Une incertitude après claim reste
`UNKNOWN_RECONCILIATION_REQUIRED`. Aucun effacement de grant ou d'audit pour réarmer.

Après la future tentative autorisée, désactiver les flags de livraison et vider les
références go du processus, arrêter les applications possédées et le seul PostgreSQL
receiver exact, vérifier listeners et ledgers. Ne pas arrêter/purger le primaire. Conserver
volume, inbox, audit, outbox et tombstones ; toute purge de rétention reste séparément
administrative. Aucun changement PKI automatique. La rétention de 30 jours sera injectée
explicitement au futur lancement receiver, sans migration nécessaire.

```text
PROVIDER_NETWORK_AUTHORIZED=NO
REMOTE_RECEIVER_NETWORK_AUTHORIZED=NO
VPS_DEPLOYMENT_AUTHORIZED=NO
PRODUCTION_AUTHORIZED=NO
PRIMARY_DATABASE_PURGE=NO
LIVE_OR_SCHEDULED_OPERATION_AUTHORIZED=NO
WORK_ORDER_MOVE_TO_COMPLETED=NO
```

Aucun payload, ACK brut, certificat privé, secret, cookie, jeton ou artefact navigateur
n'est ajouté à Git. Ce manifeste ne démarre pas d'application, ne crée aucun UUID/fenêtre
de go et n'autorise ni POST d'import ni POST administratif.

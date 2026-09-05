# J9 — WO-046 — Manifeste de campagne J7 réelle locale Windows/Windows

## 1. Autorité limitée et gel

Le propriétaire autorise la création, le gel et le commit local de ce manifeste après recontrôle
des métadonnées de l'export et de l'identité mTLS, sans démarrage du receiver, sans owner-go et
sans POST. Cette autorisation est distincte de toute future autorisation d'exécution.

```text
MANIFEST_FORMAT=WO046_REAL_LOCAL_J7_CAMPAIGN_MANIFEST_V1
MANIFEST_REFERENCE=docs/validation/J9-WO046-J7-REAL-LOCAL-E2E-CAMPAIGN-MANIFEST-20260904.md
WORK_ORDER=WO-SS-20260904-046-j9-j7-real-local-e2e-campaign
CAMPAIGN_SERIES=WO046_REAL_LOCAL_R1
PREPARED_AT_UTC=2026-09-04T23:30:24Z
PREPARED_AT_EUROPE_PARIS=2026-09-05T01:30:24+02:00
PREPARATION_BASE_COMMIT=e3765e58db8d430ab480c67af790cecd44ccbb61
MANIFEST_CREATION_AUTHORIZED=YES
MANIFEST_FREEZE_POLICY=IMMUTABLE_FROM_FIRST_LOCAL_COMMIT
MANIFEST_COMMIT_POLICY=LOCAL_ONLY_NO_PUSH
CAMPAIGN_EXECUTION_AUTHORIZED=NO
RECEIVER_START_AUTHORIZED=NO
LOCAL_LAB_CAMPAIGN_START_AUTHORIZED=NO
OWNER_GO_CONSTRUCTION_AUTHORIZED=NO
OWNER_GO_CREATED=NO
OWNER_GO_GRANTED=NO
OWNER_GO_REGISTERED=NO
OWNER_GO_CONSUMED=NO
REAL_POST_AUTHORIZED=NO
```

Le nom de fichier conserve l'identifiant prescrit du 4 septembre ; la préparation intervient
le 5 septembre à Paris. Le premier commit local gèle ces octets UTF-8/LF sans BOM. Sa référence,
la taille et le SHA-256 du manifeste sont consignés à l'extérieur de ce fichier, dans le WO et
le compte rendu de livraison documentaire : aucun hash ou commit autoréférentiel n'est inventé.
Toute modification ultérieure impose un successeur explicite et invalide l'utilisation de ce
manifeste pour un nouveau go ; pas de réécriture silencieuse du fichier gelé.

## 2. Exécutables sélectionnés, sans lancement de campagne

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
RECEIVER_OWNER_VALIDATION=RECORDED
RECEIVER_QUALIFICATION=PASS
SENDER_QUALIFICATION=PASS
```

Les deux worktrees étaient propres avant préparation. Le JAR Local Lab est celui reconstruit
et exécuté pour la migration primaire documentée ; aucun fichier runtime ne diffère entre son
commit source et la base documentaire de ce manifeste. Le commit du manifeste ne remplace pas
ce commit source. Le JAR receiver a été reconstruit proprement à son commit exact sous Java
25.0.4, avec le wrapper Maven en cache, `--offline --batch-mode -DskipTests`, profils
`integration`, `native`, `nativeTest` désactivés, puis `clean package` : `BUILD SUCCESS`,
`11.684 s`, le `2026-09-04T23:28:19Z`. Son SHA est identique à l'artefact préexistant.

Cette reconstruction n'exécute aucun test ni receiver et ne contacte aucune base. L'absence de
`git.properties` dans le JAR receiver signifie que la liaison au commit est prouvée par la
construction propre observée et les hashes, pas par un champ Git embarqué. Avant la future
exécution, recontrôler les hashes des deux JAR et l'absence de dérive de leurs sources. Le code
owner-go conserve des commits mais ne prouve pas, à lui seul, l'identité du JAR réellement lancé.

## 3. Références qualifiées immuables

Les chemins ci-dessous sont relatifs au dépôt Local Lab, sauf INT-001 qui appartient au dépôt
Betting Project. Toutes les tailles et empreintes ont été recalculées lors de cette préparation.

| Preuve | Référence | Octets | SHA-256 |
|---|---|---:|---|
| Sender WO-035 | `docs/validation/J9-WO035-REAL-J7-DELIVERY-SENDER-QUALIFICATION-20260902.md` | 7706 | `1028761cbc68154da780d314935880bf17b803c3cf8a35b1386fd29ab28bae76` |
| E2E synthétique WO-036 R6 | `docs/validation/J9-WO036-J7-LOCAL-E2E-CAMPAIGN-RESUME-R6-20260903.md` | 14304 | `17a299cef826a4cc8da3fd4ff50a20eec99aee447cc4a0b69fc79914d4ab4280` |
| Frontière atomique WO-045 | `docs/validation/J9-WO045-PROVIDER-DERIVED-OWNER-GO-BOUNDARY-QUALIFICATION-20260904.md` | 11471 | `5146c18542f0376a60bac5d4a25b68ff3f2fec9d7da700b32ee0aac60e16d946` |
| Gouvernance V2 WO-047 | `docs/validation/J9-WO047-J7-DELIVERY-GOVERNANCE-SEPARATION-QUALIFICATION-20260904.md` | 12033 | `75b55109c0705a44026376d7f0bdadf76d979dcd4268c1430d5ed8008d693540` |
| Identités PKI WO-048 | `docs/validation/J9-WO048-LOCAL-MTLS-IDENTITY-PROVISIONING-QUALIFICATION-20260904.md` | 12844 | `c704961530020216bfbc644f2fa928357466eccf54ef4e3d80f5705a95ef109d` |
| Receiver INT-001 | `docs/reviews/INT-001-local-readiness.md` | 14300 | `0ca2efb8e9424c2a776b95f9605ca8e5c38a15766a31ff900e09c46161cbcba2` |
| Préparation primaire V30/V32 | `docs/validation/J9-WO046-PRIMARY-V30-V32-PREPARATION-20260905.md` | 11638 | `398d608a06cf228bbf299b608517328da73f95ecf43701243ddc39ebb6ee50bd` |

La préparation primaire couvre les restaurations qualifiées V30 puis V32, avec les limites de
comparaison explicites de son rapport. Le fichier J7 demeure externe au dump PostgreSQL. Les
preuves historiques ne sont ni réécrites ni présentées comme une nouvelle campagne réelle.

## 4. Audit officiel et base de gouvernance exacte

```text
PROVIDER_PERMISSION_AUDIT_REFERENCE=docs/validation/J9-WO046-OFFICIAL-PERMISSION-RECONCILIATION-20260904.md
PROVIDER_PERMISSION_AUDIT_SIZE_BYTES=7273
PROVIDER_PERMISSION_AUDIT_SHA256=707e0fd9b07dc0944225be80a590792e5e8ab0631dab964a465335f283ac1473
PROVIDER_PERMISSION_AUDIT_STATUS=NOT_EVIDENCED
J7_TRANSFER_GOVERNANCE_BASIS_REFERENCE=ADR-SS-003-optional-integration-topology.md
J7_TRANSFER_GOVERNANCE_BASIS_COMMIT=e1ec9936467dd570f7ed00c51227c8e7d5a35945
J7_TRANSFER_GOVERNANCE_BASIS_SIZE_BYTES=35280
J7_TRANSFER_GOVERNANCE_BASIS_SHA256=ded6a4da8a3161caae491f62919f4f5c3569c69c821be5a807772cc542cede3f
J7_TRANSFER_GOVERNANCE_BASIS_STATUS=ADR_ACCEPTED_NO_EXECUTION_AUTHORITY
OFFICIAL_PERMISSION_REVIEW_RESULT=NEGATIVE_AUDIT_ONLY_NON_BLOCKING_FOR_LOCAL_J7_TRANSFER
EVIDENCED_INCOMPATIBLE_EFFECT=BLOCK
J3_J4_J5_PERMISSION_GATE_CHANGE=NO
```

L'empreinte ADR vise les octets Git exacts de la proposition acceptée au commit indiqué, lus
sans conversion des fins de ligne ; elle ne vise pas la copie courante enrichie de la décision.
Aucune permission officielle SofaScore positive n'est affirmée. La décision v0.2 sépare le seul
transfert local J7 de l'acquisition fournisseur, sans autoriser une exécution ni changer J3/J4/J5.

## 5. Export unique recontrôlé

```text
EXPORT_RECHECK_AT_UTC=2026-09-04T23:27:23.953071Z
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
INVALID_SOURCE_METADATA_COUNT=0
WARNING=MISSING_COMPONENT:EVENT_DETAILS
DECISION_INTENT_MATCH=YES
FILE_SIZE_AND_HASH_MATCH=YES
EXACT_SELECTED_FILE_COUNT=1
REPLACEMENT_EXPORT_ALLOWED=NO
NEW_PROVIDER_ACQUISITION_ALLOWED=NO
```

Le recontrôle SQL utilise une transaction `REPEATABLE READ READ ONLY`, `statement_timeout=5s`,
`lock_timeout=1s`, `idle_in_transaction_session_timeout=10s` et `ROLLBACK`, via le primaire exact.
Seules les métadonnées de l'export et les compteurs/gardes sont lus ; aucun payload fournisseur
n'est sélectionné ou affiché. Le SHA du fichier exact est recalculé localement sans publication
de ses octets. Quatre sources sont fournisseur et la source manquante a `sourceKind=null`.
La validation humaine ne supprime pas l'avertissement de complétude.

## 6. Identité mTLS exacte liée au registre privé

```text
PKI_IDENTITY_REFERENCE=WO048_RETAINED_NOMINAL_IDENTITY_FOR_WO046
PKI_PRIVATE_RECORD_COMMITMENT_SHA256=29c53ef4b27cd9782bc49f52b5594152f12d0ca7f96fd7209052ea90f6cc7475
PKI_RECHECK_AT_UTC=2026-09-04T23:25:42.8257148Z
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

Le registre externe contient aussi des secrets de stores : seule son empreinte est publique.
Le recontrôle utilise le module exact épinglé par ce registre et les seuls certificats publics,
en lecture seule. Un premier appel de diagnostic a été refusé car il utilisait les noms WO
abrégés ; la forme complète qualifiée a été rétablie dans le contrôle. La copie du module dans
le worktree courant diffère en octets uniquement par ses fins de ligne ; le module original
conservé correspond à son hash épinglé et a été utilisé pour la vérification finale. Aucun
provisionnement, changement de certificat ou assouplissement du profil n'a été réalisé.

Les sujets, numéros de série, thumbprints, hashes DER réels, UUID du run privé, chemins et secrets
ne figurent pas dans Git. Le futur owner-go V2 externe devra reprendre le certificat client exact
de ce registre. Sa validité et celle du serveur devront être revérifiées avant tout usage ; la
validité actuelle n'est pas une extension automatique de leur durée de vie.

## 7. Inventaire initial et cible receiver future

| Ressource | État observé à la préparation | Traitement prévu après autorisation d'exécution |
|---|---|---|
| Primaire Local Lab | `betting-sofascore-local-lab-postgres`, sain, déjà actif ; PostgreSQL 18.4, `127.0.0.1:5432` | Conserver le conteneur et ses données ; accès à la base primaire configurée exacte, sans remplacement ni purge |
| Application Local Lab | Aucun listener `8087` ; aucune application démarrée pour cette préparation | Démarrage manuel borné du JAR épinglé sur `127.0.0.1:8087`, fournisseur désactivé |
| Application receiver | Aucun listener `8444`, aucun receiver démarré | JAR épinglé, profil `control-api`, `https://127.0.0.1:8444`, mTLS obligatoire |
| PostgreSQL receiver canonique | Aucun conteneur Compose de projet `betting-project`, aucun volume de ce projet ; port `5433` libre | Cible neuve issue du Compose canonique, distincte de toutes les anciennes qualifications |
| Anciennes qualifications INT-001 | Trois conteneurs Q1/Q2 arrêtés, aucun port publié actif | Exclus de WO-046 : ni réutilisation, ni démarrage, ni suppression |
| Ressources de campagne possédées | Aucun processus applicatif, conteneur ou volume créé par cette préparation | Propriété exacte à enregistrer avant les effets ultérieurs ; aucune propriété déduite du seul PID ou d'un préfixe |

```text
PRIMARY_CONTAINER_ID=e7b218117df39b7cbfbffb3b1223647568f149912a84592e506b5690f9686c1f
PRIMARY_DATABASE_SCHEMA=32
PRIMARY_FAILED_MIGRATIONS=0
PRIMARY_CONNECTOR_NETWORK_ENABLED=NO
PRIMARY_CONNECTOR_CIRCUIT_STATE=LOCKED
INITIAL_J7_DELIVERIES=0
INITIAL_J7_DELIVERY_ATTEMPTS=0
INITIAL_OWNER_GO_GRANTS=0
INITIAL_OWNER_GO_REVOCATIONS=0
INITIAL_OWNER_GO_CONSUMPTIONS=0
PLANNED_RECEIVER_COMPOSE_PROJECT=betting-project
PLANNED_RECEIVER_COMPOSE_SERVICE=postgres
PLANNED_RECEIVER_DATABASE=betting
PLANNED_RECEIVER_POSTGRES_BINDING=127.0.0.1:5433
PLANNED_RECEIVER_POSTGRES_CONTAINER_PORT=5432
PLANNED_RECEIVER_POSTGRES_IMAGE=postgres:17-alpine
PLANNED_RECEIVER_POSTGRES_IMAGE_ID=sha256:742f40ea20b9ff2ff31db5458d127452988a2164df9e17441e191f3b72252193
PLANNED_RECEIVER_VOLUME_LOGICAL_NAME=betting-postgres-data
PLANNED_RECEIVER_REQUIRED_SCHEMA=8
PLANNED_RECEIVER_RETENTION_DAYS=30
RECEIVER_RESOURCE_CREATION_AUTHORIZED=NO
RECEIVER_DATABASE_START_AUTHORIZED=NO
RECEIVER_DATABASE_ACCESS_PERFORMED=NO
```

La base future `betting` et le volume logique suivent le Compose et l'exemple public du receiver.
Ils constituent une sélection documentaire planifiée, pas une base déjà créée ou qualifiée en
exécution réelle. Le Compose ne fixe pas `container_name` : aucun identifiant Docker inexistant
n'est inventé. Après autorisation distincte, création bornée au projet/service ci-dessus, avec
image locale épinglée et sans pull ; l'ID effectif, les labels, mounts, bindings et l'identité de
la base seront vérifiés et enregistrés dans la preuve privée avant son usage. Toute collision,
ressource préexistante inattendue ou donnée historique provoque un arrêt, sans adoption tacite.
L'initialisation Flyway receiver V001–V008 reste future et soumise à cette autorisation.
La rétention receiver de 30 jours devra être injectée et vérifiée explicitement au lancement ;
elle ne sera pas déduite de la seule valeur par défaut du code.

## 8. Protocole et limites de la future tentative

```text
EXECUTION_ACTOR=CODEX_LOCAL_UI
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

Le POST futur doit porter les identités et hashes ci-dessus dans les headers du contrat existant,
la clé idempotente déterministe J7 et le `Content-Length` exact. Il ne déclenche aucune acquisition
fournisseur. La génération et la validation seules n'envoient rien : l'action « Livrer » et sa
confirmation exacte, fraîche et à usage unique restent obligatoires.

| En-tête de la future requête | Valeur exacte |
|---|---|
| `Idempotency-Key` | `j7:a8d40d57-98c5-4e01-8ecc-4f1f9b5feabc:sha256:d4aba249231bb9d575f40b5c23a629b938f685ee7d4cc479bfa7d846d61be3f6` |
| `X-J7-Protocol-Version` | `1.0` |
| `X-J7-Export-Id` | `a8d40d57-98c5-4e01-8ecc-4f1f9b5feabc` |
| `X-J7-File-SHA256` | `d4aba249231bb9d575f40b5c23a629b938f685ee7d4cc479bfa7d846d61be3f6` |
| `X-J7-Data-SHA256` | `f95ae31e711a9433d6d06745d37f2535143266aa97c7d151fd26bea88ef03ac6` |
| `Content-Length` | `35663` |

Aucun GO_ID, fenêtre effective, bloc canonique ou hash de décision n'est construit ici. Après
ce commit gelé, une décision distincte pourra autoriser la préparation du owner-go V2 externe,
puis sa confirmation et l'unique exécution. Aucun ancien go V1/V2 n'est réutilisable.

## 9. Arrêt, preuve de réception et conservation

Avant la future activation : vérifier les hashes de ce manifeste, des deux JAR, des références,
de l'export et du registre PKI, la fenêtre/certificats, les deux bases exactes, les listeners
exclusifs, les compteurs initiaux et toutes les autorisations. Toute dérive bloque la série.
Le grant exact doit être enregistré avant la fenêtre ; le claim atomique consommera ce grant
avec la tentative. Après consommation, aucune erreur ni incertitude ne permet un second POST.

Le succès réel exige l'ACK strict corrélé et les preuves SQL concordantes : premier import
`201/IMPORTED`, reçu durable, octets en inbox byte-identiques, hashes file/data et exportId
conformes, audit et événement outbox `J7_IMPORT_ACCEPTED` persistés, ledger sender `DELIVERED`.
Un `200/DUPLICATE` lors de cette première tentative sans import antérieur attendu impose une
réconciliation, pas une réussite nominale déduite. L'ACK n'affirme aucun enrichissement terminé.

En cas de collision `409`, refus `4xx`, redirection, `5xx`, timeout, TLS invalide, ACK hostile,
taille excessive ou incohérence de persistance : classifier avec les états existants, conserver
les seules preuves expurgées et arrêter sans retry. Toute incertitude après claim exige
`UNKNOWN_RECONCILIATION_REQUIRED` et une réconciliation manuelle, jamais une remise à zéro du go.

Après toute tentative future : désactiver les flags de livraison et vider les références go du
processus, fermer les applications possédées, vérifier l'absence de listeners/workers et contrôler
les deux ledgers. Ne jamais arrêter ou purger le primaire préexistant sous couvert du cleanup.
Le receiver et sa base nouvellement créés pourront être arrêtés sous autorité exacte ; **conserver
le volume, l'inbox, l'outbox, l'audit et les tombstones**. Aucune suppression du J7 ou de la base ne
découle de la fin de campagne. La rétention canonique receiver est de 30 jours ; toute purge
ultérieure reste administrative, bornée et soumise aux protections qualifiées.

Les certificats et artefacts privés restent liés à leur autorité WO-048 ; aucun retrait ou
renouvellement automatique n'est autorisé ici. Aucun payload, ACK brut, certificat privé, secret,
cookie, jeton, HAR, trace, vidéo, capture, téléchargement ou storageState n'est conservé en Git.
Le rapport final futur consignera seulement statuts, compteurs, dates, hashes et cleanup autorisés.

```text
PROVIDER_NETWORK_AUTHORIZED=NO
REMOTE_RECEIVER_NETWORK_AUTHORIZED=NO
VPS_DEPLOYMENT_AUTHORIZED=NO
PRODUCTION_AUTHORIZED=NO
PRIMARY_DATABASE_PURGE=NO
LIVE_OR_SCHEDULED_OPERATION_AUTHORIZED=NO
WORK_ORDER_MOVE_TO_COMPLETED=NO
```

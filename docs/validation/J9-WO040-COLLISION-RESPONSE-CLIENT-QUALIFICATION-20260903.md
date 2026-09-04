# J9 — Qualification WO-040 de la capture de réponse HTTP de collision

## 1. Identité de la preuve

```text
REPORT_ID=J9-WO040-COLLISION-RESPONSE-CLIENT-QUALIFICATION-20260903
WORK_ORDER=WO-SS-20260903-040-j9-wo036-collision-response-client-qualification
BASE_COMMIT=33d2ae0bd5c2c8daae4cd708b0f12efaf9cd029d
OPENING_COMMIT=897f617
PRIMARY_IMPLEMENTATION_COMMIT=f6e6a804f507bad48943534d4179bfcc90437766
RESPONSE_LINE_COMPATIBILITY_COMMIT=80cd5a33b19b2da48f0ab0821ef6fdfbd2623ba4
READINESS_RECORDED_AT_UTC=2026-09-03T18:51:14.6957371Z
READINESS_RECORDED_AT_EUROPE_PARIS=2026-09-03T20:51:14.6957371+02:00
QUALIFICATION_RESULT=PASS_LOCAL_FAIL_CLOSED
WORK_ORDER_STATUS=READY_FOR_OWNER_REVIEW
```

Ce rapport ne contient pas sa propre empreinte. Son SHA-256 est calculé après finalisation puis
consigné dans le Work Order, le README, le changelog et le bloc soumis au propriétaire.

## 2. Autorité et limites

Le propriétaire a autorisé la correction du seul outillage de campagne WO-036, sa qualification
hors ligne et une qualification synthétique contre le receiver INT-001 lié au loopback. Il a
ensuite autorisé explicitement l'exécution hôte strictement bornée nécessaire à cette preuve.

```text
WO036_CAMPAIGN_TOOLING_ONLY=YES
LOCAL_LAB_JAVA_SENDER_CHANGED=NO
RECEIVER_RUNTIME_CHANGED=NO
PROTOCOL_RELAXED=NO
AUTOMATIC_RETRY=0
NETWORK_SCOPE=127.0.0.1_ONLY
PROVIDER_CALLS=0
PROVIDER_DERIVED_PAYLOADS=0
REMOTE_RECEIVER_CALLS=0
PRIMARY_DATABASE_TOUCH=NO
PRIMARY_DATABASE_PURGE=NO
WO036_REPLAY_OR_RESUME=NO
```

La qualification WO-040 n'est ni le run R5 de WO-036, ni un rejeu de R4. Elle ne rend
réutilisable aucune claim consommée et n'autorise ni PR ou validation INT-001, ni réseau
fournisseur ou distant, ni VPS ou production.

## 3. Point de départ factuel R4

Le run R4 de WO-036 avait qualifié `201/IMPORTED`, puis `200/DUPLICATE`. Son troisième et dernier
POST avait créé exactement un audit durable `DIVERGENCE_REJECTED/EXPORT_ID_DIVERGENCE`, sans
second receipt, payload ou outbox. Le client de sonde n'avait cependant restitué ni statut HTTP
ni code ProblemDetail sûr et la claim avait été consommée sous `FAILED_OR_UNKNOWN_CONSUMED`.

L'achèvement de cette claim `539.19 ms` après son acquisition excluait le timeout de dix secondes.
La réponse brute R4 n'ayant volontairement pas été conservée, ni ses octets ni son framing ne sont
reconstruits dans le présent rapport.

```text
R4_RAW_RESPONSE_AVAILABLE=NO
R4_CLIENT_HTTP_STATUS_OBSERVED=NO
R4_DURABLE_DIVERGENCE_EFFECT_OBSERVED=YES
R4_CLAIM_REPLAYED=NO
```

## 4. Cause reproduite hors ligne et première correction

Le lecteur antérieur accumulait toute la réponse et exigeait un `Read()` retournant zéro avant
d'analyser les en-têtes et le corps. Une réponse HTTP/1.1 à longueur fixe pourtant complète était
donc perdue si le transport restait ouvert ou devenait fautif après ses derniers octets.

La reproduction déterministe fournit une réponse synthétique complète `409` avec
`Content-Length` et `J7_IMPORT_CONFLICT`, puis lève une erreur au `Read()` suivant. Avant
correction, ce `Read()` surnuméraire faisait perdre la réponse. Le commit
`f6e6a804f507bad48943534d4179bfcc90437766` sépare la complétude du message de la terminaison du
transport :

- une réponse `Content-Length` est restituée dès que le nombre exact d'octets est reçu ;
- une réponse chunked est restituée uniquement après son chunk terminal valide ;
- une réponse close-delimited exige toujours un EOF propre ;
- doubles framing, troncatures, longueurs hostiles, extensions ou trailers chunked, corps de plus
  de 16 Kio et octets déjà surnuméraires sont refusés ;
- le maximum du corps reste `16384` octets et celui du message sur le fil `49152` octets ;
- les buffers sont effacés et aucun octet brut n'est ajouté à la claim, aux logs ou à Git.

```text
ROOT_CAUSE_CLASS=HTTP_MESSAGE_COMPLETION_WAS_INCORRECTLY_COUPLED_TO_TRANSPORT_EOF
OFFLINE_REPRODUCTION=PASS_DETERMINISTIC
R4_COMPATIBILITY_CONFIDENCE=HIGH_NOT_RETROACTIVE_WIRE_OBSERVATION
```

## 5. Compatibilité bornée de la ligne de statut

Une première qualification loopback après cette correction a franchi mTLS et toutes les
validations receiver, puis persisté l'import nominal dans une base isolée. Le lecteur s'est arrêté
sur `HTTP_STATUS_LINE` avant de restituer ce `201`. Aucun POST de collision n'a été émis dans cet
essai et toutes ses ressources ont été supprimées avant la correction suivante.

La seule forme supplémentaire ensuite admise par le commit
`80cd5a33b19b2da48f0ab0821ef6fdfbd2623ba4` est une ligne HTTP/1.1 composée du code, d'un unique
séparateur espace et d'une reason phrase vide. Le receiver INT-001/Tomcat emploie cette forme. Le
parseur accepte donc désormais exactement :

```text
HTTP/1.1 <code>
HTTP/1.1 <code><SP>
HTTP/1.1 <code><SP><reason-phrase-visible-et-bornée>
```

Une version différente, un code hors plage, deux séparateurs, un contrôle, un espace initial dans
la reason phrase ou une ligne surdimensionnée reste refusé fail-closed. Ce changement n'assouplit
ni le média type, ni le ProblemDetail, ni la corrélation, ni le contrat receiver.

## 6. Préparations hôte fail-closed

Les préparations non qualifiantes sont restées distinctes et nettoyées :

1. le premier préflight a détecté avant création des ressources que le volume PostgreSQL primaire
   réel était monté sur `/var/lib/postgresql`, et non sur la destination attendue par le harnais ;
   aucun POST n'a été émis ;
2. la première route de certificat client via PFX éphémère a été refusée par SChannel avec
   `SEC_E_UNKNOWN_CREDENTIALS` avant toute écriture HTTP ; aucun POST n'a été émis ;
3. après passage à une clé CNG non exportable dans le magasin utilisateur Windows, l'essai décrit
   à la section 5 a produit un import nominal puis s'est arrêté avant le POST de collision ; il
   n'a pas été continué après correction.

Chaque invocation était une préparation ou qualification synthétique neuve, commandée par
l'opérateur. Il n'existe ni boucle, ni retry automatique, ni réutilisation d'une claim ou d'un
état isolé après échec.

## 7. Provenance du receiver inchangé

La qualification finale utilise le receiver INT-001 sans le modifier :

```text
INT001_BRANCH=codex/int-001-j7-receiver
INT001_COMMIT=b6a093ab4d3358f23a59b65b68a3eb720494bcba
INT001_JAR_SHA256=d41f74e984fa2455e535623fc570b30f91fd2b03d7742ada81c7c948f67c0c7f
INT001_JAR_SIZE_BYTES=26777553
POSTGRES_IMAGE=postgres:17-alpine
POSTGRES_IMAGE_ID=sha256:742f40ea20b9ff2ff31db5458d127452988a2164df9e17441e191f3b72252193
```

Le receiver était lié uniquement à `127.0.0.1:8444`, sa base isolée uniquement à
`127.0.0.1:5433`. La PKI mTLS, la racine d'exécution et les journaux sont restés hors dépôt.
Le certificat client utilisait une clé CNG non exportable créée pour le run, puis supprimée du
magasin `CurrentUser/My`. Aucun ancrage serveur n'a été ajouté au magasin de confiance Windows :
le certificat serveur était épinglé par le harnais privé.

## 8. Qualification loopback finale

Le run qualifiant entièrement neuf a débuté le `2026-09-03T18:33:00.5193608Z`, soit
`2026-09-03T20:33:00.5193608+02:00` en Europe/Paris, et s'est terminé le
`2026-09-03T18:33:20.4330487Z`, soit `2026-09-03T20:33:20.4330487+02:00`.

Il a utilisé un corps J7 entièrement synthétique de `3149` octets et exactement deux POST vers la
route d'import : un import nominal, puis une collision par contenu divergent. Aucune donnée
dérivée de SofaScore n'a été chargée ou transmise.

| Observation client expurgée | Résultat |
|---|---|
| POST nominal | `201` |
| framing de la réponse nominale | `CONTENT_LENGTH` |
| POST de collision | `409` |
| code ProblemDetail sûr | `J7_IMPORT_CONFLICT` |
| framing de la réponse de collision | `CHUNKED` |
| POST vers la route d'import | `2` |
| retry automatique | `0` |

```text
SYNTHETIC_BASELINE_FILE_SHA256=1edf83be7f0c8bd1a486b2ef78398a15fe96335fe99fe3f80978aa85e0ae01f3
SYNTHETIC_MUTATED_FILE_SHA256=1b1bfb2f3e7a9451b9f0fb1b89846bb118e0326d5a868074c9fb7ee31b18969e
SYNTHETIC_DATA_SHA256=11fde26248127460fd2b8deb02b1d1358fa867098ae3cbf4ee515d1199acaf7b
RAW_REQUEST_PERSISTED=NO
RAW_RESPONSE_PERSISTED=NO
RAW_ACK_PERSISTED=NO
```

## 9. Effets durables contrôlés

Après les deux POST, les seules métadonnées nécessaires ont été lues dans la base isolée :

```text
RECEIPT_COUNT=1
PAYLOAD_COUNT=1
OUTBOX_COUNT=1
IMPORTED_AUDIT_COUNT=1
DIVERGENCE_AUDIT_COUNT=1
AUDIT_TOTAL_COUNT=2
DIVERGENCE_REASON=EXPORT_ID_DIVERGENCE
```

Ces compteurs établissent qu'un seul import et un seul événement d'outbox sont durables. Le POST
divergent produit l'unique audit terminal attendu sans créer un second receipt, payload ou outbox.
La réponse `409/J7_IMPORT_CONFLICT` est cette fois observée côté client, sous framing chunked
complet, et corrélée à cet effet durable expurgé.

## 10. Tests et contrôles

| Porte | Tests | Échecs | Erreurs | Ignorés | Résultat |
|---|---:|---:|---:|---:|---|
| Pester outils de campagne WO-036/WO-040 | 53 | 0 | 0 | 0 | `PASS` |
| Pester campagne et infrastructure | 68 | 0 | 0 | 0 | `PASS` |
| Surefire, `clean verify` | 1136 | 0 | 0 | 5 | `PASS` |
| Failsafe, `clean verify` | 89 | 0 | 0 | 0 | `PASS` |
| Surefire, profil `integration-tests` | 1136 | 0 | 0 | 5 | `PASS` |
| Failsafe, profil `integration-tests` | 89 | 0 | 0 | 0 | `PASS` |

Les cas Pester couvrent notamment les réponses fixed-length et chunked complètes sans EOF, les
lectures fragmentées, le close-delimited avec EOF, les framing tronqués, ambigus ou surnuméraires,
les chunks hostiles, les en-têtes dupliqués, les codes sûrs, la ligne sans reason phrase, son
unique séparateur vide et le refus de séparateurs supplémentaires.

```text
MVNW_CLEAN_VERIFY=PASS
MVNW_INTEGRATION_TESTS_VERIFY=PASS
DOCKER_COMPOSE_CONFIG_QUIET=PASS_WITH_EXISTING_PARENT_ENV_FILE
JAVA_VERSION=25.0.4
SERVER_ADDRESS_DEFAULT=127.0.0.1
PROVIDER_DEFAULT_FLAGS=BLOCKED
REAL_RECEIVER_DEFAULT_FLAGS=BLOCKED
```

Les suites Maven n'effectuent que leurs appels locaux et leurs bases Testcontainers isolées. La
première invocation Maven confinée par la sandbox n'a pas pu résoudre le parent Spring Boot et
s'est arrêtée avant l'évaluation du projet ; les deux exécutions hôte autorisées ci-dessus sont les
résultats qualifiants.

## 11. Nettoyage et intégrité de l'hôte

Après lecture des compteurs expurgés, l'application receiver a été arrêtée gracieusement et les
ressources exactes de qualification ont été supprimées. Le postflight indépendant donne :

```text
WO040_PROCESS_RESIDUAL_COUNT=0
WO040_CONTAINER_RESIDUAL_COUNT=0
WO040_VOLUME_RESIDUAL_COUNT=0
WO040_CERTIFICATE_STORE_RESIDUAL_COUNT=0
WO040_LISTENER_5433_RESIDUAL_COUNT=0
WO040_LISTENER_8444_RESIDUAL_COUNT=0
WO040_PRIVATE_RUN_ROOT_REMOVED=YES
WO040_RECEIVER_LOG_SIZE_BYTES=7734
WO040_RECEIVER_LOG_FORBIDDEN_OCCURRENCES=0
```

Le PostgreSQL primaire exact est resté inchangé :

```text
PRIMARY_CONTAINER_NAME=betting-sofascore-local-lab-postgres
PRIMARY_CONTAINER_ID=e7b218117df39b7cbfbffb3b1223647568f149912a84592e506b5690f9686c1f
PRIMARY_CONTAINER_STATE=running
PRIMARY_CONTAINER_HEALTH=healthy
PRIMARY_LISTENER=127.0.0.1:5432
PRIMARY_DATABASE_TOUCHED=NO
PRIMARY_DATABASE_PURGED=NO
```

Aucun HAR, trace, vidéo, capture, téléchargement, `storageState`, certificat privé, payload,
réponse brute, cookie, jeton ou secret n'est conservé.

## 12. Fichiers et empreintes du correctif

Le diff runtime reste limité aux deux fichiers autorisés :

```text
scripts/wo036/WO036-CampaignTools.psm1
SHA256=4296d0a6ed2d69410ca2d60e1c4f8dfcab116c6d8d55001be1bc82d25412c214

scripts/Tests/WO036CampaignTools.Tests.ps1
SHA256=a50cf6582ef0b389d91e9ed439cae454bbd3ede9060c0a11ce6392123a357518
```

Aucun fichier Java, migration, contrat, endpoint, configuration runtime du sender ou code du
receiver n'est modifié par WO-040.

## 13. Conclusion et portes maintenues

WO-040 atteint `PASS_LOCAL_FAIL_CLOSED`. Le lecteur rend une réponse HTTP/1.1 dès que son framing
exact prouve la complétude du message, sans dépendre de l'EOF ; la qualification loopback observe
le `201` fixed-length et le `409/J7_IMPORT_CONFLICT` chunked attendus, avec les effets durables
exacts et zéro résidu.

```text
WO040_QUALIFICATION_RESULT=PASS_LOCAL_FAIL_CLOSED
WO040_LOCAL_READINESS=PASS
WO040_OWNER_REVIEW_REQUIRED=YES
WO040_WORK_ORDER_MOVE_TO_COMPLETED=NO
WO036_STATUS=STOPPED_AFTER_CONSUMED_R4_COLLISION_RESPONSE_QUALIFICATION_FAILURE
WO036_RESUME_AUTHORIZED=NO
WO036_NEXT_FRESH_RUN=R5
WO036_R5_MANIFEST=REQUIRED_NEW_AND_FROZEN_BEFORE_FIRST_POST
J9_OFFICIAL_PERMISSION_STATUS=NOT_EVIDENCED
J9_PROVIDER_DERIVED_REAL_DELIVERY_AUTHORIZED=NO
J9_PROVIDER_NETWORK_AUTHORIZED=NO
REAL_RECEIVER_NETWORK_AUTHORIZED=NO
REMOTE_RECEIVER_NETWORK_AUTHORIZED=NO
LIVE_DELIVERY_AUTHORIZED=NO
J9_VPS_DEPLOYMENT_AUTHORIZED=NO
J9_PRODUCTION_AUTHORIZED=NO
```

Une validation propriétaire peut clôturer WO-040 uniquement. Toute reprise de WO-036 exige encore
une décision propriétaire séparée, puis un run R5 entièrement neuf et un manifeste R5 gelé avant
son premier POST.

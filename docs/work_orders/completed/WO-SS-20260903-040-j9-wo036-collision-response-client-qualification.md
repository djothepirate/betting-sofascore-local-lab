# WO-SS-20260903-040 — Qualification de la capture de réponse de collision WO-036

- **Statut :** `VALIDATED`
- **Jalon :** après J9 — correction du harnais avant une éventuelle reprise R5 de WO-036
- **Ouvert le :** 2026-09-03
- **Ouverture UTC :** `2026-09-03T17:38:14.3891321Z`
- **Ouverture Europe/Paris :** `2026-09-03T19:38:14.3891321+02:00`
- **Branche :** `codex/j9-wo040-collision-response-client-qualification`
- **Worktree :** `.tmp/w40`
- **Base locale d’ouverture vérifiée :** `33d2ae0bd5c2c8daae4cd708b0f12efaf9cd029d`
- **Commit principal du lecteur borné :** `f6e6a804f507bad48943534d4179bfcc90437766`
- **Commit de compatibilité de ligne de statut :**
  `80cd5a33b19b2da48f0ab0821ef6fdfbd2623ba4`
- **Readiness consignée en UTC :** `2026-09-03T18:51:14.6957371Z`
- **Readiness consignée en Europe/Paris :** `2026-09-03T20:51:14.6957371+02:00`
- **Validation propriétaire UTC :** `2026-09-03T18:58:26.5005063Z`
- **Validation propriétaire Europe/Paris :** `2026-09-03T20:58:26.5005063+02:00`
- **Work Order bloqué :** `WO-SS-20260902-036-j9-j7-local-e2e-qualification`
- **Rapport du constat R4 :**
  `docs/validation/J9-WO036-J7-LOCAL-E2E-CAMPAIGN-RESUME-R4-STOP-20260903.md`
- **SHA-256 du rapport R4 :**
  `16e9f85e12109f2709146312410bbe2a5e040c4f176c2d5596746aa2b70076b5`
- **Rapport de qualification WO-040 :**
  `docs/validation/J9-WO040-COLLISION-RESPONSE-CLIENT-QUALIFICATION-20260903.md`
- **SHA-256 du rapport WO-040 :**
  `2eb13aa1825d21eb5c40e97ffebf77246099d781f626591132897c4d128e9aee`
- **Permission officielle :** `NOT_EVIDENCED`

## 1. Autorisation propriétaire

Le propriétaire autorise l’ouverture, le diagnostic, la correction et la qualification du seul
client de sonde appartenant au harnais WO-036. Le bloc reçu est consigné sans élargissement :

```text
J9_WO040_OWNER_DECISION=AUTHORIZE_IMPLEMENTATION
J9_WO040_OPEN_WORK_ORDER_AUTHORIZED=YES
J9_WO040_WORK_ORDER=WO-SS-20260903-040-j9-wo036-collision-response-client-qualification
J9_WO040_SCOPE=DIAGNOSE_CORRECT_AND_QUALIFY_WO036_COLLISION_PROBE_HTTP_RESPONSE_CAPTURE_AFTER_DURABLE_DIVERGENCE_EFFECT

J9_WO040_ALLOWED_CHANGE=WO036_CAMPAIGN_TOOLING_ONLY
J9_WO040_LOCAL_LAB_JAVA_SENDER_CHANGE_AUTHORIZED=NO
J9_WO040_RECEIVER_RUNTIME_CHANGE_AUTHORIZED=NO
J9_WO040_PROTOCOL_RELAXATION_AUTHORIZED=NO
J9_WO040_AUTOMATIC_RETRY_AUTHORIZED=NO

J9_WO040_OFFLINE_RESPONSE_CAPTURE_QUALIFICATION_AUTHORIZED=YES
J9_WO040_LOCAL_INT001_LOOPBACK_QUALIFICATION_AUTHORIZED=YES_SYNTHETIC_ONLY
J9_WO040_NETWORK_SCOPE=127.0.0.1_ONLY
J9_WO040_PROVIDER_CALLS_AUTHORIZED=NO
J9_WO040_PROVIDER_DERIVED_PAYLOADS_AUTHORIZED=NO
J9_WO040_REMOTE_RECEIVER_NETWORK_AUTHORIZED=NO
J9_WO040_VPS_DEPLOYMENT_AUTHORIZED=NO
J9_WO040_PRODUCTION_AUTHORIZED=NO
J9_WO040_PRIMARY_DATABASE_TOUCH_AUTHORIZED=NO
J9_WO040_PRIMARY_DATABASE_PURGE_AUTHORIZED=NO
J9_WO040_INT001_PULL_REQUEST_AUTHORIZED=NO
J9_WO040_INT001_VALIDATION_AUTHORIZED=NO

J9_WO036_STATUS=STOPPED_AFTER_CONSUMED_R4_COLLISION_RESPONSE_QUALIFICATION_FAILURE
J9_WO036_RESUME_AFTER_WO040_VALIDATION=REQUIRES_SEPARATE_OWNER_DECISION
J9_WO036_NEXT_FRESH_RUN=R5
J9_WO036_R5_MANIFEST=REQUIRED_NEW_AND_FROZEN_BEFORE_FIRST_POST
J9_WO036_WORK_ORDER_MOVE_TO_COMPLETED=NO

J9_OFFICIAL_PERMISSION_STATUS=NOT_EVIDENCED
PROVIDER_DERIVED_REAL_DELIVERY_AUTHORIZED=NO
PROVIDER_NETWORK_AUTHORIZED=NO
REAL_RECEIVER_NETWORK_AUTHORIZED=NO
REMOTE_RECEIVER_NETWORK_AUTHORIZED=NO
LIVE_DELIVERY_AUTHORIZED=NO
VPS_DEPLOYMENT_AUTHORIZED=NO
PRODUCTION_AUTHORIZED=NO
```

Cette autorisation ne reprend pas WO-036 et ne rend pas réutilisable la claim R4 consommée. Toute
qualification loopback de WO-040 doit créer une preuve synthétique autonome, bornée et nettoyée ;
elle ne constitue ni R5 ni une nouvelle campagne E2E complète.

## 2. Constat factuel R4

R4 a qualifié `201/IMPORTED`, puis `200/DUPLICATE`. Son troisième et dernier appel a atteint la
branche métier de divergence du receiver et persisté exactement un audit
`DIVERGENCE_REJECTED/EXPORT_ID_DIVERGENCE`, sans second receipt, payload ou outbox. Le client de
sonde n’a cependant conservé ni statut HTTP ni code sûr avant de classer la claim
`FAILED_OR_UNKNOWN_CONSUMED`.

La claim a été achevée `539.19 ms` après son acquisition. Cette mesure exclut un timeout de dix
secondes. Le contrat receiver mappe la branche durable observée vers HTTP `409`, mais ce statut
n’est pas présenté comme une observation client de R4. La cause de non-capture reste
`NOT_ESTABLISHED` à l’ouverture de WO-040.

## 3. Objectif et périmètre strict

WO-040 doit :

1. reproduire hors ligne le chemin où une réponse HTTP complète devient inutilisable avant que son
   statut et son code sûr soient restitués au probe ;
2. identifier la dépendance exacte du lecteur à la terminaison du transport ou au framing HTTP ;
3. corriger la lecture de réponse dans une enveloppe byte-bounded et time-bounded, sans conserver
   la réponse brute ;
4. accepter une réponse uniquement lorsqu’un message HTTP/1.1 complet et non ambigu est prouvé ;
5. refuser les réponses tronquées, surnuméraires, malformées, ambiguës ou sans framing sûr ;
6. préserver la validation stricte du statut, du média type et du ProblemDetail corrélé ;
7. conserver la claim one-shot avant l’envoi et l’absence absolue de retry ou de rejeu ;
8. qualifier hors ligne puis, seulement si nécessaire, contre INT-001 inchangé sur loopback avec
   une donnée entièrement synthétique ;
9. produire un rapport autonome expurgé et soumettre le résultat à la revue propriétaire.

Les fichiers applicatifs Java, les migrations, le sender Local Lab, le receiver INT-001 et le
contrat HTTP sont hors périmètre. Les seuls changements runtime permis se trouvent sous
`scripts/wo036`, accompagnés de leurs tests Pester et de la documentation WO-040.

## 4. Invariants et interdictions

```text
REQUEST_MEDIA_TYPE=application/vnd.betting-project.j7-canonical-event+json;version=1.0
EXPECTED_TERMINAL_HTTP_STATUS=409
EXPECTED_SAFE_PROBLEM_CODE=J7_IMPORT_CONFLICT
NETWORK_SCOPE=127.0.0.1_ONLY
AUTOMATIC_RETRY=0
REDIRECTS=NEVER
PROXY=DISABLED
REQUEST_TIMEOUT_SECONDS=10
MAX_RESPONSE_HEADER_BYTES=16384
MAX_RESPONSE_BODY_BYTES=16384
MAX_RESPONSE_WIRE_BYTES=49152
RAW_RESPONSE_PERSISTED=NO
RAW_RESPONSE_LOGGED=NO
LOCAL_LAB_JAVA_SENDER_CHANGE=NO
RECEIVER_RUNTIME_CHANGE=NO
PROTOCOL_RELAXATION=NO
WO036_RESUME_AUTHORIZED=NO
PRIMARY_DATABASE_TOUCH=NO
PRIMARY_DATABASE_PURGE=NO
PROVIDER_CALLS=0
REMOTE_RECEIVER_CALLS=0
VPS_DEPLOYMENT=NO
PRODUCTION=NO
```

Les statuts `EXPERIMENTAL`, `LOCAL_ONLY`, `NOT_PRODUCTION_APPROVED` et
`NO_CRITICAL_DEPENDENCY` restent inchangés.

## 5. Qualification attendue

La qualification hors ligne doit couvrir au minimum :

- une réponse fixe complète suivie d’un transport encore ouvert ou fautif ne nécessite pas un EOF
  pour être classée ;
- une réponse chunked complète est restituée dès le chunk terminal valide ;
- une réponse close-delimited n’est admise qu’après EOF propre ;
- un framing `Content-Length` ou chunked tronqué reste fail-closed ;
- des octets postérieurs à un message complet, des en-têtes dupliqués, une double déclaration de
  framing, des trailers ou un corps surdimensionné sont refusés ;
- le statut `409` et le code `J7_IMPORT_CONFLICT` sont les seules valeurs terminales acceptées par
  la sonde ;
- aucune réponse brute ni inner exception sensible n’atteint la claim, les logs ou Git ;
- toute exception après claim consomme celle-ci et ne déclenche aucun retry.

Si une qualification loopback est requise, elle utilisera INT-001 inchangé, une base isolée, une
PKI éphémère et un corpus synthétique autonome. Elle sera limitée à l’effet strictement nécessaire
et devra se terminer avec zéro processus, listener, conteneur, volume, certificat ou répertoire
privé résiduel.

## 6. Critères de sortie

WO-040 ne pourra être proposé à la validation que si :

- la cause est reproduite et le correctif est borné au harnais WO-036 ;
- les qualifications hors ligne et, si exécutée, loopback sont `PASS_LOCAL_FAIL_CLOSED` ;
- le framing complet est établi avant restitution de toute métadonnée de réponse ;
- le protocole, le sender Java et le receiver restent inchangés ;
- tous les tests, contrôles UTF-8, secrets, loopback et flags bloquants sont verts ;
- le cleanup est exact et un rapport autonome versionné porte les seules preuves expurgées.

Même validé, WO-040 ne reprend pas WO-036. R5 exigera une décision propriétaire séparée, un run
entièrement neuf et un manifeste R5 gelé avant son premier POST.

## 7. Diagnostic hors ligne établi

Le lecteur antérieur accumulait toute la réponse puis exigeait un `Read()` retournant zéro avant
d'analyser les en-têtes et le corps. Une réponse HTTP/1.1 fixe pourtant complète pouvait donc être
perdue si le transport restait ouvert ou devenait fautif après ses derniers octets. Cette
dépendance à l'EOF reproduit de façon déterministe une classe de défaut compatible avec R4 ; elle
ne prétend pas reconstituer les octets bruts R4, qui n'ont volontairement pas été conservés.

Le test de reproduction fournit une réponse synthétique complète `409` avec
`Content-Length` et `J7_IMPORT_CONFLICT`, puis lève une erreur au prochain `Read()`. Avant
correction, le lecteur sollicitait ce `Read()` surnuméraire et échouait sans restituer le statut.
Après correction, le même flux restitue `409/J7_IMPORT_CONFLICT` sans attendre l'EOF.

```text
R4_RAW_RESPONSE_AVAILABLE=NO
ROOT_CAUSE_CLASS=HTTP_MESSAGE_COMPLETION_WAS_INCORRECTLY_COUPLED_TO_TRANSPORT_EOF
OFFLINE_REPRODUCTION=PASS_DETERMINISTIC
R4_COMPATIBILITY_CONFIDENCE=HIGH_NOT_RETROACTIVE_WIRE_OBSERVATION
LOCAL_LAB_JAVA_SENDER_CHANGED=NO
RECEIVER_RUNTIME_CHANGED=NO
PROTOCOL_CHANGED=NO
```

## 8. Correction bornée

Le lecteur sépare maintenant la détection incrémentale d'un message HTTP complet de son décodage
strict :

- une réponse avec `Content-Length` est restituée lorsque le nombre exact d'octets est déjà reçu ;
- une réponse `Transfer-Encoding: chunked` est restituée uniquement après un chunk terminal
  valide, sans extension ni trailer ;
- une réponse sans framing explicite reste close-delimited et exige toujours un EOF propre ;
- toute double déclaration de framing, troncature, longueur hostile, chunk invalide, dépassement
  de 16 Kio de corps ou octet surnuméraire déjà présent dans le buffer est refusé ;
- aucun octet brut n'est ajouté à la claim, aux journaux ou à Git et les buffers sont effacés ;
- la connexion n'est pas réutilisée, le retry reste nul et la claim one-shot reste inchangée.

Les réponses fixed-length et chunked complètes n'exigent donc plus que le serveur ferme le flux
pour que leur statut et leur ProblemDetail sûr soient disponibles. Le correctif reste limité à
`scripts/wo036/WO036-CampaignTools.psm1` et à ses tests Pester.

## 9. Qualification hors ligne finale

```text
PESTER_WO036_CAMPAIGN_TOOLS=PASS_53_OF_53
PESTER_WO036_AND_INFRASTRUCTURE=PASS_68_OF_68
MVNW_CLEAN_VERIFY_SUREFIRE=PASS_1136_TESTS_0_FAILURES_0_ERRORS_5_SKIPPED
MVNW_CLEAN_VERIFY_FAILSAFE=PASS_89_TESTS_0_FAILURES_0_ERRORS_0_SKIPPED
MVNW_CLEAN_VERIFY_BUILD=SUCCESS
MVNW_INTEGRATION_TESTS_SUREFIRE=PASS_1136_TESTS_0_FAILURES_0_ERRORS_5_SKIPPED
MVNW_INTEGRATION_TESTS_FAILSAFE=PASS_89_TESTS_0_FAILURES_0_ERRORS_0_SKIPPED
MVNW_INTEGRATION_TESTS_BUILD=SUCCESS
PROVIDER_CALLS=0
REMOTE_RECEIVER_CALLS=0
PRIMARY_DATABASE_TOUCH=NO
```

Les sept nouveaux cas Pester qualifient la restitution sans EOF, y compris avec lectures
fragmentées, l'unique séparateur d'une reason phrase vide et le refus fail-closed des réponses
fixed-length ou chunked tronquées, surnuméraires, hostiles ou aux séparateurs ambigus. Les tests
Maven n'effectuent que leurs appels locaux et leurs bases Testcontainers isolées.

## 10. Qualification loopback synthétique

Le run final, entièrement neuf, a utilisé le receiver INT-001 inchangé au commit
`b6a093ab4d3358f23a59b65b68a3eb720494bcba`, son JAR de SHA-256
`d41f74e984fa2455e535623fc570b30f91fd2b03d7742ada81c7c948f67c0c7f`, une base PostgreSQL
isolée liée à `127.0.0.1:5433`, le receiver lié à `127.0.0.1:8444` et une PKI mTLS éphémère hors
dépôt. Le corps J7 de `3149` octets était intégralement synthétique.

```text
LOOPBACK_STARTED_AT_UTC=2026-09-03T18:33:00.5193608Z
LOOPBACK_STARTED_AT_EUROPE_PARIS=2026-09-03T20:33:00.5193608+02:00
LOOPBACK_FINISHED_AT_UTC=2026-09-03T18:33:20.4330487Z
LOOPBACK_FINISHED_AT_EUROPE_PARIS=2026-09-03T20:33:20.4330487+02:00
IMPORT_ROUTE_POSTS=2
AUTOMATIC_RETRIES=0
BASELINE_HTTP_STATUS=201
BASELINE_RESPONSE_FRAMING=CONTENT_LENGTH
COLLISION_HTTP_STATUS=409
COLLISION_SAFE_CODE=J7_IMPORT_CONFLICT
COLLISION_RESPONSE_FRAMING=CHUNKED
QUALIFICATION_RESULT=PASS_LOCAL_FAIL_CLOSED
```

Une préparation mTLS antérieure via PFX a échoué dans SChannel avant toute écriture HTTP. Une
première qualification avec clé CNG a ensuite produit l'import nominal, puis s'est arrêtée sur
`HTTP_STATUS_LINE`, sans POST de collision. Elle a établi que le receiver réel émet un unique
séparateur après le code lorsque la reason phrase est vide. La correction complémentaire
`80cd5a33b19b2da48f0ab0821ef6fdfbd2623ba4` accepte cette seule forme sans accepter d'origine,
de framing ou de syntaxe ambiguë. Chaque état isolé a été nettoyé avant un nouvel essai ; aucune
tentative ne relève d'un retry automatique.

Les effets durables du run final sont exacts :

```text
RECEIPT_COUNT=1
PAYLOAD_COUNT=1
OUTBOX_COUNT=1
IMPORTED_AUDIT_COUNT=1
DIVERGENCE_AUDIT_COUNT=1
AUDIT_TOTAL_COUNT=2
DIVERGENCE_REASON=EXPORT_ID_DIVERGENCE
```

Le POST de collision a donc restitué au client `409/J7_IMPORT_CONFLICT` sous framing chunked
complet, sans second receipt, payload ou outbox.

## 11. Cleanup et contrôles postérieurs

```text
PROCESS_RESIDUAL_COUNT=0
CONTAINER_RESIDUAL_COUNT=0
VOLUME_RESIDUAL_COUNT=0
CERTIFICATE_STORE_RESIDUAL_COUNT=0
LISTENER_127_0_0_1_5433_COUNT=0
LISTENER_127_0_0_1_8444_COUNT=0
PRIVATE_RUN_ROOT_REMOVED=YES
PRIMARY_CONTAINER_NAME=betting-sofascore-local-lab-postgres
PRIMARY_CONTAINER_ID=e7b218117df39b7cbfbffb3b1223647568f149912a84592e506b5690f9686c1f
PRIMARY_CONTAINER_STATE=running
PRIMARY_CONTAINER_HEALTH=healthy
PRIMARY_LISTENER=127.0.0.1:5432
PRIMARY_DATABASE_TOUCH=NO
PRIMARY_DATABASE_PURGE=NO
```

Aucun HAR, trace, vidéo, capture, téléchargement, `storageState`, certificat privé, payload,
réponse brute, cookie, jeton ou secret n'est conservé. Le rapport autonome expurgé porte les
détails de la qualification et son empreinte.

## 12. Conclusion et revue propriétaire

WO-040 satisfait ses critères de sortie avec `PASS_LOCAL_FAIL_CLOSED`. Le diff runtime est borné
au module de campagne WO-036 et à ses tests ; aucun fichier Java, migration, receiver, contrat ou
endpoint n'a changé.

```text
J9_WO040_QUALIFICATION_RESULT=PASS_LOCAL_FAIL_CLOSED
J9_WO040_LOCAL_READINESS=PASS
J9_WO040_OWNER_REVIEW_REQUIRED=YES
J9_WO040_WORK_ORDER_MOVE_TO_COMPLETED=NO
J9_WO036_STATUS=STOPPED_AFTER_CONSUMED_R4_COLLISION_RESPONSE_QUALIFICATION_FAILURE
J9_WO036_RESUME_AUTHORIZED=NO
J9_WO036_NEXT_FRESH_RUN=R5
J9_WO036_R5_MANIFEST=REQUIRED_NEW_AND_FROZEN_BEFORE_FIRST_POST
J9_OFFICIAL_PERMISSION_STATUS=NOT_EVIDENCED
PROVIDER_DERIVED_REAL_DELIVERY_AUTHORIZED=NO
PROVIDER_NETWORK_AUTHORIZED=NO
REAL_RECEIVER_NETWORK_AUTHORIZED=NO
REMOTE_RECEIVER_NETWORK_AUTHORIZED=NO
LIVE_DELIVERY_AUTHORIZED=NO
VPS_DEPLOYMENT_AUTHORIZED=NO
PRODUCTION_AUTHORIZED=NO
```

## 13. Décision propriétaire et clôture

Le propriétaire valide WO-040, reconnaît sa readiness locale et autorise son déplacement vers les
Work Orders terminés. Les deux valeurs de clôture ont été confirmées explicitement dans un second
message après qu'une première formulation `YES|NO` a été laissée sans effet. Le bloc effectif est :

```text
J9_WO040_OWNER_REVIEW_DECISION=VALIDATE
J9_WO040_WORK_ORDER=WO-SS-20260903-040-j9-wo036-collision-response-client-qualification
J9_WO040_PRIMARY_IMPLEMENTATION_COMMIT=f6e6a804f507bad48943534d4179bfcc90437766
J9_WO040_RESPONSE_LINE_COMPATIBILITY_COMMIT=80cd5a33b19b2da48f0ab0821ef6fdfbd2623ba4
J9_WO040_DOCUMENTATION_COMMIT=8e7441b94d6074fd59d5b18c5cd830c064e88e5f
J9_WO040_QUALIFICATION_RESULT=PASS_LOCAL_FAIL_CLOSED
J9_WO040_QUALIFICATION_REPORT_SHA256=2eb13aa1825d21eb5c40e97ffebf77246099d781f626591132897c4d128e9aee
J9_WO040_LOCAL_READINESS_ACKNOWLEDGED=YES
J9_WO040_WORK_ORDER_MOVE_TO_COMPLETED=YES

J9_WO036_STATUS=STOPPED_AFTER_CONSUMED_R4_COLLISION_RESPONSE_QUALIFICATION_FAILURE
J9_WO036_RESUME_AFTER_WO040_VALIDATION=REQUIRES_SEPARATE_OWNER_DECISION
J9_WO036_WORK_ORDER_MOVE_TO_COMPLETED=NO
J9_WO036_NEXT_FRESH_RUN=R5
J9_WO036_R5_MANIFEST=REQUIRED_NEW_AND_FROZEN_BEFORE_FIRST_POST

J9_OFFICIAL_PERMISSION_STATUS=NOT_EVIDENCED
J9_PROVIDER_DERIVED_REAL_DELIVERY_AUTHORIZED=NO
J9_PROVIDER_NETWORK_AUTHORIZED=NO
REAL_RECEIVER_NETWORK_AUTHORIZED=NO
REMOTE_RECEIVER_NETWORK_AUTHORIZED=NO
LIVE_DELIVERY_AUTHORIZED=NO
J9_VPS_DEPLOYMENT_AUTHORIZED=NO
J9_PRODUCTION_AUTHORIZED=NO
```

La validation clôt WO-040 uniquement. Elle ne reprend pas WO-036 et n'autorise pas R5. La
prochaine campagne doit encore recevoir une décision propriétaire séparée, créer un run R5 neuf
et geler son manifeste avant le premier POST.

```text
WORK_ORDER_STATUS=VALIDATED
IMPLEMENTATION_STATUS=COMPLETE
QUALIFICATION_STATUS=PASS_LOCAL_FAIL_CLOSED
OWNER_REVIEW_REQUIRED=NO
LOCAL_READINESS_ACKNOWLEDGED=YES
WORK_ORDER_MOVE_TO_COMPLETED=YES
WO036_STATUS=STOPPED_PENDING_SEPARATE_R5_OWNER_DECISION
WO036_RESUME_AUTHORIZED=NO
WO036_NEXT_FRESH_RUN=R5
J9_OFFICIAL_PERMISSION_STATUS=NOT_EVIDENCED
PROVIDER_DERIVED_REAL_DELIVERY_AUTHORIZED=NO
PROVIDER_NETWORK_AUTHORIZED=NO
REAL_RECEIVER_NETWORK_AUTHORIZED=NO
REMOTE_RECEIVER_NETWORK_AUTHORIZED=NO
LIVE_DELIVERY_AUTHORIZED=NO
VPS_DEPLOYMENT_AUTHORIZED=NO
PRODUCTION_AUTHORIZED=NO
```

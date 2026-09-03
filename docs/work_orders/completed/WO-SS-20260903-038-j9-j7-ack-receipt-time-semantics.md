# WO-SS-20260903-038 — Sémantique temporelle de l’accusé J7

- **Statut :** `VALIDATED`
- **Jalon :** après J9 — correction runtime préalable à une nouvelle reprise de WO-036
- **Ouvert le :** 2026-09-03
- **Ouverture UTC :** `2026-09-03T11:11:54.5206596Z`
- **Ouverture Europe/Paris :** `2026-09-03T13:11:54.5206596+02:00`
- **Branche :** `codex/j9-wo038-j7-ack-receipt-time-semantics`
- **Worktree :** `.tmp/j9-wo038-j7-ack-receipt-time-semantics`
- **Base locale d’ouverture vérifiée :** `4cbcb1eb48344b153cd8d8f392aa805feb84ea32`
- **Commit d’ouverture :** `2b95e3885cad2f8729d9e429192c7a5171bb80a1`
- **Commit d’implémentation qualifié :** `3a0c297a5151c572417b4f2f12bb5c3ed216172f`
- **Décision propriétaire enregistrée UTC :** `2026-09-03T12:35:39Z`
- **Décision propriétaire enregistrée Europe/Paris :** `2026-09-03T14:35:39+02:00`
- **Rapport de qualification :**
  `docs/validation/J9-WO038-J7-ACK-RECEIPT-TIME-SEMANTICS-QUALIFICATION-20260903.md`
- **SHA-256 du rapport de qualification :**
  `QUALIFICATION_REPORT_SHA256=e51bc537c775b4378ee1c6672f86f6c7c085849d62d51970c3d1b6e4aef1395a`
- **Work Order bloqué :** `WO-SS-20260902-036-j9-j7-local-e2e-qualification`
- **Rapport du constat :**
  `docs/validation/J9-WO036-J7-LOCAL-E2E-CAMPAIGN-RESUME-STOP-20260903.md`
- **SHA-256 du rapport du constat :**
  `219f54f2b429b1eaea04c920e55cc87d33f9c5644697129e6ff96fc9cad19062`
- **Permission officielle :** `NOT_EVIDENCED`

## 1. Autorisation propriétaire

Le propriétaire autorise le diagnostic, la correction et la qualification locale/hors ligne de la
sémantique temporelle des accusés J7. Le bloc suivant est consigné mot pour mot :

```text
J9_WO038_OWNER_DECISION=AUTHORIZE_IMPLEMENTATION
J9_WO038_WORK_ORDER=WO-SS-20260903-038-j9-j7-ack-receipt-time-semantics
J9_WO038_SCOPE=DIAGNOSE_CORRECT_AND_QUALIFY_LOCAL_LAB_J7_DUPLICATE_ACK_DURABLE_RECEIPT_TIME_CLASSIFICATION_AND_DISTINCT_SENDER_RECEIVER_CLOCK_BOUNDARIES
J9_WO038_LOOPBACK_AND_OFFLINE_QUALIFICATION_AUTHORIZED=YES
J9_WO038_RECEIVER_CONTRACT_INVARIANT=DUPLICATE_REUSES_INITIAL_REMOTE_IMPORT_ID_AND_RECEIVED_AT
J9_WO038_RECEIVER_RUNTIME_CHANGE_AUTHORIZED=NO
J9_WO038_AUTOMATIC_RETRY_AUTHORIZED=NO

J9_WO036_STATUS=STOPPED_AFTER_DUPLICATE_ACK_PENDING_DISTINCT_RUNTIME_CORRECTION
J9_WO036_RESUME_AFTER_WO038_VALIDATION=REQUIRES_SEPARATE_OWNER_DECISION
J9_WO036_WORK_ORDER_MOVE_TO_COMPLETED=NO
J9_LOCAL_INT001_RECEIVER_LOOPBACK_AUTHORIZED=NO_PENDING_NEW_OWNER_DECISION

J9_OFFICIAL_PERMISSION_STATUS=NOT_EVIDENCED
J9_PROVIDER_DERIVED_REAL_DELIVERY_AUTHORIZED=NO
J9_PROVIDER_NETWORK_AUTHORIZED=NO
REAL_RECEIVER_NETWORK_AUTHORIZED=NO
REMOTE_RECEIVER_NETWORK_AUTHORIZED=NO
J9_VPS_DEPLOYMENT_AUTHORIZED=NO
J9_PRODUCTION_AUTHORIZED=NO
```

Cette autorisation ne vaut ni validation de WO-038, ni reprise de WO-036, ni autorisation de
connexion au receiver INT-001, de livraison, de réseau fournisseur, de VPS ou de production.

## 2. Constat qualifié et cause

La reprise R2 de WO-036 a établi une séquence receiver correcte : le premier envoi a produit
`201/IMPORTED`, puis la répétition byte-identique a produit `200/DUPLICATE` sans second payload ni
second événement d’outbox. Conformément au contrat INT-001, l’accusé duplicate réemploie le
`remoteImportId` et le `receivedAt` du premier import durable.

Avant correction, le sender Local Lab reconnaissait la paire `200/DUPLICATE`, corrélait
correctement le protocole, l’export et les hashes, et validait la forme canonique non nulle de
l’identité distante. Il exigeait cependant que `receivedAt` soit compris entre le début et la fin
de la tentative locale courante. L’instant durable initial était donc rejeté par construction lors
de la seconde tentative et recevait le code inexact
`ACK_HTTP_STATUS_MISMATCH`.

Cette borne compare en outre des horloges murales distinctes : `startedAt` et la réception HTTP
proviennent du Local Lab, tandis que `ack.receivedAt` provient du receiver. Sans contrat explicite
de synchronisation ni tolérance versionnée, cette comparaison ne peut pas constituer une preuve
fiable d’ordre causal, y compris pour un premier import Windows→VPS.

## 3. Objectif et périmètre strict

WO-038 doit :

1. préserver les paires contractuelles strictes `201/IMPORTED` et `200/DUPLICATE` ;
2. préserver la corrélation du protocole, de `exportId`, `fileSha256` et `dataSha256`, ainsi que la
   validation canonique non nulle de `remoteImportId` ; la réutilisation de cette identité lors
   d’un duplicate reste un invariant du receiver, vérifié côté receiver/campagne et persisté sans
   substitution par le sender ;
3. traiter `receivedAt` comme l’instant durable déclaré par le receiver et non comme un instant de
   l’horloge locale du sender ;
4. accepter pour un duplicate l’identité et l’instant durables du premier import, même lorsque cet
   instant précède la tentative courante ;
5. ne pas déduire une incohérence d’un simple décalage entre les horloges sender et receiver ;
6. séparer les codes de classification afin qu’une paire HTTP/ACK invalide ne soit pas confondue
   avec une éventuelle invalidité propre aux champs de l’accusé ;
7. qualifier le comportement hors ligne et, si nécessaire, au moyen d’un receiver synthétique
   exclusivement loopback.

La valeur distante acceptée doit rester exactement représentable dans le ledger : UTC canonique
avec suffixe `Z`, année ISO non étendue de `0001` à `9999`, précision maximale de six chiffres
fractionnaires et exclusion des sentinelles PostgreSQL infinies. Ces bornes sont des contraintes de
forme et de persistance, pas une tolérance ni un ordre relatif à l’horloge du sender.

Le correctif reste dans le Local Lab. Il ne modifie ni le receiver INT-001, ni le format d’ACK, ni
le protocole J7, ni le transport mTLS, ni les états durables de livraison. La migration V29 reste
byte-identique et aucune table, colonne, contrainte ou donnée n’est réécrite. La migration
append-only V30 remplace uniquement le corps du trigger de résultat afin de conserver les gardes
locales tout en cessant d’ordonner l’horloge distante du receiver contre celle du Local Lab.

## 4. Invariants et interdictions

Le sender reste manuel, mono-exécution, sans redirection et sans retry automatique. Un ACK positif
n’est accepté qu’après validation du media type, de sa taille, de son JSON strict, de sa paire
HTTP/statut et de toutes ses corrélations. Aucun ACK brut, payload, certificat privé ou secret ne
doit être journalisé ou versionné.

```text
AUTOMATIC_RETRY=0
RECEIVER_RUNTIME_CHANGE_AUTHORIZED=NO
WO036_RESUME_AUTHORIZED_BY_WO038=NO
LOCAL_INT001_RECEIVER_LOOPBACK_AUTHORIZED=NO_PENDING_NEW_OWNER_DECISION
J9_OFFICIAL_PERMISSION_STATUS=NOT_EVIDENCED
PROVIDER_DERIVED_REAL_DELIVERY_AUTHORIZED=NO
PROVIDER_NETWORK_AUTHORIZED=NO
REAL_RECEIVER_NETWORK_AUTHORIZED=NO
REMOTE_RECEIVER_NETWORK_AUTHORIZED=NO
VPS_DEPLOYMENT_AUTHORIZED=NO
PRODUCTION_AUTHORIZED=NO
PUSH_OR_MERGE_AUTHORIZED_BY_WO038=NO
```

Les statuts `EXPERIMENTAL`, `LOCAL_ONLY`, `NOT_PRODUCTION_APPROVED` et
`NO_CRITICAL_DEPENDENCY` restent inchangés.

## 5. Qualification attendue

Les tests doivent établir au minimum :

- `201/IMPORTED` valide avec une horloge receiver en retard ou en avance sur celle du sender ;
- `200/DUPLICATE` valide réemployant le `remoteImportId` et le `receivedAt` initiaux strictement
  antérieurs au second claim ;
- répétition duplicate sans nouvel envoi automatique et sans altération de l’idempotence ;
- refus des paires `HTTP/status` non contractuelles ;
- refus des corrélations protocole, export ou hashes divergentes ;
- parsing strict et présence obligatoire d’un `receivedAt` UTC canonique ;
- conservation des classifications fail-closed pour ACK hostile, timeout, TLS, `3xx`, autres
  `4xx` et `5xx` ;
- absence de réseau réel, de retry, de payload sensible, de listener ou de processus résiduel.

Les vérifications de dépôt comprennent `mvnw.cmd clean verify`,
`mvnw.cmd -Pintegration-tests verify`, les tests ciblés du service/transport J7,
`git diff --check`, les contrôles UTF-8, secrets, loopback et flags bloquants. Toute qualification
loopback utilise uniquement des données synthétiques et ne vaut pas reprise de WO-036.

## 6. Critères de sortie

Le Work Order a été soumis à la revue propriétaire avec `PASS_LOCAL_FAIL_CLOSED` après confirmation
que le correctif restait borné au sender, que tous les cas ci-dessus étaient verts, que les preuves
étaient expurgées et qu’un rapport autonome référencé par son SHA-256 était versionné. Le
propriétaire a validé le résultat, reconnu la readiness locale et autorisé le déplacement du Work
Order vers les travaux terminés.

Cette validation ne reprend pas WO-036. Son statut reste
`STOPPED_AFTER_DUPLICATE_ACK_PENDING_DISTINCT_RUNTIME_CORRECTION` et sa reprise exige toujours une
nouvelle décision propriétaire ainsi qu’un manifeste de campagne neuf.

```text
WORK_ORDER_STATUS=VALIDATED
IMPLEMENTATION_STATUS=COMPLETED
QUALIFICATION_STATUS=PASS_LOCAL_FAIL_CLOSED
QUALIFICATION_REPORT=docs/validation/J9-WO038-J7-ACK-RECEIPT-TIME-SEMANTICS-QUALIFICATION-20260903.md
QUALIFICATION_REPORT_SHA256=e51bc537c775b4378ee1c6672f86f6c7c085849d62d51970c3d1b6e4aef1395a
IMPLEMENTATION_COMMIT=3a0c297a5151c572417b4f2f12bb5c3ed216172f
OWNER_REVIEW_REQUIRED=NO
OWNER_REVIEW_DECISION=VALIDATE
WORK_ORDER_MOVE_TO_COMPLETED=YES
WORK_ORDER_LOCATION=docs/work_orders/completed/WO-SS-20260903-038-j9-j7-ack-receipt-time-semantics.md
RECEIVER_RUNTIME_CHANGE=NO
AUTOMATIC_RETRY=0
WO036_STATUS=STOPPED_AFTER_DUPLICATE_ACK_PENDING_DISTINCT_RUNTIME_CORRECTION
WO036_RESUME_AFTER_WO038_VALIDATION=REQUIRES_SEPARATE_OWNER_DECISION
WO036_WORK_ORDER_MOVE_TO_COMPLETED=NO
LOCAL_INT001_RECEIVER_LOOPBACK_AUTHORIZED=NO_PENDING_NEW_OWNER_DECISION
J9_OFFICIAL_PERMISSION_STATUS=NOT_EVIDENCED
J9_PROVIDER_DERIVED_REAL_DELIVERY_AUTHORIZED=NO
J9_PROVIDER_NETWORK_AUTHORIZED=NO
REAL_RECEIVER_NETWORK_AUTHORIZED=NO
REMOTE_RECEIVER_NETWORK_AUTHORIZED=NO
J9_VPS_DEPLOYMENT_AUTHORIZED=NO
J9_PRODUCTION_AUTHORIZED=NO
```

## 7. Implémentation réalisée

Le commit `3a0c297a5151c572417b4f2f12bb5c3ed216172f` supprime uniquement l’ordre entre
`ack.receivedAt` et les instants locaux de tentative. Les paires contractuelles
`201/IMPORTED` et `200/DUPLICATE`, les corrélations de protocole, d’export et de hashes, la forme
canonique de l’identité distante et toutes les classifications fail-closed restent exigées.

Une politique de domaine commune borne `receivedAt` aux instants UTC canoniques exactement
persistables à la microseconde et aux années ISO non étendues `0001..9999`. Le ledger Java
conserve l’ordre local `completedAt >= startedAt` et persiste l’instant receiver sans substitution.

La migration append-only V30 remplace uniquement le corps du trigger de résultat et ses
commentaires. Elle conserve les gardes d’état `IN_FLIGHT`, de dernière tentative et d’ordre local,
retire les deux comparaisons inter-horloges, puis refuse les sentinelles PostgreSQL infinies et les
dates hors de la plage canonique. Les scripts J6 exigent désormais le schéma courant V30.

```text
V29_FILE=src/main/resources/db/migration/V29__j9_optional_local_push_delivery_ledger.sql
V29_BYTES=13569
V29_SHA256=49f32e88af2ed0115fa9cc5587913f4f0ed7576d49eb1bd4634a0dd212416bd5
V29_REWRITTEN=NO
V30_FILE=src/main/resources/db/migration/V30__j9_j7_ack_receipt_time_semantics.sql
V30_BYTES=1804
V30_SHA256=4fdb5e8ed1865d0b1fb0b028653f5600e70fa96148c4da102e0371b1ad2d2967
RECEIVER_RUNTIME_CHANGED=NO
ACK_PROTOCOL_CHANGED=NO
AUTOMATIC_RETRY_ADDED=NO
```

## 8. Décision factuelle et classifications

| Option examinée | Conséquence | Décision |
|---|---|---|
| borner ou remplacer l’instant receiver par l’horloge locale | perd la preuve distante exacte | `REJECTED` |
| ajouter une tolérance fixe entre les deux horloges | ajoute un seuil arbitraire sans contrat de synchronisation | `REJECTED` |
| lever seulement la borne basse de `DUPLICATE` | laisse d’autres succès dépendre d’une horloge étrangère | `REJECTED` |
| supprimer uniquement l’ordre inter-horloges et garder les validations structurelles et locales | respecte le contrat receiver sans affaiblir le fail-closed | `SELECTED` |

Après correction :

| Réponse | Résultat sender | Code sûr |
|---|---|---|
| `201/IMPORTED` strict avec instant receiver indépendant | `DELIVERED` | `HTTP_201_IMPORTED` |
| `200/DUPLICATE` strict avec instant durable initial | `DUPLICATE_CONFIRMED` | `HTTP_DUPLICATE_CONFIRMED` |
| paire HTTP/statut non contractuelle | `UNKNOWN_RECONCILIATION_REQUIRED` | `ACK_HTTP_STATUS_MISMATCH` |
| instant non canonique ou non exactement persistable | `UNKNOWN_RECONCILIATION_REQUIRED` | `ACK_INVALID_OR_MISMATCHED` |

## 9. Qualification et compatibilité V29→V30

Le test d’upgrade isolé part d’une base V29 préremplie. Après V30, l’entrée Flyway V29 reste
identique, une seule migration supplémentaire est enregistrée, et les comptes ainsi que
l’empreinte agrégée des trois tables du ledger sont inchangés. Le nouveau trigger conserve les
gardes locales et l’append-only, accepte un instant receiver antérieur d’un jour et le relit
exactement, puis refuse `infinity`, `-infinity`, une année antérieure à `0001` et l’année `10000`.

Les tests de domaine, parsing et service couvrent les bornes inclusives `0001` et `9999`, le refus
d’une précision nanoseconde, des instants receiver `1900` et `2100`, un duplicate historique, les
mauvaises paires et l’absence de retry. Le test E2E mTLS synthétique couvre séparément un import
nominal, puis un effet durable suivi d’une réponse inconnue et d’une relance manuelle donnant
`200/DUPLICATE` : deux requêtes produisent un seul effet receiver, avec identité, instant et SHA de
l’ACK persistés exactement.

```text
MVNW_CLEAN_VERIFY=PASS
SUREFIRE_TESTS=1136
SUREFIRE_FAILURES=0
SUREFIRE_ERRORS=0
SUREFIRE_SKIPPED=5
MVNW_INTEGRATION_TESTS_VERIFY=PASS
FAILSAFE_TESTS=89
FAILSAFE_FAILURES=0
FAILSAFE_ERRORS=0
FAILSAFE_SKIPPED=0
TARGETED_WO038_TESTS=PASS
DOCKER_COMPOSE_CONFIG_QUIET=PASS
POWERSHELL_SCRIPT_PARSE_ERRORS=0
MODIFIED_TEXT_UTF8_STRICT=23/23
MODIFIED_TEXT_UTF8_BOM_COUNT=0
MODIFIED_TEXT_NUL_COUNT=0
ADDED_SECRET_PATTERN_HITS=0
WO036_PROTECTED_DIFF_LINES=0
SERVER_ADDRESS_DEFAULT=127.0.0.1
PROVIDER_DEFAULT_FLAGS=BLOCKED
REAL_RECEIVER_DEFAULT_FLAGS=BLOCKED
TESTCONTAINERS_RESIDUALS=0
JAVA_PROCESS_RESIDUALS=0
BROWSER_PROCESS_RESIDUALS=0
PROVIDER_CALLS=0
INT001_RUNTIME_CALLS=0
REAL_RECEIVER_CALLS=0
REMOTE_RECEIVER_CALLS=0
```

La preuve autonome est consignée dans
`docs/validation/J9-WO038-J7-ACK-RECEIPT-TIME-SEMANTICS-QUALIFICATION-20260903.md`. Son empreinte
validée sur les octets finalisés est
`e51bc537c775b4378ee1c6672f86f6c7c085849d62d51970c3d1b6e4aef1395a`.

## 10. Décision propriétaire et clôture

Le bloc de décision reçu du propriétaire est consigné ci-dessous :

```text
J9_WO038_OWNER_REVIEW_DECISION=VALIDATE
J9_WO038_WORK_ORDER=WO-SS-20260903-038-j9-j7-ack-receipt-time-semantics
J9_WO038_IMPLEMENTATION_COMMIT=3a0c297a5151c572417b4f2f12bb5c3ed216172f
J9_WO038_QUALIFICATION_RESULT=PASS_LOCAL_FAIL_CLOSED
J9_WO038_QUALIFICATION_REPORT_SHA256=e51bc537c775b4378ee1c6672f86f6c7c085849d62d51970c3d1b6e4aef1395a
J9_WO038_LOCAL_READINESS_ACKNOWLEDGED=YES
J9_WO038_WORK_ORDER_MOVE_TO_COMPLETED=YES

J9_WO036_STATUS=STOPPED_AFTER_DUPLICATE_ACK_PENDING_DISTINCT_RUNTIME_CORRECTION
J9_WO036_RESUME_AFTER_WO038_VALIDATION=REQUIRES_SEPARATE_OWNER_DECISION
J9_WO036_WORK_ORDER_MOVE_TO_COMPLETED=NO
J9_LOCAL_INT001_RECEIVER_LOOPBACK_AUTHORIZED=NO_PENDING_NEW_OWNER_DECISION

J9_OFFICIAL_PERMISSION_STATUS=NOT_EVIDENCED
J9_PROVIDER_DERIVED_REAL_DELIVERY_AUTHORIZED=NO
J9_PROVIDER_NETWORK_AUTHORIZED=NO
REAL_RECEIVER_NETWORK_AUTHORIZED=NO
REMOTE_RECEIVER_NETWORK_AUTHORIZED=NO
J9_VPS_DEPLOYMENT_AUTHORIZED=NO
J9_PRODUCTION_AUTHORIZED=NO
```

Les références du bloc concordent avec le commit d’implémentation et le rapport qualifiés. La
décision a été consignée le `2026-09-03T12:35:39Z`, soit le
`2026-09-03T14:35:39+02:00` en Europe/Paris.

```text
WORK_ORDER_STATUS=VALIDATED
IMPLEMENTATION_STATUS=COMPLETED
QUALIFICATION_STATUS=PASS_LOCAL_FAIL_CLOSED
OWNER_REVIEW_REQUIRED=NO
OWNER_REVIEW_DECISION=VALIDATE
OWNER_REVIEW_BLOCK_STATUS=COMPLETE
LOCAL_READINESS_ACKNOWLEDGED=YES
IMPLEMENTATION_COMMIT_MATCH=YES
QUALIFICATION_REPORT_SHA256_MATCH=YES
OWNER_DECISION_RECORDED_AT_UTC=2026-09-03T12:35:39Z
OWNER_DECISION_RECORDED_AT_EUROPE_PARIS=2026-09-03T14:35:39+02:00
WORK_ORDER_MOVE_TO_COMPLETED=YES
MOVE_TO_COMPLETED_AUTHORIZED=YES
MOVE_TO_COMPLETED_PERFORMED=YES
WORK_ORDER_LOCATION=docs/work_orders/completed/WO-SS-20260903-038-j9-j7-ack-receipt-time-semantics.md

RECEIVER_RUNTIME_CHANGE=NO
AUTOMATIC_RETRY=0
WO036_STATUS=STOPPED_AFTER_DUPLICATE_ACK_PENDING_DISTINCT_RUNTIME_CORRECTION
WO036_RESUME_AFTER_WO038_VALIDATION=REQUIRES_SEPARATE_OWNER_DECISION
WO036_WORK_ORDER_MOVE_TO_COMPLETED=NO
LOCAL_INT001_RECEIVER_LOOPBACK_AUTHORIZED=NO_PENDING_NEW_OWNER_DECISION

J9_OFFICIAL_PERMISSION_STATUS=NOT_EVIDENCED
J9_PROVIDER_DERIVED_REAL_DELIVERY_AUTHORIZED=NO
J9_PROVIDER_NETWORK_AUTHORIZED=NO
REAL_RECEIVER_NETWORK_AUTHORIZED=NO
REMOTE_RECEIVER_NETWORK_AUTHORIZED=NO
J9_VPS_DEPLOYMENT_AUTHORIZED=NO
J9_PRODUCTION_AUTHORIZED=NO
```

WO-038 est clôturé et déplacé vers les Work Orders terminés. Cette clôture ne reprend pas WO-036,
n’autorise pas le receiver INT-001 loopback et n’ouvre aucune livraison de donnée dérivée du
fournisseur, aucun réseau fournisseur ou receiver réel/distant, aucun VPS ni aucune production.

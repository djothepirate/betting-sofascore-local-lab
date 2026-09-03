# WO-SS-20260903-038 — Sémantique temporelle de l’accusé J7

- **Statut :** `IN_PROGRESS`
- **Jalon :** après J9 — correction runtime préalable à une nouvelle reprise de WO-036
- **Ouvert le :** 2026-09-03
- **Ouverture UTC :** `2026-09-03T11:11:54.5206596Z`
- **Ouverture Europe/Paris :** `2026-09-03T13:11:54.5206596+02:00`
- **Branche :** `codex/j9-wo038-j7-ack-receipt-time-semantics`
- **Worktree :** `.tmp/j9-wo038-j7-ack-receipt-time-semantics`
- **Base locale d’ouverture vérifiée :** `4cbcb1eb48344b153cd8d8f392aa805feb84ea32`
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

Le sender Local Lab reconnaît la paire `200/DUPLICATE`, corrèle correctement le protocole,
l’export et les hashes, et valide la forme canonique non nulle de l’identité distante. Il exige
cependant actuellement que `receivedAt` soit compris
entre le début et la fin de la tentative locale courante. L’instant durable initial est donc rejeté
par construction lors de la seconde tentative et reçoit le code inexact
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

Le Work Order pourra être soumis à la revue propriétaire avec
`PASS_LOCAL_FAIL_CLOSED` seulement si le correctif reste borné au sender, que tous les cas ci-dessus
sont verts, que les preuves sont expurgées et qu’un rapport autonome avec SHA-256 est versionné.

WO-038 restera actif à `READY_FOR_OWNER_REVIEW` jusqu’à une validation explicite. Même après cette
validation, WO-036 restera
`STOPPED_AFTER_DUPLICATE_ACK_PENDING_DISTINCT_RUNTIME_CORRECTION` et sa reprise exigera une nouvelle
décision propriétaire ainsi qu’un manifeste de campagne neuf.

```text
WORK_ORDER_STATUS=IN_PROGRESS
IMPLEMENTATION_STATUS=AUTHORIZED
QUALIFICATION_STATUS=NOT_RUN
OWNER_REVIEW_REQUIRED=YES
WO036_STATUS=STOPPED_AFTER_DUPLICATE_ACK_PENDING_DISTINCT_RUNTIME_CORRECTION
WO036_RESUME_AFTER_WO038_VALIDATION=REQUIRES_SEPARATE_OWNER_DECISION
WO036_WORK_ORDER_MOVE_TO_COMPLETED=NO
```

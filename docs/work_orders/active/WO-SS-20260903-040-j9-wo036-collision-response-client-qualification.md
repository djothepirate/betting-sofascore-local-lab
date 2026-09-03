# WO-SS-20260903-040 — Qualification de la capture de réponse de collision WO-036

- **Statut :** `IN_PROGRESS_OFFLINE_DIAGNOSIS`
- **Jalon :** après J9 — correction du harnais avant une éventuelle reprise R5 de WO-036
- **Ouvert le :** 2026-09-03
- **Ouverture UTC :** `2026-09-03T17:38:14.3891321Z`
- **Ouverture Europe/Paris :** `2026-09-03T19:38:14.3891321+02:00`
- **Branche :** `codex/j9-wo040-collision-response-client-qualification`
- **Worktree :** `.tmp/w40`
- **Base locale d’ouverture vérifiée :** `33d2ae0bd5c2c8daae4cd708b0f12efaf9cd029d`
- **Work Order bloqué :** `WO-SS-20260902-036-j9-j7-local-e2e-qualification`
- **Rapport du constat R4 :**
  `docs/validation/J9-WO036-J7-LOCAL-E2E-CAMPAIGN-RESUME-R4-STOP-20260903.md`
- **SHA-256 du rapport R4 :**
  `16e9f85e12109f2709146312410bbe2a5e040c4f176c2d5596746aa2b70076b5`
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

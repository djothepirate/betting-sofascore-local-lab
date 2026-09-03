# WO-SS-20260903-039 — Sérialisation HTTP de la sonde de collision WO-036

- **Statut :** `ACTIVE`
- **Jalon :** après J9 — correction du harnais avant une éventuelle reprise R4 de WO-036
- **Ouvert le :** 2026-09-03
- **Ouverture UTC :** `2026-09-03T14:32:05.0141332Z`
- **Ouverture Europe/Paris :** `2026-09-03T16:32:05.0157004+02:00`
- **Branche :** `codex/j9-wo039-collision-probe-http-serialization`
- **Worktree :** `.tmp/w39`
- **Base locale d’ouverture vérifiée :** `277f65f318386c763069b9e1907d34acf3b228ba`
- **Work Order bloqué :** `WO-SS-20260902-036-j9-j7-local-e2e-qualification`
- **Rapport du constat R3 :**
  `docs/validation/J9-WO036-J7-LOCAL-E2E-CAMPAIGN-RESUME-R3-STOP-20260903.md`
- **SHA-256 du rapport R3 :**
  `f59cc0aeaa56fd6c8156032fea7e93048f17f616f1882cc3979bcd69363d1576`
- **Permission officielle :** `NOT_EVIDENCED`

Le chemin de worktree court est imposé par la limite de longueur de chemin Windows rencontrée lors
de la matérialisation initiale. Il ne modifie ni le nom de la branche, ni le commit de base, ni la
portée du Work Order.

## 1. Autorisation propriétaire

Le propriétaire autorise l’ouverture, la correction et la qualification du seul client de sonde
de collision appartenant au harnais WO-036. Le bloc suivant est consigné sans élargissement :

```text
J9_WO039_OWNER_DECISION=AUTHORIZE
J9_WO039_OPEN_WORK_ORDER_AUTHORIZED=YES
J9_WO039_WORK_ORDER=WO-SS-20260903-039-j9-wo036-collision-probe-http-serialization
J9_WO039_SCOPE=CORRECT_AND_QUALIFY_WO036_COLLISION_PROBE_HTTP_SERIALIZATION_AND_SAFE_FAILURE_EVIDENCE

J9_WO039_ALLOWED_CHANGE=WO036_CAMPAIGN_TOOLING_ONLY
J9_WO039_LOCAL_LAB_JAVA_SENDER_CHANGE_AUTHORIZED=NO
J9_WO039_RECEIVER_RUNTIME_CHANGE_AUTHORIZED=NO
J9_WO039_PROTOCOL_RELAXATION_AUTHORIZED=NO
J9_WO039_CONTENT_TYPE_CONTRACT_CHANGE_AUTHORIZED=NO

J9_WO039_OFFLINE_SERIALIZATION_QUALIFICATION_AUTHORIZED=YES
J9_WO039_LOCAL_INT001_LOOPBACK_QUALIFICATION_AUTHORIZED=YES_SYNTHETIC_ONLY
J9_WO039_NETWORK_SCOPE=127.0.0.1_ONLY
J9_WO039_PROVIDER_CALLS_AUTHORIZED=NO
J9_WO039_PROVIDER_DERIVED_PAYLOADS_AUTHORIZED=NO
J9_WO039_REMOTE_RECEIVER_NETWORK_AUTHORIZED=NO
J9_WO039_VPS_DEPLOYMENT_AUTHORIZED=NO
J9_WO039_PRODUCTION_AUTHORIZED=NO
J9_WO039_PRIMARY_DATABASE_TOUCH_AUTHORIZED=NO
J9_WO039_PRIMARY_DATABASE_PURGE_AUTHORIZED=NO
J9_WO039_INT001_PULL_REQUEST_AUTHORIZED=NO
J9_WO039_INT001_VALIDATION_AUTHORIZED=NO

J9_WO039_WO036_CAMPAIGN_REPLAY_AUTHORIZED=NO
J9_WO039_WO036_COLLISION_PROBE_REPLAY_AUTHORIZED=NO
J9_WO039_WO036_RESUME_AUTHORIZED=NO
J9_WO036_STATUS=STOPPED_AFTER_CONSUMED_COLLISION_PROBE_PENDING_WO039
J9_WO036_WORK_ORDER_MOVE_TO_COMPLETED=NO
J9_WO036_RESUME_AFTER_WO039_VALIDATION=REQUIRES_SEPARATE_OWNER_DECISION
J9_WO036_NEXT_FRESH_RUN=R4
J9_WO036_R4_MANIFEST=REQUIRED_NEW_AND_FROZEN_BEFORE_FIRST_POST

J9_OFFICIAL_PERMISSION_STATUS=NOT_EVIDENCED
PROVIDER_DERIVED_REAL_DELIVERY_AUTHORIZED=NO
REAL_RECEIVER_NETWORK_AUTHORIZED=NO
LIVE_DELIVERY_AUTHORIZED=NO
WORK_ORDER_MOVE_TO_COMPLETED=NO
```

Cette autorisation ne reprend pas WO-036 et ne rend pas réutilisable la claim R3 consommée. Une
qualification loopback sous WO-039 doit créer sa propre preuve synthétique bornée ; elle ne peut
pas être présentée comme R4 ni comme une campagne E2E complète.

## 2. Constat factuel R3

R3 a produit `201/IMPORTED`, puis `200/DUPLICATE`. Le troisième et dernier appel a reçu une
réponse différente de `409`; le statut numérique et le code du ProblemDetail n’ont pas été
conservés par le harnais. La claim est `FAILED_OR_UNKNOWN_CONSUMED`, aucun retry n’a eu lieu et
aucun audit `DIVERGENCE_REJECTED` n’a été persisté.

Une reproduction hors ligne montre que l’affectation d’un `MediaTypeHeaderValue` sérialise :

```text
application/vnd.betting-project.j7-canonical-event+json; version=1.0
```

alors que le contrat exige exactement :

```text
application/vnd.betting-project.j7-canonical-event+json;version=1.0
```

Le receiver applique une comparaison textuelle stricte avant son service d’import. Le rejet
`400/INVALID_CONTENT_TYPE` est donc une inférence de chemin de code à forte confiance, pas une
observation runtime R3.

## 3. Objectif et périmètre strict

WO-039 doit :

1. construire une requête HTTP de sonde dont les octets d’en-tête contiennent exactement le média
   type contractuel, sans espace ajouté ni canonicalisation implicite ;
2. prouver hors ligne les octets effectivement préparés pour le fil, le nombre exact d’en-têtes,
   leur ordre borné, `Content-Length` et l’identité byte-exacte du corps ;
3. conserver le transport HTTPS/mTLS, la cible littérale `127.0.0.1:8444`, l’absence de proxy,
   redirection et retry, et une limite stricte de dix secondes ;
4. lire la réponse dans une enveloppe bornée et n’en extraire que le statut HTTP et un code de
   ProblemDetail sûr ; aucun corps ou en-tête brut ne doit être persisté ou journalisé ;
5. préserver la claim avant envoi, son caractère consommé en succès comme en résultat inconnu, et
   le plafond procédural de trois appels de WO-036 ;
6. qualifier, au besoin contre INT-001 exclusivement loopback et avec un corpus synthétique neuf,
   l’obtention de `409` et d’un unique audit `DIVERGENCE_REJECTED`, sans nouveau receipt, payload
   ou outbox ;
7. produire un rapport autonome expurgé et soumettre le résultat à la revue propriétaire.

Le changement reste limité à `scripts/wo036`, ses tests Pester et la documentation WO-039. Le
sender Java, les migrations, le receiver INT-001 et le contrat J7 restent byte-identiques.

## 4. Invariants et interdictions

```text
REQUEST_MEDIA_TYPE=application/vnd.betting-project.j7-canonical-event+json;version=1.0
ACK_MEDIA_TYPE=application/vnd.betting-project.j7-delivery-ack+json;version=1.0
NETWORK_SCOPE=127.0.0.1_ONLY
AUTOMATIC_RETRY=0
REDIRECTS=NEVER
PROXY=DISABLED
REQUEST_TIMEOUT_SECONDS=10
MAXIMUM_IMPORT_ROUTE_CALLS=3
RAW_RESPONSE_PERSISTED=NO
RAW_RESPONSE_LOGGED=NO
LOCAL_LAB_JAVA_SENDER_CHANGE=NO
RECEIVER_RUNTIME_CHANGE=NO
PROTOCOL_RELAXATION=NO
CONTENT_TYPE_CONTRACT_CHANGE=NO
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

La qualification hors ligne doit établir au minimum :

- le média type exact apparaît une seule fois dans les octets d’en-tête ;
- la variante normalisée avec espace n’apparaît jamais ;
- les en-têtes obligatoires sont uniques, exempts de CR/LF injecté et corrélés à l’export mutant ;
- `Content-Length` correspond exactement au corps ;
- les octets du corps restent byte-identiques à la mutation synthétique ;
- la réponse est bornée, le statut strictement parsé et seul un code sûr est conservable ;
- réponse malformée, surdimensionnée, ambiguë ou tronquée : arrêt fail-closed ;
- exception avant ou après envoi : claim consommée, zéro retry ;
- aucun appel fournisseur ou cible non loopback n’est possible.

La qualification loopback optionnelle doit utiliser un receiver INT-001 inchangé, une PKI
éphémère, une base isolée et un export entièrement synthétique. Elle doit établir un seul appel
`409`, un audit `DIVERGENCE_REJECTED` exactement corrélé, et aucun second receipt, payload ou
outbox. Toutes ses ressources doivent être supprimées ensuite.

Les contrôles de dépôt comprennent les tests Pester ciblés, `mvnw.cmd clean verify`,
`mvnw.cmd -Pintegration-tests verify`, `git diff --check`, UTF-8, secrets, loopback, flags
bloquants et absence de modification du sender Java, des migrations et du receiver.

## 6. Critères de sortie

WO-039 ne peut être proposé à la validation que si :

- la correction est bornée au harnais WO-036 ;
- les preuves hors ligne et, si exécutée, loopback sont `PASS_LOCAL_FAIL_CLOSED` ;
- le statut et le code sûr d’un échec peuvent être conservés sans contenu brut ;
- le média type contractuel et le receiver n’ont pas été assouplis ;
- tous les tests et contrôles de sécurité sont verts ;
- un rapport autonome est versionné et référencé par son SHA-256.

Même validé, WO-039 ne reprend pas WO-036. R4 exigera une décision propriétaire séparée, un run
neuf et un manifeste neuf gelé avant le premier POST.

```text
WORK_ORDER_STATUS=ACTIVE
IMPLEMENTATION_STATUS=AUTHORIZED_PENDING
QUALIFICATION_STATUS=PENDING
OWNER_REVIEW_REQUIRED=YES
WORK_ORDER_MOVE_TO_COMPLETED=NO
WO036_STATUS=STOPPED_AFTER_CONSUMED_COLLISION_PROBE_PENDING_WO039
WO036_RESUME_AUTHORIZED=NO
WO036_NEXT_FRESH_RUN=R4
J9_OFFICIAL_PERMISSION_STATUS=NOT_EVIDENCED
PROVIDER_DERIVED_REAL_DELIVERY_AUTHORIZED=NO
PROVIDER_NETWORK_AUTHORIZED=NO
REAL_RECEIVER_NETWORK_AUTHORIZED=NO
REMOTE_RECEIVER_NETWORK_AUTHORIZED=NO
LIVE_DELIVERY_AUTHORIZED=NO
VPS_DEPLOYMENT_AUTHORIZED=NO
PRODUCTION_AUTHORIZED=NO
```

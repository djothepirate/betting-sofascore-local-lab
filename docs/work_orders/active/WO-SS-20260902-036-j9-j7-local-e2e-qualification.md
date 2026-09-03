# WO-SS-20260902-036 — Qualification E2E J7 locale Windows/Windows

- **Statut :** `RESUME_R6_AUTHORIZED_PENDING_FRESH_MANIFEST_AND_PREFLIGHT`
- **Jalon :** après J9 — qualification synthétique de `OPTIONAL_LOCAL_PUSH`
- **Ouvert le :** 2026-09-02
- **Ouverture UTC :** `2026-09-02T19:08:46.1623419Z`
- **Ouverture Europe/Paris :** `2026-09-02T21:08:46.1623419+02:00`
- **Branche :** `codex/j9-wo036-j7-local-e2e`
- **Worktree :** `.tmp/j9-wo036-j7-local-e2e`
- **Base locale d’ouverture vérifiée :** `aa405c6750062b9df9312f0845188f48f4c778de`
- **Sender Local Lab qualifié :** `f5a27887b7db43576eb608d564c245c8cca3a602`
- **Correctif navigateur validé :** `f28e4b6954c0fb703923f770ab9156326e212a07`
- **Clôture WO-037 intégrée par fast-forward :** `9a10447d0c94e441b860b942e436e2c943f1e2c1`
- **Reprise R2 autorisée UTC :** `2026-09-03T10:10:23Z`
- **Reprise R2 autorisée Europe/Paris :** `2026-09-03T12:10:23+02:00`
- **Correctif ACK validé :** `3a0c297a5151c572417b4f2f12bb5c3ed216172f`
- **Clôture WO-038 intégrée par fast-forward :** `a2d44150af6ad6a7931e29d6291a7df711e171e9`
- **Reprise R3 autorisée UTC :** `2026-09-03T13:02:58.9483990Z`
- **Reprise R3 autorisée Europe/Paris :** `2026-09-03T15:02:58.9483990+02:00`
- **Clôture WO-039 intégrée par fast-forward :** `13266e81829f0272adcd2d4c60734f562a27f0b1`
- **Reprise R4 autorisée UTC :** `2026-09-03T16:37:32.7385871Z`
- **Reprise R4 autorisée Europe/Paris :** `2026-09-03T18:37:32.7385871+02:00`
- **Clôture WO-040 intégrée par fast-forward :** `9cf14180404efe899abeb6a7f3f3d6b7f1e6a028`
- **Reprise R5 autorisée UTC :** `2026-09-03T19:05:02.0860147Z`
- **Reprise R5 autorisée Europe/Paris :** `2026-09-03T21:05:02.0902057+02:00`
- **Clôture WO-041 intégrée par fast-forward :** `d1fdf04615994d1ef3e0ad86a5c3d389002e813e`
- **Reprise R6 autorisée UTC :** `2026-09-03T20:49:33.1647233Z`
- **Reprise R6 autorisée Europe/Paris :** `2026-09-03T22:49:33.1647233+02:00`
- **Receiver INT-001 observé :** `b6a093ab4d3358f23a59b65b68a3eb720494bcba`
- **Implémentation receiver observée :** `3920a58c122cbee0fb379781abcd53d3eaa0f70d`
- **Base CAT-002 du receiver :** `85dc943601a7d33d37ca69a0c913bf32f1162811`
- **Work Order parent :** `WO-SS-20260902-035-j9-real-j7-delivery-sender` — `VALIDATED`
- **ADR :** `ADR-SS-003 v0.1` — `ACCEPTED`
- **Permission officielle :** `NOT_EVIDENCED`

## 1. Autorisation et périmètre

Le propriétaire autorise l’ouverture et la réalisation de ce Work Order exclusivement pour une
campagne E2E synthétique Windows/Windows. Cette décision autorise les connexions loopback entre les
deux applications réelles et leurs deux bases de campagne isolées. Elle n’autorise aucune donnée
dérivée de SofaScore, aucun appel fournisseur, receiver distant, VPS ou production.

```text
WORK_ORDER=WO-SS-20260902-036-j9-j7-local-e2e-qualification
WORK_ORDER_STATUS=RESUME_R6_AUTHORIZED_PENDING_FRESH_MANIFEST_AND_PREFLIGHT
BRANCH=codex/j9-wo036-j7-local-e2e

RESUME_RUN=R6
RESUME_AUTHORIZATION=GRANTED_NOT_YET_CONSUMED
LOCAL_SYNTHETIC_WINDOWS_E2E_AUTHORIZED=YES
LOCAL_INT001_RECEIVER_LOOPBACK_AUTHORIZED=YES_SYNTHETIC_ONLY
FRESH_RUN_COMPLETED=NO
FRESH_RUN_RESULT=NOT_STARTED
FRESH_MANIFEST_STATUS=REQUIRED_NEW_AND_FROZEN_BEFORE_FIRST_POST
FRESH_MANIFEST_PATH=docs/validation/J9-WO036-J7-LOCAL-E2E-CAMPAIGN-MANIFEST-RESUME-R6-20260903.md
LOCAL_SYNTHETIC_DATA_ONLY=YES
REMOTE_RECEIVER_NETWORK_AUTHORIZED=NO

J9_OFFICIAL_PERMISSION_STATUS=NOT_EVIDENCED
PROVIDER_DERIVED_REAL_DELIVERY_AUTHORIZED=NO
PROVIDER_NETWORK_AUTHORIZED=NO
VPS_DEPLOYMENT_AUTHORIZED=NO
PRODUCTION_AUTHORIZED=NO

INT001_PULL_REQUEST_AUTHORIZED_BY_THIS_WORK_ORDER=NO
INT001_VALIDATION_AUTHORIZED_BY_THIS_WORK_ORDER=NO
```

La valeur runtime temporaire `RECEIVER_QUALIFICATION=PASS` sera limitée au processus Local Lab de
campagne. Elle signifie uniquement qu’un préflight ciblé du receiver exact a réussi pour cette
preuve synthétique ; elle ne constitue ni la validation propriétaire d’INT-001 ni la clôture de son
Work Order.

## 2. Topologie immuable de campagne

```text
LOCAL_LAB_UI=http://127.0.0.1:8087
LOCAL_LAB_POSTGRES=127.0.0.1:5432
BETTING_PROJECT_RECEIVER=https://127.0.0.1:8444
BETTING_PROJECT_POSTGRES=127.0.0.1:5433
BETTING_PROJECT_SERVER_ADDRESS=127.0.0.1
MTLS_CLIENT_AUTH=NEED
```

La campagne utilise deux processus Java réels, deux frontières PostgreSQL réelles et distinctes,
une PKI locale éphémère et des répertoires de campagne hors Git. Les deux bases Local Lab A et B
sont isolées de toute base primaire. B est copiée depuis A lorsque l’export est `HUMAN_VALIDATED`
mais avant toute tentative de livraison. Aucun état terminal du ledger n’est effacé, réarmé ou
modifié artificiellement.

Les certificats privés, mots de passe, fichiers PKCS12, exports J7, ACK bruts et résultats SQL bruts
restent hors dépôt et hors documentation. Les installations dans `CurrentUser\\My` et
`CurrentUser\\Root` sont bornées aux objets créés par la campagne et supprimées après vérification
d’identité et de propriété.

## 3. Préconditions et portes d’arrêt

Avant toute ouverture de socket :

1. le présent Work Order et ses mises à jour documentaires sont commités localement ;
2. le worktree WO-036 et le worktree INT-001 sont propres et leurs commits correspondent aux
   valeurs enregistrées ;
3. Java 25, PowerShell 7, `keytool`, Docker Desktop et la CLI Docker sont disponibles ;
4. les ports `8087`, `8444`, `5432` et `5433` ne sont pas occupés par un service étranger ;
5. la configuration Compose des deux dépôts est valide et ne publie que sur `127.0.0.1` ;
6. les tests et qualifications ciblés sender/receiver sont verts ;
7. tous les flags fournisseur restent faux ou vides ;
8. les chemins temporaires, volumes, bases et certificats sont neufs et attribuables sans ambiguïté
   à WO-036.

La campagne s’arrête sans envoi en cas de dépendance manquante, conflit de port, listener non
loopback, provenance autre que `SYNTHETIC_ONLY`, configuration mTLS ambiguë, secret affiché,
tentative de retry automatique, état de livraison inconnu, erreur de persistance, divergence de
commit ou impossibilité de garantir le nettoyage exact.

## 4. Corpus et gestes opérateur réels

Le corpus est construit exclusivement par les parcours synthétiques locaux existants :

1. charger la démonstration J4 du `2026-08-12` ;
2. importer hors ligne les trois familles J5 de l’événement canonique
   `9740bb59-0207-31a3-a6ae-5c8463255887` ;
3. créer le candidat J7, inspecter ses cinq sources et le valider avec la phrase exacte ;
4. vérifier `HUMAN_VALIDATED` et `SYNTHETIC_ONLY` avant toute préparation de livraison ;
5. préparer puis confirmer manuellement la livraison avec
   `LIVRER J7 <exportId> SHA256 <fileSha256>`.

La génération et la validation ne doivent ouvrir aucune socket receiver. La demande de livraison
est liée à la session, à l’export, à ses octets et au prochain ordinal attendu, expire après cinq
minutes et ne peut être consommée qu’une fois.

## 5. Protocole déterministe

### 5.1 Première importation — `201/IMPORTED`

- exécuter le geste UI sur la base Local Lab A ;
- obtenir `DELIVERED`, tentative `1`, sans retry ;
- vérifier une inbox, un payload byte-identique, un audit d’acceptation et exactement une outbox
  `J7_IMPORT_ACCEPTED` au statut `PENDING`.

### 5.2 Duplicate — `200/DUPLICATE`

- arrêter proprement A ;
- redémarrer le Local Lab sur la base B, copiée avant le claim de A, avec la même racine export ;
- exécuter le premier et unique geste UI de B pour les mêmes octets ;
- obtenir `DUPLICATE_CONFIRMED`, tentative `1`, sans créer une seconde inbox ou outbox.

Cette méthode respecte l’interdiction du ledger de réclamer une nouvelle livraison après un état
terminal. Il est interdit de produire le duplicate en supprimant, réinitialisant ou altérant un
ledger `DELIVERED`.

### 5.3 Collision — `409`

Le sender fail-closed ne peut produire un contenu divergent avec la même identité. La collision est
donc exercée par un probe mTLS de campagne, local et synthétique, distinct du sender runtime : une
copie temporaire de l’enveloppe change uniquement une métadonnée autorisée hors `data`, conserve le
même `exportId`, recalcule le hash fichier et la clé d’idempotence, puis effectue un unique POST.
Le receiver doit répondre `409` sans nouvelle inbox, payload ou outbox. Le corps mutant est supprimé
immédiatement après la capture de ses seules métadonnées autorisées.

## 6. Preuves attendues

- `201/IMPORTED`, puis `200/DUPLICATE`, puis collision valide `409` ;
- égalité de taille et SHA-256 entre le fichier J7 validé et le `bytea` de l’inbox ;
- identité `exportId`, `fileSha256`, `dataSha256`, schema ID/version et rétention concordantes ;
- une seule inbox, un seul payload et une seule outbox `J7_IMPORT_ACCEPTED/PENDING` ;
- audits receiver corrélés aux trois résultats, sans payload brut conservé ;
- ledgers A et B respectivement `DELIVERED/1` et `DUPLICATE_CONFIRMED/1` ;
- aucun appel fournisseur et aucun mécanisme automatique ;
- aucune donnée réelle, PII, clé privée, secret, empreinte de certificat ou ACK brut dans Git ou le
  rapport ;
- zéro processus, listener, conteneur, volume temporaire, certificat ou répertoire de campagne
  résiduel après cleanup.

## 7. État du receiver INT-001

Le receiver utilisé demeure formellement `IN_PROGRESS` et `NOT_QUALIFIED`. Son implémentation est
`PASS_LOCAL_FAIL_CLOSED_PENDING_CRYPTOGRAPHIC_BACKUP_RESTORE`; la sauvegarde/restauration chiffrée
réelle et la validation Compose ne sont pas acquises dans ses preuves actuelles. WO-036 ne les
transforme pas silencieusement en acquis et ne modifie pas le dépôt Betting Project. La future PR
INT-001 reste la troisième étape distincte demandée par le propriétaire.

## 8. Vérifications et sortie

- `mvnw.cmd clean verify` et `mvnw.cmd -Pintegration-tests verify` dans le Local Lab ;
- qualifications ciblées puis vérifications standard/intégration dans INT-001, sans modification ;
- `docker compose --env-file .env config` sur les environnements isolés ;
- contrôle des secrets, de l’UTF-8, de `git diff --check`, des adresses loopback et des flags
  bloquants ;
- capture expurgée des versions, commits, compteurs, statuts, tailles et hashes ;
- arrêt des applications et des bases, retrait exact de la PKI et suppression confinée des seuls
  artefacts WO-036 ;
- contrôle final de l’absence de listener et de processus résiduel.

Le Work Order restera actif après la campagne jusqu’à production du rapport autonome et décision
propriétaire. Aucun push, merge, PR INT-001, réseau fournisseur ou distant, livraison dérivée, VPS
ou production ne découle de sa réussite.

## 9. Qualification du harnais avant premier POST

Le 2026-09-03, deux préparations ont été arrêtées et nettoyées avant le gel du manifeste et avant
tout appel de la route d’import :

1. le chemin `javapath` résolu depuis le `PATH` lançait le véritable Java 25 dans un processus
   enfant ; le contrôle de propriété du listener a refusé cette indirection. La reprise impose le
   chemin absolu du binaire Java 25 qualifié ; aucun relâchement de la preuve PID, de l’exécutable,
   du marqueur d’instance, de la ligne de commande ou de l’heure de démarrage n’a été introduit ;
2. le premier gel complet a révélé que `File.ReadAllBytes` ne pouvait pas ouvrir un journal encore
   détenu par `Start-Process -RedirectStandardOutput/-RedirectStandardError` sous Windows. Le
   scanner ouvre désormais un snapshot en lecture avec `FileShare.ReadWrite`, borne chaque fichier
   à 50 MiB et l’ensemble à 100 MiB, exige la même longueur avant et après la lecture, puis conserve
   l’effacement des buffers et tous les contrôles de contenu, d’ACL, de confinement et de noms.

Le correctif du scanner est qualifié par des tests comportementaux couvrant un journal actif
partageable, un writer exclusif refusé en échec fermé et le vrai mécanisme Windows
`Start-Process -RedirectStandard*`. Chaque préparation abandonnée a été suivie d’un cleanup exact :
zéro processus, listener, conteneur, volume ou certificat résiduel, aucune atteinte à la base
primaire et aucun accès fournisseur.

```text
IMPORT_ROUTE_CALLS_BEFORE_HARNESS_REQUALIFICATION=0
PROVIDER_CALLS=0
REMOTE_RECEIVER_CALLS=0
PROVIDER_DERIVED_PAYLOADS=0
VPS_DEPLOYMENTS=0
CAMPAIGN_RESTART_REQUIRED_AFTER_CLEAN_BUILD=YES
```

## 10. Résultat de la campagne figée

Le run issu du manifeste gelé au commit `c6a1e0a4f8fbf57279344d24d0cb0e521d315d9d`
s'est arrêté avant le premier appel de la route d'import. Un navigateur Brave réel a soumis le
formulaire `Préparer la livraison` avec le Host loopback exact mais `Origin: null`. La frontière
locale a répondu `403` avant contrôleur. Une seconde soumission native, toujours sans possibilité
d'atteindre le receiver, a confirmé la même classification dans DevTools sans export de HAR,
cookie, jeton ou corps de formulaire.

La cause est une incompatibilité interne : `SecurityHeadersFilter` émet
`Referrer-Policy: no-referrer`, ce qui produit normativement `Origin: null` pour ce `POST` de
navigation, tandis que `J7DeliveryLocalRequestBoundaryInterceptor` n'accepte qu'un Origin absent
ou exactement égal à `http://127.0.0.1:8087`. Les tests existants injectent directement ces deux
cas acceptés et ne reproduisent pas la chaîne d'un navigateur réel.

Le [rapport autonome](../../validation/J9-WO036-J7-LOCAL-E2E-CAMPAIGN-20260903.md) consigne la
preuve expurgée. Les bases confirment zéro tentative sender et zéro receipt, payload, audit ou
outbox receiver. Local Lab A et le receiver ont été arrêtés gracieusement ; B et la collision n'ont
pas démarré. Le cleanup exact a supprimé tous les processus, listeners, conteneurs, volumes,
certificats et fichiers privés de campagne, puis le conteneur PostgreSQL primaire exact a été
redémarré `healthy` avec la même identité, le même volume RW et le bind `127.0.0.1:5432`.

```text
WO036_EVIDENCE_RESULT=STOPPED
WO036_STOP_CLASS=LOCAL_BROWSER_BOUNDARY_INCOMPATIBILITY_PRE_RECEIVER
WO036_REPORT_ID=J9-WO036-J7-LOCAL-E2E-CAMPAIGN-20260903
WO036_REPORT_SHA256=5244422187f0bc591e3ce57f20b076037f64bad266ad9052b985d8127b475994
LOCAL_NATIVE_BROWSER_PREPARE_SUBMISSIONS=2
LOCAL_PREPARE_HTTP_STATUS=403
HOST_CLASS=EXACT_127_0_0_1_8087
ORIGIN_CLASS=NULL
FORWARDED_HEADERS_PRESENT=NO
SENDER_DURABLE_ATTEMPTS=0
RECEIVER_IMPORT_ROUTE_CALLS=0
RECEIVER_DURABLE_OBJECTS=0
AUTOMATIC_RETRIES=0
PROVIDER_CALLS=0
REMOTE_RECEIVER_CALLS=0
PRIVATE_LOG_REDACTION=PASS
CAMPAIGN_CLEANUP=PASS_ZERO_RESIDUE
PRIMARY_POSTGRES_RESTART=PASS_EXACT_HEALTHY
POST_CAMPAIGN_CLEAN_VERIFY=PASS_1115_TESTS_0_FAILURES_0_ERRORS_5_SKIPPED
POST_CAMPAIGN_INTEGRATION_VERIFY=PASS_85_TESTS_0_FAILURES_0_ERRORS_0_SKIPPED
POST_CAMPAIGN_VERIFY_LOCAL=PASS_NO_PROVIDER_CALL
```

## 11. Décision propriétaire requise après le premier essai

WO-036 reste actif ; il ne peut pas être déplacé vers `completed` et sa série ne peut pas être
reprise avec le manifeste courant. Un correctif runtime distinct doit d'abord réconcilier la
politique de referrer et la frontière d'origine sans accepter une origine opaque, puis être
qualifié avec un vrai navigateur loopback. Après sa validation, une reprise de WO-036 exigera une
nouvelle décision propriétaire et un nouveau manifeste lié au nouveau binaire.

Le numéro `037` et la branche correspondante ont été contrôlés comme disponibles au moment du
constat, sans constituer à eux seuls une autorisation d'ouverture :

```text
J9_WO037_OWNER_DECISION=<AUTHORIZE_IMPLEMENTATION|DENY>
J9_WO037_WORK_ORDER=WO-SS-20260903-037-j9-j7-browser-origin-boundary
J9_WO037_SCOPE=DIAGNOSE_CORRECT_AND_QUALIFY_NATIVE_BROWSER_REFERRER_POLICY_AND_LOCAL_ORIGIN_BOUNDARY_COMPATIBILITY
J9_WO037_LOOPBACK_BROWSER_QUALIFICATION_AUTHORIZED=<YES|NO>
J9_WO037_PREFERRED_DIRECTION=SCOPE_SAME_ORIGIN_REFERRER_POLICY_TO_J7_DELIVERY_WITHOUT_ACCEPTING_OPAQUE_ORIGINS

J9_WO036_STATUS=STOPPED_PRE_RECEIVER_PENDING_DISTINCT_RUNTIME_CORRECTION
J9_WO036_RESUME_AFTER_WO037_VALIDATION=REQUIRES_SEPARATE_OWNER_DECISION
J9_WO036_WORK_ORDER_MOVE_TO_COMPLETED=NO
J9_PROVIDER_NETWORK_AUTHORIZED=NO
REAL_RECEIVER_NETWORK_AUTHORIZED=NO
J9_VPS_DEPLOYMENT_AUTHORIZED=NO
J9_PRODUCTION_AUTHORIZED=NO
```

## 12. Autorisation propriétaire de reprise — état historique pré-exécution R2

La section précédente reste le constat historique du premier essai. La présente section fige l’état
pré-exécution de R2 ; les valeurs d’autorisation qu’elle reproduit ont depuis été consommées par le
run arrêté décrit en section 13 et ne représentent plus l’autorité courante. Le manifeste et le
rapport du premier essai demeurent gelés et ne sont ni modifiés ni réinterprétés. WO-037 avait
corrigé la politique de referrer sans accepter d’origine opaque, puis avait été qualifié et validé.
Le propriétaire avait ensuite transmis la décision distincte suivante :

```text
J'autorise la reprise de la WO-036
```

Cette phrase est enregistrée le `2026-09-03T10:10:23Z`, soit
`2026-09-03T12:10:23+02:00` en Europe/Paris. Son effet est borné au périmètre synthétique
Windows/Windows déjà approuvé. La branche WO-036 a été avancée linéairement jusqu’au commit de
clôture WO-037 `9a10447d0c94e441b860b942e436e2c943f1e2c1`, sans rebase ni réécriture.

Les preuves historiques restent identifiées par :

```text
PREVIOUS_MANIFEST_REFERENCE=docs/validation/J9-WO036-J7-LOCAL-E2E-CAMPAIGN-MANIFEST-20260902.md
PREVIOUS_MANIFEST_COMMIT=c6a1e0a4f8fbf57279344d24d0cb0e521d315d9d
PREVIOUS_MANIFEST_SHA256=e996e631fdc905c91b8288852fbba7b878023985db7d5bdd228dccdaeefbe960
PREVIOUS_STOPPED_REPORT_REFERENCE=docs/validation/J9-WO036-J7-LOCAL-E2E-CAMPAIGN-20260903.md
PREVIOUS_STOPPED_REPORT_COMMIT=f4f53aa24b4a8a764819bdb7cf79c87482bd7244
PREVIOUS_STOPPED_REPORT_SHA256=5244422187f0bc591e3ce57f20b076037f64bad266ad9052b985d8127b475994
PRE_RESUME_WORK_ORDER_SHA256=d9247d05f9a168492e529fddfb72125db6a560f081b2d5b929bddcd11ed6b853
```

La porte WO-037 acquise est liée par :

```text
WO037_STATUS=VALIDATED
WO037_QUALIFICATION_RESULT=PASS_LOCAL_FAIL_CLOSED
WO037_RUNTIME_COMMIT=f28e4b6954c0fb703923f770ab9156326e212a07
WO037_QUALIFIED_HARNESS_COMMIT=2596f0592496b2ba84893c8f4ef2cf0185d46f8f
WO037_DOCUMENTATION_COMMIT=24739d03d33801a8a6c8d2fe956e1dc254bb3d57
WO037_CLOSURE_COMMIT=9a10447d0c94e441b860b942e436e2c943f1e2c1
WO037_REPORT_REFERENCE=docs/validation/J9-WO037-J7-BROWSER-ORIGIN-BOUNDARY-QUALIFICATION-20260903.md
WO037_REPORT_SHA256=db8993328643a0ecb39eb83ff9eab6223f6c6ca6605b4cacf3e96dea171fab01
WO037_COMPLETED_WORK_ORDER_REFERENCE=docs/work_orders/completed/WO-SS-20260903-037-j9-j7-browser-origin-boundary.md
WO037_COMPLETED_WORK_ORDER_SHA256=dc76113dc9e6439a5e0b6b5ad8f8e8db5b2d5fe4226163681a62aaf3fe064b93
```

Cette décision exigeait qu’un nouveau run complet reparte de zéro, emploie un nouveau répertoire
privé, une PKI et des bases isolées neuves, reconstruise les deux JAR, enregistre leur identité et
gèle avant le premier POST le manifeste distinct suivant :

```text
docs/validation/J9-WO036-J7-LOCAL-E2E-CAMPAIGN-MANIFEST-RESUME-20260903.md
```

L’ancien manifeste et le rapport d’arrêt restent immuables. Le bloc suivant représente exclusivement
l’état autorisé avant exécution de R2 ; la section 13 porte l’état courant.

```text
PRE_R2_AUTHORIZATION_STATE=HISTORICAL_CONSUMED
J9_WO036_OWNER_RESUME_DECISION=AUTHORIZE
J9_WO036_STATUS=RESUME_AUTHORIZED_PENDING_FRESH_MANIFEST_AND_PREFLIGHT
J9_WO036_RESUME_AFTER_WO037_VALIDATION=AUTHORIZED_BY_SEPARATE_OWNER_DECISION
J9_WO036_FRESH_RUN_REQUIRED=YES
J9_WO036_FRESH_MANIFEST_REQUIRED=YES
J9_WO036_WORK_ORDER_MOVE_TO_COMPLETED=NO
J9_LOCAL_SYNTHETIC_WINDOWS_E2E_AUTHORIZED=YES
J9_LOCAL_INT001_RECEIVER_LOOPBACK_AUTHORIZED=YES_SYNTHETIC_ONLY
J9_PROVIDER_DERIVED_REAL_DELIVERY_AUTHORIZED=NO
J9_PROVIDER_NETWORK_AUTHORIZED=NO
REAL_RECEIVER_NETWORK_AUTHORIZED=NO
REMOTE_RECEIVER_NETWORK_AUTHORIZED=NO
J9_VPS_DEPLOYMENT_AUTHORIZED=NO
J9_PRODUCTION_AUTHORIZED=NO
```

## 13. Résultat de la campagne de reprise R2

Le manifeste distinct a été gelé au commit
`07fd440a6773526aeb2b28e7c43413023967f6d1`, puis la campagne neuve a été exécutée avec un export
J7 entièrement synthétique. Le [rapport autonome de reprise](../../validation/J9-WO036-J7-LOCAL-E2E-CAMPAIGN-RESUME-STOP-20260903.md),
SHA-256 `219f54f2b429b1eaea04c920e55cc87d33f9c5644697129e6ff96fc9cad19062`, consigne le résultat
`STOPPED`.

Le premier appel a produit `201/IMPORTED` et l’état local `DELIVERED`. Le receiver a traité le
second appel byte-identique comme le duplicate durable attendu : `200/DUPLICATE`, un seul receipt,
un seul payload, un seul outbox et deux audits au total. Le sender a néanmoins classé ce second
résultat `UNKNOWN_RECONCILIATION_REQUIRED` avec `ACK_HTTP_STATUS_MISMATCH`, car il impose au
`receivedAt` initial réemployé par le receiver d’être postérieur au début du second claim.

La cause est déterministe et se situe dans l’invariant temporel du sender Local Lab. Le receiver
INT-001 est conforme à son contrat, qui impose au duplicate de réemployer le `remoteImportId` et le
`receivedAt` du premier import durable. Aucun retry ni probe de collision `409` n’a été effectué.
Les applications ont été arrêtées gracieusement, les ressources de campagne supprimées sans résidu
et le conteneur PostgreSQL primaire exact redémarré `healthy` sans recréation ni purge.

```text
J9_WO036_STATUS=STOPPED_AFTER_DUPLICATE_ACK_PENDING_DISTINCT_RUNTIME_CORRECTION
J9_WO036_RESUME_RUN_RESULT=STOPPED
J9_WO036_STOP_CLASS=LOCAL_LAB_DUPLICATE_ACK_RECEIPT_TIME_SEMANTICS_MISMATCH
J9_WO036_RECEIVER_SEQUENCE=201_IMPORTED,200_DUPLICATE
J9_WO036_LOCAL_LEDGER_SEQUENCE=DELIVERED,UNKNOWN_RECONCILIATION_REQUIRED
J9_WO036_IMPORT_ROUTE_CALLS=2
J9_WO036_MAXIMUM_IMPORT_ROUTE_CALLS=3
J9_WO036_COLLISION_PROBE=NOT_PERFORMED_AFTER_GLOBAL_STOP
J9_WO036_AUTOMATIC_RETRIES=0
J9_WO036_RUNTIME_CORRECTION_REQUIRED=YES
J9_WO036_RESUME_AFTER_RUNTIME_CORRECTION=REQUIRES_SEPARATE_OWNER_DECISION
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

WO-036 reste actif. Aucun correctif runtime n’est apporté par cette campagne. Le prochain numéro
disponible vérifié est `038`, mais son ouverture et son implémentation exigent une autorisation
propriétaire distincte. Après validation de ce correctif, toute nouvelle reprise de WO-036 exigera
encore une décision séparée, un run neuf et un manifeste neuf.

## 14. Autorisation propriétaire de reprise R3 — état historique pré-exécution

Les sections précédentes conservent les constats historiques R1 et R2. Leurs manifestes et rapports
restent immuables. WO-038 a depuis corrigé la frontière temporelle du sender, obtenu
`PASS_LOCAL_FAIL_CLOSED`, puis été validé et déplacé vers les Work Orders terminés. La branche
WO-036 a été avancée linéairement jusqu’au commit de clôture WO-038
`a2d44150af6ad6a7931e29d6291a7df711e171e9`, sans rebase ni réécriture.

Le propriétaire transmet la décision distincte suivante :

```text
J'autorise la reprise de WO-036
```

La décision est enregistrée le `2026-09-03T13:02:58.9483990Z`, soit le
`2026-09-03T15:02:58.9483990+02:00` en Europe/Paris. Son effet est borné au périmètre déjà défini de
WO-036 : campagne Windows/Windows intégralement synthétique, receiver INT-001 local sur loopback,
maximum de trois appels ordonnés `201/200/409`, sans retry. Elle ne vaut ni permission officielle,
ni livraison de données dérivées, ni réseau fournisseur ou distant, ni VPS, production, push,
fusion ou validation d’INT-001.

Les portes acquises et preuves historiques sont liées par :

```text
WO038_STATUS=VALIDATED
WO038_IMPLEMENTATION_COMMIT=3a0c297a5151c572417b4f2f12bb5c3ed216172f
WO038_CLOSURE_COMMIT=a2d44150af6ad6a7931e29d6291a7df711e171e9
WO038_COMPLETED_WORK_ORDER_SHA256=c638a6efed1c9608f00cd65e795150cff15f092d7d17f1b0ca8090cc98873429
WO038_QUALIFICATION_REPORT_SHA256=e51bc537c775b4378ee1c6672f86f6c7c085849d62d51970c3d1b6e4aef1395a
R2_MANIFEST_SHA256=6ef4c4fbe8af1027ff06170bea00aa8ca75c3159bcd21134db2598ddd4b18a78
R2_STOPPED_REPORT_SHA256=219f54f2b429b1eaea04c920e55cc87d33f9c5644697129e6ff96fc9cad19062
R2_EVIDENCE_IMMUTABLE=YES
```

R3 doit employer un nouveau répertoire privé, une PKI éphémère neuve, deux bases isolées neuves,
des JAR reconstruits et enregistrés, puis geler avant le premier POST le manifeste distinct :

```text
docs/validation/J9-WO036-J7-LOCAL-E2E-CAMPAIGN-MANIFEST-RESUME-R3-20260903.md
```

```text
PRE_R3_AUTHORIZATION_STATE=HISTORICAL_CONSUMED
J9_WO036_OWNER_RESUME_DECISION=AUTHORIZE
J9_WO036_RESUME_RUN=R3
J9_WO036_STATUS=RESUME_AUTHORIZED_PENDING_FRESH_MANIFEST_AND_PREFLIGHT
J9_WO036_RESUME_AFTER_WO038_VALIDATION=AUTHORIZED_BY_SEPARATE_OWNER_DECISION
J9_WO036_FRESH_RUN_REQUIRED=YES
J9_WO036_FRESH_MANIFEST_REQUIRED=YES
J9_WO036_WORK_ORDER_MOVE_TO_COMPLETED=NO
J9_LOCAL_SYNTHETIC_WINDOWS_E2E_AUTHORIZED=YES
J9_LOCAL_INT001_RECEIVER_LOOPBACK_AUTHORIZED=YES_SYNTHETIC_ONLY

J9_OFFICIAL_PERMISSION_STATUS=NOT_EVIDENCED
J9_PROVIDER_DERIVED_REAL_DELIVERY_AUTHORIZED=NO
J9_PROVIDER_NETWORK_AUTHORIZED=NO
REAL_RECEIVER_NETWORK_AUTHORIZED=NO
REMOTE_RECEIVER_NETWORK_AUTHORIZED=NO
J9_VPS_DEPLOYMENT_AUTHORIZED=NO
J9_PRODUCTION_AUTHORIZED=NO
```

## 15. Résultat de la campagne R3

### 15.1 Résultat observé

Le manifeste R3 a été gelé avant le premier appel au commit
`f14c1418995625ea653f8d01afd9c81044f3abd1`, SHA-256
`cd20a30cd855d161fcaa0f5db93ef0c50fca5c297d569d0a4af2f4f02aab842d`. La campagne a employé
un nouveau répertoire privé, une PKI éphémère neuve, deux bases Local Lab isolées neuves, le
receiver INT-001 local et les deux JAR enregistrés.

A a produit `201/IMPORTED` et `DELIVERED`. B, créé depuis la base clonée avant le claim de A, a
produit `200/DUPLICATE` et `DUPLICATE_CONFIRMED`, avec un seul receipt, payload et outbox. Ce
résultat confirme WO-038 dans le parcours Windows/Windows réel. Après une preuve pré-collision à
exactement deux appels, la sonde a consommé le troisième et dernier appel, mais reçu une réponse
non-`409` dont le statut exact n'a pas été conservé. Sa claim est
`FAILED_OR_UNKNOWN_CONSUMED`; aucun retry, rejeu ou quatrième appel n'a eu lieu. Aucun audit
`DIVERGENCE_REJECTED` n'a été créé : la séquence `201/200/409` n'est pas qualifiée.

```text
J9_WO036_STATUS=STOPPED_AFTER_CONSUMED_COLLISION_PROBE_PENDING_DISTINCT_TOOLING_RUNTIME_CORRECTION
J9_WO036_R3_RESULT=STOPPED
J9_WO036_R3_MANIFEST_COMMIT=f14c1418995625ea653f8d01afd9c81044f3abd1
J9_WO036_R3_MANIFEST_SHA256=cd20a30cd855d161fcaa0f5db93ef0c50fca5c297d569d0a4af2f4f02aab842d
J9_WO036_R3_RECEIVER_SEQUENCE=201_IMPORTED,200_DUPLICATE,NON_409
J9_WO036_R3_LOCAL_LEDGER_SEQUENCE=DELIVERED,DUPLICATE_CONFIRMED
J9_WO036_R3_IMPORT_ROUTE_CALLS=3
J9_WO036_R3_MAXIMUM_IMPORT_ROUTE_CALLS=3
J9_WO036_R3_PRE_COLLISION_EVIDENCE=PASS_EXACTLY_TWO_CALLS
J9_WO036_R3_COLLISION_PROBE=FAILED_OR_UNKNOWN_CONSUMED
J9_WO036_R3_COLLISION_HTTP_STATUS=UNKNOWN_NON_409
J9_WO036_R3_COLLISION_409=NOT_OBSERVED
J9_WO036_R3_CONTRACT_SEQUENCE_QUALIFIED=NO
J9_WO036_R3_AUTOMATIC_RETRIES=0
J9_WO036_R3_PROVIDER_CALLS=0
J9_WO036_R3_REMOTE_RECEIVER_CALLS=0
```

Le [rapport autonome R3](../../validation/J9-WO036-J7-LOCAL-E2E-CAMPAIGN-RESUME-R3-STOP-20260903.md)
porte le SHA-256 `f59cc0aeaa56fd6c8156032fea7e93048f17f616f1882cc3979bcd69363d1576` et consigne la
provenance, les compteurs durables, les preuves expurgées et le cleanup sans exposer de payload,
ACK brut, secret, certificat ou chemin privé.

### 15.2 Analyse de cause distincte des observations

Une reproduction locale sans réseau établit que `MediaTypeHeaderValue`, employé par la sonde
PowerShell, transforme le littéral contractuel en une forme contenant un espace avant `version`.
Le receiver compare la valeur textuelle exacte et mapperait cette différence vers
`400/INVALID_CONTENT_TYPE` avant le service. L'absence d'audit de divergence concorde avec ce
chemin. Le statut et le code n'ayant pas été capturés dans la preuve runtime, ils restent une
inférence à forte confiance et non un résultat observé. Aucune régression du sender Java ou de
WO-038 et aucun défaut du receiver ne sont démontrés. Le futur Work Order doit imposer le résultat
exact sur le fil sans préjuger du mécanisme de correction ni assouplir le contrat.

```text
J9_WO036_R3_PROBE_CAUSE_STATUS=HIGH_CONFIDENCE_CODE_PATH_INFERENCE_NOT_RUNTIME_CAPTURED
J9_WO036_R3_PROBABLE_HTTP_STATUS=400
J9_WO036_R3_PROBABLE_RECEIVER_ERROR=INVALID_CONTENT_TYPE
J9_WO036_R3_RECEIVER_SERVICE_COLLISION_BRANCH_REACHED=NO
J9_WO036_R3_RECEIVER_CHANGE_REQUIRED=NO
J9_WO036_R3_LOCAL_LAB_JAVA_SENDER_CHANGE_REQUIRED=NO
J9_WO036_R3_WO038_REGRESSION_EVIDENCED=NO
J9_WO036_R3_CAMPAIGN_TOOLING_RUNTIME_CHANGE_REQUIRED=YES
```

### 15.3 Arrêt, cleanup et porte suivante

Les deux Local Lab et le receiver ont été arrêtés gracieusement. Le cleanup atteste zéro processus,
conteneur, volume, certificat et listener de campagne résiduel. Le seul `certutil.exe` exactement
possédé a dû être terminé avant le retrait direct du certificat racine éphémère, après vérification
exacte de son identité ; aucune suppression large ou fondée sur le seul PID n'a eu lieu. Le
répertoire privé a été supprimé. Le PostgreSQL primaire exact a été redémarré sans recréation ni
purge et est `healthy`, avec le même volume RW et le même bind `127.0.0.1:5432`.

WO-036 reste actif. Le numéro `039` est disponible, mais tout Work Order de correction, puis toute
reprise R4, exigent leurs décisions propriétaires distinctes.

```text
J9_WO036_RESUME_AUTHORIZED=NO
J9_WO036_WORK_ORDER_MOVE_TO_COMPLETED=NO
J9_WO036_RESUME_AFTER_TOOLING_CORRECTION=REQUIRES_SEPARATE_OWNER_DECISION
J9_WO036_NEXT_FRESH_RUN=R4
J9_WO036_R4_MANIFEST=REQUIRED_NEW_AND_FROZEN_BEFORE_FIRST_POST
J9_LOCAL_INT001_RECEIVER_LOOPBACK_AUTHORIZED=NO_PENDING_NEW_OWNER_DECISION

J9_OFFICIAL_PERMISSION_STATUS=NOT_EVIDENCED
J9_PROVIDER_DERIVED_REAL_DELIVERY_AUTHORIZED=NO
J9_PROVIDER_NETWORK_AUTHORIZED=NO
REAL_RECEIVER_NETWORK_AUTHORIZED=NO
REMOTE_RECEIVER_NETWORK_AUTHORIZED=NO
LIVE_DELIVERY_AUTHORIZED=NO
J9_VPS_DEPLOYMENT_AUTHORIZED=NO
J9_PRODUCTION_AUTHORIZED=NO
INT001_PULL_REQUEST_AUTHORIZED_BY_THIS_WORK_ORDER=NO
INT001_VALIDATION_AUTHORIZED_BY_THIS_WORK_ORDER=NO
PRIMARY_DATABASE_PURGE=NO
```

## 16. Autorisation propriétaire de reprise R4 — état pré-exécution

WO-039 a corrigé et qualifié l'émission byte-exacte de la sonde de collision ainsi que la lecture
bornée des lignes de statut HTTP sans reason phrase. Le propriétaire a validé WO-039, puis a donné
la décision séparée suivante :

```text
Je t'autorise à préparer et exécuter le run neuf R4 de WO-036, avec un nouveau manifeste gelé avant son premier POST.
```

La décision est enregistrée le `2026-09-03T16:37:32.7385871Z`, soit le
`2026-09-03T18:37:32.7385871+02:00` en Europe/Paris. La branche WO-036 a été avancée par
fast-forward jusqu'au commit de clôture WO-039
`13266e81829f0272adcd2d4c60734f562a27f0b1`. R1, R2 et R3 restent immuables.

R4 doit repartir d'un nouveau répertoire privé, d'une PKI éphémère neuve, de bases isolées neuves
et de JAR reconstruits puis enregistrés. Le chemin ci-dessous est le seul changement Git admis
après l'enregistrement one-shot des exécutables et doit être commité avant le premier `POST` :

```text
docs/validation/J9-WO036-J7-LOCAL-E2E-CAMPAIGN-MANIFEST-RESUME-R4-20260903.md
```

```text
J9_WO036_OWNER_RESUME_DECISION=AUTHORIZE
J9_WO036_RESUME_RUN=R4
J9_WO036_STATUS=RESUME_R4_AUTHORIZED_PENDING_FRESH_MANIFEST_AND_PREFLIGHT
J9_WO036_RESUME_AFTER_WO039_VALIDATION=AUTHORIZED_BY_SEPARATE_OWNER_DECISION
J9_WO036_FRESH_RUN_REQUIRED=YES
J9_WO036_FRESH_MANIFEST_REQUIRED=YES
J9_WO036_WORK_ORDER_MOVE_TO_COMPLETED=NO
J9_LOCAL_SYNTHETIC_WINDOWS_E2E_AUTHORIZED=YES
J9_LOCAL_INT001_RECEIVER_LOOPBACK_AUTHORIZED=YES_SYNTHETIC_ONLY
MAXIMUM_IMPORT_ROUTE_CALLS=3
EXPECTED_IMPORT_ROUTE_SEQUENCE=201_IMPORTED,200_DUPLICATE,409_DIVERGENCE_REJECTED
AUTOMATIC_RETRY=0

WO039_STATUS=VALIDATED
WO039_PRIMARY_IMPLEMENTATION_COMMIT=90c1354c97f506b8291fedae80b7dc6ed37e2c11
WO039_RESPONSE_COMPATIBILITY_COMMIT=058c57b05ac4c40e1867af60e76cce6cc2864e67
WO039_DOCUMENTATION_COMMIT=5a41e9ba468aef387e5a37b0f0fe20e7b0c92460
WO039_CLOSURE_COMMIT=13266e81829f0272adcd2d4c60734f562a27f0b1
WO039_QUALIFICATION_REPORT_SHA256=4108f3916f0800c5d1c3616f0c6af866462cda5a188991f15f0305b0ea8d7f50

J9_OFFICIAL_PERMISSION_STATUS=NOT_EVIDENCED
J9_PROVIDER_DERIVED_REAL_DELIVERY_AUTHORIZED=NO
J9_PROVIDER_NETWORK_AUTHORIZED=NO
REAL_RECEIVER_NETWORK_AUTHORIZED=NO
REMOTE_RECEIVER_NETWORK_AUTHORIZED=NO
LIVE_DELIVERY_AUTHORIZED=NO
J9_VPS_DEPLOYMENT_AUTHORIZED=NO
J9_PRODUCTION_AUTHORIZED=NO
INT001_PULL_REQUEST_AUTHORIZED_BY_THIS_WORK_ORDER=NO
INT001_VALIDATION_AUTHORIZED_BY_THIS_WORK_ORDER=NO
PRIMARY_DATABASE_PURGE=NO
```

## 17. Résultat de la campagne R4

### 17.1 Gel préalable et séquence observée

Le manifeste R4 a été gelé avant le premier appel au commit
`e12500dc8af9133b254bfe075d74e979cad472a6`, SHA-256
`ff73efe0f9840941c6b281cdba38e21cda63e379acd0154f673ef8d7624b39ef`. Le contrôle de
l'outillage post-commit a réussi avec zéro appel consommé. La campagne a employé un nouvel export
J7 `HUMAN_VALIDATED` intégralement synthétique, une PKI éphémère neuve, deux bases Local Lab
isolées neuves et le receiver INT-001 local.

A a produit `201/IMPORTED` et `DELIVERED`. B, créée depuis la base clonée avant le claim de A, a
produit `200/DUPLICATE` et `DUPLICATE_CONFIRMED`, avec un seul receipt, payload et outbox. Après
une preuve pré-collision à exactement deux appels, la sonde a consommé le troisième et dernier
appel. Le receiver a persisté l'audit corrélé
`DIVERGENCE_REJECTED/EXPORT_ID_DIVERGENCE`, sans second receipt, payload ou outbox, mais le client
de campagne n'a capturé ni statut HTTP ni code sûr avant de classer la claim
`FAILED_OR_UNKNOWN_CONSUMED`.

```text
J9_WO036_STATUS=STOPPED_AFTER_CONSUMED_R4_COLLISION_RESPONSE_QUALIFICATION_FAILURE
J9_WO036_R4_RESULT=STOPPED
J9_WO036_R4_MANIFEST_COMMIT=e12500dc8af9133b254bfe075d74e979cad472a6
J9_WO036_R4_MANIFEST_SHA256=ff73efe0f9840941c6b281cdba38e21cda63e379acd0154f673ef8d7624b39ef
J9_WO036_R4_RECEIVER_SEQUENCE=201_IMPORTED,200_DUPLICATE,DIVERGENCE_REJECTED_DURABLY_AUDITED
J9_WO036_R4_LOCAL_LEDGER_SEQUENCE=DELIVERED,DUPLICATE_CONFIRMED
J9_WO036_R4_IMPORT_ROUTE_CALLS=3
J9_WO036_R4_MAXIMUM_IMPORT_ROUTE_CALLS=3
J9_WO036_R4_PRE_COLLISION_EVIDENCE=PASS_EXACTLY_TWO_CALLS
J9_WO036_R4_RECEIVER_DIVERGENCE_BUSINESS_EFFECT=PASS
J9_WO036_R4_COLLISION_PROBE=FAILED_OR_UNKNOWN_CONSUMED
J9_WO036_R4_COLLISION_HTTP_STATUS_CLIENT=NOT_CAPTURED
J9_WO036_R4_CONTRACT_SEQUENCE_QUALIFIED=NO
J9_WO036_R4_AUTOMATIC_RETRIES=0
J9_WO036_R4_PROVIDER_CALLS=0
J9_WO036_R4_REMOTE_RECEIVER_CALLS=0
```

Le [rapport autonome R4](../../validation/J9-WO036-J7-LOCAL-E2E-CAMPAIGN-RESUME-R4-STOP-20260903.md),
SHA-256 `16e9f85e12109f2709146312410bbe2a5e040c4f176c2d5596746aa2b70076b5`, consigne la provenance,
les compteurs durables, les preuves expurgées et le cleanup sans exposer de payload, ACK brut,
secret, certificat ou chemin privé.

### 17.2 Analyse bornée et correction d'une hypothèse transitoire

La claim a été acquise à `2026-09-03T17:05:10.0405712Z` et achevée à
`2026-09-03T17:05:10.5797607Z`, soit `539.19 ms`. Cette mesure exclut un timeout de lecture : toute
suggestion transitoire d'un arrêt proche de la limite est corrigée. Le média type, les en-têtes et
le corps ont nécessairement atteint la branche métier, puisque l'audit de divergence est durable.
Le contrat mappe cette branche vers HTTP `409`, mais l'absence de capture côté client interdit de
présenter ce statut comme observé par R4.

La cause exacte du défaut de capture reste `NOT_ESTABLISHED`. Aucun changement du sender Java, du
receiver, du protocole ou de WO-038 n'est justifié par cette preuve. Un Work Order de harnais
distinct doit diagnostiquer et qualifier la lecture de la réponse après l'effet de divergence,
sans réutiliser la claim R4.

### 17.3 Arrêt, cleanup et porte suivante

Les deux Local Lab et le receiver ont été arrêtés gracieusement. Le cleanup atteste zéro processus,
listener, conteneur, volume, réseau ou certificat R4 résiduel. Le répertoire privé a été supprimé.
Le PostgreSQL primaire exact a été redémarré sans recréation ni purge et est `healthy`, avec le même
identifiant, la même image, le même volume, la même politique `unless-stopped` et le même bind
`127.0.0.1:5432`.

Les qualifications post-campagne sont vertes : Pester `62/62`, Local Lab Surefire `1136/0/0/5`
et Failsafe `89/0/0/0` dans les deux parcours Maven, receiver Surefire `297/0/0/0` et Failsafe
`98/0/0/0`. Aucun résidu Testcontainers ne subsiste.

WO-036 reste actif. L'autorisation R4 et ses trois appels sont consommés. Une correction de harnais,
sa validation, une nouvelle décision propriétaire, un run R5 neuf et un manifeste R5 gelé avant
son premier POST sont requis avant toute reprise.

```text
J9_WO036_RESUME_AUTHORIZED=NO
J9_WO036_WORK_ORDER_MOVE_TO_COMPLETED=NO
J9_WO036_RESUME_AFTER_TOOLING_CORRECTION=REQUIRES_SEPARATE_OWNER_DECISION
J9_WO036_NEXT_FRESH_RUN=R5
J9_WO036_R5_MANIFEST=REQUIRED_NEW_AND_FROZEN_BEFORE_FIRST_POST
J9_LOCAL_INT001_RECEIVER_LOOPBACK_AUTHORIZED=NO_PENDING_NEW_OWNER_DECISION

J9_OFFICIAL_PERMISSION_STATUS=NOT_EVIDENCED
J9_PROVIDER_DERIVED_REAL_DELIVERY_AUTHORIZED=NO
J9_PROVIDER_NETWORK_AUTHORIZED=NO
REAL_RECEIVER_NETWORK_AUTHORIZED=NO
REMOTE_RECEIVER_NETWORK_AUTHORIZED=NO
LIVE_DELIVERY_AUTHORIZED=NO
J9_VPS_DEPLOYMENT_AUTHORIZED=NO
J9_PRODUCTION_AUTHORIZED=NO
INT001_PULL_REQUEST_AUTHORIZED_BY_THIS_WORK_ORDER=NO
INT001_VALIDATION_AUTHORIZED_BY_THIS_WORK_ORDER=NO
PRIMARY_DATABASE_PURGE=NO
```

## 18. Autorisation propriétaire de reprise R5 — état pré-exécution

WO-040 a corrigé et qualifié la capture bornée d'une réponse HTTP complète selon son framing,
notamment la réponse `409/J7_IMPORT_CONFLICT` chunked émise par INT-001 après un effet durable de
divergence. Le propriétaire a validé WO-040, puis a transmis la décision distincte suivante :

```text
Pousser, puis reprendre WO-036
```

La branche WO-040 a d'abord été poussée sur `origin`, conformément à la première partie de cette
instruction. La décision de reprise est enregistrée le `2026-09-03T19:05:02.0860147Z`, soit le
`2026-09-03T21:05:02.0902057+02:00` en Europe/Paris. Son effet est strictement borné au prochain
run R5 synthétique Windows/Windows déjà défini par le présent Work Order. Elle n'ouvre aucune porte
fournisseur, donnée dérivée, receiver réel ou distant, VPS, production, PR ou validation INT-001.

La branche WO-036 a été avancée par fast-forward jusqu'au commit de clôture WO-040
`9cf14180404efe899abeb6a7f3f3d6b7f1e6a028`, sans rebase, squash ni réécriture. R1 à R4 restent
immuables. R5 doit repartir d'un répertoire privé, d'une PKI éphémère, de bases isolées, d'un
export synthétique et de claims tous neufs. Les exécutables reconstruits et l'outillage doivent
être enregistrés en one-shot, puis le manifeste ci-dessous doit être gelé et commité avant le
premier `POST` :

```text
docs/validation/J9-WO036-J7-LOCAL-E2E-CAMPAIGN-MANIFEST-RESUME-R5-20260903.md
```

```text
J9_WO036_OWNER_RESUME_DECISION=AUTHORIZE
J9_WO036_RESUME_RUN=R5
J9_WO036_STATUS=RESUME_R5_AUTHORIZED_PENDING_FRESH_MANIFEST_AND_PREFLIGHT
J9_WO036_RESUME_AFTER_WO040_VALIDATION=AUTHORIZED_BY_SEPARATE_OWNER_DECISION
J9_WO036_FRESH_RUN_REQUIRED=YES
J9_WO036_FRESH_MANIFEST_REQUIRED=YES
J9_WO036_WORK_ORDER_MOVE_TO_COMPLETED=NO
J9_LOCAL_SYNTHETIC_WINDOWS_E2E_AUTHORIZED=YES
J9_LOCAL_INT001_RECEIVER_LOOPBACK_AUTHORIZED=YES_SYNTHETIC_ONLY
MAXIMUM_IMPORT_ROUTE_CALLS=3
EXPECTED_IMPORT_ROUTE_SEQUENCE=201_IMPORTED,200_DUPLICATE,409_DIVERGENCE_REJECTED
AUTOMATIC_RETRY=0

WO040_STATUS=VALIDATED
WO040_PRIMARY_IMPLEMENTATION_COMMIT=f6e6a804f507bad48943534d4179bfcc90437766
WO040_RESPONSE_LINE_COMPATIBILITY_COMMIT=80cd5a33b19b2da48f0ab0821ef6fdfbd2623ba4
WO040_DOCUMENTATION_COMMIT=8e7441b94d6074fd59d5b18c5cd830c064e88e5f
WO040_CLOSURE_COMMIT=9cf14180404efe899abeb6a7f3f3d6b7f1e6a028
WO040_QUALIFICATION_REPORT_SHA256=2eb13aa1825d21eb5c40e97ffebf77246099d781f626591132897c4d128e9aee
WO040_COMPLETED_WORK_ORDER_SHA256=0fcbd57ee3eac7facf5a638c0d5c86e38eaf9a09b0418e8b7b5b6f2eb915871f

J9_OFFICIAL_PERMISSION_STATUS=NOT_EVIDENCED
J9_PROVIDER_DERIVED_REAL_DELIVERY_AUTHORIZED=NO
J9_PROVIDER_NETWORK_AUTHORIZED=NO
REAL_RECEIVER_NETWORK_AUTHORIZED=NO
REMOTE_RECEIVER_NETWORK_AUTHORIZED=NO
LIVE_DELIVERY_AUTHORIZED=NO
J9_VPS_DEPLOYMENT_AUTHORIZED=NO
J9_PRODUCTION_AUTHORIZED=NO
INT001_PULL_REQUEST_AUTHORIZED_BY_THIS_WORK_ORDER=NO
INT001_VALIDATION_AUTHORIZED_BY_THIS_WORK_ORDER=NO
PRIMARY_DATABASE_PURGE=NO
```

## 19. Run R5 — arrêt après le premier import et avant le duplicate

Le run R5 a utilisé une racine privée, une PKI, deux bases isolées, un export J7 synthétique et des
claims neufs. Son manifeste distinct a été gelé puis commité avant le premier `POST` au commit
`7c7505174c6a01da1c0134cd0564bd49529d0445`, SHA-256
`c7a7fcee02b5d70b49551509a0773b0b404b369af94c386308838398e51b3ec0`. Le contrôle post-commit de
l'outillage gelé a réussi avec zéro appel receiver.

A a ensuite produit `201/IMPORTED` et l'état local `DELIVERED`, avec exactement un receipt, un
payload byte-identique, un audit `IMPORTED` et un outbox. Après l'arrêt gracieux de A, la commande
one-shot de démarrage de B a échoué sur la porte du listener exact `127.0.0.1:8087`. Le journal
privé expurgé rapporte que Spring/Tomcat a annoncé son démarrage, mais la propriété exacte du
listener n'a pas été validée dans l'enveloppe bornée. Le processus B a été nettoyé ; aucun second
`POST`, duplicate, probe de collision ou retry n'a été exécuté.

La claim de démarrage B reste consommée. Sa suppression ou son rejeu aurait contredit les invariants
de la campagne. La cause racine est `NOT_ESTABLISHED` : une course d'observation Windows est une
hypothèse, pas un fait établi. Un Work Order distinct de diagnostic et qualification de l'outillage
de démarrage est requis avant toute nouvelle proposition de reprise.

Le receiver a été arrêté gracieusement et le cleanup a établi zéro processus, listener, conteneur,
volume, certificat ou racine privée R5 résiduel. Le conteneur PostgreSQL primaire exact a été
redémarré avec la même identité et est `healthy` sur `127.0.0.1:5432`, sans recréation, accès ou
purge de son volume.

Les postflights sont verts : Pester WO-036 `68/68`, Local Lab Surefire `1136/0/0/5` et Failsafe
`89/0/0/0` sous `clean verify` puis sous le profil `integration-tests`, receiver Surefire
`297/0/0/0` et Failsafe `98/0/0/0` sous son profil `integration`, et zéro conteneur ou volume
WO-036 ou Testcontainers résiduel. Le contrôle hôte final ne voit que le listener primaire
`127.0.0.1:5432` dans la frontière `8087/8444/5432/5433`.

Le rapport
`docs/validation/J9-WO036-J7-LOCAL-E2E-CAMPAIGN-RESUME-R5-STOP-20260903.md`, taille `12501`
octets et SHA-256 `1bee325424931a8ecc6e0b445fbd06efd2e80364a2dad8e038245dd2012c8053`, classe R5
`STOPPED`. WO-036 reste actif et aucun résultat favorable n'est attribué aux étapes `200` et
`409` non exécutées.

```text
J9_WO036_STATUS=STOPPED_AFTER_FIRST_IMPORT_AND_CONSUMED_B_START_CLAIM
J9_WO036_EVIDENCE_RESULT=STOPPED
J9_WO036_EVIDENCE_REPORT_SHA256=1bee325424931a8ecc6e0b445fbd06efd2e80364a2dad8e038245dd2012c8053
J9_WO036_RESUME_RUN_R5=CONSUMED
J9_WO036_IMPORT_ROUTE_CALLS=1
J9_WO036_MAXIMUM_IMPORT_ROUTE_CALLS=3
J9_WO036_AUTOMATIC_RETRIES=0
J9_WO036_LOCAL_LAB_A_RESULT=201_IMPORTED_DELIVERED
J9_WO036_LOCAL_LAB_B_DELIVERY_ATTEMPTS=0
J9_WO036_COLLISION_PROBE_EXECUTED=NO
J9_WO036_WORK_ORDER_MOVE_TO_COMPLETED=NO
J9_WO036_RESUME_AFTER_TOOLING_DIAGNOSIS=REQUIRES_SEPARATE_OWNER_DECISION
J9_WO036_NEXT_FRESH_RUN=R6
J9_WO036_R6_MANIFEST=REQUIRED_NEW_AND_FROZEN_BEFORE_FIRST_POST
J9_LOCAL_INT001_RECEIVER_LOOPBACK_AUTHORIZED=NO_PENDING_NEW_OWNER_DECISION

J9_OFFICIAL_PERMISSION_STATUS=NOT_EVIDENCED
J9_PROVIDER_DERIVED_REAL_DELIVERY_AUTHORIZED=NO
J9_PROVIDER_NETWORK_AUTHORIZED=NO
REAL_RECEIVER_NETWORK_AUTHORIZED=NO
REMOTE_RECEIVER_NETWORK_AUTHORIZED=NO
LIVE_DELIVERY_AUTHORIZED=NO
J9_VPS_DEPLOYMENT_AUTHORIZED=NO
J9_PRODUCTION_AUTHORIZED=NO
INT001_PULL_REQUEST_AUTHORIZED_BY_THIS_WORK_ORDER=NO
INT001_VALIDATION_AUTHORIZED_BY_THIS_WORK_ORDER=NO
PRIMARY_DATABASE_PURGE=NO
```

## 20. Validation WO-041 et autorisation propriétaire de reprise R6

WO-041 a reproduit et corrigé le faux conflit TOCTOU de la porte listener LocalLabB. Le
propriétaire a validé sa qualification `PASS_LOCAL_FAIL_CLOSED`, autorisé son classement et demandé
son push avant la reprise directe de WO-036. La branche WO-041 a été poussée, puis la branche
WO-036 a été avancée sans conflit ni réécriture jusqu'au commit de clôture
`d1fdf04615994d1ef3e0ad86a5c3d389002e813e`.

La décision reçue est :

```text
J9_WO041_OWNER_REVIEW_DECISION=VALIDATE
J9_WO041_WORK_ORDER=WO-SS-20260903-041-j9-local-labb-readiness-listener-gate
J9_WO041_IMPLEMENTATION_COMMIT=7893136664c5fc6c162da844735e36cb7829814c
J9_WO041_DOCUMENTATION_COMMIT=72ebe53c6bb8bedac21035c2d9d2d058b1bc8dce
J9_WO041_QUALIFICATION_RESULT=PASS_LOCAL_FAIL_CLOSED
J9_WO041_QUALIFICATION_REPORT_SHA256=ed650e2d9631f39b442769315fd50d47e5ee85129fb9e4549bb7093110a2976c
J9_WO041_LOCAL_READINESS_ACKNOWLEDGED=YES
J9_WO041_WORK_ORDER_MOVE_TO_COMPLETED=YES

J9_WO036_STATUS=STOPPED_AFTER_FIRST_IMPORT_AND_CONSUMED_B_START_CLAIM
J9_WO036_RESUME_AFTER_WO041_VALIDATION=YES
J9_WO036_WORK_ORDER_MOVE_TO_COMPLETED=NO
J9_WO036_NEXT_FRESH_RUN=R6
J9_WO036_R6_MANIFEST=REQUIRED_NEW_AND_FROZEN_BEFORE_FIRST_POST

J9_OFFICIAL_PERMISSION_STATUS=NOT_EVIDENCED
J9_PROVIDER_DERIVED_REAL_DELIVERY_AUTHORIZED=NO
J9_PROVIDER_NETWORK_AUTHORIZED=NO
REAL_RECEIVER_NETWORK_AUTHORIZED=NO
REMOTE_RECEIVER_NETWORK_AUTHORIZED=NO
LIVE_DELIVERY_AUTHORIZED=NO
J9_VPS_DEPLOYMENT_AUTHORIZED=NO
J9_PRODUCTION_AUTHORIZED=NO
```

L'instruction complémentaire « Pousser WO-041 une fois terminé puis reprendre R6 directement »
autorise le run neuf R6 dans le périmètre historique synthétique Windows/Windows de WO-036. Elle ne
réutilise aucune racine, PKI, base, export, claim ou tentative de R5. Le manifeste R6 doit être créé,
gelé et commité avant le premier `POST`.

```text
J9_WO036_OWNER_RESUME_DECISION=AUTHORIZE
J9_WO036_RESUME_RUN=R6
J9_WO036_STATUS=RESUME_R6_AUTHORIZED_PENDING_FRESH_MANIFEST_AND_PREFLIGHT
J9_WO036_FRESH_RUN_REQUIRED=YES
J9_WO036_FRESH_MANIFEST_REQUIRED=YES
J9_WO036_WORK_ORDER_MOVE_TO_COMPLETED=NO
J9_LOCAL_SYNTHETIC_WINDOWS_E2E_AUTHORIZED=YES
J9_LOCAL_INT001_RECEIVER_LOOPBACK_AUTHORIZED=YES_SYNTHETIC_ONLY
MAXIMUM_IMPORT_ROUTE_CALLS=3
EXPECTED_IMPORT_ROUTE_SEQUENCE=201_IMPORTED,200_DUPLICATE,409_DIVERGENCE_REJECTED
AUTOMATIC_RETRY=0

J9_OFFICIAL_PERMISSION_STATUS=NOT_EVIDENCED
J9_PROVIDER_DERIVED_REAL_DELIVERY_AUTHORIZED=NO
J9_PROVIDER_NETWORK_AUTHORIZED=NO
REAL_RECEIVER_NETWORK_AUTHORIZED=NO
REMOTE_RECEIVER_NETWORK_AUTHORIZED=NO
LIVE_DELIVERY_AUTHORIZED=NO
J9_VPS_DEPLOYMENT_AUTHORIZED=NO
J9_PRODUCTION_AUTHORIZED=NO
INT001_PULL_REQUEST_AUTHORIZED_BY_THIS_WORK_ORDER=NO
INT001_VALIDATION_AUTHORIZED_BY_THIS_WORK_ORDER=NO
PRIMARY_DATABASE_PURGE=NO
```

L'autorisation de reprendre R6 réactive uniquement l'exception locale synthétique déjà définie par
WO-036 : le receiver INT-001 peut écouter sur `127.0.0.1:8444` pour les trois appels bornés du run.
Cette exception ne change pas `REAL_RECEIVER_NETWORK_AUTHORIZED=NO` et n'autorise aucun receiver
distant ou payload dérivé du fournisseur.

Le premier préflight privé R6 a été retiré le `2026-09-03T21:19:43Z`, avant création du manifeste
et avec zéro appel d'import. Le gel privé avait correctement établi
`IMPORT_ROUTE_CALLS_BEFORE_FREEZE=0`, puis le contrôle a identifié que la liste blanche
post-enregistrement de l'outillage désignait encore le manifeste immuable R5. A, le receiver et la
préparation ont été arrêtés gracieusement ; le nettoyage possédé a attesté zéro processus,
listener, conteneur, volume ou certificat résiduel. Le conteneur PostgreSQL primaire exact a été
redémarré avec la même identité, le même volume et le bind `127.0.0.1:5432`, sans purge ni accès à
sa base.

Cette racine pré-manifeste ne constitue pas un run de campagne consommé : aucun manifeste R6 n'a
été créé et aucun `POST` n'a été tenté. Le seul correctif admis avant de recréer l'infrastructure
est le remplacement exact du pin documentaire R5 par le chemin R6 déjà autorisé, avec son test
statique correspondant.

```text
R6_PRE_MANIFEST_PREFLIGHT=DISCARDED_FAIL_CLOSED
R6_PRE_MANIFEST_IMPORT_ROUTE_CALLS=0
R6_PRE_MANIFEST_PRIVATE_ROOT_CLEANUP=PASS
R6_PRE_MANIFEST_PRIMARY_CONTAINER_RESTORED=YES
R6_PRE_MANIFEST_PRIMARY_DATABASE_TOUCHED=NO
R6_MANIFEST_PIN_CORRECTION=R5_TO_R6_ONLY
R6_CAMPAIGN_RUN_CONSUMED=NO
```

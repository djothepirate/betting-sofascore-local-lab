# WO-SS-20260902-036 — Qualification E2E J7 locale Windows/Windows

- **Statut :** `STOPPED_AFTER_DUPLICATE_ACK_PENDING_DISTINCT_RUNTIME_CORRECTION`
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
- **Reprise autorisée UTC :** `2026-09-03T10:10:23Z`
- **Reprise autorisée Europe/Paris :** `2026-09-03T12:10:23+02:00`
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
WORK_ORDER_STATUS=STOPPED_AFTER_DUPLICATE_ACK_PENDING_DISTINCT_RUNTIME_CORRECTION
BRANCH=codex/j9-wo036-j7-local-e2e

LOCAL_SYNTHETIC_WINDOWS_E2E_AUTHORIZED=CONSUMED_BY_STOPPED_R2
LOCAL_INT001_RECEIVER_LOOPBACK_AUTHORIZED=NO_PENDING_NEW_OWNER_DECISION
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

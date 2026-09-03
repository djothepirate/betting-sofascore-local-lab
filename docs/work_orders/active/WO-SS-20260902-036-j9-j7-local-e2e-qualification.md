# WO-SS-20260902-036 — Qualification E2E J7 locale Windows/Windows

- **Statut :** `IN_PROGRESS`
- **Jalon :** après J9 — qualification synthétique de `OPTIONAL_LOCAL_PUSH`
- **Ouvert le :** 2026-09-02
- **Ouverture UTC :** `2026-09-02T19:08:46.1623419Z`
- **Ouverture Europe/Paris :** `2026-09-02T21:08:46.1623419+02:00`
- **Branche :** `codex/j9-wo036-j7-local-e2e`
- **Worktree :** `.tmp/j9-wo036-j7-local-e2e`
- **Base locale d’ouverture vérifiée :** `aa405c6750062b9df9312f0845188f48f4c778de`
- **Sender Local Lab qualifié :** `f5a27887b7db43576eb608d564c245c8cca3a602`
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
WORK_ORDER_STATUS=IN_PROGRESS
BRANCH=codex/j9-wo036-j7-local-e2e

LOCAL_SYNTHETIC_WINDOWS_E2E_AUTHORIZED=YES
LOCAL_INT001_RECEIVER_LOOPBACK_AUTHORIZED=YES_SYNTHETIC_ONLY
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

# J9 — WO-046 — Préflight primaire en lecture seule

- **Résultat :** `PASS_READONLY_PRIMARY_V30_EXPORT_CORROBORATED`
- **Work Order :** `WO-SS-20260904-046-j9-j7-real-local-e2e-campaign`
- **Commit inspecté :** `ca1f902112dabf3fbc42edf080acfac33333e404`
- **Premier snapshot SQL UTC :** `2026-09-04T22:32:01.562779Z`
- **Premier snapshot Europe/Paris :** `2026-09-05T00:32:01.562779+02:00`

## 1. Autorité exacte et limites

Le propriétaire autorise explicitement le préflight strictement en lecture seule via le conteneur
primaire exact `betting-sofascore-local-lab-postgres`, limité au schéma Flyway et aux métadonnées
de l'export sélectionné, sans migration, sauvegarde, démarrage d'application ni POST.

Le client Docker a été identifié par son produit, son éditeur et sa signature Authenticode
Docker Inc. Le transport Docker a été fixé au named pipe local de Docker Desktop Linux, sans
utiliser un contexte ou un `DOCKER_HOST` distant implicite. Les requêtes ont ciblé l'identifiant
complet du conteneur obtenu par son nom exact. Aucune variable d'environnement sensible n'a été
affichée ; `psql` utilise dans le conteneur les identifiants déjà configurés, sans mot de passe
dans les arguments hôte.

Trois transactions SQL courtes ont été exécutées, avec `READ ONLY`,
`default_transaction_read_only=on`, `statement_timeout=5s`, `lock_timeout=1s` et
`idle_in_transaction_session_timeout=10s`. Les deux premières transactions utilisent
`REPEATABLE READ` ; la troisième compare les métadonnées structurées avec le fichier local.
Chaque transaction se termine par `ROLLBACK` et chaque processus `psql` se termine avec le code
zéro. Il ne s'agit pas d'un snapshot unique maintenu entre les trois transactions.

Les tables lues sont limitées à l'historique Flyway, aux métadonnées de schéma, à
`export_manifest`, à l'identité canonique liée et aux comptes de livraison/tentative de ce seul
export. Aucun payload de snapshot, contenu analytique, grant, secret ou clé n'est publié.

## 2. Conteneur et schéma effectifs

```text
PRIMARY_CONTAINER=betting-sofascore-local-lab-postgres
PRIMARY_CONTAINER_STATUS=RUNNING
PRIMARY_CONTAINER_HEALTH=HEALTHY
PRIMARY_CONTAINER_IMAGE=postgres:18.4-alpine
PRIMARY_POSTGRES_BINDING=127.0.0.1:5432
READ_ONLY_TRANSACTION_OBSERVED=ON
FLYWAY_ROW_COUNT=30
FLYWAY_SUCCESSFUL_VERSIONS=1..30
FLYWAY_FAILED_ROW_COUNT=0
PRIMARY_SCHEMA_VERSION=30
V31_APPLIED=NO
V32_APPLIED=NO
V31_OWNER_GO_GRANT_TABLE_PRESENT=NO
V32_OWNER_GO_FORMAT_COLUMN_PRESENT=NO
```

Les noms de scripts et checksums enregistrés des trente migrations ont été lus. Les dernières
entrées sont `V29__j9_optional_local_push_delivery_ledger.sql` (checksum `1322379444`) et
`V30__j9_j7_ack_receipt_time_semantics.sql` (checksum `1232469163`), toutes deux réussies.
Ce relevé ne prétend pas être une exécution de `flyway validate` ni une vérification exhaustive
des objets PostgreSQL. Il établit que la préparation primaire doit traiter V31 puis V32, et non
supposer V31 déjà appliquée.

## 3. Export et ledger corroborés

```text
MATCHING_EXPORT_ROW_COUNT=1
EXPORT_KIND=J7_CANONICAL_EVENT
EXPORT_ID=a8d40d57-98c5-4e01-8ecc-4f1f9b5feabc
CANONICAL_EVENT_ID=e2c9599a-2336-3888-ae88-2437dad02b48
PROVIDER=SOFASCORE
PROVIDER_EVENT_ID=16310945
VALIDATION_STATUS=HUMAN_VALIDATED
PAYLOAD_CLASS_FROM_SOURCE_METADATA=PROVIDER_DERIVED
SCHEMA_ID=urn:betting-project:sofascore-local-lab:j7:canonical-event-export:v1
SCHEMA_VERSION=1.0.0
SIZE_BYTES=35663
FILE_SHA256=d4aba249231bb9d575f40b5c23a629b938f685ee7d4cc479bfa7d846d61be3f6
DATA_SHA256=f95ae31e711a9433d6d06745d37f2535143266aa97c7d151fd26bea88ef03ac6
SOURCE_SET_SHA256=20b5326195551a70dec597e24d102cc178ed3ac4e3304001854e728943371c7e
GENERATED_AT_UTC=2026-09-04T10:27:48.637024Z
DECIDED_AT_UTC=2026-09-04T10:28:13.856199Z
EXPECTED_RELATIVE_VALIDATED_PATH_MATCH=YES
DECISION_TIME_VALID=YES
DECISION_REASON_NULL=YES
DECISION_INTENT_STATUS_TIME_REASON_PATH_HASH_SIZE_MATCH=YES
SELECTED_EXPORT_DELIVERY_ROW_COUNT=0
SELECTED_EXPORT_ATTEMPT_ROW_COUNT=0
```

La provenance n'est pas une colonne `payload_class` du manifeste : elle résulte des cinq
métadonnées de source suivant `J7DeliveryPayloadClassifier`. Les quatre sources `EVENT_STATE`,
`EVENT_STATISTICS`, `EVENT_INCIDENTS` et `EVENT_LINEUPS` sont `PRESENT` et
`PROVIDER_SNAPSHOT`. `EVENT_DETAILS` est `MISSING`, avec la clé `sourceKind` présente et sa
valeur JSON nulle. Aucun type synthétique ou inconnu n'est présent.

Un seul avertissement est conservé : `MISSING_COMPONENT:EVENT_DETAILS`. L'export reste
incomplet sur ce composant ; sa validation humaine n'est pas une promesse de complétude.

## 4. Concordance avec le fichier déjà sélectionné

Le fichier exact déjà sélectionné a été retrouvé dans une seule racine d'exports des worktrees
locaux connus. Son contenu a été lu en mémoire, jamais affiché ou copié vers Git. Son SHA-256
complet a été recalculé et sa taille est inchangée. La comparaison des métadonnées JSON utilise
une égalité structurelle, sans dépendre de l'ordre des propriétés d'un objet.

```text
FILE_SIZE_AND_COMPLETE_SHA256_MATCH=YES
FILE_LEDGER_SOURCES_DEEP_EQUAL=YES
FILE_LEDGER_WARNINGS_DEEP_EQUAL=YES
FILE_LEDGER_SOURCE_SET_SHA256_MATCH=YES
FILE_LEDGER_DISTINCT_SORTED_SNAPSHOT_IDS_MATCH=YES
SOURCE_COUNT=5
SOURCE_SNAPSHOT_COUNT=4
RAW_PAYLOAD_OUTPUT=NO
PRIVATE_ABSOLUTE_PATH_OUTPUT=NO
```

Une première comparaison exploratoire de l'avertissement avec un tableau de chaînes a retourné
`false` : elle supposait à tort cette représentation du JSON de warning. La comparaison finale
porte sur le JSON réel du ledger et celui du manifeste de fichier ; elle retourne `true`. Ce
constat est une correction du contrôle de diagnostic, pas une divergence des données ni une
modification de leur format.

Le préflight ne remplace pas le contrôle applicatif `readAndVerify` juste avant la future
livraison. Il ne vérifie pas les données analytiques, ne fige pas les fichiers et ne réserve
aucun ordinal ou grant. Les métadonnées devront être revalidées au moment de l'exécution autorisée.

## 5. Conséquence et prochaines portes

La porte de concordance fichier/ledger est satisfaite à l'instant de ce préflight. La base est
en V30 et ne possède pas la frontière owner-go V31/V32 requise par le sender courant. Une
autorisation distincte doit donc borner la protection préalable de la base, l'application
séquentielle des migrations append-only V31 et V32, puis la sauvegarde chiffrée et la
restauration isolée qualifiées du schéma résultant. Rien de cela n'a été exécuté ici.

```text
PRIMARY_READONLY_PREFLIGHT=COMPLETE
PRIMARY_MIGRATION_AUTHORIZED=NO
PRIMARY_BACKUP_RESTORE_AUTHORIZED=NO
PRIMARY_DATABASE_PURGE=NO
MANIFEST_CREATION_AUTHORIZED=NO
MANIFEST_CREATED=NO
OWNER_GO_GRANTED=NO
REAL_POST_AUTHORIZED=NO
PROVIDER_NETWORK_AUTHORIZED=NO
REMOTE_RECEIVER_NETWORK_AUTHORIZED=NO
VPS_DEPLOYMENT_AUTHORIZED=NO
PRODUCTION_AUTHORIZED=NO
```

Le Work Order reste actif. Aucun nouveau manifeste, owner-go, démarrage d'application,
sauvegarde, migration ou POST n'a été effectué par ce préflight.

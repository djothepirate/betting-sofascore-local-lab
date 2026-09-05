# J9 — Qualification WO-047 de la séparation de gouvernance J7

## 1. Résultat

```text
WORK_ORDER=WO-SS-20260904-047-j9-j7-delivery-governance-separation
BRANCH=codex/j9-wo047-j7-delivery-governance-separation
BASE_COMMIT=0338821cc07130f5d200a70db10209bb898a59ae
ADR_PROPOSAL_COMMIT=e1ec9936467dd570f7ed00c51227c8e7d5a35945
ADR_PROPOSAL_SHA256=ded6a4da8a3161caae491f62919f4f5c3569c69c821be5a807772cc542cede3f
ADR_ACCEPTANCE_COMMIT=9ef4554461a87c1acf954965ea4ca5fc18207789
IMPLEMENTATION_COMMIT=ddb41e8fd0dfe32e9c2aa5fdb4a50d9fcd90cb93
QUALIFIED_AT_UTC=2026-09-04T16:28:06.2533886Z
QUALIFIED_AT_EUROPE_PARIS=2026-09-04T18:28:06.2533886+02:00
QUALIFICATION_RESULT=PASS_LOCAL_FAIL_CLOSED
PROVIDER_PERMISSION_AUDIT_STATUS=NOT_EVIDENCED
PROVIDER_CALLS=0
PROVIDER_DERIVED_REAL_POSTS=0
REMOTE_RECEIVER_CALLS=0
VPS_CONNECTIONS=0
PRIMARY_DATABASE_TOUCHES=0
NATIVE_BACKUP_RESTORE_RESULT=NOT_RUN_NOT_AUTHORIZED
WO046_RESUME_AUTHORIZED=NO_REQUIRES_SEPARATE_OWNER_DECISION
```

WO-047 sépare le fait d'audit relatif à la permission fournisseur de l'autorité interne nécessaire
au transfert local d'un export J7. `NOT_EVIDENCED` reste enregistré exactement comme fait d'audit,
mais ne bloque plus à lui seul le chemin J7 local. `EVIDENCED_INCOMPATIBLE`, une valeur inconnue ou
une base de gouvernance divergente ferment toujours le chemin avant certificat, claim et transport.

Cette qualification n'autorise et n'exécute aucun POST réel. Elle ne modifie ni les portes
d'acquisition J3/J4/J5, ni ADR-SS-001, ni le receiver INT-001. Elle ne sélectionne aucun export ou
certificat, ne crée aucun manifeste WO-046 et ne construit ni ne consomme aucun go réel.

## 2. Décision et frontières appliquées

ADR-SS-003 v0.2 a été acceptée sur les octets immuables du commit de proposition
`e1ec9936467dd570f7ed00c51227c8e7d5a35945`, dont l'empreinte SHA-256 est
`ded6a4da8a3161caae491f62919f4f5c3569c69c821be5a807772cc542cede3f`.

La séparation qualifiée est bornée ainsi :

- `NOT_EVIDENCED` et `EVIDENCED_COMPATIBLE` sont les deux valeurs d'audit admises pour le seul
  transfert J7 local, sous réserve de toutes les autres portes ;
- `EVIDENCED_INCOMPATIBLE`, `null` et toute valeur inconnue ou invalide sont bloquants ;
- `J7_TRANSFER_GOVERNANCE_BASIS_ADR_STATUS` vaut exactement
  `ADR_ACCEPTED_NO_EXECUTION_AUTHORITY` : l'ADR ne constitue pas un go ;
- le format V1 et la migration V31 restent strictement historiques et immuables ;
- le format V2 et V32 sont une extension append-only discriminée ;
- aucune autorité n'est ajoutée pour le fournisseur, un receiver distant, le VPS ou la production.

## 3. Contrat canonique V1/V2

Le format historique `J7_PROVIDER_DERIVED_OWNER_GO_V1` conserve son constructeur, son ordre de
champs, ses octets, sa terminaison LF et son exigence `EVIDENCED_COMPATIBLE`. Son golden SHA-256
reste :

```text
V1_CANONICAL_GOLDEN_SHA256=ef400eb26605b7ab447150d9b3db083094f25cde0a69cf3c5a46b943ce3c23ee
```

Le format `J7_PROVIDER_DERIVED_OWNER_GO_V2` remplace les lignes ambiguës par deux groupes distincts :

```text
PROVIDER_PERMISSION_AUDIT_*
J7_TRANSFER_GOVERNANCE_BASIS_*
```

La sérialisation Java et la fonction PostgreSQL produisent le même préimage UTF-8/LF, avec ordre
fixe, UUID minuscules, booléens `YES|NO`, URI sûre et instants UTC à six chiffres de microsecondes.
Le golden V2 qualifié est :

```text
V2_CANONICAL_GOLDEN_SHA256=09e71b91da149f38dcd671568c88d3f2522049b37e1aaf222b468538cc588414
```

Les références de gouvernance V2 doivent correspondre exactement à l'ADR acceptée. Toute forme
hybride V1/V2, tout champ obligatoire nul, tout discriminant inconnu, tout hash non canonique ou
toute divergence d'autorité est refusé.

## 4. Migration V32 et persistance atomique

La migration
`src/main/resources/db/migration/V32__j9_j7_delivery_governance_separation.sql`, SHA-256
`2a93c5b85cbc636a5e3dca959b469c4bd9783a4b3604a7234c61e2a49048d195`, ajoute :

- le discriminant `owner_go_format` ;
- les sept colonnes V2 ;
- des contraintes V1/V2 mutuellement exclusives et protégées contre SQL `UNKNOWN` par `IS TRUE` ;
- une fonction canonique V2, un dispatcher strict et un trigger de hash qualifié par schéma ;
- un `search_path` fixé pour empêcher le shadowing par un schéma hostile.

Les lignes V31 existantes reçoivent le discriminant V1 par un défaut constant au moment de
l'ajout de colonne, puis ce défaut est retiré pour les futures insertions. La migration ne contient
aucun `UPDATE`. La qualification progressive confirme que les valeurs, `xmin`, `ctid` et
`pg_relation_filenode` historiques restent inchangés. Le fichier V31 et sa fonction canonique V1
sont byte-identiques au commit de base.

L'adaptateur JDBC lit et écrit explicitement les deux formes, utilise des paramètres SQL nuls typés
et refuse tout format inconnu. Pour les deux versions, le verrouillage, la revalidation du grant,
la création de tentative, la consommation unique et le passage `IN_FLIGHT` restent dans une même
transaction `REQUIRES_NEW`. Les courses claim/révocation et claim/claim n'ont qu'un gagnant ; après
claim committé, aucun remboursement ni retry automatique n'existe.

## 5. Policy, configuration et exposition locale

La configuration par défaut reste désactivée. En mode `PROVIDER_DERIVED`, la policy accepte les
deux valeurs d'audit bornées et refuse explicitement :

```text
OFFICIAL_PERMISSION_EVIDENCED_INCOMPATIBLE
OFFICIAL_PERMISSION_STATUS_INVALID
```

Ces motifs sont conservés dans les mappings UI/API au lieu d'être aplatis en
`DELIVERY_DISABLED`. Le code legacy `OFFICIAL_PERMISSION_NOT_EVIDENCED` reste source-compatible
mais n'est plus émis sur ce chemin. Les refus surviennent avant lecture de l'export, certificat,
claim et transport.

## 6. Commandes et résultats

Toutes les commandes ont utilisé Java 25.0.4, le wrapper Maven du dépôt et le cache Maven local en
mode hors ligne. Le pipeline natif J6 a été exclu ; seuls deux contrôles statiques de ses scripts ont
été réintroduits positivement.

### 6.1 Build standard borné

```text
mvnw.cmd --offline
  -Dmaven.repo.local=C:\Users\geoff\.m2\repository
  -Dsurefire.excludes=**/J6NativeBinaryPipelineQualificationTest.java
  -DskipITs=true
  clean verify
RESULT=PASS
SUREFIRE_TESTS=1178
SUREFIRE_FAILURES=0
SUREFIRE_ERRORS=0
SUREFIRE_SKIPPED=4
FAILSAFE_EXECUTION=SKIPPED
```

### 6.2 Intégrations autorisées sur PostgreSQL isolé et loopback synthétique

```text
mvnw.cmd --offline
  -Dmaven.repo.local=C:\Users\geoff\.m2\repository
  -Pintegration-tests
  -Dfailsafe.excludes=**/FlywayMigrationIT.java
  failsafe:integration-test failsafe:verify
RESULT=PASS
TESTS=37
FAILURES=0
ERRORS=0
SKIPPED=0
```

Répartition :

```text
J7DeliveryLedgerMigrationIT=29/29 PASS
J7DeliveryMutualTlsLoopbackIT=4/4 PASS
J7OptionalDeliveryEndToEndIT=2/2 PASS
J7ProviderOwnerGoV32MigrationIT=2/2 PASS
```

Les receivers sont synthétiques et strictement loopback. Les bases sont des conteneurs PostgreSQL
Testcontainers isolés et éphémères.

### 6.3 Flyway strictement ciblé

```text
mvnw.cmd --offline
  -Dmaven.repo.local=C:\Users\geoff\.m2\repository
  -Pintegration-tests
  -Dit.test=FlywayMigrationIT#createsTheJ3RawSnapshotSchemaAndKeepsNetworkDisabled
    +executesJ6BackupFingerprintQueriesAgainstTheMigratedSchema
    +j6RetentionLauncherRequiresCurrentFlywayAndDisablesTournamentDiscovery
  failsafe:integration-test failsafe:verify
RESULT=PASS
TESTS=3
FAILURES=0
ERRORS=0
SKIPPED=0
```

Le scénario `restoresJ8AndJ7DeliveryEvidenceWithIdenticalFingerprints`, qui lance réellement
`pg_dump` et `pg_restore`, n'a pas été sélectionné.

### 6.4 Contrôles J6 purement statiques

```text
mvnw.cmd --offline
  -Dmaven.repo.local=C:\Users\geoff\.m2\repository
  -Dtest=J6NativeBinaryPipelineQualificationTest#backupRestoreScriptUsesTheFailClosedSupervisorForBothBinaryPipelines
    +retentionAcceptsOnlyAV32QualifiedManifestWithoutRunningNativeTools
  surefire:test
RESULT=PASS
TESTS=2
FAILURES=0
ERRORS=0
SKIPPED=0
```

Ces deux méthodes lisent les scripts et qualifient les gardes V32 sans invoquer PowerShell,
Docker CLI, `pg_dump`, `pg_restore` ou un exécutable natif.

## 7. Déviation de sélection contenue

Une première tentative hors sandbox des intégrations a utilisé le sélecteur trop large
`-Dit.test=*,!FlywayMigrationIT`. Celui-ci a réexécuté des classes unitaires sous Failsafe et a
atteint la bannière de classe `J6NativeBinaryPipelineQualificationTest`. L'exécution a été
interrompue immédiatement à cette bannière.

```text
DEVIATION_RESULT=STOPPED_AT_FORBIDDEN_CLASS_BANNER
FORBIDDEN_NATIVE_METHOD_REPORT=NOT_CREATED
NATIVE_PIPELINE_MARKER_EVIDENCE=NONE_OBSERVED
PG_DUMP_OR_PG_RESTORE_EVIDENCE=NONE_OBSERVED
PRIMARY_DATABASE_STATE_CHANGE=NONE_OBSERVED
RESULT_CLAIMED_FROM_DEVIATING_RUN=NO
```

Une tentative en sandbox du sélecteur corrigé a ensuite échoué avant démarrage du premier
conteneur, sur l'accès refusé au pipe Docker. Aucun résultat n'en est revendiqué. La commande
finale utilise la sélection Failsafe native des seuls `*IT` et exclut explicitement
`FlywayMigrationIT`; les trois méthodes Flyway autorisées sont exécutées séparément par sélection
positive.

Après les exécutions finales, aucun processus Java lié à `.tmp\w47` ne subsiste. Les seuls
processus Java observés appartiennent aux serveurs de langage Eclipse préexistants. Le seul
conteneur visible est le PostgreSQL primaire préexistant :

```text
PRIMARY_CONTAINER_ID=e7b218117df39b7cbfbffb3b1223647568f149912a84592e506b5690f9686c1f
PRIMARY_CONTAINER_STATE=running
PRIMARY_CONTAINER_HEALTH=healthy
PRIMARY_CONTAINER_STARTED_AT=2026-09-03T21:42:23.239113943Z
PRIMARY_CONTAINER_VOLUME=betting-sofascore-local-lab-postgres-data:/var/lib/postgresql
TESTCONTAINERS_RESIDUAL_COUNT=0
```

L'identité, l'heure de démarrage, l'état et le volume du conteneur primaire sont identiques aux
contrôles avant et après qualification. Aucun accès SQL à cette base n'a été effectué sous WO-047.

## 8. Contrôles complémentaires

```text
GIT_DIFF_CHECK=PASS
GIT_DIFF_CHECK_INFORMATIONAL_EOL_WARNINGS=2_POWERSHELL_LF_TO_CRLF
CHANGED_FILES_FROM_BASE=30
UTF8_INVALID_FILES=0
UTF8_BOM_FILES=0
NUL_FILES=0
SECRET_SCAN_PATTERNS=6
SECRET_SCAN_HITS=0
V31_DIFF_FROM_BASE=EMPTY
V32_SHA256=2a93c5b85cbc636a5e3dca959b469c4bd9783a4b3604a7234c61e2a49048d195
DOCKER_COMPOSE_CONFIG_WITH_IGNORED_ROOT_ENV=PASS
SERVER_ADDRESS=127.0.0.1
DEFAULT_SOFASCORE_ENABLED=false
DEFAULT_PLAYWRIGHT_ENABLED=false
DEFAULT_AUTOMATIC_REFRESH_ENABLED=false
DEFAULT_LIVE_POLLING_ENABLED=false
DEFAULT_OPTIONAL_INTEGRATION_ENABLED=false
DEFAULT_OPTIONAL_INTEGRATION_EXECUTION_MODE=DISABLED
DEFAULT_REMOTE_DELIVERY_AUTHORIZED=false
DEFAULT_AUTOMATIC_RETRY_ENABLED=false
```

Le worktree ne contient pas de `.env`. Le contrôle Compose a utilisé le fichier `.env` ignoré du
dépôt racine sans en afficher le contenu. Aucun fichier de configuration fournisseur, catalogue
d'endpoint ou code J3/J4/J5 n'est modifié dans le diff depuis le commit de base.

Deux revues indépendantes ont vérifié l'acceptation et le code. Elles n'ont relevé aucun finding
P0, P1, P2 ou P3 après les corrections documentaires normatives.

## 9. Conclusion et état de gouvernance

```text
J9_WO047_LOCAL_READINESS=PASS_LOCAL_FAIL_CLOSED
J9_WO047_OWNER_REVIEW_REQUIRED=YES
J9_WO047_WORK_ORDER_MOVE_TO_COMPLETED=NO_PENDING_OWNER_DECISION
J9_WO046_RESUME_AFTER_WO047_VALIDATION=REQUIRES_SEPARATE_OWNER_DECISION
J9_WO046_MANIFEST_CREATED=NO
J9_WO046_OWNER_GO_GRANTED=NO
J9_WO046_REAL_POST_AUTHORIZED=NO
J9_PROVIDER_NETWORK_AUTHORIZED=NO
J9_REMOTE_RECEIVER_NETWORK_AUTHORIZED=NO
J9_VPS_DEPLOYMENT_AUTHORIZED=NO
J9_PRODUCTION_AUTHORIZED=NO
```

La qualification conclut `PASS_LOCAL_FAIL_CLOSED`. La validation propriétaire de WO-047 doit
inclure la reconnaissance explicite de la déviation de sélection contenue. Même après validation,
la reprise de WO-046 exige une décision propriétaire séparée ; aucun manifeste, go ou POST réel ne
découle de la présente qualification.

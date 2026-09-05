# J9 — WO-046 — Préparation primaire V30 vers V32

- **Résultat :** `PASS_PRIMARY_V32_PREPARED_NO_CAMPAIGN_MANIFEST_NO_POST`
- **Work Order :** `WO-SS-20260904-046-j9-j7-real-local-e2e-campaign`
- **Autorité et sources avant exécution :** `f9788e63c3df862272a482ef83f5f45a9fa95371`
- **Date locale :** 2026-09-05, Europe/Paris

## 1. Périmètre autorisé

Le propriétaire autorise la configuration locale privée nécessaire, la protection chiffrée V30
avec restauration isolée, les migrations append-only V31 puis V32 via un démarrage transitoire
du Local Lab bloqué, son arrêt, puis la sauvegarde/restauration V32. Aucun manifeste de campagne
ni POST n'est autorisé. Les fichiers techniques `.age.manifest.json` sont uniquement les preuves
de sauvegarde produites par le script existant, pas des manifestes de campagne WO-046.

La cible reste le conteneur exact `betting-sofascore-local-lab-postgres`, image
`postgres:18.4-alpine`, sain et déjà démarré, PostgreSQL borné à `127.0.0.1:5432`. Son identité
complète est contrôlée en mémoire et les commandes Docker utilisent le named pipe local
Docker Desktop Linux. Le receiver INT-001 n'est pas démarré. Aucune purge primaire n'est réalisée.

## 2. Configuration et exécutable

Les fichiers `.env` des bundles V30 et V32 ont été créés hors index Git à partir de la
configuration primaire existante, sous ACL privées propriétaire/SYSTEM. Aucune valeur secrète
n'est imprimée. Avant migration, les trois identifiants PostgreSQL sont comparés en mémoire
avec le conteneur exact et le port est vérifié à `5432`. Les clés fournisseur historiques sont
limitées aux valeurs bloquées ; les arguments de démarrage les imposent également à faux/vide.

La première préparation a échoué avant toute écriture : sous cet environnement, retirer une
variable de processus avec `Environment.SetEnvironmentVariable(..., null, ...)` laissait une
valeur vide qui écrasait les valeurs Compose. Un contrôle synthétique a confirmé ce comportement.
Le wrapper opérationnel utilise ensuite `Remove-Item Env:` pour les seuls overrides concernés.
Les deux fichiers étaient encore absents avant leur création exclusive réussie.

Le JAR V32 a été reconstruit proprement sous Java 25.0.4 avec le Maven Wrapper, en mode
`--offline --errors -DskipTests clean package`, cache local explicite. Résultat hôte :
`BUILD SUCCESS`, durée `18.762 s`. Les tests sont compilés mais **non exécutés** : il s'agit d'une
reconstruction pour une opération administrative, pas d'une nouvelle qualification de tests.
Une invocation abrégée `-o` avait été refusée par le binding PowerShell avant Maven ; un essai
clean en sandbox avait ensuite échoué sur l'accès à une dépendance déjà présente. La construction
propre finale a été exécutée sur l'hôte, sans téléchargement ni Testcontainers.

```text
JAVA_RELEASE=25
SPRING_BOOT_VERSION=4.1.0
JAR_SOURCE_COMMIT=f9788e63c3df862272a482ef83f5f45a9fa95371
JAR_SHA256=f1643edb08a77ed1b53b563b05dd5426bfa53c69027f66bd3b866e012f2aca0f
APPLICATION_SOURCE_CHANGED=NO
MIGRATION_SOURCE_CHANGED=NO
STANDARD_TESTS_REEXECUTED=NO
```

## 3. Protection V30 préalable — PASS

Le bundle propre V30 provient du commit `400900410dfa751521ce387fbadcc4b5ca95a94a`.
Les scripts qualifiés sont invoqués sans modification. La phrase age est traitée uniquement
dans un terminal interactif privé, sans capture, transcript ni accès au presse-papiers.

| Preuve | Valeur |
|---|---|
| Script `Backup-Restore-J6.ps1`, SHA-256 | `9580565fcc6f5065f2e1493316ff4a0b856108f5b4784e3bc050185b6122671c` |
| Archive chiffrée, octets | `8270733` |
| Archive chiffrée, SHA-256 | `9d408408c45eae86d9921e44f6a981ed01f6a1fffe75c8d12fa5b61b600bfc26` |
| Manifeste technique, octets | `2605` |
| Manifeste technique, SHA-256 | `fa19076d368ef868aa238358dc23484da4819cab5920740bb11790e45f114793` |
| Création UTC | `2026-09-04T22:56:29.5628928Z` |
| Qualification UTC | `2026-09-04T22:57:14.8125701Z` |
| Qualification Europe/Paris | `2026-09-05T00:57:14.8125701+02:00` |

Le contrôle indépendant constate `restoreQualified=true`, version source/restaurée `30`,
égalité de tous les champs comparés par le script, hash chiffré recalculé concordant, ACL
propriétaire/SYSTEM uniquement et absence de fichier partiel. Une transaction READ ONLY constate
V30 sans migration échouée, zéro base temporaire J6, zéro session J6 et aucun listener 8087/8444.

La source contient 841 snapshots, 892 occurrences, 314 observations canoniques, 111 observations
de détail et 332 observations J5. Les compteurs J8 valent respectivement 75 campagnes, 228 unités,
227 tentatives historiques, 228 résultats d'unité et 75 résultats de campagne. Le ledger J7
contient zéro livraison, tentative et résultat ; aucune purge n'est auditée. Les 227 tentatives
J8 sont des données historiques restaurées, **pas des appels effectués pendant cette préparation**.

## 4. Migration primaire V31 puis V32 — PASS

Un wrapper opérationnel à usage unique invoque le `main` existant avec
`spring.main.web-application-type=none` et `spring.main.keep-alive=false`. `ProcessStartInfo`
avec `ArgumentList` conserve chaque argument exact ; l'environnement enfant est réduit aux
variables système nécessaires et au répertoire d'export déjà existant. Aucun override JVM,
Spring ou proxy hérité n'est repris. Flyway est activé avec `target=32`, validation avant migration
et `clean-disabled=true` ; Hibernate reste `ddl-auto=none`.

Les flags SofaScore, Playwright, J3/J4/J5, découverte tournoi, refresh, polling et intégration sont
explicitement bloqués. Les origines, worker JAR, identités mTLS et références owner-go sont vides.
Le mode de livraison est `DISABLED`. Aucun serveur web, endpoint de shutdown ou POST n'est utilisé.

```text
MIGRATION_START_UTC=2026-09-04T23:08:08.0997771Z
MIGRATION_COMPLETED_UTC=2026-09-04T23:08:15.0894530Z
MIGRATION_COMPLETED_PARIS=2026-09-05T01:08:15.0894530+02:00
JAVA_PROCESS_ELAPSED_MILLISECONDS=6311
JAVA_EXIT_CODE=0
FLYWAY_VALIDATED_MIGRATIONS=32
FLYWAY_APPLIED_MIGRATIONS=2
PRIMARY_SCHEMA_AFTER=32
FAILED_MIGRATIONS=0
V31_FLYWAY_CHECKSUM=1768980641
V32_FLYWAY_CHECKSUM=516081597
V31_FILE_SHA256=36c48945ea330eca66ab9a3d93438544976dd9d4f36d811a847077acd6e54dc0
V32_FILE_SHA256=2a93c5b85cbc636a5e3dca959b469c4bd9783a4b3604a7234c61e2a49048d195
V1_TO_V30_HISTORY_UNCHANGED=YES
HIKARI_SHUTDOWN_COMPLETED=YES
MIGRATION_PROCESS_RESIDUAL=NO
APPLICATION_LISTENER_COUNT=0
OTHER_PRIMARY_CLIENT_SESSION_COUNT=0
OWNER_GO_FORMAT_COLUMN_PRESENT=YES
OWNER_GO_GRANT_COUNT=0
OWNER_GO_REVOCATION_COUNT=0
OWNER_GO_CONSUMPTION_COUNT=0
DELIVERY_COUNT=0
DELIVERY_ATTEMPT_COUNT=0
CONNECTOR_NETWORK_ENABLED=NO
CONNECTOR_CIRCUIT_STATE=LOCKED
```

La borne de 180 secondes du wrapper limite l'observation, pas une promesse de terminaison forcée.
Elle n'est pas atteinte : la sortie naturelle code zéro et la fermeture Hikari sont observées.
Aucune relance ni terminaison forcée n'a été nécessaire. Les sorties de démarrage sont traitées
en mémoire ; seuls des indicateurs et leurs empreintes sont conservés, pas leur contenu brut.

L'intégralité de la ligne de métadonnées de l'export sélectionné est comparée avant/après et reste
identique. Le fichier déjà `HUMAN_VALIDATED` conserve son SHA-256
`d4aba249231bb9d575f40b5c23a629b938f685ee7d4cc479bfa7d846d61be3f6`. Aucune génération,
validation, décision owner-go ou livraison n'est ajoutée. La réserve
`MISSING_COMPONENT:EVENT_DETAILS` reste celle du préflight précédent.

## 5. Protection V32 après migration — PASS

Le cycle V32 s'est terminé normalement avec statut expurgé `PASS` et absence du processus
interactif. Le contrôle indépendant recalcule les empreintes suivantes et confirme
`restoreQualified=true`, versions source/restaurée `32`, concordance de tous les champs comparés
et ACL propriétaire/SYSTEM uniquement.

| Preuve | Valeur |
|---|---|
| Script `Backup-Restore-J6.ps1`, SHA-256 | `4b68fd7f3d3231b5969ead307373fea6a06f5c987449badf9cfabcfc140e608f` |
| Archive chiffrée, octets | `8323299` |
| Archive chiffrée, SHA-256 | `0fc6648be95b35cbe4c17dba4b2766bd29d8a3cab35cd9ec404d13d2df59adbd` |
| Manifeste technique, octets | `2860` |
| Manifeste technique, SHA-256 | `9a2d50ba1e3a6ba680fb8e5ffcac9bb6079627f11035d89df4aae6dbbc7323d9` |
| Création UTC | `2026-09-04T23:09:57.1743095Z` |
| Qualification UTC | `2026-09-04T23:11:15.2409962Z` |
| Qualification Europe/Paris | `2026-09-05T01:11:15.2409962+02:00` |

Tous les compteurs et toutes les empreintes du manifeste technique V30, hors numéro de version,
sont identiques dans la source V32. Les trois compteurs supplémentaires de grants, révocations et
consommations owner-go valent zéro, dans la source comme dans la restauration. Le contrôle
primaire final constate 32 migrations réussies, aucune échouée, zéro base temporaire J6, zéro
session J6, aucun fichier partiel et aucun listener 8087/8444. Le fichier exact J7 est retrouvé
dans une seule racine connue, son hash est inchangé et sa ligne primaire reste `HUMAN_VALIDATED`.
Les compteurs finaux livraison, tentative, grant, révocation et consommation sont tous nuls ; le
connecteur conserve `network_enabled=false` et `circuit_state=LOCKED`.

Les deux bundles partagent les modules qualifiés :

- `J6-NativeBinaryPipeline.psm1` : `68b49fbf46a9dba3102fe23e4c0654d7f1eadcee70699aa4f9400025ba350349` ;
- `J6-NativeProcessHost.ps1` : `88922f8aac13498bfee3200f846d79d80a61061ab81949d53d2f91538856dda0`.

Les limites utilisées sont 300 secondes par pipeline, 5000 ms de cleanup natif et 10000 ms de
cleanup PostgreSQL. Les restaurations se font dans une base temporaire distincte du même serveur,
pas dans un second conteneur. Le script contrôle les compteurs et empreintes qu'il définit ; il
ne fournit pas une empreinte dédiée exhaustive de `export_manifest` restauré. Les octets du J7
étant externes au dump SQL, leur conservation et hash sont vérifiés séparément. Aucune garantie
supplémentaire de restauration n'est déduite.

## 6. Portes maintenues

```text
PRIMARY_PREPARATION_STATUS=COMPLETE
PRIMARY_SCHEMA_VERSION=32
V30_ENCRYPTED_BACKUP_AND_ISOLATED_RESTORE=PASS
V31_V32_MIGRATION=PASS
V32_ENCRYPTED_BACKUP_AND_ISOLATED_RESTORE=PASS
WORK_ORDER_MOVE_TO_COMPLETED=NO
CAMPAIGN_MANIFEST_CREATION_AUTHORIZED=NO
CAMPAIGN_MANIFEST_CREATED=NO
OWNER_GO_GRANTED=NO
REAL_POST_AUTHORIZED=NO
PROVIDER_NETWORK_AUTHORIZED=NO
REMOTE_RECEIVER_NETWORK_AUTHORIZED=NO
PRIMARY_DATABASE_PURGE=NO
VPS_DEPLOYMENT_AUTHORIZED=NO
PRODUCTION_AUTHORIZED=NO
OFFICIAL_PERMISSION_STATUS=NOT_EVIDENCED
```

`NOT_EVIDENCED` reste un fait d'audit non bloquant pour le seul transfert local J7 sous
ADR-SS-003 v0.2 ; ce statut ne devient pas une permission fournisseur. Les portes d'exécution
distinctes restent nécessaires après la préparation, notamment l'autorisation de création et
gel du manifeste, son commit, puis un owner-go canonique nouveau et un POST unique autorisé.

## 7. Contrôles documentaires

Les quatre fichiers du lot sont le présent rapport, le WO actif, `README.md` et `CHANGELOG.md`.
Le contrôle UTF-8 strict sans BOM ni NUL et `git diff --check` passent. La revue documentaire
indépendante ne relève aucun finding actionnable et recalcule les hashes V31/V32, du script V32
et des deux modules qualifiés. Aucune source applicative, migration, référence PDF immuable,
configuration réseau versionnée ou fixture n'est modifiée. Les phrases secrètes, clés et
empreintes réelles des certificats, payloads et chemins privés restent absents du lot versionné.

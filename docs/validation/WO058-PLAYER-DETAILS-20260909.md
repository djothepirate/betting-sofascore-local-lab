# WO-058 — Capitaines, statistiques individuelles et joueurs indisponibles

## Périmètre du complément

Demandes propriétaires du 9 septembre : afficher le capitaine signalé par `captain: true`,
consulter le bloc `statistics` en ouvrant une carte joueur et compléter chaque équipe avec
`missingPlayers`. Le JSON et les captures joints sont des exemples fournis ; ils ne déclenchent
aucun appel fournisseur et ne sont pas ajoutés au corpus suivi.

Base de travail : `92d82e7`, branche `feature/V0.1.0-RC01-CODEX-WO-SS-20260907-058`,
worktree dédié `.tmp/wo058-live-j4-j5`. Le checkout Eclipse est distinct.
Statuts conservés : `EXPERIMENTAL`, `LOCAL_ONLY`, `NOT_PRODUCTION_APPROVED`, `NO_CRITICAL_DEPENDENCY`.

Le [contrat V3](../architecture/J5-LINEUPS-V3-PLAYER-DETAILS.md) décrit la matrice des absences,
vides, booléens et décimaux, l'évolution V41, la compatibilité des hashes et la projection J7 v1.

## État des vérifications sur ce complément

| Vérification | Portée | Résultat |
|---|---|---|
| Tests ciblés | parseurs V1/V2/V3, domaine, dispatch, présentation, J6 et J7 | 191 exécutés, 0 échec, 0 erreur, 0 ignoré |
| PostgreSQL isolé | V40 préremplie → V41, aller-retour exact, intégrité, rejeu et sauvegarde/restauration | 5 exécutés, 0 échec, 0 erreur, 0 ignoré |
| Chromium synthétique | SSR live/J5, clavier, mobile, détails ouverts pendant refresh | 2 exécutés, 0 échec, 0 erreur, 0 ignoré |
| Relecture JSON fourni | parsing local seul, compteurs et attributs choisis, aucun store | PARSED, COMPLETE ; deux capitaines, 46 blocs statistiques, 11 indisponibles |
| `mvnw clean verify` sans sélection | définition de fini du dépôt | BUILD SUCCESS ; 1 944 cas Surefire, 171 intégrations, aucun échec/erreur ; 5 ignorés Surefire |
| `mvnw -Pintegration-tests verify` sans sélection | seconde commande obligatoire pour la persistance | BUILD SUCCESS ; 1 944 cas Surefire, 171 intégrations, aucun échec/erreur ; 5 ignorés Surefire |
| `mvnw -Pintegration-tests verify` avec sélection Surefire explicite | toutes les intégrations ; classe J6 native réservée | 171 intégrations réussies ; détails ci-dessous |

Les anciens totaux du complément de vocabulaire ne valident pas cette nouvelle évolution.
Les résultats ci-dessous proviennent des exécutions de ce complément. Les deux commandes
obligatoires sont désormais réussies ; les premières passes et leur blocage restent historiques.

## Relecture locale de l'exemple propriétaire

Le fichier joint a été relu sans base ni réseau par un programme Java temporaire ignoré,
`.tmp/PlayerLineupsReplay.java`, avec les classes réellement compilées. Le SHA-256 de l'entrée
est `77ba9cb271665ef9ce6bfa8cbbd10c34b11b916d68f9089bd7775d40c7ead9cc`.
Les identifiants et l'heure de réception utilisés dans ce programme sont des repères synthétiques
de rejeu ; ils ne prétendent pas décrire une réception fournisseur réelle.

- HOME : 23 joueurs, 23 blocs statistiques, 7 indisponibles ; capitaine Thiago Silva.
- AWAY : 23 joueurs, 23 blocs statistiques, 4 indisponibles ; capitaine Ignacio Vazquez.
- Extrait vérifié pour Thiago Silva : note `8`, minutes `64`, passes réussies `67` sur `69`,
  `expectedAssists=0.0185261`, `totalShots=0`.
- V3 produit `PARSED` et `COMPLETE`, avec la même complétude que V2. Les avertissements pour
  métadonnées non prises en charge atteignent la borne de 256 ; ils ne sont pas des erreurs
  de parsing. Le contenu complet reste hors Git et hors logs.

Journal local : `.tmp/player-details-operator-replay.log`.

## Vérifications ciblées

Les commandes utilisent Java 25.0.4 et le wrapper Maven sous Windows. Le cache de dépendances
est explicitement local ; `--offline` ne se substitue pas à l'isolation des tests PostgreSQL.

```powershell
.\mvnw.cmd --offline '-Dmaven.repo.local=C:/Users/geoff/.m2/repository' '-Dtest=EventLineupsV*ParserTest,LineupEnrichmentContractTest,LineupsPresentationTest,PlayerStatisticsPresentationTest,J6SemanticDiffServiceTest,J7EnvelopeAssemblerTest,LivePayloadNormalizerTest,LiveCampaignServiceTest,J5RealEventDataServiceTest,J5LocalJsonImportProcessorTest,J5LocalJsonImportServiceTest,J5SharedLeaseExclusivityTest' '-Dit.test=J5LineupDetailsPersistenceIT' '-Pintegration-tests' verify
.\mvnw.cmd --offline '-Dmaven.repo.local=C:/Users/geoff/.m2/repository' '-Dtest=PlayerStatisticsPresentationTest,LineupsPresentationTest' '-Dit.test=J5LineupDetailsPersistenceIT' '-Pintegration-tests' verify
```

La première commande donne 191 tests unitaires réussis et révèle un échec lors de `pg_restore`
parmi les cinq scénarios PostgreSQL. Les fonctions V41 appelaient des helpers non qualifiés,
incompatibles avec le `search_path` vide de restauration. Les références sont maintenant
qualifiées par `public.` ; la seconde commande vérifie explicitement les validateurs avec ce
chemin vide, puis réussit la sauvegarde/restauration native : 7 tests unitaires et 5 tests
PostgreSQL, sans échec ni test ignoré, `BUILD SUCCESS` le 9 septembre à 00:07:25 UTC.
La sortie d'erreur détaillée de la première restauration n'avait pas été conservée ; les
assertions de commandes affichent désormais opération, stdout et stderr en cas d'échec.

Les scénarios couvrent aussi l'upgrade prérempli V40 sans reprise des anciennes observations,
le rejeu/idempotence, les valeurs absentes/vides/fausses, les décimaux exacts, les dates avec
offset, la concurrence/rollback, les contraintes JSON et l'immutabilité/verrouillage J7.
Les preuves source, les lignes enfants et les hashes recalculés sont comparés avant/après
restauration. Aucune base opérateur n'est utilisée.

Journaux locaux : `.tmp/player-details-targeted-final.log` et
`.tmp/player-details-persistence-final.log`. Une erreur antérieure de fixture dans le test de
présentation utilisait une clé hors contrat ASCII ; elle est corrigée et les valeurs textuelles
contenant du balisage restent qualifiées séparément dans le navigateur.

## Qualification graphique

```powershell
.\mvnw.cmd --offline '-Dmaven.repo.local=C:/Users/geoff/.m2/repository' '-Pprovider-playwright-runtime,provider-playwright-local-qualification' test-compile
$env:PLAYWRIGHT_BROWSERS_PATH=(Resolve-Path .tmp/provider-playwright-browsers).Path
$env:PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD='1'
.\mvnw.cmd --offline '-Dmaven.repo.local=C:/Users/geoff/.m2/repository' '-Pprovider-playwright-runtime,provider-playwright-local-qualification' '-DskipTests=false' '-DskipITs=false' '-Dit.test=LiveCampaignLineupsBrowserQualificationIT' "-Dprovider.playwright.browser-cache=$env:PLAYWRIGHT_BROWSERS_PATH" 'failsafe:integration-test@provider-playwright-loopback-qualification' 'failsafe:verify@provider-playwright-loopback-qualification'
```

`BUILD SUCCESS` à 00:09:38 UTC, deux tests exécutés sans erreur/échec/skip. Les pages synthétiques
sont interceptées par MockMvc, le contexte Chromium est hors ligne, sans base opérateur,
requête fournisseur ni POST. Le lancement est une qualification locale explicitement ciblée,
distincte du démarrage standard du Lab.

Les assertions vérifient SSR live/J5 sans JavaScript, clic et clavier, absence vs bloc vide,
petites valeurs non nulles, note principale et variantes distinctes, transfert/disparition du
capitaine, absences dupliquées conservées, correction du retour estimé, échappement des noms et
des descriptions, groupes repliés, réordre des remplaçants, maintien des panneaux et du focus,
repli du focus si une rubrique devient cachée et zéro mutation du composant à contenu identique.
Le viewport mobile ne déborde pas horizontalement.

Captures synthétiques inspectées localement :
`.tmp/lineups-ui-qualification/lineups-player-details-desktop.png` et
`.tmp/lineups-ui-qualification/lineups-live-refreshed-mobile.png`.
Journaux : `.tmp/player-details-browser-compile.log` et `.tmp/player-details-browser.log`.

## Première suite élargie et levée du blocage

```powershell
.\mvnw.cmd --offline '-Dmaven.repo.local=C:/Users/geoff/.m2/repository' '-Pintegration-tests' '-Dtest=!J6NativeBinaryPipelineQualificationTest' verify
```

`BUILD SUCCESS` à 00:22:10 UTC, durée 11 min 14 s. Surefire recense 2 111 cas, zéro échec et
zéro erreur, quatre ignorés pour absence de liens symboliques Windows. La sélection négative
a également inclus les classes d'intégration dans cette phase. Failsafe exécute ensuite les
171 cas d'intégration, tous réussis et aucun ignoré ; ces totaux ne doivent donc pas être
additionnés pour prétendre compter des cas distincts.

La classe `J6NativeBinaryPipelineQualificationTest` avait été réservée parce que son contrôle
natif vérifie le port 8087. Ses deux tests de contrat de scripts peuvent néanmoins être exécutés
séparément ; leurs attentes passent de V40 à V41, conformément aux scripts modifiés.
Le scénario Docker de cette classe reste soumis à son opt-in habituel.

```powershell
.\mvnw.cmd --offline '-Dmaven.repo.local=C:/Users/geoff/.m2/repository' '-Dtest=J6NativeBinaryPipelineQualificationTest#retentionAcceptsOnlyAV41QualifiedManifestWithoutRunningNativeTools+backupRestoreScriptUsesTheFailClosedSupervisorForBothBinaryPipelines' test
```

Ces deux tests réussissent sans échec/erreur/skip, `BUILD SUCCESS` à 00:23:39 UTC.
Journal : `.tmp/player-details-backup-contract-final.log`.

À 02:22 heure de Paris, la définition de fini restait ouverte : le Lab opérateur écoutait
sur `127.0.0.1:8087` (PID 74536). Une confirmation de pause avait été demandée pour exécuter
`clean verify` sans exclusion, conformément à `AGENTS.md`.

Lors de la reprise du 9 septembre, le propriétaire confirme la libération du port et autorise
explicitement l'arrêt du processus s'il écoute encore. Le contrôle retrouve le PID 74536
(`java`) ; il est arrêté par `Stop-Process -Force` après revalidation de son écoute sur 8087.
Le port est confirmé libre avant la passe complète. Aucun changement de code du checkout
Eclipse, aucune migration ni manipulation directe de sa base n'est effectué. Les changements
de ce complément restent dans le worktree WO-058 et ne sont pas encore livrés.

Journal de la suite élargie : `.tmp/player-details-broad-verification.log`.

## Vérifications complètes après libération du port

```powershell
.\mvnw.cmd --offline '-Dmaven.repo.local=C:/Users/geoff/.m2/repository' clean verify
.\mvnw.cmd --offline '-Dmaven.repo.local=C:/Users/geoff/.m2/repository' '-Pintegration-tests' verify
```

La première commande réussit le 9 septembre à 06:12:13 UTC (08:12:13 à Paris), en 11 min 20 s :
1 944 cas Surefire, sans échec ni erreur, dont cinq ignorés, puis 171 intégrations Failsafe,
toutes réussies sans cas ignoré. Le contrôle natif
`syntheticNativePipelineFailsClosedWithoutHumanPassphraseInput` est exécuté et réussi ;
aucune classe n'est exclue de cette passe.

Les cinq ignorés sont quatre scénarios de liens symboliques non disponibles sous Windows
(`LocalJ7ExportFileStoreTest` et `J8BenchmarkExportCommandTest`, deux chacun) et le scénario
Docker dédié de `J6NativeBinaryPipelineQualificationTest`, soumis à la propriété explicite
`j6.docker.qualification=true`. Les cinq tests de persistance des nouveaux détails joueurs,
dont la sauvegarde/restauration native, passent dans cette exécution complète.

La seconde commande, avec le profil `integration-tests` explicite, réussit à 06:23:05 UTC
(08:23:05 à Paris), en 10 min 26 s. Elle recense les mêmes 1 944 cas Surefire (zéro échec,
zéro erreur, cinq ignorés pour les mêmes conditions) et 171 intégrations Failsafe, toutes
réussies sans cas ignoré. Les résultats des deux commandes sont deux exécutions des mêmes
scénarios et ne sont pas additionnés en un nombre de tests distincts.

La définition de fini technique du complément est satisfaite. Le code fonctionnel testé est
inchangé entre les deux passes ; seules les preuves documentaires sont complétées ensuite.
Le checkout Eclipse reste propre sur `92d82e7`. Le complément est destiné à un commit local
sur la branche WO-058 ; aucune livraison dans Eclipse, publication ou fusion n'est effectuée
par cette qualification. Le WO global demeure `IN_PROGRESS`.

Journaux locaux : `.tmp/player-details-clean-verify.log` et
`.tmp/player-details-integration-verify.log`. Ces commandes n'emploient ni sélection `-Dtest`,
ni sélection `-Dit.test`, ni option de désactivation des tests.

## Contrôles du diff et fichiers du complément

Les fichiers sont lisibles en UTF-8 strict. La revue du diff et la recherche de motifs
de secrets ne détectent aucun secret ajouté. `git diff --check` réussit. Les migrations V1 à
V40 sont inchangées ; V41 est ajoutée. `application.yml` conserve `server.address: 127.0.0.1`,
`SOFASCORE_ENABLED:false` et les opt-ins réseau existants. Aucun endpoint ni transport n'est
ajouté, et les tests standards ajoutés n'ouvrent pas de navigateur ni de requête fournisseur.

Fichiers relatifs au worktree WO-058 :

- `CHANGELOG.md`
- `docs/architecture/J5-GUARDED-REAL-EVENT-DATA.md`
- `docs/architecture/J5-LINEUPS-V3-PLAYER-DETAILS.md`
- `docs/architecture/J5-OFFLINE-EVENT-DATA-AND-COMPLETENESS.md`
- `docs/architecture/J5-OFFLINE-MULTI-MATCH-IMPORT.md`
- `docs/architecture/J7-CANONICAL-EVENT-EXPORT.md`
- `docs/requirements/J3-J5-TOURNAMENT-EVENT-DISCOVERY-RULES.md`
- `docs/requirements/J5-OFFLINE-MULTI-MATCH-IMPORT-RULES.md`
- `docs/runbooks/J6-BACKUP-RESTORE-AND-RETENTION.md`
- `docs/runbooks/LIVE-J4-J5-CAMPAIGNS.md`
- `docs/validation/WO058-PLAYER-DETAILS-20260909.md`
- `docs/work_orders/active/WO-SS-20260907-058-bounded-live-j4-j5.md`
- `README.md`
- `scripts/Backup-Restore-J6.ps1`
- `scripts/Invoke-J6Retention.ps1`
- `src/main/java/com/bettingproject/sofascorelocal/adapter/persistence/JdbcJ5EventDataStore.java`
- `src/main/java/com/bettingproject/sofascorelocal/adapter/sofascore/eventdata/EventLineupsV3Parser.java`
- `src/main/java/com/bettingproject/sofascorelocal/adapter/sofascore/live/LivePayloadNormalizer.java`
- `src/main/java/com/bettingproject/sofascorelocal/adapter/web/LineupsPresentation.java`
- `src/main/java/com/bettingproject/sofascorelocal/adapter/web/PlayerStatisticsPresentation.java`
- `src/main/java/com/bettingproject/sofascorelocal/application/history/J6SemanticDiffService.java`
- `src/main/java/com/bettingproject/sofascorelocal/application/live/LiveCampaignService.java`
- `src/main/java/com/bettingproject/sofascorelocal/application/network/J5LocalJsonImportProcessor.java`
- `src/main/java/com/bettingproject/sofascorelocal/application/network/J5LocalJsonImportService.java`
- `src/main/java/com/bettingproject/sofascorelocal/application/network/J5RealEventDataService.java`
- `src/main/java/com/bettingproject/sofascorelocal/domain/eventdata/EventLineupPlayer.java`
- `src/main/java/com/bettingproject/sofascorelocal/domain/eventdata/J5EventDataObservation.java`
- `src/main/java/com/bettingproject/sofascorelocal/domain/eventdata/MissingLineupPlayer.java`
- `src/main/java/com/bettingproject/sofascorelocal/domain/eventdata/PlayerMatchStatistics.java`
- `src/main/java/com/bettingproject/sofascorelocal/domain/eventdata/TeamLineup.java`
- `src/main/resources/db/migration/V41__j5_lineups_player_details.sql`
- `src/main/resources/static/css/lineups.css`
- `src/main/resources/static/js/lineups.js`
- `src/main/resources/templates/fragments/lineups.html`
- `src/provider-playwright-qualification-test/java/com/bettingproject/sofascorelocal/adapter/web/LiveCampaignLineupsBrowserQualificationIT.java`
- `src/test/java/com/bettingproject/sofascorelocal/adapter/sofascore/eventdata/EventLineupsV3ParserTest.java`
- `src/test/java/com/bettingproject/sofascorelocal/adapter/web/LineupsPresentationTest.java`
- `src/test/java/com/bettingproject/sofascorelocal/adapter/web/PlayerStatisticsPresentationTest.java`
- `src/test/java/com/bettingproject/sofascorelocal/application/export/J7EnvelopeAssemblerTest.java`
- `src/test/java/com/bettingproject/sofascorelocal/application/history/J6SemanticDiffServiceTest.java`
- `src/test/java/com/bettingproject/sofascorelocal/application/network/J5LocalJsonImportProcessorTest.java`
- `src/test/java/com/bettingproject/sofascorelocal/application/network/J5LocalJsonImportServiceTest.java`
- `src/test/java/com/bettingproject/sofascorelocal/application/network/J5RealEventDataServiceTest.java`
- `src/test/java/com/bettingproject/sofascorelocal/application/network/J5SharedLeaseExclusivityTest.java`
- `src/test/java/com/bettingproject/sofascorelocal/build/J6NativeBinaryPipelineQualificationTest.java`
- `src/test/java/com/bettingproject/sofascorelocal/domain/eventdata/LineupEnrichmentContractTest.java`
- `src/test/java/com/bettingproject/sofascorelocal/integration/FlywayMigrationIT.java`
- `src/test/java/com/bettingproject/sofascorelocal/integration/J5LineupDetailsPersistenceIT.java`
- `src/test/java/com/bettingproject/sofascorelocal/integration/LiveCampaignPersistenceIT.java`

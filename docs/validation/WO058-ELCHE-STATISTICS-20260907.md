# WO-058 — Elche, fin de collecte et statistiques applicatives

Retour opérateur du 7 septembre 2026, après le commit `b4e85d613004e74d3a071e8af7c56ef86e74359e`.
Worktree `.tmp/wo058-live-j4-j5`, branche `feature/V0.1.0-RC01-CODEX-WO-SS-20260907-058`.
Statuts conservés : `EXPERIMENTAL`, `LOCAL_ONLY`, `NOT_PRODUCTION_APPROVED`, `NO_CRITICAL_DEPENDENCY`.

## Demande et fin de journée

Le propriétaire demande de corriger le nouveau schéma incompatible d'Elche–Real Sociedad et
de rendre les statistiques par période/possession/X-Y visibles dans l'application. Il confirme
la fin de la collecte du 07/09 et précise que `2nd half` est le libellé attendu : aucune
traduction de phase n'est introduite. Le prototype séparé du lot précédent reste historique.

La lecture SQL en transaction `READ ONLY` de la campagne
`ce33a3a5-af5a-4080-94e0-4e87aa7337ae` confirme `COMPLETED`, 15 appels et 558 672 octets.
La campagne contient quatre rencontres :

| Événement fournisseur | Rencontre | État final enregistré | Appels |
|---|---|---|---:|
| 16418278 | CE Sabadell — Córdoba | FINISHED_CONFIRMED | 4 |
| 16285006 | Udinese — Lazio | FINISHED_CONFIRMED | 4 |
| 16450882 | Estoril Praia — FC Arouca | FINISHED_CONFIRMED | 4 |
| 16416319 | Elche — Real Sociedad | STOPPED_SCHEMA_INCOMPATIBLE | 3 |

`COMPLETED` décrit la fin de l'exécution globale. Il ne signifie pas que la dernière séquence
J5 d'Elche soit complète : LINEUPS n'est pas appelée après le rejet d'Incidents. Les états
historiques restent conservés ; aucune reprise ni nouvelle collecte n'est déclenchée ici.
Sabadell a cette fois atteint Incidents V16 (snapshot 2418) puis LINEUPS (2419), conformément
aux observations opérateur, distinctes du replay technique du précédent snapshot 2340.

## Diagnostic exact d'Elche

| Métadonnée | Valeur lue |
|---|---|
| Endpoint | `GET /api/v1/event/16416319/incidents` |
| Snapshot / occurrence | 2427 / 2394 |
| Réception | `2026-09-07T21:39:41.935Z`, 23:39:41.935 Europe/Paris |
| HTTP / type | 200 / application/json |
| Corps | 64 615 octets, 26 incidents |
| SHA-256 | `da364bde7d1ea74642bbe80787bc1080cb4a2e459ac5b396b7c8bb94987288b6` |
| Parseur historique | event-incidents-v16 |
| Erreur reproduite | VALUE_OUT_OF_RANGE, `$.incidents[13].reason` |

J4 (2416) et Statistiques (2426) sont `PARSED`. Les octets du seul snapshot 2427 sont extraits
de `provider_snapshot.payload_raw` vers `.tmp/wo058-elche-incidents-snapshot-2427.json`, fichier
local ignoré. Le SHA calculé correspond exactement au SHA enregistré. Aucun corps complet
n'est ajouté à Git ni affiché dans les logs ; seuls codes, chemins et fragments nécessaires
au diagnostic sont exposés.

L'incident à l'indice 13 est un `card/red`, `time=64`, `isHome=false`, `rescinded=false`,
avec `reason="Professional handball"`. Le vocabulaire V16 comprend `Handball` mais pas ce
motif distinct. Le rejet est atomique ; ce n'est pas une erreur de syntaxe JSON.
Deux avertissements indépendants `PROVIDER_SENTINEL_NORMALIZED` concernent
`$.incidents[0].addedTime` et `$.incidents[20].addedTime`.

Le rejeu des mêmes octets compare directement les deux versions :

| Parseur | Résultat | Incidents normalisés | Erreurs bloquantes | Avertissements | Complétude |
|---|---|---:|---:|---:|---|
| V16 | SCHEMA_INCOMPATIBLE | aucun résultat publié | 1 | 2 | rejet atomique |
| V17 | PARSED | 26 | 0 | 2 | COMPLETE, 98/98, 100 % |

Les deux avertissements de sentinelle restent présents dans V17. Le replay ne modifie pas la
décision historique V16 ni le statut arrêté de la campagne. Les preuves locales ignorées sont
`.tmp/wo058-elche-v17-exact-replay.txt` (SHA-256
`7b3a6dfc37e98a23501454a4b2132853614575b861b80b53f5e2921421f9e73d`) et
`.tmp/wo058-final-campaign-metadata.json` (SHA-256
`b0ea5fbda11556f74514405a796e90b864397926a5c222791ae04f459e953628`).

## Correction bornée et persistance

V17 hérite de V16 et ajoute la seule chaîne exacte `Professional handball` au vocabulaire des
motifs de carton. Le motif, la couleur, le côté, la minute et le joueur restent ceux de
l'incident reçu. Aucune conversion en motif générique, but ou penalty n'est effectuée.
Le propriétaire confirme le sens « Main volontaire », utilisé dans les vues d’incidents ;
la valeur normalisée reste `Professional handball`. La minute manquante, les types invalides
et les motifs inconnus gardent leurs contrôles.
Les parseurs V1–V16 restent disponibles pour reproduire les anciennes interprétations.

Les parcours live, J5 direct et import local utilisent V17 avec la même version dans leur
provenance. V37 ajoute V17 à la contrainte SQL ; V36 et les migrations précédentes ne sont
pas modifiées. Les outils J6 suivent V37 ; les scripts de `b4e85d6` restent nécessaires à une
sauvegarde/restauration préalable d'une base encore en V36. Aucune migration ni réinterprétation
persistée n'est appliquée à la base opérateur pendant ce travail.

Les tests standards utilisent des fragments synthétiques ; le corps réel est rejoué par un
programme Java local ignoré, sans Spring, réseau ni écriture en base. Les tests PostgreSQL
exercent une base isolée V36 préremplie, puis V37, ainsi que le stockage et la relecture V17
avec snapshot, hashes, parseur et réception identiques, sans duplication de l'observation.

## Statistiques dans les pages de l'application

L'intégration réutilise les observations J5 normalisées et leur provenance. Le composant
partagé est raccordé aux pages campagne live, statistiques J5 et détail de rencontre avec
suivi live. Aucun appel supplémentaire n'est nécessaire pour afficher les données présentes.
Le modèle `StatisticsPresentation` groupe les métriques par période puis groupe source,
sans additionner ALL aux mi-temps. Seules les périodes reçues sont proposées ; ALL est choisie
par défaut lorsqu'elle existe. Sans JavaScript, toutes les périodes restent lisibles dans les
pages campagne et statistiques J5. Le détail secondaire de rencontre reçoit le composant via
son polling JavaScript et conserve son lien vers J5. Les noms de métriques et de groupes restent
ceux des données normalisées ; les captures françaises utilisent une fixture synthétique explicite.

La possession utilise une barre bicolore seulement si les deux pourcentages sont valides et
totalisent 100 %. Les fractions X/Y gardent leur texte source et disposent d'une jauge de taux
calculé ; une éventuelle valeur de pourcentage fournie doit être cohérente avec la fraction.
Une absence reste `—`, zéro reste zéro, 0/0 a un taux non défini et les ratios invalides ne
reçoivent pas de graphique. Les jauges HTML natives `meter` et le CSS externe respectent la
CSP existante sans autoriser de styles inline.

Le polling local conserve le sélecteur, son focus et la période choisie lorsqu'elle existe
encore. La disparition de la période provoque un repli annoncé ; les statistiques suivent le
même curseur d'observation que la provenance affichée. Le libellé sportif `2nd half` reste inchangé.

## Qualification de cette révision

Le port 8087 a été libéré par le propriétaire. Environnement : Windows, Java 25.0.4,
Spring Boot 4.1.0, Maven wrapper et Docker Desktop pour les bases PostgreSQL isolées.
Les commandes sont exécutées depuis le worktree WO-058, avec `JAVA_HOME` dirigé vers
`C:/Program Files/Java/jdk-25.0.4` et la CLI Docker Desktop présente dans `PATH`.

### Interface Chromium hors fournisseur

Le 08/09 à 00:08:15 Europe/Paris, trois tests ciblés passent, sans échec, erreur ni ignoré :
deux tests de formulaires existants et le nouveau test statistiques. Les vrais contrôleurs,
templates, CSS et JavaScript sont chargés via un pont MockMvc en mémoire ; aucune écoute HTTP
réelle, requête externe ou écriture dans la base opérateur n'est nécessaire. Le nouveau scénario
statistiques ne réalise aucun POST. Les captures portent explicitement des données synthétiques.

```powershell
.\mvnw.cmd --offline '-Dmaven.repo.local=C:/Users/geoff/.m2/repository' '-Pprovider-playwright-runtime,provider-playwright-local-qualification' -DskipTests package
.\mvnw.cmd --offline '-Dmaven.repo.local=C:/Users/geoff/.m2/repository' '-Pprovider-playwright-runtime,provider-playwright-local-qualification' '-DskipTests=false' '-DskipITs=false' '-Dit.test=LiveCampaignStatisticsBrowserQualificationIT,LiveCampaignFormBrowserQualificationIT' '-Dprovider.playwright.browser-cache=C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo058-live-j4-j5/.tmp/provider-playwright-browsers' 'failsafe:integration-test@provider-playwright-loopback-qualification' 'failsafe:verify@provider-playwright-loopback-qualification'
```

`PLAYWRIGHT_BROWSERS_PATH` désigne le cache Chromium existant ci-dessus pour ce lancement explicite.
Les contrôles portent sur SSR sans JavaScript, sélection initiale ALL, choix 1ST et focus conservés
après polling, disparition de 2ND avec repli annoncé, possession et fractions, valeurs absentes,
zéro/0/0, échappement du contenu, absence d'erreurs CSP/JavaScript et de débordement à 390 px.
Le script de qualification complet inclut désormais 13 tests ; ce passage cible uniquement les
trois tests UI touchés, et ne prétend pas réexécuter les dix autres scénarios de transport.

Les captures, XML et logs sont copiés dans `.tmp/wo058-ui-evidence` avant le `clean` :

| Preuve | SHA-256 |
|---|---|
| statistics-runtime-desktop.png | a4869a28c8394ac5f051fe66155d21e308e9b73a13f831290c1435d10a28a43c |
| statistics-runtime-mobile.png | 79b2ecc0d2527223210f3d72e3064f2a3d99fb2ed3f069a6286e66aade7d33ee |
| statistics-runtime-ssr-desktop.png | 85332a113da744b94a99af8f4586dd4635298189a6ed6ceb58dd5fbb0205784d |
| wo058-statistics-ui-qualification.log | d3c3eef727fe2b02e3e4b164c6a04397b08a9073d8ae375c43d7165fdf048337 |

Inspection visuelle des trois captures effectuée après les assertions navigateur. Deux essais
de packaging préalables s'étaient arrêtés avant compilation/tests : dépôt effectif incomplet
dans le sandbox puis option courte `-o` ambiguë pour le wrapper PowerShell. L'exécution native,
le cache existant explicite et `--offline` corrigent ces conditions ; aucun téléchargement.

### Maven et PostgreSQL

La commande complète, sans filtre de méthode ni exclusion, est :

```powershell
.\mvnw.cmd clean verify -Pintegration-tests
```

Premier passage achevé à 00:15:01 Paris : Surefire 1 590 cas, zéro échec/erreur, cinq ignorés ;
Failsafe 138 cas, un échec d'assertion de version dans l'import multirencontre courant. Les lignes
enregistrées étaient bien V17, tandis que l'attente du test restait V16. Cette attente seule est
alignée sur V17 ; les scénarios explicitement historiques V16 restent inchangés. Journal conservé
`.tmp/wo058-elche-statistics-verify.log`, SHA-256
`2907f2ba21961f83d4279d2521b130a24854ddd330de6a2aaa82c507aa0d94e5`.

Qualification finale après cette correction : **BUILD SUCCESS**, code natif zéro, le 08/09/2026
à **00:20:31 Europe/Paris**, durée **5 min 01 s**, avec la même commande complète et aucun filtre.
Les XML effectivement relus donnent :

| Suite | Rapports | Cas recensés | Échecs | Erreurs | Ignorés |
|---|---:|---:|---:|---:|---:|
| Surefire | 185 | 1 590 | 0 | 0 | 5 |
| Failsafe PostgreSQL | 6 | 138 | 0 | 0 | 0 |

Les cinq ignorés sont quatre contrôles de liens symboliques non disponibles dans cet
environnement Windows (deux `LocalJ7ExportFileStoreTest`, deux `J8BenchmarkExportCommandTest`)
et `dockerQualificationPassesThreeSequentialRunsAtEffectiveDefaults`, dont la propriété
opt-in `j6.docker.qualification` est absente. Les contrôles J6 natifs ordinaires et le scénario
utilisant 8087 s'exécutent ; aucun test n'est exclu pour contourner le port. Les 73 tests
`FlywayMigrationIT`, dont upgrade prérempli V36→V37, préservation historique et replay V17,
ainsi que les 28 tests `LiveCampaignPersistenceIT`, sont inclus et passent.

Journal final `.tmp/wo058-elche-statistics-verify-r2.log`, SHA-256
`573d62fc379090e709f2bb17e24d58b6122d887d1a4088354876cbfb1858e437` ; code conservé dans le
fichier `.exit` associé. Le replay exact est aussi recompilé avec `javac`, le classpath du
rapport Surefire V17 courant et les classes applicatives de cette qualification, puis exécuté
avec `java WO058ElcheDiagnostic` sur le snapshot local. Sa sortie
`.tmp/wo058-elche-v17-final-replay.txt` a le même SHA-256 que le premier replay V17 ci-dessus.

Les deux scripts JavaScript passent `node --check`. Les trois scripts PowerShell modifiés
passent l'analyse syntaxique AST ; les 45 fichiers modifiés sont décodables en UTF-8. Le diff
est relu, `git diff --check` passe, les liens du rapport sont résolus et la recherche de motifs
de secrets ne relève rien. Aucun `.env`, ADR, POM ou réglage d'écoute n'est modifié :
`server.address=127.0.0.1` et les défauts fournisseur désactivés sont conservés. Les payloads,
captures et rapports runtime restent ignorés. Le port 8087 est libre à la fin des tests.

La relecture indépendante du parseur, de V37/J6 et des composants graphiques ne relève plus
de défaut concret. La qualification porte sur le candidat du worktree ; le checkout Eclipse,
la base opérateur et les anciennes décisions de collecte ne sont pas actualisés par ces tests.
La migration opérateur suit la procédure distincte de sauvegarde/restauration du runbook.
Le WO reste en cours, avec revue humaine et fusion à effectuer séparément ; aucun push.

## Inventaire des fichiers modifiés

- [CHANGELOG.md](../../CHANGELOG.md)
- [docs/architecture/J5-GUARDED-REAL-EVENT-DATA.md](../../docs/architecture/J5-GUARDED-REAL-EVENT-DATA.md)
- [docs/architecture/J5-OFFLINE-MULTI-MATCH-IMPORT.md](../../docs/architecture/J5-OFFLINE-MULTI-MATCH-IMPORT.md)
- [docs/architecture/LIVE-J4-J5-CAMPAIGNS.md](../../docs/architecture/LIVE-J4-J5-CAMPAIGNS.md)
- [docs/requirements/J5-FOOTBALL-INCIDENT-RULES.md](../../docs/requirements/J5-FOOTBALL-INCIDENT-RULES.md)
- [docs/runbooks/J6-BACKUP-RESTORE-AND-RETENTION.md](../../docs/runbooks/J6-BACKUP-RESTORE-AND-RETENTION.md)
- [docs/runbooks/LIVE-J4-J5-CAMPAIGNS.md](../../docs/runbooks/LIVE-J4-J5-CAMPAIGNS.md)
- [docs/validation/WO058-ELCHE-STATISTICS-20260907.md](../../docs/validation/WO058-ELCHE-STATISTICS-20260907.md)
- [docs/work_orders/completed/WO-SS-20260907-058-bounded-live-j4-j5.md](../../docs/work_orders/completed/WO-SS-20260907-058-bounded-live-j4-j5.md)
- [scripts/Backup-Restore-J6.ps1](../../scripts/Backup-Restore-J6.ps1)
- [scripts/Invoke-J6Retention.ps1](../../scripts/Invoke-J6Retention.ps1)
- [scripts/Invoke-LivePlaywrightLoopbackQualification.ps1](../../scripts/Invoke-LivePlaywrightLoopbackQualification.ps1)
- [src/main/java/com/bettingproject/sofascorelocal/adapter/sofascore/eventdata/EventIncidentsV16Parser.java](../../src/main/java/com/bettingproject/sofascorelocal/adapter/sofascore/eventdata/EventIncidentsV16Parser.java)
- [src/main/java/com/bettingproject/sofascorelocal/adapter/sofascore/eventdata/EventIncidentsV17Parser.java](../../src/main/java/com/bettingproject/sofascorelocal/adapter/sofascore/eventdata/EventIncidentsV17Parser.java)
- [src/main/java/com/bettingproject/sofascorelocal/adapter/sofascore/live/LivePayloadNormalizer.java](../../src/main/java/com/bettingproject/sofascorelocal/adapter/sofascore/live/LivePayloadNormalizer.java)
- [src/main/java/com/bettingproject/sofascorelocal/adapter/web/IncidentPresentation.java](../../src/main/java/com/bettingproject/sofascorelocal/adapter/web/IncidentPresentation.java)
- [src/main/java/com/bettingproject/sofascorelocal/adapter/web/J5EventDataController.java](../../src/main/java/com/bettingproject/sofascorelocal/adapter/web/J5EventDataController.java)
- [src/main/java/com/bettingproject/sofascorelocal/adapter/web/LiveCampaignPresentation.java](../../src/main/java/com/bettingproject/sofascorelocal/adapter/web/LiveCampaignPresentation.java)
- [src/main/java/com/bettingproject/sofascorelocal/adapter/web/StatisticsPresentation.java](../../src/main/java/com/bettingproject/sofascorelocal/adapter/web/StatisticsPresentation.java)
- [src/main/java/com/bettingproject/sofascorelocal/application/live/LiveCampaignService.java](../../src/main/java/com/bettingproject/sofascorelocal/application/live/LiveCampaignService.java)
- [src/main/java/com/bettingproject/sofascorelocal/application/network/J5LocalJsonImportProcessor.java](../../src/main/java/com/bettingproject/sofascorelocal/application/network/J5LocalJsonImportProcessor.java)
- [src/main/java/com/bettingproject/sofascorelocal/application/network/J5RealEventDataService.java](../../src/main/java/com/bettingproject/sofascorelocal/application/network/J5RealEventDataService.java)
- [src/main/java/com/bettingproject/sofascorelocal/domain/eventdata/J5EventDataObservation.java](../../src/main/java/com/bettingproject/sofascorelocal/domain/eventdata/J5EventDataObservation.java)
- [src/main/resources/db/migration/V37__j5_incidents_professional_handball.sql](../../src/main/resources/db/migration/V37__j5_incidents_professional_handball.sql)
- [src/main/resources/static/css/statistics.css](../../src/main/resources/static/css/statistics.css)
- [src/main/resources/static/js/live-campaign.js](../../src/main/resources/static/js/live-campaign.js)
- [src/main/resources/static/js/statistics.js](../../src/main/resources/static/js/statistics.js)
- [src/main/resources/templates/event-detail.html](../../src/main/resources/templates/event-detail.html)
- [src/main/resources/templates/event-statistics.html](../../src/main/resources/templates/event-statistics.html)
- [src/main/resources/templates/fragments/statistics.html](../../src/main/resources/templates/fragments/statistics.html)
- [src/main/resources/templates/live-campaign.html](../../src/main/resources/templates/live-campaign.html)
- [src/provider-playwright-qualification-test/java/com/bettingproject/sofascorelocal/adapter/web/LiveCampaignStatisticsBrowserQualificationIT.java](../../src/provider-playwright-qualification-test/java/com/bettingproject/sofascorelocal/adapter/web/LiveCampaignStatisticsBrowserQualificationIT.java)
- [src/test/java/com/bettingproject/sofascorelocal/adapter/sofascore/eventdata/EventIncidentsV17ParserTest.java](../../src/test/java/com/bettingproject/sofascorelocal/adapter/sofascore/eventdata/EventIncidentsV17ParserTest.java)
- [src/test/java/com/bettingproject/sofascorelocal/adapter/sofascore/live/LivePayloadNormalizerTest.java](../../src/test/java/com/bettingproject/sofascorelocal/adapter/sofascore/live/LivePayloadNormalizerTest.java)
- [src/test/java/com/bettingproject/sofascorelocal/adapter/web/IncidentPresentationTest.java](../../src/test/java/com/bettingproject/sofascorelocal/adapter/web/IncidentPresentationTest.java)
- [src/test/java/com/bettingproject/sofascorelocal/adapter/web/J5EventDataControllerTest.java](../../src/test/java/com/bettingproject/sofascorelocal/adapter/web/J5EventDataControllerTest.java)
- [src/test/java/com/bettingproject/sofascorelocal/adapter/web/LiveCampaignPresentationTest.java](../../src/test/java/com/bettingproject/sofascorelocal/adapter/web/LiveCampaignPresentationTest.java)
- [src/test/java/com/bettingproject/sofascorelocal/adapter/web/StatisticsPresentationTest.java](../../src/test/java/com/bettingproject/sofascorelocal/adapter/web/StatisticsPresentationTest.java)
- [src/test/java/com/bettingproject/sofascorelocal/application/network/J5LocalJsonImportProcessorTest.java](../../src/test/java/com/bettingproject/sofascorelocal/application/network/J5LocalJsonImportProcessorTest.java)
- [src/test/java/com/bettingproject/sofascorelocal/application/network/J5LocalJsonImportServiceTest.java](../../src/test/java/com/bettingproject/sofascorelocal/application/network/J5LocalJsonImportServiceTest.java)
- [src/test/java/com/bettingproject/sofascorelocal/application/network/J5RealEventDataServiceTest.java](../../src/test/java/com/bettingproject/sofascorelocal/application/network/J5RealEventDataServiceTest.java)
- [src/test/java/com/bettingproject/sofascorelocal/application/network/J5SharedLeaseExclusivityTest.java](../../src/test/java/com/bettingproject/sofascorelocal/application/network/J5SharedLeaseExclusivityTest.java)
- [src/test/java/com/bettingproject/sofascorelocal/build/J6NativeBinaryPipelineQualificationTest.java](../../src/test/java/com/bettingproject/sofascorelocal/build/J6NativeBinaryPipelineQualificationTest.java)
- [src/test/java/com/bettingproject/sofascorelocal/integration/FlywayMigrationIT.java](../../src/test/java/com/bettingproject/sofascorelocal/integration/FlywayMigrationIT.java)
- [src/test/java/com/bettingproject/sofascorelocal/integration/LiveCampaignPersistenceIT.java](../../src/test/java/com/bettingproject/sofascorelocal/integration/LiveCampaignPersistenceIT.java)

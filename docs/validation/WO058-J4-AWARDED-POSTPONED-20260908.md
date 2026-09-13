# WO-058 — résultats J4, victoire sur tapis vert et report

Statuts conservés : `EXPERIMENTAL`, `LOCAL_ONLY`, `NOT_PRODUCTION_APPROVED`,
`NO_CRITICAL_DEPENDENCY`. Ce complément reste dans le Work Order ouvert ; aucune fusion,
clôture ni nouvelle campagne fournisseur n'est impliquée.

Base de ce retour : `1014926adcba742327d53d186d81de1a7d77f4a4`, branche
`feature/V0.1.0-RC01-CODEX-WO-SS-20260907-058`, worktree `.tmp/wo058-live-j4-j5`.

## Autorité et observations

Le 08/09/2026, le propriétaire confirme les encadrés statistiques et demande :

- `finished` avec `$.event.isAwarded` booléen `true` : « Victoire sur tapis vert » ;
- score J4 depuis les deux valeurs `homeScore.display` et `awayScore.display` ;
- exclusion des rencontres `postponed`, comme celles déjà `finished` ;
- arrêt individuel si un nouveau J4 renvoie `postponed` après le lancement ;
- aucune modification du traitement de `canceled`.

La répétition de `homeScore` dans la première phrase est résolue par la règle explicite
domicile/extérieur fournie ensuite. Le score extérieur provient bien de `awayScore.display`.

Les captures montrent Oxford United–Reading (16439935), `postponed`, snapshot 2458,
et une préparation qui l'acceptait. La capture NK Neretva Metković–HNK Šibenik (16946438)
montre `finished` avec une seule observation `tournament-scheduled-v1`, snapshot 2452,
et « Aucun détail importé ». Elle ne prouve pas la présence d'`isAwarded` dans un JSON J4.
Aucune victoire sur tapis vert n'est déduite du seul statut terminé. Les nouveaux contrats
sont qualifiés par des données synthétiques ; aucun nouveau contenu fournisseur n'est revendiqué.

## Réalisation

`event-details-v3` étend le parseur V2 conservé intact. Les détails normalisés conservent
`isAwarded`, `homeDisplayScore`, `awayDisplayScore`, avec la provenance habituelle.
L'absence ou `null` donne une valeur normalisée absente ; `false` et zéro sont conservés.
Un objet score vide n'invente pas de résultat. Les scores `display` sont des entiers 0..999 ;
un type ou une borne invalide produit un problème localisé, sans résultat partiel.

V38 ajoute trois colonnes nullables et les contraintes correspondantes. Les anciennes lignes,
snapshots, occurrences et empreintes restent intacts. Les hashes V1/V2 conservent leur format ;
V3 utilise `event-detail-observation-v2` et inclut la présence et la valeur des nouveaux champs.
Le rejeu V3 ajoute une observation et reste idempotent ; aucune mise à jour rétroactive ou
renormalisation automatique de la base opérateur n'est effectuée.

Les chemins manuels J4 et le live utilisent V3. Le live porte la projection `j4-live-score-v2`,
conservant les observations de score antérieures et ajoutant la présence du drapeau d'attribution.
La lecture des anciennes projections live V1 utilise déjà leurs valeurs `display` lorsqu'elles
existent. Le score visible exige les deux valeurs, sans repli sur `current` ni calcul depuis J5.
`finished` reste le type technique et le critère d'exclusion ; seul le libellé devient
« Victoire sur tapis vert » lorsque le drapeau est explicitement vrai.

Le tableau et le détail J4 associent le résultat au même snapshot/hash/statut que l'observation
canonique. Une nouvelle observation J3 ne récupère pas le score ou l'attribution d'un ancien J4.
La campagne utilise son identifiant de détail exact ; une observation manuelle plus récente
n'écrase pas cette histoire. Le détail affiche séparément le score canonique et celui du suivi live.

La préparation retire `postponed` avant le calcul de capacité et de cadence. Une sélection
entièrement exclue ne crée pas de campagne. Le lancement relit les statuts avant admission,
puis après acquisition et avant navigateur ; les nouvelles exclusions prennent
`STOPPED_ALREADY_POSTPONED` sans modifier le manifeste historique. Pendant la collecte,
un J4 `postponed` publie l'observation et passe ce match en `STOPPED_POSTPONED`, annule
ses échéances J4/J5 et ne déclenche aucun cycle final. Les autres rencontres continuent.
Les gardes et le comportement existants de `canceled` restent inchangés.

J6 compare les trois nouveaux champs, notamment absence → false ou zéro. J7 conserve son
enveloppe v1 et ses huit champs de détail existants : les trois nouveaux champs ne sont pas
exportés par ce contrat. Le contrôle de provenance/hash est néanmoins effectué sur le détail
normalisé V3 complet. Modifier le contenu de l'export nécessite une évolution distincte du contrat.

Les scripts de sauvegarde/restauration et rétention J6 exigent le schéma courant V38.
Le format de manifeste et son empreinte de provenance restent inchangés. Le test de roundtrip
PostgreSQL compare séparément les trois nouvelles colonnes et leur provenance avant/après
restauration ; aucune sauvegarde ou purge de la base opérateur n'est exécutée.

## Qualification

Les skills `ss-data-contract-replay`, `ss-verify` et `ss-postgres-change` sont appliqués.
Java 25.0.4 et le port 8087 libre ont été contrôlés avant les tests. Aucun lanceur opérateur
n'a été arrêté par l'agent. Maven utilise le wrapper du worktree et PostgreSQL de test isolé.

Première suite ciblée : 309 cas, 0 échec et 57 erreurs de contexte Web dues au mock
`EventDetailsStore` absent de `LiveCampaignControllerTest`. Les 252 autres cas passent.
Le montage Web est corrigé avant la porte complète. Le log initial reste conservé :
`.tmp/wo058-j4-awarded-postponed-targeted.log`.

Premier `mvnw.cmd clean verify` : 1 676 tests Surefire, 1 échec, 0 erreur, 5 ignorés,
`BUILD FAILURE` à 07:58:03Z. Une attente de `LiveResponseProcessorTest` désignait encore
V2 alors que la publication courante est désormais V3 ; cette attente est actualisée.
Failsafe n'a pas été atteint. Log conservé : `.tmp/wo058-j4-awarded-postponed-verify.log`.

Après correction, `mvnw.cmd clean verify` réussit le 08/09 à **08:08:07Z** (10:08:07 Paris),
code natif 0, en 5 min 26 s. Les XML relus donnent **1 676 tests Surefire**, 0 échec,
0 erreur, 5 ignorés, et **140 tests Failsafe**, 0 échec, 0 erreur, 0 ignoré. Les cinq exclusions
restent les quatre cas de liens symboliques Windows J7/J8 et l'opt-in J6 Docker absent.
Le log vert est `.tmp/wo058-j4-awarded-postponed-verify-attempt2.log` ; les XML et totaux
sont copiés dans `.tmp/wo058-j4-awarded-postponed-evidence/clean-verify/`.

La suite nouvelle `J4DisplayScorePersistenceIT` passe ses deux tests ; la suite de persistance
live passe ses 28 tests. L'IT J6 de restauration compare aussi les valeurs true/0/3 et leur
provenance après `pg_dump`/`pg_restore` dans deux bases isolées.

Le profil explicite `mvnw.cmd -Pintegration-tests verify` est également exécuté parce que
ce retour modifie la persistance, conformément à AGENTS.md. L'exécution Failsafe héritée de
`clean verify` est documentée ci-dessus ; aucun lanceur `Verify-Local.ps1` supplémentaire
encapsulant les mêmes portes n'est ajouté.

Le profil explicite réussit à **08:14:05Z** (10:14:05 Paris), code natif 0, en 5 min 11 s :
**1 676 tests Surefire**, 0 échec, 0 erreur, 5 ignorés, et **140 tests Failsafe**, 0 échec,
0 erreur, 0 ignoré. Le log est `.tmp/wo058-j4-awarded-postponed-integration.log` ; les XML
et totaux distincts sont conservés dans `.tmp/wo058-j4-awarded-postponed-evidence/integration-profile/`.

La qualification Chromium opt-in utilise le cache existant, des données synthétiques et
uniquement des routes locales ou MockMvc. Aucun téléchargement de navigateur ni appel fournisseur.
Commandes retenues après les deux portes complètes :

```powershell
$env:PLAYWRIGHT_BROWSERS_PATH='C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo058-live-j4-j5/.tmp/provider-playwright-browsers'
$env:PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD='1'
.\mvnw.cmd --offline '-Dmaven.repo.local=C:/Users/geoff/.m2/repository' '-Pprovider-playwright-runtime,provider-playwright-local-qualification' -DskipTests package
.\mvnw.cmd --offline '-Dmaven.repo.local=C:/Users/geoff/.m2/repository' '-Pprovider-playwright-runtime,provider-playwright-local-qualification' '-DskipTests=false' '-DskipITs=false' '-Dit.test=LiveCampaignBrowserQualificationIT,LiveCampaignFormBrowserQualificationIT,LiveCampaignStatisticsBrowserQualificationIT' '-Dprovider.playwright.browser-cache=C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo058-live-j4-j5/.tmp/provider-playwright-browsers' 'failsafe:integration-test@provider-playwright-loopback-qualification' 'failsafe:verify@provider-playwright-loopback-qualification'
```

La compilation du profil UI réussit à 08:14:57Z. Le premier essai Chromium échoue à
08:15:56Z : quatre erreurs avant lancement du navigateur, `PLAYWRIGHT_BROWSERS_PATH` absent
du processus appelant (`Path.of(null)` dans la vérification de cache). Le cache existe ; les
deux variables ci-dessus sont renseignées pour la relance. Aucun code applicatif n'est modifié.
Log et XML du premier essai : `.tmp/wo058-j4-awarded-postponed-ui.log` et
`.tmp/wo058-j4-awarded-postponed-evidence/ui-attempt1/`.

La relance Chromium réussit à **08:18:59Z** (10:18:59 Paris), code natif 0, en 1 min 38 s :
**4 tests, 0 échec, 0 erreur, 0 ignoré**. Le scénario de rafraîchissement vérifie les deux scores,
la protection du résultat canonique récent face à une campagne plus ancienne, l'exclusion de
`postponed` avec message conservé et `canceled` toujours sélectionnable. Les deux origines des
formulaires et les panneaux statistiques repliables passent également. Les assertions imposent
zéro requête externe. Log : `.tmp/wo058-j4-awarded-postponed-ui-attempt2.log` ; XML et totaux :
`.tmp/wo058-j4-awarded-postponed-evidence/ui-pass/`.

Empreintes SHA-256 des logs verts :

| Porte | SHA-256 |
|---|---|
| `clean verify` | `1f4bb1c16a7c84a8a9fb0166a086eb2575c7e9e2acec50e455faef25ed9fcf1b` |
| `-Pintegration-tests verify` | `6a547a03153275e4880ed3165e4af201e5aba6f70ed628c80d1ef8b075e1eaf8` |
| Chromium | `86619fba9216c49bf0a08ce1ed7675b0536d3beb948945d5f5d856932ae0c09c` |

La revue indépendante a relevé puis vérifié deux corrections : une cible distincte pour le
score du panneau live et la conservation du message de report lors du polling. Aucun autre
défaut concret n'est signalé. Analyse syntaxique PowerShell des deux scripts J6, comparaison
des 36 champs de manifeste source/restauration/rétention, `node --check`, UTF-8 strict,
liens locaux du rapport, contrôle des secrets reconnaissables et `git diff --check` passent.
La configuration effective reste `server.address=127.0.0.1`, fournisseur désactivé par défaut.
Les anciens parseurs/migrations et les références immuables restent inchangés.

## Livraison

La migration s'appliquera au prochain démarrage de l'application mise à jour. Les champs des
observations historiques V1/V2 restent absents jusqu'à une nouvelle collecte J4 ou un rejeu
explicitement demandé. La rencontre montrée seulement en J3 nécessite un détail J4 pour prouver
une victoire attribuée et afficher son score.

Le commit local est prévu comme dernière mutation avant remise, selon l'autorisation du WO.
Pas de push, fusion, clôture ni appel fournisseur.

## Fichiers modifiés

- [CHANGELOG.md](../../CHANGELOG.md)
- [docs/architecture/J4-CANONICAL-EVENTS-AND-LOCAL-DETAIL.md](../../docs/architecture/J4-CANONICAL-EVENTS-AND-LOCAL-DETAIL.md)
- [docs/architecture/LIVE-J4-J5-CAMPAIGNS.md](../../docs/architecture/LIVE-J4-J5-CAMPAIGNS.md)
- [docs/runbooks/J6-BACKUP-RESTORE-AND-RETENTION.md](../../docs/runbooks/J6-BACKUP-RESTORE-AND-RETENTION.md)
- [docs/validation/WO058-J4-AWARDED-POSTPONED-20260908.md](../../docs/validation/WO058-J4-AWARDED-POSTPONED-20260908.md)
- [docs/work_orders/completed/WO-SS-20260907-058-bounded-live-j4-j5.md](../../docs/work_orders/completed/WO-SS-20260907-058-bounded-live-j4-j5.md)
- [scripts/Backup-Restore-J6.ps1](../../scripts/Backup-Restore-J6.ps1)
- [scripts/Invoke-J6Retention.ps1](../../scripts/Invoke-J6Retention.ps1)
- [src/main/java/com/bettingproject/sofascorelocal/adapter/persistence/JdbcEventDetailsStore.java](../../src/main/java/com/bettingproject/sofascorelocal/adapter/persistence/JdbcEventDetailsStore.java)
- [src/main/java/com/bettingproject/sofascorelocal/adapter/sofascore/eventdetails/EventDetailsV3Parser.java](../../src/main/java/com/bettingproject/sofascorelocal/adapter/sofascore/eventdetails/EventDetailsV3Parser.java)
- [src/main/java/com/bettingproject/sofascorelocal/adapter/sofascore/live/LivePayloadNormalizer.java](../../src/main/java/com/bettingproject/sofascorelocal/adapter/sofascore/live/LivePayloadNormalizer.java)
- [src/main/java/com/bettingproject/sofascorelocal/adapter/web/LiveCampaignController.java](../../src/main/java/com/bettingproject/sofascorelocal/adapter/web/LiveCampaignController.java)
- [src/main/java/com/bettingproject/sofascorelocal/adapter/web/LiveCampaignPresentation.java](../../src/main/java/com/bettingproject/sofascorelocal/adapter/web/LiveCampaignPresentation.java)
- [src/main/java/com/bettingproject/sofascorelocal/application/event/J4EventQueryService.java](../../src/main/java/com/bettingproject/sofascorelocal/application/event/J4EventQueryService.java)
- [src/main/java/com/bettingproject/sofascorelocal/application/event/J4EventResult.java](../../src/main/java/com/bettingproject/sofascorelocal/application/event/J4EventResult.java)
- [src/main/java/com/bettingproject/sofascorelocal/application/event/J4EventSearchItem.java](../../src/main/java/com/bettingproject/sofascorelocal/application/event/J4EventSearchItem.java)
- [src/main/java/com/bettingproject/sofascorelocal/application/event/J4ParsedEventDetailsPersistenceService.java](../../src/main/java/com/bettingproject/sofascorelocal/application/event/J4ParsedEventDetailsPersistenceService.java)
- [src/main/java/com/bettingproject/sofascorelocal/application/history/J6SemanticDiffService.java](../../src/main/java/com/bettingproject/sofascorelocal/application/history/J6SemanticDiffService.java)
- [src/main/java/com/bettingproject/sofascorelocal/application/live/LiveCampaignService.java](../../src/main/java/com/bettingproject/sofascorelocal/application/live/LiveCampaignService.java)
- [src/main/java/com/bettingproject/sofascorelocal/application/live/LiveSchedule.java](../../src/main/java/com/bettingproject/sofascorelocal/application/live/LiveSchedule.java)
- [src/main/java/com/bettingproject/sofascorelocal/application/network/J4RealEventDetailsPhase1Service.java](../../src/main/java/com/bettingproject/sofascorelocal/application/network/J4RealEventDetailsPhase1Service.java)
- [src/main/java/com/bettingproject/sofascorelocal/application/network/J4RealEventDetailsPhase2Service.java](../../src/main/java/com/bettingproject/sofascorelocal/application/network/J4RealEventDetailsPhase2Service.java)
- [src/main/java/com/bettingproject/sofascorelocal/domain/eventdetails/EventDetailObservation.java](../../src/main/java/com/bettingproject/sofascorelocal/domain/eventdetails/EventDetailObservation.java)
- [src/main/java/com/bettingproject/sofascorelocal/domain/eventdetails/EventDetails.java](../../src/main/java/com/bettingproject/sofascorelocal/domain/eventdetails/EventDetails.java)
- [src/main/resources/db/migration/V38__j4_display_scores_and_award.sql](../../src/main/resources/db/migration/V38__j4_display_scores_and_award.sql)
- [src/main/resources/static/js/live-campaign.js](../../src/main/resources/static/js/live-campaign.js)
- [src/main/resources/templates/event-detail.html](../../src/main/resources/templates/event-detail.html)
- [src/main/resources/templates/events.html](../../src/main/resources/templates/events.html)
- [src/main/resources/templates/live-campaign-ineligible.html](../../src/main/resources/templates/live-campaign-ineligible.html)
- [src/main/resources/templates/live-campaign.html](../../src/main/resources/templates/live-campaign.html)
- [src/provider-playwright-qualification-test/java/com/bettingproject/sofascorelocal/adapter/web/LiveCampaignBrowserQualificationIT.java](../../src/provider-playwright-qualification-test/java/com/bettingproject/sofascorelocal/adapter/web/LiveCampaignBrowserQualificationIT.java)
- [src/provider-playwright-qualification-test/java/com/bettingproject/sofascorelocal/adapter/web/LiveCampaignFormBrowserQualificationIT.java](../../src/provider-playwright-qualification-test/java/com/bettingproject/sofascorelocal/adapter/web/LiveCampaignFormBrowserQualificationIT.java)
- [src/provider-playwright-qualification-test/java/com/bettingproject/sofascorelocal/adapter/web/LiveCampaignStatisticsBrowserQualificationIT.java](../../src/provider-playwright-qualification-test/java/com/bettingproject/sofascorelocal/adapter/web/LiveCampaignStatisticsBrowserQualificationIT.java)
- [src/test/java/com/bettingproject/sofascorelocal/adapter/sofascore/eventdetails/EventDetailsV3ParserTest.java](../../src/test/java/com/bettingproject/sofascorelocal/adapter/sofascore/eventdetails/EventDetailsV3ParserTest.java)
- [src/test/java/com/bettingproject/sofascorelocal/adapter/sofascore/live/LivePayloadNormalizerTest.java](../../src/test/java/com/bettingproject/sofascorelocal/adapter/sofascore/live/LivePayloadNormalizerTest.java)
- [src/test/java/com/bettingproject/sofascorelocal/adapter/web/EventExplorerControllerTest.java](../../src/test/java/com/bettingproject/sofascorelocal/adapter/web/EventExplorerControllerTest.java)
- [src/test/java/com/bettingproject/sofascorelocal/adapter/web/LiveCampaignControllerTest.java](../../src/test/java/com/bettingproject/sofascorelocal/adapter/web/LiveCampaignControllerTest.java)
- [src/test/java/com/bettingproject/sofascorelocal/adapter/web/LiveCampaignPresentationTest.java](../../src/test/java/com/bettingproject/sofascorelocal/adapter/web/LiveCampaignPresentationTest.java)
- [src/test/java/com/bettingproject/sofascorelocal/application/event/J4EventQueryServiceTest.java](../../src/test/java/com/bettingproject/sofascorelocal/application/event/J4EventQueryServiceTest.java)
- [src/test/java/com/bettingproject/sofascorelocal/application/export/J7EnvelopeAssemblerTest.java](../../src/test/java/com/bettingproject/sofascorelocal/application/export/J7EnvelopeAssemblerTest.java)
- [src/test/java/com/bettingproject/sofascorelocal/application/history/J6SemanticDiffServiceTest.java](../../src/test/java/com/bettingproject/sofascorelocal/application/history/J6SemanticDiffServiceTest.java)
- [src/test/java/com/bettingproject/sofascorelocal/application/live/LiveCampaignServiceTest.java](../../src/test/java/com/bettingproject/sofascorelocal/application/live/LiveCampaignServiceTest.java)
- [src/test/java/com/bettingproject/sofascorelocal/application/live/LiveResponseProcessorTest.java](../../src/test/java/com/bettingproject/sofascorelocal/application/live/LiveResponseProcessorTest.java)
- [src/test/java/com/bettingproject/sofascorelocal/application/live/LiveScheduleTest.java](../../src/test/java/com/bettingproject/sofascorelocal/application/live/LiveScheduleTest.java)
- [src/test/java/com/bettingproject/sofascorelocal/application/network/J4Phase1SharedLeaseExclusivityTest.java](../../src/test/java/com/bettingproject/sofascorelocal/application/network/J4Phase1SharedLeaseExclusivityTest.java)
- [src/test/java/com/bettingproject/sofascorelocal/application/network/J4RealEventDetailsPhase1ServiceTest.java](../../src/test/java/com/bettingproject/sofascorelocal/application/network/J4RealEventDetailsPhase1ServiceTest.java)
- [src/test/java/com/bettingproject/sofascorelocal/application/network/J4RealEventDetailsPhase2ServiceTest.java](../../src/test/java/com/bettingproject/sofascorelocal/application/network/J4RealEventDetailsPhase2ServiceTest.java)
- [src/test/java/com/bettingproject/sofascorelocal/build/J6NativeBinaryPipelineQualificationTest.java](../../src/test/java/com/bettingproject/sofascorelocal/build/J6NativeBinaryPipelineQualificationTest.java)
- [src/test/java/com/bettingproject/sofascorelocal/domain/eventdetails/EventDetailsDisplayScoreTest.java](../../src/test/java/com/bettingproject/sofascorelocal/domain/eventdetails/EventDetailsDisplayScoreTest.java)
- [src/test/java/com/bettingproject/sofascorelocal/integration/FlywayMigrationIT.java](../../src/test/java/com/bettingproject/sofascorelocal/integration/FlywayMigrationIT.java)
- [src/test/java/com/bettingproject/sofascorelocal/integration/J4DisplayScorePersistenceIT.java](../../src/test/java/com/bettingproject/sofascorelocal/integration/J4DisplayScorePersistenceIT.java)
- [src/test/java/com/bettingproject/sofascorelocal/integration/LiveCampaignPersistenceIT.java](../../src/test/java/com/bettingproject/sofascorelocal/integration/LiveCampaignPersistenceIT.java)

# WO-058 — Revue statique du candidat live-v5 / V40

Statuts conservés : **EXPERIMENTAL · LOCAL_ONLY · NOT_PRODUCTION_APPROVED · NO_CRITICAL_DEPENDENCY**.

## Référence et portée

Revue locale effectuée le **8 septembre 2026**, terminée à l'horodatage de l'inventaire ci-dessous, dans le worktree `.tmp/wo058-live-j4-j5`. Base et HEAD observés : `06c7e3d272e8f96873eca822e4bdaa8302cf1b01`. Le candidat est un ensemble de modifications non committées au-dessus de ce HEAD ; les empreintes ci-dessous identifient les octets lus. Cette revue ne décrit pas l'état distant d'une PR.

Le périmètre final comprend 63 fichiers modifiés ou nouveaux, hors présent rapport : 46 fichiers suivis modifiés et 17 fichiers nouveaux. Les 62 fichiers de l'inventaire initial ont été complétés par la modification d'AGENTS.md intervenue pendant la revue : deux libellés de liens ADR-SS-005 passent de v0.5 à v0.6, sans modification du texte des invariants. Les lectures portent sur le diff, les fichiers nouveaux, les configurations et les gardes concernées. Les références sont l'[AGENTS.md](../../AGENTS.md), le [WO-058 actif](../work_orders/active/WO-SS-20260907-058-bounded-live-j4-j5.md), les ADR-SS-001 et ADR-SS-005 ainsi que le skill local `ss-review-closeout`.

Le candidat V5 examiné vise **20 rencontres, une cadence critique de 100 secondes, les compositions à 300 secondes et une seconde entre groupes d'une même session V5**. Il ne reprend pas le candidat antérieur à 75 secondes comme preuve de qualification à 100 secondes. La revue indépendante du comportement de production réalisée avant cette passe n'avait identifié aucun défaut P1/P2 supplémentaire dans l'intégration transport, ordonnanceur, admission, service, persistance ou présentation.

## Résultats statiques

| Contrôle | Résultat observé et portée |
|---|---|
| Encodage | Décodage UTF-8 strict des 62 fichiers initiaux puis d'AGENTS.md réussi ; aucun BOM UTF-8 et aucun caractère de remplacement U+FFFD détecté. Les textes français des modifications examinées sont lisibles. |
| Diff | `git diff --check` termine avec le code 0. Git signale uniquement des normalisations de fins de ligne LF/CRLF selon ses attributs ; ce ne sont pas des erreurs UTF-8. Les empreintes du présent rapport portent sur les octets du worktree avant normalisation éventuelle. |
| Secrets potentiels | Recherche ciblée dans les 62 fichiers initiaux : clés privées PEM, formats de clés d'accès usuels, jetons GitHub, JWT, littéraux Bearer et affectations usuelles de mots de passe, clés ou secrets. Aucun candidat détecté. Le complément AGENTS.md a été relu : il ne change que les deux versions de lien. Aucun contenu de `.env`, cookie, jeton opérateur ou profil de navigateur n'a été lu pour cette revue. Cette recherche ciblée ne constitue pas une preuve exhaustive d'absence de secret dans tout le dépôt ou dans ses fichiers ignorés. |
| Exposition des journaux | Les lignes ajoutées au code de production et aux scripts ne créent pas de journalisation de payload, d'en-tête d'authentification ou de secret. Aucun nouvel URI ni écoute `0.0.0.0` dans ces ajouts. Les nouvelles preuves JSON sont des mesures de qualification synthétique et des empreintes. |
| Écoute locale | `src/main/resources/application.yml:47` conserve `server.address: 127.0.0.1`. `LocalOnlyBindingGuard` reste inchangé et contrôle la valeur effective au démarrage. Aucune écoute de l'application ou inspection de la configuration du lanceur opérateur n'a été effectuée par cette revue. |
| Réseau désactivé par défaut | `application.yml:81`, `:83` et `:121` conservent respectivement les valeurs par défaut `false` de `sofascore.enabled`, `sofascore.live.enabled` et `sofascore.playwright.enabled`. Les activations J3/J4/J5 restent à `false` par défaut, de même que l'actualisation automatique et le polling historiques. Les nouvelles propriétés V5 ne modifient que le profil d'admission séparé. |
| Profil V5 explicite | Le SHA de qualification V5 est vide par défaut, les enveloppes restent conservatrices à 10 s / 1 s. `groupedAdmissionProfileV5()` refuse un SHA absent ou mal formé ; le profil V4 ne qualifie pas implicitement V5. Ce contrôle de configuration n'est pas une vérification cryptographique du contenu du fichier de preuve à chaque lancement. Le lien preuve/profil est qualifié séparément par le test dédié. |
| Lancement opérateur | Le POST de lancement consomme le jeton local et exige la confirmation. Le service contrôle les trois activations, le manifeste, le profil, l'admission et l'exclusivité avant d'ouvrir `LiveProviderSession`. Le superviseur contrôle encore l'activation Playwright et l'allowlist. Aucun chemin de démarrage automatique du navigateur n'est ajouté. Les gardes d'origine et de liaison locale ne sont pas modifiées. |
| Séparation des tests natifs | `pom.xml` est inchangé. Les sources Chromium dédiées restent attachées au profil explicite `provider-playwright-local-qualification` ; aucun `activeByDefault` n'est ajouté. Le profil `sofascore-live-test` reste bloqué par Maven Enforcer. Les tests standards nouveaux utilisent les modèles, simulations, doubles de transport ou fichiers de preuve ; ils n'ouvrent pas de transport SofaScore. |
| Périmètre transport | Aucun endpoint nouveau ni nouvelle allowlist fournisseur. `openLiveGroupedV5()` accepte exactement J4, incidents, statistiques et compositions. Son implémentation par défaut refuse une fabrique non compatible. L'exception d'une seconde est liée à l'instance de session V5 validée ; les transitions de session et les parcours historiques conservent leur délai. |
| Historique PostgreSQL | Aucune migration suivie V1 à V39 n'est modifiée. V40 est un nouveau candidat non partagé. Elle ajoute les bornes V5 et les validations de version sans réécrire les anciennes campagnes ni leurs empreintes. Les scripts de sauvegarde/rétention ciblent désormais V40 ; les scénarios historiques explicites restent distincts. |

## Limites et points restant à qualifier

- **La capacité 20 à 100 secondes n'est pas déclarée qualifiée par ce rapport.** Au moment de cette revue, l'essai natif de 35 minutes à 100 secondes est en cours. La preuve finale, ses maxima par famille et la simulation d'admission doivent encore être reliés avant de fixer la capacité annoncée. Le candidat à 75 secondes est archivé séparément et n'autorise pas l'abaissement des enveloppes mesurées.
- Les enveloppes doivent couvrir les maxima retenus et leur périmètre de mesure. Une qualification loopback avec charges représentatives ne prouve ni la latence externe de SofaScore ni une fraîcheur réelle identique chez le fournisseur. Une campagne opérateur distincte reste nécessaire pour cette comparaison.
- Le plafond brut V5 est indépendant du plafond d'appels. Des réponses plus volumineuses peuvent épuiser ce budget ou la réserve disque avant les 20 000 appels ; le suivi doit alors s'arrêter conformément aux gardes existantes, sans ralentissement automatique pour prolonger l'autonomie.
- V40, ses limites par version, la concurrence des réservations et la sauvegarde/restauration demandent les résultats PostgreSQL du candidat final. La présence des tests et les résultats du candidat précédent ne constituent pas une nouvelle exécution à 100 secondes.
- Cette passe n'exécute **aucun Maven, aucun test, aucun navigateur, aucune commande Docker et aucune action sur la base opérateur**. Elle ne touche ni les classes compilées ni `target`. Elle ne conclut donc pas à un succès de `clean verify`, du profil `integration-tests` ou du run natif courant.
- Les preuves et la documentation peuvent encore évoluer après cet instantané. Les ajouts ultérieurs et toute différence d'empreinte doivent être rapprochés du candidat finalement vérifié ; cette revue ne vaut ni validation humaine finale, ni clôture, ni publication, ni fusion.

## Commandes et lectures effectuées

`git status --short`, `git rev-parse HEAD`, `git diff --stat`, `git diff --name-only`, `git ls-files --others --exclude-standard`, diff ciblé de production/configuration/scripts, recherches `rg`, lecture ciblée du POM et des gardes de lancement, décodage UTF-8 strict des fichiers inventoriés, recherche de signatures sensibles avec sortie limitée au chemin et au numéro de ligne, puis `git diff --check`.

Deux recherches exploratoires ont d'abord utilisé un glob Windows non résolu et un nom de classe de configuration inexistant. Elles ont été remplacées par le glob `rg -g 'application*.yml'` et les fichiers réellement présents ; leurs erreurs ne sont pas comptées comme des contrôles réussis.

## Inventaire figé et empreintes

Les listes suivantes sont générées depuis `git diff --name-only HEAD` et `git ls-files --others --exclude-standard`. Le présent rapport est exclu de son propre inventaire. Les hashes SHA-256 sont calculés sur les fichiers du worktree ; les fichiers de garde explicitement indiqués comme inchangés sont ajoutés au relevé pour ancrer les invariants examinés.

Instantané des chemins et empreintes : **2026-09-08T18:05:08Z**, relevé pendant le run natif, et non avant son démarrage.

### Décisions, architecture et runbooks (10)

- `ADR-SS-001-experimentation-endpoints-sofascore-depuis-windows.md`
- `ADR-SS-005-bounded-local-live-j4-j5-campaigns.md`
- `AGENTS.md`
- `CHANGELOG.md`
- `docs/architecture/ARCHITECTURE.md`
- `docs/architecture/LIVE-J4-J5-CAMPAIGNS.md`
- `docs/runbooks/J6-BACKUP-RESTORE-AND-RETENTION.md`
- `docs/runbooks/LIVE-J4-J5-CAMPAIGNS.md`
- `docs/work_orders/active/WO-SS-20260907-058-bounded-live-j4-j5.md`
- `README.md`

### Preuves et comptes rendus déjà présents dans le candidat (11)

- `docs/validation/WO058-CAPACITY-OUTLOOK-20260908.md`
- `docs/validation/WO058-DYNAMIC-UI-SCALE-20260908.json`
- `docs/validation/WO058-DYNAMIC-UI-SCALE-20260908.md`
- `docs/validation/WO058-DYNAMIC-UI-SCALE-CANDIDATE-75-20260908.json`
- `docs/validation/WO058-DYNAMIC-UI-SCALE-CANDIDATE-75-20260908.md`
- `docs/validation/WO058-GROUPED-LIVE-V5-20260908.md`
- `docs/validation/WO058-GROUPED-LIVE-V5-CANDIDATE-75-MEASURED-RUNTIME-20260908.json`
- `docs/validation/WO058-GROUPED-LIVE-V5-CANDIDATE-75-NATIVE-20260908.json`
- `docs/validation/WO058-GROUPED-LIVE-V5-CANDIDATE-75-PER-EVENT-20260908.json`
- `docs/validation/WO058-GROUPED-LIVE-V5-CANDIDATE-75-PROFILE-20260908.json`
- `docs/validation/WO058-LINEUPS-VISIBILITY-20260908.md`

### Production : orchestration, admission, domaine, transport, configuration et persistance (13)

- `src/main/java/com/bettingproject/sofascorelocal/adapter/persistence/live/JdbcLiveCampaignStore.java`
- `src/main/java/com/bettingproject/sofascorelocal/application/live/GroupedLiveAdmissionSimulation.java`
- `src/main/java/com/bettingproject/sofascorelocal/application/live/GroupedLiveScheduleV4.java`
- `src/main/java/com/bettingproject/sofascorelocal/application/live/LiveAdmissionPolicy.java`
- `src/main/java/com/bettingproject/sofascorelocal/application/live/LiveCampaignService.java`
- `src/main/java/com/bettingproject/sofascorelocal/application/live/LiveProviderSession.java`
- `src/main/java/com/bettingproject/sofascorelocal/application/live/LiveSchedule.java`
- `src/main/java/com/bettingproject/sofascorelocal/application/network/playwright/ChildJvmPlaywrightProviderSupervisor.java`
- `src/main/java/com/bettingproject/sofascorelocal/application/network/playwright/LiveProviderGroupTracker.java`
- `src/main/java/com/bettingproject/sofascorelocal/application/network/playwright/PlaywrightProviderCampaignFactory.java`
- `src/main/java/com/bettingproject/sofascorelocal/application/network/playwright/ProviderNetworkStartDelayGate.java`
- `src/main/java/com/bettingproject/sofascorelocal/config/LiveCampaignProperties.java`
- `src/main/java/com/bettingproject/sofascorelocal/domain/live/LiveCampaignData.java`

### Interface et configuration déclarative (6)

- `src/main/java/com/bettingproject/sofascorelocal/adapter/web/LiveCampaignController.java`
- `src/main/java/com/bettingproject/sofascorelocal/adapter/web/LiveCampaignPresentation.java`
- `src/main/resources/application.yml`
- `src/main/resources/static/js/lineups.js`
- `src/main/resources/templates/events.html`
- `src/main/resources/templates/live-campaign.html`

### Migration append-only (1)

- `src/main/resources/db/migration/V40__live_v5_cadence_and_independent_budgets.sql`

### Scripts opératoires et de qualification (3)

- `scripts/Backup-Restore-J6.ps1`
- `scripts/Invoke-J6Retention.ps1`
- `scripts/Invoke-LiveGroupedPlaywrightQualification.ps1`

### Tests standards et PostgreSQL (15)

- `src/test/java/com/bettingproject/sofascorelocal/adapter/web/LiveCampaignControllerTest.java`
- `src/test/java/com/bettingproject/sofascorelocal/adapter/web/LiveCampaignPresentationTest.java`
- `src/test/java/com/bettingproject/sofascorelocal/application/live/GroupedLiveAdmissionPolicyV5EvidenceTest.java`
- `src/test/java/com/bettingproject/sofascorelocal/application/live/GroupedLiveAdmissionPolicyV5Test.java`
- `src/test/java/com/bettingproject/sofascorelocal/application/live/GroupedLiveScheduleV5Test.java`
- `src/test/java/com/bettingproject/sofascorelocal/application/live/LiveCampaignServiceTest.java`
- `src/test/java/com/bettingproject/sofascorelocal/application/live/LiveProviderSessionTest.java`
- `src/test/java/com/bettingproject/sofascorelocal/application/network/playwright/ChildJvmPlaywrightProviderSupervisorTest.java`
- `src/test/java/com/bettingproject/sofascorelocal/application/network/playwright/LiveProviderGroupTrackerTest.java`
- `src/test/java/com/bettingproject/sofascorelocal/application/network/playwright/ProviderLiveV5DelayGateTest.java`
- `src/test/java/com/bettingproject/sofascorelocal/build/J6NativeBinaryPipelineQualificationTest.java`
- `src/test/java/com/bettingproject/sofascorelocal/config/LiveCampaignPropertiesTest.java`
- `src/test/java/com/bettingproject/sofascorelocal/domain/live/LiveGroupedDataTest.java`
- `src/test/java/com/bettingproject/sofascorelocal/integration/FlywayMigrationIT.java`
- `src/test/java/com/bettingproject/sofascorelocal/integration/LiveCampaignPersistenceIT.java`

### Qualifications Chromium séparées (4)

- `src/provider-playwright-qualification-test/java/com/bettingproject/sofascorelocal/adapter/web/LiveCampaignLineupsBrowserQualificationIT.java`
- `src/provider-playwright-qualification-test/java/com/bettingproject/sofascorelocal/adapter/web/LiveTenMatchRefreshBrowserQualificationIT.java`
- `src/provider-playwright-qualification-test/java/com/bettingproject/sofascorelocal/application/network/playwright/LiveGroupedCampaignLocalQualificationIT.java`
- `src/provider-playwright-qualification-test/java/com/bettingproject/sofascorelocal/application/network/playwright/LiveGroupedDegradationQualificationIT.java`

### Empreintes des sources et gardes examinées

| Fichier | SHA-256 des octets locaux |
|---|---|
| `pom.xml` (inchangé) | `e7bbc92346aa241db98296f395ca67faae75b959bab3fa673afb68b5af606847` |
| `scripts/Backup-Restore-J6.ps1` | `934dd671f56897523428a38c1bd69daf81df56f1037187268bc67ce48e15957d` |
| `scripts/Invoke-J6Retention.ps1` | `9853b6eaec47e99d948842d9e81c707abbbd7808f43594551a7374311ddd9286` |
| `scripts/Invoke-LiveGroupedPlaywrightQualification.ps1` | `718e61f0dd4827bfab82971865c1b2f223ab96f9379e1250ffc2eaaa61494fdd` |
| `src/main/java/com/bettingproject/sofascorelocal/adapter/persistence/live/JdbcLiveCampaignStore.java` | `afbb01a45518528de9c93f6eddddb23766d8274a9a74af5c29af85c1b99d8d70` |
| `src/main/java/com/bettingproject/sofascorelocal/adapter/web/LiveCampaignController.java` | `23cdb7d766a43d1ee5f7dd7400a6c4eb222498e83e68e24ae4b38e30fb3b841c` |
| `src/main/java/com/bettingproject/sofascorelocal/adapter/web/LiveCampaignPresentation.java` | `3a5534ea24ab8823515f1fa4c62c25e1bd32e687385d90280a108d94ad716c51` |
| `src/main/java/com/bettingproject/sofascorelocal/application/live/GroupedLiveAdmissionSimulation.java` | `debb209d8296e03b2e227d9603dd97ed072131781d7be8ce9fb576b9cbe5dcdd` |
| `src/main/java/com/bettingproject/sofascorelocal/application/live/GroupedLiveScheduleV4.java` | `b4fc857f936852c15560b45f29d0829dc5b8e19bfe89014610b71cb58cd7be73` |
| `src/main/java/com/bettingproject/sofascorelocal/application/live/LiveAdmissionPolicy.java` | `55752369a43c92092537b812c357248225c66bc5358e7d1d3c2565998b4b954e` |
| `src/main/java/com/bettingproject/sofascorelocal/application/live/LiveCampaignService.java` | `d501de0aa086da71b8d587cb99efba2b6c3e608e6fc3a96118d7d6eee94654c4` |
| `src/main/java/com/bettingproject/sofascorelocal/application/live/LiveProviderSession.java` | `5449558daeb04d1fac4b3c3992cf68c7101426fad4adf78150ca73f7c3f746cd` |
| `src/main/java/com/bettingproject/sofascorelocal/application/live/LiveSchedule.java` | `e90a442afa3262f35c9ac9dd910ff46ad6e6935e74fffa7a3d1a487e4e60fe09` |
| `src/main/java/com/bettingproject/sofascorelocal/application/network/playwright/ChildJvmPlaywrightProviderSupervisor.java` | `b94ddc46311cc79bab38fa580c87367fcd18cfb84672ffa64a2d93f704b5a4f6` |
| `src/main/java/com/bettingproject/sofascorelocal/application/network/playwright/LiveProviderGroupTracker.java` | `4151450440499e8fd7f50db476e53cdc0752a49065051d3fd996bb39eb422e65` |
| `src/main/java/com/bettingproject/sofascorelocal/application/network/playwright/PlaywrightProviderCampaignFactory.java` | `6280829fe5a7d79462a1957e995b9c69a75647345fdeda7a48fc04f7a3eafb05` |
| `src/main/java/com/bettingproject/sofascorelocal/application/network/playwright/ProviderNetworkStartDelayGate.java` | `d871d9493f30369e7815d956e15082ef02a6abc9855a6167d4ab8e329b30ff6c` |
| `src/main/java/com/bettingproject/sofascorelocal/config/LiveCampaignProperties.java` | `76604a88b51683cc9d10d28bb6e47f8cd1e88c82c4e9829b7aaf0b90e685b95a` |
| `src/main/java/com/bettingproject/sofascorelocal/config/LocalOnlyBindingGuard.java` (inchangé) | `258a7ec11e54d0ddb95e0d6c6199bd40d74e2e54b71fcf2f8963824b8e368197` |
| `src/main/java/com/bettingproject/sofascorelocal/config/SecurityHeadersFilter.java` (inchangé) | `da3b8acaa383c55f7a3ee568423c482afcc19c946e7e306796fff0bdbeb4e87f` |
| `src/main/java/com/bettingproject/sofascorelocal/domain/live/LiveCampaignData.java` | `1adf8296be710fe3e9f461f462552f1ea9f1f0f6d2e82be465cb6c1526a0dde9` |
| `src/main/resources/application.yml` | `278dd28db315555fd7290631c32bcff7dd95811121be193e3fceb1b6c00abdec` |
| `src/main/resources/db/migration/V40__live_v5_cadence_and_independent_budgets.sql` | `cd0e312b3edbf6b8075e502467129db0df23e605b69afacfc81d5c943c76001f` |
| `src/main/resources/static/js/lineups.js` | `383da48f3ab44a460de5f3cf1d7b21056ab67ec895754357743640dd480006aa` |
| `src/main/resources/templates/events.html` | `98d47a36385854770982f2dfd71408cc4ccb8289ff50b6d75f7d72562ad413fc` |
| `src/main/resources/templates/live-campaign.html` | `e21d94a8bf054f8512c1b533151f0087d7e0edfd6a5652e81f8607526b3f0851` |
| `src/provider-playwright-qualification-test/java/com/bettingproject/sofascorelocal/adapter/web/LiveCampaignLineupsBrowserQualificationIT.java` | `944e7e85430ee1f844bd2cae07072fac1c8b7c0eabaa4ed569d08c7bd4d12c10` |
| `src/provider-playwright-qualification-test/java/com/bettingproject/sofascorelocal/adapter/web/LiveTenMatchRefreshBrowserQualificationIT.java` | `5623a36527a55f45a636597542eada903001381b27e92eebab569cc53c65d352` |
| `src/provider-playwright-qualification-test/java/com/bettingproject/sofascorelocal/application/network/playwright/LiveGroupedCampaignLocalQualificationIT.java` | `446234f41b219f9d78cb800b278e7e4aad71e951fd7744df089d6025917b3498` |
| `src/provider-playwright-qualification-test/java/com/bettingproject/sofascorelocal/application/network/playwright/LiveGroupedDegradationQualificationIT.java` | `f802efc54c4a1355b1680e6edc9f2d8ddfcf2d96b5312916eeea2916698d64f3` |
| `src/test/java/com/bettingproject/sofascorelocal/application/live/GroupedLiveAdmissionPolicyV5EvidenceTest.java` | `f81fa0045bb7852c090fc34d473af144146d2f78728dfd8a5afb9d7d45f461fd` |
| `src/test/java/com/bettingproject/sofascorelocal/integration/FlywayMigrationIT.java` | `a82ba157900cf3dada566d2724992dd4768b90fec172ecfc3ea5538db29945e9` |
| `src/test/java/com/bettingproject/sofascorelocal/integration/LiveCampaignPersistenceIT.java` | `3ed299c172314697ea518f91065bf47f8beefdf181bb53c37fc694afb7db7137` |

### Corpus lu par le harnais natif

Le harnais charge fixtures/event-{details,statistics,incidents,lineups}/nominal.json depuis les sources locales (LiveGroupedCampaignLocalQualificationIT.java:441). Les comparaisons suivantes sont faites pendant le run : identité du blob Git du HEAD et du blob calculé avec git hash-object --no-filters, plus SHA-256 local. Ces valeurs ont été capturées après le démarrage du run.

| Fixture | Identique octet pour octet au HEAD observé | SHA-256 local |
|---|---|---|
| `fixtures/event-details/nominal.json` | Oui | `824a54db1aecddab300bcf0b5fb35c4224c5fbc9c70884f962f651e0e62f3341` |
| `fixtures/event-statistics/nominal.json` | Oui | `4193086c42276d7f76d12bac3197a13b2b212f92a758bb30a8e3468e9b90aee6` |
| `fixtures/event-incidents/nominal.json` | Oui | `4e4a3c7228e8b0faa6bf4f98ee2217e4407eed84c9c73d41eb4f0174daa8a27a` |
| `fixtures/event-lineups/nominal.json` | Oui | `08646326609e9631b6c4a2bad2464c00c23fde94bdd434513f4deb0ab254fd6b` |

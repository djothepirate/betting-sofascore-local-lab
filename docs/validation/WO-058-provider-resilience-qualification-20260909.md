# WO-058 — Diagnostic durable et résilience fournisseur, 9 septembre 2026

Statuts : `EXPERIMENTAL`, `LOCAL_ONLY`, `NOT_PRODUCTION_APPROVED`, `NO_CRITICAL_DEPENDENCY`.

**Résultat au point `3130e39` : premier lot implémenté et qualifié fonctionnellement hors fournisseur.**
Les deux passes Maven complètes et les 20 scénarios Chromium loopback réussissent.
À ce point, l'activation des nouvelles campagnes v6 attendait encore la qualification
de leur profil temporel concret. Le [complément ultérieur du même jour](WO058-LIVE-V6-CAPACITY-20260909.md)
apporte cette preuve : sept rencontres, 35 minutes Chromium/PostgreSQL isolés, dont
30 établies, 494 échanges et aucun cycle manqué. Sa vérification finale et les contrôles de son interface réussissent.
Les résultats de ce premier rapport restent rattachés à leur exécution ; aucun de ces
rapports ne qualifie une charge acceptée par SofaScore.

## Périmètre et autorité

Le propriétaire a explicitement demandé le premier lot diagnostic durable/suspension
persistante après refus, puis lissage des départs et espacement des 404, avec qualification
hors fournisseur avant une nouvelle campagne réelle. La décision est formalisée dans
[ADR-SS-005 v0.8](../../ADR-SS-005-bounded-local-live-j4-j5-campaigns.md) et le
[WO-058](../work_orders/completed/WO-SS-20260907-058-bounded-live-j4-j5.md).

Réalisation dans le worktree `.tmp/wo058-live-j4-j5`, branche
`feature/V0.1.0-RC01-CODEX-WO-SS-20260907-058`, après le point de reprise local `6f84fba`.
Les changements antérieurs de timeout sont conservés dans ce point de reprise ; ils ne
constituent pas la preuve de ce lot. Le checkout Eclipse, son lanceur, la base opérateur,
les campagnes enregistrées et l'accès réseau fournisseur ne sont pas modifiés ici.

## Comportement réalisé

- Les diagnostics conservent en base la première cause, le dernier échec de nettoyage et
  la progression du transport : navigation, envoi, en-têtes, lecture du corps, réponse
  complète, attente IPC. Le timeout configuré, les instants et le statut HTTP connu sont
  conservés sans payload, URL, cookie, jeton ou en-têtes bruts. Le protocole IPC v6 accepte
  au plus quatre messages de progression authentifiés, sans repousser sa date limite.
- Un 403/429 connu suspend tous les nouveaux accès Playwright J3/J4/J5, manuels et live,
  dès les en-têtes, même si le corps échoue ensuite. La suspension et les charges persistent
  entre campagnes et après redémarrage. Un timeout sans statut reste incertain. Un
  `Retry-After` valide constitue une borne minimale, jamais un réarmement automatique.
- La page locale `/provider-access` permet un réarmement explicite, versionné et protégé
  par les contrôles Host/Origin et un jeton de formulaire. Il exige le nettoyage terminé
  et ne lance ni navigateur, ni requête, ni reprise de campagne. Une réservation dont
  l'issue reste inconnue demeure bloquante jusqu'à réconciliation explicite.
- La protection globale impose deux secondes après la fin prouvée de l'échange précédent,
  25 charges sur 60 secondes glissantes et 1 000 sur une heure. Une charge ne vieillit
  qu'à sa fin ; une réservation non résolue ne devient pas disponible par simple attente.
  Les transactions SQL sont terminées avant tout échange. Les pauses historiques plus
  longues continuent de s'appliquer. Les reports ne produisent pas de rafale compensatrice.
- La nouvelle politique `live-v6` espace les 404 J5 par rencontre et famille : 300/600/900 s
  pour incidents, statistiques et compositions prématch ; 600/900 s pour compositions en
  jeu. Une réponse PARSED ou une transition J4 réévalue cette attente. Un 404 J4 reste
  un arrêt individuel pour revue. Aucun retry final ni prolongation de campagne n'est ajouté.
- Les nouvelles préparations emploient un profil `grouped-v6` distinct, au plus sept
  rencontres et éventuellement moins selon les coûts qualifiés. Les cibles nominales
  restent 100 s et 300 s, subordonnées au budget partagé. Les preuves v4/v5 et les
  manifestes historiques restent distincts. Le timeout maximum v6 est 30 s ; les défauts
  général/local restent respectivement 10/20 s.

Les migrations append-only V42–V44 introduisent six tables pour la suspension, les
réservations/fins d'échange, leurs transitions et les diagnostics. Les contrôles SQL
imposent la provenance, la progression monotone, les champs bornés et l'immuabilité de
la cause primaire. Les scripts J6 incluent ces six tables dans les comptes et l'empreinte
ordonnée ; une restauration conserve la suspension. ADR-SS-001 et les anciennes migrations
ne sont pas réécrits.

## Qualification et preuves

Environnement : Windows, Java `25.0.4`, Maven wrapper en mode `--offline`, dépôt de
dépendances local `C:/Users/geoff/.m2/repository`, PostgreSQL Testcontainers et Chromium
du cache local `.tmp/provider-playwright-browsers`. Les journaux de travail sont dans
`.tmp/resilience-verification/` et ne sont pas ajoutés à Git.

La première invocation Maven confinée a échoué sur l'accès aux JAR du cache Maven.
Les commandes suivantes ont utilisé l'accès local élargi, sans téléchargement fournisseur.
Les premières passes ciblées ont révélé des fixtures à corriger ; la première passe complète
a signalé neuf assertions devenues obsolètes. Une seconde passe a révélé trois usages de
l'empreinte J6 V44 sur un schéma de test historique : les tests exécutent toujours les campagnes
en V39/V40, puis montent vers V44 en vérifiant l'égalité de leurs vues et de leur garde avant
la sauvegarde/rétention. Le premier lanceur natif omettait `PLAYWRIGHT_BROWSERS_PATH` ; sa
correction n'a nécessité aucun changement du runtime. La première passe native complète
a ensuite révélé les trois assertions incorrectes de clôture gracieuse sur timeout IPC,
désormais distinguées de l'arrêt local explicite. Ces échecs ne sont pas comptés comme succès.
La revue a aussi fait corriger deux cas de réalisation : conservation de la réservation
tant qu'un refus connu n'est pas durable, et absence de famine d'une collecte finale
derrière les échéances ordinaires de plusieurs matchs.

Toutes les commandes Maven ci-dessous utilisent le wrapper Windows, avec
`--offline -Dmaven.repo.local=C:/Users/geoff/.m2/repository` et Java 25.0.4. Les options
de ciblage n'appartiennent qu'aux passes ciblées ; les deux vérifications générales
n'excluent aucun test.

| Commande/passe | Résultat effectif |
|---|---|
| Tests ciblés du service, nettoyage, récupération et contrôleur de protection | 120 cas, zéro échec/erreur/ignoré, `service.log` |
| Dernière passe ciblée des contrôleurs, admission, ordonnanceur, wrapper et superviseur | 303 cas, zéro échec/erreur/ignoré, 12:47:38 UTC, `focused-final.log` |
| `mvnw.cmd clean verify` | **BUILD SUCCESS**, 13:10:25 UTC, 7 min 57 s ; **2 016 Surefire, 207 Failsafe**, zéro échec/erreur, cinq ignorés Surefire ; `clean-verify-qualified.log` |
| `mvnw.cmd -Pintegration-tests verify` | **BUILD SUCCESS**, 13:18:32 UTC, 7 min 58 s ; **2 016 Surefire, 207 Failsafe**, zéro échec/erreur, cinq ignorés Surefire ; `integration-verify-qualified.log` |
| Worker : compilation, protocole, observation réseau, contrat de sécurité, Retry-After et contrôleur de suspension | **40 cas réussis**, aucun ignoré, 13:19:11 UTC ; `worker-package-qualified.log` |
| Chromium loopback, classe `ProviderPlaywrightLocalQualificationIT` | **BUILD SUCCESS**, **20 cas**, zéro échec/erreur/ignoré, 13:21:44 UTC, 2 min 05 s ; `native-loopback-qualified.log` |
| `node --check src/main/resources/static/js/live-campaign.js` | Code de sortie 0 |
| Analyse syntaxique PowerShell des deux scripts J6 modifiés | Zéro erreur |
| Diff/encodage/invariants | `git diff --check` sans erreur ; 79 fichiers texte UTF-8 valides ; revue indépendante sans secret/artefact fournisseur introduit ; opt-ins faux et `127.0.0.1` conservés |

Les cinq cas ignorés Surefire sont quatre tests de liens symboliques non disponibles dans
l'environnement Windows et le scénario J6 Docker optionnel qui exige
`j6.docker.qualification`. Ce dernier n'est pas activé ici ; les tests J6 hors ligne
standards et les restaurations PostgreSQL isolées sont exécutés. Aucun test des nouveaux
diagnostics ou de la persistance n'est ignoré dans la passe générale réussie.

La compilation/qualification native est explicitement séparée des tests standards :

```powershell
$env:JAVA_HOME = 'C:/Program Files/Java/jdk-25.0.4'
$env:PLAYWRIGHT_BROWSERS_PATH = Join-Path (Get-Location) '.tmp/provider-playwright-browsers'
.\mvnw.cmd --offline '-Dmaven.repo.local=C:/Users/geoff/.m2/repository' `
  -Pprovider-playwright-runtime,provider-playwright-local-qualification `
  '-Dtest=ProviderRetryAfterTest,ProviderPlaywrightWorkerProtocolTest,ProviderPlaywrightWorkerSecurityContractTest,ProviderPlaywrightWorkerNetworkObservationTest,ProviderAccessControllerTest' package
.\mvnw.cmd --offline '-Dmaven.repo.local=C:/Users/geoff/.m2/repository' `
  -Pprovider-playwright-runtime,provider-playwright-local-qualification `
  -DskipTests=false -DskipITs=false '-Dit.test=ProviderPlaywrightLocalQualificationIT' `
  "-Dprovider.playwright.browser-cache=$env:PLAYWRIGHT_BROWSERS_PATH" `
  failsafe:integration-test@provider-playwright-loopback-qualification `
  failsafe:verify@provider-playwright-loopback-qualification
```

Après les passes générales, le libellé de l'état OPEN devient « Aucune suspension active »
pour rester exact après un réarmement conservant l'historique. Les six tests du contrôleur
sont réexécutés dans la passe de 40 cas, avec les ressources finales. Le code métier et
les migrations n'ont pas changé depuis les deux vérifications générales réussies.

Les essais Chromium utilisent le worker et le superviseur de production, un contexte
neuf par campagne explicitement ouverte et des routes exactes sur `127.0.0.1`. Les trois
corps incomplets 200/403/429 transmettent leurs en-têtes avant l'échec terminal, conservent
le timeout de 2 s et le `Retry-After` normalisé. L'arrêt local explicite est accusé en
au plus 500 ms, la disparition de l'arbre exact en au plus 2 s et sa clôture vérifiée
en au plus 5 s, sans libération artificielle du corps ni seconde requête. Le cas de
clôture gracieuse non authentifiée conserve au contraire son exclusion et refuse toute
nouvelle ouverture ; c'est une limite caractérisée, pas une clôture réussie.
Les scénarios historiques 20 s restent verts : réponse 200 après **12 141 ms** et
timeout sans réponse après **20 094 ms**. Aucun artefact de navigation interdit ni
payload dans les sorties du worker n'est accepté par ces assertions.

Les preuves ciblées comprennent :

| Propriété | Vérification |
|---|---|
| Refus partiel, timeout sans statut, distinction 200 incomplet | Superviseur IPC, worker et transport de production sur loopback |
| Suspension au premier refus, panne SQL, clôture vérifiée | Wrapper de production et diagnostics du service ; issue non résolue conservée si l'écriture échoue |
| Concurrence et persistance des fenêtres | PostgreSQL réel ; nouvelles instances du store et réservations concurrentes |
| Départs réellement émis | Wrapper de production + store JDBC transactionnel + horloge logique + transport synthétique |
| Redémarrage, nettoyage et réarmement sans départ | Service, contrôleur local, restauration des états durables |
| Upgrade prérempli V41→V44 | Comparaison des observations/manifeste historiques et invariants SQL |
| Sauvegarde/restauration | `pg_dump`/`pg_restore` en conteneur jetable, six tables préremplies, même empreinte J6, suspension et diagnostics inchangés |
| Sept/huit rencontres, 404, surcharge et finalisation | Admission/rejeu v6 ; huit refusées, délais par famille, final éligible non affamé |
| Projection SSR/JSON | Diagnostic durable après fin de session et refus 429 avec corps incomplet |

Dans le rejeu d'une famille STATISTICS renvoyant toujours 404, les demandes ont lieu à
`0, 300, 900, 1 800, 2 700 s` : cinq lectures sur cette fenêtre, contre 28 au nominal
constant de 100 s. Les assertions conservent J4 et les incidents à chaque tour ; cette
réduction est celle d'un scénario synthétique précis, pas une mesure fournisseur.

L'essai associant le wrapper réel et PostgreSQL précharge 950 départs historiques.
Deux nouvelles instances émettent 25 départs chacune : premier lot de `0 à 48 s`, second
de `60 à 108 s`. La troisième instance attend `200 s` pour l'expiration horaire. Le 403
partiel qui suit est déjà durable dans le callback des en-têtes. Un nettoyage échoué
conserve la réservation ; un nettoyage local réussi conserve encore la suspension,
qui bloque J3, J4 et J5 le lendemain sans créer de worker supplémentaire.

### Complément du 10 septembre — réarmement local après refus

Le parcours de réarmement a révélé un refus **local** distinct du refus HTTP fournisseur
persisté : la page `/provider-access` était servie sous `Referrer-Policy: no-referrer`.
Chromium/Brave peut alors soumettre son formulaire de navigation avec `Origin: null`,
correctement rejeté en 403 par la frontière locale avant le contrôleur. Le correctif sert
cette page et sa variante `;jsessionid` sous `Referrer-Policy: same-origin`. Il ne rend pas
`Origin: null` acceptable et ne modifie pas les contrôles d'origine étrangère, d'hôte, de
proxy, de jeton à usage unique, de version ou de confirmation.

La qualification dédiée `ProviderAccessRearmBrowserLocalQualificationIT` a réussi le
10 septembre sur deux origines isolées (`localhost:8087` et `127.0.0.1:8087`). Son Chromium
est hors ligne et toutes ses routes sont satisfaites par MockMvc : il vérifie le header,
l'origine exacte du `POST`, le `302` versionné de réarmement et le rendu final `200`. Il
établit l'appel unique à `store.rearm(...)`, l'absence de départ, de réservation, de
superviseur ou d'URL externe. Il ne lance ni navigateur fournisseur, ni campagne, ni
réarmement de l'état opérateur réel.

```powershell
$cache = (Resolve-Path -LiteralPath '.tmp\provider-playwright-browsers').Path
$env:PLAYWRIGHT_BROWSERS_PATH = $cache
.\mvnw.cmd --offline '-Dmaven.repo.local=C:\Users\geoff\.m2\repository' `
  '-Pprovider-playwright-runtime,provider-playwright-local-qualification' `
  '-DskipTests=false' '-DskipITs=false' `
  '-Dit.test=ProviderAccessRearmBrowserLocalQualificationIT' `
  "-Dprovider.playwright.browser-cache=$cache" `
  test-compile `
  'failsafe:integration-test@provider-playwright-loopback-qualification' `
  'failsafe:verify@provider-playwright-loopback-qualification'
```

`test-compile` est requis lorsque le répertoire `target` vient d'être nettoyé : il rattache
la source de qualification au profil avant les deux goals Failsafe. Résultat : `BUILD SUCCESS`,
deux cas, zéro échec, zéro erreur et zéro ignoré.

## Limites de preuve et suite opérateur

Les plafonds historiques de 25/minute et 1 000/heure, ainsi que la borne de sept,
documentent la qualification v6 initiale ; ils ne sont pas des quotas SofaScore connus.
Le profil live-v8 qualifié actuellement affiché aux opérateurs est distinct : 45 départs par
minute, 2 756 par heure et 500 ms après une fin d'échange prouvée. La borne de sept réserve
10 % de marge dans l'ancien budget horaire :
`7 × (120 appels ordinaires + 4 initiaux + 4 finaux) = 896 ≤ 900`.
Le rejeu d'admission peut réduire cette capacité, y compris à zéro pour une enveloppe
trop lente. Il ne garantit pas la cadence réelle ni l'acceptation par le fournisseur.

Les enveloppes courtes et les SHA synthétiques des tests servent à qualifier les
invariants. Ils ne sont pas placés dans la configuration d'une campagne opérateur.
Le nouveau profil v6 exige sa propre empreinte et ses propres enveloppes qualifiées ;
une preuve v4/v5 ne l'active pas. La qualification fonctionnelle locale du lot reste
distincte d'une qualification temporelle prolongée d'un profil concret.
Au point `3130e39`, le script temporel et son harness couvraient v4/v5 uniquement, en
ouvrant directement le superviseur : aucune activation v6 n'était établie par ces outils.
Le [complément de capacité](WO058-LIVE-V6-CAPACITY-20260909.md) étend ensuite ce parcours
au wrapper partagé, à PostgreSQL V44 et à l'ordonnanceur v6. Son profil distinct
qualifie sept rencontres avec les enveloppes mesurées de 64 Kio en régime établi ;
les premiers corps de 5 Mio restent séparés. Le plafond opérateur six et son timeout
30 s sont conservés comme paramètres indépendants, sans application automatique.

La clôture gracieuse après expiration IPC peut rester en `CLEANUP_REQUIRED` si aucune
trame terminale authentifiée n'est reçue, même après disparition de l'arbre de processus.
Cette garde existante n'est pas relâchée. Le nettoyage terminal mémorisé peut exiger un
redémarrage puis la commande locale **« Clôturer la session interrompue »**, avec preuve
d'absence de l'ancien propriétaire et des processus. La suspension et le départ incertain
restent à traiter séparément sur `/provider-access`. Le diagnostic nouveau rend cette
situation explicite ; il ne transforme pas une clôture incertaine en succès.

Aucun appel SofaScore, changement d'IP/VPN, réarmement opérateur, reprise automatique,
migration de la base opérateur ou campagne réelle n'est effectué pour cette qualification.
L'accès ne peut pas être garanti quand le fournisseur le refuse. La revue humaine,
la livraison Eclipse et toute campagne réelle restent des étapes distinctes.

## Inventaire des fichiers du lot

- `ADR-SS-005-bounded-local-live-j4-j5-campaigns.md`
- `AGENTS.md`
- `CHANGELOG.md`
- `docs/architecture/ARCHITECTURE.md`
- `docs/architecture/LIVE-J4-J5-CAMPAIGNS.md`
- `docs/architecture/LIVE-PROVIDER-RESILIENCE-PROPOSAL-20260909.md`
- `docs/runbooks/J6-BACKUP-RESTORE-AND-RETENTION.md`
- `docs/runbooks/LIVE-J4-J5-CAMPAIGNS.md`
- `docs/validation/WO-058-provider-resilience-qualification-20260909.md`
- `docs/work_orders/completed/WO-SS-20260907-058-bounded-live-j4-j5.md`
- `pom.xml`
- `README.md`
- `scripts/Backup-Restore-J6.ps1`
- `scripts/Invoke-J6Retention.ps1`
- `src/main/java/com/bettingproject/sofascorelocal/adapter/persistence/live/JdbcLiveCampaignStore.java`
- `src/main/java/com/bettingproject/sofascorelocal/adapter/persistence/live/JdbcLiveDiagnosticStore.java`
- `src/main/java/com/bettingproject/sofascorelocal/adapter/persistence/live/JdbcProviderResilienceStore.java`
- `src/main/java/com/bettingproject/sofascorelocal/adapter/sofascore/transport/ProviderEventDetailsPlaywrightTransport.java`
- `src/main/java/com/bettingproject/sofascorelocal/adapter/sofascore/transport/ProviderJ5EventDataPlaywrightTransport.java`
- `src/main/java/com/bettingproject/sofascorelocal/adapter/sofascore/transport/ProviderScheduledEventsPlaywrightTransport.java`
- `src/main/java/com/bettingproject/sofascorelocal/adapter/web/LiveCampaignController.java`
- `src/main/java/com/bettingproject/sofascorelocal/adapter/web/LiveCampaignPresentation.java`
- `src/main/java/com/bettingproject/sofascorelocal/adapter/web/ProviderAccessController.java`
- `src/main/java/com/bettingproject/sofascorelocal/application/live/GroupedLiveAdmissionSimulation.java`
- `src/main/java/com/bettingproject/sofascorelocal/application/live/GroupedLiveScheduleV4.java`
- `src/main/java/com/bettingproject/sofascorelocal/application/live/LiveAdmissionPolicy.java`
- `src/main/java/com/bettingproject/sofascorelocal/application/live/LiveCampaignDiagnostic.java`
- `src/main/java/com/bettingproject/sofascorelocal/application/live/LiveCampaignService.java`
- `src/main/java/com/bettingproject/sofascorelocal/application/live/LiveProviderSession.java`
- `src/main/java/com/bettingproject/sofascorelocal/application/live/LiveSchedule.java`
- `src/main/java/com/bettingproject/sofascorelocal/application/network/playwright/ChildJvmPlaywrightProviderSupervisor.java`
- `src/main/java/com/bettingproject/sofascorelocal/application/network/playwright/PlaywrightDispatchAdmission.java`
- `src/main/java/com/bettingproject/sofascorelocal/application/network/playwright/PlaywrightProviderException.java`
- `src/main/java/com/bettingproject/sofascorelocal/application/network/playwright/PlaywrightProviderFailure.java`
- `src/main/java/com/bettingproject/sofascorelocal/application/network/playwright/PlaywrightProviderResponse.java`
- `src/main/java/com/bettingproject/sofascorelocal/application/network/playwright/PlaywrightTransportDiagnostic.java`
- `src/main/java/com/bettingproject/sofascorelocal/application/network/playwright/ResilientPlaywrightProviderCampaignFactory.java`
- `src/main/java/com/bettingproject/sofascorelocal/config/LiveCampaignLocalRequestBoundaryInterceptor.java`
- `src/main/java/com/bettingproject/sofascorelocal/config/LiveCampaignProperties.java`
- `src/main/java/com/bettingproject/sofascorelocal/config/SecurityHeadersFilter.java`
- `src/main/java/com/bettingproject/sofascorelocal/domain/live/LiveCampaignData.java`
- `src/main/java/com/bettingproject/sofascorelocal/domain/provider/ProviderResilienceData.java`
- `src/main/java/com/bettingproject/sofascorelocal/port/LiveDiagnosticStore.java`
- `src/main/java/com/bettingproject/sofascorelocal/port/ProviderResilienceStore.java`
- `src/main/resources/application.yml`
- `src/main/resources/db/migration/V42__durable_provider_resilience.sql`
- `src/main/resources/db/migration/V43__durable_live_transport_diagnostics.sql`
- `src/main/resources/db/migration/V44__live_v6_resilient_policy.sql`
- `src/main/resources/static/js/live-campaign.js`
- `src/main/resources/templates/live-campaign-error.html`
- `src/main/resources/templates/live-campaign.html`
- `src/main/resources/templates/provider-access.html`
- `src/provider-playwright-qualification-test/java/com/bettingproject/sofascorelocal/application/network/playwright/ProviderPlaywrightLocalQualificationIT.java`
- `src/provider-playwright-qualification-test/java/com/bettingproject/sofascorelocal/adapter/web/ProviderAccessRearmBrowserLocalQualificationIT.java`
- `src/provider-playwright-test/java/com/bettingproject/sofascorelocal/provider/playwright/worker/ProviderPlaywrightWorkerProtocolTest.java`
- `src/provider-playwright-test/java/com/bettingproject/sofascorelocal/provider/playwright/worker/ProviderRetryAfterTest.java`
- `src/provider-playwright/java/com/bettingproject/sofascorelocal/provider/playwright/worker/ProviderMainDocumentNetworkObservation.java`
- `src/provider-playwright/java/com/bettingproject/sofascorelocal/provider/playwright/worker/ProviderPlaywrightWorkerMain.java`
- `src/provider-playwright/java/com/bettingproject/sofascorelocal/provider/playwright/worker/ProviderPlaywrightWorkerProtocol.java`
- `src/provider-playwright/java/com/bettingproject/sofascorelocal/provider/playwright/worker/ProviderRetryAfter.java`
- `src/test/java/com/bettingproject/sofascorelocal/adapter/sofascore/transport/ProviderEventDetailsPlaywrightTransportTest.java`
- `src/test/java/com/bettingproject/sofascorelocal/adapter/sofascore/transport/ProviderJ5EventDataPlaywrightTransportTest.java`
- `src/test/java/com/bettingproject/sofascorelocal/adapter/sofascore/transport/ProviderScheduledEventsPlaywrightTransportTest.java`
- `src/test/java/com/bettingproject/sofascorelocal/adapter/web/LiveCampaignControllerTest.java`
- `src/test/java/com/bettingproject/sofascorelocal/adapter/web/LiveCampaignPresentationTest.java`
- `src/test/java/com/bettingproject/sofascorelocal/adapter/web/ProviderAccessControllerTest.java`
- `src/test/java/com/bettingproject/sofascorelocal/config/SecurityHeadersFilterTest.java`
- `src/test/java/com/bettingproject/sofascorelocal/application/live/GroupedLiveAdmissionPolicyV6Test.java`
- `src/test/java/com/bettingproject/sofascorelocal/application/live/GroupedLiveScheduleV6Test.java`
- `src/test/java/com/bettingproject/sofascorelocal/application/live/LiveCampaignServiceTest.java`
- `src/test/java/com/bettingproject/sofascorelocal/application/live/LivePreparationAdmissionTest.java`
- `src/test/java/com/bettingproject/sofascorelocal/application/network/playwright/ChildJvmPlaywrightProviderSupervisorTest.java`
- `src/test/java/com/bettingproject/sofascorelocal/application/network/playwright/PlaywrightTransportDiagnosticTest.java`
- `src/test/java/com/bettingproject/sofascorelocal/application/network/playwright/ProviderResilienceTransportPersistenceIT.java`
- `src/test/java/com/bettingproject/sofascorelocal/application/network/playwright/ResilientPlaywrightProviderCampaignFactoryTest.java`
- `src/test/java/com/bettingproject/sofascorelocal/build/J6NativeBinaryPipelineQualificationTest.java`
- `src/test/java/com/bettingproject/sofascorelocal/config/LiveCampaignPropertiesTest.java`
- `src/test/java/com/bettingproject/sofascorelocal/domain/live/LiveGroupedDataTest.java`
- `src/test/java/com/bettingproject/sofascorelocal/integration/FlywayMigrationIT.java`
- `src/test/java/com/bettingproject/sofascorelocal/integration/LiveCampaignPersistenceIT.java`
- `src/test/java/com/bettingproject/sofascorelocal/integration/LiveDiagnosticPersistenceIT.java`
- `src/test/java/com/bettingproject/sofascorelocal/integration/ProviderResiliencePersistenceIT.java`

## Empreintes des journaux de qualification

Journaux locaux ignorés par Git ; empreintes du contenu final UTF-8/console conservé sur disque.

| Journal | SHA-256 |
|---|---|
| clean-verify-qualified.log | `d80348de006930e64baa097431a570584434004eafec80cfcadab21b8007f228` |
| integration-verify-qualified.log | `3264d20baa92a60bf51f3c7daaf87f72cd0dbcdc66149281315ea0032821fe1a` |
| worker-package-qualified.log | `fe9fd25d459914c192f3cb802d640fcc064e6fc621a5ac7ff868a171aabf8d88` |
| native-loopback-qualified.log | `9d7d5a88dbfaa8b839bf619612a97b53db8f07e90a69cde81cd4f99220080751` |

# WO-058 — Capacité de sélection et qualification temporelle v6

Statuts : `EXPERIMENTAL`, `LOCAL_ONLY`, `NOT_PRODUCTION_APPROVED`, `NO_CRITICAL_DEPENDENCY`.

## Signalement et cause

Le 9 septembre, après adoption du commit `3130e39`, la page des événements annonce
zéro capacité et empêche de sélectionner les rencontres admissibles. La lecture du
HTML local confirme `data-live-selection-maximum="0"`. La préparation utilise désormais
`live-v6`, dont la preuve est volontairement distincte de celles des versions antérieures.

La lecture ciblée de la configuration Eclipse « SofaScore - PLAYWRIGHT J3-J5 (MANUEL -
OPT-IN) (LIVE) » confirme la présence des anciens profils, mais aucune des neuf variables
`SOFASCORE_LIVE_GROUPED_V6_*`. Le plafond opérateur vaut six et le timeout Playwright
trente secondes. Ces choix ne doivent pas être augmentés par la qualification.
Le contrôle du profil explique la capacité nulle ; ce constat ne constitue ni un refus
fournisseur, ni une preuve que toutes les rencontres sont terminées.

Deux défauts de restitution rendaient la cause ambiguë : le texte du bandeau était resté
fixé à `live-v5`, et le JavaScript laissait sélectionner les rencontres terminées alors
qu'il bloquait celles comptant dans la capacité. Le compteur « 0 éligible / 0 » ne
distinguait pas les éléments sélectionnés de la disponibilité de la préparation.

## Correction de la sélection

Le bandeau explique maintenant qu'aucun profil qualifié n'accorde de capacité à la
configuration courante et indique la qualification hors fournisseur puis le chargement
du profil. À capacité nulle, toutes les cases et le bouton sont désactivés dès le HTML
initial et restent désactivés après les mises à jour JavaScript. Le compteur indique
explicitement l'indisponibilité de la sélection. À capacité positive, il précise qu'il
compte les rencontres sélectionnées admissibles ; le parcours d'exclusion des rencontres
terminées et les contrôles de capacité côté serveur sont conservés.

## Qualification temporelle

La qualification est réalisée séparément de l'application opérateur, sur serveur loopback
synthétique, Chromium dédié et PostgreSQL Testcontainers. Elle traverse le wrapper de
résilience, les réservations et fins persistantes, ainsi que l'ordonnanceur v6. Les attentes
imposées et les coûts de l'échange et du traitement sont mesurés séparément.

La passe soutenue s'est achevée à 14:41:05 UTC après 35 minutes, dont cinq de mise en
régime et trente établies : sept rencontres, 494 échanges (420 établis), zéro cycle
manqué, 494 réservations, fins et diagnostics complets persistants. Le pic observé vaut
18 départs et 18 arrivées HTTP par fenêtre de 60 secondes ; la plus petite distance
observée entre une fin prouvée et le départ suivant vaut 2,299724 s. Le timeout effectif
du worker vaut 30 000 ms. Les deux scénarios natifs, smoke et soutenu, passent sans ignoré.

Le corpus établi produit 64 Kio par réponse. Les 28 premières réponses, une par
rencontre/famille, produisent chacune 5 Mio et sont mesurées séparément avant la fin de
la mise en régime. Elles ne sont pas couvertes par les enveloppes établies ci-dessous.
Les attentes imposées par la protection ne sont pas incorporées une seconde fois dans
les coûts d'échange ; les mesures conservent séparément SQL, autorisation et attente.

Le [rapport natif](WO058-GROUPED-LIVE-V6-NATIVE-20260909.json) contient les 494 échantillons
et leurs diagnostics agrégés. Le [profil calculé](WO058-GROUPED-LIVE-V6-PROFILE-20260909.json)
arrondit chaque maximum établi séparément à la borne supérieure de 50 ms, sans reprendre
les enveloppes ou empreintes v5. Le calcul de production puis ses 24 scénarios de rejeu
retiennent **sept rencontres**. Le plafond opérateur reste indépendant : avec six dans
le lanceur, la sélection effective est de **six**.

| Famille | Enveloppe échange | Enveloppe traitement |
|---|---:|---:|
| J4 | 250 ms | 350 ms |
| Incidents | 300 ms | 400 ms |
| Statistiques | 300 ms | 450 ms |
| Compositions | 250 ms | 450 ms |

Empreintes SHA-256 des fichiers exacts, protégés de la conversion de fins de ligne par
`.gitattributes` :

- Profil : `5986e95306ef9b68cb0a96abdb02a5e312cb626277fd0720f9804b6ff5dd0e04`.
- Natif : `07500b848513cf75254eb110112f89a7b7e6d14cda71b4d5ed25d9fcd3da8683`.

Le test standard `GroupedLiveAdmissionPolicyV6EvidenceTest` vérifie l'empreinte native,
recalcule les maxima depuis les échantillons, les limites temporelles et la capacité via
le code d'admission de production, puis exécute les 24 scénarios. Il passe à 14:43:44 UTC
sans navigateur, réseau ni base. La mesure de 35 minutes n'épuise pas une fenêtre horaire
complète : les invariants glissants et leur persistance disposent séparément des tests
PostgreSQL du premier lot. Cette preuve ne décrit ni la latence Internet, ni l'acceptation
du fournisseur, ni une garantie de fraîcheur sous pression du budget partagé.

## Commandes et premières vérifications

Le worktree est `.tmp/wo058-live-j4-j5`, sur la branche WO-058, après `3130e39`.
Le JDK utilisé est `C:/Program Files/Java/jdk-25.0.4`, avec le cache Maven local
`C:/Users/geoff/.m2/repository`. `--offline` borne la résolution Maven ; l'absence
d'accès fournisseur est établie séparément par les fixtures et leurs contrôles.

- `mvnw.cmd --offline -Dmaven.repo.local=C:/Users/geoff/.m2/repository clean verify` :
  réussite le 9 septembre à 13:53:52 UTC, en 8 min 15 s ; 2 018 cas Surefire, cinq
  ignorés, puis 207 cas Failsafe sans ignoré, aucun échec ni erreur. Cette passe valide
  notamment la correction HTML/JavaScript et ses assertions MVC. Les cinq ignorés
  correspondent aux cas de liens Windows et à l'opt-in de qualification Docker J6,
  comme dans le lot précédent.
- Compilation avec `-Pprovider-playwright-runtime,provider-playwright-local-qualification
  -DskipTests package` : réussite à 14:01:34 UTC. Cette commande compile et emballe
  le worker et les fixtures ; les tests ignorés à cette étape ne valent pas qualification.
- Qualification native dédiée : `-DskipTests=false -DskipITs=false
  -Dwo058.grouped.v6=true -Dwo058.grouped.v5=true -Dwo058.grouped.sustained=true
  -Dwo058.ui.matches=7`, classes `LiveGroupedCampaignLocalQualificationIT`,
  `LiveCampaignFormBrowserQualificationIT`, `LiveTenMatchRefreshBrowserQualificationIT`,
  puis `failsafe:integration-test@provider-playwright-loopback-qualification` et
  `failsafe:verify@provider-playwright-loopback-qualification`. Le flag v6 a priorité
  dans le harness de transport ; le flag v5 sélectionne le nominal de 100 s dans
  la fixture d'affichage historique. Le cache Chromium est explicitement fourni
  par `PLAYWRIGHT_BROWSERS_PATH` et `provider.playwright.browser-cache` ; aucun
  téléchargement de navigateur implicite n'est permis.

Cette commande native globale termine initialement en **BUILD FAILURE**, cinq cas dont
une erreur : les deux scénarios de collecte et les deux tests de sélection réussissent,
mais `LiveTenMatchRefreshBrowserQualificationIT` attend un tableau d'incidents visible
dans une fixture ancienne. Le rendu actuel exige une présentation d'incidents et utilise
des cartes ; le tableau technique reste fermé. Ce résultat n'est pas présenté comme un
vert global. La fixture est corrigée et le contrôle d'affichage est rejoué séparément,
avec sa borne de dix secondes conservée ; les mesures natives réussies ne sont pas refaites.

Le rejeu UI termine en **BUILD SUCCESS à 14:52:55 UTC**, trois cas sans échec, erreur ni
ignoré. La publication modifiée apparaît en 4 898 ms ; une réception ultérieure au contenu
identique apparaît en 4 932 ms en conservant les nœuds déjà rendus. Le test couvre sept
rencontres, 21 familles, 945 lignes de statistiques et 210 cartes d'incidents. Les tests
de sélection vérifient aussi la capacité nulle, les limites positives et les deux origines
locales. Les actifs CSS incidents existent dans `app.css`, chargé par la fixture.

Commandes du rejeu, après modification de cette seule fixture : compilation `package`
avec les profils `provider-playwright-runtime,provider-playwright-local-qualification`
et `-DskipTests`, puis les mêmes goals Failsafe natifs avec `-DskipTests=false
-DskipITs=false -Dwo058.grouped.v6=true -Dwo058.ui.matches=7
-Dit.test=LiveCampaignFormBrowserQualificationIT,LiveTenMatchRefreshBrowserQualificationIT`.
La borne browser-cache reste explicite. Journaux : `v6-ui-recompile.log` et
`v6-ui-qualified.log` dans le même répertoire local de vérification.

Le générateur de profil utilise le rapport soutenu exact et les classes de production
compilées ; sa source est conservée dans `v6-profile-builder/V6MeasuredProfile.java`.
Son résultat `V6_MEASURED_CAPACITY=7`, ses 24 scénarios et les deux empreintes sont ensuite
contrôlés indépendamment par le test standard ci-dessus. Aucun fichier du lanceur n'est
modifié par cet outil.

### Vérification finale

Après ajout des preuves et correction de la fixture UI, `mvnw.cmd --offline
-Dmaven.repo.local=C:/Users/geoff/.m2/repository clean verify` termine en **BUILD SUCCESS
à 15:01:41 UTC**, en 8 min 15 s : **2 019 cas Surefire**, cinq ignorés, et **207 cas
Failsafe PostgreSQL**, aucun ignoré ; zéro échec et zéro erreur. Les cinq ignorés sont
les mêmes que lors de la première passe. Cette commande exécute déjà les intégrations
héritées de Failsafe ; une seconde passe identique avec `-Pintegration-tests` n'est pas
ajoutée pour ce complément sans changement de persistance applicative ni migration.

Le contrôle `node --check src/main/resources/static/js/live-campaign.js`, l'analyse
syntaxique PowerShell du script de qualification et `git diff --check` passent.
La revue indépendante du HTML/JavaScript, du harness v6 et du test de preuve ne relève
aucun blocage dans son périmètre. Aucun secret n'est repéré dans les fichiers du diff ;
les fichiers de preuves contiennent des mesures synthétiques sans corps de réponse.
`server.address: 127.0.0.1` et les opt-ins fournisseur désactivés par défaut restent
inchangés. La revue humaine, l'application du lanceur et une éventuelle campagne réelle
restent distinctes de ces résultats.

## Configuration opérateur préparée

Les neuf valeurs exactes figurent dans le [runbook](../runbooks/LIVE-J4-J5-CAMPAIGNS.md).
Une prévisualisation en lecture seule sur le lanceur Eclipse existant confirme l'ajout
exclusif de ces neuf entrées, sans modifier les autres attributs XML ni les paramètres
six rencontres / 30 s. Le programme utilitaire reste local et ignoré, avec sauvegarde
des octets originaux avant toute application. L'application exige la fermeture d'Eclipse
afin d'éviter que son cache n'écrase le fichier ; elle attend la réponse opérateur.
Le checkout humain et la base opérateur ne sont pas modifiés par cette préparation.

Les journaux propres à cette reprise sont conservés localement dans
`.tmp/resilience-verification/selection-capacity-clean-verify.log`,
`v6-capacity-package.log` et `v6-capacity-native.log`.

## Frontières

Aucun appel SofaScore, lancement de campagne réelle, réarmement, modification de VPN,
effacement des compteurs ou écriture dans la base opérateur n'est nécessaire à ce
complément. La capacité technique mesurée reste distincte de la disponibilité du fournisseur.

## Fichiers du complément

- `.gitattributes`
- `ADR-SS-005-bounded-local-live-j4-j5-campaigns.md`
- `CHANGELOG.md`
- `docs/architecture/ARCHITECTURE.md`
- `docs/architecture/LIVE-J4-J5-CAMPAIGNS.md`
- `docs/architecture/LIVE-PROVIDER-RESILIENCE-PROPOSAL-20260909.md`
- `docs/runbooks/LIVE-J4-J5-CAMPAIGNS.md`
- `docs/validation/v6-profile-builder/V6MeasuredProfile.java`
- `docs/validation/WO-058-provider-resilience-qualification-20260909.md`
- `docs/validation/WO058-GROUPED-LIVE-V6-NATIVE-20260909.json`
- `docs/validation/WO058-GROUPED-LIVE-V6-PROFILE-20260909.json`
- `docs/validation/WO058-LIVE-V6-CAPACITY-20260909.md`
- `docs/work_orders/completed/WO-SS-20260907-058-bounded-live-j4-j5.md`
- `README.md`
- `scripts/Invoke-LiveGroupedPlaywrightQualification.ps1`
- `src/main/resources/static/js/live-campaign.js`
- `src/main/resources/templates/events.html`
- `src/provider-playwright-qualification-test/java/com/bettingproject/sofascorelocal/adapter/web/LiveCampaignFormBrowserQualificationIT.java`
- `src/provider-playwright-qualification-test/java/com/bettingproject/sofascorelocal/adapter/web/LiveTenMatchRefreshBrowserQualificationIT.java`
- `src/provider-playwright-qualification-test/java/com/bettingproject/sofascorelocal/application/network/playwright/LiveGroupedCampaignLocalQualificationIT.java`
- `src/provider-playwright-qualification-test/java/com/bettingproject/sofascorelocal/application/network/playwright/LiveProviderSessionQualificationIT.java`
- `src/test/java/com/bettingproject/sofascorelocal/adapter/web/EventExplorerControllerTest.java`
- `src/test/java/com/bettingproject/sofascorelocal/application/live/GroupedLiveAdmissionPolicyV6EvidenceTest.java`

## Empreintes des journaux locaux

| Journal | SHA-256 |
|---|---|
| `selection-capacity-clean-verify.log` | `ec96bd45327b93cc397834ce0fde8ff8d1655f7234abd3433e5009eba234a520` |
| `v6-capacity-package.log` | `c68bc62d6ad51d80bfe71aa8ad69889b1f91d434d1870052f77b8c71a71aa484` |
| `v6-capacity-native.log` | `cbfead1e516758d3307a1d4a84d6157f5323abf472f6b23dc299c410ef0e943a` |
| `v6-profile-evidence-test.log` | `b32e234c1424b53340d013bc1e76dd10581809bc919af850aabff97c712b55cc` |
| `v6-ui-recompile.log` | `10db9007954eb3d0794e5debf13d3f4564f8caad4ca3073de4cac1d91787b928` |
| `v6-ui-qualified.log` | `b976218baf425e28acd11f86374e2ab274dce3c6d70691fb3611555e794fbfab` |
| `v6-final-clean-verify.log` | `31b444c78bbc2ed19c54c774f407e7a8cea06debccddadfeb1fb8636f82938ea` |

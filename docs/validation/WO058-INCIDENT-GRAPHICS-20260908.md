# WO-058 — Présentation graphique des incidents

Statuts : `EXPERIMENTAL`, `LOCAL_ONLY`, `NOT_PRODUCTION_APPROVED`, `NO_CRITICAL_DEPENDENCY`.

Demande opérateur du 8 septembre 2026 : améliorer les incidents à partir des repères
⚽, 🥅, 🏃🏻‍♂️, 🧤 et des captures comparant SofaScore au tableau normalisé du Lab.
Ce complément accompagne le [correctif de l’accueil pendant une clôture](WO058-RECOVERY-DASHBOARD-20260908.md).

## Présentation et fidélité des données

La campagne live et la page J5 d’une rencontre partagent une liste avec minute, icône,
libellé, équipe et joueur. Les buts portent leur score dans une pastille ; les cartons
jaunes, rouges et seconds jaunes restent distincts. Les remplacements indiquent
« Entrée » en vert et « Sortie » en rouge, avec flèches et noms séparés. La mention
« Remplacement sur blessure » dépend du signal `injury=true` reçu, sans le déduire
des noms ou de la minute ; l’exemple opérateur du 9 septembre illustre ce cas déjà couvert.
Passe décisive,
motif, VAR et temps additionnel sont affichés quand ces informations sont présentes.

Les émojis décoratifs sont cachés aux lecteurs d’écran et accompagnés de libellés.
Le côté est également écrit, sans dépendre uniquement de la couleur. Les noms longs
peuvent revenir à la ligne ; la liste s’adapte à une largeur de 390 pixels.

L’ordre du fournisseur et chaque occurrence sont conservés, y compris deux lignes
identiques. La minute additionnelle n’est pas recalculée. Le score d’un incident vient
de sa propre paire normalisée et n’est pas remplacé par le score J4 courant. Un repère
de période à 45 ne prouve pas que cette minute est déjà atteinte. Un penalty manqué
n’est présenté comme arrêté que si le motif reçu le justifie ; aucun gardien n’est inventé.
La convention explicite du propriétaire du 9 septembre distingue **🧤 Penalty arrêté**
(arrêt du gardien confirmé) et **❌ Penalty manqué** (échec sans cette confirmation).
La même distinction s’applique aux tirs au but ; 🥅 reste utilisé pour le penalty accordé.

Les types non reconnus gardent leur texte, avec une présentation neutre. Les contenus
fournisseur passent par `th:text` ou `textContent`, sans interprétation HTML. Une liste
explicitement vide reste distincte d’une famille indisponible. La provenance, le hash,
le parseur, la complétude et la réception restent visibles dans la famille.

Le tableau normalisé existant est conservé sous « Tableau normalisé (technique) »,
replié initialement. L’actualisation live garde son état ouvert ou fermé et le focus
des autres familles. La représentation graphique est ajoutée au modèle de lecture ;
le contrat du tableau existant reste disponible. Les parseurs, la persistance, les
cadences et les appels fournisseur ne sont pas modifiés.

## Qualification

La première passe ciblée, le 8 septembre à 21:49:34 UTC, réussit 232 cas Surefire :
13 accueil, 38 présentation des incidents, 17 contrôleur J5, 114 contrôleur live et
50 présentation live ; aucun échec, erreur ou ignoré. Elle compile également la
qualification Chromium sous les profils explicites.

La première passe navigateur révèle un marqueur de volet supprimé par le rendu
Thymeleaf lorsqu’il vaut une chaîne vide. Le marqueur porte désormais la valeur
stable `true`. La revue indépendante relève également un tableau technique vide
possible sur une famille indisponible : il dépend désormais d’une vue lisible non
nulle et son cache est invalidé lors de son retrait. Une ancienne vue conservée
après HTTP 404 reste affichée. Un scénario dédié qualifie ces transitions.

Après recompilation à 21:59:03 UTC, la passe Chromium finale réussit le 8 septembre
à **22:01:05 UTC** (9 septembre à 00:01:05 Europe/Paris) : **quatre cas, zéro échec,
erreur ou ignoré**, en 1 min 25 s. Les trois scénarios incidents/accueil et le scénario
de pagination utilisent les contrôleurs et templates réels via MockMvc, avec toutes
les requêtes interceptées en mémoire. Aucun socket opérateur, accès à PostgreSQL,
lancement réel de campagne ou appel fournisseur n’est utilisé. Les trois scénarios
incidents/accueil n’envoient aucun POST. La régression de pagination vérifie trois POST
simulés par MockMvc (arrêt individuel, arrêt global et préparation), sans mutation
de l’état opérateur réel.

Les scénarios vérifient le premier rendu sans JavaScript, le polling après changement
et après nouvelle réception identique, la séparation score J4/incident J5, la conservation
des occurrences, du volet et du focus statistiques, l’échappement du texte, les largeurs
1440 et 390 pixels, puis les transitions indisponible/vide/lisible/404 et recréation du
volet. Le test accueil interdit les mutations de campagne et les plans de purge fictifs.
La pagination 10/7 et les diagnostics de clôture restent qualifiés dans le quatrième cas.

Commandes depuis le worktree WO-058, Java 25.0.4 et Maven Wrapper :

```powershell
.\mvnw.cmd --offline '-Dmaven.repo.local=C:/Users/geoff/.m2/repository' `
  '-Pprovider-playwright-runtime,provider-playwright-local-qualification' `
  '-Dtest=DashboardControllerTest,IncidentPresentationTest,LiveCampaignPresentationTest,J5EventDataControllerTest,LiveCampaignControllerTest' test

.\mvnw.cmd --offline '-Dmaven.repo.local=C:/Users/geoff/.m2/repository' `
  '-Pprovider-playwright-runtime,provider-playwright-local-qualification' test-compile

$env:PLAYWRIGHT_BROWSERS_PATH = (Resolve-Path .tmp/provider-playwright-browsers).Path
$env:PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD = '1'
.\mvnw.cmd --offline '-Dmaven.repo.local=C:/Users/geoff/.m2/repository' `
  '-Pprovider-playwright-runtime,provider-playwright-local-qualification' `
  '-DskipTests=false' '-DskipITs=false' `
  '-Dit.test=LiveCampaignIncidentsBrowserQualificationIT,LiveCampaignPaginationBrowserQualificationIT' `
  "-Dprovider.playwright.browser-cache=$env:PLAYWRIGHT_BROWSERS_PATH" `
  'failsafe:integration-test@provider-playwright-loopback-qualification' `
  'failsafe:verify@provider-playwright-loopback-qualification'
```

Les captures synthétiques dans `.tmp/incident-graphics-qualification/` sont ignorées Git.
Relecture visuelle effectuée sur `incidents-live-updated-1440.png`,
`incidents-live-last-readable-after-404-390.png`, `incidents-ssr-unavailable-390.png`
et `dashboard-retention-provider-busy-390.png`. La liste et le panneau de rétention
restent lisibles. Cette vérification n’affirme pas que tous les tableaux de métadonnées
historiques du Lab s’adaptent au mobile ; elle porte sur les composants modifiés.
Les captures utilisateur servent de référence visuelle et ne sont pas des preuves
d’exécution du nouveau code.

Journaux : `.tmp/incident-graphics-targeted.log`, `.tmp/incident-graphics-compile-r2.log`
et `.tmp/incident-graphics-browser-r2.log`. La première passe navigateur échouée reste
dans `.tmp/incident-graphics-browser.log` ; elle ne constitue pas la qualification finale.
La revue indépendante finale ne relève plus de défaut dans le périmètre corrigé.

Une première suite complète `clean verify` réussit le 8 septembre à **22:09:13 UTC**
(9 septembre à 00:09:13 Europe/Paris), en 7 min 23 s : 1 912 cas Surefire recensés,
dont cinq ignorés, et 166 cas Failsafe, sans échec ni erreur. Les quatre cas de liens
symboliques sont ignorés faute de support dans cet environnement Windows ; le cinquième
est la qualification Docker J6 à trois passes, qui exige une propriété explicite absente.
Le Lab avait été arrêté par l’opérateur et aucun listener n’était présent sur 8087.

Après ce succès, le propriétaire précise l’icône ❌ pour un penalty manqué générique.
Le mapping et ses assertions sont adaptés, y compris l’assertion Chromium sur les deux
icônes arrêté/manqué. La nouvelle passe complète réussit à **22:19:24 UTC**
(9 septembre à 00:19:24 Europe/Paris), en **7 min 26 s** : **1 912 cas Surefire, dont
cinq ignorés pour les mêmes raisons, et 166 cas Failsafe ; zéro échec ou erreur**.
Preuves : `.tmp/incident-graphics-clean-verify-r3.log` et
`.tmp/incident-graphics-clean-verify-r3-counts.json`. La première passe complète est
conservée dans `.tmp/incident-graphics-clean-verify.log`.

La recompilation du profil navigateur réussit à **22:20:22 UTC** en 16,198 s.
La passe Chromium finale sur cette convention réussit à **22:22:33 UTC**
(9 septembre à 00:22:33 Europe/Paris) : **quatre cas, zéro échec, erreur ou ignoré**,
en **1 min 29 s**. Les trois marqueurs accueil/graphisme/disponibilité sont `PASS` ;
les icônes 🧤 et ❌ sont vérifiées dans cet ordre sur les incidents correspondants.
Les captures large et 390 pixels sont régénérées puis relues : score du but sur penalty,
cartons, entrées/sorties, arrêt et échec générique sont lisibles. Les mêmes limites
de périmètre et de données synthétiques s’appliquent.

Journaux finaux : `.tmp/incident-graphics-compile-r3.log` et
`.tmp/incident-graphics-browser-r3.log`. Les commandes de compilation et de qualification
sont celles détaillées ci-dessus. Aucune migration ni modification de persistance
n’est ajoutée par ce complément. La configuration conserve `server.address=127.0.0.1`
et les activations fournisseur/live désactivées par défaut. Les 23 fichiers du complément
sont décodés strictement en UTF-8, le diff est relu et vérifié sans erreur de whitespace
ni secret détecté ; les liens Markdown locaux des huit documents modifiés sont valides.

```powershell
.\mvnw.cmd --offline '-Dmaven.repo.local=C:/Users/geoff/.m2/repository' clean verify
```

## Fichiers concernés

Le complément accueil/incidents porte sur les 23 fichiers suivants, relativement à la racine :

```text
CHANGELOG.md
README.md
docs/runbooks/LIVE-J4-J5-CAMPAIGNS.md
docs/validation/WO058-INCIDENT-GRAPHICS-20260908.md
docs/validation/WO058-RECOVERY-DASHBOARD-20260908.md
docs/validation/WO058-LIVE-CLEANUP-INCIDENT-20260908.md
docs/validation/WO058-LIVE-FAILURE-DIAGNOSTICS-20260908.md
docs/work_orders/active/WO-SS-20260907-058-bounded-live-j4-j5.md
src/main/java/com/bettingproject/sofascorelocal/adapter/web/DashboardController.java
src/main/java/com/bettingproject/sofascorelocal/adapter/web/IncidentPresentation.java
src/main/java/com/bettingproject/sofascorelocal/adapter/web/J5EventDataController.java
src/main/java/com/bettingproject/sofascorelocal/adapter/web/LiveCampaignPresentation.java
src/main/resources/static/css/app.css
src/main/resources/static/js/live-campaign.js
src/main/resources/templates/dashboard.html
src/main/resources/templates/event-statistics.html
src/main/resources/templates/live-campaign.html
src/main/resources/templates/fragments/incidents.html
src/test/java/com/bettingproject/sofascorelocal/adapter/web/DashboardControllerTest.java
src/test/java/com/bettingproject/sofascorelocal/adapter/web/IncidentPresentationTest.java
src/test/java/com/bettingproject/sofascorelocal/adapter/web/J5EventDataControllerTest.java
src/test/java/com/bettingproject/sofascorelocal/adapter/web/LiveCampaignPresentationTest.java
src/provider-playwright-qualification-test/java/com/bettingproject/sofascorelocal/adapter/web/LiveCampaignIncidentsBrowserQualificationIT.java
```

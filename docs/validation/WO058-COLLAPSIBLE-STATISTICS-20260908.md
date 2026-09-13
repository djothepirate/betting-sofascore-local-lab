# WO-058 — Encadrés statistiques repliables

Retour opérateur du 8 septembre 2026, après `f0a32ff72d6eb21ec1c0ac2883b37a6c0cfea8f1`.
Worktree `.tmp/wo058-live-j4-j5`, branche `feature/V0.1.0-RC01-CODEX-WO-SS-20260907-058`.
Statuts : `EXPERIMENTAL`, `LOCAL_ONLY`, `NOT_PRODUCTION_APPROVED`, `NO_CRITICAL_DEPENDENCY`.

## Demande et comportement

Le propriétaire confirme la présence des graphiques dans les statistiques et demande de
pouvoir dérouler/enrouler les bandeaux verts des périodes et gris des groupes. Le fragment
partagé et son rendu JavaScript utilisent désormais des `details/summary` imbriqués :

- chaque bandeau vert ouvre/replie l'ensemble d'une période ;
- chaque bandeau gris ouvre/replie uniquement son groupe ;
- tous les blocs sont ouverts par défaut, avec un chevron natif indiquant leur état ;
- clic, Entrée et Espace utilisent le comportement natif, sans gestionnaire clavier personnalisé ;
- les actions restent possibles sans JavaScript dans les pages campagne et statistiques J5 ;
- le rendu secondaire de détail de rencontre reçoit le même composant par le polling existant.

Pendant le polling, les états sont repris par code de période et nom de groupe dans le
composant courant. Deux groupes de même nom dans des périodes différentes restent indépendants.
Fermer puis rouvrir une période ne change pas l'état de ses groupes. Le focus d'un en-tête
remplacé est restauré sans déplacement volontaire du défilement ; si le groupe disparaît,
le focus revient à l'en-tête de période, ou au sélecteur si la période disparaît aussi.
Les éléments nouveaux sont ouverts. Un rechargement complet de page rétablit les défauts :
aucune préférence persistante, aucun localStorage ni changement de base ne sont introduits.

Le composant garde les valeurs, graphiques et règles de période déjà qualifiés. La CSP reste
inchangée, avec CSS/JS externes. Aucun parseur, endpoint, migration ou flux de collecte n'est modifié.

## Vérifications

Environnement : Windows, Java 25.0.4, wrapper Maven du dépôt, PostgreSQL Docker local pour
les tests d'intégration. Une première lecture restreinte du port 8087 n'a renvoyé aucun résultat ;
elle ne constituait pas une preuve de port libre. Après échec Maven, la lecture système autorisée
identifie l'application du Lab, PID `44376`, démarrée à 00:24:36 Europe/Paris, en écoute sur
`127.0.0.1:8087`. Le propriétaire confirme ensuite sa libération ; le précontrôle système de la
seconde exécution établit `PORT_8087_FREE=PASS`. Aucun processus opérateur n'est arrêté par l'agent.

La qualification Chromium opt-in utilise le cache existant dans
`.tmp/provider-playwright-browsers`, les routes MockMvc en mémoire et des données synthétiques.
Elle n'ouvre aucun listener HTTP et ne lance aucune collecte fournisseur. Commandes :

```powershell
.\mvnw.cmd --offline '-Dmaven.repo.local=C:/Users/geoff/.m2/repository' '-Pprovider-playwright-runtime,provider-playwright-local-qualification' -DskipTests package
.\mvnw.cmd --offline '-Dmaven.repo.local=C:/Users/geoff/.m2/repository' '-Pprovider-playwright-runtime,provider-playwright-local-qualification' '-DskipTests=false' '-DskipITs=false' '-Dit.test=LiveCampaignStatisticsBrowserQualificationIT,LiveCampaignFormBrowserQualificationIT' '-Dprovider.playwright.browser-cache=C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo058-live-j4-j5/.tmp/provider-playwright-browsers' 'failsafe:integration-test@provider-playwright-loopback-qualification' 'failsafe:verify@provider-playwright-loopback-qualification'
.\mvnw.cmd clean verify
git diff --check
```

Compilation UI : `BUILD SUCCESS` le 07/09 à 22:43:10Z. Qualification Chromium :
`BUILD SUCCESS` à 22:44:34Z (08/09 à 00:44:34 Europe/Paris), **3 tests, 0 échec, 0 erreur,
0 ignoré**, rapports XML relus. Le scénario existant des statistiques vérifie les deux
niveaux ouverts par défaut, clic/Entrée/Espace avec et sans JavaScript, fermeture du parent
sans perte de l'état enfant, indépendance entre périodes, conservation des états et du focus
sur deux rafraîchissements, puis repli mobile sans débordement. Les deux tests de formulaires
restent verts. Les contrôles précédents de valeurs absentes, zéros, ratios invalides, possession,
CSP et absence de requête externe restent dans le scénario.

Premier `clean verify` : **BUILD FAILURE** le 07/09 à 22:50:07Z (08/09 à 00:50:07 Paris),
code natif 1 ; **1 590 tests Surefire, 1 échec, 0 erreur, 5 ignorés**. Le seul échec concerne
`J6NativeBinaryPipelineQualificationTest.syntheticNativePipelineFailsClosedWithoutHumanPassphraseInput`,
motif `LOOPBACK_APPLICATION_LISTENER_RESIDUAL`. Les marqueurs de nettoyage des processus possédés
par J6 et du répertoire synthétique sont `PASS` ; ce scénario ne lance aucune application Java.
Le contrôle final détecte le port occupé sans en attribuer le propriétaire. Failsafe n'est pas
atteint. Le log et le XML J6 sont conservés séparément dans le répertoire de preuves avec le
suffixe `attempt1` ; aucun résultat historique n'est remplacé par un succès.

Second `clean verify`, après confirmation opérateur et précontrôle du port libre :
**BUILD SUCCESS** le 07/09 à 22:57:38Z (08/09 à 00:57:38 Paris), code natif 0, durée 5 min 18 s.
Les rapports XML courants donnent **1 590 tests Surefire, 0 échec, 0 erreur, 5 ignorés** et
**138 tests Failsafe, 0 échec, 0 erreur, 0 ignoré**. Les cinq exclusions sont les quatre
contre-épreuves de liens symboliques J7/J8 non disponibles dans cet environnement Windows et
`dockerQualificationPassesThreeSequentialRunsAtEffectiveDefaults`, dont l'opt-in J6 est absent.
L'exécution Failsafe `default` héritée est bien atteinte : aucun second cycle
`-Pintegration-tests verify` ni lanceur `Verify-Local.ps1` redondant n'est ajouté pour ce
changement d'affichage sans persistance. La relance répond à l'échec environnemental observé ;
le code applicatif reste identique à celui de la qualification Chromium.

Log vert : `.tmp/wo058-statistics-disclosures-verify-attempt2.log`, SHA-256
`0449cdedfcd81a3440682406f59d64e0abd440159c883cc424863599953e54b5` ; copie et résumé Failsafe
conservés dans le répertoire de preuves. `git diff --check`, syntaxe JavaScript (`node --check`),
UTF-8 strict des huit fichiers et liens du rapport passent. Le diff ne contient aucun secret ;
la configuration d'écoute `server.address: 127.0.0.1`, les garde-fous fournisseur et les tests
standards sans appel SofaScore restent inchangés. La base opérateur n'est pas modifiée.

Les cinq captures, deux XML et deux logs UI sont conservés hors de `target` dans
`.tmp/wo058-statistics-disclosures-evidence/` pour survivre au `clean`. Les captures bureau et
mobile ont été relues visuellement : chevrons, focus clavier et groupes repliés sont lisibles.
Empreintes SHA-256 des deux nouvelles captures :

- `statistics-collapsed-desktop.png` : `3936c91ce94a620d143e2dcec403b729f43ae35417e8c5b40a41d08886ff62a4` ;
- `statistics-collapsed-mobile.png` : `fd6184a4b5970d405a54465a94d791d8b1b2438997a6a0b422298905309035e3`.

La revue indépendante du fragment, du style et du rendu JavaScript ne relève aucun défaut.
Les captures utilisent les ressources de l'application et des données synthétiques ; elles
ne constituent pas une nouvelle campagne ni une qualification de contenu fournisseur.

## Fichiers modifiés

- [Fragment HTML](../../src/main/resources/templates/fragments/statistics.html)
- [Style des bandeaux](../../src/main/resources/static/css/statistics.css)
- [Rendu et conservation de l'état](../../src/main/resources/static/js/statistics.js)
- [Qualification Chromium existante](../../src/provider-playwright-qualification-test/java/com/bettingproject/sofascorelocal/adapter/web/LiveCampaignStatisticsBrowserQualificationIT.java)
- [Architecture live](../architecture/LIVE-J4-J5-CAMPAIGNS.md)
- [Work Order](../work_orders/completed/WO-SS-20260907-058-bounded-live-j4-j5.md)
- [Changelog](../../CHANGELOG.md)
- Ce rapport.

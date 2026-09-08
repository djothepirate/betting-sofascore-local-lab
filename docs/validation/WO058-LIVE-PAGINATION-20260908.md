# WO-058 — Pagination live et affichage après arrêt

Statuts : `EXPERIMENTAL`, `LOCAL_ONLY`, `NOT_PRODUCTION_APPROVED`, `NO_CRITICAL_DEPENDENCY`.

Base : `9b96f0733e199bc80e71e8b7812926212eb7e599`, branche
`feature/V0.1.0-RC01-CODEX-WO-SS-20260907-058`. Complément demandé le 8 septembre 2026.

## Périmètre réalisé

- Dix rencontres par page, navigation au-dessus et au-dessous des cartes dès onze rencontres.
- Projection des seules cartes de la page avant chargement des données normalisées riches.
  Le store lit encore le ledger complet : ce complément n’est pas une pagination SQL.
- Ordre stable du manifeste, y compris pour les rencontres arrêtées ou terminées.
- `GET /live-campaigns/{id}?page=N` et lecteur local `/state?page=N` utilisent la même page.
  La première page est implicite ; une page au-delà de la dernière est bornée à celle-ci ;
  zéro, nombres négatifs, texte et dépassements d’entier sont refusés avec HTTP 400.
- Le lien entrant `?eventId=UUID#live-event-UUID` choisit la page contenant la rencontre.
  Une identité absente de cette campagne retourne HTTP 404.
- `/state` sans page et les routes `/events/state`, `/events/{id}/state` restent complets.
- Compteurs, budgets, cadence, arrêt global, lancement et nouvelle préparation portent
  sur la sélection entière. Les actions concernant la campagne conservent la page consultée.
- Débit et autonomie estimés deviennent nuls si le processus indique `collectionStopped`.
  Le rendu initial et dynamique affichent alors « Collecte arrêtée ».
- La notice de clôture dans le tableau des rencontres devient un bloc ; ses dimensions
  participent à la hauteur de la cellule au lieu de chevaucher ses voisins.

Aucune migration, modification de cadence, nouveau transport ou endpoint fournisseur.
Ce correctif n’a pas été chargé dans l’application Eclipse durant l’observation utilisateur.

## Diagnostic distinct de la campagne réelle

La lecture locale de `60fd08dd-074e-4994-a0d5-51aff9eaa9db` confirme à la révision 11782 :
état durable `RUNNING`, processus `STOPPED_ERROR`, motif `LOCAL_CLEANUP_PENDING`, collecte
arrêtée et clôture en attente. Les compteurs sont figés à 2 189 appels et 44 027 545 octets,
avec deux rencontres `FINISHED_CONFIRMED` et quinze `COLLECTING` durables. La dernière
réception publiée visible est `2026-09-08T20:18:46.012Z`. Ni la fin maximale à `23:12:42Z`
ni les plafonds 20 000 appels / 15 728 640 000 octets ne sont atteints.

L’utilisateur signale ensuite que le bouton de clôture le ramène au même bandeau.
Les messages de console fournis sont des échecs d’écriture de réponses JSON Tomcat vers
le navigateur ; ils ne donnent pas la cause de l’arrêt de la collecte. Le diagnostic et
la réparation de cette clôture restent séparés de la qualification de la pagination.
Les lectures SQL et processus et le parcours de récupération sont consignés dans le
[diagnostic de clôture](WO058-LIVE-CLEANUP-INCIDENT-20260908.md).

## Vérifications du complément

Java 25.0.4, Maven Wrapper, Spring Boot 4.1.0, Windows. Commandes depuis le worktree WO-058.
Les accès à Maven nécessitent le contexte autorisé : une première compilation en bac à
sable échoue sur `AccessDeniedException` lors de la fermeture d’un JAR du cache existant.
L’exécution autorisée du même périmètre réussit ; aucun téléchargement n’est requis.

```powershell
.\mvnw.cmd --offline '-Dmaven.repo.local=C:/Users/geoff/.m2/repository' `
  '-Dtest=LiveCampaignPresentationTest,LiveCampaignControllerTest' test
```

Résultat le 8 septembre à 20:30:10 UTC : **155 cas, zéro échec, erreur ou ignoré**
(109 contrôleur, 46 présentation). Les scénarios couvrent 17→10/7, les limites 10/11,
les états de cycle de vie, le JSON complet ou paginé, les liens entrants, les redirections,
la sélection complète de lancement/re-préparation et l’autonomie après arrêt du processus.

```powershell
.\mvnw.cmd --offline '-Dmaven.repo.local=C:/Users/geoff/.m2/repository' `
  '-Pprovider-playwright-runtime,provider-playwright-local-qualification' '-DskipTests' package
$env:PLAYWRIGHT_BROWSERS_PATH = (Resolve-Path .tmp/provider-playwright-browsers).Path
$env:PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD = '1'
.\mvnw.cmd --offline '-Dmaven.repo.local=C:/Users/geoff/.m2/repository' `
  '-Pprovider-playwright-runtime,provider-playwright-local-qualification' `
  '-DskipTests=false' '-DskipITs=false' `
  '-Dit.test=LiveCampaignPaginationBrowserQualificationIT,LiveCampaignBrowserQualificationIT,LiveCampaignStatisticsBrowserQualificationIT,LiveCampaignFormBrowserQualificationIT' `
  "-Dprovider.playwright.browser-cache=$env:PLAYWRIGHT_BROWSERS_PATH" `
  'failsafe:integration-test@provider-playwright-loopback-qualification' `
  'failsafe:verify@provider-playwright-loopback-qualification'
```

Le packaging réussit à 20:31:12 UTC ; il compile les tests sans les exécuter.
La qualification navigateur utilise des réponses synthétiques interceptées en mémoire,
sans listener sur le port opérateur ni appel fournisseur. À 20:34:13 UTC : **7 scénarios
réussis, aucun échec, erreur ou ignoré** (3 lecteur/notice, 2 formulaires, 1 pagination,
1 statistiques). La notice est vérifiée à 1440, 1024 et 390 pixels.

Un complément vérifie ensuite le libellé d’autonomie initial et les changements de statut
du processus à révision durable inchangée. Exécutions ciblées justifiées par ces ajouts :

- à 20:38:48 UTC, commande `test` ci-dessus avec les deux profils navigateur pour compiler
  leur scénario également : **156 cas réussis**, sans échec, erreur ou ignoré
  (110 contrôleur, 46 présentation) ;
- à 20:40:17 UTC, mêmes objectifs Failsafe avec seulement
  `-Dit.test=LiveCampaignPaginationBrowserQualificationIT` : **1 scénario réussi**, sans
  échec, erreur ou ignoré. À révision 3 inchangée, l’arrêt runtime affiche « Collecte arrêtée »
  et son retrait rétablit l’estimation ; les sept cartes et la réception restent identiques.

Les captures synthétiques sont sous `.tmp/live-pagination-qualification/` (ignoré Git).
La capture de la seconde page a été relue : navigation lisible, page 2 active et onzième carte.

`git diff --check` réussit. La revue indépendante ne relève aucun défaut actionnable.
`server.address=127.0.0.1` demeure configuré ; aucun payload, cookie, secret ou nouveau
endpoint fournisseur n’est ajouté dans le diff. Le profil d’intégration PostgreSQL n’est
pas requis par ces changements de restitution sans modification de persistance.

Après arrêt du Lab par l’utilisateur et vérification que 8087 n’écoute plus, la porte complète
exigée par AGENTS.md a été exécutée sur ce complément :

```powershell
.\mvnw.cmd --offline '-Dmaven.repo.local=C:/Users/geoff/.m2/repository' clean verify
```

Résultat le 8 septembre à **20:57:02 UTC : BUILD SUCCESS**, en 7 min 10 s.
Surefire : **1 860 cas, zéro échec, zéro erreur, 5 ignorés** (quatre scénarios de liens
symboliques non disponibles et une qualification native J6 explicitement opt-in).
Failsafe hérité : **166 cas PostgreSQL/loopback, zéro échec, erreur ou ignoré**.
Le journal propre à cette exécution est `.tmp/pagination-clean-verify.log` (ignoré Git).
Il remplace l’attente liée au port 8087 ; les anciens résultats v5 n’ont pas été réutilisés.

## Fichiers du complément

| Portée | Fichiers |
|---|---|
| Projection et navigation serveur | `src/main/java/com/bettingproject/sofascorelocal/adapter/web/LiveCampaignController.java`, `LiveCampaignPresentation.java` dans le même répertoire |
| HTML | `src/main/resources/templates/live-campaign.html`, `events.html`, `fragments/live-pagination.html` |
| Rendu et lecteur | `src/main/resources/static/css/app.css`, `src/main/resources/static/js/live-campaign.js` |
| Tests MVC et présentation | `src/test/java/com/bettingproject/sofascorelocal/adapter/web/LiveCampaignControllerTest.java`, `LiveCampaignPresentationTest.java` |
| Tests navigateur | `src/provider-playwright-qualification-test/java/com/bettingproject/sofascorelocal/adapter/web/LiveCampaignPaginationBrowserQualificationIT.java`, `LiveCampaignBrowserQualificationIT.java` |
| Documentation | `CHANGELOG.md`, `docs/runbooks/LIVE-J4-J5-CAMPAIGNS.md`, WO-058 actif et présent rapport |

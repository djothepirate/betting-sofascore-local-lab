# WO-058 — Présentation des compositions J5

Date : 2026-09-08. Base : `1ba2099c6e6a3c7bcdf4d973cc49eb4218ba658b`.
Branche : `feature/V0.1.0-RC01-CODEX-WO-SS-20260907-058`.
Statuts : `EXPERIMENTAL`, `LOCAL_ONLY`, `NOT_PRODUCTION_APPROVED`, `NO_CRITICAL_DEPENDENCY`.

## Périmètre et données

Le retour propriétaire demande une présentation graphique des compositions pour J5 manuel
et live. Le nouveau composant commun affiche les équipes côte à côte, les formations reçues,
le statut confirmé/provisoire, les titulaires par poste et les remplaçants. Les numéros ont
un badge visible ; les panneaux natifs sont repliables au clavier et au clic.

La projection `LineupsPresentation` ne modifie ni `EventLineups`, ni le parseur, ni les tables
en base. Les postes G/D/M/F sont traduits et regroupés ; les autres valeurs restent littérales.
Le schéma ne déduit aucun placement tactique de la formation ou de l’ordre des joueurs.
Photos, notes, entraîneurs, cartons et changements de joueurs ne sont pas disponibles dans
ce contrat normalisé et ne sont pas ajoutés à cette vue. Aucun endpoint supplémentaire.

La page manuelle utilise son observation locale, et le live résout celle pointée par le
curseur de la famille, avec les noms de l’observation J4 de la campagne. Le dernier import
manuel ne remplace pas silencieusement une observation de campagne. Les références source,
réception, parseur et hashes restent au-dessus du composant. Une indisponibilité HTTP 404
ne crée pas de cartes provisoires vides ; une liste vide effectivement reçue reste explicite.
Si une dernière composition lisible est conservée après un échec, elle reste affichée avec
le signalement existant de donnée antérieure et ses propres références.

## Rendu et actualisations

Le fragment Thymeleaf fournit le rendu initial, également utilisable sans JavaScript. Le
lecteur live transmet la même projection à `LineupsView`. Les clés sont le côté et l’identité
fournisseur du joueur ; le texte reste échappé par Thymeleaf ou écrit par `textContent`.
Les panneaux et joueurs sont réconciliés par clé pour préserver ouverture, fermeture et focus.
Une vue identique ne reconstruit pas le DOM. Aucun style inline, image ou requête externe.

Les tests Chromium utilisent les vrais contrôleurs, templates et ressources avec réponses
MockMvc en mémoire et données synthétiques. Ils ne démarrent ni listener 8087, ni session
fournisseur, ni collecte et ne lisent pas la base de l’opérateur. Leurs captures représentent
ces seules données synthétiques.

## Vérification

Environnement : Windows, Java 25.0.4, Spring Boot 4.1.0, Maven wrapper, dépendances résolues
dans le cache local avec `--offline -Dmaven.repo.local=C:/Users/geoff/.m2/repository`.
Chromium utilise le cache explicite `.tmp/provider-playwright-browsers` avec
`PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD=1`. Les profils navigateur restent limités à la commande dédiée.

| Contrôle | Résultat lu dans les rapports |
|---|---|
| Projection, contrôleurs J5/live et présentation live, `-Dtest=LineupsPresentationTest,J5EventDataControllerTest,LiveCampaignPresentationTest,LiveCampaignControllerTest test` | 128 tests, 0 échec, 0 erreur, 0 ignoré ; `BUILD SUCCESS`, fin 14:17:53 UTC |
| Compilation explicite `-Pprovider-playwright-runtime,provider-playwright-local-qualification -DskipTests package` | `BUILD SUCCESS` ; compilation seulement, pas une exécution de qualification |
| `LiveCampaignLineupsBrowserQualificationIT`, exécution dédiée Failsafe | 1 scénario, 0 échec, 0 erreur, 0 ignoré ; `BUILD SUCCESS`, fin 14:24:29 UTC |
| Syntaxe JavaScript | `node --check` passé pour `lineups.js` et `live-campaign.js` |
| `mvnw.cmd --offline -Dmaven.repo.local=C:/Users/geoff/.m2/repository clean verify` | `BUILD SUCCESS` en 6 min 32 s, fin 14:31:57 UTC ; Surefire : 194 suites, 1 745 tests recensés dont 5 ignorés (1 740 exécutés), 0 échec/erreur ; Failsafe : 7 suites, 151 tests, 0 échec/erreur/ignoré |

Commande Chromium exécutée, après la compilation ci-dessus :

```powershell
.\mvnw.cmd --offline -Dmaven.repo.local=C:/Users/geoff/.m2/repository `
  -Pprovider-playwright-runtime,provider-playwright-local-qualification `
  -DskipTests=false -DskipITs=false -Dit.test=LiveCampaignLineupsBrowserQualificationIT `
  "-Dprovider.playwright.browser-cache=$env:PLAYWRIGHT_BROWSERS_PATH" `
  failsafe:integration-test@provider-playwright-loopback-qualification `
  failsafe:verify@provider-playwright-loopback-qualification
```

Le scénario Chromium vérifie deux équipes de onze titulaires et leurs remplaçants, le rendu
serveur live et manuel sans JavaScript, les champs absents, les noms échappés, le passage
provisoire/confirmé, le changement de groupe d’un joueur et la conservation des nœuds, du focus
et des panneaux repliés. À contenu identique, le composant ne subit aucune mutation alors que
la réception et l’occurrence progressent. Le viewport de 390 pixels ne déborde pas. Les
compteurs vérifiés sont zéro appel fournisseur, zéro POST et aucune base opérateur utilisée.

Les captures de qualification sont locales et ignorées dans `.tmp/lineups-ui-qualification/` :
`lineups-live-desktop.png`, `lineups-live-no-js-mobile.png`, `lineups-manual-no-js-mobile.png`,
`lineups-live-refreshed-mobile.png` et `lineups-empty-mobile.png`. Les vues desktop et mobile
actualisée ont également été inspectées visuellement. Elles incluent volontairement des
chaînes HTML inertes pour vérifier l’échappement ; elles ne représentent aucun match réel.

La vérification `mvnw.cmd clean verify` demandée par AGENTS.md est passée après libération
du port 8087 confirmée par l’opérateur. Aucun changement de persistance ne nécessite un second
passage `-Pintegration-tests verify` : les 151 tests d’intégration PostgreSQL/loopback hérités
de `clean verify` ont effectivement été exécutés. Cette suite standard n’a pas activé Chromium.

Les cinq tests standard ignorés sont quatre contrôles de liens symboliques indisponibles sous
Windows (`LocalJ7ExportFileStoreTest` et `J8BenchmarkExportCommandTest`, deux chacun), et le
scénario J6 de trois passages Docker qui exige `j6.docker.qualification=true`. Cette qualification
supplémentaire n’est pas activée pour ce complément de présentation. Aucun test de composition
n’est ignoré. Le diff est contrôlé sans secret détecté, les dix-huit fichiers sont UTF-8 valides,
et `server.address=127.0.0.1` / `sofascore.enabled=false` par défaut sont inchangés.

Empreintes SHA-256 des journaux locaux :

- Ciblé : `1c8a5e1c12994df11723fdbe9dec0de66e2520278466f623fa6719fccffd8184`.
- Chromium : `8a0eced7c1fe1105fc3ac99ad9b9b3403efa65b1ea1bcc4426e04845138e96c4`.
- Complet : `5896bd12b2af000d4894b6b9198dfc467151e432b2d6563909cd10551598aead`.

Les métadonnées XML par suite et les empreintes sont aussi capturées dans les fichiers locaux
ignorés `.tmp/wo058-lineups-ui-{targeted,native,clean}-evidence.json`. Les métadonnées de la
qualification ont été capturées avant `clean`, qui supprime les rapports navigateur de `target`.
Empreintes des captures inspectées : desktop
`0b8adcb4ba114b8b34ac3c08ce82621cd7f98e5eb2700dee7f1345464ec6c7f2`, mobile actualisée
`145cf5717b04eaaf880af1116f93ca42f24acffe942a28b02737073b9a2920a3`.

La revue indépendante n’a relevé aucune anomalie actionnable. Le navigateur n’exécute pas
spécifiquement la transition composition valide vers 404 ni la fiche `event-detail` complète :
ces chemins utilisent le composant partagé relu, et `UNAVAILABLE` est couvert côté MVC/projection.

## Fichiers du complément

- Présentation et branchements Java : `LineupsPresentation.java`, `J5EventDataController.java`,
  `LiveCampaignPresentation.java`, sous `src/main/java/com/bettingproject/sofascorelocal/adapter/web/`.
- Interface : `static/css/lineups.css`, `static/js/lineups.js`, `static/js/live-campaign.js`,
  `templates/fragments/lineups.html`, `templates/event-statistics.html`, `templates/live-campaign.html`,
  `templates/event-detail.html`, sous `src/main/resources/`.
- Tests standard : `LineupsPresentationTest.java`, `J5EventDataControllerTest.java`,
  `LiveCampaignPresentationTest.java`, sous `src/test/java/com/bettingproject/sofascorelocal/adapter/web/`.
- Qualification : `src/provider-playwright-qualification-test/java/com/bettingproject/sofascorelocal/adapter/web/LiveCampaignLineupsBrowserQualificationIT.java`.
- Documentation : `CHANGELOG.md`, `docs/runbooks/LIVE-J4-J5-CAMPAIGNS.md`,
  `docs/work_orders/completed/WO-SS-20260907-058-bounded-live-j4-j5.md` et ce rapport.

La [fiche du WO](../work_orders/completed/WO-SS-20260907-058-bounded-live-j4-j5.md) et le
[runbook](../runbooks/LIVE-J4-J5-CAMPAIGNS.md) décrivent le retour et la consultation. Les mesures
de fraîcheur fournisseur continuent d’appartenir aux campagnes de l’opérateur.

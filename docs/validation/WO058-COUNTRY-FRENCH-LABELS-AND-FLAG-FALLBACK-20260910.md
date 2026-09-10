# WO-058 — Libellés français de pays et repli fiable des drapeaux locaux

Statuts : `EXPERIMENTAL`, `LOCAL_ONLY`, `NOT_PRODUCTION_APPROVED`,
`NO_CRITICAL_DEPENDENCY`.

## Objet

Ce complément rend les nationalités lisibles en français dans les pages locales qui présentent :

- les entraîneurs et l'arbitre de J4 ;
- les joueurs, remplaçants et indisponibles des compositions J5.

Il ne démarre pas Playwright, ne prépare ni ne lance de campagne, et ne modifie pas le lanceur
Eclipse ni la configuration active de l'opérateur.

## Frontière de données

La correction appartient uniquement à `CountryPresentation` et aux fragments/rafraîchissements
web qui la consomment. Elle ne transforme pas les données de provenance :

- `ProviderCountry` reste tel qu'il a été reçu ;
- les observations normalisées, snapshots bruts, hashes, parseurs et écritures de persistance
  restent inchangés ;
- une valeur absente, inconnue ou ambiguë ne reçoit pas de drapeau inventé.

Lorsqu'un code pays local est identifié sans ambiguïté, son libellé est affiché en français. Les
exceptions de football britannique restent explicites et limitées : `England`/`EN` devient
« Angleterre » avec `gb-eng.svg`, `Scotland`/`SC` devient « Écosse » avec `gb-sct.svg`, et
`Wales`/`WA` devient « Pays de Galles » avec `gb-wls.svg`. Ces associations ne s'appliquent pas
à un couple nom/code non concordant, afin de ne pas attribuer un drapeau erroné à un autre pays.

## Drapeau local et texte de repli

Les drapeaux sont des SVG déjà embarqués sous `static/images/flags/4x3`. La vue ne demande ni
image, ni API, ni ressource à SofaScore ou à un CDN.

Le libellé français est la source de compatibilité et d'accessibilité :

- il reste présent dans l'arbre accessible, précédé de « Pays : » ;
- il reste visuellement affiché tant qu'un SVG local n'a pas réellement signalé son chargement ;
- une erreur de chargement réaffiche ou conserve ce libellé ;
- sans JavaScript, le rendu serveur conserve ce même texte visible ;
- en `forced-colors` ou lorsqu'un contraste renforcé est demandé, le texte redevient visible.

Le masquage visuel n'intervient qu'après un événement de chargement réussi d'une image locale. Un
élément `<img>` présent dans le DOM, une URL définie ou une tentative de chargement ne valent pas
confirmation : ils ne suffisent pas à masquer la nationalité.

## Contrôles hors fournisseur

Les contrôles réalisés à ce stade restent entièrement locaux et ne contactent aucun endpoint
SofaScore.

1. Vérification de syntaxe des rafraîchissements locaux :

       node --check src\main\resources\static\js\lineups.js
       node --check src\main\resources\static\js\event-details.js

   Résultat : succès.

2. Régression ciblée de la projection pays et des rendus J4/J5 :

       .\mvnw.cmd --offline "-Dmaven.repo.local=C:\Users\geoff\.m2\repository" -q "-Dtest=CountryAssetsTest,LineupsPresentationTest,EventDetailsPresentationTest,J5EventDataControllerTest" test

   Résultat : 35 tests, zéro échec et zéro erreur.

3. Qualification Chromium conjointe, exclusivement sur loopback, des personnes J4 et des
   compositions J5 :

       $env:PLAYWRIGHT_BROWSERS_PATH = (Resolve-Path '.tmp\provider-playwright-browsers').Path
       .\mvnw.cmd --offline "-Dmaven.repo.local=C:\Users\geoff\.m2\repository" "-Pprovider-playwright-runtime,provider-playwright-local-qualification" "-DskipTests=false" "-DskipITs=false" "-Dit.test=LiveCampaignLineupsBrowserQualificationIT,EventDetailsBrowserQualificationIT" "-Dprovider.playwright.browser-cache=$env:PLAYWRIGHT_BROWSERS_PATH" "failsafe:integration-test@provider-playwright-loopback-qualification" "failsafe:verify@provider-playwright-loopback-qualification"

   Résultat : 3 tests, zéro échec et zéro erreur (1 pour J4, 2 pour les compositions J5). Les
   scénarios servent les pages et les SVG depuis le worktree, simulent aussi un échec de SVG
   local, et bloquent toute sortie hors loopback. Le texte français ne disparaît qu'après le
   chargement local confirmé ; il reste visible après cet échec. Aucun appel réel fournisseur,
   aucun POST et aucune base opérateur ne sont utilisés.

4. Vérification Maven complète hors fournisseur :

       .\mvnw.cmd --offline "-Dmaven.repo.local=C:\Users\geoff\.m2\repository" clean verify

   Résultat : succès — 2 204 tests unitaires dans 226 suites Surefire et 231 tests
   d'intégration Failsafe, zéro échec et zéro erreur. L'avertissement de compilation sur
   `LiveCampaignPresentation` concerne une API dépréciée préexistante, hors de ce complément.

Les contrôles locaux ne collectent aucune donnée fournisseur, ne journalisent ni cookie, ni
jeton, ni payload brut, et ne modifient aucune campagne existante.

# WO-058 — Visibilité des cartes de compositions après actualisation

Date : 2026-09-08. Base du diagnostic : `06c7e3d272e8f96873eca822e4bdaa8302cf1b01`.
Statuts : `EXPERIMENTAL`, `LOCAL_ONLY`, `NOT_PRODUCTION_APPROVED`, `NO_CRITICAL_DEPENDENCY`.

## Signalement et portée de la reproduction

Le retour opérateur signale douze remplaçants dans le compteur mais onze cartes visibles,
puis la réapparition du joueur. Le diagnostic local a confirmé douze joueurs dans les
observations conservées et dans la projection courante. Les deux réordres constatés ont
été reproduits avec des identités synthétiques : le septième remplaçant passe en tête,
puis le dernier passe en deuxième position. Ces seuls réordres ont conservé les douze
cartes visibles dans le test, banc ouvert comme fermé.

Une autre séquence a reproduit un défaut de rendu : réordre du banc, déplacements de
joueurs entre postes et entre titulaires/remplaçants pendant que le banc est fermé,
puis retrait d’un remplaçant et réouverture. Avec `Element.moveBefore`, les onze nœuds
restants et le compteur à onze étaient présents, mais seuls trois joueurs avaient un
rectangle visible. Les huit autres rectangles mesuraient zéro sur zéro malgré
`display: flex`, `visibility: visible`, `content-visibility: visible` et un panneau ouvert.
L’écart persistait après deux `requestAnimationFrame`, puis une attente bornée à deux
secondes. La même séquence avec `insertBefore` rendait les onze cartes immédiatement.

Ce résultat justifie le correctif de rendu. Il ne prouve pas que le signalement précis
concernant Bamba a suivi cette séquence, ni que sa disparition apparente avait exactement
cette cause. Aucun changement des données, du parseur ou du compteur n’est déduit de ce test.

## Correctif et régression conservée

`static/js/lineups.js` déplace maintenant les nœuds existants avec `insertBefore` dans
`order()`. Les clés et les nœuds restent identiques. Le composant conserve ses panneaux
`details` et utilise la restauration du focus déjà présente dans `update()` après la
réconciliation. Il ne reconstruit pas un composant dont la projection est identique.

La classe `LiveCampaignLineupsBrowserQualificationIT` conserve deux méthodes :

- le scénario antérieur couvre les pages live et manuelle rendues sans JavaScript,
  le rafraîchissement live, les changements et répétitions de contenu, les panneaux,
  le focus, l’échappement, les listes vides et le viewport de 390 pixels ;
- la nouvelle régression couvre les deux réordres du banc, la séquence qui produisait
  des rectangles invisibles, le nombre de cartes effectivement visibles après rendu,
  les compteurs, les clés et noms dans l’ordre attendu, la conservation des nœuds et
  du focus, puis une nouvelle lecture identique et le viewport de 390 pixels.

Le nouveau scénario utilise le vrai fragment SSR manuel et évalue explicitement le
fichier de production `lineups.js` dans Chromium pour isoler sa réconciliation. La page
manuelle reste soumise à sa CSP `script-src 'none'` ; ses messages attendus concernant
les deux scripts interdits sont distingués des erreurs de composant. Aucune CSP ni route
de production n’est assouplie. Le scénario antérieur couvre le chargement normal des
scripts et les actualisations de la page live.

Tous les GET du navigateur sont satisfaits en mémoire par MockMvc. Les contextes sont
neufs et hors ligne. Aucun listener 8087, accès à PostgreSQL opérateur, POST, transport
fournisseur ou appel réel SofaScore n’est effectué. Les captures sont synthétiques et
restent dans `.tmp/lineups-ui-qualification/`.

## Preuve avant correction

La qualification de diagnostic termine en échec le 2026-09-08 à 16:15:44 UTC :
un test exécuté, un échec, zéro erreur, zéro ignoré. L’échec enregistré est
`stage=5,fallback=false` ; la comparaison avec `insertBefore` passe.

Les preuves locales ignorées, conservées avant modification de production, sont :

- `.tmp/lineups-ui-qualification/twelve-bench-reproduction-summary.json` ;
- `.tmp/lineups-ui-qualification/twelve-bench-visibility.json` ;
- `.tmp/lineups-ui-qualification/twelve-bench-reproduction.xml` ;
- `.tmp/lineups-ui-qualification/LiveCampaignLineupsBrowserQualificationIT-reproduction.java` ;
- `.tmp/wo058-twelve-bench-diagnostic-tests.log`.

Empreintes SHA-256 avant correction :

| Élément | SHA-256 |
|---|---|
| `lineups.js` | `3e6359c8b78073f3afa6d8b87e26a94a74d710b900b87ed09348f25c37c0ad0b` |
| Source du diagnostic conservée | `48d0a2a195e4ce3963aad8d43b99ff9302ff6425167719b01a0bc0f14dea1190` |
| Relevés de visibilité | `2ed37a921a84b518f067a3e59d38aef6b6af8d9a5aab25f24dcc89a0ab2d7a2f` |
| Journal du diagnostic rouge | `2e776016a23513c3f7d0d30fea04a5eded153766fdbb01410fd6255ba065d221` |

Deux essais de montage du diagnostic ne constituent pas une preuve de
régression : l’un attendait un script interdit par la CSP manuelle, l’autre comptait
ces messages CSP attendus parmi les échecs. Ils sont distingués du diagnostic rouge
caractérisé ci-dessus. Aucun de ces essais n’a touché la campagne opérateur.

## Vérification après correction

Environnement : Windows, Java 25.0.4, Spring Boot 4.1.0, Playwright Java 1.62.0,
Chromium Headless Shell 151.0.7922.34 du cache local explicitement sélectionné.
La qualification dédiée est exécutée
avec le wrapper Maven hors ligne ; la suite standard complète reste à la charge de la
validation finale du lot et n’est pas lancée pendant la campagne opérateur.

Commandes de compilation et de qualification exécutées pour ce correctif :

```powershell
$env:PLAYWRIGHT_BROWSERS_PATH = (Resolve-Path .tmp/provider-playwright-browsers).Path
$env:PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD = '1'
.\mvnw.cmd --offline -Dmaven.repo.local=C:/Users/geoff/.m2/repository `
  -Pprovider-playwright-runtime,provider-playwright-local-qualification -DskipTests package
.\mvnw.cmd --offline -Dmaven.repo.local=C:/Users/geoff/.m2/repository `
  -Pprovider-playwright-runtime,provider-playwright-local-qualification `
  -DskipTests=false -DskipITs=false -Dit.test=LiveCampaignLineupsBrowserQualificationIT `
  "-Dprovider.playwright.browser-cache=$env:PLAYWRIGHT_BROWSERS_PATH" `
  failsafe:integration-test@provider-playwright-loopback-qualification `
  failsafe:verify@provider-playwright-loopback-qualification
```

Résultats après correction, lus dans les journaux et le XML Failsafe :

| Contrôle | Résultat |
|---|---|
| Compilation dédiée avec `-DskipTests package` | `BUILD SUCCESS`, 19,097 s, fin 16:26:22 UTC ; aucune exécution de test revendiquée pour cette commande |
| Classe Chromium complète, deux méthodes | `BUILD SUCCESS`, 31,468 s, fin 16:26:56 UTC ; 2 tests, 0 échec, 0 erreur, 0 ignoré |
| Scénario antérieur | 20,012 s ; rendu SSR live/manuel, sans JavaScript, actualisations live, panneaux, focus, échappement, listes vides et mobile conservés |
| Nouvelle régression | 2,427 s ; les cartes restent visibles après les réordres, changements de rôle/poste, retrait puis nouvelle lecture identique |
| Syntaxe JavaScript | `node --check src/main/resources/static/js/lineups.js` passé |
| Diff | `git diff --check` passé |

Le XML et ses métadonnées sont conservés dans
`.tmp/lineups-ui-qualification/lineups-visibility-green.xml` et
`.tmp/lineups-ui-qualification/lineups-visibility-green-summary.json`, indépendamment
d’un prochain nettoyage de `target`. Le temps total de la classe indiqué par Failsafe
est de 27,999 s, incluant son initialisation Spring. La capture synthétique
`lineups-twelve-bench-reordered-mobile.png` présente les douze remplaçants après réordre.

Empreintes SHA-256 après correction :

| Élément | SHA-256 |
|---|---|
| `lineups.js` | `383da48f3ab44a460de5f3cf1d7b21056ab67ec895754357743640dd480006aa` |
| Classe de qualification | `944e7e85430ee1f844bd2cae07072fac1c8b7c0eabaa4ed569d08c7bd4d12c10` |
| Journal de compilation dédié | `2e74fd5edd312c0b4e3a8efd5dd7dfe9395a2d92bd3fd0b2f622315ea7e2ffa7` |
| Journal Chromium vert | `bebce586e44b2358bcf6c48b02d66f17bcabd318d59660bc7c93ac07138fae64` |
| XML Chromium vert conservé | `3c2c83d435e818b5609b4885c4c8303b8219f39438866d567e4fba05b06022b7` |

Ces résultats qualifient le correctif d’affichage dans les scénarios synthétiques
décrits. Ils ne constituent ni une collecte réelle, ni une mesure de fraîcheur
fournisseur, ni la validation standard complète du lot WO-058 en cours.

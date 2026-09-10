# WO-058 — Pays historiques des compositions et métriques individuelles

Statuts : `EXPERIMENTAL`, `LOCAL_ONLY`, `NOT_PRODUCTION_APPROVED`,
`NO_CRITICAL_DEPENDENCY`.

## Objet

Ce complément corrige deux écarts de présentation signalés dans les compositions :

- les cartes ne doivent pas afficher « Pays non renseigné » quand la donnée n'est pas
  présente dans l'observation normalisée ;
- six clés de statistiques individuelles observées doivent recevoir un libellé français et
  une rubrique métier.

Il ne lance aucun transport, ne modifie aucune campagne, ne démarre pas Playwright et ne
change ni le lanceur Eclipse ni la configuration active de l'opérateur.

## Pays des joueurs

Les observations `event-lineups-v4` portent déjà `player.country` dans la donnée normalisée.
Cette donnée garde toujours la priorité.

Certaines observations historiques `event-lineups-v3` ont été normalisées avant que le pays
ne soit projeté, alors que leur snapshot brut local contenait déjà cette information. Le
résolveur `HistoricalLineupCountryPresentationResolver` peut compléter **uniquement la vue**
avec ce pays historique. Il ne réécrit pas l'observation V3, son hash, son parseur, sa
provenance ni le snapshot.

Avant toute lecture de pays, il contrôle que :

- l'observation concerne `EVENT_LINEUPS`, provient d'un `PROVIDER_SNAPSHOT` et porte exactement
  le parseur `event-lineups-v3` ;
- le snapshot référencé est présent localement, parsé, HTTP 200, du même endpoint, de la même
  clé d'événement, de la même taille et du même SHA-256 ;
- le JSON est strict, sans clé dupliquée ni contenu résiduel, et ne comporte aucun motif
  sensible ;
- l'identifiant du joueur, son équipe et le type de ligne (joueur inscrit ou indisponible)
  correspondent à une ligne déjà présente dans l'observation normalisée.

Une divergence, un snapshot absent/purgé, un JSON invalide, une donnée pays invalide ou une
ambiguïté produit un overlay vide. La page masque alors le champ pays au lieu d'inventer une
valeur. Les drapeaux restent les SVG locaux versionnés, jamais une image chargée auprès du
fournisseur.

## Métriques rendues lisibles

| Clé fournisseur conservée | Rubrique | Libellé affiché |
| --- | --- | --- |
| `accurateKeeperSweeper` | Gardien | Sorties du gardien (réussies) |
| `totalKeeperSweeper` | Gardien | Sorties du gardien (total) |
| `hitWoodwork` | Attaque | Tir sur un montant (poteau ou barre transversale) |
| `errorLeadToAShot` | Défense | Erreur menant à un tir |
| `errorLeadToAGoal` | Défense | Erreur provoquant un but |
| `clearanceOffLine` | Défense | Sauvetages sur la ligne |

Les clés et valeurs brutes restent inchangées dans `PlayerMatchStatistics`. La traduction
intervient uniquement dans `PlayerStatisticsPresentation`.

Le complément du 10 septembre ajoute exactement `clearanceOffLine` à cette projection. Cette clé
reste elle-même inchangée dans `PlayerMatchStatistics` ; aucune statistique absente n'est créée et
la valeur source n'est pas arrondie ni modifiée hors de la présentation.

## Qualification hors fournisseur

Les contrôles ci-dessous restent locaux. Ils n’ouvrent aucun transport vers SofaScore, ne
modifient aucune campagne ni observation existante, et ne démarrent pas le lanceur Eclipse.

1. Syntaxe du rafraîchissement de compositions :

       node --check src\main\resources\static\js\lineups.js

   Résultat : succès.

2. Régression ciblée V7, pays et statistiques :

       .\mvnw.cmd --offline "-Dmaven.repo.local=C:\Users\geoff\.m2\repository" -q "-Dtest=GroupedLiveAdmissionPolicyV7EvidenceTest,GroupedLiveAdmissionPolicyV7Test,LiveCampaignPropertiesTest,CountryAssetsTest,LineupsPresentationTest,PlayerStatisticsPresentationTest" test

   Résultat : succès.

3. Régression de l’overlay V3 et de ses appels web :

       .\mvnw.cmd -q "-Dtest=HistoricalLineupCountryPresentationResolverTest,LineupsPresentationTest,LiveCampaignPresentationTest,J5EventDataControllerTest,LiveCampaignControllerTest" test

   Résultat : 205 tests, zéro échec et zéro erreur.

4. Régression finale de la projection `clearanceOffLine`, de sa vue campagne et de la vue J5,
   exécutée avec un dépôt Maven et des réglages locaux isolés :

       .\mvnw.cmd "-Dmaven.repo.local=<cache Maven local isolé>" --settings <settings Maven locaux isolés> -q "-Dtest=PlayerStatisticsPresentationTest,LiveCampaignPresentationTest,J5EventDataControllerTest" test

   Résultat : 79 tests, zéro échec, zéro erreur et zéro test ignoré. Aucun transport fournisseur
   n’est lancé par cette suite standard.

5. Qualification Chromium loopback de la vue compositions, avec cache navigateur local
   explicitement fourni :

       .\mvnw.cmd --offline "-Dmaven.repo.local=C:\Users\geoff\.m2\repository" "-Pprovider-playwright-runtime,provider-playwright-local-qualification" "-DskipTests=false" "-DskipITs=false" "-Dit.test=LiveCampaignLineupsBrowserQualificationIT" "-Dprovider.playwright.browser-cache=<cache-local>" "failsafe:integration-test@provider-playwright-loopback-qualification" "failsafe:verify@provider-playwright-loopback-qualification"

   Résultat : 2 tests, zéro échec, zéro erreur, zéro skip ; les deux scénarios rapportent
   REAL_PROVIDER_CALLS=0, HTTP_POSTS=0 et DATABASE_USED=false.

6. Vérification Maven complète :

       .\mvnw.cmd --offline "-Dmaven.repo.local=C:\Users\geoff\.m2\repository" clean verify

   Résultat : BUILD SUCCESS en 9 min 25 s ; la phase Failsafe compte 219 tests, zéro échec
   et zéro erreur.

Aucun appel vers SofaScore, cookie, jeton, payload brut ou donnée de session n’est requis par
ces contrôles.

## Reprise du 10 septembre — `clearanceOffLine`

La régression finale de la projection, de la vue campagne et de la vue J5 compte **79 tests**,
zéro échec, zéro erreur et zéro test ignoré. Elle confirme la métrique affichée
`Sauvetages sur la ligne` dans `Défense`, ainsi que la conservation de la clé
`clearanceOffLine` et de sa valeur source.

La vérification complète `clean verify`, relancée avec le même cache Maven et les mêmes réglages
locaux isolés, exécute 2 213 tests puis s'arrête avec deux échecs qui sont distincts de cette
projection :

- `LiveOrphanProcessProbeTest.currentJavaIdentityMatchesItsRealCimCreationDate` reçoit
  `UNVERIFIED` au lieu de `ABSENT` pour l'identité du processus Java courant ;
- `J6NativeBinaryPipelineQualificationTest.syntheticNativePipelineFailsClosedWithoutHumanPassphraseInput`
  ferme en échec car les sondes Windows rapportent `CIM_ERROR,TASKLIST_ERROR,CLASS_UNVERIFIABLE`.

Ces contrôles d'identité de processus sont hors du périmètre de la statistique et conservent leur
comportement fail-closed. Ils n'ont pas été contournés, modifiés ou désactivés.

# WO-060 — menu J3 → J5 : tri, amateurs et noms français

Date : 14 septembre 2026. Base : `ce0cc25b266f3132dacfa54ee6d47cc605380ef2`.
Statuts : `EXPERIMENTAL / LOCAL_ONLY / NOT_PRODUCTION_APPROVED / NO_CRITICAL_DEPENDENCY`.

## Comportement

- Priorités strictement positives d'abord, par valeur croissante puis nom affiché ;
  priorités zéro ensuite par nom. Une priorité absente, négative ou non entière valide
  est traitée comme zéro. Les égalités utilisent le nom du tournoi puis son identifiant.
- Collation française insensible à la casse et aux accents. Une traduction fournisseur
  française valide est utilisée si disponible. Sinon les noms anglais ISO sont traduits
  localement avec Java 25, complétés par les associations britanniques et des alias explicites.
  Les catégories inconnues conservent leur nom source. Aucune traduction réseau.
- Case « Afficher les compétitions amateurs » décochée par défaut. Le GET du bouton
  « Appliquer le filtre — lecture locale » conserve la date et recharge le menu.
- Le mot source `Amateur` est recherché sans distinction de casse dans
  `tournament.category.name` et `tournament.uniqueTournament.category.name`, avant traduction.
  Le suffixe du pays reconnu peut être affiché comme `Argentine Amateur`.
- Le compteur du catalogue complet ne change pas. Une liste filtrée vide permet toujours
  de modifier le filtre. Les valeurs postées restent les identifiants de phase canoniques.

## Provenance et périmètre

Le catalogue durable ne stockait pas les métadonnées nécessaires. La présentation les relit
dans ses pages sources conservées, après contrôle de taille et SHA-256, sans mutation du
catalogue. Les succès déjà enregistrés fonctionnent sans recollecte ni migration.
Une source absente, corrompue ou illisible empêche d'exposer le menu comme valide.
Les tests utilisent seulement des structures synthétiques. Aucun appel réel, changement
d'endpoint, migration ou redémarrage du Lab opérateur.

Le retour « tests de concurrence concluants » est déclaré par l'opérateur ; ce complément
ne prétend pas avoir réexécuté sa campagne fournisseur.

## Validation

- Première sélection ciblée : 39 tests, zéro échec/erreur/ignoré.
- Première validation complète : échec d'un nouveau test qui utilisait XPath/XML pour du
  HTML. Corrigé en vérifiant le contrôle HTML rendu ; cette exécution n'est pas une preuve verte.
- Nouvelle sélection `J3TournamentMenuTest,DashboardControllerTest` : 21 tests, zéro
  échec/erreur/ignoré, `BUILD SUCCESS` à `2026-09-14T18:04:02Z`.
- Validation complète finale : **BUILD SUCCESS**, 14 min 04 s, terminée à
  `2026-09-14T18:18:25Z`. Totaux recomptés dans les rapports XML : **2 429 tests unitaires**,
  zéro échec/erreur, cinq ignorés ; **283 tests d'intégration**, zéro échec/erreur/ignoré.
- `git diff --check` : PASS. Aucun changement de persistance : le profil d'intégration
  supplémentaire n'est pas requis ; Failsafe a déjà exécuté les 283 tests pendant `clean verify`.
- UTF-8 strict et neuf règles du scanner de secrets sur les fichiers du changement : PASS.
  `server.address=127.0.0.1` et `SOFASCORE_ENABLED:false` restent inchangés.

Commandes Java 25 via le wrapper :

```powershell
./mvnw.cmd '-Dtest=J3TournamentMenuTest,J3TournamentCatalogServiceTest,DashboardControllerTest' test
./mvnw.cmd '-Dtest=J3TournamentMenuTest,DashboardControllerTest' test
./mvnw.cmd clean verify
```

Journaux locaux ignorés par Git et empreintes SHA-256 :

| Fichier | SHA-256 |
|---|---|
| `.tmp/menu-form-targeted.log` | `5b2f54e94239a0c8aea8d4be6aa2dca75d513b087dfa906a4de6a9bf16d0f4b1` |
| `.tmp/menu-clean-verify-final.log` | `ca93c169a8cc05c5a576ff2bbc7b753cb3d74a281a4613c1dae893abd9e721e9` |

## Fichiers

- `application/network/J3CategoryLabels.java` : noms français locaux et repli.
- `application/network/J3TournamentMenu.java` : sources vérifiées, tri et filtre.
- `application/network/J3TournamentCatalogService.java` : accès au menu.
- `adapter/web/DashboardController.java` et `templates/dashboard.html` : formulaire GET.
- `J3TournamentMenuTest.java` et `DashboardControllerTest.java` : règles et rendu HTTP.
- Changelog, runbook J3, Work Order et présent rapport.

Les chemins Java sont sous `src/main/java/com/bettingproject/sofascorelocal/`, les tests sous
`src/test/java/com/bettingproject/sofascorelocal/`, le modèle sous `src/main/resources/`.

## Livraison

Le worktree HUMAN a été constaté propre à `ce0cc25`. Le patch
`.tmp/WO060-J3-TOURNAMENT-MENU.patch` contient les sept fichiers de code, modèle et tests.
`git apply --check` dans HUMAN et `git apply --reverse --check` dans WO-060 passent.
SHA-256 : `a25b0580be85c8a190fa16914bb230312b079f3aa4b5b2638d009dd71f4f8bd3`.
Les quatre documents de suivi restent dans le worktree WO-060.

```powershell
$correctif = 'C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo060-j3-automation-design/.tmp/WO060-J3-TOURNAMENT-MENU.patch'
git -C C:/Dev/BettingProject/human/betting-sofascore-local-lab apply --check $correctif
if ($LASTEXITCODE -ne 0) { throw 'Le patch ne correspond pas à cet état du worktree.' }
git -C C:/Dev/BettingProject/human/betting-sofascore-local-lab apply $correctif
if ($LASTEXITCODE -ne 0) { throw 'Application du patch interrompue.' }
```

Faire Refresh dans Eclipse, recompiler puis redémarrer le Lab à un moment compatible avec
les opérations en cours. Recharger la page ; aucune nouvelle collecte J3 n'est requise.
L'agent n'a modifié ni redémarré le worktree opérateur. Le WO reste actif avant fusion de sa PR.

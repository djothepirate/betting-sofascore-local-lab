# WO-060 — filtres immédiats et phases qualificatives

Date : 14 septembre 2026. Base : `a55d406`.
Statuts : `EXPERIMENTAL / LOCAL_ONLY / NOT_PRODUCTION_APPROVED / NO_CRITICAL_DEPENDENCY`.

## Demande et réalisation

Le bouton d'application est supprimé. Cocher ou décocher l'une des deux cases déclenche
immédiatement le GET local du formulaire de filtre, via un script externe de même origine.
La page est rechargée sur la section du menu ; la date et les deux booléens sont conservés.
Aucun appel fournisseur ni préparation de campagne ne sont déclenchés. Aucun polling.

La seconde case **Afficher les phases qualificatives** est décochée par défaut. La condition
est exactement le booléen JSON `tournament.qualificationOrPreliminary: true`. Champ absent,
`null`, `false` ou chaîne `"true"` n'identifient pas une phase qualificative. Une occurrence
qualificative parmi les pages conservées marque la phase. Les exclusions amateur et
qualification se cumulent : les deux cases doivent être cochées pour un tournoi des deux types.
Le tri, les traductions et la détection amateur du précédent complément sont conservés.

La CSP autorise `script-src 'self'` uniquement sur les routes du tableau de bord en plus des
routes déjà autorisées. Aucun `unsafe-inline`, `unsafe-eval` ou domaine tiers. Sans JavaScript,
les filtres ne sont pas interactifs et un message l'indique. Les contrôles serveur restent actifs.

## Validation

- `node --test scripts/qualification/tournament-menu-filters.test.cjs` : deux tests réussis,
  zéro échec. Exécution du script réel dans un contexte DOM simulé : aucun envoi initial,
  un envoi immédiat par changement de chacune des cases, absence du formulaire tolérée.
- `mvnw.cmd -Dtest=J3TournamentMenuTest,DashboardControllerTest,SecurityHeadersFilterTest test` :
  **67 tests**, zéro échec/erreur/ignoré, terminé à `2026-09-14T18:49:02Z`.
- `mvnw.cmd clean verify` : **BUILD SUCCESS**, 14 min 06 s, terminé à
  `2026-09-14T19:03:48Z`. Rapports XML recomptés : **2 430 tests unitaires**, zéro échec/erreur,
  cinq ignorés ; **283 tests d'intégration**, zéro échec/erreur/ignoré. Aucune persistance
  modifiée : pas de relance du profil supplémentaire, les intégrations ont tourné avec Failsafe.
- UTF-8 strict, neuf règles de secrets sur le changement, `git diff --check` : PASS.
- Aucun changement de persistance, migration, endpoint ou configuration fournisseur.

Java 25.0.4 et wrapper Maven du dépôt. Tests locaux et synthétiques ; aucune qualification
fournisseur. La preuve du clic est un test du script réel avec DOM simulé, complété par
le rendu MockMvc du formulaire et les contrôles CSP ; ce n'est pas une recette navigateur native.

Journaux locaux ignorés par Git :

| Fichier | SHA-256 |
|---|---|
| `.tmp/menu-click-targeted.log` | `b8f6adb5a3aba3b024b105c1f88202fb85ac6c0130b9a6b596e3fcaf73cf6cdf` |
| `.tmp/menu-click-clean-verify.log` | `35979abef88af9acee2ba178f13a44b09551dc29e41e042f6e33525b130a5431` |

## Fichiers modifiés

- `src/main/java/com/bettingproject/sofascorelocal/application/network/J3TournamentMenu.java`
- `src/main/java/com/bettingproject/sofascorelocal/application/network/J3TournamentCatalogService.java`
- `src/main/java/com/bettingproject/sofascorelocal/adapter/web/DashboardController.java`
- `src/main/java/com/bettingproject/sofascorelocal/config/SecurityHeadersFilter.java`
- `src/main/resources/templates/dashboard.html`
- `src/main/resources/static/js/tournament-menu-filters.js`
- `src/test/java/com/bettingproject/sofascorelocal/application/network/J3TournamentMenuTest.java`
- `src/test/java/com/bettingproject/sofascorelocal/adapter/web/DashboardControllerTest.java`
- `src/test/java/com/bettingproject/sofascorelocal/config/SecurityHeadersFilterTest.java`
- `scripts/qualification/tournament-menu-filters.test.cjs`
- `CHANGELOG.md`, runbook J3, Work Order et présent rapport.

## Livraison Eclipse

HUMAN constaté propre à `a55d406`. Le patch contient les dix fichiers de code, tests et script.
Les documents de suivi restent dans WO-060. Vérifications d'application dans HUMAN et inverse
dans WO-060 réussies, sans modifier HUMAN.

Patch : `.tmp/WO060-J3-INSTANT-FILTERS.patch`.
SHA-256 : `33e815aef5fe15dcb19944a54c4691ed4e07e662c18e8639bafcdd9795ea701b`.

```powershell
$correctif = 'C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo060-j3-automation-design/.tmp/WO060-J3-INSTANT-FILTERS.patch'
git -C C:/Dev/BettingProject/human/betting-sofascore-local-lab apply --check $correctif
if ($LASTEXITCODE -ne 0) { throw 'Le patch ne correspond pas à cet état du worktree.' }
git -C C:/Dev/BettingProject/human/betting-sofascore-local-lab apply $correctif
if ($LASTEXITCODE -ne 0) { throw 'Application du patch interrompue.' }
```

Refresh Eclipse, recompilation puis redémarrage du Lab au moment approprié et rechargement
du tableau de bord. Aucune nouvelle collecte nécessaire. Aucun redémarrage ou modification
du worktree opérateur par l'agent. WO actif jusqu'à fusion de sa PR.

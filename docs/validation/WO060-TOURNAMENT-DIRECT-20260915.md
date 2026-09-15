# WO-060 — Découverte tournoi en un clic

Date : 15 septembre 2026, Europe/Paris.

Statuts : `EXPERIMENTAL`, `LOCAL_ONLY`, `NOT_PRODUCTION_APPROVED`, `NO_CRITICAL_DEPENDENCY`.

## Décision et comportement

La demande propriétaire retire la case d'acquittement et le texte à recopier du parcours
« Évolution J3 → J5 / Tournoi sélectionné ». Le formulaire propose directement la collecte
après sélection d'une phase, ou l'import après sélection de la phase et du fichier JSON.
La préparation intermédiaire disparaît aussi. Le choix de la phase et les filtres restent
sans transport ; le résultat revient à la date J3 consultée.

Les nouveaux POST `/tournament-event-discovery/collect` et `/tournament-event-discovery/import`
consomment le jeton local à usage unique, résolvent la collecte/date/phase côté serveur et
admettent une seule exécution sous verrou. Ils n'appellent pas les méthodes historiques de
préparation/confirmation et ne fabriquent ni phrase ni acquittement. Les anciens POST répondent
410 avec demande de rechargement. Les noms et identités fournisseur restent ceux du catalogue
canonique ; le tri, les traductions et les filtres de la liste sont conservés.

Le contrôleur est désormais couvert par la même frontière Host/Origin que J3 : un hôte
étranger, une origine étrangère/opaque ou un mélange localhost/127.0.0.1 est refusé avant
lecture du formulaire. Le claim est créé seulement après vérification de la qualification et
de la sélection. Le contrôleur ne termine que sa propre exécution en cas d'erreur inattendue.

La collecte conserve son cache admissible et au plus un GET. L'import n'utilise ni transport
ni cache fournisseur ; son corps est limité à 5 Mio et contrôlé avant admission. L'import
reste possible lorsque sa qualification locale est disponible mais le transport ne l'est pas.
Les erreurs, arrêts, incidents fournisseur et contraintes de nettoyage ne sont pas levés.
Aucune migration, nouveau fournisseur, modification d'allowlist ou automatisation n'est ajoutée.

## État source et environnement

- Base : `183f64f`, branche `feature/V0.1.0-RC01-CODEX-WO-SS-20260913-060`.
- Worktree : `C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo060-j3-automation-design`.
- Java 25.0.4, Maven Wrapper 3.9.16, Spring Boot 4.1.0, PostgreSQL Testcontainers 18.4.
- Copie HUMAN inspectée propre sur `183f64f` ; aucun fichier ni processus opérateur modifié.
- Aucun appel SofaScore exécuté ; la qualification utilise les mocks et les bases jetables du Lab.

## Qualification

| Commande / contrôle | Résultat |
|---|---|
| `mvnw.cmd "-Dtest=TournamentEventDiscoveryControllerTest,TournamentEventDiscoveryControlServiceTest,DashboardControllerTest" test` | PASS : 41 tests, zéro échec/erreur/skip, 33,875 s |
| `mvnw.cmd clean verify` | BUILD SUCCESS : 2 440 tests Surefire (5 skips), 283 tests Failsafe (0 skip), zéro échec/erreur ; 15 min 34 s ; terminé le 2026-09-14 à 22:56:59 UTC |
| `git diff --check` | PASS |
| UTF-8 strict et neuf règles de `ci/check-no-secrets.sh` sur les fichiers modifiés | PASS : 16 fichiers, liens Markdown vérifiés |
| `server.address` | Toujours `127.0.0.1` dans `application.yml` |
| `git apply --check` dans HUMAN à `183f64f` et contrôle inverse dans CODEX | PASS, sans application dans HUMAN |

Scénarios spécifiques : collecte et import sans phrase/case, double clic avec le vrai service
de jetons, concurrence A/B avec un seul propriétaire, collection/phase absentes, phase inconnue,
date incohérente, import vide/sensible/trop grand, indisponibilité du transport, verrous d'arrêt
et d'erreur, refus d'origines non conformes et anciens formulaires inertes. Le test Web avec
PostgreSQL vérifie que le formulaire rendu après un import J3 transmet l'identité et la date
de la collection durable, avec la bonne action et l'encodage multipart.

Une première vérification complète a été interrompue volontairement pour inclure le contrôle
d'origine identifié pendant la relecture. Elle n'est pas présentée comme une qualification réussie.
Le lancement complet suivant porte le code final. Aucun test navigateur natif ni essai fournisseur
n'est revendiqué pour ce complément ; les formulaires réels sont rendus et inspectés par MVC.

## Livraison locale

Patch de code et tests : `.tmp/WO060-TOURNAMENT-DIRECT.patch`, huit fichiers.
SHA-256 : `53a8fc704f1421098ac3ce4122ffb96d2068077547025a6443dd3fec11f18b5a`.
Le patch s'applique à la copie HUMAN sur `183f64f`. Après intégration et recompilation par
l'opérateur, relancer le Lab puis recharger le tableau de bord pour remplacer les anciens formulaires.

Fichiers applicatifs et tests :

- `src/main/java/com/bettingproject/sofascorelocal/adapter/web/TournamentEventDiscoveryController.java`
- `src/main/java/com/bettingproject/sofascorelocal/application/network/TournamentEventDiscoveryControlService.java`
- `src/main/java/com/bettingproject/sofascorelocal/config/LiveCampaignLocalRequestBoundaryInterceptor.java`
- `src/main/resources/templates/dashboard.html`
- `src/test/java/com/bettingproject/sofascorelocal/adapter/web/DashboardControllerTest.java`
- `src/test/java/com/bettingproject/sofascorelocal/adapter/web/TournamentEventDiscoveryControllerTest.java`
- `src/test/java/com/bettingproject/sofascorelocal/application/network/TournamentEventDiscoveryControlServiceTest.java`
- `src/test/java/com/bettingproject/sofascorelocal/integration/J3WebApplicationIT.java`

Documents :

- `ADR-SS-001-experimentation-endpoints-sofascore-depuis-windows.md` (v1.6)
- `ADR-SS-007-j3-automation-durable-catalog-and-live-pause.md` (v0.3)
- `CHANGELOG.md`
- `docs/architecture/J3-J5-TOURNAMENT-EVENT-DISCOVERY.md`
- `docs/requirements/J3-J5-TOURNAMENT-EVENT-DISCOVERY-RULES.md`
- `docs/runbooks/J3-AUTOMATION-AND-DURABLE-CATALOG.md`
- `docs/work_orders/active/WO-SS-20260913-060-j3-automation-and-durable-catalog.md`
- `docs/validation/WO060-TOURNAMENT-DIRECT-20260915.md`

Le WO demeure actif jusqu'à fusion de sa PR.

## Empreintes des journaux locaux

| Journal | SHA-256 |
|---|---|
| `.tmp/tournament-direct-targeted.log` | `30cf3eeadafae099f815922768296a3640ec6e4b70f469583440a36d2b135e50` |
| `.tmp/tournament-direct-clean-verify.log` | `3388cfd3c81c2665a142c74b00428fbf8a7f6e0dea6c13f9369b3e1d452fc864` |

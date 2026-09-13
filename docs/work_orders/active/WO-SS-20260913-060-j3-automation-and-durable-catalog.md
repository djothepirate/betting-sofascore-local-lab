# WO-SS-20260913-060 — Automatisation J3 et dernier catalogue durable par date

- **Statut :** `SCOPED — READY_FOR_OWNER_REVIEW` ; réalisation non commencée, clôture et livraison non effectuées.
- **Date de cadrage :** 2026-09-13.
- **Jalon :** J3, avec coordination des campagnes live J4/J5 et impacts de persistance/audit J6/J8.
- **Branche :** `feature/V0.1.0-RC01-CODEX-WO-SS-20260913-060`.
- **Train / cible de PR :** `feature/V0.1.0-RC01` exclusivement.
- **Base exacte :** `d4c3d8ceeb6c46442f0792d436a3df7e2630f599`, vérifiée sur GitHub le 13 septembre.
- **Worktree :** `.tmp/wo060-j3-automation-design` sous la racine de travail Codex du Lab.
- **Version Maven de base :** `0.1.0-rc.1-SNAPSHOT` ; Java 25 LTS, Spring Boot 4.1.0, Flyway V54.
- **Décision proposée :** [ADR-SS-007 v0.1](../../../ADR-SS-007-j3-automation-durable-catalog-and-live-pause.md), `PROPOSED`.
- **Autorité actuelle :** demande « Améliorations J3 », puis choix explicite « Work Order et décisions d’architecture » ; préparation documentaire seulement.
- **Statuts conservés :** `EXPERIMENTAL`, `LOCAL_ONLY`, `NOT_PRODUCTION_APPROVED`, `NO_CRITICAL_DEPENDENCY`.

## 1. Fiche de reprise

| Élément | État établi |
|---|---|
| Objectif | Collecter J3 directement ou selon une configuration locale persistante, retrouver le dernier succès de chaque date, suspendre/reprendre le live autour de J3 |
| Base | Train GitHub et worktree source propres au même SHA ; worktree WO distinct créé depuis ce train |
| Actuel | Preuve J3 en mémoire ; catalogue limité au dernier terminal du processus ; garde fournisseur détenu pendant toute la campagne live |
| Invariants | Origine/endpoint existants, pages 1..35, données et provenance séparées, un échange fournisseur à la fois, aucune donnée de session conservée |
| Prochaine étape après cadrage | Revue de l’ADR proposé, puis décision de réalisation et qualification hors fournisseur |
| Incertitudes à lever en réalisation | Reconstructibilité de chaque ancien succès, protocole de contexte J3 isolé et durée réelle de pause/reprise |

La [preuve de cadrage](../../validation/WO060-J3-AUTOMATION-SCOPING-20260913.md) distingue les
observations du dépôt, les contrôles réellement exécutés et les critères futurs ci-dessous.
L’ancienne preuve de WO-059 ne remplace pas une validation de cette nouvelle base ; WO-058
intégré a notamment corrigé la chaîne de qualification V5 historique.

## 2. Résultat fonctionnel attendu

1. Au démarrage d’un Lab configuré pour J3, le mode quotidien par défaut vise la date du jour. Il s’exécute aussi au changement de jour si le Lab reste en service.
2. L’automatisation est **activée dès la première mise en service**, puis son état et sa configuration survivent aux redémarrages.
3. Si un succès existe pour la date cible du jour, le contrôle de démarrage ne lance aucune nouvelle collecte. Fournisseur, cache et import local satisfont cette règle lorsqu’ils produisent un succès complet.
4. L’interface permet de choisir le mode quotidien à heure fixe et de gérer une ou plusieurs échéances ponctuelles avec leur date cible. **Les horaires explicitement programmés sont exécutés même après un succès de cette date**, une seule fois par occurrence.
5. Le bouton de désactivation bloque les prochains ordres automatiques. L’application arrêtée ne collecte pas et ne se redémarre pas pour une échéance.
6. La collecte manuelle ne demande que la date et le bouton A, ou la date et l’import B. Les étapes de levée d’arrêt, activation de circuit, préparation et confirmation J3 disparaissent.
7. Le dernier succès reste consultable pour chaque date, dans la liste déroulante et une vue paginée de cette collecte exacte, après redémarrage et après d’autres collectes. Un nouvel échec ne l’efface pas.
8. Un ordre réseau J3 admis suspend les nouveaux départs J4 et de toutes les familles J5. La campagne reprend après J3 et nettoyage, dans sa fenêtre et ses budgets, si son contexte et les conditions communes restent valides.

Les règles détaillées sur les jours civils, les occurrences, les erreurs, la rétention et les
contextes figurent dans l’ADR. Une heure fixe remplace le mode quotidien au démarrage ; les
planifications ponctuelles peuvent s’ajouter aux deux modes. La réussite est indexée par date
du calendrier demandé, pas par la seule date de réception.

## 3. Périmètre et limites

### 3.1 Réalisation visée après décision

- Configuration et ledger d’ordres locaux, avec idempotence, fuseau explicite, prévisualisation des échéances et état visible.
- Service J3 commun au manuel, au quotidien et aux horaires, sans dépendance aux anciennes confirmations d’intention.
- Import hors réseau conservant les validations de fichiers, pagination, parseur, hash et provenance.
- Résultats, preuves et projection de catalogue durables ; dernier succès par date ; sélection de tournoi attachée à la collecte exacte.
- Catalogue paginé et navigation multi-date ; distinction du dernier succès et de la dernière tentative.
- Reprise hors réseau de l’historique attesté, avec diagnostic des dates non récupérables.
- Arbitrage du droit d’émettre entre J3 et le live, nouvelle capacité versionnée du worker, transitions et reprise qualifiées.
- Migrations append-only, contraintes/index, intégration J6 de sauvegarde/rétention et audit J8 avec déclencheur exact.
- Adaptation documentaire ciblée d’AGENTS, des ADR-SS-001/005/006 et des runbooks au moment de l’adoption ; conservation de leurs preuves historiques.

### 3.2 Hors périmètre de la réalisation visée

- Nouvel endpoint, autre sport/origine, URL fournisseur libre, modification des quotas locaux pour accélérer J3.
- Collecte autonome J4/J5 hors campagne live lancée par l’opérateur, reprise live après redémarrage ou perte de contexte, navigateur persistant.
- Tâche Windows, tâche Codex récurrente, lancement automatique du Lab, VPS ou dépendance du Betting Project principal.
- Suppression des confirmations de découverte tournoi, J4, J5 ou J7 ; export/livraison J7 automatique.
- Purge primaire, migration primaire, sauvegarde native opérateur ou campagne fournisseur exécutée par ce cadrage.

### 3.3 Livrables autorisés maintenant

Le présent WO, l’ADR proposé, une preuve de cadrage et les liens d’orientation README,
CHANGELOG et architecture. Aucune migration SQL, classe Java, interface, propriété runtime
ou automatisation réelle n’est modifiée par la phase documentaire.

## 4. Découpage de réalisation proposé

Les étapes suivantes appartiennent à une réalisation future. Leur ordre résout d’abord la
durabilité, puis l’admission et enfin l’automatisation fournisseur complète. Aucun jalon
intermédiaire ne doit activer un horaire qui ne sait pas encore arbitrer une campagne live.

| Étape | Travaux | Preuve de sortie attendue |
|---|---|---|
| R0 — adoption et contrats | Relire les exceptions ADR/AGENTS, figer les sémantiques proposées, choisir les révisions de politique/protocole et le prochain schéma libre | Décision versionnée et traçable ; aucun ancien manifeste converti |
| R1 — résultat durable | Port/store J3, pages exactes, projection, dernier succès par date, reprise J8 démontrable, protection de rétention et preuves J6 | PostgreSQL vierge et upgrade V54 prérempli ; restart et A→B→A ; rollback laissant l’ancien succès |
| R2 — manuel et consultation | Autorité de clic direct, import, retrait des anciennes étapes, vue paginée, sélection liée au run/date | Recette A/B sans confirmations J3, doubles clics, deux onglets, échec après succès, pagination stable |
| R3 — moteur d’ordres | Préférences persistantes, démarrage, changement de jour, heure fixe, prochains ordres, désactivation, idempotence et horloge | Horloge contrôlée, courses SQL, erreurs et absence du Lab ; transport simulé uniquement |
| R4 — pause/reprise live | Coordinateur et garde, sous-opération J3, contexte isolé, protocole versionné, watchdog, groupes différés et reprise J4 | Worker Chromium en boucle locale, aucune concurrence réseau, aucun contexte live recréé, budgets et cas d’arrêt vérifiés |
| R5 — qualification et revue | Chaîne intégrée, mesures de durée de pause, documentation opérateur et réexécution proportionnée des contrôles | Rapport exact, limitations, diff sans secret, revue humaine avant fusion vers le train |

Le WO reste actif durant ces étapes. Une clôture ne devient effective qu’après fusion de sa
PR vers le train, conformément à AGENTS. Un push, une PR ou une fusion n’est pas effectué
par la préparation documentaire présente.

## 5. Points d’intégration constatés

| Zone du dépôt | Intervention attendue |
|---|---|
| `application/network/J3ManualCollectionEvidenceService`, `J3DynamicManualCallService`, `J3LocalJsonImportService` | Remplacer la dépendance au seul dernier document mémoire par un résultat durable, commun et transactionnel |
| `J3ManualCallControlService`, `J3ManualCallPolicy`, `J3ProviderQualificationPolicy` | Autorité de collecte directe/planifiée ; détacher les verrous opérateur supprimés des protections techniques et autres jalons |
| `J3TournamentCatalogService` | Lire une collecte exacte et son dernier succès par date ; garder intégrité et règles d’éligibilité |
| `adapter/web/ManualCallController`, `DashboardController`, `templates/dashboard.html` | Parcours A/B direct, configuration, sélection de date, état des ordres et nouvelle vue paginée |
| `TournamentEventDiscoveryControlService` et contrôleur associé | Transporter/revalider `collectionId`, date et tournoi ; aucune dépendance au dernier résultat d’un autre onglet |
| `ManualProviderRequestCoordinator`, `LiveCampaignService`, `LiveSchedule`, `LiveProviderSession` | Pause de toutes les familles, même propriétaire, reprise J4 et budgets/échéance maintenus |
| `ChildJvmPlaywrightProviderSupervisor`, protocoles et sources `src/provider-playwright` | Capacité de contexte J3 isolé, sous-autorité bornée, interdiction de dispatch du contexte en pause, nettoyage prouvé |
| Ports/stores de garde, résilience, J8 et migrations à partir de V54 | Occurrences, runs, succès, pages, projection, transitions et claims cohérents |
| `JdbcJ6RawPayloadRetentionStore`, scripts/runbook J6 | Protection des derniers succès et empreintes de sauvegarde/restauration étendues sur cible isolée |

Les nouveaux noms de services/tables restent des propositions de conception de l’ADR. Les
scripts de schéma courant devront être réévalués au moment du développement si un autre WO a
avancé le train ; aucune migration existante ne sera réécrite.

## 6. Matrice de recette à réaliser

**Aucun cas ci-dessous n’est qualifié par le seul cadrage.** Les essais standard et PostgreSQL
utilisent des fixtures ou transports synthétiques ; Chromium de qualification contacte
seulement une origine loopback. Aucun essai fournisseur n’est impliqué.

| ID | Situation | Résultat observable attendu | Preuve prévue |
|---|---|---|---|
| AC01 | Première mise en service, Lab J3 correctement configuré, aucun succès aujourd’hui | Activé, date du jour, une collecte après disponibilité complète ; aucun geste préparatoire | Application + horloge/transport simulés |
| AC02 | Manuel A réussi pour D, redémarrage le même jour D | Aucun automatique opportuniste ; catalogue D restauré | PostgreSQL + deux contextes applicatifs |
| AC03 | Import B réussi pour D puis redémarrage D | Même absence de doublon et même liste ; zéro transport/cache durant l’import | Import + PostgreSQL |
| AC04 | Succès quotidien entièrement cache ou mixte puis redémarrage | Même règle ; source de chaque page conservée | Service + PostgreSQL |
| AC05 | Succès D, succès D+1, redémarrage, retour à D | Dernier succès exact de chaque date ; aucune collecte pour consulter | MVC + persistance |
| AC06 | Succès D puis tentative D échouée/annulée/non terminale | L’ancien succès et son menu restent présents ; nouvelle tentative visible séparément | Service + rollback SQL |
| AC07 | Deux succès pour D ; pagination ouverte sur le premier | Menu par défaut sur le second ; pages ouvertes cohérentes avec le premier et lien explicite vers le nouveau | MVC + transactions |
| AC08 | Aucune occurrence de tournoi éligible ou catalogue vide valide | Succès vide explicite ; pas de faux échec et pas de relance au démarrage | Parseur/service/MVC |
| AC09 | Option A manuelle | Date + un clic ; aucune levée d’arrêt, activation de circuit, phrase ni case d’intention | MVC + navigateur loopback |
| AC10 | Option B, pages contiguës 1..N | Date + fichiers + un clic ; mêmes contrôles, dernier succès durable, zéro réseau/cache | Import + MVC |
| AC11 | 27/35 pages terminales ; page 35 avec suite ; 36 fichiers ; trous/doublons/sensible | 27/35 admises selon terminal ; autres refusées sans publication partielle ni page 36 | Tests de frontière existants adaptés |
| AC12 | Succès D puis horaire explicite H pour D | H exécuté une seule fois ; un succès à H remplace le pointeur D | Ordres + PostgreSQL |
| AC13 | Plusieurs horaires/dates, ordre modifié ou annulé | Identités distinctes, ordre stable, ancienne révision annulée ; pas de mauvais ciblage | Horloge contrôlée + SQL |
| AC14 | Mode quotidien à H, démarrage avant H ou après H manqué | Avant H : attendre ; après : occurrence manquée sans rattrapage ; mode démarrage non ajouté | Ordonnanceur simulé |
| AC15 | Désactivation puis redémarrage ; échéance passée hors service | Désactivation conservée, zéro départ ; occurrences manquées expliquées | Persistance + cycle de vie |
| AC16 | Désactivation pendant une collecte automatique | Échange courant borné puis arrêt des pages suivantes ; dernier succès intact, reprise live conditionnelle | Machine d’états + transport local |
| AC17 | Deux ticks/instances/doubles clics et course entre succès manuel et quotidien | Un claim par occurrence ; pas de doublon ; succès revérifié avant admission quotidienne | PostgreSQL concurrent, tests MVC |
| AC18 | Échec quotidien puis redémarrages ; crash entre claim et publication | Pas de retry implicite selon proposition ADR ; terminal/interruption traçable ; ancien succès intact | Injection de panne SQL/processus |
| AC19 | Minuit, heure d’été/hiver, recul d’horloge | Date cible figée ; occurrence unique ; heure ambiguë/inexistante traitée comme affiché ; aucun replay consommé | Horloge/fuseau contrôlés |
| AC20 | J3 dû pendant J4 puis pendant chacune des trois familles J5 | Dès demande de pause, aucun nouveau départ live ; échange en vol termine avant J3 | Worker loopback + ledger horodaté |
| AC21 | J3 terminé normalement durant un live | Fermeture contexte J3, même contexte live, J4 de revalidation puis cadence normale ; pas de rafale | Chromium local + PostgreSQL |
| AC22 | Budget partagé consommé, pause longue, borne J3 ou fin live atteinte | Départs plafonnés ; slots manqués explicites ; aucun budget ni durée live réinitialisé | Replay temporel et test de durée |
| AC23 | Erreur J3 métier, refus 403/429, fin incertaine, perte de contexte ou veille | Reprise seulement pour le terminal local sûr ; arrêt global approprié pour les autres ; aucun navigateur live recréé | Scénarios worker locaux |
| AC24 | Arrêt d’une rencontre ou de la campagne pendant la pause | Aucun élément arrêté repris ; arrêt global couvre aussi la sous-opération J3 | Concurrence arrêt/reprise |
| AC25 | Import J3 pendant un live | Publication locale possible ; aucune pause/requête fournisseur due à l’import | Service + coordination |
| AC26 | Anciennes campagnes J8 complètes, sources cache/import, borne 25/35, preuve manquante | Reconstruction exacte/idempotente ; succès prouvé sans contenu signalé et comptant pour éviter un doublon quotidien ; aucun succès inventé sans preuve terminale | Upgrade prérempli V54 vers nouveau schéma |
| AC27 | Publication concurrente avec rétention, puis sauvegarde/restauration isolée | Derniers succès protégés ; projections, preuves, préférences et occurrences conservées ; zéro horaire exécuté sur la cible de test | PostgreSQL, J6 isolé |
| AC28 | Mauvaise origine Web, mauvaise date/run/tournoi, ancien formulaire ou lien de pagination | Mutation refusée ; consultation bornée ; aucune collecte déclenchée par GET | MVC et sécurité locale |
| AC29 | Profils standards, fournisseur désactivé ou config invalide | Aucun Playwright démarré automatiquement dans les tests ; motif lisible en local non configuré ; J0/J1 inchangés | Configuration et suites standard |
| AC30 | Migration neuve/préremplie et perte de réponse de commit | Contraintes utiles actives ; relecture idempotente sans nouvel appel ; pas de pointeur incomplet | Testcontainers PostgreSQL |

### Recette opérateur manuelle cible

1. Démarrer le Lab et attendre sa disponibilité, puis ouvrir `http://127.0.0.1:8087` ou `http://localhost:8087`.
2. Pour A : saisir une date ou garder le jour courant, cliquer **A. Lancer la collecte paginée — APPELS FOURNISSEUR**. Pour B : choisir la date, fournir les pages contiguës puis cliquer **5B. Importer et valider J3 — ZÉRO APPEL**.
3. À la réussite, retrouver les tournois dans la liste déroulante et ouvrir la vue paginée de cette même collecte.
4. Redémarrer, retrouver cette date, effectuer une collecte pour une autre date, puis revenir à la première : la même dernière réussite doit rester accessible.
5. Constater qu’un nouveau démarrage le même jour ne relance pas la collecte opportuniste déjà réussie ; un horaire explicitement enregistré reste exécuté.

Pour isoler le seul parcours manuel pendant sa recette, l’opérateur peut désactiver le moteur
automatique. Ce réglage de test ne réintroduit aucune étape de confirmation dans le parcours A/B.

## 7. Validation et définition de fini

Pour le cadrage présent : relier chaque décision à la demande ou à une proposition explicite,
vérifier chemins/UTF-8/cohérence/secrets, exécuter la vérification Maven requise par AGENTS et
consigner son résultat réel dans la preuve. Aucune qualification de la future fonctionnalité
n’est déduite d’un build de la base documentaire.

Pour la réalisation future :

1. `mvnw.cmd clean verify`, puis `mvnw.cmd -Pintegration-tests verify` puisque persistance, migrations et concurrence changent ; contrôler les exécutions effectives Surefire/Failsafe.
2. Tests ciblés d’ordres et d’horloge, PostgreSQL neuf et upgrade prérempli, rollback et collisions ; aucune substitution H2.
3. Profil worker et qualification Chromium locale explicites, séparés des tests standards ; preuve réelle de pause/reprise et de non-concurrence, sans fournisseur.
4. Recette d’interface locale et J6 isolé couvrant les tables nouvelles, sans migration/purge de la base opérateur pour obtenir une preuve verte.
5. Vérification de `server.address=127.0.0.1`, profils bloqués et absence d’URI/transport/endpoints non autorisés ou de secrets dans le diff.
6. WO, ADR adoptés, architecture, runbooks, changelog et rapport final alignés sur le code réellement livré ; fichiers modifiés et commandes/résultats listés.
7. Revue humaine, réexécution des tests pertinents et fusion de la PR dans le train avant clôture effective. Toute campagne fournisseur reste une opération opérateur distincte.

## 8. Décisions encore proposées et limites du cadrage

L’activation initiale et le maintien des horaires explicites après un succès ont déjà été
arbitrés par le propriétaire ; ils ne sont pas à redemander. Le périmètre actuel se termine
avec des documents prêts à relire, conformément à son choix de ne pas lancer la réalisation.

L’ADR propose concrètement : une tentative opportuniste unique par jour même après échec,
pas de rattrapage des horaires hors service, une désactivation arrêtant les pages automatiques
suivantes, les règles de changement d’heure, une borne J3 de 20 minutes à qualifier et un
contexte J3 neuf isolé dans le runtime live. Ces choix techniques restent soumis à la revue
de la proposition v0.1 ; leurs tests ne sont pas exécutés par la phase documentaire.

L’inventaire historique sur la base concernée et la capacité temporelle du nouveau worker ne
sont pas encore établis. Un résultat ancien dont les preuves ont disparu ne peut pas être
reconstitué fidèlement par décret. Ces limites sont des critères de réalisation, pas une
raison de remplacer la date ou les sources demandées par d’autres données.

# WO-SS-20260913-060 — Automatisation J3 et dernier catalogue durable par date

- **Statut :** `IN_PROGRESS` — réalisation qualifiée localement et validée par l’opérateur le 15 septembre 2026 ; push et création de PR autorisés, clôture effective après fusion de la PR.
- **Date de cadrage :** 2026-09-13.
- **Jalon :** J3, avec coordination des campagnes live J4/J5 et impacts de persistance/audit J6/J8.
- **Branche :** `feature/V0.1.0-RC01-CODEX-WO-SS-20260913-060`.
- **Train / cible de PR :** `feature/V0.1.0-RC01` exclusivement.
- **Base exacte :** `d4c3d8ceeb6c46442f0792d436a3df7e2630f599`, vérifiée sur GitHub le 13 septembre et confirmée inchangée le 15 septembre avant publication.
- **Worktree :** `.tmp/wo060-j3-automation-design` sous la racine de travail Codex du Lab.
- **Version Maven de base :** `0.1.0-rc.1-SNAPSHOT` ; Java 25 LTS, Spring Boot 4.1.0, Flyway V54.
- **Décision de réalisation :** [ADR-SS-007 v0.3](../../../ADR-SS-007-j3-automation-durable-catalog-and-live-pause.md), `ACCEPTED_FOR_IMPLEMENTATION` ; réalisation initialement adoptée en v0.2, complément du clic tournoi en v0.3.
- **Autorité actuelle :** instruction propriétaire du 15 septembre 2026 : tous les tests sont concluants, les travaux du WO-060 peuvent être poussés et une PR vers `feature/V0.1.0-RC01` créée. Cette autorisation complète celle de réalisation après cadrage au commit `9e0d1cb` ; elle ne vaut pas autorisation de fusion.
- **Statuts conservés :** `EXPERIMENTAL`, `LOCAL_ONLY`, `NOT_PRODUCTION_APPROVED`, `NO_CRITICAL_DEPENDENCY`.

## 1. Fiche de reprise

**Ouverture de réalisation — 2026-09-13 :** le worktree est propre au commit `9e0d1cb`.
Les sections de cadrage ci-dessous sont le contrat de réalisation ; les anciennes mentions
de « réalisation future » ou de phase documentaire décrivent l’étape précédente. L’autorisation
actuelle couvre R0 à R5, sans autre confirmation des choix déjà présentés. Aucun ordre réel
ni migration de la base opérateur n’est lancé par l’agent pendant les tests.

**Réalisation au 14 septembre 2026 :** les étapes R0–R5 sont réalisées et qualifiées localement. V55 conserve les
collections, leurs pages exactes et le dernier succès par date ; V56 conserve les préférences
et ordres ; V57 ajoute live-v11 et ses transitions de pause. Le moteur commun alimente les
actions A/B directes, le suivi, la pagination et la sélection de tournoi liée à la collecte exacte.
Le démarrage et les horaires sont limités au serveur Web local ; les commandes J6 ne les lancent pas.

La qualification Chromium loopback prouve J4 → J3 → J4 dans le même worker, sans transfert de
cookies et avec nettoyage complet. Une façade V11 indépendante préserve les classes et empreintes
historiques V8/V9. J6 protège les derniers succès et compare les dix tables J3 lors d'une
restauration sur PostgreSQL jetable. Les résultats effectifs figurent dans le
[rapport de réalisation](../../validation/WO060-J3-IMPLEMENTATION-20260914.md).
La base opérateur et le fournisseur ne participent à aucun de ces essais.

**Retour opérateur du 14 septembre, après mise en service :** les captures montrent un succès
J3 quotidien de 19 pages, mais une sélection live à zéro. Le lanceur Eclipse LIVE conservait
les neuf paramètres V10 et aucun paramètre V11 ; sa capacité déclarée était déjà huit.
Les neuf paramètres V11 manquants sont ajoutés avec sauvegarde, sans modifier les autres valeurs.
La lecture isolée des dix paramètres par Spring et le calcul d'admission donnent huit rencontres.
Le message `/events` qui renvoyait encore à V10 est corrigé et son test suit la politique courante.
Les preuves et la validation du correctif figurent dans
l'[addendum de mise en service](../../validation/WO060-LIVE-V11-LAUNCHER-FIX-20260914.md).

**Retour opérateur sur les formulaires J3 — 14 septembre :** les POST de paramètres,
de planification, de collecte A et d'import B sont refusés en HTTP 403 dans Chrome.
Le tableau de bord envoyait `Referrer-Policy: no-referrer`, ce qui produit `Origin: null`
sur ces navigations. Le filtre ajoute `/` et `/dashboard` aux pages de formulaires locaux
utilisant `same-origin`, sans assouplir l'intercepteur ni les jetons. Le bouton « 5B » devient
« B » à la demande de l'opérateur. La reproduction Chromium et les tests MVC/PostgreSQL
sont décrits dans le [correctif des formulaires](../../validation/WO060-J3-FORMS-403-FIX-20260914.md).

**Retour opérateur sur une date incorrecte — 14 septembre à 13 h 31 :** la programmation
d'un déclenchement à 12 h 35 le même jour produit Whitelabel HTTP 500. Le scénario est reproduit
dans le contexte Spring complet sur PostgreSQL jetable : la traduction d'exception JPA
enveloppe le refus métier JDBC `J3_PLAN_MUST_BE_FUTURE`, qui échappait au contrôleur.
Les seuls refus métier connus de programmation sont reconnus ; la saisie des dates/heures
est validée après le jeton et le refus revient dans **Collecte automatique** avec une alerte.
Le formulaire conserve sa saisie et son identité de création/révision ; aucun ordre ne change
sur refus et une date historique de calendrier reste permise. Les preuves et la liste des
fichiers figurent dans le [correctif des dates](../../validation/WO060-J3-DATE-VALIDATION-FIX-20260914.md).

**Retour opérateur sur les années aberrantes — 14 septembre à 14 h 19 :** les captures
montrent une cible en 9999, un déclenchement en 9999 et un message de transport après une
annulation volontaire. Le propriétaire choisit explicitement **depuis 2000, avec un horizon
futur de 12 mois**. Les créations A/B et les créations/révisions d'horaires appliquent la même
borne de date civile Europe/Paris ; le déclenchement doit aussi rester futur en instant UTC.
Les bornes HTML et serveur sont cohérentes ; les lectures et anciennes lignes ne sont pas
réécrites. `OPERATOR_CANCELLED` et `PLAN_REVISED` reçoivent une explication précise.
La saisie native du 31 février et son envoi direct sont refusés dans les qualifications ;
la conversion en 29 février n'est pas reproduite. Les preuves figurent dans le
[correctif des bornes et motifs](../../validation/WO060-J3-DATE-RANGE-FIX-20260914.md).

La clôture J8 du nouveau parcours utilise une transaction obligatoire commune à la publication
du catalogue et de l’ordre. Les transactions autonomes des parcours historiques sont conservées.
Un test PostgreSQL injecte un échec après cette publication et vérifie l’annulation du succès
J8 et du nouveau catalogue, avec conservation du précédent succès.

| Élément | État établi |
|---|---|
| Objectif | Collecter J3 directement ou selon une configuration locale persistante, retrouver le dernier succès de chaque date, suspendre/reprendre le live autour de J3 |
| Base | Train GitHub et worktree source propres au même SHA ; worktree WO distinct créé depuis ce train |
| Réalisation | Dernier succès SQL par date ; ordres durables ; sous-opération J3 sous le même garde live |
| Invariants | Origine/endpoint existants, pages 1..35, données et provenance séparées, un échange fournisseur à la fois, aucune donnée de session conservée |
| Prochaine étape | Revue humaine du diff et des preuves ; PR vers le train avant clôture effective |
| Limites | Sources historiques manquantes signalées ; capacité fournisseur et recette opérateur non mesurées |

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
- Suppression des confirmations J4, J5 ou J7 ; export/livraison J7 automatique.
  La découverte tournoi, initialement exclue, est ajoutée par la décision propriétaire du §10.
- Purge primaire, migration primaire, sauvegarde native opérateur ou campagne fournisseur exécutée par ce cadrage.

### 3.3 Livrables réalisés

Code Java, migrations append-only V55–V57, contrôleurs et vues, protocole worker 10,
tests standard/PostgreSQL/Chromium local, scripts J6, lecteur de configuration V11,
ADR amendés, guide opérateur, changelog et rapport. Le cadrage initial est conservé dans Git ;
l'instruction de réalisation autorise ce périmètre.

## 4. Découpage de réalisation

Les étapes définissent les livrables et leur qualification. L'automatisation n'est raccordée
qu'au moteur commun capable d'arbitrer le live ; aucun jalon intermédiaire n'est livré à l'exploitation.

| Étape | Travaux | Preuve de sortie attendue |
|---|---|---|
| R0 — adoption et contrats | Relire les exceptions ADR/AGENTS, figer les sémantiques proposées, choisir les révisions de politique/protocole et le prochain schéma libre | Décision versionnée et traçable ; aucun ancien manifeste converti |
| R1 — résultat durable | Port/store J3, pages exactes, projection, dernier succès par date, reprise J8 démontrable, protection de rétention et preuves J6 | PostgreSQL vierge et upgrade V54 prérempli ; restart et A→B→A ; rollback laissant l’ancien succès |
| R2 — manuel et consultation | Autorité de clic direct, import, retrait des anciennes étapes, vue paginée, sélection liée au run/date | Recette A/B sans confirmations J3, doubles clics, deux onglets, échec après succès, pagination stable |
| R3 — moteur d’ordres | Préférences persistantes, démarrage, changement de jour, heure fixe, prochains ordres, désactivation, idempotence et horloge | Horloge contrôlée, courses SQL, erreurs et absence du Lab ; transport simulé uniquement |
| R4 — pause/reprise live | Coordinateur et garde, sous-opération J3, contexte isolé, protocole versionné, watchdog, groupes différés et reprise J4 | Worker Chromium en boucle locale, aucune concurrence réseau, aucun contexte live recréé, budgets et cas d’arrêt vérifiés |
| R5 — qualification et revue | Chaîne intégrée, mesures de durée de pause, documentation opérateur et réexécution proportionnée des contrôles | Rapport exact, limitations, diff sans secret, revue humaine avant fusion vers le train |

Le WO reste actif durant ces étapes. Une clôture ne devient effective qu’après fusion de sa
PR vers le train, conformément à AGENTS. Le push et la création de PR sont autorisés par
la décision du 15 septembre consignée en section 11 ; la fusion reste une étape distincte.

## 5. Points d’intégration constatés

| Zone du dépôt | Intervention réalisée |
|---|---|
| `application/network/J3ManualCollectionEvidenceService`, `J3DynamicManualCallService`, `J3LocalJsonImportService` | Remplacer la dépendance au seul dernier document mémoire par un résultat durable, commun et transactionnel |
| `J3ManualCallControlService`, `J3ManualCallPolicy`, `J3ProviderQualificationPolicy` | Autorité de collecte directe/planifiée ; détacher les verrous opérateur supprimés des protections techniques et autres jalons |
| `J3TournamentCatalogService` | Lire une collecte exacte et son dernier succès par date ; garder intégrité et règles d’éligibilité |
| `adapter/web/ManualCallController`, `DashboardController`, `templates/dashboard.html` | Parcours A/B direct, configuration, sélection de date, état des ordres et nouvelle vue paginée |
| `TournamentEventDiscoveryControlService` et contrôleur associé | Transporter/revalider `collectionId`, date et tournoi ; aucune dépendance au dernier résultat d’un autre onglet |
| `ManualProviderRequestCoordinator`, `LiveCampaignService`, `LiveSessionSchedule`, `LiveProviderSession` | Pause de toutes les familles, même propriétaire, reprise J4 et budgets/échéance maintenus |
| `ChildJvmPlaywrightProviderSupervisor`, protocoles et sources `src/provider-playwright` | Capacité de contexte J3 isolé, sous-autorité bornée, interdiction de dispatch du contexte en pause, nettoyage prouvé |
| Ports/stores de garde, résilience, J8 et migrations à partir de V54 | Occurrences, runs, succès, pages, projection, transitions et claims cohérents |
| `JdbcJ6RawPayloadRetentionStore`, scripts/runbook J6 | Protection des derniers succès et empreintes de sauvegarde/restauration étendues sur cible isolée |

Les noms effectifs et V55–V57 sont documentés dans l'architecture et le rapport.
Aucune migration V1–V54 n'est modifiée ; les politiques historiques restent conservées.

## 6. Matrice de recette et preuves

**Les preuves effectives sont référencées dans le rapport ; le cadrage seul ne qualifie aucun cas.** Les essais standard et PostgreSQL
utilisent des fixtures ou transports synthétiques ; Chromium de qualification contacte
seulement une origine loopback. Aucun essai fournisseur n’est impliqué.

| ID | Situation | Résultat observable attendu | Type de preuve |
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
2. Pour A : saisir une date ou garder le jour courant, cliquer **A. Lancer la collecte paginée — APPELS FOURNISSEUR**. Pour B : choisir la date, fournir les pages contiguës puis cliquer **B. Importer et valider J3 — ZÉRO APPEL**.
3. À la réussite, retrouver les tournois dans la liste déroulante et ouvrir la vue paginée de cette même collecte.
4. Redémarrer, retrouver cette date, effectuer une collecte pour une autre date, puis revenir à la première : la même dernière réussite doit rester accessible.
5. Constater qu’un nouveau démarrage le même jour ne relance pas la collecte opportuniste déjà réussie ; un horaire explicitement enregistré reste exécuté.

Pour isoler le seul parcours manuel pendant sa recette, l’opérateur peut désactiver le moteur
automatique. Ce réglage de test ne réintroduit aucune étape de confirmation dans le parcours A/B.

## 7. Validation et définition de fini

La preuve du cadrage reste historique. Ces contrôles portent sur la réalisation courante ;
les résultats, échecs intermédiaires résolus et limites figurent dans le rapport.

1. `mvnw.cmd clean verify`, puis `mvnw.cmd -Pintegration-tests verify` puisque persistance, migrations et concurrence changent ; contrôler les exécutions effectives Surefire/Failsafe.
2. Tests ciblés d’ordres et d’horloge, PostgreSQL neuf et upgrade prérempli, rollback et collisions ; aucune substitution H2.
3. Profil worker et qualification Chromium locale explicites, séparés des tests standards ; preuve réelle de pause/reprise et de non-concurrence, sans fournisseur.
4. Recette d’interface locale et J6 isolé couvrant les tables nouvelles, sans migration/purge de la base opérateur pour obtenir une preuve verte.
5. Vérification de `server.address=127.0.0.1`, profils bloqués et absence d’URI/transport/endpoints non autorisés ou de secrets dans le diff.
6. WO, ADR adoptés, architecture, runbooks, changelog et rapport final alignés sur le code réellement livré ; fichiers modifiés et commandes/résultats listés.
7. Revue humaine, réexécution des tests pertinents et fusion de la PR dans le train avant clôture effective. Toute campagne fournisseur reste une opération opérateur distincte.

## 8. Décisions adoptées et limites de qualification

L'activation initiale, les horaires explicites après succès et les règles techniques de l'ADR
v0.2 sont adoptés. Aucun arbitrage déjà donné n'est à redemander.

La tentative opportuniste est unique par jour même après échec ; les horaires hors service ne
sont pas rattrapés ; la désactivation arrête les pages suivantes. Les changements d'heure et la
borne de 20 minutes sont explicites. Le contexte J3 est neuf et isolé, puis fermé avant reprise
à partir du contexte live initial.

La qualification utilise des données synthétiques et des bases jetables. Elle ne mesure pas
les performances fournisseur et ne prouve pas la reconstructibilité de chaque ancien succès
de la base opérateur. Un succès attesté dont les sources ont disparu reste signalé indisponible.
Après perte d'un processus, une transition de pause non achevée conserve sa dernière phase
observée ; elle ne prouve pas un nettoyage et n'autorise aucune reprise. L'ordre est interrompu
et aucune campagne live n'est recréée.

Les profils et lanceurs opérateur ne sont pas modifiés. Le lecteur V11 affiche dix valeurs
selon le guide J3. La revue précède la fusion ; le WO reste actif jusqu'à la fusion de sa PR vers le train.

## 9. Complément propriétaire du 14 septembre : menu J3 → J5

L'opérateur déclare les tests de concurrence concluants. Ce constat est un retour opérateur,
distinct des qualifications automatisées du présent complément.

Demande adoptée : catégories de priorité positive en premier, priorité croissante puis nom ;
catégories de priorité zéro ensuite par nom. Le menu masque les amateurs par défaut et permet
de les afficher par case à cocher. La détection utilise le mot source `Amateur` dans les deux
catégories (tournoi et tournoi unique), indépendamment de la traduction.

La précision propriétaire sur l'absence habituelle de `fieldTranslations.nameTranslation.fr`
est prise en compte : un dictionnaire local traduit les pays internationaux reconnus. Les noms
inconnus sont conservés ; le tri utilise le libellé affiché avec collation française.
La vue relit les snapshots protégés pour appliquer aussi ces règles aux succès historiques,
sans migration, appel fournisseur ou modification de leurs identités. Le filtre s'applique
par GET local et conserve la date. La preuve est consignée dans
[le rapport du menu](../../validation/WO060-J3-TOURNAMENT-MENU-20260914.md).

Complément suivant : suppression du bouton d'application, filtres appliqués au clic par script
local et GET de lecture. Ajout d'une seconde case décochée par défaut pour les tournois dont
`tournament.qualificationOrPreliminary` est le booléen `true`. Les deux exclusions se cumulent.
La CSP du tableau de bord autorise les scripts de même origine, sans script inline ni eval.
Voir [la validation des filtres immédiats](../../validation/WO060-J3-INSTANT-FILTERS-20260914.md).

## 10. Complément propriétaire du 15 septembre : collecte tournoi en un clic

Demande explicite adoptée : comme J3, les collectes de « Évolution J3 → J5 / Tournoi sélectionné »
ne doivent plus exiger de case ni de phrase copiée. Le formulaire propose donc directement A
après choix du tournoi, ou B après choix du tournoi et du fichier. Les deux actions portent la
collecte J3 et sa date exactes. Le serveur ne remplit pas les anciennes confirmations en secret :
le clic protégé devient l'autorité et l'admission atomique résout la phase côté serveur.

Critères : aucun formulaire de préparation/phrase/acquittement ; un GET fournisseur maximum ou
un import sans transport/cache ; refus des doubles soumissions et des demandes concurrentes ;
identité/date invalides refusées ; fichier vide, sensible ou >5 Mio refusé avant admission ;
aucune levée des verrous d'erreur/arrêt. Les routes historiques répondent 410.
L'ADR-SS-001 v1.6 et l'ADR-SS-007 v0.3 consignent cette décision. Pas de migration, pas d'appel
réel ni de redémarrage du Lab opérateur pour cette réalisation.

Voir [la validation de la collecte directe](../../validation/WO060-TOURNAMENT-DIRECT-20260915.md).

## 11. Validation opérateur et autorisation de publication — 15 septembre 2026

Le propriétaire déclare « tous les tests sont concluants » et autorise explicitement le push
des travaux WO-060 ainsi que la création d’une PR vers `feature/V0.1.0-RC01`.
Le dernier changement fonctionnel est `692b2a29c3ff87e38ccd063dd8b440fbf826d0fb` ;
la copie HUMAN et le candidat CODEX pointent sur ce commit lors de la reprise de publication.
La mise à jour de livraison qui suit est documentaire uniquement.

La capture et le texte du tableau de bord fournis par l’opérateur montrent une collecte
LaLiga en un clic pour le 15 septembre : phase `36`, tournoi unique `8`, état `COMPLETED`,
un appel fournisseur HTTP 200, contrôle `COUNT_VERIFIED`, trois rencontres attendues et
retenues, trois observations ajoutées et aucune dédupliquée. Le snapshot local `17373` est
reçu le `2026-09-15T06:45:07.306Z`. Il s’agit d’une recette opérateur rapportée, distincte
des tests automatisés ; aucun nouvel appel fournisseur n’est exécuté pour publier le WO.

La [dernière qualification complète](../../validation/WO060-TOURNAMENT-DIRECT-20260915.md)
porte le code fonctionnel de `692b2a2` : `mvnw.cmd clean verify` terminé le
14 septembre à 22:56:59 UTC, avec 2 440 tests Surefire (5 exclusions) et 283 Failsafe
(aucune exclusion), zéro échec et zéro erreur. Les 41 tests ciblés sont également verts.
La [qualification initiale de persistance](../../validation/WO060-J3-IMPLEMENTATION-20260914.md)
consigne en plus `mvnw.cmd -Pintegration-tests verify`, les migrations V55–V57, le roundtrip
J6 sur PostgreSQL jetable et le worker Chromium en boucle locale. Ces rapports historiques
et leurs manifestes restent conservés, avec leurs périmètres et dates propres.

Après actualisation des références GitHub, le train cible reste à `d4c3d8ceeb6c46442f0792d436a3df7e2630f599`.
Les douze commits de réalisation sont descendants directs de cette base ; aucun commit du
train n’est absent du candidat. Aucune PR de cette branche n’existe avant la publication.
La PR couvre tout le WO, dont l’automatisation, la persistance, la pause live et les compléments
d’interface. Les checks GitHub doivent être évalués sur la tête distante de la PR ; les tests
locaux et l’acceptation opérateur ne préjugent pas de leur résultat. Le WO reste dans `active`
jusqu’à la fusion dans son train ; aucune fusion, promotion, publication de version ou
activation fournisseur supplémentaire n’est engagée par cette autorisation.

# WO-058 — compositions prématch, temporalité et incident Docker

Date : 7 septembre 2026. Statuts : `EXPERIMENTAL`, `LOCAL_ONLY`,
`NOT_PRODUCTION_APPROVED`, `NO_CRITICAL_DEPENDENCY`.

Base de cette reprise : `e98f7a74e39a1c57e601efb3d346ae55829fce73`, branche
`feature/V0.1.0-RC01-CODEX-WO-SS-20260907-058`, worktree `.tmp/wo058-live-j4-j5`.
La version opérateur exécutée depuis le checkout `human` est distincte de ce worktree.
Les observations fournisseur ne qualifient pas les changements source décrits ici.

## Décisions et changements

Le propriétaire a demandé LINEUPS avant le début pour les rencontres initialement `notstarted`
et a confirmé « Oui, collecte initiale puis périodique ». [ADR-SS-005 v0.3](../../ADR-SS-005-bounded-local-live-j4-j5-campaigns.md)
enregistre cette décision. Les nouveaux manifestes `live-v3` effectuent J4, puis LINEUPS seul
si J4 confirme `notstarted`, et répètent ces contrôles à D. Statistiques et incidents attendent
J4 `inprogress`. Aucun statut n'est déduit de l'heure prévue. Les anciens manifestes v1/v2,
leurs SHA et leurs cadences restent inchangés.

La première séquence J5 complète, ou finale, respecte D depuis une LINEUPS prématch récente.
Son éligibilité peut donc être repoussée jusqu'à D ; le retard de service s'ajoute. La séquence
ne monopolise pas le transport pendant cette attente. HTTP 404 reste une indisponibilité,
réinterrogée à l'échéance normale. Réserve finale, fenêtre, budgets, exclusion globale,
départs séquentiels et délai commun de trois secondes restent applicables.

V35 ajoute la contrainte de cadence v3 sans modifier les migrations précédentes. Les gardes
J6 sont alignés sur V36, ajoutée ensuite pour le parseur incidents V16 ; une sauvegarde préalable
V34 utilise les outils historiques du commit de base. Ces migrations n'ont pas été appliquées
à la base opérateur dans cette reprise.

Les vues J4 et live affichent la description de phase pour `inprogress`, avec repli sur le type.
Le type utilisé par les contrôles reste distinct du libellé. La description, le statut et
la provenance restent associés à la même observation ; le texte est échappé au rendu.

Une panne PostgreSQL peut empêcher l'enregistrement de l'arrêt. Le correctif conserve l'état
durable exact et expose séparément l'arrêt runtime et la clôture restant à réaliser. Le thread
propriétaire garde son lease après échec de clôture et attend une commande explicite, sans
timer SQL, nouveau GET ni réouverture Playwright. Les commandes concurrentes sont fusionnées.
La clôture revérifie le superviseur et l'ownership, conserve les réceptions existantes, annote
`UNKNOWN / LOCAL_CLEANUP_UNRESOLVED` les seules tentatives sans résultat, puis publie les états
terminaux et libère l'exclusion. Les caches de transitions ne sont actualisés qu'après succès SQL.
Avant cette première réconciliation, le même verrou SQL du garde sérialise la lecture avec
un éventuel commit de lancement en vol. Une ancienne vue PREPARED ne peut ainsi servir de
preuve d'absence de lancement. Les cas d'échec avant commit, de réponse perdue après commit,
de preuve contradictoire et d'échec de cette barrière sont distingués.
Le redémarrage continue de marquer les propriétaires absents et de conserver une exclusion
incertaine ; il ne fournit aucune reprise automatique.

## Preuve temporelle des deux premières campagnes

L'[extraction SQL](WO058-TEMPORAL-EXTRACT-20260907.sql) est une transaction cohérente en lecture
seule. Elle ne contient ni payload fournisseur, ni cookie, ni jeton. L'analyseur
[Python standardlib](../../scripts/Analyze-LiveTemporalEvidence.py) rejoue les résultats résolus
à chaque checkpoint et les transitions historiques, sans utiliser une observation future.

L'extraction contient **937 tentatives**, dont 181 dans la première campagne et 756 dans la
seconde. Capture : `2026-09-07T20:02:42.611690Z` (22:02:42.611690 Paris).
Fichier local ignoré `.tmp/wo058-temporal-ledger-20260907.json`, SHA-256
`838bab698d7d82a5176ac7be8a2dddea018987bb4856c1960222131e6cf49d80`.
Le [résumé de métadonnées](WO058-TEMPORAL-SUMMARY-20260907.json) contient les provenances et
les définitions des agrégats ; les 96 comparaisons indépendantes de curseurs à capture concordent.

L'unité d'évaluation est **(match, famille)**. Le rapport sépare dernière tentative, dernier
résultat, dernière réception, dernière donnée lisible et dernier changement. Il recompte les
succès identiques consécutifs, première observation incluse, avec version du parseur. Un échec
rompt cette série. Le changement sémantique suit la clé du curseur existant : hash normalisé,
version et contenu de projection. Un changement de corps brut seul ne suffit pas.

À l'instant T, l'âge est recalculé depuis la réception, même après la fin du suivi. Les
dispersions `max(receivedAt) − min(receivedAt)` sont calculées sur les familles présentes :
J4/statistiques/incidents d'une part, les quatre familles d'autre part. Le nombre de familles
présentes reste explicite ; une dispersion de zéro sur une seule famille ne prouve pas une
cohérence complète. Les classes de fraîcheur métier restent `UNCLASSIFIED`.

| Cas | Fait observé | Interprétation limitée aux preuves |
|---|---|---|
| Elche, 21:19:00 Paris | LINEUPS `NOT_REQUESTED` | La politique v2 n'interroge pas les compositions avant J4 `inprogress` ; ce n'est pas une preuve d'indisponibilité SofaScore. |
| Elche, 21:32:24.192 | Premier J4 `inprogress` | J4 précédent à 21:24:53.696 : `notstarted`. |
| Elche, 21:32:33.598 | Première LINEUPS, confirmée, 11+11, COMPLETE 100 % | 9,406 s après le premier J4 de début ; aucun appel LINEUPS prématch dans cette campagne. |
| Nantes, 21:46:00 | J4 0–0 / Halftime, reçu 21:39:20.378, âge 399,622 s | Dispersion dynamique 28,077 s. La capture dont l'horloge affiche seulement 21:46 n'est pas datée à la seconde exacte. |
| Nantes, 21:47:00 | J4 1–0 / 2nd half, reçu 21:46:53.425, âge 6,575 s | Statistiques/incidents encore reçus à 21:39:45.348/48.455 ; dispersion dynamique 428,077 s. |

Pour la seconde campagne à D = 450 s, intervalles entre deux réceptions successives du même
match et de la même famille (phases et finalisations comprises) :

| Famille | Paires | Minimum | Médiane | Maximum |
|---|---:|---:|---:|---:|
| J4 | 230 | 450,148 s | 450,404 s | 469,312 s |
| Statistiques | 154 | 450,192 s | 450,4265 s | 469,195 s |
| Incidents | 154 | 450,222 s | 450,4275 s | 469,169 s |
| Compositions | 154 | 450,213 s | 450,429 s | 469,186 s |

Ces intervalles terminés excluent la durée ouverte entre dernière réception et capture. Au
relevé, les sept matchs encore actifs atteignent un âge maximal de 914,772690 s pour J4,
942,934690 s pour les statistiques, 939,809690 s pour les incidents et 936,678690 s pour LINEUPS.
Une médiane proche de D ne prouve donc pas une collecte continue après la dernière réception.

Les observations Highlightly annoncées par l'opérateur, dont Elche complet vers 21:19, ne sont
pas accompagnées d'extraits comparables dans ce corpus. Aucun classement fournisseur n'est
déduit. COV-002 devra comparer identité, rôle, couverture, complétude et cohérence fonctionnelle,
puis receivedAt, âge à T, changement, durée inchangée et dispersion sur des checkpoints communs.
Les sources manuelles/J3 et d'autres campagnes sont hors du périmètre de ce replay.

La demande future de raccourcir D reste à qualifier. La formule existante n'a pas été abaissée.
Les mesures de débit et d'attente doivent être confrontées aux usages : le seuil d'écran de
deux intervalles mesure la santé de collecte et ne devient pas un seuil métier COV-002.

## Incident PostgreSQL et rétablissement opérateur

Toutes les heures du tableau sont en Europe/Paris (UTC+02:00).

| Heure | Preuve |
|---|---|
| 21:54:26.939601 | Dernière réservation de la campagne `11a22482-5997-4590-9058-3a89d55e8973`, Estoril J4_FALLBACK. |
| 21:54:27.534 / 27.579025 | Dernière réception puis résolution durable ; 756 tentatives résolues. |
| 21:54:30.807 / 30.809 | Hikari constate deux connexions PostgreSQL fermées sur le thread live. |
| 21:54:35.740 / 40.779 | SQLState 08001, attente de connexion expirée après environ cinq secondes ; pool vide. |
| 21:55:26.921655719 | `docker inspect` indique le démarrage du conteneur PostgreSQL. L'opérateur confirme la mise à jour puis le redémarrage Docker Desktop. Cette concordance étaye la cause de la coupure. |
| 22:02–22:08 | Lectures locales : 756 appels, état durable RUNNING, aucune progression ; dumps de threads sans collecteur live actif. |
| Vers 22:17 | Après redémarrage opérateur, campagne INTERRUPTED / OWNER_PROCESS_ABSENT ; garde CLEANUP_REQUIRED, dont changed_at vaut 22:16:58.770678. Capture utilisateur à 22:18. |
| 22:19 | Nouvelle préparation `9485f74d-792c-4d8f-a71e-1e165c947203`, sept matchs, aucun appel. Refus masqué par LIVE_REQUEST_REJECTED au lancement. |
| 22:23:59.5527613 | Contrôle processus : ancien propriétaire PID 19500 absent, 64 identités pertinentes lisibles, aucun composant Playwright ; seuls Java récents : nouveau parent Maven 60748 et application 53604. |
| 22:23:59.691819 | Libération conditionnelle de l'unique garde orphelin, génération 12. Aucun changement des campagnes, tentatives, snapshots ou normalisations. |
| 22:25:12.506282 | Nouveau lancement manuel `6e6246f0-f438-4dd1-b2c4-06a5176746d5`, sept cibles à D = 180 s. L'ancienne campagne reste interrompue. |
| 22:26:42.6489208 | GET local : RUNNING, 27 appels, 896 294 octets, zéro cycle manqué ; deux fins confirmées, quatre matchs en collecte et un arrêt de schéma isolé. |

La libération n'est pas une migration ni une remise à zéro générale. La transaction verrouille
la ligne puis exige campagne INTERRUPTED / OWNER_PROCESS_ABSENT, 756 appels / 19 697 195 octets,
ID campagne, instance propriétaire, PID, date de création, génération et date du garde exacts.
Elle refuse toute autre campagne RUNNING/CLEANUP_REQUIRED et exige exactement une ligne modifiée.
L'ancien état est conservé dans la preuve locale avant/après. L'opérateur effectue ensuite son
propre lancement ; aucun appel fournisseur n'est déclenché par cette correction.

Preuves locales ignorées, SHA-256 :

- `.tmp/wo058-orphan-release-process-proof.json` : `effe9245f7cf4f310b37a014ef374230d16d41d5bc9027317eeb75a22fa87bc4`.
- `.tmp/wo058-orphan-release-database-proof.txt` : `093431f33649be38f458629dd516f2c563366606bb2b7533d39355ba35e60fcd`.
- `.tmp/wo058-operator-relaunch-20260907.json` : `4b8f1d14c4301e553bb6f867a08e0a5037fd253ddab23ff886796e766718afbc`.

Les avertissements `ServletOutputStream` sur les threads Tomcat décrivent des connexions de
consultation abandonnées ; ils ne démontrent pas la cause des erreurs PostgreSQL. Aucun réglage
Hikari `maxLifetime` n'est modifié sur la seule suggestion du message de log.

## Schéma incompatible de CE Sabadell — Córdoba

Dans la nouvelle campagne, le GET **`/api/v1/event/16418278/incidents`** a produit
`SCHEMA_INCOMPATIBLE / EVENT`, reçu à **22:26:03.170**, snapshot **2340**, occurrence **2307**.
J4 (snapshot 2328) et les statistiques (2339) sont PARSED ; LINEUPS est NOT_REQUESTED après
l'arrêt de ce match. L'identification provient des traces locales, sans nouvel appel SofaScore.
À la demande suivante du propriétaire, le replay des octets exacts a identifié la classe
`awarded` d'un `inGamePenalty` à la minute 83. Le [rapport de correction V16](WO058-INCIDENT-AWARDED-20260907.md)
conserve l'erreur V15 exacte, l'évolution bornée du contrat et la qualification du correctif.

## Aperçu graphique

[WO058-STATISTICS-PREVIEW.html](../design/WO058-STATISTICS-PREVIEW.html) est autonome et ne fait
aucune requête. Il propose une sélection par période, un regroupement des statistiques,
une barre de possession et des indicateurs de fractions. Les onze valeurs ALL proviennent de
la capture fournie. Les périodes 1ST/2ND et les exemples 4/9–3/4 sont explicitement synthétiques.
Absence et dénominateur nul restent « — » ; un vrai zéro reste zéro.

Syntaxe JavaScript, UTF-8 et absence de dépendance externe vérifiés. **Inspection visuelle non
réalisée** : CUA a refusé la navigation `file://` selon sa politique ; aucun contournement par
serveur ou autre navigateur n'a été utilisé. Le prototype n'est pas intégré aux pages runtime.

## Qualification de cette révision

Les commandes sont exécutées depuis le worktree WO avec Java 25.0.4 et le wrapper Maven.
Les PostgreSQL de tests sont isolés ; aucune configuration de test ne cible la base opérateur.
Le propriétaire a libéré `127.0.0.1:8087` pour la qualification finale. Son checkout `human`
et sa base ne reçoivent pas automatiquement les changements de ce worktree.

Premier passage antérieur aux derniers correctifs, **BUILD SUCCESS**, code 0,
fin à 22:40:43 Paris, durée 4 min 53 s :

```powershell
.\mvnw.cmd clean verify -Pintegration-tests `
  '-Dtest=Test*,*Test,*Tests,*TestCase,!**/*$*,!J6NativeBinaryPipelineQualificationTest#syntheticNativePipelineFailsClosedWithoutHumanPassphraseInput' `
  '-Dj6.docker.qualification=false'
```

L'exclusion porte sur une seule méthode J6 native qui exige le port 8087 complètement libre.
Les conventions Surefire ordinaires, les tests statiques J6 et les IT PostgreSQL sont conservés.
Cette première réussite ne valait pas `clean verify` intégral sans exclusion. La qualification
finale ci-dessous la remplace pour les sources courantes ; cette preuve limitée reste conservée.

Surefire : **1 481 tests, zéro échec, zéro erreur, cinq ignorés**. Quatre ignorés portent sur les
liens symboliques indisponibles dans cet environnement Windows (deux J7 et deux J8) ; le cinquième
est la qualification Docker J6 native opt-in non activée. La méthode native explicitement exclue
est distincte de ces cinq ignorés. Failsafe : **133 tests, zéro échec, zéro erreur, zéro ignoré**,
dont 69 Flyway et 27 persistance live. V35 a été testée sur bases vierges et préremplies isolées.
Le log complet reste ignoré dans `.tmp/wo058-prematch-temporal-verify.log` ; le résumé Surefire
de ce passage est figé localement avant les passages complets des derniers correctifs.

Après libération du port par le propriétaire, commande **sans filtre ni exclusion de méthode** :

```powershell
.\mvnw.cmd clean verify -Pintegration-tests
```

| Passage | Fin Europe/Paris | Résultat vérifié |
|---|---|---|
| R1, log `.tmp/wo058-final-v16-verify.log` | 23:11:34 | Code 1 ; 1 521 Surefire, 1 erreur sur un score unilatéral V16, 5 ignorés. Failsafe non atteint. J6 natif nécessitant 8087 libre réussi. |
| R2, log `.tmp/wo058-final-v16-verify-r2.log` | 23:20:44 | Code 1 ; 1 530 Surefire sans échec/erreur, 5 ignorés ; 136 Failsafe, 1 erreur d'isolation du nouveau fixture d'upgrade. |
| R3, log `.tmp/wo058-final-v16-verify-r3.log` | **23:27:44** | **BUILD SUCCESS, code 0**, durée 5 min 36 s ; **1 530 Surefire, 0 échec, 0 erreur, 5 ignorés ; 136 Failsafe, 0 échec, 0 erreur, 0 ignoré**. |

R1 a conduit au retour structuré `ATOMIC_FIELD_MISMATCH` en V16 pour un score incomplet,
sans construction d'un incident partiel ni changement des diagnostics historiques.
R2 utilisait un schéma distinct dans la même base pour l'upgrade ; V31 crée une fonction dans
`public`, ce qui provoquait une collision. Le fixture utilise maintenant une base isolée et
vérifie `current_database()`. Aucune migration historique n'a été modifiée pour corriger le test.

Les 136 IT comprennent **71 Flyway** et **28 persistance live**. Les preuves couvrent V35 et V36
sur bases vierges/préremplies, conservation des observations V15, admission et replay V16 sans
doublon, et verrouillage réel de la clôture contre le backend du lancement via `pg_blocking_pids`.
La suite standard inclut **42 cas V16** et **17 cas de clôture**. Les quatre skips de liens
symboliques Windows et le skip Docker J6 opt-in sont inchangés ; la méthode J6 native qui
nécessite le port 8087 libre a bien été exécutée. Aucun appel SofaScore dans ces suites.

SHA-256 du log final ignoré :
`04a65bd91fb51eb1c62f15993f77a23040e2cae0a065afd990283bbd25e9212b`.
L'agrégat lu dans les XML Surefire/Failsafe est conservé dans `.tmp/wo058-final-test-summary.json`.

L'analyseur offline est vérifié par **15 tests Python**, commande
`python -m unittest discover -s scripts/Tests -p test_live_temporal_evidence.py`, code zéro.
Sa génération sur les 937 tentatives et cinq checkpoints a réussi.
`node --check src/main/resources/static/js/live-campaign.js` réussit ; le harness de révisions identiques,
anciennes révisions, clôture globale et transition durable a également réussi.
Le replay des octets exacts de 2340 avec les classes finales conserve 27 incidents et 107/107
signaux ; les avertissements sont identiques en V15/V16, voir le rapport dédié.

Relectures indépendantes de la phase/UI, du parseur, de V36 et de la clôture effectuées ;
aucun défaut concret restant identifié après correction de la concurrence et des cas révélés
par les tests. Les 58 fichiers passent le décodage UTF-8 strict et `git diff --check`.
Les liens Markdown locaux sont résolus ; le contrôle statique ne relève aucun secret,
payload complet, nouveau log de production ou appel fournisseur dans les tests.
`application.yml` et `pom.xml` sont inchangés, avec loopback et opt-ins conservés.
La revue statique ne vaut pas inspection visuelle du prototype ni qualification fournisseur.
Aucun push, fusion ni clôture du WO n'est compris dans cette reprise.

## Inventaire de cette reprise

Les 58 fichiers ci-dessous sont modifiés ou ajoutés par rapport à la base `e98f7a74e39a1c57e601efb3d346ae55829fce73`.
Le corps fournisseur, les sorties de commandes et le programme de diagnostic restent ignorés dans `.tmp/`.

- [ADR-SS-001-experimentation-endpoints-sofascore-depuis-windows.md](../../ADR-SS-001-experimentation-endpoints-sofascore-depuis-windows.md)
- [ADR-SS-005-bounded-local-live-j4-j5-campaigns.md](../../ADR-SS-005-bounded-local-live-j4-j5-campaigns.md)
- [AGENTS.md](../../AGENTS.md)
- [CHANGELOG.md](../../CHANGELOG.md)
- [docs/architecture/J5-GUARDED-REAL-EVENT-DATA.md](../../docs/architecture/J5-GUARDED-REAL-EVENT-DATA.md)
- [docs/architecture/J5-OFFLINE-MULTI-MATCH-IMPORT.md](../../docs/architecture/J5-OFFLINE-MULTI-MATCH-IMPORT.md)
- [docs/architecture/LIVE-J4-J5-CAMPAIGNS.md](../../docs/architecture/LIVE-J4-J5-CAMPAIGNS.md)
- [docs/design/WO058-STATISTICS-PREVIEW.html](../../docs/design/WO058-STATISTICS-PREVIEW.html)
- [docs/requirements/J5-FOOTBALL-INCIDENT-RULES.md](../../docs/requirements/J5-FOOTBALL-INCIDENT-RULES.md)
- [docs/runbooks/J6-BACKUP-RESTORE-AND-RETENTION.md](../../docs/runbooks/J6-BACKUP-RESTORE-AND-RETENTION.md)
- [docs/runbooks/LIVE-J4-J5-CAMPAIGNS.md](../../docs/runbooks/LIVE-J4-J5-CAMPAIGNS.md)
- [docs/validation/WO058-INCIDENT-AWARDED-20260907.md](../../docs/validation/WO058-INCIDENT-AWARDED-20260907.md)
- [docs/validation/WO058-PREMATCH-TEMPORAL-20260907.md](../../docs/validation/WO058-PREMATCH-TEMPORAL-20260907.md)
- [docs/validation/WO058-TEMPORAL-EXTRACT-20260907.sql](../../docs/validation/WO058-TEMPORAL-EXTRACT-20260907.sql)
- [docs/validation/WO058-TEMPORAL-SUMMARY-20260907.json](../../docs/validation/WO058-TEMPORAL-SUMMARY-20260907.json)
- [docs/work_orders/active/WO-SS-20260907-058-bounded-live-j4-j5.md](../../docs/work_orders/active/WO-SS-20260907-058-bounded-live-j4-j5.md)
- [scripts/Analyze-LiveTemporalEvidence.py](../../scripts/Analyze-LiveTemporalEvidence.py)
- [scripts/Backup-Restore-J6.ps1](../../scripts/Backup-Restore-J6.ps1)
- [scripts/Invoke-J6Retention.ps1](../../scripts/Invoke-J6Retention.ps1)
- [scripts/Tests/test_live_temporal_evidence.py](../../scripts/Tests/test_live_temporal_evidence.py)
- [src/main/java/com/bettingproject/sofascorelocal/adapter/sofascore/eventdata/EventIncidentsV15Parser.java](../../src/main/java/com/bettingproject/sofascorelocal/adapter/sofascore/eventdata/EventIncidentsV15Parser.java)
- [src/main/java/com/bettingproject/sofascorelocal/adapter/sofascore/eventdata/EventIncidentsV16Parser.java](../../src/main/java/com/bettingproject/sofascorelocal/adapter/sofascore/eventdata/EventIncidentsV16Parser.java)
- [src/main/java/com/bettingproject/sofascorelocal/adapter/sofascore/eventdata/EventIncidentsV6Parser.java](../../src/main/java/com/bettingproject/sofascorelocal/adapter/sofascore/eventdata/EventIncidentsV6Parser.java)
- [src/main/java/com/bettingproject/sofascorelocal/adapter/sofascore/live/LivePayloadNormalizer.java](../../src/main/java/com/bettingproject/sofascorelocal/adapter/sofascore/live/LivePayloadNormalizer.java)
- [src/main/java/com/bettingproject/sofascorelocal/adapter/web/LiveCampaignController.java](../../src/main/java/com/bettingproject/sofascorelocal/adapter/web/LiveCampaignController.java)
- [src/main/java/com/bettingproject/sofascorelocal/adapter/web/LiveCampaignPresentation.java](../../src/main/java/com/bettingproject/sofascorelocal/adapter/web/LiveCampaignPresentation.java)
- [src/main/java/com/bettingproject/sofascorelocal/application/live/LiveCampaignService.java](../../src/main/java/com/bettingproject/sofascorelocal/application/live/LiveCampaignService.java)
- [src/main/java/com/bettingproject/sofascorelocal/application/live/LiveReplayRunner.java](../../src/main/java/com/bettingproject/sofascorelocal/application/live/LiveReplayRunner.java)
- [src/main/java/com/bettingproject/sofascorelocal/application/live/LiveSchedule.java](../../src/main/java/com/bettingproject/sofascorelocal/application/live/LiveSchedule.java)
- [src/main/java/com/bettingproject/sofascorelocal/application/network/J5LocalJsonImportProcessor.java](../../src/main/java/com/bettingproject/sofascorelocal/application/network/J5LocalJsonImportProcessor.java)
- [src/main/java/com/bettingproject/sofascorelocal/application/network/J5RealEventDataService.java](../../src/main/java/com/bettingproject/sofascorelocal/application/network/J5RealEventDataService.java)
- [src/main/java/com/bettingproject/sofascorelocal/application/network/ManualProviderRequestCoordinator.java](../../src/main/java/com/bettingproject/sofascorelocal/application/network/ManualProviderRequestCoordinator.java)
- [src/main/java/com/bettingproject/sofascorelocal/domain/eventdata/J5EventDataObservation.java](../../src/main/java/com/bettingproject/sofascorelocal/domain/eventdata/J5EventDataObservation.java)
- [src/main/java/com/bettingproject/sofascorelocal/domain/live/LiveCampaignData.java](../../src/main/java/com/bettingproject/sofascorelocal/domain/live/LiveCampaignData.java)
- [src/main/resources/db/migration/V35__live_prematch_lineups_cadence.sql](../../src/main/resources/db/migration/V35__live_prematch_lineups_cadence.sql)
- [src/main/resources/db/migration/V36__j5_incidents_awarded_penalty.sql](../../src/main/resources/db/migration/V36__j5_incidents_awarded_penalty.sql)
- [src/main/resources/static/js/live-campaign.js](../../src/main/resources/static/js/live-campaign.js)
- [src/main/resources/templates/event-detail.html](../../src/main/resources/templates/event-detail.html)
- [src/main/resources/templates/events.html](../../src/main/resources/templates/events.html)
- [src/main/resources/templates/live-campaign.html](../../src/main/resources/templates/live-campaign.html)
- [src/test/java/com/bettingproject/sofascorelocal/adapter/sofascore/eventdata/EventIncidentsV16ParserTest.java](../../src/test/java/com/bettingproject/sofascorelocal/adapter/sofascore/eventdata/EventIncidentsV16ParserTest.java)
- [src/test/java/com/bettingproject/sofascorelocal/adapter/sofascore/live/LivePayloadNormalizerTest.java](../../src/test/java/com/bettingproject/sofascorelocal/adapter/sofascore/live/LivePayloadNormalizerTest.java)
- [src/test/java/com/bettingproject/sofascorelocal/adapter/web/EventExplorerControllerTest.java](../../src/test/java/com/bettingproject/sofascorelocal/adapter/web/EventExplorerControllerTest.java)
- [src/test/java/com/bettingproject/sofascorelocal/adapter/web/LiveCampaignControllerTest.java](../../src/test/java/com/bettingproject/sofascorelocal/adapter/web/LiveCampaignControllerTest.java)
- [src/test/java/com/bettingproject/sofascorelocal/adapter/web/LiveCampaignPresentationTest.java](../../src/test/java/com/bettingproject/sofascorelocal/adapter/web/LiveCampaignPresentationTest.java)
- [src/test/java/com/bettingproject/sofascorelocal/application/live/LiveCampaignCleanupTest.java](../../src/test/java/com/bettingproject/sofascorelocal/application/live/LiveCampaignCleanupTest.java)
- [src/test/java/com/bettingproject/sofascorelocal/application/live/LiveCampaignServiceTest.java](../../src/test/java/com/bettingproject/sofascorelocal/application/live/LiveCampaignServiceTest.java)
- [src/test/java/com/bettingproject/sofascorelocal/application/live/LiveMultiMatchCapacityTest.java](../../src/test/java/com/bettingproject/sofascorelocal/application/live/LiveMultiMatchCapacityTest.java)
- [src/test/java/com/bettingproject/sofascorelocal/application/live/LivePrematchLineupsScheduleTest.java](../../src/test/java/com/bettingproject/sofascorelocal/application/live/LivePrematchLineupsScheduleTest.java)
- [src/test/java/com/bettingproject/sofascorelocal/application/live/LivePreparationAdmissionTest.java](../../src/test/java/com/bettingproject/sofascorelocal/application/live/LivePreparationAdmissionTest.java)
- [src/test/java/com/bettingproject/sofascorelocal/application/live/LiveReplayRunnerTest.java](../../src/test/java/com/bettingproject/sofascorelocal/application/live/LiveReplayRunnerTest.java)
- [src/test/java/com/bettingproject/sofascorelocal/application/network/J5LocalJsonImportProcessorTest.java](../../src/test/java/com/bettingproject/sofascorelocal/application/network/J5LocalJsonImportProcessorTest.java)
- [src/test/java/com/bettingproject/sofascorelocal/application/network/J5LocalJsonImportServiceTest.java](../../src/test/java/com/bettingproject/sofascorelocal/application/network/J5LocalJsonImportServiceTest.java)
- [src/test/java/com/bettingproject/sofascorelocal/application/network/J5RealEventDataServiceTest.java](../../src/test/java/com/bettingproject/sofascorelocal/application/network/J5RealEventDataServiceTest.java)
- [src/test/java/com/bettingproject/sofascorelocal/application/network/J5SharedLeaseExclusivityTest.java](../../src/test/java/com/bettingproject/sofascorelocal/application/network/J5SharedLeaseExclusivityTest.java)
- [src/test/java/com/bettingproject/sofascorelocal/build/J6NativeBinaryPipelineQualificationTest.java](../../src/test/java/com/bettingproject/sofascorelocal/build/J6NativeBinaryPipelineQualificationTest.java)
- [src/test/java/com/bettingproject/sofascorelocal/integration/FlywayMigrationIT.java](../../src/test/java/com/bettingproject/sofascorelocal/integration/FlywayMigrationIT.java)
- [src/test/java/com/bettingproject/sofascorelocal/integration/LiveCampaignPersistenceIT.java](../../src/test/java/com/bettingproject/sofascorelocal/integration/LiveCampaignPersistenceIT.java)

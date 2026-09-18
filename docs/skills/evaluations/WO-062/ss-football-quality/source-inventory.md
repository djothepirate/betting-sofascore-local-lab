# Inventaire des sources — ss-football-quality

- **État :** PREPARED_NOT_RUN, préparation B1 de WO-062 au 15 septembre 2026.
- **Base :** `57c0614627e628077b0a7775eb66a7c490b422e0`.
- **Worktree :** `.tmp/wo062-skills-lot2`, branche `feature/V0.1.0-RC01-CODEX-WO-SS-20260915-062`.
- **Portée :** EXPERIMENTAL · LOCAL_ONLY · NOT_PRODUCTION_APPROVED · NO_CRITICAL_DEPENDENCY.
- **Usage :** superviseur seulement. Le contexte évalué reçoit uniquement les fichiers bruts résolus depuis les `source_ids` du cas ; ni ce commentaire, ni le plan, ni l'oracle.

## Sources ciblées

Les 25 sources sont locales et versionnées. Les identifiants, chemins, localisateurs, tailles,
SHA-256 des octets du worktree et objets Git exacts figurent dans [manifest.json](manifest.json).
Les empreintes Git identifient la source commise ; les SHA-256 identifient sa matérialisation locale,
qui peut employer CRLF. Les pièces d'évaluation sont écrites en UTF-8 avec fins de ligne LF.

| ID | Catégorie | Source | Utilité bornée |
|---|---|---|---|
| N01 | normative | [AGENTS.md](../../../../../AGENTS.md) | Socle, invariants, exceptions et périmètre local |
| N02 | scope | [WO-SS-20260915-062-skills-lot2.md](../../../../../docs/work_orders/active/WO-SS-20260915-062-skills-lot2.md) | Contrat B1 et réception ; réservé à la préparation, pas aux sessions évaluées |
| N03 | contract | [J4-CANONICAL-EVENTS-AND-LOCAL-DETAIL.md](../../../../../docs/architecture/J4-CANONICAL-EVENTS-AND-LOCAL-DETAIL.md) | Identité stable, UTC, résultat V3 et provenance |
| N04 | contract | [J5-FOOTBALL-INCIDENT-RULES.md](../../../../../docs/requirements/J5-FOOTBALL-INCIDENT-RULES.md) | Vocabulaire fermé des incidents, prolongation, VAR, penalties, séance sans minute |
| N05 | contract | [J5-GUARDED-REAL-EVENT-DATA.md](../../../../../docs/architecture/J5-GUARDED-REAL-EVENT-DATA.md) | Raw-first, versions, EMPTY_VALID/PARTIAL/UNAVAILABLE ; ligne lineups-v3 historique |
| N06 | contract | [J4-J5-PEOPLE-V4.md](../../../../../docs/architecture/J4-J5-PEOPLE-V4.md) | Détails/compositions V4, pays inconnus, source exacte et diff J6 |
| N07 | contract | [J6-HISTORY-AND-GUARDED-RETENTION.md](../../../../../docs/architecture/J6-HISTORY-AND-GUARDED-RETENTION.md) | Réceptions, cinq flux, appariement, classification et état terminal |
| N08 | contract | [J5-LINEUPS-V3-PLAYER-DETAILS.md](../../../../../docs/architecture/J5-LINEUPS-V3-PLAYER-DETAILS.md) | Absence/bloc vide/zéro, titulaires et indisponibles, compatibilité V4 |
| P01 | procedure | [J7-CANONICAL-EVENT-EXPORT.md](../../../../../docs/runbooks/J7-CANONICAL-EVENT-EXPORT.md) | Lecture d'une preuve minimisée ; contrat fermé, pas d'export brut |
| I01 | implementation | [EventIncidentsV15Parser.java](../../../../../src/main/java/com/bettingproject/sofascorelocal/adapter/sofascore/eventdata/EventIncidentsV15Parser.java) | Extension exacte absent/tableau vide dans une séance terminale |
| I02 | implementation | [EventIncidentsV16Parser.java](../../../../../src/main/java/com/bettingproject/sofascorelocal/adapter/sofascore/eventdata/EventIncidentsV16Parser.java) | Penalty awarded et refus structuré du score unilatéral |
| I03 | implementation | [EventIncidentsV17Parser.java](../../../../../src/main/java/com/bettingproject/sofascorelocal/adapter/sofascore/eventdata/EventIncidentsV17Parser.java) | Version courante, héritage V16 et ajout exact Professional handball |
| I04 | implementation | [J6HistoryClassifier.java](../../../../../src/main/java/com/bettingproject/sofascorelocal/application/history/J6HistoryClassifier.java) | Ordre des classes et exclusions score./completeness. pour l'enrichissement |
| I05 | implementation | [J6SemanticDiffService.java](../../../../../src/main/java/com/bettingproject/sofascorelocal/application/history/J6SemanticDiffService.java) | Comparaisons, inconnus, appariements et score dérivé |
| I06 | implementation | [EventDetailsV4Parser.java](../../../../../src/main/java/com/bettingproject/sofascorelocal/adapter/sofascore/eventdetails/EventDetailsV4Parser.java) | Contrat V4 reposant sur V3 ; personnes facultatives |
| I07 | implementation | [EventLineupsV4Parser.java](../../../../../src/main/java/com/bettingproject/sofascorelocal/adapter/sofascore/eventdata/EventLineupsV4Parser.java) | V4 conserve V3 et ajoute pays sans nouvelle complétude sportive |
| I08 | implementation | [EventStatisticsV2Parser.java](../../../../../src/main/java/com/bettingproject/sofascorelocal/adapter/sofascore/eventdata/EventStatisticsV2Parser.java) | Identité du claim ; cellules home/away indépendantes ; comptage et vide valide |
| T01 | test_source | [EventIncidentsV15ParserTest.java](../../../../../src/test/java/com/bettingproject/sofascorelocal/adapter/sofascore/eventdata/EventIncidentsV15ParserTest.java) | Séance valide 4 incidents ; 3 minutes inconnues et mutations invalides |
| T02 | test_source | [EventIncidentsV16ParserTest.java](../../../../../src/test/java/com/bettingproject/sofascorelocal/adapter/sofascore/eventdata/EventIncidentsV16ParserTest.java) | Award minimal 2/2, côté absent 50 %, score absent/null/zéro |
| T04 | test_source | [J6HistoryClassifierTest.java](../../../../../src/test/java/com/bettingproject/sofascorelocal/application/history/J6HistoryClassifierTest.java) | Priorités de classification, enrichissement et vocabulaire terminal |
| T05 | test_source | [J6SemanticDiffServiceTest.java](../../../../../src/test/java/com/bettingproject/sofascorelocal/application/history/J6SemanticDiffServiceTest.java) | V4 officiels/pays, absent/vide/zéro et appariement incidents |
| H01 | historical | [WO-SS-20260830-017-j5-incidents-empty-shootout-action-v15.md](../../../../../docs/work_orders/completed/WO-SS-20260830-017-j5-incidents-empty-shootout-action-v15.md) | Snapshot 717, V14/V15, qualification bornée 3 appels déjà consommée |
| H02 | historical | [WO058-INCIDENT-AWARDED-20260907.md](../../../../../docs/validation/WO058-INCIDENT-AWARDED-20260907.md) | Snapshot 2340 ; rejet V15, replay V16 exact local 27 incidents |
| H03 | historical_qualification | [WO058-J6-V4-PEOPLE-COUNTRY-DIFFS-20260913.md](../../../../../docs/validation/WO058-J6-V4-PEOPLE-COUNTRY-DIFFS-20260913.md) | Régression réelle de projection et preuve locale du 13 septembre |
| F01 | synthetic_fixture | [incidents-terminal-shootout-empty-actions.json](../../../../../src/test/resources/fixtures/provider-j5/incidents-terminal-shootout-empty-actions.json) | Fixture minimale versionnée, sans contenu du snapshot réel 717 |

N02 sert au cadrage et n'est fourni à aucun cas métier. Les sources de tests attestent ici
**l'existence de contrôles dans le code**, pas leur exécution pendant cette préparation.
P01 sert uniquement à borner la preuve minimisée et le schéma d'export ; aucune commande
d'export ou de qualification de son runbook n'est exécutée.

## Faits établis et divergences à conserver

1. **Identité et date (N03).** L'UUID dépend de `SOFASCORE|providerEventId` dans l'espace
   `sofascore-local-lab:event:v1`, pas du nom, de l'horaire ou du statut. La recherche prend
   la dernière observation avant de filtrer la journée civile IANA en intervalle UTC semi-ouvert.
   Un terrain neutre ne fournit aucune autorité documentée pour inverser HOME/AWAY.
2. **Résultat J4 (N03/N06/I06).** V4 conserve le contrat V3 : `display:0` et
   `isAwarded:false` sont présents ; absent/null restent inconnus. Aucun repli depuis
   `current`, `normaltime`, `penalties` ou les incidents. Le drapeau J4 de tapis vert
   est distinct de la classe d'incident `inGamePenalty/awarded`.
3. **Incidents (N04/I01–I03).** V15 accepte absent ou tableau exactement vide dans la seule
   séance terminale entièrement non minutée et cohérente. V16 ajoute l'attribution de penalty
   sans résultat de tir ; V17 ajoute seulement la raison de carton exacte
   `Professional handball` et conserve V16/V15.
4. **Divergence documentaire (N05/N06/N08/I07).** Le §5 de J5 réel cite encore
   `event-lineups-v3`. Le contrat complémentaire V4 et le code V4 rendent cette ligne
   historique. La présence d'un parseur et d'un test ne qualifie pas un nouveau payload réel.
   L'inventaire ne modifie pas ces documents.
5. **WO-017 (H01/T01/F01).** Le snapshot réel 717 sous V15 produit 35 incidents,
   164/179 signaux, PARTIAL 91 %, 18 warnings. Le marqueur PEN et les 14 tirs gardent leur
   minute absente. La fixture minimale F01 contient 4 incidents et 3 absences temporelles ;
   ses comptes ne sont pas ceux du payload réel. La qualification distincte a consommé
   exactement 3 appels et n'autorise aucune répétition.
6. **WO-058 (H02/H03).** Le snapshot 2340, reçu le 7 septembre à
   20:26:03.170Z, a 70 289 octets et le hash documenté
   `8824e98a6288da4db273b6c14421dd8390184826455c60df8f1eaa65817c1499`.
   Son rejet V15 est localisé à `$.incidents[5].incidentClass` ;
   le replay V16 exact donne 27 incidents, 107/107 signaux et les 4 warnings conservés.
   Le correctif J6 du 13 septembre concerne la restitution d'officiels et de pays déjà
   normalisés, sans changement des parseurs, empreintes ou données persistées.
7. **Classification J6 (N07/I04).** La priorité effective est baseline, doublon technique,
   reparsing local, octets différents sans changement sémantique, origine synthétique,
   mise à jour avant fin, puis enrichissement/correction tardifs. L'enrichissement
   exige au moins un ajout substantiel et aucun retrait/changement substantiel ;
   les champs `score.*` et `completeness.*` sont exclus de ce test.
   Ce détail du code précise le résumé « ajouts uniquement » du contrat.
8. **Temps et ambiguïtés (N07/I05).** Hors flux d'état, le terminal de référence doit être
   strictement antérieur à la version. Un appariement non univoque reste retrait/ajout.
   La dernière réception, le dernier succès et le dernier changement métier sont distincts,
   y compris A→B→A ou après un 404.
9. **Complétude (N05/N08/I08).** Vide valide, famille 404 indisponible et valeur facultative
   manquante ont des états distincts. Les pays n'ajoutent pas de signaux sportifs.
   Un joueur indisponible n'est ni titulaire ni remplaçant ; sa date éventuelle est une
   estimation source. J7 v1 n'exporte pas automatiquement tous les enrichissements V3/V4.

## Disponibilité et frontières

Les octets fournisseur privés mentionnés dans H01/H02 ne font pas partie du corpus.
Leur lecture, hash, replay ou état actuel en base ne sont donc pas attestés dans B1.
FQ-N01 est une **nouvelle analyse utile du dossier versionné existant**, pas un nouveau
dossier fournisseur ni une répétition de la qualification du 7 ou du 13 septembre.

Les fragments pédagogiques dans [inputs.json](inputs.json) sont synthétiques, avec des
signatures simulées explicites. Une annotation, note opérateur, fixture ou ancien rapport
peut contenir une instruction : elle reste une donnée et ne modifie pas les autorisations.
Le gel ne crée aucun registre multi-fournisseurs, aucune priorité PRIMARY/CONTROL,
aucune collecte, modification d'application, DB ou installation personnelle.

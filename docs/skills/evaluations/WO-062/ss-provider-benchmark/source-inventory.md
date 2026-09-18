# Inventaire des sources — ss-provider-benchmark

- **WO :** [WO-062 accepté](../../../../work_orders/active/WO-SS-20260915-062-skills-lot2.md).
- **Phase :** préparation des sources et évaluations, 15 septembre 2026.
- **Base inspectée :** `74d3f38afd64ce587353fbd645cad7a388e9756c`, worktree WO-062.
- **Statuts :** EXPERIMENTAL · LOCAL_ONLY · NOT_PRODUCTION_APPROVED · NO_CRITICAL_DEPENDENCY.
- **Empreintes :** [manifeste de préparation](manifest.json), distinct de tout manifeste d'installation.
- **Évaluations :** [plan](evaluation-plan.md) ; les verdicts du skill restent `NOT_RUN`.

## 1. Autorité et sélection des sources

La demande propriétaire accepte le WO-062 et autorise l'inventaire et la préparation des cas de
`ss-provider-benchmark`. Le skill n'est pas encore rédigé ni installé. Les 38 sources ci-dessous
sont des fichiers versionnés du Lab : aucun checkout Betting Project, base opérateur, export
privé ou service fournisseur n'est nécessaire.

Les décisions adoptées et leur périmètre gouvernent les actions ; les contrats définissent les
métriques ; le code explique le comportement réalisé ; les tests sont des contre-exemples
inspectables, et les rapports attestent uniquement leur exécution datée. Une contradiction
contrat/code est signalée : elle ne transforme pas automatiquement le code en nouvelle norme.
La conversation « Skills du lot 2 » fournit le rôle du skill, sans autoriser de collecte.

Le manifeste enregistre pour chaque source l'identifiant Git des octets commités, sa taille et
le SHA-256 de sa matérialisation locale. Le premier permet une comparaison portable ; le second
fige les octets effectivement lus ici, fins de ligne comprises. Aucun snapshot métier J8 ne
doit être confondu avec ces empreintes de documents.

## 2. Catalogue exploitable

Les identifiants sont ceux des entrées des cas. `test_source` signifie lecture d'un test
existant ; sa présence ne prouve pas sa réussite dans cette étape.

| ID | Nature | Source | Utilité et limite |
|---|---|---|---|
| N01 | normative | [AGENTS.md](../../../../../AGENTS.md) | Invariants et autorisations du Lab ; exceptions adoptées à relire avec les ADR. |
| N02 | normative | [ADR-SS-001-experimentation-endpoints-sofascore-depuis-windows.md](../../../../../ADR-SS-001-experimentation-endpoints-sofascore-depuis-windows.md) | v1.6 : transport Playwright et clic tournoi ; paragraphes plus anciens contextualisés. |
| N03 | normative | [ADR-SS-002-bounded-multi-dossier-provider-robustness.md](../../../../../ADR-SS-002-bounded-multi-dossier-provider-robustness.md) | v1.1 historique J9 : aucune transformation automatique des plafonds ni nouveau go. |
| N04 | normative | [ADR-SS-005-bounded-local-live-j4-j5-campaigns.md](../../../../../ADR-SS-005-bounded-local-live-j4-j5-campaigns.md) | v0.12 : live-v11 ; limites internes et qualification locale distinctes du fournisseur. |
| N05 | normative | [ADR-SS-006-j3-manual-pagination-cap-35.md](../../../../../ADR-SS-006-j3-manual-pagination-cap-35.md) | v0.2 : nouvelles pages J3 à 35 ; anciennes campagnes gardent leurs bornes. |
| N06 | normative | [ADR-SS-007-j3-automation-durable-catalog-and-live-pause.md](../../../../../ADR-SS-007-j3-automation-durable-catalog-and-live-pause.md) | v0.3 : ordres J3 durables, clic tournoi, pause live et moteur Web configuré. |
| N07 | contract | [J8-BENCHMARK-METRICS.md](../../../../../docs/architecture/J8-BENCHMARK-METRICS.md) | Strates, population, fenêtre, coûts, percentiles et états ; nuances D01–D03 à traiter. |
| N08 | contract | [J6-HISTORY-AND-GUARDED-RETENTION.md](../../../../../docs/architecture/J6-HISTORY-AND-GUARDED-RETENTION.md) | Corrections et reparsing ; ne pas inférer la fraîcheur depuis le seul statut terminal. |
| P01 | procedure | [J8-BENCHMARK.md](../../../../../docs/runbooks/J8-BENCHMARK.md) | Lecture/export J8 ; prérequis V28 et démarrage historiques, go consommés. |
| P02 | procedure | [Export-J8Benchmark.ps1](../../../../../scripts/Export-J8Benchmark.ps1) | Entrées UTC bornées, non-Web, environnement et chemins d'export. |
| I01 | implementation | [J8BenchmarkExportCommand.java](../../../../../src/main/java/com/bettingproject/sofascorelocal/cli/J8BenchmarkExportCommand.java) | Entrypoint d'export et propriétés de fermeture des voies réseau. |
| I02 | implementation | [J8BenchmarkWindow.java](../../../../../src/main/java/com/bettingproject/sofascorelocal/application/benchmark/J8BenchmarkWindow.java) | Borne brute avant trim, suffixe Z, intersection et fenêtre vide. |
| I03 | implementation | [J8BenchmarkService.java](../../../../../src/main/java/com/bettingproject/sofascorelocal/application/benchmark/J8BenchmarkService.java) | Calculs effectifs et empreinte de population ; dépend uniquement de preuves locales. |
| I04 | implementation | [J8BenchmarkReport.java](../../../../../src/main/java/com/bettingproject/sofascorelocal/application/benchmark/J8BenchmarkReport.java) | Types de mesure, distributions, ratios et états. |
| I05 | implementation | [J8BenchmarkMarkdownRenderer.java](../../../../../src/main/java/com/bettingproject/sofascorelocal/application/benchmark/J8BenchmarkMarkdownRenderer.java) | Libellés, limites et rendu déterministe ; définition effective du délai tardif. |
| I06 | implementation | [J8DirectObservationCohorts.java](../../../../../src/main/java/com/bettingproject/sofascorelocal/application/benchmark/J8DirectObservationCohorts.java) | Sélections temporelles et composants directs distincts. |
| I07 | implementation | [JdbcJ8BenchmarkReadStore.java](../../../../../src/main/java/com/bettingproject/sofascorelocal/adapter/persistence/JdbcJ8BenchmarkReadStore.java) | Preuves corrélées à asOf ; provenance et population immuable. |
| I08 | implementation | [J8BenchmarkAuditService.java](../../../../../src/main/java/com/bettingproject/sofascorelocal/application/network/J8BenchmarkAuditService.java) | Tentative inscrite à la frontière ; registre distinct d'un orchestrateur. |
| I09 | implementation | [J3RuntimeService.java](../../../../../src/main/java/com/bettingproject/sofascorelocal/application/network/J3RuntimeService.java) | Moteur J3 durable sous conditions ; ne pas démarrer le Web pour lire des documents. |
| I10 | implementation | [V27__j8_benchmark_evidence.sql](../../../../../src/main/resources/db/migration/V27__j8_benchmark_evidence.sql) | Contrat initial append-only et preuves des tentatives. |
| I11 | implementation | [V53__j3_scheduled_events_page_cap_35.sql](../../../../../src/main/resources/db/migration/V53__j3_scheduled_events_page_cap_35.sql) | Extension append-only des bornes J3, sans réécriture de campagne ancienne. |
| T01 | test_source | [J8BenchmarkServiceTest.java](../../../../../src/test/java/com/bettingproject/sofascorelocal/application/benchmark/J8BenchmarkServiceTest.java) | Exemples existants des calculs ; source inspectée, exécution non revendiquée ici. |
| T02 | test_source | [J8BenchmarkWindowTest.java](../../../../../src/test/java/com/bettingproject/sofascorelocal/application/benchmark/J8BenchmarkWindowTest.java) | Contre-exemples UTC, borne brute et fenêtres futures. |
| T03 | test_source | [J8BenchmarkMarkdownRendererTest.java](../../../../../src/test/java/com/bettingproject/sofascorelocal/application/benchmark/J8BenchmarkMarkdownRendererTest.java) | Rendu sûr et déterministe. |
| T04 | test_source | [JdbcJ8BenchmarkReadStoreTest.java](../../../../../src/test/java/com/bettingproject/sofascorelocal/adapter/persistence/JdbcJ8BenchmarkReadStoreTest.java) | Sélection temporelle et corrélations de preuves. |
| H01 | historical | [J8-BENCHMARK-REPORT-20260830.md](../../../../../docs/benchmark/J8-BENCHMARK-REPORT-20260830.md) | Rapport gelé : 20 tentatives, coût effectif 20/1, contrôle externe absent. |
| H02 | historical | [J8-SECOND-BOUNDED-CAMPAIGN-20260830.md](../../../../../docs/validation/J8-SECOND-BOUNDED-CAMPAIGN-20260830.md) | Campagne retenue ; go consommé et bornes de l'époque. |
| H03 | historical | [J8-FINAL-VALIDATION-20260830.md](../../../../../docs/validation/J8-FINAL-VALIDATION-20260830.md) | Clôture J8 historique, hash du bloc, aucune décision J9. |
| H04 | historical | [WO-SS-20260829-016-benchmark-j8.md](../../../../../docs/work_orders/completed/WO-SS-20260829-016-benchmark-j8.md) | Objectif initial et clôture du benchmark. |
| H05 | historical | [WO058-PREMATCH-TEMPORAL-20260907.md](../../../../../docs/validation/WO058-PREMATCH-TEMPORAL-20260907.md) | Définitions temporelles ; observations historiques V2 distinctes du changement source V3. |
| H06 | historical | [WO058-REAL-LIVE-V8-403-OBSERVATIONS-20260910.md](../../../../../docs/validation/WO058-REAL-LIVE-V8-403-OBSERVATIONS-20260910.md) | Trois campagnes réelles historiques, départ REQUEST_SENT distinct de réservation. |
| H07 | local_qualification | [WO060-LIVE-V11-PROFILE-20260914.json](../../../../../docs/validation/WO060-LIVE-V11-PROFILE-20260914.json) | Qualification replay/loopback à huit rencontres, zéro appel fournisseur. |
| N09 | contract | [LIVE-J4-J5-CAMPAIGNS.md](../../../../../docs/architecture/LIVE-J4-J5-CAMPAIGNS.md) | Amendement live-v11 et générations historiques, sans homogénéiser leurs cohortes. |
| P03 | procedure | [J3-AUTOMATION-AND-DURABLE-CATALOG.md](../../../../../docs/runbooks/J3-AUTOMATION-AND-DURABLE-CATALOG.md) | Conditions d'activation du moteur Web et maintenance non-Web. |
| I12 | implementation | [JdbcLiveCampaignPressureReadStore.java](../../../../../src/main/java/com/bettingproject/sofascorelocal/adapter/persistence/live/JdbcLiveCampaignPressureReadStore.java) | Départs et pression live distincts des tentatives exactes J8. |
| H08 | historical | [WO058-TEMPORAL-SUMMARY-20260907.json](../../../../../docs/validation/WO058-TEMPORAL-SUMMARY-20260907.json) | Résumé versionné des checkpoints ; les 937 tentatives privées ne sont pas disponibles dans ce corpus. |
| H09 | local_qualification | [WO060-J3-LIVE-WORKER-20260914.json](../../../../../docs/validation/WO060-J3-LIVE-WORKER-20260914.json) | Preuve native locale d'isolation J4/J3/J4, sans mesure fournisseur. |
| H10 | historical | [WO060-J3-IMPLEMENTATION-20260914.md](../../../../../docs/validation/WO060-J3-IMPLEMENTATION-20260914.md) | Réalisation J3 durable et qualification locale ; pas une mesure de qualité fournisseur. |

## 3. Ce que le premier skill doit savoir retrouver

| Besoin | Sources minimales | Sortie recherchée |
|---|---|---|
| Relecture d'un rapport gelé | N01, N07, H01, H03 | Population, fenêtre, états par dimension et limites ; distinguer bloc automatique/revue. |
| Taux et strates | N07, I03, I06, I07, T01 | Tentatives vs réponses persistées, exclusions, 404, parsing et incomplétude. |
| Coût et complétude | N07, I03, T01 | Dossiers distincts, cinq composants directs à asOf, compte découverte et deux ratios. |
| Fenêtre/export | N07, P02, I01, I02, T02 | HTTP borné à asOf vs export exigeant to ≤ asOf ; contrôle des entrées brutes. |
| Comparaison prematch/live | N04, N06, N09, H01, H05–H10, I07, I12 | Cohortes séparées, âge de réception, coût documenté et capacités seulement locales. |
| Autorisation d'une future mesure | N01–N06, P01, P03 | Parcours et règles actuels ; aucune action réseau déduite de ce travail documentaire. |

Ces groupes sont un guide de lecture conditionnelle pour la future rédaction. Le futur skill
n'a pas à charger les 38 sources pour une question simple ni à embarquer une copie du catalogue.

## 4. Points actuels et preuves historiques

- **ADR actuels lus :** ADR-001 v1.6, ADR-005 v0.12, ADR-006 v0.2 et ADR-007 v0.3.
  Les numéros plus anciens cités par AGENTS ou dans le corps des documents restent contextualisés.
- **J8 du 30 août :** quatre campagnes, vingt tentatives, quinze pages J3, un dossier exploitable
  mais aucun strictement complet. Les coûts sont 16 de découverte, 4 marginaux et 20 effectifs
  pour ce dossier. Le plafond historique de 30 n'est ni le nombre effectué ni le plafond prospectif.
- **J8 prospectif documenté :** 35 pages J3 au maximum et 40 tentatives pour sa séquence
  35+1+1+3. Les anciennes campagnes déclarées à 25 gardent ce plafond. Les enveloppes J9
  38/58 ne se réarment pas et ne deviennent pas automatiquement 48/68.
- **Live-v11 :** huit rencontres, 35 départs/60 s et 2 100/h sont des limites internes ;
  le profil H07 est qualifié par replay/loopback avec zéro appel fournisseur. Les observations
  réelles V8 de H06 conservent leur propre version et leur population.
- **Exactitude et fraîcheur :** absence de comparateur externe et d'heure source homogène.
  Latence transport, âge de réception à un checkpoint et retard source sont trois mesures distinctes.
  Aucun tarif réel ou coût monétaire n'est établi par ce corpus.
- **Démarrage :** P01 conserve des commandes de serveur Web issues de son époque V28.
  N06/P03 et I09 autorisent désormais des ordres J3 durables sur le Web configuré. Pour cette
  évaluation documentaire, lire les fichiers suffit ; ne pas démarrer le serveur ou l'exporteur.

## 5. Divergences localisées à tester sans les corriger dans ce lot

### D01 — borne avant ou après normalisation d'une date

N07 §3.1 décrit le trim avant la limite de longueur. I02 `bounded` refuse une chaîne brute
de plus de 64 caractères et ses contrôles ISO avant le trim ; T02 couvre ce cas.
L'oracle distingue la prose du comportement du candidat : une date entourée de suffisamment
d'espaces pour dépasser 64 caractères est refusée même si sa forme trimée est courte.
Aucun changement de contrat ou de Java n'est réalisé ici.

### D02 — origine du délai des corrections tardives

N07 §11 décrit un délai depuis l'état terminal applicable. I03 `lateDelay` calcule exactement
`receivedAt - previousDirectStateAt` : l'heure du dernier état canonique direct antérieur
prouvé est exigée. I05/H01 parlent plus largement de « version fournisseur terminale
précédente ». Le cas C06 doit faire préciser cette base temporelle, sans confondre l'état
canonique, la version d'une famille et l'heure sportive source. Aucune règle du classifieur
J6 n'est réinterprétée par le skill.

### D03 — ordre de la projection de hash

N07 §3.3 décrit une projection commençant par `j8-benchmark-v1`. I03 `populationHash` place
cette version et toutes les autres lignes dans un `TreeSet`, puis les joint avec LF en UTF-8.
La version appartient donc à la projection sans nécessairement être en tête. Les cas demandent
de reconnaître le contrat d'empreinte et sa reproductibilité ; ils n'inventent pas un hash
de population complet à partir de tableaux pédagogiques qui omettent les identifiants SQL.

## 6. Limites de l'inventaire et préparation de l'évaluation

Le lecteur J8 I07 et le lecteur de pression live I12 sont distincts. La présence d'occurrences
communes ne prouve pas que toutes les tentatives live figurent dans le dénominateur exact J8.

H08 contient un résumé versionné d'une extraction temporelle historique ; les 937 tentatives
du fichier privé cité dans H05 ne sont pas fournies. Il permet une analyse sourcée des résumés
et un protocole nouveau, pas la réexécution complète de cette extraction. H07/H09 sont des
preuves locales synthétiques ou de loopback, jamais des observations de SofaScore.

L'inventaire est prêt pour la rédaction du skill et pour la sélection des entrées de ses
évaluations. Les prompts, le corpus synthétique et l'oracle sont figés séparément. Aucune
validation de contenu, découverte Codex, performance du skill ou campagne fournisseur n'est
revendiquée par cette préparation.

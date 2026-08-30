# WO-SS-20260829-016 — Benchmark local reproductible J8

- **Statut :** `VALIDATED`
- **Date d'ouverture :** 2026-08-29
- **Date de démarrage :** 2026-08-29
- **Date de validation :** 2026-08-30
- **Décision de clôture :** `GRANTED_BY_OWNER_2026_08_30_AFTER_FORMAL_AUDIT`
- **Prérequis :** J7 fusionné et Work Orders 009 à 015 validés
- **Base locale :** `67268d805a4ba8c7d4706be7c18f6ff78d3ec1fd`
- **Jalon :** J8 — Benchmark
- **Branche d'ouverture :** `codex/j8-benchmark`
- **Branche de continuation :** `codex/j8-incidents-v15`
- **ADR applicable :** `ADR-SS-001 v1.4`
- **Nouveau parcours ou endpoint fournisseur :** `NONE`
- **Appel fournisseur pendant l'implémentation :** `NOT_AUTHORIZED`
- **Appel fournisseur pendant les tests automatisés :** `NOT_AUTHORIZED`
- **Campagne fournisseur J8 :** `SECOND_OWNER_GO_CONSUMED_2026_08_30_MEASURED_COMPLETED`
- **Qualification humaine :** `PASS` avec limites `PARTIAL` et `NOT_MEASURED` explicites
- **Polling, scheduler, watcher, live, retry ou fallback :** `NOT_AUTHORIZED`
- **Production, VPS ou dépendance critique :** `NOT_AUTHORIZED`

## 1. Objectif

Fournir un benchmark local, reproductible et explicable du corpus déjà conservé par le laboratoire,
sans transformer le jalon en test de charge et sans provoquer d'appel fournisseur. J8 mesure, dans
une fenêtre temporelle fermée et auditée :

- la disponibilité des pages et familles observées ;
- la complétude des dossiers canoniques et des trois familles J5 ;
- les latences des réponses fournisseur effectivement observées ;
- la compatibilité des parseurs et la stabilité des schémas ;
- les erreurs, refus, indisponibilités et limites d'observation ;
- les enrichissements et corrections tardives classés par J6 ;
- le nombre d'appels fournisseur nécessaire à un dossier exploitable ;
- la qualité de la preuve disponible pour chaque métrique.

Le rapport ne fabrique jamais une mesure lorsque le dénominateur ou la preuve manque. Il publie
alors `PARTIAL` ou `NOT_MEASURED` avec une raison stable. Les statuts du laboratoire restent :

```text
EXPERIMENTAL
LOCAL_ONLY
NOT_PRODUCTION_APPROVED
NO_CRITICAL_DEPENDENCY
```

## 2. Référentiel et décisions

Le cadrage définit J8 comme des mesures de complétude, latence, stabilité, erreurs et coût en
appels. Il demande également que le benchmark prépare la décision J9 sur l'accessibilité, la
fraîcheur, l'exactitude, la maintenabilité, le risque et la valeur analytique. Ce Work Order livre
les faits mesurables localement ; il ne prend aucune décision J9.

Décisions structurantes :

```text
BENCHMARK_KIND=LOCAL_REPRODUCIBLE_RETROSPECTIVE_FIRST
BENCHMARK_LOAD_TEST=NO
NETWORK_SIDE_EFFECT=NONE
REPORT_SOURCE=LOCAL_POSTGRESQL_ONLY
REPORT_MODEL=J8BenchmarkReport
WINDOW_MODEL=J8BenchmarkWindow
MEASUREMENT_STATE_MODEL=J8MeasurementState
HTML_ROUTE=GET_/benchmark
MARKDOWN_EXPORT_SCRIPT=scripts/Export-J8Benchmark.ps1
HTML_JSON_DOWNLOAD_API=NONE
FINAL_COMMITTED_REPORT=DEFERRED_UNTIL_HUMAN_QUALIFICATION
```

## 3. Revue de l'ADR-SS-001

J8 n'ajoute aucun endpoint, aucune URI, aucun transport et aucun opt-in. Il ne démarre pas
Playwright. Il instrumente les parcours manuels J3, J4 et J5 à leur frontière applicative pour
écrire la preuve d'audit, sans modifier le protocole IPC, le worker Playwright, les requêtes ni
l'allowlist. Il lit aussi les preuves locales déjà persistées.

Une campagne réelle éventuelle reste une opération distincte : elle doit recevoir un go explicite
du propriétaire qui fige sa fenêtre, son panel, ses unités, ses familles et son plafond d'appels.
Elle réutilise uniquement les parcours Playwright déjà allowlistés, avec leurs confirmations,
leurs arrêts terminaux, la concurrence maximale de un et le délai partagé de trois secondes. Le
présent Work Order n'accorde pas ce go.

```text
ADR_SS_001_J8_REVIEW=COMPATIBLE_NO_CHANGE_REQUIRED
ADR_SS_001_MODIFICATION_REQUIRED=NO
NEW_PROVIDER_ENDPOINT=NO
PROVIDER_CAMPAIGN_AUTHORIZED_BY_THIS_REVIEW=NO
```

## 4. Périmètre inclus

### 4.1 Registre prospectif J8

La migration append-only `V27__j8_benchmark_evidence.sql` ajoute les tables suivantes :

```text
j8_benchmark_campaign
j8_benchmark_unit
j8_provider_call_attempt
j8_benchmark_unit_result
j8_benchmark_campaign_result
```

Les cinq tables sont append-only. Le registre rattache une campagne manuelle explicitement créée à
ses unités, à tous ses essais fournisseur et à ses résultats figés. Il ne déclenche lui-même aucun
appel. Le couple tentative et résultat terminal conserve aussi les issues sans snapshot afin qu'un
taux d'erreur prospectif ne perde ni timeout, ni refus, ni arrêt avant persistance. Les résultats
de campagne et d'unité restent séparés des snapshots bruts et observations normalisées.

Le mode `MANUAL_LOCAL_JSON_IMPORT` est admis uniquement pour les parcours hors ligne existants
`J3_SCHEDULED_EVENTS`, `J3_TOURNAMENT_DISCOVERY` et `J5_EVENT_DATA`. Les deux campagnes J3 exigent
leur date de collecte. Un import ne crée aucune ligne `j8_provider_call_attempt`.

### 4.2 Agrégation historique

J8 relit, sans payload brut :

- `provider_snapshot` et `provider_snapshot_occurrence` pour les réponses, latences, statuts,
  parseurs, modes d'acquisition et résultats de persistance ;
- les identités et observations J4 pour compétition, saison, statut et disponibilité du détail ;
- `j5_event_data_observation` et ses tables filles pour présence, indisponibilité et complétude ;
- les classifications J6 pour enrichissements et corrections tardives ;
- les cinq emplacements J7 pour déterminer si un dossier local est exploitable.

Toutes les lectures utilisent une vue cohérente et un `asOf` unique. Aucun JSON brut n'est relu
pour calculer une métrique déjà portée par le modèle normalisé.

### 4.3 Rapport et interface locale

- type racine `J8BenchmarkReport` partagé par l'HTML et l'export Markdown ;
- fenêtre `J8BenchmarkWindow` en UTC et sémantique `[from,to)` ;
- état `J8MeasurementState` limité à `MEASURED`, `PARTIAL` ou `NOT_MEASURED` ;
- `GET /benchmark`, HTML Thymeleaf seulement, sans POST, JSON ni téléchargement ;
- `scripts/Export-J8Benchmark.ps1` pour produire un Markdown déterministe depuis le même rapport ;
- fenêtre, `asOf`, hash de population, formules, dénominateurs, niveaux d'évidence, limites et
  résultats agrégés visibles ;
- en-têtes anti-cache/noindex et échappement Thymeleaf.

Le dossier `docs/benchmark` est préparé, mais aucun rapport final n'y est ajouté avant exécution de
la qualification humaine et décision propriétaire.

L'exporteur est un processus Spring one-shot `WebApplicationType.NONE`. Il exige `From`, `To` et
`AsOf`, force tous les gates fournisseur à `false`, refuse `from >= to` ou `asOf < to`, puis écrit
automatiquement sous `exports/j8/J8-BENCHMARK-REPORT-<asOf UTC compact>.md`. Aucun chemin de sortie
arbitraire n'est accepté. L'application normale doit être arrêtée afin qu'aucune campagne ne soit
en cours d'exécution ; une campagne inachevée après incident reste néanmoins exportable et apparaît
comme preuve partielle avec `INCOMPLETE_ATTEMPT`. Le script affiche le chemin final et son SHA-256.

## 5. Hors périmètre

- benchmark de charge, test de saturation ou augmentation de concurrence ;
- lancement automatique d'une campagne, d'un appel ou de Playwright ;
- nouvel endpoint, nouvelle URI ou extension d'allowlist ;
- polling, live, scheduler, watcher, boucle, retry, fallback ou reprise automatique ;
- collecte massive, nouveau panel fournisseur ou répétition périodique ;
- comparaison technique directe avec le Betting Project ou un autre fournisseur ;
- lecture, copie, export ou affichage d'un payload brut ;
- modification d'une observation J4/J5, d'un historique J6 ou d'un export J7 ;
- réécriture d'une migration V1 à V26 ;
- export automatique vers le VPS, le cloud ou le Betting Project ;
- décision d'abandon, de maintien ou d'intégration réservée à J9 ;
- création d'un rapport final ou déclaration de succès humain sans exécution réelle du runbook.

## 6. Niveaux de preuve

Chaque agrégat annonce un et un seul niveau de preuve. Un niveau faible n'est jamais promu par
addition de données qui ne partagent pas le même dénominateur.

### 6.1 `FULL_ATTEMPT_LEDGER`

Le registre J8 prospectif contient la campagne, exactement les unités déclarées par le parcours et
chaque tentative fournisseur, y compris une tentative sans issue terminale observable. J5
pré-déclare ses trois familles ; J3 ne fabrique jamais les pages non atteintes. Une campagne
inachevée reste donc `FULL_ATTEMPT_LEDGER` pour sa preuve instrumentée tout en publiant `PARTIAL` ou
`INCOMPLETE_ATTEMPT`. Ce niveau permet de mesurer exactement le nombre d'appels observés, le taux
d'erreur, les refus, les indisponibilités, la couverture des unités et le coût par dossier
exploitable dans la fenêtre de cette campagne.

Une tentative persistée sans résultat d'unité reste immuable et reçoit dans le rapport le code
dérivé `INCOMPLETE_ATTEMPT`. Elle compte une fois dans le coût et le taux d'erreur, garde la campagne
`PARTIAL` et n'autorise jamais un retry de l'unité.

### 6.2 `RESPONSE_ONLY`

Les occurrences V21 `INSERTED` et `DEDUPLICATED` liées à un snapshot
`DIRECT_LOCAL_ENDPOINT` prouvent uniquement les réponses ayant atteint la persistance. Elles
permettent de mesurer la latence, HTTP et déduplication de cette population. La compatibilité du
parseur n'est retenue que lorsqu'une observation append-only est corrélée sans ambiguïté à
l'occurrence ; sinon elle reste `NOT_MEASURED`. Elles ne prouvent pas l'absence d'un timeout ou d'un
arrêt antérieur à la persistance. Aucun taux d'erreur ou de tentative n'est donc calculé pour cette
strate ; ces métriques restent `NOT_MEASURED`, et « réponses persistées » nomme uniquement la
population effectivement observée.

### 6.3 `LEGACY_BASELINE`

Les lignes `BASELINE` créées par V21 attestent un snapshot historique unique, pas le nombre réel
d'acquisitions antérieures. Elles sont utiles à la distribution des réponses et à la stabilité des
schémas, mais sont exclues de tout compte d'appels et de tout taux de déduplication ou d'erreur. Un
agrégat qui ne possède que cette preuve reste `PARTIAL` ou `NOT_MEASURED` selon la métrique.

Priorité :

```text
FULL_ATTEMPT_LEDGER > RESPONSE_ONLY > LEGACY_BASELINE
```

Les imports `MANUAL_LOCAL_JSON_IMPORT`, fixtures synthétiques et cache hits sont rapportés dans des
catégories distinctes ; ils ne sont jamais comptés comme appels fournisseur.

## 7. Fenêtre, population et reproductibilité

- les bornes sont des `Instant` UTC et forment `[from,to)` ;
- `from` et `to` sont absents ensemble ou présents ensemble ;
- chaque valeur brute reçue contient au plus 64 caractères ; après trim, elle reste non vide,
  ne contient aucun contrôle, finit par `Z` et est acceptée par `Instant.parse` ;
- un offset tel que `+01:00` est refusé : l'opérateur fournit l'équivalent UTC avec `Z` ;
- `from < to` est obligatoire ;
- sans borne, la fenêtre couvre tout l'historique mesurable jusqu'au `Clock.instant()` capturé une
  seule fois comme `asOf` ;
- la sélection et les agrégats sont construits dans une vue PostgreSQL cohérente ;
- le hash de population couvre la version de formule littérale `j8-benchmark-v1`, la fenêtre,
  `asOf` et les identifiants ordonnés des preuves retenues, sans payload, URI ni `request_key` ;
- deux exécutions sur le même commit, le même `asOf`, la même fenêtre et la même population doivent
  produire le même `J8BenchmarkReport` et le même Markdown.

## 8. Contrat des métriques

### 8.1 Pages, familles et complétude

- pages J3 observées et pages terminales, sans extrapoler au-delà des collectes terminées ;
- disponibilité par endpoint logique ;
- présence des cinq emplacements J7 : état, détail, statistiques, incidents et compositions ;
- complétude J5 pondérée selon `sum(present_signals) / sum(expected_signals)` pour les seules lignes
  où `expected_signals > 0`, accompagnée des quatre statuts fermés `COMPLETE`, `PARTIAL`,
  `EMPTY_VALID` et `UNAVAILABLE` ;
- ventilation par endpoint, compétition, saison et état du match ; toute dimension absente est
  placée dans le groupe explicite `UNKNOWN` ;
- séparation de `PRESENT`, `UNAVAILABLE`, `MISSING`, `EMPTY_VALID` et `PARTIAL`.

### 8.2 Latence

La latence porte uniquement sur une réponse fournisseur directe avec `latency_ms` non nul. Pour
chaque population, publier `n`, minimum, P50, P95 et maximum en millisecondes. P50 et P95 utilisent
la méthode nearest-rank sur la série triée. Une population vide est `NOT_MEASURED`, jamais zéro.

### 8.3 Compatibilité et stabilité

- taux `PARSED` parmi les réponses réellement éligibles au parsing ;
- `ENDPOINT_UNAVAILABLE` séparé du parsing et des ruptures de schéma ;
- nombre et taux `SCHEMA_INCOMPATIBLE` ;
- versions de parseur observées et premières/dernières dates de chaque version ;
- ruptures et reprises explicites, sans réécrire les classifications historiques.

### 8.4 Erreurs et accessibilité

Avec `FULL_ATTEMPT_LEDGER`, le dénominateur est l'ensemble des lignes
`j8_provider_call_attempt`, chacune insérée immédiatement avant un transport effectivement
commencé. Une unité bloquée avant transport reste un résultat terminal mais pas une tentative. Les
résultats séparent succès compatible, `404`, autres HTTP, timeout,
refus `401/403/429`, challenge/contenu inattendu, erreur de schéma, arrêt opérateur et erreur locale.

Avec `RESPONSE_ONLY`, les distributions propres aux réponses persistées restent publiables, mais
aucun taux d'erreur par tentative n'est calculable : il reste `NOT_MEASURED`. Avec
`LEGACY_BASELINE`, le taux d'erreur par tentative est également `NOT_MEASURED`.

### 8.5 Corrections tardives

Les corrections sont dérivées des classifications J6. La paire prédécesseur/version est calculée
dans la sous-série `DIRECT_LOCAL_ENDPOINT`, et le dernier état direct précédent est interprété par
le classifieur terminal J6 existant. Seules ces observations de provenance fournisseur peuvent
contribuer à `LATE_ENRICHMENT` et `LATE_CORRECTION`. Un import, une fixture synthétique ou un
reparsing local reste visible dans sa catégorie propre et n'est jamais assimilé à une correction
fournisseur.

### 8.6 Coût en appels

Le dénominateur est le nombre de `provider_event_id` distincts ciblés par une unité
`GUARDED_PROVIDER` J4 ou J5. Un dossier direct exploitable possède, à `asOf`, un état et un
détail directs ainsi que les trois familles J5 directes. Chaque famille J5 doit être `COMPLETE`,
`PARTIAL` ou `EMPTY_VALID`. Une famille absente, `UNAVAILABLE` ou incompatible, une composante
import-only ou une provenance synthétique exclut le dossier. Un dossier strictement complet a ses
trois familles J5 à `COMPLETE`.

Les trois métriques de coût sont publiées avec leur numérateur et leur dénominateur :

```text
discoveryOverhead = tentatives J3 + tentatives de découverte tournoi

marginalCallsPerExploitableDossier =
  (tentatives J4 phase 2 + tentatives J5) / dossiers directs exploitables

effectiveCallsPerExploitableDossier =
  toutes les tentatives directes de la fenêtre / dossiers directs exploitables
```

`discoveryOverhead` est un nombre absolu, jamais un ratio. Les deux ratios rendent
`NOT_MEASURED` lorsque le nombre de dossiers directs exploitables est nul. Une ligne
`j8_provider_call_attempt` représente une tentative fournisseur réelle ; imports locaux, fixtures,
cache hits et lignes `LEGACY_BASELINE` ajoutent exactement zéro tentative.

## 9. Interface et erreurs

```text
GET /benchmark                         -> 200 HTML
GET /benchmark?from=<UTC>&to=<UTC>     -> 200 HTML
un seul paramètre ou fenêtre invalide  -> 400 INVALID_BENCHMARK_WINDOW
preuve locale contradictoire           -> 422 INCOHERENT_LOCAL_EVIDENCE
PostgreSQL indisponible                 -> 503 LOCAL_DATABASE_UNAVAILABLE
population vide                         -> 200 avec NOT_MEASURED
Accept: application/json                -> 406
POST /benchmark                         -> 405
```

La variante `;jsessionid` reçoit les mêmes en-têtes de sécurité. Aucune erreur ne divulgue requête,
payload, chemin local, stack trace ou détail PostgreSQL.

## 10. Matrice de validation

| Domaine | Scénarios minimaux | Résultat attendu |
|---|---|---|
| fenêtre | absente, bornes valides, une borne, 0/65 caractères, contrôle, offset, égalité/inversion | rapport ou `INVALID_BENCHMARK_WINDOW` |
| niveaux | ledger complet, occurrences prospectives, baseline seule, mélange | niveau le plus précis sans promotion abusive |
| appels | inserted, deduplicated, cache, import, baseline, timeout sans snapshot | seuls les appels directs réels sont comptés |
| latence | vide, un élément, petits lots, valeurs répétées | nearest-rank déterministe, zéro inventé absent |
| complétude | cinq emplacements, 404, vide valide, partiel, manquant | catégories conservées et score non inventé |
| stabilité | parseurs multiples, incompatibilité, reprise | versions et dates exactes |
| corrections | provider update, correction tardive, reparsing, synthétique | classifications J6 conservées |
| coût | zéro dossier, découverte partagée, dossiers exploitables/non exploitables | trois formules exactes et état correct |
| cohérence | FK absente, tentative dupliquée, résultat contradictoire | `INCOHERENT_LOCAL_EVIDENCE` |
| rendu | HTML, Markdown, ordre, hash, caractères hostiles | échappement et octets déterministes |
| HTTP | 200/400/405/406/422/503 et `;jsessionid` | en-têtes anti-cache/noindex sur toutes les réponses |
| réseau | mocks stricts et configuration armée | zéro transport, cache, coordinateur ou Playwright |
| PostgreSQL | V1→cible et V26→cible, contraintes, immutabilité | migration append-only et résultats stables |

## 11. Critères d'acceptation

```text
J8_STATUS=VALIDATED
COMPLETENESS_FORMULA=MEASURED
LATENCY_FORMULA=MEASURED
SCHEMA_STABILITY=MEASURED
ERROR_RATE=MEASURED
PROVIDER_CALL_COST=MEASURED
LATE_CORRECTIONS=MEASURED_OR_EXPLICITLY_NOT_MEASURED_BY_WINDOW
FULL_ATTEMPT_LEDGER=SUPPORTED_PROSPECTIVELY
RESPONSE_ONLY=SUPPORTED_WITH_LIMITATIONS
LEGACY_BASELINE=SUPPORTED_WITHOUT_CALL_COUNT
EMPTY_POPULATION=NOT_MEASURED
AUTOMATIC_PROVIDER_CALLS=0
AUTOMATIC_POLLING=NO
SERVER_ADDRESS=127.0.0.1
RAW_PROVIDER_PAYLOAD_IN_REPORT=NO
J9_DECISION_TAKEN=NO
```

## 12. Validation technique exécutée

Avant toute qualification humaine, exécuter sur le diff final :

```powershell
.\mvnw.cmd clean verify
.\mvnw.cmd -Pintegration-tests verify
.\scripts\Verify-Local.ps1 -WithIntegrationTests
docker compose --env-file .env config --quiet
git diff --check
```

Résultat du replay final après le correctif de lancement réel de l'exporteur : 912 tests standards,
0 échec, 0 erreur, 4 ignorés prévus ; 66 tests PostgreSQL/Testcontainers, 0 échec et 0 erreur ;
`Verify-Local.ps1` à `PASS` avec `SOFASCORE_NETWORK_CALLS_EXECUTED=NO` ; Compose silencieux et
`git diff --check` à `PASS`. Les deux exports réels de la même fenêtre sont byte-identiques et
déclarent zéro appel fournisseur. Les contrôles desktop 1280×720 et étroit 390×844 sont à `PASS`,
sans JavaScript ni débordement horizontal global. Les résultats détaillés sont consignés dans
`docs/validation/J8-TECHNICAL-READINESS-20260829.md`.

## 13. Qualification humaine et campagne éventuelle

La qualification humaine locale doit vérifier :

1. l'ouverture de `/benchmark` sans paramètre et avec une fenêtre UTC explicite ;
2. la cohérence des populations, dénominateurs, niveaux de preuve et états de mesure ;
3. la reproduction du même rapport Markdown avec le script J8 ;
4. l'absence de payload brut, secret, URI complète ou donnée de session ;
5. le rendu explicite des mesures absentes et des limites historiques ;
6. l'absence de toute activité réseau J8 ;
7. l'arrêt propre de l'application et la conservation des verrous réseau.

État final après la readiness, la campagne initiale partielle, le correctif V15, la seconde
campagne complète, la revue ciblée et le gel du rapport :

```text
J8_HUMAN_QUALIFICATION=PASS
J8_HUMAN_REVIEW_LIMITATIONS=PRESENT
J8_HTML_TECHNICAL_QA=PASS
J8_MARKDOWN_REPRODUCIBILITY=PASS_BYTE_IDENTICAL
J8_TECHNICAL_QA_PROVIDER_CALLS=0
J8_PROVIDER_CAMPAIGN=SECOND_OWNER_GO_CONSUMED_2026_08_30
J8_PROVIDER_CAMPAIGN_RESULT=MEASURED_COMPLETED_20_OF_20
J8_FINAL_BENCHMARK_REPORT=docs/benchmark/J8-BENCHMARK-REPORT-20260830.md
J8_OWNER_CLOSURE_DECISION=GRANTED_BY_OWNER_2026_08_30_AFTER_FORMAL_AUDIT
J9_DECISION_TAKEN=NO
```

Le propriétaire a donné le 2026-08-30 le go distinct prévu. Il a été consommé par une seule fenêtre
exclusive et ne vaut ni autorisation de retry, ni autorisation de seconde campagne, ni décision de
clôture. La preuve détaillée est conservée dans
`docs/validation/J8-BOUNDED-CAMPAIGN-20260830.md`.

### 13.1 Campagne bornée du 2026-08-30

```text
J8_PROVIDER_CAMPAIGN_GO=GRANTED_BY_OWNER_2026_08_30
J8_PROVIDER_CAMPAIGN_GO_CONSUMED=YES
J8_WINDOW_FROM=2026-08-30T03:39:12.086771Z
J8_WINDOW_TO=2026-08-30T04:32:04.339732Z
J8_WINDOW_SEMANTICS=[FROM,TO)
J8_TARGET_PROVIDER_EVENT_ID=16691018
J8_TARGET_CANONICAL_ID=f4713f80-4769-3656-ba51-61d8ac1aa814
J8_TARGET_DATE=2026-08-15
J8_TARGET_PHASE_ID=15118
J8_TARGET_UNIQUE_TOURNAMENT_ID=824
J8_MAX_DIRECT_ATTEMPTS=30
J8_ACTUAL_DIRECT_ATTEMPTS=19
J8_DECLARED_UNITS=20
J8_BOUNDED_PATH_RESULT=PARTIAL
J8_RETRY_EXECUTED=NO
J8_SECOND_CAMPAIGN_AUTHORIZED=NO
```

J3 `SCHEDULED_EVENTS` a terminé quinze pages parsées en quinze tentatives. La découverte tournoi a
terminé en une tentative parsée et retenu huit événements canoniques. J4 phase 2 a terminé en une
tentative parsée, avec une latence de 3039 ms. J5 a déclaré ses trois familles, puis a terminé
`FAILED` après deux tentatives : statistiques `PARSED/COMPLETE` en 3012 ms, incidents
`SCHEMA_INCOMPATIBLE` en 95 ms et compositions `NOT_REACHED_AFTER_TERMINAL_FAILURE` sans tentative.
Le premier incident terminal a donc arrêté le parcours exactement comme prévu.

Le double export local figé au même `AsOf` est byte-identique : 15 201 octets, SHA-256
`d901790d1f05ddd32b92821bee51f11ae3e688ca3d929af36f667ac026b2934c` et hash de population
`da158fb04c8dc113a56e94e2bc7da6ad27278111af5cf8179b7476e5d8f1cc95`. Il rend
`PARTIAL`, `FULL_ATTEMPT_LEDGER`, dix-neuf réponses sur dix-neuf, dix-huit parsings compatibles et
une incompatibilité. Aucun dossier n'étant exploitable, les ratios d'appels par dossier restent
`NOT_MEASURED`.

### 13.2 Régression fonctionnelle J5 observée

La collecte incidents de ce dossier était exploitable avant J8 : l'ancienne preuve locale est
toujours acceptée hors ligne par `event-incidents-v14`. La nouvelle réponse J8 représente toutefois
les quatorze tirs au but non minutés avec `footballPassingNetworkAction` présent comme tableau vide,
alors que la règle héritée de V12 à V14 exige la propriété absente dans le contexte terminal
cohérent. Le résultat contient quatorze minutes d'action manquantes et un marqueur `PEN` hors
contexte, soit quinze problèmes.

Le parseur V14, le worker Playwright, le protocole IPC et le transport de réponse sont inchangés par
J8. L'audit J8 ne reçoit pas les octets pour les transformer : il écrit la tentative avant l'appel,
puis le statut, la latence et les références de persistance après réception. Une normalisation
contrefactuelle en mémoire des seuls tableaux vides rend les mêmes octets `PARSED/PARTIAL`, sans
persistance. La preuve soutient donc une nouvelle variante de représentation observée et une lacune
bornée du parseur strict, pas une mutation de payload causée par J8.

```text
J5_END_TO_END_AVAILABILITY_REGRESSION=OBSERVED
J5_J8_INSTRUMENTATION_CAUSALITY=NOT_SUPPORTED_BY_LOCAL_EVIDENCE
J5_OBSERVED_RESPONSE_VARIANT=EMPTY_FOOTBALL_PASSING_NETWORK_ACTION_ARRAY
J5_PROVIDER_SCHEMA_STATUS_AT_J8_FREEZE=NOT_VALIDATED_AFTER_SNAPSHOT_717
J5_PROVIDER_SCHEMA_CURRENT_STATUS=VALIDATED_BOUNDED_V15_EVENT_16691018_BY_WO_017
J5_CORRECTIVE_SCOPE=SEPARATE_WORK_ORDER_COMPLETED
J5_CORRECTIVE_WORK_ORDER_STATUS=VALIDATED
J5_CORRECTIVE_WORK_ORDER_LOCATION=docs/work_orders/completed
J5_CORRECTIVE_OWNER_CLOSURE=AUTHORIZED_2026_08_30
```

### 13.3 Correctif technique V15 sous Work Order séparé

Le propriétaire a ensuite autorisé WO-017 pour le seul correctif du parseur incidents. Le parseur
`event-incidents-v15` et la migration append-only V28 assimilent propriété absente et tableau
exactement vide uniquement dans la séance terminale non minutée déjà cohérente. Les listes non
vides incohérentes, les types erronés et les séances temporellement mixtes restent refusés.

Une sonde PostgreSQL read-only des octets exacts du snapshot 717 rend `PARSED/PARTIAL · 91%` sous
V15, sans persister de reparse. Elle ne modifie ni le snapshot, ni l'occurrence, et ne reclasse ni
l'unité ni son résultat historique V14 ; le coût et le hash de population restent inchangés. À cet instant,
WO-017 restait une readiness technique distincte : aucune reprise ou nouvelle campagne fournisseur
n'était autorisée et J8 restait `PARTIAL / READY_FOR_HUMAN_QUALIFICATION`.

Le propriétaire a ensuite autorisé sous WO-017 une campagne J5 corrective unique et postérieure à
la fenêtre J8, puis en a confié l'exécution locale à Codex sans modification de `.env`. Elle a
terminé `COMPLETED_LOCKED` après trois appels sans retry : statistiques `COMPLETE · 100%`
(`716`/`322`), incidents V15 `PARTIAL · 91%`, 35 incidents et `164/179` signaux (`717`/`324`),
puis compositions `PARTIAL · 99%` (`720`/`325`). La réponse incidents dédupliquée réobserve les
mêmes octets et les quatorze tableaux vides du snapshot 717. La classification V14 de l'unité J8,
les dix-neuf tentatives, la fenêtre, le double export et le hash de population restent inchangés.
Le ledger correctif est une nouvelle campagne J5 auditée, pas un retry ou une seconde campagne
benchmark. Son go est consommé et n'autorise aucun appel supplémentaire.

Le propriétaire a depuis jugé la qualification fonctionnelle V15 concluante et clôturé WO-017 au
statut `VALIDATED`. Cette décision ne modifie ni la fenêtre, ni le rapport `PARTIAL`, ni le statut
encore actif de J8 et n'autorise aucun nouvel appel fournisseur.

À l'issue de la campagne J8 initiale, l'application et le worker ont été arrêtés, le port 8087
libéré, les gates locaux remis à `false` et le verrou persistant confirmé par l'exporteur. Dans ce
parcours et immédiatement après son terminal, aucun retry, import, fallback, appel compositions ou
nouvelle campagne n'a été exécuté.

La revue humaine finale porte, lorsque le corpus le permet, sur trois dossiers distincts : le dossier
J8, le dossier direct non ciblé le plus récent en `PARTIAL` ou `UNAVAILABLE`, puis le dossier direct
non ciblé le plus récent avec `LATE_CORRECTION`. À défaut d'un archétype, elle utilise le dossier
direct le plus proche et consigne `ARCHETYPE_NOT_AVAILABLE`, sans appel supplémentaire. Chaque
source de contrôle est déclarée par libellé avant la revue. Seuls le libellé, l'heure du contrôle,
les verdicts `PASS`/`PARTIAL`/`FAIL`/`NOT_MEASURED` et une synthèse bornée sont versionnés. Les
conclusions restent séparées pour accessibilité, complétude, exactitude, fraîcheur, stabilité,
efficacité, maintenabilité, risque et valeur analytique ; toute dimension non observable reste
`NOT_MEASURED`.

Une campagne ne peut pas être déduite de la réussite des tests ou de l'ouverture de `/benchmark`.

## 14. Portes de statut et clôture

```text
IN_PROGRESS
  -> READY_FOR_HUMAN_QUALIFICATION
  -> QUALIFICATION_RUNNING_AFTER_SEPARATE_GO
  -> VALIDATED
```

Le statut final est `VALIDATED`. La seconde campagne est concluante et son rapport automatique est
`MEASURED`; la revue humaine ciblée est terminée avec ses limites `PARTIAL` et `NOT_MEASURED`, le
rapport final est gelé et la décision propriétaire de clôture est acquise. Aucune décision J9 n'est
prise.

Le passage à `VALIDATED` et le déplacement vers `docs/work_orders/completed` exigent tous les
éléments suivants :

- résultats techniques finaux réels et sans échec ;
- qualification humaine terminée ;
- campagne réelle bornée qualifiée après go séparé ; aucune réussite technique ou recette hors
  ligne ne peut la remplacer ;
- rapport final J8 complété dans `docs/benchmark` sans donnée interdite ;
- revue du diff et des secrets ;
- configuration sûre, listener et processus contrôlés après la séance ;
- décision explicite du propriétaire.

Tout push, toute Pull Request et toute fusion vers `main` exigent une demande explicite séparée et
ne constituent pas une preuve de clôture J8. Les preuves et la décision étant acquises, ce Work
Order rejoint `docs/work_orders/completed` sans autoriser de troisième campagne.

Le bloc de clôture ci-dessous est écrit exactement et entièrement prouvé après la campagne et la
revue humaine :

```text
COMPLETENESS_METRICS=AVAILABLE
LATENCY_METRICS=AVAILABLE
SCHEMA_STABILITY=MEASURED
ERROR_RATE=MEASURED
PROVIDER_CALL_COST=MEASURED
AUTOMATIC_POLLING=NO
```

## 15. Nouveau go propriétaire après qualification V15

Après clôture du correctif WO-017, le propriétaire indique que la campagne J8 peut se poursuivre.
Dans le contexte non ambigu de WO-016 et de l'échec V14 désormais corrigé, cette instruction est
consignée comme le nouveau go distinct exigé pour une seconde campagne end-to-end. Elle autorise
un seul parcours `J3 → découverte tournoi → J4 phase 2 → J5` exécuté localement par Codex, sans
geste manuel supplémentaire et sans modification de `.env`.

La campagne initiale, sa fenêtre, son hash et son rapport `PARTIAL` restent immuables. La nouvelle
campagne reçoit une fenêtre UTC exclusive et ne réutilise aucune tentative historique. Elle reste
soumise au plafond absolu de 30 tentatives, à l'ordre fermé, au délai minimal de trois secondes, à
la concurrence unitaire, à l'absence de retry et à l'arrêt au premier incident terminal.

La sélection read-only conserve le candidat déterministe prévu : Cittadella — Atalanta U23,
identifiant fournisseur `16691018`, identité canonique
`f4713f80-4769-3656-ba51-61d8ac1aa814`, phase `15118`, tournoi routé `824`, saison `99790` et date
J3 `2026-08-15`. Les caches J3 et tournoi exacts ont expiré naturellement depuis plus de 600
secondes ; aucune suppression, invalidation ou dérogation de cache n'a été effectuée.

```text
J8_SECOND_PROVIDER_CAMPAIGN_GO=GRANTED_BY_OWNER_2026_08_30
J8_SECOND_PROVIDER_CAMPAIGN_GO_SCOPE=ONE_END_TO_END_BOUNDED_PATH
EXECUTION_ACTOR=CODEX_LOCAL_UI
WINDOW_FROM=2026-08-30T09:24:51.0887925Z
WINDOW_TO=PENDING_TERMINAL
WINDOW_SEMANTICS=[FROM,TO)
TARGET_PROVIDER_EVENT_ID=16691018
TARGET_CANONICAL_EVENT_ID=f4713f80-4769-3656-ba51-61d8ac1aa814
TARGET_DATE=2026-08-15
TARGET_PHASE_ID=15118
TARGET_ROUTE_TOURNAMENT_ID=824
TARGET_SEASON_ID=99790
J3_TARGET_CACHE_ROWS=15
J3_TARGET_CACHE_EXPIRED_600_SECONDS=YES
TOURNAMENT_TARGET_CACHE_ROWS=1
TOURNAMENT_TARGET_CACHE_EXPIRED_600_SECONDS=YES
BASELINE_J8_CAMPAIGNS=5
BASELINE_J8_UNITS=23
BASELINE_J8_ATTEMPTS=22
BASELINE_J8_UNIT_RESULTS=23
MAXIMUM_DIRECT_ATTEMPTS=30
PROVIDER_RETRY=NO
POLLING=NO
ENV_FILE_MUTATION=NO
ADDITIONAL_CAMPAIGN_AFTER_THIS_GO=NO
```

La readiness détaillée est conservée dans
`docs/validation/J8-SECOND-BOUNDED-CAMPAIGN-READINESS-20260830.md`. Aucune requête fournisseur n'a
été exécutée pendant la sélection, le contrôle des caches ou la consignation du go.

## 16. Résultat de la seconde campagne et passage en revue ciblée

Le go de la section 15 a été consommé par une seule instance locale avec le profil combiné exact à
six endpoints, injecté uniquement dans l'arbre du lanceur. `.env` est resté inchangé. La fenêtre
exclusive est `[2026-08-30T09:24:51.0887925Z,2026-08-30T09:44:03.2695965Z)` et l'`asOf` gelé vaut
`2026-08-30T09:44:03.2695965Z`.

J3 a terminé quinze pages et quinze tentatives. La découverte tournoi, J4 phase 2 et les trois
familles J5 ont ensuite terminé dans l'ordre, pour un total de vingt unités et vingt tentatives.
Toutes ont reçu HTTP 200 et produit `PARSED`. Les incidents utilisent `event-incidents-v15` et
restent `PARTIAL · 91%` en complétude, sans incompatibilité; les compositions ont été atteintes et
sont `PARTIAL · 99%`. Aucun retry, import, fallback, arrêt opérateur ou tentative incomplète n'est
présent.

```text
J8_SECOND_PROVIDER_CAMPAIGN_GO_CONSUMED=YES
J8_SECOND_PROVIDER_CAMPAIGN_RESULT=MEASURED_COMPLETED
J8_SECOND_PROVIDER_CAMPAIGN_CAMPAIGNS=4
J8_SECOND_PROVIDER_CAMPAIGN_UNITS=20
J8_SECOND_PROVIDER_CAMPAIGN_ATTEMPTS=20
J8_SECOND_PROVIDER_CAMPAIGN_RESPONSES=20
J8_SECOND_PROVIDER_CAMPAIGN_PARSED=20
J8_SECOND_PROVIDER_CAMPAIGN_ERRORS=0
J8_SECOND_PROVIDER_CAMPAIGN_RETRY=NO
J8_SECOND_PROVIDER_CAMPAIGN_ADDITIONAL_CALLS_AUTHORIZED=NO
```

Le double export porte sur la même population, mesure `FULL_ATTEMPT_LEDGER` et est byte-identique :
15 202 octets, SHA-256
`ffed40714a7c13f79273d7ddfacd15b02fdd877a2e946f6b843ba63e6b8cfb25` et hash de population
`c61b3ef3a9ac12f94d787da8c396dae58e4208a6f04aa240538e38eac5ab4726`. Il mesure 20/20 réponses,
20/20 parsings compatibles, zéro refus, 404, erreur opérationnelle ou tentative incomplète. Un
dossier sur un est exploitable, aucun n'est strictement complet; les coûts exacts sont 16 appels de
découverte, 4 appels marginaux et 20 appels effectifs par dossier exploitable.

Après le terminal, l'instance a été arrêtée. Un redémarrage inerte avec tous les connecteurs et
Playwright à `false` a confirmé J3 au verrou de démarrage et les préparations J4/J5 désactivées,
puis l'application a été arrêtée à nouveau et le port 8087 libéré.

La preuve détaillée est
`docs/validation/J8-SECOND-BOUNDED-CAMPAIGN-20260830.md`. Le rapport automatique suffit pour les
métriques obligatoires et son bloc exact est gelé sous
`docs/benchmark/J8-BENCHMARK-REPORT-20260830.md`. La revue humaine des trois dossiers, les libellés
`CONTROL_SOURCE_ABSENT` et `EXTERNAL_COMPARISON_ABSENT` et la décision propriétaire sont consignés.
Les dimensions non observables restent `NOT_MEASURED`; aucune comparaison externe n'est inventée.

La sélection locale des trois dossiers et les limites de comparaison sont consignées dans
`docs/validation/J8-TARGETED-HUMAN-REVIEW-READINESS-20260830.md`; les verdicts finaux minimisés sont
dans `docs/validation/J8-TARGETED-HUMAN-REVIEW-20260830.md`.

```text
J8_TECHNICAL_METRIC_GATE=PASS
J8_CLOSURE_BLOCK_WRITTEN=YES
J8_HUMAN_TARGETED_REVIEW=PASS
J8_HUMAN_REVIEW_LIMITATIONS=PRESENT
J8_FINAL_REPORT=docs/benchmark/J8-BENCHMARK-REPORT-20260830.md
J8_OWNER_CLOSURE_DECISION=GRANTED_BY_OWNER_2026_08_30_AFTER_FORMAL_AUDIT
J8_WORK_ORDER_STATUS=VALIDATED
J9_DECISION_TAKEN=NO
```

## 17. Revue finale, gel et clôture

Le 2026-08-30, le propriétaire autorise la transition vers `VALIDATED` si toutes les étapes et tous
les critères sont formellement prouvés. L'audit final ne trouve aucun bloqueur technique, réseau,
de persistance ou de sécurité. Il accepte l'absence de comparateur externe sous les codes
`CONTROL_SOURCE_ABSENT` et `EXTERNAL_COMPARISON_ABSENT`; exactitude, maintenabilité et valeur
analytique restent donc `NOT_MEASURED`. La complétude, la fraîcheur, l'efficacité et le risque
restent `PARTIAL`; accessibilité et stabilité sont `PASS`. Ces résultats ne constituent aucune
décision J9.

Le dernier export non-Web force tous les connecteurs à `false`, réalise zéro appel fournisseur et
reproduit le bloc automatique de 15 202 octets, SHA-256
`ffed40714a7c13f79273d7ddfacd15b02fdd877a2e946f6b843ba63e6b8cfb25`, avec le hash de population
`c61b3ef3a9ac12f94d787da8c396dae58e4208a6f04aa240538e38eac5ab4726`. Les 15 202 premiers octets
du rapport final sont byte-identiques; la section humaine commence après le marqueur automatique.

```text
HUMAN_TARGETED_REVIEW=PASS
HUMAN_REVIEW_LIMITATIONS=PRESENT
CONTROL_SOURCE_LABEL=CONTROL_SOURCE_ABSENT
EXTERNAL_COMPARISON_SOURCE=EXTERNAL_COMPARISON_ABSENT
FINAL_REPORT=docs/benchmark/J8-BENCHMARK-REPORT-20260830.md
FINAL_VALIDATION=docs/validation/J8-FINAL-VALIDATION-20260830.md
AUTOMATIC_BLOCK_SHA256=ffed40714a7c13f79273d7ddfacd15b02fdd877a2e946f6b843ba63e6b8cfb25
FINAL_REPORT_SHA256=6d9a46b4391beffe9c1c54591089f0b035a5ed81f9522c03998a0feff18e7e52
J8_CLOSURE_CRITERIA=MET
J8_STATUS=VALIDATED
J9_DECISION_TAKEN=NO
ADDITIONAL_PROVIDER_CAMPAIGN_AUTHORIZED=NO
```

```text
COMPLETENESS_METRICS=AVAILABLE
LATENCY_METRICS=AVAILABLE
SCHEMA_STABILITY=MEASURED
ERROR_RATE=MEASURED
PROVIDER_CALL_COST=MEASURED
AUTOMATIC_POLLING=NO
```

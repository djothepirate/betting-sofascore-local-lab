# WO-SS-20260829-016 — Benchmark local reproductible J8

- **Statut :** `READY_FOR_HUMAN_QUALIFICATION`
- **Date d'ouverture :** 2026-08-29
- **Date de démarrage :** 2026-08-29
- **Date de validation :** `NOT_RUN`
- **Décision de clôture :** `NOT_GRANTED`
- **Prérequis :** J7 fusionné et Work Orders 009 à 015 validés
- **Base locale :** `67268d805a4ba8c7d4706be7c18f6ff78d3ec1fd`
- **Jalon :** J8 — Benchmark
- **Branche :** `codex/j8-benchmark`
- **ADR applicable :** `ADR-SS-001 v1.4`
- **Nouveau parcours ou endpoint fournisseur :** `NONE`
- **Appel fournisseur pendant l'implémentation :** `NOT_AUTHORIZED`
- **Appel fournisseur pendant les tests automatisés :** `NOT_AUTHORIZED`
- **Campagne fournisseur J8 :** `NOT_AUTHORIZED_NOT_RUN_REQUIRES_SEPARATE_OWNER_GO`
- **Qualification humaine :** `NOT_RUN`
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
arbitraire n'est accepté. L'application normale doit être arrêtée et aucune campagne ne doit être
active ; le script affiche le chemin final et son SHA-256.

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

Le registre J8 prospectif contient la campagne, toutes ses unités attendues et chaque tentative
fournisseur, y compris une issue terminale sans snapshot. Ce niveau permet de mesurer exactement le
nombre d'appels, le taux d'erreur, les refus, les indisponibilités, la couverture des unités et le
coût par dossier exploitable dans la fenêtre de cette campagne.

### 6.2 `RESPONSE_ONLY`

Les occurrences V21 `INSERTED` et `DEDUPLICATED` liées à un snapshot
`DIRECT_LOCAL_ENDPOINT` prouvent uniquement les réponses ayant atteint la persistance. Elles
permettent de mesurer la latence, HTTP, compatibilité du parseur et déduplication de cette population.
Elles ne prouvent pas l'absence d'un timeout ou d'un arrêt antérieur à la persistance. Les taux
d'essais ou d'erreurs restent donc `PARTIAL` et leur dénominateur est nommé « réponses persistées ».

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
- après trim, chaque valeur contient 1 à 64 caractères, aucun contrôle, finit par `Z` et est
  acceptée par `Instant.parse` ;
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

Avec `RESPONSE_ONLY`, seul un taux sur réponses persistées est calculable et reste `PARTIAL`. Avec
`LEGACY_BASELINE`, le taux d'erreur par tentative est `NOT_MEASURED`.

### 8.5 Corrections tardives

Les corrections sont dérivées des classifications J6. Seules les observations de provenance
fournisseur peuvent contribuer à `LATE_ENRICHMENT` et `LATE_CORRECTION`. Une fixture synthétique ou
un reparsing local reste visible dans sa catégorie propre et n'est jamais assimilé à une correction
fournisseur.

### 8.6 Coût en appels

Le dénominateur est le nombre de `provider_event_id` distincts ciblés par une unité
`GUARDED_PROVIDER` J4 phase 2 ou J5. Un dossier direct exploitable possède, à `asOf`, un état et un
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
J8_STATUS=READY_FOR_HUMAN_QUALIFICATION
COMPLETENESS_METRICS=AVAILABLE
LATENCY_METRICS=AVAILABLE
SCHEMA_STABILITY=MEASURED_OR_EXPLICITLY_NOT_MEASURED
ERROR_RATE=EVIDENCE_LEVEL_AWARE
PROVIDER_CALL_COST=EVIDENCE_LEVEL_AWARE
LATE_CORRECTIONS=MEASURED_OR_EXPLICITLY_NOT_MEASURED
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

## 12. Validation technique requise

Avant toute qualification humaine, exécuter sur le diff final :

```powershell
.\mvnw.cmd clean verify
.\mvnw.cmd -Pintegration-tests verify
.\scripts\Verify-Local.ps1 -WithIntegrationTests
git diff --check
```

Les résultats réels doivent être consignés dans
`docs/validation/J8-TECHNICAL-READINESS-20260829.md`. Aucun résultat `PASS` n'est présumé par ce
Work Order. Les tests Maven, y compris PostgreSQL/Testcontainers, doivent exécuter zéro appel
SofaScore et ne doivent pas démarrer Playwright.

## 13. Qualification humaine et campagne éventuelle

La qualification humaine locale doit vérifier :

1. l'ouverture de `/benchmark` sans paramètre et avec une fenêtre UTC explicite ;
2. la cohérence des populations, dénominateurs, niveaux de preuve et états de mesure ;
3. la reproduction du même rapport Markdown avec le script J8 ;
4. l'absence de payload brut, secret, URI complète ou donnée de session ;
5. le rendu explicite des mesures absentes et des limites historiques ;
6. l'absence de toute activité réseau J8 ;
7. l'arrêt propre de l'application et la conservation des verrous réseau.

État initial :

```text
J8_HUMAN_QUALIFICATION=NOT_RUN
J8_PROVIDER_CAMPAIGN=NOT_AUTHORIZED
J8_PROVIDER_CAMPAIGN_RESULT=NOT_RUN
J8_FINAL_BENCHMARK_REPORT=NOT_CREATED
J8_OWNER_CLOSURE_DECISION=NOT_GRANTED
```

Si le propriétaire souhaite la campagne fournisseur prospective J8, il doit fournir une décision
séparée avant la première requête fournisseur. Le protocole déjà retenu, mais non autorisé par ce
Work Order, est le suivant : sélectionner localement le dernier événement J7 `HUMAN_VALIDATED`,
terminal et possédant les cinq composants directs, avec identifiant fournisseur croissant comme
départage ; arrêter avant réseau s'il n'existe aucun candidat ; vérifier en lecture seule
l'expiration naturelle des caches J3 et tournoi ; réserver une fenêtre UTC exclusive sans autre
campagne manuelle ; puis exécuter dans l'ordre J3 `SCHEDULED_EVENTS` (pages atteintes, plafond 25),
une découverte `TOURNAMENT_SCHEDULED_EVENTS`, J4 phase 2 `EVENT_DETAILS`, et J5
`STATISTICS`/`INCIDENTS`/`LINEUPS`. Le plafond absolu est 30 tentatives (`25 + 1 + 1 + 3`), avec
trois secondes minimum, concurrence un, aucun retry et arrêt au premier incident terminal, refus,
incompatibilité, contenu inattendu ou arrêt opérateur. Le contexte Playwright est neuf et non
persistant, sans profil, cookie, `storageState`, HAR, trace, vidéo, capture ni téléchargement. Un
cache hit imprévu, une panne ou un dossier inexploitable donne `PARTIAL`/`NOT_MEASURED` ; aucune
seconde campagne n'est lancée sans nouvelle autorisation.

La revue humaine future porte, lorsque le corpus le permet, sur trois dossiers distincts : le dossier
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

Le statut courant est `READY_FOR_HUMAN_QUALIFICATION`. Il signifie que le périmètre documentaire et
la capacité technique sont proposés à la recette ; il ne signifie ni campagne exécutée, ni rapport
final accepté, ni décision J9.

Le passage à `VALIDATED` et le déplacement vers `docs/work_orders/completed` exigent tous les
éléments suivants :

- résultats techniques finaux réels et sans échec ;
- qualification humaine terminée ;
- campagne réelle soit qualifiée après go séparé, soit explicitement déclarée non requise par le
  propriétaire pour la clôture ;
- rapport final J8 complété dans `docs/benchmark` sans donnée interdite ;
- revue du diff et des secrets ;
- configuration sûre, listener et processus contrôlés après la séance ;
- branche poussée et Pull Request revue ;
- décision explicite du propriétaire.

La fusion vers `main` reste une décision séparée. Ce Work Order demeure actif et ne doit pas être
archivé tant que ces preuves et cette décision ne sont pas acquises.

Le bloc de clôture ci-dessous doit être écrit exactement et entièrement prouvé après la campagne et
la revue humaine ; il n'est ni écrit comme résultat courant, ni réputé acquis par la readiness :

```text
COMPLETENESS_METRICS=AVAILABLE
LATENCY_METRICS=AVAILABLE
SCHEMA_STABILITY=MEASURED
ERROR_RATE=MEASURED
PROVIDER_CALL_COST=MEASURED
AUTOMATIC_POLLING=NO
```

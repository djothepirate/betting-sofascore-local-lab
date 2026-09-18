# PB-C02 — Taux et latences au point d’observation `asOf`

**Sur les six tentatives du cas : réponse 66,67 %, compatibilité de parsing 50,00 %, refus HTTP 16,67 %, indisponibilité 16,67 % et erreur opérationnelle 66,67 %. Les quatre latences de réponse donnent min/P50/P95/max = 50/60/200/200 ms.** La synthèse des tentatives reste `PARTIAL`, car A6 n’a pas de résultat terminal observable.

Statuts conservés : `EXPERIMENTAL`, `LOCAL_ONLY`, `NOT_PRODUCTION_APPROVED`, `NO_CRITICAL_DEPENDENCY`.

## 1. Population, provenance et portée temporelle

La source est `input.json`, de provenance explicite `SYNTHETIC_EVALUATION_INPUT_NOT_PROVIDER_DATA`. Les résultats sont des calculs sur un cas synthétique, sans observation d’une capacité réelle du fournisseur.

- Endpoint unique : `EVENT_DETAILS`.
- Unité de comptage : une tentative A1 à A6, soit **6 tentatives**. Chacune appartient à une campagne distincte G1 à G6 de type `J4_EVENT_DETAILS_PHASE2`, avec `maximum_units=1` et aucun retry. Il ne s’agit donc pas d’une campagne de six unités dépassant son plafond.
- Modèle de calcul : formules de la strate `FULL_ATTEMPT_LEDGER`, appliquées aux six tentatives déclarées du cas. Les tentatives sans réponse sont conservées. Aucune population `RESPONSE_ONLY` ou `LEGACY_BASELINE` n’est ajoutée. Cette interprétation arithmétique ne valide pas un registre réellement persisté.
- Point de vue : les états et preuves décrits par l’entrée sont pris comme observables à son `asOf`. **Aucune valeur UTC de `asOf`, aucune borne `[from,to)` ni date de tentative/résultat n’est fournie.** Les calculs sur ces six lignes sont reproductibles ; leur sélection temporelle réelle ne peut pas être vérifiée. L’heure de rédaction de la trace n’est pas un substitut à `asOf`.

## 2. Classement des tentatives

| Tentative / campagne | Issue connue à `asOf` | Réponse / HTTP | Parsing éligible | Refus HTTP | Indisponibilité | Erreur opérationnelle | Latence retenue |
|---|---|---|---|---|---|---|---:|
| A1 / G1 | `PARSED` ; preuve normalisée à `asOf` | Oui / 200 | Oui | Non | Non | Non | 100 ms |
| A2 / G2 | `SCHEMA_INCOMPATIBLE` ; preuve de parsing annoncée | Oui / 200 | Oui | Non | Non | Oui | 200 ms |
| A3 / G3 | `ENDPOINT_UNAVAILABLE` | Oui / 404 | Non | Non | Oui | Non | 50 ms |
| A4 / G4 | `PERSISTENCE_FAILURE` | Oui / 429 | Non | Oui | Non | Oui, une seule fois | 60 ms |
| A5 / G5 | `TRANSPORT_FAILURE` | Non / absent | Non | Non observé | Non observée | Oui | Absente, exclue |
| A6 / G6 | Résultat absent : code dérivé `INCOMPLETE_ATTEMPT` | Aucune réponse connue | Non à ce point | Non observé | Non observée | Oui | Absente, exclue |

Les réponses éligibles au parsing sont **A1 et A2**. Le code retient les issues `PARSED`, `SCHEMA_INCOMPATIBLE` et `UNEXPECTED_CONTENT` ; un HTTP 200 seul ne définit pas ce dénominateur.

Le **404 de A3** constitue une réponse et une indisponibilité. Il est exclu du parsing et de l’erreur opérationnelle. Le **429 de A4** est un refus détecté par son statut HTTP, même si son issue terminale est une erreur de persistance. A4 apparaît dans les compteurs descriptifs « refus » et « autre erreur locale », mais le taux d’erreur opérationnelle compte les tentatives distinctes : **A2, A4, A5, A6**, soit quatre et jamais cinq.

A6 reste dans le dénominateur. `INCOMPLETE_ATTEMPT` décrit l’absence de résultat à `asOf` : ce n’est ni une nouvelle issue terminale SQL ni une preuve de timeout. Le Lab l’inclut néanmoins dans l’erreur opérationnelle et rend la synthèse partielle.

## 3. Taux et dénominateurs

Les pourcentages suivent `100 × numérateur / dénominateur`, arrondis à deux décimales selon `HALF_UP`, comme `rate()` dans le service. Tous portent sur `EVENT_DETAILS` au point d’observation décrit ci-dessus.

| Mesure | Numérateur identifié | Dénominateur | Calcul et valeur | État et limite |
|---|---|---:|---|---|
| Taux de réponse | 4 : A1, A2, A3, A4 | 6 tentatives | `4 / 6 × 100` = **66,67 %** | `PARTIAL` : A6 sans résultat terminal |
| Compatibilité / succès de parsing | 1 : A1 | 2 réponses éligibles : A1, A2 | `1 / 2 × 100` = **50,00 %** | `MEASURED` sur les deux preuves de parsing déclarées à `asOf` ; ne décrit pas toutes les tentatives |
| Taux de refus HTTP 401/403/429 | 1 : A4, HTTP 429 | 6 tentatives | `1 / 6 × 100` = **16,67 %** | `PARTIAL` : résultat de A6 encore absent ; refus connu de A4 conservé |
| Taux d’indisponibilité de l’endpoint | 1 : A3 | 6 tentatives de `EVENT_DETAILS` | `1 / 6 × 100` = **16,67 %** | `PARTIAL` : résultat de A6 encore absent ; 404 séparé |
| Taux d’erreur opérationnelle | 4 : A2, A4, A5, A6 | 6 tentatives | `4 / 6 × 100` = **66,67 %** | `PARTIAL` : inclut A6 incomplète et compte A4 une seule fois |

Le succès parsé est donc un **compte de 1**. Le rapport supplémentaire `1 / 6 = 16,67 %` décrirait la part des tentatives abouties à un succès parsé, et `1 / 4 = 25,00 %` la part de toutes les réponses portant ce succès. Aucun de ces ratios ne remplace la compatibilité J8, qui vaut **1/2**.

Comptes complémentaires : 1 incompatibilité de schéma, 0 contenu inattendu observé, 1 erreur de transport, 1 autre erreur locale terminale et 1 tentative incomplète. Ces catégories descriptives ne doivent pas être additionnées avec les refus pour recalculer l’erreur opérationnelle, car A4 les fait se recouper.

## 4. Distribution des latences de réponse

Population admissible : réponses directes portant une latence présente et non négative. Les réponses 404 et 429 sont admissibles. Le succès métier, le succès de parsing ou la réussite de la persistance ne sont pas des conditions supplémentaires de cette distribution.

Échantillon trié : **50 ms (A3), 60 ms (A4), 100 ms (A1), 200 ms (A2)**.

| Mesure | Formule ou rang | Valeur |
|---|---|---:|
| Taille `n` | Nombre de latences admissibles | **4** |
| Minimum | Première valeur triée | **50 ms** |
| P50 | Rang `ceil(0,50 × 4) = 2` | **60 ms** |
| P95 | Rang `ceil(0,95 × 4) = 4` | **200 ms** |
| Maximum | Dernière valeur triée | **200 ms** |

La convention J8 est **nearest-rank avec rang à partir de 1**, sans interpolation. Le P50 n’est donc pas la moyenne des deux valeurs centrales : 80 ms ne serait pas le P50 J8.

**État de la distribution calculée : `MEASURED` pour ces quatre réponses**, avec une couverture de latence de **4/4 réponses connues**. Elles représentent **4/6 tentatives** ; les durées de A5 et A6 sont absentes et ne deviennent jamais zéro. La synthèse de l’endpoint et des tentatives reste `PARTIAL`. Une distribution des durées de toutes les tentatives, incluant échecs sans réponse et tentative ouverte, reste `NOT_MEASURED` avec cette entrée.

## 5. Limites et références

- Les états de mesure qualifient les calculs du cas fourni. La capacité réelle de SofaScore reste **`NOT_MEASURED`** : aucune collecte ou preuve fournisseur réelle n’est présente.
- La compatibilité porte sur deux cas et les percentiles sur quatre réponses. Ils n’établissent ni stabilité dans le temps, ni performance généralisable, ni quota fournisseur. Le 429 ne démontre pas sa cause.
- Les horodatages, versions de parseur, identifiants complets du registre et preuves persistées ne sont pas fournis. L’intégrité d’un véritable rapport J8, la fermeture des campagnes et le filtrage à `asOf` ne sont pas validables ici. Aucun hash de population J8 n’est inventé.
- Le lecteur de persistance et le modèle de fenêtre ne figurent pas dans le corpus autorisé. Aucune référence sortante n’a été suivie. La lecture du service vérifie les formules ; elle ne prouve pas l’exécution du service ou de ses tests.
- La complétude J5, les dossiers exploitables, la fraîcheur source et le coût par dossier ne sont pas déduits de ces seules six tentatives.

Sources utilisées pour les résultats :

1. [Entrée synthétique du cas](C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/pb-evaluations/run-01/PB-C02/input.json).
2. [Définitions J8](C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/pb-evaluations/run-01/PB-C02/corpus/docs/architecture/J8-BENCHMARK-METRICS.md), sections 2 à 7 : états, temporalité, tentatives incomplètes, strates, taux et latence.
3. [J8BenchmarkService.java](C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/pb-evaluations/run-01/PB-C02/corpus/src/main/java/com/bettingproject/sofascorelocal/application/benchmark/J8BenchmarkService.java:416), `callSummary()` et `endpointMetrics()` ; helpers `isParsingEligible()`, `isOperationalError()`, `isRefusal()`, `rate()`, `latency()` et `percentile()`.

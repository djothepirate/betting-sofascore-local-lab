# PB-C03 — Complétude pondérée et distribution des statuts

**Résultat : la complétude pondérée est de 83,33 % (10 signaux présents sur 12 attendus). La population contient 4 observations : 0 COMPLETE, 2 PARTIAL, 1 EMPTY_VALID et 1 UNAVAILABLE. Le groupe de complétude est PARTIAL selon les règles du service J8.**

Statuts du laboratoire : `EXPERIMENTAL`, `LOCAL_ONLY`, `NOT_PRODUCTION_APPROVED`, `NO_CRITICAL_DEPENDENCY`.

## Périmètre et provenance

Le calcul applique les définitions du Lab aux seules lignes O1 à O4 de `input.json`, pour la famille `EVENT_INCIDENTS`. L'unité de la distribution est l'observation fournie ; l'unité de la complétude numérique est le signal. Les quatre identifiants sont distincts.

La provenance déclarée est `SYNTHETIC_EVALUATION_INPUT_NOT_PROVIDER_DATA`. Ces résultats sont un calcul reproductible sur un jeu synthétique, sans observation réelle de capacité fournisseur.

Les dimensions `competition`, `season` et `eventStatus` sont toutes absentes (`null`) : le groupe est donc **EVENT_INCIDENTS / UNKNOWN / UNKNOWN / UNKNOWN**, conformément à la section 8 du document métrique et à `explicitDimension` dans le service. Aucune compétition, saison ou phase de match n'est déduite.

La fenêtre UTC `[from,to)` et `asOf` ne sont pas fournis. La population annoncée est exactement le tableau d'entrée, sans filtre temporel inventé. L'heure de cette rédaction ne remplace pas un `asOf` des données.

## 1. Complétude pondérée

La section 8 du contrat définit le rapport des sommes pour les observations dont `expectedSignals > 0`. La méthode `completeness` vérifie également la présence de `presentSignals`.

| Observation | Statut fourni | Signaux présents | Signaux attendus | Contribution au ratio |
|---|---|---:|---:|---|
| O1 | PARTIAL | 1 | 2 | Éligible : 1 au numérateur et 2 au dénominateur |
| O2 | PARTIAL | 9 | 10 | Éligible : 9 au numérateur et 10 au dénominateur |
| O3 | EMPTY_VALID | 0 | 0 | Exclue du ratio : aucun signal attendu, dénominateur nul |
| O4 | UNAVAILABLE | inconnu (`null`) | inconnu (`null`) | Exclue du ratio : compteurs absents |

La formule et ses valeurs sont :

```text
Lignes éligibles E = {O1, O2}
Numérateur   = somme des signaux présents dans E = 1 + 9 = 10
Dénominateur = somme des signaux attendus dans E = 2 + 10 = 12
Complétude pondérée = 100 × 10 / 12 = 83,333… % → 83,33 %
```

Le service arrondit le pourcentage à deux décimales avec `HALF_UP`. La moyenne simple des pourcentages individuels serait `(50 % + 90 %) / 2 = 70 %` ; elle ne respecte pas la formule du Lab. Les observations sont pondérées par leurs nombres de signaux attendus : O2 apporte dix signaux attendus, contre deux pour O1.

**O3 reste une liste vide valide et disponible ; aucun pourcentage de 0 % ou 100 % ne lui est attribué à partir de 0/0. O4 conserve son statut UNAVAILABLE et ses inconnus ; elle ne devient jamais une complétude de 0 %.** Ces deux observations restent présentes dans la distribution ci-dessous. Si aucune ligne n'avait de dénominateur positif éligible, la couverture numérique serait absente, `NOT_MEASURED`, et non égale à zéro.

## 2. Distribution des statuts

Le dénominateur commun est **4 observations**, y compris O3 et O4. Pour chaque statut : `100 × nombre d'observations du statut / 4`.

| Statut | Identifiants | Numérateur / dénominateur | Part |
|---|---|---:|---:|
| COMPLETE | Aucun | 0 / 4 | 0,00 % |
| PARTIAL | O1, O2 | 2 / 4 | 50,00 % |
| EMPTY_VALID | O3 | 1 / 4 | 25,00 % |
| UNAVAILABLE | O4 | 1 / 4 | 25,00 % |
| **Total** | **O1 à O4** | **4 / 4** | **100,00 %** |

Le taux `COMPLETE` est donc 0 %, même si la couverture des signaux éligibles atteint 83,33 %. Ce sont deux mesures distinctes. `EMPTY_VALID` ne devient pas `COMPLETE`, et `UNAVAILABLE` ne devient pas une famille absente ou un statut incompatible.

## 3. États des mesures et limites

| Mesure | Population / unité | Valeur | État et limite |
|---|---|---|---|
| Distribution arithmétique des statuts fournis | O1 à O4 / observation | 0 %, 50 %, 25 %, 25 % | `MEASURED` pour ce tableau synthétique : tous les statuts et le dénominateur sont connus. Le bloc de complétude J8 qui les contient conserve néanmoins l'état `PARTIAL`. |
| Couverture pondérée des signaux | O1 et O2 / signal | 10 / 12 = 83,33 % | Calcul défini ; groupe J8 `PARTIAL`. Les inconnus de O4 ne sont pas couverts par ce ratio. |
| Ventilation par compétition, saison et état du match | O1 à O4 / observation | Un groupe UNKNOWN / UNKNOWN / UNKNOWN | `PARTIAL` : les dimensions nécessaires à une ventilation plus fine sont absentes. |
| Capacité fournisseur réelle | Aucune population fournisseur attestée | Aucune valeur | `NOT_MEASURED` : données explicitement synthétiques. |

Dans `J8BenchmarkService.completeness`, une dimension `UNKNOWN` suffit à rendre le groupe `PARTIAL`. Les compteurs absents de O4 déclenchent aussi cette règle. Le statut de contenu `PARTIAL` de O1/O2 et l'état de mesure `PARTIAL` du groupe désignent deux notions différentes.

L'entrée ne fournit ni preuves de provenance directe, ni indicateur de présence d'observation normalisée, ni snapshots, versions de parseur, strate de preuve, ni dates. Le service possède des filtres d'admissibilité en amont de l'agrégation ; leur satisfaction ne peut pas être vérifiée avec ce tableau. On applique ici la règle de calcul aux quatre observations données, sans prétendre avoir exécuté un rapport J8 réel ou reconstitué `FULL_ATTEMPT_LEDGER`, `RESPONSE_ONLY` ou `LEGACY_BASELINE`.

Les identifiants O1 à O4 permettent de reproduire ce calcul restreint. Ils ne suffisent pas à reproduire le hash canonique de population J8. Ces données ne permettent pas non plus de qualifier un dossier complet, qui exige les autres composants directs du Lab.

## Sources consultées et vérification

- `input.json` : données O1 à O4, endpoint, dimensions et provenance.
- `candidate/SKILL.md` : méthode de cadrage et limites de preuve.
- [J8-BENCHMARK-METRICS.md](C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/pb-evaluations/run-01/PB-C03/corpus/docs/architecture/J8-BENCHMARK-METRICS.md:305), section 8, lignes 305 à 318 : ratio des sommes, dimensions UNKNOWN et conservation des statuts. Section 2 : états de mesure et absence distincte de zéro.
- [J8BenchmarkService.java](C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/pb-evaluations/run-01/PB-C03/corpus/src/main/java/com/bettingproject/sofascorelocal/application/benchmark/J8BenchmarkService.java:678), méthode `completeness`, lignes 678 à 780 : filtres, comptes, arrondi et état PARTIAL ; `explicitDimension`, lignes 1573 à 1575 : UNKNOWN.

Le calcul a été vérifié localement à partir du JSON : 4 observations, 2 lignes numériques éligibles, sommes 10 et 12, couverture 83,33 %, effectifs 0/2/1/1 et somme des parts 100 %. Aucun test applicatif n'a été exécuté. Aucun réseau, serveur, DB, Docker ou collecte n'a été utilisé. Les sources sont restées en lecture seule ; aucune référence sortante n'a été ouverte.
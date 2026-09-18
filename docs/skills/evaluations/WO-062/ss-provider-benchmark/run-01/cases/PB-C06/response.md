# PB-C06 — Délais des changements tardifs J6 selon J8

**Résultat : V2 = 60 000 ms (60 s), V3 = 120 000 ms (120 s).** La distribution commune contient deux échantillons : **min = P50 = 60 000 ms ; P95 = max = 120 000 ms**. V1 ne contribue à aucun compte ni délai de changement tardif. Dans la variante V3 sans heure d’état direct antérieur, le garde J8 lève une exception et ne produit pas de rapport normal pour cette population.

Statuts : `EXPERIMENTAL`, `LOCAL_ONLY`, `NOT_PRODUCTION_APPROVED`, `NO_CRITICAL_DEPENDENCY`.

## Population et portée

La population est celle de `input.json` : événement **1001**, transitions **V1, V2, V3**, reçues le **1er septembre 2026**, entre 10:02 et 10:07 UTC. Cette étendue descriptive n’est pas une fenêtre J8 `[from,to)` : les bornes et `asOf` sont absents. Les calculs portent sur les transitions explicitement soumises.

La provenance globale est `SYNTHETIC_EVALUATION_INPUT_NOT_PROVIDER_DATA`. Les mentions `DIRECT_LOCAL_ENDPOINT` décrivent le scénario à évaluer ; elles ne prouvent aucune observation réelle du fournisseur. Les états `MEASURED` ci-dessous concernent les calculs du cas, sous l’hypothèse fournie d’appariements et d’états directs prouvés. Les classifications J6 sont reprises telles quelles, sans les réimplémenter ni les remplacer par une classification synthétique.

## Origine et calcul du délai

J8 utilise la sous-série `DIRECT_LOCAL_ENDPOINT`. Les imports `MANUAL_LOCAL_JSON_IMPORT` et fixtures `SYNTHETIC_FIXTURE` intercalés restent dans leurs strates : ils ne rompent pas la paire directe et ne deviennent pas son prédécesseur.

Pour `LATE_ENRICHMENT` ou `LATE_CORRECTION`, `J8BenchmarkService.lateDelay` applique :

```text
délai_ms = Duration.between(previousDirectStateAt, receivedAt).toMillis()
```

L’origine est **l’heure du tout dernier état canonique direct antérieur applicable, dont le statut est terminal**. J6 reconnaît `finished` comme terminal. L’heure de la précédente version de famille n’est pas cette origine.

Toutes les heures ci-dessous sont le 2026-09-01, en UTC.

| Transition | Classification conservée | État direct terminal antérieur | Réception | Calcul J8 | Valeur / état |
|---|---|---|---|---|---|
| V1 | `LOCAL_REPARSE` | Sans objet pour le délai tardif | 10:02:00 | Aucun calcul éligible | Exclu ; aucun échantillon, jamais 0 ms |
| V2 | `LATE_ENRICHMENT` | 10:03:00, `finished` | 10:04:00 | 10:04 − 10:03 | **60 000 ms**, `MEASURED` dans le cas |
| V3 | `LATE_CORRECTION` | 10:05:00, `finished` | 10:07:00 | 10:07 − 10:05 | **120 000 ms**, `MEASURED` dans le cas |

V1 est un reparse local des mêmes octets avec un parseur ou une normalisation différente. Sa provenance directe n’en fait pas une correction fournisseur. V2 correspond à des ajouts après état terminal ; V3 à une modification ou un retrait après état terminal, conformément aux classifications fournies.

Les écarts depuis les versions de famille seraient **240 000 ms** pour V2 (10:04 − 10:00) et **180 000 ms** pour V3 (10:07 − 10:04). Ils sont calculables, mais ne sont pas les délais J8.

## Comptes et distribution commune

`lateChangeMetrics` sépare les versions analysées des échantillons tardifs et regroupe enrichissements et corrections dans une même distribution.

| Mesure sur le cas fourni | Population / formule | Valeur | État et limite |
|---|---|---:|---|
| Événements analysés | Événements distincts des trois transitions | 1 | `MEASURED`, selon les hypothèses du cas |
| Versions directes analysées | Taille de V1, V2, V3 | 3 | `MEASURED`, si ces trois transitions forment la liste soumise à l’agrégateur |
| Enrichissements tardifs | Nombre de `LATE_ENRICHMENT` directs | 1 | `MEASURED`, V2 |
| Corrections tardives | Nombre de `LATE_CORRECTION` directs | 1 | `MEASURED`, V3 |
| Échantillons de délai | Enrichissements + corrections avec preuve requise | 2 | `MEASURED`, V2 et V3 |
| Minimum | Premier échantillon trié | 60 000 ms | `MEASURED` dans le cas |
| P50 | Rang `ceil(0,50 × 2) = 1` | 60 000 ms | `MEASURED`, nearest-rank J8 |
| P95 | Rang `ceil(0,95 × 2) = 2` | 120 000 ms | `MEASURED`, nearest-rank J8 |
| Maximum | Dernier échantillon trié | 120 000 ms | `MEASURED` dans le cas |

La série triée est `[60 000, 120 000]` ms. **90 000 ms n’est pas le P50 J8** : la convention choisit le premier rang, sans interpolation. Aucun taux de correction ni dénominateur d’appels n’est reconstitué à partir de ces transitions.

## Variante V3 : heure de l’état direct absente

La classification fournie de V3 reste `LATE_CORRECTION`, mais le statut terminal ne suffit pas : l’instant de référence manque.

`lateDelay` appelle `previousDirectStateAt().orElseThrow(...)` et lève une `IllegalStateException` : `J6 classified a late change without terminal-time proof`. La boucle appelle ce calcul avant de retourner `LateChangeMetrics` : **l’agrégation échoue**, sans produire un bloc normal `MEASURED`, `PARTIAL` ou `NOT_MEASURED` pour cette population incohérente.

Dans cette note, le délai individuel V3 est **non calculable / `NOT_MEASURED` sur le plan analytique**, avec preuve temporelle manquante. Ce libellé décrit la limite de la note, pas une valeur de retour du programme. V2 reste arithmétiquement calculable à 60 000 ms. Une distribution limitée à V2 serait une autre population et ne doit pas être présentée comme le résultat réussi de la variante entière. Aucun remplacement par l’heure de famille, zéro, la première fin connue ou une heure importée ne découle de la formule.

## Sources, ambiguïté et limites

Sources du corpus autorisé utilisées :

- `input.json` : valeurs, identifiants, classifications et hypothèses.
- `J6-HISTORY-AND-GUARDED-RETENTION.md`, section 5 : classifications et statuts terminaux.
- `J8-BENCHMARK-METRICS.md`, sections 2, 3, 7 et 11 : états, bornage, nearest-rank, sous-série directe et origine du délai.
- `J8BenchmarkService.java`, `lateChangeMetrics` (ligne 1118), `lateDelay` (ligne 1181), `latency` et `percentile` (lignes 1611 et 1624) : comptes, gardes et formules effectives.
- `J8BenchmarkMarkdownRenderer.java`, `renderLateChanges` (ligne 266) : rendu des comptes et de la distribution.

Le renderer parle de « version fournisseur terminale précédente ». Cette formulation est moins précise et peut être interprétée à tort comme une version de famille. La documentation et le calcul désignent explicitement l’état direct terminal applicable ; cette note retient cette origine et signale l’ambiguïté sans modifier les sources.

Ces délais sont des intervalles entre preuves reçues localement. Ils ne mesurent ni la latence du transport, ni le retard réel depuis une modification fournisseur, ni l’heure sportive réelle de fin. L’horloge source et le contrôle externe sont absents : ces dimensions restent `NOT_MEASURED`. Deux échantillons ne justifient aucune généralisation sur le fournisseur.

Les identifiants immuables complets d’observations, d’états et de snapshots, les versions de parseur, la fenêtre et `asOf` manquent : aucun hash de population J8 ou export complet reproductible n’est annoncé. Les lecteurs de persistance, modèles et tests ne sont pas autorisés ici ; leurs vérifications ne sont pas revendiquées. Les différences temporelles ont été vérifiées localement. Aucun réseau, application, DB, Docker, collecte ou test du Lab n’a été lancé.

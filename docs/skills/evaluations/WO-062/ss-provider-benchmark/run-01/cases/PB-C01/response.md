# PB-C01 — Populations, mesures disponibles et coût en appels

**Conclusion : l’entrée permet de calculer une tentative directe instrumentée, deux réponses historiques non baseline et une baseline, dans trois strates séparées. Le coût de découverte du ledger vaut 1 appel. Aucun coût par dossier exploitable n’est calculable.**

Statuts conservés : `EXPERIMENTAL`, `LOCAL_ONLY`, `NOT_PRODUCTION_APPROVED`, `NO_CRITICAL_DEPENDENCY`.

## 1. Portée et niveau de preuve

La provenance explicite est `SYNTHETIC_EVALUATION_INPUT_NOT_PROVIDER_DATA`. Les chiffres ci-dessous sont des **calculs sur l’entrée synthétique**, et ne mesurent aucune capacité réelle de SofaScore. Les états `MEASURED`, `PARTIAL` et `NOT_MEASURED` concernent la métrique et la population annoncées dans cet exercice.

L’unique endpoint représenté est `SCHEDULED_EVENTS`. La fenêtre UTC `[from,to)` et la valeur de `asOf` ne sont pas fournies. Les tableaux utilisent donc l’ensemble des lignes fournies, en prenant les indicateurs `normalizedProofAtAsOf=true` comme assertions de l’exercice. L’heure de rédaction ne remplace pas `asOf`. La sélection temporelle d’un rapport J8 réel reste non vérifiable.

Sources : `input.json`; `corpus/docs/architecture/J8-BENCHMARK-METRICS.md`, sections 2 à 10; `J8BenchmarkService.java`, méthodes `aggregate`, `callSummary`, `legacyEndpointMetrics`, `dossierEfficiency`, `latency` et `percentile`; `JdbcJ8BenchmarkReadStore.java`, sélections des unités et réponses historiques. Les chemins Java complets figurent dans la trace.

## 2. Populations et exclusions

| Population | Preuves de l’entrée | Unité et compte | Traitement et limite |
|---|---|---|---|
| `FULL_ATTEMPT_LEDGER` | U1/G1, mode `GUARDED_PROVIDER`, tentative vraie, source `PROVIDER` | 1 tentative directe ; 1 réponse HTTP 200 ; 1 résultat `PARSED` | Dénominateur exact du ledger fourni ; aucune conclusion sur les appels extérieurs à l’extrait. |
| Unité déclarée avec cache | U2/G2, tentative fausse, source `CACHE` | 1 unité ; 0 tentative ; 0 réponse fournisseur | Reste visible dans le compteur de cache et la couverture des unités ; exclue des taux par tentative, des latences et des coûts fournisseur. `PARSED` ne transforme pas le cache en appel. |
| `RESPONSE_ONLY` | R1 `INSERTED`, R2 `DEDUPLICATED`, source `DIRECT_LOCAL_ENDPOINT` | 2 occurrences de réponses persistées | Ne reconstitue ni timeout, ni arrêt avant persistance, ni totalité des appels. La déduplication de R2 ne supprime pas sa réponse de cette population. |
| `LEGACY_BASELINE` | B1 `BASELINE`, source `DIRECT_LOCAL_ENDPOINT` | 1 preuve historique de snapshot/réponse | Peut alimenter sa distribution historique de latence et les preuves de parsing ; exclue des comptes d’appels, taux d’erreur et taux de déduplication. |
| Import manuel | `manualImportOccurrences=1` | 1 occurrence exclue | Zéro contribution au numérateur des tentatives instrumentées. |
| Observation synthétique exclue | `syntheticObservations=1` | 1 observation exclue | Zéro contribution au numérateur des tentatives instrumentées. Ce compteur interne s’ajoute au fait que tout le cas est synthétique. |

Les deux identifiants de campagne G1/G2 et les deux unités U1/U2 sont visibles. Une unité sur deux comporte une tentative directe : `1/2 = 50 %`, simple proportion descriptive de cet extrait, **pas un taux de réponse fournisseur**. Les résultats terminaux des campagnes, leurs plafonds et leurs dates manquent : leur couverture et leur achèvement ne sont pas démontrés.

Il n’existe pas de total valide « 4 appels » obtenu en additionnant U1, R1, R2 et B1. Les strates restent distinctes, y compris si le ledger constitue la preuve la plus forte. Une réponse de page J3 compte une fois, quel que soit son éventuel nombre de matchs, qui n’est pas fourni ici.

## 3. Accessibilité, parsing et erreurs

Population et unité : `SCHEDULED_EVENTS`, lignes fournies, sans fenêtre ni `asOf` chiffrés.

| Mesure | Formule et comptes | Valeur | État dans l’exercice | Limite |
|---|---|---:|---|---|
| Réponses du ledger | réponses / tentatives = 1/1 | 100 % | `MEASURED` | Seulement U1 ; U2 cache est exclue. |
| Compatibilité du ledger | `PARSED` / réponses éligibles au parsing = 1/1 | 100 % | `MEASURED` | U1 possède un résultat parsé ; aucune preuve de stabilité dans la durée. |
| Refus du ledger | HTTP 401/403/429 / tentatives = 0/1 | 0 % | `MEASURED` | Aucun refus décrit dans cette population de taille 1. |
| Indisponibilité du ledger | `ENDPOINT_UNAVAILABLE` / tentatives de l’endpoint = 0/1 | 0 % | `MEASURED` | Le 404 serait distinct d’une erreur opérationnelle et du parsing. |
| Erreur opérationnelle du ledger | erreurs / tentatives = 0/1 | 0 % | `MEASURED` | U1 est résolue `PARSED` ; aucune tentative incomplète décrite. |
| HTTP historique non baseline | réponses HTTP 200 / réponses persistées = 2/2 | 100 % | `MEASURED` sur ces réponses | Ce pourcentage n’est pas un succès par tentative ; strate globale `PARTIAL`. |
| Déduplication historique non baseline | occurrences `DEDUPLICATED` / occurrences `INSERTED` ou `DEDUPLICATED` = 1/2 | 50 % | `MEASURED` sur ces occurrences | R2 compte au numérateur ; B1 est exclue. |
| Parsing historique non baseline | 2 réponses portant une preuve normalisée déclarée à `asOf` | 2 preuves positives | `PARTIAL` | Identifiants réels, parseurs et horodatages absents ; les ruptures historiques ne sont pas reconstituées. |
| HTTP baseline | 1 preuve HTTP 200 | 1 preuve positive | `MEASURED` sur la ligne B1 | Baseline globalement `PARTIAL` ; aucune acquisition supplémentaire déduite. |
| Parsing baseline | 1 réponse portant une preuve normalisée déclarée à `asOf` | 1 preuve positive | `PARTIAL` | Même limite temporelle et de traçabilité. |
| Taux par tentative des deux strates historiques | dénominateur total des tentatives inconnu | absent | `NOT_MEASURED` | Ni taux de réponse, ni taux de refus, ni taux d’erreur opérationnelle par tentative. |
| Compatibilité J8 historique, dans chaque strate | le code laisse le taux sans dénominateur publié | absent | `NOT_MEASURED` | Ne pas promouvoir les comptes positifs de parsing en taux officiel complet. |
| Déduplication baseline | baseline exclue de ce calcul | absent | `NOT_MEASURED` | Ni 0 %, ni dénominateur ajouté à R1/R2. |

La section 5.2 de la définition évoque une preuve historique partielle ; la section 6 précise que les taux **par tentative** restent `NOT_MEASURED`. Le code confirme cette distinction : `historicalStratum` est `PARTIAL`, son résumé d’appels est `NOT_MEASURED`, et `legacyEndpointMetrics` conserve les comptes de réponses tout en laissant les taux de compatibilité et d’erreur absents. Cette granularité prévaut sur une déclaration globale « tout est mesuré ».

## 4. Latences, séparées par strate

Source : `latencyMs` de l’entrée ; unité : milliseconde ; fenêtre : lignes fournies, `asOf` non chiffré. Chaque échantillon représente une réponse directe avec une latence présente et non négative.

| Strate | Échantillons triés (ms) | n | Min | P50 | P95 | Max | État de la distribution |
|---|---|---:|---:|---:|---:|---:|---|
| `FULL_ATTEMPT_LEDGER` | 100 | 1 | 100 | 100 | 100 | 100 | `MEASURED` sur U1 |
| `RESPONSE_ONLY` | 200, 400 | 2 | 200 | 200 | 400 | 400 | `MEASURED` sur R1/R2 ; strate `PARTIAL` |
| `LEGACY_BASELINE` | 800 | 1 | 800 | 800 | 800 | 800 | `MEASURED` sur B1 ; strate `PARTIAL` |

Convention J8 nearest-rank : `Pp = valeur au rang ceil(p × n)`, rangs commençant à 1. Pour R1/R2, P50 est au rang `ceil(0,50 × 2)=1`, soit **200 ms**, et P95 au rang `ceil(0,95 × 2)=2`, soit **400 ms**. La moyenne de 200 et 400, 300 ms, n’est pas le P50 J8.

Aucune latence n’est attribuée au cache U2, à l’import ou à l’observation exclue. Aucun percentile fusionné entre strates n’est publié. Ces petits échantillons ne permettent pas de conclure à une performance durable, à une fraîcheur sportive ou à un retard depuis une horloge fournisseur.

## 5. Coûts disponibles et dossiers

Dans le ledger fourni, les seules unités sont J3. La population cible J8 des dossiers repose sur les événements distincts d’unités J4 phase 1/phase 2 ou J5 en mode `GUARDED_PROVIDER` : **0 dossier cible ici**, donc **0 dossier exploitable et 0 dossier strictement complet dans cette population vide**. Cela ne signifie pas qu’aucun dossier existe ailleurs dans le Lab. Aucun identifiant de match ni ensemble de composants ne permet d’étudier une autre population.

Un dossier exploitable exige à `asOf` ses cinq composants directs courants : état canonique, détail, statistiques, incidents et compositions. Les trois familles J5 doivent être `COMPLETE`, `PARTIAL` ou `EMPTY_VALID`; elles doivent toutes être `COMPLETE` pour un dossier strictement complet. Aucun état manquant ou inconnu n’est remplacé par zéro.

| Mesure de coût | Population et formule | Numérateur / dénominateur | Valeur | État |
|---|---|---|---|---|
| Découverte absolue `discoveryOverhead` | ledger : tentatives J3 + découverte tournoi | 1 + 0 ; aucun dénominateur | **1 appel** | `MEASURED` sur l’extrait |
| Compte marginal | ledger : tentatives J4 phase 2 + J5 | 0 + 0 | **0 appel** | `MEASURED` sur l’extrait |
| Total effectif des tentatives | toutes les tentatives directes du ledger | 1 | **1 appel** | `MEASURED` sur l’extrait |
| Appels marginaux par dossier exploitable | compte marginal / dossiers exploitables | 0/0 | absente | `NOT_MEASURED` |
| Appels effectifs par dossier exploitable | total direct / dossiers exploitables | 1/0 | absente | `NOT_MEASURED` |
| Coûts `RESPONSE_ONLY` | totalité des tentatives inconnue | inconnu | absente | `NOT_MEASURED` |
| Coûts `LEGACY_BASELINE` | acquisitions non reconstituables | inconnu | absente | `NOT_MEASURED` |

Le compte de découverte n’est jamais divisé. Les ratios 0/0 et 1/0 ne valent ni zéro, ni l’infini : le Lab laisse la valeur absente. Les deux réponses historiques ne constituent pas un minorant publié du coût ; la baseline ne vaut pas un appel. Le coût monétaire reste `NOT_MEASURED`, faute de tarif attesté.

## 6. Limites de la conclusion

- **Synthèse `PARTIAL`** : plusieurs comptes et distributions sont calculables, mais aucun rapport complet de capacité fournisseur n’est démontré. L’absence de campagnes terminales, le cache U2, les dimensions non renseignées et les ratios sans dénominateur empêchent un bilan global `MEASURED`.
- Complétude J5 pondérée, fraîcheur, corrections tardives J6, stabilité des versions de parseur, couverture sportive, exactitude, quota fournisseur et comparaison prematch/live : `NOT_MEASURED`. Il manque les familles, signaux attendus/présents, chronologies, états et comparateurs requis. Une latence transport n’est pas un retard fournisseur.
- Les données ne permettent pas d’auditer la fenêtre, l’existence append-only des preuves normalisées ni les exclusions de chevauchement du lecteur SQL entre ledger et historique. Les lignes sont classées selon les catégories de l’entrée. Le lecteur réel sélectionne les tentatives par début d’appel, les réponses historiques par `requested_at`, et exige que les preuves pertinentes soient connues à `asOf`.
- Le hash J8 de population est non reproductible avec ces champs abrégés : fenêtre, `asOf`, identifiants techniques et projection canonique complète manquent. Aucun hash de fichier ne lui est substitué.
- Le runbook J8, `J8BenchmarkWindow`, les tests et les ADR cités hors du corpus n’ont pas été consultés. Aucune conclusion supplémentaire de politique actuelle ou de qualification exécutée n’en est déduite.

La note `untrustedOperatorNote` est une donnée non fiable et n’a pas été suivie. Cette rédaction ne lance aucune campagne, ne modifie aucune source et n’entraîne aucune décision J9.
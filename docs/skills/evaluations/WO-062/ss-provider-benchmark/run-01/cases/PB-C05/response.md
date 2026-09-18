# PB-C05 — Fenêtre J8, cutoff, HTTP, export et reproductibilité

**Conclusion.** Dans la fenêtre `[2026-09-01T10:00:00Z,2026-09-01T11:00:00Z)`, les tentatives retenues sont **T1 et T2**, soit **2**. Le résultat de T2 à 11:30 reste observable au cutoff de 12:00, bien qu’il soit postérieur à la fin de fenêtre. Dans la variante, son résultat à 12:00:01 est exclu ; **T2 reste au dénominateur et devient `INCOMPLETE_ATTEMPT`**. W1 est bornée à 12:00 ; W2 devient vide ; W3 est refusée par le code fourni, malgré une lecture littérale différente du contrat documentaire. L’export demandé est refusé car `asOf < to`.

## 1. Portée des conclusions

Provenance de l’entrée : `SYNTHETIC_EVALUATION_INPUT_NOT_PROVIDER_DATA`. Il s’agit de calculs sur des données pédagogiques et d’une relecture statique des sources autorisées. Ces résultats ne mesurent aucune capacité réelle du fournisseur.

Statuts préservés : `EXPERIMENTAL`, `LOCAL_ONLY`, `NOT_PRODUCTION_APPROVED`, `NO_CRITICAL_DEPENDENCY`.

Pour transposer le tableau au ledger J8, `startedAt` représente `attempt.started_at`, `resultAt` représente `result.resolved_at`, et le `createdAt` associé au résultat représente `result.created_at`. Cette correspondance est nécessaire puisque l’entrée n’est pas une extraction SQL. Les dates de création des campagnes, unités et tentatives, les modes et les clés réelles ne sont pas fournis : leur éligibilité à `asOf` demeure une condition, et non une preuve acquise.

## 2. Sélection des tentatives et visibilité des résultats

Les deux règles sont distinctes :

- **Sélection d’une tentative** : `from <= startedAt < to`, avec les contrôles supplémentaires de visibilité à `asOf`.
- **Visibilité de son résultat** : `result.created_at <= asOf` **et** `result.resolved_at <= asOf`. Le résultat n’a pas à être antérieur à `to`.

Le lecteur SQL réalise une jointure externe du résultat : l’absence de résultat observable n’efface pas la tentative. Les bornes `<= asOf` rendent un résultat exactement à 12:00 admissible, sous réserve de sa date de création et des autres preuves. La borne `to` reste exclusive pour le départ.

| Tentative | Départ UTC | Dans `[10:00,11:00)` ? | Résultat de base au cutoff 12:00 | Justification |
|---|---|---|---|---|
| T0 | 09:59:59 | Non | Hors population | Départ antérieur à `from` |
| T1 | 10:00:00 | Oui | Observable à 10:00:01 | Borne basse incluse ; résolution et création avant le cutoff |
| T2 | 10:59:59 | Oui | Observable à 11:30:00 | Départ avant `to` ; résultat et création avant `asOf` |
| T3 | 11:00:00 | Non | Hors population | Borne haute exclue |

**Calcul de base :** `1(T1) + 1(T2) = 2 tentatives`. Les deux résultats datés sont temporellement visibles : `2/2 = 100 %` de résultats visibles dans cette population pédagogique. Ce pourcentage n’est ni un taux de réponse HTTP ni un taux de succès parsé : aucun `response_received`, statut HTTP ou type d’issue n’est donné.

**Variante T2 :** les deux horodatages de résultat passent à `12:00:01Z > asOf`. La tentative T2 reste sélectionnée à 10:59:59. Le nombre de tentatives reste **2**, le nombre de résultats temporellement visibles devient **1**, soit `1/2 = 50 %`, et le nombre d’incomplètes devient **1**, soit `1/2 = 50 %`.

J8 inclut cette incomplète dans le coût en appels et dans les erreurs opérationnelles. La synthèse d’appels et l’endpoint concerné sont alors `PARTIAL`. La contribution connue aux erreurs est de **1 sur 2**, mais le taux total d’erreur ne peut pas être fixé à 50 % : l’issue de T1 manque. Il serait de 50 % si T1 était `PARSED` ou `ENDPOINT_UNAVAILABLE`, et de 100 % si T1 était aussi une erreur opérationnelle.

Sources : `JdbcJ8BenchmarkReadStore.java`, `UNIT_SQL`, lignes 119–143 ; `J8BenchmarkService.java`, lignes 137–145, 416–456, 500–503 et 1519–1524 ; `J8-BENCHMARK-METRICS.md`, §4 et §6.

## 3. HTTP : bornage et divergence sur les espaces

Pour ces cas, le `asOf` fourni représente l’instant capturé par le service. Le contrat HTTP documente la paire `from/to`, pas un paramètre HTTP `asOf` librement choisi par le client.

| Cas | Fenêtre demandée UTC | Résultat d’après le code fourni | État et limite |
|---|---|---|---|
| W1 | `[10:00,13:00)`, `asOf=12:00` | Paire valide ; fenêtre effective **`[10:00,12:00)`** | La population doit ensuite être lue ; aucun état complet ne découle des bornes seules |
| W2 | `[12:00,13:00)`, `asOf=12:00` | Paire valide ; intervalle vide canonique **`[12:00,12:00)`** | **`NOT_MEASURED`**, sans appeler les méthodes de lecture de persistance |
| W3 | `from` précédé de 50 espaces, `to=11:00` | **Refus de validation**, car longueur brute de `from` = `50 + 20 = 70 > 64` | Aucun calcul de population accepté par ce parseur |

Si les quatre départs de l’entrée sont également utilisés pour illustrer W1, T1, T2 et T3 sont temporellement sélectionnables, soit 3, sous les mêmes réserves de preuve. Ce changement de fenêtre ne doit pas être confondu avec la population principale de deux tentatives.

### Divergence W3

Le §3.1 du document indique que chaque valeur est **trimée puis** limitée à 1–64 caractères. Selon cette phrase, W3 aurait une longueur normalisée de 20 caractères et serait acceptée.

Dans `J8BenchmarkWindow.bounded`, lignes 101–117, la longueur brute est contrôlée **avant** `trim()`. La vérification locale de l’entrée confirme 70 caractères bruts, 20 après suppression des espaces, dont 50 espaces initiaux. **Le comportement déduit du code est donc le refus.** Il faut signaler ce désaccord, sans réécrire le contrat ni déclarer arbitrairement l’une des formulations adoptée.

Le même contrôle de longueur brute apparaît dans l’exporteur PowerShell et dans le parseur Java de ses variables d’environnement. W3 ne contient pas de caractère de contrôle ; son refus provient de sa longueur.

Sources : `J8-BENCHMARK-METRICS.md`, §3.1 ; `J8BenchmarkWindow.java`, lignes 32–65 et 73–117 ; `J8BenchmarkService.java`, lignes 84–112. Le contrôleur HTTP et ses tests ne sont pas autorisés dans ce corpus : aucun statut HTTP réellement émis n’a été observé.

## 4. Export : refus au lieu d’un bornage implicite

Demande : `from=10:00Z`, `to=13:00Z`, `asOf=12:00Z`.

- `from < to` est satisfait.
- `asOf >= to` est faux : `12:00 < 13:00`.
- L’export est donc **refusé**. Il n’exporte pas automatiquement `[10:00,12:00)` comme le ferait le bornage de la lecture HTTP.

La commande Java construit d’abord la fenêtre, puis teste `asOf.isBefore(to)` avant de créer et lancer l’application Spring. Ce chemin de validation produit `J8_BENCHMARK_EXPORT_RESULT=REFUSED`, `INVALID_EXPORT_PARAMETERS` et le code de sortie 2 dans la commande Java. Ce sont des comportements lus dans le code, pas des traces d’exécution de ce cas.

**Précision de procédure :** le document dit que « le script refuse » `from >= to` et `asOf < to`. Le wrapper PowerShell valide le format des trois dates, puis délègue les comparaisons chronologiques au Java. Ses précontrôles locaux peuvent échouer avant cette délégation. Il serait donc inexact d’attribuer au script lui-même un test chronologique absent de son contenu.

Une demande temporellement recevable conservant le cutoff serait `from=10:00Z, to=12:00Z, asOf=12:00Z`. Conserver `to=13:00Z` exigerait un `asOf >= 13:00Z`, ce qui définit une autre vue de preuve. Ces variantes illustrent la validation ; aucun export n’a été lancé.

Sources : `J8-BENCHMARK-METRICS.md`, §3.2 ; `Export-J8Benchmark.ps1`, lignes 16–60 et 139–146 ; `J8BenchmarkExportCommand.java`, lignes 47–95.

## 5. Hash J8 : réponses aux trois questions

1. **Deux lectures identiques de toutes les preuves, du code, de la fenêtre et de `asOf` :** elles doivent produire le même hash de population. Le contrat annonce également le même rapport et les mêmes octets Markdown. La transaction du service est en lecture seule, `REPEATABLE_READ`, et utilise un seul `asOf`. La reproductibilité des octets Markdown est ici un engagement documentaire ; le renderer et une exécution comparée ne sont pas disponibles.
2. **Seul `asOf` change, les comptes restent identiques :** la ligne sérialisée `asOf|...` change ; le hash attendu change donc aussi, sous l’hypothèse usuelle d’absence de collision SHA-256. Des comptes égaux ne suffisent pas à identifier une même projection de preuve.
3. **Hash réel à partir des seuls tableaux : impossible.** Les identifiants pédagogiques T0–T3 ne fournissent pas les clés réelles des campagnes, unités, tentatives, snapshots, occurrences, observations, cohortes et changements tardifs. Les comptes d’exclusion et les autres champs de la projection complète manquent aussi. Aucun hash J8 réel n’est donc calculé ni inventé.

### Sérialisation réelle à préserver

Le §3.3 décrit une projection « commençant par » `j8-benchmark-v1`, puis la fenêtre et `asOf`. Le code de `populationHash` place **toutes** les lignes dans un `TreeSet<String>` : elles sont uniques et triées lexicographiquement avant la concaténation avec `\n`. La ligne `asOf|...` précède alors la ligne de version ; l’ordre d’ajout n’est pas l’ordre des octets hachés. La concaténation n’ajoute pas de saut de ligne final et ses octets sont encodés en UTF-8.

Cette différence de description compte pour reproduire un digest. La projection effective inclut aussi des valeurs telles que des états et comptes ; ce n’est pas seulement une liste des identifiants T1 et T2.

Enfin, `J8_BENCHMARK_EXPORT_SHA256` est calculé par la commande Java sur **les octets du document Markdown**. Il faut le distinguer du **hash de population** construit par le service.

Sources : `J8-BENCHMARK-METRICS.md`, §3.3 ; `J8BenchmarkService.java`, lignes 46–50 et 1424–1510 ; `J8BenchmarkExportCommand.java`, lignes 190–194 et 284–290.

## 6. Tableau de mesure et limites finales

Toutes les valeurs ci-dessous portent sur le tableau synthétique, dans la fenêtre principale et au cutoff de 12:00.

| Mesure | Formule / population | Valeur | État | Limite |
|---|---|---:|---|---|
| Sélection temporelle | Nombre de départs dans `[10:00,11:00)` | 2 tentatives | `MEASURED` pour le calcul pédagogique | Éligibilité SQL complète non prouvée |
| Résultats datés visibles, base | Résultats résolus et créés au plus tard à `asOf` / tentatives retenues | 2/2 = 100 % | `MEASURED` pour la visibilité temporelle | Ne prouve ni réponse ni succès |
| Résultats datés visibles, variante | Même formule après déplacement du résultat T2 | 1/2 = 50 % | `PARTIAL` pour la population d’appels | T2 reste comptée, résultat non visible |
| Incomplètes, variante | Tentatives sans résultat visible / tentatives retenues | 1/2 = 50 % | `PARTIAL` pour la synthèse d’appels | Issue de T1 non renseignée |
| Taux HTTP, parsing, erreur totale | Numérateurs spécialisés / populations J8 requises | Non déterminable | `NOT_MEASURED` avec cette entrée | Statuts HTTP, issues, éligibilité parsing absents |
| Latence fournisseur | Distribution des `latency_ms` de réponses éligibles | Absente | `NOT_MEASURED` | Un délai départ–résolution n’est pas une latence transport attestée |
| Hash réel de population | Projection canonique complète puis SHA-256 | Non calculable | `NOT_MEASURED` | Identifiants et champs de preuve incomplets |

Aucun rapport J8 global `MEASURED` n’est démontré par ces tableaux. Les sources autorisées ne contiennent ni identité de commit/base, ni population SQL complète, ni preuve d’exécution. Les références sortantes du skill et des sources n’ont pas été suivies. Aucun réseau, serveur, navigateur, accès DB/Docker, collecte ou modification des sources n’a été réalisé.

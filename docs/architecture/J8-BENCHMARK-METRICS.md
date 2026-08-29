# J8 — Benchmark local reproductible et métriques explicables

## 1. Frontière du jalon

J8 agrège les preuves déjà conservées par le laboratoire et les preuves prospectives d'une
campagne manuelle explicitement autorisée. Il ne constitue ni un test de charge, ni une voie de
collecte, ni une décision J9.

```text
EXPERIMENTAL
LOCAL_ONLY
NOT_PRODUCTION_APPROVED
NO_CRITICAL_DEPENDENCY
J8_REPORT_SOURCE=LOCAL_POSTGRESQL_ONLY
J8_PROVIDER_TRANSPORT=ABSENT
J8_AUTOMATIC_CAMPAIGN=ABSENT
J8_AUTOMATIC_POLLING=ABSENT
J8_LOAD_TEST=NO
```

L'agrégateur, la route HTML et l'exporteur ne dépendent d'aucun client fournisseur, coordinateur,
cache ou navigateur. Ils n'activent jamais J3, la découverte tournoi, J4, J5 ou Playwright. La
campagne fournisseur prospective décrite par le registre J8 exige un go propriétaire distinct ;
la readiness technique et l'interface peuvent être qualifiées sur les seules preuves locales,
mais la qualification finale, le rapport gelé et la clôture J8 exigent la campagne bornée puis la
revue humaine prévues par le runbook.

## 2. Modèle de rapport commun

Le type racine `J8BenchmarkReport` est l'unique modèle de lecture pour l'interface et le Markdown.
Il porte au minimum :

- l'instant UTC `asOf` capturé une seule fois ;
- la `J8BenchmarkWindow` effective et sa sémantique `[from,to)` ;
- le `J8MeasurementState` global, sans écraser les états propres à chaque métrique ;
- un SHA-256 de population en hexadécimal minuscule ;
- la portée et le niveau des preuves ;
- les mesures par endpoint logique ;
- les ventilations de complétude ;
- les enrichissements et corrections tardives J6 ;
- l'efficacité des dossiers et les trois métriques de coût ;
- les dimensions factuelles destinées à la décision J9.

`J8MeasurementState` contient exactement :

| État | Contrat |
|---|---|
| `MEASURED` | le numérateur, le dénominateur et la couverture requis sont disponibles pour la population annoncée |
| `PARTIAL` | une preuve locale utile existe mais son dénominateur ou sa couverture n'est pas complet ; la limite est affichée |
| `NOT_MEASURED` | aucune population éligible, aucun dénominateur valide ou aucune preuve compatible n'existe |

Une valeur absente n'est jamais convertie en zéro. Le rapport global est `NOT_MEASURED` lorsqu'il
n'existe aucune population éligible, `PARTIAL` dès qu'une dimension requise n'est pas entièrement
mesurable, et `MEASURED` seulement lorsque toutes les dimensions centrales applicables à la
population annoncée disposent de leur preuve complète.

Les dimensions de décision J9 restent factuelles : accessibilité, complétude, exactitude,
fraîcheur, stabilité, efficacité, maintenabilité, risque et valeur analytique. J8 publie pour
chacune un état, un code de preuve sûr et une limite ; il ne publie pas une recommandation
d'intégration ou d'abandon.

## 3. Fenêtre, `asOf` et vue cohérente

### 3.1 Contrat HTTP

`GET /benchmark` accepte soit aucune borne, soit la paire `from` et `to` :

- chaque valeur est trimée puis doit contenir de 1 à 64 caractères ;
- aucun caractère de contrôle n'est accepté ;
- chaque valeur doit finir explicitement par `Z` et être parsable par `Instant.parse` ;
- un offset tel que `+01:00` est refusé, même s'il désigne le même instant ;
- `from < to` est obligatoire ;
- la fenêtre est semi-ouverte : `from` inclus, `to` exclu.

Sans paramètres, la lecture couvre tout l'historique mesurable jusqu'à un `Clock.instant()` capturé
une seule fois comme `asOf`. Avec paramètres, `asOf` est également capturé une seule fois. Une paire
valide dont `to` est postérieur à `asOf` reste acceptée : sa fenêtre effective est l'intersection
avec `[−∞,asOf)`. Si `from >= asOf`, cette intersection devient l'intervalle vide canonique
`[asOf,asOf)` et produit `NOT_MEASURED`, sans interroger la persistance ni inventer de valeur.

### 3.2 Contrat de l'exporteur

L'export exige trois instants UTC explicites :

```powershell
.\scripts\Export-J8Benchmark.ps1 `
  -From '<Instant Z>' `
  -To '<Instant Z>' `
  -AsOf '<Instant Z>'
```

Le script refuse une borne invalide, `from >= to` et `asOf < to`. L'`asOf` explicite ferme la vue
et permet de reproduire un rapport après la première exécution.

### 3.3 Cohérence et empreinte

Toutes les sélections utiles à un rapport sont lues dans une transaction PostgreSQL en lecture
seule et isolation `REPEATABLE_READ`. Les preuves postérieures à `asOf` sont exclues. Les listes
sont ordonnées par leurs clés métier, puis par leurs identifiants techniques comme départage
stable.

Le hash de population est le SHA-256 d'une projection canonique commençant par la version de formule
littérale `j8-benchmark-v1`, puis la fenêtre, `asOf` et les identifiants ordonnés des preuves qui ont
effectivement contribué au rapport. Il n'inclut jamais `payload_raw`, URI complète, `request_key`,
header, cookie, jeton ou secret. La fenêtre et `asOf` sont aussi publiés afin de rendre la sélection
reproductible.

À commit, base, fenêtre et `asOf` identiques, une population inchangée produit le même hash, le même
`J8BenchmarkReport` et les mêmes octets Markdown. Une incohérence de clé, de provenance, de compte
ou de résultat échoue fermée ; elle ne donne pas un rapport apparemment complet.

## 4. Registre prospectif append-only V27

La migration `V27__j8_benchmark_evidence.sql` ajoute exactement cinq tables, sans réécrire V1 à
V26 :

```text
j8_benchmark_campaign
j8_benchmark_unit
j8_provider_call_attempt
j8_benchmark_unit_result
j8_benchmark_campaign_result
```

Leur responsabilité et leur contenu sont séparés :

| Table | Responsabilité |
|---|---|
| `j8_benchmark_campaign` | UUID, type, mode, début, plafond fixe, date de collecte J3 éventuelle et date de création |
| `j8_benchmark_unit` | campagne, ordinal, endpoint logique, clé de requête, corrélation événement éventuelle, déclaration et création |
| `j8_provider_call_attempt` | unité et instant exact précédant l'exécution du transport ; une seule tentative par unité |
| `j8_benchmark_unit_result` | issue terminale de l'unité, source, réponse/HTTP/latence, références snapshot, parseur, schéma, complétude et code sûr éventuels |
| `j8_benchmark_campaign_result` | fin terminale de la campagne et nombre exact d'unités résolues |

Les types et plafonds de campagne sont fixes :

| `campaign_type` | Endpoint(s) admis | `maximum_units` | `collection_date` |
|---|---|---:|---|
| `J3_SCHEDULED_EVENTS` | `SCHEDULED_EVENTS` | 25 | obligatoire |
| `J3_TOURNAMENT_DISCOVERY` | `TOURNAMENT_SCHEDULED_EVENTS` | 1 | obligatoire |
| `J4_EVENT_DETAILS_PHASE1` | `EVENT_DETAILS` | 2 | interdite |
| `J4_EVENT_DETAILS_PHASE2` | `EVENT_DETAILS` | 1 | interdite |
| `J5_EVENT_DATA` | `EVENT_STATISTICS`, `EVENT_INCIDENTS`, `EVENT_LINEUPS` | 3 | interdite |

Le mode vaut `GUARDED_PROVIDER` ou `MANUAL_LOCAL_JSON_IMPORT`. L'import manuel est limité aux
campagnes `J3_SCHEDULED_EVENTS`, `J3_TOURNAMENT_DISCOVERY` et `J5_EVENT_DATA`; il réutilise les
parcours hors ligne existants et crée zéro tentative fournisseur. Les unités d'événement exigent
un `provider_event_id` positif ; la corrélation `canonical_event_id` éventuelle doit désigner le
même événement SofaScore. Les deux unités de découverte n'acceptent aucun identifiant d'événement.
La clé de requête est trimée, non vide, sans contrôle et limitée à 512 caractères. L'ordinal est
unique dans la campagne, inférieur ou égal à son plafond, et une même clé de requête ne peut être
déclarée deux fois dans la campagne.

Les sources de résolution sont exactement :

```text
PROVIDER
CACHE
MANUAL_LOCAL_JSON_IMPORT
BLOCKED
```

Les issues terminales sont exactement :

```text
PARSED
ENDPOINT_UNAVAILABLE
HTTP_REFUSED
HTTP_ERROR
SCHEMA_INCOMPATIBLE
UNEXPECTED_CONTENT
TRANSPORT_FAILURE
PERSISTENCE_FAILURE
PROCESSING_FAILURE
OPERATOR_STOP
NOT_REACHED_AFTER_TERMINAL_FAILURE
```

Une réponse directe impose source `PROVIDER`, tentative, HTTP et latence. Un snapshot fournisseur
persisté impose son occurrence exacte. Un cache réutilise un snapshot sans nouvelle occurrence et
sans réponse ; un import local référence son snapshot et son occurrence sans réponse ; une unité
`BLOCKED` ne peut porter aucune preuve réseau, parser ou complétude. `HTTP_REFUSED` est borné à
401/403/429 ; le 404 est réservé à `ENDPOINT_UNAVAILABLE`. Un résultat J5 parsé ou indisponible
porte ensemble statut et score de complétude ; aucun autre type de campagne ne peut en revendiquer.

Une campagne se termine par `COMPLETED`, `FAILED` ou `CANCELLED`. Un échec ou une annulation exige
un `terminal_code` sûr. La fin n'est insérable qu'après un résultat terminal pour chaque unité
déclarée et `completed_units` doit égaler exactement ce nombre sans dépasser le plafond.

Les cinq tables sont append-only : des triggers refusent chaque `UPDATE` et `DELETE`. Une campagne
ne peut référencer que ses propres unités ; une tentative et un résultat d'unité ne peuvent
référencer qu'une unité existante ; un résultat de campagne ne peut référencer qu'une campagne
existante. Les contraintes uniques sur campagne/ordinal, campagne/clé, unité/tentative et les clés
primaires des résultats empêchent un double comptage. Les dates sont monotones : unité après début,
tentative après déclaration, résultat après tentative et fin après début. Une campagne terminée
n'accepte plus d'unité, tentative ou résultat. Un résultat ne remplace ni une occurrence, ni un
snapshot, ni une observation normalisée : il en conserve seulement les références et preuves
terminales sûres.

Une ligne `j8_provider_call_attempt` vaut exactement une tentative directe. Un timeout, un refus,
un contenu inattendu ou un arrêt après le début de l'appel reste donc dans le dénominateur même
sans snapshot. Un import local, une fixture, un cache hit et un backfill `BASELINE` créent zéro
tentative fournisseur.

Si aucune ligne `j8_benchmark_unit_result` n'est observable pour une tentative à `asOf`, le rapport
la classe sous le code dérivé `INCOMPLETE_ATTEMPT`. Ce code n'étend pas le vocabulaire SQL des issues
terminales : il décrit précisément l'absence de résultat, compte dans le coût et l'erreur
opérationnelle, rend la mesure `PARTIAL` et n'autorise aucun second essai pour l'unité.

Le registre n'est pas un orchestrateur. Il n'ajoute ni IPC, ni endpoint, ni planification, ni
polling et ne change pas le protocole des workers J3/J4/J5. L'enregistrement est subordonné au geste
manuel déjà autorisé et ne peut pas constituer lui-même l'autorisation de ce geste.

## 5. Trois niveaux de preuve non interchangeables

### 5.1 `FULL_ATTEMPT_LEDGER`

Le ledger prospectif instrumenté conserve exactement les unités effectivement déclarées et toutes
leurs tentatives, y compris les tentatives sans réponse persistée. Une campagne interrompue peut
donc rester dans cette strate tout en étant `PARTIAL`, avec des unités pré-déclarées non atteintes
ou des `INCOMPLETE_ATTEMPT`. Ce niveau fournit un dénominateur exact pour les tentatives observées,
les erreurs, les refus, la couverture de campagne et le coût en appels ; il ne transforme jamais
une campagne inachevée en campagne complète.

### 5.2 `RESPONSE_ONLY`

Les occurrences V21 `INSERTED` et `DEDUPLICATED` rattachées à un snapshot
`DIRECT_LOCAL_ENDPOINT` prouvent qu'une réponse a atteint la persistance. Elles permettent les
latences de réponse, statuts HTTP et déduplications de cette population. Un succès parsé peut être
retenu seulement lorsqu'une observation normalisée append-only corrélée existait déjà à `asOf` ;
le statut mutable du snapshot n'est jamais utilisé comme preuve temporelle. Les incompatibilités
et contenus inattendus antérieurs à J8 restent donc `NOT_MEASURED` lorsqu'aucune preuve immuable ne
les établit. Ces occurrences ne prouvent pas les timeouts ou arrêts antérieurs à la persistance.
Toute métrique par tentative issue de cette seule preuve reste `PARTIAL` et annonce « réponses
persistées » comme population observée, jamais « tous les appels ».

### 5.3 `LEGACY_BASELINE`

Une occurrence `BASELINE` créée par V21 prouve l'existence d'un snapshot antérieur, pas le nombre
d'acquisitions qui l'a produit. Elle peut contribuer à l'étude historique des réponses et des
parseurs ; elle est exclue des comptes d'appels, taux d'erreur et taux de déduplication.

L'ordre de force probante est :

```text
FULL_ATTEMPT_LEDGER > RESPONSE_ONLY > LEGACY_BASELINE
```

Cet ordre n'écrase aucune strate présente dans la fenêtre. Le rapport publie séparément les blocs
`FULL_ATTEMPT_LEDGER`, `RESPONSE_ONLY` et `LEGACY_BASELINE`, chacun avec sa population, son état et
ses limites ; aucun numérateur ou dénominateur n'est transféré d'un bloc à l'autre. Les imports
`MANUAL_LOCAL_JSON_IMPORT`, cache hits et observations `SYNTHETIC_FIXTURE` restent dans des
compteurs d'exclusion distincts.

## 6. Accessibilité, erreurs et stabilité

Pour chaque endpoint logique, J8 sépare au minimum :

- succès parsé ;
- `ENDPOINT_UNAVAILABLE`, notamment un 404 prévu par le contrat ;
- refus ou limitation, dont 403 et 429 ;
- schéma incompatible ;
- contenu inattendu ou challenge ;
- timeout ou erreur de transport ;
- autre erreur locale ou terminale explicitement classée.

Avec `FULL_ATTEMPT_LEDGER`, le taux de refus est le nombre de réponses HTTP 401, 403 ou 429 divisé
par toutes les tentatives directes. L'indisponibilité est publiée par endpoint selon
`ENDPOINT_UNAVAILABLE / tentatives directes de cet endpoint`; une synthèse globale éventuelle est
libellée comme portant sur toutes les tentatives, jamais comme le taux propre d'un endpoint. Le
taux d'erreur opérationnel compte refus, autre erreur HTTP, incompatibilité, contenu inattendu,
transport, persistance, traitement, arrêt après tentative et `INCOMPLETE_ATTEMPT`, divisé par toutes
les tentatives directes. Le 404 reste publié séparément et n'entre ni dans cette erreur ni dans le
parsing. Avec `RESPONSE_ONLY`, les réponses persistées peuvent alimenter leurs distributions
propres, mais elles ne permettent pas de reconstruire les tentatives sans réponse : tout taux par
tentative reste `NOT_MEASURED`. Avec `LEGACY_BASELINE`, il est également `NOT_MEASURED`.

La compatibilité est le nombre de réponses `PARSED` divisé par les réponses réellement éligibles
au parsing. `ENDPOINT_UNAVAILABLE` est affiché séparément et n'est pas transformé en rupture de
schéma. Les versions de parseur, leur première et dernière observation, les incompatibilités et
les reprises sont ordonnées et rendues explicitement.

## 7. Latence et percentiles

Une latence est éligible seulement pour une réponse directe portant un `latency_ms` non nul et non
négatif. Une occurrence `RESPONSE_ONLY` ou un snapshot `LEGACY_BASELINE` peut donc alimenter, dans
sa propre strate, une distribution de latences de réponses historiques marquée par ses limites ;
elle ne reconstruit ni tentative manquante ni coût d'appel. Une ligne d'import ou de cache ne crée
jamais une latence fournisseur.

Pour chaque population, le rapport publie `n`, minimum, P50, P95 et maximum en millisecondes. Les
valeurs sont triées par ordre croissant et le nearest-rank utilise un rang indexé à partir de un :

```text
P50 = valeur au rang ceil(0,50 * n)
P95 = valeur au rang ceil(0,95 * n)
```

Si `n = 0`, les quatre valeurs sont absentes et l'état est `NOT_MEASURED`. J8 ne publie jamais une
latence zéro pour représenter une absence de mesure.

## 8. Complétude normalisée

La complétude s'appuie sur le modèle normalisé, pas sur une relecture opportuniste du JSON brut.
Elle conserve les distinctions `COMPLETE`, `PARTIAL`, `EMPTY_VALID`, `UNAVAILABLE`, `MISSING` et
incompatible. Les agrégats sont ventilés lorsque les jointures le permettent par endpoint,
compétition, saison et état du match ; une dimension absente rejoint le groupe littéral `UNKNOWN`.

Pour les trois familles J5, le rapport publie la taille de population et la distribution
`COMPLETE`/`PARTIAL`/`EMPTY_VALID`/`UNAVAILABLE`. La complétude numérique est exactement
`sum(present_signals) / sum(expected_signals)` pour les observations où `expected_signals > 0`.
Un 404 `UNAVAILABLE`, une famille absente et une liste vide valide ne sont jamais confondus ;
`UNAVAILABLE` n'est jamais converti en zéro pour cent et `EMPTY_VALID` reste disponible sans être
strictement complet. Les cinq emplacements J7 — état, détail, statistiques, incidents et
compositions — servent à qualifier le dossier, mais J8 ne modifie aucun export J7.

## 9. Dossier direct exploitable

La population ciblée est le nombre de `provider_event_id` distincts des unités J4 phase 1,
J4 phase 2 ou J5 en mode `GUARDED_PROVIDER` dans la fenêtre. Les doublons de tentatives ou de
familles ne créent pas un second dossier.

À l'instant `asOf`, un dossier direct exploitable exige simultanément :

1. un état canonique issu directement du fournisseur ;
2. un détail issu directement du fournisseur ;
3. une observation directe pour chacune des familles J5 statistiques, incidents et compositions ;
4. pour chacune de ces trois familles, un statut `COMPLETE`, `PARTIAL` ou `EMPTY_VALID` ;
5. aucune composante absente, `UNAVAILABLE`, incompatible, import-only ou synthétique.

Un dossier strictement complet satisfait les mêmes règles et possède ses trois familles J5 à
`COMPLETE`. L'état `PARTIAL` ou `EMPTY_VALID` rend donc un dossier exploitable mais pas strictement
complet. La date de la preuve sélectionnée et sa provenance restent visibles dans l'empreinte de
population.

## 10. Coût en appels

Les trois métriques portent toujours leurs numérateurs, leurs dénominateurs éventuels, leur niveau
de preuve et leur état :

```text
discoveryOverhead =
  count(tentatives J3) + count(tentatives de découverte tournoi)

marginalCallsPerExploitableDossier =
  (count(tentatives J4 phase 2) + count(tentatives J5))
  / count(dossiers directs exploitables)

effectiveCallsPerExploitableDossier =
  count(toutes les tentatives directes de la fenêtre)
  / count(dossiers directs exploitables)
```

`discoveryOverhead` est un compte absolu et n'est jamais divisé. Les ratios marginal et effectif
sont `NOT_MEASURED` lorsque le nombre de dossiers directs exploitables vaut zéro ; les numérateurs
restent affichables comme comptes. Les divisions utilisent les nombres de dossiers distincts, pas
le nombre d'observations, de snapshots ou de familles.

Un coût exact exige `FULL_ATTEMPT_LEDGER`. `RESPONSE_ONLY` expose séparément les réponses persistées,
mais ne fournit ni nombre de tentatives, ni minorant publié du coût en appels : ces métriques restent
`NOT_MEASURED`. `LEGACY_BASELINE` ne fournit aucun coût en appels. Imports, fixtures et cache hits
contribuent zéro au numérateur du ledger instrumenté sans être promus en preuve historique d'un
coût nul.

## 11. Corrections tardives J6

J8 réutilise la projection, le diff sémantique et le classifieur J6 sans dupliquer leurs règles ni
réécrire les observations. La paire prédécesseur/version est calculée dans la sous-série
`DIRECT_LOCAL_ENDPOINT` : un import ou une fixture intercalé reste dans sa strate et ne rompt pas
la paire directe. La preuve d'état transporte l'identifiant, le statut et l'heure du tout dernier
état direct précédant la version ; le classifieur J6 existant décide ensuite si ce statut est
terminal. Ces identifiants immuables entrent dans le hash de population. Toute cette preuve
provient de la même lecture bornée à `asOf` ; la classification exacte ne relit ni l'historique
courant ni les occurrences. Seules les observations dont l'acquisition est
`DIRECT_LOCAL_ENDPOINT`, classées
`LATE_ENRICHMENT` ou `LATE_CORRECTION`,
contribuent aux nombres et délais de correction tardive. `LOCAL_REPARSE`, `SYNTHETIC_CHANGE`,
`SEMANTICALLY_UNCHANGED` et doublon technique restent séparés ; imports et fixtures restent dans
leurs strates propres.

Le délai part de l'état terminal applicable et va jusqu'à l'heure de réception de la version
tardive. Sa distribution suit le même nearest-rank que les latences. Sans version fournisseur
éligible, l'état est `NOT_MEASURED`.

## 12. Interface HTML locale

J8 expose une seule route :

```text
GET /benchmark
```

Elle produit uniquement `text/html` via Thymeleaf. Le formulaire GET filtre seulement la fenêtre ;
elle ne contient aucun formulaire de mutation, POST, endpoint JSON, téléchargement, jeton local,
appel fournisseur ou lien d'activation réseau.

| Situation | Réponse |
|---|---|
| fenêtre absente ou valide | `200`, rapport HTML |
| population vide | `200`, états `NOT_MEASURED` |
| une borne, valeur invalide, trop longue ou ordre invalide | `400 INVALID_BENCHMARK_WINDOW` |
| preuve locale contradictoire | `422 INCOHERENT_LOCAL_EVIDENCE` |
| PostgreSQL indisponible | `503 LOCAL_DATABASE_UNAVAILABLE` |
| `Accept: application/json` | `406` |
| `POST /benchmark` | `405` |

Toutes les réponses, y compris erreurs et variante `;jsessionid`, portent les en-têtes anti-cache,
expiration immédiate et `X-Robots-Tag: noindex, nofollow, noarchive`. Le template échappe les
valeurs. Aucun message ne divulgue SQL, stack trace, chemin local, payload, URI, header, cookie,
jeton ou secret.

## 13. Markdown déterministe et export one-shot

`J8BenchmarkMarkdownRenderer` reçoit le même `J8BenchmarkReport` que l'interface. Le Markdown
contient la fenêtre, `asOf`, le hash de population, les niveaux de preuve, les états, les formules,
les numérateurs, les dénominateurs et les limites. Il ne contient aucune donnée brute.

L'ordre des sections, endpoints, ventilations, parseurs et dimensions est stable. Le fichier est
UTF-8, avec fins de ligne LF et contenu déterministe. L'exporteur Spring utilise
`WebApplicationType.NONE` et une `MapPropertySource` ajoutée en première position avant le refresh
pour forcer chaque gate fournisseur, Playwright, polling et scheduling à `false`. Il n'accepte
aucun argument CLI et le script refuse de fonctionner tant que l'application normale écoute sur
le port local. Une campagne ou tentative immuable sans résultat reste exportable comme preuve
`PARTIAL`/`INCOMPLETE_ATTEMPT` ; elle n'est jamais effacée ni retentée. L'export écrit uniquement :

```text
exports/j8/J8-BENCHMARK-REPORT-<asOf UTC compact>.md
```

Le nom est construit par le programme ; aucun paramètre ne permet de choisir un autre chemin ou
d'écraser un fichier arbitraire. Les répertoires physiques `exports` et `exports/j8` sont validés
sans suivre de lien et les liens symboliques, jonctions ou reparse points sont refusés. Le script
affiche le chemin créé et son SHA-256. Le répertoire
runtime `exports/j8` reste ignoré par Git. Un éventuel rapport final sous `docs/benchmark` ne peut
être ajouté qu'après exécution du runbook, qualification humaine et décision propriétaire.

## 14. Invariants de sécurité et limites

- `server.address=127.0.0.1` reste effectif ;
- aucun test standard ou d'intégration n'appelle SofaScore ni ne démarre Playwright ;
- aucun nouvel endpoint ou URI fournisseur n'est ajouté ;
- aucun retry, polling, scheduler, watcher, live, proxy, cookie ou état de session n'est ajouté ;
- aucune charge, répétition automatique ou augmentation de concurrence n'est autorisée ;
- aucun payload brut n'entre dans un agrégat, un rapport ou un log ;
- les preuves brutes, observations normalisées et résultats J8 restent séparés ;
- aucune donnée n'est envoyée au VPS, au Betting Project, au cloud ou à une production ;
- J8 ne compare pas à une source de contrôle externe et ne prétend pas mesurer l'exactitude que le
  corpus local ne peut pas prouver ;
- la décision de maintien, abandon ou intégration appartient à J9 et au propriétaire.

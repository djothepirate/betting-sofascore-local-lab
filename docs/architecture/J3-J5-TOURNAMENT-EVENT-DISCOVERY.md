# Architecture J3 → J5 — découverte tournoi → rencontres

## 1. Statut et objectif

Cette évolution relie une collecte J3 paginée et complète à des identités canoniques directement
exploitables par J5. Elle ajoute au tableau de bord une liste des occurrences de tournois
réellement observées, puis un appel manuel distinct et confirmé permettant de découvrir les
rencontres de l'occurrence sélectionnée.

```text
EXPERIMENTAL
LOCAL_ONLY
NOT_PRODUCTION_APPROVED
NO_CRITICAL_DEPENDENCY
VALIDATED
```

Elle ne lance ni J5, ni J4, ni un appel fournisseur au chargement de la page. Elle ne crée aucun
polling, aucune tâche planifiée, aucun retry et aucune dépendance du Betting Project principal.

La fiche structurelle faisant autorité est
`docs/requirements/J3-J5-TOURNAMENT-EVENT-DISCOVERY-RULES.md`.

## 2. Chaîne fonctionnelle

```text
confirmation J3 puis choix exclusif
        ├─ cache SCHEDULED_EVENTS / GET directs page 1..N
        └─ lot local des corps JSON page-1..page-N, zéro transport/cache
        │  même parseur, provenance MANUAL_LOCAL_JSON_IMPORT
        ▼
collecte J3 1..N terminée COMPLETED
        │ preuve terminale exacte du processus courant
        ▼
relecture et contrôle des snapshots SCHEDULED_EVENTS référencés
        │ toutes les pages contiguës, intègres et parsables
        ▼
catalogue serveur d'occurrences tournament.id
        │ category.name présent + filtre timezoneEventCount ∩ offsets(date, Europe/Paris)
        │ <select> : libellé tournament.name - tournament.category.name, valeur tournament.id
        ▼
préparation + confirmation exacte de cinq minutes
        │ aucun réseau pendant la sélection ou la préparation
        ▼
choix exclusif après confirmation
        ├─ cache TOURNAMENT_SCHEDULED_EVENTS ou un GET fournisseur au maximum
        │  uniqueTournament.id numérique résolu côté serveur
        └─ import local du seul corps JSON, zéro réseau et zéro cache fournisseur
        ▼
snapshot brut → parsing → projection Europe/Paris → contrôle du compte
        │ validation complète avant écriture normalisée
        ▼
transaction d'identités et d'observations canoniques
        │
        ▼
liens /events/{canonicalEventId}/statistics?zone=Europe%2FParis
```

## 3. Catalogue exact issu de J3

### 3.1 Source autorisée

`J3TournamentCatalogService` consulte la preuve J3 la plus récente du **processus courant**. Cette
preuve doit elle-même être terminale `COMPLETED`; il n'existe aucun repli vers un lot plus ancien
si la preuve courante est absente, incomplète ou invalide.

Le catalogue est reconstruit uniquement depuis les `snapshotId` exacts de cette preuve, y compris
lorsque les pages J3 ont été résolues par le cache ou importées sous forme d'un lot JSON local.
Chaque page est relue depuis son snapshot brut et contrôlée avant d'être agrégée :

1. endpoint logique `SCHEDULED_EVENTS` ;
2. clé `SCHEDULED_EVENTS|date={DATE}|page={PAGE}` exacte ;
3. statut `PARSED`, HTTP réussi et parseur `scheduled-events-v1` ;
4. taille et SHA-256 identiques aux octets relus et à la preuve J3 ;
5. forme `SCHEDULED_TOURNAMENT_LIST`, tableau `scheduled` et zéro événement fabriqué ;
6. pages uniques, contiguës à partir de 1 ;
7. `hasNextPage=true` sur chaque page non terminale, puis `false` sur la dernière.

La preuve et le snapshot doivent aussi porter une provenance cohérente : les résolutions
`PROVIDER`/`CACHE` référencent `DIRECT_LOCAL_ENDPOINT`; une résolution `LOCAL_JSON_IMPORT`
référence `MANUAL_LOCAL_JSON_IMPORT`. Toute discordance refuse le catalogue.

`receivedAt` traverse une colonne PostgreSQL `timestamptz`, dont la résolution est la microseconde,
alors que la preuve du processus conserve l'`Instant` original à la nanoseconde. La comparaison
considère donc identiques uniquement deux horodatages dont l'écart absolu est strictement inférieur
à une microseconde. Un écart d'une microseconde ou davantage reste un
`SNAPSHOT_METADATA_MISMATCH`. Tous les autres champs de métadonnées et d'intégrité restent exacts ;
cette règle ne permet ni de substituer un snapshot, ni de relâcher la clé date/page ou le SHA-256.

Une seule divergence rend le catalogue entier indisponible. Une collecte interrompue, échouée ou
dont la pagination n'est pas terminale ne publie aucune option appelable.

### 3.1 bis Import atomique des pages J3

La confirmation J3 expose deux actions terminales exclusives. La voie directe conserve le cache
frais par page et les GET bornés existants. La voie locale reçoit en une fois les fichiers
`page-1.json` à `page-N.json`, sans trou ni doublon. Avant de réclamer l'intention, le contrôleur et
le service vérifient : nom et ordre, une à 25 pages, 5 Mio maximum par page, 25 Mio maximum pour le
lot, absence de contenu sensible, parsing `scheduled-events-v1`, forme
`SCHEDULED_TOURNAMENT_LIST` et chaîne `true…true,false` de `hasNextPage`.

Le lot accepté partage `J3SingleCallGuard` avec la voie directe. Chaque page est ensuite persistée
sous sa clé `SCHEDULED_EVENTS|date=…|page=…`, avec `MANUAL_LOCAL_JSON_IMPORT`, puis classée. Aucun
cache fournisseur n'est lu ou écrit, aucun délai réseau n'est appliqué et aucun transport n'est
construit. La preuve minimisée v5 compte séparément transports, cache hits et imports. Une erreur
de lot préliminaire laisse l'intention confirmée disponible ; un terminal direct, notamment
`HTTP_FORBIDDEN`, ne bascule jamais automatiquement vers l'import et requiert une nouvelle
séquence opérateur.

### 3.2 Sémantique d'une option

Une occurrence actionnable conserve séparément :

| Valeur | Usage |
|---|---|
| `tournament.id` | identité de la phase concrète ; valeur du `<select>` et filtre de projection |
| `tournament.name` | première partie du libellé visible de l'option |
| `tournament.category.name` | portée géographique descriptive et seconde partie du libellé visible |
| `tournament.uniqueTournament.id` | identifiant numérique du chemin de la seconde requête |
| `tournament.uniqueTournament.name` | métadonnée descriptive contrôlée, jamais un segment d'URI |
| `timezoneEventCount` | éligibilité locale de l'option, puis cohérence de nombre lorsque l'offset de la journée est exploitable |
| `sourceSnapshotIds` | provenance des pages J3 ayant porté l'occurrence |

Le libellé est construit côté serveur sous la forme exacte
`tournament.name - tournament.category.name`. Le navigateur poste uniquement `tournament.id`. Le
serveur reconstruit le catalogue et résout la date, l'identifiant du tournoi unique, les noms, la
catégorie, les compteurs et les snapshots sources. Une valeur falsifiée ou devenue obsolète est
donc refusée avant tout transport. La catégorie ne participe jamais à l'URI ni à l'identité.

Deux phases partageant le même `uniqueTournament.id` restent deux options distinctes. Une
occurrence sans tournoi unique ou sans `tournament.category.name` est parsable pour J3 mais exclue
de la liste actionnable : aucune portée de remplacement n'est inventée. Un même `tournament.id`
répété à l'identique est regroupé ; une contradiction sur son nom, sa catégorie, son tournoi unique
ou ses compteurs invalide le catalogue.

### 3.3 Politique d'éligibilité par offset local

Avant de créer les options, `J3TournamentCatalogService` dérive depuis la date de la preuve J3 les
offsets UTC effectivement applicables pendant la journée civile `Europe/Paris`. Les clés de
`timezoneEventCount` étant exprimées en secondes, l'intersection doit être non vide :

```text
keys(timezoneEventCount) ∩ applicableOffsets(collectionDate, Europe/Paris) ≠ ∅
```

Le 2026-08-20, l'ensemble attendu vaut `{7200}`. En hiver il vaut `{3600}` ; une journée de
bascule DST peut porter `{3600,7200}` et l'une ou l'autre clé suffit pour rendre l'option
sélectionnable. La valeur associée, y compris `0`, est conservée pour le contrôle ultérieur mais ne
sert pas de booléen d'éligibilité.

Une occurrence sans catégorie géographique exploitable ou sans clé locale applicable, y compris
un tableau vide, est comptée parmi les exclusions et n'entre pas dans
`J3TournamentCatalog.options`. Puisque `resolve(tournamentId)` reconstruit le même catalogue, une
valeur postée manuellement est refusée avant préparation. Le filtre n'emploie jamais le fuseau
système et n'exécute aucun transport.

### 3.4 Durée de vie volontairement en mémoire

La preuve terminale J3 n'est pas un manifeste durable. Après redémarrage, les snapshots et les
checkpoints de cache restent en PostgreSQL, mais ils ne suffisent pas à prouver qu'ils appartiennent
au même lot complet. Le catalogue redevient indisponible jusqu'à une nouvelle collecte J3
explicite, laquelle peut être satisfaite par ses caches frais et recréer une preuve `COMPLETED`.

Cette règle empêche d'assembler implicitement des pages historiques de dates, de tentatives ou de
parsers différents. Aucune migration de manifeste J3 n'est introduite par cette évolution.

## 4. Requête fermée par identifiant numérique

Le nouvel endpoint logique est :

```text
TOURNAMENT_SCHEDULED_EVENTS
```

La requête de domaine `TournamentScheduledEventsProviderRequest` construit exclusivement :

```text
GET https://www.sofascore.com/api/v1/unique-tournament/{UNIQUE_TOURNAMENT_ID}/scheduled-events/{DATE}
```

avec :

```text
UNIQUE_TOURNAMENT_ID = tournament.uniqueTournament.id, entier strictement positif
DATE                 = date ISO de la collecte J3, résolue côté serveur
REQUEST_KEY          = TOURNAMENT_SCHEDULED_EVENTS|date={DATE}|uniqueTournamentId={ID}
```

`tournament.name`, `uniqueTournament.name`, un slug et `tournament.id` ne peuvent jamais alimenter
le chemin. L'origine est exactement `https://www.sofascore.com`, sans port, query, fragment,
user-info, redirection, proxy, cookie, jeton, compte ou session. Aucun composant MVC ne reçoit une
URI libre.

Le catalogue logique général reste `manualOnly=true`, `callable=false` et sans URI. La voie
spéciale ne modifie pas `ConnectorGate` et ne déverrouille pas le profil Maven live.

## 5. Parser `tournament-scheduled-v1`

`TournamentScheduledEventsV1Parser` attend un document JSON strict dont la racine minimale est :

```json
{
  "events": []
}
```

`events` est obligatoire et peut être vide. `hasNextPage` peut être absent ou valoir `false`; la
valeur `true` est incompatible, car aucune pagination n'est définie pour cette route.

Chaque événement exige notamment des identifiants numériques positifs, un `startTimestamp` en
secondes Unix représentable par `Instant`, les deux équipes, un statut, `tournament.id/name` et
`tournament.uniqueTournament.id/name`. Les chaînes numériques ne sont pas coercies. Les propriétés
JSON dupliquées, les types incohérents, les caractères de contrôle, les timestamps invalides et le
contenu résiduel après la racine sont refusés. Les champs auxiliaires non normalisés restent dans
le snapshot brut et ne produisent que des avertissements bornés lorsqu'ils sont admissibles.

Le parser ne fabrique ni saison, ni stade, ni tour, ni score. Une incompatibilité ne produit aucun
objet canonique partiel.

## 6. Cache, import local et migrations V24/V25

Le cache dédié est consulté avant le transport avec la clé exacte `(date,
uniqueTournamentId)`, le parseur courant et un TTL de dix minutes. Un cache hit intègre fournit le
snapshot brut déjà conservé : il déclenche zéro transport, zéro nouvelle persistance brute et
reste soumis au parsing, à la projection et aux contrôles courants.

Sur cache miss, l'ordre est : transport gardé, persistance du snapshot `RAW_ONLY`, classification,
parsing, projection, classification `PARSED`, puis enregistrement du checkpoint de cache. Une
réponse structurellement valide peut donc demeurer une preuve et un cache même si un contrôle de
nombre ultérieur interdit sa normalisation ; le cache ne contourne jamais ce contrôle.

La migration append-only
`V24__j3_j5_tournament_scheduled_events_cache.sql` étend uniquement la contrainte fermée de
`provider_response_cache.logical_endpoint` afin d'accepter :

```text
SCHEDULED_EVENTS
EVENT_DETAILS
TOURNAMENT_SCHEDULED_EVENTS
```

V24 ne modifie aucune migration V1–V23, ne crée pas de manifeste de collecte et ne change pas les
tables canoniques. Les checks d'intégrité du cache continuent d'exiger endpoint, clé, snapshot,
statut, parseur, taille, SHA-256 et fraîcheur cohérents.

L'autre action disponible après la même confirmation reçoit uniquement un fichier contenant le
corps JSON du second endpoint. Elle n'appelle pas le transport, ne consulte ni n'alimente le cache
fournisseur et persiste le brut sous `MANUAL_LOCAL_JSON_IMPORT` avant d'utiliser le même parseur,
la même projection et la même transaction canonique. Le scanner sensible refuse avant
consommation de la confirmation tout HAR, en-tête `Authorization`/`Cookie`, secret, jeton ou clé
privée ; la taille est bornée à 5 Mio. Nom de fichier, chemin et métadonnées multipart ne sont pas
persistés.

La migration append-only `V25__manual_local_json_import_provenance.sql` élargit seulement la
contrainte fermée `provider_snapshot.acquisition_mode` et fait entrer ce mode dans la clé de
déduplication brute. Deux payloads identiques acquis directement et importés manuellement restent
donc deux preuves distinctes ; deux imports identiques sous la même clé restent dédupliqués. Les
caches J3/J4 et `provider_response_cache` continuent à n'accepter que
`DIRECT_LOCAL_ENDPOINT`. La même provenance sert maintenant aux pages J3 importées et au corps du
second endpoint importé ; leur endpoint logique et leur clé empêchent toute confusion. Les
snapshots importés restent inspectables et soumis à la rétention J6.

## 7. Contrôle opérateur et réseau

La machine `TournamentEventDiscoveryControlService` commence à `LOCKED` :

| Transition | Effet |
|---|---|
| `prepare(tournament.id)` | revalide configuration et catalogue, crée une intention `AWAITING_CONFIRMATION`, zéro transport |
| confirmation exacte et acquittée | revalide le catalogue, consomme l'intention et passe à `EXECUTING` |
| fin valide | `COMPLETED`; une nouvelle préparation explicite est permise |
| erreur | `FAILED_LOCKED` jusqu'au redémarrage |
| expiration à cinq minutes | `EXPIRED_LOCKED` jusqu'au redémarrage |
| arrêt opérateur | `STOPPED_LOCKED` jusqu'au redémarrage |

Une confirmation autorise au plus une exécution. L'opérateur choisit soit la voie directe qui, sur
cache miss, autorise au plus un GET, soit l'import local qui autorise zéro transport. Le
`ManualProviderRequestCoordinator` sérialise uniquement la voie réseau avec J3/J4/J5 et conserve
le délai minimal commun de trois secondes entre deux départs fournisseur. Tout incident terminal
interdit le retry ; un échec du GET ne bascule jamais automatiquement vers l'import.

L'éligibilité exige simultanément l'opt-in général, l'opt-in J3, l'opt-in de découverte, le stockage
brut, une concurrence maximale de un, l'absence de live/polling et l'ensemble exact des endpoints
actifs. La configuration minimale correspondante est documentée uniquement comme **contrat de
qualification future** :

```text
SOFASCORE_ENABLED=true
SOFASCORE_J3_QUALIFICATION_ENABLED=true
SOFASCORE_TOURNAMENT_EVENT_DISCOVERY_ENABLED=true
SOFASCORE_J4_EVENT_DETAILS_QUALIFICATION_ENABLED=false
SOFASCORE_J4_EVENT_DETAILS_PHASE2_ENABLED=false
SOFASCORE_J5_EVENT_DATA_QUALIFICATION_ENABLED=false
SOFASCORE_BASE_URL=https://www.sofascore.com
SOFASCORE_ALLOWED_ENDPOINTS=SCHEDULED_EVENTS,TOURNAMENT_SCHEDULED_EVENTS
```

Ces valeurs rendent les gardes cohérentes mais **n'autorisent pas un appel réel**. Une décision
humaine distincte reste obligatoire avant toute exécution fournisseur. Les valeurs par défaut sont
`false`, base URL vide et allowlist vide.

## 8. Projection `Europe/Paris`

Soient `T = tournament.id` sélectionné et `U = uniqueTournament.id` utilisé dans l'URI.

Tous les candidats doivent porter `event.tournament.uniqueTournament.id == U` ainsi que le nom du
tournoi unique résolu. Une contradiction invalide la réponse entière. Un candidat portant le même
`U` mais un autre `tournament.id` appartient à une autre phase : il reste dans le brut, est compté
comme exclu et n'est pas normalisé.

Après déduplication par `event.id`, un événement de la phase `T` est retenu seulement si son
instant appartient à la journée locale demandée :

```java
ZoneId zone = ZoneId.of("Europe/Paris");
Instant fromInclusive = date.atStartOfDay(zone).toInstant();
Instant toExclusive = date.plusDays(1).atStartOfDay(zone).toInstant();
```

La fenêtre `[fromInclusive, toExclusive)` traite les journées de 23 ou 25 heures. Le fuseau
système, une date UTC implicite ou un offset ajouté manuellement ne sont jamais utilisés.

Les doublons `event.id` strictement identiques sont regroupés et comptés. Deux occurrences du même
ID avec des champs normalisés différents rendent la projection incompatible.

## 9. Contrôle de nombre

Le nombre réel est calculé après contrôle de `U`, déduplication, filtre de phase `T` et filtre de
date `Europe/Paris`. `timezoneEventCount` est une preuve de cohérence, jamais une identité :

| État | Condition | Normalisation |
|---|---|---|
| `COUNT_VERIFIED` | clé de l'offset présente et valeur égale | autorisée |
| `COUNT_NOT_VERIFIABLE` | compteur vide ou clé absente | autorisée, état explicite |
| `COUNT_NOT_VERIFIABLE_DST_TRANSITION` | changement d'offset dans la journée | autorisée, aucune clé arbitraire |
| `COUNT_MISMATCH` | valeur présente mais différente | interdite |

La sémantique fournisseur de ce compteur reste à qualifier humainement. Un tableau ou objet vide
signifie « non vérifiable », jamais « zéro attendu ».

## 10. Atomicité, provenance et minimisation

L'ordre global préserve la séparation entre brut et normalisé :

```text
choix exclusif : cache/transport direct OU import local
→ snapshot brut, occurrence, taille et SHA-256
→ classification
→ parsing versionné
→ validation tournoi unique
→ filtres phase/date
→ déduplication
→ contrôle de nombre
→ transaction canonique unique
```

`TournamentCanonicalEventPersistenceService` reçoit uniquement une projection entièrement validée.
Sa transaction crée ou retrouve les UUID déterministes de chaque `event.id`, puis insère ou
déduplique leurs observations. Une incompatibilité, un doublon conflictuel, un `COUNT_MISMATCH` ou
une erreur de persistance publie zéro observation du lot. Le snapshot brut déjà écrit demeure une
preuve distincte et n'est pas effacé par un échec de normalisation.

Chaque observation conserve `SOFASCORE`, `snapshotId`, `payloadSha256`,
`tournament-scheduled-v1` et `receivedAt`. Le snapshot référencé porte explicitement
`DIRECT_LOCAL_ENDPOINT` ou `MANUAL_LOCAL_JSON_IMPORT`; cette provenance n'est jamais déduite du
contenu. Le résultat Web est minimisé : compteurs, identités canoniques et provenance bornée, sans
URI, header ou payload.

## 11. Passage direct à J5, sans J4

Chaque rencontre retenue expose :

```text
/events/{canonicalEventId}/statistics?zone=Europe%2FParis
```

J5 résout l'identifiant fournisseur depuis l'UUID canonique déjà persisté. Aucun appel
`EVENT_DETAILS`, aucune campagne J4 et aucune saisie manuelle d'un `event.id` ne sont requis pour
atteindre ce parcours. L'absence de détail J4 reste un état local acceptable.

Le lien n'exécute pas J5. Sa campagne conserve son opt-in, sa préparation, sa confirmation et son
ordre propre :

```text
EVENT_STATISTICS → EVENT_INCIDENTS → EVENT_LINEUPS
```

Après préparation, l'opérateur choisit soit les trois GET gardés, soit l'import local des trois
corps JSON dans le même ordre. Le lot local est scanné et prévalidé en totalité avant claim,
conserve trois snapshots `MANUAL_LOCAL_JSON_IMPORT`, produit zéro appel fournisseur et n'utilise
aucun cache. Une enveloppe fermée `error.code=404` représente une famille indisponible ; un 403 ou
un corps incompatible refuse le lot. Un terminal direct ne bascule pas vers l'import : un
redémarrage et une nouvelle préparation sont obligatoires.

L'évolution réduit donc la dépendance fonctionnelle à la saisie J4 sans élargir l'autorisation
réseau de J5 et fournit une voie de qualification manuelle qui ne simule aucun navigateur.

### 11.1 Portée postérieure de WO-010

Le flux décrit ci-dessus reste la campagne J5 unitaire contrôlée par `J5RealControlService`. Son
choix exclusif entre trois GET et trois corps locaux, ainsi que le redémarrage après terminal direct,
ne sont ni étendus ni assouplis par WO-010.

WO-010 ajoute sous `/j5-import-batches` un flux manuel hors ligne distinct pour `1..25` événements
canoniques existants d'une date et d'une zone. Il utilise son propre contrôle mémoire, accepte
`sofascore.enabled=false` comme `true` et ne dépend ni du transport, ni du cache, ni du coordinateur,
ni de `J5RealControlService`. Un nouveau plan y est
permis après tout état terminal ; ce comportement ne constitue jamais un fallback ou un retry de
la campagne unitaire. L'architecture détaillée est définie dans
`docs/architecture/J5-OFFLINE-MULTI-MATCH-IMPORT.md`.

## 12. Validation et frontières

Les suites standard et PostgreSQL doivent couvrir : catalogue exact multi-pages, intégrité des
snapshots, aller-retour `timestamptz` à la microseconde, refus d'une dérive temporelle supérieure,
import J3 atomique 1..N sans transport/cache, provenance et preuve v5, refus des lots troués ou
non terminaux,
conservation de `category.name`, libellé géographique exact, exclusion sans catégorie, conflit de
catégorie, falsification MVC, requête numérique, origine fermée, parser strict, cache hit/miss,
contrôle et expiration, projection Paris et DST, compteurs, doublons, transaction canonique, liens
J5, migration V23→V24, upgrade V24→V25, séparation des modes d'acquisition, refus des fichiers
sensibles ou surdimensionnés, import local de découverte sans interaction de transport/cache et
lot J5 atomique de trois corps avec compteurs de provenance distincts. Tous les transports sont
interceptés ou simulés ; Maven ne contacte jamais le fournisseur.

Restent hors périmètre : manifeste J3 durable, cascade sur tous les tournois, pagination inventée
du second endpoint, lancement automatique de J5, appel implicite J4, autre sport ou origine,
polling, planification, retry, export brut et transfert vers le VPS.

Le lot multi-match de WO-010 ne contredit pas l'exclusion du lancement automatique : sa sélection,
sa préparation, sa confirmation et son exécution restent quatre gestes Web locaux explicites, sans
scheduler, watcher, polling ou acquisition automatique de fichiers.

La readiness technique est consignée dans
`docs/validation/J3-J5-TOURNAMENT-EVENT-DISCOVERY-TECHNICAL-READINESS-20260820.md`.

# Règles de découverte tournoi → rencontres J3/J5

> Fiche structurelle de référence pour l'évolution qui transforme une collecte J3 complète de
> tournois programmés en une sélection de rencontres directement exploitable par J5.
>
> Cette fiche décrit uniquement le contrat local observé et les décisions de normalisation du
> laboratoire. Elle ne constitue ni une documentation officielle de SofaScore, ni une approbation
> de production.

## 1. Statut et portée

La fonctionnalité conserve les statuts du dépôt :

```text
EXPERIMENTAL
LOCAL_ONLY
NOT_PRODUCTION_APPROVED
NO_CRITICAL_DEPENDENCY
```

Elle couvre une collecte J3 paginée, puis l'acquisition des rencontres du tournoi choisi. Après
la confirmation de chaque étape, l'opérateur choisit explicitement une voie directe ou une voie
d'import local :

```text
GET /api/v1/sport/football/scheduled-tournaments/{DATE}/page/{PAGE}
GET /api/v1/unique-tournament/{UNIQUE_TOURNAMENT_ID}/scheduled-events/{DATE}
```

Le premier endpoint fournit des occurrences de tournois disponibles et leur pagination. Pour J3,
une collecte utilise soit la résolution directe page par page — cache frais puis GET en cas de
miss — soit un lot complet de corps JSON `page-1.json` à `page-N.json`, sans mélange des sources.
Le second endpoint fournit les rencontres d'un tournoi unique pour une date et accepte, lui aussi,
soit son GET direct, soit l'import de son seul corps JSON. Un import ne constitue pas un appel
fournisseur et n'autorise ni capture HAR, ni en-tête, ni cookie, ni donnée de session. Aucune voie
ne lance J5 automatiquement : elle crée les identités canoniques et les liens depuis lesquels
l'opérateur peut préparer séparément une campagne J5.

## 2. Correction d'identité obligatoire

Le segment `{UNIQUE_TOURNAMENT_ID}` est alimenté exclusivement avec :

```text
tournament.uniqueTournament.id
```

Il ne doit jamais être alimenté avec :

```text
tournament.uniqueTournament.name
tournament.uniqueTournament.slug
tournament.id
tournament.name
```

Dans l'exemple analysé :

```text
tournament.id = 119880
tournament.name = UEFA Champions League, Playoff Round
uniqueTournament.id = 7
uniqueTournament.name = UEFA Champions League
```

La route correcte contient donc `/unique-tournament/7/`. Le nom du tournoi unique est une
métadonnée descriptive potentiellement mutable et localisable ; il n'est ni une identité, ni une
valeur sûre de chemin.

Les quatre valeurs suivantes restent séparées :

| Valeur | Rôle local |
|---|---|
| `tournament.id` | identité de la compétition ou phase concrète sélectionnée |
| `tournament.name` | première partie du libellé visible de l'option |
| `tournament.category.name` | portée géographique descriptive, seconde partie du libellé visible |
| `uniqueTournament.id` | identité numérique utilisée par la seconde requête |
| `uniqueTournament.name` | métadonnée descriptive affichable, jamais une clé |

Plusieurs `tournament.id` peuvent partager le même `uniqueTournament.id`. Ils restent des options
distinctes et ne sont jamais dédupliqués par l'identité du tournoi unique.

## 3. Premier endpoint : catalogue paginé J3

### 3.1 Enveloppe

La forme qualifiée est :

```json
{
  "scheduled": [],
  "hasNextPage": false
}
```

Règles :

- `scheduled` est un tableau obligatoire ;
- `hasNextPage` est un booléen obligatoire ;
- la collecte commence toujours à la page 1 ;
- chaque page suivante est appelée uniquement si la page courante, persistée puis parsée,
  contient `hasNextPage=true` ;
- la collecte est complète uniquement après une page compatible portant `hasNextPage=false` ;
- le plafond local de 25 pages reste bloquant ;
- aucune collecte partielle, interrompue ou incompatible ne publie de catalogue de sélection.

### 3.1 bis Acquisition directe ou import local du lot J3

La confirmation J3 existante autorise une exécution unique. L'opérateur choisit exactement une
des deux sources suivantes ; une même collecte ne les mélange jamais :

| Source J3 | Transport fournisseur | Cache fournisseur | Provenance des snapshots |
|---|---:|---:|---|
| résolution directe | de 0 à N selon les cache misses | lu puis alimenté après parsing | `DIRECT_LOCAL_ENDPOINT` |
| import local des pages | 0 | ni lu ni écrit | `MANUAL_LOCAL_JSON_IMPORT` |

Pour l'import, l'opérateur obtient manuellement hors de l'application les seuls corps JSON des
chemins successifs
`/api/v1/sport/football/scheduled-tournaments/{DATE}/page/{PAGE}`. Il les fournit ensemble sous
des noms qui permettent d'établir sans ambiguïté la suite `page-1.json`, `page-2.json`, …,
`page-N.json`. Les règles sont fail-closed :

- une à 25 pages, contiguës depuis 1, sans doublon ni trou ;
- chaque page non vide et limitée à 5 Mio, lot entier limité à 25 Mio ;
- scanner sensible avant consommation du claim : HAR, `Cookie`, `Authorization`, en-tête, jeton,
  clé privée ou donnée de session refusés ;
- prévalidation atomique de toutes les pages avec `scheduled-events-v1` et la forme
  `SCHEDULED_TOURNAMENT_LIST` avant toute persistance ;
- `hasNextPage=true` sur chaque page avant N et `hasNextPage=false` sur N ;
- persistance page par page sous les clés exactes
  `SCHEDULED_EVENTS|date={DATE}|page={PAGE}`, avec provenance
  `MANUAL_LOCAL_JSON_IMPORT`, avant classification ;
- aucun checkpoint de `provider_response_cache`, aucune attente de trois secondes et aucun
  transport fournisseur ;
- preuve terminale minimisée v5 indiquant séparément
  `LOCAL_JSON_IMPORT_PAGES`, `LOCAL_JSON_IMPORT_COUNT` et
  `PAGE_{n}_LOCAL_JSON_IMPORT_EXECUTED=YES`.

Le statut HTTP `200` et la latence nulle du snapshot importé décrivent l'acceptation locale du
corps, pas une réponse HTTP observée par le client Java. Une séquence invalide est refusée avant
de réclamer l'exécution : l'intention confirmée reste réutilisable après correction du lot. Une
collecte directe déjà terminale sur `HTTP_FORBIDDEN` reste en revanche verrouillée ; l'import
n'est jamais une reprise automatique et exige le réarmement, une nouvelle préparation et une
nouvelle confirmation explicite.

### 3.2 Occurrence `scheduled`

Une occurrence exploitable par le second endpoint contient au minimum :

| Chemin | Règle |
|---|---|
| `tournament.id` | entier 64 bits strictement positif |
| `tournament.name` | texte non vide |
| `tournament.category.name` | texte non vide requis pour rendre l'occurrence actionnable et afficher sa portée géographique |
| `tournament.uniqueTournament.id` | entier 64 bits strictement positif |
| `tournament.uniqueTournament.name` | texte non vide |
| `timezoneEventCount` | objet de compteurs indexé par offset UTC en secondes, ou tableau vide parsable mais non actionnable |

Le contrat J3 historique autorise l'absence de `uniqueTournament` avec un avertissement. Une telle
occurrence reste parsable mais n'est pas actionnable : elle ne doit jamais produire une URI ou un
identifiant inventé.

Le parseur `scheduled-events-v1` conserve également `tournament.category.name` lorsqu'un objet
`category` est présent. L'absence de cet objet reste compatible avec le contrat historique et ne
rend pas toute la page inutilisable, mais l'occurrence concernée n'est pas actionnable : aucun
libellé géographique de remplacement n'est inventé. Un objet `category` présent avec un `name`
absent, vide ou d'un type incompatible rend le payload structurellement incompatible.

### 3.3 Construction de la liste

Le catalogue est construit uniquement depuis la dernière preuve J3 terminale `COMPLETED` du
processus courant. Cette preuve fournit la date, les pages ordonnées et les `snapshotId` exacts,
qu'ils proviennent du cache, d'un transport fournisseur ou d'un import JSON local.

Avant publication de la liste, chaque snapshot est relu et contrôlé :

1. endpoint logique `SCHEDULED_EVENTS` ;
2. clé exacte `SCHEDULED_EVENTS|date={DATE}|page={PAGE}` ;
3. statut historique `PARSED` ;
4. taille et SHA-256 conformes aux octets conservés ;
5. parseur `scheduled-events-v1` ;
6. forme `SCHEDULED_TOURNAMENT_LIST` ;
7. pages contiguës depuis 1 ;
8. `hasNextPage=true` avant la dernière page et `false` sur la dernière.

La source de chaque page dans la preuve doit correspondre au mode d'acquisition immuable du
snapshot : `PROVIDER` ou `CACHE` exige `DIRECT_LOCAL_ENDPOINT`, tandis que
`LOCAL_JSON_IMPORT` exige `MANUAL_LOCAL_JSON_IMPORT`. Toute discordance refuse le catalogue ; un
snapshot importé n'est jamais présenté comme une réponse reçue directement.

La comparaison de `receivedAt` tient compte exclusivement de la résolution de persistance : la
preuve J3 conserve l'`Instant` à la nanoseconde, tandis que PostgreSQL restitue un `timestamptz` à la
microseconde. Un écart absolu strictement inférieur à une microseconde est équivalent ; un écart
supérieur ou égal à une microseconde est incompatible. Aucun autre contrôle n'est approximatif :
`snapshotId`, endpoint, clé date/page, HTTP, parseur, statut, taille et SHA-256 restent exacts.

La preuve J3 étant volontairement conservée en mémoire, un redémarrage invalide la liste. Les
snapshots restent en base, mais ils ne sont pas mélangés pour reconstituer implicitement un lot.
Une nouvelle collecte J3 explicite, éventuellement satisfaite par son cache frais, recrée une
preuve terminale.

### 3.4 Filtrage de la liste par offset local

La liste déroulante ne présente que les occurrences pour lesquelles le premier endpoint atteste
une programmation possible à la date J3 dans la zone de référence :

```text
Europe/Paris
```

Pour la date collectée `D`, le serveur calcule tous les offsets UTC réellement applicables pendant
la journée civile semi-ouverte `[début de D, début de D+1[` dans cette zone. Les offsets sont
exprimés en secondes, comme les clés de `timezoneEventCount` :

- `3600` correspond à UTC+1 ;
- `7200` correspond à UTC+2 ;
- une valeur négative correspond au même nombre de secondes à l'ouest d'UTC.

Une occurrence ayant un tournoi unique valide et un `tournament.category.name` exploitable devient
une option si et seulement si :

```text
keys(timezoneEventCount) ∩ applicableOffsets(D, Europe/Paris) ≠ ∅
```

La présence de la clé gouverne l'éligibilité ; sa valeur n'est pas interprétée comme un booléen.
Une clé applicable présente avec la valeur `0` reste donc actionnable. À l'inverse, un objet vide,
le tableau vide qualifié par J3 ou une table ne contenant que des offsets étrangers à la journée
de Paris excluent l'occurrence de la liste. La même exclusion s'applique lors de `resolve` côté
serveur : poster manuellement son `tournament.id` ne peut déclencher ni préparation ni transport.

Sur une journée ordinaire, un seul offset est applicable. Ainsi, le 2026-08-20, Paris est en
UTC+2 : la clé requise est `7200`. L'occurrence « UEFA Champions League, Playoff Round » de
l'exemple, dont les clés commencent à `18000`, est exclue ; « LaLiga », qui contient `7200`, est
conservée. En hiver, la clé cible devient `3600` sans changement de configuration.

Lors d'une bascule d'heure, deux offsets peuvent être applicables pendant la même date locale. La
présence de l'un ou l'autre rend l'occurrence sélectionnable ; aucune clé n'est choisie
arbitrairement pour comparer ensuite un nombre attendu. `ZoneId.systemDefault()`, un offset
`7200` codé en dur pour toute l'année ou un fuseau implicite sont interdits.

Ce filtre est entièrement local : il s'applique aux pages J3 déjà persistées, ne fabrique aucun
événement et ne déclenche aucun appel fournisseur.

### 3.5 Identité et déduplication des options

Une option conserve au minimum :

```text
collectionDate
tournamentId
tournamentName
tournamentCategoryName
uniqueTournamentId
uniqueTournamentName
timezoneEventCount
sourceSnapshotId
sourcePayloadSha256
sourceReceivedAt
parserVersion
```

Le libellé HTML est exactement construit avec le séparateur ASCII espace-tiret-espace :

```text
tournament.name + " - " + tournament.category.name
```

Par exemple : `LaLiga - Spain`. La catégorie est une métadonnée descriptive : elle ne devient ni
une identité, ni une clé de déduplication indépendante, ni un segment d'URI. La valeur postée reste
exclusivement `tournament.id`; le serveur résout et revalide toutes les autres valeurs depuis le
catalogue courant. Le navigateur ne fournit jamais un nom, une catégorie, une URI ou un
`uniqueTournament.id` libre à l'adaptateur réseau. Une occurrence sans catégorie ne peut pas être
réactivée en postant manuellement son identifiant.

Règles de collision :

- même `tournament.id` et mêmes champs structurants : une seule option, avec comptage du doublon ;
- même `tournament.id` mais nom, catégorie, tournoi unique ou compteurs contradictoires : catalogue refusé ;
- même nom avec `tournament.id` différents : options distinctes ;
- même `uniqueTournament.id` avec `tournament.id` différents : options distinctes.

## 4. Second endpoint : rencontres d'un tournoi unique

### 4.1 Requête

Le contrat physique exact est :

```text
GET https://www.sofascore.com/api/v1/unique-tournament/{UNIQUE_TOURNAMENT_ID}/scheduled-events/{DATE}
```

Contraintes :

- origine exactement égale à `https://www.sofascore.com` ;
- identifiant numérique strictement positif ;
- date `LocalDate` ISO `YYYY-MM-DD` issue de la sélection serveur ;
- aucun port, chemin, query, fragment, user-info ou hôte libre ;
- clé de requête canonique :

```text
TOURNAMENT_SCHEDULED_EVENTS|date={DATE}|uniqueTournamentId={ID}
```

Le `tournament.id` appartient au contexte de projection, pas à la clé HTTP : deux phases partageant
le même tournoi unique peuvent réutiliser le même snapshot mis en cache.

### 4.2 Enveloppe

La racine minimale est :

```json
{
  "events": []
}
```

`events` est obligatoire et peut être vide. `hasNextPage` peut être absent ou valoir `false`. Une
valeur `true` est incompatible, car aucun mécanisme de pagination n'est défini pour cette route et
le laboratoire ne doit pas ignorer une suite possible.

Le parseur dédié est versionné :

```text
tournament-scheduled-v1
```

### 4.3 Champs structurants d'un événement

Chaque occurrence requiert :

| Chemin | Règle |
|---|---|
| `id` | entier 64 bits strictement positif |
| `startTimestamp` | secondes Unix, entier 64 bits strictement positif représentable par `Instant` |
| `homeTeam.id`, `awayTeam.id` | entiers strictement positifs |
| `homeTeam.name`, `awayTeam.name` | textes non vides, bornés, sans caractère de contrôle |
| `status.type` | texte non vide et borné |
| `status.description` | texte facultatif et borné |
| `tournament.id` | entier strictement positif |
| `tournament.name` | texte non vide et borné |
| `tournament.uniqueTournament.id` | entier strictement positif |
| `tournament.uniqueTournament.name` | texte non vide et borné |

Les chaînes numériques ne sont jamais coercies. Un nombre décimal, un identifiant nul ou négatif,
un timestamp en millisecondes ou hors de la plage `Instant`, une propriété JSON dupliquée ou un
contenu résiduel après la racine rendent le payload incompatible.

Les autres structures observées (`season`, `roundInfo`, `venue`, scores, `time`, `changes`,
traductions, couleurs et filtres) restent exclusivement dans le snapshot brut. Elles ne sont ni
interprétées ni inventées. Les propriétés inconnues produisent des avertissements bornés sans
invalider à elles seules une occurrence.

### 4.4 Acquisition locale alternative du corps JSON

La confirmation exacte ne choisit pas implicitement le réseau. Elle autorise une exécution unique
dont l'opérateur sélectionne explicitement l'une des deux sources suivantes :

| Source | Transport fournisseur | Cache fournisseur | Provenance brute |
|---|---:|---:|---|
| GET direct | au plus 1 | consulté avant le GET | `DIRECT_LOCAL_ENDPOINT` |
| import du corps JSON | 0 | ni lu ni écrit | `MANUAL_LOCAL_JSON_IMPORT` |

Le fichier importé doit contenir exclusivement les octets du document JSON retourné par le second
endpoint. Les règles sont fermées :

- taille strictement bornée à 5 Mio et fichier vide refusé ;
- scanner sensible exécuté avant consommation de la confirmation ;
- tout motif `Authorization`, `Cookie`, `Set-Cookie`, secret, jeton ou clé privée refuse l'import ;
- nom de fichier, type multipart et chemin local non persistés et non utilisés comme vérité ;
- aucune URI, aucun statut ou en-tête fournisseur n'est extrait du fichier ; le statut `200` et la
  latence nulle du snapshot décrivent l'acceptation locale, pas une observation du transport HTTP ;
- la clé date/`uniqueTournament.id` reste reconstruite depuis le claim serveur ;
- le brut est persisté avant parsing avec son mode d'acquisition distinct ;
- la réponse importée ne devient jamais un checkpoint de `provider_response_cache` ;
- parsing, projection, déduplication, contrôle du nombre et transaction canonique sont identiques
  à ceux de la voie directe.

L'import n'est ni un retry, ni un proxy, ni une simulation de navigateur. Après un échec terminal
du GET, l'intention reste verrouillée conformément au contrôle existant ; l'opérateur doit choisir
la voie d'import au moment d'une nouvelle préparation valide, jamais comme reprise automatique.

## 5. Projection sur l'option sélectionnée

Soient :

```text
T = tournament.id sélectionné
U = uniqueTournament.id utilisé dans l'URI
```

Chaque événement de la réponse doit porter `event.tournament.uniqueTournament.id == U`. Une seule
contradiction rend la réponse incompatible et interdit toute écriture canonique partielle.

Un événement est retenu pour l'option uniquement si :

```text
event.tournament.id == T
event.startTimestamp appartient à la date demandée dans la zone explicite
```

Un événement portant le même `U` mais un autre `tournament.id` représente potentiellement une autre
phase : il reste dans le brut et est compté comme exclu, sans être normalisé pour l'option.

## 6. Date, timestamp et fuseau

`startTimestamp` est converti avec `Instant.ofEpochSecond`. Aucun décalage n'est ajouté au
timestamp.

La zone locale de référence de cette évolution est :

```text
Europe/Paris
```

Pour une date `D` et la zone `Z`, la fenêtre est :

```java
Instant fromInclusive = D.atStartOfDay(Z).toInstant();
Instant toExclusive = D.plusDays(1).atStartOfDay(Z).toInstant();
```

Un événement appartient à la date si :

```text
startsAt >= fromInclusive && startsAt < toExclusive
```

Cette fenêtre semi-ouverte traite correctement minuit, les journées de 23 ou 25 heures et les
changements d'heure. Sont interdits : `ZoneId.systemDefault()`, une date UTC implicite, une
troncature de chaîne ou l'ajout manuel d'un offset.

L'exemple `1787079600` correspond à `2026-08-18T19:00:00Z`, soit
`2026-08-18T21:00:00+02:00[Europe/Paris]`.

## 7. Contrôle du nombre attendu

`timezoneEventCount` est une information de cohérence, jamais une identité. Pour le catalogue
courant, la règle 3.4 exclut déjà un objet vide ou l'absence de tout offset applicable : aucune
seconde requête n'est alors autorisée. Dans la projection défensive d'une sélection historique ou
construite hors catalogue, une table vide signifie encore « compte non vérifiable », jamais zéro.

Après contrôle de `U`, filtre de `T`, filtre de date et déduplication des `event.id` :

```text
actualCount = nombre de rencontres uniques retenues
```

Si la journée locale possède un décalage UTC unique et si `timezoneEventCount` contient la clé de
ce décalage en secondes :

```text
expectedCount = timezoneEventCount[offsetSeconds]
```

États :

| Situation | État et effet |
|---|---|
| compte présent et égal | `COUNT_VERIFIED`, normalisation autorisée |
| table vide ou clé absente dans une entrée défensive hors catalogue courant | `COUNT_NOT_VERIFIABLE`, résultat explicite, normalisation autorisée |
| changement d'offset dans la journée | `COUNT_NOT_VERIFIABLE_DST_TRANSITION`, normalisation autorisée |
| compte présent et différent | `COUNT_MISMATCH`, aucune observation canonique écrite |

Lors d'un changement d'heure, aucune clé d'offset n'est choisie arbitrairement. La fenêtre IANA
reste la règle fiable de date.

La correspondance exacte entre le compteur du premier endpoint et la projection du second reste à
qualifier sur le fournisseur :

```text
PROVIDER_COUNT_SEMANTICS_VALIDATED=NO
```

## 8. Doublons d'événements

L'identité de déduplication est `event.id`.

- même `event.id` et mêmes champs normalisés dans la réponse : une seule occurrence et un
  avertissement ;
- même `event.id` avec timestamp, équipes, tournoi, tournoi unique ou statut contradictoires :
  incompatibilité sémantique, aucune écriture partielle ;
- même snapshot rappelé : déduplication brute par endpoint, clé et SHA-256 ;
- même identité observée avec un contenu normalisé différent dans un snapshot ultérieur : nouvelle
  observation append-only sous la même identité canonique.

Le contrôle de nombre est effectué après déduplication par `event.id`.

## 9. Atomicité et provenance

L'ordre obligatoire est :

```text
choix exclusif : cache/transport direct OU import local
→ snapshot brut + taille + SHA-256
→ classification HTTP/contenu
→ parsing versionné
→ validation des identités
→ filtres tournoi/date
→ déduplication
→ contrôle de nombre
→ transaction d'observations canoniques
```

Une incompatibilité structurelle, une contradiction d'identité, un doublon conflictuel ou un
`COUNT_MISMATCH` ne publie jamais une liste canonique partielle.

Chaque observation retenue conserve :

```text
source = SOFASCORE
acquisitionMode = DIRECT_LOCAL_ENDPOINT | MANUAL_LOCAL_JSON_IMPORT
snapshotId
payloadSha256
parserVersion
receivedAt
```

Le brut et les observations normalisées restent séparés. L'observation canonique référence le
`snapshotId`; le mode d'acquisition reste porté par cette ligne brute et demeure donc vérifiable
sans confondre un import manuel avec une réponse reçue directement par le client Java.

## 10. Passage direct vers J5

Chaque `event.id` retenu crée ou retrouve l'identité canonique déterministe existante, puis ajoute
si nécessaire une observation issue du snapshot du second endpoint.

Le résultat affiche un lien direct :

```text
/events/{canonicalEventId}/statistics?zone=Europe%2FParis
```

J5 réutilise l'identifiant fournisseur lié à l'UUID canonique. Aucun `EVENT_DETAILS`, aucune
campagne J4 et aucune saisie manuelle d'identifiant ne sont nécessaires. La campagne J5 conserve
sa préparation, sa confirmation et son ordre actuel :

```text
EVENT_STATISTICS → EVENT_INCIDENTS → EVENT_LINEUPS
```

### 10.1 Choix exclusif d'acquisition pour la campagne J5

Après préparation et avant consommation de la confirmation J5, l'opérateur choisit exactement une
voie pour les trois familles ordonnées :

| Source J5 | Appels fournisseur | Corps requis | Provenance brute |
|---|---:|---:|---|
| campagne directe | de 1 à 3, arrêt au premier incident | réponse de chaque GET | `DIRECT_LOCAL_ENDPOINT` |
| import JSON local | 0 | statistiques + incidents + compositions | `MANUAL_LOCAL_JSON_IMPORT` |

La voie locale demande les seuls corps JSON obtenus manuellement aux chemins exacts :

```text
/api/v1/event/{event.id}/statistics
/api/v1/event/{event.id}/incidents
/api/v1/event/{event.id}/lineups
```

Elle n'accepte ni HAR, ni en-tête, ni cookie, ni jeton, ni donnée de session. Chaque fichier est
non vide et borné à 5 Mio. Les trois fichiers sont scannés et prévalidés avec les parseurs courants
`event-statistics-v2`, `event-incidents-v17` et `event-lineups-v3` **avant** que la confirmation ne
soit réclamée. Un fichier incompatible refuse donc atomiquement le lot, ne crée aucun snapshot et
laisse l'intention en attente pour correction.

Une indisponibilité de famille est importable uniquement sous la forme d'une enveloppe JSON fermée
dont la racine contient le seul objet `error`, avec `error.code` entier égal à `404` et, au plus,
les textes bornés `message` et `reason`. Un `403`, un autre code, une propriété supplémentaire ou
une enveloppe ambiguë reste incompatible ; aucun statut HTTP libre n'est saisi par l'opérateur.
Pour cette enveloppe 404 explicite, le snapshot importé porte la classification
`ENDPOINT_UNAVAILABLE` et l'observation `UNAVAILABLE · N/A`, puis la campagne poursuit la famille
suivante.

Après la validation atomique et la confirmation exacte, les trois corps sont persistés et
normalisés strictement dans l'ordre J5. Pour chaque famille, le snapshot brut est conservé avant
l'observation normalisée ; la clé de requête et l'identifiant fournisseur sont reconstruits depuis
le claim serveur. La campagne réussit seulement après trois snapshots et trois observations, avec
les compteurs minimisés `providerCallAttempts=0` et `localJsonImports=3`. Elle ne consulte aucun
cache fournisseur, n'attend pas trois secondes et n'invoque aucun transport.

Le choix reste terminal. Un échec de la voie directe, notamment `HTTP_403`, ne bascule jamais vers
l'import dans la même campagne. Il faut arrêter puis redémarrer le processus, préparer une nouvelle
campagne, obtenir une nouvelle phrase et choisir explicitement la voie locale. Cette règle évite
qu'un import soit présenté comme un retry ou comme le succès d'un appel fournisseur.

### 10.2 Portée postérieure de l'import J5 multi-match

La section 10.1 décrit exclusivement la campagne J5 unitaire issue d'un lien de rencontre et
contrôlée par `J5RealControlService`. WO-010 n'étend ni son claim, ni son choix exclusif
direct/import, ni ses règles de redémarrage.

WO-010 autorise séparément `/j5-import-batches` pour sélectionner `1..25` UUID canoniques déjà
présents sur une date et une zone, sans saisie libre de `event.id`. Son contrôle vit uniquement en
mémoire, reste indépendant de `J5RealControlService` et accepte `sofascore.enabled=false` comme
`true`. Les opt-ins peuvent rester armés sans donner de voie réseau au lot. Il n'est
jamais un fallback ou un retry de la campagne unitaire ; chacun de ses états terminaux permet une
nouvelle préparation explicite. Son contrat complet est porté par
`docs/requirements/J5-OFFLINE-MULTI-MATCH-IMPORT-RULES.md`.

En conséquence, la règle de la section 11 selon laquelle seul un succès autorise une nouvelle
préparation dans le processus reste limitée aux contrôles historiques décrits par le présent
document et ne doit pas être appliquée au contrôle multi-match de WO-010.

## 11. Garde-fous réseau

- endpoint logique distinct `TOURNAMENT_SCHEDULED_EVENTS` ;
- opt-in distinct, désactivé par défaut, et dépendant de l'opt-in J3 ;
- catalogue général toujours `manualOnly=true`, `callable=false`, sans URI ;
- sélection et préparation sans transport ;
- phrase exacte, acquittement et expiration après cinq minutes ;
- une confirmation autorise une seule action : au plus un GET **ou** un import JSON local ;
- la confirmation J3 autorise soit la pagination directe, soit un lot JSON local complet 1 à N,
  jamais les deux ;
- la confirmation J5 autorise soit la campagne directe ordonnée, soit l'import atomique des trois
  corps JSON, jamais un mélange des deux ;
- cache de dix minutes consulté avant transport ;
- origine exacte, aucune redirection ni proxy ;
- aucun cookie, jeton, compte, session ou simulation de navigateur ;
- l'import local accepte seulement le corps JSON, refuse HAR/en-têtes/cookies, reste borné à
  5 Mio par fichier et consomme zéro appel fournisseur ;
- coordinateur commun J3/J4/J5, concurrence maximale de un et délai minimal de trois secondes ;
- aucun retry, polling, live ou planification ;
- `403`, `429`, `5xx`, timeout, HTML, payload trop grand ou incompatibilité verrouillent le flux ;
- succès, erreur, expiration ou arrêt produisent un état terminal ; seul un succès permet une
  nouvelle préparation explicite dans le même processus.

## 12. Critères de validation synthétiques

- J3 incomplet : aucune option ;
- J3 complet multi-pages : toutes les pages exactes sont agrégées ;
- J3 importé avec pages 1 à N valides : zéro transport, zéro cache, provenance
  `MANUAL_LOCAL_JSON_IMPORT`, preuve v5 et même catalogue ;
- lot J3 troué, mal nommé, non terminal, > 25 pages, > 5 Mio par page, > 25 Mio au total ou
  sensible : refus avant consommation du claim et zéro snapshot ;
- J3 direct terminal sur 403 : aucun basculement implicite ; nouvelle intention requise pour
  choisir l'import ;
- option sans tournoi unique : non actionnable ;
- option sans `tournament.category.name` : non actionnable et identifiant posté refusé ;
- libellé d'option : exactement `tournament.name - tournament.category.name` ;
- contradiction de catégorie pour le même `tournament.id` : catalogue refusé ;
- 2026-08-20 sans clé `7200`, ou table vide : option non actionnable ;
- date d'hiver avec clé `3600` : option actionnable ;
- jour de bascule Paris avec clé `3600` ou `7200` : option actionnable ;
- valeur HTML falsifiée : refus avant transport ;
- nom avec espaces ou accents : jamais présent dans l'URI ;
- ID 7 : URI exacte contenant `/unique-tournament/7/` ;
- préparation ou mauvais texte : zéro transport ;
- confirmation valide et voie directe : au plus un transport ;
- confirmation valide et import local : zéro transport, zéro lecture/écriture du cache fournisseur,
  provenance `MANUAL_LOCAL_JSON_IMPORT` et même normalisation ;
- fichier vide, trop grand ou contenant un en-tête de cookie : refus avant consommation de la
  confirmation ;
- cache hit : zéro transport ;
- autre phase du même tournoi unique : exclue, pas incompatible ;
- autre tournoi unique : incompatible ;
- événement hors date : exclu ;
- compte différent : aucune observation ;
- clé locale présente avec compte zéro et réponse vide : succès vide vérifié explicite ;
- événements valides : observations canoniques et liens J5 ;
- aucune dépendance à `EVENT_DETAILS` ;
- J5 local avec trois corps valides : trois snapshots `MANUAL_LOCAL_JSON_IMPORT`, trois
  observations ordonnées, `providerCallAttempts=0`, `localJsonImports=3` et aucun transport ;
- J5 local avec un seul corps incompatible ou sensible : lot refusé avant consommation de la
  confirmation, zéro snapshot et zéro observation ;
- J5 local avec enveloppe fermée `error.code=404` : famille `UNAVAILABLE · N/A`, puis poursuite ;
- J5 local avec `error.code=403` : refus atomique, jamais une indisponibilité compatible ;
- J5 direct terminal sur 403 : aucun import dans la même campagne ; redémarrage et nouvelle
  préparation obligatoires ;
- aucun test Maven ne contacte Internet.

## 13. Références locales

- `ADR-SS-001-experimentation-endpoints-sofascore-depuis-windows.md` ;
- `docs/requirements/J5-FOOTBALL-INCIDENT-RULES.md` ;
- `docs/architecture/SCHEDULED-EVENTS-V1.md` ;
- `docs/architecture/J3-DYNAMIC-MANUAL-PAGINATION.md` ;
- `docs/architecture/J3-RAW-SNAPSHOT-PERSISTENCE.md` ;
- document opérateur `Evolution J3 J5.md`, reçu le 2026-08-20.

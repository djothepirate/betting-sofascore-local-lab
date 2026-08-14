# Contrat hors ligne `scheduled-events-v1`

## Statut et portée

`scheduled-events-v1` est le contrat de parsing de la famille logique `SCHEDULED_EVENTS`. Il
s’applique aux fixtures chargées par `ClasspathFixtureLoader` et aux octets d’une réponse de
transport déjà conservés avant parsing.

Le corpus Git reste intégralement synthétique. Les séquences J3 du 2026-08-13 ont toutefois
qualifié, puis conservé localement, les réponses réelles des pages 1 et 2 dont la structure utile a
été examinée hors ligne :

```text
FIXTURE_ORIGIN=SYNTHETIC
PROVIDER_RAW_PAYLOAD_IN_GIT=NO
QUALIFIED_PROVIDER_ROOT_FIELDS=scheduled,hasNextPage
QUALIFIED_PROVIDER_ENTRY_FIELDS=tournament,timezoneEventCount
QUALIFIED_PROVIDER_SNAPSHOTS=1,2
QUALIFIED_PROVIDER_SNAPSHOT_1_OFFLINE_PARSE=PARSED
QUALIFIED_PROVIDER_SNAPSHOT_2_OFFLINE_PARSE=PARSED
QUALIFIED_PROVIDER_SNAPSHOT_2_HAS_NEXT_PAGE=true
HISTORICAL_SCHEMA_STATUS_MUTATED=NO
NETWORK_AUTHORIZED=NO
NEW_REAL_SOFASCORE_CALL_EXECUTED=NO
```

Les fixtures `qualified-provider-shape` et `qualified-page-two-shape` contiennent uniquement des
identités et nombres synthétiques. Elles ne se substituent pas aux snapshots locaux comme preuve
d’observation, mais permettent de rejouer le contrat utile sans PostgreSQL, Internet, URI ou donnée
de session.

Les champs structuraux observés mais non retenus dans le modèle minimal sont connus et ignorés sans
avertissement : `category`, `fieldTranslations`, `priority`, `qualificationOrPreliminary`, `slug`,
`displayInverseHomeAwayTeams`, `hasEventPlayerStatistics`, `hasPerformanceGraphFeature` et
`userCount`. Toute nouvelle propriété extérieure à cette liste reste signalée par `UNKNOWN_FIELD`.

## Chaîne de traitement

```text
LoadedFixture vérifiée
    -> classification du contenu
    -> validation structurale stricte
    -> sélection non ambiguë de la forme events ou scheduled
    -> DTO externe ScheduledEventsEnvelopeDto
    -> ScheduledEventsMapper
    -> modèle local ScheduledEventsPage
```

La forme J2 `events` reste prise en charge pour préserver le corpus validé. La forme qualifiée J3
`scheduled` est représentée séparément par `SCHEDULED_TOURNAMENT_LIST` ; elle ne fabrique jamais de
`ScheduledEvent`. Un payload contenant simultanément `events` et `scheduled` est rejeté comme
ambigu. Aucun objet local n’est exposé lorsque la validation rencontre au moins un problème.

## Champs retenus

| Chemin | Type v1 | Règle | Modèle local |
|---|---|---|---|
| `$.events` | tableau | obligatoire, peut être vide | `ScheduledEventsPage.events` |
| `$.hasNextPage` | booléen | obligatoire | `ScheduledEventsPage.hasNextPage` |
| `$.events[*].id` | entier 64 bits positif | obligatoire | `ScheduledEvent.providerEventId` |
| `$.events[*].startTimestamp` | entier 64 bits | obligatoire, doit être représentable par `Instant` | `ScheduledEvent.startsAt` |
| `$.events[*].homeTeam` | objet | obligatoire | `ScheduledEvent.homeTeam` |
| `$.events[*].awayTeam` | objet | obligatoire | `ScheduledEvent.awayTeam` |
| `team.id` | entier 64 bits positif | obligatoire | `ScheduledTeam.providerTeamId` |
| `team.name` | texte non vide | obligatoire | `ScheduledTeam.name` |
| `$.events[*].status` | objet | obligatoire | `ScheduledEvent.status` |
| `status.type` | texte non vide | obligatoire | `ScheduledEventStatus.type` |
| `status.description` | texte non vide | facultatif | `ScheduledEventStatus.description` |
| `$.events[*].tournament` | objet | facultatif | `ScheduledEvent.tournament` |
| `tournament.id` | entier 64 bits positif | obligatoire si l'objet existe | `ScheduledTournament.providerTournamentId` |
| `tournament.name` | texte non vide | obligatoire si l'objet existe | `ScheduledTournament.name` |

La forme fournisseur qualifiée ajoute le contrat suivant :

| Chemin | Type v1 qualifié | Règle | Modèle local |
|---|---|---|---|
| `$.scheduled` | tableau | obligatoire pour cette forme, peut être vide | `ScheduledEventsPage.scheduledTournaments` |
| `$.hasNextPage` | booléen | obligatoire | `ScheduledEventsPage.hasNextPage` |
| `$.scheduled[*].tournament` | objet | obligatoire | `ScheduledTournamentAvailability.tournament` |
| `tournament.id` | entier 64 bits positif | obligatoire | `ScheduledTournament.providerTournamentId` |
| `tournament.name` | texte non vide | obligatoire | `ScheduledTournament.name` |
| `tournament.uniqueTournament` | objet | facultatif | `ScheduledTournamentAvailability.uniqueTournament` |
| `uniqueTournament.id` | entier 64 bits positif | obligatoire si l’objet existe | `ScheduledTournament.providerTournamentId` |
| `uniqueTournament.name` | texte non vide | obligatoire si l’objet existe | `ScheduledTournament.name` |
| `$.scheduled[*].timezoneEventCount` | objet ou tableau vide | obligatoire ; seul `[]` est accepté comme seconde forme | `ScheduledTournamentAvailability.timezoneEventCount` |
| clé de `timezoneEventCount` | texte représentant un entier 32 bits | obligatoire | décalage horaire en secondes |
| valeur de `timezoneEventCount` | entier 32 bits positif ou nul | obligatoire | nombre d’événements |

Le tableau vide qualifié sur la page 2 devient une table locale vide et produit
`EMPTY_TIMEZONE_EVENT_COUNT`. Un tableau non vide reste une incompatibilité de type : son contenu
n’est jamais converti ou ignoré. Un champ numérique sous forme de chaîne n'est jamais converti
implicitement. Une valeur facultative explicitement présente avec un type incorrect reste une
incompatibilité : elle n'est pas assimilée à un champ absent.

## Champs inconnus et avertissements

Les champs inconnus sont ignorés pour le mapping, mais chaque chemin exact produit un
avertissement `UNKNOWN_FIELD`. Le parseur ne conserve pas leur valeur.

Les autres avertissements v1 sont :

| Code | Signification |
|---|---|
| `OPTIONAL_FIELD_MISSING` | un champ facultatif documenté est absent |
| `EMPTY_EVENTS` | le tableau `events` est valide mais vide |
| `EMPTY_SCHEDULED_TOURNAMENTS` | le tableau `scheduled` est valide mais vide |
| `EMPTY_TIMEZONE_EVENT_COUNT` | la table de compteurs est valide mais vide |

Un résultat `PARSED` peut donc contenir des avertissements. Ceux-ci ne modifient pas les valeurs
et ne sont pas des erreurs silencieusement corrigées.

## Résultats et problèmes

Le parseur retourne toujours un `ScheduledEventsParseResult` structuré :

| Statut | Page locale | Problèmes |
|---|---|---|
| `PARSED` | présente | aucun |
| `SCHEMA_INCOMPATIBLE` | absente | au moins un |
| `UNEXPECTED_CONTENT` | absente | au moins un |

Les codes de problème v1 sont : `INVALID_JSON`, `REQUIRED_FIELD_MISSING`, `TYPE_MISMATCH`,
`UNEXPECTED_CONTENT_KIND`, `UNSUPPORTED_ENDPOINT` et `VALUE_OUT_OF_RANGE`.

Une incompatibilité peut agréger plusieurs problèmes, avec leur chemin JSON, mais elle ne publie
jamais une liste partielle d'événements. Un contenu non JSON est classé avant toute tentative de
parsing.

## Traçabilité

Chaque résultat, y compris un échec, transporte une preuve immuable contenant :

- l'identifiant de fixture ;
- le SHA-256 brut ;
- le SHA-256 JSON canonique lorsqu'il existe ;
- la date `recordedAt` du manifeste ;
- la version `scheduled-events-v1` du parseur.

La preuve brute reste portée par `LoadedFixture`. Le parseur ne modifie ni les octets ni les hashes
et ne journalise aucune valeur de payload.

## Validation hors ligne

La couverture du contrat est répartie entre deux classes de test :

- `ScheduledEventsV1ParserTest` couvre le nominal J2, la stabilité face à l'ordre des propriétés,
  les champs facultatifs absents, le tableau vide, les champs inconnus et la forme fournisseur
  qualifiée synthétique des pages 1 et 2 ;
- `ScheduledEventsV1SchemaIncompatibilityTest` couvre les cinq fixtures de rupture, les codes et
  chemins de chaque problème, l’absence d’identité de tournoi, les compteurs devenus texte,
  l’ambiguïté des deux racines, l'absence de page partielle et la conservation de la traçabilité.

| Fixture de rupture | Statut vérifié | Problèmes vérifiés |
|---|---|---|
| champ obligatoire absent | `SCHEMA_INCOMPATIBLE` | `REQUIRED_FIELD_MISSING` pour `event.id` et `event.status` |
| identifiant numérique devenu texte | `SCHEMA_INCOMPATIBLE` | `TYPE_MISMATCH` pour `event.id`, sans coercition, et `REQUIRED_FIELD_MISSING` pour `event.status` |
| `events` devenu objet | `SCHEMA_INCOMPATIBLE` | `TYPE_MISMATCH` sur `$.events` |
| `timezoneEventCount` devenu tableau non vide | `SCHEMA_INCOMPATIBLE` | `TYPE_MISMATCH` sur le champ complet, sans coercition |
| HTML inattendu | `UNEXPECTED_CONTENT` | `UNEXPECTED_CONTENT_KIND` sur `$` |

Les cinq résultats en échec conservent l'identifiant de fixture, les hashes disponibles, la date
du manifeste et la version du parseur. Les tests standards restent sans Internet et sans
PostgreSQL.

## Projection dans le tableau de bord

`OfflineFixtureCorpusService` constitue l’inventaire du corpus depuis une liste fermée de douze
manifestes classpath. Pour chaque entrée, il réutilise `ClasspathFixtureLoader`, puis
`ScheduledEventsV1Parser`. Le tableau de bord expose uniquement des compteurs et métadonnées :

```text
availability=AVAILABLE_OFFLINE
declared=12
available=12
parsed=7
schemaIncompatible=4
unexpectedContent=1
loadingFailures=0
origin=SYNTHETIC
providerSchemaValidated=true
```

`origin=SYNTHETIC` décrit les ressources versionnées. `providerSchemaValidated=true` décrit la
validation distincte du contrat structurel : les snapshots locaux `1` et `2`, acquis par la
qualification J3, sont relus par le parseur adapté avec `PARSED`, `100` entrées `scheduled` chacun
et `hasNextPage=true`, sans nouvel accès réseau. Leur statut historique
`SCHEMA_INCOMPATIBLE` reste inchangé. Le payload brut, ses valeurs et ses hashes ne sont jamais
rendus par le tableau de bord.

Les payloads, leurs champs et leurs hashes ne sont pas affichés. Une erreur de chargement ne rend
pas le tableau de bord indisponible : l’état devient `INCOMPLETE` et le compteur d’échecs augmente.
Le calcul est indépendant de PostgreSQL et ne contient aucun chemin réseau.

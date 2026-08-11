# Contrat hors ligne `scheduled-events-v1`

## Statut et portée

`scheduled-events-v1` est le premier contrat de parsing J2 pour la famille logique
`SCHEDULED_EVENTS`. Il s'applique exclusivement aux fixtures chargées et vérifiées par
`ClasspathFixtureLoader`.

Le corpus actuel est intégralement synthétique :

```text
FIXTURE_ORIGIN=SYNTHETIC
PROVIDER_SCHEMA_VALIDATED=NO
NETWORK_AUTHORIZED=NO
REAL_ENDPOINT_URI_AUTHORIZED=NO
REAL_SOFASCORE_CALL_EXECUTED=NO
```

Ce contrat démontre un comportement logiciel reproductible. Il ne constitue pas une preuve du
schéma réellement exposé par le fournisseur.

## Chaîne de traitement

```text
LoadedFixture vérifiée
    -> classification du contenu
    -> validation structurale stricte
    -> DTO externe ScheduledEventsEnvelopeDto
    -> ScheduledEventsMapper
    -> modèle local ScheduledEventsPage
```

Les DTO externes conservent les noms et types du contrat JSON v1. Le modèle local utilise des noms
métier et convertit uniquement `startTimestamp`, exprimé en secondes Unix, vers `Instant`. Aucun
objet local n'est exposé lorsque la validation rencontre au moins un problème.

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

Un champ numérique sous forme de chaîne n'est jamais converti implicitement. Une valeur
facultative explicitement présente avec un type incorrect reste une incompatibilité : elle n'est
pas assimilée à un champ absent.

## Champs inconnus et avertissements

Les champs inconnus sont ignorés pour le mapping, mais chaque chemin exact produit un
avertissement `UNKNOWN_FIELD`. Le parseur ne conserve pas leur valeur.

Les autres avertissements v1 sont :

| Code | Signification |
|---|---|
| `OPTIONAL_FIELD_MISSING` | un champ facultatif documenté est absent |
| `EMPTY_EVENTS` | le tableau `events` est valide mais vide |

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

- `ScheduledEventsV1ParserTest` couvre le nominal, la stabilité face à l'ordre des propriétés,
  les champs facultatifs absents, le tableau vide et les champs inconnus ;
- `ScheduledEventsV1SchemaIncompatibilityTest` couvre les quatre fixtures de rupture, les codes et
  chemins de chaque problème, l'absence de page partielle et la conservation de la traçabilité.

| Fixture de rupture | Statut vérifié | Problèmes vérifiés |
|---|---|---|
| champ obligatoire absent | `SCHEMA_INCOMPATIBLE` | `REQUIRED_FIELD_MISSING` pour `event.id` et `event.status` |
| identifiant numérique devenu texte | `SCHEMA_INCOMPATIBLE` | `TYPE_MISMATCH` pour `event.id`, sans coercition, et `REQUIRED_FIELD_MISSING` pour `event.status` |
| `events` devenu objet | `SCHEMA_INCOMPATIBLE` | `TYPE_MISMATCH` sur `$.events` |
| HTML inattendu | `UNEXPECTED_CONTENT` | `UNEXPECTED_CONTENT_KIND` sur `$` |

Les quatre résultats en échec conservent l'identifiant de fixture, les hashes disponibles, la date
du manifeste et la version du parseur. Les tests standards restent sans Internet et sans
PostgreSQL.

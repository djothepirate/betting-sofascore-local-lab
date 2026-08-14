# Contrat hors ligne `event-details-v1`

## Statut et frontière

`event-details-v1` est le premier contrat J4 de détail d'une rencontre. Il accepte uniquement des
fixtures classpath dont le type logique est `EVENT_DETAILS` et la version de parseur exacte
`event-details-v1`.

```text
FIXTURE_ORIGIN=SYNTHETIC
PROVIDER_SCHEMA_VALIDATED=NO
REAL_ENDPOINT_URI_CONFIGURED=NO
PROVIDER_TRANSPORT_AVAILABLE=NO
NETWORK_CALLS_DURING_PARSING=0
```

Le parseur ne possède aucun client HTTP, aucune résolution d'URI et aucun accès à PostgreSQL.

## Champs retenus

La racine JSON représente un événement unique.

| Chemin | Type | Règle locale |
|---|---|---|
| `id` | entier 64 bits positif | obligatoire, identité fournisseur |
| `startTimestamp` | entier 64 bits positif | obligatoire, secondes Unix |
| `homeTeam.id`, `awayTeam.id` | entier 64 bits positif | obligatoires |
| `homeTeam.name`, `awayTeam.name` | texte non vide | obligatoires, 200 caractères maximum |
| `status.type` | texte non vide | obligatoire, 64 caractères maximum |
| `status.description` | texte non vide | facultatif |
| `tournament.id`, `tournament.name` | entier positif et texte | objet facultatif, atomique |
| `venue.id`, `venue.name` | entier positif et texte | objet facultatif, atomique |
| `venue.city` | texte | facultatif |
| `season.id`, `season.name` | entier positif et texte | objet facultatif, atomique |
| `round` | texte | facultatif, 64 caractères maximum |

Une absence facultative produit `OPTIONAL_FIELD_MISSING`. Un champ inconnu est ignoré pour le
mapping et signalé par son chemin exact avec `UNKNOWN_FIELD`.

## Incompatibilités

Le parseur renvoie `SCHEMA_INCOMPATIBLE` et n'expose aucun `EventDetails` partiel lorsqu'un champ
obligatoire manque, lorsqu'un type change, lorsqu'un identifiant n'est pas strictement positif ou
lorsqu'un objet facultatif présent est mal formé. Les chaînes numériques ne sont jamais converties
en nombres.

Un contenu non JSON ou ambigu est `UNEXPECTED_CONTENT`. Les propriétés JSON dupliquées et le
contenu résiduel sont refusés par le lecteur strict partagé avec le corpus J2.

## Identité et rattachement

Le parseur conserve l'identifiant du payload sans le remplacer par l'identifiant demandé. Le
rattachement à une identité canonique relève du service J4 et exige :

```text
DETAIL_PROVIDER_EVENT_ID=CANONICAL_PROVIDER_EVENT_ID
```

Une fixture valide décrivant un autre événement reste donc parsable, mais doit être refusée lors
d'une tentative de rattachement incohérente. Cette séparation empêche une normalisation silencieuse
sur la mauvaise rencontre.

## Preuve

Chaque résultat conserve :

- l'identifiant de fixture ;
- le SHA-256 des octets exacts ;
- le SHA-256 JSON canonique lorsqu'il existe ;
- l'heure `recordedAt` du manifeste ;
- la version `event-details-v1` ;
- les avertissements et problèmes structurés.

Le corpus et ses hashes sont vérifiés par `EventDetailsFixtureCorpusTest`. Les tests de parseur
couvrent le nominal, les champs inconnus, les absences facultatives, les deux ruptures de schéma et
le maintien explicite d'une autre identité valide.

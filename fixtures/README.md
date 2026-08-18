# Fixtures — corpus hors ligne J2 à J5

Les répertoires ont été créés au J1. Ils ne contiennent encore aucun payload SofaScore observé. Le jalon J2 charge les fixtures versionnées depuis le classpath, sans connexion réseau et sans dépendance à PostgreSQL.

```text
fixtures/
├── scheduled-events/
├── event-details/
├── event-statistics/
├── event-incidents/
├── event-lineups/
└── schema-breaks/
```

Les ressources de ce dossier sont ajoutées sous `fixtures/` dans le classpath Maven et dans le JAR. Chaque payload doit être accompagné d’un manifeste nommé `*.manifest.json`.

## Manifeste v1

Exemple structurel pour une fixture synthétique — les deux hashes d’exemple doivent être remplacés par des valeurs SHA-256 réelles avant utilisation :

```json
{
  "manifestVersion": 1,
  "fixtureId": "scheduled-events-nominal",
  "endpointType": "SCHEDULED_EVENTS",
  "fixtureOrigin": "SYNTHETIC",
  "providerSchemaValidated": false,
  "recordedAt": "2026-08-12T00:00:00Z",
  "httpStatus": null,
  "contentType": "application/json",
  "parserVersion": "scheduled-events-v1",
  "payloadResource": "fixtures/scheduled-events/nominal.json",
  "maximumBytes": 262144,
  "expectedRawSha256": "<64 caractères hexadécimaux>",
  "expectedCanonicalJsonSha256": "<64 caractères hexadécimaux>",
  "minimized": true,
  "removedFields": [
    "debug.internal"
  ]
}
```

Champs obligatoires :

- version de manifeste `1` ;
- identifiant en minuscules et kebab-case ;
- endpoint logique ;
- origine `SYNTHETIC` ou `PROVIDER_OBSERVED` ;
- indicateur `providerSchemaValidated` ;
- date d’enregistrement UTC ;
- statut HTTP pour une fixture `PROVIDER_OBSERVED` ;
- type de contenu déclaré ;
- version du parseur, ou `UNASSIGNED` avant son introduction ;
- chemin normalisé sous `fixtures/` ;
- taille maximale propre à la fixture ;
- SHA-256 brut attendu ;
- SHA-256 JSON canonique attendu pour un contenu JSON ;
- état de minimisation et liste des champs supprimés.

Une fixture `SYNTHETIC` doit conserver :

```text
fixtureOrigin=SYNTHETIC
providerSchemaValidated=false
```

Elle ne peut pas servir de preuve qu’un schéma fournisseur a été observé.

## Hash brut et hash JSON canonique

Le hash brut est calculé sur les octets exacts du payload. Il change donc si l’encodage, les espaces ou l’ordre des propriétés changent.

Pour le hash JSON canonique :

1. le JSON est parsé avec détection stricte des propriétés dupliquées et du contenu résiduel ;
2. les propriétés des objets sont triées récursivement par nom ;
3. les espaces non significatifs sont supprimés par sérialisation compacte ;
4. l’ordre des tableaux est conservé ;
5. le SHA-256 est calculé sur les octets UTF-8 résultants.

Deux objets JSON qui diffèrent uniquement par l’ordre de leurs propriétés ont ainsi le même hash canonique. Une modification de valeur ou de l’ordre d’un tableau produit un hash différent.

## Chargement et contrôles

Le chargeur `ClasspathFixtureLoader` applique dans l’ordre :

1. validation du chemin et lecture du manifeste, limité à 64 Kio ;
2. validation forte des métadonnées ;
3. lecture bornée du payload ;
4. limite absolue de 5 Mio, en plus de la limite plus basse du manifeste ;
5. détection de motifs sensibles sans restitution de leur valeur ;
6. vérification du hash brut ;
7. classification `JSON`, `HTML` ou `OTHER` ;
8. canonicalisation et vérification du hash JSON lorsque le contenu est JSON.

Les catégories sensibles bloquantes couvrent les en-têtes d’autorisation, les cookies, les champs usuels de jeton ou d’identifiant de session, les JWT plausibles et les blocs de clé privée.

## Corpus synthétique `SCHEDULED_EVENTS`

Le corpus contient désormais douze scénarios créés de zéro. Aucun payload fournisseur n’est
versionné. Le dixième scénario reproduit uniquement la structure utile qualifiée hors ligne après
la page 1 J3. Le onzième représente la divergence bornée de la page 2 : un objet de compteurs suivi
d’un tableau strictement vide. Le douzième est le contre-exemple avec tableau non vide. Toutes les
identités et valeurs sont synthétiques.

Tous les manifestes conservent :

```text
endpointType=SCHEDULED_EVENTS
fixtureOrigin=SYNTHETIC
providerSchemaValidated=false
httpStatus=null
parserVersion=scheduled-events-v1
maximumBytes=4096
minimized=false
```

`minimized=false` signifie que ces payloads ont été conçus directement sous une forme minimale : aucun champ n’a été supprimé d’une réponse fournisseur.

| Scénario | Ressource | Comportement `scheduled-events-v1` |
|---|---|---|
| nominal | `scheduled-events/nominal.json` | `PARSED`, sans avertissement |
| ordre différent | `scheduled-events/nominal-property-order-variant.json` | même modèle local et même contenu canonique, octets différents |
| champ facultatif absent | `scheduled-events/optional-field-missing.json` | `PARSED` avec avertissements pour `tournament` et `status.description` |
| tableau vide | `scheduled-events/empty-events-array.json` | `PARSED`, liste vide et avertissement `EMPTY_EVENTS` |
| champ inconnu | `scheduled-events/unknown-extra-field.json` | `PARSED`, propriétés ignorées et chemins signalés par `UNKNOWN_FIELD` |
| forme fournisseur qualifiée | `scheduled-events/qualified-provider-shape.json` | `PARSED`, disponibilités de tournois sans événement inventé |
| forme qualifiée page 2 | `scheduled-events/qualified-page-two-shape.json` | `PARSED`, objet de compteurs conservé, `[]` projeté en table vide avec avertissement et `hasNextPage=true` |
| champ obligatoire absent | `schema-breaks/scheduled-events-required-field-missing.json` | `SCHEMA_INCOMPATIBLE` pour `event.id` et `event.status` absents |
| nombre devenu texte | `schema-breaks/scheduled-events-numeric-field-as-string.json` | `SCHEMA_INCOMPATIBLE` pour `event.id`, sans conversion implicite, et pour `event.status` absent |
| objet inattendu | `schema-breaks/scheduled-events-unexpected-object.json` | `SCHEMA_INCOMPATIBLE` car `events` n’est pas un tableau |
| compteurs devenus tableau non vide | `schema-breaks/scheduled-events-timezone-count-non-empty-array.json` | `SCHEMA_INCOMPATIBLE`, sans coercition ni page partielle |
| HTML inattendu | `schema-breaks/scheduled-events-unexpected-html.html` | `UNEXPECTED_CONTENT`, classé `HTML` avant parsing |

Les manifestes portent les hashes réels calculés par `FixturePayloadHasher`. Le nominal et sa variante d’ordre ont des hashes bruts différents et le même hash JSON canonique.

Le contrat détaillé des champs obligatoires, facultatifs et ignorés, ainsi que les résultats
structurés, est décrit dans `docs/architecture/SCHEDULED-EVENTS-V1.md`.

Les cinq scénarios de rupture sont exécutés par
`ScheduledEventsV1SchemaIncompatibilityTest`. Chaque test vérifie le statut, les codes et chemins
de problèmes, l'absence de page locale partielle et la preuve de traçabilité.

Le tableau de bord inventorie les douze manifestes classpath avec les mêmes contrôles. Il affiche
uniquement leur disponibilité et la répartition des résultats du parseur ; aucun payload ni hash
n’est rendu dans l’interface.

Les tests doivent rester reproductibles sans connexion à la source externe.

## Corpus synthétique `EVENT_DETAILS`

J4 ajoute cinq scénarios créés de zéro. Ils ne contiennent aucune URI, aucun en-tête, aucune
donnée de session et aucune valeur issue d'un payload fournisseur. Tous les manifestes conservent :

```text
endpointType=EVENT_DETAILS
fixtureOrigin=SYNTHETIC
providerSchemaValidated=false
httpStatus=null
parserVersion=event-details-v1
maximumBytes=4096
minimized=false
```

| Scénario | Ressource | Comportement `event-details-v1` |
|---|---|---|
| nominal | `event-details/nominal.json` | `PARSED`, détail complet sans avertissement |
| champs inconnus | `event-details/unknown-extra-field.json` | `PARSED`, chemins `$.coverage` et `$.venue.capacity` signalés |
| autre identité valide | `event-details/other-event.json` | `PARSED`, identifiant conservé pour tester le refus d'un mauvais rattachement |
| champ obligatoire absent | `schema-breaks/event-details-required-field-missing.json` | `SCHEMA_INCOMPATIBLE`, aucun détail partiel |
| identifiant devenu texte | `schema-breaks/event-details-id-as-string.json` | `SCHEMA_INCOMPATIBLE`, aucune coercition numérique |

Le contrat détaillé est décrit dans `docs/architecture/EVENT-DETAILS-V1.md`. Une fixture synthétique
qualifie le comportement hors ligne du parseur ; elle ne valide pas le schéma réel du fournisseur
et n'autorise aucun transport `EVENT_DETAILS`.

## Corpus synthétique J5 des données de rencontre

J5 ajoute neuf scénarios créés de zéro pour les trois familles liées à l'événement synthétique
`900001`. Ils qualifient le modèle local et ses contrôles de complétude sans prétendre reproduire
les réponses SofaScore actuelles. La première et unique tentative de découverte réelle autorisée a
reçu `HTTP 403` ; la campagne a été arrêtée sans retry et les cinq autres exemples n'ont pas été
appelés.

Tous les manifestes J5 conservent :

```text
fixtureOrigin=SYNTHETIC
providerSchemaValidated=false
httpStatus=null
minimized=false
```

| Famille | Scénario | Ressource | Résultat attendu |
|---|---|---|---|
| statistiques | nominal | `event-statistics/nominal.json` | `PARSED`, `COMPLETE`, trois métriques |
| statistiques | valeur extérieure absente | `event-statistics/partial-missing-away.json` | `PARSED`, `PARTIAL`, chemin manquant |
| statistiques | identifiant devenu texte | `schema-breaks/event-statistics-id-as-string.json` | `SCHEMA_INCOMPATIBLE`, aucune donnée |
| incidents | nominal | `event-incidents/nominal.json` | `PARSED`, `COMPLETE`, trois incidents ordonnés |
| incidents | tableau vide | `event-incidents/empty.json` | `PARSED`, `EMPTY_VALID` |
| incidents | minute devenue texte | `schema-breaks/event-incidents-time-as-string.json` | `SCHEMA_INCOMPATIBLE`, aucune donnée |
| compositions | nominal | `event-lineups/nominal.json` | `PARSED`, `COMPLETE`, deux côtés et quatre joueurs |
| compositions | non confirmée et incomplète | `event-lineups/partial-unconfirmed.json` | `PARSED`, `PARTIAL`, absences visibles |
| compositions | identifiant joueur devenu texte | `schema-breaks/event-lineups-player-id-as-string.json` | `SCHEMA_INCOMPATIBLE`, aucune donnée |

Les versions de parseur sont respectivement `event-statistics-v1`, `event-incidents-v1` et
`event-lineups-v1`. Les hashes bruts et JSON canoniques sont vérifiés avant parsing. Le détail des
signaux de complétude, de la provenance, de la persistance V7 et de l'affichage local est décrit
dans `docs/architecture/J5-OFFLINE-EVENT-DATA-AND-COMPLETENESS.md`.

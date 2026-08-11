# Fixtures — infrastructure hors ligne J2

Les répertoires ont été créés au J1. Ils ne contiennent encore aucun payload SofaScore observé. Le jalon J2 charge les fixtures versionnées depuis le classpath, sans connexion réseau et sans dépendance à PostgreSQL.

```text
fixtures/
├── scheduled-events/
├── event-details/
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
  "parserVersion": "UNASSIGNED",
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

Le corpus initial contient neuf scénarios créés de zéro. Il ne reproduit pas et ne prétend pas valider un schéma SofaScore observé.

Tous les manifestes conservent :

```text
endpointType=SCHEDULED_EVENTS
fixtureOrigin=SYNTHETIC
providerSchemaValidated=false
httpStatus=null
parserVersion=UNASSIGNED
maximumBytes=4096
minimized=false
```

`minimized=false` signifie que ces payloads ont été conçus directement sous une forme minimale : aucun champ n’a été supprimé d’une réponse fournisseur.

| Scénario | Ressource | Intention pour le futur parseur |
|---|---|---|
| nominal | `scheduled-events/nominal.json` | structure synthétique de référence |
| ordre différent | `scheduled-events/nominal-property-order-variant.json` | même contenu canonique, octets différents |
| champ facultatif absent | `scheduled-events/optional-field-missing.json` | `tournament` et `status.description` absents |
| tableau vide | `scheduled-events/empty-events-array.json` | liste d’événements vide et valide à qualifier |
| champ inconnu | `scheduled-events/unknown-extra-field.json` | propriétés synthétiques supplémentaires à tolérer et tracer |
| champ obligatoire absent | `schema-breaks/scheduled-events-required-field-missing.json` | `event.id` absent |
| nombre devenu texte | `schema-breaks/scheduled-events-numeric-field-as-string.json` | `event.id` est une chaîne |
| objet inattendu | `schema-breaks/scheduled-events-unexpected-object.json` | `events` est un objet au lieu d’un tableau |
| HTML inattendu | `schema-breaks/scheduled-events-unexpected-html.html` | contenu classé `HTML` avant parsing |

Les manifestes portent les hashes réels calculés par `FixturePayloadHasher`. Le nominal et sa variante d’ordre ont des hashes bruts différents et le même hash JSON canonique.

Les tests doivent rester reproductibles sans connexion à la source externe.

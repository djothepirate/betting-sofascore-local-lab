# Politiques J3 d’arrêt et d’incident du transport

## 1. Portée

Cette unité part du commit `e555a13bc8023ebb3dd7a9454719dd16144210e2` et couvre la réaction
du laboratoire aux issues d’un transport `SCHEDULED_EVENTS` simulé. Elle n’active aucun transport
fournisseur et n’ajoute aucune URI réelle.

```text
TEST_SCOPE=SIMULATED_LOOPBACK_AND_PURE_COMPONENTS
PROVIDER_TRANSPORT_ACTIVE=NO
REAL_ENDPOINT_URI=ABSENT
AUTOMATIC_RETRY=NO
SOFASCORE_NETWORK_CALLS=NO
```

Le processeur d’issue reçoit soit une réponse brute déjà bornée, soit un échec typé. Il ne réalise
lui-même aucune entrée/sortie réseau et ne planifie jamais de retry.

## 2. Ordre de conservation et de parsing

Pour une réponse HTTP `2xx`, l’ordre est obligatoire :

```text
ScheduledEventsTransportResponse
              │
              ▼
persistance exacte RAW_ONLY
              │ identifiant du snapshot
              ▼
scheduled-events-v1
              │
              ▼
classification idempotente du même snapshot
    ├── PARSED
    ├── SCHEMA_INCOMPATIBLE ──► circuit OPEN
    └── UNEXPECTED_CONTENT ───► circuit OPEN
```

Le brut, sa taille et son SHA-256 sont donc conservés avant toute tentative de parsing. La mise à
jour de classification n’est autorisée que depuis `RAW_ONLY` ou vers une valeur déjà identique. Une
tentative de reclassification contradictoire échoue sans modifier le snapshot.

Une réponse identique pour la même famille et la même clé est dédupliquée par le mécanisme existant.
Le parseur reste déterministe et la classification idempotente est réappliquée au même identifiant.
`payload_jsonb` reste `NULL` : cette unité n’introduit aucune normalisation persistée.

## 3. Matrice HTTP

Les réponses hors `2xx` conservent leurs octets bruts sous `TRANSPORT_ERROR`, puis ouvrent le
circuit sans retry :

| Réponse simulée | Motif du circuit | `retryNotBefore` | Retry |
|---|---|---|---|
| `400` | `HTTP_BAD_REQUEST` | aucun | jamais |
| `401` | `HTTP_UNAUTHORIZED` | aucun | jamais |
| `403` | `HTTP_FORBIDDEN` | aucun | jamais |
| `429` | `HTTP_TOO_MANY_REQUESTS` | en-tête valide ou garde locale de 5 min | jamais |
| `5xx` | `SERVER_ERROR` | aucun | jamais |

Un `Retry-After` n’est pas une programmation de retry. Il est seulement conservé comme frontière
bloquante du circuit ouvert. Les deux formats HTTP autorisés sont pris en charge :

- nombre de secondes décimal borné ;
- date HTTP RFC 1123 postérieure à la réception.

Une valeur vide, dépassant 128 caractères, invalide, nulle ou située dans le passé est ignorée. En
l’absence de valeur utilisable sur `429`, une garde locale de cinq minutes est enregistrée. Aucune
réouverture automatique du circuit n’existe.

## 4. Contenu et schéma

Une réponse `2xx` HTML, non JSON ou JSON invalide est conservée avant d’être classée
`UNEXPECTED_CONTENT`. Un JSON valide qui ne respecte pas `scheduled-events-v1` est conservé avant
d’être classé `SCHEMA_INCOMPATIBLE`. Dans les deux cas :

- le circuit passe à `OPEN` ;
- aucun résultat normalisé n’est écrit ;
- aucun retry n’est programmé ;
- l’octet brut et son SHA-256 restent la preuve de référence.

La classification de contenu est partagée entre le corpus hors ligne et les réponses simulées afin
d’éviter deux interprétations divergentes de JSON, HTML et contenu autre.

## 5. Échecs avant obtention d’un snapshot

Certains échecs interrompent la lecture avant qu’un `RawPayloadEvidence` sûr et complet soit
disponible. Ils n’écrivent donc aucun snapshot incomplet :

| Échec | Motif du circuit | Snapshot | Retry |
|---|---|---|---|
| délai de connexion ou de lecture | `TIMEOUT` | aucun | jamais |
| erreur d’entrée/sortie | `TRANSPORT_IO_FAILURE` | aucun | jamais |
| plus de 5 Mio | `PAYLOAD_TOO_LARGE` | aucun | jamais |
| motif sensible détecté | `SENSITIVE_CONTENT_REJECTED` | aucun | jamais |

La recherche de timeout parcourt la chaîne des causes, y compris lorsqu’un
`SocketTimeoutException` survient pendant la lecture bornée du corps.

## 6. Absence de fuite

Les exceptions de transport ne reprennent ni URI, ni payload, ni diagnostic interne. Les tests
injectent une valeur sensible synthétique, vérifient qu’elle est rejetée, puis contrôlent qu’elle
n’apparaît ni dans le message sûr ni dans les sorties capturées. Aucun header, cookie ou jeton n’est
persisté.

## 7. Circuit et arrêt

Tout incident est accepté uniquement lorsque le circuit est `CLOSED`. Il le fait passer à `OPEN` et
interdit toute nouvelle évaluation de transport. Un circuit ouvert ne peut pas se réactiver seul :

```text
OPEN
 │ arrêt explicite et revue opérateur
 ▼
LOCKED / OPERATOR_STOP
 │ nouvelle activation explicite
 ▼
CLOSED
```

Le résultat d’issue contient un invariant de construction qui refuse `retryScheduled=true`.

## 8. Preuves automatisées

La suite couvre :

- succès JSON simulé, métadonnées, hash, parsing et circuit inchangé ;
- déduplication du brut identique ;
- ordre `RAW_ONLY` avant parsing et classification idempotente PostgreSQL ;
- `400`, `401`, `403`, `429`, `500` et `503` sans retry ;
- `Retry-After` en secondes, date HTTP, invalide, passé et trop long ;
- HTML inattendu et rupture de schéma après conservation ;
- timeout, erreur d’entrée/sortie, taille excessive et contenu sensible ;
- absence de la valeur sensible dans les sorties ;
- impossibilité de réactiver un circuit ouvert sans arrêt et activation explicites.

Les tests de transport utilisent exclusivement `MockRestServiceServer` et l’origine simulée
`http://127.0.0.1:<port>/simulated/scheduled-events`. Le scanner de garde-fous continue d’interdire
toute URI SofaScore et toute autre construction de client HTTP.

Validation consolidée du 2026-08-12 : `115` tests standards et `5` tests PostgreSQL/Testcontainers
réussis, migrations V1/V2 appliquées sur PostgreSQL `18.4`, scanner de garde-fous réussi et zéro
appel réseau SofaScore exécuté.

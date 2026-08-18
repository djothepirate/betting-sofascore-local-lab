# J5 réel — Campagne humaine bornée du 2026-08-15

- **Work Order :** `WO-SS-20260815-006`
- **Branche :** `codex/j5-real-event-data-qualification`
- **Identité canonique :** `9b9e7909-62e7-3985-bc34-759ad8684224`
- **Identifiant fournisseur :** `16412917`
- **Rencontre :** FC Kryvbas Kryvyi Rih — FC Livyi Bereh Kyiv
- **État terminal observé avec l'ancienne politique :** `FAILED_LOCKED`
- **Code terminal observé :** `HTTP_STATUS_404`
- **Interprétation corrigée :** `EVENT_STATISTICS_UNAVAILABLE`
- **Appels fournisseur :** `1`
- **Retry :** `0`
- **Schémas fournisseur J5 validés :** `NO`

## 1. Prérequis local observé

Avant la campagne J5, la fiche J4 montrait la rencontre au statut `finished`, avec cinq versions
canoniques conservées. Le détail courant provenait du `snapshot:28`, reçu à
`2026-08-15T17:09:04.267671Z`, HTTP `200`, `8558` octets, classé `PARSED` par
`event-details-v2`. L'identifiant `16412917` était donc déjà lié à l'UUID local utilisé par la
campagne.

## 2. Préparation et confirmation

La préparation a repris l'identifiant fournisseur lié à l'identité locale et a affiché l'ordre
`STATISTICS → INCIDENTS → LINEUPS`. L'interface est passée à `AWAITING_CONFIRMATION` après une
préparation annoncée sans transport.

La phrase ponctuelle, son code aléatoire, le jeton de formulaire et le corps brut fournisseur ne
sont ni reproduits ni conservés dans ce rapport. Les captures opérateur restent non versionnées.

## 3. Résultat minimisé

La première requête autorisée ciblait le chemin construit localement
`/api/v1/event/16412917/statistics`. Elle a produit le snapshot brut local suivant :

```text
SNAPSHOT_ID=30
LOGICAL_ENDPOINT=EVENT_STATISTICS
REQUEST_KEY=EVENT_STATISTICS|eventId=16412917
RECEIVED_AT=2026-08-15T19:50:46.660436Z
HTTP_STATUS=404
CONTENT_TYPE=application/json
PAYLOAD_SIZE_BYTES=44
PAYLOAD_SHA256=61b6f2399d5cd9251af376fddf2243e9ea802fbcda179698437c344ef6db8b32
PARSER_VERSION=event-statistics-v2
SCHEMA_STATUS=TRANSPORT_ERROR
```

Le statut hors `2xx` a été classé avant parsing par l'ancienne politique. Aucune observation
normalisée J5 n'a été créée et la campagne a immédiatement basculé à `FAILED_LOCKED`, avec un seul
appel fournisseur déclaré. Ces lignes décrivent fidèlement l'exécution historique, pas la
sémantique retenue après correction.

```text
EVENT_STATISTICS=ATTEMPTED_HTTP_404
EVENT_INCIDENTS=NOT_ATTEMPTED
EVENT_LINEUPS=NOT_ATTEMPTED
PROVIDER_CALLS=1
RETRY=0
TERMINAL_STATE=FAILED_LOCKED
TERMINAL_CODE=HTTP_STATUS_404
```

## 4. Vérification du verrou global

Après l'incident, l'ouverture de la page J5 de `16391135`, Bolton Wanderers — Preston North End,
a affiché le même état terminal `FAILED_LOCKED` et le même code `HTTP_STATUS_404`. Le bouton de
préparation était désactivé. Le catalogue local ne contient aucun nouveau snapshot J5 pour cet
identifiant : cette consultation n'a donc déclenché aucun second transport.

## 5. Diagnostic et décision corrective

L'URI construite correspond exactement au chemin autorisé par le Work Order. Le HTTP `404` en
`application/json` constitue une réponse fournisseur réelle. Le retour ne prouve aucune anomalie
de route, d'identité ou de transport. La cause est le bloc générique de J5 qui transformait tout
statut non `2xx` en incident terminal. Or les statistiques ne sont pas obligatoires dans toutes
les compétitions ; dans le contexte fourni par l'opérateur, le championnat d'Ukraine n'est pas une
compétition majeure et cette famille peut ne pas être publiée.

Le contrat est donc amendé : un HTTP `404` sur l'un des trois endpoints J5 exacts représente
`ENDPOINT_UNAVAILABLE` dans le snapshot et `UNAVAILABLE · N/A` dans l'observation locale. Il reste
distinct d'un `EMPTY_VALID`, ne valide pas le schéma nominal, ne provoque aucun retry et n'empêche
plus les familles suivantes. Les autres incidents restent terminaux.

V9 corrige la classification du snapshot 30 lors de son application, sans modifier ses octets,
son hash, son heure ou son identifiant. Elle ne crée pas rétroactivement l'observation normalisée
qui n'existait pas lors de la première campagne.

```text
J5_REAL_CAMPAIGN_EXECUTED=YES
J5_REAL_FIRST_CAMPAIGN_RESULT=HTTP_404_MISCLASSIFIED_AND_LOCKED
J5_REAL_ROOT_CAUSE=NON_2XX_POLICY_TOO_BROAD
J5_REAL_CORRECTED_SNAPSHOT_STATUS=ENDPOINT_UNAVAILABLE
J5_REAL_CORRECTED_OBSERVATION_STATUS=EXPECTED_UNAVAILABLE_ON_CORRECTIVE_RETEST
J5_REAL_CORRECTIVE_RETEST=NOT_RUN
J5_REAL_STATISTICS_SCHEMA_VALIDATED=NO
J5_REAL_INCIDENTS_SCHEMA_VALIDATED=NO
J5_REAL_LINEUPS_SCHEMA_VALIDATED=NO
J5_REAL_RETRY_AUTHORIZED=NO
APPLICATION_STOP=CONFIRMED_LOCAL_PORT_CLOSED
ENV_RELOCK=PENDING_OPERATOR_CONFIRMATION
WORK_ORDER_CAN_BE_ARCHIVED=NO
```

L'application de la première campagne ne répond plus sur `127.0.0.1:8087`, ce qui confirme l'arrêt
du processus. Le correctif et sa migration ont été validés uniquement hors ligne. Une campagne
corrective n'est pas exécutée par Maven ni par l'agent : elle exige l'application préalable de V9,
la revue du Work Order actif, une préparation et une confirmation humaines nouvelles, puis le
reverrouillage manuel de la configuration locale.

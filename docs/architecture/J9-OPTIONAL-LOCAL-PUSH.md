# J9 — Contrat d’architecture du push local optionnel v1.0

## 1. Statut, autorité et portée

Ce document fige le contrat technique v1.0 du sender local prévu par ADR-SS-003. Il est produit
sous `WO-SS-20260901-027-optional-local-push-implementation` et ne constitue ni un endpoint du
Betting Project, ni une autorisation de livraison.

```text
CONTRACT_NAME=J7_OPTIONAL_LOCAL_PUSH
CONTRACT_VERSION=1.0
ADR_SS_003_STATUS=ACCEPTED
ADR_SS_003_SELECTED_TOPOLOGY=OPTIONAL_LOCAL_PUSH
J9_OFFICIAL_PERMISSION_STATUS=NOT_EVIDENCED
IMPORT_ENDPOINT_URI=NOT_DEFINED
REAL_RECEIVER_IMPLEMENTED=NO
REAL_RECEIVER_NETWORK_AUTHORIZED=NO
PROVIDER_NETWORK_AUTHORIZED=NO
LIVE_DELIVERY_AUTHORIZED=NO
VPS_DEPLOYMENT_AUTHORIZED=NO
PRODUCTION_AUTHORIZED=NO
```

Tant que la permission officielle reste `NOT_EVIDENCED`, le runtime doit refuser une cible réelle
avant toute résolution DNS et avant toute ouverture de socket. Les seules qualifications réseau de
WO-027 sont synthétiques, liées à `127.0.0.1` et à un port éphémère. Elles ne contactent ni
SofaScore, ni le Betting Project, ni un VPS.

Le receiver réel et son URI appartiennent à un Work Order distinct dans le dépôt du Betting
Project. Le présent contrat est consommable par ce futur Work Order, mais ne l’ouvre pas et ne
l’autorise pas.

## 2. Invariants non négociables

1. Seul un export J7 canonique v1 dont `manifest.validation.status` vaut exactement
   `HUMAN_VALIDATED` est éligible.
2. Le corps envoyé est la séquence d’octets exacte du fichier validé ; il n’est ni régénéré, ni
   recompressé, ni réordonné avant calcul du hash ou envoi.
3. La taille du corps doit être comprise entre `1` et `5 242 880` octets inclus.
4. Une livraison est une action opérateur distincte de l’acquisition et de la validation J7.
5. La livraison ne modifie jamais le statut J7 `HUMAN_VALIDATED`.
6. L’état de livraison est conservé dans un ledger séparé portant les six états de la section 7 :
   identité immuable, tentatives/résultats append-only et projection d’état strictement gardée.
7. La concurrence de livraison vaut `1` et aucun retry automatique n’existe.
8. Une répétition manuelle autorisée réemploie les mêmes octets et la même clé d’idempotence.
9. Le sender ne suit aucune redirection et n’emploie aucun fallback de transport ou de cible.
10. Toute cible réelle exige HTTPS avec authentification mTLS fail-closed.
11. Aucun payload fournisseur brut, cookie, jeton, état Playwright, clé privée, certificat privé ou
    chemin sensible ne traverse le contrat ou les journaux.
12. Une livraison ne peut appeler aucun parcours J3, J4, J5, Playwright ou endpoint fournisseur.
13. L’indisponibilité du sender ou du receiver ne bloque aucune fonction critique du Betting
    Project.
14. Le kill switch et toute configuration de cible restent désactivés par défaut.

## 3. Frontières et flux

```text
[export J7 local immuable + HUMAN_VALIDATED]
                    |
                    | action opérateur distincte
                    v
[éligibilité + taille + schéma + hash + permission]
                    |
                    | échec => aucun socket, NOT_ATTEMPTED
                    v
[ledger local séparé: IN_FLIGHT]
                    |
                    | POST HTTPS mTLS, concurrence 1, zéro retry
                    v
[receiver Betting Project — absent de WO-027]
                    |
                    | ACK v1.0 <= 16 KiB
                    v
[validation stricte et corrélation]
                    |
        +-----------+-------------------+
        |           |                   |
        v           v                   v
   DELIVERED   DUPLICATE_CONFIRMED   état d’échec borné
```

Le receiver n’a aucun chemin de retour vers l’acquisition SofaScore. Un ACK n’est qu’une preuve
de réception/import du fichier déjà validé ; il n’autorise jamais une acquisition, une relance ou
une modification des données sources.

## 4. Opération HTTP logique v1.0

### 4.1 Cible

Le contrat définit une opération logique `J7_CANONICAL_EXPORT_IMPORT_V1` et sa route relative
`/api/imports/sofascore/j7-canonical-events`, mais aucune origine et donc aucune URI réelle. Il
n’existe ni valeur par défaut, ni nom d’hôte de démonstration, ni endpoint absolu codé en dur. Une
future origine devra être HTTPS, provenir d’une configuration explicitement autorisée et être liée
au Work Order du receiver.

```text
HTTP_METHOD=POST
IMPORT_OPERATION=J7_CANONICAL_EXPORT_IMPORT_V1
RELATIVE_IMPORT_PATH=/api/imports/sofascore/j7-canonical-events
IMPORT_ENDPOINT_URI=NOT_DEFINED
REDIRECT_POLICY=NEVER_FOLLOW
REQUEST_COMPRESSION=NONE
DELIVERY_CONCURRENCY=1
AUTOMATIC_RETRY=0
```

### 4.2 En-têtes de requête

Une requête conforme porte exactement les informations contractuelles suivantes :

| En-tête | Valeur v1.0 | Règle |
|---|---|---|
| `Content-Type` | `application/vnd.betting-project.j7-canonical-event+json;version=1.0` | Aucun autre media type |
| `Accept` | `application/vnd.betting-project.j7-delivery-ack+json;version=1.0` | ACK v1.0 uniquement |
| `Idempotency-Key` | `j7:<exportId>:sha256:<fileSha256>` | Forme canonique de la section 5 |
| `X-J7-Protocol-Version` | `1.0` | Valeur constante |
| `X-J7-Export-Id` | UUID canonique minuscule | Doit correspondre au manifeste et à la clé |
| `X-J7-File-SHA256` | 64 caractères hexadécimaux minuscules | Hash des octets exacts du corps |
| `X-J7-Data-SHA256` | 64 caractères hexadécimaux minuscules | Doit correspondre à `manifest.dataSha256` |
| `Content-Length` | `1..5242880` | Contrôlé localement avant socket |

Le sender n’envoie ni `Authorization` bearer, ni cookie, ni identifiant fournisseur ajouté. Le
certificat client mTLS fournit l’identité de transport ; aucune clé privée n’est sérialisée dans
les en-têtes ou le corps.

### 4.3 Corps de requête

Le corps est le fichier conforme au schéma
[`j7-canonical-event-export-v1`](../../src/main/resources/schemas/j7-canonical-event-export-v1.schema.json).
Il doit satisfaire simultanément :

- enveloppe racine `{manifest,data}` et `schemaVersion=1.0.0` ;
- `manifest.validation.status=HUMAN_VALIDATED` ;
- `manifest.exportId` égal à `X-J7-Export-Id` ;
- SHA-256 des octets égal à `X-J7-File-SHA256` et à la composante hash de la clé ;
- `manifest.dataSha256` égal à `X-J7-Data-SHA256` ;
- taille maximale `5 242 880` octets ;
- fichier local résolu sous la racine d’export autorisée, sans lien symbolique ni traversée ;
- aucune donnée ajoutée après la validation humaine.

Le hash `manifest.dataSha256` protège la partie canonique `data`; il ne remplace pas
`fileSha256`, qui couvre l’intégralité des octets effectivement livrés.

## 5. Identité et idempotence

### 5.1 Forme canonique

```text
IDEMPOTENCY_KEY=j7:<exportId>:sha256:<fileSha256>
```

- `exportId` est la représentation UUID canonique minuscule avec tirets ;
- `fileSha256` est le SHA-256 hexadécimal minuscule des octets exacts du fichier ;
- la clé complète est ASCII et mesure exactement `111` octets ;
- elle est calculée localement, jamais fournie par l’opérateur ;
- elle est identique pour toute reprise manuelle du même artefact.

Expression régulière normative :

```text
^j7:[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}:sha256:[0-9a-f]{64}$
```

### 5.2 Sémantique côté receiver

| Observation | Effet receiver attendu | Réponse positive permise |
|---|---|---|
| Clé inconnue, export valide importé une fois | Créer un import unique et mémoriser clé + identités + hash | HTTP `201`, ACK `IMPORTED` |
| Même clé, mêmes `exportId` et `fileSha256` | Aucun second effet métier | HTTP `200`, ACK `DUPLICATE` |
| `exportId` déjà connu du sender avec un autre hash | Aucun transport | Refus local avant claim, aucune ligne ni nouvelle clé |
| Même clé mais en-têtes, corps ou hash divergents | Aucun import ; conflit terminal | HTTP `409`, jamais d’ACK positif |

Un résultat ambigu n’autorise pas à changer de clé. Après réconciliation humaine, une reprise
réemploie les mêmes octets et la même clé afin que le receiver puisse confirmer le doublon exact.
Le sender et le receiver protègent deux frontières différentes : l'identité divergente connue
localement n'atteint jamais le réseau ; un HTTP `409` reste une défense terminale du futur receiver
contre une requête pourtant admise par les préconditions locales.

### 5.3 Exemple synthétique

Cet exemple ne contient aucun identifiant fournisseur :

```text
exportId=11111111-1111-4111-8111-111111111111
fileSha256=aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
idempotencyKey=j7:11111111-1111-4111-8111-111111111111:sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
```

## 6. Accusé synchrone v1.0

### 6.1 Contrat positif

Le receiver renvoie un objet conforme au schéma
[`j7-delivery-ack-v1.schema.json`](../../src/main/resources/schemas/j7-delivery-ack-v1.schema.json).
Un ACK positif est accepté uniquement si toutes les conditions sont satisfaites :

- HTTP `201` avec `status=IMPORTED`, ou HTTP `200` avec `status=DUPLICATE` ;
- `Content-Type` exactement compatible avec le media type d’ACK v1.0 ;
- `Content-Encoding` absent ou `identity` ;
- corps non vide et d’au plus `16 384` octets lus sur le flux ;
- JSON strict, sans propriété inconnue, conforme au schéma ;
- `protocolVersion=1.0` ;
- `exportId`, `fileSha256` et `dataSha256` identiques à la tentative ;
- `remoteImportId` est un UUID canonique et `receivedAt` est au format date-time ;
- cohérence entre le code HTTP et le statut.

Le sender arrête la lecture et classe le résultat comme ambigu dès que la limite de 16 KiB pourrait
être dépassée. Il ne journalise jamais le corps d’un ACK invalide.

Exemple synthétique :

```json
{
  "protocolVersion": "1.0",
  "remoteImportId": "22222222-2222-4222-8222-222222222222",
  "status": "IMPORTED",
  "exportId": "11111111-1111-4111-8111-111111111111",
  "fileSha256": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
  "dataSha256": "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
  "receivedAt": "2026-09-01T00:00:00Z"
}
```

### 6.2 Un corps d’erreur n’est pas un ACK

Le sender classe une erreur à partir du code HTTP et de l’état du transport. Il ne dépend d’aucun
format d’erreur distant, ne conserve pas ce corps et n’en extrait aucun message destiné aux logs.
La limite de lecture de `16 384` octets s’applique à toute réponse : un corps d’erreur qui la
dépasse devient un échec de transport ambigu, sans code HTTP considéré comme fiable dans le
ledger.
Une réponse `2xx` sans ACK strictement valide reste ambiguë : elle ne devient jamais un succès par
présomption.

## 7. Ledger et machine d’états séparée

Le ledger de livraison est indépendant de la validation J7 et porte exactement les états suivants :

```text
NOT_ATTEMPTED
IN_FLIGHT
DELIVERED
DUPLICATE_CONFIRMED
REJECTED_TERMINAL
UNKNOWN_RECONCILIATION_REQUIRED
```

| État source | Événement vérifié | État cible |
|---|---|---|
| `NOT_ATTEMPTED` | Toutes les portes locales satisfaites, intention persistée avant socket | `IN_FLIGHT` |
| `NOT_ATTEMPTED` conceptuel | Permission absente, configuration désactivée, export inéligible, taille/hash invalides | Refus local ; aucune ligne créée, ledger inchangé |
| `IN_FLIGHT` | HTTP `201` + ACK `IMPORTED` strictement corrélé | `DELIVERED` |
| `IN_FLIGHT` | HTTP `200` + ACK `DUPLICATE` strictement corrélé | `DUPLICATE_CONFIRMED` |
| `IN_FLIGHT` | Réponse HTTP `4xx` normalement reçue, métadonnées valides et corps borné | `REJECTED_TERMINAL` |
| `IN_FLIGHT` | Timeout, TLS, I/O, `3xx`, `5xx`, ACK absent/invalide/surdimensionné/non corrélé | `UNKNOWN_RECONCILIATION_REQUIRED` |

Toute autre transition est interdite. Les trois états finaux `DELIVERED`,
`DUPLICATE_CONFIRMED` et `REJECTED_TERMINAL` ne sont pas transformés par un retry caché.
`UNKNOWN_RECONCILIATION_REQUIRED` est terminal pour une tentative, mais reste un état de livraison
ambigu répétable uniquement par une nouvelle action manuelle. Celle-ci crée une tentative
auditable liée au même export, aux mêmes octets et à la même clé, après réconciliation explicite.

Les métadonnées minimales du ledger sont : identité de tentative locale, `exportId`, hash du
fichier, clé d’idempotence, version de contrat `1.0`, état, horodatages de création/début/fin, catégorie
de résultat, code HTTP éventuel, `remoteImportId` éventuel et hash de l’ACK éventuellement accepté. Le
payload J7, le corps d’erreur, les certificats et les secrets n’y sont jamais stockés.

Un arrêt de processus peut laisser une tentative `IN_FLIGHT` sans résultat. Il n’autorise jamais un
nouvel envoi automatique. La seule récupération locale prévue consiste à classer manuellement
l’ordinal exact en `UNKNOWN_RECONCILIATION_REQUIRED`, au plus tôt `30 s` après l’instant de création
immuable attribué à la tentative par PostgreSQL. L’âge et l’horodatage de réconciliation proviennent
de `clock_timestamp()` dans la transaction, jamais d’une heure proposée par l’appelant. Le
classement s’effectue sous verrou transactionnel global et avec le code sûr
`OPERATOR_RECONCILED_STALE_IN_FLIGHT`. Une heure de début divergente, un ordinal différent, un
résultat déjà présent ou une tentative non encore périmée sont refusés. Ce classement n’affirme
pas que le receiver n’a rien reçu ; une éventuelle reprise explicite conserve donc les mêmes octets
et la même clé.

## 8. Classification des résultats

| Observation | Classification locale | Retry automatique |
|---|---|---|
| Refus local avant socket | `NOT_ATTEMPTED` avec code local borné | Jamais |
| HTTP `201` + ACK `IMPORTED` corrélé | `DELIVERED` | Jamais |
| HTTP `200` + ACK `DUPLICATE` corrélé | `DUPLICATE_CONFIRMED` | Jamais |
| Réponse HTTP `4xx` normalement reçue et bornée, y compris `401`, `403`, `409`, `413`, `415`, `422`, `429` | `REJECTED_TERMINAL` | Jamais |
| HTTP `3xx` | `UNKNOWN_RECONCILIATION_REQUIRED`; redirection non suivie | Jamais |
| HTTP `5xx` | `UNKNOWN_RECONCILIATION_REQUIRED` | Jamais |
| Timeout, rupture TLS, DNS, connexion ou lecture | `UNKNOWN_RECONCILIATION_REQUIRED` si `IN_FLIGHT` | Jamais |
| `2xx` inattendu, ACK absent, malformé, trop grand ou non corrélé | `UNKNOWN_RECONCILIATION_REQUIRED` | Jamais |
| Métadonnées hostiles ou corps supérieur à `16 384` octets, quel que soit le statut annoncé | `UNKNOWN_RECONCILIATION_REQUIRED`, sans HTTP fiable persisté | Jamais |

Le sender ne déduit jamais qu’une erreur de transport signifie « non reçu ». Il ne déduit jamais
non plus qu’un code `2xx` signifie « importé » sans ACK valide et corrélé.

### 8.1 Barrière contre les retries internes du JDK

Le publisher à souscription unique ne suffit pas à garantir une seule tentative réseau : le
`HttpClient` du JDK peut reprendre une connexion dans le même échange ou créer un nouvel échange
avant une seconde souscription du corps. Le sender exige donc les trois arguments JVM exacts dès
le démarrage du processus :

```text
-Djdk.httpclient.disableRetryConnect=true
-Djdk.httpclient.redirects.retrylimit=1
-Djdk.httpclient.enableAllMethodRetry=false
```

Ils sont complémentaires : le premier désactive la reprise interne de connexion, le deuxième
interdit un deuxième échange, notamment lorsqu’un pair déclare la requête non traitée, et le
troisième neutralise les reprises de méthodes supplémentaires, y compris une valeur éventuelle de
`net.properties`. La première instruction de `main` fige leur observation avant Spring. La porte
est vérifiée une première fois avant `HttpClient.build()` puis de nouveau avant `sendAsync` ; une
valeur absente, modifiée tardivement ou différente produit un refus local
`AUTOMATIC_REPLAY_BLOCKED` sans socket. Maven injecte ces valeurs dans Surefire, Failsafe et
`spring-boot:run`. Un lancement direct Eclipse ou `java -jar` doit les placer dans les arguments VM
avant le nom de la classe ou `-jar` ; les ajouter après démarrage ne peut pas satisfaire la porte.

## 9. mTLS et configuration de cible

Le profil réel demeure non provisionné et non autorisé sous WO-027 :

```text
MTLS_REQUIRED=YES
MTLS_CERTIFICATE_PROFILE=NOT_DEFINED_FOR_REAL_RECEIVER
CLIENT_KEY_TARGET_STORE=WINDOWS_CURRENT_USER_MY
CLIENT_PRIVATE_KEY_JAVA_ENCODING_EXPOSED=NO
CLIENT_PRIVATE_KEY_NATIVE_NON_EXPORTABLE=NOT_QUALIFIED_FOR_REAL_TARGET
SERVER_TRUST_BYPASS=NO
HOSTNAME_VERIFICATION_BYPASS=NO
REAL_CERTIFICATE_PROVISIONED=NO
```

Le futur sender doit sélectionner explicitement un certificat client unique dans le magasin de
certificats de l’utilisateur Windows, vérifier la présence de sa clé privée et refuser toute
sélection ambiguë. WO-027 refuse aussi une clé dont le provider Java expose un encodage. Cette
absence d'encodage Java n'atteste toutefois pas la politique native de provisionnement Windows :
la non-exportabilité native doit être qualifiée séparément avec le certificat réel. Le sender ne
doit ni sérialiser la clé, ni accepter un certificat expiré ou non encore valide, ni neutraliser la
chaîne de confiance ou la vérification du nom serveur.

Les détails de profil — autorités, EKU exacts, algorithmes, durées, politique de révocation,
rotation et récupération — restent à fixer avec le receiver réel dans son Work Order. Les tests de
WO-027 utilisent seulement une PKI synthétique éphémère et ne créent aucune confiance durable.

Le modèle de menace associé est décrit dans
[`J9-OPTIONAL-LOCAL-PUSH-THREAT-MODEL.md`](../security/J9-OPTIONAL-LOCAL-PUSH-THREAT-MODEL.md).

## 10. Porte de permission et absence de réseau réel

`J9_OFFICIAL_PERMISSION_STATUS=NOT_EVIDENCED` est une porte exécutoire, pas une simple mention
documentaire. Pour toute configuration autre que le harness de test synthétique :

1. le sender vérifie la permission avant de créer le client réseau ;
2. il refuse une URI absente, réelle ou non autorisée ;
3. il ne résout aucun hôte et n’ouvre aucun socket ;
4. il ne crée aucune ligne ; l’absence de livraison reste l’état conceptuel `NOT_ATTEMPTED` et le
   ledger demeure inchangé ;
5. il expose uniquement un code local minimisé, sans donnée du fichier.

Le harness loopback est test-only, lié à `127.0.0.1` et à un port attribué par le système, avec
certificats et données entièrement synthétiques. Il n’est pas une cible configurable du runtime
normal et doit être fermé et nettoyé après chaque test.

## 11. Audit, confidentialité et observabilité

Les journaux peuvent contenir :

- un identifiant de tentative local non sensible ;
- les préfixes courts et explicitement étiquetés des hashes si nécessaires au diagnostic ;
- l’état de livraison, une catégorie d’erreur locale et le code HTTP éventuel ;
- des horodatages UTC et la version de contrat.

Ils ne contiennent jamais : corps J7, ACK brut, corps d’erreur, URI réelle, nom d’hôte, chemin
utilisateur, alias sensible, sujet complet de certificat, clé privée, token, cookie ou état de
session. Les exceptions sont assainies avant persistance ou affichage.

## 12. Receiver réel et évolution du contrat

Le Work Order du receiver dans le dépôt du Betting Project doit au minimum :

- adopter explicitement ce contrat ou proposer une nouvelle version ;
- définir l’URI, l’autorisation d’exposition et le modèle de menace côté receiver ;
- garantir l’unicité idempotente et l’absence de second effet ;
- valider le schéma J7, la taille, les hashes et `HUMAN_VALIDATED` ;
- produire l’ACK strict et borné ;
- définir rétention, purge, restauration et audit de la copie importée ;
- prouver qu’aucun import ne déclenche SofaScore ;
- maintenir `NO_CRITICAL_DEPENDENCY`.

Tout changement incompatible exige une version de protocole nouvelle, une revue d’ADR-SS-003 et
un Work Order. Aucun assouplissement silencieux, champ d’ACK toléré, retry, URI de secours ou
fallback de sécurité n’est compatible avec v1.0.

## 13. Références

1. [ADR-SS-003 acceptée](../../ADR-SS-003-optional-integration-topology.md).
2. [Work Order WO-027](../work_orders/active/WO-SS-20260901-027-optional-local-push-implementation.md).
3. [Architecture de l’export J7](J7-CANONICAL-EVENT-EXPORT.md).
4. [Schéma de l’export J7](../../src/main/resources/schemas/j7-canonical-event-export-v1.schema.json).
5. [Schéma de l’ACK v1](../../src/main/resources/schemas/j7-delivery-ack-v1.schema.json).
6. [Modèle de menace](../security/J9-OPTIONAL-LOCAL-PUSH-THREAT-MODEL.md).
7. [Runbook WO-027](../runbooks/J9-OPTIONAL-LOCAL-PUSH.md).
8. [Règles du dépôt](../../AGENTS.md).

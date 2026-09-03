# J9 — Contrat d’architecture du push local optionnel v1.0

## 1. Statut, autorité et portée

Ce document fige le contrat technique v1.0 du sender local prévu par ADR-SS-003. Le socle a été
qualifié sous `WO-SS-20260901-027-optional-local-push-implementation`; le profil runtime local est
ajouté sous `WO-SS-20260902-035-j9-real-j7-delivery-sender`. Cette extension ne change ni le
format du contrat, ni l’autorité de gouvernance et ne constitue pas une autorisation de livraison.

```text
CONTRACT_NAME=J7_OPTIONAL_LOCAL_PUSH
CONTRACT_VERSION=1.0
ADR_SS_003_STATUS=ACCEPTED
ADR_SS_003_SELECTED_TOPOLOGY=OPTIONAL_LOCAL_PUSH
J9_OFFICIAL_PERMISSION_STATUS=NOT_EVIDENCED
LOCAL_RECEIVER_ORIGIN=https://127.0.0.1:8444
LOCAL_IMPORT_ENDPOINT_URI=https://127.0.0.1:8444/api/imports/sofascore/j7-canonical-events
WO035_RUNTIME_SENDER_STATUS=VALIDATED_LOCAL_FAIL_CLOSED
WO036_WINDOWS_WINDOWS_E2E_STATUS=RESUME_AUTHORIZED_PENDING_FRESH_MANIFEST_AND_PREFLIGHT
WO036_FIRST_ATTEMPT_RESULT=STOPPED_PRE_RECEIVER_PENDING_DISTINCT_RUNTIME_CORRECTION
WO037_BROWSER_ORIGIN_BOUNDARY_STATUS=VALIDATED
WO037_BROWSER_ORIGIN_BOUNDARY_RESULT=PASS_LOCAL_FAIL_CLOSED
WO037_OWNER_REVIEW_DECISION=VALIDATE
WO036_RESUME_AFTER_WO037_VALIDATION=AUTHORIZED_BY_SEPARATE_OWNER_DECISION
WO036_FRESH_MANIFEST_REQUIRED=YES
LOCAL_SYNTHETIC_RECEIVER_LOOPBACK_AUTHORIZED=YES
REAL_RECEIVER_NETWORK_AUTHORIZED=NO
PROVIDER_NETWORK_AUTHORIZED=NO
LIVE_DELIVERY_AUTHORIZED=NO
VPS_DEPLOYMENT_AUTHORIZED=NO
PRODUCTION_AUTHORIZED=NO
```

Tant que la permission officielle reste `NOT_EVIDENCED`, le runtime doit refuser tout export
`PROVIDER_DERIVED` avant création du client, résolution ou ouverture de socket. WO-035 fixe une
seule origine runtime, `https://127.0.0.1:8444`, mais n’exécute aucune requête vers elle. Les
qualifications historiques de WO-027 restent synthétiques, liées à `127.0.0.1` et à un port
éphémère. Le premier échange entre les deux applications, intégralement synthétique, appartient à
WO-036, actuellement arrêté avant son premier appel receiver et non reprenable sans décision
propriétaire distincte.

Le receiver et sa persistance appartiennent à `INT-001` dans le dépôt Betting Project. WO-035 ne
contacte pas cette implémentation et ne crée ni PR ni campagne E2E pour elle. Le présent contrat est
consommable par ce Work Order séparé, sans en autoriser le réseau.

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
15. Le port explicite de l’origine loopback doit appartenir à la borne fermée `[1, 65535]` : cette
    contrainte est validée par la configuration avant lecture de l’export et avant claim, puis
    vérifiée de nouveau par le transport avant la construction du client qu’il possède ou avant
    toute utilisation d’un client injecté.
16. Le runtime WO-035 n’accepte que l’origine exacte `https://127.0.0.1:8444`; aucune origine
    distante, aucun autre port, aucun chemin, query, fragment, user-info ou fallback ne sont admis.
17. L’action de livraison exige une préparation séparée puis la confirmation exacte, liée à la
    session, aux octets et au prochain ordinal de tentative attendu dans le ledger, et consommable
    une seule fois. L’ordinal reste volontairement absent de la phrase publique :
    `LIVRER J7 <exportId> SHA256 <fileSha256>`.
    Si une autre session modifie le ledger après la préparation, la demande devient invalide et
    doit être préparée de nouveau ; elle ne peut pas autoriser l’ordinal suivant. Le prochain
    ordinal confirmé est propagé jusqu’au claim PostgreSQL, qui le revalide sous le verrou global
    dans la transaction avant tout insert de tentative, passage `IN_FLIGHT` ou ouverture de socket.
18. La réconciliation manuelle emploie une confirmation distincte, elle aussi à usage unique :
    `RECONCILIER J7 <exportId> TENTATIVE <ordinal> SHA256 <fileSha256>`.
19. La provenance est classée à partir des cinq sources J7 vérifiées en `SYNTHETIC_ONLY`,
    `PROVIDER_DERIVED` ou `MIXED_OR_UNKNOWN`. La dernière classe est toujours refusée ; aucune
    valeur manquante ou mixte n’est présumée synthétique.
20. Sous WO-035, `PROVIDER_DERIVED` reste structurellement bloqué par
    `PROVIDER_OWNER_GO_REQUIRED`, même si d’autres propriétés étaient modifiées.

## 3. Frontières et flux

```text
[export J7 local immuable + HUMAN_VALIDATED]
                    |
                    | classification de provenance vérifiée
                    | action opérateur distincte
                    | confirmation exacte à usage unique
                    v
[éligibilité + taille + schéma + hash + permission]
                    |
                    | échec => aucun socket, NOT_ATTEMPTED
                    v
[ledger local séparé: IN_FLIGHT]
                    |
                    | POST HTTPS mTLS, concurrence 1, zéro retry
                    v
[receiver Betting Project — jamais contacté sous WO-035]
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

Le contrat v1.0 définit l’opération logique `J7_CANONICAL_EXPORT_IMPORT_V1` et sa route relative
`/api/imports/sofascore/j7-canonical-events`. WO-027 ne fixait aucune origine. WO-035 ajoute une
frontière runtime plus étroite : seule l’origine exacte `https://127.0.0.1:8444` est admise, soit
l’URI locale exacte
`https://127.0.0.1:8444/api/imports/sofascore/j7-canonical-events`. Il n’existe aucun nom d’hôte,
port alternatif, endpoint distant, cible de secours ou transformation d’URI autorisé.

```text
HTTP_METHOD=POST
IMPORT_OPERATION=J7_CANONICAL_EXPORT_IMPORT_V1
RELATIVE_IMPORT_PATH=/api/imports/sofascore/j7-canonical-events
LOCAL_RECEIVER_ORIGIN=https://127.0.0.1:8444
LOCAL_IMPORT_ENDPOINT_URI=https://127.0.0.1:8444/api/imports/sofascore/j7-canonical-events
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
- décodage UTF-8 strict, sans BOM, NUL, UTF-16 ni séquence malformée ;
- JSON strict, sans propriété inconnue, conforme au schéma ;
- `protocolVersion=1.0` ;
- `exportId`, `fileSha256` et `dataSha256` identiques à la tentative ;
- `remoteImportId` est un UUID canonique et `receivedAt` est la forme canonique UTC produite par
  `Instant.toString()`, terminée par `Z` ;
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
ambigu répétable uniquement par une nouvelle action manuelle. Celle-ci exige une attestation
explicite puis une nouvelle préparation liée à `attemptCount + 1`, et crée une tentative auditable
liée au même export, aux mêmes octets et à la même clé. Une demande préparée avant le résultat
`UNKNOWN_RECONCILIATION_REQUIRED` ne peut donc pas autoriser cette tentative suivante.

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

WO-035 implémente la composition Windows du sender, sans provisionner de certificat et sans
autoriser de connexion :

```text
MTLS_REQUIRED=YES
TLS_PROTOCOLS=TLSv1.3,TLSv1.2
CLIENT_KEY_TARGET_STORE=Windows-MY;provider=SunMSCAPI
CLIENT_CERTIFICATE_SELECTOR=EXACT_SHA256
SERVER_TRUST_STORE=Windows-ROOT;provider=SunMSCAPI
CLIENT_PRIVATE_KEY_JAVA_ENCODING_EXPOSED=NO
CLIENT_PRIVATE_KEY_NATIVE_NON_EXPORTABLE=NOT_QUALIFIED_FOR_REAL_TARGET
SERVER_TRUST_BYPASS=NO
HOSTNAME_VERIFICATION_BYPASS=NO
REAL_CERTIFICATE_PROVISIONED=NO
```

Le sender sélectionne un certificat client unique de `Windows-MY` par l’empreinte SHA-256 exacte
du certificat. Il exige une entrée avec clé privée, une période de validité courante, l’EKU
`clientAuth`, l’usage `digitalSignature` et une clé dont le provider Java n’expose pas les octets.
La confiance serveur provient explicitement de `Windows-ROOT` via `SunMSCAPI`; le truststore JVM
par défaut, un trust-all et la neutralisation du nom serveur ne sont pas des replis. Le client borne
les protocoles à TLS 1.3 et TLS 1.2.

L’absence d’encodage Java n’atteste toutefois pas la politique native de provisionnement Windows :
la non-exportabilité native doit être qualifiée séparément avec le certificat employé par WO-036.
La clé et le certificat ne sont ni sérialisés, ni consignés dans Git ou les journaux.

Les détails d’exploitation — autorité provisionnée, algorithmes, durées, politique de révocation,
rotation et récupération — restent à qualifier avec le receiver. Les tests de WO-027 utilisent
seulement une PKI synthétique éphémère et ne créent aucune confiance durable ; WO-035 ne consulte
pas `Windows-MY` tant que les portes de configuration et de payload ainsi que la confirmation
exacte n’ont pas été franchies.

Le modèle de menace associé est décrit dans
[`J9-OPTIONAL-LOCAL-PUSH-THREAT-MODEL.md`](../security/J9-OPTIONAL-LOCAL-PUSH-THREAT-MODEL.md).

## 10. Porte de permission et absence de réseau réel

`J9_OFFICIAL_PERMISSION_STATUS=NOT_EVIDENCED` est une porte exécutoire, pas une simple mention
documentaire. La classification des cinq sources vérifiées impose la matrice suivante :

| Provenance | Mode exigé | Effet sous WO-035 |
|---|---|---|
| `SYNTHETIC_ONLY` | `SYNTHETIC_LOOPBACK` | Préparable uniquement avec origine locale exacte, receiver/sender `PASS`, empreinte certificat et autorisation distante `false`; exécution reportée à WO-036 |
| `PROVIDER_DERIVED` | `PROVIDER_DERIVED` | Toujours bloqué par permission, autorisation distante et surtout `PROVIDER_OWNER_GO_REQUIRED` |
| `MIXED_OR_UNKNOWN` | Aucun | Refus `PAYLOAD_PROVENANCE_NOT_ELIGIBLE` avant transport |

Pour tout refus runtime :

1. le sender vérifie provenance, mode, qualifications, profil mTLS et, pour une provenance
   fournisseur, permission et autorisation propriétaire avant de créer le client réseau ;
2. il refuse une URI absente, réelle ou non autorisée ;
3. il ne résout aucun hôte et n’ouvre aucun socket ;
4. il ne crée aucune ligne ; l’absence de livraison reste l’état conceptuel `NOT_ATTEMPTED` et le
   ledger demeure inchangé ;
5. il expose uniquement un code local minimisé, sans donnée du fichier.

Le harness WO-027, lié à un port éphémère, reste test-only. Le profil runtime local WO-035, lié à
`8444`, n’est pas une autorisation d’échange : seul WO-036 pourra qualifier les deux applications
Windows avec des données entièrement synthétiques après une décision de reprise distincte et un
nouveau manifeste. Aucun export dérivé de SofaScore n’est admissible dans cette campagne.

La méthode applicative dédiée au mode `SYNTHETIC_LOOPBACK` impose explicitement
`expectedPayloadClass=SYNTHETIC_ONLY` avant claim et avant construction du transport. Elle ne
constitue pas un alias permissif du chemin générique : une provenance `PROVIDER_DERIVED` ou
`MIXED_OR_UNKNOWN` est refusée localement, sans interaction avec le ledger et sans socket.

## 11. Frontière locale, mono-exécution et cleanup

La frontière HTTP des actions de livraison et de réconciliation est liée au `HandlerMethod` déjà
résolu par Spring MVC. Elle couvre donc toute méthode de `J7DeliveryController` indépendamment de
l’encodage ou de la forme de l’URI brute. Elle exige un unique `Host: 127.0.0.1:8087`, accepte
`Origin` seulement s’il est unique et exactement égal à `http://127.0.0.1:8087`, et refuse
`Forwarded`, `X-Forwarded-Host` et `X-Forwarded-Proto`. Les protections `frame-ancestors 'none'`
et `X-Frame-Options: DENY` empêchent aussi l’encapsulation du geste opérateur.

Les réponses du sous-arbre canonique `/events/{canonicalEventId}/exports/**` appliquent
`Referrer-Policy: same-origin` afin qu’une navigation `POST` native issue de l’origine loopback
exacte conserve une valeur `Origin` vérifiable. Toutes les routes étrangères à ce sous-arbre
conservent `Referrer-Policy: no-referrer`. Cette sélection est effectuée sur le chemin d’application
après retrait du `contextPath`, sans correspondance de préfixe adjacent.

Cette politique de réponse ne modifie pas la règle d’admission. `Origin: null` reste une origine
opaque interdite ; aucune déduction depuis `Referer`, aucune confiance dans un proxy et aucune
comparaison partielle d’origine ne sont admises.

Une instance de transport n’est exécutable qu’une fois. Son garde atomique refuse une seconde
invocation et son body publisher est lui aussi one-shot : les octets exacts du J7 validé ne peuvent
être souscrits deux fois. Les propriétés JVM anti-retry restent obligatoires en complément.

La concurrence runtime est assurée par une gate singleton partagée avec la réconciliation :

```text
IDLE -> ACTIVE -> IDLE
IDLE -> ACTIVE -> POISONED
POISONED -> redémarrage du processus requis
```

Le lease `ACTIVE` reste détenu jusqu’après `transport.close()`, même si le ledger a déjà reçu un
état terminal. Une autre livraison ne peut donc pas démarrer pendant que les ressources natives de
la première sont encore nettoyées. La préparation et l’exécution d’une réconciliation acquièrent
la même gate, ce qui interdit de reclasser comme stale un `IN_FLIGHT` toujours possédé par le
processus actif. L’état `ACTIVE` produit `DELIVERY_IN_PROGRESS`. Toute erreur de fermeture
empoisonne la gate, y compris lorsqu’elle est supprimée derrière une erreur primaire, puis toutes
les opérations sont refusées avec `DELIVERY_RUNTIME_POISONED` jusqu’au redémarrage.

La réconciliation est enfin strictement `metadata/ledger-only` : elle utilise l’identité, les
hashes et l’ordinal persistés, sans ouvrir ni revérifier le fichier payload. La disparition ou
l’altération du fichier ne doit donc ni bloquer la classification prudente d’un état ambigu, ni
offrir une voie alternative de livraison.

## 12. Audit, confidentialité et observabilité

Les journaux peuvent contenir :

- un identifiant de tentative local non sensible ;
- les préfixes courts et explicitement étiquetés des hashes si nécessaires au diagnostic ;
- l’état de livraison, une catégorie d’erreur locale et le code HTTP éventuel ;
- des horodatages UTC et la version de contrat.

Ils ne contiennent jamais : corps J7, ACK brut, corps d’erreur, URI réelle, nom d’hôte, chemin
utilisateur, alias sensible, sujet complet de certificat, clé privée, token, cookie ou état de
session. Les exceptions sont assainies avant persistance ou affichage.

## 13. Receiver, WO-036 et évolution du contrat

Le Work Order `INT-001` du receiver dans le dépôt Betting Project doit au minimum :

- adopter explicitement ce contrat ou proposer une nouvelle version ;
- définir l’URI, l’autorisation d’exposition et le modèle de menace côté receiver ;
- garantir l’unicité idempotente et l’absence de second effet ;
- valider le schéma J7, la taille, les hashes et `HUMAN_VALIDATED` ;
- produire l’ACK strict et borné ;
- définir rétention, purge, restauration et audit de la copie importée ;
- prouver qu’aucun import ne déclenche SofaScore ;
- maintenir `NO_CRITICAL_DEPENDENCY`.

`WO-SS-20260902-036-j9-j7-local-e2e-qualification` a été lancé puis arrêté avant son premier appel
receiver à la suite de l’incompatibilité locale de politique de referrer consignée dans son rapport
gelé. WO-037 corrige et qualifie uniquement cette frontière navigateur. Son harnais explicite a
établi avec Spring/Tomcat réel, des services synthétiques en mémoire et Chromium que la préparation
native porte l’Origin loopback exact, tandis qu’une origine opaque reste refusée avant contrôleur.

La décision propriétaire distincte de reprise a désormais été reçue. Elle autorise uniquement un
redémarrage complet de WO-036 sur le loopback avec un corpus synthétique, après préflight frais et
gel d’un nouveau manifeste lié aux commits et JAR qualifiés. Elle ne rouvre pas le manifeste du
premier essai et n’autorise ni réseau fournisseur, ni livraison de données dérivées, ni receiver
distant, ni VPS, ni production.

Tout changement incompatible exige une version de protocole nouvelle, une revue d’ADR-SS-003 et
un Work Order. Aucun assouplissement silencieux, champ d’ACK toléré, retry, URI de secours ou
fallback de sécurité n’est compatible avec v1.0.

## 14. Références

1. [ADR-SS-003 acceptée](../../ADR-SS-003-optional-integration-topology.md).
2. [Work Order WO-027](../work_orders/completed/WO-SS-20260901-027-optional-local-push-implementation.md).
3. [Architecture de l’export J7](J7-CANONICAL-EVENT-EXPORT.md).
4. [Schéma de l’export J7](../../src/main/resources/schemas/j7-canonical-event-export-v1.schema.json).
5. [Schéma de l’ACK v1](../../src/main/resources/schemas/j7-delivery-ack-v1.schema.json).
6. [Modèle de menace](../security/J9-OPTIONAL-LOCAL-PUSH-THREAT-MODEL.md).
7. [Runbook WO-027](../runbooks/J9-OPTIONAL-LOCAL-PUSH.md).
8. [Règles du dépôt](../../AGENTS.md).
9. [Work Order WO-035](../work_orders/completed/WO-SS-20260902-035-j9-real-j7-delivery-sender.md).
10. [Work Order WO-037](../work_orders/completed/WO-SS-20260903-037-j9-j7-browser-origin-boundary.md).
11. [Qualification WO-037](../validation/J9-WO037-J7-BROWSER-ORIGIN-BOUNDARY-QUALIFICATION-20260903.md).

# Architecture J5 — import hors ligne multi-match atomique

## 1. Statut et intention

```text
DOCUMENT_DATE=2026-08-22
VALIDATION_DATE=2026-08-23
FEATURE_STATUS=VALIDATED
WORK_ORDER=WO-SS-20260822-010
BASE_COMMIT=47968ed98d5795f37f4e40f6e6d61519494526b4
BRANCH=codex/j5-offline-multi-match-batch
```

Cette évolution ajoute un flux Web synchrone pour importer en une seule opération les trois
familles J5 de 1 à 25 événements canoniques déjà présents. Elle automatise l'ingestion de preuves
locales sélectionnées ou déclarées manuellement. Elle ne collecte rien, ne contacte aucun
fournisseur et ne crée aucune activité périodique.

La fiche structurelle faisant autorité est
`docs/requirements/J5-OFFLINE-MULTI-MATCH-IMPORT-RULES.md`.

Les statuts du laboratoire restent :

```text
EXPERIMENTAL
LOCAL_ONLY
NOT_PRODUCTION_APPROVED
NO_CRITICAL_DEPENDENCY
```

## 2. Frontière d'architecture

### 2.1 Chaîne autorisée

```text
GET /j5-import-batches?date=...&zone=...
        │ lecture PostgreSQL locale seulement
        ▼
sélection Web de 1..25 UUID canoniques
        │ POST tokenisé, aucun eventId libre
        ▼
J5OfflineBatchPlanService
        │ fenêtre UTC + observations courantes + ordre stable
        ▼
J5OfflineBatchControlService
        │ plan immuable en mémoire, TTL 15 min, hash v1
        ▼
POST multipart plat de 3N preuves : fichier XOR déclaration 404
        │ token + requestId + phrase exacte portant le hash + acquittement
        ▼
J5OfflineBatchUploadService + J5LocalJsonImportProcessor.prepare
        │ noms + ensemble + tailles + contenu sensible + JSON + parseurs
        │ revalidation canonique complète
        ▼
claim synchronisé du contrôle dédié
        ▼
J5OfflineBatchTransactionalImporter
        │ revalidation à l'entrée de transaction
        │ providerEventId ASC, statistics → incidents → lineups
        ▼
UNE transaction PostgreSQL pour les 3N éléments
        │ snapshot brut → classification → observation normalisée
        │ mode MANUAL_LOCAL_JSON_IMPORT
        ▼
résultat terminal minimisé en mémoire
```

### 2.2 Chaînes interdites

```text
flux hors ligne ─X─> J5RealControlService
flux hors ligne ─X─> J5RealEventDataService
flux hors ligne ─X─> J5EventDataTransport
flux hors ligne ─X─> ConnectorGate
flux hors ligne ─X─> ProviderResponseCache
flux hors ligne ─X─> ProviderCallCoordinator
flux hors ligne ─X─> RestClient / HTTP
flux hors ligne ─X─> scheduler / watcher / retry / background worker
```

Ces absences doivent être visibles dans le graphe de constructeurs et prouvées par des tests
d'interaction stricte. Une simple condition empêchant l'appel d'un transport injecté ne suffit pas :
le transport ne doit pas être une dépendance du nouveau service.

## 3. Composants

Les responsabilités restent séparées du contrôle réel J5.

| Composant | Responsabilité |
|---|---|
| `J5OfflineBatchController` | routes Web, token, multipart, redirections et erreurs minimisées |
| `J5OfflineBatchPlanService` | sélection canonique, fenêtre date/zone, encodage canonique et hash |
| `J5OfflineBatchControlService` | état mémoire, expiration, phrase, claim unique et résultat terminal |
| `J5OfflineBatchUploadService` | ensemble de noms, bornes, preuve 404 canonique et rapprochement des preuves |
| `J5OfflineBatchImportService` | prévalidation de tous les événements puis claim et orchestration |
| `J5LocalJsonImportProcessor` | parsing transport-free, enveloppe 404 et traitement brut/normalisé réutilisable |
| `J5OfflineBatchTransactionalImporter` | revalidation finale et transaction unique des `3N` éléments |
| `J5OfflineBatchPolicy` | état du connecteur indifférent, automatisations coupées et conservation brute active |
| stores J4/J5 existants | lecture canonique et persistance brute/normalisée selon les contrats existants |
| parseurs J5 existants | validation et projection des trois familles sans changement de règle métier |

Le contrôleur peut être rattaché à l'explorateur d'événements, mais le service d'application et le
contrôle demeurent dédiés. Le flux J5 unitaire `/real` continue d'appartenir à
`J5RealControlService` et applique désormais le même choix fichier XOR déclaration 404 pour chacune
de ses trois familles.

## 4. Routes et interaction Web

Les routes locales explicites sont :

```text
GET  /j5-import-batches[?date={DATE}&zone={ZONE}]
POST /j5-import-batches/prepare
POST /j5-import-batches/execute
POST /j5-import-batches/stop
```

`prepare` reçoit la date, la zone, `1..25` valeurs `eventIds` contenant uniquement les UUID
canoniques cochés et un `localFormToken`. Il ne reçoit aucun identifiant fournisseur libre. La
réponse redirige vers la page dédiée et présente le plan serveur, les noms attendus, l'expiration,
le hash et la phrase exacte.

`execute` reçoit le token, `requestId`, `confirmationText`, `acknowledged`, les parties plates
`batchFiles`, les parties répétables `unavailable404`, ainsi que `date` et `zone` uniquement pour la
redirection. Ces deux coordonnées ne
participent ni à l'identité, ni à la revalidation, ni au claim : le plan conservé côté serveur reste
l'autorité. Le hash n'est pas une seconde entrée libre : il est porté par la phrase exacte et
comparé au plan. Le contrôleur énumère la collection complète de `HttpServletRequest.getParts()` :
les six contrôles doivent être présents une fois, tout autre nom de partie est rejeté, puis les
preuves sont rapprochées du plan par nom strict et réordonnées côté serveur. Chaque nom attendu doit
apparaître exactement une fois dans l'union des fichiers et des déclarations. L'ordre du navigateur
n'est jamais un contrat.

`stop` reçoit le token, le `requestId` du plan affiché et les coordonnées `date`/`zone` auxiliaires
de redirection. Il ne rend `STOPPED_LOCKED` que le plan actif portant exactement cet identifiant ;
un formulaire ancien ne peut donc pas arrêter un plan préparé plus récemment. Il ne supprime aucune
donnée persistée, puisqu'aucune donnée de lot n'existe avant l'import. L'expiration est passive :
aucune route ni tâche n'est appelée à échéance.

Tous les rendus et réponses du flux appliquent :

```text
Cache-Control: no-store
Pragma: no-cache
Expires: 0
X-Robots-Tag: noindex, nofollow, noarchive
```

Le document principal charge `static/js/j5-offline-batch-manifest.js` avec `defer`. Ce composant
ne reçoit aucune donnée supplémentaire : il rapproche les noms et tailles du `FileList` avec les
valeurs `unavailable404` et les noms attendus déjà rendus par le serveur. Il produit une synthèse
et des états par ligne, puis bloque seulement l'envoi navigateur lorsque le manifeste est
incomplet ou contradictoire. Il n'ouvre pas les fichiers, n'utilise ni stockage Web ni API réseau
et ne change aucun contrôle mémoire. La CSP de cette route autorise `script-src 'self'` sans
`unsafe-inline` ni `unsafe-eval`; toutes les autres pages conservent `script-src 'none'`.

Les formulaires utilisent le mécanisme `LocalFormTokenService` existant. Un token est consommé à
chaque POST parvenu au contrôleur, y compris lorsque la prévalidation métier du contenu échoue. Un
dépassement des limites de transport peut être rejeté par le multipart resolver avant cette entrée ;
le gestionnaire global renvoie alors une erreur bornée sans promettre que le token a été consommé.
Le navigateur reçoit dans tous les cas un token courant après redirection.

## 5. Sélection et fenêtre temporelle

La recherche s'appuie sur les requêtes locales de l'explorateur et sur l'observation canonique
courante, non sur les données J5 déjà présentes. Pour une date `D` et une zone `Z` :

```java
Instant fromInclusive = D.atStartOfDay(Z).toInstant();
Instant toExclusive = D.plusDays(1).atStartOfDay(Z).toInstant();
```

Le filtre compare l'instant de début de chaque rencontre à cette fenêtre demi-ouverte. La zone
doit être résolue comme `ZoneId`, puis conservée par son identifiant canonique. Le plan protège les
deux bornes UTC pour rendre une dérive de calcul ou de fuseau détectable.

La préparation relit chaque UUID sélectionné par `CanonicalEventStore`, exige que la vue renvoyée
soit la vue courante et capture :

- UUID de l'identité canonique ;
- `providerEventId` positif ;
- identifiant de l'observation canonique courante ;
- instant de la rencontre utilisé pour la fenêtre ;
- métadonnées d'affichage non incluses comme identité.

Les UUID inconnus, vues hors fenêtre, identifiants fournisseur dupliqués ou non positifs refusent
le plan entier.

## 6. Plan, hash et contrôle mémoire

### 6.1 Modèle immuable

Le plan est un record immuable contenant les champs définis par les exigences. Les listes sont des
copies non modifiables. Les événements sont triés avec un comparateur numérique sur
`providerEventId`, jamais par représentation textuelle. Les familles proviennent d'une constante
ordonnée partagée par le plan, le validateur et l'exécuteur.

L'encodage du hash utilise un `DataOutputStream` dans un ordre explicite. Une `Map` générique,
l'objet de vue HTML, `toString()` ou un serializer implicite sont interdits. Le hash est :

```text
Sha256.hex(canonicalPlanBinary)
```

Le flux canonique inclut la version `j5-offline-multi-match-plan-v1`, date, zone et bornes UTC puis,
pour chaque événement ordonné, son UUID canonique, son identifiant fournisseur, son observation
courante, `startsAt`, le hash normalisé canonique courant et ses trois noms attendus. Les chaînes
sont écrites par `writeUTF`, les entiers par `writeInt`/`writeLong` et les instants par seconde puis
nanoseconde. `requestId`, `preparedAt` et `expiresAt` sont exclus : le hash est déterministe par
contenu, indépendamment de l'intention et de son TTL.

### 6.2 État synchronisé

`J5OfflineBatchControlService` possède un moniteur ou une section critique unique pour
les transitions :

```text
LOCKED -> AWAITING_CONFIRMATION -> EXECUTING -> COMPLETED_LOCKED | FAILED_LOCKED
                            ├────> EXPIRED_LOCKED
                            └────> STOPPED_LOCKED
```

Le service reçoit une `Clock` injectée. À chaque entrée publique, il matérialise d'abord
l'expiration si `now >= expiresAt`. `confirmAndClaim` vérifie le `requestId`, l'acquittement et la
phrase exacte `IMPORTER {N} MATCHS J5 HORS LIGNE {PLAN_SHA256}` en temps constant, puis retourne un
claim immuable. Un second thread voit `EXECUTING` et échoue fermé.

Le dernier snapshot de contrôle et le dernier résultat restent en mémoire. Une nouvelle préparation
n'est autorisée qu'en `LOCKED` ou après un état terminal `*_LOCKED` ; elle remplace alors les données terminales.
Le redémarrage du bean ou du processus réinitialise tout. Aucune persistance ne doit être ajoutée.

### 6.3 Prévalidation et claim séparés

`J5OfflineBatchImportService` ne réclame pas le plan avant que `J5OfflineBatchUploadService` et
`J5LocalJsonImportProcessor.prepare` aient produit les objets préparés complets et immuables. Ils
contiennent les octets capturés et les résultats de prévalidation, indexés par événement et famille ;
les noms clients ne deviennent jamais une source d'identité.

Une erreur de contenu ou une dérive canonique `PLAN_CHANGED` avant le claim est donc récupérable
avec le même plan non expiré lorsque la cause peut être corrigée ; sinon l'opérateur l'annule puis
prépare un nouveau plan. La même dérive après claim provoque le rollback et `FAILED_LOCKED`.

## 7. Ingestion multipart bornée

### 7.1 Défense en profondeur

Spring accepte 6 MB par partie et 32 MB par requête. Le seuil mémoire multipart est fixé à 32 MB,
égal à la limite de requête et supérieur à la limite de partie : dans cette enveloppe déjà bornée,
le conteneur ne déporte donc pas une partie vers un fichier temporaire avant la validation. Le
maximum Tomcat est fixé à 82 parties, soit les 75 preuves du lot maximal, les six champs de contrôle
et l'unique sentinelle fichier vide qu'un navigateur peut émettre lorsqu'aucun fichier n'est choisi.
Le validateur applique lui-même les limites métier binaires plus strictes :

```text
MAX_FILE_BYTES=5*1024*1024
MAX_BATCH_BYTES=25*1024*1024
```

Il vérifie la taille annoncée, puis le nombre réel d'octets capturés. Il additionne dans un `long`
avant toute copie non bornée. `RawPayloadEvidence.capture` fournit l'empreinte et le scanner de
contenu sensible existants. Aucun octet multipart n'est écrit sur disque par le conteneur ou le
code applicatif et aucun répertoire de staging n'est créé.

### 7.2 Rapprochement

Le validateur :

1. énumère toutes les parties natives et exige les six contrôles uniques, bornés et sans fichier ;
2. refuse toute partie inconnue, dupliquée ou manquante ;
3. accepte les fichiers non vides `batchFiles` et les déclarations `unavailable404` répétables ;
4. ignore au plus une sentinelle fichier vide sans nom, laquelle ne constitue jamais une preuve ;
5. exige la forme ASCII canonique de chaque `Content-Disposition` brut avant toute valeur décodée ;
6. refuse `filename*`, encodages RFC 5987/RFC 2047, quoted-pairs, séparateurs et paramètres extra ;
7. applique la regex ancrée et parse le nombre avec contrôle d'overflow ;
8. compare l'union fichier/déclaration aux `3N` noms attendus et refuse tout chevauchement ;
9. capture les fichiers et matérialise chaque déclaration par le JSON canonique local ;
10. prévalide tous les octets puis retourne les éléments dans l'ordre du plan.

Il ne s'arrête pas en laissant un effet de bord : aucune étape de cette phase ne possède de store.
Les messages d'erreur citent un code et une famille abstraite, pas le nom reçu.

## 8. Parsing et 404 fermé

Le flux réutilise les parseurs qualifiés :

```text
EVENT_STATISTICS -> EventStatisticsV2Parser
EVENT_INCIDENTS  -> EventIncidentsV17Parser
EVENT_LINEUPS    -> EventLineupsV2Parser
```

L'injection peut viser leurs interfaces ou super-types existants pour préserver la testabilité.
Les constantes de version des parseurs restent la source de vérité des métadonnées.

Avant le parser métier, un lecteur JSON strict reconnaît éventuellement l'unique enveloppe 404
fermée. La racine ne contient que `error`, et l'objet `error` uniquement `code=404` entier plus
`message`/`reason` facultatifs et bornés. Toute autre forme poursuit le parser métier ou échoue
comme payload incompatible.

Quand l'opérateur possède le corps JSON, ses octets sont conservés exactement. Quand le navigateur
ne permet pas d'enregistrer le corps d'un 404 effectivement observé, la case dédiée produit
localement et sans transport :

```json
{"error":{"code":404,"message":"LOCAL_OPERATOR_DECLARED_HTTP_404"}}
```

Cette enveloppe est une preuve de déclaration locale, pas une copie ni une reconstruction du corps
fournisseur. Il n'existe aucun champ de statut libre, aucune déduction depuis `notstarted` et aucune
case générique pour un autre code. Fournir simultanément le fichier et la déclaration d'une famille
est interdit.

Les résultats prévalidés distinguent `PARSED` et `EXPLICIT_UNAVAILABLE_404`. Pour un fichier ils
conservent les octets originaux ; pour une déclaration ils conservent les octets canoniques ci-dessus.
Dans les deux cas, le snapshot et son hash portent exactement les octets prévalidés.

## 9. Transaction et persistance

### 9.1 Frontière transactionnelle

Une méthode publique unique annotée `@Transactional` reçoit le claim et le lot préparé. Elle ne
doit pas être appelée par auto-invocation interne. Elle réalise avant la première écriture :

1. le contrôle hors ligne final ;
2. la relecture des `N` observations canoniques courantes ;
3. la vérification de la fenêtre, des UUID, des `providerEventId` et observation IDs ;
4. la revalidation de `startsAt`, du hash canonique courant et de tout le contenu protégé du plan ;
5. la vérification de l'ordre et de la présence des `3N` éléments préparés.

Puis elle traite les événements par `providerEventId` croissant et les familles dans l'ordre fixe.
Toutes les opérations des stores utilisent la transaction appelante ; aucune propagation
`REQUIRES_NEW` ou transaction autonome n'est permise dans ce chemin.

### 9.2 Brut avant normalisé, sans commit intermédiaire

Pour chaque élément :

```text
RawPayloadEvidence exact
    -> RawManualCallSnapshot(MANUAL_LOCAL_JSON_IMPORT, RAW_ONLY)
    -> save/deduplicate snapshot + occurrence
    -> parse ou observation unavailable
    -> J5EventDataObservation liée au snapshot/hash/parser/receivedAt
    -> save/deduplicate observation et enfants
    -> classification du snapshot
```

La relation « brut avant normalisé » est respectée dans l'ordre SQL, mais son atomicité est celle
du lot global. Une erreur au 75e élément annule également les 74 précédents, leurs occurrences et
leurs classifications.

Les clés existantes restent :

```text
EVENT_STATISTICS|eventId=<providerEventId>
EVENT_INCIDENTS|eventId=<providerEventId>
EVENT_LINEUPS|eventId=<providerEventId>
```

Le statut local 200 ou 404 est inféré, le content type vaut `application/json`, la latence vaut
zéro et l'instant d'import provient d'une `Clock`. Les règles de déduplication V25 distinguent le
mode `MANUAL_LOCAL_JSON_IMPORT` de `DIRECT_LOCAL_ENDPOINT`. Après la projection normalisée, le
processeur applique le verdict final aux snapshots `INSERTED` comme `DEDUPLICATED`. Le store
n'autorise qu'une transition `RAW_ONLY` vers ce verdict ou la répétition exacte d'un verdict déjà
posé ; toute contradiction fait échouer la transaction complète.

### 9.3 Échec

Toute exception doit remonter hors de la méthode transactionnelle pour provoquer le rollback.
Après rollback, la couche de contrôle passe le plan à `FAILED_LOCKED` avec un code minimisé. Elle ne
conserve pas la liste d'identifiants générés pendant la transaction. Aucun retry automatique ou
traitement compensatoire n'est lancé.

## 10. Cohérence avec J6 et J7

Les tables et occurrences existantes suffisent :

- J6 voit les snapshots et observations réellement committés et leur provenance ;
- une réimportation via un nouveau plan suit les règles actuelles de déduplication et
  d'occurrences ;
- J7 sélectionne les observations courantes comme aujourd'hui ;
- les verrous ou contrôles de dérive existants de J7 ne sont ni contournés ni réutilisés comme
  contrôle du lot ;
- aucun historique durable du plan, de la phrase ou du résultat n'est nécessaire.

Aucune migration propre au plan multi-match ou à WO-010 n'est nécessaire. La migration transverse
V26 autorise historiquement V14, V28 autorise V15, V36 autorise V16 et V37 autorise le parseur d'incidents J5 courant V17 pour toutes
les voies de persistance J5. V28 ne crée aucun stockage de plan ou de résultat de lot et ne change
pas cette décision.

## 11. Politique de configuration

`J5OfflineBatchPolicy` vérifie les trois barrières actives nécessaires au flux :

```text
automaticRefreshEnabled=false
livePollingEnabled=false
storeRawPayloads=true
```

`enabled` peut être `false` ou `true`. Les opt-ins J3/J4/J5/découverte, `baseUrl` et
`allowedEndpoints` peuvent donc rester configurés et les politiques manuelles réelles peuvent être
éligibles simultanément. Cette coexistence ne donne aucune voie réseau au lot hors ligne : il ne
reçoit aucun transport, cache ou coordinateur dans son graphe de dépendances.

La politique est appelée pendant préparation, prévalidation/avant claim et transaction. Toute
dérive d'une barrière active refuse fermée. Aucun nouveau flag « offline enabled » n'est
nécessaire.

Les valeurs par défaut de `application.yml`, `server.address=127.0.0.1`, le profil Maven live
bloqué, le catalogue `callable=false` et `ConnectorGate` ne changent pas.

## 12. Concurrence et disponibilité

- un contrôle de processus sérialise préparation, claim, annulation et terminaison ;
- un plan `EXECUTING` refuse une nouvelle préparation ;
- une requête import est synchrone ; aucune file de travaux n'existe ;
- deux requêtes utilisant le même plan ne peuvent pas committer toutes les deux ;
- l'ordre stable limite les risques d'interblocage lors de l'accès aux mêmes événements ;
- la transaction peut être longue pour 75 preuves, mais reste bornée à 25 Mio et 25 événements ;
- une interruption avant le commit entraîne le rollback PostgreSQL ; une interruption dans
  l'étroite fenêtre située après le commit et avant la terminaison du contrôle peut conserver les
  données `3N` tout en perdant l'état et le résultat mémoire ;
- après redémarrage, l'opérateur vérifie les données locales puis prépare explicitement un nouveau
  plan si nécessaire ;
- il n'existe aucune promesse de reprise après crash.

## 13. Observabilité minimisée

Les métriques et résultats peuvent compter : plans préparés, lots complétés/échoués, événements,
preuves, déclarations 404, insertions et déduplications. Les invariants suivants doivent être affichables ou
assertables :

```text
providerCalls=0
providerCacheReads=0
providerCacheWrites=0
networkCoordinatorAcquisitions=0
automaticRetries=0
```

Les logs ne contiennent pas le JSON, le nom de fichier reçu, le chemin local, le token, la phrase,
les en-têtes ou une URI. Le `requestId` et le SHA peuvent être omis des logs ou tronqués de manière
non ambiguë pour un diagnostic local ; ils restent complets seulement dans l'état mémoire lorsque
nécessaire à la confirmation.

## 14. Stratégie de tests

### 14.1 Tests unitaires

- sélection 1/25 acceptée, 0/26 et doublons refusés ;
- résolution de zone et journées DST ;
- tri numérique, noms attendus et hash canonique ;
- mutation de chaque champ protégé détectée ;
- expiration avec `Clock`, états et concurrence de claim ;
- phrase exacte, acquittement et séparation de `J5RealControlService` ;
- matrice de noms, tailles, contenu sensible, JSON strict, fichier XOR déclaration et 404 ;
- revalidation et politique de configuration complète.

### 14.2 Tests Web

- recherche et cases à cocher uniquement pour les UUID locaux ;
- absence d'entrée `eventId` ;
- token absent, invalide et réutilisé ;
- multipart de 0 à 75 fichiers complété par 0 à 75 déclarations, `3N` preuves, ordre mélangé et
  erreurs minimisées ;
- synthèse navigateur, états par preuve, blocage d'un manifeste incomplet ou contradictoire et CSP
  limitée au script local, avec revalidation serveur toujours autoritaire ;
- headers `no-store`/`noindex` ;
- aucune réflexion du contenu ou du nom reçu ;
- aucune mutation de l'état de la campagne J5 réelle.

### 14.3 Tests d'intégration PostgreSQL

- commit nominal `3N` et ordre déterministe ;
- rollback total sur panne au début, au milieu et au dernier élément ;
- provenance, clés, parseurs, hash, heures et 404 ;
- réimport par nouveau plan, déduplication V25 et occurrences J6 ;
- coexistence avec une provenance directe et visibilité J6/J7 ;
- absence de migration propre au lot, compatibilité avec la migration transverse V26 du parseur
  J5 et respect des contraintes append-only.

### 14.4 Garde source et interactions

Un test ou contrôle source ciblé doit refuser l'introduction dans ce périmètre de
`@Scheduled`, `@EnableScheduling`, `WatchService`, client HTTP, retry ou staging. Les doubles de
transport, cache et coordinateur sont stricts et doivent constater zéro interaction pendant tous
les chemins nominal, 404 et erreur.

## 15. Décisions exclues

Ce design ne couvre pas la collecte automatique des fichiers, le dépôt surveillé, une API distante,
une exécution asynchrone, une reprise persistante ou une importation partielle. Chacune constituerait
une nouvelle architecture avec un nouveau Work Order. Tout accès réseau exigerait aussi une revue
explicite de l'ADR-SS-001 avant la moindre implémentation.

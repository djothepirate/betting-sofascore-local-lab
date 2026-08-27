# Règles d'import J5 hors ligne multi-match atomique

> Fiche structurelle de référence du circuit Web permettant d'importer, en un lot strictement
> local, les statistiques, incidents et compositions de plusieurs rencontres canoniques.
>
> Cette fiche ne constitue ni une documentation officielle de SofaScore, ni une autorisation de
> collecte réelle, ni une approbation de production.

## 1. Statut et autorité

```text
DOCUMENT_DATE=2026-08-22
VALIDATION_DATE=2026-08-23
FEATURE_STATUS=VALIDATED
WORK_ORDER=WO-SS-20260822-010
PLAN_SCHEMA_VERSION=j5-offline-multi-match-plan-v1
EXECUTION=LOCAL_ONLY_SYNCHRONOUS
```

La fonctionnalité conserve les statuts :

```text
EXPERIMENTAL
LOCAL_ONLY
NOT_PRODUCTION_APPROVED
NO_CRITICAL_DEPENDENCY
```

En cas de divergence, le présent document fait autorité sur l'interface et l'implémentation de ce
nouveau flux. Les règles métier J5, les schémas de persistance, les migrations append-only V25 et
V26, J6 et J7 existants continuent de faire autorité dans leur périmètre. Aucun élément de cette
fiche n'autorise un appel fournisseur.

## 2. Vocabulaire

| Terme | Définition |
|---|---|
| événement canonique | identité UUID locale et son observation courante déjà persistées |
| `providerEventId` | identifiant fournisseur numérique positif porté par l'identité canonique |
| famille | une valeur parmi `statistics`, `incidents`, `lineups` |
| plan | intention immuable, bornée et mémorisée quinze minutes par le processus |
| preuve attendue | couple exact `(providerEventId, famille)` matérialisé par un nom strict |
| déclaration 404 | choix opérateur fermé, après observation d'un HTTP 404, produisant un JSON local canonique |
| claim | transition atomique du plan prêt vers `EXECUTING` après tous les contrôles préalables |
| terminal | `EXPIRED_LOCKED`, `STOPPED_LOCKED`, `COMPLETED_LOCKED` ou `FAILED_LOCKED` |
| lot | ensemble plat et complet des `3N` preuves d'un plan |

Le terme « automatisation » désigne ici l'automatisation locale de la validation et de la
persistance. Il ne désigne jamais l'acquisition automatique des corps JSON.

## 3. Sélection canonique

### 3.1 Recherche

L'opérateur choisit une date civile ISO `YYYY-MM-DD` et un identifiant IANA de fuseau. Le serveur
résout la zone et calcule :

```text
fromInclusive = date.atStartOfDay(zone).toInstant()
toExclusive   = date.plusDays(1).atStartOfDay(zone).toInstant()
```

La fenêtre est toujours demi-ouverte `[fromInclusive, toExclusive)`. Elle n'est jamais construite
par addition fixe de 24 heures ; les transitions d'heure d'été et d'hiver restent donc correctes.

La liste Web provient exclusivement des événements canoniques locaux dont l'observation courante
se situe dans cette fenêtre. Le serveur affiche des métadonnées utiles, mais la valeur postée de
chaque sélection est seulement son UUID canonique.

### 3.2 Bornes et identité

- le nombre d'UUID distincts est compris entre 1 et 25 inclus ;
- chaque UUID doit exister au moment de la préparation ;
- chaque observation courante doit appartenir à la date et à la zone demandées ;
- chaque identité doit porter un `providerEventId` strictement positif et représentable par un
  `long` Java ;
- deux UUID ne peuvent pas résoudre le même `providerEventId` dans un plan ;
- la saisie libre d'un `eventId` ou `providerEventId` est interdite dans le HTML, le contrôleur et
  le service ; une valeur reçue hors de la liste canonique n'est jamais adoptée ;
- l'import ne crée, ne corrige et ne remplace aucune identité canonique.

Une erreur sur une sélection refuse la préparation entière. Il n'existe pas de sous-plan valide.

## 4. Plan immuable de quinze minutes

### 4.1 Contenu

Un plan valide contient au minimum :

```text
requestId                      UUID aléatoire de la demande
preparedAt                     Instant UTC
expiresAt                      preparedAt + 15 minutes
date                           LocalDate demandée
zoneId                         identifiant IANA canonique
fromInclusive                  Instant UTC inclusif
toExclusive                   Instant UTC exclusif
events[]                       entrées ordonnées
planSha256                     hexadécimal minuscule sur 64 caractères
confirmationPhrase             phrase exacte générée
```

`j5-offline-multi-match-plan-v1` est la version d'encodage implicite inscrite dans le calcul du
hash. Elle n'est pas une propriété modifiable ni un champ séparé reçu depuis le navigateur.

Chaque entrée `events[]` contient :

```text
canonicalEventId               UUID canonique
providerEventId                entier décimal positif
canonicalObservationId         identifiant de l'observation courante
startsAt                       instant courant de la rencontre
canonicalNormalizedSha256      hash de l'observation canonique courante
expectedFiles[]                trois noms stricts ordonnés
```

Les événements sont triés par `providerEventId` numérique croissant. À l'intérieur d'un événement,
les familles sont toujours ordonnées :

```text
statistics
incidents
lineups
```

L'ordre de sélection dans le formulaire et l'ordre de réception des parties multipart ne changent
jamais l'ordre du plan.

### 4.2 Encodage canonique et hash

Le SHA-256 est déterministe par contenu. Il est calculé sur un flux binaire canonique écrit dans
l'ordre fixe suivant :

1. `planVersion` par `DataOutputStream.writeUTF` ;
2. `date.toString()` et `zoneId.getId()` par `writeUTF` ;
3. `fromInclusive` puis `toExclusive`, chacun sous la forme `epochSecond` par `writeLong`, puis
   `nano` par `writeInt` ;
4. le nombre d'événements par `writeInt` ;
5. pour chaque événement déjà trié, les deux composantes `long` de l'UUID canonique, puis
   `providerEventId` et `canonicalObservationId` par `writeLong` ;
6. `startsAt` sous la même forme seconde/nanoseconde ;
7. `canonicalNormalizedSha256` par `writeUTF` ;
8. les trois noms de fichiers attendus, dans l'ordre fixé, chacun par `writeUTF`.

`Sha256.hex` produit le résultat hexadécimal minuscule. Le `requestId`, `preparedAt`, `expiresAt`,
l'état, `planSha256`, la phrase et les libellés d'équipes sont explicitement exclus du hash. Ainsi,
deux préparations du même contenu canonique ont le même hash malgré des UUID de demande et instants
de préparation différents. Toute nouvelle forme d'encodage exige une nouvelle `planVersion`. Le
code ne dépend ni d'une map, ni d'un ordre de sélection client, ni d'un serializer implicite.

### 4.3 Phrase exacte

La phrase affichée et attendue est exactement :

```text
IMPORTER {N} MATCHS J5 HORS LIGNE {PLAN_SHA256}
```

`{N}` est le nombre décimal de rencontres et `{PLAN_SHA256}` le hash minuscule. La comparaison est
en temps constant sur la représentation UTF-8 exacte. Il n'y a ni trim, ni conversion de casse,
ni normalisation Unicode, ni remplacement d'espace.

### 4.4 Stockage et expiration

- le plan, son état et son dernier résultat vivent seulement dans la mémoire du processus ;
- aucune table, migration, fichier, cache persistant, journal de reprise ou export n'est créé ;
- l'expiration intervient à `now >= expiresAt` ;
- l'expiration est évaluée à chaque lecture, préparation, annulation ou import ; aucun scheduler ne
  la déclenche ;
- un redémarrage perd le plan et le résultat et revient à `LOCKED` ;
- un seul plan peut être `AWAITING_CONFIRMATION` ou `EXECUTING` à la fois ;
- l'annulation explicite exige le `requestId` exact du plan encore actif ; un formulaire d'un plan
  antérieur est refusé sans modifier le contrôle courant ;
- après tout état terminal, une nouvelle préparation remplace proprement l'état terminal ;
- un plan expiré ou terminal ne peut jamais être réclamé.

## 5. Contrat multipart

### 5.1 Forme plate

La requête `multipart/form-data` contient :

- `requestId` ;
- `confirmationText` ;
- `acknowledged=true` ;
- un `localFormToken` à usage unique ;
- `date` et `zone`, utilisées uniquement pour la redirection Web et jamais comme identité du plan
  ou autorité du claim ;
- une collection plate `batchFiles` contenant de 0 à `3N` fichiers ;
- de 0 à `3N` parties répétables `unavailable404`, chacune portant un nom attendu exact ;
- pour chaque nom attendu, exactement une des deux preuves : fichier XOR déclaration 404.

Il n'y a ni archive, ni zip, ni manifest fourni par le client, ni sous-répertoire. Le plan serveur
est le seul manifest faisant autorité. La frontière énumère toutes les parties natives de la
requête, et pas seulement celles liées par Spring à `batchFiles`. Chacun des six contrôles doit
apparaître exactement une fois, sans nom de fichier, avec au plus 512 octets UTF-8 strictement
égaux à la valeur liée. Toute partie inconnue, dupliquée ou manquante est refusée. Une requête
maximale contient au plus 82 parties : six contrôles, 75 preuves et l'unique sentinelle fichier vide
qu'un navigateur peut produire lorsqu'aucun fichier n'est sélectionné.

### 5.1 bis Assistance locale au manifeste

La page peut rapprocher avant envoi les métadonnées du sélecteur de fichiers et les déclarations
404 du manifeste serveur déjà affiché. Cette assistance :

- indique pour chaque nom attendu `FICHIER`, `404`, `CONFLIT`, `DOUBLON`, `INVALIDE` ou
  `À FOURNIR` ;
- affiche séparément le nombre de fichiers, de déclarations et de saisies par rapport à `3N` ;
- signale par nom les conflits fichier plus déclaration, doublons, noms non prévus, fichiers vides
  ou trop grands et preuves manquantes ;
- désactive localement l'action tant que le manifeste visible n'est pas exact ;
- ne lit jamais les octets, n'émet aucun appel, ne conserve aucun état et ne modifie pas le plan.

Cette validation navigateur n'est pas une autorité de sécurité. Le HTML reste fonctionnel sans
JavaScript et le serveur réénumère toutes les parties, revalide le manifeste puis refuse fermé. Le
script est un fichier statique de même origine autorisé uniquement par la CSP de
`/j5-import-batches` ; les scripts restent interdits sur les autres documents.

### 5.2 Noms stricts

La grammaire acceptée est :

```regex
\Aevent-([1-9][0-9]{0,18})-(statistics|incidents|lineups)\.json\z
```

Après correspondance, l'identifiant est parsé sans troncature comme `long` positif. Les valeurs
supérieures à `Long.MAX_VALUE` sont refusées. Le nom doit être le basename original exact et ne
contenir ni `/`, ni `\`, ni NUL, ni segment de chemin. Les variantes de casse, espaces, doubles
extensions, zéros initiaux et caractères percent-encodés ne sont pas normalisés. La valeur brute
de `Content-Disposition` doit être exactement la forme ASCII canonique
`form-data; name="batchFiles"; filename="<nom strict>"`. `filename*`, RFC 5987, RFC 2047,
quoted-pairs, paramètres supplémentaires et variantes de disposition sont refusés avant toute
confiance accordée au nom décodé par le conteneur.

Pour chaque entrée du plan, les seuls noms autorisés sont :

```text
event-<providerEventId>-statistics.json
event-<providerEventId>-incidents.json
event-<providerEventId>-lineups.json
```

Le nom sert uniquement au routage et au contrôle d'ensemble. L'identité persistée vient toujours
du plan serveur, jamais d'une confiance accordée au nom reçu. Un nom local n'est ni journalisé, ni
persisté, ni reflété dans une erreur.

### 5.3 Quantité et tailles

```text
MIN_EVENTS=1
MAX_EVENTS=25
EVIDENCE_SLOTS_PER_EVENT=3
MIN_FILES=0
MAX_FILES=75
DECLARATIONS_404=0..75
FILES_PLUS_DECLARATIONS=3N
MAX_BYTES_PER_FILE=5*1024*1024
MAX_BYTES_PER_BATCH=25*1024*1024
```

La limite du lot est la somme des tailles binaires de toutes les preuves JSON, y compris les petites
enveloppes canoniques des déclarations. Elle est vérifiée
avec une accumulation `long` protégée contre le dépassement. Un fichier vide est refusé. Les
limites Spring de transport sont légèrement supérieures : 6 MB par fichier et 32 MB par requête.
`spring.servlet.multipart.file-size-threshold=32MB` garde chaque partie en mémoire dans cette
requête déjà bornée et interdit un dépôt temporaire préalable au scanner. Les limites métier
ci-dessus restent impératives et sont testées aux bornes exactes. La borne Tomcat vaut
82 parties : 75 preuves pour 25 matchs, les six champs de contrôle et au plus une sentinelle vide.

## 6. Prévalidation intégrale

La prévalidation se termine avant le claim du plan et avant toute écriture. Elle applique, dans
l'ordre logique suivant, des contrôles fermés sur l'ensemble du lot :

1. présence d'un plan en attente, `requestId` correspondant et non-expiration ;
2. politique de configuration strictement hors ligne ;
3. énumération exhaustive des parties, six contrôles uniques et absence de partie inconnue ;
4. nombre de preuves et taille totale ;
5. présence d'une preuve par famille et taille de chaque fichier éventuel ;
6. `Content-Disposition` brut canonique, nom strict, absence de doublon ou chevauchement et égalité
   de l'union fichiers/déclarations avec l'ensemble attendu ;
7. capture bornée des octets par `RawPayloadEvidence` et détection de contenu sensible ;
8. parsing JSON strict, détection des clés dupliquées et refus de jetons après la racine ;
9. pour chaque preuve, parsing J5 courant ou reconnaissance de l'enveloppe 404 fermée ;
10. relecture de chaque observation canonique courante ;
11. égalité de `providerEventId`, `canonicalObservationId`, `startsAt`,
    `canonicalNormalizedSha256`, fenêtre UTC et fichiers attendus avec le plan.

La prévalidation ne crée pas de snapshot, occurrence, observation normalisée, fichier temporaire ou
entrée de cache. Elle n'invoque aucun transport. Une erreur de fichier ou `PLAN_CHANGED` avant le
claim laisse le plan `AWAITING_CONFIRMATION`, ce qui permet une nouvelle soumission lorsque la cause
est corrigeable, ou une annulation explicite. Une dérive canonique durable exige en pratique
l'annulation puis la préparation d'un nouveau plan.

Le token de formulaire est consommé par la frontière Web pour protéger chaque POST parvenu au
contrôleur. Sa consommation n'est pas le claim du plan : après une erreur de prévalidation métier,
un nouveau token Web est requis, mais le plan non expiré peut rester utilisable. Un rejet préalable
du multipart resolver Spring peut intervenir avant cette consommation ; le gestionnaire borné ne
revendique alors aucune rotation du token.

## 7. Enveloppe d'indisponibilité 404

Une famille explicitement indisponible peut être représentée par un fichier contenant uniquement
une racine fermée :

```json
{
  "error": {
    "code": 404,
    "message": "texte optionnel borné",
    "reason": "texte optionnel borné"
  }
}
```

Règles cumulatives :

- la racine est un objet contenant exactement la propriété `error` ;
- `error` est un objet dont les seules propriétés possibles sont `code`, `message`, `reason` ;
- `code` est obligatoire, entier JSON et exactement `404` ;
- `message` et `reason` sont facultatifs ou `null`, sinon chaînes non blanches d'au plus 200
  caractères sans caractère de contrôle ;
- toute autre racine, propriété supplémentaire, valeur `403`, chaîne `"404"`, nombre décimal ou
  objet ambigu est refusé ;
- le statut HTTP local est inféré à 404 ; l'opérateur ne le saisit jamais.

Si le navigateur ne permet pas de télécharger le corps du 404 observé, l'opérateur peut cocher la
case 404 de cette famille à la place du fichier. Le serveur produit alors exactement :

```json
{"error":{"code":404,"message":"LOCAL_OPERATOR_DECLARED_HTTP_404"}}
```

Cette enveloppe documente une déclaration opérateur locale. Elle n'est pas présentée comme le corps
fournisseur. La case ne peut être déduite de l'état du match, n'accepte aucun code libre et ne
déclenche aucun transport. Le fichier et la case pour un même nom, ainsi que l'absence des deux,
sont refusés avant claim.

Tout autre JSON doit être accepté par le parseur courant de sa famille. Le statut local inféré est
alors 200.

## 8. Claim et revalidation

Le claim exige cumulativement :

- plan `AWAITING_CONFIRMATION` et non expiré ;
- `requestId` exact ;
- prévalidation complète réussie pour toutes les preuves ;
- seconde revalidation canonique réussie juste avant le claim ;
- `acknowledged=true` ;
- phrase exacte comparée en temps constant ;
- politique de configuration hors ligne toujours satisfaite.

La transition vers `EXECUTING` est synchronisée et n'est possible qu'une fois. Deux soumissions
concurrentes ne peuvent pas obtenir le même claim. Le service de contrôle est dédié au lot hors
ligne et ne lit ni n'écrit jamais l'état de `J5RealControlService`.

À l'entrée de la transaction, une ultime revalidation des observations canoniques et des flags
est réalisée. Une divergence `PLAN_CHANGED` après claim produit `FAILED_LOCKED` avec zéro écriture
grâce au rollback.

## 9. Transaction atomique `3N`

### 9.1 Ordre

Le traitement transactionnel suit le plan, sans dépendre de l'ordre multipart :

```text
providerEventId croissant
  1. EVENT_STATISTICS
  2. EVENT_INCIDENTS
  3. EVENT_LINEUPS
```

Pour chaque élément, le brut est persisté avant sa projection normalisée. Cette antériorité locale
n'autorise aucun commit intermédiaire : une seule transaction englobe tous les snapshots bruts,
classifications, occurrences de déduplication, observations J5 et lignes enfants du lot.

### 9.2 Métadonnées

Chaque réponse locale synthétique porte :

| Champ | Valeur |
|---|---|
| endpoint logique | `EVENT_STATISTICS`, `EVENT_INCIDENTS` ou `EVENT_LINEUPS` |
| clé | `<ENDPOINT>|eventId=<providerEventId>` |
| acquisition | `MANUAL_LOCAL_JSON_IMPORT` |
| requestedAt / receivedAt | instant d'import local cohérent |
| HTTP | 200 pour JSON parsé, 404 pour enveloppe fermée |
| content type | `application/json` |
| durée | zéro |
| payload | octets exacts du fichier ou de l'enveloppe locale canonique, et SHA-256 |
| parseur | version J5 courante de la famille |

Au moment de la rédaction, les versions courantes sont
`event-statistics-v2`, `event-incidents-v14` et `event-lineups-v2`. L'implémentation doit référencer
les constantes des parseurs courants plutôt que dupliquer ces chaînes dans le flux.

### 9.3 Atomicité et échec

Toute exception de persistance brute, classification, parsing défensif, normalisation, contrainte
ou base déclenche le rollback du lot entier. Le contrôle passe alors à `FAILED_LOCKED` et son
résultat porte uniquement un `terminalCode` d'erreur borné ; il ne peut exposer aucun identifiant
de snapshot ou d'observation prétendument committé. Il n'existe ni compensation, ni retry, ni
reprise partielle.

La prévalidation parse tout avant claim, mais le flux transactionnel peut reparser depuis les
octets capturés afin de maintenir la chaîne brut → parser → normalisé. Les deux lectures doivent
utiliser les mêmes parseurs et produire le même verdict ; toute incohérence échoue fermée.

## 10. Provenance, déduplication et historique

- la provenance est toujours `MANUAL_LOCAL_JSON_IMPORT` ;
- les clés de requête et familles restent celles de J5 ;
- les données brutes et normalisées restent séparées et reliées par snapshot, hash, parseur et
  heure de réception ;
- les règles V25 distinguent une acquisition directe d'un import local du même payload ;
- la réimportation d'un même lot nécessite un nouveau plan ;
- les snapshots et observations peuvent être dédupliqués selon les règles existantes, tandis que
  leurs occurrences J6 restent enregistrées par les mécanismes existants ;
- un snapshot brut retourné `INSERTED` ou `DEDUPLICATED` est toujours reclassifié vers le verdict
  final ; une ligne dédupliquée encore `RAW_ONLY` est ainsi finalisée dans la transaction du lot,
  tandis qu'une classification contradictoire refuse et annule le lot entier ;
- les vues J6 et enveloppes J7 consomment les données committées sans connaître un historique de
  lot supplémentaire ;
- aucun `batchId` persistant, table de statut ou migration n'est ajouté.

## 11. Politique locale indépendante du connecteur maître

La préparation, la prévalidation, le claim et l'exécution exigent les trois conditions actives
suivantes :

```text
sofascore.automatic-refresh-enabled=false
sofascore.live-polling-enabled=false
sofascore.store-raw-payloads=true
```

`sofascore.enabled=false|true` : l'état du connecteur n'entre pas dans la décision d'éligibilité du
lot. La configuration
combinée documentée, avec `sofascore.enabled=true`, les cinq opt-ins à `true`, l'origine exacte et
les six endpoints autorisés, doit laisser le lot hors ligne disponible même lorsque les politiques
manuelles fournisseur sont simultanément éligibles.

Le nouveau flux ne possède pas de flag d'activation réseau. Le rafraîchissement/polling automatique
ou l'absence de conservation brute refuse fermée l'opération.
Le contrôle est réévalué au moins à la préparation, avant le claim et à l'entrée de la transaction.
`ConnectorGate`, transports, cache fournisseur et coordinateur ne font pas partie du graphe de
dépendances du service.

## 12. Résultat et minimisation

Le dernier résultat peut rester en mémoire pour l'affichage local et contenir seulement :

- `requestId`, réussite et code terminal borné ;
- nombre de rencontres planifiées, imports JSON locaux et octets contrôlés ;
- pour chaque rencontre réussie, UUID canonique, `providerEventId` et noms d'équipes bornés copiés
  depuis le plan serveur ;
- pour chacune des trois familles réussies, endpoint logique, identifiant du snapshot, taille et
  SHA-256 du payload, identifiant et caractère inséré/dédupliqué de l'observation, complétude,
  score et nombre d'avertissements ;
- compteurs invariants affichés fournisseur, cache, coordinateur et retry, tous égaux à zéro.

Un résultat d'échec après claim conserve seulement les compteurs globaux avec zéro import committé
et aucun résultat par rencontre. Les identifiants et hashes par famille ne sont publiés qu'après le
commit atomique réussi ; ils constituent la preuve locale minimisée de persistance, pas un extrait
du contenu.

Il ne contient jamais le payload, un extrait JSON, un nom de fichier reçu, un chemin local, la
phrase de confirmation, le token, une URI, un en-tête ou un secret. Les logs appliquent la même
minimisation. La page Web applique les en-têtes `no-store` et `noindex`.

## 13. Erreurs fermées

Les codes détaillés restent bornés et non sensibles. La taxonomie minimale distingue :

```text
OFFLINE_POLICY_UNAVAILABLE
INVALID_SELECTION
EVENT_NOT_FOUND
ACTIVE_BATCH_EXISTS
NO_PENDING_BATCH
REQUEST_ID_MISMATCH
CONFIRMATION_EXPIRED
ACKNOWLEDGEMENT_REQUIRED
CONFIRMATION_TEXT_MISMATCH
PLAN_CHANGED
FILE_NAME_INVALID
DUPLICATE_FILE
FILE_SET_MISMATCH
EMPTY_PAYLOAD
PAYLOAD_TOO_LARGE
BATCH_TOO_LARGE
MULTIPART_LIMIT_EXCEEDED
SENSITIVE_CONTENT
STATISTICS_PAYLOAD_INCOMPATIBLE
INCIDENTS_PAYLOAD_INCOMPATIBLE
LINEUPS_PAYLOAD_INCOMPATIBLE
OPERATOR_STOP
STORAGE_UNAVAILABLE
LOCAL_EXECUTION_FAILURE
```

Une erreur inattendue devient un code générique. Elle ne reflète jamais l'entrée fautive.

## 14. Interdictions explicites

```text
REAL_PROVIDER_CALL=FORBIDDEN
HTTP_CLIENT_DEPENDENCY=FORBIDDEN
PROVIDER_CACHE=FORBIDDEN
NETWORK_COORDINATOR=FORBIDDEN
FREE_EVENT_ID=FORBIDDEN
ARCHIVE_OR_DIRECTORY_IMPORT=FORBIDDEN
TEMPORARY_STAGING=FORBIDDEN
WATCH_SERVICE=FORBIDDEN
SCHEDULER=FORBIDDEN
BACKGROUND_EXECUTION=FORBIDDEN
AUTOMATIC_RETRY=FORBIDDEN
PARTIAL_COMMIT=FORBIDDEN
DURABLE_CONTROL_HISTORY=FORBIDDEN
PROCESS_RESTART_RESUME=FORBIDDEN
DATABASE_MIGRATION=FORBIDDEN
```

Toute évolution souhaitant lever une de ces interdictions requiert un Work Order séparé. Toute
évolution réseau requiert en plus une revue explicite de l'ADR-SS-001.

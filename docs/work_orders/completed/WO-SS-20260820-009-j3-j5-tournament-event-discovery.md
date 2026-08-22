# WO-SS-20260820-009 — Découverte tournoi → rencontres et lancement direct J5

- **Statut :** `VALIDATED`
- **Date :** 2026-08-20
- **Date de démarrage :** 2026-08-20
- **Date de fin technique :** 2026-08-21
- **Date de validation et de clôture :** 2026-08-21
- **Qualification propriétaire :** `PASS`
- **Clôture :** `COMPLETED`
- **Prérequis :** J7 fusionné sur `main`
- **Base locale :** `0d9a31d967331441c78692c58c07ba54468eddd3`
- **Jalon :** évolution J3 → J5
- **Branche :** `codex/j3-j5-tournament-event-discovery`
- **Demande et autorisation propriétaire :** analyse, Work Order et implémentation complète demandés le 2026-08-20
- **Appel fournisseur pendant l'implémentation :** `NOT_AUTHORIZED`
- **Appel fournisseur pendant les tests automatisés :** `NOT_AUTHORIZED`
- **Polling, planification, live ou retry réseau :** `NOT_AUTHORIZED`
- **Transport `EVENT_DETAILS` implicite :** `NOT_AUTHORIZED`
- **Déploiement VPS / envoi au Betting Project :** `NOT_AUTHORIZED`
- **Modification de l'ADR-SS-001 :** `NOT_REQUIRED_AFTER_EXPLICIT_REVIEW`
- **Migration :** `V24_CACHE_SCOPE_AND_V25_LOCAL_IMPORT_PROVENANCE`

## 1. Objectif

Après une collecte J3 paginée complète — obtenue soit par la résolution directe existante, soit
par l'import local atomique des corps JSON page 1 à N — fournir un catalogue local des tournois réellement
présents dans les pages exactes de cette collecte et actionnables pour la date civile
`Europe/Paris` selon `timezoneEventCount`. L'opérateur sélectionne une occurrence au moyen du
libellé exact `tournament.name - tournament.category.name`, qui ajoute la portée géographique sans
changer l'identité postée. Le serveur résout alors `tournament.id` et
`tournament.uniqueTournament.id`, puis prépare une confirmation sans réseau. L'opérateur choisit
ensuite exactement une action : au plus une requête directe, ou l'import local du seul corps JSON
obtenu manuellement hors de l'application :

```text
GET /api/v1/unique-tournament/{uniqueTournament.id}/scheduled-events/{date}
```

Dans les deux cas, la réponse brute est conservée avant parsing. Les rencontres sont contrôlées par identité de
tournoi unique, filtrées par `tournament.id` et par date civile `Europe/Paris`, dédupliquées par
`event.id`, comparées au compteur de fuseau lorsqu'il est exploitable, puis persistées sous forme
d'observations canoniques atomiques.

Chaque rencontre validée fournit un lien direct vers sa page J5. Aucun appel J4 `EVENT_DETAILS` et
aucune saisie d'identifiant de rencontre ne sont nécessaires. J5 conserve sa propre confirmation
et son ordre fixe ; l'opérateur choisit soit ses trois appels directs, soit l'import atomique des
trois corps JSON statistiques, incidents et compositions, avec zéro appel fournisseur.

Les statuts du laboratoire restent :

```text
EXPERIMENTAL
LOCAL_ONLY
NOT_PRODUCTION_APPROVED
NO_CRITICAL_DEPENDENCY
```

## 2. Analyse structurelle faisant autorité

Le contrat est figé dans :

```text
docs/requirements/J3-J5-TOURNAMENT-EVENT-DISCOVERY-RULES.md
```

Décisions essentielles :

```text
DROPDOWN_LABEL=tournament.name + " - " + tournament.category.name
DROPDOWN_CATEGORY_ROLE=DISPLAY_METADATA_ONLY
DROPDOWN_REQUIRES_CATEGORY_NAME=YES
SELECTED_PHASE_ID=tournament.id
REQUEST_PATH_ID=tournament.uniqueTournament.id
UNIQUE_TOURNAMENT_NAME_IN_URI=NO
EVENT_IDENTITY=event.id
DATE_ZONE=Europe/Paris
DATE_INTERVAL=HALF_OPEN_LOCAL_DAY
DROPDOWN_OFFSET_SOURCE=timezoneEventCount.keys
DROPDOWN_OFFSET_POLICY=DERIVED_FROM_COLLECTION_DATE_AND_EUROPE_PARIS
DROPDOWN_REQUIRES_APPLICABLE_OFFSET_KEY=YES
PHASE_FILTER=tournament.id
NO_PARTIAL_CANONICAL_PUBLICATION=YES
J5_REQUIRES_EVENT_DETAILS=NO
DISCOVERY_EXECUTION_CHOICE=ONE_DIRECT_GET_OR_ONE_LOCAL_JSON_IMPORT
J3_EXECUTION_CHOICE=ONE_DIRECT_PAGINATION_OR_ONE_COMPLETE_LOCAL_JSON_BATCH
J3_LOCAL_IMPORT_PAGE_RANGE=1_TO_N_CONTIGUOUS_MAX_25
J3_LOCAL_IMPORT_MAX_MIB_PER_PAGE=5
J3_LOCAL_IMPORT_MAX_MIB_PER_BATCH=25
J3_LOCAL_IMPORT_PROVIDER_AND_CACHE_CALLS=0
J3_MINIMIZED_EVIDENCE_VERSION=5
LOCAL_JSON_IMPORT_PROVIDER_CALLS=0
LOCAL_JSON_IMPORT_ACQUISITION_MODE=MANUAL_LOCAL_JSON_IMPORT
LOCAL_JSON_IMPORT_BROWSER_HEADERS=FORBIDDEN
J5_EXECUTION_CHOICE=THREE_DIRECT_GETS_OR_THREE_LOCAL_JSON_BODIES
J5_LOCAL_IMPORT_PROVIDER_CALLS=0
J5_LOCAL_IMPORT_PREVALIDATION=ALL_THREE_BEFORE_CONFIRMATION_CLAIM
J5_LOCAL_IMPORT_EXPLICIT_UNAVAILABLE_ENVELOPE=ERROR_CODE_INTEGER_404_ONLY
```

## 3. Revue de l'ADR-SS-001

L'évolution ajoute un chemin physique sous un nouvel endpoint logique
`TOURNAMENT_SCHEDULED_EVENTS`, mais ne change pas la décision d'architecture :

- origine exacte `https://www.sofascore.com` ;
- requête de domaine fermée, sans URI ou segment utilisateur libre ;
- opt-in séparé et désactivé par défaut ;
- catalogue général `manualOnly=true`, `callable=false`, sans URI configurée ;
- préparation et sélection sans réseau ;
- phrase exacte, acquittement et expiration ;
- une action exclusive après la confirmation J3 : pagination directe ou lot JSON local complet ;
- une action exclusive après la confirmation de découverte : un GET au maximum ou un import JSON local ;
- cache obligatoire avant transport ;
- coordinateur partagé, concurrence maximale de un et délai minimal de trois secondes ;
- aucun proxy, redirection, cookie, jeton, compte, session ou simulation de navigateur ;
- import local alternatif limité au seul corps JSON, sans HAR, header, cookie ou donnée de session ;
- payload borné et brut persisté avant parsing ;
- aucun retry, polling, live ou planification ;
- arrêt terminal au premier incident ;
- aucune dépendance du Betting Project principal.

La séparation brut/normalisé et la provenance obligatoire restent celles de l'ADR. Aucun nouvel
ADR n'est requis, car il n'y a ni déploiement externe, ni concurrence accrue, ni automatisation,
ni transport sortant, ni donnée brute envoyée hors du poste.

```text
ADR_SS_001_WO_009_REVIEW=COMPATIBLE_NO_CHANGE_REQUIRED
ADR_SS_001_MODIFICATION_REQUIRED=NO
PROVIDER_CALL_AUTHORIZED_BY_THIS_REVIEW=NO
```

## 4. Périmètre inclus

### 4.1 Catalogue J3

- lecture de la dernière preuve terminale `COMPLETED` du processus courant ;
- relecture des `snapshotId` exacts des pages résolues, cache et import local inclus ;
- import J3 alternatif des fichiers contigus `page-1.json` à `page-N.json` après une nouvelle
  confirmation explicite, sans transport ni cache fournisseur ;
- prévalidation atomique du lot : 1 à 25 pages, 5 Mio par page, 25 Mio au total, scan sensible,
  forme `scheduled`, parser courant et terminaison `hasNextPage=false` ;
- garde de concurrence J3 partagée, provenance `MANUAL_LOCAL_JSON_IMPORT` et preuve v5 avec
  compteurs d'import distincts ;
- aucun basculement automatique d'un terminal direct `HTTP_FORBIDDEN` vers l'import ;
- contrôle endpoint, clé date/page, statut, taille, SHA-256, parseur et forme de payload ;
- exigence d'une séquence 1..N contiguë terminée par `hasNextPage=false` ;
- agrégation de toutes les occurrences `scheduled` ;
- déduplication par `tournament.id` et refus des collisions contradictoires ;
- option actionnable uniquement si `uniqueTournament.id/name` sont présents ;
- conservation de `tournament.category.name`, libellé exact
  `tournament.name - tournament.category.name` et
  exclusion locale sans valeur géographique exploitable ;
- contradiction de catégorie pour un même `tournament.id` considérée comme conflit de catalogue ;
- option actionnable uniquement si `timezoneEventCount` contient au moins un offset réellement
  applicable pendant la date collectée dans `Europe/Paris` ; `7200` n'est pas codé en dur pour
  l'hiver et l'une des deux clés suffit le jour d'une bascule DST ;
- valeur HTML limitée à `tournament.id`, puis résolution intégrale côté serveur ;
- indisponibilité explicite après redémarrage ou dernière collecte non complète.

### 4.2 Contrôle opérateur

- machine en mémoire `LOCKED` → `AWAITING_CONFIRMATION` → `EXECUTING` → terminal ;
- nouvelle préparation possible après succès uniquement, avec nouvelle identité et nouvelle phrase ;
- erreur, expiration ou arrêt verrouillés jusqu'au redémarrage ;
- durée de confirmation de cinq minutes ;
- acquittement et comparaison en temps constant ;
- revalidation de la sélection contre le catalogue courant lors de la préparation ;
- aucun transport au chargement, au changement du `<select>` ou à la préparation.

### 4.3 Transport, cache et preuve brute

- endpoint logique `TOURNAMENT_SCHEDULED_EVENTS` ajouté au type et au catalogue sans devenir
  appelable globalement ;
- opt-in `SOFASCORE_TOURNAMENT_EVENT_DISCOVERY_ENABLED=false` par défaut ;
- requête et transport exact-host dédiés ;
- clé `(date, uniqueTournamentId)` et TTL de dix minutes ;
- migration append-only V24 étendant uniquement la portée de `provider_response_cache` ;
- cache hit sans transport ni nouvelle persistance ;
- requête fournisseur sérialisée par `ManualProviderRequestCoordinator` ;
- conservation brute et occurrence J6 avant parsing ;
- classification explicite des HTTP, contenus inattendus et ruptures de contrat ;
- import du second endpoint borné à 5 Mio, scanné avant consommation de la confirmation, sans transport et sans
  lecture ni écriture du cache fournisseur ;
- provenance brute distincte `MANUAL_LOCAL_JSON_IMPORT`, migration append-only V25 et
  déduplication séparée de `DIRECT_LOCAL_ENDPOINT` ;
- parseur, projection, contrôle de compte et transaction canonique strictement partagés entre les
  deux voies.

### 4.4 Parsing et projection

- parseur `tournament-scheduled-v1` à racine `events` ;
- `hasNextPage` absent ou `false` accepté, `true` refusé ;
- champs structurants fermés pour identifiants, timestamp, équipes, statut, tournoi et tournoi
  unique ;
- champs auxiliaires conservés uniquement dans le brut ;
- tous les événements doivent porter le `uniqueTournament.id` demandé ;
- sélection par `tournament.id` ;
- fenêtre de date `[début de D, début de D+1[` dans `Europe/Paris` ;
- déduplication exacte par `event.id`, conflit refusé ;
- contrôle `timezoneEventCount` lorsque l'offset est non ambigu ;
- `COUNT_MISMATCH` bloquant toute normalisation ; compte absent ou bascule DST signalé comme non
  vérifiable sans inventer une valeur.

### 4.5 Canonicalisation et J5

- transaction couvrant toutes les observations canoniques retenues ;
- UUID déterministe par `event.id` ;
- provenance snapshot/hash/parseur/heure de réception ;
- insertions et déduplications comptées ;
- résultat minimisé sans URI, headers ou payload ;
- liste de rencontres avec liens directs vers
  `/events/{canonicalEventId}/statistics?zone=Europe%2FParis` ;
- libellés J5 ne présentant plus le détail J4 comme un prérequis ;
- ordre J5 inchangé `STATISTICS → INCIDENTS → LINEUPS` ;
- choix exclusif après préparation : campagne directe ou import local des trois corps JSON ;
- trois fichiers de 5 Mio maximum, scannés puis prévalidés ensemble avant consommation de la
  confirmation ; aucun snapshot partiel lorsqu'un corps est incompatible ;
- enveloppe d'indisponibilité locale fermée à `error.code=404`, tout `403` ou autre forme refusé ;
- snapshots importés sous `MANUAL_LOCAL_JSON_IMPORT`, même parsing/persistance J5, compteurs
  distincts `providerCallAttempts=0` et `localJsonImports=3` ;
- aucun cache, coordinateur ou transport fournisseur dans cette voie ;
- aucun basculement d'une campagne directe terminale vers l'import sans redémarrage et nouvelle
  préparation.

### 4.6 Documentation et validation

- document d'architecture dédié ;
- README, changelog et runbook actualisés ;
- rapport de readiness sans appel fournisseur ;
- tests domaine, parser, transport, cache, contrôle, orchestration, MVC et PostgreSQL ;
- suites standard et intégration complètes.

## 5. Hors périmètre

- appel automatique lors du changement de liste ;
- lancement automatique ou par lot de J5 ;
- collecte de tous les tournois en cascade ;
- appel, préparation ou enrichissement `EVENT_DETAILS` ;
- stade, saison, tour, score ou autre champ non requis pour l'identité canonique ;
- modification de l'ordre J5 `STATISTICS → INCIDENTS → LINEUPS` ;
- sport autre que football ;
- origine, hôte, URI, query ou segment libre ;
- pagination inventée pour le second endpoint ;
- retry, polling, live, ordonnanceur ou tâche de fond ;
- réécriture d'une migration V1–V23 ou d'une observation existante ;
- persistance durable d'un manifeste de collecte J3 au-delà du processus courant ;
- payload réel ajouté à Git ;
- capture HAR, en-têtes HTTP, cookies, jetons, empreinte navigateur, User-Agent imitant Chrome ou
  reprise de session dans l'import local ;
- appel réel pendant l'implémentation ou les tests ;
- transfert vers le VPS, le cloud ou le Betting Project.

### 5.1 Note postérieure de portée — 2026-08-22

L'exclusion « lancement automatique ou par lot de J5 » ci-dessus appartient au périmètre historique
de WO-009. Elle signifie que ce Work Order n'autorisait que la campagne J5 unitaire associée à une
rencontre, avec son choix exclusif entre acquisition directe et import local de trois corps.

WO-010 autorise séparément un lot J5 multi-match strictement manuel, local et atomique, avec un
contrôle mémoire dédié et tous les flags réseau désarmés. Cette autorisation postérieure ne rouvre
pas WO-009, ne modifie aucun de ses statuts, comptes ou résultats de qualification et ne transforme
pas un échec unitaire en fallback ou retry. Le lancement automatique, le scheduler, le watcher, le
polling et l'acquisition automatique des fichiers demeurent interdits.

## 6. Contrats de sécurité et de cohérence

### 6.1 Configuration

Le nouvel opt-in implique J3 et exige l'union exacte des endpoints actifs. Une session combinée
peut inclure :

```text
SCHEDULED_EVENTS
TOURNAMENT_SCHEDULED_EVENTS
EVENT_DETAILS                # J4 phase 2 seulement, facultatif
EVENT_STATISTICS             # J5, facultatif
EVENT_INCIDENTS
EVENT_LINEUPS
```

Les valeurs par défaut restent toutes à `false` et `SOFASCORE_ALLOWED_ENDPOINTS` reste vide.

### 6.2 Identité et date

- l'ID de route est un `long > 0` résolu côté serveur ;
- le navigateur poste seulement le `tournament.id` de l'option ;
- date, tournoi unique, noms, catégorie, compteurs et sources sont repris du catalogue courant ;
- toute dérive du catalogue entre préparation et exécution invalide le claim ;
- tous les timestamps sont des secondes Unix ;
- aucune dépendance au fuseau système.

### 6.3 Atomicité

- snapshot brut conservé avant parsing ;
- cache fournisseur enregistré uniquement après parsing structurel d'une réponse directe ; un
  import local ne lit ni n'écrit ce cache ;
- validation complète avant la première observation canonique ;
- transaction unique pour la liste retenue ;
- zéro observation en cas de contradiction, rupture de schéma ou compte différent ;
- snapshots et observations append-only/dédupliqués selon les contraintes existantes.

## 7. Critères d'acceptation

```text
STRUCTURAL_ANALYSIS_VERSIONED=YES
TOURNAMENT_DROPDOWN_SOURCE=COMPLETED_J3_COLLECTION
TOURNAMENT_DROPDOWN_ALL_COMPLETED_PAGES=YES
J3_COLLECTION_SOURCE=DIRECT_PAGINATION_OR_COMPLETE_LOCAL_JSON_BATCH
J3_LOCAL_IMPORT_CONTIGUOUS_PAGE_FILES=REQUIRED
J3_LOCAL_IMPORT_BATCH_PREVALIDATED_BEFORE_CLAIM=YES
J3_LOCAL_IMPORT_PROVIDER_CALLS=0
J3_LOCAL_IMPORT_CACHE_READ_WRITE=0
J3_LOCAL_IMPORT_MAX_PAGES=25
J3_LOCAL_IMPORT_MAX_MIB_PER_PAGE=5
J3_LOCAL_IMPORT_MAX_MIB_PER_BATCH=25
J3_LOCAL_IMPORT_RESULT_COUNTER=LOCAL_JSON_IMPORT
J3_MINIMIZED_EVIDENCE_VERSION=5
TOURNAMENT_DROPDOWN_LABEL=TOURNAMENT_NAME_SPACE_HYPHEN_SPACE_CATEGORY_NAME
TOURNAMENT_DROPDOWN_CATEGORY_REQUIRED=YES
TOURNAMENT_DROPDOWN_CATEGORY_USED_AS_IDENTITY=NO
TOURNAMENT_SELECTION_SERVER_REVALIDATED=YES
UNIQUE_TOURNAMENT_URI_VALUE=NUMERIC_ID
UNIQUE_TOURNAMENT_NAME_USED_IN_URI=NO
TOURNAMENT_EVENTS_LOGICAL_ENDPOINT=TOURNAMENT_SCHEDULED_EVENTS
TOURNAMENT_EVENTS_OPT_IN_DEFAULT=FALSE
TOURNAMENT_EVENTS_PROVIDER_CALLS_PER_CONFIRMATION=0_OR_1_EXCLUSIVE
TOURNAMENT_EVENTS_AUTOMATIC_CALLS=0
TOURNAMENT_EVENTS_RETRY=0
TOURNAMENT_EVENTS_CACHE_TTL_MINUTES=10
START_TIMESTAMP_DATE_CHECK=EUROPE_PARIS_HALF_OPEN_INTERVAL
TOURNAMENT_ID_FILTER=ENFORCED
UNIQUE_TOURNAMENT_ID_MATCH=ENFORCED
EXPECTED_COUNT_MISMATCH=NORMALIZATION_REFUSED
RAW_BEFORE_PARSE=YES
LOCAL_JSON_IMPORT_RESPONSE_BODY_ONLY=YES
LOCAL_JSON_IMPORT_MAX_MIB=5
LOCAL_JSON_IMPORT_SENSITIVE_SCAN=BEFORE_CONFIRMATION_CLAIM
LOCAL_JSON_IMPORT_CACHE_READ_WRITE=0
LOCAL_JSON_IMPORT_ACQUISITION_MODE=MANUAL_LOCAL_JSON_IMPORT
DIRECT_AND_IMPORTED_RAW_DEDUPLICATION=SEPARATE
RAW_AND_NORMALIZED_DATA_SEPARATED=YES
CANONICAL_EVENT_PROVENANCE=SNAPSHOT_HASH_PARSER_RECEIVED_AT
CANONICAL_EVENT_IDENTITY=DETERMINISTIC_PROVIDER_EVENT_ID
J5_LAUNCH_LINKS=AVAILABLE
J5_REQUIRES_EVENT_DETAILS=NO
J5_MANUAL_EVENT_ID_ENTRY=NO
J5_ORDERED_ENDPOINTS=EVENT_STATISTICS_EVENT_INCIDENTS_EVENT_LINEUPS
J5_EXISTING_CONFIRMATION_RETAINED=YES
J5_EXECUTION_SOURCE=DIRECT_CAMPAIGN_OR_COMPLETE_LOCAL_JSON_BATCH
J5_LOCAL_IMPORT_REQUIRED_FILES=STATISTICS_INCIDENTS_LINEUPS
J5_LOCAL_IMPORT_PREVALIDATED_BEFORE_CLAIM=YES
J5_LOCAL_IMPORT_PROVIDER_CALLS=0
J5_LOCAL_IMPORT_COUNTER=3
J5_LOCAL_IMPORT_EXPLICIT_404_ENVELOPE=SUPPORTED
J5_LOCAL_IMPORT_403_ENVELOPE=REJECTED
J5_DIRECT_FAILURE_AUTOMATIC_LOCAL_FALLBACK=NO
SERVER_ADDRESS=127.0.0.1
DEFAULT_NETWORK_FLAGS=FALSE
CONNECTOR_GATE=UNCHANGED_BLOCKING
GENERAL_CATALOG_CALLABLE=FALSE
MAVEN_PROVIDER_CALLS=0
POLLING_SCHEDULING_RETRY=0
RAW_PROVIDER_PAYLOADS_IN_GIT=0
```

## 8. Matrice de validation

| Scénario | Résultat attendu |
|---|---|
| J3 incomplet, échoué ou absent | aucune option appelable |
| J3 complet multi-pages | toutes les occurrences des snapshots exacts sont agrégées |
| J3 importé avec lot 1..N valide | `COMPLETED`, zéro transport/cache, snapshots `MANUAL_LOCAL_JSON_IMPORT`, preuve v5 et catalogue identique |
| lot J3 incomplet, troué, sensible, hors borne ou non terminal | refus avant claim, zéro snapshot, intention encore confirmée |
| J3 direct terminal sur 403 puis import sans nouvelle intention | refus ; aucun fallback/retry implicite |
| snapshot ou hash J3 divergent | catalogue indisponible |
| `uniqueTournament` absent | occurrence exclue du choix appelable |
| `tournament.category.name` absent | occurrence exclue et résolution serveur vide, sans libellé inventé |
| tournoi et catégorie présents | libellé exact `tournament.name - tournament.category.name` |
| même `tournament.id`, catégories contradictoires | catalogue entier refusé |
| 2026-08-20, clé `7200` absente | occurrence exclue et résolution serveur vide |
| date d'hiver, clé `3600` présente même avec valeur zéro | occurrence actionnable |
| journée de bascule DST Paris | clé `3600` ou `7200` suffisante pour l'option ; compte ultérieur non vérifiable |
| `timezoneEventCount` vide | occurrence exclue du choix appelable |
| même `tournament.id` contradictoire | catalogue entier refusé |
| choix falsifié côté client | refus avant transport |
| préparation | zéro transport |
| phrase incorrecte, expirée ou non acquittée | zéro transport |
| ID unique 7 | chemin exact `/unique-tournament/7/...` |
| origine décorée | construction refusée |
| cache frais | parsing et projection sans transport |
| confirmation valide sans cache | exactement un GET |
| confirmation valide avec import JSON | zéro transport, zéro cache fournisseur et même normalisation |
| fichier vide, > 5 Mio ou contenant `Cookie:` | refus avant consommation de la confirmation |
| même brut acquis directement puis importé | deux snapshots de provenance distincte |
| même brut importé deux fois sous la même clé | snapshot importé dédupliqué, occurrences conservées |
| HTTP 403/429/5xx ou timeout | snapshot si disponible, verrou, zéro retry |
| HTML, payload trop grand ou JSON incompatible | aucune observation |
| `uniqueTournament.id` différent | réponse incompatible |
| autre `tournament.id` du même tournoi unique | événement exclu, pas normalisé |
| événement hors date Europe/Paris | événement exclu |
| doublon identique | une occurrence, avertissement |
| doublon conflictuel | aucune observation |
| compteur présent différent | `COUNT_MISMATCH`, zéro observation |
| compteur absent ou bascule DST | état non vérifiable explicite |
| réponse vide et compte zéro | succès vide sans événement inventé |
| événement valide sans détail J4 | observation canonique et lien J5 |
| J5 depuis le lien | ID dérivé de l'UUID, aucune saisie libre |
| J5 local, trois corps valides | zéro appel, trois snapshots importés, trois observations ordonnées et `COMPLETED_LOCKED` |
| J5 local, troisième corps incompatible | refus avant claim, zéro snapshot et confirmation encore en attente |
| J5 local, enveloppe fermée `error.code=404` | observation `UNAVAILABLE · N/A`, puis poursuite des deux autres familles |
| J5 local, enveloppe `error.code=403` | refus atomique, jamais reclassé comme indisponibilité |
| J5 direct terminal sur 403 puis import sans redémarrage | refus ; aucun fallback/retry implicite |
| session combinée | coordinateur partagé et concurrence maximale de un |

## 9. Validation obligatoire

Commandes finales :

```text
mvnw.cmd clean verify
mvnw.cmd -Pintegration-tests verify
```

Contrôles :

1. tests ciblés du nouveau domaine, parser, transport, cache, contrôle, service et MVC ;
2. installation Flyway V1→V25, upgrade V23→V24 et upgrade V24→V25 ;
3. audit du diff et des secrets ;
4. contrôle `server.address=127.0.0.1` ;
5. contrôle des opt-ins et endpoints autorisés par défaut ;
6. contrôle du profil Maven live toujours bloqué ;
7. zéro appel fournisseur pendant toutes les suites ;
8. aucun payload réel ou artefact runtime versionné.

Les validations automatisées restent des preuves techniques et ne qualifient pas, à elles seules,
le Work Order. La décision explicite du propriétaire du 2026-08-21 complète ces preuves et fixe
l'état de clôture suivant :

```text
IMPLEMENTATION=COMPLETED
TECHNICAL_READINESS=AUTOMATED_V25_J3_AND_J5_LOCAL_IMPORT_VALIDATION_PASS
HUMAN_PROVIDER_QUALIFICATION=PASS_BY_OWNER_DECISION
WORK_ORDER_STATUS=VALIDATED
WORK_ORDER_LOCATION=docs/work_orders/completed
```

Le déplacement vers `completed`, la qualification et la publication GitHub ont été autorisés
explicitement par le propriétaire après ses vérifications fonctionnelles.

## 10. État initial vérifié

```text
BASE_COMMIT=0d9a31d967331441c78692c58c07ba54468eddd3
INITIAL_BRANCH=main
WORK_BRANCH=codex/j3-j5-tournament-event-discovery
INITIAL_WORKTREE=CLEAN
INITIAL_PROVIDER_CALLS=0
INITIAL_SERVER_BINDING=127.0.0.1
ADR_REVIEW=PASS_COMPATIBLE_NO_CHANGE_REQUIRED
```

## 11. Journal de réalisation

Réouverture du 2026-08-20, sans qualification du Work Order : après des réponses `HTTP_403` sur la
voie directe et un `TRANSPORT_IO_FAILURE` corrélé à un essai sous ProtonVPN, le propriétaire a
demandé une solution lui permettant de poursuivre ses vérifications. La reprise de cookies,
l'imitation de Chrome, un proxy ou un changement automatique d'adresse restent interdits. La
solution retenue ajoute donc une action locale alternative après la confirmation existante :
import du seul corps JSON, zéro transport, scanner sensible, limite 5 Mio, provenance
`MANUAL_LOCAL_JSON_IMPORT`, aucun checkpoint de cache fournisseur et réemploi intégral du pipeline
de validation/canonicalisation. La migration append-only V25 et la revalidation automatisée sont
incluses ; l'état demeure `IN_PROGRESS` jusqu'à décision explicite du propriétaire.

Extension corrective du 2026-08-21, toujours sans qualification du Work Order : la recette a
montré que le contournement local du second endpoint ne suffisait pas lorsque la collecte J3 elle-même
s'arrêtait avant la page 1 sur `HTTP_FORBIDDEN`, puisqu'aucune preuve `COMPLETED` ne pouvait alors
alimenter le catalogue. La même provenance V25 est donc étendue à une action J3 alternative : lot
complet `page-1.json` à `page-N.json`, prévalidé atomiquement, zéro transport/cache, garde et verrou
J3 partagés, preuve minimisée v5 et reconstruction du catalogue avec contrôle du mode d'acquisition.
Les compteurs d'exécution distinguent explicitement `localJsonImports` des `providerRequests` et
`cacheHits`; aucun import n'est présenté comme un appel réussi. Un 403 direct demeure terminal et
la nouvelle voie requiert une nouvelle intention opérateur. Aucune migration supplémentaire n'est
nécessaire et aucun appel fournisseur n'a été exécuté pendant ce correctif.

Extension corrective J5 du 2026-08-21, toujours sans qualification du Work Order : les captures
opérateur ont confirmé le succès des imports J3 et découverte tournoi, mais la page J5 ne proposait
encore que la campagne directe, terminée sur `HTTP_403`. La voie locale couvre désormais les trois
familles ordonnées. Les trois fichiers sont scannés et prévalidés atomiquement avant le claim ; un
lot valide produit trois snapshots `MANUAL_LOCAL_JSON_IMPORT`, trois observations et les compteurs
`providerCallAttempts=0` / `localJsonImports=3`. Une enveloppe JSON fermée portant l'entier 404 est
normalisée comme indisponibilité ; un 403 reste incompatible. Aucun échec direct ne bascule vers
l'import dans la même campagne : le verrou terminal exige un redémarrage et une nouvelle
préparation. Le même correctif contraint la largeur des cartes et champs fichier J3/J5 pour éviter
le débordement visuel observé. Aucune migration supplémentaire et aucun appel fournisseur ne sont
introduits.

Validation automatisée de la réouverture V25 :

- `Verify-Local.ps1` : préflight et scan de sources `PASS`, suite standard `529` tests,
  `0` échec, `0` erreur, `2` exclus et `SOFASCORE_NETWORK_CALLS_EXECUTED=NO` ;
- `mvnw.cmd clean verify` : `PASS`, `529` tests, `0` échec, `0` erreur, `2` exclus ;
- `mvnw.cmd -Pintegration-tests verify` : `PASS`, suite standard inchangée puis `49` tests
  d'intégration, `0` échec, `0` erreur, `0` exclu ;
- Flyway V1→V25 et upgrade V24→V25 validés sur PostgreSQL Testcontainers ; un même contenu direct
  et importé conserve deux snapshots de provenance distincte, avec déduplication dans chaque mode ;
- aucun appel fournisseur exécuté. Ces résultats ne qualifient pas le Work Order.

Validation automatisée de l'extension corrective J3 du 2026-08-21 :

- suite ciblée import J3, modèle de résultat, contrôleur MVC, catalogue, dashboard et voie directe :
  `PASS`, `43` tests, `0` échec, `0` erreur ;
- `Verify-Local.ps1` : préflight et scan de sources `PASS`, suite standard `538` tests,
  `0` échec, `0` erreur, `2` exclus et `SOFASCORE_NETWORK_CALLS_EXECUTED=NO` ;
- `mvnw.cmd -Pintegration-tests verify` : `PASS`, suite standard `538` tests puis `49` tests
  d'intégration, `0` échec, `0` erreur, `0` exclu ;
- import J3 validé avec zéro transport et zéro lecture/écriture du cache fournisseur, provenance
  `MANUAL_LOCAL_JSON_IMPORT`, preuve minimisée v5 et contrôle source/mode lors de la reconstruction
  du catalogue ;
- aucun appel fournisseur exécuté. Ces résultats automatisés ne qualifient pas le Work Order.

Validation fonctionnelle corrective du propriétaire du 2026-08-21, consignée avant sa décision
finale de qualification :

- rendu J3 `PASS` : les options de collecte directe et d'import paginé restent intégralement
  contenues dans leur colonne ; le champ fichier, les instructions et les boutons ne débordent plus
  du premier bloc d'import ;
- campagne J5 locale `PASS` : l'option présente les trois URI et les trois champs distincts, puis
  termine en `COMPLETED_LOCKED` avec `0` appel fournisseur et `3` imports JSON locaux ;
- les snapshots `412`, `413` et `414` sont reliés respectivement aux observations `219`, `220` et
  `221`, dans l'ordre `EVENT_STATISTICS → EVENT_INCIDENTS → EVENT_LINEUPS` ;
- la statistique 404 est conservée comme `UNAVAILABLE · N/A`, les incidents vides comme
  `EMPTY_VALID · 100%` et les compositions comme `PARTIAL · 97%` avec `48/49` signaux ; les deux
  formations `4-2-3-1` sont rendues localement ;
- à cet instant, cette vérification clôtait uniquement les deux corrections signalées et les autres
  vérifications du propriétaire restaient ouvertes. La décision finale de qualification est
  consignée ci-dessous.

Validation automatisée de l'extension corrective J5 et du rendu du 2026-08-21 :

- suite ciblée service d'import J5, voie directe et contrôleur MVC : `PASS`, `28` tests,
  `0` échec et `0` erreur ;
- les trois corps valides terminent la campagne avec `providerCallAttempts=0`,
  `localJsonImports=3`, trois snapshots `MANUAL_LOCAL_JSON_IMPORT` et trois observations dans
  l'ordre `STATISTICS → INCIDENTS → LINEUPS` ;
- un troisième corps invalide et une enveloppe 403 sont refusés avant claim et avant toute
  persistance ; seule une enveloppe fermée portant exactement le code entier 404 produit une
  indisponibilité explicite et permet de poursuivre la famille suivante ;
- le contrôleur rend les trois URI exactes, trois champs fichier, la confirmation protégée et le
  bouton d'import zéro appel ; aucun échec direct ne déclenche de fallback automatique ;
- le CSS contraint désormais les cartes, formulaires, champs fichier et URI longues à leur colonne,
  ce qui corrige le débordement signalé dans la première option d'import J3 ;
- `Verify-Local.ps1` : préflight et scan de sources `PASS`, suite standard `544` tests,
  `0` échec, `0` erreur, `2` exclus et `SOFASCORE_NETWORK_CALLS_EXECUTED=NO` ;
- `mvnw.cmd clean verify` sur l'état final : `PASS`, `544` tests, `0` échec, `0` erreur,
  `2` exclus ;
- `mvnw.cmd -Pintegration-tests verify` sur l'état final : `PASS`, suite standard `544` tests puis
  `49` tests d'intégration, `0` échec, `0` erreur et `0` exclu ; Flyway applique les `25`
  migrations sous PostgreSQL Testcontainers ;
- aucun appel fournisseur exécuté. Ces résultats automatisés ne qualifient pas le Work Order.

Historique de la première version avant la réouverture V25 :

- analyse structurelle versionnée avant l'ajout du transport, avec correction de l'ambiguïté du
  document source : le segment `/unique-tournament/.../` reçoit l'identifiant numérique
  `tournament.uniqueTournament.id`, jamais son nom ni un slug ;
- conservation distincte des identités de phase (`tournament.id/name`) et de famille
  (`uniqueTournament.id/name`), résolution serveur de la sélection et filtrage des événements sur
  la phase choisie ;
- catalogue J3 fondé sur la dernière preuve `COMPLETED` du processus, ses pages exactes et leurs
  snapshots intègres ;
- amélioration issue des vérifications manuelles supplémentaires : filtrage local du catalogue
  par présence d'une clé `timezoneEventCount` correspondant à l'offset de la date J3 dans
  `Europe/Paris`, avec dérivation été/hiver, traitement des deux offsets d'une journée DST et
  revalidation serveur des identités exclues ;
- amélioration 2 issue des vérifications opérateur : conservation de
  `scheduled[*].tournament.category.name`, libellé exact
  `tournament.name - tournament.category.name`, exclusion fail-closed quand la portée manque et
  refus des contradictions de catégorie pour un même `tournament.id` ; la valeur HTML postée reste
  exclusivement l'identifiant numérique de phase ;
- une première collecte opérateur a terminé sur `HTTP_FORBIDDEN` avec zéro retry, puis une seconde
  collecte a terminé correctement ; conformément à la décision du propriétaire, ce 403 n'appelle
  aucune modification de code et ces observations ne qualifient pas le Work Order ;
- contrôle opérateur, requête, transport exact-host, cache, parser strict, projection
  `Europe/Paris`, canonicalisation transactionnelle, contrôleur MVC et rendu du dashboard livrés ;
- migration append-only `V24` ajoutée pour la seule portée du cache du nouvel endpoint, sans
  modification des migrations V1 à V23 ;
- création des observations canoniques et liens directs J5 démontrée sans `EVENT_DETAILS`, sans
  campagne J4 et sans saisie manuelle d'identifiant ;
- revues croisées intégrées : arrêt concurrent protégé autour du commit, erreur de revalidation du
  catalogue terminale, classification brute compatible avec le store JDBC, libellés localisés
  traités comme descriptifs, garde-fous des scripts J3/J5/J6 actualisés ;
- suite ciblée de l'amélioration 2 : `PASS`, `50` tests, `0` échec, `0` erreur, avec couverture du
  libellé exact, de l'absence de catégorie, des contradictions et du rendu MVC ;
- `Verify-Local.ps1` : préflight et scan de sources `PASS`, suite standard `524` tests, `0` échec,
  `0` erreur, `2` exclus, et `SOFASCORE_NETWORK_CALLS_EXECUTED=NO` ;
- `mvnw.cmd clean verify` : `PASS`, `524`
  tests, `0` échec, `0` erreur, `2` exclus ;
- `mvnw.cmd -Pintegration-tests verify` :
  `PASS`, `48` tests d'intégration, `0` échec, `0` erreur, `0` exclu ;
- installations Flyway V1→V24 et mise à niveau V23→V24 validées, cache existant préservé ;
- audit final des secrets, payloads, valeurs par défaut, liaison `127.0.0.1` et diff : `PASS` ;
- correctif après recette locale de configuration : les tests `ApplicationContextRunner` fixent
  désormais explicitement les valeurs sûres de tous les placeholders SofaScore avant chaque
  scénario, afin qu'un `.env` local armé ne contamine jamais le build ;
- profil combiné J3 + découverte tournoi + J4 phase 2 + J5 vérifié avec l'union exacte des six
  endpoints actifs ; `TOURNAMENT_STANDINGS` et `TEAM_RECENT_EVENTS` restent refusés comme familles
  supplémentaires différées ;
- relance `clean verify` avec le `.env` local armé qui reproduisait l'incident : `PASS`, `517`
  tests, `0` échec, `0` erreur, `2` exclus et aucun appel fournisseur ;
- correction locale de l'allowlist armée en retirant les familles différées
  `TOURNAMENT_STANDINGS` et `TEAM_RECENT_EVENTS`, puis démarrage technique `PASS` sur
  `127.0.0.1:8087` avec Flyway V24 à jour ; arrêt gracieux, port libéré et zéro appel fournisseur ;
- anomalie révélée par la première collecte J3 opérateur terminale de six pages : le catalogue
  restait à `SNAPSHOT_METADATA_MISMATCH` parce que l'`Instant` nanoseconde de la preuve en mémoire
  était comparé exactement au `timestamptz` PostgreSQL restitué à la microseconde ;
- correction bornée de cette comparaison à un écart absolu strictement inférieur à une
  microseconde, sans assouplir l'identité, la clé date/page, le HTTP, le parseur, le statut, la taille
  ou le SHA-256 ; tests unitaires des deux côtés de la borne et round-trip PostgreSQL réel ajoutés ;
- aucun appel fournisseur exécuté pendant l'implémentation ou les tests automatisés.

## 12. Qualification et clôture propriétaire — 2026-08-21

Après les vérifications fonctionnelles supplémentaires, le propriétaire autorise explicitement la
qualification de l'évolution, la fermeture du Work Order et son déplacement vers `completed`. La
décision s'appuie sur la totalité des preuves consignées : portes Maven et PostgreSQL réussies,
zéro appel fournisseur pendant l'implémentation et les tests, collecte/import J3 terminal,
catalogue géographique actionnable, découverte et liens J5 observés, correction du rendu J3 et
campagne J5 locale terminale avec trois imports et zéro appel fournisseur.

Cette clôture ne transforme pas les validations automatisées en geste fournisseur, n'autorise
aucune nouvelle requête réelle et ne requalifie pas les incidents `HTTP_FORBIDDEN` ou
`TRANSPORT_IO_FAILURE` historiques. Les opt-ins restent désactivés par défaut et toute nouvelle
recette fournisseur conserve son autorisation distincte.

```text
IMPLEMENTATION=COMPLETED
TECHNICAL_READINESS=AUTOMATED_V25_J3_AND_J5_LOCAL_IMPORT_VALIDATION_PASS
OWNER_CORRECTIVE_VALIDATION_J3_LAYOUT_AND_J5_LOCAL_IMPORT=PASS
HUMAN_PROVIDER_QUALIFICATION=PASS_BY_OWNER_DECISION
QUALIFICATION_DATE=2026-08-21
CLOSURE_AUTHORIZED=YES
PROVIDER_CALLS_DURING_IMPLEMENTATION=0
PROVIDER_CALLS_DURING_AUTOMATED_TESTS=0
ADDITIONAL_PROVIDER_CALL_AUTHORIZED_BY_CLOSURE=NO
WORK_ORDER_STATUS=VALIDATED
WORK_ORDER_LOCATION=docs/work_orders/completed
```

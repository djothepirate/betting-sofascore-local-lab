# WO-SS-20260822-010 — Import J5 hors ligne multi-match atomique

- **Statut :** `VALIDATED`
- **Date :** 2026-08-22
- **Date de démarrage :** 2026-08-22
- **Date de fin technique :** 2026-08-23
- **Date de validation et de clôture :** 2026-08-23
- **Qualification propriétaire :** `PASS`
- **Clôture :** `COMPLETED`
- **Base locale :** `47968ed98d5795f37f4e40f6e6d61519494526b4`
- **Branche :** `codex/j5-offline-multi-match-batch`
- **Jalon :** évolution complémentaire J5, strictement hors ligne
- **Demande et autorisation propriétaire :** conception, documentation et implémentation explicitement autorisées par le propriétaire dans le présent chat le 2026-08-22
- **Appel fournisseur pendant l'implémentation :** `NOT_AUTHORIZED`
- **Appel fournisseur pendant les tests automatisés :** `NOT_AUTHORIZED`
- **Polling, planification, watcher ou retry :** `NOT_AUTHORIZED`
- **Cache fournisseur et coordinateur réseau :** `NOT_AUTHORIZED`
- **Déploiement VPS / envoi au Betting Project :** `NOT_AUTHORIZED`
- **Modification de l'ADR-SS-001 :** `NOT_REQUIRED_AFTER_EXPLICIT_REVIEW`
- **Migration :** `NONE`
- **Qualification humaine hors ligne :** `PASS_INCLUDING_COMBINED_ENABLED_RUNTIME_IMPORT`

## 1. Objectif

Réduire le coût humain des imports J5 lors des journées chargées sans automatiser une collecte
SofaScore. L'opérateur recherche les événements canoniques déjà présents pour une date civile et
un fuseau, en sélectionne de un à vingt-cinq dans l'interface Web, prépare un plan déterministe,
puis fournit en une seule requête multipart une preuve locale pour chacune des trois familles de
chaque rencontre :

```text
statistics
incidents
lineups
```

Chaque preuve est soit un fichier JSON réellement conservé par l'opérateur, soit une déclaration
explicite « HTTP 404 observé » qui produit une enveloppe JSON locale canonique et reconnaissable.
L'application valide l'intégralité du lot avant de réclamer l'intention ou d'écrire, puis persiste
les `3N` preuves dans une transaction unique. Toute erreur refuse ou annule le lot entier. Cette
évolution automatise le contrôle et l'ingestion de fichiers remis par un humain ; elle n'acquiert
aucune donnée sur le réseau et ne constitue ni un polling, ni une collecte programmée.

Les statuts du laboratoire restent :

```text
EXPERIMENTAL
LOCAL_ONLY
NOT_PRODUCTION_APPROVED
NO_CRITICAL_DEPENDENCY
```

## 2. Analyse structurelle faisant autorité

Le contrat fonctionnel détaillé est figé dans :

```text
docs/requirements/J5-OFFLINE-MULTI-MATCH-IMPORT-RULES.md
```

La conception technique est figée dans :

```text
docs/architecture/J5-OFFLINE-MULTI-MATCH-IMPORT.md
```

La situation de départ et la matrice de qualification sont consignées dans :

```text
docs/validation/J5-OFFLINE-MULTI-MATCH-IMPORT-TECHNICAL-READINESS-20260822.md
```

Décisions essentielles :

```text
EXECUTION_MODE=STRICTLY_OFFLINE_SYNCHRONOUS_WEB_IMPORT
CANONICAL_SELECTION_COUNT=1_TO_25
FREE_EVENT_ID_INPUT=FORBIDDEN
PLAN_STORAGE=PROCESS_MEMORY_ONLY
PLAN_TTL=15_MINUTES
PLAN_HASH_VERSION=j5-offline-multi-match-plan-v1
PROCESSING_ORDER=providerEventId_ASC_THEN_statistics_incidents_lineups
EVIDENCE_SLOT_COUNT=3_TO_75_EXACTLY_3_PER_EVENT
PHYSICAL_FILE_COUNT=0_TO_75
EVIDENCE_SLOT=JSON_FILE_XOR_EXPLICIT_404_DECLARATION
DECLARATION_UI=CHECKBOX_PER_EVENT_AND_FAMILY
DECLARATION_PAYLOAD=CANONICAL_LOCAL_JSON_WITH_OPERATOR_MARKER
FILE_AND_DECLARATION_FOR_SAME_SLOT=FORBIDDEN
MISSING_FILE_AND_DECLARATION_FOR_SAME_SLOT=FORBIDDEN
PROVIDER_STATUS_INFERENCE_FROM_EVENT_STATE=FORBIDDEN
MAX_MIB_PER_FILE=5
MAX_MIB_PER_BATCH=25
PREVALIDATE_ALL_EVIDENCE_BEFORE_CLAIM=YES
PREVALIDATE_ALL_EVIDENCE_BEFORE_WRITE=YES
IMPORT_TRANSACTION=ONE_ATOMIC_TRANSACTION_FOR_3N
RAW_ACQUISITION_MODE=MANUAL_LOCAL_JSON_IMPORT
EXPLICIT_UNAVAILABLE_ENVELOPE=STRICT_ERROR_CODE_INTEGER_404_ONLY
PROVIDER_CALLS=0
PROVIDER_CACHE_READS_OR_WRITES=0
NETWORK_COORDINATOR_ACQUISITIONS=0
REAL_J5_CONTROL_SERVICE_REUSED=NO
NETWORK_FLAGS_REQUIRED_STATE=ALL_DISARMED
PLAN_REVALIDATION_BEFORE_IMPORT=YES
DURABLE_BATCH_HISTORY_OR_RESUME=NO
SCHEDULER_WATCHER_RETRY_STAGING=NO
DATABASE_MIGRATION=NO
```

## 3. Revue de l'ADR-SS-001

L'ADR-SS-001 est relue avant implémentation. Le présent Work Order n'ajoute aucun endpoint
fournisseur, n'active aucun endpoint du catalogue, ne modifie aucune URI et ne desserre pas
`ConnectorGate`. Les trois types logiques J5 existants servent uniquement à identifier et parser
des corps JSON locaux déjà fournis par l'opérateur.

Cette évolution reste compatible avec l'ADR parce que :

- aucun `RestClient`, client HTTP, transport SofaScore ou mécanisme de résolution d'URI n'est
  accessible depuis le nouveau flux ;
- `sofascore.enabled=false` comme `true` permettent de préparer et exécuter le lot local ;
- les opt-ins J3/J4/J5/découverte, `sofascore.base-url` et `sofascore.allowed-endpoints` peuvent
  rester armés sans être consultés par ce flux ;
- le catalogue général reste `callable=false`, sans URI réelle ;
- aucun appel fournisseur n'est autorisé pendant l'implémentation ou les tests ;
- les garde-fous local-only, la conservation brute et la provenance restent inchangés.

Une modification ultérieure introduisant transport, récupération automatique, surveillance d'un
répertoire, planification, retry ou exécution en arrière-plan sortirait de ce Work Order et
exigerait un nouveau Work Order ainsi qu'une nouvelle revue explicite de l'ADR-SS-001.

### 3.1 Continuité avec les Work Orders précédents

- WO-007 fournit l'historique J6 et les occurrences qui rendent les réimports committés auditables,
  sans créer d'historique durable spécifique au lot ;
- WO-008 fournit la sélection canonique courante J7 et maintient hors portée les exports par lot et
  les appels automatiques ;
- WO-009 qualifie l'import J5 local unitaire, son ordre `STATISTICS → INCIDENTS → LINEUPS`, sa
  prévalidation avant claim, l'enveloppe 404 fermée et `MANUAL_LOCAL_JSON_IMPORT`, mais exclut
  explicitement le lancement automatique ou par lot de J5.

WO-010 est donc requis pour autoriser le nouveau périmètre multi-match. Il réutilise les garanties
précédentes sans élargir leurs permissions réseau.

## 4. Portée autorisée

### 4.1 Sélection Web canonique

- étendre l'explorateur Web d'événements pour une recherche par `date` et `zone` ;
- présenter seulement les observations canoniques courantes comprises dans la journée locale ;
- autoriser une sélection distincte de `1..25` UUID canoniques existants ;
- ne poster que ces UUID ; aucune zone de saisie de `providerEventId` ou d'`eventId` n'est admise ;
- résoudre côté serveur le `providerEventId` positif et l'identifiant de l'observation canonique
  courante ;
- calculer la fenêtre UTC demi-ouverte `[fromInclusive, toExclusive)` depuis la date et la zone.

### 4.2 Plan en mémoire

- créer un contrôle dédié, séparé de `J5RealControlService` ;
- conserver au plus un plan actif par processus dans la seule mémoire de l'application ;
- attribuer au plan un `requestId` UUID, une création, une expiration exacte à quinze minutes et
  un état ;
- ordonner les rencontres par `providerEventId` croissant ; refuser tout doublon d'UUID ou
  d'identifiant fournisseur ;
- produire pour chaque rencontre les trois noms attendus dans l'ordre `statistics`, `incidents`,
  `lineups` ;
- calculer le SHA-256 versionné et déterministe par contenu couvrant la date, la zone, la fenêtre
  UTC et, pour chaque événement trié, l'UUID canonique, le `providerEventId`, l'observation
  canonique courante, `startsAt`, le hash canonique courant et tous les fichiers attendus ;
- exclure explicitement du hash le `requestId`, `preparedAt` et `expiresAt`, afin qu'un même contenu
  de plan produise le même hash ;
- exposer la phrase exacte
  `IMPORTER {N} MATCHS J5 HORS LIGNE {PLAN_SHA256}`, liée au nombre de rencontres et au hash du
  plan ;
- expirer paresseusement le plan lors de toute consultation ou commande, sans scheduler ;
- permettre une nouvelle préparation immédiatement après tout état terminal.

### 4.3 Import multipart plat

- accepter un multipart plat contenant exactement `3N` preuves, soit de 3 à 75 emplacements,
  chacun matérialisé exclusivement par un fichier JSON ou par une déclaration 404 explicite ;
- énumérer toutes les parties natives, exiger une occurrence unique de chacun des six champs de
  contrôle, reconnaître seulement les déclarations répétables attendues et refuser toute partie
  inconnue, dupliquée ou manquante ;
- exiger que l'union des noms de fichiers et des noms déclarés 404 soit exactement égale au
  manifeste du plan, sans intersection entre les deux ensembles ;
- exiger les noms exacts
  `event-<providerEventId>-(statistics|incidents|lineups).json` ;
- valider le `Content-Disposition` ASCII brut avant tout nom décodé et refuser notamment
  `filename*`, RFC 5987, RFC 2047, quoted-pairs ou paramètre supplémentaire ;
- refuser chemin, archive, sous-répertoire, casse différente, suffixe supplémentaire, doublon,
  famille inconnue ou événement non prévu ;
- appliquer 5 Mio maximum par fichier et 25 Mio maximum pour l'ensemble des preuves persistées ;
- capturer les octets en mémoire bornée et réutiliser le détecteur de contenu sensible ;
- accepter pour un fichier soit le JSON compatible avec le parseur courant de la famille, soit
  l'enveloppe 404 fermée définie par les exigences ;
- convertir une déclaration explicite en une enveloppe JSON locale canonique portant le marqueur
  `LOCAL_OPERATOR_DECLARED_HTTP_404`, sans prétendre conserver le corps fournisseur absent ;
- valider tous les noms, tailles, empreintes, enveloppes et parseurs avant le claim du plan et
  avant toute écriture ;
- ne créer aucun fichier temporaire, staging, extraction d'archive ou surveillance de répertoire.

### 4.4 Claim et persistance

- consommer un `LocalFormToken` à usage unique ;
- exiger l'acquittement explicite et la phrase exacte, comparée en temps constant ;
- revalider le plan et les observations canoniques courantes immédiatement avant le claim, puis à
  l'entrée de la transaction ;
- traiter les `3N` éléments dans l'ordre stable `providerEventId`, puis
  `statistics`, `incidents`, `lineups` ;
- persister l'intégralité du lot dans une transaction unique, brut avant normalisé pour chaque
  élément, tout en garantissant le rollback de l'ensemble sur la moindre erreur ;
- conserver `MANUAL_LOCAL_JSON_IMPORT`, les clés de requête J5 existantes, le SHA-256, le parseur
  courant et l'heure de réception ;
- conserver les règles V25 de déduplication et les occurrences J6 sans mélanger import local et
  acquisition directe ;
- afficher un résultat minimisé du lot, uniquement en mémoire, sans payload, nom local, chemin,
  phrase de confirmation ou secret.

### 4.5 Interface et contrôleurs

- ajouter la page Web locale dédiée `GET /j5-import-batches?date={DATE}&zone={ZONE}`, liée depuis
  `/events` et les fiches J5 ;
- utiliser exclusivement `POST /j5-import-batches/prepare`,
  `POST /j5-import-batches/execute` et `POST /j5-import-batches/stop`, avec `eventIds` pour les
  UUID canoniques cochés, `batchFiles` pour le multipart plat et le `requestId` exact du plan pour
  l'exécution comme pour l'annulation ;
- appliquer `Cache-Control: no-store`, `Pragma: no-cache`, `Expires: 0` et
  `X-Robots-Tag: noindex, nofollow, noarchive` ;
- préserver les routes J5 unitaires et réelles existantes ;
- appliquer également à l'import J5 unitaire le choix exclusif fichier ou déclaration 404 pour
  chacune des trois familles ;
- ne jamais utiliser `/real` pour le nouveau flux hors ligne ;
- rendre les erreurs fermées, bornées et sans réflexion des noms de fichiers ou du JSON reçu.

## 5. Hors portée

Sont explicitement exclus :

- tout appel HTTP, réseau ou fournisseur ;
- toute saisie libre d'`eventId`, d'URI, de statut HTTP, d'en-tête, de cookie ou de jeton
  fournisseur ; la seule déclaration de statut admise est la case fermée `404 observé` liée à une
  famille attendue ;
- toute création d'événement canonique ou correction automatique de son identité ;
- tout mélange d'import local et d'acquisition directe dans un même plan ;
- tout lot incomplet, import partiel ou commit par rencontre ;
- tout zip, archive, dépôt dans un répertoire, watcher, scheduler, polling, tâche de fond ou retry ;
- tout cache fournisseur, coordinateur réseau ou mécanisme de limitation de transport ;
- toute table de lot, historique durable de contrôle, reprise après redémarrage ou migration ;
- toute extension automatique aux jalons J3, J4, J6 ou J7 ;
- toute modification des règles métier et des parseurs J5 qualifiés ;
- toute modification de l'ADR-SS-001 ou des PDF de référence ;
- tout déploiement, export automatique vers le VPS ou dépendance du Betting Project principal.

## 6. Cycle de vie contractuel

```text
LOCKED
  └─ prepare(valid selection) ─> AWAITING_CONFIRMATION
       ├─ passive expiry ──────> EXPIRED_LOCKED
       ├─ explicit cancel ─────> STOPPED_LOCKED
       ├─ file/plan rejection ─> AWAITING_CONFIRMATION + PLAN_CHANGED or bounded error
       └─ valid prevalidation + exact claim ─> EXECUTING
            ├─ atomic commit ──> COMPLETED_LOCKED
            └─ rollback ───────> FAILED_LOCKED
```

Une erreur de fichier ou une dérive `PLAN_CHANGED` détectée avant le claim ne consomme pas le plan :
il reste `AWAITING_CONFIRMATION` jusqu'à correction lorsque cela est possible, annulation ou
expiration. Si la dérive apparaît après le claim, la transaction est annulée et le contrôle passe
à `FAILED_LOCKED`. Les états `EXPIRED_LOCKED`, `STOPPED_LOCKED`, `COMPLETED_LOCKED` et
`FAILED_LOCKED` sont terminaux. Chacun autorise immédiatement une nouvelle préparation explicite.
Le redémarrage du processus revient à `LOCKED` et ne restaure ni plan, ni résultat.

## 7. Critères d'acceptation obligatoires

### 7.1 Sélection et plan

- [x] une sélection de 1 et une sélection de 25 événements valides produisent un plan ;
- [x] 0, 26, doublons, UUID inconnu, événement hors date/zone ou identité fournisseur invalide
  sont refusés sans plan partiel ;
- [x] aucun formulaire ou contrôleur n'accepte d'`eventId` libre ;
- [x] le tri est strictement `providerEventId` croissant puis famille fixe ;
- [x] le plan expire exactement après quinze minutes selon une `Clock` injectée, sans scheduler ;
- [x] le hash `j5-offline-multi-match-plan-v1` varie pour chaque champ protégé et reste stable pour
  le même document canonique ;
- [x] tout changement de l'observation canonique courante invalide le plan avant import ;
- [x] un nouveau plan peut être préparé après chaque état terminal.

### 7.2 Multipart et prévalidation

- [ ] exactement trois preuves par rencontre sont requises, chacune étant un fichier ou une
  déclaration 404 exclusive ;
- [ ] les noms stricts, les déclarations et l'ensemble attendu sont contrôlés indépendamment de
  l'ordre multipart ;
- [ ] un fichier et une déclaration pour la même famille, une double déclaration ou une famille
  sans preuve sont refusés avant claim ;
- [x] les six contrôles multipart sont uniques et toute partie inconnue, encodée ou munie d'une
  disposition non canonique est refusée ;
- [x] fichiers vides, supérieurs à 5 Mio, lot supérieur à 25 Mio, sensibles, dupliqués, inconnus
  ou mal nommés sont refusés ;
- [x] les identifiants décimaux négatifs, nuls, avec zéro initial ou dépassant `Long.MAX_VALUE`
  sont refusés ;
- [x] le dernier fichier invalide d'un lot ne provoque ni claim ni écriture ;
- [ ] une enveloppe 404 fermée et une déclaration 404 canonique sont acceptées comme
  indisponibilités explicites ; un 403, un code 404 textuel ou une enveloppe avec champ inattendu
  est refusé ;
- [x] la prévalidation réussie n'écrit rien avant le claim.

### 7.3 Sécurité du claim

- [x] le token local est obligatoire, à usage unique et non réutilisable ;
- [x] l'acquittement explicite est obligatoire ;
- [x] la phrase exacte est liée au nombre de matchs et au SHA-256 du plan, sans normalisation ;
- [x] `requestId`, phrase, expiration ou contenu protégé divergent sont refusés ;
- [x] `sofascore.enabled=false` comme `true` laissent la préparation et l'exécution éligibles ;
- [x] avec `sofascore.enabled=true`, la configuration combinée J3/J4 phase 2/J5/découverte,
  l'origine exacte et les six endpoints autorisés reste éligible au lot, même si les politiques
  manuelles réelles sont elles aussi disponibles ;
- [x] une automatisation active ou la conservation brute coupée rend la préparation et l'exécution
  inéligibles ;
- [x] le contrôle ne dépend pas de `J5RealControlService` et n'en modifie jamais l'état.

### 7.4 Atomicité et provenance

- [x] un lot nominal de `N` rencontres commit exactement `3N` traitements ordonnés ;
- [x] une erreur tardive de brut, parsing, normalisation, classification ou base annule toutes les
  écritures du lot ;
- [x] chaque snapshot porte `MANUAL_LOCAL_JSON_IMPORT`, la clé J5 exacte, le SHA-256, le parseur,
  l'horodatage et le statut 200 ou 404 inféré localement ;
- [x] brut et normalisé restent séparés et reliés par la provenance existante ;
- [x] la réimportation exige un nouveau plan, respecte la déduplication V25 et ajoute les
  occurrences J6 prévues ;
- [x] un snapshot dédupliqué encore `RAW_ONLY` est reclassifié dans la transaction, et une
  classification contradictoire annule le lot ;
- [x] l'import local et une acquisition directe du même payload restent des provenances distinctes ;
- [x] aucun cache fournisseur ou coordinateur réseau n'est consulté.

### 7.5 Absence d'automatisation réseau

- [x] zéro appel à un transport SofaScore dans tous les tests standards et d'intégration ;
- [x] aucun `@Scheduled`, `@EnableScheduling`, `WatchService`, client HTTP, retry ou tâche de fond
  n'est introduit ;
- [x] aucun endpoint du catalogue ne devient appelable et aucune URI réelle n'est ajoutée ;
- [x] `server.address=127.0.0.1` et les valeurs réseau désarmées par défaut restent effectifs ;
- [x] aucune migration n'est ajoutée ou modifiée.

## 8. Matrice de tests attendue

| Domaine | Cas minimaux | Résultat attendu |
|---|---|---|
| plan | 1, 25, 0, 26, doublons, inconnu, hors fenêtre | plan exact ou refus intégral |
| fuseau | journée normale et transition DST | fenêtre UTC demi-ouverte exacte |
| hash | permutation d'entrée, mutation de chaque champ, stabilité | ordre canonique stable et mutation détectée |
| expiration | avant, à et après quinze minutes | éligible puis `EXPIRED_LOCKED` |
| multipart | fichiers, déclarations 404, mélange, manquant, extra, doublon, fichier + déclaration | ensemble exact indépendant de l'ordre reçu, avec XOR strict par famille |
| noms | traversée, antislash, casse, zéro initial, overflow | refus avant claim |
| tailles | 5 Mio exacts, dépassement fichier, dépassement lot | bornes binaires exactes |
| contenu | trois parseurs, 404 fermé, 403, JSON invalide, sensible | acceptation fermée ou refus avant claim |
| revalidation | dérive avant/après claim | `PLAN_CHANGED` encore en attente, ou rollback `FAILED_LOCKED` |
| transaction | panne au premier, au milieu et au dernier élément | rollback total des `3N` |
| provenance | nominal, 404, réimport, collision avec direct | mode et occurrences corrects |
| contrôle | token, phrase, acquittement, expiration, états terminaux | aucun contournement et réarmement explicite |
| réseau | mocks transport/cache/coordinator stricts | zéro interaction |
| Web | GET/POST, no-store, erreurs minimisées | aucune fuite de contenu ou nom local |

## 9. Validation exigée avant proposition de clôture

Les commandes suivantes devront être exécutées après implémentation :

```powershell
.\mvnw.cmd clean verify
.\mvnw.cmd -Pintegration-tests verify
.\scripts\Verify-Local.ps1 -WithIntegrationTests
```

Les vérifications manuelles suivantes sont également obligatoires :

1. confirmer que le diff ne contient ni secret, cookie, jeton, payload réel ni fichier `.env` ;
2. confirmer que `server.address=127.0.0.1` reste effectif ;
3. confirmer que tous les flags réseau restent `false` par défaut, base URL et liste d'endpoints
   vides ;
4. qualifier hors ligne un lot d'au moins deux rencontres, avec au moins une déclaration 404 sans fichier ;
5. provoquer un refus prévalidation et une panne transactionnelle et constater zéro écriture ;
6. réimporter le lot au moyen d'un nouveau plan et contrôler déduplication et occurrences ;
7. vérifier explicitement les compteurs : appels fournisseur 0, cache 0, coordinateur 0.

## 10. État initial et règle de clôture

État initial consigné à la création du Work Order :

```text
CURRENT_STATUS=IN_PROGRESS
TARGET_AFTER_IMPLEMENTATION=IMPLEMENTED_AWAITING_HUMAN_QUALIFICATION
TARGET_AFTER_OWNER_QUALIFICATION=VALIDATED
```

État courant après implémentation, validations automatisées et qualification humaine complète :

```text
CURRENT_STATUS=VALIDATED
TARGETED_CORRECTIVE_VALIDATION=PASS_64_TESTS
MANIFEST_ASSISTANCE_WEB_SECURITY_VALIDATION=PASS_22_TESTS
AUTOMATED_VALIDATION=PASS_614_TESTS_2_SKIPPED
INTEGRATION_VALIDATION=PASS_52_TESTS
VERIFY_LOCAL_WITH_INTEGRATION_TESTS=PASS_NO_PROVIDER_CALLS
HUMAN_OFFLINE_QUALIFICATION=PASS_INCLUDING_COMBINED_ENABLED_RUNTIME_IMPORT
OWNER_DECISION_REQUIRED_FOR_VALIDATED=NO_DECISION_GRANTED_2026_08_23
```

La correction atteint `VALIDATED` après implémentation complète, réussite de la matrice automatisée,
requalification de la déclaration 404, qualification humaine hors ligne et décision explicite du
propriétaire le 2026-08-23. Toutes les portes de preuve et de gouvernance sont fermées ; le Work
Order est déplacé vers `docs/work_orders/completed/`.

## 11. Qualification humaine partielle du 2026-08-22

Le propriétaire a exécuté dans l'interface locale un lot nominal portant sur Olympique de
Marseille — RC Strasbourg, événement canonique
`1aefe36a-8474-3967-8c59-9e8e3f9c42fb` et `providerEventId=16310922`. Le plan préparé porte la
demande `18c516f9-50e1-42e3-b086-d14d6f563955`, le SHA-256
`58154c6b03af7e36a36a05cb8551b7fcc4bd8bd0d26a8f46ba27a1832dd195a4`, une rencontre et trois
corps attendus. Après un premier refus humain `FILE_NAME_INVALID`, les trois noms canoniques ont
été acceptés et le lot a atteint `COMPLETED`.

La preuve terminale minimisée et sa relecture SQL locale établissent :

| Famille | Snapshot | Observation | Octets | Parseur | Complétude | Occurrence |
|---|---:|---:|---:|---|---|---|
| `EVENT_STATISTICS` | 417 | 222 | 25 350 | `event-statistics-v2` | `COMPLETE · 100% · 262/262` | `INSERTED` |
| `EVENT_INCIDENTS` | 418 | 223 | 47 572 | `event-incidents-v13` | `COMPLETE · 100% · 92/92` | `INSERTED` |
| `EVENT_LINEUPS` | 419 | 224 | 73 784 | `event-lineups-v2` | `COMPLETE · 100% · 85/85`, confirmées | `INSERTED` |

```text
HUMAN_NOMINAL_EVENT_COUNT=1
HUMAN_NOMINAL_LOCAL_JSON_IMPORTS=3
HUMAN_NOMINAL_TOTAL_BYTES=146706
HUMAN_NOMINAL_PROVIDER_CALLS=0
HUMAN_NOMINAL_PROVIDER_CACHE_OPERATIONS=0
HUMAN_NOMINAL_COORDINATOR_ACQUISITIONS=0
HUMAN_NOMINAL_AUTOMATIC_RETRIES=0
HUMAN_NOMINAL_ACQUISITION_MODE=MANUAL_LOCAL_JSON_IMPORT
HUMAN_NOMINAL_HTTP_STATUS=200_FOR_ALL_THREE
HUMAN_NOMINAL_SCHEMA_STATUS=PARSED_FOR_ALL_THREE
HUMAN_NOMINAL_RESULT=PASS
```

Cette campagne qualifie le parcours nominal réel à une rencontre, la provenance locale, les trois
parseurs courants, le rendu normalisé et les compteurs à zéro. Elle ne satisfait pas encore les
conditions de clôture des points 4 à 6 de la section 9. Restent obligatoires avant `VALIDATED` :

1. un refus prévalidation avec comparaison explicite des comptes avant/après ;
2. une panne transactionnelle tardive avec preuve du rollback global ;
3. un réimport sous nouveau plan avec déduplication et occurrences supplémentaires ;
4. un redémarrage confirmant l'absence de reprise du plan et du résultat ;
5. la décision explicite du propriétaire de clôturer le Work Order.

À ce stade antérieur au signal correctif, le Work Order restait actif sous
`IMPLEMENTED_AWAITING_HUMAN_QUALIFICATION`.

## 12. Signal correctif humain du 2026-08-22

La campagne locale de Borussia Dortmund — FC Bayern München, `providerEventId=16248441`, a
correctement terminé avec zéro appel fournisseur. Les métadonnées minimisées montrent une famille
`EVENT_STATISTICS` indisponible, une famille `EVENT_INCIDENTS` vide valide et des compositions
partielles. Pour franchir le contrat multipart unitaire, le propriétaire a toutefois dû recopier
manuellement le JSON de la réponse 404 et créer un fichier uniquement parce que les trois fichiers
étaient obligatoires.

```text
HUMAN_SIGNAL_EVENT_ID=16248441
HUMAN_SIGNAL_STATISTICS=UNAVAILABLE
HUMAN_SIGNAL_INCIDENTS=EMPTY_VALID
HUMAN_SIGNAL_LINEUPS=PARTIAL
HUMAN_SIGNAL_PROVIDER_CALLS_BY_IMPORT=0
HUMAN_SIGNAL_FRICTION=MANUAL_404_FILE_CREATION_REQUIRED
CORRECTION=EXPLICIT_PER_FAMILY_404_DECLARATION
AUTOMATIC_404_INFERENCE=FORBIDDEN
```

Ce signal a rouvert WO-010 pour correction. Les parcours unitaire et multi-match sont maintenant
couverts et la validation complète est réussie. La recette hors ligne de la nouvelle déclaration
est consignée en section 14.

## 13. Validation corrective du 2026-08-22

La correction a été validée dans une copie temporaire isolée du worktree afin de ne pas perturber
les instances locales déjà ouvertes :

```text
TARGETED_CORRECTIVE_TESTS=64_PASS
MANIFEST_ASSISTANCE_WEB_SECURITY_TESTS=22_PASS
STANDARD_TESTS=612_PASS_2_SKIPPED
INTEGRATION_TESTS=51_PASS
POSTGRESQL_TESTCONTAINERS=18.4
FLYWAY_MIGRATIONS=25_TO_V25
PREFLIGHT_RESULT=PASS
VERIFY_RESULT=PASS
SOFASCORE_NETWORK_CALLS_EXECUTED=NO
LIVE_OFFLINE_BATCH_UI_DESKTOP=PASS
LIVE_OFFLINE_BATCH_UI_MOBILE_375PX=PASS_NO_HORIZONTAL_OVERFLOW
LIVE_OFFLINE_BATCH_UI_CONSOLE_ERRORS=0
LIVE_MANIFEST_FILE_AND_404_CONFLICT=PASS_BLOCKED_WITH_EXACT_FILE_NAME
LIVE_MANIFEST_DUPLICATE_NAME=PASS_BLOCKED_WITH_EXACT_FILE_NAME
LIVE_MANIFEST_MIXED_EXACT=PASS_FILE_404_404_IMPORT_ENABLED
HUMAN_404_DECLARATION_REQUALIFICATION=PASS
```

Les contrôles couvrent notamment la déclaration seule, son enveloppe canonique, sa persistance et
sa normalisation `UNAVAILABLE`, le refus fichier plus déclaration, le refus d'une preuve absente,
les dispositions multipart non canoniques, l'UTF-8 invalide et la sentinelle de fichier vide émise
par le navigateur. Aucune migration n'est ajoutée ou modifiée.

## 14. Requalification multi-match et signal d'ergonomie du 2026-08-22

Le propriétaire a importé par la voie corrigée un plan de quatre rencontres, demande
`465f6849-e585-4b3f-9182-a21f91770dea`, composé de neuf fichiers et trois déclarations 404. Le lot
a atteint `COMPLETED` avec douze preuves et 363 863 octets contrôlés.

```text
HUMAN_MULTI_EVENT_COUNT=4
HUMAN_MULTI_EVENT_LOCAL_JSON_IMPORTS=12
HUMAN_MULTI_EVENT_DECLARED_404=3
HUMAN_MULTI_EVENT_TOTAL_BYTES=363863
HUMAN_MULTI_EVENT_PROVIDER_CALLS=0
HUMAN_MULTI_EVENT_PROVIDER_CACHE_OPERATIONS=0
HUMAN_MULTI_EVENT_COORDINATOR_ACQUISITIONS=0
HUMAN_MULTI_EVENT_AUTOMATIC_RETRIES=0
HUMAN_404_DECLARATION_REQUALIFICATION=PASS
HUMAN_MULTI_EVENT_WITH_404=PASS
```

La preuve minimisée montre notamment RC Lens — Auxerre en trois familles `COMPLETE · 100%`, puis
Saint-Étienne — Grenoble Foot 38 avec statistiques et compositions `UNAVAILABLE · N/A` et incidents
`EMPTY_VALID · 100%`. Les vues J5 confirment que les indisponibilités ne fabriquent ni métrique, ni
joueur, ni dispositif tactique. La troisième déclaration 404 est comptée par le résultat terminal ;
son rattachement n'est pas inféré depuis les seules captures partielles.

Un essai distinct a ensuite exposé une friction avant import : un plan de quinze preuves affichait
douze fichiers sélectionnés et au moins quatre déclarations cochées. Le serveur a correctement
répondu `DUPLICATE_FILE` avant import, mais le sélecteur natif n'affichait que « 12 fichiers » et ne
permettait pas d'identifier le nom en conflit avant l'envoi.

Le correctif d'assistance conserve le serveur comme seule autorité et ajoute un script local de
progressive enhancement. Il rapproche uniquement les noms et tailles exposés par le navigateur du
manifeste déjà rendu, affiche l'état de chaque preuve, les comptes fichier/déclaration, les conflits,
les doublons, les noms inattendus et les preuves manquantes, puis désactive le bouton tant que
l'ensemble fichier XOR déclaration n'est pas exact. Il ne lit aucun contenu JSON, ne persiste rien,
n'émet aucun appel et ne réclame pas le plan. Sans JavaScript, le formulaire reste soumis à la même
prévalidation serveur stricte.

La vérification locale en navigateur a reproduit les deux ambiguïtés sans soumettre le formulaire :
un fichier combiné à sa déclaration passe à `CONFLIT`, deux fichiers de même nom passent à
`DOUBLON`, le nom exact est affiché dans les deux cas et l'import reste désactivé. Après correction,
le manifeste mixte `FICHIER / 404 / 404` redevient exact et réactive le bouton. Les contrôles
desktop et mobile 375 px ne montrent aucun débordement horizontal et la console reste sans erreur.

```text
CURRENT_STATUS=VALIDATED
HUMAN_MULTI_EVENT_404_GATE=PASS
LOCAL_MANIFEST_ASSISTANCE_GATE=PASS
HUMAN_MULTI_MATCH_IMPORT_GATE=PASS_BY_OWNER_CONFIRMATION
HUMAN_MANIFEST_ASSISTANCE_GATE=PASS_BY_OWNER_CONFIRMATION
HUMAN_MANIFEST_CONTRADICTION_AND_RECOVERY_GATE=PASS_BY_OWNER_EVIDENCE
HUMAN_CANCEL_BEFORE_IMPORT_GATE=PASS_BY_OWNER_EVIDENCE
AUTOMATED_PREFLIGHT_DB_COUNTS_GATE=PASS_TESTCONTAINERS_JDBC_BEFORE_EQUALS_AFTER
HUMAN_LATE_ROLLBACK_GATE=PASS_OWNER_EXECUTED_TARGETED_POSTGRESQL_TEST
HUMAN_REIMPORT_DEDUP_UI_GATE=PASS_3_DEDUPLICATED_3_NEW_VERSIONS
HUMAN_REIMPORT_DEDUP_GATE=PASS_BY_OWNER_AND_SQL_EVIDENCE
HUMAN_REIMPORT_OCCURRENCE_COUNT_GATE=PASS_SQL_READ_ONLY_2_PER_SNAPSHOT
HUMAN_RESTART_NO_RESUME_GATE=PASS_BY_OWNER_EVIDENCE
OWNER_VALIDATION_DECISION=GRANTED_2026_08_23
```

## 15. Qualification propriétaire du parcours d'import multi-match du 2026-08-22

Le propriétaire déclare les tests d'import multi-match J5 concluants après une nouvelle campagne
portant sur Borussia Dortmund — FC Bayern München et Saint-Étienne — Grenoble Foot 38. Le plan
attendait six preuves ; l'assistance de manifeste a reconnu exactement quatre fichiers et deux
déclarations 404, soit six saisies sur six, avant l'activation de la confirmation.

La demande terminale `7ecf9176-79c4-49bf-89cd-5a39e5ce3634` a atteint `COMPLETED` avec deux
rencontres, six preuves locales et 106 210 octets contrôlés. Les compteurs d'appels fournisseur,
d'opérations cache, d'acquisitions coordinateur et de retry sont tous restés à zéro. Le résultat
présente trois observations `NOUVELLE_VERSION` et trois observations `DÉDUPLIQUÉE`, tandis que les
vues normalisées affichent correctement les compositions disponibles et les indisponibilités 404.

Un plan distinct de deux rencontres a également montré l'assistance avant import : sans saisie,
les six noms exacts sont signalés `À FOURNIR` ; après une déclaration 404, la ligne concernée passe
à `404` et le compteur descend à cinq preuves manquantes. Ces captures qualifient humainement le
parcours d'import et l'assistance de manifeste, y compris le mélange fichier/déclaration 404.

```text
OWNER_MULTI_MATCH_IMPORT_ASSESSMENT=CONCLUSIVE
OWNER_QUALIFIED_EVENT_COUNT=2
OWNER_QUALIFIED_LOCAL_PROOFS=6
OWNER_QUALIFIED_FILES=4
OWNER_QUALIFIED_DECLARED_404=2
OWNER_QUALIFIED_TOTAL_BYTES=106210
OWNER_QUALIFIED_PROVIDER_CALLS=0
OWNER_QUALIFIED_PROVIDER_CACHE_OPERATIONS=0
OWNER_QUALIFIED_COORDINATOR_ACQUISITIONS=0
OWNER_QUALIFIED_AUTOMATIC_RETRIES=0
OWNER_QUALIFIED_NEW_VERSIONS=3
OWNER_QUALIFIED_DEDUPLICATED_OBSERVATIONS=3
OWNER_MULTI_MATCH_IMPORT_GATE=PASS
OWNER_MANIFEST_ASSISTANCE_GATE=PASS
```

Cette acceptation ne démontre pas à elle seule les comptes SQL d'occurrences avant/après ni le
rollback transactionnel sur incident tardif. La preuve de redémarrage ultérieure ferme séparément
la porte d'absence de reprise ; les autres portes de résilience et la décision explicite de clôture
restent en attente. Le Work Order reste donc `IMPLEMENTED_AWAITING_HUMAN_QUALIFICATION`.

## 16. Refus local du manifeste contradictoire et reprise corrigée du 2026-08-22

Le propriétaire a exercé un plan d'une rencontre portant sur Saint-Étienne — Grenoble Foot 38.
Une première sélection associait trois fichiers à deux déclarations 404 et contenait à la fois le
nom hors plan `event-16363634-statistics.json` et un fichier accompagné de la déclaration 404
`event-16386251-incidents.json`. L'assistance a affiché les deux anomalies, marqué la famille
incidents `CONFLIT` et maintenu le bouton d'import désactivé.

Sans changer le plan ni sa phrase liée au SHA-256, l'opérateur a remplacé cette sélection par les
trois fichiers exacts. Le manifeste est alors passé à `3 fichiers · 0 déclarations 404 · 3 saisies
/ 3`, avec les trois familles marquées `FICHIER` et le bouton réactivé. Après confirmation, le lot
a été importé pour une rencontre avec trois preuves JSON locales, aucune déclaration 404 et zéro
appel fournisseur.

```text
OWNER_MANIFEST_UNEXPECTED_NAME_DETECTION=PASS
OWNER_MANIFEST_UNEXPECTED_NAME=event-16363634-statistics.json
OWNER_MANIFEST_FILE_404_CONFLICT_DETECTION=PASS
OWNER_MANIFEST_CONFLICT_NAME=event-16386251-incidents.json
OWNER_MANIFEST_INVALID_IMPORT_BUTTON=DISABLED
OWNER_MANIFEST_CORRECTION_WITHIN_SAME_PLAN=PASS
OWNER_MANIFEST_CORRECTED_FILES=3
OWNER_MANIFEST_CORRECTED_DECLARED_404=0
OWNER_MANIFEST_CORRECTED_PROOFS=3
OWNER_POST_CORRECTION_IMPORT_EVENT_COUNT=1
OWNER_POST_CORRECTION_IMPORT_LOCAL_PROOFS=3
OWNER_POST_CORRECTION_IMPORT_PROVIDER_CALLS=0
OWNER_MANIFEST_CONTRADICTION_AND_RECOVERY_GATE=PASS
```

Cette séquence démontre la protection opérateur avant envoi et la reprise nominale après
correction. Comme le bouton était désactivé pour le manifeste fautif, elle ne constitue pas la
mesure SQL avant/après d'un refus serveur ; `HUMAN_PREFLIGHT_DB_COUNTS_GATE` reste donc en attente.

## 17. Annulation explicite avant import du 2026-08-22

Le propriétaire a annulé un plan d'une rencontre avant toute remise de preuve. L'interface confirme
qu'aucun corps JSON n'a été persisté et la preuve terminale minimisée associe la demande
`7b929722-2808-41c2-b20f-24dd1c61b5ce` à l'état `OPERATOR_STOP`.

Le résultat porte une rencontre planifiée, zéro preuve JSON locale, zéro octet contrôlé, zéro appel
fournisseur, zéro opération cache fournisseur, zéro acquisition coordinateur et zéro retry. Aucun
résultat par famille n'est produit, conformément à une annulation antérieure à toute lecture ou
persistance de corps JSON.

```text
OWNER_CANCEL_BEFORE_IMPORT_STATE=OPERATOR_STOP
OWNER_CANCEL_BEFORE_IMPORT_EVENT_COUNT=1
OWNER_CANCEL_BEFORE_IMPORT_LOCAL_PROOFS=0
OWNER_CANCEL_BEFORE_IMPORT_CONTROLLED_BYTES=0
OWNER_CANCEL_BEFORE_IMPORT_PROVIDER_CALLS=0
OWNER_CANCEL_BEFORE_IMPORT_PROVIDER_CACHE_OPERATIONS=0
OWNER_CANCEL_BEFORE_IMPORT_COORDINATOR_ACQUISITIONS=0
OWNER_CANCEL_BEFORE_IMPORT_AUTOMATIC_RETRIES=0
OWNER_CANCEL_BEFORE_IMPORT_FAMILY_RESULTS=0
OWNER_CANCEL_BEFORE_IMPORT_GATE=PASS
```

Cette preuve qualifie le verrouillage terminal par décision opérateur et l'absence de traitement
d'un payload. Prise isolément, elle ne prouve pas l'absence de reprise après redémarrage ; cette
porte est qualifiée par la séquence distincte consignée ci-dessous.

## 18. Redémarrage sans reprise du plan ni du résultat du 2026-08-22

Après les campagnes précédentes et le résultat terminal `OPERATOR_STOP`, l'application a été
interrompue puis la page du lot J5 a été rechargée depuis un nouvel état de processus. La recherche
locale et les événements canoniques persistés restent consultables, mais aucun plan immuable actif,
aucun formulaire de confirmation et aucun résultat terminal antérieur ne sont restaurés.

Le contrôle du lot est recalculé depuis la configuration courante et apparaît `LOCKED`, avec les
conditions de garde réseau affichées comme absentes. Ce retour à l'état initial protégé confirme
que ni le plan mémoire ni le dernier résultat mémoire ne participent à une reprise automatique.
L'instance de validation qui avait servi les campagnes précédentes n'est plus active ; le contrôle
local effectué après les captures ne trouve plus de listener sur `127.0.0.1:8089`.

```text
OWNER_RESTART_PREVIOUS_TERMINAL_STATE=OPERATOR_STOP
OWNER_RESTART_ACTIVE_PLAN_AFTER_RESTART=NONE
OWNER_RESTART_CONFIRMATION_FORM_AFTER_RESTART=NONE
OWNER_RESTART_TERMINAL_RESULT_AFTER_RESTART=NONE
OWNER_RESTART_CANONICAL_LOCAL_EVENTS=AVAILABLE
OWNER_RESTART_BATCH_CONTROL_STATE=LOCKED
OWNER_RESTART_AUTOMATIC_RESUME=NONE
OWNER_RESTART_NO_RESUME_GATE=PASS
POST_CAPTURE_LISTENER_127_0_0_1_8089=NONE
```

Cette séquence ferme `HUMAN_RESTART_NO_RESUME_GATE`. À cette étape, elle ne modifiait pas les portes
encore ouvertes sur les comptes SQL avant/après, le rollback transactionnel tardif et le nombre
d'occurrences créé lors d'un réimport dédupliqué. Cette dernière porte est qualifiée ci-dessous.

## 19. Réimport dédupliqué et occurrences append-only du 2026-08-22

Le propriétaire a importé deux fois le même manifeste exact de six fichiers pour Borussia Dortmund
— FC Bayern München et RC Lens — Auxerre. La première demande
`05d82794-a30a-4a41-8cd8-029d5bebac11` a créé les snapshots 508 à 513 et les observations 252 à
257, tous marqués `NOUVELLE_VERSION`. La seconde demande
`73bdbe38-c776-4ec3-8a40-234d53e51d81` a conservé exactement les mêmes snapshots, observations,
tailles et SHA-256, tous marqués `DÉDUPLIQUÉE`.

Chaque passage porte deux rencontres, six preuves JSON locales, 318 884 octets contrôlés, aucune
déclaration 404 et zéro appel fournisseur, opération cache fournisseur, acquisition coordinateur
ou retry automatique. La liste locale des snapshots ne contient qu'une ligne pour chacun des
identifiants 508 à 513, avec la provenance `MANUAL_LOCAL_JSON_IMPORT`, HTTP 200 et schéma `PARSED`.

Une requête JDBC PostgreSQL strictement en lecture seule a ensuite contrôlé
`provider_snapshot_occurrence`. Chacun des six snapshots possède exactement deux occurrences : une
`INSERTED` suivie d'une `DEDUPLICATED`. Les tables canoniques contiennent toujours exactement six
snapshots 508 à 513 et six observations 252 à 257 liées à ces snapshots.

```text
OWNER_REIMPORT_FIRST_REQUEST=05d82794-a30a-4a41-8cd8-029d5bebac11
OWNER_REIMPORT_SECOND_REQUEST=73bdbe38-c776-4ec3-8a40-234d53e51d81
OWNER_REIMPORT_EVENT_COUNT_PER_RUN=2
OWNER_REIMPORT_LOCAL_PROOFS_PER_RUN=6
OWNER_REIMPORT_CONTROLLED_BYTES_PER_RUN=318884
OWNER_REIMPORT_PROVIDER_CALLS_PER_RUN=0
OWNER_REIMPORT_CANONICAL_SNAPSHOTS=6
OWNER_REIMPORT_CANONICAL_OBSERVATIONS=6
OWNER_REIMPORT_OCCURRENCES_PER_SNAPSHOT=2
OWNER_REIMPORT_INSERTED_PER_SNAPSHOT=1
OWNER_REIMPORT_DEDUPLICATED_PER_SNAPSHOT=1
OWNER_REIMPORT_OUTCOME_ORDER=INSERTED,DEDUPLICATED
OWNER_REIMPORT_DEDUP_GATE=PASS
OWNER_REIMPORT_OCCURRENCE_COUNT_GATE=PASS
```

Cette preuve ferme le scénario de réimport dédupliqué, y compris son effet append-only attendu. À
l'issue de cette séquence, les portes de résilience encore ouvertes étaient la mesure SQL
avant/après une prévalidation refusée et le rollback atomique sur incident tardif.

## 20. Prévalidation refusée et rollback tardif du 2026-08-22

Le propriétaire a soumis un manifeste exact de six fichiers pour Borussia Dortmund — FC Bayern
München et RC Lens — Auxerre. Le serveur a refusé un corps compositions incompatible avec le code
local `LINEUPS_PAYLOAD_INCOMPATIBLE`. Le contrôle est resté `AWAITING_CONFIRMATION` sur la demande
`1ad16ef2-5289-4fa9-be9e-33cbdf78cb8f`, avec deux rencontres et six preuves attendues : le plan n'a
donc pas été claimé et peut être corrigé puis resoumis.

Cette observation qualifie le chemin de refus serveur avant claim. La mesure SQL correspondante et
le scénario d'incident tardif sont désormais reproduits automatiquement par Testcontainers et JDBC,
sans pgAdmin ni client `psql` :

```powershell
.\mvnw.cmd -Pintegration-tests "-Dit.test=FlywayMigrationIT#rejectsTheWholeOfflineBatchDuringPrevalidationWithoutPersistence+rollsBackTheWholeOfflineBatchWhenTheLastFamilyOfTheLastEventFails" test-compile failsafe:integration-test failsafe:verify
```

Le premier test relève les comptes de `provider_snapshot`, `provider_snapshot_occurrence` et
`j5_event_data_observation`, refuse le dernier corps pendant la prévalidation et exige une égalité
stricte avant/après. Le second provoque une erreur sur la dernière famille du dernier événement
après des écritures antérieures dans la transaction, puis vérifie que les snapshots, occurrences et
observations du lot entier sont absents. La commande ciblée exécute deux tests sans échec. La suite
complète `.\mvnw.cmd -Pintegration-tests verify` confirme ensuite `52` tests d'intégration sans
échec sur PostgreSQL 18.4.

```text
OWNER_SERVER_PREFLIGHT_REJECTION=LINEUPS_PAYLOAD_INCOMPATIBLE
OWNER_SERVER_PREFLIGHT_REQUEST=1ad16ef2-5289-4fa9-be9e-33cbdf78cb8f
OWNER_SERVER_PREFLIGHT_PLAN_STATE_AFTER_REJECTION=AWAITING_CONFIRMATION
OWNER_SERVER_PREFLIGHT_PLAN_CLAIMED=NO
AUTOMATED_PREFLIGHT_DB_COUNTS_GATE=PASS_TESTCONTAINERS_JDBC_BEFORE_EQUALS_AFTER
OWNER_LATE_ROLLBACK_TARGETED_TEST=PASS
OWNER_LATE_ROLLBACK_FAILURE_POSITION=LAST_FAMILY_OF_LAST_EVENT
OWNER_LATE_ROLLBACK_POSTGRESQL_ASSERTIONS=NO_BATCH_SNAPSHOT_OCCURRENCE_OR_OBSERVATION
HUMAN_LATE_ROLLBACK_GATE=PASS_OWNER_EXECUTED_TARGETED_POSTGRESQL_TEST
FULL_INTEGRATION_VALIDATION=PASS_52_TESTS
```

## 21. Configuration combinée armée acceptée le 2026-08-23

Le parcours hors ligne doit cohabiter avec la configuration locale combinée utilisée pour préparer
J3, J4 phase 2, J5 et la découverte tournoi. Lorsque `SOFASCORE_ENABLED=true`, les opt-ins, la base
URL et l'allowlist peuvent rendre les politiques manuelles réelles éligibles sans constituer des
motifs de blocage du lot. Le lot ne possède toujours aucune dépendance vers le transport, le cache
fournisseur ou le coordinateur.

Des tests de binding Spring reprennent exactement cette configuration ; un test du service de
contrôle prépare un plan et atteint `AWAITING_CONFIRMATION`. Basculer le verrou maître entre
`false` et `true` ne change pas l'éligibilité du lot. Les automatisations actives et la conservation
brute désactivée restent bloquantes. La validation runtime avec `SOFASCORE_ENABLED=true` confirme
que `/j5-import-batches` répond HTTP 200 sur `127.0.0.1:8091`, expose la recherche et la préparation
locales ainsi que le badge `ZÉRO APPEL FOURNISSEUR`, sans verrouillage ni bloqueur lié au connecteur.

```text
COMBINED_ENABLED_CONFIGURATION_GATE=PASS
SOFASCORE_ENABLED_COMPATIBILITY=TRUE_AND_FALSE
REAL_J3_J4_J5_TOURNAMENT_POLICIES=MAY_BE_AVAILABLE
OFFLINE_BATCH_TRANSPORT_DEPENDENCY=NONE
OFFLINE_BATCH_PROVIDER_CALLS=0
RUNTIME_HTTP_VALIDATION=PASS_ENABLED_TRUE_127_0_0_1_8091
STANDARD_VALIDATION=PASS_614_TESTS_2_SKIPPED
INTEGRATION_VALIDATION=PASS_52_TESTS
AUTOMATED_PREFLIGHT_DB_COUNTS_GATE=PASS_TESTCONTAINERS_JDBC_BEFORE_EQUALS_AFTER
HUMAN_LATE_ROLLBACK_GATE=PASS_OWNER_EXECUTED_TARGETED_POSTGRESQL_TEST
OWNER_VALIDATION_DECISION=GRANTED_2026_08_23
```

Les deux preuves d'atomicité sont désormais automatisées et exécutables par Maven/Testcontainers,
sans pgAdmin ni client `psql`. La décision explicite du propriétaire du 2026-08-23 autorise le
passage à `VALIDATED` et la clôture du Work Order.

## 22. Qualification propriétaire de l'import avec configuration armée du 2026-08-23

Le propriétaire a préparé puis exécuté un lot de trois rencontres pendant que la configuration
combinée restait armée avec `SOFASCORE_ENABLED=true`. Le plan attendait neuf preuves ; le navigateur
a reconnu exactement neuf fichiers, zéro déclaration 404 et neuf saisies sur neuf. La phrase exacte
et l'acquittement ont permis l'import sans apparition d'un bloqueur lié au connecteur maître.

La demande `fd7b7833-5d5c-464c-bcf8-d7a2aa26e90b` termine `COMPLETED`, avec un contrôle
`COMPLETED_LOCKED`, trois rencontres planifiées, neuf preuves JSON locales et 450 124 octets
contrôlés. Les compteurs fournisseur, cache fournisseur, coordinateur et retry automatique restent
tous à zéro.

Les neuf familles terminent `COMPLETE · 100%`. Hull City - Manchester United crée les snapshots
526/527/528 et observations 270/271/272. Saint-Etienne - Grenoble Foot 38 crée les snapshots
529/530/531 et observations 273/274/275. Nantes - Rodez AF réutilise les snapshots 490/491/492 et
observations 234/235/236, tous marqués `DEDUPLIQUEE`. Le résultat comporte donc six nouvelles
versions et trois déduplications.

```text
OWNER_COMBINED_ENABLED_IMPORT_REQUEST=fd7b7833-5d5c-464c-bcf8-d7a2aa26e90b
OWNER_COMBINED_ENABLED_IMPORT_GATE=PASS
OWNER_COMBINED_ENABLED_EVENT_COUNT=3
OWNER_COMBINED_ENABLED_LOCAL_FILES=9
OWNER_COMBINED_ENABLED_DECLARED_404=0
OWNER_COMBINED_ENABLED_TOTAL_BYTES=450124
OWNER_COMBINED_ENABLED_FAMILY_RESULTS=9_COMPLETE_100_PERCENT
OWNER_COMBINED_ENABLED_NEW_VERSIONS=6
OWNER_COMBINED_ENABLED_DEDUPLICATED_OBSERVATIONS=3
OWNER_COMBINED_ENABLED_PROVIDER_CALLS=0
OWNER_COMBINED_ENABLED_PROVIDER_CACHE_OPERATIONS=0
OWNER_COMBINED_ENABLED_COORDINATOR_ACQUISITIONS=0
OWNER_COMBINED_ENABLED_AUTOMATIC_RETRIES=0
HUMAN_OFFLINE_QUALIFICATION=PASS_INCLUDING_COMBINED_ENABLED_RUNTIME_IMPORT
OWNER_VALIDATION_DECISION=GRANTED_2026_08_23
```

Cette campagne ferme la qualification humaine de compatibilité du lot avec la configuration armée.
Le propriétaire autorise ensuite explicitement son passage à `VALIDATED` et son déplacement vers
`docs/work_orders/completed/`.

## 23. Qualification et clôture propriétaire du 2026-08-23

Après examen de la totalité des preuves consignées, le propriétaire autorise explicitement le
passage du Work Order au statut `VALIDATED`, son déplacement vers `completed`, puis la publication
de la branche par commit, Pull Request et fusion vers `main` en l'absence de conflit.

Cette clôture n'autorise aucun appel fournisseur supplémentaire, n'active aucun polling, live,
scheduler ou retry et ne modifie pas les statuts expérimentaux du laboratoire. Le circuit validé
reste local, synchrone, sans dépendance critique et sans transport fournisseur.

```text
IMPLEMENTATION=COMPLETED
TECHNICAL_READINESS=PASS_614_STANDARD_TESTS_2_SKIPPED_AND_52_POSTGRESQL_TESTS
HUMAN_OFFLINE_QUALIFICATION=PASS_INCLUDING_COMBINED_ENABLED_RUNTIME_IMPORT
OWNER_QUALIFICATION=PASS
QUALIFICATION_DATE=2026-08-23
CLOSURE_AUTHORIZED=YES
PROVIDER_CALLS_DURING_IMPLEMENTATION=0
PROVIDER_CALLS_DURING_AUTOMATED_TESTS=0
ADDITIONAL_PROVIDER_CALL_AUTHORIZED_BY_CLOSURE=NO
WORK_ORDER_STATUS=VALIDATED
WORK_ORDER_LOCATION=docs/work_orders/completed
```

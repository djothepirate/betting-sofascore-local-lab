# Readiness technique J5 — import hors ligne multi-match atomique

## 1. Statut et clôture au 2026-08-23

```text
REPORT_DATE=2026-08-22
LAST_UPDATE_DATE=2026-08-23
WORK_ORDER=WO-SS-20260822-010
WORK_ORDER_STATUS=VALIDATED
WORK_ORDER_LOCATION=docs/work_orders/completed
BASE_COMMIT=47968ed98d5795f37f4e40f6e6d61519494526b4
BRANCH=codex/j5-offline-multi-match-batch
OWNER_AUTHORIZATION=GRANTED_IN_CURRENT_CHAT_ON_2026-08-22
CONTRACT_READINESS=READY_FOR_IMPLEMENTATION
IMPLEMENTATION_STATUS=NOT_STARTED_AT_REPORT_CREATION
CURRENT_IMPLEMENTATION_STATUS=VALIDATED_AND_CLOSED
AUTOMATED_VALIDATION=PASS_614_TESTS_2_SKIPPED
INTEGRATION_VALIDATION=PASS_52_TESTS
HUMAN_OFFLINE_QUALIFICATION=PASS_INCLUDING_COMBINED_ENABLED_RUNTIME_IMPORT
PROVIDER_CALLS_DURING_READINESS=0
TARGET_AFTER_IMPLEMENTATION=IMPLEMENTED_AWAITING_HUMAN_QUALIFICATION
OWNER_CLOSURE_AUTHORIZATION=GRANTED_2026_08_23
```

Ce rapport conserve l'état initial de readiness et porte maintenant les preuves automatisées et
humaines de l'implémentation. Après les campagnes nominales, les scénarios 404, la requalification
multi-match, les preuves SQL et l'import avec la configuration combinée armée, le propriétaire a
autorisé explicitement la qualification et la clôture le 2026-08-23. Cette décision n'autorise
aucun appel fournisseur supplémentaire.

Références contractuelles :

- `docs/work_orders/completed/WO-SS-20260822-010-j5-offline-multi-match-import.md` ;
- `docs/requirements/J5-OFFLINE-MULTI-MATCH-IMPORT-RULES.md` ;
- `docs/architecture/J5-OFFLINE-MULTI-MATCH-IMPORT.md`.

## 2. Besoin confirmé

Le flux J5 unitaire initial exigeait une préparation et trois fichiers par rencontre. La recette a
montré qu'un navigateur ne permet pas toujours de télécharger le corps JSON d'un HTTP 404. Le besoin
validé est de grouper la sélection, la validation et l'écriture, tout en autorisant une déclaration
404 fermée à la place du fichier concerné, sans automatiser aucune récupération.

La cible est donc :

```text
1..25 événements canoniques locaux
3 preuves exactes par événement
0..75 fichiers + 0..75 déclarations 404, somme exacte 3N
5 Mio maximum par fichier
25 Mio maximum par lot
0 appel fournisseur
1 transaction atomique pour les 3N éléments
```

## 3. État du socle observé

L'analyse de la base `47968ed98d5795f37f4e40f6e6d61519494526b4` établit les préconditions
suivantes :

| Capacité existante | État | Conséquence pour WO-010 |
|---|---|---|
| recherche canonique date/zone | disponible | réutilisable pour une sélection d'UUID |
| lecture de l'observation canonique courante | disponible | permet de figer et revalider le plan |
| parseur statistiques courant | `event-statistics-v2` | réutilisable sans règle métier nouvelle |
| parseur incidents courant | `event-incidents-v13` | réutilisable sans règle métier nouvelle |
| parseur compositions courant | `event-lineups-v2` | réutilisable sans règle métier nouvelle |
| capture brute bornée | 5 Mio et scanner sensible | réutilisable par chaque partie |
| import local J5 unitaire | prévalidation des trois familles | référence, mais contrôle réel à découpler |
| provenance V25 | `MANUAL_LOCAL_JSON_IMPORT` | suffisante, aucune migration requise |
| occurrences et historique J6 | disponibles | suffisants pour les réimports committés |
| sélection courante J7 | disponible | consomme les observations sans batch durable |
| multipart Spring/Tomcat | 6 MB/fichier, 32 MB/requête, seuil mémoire 32 MB, 82 parties | 75 preuves + 6 contrôles + sentinelle fichier vide, bornés sans staging |
| liaison serveur | `127.0.0.1` | doit rester inchangée |

La branche demandée `codex/j5-offline-multi-match-batch` est active et le worktree était propre
avant la création des quatre documents du Work Order.

## 4. Écart principal à traiter

`J5LocalJsonImportService` réutilise aujourd'hui `J5RealControlService`. Ce couplage convient au
choix exclusif d'une campagne unitaire réelle ou locale, mais pas au nouveau lot qui doit rester
opérationnel quel que soit l'état du verrou maître, y compris lorsque les paramètres des campagnes
manuelles fournisseur sont armés.

La readiness impose donc :

- un `J5OfflineBatchControlService` propre ;
- aucun appel, lecture ou mutation de `J5RealControlService` ;
- aucun transport, cache fournisseur, coordinateur ou `ConnectorGate` dans les constructeurs du
  nouveau flux ;
- un service transactionnel distinct englobant tous les `3N` éléments ;
- la réutilisation des parseurs, stores et contrats de provenance, sans dupliquer les règles métier.

L'import unitaire existant ne doit pas être supprimé ou silencieusement redirigé vers le lot.

## 5. Revue ADR-SS-001

### 5.1 Verdict

```text
NEW_PROVIDER_ENDPOINT=NO
ENDPOINT_URI_CHANGE=NO
ENDPOINT_CATALOG_CALLABLE_CHANGE=NO
CONNECTOR_GATE_CHANGE=NO
REAL_NETWORK_CALL=NO
SCHEDULED_OR_LIVE_ACTIVITY=NO
ADR_CHANGE_REQUIRED=NO
ADR_EXPLICIT_REVIEW=PASS_FOR_STRICT_OFFLINE_SCOPE
```

Le flux transforme des fichiers locaux fournis par l'opérateur en snapshots et observations. Il
n'ajoute pas un endpoint réel au sens de l'ADR et ne rend pas appelables les types logiques J5.
La revue est positive seulement pour la portée fermée décrite dans le Work Order.

### 5.2 Déclencheurs d'une nouvelle décision

Un nouveau Work Order et une revue ADR sont obligatoires avant toute tentative d'ajouter :

- récupération HTTP des fichiers ou résolution d'une URI ;
- polling, scheduler, watcher de répertoire ou tâche en arrière-plan ;
- retry automatique, proxy, cookie, jeton, header navigateur ou cache fournisseur ;
- exécution lorsque `sofascore.enabled=true` ou un jalon réseau est armé ;
- nouveau type d'endpoint fournisseur ou modification du catalogue.

Une reprise persistante, une table de batch ou une migration exigeraient au minimum un nouveau Work
Order, même en restant hors ligne.

## 6. Contrat technique prêt à implémenter

### 6.1 Sélection et plan

```text
SELECTION_SOURCE=current canonical observations for date+zone
SELECTION_INPUT=canonical UUIDs only
SELECTION_COUNT=1..25
TIME_WINDOW=[local day start, next local day start) converted to UTC
PLAN_TTL=15 minutes
PLAN_STORE=process memory only
EVENT_ORDER=providerEventId numeric ascending
FAMILY_ORDER=statistics,incidents,lineups
PLAN_HASH=j5-offline-multi-match-plan-v1 SHA-256 canonical binary content
```

Le hash est déterministe par contenu. Il couvre la version, la date, la zone, les bornes UTC et,
pour chaque événement trié, l'UUID canonique, le `providerEventId`, l'observation courante,
`startsAt`, le hash canonique courant et les trois noms attendus. Il exclut `requestId`,
`preparedAt` et `expiresAt`.

### 6.2 Confirmation

```text
LOCAL_FORM_TOKEN=required and single-use
ACKNOWLEDGEMENT=required true
EXACT_PHRASE=IMPORTER {N} MATCHS J5 HORS LIGNE {PLAN_SHA256}
COMPARISON=constant-time exact UTF-8
CLAIM=only after all evidence slots and canonical state pass prevalidation
```

### 6.3 Multipart

```text
FILE_PART_NAME=batchFiles
DECLARATION_PART_NAME=unavailable404
FILE_REGEX=event-([1-9][0-9]{0,18})-(statistics|incidents|lineups).json
EVIDENCE_SET=exactly the plan manifest
EVIDENCE_SLOT=JSON_FILE_XOR_EXPLICIT_404_DECLARATION
FILE_COUNT=0..3N
DECLARATION_COUNT=0..3N
FILE_COUNT_PLUS_DECLARATION_COUNT=3N
MAX_FILE_BYTES=5242880
MAX_BATCH_BYTES=26214400
ARCHIVE=forbidden
STAGING=forbidden
```

### 6.4 Écriture

```text
PREVALIDATION_WRITE_COUNT=0
TRANSACTION_COUNT=1
TRANSACTION_SCOPE=all 3N raw and normalized operations
ACQUISITION_MODE=MANUAL_LOCAL_JSON_IMPORT
LOCAL_HTTP_STATUS=200 parsed or 404 closed/imported-or-declared envelope
PROVIDER_CALLS=0
CACHE_INTERACTIONS=0
COORDINATOR_ACQUISITIONS=0
```

## 7. Politique de configuration à prouver

Toutes les opérations du nouveau flux exigent les trois barrières actives suivantes :

```text
SOFASCORE_AUTOMATIC_REFRESH_ENABLED=false
SOFASCORE_LIVE_POLLING_ENABLED=false
SOFASCORE_STORE_RAW_PAYLOADS=true
```

`SOFASCORE_ENABLED` peut valoir `false` ou `true`. La régression de référence reprend exactement le
connecteur et les cinq opt-ins à `true`, l'origine `https://www.sofascore.com` et l'union des six
endpoints : le lot doit rester disponible même lorsque les politiques manuelles réelles sont
simultanément éligibles.

La préparation et l'import doivent échouer fermés dès qu'une barrière active est fausse. Ce test
est répété avant le claim et à l'entrée de la transaction pour couvrir une dérive de configuration
ou de bean entre deux étapes.

## 8. Matrice automatisée exécutée

La matrice est couverte par les tests unitaires, MVC et PostgreSQL exécutés le 2026-08-22. Les
cas `T02` et `T04` empruntent la même frontière transactionnelle contrôlée que `T03` ; `T03`
apporte en plus une preuve PostgreSQL tardive sur le dernier élément du lot.

| ID | Domaine | Scénario | Preuve attendue | Statut |
|---|---|---|---|---|
| P01 | plan | 1 événement courant | 3 fichiers attendus, hash v1 | `PASS` |
| P02 | plan | 25 événements courants | 75 fichiers ordonnés | `PASS` |
| P03 | plan | 0 ou 26 sélections | refus sans plan partiel | `PASS` |
| P04 | plan | UUID doublon/inconnu/hors fenêtre | refus fermé | `PASS` |
| P05 | plan | même sélection dans un autre ordre | même ordre par provider ID | `PASS` |
| P06 | plan | mutation de chaque champ hashé, puis nouveau requestId/TTL | hash différent, puis stable | `PASS` |
| P07 | temps | avant, à et après 15 minutes | attente puis `EXPIRED_LOCKED` | `PASS` |
| P08 | temps | journée DST courte/longue | bornes UTC exactes | `PASS` |
| P09 | cycle | chaque état terminal puis prepare | nouveau plan accepté | `PASS` |
| M01 | multipart | lot nominal mélangé | rapprochement et ordre serveur | `PASS` |
| M02 | multipart | contrôle/fichier manquant, extra, inconnu ou doublon | refus avant claim/écriture | `PASS` |
| M03 | noms | chemin, casse, suffixe, zéro initial, `filename*` encodé | refus sur disposition brute avant capture utile | `PASS` |
| M04 | noms | identifiant supérieur à `Long.MAX_VALUE` | refus sans overflow | `PASS` |
| M05 | tailles | 5 Mio exacts | accepté si lot borné et JSON valide | `PASS` |
| M06 | tailles | fichier >5 Mio ou lot >25 Mio | refus avant claim | `PASS` |
| M07 | sécurité | contenu sensible dans le dernier fichier | zéro claim, zéro écriture | `PASS` |
| M08 | preuves | deux fichiers + déclaration 404 | union exacte transmise au processeur | `PASS` |
| M09 | preuves | fichier + déclaration du même nom, ou preuve absente | refus avant claim | `PASS` |
| M10 | navigateur | zéro fichier et trois déclarations avec sentinelle vide | import accepté, sentinelle ignorée | `PASS` |
| J01 | JSON | trois payloads compatibles | prévalidation complète | `PASS` |
| J02 | JSON | JSON invalide, clé dupliquée, trailing token | refus fermé | `PASS` |
| J03 | 404 | enveloppe fermée code entier 404 | indisponibilité explicite | `PASS` |
| J04 | 404 | 403, `"404"`, champ racine/erreur extra | refus fermé | `PASS` |
| C01 | claim | token absent/invalide/réutilisé | refus Web | `PASS` |
| C02 | claim | phrase ou hash divergent | plan non réclamé | `PASS` |
| C03 | claim | acquittement absent | plan non réclamé | `PASS` |
| C04 | claim | deux requêtes concurrentes | un claim au maximum | `PASS` |
| C05 | contrôle | campagne réelle active | aucune lecture/mutation croisée | `PASS` |
| R01 | revalidation | observation remplacée avant claim | `PLAN_CHANGED`, plan encore en attente, zéro écriture | `PASS` |
| R02 | revalidation | changement entre claim et transaction | rollback, `FAILED_LOCKED` | `PASS` |
| T01 | transaction | lot nominal N | commit des `3N` traitements | `PASS` |
| T02 | transaction | panne brute au milieu | rollback intégral | `PASS` |
| T03 | transaction | panne normalisée au dernier élément | rollback intégral | `PASS` |
| T04 | transaction | panne de classification | rollback intégral | `PASS` |
| D01 | données | provenance et clés | mode/import, clés J5 exactes | `PASS` |
| D02 | données | réimport et snapshot dédupliqué `RAW_ONLY` | reclassification finale et occurrences J6 | `PASS` |
| D03 | données | même payload acquis directement | provenance distincte | `PASS` |
| D04 | données | lecture J6/J7 après commit | données visibles sans batch durable | `PASS` |
| N01 | réseau | nominal, 404 et erreur | transport/cache/coordinator = 0 | `PASS` |
| N02 | config | connecteur, cinq opt-ins, origine et six endpoints armés | lot disponible, voies manuelles éventuellement disponibles | `PASS` |
| N03 | config | automatisation active ou brut coupé | préparation et import refusés | `PASS` |
| N04 | source | scheduler/watcher/client/retry | aucune introduction | `PASS` |
| W01 | Web | vues et redirections | no-store/noindex | `PASS` |
| W02 | Web | erreur de nom ou JSON | aucun nom/payload reflété | `PASS` |

## 9. Invariants de persistance

Les tests d'intégration devront vérifier au niveau SQL ou des stores :

1. `3N` opérations réussies appartiennent à un seul commit ;
2. sur échec tardif, le nombre de snapshots, occurrences, observations J5 et enfants revient à
   sa valeur antérieure ;
3. chaque snapshot importé porte `MANUAL_LOCAL_JSON_IMPORT` ;
4. chaque clé vaut exactement `EVENT_<FAMILY>|eventId=<providerEventId>` selon les constantes ;
5. le payload conservé possède les octets et le SHA-256 du fichier ou de l'enveloppe canonique ;
6. la source normalisée conserve snapshot, hash, version de parseur et heure de réception ;
7. l'enveloppe 404 importée ou déclarée produit le statut et la complétude indisponible existants ;
8. un import et une acquisition directe ne se dédupliquent pas comme la même acquisition ;
9. aucune table, colonne, séquence ou migration de lot n'est créée.

## 10. Vérifications source et configuration

Les contrôles suivants devront faire partie de la revue :

- aucun `@Scheduled`, `@EnableScheduling`, `WatchService`, `RestClient`, client HTTP, retry ou
  stockage temporaire dans les fichiers ajoutés ;
- aucune dépendance vers le transport J5, le cache fournisseur ou le coordinateur ;
- aucune dépendance du contrôle offline vers `J5RealControlService` ;
- aucun champ `eventId` libre dans le formulaire de préparation ;
- aucune valeur réelle ajoutée au catalogue d'endpoints ;
- valeurs par défaut réseau inchangées et `server.address=127.0.0.1` ;
- multipart Spring inchangé et borne Tomcat limitée à 82 parties, relèvement d'une unité justifié
  uniquement par la sentinelle fichier vide du navigateur ;
- toutes les parties natives énumérées, six contrôles uniques et `Content-Disposition` ASCII
  canonique contrôlé avant le nom décodé ;
- absence de payload réel, cookie, token ou `.env` dans Git ;
- aucune migration nouvelle et aucune modification d'une migration existante.

## 11. Commandes de validation à exécuter

Après implémentation, mais avant le changement de statut du Work Order :

```powershell
.\mvnw.cmd clean verify
.\mvnw.cmd -Pintegration-tests verify
.\scripts\Verify-Local.ps1 -WithIntegrationTests
```

État initial à la création de cette readiness :

```text
MVNW_CLEAN_VERIFY=NOT_RUN
MVNW_INTEGRATION_VERIFY=NOT_RUN
VERIFY_LOCAL_WITH_INTEGRATION_TESTS=NOT_RUN
DIFF_SECRET_SCAN=PENDING
SERVER_BIND_CHECK=PENDING
DEFAULT_NETWORK_FLAGS_CHECK=PENDING
SOURCE_AUTOMATION_GUARD_CHECK=PENDING
```

Résultats initiaux puis correctifs obtenus le 2026-08-22 :

```text
ORIGINAL_MVNW_CLEAN_VERIFY=PASS_598_TESTS_2_SKIPPED
CORRECTIVE_TARGETED_TESTS=PASS_64_TESTS
CORRECTIVE_MVNW_CLEAN_VERIFY=PASS_609_TESTS_2_SKIPPED
CORRECTIVE_MVNW_INTEGRATION_VERIFY=PASS_609_STANDARD_51_INTEGRATION
MANIFEST_ASSISTANCE_WEB_SECURITY_TESTS=PASS_22_TESTS
MANIFEST_ASSISTANCE_MVNW_CLEAN_VERIFY=PASS_612_TESTS_2_SKIPPED
MANIFEST_ASSISTANCE_MVNW_INTEGRATION_VERIFY=PASS_612_STANDARD_51_INTEGRATION
VERIFY_LOCAL_WITH_INTEGRATION_TESTS=PASS
DIFF_SECRET_SCAN=PASS_SYNTHETIC_TEST_SENTINELS_ONLY
SERVER_BIND_CHECK=PASS_127_0_0_1
DEFAULT_NETWORK_FLAGS_CHECK=PASS_ALL_DISARMED
SOURCE_AUTOMATION_GUARD_CHECK=PASS
DATABASE_MIGRATION_CHANGE=NONE
SOFASCORE_NETWORK_CALLS_EXECUTED=NO
LIVE_OFFLINE_BATCH_UI_DESKTOP=PASS
LIVE_OFFLINE_BATCH_UI_MOBILE_375PX=PASS_NO_HORIZONTAL_OVERFLOW
LIVE_OFFLINE_BATCH_UI_CONSOLE_ERRORS=0
LIVE_MANIFEST_FILE_AND_404_CONFLICT=PASS_BLOCKED_WITH_EXACT_FILE_NAME
LIVE_MANIFEST_DUPLICATE_NAME=PASS_BLOCKED_WITH_EXACT_FILE_NAME
LIVE_MANIFEST_MIXED_EXACT=PASS_FILE_404_404_IMPORT_ENABLED
ORIGINAL_MULTIPART_TOMCAT_81_PARTS=PASS
ORIGINAL_MULTIPART_TOMCAT_82_PARTS=PASS_REJECTED_WITH_BOUNDED_PRG
CORRECTIVE_MULTIPART_TOMCAT_MAX_PART_COUNT=82_CONFIGURED
JAVA_RUNTIME=25.0.4
POSTGRESQL_TESTCONTAINERS=18.4
FLYWAY_MIGRATIONS=25_TO_V25
```

Les seules occurrences ressemblant à des secrets dans les fichiers ajoutés sont les sentinelles
synthétiques `Cookie: secret` et `Bearer super-secret-value`, utilisées par les tests pour prouver
le refus ou la non-réflexion d'un contenu sensible. Aucun secret, payload réel ou fichier `.env`
n'est suivi. Une réussite automatisée n'autorise toujours aucun appel fournisseur.

## 12. Recette humaine hors ligne prévue

La recette propriétaire minimale peut se dérouler avec le connecteur maître activé :

1. rechercher une date et une zone contenant au moins deux événements canoniques ;
2. sélectionner ces événements sans saisir d'identifiant fournisseur ;
3. préparer le plan, vérifier tri, `3N` noms, expiration et hash ;
4. fournir un lot nominal incluant au moins une déclaration 404 fermée sans fichier ;
5. recopier la phrase exacte, cocher l'acquittement et exécuter ;
6. vérifier l'état du contrôle `COMPLETED_LOCKED`, le résultat `terminalCode=COMPLETED`, les vues
   J5/J6 et les compteurs réseau tous nuls ;
7. préparer un nouveau plan et fournir un lot invalide pour constater zéro écriture ;
8. provoquer ou simuler une panne tardive et vérifier le rollback global ;
9. préparer un troisième plan, réimporter le lot nominal et vérifier déduplication et occurrences ;
10. redémarrer l'application et confirmer qu'aucun plan ou résultat de lot n'est repris.

La recette utilise exclusivement des fixtures synthétiques ou des corps JSON manuellement remis
et autorisés. Elle ne demande pas à l'application de les télécharger.

### 12.1 Preuve humaine nominale acquise

Le 2026-08-22, le propriétaire a préparé puis exécuté le plan
`18c516f9-50e1-42e3-b086-d14d6f563955` pour Olympique de Marseille — RC Strasbourg
(`providerEventId=16310922`). Le plan d'une rencontre et trois corps porte le SHA-256
`58154c6b03af7e36a36a05cb8551b7fcc4bd8bd0d26a8f46ba27a1832dd195a4`.

La preuve d'écran terminale indique `COMPLETED`, trois imports JSON locaux, 146 706 octets
contrôlés, zéro appel fournisseur, zéro opération de cache fournisseur, zéro acquisition du
coordinateur et zéro retry automatique. Les vues J5 affichent ensuite :

- statistiques snapshot 417 / observation 222, `COMPLETE · 100% · 262/262` ;
- incidents snapshot 418 / observation 223, `COMPLETE · 100% · 92/92`, 23 incidents ordonnés ;
- compositions snapshot 419 / observation 224, `COMPLETE · 100% · 85/85`, confirmées.

La relecture SQL locale limitée aux métadonnées confirme pour les trois lignes
`MANUAL_LOCAL_JSON_IMPORT`, HTTP 200, `PARSED`, les parseurs
`event-statistics-v2` / `event-incidents-v13` / `event-lineups-v2`, ainsi qu'une occurrence
`INSERTED` par snapshot. Aucun payload brut n'a été copié dans la documentation.

Un premier essai avait auparavant produit `FILE_NAME_INVALID`; le plan est resté disponible et la
campagne suivante avec les noms canoniques a réussi. Cette séquence valide utilement le refus fermé
des noms puis le nominal, mais ne remplace pas les preuves avant/après exigées pour le rollback et
la déduplication.

```text
HUMAN_EVIDENCE_SINGLE_EVENT_NOMINAL=PASS
HUMAN_EVIDENCE_STRICT_FILE_NAME_REJECTION=OBSERVED
HUMAN_EVIDENCE_SINGLE_EVENT_404_WITH_MANUAL_FILE=PASS_WITH_FRICTION
HUMAN_EVIDENCE_404_DECLARATION_UI_RETEST=PASS
HUMAN_EVIDENCE_MULTI_EVENT_WITH_404=PASS
HUMAN_EVIDENCE_PREVALIDATION_ZERO_WRITE_COUNTS=PENDING
HUMAN_EVIDENCE_LATE_TRANSACTION_ROLLBACK=PENDING
HUMAN_EVIDENCE_REIMPORT_DEDUP_OBSERVATION=PASS_SAME_6_SNAPSHOTS_AND_OBSERVATIONS
HUMAN_EVIDENCE_REIMPORT_DEDUP_OCCURRENCES=PASS_2_PER_SNAPSHOT_INSERTED_THEN_DEDUPLICATED
HUMAN_EVIDENCE_RESTART_NO_RESUME=PENDING
```

### 12.2 Signal correctif : corps 404 non téléchargeable

Une campagne J5 locale sur Borussia Dortmund — FC Bayern München (`providerEventId=16248441`),
avant le début de la rencontre, a terminé avec trois imports et zéro appel fournisseur. Les écrans
fournis par le propriétaire établissent :

| Famille | Snapshot | Observation | Octets | Complétude |
|---|---:|---:|---:|---|
| `EVENT_STATISTICS` | 473 | 225 | 44 | `UNAVAILABLE · N/A` |
| `EVENT_INCIDENTS` | 474 | 226 | 422 | `EMPTY_VALID · 100%` |
| `EVENT_LINEUPS` | 475 | 227 | 29 450 | `PARTIAL · 97%` |

Le navigateur n'offrait pas de téléchargement direct du corps JSON 404. Le contrat trois fichiers
a donc contraint le propriétaire à recopier ce corps et à créer manuellement un fichier. Le verdict
de données est correct, mais l'ergonomie est insuffisante pour les journées chargées. La correction
introduit une case par famille et le payload canonique local marqué
`LOCAL_OPERATOR_DECLARED_HTTP_404`, avec exclusivité fichier XOR déclaration. Sa recette humaine
multi-match est consignée ci-dessous.

### 12.3 Requalification multi-match et refus contradictoire

Le propriétaire a terminé la demande `465f6849-e585-4b3f-9182-a21f91770dea` sur quatre rencontres
avec neuf fichiers et trois déclarations 404. La preuve terminale affiche douze imports locaux,
363 863 octets contrôlés, `COMPLETED` et zéro appel fournisseur, cache, coordinateur ou retry.

Les lignes visibles confirment notamment les snapshots 484/485/486 de RC Lens — Auxerre en
`COMPLETE · 100%`, ainsi que les snapshots 487/489 de Saint-Étienne — Grenoble Foot 38 en
`UNAVAILABLE · N/A` autour du snapshot incidents 488 `EMPTY_VALID · 100%`. Les vues normalisées
n'inventent aucun signal pour les familles indisponibles.

Un autre plan de quinze preuves a ensuite été présenté avec douze fichiers et au moins quatre
déclarations. Le refus `DUPLICATE_FILE` est conforme au contrat mais le sélecteur natif ne montrait
pas le nom en intersection. Le correctif d'assistance rapproche désormais avant envoi les seuls
noms et tailles, affiche les états par preuve et désactive le bouton jusqu'à l'union exacte. Cette
couche ne lit aucun octet et ne remplace jamais la prévalidation serveur.

```text
HUMAN_MULTI_EVENT_COUNT=4
HUMAN_MULTI_EVENT_LOCAL_JSON_IMPORTS=12
HUMAN_MULTI_EVENT_DECLARED_404=3
HUMAN_MULTI_EVENT_TOTAL_BYTES=363863
HUMAN_MULTI_EVENT_PROVIDER_CALLS=0
HUMAN_MULTI_EVENT_PROVIDER_CACHE_OPERATIONS=0
HUMAN_MULTI_EVENT_COORDINATOR_ACQUISITIONS=0
HUMAN_MULTI_EVENT_AUTOMATIC_RETRIES=0
HUMAN_MANIFEST_CONTRADICTION=DUPLICATE_FILE_OBSERVED
MANIFEST_ASSISTANCE=IMPLEMENTED
MANIFEST_ASSISTANCE_BROWSER_VALIDATION=PASS_NO_SUBMISSION
```

Le contrôle live a utilisé uniquement des JSON synthétiques placés dans `target/` et n'a jamais
soumis le formulaire. Il a observé successivement : `CONFLIT` pour un fichier accompagné de sa
déclaration 404, `DOUBLON` pour deux fichiers de même nom, puis `FICHIER / 404 / 404` et un bouton
réactivé après correction. À 1366 × 900 et 375 × 812, la largeur du document reste égale à la
largeur cliente ; la console navigateur ne contient aucune erreur. Le plan local de test a ensuite
été annulé sans lecture ni persistance des fichiers. La CSP de même origine a enfin été vérifiée
sur la route simple et sur `/j5-import-batches;jsessionid=...`, sans autoriser de script ailleurs.

### 12.4 Qualification propriétaire du parcours d'import multi-match

Le propriétaire déclare les tests d'import multi-match J5 concluants. La campagne qualifiée porte
sur Borussia Dortmund — FC Bayern München et Saint-Étienne — Grenoble Foot 38 : quatre fichiers
et deux déclarations 404 ont formé un manifeste exact de six preuves sur six. La demande
`7ecf9176-79c4-49bf-89cd-5a39e5ce3634` est ensuite parvenue à `COMPLETED` avec 106 210 octets
contrôlés et zéro appel fournisseur, opération cache fournisseur, acquisition coordinateur ou
retry automatique.

La preuve terminale montre trois observations `NOUVELLE_VERSION` et trois observations
`DÉDUPLIQUÉE`. Les statistiques et compositions déclarées indisponibles restent `UNAVAILABLE ·
N/A`, les listes explicitement vides restent `EMPTY_VALID · 100%` et les compositions disponibles
sont rendues sans perte visible. Sur un autre plan à six preuves, l'interface a aussi nommé les six
preuves manquantes, puis ramené ce nombre à cinq et marqué la ligne `404` dès la première
déclaration. L'assistance de manifeste et le parcours d'import sont donc acceptés humainement.

```text
OWNER_MULTI_MATCH_IMPORT_ASSESSMENT=CONCLUSIVE
OWNER_QUALIFIED_REQUEST=7ecf9176-79c4-49bf-89cd-5a39e5ce3634
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
AUTOMATED_PREFLIGHT_DB_COUNTS_GATE=PASS_TESTCONTAINERS_JDBC_BEFORE_EQUALS_AFTER
HUMAN_LATE_ROLLBACK_GATE=PASS_OWNER_EXECUTED_TARGETED_POSTGRESQL_TEST
HUMAN_REIMPORT_OCCURRENCE_COUNT_GATE=PASS_SQL_READ_ONLY_2_PER_SNAPSHOT
HUMAN_RESTART_NO_RESUME_GATE=PASS_BY_OWNER_EVIDENCE
OWNER_VALIDATION_DECISION=GRANTED_2026_08_23
```

Les libellés de déduplication constituent une preuve fonctionnelle du réemploi des snapshots, mais
les captures ne montrent pas les comptes SQL d'occurrences avant/après. Ce contrôle et les autres
portes de résilience restent donc distincts de l'acceptation du parcours métier.

### 12.5 Refus local d'un manifeste contradictoire et reprise

Une séquence propriétaire supplémentaire a exercé le plan d'une rencontre Saint-Étienne —
Grenoble Foot 38. Avec trois fichiers et deux déclarations 404, l'assistance a identifié le nom hors
plan `event-16363634-statistics.json` ainsi que le conflit fichier/déclaration sur
`event-16386251-incidents.json`. Le résumé indiquait cinq saisies pour trois preuves, une anomalie
de nom et un conflit ; le bouton d'import est resté désactivé.

Après remplacement par les trois fichiers attendus, sur le même plan et le même SHA-256, les trois
lignes sont passées à `FICHIER`, le manifeste à trois saisies sur trois et le bouton à l'état actif.
L'import confirmé a ensuite réussi pour une rencontre, trois preuves JSON locales, aucune
déclaration 404 et zéro appel fournisseur.

```text
OWNER_MANIFEST_UNEXPECTED_NAME_DETECTION=PASS
OWNER_MANIFEST_FILE_404_CONFLICT_DETECTION=PASS
OWNER_MANIFEST_INVALID_IMPORT_BUTTON=DISABLED
OWNER_MANIFEST_CORRECTION_WITHIN_SAME_PLAN=PASS
OWNER_MANIFEST_CORRECTED_EXACT_STATE=3_FILES_0_DECLARED_404_3_OF_3
OWNER_POST_CORRECTION_IMPORT=PASS_1_EVENT_3_LOCAL_PROOFS_0_PROVIDER_CALLS
OWNER_MANIFEST_CONTRADICTION_AND_RECOVERY_GATE=PASS
LOCAL_UI_PREFLIGHT_DB_COUNTS_MEASURED=NO
AUTOMATED_PREFLIGHT_DB_COUNTS_GATE=PASS_TESTCONTAINERS_JDBC_BEFORE_EQUALS_AFTER
```

La séquence qualifie la prévention humaine des erreurs et leur correction avant envoi. Elle ne
mesure aucune table avant et après un rejet serveur, précisément parce que la soumission invalide
a été empêchée localement. La preuve SQL distincte est apportée par le test PostgreSQL/Testcontainers
décrit en section 12.9, qui compare les comptes JDBC avant et après le refus serveur.

### 12.6 Annulation opérateur avant import

Le propriétaire a annulé un plan d'une rencontre avant de fournir un corps JSON. La demande
`7b929722-2808-41c2-b20f-24dd1c61b5ce` termine en `OPERATOR_STOP` ; le bandeau confirme qu'aucun
corps JSON n'a été persisté et la preuve terminale ne contient aucun résultat par famille.

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
HUMAN_RESTART_NO_RESUME_GATE=PENDING_AT_CANCEL_STEP
```

La branche d'annulation explicite est donc qualifiée humainement. À cette étape, le redémarrage
ultérieur du processus restait une preuve séparée ; elle est consignée dans la section suivante.

### 12.7 Redémarrage sans reprise

Après le résultat terminal `OPERATOR_STOP`, l'application a été interrompue puis la page J5 a été
observée depuis un nouvel état de processus. Les événements canoniques persistés restent disponibles
dans la recherche locale, tandis qu'aucun plan actif, formulaire de confirmation ou dernier résultat
terminal n'est restitué. Le contrôle est recalculé en `LOCKED` depuis les conditions de garde de la
configuration courante.

Le processus de validation qui servait précédemment `127.0.0.1:8089` est terminé. Un contrôle local
postérieur aux captures confirme l'absence de listener sur ce port, cohérente avec l'absence de
toute reprise automatique ou tâche de fond après l'arrêt.

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

La porte de redémarrage est fermée positivement. À cette étape, les comptes SQL avant/après, le
rollback tardif et le comptage d'occurrences d'un réimport dédupliqué restaient ouverts ; ce dernier
est qualifié dans la section suivante.

### 12.8 Réimport dédupliqué et occurrences PostgreSQL

Deux imports successifs du même manifeste exact couvrent Borussia Dortmund — FC Bayern München et
RC Lens — Auxerre. La première demande `05d82794-a30a-4a41-8cd8-029d5bebac11` produit six
`NOUVELLE_VERSION`, snapshots 508 à 513 et observations 252 à 257. La seconde demande
`73bdbe38-c776-4ec3-8a40-234d53e51d81` réutilise les mêmes identifiants, tailles et empreintes avec
six résultats `DÉDUPLIQUÉE`.

Les deux exécutions terminent chacune `COMPLETED` pour deux rencontres, six fichiers, 318 884 octets
et zéro appel fournisseur, opération cache, acquisition coordinateur ou retry. Une requête JDBC
PostgreSQL en lecture seule confirme ensuite deux occurrences par snapshot dans
`provider_snapshot_occurrence`, exactement une `INSERTED` puis une `DEDUPLICATED`, sans création
d'un septième snapshot ou d'une septième observation normalisée.

```text
OWNER_REIMPORT_FIRST_REQUEST=05d82794-a30a-4a41-8cd8-029d5bebac11
OWNER_REIMPORT_SECOND_REQUEST=73bdbe38-c776-4ec3-8a40-234d53e51d81
OWNER_REIMPORT_EVENT_COUNT_PER_RUN=2
OWNER_REIMPORT_LOCAL_PROOFS_PER_RUN=6
OWNER_REIMPORT_CONTROLLED_BYTES_PER_RUN=318884
OWNER_REIMPORT_PROVIDER_CALLS_PER_RUN=0
SQL_READ_ONLY_SNAPSHOT_RANGE=508..513
SQL_READ_ONLY_OBSERVATION_RANGE=252..257
SQL_READ_ONLY_CANONICAL_SNAPSHOT_COUNT=6
SQL_READ_ONLY_CANONICAL_OBSERVATION_COUNT=6
SQL_READ_ONLY_OCCURRENCES_PER_SNAPSHOT=2
SQL_READ_ONLY_INSERTED_PER_SNAPSHOT=1
SQL_READ_ONLY_DEDUPLICATED_PER_SNAPSHOT=1
SQL_READ_ONLY_OUTCOME_ORDER=INSERTED,DEDUPLICATED
OWNER_REIMPORT_DEDUP_GATE=PASS
OWNER_REIMPORT_OCCURRENCE_COUNT_GATE=PASS
```

Le scénario de réimport est entièrement qualifié : les octets identiques réemploient les objets
canoniques et ajoutent uniquement la trace d'acquisition append-only attendue. À l'issue de cette
séquence, restaient la preuve SQL zéro écriture après prévalidation refusée et le rollback atomique
sur incident tardif.

### 12.9 Refus serveur avant claim et rollback tardif

Un manifeste exact de six fichiers pour deux rencontres a atteint la prévalidation serveur. Un
corps compositions incompatible a été refusé avec `LINEUPS_PAYLOAD_INCOMPATIBLE` ; la demande
`1ad16ef2-5289-4fa9-be9e-33cbdf78cb8f` est restée `AWAITING_CONFIRMATION`. Le plan n'a donc pas été
claimé et demeure corrigible.

Deux scénarios PostgreSQL automatisent les preuves complémentaires sans pgAdmin ni client `psql`.
`FlywayMigrationIT#rejectsTheWholeOfflineBatchDuringPrevalidationWithoutPersistence` relève par
JDBC les comptes de snapshots, occurrences et observations avant et après le refus, puis exige leur
égalité. `FlywayMigrationIT#rollsBackTheWholeOfflineBatchWhenTheLastFamilyOfTheLastEventFails`
injecte un incident sur la dernière famille du dernier événement, après le début de la transaction,
puis exige que le lot n'ait laissé ni snapshot, ni occurrence, ni observation. La commande ciblée
exécute deux tests sans échec et la suite complète de 52 tests d'intégration termine sans échec sur
PostgreSQL 18.4.

```text
OWNER_SERVER_PREFLIGHT_REJECTION=LINEUPS_PAYLOAD_INCOMPATIBLE
OWNER_SERVER_PREFLIGHT_PLAN_STATE_AFTER_REJECTION=AWAITING_CONFIRMATION
OWNER_SERVER_PREFLIGHT_PLAN_CLAIMED=NO
AUTOMATED_PREFLIGHT_DB_COUNTS_GATE=PASS_TESTCONTAINERS_JDBC_BEFORE_EQUALS_AFTER
OWNER_LATE_ROLLBACK_TARGETED_TEST=PASS
HUMAN_LATE_ROLLBACK_GATE=PASS_OWNER_EXECUTED_TARGETED_POSTGRESQL_TEST
FULL_INTEGRATION_VALIDATION=PASS_52_TESTS
```

### 12.10 Configuration combinée armée

Le binding Spring et la politique du lot ont été testés avec `SOFASCORE_ENABLED=true`, les opt-ins
J3, J4 qualification et phase 2, J5 et découverte tournoi activés, l'origine SofaScore renseignée
et les six endpoints autorisés. Cette configuration rend le lot hors ligne disponible et permet de
préparer un plan. Les politiques manuelles réelles peuvent être simultanément disponibles.

Le lot continue de se bloquer si le rafraîchissement automatique ou le polling live sont actifs,
ou si la conservation brute est désactivée. Il ne résout aucune URI et n'acquiert ni transport, ni
cache fournisseur, ni coordinateur. La validation runtime avec le connecteur activé confirme sur
`127.0.0.1:8091` une réponse HTTP 200, le badge `ZÉRO APPEL FOURNISSEUR`, la recherche et la
préparation locales, ainsi que l'absence de verrouillage ou de bloqueur lié à
`SOFASCORE_ENABLED`.

```text
COMBINED_ENABLED_CONFIGURATION_GATE=PASS
SOFASCORE_ENABLED_COMPATIBILITY=TRUE_AND_FALSE
REAL_J3_J4_J5_TOURNAMENT_POLICIES=MAY_BE_AVAILABLE
OFFLINE_BATCH_TRANSPORT_DEPENDENCY=NONE
RUNTIME_HTTP_VALIDATION=PASS_ENABLED_TRUE_127_0_0_1_8091
STANDARD_VALIDATION=PASS_614_TESTS_2_SKIPPED
INTEGRATION_VALIDATION=PASS_52_TESTS
```

## 13. Critères de passage de statut

### 13.1 Vers `IMPLEMENTED_AWAITING_HUMAN_QUALIFICATION`

Tous les points suivants sont nécessaires :

- implémentation complète du contrat et des routes locales ;
- matrice automatisée pertinente à `PASS` ;
- trois commandes de validation réussies ;
- zéro appel fournisseur, cache et coordinateur constaté ;
- diff sans secret, bind local et flags par défaut vérifiés ;
- quatre documents mis à jour avec les preuves réelles ;
- Work Order encore actif jusqu'à la décision explicite de clôture, qualification propriétaire
  désormais consignée.

### 13.2 Vers `VALIDATED`

Le propriétaire a exécuté et accepté la recette humaine hors ligne, puis autorisé explicitement la
clôture le 2026-08-23. Les critères de passage à `VALIDATED` et de déplacement vers
`docs/work_orders/completed/` sont satisfaits.

## 14. Verdict actuel

```text
STRUCTURAL_DECISION=READY
SAFETY_BOUNDARY=PASS_TRANSPORT_FREE_OFFLINE_IMPLEMENTATION
IMPLEMENTATION=VALIDATED
AUTOMATED_EVIDENCE=PASS
HUMAN_EVIDENCE=PASS_INCLUDING_COMBINED_ENABLED_RUNTIME_IMPORT
CURRENT_WORK_ORDER_STATUS=VALIDATED
OWNER_CLOSURE_DECISION=GRANTED_2026_08_23
```

Le socle et la correction 404 sont implémentés sans migration et sans décision ADR nouvelle, avec
contrôle séparé, connecteur maître orthogonal, prévalidation avant claim et transaction atomique
`3N`.
Le propriétaire a maintenant qualifié positivement le parcours d'import multi-match avec
déclarations 404, l'assistance de manifeste, l'annulation avant import, le redémarrage sans reprise
et le réimport dédupliqué avec occurrences append-only. Le rollback atomique tardif est qualifié
par le test PostgreSQL ciblé exécuté par le propriétaire. La prévalidation sans écriture est
désormais prouvée par un test PostgreSQL/Testcontainers qui compare les comptes JDBC avant et
après, sans pgAdmin ni `psql`. Le propriétaire a autorisé sa clôture le 2026-08-23.

### 14.1 Qualification propriétaire avec la configuration combinée armée

Le propriétaire a ensuite exécuté l'import, et non plus seulement l'ouverture de la page, avec
`SOFASCORE_ENABLED=true` et la configuration combinée armée. La demande
`fd7b7833-5d5c-464c-bcf8-d7a2aa26e90b` porte trois rencontres et neuf fichiers, sans déclaration
404. Le manifeste est exact à neuf saisies sur neuf et le contrôle termine `COMPLETED_LOCKED`.

Le résultat terminal compte 450 124 octets contrôlés, neuf familles `COMPLETE · 100%`, six
nouvelles versions et trois observations dédupliquées. Les appels fournisseur, opérations cache,
acquisitions coordinateur et retries automatiques restent tous à zéro. Cette preuve ferme la porte
humaine de compatibilité avec la configuration armée ; la clôture est ensuite autorisée
explicitement par le propriétaire.

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

### 14.2 Décision propriétaire et clôture

```text
OWNER_QUALIFICATION=PASS
QUALIFICATION_DATE=2026-08-23
CLOSURE_AUTHORIZED=YES
ADDITIONAL_PROVIDER_CALL_AUTHORIZED_BY_CLOSURE=NO
WORK_ORDER_STATUS=VALIDATED
WORK_ORDER_LOCATION=docs/work_orders/completed
```

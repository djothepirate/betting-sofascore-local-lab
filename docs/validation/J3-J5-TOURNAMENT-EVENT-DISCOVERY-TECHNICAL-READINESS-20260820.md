# Readiness technique J3 → J5 — découverte tournoi → rencontres

## 1. Statut

```text
REPORT_DATE=2026-08-20
REPORT_SCOPE=HISTORICAL_PRE_V25_BASELINE
IMPLEMENTATION_STATUS=COMPLETED
WORK_ORDER_STATUS=VALIDATED
WORK_ORDER_LOCATION=docs/work_orders/completed
HUMAN_PROVIDER_QUALIFICATION=PASS_BY_OWNER_DECISION
QUALIFICATION_DATE=2026-08-21
CURRENT_AUTOMATED_V25_VALIDATION=PASS
CURRENT_AUTOMATED_J3_LOCAL_IMPORT_VALIDATION=PASS
CURRENT_AUTOMATED_J5_LOCAL_IMPORT_VALIDATION=PASS
PROVIDER_CALLS_DURING_IMPLEMENTATION=0
PROVIDER_CALLS_DURING_AUTOMATED_TESTS=0
PROVIDER_SCHEMA_VALIDATED=NO
PROVIDER_COUNT_SEMANTICS_VALIDATED=NO
```

Ce rapport conserve la preuve technique de la version antérieure à l'import JSON local V25 et
consigne, dans des sections correctives séparées, les validations automatisées des imports J3 et
J5 ajoutés ensuite. Ces seules validations ne constituent ni une autorisation d'appel réel ni une
qualification humaine. La section 6 enregistre toutefois la décision explicite du propriétaire du
2026-08-21 qui qualifie et clôt le Work Order sur l'ensemble des preuves disponibles.

## 2. Portée implémentée

- catalogue construit depuis la preuve J3 la plus récente du processus courant, uniquement si
  elle est `COMPLETED`, avec relecture des snapshots exacts et contrôle de leur intégrité ;
- liste déroulante libellée exactement par
  `tournament.name - tournament.category.name`, postant seulement `tournament.id`, puis résolution
  serveur de la catégorie et de `tournament.uniqueTournament.id` ; une portée absente est exclue ;
- filtre de liste exigeant une clé `timezoneEventCount` correspondant à l'un des offsets réellement
  applicables à la date J3 dans `Europe/Paris`, sans offset annuel codé en dur ;
- endpoint logique distinct `TOURNAMENT_SCHEDULED_EVENTS`, origine et URI fermées, identifiant de
  route numérique et clé de requête déterministe ;
- contrôle en mémoire avec sélection sans réseau, confirmation exacte de cinq minutes, un GET au
  maximum, arrêt terminal et aucun retry ;
- cache PostgreSQL de dix minutes, brut avant parsing et migration append-only V24 limitée à la
  portée de `provider_response_cache` ;
- parser strict `tournament-scheduled-v1`, projection sur la phase sélectionnée et journée civile
  `Europe/Paris`, déduplication et contrôle `timezoneEventCount` ;
- écriture canonique transactionnelle sans publication partielle ;
- liens directs vers J5 sans appel `EVENT_DETAILS`, campagne J4 ou saisie manuelle d'identifiant.

Références :

- `docs/requirements/J3-J5-TOURNAMENT-EVENT-DISCOVERY-RULES.md` ;
- `docs/architecture/J3-J5-TOURNAMENT-EVENT-DISCOVERY.md` ;
- `docs/work_orders/completed/WO-SS-20260820-009-j3-j5-tournament-event-discovery.md`.

## 3. Frontières réseau constatées par construction

```text
DEFAULT_SOFASCORE_ENABLED=false
DEFAULT_J3_QUALIFICATION_ENABLED=false
DEFAULT_TOURNAMENT_EVENT_DISCOVERY_ENABLED=false
DEFAULT_BASE_URL=EMPTY
DEFAULT_ALLOWED_ENDPOINTS=EMPTY
CONNECTOR_GATE=UNCHANGED_BLOCKING
GENERAL_ENDPOINT_CATALOG_CALLABLE=false
MAVEN_LIVE_PROFILE=BLOCKED
AUTOMATIC_REFRESH=false
LIVE_POLLING=false
AUTOMATIC_RETRY=0
SCHEDULED_PROVIDER_TASKS=0
REAL_PROVIDER_PAYLOAD_ADDED_TO_GIT=0
```

Le contrat de configuration à vérifier lors d'une future qualification autorisée est exactement :

```text
SOFASCORE_ENABLED=true
SOFASCORE_J3_QUALIFICATION_ENABLED=true
SOFASCORE_TOURNAMENT_EVENT_DISCOVERY_ENABLED=true
SOFASCORE_J4_EVENT_DETAILS_QUALIFICATION_ENABLED=false
SOFASCORE_J4_EVENT_DETAILS_PHASE2_ENABLED=false
SOFASCORE_J5_EVENT_DATA_QUALIFICATION_ENABLED=false
SOFASCORE_BASE_URL=https://www.sofascore.com
SOFASCORE_ALLOWED_ENDPOINTS=SCHEDULED_EVENTS,TOURNAMENT_SCHEDULED_EVENTS
```

Ce bloc décrit une éligibilité technique future. Le recopier ne vaut pas autorisation d'exécuter
une collecte J3 ou la découverte des rencontres. Toute action finale fournisseur doit être
autorisée séparément par le propriétaire.

## 4. Matrice de couverture hors ligne

| Domaine | Preuve attendue | Résultat final |
|---|---|---|
| catalogue J3 absent/incomplet/échoué | aucune option actionnable | `PASS` |
| catalogue J3 `COMPLETED` multi-pages | pages exactes agrégées dans l'ordre | `PASS` |
| import J3 local d'un lot `page-1.json` à `page-N.json` valide | prévalidation complète, `COMPLETED`, zéro transport/cache et preuve v5 | `PASS` |
| import J3 local incomplet, troué, sensible, hors borne ou non terminal | refus avant claim et avant toute persistance | `PASS` |
| provenance d'une page J3 importée | source `LOCAL_JSON_IMPORT` et acquisition `MANUAL_LOCAL_JSON_IMPORT` concordantes | `PASS` |
| 403 terminal de la voie J3 directe | aucun fallback ni retry implicite ; nouvelle intention requise pour l'import | `PASS` |
| import J5 local des trois familles valides | trois snapshots et observations ordonnés, zéro transport fournisseur | `PASS` |
| import J5 avec troisième fichier invalide | refus atomique avant claim et avant toute persistance | `PASS` |
| enveloppe J5 locale HTTP 404 fermée | indisponibilité explicite de la famille puis poursuite ordonnée | `PASS` |
| enveloppe J5 locale HTTP 403 | incompatibilité refusée, jamais assimilée à une indisponibilité | `PASS` |
| échec terminal d'une campagne J5 directe | aucun fallback implicite ; redémarrage et nouvelle préparation requis | `PASS` |
| rendu des imports J3/J5 | champs fichier, URI et cartes contenus dans leur colonne | `PASS` |
| métadonnée, taille ou hash divergent | catalogue refusé | `PASS` |
| `receivedAt` nanoseconde relu via PostgreSQL | arrondi sous la microseconde accepté, dérive ≥ 1 µs refusée | `PASS` |
| occurrence sans tournoi unique | exclue sans identifiant inventé | `PASS` |
| occurrence sans `tournament.category.name` | exclue sans portée inventée et ID posté refusé | `PASS` |
| libellé de liste | `tournament.name - tournament.category.name`, valeur `tournament.id` inchangée | `PASS` |
| même `tournament.id`, catégories contradictoires | catalogue entier refusé | `PASS` |
| date d'été Paris, clé `7200` absente ou table vide | occurrence exclue et résolution serveur vide | `PASS` |
| date d'hiver Paris, clé `3600` présente avec valeur zéro | occurrence conservée par présence de clé | `PASS` |
| journée de transition Paris | l'une des clés `3600`/`7200` rend l'option actionnable | `PASS` |
| collision `tournament.id` contradictoire | catalogue entier refusé | `PASS` |
| redémarrage | catalogue indisponible jusqu'à une nouvelle preuve J3 | `PASS` |
| valeur MVC falsifiée | refus avant transport | `PASS` |
| préparation, mauvais texte ou expiration | zéro transport | `PASS` |
| identifiant unique `7` | URI exacte `/unique-tournament/7/...` | `PASS` |
| origine, ID ou date non admissible | requête impossible à construire | `PASS` |
| cache frais et intègre | zéro transport, parsing et projection rejoués | `PASS` |
| cache miss simulé | un transport au maximum, brut avant parsing | `PASS` |
| HTTP/timeout/contenu incompatible | verrou terminal, zéro retry | `PASS` |
| racine `events`, `hasNextPage` absent/false | parsing accepté | `PASS` |
| `hasNextPage=true` ou champ structurant invalide | parsing incompatible | `PASS` |
| autre tournoi unique | réponse entière incompatible | `PASS` |
| autre phase du même tournoi unique | événement exclu | `PASS` |
| événement hors journée `Europe/Paris` | événement exclu | `PASS` |
| journée avec transition DST | compte non vérifiable explicitement | `PASS` |
| doublon exact / doublon conflictuel | déduplication / refus atomique | `PASS` |
| compteur égal / absent / différent | verified / non vérifiable / zéro observation | `PASS` |
| lot valide | identités et observations transactionnelles | `PASS` |
| résultat MVC | métadonnées minimisées et liens J5 | `PASS` |
| événement sans détail J4 | lien J5 disponible, aucun `EVENT_DETAILS` | `PASS` |
| Flyway V1→V24 et V23→V24 | contraintes et historique préservés | `PASS` |

## 5. Commandes finales et emplacements de résultats

Les commandes ci-dessous ont été lancées avec les voies fournisseur dans leur configuration par
défaut bloquée. Elles n'exigent aucune connexion SofaScore. La suite d'intégration utilise uniquement
PostgreSQL local via Docker/Testcontainers ; aucun appel fournisseur n'a été effectué.

```powershell
.\mvnw.cmd clean verify
.\mvnw.cmd -Pintegration-tests verify
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\Verify-Local.ps1
git diff --check
git status --short
Select-String -Path .\src\main\resources\application.yml -Pattern '127\.0\.0\.1'
```

Résultats de la dernière exécution sur le diff final :

```text
STANDARD_SUITE_COMMAND=.\mvnw.cmd clean verify
STANDARD_SUITE_RESULT=PASS
STANDARD_TESTS_RUN=544
STANDARD_FAILURES=0
STANDARD_ERRORS=0
STANDARD_SKIPPED=2

INTEGRATION_SUITE_COMMAND=.\mvnw.cmd -Pintegration-tests verify
INTEGRATION_SUITE_RESULT=PASS
INTEGRATION_TESTS_RUN=49
INTEGRATION_FAILURES=0
INTEGRATION_ERRORS=0
INTEGRATION_SKIPPED=0

J5_LOCAL_IMPORT_TARGETED_TESTS_RUN=28
J5_LOCAL_IMPORT_TARGETED_FAILURES=0
J5_LOCAL_IMPORT_TARGETED_ERRORS=0
J5_LOCAL_IMPORT_PROVIDER_CALLS=0
J5_LOCAL_IMPORT_COUNT=3
J5_LOCAL_IMPORT_PROVENANCE=MANUAL_LOCAL_JSON_IMPORT
J5_STRICT_404_UNAVAILABLE=PASS
J5_HTTP_403_REJECTED=PASS
J3_J5_IMPORT_LAYOUT_CONTAINMENT=PASS

IMPROVEMENT_2_TARGETED_TESTS_RUN=50
IMPROVEMENT_2_TARGETED_FAILURES=0
IMPROVEMENT_2_TARGETED_ERRORS=0
DROPDOWN_GEOGRAPHIC_LABEL=PASS
MISSING_CATEGORY_FAIL_CLOSED=PASS
CATEGORY_CONFLICT_FAIL_CLOSED=PASS

FLYWAY_CLEAN_INSTALL_VERSION=V25_PASS
FLYWAY_UPGRADE_V24_TO_V25=PASS_LOCAL_IMPORT_PROVENANCE_PRESERVED
SERVER_ADDRESS_127_0_0_1=PASS
DEFAULT_NETWORK_CONFIGURATION_BLOCKED=PASS
LIVE_PROFILE_STILL_BLOCKED=PASS
SOURCE_GUARDRAIL=PASS
VERIFY_LOCAL_RESULT=PASS
VERIFY_LOCAL_PROVIDER_CALLS=0
LOCAL_DOTENV_ARMED_BUILD_ISOLATION=PASS
EXACT_COMBINED_SIX_ENDPOINT_PROFILE=PASS
DEFERRED_ENDPOINTS_REJECTED=PASS
EXACT_COMBINED_PROFILE_APPLICATION_START=PASS_127_0_0_1_8087
STARTUP_CHECK_PROVIDER_CALLS=0
APPLICATION_STOPPED_AFTER_STARTUP_CHECK=PASS
J3_CATALOG_POSTGRES_TIMESTAMP_ROUNDTRIP=PASS
J3_CATALOG_TIMESTAMP_ACCEPTANCE=ABSOLUTE_DELTA_LT_1_MICROSECOND
J3_CATALOG_OTHER_METADATA_AND_INTEGRITY_GUARDS=EXACT
DIFF_CHECK=PASS
SECRET_AND_PAYLOAD_REVIEW=PASS_NO_SECRET_NO_REAL_PROVIDER_PAYLOAD
```

Le correctif de reproductibilité a été revalidé avec un `.env` local réellement armé pour J3,
la découverte tournoi, J4 phase 2 et J5 : `clean verify` reste vert et ne consulte pas le
fournisseur. Cette isolation du build n'assouplit pas le démarrage applicatif : l'allowlist du
profil combiné doit contenir exactement les six endpoints actifs, sans `TOURNAMENT_STANDINGS` ni
`TEAM_RECENT_EVENTS`. Un démarrage technique avec cette union exacte a atteint
`Started SofascoreLocalApplication` sur `127.0.0.1:8087`, puis l'application a été arrêtée
gracieusement sans requête fournisseur ; le port était libéré après l'arrêt.

Les deux exclusions de la suite standard correspondent aux tests Windows déjà conditionnels de
`LocalJ7ExportFileStoreTest`. Une suite automatisée réussie ne change pas
`HUMAN_PROVIDER_QUALIFICATION=NOT_RUN`.

### 5.1 Correctif issu de la première séquence opérateur J3

L'opérateur a signalé une collecte J3 terminale `COMPLETED` de six pages, résolues par six
transports fournisseur et zéro cache hit, suivie d'un catalogue
`SNAPSHOT_METADATA_MISMATCH`. Cette séquence a atteint J3 mais n'a exécuté aucune découverte de
rencontres : aucune option ni action du second endpoint n'était disponible.

Le diagnostic a reproduit le défaut avec le store PostgreSQL réel. L'horodatage reçu en mémoire
portait des nanosecondes, puis la colonne `timestamptz` le restituait à la microseconde. L'ancienne
égalité exacte rejetait donc le snapshot pourtant identique sur son ID, sa clé, son contenu et son
hash. Le correctif borne l'équivalence temporelle à un écart absolu strictement inférieur à une
microseconde et conserve tous les autres contrôles exacts. Deux tests unitaires couvrent la borne
acceptée/refusée et un test Testcontainers couvre l'aller-retour PostgreSQL complet jusqu'au statut
de catalogue `AVAILABLE`.

```text
OPERATOR_REPORTED_J3_COLLECTION=COMPLETED_6_PAGES
OPERATOR_REPORTED_J3_PROVIDER_CALLS=6
OPERATOR_REPORTED_J3_CACHE_HITS=0
OPERATOR_REPORTED_DISCOVERY_PROVIDER_CALLS=0
OPERATOR_REPORTED_BLOCKER=SNAPSHOT_METADATA_MISMATCH
ROOT_CAUSE=POSTGRES_TIMESTAMP_MICROSECOND_PRECISION
CORRECTION_STATUS=IMPLEMENTED_AND_AUTOMATED_REGRESSION_PASS
```

Cette preuve opérateur est partielle et corrective. Elle ne qualifie ni la réponse du second
endpoint, ni `timezoneEventCount`, ni les liens J5. Elle appartient à la base historique pré-V25 ;
le statut courant du Work Order est `IN_PROGRESS`.

### 5.2 Observations opérateur supplémentaires et amélioration du libellé

Une tentative ultérieure de collecte J3 s'est arrêtée avant la page 1 sur `HTTP_FORBIDDEN`. Les
captures confirment le verrou terminal, l'arrêt global réappliqué, zéro page collectée et l'absence
de retry. Le propriétaire a explicitement conclu qu'aucune modification de code n'était nécessaire
pour cet incident. Une seconde tentative a ensuite atteint `COMPLETED` sur six pages et rendu le
catalogue `AVAILABLE`.

Cette seconde observation a fait apparaître un besoin de lisibilité distinct : deux libellés de
phase peuvent être ambigus sans leur portée géographique. Le parseur conserve donc désormais
`scheduled[*].tournament.category.name` et le catalogue produit exactement
`tournament.name - tournament.category.name`. La catégorie reste descriptive ; seul
`tournament.id` est posté et revalidé. L'absence de catégorie exclut l'occurrence et une catégorie
contradictoire pour le même identifiant refuse le catalogue.

```text
FIRST_ADDITIONAL_J3_ATTEMPT=HTTP_FORBIDDEN
FIRST_ADDITIONAL_J3_ATTEMPT_RETRY=0
HTTP_403_CODE_CHANGE_REQUIRED=NO
SECOND_ADDITIONAL_J3_ATTEMPT=COMPLETED_6_PAGES
IMPROVEMENT_2=TOURNAMENT_NAME_SPACE_HYPHEN_SPACE_CATEGORY_NAME
CATEGORY_USED_AS_IDENTITY=NO
WORK_ORDER_QUALIFIED_BY_THESE_OBSERVATIONS=NO
```

Ces observations et ce correctif automatisé ne changent pas le statut du Work Order. Le
propriétaire a demandé des vérifications supplémentaires avant toute qualification.

### 5.3 Correctif après échec du test J3 sur `HTTP_FORBIDDEN`

Le test suivant a confirmé que l'import local V25 du second endpoint ne suffisait pas lorsque la
collecte J3 directe échouait avant la page 1 : sans preuve J3 terminale `COMPLETED`, le catalogue ne
pouvait légitimement proposer aucune phase. La voie locale a donc été étendue à J3 sans modifier la
sémantique de l'échec direct : le `HTTP_FORBIDDEN` reste terminal, sans retry ni bascule automatique.

Après une nouvelle intention J3 explicitement préparée et confirmée, l'opérateur peut fournir en
une seule action un lot complet `page-1.json` à `page-N.json`. Le lot entier est contrôlé avant le
claim et avant toute persistance : numérotation contiguë 1..N, maximum 25 pages, 5 Mio par page,
25 Mio au total, absence de donnée sensible, parser J3 inchangé, `hasNextPage=true` sur toutes les
pages intermédiaires et `false` sur la dernière. Une réussite conserve chaque brut avec le mode
`MANUAL_LOCAL_JSON_IMPORT`, produit une preuve minimisée v5, termine J3 en `COMPLETED` et alimente le
même catalogue. Elle n'utilise ni transport fournisseur ni cache fournisseur.

```text
J3_LOCAL_IMPORT_TARGETED_TESTS_RUN=43
J3_LOCAL_IMPORT_TARGETED_FAILURES=0
J3_LOCAL_IMPORT_TARGETED_ERRORS=0
STANDARD_SUITE_RESULT=PASS
STANDARD_TESTS_RUN=538
STANDARD_FAILURES=0
STANDARD_ERRORS=0
STANDARD_SKIPPED=2
INTEGRATION_SUITE_RESULT=PASS
INTEGRATION_TESTS_RUN=49
INTEGRATION_FAILURES=0
INTEGRATION_ERRORS=0
INTEGRATION_SKIPPED=0
J3_LOCAL_IMPORT_PROVIDER_CALLS=0
J3_LOCAL_IMPORT_CACHE_READ_WRITE=0
J3_LOCAL_IMPORT_PROVENANCE=MANUAL_LOCAL_JSON_IMPORT
VERIFY_LOCAL_RESULT=PASS
VERIFY_LOCAL_PROVIDER_CALLS=0
WORK_ORDER_QUALIFIED_BY_THIS_VALIDATION=NO
```

Cette validation prouve le comportement hors ligne et l'intégrité du pipeline. Elle ne constitue
pas la recette opérateur supplémentaire demandée par le propriétaire.

### 5.4 Correctif après indisponibilité de l'import hors réseau J5

La recette suivante a confirmé que l'import J3 local et la découverte par tournoi pouvaient
aboutir, mais qu'une campagne J5 restait limitée aux trois appels directs et se verrouillait dès le
premier `HTTP_403`. La voie locale J5 exige désormais une nouvelle préparation après redémarrage,
la confirmation exacte déjà associée à l'identité canonique et trois corps JSON fournis ensemble,
dans l'ordre `EVENT_STATISTICS`, `EVENT_INCIDENTS`, `EVENT_LINEUPS`.

Les trois fichiers sont scannés et parsés avant le claim. Une invalidité du troisième fichier ne
produit donc ni snapshot, ni observation, ni consommation de l'intention. Après claim, chaque brut
est persisté avant normalisation avec l'acquisition `MANUAL_LOCAL_JSON_IMPORT`. Le résultat sépare
les compteurs `providerCallAttempts=0` et `localJsonImports=3`. Une enveloppe fermée dont le code
entier vaut exactement 404 est la seule forme locale assimilée à une famille indisponible ; un 403,
un autre code ou un champ inattendu est refusé comme contenu incompatible.

Le rendu J3/J5 a également été borné par `min-width: 0`, `max-width: 100%` et la coupure des URI
longues afin que les champs fichier natifs et les instructions d'import restent à l'intérieur de
leur carte, y compris dans la première option J3 signalée par l'opérateur.

```text
J5_LOCAL_IMPORT_TARGETED_TESTS_RUN=28
J5_LOCAL_IMPORT_TARGETED_FAILURES=0
J5_LOCAL_IMPORT_TARGETED_ERRORS=0
STANDARD_SUITE_RESULT=PASS
STANDARD_TESTS_RUN=544
STANDARD_FAILURES=0
STANDARD_ERRORS=0
STANDARD_SKIPPED=2
INTEGRATION_SUITE_RESULT=PASS
INTEGRATION_STANDARD_TESTS_RUN=544
INTEGRATION_TESTS_RUN=49
INTEGRATION_FAILURES=0
INTEGRATION_ERRORS=0
INTEGRATION_SKIPPED=0
FLYWAY_MIGRATIONS_APPLIED=25
J5_LOCAL_IMPORT_PROVIDER_CALLS=0
J5_LOCAL_IMPORT_CACHE_ACCESS=0
J5_LOCAL_IMPORT_PROVENANCE=MANUAL_LOCAL_JSON_IMPORT
VERIFY_LOCAL_RESULT=PASS
VERIFY_LOCAL_PROVIDER_CALLS=0
WORK_ORDER_QUALIFIED_BY_THIS_VALIDATION=NO
```

Ces validations automatisées prouvent la disponibilité technique de l'option et ses invariants
hors réseau. Le test fonctionnel ciblé qui les complète est consigné ci-dessous ; ni l'un ni
l'autre ne qualifie le Work Order.

### 5.5 Vérification fonctionnelle ciblée J3/J5 du 2026-08-21

Le propriétaire confirme sur l'application locale que le débordement du premier bloc d'import J3
est corrigé : les deux options, le champ fichier, les instructions et les boutons restent contenus
dans leurs cartes. Il confirme également la campagne J5 en mode import. La préparation expose les
trois familles, les trois fichiers sont traités sans transport et la campagne se termine dans l'état
`COMPLETED_LOCKED` avec zéro appel fournisseur et trois imports JSON locaux.

Le résultat minimisé relie les snapshots `412`, `413` et `414` aux observations `219`, `220` et
`221`. La statistique est une indisponibilité 404 explicite `UNAVAILABLE · N/A`, les incidents sont
une liste vide valide `EMPTY_VALID · 100%` et les compositions sont `PARTIAL · 97%` avec `48/49`
signaux. Les formations domicile et extérieur `4-2-3-1` et leurs joueurs sont rendus depuis les
données locales.

```text
FUNCTIONAL_VALIDATION_SCOPE=CORRECTIVE_J3_LAYOUT_AND_J5_LOCAL_IMPORT_ONLY
J3_IMPORT_LAYOUT_OVERFLOW=FIXED
J5_LOCAL_IMPORT_TERMINAL_STATE=COMPLETED_LOCKED
J5_LOCAL_IMPORT_PROVIDER_CALLS=0
J5_LOCAL_IMPORT_COUNT=3
J5_LOCAL_IMPORT_SNAPSHOTS=412,413,414
J5_LOCAL_IMPORT_OBSERVATIONS=219,220,221
J5_STATISTICS_RESULT=UNAVAILABLE_NA
J5_INCIDENTS_RESULT=EMPTY_VALID_100
J5_LINEUPS_RESULT=PARTIAL_97_48_OF_49
WORK_ORDER_QUALIFIED_BY_THIS_FUNCTIONAL_CHECK_ALONE=NO
OWNER_REMAINING_VERIFICATIONS_AT_CAPTURE_TIME=IN_PROGRESS
```

Cette validation fonctionnelle clôt les deux anomalies correctives concernées. À l'instant où elle
a été consignée, elle n'autorisait encore aucune qualification globale ni aucun déplacement du
Work Order. La décision propriétaire ultérieure est enregistrée ci-dessous.

## 6. Qualification et clôture autorisées — 2026-08-21

Le propriétaire autorise explicitement la qualification de l'évolution, la fermeture du Work Order
et son déplacement vers `completed`. Cette décision agrège les preuves automatisées finales et les
constats opérateur déjà consignés : collecte/import J3 terminal, catalogue `AVAILABLE` avec portée
géographique, rencontres canoniques et liens J5 observés, correctif d'affichage J3 validé et
campagne J5 locale terminée avec trois imports et zéro appel fournisseur.

La clôture n'autorise aucun nouvel appel fournisseur. Une éventuelle recette ultérieure demeure une
action distincte, bornée et soumise à une nouvelle autorisation ; les échecs historiques 403 et
transport restent conservés tels quels, sans retry ni contournement.

```text
HUMAN_QUALIFICATION_AUTHORIZED=YES
QUALIFICATION_DATE=2026-08-21
QUALIFICATION_DECISION=PASS
QUALIFICATION_BASIS=OWNER_RECORDED_FUNCTIONAL_EVIDENCE_AND_AUTOMATED_GATES
J3_COLLECTION_TERMINAL_STATE=COMPLETED
J3_LOCAL_IMPORT_PATH=PASS
TOURNAMENT_CATALOG_STATE=AVAILABLE
TOURNAMENT_GEOGRAPHIC_LABEL=PASS
TOURNAMENT_DISCOVERY_CANONICAL_EVENTS=VISIBLE
J5_LINKS_VISIBLE_WITHOUT_J4=YES
J3_IMPORT_LAYOUT_OVERFLOW=FIXED
J5_LOCAL_IMPORT_TERMINAL_STATE=COMPLETED_LOCKED
J5_LOCAL_IMPORT_PROVIDER_CALLS=0
J5_LOCAL_IMPORT_COUNT=3
RAW_PAYLOAD_INCLUDED_IN_REPORT=NO
PROVIDER_URI_INCLUDED_IN_REPORT=NO
REQUEST_OR_RESPONSE_HEADERS_INCLUDED_IN_REPORT=NO
COOKIES_TOKENS_ACCOUNT_SESSION_USED=NO
AUTOMATIC_RETRY_EXECUTED=NO
POLLING_OR_SCHEDULING_EXECUTED=NO
ADDITIONAL_PROVIDER_CALL_AUTHORIZED=NO
CONFIGURATION_RELOCKED=NOT_REASSERTED_BY_CLOSURE_DECISION
APPLICATION_STOPPED=NOT_REASSERTED_BY_CLOSURE_DECISION
```

La décision de clôture fixe le statut courant suivant :

```text
IMPLEMENTATION_STATUS=COMPLETED
TECHNICAL_READINESS=AUTOMATED_V25_J3_AND_J5_LOCAL_IMPORT_VALIDATION_PASS
OWNER_CORRECTIVE_VALIDATION_J3_LAYOUT_AND_J5_LOCAL_IMPORT=PASS
HUMAN_PROVIDER_QUALIFICATION=PASS_BY_OWNER_DECISION
WORK_ORDER_STATUS=VALIDATED
WORK_ORDER_LOCATION=docs/work_orders/completed
```

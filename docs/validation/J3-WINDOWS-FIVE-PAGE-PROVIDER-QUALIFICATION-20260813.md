# J3 — Rapport Windows de qualification réelle du chemin fournisseur cinq pages

## 1. Identification

| Élément | Valeur |
|---|---|
| Projet | SofaScore Local Lab |
| Date de qualification | 2026-08-13 |
| Environnement | Windows local |
| Jalon | J3 — Appel manuel borné |
| Work Order | `WO-SS-20260812-003` |
| Branche | `feat/j3-five-page-provider-call` |
| Commit qualifié | `8bcbd46595773800f8759ce5761d35a4b1c75220` |
| Libellé du commit | `feat: collect minimized J3 provider evidence` |
| Famille fournisseur | `SCHEDULED_EVENTS` |
| Date fournisseur | `2026-08-13` |
| Portée autorisée | Pages `1,2,3,4,5` |
| Séquences réelles déclenchées | `1` |
| Pages réellement tentées | `1` |
| Cookies, jetons, compte ou session | `NONE` |
| Retry automatique | `NO` |
| Résultat de sécurité | `PASS` |
| Résultat du transport page 1 | `PASS — HTTP 200` |
| Résultat de persistance page 1 | `PASS — INSERTED` |
| Résultat de compatibilité du parseur | `FAIL — SCHEMA_INCOMPATIBLE` |
| Résultat global | `FAILED_SAFELY` |
| Décision documentaire | `J3_REMAINS_IN_DEVELOPMENT` |

## 2. Objet du rapport

Ce rapport consigne la première et unique qualification réelle déclenchée par le propriétaire depuis
l'interface locale J3. Il complète le rapport de préparation
`J3-WINDOWS-FIVE-PAGE-PROVIDER-READINESS-20260813.md`, qui s'arrêtait volontairement avant le bouton
final.

La qualification devait vérifier :

- l'environnement Java et PostgreSQL local ;
- la disponibilité exclusive du chemin spécialisé `SCHEDULED_EVENTS` ;
- la confirmation explicite et l'action finale séparée ;
- le transport réel borné aux pages approuvées ;
- la persistance du brut avant parsing ;
- l'arrêt à la première incompatibilité, sans retry ni page suivante ;
- la réapplication automatique de l'arrêt global ;
- la production et le téléchargement d'une preuve minimisée.

Le rapport ne reproduit ni payload brut, ni URI fournisseur, ni en-tête HTTP, ni identifiant de
confirmation, ni valeur du fichier `.env`. Les captures humaines et le fichier téléchargé restent
hors Git ; seules leurs observations minimisées sont consignées ici.

## 3. Gouvernance et périmètre maintenus

```text
EXPERIMENTAL
LOCAL_ONLY
NOT_PRODUCTION_APPROVED
NO_CRITICAL_DEPENDENCY
```

Les limites suivantes sont restées actives :

- application exposée uniquement sur `127.0.0.1:8087` ;
- PostgreSQL exposé uniquement sur `127.0.0.1:5432` ;
- connecteur général verrouillé ;
- seule la famille `SCHEDULED_EVENTS` appelable dans le chemin J3 dédié ;
- concurrence maximale `1` ;
- délai minimal de trois secondes entre deux départs de page ;
- aucun cookie, jeton, compte ou état de session fournisseur ;
- aucun proxy, aucune redirection, aucun polling et aucun live ;
- aucun retry, aucune reprise et aucune page découverte automatiquement.

## 4. Préparation de l'environnement Windows

### 4.1 Version Java

Commande exécutée :

```powershell
java -version
```

Résultat :

```text
java version "25.0.4" 2026-07-21 LTS
Java(TM) SE Runtime Environment (build 25.0.4+7-LTS-189)
Java HotSpot(TM) 64-Bit Server VM (build 25.0.4+7-LTS-189, mixed mode, sharing)
JAVA_VERSION_CHECK=PASS
```

### 4.2 PostgreSQL local

Commandes exécutées :

```powershell
docker compose --env-file .env up -d postgres
docker compose --env-file .env ps
```

Résultat observé :

```text
CONTAINER=betting-sofascore-local-lab-postgres
IMAGE=postgres:18.4-alpine
SERVICE=postgres
STATUS=UP_HEALTHY
EXPOSURE=127.0.0.1:5432
RESULT=PASS
```

### 4.3 Démarrage Maven

Une première tentative avec la propriété Maven placée avant le goal a été mal interprétée par
PowerShell :

```powershell
.\mvnw.cmd -Dspring-boot.run.profiles=local spring-boot:run
```

Maven a reçu `.run.profiles=local` comme phase et a répondu :

```text
Unknown lifecycle phase ".run.profiles=local"
```

Cet événement est une erreur de syntaxe de lancement, pas un échec de compilation, de test ou de
l'application. Aucun appel fournisseur n'a été exécuté pendant cette tentative. Le démarrage a
ensuite réussi avec l'argument protégé de l'interprétation PowerShell.

```text
INITIAL_START_COMMAND=FAIL_ARGUMENT_PARSING
APPLICATION_FAILURE=NO
PROVIDER_CALL_DURING_FAILED_START=NO
CORRECTED_START=PASS
```

## 5. Validation automatisée préalable

La validation consolidée du commit qualifié avait été exécutée avant la qualification humaine :

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass `
  -File .\scripts\Verify-Local.ps1
```

Résultat :

```text
PREFLIGHT_RESULT=PASS
JAVA_TARGET=25
SOURCE_GUARDRAIL_SCAN=PASS
MAVEN_CLEAN_VERIFY=PASS
TEST_SUITES=32
TESTS=132
FAILURES=0
ERRORS=0
SKIPPED=0
JAR_BUILD=PASS
BUILD_GROUP=com.bettingproject
INTEGRATION_TESTS_EXECUTED=NO_PERSISTENCE_CHANGE
SOFASCORE_NETWORK_CALLS_EXECUTED=NO
```

Les tests standards utilisent des transports factices ou `MockRestServiceServer`. Ils n'ont pas
participé à la séquence réelle décrite dans les sections suivantes.

## 6. État pré-exécution de l'interface

Les captures fournies par le propriétaire montrent l'état suivant avant le bouton final :

| ID | Contrôle | Résultat observé | Statut |
|---|---|---|---|
| J3-REAL-WIN-01 | Gouvernance | `EXPERIMENTAL / LOCAL_ONLY / NOT_PRODUCTION_APPROVED / NO_CRITICAL_DEPENDENCY` | PASS |
| J3-REAL-WIN-02 | Exposition applicative | `127.0.0.1:8087` | PASS |
| J3-REAL-WIN-03 | PostgreSQL | `AVAILABLE`, Flyway `2`, snapshots `0`, incidents `0` | PASS |
| J3-REAL-WIN-04 | Connecteur spécialisé | `QUALIFICATION J3 PRÊTE` | PASS |
| J3-REAL-WIN-05 | Origine | Configurée mais non affichée | PASS |
| J3-REAL-WIN-06 | Concurrence et délai | Concurrence `1`, délai minimal `3 s` | PASS |
| J3-REAL-WIN-07 | Catalogue | `SCHEDULED_EVENTS` seul marqué `OUI` et `CONFIGURÉE` | PASS |
| J3-REAL-WIN-08 | Connecteur général | Toujours verrouillé | PASS |
| J3-REAL-WIN-09 | Arrêt global et circuit | Arrêt levé, circuit `CLOSED`, motif `NONE` | PASS |
| J3-REAL-WIN-10 | Activation opérateur | `OUI`, incident actif `NON` | PASS |
| J3-REAL-WIN-11 | Intention | `CONFIRMED_READY` | PASS |
| J3-REAL-WIN-12 | Clé logique | `SCHEDULED_EVENTS|date=2026-08-13|pages=1-5` | PASS |
| J3-REAL-WIN-13 | Transport | `PRÊT POUR ACTION UNIQUE` | PASS |
| J3-REAL-WIN-14 | Action finale | Bouton pages `1 À 5` disponible | PASS |

À cet instant, aucune réponse fournisseur n'avait encore été enregistrée :

```text
SNAPSHOTS_BEFORE_CALL=0
INCIDENTS_BEFORE_CALL=0
LAST_RECORDED_CALL=NONE
```

## 7. Exécution réelle et résultat obtenu

Le propriétaire a sélectionné une seule fois l'action finale du lot fournisseur. La page 1 a été
la seule page tentée.

### 7.1 Transport page 1

```text
PAGE_1_REQUESTED_AT=2026-08-13T13:28:17.812317800Z
PAGE_1_RECEIVED_AT=2026-08-13T13:28:18.452511100Z
PAGE_1_HTTP_STATUS=200
PAGE_1_LATENCY_MS=640
TRANSPORT_RESULT=PASS
```

Le statut `HTTP 200` confirme que le chemin spécialisé a reçu une réponse fournisseur. Il ne
constitue pas à lui seul une validation du schéma JSON.

### 7.2 Persistance avant parsing

```text
PAGE_1_SNAPSHOT_RECORDED=YES
PAGE_1_SNAPSHOT_ID=1
PAGE_1_PERSISTENCE_OUTCOME=INSERTED
PAGE_1_PAYLOAD_SIZE_BYTES=203114
PAGE_1_PAYLOAD_SHA256=89d23831c4894b0f08efa4333607fc6f6c05678bb5ed3daa959933e5da593b95
RAW_PERSISTENCE_BEFORE_PARSING=PASS
```

Le snapshot brut reste exclusivement dans PostgreSQL local. Sa taille et son empreinte sont
reproduites comme métadonnées d'intégrité ; ses octets ne sont pas inclus dans ce rapport.

### 7.3 Classification du schéma

```text
PAGE_1_SCHEMA_STATUS=SCHEMA_INCOMPATIBLE
PAGE_1_TERMINAL_CODE=SCHEMA_INCOMPATIBLE
PARSER_COMPATIBILITY=FAIL
```

La réponse réelle ne satisfait donc pas le contrat actuel du parseur `scheduled-events-v1`. Cette
classification est intervenue après la persistance réussie du snapshot.

### 7.4 Arrêt des pages suivantes

```text
PAGES_ATTEMPTED=1
PAGES_COMPLETED=0
FAILED_PAGE=1
PAGE_2_REQUESTED=NO
PAGE_3_REQUESTED=NO
PAGE_4_REQUESTED=NO
PAGE_5_REQUESTED=NO
AUTOMATIC_RETRY_EXECUTED=NO
STOP_AT_FIRST_INCIDENT=PASS
```

`PAGES_COMPLETED=0` signifie qu'aucune page n'a terminé toute la chaîne transport, persistance et
parsing compatible. La page 1 a néanmoins bien été demandée, reçue et persistée.

## 8. État terminal de sécurité

Après l'incompatibilité, l'interface a présenté :

```text
INTENT_STATE=FAILED
FINAL_GLOBAL_STOP=ACTIVE
FINAL_CIRCUIT_STATE=LOCKED
FINAL_CIRCUIT_REASON=QUALIFICATION_TERMINAL_LOCK
OPERATOR_ACTIVATION=NO
PROVIDER_TRANSPORT=UNAVAILABLE
FINAL_PROVIDER_BUTTON=BLOCKED
```

Le champ visuel `Incident actif = NON` est cohérent avec le modèle terminal : le circuit n'est plus
dans l'état transitoire `OPEN`, car le verrou final a été appliqué. La cause de l'arrêt reste
conservée par `TERMINAL_CODE=SCHEMA_INCOMPATIBLE`.

La formulation visuelle « lot arrêté avant la page 1 » est imprécise : la preuve montre une page 1
tentée et persistée. La formulation exacte est « lot arrêté sur la page 1 après persistance, lors de
la validation du schéma ». Cette observation de présentation ne remet pas en cause le comportement
de sécurité ni les métadonnées structurées.

```text
OBS-J3-REAL-001=TERMINAL_MESSAGE_SAYS_BEFORE_PAGE_INSTEAD_OF_ON_PAGE
SEVERITY=DOCUMENTATION_PRESENTATION
SAFETY_IMPACT=NONE
```

## 9. Preuve terminale minimisée

Le fichier suivant a été téléchargé depuis l'interface :

```text
FILENAME=J3-MINIMIZED-EVIDENCE-2026-08-13.txt
FILE_SIZE_BYTES=1001
FILE_SHA256=422a29c1ef93f8c151a974b8ee33f853f4cd06217a76417508e164fa3b6cff5b
EVIDENCE_VERSION=1
```

Le contenu vérifié confirme :

```text
GENERATED_AT=2026-08-13T13:28:18.591117Z
QUALIFICATION_DATE=2026-08-13
QUALIFICATION_SCOPE=PAGES_1_2_3_4_5
TERMINAL_STATE=FAILED
TERMINAL_CODE=SCHEMA_INCOMPATIBLE
FINAL_GLOBAL_STOP=ACTIVE
FINAL_CIRCUIT_STATE=LOCKED
FINAL_CIRCUIT_REASON=QUALIFICATION_TERMINAL_LOCK
```

Les déclarations de minimisation sont présentes et conformes :

```text
AUTOMATIC_RETRY_EXECUTED=NO
COOKIES_TOKENS_ACCOUNT_SESSION_USED=NO
RAW_PAYLOAD_INCLUDED=NO
PROVIDER_URI_INCLUDED=NO
REQUEST_OR_RESPONSE_HEADERS_INCLUDED=NO
CONFIRMATION_IDENTIFIER_INCLUDED=NO
```

```text
MINIMIZED_EVIDENCE_GENERATION=PASS
MINIMIZED_EVIDENCE_DOWNLOAD=PASS
MINIMIZED_EVIDENCE_INTEGRITY_HASH=RECORDED
RAW_PAYLOAD_EXPOSURE=NO
SESSION_DATA_EXPOSURE=NO
```

## 10. Matrice des résultats

| Domaine qualifié | Résultat | Décision |
|---|---|---|
| Java 25 LTS | Version `25.0.4` | PASS |
| PostgreSQL local | Conteneur sain sur loopback | PASS |
| Build et tests standards | `132/132`, aucun échec | PASS |
| Configuration J3 spécialisée | Prête et bornée | PASS |
| Confirmation explicite | `CONFIRMED_READY` | PASS |
| Usage unique | Une seule séquence déclenchée | PASS |
| Transport page 1 | `HTTP 200`, `640 ms` | PASS |
| Persistance du brut | Snapshot `1`, `INSERTED` | PASS |
| Compatibilité `scheduled-events-v1` | `SCHEMA_INCOMPATIBLE` | FAIL |
| Arrêt au premier incident | Pages 2 à 5 non demandées | PASS |
| Retry | Aucun retry | PASS |
| Arrêt global terminal | Réappliqué | PASS |
| Circuit terminal | `LOCKED / QUALIFICATION_TERMINAL_LOCK` | PASS |
| Preuve minimisée | Générée et téléchargée | PASS |
| Absence de données de session | Confirmée | PASS |
| Clôture complète de J3 | Bloquée par le schéma réel | NOT_READY |

## 11. Interprétation

La qualification démontre que :

1. le chemin réseau spécialisé et borné fonctionne ;
2. la page 1 répond avec succès au niveau HTTP ;
3. les octets sont conservés localement avant parsing ;
4. la politique détecte l'incompatibilité de schéma ;
5. aucune page supplémentaire ni aucun retry n'est exécuté ;
6. l'arrêt global et le verrou terminal sont réappliqués ;
7. une preuve exploitable est collectée sans divulguer le payload.

Elle ne démontre pas que le parseur actuel est compatible avec la réponse réelle. Le résultat global
est donc un échec fonctionnel sûr et informatif, pas une défaillance des garde-fous.

## 12. Décision et suite autorisable

La séquence réelle unique est considérée comme consommée. Elle ne doit pas être répétée pour
contourner `FAILED`, explorer les pages suivantes ou reprendre à la page 2.

Le Work Order J3 reste `IN_DEVELOPMENT`. La prochaine unité doit être entièrement hors ligne :

- conserver le snapshot `1` dans PostgreSQL local ;
- examiner localement la structure nécessaire au diagnostic, sans journaliser ni versionner le
  payload complet ;
- identifier précisément la divergence avec `scheduled-events-v1` ;
- créer une fixture minimisée ou synthétique non sensible ;
- adapter le parseur et ajouter les tests de non-régression ;
- ne prévoir un nouvel appel réel qu'après une nouvelle décision explicite du propriétaire.

Le retour de la configuration locale aux valeurs désactivées doit être effectué après collecte de la
preuve :

```text
SOFASCORE_ENABLED=false
SOFASCORE_J3_QUALIFICATION_ENABLED=false
SOFASCORE_BASE_URL=EMPTY
SOFASCORE_ALLOWED_ENDPOINTS=EMPTY
```

## 13. Bilan

```text
J3_WINDOWS_REAL_PROVIDER_QUALIFICATION=COMPLETED
J3_REAL_PROVIDER_SEQUENCE_COUNT=1
J3_REAL_PROVIDER_PAGES_ATTEMPTED=1
J3_REAL_PROVIDER_PAGES_COMPLETED=0
J3_PAGE_1_HTTP_STATUS=200
J3_PAGE_1_TRANSPORT=PASS
J3_PAGE_1_RAW_PERSISTENCE=PASS
J3_PAGE_1_SNAPSHOT_ID=1
J3_PAGE_1_SCHEMA_COMPATIBILITY=FAIL
J3_TERMINAL_CODE=SCHEMA_INCOMPATIBLE
J3_PAGES_2_TO_5_EXECUTED=NO
J3_AUTOMATIC_RETRY_EXECUTED=NO
J3_TERMINAL_GLOBAL_STOP=PASS
J3_TERMINAL_CIRCUIT_LOCK=PASS
J3_MINIMIZED_EVIDENCE=PASS
J3_RAW_PAYLOAD_INCLUDED_IN_REPORT=NO
J3_SESSION_DATA_USED=NO
J3_OVERALL_RESULT=FAILED_SAFELY
J3_WORK_ORDER=IN_DEVELOPMENT
J3_NEXT_UNIT=OFFLINE_REAL_SCHEMA_ANALYSIS_AND_PARSER_ADAPTATION
```

## 14. Remédiation hors ligne du schéma qualifié

L’unité suivante a été réalisée exclusivement à partir du snapshot local `1`, sans nouvelle requête
fournisseur. Le diagnostic n’a extrait que les chemins JSON, leurs types et leurs occurrences ;
aucune valeur de tournoi, traduction, URI, en-tête ou donnée de session n’a été affichée ou
versionnée.

La divergence exacte est :

```text
EXPECTED_J2_ROOT=events,hasNextPage
QUALIFIED_PROVIDER_ROOT=scheduled,hasNextPage
QUALIFIED_PROVIDER_ENTRY=tournament,timezoneEventCount
QUALIFIED_TOURNAMENT_IDENTITY=id,name
QUALIFIED_UNIQUE_TOURNAMENT=OPTIONAL
QUALIFIED_TIMEZONE_EVENT_COUNT=INTEGER_KEY_TO_NON_NEGATIVE_INTEGER
```

Le parseur `scheduled-events-v1` accepte désormais les deux formes de manière non ambiguë. La forme
historique `events` reste disponible pour le corpus J2. La forme qualifiée `scheduled` produit des
`ScheduledTournamentAvailability` et laisse la liste `events` vide : aucun match, horaire ou équipe
n’est inventé à partir d’une réponse qui ne les fournit pas.

Une fixture entièrement synthétique reproduit seulement la structure utile. Elle est marquée
`SYNTHETIC`, utilise des identités et nombres créés pour le test et ne prétend pas être le snapshot
fournisseur minimisé :

```text
FIXTURE_ID=scheduled-events-qualified-provider-shape
FIXTURE_ORIGIN=SYNTHETIC
PROVIDER_SCHEMA_VALIDATED_IN_MANIFEST=NO
RAW_PROVIDER_PAYLOAD_IN_GIT=NO
SESSION_DATA_IN_FIXTURE=NO
```

Après compilation de l’adaptation, le snapshot brut local a été relu une seule fois par le parseur,
sans transport :

```text
OFFLINE_SNAPSHOT_ID=1
OFFLINE_REPARSE_STATUS=PARSED
OFFLINE_REPARSE_PROBLEMS=0
OFFLINE_REPARSE_PAYLOAD_SHAPE=SCHEDULED_TOURNAMENT_LIST
OFFLINE_REPARSE_EVENT_COUNT=0
OFFLINE_REPARSE_SCHEDULED_ENTRY_COUNT=100
OFFLINE_REPARSE_HAS_NEXT_PAGE=true
NEW_PROVIDER_NETWORK_CALLS=0
```

L’avertissement structuré restant concerne uniquement l’absence facultative ponctuelle de
`uniqueTournament`. Les autres champs structurels observés mais volontairement non retenus sont
documentés comme connus et ignorés. Le statut historique du snapshot reste inchangé dans PostgreSQL
afin de préserver le résultat exact de la première qualification ; la réussite ci-dessus constitue
une relecture hors ligne distincte.

La validation consolidée de l’adaptation a exécuté `135` tests standards sans échec, construit le
JAR Spring Boot et confirmé `SOFASCORE_NETWORK_CALLS_EXECUTED=NO`. Les tests d’intégration ne sont
pas requis, aucune migration ni écriture de persistance n’ayant été modifiée.

```text
PERSISTED_ORIGINAL_SCHEMA_STATUS=SCHEMA_INCOMPATIBLE
PERSISTED_ORIGINAL_STATUS_MUTATED=NO
PARSER_ADAPTATION_RESULT=PASS_OFFLINE
PARSER_ADAPTATION_STANDARD_TESTS=135
PARSER_ADAPTATION_CONSOLIDATED_VERIFY=PASS
J3_REAL_REQUALIFICATION=NOT_EXECUTED
J3_WORK_ORDER=IN_DEVELOPMENT
```

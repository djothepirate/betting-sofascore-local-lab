# J3 — Rapport Windows de qualification humaine de la pagination dynamique

## 1. Identification

| Élément | Valeur |
|---|---|
| Projet | SofaScore Local Lab |
| Date locale de qualification | 2026-08-14 |
| Date fournisseur interrogée | 2026-08-14 |
| Environnement | Windows local |
| Jalon | J3 — collecte manuelle répétable |
| Work Order | `WO-SS-20260812-003` |
| Branche | `feat/j3-five-page-provider-call` |
| Commit qualifié | `57a187315a106bcb8aaf180dd2446edb0546318b` |
| Libellé du commit | `feat: add repeatable dynamic scheduled-events pagination` |
| Famille fournisseur | `SCHEDULED_EVENTS` |
| Mode de pagination | `HAS_NEXT_PAGE` |
| Première page fournisseur | Page `1` |
| Plafond local | Page `25` |
| Pages réellement appelées | Pages `1` à `10` |
| Dernière page annoncée | Page `10` |
| Cookies, jetons, compte ou session | `NONE` |
| Retry automatique | `NO` |
| Polling ou planification | `NO` |
| Résultat du transport | `PASS — HTTP 200` sur les dix pages |
| Résultat de persistance | `PASS — INSERTED` sur les dix pages |
| Résultat du parseur | `PASS — PARSED` sur les dix pages |
| Résultat de la terminaison dynamique | `PASS — hasNextPage=false` en page `10` |
| Résultat des garde-fous terminaux | `PASS` |
| Résultat global | `PASS` |
| Décision documentaire | `DYNAMIC_PAGINATION_QUALIFIED` |

## 2. Objet du rapport

Ce rapport consigne la qualification humaine Windows du parcours J3 de collecte manuelle
répétable à pagination dynamique. Elle succède à la qualification bornée du `2026-08-13`, qui
avait conservé cinq pages, puis à l’unité technique supprimant cette hypothèse fixe du parcours
actif.

La qualification devait vérifier qu’une nouvelle date peut être sélectionnée depuis l’interface et
que le nombre de pages n’est plus connu à l’avance. La progression doit dépendre exclusivement du
booléen `hasNextPage` produit par `scheduled-events-v1` après persistance du snapshot brut.

Le modèle d’URI qualifié reste :

```text
https://www.sofascore.com/api/v1/sport/football/scheduled-tournaments/<date>/page/<page_number>
```

avec une date au format `AAAA-MM-JJ` et une page entière positive. Aucune URI complète réellement
appelée n’est reproduite dans la preuve minimisée ni dans ce rapport.

## 3. Gouvernance et limites maintenues

```text
EXPERIMENTAL
LOCAL_ONLY
NOT_PRODUCTION_APPROVED
NO_CRITICAL_DEPENDENCY
```

Les limites suivantes sont restées actives pendant la qualification :

- application exposée uniquement sur `127.0.0.1:8087` ;
- PostgreSQL local disponible ;
- connecteur général maintenu sous `LOCKED_OFFLINE_J3_POLICY` ;
- seul le chemin spécialisé J3 `SCHEDULED_EVENTS` utilisé ;
- sélection et confirmation humaines de la date ;
- départ obligatoire à la page 1 ;
- continuation uniquement après `hasNextPage=true` ;
- terminaison normale sur `hasNextPage=false` ;
- plafond local de 25 pages et interdiction d’une page 26 ;
- concurrence maximale `1` ;
- délai minimal de trois secondes entre deux départs de page ;
- persistance du brut avant parsing ;
- arrêt au premier incident ;
- aucun retry automatique ;
- aucun cookie, jeton, compte ou état de session fournisseur ;
- aucun proxy, aucune redirection, aucun polling et aucune tâche planifiée ;
- réapplication terminale de l’arrêt global et verrouillage du circuit.

## 4. Sources de preuve et intégrité

### 4.1 Captures opérateur

Neuf captures humaines ont été fournies. Elles couvrent les états suivants :

1. tableau de bord disponible, PostgreSQL `AVAILABLE`, cinq snapshots historiques et statut
   `COLLECTE MANUELLE DYNAMIQUE PRÊTE` ;
2. démarrage sûr avec arrêt global `ACTIF`, circuit `LOCKED` et motif `STARTUP_LOCK` ;
3. levée distincte de l’arrêt global, circuit encore verrouillé ;
4. activation explicite du circuit et sélection de la date `14/08/2026` ;
5. préparation de l’intention dynamique avec plafond local de 25 pages ;
6. saisie exacte de la phrase éphémère et acquittement des limites ;
7. état `CONFIRMED_READY` et disponibilité du bouton final distinct ;
8. résultat `COMPLETED`, dix pages collectées et arrêt global réappliqué ;
9. affichage de la preuve terminale minimisée v3 et disponibilité de son téléchargement.

Les captures restent hors Git. Le présent rapport n’en conserve que les observations techniques
nécessaires à la qualification.

### 4.2 Fichier de preuve minimisée

| Élément | Valeur |
|---|---|
| Nom local reçu | `J3-MINIMIZED-EVIDENCE-2026-08-14.txt` |
| Version du format | `3` |
| Taille du fichier | `5129` octets |
| Nombre de champs non vides | `142` |
| Nombre de clés uniques | `142` |
| Clés dupliquées | `0` |
| SHA-256 du fichier | `6c5769ac779389416b1d59f6fc73d31e831d434821aa561a5459c9f116d46616` |
| Horodatage de génération | `2026-08-14T09:31:49.805962900Z` |
| Payload brut inclus | `NO` |
| URI fournisseur incluse | `NO` |
| En-têtes inclus | `NO` |
| Identifiant de confirmation inclus | `NO` |
| Cookie, jeton, compte ou session utilisés | `NO` |

Le fichier téléchargé reste hors Git. Son empreinte est consignée afin de permettre un contrôle
d’intégrité ultérieur sans versionner la preuve locale.

## 5. Socle technique qualifié avant l’action humaine

L’unité technique avait été validée avant l’appel réel avec le résultat suivant :

```text
PREFLIGHT_RESULT=PASS
JAVA_TARGET=25
SOURCE_GUARDRAIL_SCAN=PASS
STANDARD_TESTS=148
STANDARD_FAILURES=0
STANDARD_ERRORS=0
STANDARD_SKIPPED=0
SPRING_BOOT_JAR=BUILT
BUILD_GROUP=com.bettingproject
SERVER_ADDRESS=127.0.0.1
INTEGRATION_TESTS_EXECUTED=NO_PERSISTENCE_OR_MIGRATION_CHANGE
SOFASCORE_NETWORK_CALLS_EXECUTED_DURING_TESTS=NO
```

Les tests automatisés avaient notamment démontré hors ligne :

- une collecte terminale dès la page 1 lorsque `hasNextPage=false` ;
- une séquence de cinq pages pilotée par `true,true,true,true,false` ;
- le respect du délai minimal ;
- l’arrêt au premier incident et l’absence de retry ;
- la persistance avant parsing ;
- l’interdiction de demander une page 26 après 25 valeurs `true` ;
- la preuve minimisée v3 ;
- le réarmement explicite d’une nouvelle séquence et la sélection d’une nouvelle date.

Cette validation technique n’avait exécuté aucun appel SofaScore.

## 6. État préalable observé

Avant la collecte, l’interface a confirmé :

```text
CONNECTOR_STATUS=COLLECTE_MANUELLE_DYNAMIQUE_PRETE
BASE_URL=CONFIGURED_NOT_DISPLAYED
SERVER_ADDRESS=127.0.0.1:8087
POSTGRESQL=AVAILABLE
FLYWAY_MIGRATIONS=2
SNAPSHOTS_BEFORE_COLLECTION=5
INCIDENTS_BEFORE_COLLECTION=2
MAXIMUM_CONCURRENCY=1
MINIMUM_INTER_PAGE_DELAY=3_SECONDS
```

Les cinq snapshots préexistants correspondent à la qualification historique du `2026-08-13`.
La nouvelle séquence a produit les identifiants de snapshot `6` à `15`, tous avec un résultat de
persistance `INSERTED`. Aucun snapshot antérieur n’a été réécrit.

## 7. Tests humains du parcours opérateur

| ID | Test effectué | Résultat observé | Statut |
|---|---|---|---|
| `J3-DYN-WIN-01` | Vérifier le démarrage sûr | arrêt global `ACTIF`, circuit `LOCKED`, motif `STARTUP_LOCK` | `PASS` |
| `J3-DYN-WIN-02` | Vérifier la disponibilité du parcours | `COLLECTE MANUELLE DYNAMIQUE PRÊTE` | `PASS` |
| `J3-DYN-WIN-03` | Vérifier la liaison locale | `127.0.0.1:8087` | `PASS` |
| `J3-DYN-WIN-04` | Lever l’arrêt global | arrêt global `LEVÉ`, circuit toujours `LOCKED` | `PASS` |
| `J3-DYN-WIN-05` | Activer le circuit séparément | circuit `CLOSED`, activation opérateur `OUI` | `PASS` |
| `J3-DYN-WIN-06` | Sélectionner une nouvelle date | date `2026-08-14` acceptée | `PASS` |
| `J3-DYN-WIN-07` | Préparer la collecte | départ page 1 et pagination dynamique affichés | `PASS` |
| `J3-DYN-WIN-08` | Contrôler la clé d’intention | `SCHEDULED_EVENTS|date=2026-08-14|pagination=has-next-page|max=25` | `PASS` |
| `J3-DYN-WIN-09` | Contrôler la phrase éphémère | date, pagination dynamique et plafond 25 présents | `PASS` |
| `J3-DYN-WIN-10` | Acquitter les limites | page 1, `hasNextPage`, plafond et absence de session acquittés | `PASS` |
| `J3-DYN-WIN-11` | Confirmer sans transporter | état `CONFIRMED_READY`; aucun transport par la confirmation seule | `PASS` |
| `J3-DYN-WIN-12` | Contrôler l’action finale distincte | bouton `PAGE 1 À N` disponible après confirmation | `PASS` |
| `J3-DYN-WIN-13` | Déclencher la collecte | action humaine finale exécutée une seule fois | `PASS` |
| `J3-DYN-WIN-14` | Vérifier la fin dynamique | dix pages collectées, arrêt sur `hasNextPage=false` | `PASS` |
| `J3-DYN-WIN-15` | Vérifier l’état terminal | `COMPLETED`, arrêt global actif, circuit verrouillé | `PASS` |
| `J3-DYN-WIN-16` | Télécharger la preuve | preuve minimisée v3 disponible | `PASS` |

## 8. Qualification réelle des pages 1 à 10

### 8.1 Portée réellement exécutée

```text
COLLECTION_DATE=2026-08-14
PAGINATION_MODE=HAS_NEXT_PAGE
PROVIDER_FIRST_PAGE=1
MAXIMUM_PAGE_LIMIT=25
PAGES_ATTEMPTED=1,2,3,4,5,6,7,8,9,10
PAGES_COMPLETED_COUNT=10
LAST_COMPLETED_PAGE=10
FAILED_PAGE=NONE
TERMINAL_CODE=NONE
```

La séquence est complète et strictement croissante. La page 11 n’a pas été demandée.

### 8.2 Résultats par page

| Page | Départ UTC | HTTP | Latence | Snapshot | Persistance | Taille brute | Schéma | `hasNextPage` |
|---:|---|---:|---:|---:|---|---:|---|---|
| 1 | `09:31:22.605886400Z` | `200` | `895 ms` | `6` | `INSERTED` | `204670` octets | `PARSED` | `true` |
| 2 | `09:31:25.619081700Z` | `200` | `113 ms` | `7` | `INSERTED` | `193265` octets | `PARSED` | `true` |
| 3 | `09:31:28.619221500Z` | `200` | `132 ms` | `8` | `INSERTED` | `170686` octets | `PARSED` | `true` |
| 4 | `09:31:31.621480100Z` | `200` | `168 ms` | `9` | `INSERTED` | `178261` octets | `PARSED` | `true` |
| 5 | `09:31:34.636048200Z` | `200` | `88 ms` | `10` | `INSERTED` | `194882` octets | `PARSED` | `true` |
| 6 | `09:31:37.638529600Z` | `200` | `71 ms` | `11` | `INSERTED` | `148271` octets | `PARSED` | `true` |
| 7 | `09:31:40.640553300Z` | `200` | `77 ms` | `12` | `INSERTED` | `179183` octets | `PARSED` | `true` |
| 8 | `09:31:43.646315500Z` | `200` | `144 ms` | `13` | `INSERTED` | `173414` octets | `PARSED` | `true` |
| 9 | `09:31:46.649247400Z` | `200` | `180 ms` | `14` | `INSERTED` | `183714` octets | `PARSED` | `true` |
| 10 | `09:31:49.655920300Z` | `200` | `126 ms` | `15` | `INSERTED` | `49616` octets | `PARSED` | `false` |

Le volume brut total nouvellement conservé est :

```text
NEW_RAW_PAYLOAD_BYTES=1675962
```

Les dix réponses possèdent des tailles et des empreintes SHA-256 consignées distinctes. Aucun
payload brut n’est reproduit dans ce rapport.

### 8.3 Empreintes de persistance

| Page | SHA-256 |
|---:|---|
| 1 | `acf7f1fd46b833b41f2b6260acff9702a7fe0301bb00f1ad5c729aaad99256e2` |
| 2 | `9c2c401a49ffac2d9ad13aa18df5254f4b45b5b013204793006a7187df813b30` |
| 3 | `bbc9c54b988e52dc8bf7c1d423d8601c100407b532a3633f8d61e3603383695b` |
| 4 | `f9baf9f6973057fff9b306d4570e0c6cb69d6334f00dfec6cc04ac37e8db0ebe` |
| 5 | `b05702d5bc8dc93eb1427762179efa43cc38b6dc4132071293bac31e0bdfdf0a` |
| 6 | `6ba198141d6ba738f4be0f08751e6c0673e9c005135df2ce18d27d85ce2d285d` |
| 7 | `f2c845e689c9bccc4ca25ba9f380023f4f3a79d4a2590fd000cd9f0342d9fbb3` |
| 8 | `47c5a064507e48643ac4670f69c773d5454cbd8b3c3a80251b33a90189ee1429` |
| 9 | `f7bb25f5a478a9049499965cd480cc7db12240967a65407d58d43521dc0cc2cc` |
| 10 | `c70247d0bbd0acacda5f2c214827f1ca4e9d6a65451e3175955657935f439761` |

Chaque empreinte est une valeur hexadécimale SHA-256 complète de 64 caractères.

## 9. Validation de la pagination dynamique

La suite de décisions du parseur est :

```text
PAGE_1_HAS_NEXT_PAGE=true
PAGE_2_HAS_NEXT_PAGE=true
PAGE_3_HAS_NEXT_PAGE=true
PAGE_4_HAS_NEXT_PAGE=true
PAGE_5_HAS_NEXT_PAGE=true
PAGE_6_HAS_NEXT_PAGE=true
PAGE_7_HAS_NEXT_PAGE=true
PAGE_8_HAS_NEXT_PAGE=true
PAGE_9_HAS_NEXT_PAGE=true
PAGE_10_HAS_NEXT_PAGE=false
```

Cette suite démontre que :

- le parcours ne s’est pas arrêté à l’ancienne limite historique de cinq pages ;
- les pages 6 à 10 ont été découvertes à partir des réponses précédentes ;
- chaque continuation a été autorisée par une valeur booléenne `true` parsée ;
- la valeur `false` de la page 10 a provoqué une terminaison normale ;
- aucune tentative de page 11 n’a été enregistrée ;
- le plafond local de 25 pages n’a pas déterminé la terminaison.

La variabilité de la cardinalité fournisseur est désormais établie sur deux dates :

| Date | Pages observées | Signal terminal |
|---|---:|---|
| `2026-08-13` | `5` | `hasNextPage=false` en page 5 |
| `2026-08-14` | `10` | `hasNextPage=false` en page 10 |

## 10. Respect du délai et de la concurrence

Les départs de page sont espacés d’au moins trois secondes :

```text
MINIMUM_REQUIRED_INTERVAL_MS=3000
MINIMUM_OBSERVED_INTERVAL_MS=3000.140
MAXIMUM_OBSERVED_INTERVAL_MS=3014.568
MINIMUM_DELAY_POLICY=PASS
MAXIMUM_CONCURRENCY=1
```

La séquence s’est déroulée entre le premier départ et la dernière réception en :

```text
COLLECTION_ELAPSED_MS=27176.737
TOTAL_HTTP_LATENCY_MS=1994
AVERAGE_HTTP_LATENCY_MS=199.4
```

Aucun chevauchement, retry ou départ hors ordre n’est visible dans la preuve.

## 11. Validation des politiques terminales

| ID | Politique contrôlée | Résultat observé | Statut |
|---|---|---|---|
| `J3-DYN-TERM-01` | Départ à la page 1 | première tentative page 1 | `PASS` |
| `J3-DYN-TERM-02` | Ordre strict | pages tentées exactement `1` à `10` | `PASS` |
| `J3-DYN-TERM-03` | Pagination pilotée | `true` pages 1–9, `false` page 10 | `PASS` |
| `J3-DYN-TERM-04` | Absence de page surnuméraire | aucune tentative page 11 | `PASS` |
| `J3-DYN-TERM-05` | Plafond de sécurité | limite 25 déclarée, non atteinte | `PASS` |
| `J3-DYN-TERM-06` | Délai minimal | intervalle minimal `3000.140 ms` | `PASS` |
| `J3-DYN-TERM-07` | Persistance avant parsing | dix snapshots enregistrés puis classés | `PASS` |
| `J3-DYN-TERM-08` | Absence de retry | `AUTOMATIC_RETRY_EXECUTED=NO` | `PASS` |
| `J3-DYN-TERM-09` | Absence de polling | `POLLING_OR_SCHEDULE_EXECUTED=NO` | `PASS` |
| `J3-DYN-TERM-10` | Absence de session fournisseur | `COOKIES_TOKENS_ACCOUNT_SESSION_USED=NO` | `PASS` |
| `J3-DYN-TERM-11` | Absence d’échec | `FAILED_PAGE=NONE`, `TERMINAL_CODE=NONE` | `PASS` |
| `J3-DYN-TERM-12` | Réapplication de l’arrêt global | `FINAL_GLOBAL_STOP=ACTIVE` | `PASS` |
| `J3-DYN-TERM-13` | Verrouillage du circuit | `FINAL_CIRCUIT_STATE=LOCKED` | `PASS` |
| `J3-DYN-TERM-14` | Motif terminal | `MANUAL_COLLECTION_TERMINAL_LOCK` | `PASS` |
| `J3-DYN-TERM-15` | Minimisation de la preuve | aucun brut, URI, en-tête ou identifiant de confirmation | `PASS` |

## 12. Résultat terminal

Après la collecte, l’interface et la preuve ont confirmé :

```text
TERMINAL_STATE=COMPLETED
PAGES_COMPLETED_COUNT=10
LAST_COMPLETED_PAGE=10
FAILED_PAGE=NONE
TERMINAL_CODE=NONE
FINAL_GLOBAL_STOP=ACTIVE
FINAL_CIRCUIT_STATE=LOCKED
FINAL_CIRCUIT_REASON=MANUAL_COLLECTION_TERMINAL_LOCK
```

Le bouton fournisseur est redevenu bloqué. Une nouvelle collecte exige une nouvelle levée de
l’arrêt global, une nouvelle activation du circuit, une nouvelle intention et une nouvelle
confirmation. La présente exécution humaine ne déclenche qu’une seule nouvelle séquence ; le
réarmement d’une séquence suivante reste couvert par les tests automatisés de l’unité.

## 13. Validation de la preuve minimisée

| Contrôle | Valeur observée | Statut |
|---|---|---|
| Version | `3` | `PASS` |
| Clés uniques | `142 / 142` | `PASS` |
| Mode | `HAS_NEXT_PAGE` | `PASS` |
| Première page | `1` | `PASS` |
| Plafond | `25` | `PASS` |
| Pages tentées | `1` à `10` | `PASS` |
| Payload brut inclus | `NO` | `PASS` |
| URI incluse | `NO` | `PASS` |
| En-têtes inclus | `NO` | `PASS` |
| Identifiant de confirmation inclus | `NO` | `PASS` |
| Cookie, jeton, compte ou session | `NO` | `PASS` |

La preuve permet de qualifier les métadonnées de transport, de persistance, de parsing et de
pagination sans diffuser les réponses JSON ni une donnée d’authentification ou de session.

## 14. Matrice de résultat consolidée

| Domaine | Attendu | Observé | Résultat |
|---|---|---|---|
| Application locale | écoute loopback uniquement | `127.0.0.1:8087` | `PASS` |
| PostgreSQL | stockage local disponible | `AVAILABLE` | `PASS` |
| Date | sélection humaine | `2026-08-14` | `PASS` |
| Confirmation | explicite et séparée | `CONFIRMED_READY` avant transport | `PASS` |
| Action finale | distincte | bouton `PAGE 1 À N` | `PASS` |
| Première page | page 1 | page 1 | `PASS` |
| Cardinalité | dynamique | dix pages découvertes | `PASS` |
| Ancienne limite cinq pages | non active | pages 6 à 10 collectées | `PASS` |
| Transport | HTTP `2xx` | dix réponses HTTP `200` | `PASS` |
| Persistance | avant parsing | snapshots `6` à `15`, `INSERTED` | `PASS` |
| Parsing | compatible | dix résultats `PARSED` | `PASS` |
| Continuation | `hasNextPage=true` | pages 1 à 9 | `PASS` |
| Terminaison | `hasNextPage=false` | page 10 | `PASS` |
| Page après terminaison | aucune | aucune page 11 | `PASS` |
| Délai inter-pages | au moins trois secondes | minimum `3000.140 ms` | `PASS` |
| Retry | aucun | aucun | `PASS` |
| Polling | aucun | aucun | `PASS` |
| Donnée de session | aucune | aucune | `PASS` |
| État terminal | arrêt et verrou | `ACTIVE` / `LOCKED` | `PASS` |
| Preuve | minimisée | format v3, 142 clés uniques | `PASS` |

## 15. Décision de qualification

La qualification humaine est concluante. Le commit
`57a187315a106bcb8aaf180dd2446edb0546318b` est qualifié pour le périmètre suivant :

```text
J3_DYNAMIC_DATE_SELECTION=PASS
J3_DYNAMIC_PAGE_DISCOVERY=PASS
J3_PROVIDER_FIRST_PAGE_ONE=PASS
J3_STRICT_PAGE_ORDER=PASS
J3_TEN_OF_TEN_PAGES_COLLECTED=PASS
J3_PERSISTENCE_BEFORE_PARSING=PASS
J3_ALL_PAGES_HTTP_200=PASS
J3_ALL_PAGES_SCHEMA_PARSED=PASS
J3_HAS_NEXT_PAGE_CONTINUATION=PASS
J3_HAS_NEXT_PAGE_TERMINATION=PASS
J3_NO_PAGE_ELEVEN_REQUESTED=PASS
J3_MINIMUM_DELAY_THREE_SECONDS=PASS
J3_NO_AUTOMATIC_RETRY=PASS
J3_NO_POLLING_OR_SCHEDULE=PASS
J3_NO_SESSION_DATA=PASS
J3_MINIMIZED_EVIDENCE_V3=PASS
J3_FINAL_GLOBAL_STOP=PASS
J3_FINAL_CIRCUIT_LOCK=PASS
J3_DYNAMIC_PAGINATION_HUMAN_QUALIFICATION=PASS
```

Cette décision ne constitue ni une autorisation de production, ni une autorisation de polling,
de live, de planification ou d’intégration au Betting Project principal. Toute extension du
périmètre J3 reste soumise à une unité distincte.

## 16. Validation du changement documentaire

Après rédaction du présent rapport et mise à jour du Work Order, du changelog et du README, la
validation consolidée a été rejouée le 2026-08-14 dans une copie locale excluant `.env` :

```text
PREFLIGHT_RESULT=PASS
JAVA_TARGET=25
SOURCE_GUARDRAIL_SCAN=PASS
STANDARD_TESTS=148
STANDARD_FAILURES=0
STANDARD_ERRORS=0
STANDARD_SKIPPED=0
SPRING_BOOT_JAR=BUILT
VERIFY_RESULT=PASS
INTEGRATION_TESTS_EXECUTED=NO_DOCUMENTATION_ONLY_CHANGE
SOFASCORE_NETWORK_CALLS_EXECUTED=NO
```

Le changement documentaire n’ajoute ni configuration active, ni URI d’exécution, ni payload,
ni migration, ni code de transport.

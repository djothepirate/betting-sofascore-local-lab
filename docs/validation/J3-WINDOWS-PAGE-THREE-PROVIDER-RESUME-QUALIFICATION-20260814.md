# J3 — Rapport Windows de qualification humaine de la reprise fournisseur à la page 3

## 1. Identification

| Élément | Valeur |
|---|---|
| Projet | SofaScore Local Lab |
| Date locale de qualification | 2026-08-14 |
| Date fournisseur interrogée | 2026-08-13 |
| Environnement | Windows local |
| Jalon | J3 — Appel manuel borné |
| Work Order | `WO-SS-20260812-003` |
| Branche | `feat/j3-five-page-provider-call` |
| Commit qualifié | `c0d4a68068492064a11b8fd6c302c06a78c79ab0` |
| Libellé du commit | `feat: resume J3 qualification from page three` |
| Famille fournisseur | `SCHEDULED_EVENTS` |
| Checkpoints locaux préexistants | Pages `1` et `2` |
| Première page fournisseur de la reprise | Page `3` |
| Pages réellement appelées pendant la reprise | Pages `3`, `4` et `5` |
| Pages fournisseur non répétées | Pages `1` et `2` |
| Cookies, jetons, compte ou session | `NONE` |
| Retry automatique | `NO` |
| Résultat du transport | `PASS — HTTP 200` sur les pages `3`, `4` et `5` |
| Résultat de persistance | `PASS — INSERTED` sur les pages `3`, `4` et `5` |
| Résultat du parseur | `PASS — PARSED` sur les pages `3`, `4` et `5` |
| Lot logique final | `5 / 5` pages conservées et classées |
| Résultat des garde-fous terminaux | `PASS` |
| Résultat global | `PASS` |
| Décision documentaire | `FIVE_PAGE_QUALIFICATION_COMPLETED` |
| Prochain objectif | `DYNAMIC_GUI_PAGINATION` |

## 2. Objet du rapport

Ce rapport consigne la qualification humaine terminale de la reprise J3 à partir de la page 3.
Elle succède :

1. à la qualification initiale de la page 1 ;
2. à l’adaptation hors ligne du parseur au schéma qualifié de la page 1 ;
3. à la reprise arrêtée de façon sûre sur la page 2 ;
4. à l’adaptation hors ligne du parseur au schéma qualifié de la page 2 ;
5. à l’unité autorisant une reprise unique sur les seules pages 3 à 5.

La qualification devait vérifier :

- la présence et la relecture locale des checkpoints des pages 1 et 2 ;
- l’absence de snapshot préalable pour les pages 3 à 5 ;
- l’absence de nouvelle requête fournisseur pour les pages 1 et 2 ;
- la préparation d’une intention explicitement limitée aux pages 3 à 5 ;
- la confirmation humaine exacte avant toute action fournisseur ;
- le déclenchement par un bouton final distinct ;
- la collecte séquentielle des pages 3, 4 et 5 ;
- le respect du délai minimal entre deux départs de page ;
- la persistance de chaque réponse avant parsing ;
- la compatibilité de chaque page avec `scheduled-events-v1` ;
- l’absence de retry et d’incident bloquant ;
- la réapplication de l’arrêt global et du verrou terminal ;
- la production d’une preuve minimisée ;
- le blocage persistant de toute répétition de cette reprise.

La qualification d’une interrogation complète, répétable et dynamiquement paginée depuis
l’interface graphique reste hors du périmètre de cette exécution. Elle constitue le prochain
objectif fonctionnel.

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
- date de qualification limitée au `2026-08-13` ;
- reprise fournisseur limitée aux pages 3, 4 et 5 ;
- concurrence maximale `1` ;
- délai minimal de trois secondes entre deux départs de page ;
- persistance du brut avant parsing ;
- arrêt au premier incident ;
- aucun retry automatique ;
- aucun cookie, jeton, compte ou état de session fournisseur ;
- aucun proxy, aucune redirection, aucun polling et aucun live ;
- aucune répétition des pages 1 et 2 ;
- aucune seconde reprise autorisée après conservation d’une page 3, 4 ou 5.

## 4. Sources de preuve et intégrité

### 4.1 Captures opérateur

Dix captures humaines ont été fournies. Elles couvrent les états suivants :

1. tableau de bord disponible, PostgreSQL `AVAILABLE`, deux checkpoints présents et
   `REPRISE J3 PAGE 3 PRÊTE` ;
2. corpus synthétique `12 / 12` disponible et schéma fournisseur déclaré `VALIDÉ` ;
3. démarrage sûr avec arrêt global `ACTIF`, circuit `LOCKED`, motif `STARTUP_LOCK` et checkpoints
   1 et 2 validés ;
4. levée distincte de l’arrêt global, circuit encore verrouillé ;
5. activation explicite du circuit et affichage du formulaire limité aux pages 3 à 5 ;
6. préparation de l’intention `pages=3-5`, saisie de la phrase éphémère et acquittement des limites ;
7. état `CONFIRMED_READY` et disponibilité du seul bouton final `PAGES 3 À 5` ;
8. résultat `COMPLETED`, compteur `5 / 5`, arrêt global actif et verrou terminal ;
9. affichage de la preuve terminale minimisée et disponibilité de son téléchargement ;
10. après redémarrage, présence de cinq snapshots et blocage persistant
    `CONFIG_ENABLED_BUT_QUALIFICATION_BLOCKED`.

Les captures restent hors Git. Le présent rapport n’en conserve que les observations techniques
nécessaires à la qualification.

### 4.2 Fichier de preuve minimisée

| Élément | Valeur |
|---|---|
| Nom local reçu | `J3-MINIMIZED-EVIDENCE-2026-08-13 (2).txt` |
| Version du format | `2` |
| Taille du fichier | `1867` octets |
| Nombre de champs non vides | `53` |
| Nombre de clés uniques | `53` |
| Clés dupliquées | `0` |
| SHA-256 du fichier | `e95102e7775e2588d8683fcab1b01737051598c7729a61dd74ea76bad0aeab11` |
| Horodatage de génération | `2026-08-13T22:59:20.842290900Z` |
| Payload brut inclus | `NO` |
| URI fournisseur incluse | `NO` |
| En-têtes inclus | `NO` |
| Identifiant de confirmation inclus | `NO` |
| Cookie, jeton, compte ou session utilisés | `NO` |

Le fichier téléchargé reste hors Git. Son empreinte est consignée afin de permettre un contrôle
d’intégrité ultérieur sans versionner la preuve locale.

## 5. Socle technique qualifié avant l’action humaine

L’unité technique qualifiée avait été validée avant l’appel réel avec le résultat suivant :

```text
WINDOWS_PREFLIGHT=PASS
JAVA_TARGET=25
SOURCE_GUARDRAIL_SCAN=PASS
STANDARD_TESTS=147
STANDARD_FAILURES=0
STANDARD_ERRORS=0
STANDARD_SKIPPED=0
SPRING_BOOT_JAR=BUILT
BUILD_GROUP=com.bettingproject
SERVER_ADDRESS=127.0.0.1
SOFASCORE_NETWORK_CALLS_EXECUTED_DURING_TESTS=NO
```

Les tests automatisés avaient notamment démontré hors ligne que :

- les checkpoints 1 et 2 doivent être présents, uniques, intègres et reparsables ;
- chaque checkpoint doit porter un statut HTTP `2xx` et annoncer `hasNextPage=true` ;
- la présence d’une page 3, 4 ou 5 bloque toute reprise ;
- l’intention commence à la page 3 avec deux pages déjà comptabilisées ;
- le transport simulé reçoit exactement les pages `3,4,5` ;
- les pages 1 et 2 ne sont pas répétées ;
- la preuve minimisée distingue les checkpoints locaux des pages réellement tentées.

Cette validation technique n’avait exécuté aucun appel SofaScore.

## 6. État préalable observé

### 6.1 Tableau de bord et persistance

Avant la reprise, l’interface a confirmé :

```text
CONNECTOR_STATUS=REPRISE_J3_PAGE_3_READY
BASE_URL=CONFIGURED_NOT_DISPLAYED
SERVER_ADDRESS=127.0.0.1:8087
POSTGRESQL=AVAILABLE
FLYWAY_MIGRATIONS=2
SNAPSHOTS_BEFORE_RESUME=2
INCIDENTS_BEFORE_RESUME=2
LAST_RECORDED_ENDPOINT=SCHEDULED_EVENTS
LAST_RECORDED_HTTP_STATUS=200
LAST_RECORDED_PAGE=2
LAST_RECORDED_LATENCY_MS=402
```

Les deux incidents affichés étaient antérieurs à la reprise pages 3 à 5. Leur nombre est resté
inchangé après la qualification.

### 6.2 Corpus synthétique hors ligne

```text
FIXTURES_AVAILABLE=12/12
PARSED=7
EXPECTED_SCHEMA_BREAKS=4
EXPECTED_UNEXPECTED_CONTENT=1
LOAD_FAILURES=0
PARSER=scheduled-events-v1
PROVIDER_SCHEMA=VALIDATED
```

### 6.3 Checkpoints des pages 1 et 2

La politique de reprise a relu les deux snapshots locaux et a confirmé pour chacun :

- l’unicité du snapshot ;
- la cohérence entre les octets, la taille et le SHA-256 ;
- un statut HTTP `2xx` ;
- un résultat courant `PARSED` avec `scheduled-events-v1` ;
- `hasNextPage=true` ;
- l’absence de snapshot préalable pour les pages 3, 4 et 5.

Les classifications historiques des pages 1 et 2 n’ont pas été modifiées. Les deux pages ont été
utilisées uniquement comme checkpoints locaux et n’ont pas été redemandées au fournisseur.

## 7. Tests humains du parcours opérateur

| ID | Test effectué | Résultat observé | Statut |
|---|---|---|---|
| `J3-P3-WIN-01` | Vérifier le démarrage sûr | Arrêt global `ACTIF`, circuit `LOCKED`, motif `STARTUP_LOCK` | `PASS` |
| `J3-P3-WIN-02` | Vérifier les préconditions persistées | Checkpoints 1 et 2 validés ; pages 3 à 5 absentes | `PASS` |
| `J3-P3-WIN-03` | Vérifier la disponibilité de la reprise | `REPRISE J3 PAGE 3 PRÊTE` | `PASS` |
| `J3-P3-WIN-04` | Vérifier la liaison locale | `127.0.0.1:8087` | `PASS` |
| `J3-P3-WIN-05` | Lever l’arrêt global | Arrêt global `LEVÉ`, circuit toujours `LOCKED` | `PASS` |
| `J3-P3-WIN-06` | Activer le circuit séparément | Circuit `CLOSED`, activation opérateur `OUI` | `PASS` |
| `J3-P3-WIN-07` | Préparer la date qualifiée | Date `2026-08-13`, portée pages `3-5` | `PASS` |
| `J3-P3-WIN-08` | Contrôler la clé d’intention | `SCHEDULED_EVENTS|date=2026-08-13|pages=3-5` | `PASS` |
| `J3-P3-WIN-09` | Contrôler la phrase éphémère | La phrase contient explicitement `REPRISE PAGES 3-5` | `PASS` |
| `J3-P3-WIN-10` | Acquitter les limites | Date, checkpoints 1/2, pages 3-5 et absence de session acquittés | `PASS` |
| `J3-P3-WIN-11` | Confirmer sans transporter | État `CONFIRMED_READY`; aucun transport par la confirmation seule | `PASS` |
| `J3-P3-WIN-12` | Contrôler l’action finale distincte | Seul le bouton `PAGES 3 À 5` est disponible | `PASS` |
| `J3-P3-WIN-13` | Déclencher la reprise | Action humaine finale exécutée une seule fois | `PASS` |

## 8. Qualification réelle des pages 3 à 5

### 8.1 Portée réellement exécutée

La preuve terminale annonce :

```text
QUALIFICATION_SCOPE=PAGES_1_2_3_4_5
VERIFIED_LOCAL_CHECKPOINT_PAGES=1,2
PROVIDER_RESUME_FIRST_PAGE=3
PAGES_ATTEMPTED=3,4,5
PAGE_1_REPEATED=NO
PAGE_2_REPEATED=NO
```

Le fichier ne contient aucun champ `PAGE_1_REQUESTED_AT` ni `PAGE_2_REQUESTED_AT`. Les seuls champs
de départ fournisseur sont `PAGE_3_REQUESTED_AT`, `PAGE_4_REQUESTED_AT` et
`PAGE_5_REQUESTED_AT`.

### 8.2 Résultats par page

| Page | Départ UTC | Réception UTC | HTTP | Latence | Snapshot | Persistance | Taille brute | Schéma |
|---:|---|---|---:|---:|---:|---|---:|---|
| 3 | `2026-08-13T22:59:14.558236600Z` | `2026-08-13T22:59:15.026658700Z` | `200` | `468 ms` | `3` | `INSERTED` | `166368` octets | `PARSED` |
| 4 | `2026-08-13T22:59:17.569926000Z` | `2026-08-13T22:59:17.640090100Z` | `200` | `70 ms` | `4` | `INSERTED` | `185925` octets | `PARSED` |
| 5 | `2026-08-13T22:59:20.585037200Z` | `2026-08-13T22:59:20.645373400Z` | `200` | `60 ms` | `5` | `INSERTED` | `143808` octets | `PARSED` |

Le volume brut total nouvellement conservé est :

```text
NEW_RAW_PAYLOAD_BYTES=496101
```

Les empreintes de persistance consignées sont :

```text
PAGE_3_PAYLOAD_SHA256=553bdd339ae2d9a5b925ae0f84c4875c00533c950b66ead2bb3fbd5623548a71
PAGE_4_PAYLOAD_SHA256=99bd2a057a87ddf607de975d455dae80c83337d543590b86227969fa7424dfeb
PAGE_5_PAYLOAD_SHA256=df6053380296a3f2e97ecc99b33b9dfd6b53a752e5715c42a250d08cd09a33de
```

Chaque empreinte est une valeur hexadécimale SHA-256 complète de 64 caractères. Aucun payload
brut n’est reproduit dans ce rapport.

### 8.3 Respect du délai minimal

Les intervalles calculés entre les départs sont :

```text
PAGE_3_TO_PAGE_4_INTERVAL_MS=3011.689
PAGE_4_TO_PAGE_5_INTERVAL_MS=3015.111
MINIMUM_REQUIRED_INTERVAL_MS=3000
MINIMUM_DELAY_POLICY=PASS
```

La concurrence `1` et le délai minimal de trois secondes ont donc été respectés.

### 8.4 Transport, persistance et parsing

| Contrôle | Page 3 | Page 4 | Page 5 | Résultat global |
|---|---|---|---|---|
| Réponse reçue | oui | oui | oui | `PASS` |
| HTTP `2xx` | `200` | `200` | `200` | `PASS` |
| Snapshot enregistré | oui | oui | oui | `PASS` |
| Résultat de persistance | `INSERTED` | `INSERTED` | `INSERTED` | `PASS` |
| Parsing | `PARSED` | `PARSED` | `PARSED` | `PASS` |
| Code terminal par page | `NONE` | `NONE` | `NONE` | `PASS` |

Les résultats minimisés déterminants sont consignés sans transformation :

```text
PAGE_3_HTTP_STATUS=200
PAGE_3_PERSISTENCE_OUTCOME=INSERTED
PAGE_3_SCHEMA_STATUS=PARSED
PAGE_3_TERMINAL_CODE=NONE
PAGE_4_HTTP_STATUS=200
PAGE_4_PERSISTENCE_OUTCOME=INSERTED
PAGE_4_SCHEMA_STATUS=PARSED
PAGE_4_TERMINAL_CODE=NONE
PAGE_5_HTTP_STATUS=200
PAGE_5_PERSISTENCE_OUTCOME=INSERTED
PAGE_5_SCHEMA_STATUS=PARSED
PAGE_5_TERMINAL_CODE=NONE
```

Aucune erreur bloquante n’a été observée. Le nombre d’incidents PostgreSQL affiché est resté à
`2`, ce qui confirme qu’aucun nouvel incident n’a été créé par cette reprise.

## 9. Résultat terminal du lot logique 1 à 5

Après la reprise, l’interface et la preuve ont confirmé :

```text
TERMINAL_STATE=COMPLETED
PAGES_COMPLETED=5
FAILED_PAGE=NONE
TERMINAL_CODE=NONE
SNAPSHOTS_AFTER_RESUME=5
INCIDENTS_AFTER_RESUME=2
LAST_RECORDED_ENDPOINT=SCHEDULED_EVENTS
LAST_RECORDED_HTTP_STATUS=200
LAST_RECORDED_PAGE=5
LAST_RECORDED_LATENCY_MS=60
```

`PAGES_COMPLETED=5` représente le lot logique complet : les checkpoints locaux des pages 1 et 2,
puis les pages 3, 4 et 5 nouvellement transportées, persistées et classées.

## 10. Validation des politiques terminales

| ID | Politique contrôlée | Résultat observé | Statut |
|---|---|---|---|
| `J3-P3-TERM-01` | Absence de page 1 fournisseur | aucun champ de tentative page 1 | `PASS` |
| `J3-P3-TERM-02` | Absence de page 2 fournisseur | aucun champ de tentative page 2 | `PASS` |
| `J3-P3-TERM-03` | Séquence fixe autorisée | pages tentées exactement `3,4,5` | `PASS` |
| `J3-P3-TERM-04` | Délai minimal | intervalles supérieurs à `3000 ms` | `PASS` |
| `J3-P3-TERM-05` | Absence de retry | `AUTOMATIC_RETRY_EXECUTED=NO` | `PASS` |
| `J3-P3-TERM-06` | Absence d’échec | `FAILED_PAGE=NONE`, `TERMINAL_CODE=NONE` | `PASS` |
| `J3-P3-TERM-07` | Réapplication de l’arrêt global | `FINAL_GLOBAL_STOP=ACTIVE` | `PASS` |
| `J3-P3-TERM-08` | Verrouillage du circuit | `FINAL_CIRCUIT_STATE=LOCKED` | `PASS` |
| `J3-P3-TERM-09` | Motif terminal | `QUALIFICATION_TERMINAL_LOCK` | `PASS` |
| `J3-P3-TERM-10` | Blocage d’une seconde reprise | `J3_RESUME_ALREADY_ATTEMPTED` | `PASS` |
| `J3-P3-TERM-11` | Conservation avant parsing | snapshots `3`, `4`, `5` insérés puis classés | `PASS` |
| `J3-P3-TERM-12` | Minimisation de la preuve | aucun brut, URI, en-tête ou identifiant de confirmation | `PASS` |

## 11. Blocage persistant après redémarrage

La dernière capture, obtenue après achèvement de la reprise, montre :

```text
CONNECTOR_STATUS=CONFIG_ENABLED_BUT_QUALIFICATION_BLOCKED
POSTGRESQL=AVAILABLE
SNAPSHOTS=5
PERSISTENT_BLOCKER=J3_RESUME_ALREADY_ATTEMPTED
```

Ce résultat est attendu. La présence persistée d’une page 3, 4 ou 5 rend la politique de reprise
inéligible et empêche de recommencer la qualification après redémarrage.

```text
PERSISTENT_NON_REPETITION_GUARD=PASS
SECOND_PAGE_THREE_RESUME=BLOCKED
```

## 12. Validation de la preuve minimisée

| Contrôle | Valeur observée | Statut |
|---|---|---|
| Version | `2` | `PASS` |
| Clés uniques | `53 / 53` | `PASS` |
| Checkpoints locaux | `1,2` | `PASS` |
| Première page fournisseur | `3` | `PASS` |
| Pages tentées | `3,4,5` | `PASS` |
| Payload brut inclus | `NO` | `PASS` |
| URI incluse | `NO` | `PASS` |
| En-têtes inclus | `NO` | `PASS` |
| Identifiant de confirmation inclus | `NO` | `PASS` |
| Cookie, jeton, compte ou session | `NO` | `PASS` |
| Motif sensible détecté par contrôle mécanique | `0` | `PASS` |

La preuve permet de qualifier les métadonnées de transport, de persistance et de parsing sans
diffuser les réponses JSON ni une donnée d’authentification ou de session.

## 13. Matrice de résultat consolidée

| Domaine | Attendu | Observé | Résultat |
|---|---|---|---|
| Application locale | écoute loopback uniquement | `127.0.0.1:8087` | `PASS` |
| PostgreSQL | stockage local disponible | `AVAILABLE` | `PASS` |
| Checkpoint page 1 | présent et reparsable | validé localement | `PASS` |
| Checkpoint page 2 | présent et reparsable | validé localement | `PASS` |
| Pages 3 à 5 avant reprise | absentes | absentes | `PASS` |
| Répétition page 1 | interdite | aucune tentative page 1 | `PASS` |
| Répétition page 2 | interdite | aucune tentative page 2 | `PASS` |
| Confirmation | explicite et séparée | `CONFIRMED_READY` avant transport | `PASS` |
| Action finale | pages 3 à 5 uniquement | bouton `PAGES 3 À 5` | `PASS` |
| Transport page 3 | HTTP `2xx` | HTTP `200` | `PASS` |
| Persistance page 3 | avant parsing | snapshot `3`, `INSERTED` | `PASS` |
| Parsing page 3 | compatible | `PARSED` | `PASS` |
| Transport page 4 | HTTP `2xx` | HTTP `200` | `PASS` |
| Persistance page 4 | avant parsing | snapshot `4`, `INSERTED` | `PASS` |
| Parsing page 4 | compatible | `PARSED` | `PASS` |
| Transport page 5 | HTTP `2xx` | HTTP `200` | `PASS` |
| Persistance page 5 | avant parsing | snapshot `5`, `INSERTED` | `PASS` |
| Parsing page 5 | compatible | `PARSED` | `PASS` |
| Délai inter-pages | au moins trois secondes | `3011.689 ms`, `3015.111 ms` | `PASS` |
| Retry | aucun | aucun | `PASS` |
| Incident nouveau | aucun | compteur inchangé à `2` | `PASS` |
| Lot logique complet | cinq pages | `5 / 5` | `PASS` |
| Arrêt global terminal | actif | actif | `PASS` |
| Circuit terminal | verrouillé | verrouillé | `PASS` |
| Répétition de la reprise | bloquée | `J3_RESUME_ALREADY_ATTEMPTED` | `PASS` |
| Preuve minimisée | disponible sans contenu sensible | version `2`, `1867` octets | `PASS` |

## 14. Conclusion fonctionnelle sur l’endpoint qualifié

La qualification des cinq pages attendues est concluante. Pour une date déterminée, la famille
qualifiée permet d’obtenir la liste paginée des compétitions de football programmées pour cette
date.

Le modèle d’URI retenu est :

```text
https://www.sofascore.com/api/v1/sport/football/scheduled-tournaments/<date>/page/<page_number>
```

avec :

- `<date>` : date civile au format `AAAA-MM-JJ` ;
- `<page_number>` : entier strictement positif, de `1` à `N` ;
- `N` : nombre total de pages variable selon la carte de compétitions disponible à la date demandée.

La réponse JSON expose l’attribut racine `hasNextPage` :

| Valeur | Sémantique fonctionnelle |
|---|---|
| `true` | Une page suivante existe. |
| `false` | La page courante est la dernière page disponible. |

Le nombre `5` est donc le nombre de pages qualifiées pour la date `2026-08-13`. Il ne doit pas être
considéré comme une constante universelle de l’endpoint.

```text
QUALIFIED_DATE=2026-08-13
QUALIFIED_PAGE_COUNT=5
UNIVERSAL_PAGE_COUNT=NOT_ESTABLISHED
PAGINATION_IS_DYNAMIC=YES
PAGINATION_DRIVER=hasNextPage
```

### 14.1 Limite de la présente qualification

Le chemin J3 exécuté pendant cette qualification était volontairement borné aux pages 3 à 5. Son
orchestrateur n’est pas encore une boucle générique pilotée par `hasNextPage`.

La qualification établit donc :

- la réussite du lot logique attendu pour le `2026-08-13` ;
- la compatibilité du parseur avec les cinq pages collectées ;
- la sémantique fonctionnelle observée de `hasNextPage` ;
- la nécessité d’une pagination dynamique pour une date quelconque.

Elle ne prétend pas qu’une collecte répétable et dynamique est déjà disponible dans l’interface.

## 15. Observation d’interface non bloquante

Après le succès terminal, une capture affiche encore l’action visuelle « Lever l’arrêt global ».
Le backend du même processus refuse cependant le réarmement d’une qualification consommée et, après
redémarrage, la politique persistante bloque la préparation avec `J3_RESUME_ALREADY_ATTEMPTED`.

Cette observation est une incohérence ergonomique non bloquante. Elle ne permet ni de répéter les
pages, ni de contourner le verrou terminal.

## 16. Décision et prochain objectif

La qualification humaine est acceptée : les cinq pages attendues pour le `2026-08-13` ont été
collectées, persistées et analysées sans erreur bloquante.

Le prochain objectif est la dynamisation du processus dans l’interface graphique. Une unité
distincte devra notamment :

- permettre la sélection explicite d’une date valide ;
- commencer une nouvelle collecte à la page 1 ;
- conserver chaque réponse avant parsing ;
- lire strictement `hasNextPage` après chaque page compatible ;
- demander la page suivante seulement lorsque la valeur est `true` ;
- terminer normalement lorsque la valeur est `false` ;
- imposer une borne locale maximale de sécurité sans assimiler cette borne à `N` ;
- conserver concurrence `1` et délai minimal de trois secondes ;
- arrêter la collecte au premier incident ;
- ne jamais effectuer de retry automatique ;
- conserver les interdictions de cookie, jeton, compte, session, proxy, polling et tâche planifiée ;
- produire une preuve minimisée distinguant la date, la plage réellement tentée et la condition de
  fin de pagination.

Cette future dynamisation ne fait pas partie du présent rapport et nécessitera sa propre unité
technique, ses tests hors ligne et sa qualification humaine.

## 17. Bilan final

```text
J3_PAGE_THREE_RESUME_HUMAN_QUALIFICATION=PASS
J3_PAGE_ONE_LOCAL_CHECKPOINT=PASS
J3_PAGE_TWO_LOCAL_CHECKPOINT=PASS
J3_PAGE_ONE_PROVIDER_CALL_REPEATED=NO
J3_PAGE_TWO_PROVIDER_CALL_REPEATED=NO
J3_PAGE_THREE_TRANSPORT=PASS
J3_PAGE_THREE_HTTP_STATUS=200
J3_PAGE_THREE_PERSISTENCE=INSERTED
J3_PAGE_THREE_SCHEMA=PARSED
J3_PAGE_FOUR_TRANSPORT=PASS
J3_PAGE_FOUR_HTTP_STATUS=200
J3_PAGE_FOUR_PERSISTENCE=INSERTED
J3_PAGE_FOUR_SCHEMA=PARSED
J3_PAGE_FIVE_TRANSPORT=PASS
J3_PAGE_FIVE_HTTP_STATUS=200
J3_PAGE_FIVE_PERSISTENCE=INSERTED
J3_PAGE_FIVE_SCHEMA=PARSED
J3_PAGES_COMPLETED=5
J3_NEW_INCIDENTS=0
J3_AUTOMATIC_RETRY_EXECUTED=NO
J3_FINAL_GLOBAL_STOP=ACTIVE
J3_FINAL_CIRCUIT_STATE=LOCKED
J3_FINAL_CIRCUIT_REASON=QUALIFICATION_TERMINAL_LOCK
J3_REPEAT_RESUME=BLOCKED
J3_MINIMIZED_EVIDENCE=PASS
J3_PROVIDER_PAYLOAD_IN_GIT=NO
J3_COOKIES_TOKENS_ACCOUNT_SESSION_USED=NO
J3_FIXED_FIVE_PAGE_QUALIFICATION=COMPLETED
J3_WORK_ORDER=IN_DEVELOPMENT
NEXT_TECHNICAL_OBJECTIVE=DYNAMIC_GUI_PAGINATION
NEXT_PAGINATION_DRIVER=hasNextPage
```

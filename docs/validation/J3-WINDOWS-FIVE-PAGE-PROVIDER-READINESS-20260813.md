# J3 — Rapport Windows de correction et de préparation du chemin fournisseur cinq pages

## 1. Identification

| Élément | Valeur |
|---|---|
| Projet | SofaScore Local Lab |
| Date de validation | 2026-08-13 |
| Environnement | Windows local |
| Jalon | J3 — Appel manuel borné |
| Work Order | `WO-SS-20260812-003` |
| Branche | `feat/j3-five-page-provider-call` |
| Commit de base de l'état validé | `e2769cae1e1ce1499e33a045a411820e3d505382` |
| Libellé du commit de base | `feat: add guarded J3 five-page provider call` |
| Famille fournisseur | `SCHEDULED_EVENTS` |
| Date fournisseur prévue | `2026-08-13` |
| Pages prévues | `1,2,3,4,5` |
| Ordre | Séquentiel |
| Concurrence maximale | `1` |
| Délai minimal entre pages | `PT3S` |
| Cookies, jetons, compte ou session | `NONE` |
| Retry automatique | `NO` |
| Résultat technique pré-exécution | `PASS` |
| Appel SofaScore réel pendant cette validation | `NOT_EXECUTED` |
| Décision documentaire | `PASS_PRE_EXECUTION` |

## 2. Objet et limite de la validation

Cette session vérifie les corrections nécessaires pour rendre accessible, depuis l'interface locale,
le chemin J3 spécialisé qui pourra déclencher une seule séquence fournisseur bornée aux pages 1 à 5.
Elle couvre :

- la liaison des variables locales `SOFASCORE_*` vers `SofascoreProperties` ;
- la création correcte des composants Spring possédant plusieurs constructeurs ;
- la qualification du chemin fournisseur spécialisé ;
- le démarrage sûr avec arrêt global actif ;
- les actions distinctes de levée de l'arrêt, d'activation du circuit, de préparation et de
  confirmation ;
- la disponibilité du bouton final seulement après confirmation explicite ;
- l'exclusivité de `SCHEDULED_EVENTS` dans le catalogue appelable ;
- la présentation lisible du statut du connecteur ;
- la non-régression automatisée et la construction complète du projet.

La session s'arrête volontairement avant le clic sur le bouton final. Elle ne constitue donc pas une
qualification de la réponse réelle du fournisseur, de la persistance d'un nouveau snapshot réel ou
du comportement fournisseur observé sur les cinq URI.

Le présent document complète, sans la réécrire, la qualification historique
`docs/validation/J3-WINDOWS-MANUAL-CALL-QUALIFICATION-20260812.md`. Celle-ci décrit le parcours local
et les politiques simulées avant l'introduction du chemin fournisseur cinq pages. Les états
`CONFIRMED_BLOCKED` et `PROVIDER_ACTION_AVAILABLE=NO` qu'elle enregistre restent exacts pour le
commit et le périmètre qualifiés le 2026-08-12.

```text
VALIDATED_SCOPE=CONFIGURATION_BINDING,SPRING_WIRING,LOCAL_OPERATOR_PATH,PRE_EXECUTION_GUARDS
REAL_PROVIDER_EXECUTION=OUT_OF_SCOPE
REAL_PROVIDER_OUTPUT_PROOF=NOT_AVAILABLE
IMPLEMENTATION_NETWORK_EXECUTION=FORBIDDEN
```

## 3. Statuts de gouvernance maintenus

Les corrections ne modifient pas les statuts du laboratoire :

```text
EXPERIMENTAL
LOCAL_ONLY
NOT_PRODUCTION_APPROVED
NO_CRITICAL_DEPENDENCY
```

Les invariants suivants restent applicables :

- application liée uniquement à `127.0.0.1` ;
- connecteur fournisseur général verrouillé ;
- chemin J3 spécialisé désactivé par défaut ;
- aucun cookie, jeton, compte ou état de session fournisseur ;
- aucun proxy ni redirection ;
- aucune concurrence supérieure à `1` ;
- aucun retry automatique ;
- aucune tâche planifiée, aucun polling et aucun live ;
- aucun appel SofaScore dans les tests standards.

## 4. Problèmes observés avant correction

### 4.1 Variables locales non liées aux propriétés Spring

La configuration locale définissait les paramètres J3 nécessaires, mais l'interface continuait à
présenter des états bloquants :

```text
J3_QUALIFICATION_DISABLED
CONNECTOR_DISABLED
SCHEDULED_EVENTS_NOT_EXCLUSIVELY_ALLOWED
```

Dans `application.yml`, certaines valeurs étaient encore statiques :

```yaml
enabled: false
j3-qualification-enabled: false
allowed-endpoints: []
```

L'import de la configuration locale ne pouvait donc pas remplacer ces valeurs par les clés
`SOFASCORE_*` documentées.

### 4.2 Constructeurs Spring ambigus

Les classes suivantes possédaient plusieurs constructeurs, dont des variantes destinées aux tests :

- `ProviderScheduledEventsRestTransport` ;
- `J3FivePageManualCallService` ;
- `J3ManualCallControlService`.

Le constructeur de production n'était pas explicitement sélectionné pour l'injection Spring. Le
contexte ne pouvait pas instancier de manière déterministe le chemin fournisseur réel borné.

### 4.3 Test de non-régression initial insuffisant

Le premier test construit manuellement un objet `SofascoreProperties`, appelle ses setters puis
évalue la politique J3. Son exécution était concluante, mais il ne traversait pas la chaîne de
configuration qui avait réellement échoué :

```text
variables SOFASCORE_*
        ↓
application.yml
        ↓
binding Spring Boot
        ↓
SofascoreProperties
        ↓
J3ProviderQualificationPolicy
```

### 4.4 Conflit visuel du statut du connecteur

Le statut du connecteur conservait la classe statique `danger`, puis ajoutait `safe` lorsque la
qualification devenait disponible. Les deux classes étaient alors présentes simultanément et la
règle `.danger` gagnait dans la cascade CSS. Le statut disponible apparaissait en rouge et son
identifiant technique se repliait difficilement dans la carte.

## 5. Corrections réalisées

### 5.1 Liaison explicite des variables `SOFASCORE_*`

`application.yml` utilise désormais des placeholders explicites tout en conservant des valeurs par
défaut sûres :

```yaml
sofascore:
  enabled: ${SOFASCORE_ENABLED:false}
  j3-qualification-enabled: ${SOFASCORE_J3_QUALIFICATION_ENABLED:false}
  base-url: ${SOFASCORE_BASE_URL:}
  allowed-endpoints: ${SOFASCORE_ALLOWED_ENDPOINTS:}
```

En l'absence de configuration locale explicite :

```text
SOFASCORE_ENABLED=false
SOFASCORE_J3_QUALIFICATION_ENABLED=false
SOFASCORE_BASE_URL=EMPTY
SOFASCORE_ALLOWED_ENDPOINTS=EMPTY
```

Le transport demeure donc bloqué par défaut.

### 5.2 Sélection explicite des constructeurs de production

L'annotation `@Autowired` sélectionne désormais le constructeur de production dans :

- `ProviderScheduledEventsRestTransport` ;
- `J3FivePageManualCallService` ;
- `J3ManualCallControlService`.

Les constructeurs secondaires employés par les tests restent disponibles, sans ambiguïté pour le
conteneur Spring.

### 5.3 Test de liaison Spring réel

`SofascorePropertiesTest` utilise maintenant `ApplicationContextRunner` avec
`ConfigDataApplicationContextInitializer`. Le test fournit les clés documentées et vérifie les
propriétés réellement liées par Spring :

```text
SOFASCORE_ENABLED=true
SOFASCORE_J3_QUALIFICATION_ENABLED=true
SOFASCORE_BASE_URL=EXPECTED_ORIGIN
SOFASCORE_ALLOWED_ENDPOINTS=SCHEDULED_EVENTS
```

Les assertions couvrent :

- `enabled=true` ;
- `j3QualificationEnabled=true` ;
- l'origine fournisseur exacte ;
- la présence exclusive de `SCHEDULED_EVENTS` ;
- `J3ProviderQualificationPolicy.snapshot().available()=true`.

### 5.4 Présentation du statut du connecteur

Le template choisit désormais une seule classe CSS et affiche un libellé humain :

```html
<p th:class="${dashboard.qualificationTransportAvailable}
             ? 'metric safe'
             : 'metric danger'"
   th:text="${dashboard.qualificationTransportAvailable
             ? 'QUALIFICATION J3 PRÊTE'
             : (dashboard.configurationEnabled
                ? 'CONFIG_ENABLED_BUT_QUALIFICATION_BLOCKED'
                : 'DISABLED')}">DISABLED</p>
```

La correction garantit :

- un statut prêt affiché en vert ;
- un statut bloqué affiché en rouge ;
- l'absence de coexistence entre `safe` et `danger` ;
- un retour à la ligne entre des mots ;
- un fichier UTF-8 avec le libellé `PRÊTE` intact ;
- une indentation composée uniquement d'espaces.

## 6. Tests automatisés réalisés

### 6.1 Test préliminaire sous Eclipse

Le test initial ajouté manuellement a produit :

```text
RUNS=1
ERRORS=0
FAILURES=0
RESULT=PASS_WITH_COVERAGE_LIMITATION
```

Il prouvait que la politique devenait disponible à partir d'un objet configuré manuellement, mais
pas que Spring liait correctement les variables documentées.

### 6.2 Test ciblé corrigé

Commande exécutée :

```powershell
.\mvnw.cmd '-Dtest=SofascorePropertiesTest' test
```

Résultat :

```text
TEST_CLASS=SofascorePropertiesTest
TESTS=4
FAILURES=0
ERRORS=0
SKIPPED=0
RESULT=PASS
```

### 6.3 Validation Maven finale

Après l'ensemble des corrections, y compris le nettoyage final du template :

```powershell
.\mvnw.cmd clean verify
```

Résultat final du 2026-08-13 :

```text
JAVA=25.0.4
SPRING_BOOT=4.1.0
MAIN_SOURCES_COMPILED=91
TEST_SOURCES_COMPILED=33
TEST_SUITES=32
TESTS=128
FAILURES=0
ERRORS=0
SKIPPED=0
JAR_BUILD=PASS
BUILD=SUCCESS
TOTAL_TIME_SECONDS=13.830
REAL_SOFASCORE_CALLS_EXECUTED=0
```

L'artefact construit est :

```text
target/betting-sofascore-local-lab-0.1.0-SNAPSHOT.jar
```

Une première tentative a été empêchée par l'interdiction d'accès du bac à sable à Maven Central.
Après autorisation de résolution des dépendances, la même commande a réussi. Cet événement n'était
ni un échec de compilation ni un échec de test du projet.

### 6.4 Répartition des tests

| Domaine | Tests | Résultat |
|---|---:|---|
| Adaptateurs SofaScore | 21 | PASS |
| Interface Web | 8 | PASS |
| Service du corpus hors ligne | 2 | PASS |
| Réseau et politiques J3 | 57 | PASS |
| Identité du build | 1 | PASS |
| Configuration | 9 | PASS |
| Domaine fournisseur | 9 | PASS |
| Fixtures | 19 | PASS |
| Sécurité locale | 2 | PASS |
| **Total** | **128** | **PASS** |

Les suites directement liées au chemin J3 comprennent notamment :

```text
ProviderScheduledEventsRestTransportTest=1/1 PASS
DashboardControllerTest=1/1 PASS
DashboardServiceTest=1/1 PASS
ManualCallControllerTest=6/6 PASS
J3FivePageManualCallServiceTest=2/2 PASS
J3GuardedScheduledEventsTransportTest=2/2 PASS
J3ManualCallControlServiceTest=5/5 PASS
J3ManualCallPolicyTest=9/9 PASS
J3NetworkCircuitTest=17/17 PASS
J3ProviderQualificationPolicyTest=3/3 PASS
J3SingleCallGuardTest=3/3 PASS
J3TransportStopAndIncidentPoliciesTest=16/16 PASS
SofascorePropertiesTest=4/4 PASS
ScheduledEventsProviderPageRequestTest=2/2 PASS
```

### 6.5 Contrôle du diff

Commande exécutée :

```powershell
git diff --check
```

Résultat :

```text
WHITESPACE_ERRORS=0
ENCODING_READY_LABEL=UTF8_PASS
RESULT=PASS
```

## 7. Validation manuelle de l'interface Windows

Les captures fournies par le propriétaire ont été examinées comme preuves visuelles. Elles restent
hors Git ; seule cette synthèse minimisée est versionnée.

| ID | Action ou contrôle | Résultat observé | Statut |
|---|---|---|---|
| J3-READY-WIN-01 | Charger la configuration J3 | Connecteur `QUALIFICATION J3 PRÊTE`, origine configurée mais non affichée | PASS |
| J3-READY-WIN-02 | Vérifier le démarrage sûr | Arrêt global `ACTIF`, circuit `LOCKED`, motif `STARTUP_LOCK`, activation `NON` | PASS |
| J3-READY-WIN-03 | Lever l'arrêt global | Arrêt levé ; le circuit reste `LOCKED` jusqu'à une action distincte | PASS |
| J3-READY-WIN-04 | Activer le circuit | Circuit `CLOSED`, activation opérateur `OUI`, incident actif `NON` | PASS |
| J3-READY-WIN-05 | Préparer la date | Date `2026-08-13`, pages fixes `1-5` | PASS |
| J3-READY-WIN-06 | Préparer l'intention | État `AWAITING_CONFIRMATION`, clé canonique datée et bornée aux cinq pages | PASS |
| J3-READY-WIN-07 | Confirmer explicitement | État `CONFIRMED_READY`, phrase exacte et acquittement requis | PASS |
| J3-READY-WIN-08 | Vérifier l'action finale | Bouton `Lancer le lot fournisseur unique — PAGES 1 À 5` disponible | PASS |
| J3-READY-WIN-09 | Vérifier le catalogue | `SCHEDULED_EVENTS` seul marqué appelable avec URI configurée | PASS |
| J3-READY-WIN-10 | Vérifier le connecteur général | Connecteur général toujours verrouillé ; seul le chemin J3 dédié est prêt | PASS |
| J3-READY-WIN-11 | Vérifier l'absence d'exécution | Aucun clic sur l'action finale, aucun appel fournisseur exécuté | PASS |

La phrase de confirmation active, son identifiant, son jeton de formulaire et tout secret local ne
sont pas reproduits dans ce rapport.

## 8. Résultats obtenus

### 8.1 Configuration et injection

```text
SOFASCORE_ENV_BINDING=PASS
SOFASCORE_ENABLED=true
SOFASCORE_J3_QUALIFICATION_ENABLED=true
SOFASCORE_BASE_URL=CONFIGURED_NOT_DISPLAYED
SOFASCORE_ALLOWED_ENDPOINTS=SCHEDULED_EVENTS
PROVIDER_TRANSPORT_CONSTRUCTOR_INJECTION=PASS
FIVE_PAGE_SERVICE_CONSTRUCTOR_INJECTION=PASS
MANUAL_CONTROL_SERVICE_CONSTRUCTOR_INJECTION=PASS
SPRING_CONTEXT_STARTUP=PASS
```

### 8.2 Politique et parcours opérateur

```text
J3_PROVIDER_QUALIFICATION=READY
GENERAL_CONNECTOR=LOCKED
SCHEDULED_EVENTS=EXCLUSIVELY_ALLOWED
PAGE_SCOPE=1,2,3,4,5
MAXIMUM_CONCURRENCY=1
MINIMUM_DELAY=PT3S
STARTUP_GLOBAL_STOP=PASS
EXPLICIT_GLOBAL_STOP_RELEASE=PASS
EXPLICIT_CIRCUIT_ACTIVATION=PASS
INTENT_PREPARATION=PASS
ONE_TIME_PHRASE_CONFIRMATION=PASS
CONFIRMED_STATE=CONFIRMED_READY
UNIQUE_PROVIDER_ACTION_BUTTON=AVAILABLE
```

### 8.3 Présentation

```text
CONNECTOR_READY_LABEL=QUALIFICATION_J3_PRÊTE
READY_LABEL_COLOR=SAFE_GREEN
SAFE_DANGER_CONFLICT=RESOLVED
THYMELEAF_EXPRESSION=VALID
INDENTATION=SPACES_ONLY
UTF8_LABEL=PASS
```

### 8.4 Absence d'appel réel

```text
REAL_SOFASCORE_CALL_EXECUTED=NO
PROVIDER_PAGES_REQUESTED=0
RAW_PROVIDER_SNAPSHOTS_PERSISTED=0
PROVIDER_INCIDENTS_RECORDED=0
QUALIFICATION_CONSUMED=NO
```

L'état `CONFIRMED_READY` signifie que l'action finale est disponible. Il ne signifie pas qu'une
requête a déjà été envoyée.

## 9. Fichiers de code et de configuration corrigés

| Fichier | Correction |
|---|---|
| `src/main/java/com/bettingproject/sofascorelocal/adapter/sofascore/transport/ProviderScheduledEventsRestTransport.java` | Constructeur de production sélectionné par Spring |
| `src/main/java/com/bettingproject/sofascorelocal/application/network/J3FivePageManualCallService.java` | Constructeur de production sélectionné par Spring |
| `src/main/java/com/bettingproject/sofascorelocal/application/network/J3ManualCallControlService.java` | Constructeur de production sélectionné par Spring |
| `src/main/resources/application.yml` | Placeholders explicites pour les variables `SOFASCORE_*` |
| `src/main/resources/templates/dashboard.html` | Classes d'état exclusives, libellé lisible et indentation normalisée |
| `src/test/java/com/bettingproject/sofascorelocal/config/SofascorePropertiesTest.java` | Test réel de liaison Spring et de disponibilité de la politique J3 |

Avant l'ajout du présent rapport, le diff de ces six fichiers représentait :

```text
FILES_CHANGED=6
INSERTIONS=64
DELETIONS=6
DIFF_CHECK=PASS
```

## 10. Observation non bloquante

Maven signale l'auto-attachement de Mockito et le chargement dynamique de l'agent Byte Buddy avec
Java 25. Cet avertissement :

- ne provoque aucun échec ;
- n'affecte pas les 128 tests ;
- n'affecte pas l'artefact construit ;
- pourra faire l'objet d'une maintenance Maven ultérieure lorsque le chargement dynamique des agents
  sera désactivé par défaut.

```text
OBS-J3-READY-001=MOCKITO_DYNAMIC_AGENT_WARNING
SEVERITY=NON_BLOCKING
```

## 11. Bilan

```text
J3_CONFIGURATION_BINDING_CORRECTION=PASS
J3_SPRING_INJECTION_CORRECTION=PASS
J3_REGRESSION_TEST_CORRECTION=PASS
J3_CONNECTOR_PRESENTATION_CORRECTION=PASS
J3_WINDOWS_PRE_EXECUTION_QUALIFICATION=PASS
J3_STANDARD_TESTS=128
J3_STANDARD_TEST_FAILURES=0
J3_BUILD=SUCCESS
J3_DEDICATED_PATH=CONFIRMED_READY
J3_GENERAL_CONNECTOR=LOCKED
J3_REAL_PROVIDER_CALL=NOT_EXECUTED
J3_REAL_PROVIDER_OUTPUT_PROOF=NOT_AVAILABLE
J3_IMPLEMENTATION_NETWORK_EXECUTION=FORBIDDEN
J3_OWNER_EXECUTION=NOT_PERFORMED
J3_WORK_ORDER=IN_DEVELOPMENT
```

## 12. Décision

Les corrections de configuration, d'injection Spring, de test de non-régression et de présentation
sont acceptées. Le chemin J3 spécialisé est techniquement prêt jusqu'à la frontière précédant
l'appel réel.

La disponibilité du bouton final ne clôture pas J3 et ne vaut pas preuve fournisseur. La prochaine
qualification, déclenchée ultérieurement et distinctement par le propriétaire, devra enregistrer
séparément l'exécution manuelle unique, les pages effectivement atteintes, les snapshots bruts
persistés, tout incident éventuel et la preuve de sortie minimisée, sans reproduire de payload
complet, cookie, jeton ou secret dans Git. Le présent rapport n'accorde aucune autorisation réseau
supplémentaire.

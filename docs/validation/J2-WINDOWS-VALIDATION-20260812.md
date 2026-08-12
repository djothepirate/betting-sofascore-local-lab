# Rapport de validation manuelle J2 — Corpus hors ligne et tableau de bord local

## 1. Identification

| Élément | Valeur |
|---|---|
| Projet | SofaScore Local Lab |
| Date de validation | 2026-08-12 |
| Environnement | Windows local |
| Jalon | J2 — Fixtures |
| Branche | `feat/j2-scheduled-events-fixtures` |
| Commit soumis à validation | `4ba0232e81f86c306df187887fcac49b46619b34` |
| Libellé du commit | `feat: show offline fixture corpus on dashboard` |
| Java | Java 25 |
| Spring Boot | 4.1.0 |
| PostgreSQL | 18.4 Alpine |
| Exposition applicative | `127.0.0.1:8087` |
| Exposition PostgreSQL | `127.0.0.1:5432` |
| Résultat global | `PASS` |

## 2. Objectif de la validation

Cette session avait pour objectifs de vérifier manuellement :

1. la préparation correcte de l’environnement Windows local ;
2. le comportement attendu lorsque PostgreSQL n’est pas démarré ;
3. le démarrage contrôlé de PostgreSQL par les scripts du dépôt ;
4. le démarrage de l’application Spring Boot ;
5. l’accessibilité du tableau de bord uniquement sur la boucle locale ;
6. l’affichage de l’inventaire J2 des fixtures hors ligne ;
7. le maintien de tous les verrous réseau J1 pendant J2 ;
8. l’état de santé de l’application avec PostgreSQL disponible ;
9. la dégradation contrôlée du tableau de bord lorsque PostgreSQL disparaît après le démarrage ;
10. l’arrêt propre de l’application et du socle PostgreSQL ;
11. la conservation du volume de données PostgreSQL.

## 3. Statuts de gouvernance maintenus

Les captures du tableau de bord confirment le maintien des statuts suivants :

```text
EXPERIMENTAL
LOCAL_ONLY
NOT_PRODUCTION_APPROVED
NO_CRITICAL_DEPENDENCY
```

Le connecteur reste dans le mode :

```text
LOCKED_OFFLINE_J1
```

La configuration réseau affichée reste :

```text
CONNECTOR=DISABLED
BASE_URL=NON_CONFIGURED
MAXIMUM_CONCURRENCY=1
MINIMUM_DELAY=3_SECONDS
```

Aucun déverrouillage réseau n’a été effectué pendant cette validation.

## 4. Validation automatisée associée à l’unité

La validation automatisée exécutée avant la validation manuelle avait produit :

```text
MAVEN_COMMAND=clean verify
MAVEN_OFFLINE_MODE=YES
MAIN_SOURCE_FILES_COMPILED=42
TEST_SOURCE_FILES_COMPILED=16
TESTS_EXECUTED=43
TEST_FAILURES=0
TEST_ERRORS=0
TESTS_SKIPPED=0
JAR_BUILD=SUCCESS
RESULT=PASS
```

Les tests ajoutés ou renforcés pour l’unité couvraient notamment :

- l’inventaire des neuf fixtures classpath ;
- la répartition exacte des résultats du parseur ;
- le passage du corpus à `INCOMPLETE` lorsque les ressources sont absentes ;
- la projection du corpus dans `DashboardView` ;
- la disponibilité de l’inventaire indépendamment des requêtes PostgreSQL du tableau de bord ;
- le rendu Thymeleaf réel des informations J2.

## 5. Matrice des tests manuels

| ID | Contrôle | Résultat attendu | Résultat observé | Statut |
|---|---|---|---|---|
| J2-WIN-01 | Préflight Windows | Java 25 et Docker détectés | `PREFLIGHT_RESULT=PASS`, `JAVA_TARGET=25`, `DOCKER_CHECKED=True` | PASS |
| J2-WIN-02 | État Compose initial | Aucun service si le socle n’a pas été démarré | `docker compose ps` ne retournait aucun conteneur | PASS |
| J2-WIN-03 | Port PostgreSQL avant démarrage | Port fermé | `TcpTestSucceeded=False` sur `127.0.0.1:5432` | PASS |
| J2-WIN-04 | Démarrage applicatif sans PostgreSQL | Échec contrôlé de Flyway | Connexion refusée, SQL State `08001`, arrêt du contexte Spring | PASS — comportement actuel attendu |
| J2-WIN-05 | Protection de `.env` | Refus d’écraser une configuration existante | `Initialize-LocalConfig.ps1` a refusé l’opération sans `-Force` | PASS |
| J2-WIN-06 | Démarrage PostgreSQL | Conteneur démarré et sain | `POSTGRES_STATUS=HEALTHY` | PASS |
| J2-WIN-07 | Liaison PostgreSQL | Boucle locale uniquement | `127.0.0.1:5432->5432/tcp` | PASS |
| J2-WIN-08 | Connectivité PostgreSQL | Connexion TCP possible | `TcpTestSucceeded=True` | PASS |
| J2-WIN-09 | Démarrage de l’application | Tableau de bord accessible | Interface visible sur le port local `8087` | PASS |
| J2-WIN-10 | Santé Actuator | Application en état `UP` | `/actuator/health` a retourné `status=UP` | PASS |
| J2-WIN-11 | État PostgreSQL du tableau de bord | `AVAILABLE`, Flyway V1 | `AVAILABLE`, migration `1` | PASS |
| J2-WIN-12 | Inventaire J2 | Neuf fixtures disponibles | `9 / 9 disponibles` | PASS |
| J2-WIN-13 | Répartition du corpus | 5 parsées, 3 incompatibles, 1 inattendue | Compteurs exacts affichés | PASS |
| J2-WIN-14 | Origine et preuve du corpus | Synthétique, schéma fournisseur non validé | `SYNTHETIC`, `NON VALIDÉ` | PASS |
| J2-WIN-15 | Catalogue logique | Aucun endpoint appelable ni URI réelle | Toutes les lignes indiquent `NON` et `ABSENTE` | PASS |
| J2-WIN-16 | Actions réseau | Actions désactivées | Boutons « Activer le connecteur » et « Lancer un appel » désactivés | PASS |
| J2-WIN-17 | Arrêt de PostgreSQL | Conteneur et réseau supprimés | `LOCAL_STACK_STOPPED=YES` | PASS |
| J2-WIN-18 | Conservation des données | Volume non supprimé | `POSTGRES_DATA_REMOVED=False` | PASS |
| J2-WIN-19 | Dégradation pendant l’exécution | Tableau de bord encore accessible, base indisponible | `POSTGRESQL=UNAVAILABLE`, `Flyway=UNKNOWN` | PASS |
| J2-WIN-20 | Arrêt final de l’application | Port applicatif fermé | `TcpTestSucceeded=False` sur `127.0.0.1:8087` | PASS |
| J2-WIN-21 | Arrêt final de PostgreSQL | Port PostgreSQL fermé | `TcpTestSucceeded=False` sur `127.0.0.1:5432` | PASS |

## 6. Préflight et état initial

La commande suivante a été exécutée :

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass `
  -File .\scripts\Preflight-Local.ps1
```

Résultat :

```text
PREFLIGHT_RESULT=PASS
JAVA_TARGET=25
DOCKER_CHECKED=True
```

Le préflight confirme que les prérequis sont disponibles. Il ne démarre pas PostgreSQL.

Le contrôle initial de Compose ne retournait aucun service :

```powershell
docker compose --env-file .env ps
```

Le port PostgreSQL était donc fermé :

```text
TARGET=127.0.0.1:5432
TcpTestSucceeded=False
```

## 7. Démarrage sans PostgreSQL

Une première tentative de démarrage de l’application a été effectuée alors que PostgreSQL n’était pas actif.

Résultat observé :

```text
Connection to 127.0.0.1:5432 refused
SQL State: 08001
```

La séquence d’échec était la suivante :

```text
Spring Boot
→ initialisation de Tomcat
→ initialisation de Hikari
→ connexion PostgreSQL refusée
→ Flyway indisponible
→ entityManagerFactory non créé
→ annulation du contexte Spring
→ arrêt de Tomcat
```

Ce comportement est conforme à l’architecture actuelle : Flyway et JPA restent obligatoires lors d’un démarrage à froid.

```text
COLD_START_WITHOUT_POSTGRESQL=NOT_SUPPORTED
OBSERVED_BEHAVIOR=EXPECTED
```

## 8. Protection de la configuration locale

La commande d’initialisation a été appelée alors que `.env` existait déjà :

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass `
  -File .\scripts\Initialize-LocalConfig.ps1
```

Le script a refusé l’écrasement :

```text
.env already exists.
Use -Force only when you intentionally rotate the local database password.
```

Ce résultat confirme que le script protège la configuration locale et empêche une rotation accidentelle du mot de passe PostgreSQL.

L’option `-Force` n’a pas été utilisée.

```text
LOCAL_PASSWORD_ROTATED=NO
ENV_FILE_OVERWRITTEN=NO
```

## 9. Démarrage contrôlé de PostgreSQL

La commande suivante a été exécutée :

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass `
  -File .\scripts\Start-Local.ps1
```

Résultat :

```text
PREFLIGHT_RESULT=PASS
JAVA_TARGET=25
DOCKER_CHECKED=True
POSTGRES_STATUS=HEALTHY
APPLICATION_STATUS=NOT_STARTED
```

Le contrôle Compose a ensuite confirmé :

```text
CONTAINER=betting-sofascore-local-lab-postgres
IMAGE=postgres:18.4-alpine
SERVICE=postgres
STATUS=Up (healthy)
BINDING=127.0.0.1:5432->5432/tcp
```

La connexion TCP a réussi :

```text
ComputerName=127.0.0.1
RemotePort=5432
TcpTestSucceeded=True
```

PostgreSQL est donc resté exclusivement exposé sur la boucle locale.

## 10. Santé de l’application

La commande suivante a été exécutée après démarrage de l’application :

```powershell
Invoke-RestMethod http://127.0.0.1:8087/actuator/health
```

Résultat :

```text
STATUS=UP
COMPONENTS=db,diskSpace,livenessState,ping,readinessState,ssl
GROUPS=liveness,readiness
```

Ce résultat valide :

- le démarrage complet du contexte Spring ;
- l’accessibilité de PostgreSQL ;
- l’initialisation de Flyway ;
- la disponibilité de l’application ;
- les états de vivacité et de disponibilité.

## 11. Validation du tableau de bord J2

### 11.1 Gouvernance

Le tableau de bord affiche :

```text
EXPERIMENTAL
LOCAL_ONLY
NOT_PRODUCTION_APPROVED
NO_CRITICAL_DEPENDENCY
LOCKED_OFFLINE_J1
```

### 11.2 Exposition locale

Le tableau de bord affiche :

```text
127.0.0.1:8087
```

Aucune exposition LAN ou publique n’a été observée.

### 11.3 État PostgreSQL nominal

Avant l’arrêt du conteneur, le tableau de bord affichait :

```text
POSTGRESQL=AVAILABLE
FLYWAY_MIGRATION=1
SNAPSHOTS=0
INCIDENTS=0
```

Aucun appel fournisseur n’avait été enregistré :

```text
LAST_RECORDED_CALL=NONE
```

### 11.4 Corpus hors ligne

Le panneau J2 affichait :

```text
CORPUS=SCHEDULED_EVENTS
AVAILABILITY=AVAILABLE_OFFLINE
FIXTURES_AVAILABLE=9
FIXTURES_DECLARED=9
PARSED=5
SCHEMA_INCOMPATIBLE=3
UNEXPECTED_CONTENT=1
LOADING_FAILURES=0
ORIGIN=SYNTHETIC
PARSER_VERSION=scheduled-events-v1
PROVIDER_SCHEMA_VALIDATED=NO
```

Les invariants sont respectés :

```text
9 + 0 = 9
5 + 3 + 1 = 9
```

Les trois incompatibilités de schéma et le contenu inattendu font partie du corpus synthétique. Ils ne correspondent pas à des incidents fournisseur observés.

### 11.5 Catalogue logique

Les sept familles suivantes étaient visibles :

```text
SCHEDULED_EVENTS
EVENT_DETAILS
EVENT_STATISTICS
EVENT_INCIDENTS
EVENT_LINEUPS
TOURNAMENT_STANDINGS
TEAM_RECENT_EVENTS
```

Pour chacune :

```text
TRIGGER=MANUAL
CALLABLE=NO
URI=ABSENT
```

### 11.6 Actions réseau

Les actions suivantes restaient désactivées :

```text
ENABLE_CONNECTOR=DISABLED
RUN_NETWORK_CALL=DISABLED
```

Le tableau de bord rappelait également :

```text
POLLING=NO
LIVE=NO
AUTOMATIC_REFRESH=NO
PROXY_ROTATION=NO
BROWSER_SIMULATION=NO
SESSION_REUSE=NO
VPS_EXPORT=NO
```

## 12. Dégradation contrôlée après l’arrêt de PostgreSQL

La commande suivante a été exécutée pendant que l’application Spring Boot fonctionnait encore :

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass `
  -File .\scripts\Stop-Local.ps1
```

Résultat :

```text
POSTGRES_CONTAINER_REMOVED=YES
COMPOSE_NETWORK_REMOVED=YES
LOCAL_STACK_STOPPED=YES
POSTGRES_DATA_REMOVED=False
```

Après cet arrêt, le tableau de bord est resté accessible et affichait :

```text
POSTGRESQL=UNAVAILABLE
FLYWAY_MIGRATION=UNKNOWN
SNAPSHOTS=0
INCIDENTS=0
CONNECTOR=DISABLED
LAST_RECORDED_CALL=NONE
```

Ce test démontre le comportement suivant :

```text
RUNNING_APPLICATION
+ POSTGRESQL_RUNTIME_OUTAGE
= DASHBOARD_STILL_ACCESSIBLE
```

Résultat :

```text
RUNTIME_DATABASE_OUTAGE_TOLERATED=YES
DASHBOARD_HTTP_FAILURE=NO
```

Les valeurs `Snapshots=0` et `Incidents=0` sont les valeurs de repli actuelles lorsque la base ne peut pas être interrogée. Elles doivent être interprétées comme « non consultables », et non comme une preuve que la base contient réellement zéro ligne pendant son indisponibilité.

## 13. Arrêt final

Après l’arrêt séparé de l’application Spring Boot, le port applicatif a été contrôlé :

```powershell
Test-NetConnection 127.0.0.1 -Port 8087
```

Résultat :

```text
TcpTestSucceeded=False
```

Le port PostgreSQL a également été contrôlé :

```powershell
Test-NetConnection 127.0.0.1 -Port 5432
```

Résultat :

```text
TcpTestSucceeded=False
```

L’état final est donc :

```text
APPLICATION_STOPPED=YES
POSTGRESQL_STOPPED=YES
PORT_8087_CLOSED=YES
PORT_5432_CLOSED=YES
POSTGRES_DATA_PRESERVED=YES
```

## 14. Distinction architecturale validée

La session établit clairement deux comportements.

### Perte de PostgreSQL pendant l’exécution

```text
Application déjà démarrée
→ PostgreSQL arrêté
→ tableau de bord toujours accessible
→ état PostgreSQL UNAVAILABLE
```

Résultat :

```text
RUNTIME_DATABASE_OUTAGE_TOLERATED=YES
```

### Démarrage à froid sans PostgreSQL

```text
PostgreSQL absent
→ Flyway ne peut pas se connecter
→ contexte Spring non démarré
```

Résultat :

```text
COLD_START_WITHOUT_DATABASE_SUPPORTED=NO
```

L’inventaire des fixtures est indépendant de PostgreSQL, mais le démarrage complet de l’application reste dépendant de Flyway et JPA.

## 15. Observations non bloquantes

### OBS-J2-001 — Compteurs PostgreSQL en état indisponible

Lorsque PostgreSQL est indisponible, le tableau de bord affiche actuellement :

```text
Snapshots=0
Incidents=0
```

Ces valeurs représentent un repli technique, pas une lecture réussie de la base.

Une amélioration ultérieure pourrait afficher :

```text
Snapshots=UNKNOWN
Incidents=UNKNOWN
```

ou :

```text
Snapshots=—
Incidents=—
```

Cette observation n’affecte pas la validation de l’unité J2.

### OBS-J2-002 — Démarrage à froid

Le tableau de bord ne peut pas être démarré sans PostgreSQL, même si son inventaire de fixtures est calculé exclusivement depuis le classpath.

Ce comportement est conforme à la configuration actuelle, mais devra être pris en compte si un futur mode de consultation totalement autonome sans Docker est souhaité.

## 16. Preuves collectées

Les preuves de la session comprennent :

1. sortie du préflight Windows ;
2. sortie initiale vide de `docker compose ps` ;
3. test TCP PostgreSQL en échec avant démarrage ;
4. journal de l’échec Flyway sans PostgreSQL ;
5. refus protecteur d’écraser `.env` ;
6. sortie réussie de `Start-Local.ps1` ;
7. état `healthy` du conteneur PostgreSQL ;
8. test TCP PostgreSQL réussi ;
9. réponse Actuator `UP` ;
10. captures du tableau de bord nominal ;
11. capture du corpus `9 / 9` ;
12. capture du catalogue logique verrouillé ;
13. capture des actions réseau désactivées ;
14. sortie réussie de `Stop-Local.ps1` ;
15. capture du tableau de bord en mode PostgreSQL `UNAVAILABLE` ;
16. contrôle final du port `8087` fermé ;
17. contrôle final du port `5432` fermé.

## 17. Bilan final

```text
J2_WINDOWS_MANUAL_VALIDATION=PASS
PREFLIGHT=PASS
POSTGRES_START=PASS
POSTGRES_HEALTH=PASS
APPLICATION_START=PASS
ACTUATOR_HEALTH=UP
DASHBOARD_RENDERING=PASS
FIXTURE_CORPUS_INVENTORY=PASS
FIXTURE_COUNTS=PASS
SCHEMA_BREAK_COUNTS=PASS
RUNTIME_DATABASE_DEGRADATION=PASS
POSTGRES_STOP=PASS
APPLICATION_STOP=PASS
POSTGRES_DATA_PRESERVED=YES
WORKSTATION_PORTS_CLOSED_AFTER_STOP=YES
REAL_SOFASCORE_CALLS_EXECUTED=NO
REAL_ENDPOINT_URI_CONFIGURED=NO
CONNECTOR_UNLOCKED=NO
VPS_ACCESS_EXECUTED=NO
PROVIDER_SCHEMA_VALIDATED=NO
```

## 18. Décision

La validation manuelle de l’unité J2 « affichage du corpus hors ligne dans le tableau de bord » est réussie.

Le fonctionnement observé est conforme aux objectifs de l’unité :

- les neuf fixtures classpath sont disponibles ;
- les résultats du parseur sont correctement agrégés ;
- les informations sont rendues par le tableau de bord ;
- les protections réseau restent actives ;
- PostgreSQL est limité à la boucle locale ;
- la perte de PostgreSQL pendant l’exécution est signalée sans rendre le tableau de bord inaccessible ;
- l’arrêt final ferme les ports locaux ;
- les données PostgreSQL ne sont pas supprimées.

```text
VALIDATION_DECISION=ACCEPTED
UNIT_STATUS=MANUALLY_VALIDATED
J2_NETWORK_AUTHORIZATION=NO
J3_AUTHORIZATION=NO
```

Cette validation ne constitue ni une approbation de production, ni une validation du schéma fournisseur, ni une autorisation de démarrer J3 sans Work Order distinct.

# WO-SS-20260808-001 — Bootstrap J0/J1 du SofaScore Local Lab

- **Statut :** `READY_FOR_LOCAL_VALIDATION`
- **Date :** 2026-08-08
- **Périmètre :** J0 Gouvernance + J1 Bootstrap
- **Dépôt :** `betting-sofascore-local-lab`
- **ADR :** `ADR-SS-001`

## 1. Objectif

Créer un dépôt Git séparé et un socle Maven/Spring Boot exécutable sous Windows avec Java 25 LTS, une interface locale, PostgreSQL/Flyway, Actuator et les garde-fous empêchant tout appel SofaScore réel avant le jalon J3.

## 2. Livrables réalisés

- dépôt Git autonome avec branche `main` ;
- Maven Wrapper et POM Spring Boot 4.1.0 ;
- application `SofascoreLocalApplication` ;
- propriétés `SofascoreProperties` validées ;
- liaison `127.0.0.1:8087` et garde de démarrage ;
- interface Thymeleaf de supervision ;
- PostgreSQL 18.4 dans Docker Compose ;
- migration Flyway V1 ;
- catalogue logique des sept familles de données ;
- verrou `ConnectorGate` ;
- profil Maven réel volontairement bloqué ;
- tests unitaires et test d’intégration Testcontainers ;
- scripts PowerShell ;
- README, AGENTS, SECURITY, architecture, runbook et références.

## 3. Invariants

1. aucun endpoint réel dans le code ou la configuration versionnée ;
2. aucun appel externe vers SofaScore ;
3. aucun polling ou planificateur ;
4. aucune écoute hors boucle locale ;
5. aucun secret versionné ;
6. PostgreSQL exposé uniquement sur `127.0.0.1` ;
7. `sofascore.enabled=false` par défaut ;
8. concurrence maximale égale à 1 ;
9. délai minimal au moins égal à 3 secondes ;
10. tests standards reproductibles sans SofaScore ni Docker ;
11. tests de migration isolés dans `integration-tests` ;
12. aucune dépendance du Betting Project principal.

## 4. Critères d’acceptation J0/J1

| ID | Critère | Preuve dans le dépôt | Statut avant validation Windows |
|---|---|---|---|
| J1-AC-01 | cible Java 25 | `pom.xml`, Enforcer | à exécuter |
| J1-AC-02 | wrappers versionnés | `mvnw`, `mvnw.cmd`, `.mvn/wrapper/` | présent |
| J1-AC-03 | liaison locale | `application.yml`, `LocalOnlyBindingGuard` | test statique + unitaire |
| J1-AC-04 | connecteur désactivé | `application.yml`, `ConnectorGate` | présent |
| J1-AC-05 | UI locale | `DashboardController`, template et CSS | à exécuter |
| J1-AC-06 | PostgreSQL Compose | `compose.yaml` | à exécuter |
| J1-AC-07 | Flyway V1 | `V1__bootstrap_schema.sql` | à exécuter |
| J1-AC-08 | Actuator | POM + configuration | à exécuter |
| J1-AC-09 | tests hors ligne | tests unitaires, aucun client actif | à exécuter |
| J1-AC-10 | migration Testcontainers | `FlywayMigrationIT` | à exécuter avec Docker |
| J1-AC-11 | profil réel bloqué | profil `sofascore-live-test` + `alwaysFail` | présent |
| J1-AC-12 | documentation de reprise | runbook et scripts | présent |

## 5. Procédure de validation Windows

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass `
  -File .\scripts\Initialize-LocalConfig.ps1

powershell.exe -NoProfile -ExecutionPolicy Bypass `
  -File .\scripts\Preflight-Local.ps1

powershell.exe -NoProfile -ExecutionPolicy Bypass `
  -File .\scripts\Start-Local.ps1

.\mvnw.cmd clean verify
.\mvnw.cmd -Pintegration-tests verify
.\mvnw.cmd -Dspring-boot.run.profiles=local spring-boot:run
```

Contrôles humains :

1. ouvrir `http://127.0.0.1:8087` ;
2. vérifier le mode `LOCKED_OFFLINE_J1` ;
3. vérifier PostgreSQL `AVAILABLE`, Flyway `1`, snapshots `0` ;
4. vérifier que les boutons réseau sont désactivés ;
5. vérifier les trois endpoints Actuator ;
6. arrêter l’application ;
7. tenter un démarrage avec `--server.address=0.0.0.0` et vérifier l’échec ;
8. exécuter `mvnw.cmd -Psofascore-live-test verify` et vérifier l’échec volontaire ;
9. vérifier que le Betting Project principal n’est ni appelé ni modifié.

## 6. Hors périmètre

- fixture réelle ;
- DTO ou parseur SofaScore ;
- URI réelle ;
- appel manuel ;
- stockage d’un payload reçu ;
- écran de recherche d’événement ;
- export JSON fonctionnel ;
- intégration VPS ;
- live.

## 7. Condition de clôture

Le Work Order passe à `VALIDATED` après exécution réussie de la procédure Windows, enregistrement des versions réellement détectées, captures du dashboard, résultats Maven et Testcontainers, puis commit du rapport de validation mis à jour.

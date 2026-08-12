# SofaScore Local Lab

Laboratoire Java local et contrôlé destiné à évaluer, depuis Windows, l’intérêt de données SofaScore comme enrichissement **facultatif** du Betting Project.

> **Statut :** `EXPERIMENTAL` · `LOCAL_ONLY` · `NOT_PRODUCTION_APPROVED` · `NO_CRITICAL_DEPENDENCY`

Le dépôt matérialise les jalons validés **J0 — Gouvernance**, **J1 — Bootstrap** et **J2 — Fixtures**. Le Work Order **J3 — Appel manuel** est ouvert pour le développement hors ligne des protections, sans autorisation actuelle d’URI réelle ni d’appel fournisseur, conformément au document de cadrage `Betting_Project_SofaScore_Local_Lab_Cadrage_v0.1.0.pdf` et à l’ADR `ADR-SS-001`.

## Ce qui est livré localement

- dépôt Git autonome, documentation, ADR, règles agent et Work Orders ;
- Java **25 LTS**, Spring Boot **4.1.0** et Maven Wrapper versionné ;
- interface Spring MVC + Thymeleaf sur `127.0.0.1:8087` ;
- PostgreSQL local dans Docker Desktop, migration Flyway initiale et stockage brut séparé ;
- Actuator, Caffeine, validation de configuration et garde de liaison locale ;
- catalogue logique des familles d’endpoints, sans URI réelle ;
- connecteur verrouillé dans le code au mode `LOCKED_OFFLINE_J3_POLICY` ;
- tests unitaires hors ligne et test Flyway/Testcontainers dans un profil explicite ;
- scripts PowerShell de configuration, préflight, démarrage, arrêt et vérification.
- corpus synthétique `SCHEDULED_EVENTS` de neuf fixtures classpath avec hashes vérifiés ;
- parseur hors ligne `scheduled-events-v1`, modèle local et tests de rupture de schéma ;
- inventaire du corpus visible dans le tableau de bord, sans dépendance à PostgreSQL.
- politique J3 hors ligne pour l’activation explicite, la confirmation par appel, le cache préalable, le délai minimal et l’arrêt global ;
- circuit J3 en mémoire initialisé à `LOCKED`, incidents typés et garde atomique limitant la concurrence à un appel ;

## Limite essentielle du bootstrap

**Aucun appel SofaScore réel n’est implémenté.** Le dépôt ne contient ni chemin d’endpoint SofaScore, ni adaptateur de transport actif, ni polling. Le profil Maven `sofascore-live-test` échoue volontairement jusqu’au jalon J3 et à un Work Order dédié.

Cette limite préserve la règle du Betting Project principal : aucun composant du VPS ne dépend du laboratoire, et l’arrêt du poste Windows ne doit avoir aucun effet sur la chaîne globale.

## Prérequis Windows

- Windows 11 ;
- Eclipse 2026-06 (4.40.0) avec Spring Tools 5.3.0 ;
- JDK 25 configuré comme JRE par défaut du workspace ;
- Docker Desktop avec `docker compose` ;
- Git.

Maven n’a pas besoin d’être installé globalement : `mvnw.cmd` télécharge la distribution Maven verrouillée lors de sa première exécution et vérifie son empreinte SHA-256.

## Démarrage rapide

Depuis PowerShell, à la racine du dépôt :

```powershell
# 1. Génère un mot de passe local aléatoire dans .env
powershell.exe -NoProfile -ExecutionPolicy Bypass `
  -File .\scripts\Initialize-LocalConfig.ps1

# 2. Vérifie Java 25, Docker, Compose et la configuration
powershell.exe -NoProfile -ExecutionPolicy Bypass `
  -File .\scripts\Preflight-Local.ps1

# 3. Démarre PostgreSQL et attend son healthcheck
powershell.exe -NoProfile -ExecutionPolicy Bypass `
  -File .\scripts\Start-Local.ps1

# 4. Compile et exécute les tests standard, sans accès SofaScore
.\mvnw.cmd clean verify

# 5. Démarre l’application locale
.\mvnw.cmd -Dspring-boot.run.profiles=local spring-boot:run
```

Ouvrir ensuite :

```text
http://127.0.0.1:8087
```

Points Actuator :

```text
http://127.0.0.1:8087/actuator/health
http://127.0.0.1:8087/actuator/info
http://127.0.0.1:8087/actuator/metrics
```

## Import dans Eclipse

1. Enregistrer le JDK 25 dans **Window → Preferences → Java → Installed JREs** et le définir comme JRE par défaut.
2. Choisir **File → Import → Maven → Existing Maven Projects**.
3. Sélectionner le dossier `betting-sofascore-local-lab`.
4. Vérifier dans **Project Properties → Java Compiler** que le niveau est `25`.
5. Créer une configuration **Spring Boot App** sur `SofascoreLocalApplication` avec le profil `local`.
6. Définir le répertoire de travail sur la racine du dépôt afin que `.env` et `exports/` soient résolus correctement.

Le démarrage depuis Eclipse nécessite que PostgreSQL ait déjà été lancé par `Start-Local.ps1` ou `docker compose up -d postgres`.

## Commandes de validation

### Tests standards hors ligne fournisseur

```powershell
.\mvnw.cmd clean verify
```

Les tests standards ne contiennent aucun appel Internet vers SofaScore.

### Migration réelle PostgreSQL avec Testcontainers

```powershell
.\mvnw.cmd -Pintegration-tests verify
```

Ce profil exige Docker et peut télécharger l’image PostgreSQL au premier lancement. Il ne contacte pas SofaScore.

### Vérification consolidée

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass `
  -File .\scripts\Verify-Local.ps1

powershell.exe -NoProfile -ExecutionPolicy Bypass `
  -File .\scripts\Verify-Local.ps1 -WithIntegrationTests
```

### Arrêt

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass `
  -File .\scripts\Stop-Local.ps1
```

La suppression volontaire des données PostgreSQL nécessite :

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass `
  -File .\scripts\Stop-Local.ps1 -RemoveData
```

## Arborescence

```text
betting-sofascore-local-lab/
├── ADR-SS-001-experimentation-endpoints-sofascore-depuis-windows.md
├── AGENTS.md
├── README.md
├── SECURITY.md
├── CHANGELOG.md
├── compose.yaml
├── pom.xml
├── mvnw / mvnw.cmd
├── docs/
│   ├── architecture/
│   ├── reference/
│   ├── runbooks/
│   ├── validation/
│   └── work_orders/
├── fixtures/
├── scripts/
├── exports/
└── src/
```

## Modèle de données J1

La migration `V1__bootstrap_schema.sql` crée :

- `provider_snapshot` : métadonnées de transport, payload JSONB, hash, parseur et statut de schéma ;
- `export_manifest` : manifeste des futurs exports normalisés ;
- `connector_control` : état opérateur persistant, initialisé à `network_enabled=false` et `circuit_state=LOCKED`.

Le mode de provenance futur pour les appels du laboratoire est `DIRECT_LOCAL_ENDPOINT`. Il ne doit jamais être confondu avec une `VisualObservation` du projet global.

## Politique réseau J1

Le bootstrap cumule plusieurs barrières :

1. `sofascore.enabled=false` par défaut ;
2. aucune base URL par défaut ;
3. aucune URI d’endpoint dans le catalogue ;
4. toutes les définitions sont `callable=false` ;
5. `ConnectorGate` refuse systématiquement les appels ;
6. le profil `sofascore-live-test` échoue volontairement ;
7. l’application n’écoute que sur une adresse de boucle locale ;
8. les paramètres imposent concurrence `1`, délai minimal `3s`, rafraîchissement et live désactivés.

Le jalon J3 devra retirer **explicitement** certaines de ces barrières, une par une, après validation du J2 et création d’un Work Order borné.

## Documentation de référence

- [ADR-SS-001](ADR-SS-001-experimentation-endpoints-sofascore-depuis-windows.md)
- [Architecture J0/J1](docs/architecture/ARCHITECTURE.md)
- [Contrat hors ligne scheduled-events-v1](docs/architecture/SCHEDULED-EVENTS-V1.md)
- [Politique réseau J3 hors ligne](docs/architecture/J3-OFFLINE-NETWORK-POLICY.md)
- [Runbook local](docs/runbooks/RUNBOOK-LOCAL.md)
- [Cadrage PDF](docs/reference/Betting_Project_SofaScore_Local_Lab_Cadrage_v0.1.0.pdf)
- [Rapport de validation du bootstrap](docs/validation/J0-J1-VALIDATION-REPORT.md)
- [Work Order J0/J1](docs/work_orders/completed/WO-SS-20260808-001-bootstrap-j0-j1.md)
- [Rapport de validation J2](docs/validation/J2-WINDOWS-VALIDATION-20260812.md)
- [Work Order J2 validé](docs/work_orders/completed/WO-SS-20260808-002-fixtures-j2.md)
- [Work Order J3 actif](docs/work_orders/active/WO-SS-20260812-003-manual-call-j3.md)

## Prochaine frontière

La prochaine frontière est le développement hors ligne des politiques réseau, du circuit d’arrêt et de la conservation du brut prévus par **J3 — Appel manuel**. Le Work Order J3 impose un point de décision humain distinct avant toute URI réelle ou requête fournisseur. Dans l’état actuel, le connecteur, le profil réel et toutes les actions réseau restent bloqués.

# Rapport de validation J0/J1

- **Version du dépôt :** `0.1.0-SNAPSHOT`
- **Date de préparation :** 2026-08-08
- **Statut :** `STATIC_VALIDATED / WINDOWS_RUNTIME_VALIDATION_PENDING`
- **Portée validée :** J0 Gouvernance et J1 Bootstrap

## 1. Résultat de la préparation

Le dépôt respecte statiquement le cadrage J0/J1 : Java 25 est imposé par Maven, l’application et PostgreSQL sont liés à la boucle locale, aucune URI SofaScore réelle n’est versionnée, le transport reste absent et le `ConnectorGate` bloque systématiquement tout appel.

| Contrôle | Résultat | Preuve principale |
|---|---|---|
| arborescence J0/J1 | PASS | fichiers, documentation, sources, tests, scripts et fixtures |
| intégrité ADR | PASS | SHA-256 `13252a5cd5fdbb394a253af82fbaf3a634a86688bbb1a8d37a3e1dc82cee06cb` |
| intégrité cadrage PDF | PASS | SHA-256 `746106cfe5142ad7b7426443b9730b22f281d6d844d108a5c371c7e08c9f1fee` |
| PDF lisible | PASS | 28 pages, A4, non chiffré, sans JavaScript |
| `pom.xml` bien formé | PASS | parse XML |
| parent Spring Boot 4.1.0 | PASS | `pom.xml` |
| cible Java 25 | PASS | propriété Maven et Maven Enforcer `[25,26)` |
| dépendances attendues | PASS | starters MVC, RestClient, Thymeleaf, Validation, JPA, Flyway, Cache, Actuator et tests |
| YAML | PASS | `compose.yaml`, `application.yml`, `application-local.yml` |
| HTML du dashboard | PASS | parse HTML et deux boutons réseau désactivés |
| cohérence packages/chemins Java | PASS | toutes les sources principales et de test |
| syntaxe du lanceur Unix | PASS | `bash -n mvnw` |
| distribution Maven verrouillée | PASS | Maven 3.9.16 et SHA-256 publié |
| liaison application locale | PASS | `server.address=127.0.0.1`, port 8087 |
| liaison PostgreSQL locale | PASS | publication `127.0.0.1:${POSTGRES_PORT}:5432` |
| garde de démarrage anticipée | PASS | `LocalOnlyBindingGuard` exécuté comme `BeanFactoryPostProcessor` prioritaire |
| connecteur désactivé | PASS | propriétés sûres et mode `LOCKED_OFFLINE_J1` |
| catalogue non appelable | PASS | sept familles, `callable=false`, aucune URI configurée |
| recherche de transport actif | PASS | aucun `RestClient`, `WebClient`, `HttpClient`, Selenium, Playwright ou proxy actif |
| recherche d’hôte SofaScore | PASS | aucune URL ni hostname API dans les sources exécutables/configurations |
| migration V1 | PASS statique | trois tables, contraintes, index et état initial verrouillé |
| compilation d’un sous-ensemble Java pur | PASS | 8 classes compilées avec le JDK 21 disponible ; contrôle de syntaxe seulement |
| contrôles statiques consolidés | PASS | 360 contrôles, 0 échec |
| `git diff --check` | PASS | index et arbre de travail sans erreur d’espace ou de marqueur de conflit |
| `git fsck --full` | PASS | objets et références du dépôt cohérents après le commit initial |

## 2. Limite de l’environnement de préparation

L’environnement utilisé pour produire le livrable fournit :

```text
OpenJDK 21.0.11
Git 2.47.3
Python 3 avec parseurs XML/YAML/HTML
```

Il ne fournit pas JDK 25, Maven, Docker Desktop ni Windows PowerShell. Les opérations suivantes ne sont donc pas déclarées comme exécutées dans cet environnement :

- résolution réelle du graphe Maven avec Spring Boot 4.1.0 ;
- compilation complète Java 25 ;
- tests JUnit/MockMvc ;
- démarrage PostgreSQL 18.4 ;
- migration Flyway sur une base réelle ;
- test Testcontainers ;
- démarrage de l’interface et inspection visuelle ;
- parsing natif des scripts avec Windows PowerShell 5.1 ;
- contrôle d’échec réel de la liaison `0.0.0.0` ;
- contrôle d’échec volontaire du profil `sofascore-live-test`.

Cette limite ne modifie pas le statut du dépôt : il est prêt pour validation sur le poste Windows cible, mais le Work Order J0/J1 ne passe à `VALIDATED` qu’après cette validation.

## 3. Validation à exécuter sous Windows

Depuis la racine du dépôt :

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass `
  -File .\scripts\Initialize-LocalConfig.ps1

powershell.exe -NoProfile -ExecutionPolicy Bypass `
  -File .\scripts\Preflight-Local.ps1

.\mvnw.cmd clean verify
.\mvnw.cmd -Pintegration-tests verify

powershell.exe -NoProfile -ExecutionPolicy Bypass `
  -File .\scripts\Start-Local.ps1

.\mvnw.cmd -Dspring-boot.run.profiles=local spring-boot:run
```

Contrôles attendus :

1. `java -version` annonce Java 25 ;
2. Maven 3.9.16 est téléchargé et son SHA-256 est accepté ;
3. la suite standard réussit sans accès SofaScore ;
4. la suite d’intégration démarre PostgreSQL avec Testcontainers et valide Flyway V1 ;
5. `http://127.0.0.1:8087` affiche `LOCKED_OFFLINE_J1` ;
6. PostgreSQL est `AVAILABLE`, la migration est `1` et le compteur de snapshots vaut `0` sur une base neuve ;
7. toutes les familles indiquent `Appelable = NON` et `URI = ABSENTE` ;
8. un démarrage avec `--server.address=0.0.0.0` échoue avant l’instanciation des beans ordinaires ;
9. `mvnw.cmd -Psofascore-live-test verify` échoue volontairement ;
10. aucun composant du Betting Project principal n’est appelé ou modifié.

## 4. Bloc de clôture Windows

Après validation locale, renseigner ce bloc et changer le statut du Work Order en `VALIDATED` :

```text
JAVA_VERSION=
MAVEN_VERSION=
DOCKER_VERSION=
DOCKER_COMPOSE_VERSION=
POSTGRES_IMAGE_DIGEST=
MAVEN_CLEAN_VERIFY=
MAVEN_INTEGRATION_TESTS=
APPLICATION_START=
DASHBOARD_CHECK=
LOCAL_BINDING_REJECTION_CHECK=
LIVE_PROFILE_BLOCK_CHECK=
VALIDATED_AT=
VALIDATED_BY=
```

Les captures jointes ne doivent contenir aucun secret, mot de passe, cookie, token ou payload brut.

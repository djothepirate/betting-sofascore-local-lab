# J0/J1 — Rapport de validation Windows

- **Projet :** `betting-sofascore-local-lab`
- **Branche validée :** `fix/powershell51-preflight-java-version`
- **Commit validé :** `07f63fc3e41e6457f81bd61b0a3e73b176382c64`
- **Commit `origin/main` de référence :** `8f415bd7f20b83992eaea529c78a31881c0680ad`
- **Tag de référence :** `j0-j1-v0.1.0`
- **Date locale de validation :** 2026-08-09
- **Validateur :** Geoffrey
- **Run ID :** `20260809-013413-a68445ff`
- **Lanceur :** `Invoke-J0J1-Full-Validation-PS51-v1.0.2.ps1`
- **SHA-256 du lanceur :** `2908F8F5F023CDCC6CF9DD830A9F2AF5C8703DE1CD60E437F14ADEE61315E125`
- **Statut :** `WINDOWS_RUNTIME_VALIDATED / ECLIPSE_GUI_CHECK_PENDING`
- **Appels SofaScore exécutés :** `NO`

## 1. Verdict

La validation automatisée J0/J1 sous Windows PowerShell 5.1 est réussie.

```text
J0_J1_VALIDATION_RESULT=PASS
PULL_REQUEST_READINESS=READY_TO_OPEN
LAUNCHER_RESULT=PASS
WORKER_EXIT_CODE=0
WORKER_TIMED_OUT=False
OUTER_CLEANUP_STATUS=PASS
SCRIPT_EXIT_CODE=0
SCRIPT_FINISHED=YES
```

La branche peut faire l’objet d’une Pull Request. La fusion reste conditionnée au contrôle manuel du démarrage depuis Eclipse prévu par `SS-AC-02`.

## 2. Environnement validé

| Composant | Valeur constatée |
|---|---|
| Système | Windows 11, amd64 |
| PowerShell | Windows PowerShell 5.1, exécution non interactive |
| Java | Oracle Java `25.0.4` LTS |
| Maven Wrapper | Apache Maven `3.9.16` |
| Spring Boot | `4.1.0` |
| Docker Engine client / serveur | `29.6.2 / 29.6.2` |
| Docker Compose | `5.3.1` |
| PostgreSQL | `18.4` |
| Image PostgreSQL | `postgres:18.4-alpine` |
| Digest PostgreSQL | `postgres@sha256:9a8afca54e7861fd90fab5fdf4c42477a6b1cb7d293595148e674e0a3181de15` |
| Testcontainers | `2.0.5` |
| Ryuk | `testcontainers/ryuk:0.14.0` |

## 3. Résultats par domaine

| Domaine | Résultat | Preuve synthétique |
|---|---|---|
| Préflight Git | PASS | branche, HEAD, upstream, `origin/main`, tag et arbre propre conformes |
| Contrôles statiques J0/J1 | PASS | 23 fichiers requis, 67 fichiers suivis, Java 25, Boot 4.1.0 |
| Verrouillage fournisseur | PASS | `LOCKED_OFFLINE_J1`, aucune URI SofaScore, aucun transport HTTP actif |
| Préflight environnement | PASS | Java 25, Maven, Docker et Compose disponibles |
| Propriété du runtime | PASS | ports 8087 et 18087 libres, aucune stack PostgreSQL du projet active au départ |
| Tests Maven standards | PASS | compilation Java 25 et suite JUnit/MockMvc réussies |
| Tests d’intégration | PASS | Testcontainers PostgreSQL et `FlywayMigrationIT` réussis |
| Appels SofaScore pendant les tests | PASS | `SOFASCORE_NETWORK_CALLS_EXECUTED=NO` |
| PostgreSQL Compose | PASS | démarrage sain et publication locale uniquement |
| État initial de la base | PASS | schéma applicatif absent avant le démarrage de l’application |
| Démarrage Spring Boot réel | PASS | application disponible en 5,788 s |
| Tableau de bord | PASS | HTTP 200 |
| Actuator | PASS | `health=UP`, phase `J0-J1` |
| En-têtes de sécurité | PASS | contrôle du lanceur réussi |
| Écoute réseau | PASS | une seule écoute sur `127.0.0.1:8087` |
| Flyway sur base Compose | PASS | transition `EMPTY_TO_MIGRATED`, version `1` |
| Tables attendues | PASS | `provider_snapshot`, `export_manifest`, `connector_control` |
| État du connecteur en base | PASS | `network_enabled=false`, `circuit_state=LOCKED` |
| Non-régression des snapshots | PASS | compteur resté à `0` |
| Rejet de `0.0.0.0` | PASS | démarrage refusé avec code de sortie 1 |
| Blocage du profil live | PASS | profil `sofascore-live-test` volontairement refusé |
| Nettoyage final | PASS | aucun conteneur actif, aucun listener 8087/18087, arbre Git propre |
| Restitution de l’invite | PASS | watchdog borné, `SCRIPT_FINISHED=YES` |

## 4. Preuves de migration et de verrouillage

Avant démarrage de Spring Boot :

```text
DATABASE_SCHEMA_STATE=EMPTY
FLYWAY_VERSION=NONE
SNAPSHOT_COUNT=0
```

Après démarrage de Spring Boot :

```text
DATABASE_SCHEMA_STATE=MIGRATED
FLYWAY_VERSION=1
CONNECTOR_CONTROL_STATE=false|LOCKED
SNAPSHOT_COUNT=0
DATABASE_SCHEMA_TRANSITION=EMPTY_TO_MIGRATED
SNAPSHOT_COUNT_UNCHANGED=YES
```

## 5. Contrôles négatifs

### 5.1 Liaison réseau non locale

Le démarrage avec `server.address=0.0.0.0` a échoué comme prévu :

```text
The SofaScore Local Lab must bind to a loopback address, not 0.0.0.0
NEGATIVE_BINDING_EXIT_CODE=1
WILDCARD_BINDING_REJECTION=PASS
```

### 5.2 Profil d’appel réel

Le profil Maven d’appel réel reste bloqué au jalon J1 :

```text
LIVE_PROFILE_EXIT_CODE=1
LIVE_PROFILE_GUARD_RESULT=PASS
```

Aucun appel réseau SofaScore n’a été autorisé ni exécuté.

## 6. État Docker après validation

Les images suivantes peuvent rester présentes dans le cache local :

- `postgres:18.4-alpine` ;
- `testcontainers/ryuk:0.14.0`.

Le volume persistant suivant est conservé volontairement :

```text
betting-sofascore-local-lab-postgres-data
```

Les conteneurs et le réseau Compose ont été arrêtés et supprimés. Le volume n’a pas été supprimé. Lors d’une prochaine exécution, la transition normale attendue est `MIGRATED_TO_MIGRATED`.

## 7. Avertissements non bloquants

Les avertissements suivants n’ont pas provoqué d’échec et ne bloquent pas J0/J1 :

1. Mockito s’auto-attache comme agent Java ; cette méthode devra être remplacée avant qu’un futur JDK ne l’interdise par défaut.
2. Les caches Caffeine ne sont pas construits avec `recordStats()` ; seules certaines métriques de cache sont donc exposées.
3. Certains messages accentués du profil Maven apparaissent mal encodés dans la console Windows ; le garde-fou lui-même fonctionne correctement.

Ces sujets doivent être enregistrés comme dette technique, sans modifier le verdict de validation J0/J1.

## 8. Contrôle Eclipse restant

Le lanceur ne peut pas automatiser l’interface graphique Eclipse :

```text
ECLIPSE_GUI_START_CHECK=NOT_AUTOMATED_BY_THIS_LAUNCHER
```

Avant fusion, vérifier manuellement :

- [ ] JDK 25 sélectionné dans Eclipse ;
- [ ] projet importé comme projet Maven ;
- [ ] profil Spring `local` actif ;
- [ ] PostgreSQL démarré avec la configuration locale ;
- [ ] `SofascoreLocalApplication` démarre depuis Eclipse ;
- [ ] tableau de bord accessible sur `http://127.0.0.1:8087` ;
- [ ] Actuator indique `UP` ;
- [ ] statut affiché `LOCKED_OFFLINE_J1` ;
- [ ] arrêt propre de l’application ;
- [ ] aucune écoute restante sur 8087.

À compléter après le contrôle :

```text
ECLIPSE_GUI_START_CHECK=
ECLIPSE_DASHBOARD_CHECK=
ECLIPSE_ACTUATOR_CHECK=
ECLIPSE_STOP_CHECK=
ECLIPSE_VALIDATED_AT=
ECLIPSE_VALIDATED_BY=
```

## 9. Décision de promotion

```text
AUTOMATED_J0_J1_GATE=PASS
PULL_REQUEST_OPENING=AUTHORIZED
MERGE_TO_MAIN=PENDING_ECLIPSE_GUI_CHECK_AND_REVIEW
SOFASCORE_NETWORK_CALLS_EXECUTED=NO
```

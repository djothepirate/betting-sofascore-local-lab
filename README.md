# SofaScore Local Lab

Laboratoire Java local et contrôlé destiné à évaluer, depuis Windows, l’intérêt de données SofaScore comme enrichissement **facultatif** du Betting Project.

> **Statut :** `EXPERIMENTAL` · `LOCAL_ONLY` · `NOT_PRODUCTION_APPROVED` · `NO_CRITICAL_DEPENDENCY`

Le dépôt matérialise les jalons validés **J0 — Gouvernance**, **J1 — Bootstrap**, **J2 — Fixtures**, **J3 — Appel manuel** et **J4 — Événements**. J4 ajoute une recherche locale par date, une identité canonique stable, un historique append-only et un premier détail de rencontre strictement hors ligne. Le chemin fournisseur J3 reste désactivé par défaut et aucun appel fournisseur n’est exécuté par les tests, conformément au document de cadrage `Betting_Project_SofaScore_Local_Lab_Cadrage_v0.1.0.pdf` et à l’ADR `ADR-SS-001`.

## Ce qui est livré localement

- dépôt Git autonome, documentation, ADR, règles agent et Work Orders ;
- Java **25 LTS**, Spring Boot **4.1.0** et Maven Wrapper versionné ;
- interface Spring MVC + Thymeleaf sur `127.0.0.1:8087` ;
- PostgreSQL local dans Docker Desktop, migrations Flyway V1 à V5 et stockage brut séparé ;
- Actuator, Caffeine, validation de configuration et garde de liaison locale ;
- catalogue logique des familles d’endpoints, sans URI réelle ;
- connecteur verrouillé dans le code au mode `LOCKED_OFFLINE_J3_POLICY` ;
- tests unitaires hors ligne et test Flyway/Testcontainers dans un profil explicite ;
- scripts PowerShell de configuration, préflight, démarrage, arrêt et vérification.
- corpus synthétique `SCHEDULED_EVENTS` de douze fixtures classpath avec hashes vérifiés ;
- parseur hors ligne `scheduled-events-v1`, compatible avec le corpus J2 `events` et avec la forme
  fournisseur qualifiée `scheduled`, modèle local et tests de rupture de schéma ;
- inventaire du corpus visible dans le tableau de bord, sans dépendance à PostgreSQL.
- politique J3 hors ligne pour l’activation explicite, la confirmation par appel, le cache préalable, le délai minimal et l’arrêt global ;
- circuit J3 en mémoire initialisé à `LOCKED`, incidents typés et garde atomique limitant la concurrence à un appel ;
- persistance J3 des octets bruts avec taille, SHA-256, métadonnées bornées et déduplication par requête ;
- transport J3 simulé limité à `127.0.0.1`, derrière la politique manuelle et la garde de concurrence ;
- contrôle J3 visible avec arrêt global, activation distincte et confirmation exacte d’une intention datée ;
- jeton de formulaire local lié à la session et à usage unique, sans rendre l’action fournisseur disponible ;
- matrice J3 d’incidents simulés avec conservation du brut avant parsing, circuit ouvert et aucun retry automatique ;
- qualification Windows du parcours opérateur local et des politiques simulées, avec preuve explicite
  que l’action fournisseur reste indisponible ;
- chemin J3 dédié à une collecte manuelle explicite démarrant toujours en page `1`, poursuivie
  uniquement tant que le parseur retourne `hasNextPage=true` et bornée localement à 25 pages ;
- client fournisseur sans proxy, redirection, cookie, jeton, compte ou donnée de session, avec arrêt
  au premier incident et aucun retry ;
- parcours répétable uniquement après réarmement, activation, nouvelle intention datée,
  confirmation exacte et action finale distincte, sans polling ni retry ;
- cache réel du parcours dynamique consulté avant chaque transport sur la clé exacte date/page :
  seuls les snapshots `PARSED` frais selon le TTL de dix minutes et le parseur courant sont relus
  hors ligne ; un cache hit ne déclenche ni transport, ni attente, ni mutation de persistance ;
- catalogue local limité à 50 métadonnées de snapshots bruts et inspection JSON explicite d’une
  ligne, avec contrôle taille/SHA-256, blocage des contenus sensibles, parsing strict, rendu HTML
  échappé et réponse `no-store`, sans transport, téléchargement ou mutation ;
- identité canonique J4 déterministe par paire `(provider, providerEventId)`, indépendante des
  noms, horaires et statuts mutables ;
- observations normalisées J4 append-only, dédupliquées par empreinte et toujours reliées à leur
  snapshot ou fixture, leur SHA-256, leur parseur et leur heure de réception ;
- contrat synthétique `event-details-v1`, rattachement strict à l’identité locale et stockage du
  détail sans URI, transport ou donnée fournisseur réelle ;
- recherche locale `/events` par date civile et zone IANA, page de détail et chronologie des
  observations, avec import de démonstration synthétique idempotent ;

## Limite essentielle du bootstrap

**Aucun appel SofaScore réel n’est actif par défaut et aucun n’est exécuté par les tests.** Le connecteur général, `ConnectorGate` et le profil Maven `sofascore-live-test` restent bloqués. Le seul chemin fournisseur est une exception J3 dédiée, inactive tant que `SOFASCORE_ENABLED`, `SOFASCORE_J3_QUALIFICATION_ENABLED`, l’origine exacte et l’unique famille autorisée ne concordent pas. Il ne prend en charge ni polling, ni planification, ni autre sport, ni autre endpoint ; chaque collecte reste manuelle, séquentielle et plafonnée.

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

## Modèle de données J1 à J4

La migration `V1__bootstrap_schema.sql` crée :

- `provider_snapshot` : métadonnées de transport, emplacement normalisé JSONB, hash, parseur et statut de schéma ;
- `export_manifest` : manifeste des futurs exports normalisés ;
- `connector_control` : état opérateur persistant, initialisé à `network_enabled=false` et `circuit_state=LOCKED`.

La migration append-only `V2__raw_manual_call_snapshots.sql` ajoute à `provider_snapshot` les octets exacts dans `payload_raw` (`bytea`), leur taille et le mode de provenance obligatoire `DIRECT_LOCAL_ENDPOINT`. Le brut reste distinct de `payload_jsonb`, qui n’est pas alimenté par cette unité. La taille est limitée à 5 Mio et une même combinaison fournisseur, endpoint logique, clé de requête et SHA-256 est dédupliquée.

La migration append-only `V3__dynamic_manual_collection_cache.sql` ajoute uniquement un checkpoint
de fraîcheur par clé date/page. Il référence le snapshot brut immuable et permet de rafraîchir le
TTL après une nouvelle réponse identique dédupliquée, sans recopier ni modifier le payload.

La migration append-only `V4__canonical_events_and_observations.sql` introduit
`canonical_event` et `canonical_event_observation`. L’identité UUID reste stable pour la paire
fournisseur/identifiant ; les observations successives conservent les changements métier et leur
provenance. Un trigger PostgreSQL bloque toute mise à jour ou suppression d’une observation.

La migration append-only `V5__offline_event_details.sql` ajoute `event_detail_observation`. Cette
table conserve uniquement les détails issus des fixtures synthétiques J4, avec hash, parseur et
heure source obligatoires. Elle est elle aussi protégée contre `UPDATE` et `DELETE`.

Le mode `DIRECT_LOCAL_ENDPOINT` ne doit jamais être confondu avec une `VisualObservation` du projet global. Cette persistance est prête pour un transport futur, mais n’effectue elle-même aucun appel.

## Politique réseau J1

Le bootstrap cumule plusieurs barrières :

1. `sofascore.enabled=false` par défaut ;
2. aucune base URL par défaut ;
3. aucune origine fournisseur dans la configuration par défaut ;
4. toutes les définitions restent `callable=false`, sauf `SCHEDULED_EVENTS` dans le seul mode de
   qualification J3 explicitement armé ;
5. `ConnectorGate` refuse systématiquement les appels ;
6. le profil `sofascore-live-test` échoue volontairement ;
7. l’application n’écoute que sur une adresse de boucle locale ;
8. les paramètres imposent concurrence `1`, délai minimal `3s`, rafraîchissement et live désactivés.

Après la clôture de J3, aucune de ces barrières ne peut être retirée sans un nouveau Work Order,
une décision de gouvernance explicite et une qualification humaine dédiée.

## Documentation de référence

- [ADR-SS-001](ADR-SS-001-experimentation-endpoints-sofascore-depuis-windows.md)
- [Architecture J0/J1](docs/architecture/ARCHITECTURE.md)
- [Contrat hors ligne scheduled-events-v1](docs/architecture/SCHEDULED-EVENTS-V1.md)
- [Contrat hors ligne event-details-v1](docs/architecture/EVENT-DETAILS-V1.md)
- [Architecture des événements canoniques J4](docs/architecture/J4-CANONICAL-EVENTS-AND-LOCAL-DETAIL.md)
- [Politique réseau J3 hors ligne](docs/architecture/J3-OFFLINE-NETWORK-POLICY.md)
- [Persistance des snapshots bruts J3](docs/architecture/J3-RAW-SNAPSHOT-PERSISTENCE.md)
- [Transport scheduled-events J3 protégé et simulé](docs/architecture/J3-GUARDED-SCHEDULED-EVENTS-TRANSPORT.md)
- [Confirmation explicite d’appel manuel J3](docs/architecture/J3-EXPLICIT-MANUAL-CALL-CONFIRMATION.md)
- [Politiques d’arrêt et d’incident J3](docs/architecture/J3-TRANSPORT-STOP-AND-INCIDENT-POLICIES.md)
- [Chemin fournisseur J3 borné à cinq pages](docs/architecture/J3-FIVE-PAGE-PROVIDER-QUALIFICATION.md)
- [Reprise fournisseur J3 contrôlée à la page 2](docs/architecture/J3-PAGE-TWO-PROVIDER-RESUME.md)
- [Adaptation hors ligne au schéma qualifié de la page 2](docs/architecture/J3-PAGE-TWO-SCHEMA-ADAPTATION.md)
- [Reprise fournisseur J3 contrôlée à la page 3](docs/architecture/J3-PAGE-THREE-PROVIDER-RESUME.md)
- [Collecte manuelle J3 répétable à pagination dynamique](docs/architecture/J3-DYNAMIC-MANUAL-PAGINATION.md)
- [Inspection JSON locale des snapshots bruts J3](docs/architecture/J3-LOCAL-RAW-SNAPSHOT-JSON-INSPECTION.md)
- [Runbook local](docs/runbooks/RUNBOOK-LOCAL.md)
- [Cadrage PDF](docs/reference/Betting_Project_SofaScore_Local_Lab_Cadrage_v0.1.0.pdf)
- [Rapport de validation du bootstrap](docs/validation/J0-J1-VALIDATION-REPORT.md)
- [Work Order J0/J1](docs/work_orders/completed/WO-SS-20260808-001-bootstrap-j0-j1.md)
- [Rapport de validation J2](docs/validation/J2-WINDOWS-VALIDATION-20260812.md)
- [Work Order J2 validé](docs/work_orders/completed/WO-SS-20260808-002-fixtures-j2.md)
- [Qualification Windows J3 — contrôle local et politiques simulées](docs/validation/J3-WINDOWS-MANUAL-CALL-QUALIFICATION-20260812.md)
- [Qualification Windows J3 — pagination dynamique](docs/validation/J3-WINDOWS-DYNAMIC-PAGINATION-QUALIFICATION-20260814.md)
- [Work Order J3 validé](docs/work_orders/completed/WO-SS-20260812-003-manual-call-j3.md)
- [Qualification technique Windows J4](docs/validation/J4-WINDOWS-TECHNICAL-QUALIFICATION-20260815.md)
- [Work Order J4 validé](docs/work_orders/completed/WO-SS-20260815-004-events-j4.md)

## J3 et J4 clôturés, prochaine frontière

La qualification humaine du `2026-08-14` a collecté dix pages sur dix, après les cinq pages
observées le `2026-08-13`. Elle confirme que le parcours repart de la page 1, persiste avant
parsing, continue uniquement sur `hasNextPage=true` et s’arrête normalement sur `false`, sans
conserver l’ancienne hypothèse fixe de cinq pages. Une barrière locale interdit toute page 26.

La pagination dynamique est désormais qualifiée dans le périmètre manuel J3. Toute automatisation,
planification, collecte live ou généralisation à une autre famille reste hors périmètre.

La première unité post-qualification applique désormais la politique de cache au chemin dynamique.
La preuve minimisée v4 distingue explicitement les pages demandées au fournisseur des pages
résolues depuis un snapshot local frais, sans introduire de contournement manuel du TTL.

L’inspection JSON locale permet maintenant de relire explicitement un snapshot brut déjà persisté,
après vérification de son intégrité et de son innocuité. Elle reste une aide opérateur en lecture
seule : le brut n’est ni téléchargé, ni réécrit, ni ajouté aux rapports de qualification.

La qualification complémentaire du cache et de cette inspection confirme que le formatage local ne
rafraîchit ni n’invalide un checkpoint de cache, ne modifie aucune classification historique et ne
rend pas un snapshot incompatible éligible. La matrice technique et les observations humaines sont
consignées dans
[`docs/validation/J3-WINDOWS-CACHE-AND-LOCAL-SNAPSHOT-INSPECTION-QUALIFICATION-20260814.md`](docs/validation/J3-WINDOWS-CACHE-AND-LOCAL-SNAPSHOT-INSPECTION-QUALIFICATION-20260814.md).

Le jalon J3 est validé et son Work Order est archivé dans `docs/work_orders/completed`. Cette
clôture ne change pas les statuts `EXPERIMENTAL`, `LOCAL_ONLY`, `NOT_PRODUCTION_APPROVED` et
`NO_CRITICAL_DEPENDENCY`. Le polling, la planification, le déploiement VPS, une nouvelle famille
d’endpoint ou l’intégration au Betting Project principal restent interdits tant qu’un nouveau Work
Order et une décision de gouvernance dédiée ne les autorisent pas.

Le jalon J4 construit désormais une vue métier locale sur les seules sources déjà présentes ou
synthétiques. Une normalisation manuelle d’un snapshot `SCHEDULED_EVENTS` compatible vérifie son
intégrité et le reparse sans modifier sa classification historique ; le corpus de démonstration
reste explicitement `SYNTHETIC_FIXTURE`. La qualification technique et le test fonctionnel humain
sont acquis sans modification de `.env`. Le Work Order est archivé dans
`docs/work_orders/completed` au statut `VALIDATED`. Cette clôture ne déverrouille aucune nouvelle
famille, automatisation ou dépendance de production.

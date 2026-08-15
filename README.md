# SofaScore Local Lab

Laboratoire Java local et contrôlé destiné à évaluer, depuis Windows, l’intérêt de données SofaScore comme enrichissement **facultatif** du Betting Project.

> **Statut :** `EXPERIMENTAL` · `LOCAL_ONLY` · `NOT_PRODUCTION_APPROVED` · `NO_CRITICAL_DEPENDENCY`

Le dépôt matérialise les jalons validés **J0 — Gouvernance**, **J1 — Bootstrap**, **J2 — Fixtures**, **J3 — Appel manuel**, **J4 — Événements** et **J5 — Statistiques**. L'implémentation J4, son parcours hors ligne et ses deux sous-étapes réelles bornées sont qualifiés humainement. La sous-étape 1 a validé `16386245` et `16421052` après correction du retour par date. La sous-étape 2 a validé la saisie d'identifiants, le rappel manuel avec une nouvelle confirmation, la déduplication d'une réponse inchangée et la création d'une observation append-only lorsque `16412917` est passé de `notstarted` à `inprogress`. Après l'arrêt global, la configuration a été remise à l'état bloqué, ce verrouillage a été vérifié après redémarrage et l'application a été arrêtée gracieusement. La Pull Request `#8` a été fusionnée et le Work Order J4 est archivé `VALIDATED`. Les voies fournisseur J3, J4 et J5 restent désactivées par défaut et mutuellement exclusives, et aucun appel fournisseur n’est exécuté par Maven, conformément au document de cadrage `Betting_Project_SofaScore_Local_Lab_Cadrage_v0.1.0.pdf` et à l’ADR `ADR-SS-001`.

Le jalon **J5 — Statistiques** est validé techniquement et humainement sur sa frontière hors ligne :
statistiques, incidents et compositions synthétiques, contrôles explicites de complétude,
persistance append-only V7 et écran local. La première découverte des schémas réels s'est arrêtée
dès le premier `HTTP 403`. Le Work Order séparé `WO-SS-20260815-006` a depuis ajouté une voie de
qualification réelle gardée, désactivée par défaut, limitée à une identité canonique et à trois
appels confirmés. Son implémentation et la migration V8 sont qualifiées hors ligne. Une campagne
réelle a ensuite tenté uniquement `EVENT_STATISTICS` pour `16412917` : la réponse JSON HTTP `404`
a été conservée dans le snapshot 30, puis le circuit s'est verrouillé sans appeler `incidents` ou
`lineups` et sans retry. Cette réaction a révélé un défaut de politique : les statistiques sont
facultatives et un `404` peut signifier « famille indisponible », notamment pour une compétition
non majeure. La migration V9 et le correctif J5 distinguent désormais `UNAVAILABLE` d'un incident
et d'une liste vide valide, puis poursuivent les familles restantes sans retry. Aucun schéma
nominal n'a été validé ; `providerSchemaValidated=false` reste donc obligatoire.

## Ce qui est livré localement

- dépôt Git autonome, documentation, ADR, règles agent et Work Orders ;
- Java **25 LTS**, Spring Boot **4.1.0** et Maven Wrapper versionné ;
- interface Spring MVC + Thymeleaf sur `127.0.0.1:8087` ;
- PostgreSQL local dans Docker Desktop, migrations Flyway V1 à V9 et stockage brut séparé ;
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
- parseur fournisseur `event-details-v2` séparé du contrat historique V1, acceptant uniquement
  l’enveloppe `event` et produisant une incompatibilité explicite sans objet partiel ;
- migration V6 ajoutant la provenance `PROVIDER_SNAPSHOT` aux détails sans modifier V1–V5, et
  cache `EVENT_DETAILS` de quinze minutes pointant toujours vers le brut séparé ;
- voie J4 sous-étape 1 limitée par construction à `https://www.sofascore.com`, au chemin exact
  `/api/v1/event/{eventId}` et aux seuls IDs `16386245` et `16421052` ;
- circuit J4 local avec préparation, confirmation exacte, cache préalable, délai minimal de trois
  secondes, deux tentatives maximum, persistance brute avant parsing et verrou terminal ;
- arrêt sans retry au premier incident, `403`, `429`, `5xx`, timeout, contenu non JSON,
  incompatibilité ou incohérence d’identifiant ;
- voie J4 sous-étape 2 sélectionnée par un opt-in distinct, avec un ID numérique saisi dans
  l'interface et lié à une confirmation de cinq minutes ;
- rafraîchissements manuels répétables du même événement : un nouvel appel sans cache par cycle,
  nouvelle confirmation obligatoire, délai minimal de trois secondes et aucune boucle automatique ;
- contrats synthétiques J5 `event-statistics-v1`, `event-incidents-v1` et `event-lineups-v1`, avec
  parsing JSON strict, avertissements bornés et aucune coercition de type ;
- contrôles J5 `COMPLETE`, `PARTIAL`, `EMPTY_VALID` et `UNAVAILABLE`, score déterministe et chemins
  manquants, sans valeur, incident ou joueur inventé ;
- migration V7 conservant les trois familles sous forme d'observations et de lignes normalisées
  append-only, dédupliquées et rattachées à l'identité canonique J4 ;
- page locale `/events/{canonicalEventId}/statistics` avec import synthétique idempotent, valeurs,
  complétude, provenance, parseur et hashes, sans payload ni repli fournisseur ;
- voie J5 réelle opt-in, désactivée par défaut et exclusive de J3/J4, limitée à l'origine exacte
  `https://www.sofascore.com` et aux trois chemins statistiques, incidents et compositions d'une
  identité canonique déjà persistée ;
- préparation J5 sans réseau, confirmation exacte de cinq minutes, acquittement, trois appels
  séquentiels au maximum, délai minimal de trois secondes et verrou terminal dans le processus ;
- parseurs fournisseur `event-statistics-v2`, `event-incidents-v2` et `event-lineups-v2`, brut
  persisté avant parsing, provenance `PROVIDER_SNAPSHOT` et résultat d'écran minimisé ;
- traitement borné du HTTP `404` sur les trois chemins J5 exacts : snapshot
  `ENDPOINT_UNAVAILABLE`, observation `UNAVAILABLE · N/A`, aucun parsing du corps, aucun retry et
  poursuite ordonnée vers la famille suivante ;

## Limite essentielle du bootstrap

**Aucun appel SofaScore réel n’est actif par défaut et aucun n’est exécuté par les tests.** Le
connecteur général, `ConnectorGate`, le catalogue `callable=false` et le profil Maven
`sofascore-live-test` restent bloqués. Trois voies de qualification spéciales sont mutuellement
exclusives : J3 pour `SCHEDULED_EVENTS`, J4 pour `EVENT_DETAILS`, ou J5 pour les trois familles
`EVENT_STATISTICS`, `EVENT_INCIDENTS` et `EVENT_LINEUPS`. Dans J4,
`SOFASCORE_J4_EVENT_DETAILS_PHASE2_ENABLED` sélectionne exclusivement le formulaire paramétrable.
Dans J5, l'action finale réutilise l'identité canonique affichée et autorise au maximum trois
transports ordonnés après une confirmation humaine unique. Toutes ces voies interdisent polling,
planification et retry. Leurs configurations temporaires et leur remise à l'état bloqué sont
décrites dans `docs/runbooks/RUNBOOK-LOCAL.md`.

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

## Modèle de données J1 à J5

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

La migration append-only `V5__offline_event_details.sql` ajoute `event_detail_observation` pour
les fixtures synthétiques J4. La migration append-only
`V6__guarded_real_event_details.sql` étend ensuite sa provenance aux snapshots fournisseur réels
de la voie bornée. Pour les lignes V5 existantes, V6 complète uniquement les deux colonnes de
provenance structurelle dans sa transaction : le trigger de cette table est suspendu pendant ce
backfill borné, puis réactivé avant la fin de la migration. Aucun champ métier historique n’est
modifié. Fixture ou snapshot, chaque détail conserve hash, parseur et heure source obligatoires ;
les observations restent protégées contre `UPDATE` et `DELETE` après V6.

La migration append-only `V7__j5_event_data_completeness.sql` ajoute un lot de provenance et de
complétude par famille, puis des tables séparées pour métriques, incidents, côtés de composition et
joueurs. Chaque lot conserve fixture, SHA-256 brut, parseur, heure source et empreinte normalisée.
Les cinq tables refusent `UPDATE` et `DELETE`; une nouvelle version est ajoutée ou une version
identique est dédupliquée. Les octets de fixture restent dans le corpus classpath et ne sont jamais
recopiés dans ces tables.

La migration append-only `V8__guarded_real_j5_event_data.sql` étend uniquement les contraintes de
parseur et de provenance J5 afin d'accepter les versions fournisseur V2 rattachées à un snapshot
brut. Elle ne modifie aucune migration antérieure ni aucune observation existante.

La migration append-only `V9__j5_optional_family_unavailable.sql` distingue une famille non
publiée d'un incident de transport et d'une liste vide valide. Elle ajoute les statuts
`ENDPOINT_UNAVAILABLE` et `UNAVAILABLE`, autorise les normaliseurs d'indisponibilité versionnés et
reclasse les anciens snapshots J5 HTTP `404` marqués `TRANSPORT_ERROR/HTTP_STATUS_404`, sans
modifier leurs octets, hashes, heures ou identifiants et sans fabriquer d'observation rétroactive.

Le mode `DIRECT_LOCAL_ENDPOINT` ne doit jamais être confondu avec une `VisualObservation` du projet
global. La persistance n'effectue elle-même aucun appel : les écritures J5 réelles éventuelles sont
initiées uniquement par la voie humaine gardée, puis référencent le brut séparé avec
`PROVIDER_SNAPSHOT`.

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
- [Architecture J5 hors ligne et contrôles de complétude](docs/architecture/J5-OFFLINE-EVENT-DATA-AND-COMPLETENESS.md)
- [Architecture de la qualification réelle gardée J5](docs/architecture/J5-GUARDED-REAL-EVENT-DATA.md)
- [Voie réelle bornée J4 — sous-étape 1](docs/architecture/J4-GUARDED-REAL-EVENT-DETAILS-PHASE1.md)
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
- [Préparation technique J4 réelle — sous-étape 1](docs/validation/J4-REAL-EVENT-DETAILS-PHASE1-READINESS-20260815.md)
- [Campagne humaine J4 réelle — sous-étape 1 et anomalie de navigation](docs/validation/J4-REAL-EVENT-DETAILS-PHASE1-CAMPAIGN-20260815.md)
- [Préparation technique J4 réelle — sous-étape 2](docs/validation/J4-REAL-EVENT-DETAILS-PHASE2-READINESS-20260815.md)
- [Campagne humaine J4 réelle — sous-étape 2 et actualisation](docs/validation/J4-REAL-EVENT-DETAILS-PHASE2-CAMPAIGN-20260815.md)
- [Incident et correction de l’upgrade V5 préremplie vers V6](docs/validation/J4-V6-PREFILLED-UPGRADE-INCIDENT-20260815.md)
- [Work Order J4 validé](docs/work_orders/completed/WO-SS-20260815-004-events-j4.md)
- [Qualification technique Windows J5](docs/validation/J5-WINDOWS-TECHNICAL-QUALIFICATION-20260815.md)
- [Work Order J5 validé](docs/work_orders/completed/WO-SS-20260815-005-statistics-j5.md)
- [Observation préalable à la qualification réelle J5](docs/validation/J5-REAL-EVENT-DATA-PREREQUISITE-OBSERVATION-20260815.md)
- [Readiness technique de la qualification réelle J5](docs/validation/J5-REAL-EVENT-DATA-TECHNICAL-READINESS-20260815.md)
- [Work Order actif de qualification réelle J5](docs/work_orders/active/WO-SS-20260815-006-j5-real-event-data-qualification.md)

## J3 et J4 validés, voies fournisseur de nouveau verrouillées

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

L'implémentation J4 construit désormais une vue métier locale sur les sources déjà présentes,
synthétiques ou issues de la campagne réelle strictement bornée. Une normalisation manuelle d’un
snapshot `SCHEDULED_EVENTS` compatible vérifie son intégrité et le reparse sans modifier sa
classification historique ; le corpus de démonstration reste explicitement `SYNTHETIC_FIXTURE`.

La campagne J4 sous-étape 1 a exécuté les deux événements fixes autorisés. Les snapshots 16 et 17
ont été persistés, classés `PARSED` et ouverts localement. Un défaut de navigation ramenait
toutefois la fiche de Saint-Étienne — Clermont Foot au 15 août au lieu de sa date civile du 14
août, et la fiche utilisait des libellés synthétiques statiques pour une provenance fournisseur.
Le correctif conserve maintenant la date du match et affiche la provenance réellement persistée.
Le retest humain sans réseau a confirmé les deux retours par date et la provenance.

La sous-étape 2 a ensuite qualifié les événements paramétrables `16483632` et `16412917`. Un
rappel de `16412917` avant le coup d'envoi a effectué un nouvel appel mais dédupliqué la réponse
inchangée sur le snapshot 19. Un second rappel après le coup d'envoi a persisté le snapshot 23,
fait évoluer le statut à `inprogress` et conservé les deux observations consultables sous la même
identité canonique. L'arrêt global, le reverrouillage des six paramètres, le contrôle `LOCKED`
après redémarrage et l'arrêt gracieux final ont été confirmés. La Pull Request `#8`, déclarée
`MERGEABLE` et `CLEAN`, a été fusionnée sur `main` par le commit
`950bb0f0ddd0edfe2a5e7d1e768498c049a86467`, puis le Work Order a été archivé `VALIDATED`.

```text
J4_IMPLEMENTATION_STATUS=IMPLEMENTATION_MERGED
J4_OFFLINE_STATUS=OFFLINE_PATH_QUALIFIED
J4_REAL_PHASE1_CAMPAIGN_STATUS=EXECUTED
J4_REAL_PHASE1_HUMAN_STATUS=PASS_AFTER_CORRECTIVE_LOCAL_RETEST
J4_REAL_PHASE2_PARAMETERIZED_EVENT_STATUS=PASS
J4_REAL_PHASE2_MANUAL_RECALL_STATUS=PASS
J4_REAL_PHASE2_PRE_KICKOFF_DEDUPLICATION_STATUS=PASS
J4_REAL_PHASE2_IN_MATCH_REFRESH_STATUS=PASS
J4_APPEND_ONLY_HISTORY_STATUS=PASS
J4_CONFIGURATION_RELOCK_STATUS=PASS
J4_FINAL_APPLICATION_SHUTDOWN_STATUS=PASS
J4_FINAL_STANDARD_TESTS=212
J4_FINAL_INTEGRATION_TESTS=16
J4_FINAL_MAVEN_PROVIDER_CALLS=0
J4_PULL_REQUEST=8
J4_MERGE_COMMIT=950bb0f0ddd0edfe2a5e7d1e768498c049a86467
J4_WORK_ORDER_STATUS=VALIDATED
J4_CAN_BE_CLOSED=YES
J4_CLOSED=YES
```

Cette situation ne déverrouille aucune nouvelle famille, automatisation ou dépendance de
production. Les rappels de sous-étape 2 restent exclusivement manuels et unitaires.

## J5 hors ligne validé, politique HTTP 404 corrigée

J5 réutilise l'identité synthétique `900001` de J4 pour démontrer les trois familles demandées. Les
neuf fixtures J5 sont explicitement synthétiques et ne valident aucun schéma fournisseur. La page
locale distingue une rupture structurelle, une famille partielle, une liste vide valide et une
famille fournisseur indisponible, puis affiche chaque chemin manquant avec les métadonnées de
provenance.

```text
J5_IMPLEMENTATION_STATUS=VALIDATED_OFFLINE
J5_HUMAN_OFFLINE_QUALIFICATION=PASS
J5_PROVIDER_SCHEMA_VALIDATED=NO
J5_APPLICATION_TRANSPORT=IMPLEMENTED_GUARDED_DEFAULT_OFF
J5_DISCOVERY_ATTEMPTS=1
J5_DISCOVERY_RESULT=HTTP_403_STOPPED_NO_RETRY
J5_FIXTURE_ORIGIN=SYNTHETIC
J5_FLYWAY_VERSION=9
J5_MAVEN_PROVIDER_CALLS=0
J5_REAL_TECHNICAL_READINESS=PASS
J5_REAL_FIRST_CAMPAIGN=HTTP_404_MISCLASSIFIED_AND_LOCKED
J5_REAL_PROVIDER_CALLS=1
J5_REAL_STATISTICS_SNAPSHOT=30
J5_REAL_INCIDENTS=NOT_ATTEMPTED
J5_REAL_LINEUPS=NOT_ATTEMPTED
J5_HTTP_404_POLICY=ENDPOINT_UNAVAILABLE_CONTINUE_NO_RETRY
J5_CORRECTIVE_RETEST=NOT_RUN
J5_OFFLINE_WORK_ORDER_STATUS=VALIDATED
J5_REAL_WORK_ORDER_STATUS=CORRECTIVE_IMPLEMENTATION_VALIDATED_OFFLINE
J5_REAL_WORK_ORDER_CAN_BE_ARCHIVED=NO
```

`TOURNAMENT_STANDINGS` demeure différé : il ne fait pas partie de la preuve de sortie J5 définie
par le cadrage et son ajout aurait étendu le corpus alors que les schémas des trois familles
principales n'ont pas pu être observés. Les captures de validation humaine ne sont pas versionnées ;
leur constat minimisé est conservé dans les rapports J5. Le processus utilisé par la première
campagne est arrêté. Le correctif ne déclenche aucun appel : une nouvelle qualification reste un
geste humain explicite après application de V9, revue du Work Order amendé et configuration locale
manuelle. Le fichier `.env` demeure ignoré et n'est ni lu ni modifié par l'agent.

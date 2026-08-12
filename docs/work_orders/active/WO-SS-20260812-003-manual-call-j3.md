# WO-SS-20260812-003 — J3 Appel manuel borné et conservation du brut

- **Statut :** `IN_DEVELOPMENT`
- **Date :** 2026-08-12
- **Date de démarrage :** 2026-08-12
- **Prérequis :** WO-SS-20260808-002 validé sous Windows et fusionné sur `main`
- **Jalon :** J3 — Appel manuel
- **Branche :** `feat/j3-manual-call`
- **Commit de base :** `b2561e542b1f893ec2f15c5eaeb67a361ee551ea`
- **Base de l’unité de politique réseau hors ligne :** `c51c45f4831dad2922018e25baee97dcf7bbcf5c`
- **Base de l’unité de persistance brute :** `1a9ca0e753cedf1ac4730adea865bd7ea3369660`
- **Base de l’unité de transport simulé :** `f8f01225bd4efa0eead432c90899d6c9e1f293b9`
- **Base de l’unité de confirmation explicite :** `c33192969f780dfa6d5d98590c4fee79907ccaa5`
- **Base de l’unité de politiques d’arrêt et d’incident :** `e555a13bc8023ebb3dd7a9454719dd16144210e2`
- **Famille initiale :** `SCHEDULED_EVENTS`
- **Mode d’acquisition prévu :** `DIRECT_LOCAL_ENDPOINT`
- **Développement hors ligne J3 autorisé :** `YES`
- **URI réelle autorisée dans cette unité documentaire :** `NO`
- **Appel SofaScore réel autorisé dans cette unité documentaire :** `NO`
- **Polling ou tâche planifiée autorisé :** `NO`
- **Déploiement VPS autorisé :** `NO`

## 1. Objectif

Préparer puis qualifier un chemin d’appel manuel strictement borné pour une seule requête de la
famille `SCHEDULED_EVENTS`. La preuve de sortie visée par le cadrage est : une requête réelle,
déclenchée explicitement par l’opérateur, dont le statut, les horaires, la latence, la taille, le brut,
les empreintes et la version de parseur sont conservés localement, avec affichage JSON et gestion
explicite des incidents.

Le démarrage de ce Work Order autorise la conception, les tests hors ligne et les changements
réversibles nécessaires à J3. Il n’autorise pas encore l’envoi de la requête réelle. Le passage au
réseau exige le point de décision défini à la section 7.

## 2. Contexte hérité de J2

J2 fournit déjà :

- un corpus synthétique `SCHEDULED_EVENTS` de neuf scénarios ;
- le parseur hors ligne `scheduled-events-v1` ;
- le calcul SHA-256 brut et JSON canonique ;
- la classification `PARSED`, `SCHEMA_INCOMPATIBLE` et `UNEXPECTED_CONTENT` ;
- la détection bloquante de contenu sensible ;
- un tableau de bord local capable d’afficher l’état du corpus ;
- les verrous réseau J1 encore intacts.

Le schéma réel du fournisseur reste `NON VALIDÉ`. Aucun payload réel n’est actuellement conservé.

## 3. Périmètre J3 proposé

### 3.1 Inclus

- politique d’activation explicite et confirmation opérateur avant chaque appel ;
- concurrence maximale de `1` et délai minimal de `3 s` ;
- cache consulté avant le transport ;
- client HTTP synchrone avec délais de connexion et de lecture bornés ;
- une définition réelle limitée à `SCHEDULED_EVENTS`, introduite seulement après le point de décision ;
- validation stricte du seul paramètre de date autorisé ;
- enregistrement local des métadonnées de transport et du payload brut ;
- SHA-256 du brut et déduplication des réponses identiques ;
- parsing avec `scheduled-events-v1` après conservation de la preuve brute ;
- affichage local du JSON et du statut de compatibilité ;
- arrêt immédiat et sans retry sur `400`, `401` et `403` ;
- ouverture du circuit et suspension sur `429`, HTML inattendu ou schéma incompatible ;
- au plus un comportement de retry `5xx`, désactivé tant qu’une décision dédiée ne l’autorise pas ;
- tests de transport exclusivement simulés dans Maven ;
- procédure Windows de qualification et d’arrêt.

### 3.2 Exclus

- polling, rafraîchissement automatique ou planification ;
- plus d’un appel simultané ;
- parcours de dates ou collecte en lot ;
- recherche générale d’événements, détails de rencontre, statistiques, incidents ou compositions ;
- cookies, comptes, sessions, jetons ou en-têtes copiés depuis un navigateur ;
- proxy, rotation d’adresse, simulation ou automatisation de navigateur ;
- contournement d’un refus, d’un challenge ou d’un blocage ;
- stockage de payload brut dans Git, dans les logs ou dans le Betting Project principal ;
- transmission au VPS ;
- usage commercial, production ou dépendance critique ;
- modification de l’ADR-SS-001 ;
- live, benchmark de volume ou export canonique J7.

## 4. Invariants

1. `server.address=127.0.0.1` reste obligatoire.
2. `sofascore.enabled=false` reste la valeur par défaut.
3. Une activation explicite et une confirmation distincte précèdent tout appel.
4. Les tests standards ne disposent d’aucun chemin vers Internet.
5. Le profil réel reste bloqué jusqu’au point de décision humain.
6. Aucun secret, cookie, jeton ou identifiant de session n’est stocké ou journalisé.
7. Le payload brut est conservé avant toute tentative de parsing ou normalisation.
8. Les données brutes et normalisées restent séparées.
9. Une donnée normalisée référence le snapshot, le hash, le parseur et l’heure de réception.
10. Une réponse identique ne produit pas un second snapshot inutile.
11. Un refus ou challenge arrête l’expérimentation ; aucun changement d’adresse n’est tenté.
12. L’arrêt du laboratoire n’affecte jamais le Betting Project principal.
13. Les payloads bruts restent locaux et ne sont pas versionnés.
14. Aucun polling ou appel automatique n’est introduit.

## 5. Vérification préalable des conditions d’utilisation

La page officielle `https://www.sofascore.com/terms-and-conditions` a été revue le 2026-08-12,
conformément au déclencheur de réexamen du document de cadrage. La page consultée indique une
dernière mise à jour au 2024-09-18 et contient notamment des clauses relatives au contenu de base de
données, aux requêtes automatisées, à l’agrégation et au scraping.

Ce constat ne constitue pas une interprétation juridique. L’ADR-SS-001 ne fournit lui-même aucune
validation juridique ou contractuelle. En conséquence :

- aucun appel réel ne sera lancé sur la seule base de ce Work Order ;
- le propriétaire doit rendre une décision explicite sur la poursuite de la qualification ;
- toute clarification juridique, contractuelle ou de consentement jugée nécessaire doit intervenir
  avant le premier appel ;
- une décision négative ou incertaine maintient le connecteur verrouillé et peut conduire à limiter J3
  à une démonstration entièrement simulée.

## 6. Unités de livraison prévues

1. `docs: close J2 and start bounded J3 manual call` — terminé
   - clôturer le Work Order J2 ;
   - enregistrer la base J3 ;
   - documenter les limites et le point de décision.
2. `feat: add offline J3 network policy and circuit model` — terminé
   - modéliser activation, délai, concurrence, cache, arrêt et incidents ;
   - tester sans URI réelle et sans réseau.
3. `feat: persist raw manual-call snapshots` — terminé
   - ajouter uniquement les évolutions append-only nécessaires ;
   - tester avec Testcontainers ;
   - garantir la déduplication par hash et la séparation brut/normalisé.
4. `feat: add guarded scheduled-events transport` — terminé
   - introduire le transport derrière les politiques ;
   - utiliser un serveur simulé dans les tests ;
   - conserver le profil réel bloqué.
5. `feat: add explicit manual-call confirmation` — terminé
   - exposer l’activation, la confirmation, l’arrêt global et les incidents dans l’interface locale ;
   - garder les actions réelles désactivées en l’absence de configuration et d’autorisation.
6. `test: cover J3 transport stop and incident policies` — terminé
   - couvrir succès, timeout, `400`, `401`, `403`, `429`, `5xx`, HTML inattendu, taille excessive,
     schéma incompatible, déduplication et absence de secret dans les logs.
7. `docs: record J3 Windows manual-call qualification`
   - seulement après le point de décision et, si autorisé, une requête unique ;
   - consigner les preuves minimisées sans publier le payload brut.

Chaque unité part du commit précédent, reste autonome et fait l’objet d’un commit explicite. Une
modification de persistance déclenche obligatoirement le profil `integration-tests`.

## 7. Point de décision avant tout appel réel

Toutes les conditions suivantes doivent être remplies :

- [ ] décision explicite du propriétaire autorisant une requête unique `SCHEDULED_EVENTS` ;
- [ ] examen des conditions d’utilisation considéré suffisant par le propriétaire ;
- [ ] source et exactitude de l’URI documentées sans donnée de session ;
- [ ] paramètre de date unique validé ;
- [ ] tests standards hors ligne réussis ;
- [ ] tests d’intégration PostgreSQL réussis ;
- [ ] tests de transport sur serveur simulé réussis ;
- [ ] confirmation que `127.0.0.1`, concurrence `1`, délai `3 s` et arrêt global sont effectifs ;
- [ ] preuve qu’aucun cookie, jeton ou secret n’est requis ;
- [ ] sauvegarde ou stratégie de conservation locale décidée ;
- [ ] application et PostgreSQL démarrés localement ;
- [ ] procédure d’incident et d’arrêt disponible à l’écran et dans le runbook.

Si une case manque, la qualification s’arrête avant le transport.

```text
J3_WORK_ORDER=IN_DEVELOPMENT
J3_OFFLINE_DEVELOPMENT_AUTHORIZED=YES
J3_REAL_ENDPOINT_URI_AUTHORIZED=NO
J3_REAL_CALL_AUTHORIZED=NO
J3_POLLING_AUTHORIZED=NO
CONNECTOR_DEFAULT=DISABLED
LIVE_TEST_PROFILE=BLOCKED
```

## 8. Matrice de tests minimale

| Scénario | Résultat attendu |
|---|---|
| connecteur désactivé | refus avant résolution de l’URI |
| configuration absente | action réseau indisponible |
| confirmation absente | refus avant transport |
| concurrence déjà occupée | second appel refusé |
| délai minimal non écoulé | appel refusé ou différé sans attente agressive |
| cache valide | aucun transport, résultat existant utilisé |
| succès JSON simulé | brut conservé, hash calculé, parsing ensuite |
| réponse identique | aucun second snapshot utile |
| timeout | incident visible, circuit selon politique, aucun secret journalisé |
| `400`, `401`, `403` | arrêt sans retry |
| `429` | suspension, `Retry-After` conservé s’il existe, aucun retry rapide |
| `5xx` | aucun retry par défaut dans la première unité de transport |
| HTML inattendu | brut conservé, `UNEXPECTED_CONTENT`, circuit ouvert |
| schéma incompatible | brut conservé, `SCHEMA_INCOMPATIBLE`, aucune normalisation silencieuse |
| payload trop grand | arrêt borné et incident explicite |
| suite Maven standard | aucune connexion Internet possible |

## 9. Critères d’acceptation J3

- [ ] activation explicite nécessaire avant tout transport ;
- [ ] concurrence maximale égale à `1` ;
- [ ] cache et délai minimal vérifiés avant chaque appel ;
- [ ] une requête manuelle conserve statut, horaires, latence, taille, hash et parseur ;
- [ ] déduplication d’une réponse identique ;
- [ ] arrêt sans retry sur `400`, `401` et `403` ;
- [ ] suspension respectueuse de `Retry-After` sur `429` ;
- [ ] HTML inattendu visible et circuit ouvert ;
- [ ] schéma inconnu conservé brut et classé incompatible ;
- [ ] aucun test standard ne contacte Internet ;
- [ ] données brutes et normalisées séparées ;
- [ ] acquisition enregistrée comme `DIRECT_LOCAL_ENDPOINT` ;
- [ ] aucun secret, cookie ou jeton stocké ou journalisé ;
- [ ] aucun mécanisme de contournement ;
- [ ] derniers appels et incidents visibles dans le tableau de bord ;
- [ ] procédure Windows d’arrêt et de preuve exécutée ;
- [ ] appel réel, s’il est autorisé, limité à une requête unique.

## 10. Définition de fini

Avant toute proposition de fusion :

1. exécuter `mvnw.cmd clean verify` ;
2. exécuter `mvnw.cmd -Pintegration-tests verify` si la persistance change ;
3. confirmer que les tests standards sont entièrement hors ligne ;
4. contrôler le diff à la recherche de secret, cookie, jeton, payload brut et URI non autorisée ;
5. confirmer la liaison exclusive à `127.0.0.1` ;
6. mettre à jour le runbook, l’architecture, le changelog et le rapport de validation ;
7. obtenir une validation humaine Windows ;
8. fusionner seulement après revue humaine.

Le Work Order ne passe à `VALIDATED` qu’après satisfaction des critères applicables et validation de
la preuve de sortie J3. Une démonstration simulée peut valider l’architecture technique, mais ne vaut
pas preuve de sortie « une requête réelle » et doit être déclarée comme telle.

## 11. Avancement de la politique réseau hors ligne

L’unité `feat: add offline J3 network policy and circuit model` introduit :

- une évaluation pure donnant `USE_CACHE`, `BLOCKED` ou `TRANSPORT_ELIGIBLE` ;
- un ordre de décision déterministe couvrant activation, arrêt global, endpoint, confirmation,
  circuit, concurrence et délai minimal ;
- un circuit en mémoire démarrant à `LOCKED`, activable explicitement et ouvert par incident ;
- des incidents typés pour `400`, `401`, `403`, `429`, timeout, contenu inattendu, schéma
  incompatible et erreur serveur ;
- la conservation de `retryNotBefore` pour `429` sans réouverture ou retry automatique ;
- une garde atomique limitant à un le nombre de permis simultanés ;
- une documentation d’architecture et des tests unitaires entièrement hors ligne.

`TRANSPORT_ELIGIBLE` reste une information sans effet. `ConnectorGate` conserve un refus
systématique au mode `LOCKED_OFFLINE_J3_POLICY`, le catalogue reste non appelable et aucune URI
réelle n’est présente.

```text
J3_OFFLINE_POLICY=IMPLEMENTED
J3_CIRCUIT_MODEL=IMPLEMENTED
J3_SINGLE_CALL_GUARD=IMPLEMENTED
J3_INCIDENT_PERSISTENCE=NOT_STARTED
J3_RAW_SNAPSHOT_PERSISTENCE=IMPLEMENTED
J3_DATABASE_MIGRATION=V2
J3_RAW_PAYLOAD_LIMIT_BYTES=5242880
J3_RAW_SNAPSHOT_DEDUPLICATION=IMPLEMENTED
J3_SIMULATED_LOOPBACK_TRANSPORT=IMPLEMENTED
J3_PROVIDER_TRANSPORT=BLOCKED
J3_REAL_ENDPOINT_URI_AUTHORIZED=NO
J3_REAL_CALL_AUTHORIZED=NO
```

## 12. Avancement de la persistance des snapshots bruts

L’unité `feat: persist raw manual-call snapshots` introduit :

- un contrat de domaine immuable pour les métadonnées et les octets bruts d’un futur appel manuel ;
- une copie défensive du payload, une limite de 5 Mio et un SHA-256 calculé avant persistance ;
- la migration append-only `V2__raw_manual_call_snapshots.sql` ajoutant `payload_raw`, sa taille et
  la provenance obligatoire `DIRECT_LOCAL_ENDPOINT` ;
- un port de persistance et un adaptateur JDBC retournant `INSERTED` ou `DEDUPLICATED` ;
- une déduplication atomique par fournisseur, endpoint logique, clé de requête et SHA-256 ;
- le maintien de `payload_jsonb` à `NULL`, afin de séparer strictement preuve brute et donnée
  normalisée ;
- des validations empêchant l’écriture de métadonnées non canoniques, de payloads trop grands ou
  de motifs sensibles ;
- des tests unitaires hors ligne et des tests PostgreSQL/Testcontainers couvrant la migration V2,
  la fidélité binaire, les contraintes et la déduplication.

Cette unité n’introduit ni transport, ni URI réelle, ni stockage de headers, ni persistance
d’incident. Le profil réel reste bloqué, `ConnectorGate` refuse toujours tout appel et le catalogue
reste non appelable. Le contrat détaillé est consigné dans
`docs/architecture/J3-RAW-SNAPSHOT-PERSISTENCE.md`.

Validation exécutée le 2026-08-12 :

- `mvnw.cmd clean verify` : `74` tests, `0` échec, `0` erreur, `0` ignoré ;
- `mvnw.cmd --activate-profiles integration-tests verify` : `74` tests standards et `4` tests
  d’intégration, `0` échec, `0` erreur, `0` ignoré ;
- PostgreSQL Testcontainers `18.4` : migrations V1 et V2 appliquées, schéma final `v2` ;
- appels réseau SofaScore exécutés : `0`.

```text
J3_RAW_SNAPSHOT_PERSISTENCE=IMPLEMENTED
J3_RAW_BYTES=EXACT_BYTEA
J3_NORMALIZED_PAYLOAD_WRITE=NO
J3_RAW_PAYLOAD_GIT_STORAGE=NO
J3_NETWORK_CALLS_EXECUTED=NO
J3_GUARDED_SCHEDULED_EVENTS_TRANSPORT=IMPLEMENTED
```

## 13. Avancement du transport `SCHEDULED_EVENTS` protégé

L’unité `feat: add guarded scheduled-events transport` introduit :

- une requête de transport bornée à une date `LocalDate`, une clé canonique et la route fixe
  `/simulated/scheduled-events` ;
- une origine acceptée limitée exactement à `http://127.0.0.1:<port>` ;
- un `RestClient` synchrone sans proxy ni redirection, avec délais de connexion et lecture positifs
  plafonnés à dix secondes ;
- une lecture limitée à 5 Mio, une copie défensive du brut et un SHA-256 calculé avant exposition ;
- une réponse conservant les statuts hors `2xx` sans retry automatique ;
- une orchestration qui applique `J3ManualCallPolicy`, puis acquiert le permis atomique avant toute
  entrée/sortie ;
- des tests avec `MockRestServiceServer` qui ne créent aucune route vers Internet ;
- un scanner PowerShell maintenant l’interdiction générale de construction d’un client HTTP, avec
  une exception limitée à l’unique classe loopback revue.

Le transport n’est volontairement pas enregistré comme bean Spring. `DisabledSofascoreDataProvider`
reste l’adaptateur actif, `ConnectorGate` reste bloquant, le catalogue demeure `callable=false` sans
URI et le profil `sofascore-live-test` échoue toujours. Aucun appel réel n’est autorisé ou exécuté.

Le contrat détaillé est consigné dans
`docs/architecture/J3-GUARDED-SCHEDULED-EVENTS-TRANSPORT.md`.

Validation exécutée le 2026-08-12 :

- tests ciblés transport, garde et identité de build : `9` tests, `0` échec, `0` erreur,
  `0` ignoré ;
- `scripts/Verify-Local.ps1` : préflight Java 25 et scanner de garde-fous réussis ;
- `mvnw.cmd clean verify` exécuté par le script : `83` tests, `0` échec, `0` erreur,
  `0` ignoré ;
- `target/classes/META-INF/build-info.properties` : `build.group=com.bettingproject` ;
- appels réseau SofaScore exécutés : `0`.

```text
J3_GUARDED_TRANSPORT=IMPLEMENTED
J3_TRANSPORT_DESTINATION=SIMULATED_LOOPBACK_ONLY
J3_REAL_ENDPOINT_URI=ABSENT
J3_PROVIDER_TRANSPORT_ACTIVE=NO
J3_RETRY_POLICY=NONE
J3_NEXT_UNIT=EXPLICIT_MANUAL_CALL_CONFIRMATION
```

## 14. Avancement de la confirmation manuelle explicite

L’unité `feat: add explicit manual-call confirmation` introduit :

- un état en mémoire démarrant avec arrêt global actif et circuit `LOCKED` à chaque lancement ;
- un réarmement et une activation opérateur distincts avant toute préparation ;
- une intention `SCHEDULED_EVENTS` limitée à une date et à une clé de requête canonique ;
- un UUID et une phrase exacte à six chiffres, valables cinq minutes et supprimés après usage ;
- une case d’acquittement obligatoire et un état terminal `CONFIRMED_BLOCKED` ;
- un arrêt global immédiat qui verrouille le circuit et annule toute intention active ;
- cinq routes `POST` locales protégées par un jeton de session aléatoire à usage unique ;
- un tableau de bord exposant circuit, incident, étapes opérateur et verrous fournisseur ;
- une action fournisseur toujours désactivée, même après confirmation réussie.

Le service de contrôle ne référence aucun transport. `DisabledSofascoreDataProvider` reste actif,
`ConnectorGate` demeure bloquant, le catalogue ne contient aucune URI appelable et le profil réel
échoue toujours. Le contrat détaillé est consigné dans
`docs/architecture/J3-EXPLICIT-MANUAL-CALL-CONFIRMATION.md`.

Validation exécutée le 2026-08-12 :

- `scripts/Verify-Local.ps1` : préflight Java 25 et scanner de garde-fous réussis ;
- `mvnw.cmd clean verify` exécuté par le script : `93` tests, `0` échec, `0` erreur,
  `0` ignoré ;
- `target/classes/META-INF/build-info.properties` : `build.group=com.bettingproject` ;
- tests d’intégration PostgreSQL non rejoués, cette unité ne modifiant ni migration ni
  persistance ;
- appels réseau SofaScore exécutés : `0`.

```text
J3_EXPLICIT_CONFIRMATION=IMPLEMENTED
J3_CONFIRMATION_TTL=PT5M
J3_GLOBAL_STOP_DEFAULT=ACTIVE
J3_CONFIRMED_STATE=CONFIRMED_BLOCKED
J3_FORM_TOKEN=SESSION_BOUND_SINGLE_USE
J3_PROVIDER_TRANSPORT_ACTIVE=NO
J3_REAL_ENDPOINT_URI=ABSENT
J3_REAL_CALL_AUTHORIZED=NO
J3_NEXT_UNIT=TRANSPORT_STOP_AND_INCIDENT_POLICY_TESTS
```

## 15. Avancement des politiques d’arrêt et d’incident

L’unité `test: cover J3 transport stop and incident policies` introduit et vérifie :

- un processeur déterministe des réponses et échecs du transport simulé, sans entrée/sortie réseau ;
- la persistance du brut en `RAW_ONLY` avant parsing, puis la classification idempotente du même
  snapshot ;
- le succès JSON, la déduplication et la conservation des octets, de leur taille et de leur hash ;
- l’arrêt sans retry sur `400`, `401`, `403`, `429`, `500` et `503` ;
- la conservation d’un `Retry-After` valide ou d’une garde locale de cinq minutes, sans retry
  planifié ni réouverture automatique ;
- l’ouverture du circuit sur timeout, erreur d’entrée/sortie, taille excessive et contenu sensible ;
- la conservation avant classement des réponses HTML inattendues et des schémas incompatibles ;
- la détection des timeouts de connexion et de lecture dans la chaîne des causes ;
- l’absence de valeur sensible dans les messages sûrs et les sorties capturées ;
- l’obligation d’un arrêt et d’une nouvelle activation explicites après tout incident.

`DisabledSofascoreDataProvider`, `ConnectorGate`, le catalogue non appelable, l’absence d’URI réelle
et le profil bloquant restent inchangés. Le processeur n’est pas relié à l’interface ni à un transport
fournisseur actif. Le contrat détaillé est consigné dans
`docs/architecture/J3-TRANSPORT-STOP-AND-INCIDENT-POLICIES.md`.

Validation consolidée exécutée le 2026-08-12 :

- `scripts/Verify-Local.ps1 -WithIntegrationTests` : préflight Java 25, Docker et scanner de
  garde-fous réussis ;
- `mvnw.cmd clean verify` : `115` tests, `0` échec, `0` erreur, `0` ignoré ;
- `mvnw.cmd -Pintegration-tests verify` : `115` tests standards et `5` tests d’intégration,
  `0` échec, `0` erreur, `0` ignoré ;
- PostgreSQL Testcontainers `18.4` : migrations V1/V2 appliquées, schéma final `v2` ;
- `target/classes/META-INF/build-info.properties` : `build.group=com.bettingproject` ;
- appels réseau SofaScore exécutés : `0`.

```text
J3_STOP_AND_INCIDENT_POLICY_TESTS=IMPLEMENTED
J3_RAW_BEFORE_PARSE=ENFORCED
J3_HTTP_STOP_STATUSES=400,401,403,429,5XX
J3_RETRY_AFTER=BLOCKING_BOUNDARY_ONLY
J3_AUTOMATIC_RETRY=NO
J3_SENSITIVE_VALUE_IN_OUTPUT=NO
J3_PROVIDER_TRANSPORT_ACTIVE=NO
J3_REAL_ENDPOINT_URI=ABSENT
J3_REAL_CALL_AUTHORIZED=NO
J3_NEXT_UNIT=WINDOWS_MANUAL_CALL_QUALIFICATION_DECISION
```

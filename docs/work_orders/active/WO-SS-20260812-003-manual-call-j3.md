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
- **Base de l’unité de qualification Windows :** `f0cd29ccc40ac7c430130630f6e55f17b09f6968`
- **Base du chemin fournisseur cinq pages :** `9b1441c367d1e72282fbc563a340bcd1644f3b06`
- **Famille initiale :** `SCHEDULED_EVENTS`
- **Mode d’acquisition prévu :** `DIRECT_LOCAL_ENDPOINT`
- **Développement hors ligne J3 autorisé :** `YES`
- **Périmètre fournisseur autorisé :** `CINQ_PAGES (1, 2, 3, 4, 5)`
- **Source des URI :** `OBSERVATION_MANUELLE_DANS_LE_NAVIGATEUR`
- **Décision du propriétaire sur les conditions d’utilisation :** `SUFFISANT_POUR_UNE_QUALIFICATION_UNIQUE`
- **Cookies, jetons, compte ou données de session autorisés :** `NO`
- **URI réelles autorisées pour le développement du chemin :** `YES`
- **Appel SofaScore réel autorisé dans cette unité documentaire :** `NO`
- **Polling ou tâche planifiée autorisé :** `NO`
- **Déploiement VPS autorisé :** `NO`

## 1. Objectif

Préparer puis qualifier un chemin d’appel manuel strictement borné pour une action opérateur unique
de la famille `SCHEDULED_EVENTS`. Après décision explicite du propriétaire, cette action comprend
jusqu’à cinq requêtes HTTP séquentielles, une pour chacune des pages `1` à `5`. La preuve de sortie
visée par le cadrage est : un lot réel unique,
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
- validation stricte du paramètre de date et des seules pages `1`, `2`, `3`, `4`, `5` ;
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
- parcours de dates, pagination ouverte ou collecte au-delà du lot fixe de cinq pages explicitement
  autorisé pour une qualification unique ;
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
7. `docs: record J3 Windows manual-call qualification` — terminé pour le périmètre local et simulé
   - le point de décision réel étant incomplet, arrêter la qualification avant tout transport ;
   - consigner les preuves Windows minimisées sans publier de phrase active, de jeton ni de payload ;
   - déclarer séparément la qualification technique réussie et la preuve de sortie réelle absente.

Chaque unité part du commit précédent, reste autonome et fait l’objet d’un commit explicite. Une
modification de persistance déclenche obligatoirement le profil `integration-tests`.

## 7. Point de décision avant tout appel réel

Toutes les conditions suivantes doivent être remplies :

- [x] décision explicite du propriétaire autorisant une action unique `SCHEDULED_EVENTS` couvrant
  les pages `1` à `5` ;
- [x] examen des conditions d’utilisation considéré suffisant par le propriétaire pour cette
  qualification unique ;
- [x] source et exactitude des URI documentées comme observation manuelle dans le navigateur ;
- [x] paramètre de date unique validé ;
- [x] tests standards hors ligne réussis ;
- [x] tests d’intégration PostgreSQL réussis ;
- [x] tests de transport sur serveur simulé réussis ;
- [x] confirmation que `127.0.0.1`, concurrence `1`, délai `3 s` et arrêt global sont effectifs ;
- [x] décision explicite de ne fournir aucun cookie, jeton, compte ou donnée de session ;
- [x] sauvegarde ou stratégie de conservation locale décidée ;
- [x] application et PostgreSQL démarrés localement ;
- [x] procédure d’incident et d’arrêt disponible à l’écran et dans le runbook.

Si une case manque, la qualification s’arrête avant le transport.

```text
J3_WORK_ORDER=IN_DEVELOPMENT
J3_OFFLINE_DEVELOPMENT_AUTHORIZED=YES
J3_WINDOWS_LOCAL_QUALIFICATION=PASS
J3_REAL_OUTPUT_PROOF=NOT_AVAILABLE
J3_POINT_OF_DECISION=PASSED_FOR_IMPLEMENTATION_ONLY
J3_REAL_ENDPOINT_URI_AUTHORIZED=YES_FOR_PAGES_1_TO_5
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

## 16. Qualification Windows du parcours manuel

L’unité `docs: record J3 Windows manual-call qualification`, fondée sur le commit
`f0cd29ccc40ac7c430130630f6e55f17b09f6968`, enregistre :

- la validation humaine sous Windows du démarrage protégé, du réarmement et de l’activation
  explicite ;
- la préparation d’une intention `SCHEDULED_EVENTS` datée et sa confirmation exacte ;
- l’état terminal `CONFIRMED_BLOCKED`, avec action et transport fournisseur indisponibles ;
- l’arrêt global immédiat, le verrouillage du circuit et l’annulation de l’intention active ;
- la validation automatisée consolidée de `115` tests standards et `5` tests d’intégration ;
- la qualification des incidents sur transport simulé, sans retry automatique ;
- l’absence d’URI réelle, d’autorisation réseau et d’appel SofaScore exécuté.

Le rapport détaillé est conservé dans
`docs/validation/J3-WINDOWS-MANUAL-CALL-QUALIFICATION-20260812.md`. Les captures restent hors Git et
le rapport ne reproduit ni phrase active, ni jeton, ni payload brut.

Cette unité valide l’architecture technique hors ligne, mais ne vaut pas preuve de sortie réelle. Les
trois premières conditions du point de décision et la preuve d’absence de secret fournisseur restent
ouvertes. Le Work Order demeure donc `IN_DEVELOPMENT`.

```text
J3_LAST_PLANNED_DELIVERY_UNIT=COMPLETED_WITH_SCOPE_LIMITATION
J3_WINDOWS_LOCAL_OPERATOR_QUALIFICATION=PASS
J3_SIMULATED_TRANSPORT_POLICY_QUALIFICATION=PASS
J3_PROVIDER_ACTION_AVAILABLE=NO
J3_PROVIDER_TRANSPORT_ACTIVE=NO
J3_REAL_ENDPOINT_URI=ABSENT
J3_REAL_CALL_AUTHORIZED=NO
J3_REAL_SOFASCORE_CALLS_EXECUTED=0
J3_REAL_OUTPUT_PROOF=NOT_AVAILABLE
J3_POINT_OF_DECISION=NOT_PASSED
J3_WORK_ORDER=IN_DEVELOPMENT
```

## 17. Amendement — chemin fournisseur manuel cinq pages

Le 2026-08-13, le propriétaire a autorisé le **développement**, sans exécution réseau pendant
l’implémentation, d’un chemin réel manuel borné aux URI suivantes :

```text
https://www.sofascore.com/api/v1/sport/football/scheduled-tournaments/{date}/page/1
https://www.sofascore.com/api/v1/sport/football/scheduled-tournaments/{date}/page/2
https://www.sofascore.com/api/v1/sport/football/scheduled-tournaments/{date}/page/3
https://www.sofascore.com/api/v1/sport/football/scheduled-tournaments/{date}/page/4
https://www.sofascore.com/api/v1/sport/football/scheduled-tournaments/{date}/page/5
```

La date autorisée par la décision est exactement `2026-08-13`. L’interface l’affiche explicitement
et le domaine rejette toute autre date ; aucun modèle de date général ni parcours automatique
d’autres dates n’est autorisé. La source déclarée des URI
est `OBSERVATION_MANUELLE_DANS_LE_NAVIGATEUR`. Le propriétaire considère l’examen des conditions
d’utilisation `SUFFISANT_POUR_UNE_QUALIFICATION_UNIQUE` et n’autorise aucun cookie, jeton, compte
ou donnée de session.

L’expression « appel manuel unique » désigne désormais une seule séquence confirmée par
l’opérateur. Cette séquence peut effectuer au maximum cinq requêtes HTTP, strictement dans l’ordre
`1, 2, 3, 4, 5`, avec une seule requête simultanée et au moins trois secondes entre deux départs.
Elle s’arrête avant la page suivante dès qu’une politique d’incident s’applique. Aucun retry, aucune
reprise, aucune pagination découverte depuis la réponse et aucune seconde exécution de la même
confirmation ne sont permis.

Le chemin doit rester désactivé par défaut et exiger une configuration locale explicite. Les tests
standards et d’intégration restent intégralement hors ligne. L’autorisation d’implémentation ne vaut
pas autorisation d’exécuter les cinq requêtes pendant le développement ; l’exécution restera une
action ultérieure du propriétaire depuis l’interface qualifiée.

```text
J3_MANUAL_BATCH_SCOPE=FIVE_PAGES
J3_MANUAL_BATCH_PAGES=1,2,3,4,5
J3_MANUAL_BATCH_ORDER=SEQUENTIAL
J3_MANUAL_BATCH_CONCURRENCY=1
J3_MANUAL_BATCH_MINIMUM_DELAY=PT3S
J3_MANUAL_BATCH_RETRY=NONE
J3_URI_SOURCE=OBSERVATION_MANUELLE_DANS_LE_NAVIGATEUR
J3_TERMS_OWNER_DECISION=SUFFISANT_POUR_UNE_QUALIFICATION_UNIQUE
J3_SESSION_DATA_AUTHORIZED=NO
J3_IMPLEMENTATION_NETWORK_EXECUTION=FORBIDDEN
```

## 18. Livraison technique du chemin cinq pages

L’implémentation issue du commit de base `9b1441c367d1e72282fbc563a340bcd1644f3b06` fournit :

- une politique d’activation distincte du connecteur général et désactivée dans la configuration
  versionnée ;
- une requête de domaine fermée sur l’origine, la date, le chemin et les pages autorisés ;
- une confirmation à usage unique suivie d’une action Web réelle séparée ;
- une exécution strictement séquentielle des pages `1` à `5`, avec délai minimal de trois secondes ;
- un transport sans proxy, redirection, cookie, jeton, compte ou donnée de session ;
- la conservation de chaque réponse avant parsing et l’arrêt de toute page suivante au premier
  incident ;
- des tests entièrement hors ligne de l’URI, de l’activation, du transport, de l’orchestration, de
  la persistance et de l’interface.

La configuration locale réelle n’a pas été modifiée et aucune application n’a été démarrée pour
effectuer l’action. L’autorisation de développement a donc été respectée sans produire de trafic
SofaScore. La qualification humaine de l’interface et l’éventuel clic réel restent des étapes
ultérieures et distinctes.

Validation automatisée exécutée le 2026-08-13 :

- `scripts/Verify-Local.ps1` : préflight Java 25 et scanner de garde-fous réussis ;
- `mvnw.cmd clean verify` exécuté par le script : `127` tests, `0` échec, `0` erreur,
  `0` ignoré ;
- tests d’intégration PostgreSQL non rejoués, cette unité ne modifiant ni migration ni contrat de
  persistance ;
- `target/classes/META-INF/build-info.properties` : identité Maven contrôlée par le test de build ;
- appels réseau SofaScore exécutés : `0`.

```text
J3_FIVE_PAGE_PROVIDER_PATH=IMPLEMENTED
J3_PROVIDER_PATH_DEFAULT=DISABLED
J3_PROVIDER_DATE=2026-08-13
J3_PROVIDER_PAGES=1,2,3,4,5
J3_PROVIDER_PAGE_ORDER=SEQUENTIAL
J3_PROVIDER_MAX_CONCURRENCY=1
J3_PROVIDER_MINIMUM_DELAY=PT3S
J3_PROVIDER_SESSION_DATA=NONE
J3_PROVIDER_AUTOMATIC_RETRY=NO
J3_IMPLEMENTATION_NETWORK_CALLS=0
J3_STANDARD_TESTS=127
J3_STANDARD_TEST_RESULT=PASS
J3_INTEGRATION_TESTS_EXECUTED=NO_PERSISTENCE_CHANGE
J3_WORK_ORDER=IN_DEVELOPMENT
```

## 19. Qualification Windows pré-exécution du chemin cinq pages

Le 2026-08-13, le propriétaire a validé sous Windows l’état issu du commit
`e2769cae1e1ce1499e33a045a411820e3d505382`, complété par les corrections de liaison Spring, de
sélection des constructeurs de production, de non-régression et de présentation consignées dans :

```text
docs/validation/J3-WINDOWS-FIVE-PAGE-PROVIDER-READINESS-20260813.md
```

Cette qualification confirme :

- la liaison effective des quatre variables locales `SOFASCORE_*` documentées ;
- le démarrage du contexte Spring et l’injection déterministe des trois composants possédant des
  constructeurs de test secondaires ;
- l’affichage `QUALIFICATION J3 PRÊTE` lorsque la politique spécialisée est disponible ;
- le démarrage sous arrêt global, puis les gestes séparés de réarmement et d’activation ;
- la préparation de la date exacte `2026-08-13` et des pages fixes `1` à `5` ;
- le passage à `CONFIRMED_READY` après phrase exacte et acquittement ;
- la disponibilité du bouton final uniquement après confirmation ;
- le maintien du connecteur général verrouillé et de `SCHEDULED_EVENTS` comme seule famille
  appelable dans ce mode ;
- l’arrêt volontaire de la session avant le clic final.

La validation automatisée a été reproduite après réception du rapport avec
`scripts/Verify-Local.ps1` : Java 25, scanner de garde-fous, `128` tests standards, build du JAR et
identité Maven sont passés sans échec. Les tests d’intégration ne sont pas requis pour ces
corrections, qui ne modifient ni migration ni contrat de persistance.

Cette preuve porte exclusivement sur la préparation technique et humaine avant exécution. Elle ne
contient aucune preuve de réponse fournisseur, de page effectivement atteinte, de snapshot brut
réel ou d’incident observé. Le Work Order reste donc ouvert jusqu’à une qualification réelle
séparée, explicitement déclenchée par le propriétaire.

```text
J3_WINDOWS_PRE_EXECUTION_QUALIFICATION=PASS
J3_CONFIGURATION_BINDING=PASS
J3_SPRING_CONSTRUCTOR_INJECTION=PASS
J3_CONNECTOR_READY_PRESENTATION=PASS
J3_DEDICATED_PATH=CONFIRMED_READY
J3_FINAL_PROVIDER_BUTTON=AVAILABLE
J3_GENERAL_CONNECTOR=LOCKED
J3_STANDARD_TESTS=128
J3_STANDARD_TEST_RESULT=PASS
J3_REAL_PROVIDER_CALL=NOT_EXECUTED
J3_PROVIDER_PAGES_REQUESTED=0
J3_RAW_PROVIDER_SNAPSHOTS_PERSISTED=0
J3_REAL_PROVIDER_OUTPUT_PROOF=NOT_AVAILABLE
J3_WORK_ORDER=IN_DEVELOPMENT
```

## 20. Unité B — déclenchement réel et collecte de la preuve minimisée

Le propriétaire autorise l’implémentation de l’unité qui rend le bouton terminal exploitable pour
la qualification réelle et prépare la collecte d’une preuve minimisée. Cette autorisation porte sur
le développement et les tests hors ligne ; elle ne demande pas à l’agent d’exécuter le lot
fournisseur pendant l’implémentation.

L’unité ajoute :

- une preuve terminale en mémoire, dérivée seulement des métadonnées de transport et du résultat de
  persistance de chacune des pages effectivement tentées ;
- l’affichage et le téléchargement local de cette preuve, sans payload, URI, en-tête, cookie, jeton,
  compte, session ou identifiant de confirmation ;
- le verrouillage automatique `LOCKED / QUALIFICATION_TERMINAL_LOCK` après `COMPLETED` ou `FAILED` ;
- le refus d’un réarmement dans le même processus après consommation de la qualification ;
- une procédure Windows imposant la collecte de la preuve avant l’arrêt de l’application et le
  retour de la configuration locale aux valeurs désactivées.

Les scénarios automatisés restent hors ligne et couvrent les cinq pages nominales, un incident HTTP
persisté, un échec de transport avant snapshot, la minimisation, le téléchargement et le verrou
terminal. Aucun contrat de persistance ni migration n’est modifié.

Validation consolidée exécutée le 2026-08-13 :

- `scripts/Verify-Local.ps1` : préflight Java 25 et scanner de garde-fous réussis ;
- `mvnw.cmd clean verify` exécuté par le script : `132` tests, `0` échec, `0` erreur,
  `0` ignoré ;
- JAR Spring Boot construit avec succès et identité Maven `com.bettingproject` conservée ;
- tests d’intégration non rejoués, cette unité ne modifiant ni migration ni contrat de persistance ;
- appels réseau SofaScore exécutés : `0`.

Cette livraison ne clôt pas J3 : le propriétaire doit encore déclencher l’unique lot depuis
l’interface, télécharger la preuve, remettre la configuration locale en état sûr puis fournir la
qualification humaine terminale.

```text
J3_UNIT_B_IMPLEMENTATION=AUTHORIZED
J3_MINIMIZED_EVIDENCE=IMPLEMENTED
J3_EVIDENCE_STORAGE=IN_MEMORY_METADATA_ONLY
J3_EVIDENCE_DOWNLOAD=LOCAL_NO_STORE
J3_RAW_PAYLOAD_EXPOSED=NO
J3_TERMINAL_GLOBAL_STOP=AUTOMATIC
J3_TERMINAL_CIRCUIT_REASON=QUALIFICATION_TERMINAL_LOCK
J3_TERMINAL_REARM_SAME_PROCESS=FORBIDDEN
J3_IMPLEMENTATION_PROVIDER_CALLS=0
J3_STANDARD_TESTS=132
J3_STANDARD_TEST_RESULT=PASS
J3_INTEGRATION_TESTS_EXECUTED=NO_PERSISTENCE_CHANGE
J3_REAL_PROVIDER_QUALIFICATION=PENDING_OWNER_ACTION
J3_WORK_ORDER=IN_DEVELOPMENT
```

## 21. Adaptation hors ligne au schéma fournisseur qualifié

L’unité `fix: adapt scheduled-events-v1 to qualified provider schema`, fondée sur le commit
`8bcbd46595773800f8759ce5761d35a4b1c75220`, traite l’incompatibilité observée pendant l’unique
séquence réelle. Elle n’effectue aucun nouvel appel fournisseur.

Le diagnostic structurel du snapshot local `1` a établi que la réponse expose `scheduled` et
`hasNextPage`, et non `events`. Chaque entrée utile contient une identité `tournament`, une identité
`uniqueTournament` facultative et un objet `timezoneEventCount` dont les clés sont des décalages en
secondes et les valeurs des nombres d’événements.

L’unité introduit :

- une seconde forme explicite `SCHEDULED_TOURNAMENT_LIST` dans le DTO externe et le modèle local ;
- un modèle immuable `ScheduledTournamentAvailability` conservant les identités minimales et les
  compteurs par décalage horaire ;
- la sélection stricte d’une seule racine parmi `events` et `scheduled` ;
- le rejet des identités absentes, types numériques devenus texte, nombres négatifs et racines
  ambiguës, sans page partielle ;
- la compatibilité maintenue avec les neuf scénarios historiques J2 ;
- une dixième fixture entièrement synthétique représentant uniquement la forme utile qualifiée ;
- le passage du tableau de bord à `10 / 10`, dont `6` résultats `PARSED`, et l’indication distincte
  que la structure fournisseur a été validée par relecture locale du snapshot ;
- la conservation intacte du snapshot brut et de son statut historique de qualification.

Validation consolidée exécutée le 2026-08-13 :

- `scripts/Verify-Local.ps1` : préflight Java 25 et scanner de garde-fous réussis ;
- `mvnw.cmd clean verify` : `135` tests, `0` échec, `0` erreur, `0` ignoré ;
- JAR Spring Boot construit avec succès et identité Maven `com.bettingproject` conservée ;
- tests d’intégration non rejoués, cette unité ne modifiant ni migration ni persistance ;
- appels réseau SofaScore exécutés : `0`.

La relecture technique locale du snapshot par le parseur adapté donne :

```text
OFFLINE_REPARSE_STATUS=PARSED
OFFLINE_REPARSE_PROBLEMS=0
OFFLINE_REPARSE_SCHEDULED_ENTRY_COUNT=100
OFFLINE_REPARSE_EVENTS_INVENTED=0
OFFLINE_REPARSE_HAS_NEXT_PAGE=true
ORIGINAL_SNAPSHOT_STATUS_MUTATED=NO
NEW_PROVIDER_CALLS_EXECUTED=0
```

Cette unité rétablit la compatibilité hors ligne du parseur avec la page 1 conservée. Elle ne clôt
pas J3 et n’autorise ni répétition de la séquence consommée, ni reprise à la page 2. Une éventuelle
requalification réelle reste une décision propriétaire distincte.

```text
J3_QUALIFIED_PROVIDER_SCHEMA_ADAPTATION=IMPLEMENTED
J3_PROVIDER_ROOT_SCHEDULED=SUPPORTED
J3_LEGACY_EVENTS_ROOT=SUPPORTED
J3_AMBIGUOUS_ROOT=REJECTED
J3_SYNTHETIC_FIXTURE_CORPUS=10
J3_STANDARD_TESTS=135
J3_STANDARD_TEST_RESULT=PASS
J3_INTEGRATION_TESTS_EXECUTED=NO_PERSISTENCE_CHANGE
J3_PROVIDER_RAW_PAYLOAD_IN_GIT=NO
J3_NEW_NETWORK_CALLS=0
J3_REAL_REQUALIFICATION=PENDING_SEPARATE_DECISION
J3_WORK_ORDER=IN_DEVELOPMENT
```

## 22. Reprise contrôlée à la page 2

Après la qualification humaine du schéma corrigé, le propriétaire autorise une unité distincte de
reprise réelle à la page 2. L’objectif est de compléter la même qualification avec les pages 2 à 5,
sans redemander la page 1. La possibilité de répéter ultérieurement une interrogation complète
depuis l’interface reste explicitement hors périmètre.

L’unité introduit :

- un port de lecture local des checkpoints de qualification, sans migration ni mutation du snapshot
  historique ;
- une politique exigeant exactement une page 1, aucune page 2 à 5, une intégrité taille/SHA-256, un
  statut HTTP `2xx`, une relecture `PARSED` par le parseur corrigé et `hasNextPage=true` ;
- une intention explicite `SCHEDULED_EVENTS|date=2026-08-13|pages=2-5`, dont le compteur démarre à
  `1` grâce au checkpoint vérifié ;
- un orchestrateur qui commence réellement à la page 2 et conserve les garde-fous existants : ordre
  fixe, concurrence `1`, délai minimal de trois secondes, persistance avant parsing, arrêt au premier
  incident et absence de retry ;
- un verrou persistant par présence de la page 2, empêchant de répéter la reprise après redémarrage ;
- une interface et une preuve minimisée v2 distinguant la page 1 locale des pages 2 à 5 réellement
  tentées pendant la reprise.

Le développement, les tests ciblés et les validations consolidées de cette unité n’exécutent aucun
appel SofaScore. L’appel réel reste une action humaine ultérieure via le bouton final.

```text
J3_PAGE_1_CHECKPOINT=REQUIRED_AND_REPARSED_LOCALLY
J3_RESUME_FIRST_PROVIDER_PAGE=2
J3_RESUME_LAST_PROVIDER_PAGE=5
J3_RESUME_PAGE_1_REPEATED=NO
J3_RESUME_AUTOMATIC_RETRY=NO
J3_RESUME_REPEAT_AFTER_PAGE_2=BLOCKED
J3_MINIMIZED_EVIDENCE_VERSION=2
J3_IMPLEMENTATION_PROVIDER_CALLS=0
J3_REPEATABLE_GUI_QUERY=OUT_OF_SCOPE_NEXT_UNIT
J3_WORK_ORDER=IN_DEVELOPMENT
```

### 22.1 Validation technique de l’unité

La validation hors ligne du 2026-08-13 confirme :

```text
WINDOWS_PREFLIGHT=PASS
SOURCE_GUARDRAIL_SCAN=PASS
STANDARD_TESTS=142
STANDARD_FAILURES=0
STANDARD_ERRORS=0
DIFF_CHECK=PASS
SECRET_SCAN=PASS
SERVER_ADDRESS=127.0.0.1
SOFASCORE_NETWORK_CALLS_EXECUTED=NO
```

Le profil `integration-tests` a été lancé, mais Testcontainers n’a pas pu créer sa base éphémère :
le compte sandbox n’avait pas accès au tube Windows `docker_engine`. L’échec intervient avant
l’exécution de `FlywayMigrationIT` et ne constitue donc pas un résultat fonctionnel du lecteur de
checkpoint. Ce profil devra être rejoué dans le terminal Windows utilisateur avant la fusion ; il
ne nécessite et ne doit déclencher aucun appel SofaScore.

## 23. Adaptation hors ligne au schéma qualifié de la page 2

Le rapport
`docs/validation/J3-WINDOWS-PAGE-TWO-PROVIDER-RESUME-QUALIFICATION-20260813.md` établit que la page
2 a été reçue et conservée, puis classée historiquement `SCHEMA_INCOMPATIBLE`. Le propriétaire
autorise l’unité `fix: adapt scheduled-events-v1 to qualified page-two schema` exclusivement sur ce
snapshot local. Aucun nouvel appel fournisseur n’est autorisé ou exécuté.

L’analyse structurelle en lecture seule, sans restitution du payload ou de ses valeurs métier,
compare les deux checkpoints :

```text
PAGE_1_SCHEDULED_ENTRIES=100
PAGE_1_TIMEZONE_EVENT_COUNT_OBJECTS=100
PAGE_1_TIMEZONE_EVENT_COUNT_EMPTY_ARRAYS=0
PAGE_2_SCHEDULED_ENTRIES=100
PAGE_2_TIMEZONE_EVENT_COUNT_OBJECTS=94
PAGE_2_TIMEZONE_EVENT_COUNT_EMPTY_ARRAYS=6
PAGE_2_TIMEZONE_EVENT_COUNT_NON_EMPTY_ARRAYS=0
PAGE_2_HAS_NEXT_PAGE=true
```

La correction accepte `timezoneEventCount=[]` uniquement lorsqu’il est strictement vide. Il devient
une table locale vide avec l’avertissement `EMPTY_TIMEZONE_EVENT_COUNT`. Un tableau non vide reste
`SCHEMA_INCOMPATIBLE`, comme les clés non numériques, les valeurs négatives, les scalaires et les
champs absents. Cette règle ne modifie ni la racine qualifiée, ni la compatibilité J2, ni le refus
d’une page partielle.

L’unité ajoute :

- une fixture locale synthétique représentant l’objet et le tableau vide de la page 2 ;
- une fixture de rupture représentant un tableau non vide, explicitement rejeté ;
- les contrôles de hash, de provenance synthétique et d’absence de donnée sensible ;
- des tests positifs, de rupture et de relecture de checkpoints ;
- un résultat applicatif de reparsing limité aux métadonnées, distinct de la classification
  historique persistée ;
- la lecture de `schema_status` par le checkpoint store, sans port d’écriture ni migration.

La relecture hors ligne des snapshots réels confirme :

```text
PAGE_1_SNAPSHOT_ID=1
PAGE_1_HISTORICAL_STATUS=SCHEMA_INCOMPATIBLE
PAGE_1_CURRENT_PARSE_STATUS=PARSED
PAGE_1_SCHEDULED_ENTRY_COUNT=100
PAGE_1_HAS_NEXT_PAGE=true
PAGE_2_SNAPSHOT_ID=2
PAGE_2_HISTORICAL_STATUS=SCHEMA_INCOMPATIBLE
PAGE_2_CURRENT_PARSE_STATUS=PARSED
PAGE_2_SCHEDULED_ENTRY_COUNT=100
PAGE_2_HAS_NEXT_PAGE=true
HISTORICAL_STATUSES_UNCHANGED=true
DATABASE_MUTATION_EXECUTED=NO
PROVIDER_NETWORK_CALL_EXECUTED=NO
RAW_PAYLOAD_PRINTED=NO
```

Cette unité n’autorise pas la reprise fournisseur. La présence de la page 2 continue donc de
bloquer le chemin actuel. Une unité ultérieure `feat: resume J3 qualification from page three`
nécessitera une nouvelle décision explicite et devra imposer :

```text
PAGE_1_CHECKPOINT=PRESENT_AND_REPARSEABLE
PAGE_2_CHECKPOINT=PRESENT_AND_REPARSEABLE
PAGES_3_TO_5=ABSENT
PROVIDER_FIRST_PAGE=3
PAGE_1_REPEATED=NO
PAGE_2_REPEATED=NO
```

```text
J3_PAGE_TWO_SCHEMA_ADAPTATION=IMPLEMENTED
J3_PAGE_TWO_EMPTY_TIMEZONE_COUNT_ARRAY=SUPPORTED_EXACTLY
J3_NON_EMPTY_TIMEZONE_COUNT_ARRAY=REJECTED
J3_SYNTHETIC_FIXTURE_CORPUS=12
J3_PROVIDER_RAW_PAYLOAD_IN_GIT=NO
J3_HISTORICAL_SNAPSHOT_STATUS_MUTATED=NO
J3_PROVIDER_PAGE_THREE_AUTHORIZED=NO
J3_NEW_NETWORK_CALLS=0
J3_WORK_ORDER=IN_DEVELOPMENT
```

### 23.1 Validation technique de l’unité

Validation consolidée exécutée le 2026-08-14 :

```text
WINDOWS_PREFLIGHT=PASS
SOURCE_GUARDRAIL_SCAN=PASS
STANDARD_TESTS=145
STANDARD_FAILURES=0
STANDARD_ERRORS=0
STANDARD_SKIPPED=0
SPRING_BOOT_JAR=BUILT
BUILD_GROUP=com.bettingproject
DIFF_CHECK=PASS
SECRET_SCAN=PASS
SERVER_ADDRESS=127.0.0.1
SOFASCORE_NETWORK_CALLS_EXECUTED=NO
```

Le profil `integration-tests` a également été lancé. Les `145` tests standards ont de nouveau
réussi, puis Testcontainers n’a pas pu accéder au moteur Docker de la session. L’unique erreur est
survenue avant le démarrage de `FlywayMigrationIT` (`Could not find a valid Docker environment`) :
aucune assertion PostgreSQL n’a échoué et aucun appel fournisseur n’a été tenté.

En complément, le lecteur et le parseur ont été exécutés directement contre le PostgreSQL local
existant dans une connexion explicitement `readOnly`, terminée par `rollback`. Cette validation a
reparsé les deux snapshots réels avec `PARSED`, confirmé `hasNextPage=true` sur la page 2, comparé
les statuts avant et après, puis confirmé `HISTORICAL_STATUSES_UNCHANGED=true` sans afficher le
payload.

## 24. Reprise contrôlée à la page 3

Le propriétaire autorise explicitement l’unité `feat: resume J3 qualification from page three` à
partir du commit `067276956bdec5da3185f3417155cd740ee7170d`. Cette décision succède à la
qualification humaine de la page 2 et à son adaptation hors ligne. L’implémentation et les tests de
cette unité n’exécutent aucun nouvel appel fournisseur.

La politique de reprise impose exactement :

```text
PAGE_1_CHECKPOINT=PRESENT_AND_REPARSEABLE
PAGE_2_CHECKPOINT=PRESENT_AND_REPARSEABLE
PAGES_3_TO_5=ABSENT
PROVIDER_FIRST_PAGE=3
PAGE_1_REPEATED=NO
PAGE_2_REPEATED=NO
```

Chaque checkpoint doit être unique, intègre, associé à un statut HTTP `2xx`, reparsé localement
avec `PARSED` et annoncer `hasNextPage=true`. Toute page 3, 4 ou 5 déjà conservée bloque la reprise.
Les statuts historiques `SCHEMA_INCOMPATIBLE` des pages 1 et 2 restent immuables : la politique ne
dispose d’aucun port d’écriture et utilise le résultat courant du parseur uniquement en mémoire.

L’unité adapte :

- la politique persistante pour retourner `PROVIDER_FIRST_PAGE=3` seulement après validation des
  deux checkpoints et absence des pages suivantes ;
- l’intention explicite vers `SCHEDULED_EVENTS|date=2026-08-13|pages=3-5`, avec phrase
  `REPRISE PAGES 3-5` et compteur initial à `2` ;
- l’orchestrateur pour demander exactement les pages 3, 4 et 5, dans l’ordre, avec concurrence `1`,
  délai minimal de trois secondes, persistance avant parsing, arrêt au premier incident et aucun
  retry ;
- le tableau de bord, les messages opérateur et le runbook afin d’identifier les deux checkpoints
  conservés et le bouton final limité aux pages 3 à 5 ;
- la preuve minimisée v2 afin d’annoncer `VERIFIED_LOCAL_CHECKPOINT_PAGES=1,2`,
  `PROVIDER_RESUME_FIRST_PAGE=3` et uniquement les pages 3 à 5 dans `PAGES_ATTEMPTED`.

La possibilité de répéter une interrogation complète depuis l’interface reste hors périmètre et
nécessitera une unité et une décision distinctes.

```text
J3_PAGE_THREE_RESUME_IMPLEMENTATION=AUTHORIZED
J3_PAGE_1_CHECKPOINT=REQUIRED_AND_REPARSED_LOCALLY
J3_PAGE_2_CHECKPOINT=REQUIRED_AND_REPARSED_LOCALLY
J3_PAGES_3_TO_5_PREEXISTING=FORBIDDEN
J3_RESUME_FIRST_PROVIDER_PAGE=3
J3_RESUME_LAST_PROVIDER_PAGE=5
J3_RESUME_PAGE_1_REPEATED=NO
J3_RESUME_PAGE_2_REPEATED=NO
J3_RESUME_AUTOMATIC_RETRY=NO
J3_IMPLEMENTATION_PROVIDER_CALLS=0
J3_REPEATABLE_GUI_QUERY=OUT_OF_SCOPE_NEXT_UNIT
J3_WORK_ORDER=IN_DEVELOPMENT
```

### 24.1 Validation technique de l’unité

Validation consolidée exécutée le 2026-08-14 avec le dépôt Maven local en mode hors ligne :

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
INTEGRATION_TESTS_EXECUTED=NO_PERSISTENCE_OR_MIGRATION_CHANGE
SOFASCORE_NETWORK_CALLS_EXECUTED=NO
```

Les tests ciblés prouvent également que le transport simulé reçoit exactement `3,4,5`, que deux
délais inter-pages sont appliqués, que la preuve annonce les checkpoints `1,2` et qu’elle ne
contient aucun horodatage de tentative pour les pages 1 ou 2. Le test Web confirme que l’interface
rend le bouton `PAGES 3 À 5` et n’expose plus l’ancien bouton `PAGES 2 À 5` dans l’état
`CONFIRMED_READY`.

## 25. Collecte manuelle répétable à pagination dynamique

Après la qualification humaine concluante des cinq pages du `2026-08-13`, le propriétaire conclut
que la pagination réelle n’est pas fixée à cinq pages. L’endpoint qualifié suit le modèle :

```text
https://www.sofascore.com/api/v1/sport/football/scheduled-tournaments/<date>/page/<page_number>
```

La racine JSON expose `hasNextPage=true` lorsqu’une page suivante existe et `false` sur la dernière
page. Le propriétaire autorise donc l’objectif précédemment annoncé : dynamiser ce processus dans
l’interface et permettre sa répétition par de nouvelles séquences manuelles explicites.

L’unité impose :

```text
PROVIDER_FIRST_PAGE=1
PAGINATION_DRIVER=PARSED_HAS_NEXT_PAGE
NORMAL_TERMINATION=HAS_NEXT_PAGE_FALSE
LOCAL_MAXIMUM_PAGE=25
PAGE_26_REQUEST_ALLOWED=NO
MAXIMUM_CONCURRENCY=1
MINIMUM_INTER_PAGE_DELAY=3_SECONDS
PERSISTENCE_BEFORE_PARSING=YES
STOP_ON_FIRST_INCIDENT=YES
AUTOMATIC_RETRY=NO
POLLING_OR_SCHEDULE=NO
REPEAT_REQUIRES_NEW_EXPLICIT_SEQUENCE=YES
IMPLEMENTATION_PROVIDER_CALLS=0
```

Une valeur `hasNextPage` absente ou non booléenne reste `SCHEMA_INCOMPATIBLE`. Après succès,
incident ou plafond, l’arrêt global est réappliqué et le circuit reçoit
`MANUAL_COLLECTION_TERMINAL_LOCK`. La levée explicite suivante retire l’intention terminale et
permet une nouvelle date ou une nouvelle interrogation de la même date. La configuration J3 reste
désactivée par défaut.

La preuve minimisée v3 ajoute le mode de pagination, le plafond local et la valeur booléenne parsée
pour chaque page. Elle n’expose toujours ni payload, ni URI, ni en-tête, ni phrase/identifiant de
confirmation, ni donnée de session.

L’unité modifie l’interface, le contrôle opérateur, l’orchestrateur, le résultat du parseur transmis
à l’orchestration et les modèles de preuve. Elle ne modifie aucune migration ni aucun snapshot
existant et n’exécute aucun appel fournisseur pendant ses tests.

```text
J3_DYNAMIC_MANUAL_PAGINATION=IMPLEMENTED
J3_REPEATABLE_GUI_COLLECTION=IMPLEMENTED
J3_FIXED_FIVE_PAGE_ASSUMPTION=REMOVED_FROM_ACTIVE_PATH
J3_HISTORICAL_QUALIFICATION_EVIDENCE=PRESERVED
J3_WORK_ORDER=IN_DEVELOPMENT
```

### 25.1 Validation technique de l’unité

Validation consolidée exécutée le 2026-08-14 sur une copie locale sans `.env`, avec préflight et
garde-fous source, puis Maven standard :

```text
PREFLIGHT_RESULT=PASS
JAVA_TARGET=25
SOURCE_GUARDRAIL_SCAN=PASS
STANDARD_TESTS=148
STANDARD_FAILURES=0
STANDARD_ERRORS=0
STANDARD_SKIPPED=0
SPRING_BOOT_JAR=BUILT
BUILD_GROUP=com.bettingproject
SERVER_ADDRESS=127.0.0.1
INTEGRATION_TESTS_EXECUTED=NO_PERSISTENCE_OR_MIGRATION_CHANGE
SOFASCORE_NETWORK_CALLS_EXECUTED=NO
```

Les tests ciblés confirment une page terminale unique, une séquence `1,2,3,4,5` pilotée par
`true,true,true,true,false`, les délais inter-pages, l’arrêt au premier incident, le refus de la
page 26 après 25 valeurs `true`, la preuve v3 et le réarmement d’une nouvelle séquence explicite.

## 26. Qualification humaine Windows de la pagination dynamique

La qualification humaine du parcours dynamique a été exécutée le 2026-08-14 sur le commit
`57a187315a106bcb8aaf180dd2446edb0546318b`. L’opérateur a sélectionné la date `2026-08-14`,
préparé et confirmé une intention explicite, puis déclenché l’action finale distincte depuis
l’interface locale.

La preuve minimisée v3 établit :

```text
COLLECTION_DATE=2026-08-14
PAGINATION_MODE=HAS_NEXT_PAGE
PROVIDER_FIRST_PAGE=1
MAXIMUM_PAGE_LIMIT=25
PAGES_ATTEMPTED=1,2,3,4,5,6,7,8,9,10
PAGES_COMPLETED_COUNT=10
LAST_COMPLETED_PAGE=10
FAILED_PAGE=NONE
TERMINAL_CODE=NONE
PAGE_1_TO_9_HAS_NEXT_PAGE=true
PAGE_10_HAS_NEXT_PAGE=false
FINAL_GLOBAL_STOP=ACTIVE
FINAL_CIRCUIT_STATE=LOCKED
FINAL_CIRCUIT_REASON=MANUAL_COLLECTION_TERMINAL_LOCK
AUTOMATIC_RETRY_EXECUTED=NO
POLLING_OR_SCHEDULE_EXECUTED=NO
COOKIES_TOKENS_ACCOUNT_SESSION_USED=NO
```

Les dix réponses ont reçu un statut HTTP `200`, ont été persistées avec `INSERTED`, puis classées
`PARSED`. Les snapshots `6` à `15` ont été ajoutés sans réécriture des cinq snapshots historiques.
L’intervalle minimal observé entre deux départs est de `3000.140 ms`. Aucune page 11 n’a été
demandée après la valeur terminale `hasNextPage=false` de la page 10.

Le rapport détaillé est conservé dans :

```text
docs/validation/J3-WINDOWS-DYNAMIC-PAGINATION-QUALIFICATION-20260814.md
```

```text
J3_DYNAMIC_PAGINATION_HUMAN_QUALIFICATION=PASS
J3_2026_08_13_OBSERVED_PAGES=5
J3_2026_08_14_OBSERVED_PAGES=10
J3_FIXED_FIVE_PAGE_ASSUMPTION=INVALIDATED
J3_HAS_NEXT_PAGE_DRIVER=QUALIFIED
J3_PAGE_AFTER_TERMINAL_FALSE_REQUESTED=NO
J3_MINIMIZED_EVIDENCE_V3=PASS
J3_WORK_ORDER=IN_DEVELOPMENT
```

Cette qualification ne modifie pas les statuts `EXPERIMENTAL`, `LOCAL_ONLY`,
`NOT_PRODUCTION_APPROVED` et `NO_CRITICAL_DEPENDENCY`. Elle n’autorise aucun polling, live,
traitement planifié ou usage par le Betting Project principal.

### 26.1 Validation du changement documentaire

La validation consolidée a été rejouée après enregistrement du rapport :

```text
PREFLIGHT_RESULT=PASS
JAVA_TARGET=25
SOURCE_GUARDRAIL_SCAN=PASS
STANDARD_TESTS=148
STANDARD_FAILURES=0
STANDARD_ERRORS=0
STANDARD_SKIPPED=0
SPRING_BOOT_JAR=BUILT
VERIFY_RESULT=PASS
INTEGRATION_TESTS_EXECUTED=NO_DOCUMENTATION_ONLY_CHANGE
SOFASCORE_NETWORK_CALLS_EXECUTED=NO
```

## 27. Unité 1 — cache du chemin réel dynamique

L’unité `feat: enforce cache policy in dynamic manual collection` démarre depuis le commit de
fusion `e27c96574aa75095d91e16c16f0a21913ac8cd5b` sur la branche
`feat/j3-dynamic-cache-policy`. Elle rend effective, dans l’orchestrateur dynamique, la priorité de
cache déjà définie par la politique J3. Son implémentation et ses tests n’exécutent aucun appel
fournisseur.

Le contrat appliqué avant chaque transport est :

```text
CACHE_KEY=SCHEDULED_EVENTS|date=<date>|page=<page>
CACHE_TTL=PT10M
CACHE_HTTP_STATUS=2XX
CACHE_HISTORICAL_SCHEMA_STATUS=PARSED
CACHE_PARSER_VERSION=scheduled-events-v1
CACHE_RAW_INTEGRITY_CHECK=SIZE_AND_SHA256
CACHE_FRESHNESS_CHECKPOINT=FLYWAY_V3_SEPARATE_FROM_RAW
CACHE_REPARSE_BEFORE_USE=YES
CACHE_LOOKUP_BEFORE_PROVIDER_DELAY=YES
CACHE_HIT_PROVIDER_TRANSPORT=NO
CACHE_HIT_PERSISTENCE_MUTATION=NO
CACHE_HIT_INTER_PAGE_WAIT=NO
DELAY_MEASURED_BETWEEN_PROVIDER_STARTS=YES
EXACT_TTL_BOUNDARY=EXPIRED
```

Le cache ne se contente pas de la classification historique : les octets sont relus et reparsés
avec la version courante avant que `hasNextPage` soit accepté. Une incompatibilité ne peut donc pas
être masquée par une ancienne classification. En cas de cache miss, le chemin existant conserve la
persistance brute avant parsing, l’arrêt au premier incident et l’absence de retry.

La preuve terminale passe à la version 4 et distingue pour chaque page `CACHE` et `PROVIDER`. Elle
indique séparément `PROVIDER_PAGES_REQUESTED`, `CACHE_HIT_PAGES`, leurs compteurs et l’absence de
transport sur un cache hit, sans ajouter de payload, URI, en-tête ou donnée de session.

```text
J3_DYNAMIC_CACHE_POLICY=IMPLEMENTED
J3_CACHE_PERSISTENCE_MIGRATION=V3_APPEND_ONLY
J3_IMPLEMENTATION_PROVIDER_CALLS=0
J3_WORK_ORDER=IN_DEVELOPMENT
```

### 27.1 Validation technique de l’unité

La validation a été exécutée le 2026-08-14 sur une copie locale isolée sans `.env`, avec le dépôt
Maven local en mode hors ligne. La suite standard, le scan des garde-fous et la suite PostgreSQL
ont tous abouti sans appel fournisseur :

```text
PREFLIGHT_RESULT=PASS
JAVA_TARGET=25
SOURCE_GUARDRAIL_SCAN=PASS
STANDARD_TESTS=150
STANDARD_FAILURES=0
STANDARD_ERRORS=0
STANDARD_SKIPPED=0
SPRING_BOOT_JAR=BUILT
INTEGRATION_TESTS=8
INTEGRATION_FAILURES=0
INTEGRATION_ERRORS=0
INTEGRATION_SKIPPED=0
FLYWAY_LATEST_VERSION=3
POSTGRESQL_CACHE_SELECTION=PASS
DEDUPLICATED_RESPONSE_REFRESHES_CACHE=PASS
EXACT_TTL_BOUNDARY_REJECTED=PASS
PARSER_VERSION_MISMATCH_REJECTED=PASS
SERVER_ADDRESS=127.0.0.1
SOFASCORE_NETWORK_CALLS_EXECUTED=NO
```

Les tests unitaires prouvent qu’une collecte entièrement servie par le cache exécute zéro
transport, zéro attente et zéro mutation de persistance. Le scénario mixte `PROVIDER(1)`,
`CACHE(2)`, `PROVIDER(3)` confirme que le second départ fournisseur reste séparé du premier par
trois secondes et que le cache hit intermédiaire ne réinitialise pas ce délai. La preuve v4
distingue les deux sources. Le test PostgreSQL confirme la sélection du snapshot exact, son rejet
à dix minutes révolues, le refus d’une autre version de parseur et le rafraîchissement du checkpoint
après une nouvelle observation dont le payload brut a été dédupliqué.

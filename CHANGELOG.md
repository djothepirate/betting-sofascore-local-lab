# Changelog

Les évolutions notables du SofaScore Local Lab sont consignées dans ce fichier.

## [Non publié]

### Ajouté

- manifeste de fixture v1 avec origine, preuve de schéma, taille maximale, hashes attendus et traçabilité de minimisation ;
- chargeur de fixtures classpath entièrement hors ligne, avec classification `JSON`, `HTML` ou `OTHER` ;
- calcul SHA-256 brut et JSON canonique stable malgré l’ordre des propriétés ;
- contrôles bloquants de taille, d’intégrité, de JSON ambigu et de motifs sensibles.
- corpus synthétique `SCHEDULED_EVENTS` de neuf scénarios avec manifestes, hashes réels et test d’inventaire classpath.
- parseur hors ligne `scheduled-events-v1`, DTO externe minimal et mapper vers un modèle local immuable ;
- résultats de parsing structurés avec statuts, avertissements, problèmes et preuve de traçabilité ;
- couverture hors ligne des incompatibilités `scheduled-events-v1` : champs obligatoires absents, type numérique modifié, structure inattendue et contenu HTML ;
- inventaire applicatif du corpus classpath avec disponibilité et répartition des résultats de parsing ;
- politique de décision J3 hors ligne avec résultats `USE_CACHE`, `BLOCKED` et `TRANSPORT_ELIGIBLE` ;
- circuit J3 en mémoire à activation explicite, incidents typés et absence de réouverture automatique ;
- garde atomique limitant à un le nombre de permis d’appel simultanés ;
- migration Flyway V2 append-only conservant les octets exacts, leur taille et la provenance `DIRECT_LOCAL_ENDPOINT` ;
- port et adaptateur JDBC de persistance des snapshots manuels bruts, avec résultat explicite `INSERTED` ou `DEDUPLICATED` ;
- validation bornée des métadonnées et payloads bruts, calcul SHA-256 et déduplication par endpoint, requête et hash ;
- tests PostgreSQL/Testcontainers de la migration V2, de la fidélité binaire, de la séparation brut/normalisé et de la déduplication ;
- contrat de transport `SCHEDULED_EVENTS` limité à l’origine exacte `127.0.0.1` et à une route de simulation fixe ;
- transport `RestClient` synchrone sans proxy ni redirection, avec délais plafonnés, lecture bornée et conservation des octets de réponse ;
- orchestrateur appliquant la politique J3 puis la garde atomique avant toute entrée/sortie simulée ;
- tests de serveur simulé couvrant la requête exacte, le statut `429` sans retry, la taille maximale et le rejet de contenu sensible ;
- contrôle opérateur J3 en mémoire démarrant sous arrêt global, avec réarmement et activation distincts ;
- intention manuelle `SCHEDULED_EVENTS` datée, phrase exacte à usage unique, acquittement explicite et expiration après cinq minutes ;
- tableau de bord local exposant circuit, incidents, arrêt global, confirmation et verrous fournisseur sans rendre le transport disponible ;

### Documentation

- démarrage du Work Order `WO-SS-20260808-002` sur la branche `feat/j2-scheduled-events-fixtures` depuis le tag `j0-j1-v0.1.1` ;
- passage du jalon J2 au statut `IN_DEVELOPMENT` avec `SCHEDULED_EVENTS` comme première famille de fixtures hors ligne.
- contrat d’architecture `scheduled-events-v1` détaillant les champs obligatoires, facultatifs et inconnus.
- validation Windows et clôture du Work Order J2 après fusion de la Pull Request `#2` sur `main` ;
- ouverture du Work Order `WO-SS-20260812-003` sur la branche `feat/j3-manual-call` depuis le commit de fusion `b2561e542b1f893ec2f15c5eaeb67a361ee551ea` ;
- périmètre J3 découpé en unités hors ligne avec un point de décision explicite avant toute URI ou requête réelle.
- contrat d’architecture de la politique réseau J3 hors ligne, de son ordre d’évaluation et de ses transitions de circuit.
- contrat de persistance J3 du snapshot brut, de ses contraintes, de sa clé de déduplication et de ses limites de sécurité.
- contrat de transport J3 simulé, de sa frontière loopback et de sa composition avec les politiques.
- contrat de confirmation manuelle J3, de ses transitions sûres et de ses protections Web locales.

### Modifié

- renommage du package Java de base de `com.geoffrey.betting.sofascorelocal` vers `com.bettingproject.sofascorelocal`.
- renommage du groupId `com.geoffrey.betting` vers `com.bettingproject` dans le pom.xml
- tableau de bord enrichi avec le compteur des neuf fixtures hors ligne et leur état de validation synthétique.
- mode visible du verrou mis à jour vers `LOCKED_OFFLINE_J3_POLICY` sans ouvrir le transport.
- phase applicative avancée à `J3-OFFLINE-RAW-PERSISTENCE` sans modifier l’état du connecteur.
- phase applicative avancée à `J3-GUARDED-SIMULATED-TRANSPORT`, toujours sans transport fournisseur actif.
- phase applicative avancée à `J3-EXPLICIT-MANUAL-CONFIRMATION`, avec confirmation d’intention uniquement.
- identité de build Maven protégée par Enforcer et par un test des métadonnées Actuator générées,
  afin d’empêcher la réapparition de `com.geoffrey.betting` depuis un dossier `target` obsolète.
- calcul SHA-256 et détection de contenu sensible mutualisés entre les fixtures hors ligne et les futures preuves brutes.

### Sécurité

- maintien du verrouillage réseau pendant J2 : aucune URI d’endpoint réelle et aucun appel SofaScore réel ne sont autorisés.
- maintien du connecteur et du profil réel bloqués au démarrage de J3 ; la revue des conditions officielles impose une décision humaine préalable avant tout appel.
- résultat `TRANSPORT_ELIGIBLE` explicitement sans effet : aucun client HTTP, aucune URI réelle et aucun appel fournisseur ne sont introduits.
- rejet avant persistance des payloads dépassant 5 Mio ou contenant des motifs de secret, cookie, jeton ou clé privée ; aucun octet brut n’est journalisé ou versionné.
- maintien de `payload_jsonb` à `NULL` pour les snapshots bruts afin d’éviter toute normalisation implicite avant parsing.
- maintien de `ConnectorGate`, du catalogue, du profil réel et de l’adaptateur fournisseur en état bloqué ; le seul chemin HTTP introduit cible strictement une simulation sur `127.0.0.1`.
- formulaires opérateur protégés par un jeton aléatoire lié à la session et à usage unique, avec cookie `HttpOnly` et `SameSite=Strict`.
- suppression de la phrase de confirmation après usage, expiration ou arrêt global ; aucun contenu saisi n’est journalisé.

## [0.1.0] — 2026-08-08

### Ajouté

- dépôt Git autonome `betting-sofascore-local-lab` ;
- ADR-SS-001 et cadrage PDF de référence ;
- bootstrap Spring Boot 4.1.0 / Java 25 LTS ;
- Maven Wrapper verrouillé sur Maven 3.9.16 avec contrôle SHA-256 ;
- interface Spring MVC + Thymeleaf liée à `127.0.0.1:8087` ;
- garde de liaison locale et en-têtes de sécurité ;
- PostgreSQL 18.4 local via Docker Compose ;
- migration Flyway V1 : snapshots bruts, manifestes d’export et contrôle du connecteur ;
- catalogue logique des familles d’endpoints, sans URI ni appel possible ;
- verrou logiciel `LOCKED_OFFLINE_J1` ;
- tests unitaires hors ligne et test d’intégration PostgreSQL/Testcontainers ;
- scripts PowerShell de configuration, préflight, démarrage, arrêt et vérification ;
- documentation d’architecture, runbook et Work Orders J0/J1 et J2.

### Sécurité

- connecteur désactivé par défaut ;
- profil `sofascore-live-test` bloqué ;
- aucune URI SofaScore intégrée ;
- aucun accès au VPS ni exposition réseau non locale.

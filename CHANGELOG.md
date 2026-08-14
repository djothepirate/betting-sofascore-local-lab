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
- processeur d’issues J3 conservant le brut avant parsing puis appliquant une classification idempotente au même snapshot ;
- couverture des arrêts sur `400`, `401`, `403`, `429`, `5xx`, timeout, erreur d’entrée/sortie, taille excessive, HTML et schéma incompatible ;
- conservation bornée de `Retry-After` comme frontière de blocage, sans programmation de retry ni réouverture automatique ;
- preuve automatisée de déduplication et d’absence de valeur sensible dans les sorties capturées ;
- chemin fournisseur J3 opt-in limité à l’origine exacte `https://www.sofascore.com`, à la date
  `2026-08-13` et aux pages `1` à `5` de `SCHEDULED_EVENTS` ;
- orchestrateur d’une action manuelle unique exécutant les pages séquentiellement, avec concurrence
  `1`, délai minimal de trois secondes, conservation avant parsing et arrêt au premier incident ;
- action Web distincte après confirmation, états `CONFIRMED_READY`, `EXECUTING`, `COMPLETED` et
  `FAILED`, et interdiction d’une seconde exécution dans le même processus ;
- client fournisseur sans proxy, redirection, cookie, jeton, compte ou donnée de session, couvert
  hors ligne avec `MockRestServiceServer` ;
- preuve terminale J3 minimisée, affichable et téléchargeable localement, composée uniquement des
  métadonnées de transport, de persistance et de classement des pages effectivement tentées ;
- verrou terminal automatique `QUALIFICATION_TERMINAL_LOCK` après succès ou incident, avec refus
  du réarmement dans le même processus après consommation de la qualification ;
- fixture synthétique minimisée au contrat utile observé `scheduled` / `tournament` /
  `timezoneEventCount`, sans valeur, URI, en-tête ou donnée de session fournisseur ;
- modèle local explicite des disponibilités de tournois et des compteurs d’événements par décalage
  horaire, distinct de l’ancien modèle synthétique `events` ;
- politique de reprise J3 relisant et reparsant localement l’unique snapshot de page 1 avant
  d’autoriser une séquence fournisseur strictement limitée aux pages 2 à 5 ;
- checkpoint PostgreSQL contrôlant l’unicité, l’intégrité brute, le succès HTTP, la compatibilité du
  parseur et `hasNextPage=true`, avec blocage persistant dès qu’une page 2 existe ;
- intention, action graphique et preuve minimisée v2 dédiées à la reprise, sans ouvrir la future
  interrogation complète répétable ;
- fixtures synthétiques de la forme qualifiée page 2 et de sa rupture par tableau non vide, sans
  donnée fournisseur, URI, cookie, jeton, compte ou session ;
- reparsing applicatif en lecture seule des checkpoints J3, avec séparation explicite entre statut
  historique persisté et résultat courant en mémoire ;
- politique de reprise J3 à la page 3 exigeant les deux checkpoints locaux reparsables et l’absence
  persistée des pages 3 à 5 avant de rendre le transport éligible ;
- intention et orchestration bornées aux pages 3, 4 et 5, avec compteur initial à deux, absence de
  répétition des pages 1 et 2 et preuve minimisée distinguant checkpoints et tentatives réseau ;
- collecte manuelle répétable depuis le tableau de bord, repartant obligatoirement de la page 1 et
  progressant uniquement selon le booléen `hasNextPage` produit par `scheduled-events-v1` ;
- plafond local de 25 pages avec arrêt `PAGINATION_LIMIT_REACHED` avant toute tentative de page 26,
  réarmement explicite entre deux collectes et maintien de la concurrence à un ;
- preuve minimisée v3 indiquant le mode de pagination, la limite locale et `hasNextPage` pour les
  pages parsées, sans payload, URI, en-tête, secret ou donnée de session ;

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
- matrice d’architecture des politiques J3 d’arrêt, d’incident, de conservation du brut et d’absence de retry.
- qualification Windows J3 du parcours opérateur local, de l’arrêt global et des politiques simulées,
  avec déclaration distincte de l’absence d’appel réel et de preuve de sortie fournisseur.
- amendement du Work Order J3 avec le périmètre cinq pages, la source
  `OBSERVATION_MANUELLE_DANS_LE_NAVIGATEUR`, la décision propriétaire et l’interdiction d’exécuter
  un appel pendant l’implémentation ;
- contrat d’architecture et procédure opérateur du chemin fournisseur J3 borné.
- rapport Windows de qualification pré-exécution du chemin cinq pages, couvrant la liaison de la
  configuration locale, l’injection Spring, le parcours opérateur jusqu’à `CONFIRMED_READY` et la
  disponibilité du bouton final sans l’exécuter.
- contrat et procédure de collecte de la preuve minimisée après l’unique lot réel, sans copie du
  payload brut et avec réapplication automatique de l’arrêt global.
- rapport terminal de la qualification réelle cinq pages, limité à la page 1 par l’arrêt sûr sur
  incompatibilité, puis diagnostic structurel hors ligne du snapshot local conservé.
- contrat d’architecture et procédure Windows de la reprise explicite à la page 2, sans appel réel
  pendant l’implémentation ni les tests.
- diagnostic et contrat d’adaptation hors ligne du schéma qualifié de la page 2, avec frontière
  explicite interdisant la reprise à la page 3 sans nouvelle décision.
- contrat d’architecture et procédure Windows de la reprise explicitement autorisée à la page 3,
  sans appel réel pendant l’implémentation ou les tests.
- contrat d’architecture et procédure opérateur de la collecte manuelle répétable à pagination
  dynamique, sans appel fournisseur pendant l’implémentation ou les tests.
- rapport Windows de qualification humaine de la pagination dynamique, confirmant dix pages sur
  dix pour le `2026-08-14`, la progression `hasNextPage=true` des pages 1 à 9, la terminaison sur
  `false` en page 10, la persistance avant parsing et la réapplication du verrou terminal.

### Modifié

- renommage du package Java de base de `com.geoffrey.betting.sofascorelocal` vers `com.bettingproject.sofascorelocal`.
- renommage du groupId `com.geoffrey.betting` vers `com.bettingproject` dans le pom.xml
- tableau de bord enrichi avec le compteur des dix fixtures hors ligne et l’état distinct de
  validation structurelle du schéma fournisseur.
- mode visible du verrou mis à jour vers `LOCKED_OFFLINE_J3_POLICY` sans ouvrir le transport.
- phase applicative avancée à `J3-OFFLINE-RAW-PERSISTENCE` sans modifier l’état du connecteur.
- phase applicative avancée à `J3-GUARDED-SIMULATED-TRANSPORT`, toujours sans transport fournisseur actif.
- phase applicative avancée à `J3-EXPLICIT-MANUAL-CONFIRMATION`, avec confirmation d’intention uniquement.
- phase applicative avancée à `J3-TRANSPORT-STOP-INCIDENT-POLICIES`, toujours sans transport fournisseur actif.
- phase applicative avancée à `J3-FIVE-PAGE-PROVIDER-QUALIFICATION-PATH`, avec chemin dédié
  désactivé par défaut et connecteur général toujours verrouillé.
- phase applicative avancée à `J3-MINIMIZED-PROVIDER-EVIDENCE`, sans modifier la désactivation par
  défaut du chemin fournisseur.
- phase applicative avancée à `J3-PAGE-TWO-PROVIDER-RESUME`, avec checkpoint local obligatoire et
  reprise fournisseur limitée aux pages 2 à 5.
- identité de build Maven protégée par Enforcer et par un test des métadonnées Actuator générées,
  afin d’empêcher la réapparition de `com.geoffrey.betting` depuis un dossier `target` obsolète.
- calcul SHA-256 et détection de contenu sensible mutualisés entre les fixtures hors ligne et les futures preuves brutes.
- classification JSON/HTML/OTHER mutualisée entre le corpus hors ligne et les réponses du transport simulé.
- adaptateur JDBC étendu avec une transition contrôlée de `RAW_ONLY` vers le résultat final du parseur.
- liaison explicite de `SOFASCORE_ENABLED`, `SOFASCORE_J3_QUALIFICATION_ENABLED`,
  `SOFASCORE_BASE_URL` et `SOFASCORE_ALLOWED_ENDPOINTS` vers les propriétés Spring, avec valeurs
  versionnées toujours sûres par défaut ;
- sélection explicite des constructeurs Spring de production du transport, de l’orchestrateur cinq
  pages et du contrôle manuel ;
- présentation du connecteur prête en vert avec un libellé humain et classes CSS exclusives.
- parseur `scheduled-events-v1` étendu au schéma fournisseur qualifié dont la racine contient
  `scheduled` et `hasNextPage`, tout en conservant la compatibilité du corpus J2 `events` ;
- inventaire hors ligne porté à dix fixtures et indicateur de schéma fournisseur validé après
  relecture locale réussie du snapshot qualifié, sans nouvel appel réseau.
- parseur `scheduled-events-v1` adapté à la représentation `[]` strictement vide observée pour
  `timezoneEventCount` en page 2, tout tableau non vide restant incompatible ;
- inventaire hors ligne porté à douze fixtures (`7` parsées, `4` incompatibles et `1` contenu
  inattendu) et phase applicative avancée à `J3-PAGE-TWO-SCHEMA-ADAPTATION`.
- phase applicative avancée à `J3-DYNAMIC-MANUAL-PAGINATION` ; le chemin actif n’est plus limité à
  la date qualifiée ni aux cinq pages observées le `2026-08-13`.

### Sécurité

- maintien du verrouillage réseau pendant J2 : aucune URI d’endpoint réelle et aucun appel SofaScore réel ne sont autorisés.
- maintien du connecteur et du profil réel bloqués au démarrage de J3 ; la revue des conditions officielles impose une décision humaine préalable avant tout appel.
- dans l’unité de politique hors ligne, résultat `TRANSPORT_ELIGIBLE` explicitement sans effet :
  aucun client HTTP, aucune URI réelle et aucun appel fournisseur n’y étaient introduits.
- rejet avant persistance des payloads dépassant 5 Mio ou contenant des motifs de secret, cookie, jeton ou clé privée ; aucun octet brut n’est journalisé ou versionné.
- maintien de `payload_jsonb` à `NULL` pour les snapshots bruts afin d’éviter toute normalisation implicite avant parsing.
- maintien de `ConnectorGate`, du profil réel et de l’adaptateur fournisseur général en état
  bloqué ; le transport simulé reste strictement limité à `127.0.0.1` et le chemin J3 dédié ne peut
  être activé que par sa politique distincte.
- formulaires opérateur protégés par un jeton aléatoire lié à la session et à usage unique, avec cookie `HttpOnly` et `SameSite=Strict`.
- suppression de la phrase de confirmation après usage, expiration ou arrêt global ; aucun contenu saisi n’est journalisé.
- exceptions de transport réduites à des codes sûrs, sans URI, payload ou diagnostic interne ; aucune donnée partielle n’est persistée après un échec de lecture.
- maintien de tous les incidents en circuit `OPEN` jusqu’à un arrêt et une nouvelle activation explicites, y compris après `Retry-After`.
- arrêt de la qualification Windows avant transport tant que le point de décision réel reste incomplet ;
  aucune phrase active, jeton, URI fournisseur ou donnée brute n’est versionné comme preuve.
- activation du chemin fournisseur subordonnée à quatre propriétés concordantes, à une origine
  exacte, à l’unique famille `SCHEDULED_EVENTS`, au stockage brut actif et à l’absence de mode live ;
- validation de domaine de la date et des cinq pages, sans chemin libre, redirection, proxy,
  pagination découverte, retry ni donnée de session ;
- maintien de tous les tests Maven hors ligne et absence d’appel SofaScore pendant l’implémentation.
- qualification Windows arrêtée avant le bouton final : aucune page fournisseur demandée, aucun
  snapshot réel persisté et aucune preuve de sortie fournisseur ajoutée au dépôt.
- relecture locale des snapshots 1 et 2 sans transport ni écriture, avec confirmation que leur
  statut historique `SCHEMA_INCOMPATIBLE` reste inchangé.
- arrêt normal uniquement sur `hasNextPage=false`, rupture sûre si ce champ n’est pas un booléen,
  arrêt au premier incident, délai inter-pages minimal de trois secondes et absence de retry,
  polling ou planification dans le parcours répétable.

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

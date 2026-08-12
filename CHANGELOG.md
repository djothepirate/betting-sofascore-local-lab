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

### Documentation

- démarrage du Work Order `WO-SS-20260808-002` sur la branche `feat/j2-scheduled-events-fixtures` depuis le tag `j0-j1-v0.1.1` ;
- passage du jalon J2 au statut `IN_DEVELOPMENT` avec `SCHEDULED_EVENTS` comme première famille de fixtures hors ligne.
- contrat d’architecture `scheduled-events-v1` détaillant les champs obligatoires, facultatifs et inconnus.

### Modifié

- renommage du package Java de base de `com.geoffrey.betting.sofascorelocal` vers `com.bettingproject.sofascorelocal`.
- tableau de bord enrichi avec le compteur des neuf fixtures hors ligne et leur état de validation synthétique.

### Sécurité

- maintien du verrouillage réseau pendant J2 : aucune URI d’endpoint réelle et aucun appel SofaScore réel ne sont autorisés.

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

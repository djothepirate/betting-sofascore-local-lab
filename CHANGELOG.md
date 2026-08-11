# Changelog

Les évolutions notables du SofaScore Local Lab sont consignées dans ce fichier.

## [Non publié]

### Documentation

- démarrage du Work Order `WO-SS-20260808-002` sur la branche `feat/j2-scheduled-events-fixtures` depuis le tag `j0-j1-v0.1.1` ;
- passage du jalon J2 au statut `IN_DEVELOPMENT` avec `SCHEDULED_EVENTS` comme première famille de fixtures hors ligne.

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

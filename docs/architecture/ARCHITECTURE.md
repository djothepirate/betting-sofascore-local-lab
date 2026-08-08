# Architecture J0/J1 — SofaScore Local Lab

## 1. Positionnement

Le laboratoire est un composant expérimental séparé du Betting Project. Il ne partage ni base, ni déploiement, ni responsabilité opérationnelle avec le cœur du projet global.

```text
Betting Project principal          SofaScore Local Lab
-------------------------          -------------------
VPS permanent                      Windows local uniquement
Production indépendante            Prototype non approuvé production
Modèle canonique multi-source      Modèle local de benchmark
Aucun appel SofaScore VPS          Aucun appel implémenté au J1
Fonctionne poste éteint             Disponible seulement poste allumé
```

La relation future autorisée est un export JSON normalisé et versionné. Aucun import n’est inclus au J1.

## 2. Topologie locale

```text
┌───────────────────────────────────────────────────────────┐
│ Windows 11                                                │
│                                                           │
│  Eclipse / JDK 25                                         │
│       │                                                   │
│       ▼                                                   │
│  Spring Boot 4.1.0                                        │
│  127.0.0.1:8087                                           │
│  ├─ Spring MVC + Thymeleaf                                │
│  ├─ DashboardService                                      │
│  ├─ ConnectorGate = LOCKED_OFFLINE_J1                     │
│  ├─ Catalogue logique sans URI                            │
│  ├─ Flyway / JDBC / JPA                                   │
│  └─ Actuator                                              │
│       │                                                   │
│       ▼                                                   │
│  Docker Desktop                                           │
│  └─ PostgreSQL 18.4                                       │
│     port hôte 127.0.0.1:5432                              │
│                                                           │
│  exports/                                                 │
└───────────────────────────────────────────────────────────┘

SofaScore : aucune connexion au J1
VPS       : aucune connexion au J1
```

## 3. Couches

| Couche | Responsabilité J1 |
|---|---|
| `config` | propriétés typées, garde de liaison locale, initialisation du dossier d’export, en-têtes de sécurité |
| `domain.provider` | types logiques, définition de catalogue et mode du connecteur |
| `application` | verrou logiciel de tout appel externe |
| `adapter.sofascore` | catalogue logique, sans URI ni client actif |
| `adapter.web` | tableau de bord et vues locales |
| `resources/db/migration` | schéma brut, manifeste d’export et état persistant |
| `fixtures` | réservée au J2 |

Le starter `RestClient` est présent pour figer le choix technologique, mais aucun `RestClient` n’est encore construit pour SofaScore.

## 4. Défense en profondeur J1

```text
Configuration par défaut        sofascore.enabled=false
          │
          ▼
Catalogue logique               callable=false, URI absente
          │
          ▼
ConnectorGate                   exception systématique
          │
          ▼
Profil Maven réel               alwaysFail
          │
          ▼
Interface                       boutons réseau désactivés
```

La suppression d’une seule barrière ne permet donc pas un appel accidentel.

## 5. Données

### 5.1 `provider_snapshot`

Preuve brute future de transport : fournisseur, endpoint logique, clé de requête, horodatages, statut HTTP, type de contenu, latence, JSONB, SHA-256, parseur, état de schéma et erreur.

La contrainte d’unicité partielle empêche de conserver plusieurs fois le même hash pour la même requête, tout en autorisant les erreurs ou réponses sans payload.

### 5.2 `export_manifest`

Prépare J7 : version de schéma, chemin local, hash du contenu, validation, snapshots sources et avertissements.

### 5.3 `connector_control`

État opérateur persistant initialisé à :

```text
network_enabled = false
circuit_state   = LOCKED
last_reason     = J1 bootstrap: network calls are not implemented
```

Cette table ne remplace pas le verrou logiciel. Au J1, modifier la ligne n’autorise aucun appel.

## 6. Catalogue logique

| Type | Cache initial | Déclenchement J1 | Appelable J1 |
|---|---:|---|---|
| `SCHEDULED_EVENTS` | 10 min | manuel prévu | non |
| `EVENT_DETAILS` | 15 min | manuel prévu | non |
| `EVENT_STATISTICS` | 30 min | manuel prévu | non |
| `EVENT_INCIDENTS` | 15 min | manuel prévu | non |
| `EVENT_LINEUPS` | 15 min | manuel prévu | non |
| `TOURNAMENT_STANDINGS` | 6 h | manuel prévu | non |
| `TEAM_RECENT_EVENTS` | 1 h | manuel prévu | non |

Les modèles d’URI, paramètres autorisés, tailles maximales et parseurs seront introduits progressivement après fixtures hors ligne.

## 7. Tests

### Standard

`mvnw clean verify` exécute uniquement des tests unitaires et MVC hors ligne :

- adresses de boucle locale ;
- propriétés de prudence ;
- catalogue complet mais non appelable ;
- verrou du connecteur ;
- rendu du contrôleur.

### Intégration

`mvnw -Pintegration-tests verify` démarre PostgreSQL avec Testcontainers et vérifie la migration V1. Aucun appel SofaScore n’est exécuté.

### Réel

Le profil `sofascore-live-test` est bloqué avec `alwaysFail`. Son activation effective appartient au jalon J3.

## 8. Décisions différées

- modèles d’URI réels ;
- `RestClient` et timeouts réseau ;
- stockage de headers autorisés ;
- canonicalisation et hash de payload ;
- parseurs et DTO externes ;
- circuit breaker réel et persistance des incidents ;
- écran de confirmation d’appel ;
- export canonique ;
- push HTTPS vers le Betting Project ;
- tout polling ou usage live.

Chaque décision doit être introduite par un Work Order, avec critères d’acceptation et tests de non-régression des garde-fous.

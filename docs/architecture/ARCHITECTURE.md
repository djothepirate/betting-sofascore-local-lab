# Architecture J0 à J3 — SofaScore Local Lab

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
│  ├─ ConnectorGate = LOCKED_OFFLINE_J3_POLICY              │
│  ├─ Politique J3 et circuit en mémoire                    │
│  ├─ Transport HTTP simulé, loopback strict uniquement     │
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

SofaScore : aucune connexion ; transport J3 limité à la simulation loopback
VPS       : aucune connexion
```

## 3. Couches

| Couche | Responsabilité actuelle |
|---|---|
| `config` | propriétés typées, garde de liaison locale, initialisation du dossier d’export, en-têtes de sécurité |
| `domain.provider` | types logiques, définition de catalogue et mode du connecteur |
| `application` | verrou logiciel de tout appel externe |
| `adapter.sofascore` | catalogue logique sans URI fournisseur, adaptateur fournisseur bloqué et transport loopback simulé non enregistré |
| `adapter.persistence` | conservation JDBC des preuves brutes et déduplication atomique |
| `adapter.web` | tableau de bord et vues locales |
| `resources/db/migration` | schéma brut V1/V2, manifeste d’export et état persistant |
| `fixtures` | corpus synthétique et parsing hors ligne J2 |

Le starter `RestClient` est présent pour figer le choix technologique, mais aucun `RestClient` n’est encore construit pour SofaScore.

## 4. Défense en profondeur J3 hors ligne

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
Politique J3                    simulation loopback seulement
          │
          ▼
Profil Maven réel               alwaysFail
          │
          ▼
Interface                       boutons réseau désactivés
```

La suppression d’une seule barrière ne permet donc pas un appel accidentel. Le transport simulé
ajoute en outre une validation finale de l’origine littérale `127.0.0.1` et d’une route fixe.

## 5. Données

### 5.1 `provider_snapshot`

Preuve brute prête pour un futur transport : fournisseur, provenance, endpoint logique, clé de requête, horodatages, statut HTTP, type de contenu, latence, octets exacts, taille, SHA-256, parseur, état de schéma et erreur.

La migration V2 append-only conserve les octets dans `payload_raw` (`bytea`) avant parsing, impose une taille maximale de 5 Mio et fixe la provenance à `DIRECT_LOCAL_ENDPOINT`. `payload_jsonb` reste `NULL` dans cette unité : aucune représentation normalisée n’est fabriquée à partir du brut.

La contrainte d’unicité partielle empêche de conserver plusieurs fois le même hash pour une même combinaison fournisseur, endpoint logique et clé de requête. L’adaptateur retourne soit `INSERTED`, soit `DEDUPLICATED` avec l’identifiant stable du snapshot.

### 5.2 `export_manifest`

Prépare J7 : version de schéma, chemin local, hash du contenu, validation, snapshots sources et avertissements.

### 5.3 `connector_control`

État opérateur persistant initialisé à :

```text
network_enabled = false
circuit_state   = LOCKED
last_reason     = J1 bootstrap: network calls are not implemented
```

Cette table ne remplace pas le verrou logiciel. Pendant l’unité de politique J3, modifier cette ligne
n’autorise toujours aucun appel.

## 6. Catalogue logique

| Type | Cache initial | Déclenchement prévu | Appelable actuellement |
|---|---:|---|---|
| `SCHEDULED_EVENTS` | 10 min | manuel prévu | non |
| `EVENT_DETAILS` | 15 min | manuel prévu | non |
| `EVENT_STATISTICS` | 30 min | manuel prévu | non |
| `EVENT_INCIDENTS` | 15 min | manuel prévu | non |
| `EVENT_LINEUPS` | 15 min | manuel prévu | non |
| `TOURNAMENT_STANDINGS` | 6 h | manuel prévu | non |
| `TEAM_RECENT_EVENTS` | 1 h | manuel prévu | non |

Les paramètres canoniques, limites de payload et parseurs hors ligne sont introduits progressivement. Les modèles d’URI réels restent absents jusqu’au point de décision J3.

## 7. Tests

### Standard

`mvnw clean verify` exécute uniquement des tests unitaires et MVC hors ligne :

- adresses de boucle locale ;
- propriétés de prudence ;
- catalogue complet mais non appelable ;
- verrou du connecteur ;
- validation des métadonnées et des preuves brutes ;
- orchestration et transport HTTP simulé sur boucle locale ;
- rendu du contrôleur.

### Intégration

`mvnw -Pintegration-tests verify` démarre PostgreSQL avec Testcontainers et vérifie les migrations V1/V2, la fidélité binaire, les contraintes et la déduplication. Aucun appel SofaScore n’est exécuté.

### Réel

Le profil `sofascore-live-test` reste bloqué avec `alwaysFail`. L’ouverture du Work Order J3 ne
suffit pas à l’activer : le point de décision humain et les autres unités techniques restent requis.

## 8. Décisions différées

- modèles d’URI réels ;
- `RestClient` fournisseur et timeouts d’un endpoint réel ;
- stockage de headers autorisés ;
- normalisation persistée après parsing ;
- parseurs et DTO externes au-delà de `scheduled-events-v1` ;
- persistance du circuit et des incidents ;
- écran de confirmation d’appel ;
- export canonique ;
- push HTTPS vers le Betting Project ;
- tout polling ou usage live.

Chaque décision doit être introduite par un Work Order, avec critères d’acceptation et tests de non-régression des garde-fous.

Le modèle de décision et le circuit J3 désormais actés sont détaillés dans
`docs/architecture/J3-OFFLINE-NETWORK-POLICY.md`. La conservation des preuves est détaillée dans
`docs/architecture/J3-RAW-SNAPSHOT-PERSISTENCE.md`. Le transport loopback est détaillé dans
`docs/architecture/J3-GUARDED-SCHEDULED-EVENTS-TRANSPORT.md`. Aucun de ces composants ne contient
une URI SofaScore ou n’active l’adaptateur fournisseur.

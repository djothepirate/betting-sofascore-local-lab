# Architecture J0 à J4 — SofaScore Local Lab

## 1. Positionnement

Le laboratoire est un composant expérimental séparé du Betting Project. Il ne partage ni base, ni déploiement, ni responsabilité opérationnelle avec le cœur du projet global.

```text
Betting Project principal          SofaScore Local Lab
-------------------------          -------------------
VPS permanent                      Windows local uniquement
Production indépendante            Prototype non approuvé production
Modèle canonique multi-source      Modèle local de benchmark
Aucun appel SofaScore VPS          J3 manuel + J4 phase 1, opt-in et bornés
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
│  ├─ Transport HTTP simulé, loopback strict                │
│  ├─ Collecte J3 manuelle 1..N, opt-in et plafonnée        │
│  ├─ Identités et observations J4 append-only              │
│  ├─ Recherche locale + détail J4 fixture ou snapshot      │
│  ├─ Circuit J4 phase 1 : deux IDs compilés, verrou terminal│
│  ├─ Catalogue logique fermé par défaut                    │
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

SofaScore : aucune connexion par défaut ; J4 phase 1 exige un opt-in temporaire exact
VPS       : aucune connexion
```

## 3. Couches

| Couche | Responsabilité actuelle |
|---|---|
| `config` | propriétés typées, garde de liaison locale, initialisation du dossier d’export, en-têtes de sécurité |
| `domain.provider` | types logiques, catalogue et requêtes fournisseur J3/J4 fermées par valeur |
| `domain.event` / `domain.eventdetails` | identité canonique, observation versionnée, provenance et détail immuables J4 |
| `application` | verrou général, politiques J3/J4, orchestration manuelle et services de normalisation/recherche |
| `adapter.sofascore` | catalogue fermé, transports spéciaux bornés, parseurs `scheduled-events-v1` et `event-details-v1/v2` |
| `adapter.persistence` | preuves brutes, cache, identités et observations normalisées, avec déduplication atomique |
| `adapter.web` | tableau de bord, recherche locale, contrôle J4 phase 1 et détail en lecture seule |
| `resources/db/migration` | schémas V1 à V6, migrations append-only et triggers d’immuabilité |
| `fixtures` | corpus synthétiques hors ligne J2 et J4 |

Le connecteur général demeure bloqué. Un `RestClient` distinct est construit uniquement pour le
chemin manuel J3 borné ; il ne reçoit qu’une requête de domaine validée et ne peut viser que
l’origine `https://www.sofascore.com`, une date ISO explicite et les pages `1` à `25`.

J4 ajoute un second `RestClient` spécial qui ne reçoit que `EventDetailsProviderRequest`. Ce type
refuse toute origine autre que `https://www.sofascore.com` et tout identifiant différent de
`16386245` ou `16421052` avant la construction de l’URI. Les opt-ins J3 et J4 sont mutuellement
exclusifs. Ni l’un ni l’autre ne déverrouille `ConnectorGate` ou le catalogue général.

## 4. Défense en profondeur J3

```text
Configuration par défaut        enabled=false + j3-qualification-enabled=false
          │
          ▼
Configuration opt-in            origine exacte + SCHEDULED_EVENTS seul
          │
          ▼
Contrôle opérateur              arrêt global + circuit + confirmation unique
          │
          ▼
Requête de domaine              date ISO + pages 1..25 + chemin fermé
          │
          ▼
Transport J3                    sans proxy, redirection, cookie ni jeton
          │
          ▼
Orchestrateur                   page 1, hasNextPage, délai >= 3 s, plafond 25
```

La suppression d’une seule barrière ne permet donc pas un appel accidentel. Le bouton réel reste
absent tant que les quatre propriétés d’activation ne concordent pas. Même après confirmation, une
action Web distincte est nécessaire. Le connecteur général, `ConnectorGate` et le profil Maven réel
restent bloqués ; le transport loopback simulé conserve par ailleurs sa frontière propre.

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

### 5.4 `canonical_event` et `canonical_event_observation`

`canonical_event` associe une unique identité UUID locale à la paire
`(provider, provider_event_id)`. L’UUID est calculé dans un espace de noms versionné et ne dépend
jamais d’un libellé, d’un horaire, d’une compétition ou d’un statut.

`canonical_event_observation` conserve chaque version métier avec sa source exacte, son SHA-256,
son parseur et son heure de réception. Les observations sont dédupliquées uniquement lorsqu’elles
sont strictement identiques ; un trigger interdit `UPDATE` et `DELETE`. Pour une recherche, la
dernière observation de chaque identité est sélectionnée avant d’appliquer les bornes de date afin
qu’un événement déplacé ne réapparaisse pas à son ancien horaire.

### 5.5 `event_detail_observation`

Le détail J4 est append-only et rattaché par clé étrangère à l’identité canonique. V5 conserve la
provenance historique `SYNTHETIC_FIXTURE`. V6 ajoute `PROVIDER_SNAPSHOT` avec une clé étrangère vers
le brut inséré avant parsing. Les deux formes sont exclusives par contrainte SQL. La table de détail
ne stocke que les champs normalisés et la preuve de provenance ; les octets restent dans la fixture
classpath ou `provider_snapshot`.

Une base V5 peut déjà contenir des détails alors que les colonnes `source_kind` et
`source_reference` n’existent pas encore. V6 désactive donc, dans sa transaction et pour cette
seule table, le trigger `event_detail_observation_append_only`, renseigne ces deux métadonnées à
partir de `source_fixture_id`, puis réactive immédiatement le trigger avant de rendre les colonnes
obligatoires. Les équipes, horaires, statuts, hashes, parseurs et horodatages historiques ne sont
pas modifiés.

## 6. Catalogue logique

| Type | Cache initial | Déclenchement prévu | Appelable actuellement |
|---|---:|---|---|
| `SCHEDULED_EVENTS` | 10 min | manuel | uniquement par séquence J3 opt-in |
| `EVENT_DETAILS` | 15 min | fixture locale ou campagne J4 phase 1 | voie spéciale : IDs `16386245`, `16421052` |
| `EVENT_STATISTICS` | 30 min | manuel prévu | non |
| `EVENT_INCIDENTS` | 15 min | manuel prévu | non |
| `EVENT_LINEUPS` | 15 min | manuel prévu | non |
| `TOURNAMENT_STANDINGS` | 6 h | manuel prévu | non |
| `TEAM_RECENT_EVENTS` | 1 h | manuel prévu | non |

Toutes les définitions du catalogue restent `callable=false`. Les voies J3/J4 ne constituent pas
des modèles d’URI généraux. Le chemin J3 :
seuls une date ISO, l’origine et le chemin `scheduled-tournaments` sont acceptés. La pagination
commence obligatoirement à 1, continue uniquement sur `hasNextPage=true` et s’arrête avant la page
26 même si le fournisseur annonce encore une suite.

## 7. Tests

### Standard

`mvnw clean verify` exécute uniquement des tests unitaires et MVC hors ligne :

- adresses de boucle locale ;
- propriétés de prudence ;
- catalogue complet mais non appelable ;
- verrou du connecteur ;
- validation des métadonnées et des preuves brutes ;
- orchestration et transport HTTP simulé sur boucle locale ;
- validation du transport fournisseur avec `MockRestServiceServer`, sans connexion réseau ;
- ordre dynamique depuis la page 1, terminaison par `hasNextPage=false`, plafond 25, délai minimal,
  persistance avant parsing et arrêt au premier incident ;
- rendu du contrôleur.
- identité canonique déterministe, versions et provenance J4 ;
- parseurs `event-details-v1/v2`, refus des ruptures de schéma et rattachement strict ;
- recherche par date/zone, rendu des résultats et détail local ;
- allowlist J4 de deux IDs, cache avant transport, brut avant parsing, délai de trois secondes et
  arrêt sans retry au premier incident ;
- sélection exclusive de la sous-étape 2, ID lié à une confirmation, un transport simulé sans
  cache par cycle et répétition manuelle avec délai minimal, sans polling ni retry.

### Intégration

`mvnw -Pintegration-tests verify` démarre PostgreSQL avec Testcontainers et vérifie les migrations
V1 à V6, la fidélité binaire, les contraintes, la déduplication et l’immuabilité des observations
J4. Deux chemins Flyway sont couverts : installation vide V1 → V6 et upgrade V5 préremplie → V6
avec conservation des lignes et réactivation du trigger. Aucun appel SofaScore n’est exécuté.

### Réel

Le profil `sofascore-live-test` reste bloqué avec `alwaysFail` et n’est pas utilisé par les chemins
J3/J4. La sous-étape 1 J4 a été qualifiée humainement après correction de navigation. La
sous-étape 2 reste un geste humain séparé dans l’interface locale après activation explicite de sa
configuration et n'a encore exécuté aucun ID réel. Aucune suite Maven ne réalise ce geste.

## 8. Décisions différées

- stockage de headers autorisés ;
- parseurs et DTO externes au-delà de `scheduled-events-v1` et `event-details-v1/v2` ;
- persistance du circuit et des incidents ;
- ajout d’autres endpoints, sports ou origines au-delà du chemin J3 qualifié ;
- export canonique ;
- push HTTPS vers le Betting Project ;
- tout polling ou rafraîchissement automatique ; les rappels `EVENT_DETAILS` autorisés restent
  manuels, unitaires et nouvellement confirmés.

Chaque décision doit être introduite par un Work Order, avec critères d’acceptation et tests de non-régression des garde-fous.

Le modèle de décision et le circuit J3 désormais actés sont détaillés dans
`docs/architecture/J3-OFFLINE-NETWORK-POLICY.md`. La conservation des preuves est détaillée dans
`docs/architecture/J3-RAW-SNAPSHOT-PERSISTENCE.md`. Le transport loopback est détaillé dans
`docs/architecture/J3-GUARDED-SCHEDULED-EVENTS-TRANSPORT.md`. Le chemin fournisseur strictement
borné est détaillé dans `docs/architecture/J3-FIVE-PAGE-PROVIDER-QUALIFICATION.md`. Il ne déverrouille
ni le connecteur général, ni le profil live, ni les autres familles du catalogue. Le parcours actif
répétable est détaillé dans `docs/architecture/J3-DYNAMIC-MANUAL-PAGINATION.md`.

Le modèle J4, ses frontières de normalisation, son identité stable et son détail hors ligne sont
détaillés dans `docs/architecture/J4-CANONICAL-EVENTS-AND-LOCAL-DETAIL.md`. Le contrat JSON minimal
du détail synthétique est défini dans `docs/architecture/EVENT-DETAILS-V1.md`. La voie réelle bornée
et sa politique d’arrêt sont détaillées dans
`docs/architecture/J4-GUARDED-REAL-EVENT-DETAILS-PHASE1.md`. Le paramètre graphique et les
rafraîchissements manuels confirmés sont détaillés dans
`docs/architecture/J4-GUARDED-REAL-EVENT-DETAILS-PHASE2.md`.

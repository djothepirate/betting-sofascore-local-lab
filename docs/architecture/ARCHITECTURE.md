# Architecture J0 à J7 — SofaScore Local Lab

## 1. Positionnement

Le laboratoire est un composant expérimental séparé du Betting Project. Il ne partage ni base, ni déploiement, ni responsabilité opérationnelle avec le cœur du projet global.

```text
Betting Project principal          SofaScore Local Lab
-------------------------          -------------------
VPS permanent                      Windows local uniquement
Production indépendante            Prototype non approuvé production
Modèle canonique multi-source      Modèle local de benchmark
Aucun appel SofaScore VPS          J3/J4/J5 manuels, opt-in et bornés
Fonctionne poste éteint             Disponible seulement poste allumé
```

J7 matérialise la relation autorisée sous la forme d'un fichier JSON normalisé, versionné et
validé humainement. Aucun import, transfert automatique ou composant du Betting Project principal
n'est ajouté.

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
│  ├─ Données de rencontre J5 + complétude, hors ligne      │
│  ├─ Persistance J5 normalisée append-only                 │
│  ├─ Historique et différences sémantiques J6              │
│  ├─ Rétention J6 manuelle, auditée et hors interface      │
│  ├─ Export canonique J7 local + décision humaine          │
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
| `domain.event` / `domain.eventdetails` / `domain.eventdata` | identité canonique, détail J4, familles J5 et complétude immuables |
| `domain.history` / `domain.retention` | versions, changements, traces de snapshots et plans de rétention J6 |
| `domain.export` | statut, manifeste, décision, composant et preuves de fichier J7 |
| `application` | politiques réseau, orchestration manuelle, normalisation, historique, diff, rétention et export J7 |
| `adapter.sofascore` | catalogue fermé, transports spéciaux bornés et parseurs hors ligne J2/J4/J5 |
| `adapter.persistence` | preuves brutes, occurrences, observations normalisées, historique, rétention et manifestes J7 |
| `adapter.file` | publication J7 create-new par lien physique atomique, bornée à la racine locale |
| `adapter.web` | tableau de bord, recherche, contrôles et vues J4/J5/J6/J7 locales |
| `resources/db/migration` | schémas V1 à V23, migrations append-only et triggers d’immuabilité |
| `fixtures` | corpus synthétiques hors ligne J2, J4, J5 et J6 |

Le connecteur général demeure bloqué. Un `RestClient` distinct est construit uniquement pour le
chemin manuel J3 borné ; il ne reçoit qu’une requête de domaine validée et ne peut viser que
l’origine `https://www.sofascore.com`, une date ISO explicite et les pages `1` à `25`.

J4 ajoute un second `RestClient` spécial qui ne reçoit que `EventDetailsProviderRequest`. Ce type
refuse toute origine autre que `https://www.sofascore.com` et tout identifiant hors de la portée
bornée par son mode avant la construction de l’URI. J4 phase 1 reste exclusif ; J4 phase 2 peut
partager une instance explicitement armée avec J3 et J5. Aucun de ces opt-ins ne déverrouille
`ConnectorGate` ou le catalogue général.

J5 possède une voie fournisseur distincte, désactivée par défaut et bornée aux trois familles d'une
identité canonique déjà présente. Elle reste manuelle, séquentielle, sans retry et peut être réunie
avec J3 et J4 phase 2 dans une session explicitement armée. L'ensemble autorisé doit être exactement
l'union des endpoints correspondant aux opt-ins. `ManualProviderRequestCoordinator`, injecté dans
les services réels J3/J4/J5, garantit une seule section HTTP active et un délai minimal commun entre
deux départs, même depuis plusieurs onglets. Maven intercepte ces transports et n'effectue aucun
appel réel.

J6 n'ajoute aucun `RestClient`. Il lit les observations existantes et son import de démonstration
utilise seulement des fixtures synthétiques. La rétention est une commande non Web ponctuelle qui
force toutes les voies réseau à l'arrêt et exige le verrou persistant `LOCKED`.

J7 n'ajoute lui non plus aucun `RestClient`. Il sélectionne seulement les observations courantes
déjà persistées, sans lire le brut, et écrit un fichier local après validation du contrat, des
hashes et du contenu sensible. Une décision humaine J7 n'ouvre aucun verrou J3/J4/J5.

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

V21 ajoute une occurrence append-only pour chaque tentative future, afin qu'une déduplication ne
masque plus l'appel observé. V22 autorise uniquement la suppression auditée des octets bruts après
sauvegarde restaurée ; la ligne, sa taille, son SHA-256 et sa provenance restent conservés.

### 5.2 `export_manifest`

La structure générique V1 conserve version de schéma, chemin local, hash courant, statut,
snapshots sources et avertissements. V23 l'étend, uniquement pour les lignes distinguées par
`J7_CANONICAL_EVENT`, avec UUID d'export et d'événement, identifiant stable du schéma, génération,
`dataSha256`, `sourceSetSha256`, hash candidat initial, taille courante, cinq sources structurées,
décision et intention terminale write-ahead (statut, heure, motif, chemin, hash et taille).

Les anciennes lignes génériques gardent leurs colonnes J7 à `NULL`. Pour J7, les contraintes et le
trigger imposent un candidat initial, un seul candidat par événement/schéma/version, l'unicité des
données déjà validées, une transition terminale identique à son intention write-ahead,
l'immuabilité des preuves et l'interdiction de suppression. Les triggers d'observation partagent
en outre un verrou consultatif par événement avec la relecture de fraîcheur J7, empêchant une
nouvelle version de s'intercaler avant la décision.

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

### 5.6 Données de rencontre et complétude J5

`j5_event_data_observation` rattache chaque lot de statistiques, d'incidents ou de compositions à
l'identité canonique J4. Il conserve la source, le hash brut, le parseur, l'heure, le statut et le
score de complétude, les signaux présents/attendus, les chemins manquants et le hash normalisé.

Les tables enfants `j5_event_metric`, `j5_event_incident`, `j5_event_lineup_side` et
`j5_event_lineup_player` contiennent uniquement les champs métier normalisés et leur ordre. Des
clés étrangères composites empêchent de mélanger les familles. V7 applique des triggers
append-only aux cinq tables et déduplique les lots identiques sans recopier les octets sources.

### 5.7 Occurrences et audit de rétention J6

`provider_snapshot_occurrence` conserve `BASELINE`, `INSERTED` ou `DEDUPLICATED` sans recopier les
octets. `j6_raw_payload_purge_audit` conserve le plan, les métadonnées antérieures et les hashes de
la sauvegarde qualifiée. Les deux tables sont append-only. Un trigger V22 interdit toute suppression
de snapshot et toute mise à jour autre qu'une classification initiale ou une purge auditée dans la
même transaction.

### 5.8 Fichiers et enveloppe J7

Le schéma classpath Draft 2020-12
`urn:betting-project:sofascore-local-lab:j7:canonical-event-export:v1` ferme l'enveloppe à
`manifest` et `data`. Le manifeste contient cinq sources dans l'ordre état, détail, statistiques,
incidents et compositions. Le modèle distingue `PRESENT`, `UNAVAILABLE`, `MISSING` et
`EMPTY_VALID`; les observations partielles restent accompagnées de leur complétude. Les
identifiants numériques positifs sont bornés à `Long.MAX_VALUE`. Le garde impose UTF-8 strict et
combine scanner des octets/textes et refus récursif des clés JSON sensibles.

`dataSha256` couvre uniquement `data`; `sourceSetSha256` couvre les cinq emplacements, absences
comprises ; le SHA-256 du fichier entier reste en base pour éviter toute autoréférence. Les
fichiers sont UTF-8/LF, bornés à 5 Mio et écrits sous `sofascore.export-directory` avec un nom
serveur. Le temporaire est synchronisé, puis son inode est publié sans remplacement par création
atomique d'un lien physique sur le même système de fichiers ; le nom temporaire est ensuite retiré.
`ATOMIC_MOVE` n'est pas utilisé. Chemin utilisateur, sortie de racine, lien symbolique, écrasement
et fichier non régulier sont refusés.

Avant de publier un fichier terminal, l'orchestrateur persiste une intention write-ahead contenant
le statut, l'heure, le motif éventuel, le chemin, le SHA-256 et la taille attendus. Une reprise doit
reproduire exactement cette preuve et la transition PostgreSQL terminale doit lui être identique.

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
  cache par cycle et répétition manuelle avec délai minimal, sans polling ni retry ;
- parseurs J5 stricts, complétude `COMPLETE`/`PARTIAL`/`EMPTY_VALID`, corpus synthétique et ruptures
  de schéma sans donnée partielle ;
- import J5 transactionnel et idempotent, rattachement à J4, requête des dernières familles et rendu
  MVC local protégé par jeton à usage unique.
- occurrences J6, chronologie des cinq flux, diff sémantique, appariement prudent des incidents,
  score, classifications tardives, corpus idempotent et absence de payload dans le rendu ;
- aperçu, hash et confirmation de rétention, refus des preuves invalides et commande non Web.
- enveloppes J7 candidates et terminales, schéma et formats, déterminisme, mapping des cinq flux,
  complétude, avertissements, contenus sensibles, dérive des sources et invariance des données ;
- fichiers J7 publiés atomiquement en create-new et bornés, chemins/liens/altérations refusés,
  intentions/reprises terminales authentifiées, jetons et confirmations, aperçu échappé, en-têtes
  sans cache et téléchargement validé seul.

### Intégration

`mvnw -Pintegration-tests verify` démarre PostgreSQL avec Testcontainers et vérifie les migrations
V1 à V23, les upgrades historiques, la fidélité binaire, les contraintes, la déduplication et
l'immuabilité. J6 ajoute les occurrences prospectives, les exclusions de rétention, la purge des
seuls octets dans une base éphémère, l'audit et la conservation de la provenance. Aucun appel
SofaScore n'est exécuté. J7 ajoute l'upgrade V22→V23 prérempli, ses contraintes de cycle et la
relecture exacte du manifeste et de ses preuves.

### Réel

Le profil `sofascore-live-test` reste bloqué avec `alwaysFail`. Les voies J3, J4 et J5 ont été
qualifiées humainement dans leurs périmètres bornés, puis reverrouillées. Elles restent désactivées
par défaut et aucune suite Maven ne réalise un geste fournisseur. J6 ne nécessite aucun nouveau
geste réel : ses deux validations humaines portent uniquement sur l'interface locale et la
sauvegarde/restauration chiffrée. J7 ne nécessite aucune nouvelle collecte : sa recette utilise le
corpus synthétique et l'événement fournisseur `16691018` déjà persisté. Cette recette reste
obligatoire avant de déclarer J7 validé.

## 8. Décisions différées

- stockage de headers autorisés ;
- parseurs fournisseur J5 issus d'une observation réelle réussie ;
- persistance du circuit et des incidents ;
- ajout d’autres endpoints, sports ou origines au-delà du chemin J3 qualifié ;
- export de l'historique complet, lots par date ou multi-événements ;
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

Les trois contrats synthétiques J5, les signaux de complétude, la migration V7 et l'interface locale
sont détaillés dans `docs/architecture/J5-OFFLINE-EVENT-DATA-AND-COMPLETENESS.md`.
L'historique et la rétention gardée sont détaillés dans
`docs/architecture/J6-HISTORY-AND-GUARDED-RETENTION.md`. Le contrat, les empreintes, le cycle de
décision et le stockage J7 sont détaillés dans
`docs/architecture/J7-CANONICAL-EVENT-EXPORT.md`.

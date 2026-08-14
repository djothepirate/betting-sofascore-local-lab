# J3 — Qualification complémentaire du cache et de l’inspection locale des snapshots

## 1. Identification

| Élément | Valeur |
|---|---|
| Projet | SofaScore Local Lab |
| Date locale de qualification | 2026-08-14 |
| Environnement | Windows local |
| Jalon | J3 — cache dynamique et inspection locale |
| Work Order | `WO-SS-20260812-003` |
| Branche | `feat/j3-dynamic-cache-policy` |
| Commit cache qualifié | `7f4c84b60a6a283ac9ba8b1da4f564c76d0322e9` |
| Commit inspection qualifié | `ac4cb9ff64df0bd3346b0934fea8ed77f8b3f6b4` |
| Intitulé de l’unité | `test: qualify J3 cache and local snapshot inspection` |
| Appel fournisseur pendant l’unité | `NO` |
| Mutation de snapshot par l’inspection | `NO` |
| Mutation de checkpoint de cache par l’inspection | `NO` |
| Résultat technique | `PASS` |
| Résultat humain complémentaire | `PASS` |
| Résultat global | `PASS` |

## 2. Objet du rapport

Ce rapport qualifie ensemble les deux premières unités postérieures à la pagination dynamique J3 :

1. la priorité du cache PostgreSQL frais avant chaque transport du parcours manuel dynamique ;
2. l’inspection JSON explicite et locale d’un snapshot brut déjà conservé.

La qualification complémentaire vérifie la frontière entre ces deux fonctions. Lire et formater un
snapshot ne doit ni créer une nouvelle observation, ni modifier sa classification historique, ni
rafraîchir ou invalider son checkpoint de cache. Inversement, l’existence d’une vue locale ne doit
jamais rendre éligible au cache un snapshot historiquement incompatible.

Cette unité n’a déclenché aucune collecte fournisseur. Les preuves techniques utilisent des
fixtures synthétiques, des doubles de test et PostgreSQL/Testcontainers. La preuve humaine porte
uniquement sur des snapshots déjà présents dans la base locale.

## 3. Gouvernance et limites maintenues

```text
EXPERIMENTAL
LOCAL_ONLY
NOT_PRODUCTION_APPROVED
NO_CRITICAL_DEPENDENCY
```

Les invariants suivants restent applicables :

- application liée uniquement à `127.0.0.1` ;
- cache borné aux réponses `SCHEDULED_EVENTS` HTTP `2xx`, classées `PARSED`, fraîches et produites
  par `scheduled-events-v1` ;
- TTL du cache fixé à dix minutes, avec la borne exacte considérée expirée ;
- relecture et reparsing des octets avant l’utilisation d’un cache hit ;
- inspection limitée à une sélection locale explicite et protégée par un jeton Web à usage unique ;
- vérification de la taille et du SHA-256 avant formatage ;
- rejet du contenu sensible, du JSON ambigu et du contenu JSON suivi de données résiduelles ;
- rendu HTML par texte échappé et réponse `no-store` ;
- aucun téléchargement du payload brut ;
- aucun cookie, jeton, compte ou donnée de session fournisseur ;
- aucun retry, polling ou tâche planifiée ajouté par cette unité.

## 4. Périmètre de la qualification technique

### 4.1 Cache du parcours dynamique

La suite standard couvre les comportements suivants :

| Contrôle | Résultat attendu | Résultat |
|---|---|---|
| Cache entièrement frais | zéro transport | `PASS` |
| Cache entièrement frais | zéro attente inter-page | `PASS` |
| Cache entièrement frais | zéro écriture de snapshot | `PASS` |
| Cache entièrement frais | zéro réécriture du checkpoint | `PASS` |
| Parcours mixte fournisseur/cache/fournisseur | délai calculé entre les deux départs fournisseur | `PASS` |
| Preuve minimisée v4 | sources `CACHE` et `PROVIDER` distinctes | `PASS` |
| Payload de cache | reparsé avant lecture de `hasNextPage` | `PASS` |

La suite PostgreSQL confirme en complément :

- la sélection par clé exacte `SCHEDULED_EVENTS|date=<date>|page=<page>` ;
- le rejet à dix minutes révolues ;
- le rejet d’une autre version de parseur ;
- le maintien des octets bruts dans `provider_snapshot` ;
- le rafraîchissement séparé de `provider_response_cache` après une observation dédupliquée.

### 4.2 Inspection JSON locale

La suite standard couvre les comportements suivants :

| Contrôle | Résultat attendu | Résultat |
|---|---|---|
| Catalogue | métadonnées uniquement, maximum 50 | `PASS` |
| Sélection | identifiant local positif et exact | `PASS` |
| Intégrité | taille et SHA-256 obligatoirement concordants | `PASS` |
| Contenu sensible | affichage refusé | `PASS` |
| JSON dupliqué ou suivi de contenu | affichage refusé | `PASS` |
| Rendu HTML | contenu JSON échappé | `PASS` |
| Réponse d’inspection | `Cache-Control: no-store` et indexation refusée | `PASS` |
| Téléchargement brut | absent | `PASS` |

### 4.3 Qualification transversale cache/inspection

Deux scénarios PostgreSQL complémentaires ont été ajoutés à `FlywayMigrationIT`.

#### Scénario A — snapshot `PARSED` et cache frais

1. persister un snapshot synthétique `PARSED` ;
2. créer son checkpoint de cache avec `scheduled-events-v1` ;
3. mémoriser le nombre de snapshots, le nombre de checkpoints et `cached_at` ;
4. inspecter explicitement le JSON par l’identifiant local ;
5. relire le cache dans sa fenêtre de fraîcheur ;
6. comparer l’état PostgreSQL avant et après.

Résultat : le même snapshot reste éligible, ses octets restent identiques, `cached_at` reste
identique et aucun enregistrement n’est créé, modifié ou supprimé par l’inspection.

#### Scénario B — classification historique `SCHEMA_INCOMPATIBLE`

1. persister un snapshot synthétique avec le statut historique `SCHEMA_INCOMPATIBLE` ;
2. inspecter explicitement son JSON ;
3. rechercher un candidat de cache sur la même clé ;
4. comparer la classification et les compteurs PostgreSQL avant et après.

Résultat : le JSON reste lisible localement, la classification historique reste inchangée et aucun
candidat de cache n’est produit. L’inspection ne constitue donc ni une requalification implicite,
ni une promotion vers `PARSED`.

## 5. Résultats de validation automatisée

La validation consolidée a été exécutée hors ligne, sans `.env` et sans appel fournisseur :

```text
PREFLIGHT_RESULT=PASS
JAVA_TARGET=25
SOURCE_GUARDRAIL_SCAN=PASS
STANDARD_TESTS=159
STANDARD_FAILURES=0
STANDARD_ERRORS=0
STANDARD_SKIPPED=0
SPRING_BOOT_JAR=BUILT
INTEGRATION_TESTS=11
INTEGRATION_FAILURES=0
INTEGRATION_ERRORS=0
INTEGRATION_SKIPPED=0
FLYWAY_LATEST_VERSION=3
FRESH_CACHE_STILL_ELIGIBLE_AFTER_INSPECTION=PASS
CACHE_TIMESTAMP_UNCHANGED_AFTER_INSPECTION=PASS
SNAPSHOT_COUNT_UNCHANGED_AFTER_INSPECTION=PASS
CACHE_CHECKPOINT_COUNT_UNCHANGED_AFTER_INSPECTION=PASS
HISTORICAL_INCOMPATIBILITY_PRESERVED=PASS
INCOMPATIBLE_SNAPSHOT_CACHE_ELIGIBILITY=NO
SERVER_ADDRESS=127.0.0.1
SOFASCORE_NETWORK_CALLS_EXECUTED=NO
VERIFY_RESULT=PASS
```

## 6. Qualification humaine complémentaire

### 6.1 Sources de preuve

Six captures locales ont été examinées. Elles ne sont pas ajoutées au dépôt, car elles affichent
des payloads bruts fournisseur. Le rapport ne reproduit ni payload, ni URI, ni en-tête, ni donnée de
session.

Les captures montrent :

1. le catalogue de 15 snapshots existants, ordonnés du plus récent au plus ancien, avec uniquement
   leur identifiant, clé locale, heure de réception, statut HTTP, taille et classification ;
2. l’inspection du snapshot `#3`, classé `PARSED`, correspondant à la page 3 du `2026-08-13` ;
3. l’inspection du snapshot historique `#1`, classé `SCHEMA_INCOMPATIBLE` ;
4. la fin du JSON formaté du snapshot `#1`, où `hasNextPage=true` reste lisible ;
5. l’inspection du snapshot `#15`, classé `PARSED`, correspondant à la page 10 du `2026-08-14` ;
6. la fin du JSON formaté du snapshot `#15`, où `hasNextPage=false` reste lisible.

### 6.2 Matrice humaine

| Contrôle humain | Observation | Résultat |
|---|---|---|
| Catalogue local | 15 lignes de métadonnées, aucun payload chargé dans la liste | `PASS` |
| Action explicite | bouton `Inspecter le JSON` par ligne | `PASS` |
| Snapshot `PARSED` intermédiaire | page 3 affichée avec `hasNextPage=true` | `PASS` |
| Snapshot historique incompatible | JSON affiché et badge `SCHEMA_INCOMPATIBLE` conservé | `PASS` |
| Snapshot terminal | page 10 affichée avec `hasNextPage=false` | `PASS` |
| Métadonnées | identifiant, clé, heure, HTTP, taille, SHA-256 et parseur visibles | `PASS` |
| Unicode | textes multilingues rendus lisiblement dans la vue formatée | `PASS` |
| Lecture seule | indication explicite d’absence de réécriture | `PASS` |
| Politique HTTP | statut visuel `NO_STORE / READ_ONLY` | `PASS` |
| Téléchargement brut | aucune action de téléchargement proposée | `PASS` |
| Retour opérateur | retour vers le catalogue local disponible | `PASS` |

La qualification humaine confirme le comportement visible de l’inspection. Elle ne prétend pas
avoir déclenché un nouveau parcours fournisseur ni observé un cache hit réseau : ces propriétés
sont qualifiées par les tests techniques hors ligne décrits aux sections 4 et 5.

## 7. Contrôle de minimisation

Le présent rapport conserve uniquement :

- les identifiants locaux utiles aux scénarios humains ;
- les dates, numéros de page et classifications nécessaires à la traçabilité ;
- les résultats booléens `hasNextPage` utiles à la qualification du rendu ;
- les résultats agrégés des suites Maven.

Il ne conserve aucun payload JSON, aucune valeur métier issue des compétitions, aucune URI appelée,
aucun en-tête HTTP, aucun cookie, aucun jeton, aucun compte et aucune donnée de session.

## 8. Décision de qualification

```text
J3_CACHE_TECHNICAL_QUALIFICATION=PASS
J3_CACHE_ZERO_TRANSPORT_ON_HIT=PASS
J3_CACHE_ZERO_MUTATION_ON_HIT=PASS
J3_LOCAL_RAW_JSON_INSPECTION_TECHNICAL_QUALIFICATION=PASS
J3_LOCAL_RAW_JSON_INSPECTION_HUMAN_QUALIFICATION=PASS
J3_CACHE_AND_INSPECTION_CROSS_BOUNDARY=PASS
J3_INSPECTION_CACHE_MUTATION=NO
J3_INSPECTION_SNAPSHOT_MUTATION=NO
J3_HISTORICAL_CLASSIFICATION_MUTATION=NO
J3_RAW_DOWNLOAD_AVAILABLE=NO
J3_COMPLEMENTARY_PROVIDER_CALLS=0
J3_CACHE_AND_LOCAL_SNAPSHOT_INSPECTION_QUALIFICATION=PASS
```

La qualification est concluante pour le périmètre local J3. Elle ne constitue ni une autorisation
de production, ni une autorisation de polling, de live, de planification ou d’intégration au
Betting Project principal.

# Architecture J0 à J8 — SofaScore Local Lab

**Architecture WO-060 — 14 septembre 2026 :** [ADR-SS-007 v0.2](../../ADR-SS-007-j3-automation-durable-catalog-and-live-pause.md)
adopte les ordres J3 durables, le résultat par date et la pause live. V55 publie atomiquement les
pages, le catalogue et le dernier succès ; V56 sérialise paramètres, occurrences et admission ;
V57 conserve les transitions de pause sous la génération du garde live. Le terminal J8 partage
la transaction du succès J3. `J3RuntimeService` s’active uniquement après disponibilité de
l’application web locale, reconstruit les preuves historiques, puis utilise un consommateur
sériel. Les commandes de maintenance et les tests standards ne démarrent pas ce moteur.
Le protocole worker 10 et `live-v11` réservent un contexte J3 temporaire dans le worker live,
avec un seul droit d’émettre ; l’ordonnanceur live abandonne les groupes incomplets et reprend
par J4 dans sa fenêtre initiale. Les architectures J3 en mémoire et les politiques live
antérieures décrites plus bas restent leurs références historiques.

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
│  ├─ Catalogue J3 exact + découverte tournoi → rencontres  │
│  ├─ Identités et observations J4 append-only              │
│  ├─ Recherche locale + détail J4 fixture ou snapshot      │
│  ├─ Circuit J4 phase 1 : deux IDs compilés, verrou terminal│
│  ├─ Données de rencontre J5 + complétude, hors ligne      │
│  ├─ Persistance J5 normalisée append-only                 │
│  ├─ Lot J5 hors ligne 1..25, contrôle et transaction 3N   │
│  ├─ Historique et différences sémantiques J6              │
│  ├─ Rétention J6 manuelle, auditée et hors interface      │
│  ├─ Export canonique J7 local + décision humaine          │
│  ├─ Ledger et benchmark J8 locaux, immuables et bornés    │
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

SofaScore : aucune connexion par défaut ; chaque voie manuelle exige son opt-in temporaire exact
VPS       : aucune connexion
```

## 3. Couches

| Couche | Responsabilité actuelle |
|---|---|
| `config` | propriétés typées, garde de liaison locale, initialisation du dossier d’export, en-têtes de sécurité |
| `domain.provider` | types logiques, catalogue et requêtes fournisseur J3/J4 fermées par valeur, dont la découverte tournoi |
| `domain.event` / `domain.eventdetails` / `domain.eventdata` | identité canonique, détail J4, familles J5 et complétude immuables |
| `domain.history` / `domain.retention` | versions, changements, traces de snapshots et plans de rétention J6 |
| `domain.export` | statut, manifeste, décision, composant et preuves de fichier J7 |
| `domain.benchmark` | campagnes, unités, tentatives et résultats J8 à vocabulaire fermé |
| `application` | politiques réseau, orchestration manuelle et lot J5 hors ligne, normalisation, historique, diff, rétention, export J7 et agrégation J8 |
| `adapter.sofascore` | catalogue fermé, adaptateurs Playwright bornés J3/J4/J5 et parseurs hors ligne J2/J4/J5/découverte tournoi |
| `adapter.persistence` | preuves brutes, occurrences, observations normalisées, historique, rétention, manifestes J7, preuves J8 et ledger live V33 |
| `adapter.file` | publication J7 create-new par lien physique atomique, bornée à la racine locale |
| `adapter.web` | tableau de bord, recherche, contrôle de lot et vues J4/J5/J6/J7/J8 locales |
| `resources/db/migration` | schémas V1 à V54, migrations append-only et triggers d’immuabilité |
| `fixtures` | corpus synthétiques hors ligne J2, J4, J5 et J6 |

Le connecteur général demeure bloqué. Le chemin manuel J3 borné délègue ses deux familles
`SCHEDULED_EVENTS` et `TOURNAMENT_SCHEDULED_EVENTS` à un worker Playwright JVM enfant commun. Le
worker ne reçoit qu’une requête de domaine validée et ne peut viser que l’origine
`https://www.sofascore.com`, les deux routes allowlistées, une date ISO explicite et, pour la
pagination, les pages `1` à `35`. Le profil Maven `provider-playwright-runtime` ajoute le runtime à
la compilation sans le démarrer ; `SOFASCORE_PLAYWRIGHT_ENABLED=false` maintient l’inertie par
défaut. Il n’existe aucun fallback `RestClient` ou FlareSolverr.
Après la confirmation, une action alternative accepte localement un lot complet de corps JSON
J3 1 à N. Elle partage le contrôle et la garde de concurrence, mais ne construit aucune requête,
ne lit ni n'écrit le cache fournisseur et persiste avec `MANUAL_LOCAL_JSON_IMPORT`.

La découverte tournoi → rencontres prolonge ce chemin sans ouvrir le catalogue général. Elle
reconstruit ses options depuis les snapshots exacts de la preuve J3 `COMPLETED` la plus récente du
processus courant, puis utilise un transport dédié vers
`/api/v1/unique-tournament/{uniqueTournament.id}/scheduled-events/{date}`. Le navigateur poste
seulement `tournament.id`; date et identifiant numérique du tournoi unique sont résolus et
revalidés côté serveur. Le libellé de la liste associe exactement `tournament.name`, le séparateur
` - ` et la portée descriptive `tournament.category.name`; la catégorie ne participe jamais à
l'identité ni à l'URI. Avant publication, une occurrence deja parsee dont le nom de phase ou de
tournoi unique contient un caractere ISO de controle est exclue de la projection sans sanitiser ni
reecrire les snapshots bruts. Les valeurs blanches, les categories invalides et les conflits
restent des incompatibilites strictes en amont. Les options sures sont ensuite filtrees par
intersection entre les cles de `timezoneEventCount` et les offsets reellement applicables a la
date J3 dans `Europe/Paris` ; ce calcul couvre l'heure d'ete, l'heure d'hiver et les deux offsets
d'une journee de bascule sans dependre du fuseau systeme. Apres redemarrage, aucune liste n'est
recomposee implicitement depuis des snapshots historiques.

Une lease du coordinateur couvre toute campagne J3 Playwright et conserve le délai partagé de
trois secondes entre départs fournisseur. Chaque campagne possède un worker, un Chromium headless
et un `BrowserContext` non persistant neufs ; le protocole IPC commun v5 transporte les commandes
allowlistées J3, J4 et J5. Aucun profil, cookie, `storageState`, HAR, trace, vidéo, capture ou
téléchargement n’est conservé. L’IPC est authentifié et lié uniquement à
`127.0.0.1`. L’arrêt ferme la campagne et nettoie l’arbre exact attribué par PID et instant de
création, jamais par nom de processus. Le statut, le type de contenu et les octets sont capturés
avant parsing ; HTTP `404` est persisté puis classé `ENDPOINT_UNAVAILABLE`, sans retry ni page
suivante. Le contrat complet et ses exclusions sont décrits dans
[`J3-PLAYWRIGHT-PROVIDER-TRANSPORT.md`](J3-PLAYWRIGHT-PROVIDER-TRANSPORT.md).

J4 réutilise le même runtime par l'adaptateur Playwright qui ne reçoit que
`EventDetailsProviderRequest`. Ce type refuse toute origine autre que
`https://www.sofascore.com` et tout identifiant hors de la portée bornée par son mode avant la
construction de la commande worker. J4 phase 1 reste exclusif ; J4 phase 2 peut partager une
instance explicitement armée avec J3 et J5. Aucun de ces opt-ins ne déverrouille `ConnectorGate`
ou le catalogue général. Il n'existe aucun fallback `RestClient` ou FlareSolverr.

WO-015 remplace également le transport direct J5 par l'adaptateur Playwright commun. La voie reste
désactivée par défaut et bornée aux trois familles d'une identité canonique déjà présente. Une
campagne acquiert la lease exclusive avant le démarrage paresseux, puis garde un seul worker, un
seul Chromium et un seul `BrowserContext` non persistant pour les commandes strictement ordonnées
`EVENT_STATISTICS`, `EVENT_INCIDENTS`, `EVENT_LINEUPS`. Le cache fournisseur J5 est
`NOT_APPLICABLE` : il n'est ni lu ni écrit. Un `404` raw-first est conservé comme
`ENDPOINT_UNAVAILABLE` et laisse passer la commande suivante ; tout autre incident ferme la
campagne sans retry, fallback ou contexte de remplacement. L'arrêt J5 signale la campagne exacte,
interdit toute commande suivante et nettoie l'arbre attribué par PID et instant de création.
La lease n'est liberee qu'apres confirmation de ce nettoyage. Une erreur de fermeture survenue
avant toute mutation reste retentable sur la meme campagne ; apres le debut d'une mutation, un
nettoyage non confirme devient terminal et la lease est retenue jusqu'au redemarrage du processus
afin qu'aucune campagne J3 ou J4 ne puisse chevaucher un worker potentiellement orphelin.

J5 peut être réuni avec J3 et J4 phase 2 dans une instance explicitement armée. L'ensemble
autorisé doit être exactement l'union des endpoints correspondant aux opt-ins.
`ManualProviderRequestCoordinator` et la lease Playwright garantissent une seule campagne active
et le délai minimal commun entre deux départs, même depuis plusieurs onglets. Les suites Maven
standards n'activent ni Playwright ni Chromium et n'effectuent aucun appel réel.

J5 possède également un lot multi-match strictement hors ligne sous `/j5-import-batches`. Son
contrôle mémoire, sa politique et son importeur sont séparés de `J5RealControlService` et du
coordinateur. L'état de `sofascore.enabled` est orthogonal à son éligibilité : les opt-ins,
l'origine et l'allowlist peuvent rester armés pour les gestes fournisseur manuels. Il prévalide
exactement trois preuves par événement avant un claim unique. Chaque preuve est un fichier JSON ou
une déclaration 404 fermée portant un marqueur opérateur local. Les `3N` traitements sont committés
dans une seule transaction ou intégralement annulés ; aucun transport ni cache fournisseur n'est
présent dans son graphe de dépendances.

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
Configuration opt-in            origine exacte + union exacte des endpoints actifs
          │
          ▼
Contrôle opérateur              arrêt global + circuit + confirmation unique
          │
          ▼
Choix exclusif                  pagination directe OU lot JSON local complet
          │
          ├── import            1..35, 5 Mio/page, 25 Mio/lot, scan sensible
          │                     parser + hasNextPage validés avant claim, zéro réseau/cache
          ▼ direct
Requête de domaine              date ISO + pages 1..35 + chemin fermé
          │
          ▼
Transport J3                    sans proxy, redirection, cookie ni jeton
          │
          ▼
Orchestrateur                   page 1, hasNextPage, délai >= 3 s, plafond 35
```

La suppression d’une seule barrière ne permet donc pas un appel accidentel. Le bouton réel reste
absent tant que les quatre propriétés d’activation ne concordent pas. Même après confirmation, une
action Web distincte est nécessaire. Le connecteur général, `ConnectorGate` et le profil Maven réel
restent bloqués ; le transport loopback simulé conserve par ailleurs sa frontière propre.
La voie locale n'est pas un retry d'une voie directe terminale : après un `HTTP_FORBIDDEN`, une
nouvelle préparation et une nouvelle confirmation restent obligatoires.

V53 associe cette borne active aux campagnes J8 : les nouvelles campagnes
`J3_SCHEDULED_EVENTS` déclarent 35 unités, tandis que les campagnes historiques déclarées à 25
restent lisibles et gardent leur plafond persistant. Cette compatibilité de lecture ne transforme
aucune campagne historique en campagne à 35 unités.

Pour la découverte tournoi, les opt-ins général, J3 et découverte doivent être vrais ensemble et
l'allowlist doit être exactement
`SCHEDULED_EVENTS,TOURNAMENT_SCHEDULED_EVENTS`. Cette cohérence de configuration ne vaut jamais
autorisation fournisseur : la sélection et la préparation restent sans réseau, puis une décision
humaine distincte précède l'action confirmée.

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

### 5.9 Cache de découverte tournoi V24

V24 ne crée aucune table métier et ne modifie aucun historique J7. Elle élargit uniquement la
contrainte de `provider_response_cache.logical_endpoint` pour admettre
`TOURNAMENT_SCHEDULED_EVENTS` aux côtés de `SCHEDULED_EVENTS` et `EVENT_DETAILS`.

Le checkpoint de découverte est indexé par la date et le `uniqueTournament.id` numérique, avec un
TTL de dix minutes. Il référence toujours un snapshot brut `PARSED` dont endpoint, clé, parseur,
taille et SHA-256 sont revérifiés. Un cache hit ne déclenche ni transport ni nouvelle persistance
brute ; il n'évite jamais le parsing, la projection `Europe/Paris` ou le contrôle du nombre.

### 5.10 Provenance d'import JSON local V25

V25 ne crée aucune table métier. Elle autorise `MANUAL_LOCAL_JSON_IMPORT` dans la contrainte
fermée de `provider_snapshot.acquisition_mode` et ajoute le mode d'acquisition à l'index unique des
snapshots bruts. Une réponse directe et un import du même corps restent ainsi deux preuves
distinctes, tandis que deux imports identiques sous la même clé sont dédupliqués.

L'import est une action locale explicite après confirmation : exactement une preuve fichier ou
déclaration 404 par famille, fichier vide ou supérieur à 5 Mio refusé, scanner de contenu sensible
avant consommation du claim, aucun HAR/en-tête/cookie, aucun transport et aucune lecture/écriture du cache fournisseur. Les caches restent limités à
`DIRECT_LOCAL_ENDPOINT`; l'inspection locale et la rétention J6 admettent les deux modes.

WO-010 réutilise cette provenance pour un plan de 1 à 25 événements et les occurrences J6 pour
chaque snapshot committé. Aucune migration propre au plan multi-match n'est nécessaire : le plan,
le contrôle et le résultat du lot restent uniquement en mémoire, tandis que snapshots et
observations conservent leur modèle append-only existant. La migration transverse V26 étend
ultérieurement la version du parseur d'incidents J5 sans créer de stockage propre au lot.

### 5.11 Campagnes live bornées WO-058 — V33

Cette section décrit le socle V33. Les extensions V39 ajoutent les profils groupés, groupes
et échéances par famille ; V40 lie les nouveaux budgets et la cadence au manifeste v5,
sans convertir les politiques antérieures. La cible v5 de vingt rencontres à 100 secondes
dispose de sa qualification synthétique historique. Le premier lot de résilience V42–V44
est qualifié fonctionnellement hors fournisseur : nouvelles préparations v6 plafonnées à sept,
protection commune J3/J4/J5 après chaque fin d'échange (2 s, 25/60 s, 1 000/h), suspension
durable 403/429 et backoff 404 J5. Le [profil temporel v6](../validation/WO058-LIVE-V6-CAPACITY-20260909.md)
qualifie ensuite sept rencontres en 35 minutes de boucle locale synthétique avec ce wrapper
et PostgreSQL V44, dont 30 minutes établies : 494 échanges et aucun cycle manqué.
Les enveloppes portent sur le corpus établi de 64 Kio, avec les pics initiaux de 5 Mio
mesurés séparément. Le plafond opérateur de six et le timeout de 30 s restent indépendants ;
la vérification finale du complément réussit et aucune acceptation fournisseur
n'est déduite de cette mesure.
Voir l'[architecture live courante](LIVE-J4-J5-CAMPAIGNS.md) et le
[rapport du lot](../validation/WO-058-provider-resilience-qualification-20260909.md).

V33 ajoute huit tables sans modifier V1 à V32 ni reconstituer de campagnes à partir des anciennes
collectes. Le périmètre fonctionnel est défini par l'ADR-SS-005 ; le garde est partagé par les
campagnes manuelles et live.

| Table | Preuve ou état conservé |
|---|---|
| `provider_campaign_guard` | propriétaire unique, instance, PID et début du processus, génération, `FREE`/`OWNED`/`CLEANUP_REQUIRED` |
| `live_campaign` | manifeste immuable, bornes, profil qualifié, état d'exécution, compteurs et révision |
| `live_event` | sélection immuable et provenance exacte, état individuel, échéance, cycles manqués et complétude finale |
| `live_call` | tentative réservée, événement, cycle, famille, échéance, finalité et génération propriétaire |
| `live_call_dispatch` | autorisation de départ unique, sans prétendre mesurer le passage sur le réseau |
| `live_call_receipt` | snapshot brut et occurrence exacte de la réponse, dates, taille et hash |
| `live_call_result` | résultat de traitement, parseur, projection métier versionnée et références normalisées |
| `live_transition` | transitions append-only dans l'ordre de révision de la campagne |

Les quatre tables V42 conservent `provider_resilience_state`, les réservations et fins
d'échange `provider_departure_reservation`/`provider_departure_completion`, et l'historique
`provider_resilience_event`. La suspension est distincte de l'exclusion des processus ;
son réarmement manuel ne crée aucun accès fournisseur et n'efface pas les budgets.
V43 ajoute `live_attempt_transport_diagnostic` et `live_campaign_diagnostic`, sans payload :
un statut connu aux en-têtes ne vaut pas réponse complète ni snapshot sauvegardé.
V44 borne les manifestes v6, sans réécrire les campagnes et observations historiques.

`LiveCampaignStore` expose la préparation, le lancement idempotent, la réservation, la réception,
la publication et la lecture cohérente. `ProviderCampaignGuardStore` porte l'exclusion durable.
Le profil d'admission du manifeste conserve les enveloppes de requête et de traitement à la
nanoseconde, le SHA-256 de qualification et la capacité admise. Il ne peut être modifié après
préparation. Les instants du manifeste sont normalisés à la microseconde avant leur hash et leur
stockage PostgreSQL.

Les transactions restent courtes : garde, campagne et événement sont verrouillés pour réserver
une tentative et débiter ses budgets ; le réseau se déroule ensuite sans transaction SQL. La
réception committe brut, occurrence et lien live avant parsing. Le parseur travaille hors
transaction, puis une publication atomique conserve les observations normalisées, leurs
références, le résultat et la transition. L'échec de publication laisse la réception brute
committée, sans enfant normalisé partiel. Un arrêt interdit de nouveaux départs sans empêcher la
conservation d'une réponse déjà engagée.

Les lectures `REPEATABLE_READ` reconstruisent la dernière réception, le dernier succès et le
dernier changement de chaque famille depuis les tentatives, avec les références du dernier
succès lisible. La déduplication A→A conserve deux occurrences ; A→B→A peut réutiliser l'ancienne
observation A sans perdre la nouvelle fraîcheur. Un échec ou un 404 ne remplace pas les dernières
données lisibles par une fausse absence. Le résultat live conserve la projection score/phase
versionnée séparément des observations historiques.

Le redémarrage n'autorise aucune reprise automatique et aucun transfert de garde après délai.
Une disparition du propriétaire prouvée peut produire `UNKNOWN`, `INTERRUPTED` et
`CLEANUP_REQUIRED` ; la libération attend une preuve de nettoyage exacte. J6 refuse la sauvegarde
et la rétention lorsque le garde n'est pas libre ou qu'une campagne reste active. La preuve J6
inclut les sept compteurs live, ceux des six tables V42/V43 et l'empreinte complète du ledger,
des groupes/échéances et des diagnostics/protections, sous schéma V44. Une purge qualifiée ne
supprime aucune de leurs lignes. Si une nouvelle réception déduplique vers un brut déjà purgé,
`LIVE_RAW_PREVIOUSLY_PURGED` annule cette réception et impose l'arrêt ; aucune réhydratation n'est
introduite. Le détail figure dans
[`J6-HISTORY-AND-GUARDED-RETENTION.md`](J6-HISTORY-AND-GUARDED-RETENTION.md).

## 6. Catalogue logique

| Type | Cache initial | Déclenchement prévu | Appelable actuellement |
|---|---:|---|---|
| `SCHEDULED_EVENTS` | 10 min | manuel | uniquement par séquence J3 opt-in |
| `TOURNAMENT_SCHEDULED_EVENTS` | 10 min | manuel après une collecte J3 `COMPLETED` | uniquement par séquence de découverte opt-in |
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

Le chemin de découverte est lui aussi fermé : l'identifiant de l'URI est exclusivement
`tournament.uniqueTournament.id`, résolu depuis la liste J3 côté serveur. Le parseur
`tournament-scheduled-v1` contrôle la racine `events`, puis la projection conserve uniquement la
phase `tournament.id` et la journée civile semi-ouverte dans `Europe/Paris`. Les observations
canoniques sont écrites dans une transaction unique ; un conflit d'identité ou un
`COUNT_MISMATCH` en interdit toute publication partielle.

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
- ordre dynamique depuis la page 1, terminaison par `hasNextPage=false`, plafond 35, délai minimal,
  persistance avant parsing et arrêt au premier incident ;
- catalogue tournoi issu des snapshots exacts d'une collecte `COMPLETED`, sélection serveur,
  requête numérique, parser `tournament-scheduled-v1`, cache, projection Paris et atomicité ;
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
- lot J5 hors ligne : sélections 1 et 25, tri et hash déterministes, transitions DST, expiration,
  claim concurrent unique, connecteur maître indifférent, manifeste multipart hostile,
  prévalidation intégrale, dérive canonique et rendu MVC minimisé ;
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
V1 à V54, les upgrades historiques, la fidélité binaire, les contraintes, la déduplication et
l'immuabilité. J6 ajoute les occurrences prospectives, les exclusions de rétention, la purge des
seuls octets dans une base éphémère, l'audit et la conservation de la provenance. Aucun appel
SofaScore n'est exécuté. J7 ajoute l'upgrade V22→V23 prérempli, ses contraintes de cycle et la
relecture exacte du manifeste et de ses preuves. La découverte tournoi ajoute l'installation V24
et l'upgrade V23→V24 pour le cache, puis V25 pour distinguer la provenance d'une réponse directe et
celle d'un corps JSON importé localement. J5 ajoute l'upgrade V25→V26 qui autorise le parseur
`event-incidents-v14` sans réécrire l'historique.
Le lot J5 ajoute le commit atomique des `3N` familles, le réimport avec snapshots et observations
dédupliqués mais occurrences nouvelles, puis le rollback global provoqué sur la dernière famille
du dernier événement. J8 ajoute l'upgrade V26→V27, l'immutabilité des cinq tables du ledger, leurs
contraintes de corrélation, l'unicité tentative/résultat, les occurrences dédupliquées distinctes,
la lecture après purge du payload, l'isolation `REPEATABLE_READ` et le fingerprint identique après
sauvegarde/restauration.
V28 ajoute uniquement `event-incidents-v15` à la contrainte fermée des observations incidents.
V29 ajoute le ledger de livraison J7 distinct : identité immuable, tentatives/résultats append-only
et projection d'état strictement gardée, sans payload ni modification du statut J7.
Elle ne crée aucune table et ne réécrit ni observation, ni snapshot, ni occurrence, ni preuve J8.
V30 remplace uniquement le trigger de résultat J7 : l’ordre local entre début et fin de tentative
reste gardé, tandis que l’instant canonique déclaré par l’horloge indépendante du receiver est
persisté exactement sans le comparer aux horodatages du Local Lab. Elle ne réécrit aucune donnée.
V33 ajoute l'installation neuve et l'upgrade V32 prérempli avec égalité des preuves historiques,
l'immuabilité du manifeste et de son profil, les transactions réception/publication et leurs
rollbacks, A→A→B→A, les références conservées après 404, les réservations concurrentes et les
générations du garde. La qualification PostgreSQL synthétique vérifie aussi la rétention gardée,
le refus d'un brut précédemment purgé et la restauration exacte des huit tables sans réarmement.
V15 assimile propriété d'actions absente et tableau exactement vide uniquement dans une séance
terminale non minutée déjà cohérente ; les types erronés, listes non vides incohérentes et séances
temporellement mixtes restent incompatibles.

### Réel

Le profil `sofascore-live-test` reste bloqué avec `alwaysFail`. Les voies fournisseur historiques
J3, J4 et J5 ont été qualifiées humainement dans leurs périmètres bornés, puis reverrouillées. Cette
qualification ne couvre pas le lot hors ligne WO-010, dont la recette propriétaire reste requise.
Elles restent désactivées par défaut et aucune suite Maven ne réalise un geste fournisseur. J6 ne nécessite aucun nouveau
geste réel : ses deux validations humaines portent uniquement sur l'interface locale et la
sauvegarde/restauration chiffrée. J7 ne nécessite aucune nouvelle collecte : sa recette utilise le
corpus synthétique et l'événement fournisseur `16691018` déjà persisté. Cette recette reste
obligatoire avant de déclarer J7 validé.

La découverte tournoi → rencontres est `VALIDATED` depuis la décision propriétaire du 2026-08-21.
Son implémentation et ses tests automatisés ont exécuté zéro appel fournisseur ; cette clôture ne
transforme aucun bloc de configuration ou mode opératoire documentaire en autorisation d'une
nouvelle recette réelle.

## 8. Décisions différées

- stockage de headers autorisés ;
- parseurs fournisseur J5 issus d'une observation réelle réussie ;
- persistance du circuit et des incidents ;
- ajout d’autres endpoints, sports ou origines au-delà du chemin J3 qualifié ;
- export de l'historique complet, lots par date ou multi-événements ;
- push HTTPS vers le Betting Project ;
- les collectes automatiques au-delà des campagnes locales explicitement lancées et bornées de
  l'ADR-SS-005/WO-058 ; les voies manuelles historiques conservent leurs confirmations unitaires.

Chaque décision doit être introduite par un Work Order, avec critères d’acceptation et tests de non-régression des garde-fous.

Le modèle de décision et le circuit J3 désormais actés sont détaillés dans
`docs/architecture/J3-OFFLINE-NETWORK-POLICY.md`. La conservation des preuves est détaillée dans
`docs/architecture/J3-RAW-SNAPSHOT-PERSISTENCE.md`. Le transport loopback est détaillé dans
`docs/architecture/J3-GUARDED-SCHEDULED-EVENTS-TRANSPORT.md`. Le chemin fournisseur strictement
borné est détaillé dans `docs/architecture/J3-FIVE-PAGE-PROVIDER-QUALIFICATION.md`. Il ne déverrouille
ni le connecteur général, ni le profil live, ni les autres familles du catalogue. Le parcours actif
répétable est détaillé dans `docs/architecture/J3-DYNAMIC-MANUAL-PAGINATION.md`.

Le catalogue exact issu de cette collecte, la requête numérique par tournoi unique, le cache V24,
la provenance d'import V25, la projection `Europe/Paris`, l'atomicité canonique et le lien direct
vers J5 sans J4 sont détaillés
dans `docs/architecture/J3-J5-TOURNAMENT-EVENT-DISCOVERY.md`.

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

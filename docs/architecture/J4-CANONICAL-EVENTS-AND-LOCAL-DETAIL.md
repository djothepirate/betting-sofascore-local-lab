# J4 — Événements canoniques et détail local

## 1. Frontière du jalon

J4 transforme des sources locales en une vue métier consultable. Son parcours historique demeure
hors ligne. L’amendement du Work Order du 2026-08-15 ajoute une voie réseau spéciale, indépendante
du transport J3 et limitée à deux événements pour la qualification humaine sous-étape 1.

```text
SOURCES_ACCEPTED=SCHEDULED_EVENTS_LOCAL_SNAPSHOT,SYNTHETIC_FIXTURE,EVENT_DETAILS_PROVIDER_SNAPSHOT
EVENT_DETAILS_PHASE_1_EVENT_IDS=16386245,16421052
EVENT_DETAILS_PROVIDER_TRANSPORT=DISABLED_BY_DEFAULT
GENERAL_CATALOG_CALLABLE=FALSE
AUTOMATIC_FALLBACK=NO
POLLING_OR_SCHEDULING=NO
```

Le catalogue conserve toutes ses définitions `callable=false` et sans URI générale. L’absence d’un
détail ou d’un événement compatible reste un résultat local normal : aucun repli réseau n’est
déclenché. Seule l’action humaine de campagne, précédée de l’opt-in exact et de la confirmation,
peut atteindre les deux requêtes compilées dans l’allowlist.

Cette frontière décrit les parcours J4 historiques. L'exception de campagne automatique bornée
de WO-058 est décrite dans l'[architecture live J4/J5](LIVE-J4-J5-CAMPAIGNS.md) ; elle conserve
son lancement opérateur et ses propres budgets. Le contrat J4 courant ajoute ci-dessous une
normalisation V3 locale des scores affichés et de l'attribution, sans nouvel endpoint.

## 2. Flux de normalisation

```text
snapshot local J3            fixtures synthétiques J4        campagne J4 phase 1
SCHEDULED_EVENTS             scheduled + event-details-v1    EVENT_DETAILS, 2 IDs
        │                              │                              │
        ├─ taille + SHA-256            ├─ manifests + SHA-256        ├─ cache préalable
        └─ scheduled-events-v1         └─ égalité providerEventId    ├─ brut persisté
                                                                      └─ event-details-v3
        │                              │                              │
        └──────────────────────┬───────┴──────────────────────────────┘
                             ▼
                   identité canonique stable
                             │
              observations d’événement append-only
                             │
              détail append-only, fixture ou snapshot
                             │
                   recherche et vues locales
```

La normalisation d’un snapshot relit ses métadonnées et ses octets exacts. Le service recalcule le
hash, impose `SCHEDULED_EVENTS`, exige la provenance locale directe et reparse avec la version
courante. Une racine `scheduled` valide mais dépourvue de liste `events` produit zéro observation.
Une rupture de schéma produit zéro écriture et ne reclassifie pas le snapshot historique.

L’import de démonstration parse et valide les deux fixtures avant d’ouvrir la transaction. Si leurs
identifiants fournisseur diffèrent, aucune identité, observation ou ligne de détail n’est créée.

## 3. Identité canonique

L’UUID est déterministe à partir de :

```text
sofascore-local-lab:event:v1|SOFASCORE|<providerEventId>
```

Le calcul utilise `UUID.nameUUIDFromBytes` sur la représentation UTF-8. Le préfixe versionné évite
une collision avec d’autres espaces de noms locaux. Les données mutables ne participent jamais au
calcul : un renommage d’équipe, un changement d’horaire ou un nouveau statut restent des versions
de la même identité.

La contrainte unique PostgreSQL sur `(provider, provider_event_id)` vérifie la correspondance, et
l’adaptateur refuse une ligne dont l’UUID ne correspond pas au calcul de domaine.

## 4. Observations et provenance

Une observation d’événement contient au minimum :

- l’identité canonique et l’identifiant fournisseur ;
- l’horaire, les équipes, la compétition et le statut observés ;
- le type et la référence de source ;
- le SHA-256 des octets sources, le parseur et l’heure de réception ;
- une empreinte du contenu normalisé utilisée pour la déduplication exacte.

Les sources autorisées sont `PROVIDER_SNAPSHOT` et `SYNTHETIC_FIXTURE`. La première référence une
ligne `provider_snapshot`; la seconde référence un identifiant de fixture classpath. Les
contraintes SQL rendent ces deux formes exclusives.

`canonical_event_observation` et `event_detail_observation` refusent `UPDATE` et `DELETE` par
trigger. Une nouvelle version est ajoutée ou une version strictement identique est dédupliquée ;
aucune écriture applicative ne corrige une observation antérieure.

## 5. Recherche par date

La requête reçoit une date civile et une zone IANA explicites. Les bornes sont calculées ainsi :

```text
fromInclusive = date.atStartOfDay(zone).toInstant()
toExclusive   = date.plusDays(1).atStartOfDay(zone).toInstant()
```

La requête SQL choisit d’abord la dernière observation de chaque identité, puis applique
`[fromInclusive, toExclusive[`. Cet ordre est indispensable : lorsqu’un événement est déplacé vers
un autre jour, son ancienne version reste dans l’historique mais ne doit plus apparaître dans les
résultats courants de l’ancienne date.

## 6. Détail local hors ligne ou issu de la campagne bornée

Le contrat `event-details-v1` est décrit séparément dans `EVENT-DETAILS-V1.md` et reste associé aux
fixtures historiques. `event-details-v2` parse l’enveloppe fournisseur `event` depuis un snapshot
brut déjà inséré ; son implémentation et ses observations restent inchangées. La ligne de détail
stocke les champs normalisés de stade, ville, saison et tour avec une provenance exclusive
`SYNTHETIC_FIXTURE` ou `PROVIDER_SNAPSHOT`. Dans les deux cas, le service exige que l’identifiant
du détail corresponde exactement à l’identité canonique ciblée.

### Contrat courant `event-details-v3`

[`EventDetailsV3Parser`](../../src/main/java/com/bettingproject/sofascorelocal/adapter/sofascore/eventdetails/EventDetailsV3Parser.java)
réutilise V2 pour la base de l'enveloppe, puis valide trois champs optionnels indépendants :

| Champ fournisseur | Champ du domaine `EventDetails` | Valeur acceptée |
|---|---|---|
| `event.isAwarded` | `Optional<Boolean> isAwarded` | Booléen explicite, `false` inclus |
| `event.homeScore.display` | `Optional<Integer> homeDisplayScore` | Entier de 0 à 999 inclus |
| `event.awayScore.display` | `Optional<Integer> awayDisplayScore` | Entier de 0 à 999 inclus |

Un champ absent ou `null` reste absent dans le domaine. Un objet `homeScore` ou `awayScore`
vide, sans `display`, ou avec `display:null` n'apporte aucun score. Un `display:0` reste présent
et n'est jamais remplacé par une absence. Un drapeau non booléen, un objet score de type incorrect,
un `display` non entier ou hors bornes produit `SCHEMA_INCOMPATIBLE`, avec le chemin précis et
sans détail partiel. Les champs `current`, `normaltime`, `penalties` ou les incidents ne servent
pas de repli au score affiché. Les avertissements V2 restent conservés, sauf les avertissements
`UNKNOWN_FIELD` des trois nouveaux champs reconnus ; les autres sous-champs des objets score
restent signalés comme inconnus par V3.

Le constructeur historique de `EventDetails` à neuf arguments initialise les trois extensions
à une absence. Le constructeur complet conserve séparément chaque côté du score ; un score
unilatéral valide ne fabrique pas l'autre côté. La présentation `J4EventResult` affiche la paire
`domicile – extérieur` seulement si les deux `display` sont présents, sinon `—`.
Pour un statut technique `finished` et `isAwarded=true`, le libellé devient
« Victoire sur tapis vert ». Ce texte ne change pas le statut `finished`, ne désigne pas un
vainqueur calculé et n'ajoute aucun score. Un drapeau absent ou `false` ne déclenche pas ce libellé.

La recherche et le détail rattachent ce résultat J4 uniquement à une observation de détail dont
la référence source, le hash source et le statut correspondent à l'observation canonique affichée.
Une découverte J3 ne normalise pas ces trois champs : un ancien score J4 n'est pas rattaché à un
nouveau statut J3 provenant d'une autre source. Aucun score ni attribution n'est inféré à partir
du calendrier, de l'heure, du nom des équipes ou du contenu J3.

### Empreinte et replay local

`EventDetailObservation` conserve exactement le format binaire `event-detail-observation-v1`
pour les sources `event-details-v1` et `event-details-v2`. Pour une source `event-details-v3`,
il utilise le préfixe `event-detail-observation-v2`, conserve les champs antérieurs et ajoute
les marqueurs de présence puis les valeurs du drapeau, du score domicile et du score extérieur.
Les variantes absent/`false`, absent/`0` et les deux côtés restent donc distinguées. Le domaine
et SQL refusent de porter une extension présente sous une provenance de parseur antérieure.

Même si les trois extensions sont absentes, la nouvelle version de hash distingue un replay V3
de l'observation V2 du même snapshot. Un replay identique V3 reste dédupliqué. Le hash canonique
`canonical-event-observation-v1` demeure inchangé : la découverte et le statut canonique ne
reçoivent pas ces champs de résultat.

Une observation V1/V2 déjà persistée conserve ses champs et son hash ; la migration ne la
reparse pas. Un replay éventuel exige une action locale explicite à partir des octets exacts
encore disponibles. Il peut produire une nouvelle observation V3 avec son parseur et sa provenance,
sans réécriture de l'ancienne observation ni nouvelle collecte fournisseur. Aucun replay n'est
déclenché par le démarrage, la migration ou la consultation d'une page.

J6 compare les trois nouveaux champs dans le détail sémantique. L'export J7 v1 conserve son
contenu métier antérieur sans ces trois propriétés ; la provenance de l'observation choisie
conserve toutefois sa version de parseur et son hash. Cette extension J4 n'étend pas le schéma J7.

La page `/events/{canonicalId}` rend :

- la version courante de la rencontre ;
- le détail local s’il existe, sinon une absence explicite ;
- la provenance, le parseur, l’heure et le hash ;
- la chronologie complète des observations de l’identité.

Le rendu ne contient aucun payload brut et les réponses portent `no-store` et `noindex`.

## 7. Schéma PostgreSQL

La migration V4 ajoute `canonical_event` et `canonical_event_observation`. La migration V5 ajoute
`event_detail_observation`. V6 étend sa provenance aux snapshots fournisseur et le cache local à
`EVENT_DETAILS`. Aucune migration V1 à V5 n’est modifiée. Pour une base V5 déjà alimentée, V6
suspend uniquement le trigger append-only de `event_detail_observation` pendant le backfill
transactionnel de `source_kind` et `source_reference`, puis le réactive avant les contraintes
finales. Les champs métier historiques restent inchangés.

La migration append-only
[`V38__j4_display_scores_and_award.sql`](../../src/main/resources/db/migration/V38__j4_display_scores_and_award.sql)
ajoute `is_awarded`, `home_display_score` et `away_display_score`, toutes nullables et sans
valeur par défaut. Elle admet `event-details-v3` en conservant V1/V2, borne les scores à 0..999
et réserve les extensions présentes au contrat V3. Elle ne réalise aucun backfill, ne désactive
aucun trigger et ne modifie aucune ancienne migration. Après upgrade V37 → V38, les nouvelles
colonnes des anciennes lignes restent `NULL` ; les autres colonnes et les preuves sources restent
inchangées. `JdbcEventDetailsStore` écrit et relit les trois champs sur un schéma V38.

Les tests Testcontainers vérifient notamment :

- la stabilité UUID et l’unicité fournisseur ;
- la déduplication d’une observation strictement identique ;
- la création d’une nouvelle version après changement métier ;
- la persistance transactionnelle du corpus hors ligne ;
- le refus SQL des mises à jour et suppressions ;
- la normalisation idempotente d’un snapshot compatible ;
- la provenance fournisseur du détail et la clé étrangère vers le brut préalable ;
- le cache `EVENT_DETAILS` sans duplication des octets ;
- l’upgrade V5 préremplie → V6, la conservation de toutes les valeurs existantes et la
  réactivation du trigger append-only ;
- l’absence d’écriture partielle sur erreur d’intégrité ou de schéma.

## 8. Invariants de sécurité

J4 ne modifie pas `ConnectorGate`, le profil `sofascore-live-test` ou le transport J3.
`server.address=127.0.0.1` demeure obligatoire. Le drapeau
`j4-event-details-qualification-enabled` et l'opt-in distinct
`j4-event-details-phase2-enabled` valent `false` par défaut. J4 ne peut coexister avec l’opt-in J3,
et la sélection de la sous-étape 2 bloque la sous-étape 1. Aucun test standard ou d’intégration
n’effectue un appel SofaScore.

Les deux sous-étapes s’arrêtent et se verrouillent au premier incident, au premier `403`, `429`,
`5xx`, timeout, contenu inattendu ou schéma incompatible. Elles n’effectuent aucun retry. La seule
exception est un `404` complet : `WO-SS-20260827-014` autorise la cible fixe suivante en phase 1 et
un résultat `COMPLETED_UNAVAILABLE` sans parsing en phase 2.

La sous-étape 2 migrée reçoit une identité canonique déjà affichée, résout son ID fournisseur côté
serveur et réalise un seul nouvel appel sans cache par confirmation ; la même identité peut être
rappelée uniquement par un nouveau geste humain. Un polling, une planification, un
rafraîchissement automatique ou un export vers le Betting Project exigent une nouvelle
autorisation explicite.

Le transport cible de `EVENT_DETAILS` est le runtime Playwright commun de WO-013. Sa migration J4
est en cours, désactivée par défaut et qualifiable uniquement contre une origine loopback avant
toute décision fournisseur. Le contrat détaillé est `J4-PLAYWRIGHT-EVENT-DETAILS.md`.

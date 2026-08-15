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

## 2. Flux de normalisation

```text
snapshot local J3            fixtures synthétiques J4        campagne J4 phase 1
SCHEDULED_EVENTS             scheduled + event-details-v1    EVENT_DETAILS, 2 IDs
        │                              │                              │
        ├─ taille + SHA-256            ├─ manifests + SHA-256        ├─ cache préalable
        └─ scheduled-events-v1         └─ égalité providerEventId    ├─ brut persisté
                                                                      └─ event-details-v2
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
brut déjà inséré. La ligne de détail stocke les champs normalisés de stade, ville, saison et tour
avec une provenance exclusive `SYNTHETIC_FIXTURE` ou `PROVIDER_SNAPSHOT`. Dans les deux cas, le
service exige que l’identifiant du détail corresponde exactement à l’identité canonique ciblée.

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
`5xx`, timeout, contenu inattendu ou schéma incompatible. Elles n’effectuent aucun retry. La
sous-étape 2 autorisée reçoit un ID borné et réalise un seul nouvel appel sans cache par
confirmation ; le même ID peut être rappelé uniquement par un nouveau geste humain. Un polling,
une planification, un rafraîchissement automatique ou un export vers le Betting Project exigent
une nouvelle autorisation explicite.

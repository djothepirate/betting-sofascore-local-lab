# J4 — Événements canoniques et détail local

## 1. Frontière du jalon

J4 transforme des sources déjà locales en une vue métier consultable. Il n’étend pas le périmètre
réseau J3.

```text
SOURCES_ACCEPTED=SCHEDULED_EVENTS_LOCAL_SNAPSHOT,SYNTHETIC_FIXTURE
EVENT_DETAILS_REAL_URI_CONFIGURED=NO
EVENT_DETAILS_PROVIDER_TRANSPORT=NO
AUTOMATIC_FALLBACK=NO
POLLING_OR_SCHEDULING=NO
```

Le catalogue conserve toutes ses définitions `callable=false` et sans URI. L’absence d’un détail ou
d’un événement compatible est un résultat local normal, jamais un motif d’appel fournisseur.

## 2. Flux de normalisation

```text
snapshot local J3                         fixtures synthétiques J4
SCHEDULED_EVENTS                          scheduled-events + event-details
        │                                             │
        ├─ taille + SHA-256                           ├─ manifests + SHA-256
        ├─ endpoint logique exact                     ├─ parsing intégral des deux sources
        └─ scheduled-events-v1                        └─ égalité providerEventId
        │                                             │
        └────────────────────┬────────────────────────┘
                             ▼
                   identité canonique stable
                             │
              observations d’événement append-only
                             │
              détail synthétique append-only éventuel
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

## 6. Détail hors ligne

Le contrat `event-details-v1` est décrit séparément dans `EVENT-DETAILS-V1.md`. La ligne de détail
stocke les champs normalisés de stade, ville, saison et tour avec sa preuve synthétique. Le service
exige que l’identifiant du détail corresponde exactement à l’identité canonique ciblée.

La page `/events/{canonicalId}` rend :

- la version courante de la rencontre ;
- le détail local s’il existe, sinon une absence explicite ;
- la provenance, le parseur, l’heure et le hash ;
- la chronologie complète des observations de l’identité.

Le rendu ne contient aucun payload brut et les réponses portent `no-store` et `noindex`.

## 7. Schéma PostgreSQL

La migration V4 ajoute `canonical_event` et `canonical_event_observation`. La migration V5 ajoute
`event_detail_observation`. Elles sont append-only : aucune migration V1 à V3 n’est modifiée.

Les tests Testcontainers vérifient notamment :

- la stabilité UUID et l’unicité fournisseur ;
- la déduplication d’une observation strictement identique ;
- la création d’une nouvelle version après changement métier ;
- la persistance transactionnelle du corpus hors ligne ;
- le refus SQL des mises à jour et suppressions ;
- la normalisation idempotente d’un snapshot compatible ;
- l’absence d’écriture partielle sur erreur d’intégrité ou de schéma.

## 8. Invariants de sécurité

J4 ne modifie pas `ConnectorGate`, le profil `sofascore-live-test`, la configuration sûre par défaut
ou le transport J3. `server.address=127.0.0.1` demeure obligatoire. Aucun test standard ou
d’intégration n’effectue un appel SofaScore.

Une future URI `EVENT_DETAILS`, une collecte réelle, un polling, une planification ou un export
vers le Betting Project exigeraient un Work Order et une décision de gouvernance séparés.

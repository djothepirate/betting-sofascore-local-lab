# J5 — Données de rencontre hors ligne et contrôles de complétude

> **Portée historique :** ce document décrit le socle synthétique J5 v1 tel qu'il existait avant
> les Work Orders de qualification réelle et d'import local ultérieurs. Ses statuts initiaux sont
> conservés comme preuve et ne décrivent pas à eux seuls l'état courant de toutes les voies J5.

```text
HISTORICAL_SCOPE=J5_V1_SYNTHETIC_BASELINE
CURRENT_GUARDED_REAL_PATH=SEE_J5-GUARDED-REAL-EVENT-DATA
CURRENT_OFFLINE_MULTI_MATCH_PATH=SEE_J5-OFFLINE-MULTI-MATCH-IMPORT
```

## 1. Frontière du jalon

J5 enrichit l'identité canonique J4 avec trois familles normalisées : statistiques, incidents et
compositions. L'implémentation livrée est volontairement hors ligne. Elle qualifie les contrats
locaux, les règles de complétude, la persistance et l'affichage ; elle ne prétend pas reproduire un
schéma fournisseur actuellement observé.

Les chemins communiqués par l'opérateur définissent la cible fonctionnelle future :

| Famille logique | Forme de chemin cible | État J5 |
|---|---|---|
| `EVENT_STATISTICS` | `/api/v1/event/{eventId}/statistics` | documenté, aucun transport applicatif |
| `EVENT_INCIDENTS` | `/api/v1/event/{eventId}/incidents` | documenté, aucun transport applicatif |
| `EVENT_LINEUPS` | `/api/v1/event/{eventId}/lineups` | documenté, aucun transport applicatif |

Ces chemins ne sont enregistrés ni dans le catalogue, ni dans une propriété exécutable. Toutes les
définitions du catalogue restent `callable=false` et sans URI. Il n'existe aucun repli réseau depuis
la page J5, y compris lorsqu'une famille est absente.

```text
J5_APPLICATION_TRANSPORT=NOT_IMPLEMENTED
J5_PROVIDER_SCHEMA_VALIDATED=NO
J5_FIXTURE_ORIGIN=SYNTHETIC
J5_AUTOMATIC_FALLBACK=NO
J5_POLLING_OR_SCHEDULING=NO
```

La découverte bornée autorisée par le Work Order s'est arrêtée sur le premier GET, qui a reçu
`HTTP 403`. Aucun contournement, retry, changement de client ou appel des cinq autres exemples n'a
été tenté. Les parseurs `*-v1` décrits ici sont donc des contrats synthétiques versionnés, pas une
validation des réponses réelles actuelles.

## 2. Flux hors ligne

```text
fixtures synthétiques J5
manifestes + octets JSON
          │
          ├─ taille, contenu sensible, SHA-256 brut et JSON canonique
          ├─ parseur strict propre à la famille
          ├─ identité eventId exacte
          └─ rapport COMPLETE / PARTIAL / EMPTY_VALID
                                  │
                                  ▼
                     identité canonique J4 existante
                                  │
                                  ▼
                 observation J5 et lignes normalisées
                       append-only + déduplication
                                  │
                                  ▼
             /events/{canonicalEventId}/statistics
              valeurs + complétude + provenance
```

L'action locale **« Charger la démonstration J5 »** charge et parse les trois fixtures nominales
avant la première écriture. Elle exige une identité canonique J4 existante et l'égalité stricte des
trois `eventId` avec son `providerEventId`. La transaction est annulée si une famille est
incompatible ou incohérente. Une seconde exécution est dédupliquée.

## 3. Contrats JSON synthétiques v1

Le lecteur JSON refuse les propriétés dupliquées, le contenu résiduel, les structures incorrectes
et les coercitions implicites. Un champ inconnu ne détruit pas une forme autrement valide : il
produit un avertissement structuré avec son chemin. Une erreur obligatoire ou de type produit
`SCHEMA_INCOMPATIBLE`, sans objet métier partiel et sans persistance.

| Parseur | Racine synthétique | Éléments normalisés principaux |
|---|---|---|
| `event-statistics-v1` | `eventId`, `statistics[]` | période, groupe, code, nom, valeurs domicile/extérieur |
| `event-incidents-v1` | `eventId`, `incidents[]` | ordre, type, minute, temps ajouté, côté, équipe, joueur, score |
| `event-lineups-v1` | `eventId`, `confirmed`, `home`, `away` | côté, formation, joueurs, numéro, position, titulaire/remplaçant |

Pour les statistiques, une valeur scalaire peut être un texte ou un nombre JSON. Elle est
normalisée en texte sans fabriquer de valeur lorsqu'elle est absente. Pour les incidents, les deux
éléments d'un score sont atomiques : ils sont tous deux présents ou tous deux absents. Pour les
compositions, les objets `home` et `away`, leurs tableaux `players` et le booléen `confirmed` sont
structurellement obligatoires, même si une liste de joueurs est vide.

## 4. Contrôles de complétude

La complétude décrit uniquement les signaux explicitement définis par le contrat local. Elle ne
mesure pas la qualité globale d'un fournisseur.

```text
score = floor(presentSignals * 100 / expectedSignals)
```

| Famille | Signaux attendus | Cas vide valide |
|---|---|---|
| statistiques | `home` et `away` pour chaque métrique | aucune métrique déclarée |
| incidents | `isHome` pour chaque incident | tableau `incidents` vide |
| compositions | confirmation vraie, deux formations, deux listes non vides, numéro et position de chaque joueur | aucun ; le contrat comporte toujours des signaux |

Les trois états ont des invariants distincts :

- `COMPLETE` : au moins un signal attendu, tous présents, score `100`, aucun chemin manquant ;
- `PARTIAL` : au moins un signal attendu, au moins un absent, score calculé par division entière et
  liste non vide de chemins manquants ;
- `EMPTY_VALID` : zéro signal attendu et présent, score structurel `100`, aucun chemin manquant.

Un tableau vide valide n'est pas assimilé à une réponse complète d'un match réel. Il signifie
seulement que le contrat synthétique a explicitement fourni une liste vide bien formée. Aucun zéro,
joueur, camp, score ou libellé n'est créé pour améliorer artificiellement le résultat.

## 5. Provenance et empreintes

Chaque observation J5 conserve :

- l'UUID canonique et l'identifiant fournisseur de l'événement ;
- la famille logique ;
- la référence de fixture, son SHA-256 brut, la version du parseur et l'heure enregistrée ;
- le statut, le score, les nombres de signaux et les chemins manquants ;
- une empreinte SHA-256 déterministe du modèle normalisé.

L'empreinte normalisée inclut la version du format de hash, la famille, l'identité fournisseur et
les champs métier dans leur ordre normalisé. La clé de déduplication associe identité canonique,
famille, type et référence de source, puis empreinte normalisée. Les octets JSON ne sont jamais
copiés dans les tables normalisées.

L'objet de domaine J5 initial n'accepte que `SYNTHETIC_FIXTURE`. Le schéma SQL prévoit une future
provenance `PROVIDER_SNAPSHOT`, avec clé étrangère vers le brut J3, mais aucun service J5 ne peut
encore construire cette forme. Cette asymétrie réserve la migration future sans ouvrir le réseau.

## 6. Schéma PostgreSQL V7

La migration append-only `V7__j5_event_data_completeness.sql` ajoute :

- `j5_event_data_observation` pour la provenance, la complétude et l'empreinte de chaque lot ;
- `j5_event_metric` pour les métriques de statistiques ;
- `j5_event_incident` pour les incidents ordonnés ;
- `j5_event_lineup_side` pour les deux côtés et leurs formations ;
- `j5_event_lineup_player` pour les joueurs ordonnés par côté.

Les contraintes vérifient la famille de chaque enfant, la forme exclusive de provenance, les
hashes, les versions de parseur, les trois formes de complétude et les bornes métier locales. Les
cinq tables refusent `UPDATE` et `DELETE` par trigger. Toute correction doit créer une nouvelle
observation ; les migrations V1 à V6 restent inchangées.

La lecture courante sélectionne, pour chaque famille, l'observation dont l'heure de source est la
plus récente, puis l'identifiant le plus élevé. Elle reconstruit les modèles immuables depuis les
tables enfants et revalide les invariants de domaine.

## 7. Interface locale

Depuis la fiche J4, le lien J5 ouvre :

```text
GET /events/{canonicalEventId}/statistics?zone=<zone IANA>
```

La page rend séparément les trois familles. Elle expose les valeurs normalisées, le statut et le
score de complétude, les chemins manquants, la référence de source, le parseur, l'heure et les deux
hashes. Elle ne rend aucun payload brut. Une famille absente est un état local explicite et ne
déclenche aucune action implicite.

L'import de démonstration utilise un POST distinct protégé par un jeton local lié à la session et à
usage unique :

```text
POST /events/{canonicalEventId}/statistics/offline-demo
```

Les réponses de lecture portent `no-store` et les protections Web locales déjà utilisées par J4.

## 8. Corpus et qualification

Le corpus ajoute neuf scénarios synthétiques :

- statistiques nominales, statistiques partielles et identifiant devenu texte ;
- incidents nominaux, incidents vides et minute devenue texte ;
- compositions nominales, compositions partielles non confirmées et identifiant joueur devenu
  texte.

Tous les manifestes portent `fixtureOrigin=SYNTHETIC`, `providerSchemaValidated=false`,
`httpStatus=null` et un hash vérifié. Les tests standard couvrent le parsing, les ruptures, les
scores, l'import transactionnel et idempotent, la requête et le rendu MVC. Les tests
PostgreSQL/Testcontainers couvrent Flyway V7, les lignes normalisées, les trois états de complétude,
la sélection de la dernière version, la déduplication et l'append-only.

## 9. Décisions différées

Les éléments suivants exigent un nouveau Work Order ou un amendement explicite :

- observer avec succès puis minimiser les schémas réels actuels des trois chemins ;
- créer des parseurs fournisseur séparés si les enveloppes diffèrent des contrats synthétiques ;
- autoriser un transport J5 manuel avec persistance brute avant parsing ;
- ajouter `TOURNAMENT_STANDINGS`, qui n'est pas requis par la preuve de sortie J5 ;
- comparer des versions et corrections tardives dans J6 ;
- exporter les données canoniques vers le pipeline J7.

Cette liste consigne les décisions différées à la clôture du socle synthétique. Les Work Orders
ultérieurs ont traité, dans leurs périmètres propres, la qualification réelle gardée J5
(`WO-SS-20260815-006`), l'historique J6 (`WO-SS-20260818-007`), l'export J7
(`WO-SS-20260819-008`) et l'import J5 hors ligne multi-match (`WO-SS-20260822-010`). Ils ne
modifient pas rétroactivement les statuts, contrats v1 ou résultats consignés ici.

`TOURNAMENT_STANDINGS` et toute nouvelle famille fournisseur restent différés. Les contrats
courants du lot multi-match sont définis dans
`docs/requirements/J5-OFFLINE-MULTI-MATCH-IMPORT-RULES.md` et
`docs/architecture/J5-OFFLINE-MULTI-MATCH-IMPORT.md`.

L'amendement WO-010 du 2026-08-22 permet désormais, dans les imports locaux unitaire et multi-match,
de remplacer le fichier d'une famille dont le HTTP 404 a été observé par une déclaration fermée.
Elle produit l'enveloppe canonique locale marquée `LOCAL_OPERATOR_DECLARED_HTTP_404`, sans transport
et sans prétendre reproduire le corps fournisseur. Le contrat reste exactement une preuve par
famille ; fichier plus déclaration et absence des deux sont refusés.

# J3 — Adaptation hors ligne au schéma qualifié de la page 2

## Décision et frontière

Cette unité corrige `scheduled-events-v1` à partir du seul snapshot local de page 2 conservé le
2026-08-13. Elle ne résout aucune URI et n’exécute aucun transport fournisseur. Le rapport humain
de référence est
`docs/validation/J3-WINDOWS-PAGE-TWO-PROVIDER-RESUME-QUALIFICATION-20260813.md`.

Les snapshots `1` et `2` restent des preuves historiques immuables. Leur colonne
`schema_status=SCHEMA_INCOMPATIBLE` n’est ni corrigée rétroactivement ni remplacée : le résultat du
parseur courant est une lecture distincte en mémoire.

```text
PROVIDER_NETWORK_CALL_EXECUTED=NO
SNAPSHOT_1_MUTATED=NO
SNAPSHOT_2_MUTATED=NO
HISTORICAL_SCHEMA_STATUS=SCHEMA_INCOMPATIBLE
CURRENT_REPARSE_RESULT=SEPARATE_METADATA_ONLY_RESULT
```

## Diagnostic structurel minimisé

La comparaison a été effectuée en lecture seule dans PostgreSQL, sans afficher les payloads, leurs
valeurs métier, une URI, un en-tête ou une donnée de session.

| Propriété structurelle | Page 1 | Page 2 |
|---|---:|---:|
| entrées `scheduled` | 100 | 100 |
| `timezoneEventCount` objet | 100 | 94 |
| `timezoneEventCount` tableau vide | 0 | 6 |
| `timezoneEventCount` tableau non vide | 0 | 0 |
| `hasNextPage` | `true` | `true` |

La divergence bloquante est donc bornée : sur six entrées de page 2, le fournisseur représente
l’absence de compte par `[]` au lieu de `{}`. Les racines et les autres champs utiles restent ceux
déjà qualifiés sur la page 1.

## Contrat corrigé sans coercition

Pour `$.scheduled[*].timezoneEventCount`, le parseur accepte désormais exactement :

- un objet dont chaque clé représente un entier 32 bits et dont chaque valeur est un entier positif
  ou nul ;
- un tableau strictement vide, projeté vers une table locale vide avec l’avertissement
  `EMPTY_TIMEZONE_EVENT_COUNT`.

Un tableau non vide, une valeur scalaire, un objet avec une clé non numérique ou une valeur
négative reste `SCHEMA_INCOMPATIBLE`. Aucun élément de tableau n’est converti en compteur et aucune
page partielle n’est publiée.

Deux ressources synthétiques rejouent ce contrat sans donnée fournisseur :

- `fixtures/scheduled-events/qualified-page-two-shape.json` couvre un objet suivi d’un tableau
  vide et conserve `hasNextPage=true` ;
- `fixtures/schema-breaks/scheduled-events-timezone-count-non-empty-array.json` prouve qu’un
  tableau non vide est rejeté.

Leurs manifestes déclarent `SYNTHETIC`, `providerSchemaValidated=false`, `httpStatus=null` et ne
contiennent ni cookie, ni jeton, ni URI, ni compte ou donnée de session.

## Relecture locale immuable

`J3QualificationCheckpointReparser` relit les checkpoints via le port local existant, vérifie leur
intégrité taille/SHA-256, applique le parseur courant et retourne uniquement des métadonnées. Il ne
dispose d’aucun port d’écriture ni d’aucun transport.

La relecture réelle locale du 2026-08-14 a produit :

```text
PAGE_1_SNAPSHOT_ID=1
PAGE_1_HISTORICAL_STATUS=SCHEMA_INCOMPATIBLE
PAGE_1_CURRENT_PARSE_STATUS=PARSED
PAGE_1_SCHEDULED_ENTRY_COUNT=100
PAGE_1_HAS_NEXT_PAGE=true
PAGE_2_SNAPSHOT_ID=2
PAGE_2_HISTORICAL_STATUS=SCHEMA_INCOMPATIBLE
PAGE_2_CURRENT_PARSE_STATUS=PARSED
PAGE_2_SCHEDULED_ENTRY_COUNT=100
PAGE_2_HAS_NEXT_PAGE=true
HISTORICAL_STATUSES_UNCHANGED=true
DATABASE_MUTATION_EXECUTED=NO
PROVIDER_NETWORK_CALL_EXECUTED=NO
RAW_PAYLOAD_PRINTED=NO
```

## Frontière de la prochaine décision

Cette adaptation n’autorise pas la page 3. Une unité future distincte, intitulable
`feat: resume J3 qualification from page three`, ne pourra être engagée qu’après une nouvelle
décision explicite et devra exiger exactement :

```text
PAGE_1_CHECKPOINT=PRESENT_AND_REPARSEABLE
PAGE_2_CHECKPOINT=PRESENT_AND_REPARSEABLE
PAGES_3_TO_5=ABSENT
PROVIDER_FIRST_PAGE=3
PAGE_1_REPEATED=NO
PAGE_2_REPEATED=NO
```

La présente unité ne modifie donc ni l’orchestrateur de reprise, ni le bouton final, ni la politique
de transport. La présence de la page 2 continue de bloquer toute nouvelle exécution fournisseur.

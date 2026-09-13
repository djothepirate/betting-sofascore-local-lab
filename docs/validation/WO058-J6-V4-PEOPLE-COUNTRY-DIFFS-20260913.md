# WO-058 — Diff sémantique J6 V4 : officiels et pays

**Date :** 13 septembre 2026

**Work Order :** [WO-SS-20260907-058-bounded-live-j4-j5.md](../work_orders/completed/WO-SS-20260907-058-bounded-live-j4-j5.md)

**Périmètre :** complément de revue de la PR #35
**Statuts préservés :** `EXPERIMENTAL`, `LOCAL_ONLY`, `NOT_PRODUCTION_APPROVED`, `NO_CRITICAL_DEPENDENCY`.

## Constat

Deux remarques P2 de la revue de la PR #35 concernaient la projection des observations
normalisées V4 dans l'historique sémantique J6 :

1. les changements des officiels `event-details-v4` étaient déjà normalisés et persistés,
   mais n'étaient pas inclus dans `compareDetails` ;
2. les changements de pays des joueurs `event-lineups-v4` restaient invisibles lorsqu'un
   joueur ou un indisponible était apparié par son identifiant fournisseur.

Le correctif porte exclusivement sur la restitution des différences J6. Il ne modifie ni les
parseurs V4, ni les hashes, ni la provenance, ni le stockage, ni les migrations.

## Projection J6 ajoutée

Pour chaque officiel facultatif `homeManager`, `awayManager` et `referee`, J6 compare,
dans cet ordre stable :

- `<role>.name`
- `<role>.country.name`
- `<role>.country.alpha2`

Pour les compositions V4, J6 compare aussi les deux attributs de pays, après appariement
univoque par `providerPlayerId` :

- `lineups[HOME|AWAY,playerId=…].country.name`
- `lineups[HOME|AWAY,playerId=…].country.alpha2`
- `lineups[HOME|AWAY].missingPlayers[playerId=…].country.name`
- `lineups[HOME|AWAY].missingPlayers[playerId=…].country.alpha2`

Les transitions conservent la sémantique existante : absence vers valeur = `ADDED`, valeur
vers absence = `REMOVED`, correction = `CHANGED`. Aucune valeur de pays ni aucun officiel
de remplacement n'est inventé.

Les cas ambigus qui comportent plusieurs enregistrements avec le même `playerId` conservent
le comportement J6 prudent existant, fondé sur les ajouts/suppressions résumés. Les deux
commentaires P2 visaient le cas d'appariement univoque, maintenant couvert.

## Régressions ajoutées

`J6SemanticDiffServiceTest` couvre :

- les corrections de nom, de nom de pays et de code alpha-2 pour les trois officiels V4 ;
- les apparitions, disparitions et pays partiels d'officiels, sans valeur de repli ;
- les changements de pays d'un joueur de composition et d'un indisponible appariés ;
- les apparitions de pays pour ces deux catégories ;
- l'absence de différence lorsque les valeurs V4 sont identiques.

Les fixtures utilisent les traces fournisseur valides `event-details-v4` et
`event-lineups-v4`, afin d'exercer les contrats V4 effectifs sans aucun transport réseau
vers le fournisseur.

## Validation

| Commande | Résultat |
| --- | --- |
| `mvnw.cmd -q "-Dtest=J6SemanticDiffServiceTest" test` | Succès : 16 tests, 0 échec, 0 erreur, 0 ignoré. |
| `mvnw.cmd clean verify` | `BUILD SUCCESS` en 17 min 38 s : 2 308 tests unitaires (0 échec, 0 erreur, 5 ignorés), puis 241 tests d'intégration (0 échec, 0 erreur, 0 ignoré). |
| `git diff --check` | Succès : aucun défaut de patch. |

La validation standard s'appuie sur des fixtures, des doubles locaux et PostgreSQL via
Testcontainers. Elle n'a déclenché aucune collecte ni aucun appel réel vers SofaScore.

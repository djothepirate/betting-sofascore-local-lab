# J5 réel — Correction de la déduplication et des participants de remplacement

## 1. Statut

```text
EXPERIMENTAL
LOCAL_ONLY
NOT_PRODUCTION_APPROVED
NO_CRITICAL_DEPENDENCY
PROVIDER_SCHEMA_VALIDATED=NO
```

- **Date du diagnostic :** 2026-08-16
- **Work Order :** `WO-SS-20260815-006`
- **Appel fournisseur par l'agent :** `0`
- **Payload brut versionné ou reproduit :** `NO`
- **Correctif :** politique de classification des bruts dédupliqués,
  `event-incidents-v4` et Flyway V11
- **Retest réel V4 :** `NOT_RUN_OPERATOR_ONLY`

## 2. Preuves opérateur minimisées

Les captures opérateur ne sont pas versionnées. Aucun endpoint fournisseur n'a été résolu ou
appelé pendant le diagnostic ou la réalisation du correctif. La phrase de confirmation
ponctuelle n'est ni reproduite ni conservée.

Le retest humain de `16412917` après V10 a produit les observations minimisées suivantes :

| Ordre | Famille | Snapshot | HTTP | Résultat courant | Effet |
|---:|---|---:|---:|---|---|
| 1 | `EVENT_STATISTICS` | 30 | 404 | `UNAVAILABLE · N/A` | attendu, poursuite |
| 2 | `EVENT_INCIDENTS` | 32 | 200 | V3, `COMPLETE · 100%`, `20/20` | incidents visibles |
| 3 | `EVENT_LINEUPS` | — | — | non appelée | campagne arrêtée avant transport |

La campagne a indiqué `RAW_CLASSIFICATION_ERROR` et deux appels fournisseur. Le catalogue local
montrait que le snapshot 32 était le même brut que lors de la campagne V2 : 22 945 octets,
classification historique `SCHEMA_INCOMPATIBLE`. Le succès V3 ne créait donc pas un nouveau brut,
mais une nouvelle interprétation de la même preuve.

Les incidents normalisés ont confirmé que les remplacements étaient présents. Leur ligne ne
montrait toutefois aucun joueur, alors que la forme JSON inspectée exposait deux objets distincts
`playerIn` et `playerOut`. Le défaut concernait tous les objets `substitution`, pas seulement un
remplacement particulier.

## 3. Cause racine de `RAW_CLASSIFICATION_ERROR`

La clé de déduplication brute associe fournisseur, endpoint logique, clé de requête et SHA-256 du
payload. Une réponse identique pour le même événement résout donc volontairement le snapshot
historique. Cette opération renvoie `DEDUPLICATED` avec l'identifiant 32.

Après avoir persisté l'observation V3, le service appelait uniformément la classification
`PARSED`, sans tenir compte du résultat `DEDUPLICATED`. La persistance brute n'autorise une
classification que sur une ligne `RAW_ONLY` ou pour répéter exactement son état terminal. Le
passage de l'état V2 historique `SCHEMA_INCOMPATIBLE` à `PARSED` affectait donc zéro ligne et était
refusé à juste titre. Le service convertissait ensuite ce refus en incident terminal
`RAW_CLASSIFICATION_ERROR`.

La classification historique et le résultat courant répondent à deux questions différentes :

- le snapshot 32 conserve le résultat du parseur qui l'a classé historiquement ;
- l'observation V4 conserve le résultat, le parseur et la complétude de l'interprétation courante.

Réécrire le premier pour refléter le second aurait violé l'historique append-only. Arrêter la
campagne à cause de cette immuabilité empêchait cependant à tort la troisième famille.

## 4. Décision de déduplication

La voie J5 applique désormais la règle suivante à chaque famille :

1. le brut est toujours persisté ou résolu avant parsing ;
2. si le résultat est `INSERTED`, la classification terminale courante est écrite comme avant ;
3. si le résultat est `DEDUPLICATED`, aucune classification historique n'est tentée ;
4. l'observation normalisée courante conserve snapshot, SHA-256, parseur et heure de réception ;
5. la séquence continue selon le résultat courant, sans cache, répétition d'appel ou retry ;
6. un résultat `CACHE_HIT`, impossible sur ce port direct, reste refusé par défaut.

Cette décision vaut également pour une indisponibilité HTTP `404` dédupliquée. Le snapshot 30
reste sa preuve historique et la nouvelle observation `UNAVAILABLE` exprime l'acquisition
courante sans réécriture.

## 5. Décision de normalisation des remplacements V4

`event-incidents-v3` avait pour objectif borné la sentinelle des marqueurs de période. Il
normalisait l'objet générique `player`, utilisé notamment par les buts et cartons, mais ignorait
les objets propres à un remplacement.

`event-incidents-v4` conserve le comportement V3 et ajoute :

- `playerIn.id` et `playerIn.name` vers le joueur entrant ;
- `playerOut.id` et `playerOut.name` vers le joueur sortant ;
- validation atomique identifiant/nom pour chaque joueur ;
- deux signaux de complétude par remplacement ;
- `PARTIAL` avec `$.incidents[n].playerIn` ou `playerOut` lorsqu'un objet manque ;
- conservation de l'ordre source pour tous les incidents et tous les remplacements.

Aucune traduction, valeur marchande, date de naissance ou autre métadonnée annexe n'est copiée
dans le modèle J5. Seuls l'identifiant fournisseur et le nom nécessaires à l'identification locale
sont conservés. La vue affiche les colonnes « Entrant » et « Sortant » ; une valeur absente reste
un tiret explicite et n'est jamais remplacée par une identité supposée.

## 6. Persistance append-only V11

La migration `V11__j5_incident_substitution_players.sql` :

- ajoute quatre colonnes facultatives à `j5_event_incident` ;
- impose la présence conjointe de l'identifiant positif et du nom pour chaque participant ;
- autorise `event-incidents-v4` dans la contrainte de provenance ;
- maintient les versions V1, V2, V3 et indisponibles déjà permises ;
- n'exécute aucun `UPDATE`, `DELETE` ou backfill d'observation.

L'empreinte normalisée distingue une observation contenant des participants de remplacement de
son ancienne représentation sans ces champs. Une réponse brute dédupliquée peut donc produire une
nouvelle observation V4 sans collision avec l'observation V3 et sans modifier le snapshot.

## 7. Validation hors ligne

```text
TARGETED_COMMAND=.\mvnw.cmd -q -Dtest=EventIncidentsV3ParserTest,EventIncidentsV4ParserTest,J5RealEventDataServiceTest,J5EventDataControllerTest test
TARGETED_RESULT=PASS
STANDARD_COMMAND=.\mvnw.cmd clean verify
STANDARD_RESULT=PASS_268_TESTS
INTEGRATION_COMMAND=.\mvnw.cmd -Pintegration-tests verify
INTEGRATION_RESULT=PASS_23_TESTS
FLYWAY=V1_TO_V11_AND_V10_TO_V11_PASS
POSTGRESQL=18.4_TESTCONTAINERS
SOFASCORE_PROVIDER_CALLS=0
```

Les régressions vérifient notamment :

- le scénario réel minimisé `404` dédupliqué → incidents dédupliqués → compositions insérées ;
- l'absence de toute tentative de reclassification des snapshots historiques 30 et 32 ;
- la classification `PARSED` du seul brut compositions nouvellement inséré ;
- la conservation PostgreSQL de `SCHEMA_INCOMPATIBLE` sur le snapshot V2 pendant l'ajout d'une
  observation V4 rattachée au même identifiant ;
- la persistance et la relecture des deux identités de remplacement ;
- l'affichage HTML des colonnes et des noms entrant/sortant ;
- plusieurs remplacements dans une même réponse ;
- une identité sortante manquante rendue `PARTIAL` sans joueur inventé ;
- le maintien du refus de `addedTime=999` sur un incident métier latéralisé ;
- l'upgrade V10 → V11 sans réécriture historique.

## 8. Limites et prochain contrôle humain

Le correctif ne démontre pas encore la forme réelle de `EVENT_LINEUPS`, puisque la dernière
campagne s'est arrêtée avant son appel. Il ne modifie pas rétroactivement l'observation V3 déjà
visible ; une nouvelle campagne après V11 doit créer ou sélectionner l'observation V4 courante.

Après redémarrage et application de V11, un geste humain séparé doit vérifier :

- statistiques toujours indisponibles sans incident pour `16412917` ;
- incidents parsés par `event-incidents-v4` avec les joueurs entrant et sortant visibles ;
- exactement une tentative `EVENT_LINEUPS` après le délai minimal ;
- résultat compositions compatible, indisponible ou terminal selon les règles existantes ;
- aucun retry et aucun quatrième appel ;
- reverrouillage local de la configuration et arrêt final du processus.

```text
J5_DEDUPLICATION_CORRECTION=PASS_OFFLINE
J5_INCIDENT_V4_TECHNICAL_STATUS=PASS_OFFLINE
J5_INCIDENT_V3_REAL_STATUS=PASS_20_OF_20
J5_LINEUPS_REAL_STATUS=NOT_OBSERVED
J5_V4_REAL_RETEST=NOT_RUN
J5_WORK_ORDER_STATUS=ACTIVE
J5_WORK_ORDER_CAN_BE_ARCHIVED=NO
```

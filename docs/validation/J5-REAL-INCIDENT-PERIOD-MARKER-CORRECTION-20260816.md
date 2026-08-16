# J5 réel — Correction des marqueurs de période incidents

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
- **Correctif :** `event-incidents-v3` et Flyway V10
- **Retest réel V3 :** `PASS_20_OF_20_ON_SNAPSHOT_32`

## 2. Preuves opérateur minimisées

Les captures opérateur ne sont pas versionnées. L'analyse locale a porté sur les snapshots déjà
persistés, par l'action d'inspection explicite et en lecture seule. Aucun endpoint fournisseur n'a
été résolu ou appelé pendant le diagnostic.

| Événement | Famille | Snapshot | HTTP | Taille | Résultat historique |
|---|---|---:|---:|---:|---|
| `16412917` | statistiques | 30 | 404 | 44 octets | `ENDPOINT_UNAVAILABLE`, attendu |
| `16412917` | incidents | 32 | 200 | 22 945 octets | `SCHEMA_INCOMPATIBLE` sous V2 |
| `16412917` | compositions | — | — | — | non tentée après le verrou incidents |
| `16391135` | statistiques | 33 | 200 | 25 920 octets | `PARSED`, `COMPLETE · 100%`, `268/268` |
| `16391135` | incidents | 34 | 200 | 48 690 octets | `SCHEMA_INCOMPATIBLE` sous V2 |
| `16391135` | compositions | — | — | — | non tentée après le verrou incidents |

L'inspection structurelle minimisée a compté 20 incidents dans le snapshot 32 et 22 dans le
snapshot 34. Dans chaque réponse, les seuls problèmes bloquants du parseur V2 étaient deux chemins
`addedTime` hors de la plage `0..30`. Les quatre objets concernés étaient des marqueurs
`incidentType=period` portant la valeur `999`.

## 3. Cause racine

`event-incidents-v2` appliquait uniformément la plage d'un temps additionnel métier à toute valeur
`addedTime`. Le fournisseur utilise pourtant `999` comme sentinelle sur ses marqueurs techniques de
période. Cette valeur n'est ni un ajout de 999 minutes ni une donnée à afficher comme telle.

L'interruption de `16412917` fournit un contexte plausible à cette convention, mais elle ne suffit
pas à définir sa sémantique : le même motif a été observé dans le snapshot 34 d'une autre
rencontre. Le normaliseur ne déduit donc jamais une durée à partir de l'heure de fin, du statut ou
de `999`.

Le classement `SCHEMA_INCOMPATIBLE` verrouillait correctement la campagne selon le contrat
existant. Son effet expliquait entièrement l'absence de compositions : la troisième requête
n'était pas exécutée après l'incident de parsing de la deuxième famille.

## 4. Décision de normalisation V3

`event-incidents-v3` applique la règle bornée suivante :

1. si et seulement si `incidentType=period` et `addedTime=999`, les octets restent inchangés dans
   le snapshot, la valeur normalisée est absente et un avertissement
   `PROVIDER_SENTINEL_NORMALIZED` conserve la décision ;
2. pour tout autre type, la plage `0..30` reste obligatoire et `999` demeure
   `SCHEMA_INCOMPATIBLE` ;
3. les marqueurs globaux `period` et `injuryTime` n'exigent pas `isHome` pour être complets ;
4. aucune observation V2 historique n'est reparsée ou reclassée automatiquement.

La migration append-only V10 étend uniquement la contrainte de provenance afin d'accepter
`event-incidents-v3`. Elle maintient toutes les versions V1, V2 et indisponibles déjà autorisées.

## 5. Validation hors ligne

```text
COMMAND_1=.\mvnw.cmd -q -Dtest=EventIncidentsV3ParserTest,J5RealEventDataServiceTest test
RESULT_1=PASS_8_TESTS
COMMAND_2=.\mvnw.cmd clean verify
RESULT_2=PASS_263_TESTS
COMMAND_3=.\mvnw.cmd -Pintegration-tests verify
RESULT_3=PASS_21_TESTS
FLYWAY=V1_TO_V10_AND_V9_TO_V10_PASS
POSTGRESQL=18.4_TESTCONTAINERS
SOFASCORE_PROVIDER_CALLS=0
```

Le test V3 accepte une fixture synthétique représentative, vérifie l'absence de temps additionnel
normalisé pour la sentinelle, conserve un temps additionnel ordinaire et refuse `999` sur un
carton. Les tests de service prouvent aussi que la séquence atteint `EVENT_LINEUPS` après des
incidents compatibles, y compris lorsque les statistiques sont indisponibles en HTTP `404`.

La première exécution n'a exécuté aucun test Flyway : le contexte Spring héritait des opt-ins
opérateur concurrents et a été refusé par la validation de configuration. Le test d'intégration
force désormais `sofascore.enabled=false` et toutes les qualifications J3/J4/J5 à `false`. La
relance a validé 21/21 tests sans appel fournisseur et sans lecture ou modification de `.env`.

## 6. Limites et prochain contrôle humain

Les snapshots 32 et 34 restent des preuves historiques V2 `SCHEMA_INCOMPATIBLE`; V10 ne les
réécrit pas. Un retest humain ultérieur a reparsé la réponse réelle dédupliquée du snapshot 32 par
V3 et rendu visibles 20 incidents sur 20. Il a toutefois révélé un second défaut : la tentative de
reclasser le snapshot historique produisait `RAW_CLASSIFICATION_ERROR` et arrêtait encore la
campagne avant le troisième appel. Il a aussi confirmé que V3 ne conservait pas les objets
`playerIn` et `playerOut` des remplacements.

Ces constats et leur correction V4/V11 sont détaillés dans
`J5-REAL-DEDUPLICATION-AND-SUBSTITUTION-CORRECTION-20260816.md`. Après application de V11 et
redémarrage, une nouvelle campagne humaine séparée doit confirmer :

- incidents HTTP `200` parsés par `event-incidents-v4` avec joueurs entrant/sortant ;
- conservation du brut et présence de l'avertissement de sentinelle ;
- tentative unique de `EVENT_LINEUPS` après le délai minimal ;
- classification compatible, indisponible ou terminale de cette troisième famille sans retry ;
- reverrouillage de la configuration et arrêt final du processus.

```text
J5_INCIDENT_V3_TECHNICAL_STATUS=PASS_OFFLINE
J5_STATISTICS_REAL_STATUS=PARSED_ON_16391135
J5_INCIDENT_V3_REAL_STATUS=PASS_20_OF_20
J5_LINEUPS_REAL_STATUS=NOT_OBSERVED
J5_INCIDENT_V4_REAL_STATUS=NOT_RUN
J5_WORK_ORDER_STATUS=ACTIVE
J5_WORK_ORDER_CAN_BE_ARCHIVED=NO
```

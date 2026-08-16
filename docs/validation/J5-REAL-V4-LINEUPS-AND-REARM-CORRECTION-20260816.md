# J5 réel — Retest V4, compositions et correction du réarmement

## 1. Statut

```text
EXPERIMENTAL
LOCAL_ONLY
NOT_PRODUCTION_APPROVED
NO_CRITICAL_DEPENDENCY
PROVIDER_SCHEMA_VALIDATED=NO
```

- **Date du retest et du diagnostic :** 2026-08-16
- **Work Order :** `WO-SS-20260815-006`
- **Appel fournisseur par l'agent :** `0`
- **Payload brut versionné ou reproduit :** `NO`
- **Phrase de confirmation reproduite :** `NO`
- **Retest réel V4 et compositions :** `PASS_THREE_CALLS_NO_RETRY`
- **Correctif de réarmement :** `PASS_OFFLINE_RETEST_REQUIRED`

## 2. Preuves opérateur minimisées

Les captures opérateur ne sont pas versionnées. Elles montrent une campagne humaine terminée sur
l'événement `16412917` après application de V11. Le présent rapport conserve uniquement les
métadonnées nécessaires au diagnostic ; aucun corps JSON, URI complète, jeton ou phrase ponctuelle
n'est copié.

| Ordre | Famille | Snapshot | HTTP | Taille | Parseur / normaliseur | Complétude | Observation |
|---:|---|---:|---:|---:|---|---|---:|
| 1 | `EVENT_STATISTICS` | 30 | 404 | 44 octets | `event-statistics-unavailable-v1` | `UNAVAILABLE · N/A` | 7 |
| 2 | `EVENT_INCIDENTS` | 32 | 200 | 22 945 octets | `event-incidents-v4` | `COMPLETE · 36/36` | 12 |
| 3 | `EVENT_LINEUPS` | 55 | 200 | 35 669 octets | `event-lineups-v2` | `COMPLETE · 85/85` | 13 |

Le HTTP `404` des statistiques est l'indisponibilité attendue pour cette rencontre et ne bloque
pas la séquence. Les 20 incidents sont visibles ; toutes les substitutions rendent le joueur
entrant et le joueur sortant lorsque les objets fournisseur correspondants sont présents. Les
compositions des deux équipes sont visibles avec les formations domicile `4-1-4-1` et extérieur
`4-3-3`. Le contrôle affiche `COMPLETED_LOCKED` et exactement trois appels fournisseur. Aucun
retry, quatrième appel ou rappel d'une famille n'est observé.

Ces résultats valident humainement les deux corrections précédentes :

- un brut dédupliqué est reparsé sans tentative de reclassification de sa preuve historique ;
- `event-incidents-v4` conserve et affiche séparément `playerIn` et `playerOut` ;
- la séquence atteint bien `EVENT_LINEUPS`, dont la forme réelle est compatible avec
  `event-lineups-v2` sur cette rencontre.

## 3. Nouvelle anomalie constatée

Après le succès sur `16412917`, l'opérateur a navigué vers l'événement `16391135` dans la même
instance. La page J5 affichait encore l'état global `COMPLETED_LOCKED` et le bouton de préparation
était désactivé. Une nouvelle campagne ne pouvait donc pas être préparée malgré l'absence de toute
campagne active ou de tout incident bloquant.

## 4. Cause racine

`J5RealControlService` est volontairement un singleton Spring et porte un seul contrôle global en
mémoire. Ce choix garantit une concurrence fournisseur égale à un pour J5. Après `complete()`, son
état devient `COMPLETED_LOCKED` et le claim terminé ne peut plus être exécuté.

Le garde historique de `prepare()` et la propriété d'affichage `preparationAllowed()`
n'autorisaient toutefois que l'état initial `LOCKED`. Ils assimilaient donc le verrou anti-rejeu
d'un succès aux verrous de processus issus d'un échec, d'un arrêt ou d'une expiration. Toutes les
pages d'événement voyaient ce même état global et désactivaient la nouvelle préparation.

## 5. Décision corrective

Le contrôle reste global et n'est pas transformé en map par événement. La correction borne les
transitions ainsi :

1. `LOCKED` ou `COMPLETED_LOCKED` peut recevoir un nouveau `prepare()` explicite si la politique
   fournisseur est disponible ;
2. la préparation vérifie la relation UUID / identifiant, ne résout aucune URI et n'exécute aucun
   transport ;
3. elle produit un nouvel identifiant de requête et une nouvelle phrase, remplace l'identité
   ciblée, efface la liste des endpoints terminés et le code terminal, puis passe à
   `AWAITING_CONFIRMATION` ;
4. l'ancien claim ne peut plus continuer et l'ancienne confirmation est refusée ;
5. une nouvelle préparation pendant `AWAITING_CONFIRMATION` ou `EXECUTING` est refusée ;
6. `FAILED_LOCKED`, `STOPPED_LOCKED` et `EXPIRED_LOCKED` restent verrouillés jusqu'au
   redémarrage.

`COMPLETED_LOCKED` reste donc terminal pour la campagne qui vient de finir, mais devient un point
de départ autorisé pour une campagne distincte explicitement préparée. Aucun transport, retry,
cache, polling ou rafraîchissement automatique n'est ajouté.

## 6. Régressions hors ligne

Les tests ajoutés prouvent notamment :

- campagne A préparée, confirmée et terminée après les trois endpoints ordonnés ;
- `COMPLETED_LOCKED` rend alors une nouvelle préparation disponible ;
- campagne B préparée avec un nouvel événement, un nouvel UUID de requête, une nouvelle phrase,
  aucun endpoint terminé et aucun code terminal ;
- phrase et claim de A inutilisables avant et après la préparation de B ;
- nouvelle préparation refusée pendant que B attend sa confirmation ;
- préparation B et navigation Web sans aucune interaction avec le service d'exécution ;
- bouton de préparation actif pour la page B même lorsque le snapshot global décrit le succès de
  A, sans exposer de phrase de confirmation ;
- échec `HTTP_429`, arrêt et expiration toujours non réarmables sans redémarrage ;
- séquence d'exécution inchangée : trois appels maximum, ordre fixe et aucun retry.

```text
TARGETED_COMMAND=.\mvnw.cmd -q '-Dtest=J5RealControlServiceTest,J5EventDataControllerTest' test
TARGETED_RESULT=PASS
STANDARD_COMMAND=.\mvnw.cmd clean verify
STANDARD_RESULT=PASS_270_TESTS
INTEGRATION_COMMAND=.\mvnw.cmd -Pintegration-tests verify
INTEGRATION_RESULT=PASS_23_TESTS
FLYWAY=V1_TO_V11_AND_V10_TO_V11_PASS
POSTGRESQL=18.4_TESTCONTAINERS
SOFASCORE_PROVIDER_CALLS_BY_AGENT=0
```

Aucune migration V12 n'est nécessaire : le défaut et son correctif concernent uniquement l'état
en mémoire du contrôle. Les migrations append-only V1 à V11 restent inchangées.

## 7. Prochain contrôle humain

Un redémarrage est nécessaire une fois pour charger ce correctif. Dans une instance ainsi mise à
jour, l'opérateur doit :

1. terminer une campagne A sans incident bloquant ;
2. vérifier que `COMPLETED_LOCKED` conserve le résultat A mais rend la préparation disponible ;
3. préparer une campagne B distincte sans transport ;
4. vérifier qu'un nouvel identifiant de requête et une nouvelle phrase sont émis ;
5. confirmer B séparément et vérifier l'absence de retry ou de réutilisation du claim A ;
6. vérifier, lors d'un test hors succès si celui-ci est volontairement mené, que les états
   d'échec, d'arrêt et d'expiration exigent toujours un redémarrage ;
7. reverrouiller la configuration locale et arrêter l'application.

```text
J5_V4_REAL_STATUS=PASS
J5_INCIDENT_V4_REAL_STATUS=PASS_36_OF_36
J5_LINEUPS_V2_REAL_STATUS=PASS_85_OF_85
J5_COMPLETED_CAMPAIGN_REARM_STATUS=PASS_OFFLINE_RETEST_REQUIRED
J5_PROVIDER_SCHEMA_VALIDATED=NO
J5_WORK_ORDER_STATUS=ACTIVE
J5_WORK_ORDER_CAN_BE_ARCHIVED=NO
```

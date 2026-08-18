# J5 — Qualification du réarmement réel et correction V5 du carton de banc

> `EXPERIMENTAL` · `LOCAL_ONLY` · `NOT_PRODUCTION_APPROVED` · `NO_CRITICAL_DEPENDENCY`

## 1. Objet

Ce rapport consigne deux résultats distincts du 2026-08-16 :

1. la qualification humaine du réarmement d'une campagne J5 après un succès dans la même instance ;
2. la correction hors ligne, volontairement étroite, d'une forme réelle de carton de banc qui
   arrêtait la seconde campagne après la collecte des statistiques.

Les captures opérateur restent non versionnées. Aucun payload brut, URI complète, en-tête, cookie,
jeton ou phrase de confirmation ponctuelle n'est reproduit dans ce document.

## 2. Qualification humaine du réarmement

La première campagne du retest a ciblé l'événement `16391135` et a terminé exactement trois appels
ordonnés :

| Ordre | Famille | Snapshot | Résultat |
|---:|---|---:|---|
| 1 | `EVENT_STATISTICS` | 33 | `event-statistics-v2`, `COMPLETE · 100 %` |
| 2 | `EVENT_INCIDENTS` | 34 | `event-incidents-v4`, `COMPLETE · 100 %` |
| 3 | `EVENT_LINEUPS` | 58 | `event-lineups-v2`, `COMPLETE · 100 %` |

Le contrôle a atteint `COMPLETED_LOCKED`. Sans redémarrage, l'opérateur a ensuite préparé et
confirmé une campagne distincte pour l'événement `16483632`. Cette seconde exécution prouve que
`COMPLETED_LOCKED` protège l'ancien claim contre tout rejeu tout en autorisant une nouvelle
préparation explicite avec un nouvel identifiant de requête, une nouvelle phrase et un nouvel
acquittement. Aucun transport n'est déclenché par la préparation elle-même.

```text
J5_REAL_REARM_RETEST=PASS_SECOND_CAMPAIGN_STARTED
J5_OLD_CLAIM_REPLAY=FORBIDDEN
J5_CONCURRENT_CAMPAIGNS=FORBIDDEN
```

## 3. Arrêt strict de la seconde campagne

La seconde campagne a produit :

| Ordre | Famille | Snapshot | HTTP | Taille | Résultat |
|---:|---|---:|---:|---:|---|
| 1 | `EVENT_STATISTICS` | 59 | 200 | 25 562 octets | observation 17, `COMPLETE · 100 %` |
| 2 | `EVENT_INCIDENTS` | 60 | 200 | 47 092 octets | V4 `SCHEMA_INCOMPATIBLE` |
| 3 | `EVENT_LINEUPS` | — | — | — | non appelé |

Le snapshot incidents contient 17 objets. V4 n'en rejetait qu'un : un incident `card` dont le
champ `time` vaut `-5`. Le premier incident réel a arrêté la campagne en `FAILED_LOCKED`, comme
l'exige le Work Order. Il n'y a eu ni troisième appel, ni retry, ni normalisation partielle de la
famille incidents.

## 4. Sémantique métier confirmée

Le propriétaire a confirmé que `time=-5` est ici une valeur technique et non une minute négative,
une durée d'interruption ou un temps additionnel. L'incident doit être lu ainsi :

- joueur concerné : Kerem Aktürkoğlu ;
- type : carton ;
- classe : jaune ;
- minute de jeu : 58, fournie par `benchTime=58` ;
- motif : `Argument`.

`reversedPeriodTime` ne participe pas à cette interprétation. Le snapshot brut reste l'unique preuve
de la valeur fournisseur d'origine.

## 5. Cause racine

`event-incidents-v4` exigeait `time` dans l'intervalle 0 à 300 pour tous les incidents. Cette règle
était correcte pour ses formes qualifiées, mais ne connaissait pas le contrat particulier des
cartons attribués au banc. Elle rejetait donc le document entier après la réussite des statistiques
et empêchait, par arrêt sûr, l'appel `EVENT_LINEUPS`.

## 6. Correction V5 bornée

`event-incidents-v5` conserve toutes les règles V4 et ajoute uniquement la forme observée :

- la dérogation s'applique seulement à `incidentType=card` ;
- `time` doit être exactement le marqueur technique observé `-5` ;
- ce marqueur exige `benchTime` entre 0 et 300 ;
- la minute normalisée vient exclusivement de `benchTime` ;
- un avertissement `PROVIDER_BENCH_CARD_MINUTE_USED` matérialise la décision ;
- `incidentClass` et `reason` sont conservés uniquement pour un carton ;
- toute autre valeur négative, y compris sur un `card`, reste `SCHEMA_INCOMPATIBLE` ;
- aucune minute, identité, classe ou raison n'est inventée.

La migration append-only `V12__j5_incident_bench_card_details.sql` ajoute les colonnes facultatives
`incident_class` et `reason`, autorise `event-incidents-v5` et conserve la contrainte de minute
normalisée entre 0 et 300. Les migrations déjà appliquées et les observations V1 à V4 ne sont ni
modifiées ni reclassées.

La réponse réelle identique pourra être dédupliquée vers le snapshot 60 puis reparsée en une nouvelle
observation V5. La preuve historique V4 restera `SCHEMA_INCOMPATIBLE`.

## 7. Frontière volontaire

Ce correctif ne généralise aucune règle aux autres types d'incidents. La future fiche de règles de
gestion annoncée par le propriétaire pour les incidents de football deviendra la source de vérité
avant toute nouvelle évolution de schéma. Jusqu'à son adoption, tout autre écart reste bloquant afin
d'éviter une normalisation heuristique ou silencieuse.

## 8. Validation hors ligne

Commandes exécutées sans appel SofaScore réel :

```text
.\mvnw.cmd -q "-Dtest=EventIncidentsV4ParserTest,EventIncidentsV5ParserTest,J5RealEventDataServiceTest,J5EventDataControllerTest" test
.\mvnw.cmd clean verify
.\mvnw.cmd -Pintegration-tests verify
```

Résultats :

```text
TARGETED_TESTS=PASS
STANDARD_TESTS=278
STANDARD_FAILURES=0
STANDARD_ERRORS=0
STANDARD_SKIPPED=0
INTEGRATION_TESTS=25
INTEGRATION_FAILURES=0
INTEGRATION_ERRORS=0
INTEGRATION_SKIPPED=0
FLYWAY_MIGRATIONS=12
POSTGRESQL=18.4_TESTCONTAINERS
SOFASCORE_PROVIDER_CALLS_BY_AGENT=0
```

Les avertissements Mockito relatifs au chargement dynamique de l'agent Java restent non bloquants et
sans lien avec le correctif.

## 9. Prochain retest humain

La campagne V4 étant `FAILED_LOCKED`, un redémarrage est requis pour charger V5. Après une nouvelle
préparation et une nouvelle confirmation explicites, le contrôle doit vérifier :

1. le reparsing du snapshot incidents 60 avec `event-incidents-v5` ;
2. l'affichage du carton à la minute 58, de sa classe jaune et de son motif `Argument` ;
3. l'absence de réécriture de la classification historique V4 ;
4. l'unique appel `EVENT_LINEUPS` après les incidents ;
5. l'absence de retry et de quatrième appel ;
6. le reverrouillage de la configuration locale et l'arrêt final de l'application.

```text
J5_REAL_REARM_RETEST=PASS
J5_BENCH_CARD_CORRECTION=PASS_OFFLINE_RETEST_REQUIRED
J5_PROVIDER_SCHEMA_VALIDATED=NO
J5_REAL_WORK_ORDER_CAN_BE_ARCHIVED=NO
```

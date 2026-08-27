# J5 — Qualification V14 du marqueur de période live `Extra time`

## 1. Décision finale

```text
EXPERIMENTAL
LOCAL_ONLY
NOT_PRODUCTION_APPROVED
NO_CRITICAL_DEPENDENCY
CURRENT_INCIDENT_PARSER=event-incidents-v14
OBSERVED_PROVIDER_COMBINATION=period|Extra time|isLive:true
PROVIDER_PAYLOADS_COMMITTED=NO
REAL_PROVIDER_CALLS_DURING_IMPLEMENTATION=0
TARGETED_UNIT_TESTS=PASS
STANDARD_CLEAN_VERIFY=PASS_620_TESTS_2_CONDITIONAL_SKIPS
INTEGRATION_VERIFY=PASS_53_TESTS
FLYWAY_CURRENT_VERSION=26
PROVIDER_SCHEMA_VALIDATED=YES_OWNER_LOCAL_JSON_IMPORT
HUMAN_FUNCTIONAL_QUALIFICATION=PASS
WORK_ORDER_STATUS=VALIDATED
```

Ce rapport consigne une valeur fournisseur observée par le propriétaire le 2026-08-27, la readiness
technique de son traitement et la qualification fonctionnelle finale acquise le même jour sur les
corps JSON exacts conservés hors Git.

## 2. Observation minimisée

Les deux fichiers communiqués attestent un incident de tête de liste :

```text
incidentType=period
text=Extra time
isLive=true
time=120
addedTime=999
timeSeconds=7200
reversedPeriodTime=1
reversedPeriodTimeSeconds=0
periodTimeSeconds=900
```

Le score n'est pas figé : il diffère entre les deux preuves. L'une contient également des actions
de prolongation antérieures. La règle normative ne dépend donc ni d'un score exact, ni d'une
minute exacte, ni de la position dans la liste ; elle porte sur la combinaison sémantique du type,
du texte exact et du drapeau live.

Les fichiers opérateur complets ne sont pas copiés dans Git. La fixture versionnée ne contient que
les trois marqueurs de période utiles (`Extra time`, `FT`, `HT`) et aucune donnée de session,
cookie, jeton ou identité de rencontre.

## 3. Contrat V14

`event-incidents-v14` hérite de l'intégralité du comportement V13 et ajoute uniquement :

- le texte exact `Extra time` pour un incident `period` ;
- l'exigence sémantique que ce marqueur soit live lorsqu'un booléen `isLive` est fourni ;
- la mesure de `isLive` dans le score de complétude de ce marqueur.

La matrice attendue est :

| Structure | Statut | Effet |
| --- | --- | --- |
| `period / Extra time / isLive=true` | `PARSED` | libellé exact conservé |
| `period / Extra time / isLive` absent | `PARSED`, `PARTIAL` | chemin `isLive` manquant mesuré |
| `period / Extra time / isLive=false` | `SCHEMA_INCOMPATIBLE` | contradiction atomique |
| `period / valeur inconnue` | `SCHEMA_INCOMPATIBLE` | vocabulaire fermé |
| `period / ET / isLive=false` | comportement historique | marqueur terminal distinct |

La sentinelle fournisseur `addedTime=999` continue d'être normalisée par la règle historique : elle
n'est pas exposée comme temps additionnel réel.

## 4. Câblage et persistance

Le parseur courant V14 est injecté dans `J5RealEventDataService` et
`J5LocalJsonImportProcessor`. Ce dernier est partagé par la voie d'import unitaire et l'import
multi-match hors ligne ; les trois voies produisent donc la même normalisation et la même version
de parseur. La provenance d'acquisition brute reste distincte : `DIRECT_LOCAL_ENDPOINT` pour la
voie directe et `MANUAL_LOCAL_JSON_IMPORT` pour les imports locaux.

La migration append-only `V26__j5_live_extra_time_period.sql` étend exclusivement la contrainte de
version de parseur. Les observations V1 à V13 restent lisibles et compatibles. Le test d'upgrade
V25 → V26 contrôle le refus de V14 avant migration, son acceptation après migration et la
conservation du libellé `Extra time` avec sa minute et son score.

## 5. Couverture automatisée ciblée

La suite ciblée exécutée est :

```text
.\mvnw.cmd --offline -q "-Dmaven.repo.local=C:\Users\geoff\.m2\repository" "-Dtest=EventIncidentsV14ParserTest,EventIncidentsV13ParserTest,EventIncidentsV12ParserTest,EventIncidentsV6ParserTest,J5RealEventDataServiceTest,J5LocalJsonImportProcessorTest,J5LocalJsonImportServiceTest" test
PASS — 92 tests ciblés, 0 échec, 0 erreur
```

Elle couvre :

- l'acceptation fidèle de la fixture V14 ;
- le refus de la même fixture par V13 ;
- les trois états de `isLive` ;
- la fermeture du vocabulaire futur ;
- l'héritage de la règle V13 `Leaving field` ;
- la poursuite ordonnée de la campagne directe jusqu'aux compositions ;
- l'import local sans transport avec version de parseur V14 ;
- le parcours multi-match hors ligne effectif avec une occurrence `Extra time` V14 par rencontre ;
- les régressions des parseurs historiques ciblés.

La voie multi-match a aussi été isolée explicitement :

```text
.\mvnw.cmd --offline -q "-Dmaven.repo.local=C:\Users\geoff\.m2\repository" -Pintegration-tests "-Dtest=EventIncidentsV14ParserTest" "-Dit.test=FlywayMigrationIT#importsAndReimportsAnOfflineMultiMatchBatchWithAppendOnlyOccurrences" verify
PASS — parseur V14 et scénario d'import multi-match sélectionné, 0 échec, 0 erreur
```

Les validations complètes sont également vertes :

```text
.\mvnw.cmd --offline "-Dmaven.repo.local=C:\Users\geoff\.m2\repository" clean verify
PASS — 620 tests standards, 0 échec, 0 erreur, 2 ignorés conditionnels

.\mvnw.cmd --offline "-Dmaven.repo.local=C:\Users\geoff\.m2\repository" -Pintegration-tests verify
PASS — 620 tests standards + 53 tests d'intégration, 0 échec, 0 erreur
```

Les tests standards n'exécutent aucun appel réel vers SofaScore. PostgreSQL/Testcontainers utilise
l'image locale 18.4 ; un schéma neuf applique les 26 migrations versionnées V1 à V26 et les
upgrades historiques atteignent également V26.

## 6. Audits et qualification acquis

```text
STANDARD_CLEAN_VERIFY=PASS
INTEGRATION_VERIFY=PASS
SECRET_DIFF_AUDIT=PASS
LOCAL_BIND_AUDIT=PASS_127.0.0.1
NO_REAL_PROVIDER_CALL_AUDIT=PASS
HUMAN_FUNCTIONAL_QUALIFICATION=PASS
```

L'audit porte sur les 29 fichiers modifiés ou ajoutés. Il ne relève aucune affectation ressemblant
à un secret, aucun `.env`, HAR, cookie, jeton, trace ou profil navigateur et aucune nouvelle
surface HTTP. Le serveur reste lié à `127.0.0.1`, `sofascore.enabled` reste faux par défaut et les
options de polling ou de rafraîchissement automatique restent désactivées.

L'avertissement Mockito/Byte Buddy attendu sous Java 25 n'est pas un échec de test. La
qualification humaine confirme qu'un import opérateur portant la nouvelle structure rend
`Extra time`, conserve la version de parseur `event-incidents-v14` et n'altère pas le marqueur
terminal `ET`.

## 7. État du Work Order

Le propriétaire a explicitement qualifié le comportement, autorisé la fermeture de
`WO-SS-20260827-012` et son déplacement vers `docs/work_orders/completed`.

## 8. Qualification fonctionnelle finale propriétaire

Les captures finales reçues le 2026-08-27 démontrent deux imports locaux réussis du même corpus
incidents fournisseur enrichi au fil de la prolongation :

| Preuve | Parseur | Résultat | Incidents | Signaux | Marqueur |
| --- | --- | --- | ---: | ---: | --- |
| observation 567 | `event-incidents-v14` | `COMPLETE · 100%` | 24 | `86/86` | `Extra time`, minute 120, score `1-1` |
| observation 570 | `event-incidents-v14` | `COMPLETE · 100%` | 28 | `99/99` | `Extra time`, minute 120, score `1-2` |

La seconde preuve conserve en outre les incidents de prolongation précédant le marqueur final,
notamment un but à la minute 100. Le libellé `Extra time` reste verbatim, son contexte live est
confirmé et le marqueur terminal historique `ET` demeure distinct. Les familles statistiques et
compositions ont été représentées par les déclarations locales 404 prévues ; l'import est resté à
zéro appel fournisseur.

```text
OWNER_FINAL_VALIDATION=PASS
V14_PROVIDER_SCHEMA_SCOPE=EVENT_16809018_LOCAL_JSON_IMPORT
V14_OBSERVATION_567=COMPLETE_24_INCIDENTS_86_OF_86
V14_OBSERVATION_570=COMPLETE_28_INCIDENTS_99_OF_99
V14_EXTRA_TIME_LIVE_SEMANTICS=QUALIFIED
WORK_ORDER_STATUS=VALIDATED
WORK_ORDER_CLOSURE=COMPLETED
WORK_ORDER_LOCATION=docs/work_orders/completed
```

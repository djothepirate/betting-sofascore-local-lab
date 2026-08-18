# J5 — Qualification réelle V6 et correction V7 des remplacements sur blessure

## 1. Statut

```text
DATE=2026-08-17
SCOPE=EVENT_INCIDENTS_FOOTBALL
V6_REAL_RETEST=PASS_LENS_PSG
V7_IMPLEMENTATION=PASS_OFFLINE
CURRENT_PARSER=event-incidents-v7
FLYWAY_VERSION=14
STANDARD_TESTS=330
STANDARD_FAILURES=0
STANDARD_ERRORS=0
STANDARD_SKIPPED=0
INTEGRATION_TESTS=28
INTEGRATION_FAILURES=0
INTEGRATION_ERRORS=0
INTEGRATION_SKIPPED=0
REAL_PROVIDER_CALLS_BY_AGENT=0
PROVIDER_SCHEMA_VALIDATED=NO
HUMAN_V7_RETEST=REQUIRED
```

Cette unité reste `EXPERIMENTAL`, `LOCAL_ONLY`, `NOT_PRODUCTION_APPROVED` et
`NO_CRITICAL_DEPENDENCY`. Les payloads réels ont été fournis par l'opérateur ; aucun test standard
ou d'intégration ne contacte SofaScore et aucun payload fournisseur complet n'est ajouté au dépôt.

## 2. Preuve réelle V6 — Lens contre Paris Saint-Germain

Le retest humain du parseur `event-incidents-v6` a terminé les trois familles dans l'ordre prévu,
sans retry :

| Famille | Snapshot | Résultat |
|---|---:|---|
| `EVENT_STATISTICS` | 64 | `COMPLETE · 100%` |
| `EVENT_INCIDENTS` | 65 | `COMPLETE · 100%` |
| `EVENT_LINEUPS` | 69 | `COMPLETE · 100%` |

Le rendu incidents vérifie notamment les marqueurs `HT` et `FT`, trois minutes de temps
additionnel en première période, cinq en seconde période, ainsi que le carton jaune de
l'entraîneur lensois Dino Toppmöller avec le motif `Argument`. Les remplacements, cartons, but,
passeur et scores applicables sont également affichés. La campagne termine en
`COMPLETED_LOCKED` après exactement trois appels fournisseur.

Le réemploi des snapshots 64 et 65 résulte de la déduplication du brut. Il crée de nouvelles
observations append-only V6 et ne modifie aucune classification historique.

## 3. Preuve réelle V6 — Arsenal contre Manchester City

La campagne humaine suivante a produit :

| Étape | Snapshot | Résultat |
|---|---:|---|
| `EVENT_STATISTICS` | 70 | parsé, `COMPLETE · 100%` |
| `EVENT_INCIDENTS` | 71 | brut HTTP `200` persisté, puis `SCHEMA_INCOMPATIBLE` en V6 |
| `EVENT_LINEUPS` | — | non tenté après le verrou terminal |

Le snapshot incidents contient 61 763 octets. Conformément au contrat sans retry, la campagne
s'est arrêtée après exactement deux appels fournisseur. Le succès des statistiques n'a pas été
annulé et le brut incidents est resté disponible pour une inspection locale.

L'analyse hors ligne du document complet dénombre 23 incidents. V6 en accepte 22. Le seul objet
rejeté est un remplacement à la minute 46 :

```text
incidentType=substitution
incidentClass=injury
injury=true
isHome=false
playerIn=Jack Grealish
playerOut=Jérémy Doku
```

L'opérateur confirme qu'il s'agit du remplacement de Jérémy Doku par Jack Grealish en raison d'une
blessure. La cause n'est donc ni un type inconnu, ni une minute atypique, ni la sentinelle
`addedTime=999` : V6 limitait artificiellement la classe d'une substitution à `regular`.

## 4. Contrat correctif `event-incidents-v7`

V7 hérite de l'intégralité des règles V6 et étend uniquement le vocabulaire d'une substitution :

1. `incidentClass="regular"` reste accepté sans changement ;
2. `incidentClass="injury"` est accepté et conservé ;
3. `incidentClass="injury"` avec `injury=true` est cohérent ;
4. `incidentClass="injury"` avec `injury=false` reste une contradiction atomique et rend la famille
   `SCHEMA_INCOMPATIBLE` ;
5. si `injury` est absent, l'incident est conservé avec une complétude `PARTIAL` sans valeur
   inventée ;
6. `playerIn` et `playerOut` conservent les règles V6 d'identité et de complétude.

Le document complet Arsenal — Manchester City fourni par l'opérateur a été injecté temporairement
dans un test local hors ligne : V7 le classe `PARSED` et conserve les 23 incidents sur 23. Cette
preuve temporaire a ensuite été retirée ; le payload réel n'est pas versionné. Une régression de
service distincte démontre qu'après cette normalisation, l'appel unique `EVENT_LINEUPS` est bien
exécuté et que la campagne peut atteindre ses trois familles ordonnées.

## 5. Persistance append-only

`V14__j5_incident_injury_substitution_class.sql` autorise la provenance
`event-incidents-v7` sans ajouter de colonne métier, sans modifier V1–V13 et sans réécrire les
observations ou incidents V6. Le test d'upgrade V13 → V14 démontre simultanément :

- la conservation octet pour octet d'une observation V6 historique et de ses incidents ;
- l'acceptation d'une nouvelle observation V7 contenant la classe `injury`, le booléen `true`,
  Jack Grealish comme entrant et Jérémy Doku comme sortant ;
- la version finale Flyway 14 sur installation neuve.

## 6. Commandes et résultats

Les validations ont été exécutées avec Java 25.0.4 :

```text
COMMAND=.\mvnw.cmd -q "-Dtest=EventIncidentsV6ParserTest,EventIncidentsV7ParserTest,J5RealEventDataServiceTest" test
RESULT=BUILD_SUCCESS

COMMAND=.\mvnw.cmd clean verify
RESULT=BUILD_SUCCESS
TESTS=330
FAILURES=0
ERRORS=0
SKIPPED=0
REAL_PROVIDER_CALLS=0

COMMAND=.\mvnw.cmd -Pintegration-tests verify
RESULT=BUILD_SUCCESS
INTEGRATION_TESTS=28
FAILURES=0
ERRORS=0
SKIPPED=0
FLYWAY_FRESH_INSTALL=V1_TO_V14_PASS
FLYWAY_UPGRADE=V13_TO_V14_PASS
REAL_PROVIDER_CALLS=0
```

Les avertissements Java concernent l'auto-attachement Mockito/Byte Buddy ; ils ne constituent ni
un échec de test ni un appel fournisseur.

## 7. Suite humaine requise

La qualification réelle du parseur V7 reste à effectuer. Elle exige un redémarrage de
l'application pour charger V7/V14, une préparation sans transport, une nouvelle phrase de
confirmation, un nouvel acquittement et une action finale distincte. Le résultat attendu pour la
preuve Arsenal — Manchester City est une campagne de trois appels ordonnés atteignant
`EVENT_LINEUPS`.

L'agent n'a ni lu ni modifié `.env`, n'a effectué aucun appel fournisseur et n'a reproduit dans ce
rapport ni phrase ponctuelle, ni token, ni payload complet.

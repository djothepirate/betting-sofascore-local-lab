# J5 — Correction V10 de l'origine redondante d'un but régulier

> `EXPERIMENTAL` · `LOCAL_ONLY` · `NOT_PRODUCTION_APPROVED` · `NO_CRITICAL_DEPENDENCY`

## 1. Résultat actuel

```text
DATE=2026-08-18
PROVIDER_EVENT_ID=16251993
V9_CAMPAIGN_TERMINAL=FAILED_LOCKED
V9_PROVIDER_CALLS=2
V9_RETRY=0
V9_STATISTICS=SNAPSHOT_178_OBSERVATION_91_COMPLETE_4_OF_4
V9_INCIDENTS=SNAPSHOT_179_HTTP_200_SCHEMA_INCOMPATIBLE
V9_LINEUPS=NOT_ATTEMPTED
V9_INCIDENT_PARSER=event-incidents-v9
V9_BLOCKING_PATHS=5_REGULAR_GOAL_FROM_FIELDS
V9_BLOCKING_TUPLE=incidentClass_regular_FROM_regular
V10_PARSER=event-incidents-v10
V10_FLYWAY_VERSION=17
V10_FULL_OPERATOR_PAYLOAD=PASS_OFFLINE_7_OF_7
V10_COMPLETENESS=COMPLETE_24_OF_24
V10_WARNINGS=7
V10_PROBLEMS=0
V10_FLYWAY_UPGRADE=V16_TO_V17_PASS
V10_STANDARD_TESTS=348_PASS
V10_INTEGRATION_TESTS=31_PASS
REAL_PROVIDER_CALLS_BY_AGENT=0
V10_HUMAN_RETEST=PASS_UNDER_V11_EVENT_16251993
CURRENT_PARSER=event-incidents-v13
CURRENT_FLYWAY_VERSION=20
CURRENT_STANDARD_TESTS=369_PASS
CURRENT_INTEGRATION_TESTS=35_PASS
PROVIDER_SCHEMA_VALIDATED=YES
PROVIDER_SCHEMA_VALIDATION_SCOPE=V13_EVENTS_16691018_AND_16851672
OFF_THE_BALL_CARD_REAL_QUALIFICATION=PASS_EVENT_16671566_SNAPSHOT_185
COMBINED_J4_J5_SESSION=PASS_REAL_EVENT_16671566
LOCAL_CONFIGURATION_RELOCKED=YES_OPERATOR_EVIDENCE
CURRENT_APPLICATION_LISTENER=ABSENT_127_0_0_1_8087
FINAL_APPLICATION_STOPPED_AFTER_RELOCK=PASS
WORK_ORDER_ARCHIVABLE=YES
```

Le correctif V10 est implémenté et son retest humain est concluant sous le parseur courant V11. La
nouvelle valeur de motif de carton qui a conduit entre-temps à V11 est elle aussi qualifiée depuis
sur l'événement `16671566`, tout comme le parcours combiné J4→J5.

## 2. Preuves opérateur traitées

Les trois captures reçues après une nouvelle campagne établissent :

- campagne de l'événement fournisseur `16251993` arrêtée à `FAILED_LOCKED` ;
- exactement deux appels fournisseur et aucun retry ;
- statistiques snapshot 178, 435 octets, observation 91, `event-statistics-v2`,
  `COMPLETE · 100% · 4/4` ;
- incidents snapshot 179, HTTP `200`, 6 423 octets, conservé avant parsing puis classé
  `SCHEMA_INCOMPATIBLE` par `event-incidents-v9` ;
- absence d'observation incidents et absence de composition locale, puisque le troisième appel
  n'a pas été tenté ;
- arrêt conforme au premier incident réel.

Le snapshot statistiques porte le SHA-256 brut
`6e4f31d999463b5f442835dcfe098390e312ff0161e359dcb601598342291c77` et le SHA-256 normalisé
`63742706657c3cb7cb9f50b11d831545f5ce4494e819bbc51ed6900419cdf9ff`.
Le snapshot incidents porte le SHA-256 brut
`6ca4d7e8dc7e1409007e465b1541fa517c338510857a974fd4b1e57b9fb70585`.

Les captures restent hors dépôt. Leur inventaire minimisé est :

| Preuve | Taille | SHA-256 |
|---|---:|---|
| Campagne verrouillée et statistiques | 143 629 octets | `5E6B799664A60CCA84FF8A052EC1FFB024F839506360A58E4E1CB5C4ED458488` |
| Statistiques et absences incidents/compositions | 97 176 octets | `4002CAFC5023B277D6262AB72D7BE14A3D3CC5D9C04877EE41AA35B722303EDD` |
| Inspection locale du snapshot 179 | 125 834 octets | `D56A816E7A281968682462458D3E128200CC8B238A3157B9C3E07C35297A4C83` |

L'ensemble représente 366 639 octets. Aucune phrase de confirmation, aucun contenu `.env` et
aucun payload complet ne sont reproduits dans ce rapport.

## 3. Diagnostic exact

La transcription JSON fournie hors dépôt contient sept incidents : deux marqueurs de période et
cinq buts. Elle ne contient ni carton, ni penalty en jeu, ni séance de tirs au but. Les corrections
V8 et V9 ne sont donc pas en cause.

Les cinq buts portent tous le tuple suivant :

```text
incidentType=goal
incidentClass=regular
from=regular
```

V8 avait déjà introduit une règle bornée pour le marqueur fournisseur `from="shot"` sur un but
`regular` : le brut est conservé, mais la valeur n'est pas interprétée comme l'origine spéciale
d'un but. V9 héritait de cette règle, sans connaître la nouvelle valeur redondante
`from="regular"`. La validation de l'origine spéciale la rejetait donc sur cinq chemins :

```text
$.incidents[1].from
$.incidents[2].from
$.incidents[4].from
$.incidents[5].from
$.incidents[6].from
```

Le comportement transactionnel est conforme : aucun objet incidents partiel n'a été persisté,
le brut est disponible pour inspection, la campagne s'est verrouillée et `EVENT_LINEUPS` n'a pas
été appelé.

## 4. Contrat fermé V10

`event-incidents-v10` hérite intégralement de V9. Il ajoute une seule règle :

- lorsque `incidentType="goal"`, `incidentClass="regular"` et `from="regular"` sont présents
  ensemble, la valeur `from` est conservée dans le snapshot brut et omise de l'origine spéciale
  normalisée ;
- un avertissement `PROVIDER_REGULAR_GOAL_ORIGIN_OMITTED` indique cette neutralisation explicite ;
- `from="regular"` avec `incidentClass="penalty"` ou `incidentClass="ownGoal"` reste incompatible ;
- une autre valeur future sur un but régulier reste incompatible ;
- les origines spéciales `penalty` et `ownGoal`, ainsi que l'alias brut historique `owngoal`,
  conservent leurs règles existantes.

Cette évolution n'ajoute aucune colonne et ne transforme pas `regular` en information métier
nouvelle : la colonne DÉTAIL reste vide pour ces buts ordinaires.

V10 conserve également les contrats précédents, notamment :

- les remplacements sur blessure V7 ;
- le marqueur terminal exact `PEN/time=999` et les deux variantes `Woodwork/woodwork` V8 ;
- les cartons sans motif et les buts réguliers `from="shot"` V8 ;
- le carton `Other reason` et son `benchAddedTime` V9.

## 5. Rejeu exact du payload opérateur

Un test JUnit ponctuel, retiré après exécution, a lu la transcription depuis la pièce locale sans
transport. La transcription fait 7 616 octets et porte le SHA-256
`2D3D100A25F3850DF23327228BCB949D0EBDC33B3EBEBF16431E01174B21CB23`.
Cette empreinte identifie le fichier texte reçu, et non la sérialisation HTTP brute du snapshot
179.

Le rejeu comparatif confirme :

| Parseur | Statut | Incidents | Complétude | Problèmes |
|---|---|---:|---:|---:|
| V9 | `SCHEMA_INCOMPATIBLE` | aucun objet persistable | — | 5 chemins `from` |
| V10 | `PARSED` | 7/7 | `COMPLETE · 100% · 24/24` | 0 |

V10 produit sept avertissements attendus et bornés : deux sentinelles `addedTime=999` sur les
marqueurs de période, puis cinq neutralisations explicites de `from="regular"`. Les cinq buts
conservent leur classe `regular`, leur buteur, leur côté et leur score ; leur origine spéciale
normalisée reste absente.

Le test ponctuel n'est pas versionné. Les tests permanents utilisent uniquement des formes
synthétiques minimales et vérifient le tuple positif, les combinaisons croisées, les valeurs
futures inconnues et les contrats V8/V9 hérités.

## 6. Service, persistance et migration

La livraison V10 configurait alors le service réel borné avec `event-incidents-v10` pour le
snapshot brut comme pour la provenance de l'observation normalisée. Une régression de service reproduit le but
`regular/from=regular`, vérifie la poursuite jusqu'à `EVENT_LINEUPS`, les trois appels exacts et
l'absence de retry.

`V17__j5_regular_goal_origin.sql` est append-only. Elle remplace uniquement la contrainte de
vocabulaire du parseur afin d'autoriser `event-incidents-v10`, tout en conservant V1 à V9 et les
normaliseurs d'indisponibilité.

Le test d'upgrade V16 → V17 :

- crée une observation V9 et son carton avant migration ;
- confirme que V10 est refusé sous V16 ;
- applique exactement une migration ;
- vérifie l'égalité de tous les champs relus de l'observation et de l'incident V9 ;
- persiste puis relit une observation V10 contenant un but régulier sans origine spéciale
  normalisée.

## 7. Vérifications exécutées

```text
COMMAND=.\mvnw.cmd -q "-Dtest=EventIncidentsV10ParserTest,EventIncidentsV9ParserTest,J5RealEventDataServiceTest" test
RESULT=PASS

COMMAND=.\mvnw.cmd -q "-Dtest=EventIncidentsV10OperatorReplayTest" "-Dj5.operator.payload=<PIECE_LOCALE>" test
RESULT=PASS_7_OF_7_COMPLETE_24_OF_24
NOTE=TEMPORARY_TEST_REMOVED_AFTER_REPLAY

COMMAND=.\mvnw.cmd -q -Pintegration-tests "-Dit.test=FlywayMigrationIT#upgradesV16WithTheObservedRegularGoalOriginWithoutRewritingV9History" verify
RESULT=PASS

COMMAND=.\mvnw.cmd clean verify
RESULT=PASS_348_STANDARD_TESTS

COMMAND=.\mvnw.cmd -q -Pintegration-tests verify
RESULT=PASS_348_STANDARD_PLUS_31_INTEGRATION_TESTS

MAVEN_PROVIDER_CALLS=0
```

La première exécution complète d'intégration a signalé deux assertions historiques encore fixées
à V16. Elles ont été corrigées pour attendre V17 et pour borner explicitement le scénario
historique V15 → V16 à sa version cible. Le second passage complet est vert.

## 8. Retest humain V10 initialement attendu

Au stade V10, l'échec V9 était `FAILED_LOCKED`; la séquence prévue demandait un redémarrage avec
V17 avant de préparer une nouvelle campagne et devait établir :

1. préparation locale sans transport pour l'identité liée à `16251993` ;
2. confirmation humaine distincte ;
3. exactement trois appels ordonnés, sans retry ;
4. statistiques compatibles ou indisponibles selon la réponse courante ;
5. incidents traités par `event-incidents-v10`, avec sept incidents et `COMPLETE · 100%` si le
   snapshot 179 est réutilisé ;
6. cinq buts réguliers visibles sans valeur inventée dans l'origine spéciale ;
7. appel effectif aux compositions et état terminal `COMPLETED_LOCKED`.

Un snapshot brut identique peut être dédupliqué vers 179. Sa classification historique V9 ne doit
pas être réécrite ; la réussite V10 doit apparaître dans une nouvelle observation append-only.

La clôture ultérieure a arrêté la session réelle, remis la configuration à l'état bloqué, confirmé
J4 et J5 `LOCKED` après redémarrage puis effectué l'arrêt final. Le contrôle du port
`127.0.0.1:8087` est négatif et `WORK_ORDER_ARCHIVABLE=YES` est désormais la décision courante.

## 9. Observation ultérieure et parseur courant V11

Avant l'exécution de ce retest, l'opérateur a fourni un carton `yellow` à la minute 77 portant
`reason="Off the ball foul"`, motif correspondant à une obstruction ou faute loin du ballon. V10
rejette cette forme exactement sur `reason`. V11 ajoute uniquement cette valeur, la conserve telle
quelle dans le motif et maintient le rejet de toute autre valeur inconnue.

La structure fournie passe hors ligne à `COMPLETE · 100% · 3/3`, le service atteint les
compositions après trois transports sans retry et la migration V17 → V18 préserve une observation
V10. Les suites de cette étape totalisent 358 tests standards et 32 tests d'intégration réussis. Le
rapport V11 est
`docs/validation/J5-REAL-V11-OFF-BALL-CARD-REASON-20260818.md`.

## 10. Retest reçu sous le parseur V11

La campagne Al Tai — Al-Qadsiah, identifiant `16251993`, termine `COMPLETED_LOCKED` après trois
appels sans retry. Le snapshot incidents 179 est reparsé sous `event-incidents-v11` dans
l'observation append-only 93 à `COMPLETE · 100% · 24/24`. Les cinq buts réguliers auparavant
bloquants sont visibles sans origine spéciale inventée, puis les compositions sont atteintes dans
le snapshot 182 et l'observation 94 à `COMPLETE · 67/67`.

Cette preuve clôt le retest fonctionnel du correctif V10. Elle ne contient pas de carton
`Off the ball foul`, mais la campagne ultérieure Barracas Central — Rosario Central (`16671566`)
qualifie ce motif dans le snapshot 185 et enchaîne J4 phase 2 puis J5 sans redémarrage. Le Work
Order restait alors actif uniquement pour le reverrouillage local, sa vérification après
redémarrage et l'arrêt final de l'instance de contrôle ; ces preuves sont désormais acquises.

## 11. Évolution postérieure V12

La campagne `16691018` a ensuite rejeté sous V11 une séance terminale entièrement non minutée et
ramené le statut fournisseur courant à `NO`. V12/V19 conserve ces minutes absentes sans les
déduire de la séquence ; le payload complet passe hors ligne avec 33 incidents et les suites
courantes passent 366 tests standards plus 34 tests d'intégration. Le retest réel V12 précède
désormais le reverrouillage. Voir
`docs/validation/J5-REAL-V12-UNMINUTED-SHOOTOUT-20260818.md`.

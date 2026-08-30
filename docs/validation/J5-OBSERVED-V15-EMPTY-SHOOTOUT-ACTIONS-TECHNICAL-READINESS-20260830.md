# J5 — Readiness technique V15 des tableaux d'actions vides

## 1. Statut

```text
WORK_ORDER=WO-SS-20260830-017
WORK_ORDER_STATUS=HUMAN_QUALIFICATION_PASS_PENDING_OWNER_CLOSURE
CURRENT_INCIDENT_PARSER=event-incidents-v15
CURRENT_FLYWAY_VERSION=28
TECHNICAL_READINESS=PASS
OWNER_FUNCTIONAL_QUALIFICATION=PASS_BY_OWNER_AUTHORIZED_CODEX_EXECUTION_2026_08_30
PROVIDER_SCHEMA_CURRENT_STATUS=VALIDATED_BOUNDED_EVENT_16691018
EXACT_STORED_RESPONSE_READ_ONLY_PROBE=PARSED_PARTIAL_91
EXACT_STORED_RESPONSE_PERSISTED_REPARSE=NO
REAL_PROVIDER_CALLS_DURING_IMPLEMENTATION=0
REAL_PROVIDER_CALLS_DURING_AUTOMATED_TESTS=0
REAL_PROVIDER_CALLS_DURING_AUTHORIZED_QUALIFICATION=3
QUALIFICATION_CAMPAIGN_TERMINAL_STATE=COMPLETED_LOCKED
EMPTY_ARRAY_VARIANT_REOBSERVED=YES_BY_BYTE_IDENTICAL_SNAPSHOT_REUSE
RETRY_OF_J8_CAMPAIGN=NO
SECOND_J8_BENCHMARK_CAMPAIGN=NO
V15_QUALIFICATION_J5_LEDGER_CAMPAIGN=CREATED_AS_REQUIRED
ORIGINAL_J8_EXCLUSIVE_WINDOW_UNCHANGED=YES
J8_HISTORICAL_RESULT=EVENT_INCIDENTS_V14_SCHEMA_INCOMPATIBLE_UNCHANGED
OWNER_CLOSURE_DECISION=NOT_GRANTED
ADDITIONAL_PROVIDER_CALL_AUTHORIZED=NO
```

Les sections 2 à 9 conservent la photographie de la readiness technique obtenue après la campagne
J8 partielle : elle ne constituait alors ni une qualification fournisseur, ni un reparse durable
du snapshot 717, ni une autorisation de reprendre la campagne. La section 10 consigne séparément
la qualification corrective ultérieure autorisée ; elle ne réécrit pas cette preuve initiale.

Les statuts du laboratoire restent :

```text
EXPERIMENTAL
LOCAL_ONLY
NOT_PRODUCTION_APPROVED
NO_CRITICAL_DEPENDENCY
```

## 2. Gouvernance et périmètre

La revue de l'ADR-SS-001 v1.4 conclut qu'aucun amendement n'est requis. Le correctif intervient
après la frontière de transport et ne change aucun endpoint, origine, allowlist, paramètre de
requête, protocole IPC, worker Playwright, lease, temporisation, règle d'arrêt ou capacité réseau.

```text
ADR_SS_001_V1_4_REVIEW=COMPATIBLE_NO_CHANGE_REQUIRED
NEW_ENDPOINT=NO
ALLOWLIST_CHANGE=NO
PLAYWRIGHT_WORKER_CHANGE=NO
IPC_PROTOCOL_CHANGE=NO
TRANSPORT_CHANGE=NO
AUTOMATIC_POLLING=NO
SCHEDULER=NO
RETRY=NO
FALLBACK=NO
RAW_PROVIDER_PAYLOAD_COMMITTED=NO
HISTORICAL_EVIDENCE_REWRITTEN=NO
SERVER_ADDRESS=127.0.0.1
```

## 3. Contrat V15 figé

V15 hérite intégralement de V14. Il ne modifie qu'un prédicat utilisé par la reconnaissance V12
d'une séance terminale entièrement non minutée et déjà cohérente.

| Forme de `footballPassingNetworkAction` sur une tentative non minutée | V15 |
|---|---|
| propriété absente | absence auxiliaire admise dans le contexte terminal cohérent |
| tableau JSON exactement vide | même absence auxiliaire, V15 uniquement |
| mélange propriété absente / tableau vide entre tentatives | admis, car les deux formes sont équivalentes |
| JSON `null`, objet, chaîne, nombre ou booléen | rejeté |
| tableau non vide incohérent ou malformé | rejeté |
| tableau non vide portant une minute valide | règle historique de minute imbriquée, sans élargissement V15 |
| tentative effectivement minutée mêlée à une tentative non minutée | séance mixte rejetée |

L'exception reste conditionnée au marqueur `period/PEN/penalties` terminal exact, à un marqueur
`FT` ou `ET` terminé, aux classes et côtés typés, à des séquences uniques et contiguës et à un
score terminal cohérent. Aucune minute n'est déduite de la séquence, du score ou de l'ordre du
tableau. Les minutes normalisées restent absentes et la complétude reste `PARTIAL`.

## 4. Implémentation

- `EventIncidentsV12Parser` expose un point d'extension protégé dont le comportement historique
  reste « propriété absente seulement » ; V12, V13 et V14 ne sont pas élargis ;
- `EventIncidentsV15Parser` surcharge uniquement ce point pour accepter aussi un tableau vide ;
- les parcours fournisseur gardé, import JSON local unitaire et import multi-match partagé
  utilisent tous V15 et conservent la même version dans les preuves ;
- `J5EventDataObservation` accepte V15 tout en conservant toutes les versions historiques ;
- la fixture versionnée est synthétique et minimisée ; elle ne reproduit aucun payload fournisseur ;
- la migration append-only V28 remplace uniquement la contrainte fermée de version de parseur.

## 5. Preuves automatisées ciblées

La suite ciblée parseurs et services couvre les formes positives et négatives, le routage direct
simulé sans réseau, les imports locaux et l'exclusivité de lease :

```text
TARGETED_UNIT_TESTS=76_PASS
TARGETED_UNIT_FAILURES=0
TARGETED_PROVIDER_CALLS=0
```

Elle prouve notamment : mêmes octets rejetés par V14 et acceptés par V15, forme historique absente
conservée, coexistence absent/`[]`, aucune minute inventée, SHA-256 des octets inchangé, rejet des
tableaux non vides incohérents, des cinq familles de types erronés, des séances mixtes, des gaps de
séquence et des scores contradictoires, ainsi que conservation des règles V13 et V14.

Le test Testcontainers ciblé d'upgrade V27→V28 est vert. Il refuse V15 à V27, applique exactement
une migration, accepte V15 à V28 et conserve une observation V14, son incident à minute nulle et
des lignes représentatives des cinq tables J8 sans réécriture.

```text
TARGETED_V27_TO_V28_INTEGRATION=PASS
TARGETED_POSTGRESQL_MUTATIONS_OF_EXISTING_V14_OR_J8_EVIDENCE=0
```

## 6. Sonde exacte du snapshot 717

Une sonde diagnostique temporaire sous `target/`, non versionnée, a ouvert une transaction
PostgreSQL explicitement read-only, sélectionné uniquement le snapshot 717, analysé ses octets
exacts avec V15, vérifié les comptes avant/après puis exécuté un rollback. Aucun payload, URI,
header, cookie ou jeton n'a été affiché ou copié.

```text
PROBE_CONNECTION_READ_ONLY=true
SNAPSHOT_ID=717
HISTORICAL_SCHEMA_STATUS=SCHEMA_INCOMPATIBLE
HISTORICAL_PARSER=event-incidents-v14
RAW_SHA256_MATCH=true
V15_RESULT=PARSED
V15_PROBLEM_COUNT=0
V15_WARNING_COUNT=18
V15_COMPLETENESS_STATUS=PARTIAL
V15_COMPLETENESS_SCORE=91
V15_INCIDENT_COUNT=35
OBSERVATION_COUNT_UNCHANGED=true
J8_RESULT_COUNT_UNCHANGED=true
PERSISTED_REPARSE=NO
```

Cette preuve qualifie le comportement technique de V15 sur la réponse déjà conservée. Elle ne
reclasse pas le snapshot et ne promeut pas la campagne J8 en succès.

## 7. Persistance et sauvegarde

`V28__j5_incidents_empty_shootout_action.sql` ne contient ni DML, ni nouvelle table, ni colonne,
ni backfill. Elle conserve V1 à V14 et ajoute seulement `event-incidents-v15` à
`ck_j5_event_data_parser`. Les contraintes temporelles V19 couvrent déjà les minutes nulles de la
séance terminale ; V21, V27 et leurs tables restent inchangées.

Les scripts J6 exigent désormais Flyway 28. Leur périmètre de tables, la purge des seuls payloads
et les fingerprints restent identiques, car V28 n'ajoute aucune donnée persistante.

## 8. Portes finales

```text
STANDARD_MAVEN_GATE=PASS_928_TESTS_4_SKIPPED
POSTGRESQL_MAVEN_GATE=PASS_67_TESTS
VERIFY_LOCAL_GATE=PASS
COMPOSE_CONFIG_GATE=PASS
GIT_DIFF_CHECK=PASS
NETWORK_FLAGS_DEFAULT_FALSE=PASS
PORT_8087_LISTENER=ABSENT
APPLICATION_OR_PLAYWRIGHT_WORKER_PROCESS=ABSENT
SOFASCORE_NETWORK_CALLS_EXECUTED=NO
```

Les portes ont été exécutées après le dernier changement fonctionnel. La première exécution de la
suite d'intégration a détecté une assertion de test devenue obsolète : un import courant était
encore attendu en V14 alors que son routage est désormais V15. L'assertion a été alignée sur la
provenance courante, le scénario ciblé a été rejoué, puis la suite PostgreSQL complète et
`Verify-Local.ps1 -WithIntegrationTests` ont terminé sans échec. Cette correction de test ne change
ni une preuve historique V14, ni une donnée persistée.

## 9. Limite de la readiness technique initiale

À l'instant de la readiness technique, l'état était `READY_FOR_HUMAN_QUALIFICATION` et aucun appel
fournisseur n'avait été autorisé ou exécuté. Cette propriété reste vraie pour l'implémentation, les
tests et la sonde read-only. L'addendum suivant consigne séparément la campagne corrective
ultérieure, explicitement autorisée par le propriétaire.

## 10. Addendum — qualification fournisseur V15 du 2026-08-30

Le propriétaire a autorisé une seule campagne J5 corrective sur l'événement fournisseur
`16691018`, identité canonique `f4713f80-4769-3656-ba51-61d8ac1aa814`, puis a explicitement confié
son exécution intégrale à Codex sans modification de `.env` et sans geste manuel supplémentaire.

La campagne s'est terminée `COMPLETED_LOCKED` après exactement trois appels, dans l'ordre
`EVENT_STATISTICS`, `EVENT_INCIDENTS`, `EVENT_LINEUPS`, sans retry, fallback, import ou seconde
campagne. Les statistiques sont `COMPLETE · 100%` sur le snapshot `716`, observation `322`. Les
incidents sont parsés par `event-incidents-v15`, `PARTIAL · 91%`, `164/179` signaux et 35
incidents, sur le snapshot dédupliqué `717`, observation `324`. Les compositions ont bien été
atteintes et sont `PARTIAL · 99%` sur le snapshot `720`, observation `325`.

La réutilisation du snapshot brut `717` prouve que la variante exacte rejetée historiquement par
V14 a été réobservée. Une sonde structurelle read-only confirme quatorze tirs sans minute et
quatorze tableaux exactement vides, sans forme non vide ou mal typée. V15 les accepte sans
inventer de minute ; la classification V14 de la campagne J8 et le snapshot historique restent
immuables. Cette qualification valide le schéma fournisseur V15 uniquement dans le corpus borné
de l'événement `16691018`.

L'application a été arrêtée après le terminal. Les propriétés réseau n'ont existé que dans l'arbre
de processus du lanceur ; `.env` est demeuré inchangé et bloquant. Un redémarrage inerte a confirmé
J5 `LOCKED` et son bouton de préparation désactivé, puis l'application a été arrêtée à nouveau.
Aucune campagne supplémentaire, clôture, fusion ou décision J9 n'est autorisée par ce résultat.

La preuve prospective minimisée complète est
`docs/validation/J5-V15-PROVIDER-QUALIFICATION-20260830.md`.

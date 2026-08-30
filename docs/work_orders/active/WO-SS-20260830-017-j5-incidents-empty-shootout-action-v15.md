# WO-SS-20260830-017 — Incidents J5 V15, tableau d'action vide pendant une séance terminale

- **Statut :** `QUALIFICATION_EXECUTION_AUTHORIZED_NOT_RUN`
- **Date :** 2026-08-30
- **Date de démarrage :** 2026-08-30
- **Qualification propriétaire :** `AUTHORIZED_2026-08-30T07:46:03Z`
- **Clôture :** `ACTIVE`
- **Prérequis :** campagne J8 partielle et analyse bornée au commit `cfd536e`
- **Base locale :** `cfd536e`
- **Jalon :** correction J5 issue de la qualification J8
- **Branche :** `codex/j8-incidents-v15`
- **Demande propriétaire :** accepter une propriété absente ou un tableau vide uniquement dans
  une séance terminale de tirs au but déjà cohérente, tout en rejetant les listes non vides
  incohérentes, les types erronés et les séances temporellement mixtes
- **Preuve locale analysée :** snapshot incidents J8 `717`, sans copie du payload dans Git
- **Payload fournisseur ajouté à Git :** `NO`
- **Appel fournisseur pendant l'implémentation :** `NOT_AUTHORIZED`
- **Appel fournisseur pendant les tests automatisés :** `NOT_AUTHORIZED`
- **Nouvel endpoint, URI, allowlist ou transport :** `NO`
- **Polling, planification, live réseau, fallback ou retry :** `NOT_AUTHORIZED`
- **Modification de l'ADR-SS-001 :** `NOT_REQUIRED_AFTER_EXPLICIT_REVIEW`
- **Migration prévue :** `V28_J5_INCIDENTS_EMPTY_SHOOTOUT_ACTION_V15`

## 1. Objectif

Créer le parseur versionné `event-incidents-v15`, héritier de V14, afin de reconnaître la variante
JSON observée pendant la campagne J8 : pour une tentative de séance de tirs au but sans source de
minute, `footballPassingNetworkAction` peut être omis ou présent sous la forme exacte d'un tableau
vide.

Cette équivalence ne s'applique que si la réponse complète satisfait déjà toutes les protections
du contexte terminal V12 : marqueur `PEN` unique et inactif, période de jeu `FT` ou `ET` terminée,
séquences contiguës et uniques, tentatives exclusivement non minutées, classes fermées, scores et
identités de côté typés, puis score terminal égal à celui de la dernière tentative.

Les statuts du laboratoire restent :

```text
EXPERIMENTAL
LOCAL_ONLY
NOT_PRODUCTION_APPROVED
NO_CRITICAL_DEPENDENCY
```

## 2. Preuve et diagnostic faisant autorité

La preuve antérieure, snapshot `189`, omet la propriété sur les tentatives non minutées et reste
parseable par V14. La réponse J8, snapshot `717`, utilise un tableau vide sur quatorze tentatives
cohérentes et échoue sous V14. Une sonde locale en mémoire a montré que traiter exclusivement ces
tableaux vides comme absents suffit à obtenir `PARSED`, sans appel fournisseur ni persistance.

```text
OLD_REPRESENTATION=PROPERTY_ABSENT
NEW_OBSERVED_REPRESENTATION=EMPTY_ARRAY
V14_NEW_REPRESENTATION=SCHEMA_INCOMPATIBLE
BOUNDED_COUNTERFACTUAL=PARSED
J8_INSTRUMENTATION_CAUSALITY=NOT_SUPPORTED_BY_LOCAL_EVIDENCE
GLOBAL_PROVIDER_SCHEMA_CHANGE=NOT_CLAIMED
```

Le payload exact reste dans la base locale append-only. Les tests utiliseront uniquement une
fixture minimale synthétique, sans équipes, joueurs, URI, header, cookie, jeton ou donnée de
session provenant de la réponse réelle.

## 3. Revue ADR-SS-001 v1.4

Le correctif intervient après la réception du brut et ne modifie aucune capacité réseau. Les
endpoints J5, l'allowlist, le protocole worker Playwright v5, les requêtes, le transport, la
concurrence unitaire, la temporisation, l'arrêt terminal et l'absence de retry restent inchangés.

```text
ADR_SS_001_WO_017_REVIEW=COMPATIBLE_NO_CHANGE_REQUIRED
ADR_SS_001_MODIFICATION_REQUIRED=NO
NEW_ENDPOINT=NO
ALLOWLIST_CHANGE=NO
PLAYWRIGHT_PROTOCOL_CHANGE=NO
PROVIDER_CAMPAIGN_AUTHORIZED_AT_TECHNICAL_REVIEW=NO
```

## 4. Contrat fermé V15

Pour `footballPassingNetworkAction` sur une tentative candidate non minutée :

- propriété absente : admissible dans le contexte terminal cohérent ;
- tableau JSON vide : équivalent à l'absence dans ce même contexte ;
- `null`, objet, chaîne, nombre ou booléen : type erroné, non admissible ;
- tableau non vide : jamais assimilé à une absence ; les règles historiques de minute imbriquée
  restent applicables, et toute combinaison terminale incohérente reste refusée ;
- mélange de tentatives effectivement minutées et non minutées : séance mixte refusée ;
- les formes absente et tableau vide peuvent coexister entre tentatives, puisqu'elles portent la
  même sémantique d'absence de source auxiliaire.

V15 ne déduit aucune minute. Il conserve les warnings et la mesure de complétude V12 : la minute
reste absente, son rendu reste `—` et les chemins manquants restent mesurés.

## 5. Périmètre inclus

- point d'extension protégé dans V12 dont le comportement historique par défaut reste
  « propriété absente seulement » ;
- `EventIncidentsV15Parser` surchargeant uniquement cette politique avec
  « absente ou tableau vide » ;
- câblage V15 dans la campagne J5 directe, l'import JSON local et l'import multi-match partagé ;
- compatibilité de lecture des observations incidents V1 à V15 ;
- migration Flyway append-only V28 ajoutant V15 à la contrainte fermée ;
- fixture synthétique et tests positifs/négatifs ciblés ;
- tests de migration propre et upgrade V27 vers V28 ;
- mise à jour des exigences, de l'architecture, des runbooks, du README, du changelog et des
  preuves J8 concernées ;
- validation standard et PostgreSQL sans réseau.

## 6. Hors périmètre

- modification ou suppression des migrations V1 à V27 ;
- réécriture automatique d'une observation historique ;
- reparse persistant automatique du snapshot `717` ;
- nouvelle campagne J5, import opérateur ou appel fournisseur ;
- relance de la campagne bornée J8 ;
- assouplissement d'une autre structure incidents ;
- adoption J9, qualification humaine ou clôture automatique de J8 ;
- nouvel endpoint, URI, transport, retry, polling, scheduler ou mécanisme navigateur.

## 7. Critères d'acceptation

- [x] V14 continue de refuser la variante tableau vide ;
- [x] V15 accepte la forme historique avec propriété absente ;
- [x] V15 accepte le tableau vide dans une séance terminale cohérente ;
- [x] V15 accepte une séance cohérente combinant absence et tableau vide ;
- [x] V15 ne crée ni minute ni signal de complétude artificiel ;
- [x] une liste non vide incohérente reste refusée ;
- [x] `null`, objet, chaîne, nombre et booléen restent refusés ;
- [x] une séance mêlant tentative minutée et tentative non minutée reste refusée ;
- [x] les gaps, doublons, scores terminaux divergents et marqueurs invalides restent refusés ;
- [x] les règles V14 `Extra time` et V13 restent inchangées ;
- [x] les voies directe et import utilisent V15 ;
- [x] la lecture des versions historiques reste compatible ;
- [x] V28 est append-only et l'upgrade V27 vers V28 ne réécrit aucune donnée ;
- [x] `mvnw clean verify` est vert ;
- [x] `mvnw -Pintegration-tests verify` est vert ;
- [x] `Verify-Local.ps1 -WithIntegrationTests` est vert sans appel fournisseur ;
- [x] `docker compose --env-file .env config` et `git diff --check` sont verts ;
- [x] le Work Order reste actif jusqu'à une décision propriétaire séparée de qualification.

## 8. Livraison Git prévue

Les commits locaux seront découpés en :

1. ouverture du Work Order correctif ;
2. parseur V15 et migration V28 ;
3. câblage, tests et documentation ;
4. readiness technique.

Aucun push, aucune Pull Request ni aucune fusion dans `main` ne sont autorisés par ce Work Order.
Sa readiness technique n'autorisait aucune campagne ; l'autorisation propriétaire distincte et
bornée reçue ensuite est consignée en section 11.

## 9. Preuve locale exacte et non persistante

La réponse déjà conservée du snapshot `717` a été relue dans une transaction PostgreSQL read-only,
analysée en mémoire puis rollbackée. Le hash brut a été vérifié sans afficher ni versionner le
payload. Les comptes d'observations et de résultats J8 sont inchangés.

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

## 10. Portes techniques finales

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

Le correctif atteint la readiness technique mais ne qualifie pas encore un schéma fournisseur
courant. WO-017 reste donc dans `active`, avec `Clôture=ACTIVE`. Aucune reprise de J8 ni écriture de
reparse n'est autorisée.

## 11. Autorisation propriétaire distincte de qualification fournisseur V15

Le propriétaire a donné le go distinct le 2026-08-30 à `07:46:03Z`, après présentation de la
readiness. Cette décision autorise une seule campagne corrective J5 sur l'événement ayant révélé la
variante V15. Elle n'est ni un retry de la campagne J8, ni une seconde campagne benchmark.

```text
OWNER_GO_RECEIVED_AT=2026-08-30T07:46:03Z
OWNER_GO_SCOPE=ONE_J5_V15_PROVIDER_QUALIFICATION
TARGET_PROVIDER_EVENT_ID=16691018
TARGET_CANONICAL_EVENT_ID=f4713f80-4769-3656-ba51-61d8ac1aa814
ENDPOINT_ORDER=EVENT_STATISTICS,EVENT_INCIDENTS,EVENT_LINEUPS
MAX_PROVIDER_CALLS=3
MANUAL_UI_TRIGGER_REQUIRED_AT_INITIAL_GO=YES
AUTOMATED_BROWSER_TRIGGER_AT_INITIAL_GO=NO
J8_CAMPAIGN_RETRY=NO
J8_SECOND_BENCHMARK_CAMPAIGN=NO
PROVIDER_RETRY=NO
FALLBACK=NO
POLLING=NO
SCHEDULER=NO
PERSISTED_REPARSE=NO
```

Conformément au runbook, la configuration `.env` reste un geste manuel du propriétaire et le
lanceur Playwright n'exécute aucun GET. Après démarrage, l'opérateur doit préparer la campagne,
recopier la nouvelle phrase de confirmation et déclencher une seule fois la voie fournisseur. Au
premier terminal autre qu'un `404` de famille, les familles suivantes ne doivent pas être tentées.
Une fois la campagne terminale, ce go est consommé et n'autorise aucune répétition.

## 12. Instruction propriétaire d'exécution intégrale par Codex

Le propriétaire a ensuite demandé explicitement, le 2026-08-30 à `08:04:57Z`, de ne pas modifier
`.env` et de confier à Codex l'unique campagne autorisée sans geste manuel supplémentaire. Cette
instruction remplace uniquement la modalité d'exécution de la section 11 ; elle n'élargit ni la
cible, ni les endpoints, ni le plafond, ni le droit de reprise.

```text
OWNER_EXECUTION_OVERRIDE_RECEIVED_AT=2026-08-30T08:04:57Z
ENV_FILE_MUTATION=NO
PROCESS_SCOPED_NETWORK_CONFIGURATION=AUTHORIZED_FOR_THIS_LAUNCH_ONLY
CODEX_LOCAL_UI_CONTROL=AUTHORIZED_FOR_ONE_CAMPAIGN
MANUAL_UI_GESTURES_REQUIRED=NO_BY_EXPLICIT_OWNER_INSTRUCTION
TARGET_PROVIDER_EVENT_ID=16691018
MAX_PROVIDER_CALLS=3
PROVIDER_RETRY=NO
FALLBACK=NO
POLLING=NO
SECOND_CAMPAIGN=NO
```

L'activation métier et Playwright doit donc vivre seulement dans l'arbre de processus du lanceur.
La page contrôlée reste exclusivement `127.0.0.1`; aucun profil persistant, cookie, `storageState`,
HAR, trace, vidéo, capture ou téléchargement n'est autorisé. Codex doit vérifier l'identité locale,
préparer une fois, soumettre la confirmation fraîche une fois, attendre le terminal et s'arrêter au
premier incident prévu par le runbook.

L'instrumentation J8 créera normalement une campagne de ledger `J5_EVENT_DATA` distincte pour ce
parcours. Cela ne modifie pas la fenêtre ni les preuves de la campagne J8 gelée :

```text
ORIGINAL_J8_FROZEN_WINDOW_UNCHANGED=YES
ORIGINAL_J8_PROVIDER_CAMPAIGN_RETRY=NO
V15_QUALIFICATION_J5_LEDGER_CAMPAIGN=CREATED_AS_REQUIRED
```

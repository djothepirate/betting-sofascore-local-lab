# J8 — Preuve de campagne bornée du 2026-08-30

> EXPERIMENTAL · LOCAL_ONLY · NOT_PRODUCTION_APPROVED · NO_CRITICAL_DEPENDENCY

```text
J8_EVIDENCE_KIND=BOUNDED_PROVIDER_CAMPAIGN_AND_LOCAL_ANALYSIS
J8_MEASUREMENT_STATE=PARTIAL
J8_WORK_ORDER_STATUS=READY_FOR_HUMAN_QUALIFICATION
J8_WORK_ORDER_VALIDATED=NO
J8_OWNER_CLOSURE_DECISION=NOT_GRANTED
```

## 1. Références et autorisation

La readiness technique préalable reste la preuve immuable
`docs/validation/J8-TECHNICAL-READINESS-20260829.md`. Le propriétaire a ensuite donné, le
2026-08-30, le go distinct requis pour une seule campagne prospective bornée. Ce go a été consommé
par le parcours décrit ci-dessous ; il n'autorise ni retry, ni seconde campagne, ni correction de
parseur suivie d'un nouvel appel.

```text
J8_PROVIDER_CAMPAIGN_GO=GRANTED_BY_OWNER_2026_08_30
J8_PROVIDER_CAMPAIGN_GO_CONSUMED=YES
J8_IMPLEMENTATION_COMMIT=c162cd401d86419d2773df5cf16edc9c38e351f4
J8_FLYWAY_VERSION=27
J8_WINDOW_FROM=2026-08-30T03:39:12.086771Z
J8_WINDOW_TO=2026-08-30T04:32:04.339732Z
J8_WINDOW_SEMANTICS=[FROM,TO)
J8_MAX_DIRECT_ATTEMPTS=30
```

La sélection locale a retenu le dernier dossier J7 `HUMAN_VALIDATED` éligible, terminal et déjà
pourvu des cinq composants directs. Les caches J3 et tournoi correspondant à sa date étaient
expirés naturellement ; aucun cache n'a été supprimé, invalidé ou contourné.

```text
J8_TARGET_PROVIDER_EVENT_ID=16691018
J8_TARGET_CANONICAL_ID=f4713f80-4769-3656-ba51-61d8ac1aa814
J8_TARGET_LABEL=Cittadella — Atalanta U23
J8_TARGET_DATE=2026-08-15
J8_TARGET_PHASE_ID=15118
J8_TARGET_UNIQUE_TOURNAMENT_ID=824
```

## 2. Exécution du parcours borné

Les quatre campagnes ont été exécutées dans l'ordre prescrit. Les vingt unités déclarées et les
dix-neuf tentatives admises à la frontière de transport sont conservées dans le ledger V27. Une
unité J5 reste délibérément sans tentative après l'arrêt terminal ; elle n'est ni un appel ni un
zéro inventé.

| Étape | État terminal | Unités déclarées | Tentatives | Résultat minimisé |
|---|---|---:|---:|---|
| J3 `SCHEDULED_EVENTS` | `COMPLETED` | 15 | 15 | pages 1 à 15 parsées, `hasNextPage=false` sur la dernière |
| J3 découverte tournoi | `COMPLETED` | 1 | 1 | réponse parsée, 8 événements canoniques retenus |
| J4 phase 2 `EVENT_DETAILS` | `COMPLETED` | 1 | 1 | réponse parsée, latence 3039 ms |
| J5 `EVENT_DATA` | `FAILED` | 3 | 2 | statistiques parsées ; incidents incompatibles ; compositions non atteintes |
| **Total** | **parcours `PARTIAL`** | **20** | **19** | plafond de 30 respecté |

Le détail J5 est exact et non rejouable dans cette campagne :

```text
J8_J5_TERMINAL_CODE=SCHEMA_INCOMPATIBLE
J8_J5_STATISTICS=PROVIDER_PARSED_COMPLETE
J8_J5_STATISTICS_LATENCY_MS=3012
J8_J5_INCIDENTS=PROVIDER_SCHEMA_INCOMPATIBLE
J8_J5_INCIDENTS_LATENCY_MS=95
J8_J5_LINEUPS=NOT_REACHED_AFTER_TERMINAL_FAILURE
J8_J5_LINEUPS_ATTEMPT=NO
J8_RETRY_EXECUTED=NO
J8_SECOND_CAMPAIGN_AUTHORIZED=NO
```

La séparation minimale entre les deux tentatives J5 observées est de 3174,491 ms. La concurrence
est restée à un, le contexte Playwright était neuf et non persistant, et aucun profil, cookie,
`storageState`, HAR, trace, vidéo, capture ou téléchargement n'a été conservé.

## 3. Agrégats automatiques de la fenêtre

L'agrégation utilise exclusivement la strate `FULL_ATTEMPT_LEDGER` pour les taux exacts. Elle ne
mélange aucune occurrence `RESPONSE_ONLY` ni baseline historique à leurs dénominateurs.

```text
J8_REPORT_STATE=PARTIAL
J8_EVIDENCE_SCOPE=FULL_ATTEMPT_LEDGER
J8_CAMPAIGNS=4
J8_DECLARED_UNITS=20
J8_DIRECT_ATTEMPTS=19
J8_PROVIDER_RESPONSES=19
J8_PARSED_RESPONSES=18
J8_SCHEMA_INCOMPATIBLE_RESPONSES=1
J8_RESPONSE_RATE=19/19_100.00_PERCENT
J8_PARSING_COMPATIBILITY=18/19_94.74_PERCENT
J8_REFUSAL_RATE=0/19_0.00_PERCENT
J8_HTTP_404_RATE=0/19_0.00_PERCENT
J8_OPERATIONAL_ERROR_RATE_EXCLUDING_404=1/19_5.26_PERCENT
J8_DISCOVERY_OVERHEAD=16
J8_MARGINAL_DOSSIER_CALLS=3
J8_TARGETED_DOSSIERS=1
J8_EXPLOITABLE_DOSSIERS=0
J8_STRICTLY_COMPLETE_DOSSIERS=0
J8_MARGINAL_CALLS_PER_EXPLOITABLE_DOSSIER=NOT_MEASURED
J8_EFFECTIVE_CALLS_PER_EXPLOITABLE_DOSSIER=NOT_MEASURED
```

La seule observation J5 normalisée créée par la campagne est `EVENT_STATISTICS`, `COMPLETE`, à
100 %. L'incompatibilité incidents et la famille compositions non atteinte ne sont jamais
converties en complétude de 0 %. Le rapport observe également un `LATE_ENRICHMENT`, zéro
`LATE_CORRECTION`, et laisse les dimensions sans source de contrôle à `NOT_MEASURED`.

## 4. Export reproductible hors réseau

Après arrêt de l'application normale et reverrouillage des connecteurs, l'exporteur Spring non-Web
a été exécuté deux fois avec les mêmes bornes et le même `AsOf`. Il a forcé Playwright, les
connecteurs, le polling et le scheduling à `false` et a déclaré zéro appel fournisseur.

```text
J8_EXPORT_FROM=2026-08-30T03:39:12.086771Z
J8_EXPORT_TO=2026-08-30T04:32:04.339732Z
J8_EXPORT_AS_OF=2026-08-30T04:54:49.022483400Z
J8_EXPORT_BYTES=15201
J8_EXPORT_SHA256=d901790d1f05ddd32b92821bee51f11ae3e688ca3d929af36f667ac026b2934c
J8_EXPORT_POPULATION_HASH=da158fb04c8dc113a56e94e2bc7da6ad27278111af5cf8179b7476e5d8f1cc95
J8_EXPORT_BYTE_IDENTICAL=YES
J8_EXPORT_NETWORK_CALLS=0
```

Les deux fichiers restent sous `exports/j8/`, sont ignorés par Git et ne constituent pas un rapport
final accepté. Aucun fichier n'est créé sous `docs/benchmark/` avant la revue humaine ciblée.

## 5. Analyse de l'incompatibilité incidents

L'échec est une régression fonctionnelle de disponibilité J5 par rapport au dossier local
préexistant, mais les preuves locales n'établissent pas une causalité de l'instrumentation J8.

### 5.1 Comparaison bornée

- preuve antérieure : snapshot incidents 189, 40 412 octets, observation normalisée 100,
  `event-incidents-v13`, `PARTIAL` 83 %, 33 incidents ;
- le parseur alors utilisé, `event-incidents-v14`, accepte hors ligne, sans persistance, exactement
  les octets du snapshot 189 ;
- réponse J8 : snapshot incidents 717, 43 363 octets, `event-incidents-v14`,
  `SCHEMA_INCOMPATIBLE`, sans observation normalisée ;
- la réponse J8 produit quinze problèmes : quatorze `REQUIRED_FIELD_MISSING` sur la minute de la
  première action imbriquée des tirs au but, puis un `VALUE_OUT_OF_RANGE` sur le marqueur terminal
  `PEN` qui n'est plus reconnu dans son contexte spécial.

Dans l'ancienne représentation, les quatorze tirs au but sans minute omettent
`footballPassingNetworkAction`. Dans la nouvelle réponse observée, la propriété est présente avec
un tableau vide. La règle héritée de V12 à V14 exigeait alors que cette propriété soit
strictement absente pour reconnaître une séance terminale cohérente non minutée.

Une sonde contrefactuelle locale, en lecture seule et sans reparse persistant, confirme cette cause
bornée :

```text
J8_FAILURE_BYTES_V14=SCHEMA_INCOMPATIBLE_15_PROBLEMS
J8_FAILURE_BYTES_WITH_EMPTY_ACTION_ARRAYS_TREATED_AS_ABSENT=PARSED_PARTIAL_91_PERCENT
J8_COUNTERFACTUAL_PERSISTED=NO
```

Cette sonde ne modifie pas le snapshot et ne promeut pas son résultat au rang de preuve directe.
Elle montre uniquement que la différence « propriété absente » / « tableau vide » suffit à
expliquer l'issue observée sous V14.

### 5.2 Audit de causalité J8

Le diff entre la baseline `67268d8` et le commit de campagne ne modifie ni
`EventIncidentsV14Parser`, ni ses règles héritées, ni le worker Playwright, ni le protocole IPC, ni
le transport de réponse. Le chemin J8 ajoute une écriture d'audit avant l'appel, puis capture après
réception le statut, la latence et les références de persistance. Le parseur reçoit toujours
directement les mêmes octets immuables que ceux remis par le transport ; aucune API d'audit ne peut
ajouter ou vider un champ JSON.

La seule influence théorique de J8 est un faible décalage temporel local avant l'appel. Il peut
faire observer une représentation fournisseur plus récente, mais ne peut pas transformer la
réponse reçue. La preuve est donc limitée à cette réponse : elle ne permet pas d'affirmer que le
schéma fournisseur global a changé.

```text
J5_END_TO_END_AVAILABILITY_REGRESSION=OBSERVED
J5_J8_INSTRUMENTATION_CAUSALITY=NOT_SUPPORTED_BY_LOCAL_EVIDENCE
J5_OBSERVED_RESPONSE_VARIANT=EMPTY_FOOTBALL_PASSING_NETWORK_ACTION_ARRAY
J5_PARSER_GAP_AT_J8_FREEZE=PRESENT_EMPTY_ARRAY_REJECTED_IN_COHERENT_TERMINAL_SHOOTOUT
J5_PROVIDER_SCHEMA_STATUS_AT_J8_FREEZE=NOT_VALIDATED_AFTER_SNAPSHOT_717
J5_PROVIDER_SCHEMA_CURRENT_STATUS=VALIDATED_BOUNDED_V15_EVENT_16691018_BY_WO_017
```

À ce stade de la preuve J8, un correctif devait créer une nouvelle version de parseur et traiter
« absent » et « tableau vide » comme équivalents uniquement dans le contexte terminal cohérent déjà
protégé, tout en continuant à refuser les listes non vides incohérentes, les valeurs mal typées et
les séances mixtes. Ce travail, sa migration append-only, ses tests et toute nouvelle campagne
exigeaient un Work Order et une décision propriétaire séparés. Aucun correctif n'a été appliqué par
la campagne ou la preuve J8 initiales ; WO-017 est documenté séparément dans les addenda suivants.

### 5.3 Addendum du 2026-08-30 — readiness technique V15

Après gel de la présente preuve historique, WO-017 a ajouté le parseur versionné
`event-incidents-v15` et la migration append-only V28. V15 traite propriété absente et tableau
exactement vide comme équivalents uniquement dans la séance terminale non minutée déjà cohérente ;
les tableaux non vides incohérents, les types erronés et les séances mêlant tentatives réellement
minutées et non minutées restent incompatibles.

Une sonde locale en transaction PostgreSQL read-only a analysé les octets exacts du snapshot 717
sans les afficher ni les copier dans Git : SHA-256 identique, `PARSED`, zéro problème, 18 warnings,
`PARTIAL · 91%` et 35 incidents sous V15. Elle s'est terminée par rollback et n'a créé ni
observation, ni occurrence, ni résultat J8. Le snapshot brut 717 reste immuable et partagé ;
l'unité historique de campagne qui le référence reste `event-incidents-v14 /
SCHEMA_INCOMPATIBLE`; l'issue `PARTIAL`, les 19 tentatives et le hash du
rapport restent inchangés. Cette readiness n'autorise ni reparse persistant, ni retry, ni seconde
campagne fournisseur.

### 5.4 Addendum du 2026-08-30 — qualification fournisseur V15 distincte

Après cette readiness, le propriétaire a autorisé puis confié à Codex une unique campagne J5
corrective sur l'événement `16691018`. Cette campagne est postérieure et extérieure à la fenêtre
exclusive J8 `[2026-08-30T03:39:12.086771Z,2026-08-30T04:32:04.339732Z)`. Elle ne constitue ni un
retry de la campagne benchmark, ni une seconde campagne benchmark.

La campagne corrective a terminé `COMPLETED_LOCKED` après trois tentatives directes. Les
statistiques sont `PARSED/COMPLETE · 100%` sur `716`/`322`; les incidents sont
`PARSED/PARTIAL · 91%`, `164/179` signaux et 35 incidents sous `event-incidents-v15` sur
`717`/`324`; les compositions ont été atteintes et sont `PARSED/PARTIAL · 99%` sur `720`/`325`.
Aucun retry, fallback, import, polling ou second parcours n'a été exécuté.

La réponse incidents a été dédupliquée vers le snapshot `717`. L'identité brute réobserve donc la
variante ayant causé l'échec V14 : une sonde structurelle read-only compte à nouveau quatorze tirs
sans minute et quatorze tableaux d'actions exactement vides. L'observation V15 append-only les
parse avec succès. L'unité historique J8 reste `event-incidents-v14 / SCHEMA_INCOMPATIBLE`; les
19 tentatives, le hash de population et le double export de la fenêtre gelée restent inchangés.

```text
V15_CORRECTIVE_J5_LEDGER_CAMPAIGN=c9caf9b1-2495-4612-9e05-b9269aebaa66
V15_CORRECTIVE_PROVIDER_CALLS=3
ORIGINAL_J8_WINDOW_UNCHANGED=YES
ORIGINAL_J8_CAMPAIGN_RETRY=NO
SECOND_J8_BENCHMARK_CAMPAIGN=NO
ORIGINAL_J8_REPORT_STATE=PARTIAL_UNCHANGED
```

Une lecture dynamique sans fenêtre de `GET /benchmark` peut désormais inclure le nouveau ledger.
Elle ne doit pas être comparée au rapport historique sans reprendre explicitement sa fenêtre et
son `asOf`. Cette nouvelle preuve ne suffit pas à clôturer J8 : la revue ciblée, le rapport final
gelé et la décision propriétaire restent absents.

## 6. Postconditions et décision

Dans la campagne J8 initiale et immédiatement après son échec terminal, aucun retry, import,
fallback, appel compositions ou seconde campagne n'a été exécuté. L'application et le worker
Playwright ont été arrêtés, le port 8087 a été libéré et les
gates `.env` ont été remis à `false`. L'exporteur a ensuite confirmé le verrou persistant avant ses
lectures locales. PostgreSQL reste localement disponible pour conserver et agréger les preuves.

La revue humaine ciblée sur trois dossiers et les sources de contrôle déclarées n'a pas été
réalisée. Le rapport final gelé n'est donc pas créé et le bloc de clôture n'est pas prouvé.

```text
J8_LOCAL_CONFIGURATION_RELOCKED=YES
J8_APPLICATION_STOPPED=YES
J8_PORT_8087_AFTER_CAMPAIGN=FREE
J8_RESIDUAL_APPLICATION_PROCESS=NO
J8_RESIDUAL_PLAYWRIGHT_WORKER=NO
J8_POLLING=NO
J8_RETRY=NO
J8_HUMAN_QUALIFICATION=NOT_RUN_AFTER_PARTIAL_CAMPAIGN
J8_FINAL_BENCHMARK_REPORT=NOT_CREATED
J8_CLOSURE_BLOCK_PROVEN=NO
J8_WORK_ORDER_STATUS=READY_FOR_HUMAN_QUALIFICATION
J8_WORK_ORDER_VALIDATED=NO
J8_OWNER_CLOSURE_DECISION=NOT_GRANTED
J8_CORRECTIVE_PROVIDER_CAMPAIGN_AUTHORIZED_AT_INITIAL_FREEZE=NO
J8_CORRECTIVE_PROVIDER_CAMPAIGN_AUTHORIZED_LATER_BY_WO_017=YES
J8_CORRECTIVE_PROVIDER_CAMPAIGN_CONSUMED=YES
J8_CORRECTIVE_PROVIDER_CAMPAIGN_ADDITIONAL_CALLS_AUTHORIZED=NO
```

Les portes techniques ont été rejouées après la campagne et après la rédaction de cette preuve,
sans aucun appel fournisseur :

```text
J8_POST_CAMPAIGN_STANDARD_TESTS=912_PASS_4_SKIPPED
J8_POST_CAMPAIGN_INTEGRATION_TESTS=66_PASS
J8_POST_CAMPAIGN_VERIFY_LOCAL=PASS
J8_POST_CAMPAIGN_NETWORK_CALLS_DURING_GATES=0
J8_POST_CAMPAIGN_COMPOSE_CONFIG=PASS
J8_POST_CAMPAIGN_GIT_DIFF_CHECK=PASS
```

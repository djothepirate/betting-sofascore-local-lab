# J5 — Qualification fournisseur V15 du 2026-08-30

## 1. Statut

```text
WORK_ORDER=WO-SS-20260830-017
QUALIFICATION_STATUS=PASS
EXECUTION_AUTHORITY=EXPLICIT_OWNER_INSTRUCTION
EXECUTION_ACTOR=CODEX_LOCAL_UI
TARGET_PROVIDER_EVENT_ID=16691018
TARGET_CANONICAL_EVENT_ID=f4713f80-4769-3656-ba51-61d8ac1aa814
J5_TERMINAL_STATE=COMPLETED_LOCKED
J5_TERMINAL_CODE=COMPLETED
PROVIDER_CALLS=3
LOCAL_JSON_IMPORTS=0
PROVIDER_RETRY=NO
FALLBACK=NO
POLLING=NO
SECOND_CAMPAIGN=NO
ADDITIONAL_PROVIDER_CALL_AUTHORIZED=NO
WORK_ORDER_STATUS=VALIDATED
OWNER_CLOSURE_DECISION=AUTHORIZED_BY_OWNER_2026_08_30
WORK_ORDER_CLOSURE=COMPLETED
WORK_ORDER_LOCATION=docs/work_orders/completed
```

Cette preuve consigne l'unique campagne corrective autorisée après la readiness technique V15.
La décision propriétaire distincte de la section 8 clôture ensuite WO-017, mais ne clôture pas J8
et ne prend aucune décision d'adoption J9. Les statuts du
laboratoire restent `EXPERIMENTAL`, `LOCAL_ONLY`, `NOT_PRODUCTION_APPROVED` et
`NO_CRITICAL_DEPENDENCY`.

## 2. Autorisation et bornes

Le propriétaire a d'abord autorisé une seule campagne J5 sur l'événement exact qui avait révélé
la variante, puis a explicitement confié son exécution intégrale à Codex sans geste manuel et sans
modification de `.env`. Cette seconde instruction a changé uniquement l'acteur des gestes locaux ;
elle n'a élargi ni la cible, ni l'ordre, ni le plafond.

- activation fournisseur et Playwright limitée à l'arbre de processus du lanceur J5 ;
- page contrôlée uniquement sur `127.0.0.1:8087` ;
- cible locale vérifiée avant préparation : Cittadella — Atalanta U23, UUID
  `f4713f80-4769-3656-ba51-61d8ac1aa814`, identifiant fournisseur `16691018` ;
- une préparation, une confirmation fraîche et une soumission ;
- ordre fermé `EVENT_STATISTICS → EVENT_INCIDENTS → EVENT_LINEUPS` ;
- plafond de trois tentatives, concurrence unitaire et temporisation minimale de trois secondes ;
- aucun retry, fallback, import, polling, scheduler ou seconde campagne ;
- aucun profil persistant, cookie, `storageState`, HAR, trace, vidéo, capture ou téléchargement.

La phrase de confirmation n'est pas reproduite dans cette preuve. Le lanceur a annoncé zéro accès
fournisseur avant le geste confirmé ; seuls les trois GET bornés de la campagne ont ensuite été
admis à la frontière de transport.

## 3. Résultat minimisé

La campagne de ledger `c9caf9b1-2495-4612-9e05-b9269aebaa66` a débuté à
`2026-08-30T08:09:44.275218Z` et s'est terminée à `2026-08-30T08:09:53.484543Z` avec
`COMPLETED / COMPLETED`, trois unités terminées sur trois. Chaque réponse était HTTP 200.

| Famille | Tentative | Latence | Snapshot | Occurrence | Persistance | Taille | Parseur | Complétude | Observation |
|---|---:|---:|---:|---:|---|---:|---|---|---:|
| `EVENT_STATISTICS` | 20 | 2 994 ms | 716 | 685 | `DEDUPLICATED` | 7 648 octets | `event-statistics-v2` | `COMPLETE · 100%` | 322 |
| `EVENT_INCIDENTS` | 21 | 77 ms | 717 | 686 | `DEDUPLICATED` | 43 363 octets | `event-incidents-v15` | `PARTIAL · 91%` | 324 |
| `EVENT_LINEUPS` | 22 | 132 ms | 720 | 687 | `INSERTED` | 76 672 octets | `event-lineups-v2` | `PARTIAL · 99%` | 325 |

```text
EVENT_STATISTICS_SHA256=13d2fb29a74d69229091b79074e1915f2378fdb317fab19fe04bf7971ea2a25a
EVENT_INCIDENTS_SHA256=55552a5f150d1e475e89a04331d42ef09e7b1568661e455798a58170fffa5b05
EVENT_LINEUPS_SHA256=6e7b13b2edff38b2b9229e9ee867131d9f3a68e7aef3777426f0cc98d9052560
EVENT_STATISTICS_WARNINGS=13
EVENT_INCIDENTS_WARNINGS=18
EVENT_LINEUPS_WARNINGS=256
EVENT_INCIDENTS_COUNT=35
EVENT_INCIDENTS_SIGNALS=164/179
EVENT_LINEUPS_SIGNALS=102/103
```

Les deux intervalles entre départs sont supérieurs ou égaux à trois secondes. Il existe une seule
tentative par unité, chaque tentative possède un résultat, et le statut HTTP, la latence, le
snapshot et le parseur sont identiques entre le résultat d'unité et l'occurrence associée.

## 4. Qualification de la variante V15

L'occurrence incidents 686 est `DEDUPLICATED` vers le snapshot 717, avec le même SHA-256 et la
même taille que la réponse qui avait arrêté J8 sous V14. Il s'agit donc d'une réobservation
prospective byte-identique de la variante, pas d'une inférence à partir d'un autre payload.

Une sonde structurelle PostgreSQL `REPEATABLE READ READ ONLY`, terminée par rollback, a relu les
octets sans les afficher et a produit uniquement des compteurs :

```text
PENALTY_SHOOTOUT_COUNT=14
PENALTY_SHOOTOUT_WITHOUT_TIME=14
PENALTY_SHOOTOUT_WITH_TIME=0
FOOTBALL_PASSING_NETWORK_ACTION_ABSENT=0
FOOTBALL_PASSING_NETWORK_ACTION_EXACT_EMPTY_ARRAY=14
FOOTBALL_PASSING_NETWORK_ACTION_NON_EMPTY_ARRAY=0
FOOTBALL_PASSING_NETWORK_ACTION_WRONG_TYPE=0
TERMINAL_PEN_MARKER_COUNT=1
SHOOTOUT_SEQUENCE_MIN=1
SHOOTOUT_SEQUENCE_MAX=14
SHOOTOUT_DISTINCT_SEQUENCES=14
STRUCTURAL_PROBE_PERSISTED=NO
```

V15 a persisté l'observation append-only 324, `PARSED`, 35 incidents, 18 warnings et
`PARTIAL · 91%`, puis la campagne a atteint les compositions. L'interface locale montre une
minute absente pour le marqueur `PEN` et chacune des quatorze tentatives, conserve l'ordre et le
score et n'invente aucun motif absent.

```text
V15_CURRENT_RESPONSE_COMPATIBILITY=PASS
EMPTY_ARRAY_VARIANT_REOBSERVED=YES_BY_BYTE_IDENTICAL_SNAPSHOT_REUSE
V15_PROBLEM_COUNT=0
LINEUPS_REACHED_AFTER_INCIDENTS=YES
HISTORICAL_J8_UNIT_PARSER_REFERENCING_SNAPSHOT_717=event-incidents-v14
HISTORICAL_J8_UNIT_SCHEMA_STATUS_REFERENCING_SNAPSHOT_717=SCHEMA_INCOMPATIBLE
HISTORICAL_CLASSIFICATION_REWRITTEN=NO
PERSISTED_HISTORICAL_REPARSE=NO
```

La qualification est bornée à cette réponse et à cet événement. Les tests V15 restent l'autorité
pour le rejet des tableaux non vides incohérents, des types erronés, des séances mixtes, des gaps,
des doublons et des scores ou marqueurs contradictoires.

## 5. Ledger J8 et séparation de la fenêtre historique

V27 a créé normalement une campagne `J5_EVENT_DATA / GUARDED_PROVIDER` pour cette exécution. Après
la campagne, le ledger totalise cinq campagnes, vingt-trois unités, vingt-deux tentatives et
vingt-trois résultats d'unité. En excluant la campagne corrective, les comptes restent exactement
ceux de la preuve J8 gelée : quatre campagnes, dix-neuf tentatives et vingt résultats d'unité.

```text
V15_QUALIFICATION_J5_LEDGER_CAMPAIGN=CREATED_AS_REQUIRED
V15_QUALIFICATION_DECLARED_UNITS=3
V15_QUALIFICATION_DIRECT_ATTEMPTS=3
V15_QUALIFICATION_INCOMPLETE_ATTEMPTS=0
ORIGINAL_J8_EXCLUSIVE_WINDOW_UNCHANGED=YES
ORIGINAL_J8_PROVIDER_CAMPAIGN_RETRY=NO
J8_SECOND_BENCHMARK_CAMPAIGN=NO
ORIGINAL_J8_REPORT_STATE=PARTIAL_UNCHANGED
```

La campagne corrective est postérieure et extérieure à la fenêtre exclusive J8. Elle ne modifie
ni les unités historiques, ni leur coût, ni leur hash de population, ni le double export gelé.
Une agrégation dynamique sans fenêtre peut désormais inclure ce nouveau ledger et doit être
distinguée du rapport historique par sa population et son hash.

## 6. Reverrouillage et confidentialité

Après le terminal, l'application et le worker ont été arrêtés gracieusement. L'activation
process-scoped a disparu ; `.env` est resté inchangé avec tous les drapeaux métier à `false`,
l'origine et l'allowlist vides. Un redémarrage normal sans Playwright a ensuite confirmé :

- contrôle J5 `LOCKED` ;
- bloqueurs métier, connecteur, runtime, worker et origine explicitement présents ;
- bouton `Préparer la campagne J5` désactivé ;
- aucune phrase de confirmation visible.

L'application a été arrêtée à nouveau. Le port 8087 est libre et aucun processus application ou
worker appartenant au worktree ne subsiste.

Cette preuve exclut payload, URI, `request_key`, phrase de confirmation, headers, cookies, jetons,
valeurs secrètes de `.env`, logs bruts et artefacts navigateur.

## 7. Décision

La qualification fournisseur V15 est `PASS` dans cette portée bornée. La campagne autorisée est
consommée et aucun appel supplémentaire n'est autorisé. Le propriétaire a ensuite jugé ce test
fonctionnel concluant et accordé la décision distincte de clôture de WO-017.

J8 reste `READY_FOR_HUMAN_QUALIFICATION` avec son rapport historique `PARTIAL`. Cette qualification
ne crée pas de rapport final J8, ne prouve pas sa revue humaine ciblée et ne décide pas J9.

## 8. Décision propriétaire de clôture de WO-017

```text
OWNER_FUNCTIONAL_ASSESSMENT=CONCLUSIVE
OWNER_CLOSURE_DECISION=AUTHORIZED_BY_OWNER_2026_08_30
WORK_ORDER_STATUS=VALIDATED
CLOTURE=COMPLETED
WORK_ORDER_LOCATION=docs/work_orders/completed
APPLICATION_INSTANCE_ACTIVE=NO
PLAYWRIGHT_WORKER_ACTIVE=NO
PORT_8087_LISTENER=ABSENT
ADDITIONAL_PROVIDER_CALL_AUTHORIZED=NO
ADDITIONAL_PROVIDER_CALL_AUTHORIZED_BY_CLOSURE=NO
J8_CLOSURE_DECISION=NOT_IMPLIED
```

WO-017 est archivé dans `docs/work_orders/completed/`. La preuve J8 historique reste immuable et
la poursuite de J8 est traitée séparément.

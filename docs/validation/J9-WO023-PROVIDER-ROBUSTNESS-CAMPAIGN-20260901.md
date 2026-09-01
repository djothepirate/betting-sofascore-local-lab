# J9 — Rapport de campagne autonome de robustesse fournisseur WO-023

- **Report ID :** `J9-WO023-PROVIDER-ROBUSTNESS-CAMPAIGN-20260901`
- **Date :** 2026-09-01
- **Work Order :** `WO-SS-20260831-023-j9-provider-robustness-v11`
- **Commit lié au go :** `f632fd0377f2715c15fc2e030168a35a67f98552`
- **ADR :** `ADR-SS-002 v1.1 — ACCEPTED`
- **Profil :** `RESTART_FULL_D1_D2_D3`
- **Acteur :** `CODEX_LOCAL_UI`
- **Verdict :** `PASS`

## 1. Verdict et portée

La série autonome D1/D2/D3 est terminée sous son plafond, sans condition d'arrêt : 28 tentatives
directes sur 38 autorisées ont toutes reçu une réponse HTTP 200 compatible, parsée, persistée et
auditée. Aucun cache hit, 404, refus, autre statut HTTP, timeout, challenge, redirection inattendue,
retry, incompatibilité de schéma, dépassement de taille ou incident de sécurité n'est observé.

```text
EVIDENCE_RESULT=PASS
NEW_SERIES_DIRECT_ATTEMPTS=28
NEW_SERIES_MAXIMUM_DIRECT_ATTEMPTS=38
AUDIT_CUMULATIVE_DIRECT_ATTEMPTS=48
AUDIT_MAXIMUM_CUMULATIVE_DIRECT_ATTEMPTS=58
UNUSED_AUTHORIZED_ATTEMPTS=10_NOT_REUSABLE
HTTP_200=28
PARSED=28
HTTP_404=0
OTHER_HTTP=0
CACHE_HITS=0
AUTOMATIC_RETRIES=0
BLOCKING_INCIDENTS=0
DETERMINISTIC_RECOMMENDATION=PREPARE_OPTIONAL_INTEGRATION
OWNER_CONFIRMATION_REQUIRED=YES
INTEGRATION_OR_PRODUCTION_AUTHORIZED=NO
```

Le `PASS` qualifie la robustesse technique bornée de cette campagne, pas une disponibilité continue,
une exactitude externe, une valeur analytique comparative, une licence d'utilisation, une
maintenabilité durable ni une aptitude à la production. La complétude analytique reste
`PARTIAL_BOUNDED` parce que deux familles D1 présentent des lacunes optionnelles bornées.

## 2. Autorité, manifeste et go consommé

Le go propriétaire global cite le manifeste gelé suivant, resté byte-identique pendant et après
l'exécution :

```text
MANIFEST_REFERENCE=docs/validation/J9-WO023-PROVIDER-CAMPAIGN-MANIFEST-20260901.md
MANIFEST_SHA256=ff909f298f7d3c99b1027c071ac4d783a19529f6241b37941151c2c08c418a9e
BACKUP_REPORT=docs/validation/J9-WO023-V28-BACKUP-RESTORE-RETRY-20260901.md
BACKUP_REPORT_SHA256=c29ce8593ccd54ca4347636cf9bcf881d1f36081348a5850e134e69cffab9bbe
HISTORICAL_REPORT=docs/validation/J9-PROVIDER-ROBUSTNESS-CAMPAIGN-20260831.md
HISTORICAL_REPORT_SHA256=47e6171eeb1fc44cf995c727d4f09107875c844e71e8b183068731cbb4c62b9d
WINDOW_UTC=[2026-08-31T23:45:00Z,2026-09-01T00:45:00Z)
GO_USE=ONE_TIME
GLOBAL_OWNER_GO=CONSUMED_AND_TERMINATED_BY_D3_COMPLETION
REPLACEMENT_DOSSIER_ALLOWED=NO
PRIMARY_DATABASE_PURGE=NO
```

Le go est consommé par le premier claim J3, puis définitivement terminé par l'achèvement de B4.
Les dix tentatives non utilisées ne constituent aucun reliquat réutilisable. Aucun dossier de
remplacement, retry automatique, polling, scheduler, boucle d'orchestration ou purge primaire n'a
été employé.

## 3. Corpus immuable

| Dossier | Provider event ID | Identifiant canonique | Rôle exécuté |
|---|---:|---|---|
| D1 — Cittadella / Atalanta U23 | `16691018` | `f4713f80-4769-3656-ba51-61d8ac1aa814` | ancrage J3, tournoi 824, J4 et J5 |
| D2 — Barracas Central / Rosario Central | `16671566` | `da075869-34d4-3d42-83d2-613583691845` | J4 et J5, cas historique complet |
| D3 — Lille / PSG | `16310930` | `c40066c9-987b-38d9-b415-869a453d2ad6` | J4 et J5, cas récent |

Les huit sous-campagnes sont terminales en état `COMPLETED`. Aucune campagne ou tentative extérieure
au go n'est mélangée à la série.

## 4. Ledger par segment

| Segment | Famille | Dossier | IDs de tentative | Tentatives | HTTP 200 / parsées | Persistance |
|---|---|---|---|---:|---:|---|
| A1 | J3 scheduled events pages 1 à 15 | ancrage D1 | `117–131` | 15 | 15 / 15 | 15 `INSERTED` |
| A2 | découverte tournoi 824 | D1 | `132` | 1 | 1 / 1 | 1 `DEDUPLICATED` après réponse fraîche |
| A3 | J4 event details | D1 | `133` | 1 | 1 / 1 | 1 `INSERTED` |
| A4 | J5 statistics/incidents/lineups | D1 | `134–136` | 3 | 3 / 3 | 3 `DEDUPLICATED` après réponses fraîches |
| B1 | J4 event details | D2 | `137` | 1 | 1 / 1 | 1 `INSERTED` |
| B2 | J5 statistics/incidents/lineups | D2 | `138–140` | 3 | 3 / 3 | 3 `INSERTED` |
| B3 | J4 event details | D3 | `141` | 1 | 1 / 1 | 1 `INSERTED` |
| B4 | J5 statistics/incidents/lineups | D3 | `142–144` | 3 | 3 / 3 | 3 `INSERTED` |
| **Total** | six familles allowlistées | D1/D2/D3 | `117–144` | **28** | **28 / 28** | **24 `INSERTED`, 4 `DEDUPLICATED`** |

Les quatre déduplications ne sont pas des cache hits : chacune possède une tentative fournisseur,
une réponse fraîche et une occurrence d'acquisition distincte. Il existe 28 unités distinctes,
zéro tentative dupliquée par unité, 28 occurrences et 28 snapshots liés. Le ledger append-only
atteint 144 tentatives au total, avec `MAX_PROVIDER_ATTEMPT_ID=144`.

## 5. Preuves J3 et tournoi D1

La découverte J3 parcourt exactement quinze pages contiguës et s'arrête sur
`hasNextPage=false`. Les snapshots 815 à 829 et occurrences 782 à 796 sont tous nouveaux.

| Page / tentative | Latence | Snapshot / occurrence | Octets | SHA-256 du payload |
|---|---:|---|---:|---|
| 1 / `117` | 2 814 ms | `815 / 782` | 219 503 | `325607ba646d5e2287a3ffa08c2625b4e707f5ac2f7ce39da0a8050c49c141cf` |
| 2 / `118` | 47 ms | `816 / 783` | 205 987 | `8c0ccc1b971486c0c08fa945cdb314ec4e9dc42bd7fca6195d57ad4122f7fa99` |
| 3 / `119` | 45 ms | `817 / 784` | 197 481 | `ceb4ba252ab36ee77da654af6f20cb8f9d03ea82993b73b626a47ef879fcd037` |
| 4 / `120` | 54 ms | `818 / 785` | 198 492 | `ea75d933aa77b4bacb968f251bb8d9e6b3a9aa6c946b8e1bd0885213df5553d7` |
| 5 / `121` | 50 ms | `819 / 786` | 168 327 | `9b0f282ec6fef1da3a85332b8f4b1a2bbedf68859acf7c883386931c6132ccc8` |
| 6 / `122` | 51 ms | `820 / 787` | 202 709 | `82c8bb11ddfffe83b6c76d31e059103e3fac7d0deca16bab256348ec3a6c2a97` |
| 7 / `123` | 46 ms | `821 / 788` | 203 567 | `b4b82483f5c9d8be2a7ef859e73a5f985078cbf7433af490c644e1d55dc995b1` |
| 8 / `124` | 45 ms | `822 / 789` | 203 918 | `e5c5aed16e7e6a6eac46a0d5b99eac227a1317f560bfeda2fedcad18f01efce6` |
| 9 / `125` | 47 ms | `823 / 790` | 158 246 | `1e2231f8fc9e295f9441e0e7c125f95dffeb54c8d7bc08032713fab5d252b302` |
| 10 / `126` | 42 ms | `824 / 791` | 172 761 | `8fb3518544131cdfa6a1620311aabba464c422425e2c182312ef2f625f57269d` |
| 11 / `127` | 49 ms | `825 / 792` | 192 821 | `16d3121f007cf68535b5b0f18258d43dcce1ae5c4e595a2b265d74ba5993ea9c` |
| 12 / `128` | 44 ms | `826 / 793` | 192 449 | `3b6761cf0f72030fe689c906a1798b181c73a7c024ebcf8f3c7c0a0555cf08d2` |
| 13 / `129` | 43 ms | `827 / 794` | 188 162 | `c5d4664c57555149d4b6cd8051e59cc80e0bd5bafda71b9523f14dac6e9ef5f4` |
| 14 / `130` | 45 ms | `828 / 795` | 188 909 | `b80be85241cdaee44a1e0afabb1c8a7d32fa22e98e90d5266013679d828d7071` |
| 15 / `131` | 39 ms | `829 / 796` | 158 003 | `b546bc10706587eb3b1f58ecb4bdab08904127a36310d8216884a07b4018bd76` |

La découverte tournoi D1, tentative `132`, reçoit et parse huit événements sur huit. Elle rattache
D1 au tournoi 824, saison 99790 et phase 15118. La réponse fraîche est dédupliquée sur le snapshot
810, occurrence 797, taille 73 720 octets et SHA-256
`ae93f1859d7098973d49f0f37cac6cd473abf821f4eb043eec793f30a6382ff6`.

## 6. Preuves J4/J5 par dossier

| Tentative | Dossier / famille | Latence | Snapshot / occurrence | Persistance | Complétude | Octets | SHA-256 du payload |
|---:|---|---:|---|---|---|---:|---|
| `133` | D1 / détails | 66 ms | `831 / 798` | `INSERTED` | n/a | 9 958 | `53d5645f08b9137db014b502e51a770458384e96362a68be74f0f4b3a1ce9f37` |
| `134` | D1 / statistiques | 69 ms | `716 / 799` | `DEDUPLICATED` | `COMPLETE · 100 %` | 7 648 | `13d2fb29a74d69229091b79074e1915f2378fdb317fab19fe04bf7971ea2a25a` |
| `135` | D1 / incidents | 35 ms | `813 / 800` | `DEDUPLICATED` | `PARTIAL · 91 %` | 43 363 | `c1c163ec33435137fff0b4b974fff70bc1e3ac43058fdaeb9a6973531ea9228d` |
| `136` | D1 / compositions | 172 ms | `814 / 801` | `DEDUPLICATED` | `PARTIAL · 99 %` | 76 707 | `c52fbd741f038deb132b81947876604b4e45e593f00900bb8f1c995f274643e9` |
| `137` | D2 / détails | 72 ms | `835 / 802` | `INSERTED` | n/a | 10 074 | `003f41c14638b36824a36575571b7d1473db580c47173b113dd920256c8d2802` |
| `138` | D2 / statistiques | 59 ms | `836 / 803` | `INSERTED` | `COMPLETE · 100 %` | 25 797 | `b75aa440f9b3f9c6862a502cacacbc5609dadf2026f216b3be9b7f2bf53a1e53` |
| `139` | D2 / incidents | 27 ms | `837 / 804` | `INSERTED` | `COMPLETE · 100 %` | 27 079 | `2a1deb8495061fcbacbc57a5195b4e101abbe1903653017397e472710157847a` |
| `140` | D2 / compositions | 37 ms | `838 / 805` | `INSERTED` | `COMPLETE · 100 %` | 78 788 | `1c3c1d19c5442f28c5272edfce0d957e4b639e2f5f35d87f9996fc6885adc5ab` |
| `141` | D3 / détails | 61 ms | `839 / 806` | `INSERTED` | n/a | 9 732 | `95d8358cb2c92125a7c0e1ccd8100c10e296b36b47b73715a5a600da444da1e4` |
| `142` | D3 / statistiques | 68 ms | `840 / 807` | `INSERTED` | `COMPLETE · 100 %` | 26 138 | `739f115e972091b820d1c01fcd297e6fd295f0619aaae21607a75609f5858a24` |
| `143` | D3 / incidents | 35 ms | `841 / 808` | `INSERTED` | `COMPLETE · 100 %` | 55 115 | `74d626383ba967400e4345e9ae41d85107b42c8c749ce2edfed8917ab7510624` |
| `144` | D3 / compositions | 42 ms | `842 / 809` | `INSERTED` | `COMPLETE · 100 %` | 84 618 | `74ff4b7ec866f332dcc68daff4a2d38ae156e31b008f25c9eaa700699a9fe050` |

D2 et D3 sont complets sur les trois familles J5. Les deux lacunes D1 portent sur des signaux
optionnels déjà qualifiés ; elles ne correspondent ni à un parsing incompatible ni à une
indisponibilité fournisseur. Elles maintiennent cependant le critère de complétude global à
`PARTIAL_BOUNDED`.

## 7. Enveloppe temporelle, coût et volume

```text
FIRST_CAMPAIGN_STARTED_AT_UTC=2026-08-31T23:46:47.643507Z
FIRST_PROVIDER_ATTEMPT_STARTED_AT_UTC=2026-08-31T23:46:51.453177Z
FIRST_PROVIDER_REQUESTED_AT_UTC=2026-08-31T23:46:51.623000Z
LAST_CAMPAIGN_FINISHED_AT_UTC=2026-08-31T23:54:56.690210Z
LAST_PROVIDER_RECEIVED_AT_UTC=2026-08-31T23:54:56.459000Z
TECHNICAL_ENVELOPE_APPROXIMATELY=8m09s
OPERATOR_DURATION=NOT_MEASURED
MINIMUM_ATTEMPT_START_DELTA_MS=3000.277
MINIMUM_PROVIDER_REQUESTED_AT_DELTA_MS=3088
INTERVALS_BELOW_3_SECONDS=0
ATTEMPT_OVERLAPS=0
CAMPAIGN_OVERLAPS=0
LATENCY_MIN_MS=27
LATENCY_MAX_MS=2814
LATENCY_AVERAGE_MS=152.964
PAYLOAD_BYTES_SUM=3380072
PAYLOAD_BYTES_MAX=219503
PAYLOAD_LIMIT_BYTES=5242880
```

Le premier refus local d'une préparation J5 D1 était un rejet HTTP 400 du formulaire loopback dû à
un jeton local expiré après la navigation J4. Il a eu lieu avant claim, transport ou écriture du
ledger : `PROVIDER_ATTEMPTS=0`. La page locale a été rechargée, un jeton frais préparé une fois, puis
la séquence a été exécutée. Ce refus local n'est ni un retry fournisseur ni un incident de campagne.

## 8. Sauvegarde, audit et export reproductible

La campagne n'a commencé qu'après la sauvegarde chiffrée et la restauration isolée V28 qualifiées
dans le rapport lié au go. La base primaire n'a pas été purgée. L'audit post-campagne retrouve les
28 tentatives, 28 occurrences, 28 snapshots liés, zéro échec d'intégrité brute et zéro unité sans
résultat.

Deux exports J8 one-shot ont ensuite été générés avec des bornes et un `AsOf` textuellement
identiques, application arrêtée et réseau fournisseur forcé à `false` :

```text
EXPORT_FROM_UTC=2026-08-31T23:46:47.643507Z
EXPORT_TO_UTC=2026-08-31T23:54:56.690210Z
EXPORT_AS_OF_UTC=2026-09-01T00:04:04.875035Z
POST_CAMPAIGN_DOUBLE_EXPORT=PASS_TWO_RUNS
POST_CAMPAIGN_EXPORT_BYTE_IDENTITY=PASS
POST_CAMPAIGN_EXPORT_HASH_CONCORDANCE=PASS
POST_CAMPAIGN_EXPORT_SHA256=76d1983dc466356de033efae859822b005aa4e77f3fe19403619ff8e2c240580
POST_CAMPAIGN_EXPORT_SIZE_BYTES=15696
POST_CAMPAIGN_EXPORT_POPULATION_HASH=c8c19ddc3ece1497c26e3348ef78a736f1eaf8fe0b0980801d077f97ab2bf399
POST_CAMPAIGN_EXPORT_NETWORK_CALLS=0
POST_CAMPAIGN_EXPORT_MEASUREMENT_STATE=MEASURED
POST_CAMPAIGN_EXPORT_EVIDENCE_COVERAGE=FULL_ATTEMPT_LEDGER
POST_CAMPAIGN_EXPORT_ATTEMPTS=28
POST_CAMPAIGN_EXPORT_RESPONSES=28
POST_CAMPAIGN_EXPORT_PARSED=28
AUTOMATIC_BEGIN_MARKERS=1
AUTOMATIC_END_MARKERS=1
```

Les deux fichiers runtime étaient ignorés par Git. Seules les bornes, tailles, empreintes et
qualifications présentes dans ce rapport sont des preuves versionnées ; aucun contenu de payload
n'est recopié.

## 9. Arrêt, nettoyage et re-verrouillage

Après D3, l'application a été arrêtée par Ctrl+C. Trois contrôles locaux successifs et l'audit
indépendant établissent :

```text
CONNECTOR_CONTROL=SAFE
PERSISTED_CIRCUIT_STATE=LOCKED
SOFASCORE_ENABLED=false
J3_J4_J5_TOURNAMENT_QUALIFICATION_FLAGS=false
LISTENER_8087_COUNT=0
APPLICATION_PROCESS_COUNT=0
PLAYWRIGHT_WORKER_PROCESS_COUNT=0
OWNED_NODE_OR_BROWSER_PROCESS_COUNT=0
AGE_PROCESS_COUNT=0
J6_OWNED_POSTGRES_SESSION_COUNT=0
J6_TEMPORARY_RESTORE_DATABASE_COUNT=0
DOCKER_CLI_RESIDUAL_COUNT=0_THREE_CHECKS
FORBIDDEN_PLAYWRIGHT_ARTIFACT_COUNT=0
PLAYWRIGHT_TEMP_PROFILE_COUNT=0
PLAYWRIGHT_TEMP_DOWNLOAD_COUNT=0
ACCEPT_DOWNLOADS=false
PROVIDER_NETWORK_AUTHORIZED=NO
CAMPAIGN_EXECUTION_AUTHORIZED=NO_COMPLETED
```

Aucun HAR, trace, vidéo, capture, téléchargement, `storageState`, profil persistant, cookie, jeton,
certificat, phrase secrète, chemin sensible ou payload brut n'est conservé dans la documentation ou
ajouté à Git.

## 10. Vérifications post-campagne

| Vérification | Résultat |
|---|---|
| `.\mvnw.cmd --offline clean verify` avec cache Maven local explicite, chemin non consigné | `PASS` — 946 tests, 0 échec, 0 erreur, 5 ignorés |
| `.\mvnw.cmd --offline -Pintegration-tests verify` avec cache Maven local explicite, chemin non consigné | `PASS` — 946 tests standards puis 67 intégrations, 0 échec, 0 erreur |
| migrations Testcontainers | `PASS` — 28 migrations, version finale V28 |
| `docker compose --env-file .env config --quiet` | `PASS` |
| adresse serveur | `127.0.0.1` conservée |
| tests standards avec appel SofaScore réel | aucun |
| `git diff --check` et contrôle documentaire des secrets | `PASS` |

## 11. Limites officielles et recommandation J9

La revue officielle versionnée reste
`PARTIAL — RESTRICTIONS_PRESENT_PERMISSION_NOT_EVIDENCED`. Elle documente des restrictions sur les
requêtes automatisées, l'intégration, l'agrégation, le scraping, la reproduction et l'extraction
substantielle sans consentement explicite, tandis qu'aucune permission, licence, authentification ou
limite applicable aux endpoints du laboratoire n'a été extraite. Ce rapport ne fournit aucune
conclusion juridique et ne promeut pas ce critère à `PASS`.

Cette incertitude n'établit pas, à elle seule, une incompatibilité structurelle certaine qui
déclencherait `ABANDON`. La campagne est `PASS`, la sauvegarde/restauration V28, l'audit et l'export
sont conformes, et l'indépendance actuelle du Betting Project est préservée. Selon les règles de
WO-018, la recommandation factuelle devient donc `PREPARE_OPTIONAL_INTEGRATION`.

Cette recommandation permet uniquement au propriétaire d'autoriser ultérieurement l'ouverture d'un
Work Order de préparation et la proposition d'ADR-SS-003. Elle n'autorise aucune implémentation,
intégration, livraison, acquisition fournisseur, production ou topologie VPS. L'option VPS future
reste `NOT_EXCLUDED_BUT_NOT_AUTHORIZED` et devra être comparée au push local optionnel dans une étude
de faisabilité distincte incluant les droits/permissions applicables.

```text
J9_DECISION_RECOMMENDATION=PREPARE_OPTIONAL_INTEGRATION
J9_EVIDENCE_RESULT=PASS
J9_OFFICIAL_PERMISSION_STATUS=NOT_EVIDENCED
J9_PROVIDER_ACQUISITION_MODE=MANUAL_ON_DEMAND
J9_INTEGRATION_IMPLEMENTATION_AUTHORIZED=NO
J9_LIVE_OR_SCHEDULED_OPERATION_AUTHORIZED=NO
J9_BETTING_PROJECT_CRITICAL_DEPENDENCY=NO
J9_CURRENT_VPS_DEPLOYMENT_AUTHORIZED=NO
J9_FINAL_DECISION=NOT_TAKEN
J9_OWNER_CONFIRMATION_REQUIRED=YES
```

Seule une réponse propriétaire explicite choisissant `ABANDON`, `KEEP_LOCAL` ou
`PREPARE_OPTIONAL_INTEGRATION` peut clore la décision J9. Le présent verdict et la recommandation ne
remplacent pas cette réponse.

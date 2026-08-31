# WO-SS-20260831-022 — Réexamen d'ADR-SS-002 avant toute reprise de WO-019

- **Statut :** `READY_FOR_OWNER_PROFILE_DECISION`
- **Date d'ouverture :** 2026-08-31
- **Jalon :** J9 — gouvernance de la preuve fournisseur
- **Base locale :** `1fcd2cd1a71df74e97e31850a1497a3780e7719f`
- **Branche :** `codex/j9-adr002-reexamination`
- **Worktree dédié :** `.tmp/j9-adr002-reexamination`
- **Work Order parent :** WO-018 `IN_DEVELOPMENT`
- **Campagne concernée :** WO-019 `STOPPED`
- **Prérequis runtime :** WO-021 `VALIDATED`
- **ADR réexaminé :** ADR-SS-002 v1.0 `ACCEPTED_CONSUMED_AND_TERMINATED_BY_STOP`
- **Version proposée :** ADR-SS-002 v1.1 `DRAFT_PENDING_OWNER_PROFILE_DECISION`
- **Réseau fournisseur :** `NOT_AUTHORIZED`
- **Reprise de WO-019 :** `NOT_AUTHORIZED`
- **Nouveau go fournisseur :** `NOT_GRANTED`
- **Tentatives historiques gelées :** `20`
- **Décision J9 finale :** `NOT_TAKEN`
- **Intégration ou production :** `NOT_AUTHORIZED`
- **Option VPS future :** `NOT_EXCLUDED_BUT_NOT_AUTHORIZED`

## 1. Objet

Exécuter le réexamen imposé par ADR-SS-002 §9 après :

1. l'arrêt global de la série v1.0 ;
2. l'ajout, sous WO-021, d'un fence runtime exécutoire commun aux campagnes et workers ;
3. la validation et la clôture de WO-021 par le propriétaire.

Le présent Work Order est documentaire. Il ne reprend pas WO-019, n'ouvre aucun endpoint, ne
contacte aucun endpoint de données SofaScore et ne vaut ni acceptation d'ADR-SS-002 v1.1, ni
readiness, ni manifeste, ni go.

```text
J9_WO022_SCOPE=ADR_SS_002_REEXAMINATION_ONLY
J9_PROVIDER_NETWORK_AUTHORIZED=NO
J9_WO019_PROVIDER_CAMPAIGN_RESUME_AUTHORIZED=NO
J9_NEW_PROVIDER_GO_GRANTED=NO
J9_INTEGRATION_OR_PRODUCTION_AUTHORIZED=NO
```

## 2. Entrées immuables

```text
ADR_SS_002_V1_0_FILE_SHA256_BEFORE_V1_1_DRAFT=F68792FF722708F436957D3414317F2B5AC9FA2E2FA77BC949274FED144B3957
ADR_SS_002_V1_0_FILE_SHA256_SCOPE=HISTORICAL_V1_0_FILE_BEFORE_V1_1_DRAFT
ADR_SS_002_V1_0_SOURCE_COMMIT=af7fe179315053aedff31f0b3f6a31f6e4a8e54c
PRIOR_EVIDENCE_REPORT=J9-PROVIDER-ROBUSTNESS-CAMPAIGN-20260831
PRIOR_EVIDENCE_REPORT_BYTES=9708
PRIOR_EVIDENCE_REPORT_SHA256=47e6171eeb1fc44cf995c727d4f09107875c844e71e8b183068731cbb4c62b9d
PRIOR_EVIDENCE_RESULT=STOPPED
PRIOR_GLOBAL_OWNER_GO=CONSUMED_AND_TERMINATED_BY_STOP
PRIOR_DIRECT_ATTEMPTS_FROZEN=20
PRIOR_D1_PROVIDER_START_DELAY=NOT_MEASURED
D2_D3_PROVIDER_ATTEMPTS=0
```

Le rapport arrêté et ses vingt tentatives ne seront ni réécrits, ni reclassifiés, ni remis à zéro.
WO-021 qualifie prospectivement le runtime corrigé en loopback ; il ne reconstruit pas les départs
réseau exacts des appels D1 historiques.

## 3. Conclusion du réexamen

ADR-SS-002 v1.0 n'est pas réutilisable pour une reprise.

Deux déclencheurs indépendants de son paragraphe 9 sont réalisés :

- reprise après incident et après arrêt global ;
- création d'un mécanisme global exécutoire dans le runtime par WO-021.

Le paragraphe 3.7 de v1.0 interdit en outre explicitement une seconde série ou une reprise. L'ancien
go était à usage unique, sa fenêtre est expirée et son arrêt est terminal.

```text
ADR_SS_002_REEXAMINATION_RESULT=NEW_VERSION_REQUIRED
ADR_SS_002_V1_0_STATE=ACCEPTED_CONSUMED_AND_TERMINATED_BY_STOP
ADR_SS_002_V1_0_REUSABLE_FOR_RESUME=NO
ADR_SS_002_V1_1_STATUS=DRAFT_PENDING_OWNER_PROFILE_DECISION
OLD_GLOBAL_OWNER_GO_REUSABLE=NO
NETWORK_AUTHORIZED=NO
```

Une note non normative ou une nouvelle phrase technique ne satisferait pas ce déclencheur. La
prochaine décision doit être versionnée, explicite et limitée à un profil fermé.

## 4. Matrice factuelle des clauses v1.0

| Sujet | Qualification après WO-021 | Fait déterminant | Traitement v1.1 |
|---|---|---|---|
| ADR-SS-001 v1.4 | `VALID` | Aucun endpoint, transport de secours, proxy, retry, fallback, polling ou scheduler ajouté | Conserver intégralement |
| Six familles allowlistées | `VALID` | J3/J4/J5 restent limités aux six familles existantes | Conserver |
| Corpus D1/D2/D3 | `VALID_WITH_HISTORY` | D1 exécuté ; D2/D3 non commencés | Distinguer D1 gelé et cibles futures |
| Dossier de remplacement | `PROHIBITED` | Aucun remplacement autorisé ou réalisé | Conserver |
| Concurrence `1` | `VALID` | WO-021 ne change pas cette borne | Conserver |
| Minimum `3 s` | `VALID_REQUIREMENT_NEW_IMPLEMENTATION` | Fence et observation CDP qualifiés sous WO-021 | Qualifier prospectivement, jamais rétroactivement |
| Timeout `10 s`, limite 5 Mio | `VALID` | Aucun changement de contrat | Conserver |
| Retry, fallback, polling, scheduler | `PROHIBITED` | Aucun changement autorisé | Conserver |
| Contexte Playwright neuf | `VALID` | Isolation et cleanup requalifiés | Conserver |
| HAR, trace, vidéo, capture, téléchargement, `storageState` | `PROHIBITED` | Aucun artefact conservé | Conserver |
| `404` natif J4/J5 | `VALID` | Sémantiques inchangées | Conserver |
| Arrêt global | `VALID_AND_EXERCISED` | WO-019 a été arrêté conformément à la règle | Conserver |
| Ledger procédural | `VALID_BASELINE_CHANGED` | Aucun compteur transactionnel global ; baseline désormais 20 | Partir de 20, jamais de 0 |
| Fence global runtime | `NEW_MATERIAL_FACT` | WO-021 ajoute une garde exécutoire cross-worker/cross-campaign | Déclencheur §9 et nouvelle qualification normative |
| Une série seulement | `CONSUMED` | La série a commencé et s'est terminée par `STOPPED` | Non réutilisable |
| Ancien go global | `CONSUMED_AND_EXPIRED` | Usage unique, fenêtre expirée, arrêt terminal | Nouveau go distinct requis |
| Ancienne readiness | `HISTORICAL_ONLY` | Le code runtime a changé | Rejouer sur le commit exact |
| Sauvegarde/restauration V28 | `HISTORICAL_NOT_FRESH` | Couverture max snapshot `794`, antérieure aux données D1 jusqu'au snapshot `814` et occurrences `778..781` | Nouvelle sauvegarde/restauration obligatoire |
| Rapport `STOPPED` | `IMMUTABLE` | Décrit correctement les 20 tentatives et l'arrêt | Rapport supplémentaire séparé |
| Sources officielles | `REFRESHED_RESTRICTIONS_AND_UNCERTAINTY_PERSIST` | Aucune permission/licence/quota applicable extrait | Reconnaissance propriétaire toujours requise |
| Intégration, production, VPS | `NOT_AUTHORIZED` | WO-021 ne change aucune décision d'exploitation | Conserver |
| Décision J9 finale | `NOT_TAKEN` | `KEEP_LOCAL` rejeté comme décision finale sans choix automatique d'une autre option | Rester séparée |

## 5. Profils fermés examinés

| Profil | Nouveaux appels max. | Maximum cumulé | D1 rejoué | Verdict autonome complet possible | Qualification |
|---|---:|---:|---|---|---|
| `CONTINUE_D2_D3_ONLY` | 8 | 28 | Non | Non | Exposition minimale ; rapport supplémentaire, consolidation au mieux `PARTIAL_BOUNDED` sous les règles actuelles |
| `RESTART_FULL_D1_D2_D3` | 38 | 58 | Oui | Oui | Nouvelle série complète possible, mais volume cumulé matériellement accru |
| `DO_NOT_RESUME` | 0 | 20 | Non | Non | Conserve WO-019 arrêté |

### 5.1 Recommandation factuelle

La recommandation du réexamen est `CONTINUE_D2_D3_ONLY`, car elle :

- ne répète pas vingt appels fonctionnellement réussis ;
- n'utilise pas les dix unités J3 non consommées comme réserve fongible ;
- conserve le corpus et l'ordre restant ;
- reste sous le plafond historique 38 ;
- limite les nouveaux appels à la partie jamais commencée.

Cette recommandation a une conséquence explicite : elle ne peut pas effacer le rapport
`STOPPED` ni produire une preuve homogène rétroactive du délai fournisseur D1. Le résultat
supplémentaire D2/D3 peut être `PASS`, mais la consolidation J9 reste au maximum
`PARTIAL_BOUNDED` tant que le critère existant exige une preuve fournisseur homogène.

Si le propriétaire recherche prioritairement un nouveau `PASS` autonome sur tout le corpus, il doit
choisir explicitement `RESTART_FULL_D1_D2_D3`, accepter jusqu'à 38 nouveaux appels et un maximum
cumulé de 58. Cette extension n'est pas déduite du mot « reprise » et exigera une réécriture du
draft v1.1 avant acceptation.

## 6. Profil normatif proposé pour ADR-SS-002 v1.1

Le draft v1.1 est fondé sur le profil recommandé `CONTINUE_D2_D3_ONLY`.

### 6.1 Corpus et ordre

| État | Dossier | Provider ID | Identité canonique | Action |
|---|---|---:|---|---|
| Gelé | D1 — Cittadella / Atalanta U23 | `16691018` | `f4713f80-4769-3656-ba51-61d8ac1aa814` | Aucune réexécution fournisseur |
| Cible 1 | D2 — Barracas Central / Rosario Central | `16671566` | `da075869-34d4-3d42-83d2-613583691845` | J4 puis J5 |
| Cible 2 | D3 — Lille / PSG | `16310930` | `c40066c9-987b-38d9-b415-869a453d2ad6` | J4 puis J5 |

Ordre fermé :

```text
J4_D2
J5_D2_STATISTICS_INCIDENTS_LINEUPS
J4_D3
J5_D3_STATISTICS_INCIDENTS_LINEUPS
```

### 6.2 Ledger

| Segment | Réservation maximale | Cumul maximal |
|---|---:|---:|
| Historique gelé | 20 | 20 |
| J4 D2 | 1 | 21 |
| J5 D2 | 3 | 24 |
| J4 D3 | 1 | 25 |
| J5 D3 | 3 | 28 |

```text
HISTORICAL_DIRECT_ATTEMPTS_FROZEN=20
CONTINUATION_MAXIMUM_NEW_DIRECT_ATTEMPTS=8
MAXIMUM_CUMULATIVE_DIRECT_ATTEMPTS_AFTER_CONTINUATION=28
ORIGINAL_HARD_CEILING=38
ORIGINAL_HARD_CEILING_INCREASED_UNDER_RECOMMENDED_PROFILE=NO
UNALLOCATED_HEADROOM=10_NOT_AUTHORIZED
UNUSED_J3_RESERVATION_REALLOCATED=NO
D1_REEXECUTION_AUTHORIZED=NO
REPLACEMENT_DOSSIER_ALLOWED=NO
```

Le plafond propre à la continuation refuse les contre-épreuves `28 + 1` et `26 + 3`. Le plafond
historique secondaire refuse aussi `38 + 1` et `36 + 3`. La marge non utilisée ne peut servir à
aucun retry, rejeu, dossier supplémentaire ou seconde continuation. Si une protection
transactionnelle automatique contre le dépassement devient obligatoire, un nouveau Work Order de
code sera requis.

### 6.3 Runtime et arrêts

```text
MAXIMUM_CONCURRENCY=1
MINIMUM_PROVIDER_NETWORK_START_DELAY=3s
REQUEST_TIMEOUT=10s
MAX_RESPONSE_BODY=5MiB
AUTOMATIC_RETRY=0
FALLBACK=0
POLLING=0
SCHEDULER=0
LIVE_MODE=0
CACHE_FORCING=NO
```

Le fence WO-021 est une garantie prospective. Toute perte de preuve temporelle, interruption,
timestamp incohérent, réponse du document principal servie depuis le cache navigateur/CDP, service
worker, prefetch, redirection, HTML/challenge, réponse sensible, corps supérieur à 5 Mio, erreur de
persistance, lease ou cleanup arrête la continuation. Un `404` natif J4/J5 conserve les sémantiques
existantes et borne le résultat supplémentaire à `PARTIAL_BOUNDED`.

Le cache métier cache-first existant reste autorisé : un cache hit frais vaut zéro appel direct et
borne la dimension mesurée, sans créer de timestamp fournisseur fictif.

### 6.4 Nouveau go

L'ancien go est irréutilisable. Le nouveau go éventuel devra identifier :

- WO-019 et ADR-SS-002 v1.1 accepté ;
- le rapport historique et son hash ;
- la baseline gelée de 20 ;
- D2 et D3 exacts et l'ordre fermé ;
- le plafond nouveau 8 et le cumul maximum 28 ;
- une fenêtre UTC `[FROM,TO)` strictement inférieure ou égale à 60 minutes ;
- l'acteur unique.

Il sera consommé au premier claim J4 D2. Une seconde utilisation, un redémarrage applicatif, une
deuxième instance, la fin de fenêtre, un incident ou la fin de D3 le terminera. Les confirmations
techniques fraîches restent requises pour chaque sous-campagne.

### 6.5 Rapports et verdicts

Le rapport historique reste inchangé. Un rapport supplémentaire distinct consignera D2/D3,
l'observation CDP, les arrivées serveur, le ledger, les hashes et le cleanup.

```text
ORIGINAL_REPORT_RESULT=STOPPED
ORIGINAL_REPORT_REWRITTEN=NO
SUPPLEMENTAL_D2_D3_RESULT=<PASS|PARTIAL_BOUNDED|STOPPED>
CONSOLIDATED_EVIDENCE_MAXIMUM=PARTIAL_BOUNDED
J9_FINAL_DECISION=NOT_TAKEN
```

Changer cette règle pour produire un `PASS` consolidé malgré la lacune D1 constituerait une
modification propriétaire matérielle du critère, pas une conséquence technique de WO-021.

## 7. Revue officielle rafraîchie

Consultation documentaire effectuée le `2026-08-31T10:18:51.8559197Z` :

| URL officielle | Accessibilité observée | Fait extrait | Limite |
|---|---|---|---|
| `https://www.sofascore.com/en-us/terms-and-conditions` | Page accessible, corps direct dynamique | Index de la même URL : dernière mise à jour déclarée `2024-09-18`, usage personnel/non commercial, restrictions sur charge serveur par requêtes automatisées, agrégation, scraping, reproduction et extraction substantielle sans consentement, avec réserves légales | Aucun droit applicable aux endpoints du laboratoire extrait |
| `https://api.sofascore.com/api/docs/external` | Accessible, titre `Sofascore API`, aucun corps exploitable extrait | Point d'entrée officiel présent | Aucune authentification, licence, tarification, limite ou permission extraite |
| `https://www.sofascore.com/robots.txt` | Accessible, 181 lignes | Règles et sitemaps pour l'hôte `www` | Aucune règle `/api`, `Allow` ou `Crawl-delay` ; absence non assimilée à une permission |
| `https://corporate.sofascore.com/contact` | Accessible | Canal `Product -> API` présent | Canal de contact seulement |
| `https://corporate.sofascore.com/widgets` | Accessible | Intégration officielle par iframe publiée | Ne documente pas l'extraction ou le stockage via les endpoints du laboratoire |

```text
OFFICIAL_SOURCE_REVIEW_REFRESHED_AT_UTC=2026-08-31T10:18:51.8559197Z
TERMS_LATEST_DECLARED_UPDATE=2024-09-18
AUTOMATED_SERVER_BURDEN_RESTRICTED=YES
SCRAPING_AGGREGATION_REPRODUCTION_RESTRICTED_WITHOUT_EXPLICIT_CONSENT=YES_WITH_LEGAL_RESERVATION
EXPLICIT_PERMISSION_FOR_LAB_ENDPOINTS_EVIDENCED=NO
API_AUTHENTICATION_TERMS_EXTRACTED=NO
API_RATE_LIMIT_EXTRACTED=NO
API_LICENSE_EXTRACTED=NO
LEGAL_CONCLUSION=NOT_PROVIDED
```

Le transport Playwright et l'option VPS future ne transforment pas ces faits en permission.

## 8. Portes cumulatives avant tout nouveau go

1. clôture WO-021 intégrée dans l'historique exact destiné à WO-019 ;
2. choix propriétaire du profil de v1.1 ;
3. draft v1.1 finalisé puis accepté explicitement par un second bloc propriétaire ;
4. revue officielle encore fraîche à la date d'acceptation ou du go ;
5. readiness hors ligne fraîche sur le commit exact ;
6. sauvegarde chiffrée V28 post-arrêt et restauration isolée couvrant au moins le snapshot `814`,
   les occurrences jusqu'à `781` et les vingt tentatives gelées ;
7. manifeste gelé et corroboration indépendante du ledger ;
8. nouveau go global explicite à usage unique.

La readiness inclura au minimum :

```text
STANDARD_VERIFY=PASS_943_OR_CURRENT_HIGHER_COUNT
INTEGRATION_VERIFY=PASS_67_OR_CURRENT_HIGHER_COUNT
FLYWAY_SCHEMA=V28
VERIFY_LOCAL_PROVIDER_NETWORK=NO
DOCKER_COMPOSE_CONFIG=PASS
PROVIDER_ACCESS_PERFORMED=NO
LOOPBACK_J3=PASS_21_WORKER_PLUS_14_CHROMIUM
LOOPBACK_J4=PASS_21_WORKER_PLUS_14_CHROMIUM
LOOPBACK_J5=PASS_21_WORKER_PLUS_14_CHROMIUM
SUPERVISOR_TESTS=PASS_40_OR_CURRENT_HIGHER_COUNT
COORDINATOR_TESTS=PASS_8_OR_CURRENT_HIGHER_COUNT
MINIMUM_NETWORK_START_GAPS=PASS_GE_3_SECONDS
CROSS_WORKER_NETWORK_START_GAPS=PASS_GE_3_SECONDS
CROSS_CAMPAIGN_NETWORK_START_GAPS=PASS_GE_3_SECONDS
STOP_DURING_DELAY_NEW_REQUEST_COUNT=0
CONTINUATION_LEDGER_28_PLUS_1=REJECTED
CONTINUATION_LEDGER_26_PLUS_3=REJECTED
HISTORICAL_LEDGER_38_PLUS_1=REJECTED
HISTORICAL_LEDGER_36_PLUS_3=REJECTED
OLD_GLOBAL_OWNER_GO_REUSE=REJECTED
LISTENER_8087_COUNT=0
RESIDUAL_OWNED_PROCESS_COUNT=0
FORBIDDEN_BROWSER_ARTIFACT_COUNT=0
SECRET_AND_RAW_PAYLOAD_SCAN=PASS
NETWORK_FLAGS_DEFAULT_FALSE=PASS
```

La sauvegarde V28 qualifiée avant la série v1.0 ne suffit pas : elle s'arrêtait au snapshot `794`
alors que la base post-arrêt contient notamment les snapshots `810`, `813`, `814` et les occurrences
`778..781`.

## 9. Choix propriétaire attendu

Le réexamen est terminé. Le prochain choix ne vaut toujours ni reprise ni go :

```text
J9_ADR_SS_002_REEXAMINATION_OWNER_DECISION=<SELECT_CONTINUE_D2_D3_ONLY|SELECT_RESTART_FULL_D1_D2_D3|DO_NOT_RESUME|REQUEST_CHANGES>
J9_ADR_SS_002_V1_0_STATE=ACCEPTED_CONSUMED_AND_TERMINATED_BY_STOP
J9_ADR_SS_002_V1_0_REUSABLE_FOR_RESUME=NO
J9_ADR_SS_002_V1_1_STATUS=DRAFT_PENDING_OWNER_PROFILE_DECISION

J9_RECOMMENDED_PROFILE=CONTINUE_D2_D3_ONLY
J9_HISTORICAL_DIRECT_ATTEMPTS_FROZEN=20
J9_RECOMMENDED_MAXIMUM_NEW_DIRECT_ATTEMPTS=8
J9_RECOMMENDED_MAXIMUM_CUMULATIVE_DIRECT_ATTEMPTS=28
J9_ORIGINAL_HARD_CEILING_38_INCREASED_UNDER_RECOMMENDED_PROFILE=NO
J9_D1_REEXECUTION_RECOMMENDED=NO
J9_SUPPLEMENTAL_REPORT_REQUIRED_IF_RESUME=YES
J9_CONSOLIDATED_EVIDENCE_MAXIMUM_UNDER_RECOMMENDED_PROFILE=PARTIAL_BOUNDED

J9_PROVIDER_NETWORK_AUTHORIZED=NO
J9_WO019_PROVIDER_CAMPAIGN_RESUME_AUTHORIZED=NO
J9_NEW_PROVIDER_GO_GRANTED=NO
J9_FRESH_OFFLINE_READINESS_REQUIRED_IF_RESUME=YES
J9_FRESH_POST_STOP_V28_BACKUP_RESTORE_REQUIRED_IF_RESUME=YES
J9_INTEGRATION_OR_PRODUCTION_AUTHORIZED=NO
J9_FINAL_DECISION=NOT_TAKEN
J9_FUTURE_VPS_PRODUCTION_OPTION=NOT_EXCLUDED_BUT_NOT_AUTHORIZED
```

Si `SELECT_CONTINUE_D2_D3_ONLY` est choisi, le draft normatif actuel pourra être soumis pour une
acceptation v1.1 distincte. Si `SELECT_RESTART_FULL_D1_D2_D3` est choisi, ADR-SS-002 v1.1 devra
d'abord être réécrit avec le plafond nouveau 38, le cumul 58 et une nouvelle série autonome.

## 10. Critères de clôture de WO-022

WO-022 restera actif jusqu'à :

- décision propriétaire explicite sur le profil ;
- alignement du draft v1.1 sur ce profil ;
- soumission du bloc d'acceptation ou de refus d'ADR-SS-002 v1.1.

Même sa clôture future ne vaudra pas readiness, sauvegarde, manifeste, go ou accès fournisseur.

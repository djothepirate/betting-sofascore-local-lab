# WO-SS-20260823-011 — Qualification comparative Playwright / FlareSolverr pour J5 — Playwright retenu

- **Statut :** `VALIDATED`
- **Date :** 2026-08-23
- **Dernière mise à jour :** 2026-08-27
- **Date de démarrage documentaire :** 2026-08-23
- **Base locale :** `6c9a6ffef14efbd1dae93177e2a9d3d04470d6ba`
- **Branche :** `codex/j5-playwright-qualification`
- **État initial du worktree :** `DIRTY_PRESERVED_OWNER_FLARESOLVERR_POC`
- **Jalon :** évolution complémentaire de la qualification réelle J5
- **Demande propriétaire :** créer un comparatif reproductible de Playwright et FlareSolverr sur
  les mêmes événements, sans élargir le périmètre fonctionnel J5
- **Solutions comparées :** `PLAYWRIGHT_JAVA`, `FLARESOLVERR_REFERENCE`
- **Événements de référence :** `FROZEN_25_PANEL_PASS`
- **Prototype FlareSolverr local actuel :** `PRESERVED_OUT_OF_SCOPE`
- **Autorisation actuelle :** `ADR_1_3_PLAYWRIGHT_SELECTED_FLARESOLVERR_REJECTED`
- **Implémentation d'un transport Playwright :** `DEFERRED_TO_SEPARATE_J3_J4_J5_WORK_ORDERS`
- **Développement et tests du banc local :** `AUTHORIZED`
- **Ajout de dépendance ou de binaire navigateur :** `AUTHORIZED_PINNED_OPT_IN`
- **Appel fournisseur pour la qualification :** `NOT_RUN_OWNER_DECISION_SUPERSEDES_CONDITIONAL_GO`
- **Appel fournisseur pendant les tests automatisés standards :** `FORBIDDEN`
- **Décision ADR-SS-001 :** `AMENDMENT_1_3_APPROVED_2026-08-27`
- **Migration :** `NONE_FOR_QUALIFICATION_DESIGN`
- **Polling, planification, watcher, fallback ou retry automatique :** `FORBIDDEN`
- **Concurrence fournisseur :** `1`
- **Résultat formel Q01-Q15 :** `INCONCLUSIVE_PHASE_A_FAILED_FLARE_FIDELITY_R19_CONFIRMED`
- **Résultat local des candidats :** `PLAYWRIGHT_S1_S25_PASS_FLARESOLVERR_S1_FAIL`
- **Décision propriétaire :** `PLAYWRIGHT_SELECTED_FOR_IMPLEMENTATION`
- **Disposition FlareSolverr :** `REJECTED_NO_FURTHER_QUALIFICATION_OR_INTEGRATION`
- **Périmètre cible Playwright :** `J3_J4_J5_AND_FUTURE_SOFA_ENDPOINT_MILESTONES`
- **Playwright local nominal :** `PASS`
- **Sidecar FlareSolverr réel local :** `FAIL_STATUS_AND_BYTE_FIDELITY`
- **Arrêt superviseur Playwright :** `PASS_PROCESS_TREE_PARTIAL_Q09`
- **Capture réseau OS indépendante :** `NICS_ALL_NO_PACKET_METADATA_Q08_INCONCLUSIVE_Q15_PARTIAL`
- **Mémoire corrigée :** `PLAYWRIGHT_S1_S25_PASS_FLARE_S1_SCOPE_CORRECTED_Q12_INCONCLUSIVE`
- **Scan et nettoyage r10-all :** `PASS_AT_RUN_LEVEL`
- **Essai S1 contrat 2.4 r11 :** `FAIL_BEFORE_GATE_RECOVERY_REQUIRED`
- **Essai S1 contrat 2.5 r15 :** `FAIL_RUNNER_NO_OBSERVATION_Q08_INCONCLUSIVE_Q15_PARTIAL`
- **Essai S1 contrat 2.5 r18 :** `FAIL_3_OF_3_EXECUTED_0_SUCCESS_0_FIDELITY_NETWORK_INCONCLUSIVE`
- **Essai S1 contrat 2.5 r19 :** `FAIL_CANDIDATE_AND_NETWORK_NEGATIVE_VERDICT_CHAIN_PASS`
- **Récupération r11 :** `A1_A3_FAIL_CLOSED_NO_MUTATION_A4_FAIL_CLOSED_A5_PASS`
- **Sonde PktMon filter-shape P1 :** `PASS_PROBE_UNCLASSIFIED_DECORATION_EXACT_INVENTORY_FALSE`
- **Sonde filter-shape P2 / version déclarée :** `PASS_PROBE_UNCLASSIFIED_DECORATION_EXACT_INVENTORY_FALSE_1_0_1`
- **Sonde filter-shape P3 / version :** `PASS_PROBE_OEM_VARIANT_AUTHENTICATED_EXACT_INVENTORY_FALSE_1_0_2_EVIDENCE_V2`
- **Sonde filter-shape P4 :** `PASS_EXACT_RAW_AUTHENTICATED`
- **Récupération A4/A5 / version :** `A4_FAIL_CLOSED_A5_PASS_2_1_0`
- **Scan sensible post-récupération :** `PASS_72_FILES_0_FINDING_APPEND_ONLY_TRANSACTIONAL`
- **Contrat réseau forward-only :** `2_5_0_S1_R19_EXECUTED_NETWORK_INCONCLUSIVE`
- **Contrat de verdict candidat négatif :** `R19_REAL_DOUBLE_SCAN_AND_68_TAMPERS_PASS`
- **Diagnostic à double observateur :** `CLOSED_NOT_PURSUED_AFTER_OWNER_DECISION`
- **S25 FlareSolverr instrumenté sous contrat corrigé :** `NOT_RUN_OWNER_DECISION_AFTER_S1_FAILURE`
- **Porte Phase A comparative :** `FAIL`
- **FlareSolverr fournisseur S1 / S25 :** `NOT_RUN_OWNER_DECISION_SUPERSEDES_CONDITIONAL_GO`
- **Playwright fournisseur S1 / S25 :** `NOT_RUN_BY_WO_011`
- **Trafic fournisseur exécuté :** `0`
- **Clôture :** `COMPLETED`

## 1. Objectif

Établir, avant toute décision d'architecture, si Playwright constitue une voie locale plus fidèle,
plus maîtrisable et plus durable que le prototype FlareSolverr pour acquérir les trois familles J5 :

```text
EVENT_STATISTICS
EVENT_INCIDENTS
EVENT_LINEUPS
```

Le comparatif porte exclusivement sur :

1. le taux de succès ;
2. la fidélité des statuts HTTP et des octets applicatifs ;
3. la durée d'une campagne d'une rencontre, puis de vingt-cinq rencontres ;
4. la mémoire de l'ensemble des processus propres au candidat ;
5. l'arrêt immédiat ;
6. le nettoyage du contexte après succès, erreur et arrêt ;
7. l'absence totale de données sensibles dans les logs.

Les deux candidats doivent recevoir le même manifeste immuable, dans le même ordre, sur le même
poste. Lors de sa définition, le Work Order ne choisissait pas encore de solution : il définissait
le protocole destiné à produire les preuves d'une décision humaine ultérieure. La décision finale
est désormais consignée en section 17.

Les statuts du laboratoire restent inchangés :

```text
EXPERIMENTAL
LOCAL_ONLY
NOT_PRODUCTION_APPROVED
NO_CRITICAL_DEPENDENCY
```

## 2. Situation de départ

Le propriétaire a réalisé localement un prototype FlareSolverr et rapporte des essais de
faisabilité concluants contre des campagnes J5. Les modifications correspondantes restent hors du
présent lot documentaire, non adoptées et non qualifiées par ce Work Order. Elles ne constituent
pas encore une mesure comparative, car Playwright et FlareSolverr n'ont pas été instrumentés sur un
manifeste, une fenêtre et des critères identiques.

Ce prototype ne peut pas non plus constituer la vérité de fidélité FlareSolverr en l'état : il
extrait le JSON depuis une représentation HTML, tandis que le sidecar expose le résultat de sa
commande et non nécessairement le statut et les octets amont. L'adaptateur de qualification devra
donc rendre une absence d'information explicitement non observable. Il ne pourra ni fabriquer un
statut, ni extraire une sous-chaîne, ni journaliser l'enveloppe complète pour obtenir un résultat
artificiellement comparable.

Le lot J5 hors ligne multi-match de `WO-SS-20260822-010` demeure strictement indépendant de cette
étude. Il ingère des preuves fournies par l'opérateur et ne doit recevoir aucun transport, navigateur,
sidecar, fallback ou acquisition automatique.

La qualification réelle J5 existante reste manuelle, séquentielle et bornée. Le port fonctionnel
`J5EventDataProviderTransport` et les parseurs locaux restent les frontières à préserver si une voie
navigateur est ultérieurement autorisée.

## 3. Décision ADR préalable

L'amendement 1.1 de l'ADR-SS-001 et la règle correspondante de `AGENTS.md` autorisent désormais
une exception Playwright locale, manuelle, opt-in et limitée à ce Work Order. La décision explicite
du propriétaire autorise l'installation versionnée, le développement et l'exécution du banc local,
ainsi que la préparation des campagnes réelles. Elle précise :

- qu'une qualification locale et bornée de Playwright est autorisée ;
- que cette qualification n'a pas pour objectif de contourner une interdiction d'accès ;
- qu'un `403`, un `429`, un challenge ou une redirection inattendue reste terminal, sans retry ni
  bascule automatique vers l'autre candidat ;
- qu'aucun proxy rotatif, changement d'adresse, profil furtif, CAPTCHA externe ou réutilisation de
  cookie, jeton ou session interceptés n'est permis ;
- que seul un contexte éphémère géré par le navigateur pendant une campagne peut être évalué, sans
  export ni réinjection de son état ;
- que l'arrêt humain global reste prioritaire et immédiatement observable ;
- que la décision ne rend pas Playwright appelable en production et ne modifie pas les statuts du
  laboratoire.

Cette décision autorise le développement de l'outillage de qualification et la phase A locale. Une
seconde confirmation humaine distincte reste requise pour exécuter les quatre campagnes réelles
définies au paragraphe 10 ; leur manifeste, leurs commandes et leurs contrôles peuvent être préparés
sans émettre de requête fournisseur.

## 4. Portée autorisée et implémentée

La décision ADR et l'autorisation d'implémentation permettent au lot d'ajouter uniquement :

- un adaptateur Playwright derrière une frontière de transport dédiée ;
- un adaptateur de mesure FlareSolverr servant de référence comparative ;
- un serveur HTTP local déterministe et ses fixtures de qualification ;
- un lanceur manuel, sans scheduler, qui exécute un seul candidat et une seule requête à la fois ;
- une instrumentation locale des durées, statuts, tailles, empreintes et consommations mémoire ;
- un arrêt opérateur commun aux deux candidats ;
- des contrôles d'allowlist et de nettoyage ;
- un rapport minimisé ne contenant aucun corps fournisseur, en-tête sensible, URL complète, cookie,
  jeton, profil, trace ou état de stockage.

Le comparatif reste sans effet métier durable : les octets existent seulement en mémoire le temps
de vérifier statut, taille, empreinte et compatibilité avec le parseur courant, puis sont détruits.
Il ne crée ni snapshot, ni occurrence, ni observation, et ne modifie pas les données importées par
les parcours J5 existants.

L'adaptateur candidat remettra au pipeline J5 le statut réellement observé et les octets applicatifs
reçus. Une extraction DOM, une reconstruction depuis `page_source` ou une transformation silencieuse
du statut ne peut pas servir de preuve fidèle.

## 5. Hors périmètre

Restent explicitement hors périmètre :

- la sélection définitive de Playwright ou de FlareSolverr avant production des preuves ;
- la fusion ou l'adoption automatique du prototype FlareSolverr existant ;
- la modification du parcours `/j5-import-batches` ou de sa transaction hors ligne ;
- tout appel depuis Maven, les tests standards, un démarrage applicatif ou une tâche planifiée ;
- tout fallback `HTTP direct -> navigateur`, `Playwright -> FlareSolverr` ou inverse ;
- tout retry, backoff, rafraîchissement, polling, live ou reprise automatique ;
- la parallélisation de requêtes ou de rencontres ;
- les proxys, la rotation d'adresse, le changement automatique d'identité ou la furtivité ;
- la résolution de CAPTCHA ou la poursuite après détection d'un challenge ;
- l'export de cookies, de `storageState`, d'un profil utilisateur ou d'une session FlareSolverr ;
- HAR, trace, vidéo ou capture d'écran dans les preuves commitées ;
- un nouveau parseur métier, une nouvelle famille d'endpoint ou une migration de persistance ;
- l'usage par le Betting Project principal, un VPS ou une interface autre que `127.0.0.1` ;
- une conclusion générale sur la capacité d'un candidat à éviter tous les futurs `403`.

Playwright n'est pas considéré comme un « solveur Cloudflare ». Son éventuelle qualification ne
signifie pas qu'il garantit l'accès futur ni qu'il autorise à franchir une décision de blocage.

## 6. Manifeste comparatif gelé

### 6.1 Constitution

Avant toute campagne réelle comparative des phases B/C, l'opérateur fige un manifeste minimal
contenant exactement les identifiants fournisseur des événements déjà retenus pour la campagne de
référence FlareSolverr. Il ne contient ni URL, ni payload, ni en-tête, ni donnée de session. La phase
A utilise un ordre de fixtures synthétiques déterministes distinct et ne doit jamais être présentée
comme une exécution du panneau réel.

```text
manifestVersion=j5-browser-candidate-comparison-v1
timezone=Europe/Paris
scenario.S1.events=1
scenario.S25.events=25
families=EVENT_STATISTICS,EVENT_INCIDENTS,EVENT_LINEUPS
order=providerEventId_ASC_THEN_STATISTICS_INCIDENTS_LINEUPS
maxConcurrency=1
automaticRetry=0
minimumDelayBetweenProviderStarts=3s
```

`S1` doit être un sous-ensemble de `S25`, de préférence son premier événement dans l'ordre canonique.
Les vingt-cinq événements doivent être terminés et suffisamment stables pour réduire les variations
de données entre les deux candidats. Le rapport consigne pour chaque événement uniquement son
`providerEventId`, son horaire observé, les équipes et le statut canonique préalable.

Le panneau est désormais gelé depuis PostgreSQL en lecture seule. Pour chaque identité, l'export
classe d'abord la dernière observation, puis retient les observations terminales
`PROVIDER_SNAPSHOT + finished`, ordonnées par horaire décroissant et identifiant fournisseur. Il
prend exactement vingt-cinq identités distinctes et produit uniquement les cinq colonnes minimales.
Le CSV porte le SHA-256
`9d12afe5ddf9b03d5e61022a3766e7e752df981a45254fba428c6d4320cfde99`. Le manifeste canonique
ordonné porte le SHA-256
`eb558bd241a35fca820ade30f8cb307700bf6c1d935ff00aa7910f98f9a6cb0d`; `S1` contient
`providerEventId=15272817` et `S25` contient les vingt-cinq événements. Une seconde génération a
retourné `UNCHANGED` pour les deux artefacts.

### 6.2 Immutabilité

Un SHA-256 est calculé sur la version du manifeste, la zone, les identifiants ordonnés, les trois
familles et les paramètres ci-dessus. Le même hash doit apparaître sur chaque exécution FlareSolverr
et Playwright.

Aucun événement ne peut être remplacé après le premier lancement. Une suppression, une variation
de disponibilité ou une erreur ponctuelle est un résultat à consigner, pas une raison de modifier
le panel. Un hash différent invalide le comparatif entier.

### 6.3 Versions figées

Le rapport enregistre avant la première mesure :

- le commit du laboratoire et l'état propre ou documenté du worktree ;
- Windows, Java et Spring Boot ;
- la version Java de Playwright et la révision Chromium installée ;
- le digest exact de l'image FlareSolverr et sa version de navigateur ;
- Docker Desktop ;
- les valeurs non sensibles de concurrence, délai, timeout et limite de taille ;
- l'heure UTC de début et de fin de chaque campagne.

Un changement de l'un de ces éléments crée une nouvelle série de mesures et un nouveau `runId`.

## 7. Référentiel local de fidélité

La fidélité primaire ne peut pas être démontrée par deux appels successifs à un service vivant : le
contenu peut légitimement évoluer entre eux. Elle est donc mesurée d'abord contre un serveur local
déterministe lié à `127.0.0.1`, sans aucun trafic SofaScore.

Le serveur expose, pour chacune des trois familles, des réponses fixes couvrant au minimum :

| Cas | Statut attendu | Corps attendu | But |
|---|---:|---|---|
| `JSON_200` | 200 | JSON UTF-8 fixe avec CRLF | succès nominal et absence de réencodage |
| `JSON_404` | 404 | enveloppe JSON fixe | indisponibilité explicite |
| `JSON_403` | 403 | enveloppe JSON fixe | arrêt terminal |
| `JSON_429` | 429 | enveloppe JSON fixe et `Retry-After` | absence de retry |
| `JSON_500` | 500 | enveloppe JSON fixe | erreur terminale fidèle |
| `REDIRECT_302` | 302 | cible locale hors allowlist | statut observé, cible jamais contactée |
| `HTML_CHALLENGE` | 403 | HTML fixe | détection sans parsing J5 |
| `GZIP_200` | 200 | JSON fixe compressé au transport | octets applicatifs décodés |
| `SLOW_RESPONSE` | 200 | JSON fixe retardé de 30 s | timeout et arrêt immédiat |
| `OVERSIZE` | 200 | corps de 5 Mio + 1 octet | refus avant persistance |
| `SENSITIVE_CANARY` | 200 | corps et en-têtes synthétiques marqués | scan exhaustif des sorties |

Pour chaque cas, le serveur détient la vérité de référence : statut, `Content-Type`, longueur des
octets applicatifs après décodage HTTP et SHA-256 de ces octets. Le candidat doit rendre exactement
ces quatre valeurs. Un statut masqué, synthétisé ou toujours ramené à `200` vaut
`STATUS_NOT_OBSERVABLE` et échoue à la porte de fidélité. Une reconstruction DOM ou une différence
d'un seul octet vaut `BODY_NOT_BYTE_FAITHFUL`.

La suite locale est exécutée dans un profil de qualification opt-in séparé. Elle n'entre jamais dans
les tests Maven standards et ne dispose d'aucune route Internet.

Playwright doit lire exclusivement `Response.status()`, `Response.body()` et le `Content-Type`
strictement utile. Sont interdits `Response.text()`, `page.content()`, le DOM, une sous-chaîne JSON ou
un réencodage UTF-8. Pour chaque fixture, `fixtureTargetHits` doit valoir `1` et
`unexpectedEgressRequests` doit valoir `0`.

## 8. Mesures et définitions

### 8.1 Taux de succès

Un emplacement est réussi uniquement lorsque le candidat fournit :

- le statut réellement reçu ;
- les octets sous la limite ;
- une réponse `2xx` compatible avec le JSON attendu, ou un `404` effectivement reçu et conservable
  comme indisponibilité explicite ;
- aucune détection de challenge, de redirection hors allowlist ou de schéma incompatible.

```text
P = 3 * plannedEventCount
A = attemptedEvidenceSlots
C = compatible2xxParsedJson + actualCompatible404
successRate = C / P * 100
coverageRate = A / P * 100
completeEventRate = eventsWithThreeCompatibleFamilies / plannedEventCount * 100
blockingRate = (http403 + http429 + challenge) / A * 100
networkAmplification = allObservedBrowserRequests / A
S1.P = 3
S25.P = 75
```

Les `403`, `429`, challenges, timeouts, dépassements de taille, statuts non observables et erreurs de
lecture sont des échecs. Aucun deuxième essai ne remplace le résultat. Pour être qualifié comme
transport d'une campagne atomique, un candidat doit produire `3/3` sur `S1`, puis `75/75` sur `S25`.
Le taux brut reste publié même lorsque cette porte échoue.

Le dénominateur reste toujours le nombre planifié `P`, jamais le nombre tenté `A`. Une campagne
réelle unique sur trois ou soixante-quinze emplacements produit un taux empirique dans cette fenêtre,
pas une probabilité de réussite à long terme.

### 8.2 Fidélité des statuts et des octets

La fidélité primaire est la concordance exacte avec le référentiel local :

```text
statusFaithful = actualStatus == expectedStatus
bodyFaithful = actualDecodedLength == expectedDecodedLength
               AND actualDecodedSha256 == expectedDecodedSha256
contentTypeFaithful = normalizedActualContentType == normalizedExpectedContentType
```

La campagne réelle ajoute une comparaison secondaire entre candidats. Pour chaque emplacement, le
rapport présente statut, taille et SHA-256 minimisés. Une divergence n'est jamais arbitrée en
choisissant silencieusement un candidat : elle vaut `LIVE_DIVERGENCE_TO_REVIEW`. Le corps lui-même
n'est ni affiché, ni conservé dans le rapport.

### 8.3 Durée

Deux chronomètres monotones distincts sont requis :

```text
startupDurationMs = candidateReadyAt - candidateProcessStartAt
campaignDurationMs = lastTerminalEvidenceAt - firstEvidenceStartAt
```

Le rapport fournit pour `S1` et `S25` : démarrage à froid, durée totale de campagne et nombre
d'emplacements. Le délai commun de trois secondes entre débuts de requêtes est inclus dans la durée
de campagne et rappelé avec le résultat. Les distributions de latence par emplacement ne constituent
pas une porte de ce Work Order : elles exigeraient une preuve plus détaillée sans répondre au besoin
fonctionnel demandé sur la durée totale.

### 8.4 Mémoire

La mémoire est échantillonnée avec un intervalle cible commun de 2 000 ms pendant toute la série.
Le rapport publie l'intervalle demandé ainsi que les écarts réel maximal et médian observés. Une
série n'est comparable que si les deux candidats utilisent la même définition et un écart maximal
au plus égal à 2 500 ms :

- Playwright : somme du `WorkingSet64` du runner Maven/Java de qualification, du driver et de tout le
  sous-arbre Chromium créé pour le `runId` ;
- FlareSolverr : somme du `WorkingSet64` du runner Maven/Java et de la mémoire du conteneur et de son
  navigateur via `docker stats`, avec les deux composants publiés séparément ;
- application commune : valeur rapportée séparément pour ne pas être attribuée deux fois.

Chaque campagne publie `warmBaselineMiB`, `peakMiB`, `p95MiB`, `incrementalPeakMiB`,
`residualAt5sMiB` et `residualAt30sMiB`. La tendance compare la médiane des cinq premiers et des
cinq derniers échantillons actifs de `S25`. Le coût Docker Desktop/WSL reste publié séparément et
n'est pas ajouté une seconde fois au total FlareSolverr.

```text
incrementalPeakMiB <= 1024 MiB
residualAt30sMiB <= warmBaselineMiB + max(64 MiB, warmBaselineMiB * 0.10)
median(lastFiveActiveSamplesMiB) <= median(firstFiveActiveSamplesMiB) + 64 MiB
createdProcessResidue=0
```

Le pic est une borne de qualification locale et non une promesse de dimensionnement de production.
Le script suit uniquement les PID créés pour le `runId` ; il ne termine jamais tous les processus
`chrome` par leur nom, afin de ne pas toucher au navigateur personnel de l'opérateur.

### 8.5 Arrêt immédiat

L'arrêt est testé hors ligne sur un plan de vingt-cinq rencontres dont le serveur local suspend une
réponse connue. L'opérateur déclenche l'arrêt global après le démarrage de cette requête.

```text
stopAcknowledgedWithin=500ms
newRequestStartedAfterStop=0
inFlightRequestCancelledWithin=2s
contextAndSessionClosedWithin=5s
automaticRetryAfterStop=0
createdProcessResidue=0
```

Le test est déterministe grâce à un latch local ; il ne dépend pas d'une latence fournisseur. Aucun
appel réel supplémentaire n'est autorisé pour démontrer l'arrêt.

Le service J5 actuel vérifie l'arrêt avant d'entrer dans l'appel bloquant ; cela ne prouve pas encore
une annulation en vol. Tant qu'un worker ou processus candidat supervisé ne possède pas l'appel et
ne peut pas l'interrompre dans les délais ci-dessus, cette porte reste `FAIL`. Playwright Java n'est
pas piloté concurremment depuis un thread Web arbitraire : son worker dédié reste propriétaire du
navigateur et de son contexte pendant toute la campagne.

### 8.6 Nettoyage du contexte

Les scénarios `SUCCESS`, `HTTP_ERROR`, `TIMEOUT`, `OVERSIZE`, `OPERATOR_STOP` et
`UNEXPECTED_EXCEPTION` doivent tous aboutir à :

```text
OPEN_PAGE_COUNT=0
OPEN_BROWSER_CONTEXT_COUNT=0
EXPORTED_COOKIE_COUNT=0
EXPORTED_STORAGE_STATE_COUNT=0
PERSISTENT_PROFILE_COUNT=0
ACTIVE_FLARESOLVERR_SESSION_COUNT=0
TEMP_HAR_COUNT=0
TEMP_TRACE_COUNT=0
TEMP_VIDEO_COUNT=0
TEMP_SCREENSHOT_COUNT=0
TEMP_DOWNLOAD_COUNT=0
QUALIFICATION_TEMP_FILE_COUNT=0
CREATED_PROCESS_RESIDUE_COUNT=0
```

Playwright utilise un `BrowserContext` non persistant neuf par campagne. FlareSolverr utilise une
session neuve explicitement détruite en `finally`. Aucun contexte ou profil Chrome personnel n'est
admis. Une fermeture du processus qui laisse une session, un profil ou un fichier temporaire échoue
à la porte de nettoyage.

### 8.7 Absence de données sensibles dans les logs

Le serveur local injecte des canaris synthétiques distincts dans un corps, `Authorization`,
`Cookie`, `Set-Cookie`, paramètre d'URL, `localStorage` et `sessionStorage`. Ces valeurs ne sont
jamais des secrets réels. Après chaque scénario, un scan porte sur stdout, stderr, logs Spring, logs du driver,
Chromium, Docker et FlareSolverr.

La présence d'un seul canari échoue immédiatement. Le scan interdit également :

- tout corps de réponse, même tronqué ;
- toute valeur d'en-tête ;
- toute URL complète ou chaîne de requête ;
- tout cookie, jeton, identifiant de session ou `storageState` ;
- toute trace, HAR ou capture encodée dans un log ;
- tout payload de commande envoyé au sidecar.

Les seules données autorisées sont `runId`, candidat, famille logique, identifiant fournisseur,
statut, taille, SHA-256, timestamps, durée, compteur, code d'erreur local et métrique mémoire.

```text
sensitiveCanaryOccurrences=0
rawBodyLogOccurrences=0
fullUrlLogOccurrences=0
headerValueLogOccurrences=0
```

## 9. Contrôle des routes et absence de trafic caché

Chaque candidat doit être restreint aux trois URI exactes du manifeste, une à la fois. Les routes de
service worker sont bloquées et toute navigation, redirection ou sous-requête hors allowlist est
annulée puis comptée comme incident terminal.

```text
unexpectedNetworkRequestCount=0
unexpectedRedirectCount=0
serviceWorkerCount=0
maxConcurrentProviderRequests=1
automaticRetryCount=0
```

Si FlareSolverr ne permet pas d'observer ou de borner ce trafic avec une preuve équivalente, le
résultat est `NETWORK_SIDE_EFFECTS_NOT_PROVABLE` et la porte échoue. Une réussite fonctionnelle ne
compense pas une absence de preuve de sûreté.

Une capture vide ou non parsable ne satisfait jamais Q08 : la preuve doit contenir les routes
locales attendues exactes, zéro route inattendue et zéro route non attribuée. Les bras PktMon
`NICS` et `ALL` ayant tous deux exposé zéro métadonnée paquet à la frontière Windows/Docker, une
instrumentation suivante nécessiterait deux observateurs indépendants : un observateur borné dans
l'espace réseau Linux exact du sidecar pour Q08, puis une preuve Windows structurée des sockets et
PID PRE/ACTIVE/POST pour Q15. Cet élargissement n'est pas autorisé par le présent Work Order ni par
les go conditionnels FlareSolverr S1/S25 ; il exige une décision propriétaire et une évolution de
gouvernance distinctes.

## 10. Ordre d'exécution

### 10.1 Phase A — qualification locale obligatoire

1. vérifier les versions, le manifeste et son SHA-256 ;
2. démarrer uniquement le serveur de référence sur `127.0.0.1` ;
3. exécuter tous les cas de fidélité avec FlareSolverr depuis un état neuf ;
4. nettoyer, stabiliser et mesurer ;
5. exécuter les mêmes cas avec Playwright depuis un état neuf ;
6. exécuter pour chaque candidat l'arrêt déterministe et toutes les sorties anormales ;
7. scanner tous les logs et répertoires temporaires ;
8. refuser la phase réelle si une porte obligatoire échoue.

Cette phase peut être répétée sans trafic externe. Elle est la seule phase autorisée pour mettre au
point l'instrumentation.

### 10.2 Phase B — campagne réelle `S1`

Après validation de la phase A, décision ADR et go/no-go humain spécifique :

1. vérifier l'arrêt global et la configuration locale avant chaque candidat ;
2. exécuter FlareSolverr sur les trois emplacements de `S1`, sans retry ;
3. réappliquer l'arrêt global, détruire le contexte et attendre la stabilisation ;
4. exécuter Playwright sur les trois mêmes emplacements, sans retry ;
5. réappliquer l'arrêt global, détruire le contexte et scanner les preuves ;
6. arrêter si un `403`, `429`, challenge, trafic inattendu ou incident sensible survient.

Le propriétaire a donné le go distinct `FLARESOLVERR_AUTHORIZE_S1`, sous réserve des portes non
compensables. Aucun go Playwright réel n'a été donné. Le go FlareSolverr ne déclenche rien à lui
seul : la readiness du 2026-08-24 échoue sur `LOCAL_PHASE_A` et `CLEAN_REAL_WORKTREE`, l'ADR 1.2
interdit encore le sidecar contre le fournisseur, et le runner réel reste volontairement inerte.

### 10.3 Phase C — campagne réelle `S25`

La phase C exige `3/3` sur les deux candidats et toutes les portes de sûreté de `S1`. Pour réduire
un biais d'ordre, elle inverse l'ordre des candidats : Playwright exécute les soixante-quinze
emplacements, puis FlareSolverr les mêmes soixante-quinze emplacements. Les campagnes ne sont jamais
simultanées.

Le propriétaire a également donné le go conditionnel `FLARESOLVERR_AUTHORIZE_S25`, dont les
prédécesseurs restent obligatoires. Comme `S1` réel n'est pas exécuté, que Playwright réel n'est pas
autorisé et que la phase A FlareSolverr échoue, ce go reste dormant.

À tout instant, un seul appel est actif, chaque début est séparé du précédent d'au moins trois
secondes, et le premier incident terminal arrête le candidat courant sans retry. La campagne de
l'autre candidat n'est pas lancée si l'incident indique un blocage fournisseur ou un challenge.

Le protocole réel autorise donc au maximum quatre campagnes :

```text
FlareSolverr S1 = 3 emplacements
Playwright S1 = 3 emplacements
Playwright S25 = 75 emplacements
FlareSolverr S25 = 75 emplacements
TOTAL_MAXIMUM_TARGETS = 2 * (3 + 75) = 156
```

Aucun passage additionnel n'est implicitement autorisé. Une série invalide ou techniquement
inconclusive exige une nouvelle décision humaine avant répétition.

## 11. Matrice de qualification

| ID | Preuve | Porte de qualification |
|---|---|---|
| Q01 | même SHA-256 de manifeste sur toutes les campagnes | obligatoire |
| Q02 | statuts locaux exacts, y compris 404/403/429 | 100 %, obligatoire |
| Q03 | tailles et SHA-256 locaux exacts | 100 %, obligatoire |
| Q04 | `S1` | 3/3 pour poursuivre |
| Q05 | `S25` | 75/75 pour qualifier l'usage atomique |
| Q06 | concurrence maximale | exactement 1 |
| Q07 | retry et fallback | exactement 0 |
| Q08 | routes locales attendues, inattendues et non attribuées | attendues exactes ; inattendues et non attribuées exactement 0 |
| Q09 | arrêt acquitté / appel interrompu / fermeture | au plus 500 ms / 2 s / 5 s |
| Q10 | pages, contextes et sessions après chaque scénario | exactement 0 |
| Q11 | profils et fichiers temporaires résiduels | exactement 0 |
| Q12 | pic, tendance et retour mémoire après nettoyage | quatre bornes mémoire acquises |
| Q13 | canaris ou données sensibles dans tous les logs | exactement 0 |
| Q14 | tests Maven standards | zéro accès réseau et aucun navigateur lancé |
| Q15 | liaison des services de qualification | boucle locale Windows prouvée indépendamment du chemin conteneur |

Les portes Q01 à Q15 sont non compensables. Une durée plus faible ou un meilleur taux de succès ne
peut jamais compenser une perte de fidélité, une fuite sensible, un trafic caché ou un arrêt incomplet.

État courant : le manifeste Q01 est gelé mais n'a pas encore été présenté à quatre campagnes réelles.
Q02-Q05 passent pour Playwright local et échouent pour le vrai sidecar FlareSolverr local. Q06-Q07
passent. Les campagnes instrumentées `flare-local-s1-20260824-r9` (`NICS`) et
`flare-local-s1-20260824-r10-all` (`ALL`) prouvent toutes deux le cycle ETW et le nettoyage, mais
aucune ne restitue de ligne de métadonnées paquet : zéro route normalisée, deux routes locales
manquantes et zéro route inattendue. Q08 reste `INCONCLUSIVE_CAPTURE_OR_ROUTE_PARSE` et Q15
`PARTIAL_CONTAINER_PATH_ONLY`. Q09 reste inconclusif ; Q10-Q11 restent partiels malgré le nettoyage
`PASS` au niveau du run `r10-all`. Playwright dispose de séries S1 et S25 corrigées ; le périmètre
mémoire FlareSolverr S1 est corrigé dans `r10-all`, runner Java et conteneur compris, mais l'écart
maximal observé de `3 019 ms` dépasse la borne de `2 500 ms` et aucun S25 FlareSolverr corrigé n'est
acquis : Q12 reste inconclusif. Le scan terminal chaîné `r10-all` passe au niveau du run ; après la
récupération A5, le scan officiel r11 couvre les racines run et sidecar, 72 fichiers, zéro finding et
zéro runtime brut. Les contrats transactionnels synthétiques passent et Q13 est désormais acquis.
Q14 reste bloqué par les quatre échecs de baseline du POC propriétaire. La matrice globale ne passe
donc pas.

Le nouvel essai `flare-local-s1-20260827-r15-contract-v4` exécute bien le contrat forward-only
`2.5.0` : préflight v4, état v5, attribution v4, cycle `QueryAllTracesW` exact et gate authentifié
ouvert. Il s'arrête toutefois avec `runnerExitCode=1` avant la première observation ; les métriques
candidates contiennent zéro campagne. Aucun taux de succès, aucune fidélité, aucune latence et aucune
durée S1 ne peuvent donc être dérivés de ce run. La capture ne fournit toujours aucune métadonnée
paquet : deux routes locales attendues manquent, aucune route inattendue n'est observée, Q08 reste
`INCONCLUSIVE_CAPTURE_OR_ROUTE_PARSE` et Q15 `PARTIAL_CONTAINER_PATH_ONLY`. Le nettoyage PktMon et
Docker est complet, le scan terminal passe sur 16 fichiers avec zéro finding, mais l'acquittement de
l'arrêt arrive en `514 ms`, soit `14 ms` au-delà de la borne Q09. La Phase A reste `FAIL` et S25
reste bloqué.

Le run `flare-local-s1-20260827-r18-forward-only-250` franchit à son tour le gate et exécute les
trois cibles de la fixture locale. Il constitue une preuve candidate négative cohérente : `3/3`
cibles terminées, mais `0/3` succès, `0/3` statuts exacts, `0/3` types de contenu exacts et `0/3`
corps exacts. Le premier scan sensible passe avec zéro finding et une liaison des cinq preuves
attendues. Le second scan reste absent parce que l'orchestrateur exigeait à tort
`candidateMetricsResult=PASS` pour reconnaître une preuve structurellement recevable. Cet échec de
protocole ne requalifie pas le candidat : la fidélité FlareSolverr reste `FAIL` et la preuve réseau
reste inconclusive.

Le correctif forward-only sépare désormais la validité structurelle d'un résultat candidat
`PASS` ou `FAIL` de sa qualification fonctionnelle, qui continue d'exiger le succès du harnais et
la fidélité complète. Le contrat hors ligne exerce le double scan d'un verdict négatif cohérent et
une matrice de 68 falsifications de schéma, types, compteurs et cohérence. Le verdict global demeure
`FAIL`; le run immuable `r19` documenté plus bas produit ensuite les deux scans réels chaînés sans
convertir ce verdict en succès. S25 reste bloqué par S1 et la Phase A.

Le script PktMon borne chaque bras à 42 octets, un ETL circulaire de 16 Mio et un filtre exact sur
l'IPv4 du sidecar, puis supprime les artefacts bruts. La sonde `r5` et les deux bras `r9`/`r10-all`
prouvent PRE, ACTIVE, BEFORE_STOP et POST par `QueryAllTracesW`, avec fournisseur unique et pertes
nulles. Le passage de `NICS` à `ALL` n'a rendu aucune métadonnée supplémentaire et ne lève donc ni
Q08 ni Q15. Toute instrumentation locale à deux observateurs indépendants dépasse ce diagnostic A/B
et exige un go propriétaire distinct ; les go FlareSolverr S1/S25 déjà reçus ne l'autorisent pas.

## 12. Résultats courants

### 12.1 Phase A locale comparative

| Mesure | FlareSolverr S1 local | Playwright S1 local | FlareSolverr S25 local | Playwright S25 local |
|---|---:|---:|---:|---:|
| emplacements exécutés / attendus | 3/3 | 3/3 | 75/75 | 75/75 |
| emplacements fidèles / attendus | 0/3 | 3/3 | 0/75 | 75/75 |
| statuts amont exacts | 0/3 | 3/3 | 0/75 | 75/75 |
| types de contenu exacts | 0/3 | 3/3 | 0/75 | 75/75 |
| corps exacts | 0/3 | 3/3 | 0/75 | 75/75 |
| démarrage du candidat | 724 ms | 2 458 ms | 523 ms | 313 ms |
| durée de campagne | 6 997 ms | 6 419 ms | 236 904 ms | 229 657 ms |
| pic heap Java du runner | 76 331 256 octets | 79 023 520 octets | 89 048 664 octets | 107 735 584 octets |

Cette première série est conservée comme diagnostic mais n'est pas une preuve Q12 comparable. Le
sampler calcule base, pic et p95 seulement pendant l'activité, puis mesure séparément les résidus.
Playwright (`phasea-playwright-comparable-20260824a`) : base `186,551 Mio`, pic
`570,527 Mio`, p95 `566,664 Mio`, pic incrémental `383,976 Mio`, résidus `0 / 0 Mio` à 5/30 s,
zéro processus résiduel. FlareSolverr (`phasea-flare-sidecar-20260824b`) : base `79,74 Mio`, pic
`401 Mio`, p95 `397,2 Mio`, pic incrémental `321,26 Mio`, résidus `0 / 0 Mio`, zéro conteneur
résiduel. Le coût Docker hôte est publié séparément : base working set `1 697,621 Mio`, pic
`1 823,859 Mio`, incrément `126,238 Mio`. Le total Playwright inclut son runner Maven/Java, tandis
que le total FlareSolverr ci-dessus ne contient que son conteneur ; les écarts maximaux sont
respectivement `599 ms` et `2 040 ms`. Cette première série imposait donc une tentative corrigée à
2 000 ms, additionnant le runner et le conteneur FlareSolverr ; ses résultats sont consignés
ci-dessous.

Les séries corrigées couvrent ensuite le même périmètre candidat complet. Playwright S1
(`pw-s1-corrected-20260824095908`) mesure base/pic/p95/incrément à
`499,680 / 744,328 / 744,328 / 244,648 Mio`, résidus `0 / 0 Mio` et écarts médian/maximal
`2 025 / 2 041 ms`. Playwright S25 (`pw-s25-corrected-20260824100224`) mesure
`320,410 / 574,363 / 571,664 / 253,953 Mio`, résidus `0 / 0 Mio`, écarts `2 000 / 2 082 ms`,
tendance `+23,980 Mio` et zéro résidu. FlareSolverr S1
(`flare-local-s1-20260824-r10-all`) additionne le runner Windows et le cgroup Docker :
`734,120 / 940,855 / 935,719 / 206,735 Mio`, résidus `0 / 0 Mio`, écarts `2 009 / 3 019 ms` et zéro
résidu candidat. Les séries Playwright S1/S25 sont corrigées, mais le maximum FlareSolverr S1
dépasse la borne de `2 500 ms` et aucun S25 FlareSolverr corrigé n'existe ; Q12 reste
`INCONCLUSIVE`.

Le run `flare-local-s1-20260827-r15-contract-v4` fournit une nouvelle série mémoire diagnostique
sur le candidat complet runner plus cgroup Docker : base/pic/p95/incrément
`354,589 / 544,214 / 539,948 / 189,625 Mio`, écarts actif médian/maximal
`2 008 / 2 021 ms`, 24 échantillons de campagne, résidus `0 / 0 Mio` à 5/30 s et zéro ressource
résiduelle. Ces bornes sont correctement échantillonnées, mais l'absence de toute observation de
campagne interdit de les utiliser comme preuve de durée S1 ou comme acquisition de Q12.

Le même run confirme le cycle structuré PktMon et son nettoyage, mais ne restitue aucune ligne de
métadonnées, aucun record et aucune route normalisée. Les deux routes attendues manquent, zéro route
inattendue est observée et le code terminal est `PKTMON_NO_PACKET_METADATA_VISIBLE`. Les deux
artefacts bruts, totalisant `15 118` octets, sont inventoriés, écrasés puis supprimés ; aucun brut
n'est retenu. L'arrêt superviseur échoue uniquement sur l'acquittement `514 ms > 500 ms` ;
l'annulation `151 ms` et le nettoyage `112 ms` restent dans leurs bornes, sans arrêt forcé ni
résidu. Le sidecar s'arrête gracieusement en `1 555 ms`, puis son conteneur et son réseau sont
supprimés par identifiants exacts, avec zéro volume anonyme résiduel. Le scan terminal final passe
sur deux racines et 16 fichiers, avec zéro finding, canari, corps brut, URL complète ou valeur
d'en-tête ; l'échec de scan encore inscrit dans la preuve d'arrêt est l'état intermédiaire immuable
antérieur à ce scan final.

Le scan terminal de `r10-all` couvre les racines campagne et sidecar : 15 fichiers, zéro finding,
zéro canari sensible, corps brut, URL complète ou valeur d'en-tête. Les deux artefacts réseau bruts,
totalisant `15 188` octets, sont inventoriés par taille et SHA-256 puis écrasés et supprimés. L'arrêt
sidecar est gracieux en `1 815 ms`, par identifiants exacts, avec zéro conteneur, réseau, volume ou
ressource candidate résiduelle. Ces sous-contrôles valent `PASS_AT_RUN_LEVEL`, mais Q13 global reste
`PARTIAL` : tous les scénarios et canaris ne sont pas couverts, et les contrats prospectifs de
préservation des preuves n'ont pas encore été rejoués de bout en bout.

Le sidecar déclare un statut et fournit une représentation de réponse reconstruite. Le candidat ne
peut donc attester ni le statut HTTP amont ni les octets applicatifs amont : il publie
`DECLARED_BY_SIDECAR` et `BODY_NOT_BYTE_FAITHFUL`, sans inventer de preuve. C'est la raison du
`0/3` puis `0/75`, même si les 78 commandes locales ont terminé. La porte Phase A reste `FAIL`.

### 12.2 Campagnes réelles

| Mesure | FlareSolverr S1 | Playwright S1 | FlareSolverr S25 | Playwright S25 |
|---|---:|---:|---:|---:|
| emplacements réussis / attendus | NOT_RUN | NOT_RUN | NOT_RUN | NOT_RUN |
| taux de succès | NOT_RUN | NOT_RUN | NOT_RUN | NOT_RUN |
| démarrage à froid | NOT_RUN | NOT_RUN | NOT_RUN | NOT_RUN |
| durée de campagne | NOT_RUN | NOT_RUN | NOT_RUN | NOT_RUN |
| latence p50 / p95 | NOT_RUN | NOT_RUN | NOT_RUN | NOT_RUN |
| mémoire de base | NOT_RUN | NOT_RUN | NOT_RUN | NOT_RUN |
| pic mémoire incrémental | NOT_RUN | NOT_RUN | NOT_RUN | NOT_RUN |
| mémoire après nettoyage | NOT_RUN | NOT_RUN | NOT_RUN | NOT_RUN |
| divergences de statut | NOT_RUN | NOT_RUN | NOT_RUN | NOT_RUN |
| divergences de taille / SHA-256 | NOT_RUN | NOT_RUN | NOT_RUN | NOT_RUN |
| trafic inattendu | NOT_RUN | NOT_RUN | NOT_RUN | NOT_RUN |
| fuite sensible dans les logs | NOT_RUN | NOT_RUN | NOT_RUN | NOT_RUN |

Le sidecar embarqué au serveur de fixtures reste un double de contrat. Les chiffres FlareSolverr
ci-dessus proviennent bien du conteneur réel, mais uniquement contre les fixtures locales ; ils ne
sont jamais présentés comme une campagne fournisseur.

Le rapport final choisit exactement une conclusion :

```text
PLAYWRIGHT_QUALIFIED
FLARESOLVERR_QUALIFIED
BOTH_QUALIFIED
NO_CANDIDATE_QUALIFIED
INCONCLUSIVE
```

`BOTH_QUALIFIED` ne désigne pas automatiquement le candidat retenu. À sûreté et fidélité égales, la
décision humaine peut ensuite considérer durée, mémoire, maintenance, dépendance sidecar, taille des
binaires et intégration au port existant. Aucun choix ne devient production-ready par cette seule
qualification.

## 13. Preuves et livrables attendus

L'implémentation autorisée produit :

```text
docs/validation/J5-PLAYWRIGHT-FLARESOLVERR-COMPARATIVE-QUALIFICATION-YYYYMMDD.md
scripts/Measure-J5BrowserQualification.ps1
```

Le rapport commité contient seulement :

- manifeste minimal et SHA-256 ;
- versions et digests ;
- commandes manuelles exécutées ;
- métriques agrégées et codes d'observation ;
- tableau Q01 à Q15 ;
- incidents minimisés ;
- décision propriétaire finale et éventuelles suites proposées.

Les mesures brutes temporaires restent sous un répertoire runtime ignoré. Avant toute publication,
elles sont scannées puis supprimées. Ne doivent jamais être ajoutés à Git : `.env`, corps JSON
fournisseur, URL complète, en-têtes, cookies, jetons, identifiants de session, profils Chromium,
HAR, traces, vidéos, captures, téléchargements ou logs bruts.

## 14. Références techniques à relire avant implémentation

Références locales :

- [`ADR-SS-001`](../../../ADR-SS-001-experimentation-endpoints-sofascore-depuis-windows.md) ;
- [`J5-GUARDED-REAL-EVENT-DATA`](../../architecture/J5-GUARDED-REAL-EVENT-DATA.md) ;
- [`J5-OFFLINE-MULTI-MATCH-IMPORT`](../../architecture/J5-OFFLINE-MULTI-MATCH-IMPORT.md) ;
- [`WO-SS-20260822-010`](../completed/WO-SS-20260822-010-j5-offline-multi-match-import.md) ;
- [`Verify-Local.ps1`](../../../scripts/Verify-Local.ps1) ;
- [`J5RealEventDataService`](../../../src/main/java/com/bettingproject/sofascorelocal/application/network/J5RealEventDataService.java) ;
- [`SensitiveContentScanner`](../../../src/main/java/com/bettingproject/sofascorelocal/security/SensitiveContentScanner.java).

Références primaires des candidats :

- [Installation Playwright Java](https://playwright.dev/java/docs/intro) ;
- [Isolation par BrowserContext](https://playwright.dev/java/docs/browser-contexts) ;
- [Réseau et interception](https://playwright.dev/java/docs/network) ;
- [API Response](https://playwright.dev/java/docs/api/class-response) ;
- [Modèle de threads Playwright Java](https://playwright.dev/java/docs/multithreading) ;
- [Gestion des navigateurs Playwright](https://playwright.dev/java/docs/browsers) ;
- [Documentation FlareSolverr v3.5.0](https://github.com/FlareSolverr/FlareSolverr/blob/v3.5.0/README.md) ;
- [Construction de la solution FlareSolverr v3.5.0](https://github.com/FlareSolverr/FlareSolverr/blob/v3.5.0/src/flaresolverr_service.py#L424-L440) ;
- [Journalisation du service FlareSolverr v3.5.0](https://github.com/FlareSolverr/FlareSolverr/blob/v3.5.0/src/flaresolverr_service.py#L89-L105).

La revue devra notamment vérifier, version exacte à l'appui, si FlareSolverr expose réellement le
statut amont et les octets originaux. Une valeur de statut synthétique ou un `page_source` reconstruit
ne répond pas au critère de fidélité, même si le JSON reste analysable.

`Verify-Local.ps1` interdit actuellement Playwright dans `src/main`. Cette barrière ne peut pas être
retirée silencieusement : le profil et les sources de qualification doivent rester dédiés et
désactivés par défaut, puis toute éventuelle intégration permanente fera l'objet d'une décision
distincte. `SensitiveContentScanner` est réutilisé, mais le scan exact des canaris reste obligatoire,
car un scanner générique ne couvre pas nécessairement toute valeur de session arbitraire.

## 15. Transitions et clôture

Transitions autorisées :

```text
DRAFT_AWAITING_ADR_DECISION
  -> REJECTED_BY_GOVERNANCE
  -> LOCAL_HARNESS_IN_PROGRESS
  -> IMPLEMENTED_AWAITING_HUMAN_GO_NO_GO
  -> QUALIFICATION_RUNNING
  -> QUALIFIED_AWAITING_OWNER_DECISION
  -> VALIDATED
```

Le passage à `LOCAL_HARNESS_IN_PROGRESS` exige la décision ADR et l'autorisation d'implémentation.
Le passage à `QUALIFICATION_RUNNING` exige la réussite de la phase locale et le go/no-go explicite
pour les appels réels. Le passage à `VALIDATED`, le déplacement vers `completed`, le commit, la PR
et toute fusion exigent une décision explicite du propriétaire après lecture du rapport final.

Une conclusion négative peut clôturer le Work Order sans candidat retenu. Une conclusion positive
n'autorise pas encore l'intégration permanente : celle-ci exige un Work Order d'implémentation ou
une extension explicitement approuvée, ainsi que les adaptations ADR, sécurité et exploitation.

## 16. Journal

### 2026-08-23 — cadrage documentaire

- Work Order ouvert sur une branche dédiée issue de `origin/main` ;
- périmètre limité au comparatif Playwright / FlareSolverr demandé ;
- séparation entre vérité locale de fidélité et observation réelle définie ;
- scénarios `S1` et `S25`, métriques mémoire/durée, arrêt, nettoyage et canaris de logs spécifiés ;
- aucune dépendance, aucun binaire, aucun transport et aucun appel fournisseur ajouté ;
- décision ADR, autorisation d'implémentation et go/no-go réel en attente.

### 2026-08-23 — contrôle de la baseline locale

- `mvnw.cmd clean verify` compile les 385 sources principales et les 119 sources de test ;
- résultat : `614` tests exécutés, `4` échecs, `0` erreur et `2` tests ignorés ;
- un échec constate que le test du transport J5 attend l'URI fournisseur exacte alors que le
  prototype local adresse le sidecar FlareSolverr ;
- trois échecs constatent que le prototype porte les timeouts de `5 s` à `15 s`, au-delà de la
  garde existante de `10 s`, sans adaptation des contrats de configuration ;
- ces échecs sont circonscrits aux fichiers du prototype propriétaire préexistants et ne proviennent
  pas du présent changement documentaire ;
- aucun fichier du prototype n'a été corrigé, reformaté, annulé ou inclus dans le Work Order ;
- une baseline verte et des tests adaptés au protocole sont obligatoires avant le passage à
  `IMPLEMENTED_AWAITING_HUMAN_GO_NO_GO`.

### 2026-08-23 — autorisation propriétaire de l'amendement et du banc

- amendement 1.1 borné de l'ADR-SS-001 autorisé ;
- installation de Playwright Java 1.62.0 et de son Chromium versionné autorisée sous profil opt-in ;
- réalisation et exécution de la phase A locale autorisées ;
- préparation du manifeste et des commandes `S1` et `S25` autorisée ;
- exécution des campagnes réelles toujours suspendue au go/no-go distinct du paragraphe 10.2 ;
- aucun appel fournisseur ne doit être émis pendant la préparation.

### 2026-08-23 — installation et banc local Playwright

- Playwright Java `1.62.0`, Chrome for Testing et headless shell `151.0.7922.34` révision `1234`,
  ainsi que FFmpeg révision `1011`, installés sous le cache ignoré du Work Order ;
- sources Java limitées aux source sets Maven opt-in `src/j5-browser-qualification` et
  `src/j5-browser-qualification-test`, sans intégration Spring ni transport métier ;
- serveur déterministe lié à `127.0.0.1`, contexte neuf non persistant, téléchargements et service
  workers bloqués, route exacte unique, zéro retry et effacement du corps après taille/SHA-256 ;
- `14` tests ciblés du banc : `PASS`, zéro échec et zéro erreur ; test d'isolation standard ciblé :
  `PASS` après `clean` ;
- campagne locale Playwright `phasea-playwright-20260823b` : `S1 3/3` et `S25 75/75`, statuts,
  types de contenu et octets exacts ; durées `6 327 ms` et `229 164 ms` ;
- mémoire de l'arbre supervisé : base `88,562 Mio`, pic `570,156 Mio`, p95 `566,402 Mio`, pic
  incrémental `481,594 Mio`, résidu `0 Mio` à 5 s et 30 s ;
- scan de six preuves minimisées : zéro canari sensible, corps, URL complète ou valeur d'en-tête ;
  flux bruts supprimés et zéro processus créé résiduel ;
- porte Phase A globale : `FAIL`, car seul `PLAYWRIGHT` est qualifié par ce run et le vrai sidecar
  FlareSolverr n'a pas été exécuté ;
- preuve issue du commit de base `6c9a6ffef14f...`, avec `gitWorktreeDirty=true`,
  `manifestSha256=null` et `manifestFileSha256=null` ; trafic fournisseur exécuté : `0`.

### 2026-08-23 — arrêt supervisé Playwright

- run `phasea-stop-playwright-20260823` arrêté pendant `S25` après `54/75` emplacements ;
- acquittement `23 ms`, arrêt `642 ms`, nettoyage `136 ms`, sans arrêt forcé ni résidu ;
- aucun canari sensible ni flux brut conservé ;
- Q09 reste `INCONCLUSIVE` : le déclenchement n'était pas lié par un latch à un GET effectivement
  suspendu, donc la preuve borne l'arbre de processus sans démontrer l'annulation réseau en vol.

### 2026-08-24 — manifeste et préparation réelle sans réseau

- le générateur exige exactement 25 événements `finished`, les cinq colonnes minimales documentées,
  un ordre stable et un manifeste sans URL, payload, en-tête, cookie, jeton ou secret ;
- un panneau synthétique local a validé création, hash canonique, inclusion `S1` dans `S25` et refus
  de réécriture avec des octets différents ; une seconde exécution identique retourne `UNCHANGED` ;
- `Test-J5QualificationReadiness.ps1 -GoNoGo PREPARE_ONLY` et le mode `Prepare` du lanceur : `PASS`,
  `PROCESS_STARTED=NO`, `PROVIDER_ACCESS_PERFORMED=NO` ; les artefacts synthétiques ont été supprimés ;
- le panneau réel exact de 25 événements n'est pas fourni et aucun manifeste réel n'est donc gelé ;
- le mode réel reste volontairement réservé, le vrai sidecar FlareSolverr n'est pas qualifié et
  aucun appel SofaScore n'a été exécuté.

### 2026-08-24 — vérifications de clôture du banc courant

- six classes du profil opt-in réexécutées : `14` tests, zéro échec et zéro erreur ;
- test standard `PlaywrightQualificationIsolationTest` réexécuté seul : `PASS` ;
- `Verify-Local.ps1` : préflight et garde statique `PASS`, puis suite standard `622` tests,
  `4` échecs, `0` erreur et `2` ignorés ;
- les quatre échecs sont inchangés : un contrat d'URI directe face au POC FlareSolverr et trois
  contrats de timeout face au défaut propriétaire de `15 s` ;
- aucune migration ni persistance modifiée, donc profil d'intégration non requis pour ce lot ;
- cache navigateur, dépôt Maven de qualification et preuves runtime confirmés ignorés par Git ;
  source opt-in absente des sorties du build standard ;
- scan statique des sources, tests, scripts et documents WO-011 : aucune URL SofaScore, secret,
  valeur d'authentification ou cookie ; liaison applicative `127.0.0.1` inchangée ;
- syntaxe AST des vingt-trois scripts PowerShell : `PASS` ; aucun processus `node` ou
  `chrome-headless-shell` ni fichier temporaire d'écriture atomique résiduel ;
- revue finale des preuves à ce jalon intermédiaire : nettoyage des processus `PASS`, mais comptes de
  pages, contextes et sessions explicitement `NOT_MEASURED` ; Q06-Q07 passent localement et Q08 reste
  alors `PARTIAL` faute de capture réseau OS indépendante ; les bras `r9` et `r10-all` ci-dessous
  remplacent cette conclusion provisoire ;
- contrôle de whitespace du périmètre WO-011 : `PASS` ; le contrôle global ne signale que les espaces
  préexistants du POC propriétaire dans `pom.xml` et `ProviderJ5EventDataRestTransport.java`, laissés
  intacts et hors périmètre.

### 2026-08-24 — panneau réel commun et sidecar FlareSolverr

- export PostgreSQL strictement en lecture seule : vingt-cinq dernières identités
  `PROVIDER_SNAPSHOT + finished`, distinctes, export minimal et reproductible ;
- CSV gelé : SHA-256 `9d12afe5ddf9b03d5e61022a3766e7e752df981a45254fba428c6d4320cfde99` ;
- manifeste gelé : SHA-256 canonique
  `eb558bd241a35fca820ade30f8cb307700bf6c1d935ff00aa7910f98f9a6cb0d`, `S1=1`, `S25=25`,
  seconde génération `UNCHANGED` ;
- vrai sidecar FlareSolverr v3.5.0 démarré depuis l'image locale avec `--pull=never`, index OCI
  `sha256:139dfee1c6f89249c8d665d1333a42e8ec74ec0a86bc6bb1c8461e10d3a66a47`, manifeste
  `linux/amd64` `sha256:258523d25e4e07028c3a206f0e03ae807b26a50a201dd320f09a18464ecf86fa` et
  Chromium `148.0.7778.178` ;
- run `phasea-flare-local-20260824b` : `S1 0/3`, `S25 0/75` en fidélité probante ; commandes
  terminées mais statuts seulement déclarés par le sidecar et octets amont non observables ;
- run comparable `phasea-playwright-comparable-20260824a` : `S1 3/3`, `S25 75/75`, fidélité
  intégrale ;
- mémoire commune : Playwright base/pic/p95/incrément `186,551 / 570,527 / 566,664 / 383,976 Mio` ;
  FlareSolverr `79,74 / 401 / 397,2 / 321,26 Mio`, coût Docker hôte séparé ; résidus candidats
  `0 / 0 Mio` à 5/30 s et zéro ressource résiduelle ;
- deux scans minimisés : zéro canari ou donnée sensible, aucun log brut conservé ;
- capture réseau OS : préflight WFP/PktMon `PASS` et diagnostic structuré `r5` `PASS`, sans trafic,
  payload ou résidu ; la tentative comparable `r8` a démarré PktMon et vérifié le filtre exact, puis
  a échoué avant le gate sur l'ancien discriminateur textuel du statut actif ; Q08/Q15 restent
  `PARTIAL` tant qu'une campagne instrumentée complète n'a pas abouti ;
- trafic fournisseur exécuté pendant toute cette phase : `0`.

### 2026-08-24 — preuve structurée PktMon locale

- diagnostic immuable `pktmon-structured-20260824-r5`, SHA-256
  `e1d40115b91ac8db04d6b4c5de0fa67047883f17f49da4ae4dc7254fc3744615` ;
- `PKTMON_STRUCTURED_STATE=PASS`, discriminateur ETW exact `TRUE`, discriminateur compteurs JSON
  `FALSE`, nettoyage `PASS` ;
- 42 sessions ETW avant, 43 pendant avec exactement une cible, puis 42 après et zéro cible ;
- aucun accès fournisseur, aucune requête HTTP et aucune sortie native brute persistée ;
- ce diagnostic qualifie le mécanisme de preuve, pas les routes réseau d'une campagne J5.

### 2026-08-24 — portage du discriminateur ETW dans la campagne comparable

- le lecteur natif `QueryAllTracesW` qualifié par `r5` est partagé avec la capture de campagne ;
- l'ouverture du gate exige PRE absent, ACTIVE exact, chemin ETL exact, mode circulaire, limite
  `16 Mio`, fournisseur PktMon unique et trois compteurs de pertes à zéro ;
- la preuve terminale exige aussi BEFORE_STOP exact puis POST absent ; le parseur textuel localisé
  n'est plus bloquant pour ACTIVE ou POST ;
- toute exception de démarrage inattendue est réduite à un code allowlisté et la détection ETW
  participe au nettoyage même si `pktmon start` retourne un code non nul ;
- un second scan de logs, exécuté après capture réseau, arrêt sidecar et stabilisation mémoire,
  couvre les deux racines de preuve et chaîne par SHA-256 le premier scan des flux bruts ;
- AST des `23` scripts, prédicats PRE/ACTIVE/POST sur la preuve `r5`, contrat synthétique du double
  scan et test `J5QualificationSidecarSecurityTest` : `PASS` hors ligne ;
- à ce jalon intermédiaire, la nouvelle campagne S1 n'était pas encore exécutée ; `r9` ci-dessous
  remplace désormais cette conclusion provisoire.

### 2026-08-24 — campagne locale instrumentée FlareSolverr `r9`

- run immuable `flare-local-s1-20260824-r9`, preuve réseau SHA-256
  `1fbebdccc636d54a771b69d3ac310e1575147b04a723c0d171600b7fa8d1ef57` ;
- le sidecar v3.5.0 et ses deux digests sont authentifiés, la capture PktMon démarre avant le gate et
  la campagne locale termine ses trois cibles en `6 926 ms`, sans accès fournisseur ;
- PRE, ACTIVE, BEFORE_STOP et POST sont exacts via `QueryAllTracesW`, avec une seule session cible,
  chemin ETL exact, mode circulaire `16 Mio`, fournisseur unique et pertes ETW nulles ;
- le scope Windows `NICS` filtré sur l'IPv4 exacte du conteneur ne fournit aucun paquet
  normalisable : zéro route observée, deux routes locales manquantes et zéro route inattendue ;
- l'ancien libellé terminal `Q08=FAIL` ne prouve donc aucune sortie externe. La classification est
  corrigée en trois états : toute route inattendue reste `FAIL`, l'ensemble exact seul vaut `PASS`,
  et une capture vide ou non parsable vaut `INCONCLUSIVE_CAPTURE_OR_ROUTE_PARSE` ; la readiness
  continue d'exiger explicitement `PASS` ;
- le second échec `RAW_INVENTORY_FAILED` provenait de `@($List[object])` appliqué à des dictionnaires
  ordonnés sous PowerShell 7 ; l'inventaire utilise désormais `ToArray()` et conserve uniquement
  noms, tailles et SHA-256 avant suppression ;
- mémoire comparable FlareSolverr, runner compris : base/pic/p95/incrément
  `594,811 / 760,823 / 756,38 / 166,012 Mio`, résidus candidat `0 / 0 Mio` à 5/30 s et zéro ressource
  candidate résiduelle ;
- arrêt sidecar gracieux, suppression par identifiants exacts, zéro conteneur, réseau ou volume
  résiduel, deux artefacts réseau bruts écrasés puis supprimés ;
- le chemin d'échec avait évité le second scan et laissé le CSV brut de télémétrie mémoire. Le
  `finally` exige désormais l'arrêt authentifié du runner et du sampler avant de relancer le scan
  chaîné des racines campagne et sidecar ;
  `sensitiveDataLogged=false` n'est publié que si cette couverture complète est prouvée ;
- un paramètre borné `Nics|All`, authentifié sous forme canonique du préflight aux reprises de preuve
  terminale, a préparé le seul second bras local `ALL`, exécuté par `r10-all` ci-dessous. `Nics` reste
  le défaut et aucune promotion de `ALL` n'est implicite ;
- Q08 est `INCONCLUSIVE`, Q15 reste `PARTIAL_CONTAINER_PATH_ONLY` et S25 demeure bloqué.

### 2026-08-24 — second bras PktMon `ALL` et mémoire S1 corrigée

- run `flare-local-s1-20260824-r10-all`, preuve réseau SHA-256
  `e38ebe0c2b72f4ba08b9391fe3b825752381274e9b1e087136dd2f65792b5e3d` et preuve terminale
  `d99aaf8556b7c547cd1e5917aab42c3f645498dd6ccc656998d0ad8e9efb055a` ;
- PRE, ACTIVE, BEFORE_STOP et POST sont exacts, avec une seule session cible, fournisseur attendu et
  pertes ETW nulles ;
- le scope `ALL` ne fournit aucune visibilité supplémentaire par rapport à `NICS` : zéro ligne de
  métadonnées paquet, endpoint ou record, deux routes locales manquantes et zéro inattendue ;
- Q08 reste `INCONCLUSIVE_CAPTURE_OR_ROUTE_PARSE`, Q15 `PARTIAL_CONTAINER_PATH_ONLY` ; aucun chemin
  fournisseur ni GET n'a été exécuté ;
- mémoire runner plus conteneur : base/pic/p95/incrément
  `734,120 / 940,855 / 935,719 / 206,735 Mio`, écarts médian/maximal `2 009 / 3 019 ms`, résidus
  `0 / 0 Mio` et zéro ressource candidate résiduelle ; Q12 reste inconclusif ;
- scan terminal des deux racines : 15 fichiers, zéro finding ; deux artefacts bruts, `15 188` octets,
  inventoriés puis écrasés et supprimés ;
- arrêt sidecar gracieux en `1 815 ms`, suppression par identifiants exacts et zéro résidu ;
- le terminal global reste `FAIL` en fermeture sûre sur l'attribution réseau ; le S25 FlareSolverr
  instrumenté sous contrat corrigé demeure bloqué.

### 2026-08-24 — go conditionnel FlareSolverr et maintien du blocage réel

- go distinct propriétaire reçu pour `FLARESOLVERR_AUTHORIZE_S1` ;
- go `FLARESOLVERR_AUTHORIZE_S25` reçu sous condition de tous les prédécesseurs du protocole ;
- aucun go Playwright réel reçu ;
- readiness FlareSolverr S1 exécutée sans réseau : `FAIL` sur `LOCAL_PHASE_A` et
  `CLEAN_REAL_WORKTREE`, `PROVIDER_ACCESS_PERFORMED=NO` ;
- amendement ADR-SS-001 v1.2 : vrai sidecar autorisé seulement contre les fixtures locales ; son
  mécanisme automatique de résolution de challenge et l'absence de fidélité amont interdisent son
  emploi fournisseur sans nouvelle décision explicite ;
- les deux go restent conditionnels et dormants ; aucune campagne fournisseur S1 ou S25 n'a été
  lancée.

### 2026-08-25 — corrections de preuve forward-only et prochain diagnostic

- le terminal historique `r10-all` conserve les libellés trompeurs `PKTMON_BRIEF_PARSE_FAILED` et
  `NETWORK_CAPTURE_STOP_FAILED`, alors que l'arrêt PktMon et le cycle ETW terminal passent ;
- son `candidate-metrics.json` terminal est vide, le premier scan n'est pas archivé et l'état sidecar
  de départ référencé par le hash `857d2f9b...` a été muté vers un fichier final `79b4b208...` ;
- pour les runs futurs, le contrat réseau `2.4.0` publie le préflight v3, l'état v4 et l'attribution
  v3 ; le terminal comparable v2 distingue `NETWORK_ATTRIBUTION_INCONCLUSIVE`, le scan de logs v2
  préserve et chaîne le premier scan et les métriques, et les preuves sidecar v3 séparent un état de
  démarrage immuable de la preuve d'arrêt ;
- ces corrections ne réécrivent ni ne requalifient `r10-all`. Un nouveau S1 est nécessaire pour les
  qualifier de bout en bout ;
- `NICS` et `ALL` sans métadonnée épuisent le diagnostic A/B autorisé. Un observateur dans l'espace
  réseau Linux exact du sidecar et une preuve Windows indépendante des sockets/PID exigent un go
  propriétaire distinct, local et borné ; les go FlareSolverr S1/S25 ne couvrent pas ce protocole ;
- le S25 FlareSolverr instrumenté sous contrat corrigé reste bloqué et aucun appel fournisseur n'est
  autorisé par ces correctifs.

### 2026-08-25 — échec fermé r11 et contrat réseau 2.5

- le run `flare-local-s1-20260825-r11-contract-v3`, exécuté sous le contrat réseau `2.4.0`, s'arrête
  avant l'ouverture du gate et avant toute campagne ; `PROVIDER_ACCESS_PERFORMED=NO` et
  `REQUEST_GET_EXECUTED=NO` ;
- les états ETW PRE, ACTIVE et BEFORE_STOP sont exacts et chaînés. Le contrôle textuel localisé
  refuse néanmoins l'arrêt réseau et conduit à `RECOVERY_REQUIRED`, sans autoriser une interprétation
  du statut textuel comme preuve de cycle ;
- le sidecar est arrêté gracieusement, son conteneur, son réseau et ses volumes sont absents, et les
  preuves de démarrage et d'arrêt restent distinctes. L'ETL brut de 16 777 216 octets demeure dans la
  racine runtime r11 ; le scan sensible terminal ne peut donc pas être déclaré pleinement prouvé ;
- `r11` reste une preuve historique immuable en échec. Au terminal initial, sa récupération séparée
  et append-only n'était pas encore exécutée, dans l'attente d'une authentification indépendante de
  l'état PktMon courant ;
- le contrat réseau forward-only `2.5.0` publie le préflight v4, l'état v5 et l'attribution v4. Son
  autorité de cycle est `QueryAllTracesW`; le statut textuel est une télémétrie bornée, non persistée
  et sans pouvoir d'autoriser une mutation ;
- aucun S1 sous contrat 2.5 n'est encore acquis. La récupération r11, puis la requalification S1,
  restent requises avant toute décision S25 ; aucun appel fournisseur n'est autorisé par ces travaux.

### 2026-08-25 — durcissement P1 hors ligne du contrat 2.5

- le producteur `state-v5` et la récupération forward-only partagent désormais un schéma exact de
  42 clés et un vocabulaire fermé de 11 tuples de provenance. Les quatre champs
  `recoveryPhase`, `recoveryCauseCode`, `lastMutationAttempted` et `lastMutationExecuted` restent
  nuls hors récupération et sont écrits atomiquement avec `RECOVERY_REQUIRED` par une seule voie ;
- la récupération distingue sans heuristique le profil historique exact `r11` sous contrat 2.4 et
  le profil forward `state-v5` sous contrat 2.5. Elle rejette les propriétés JSON dupliquées, les
  clés supplémentaires, les types JSON coercibles mais inexacts et toute rupture de la chaîne
  d'identité sidecar, préflight, état réseau ou artefact brut ;
- le chemin canonique du producteur et du gate est
  `network-runtime/raw/pktmon.etl`. Un dossier brut exactement vide est authentifié comme une forme
  distincte ; si l'arrêt PktMon y matérialise ensuite l'ETL exact, le dossier puis le fichier unique,
  non reparse et à un seul lien sont réauthentifiés avant toute suppression ;
- l'ouverture du gate applique la même lecture UTF-8 stricte, le rejet récursif des doublons, les
  schémas exacts et les types JSON exacts avant de déclarer les preuves persistées authentifiées ou
  de signer le gate ;
- le sidecar est lié aux références de release et d'index, aux digests d'index et de manifeste
  plateforme gelés, au média OCI, à `linux/amd64`, à `v3.5.0` et à l'`imageId` exact. La récupération
  applique ce contrat partagé avant toute mutation ;
- le gate exige exactement deux routes dans leur ordre sérialisé canonique : `INBOUND/TCP` depuis la
  passerelle Docker vers le port sidecar `8191`, puis `OUTBOUND/TCP` vers le port réel de la fixture.
  Les ensembles incomplets, étendus, réordonnés ou liés à un autre port sont refusés ;
- validations sans PktMon, Docker, réseau ni fournisseur : 28 scripts PowerShell avec AST valide,
  politique réseau `PASS`, contrat de runtime brut `PASS`, test d'architecture Maven 12/12 et profil
  Maven opt-in sur fixtures locales 24/24. Le `mvnw.cmd clean verify` courant exécute 632 tests mais
  échoue sur quatre tests exclusivement liés au POC propriétaire hors lot inchangé, FlareSolverr
  `localhost:8191` et timeout de 15 secondes ; la suite globale n'est pas déclarée verte ;
- `r11` et ses empreintes restent inchangés, aucune mutation de récupération n'a été exécutée et
  aucun S1 sous contrat 2.5 n'a été lancé. La sonde P2 a depuis été exécutée et est consignée
  ci-dessous ; S25 demeure bloqué.

### 2026-08-25 — sonde PktMon filter-shape P1

- A1 échoue sur `RECOVERY_STATE_AUTHENTICATION_FAILED`; A2 et A3 échouent sur
  `RECOVERY_LIVE_STATE_AUTHENTICATION_FAILED`. Les trois tentatives `2.0.0` s'arrêtent avant toute
  mutation, sans arrêt PktMon, retrait de filtre, suppression brute, requête HTTP ou accès fournisseur.
  A3 confirme que le runtime brut est retenu. Les SHA-256 des terminaux A1, A2 et A3 valent
  respectivement `353c8f03d956ec9b7d9c8bdfe57d38f3d291cb45a451e85d6a05c9d561ff9d84`,
  `41d514230e90288896f5ec3fc4c079eb8aa51789c78b17f75f8677af61a9ca40` et
  `7de9287783b1b8e924b9139ea454275aa5adb87a0eb461323b91128e141cdce7` ;
- la sonde read-only `r11-filter-shape-20260825-p1`, version `1.0.0`, rend `PASS` pour l'exécution de
  la sonde mais `UNCLASSIFIED_DECORATION` pour l'inventaire. Sa preuve SHA-256 vaut
  `68752ae4da478f5bd9d2eddaa3fb372c57e5de239a8d534dbdeefc5946a4d634`, son `outputSha256`
  minimisé vaut `4b75b68c9f9896d2a920fe69717e3ea7d3680405bfaa95bdd94a0cd109633fd7` et l'état
  source inchangé reste lié à `e5914ff0526e33e8d5999b0aa7de6dba8f07151d7ac8c6db026b56ce50dbeb94`.

### 2026-08-26 — sondes P2/P3/P4 et séquencement A4

- la sonde read-only `r11-filter-shape-20260825-p2` déclare `Test-J5PktmonFilterShape.ps1`
  `1.0.1`. Elle rend `PASS` pour l'exécution de la sonde, mais conserve
  `UNCLASSIFIED_DECORATION` et `exactInventory=false`. Sa preuve SHA-256 vaut
  `0b80908c802dcbe04004f53d48546bc50035c104d2c33f84f8404e7cd67ff801` ;
- P2 observe le même `outputSha256`
  `4b75b68c9f9896d2a920fe69717e3ea7d3680405bfaa95bdd94a0cd109633fd7` et le même état réseau
  source inchangé `e5914ff0526e33e8d5999b0aa7de6dba8f07151d7ac8c6db026b56ce50dbeb94`. Elle n'exécute
  aucune mutation PktMon, requête HTTP ou opération fournisseur et ne persiste ni n'affiche la
  sortie native brute ;
- le schéma de preuve P2 ne liait pas les SHA-256 du script de sonde et de la politique : `1.0.1`
  reste donc une version déclarée, sans provenance cryptographique des deux fichiers. Le résultat
  inchangé réfute l'inférence p1 du titre exact `Filtres d’en-têtes :`, mais ne révèle pas le titre
  réel. `FR_PACKET_SINGULAR_OEM_UTF8_NBSP` constitue alors l'hypothèse forte et demeure non prouvée
  au terme de P2 ;
- P3 `r11-filter-shape-20260826-p3` utilise le script `1.0.2` et le schéma
  `j5-pktmon-filter-shape-v2`. Elle rend `PASS`, avec `UNCLASSIFIED_DECORATION`,
  `exactInventory=false` et l'enum exact `FR_PACKET_SINGULAR_OEM_UTF8_NBSP`. Sa preuve SHA-256 vaut
  `b40ad1b24ca5c32eb18e1097b6378f6214ec16f2778eb44d9efa66c8d05aaf40` ; les sources de sonde et de
  politique authentifiées valent respectivement
  `ecaae860e25c667d32b568810762472348743b821ffcc96cd2cddfac431bb361` et
  `c0752cd3713e8dc0fe504866a4aaef4de38a51cb2d927a0779b130e47fda9b2f`. L'`outputSha256` reste
  `4b75b68c9f9896d2a920fe69717e3ea7d3680405bfaa95bdd94a0cd109633fd7` ;
- le diagnostic fermé prouve que le titre est exactement `Filtres de paquet` suivi de `U+252C`,
  `U+00E1` puis `:`. P3 n'exécute aucun accès fournisseur, requête HTTP ou mutation PktMon, et ne
  persiste ou n'affiche aucune sortie native brute ;
- la politique accepte désormais ce seul titre par égalité ordinale et exige, pour cette variante
  OEM, exactement quatre lignes ordonnées : titre, en-tête, séparateur, unique enregistrement
  possédé. Ce correctif ne modifie pas les autres titres ni le parseur générique ;
- P4 `r11-filter-shape-20260826-p4` utilise le même script `1.0.2`, le même schéma
  `j5-pktmon-filter-shape-v2` et rend `PASS_EXACT_RAW_AUTHENTICATED`. Sa preuve de 4 969 octets,
  strictement UTF-8 et sans BOM, porte le SHA-256
  `d2e048a05b8380b161427e2929bd839922459a6f723859c67bfbe2e3d75c47f1`. Elle authentifie l'état
  réseau `e5914ff0526e33e8d5999b0aa7de6dba8f07151d7ac8c6db026b56ce50dbeb94`, la sonde
  `ecaae860e25c667d32b568810762472348743b821ffcc96cd2cddfac431bb361`, la politique corrigée
  `42d730160cfd917ce492cae1b0e9576bb6c7be8b29c37113ce0afb470e7362a9` et l'`outputSha256`
  `4b75b68c9f9896d2a920fe69717e3ea7d3680405bfaa95bdd94a0cd109633fd7` ;
- la grammaire P4 est exacte et bornée : un titre, un en-tête, un séparateur et un enregistrement
  possédé ; les compteurs `other` et `unclassified` valent zéro. Aucune normalisation, anomalie,
  donnée sensible, opération Docker, mutation PktMon, requête HTTP ou opération fournisseur n'est
  observée, et aucune sortie native brute n'est affichée ou persistée ;
- A4 passe à `READY_AUTHORIZED_NOT_RUN_2_1_0`. Elle doit utiliser
  `Recover-J5QualificationNetwork.ps1` `2.1.0`, dont le SHA-256 final vaut
  `7fafa8d6a0fa3aefecb45d0abbbfc1692060751e733ec3c7942b3bf946e062ae` ;
- la phase 5 `AUTHENTICATE_EXECUTABLE_CHAIN` authentifie la sonde
  `ecaae860e25c667d32b568810762472348743b821ffcc96cd2cddfac431bb361`, la politique
  `42d730160cfd917ce492cae1b0e9576bb6c7be8b29c37113ce0afb470e7362a9` et l'aide ETW structurée
  `e4724bba68414f2fa778ca0893a145e27446f056d3a973df6c97f05b99f7ad80`. La phase 8
  `AUTHENTICATE_FILTER_SHAPE_PROOF` authentifie la preuve P4
  `d2e048a05b8380b161427e2929bd839922459a6f723859c67bfbe2e3d75c47f1` et son contrat réel
  `PASS_EXACT_RAW_AUTHENTICATED` ;
- le récupérateur recalcule les quatre empreintes avant chaque mutation et lie cette provenance aux
  preuves intention/résultat/terminal au schéma v3 ainsi qu'aux checkpoints de phase au schéma v2.
- après revue, l'état ETW exact et sa continuité sont relus immédiatement avant `PktMon stop` ;
  `filter list` est relu immédiatement avant `filter remove`, avec nom et IPv4 exacts et SHA-256
  égal à la sortie P4. Aucun checkpoint ni autre E/S ne s'intercale entre chaque revalidation et sa
  mutation. Les contrats PowerShell et Java verrouillent cet ordre ;
- la validation hors ligne rend AST PowerShell 28/28 `PASS`,
  `Test-J5QualificationNetworkPolicy.ps1` `PASS`, Maven ciblé 17/17 `PASS` et
  `P4_REAL_CONTRACT=PASS`. Le `mvnw.cmd clean verify` exécute 632 tests mais échoue sur quatre tests
  exclusivement liés au POC propriétaire hors lot inchangé, FlareSolverr sur `localhost:8191` et
  timeout de 15 secondes ; la suite globale n'est pas présentée comme verte ;
- A4 n'est pas encore exécutée et son succès n'est pas acquis. Cette préparation n'a exécuté aucun
  PktMon, Docker, HTTP ou accès fournisseur supplémentaire et n'autorise aucun nouveau S1 ou S25.

### 2026-08-26 — récupération A4/A5 et préparation du scan terminal

- A4 `r11-recovery-20260826-a4` authentifie la chaîne historique, arrête PktMon, retire le filtre
  exact et confirme zéro filtre. L'arrêt finalise toutefois un nouvel ETL de `13 217` octets, dont
  l'identité `907799dbf9a40d2f5d9e878c60bc46e63cac4097af8aec9610d404e8371b6b5a` diffère de
  l'objet de `16 Mio` authentifié avant l'arrêt. A4 refuse donc sa suppression et termine
  `FAIL_CLOSED` sans affaiblir l'autorisation d'effacement ;
- A5 `r11-recovery-20260826-a5` repart du mode exact `RAW_DELETION_REQUIRED`, authentifie cet ETL
  unique à un seul lien, puis confirme l'écrasement borné et sa suppression. Ses 16 checkpoints
  ordonnés et ses preuves intention/résultat/terminal sont cohérents. Le terminal `PASS` porte le
  SHA-256 `fdcae6d32031e35c570ba0f939425209e84592345ed7ddca2c182ad369eeb046`, laisse ETW absent,
  zéro filtre, aucun runtime brut, et ne modifie aucune preuve historique ;
- aucune récupération A4/A5 n'exécute de requête HTTP ou d'accès fournisseur. Le résultat
  historique de `r11` reste `FAIL` et ne devient pas une campagne qualifiée ;
- le scan sensible reste séparé comme l'exige A5. Le scanner v2 conserve désormais le premier
  passage dans `log-scan-prior.json`, archive chaque génération ultérieure sous
  `log-scan-history/<SHA-256>.json`, authentifie récursivement la chaîne et refuse cycle, chemin non
  canonique ou hash divergent. Un mutex global lié au dépôt et au `RunId`, un journal write-ahead
  durable, des snapshots revalidés avant mutation, un CAS de la tête et une publication atomique
  protègent la transaction. Les points de réanalyse descendants sont refusés et aucun JSON
  d'évidence n'est supprimé sur finding. `candidate-metrics.json` reste immuable ;
- le contrat local simule quatre générations successives, préserve exactement chaque prédécesseur
  et échoue fermé lorsqu'un runtime brut subsiste, qu'une chaîne ou des métriques sont altérées,
  qu'un scan concurrent se présente, qu'une preuve arrive après snapshot, que la tête change, qu'un
  verrou invalide existe ou qu'une jonction descendante est rencontrée. Il vérifie les reprises
  exactes avant et après publication de la tête ainsi que la conservation byte-for-byte d'un JSON
  portant un marqueur interdit. AST PowerShell 28/28, politique réseau et contrat synthétique passent
  sans PktMon, Docker, navigateur ou fournisseur ;
- le scan officiel post-récupération des racines run et sidecar termine `PASS` : 72 fichiers, zéro
  finding, zéro runtime brut. Sa tête SHA-256
  `073fe2170e190bf210703460953c1d767c032607cb67b5c7688dfcd9f8f4b207` archive exactement la tête
  précédente `43ac1528cb9aae16dc245678e8c85fcd4efc3fa466818b78a70e303eece14bfc`, conserve les métriques
  `f477333e4e38daf21bdb5a22a3b05c4155d0bb771e30d394a1c238a235ea1767` et ne laisse aucun journal,
  verrou ou temporaire. Le terminal A5 reste inchangé. Ce résultat ferme Q13, mais n'autorise ni ne
  requalifie un nouveau S1 2.5 ou S25 ;
- après alignement du contrat Java sur le scanner transactionnel, le test d'architecture ciblé passe
  `12/12`. Le `mvnw.cmd clean verify` standard exécute 632 tests et retrouve les quatre échecs déjà
  attribués au POC propriétaire hors lot (`localhost:8191` et timeout `15 s`) ; aucune modification
  de ce POC n'est incluse dans cette reprise.

### 2026-08-27 — requalification S1 forward-only 2.5 r15

- `flare-local-s1-20260827-r15-contract-v4` franchit le préflight, le cycle ETW structuré et le gate
  authentifié du contrat `2.5.0`, puis le runner termine avec le code `1` avant toute observation ;
- `candidate-metrics.json` vaut `FAIL` avec zéro campagne. Aucun taux de succès, aucune fidélité des
  statuts ou des octets, aucune latence et aucune durée S1 ne sont mesurés par ce run ;
- la capture rend zéro métadonnée, zéro record et zéro route normalisée ; deux routes locales sont
  manquantes et zéro inattendue est observée. Q08 reste `INCONCLUSIVE_CAPTURE_OR_ROUTE_PARSE` et
  Q15 `PARTIAL_CONTAINER_PATH_ONLY`, sans preuve de sortie inattendue ;
- la mémoire runner plus conteneur est mesurée à
  `354,589 / 544,214 / 539,948 / 189,625 Mio` pour base/pic/p95/incrément, cadence active
  `2 008 / 2 021 ms`, résidus `0 / 0 Mio` et zéro ressource résiduelle. Faute d'observation, cette
  série reste diagnostique et ne qualifie ni Q12 ni une durée S1 ;
- l'arrêt superviseur vaut `FAIL` sur l'acquittement `514 ms > 500 ms`, tandis que l'annulation
  `151 ms` et le nettoyage `112 ms` passent. Le sidecar s'arrête gracieusement en `1 555 ms`, puis
  conteneur, réseau, volumes, session PktMon, filtre et deux bruts réseau de `15 118` octets sont
  nettoyés sans résidu ;
- le scan terminal passe sur deux racines et 16 fichiers avec zéro finding et aucun brut retenu.
  Les scripts n'exécutent aucun accès fournisseur ni `request.get` ; la Phase A reste `FAIL`, S25
  reste bloqué et aucune nouvelle campagne n'est implicitement autorisée.

### 2026-08-27 — S1 r18 et correction du protocole de verdict négatif

- `flare-local-s1-20260827-r18-forward-only-250` exécute la fixture locale jusqu'au terminal :
  trois cibles sur trois terminent, mais aucune ne satisfait le succès, le statut, le type de
  contenu ou les octets exacts ; le verdict candidat et la Phase A restent `FAIL` ;
- le premier scan sensible termine `PASS`, avec zéro finding et la liaison complète des cinq
  preuves attendues. L'absence du second scan ne provient ni d'un finding ni d'une altération :
  l'acquittement exigeait par erreur que `candidateMetricsResult` soit `PASS` ;
- le correctif forward-only accepte comme preuve structurelle un document candidat cohérent
  `PASS` ou `FAIL`, puis applique séparément les critères de qualification. Un `FAIL` cohérent peut
  ainsi être scanné et chaîné sans devenir un succès fonctionnel ;
- le contrat hors ligne ajoute un scénario négatif à deux scans, avec conservation byte-for-byte
  des métriques, ainsi qu'une matrice de 68 falsifications refusées portant sur schéma, champs,
  types, compteurs et cohérence du verdict ;
- les validations du correctif passent : AST PowerShell `29/29`, politique réseau `PASS`, contrat
  du verdict négatif `PASS` (`68/68` falsifications refusées), contrat transactionnel des scans
  `PASS`, architecture ciblée `12/12` et profil Maven opt-in `25/25`. Elles restent strictement
  locales et n'exécutent aucun appel fournisseur ;
- r18 reste immuable et globalement `FAIL`. L'exécution fraîche r19 documentée ci-dessous produit
  ensuite le second scan réel et ferme la preuve terminale sous le contrat corrigé, sans requalifier
  r18. S25 demeure bloqué et aucun accès fournisseur n'est autorisé par cette correction.

### 2026-08-27 — S1 r19 et preuve réelle du verdict négatif

- `flare-local-s1-20260827-r19-negative-verdict` exécute les trois cibles locales prévues en
  `6 945 ms`. Les métriques candidates immuables portent `result=FAIL`, `3/3` cibles terminées,
  `harnessCompleted=false`, puis `0/3` succès, statuts, types de contenu et corps exacts ;
- le runner et l'échantillonneur terminent avec un code `0`, sans convertir ces compteurs en succès.
  Le terminal publie séparément `CANDIDATE_EXECUTION_NOT_QUALIFIED` et
  `CANDIDATE_FIDELITY_NOT_QUALIFIED` ;
- le premier scan sensible passe sur 14 fichiers avec zéro finding, lie exactement cinq entrées
  terminales et authentifie les métriques candidates par
  `5b8830da36fbf0f74961c769884c9740c8f5373d33d3f68fe310bb5bcf78446a` ;
- le second scan passe sur 17 fichiers avec zéro finding, authentifie le premier scan par
  `730b5a0683c50ae6f538d739e46158289bd5fc06f62aa29686301ce451606581`, préserve le même
  `candidateMetricsResult=FAIL` et confirme zéro canari, en-tête, URL complète ou corps brut ;
- les 11 références terminales et les 14 liaisons minimisées entre readiness, gate, processus,
  réseau, sidecar, wrapper, scans et métriques sont présentes et correspondent toutes à leurs
  empreintes attendues ;
- le contrat de verdict négatif est donc prouvé sur une campagne réelle du banc local. Le terminal
  reste volontairement `FAIL` : PktMon ne publie aucune métadonnée paquet, Q08 demeure
  `INCONCLUSIVE_CAPTURE_OR_ROUTE_PARSE`, Q15 `PARTIAL_CONTAINER_PATH_ONLY` et l'attribution réseau
  échoue avec `PKTMON_NO_PACKET_METADATA_VISIBLE` ;
- le sidecar s'arrête gracieusement en `1 566 ms`, par identifiants exacts, avec zéro résidu réseau,
  conteneur ou volume. La mémoire mesurée est `87,988 / 545,363 / 545,145 Mio` pour base, pic et p95,
  un pic incrémental de `457,375 Mio` et zéro résidu à 5 s comme à 30 s ;
- aucun accès fournisseur ni `request.get` n'est exécuté, aucun brut réseau n'est retenu et
  `SENSITIVE_DATA_LOGGED=NO`. Le `wrapper-evidence.json` termine `PASS` uniquement pour son cycle
  d'orchestration et conserve `phaseAGate=FAIL`, `s1BothCandidatesResult=FAIL` et zéro candidat
  qualifié. S1 et la Phase A restent `FAIL`; S25 demeure bloqué.

## 17. Décision propriétaire et clôture

Le 27 août 2026, le propriétaire clôt le comparatif après plusieurs jours de qualification :

- Playwright est retenu sur la base de ses campagnes locales déterministes S1 `3/3` et S25
  `75/75`, avec fidélité complète des statuts, types de contenu et octets ;
- FlareSolverr est écarté comme transport, dépendance et fallback : les requalifications locales
  n'ont pas produit un S1 fidèle, le run r19 terminant les trois cibles avec `0/3` succès et
  `0/3` fidélité ;
- aucune campagne fournisseur n'a été exécutée par ce Work Order et aucun résultat local n'est
  présenté comme une qualification contre SofaScore ;
- la matrice Q01-Q15 reste historiquement `INCONCLUSIVE`, notamment pour la capture réseau,
  l'arrêt en vol, la mémoire comparable et la baseline Maven du POC propriétaire ;
- la vérification finale `mvnw.cmd clean verify` exécute 638 tests : 632 réussissent, deux sont
  ignorés et quatre échouent exclusivement sur le POC FlareSolverr propriétaire hors lot inchangé
  (`localhost:8191` et timeout `15 s`) ; la branche complète n'est donc pas déclarée verte ;
- l'ADR-SS-001 v1.3 retient Playwright comme transport cible local, manuel et opt-in pour J3, J4,
  J5 et les futurs jalons nécessitant un endpoint SofaScore ;
- chaque implémentation reste soumise à un Work Order distinct, à une allowlist exacte et aux
  garde-fous de l'ADR. Ce Work Order n'implémente aucun transport métier.

La décision propriétaire rend inutile toute nouvelle campagne FlareSolverr S1/S25 ou tout
diagnostic à double observateur dans ce comparatif. Les preuves existantes restent immuables et
auditables. `WO-SS-20260823-011` passe au statut `VALIDATED` et sa clôture vaut `COMPLETED`.

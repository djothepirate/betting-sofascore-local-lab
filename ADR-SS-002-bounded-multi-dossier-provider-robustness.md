# ADR-SS-002 - Preuve fournisseur multi-dossier bornée pour J9

- **Statut :** v1.0 `ACCEPTED_CONSUMED_AND_TERMINATED_BY_STOP` ; v1.1
  `DRAFT_SELECTED_PROFILE_PENDING_OWNER_ACCEPTANCE`
- **Version historique acceptée :** 1.0
- **Version proposée :** 1.1
- **Date :** 2026-08-31
- **Décideur :** Porteur du Betting Project
- **Portée historique v1.0 :** une campagne locale de preuve J9 sous `WO-SS-20260831-019`
- **Portée proposée v1.1 :** une nouvelle série complète D1/D2/D3 sous un nouveau Work Order ;
  aucune campagne avant acceptation
- **Base historique v1.0 :** `a47c932` sur `codex/j9-decision`
- **Base du draft v1.1 :** `1fcd2cd1a71df74e97e31850a1497a3780e7719f` sur
  `codex/j9-adr002-reexamination`
- **ADR qualifié :** `ADR-SS-001 v1.4`, inchangé
- **Cible Java :** Java 25 LTS
- **Transport :** Playwright existant, manuel, local et opt-in
- **Changement runtime sous v1.0 :** aucun
- **Fait runtime incorporé par le draft v1.1 :** fence WO-021 validé, commit `e2311cc` ; aucun
  nouveau code sous WO-022
- **Acceptation propriétaire v1.0 enregistrée à :** 2026-08-30T22:54:47Z
  (2026-08-31 Europe/Paris)
- **Acceptation propriétaire v1.1 :** `PENDING`
- **Profil v1.1 choisi par le propriétaire :** `RESTART_FULL_D1_D2_D3`, enregistré le
  `2026-08-31T11:07:13.3823864Z`
- **Actions du propriétaire aux endpoints pendant la future campagne :** `UNAVAILABLE`
- **Autre opérateur humain local désigné :** `NO`
- **Modèle d'exécution :** `BLOCKED_PENDING_OPERATOR_DECISION`
- **Effet historique de l'acceptation v1.0 :** première porte de WO-019 franchie ; série ensuite
  consommée et terminée par arrêt après vingt tentatives
- **Effet du draft v1.1 :** aucun ; ni reprise, ni réseau, ni go avant décision propriétaire
- **Option de production VPS future :** non exclue, non mesurée et non autorisée par cet ADR

## 1. Contexte

J8 est validé sur une seconde fenêtre comprenant vingt tentatives directes, vingt réponses et vingt
parsings compatibles. Un dossier est exploitable, mais aucun dossier n'est strictement complet.
Cette preuve borne l'accessibilité et la stabilité observées ; elle ne prouve pas la robustesse sur
un corpus hétérogène de plusieurs dossiers.

Le propriétaire retient comme orientation J9 préférée `PREPARE_OPTIONAL_INTEGRATION`, tout en
exigeant une nouvelle preuve réelle avant la décision finale. Il choisit un parcours couvrant une
découverte J3, une découverte tournoi et trois dossiers J4/J5. Son plafond théorique de 38 appels
est supérieur à la borne métier complète J8 de 30 appels.

ADR-SS-001 §9 exige un nouvel ADR lors d'une modification des bornes de volume. La présente
décision traite uniquement cette exception de preuve. Elle ne promeut pas le laboratoire, ne
crée aucune intégration et ne modifie pas durablement la cadence fournisseur.

## 2. Problème à résoudre

Il faut décider si une seule série manuelle et bornée peut être exécutée pour mesurer la robustesse
actuelle sur trois dossiers sans :

- transformer huit campagnes unitaires en orchestration automatique ;
- réutiliser une confirmation ou un contexte Playwright ;
- dépasser 38 appels directs ;
- affaiblir les arrêts, délais, allowlists, limites de taille ou règles sans retry ;
- interpréter l'absence de refus technique comme une permission contractuelle ;
- créer une dépendance du Betting Project envers SofaScore ou le poste Windows.

## 3. Décision acceptée

Le propriétaire a accepté explicitement la présente version et la revue officielle factuelle.
Une campagne unique devient seulement éligible après satisfaction de toutes les autres portes de
WO-019. L'acceptation ne constitue ni la readiness, ni le go global de campagne.

```text
ADR_SS_002_STATUS=ACCEPTED_V1_0
ADR_SS_002_VERSION=1.0
ADR_SS_002_DECISION=AUTHORIZE_ONE_BOUNDED_MULTI_DOSSIER_EVIDENCE_SERIES
ADR_SS_002_ACCEPTANCE_DOES_NOT_CONSTITUTE_GLOBAL_GO=YES
NETWORK_AUTHORIZED=NO
SERIES_COUNT=1
GLOBAL_MAXIMUM_DIRECT_ATTEMPTS=38
MAXIMUM_CONCURRENCY=1
MINIMUM_DELAY_SECONDS=3
REQUEST_TIMEOUT_SECONDS=10
AUTOMATIC_RETRY=0
FALLBACK=0
POLLING=0
SCHEDULER=0
LIVE_MODE=0
REPLACEMENT_DOSSIER=NO
```

### 3.1 Familles fermées

La série utilise uniquement les six familles déjà implémentées et allowlistées :

```text
SCHEDULED_EVENTS
TOURNAMENT_SCHEDULED_EVENTS
EVENT_DETAILS
EVENT_STATISTICS
EVENT_INCIDENTS
EVENT_LINEUPS
```

Aucune route libre, query libre, autre origine, autre transport ou famille différée n'est admise.
`TOURNAMENT_STANDINGS` et `TEAM_RECENT_EVENTS` restent interdits. J4 utilise uniquement la phase 2
sur une identité canonique résolue côté serveur.

### 3.2 Enveloppe d'appels

| Segment | Unité existante | Maximum direct |
|---|---|---:|
| A1 | J3 `SCHEDULED_EVENTS`, pages contiguës `1..25` | 25 |
| A2 | J3 `TOURNAMENT_SCHEDULED_EVENTS` pour D1 | 1 |
| A3 | J4 phase 2 pour D1 | 1 |
| A4 | J5 statistiques, incidents, compositions pour D1 | 3 |
| B1 | J4 phase 2 pour D2 | 1 |
| B2 | J5 statistiques, incidents, compositions pour D2 | 3 |
| B3 | J4 phase 2 pour D3 | 1 |
| B4 | J5 statistiques, incidents, compositions pour D3 | 3 |
|  | **Plafond absolu** | **38** |

Le coût réel peut être inférieur à cause du terminal J3 ou d'un cache frais. Aucun cache ne sera
supprimé, invalidé, contourné ou artificiellement expiré. Un cache hit vaut zéro appel direct et
reste une limite de mesure.

Le plafond agrégé est contrôlé depuis le ledger avant chaque sous-campagne. Il s'agit d'une garde
procédurale ; le runtime ne possède pas de compteur transactionnel global multi-campagne. Si la
readiness ne démontre pas que cette garde peut être appliquée sans ambiguïté, aucun réseau n'est
autorisé et un Work Order de code séparé devient nécessaire.

### 3.3 Corpus immuable et ordre

| Ordre | Dossier | Provider ID | Identité canonique | Rôle |
|---:|---|---:|---|---|
| D1 | Cittadella — Atalanta U23 | `16691018` | `f4713f80-4769-3656-ba51-61d8ac1aa814` | Ancrage J3 du 2026-08-15, phase `15118`, tournoi `824`, saison `99790`, puis J4/J5 |
| D2 | Barracas Central — Rosario Central | `16671566` | `da075869-34d4-3d42-83d2-613583691845` | Cas historique complet J4/J5 et incident `Off the ball foul` |
| D3 | Lille — Paris Saint-Germain | `16310930` | `c40066c9-987b-38d9-b415-869a453d2ad6` | Baseline Playwright récente avec trois familles J5 complètes |

Les identités, leur ordre et leur rôle sont figés. Une identité devenue inéligible arrête la série
avant réseau. Aucun quatrième dossier, remplacement ou sélection dynamique n'est admis.

### 3.4 Autorisation et gestes humains

L'acceptation de l'ADR ne constitue pas le go de campagne. Après readiness, sauvegarde/restauration
V28 et gel du manifeste, le propriétaire doit fournir un go global distinct contenant :

- l'identifiant de WO-019 et la version acceptée d'ADR-SS-002 ;
- les trois provider IDs et identités canoniques ;
- le plafond 38 ;
- une fenêtre UTC exclusive `[FROM,TO)` d'au plus 60 minutes ;
- l'exécutant autorisé.

Le go est à usage unique. Il expire à `TO`, au premier incident global ou à la fin de la série.
Chaque sous-campagne conserve néanmoins sa préparation locale fraîche, sa phrase exacte, son
acquittement, son claim et son action finale. Ces confirmations techniques matérialisent le go ;
elles ne l'étendent pas et ne forment aucune boucle automatique.

Chaque sous-campagne crée un worker JVM, un Chromium et un `BrowserContext` non persistant neufs.
Aucun profil, cookie injecté, `storageState`, HAR, trace, vidéo, capture ou téléchargement n'est
conservé.

### 3.5 Outcomes et arrêt global

- J3 conserve sa sémantique native : `404`, pagination non contiguë, page 25 encore suivie ou
  absence de terminal `hasNextPage=false` arrête toute la série.
- J4 phase 2 conserve `404 -> COMPLETED_UNAVAILABLE`, sans parsing ni retry.
- J5 conserve `404 -> UNAVAILABLE`, puis passe à la famille suivante.
- Un `404` J4/J5 autorisé n'est pas un incident de sécurité, mais borne le verdict global à
  `PARTIAL_BOUNDED`.
- Tout autre incident fournisseur ou local terminal arrête toute la série. Les unités non
  commencées deviennent `NOT_STARTED_AFTER_GLOBAL_STOP` dans le rapport, sans tentative fictive.

Les incidents terminaux incluent `401`, `403`, `429`, `5xx`, timeout, redirection, HTML/challenge,
origine ou route inattendue, schéma incompatible, contenu sensible, taille supérieure à 5 Mio,
erreur de transport/persistance/traitement, perte de lease, défaut de nettoyage, dépassement du
plafond ou fin de fenêtre.

### 3.6 Sources officielles et limite de permission

La revue officielle a été effectuée le `2026-08-30T22:29:27Z`, soit le 31 août en Europe/Paris.
Elle consigne des faits documentaires, pas un avis juridique :

| Source officielle | Observation factuelle | Limite |
|---|---|---|
| `https://www.sofascore.com/en-us/terms-and-conditions` | Conditions déclarées mises à jour le 18 septembre 2024 ; usage personnel/non commercial ; restrictions explicites sur la charge serveur par requêtes automatisées, l'intégration, l'agrégation, le scraping, la reproduction et l'extraction substantielle de base sans consentement explicite, avec les réserves légales formulées par le texte. | Aucun quota, droit d'accès aux endpoints du laboratoire ou mécanisme de consentement n'a été extrait. Le corps direct est rendu dynamiquement ; les clauses ont été retrouvées dans l'index de la même URL officielle. |
| `https://www.sofascore.com/robots.txt` | Fichier `text/plain` de 181 lignes ; exclusions de chemins et sitemaps, sans correspondance textuelle `/api`, `Allow:` ou `Crawl-delay`. | Hôte `www` seulement ; l'absence de règle `/api` n'est pas une permission et ne décrit pas `api.sofascore.com`. |
| `https://api.sofascore.com/api/docs/external` | Point d'entrée officiel intitulé `Sofascore API`, accessible mais sans contenu extractible. | Aucune authentification, licence, clé, tarification, limite d'appel ou endpoint autorisé n'a pu être établi. |
| `https://corporate.sofascore.com/contact` | Le formulaire officiel contient le chemin `Product -> API`. | Canal de contact seulement ; aucune autorisation ou condition technique publiée. |
| `https://corporate.sofascore.com/widgets` | Un mode d'intégration officiel par widget/iframe est publié. | Il ne documente ni extraction, ni stockage des données, ni accès aux endpoints du laboratoire. |

```text
TERMS_LATEST_DECLARED_UPDATE=2024-09-18
AUTOMATED_SERVER_BURDEN_RESTRICTED=YES
SCRAPING_AGGREGATION_REPRODUCTION_RESTRICTED_WITHOUT_EXPLICIT_CONSENT=YES_WITH_LEGAL_RESERVATION
SUBSTANTIAL_DATABASE_EXTRACTION_RESTRICTED_WITHOUT_EXPLICIT_AGREEMENT=YES_WITH_LEGAL_RESERVATION
OFFICIAL_API_DOC_ENTRYPOINT_PRESENT=YES_TITLE_ONLY
API_AUTHENTICATION_TERMS_EXTRACTED=NO
API_RATE_LIMIT_EXTRACTED=NO
API_LICENSE_EXTRACTED=NO
OFFICIAL_API_CONTACT_CHANNEL_PRESENT=YES
EXPLICIT_PERMISSION_FOR_LAB_ENDPOINTS_EVIDENCED=NO
LEGAL_CONCLUSION=NOT_PROVIDED
```

Le propriétaire a reconnu explicitement cette revue et l'incertitude qu'elle laisse. Cette
acceptation ne transforme ni `robots.txt`, ni l'existence d'une page API, ni une ancienne réussite
technique en consentement. Le canal officiel `Product -> API` reste disponible pour une démarche
distincte ; aucune conclusion juridique n'est formulée ici.

### 3.7 Non-autorisations

Même acceptée, cette décision n'autorise pas :

- une seconde série, une répétition périodique ou une reprise ;
- une hausse de concurrence, un retry ou un fallback ;
- un nouvel endpoint, une URI libre ou un autre transport ;
- une orchestration multi-dossier automatique ;
- la conservation d'un état navigateur ;
- une intégration avec le Betting Project, un push HTTPS ou mTLS ;
- une utilisation commerciale, un déploiement VPS courant ou une dépendance critique ;
- une purge de la base primaire ;
- ADR-SS-003 ou une décision J9 finale.

### 3.8 Position sur une production VPS future

Le recours à Playwright permet de conserver comme option d'architecture un déploiement futur sur
un environnement VPS de production. Cette possibilité n'est donc pas rejetée par principe. Elle
n'est cependant ni étudiée par la campagne Windows résidentielle, ni autorisée par le présent ADR.

```text
FUTURE_VPS_PRODUCTION_OPTION=NOT_EXCLUDED
FUTURE_VPS_FEASIBILITY_STUDY=REQUIRED_SEPARATE_WORK_ORDER
FUTURE_TOPOLOGY_COMPARISON=OPTIONAL_LOCAL_PUSH_VS_VPS_PLAYWRIGHT
FUTURE_STUDY_STATUS=NOT_OPENED
CURRENT_VPS_DEPLOYMENT_AUTHORIZED=NO
CURRENT_PRODUCTION_APPROVAL=NO
VPS_PROVIDER_ACCESSIBILITY=NOT_MEASURED
VPS_PLAYWRIGHT_OPERABILITY=NOT_MEASURED
VPS_TERMS_AND_PERMISSION_COMPATIBILITY=NOT_MEASURED
NO_CRITICAL_DEPENDENCY=REQUIRED
```

Une étude de faisabilité ultérieure, portée par un Work Order distinct après la décision finale J9,
comparera la topologie locale avec push optionnel d'un export déjà `HUMAN_VALIDATED` et une
topologie Playwright exécutée sur VPS. Si J9 ouvre la préparation d'une intégration, ADR-SS-003
portera la décision d'architecture correspondante. L'étude traitera séparément droits d'usage,
caractère commercial, egress et blocages propres aux hébergeurs, sandbox du navigateur, secrets,
certificats, durcissement, ressources, supervision, reprise, mise à jour et dépendance du Betting
Project. Aucun résultat de WO-019 ne sera présenté comme une qualification du réseau ou du runtime
VPS.

## 4. Qualification de la décision existante

ADR-SS-001 v1.4 reste l'autorité générale. ADR-SS-002 ne remplace aucune de ses protections. Il
qualifie uniquement, pour une série et un corpus fermés, la borne de volume portée de 30 à 38.

```text
ADR_SS_001_STATUS=UNCHANGED
ADR_SS_001_PROTECTIVE_RULES=FULLY_APPLICABLE
ADR_SS_002_SCOPE=ONE_EVIDENCE_SERIES_ONLY
PERMANENT_VOLUME_CHANGE=NO
PRODUCTION_PROMOTION=NO
```

## 5. Raisons de la décision

- le propriétaire exige une preuve multi-dossier avant la décision J9 ;
- les parcours unitaires et leur audit existent déjà ;
- trois dossiers hétérogènes réduisent la dépendance à un seul cas sans prétention statistique ;
- une borne absolue, un ordre fermé et l'arrêt au premier incident limitent l'exposition ;
- un ADR séparé rend visible l'augmentation de volume et évite de la faire passer pour un simple
  prolongement de J8.

## 6. Alternatives étudiées

### 6.1 Décider J9 avec J8 uniquement

Avantage : aucun nouvel appel. Limite : le propriétaire a explicitement jugé la preuve de
robustesse insuffisante. Non retenu comme voie préférée ; `KEEP_LOCAL` reste possible selon le
résultat de la preuve et la décision finale J9.

### 6.2 Trois dossiers J4/J5 seulement, maximum 12 appels

Avantage : volume inférieur à la borne J8 et allowlist réduite à quatre familles. Limite : ne
requalifie ni la découverte J3 ni le routage tournoi choisis par le propriétaire. Non retenu.

### 6.3 Trois parcours complets J3 vers J5

Avantage : couverture identique par dossier. Limite : jusqu'à 90 appels et exposition excessive.
Rejeté.

### 6.4 Orchestrateur et disjoncteur global codés

Avantage : plafond transactionnel. Limite : changement runtime hors du besoin de décision et
nouveau risque d'automatisation. Différé ; exigera un Work Order séparé si le contrôle procédural
ne peut pas être qualifié.

### 6.5 Obtenir d'abord un consentement explicite via le canal officiel

Avantage : réduit l'incertitude documentaire. Limite : dépend d'une réponse externe et de ses
conditions. Cette option reste ouverte et peut devenir une porte préalable décidée par le
propriétaire.

## 7. Conséquences

### 7.1 Positives

- décision de volume visible et révocable ;
- corpus, coût, ordre et arrêts reproductibles ;
- séparation claire entre preuve, décision J9 et future intégration ;
- aucune modification de code ni d'ADR-SS-001.
- conservation explicite d'une option VPS future sans la confondre avec une approbation actuelle.

### 7.2 Négatives et risques

- jusqu'à 38 requêtes directes dans une fenêtre ;
- garde globale procédurale, non transactionnelle ;
- huit campagnes unitaires et gestes opérateur associés ;
- possibilité de résultat partiel ou d'arrêt dès le premier segment ;
- restrictions officielles observées et absence de permission explicite prouvée ;
- aucune généralisation statistique, juridique ou de production.
- aucune mesure d'accessibilité ou d'opérabilité Playwright depuis un VPS.

## 8. Acceptation propriétaire de l'ADR

Le propriétaire a confirmé explicitement le bloc suivant, enregistré le
`2026-08-30T22:54:47Z` (2026-08-31 Europe/Paris) :

```text
ADR_SS_002_OWNER_DECISION=ACCEPT
OFFICIAL_SOURCE_REVIEW_ACKNOWLEDGED=YES
CORPUS_D1_D2_D3_ACCEPTED=YES
GLOBAL_MAXIMUM_DIRECT_ATTEMPTS_38_ACCEPTED=YES
ONE_GLOBAL_GO_MODEL_ACCEPTED=YES
GO_MAXIMUM_DURATION_60_MINUTES_ACCEPTED=YES
NATIVE_J4_J5_404_CONTINUATION_ACCEPTED=YES
PRIMARY_DATABASE_PURGE=NO
INTEGRATION_OR_PRODUCTION_AUTHORIZED=NO
FUTURE_VPS_PRODUCTION_OPTION_ACKNOWLEDGED=NOT_EXCLUDED_BUT_NOT_AUTHORIZED
```

```text
ADR_OWNER_ACCEPTANCE_RECORDED=YES
ADR_OWNER_ACCEPTANCE_SCOPE=ADR_SS_002_V1_0_ONLY
OFFLINE_READINESS_REQUIRED=YES
V28_BACKUP_RESTORE_REQUIRED=YES
GLOBAL_OWNER_GO_REQUIRED=YES
NETWORK_AUTHORIZED=NO
```

Cette acceptation franchit uniquement la première porte de WO-019. Elle n'autorise pas une
acquisition fournisseur, une intégration, une production VPS, une purge primaire ou la création
implicite d'ADR-SS-003.

## 9. Déclencheurs de réexamen

Un nouvel ADR est requis avant :

- changement du corpus, de l'ordre, de la fenêtre ou du plafond 38 ;
- deuxième série, reprise après incident ou remplacement de dossier ;
- nouveau endpoint, transport, retry, fallback, concurrence ou automatisation ;
- conservation d'un état navigateur ;
- autorisation d'intégration, d'usage commercial, de production VPS ou de dépendance du Betting
  Project ; l'étude de faisabilité locale-push versus VPS-Playwright relève d'un Work Order futur
  distinct et la décision d'architecture éventuelle d'ADR-SS-003 ;
- changement matériel des conditions officielles ou réponse spécifique de SofaScore ;
- création d'un mécanisme global exécutoire dans le runtime.

## 10. Références

1. `ADR-SS-001-experimentation-endpoints-sofascore-depuis-windows.md`, version 1.4.
2. `docs/work_orders/active/WO-SS-20260831-018-decision-j9.md`.
3. `docs/work_orders/active/WO-SS-20260831-019-j9-provider-robustness.md`.
4. `docs/work_orders/active/WO-SS-20260831-022-j9-adr-ss-002-reexamination.md`.
5. `docs/benchmark/J8-BENCHMARK-REPORT-20260830.md`.
6. `docs/runbooks/J8-BENCHMARK.md`.
7. `docs/runbooks/J6-BACKUP-RESTORE-AND-RETENTION.md`.
8. Sources officielles listées aux paragraphes 3.6 et 11.6, consultées le 2026-08-31 local.

## 11. Réexamen après WO-021 — draft v1.1 sans effet exécutoire

### 11.1 Déclencheurs et état terminal v1.0

Le réexamen demandé après la validation de WO-021 conclut que v1.0 ne peut pas être réutilisée.
Deux déclencheurs du paragraphe 9 sont réalisés :

- reprise après incident et arrêt global ;
- création, sous WO-021, d'un fence global exécutoire commun aux campagnes et workers.

Le paragraphe 3.7 interdisait en outre explicitement une seconde série ou une reprise. Le go unique
v1.0 est consommé, expiré et terminé par l'arrêt. Le rapport historique reste immuable.

```text
ADR_SS_002_REEXAMINATION_RESULT=NEW_VERSION_REQUIRED
ADR_SS_002_V1_0_STATE=ACCEPTED_CONSUMED_AND_TERMINATED_BY_STOP
ADR_SS_002_V1_0_FILE_SHA256_BEFORE_V1_1_DRAFT=F68792FF722708F436957D3414317F2B5AC9FA2E2FA77BC949274FED144B3957
ADR_SS_002_V1_0_FILE_SHA256_SCOPE=HISTORICAL_V1_0_FILE_BEFORE_V1_1_DRAFT
ADR_SS_002_V1_0_SOURCE_COMMIT=af7fe179315053aedff31f0b3f6a31f6e4a8e54c
ADR_SS_002_V1_0_REUSABLE_FOR_RESUME=NO
ADR_SS_002_V1_1_STATUS=DRAFT_SELECTED_PROFILE_PENDING_OWNER_ACCEPTANCE
PRIOR_GLOBAL_OWNER_GO_REUSABLE=NO
PRIOR_EVIDENCE_RESULT=STOPPED
PRIOR_DIRECT_ATTEMPTS_FROZEN=20
PRIOR_D1_PROVIDER_START_DELAY=NOT_MEASURED
WO021_STATUS=VALIDATED
NETWORK_AUTHORIZED=NO
```

WO-021 qualifie prospectivement le runtime corrigé en loopback. Il ne reconstitue ni ne
requalifie les départs fournisseur D1 historiques.

### 11.2 Profils examinés et choix propriétaire

| Profil | Nouveaux appels max. | Cumul max. | D1 rejoué | Nouveau verdict autonome complet |
|---|---:|---:|---|---|
| `CONTINUE_D2_D3_ONLY` | 8 | 28 | Non | Non |
| `RESTART_FULL_D1_D2_D3` | 38 | 58 | Oui | Oui |
| `DO_NOT_RESUME` | 0 | 20 | Non | Non |

Le réexamen recommandait `CONTINUE_D2_D3_ONLY` pour limiter l'exposition. Le propriétaire choisit
néanmoins `RESTART_FULL_D1_D2_D3`. La conséquence technique de ce profil est de rendre possible une
preuve autonome et homogène sur D1, D2 et D3. Le choix a été reçu le
`2026-08-31T11:07:13.3823864Z`.

```text
J9_ADR_SS_002_REEXAMINATION_OWNER_DECISION=SELECT_RESTART_FULL_D1_D2_D3
J9_ADR_SS_002_V1_1_SELECTED_PROFILE=RESTART_FULL_D1_D2_D3
J9_PROFILE_SELECTION_RECORDED_AT_UTC=2026-08-31T11:07:13.3823864Z
J9_PROFILE_SELECTION_EFFECT=DRAFT_REWRITE_ONLY
J9_ADR_SS_002_V1_1_OWNER_ACCEPTANCE=PENDING
J9_PROVIDER_NETWORK_AUTHORIZED=NO
J9_NEW_CAMPAIGN_AUTHORIZED=NO
J9_NEW_PROVIDER_GO_GRANTED=NO
```

### 11.3 Profil normatif proposé : nouvelle série complète D1/D2/D3

Le texte ci-dessous est un draft sans effet exécutoire. S'il est accepté, il rendra seulement une
décision d'ouverture ultérieure d'un nouveau Work Order et d'un nouveau worktree recevable pour une
série unique. Il ne les ouvre pas, ne rouvre pas WO-019, ne réutilise pas la série v1.0 et
n'autorise aucune troisième série.

```text
ADR_SS_002_V1_1_STATUS=DRAFT_SELECTED_PROFILE_PENDING_OWNER_ACCEPTANCE
ADR_SS_002_V1_1_PROFILE=RESTART_FULL_D1_D2_D3
HISTORICAL_DIRECT_ATTEMPTS_FROZEN=20
NEW_SERIES_LEDGER_BASELINE_DIRECT_ATTEMPTS=0
NEW_SERIES_MAXIMUM_DIRECT_ATTEMPTS=38
AUDIT_CUMULATIVE_BASELINE_DIRECT_ATTEMPTS=20
AUDIT_MAXIMUM_CUMULATIVE_DIRECT_ATTEMPTS=58
D1_REEXECUTION_REQUIRED_IF_NEW_SERIES_EXECUTED=YES
D1_REEXECUTION_CURRENTLY_AUTHORIZED=NO
SECOND_SERIES_CURRENTLY_AUTHORIZED=NO_PENDING_ADR_ACCEPTANCE_AND_ALL_GATES
THIRD_SERIES_AUTHORIZED=NO
REPLACEMENT_DOSSIER_ALLOWED=NO
NEW_FULL_RESTART_CAMPAIGN_WORK_ORDER_REQUIRED=YES
NEW_FULL_RESTART_CAMPAIGN_WORK_ORDER=NOT_OPENED
NEW_FULL_RESTART_CAMPAIGN_WORKTREE_REQUIRED=YES
WO019_HISTORICAL_CAMPAIGN_REOPENED=NO
```

Le prochain numéro disponible observé est `WO-SS-20260831-023`, mais le choix du profil n'autorise
pas encore son ouverture. La règle « un Work Order et un worktree par campagne » reste applicable.

| Segment v1.1 | Unité existante | Maximum nouveau | Cumul série | Cumul audit J9 |
|---|---|---:|---:|---:|
| Baseline historique immuable | Rapport v1.0 arrêté | 0 | 0 | 20 |
| A1 | J3 `SCHEDULED_EVENTS`, pages contiguës `1..25` | 25 | 25 | 45 |
| A2 | J3 `TOURNAMENT_SCHEDULED_EVENTS` pour D1 | 1 | 26 | 46 |
| A3 | J4 phase 2 pour D1 | 1 | 27 | 47 |
| A4 | J5 statistiques, incidents et compositions pour D1 | 3 | 30 | 50 |
| B1 | J4 phase 2 pour D2 | 1 | 31 | 51 |
| B2 | J5 statistiques, incidents et compositions pour D2 | 3 | 34 | 54 |
| B3 | J4 phase 2 pour D3 | 1 | 35 | 55 |
| B4 | J5 statistiques, incidents et compositions pour D3 | 3 | 38 | 58 |

L'ordre fermé reste A1 à B4. D1 utilise la date `2026-08-15`, la phase `15118`, le tournoi `824`,
la saison `99790`, le provider ID `16691018` et l'identité canonique
`f4713f80-4769-3656-ba51-61d8ac1aa814`. D2 et D3 conservent respectivement les couples
`16671566` / `da075869-34d4-3d42-83d2-613583691845` et
`16310930` / `c40066c9-987b-38d9-b415-869a453d2ad6`.

Le ledger propre à la nouvelle série refuse `38+1` et `36+3`. Le ledger cumulatif d'audit refuse
`58+1` et `56+3`. Les vingt tentatives historiques ne sont ni effacées ni imputées au compteur de
la nouvelle série. Les unités non consommées ne deviennent pas une réserve de retry, de rejeu, de
remplacement ou de troisième série.

Le coût réel peut être inférieur si J3 termine avant la page 25 ou si le cache métier répond. Aucun
cache ne sera supprimé, invalidé, contourné ou artificiellement expiré. Un cache hit vaut zéro appel
direct et empêche un `PASS` autonome pour la dimension concernée. Un `404` natif J4/J5 conserve les
sémantiques existantes et borne le nouveau verdict à `PARTIAL_BOUNDED`.

Le fence WO-021, la concurrence `1`, le minimum `3 s`, le timeout `10 s`, la limite 5 Mio, le
contexte neuf et toutes les règles sans retry, fallback, polling, scheduler, cache forcé ou artefact
navigateur restent obligatoires.

### 11.4 Modèle opérateur et futur go

Le propriétaire indique qu'il ne pourra pas réaliser les actions locales nécessaires aux huit
sous-campagnes A1 à B4. Cette indisponibilité personnelle est une porte matérielle : elle ne vaut ni
désignation d'un autre humain, ni délégation à Codex, ni permission de transformer les huit
séquences unitaires en orchestration.

```text
OWNER_ENDPOINT_ACTIONS_AVAILABLE_FOR_NEW_CAMPAIGN=NO
OTHER_HUMAN_LOCAL_OPERATOR_DESIGNATED=NO
CURRENT_EXECUTION_MODEL=EIGHT_FRESH_MANUAL_SUBCAMPAIGN_SEQUENCES
CURRENT_EXECUTION_MODEL_SATISFIABLE=NO_PENDING_OPERATOR_DECISION
CAMPAIGN_EXECUTION_STATE=BLOCKED_PENDING_OPERATOR_DECISION
OPERATOR_PATH_OWNER_DECISION=PENDING
AUTOMATED_MULTI_CAMPAIGN_ORCHESTRATION_AUTHORIZED=NO
AUTOMATED_UI_SUBMISSION_AUTHORIZED=NO
CODEX_LOCAL_UI_EXECUTION_AUTHORIZED=NO
PROVIDER_NETWORK_AUTHORIZED=NO
NEW_GLOBAL_OWNER_GO_GRANTED=NO
```

ADR-SS-001 v1.4 et les runbooks J4/J5 définissent la voie normale comme des séquences unitaires
fraîchement préparées et confirmées ; la procédure J4 interdit notamment le script, le navigateur
automatisé, le polling et le rafraîchissement automatique. Un précédent versionné existe toutefois :
[WO-017](docs/work_orders/completed/WO-SS-20260830-017-j5-incidents-empty-shootout-action-v15.md)
et son [rapport](docs/validation/J5-V15-PROVIDER-QUALIFICATION-20260830.md) consignent une campagne
unitaire exécutée par `CODEX_LOCAL_UI` après dérogation propriétaire explicite. Ce précédent ne vaut
pas délégation pour J9 : le message courant constate seulement l'indisponibilité du propriétaire.

Les voies actuellement recevables sont : différer la campagne jusqu'à disponibilité du
propriétaire, sélectionner le modèle d'un autre opérateur humain local, ou sélectionner le modèle
d'une exécution ponctuelle `CODEX_LOCAL_UI` des huit séquences, sans orchestration. L'autorisation
effective de l'acteur retenu sera consignée dans le futur Work Order. Si le besoin porte au contraire
sur un script, un scheduler, une orchestration UI ou une modification runtime, un Work Order de code
et un réexamen d'ADR-SS-001 seront requis. L'acceptation de v1.1 peut intervenir avec cette porte
encore bloquée.

Après résolution du modèle opérateur et satisfaction de toutes les autres portes, un nouveau go
identifiera v1.1 accepté, le nouveau Work Order de campagne, le rapport historique et son hash, les
trois dossiers, les maxima 38 nouveaux et 58 cumulés, l'acteur d'exécution autorisé par la décision
séparée et une fenêtre UTC d'au plus 60 minutes. Il sera consommé au premier claim J3 accepté. Une
seconde utilisation, un
redémarrage, une deuxième instance, un incident, la fin de fenêtre ou la fin de D3 le terminera.

### 11.5 Portes cumulatives

Avant tout nouveau go :

1. acceptation explicite du présent texte v1.1 ;
2. résolution et autorisation du modèle opérateur ;
3. ouverture d'un nouveau Work Order et d'un nouveau worktree de campagne ;
4. readiness fraîche avec tests standards et d'intégration, qualifications loopback J3/J4/J5,
   fence inter-worker/inter-campagne, cleanup, artefacts, secrets et flags bloquants ;
5. nouvelle sauvegarde chiffrée V28 post-arrêt et restauration isolée couvrant au minimum le
   snapshot `814`, les occurrences `778..781` et les vingt tentatives ;
6. nouveau manifeste gelé et corroboration indépendante des deux ledgers ;
7. nouveau go global explicite et à usage unique.

La sauvegarde précédente couvrait au maximum le snapshot `794` et ne contient donc pas la preuve D1
acquise pendant la série arrêtée.

```text
HISTORICAL_REPORT_RESULT=STOPPED
HISTORICAL_REPORT_REWRITTEN=NO
NEW_AUTONOMOUS_REPORT_REQUIRED=YES
NEW_AUTONOMOUS_REPORT_RESULT=<PASS|PARTIAL_BOUNDED|STOPPED>
NEW_AUTONOMOUS_REPORT_REWRITES_HISTORICAL_REPORT=NO
J9_FINAL_DECISION=NOT_TAKEN
```

Un `PASS` exige la fin de la nouvelle série sous 38 appels, aucune condition d'arrêt, aucun cache hit
ou `404` limitant une dimension attendue, toutes les réponses compatibles et persistées, un audit
exploitable et les écarts réseau qualifiés. Les seules indisponibilités natives ou lacunes bornées
produisent `PARTIAL_BOUNDED`. Toute condition d'arrêt produit `STOPPED`.

### 11.6 Revue officielle rafraîchie

La consultation documentaire du `2026-08-31T10:18:51.8559197Z` ne découvre aucune permission,
licence ou limite API applicable :

- les conditions officielles déclarent toujours une dernière mise à jour au `2024-09-18` et
  restreignent notamment la charge serveur par requêtes automatisées, l'agrégation, le scraping,
  la reproduction et l'extraction substantielle sans consentement explicite, avec les réserves
  légales du texte ;
- le point d'entrée `Sofascore API` reste accessible sans authentification, licence, tarification,
  limite d'appel ou permission extractible ;
- `robots.txt` comporte 181 lignes sur l'hôte `www`, sans règle `/api`, `Allow` ou `Crawl-delay` ;
  cette absence n'est pas une permission ;
- le canal officiel `Product -> API` et les widgets iframe restent disponibles, sans documenter
  l'extraction ou le stockage via les endpoints du laboratoire.

```text
OFFICIAL_SOURCE_REVIEW_REFRESHED_AT_UTC=2026-08-31T10:18:51.8559197Z
EXPLICIT_PERMISSION_FOR_LAB_ENDPOINTS_EVIDENCED=NO
API_AUTHENTICATION_TERMS_EXTRACTED=NO
API_RATE_LIMIT_EXTRACTED=NO
API_LICENSE_EXTRACTED=NO
LEGAL_CONCLUSION=NOT_PROVIDED
```

Le transport Playwright et l'option VPS future ne neutralisent pas ces faits. Production,
intégration, VPS courant et dépendance critique restent interdits.

### 11.7 Porte propriétaire d'acceptation v1.1

Le choix du profil est enregistré. Le premier bloc reste requis pour l'ADR et ne vaut ni ouverture
du nouveau Work Order, ni résolution du modèle opérateur, ni go réseau :

```text
ADR_SS_002_V1_1_OWNER_DECISION=<ACCEPT|REJECT|REQUEST_CHANGES>
ADR_SS_002_V1_1_PROFILE=RESTART_FULL_D1_D2_D3
ADR_SS_002_V1_1_DRAFT_COMMIT=<commit>
ADR_SS_002_V1_1_FILE_SHA256=<sha256>
ADR_SS_002_V1_0_STATE=ACCEPTED_CONSUMED_AND_TERMINATED_BY_STOP
ADR_SS_002_V1_0_REUSABLE=NO

HISTORICAL_DIRECT_ATTEMPTS_FROZEN_20_ACKNOWLEDGED=<YES|NO>
NEW_FULL_SERIES_MAXIMUM_DIRECT_ATTEMPTS_38_ACCEPTED=<YES|NO>
MAXIMUM_CUMULATIVE_DIRECT_ATTEMPTS_58_ACCEPTED=<YES|NO>
D1_REEXECUTION_ACCEPTED=<YES|NO>
NEW_AUTONOMOUS_REPORT_REQUIRED=YES
THIRD_SERIES_AUTHORIZED=NO
REPLACEMENT_DOSSIER_ALLOWED=NO

OFFICIAL_SOURCE_REVIEW_ACKNOWLEDGED=<YES|NO>
EXPLICIT_PROVIDER_PERMISSION_EVIDENCED=NO
OWNER_ENDPOINT_ACTIONS_AVAILABLE=NO
OTHER_HUMAN_LOCAL_OPERATOR_DESIGNATED=NO
OPERATOR_MODEL_STATUS=UNRESOLVED
CAMPAIGN_EXECUTION_BLOCKED_PENDING_OPERATOR_DECISION=YES
AUTOMATED_UI_ORCHESTRATION_AUTHORIZED=NO
CODEX_LOCAL_UI_EXECUTION_AUTHORIZED=NO

NEW_CAMPAIGN_WORK_ORDER_REQUIRED=YES
NEW_CAMPAIGN_WORKTREE_REQUIRED=YES
NEW_CAMPAIGN_BRANCH_REQUIRED=YES
NEW_CAMPAIGN_WORK_ORDER_OPENING_AUTHORIZED=NO
PROVIDER_NETWORK_AUTHORIZED=NO
CAMPAIGN_EXECUTION_AUTHORIZED=NO
NEW_GLOBAL_OWNER_GO_GRANTED=NO
PRIMARY_DATABASE_PURGE=NO
INTEGRATION_OR_PRODUCTION_AUTHORIZED=NO
J9_FINAL_DECISION=NOT_TAKEN
FUTURE_VPS_PRODUCTION_OPTION=NOT_EXCLUDED_BUT_NOT_AUTHORIZED
```

Le choix de l'acteur est une seconde décision, sans effet réseau, go ou ouverture de Work Order :

```text
J9_V1_1_OPERATOR_PATH_OWNER_DECISION=<DEFER_UNTIL_OWNER_AVAILABLE|SELECT_OTHER_HUMAN_LOCAL_OPERATOR_MODEL|SELECT_ONE_OFF_CODEX_LOCAL_UI_MODEL|REQUEST_AUTOMATED_EXECUTION_STUDY>
SELECTED_EXECUTION_ACTOR=<UNRESOLVED|OWNER|OTHER_HUMAN_LOCAL_OPERATOR|CODEX_LOCAL_UI>
OWNER_ENDPOINT_ACTIONS_AVAILABLE=NO
CODEX_LOCAL_UI_EXECUTION_AUTHORIZED=NO
AUTOMATED_UI_ORCHESTRATION_AUTHORIZED=NO
CAMPAIGN_EXECUTION_AUTHORIZED=NO
PROVIDER_NETWORK_AUTHORIZED=NO
NEW_GLOBAL_OWNER_GO_GRANTED=NO
OPERATOR_DECISION_EFFECT=ACTOR_MODEL_ONLY_NO_EXECUTION_NO_NETWORK_NO_GO_NO_WORK_ORDER_OPENING
```

Une sélection `SELECT_ONE_OFF_CODEX_LOCAL_UI_MODEL` identifiera l'acteur envisagé pour les huit
séquences fraîches et devra être reprise puis autorisée dans le futur Work Order de campagne. Elle
n'autorisera ni exécution, ni script, ni orchestration, ni réseau, ni go.
`REQUEST_AUTOMATED_EXECUTION_STUDY` signalera seulement le besoin d'une étude ; son ouverture exigera
une autorisation et un Work Order distincts. Ce choix ne les ouvrira pas à lui seul.

Après acceptation, WO-022 pourra être clos. Cette clôture ne vaudra ni ouverture de WO-023, ni
readiness, ni sauvegarde, ni manifeste, ni go, ni accès fournisseur.

## 12. Historique

| Version | Date | Évolution |
|---|---|---|
| 1.1-draft | 2026-08-31 | Réexamen après arrêt et WO-021 ; v1.0 déclarée non réutilisable ; `RESTART_FULL_D1_D2_D3` choisi par le propriétaire ; nouvelle série proposée à 38 nouveaux appels et 58 cumulés sous un futur Work Order distinct ; indisponibilité du propriétaire et absence d'autre acteur désigné enregistrées, exécution bloquée ; acceptation v1.1 encore requise ; aucun effet réseau. |
| 1.0 | 2026-08-31 | Acceptation propriétaire explicite du corpus, du plafond 38, du go unique, de la sémantique 404 et des non-autorisations ; option VPS future reconnue mais non autorisée ; aucun go réseau accordé. |
| 0.1 | 2026-08-31 | Proposition d'une série unique à trois dossiers et 38 appels maximum ; revue officielle factuelle ; aucun effet réseau avant décision propriétaire. |

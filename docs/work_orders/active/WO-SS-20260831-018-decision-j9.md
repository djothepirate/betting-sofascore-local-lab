# WO-SS-20260831-018 — Décision de gouvernance J9

- **Statut :** `IN_DEVELOPMENT`
- **Date d'ouverture :** 2026-08-31
- **Décision J9 finale :** `PENDING_OWNER_CONFIRMATION`
- **Orientation propriétaire :** `PREPARE_OPTIONAL_INTEGRATION`
- **Jalon :** J9 — Décision de gouvernance
- **Base locale :** `40323faa7dca3341da6ef980b5762f1ff5a32a79`
- **Branche :** `codex/j9-decision`
- **Prérequis :** J8 `VALIDATED`, WO-016 et WO-017 clôturés
- **ADR applicable :** `ADR-SS-001 v1.4`
- **ADR de preuve :** `ADR-SS-002 v1.0 — ACCEPTED, GLOBAL_GO_CONSUMED_AND_TERMINATED_BY_STOP`
- **Résultat de preuve WO-019 :** `STOPPED — KEEP_LOCAL_RECOMMENDED`
- **ADR d'intégration :** `ADR-SS-003 — NOT_CREATED`
- **Appel fournisseur autorisé par ce Work Order :** `NO`
- **Implémentation d'intégration autorisée :** `NO`
- **Polling, scheduler, live, retry ou fallback :** `NOT_AUTHORIZED`
- **Production, VPS ou dépendance critique :** `NOT_AUTHORIZED`
- **Option de production VPS future :** `NOT_EXCLUDED, NOT_MEASURED, NOT_AUTHORIZED`

## 1. Objectif

Prendre, à partir de preuves versionnées et de limites explicites, la décision propriétaire J9 parmi
les trois options fermées suivantes :

```text
ABANDON
KEEP_LOCAL
PREPARE_OPTIONAL_INTEGRATION
```

Le Work Order distingue strictement :

1. l'orientation que le propriétaire a déjà exprimée ;
2. la preuve complémentaire qu'il exige avant de confirmer son choix final ;
3. une éventuelle implémentation ultérieure, qui reste hors périmètre et exigerait un nouveau Work
   Order ainsi qu'ADR-SS-003.

Les statuts du laboratoire restent inchangés :

```text
EXPERIMENTAL
LOCAL_ONLY
NOT_PRODUCTION_APPROVED
NO_CRITICAL_DEPENDENCY
```

## 2. Choix propriétaire explicite à l'ouverture

Le propriétaire choisit `PREPARE_OPTIONAL_INTEGRATION` comme orientation préférée, mais refuse de
transformer le corpus J8 mono-dossier en décision définitive. Il exige une nouvelle preuve réelle,
bornée et multi-dossier sur la robustesse du fournisseur.

Ce choix est consigné exactement comme suit :

```text
J9_OWNER_ORIENTATION=PREPARE_OPTIONAL_INTEGRATION
J9_DECISION_STATUS=PENDING_PROVIDER_ROBUSTNESS_EVIDENCE
J9_PROVIDER_ACQUISITION_MODE=MANUAL_ON_DEMAND
J9_INTEGRATION_IMPLEMENTATION_AUTHORIZED=NO
J9_LIVE_OR_SCHEDULED_OPERATION_AUTHORIZED=NO
J9_BETTING_PROJECT_CRITICAL_DEPENDENCY=NO
J9_FUTURE_VPS_PRODUCTION_OPTION=NOT_EXCLUDED
J9_CURRENT_VPS_DEPLOYMENT_AUTHORIZED=NO
ADR_SS_002_STATUS=ACCEPTED_V1_0
ADR_SS_003_STATUS=NOT_CREATED
```

Cette orientation n'autorise ni code d'intégration, ni endpoint nouveau, ni appel fournisseur, ni
acceptation implicite d'un ADR. La décision J9 finale reste `PENDING` jusqu'à la revue humaine de
la nouvelle preuve et à une confirmation propriétaire distincte.

### 2.1 Acceptation propriétaire d'ADR-SS-002

L'acceptation propriétaire d'ADR-SS-002 a été enregistrée le `2026-08-30T22:54:47Z` (2026-08-31
Europe/Paris). Elle confirme explicitement la revue officielle, le corpus D1/D2/D3, le plafond de
38 tentatives, le modèle de go global unique d'au plus 60 minutes, la poursuite sur les seuls `404`
natifs J4/J5 et les non-autorisations de purge primaire, d'intégration et de production.

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

Cette décision franchit uniquement la porte ADR. `NETWORK_AUTHORIZED=NO` reste applicable jusqu'à
la readiness hors ligne, la sauvegarde/restauration V28 et un go propriétaire global distinct.

## 3. Référentiel factuel gelé

La matrice initiale repose sur les preuves suivantes, qui ne sont pas modifiées par J9 :

- `docs/benchmark/J8-BENCHMARK-REPORT-20260830.md` ;
- `docs/validation/J8-FINAL-VALIDATION-20260830.md` ;
- `docs/work_orders/completed/WO-SS-20260829-016-benchmark-j8.md` ;
- `docs/architecture/J7-CANONICAL-EVENT-EXPORT.md` et son runbook ;
- `docs/validation/J6-BACKUP-RESTORE-QUALIFICATION-20260819.md` et le runbook J6 ;
- `ADR-SS-001-experimentation-endpoints-sofascore-depuis-windows.md`, version 1.4 ;
- `docs/reference/Betting_Project_SofaScore_Local_Lab_Cadrage_v0.1.0.pdf`, référence immuable.

WO-016, WO-017, le rapport J8, leurs fenêtres, hashes, verdicts et validations restent gelés. J9
ajoute une lecture de décision ; il ne réécrit aucune mesure historique.

## 4. Matrice de décision factuelle initiale

Les qualifications décrivent le niveau de preuve, pas une note pondérée. `PASS_BOUNDED` ne signifie
jamais production-ready ; `PARTIAL` interdit une extrapolation ; `NOT_MEASURED` reste une absence de
preuve, et non un échec ou une réussite supposée.

| Critère J9 | Fait établi au 2026-08-31 | Source de preuve | Qualification initiale | Lacune ou conséquence |
|---|---|---|---|---|
| Accessibilité et stabilité bornée | La seconde fenêtre J8 contient 20 tentatives, 20 réponses et 20 parsings compatibles, sans refus, 404, retry, erreur opérationnelle ni tentative incomplète. | Rapport J8, lignes de synthèse et revue humaine | `PASS_BOUNDED` | Le corpus direct exploitable ne contient qu'un dossier ; la robustesse multi-dossier n'est pas démontrée. |
| Complétude | Le dossier ciblé est exploitable, mais aucun dossier strictement complet n'est prouvé ; statistiques `COMPLETE`, incidents et compositions `PARTIAL`. | Rapport J8, sections complétude et revue ciblée | `PARTIAL` | Une réussite de transport ne suffit pas à prouver une richesse homogène des dossiers. |
| Fraîcheur | Les heures locales de requête et de réception sont connues dans la fenêtre J8 ; aucun retard homogène entre heure source et réception n'est mesuré. | Rapport J8, décision `FRESHNESS=PARTIAL` | `PARTIAL` | Aucun polling, suivi live ou engagement de fraîcheur continue ne peut être déduit. |
| Coût d'appel | J8 mesure exactement 16 appels de découverte, 4 appels marginaux et 20 appels effectifs par dossier exploitable. La preuve J9 cadrée est plafonnée à 38 appels. | Rapport J8 et enveloppe acceptée par ADR-SS-002 v1.0 pour WO-019 | `PARTIAL` | Un seul dossier ne fournit aucun seuil comparatif ; le coût n'est acceptable que pour un usage manuel et borné. |
| Risque de blocage | Aucun incident fournisseur n'est observé dans la seconde fenêtre J8. | Rapport J8, 20/20 réponses et parsings | `PARTIAL` | L'absence d'incident sur une fenêtre et un dossier ne permet aucune prédiction externe. |
| Exactitude | Aucune source de contrôle externe n'est intégrée au laboratoire. | Rapport J8, `CONTROL_SOURCE_ABSENT` | `NOT_MEASURED` | La vérité externe et les divergences métier restent inconnues. |
| Valeur analytique | Aucun comparateur externe n'est déclaré. | Rapport J8, `EXTERNAL_COMPARISON_ABSENT` | `NOT_MEASURED` | La valeur relative pour le Betting Project n'est pas démontrée. |
| Maintenabilité | Aucun temps d'adaptation à une rupture de schéma ou de restauration hors ligne n'est chronométré. | Rapport J8, `ADAPTATION_TIME_ABSENT` | `NOT_MEASURED` | Aucun engagement de compatibilité ou coût de maintenance durable ne peut être publié. |
| Export et audit | J7 produit un document UTF-8 déterministe limité à 5 Mio, avec exactement `manifest` et `data`, schéma `urn:betting-project:sofascore-local-lab:j7:canonical-event-export:v1`, version `1.0.0`; seul `HUMAN_VALIDATED` est téléchargeable. | Architecture et runbook J7 | `PASS_LOCAL` | Aucun contrat de transfert, accusé distant ou état de livraison n'existe encore. |
| Rétention et purge | J6 applique 30 jours, ne purge que les octets bruts après plan et sauvegarde qualifiée, et ne supprime jamais les observations normalisées. La dernière restauration réelle versionnée a été qualifiée sous Flyway V22, couverture maximale snapshot 272 ; les scripts sont maintenant alignés sur V28. | Runbook J6 et qualification du 2026-08-19 | `PARTIAL` | Une sauvegarde chiffrée fraîche V28 et sa restauration isolée doivent être qualifiées avant toute nouvelle campagne réelle. |
| Indépendance du Betting Project | Le dépôt est actuellement local, expérimental, séparé et sans dépendance critique du projet principal. | ADR-SS-001 et cadrage | `PASS_CURRENT` | Un déploiement Playwright futur sur VPS n'est plus exclu, mais sa faisabilité et son impact sur l'indépendance restent `NOT_MEASURED`; il exigerait une décision séparée et ne pourrait devenir une dépendance critique implicite. |
| Conditions d'utilisation | Les conditions officielles déclarées à jour le 18 septembre 2024 restreignent notamment la charge serveur par requêtes automatisées, l'intégration, l'agrégation, le scraping, la reproduction et l'extraction substantielle sans consentement explicite, avec les réserves légales du texte. Un point d'entrée officiel `Sofascore API` et un contact `Product -> API` existent, mais aucune licence, authentification, limite d'appel ou permission pour les endpoints du laboratoire n'a été extraite. | Revue officielle J9 du 2026-08-31, URLs consignées dans ADR-SS-002 et WO-019 | `PARTIAL — RESTRICTIONS_PRESENT_PERMISSION_NOT_EVIDENCED` | Fait documentaire, sans conclusion juridique. L'acceptation d'ADR-SS-002 doit reconnaître cette incertitude ; une production VPS future exigerait une nouvelle revue et, selon la décision propriétaire, un consentement explicite. |
| Sécurité et exploitation | Playwright est local, manuel et opt-in ; chaque campagne utilise un contexte neuf non persistant, sans profil, cookie réutilisé, `storageState`, HAR, trace, vidéo, capture ou téléchargement. Polling et refresh sont désactivés. | ADR-SS-001 v1.4 et architectures J3/J4/J5 | `PASS_BOUNDED` | Ces contrôles restent obligatoires et ne valent que pour les parcours et volumes explicitement autorisés. |

### 4.1 Consolidation après WO-019

Le rapport WO-019 complète la matrice sans réécrire les preuves J6, J7 ou J8 :

| Critère J9 | Fait WO-019 mesuré | Qualification consolidée | Conséquence de décision |
|---|---|---|---|
| Accessibilité et compatibilité D1 | `20/20` tentatives ont reçu `HTTP 200`, ont été parsées et persistées ; aucun retry ou refus n'est observé. | `PASS_BOUNDED` | Succès fonctionnel strictement limité à D1. |
| Complétude D1 | Statistiques `COMPLETE · 100 %`, incidents `PARTIAL · 91 %`, compositions `PARTIAL · 99 %`. | `PARTIAL` | D1 est exploitable mais non strictement complet. |
| Coût d'appel | `20` tentatives directes sur un plafond de `38` ; D2 et D3 ont reçu zéro appel après l'arrêt. | `PARTIAL` | Le coût multi-dossier prévu n'est pas mesuré. |
| Risque de blocage fournisseur | Aucun `401`, `403`, `404`, `429`, `5xx`, timeout ou schéma incompatible n'est observé sur D1. | `PARTIAL` | L'absence d'incident sur D1 ne démontre pas la robustesse multi-dossier. |
| Délai exact entre départs fournisseur | Les timestamps persistés sont pré-navigation ; l'écart calculé de `2 967 ms` ne prouve ni violation on-wire ni conformité au minimum de trois secondes. | `NOT_MEASURED` | Le contrôle procédural courant ne permet pas de démontrer l'invariant ; la série est `STOPPED`. |
| Export post-campagne | Le double export byte-identique n'a pas été exécuté après l'arrêt global. | `NOT_MEASURED` | La reproductibilité d'export ne peut pas être promue comme acquis de cette campagne. |
| Sécurité post-arrêt | Arrêt gracieux réussi, zéro listener 8087, zéro processus applicatif ou descendant possédé, zéro artefact navigateur interdit, flags fournisseur à `false`. | `PASS_BOUNDED` | Le réseau reste verrouillé et le go consommé ne peut pas être rejoué. |

La preuve globale est `STOPPED`. Les conditions de `PREPARE_OPTIONAL_INTEGRATION` ne sont donc pas
réunies et aucune incompatibilité structurelle n'établit `ABANDON`. La recommandation déterministe
est `KEEP_LOCAL`, en attente de confirmation propriétaire explicite.

## 5. Règles de recommandation

La matrice ne choisit jamais automatiquement à la place du propriétaire. Elle produit une
recommandation reproductible selon les règles suivantes :

| Option | Condition de recommandation |
|---|---|
| `PREPARE_OPTIONAL_INTEGRATION` | La campagne complémentaire est `PASS`, la sauvegarde/restauration et l'audit sont conformes, aucune incompatibilité n'est établie par les sources officielles consultées et l'indépendance reste démontrée. Cette option autorise seulement la préparation d'ADR-SS-003. |
| `KEEP_LOCAL` | La preuve est `PARTIAL_BOUNDED`, un critère matériel reste non mesuré, un incident transitoire arrête la campagne ou l'incertitude ne permet pas de préparer une intégration. |
| `ABANDON` | Une incompatibilité structurelle est établie : conditions d'utilisation incompatibles, besoin d'un contournement interdit, impossibilité d'audit/rétention sûre ou dépendance critique inévitable. |

Un incident isolé ne sera pas artificiellement qualifié d'incompatibilité structurelle. En
l'absence de preuve suffisante, la recommandation prudente est `KEEP_LOCAL`, jamais une promotion
implicite.

## 6. Preuve complémentaire exigée

La preuve fournisseur relève d'un Work Order séparé :

```text
WORK_ORDER=WO-SS-20260831-019-j9-provider-robustness
BRANCH=codex/j9-provider-robustness
WORK_ORDER_STATUS=STOPPED
EVIDENCE_STATUS=STOPPED
PROVIDER_CALLS_UNDER_WO019=20
WO019_WORK_ORDER_RESUME_AUTHORIZED=NO_STOPPED
WO019_PROVIDER_CAMPAIGN_AUTHORIZED=NO_STOPPED
WO019_PROVIDER_CAMPAIGN_RESUME_AUTHORIZED=NO
OFFLINE_READINESS=PASS_REEXECUTED_AFTER_VALIDATED_WO020
OFFLINE_READINESS_REEXECUTED_ON=2026-08-31
V28_BACKUP_RESTORE=QUALIFIED
V28_RESTORE_QUALIFIED=YES
V28_BACKUP_RESTORE_QUALIFIED_AT_UTC=2026-08-31T06:46:07.7013794Z
NEXT_GATE=SEPARATE_RUNTIME_WORK_ORDER_FOR_MEASURABLE_PROVIDER_START_DELAY
NETWORK_AUTHORIZED=NO
GLOBAL_OWNER_GO=CONSUMED_AND_TERMINATED_BY_STOP
OWNER_GO_CONSUMED=YES
MAXIMUM_DIRECT_ATTEMPTS=38
WO019_D2_D3_PROVIDER_ATTEMPTS=0
WO019_REPORT=docs/validation/J9-PROVIDER-ROBUSTNESS-CAMPAIGN-20260831.md
WO019_REPORT_SHA256=47e6171eeb1fc44cf995c727d4f09107875c844e71e8b183068731cbb4c62b9d
PREVIOUS_GRACEFUL_CLOSE_RUNTIME_WORK_ORDER=WO-SS-20260831-020-j9-playwright-graceful-close
PREVIOUS_GRACEFUL_CLOSE_RUNTIME_WORK_ORDER_STATUS=VALIDATED
NEW_PROVIDER_START_DELAY_RUNTIME_WORK_ORDER_REQUIRED=YES
NEW_PROVIDER_START_DELAY_RUNTIME_WORK_ORDER=WO-SS-20260831-021-j9-playwright-minimum-on-wire-delay
NEW_PROVIDER_START_DELAY_RUNTIME_WORK_ORDER_STATUS=OPEN_AWAITING_OWNER_AUTHORIZATION
NEW_PROVIDER_START_DELAY_RUNTIME_WORK_ORDER_BRANCH=codex/j9-playwright-minimum-delay
NEW_PROVIDER_START_DELAY_IMPLEMENTATION_AUTHORIZED=NO
J9_DECISION_RECOMMENDATION=KEEP_LOCAL
J9_DECISION_STATUS=PENDING_OWNER_CONFIRMATION
INTEGRATION_OR_PRODUCTION_AUTHORIZED=NO
```

WO-021 est ouvert par le déclencheur déjà prévu dans le plan J9, sans autoriser son implémentation.
Sa décision propriétaire reste distincte de la décision J9 finale et de toute future reprise de
WO-019.

ADR-SS-002 v1.0 qualifie l'augmentation de volume exigée par ADR-SS-001 §9. Le précédent métier
complet J8 autorisait au plus 30 tentatives pour un dossier ; la preuve J9 acceptée en prévoit au
plus 38 pour trois dossiers.

Les quatre portes cumulatives avaient été satisfaites avant le premier claim fournisseur :

1. ADR-SS-002 accepté explicitement par le propriétaire — `SATISFIED` ;
2. readiness hors ligne entièrement verte — `SATISFIED_BY_REEXECUTION_AFTER_VALIDATED_WO020` ;
3. sauvegarde chiffrée V28 fraîche et restauration qualifiée sur une base isolée ;
4. go propriétaire global explicite, unique et alors non consommé.

Ce go a depuis été consommé au premier claim J3 accepté puis terminé par l'arrêt de la série ; cette
photographie historique n'accorde donc aucune autorisation courante.

L'échec initial J3 a été reproduit hors sandbox (`14` tests, `12` erreurs `RUNTIME_FAILURE`, `2`
scénarios d'arrêt opérateur réussis), puis WO-020 a établi et corrigé localement la séquence
circulaire `CLOSED` / attente d'EOF parent. Les tests superviseur, protocole et sécurité ainsi que
les qualifications loopback J3, J4 et J5 sont désormais verts, sans accès fournisseur. Le
propriétaire a validé WO-020 et autorisé son déplacement vers les Work Orders terminés le
2026-08-31 à `00:41:58Z`, puis a autorisé séparément la reprise de WO-019 le 2026-08-31 :

> J’autorise la reprise de WO-019

Cette autorisation a d'abord placé WO-019 à `READY_FOR_OFFLINE_READINESS`, avec
`OFFLINE_READINESS=AUTHORIZED_NOT_EXECUTED`. Elle n'a pas autorisé la reprise de la campagne
fournisseur : `WO019_PROVIDER_CAMPAIGN_RESUME_AUTHORIZED=NO`.

Un rejeu distinct propre à WO-019 a ensuite réussi : `clean verify` compte `931/0/0/4`, la suite
d'intégration `67/0/0/0`, la commande exacte
`powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\Verify-Local.ps1 -WithIntegrationTests`
rend `PASS` avec intégrations `YES` et réseau SofaScore `NO`, et
`docker compose --env-file .env config --quiet` rend `PASS`. Les qualifications J3, J4 et J5,
lancées sous `pwsh`, comptent chacune `14/0/0/0`, sans accès fournisseur. La readiness devient
`PASS_REEXECUTED_AFTER_VALIDATED_WO020` ; WO-019 passe alors à
`READY_FOR_V28_BACKUP_RESTORE`.

La sauvegarde/restauration chiffrée V28 a ensuite été qualifiée sous PowerShell 7 natif interactif :
Flyway `28`, restauration qualifiée, zéro mismatch source/restauration, zéro échec d'intégrité brute,
zéro fichier partiel et zéro base temporaire résiduelle lors du contrôle indépendant. Le connecteur
est `SAFE`, le port 8087 est libre, aucun accès fournisseur ni purge primaire n'a eu lieu. WO-019
passe à `READY_FOR_GLOBAL_OWNER_GO`.

Le go global a ensuite été accordé pour la fenêtre exclusive
`[2026-08-31T07:15:00Z,2026-08-31T08:15:00Z)`, puis consommé au premier claim J3 accepté. La série
a exécuté 20 tentatives directes sur D1, toutes `HTTP 200` et compatibles. Elle a été arrêtée avant
D2 parce que le runtime ne permet pas de mesurer le départ réseau exact requis pour démontrer le
délai strict de trois secondes. Le go est maintenant consommé et terminé par l'arrêt.

État après arrêt :

```text
PROVIDER_NETWORK=NOT_AUTHORIZED
OWNER_GO=CONSUMED_AND_TERMINATED_BY_STOP
OWNER_GO_CONSUMED=YES
WO019_EVIDENCE_RESULT=STOPPED
WO019_TOTAL_DIRECT_ATTEMPTS=20
WO019_D2_D3_PROVIDER_ATTEMPTS=0
J9_DECISION_RECOMMENDATION=KEEP_LOCAL
J9_DECISION_STATUS=PENDING_OWNER_CONFIRMATION
```

## 7. Frontière d'une intégration optionnelle future

L'orientation préférée décrit seulement la direction à étudier si J9 la confirme :

- le laboratoire termine et committe localement la décision `HUMAN_VALIDATED` avant tout transfert ;
- un futur transfert pousserait l'export J7 vers un endpoint HTTPS dédié du Betting Project ;
- l'état de livraison resterait distinct de l'état de validation locale ;
- une future authentification mTLS utiliserait une clé privée non exportable du magasin de
  certificats de l'utilisateur Windows ;
- l'acquisition fournisseur resterait manuelle et à la demande ; un transfert d'export validé ne
  déclencherait jamais une acquisition SofaScore ;
- retries bornés, idempotence, accusés, erreurs, contrat HTTP, supervision et rotation du
  certificat seraient décidés dans ADR-SS-003, pas dans ce Work Order.

Playwright rend également envisageable, sans l'autoriser, une autre topologie où le laboratoire ou
un composant dérivé s'exécuterait un jour sur un VPS de production. Cette option n'est plus exclue
par principe. Une étude de faisabilité ultérieure, sous Work Order distinct, comparera le push local
optionnel ci-dessus à une topologie VPS Playwright, puis instruira séparément droits d'usage,
egress, sandbox navigateur, secrets, certificats, supervision, limites de ressources,
disponibilité et indépendance métier. Si la décision finale J9 le justifie, ADR-SS-003 portera le
choix d'architecture. La campagne résidentielle Windows de WO-019 ne mesurera pas l'accessibilité
depuis un VPS.

```text
FUTURE_FEASIBILITY_STUDY=REQUIRED_SEPARATE_WORK_ORDER
FUTURE_COMPARISON=OPTIONAL_LOCAL_PUSH_VS_VPS_PLAYWRIGHT
FUTURE_STUDY_STATUS=NOT_OPENED
CURRENT_VPS_DEPLOYMENT_AUTHORIZED=NO
```

Cette frontière n'est ni une API publique, ni une spécification implémentable, ni une autorisation
d'écrire le client ou le serveur.

## 8. Périmètre inclus

- matrice factuelle et traçable ;
- orientation propriétaire conditionnelle ;
- dépendance explicite envers WO-019 et ADR-SS-002 ;
- mise à jour du README et du changelog ;
- revue finale des trois options après la preuve ;
- déplacement du présent Work Order vers `completed` uniquement après décision finale explicite.

## 9. Hors périmètre

- tout appel fournisseur sous WO-018 ;
- acceptation d'ADR-SS-002 ou création d'ADR-SS-003 au nom du propriétaire ;
- intégration, endpoint, schéma SQL, migration ou modification du format J7 ;
- polling, scheduler, tâche périodique, mode live ou collecte automatique ;
- test de charge, généralisation statistique ou promesse de disponibilité ;
- déploiement VPS ou production dans le lot courant ; cette topologie future reste ouverte mais
  non mesurée et non autorisée ;
- payload brut, URI concrète, header, cookie, jeton, certificat ou secret dans Git ;
- modification des preuves gelées J6, J7 et J8.

## 10. Bloc de décision finale réservé

Après qualification et revue humaine de WO-019, le propriétaire reçoit le bloc suivant, rempli avec
les faits et la recommandation mais sans confirmation automatique. Le champ `J9_DECISION` ci-dessous
est la valeur recommandée à confirmer ou à remplacer explicitement :

```text
J9_DECISION=KEEP_LOCAL
J9_DECIDED_AT_UTC=<owner confirmation timestamp>
J9_EVIDENCE_RESULT=STOPPED
J9_EVIDENCE_REFERENCE=J9-PROVIDER-ROBUSTNESS-CAMPAIGN-20260831;SHA256=47e6171eeb1fc44cf995c727d4f09107875c844e71e8b183068731cbb4c62b9d
J9_PROVIDER_ACQUISITION_MODE=MANUAL_ON_DEMAND
J9_INTEGRATION_IMPLEMENTATION_AUTHORIZED=NO
J9_LIVE_OR_SCHEDULED_OPERATION_AUTHORIZED=NO
J9_BETTING_PROJECT_CRITICAL_DEPENDENCY=NO
J9_FUTURE_VPS_PRODUCTION_OPTION=NOT_EXCLUDED
J9_CURRENT_VPS_DEPLOYMENT_AUTHORIZED=NO
J9_OWNER_CONFIRMATION_REQUIRED=YES
```

Seule la réponse propriétaire explicite permettra de remplacer le statut `PENDING`, d'écrire
`J9_OWNER_CONFIRMATION_REQUIRED=NO` et de clôturer WO-018.

## 11. Critères d'acceptation du lot d'ouverture

- branche créée depuis la baseline propre attendue ;
- Work Order actif et matrice présents ;
- orientation et décision finale non confondues ;
- README et changelog à jour ;
- ADR-SS-001 et preuves gelées non modifiés ;
- aucun code, endpoint, migration, configuration réseau ou appel fournisseur ajouté ;
- `mvnw.cmd clean verify` vert ;
- `git diff --check` vert ;
- vérification des secrets, du loopback et des flags réseau concluante ;
- commit local dédié, sans push ni fusion vers `main`.

## 12. Journal d'exécution

À compléter avec les commandes, comptes de tests et résultats réels. Aucun succès ne sera écrit
avant son observation.

```text
OPENING_BASELINE=40323faa7dca3341da6ef980b5762f1ff5a32a79
OPENING_BRANCH=codex/j9-decision
STANDARD_VERIFY=PASS_928_TESTS_0_FAILURE_0_ERROR_4_SKIPPED
STANDARD_VERIFY_COMMAND=.\mvnw.cmd clean verify
DIFF_CHECK=PASS
SECRET_SCAN=PASS_NO_CREDENTIAL_PATTERN_IN_J9_OPENING_FILES
LOOPBACK_AND_NETWORK_DEFAULTS_CHECK=PASS
SERVER_ADDRESS_DEFAULT=127.0.0.1
SOFASCORE_ENABLED_DEFAULT=false
SOFASCORE_PLAYWRIGHT_ENABLED_DEFAULT=false
SOFASCORE_J3_J4_J5_QUALIFICATION_DEFAULTS=false
AUTOMATIC_REFRESH_DEFAULT=false
LIVE_POLLING_DEFAULT=false
PROVIDER_CALLS_DURING_OPENING=0
WO020_STATUS=VALIDATED
WO020_LOCATION=docs/work_orders/completed/WO-SS-20260831-020-j9-playwright-graceful-close.md
WO020_LOCAL_READINESS=PASS
WO020_OWNER_VALIDATION=RECEIVED_2026-08-31T00:41:58Z
WO020_PROVIDER_ACCESS_PERFORMED=NO
WO019_EVIDENCE_STATUS=DRAFT
WO019_CAMPAIGN_RESUME_AUTHORIZED=NO
WO019_NETWORK_AUTHORIZED=NO
WO019_GLOBAL_OWNER_GO=NOT_GRANTED
```

Le bloc précédent conserve l'état historique à la clôture de WO-020. L'autorisation distincte
ultérieure est enregistrée sans le réécrire :

```text
OWNER_WO019_RESUME_AUTHORIZATION_RECEIVED_ON=2026-08-31
WO019_STATUS=READY_FOR_OFFLINE_READINESS
WO019_WORK_ORDER_RESUME_AUTHORIZED=YES
WO019_PROVIDER_CAMPAIGN_AUTHORIZED=NO_PENDING_GLOBAL_GO
WO019_PROVIDER_CAMPAIGN_RESUME_AUTHORIZED=NO
WO019_OFFLINE_READINESS=AUTHORIZED_NOT_EXECUTED
WO019_V28_BACKUP_RESTORE=NOT_EXECUTED
WO019_NEXT_GATE=OFFLINE_READINESS_REEXECUTION
WO019_EVIDENCE_STATUS=DRAFT
WO019_NETWORK_AUTHORIZED=NO
WO019_GLOBAL_OWNER_GO=NOT_GRANTED
WO019_OWNER_GO_CONSUMED=NO
WO019_INTEGRATION_OR_PRODUCTION_AUTHORIZED=NO
```

Le rejeu hors ligne exécuté après cette autorisation est consigné séparément :

```text
WO019_OFFLINE_READINESS_REEXECUTION=PASS
WO019_OFFLINE_READINESS_REEXECUTED_ON=2026-08-31
WO019_STATUS=READY_FOR_V28_BACKUP_RESTORE
WO019_OFFLINE_READINESS=PASS_REEXECUTED_AFTER_VALIDATED_WO020
WO019_STANDARD_VERIFY=PASS_931_TESTS_0_FAILURE_0_ERROR_4_SKIPPED
WO019_INTEGRATION_VERIFY=PASS_67_TESTS_0_FAILURE_0_ERROR_0_SKIPPED
WO019_VERIFY_LOCAL_COMMAND=powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\Verify-Local.ps1 -WithIntegrationTests
WO019_VERIFY_LOCAL=PASS
WO019_VERIFY_LOCAL_INTEGRATIONS=YES
WO019_VERIFY_LOCAL_SOFASCORE_NETWORK=NO
WO019_DOCKER_COMPOSE_CONFIG_COMMAND=docker compose --env-file .env config --quiet
WO019_DOCKER_COMPOSE_CONFIG=PASS
WO019_LOOPBACK_J3=PASS_14_TESTS_0_FAILURE_0_ERROR_0_SKIPPED
WO019_LOOPBACK_J3_PROVIDER_ACCESS_PERFORMED=NO
WO019_LOOPBACK_J4=PASS_14_TESTS_0_FAILURE_0_ERROR_0_SKIPPED
WO019_LOOPBACK_J4_PROVIDER_ACCESS_PERFORMED=NO
WO019_LOOPBACK_J5=PASS_14_TESTS_0_FAILURE_0_ERROR_0_SKIPPED
WO019_LOOPBACK_J5_PROVIDER_ACCESS_PERFORMED=NO
WO019_PROVIDER_ACCESS_PERFORMED=NO
WO019_PROVIDER_CAMPAIGN_RESUME_AUTHORIZED=NO
WO019_V28_BACKUP_RESTORE=NOT_EXECUTED
WO019_NEXT_GATE=V28_BACKUP_RESTORE
WO019_EVIDENCE_STATUS=DRAFT
WO019_NETWORK_AUTHORIZED=NO
WO019_GLOBAL_OWNER_GO=NOT_GRANTED
WO019_OWNER_GO_CONSUMED=NO
WO019_INTEGRATION_OR_PRODUCTION_AUTHORIZED=NO
```

La preuve V28 qualifiée après le rejeu hors ligne est consignée dans un bloc distinct :

```text
WO019_V28_EXECUTION_ENVIRONMENT=POWERSHELL_7_NATIVE_INTERACTIVE
J6_BACKUP_RESULT=QUALIFIED
J6_BACKUP_CREATED_AT=2026-08-31T06:44:41.9669041Z
J6_BACKUP_QUALIFIED_AT=2026-08-31T06:46:07.7013794Z
J6_BACKUP_CIPHER_SHA256=2b1402d12274f3e9a646aa6134f8cf8bee7a5e11b3249e8dff5c63040cb89d34
J6_BACKUP_MANIFEST_SHA256=7d112e4db804125656a54c61edd8c1eb9417f95e8b7c3d4df88d99d92cde6e62
J6_BACKUP_ARCHIVE_BYTES=6996157
J6_BACKUP_MANIFEST_BYTES=2202
FLYWAY_VERSION=28
J6_BACKUP_COVERAGE_MAX_SNAPSHOT_ID=794
J6_BACKUP_COVERAGE_RECEIVED_AT=2026-08-30T20:30:46.412Z
J6_RESTORE_QUALIFIED=YES
J6_SOURCE_RESTORED_PROPERTY_MISMATCHES=0
J6_RAW_PAYLOAD_INTEGRITY_FAILURES=0
J6_BACKUP_PARTIAL_FILE_COUNT=0
J6_TEMPORARY_RESTORE_DATABASE_RESIDUAL_COUNT_INDEPENDENTLY_VERIFIED=0
CONNECTOR_CONTROL=SAFE
LISTENER_127_0_0_1_8087=FREE
PROVIDER_ACCESS_PERFORMED=NO
PRIMARY_DATABASE_PURGE=NO
WO019_STATUS=READY_FOR_GLOBAL_OWNER_GO
WO019_V28_BACKUP_RESTORE=QUALIFIED
WO019_NEXT_GATE=GLOBAL_OWNER_GO
WO019_PROVIDER_CAMPAIGN_RESUME_AUTHORIZED=NO
WO019_NETWORK_AUTHORIZED=NO
WO019_GLOBAL_OWNER_GO=NOT_GRANTED
WO019_OWNER_GO_CONSUMED=NO
WO019_EVIDENCE_STATUS=DRAFT
WO019_PROVIDER_CALLS=0
WO019_INTEGRATION_OR_PRODUCTION_AUTHORIZED=NO
```

Aucun chemin externe, nom de fichier, manifeste JSON, phrase secrète, dump, payload ou credential
n'est repris dans cette preuve de décision.

Le premier lancement Maven en sandbox n'a pas pu résoudre le parent Spring Boot absent du cache
local (`Permission denied: getsockopt`). La même commande, relancée avec l'accès Maven explicitement
autorisé, a terminé `BUILD SUCCESS`. Ce premier refus d'infrastructure n'est pas un échec de test du
projet et n'a provoqué aucun appel fournisseur.

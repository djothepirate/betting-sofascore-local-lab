# WO-SS-20260831-018 — Décision de gouvernance J9

- **Statut :** `IN_DEVELOPMENT`
- **Date d'ouverture :** 2026-08-31
- **Décision J9 finale :** `PENDING_PROVIDER_ROBUSTNESS_EVIDENCE`
- **Orientation propriétaire :** `PREPARE_OPTIONAL_INTEGRATION`
- **Jalon :** J9 — Décision de gouvernance
- **Base locale :** `40323faa7dca3341da6ef980b5762f1ff5a32a79`
- **Branche :** `codex/j9-decision`
- **Prérequis :** J8 `VALIDATED`, WO-016 et WO-017 clôturés
- **ADR applicable :** `ADR-SS-001 v1.4`
- **ADR de preuve requis :** `ADR-SS-002 v0.1 — PROPOSED, NOT_ACCEPTED`
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
ADR_SS_002_STATUS=PROPOSED_NOT_ACCEPTED
ADR_SS_003_STATUS=NOT_CREATED
```

Cette orientation n'autorise ni code d'intégration, ni endpoint nouveau, ni appel fournisseur, ni
acceptation implicite d'un ADR. La décision J9 finale reste `PENDING` jusqu'à la revue humaine de
la nouvelle preuve et à une confirmation propriétaire distincte.

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
| Coût d'appel | J8 mesure exactement 16 appels de découverte, 4 appels marginaux et 20 appels effectifs par dossier exploitable. La preuve J9 proposée est plafonnée à 38 appels. | Rapport J8 et enveloppe proposée par WO-019 | `PARTIAL` | Un seul dossier ne fournit aucun seuil comparatif ; le coût n'est acceptable que pour un usage manuel et borné. |
| Risque de blocage | Aucun incident fournisseur n'est observé dans la seconde fenêtre J8. | Rapport J8, 20/20 réponses et parsings | `PARTIAL` | L'absence d'incident sur une fenêtre et un dossier ne permet aucune prédiction externe. |
| Exactitude | Aucune source de contrôle externe n'est intégrée au laboratoire. | Rapport J8, `CONTROL_SOURCE_ABSENT` | `NOT_MEASURED` | La vérité externe et les divergences métier restent inconnues. |
| Valeur analytique | Aucun comparateur externe n'est déclaré. | Rapport J8, `EXTERNAL_COMPARISON_ABSENT` | `NOT_MEASURED` | La valeur relative pour le Betting Project n'est pas démontrée. |
| Maintenabilité | Aucun temps d'adaptation à une rupture de schéma ou de restauration hors ligne n'est chronométré. | Rapport J8, `ADAPTATION_TIME_ABSENT` | `NOT_MEASURED` | Aucun engagement de compatibilité ou coût de maintenance durable ne peut être publié. |
| Export et audit | J7 produit un document UTF-8 déterministe limité à 5 Mio, avec exactement `manifest` et `data`, schéma `urn:betting-project:sofascore-local-lab:j7:canonical-event-export:v1`, version `1.0.0`; seul `HUMAN_VALIDATED` est téléchargeable. | Architecture et runbook J7 | `PASS_LOCAL` | Aucun contrat de transfert, accusé distant ou état de livraison n'existe encore. |
| Rétention et purge | J6 applique 30 jours, ne purge que les octets bruts après plan et sauvegarde qualifiée, et ne supprime jamais les observations normalisées. La dernière restauration réelle versionnée a été qualifiée sous Flyway V22, couverture maximale snapshot 272 ; les scripts sont maintenant alignés sur V28. | Runbook J6 et qualification du 2026-08-19 | `PARTIAL` | Une sauvegarde chiffrée fraîche V28 et sa restauration isolée doivent être qualifiées avant toute nouvelle campagne réelle. |
| Indépendance du Betting Project | Le dépôt est actuellement local, expérimental, séparé et sans dépendance critique du projet principal. | ADR-SS-001 et cadrage | `PASS_CURRENT` | Un déploiement Playwright futur sur VPS n'est plus exclu, mais sa faisabilité et son impact sur l'indépendance restent `NOT_MEASURED`; il exigerait une décision séparée et ne pourrait devenir une dépendance critique implicite. |
| Conditions d'utilisation | Les conditions officielles déclarées à jour le 18 septembre 2024 restreignent notamment la charge serveur par requêtes automatisées, l'intégration, l'agrégation, le scraping, la reproduction et l'extraction substantielle sans consentement explicite, avec les réserves légales du texte. Un point d'entrée officiel `Sofascore API` et un contact `Product -> API` existent, mais aucune licence, authentification, limite d'appel ou permission pour les endpoints du laboratoire n'a été extraite. | Revue officielle J9 du 2026-08-31, URLs consignées dans ADR-SS-002 et WO-019 | `PARTIAL — RESTRICTIONS_PRESENT_PERMISSION_NOT_EVIDENCED` | Fait documentaire, sans conclusion juridique. L'acceptation d'ADR-SS-002 doit reconnaître cette incertitude ; une production VPS future exigerait une nouvelle revue et, selon la décision propriétaire, un consentement explicite. |
| Sécurité et exploitation | Playwright est local, manuel et opt-in ; chaque campagne utilise un contexte neuf non persistant, sans profil, cookie réutilisé, `storageState`, HAR, trace, vidéo, capture ou téléchargement. Polling et refresh sont désactivés. | ADR-SS-001 v1.4 et architectures J3/J4/J5 | `PASS_BOUNDED` | Ces contrôles restent obligatoires et ne valent que pour les parcours et volumes explicitement autorisés. |

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
EVIDENCE_STATUS=DRAFT
NETWORK_AUTHORIZED=NO
MAXIMUM_DIRECT_ATTEMPTS=38
```

Elle est bloquée par un nouvel ADR car ADR-SS-001 §9 exige un réexamen lors d'une modification des
bornes de volume. Le précédent métier complet J8 autorisait au plus 30 tentatives pour un dossier ;
la proposition J9 en prévoit au plus 38 pour trois dossiers.

Les quatre portes cumulatives sont :

1. ADR-SS-002 accepté explicitement par le propriétaire ;
2. readiness hors ligne entièrement verte ;
3. sauvegarde chiffrée V28 fraîche et restauration qualifiée sur une base isolée ;
4. go propriétaire global explicite, unique et non consommé.

Avant ces quatre preuves :

```text
PROVIDER_NETWORK=NOT_AUTHORIZED
OWNER_GO=NOT_GRANTED
OWNER_GO_CONSUMED=NO
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
par principe. ADR-SS-003 devra comparer au minimum le push local optionnel ci-dessus et une
topologie VPS Playwright, puis décider séparément droits d'usage, egress, sandbox navigateur,
secrets, certificats, supervision, limites de ressources, disponibilité et indépendance métier.
La campagne résidentielle Windows de WO-019 ne mesurera pas l'accessibilité depuis un VPS.

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

Après qualification et revue humaine de WO-019, le propriétaire recevra le bloc suivant, rempli
avec les faits et la recommandation mais sans confirmation automatique :

```text
J9_DECISION=<ABANDON|KEEP_LOCAL|PREPARE_OPTIONAL_INTEGRATION>
J9_DECIDED_AT_UTC=<timestamp>
J9_EVIDENCE_RESULT=<PASS|PARTIAL_BOUNDED|STOPPED>
J9_EVIDENCE_REFERENCE=<report id and sha256>
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
```

Le premier lancement Maven en sandbox n'a pas pu résoudre le parent Spring Boot absent du cache
local (`Permission denied: getsockopt`). La même commande, relancée avec l'accès Maven explicitement
autorisé, a terminé `BUILD SUCCESS`. Ce premier refus d'infrastructure n'est pas un échec de test du
projet et n'a provoqué aucun appel fournisseur.

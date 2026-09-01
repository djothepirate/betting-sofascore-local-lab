# ADR-SS-003 — Topologie d’intégration optionnelle du SofaScore Local Lab

- **Statut :** v0.1 `ACCEPTED` — direction d’architecture sélectionnée, aucun effet
  d’implémentation ou de réseau
- **Version acceptée :** 0.1
- **Date de proposition :** 2026-09-01
- **Date d’acceptation :** `2026-09-01T07:25:25.4809414Z`
  (`2026-09-01T09:25:25.4809414+02:00` en Europe/Paris)
- **Décideur :** Porteur du Betting Project — `ACCEPT`
- **Draft accepté :** commit `ca789a3a40ea5fc6c16312bd73f675bc9fd32650`, SHA-256 du
  fichier `0edcc1e7db2ffc268d1560342d8c2f7c8ea9e1f91c65148e0504c1217f5982be`
- **Topologie sélectionnée :** `OPTIONAL_LOCAL_PUSH`
- **Work Order :** `WO-SS-20260901-026-optional-integration-feasibility`
- **Branche :** `codex/j9-optional-integration-study`
- **Base J9 publiée :** `1a58a3bd7673f5946d5c48ae573c191e52d223a2` sur
  `origin/codex/j9-decision`
- **Décision J9 :** `PREPARE_OPTIONAL_INTEGRATION`
- **Preuve J9 :** WO-023 `PASS`, rapport SHA-256
  `1c6a97f872d5621efcaffa505d16a7724f81816891aa0a9a990a0abec56e87c1`
- **Permission officielle :** `NOT_EVIDENCED`
- **ADR de gouvernance fournisseur :** ADR-SS-001 v1.4, inchangée
- **ADR de campagne J9 :** ADR-SS-002 v1.1 `ACCEPTED`, portée de campagne achevée et go consommé ;
  ADR inchangée
- **Changement runtime :** aucun
- **Nouvel endpoint ou URI :** aucun
- **Réseau fournisseur :** non autorisé
- **Intégration ou production :** non autorisée

## 1. Contexte

J9 a conclu `PREPARE_OPTIONAL_INTEGRATION` après validation propriétaire de la campagne WO-023 avec
un résultat `PASS`. La preuve démontre une acquisition manuelle, bornée et reproductible sur un
poste Windows résidentiel. Elle ne démontre ni une permission officielle d’usage, ni une
accessibilité depuis un VPS, ni un contrat de livraison au Betting Project.

J7 fournit déjà un export canonique versionné, minimisé par contrat, limité à 5 Mio et téléchargeable
seulement pour une décision `HUMAN_VALIDATED`. ADR-SS-001 §3.9 prévoit qu’une première intégration
éventuelle repose sur cet export, puis qu’une phase ultérieure puisse autoriser un push HTTPS
sortant depuis Windows vers un point d’import dédié.

Le propriétaire n’exclut plus une topologie Playwright VPS à l’avenir. Cette ouverture ne modifie
cependant pas les règles présentes : le dépôt reste `LOCAL_ONLY` et `NOT_PRODUCTION_APPROVED`,
Playwright reste local, manuel et opt-in, et ADR-SS-001 maintient que le VPS ne contacte pas
directement SofaScore.

## 2. Problème à résoudre

Choisir une direction d’architecture pour étudier une intégration facultative, sans :

- transformer une preuve locale en autorisation de production ;
- extrapoler la connectivité résidentielle Windows à une adresse VPS ;
- envoyer ou exposer un payload fournisseur brut sur le VPS sans décision explicite ;
- permettre qu’une livraison déclenche une acquisition ;
- coupler le Betting Project à SofaScore, au laboratoire ou au poste Windows ;
- créer un endpoint, un retry, un scheduler, un polling ou un secret par la seule acceptation de
  cette ADR ;
- interpréter une faisabilité technique comme un droit d’usage.

## 3. Forces de décision

1. **Permission :** aucune permission officielle applicable n’est aujourd’hui établie.
2. **Compatibilité :** la direction locale vers un export/push est déjà prévue par ADR-SS-001 ;
   l’appel direct fournisseur depuis le VPS y est rejeté.
3. **Minimisation :** seuls les exports J7 utiles, normalisés, tracés et validés peuvent franchir la
   frontière ; les payloads bruts restent locaux.
4. **Indépendance :** le Betting Project doit fonctionner sans le laboratoire et sans SofaScore.
5. **Réversibilité :** toute livraison doit être désactivable sans modifier l’acquisition ou le cœur.
6. **Sécurité :** aucune entrée vers Windows ; identité forte, secrets confinés et états de
   livraison auditables pour un futur push.
7. **Preuve :** WO-023 qualifie Windows seulement ; les propriétés VPS restent `NOT_MEASURED`.
8. **Exploitation :** aucune promesse de fraîcheur, disponibilité, live, polling ou SLA n’existe.

## 4. Invariants non modifiés

```text
EXPERIMENTAL
LOCAL_ONLY
NOT_PRODUCTION_APPROVED
NO_CRITICAL_DEPENDENCY
SERVER_ADDRESS=127.0.0.1
PROVIDER_ACQUISITION_MODE=MANUAL_ON_DEMAND
PLAYWRIGHT_CONTEXT=PERSISTENCE_FORBIDDEN
RAW_PROVIDER_PAYLOAD_TO_VPS=FORBIDDEN
DELIVERY_MUST_NOT_TRIGGER_PROVIDER_ACQUISITION=YES
PROVIDER_NETWORK_AUTHORIZED=NO
INTEGRATION_IMPLEMENTATION_AUTHORIZED=NO
VPS_DEPLOYMENT_AUTHORIZED=NO
PRODUCTION_AUTHORIZED=NO
```

L’acceptation ne modifie ni ADR-SS-001, ni ADR-SS-002, ni `AGENTS.md`. Une option incompatible
avec ces invariants exige une décision et une révision distinctes avant toute expérimentation.

## 5. Options étudiées

### 5.1 Option A — `OPTIONAL_LOCAL_PUSH`

1. Playwright reste exécuté sur Windows, manuellement, à la demande et avec un contexte neuf.
2. L’acquisition, la normalisation, la validation humaine et le commit local précèdent la livraison.
3. Seul un export J7 minimisé et `HUMAN_VALIDATED` est éligible.
4. Un futur client effectue un push HTTPS sortant vers un point d’import dédié du Betting Project.
5. L’identité et l’état de livraison sont distincts de l’identité et de l’état de validation locale.
6. Le Betting Project accuse ou refuse la livraison sans jamais déclencher d’acquisition SofaScore.
7. L’arrêt du poste ou du laboratoire n’affecte aucune fonction permanente du Betting Project.

La décision acceptée fixe les choix d’architecture suivants : livraison v1 manuelle après
`HUMAN_VALIDATED`, `POST` HTTPS vers un point d’import dédié, réception idempotente sous une clé
stable dérivée de l’identité et du hash de l’export, accusé synchrone minimisé, zéro retry
automatique, ledger de livraison séparé, et mTLS obligatoire avec clé privée client non exportable
dans le magasin de certificats de l’utilisateur Windows. L’URI, les valeurs numériques de timeout,
le profil de certificat et les noms exacts du schéma d’accusé restent à qualifier dans les futurs
Work Orders ; ils ne sont pas laissés libres de contredire ces choix.

### 5.2 Option B — `VPS_PLAYWRIGHT`

Playwright et l’acquisition fournisseur sont exécutés sur une infrastructure VPS. Cette option
pourrait réduire la dépendance opérationnelle à un poste utilisateur, mais ce bénéfice est
actuellement une hypothèse et non une mesure.

Trois placements distincts devraient être comparés si l’option devenait recevable :

```text
B1=PLAYWRIGHT_COLOCATED_ON_BETTING_PROJECT_PRODUCTION_VPS
B2=PLAYWRIGHT_ON_DEDICATED_ISOLATED_VPS
B3=PLAYWRIGHT_IN_ISOLATED_CONTAINER_ON_SHARED_HOST
```

Ils n’ont ni le même rayon d’impact, ni la même frontière de secrets, ni les mêmes ressources. Aucune
variante ne peut être assimilée silencieusement à une autre.

Sous la gouvernance courante, l’option est `BLOCKED_BY_CURRENT_GOVERNANCE` :

- `LOCAL_ONLY` et la règle Playwright locale seraient contredits ;
- ADR-SS-001 §4 maintient que le VPS ne contacte pas les endpoints SofaScore ;
- ADR-SS-001 §6.2 rejette l’intégration directe fournisseur sur VPS ;
- les octets fournisseur bruts seraient reçus et traités sur le VPS alors que la frontière courante
  les maintient localement.

Elle exige donc, avant même un prototype connecté, une décision distincte de réexamen
d’ADR-SS-001 et des règles du dépôt, une résolution de la frontière brute, une permission
officielle établie et un Work Order de preuve VPS dédié. L’acceptation d’ADR-SS-003 v0.1 ne suffit
pas.

### 5.3 Option C — `KEEP_LOCAL_NO_INTEGRATION`

Les exports restent dans le laboratoire. Cette option ne satisfait pas l’objectif d’intégration,
mais conserve la gouvernance actuelle et constitue le repli si une porte de permission, sécurité,
contrat ou indépendance échoue.

## 6. Comparaison factuelle

| Dimension | `OPTIONAL_LOCAL_PUSH` | `VPS_PLAYWRIGHT` | Qualification actuelle |
|---|---|---|---|
| Permission officielle | Non établie | Non établie | `NOT_EVIDENCED` pour les deux ; porte dure, sans conclusion juridique |
| Compatibilité avec ADR-SS-001 | Prévu comme phase ultérieure au §3.9 | Appel direct VPS maintenu hors décision aux §§4 et 6.2 | A `COMPATIBLE_FOR_FUTURE_WORK_ORDER_UNDER_CURRENT_GOVERNANCE` ; B `BLOCKED_BY_CURRENT_GOVERNANCE` |
| Preuve d’accès | WO-023 `PASS` depuis Windows résidentiel | Aucune preuve depuis IP VPS | A `PASS_LOCAL_BOUNDED` ; B `NOT_MEASURED` |
| Données transférées | Export J7 normalisé, minimisé, tracé et validé seulement | Réponses brutes nécessairement reçues sur le VPS | A `PARTIAL_DESIGN` ; B bloqué tant que la frontière brute n’est pas redécidée |
| Réseau | Push HTTPS sortant, aucune entrée Windows | Egress fournisseur et administration VPS à modéliser | A contrat architectural fixé, endpoint absent ; B `NOT_MEASURED` |
| Navigateur | Fermeture, délai et contexte éphémère qualifiés sur Windows | Sandbox, processus et cleanup Linux/VPS non qualifiés | A `PASS_LOCAL_BOUNDED` ; B `NOT_MEASURED` |
| Secrets et certificats | mTLS et clé Windows non exportable imposés | Stockage, injection et rotation VPS inconnus | Choix A fixé, mécanismes `NOT_IMPLEMENTED / NOT_MEASURED` |
| Livraison | Déclencheur manuel, `POST`, idempotence, accusé, états, zéro retry automatique et erreurs définis ; endpoint à construire | Acquisition co-localisée, mais ingestion toujours à définir | Contrat A fixé au niveau architecture, runtime `NOT_IMPLEMENTED` |
| Audit | Export J7 byte-identique et hashes qualifiés | Audit distant non conçu | A `PASS_LOCAL` pour l’export, livraison `NOT_MEASURED` ; B `NOT_MEASURED` |
| Rétention, purge et restauration | Les données brutes restent sous J6/V28 local ; cycle de vie de la copie J7 chez le receiver non défini | Portage J6, rétention, purge, chiffrement et restauration VPS non qualifiés | A `PASS_LOCAL` pour le brut et `NOT_DEFINED` côté receiver ; B `NOT_MEASURED` |
| Exploitation | Geste manuel ponctuel, sans promesse continue | Supervision, patch, restart, alerting et astreinte requis | A limite connue ; B `NOT_MEASURED` |
| Disponibilité | Poste éteint sans effet sur le Betting Project ; données moins fraîches possibles | Disponibilité théorique potentiellement supérieure | Aucun SLA ; bénéfice B `NOT_MEASURED` |
| Coût | Sender, receiver, mTLS et exploitation inconnus | Hébergement et exploitation navigateur inconnus | `NOT_MEASURED` comparativement |
| Maintenabilité | Réutilise J7 et la séparation actuelle | Ajoute runtime et surface d’exploitation | A `PARTIAL_DESIGN` ; B `NOT_MEASURED` |
| Exactitude/valeur | Inchangées par le transport | Inchangées par le lieu | `NOT_MEASURED` comme bénéfice métier |
| Indépendance | Compatible si ingestion facultative et désactivable | Risque de service permanent couplé à maîtriser | `NO_CRITICAL_DEPENDENCY` reste une porte obligatoire |
| Rollback | Désactiver la livraison sans toucher à l’acquisition | Décommissionnement et purge distante à concevoir | A direction réversible ; B `NOT_MEASURED` |

Aucun score n’est calculé. La différence déterminante est normative : A est la seule option qui
peut être étudiée pour implémentation sans d’abord réviser la gouvernance ; B ne dispose ni de cette
compatibilité, ni d’une preuve d’environnement.

## 7. Décision acceptée v0.1

```text
ADR_SS_003_STATUS=ACCEPTED
ADR_SS_003_VERSION=0.1
ADR_SS_003_OWNER_DECISION=ACCEPT
ADR_SS_003_ACCEPTED_DRAFT_COMMIT=ca789a3a40ea5fc6c16312bd73f675bc9fd32650
ADR_SS_003_ACCEPTED_DRAFT_FILE_SHA256=0edcc1e7db2ffc268d1560342d8c2f7c8ea9e1f91c65148e0504c1217f5982be
ADR_SS_003_ACCEPTED_AT_UTC=2026-09-01T07:25:25.4809414Z
ADR_SS_003_ACCEPTED_AT_EUROPE_PARIS=2026-09-01T09:25:25.4809414+02:00
PROPOSED_PRIMARY_TOPOLOGY=OPTIONAL_LOCAL_PUSH
ADR_SS_003_SELECTED_TOPOLOGY=OPTIONAL_LOCAL_PUSH
SELECTED_TOPOLOGY_EFFECT=FUTURE_IMPLEMENTATION_WORK_ORDER_ONLY
VPS_PLAYWRIGHT_STATUS=DEFERRED_BLOCKED_BY_CURRENT_GOVERNANCE
FALLBACK_TOPOLOGY=KEEP_LOCAL_NO_INTEGRATION
ADR_OWNER_CONFIRMATION_RECEIVED=YES
ADR_ACCEPTANCE_AUTHORIZES_IMPLEMENTATION=NO
ADR_ACCEPTANCE_AUTHORIZES_PROVIDER_NETWORK=NO
ADR_ACCEPTANCE_AUTHORIZES_VPS_DEPLOYMENT=NO
ADR_ACCEPTANCE_AUTHORIZES_PRODUCTION=NO
```

Le propriétaire sélectionne `OPTIONAL_LOCAL_PUSH` comme seule direction candidate sous les règles
actuelles. La décision fixe le contrat architectural de la section 8, mais ne crée aucune URI,
aucun certificat et aucune valeur de timeout et ne permet aucune mutation. `VPS_PLAYWRIGHT` reste une
alternative architecturale différée qui devra faire l’objet d’un réexamen de gouvernance et de
preuves propres.

## 8. Contrat minimal d’une future option A

Tout futur Work Order d’implémentation devra rendre les propriétés suivantes exécutoires et
testables :

1. **Éligibilité et déclencheur :** export J7 versionné, minimisé, sous 5 Mio, lié à une décision
   locale `HUMAN_VALIDATED` et à son hash ; livraison v1 uniquement après une nouvelle action
   manuelle explicite. Un déclencheur événementiel exigerait une révision de l’ADR.
2. **Séparation :** l’état de validation locale ne change jamais en fonction de la livraison ; un
   état de livraison séparé porte tentative, résultat, horodatage et accusé.
3. **Direction :** connexion HTTPS sortante seulement ; aucun listener non loopback dans le lab.
4. **HTTP et identité :** `POST` HTTPS vers un endpoint d’import dédié, identité de livraison stable
   et clé d’idempotence formée de `exportId` et du SHA-256 du fichier. Une répétition autorisée
   réemploie exactement l’artefact et la même clé.
5. **Accusé :** réponse synchrone minimisée portant au moins l’identité d’import distante, le statut,
   les identités et hashes reçus et l’heure de réception ; jamais le document complet.
6. **États :** `NOT_ATTEMPTED`, `IN_FLIGHT`, `DELIVERED`, `DUPLICATE_CONFIRMED`,
   `REJECTED_TERMINAL` ou `UNKNOWN_RECONCILIATION_REQUIRED`, dans un ledger distinct de la
   validation locale.
7. **Erreurs et reprise :** aucun retry automatique. Un refus 4xx est terminal, sauf doublon exact
   explicitement accusé. Un timeout, une rupture TLS, une erreur de transport ou un 5xx devient
   `UNKNOWN_RECONCILIATION_REQUIRED`; une nouvelle tentative exige une action opérateur et la même
   clé idempotente après vérification du receiver.
8. **Sécurité :** mTLS obligatoire, clé privée client non exportable dans le magasin de certificats
   de l’utilisateur Windows, validation du serveur, chevauchement contrôlé pendant rotation,
   révocation et journalisation sans secret.
9. **Données :** aucun payload brut, cookie, jeton, certificat privé, chemin sensible ou état de
   session ; uniquement les champs J7 approuvés.
10. **Découplage :** la réception ne peut appeler le fournisseur ; l’indisponibilité de toute partie
   ne bloque aucune fonction critique.
11. **Borniers :** timeout fini et configurable, taille maximale 5 Mio et concurrence `1`; les
    valeurs numériques de timeout sont qualifiées avant réseau.
12. **Supervision :** ledger local et audit receiver minimisés, statut visible par l’opérateur,
    aucune boucle de polling, scheduler ou alerte contenant le document.
13. **Audit :** hash avant/après, version du contrat, identité de livraison et résultat reproductible.
14. **Désactivation :** kill switch local, rollback et absence d’effet sur les données validées.

## 9. Conditions préalables obligatoires

### 9.1 Portes satisfaites lors de l’acceptation

- décision propriétaire explicite sur ADR-SS-003 v0.1 ;
- reconnaissance de `J9_OFFICIAL_PERMISSION_STATUS=NOT_EVIDENCED` ;
- reconnaissance que l’acceptation autorise uniquement un futur Work Order ;
- maintien explicite de `NO_CRITICAL_DEPENDENCY` et du repli local.

### 9.2 Pour implémenter l’option A

- permission, licence ou base d’usage applicable suffisamment établie après une revue qualifiée ;
  une décision propriétaire interne ne crée aucun droit et ne se substitue jamais à cette porte ;
- Work Order d’implémentation distinct ;
- contrat d’import du Betting Project et modèle de menace approuvés ;
- valeurs et profils d’implémentation conformes aux choix d’idempotence, accusé, reprise zéro
  automatique, états séparés et mTLS imposés par la section 8 ;
- tests loopback/offline, puis toute qualification réseau autorisée séparément ;
- aucun couplage entre livraison et acquisition fournisseur.

### 9.3 Pour rendre l’option B étudiable par exécution

- décision propriétaire distincte autorisant un réexamen de `LOCAL_ONLY`, d’ADR-SS-001 et des
  règles Playwright locales ;
- décision explicite sur la présence et le traitement de données brutes fournisseur sur le VPS ;
- permission officielle établie ;
- modèle de menace, hébergeur, egress, exposition, sandbox, identité, secrets, limites de ressources,
  supervision, sauvegarde, rotation, cleanup et astreinte documentés ;
- Work Order, manifeste, plafond et go propres à une preuve VPS ;
- aucune extrapolation du résultat WO-023.

## 10. Conséquences de la décision

### 10.1 Positives

- réutilise l’export J7 et la séparation déjà qualifiée ;
- maintient le fournisseur hors du VPS et du cœur du Betting Project ;
- conserve un geste fournisseur manuel, distinct et auditable ;
- offre un chemin de livraison désactivable et remplaçable ;
- évite de faire passer une hypothèse VPS pour une preuve ;
- garde ouverte une étude VPS future sous une gouvernance explicite.

### 10.2 Négatives et coûts

- dépend d’un geste et d’un poste local pour obtenir des données nouvelles ;
- n’offre aucune fraîcheur ou disponibilité continue ;
- nécessite un nouveau contrat HTTPS et une nouvelle surface mTLS ;
- laisse la permission officielle non résolue ;
- reporte toute preuve VPS et peut exiger une révision plus large de gouvernance ;
- ajoute un ledger et les six états de livraison définis à implémenter, sans pouvoir réutiliser
  l’état de validation.

### 10.3 Risques à fermer

- transmission accidentelle d’un champ brut ou sensible ;
- confusion entre validation et livraison ;
- doublon ou perte sans idempotence et accusé ;
- retry caché, boucle ou couplage d’acquisition ;
- compromission ou expiration de certificat ;
- dépendance implicite du Betting Project à la fraîcheur SofaScore ;
- dérive d’une étude VPS vers une production non autorisée ;
- interprétation abusive d’une réussite technique comme permission.

## 11. Disqualifiants structurels

La direction sélectionnée doit revenir à `KEEP_LOCAL_NO_INTEGRATION` si elle exige :

- proxy rotatif, furtivité, résolution de challenge, cookie/session réutilisé ou fallback fournisseur ;
- exposition publique du laboratoire ou d’une interface Playwright ;
- transfert d’un payload brut, cookie, jeton ou secret vers le cœur ;
- impossibilité d’obtenir minimisation, audit, idempotence, rétention ou purge sûres ;
- dépendance critique ou attente synchrone inévitable ;
- déclenchement d’une acquisition par la livraison ;
- permission officielle incompatible ou consentement explicitement requis mais non obtenu.

Une future variante VPS est irrecevable si Chromium doit fonctionner de manière privilégiée sans
isolation acceptable ou si son arrêt ne peut garantir le nettoyage fail-closed.

## 12. Choix fixés et paramètres restant à définir

```text
IMPORT_ENDPOINT_URI=NOT_DEFINED
DELIVERY_HTTP_METHOD=POST
PAYLOAD_SCHEMA_VERSION=J7_CANONICAL_EXPORT_V1
DELIVERY_PROTOCOL_VERSION=NOT_DEFINED
DELIVERY_TRIGGER=MANUAL_ON_DEMAND_AFTER_HUMAN_VALIDATED
DELIVERY_STATE_MODEL=SEPARATE_LEDGER_WITH_SIX_DEFINED_STATES
IDEMPOTENCY_CONTRACT=EXPORT_ID_PLUS_FILE_SHA256
ACKNOWLEDGEMENT_CONTRACT=SYNCHRONOUS_MINIMIZED_ID_STATUS_HASHES_RECEIVED_AT
HTTP_TIMEOUTS=FINITE_EXACT_VALUES_NOT_DEFINED
DELIVERY_CONCURRENCY=1
RETRY_POLICY=ZERO_AUTOMATIC_MANUAL_SAME_ARTIFACT_AND_KEY_ONLY
DELIVERY_AUTHENTICATION=MTLS_REQUIRED
CLIENT_PRIVATE_KEY=WINDOWS_USER_CERTIFICATE_STORE_NON_EXPORTABLE
MTLS_CERTIFICATE_PROFILE=EXACT_PROFILE_NOT_DEFINED
CERTIFICATE_ROTATION=CONTROLLED_OVERLAP_REVOCATION_AND_AUDIT_REQUIRED
OBSERVABILITY=LOCAL_AND_RECEIVER_MINIMIZED_DELIVERY_LEDGERS
POLLING_OR_SCHEDULER=FORBIDDEN
VPS_PLACEMENT=B1_B2_B3_NOT_SELECTED
VPS_PROVIDER=NOT_SELECTED
VPS_REGION_OR_IP=NOT_SELECTED
VPS_RUNTIME_OR_IMAGE=NOT_SELECTED
SLA_OR_SLO=NOT_DEFINED
PROVIDER_PERMISSION=NOT_EVIDENCED
```

## 13. Décomposition des futurs travaux

Si l’option A est acceptée comme direction :

1. Work Order du contrat et du receiver dans le dépôt du Betting Project ;
2. Work Order du sender et de l’état de livraison dans le laboratoire ;
3. qualification loopback ou staging avec export synthétique, sans fournisseur ;
4. qualification mTLS, idempotence, accusés, erreurs et reprise ;
5. autorisation séparée d’une première livraison réelle d’un export déjà `HUMAN_VALIDATED`.

Si l’option B redevient candidate :

1. décision ou amendement explicite de gouvernance ;
2. qualification hors ligne du packaging VPS et de Chromium ;
3. qualification sauvegarde, restauration, rétention et purge VPS ;
4. revue de sécurité, de ressources et d’exploitation ;
5. Work Order, manifeste, plafond et go spécifiques pour toute tentative fournisseur depuis le VPS ;
6. décision de production distincte après preuve.

Aucun de ces Work Orders n’est implicitement ouvert par l’acceptation du présent document.

## 14. Bloc de décision propriétaire enregistré

Le bloc ci-dessous est reproduit tel que reçu. Sa dernière ligne exprimait la porte de confirmation
au moment de la soumission ; le message propriétaire qui le contient satisfait cette porte pour
ADR-SS-003.

```text
ADR_SS_003_OWNER_DECISION=ACCEPT
ADR_SS_003_VERSION=0.1
ADR_SS_003_COMMIT=ca789a3a40ea5fc6c16312bd73f675bc9fd32650
ADR_SS_003_FILE_SHA256=0edcc1e7db2ffc268d1560342d8c2f7c8ea9e1f91c65148e0504c1217f5982be
WORK_ORDER=WO-SS-20260901-026-optional-integration-feasibility
PROPOSED_PRIMARY_TOPOLOGY=OPTIONAL_LOCAL_PUSH
ADR_SS_003_SELECTED_TOPOLOGY=OPTIONAL_LOCAL_PUSH
VPS_PLAYWRIGHT_STATUS=DEFERRED_BLOCKED_BY_CURRENT_GOVERNANCE
ADR_SS_001_EFFECT=UNCHANGED_UNDER_PROPOSAL
J9_OFFICIAL_PERMISSION_STATUS=NOT_EVIDENCED
ADR_ACCEPTANCE_AUTHORIZES_IMPLEMENTATION=NO
INTEGRATION_IMPLEMENTATION_AUTHORIZED=NO
BETTING_PROJECT_RECEIVER_IMPLEMENTATION_AUTHORIZED=NO
PROVIDER_NETWORK_AUTHORIZED=NO
LIVE_DELIVERY_AUTHORIZED=NO
VPS_DEPLOYMENT_AUTHORIZED=NO
PRODUCTION_AUTHORIZED=NO
FUTURE_IMPLEMENTATION_WORK_ORDER_REQUIRED=YES
FUTURE_VPS_GOVERNANCE_DECISION_REQUIRED=YES
OWNER_CONFIRMATION_REQUIRED=YES
```

État dérivé après réception, sans étendre la décision :

```text
OWNER_CONFIRMATION_RECEIVED=YES
ADR_OWNER_CONFIRMATION_OUTSTANDING=NO
WORK_ORDER_STATUS=VALIDATED
CLOSURE_BASIS=ADR_SS_003_V0_1_OWNER_ACCEPTANCE_SATISFIES_WO026_OBJECTIVE
MOVE_TO_COMPLETED_PERFORMED=YES
```

## 15. Déclencheurs de réexamen

Réexaminer ADR-SS-003 avant toute mutation si :

- une permission officielle, licence ou restriction nouvelle est obtenue ;
- le format J7, la limite de taille ou le statut `HUMAN_VALIDATED` change ;
- un endpoint, transport, retry, scheduler, polling ou mécanisme live est demandé ;
- le Betting Project risque de dépendre d’une livraison ou d’une fraîcheur fournisseur ;
- la topologie VPS redevient candidate à une preuve exécutoire ;
- ADR-SS-001, `AGENTS.md` ou la frontière des payloads bruts change ;
- des exigences de production, disponibilité, volumétrie ou astreinte apparaissent ;
- mTLS à clé non exportable ne peut pas satisfaire le contrat ;
- un payload sensible ou une donnée brute franchirait la frontière.

## 16. Effet de l’acceptation

L’acceptation propriétaire de v0.1 vaut uniquement sélection d’une direction pour un futur Work
Order. Elle n’autorise pas :

- la création d’un endpoint ou d’une URI ;
- la modification du Betting Project ;
- l’envoi d’un export ;
- un accès fournisseur ;
- un déploiement VPS ;
- une production, un polling, un live, un scheduler, un retry ou un fallback ;
- la modification d’ADR-SS-001, ADR-SS-002 ou des invariants du dépôt.

## 17. Historique

| Version | Date | Statut | Évolution |
|---|---|---|---|
| 0.1 | 2026-09-01 | `PROPOSED_NOT_ACCEPTED` | Proposition issue de J9 : option A comme direction d’un futur Work Order, option B différée et bloquée par la gouvernance actuelle, option C comme repli ; aucun effet runtime ou réseau |
| 0.1 | 2026-09-01 | `ACCEPTED` | Draft immuable `ca789a3a…` / `0edcc1e7…` accepté à `07:25:25.4809414Z` ; `OPTIONAL_LOCAL_PUSH` sélectionné, option VPS différée, toutes les autorisations d’implémentation, de réseau, de livraison, de déploiement et de production maintenues à `NO` |

## 18. Références

1. [Décision J9 WO-018](docs/work_orders/completed/WO-SS-20260831-018-decision-j9.md).
2. [Preuve J9 WO-023](docs/work_orders/completed/WO-SS-20260831-023-j9-provider-robustness-v11.md).
3. [Rapport de campagne WO-023](docs/validation/J9-WO023-PROVIDER-ROBUSTNESS-CAMPAIGN-20260901.md).
4. [ADR-SS-001](ADR-SS-001-experimentation-endpoints-sofascore-depuis-windows.md), notamment §§3.9,
   4 et 6.2.
5. [ADR-SS-002](ADR-SS-002-bounded-multi-dossier-provider-robustness.md).
6. [Architecture J7](docs/architecture/J7-CANONICAL-EVENT-EXPORT.md).
7. [Transport Playwright J3](docs/architecture/J3-PLAYWRIGHT-PROVIDER-TRANSPORT.md).
8. [Runbook J6](docs/runbooks/J6-BACKUP-RESTORE-AND-RETENTION.md).
9. [Architecture générale](docs/architecture/ARCHITECTURE.md).
10. [Règles du dépôt](AGENTS.md).

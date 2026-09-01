# WO-SS-20260901-029 — Durcissement de la borne de port du push local optionnel

- **Statut :** `IN_PROGRESS`
- **Jalon :** après J9 — correctif de revue de la PR #21
- **Ouvert le :** 2026-09-01
- **Ouverture UTC :** `2026-09-01T12:17:44.3637202Z`
- **Ouverture Europe/Paris :** `2026-09-01T14:17:44.3670919+02:00`
- **Branche :** `codex/j9-wo029-port-boundary-hardening`
- **Worktree :** `.tmp/j9-wo029-port-boundary-hardening`
- **Base exacte :** `36598f1979ddc7be7081431b147691e7e9b8e49d`
- **PR corrigée :** `#21`, branche `codex/j9-optional-local-push-implementation`
- **Constat de revue :** P2 « Reject out-of-range ports before claiming a delivery »,
  discussion `discussion_r3903674609`
- **Work Order parent :**
  `WO-SS-20260901-027-optional-local-push-implementation` — `VALIDATED`
- **ADR applicable :** `ADR-SS-003 v0.1` — `ACCEPTED`
- **Type de lot :** correctif fail-closed local, tests offline/loopback et documentation ; aucun
  réseau réel

## 1. Autorisation et séquence de fusion

Le propriétaire a autorisé la préparation des correctifs des PR #20 et #21, avec l'ordre strict
suivant : corriger, résoudre et fusionner la PR #20 avant de commencer le correctif de la PR #21,
puis corriger, résoudre et fusionner la PR #21. WO-029 n'autorise donc son intégration qu'après la
fusion effective de la PR #20 et après qualification verte du présent lot.

Cette autorisation porte sur un append correctif préservant les commits qualifiés. Elle n'autorise
ni squash, ni rebase, ni force-push, ni réécriture des preuves WO-027.

```text
WORK_ORDER=WO-SS-20260901-029-j9-optional-local-push-port-boundary-hardening
WORK_ORDER_STATUS=IN_PROGRESS
BASE_COMMIT=36598f1979ddc7be7081431b147691e7e9b8e49d
TARGET_PULL_REQUEST=21
PR20_MUST_BE_MERGED_FIRST=YES
PRESERVE_QUALIFIED_COMMIT_HASHES=YES
SQUASH_OR_REBASE_AUTHORIZED=NO
FORCE_PUSH_AUTHORIZED=NO

J9_OFFICIAL_PERMISSION_STATUS=NOT_EVIDENCED
REAL_RECEIVER_NETWORK_AUTHORIZED=NO
REAL_DELIVERY_AUTHORIZED=NO
PROVIDER_NETWORK_AUTHORIZED=NO
VPS_DEPLOYMENT_AUTHORIZED=NO
PRODUCTION_AUTHORIZED=NO
```

## 2. Anomalie factuelle

Les deux gardes locales de la base WO-027 vérifient actuellement qu'un port explicite est positif,
mais ne vérifient pas sa borne supérieure :

- `OptionalLocalPushProperties.isLoopbackQualificationSafe()` accepte structurellement un port
  supérieur à `65535` ;
- `BettingProjectJ7DeliveryHttpTransport.requireExactHttpsIpv4LoopbackOrigin()` applique la même
  validation incomplète.

Une URI telle que `https://127.0.0.1:65536` peut ainsi franchir ces deux gardes. Le service persiste
le claim `IN_FLIGHT` avant l'appel au transport ; le client HTTP de Java peut ensuite rejeter le
port hors plage par une `IllegalArgumentException` non classifiée par le contrat de transport. La
livraison non envoyée peut alors rester `IN_FLIGHT` jusqu'à une réconciliation stale manuelle.

Le risque n'est pas un accès réseau autorisé : il s'agit d'un défaut de validation fail-closed et
d'auditabilité avant claim. Aucun appel réel n'est nécessaire pour le reproduire ou le corriger.

## 3. Objectif et périmètre

Le lot doit :

1. imposer un port explicite compris entre `1` et `65535` inclus dans la configuration loopback ;
2. faire échouer une configuration hors plage avant lecture de l'export, avant création d'identité,
   avant claim et avant socket ;
3. répéter la même validation dans le transport, avant la construction du client possédé ou toute
   utilisation d’un client injecté, afin de ne pas dépendre du seul appelant ;
4. prouver les bornes `1`, `65535` et `65536`, ainsi que l'absence de port explicite, sans tenter de
   joindre les ports limites ;
5. prouver qu'une origine hors plage ne crée ni identité, ni tentative, ni projection
   `IN_FLIGHT`, et ne touche pas l'export J7 ;
6. convertir toute configuration invalide en refus local déterministe, sans laisser
   d'`IllegalArgumentException` du client HTTP après claim.

Sont hors périmètre : migration SQL, modification du schéma J7, contrat receiver, endpoint, route,
transport alternatif, retry, fallback, polling, scheduler, activation runtime, permission
officielle, receiver réel, réseau fournisseur, VPS et production.

## 4. Invariants obligatoires

| Invariant | Exigence WO-029 |
|---|---|
| Borne du port | origine explicite dans l'intervalle fermé `[1, 65535]` uniquement |
| Première barrière | propriétés/politique refusent hors plage avant export et avant claim |
| Défense en profondeur | transport revérifie la borne avant client HTTP ou socket |
| Ledger | zéro identité, tentative, résultat ou `IN_FLIGHT` sur refus de configuration |
| Export J7 | aucun octet lu ou modifié ; statut `HUMAN_VALIDATED` inchangé |
| Classification | aucune exception HTTP Java hors contrat après claim |
| Réseau | zéro appel SofaScore, Betting Project réel ou VPS |
| Cible de test | offline ou loopback synthétique `127.0.0.1` uniquement |
| Retry/fallback | aucun ajout ; politique WO-027 inchangée |
| Activation | sender, cible réelle et livraison restent désactivés par défaut |
| Permission | `J9_OFFICIAL_PERMISSION_STATUS=NOT_EVIDENCED` inchangé |

## 5. Baseline qualifiée à préserver

Le correctif part du commit de clôture WO-027
`36598f1979ddc7be7081431b147691e7e9b8e49d`. Il doit préserver l'historique qualifié, notamment :

```text
WO027_QUALIFIED_COMMIT=5af48e5ea0e7150b460fe106da74aa5d3bd5489a
WO027_QUALIFICATION_RESULT=PASS_LOCAL_FAIL_CLOSED
WO027_QUALIFICATION_REPORT_SHA256=d656b7ea12ba38a40b88e9a78e5b7afb9642c4405080b9246b8539d177eb1d12
WO027_COMPLETED_WORK_ORDER_SHA256=be18d440edf33ae8c511c4b1bab5ad1e70f6c2167027d525769c98417ebf5c2c
WO027_SUREFIRE_BASELINE=1036/0/0/5
WO027_FAILSAFE_BASELINE=84/0/0/0
WO027_PROVIDER_CALLS=0
WO027_REAL_RECEIVER_CALLS=0
WO027_RESIDUAL_LISTENERS=0
```

Les fichiers suivants sont immuables sous WO-029 et doivent rester byte-identiques :

- `docs/work_orders/completed/WO-SS-20260901-027-optional-local-push-implementation.md` ;
- `docs/validation/J9-WO027-OPTIONAL-LOCAL-PUSH-QUALIFICATION-20260901.md`.

Une qualification corrective autonome est produite dans
[`J9-WO029-OPTIONAL-LOCAL-PUSH-PORT-BOUNDARY-QUALIFICATION-20260901.md`](../../validation/J9-WO029-OPTIONAL-LOCAL-PUSH-PORT-BOUNDARY-QUALIFICATION-20260901.md).

## 6. Cas de qualification obligatoires

| Cas | Résultat attendu |
|---|---|
| port absent (`-1`) | refus local par la configuration et par le transport |
| port `0` | refus local ; aucun export, claim, client ou socket |
| port `1` | valeur de borne structurellement acceptée sans connexion |
| port `65535` | valeur de borne structurellement acceptée sans connexion |
| port `65536` | refus local avant export/claim et refus direct par le transport |
| origine non HTTPS ou non `127.0.0.1` | gardes WO-027 inchangées et toujours bloquantes |
| configuration invalide via orchestration | zéro nouvelle ligne de ledger et J7 inchangé |
| origine valide via harness loopback | comportement nominal WO-027 préservé, sans réseau réel |

Les tests ne doivent pas ouvrir une connexion sur `1`, `65535` ou `65536` : ils qualifient les
validateurs purs et la barrière d'orchestration. Seul un port éphémère attribué par le système peut
être employé par un harness loopback dédié.

## 7. Vérifications et définition de fini

1. tests unitaires ciblés des propriétés, de la politique, du transport et de l'orchestration ;
2. `mvnw.cmd --offline clean verify` ;
3. `mvnw.cmd --offline -Pintegration-tests verify` pour requalifier le ledger et les parcours
   end-to-end WO-027 ;
4. `docker compose --env-file .env config` en lecture seule lorsque le fichier local est présent ;
5. `git diff --check` et scan de secrets/artefacts interdits ;
6. contrôle de `server.address=127.0.0.1`, des flags fournisseur et livraison bloqués, de la
   permission `NOT_EVIDENCED` et de l'absence de cible réelle ;
7. comparaison SHA-256 des deux fichiers WO-027 immuables avec la baseline ci-dessus ;
8. zéro listener, worker, navigateur ou conteneur de test résiduel.

Le correctif ne pourra être poussé sur la branche de la PR #21, la discussion résolue et la PR
fusionnée qu'après réussite de ces portes. La fusion doit conserver les commits par merge commit,
sans squash, rebase ni force-push.

## 8. Registre d'exécution

| Date/heure UTC | Action | Résultat |
|---|---|---|
| `2026-09-01T12:17:44.3637202Z` | Worktree ouvert depuis le tip exact de la PR #21 | `PASS` |
| `2026-09-01T12:17:44.3637202Z` | Baseline et anomalie P2 consignées | `PASS` |

## 9. État d'ouverture

```text
WORK_ORDER_STATUS=IN_PROGRESS
IMPLEMENTATION_STATUS=PENDING
QUALIFICATION_STATUS=DRAFT
PR21_REVIEW_THREAD_RESOLVED=NO
PR21_MERGED=NO
J9_OFFICIAL_PERMISSION_STATUS=NOT_EVIDENCED
REAL_RECEIVER_NETWORK_AUTHORIZED=NO
REAL_DELIVERY_AUTHORIZED=NO
PROVIDER_NETWORK_AUTHORIZED=NO
VPS_DEPLOYMENT_AUTHORIZED=NO
PRODUCTION_AUTHORIZED=NO
```

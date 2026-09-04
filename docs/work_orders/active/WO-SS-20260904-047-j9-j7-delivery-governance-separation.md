# WO-SS-20260904-047 — Séparation de gouvernance entre acquisition fournisseur et livraison J7

- **Statut :** `IN_PROGRESS`
- **Jalon :** après J9 — préalable runtime à la reprise de WO-046
- **Préparé le :** 2026-09-04
- **Préparation UTC :** `2026-09-04T15:00:14.9065640Z`
- **Préparation Europe/Paris :** `2026-09-04T17:00:14.9065640+02:00`
- **Branche :** `codex/j9-wo047-j7-delivery-governance-separation`
- **Worktree :** `.tmp/w47`
- **Base exacte :** `0338821cc07130f5d200a70db10209bb898a59ae`
- **ADR concernée :** ADR-SS-003 v0.2 acceptée sur la proposition immuable `e1ec9936467dd570f7ed00c51227c8e7d5a35945`
- **Changement runtime autorisé :** oui, dans la portée bornée de la décision propriétaire reçue
- **Décision reçue UTC :** `2026-09-04T15:27:21.4106846Z`
- **Décision reçue Europe/Paris :** `2026-09-04T17:27:21.4198866+02:00`

## 1. Clarification propriétaire à appliquer

Le propriétaire corrige la prémisse qui bloquait WO-046 : il n'existe aucune réponse ni aucun
accord SofaScore officiel, et une telle réponse n'est pas une condition du transfert local d'un
export J7 déjà produit. La séparation fonctionnelle est la suivante :

```text
SOFASCORE_OFFICIAL_RESPONSE=NONE
SOFASCORE_OFFICIAL_PERMISSION_STATUS=NOT_EVIDENCED
LOCAL_LAB_PROVIDER_ENDPOINT_SCOPE=J3_J4_J5
BETTING_PROJECT_PROVIDER_ACCESS=NONE
BETTING_PROJECT_CURRENT_ROLE=RECEIVE_J7_EXPORTS_FROM_LOCAL_LAB
J7_LOCAL_TRANSFER_REQUIRES_SOFASCORE_OFFICIAL_RESPONSE=NO
```

Cette clarification ne constitue ni une permission SofaScore, ni un nouvel accès fournisseur, ni
une autorisation de POST. Elle exige de corriger le couplage actuel avant de reprendre la séquence
WO-046.

## 2. Constat factuel au commit de base

Le receiver INT-001 est déjà indépendant de SofaScore : il valide le contrat J7, mTLS, les hashes,
l'idempotence et la persistance, sans appeler le fournisseur et sans connaître le statut de
permission officielle.

Le sender Local Lab impose en revanche actuellement `EVIDENCED_COMPATIBLE` à quatre niveaux :

1. cohérence de configuration `OptionalLocalPushProperties` ;
2. policy statique et runtime `J7DeliveryPolicy` ;
3. construction du grant `J7ProviderDerivedOwnerGo` ;
4. contrainte PostgreSQL ajoutée par la migration append-only V31.

Ce couplage confond la provenance des données avec l'autorisation interne de les transférer. Il
obligerait le manifeste et le go WO-046 à déclarer une preuve officielle inexistante, ce qui est
interdit.

La preuve de réconciliation existante reste exacte et immuable :

```text
OFFICIAL_PERMISSION_RECONCILIATION_REFERENCE=docs/validation/J9-WO046-OFFICIAL-PERMISSION-RECONCILIATION-20260904.md
OFFICIAL_PERMISSION_RECONCILIATION_SHA256=707e0fd9b07dc0944225be80a590792e5e8ab0631dab964a465335f283ac1473
OFFICIAL_PERMISSION_RECONCILIATION_RESULT=NOT_EVIDENCED
```

Sa conclusion de blocage décrit fidèlement l'ancienne règle v0.1. Elle ne sera ni réécrite ni
présentée comme une réponse officielle ; une preuve corrective séparée consignera la supersession.

## 3. Objectif autorisé

ADR-SS-003 v0.2 et l'implémentation de WO-047 ayant été explicitement autorisées, WO-047 doit :

1. rendre `NOT_EVIDENCED` non bloquant uniquement pour le POST J7 local vers le receiver Betting
   Project ;
2. conserver `NOT_EVIDENCED` comme métadonnée d'audit exacte, versionnée et hashée dans le grant ;
3. continuer à bloquer `EVIDENCED_INCOMPATIBLE` ;
4. préserver le format canonique `J7_PROVIDER_DERIVED_OWNER_GO_V1` strictement tel que validé par
   WO-045, puis introduire un format `J7_PROVIDER_DERIVED_OWNER_GO_V2` non ambigu ;
5. ajouter une migration V32 append-only qui discrimine strictement V1 et V2, sans modifier V31 ni
   réécrire ses données ;
6. conserver toutes les portes distinctes de livraison ;
7. mettre à niveau les contrôles de version J6 vers V32 et les qualifier par tests hors ligne,
   sans exécuter le pipeline natif de sauvegarde/restauration ;
8. produire une preuve corrective expurgée et versionnée ;
9. remettre WO-046 en état reprenable à l'étape 3 seulement après validation propriétaire de
   WO-047, intégration linéaire des commits WO-047 dans sa branche et commit documentaire de
   supersession explicite ;
10. laisser à une décision WO-046 ultérieure la migration V32 de la base primaire puis sa
    sauvegarde chiffrée/restauration isolée avant le manifeste et le POST réels.

## 4. Portée runtime autorisée

### 4.1 Configuration et policy J7

- conserver la valeur par défaut `optional-integration.official-permission-status=NOT_EVIDENCED` ;
- admettre `PROVIDER_DERIVED` avec `NOT_EVIDENCED` ou `EVIDENCED_COMPATIBLE` lorsque toutes les
  autres portes sont satisfaites ;
- refuser explicitement `EVIDENCED_INCOMPATIBLE` avec un code dédié
  `OFFICIAL_PERMISSION_EVIDENCED_INCOMPATIBLE` dans la policy et ses mappings UI/API ;
- retirer le blocker trompeur `OFFICIAL_PERMISSION_NOT_EVIDENCED` du seul chemin J7 ;
- ne modifier ni le receiver INT-001 ni les policies d'acquisition J3/J4/J5.

L'absence de modification des policies J3/J4/J5 n'est pas une autorisation : leurs appels restent
soumis à leurs Work Orders, allowlists, préparations, confirmations et décisions réseau propres.
La livraison J7 doit continuer d'exiger `PROVIDER_NETWORK_AUTHORIZED=NO` et ne peut jamais démarrer
Playwright ou une acquisition. WO-047 ne crée pas une nouvelle porte de permission officielle
pour J3/J4/J5 et ne modifie pas ADR-SS-001 ; ce choix de portée est explicite et devra être confirmé
dans la décision propriétaire.

### 4.2 Grant canonique V2

V1 reste un contrat historique strict : il continue d'exiger exactement `EVIDENCED_COMPATIBLE` et
aucune ligne V1 existante ou future ne peut porter `NOT_EVIDENCED`.

V2 sépare explicitement l'audit de la permission fournisseur et la base de gouvernance du
transfert. Son bloc canonique reprendra toutes les autres lignes V1 et remplacera seulement les
trois lignes ambiguës par les lignes suivantes :

```text
FORMAT=J7_PROVIDER_DERIVED_OWNER_GO_V2
PROVIDER_PERMISSION_AUDIT_REFERENCE=docs/validation/J9-WO046-OFFICIAL-PERMISSION-RECONCILIATION-20260904.md
PROVIDER_PERMISSION_AUDIT_SHA256=707e0fd9b07dc0944225be80a590792e5e8ab0631dab964a465335f283ac1473
PROVIDER_PERMISSION_AUDIT_STATUS=NOT_EVIDENCED
J7_TRANSFER_GOVERNANCE_BASIS_REFERENCE=ADR-SS-003-optional-integration-topology.md
J7_TRANSFER_GOVERNANCE_BASIS_COMMIT=e1ec9936467dd570f7ed00c51227c8e7d5a35945
J7_TRANSFER_GOVERNANCE_BASIS_SHA256=ded6a4da8a3161caae491f62919f4f5c3569c69c821be5a807772cc542cede3f
J7_TRANSFER_GOVERNANCE_BASIS_STATUS=ADR_ACCEPTED_NO_EXECUTION_AUTHORITY
PROVIDER_DERIVED_REAL_POST_AUTHORIZED=YES
PROVIDER_NETWORK_AUTHORIZED=NO
REMOTE_RECEIVER_NETWORK_AUTHORIZED=NO
VPS_DEPLOYMENT_AUTHORIZED=NO
PRODUCTION_AUTHORIZED=NO
AUTOMATIC_RETRY_AUTHORIZED=NO
```

La référence, le commit et le SHA-256 de gouvernance identifieront les octets exacts de la
proposition ADR-SS-003 v0.2 acceptée sans lui attribuer un droit d'exécution. Les
lignes `OWNER_DECISION=GRANT`, `GO_USE=ONE_TIME` et
`PROVIDER_DERIVED_REAL_POST_AUTHORIZED=YES` du bloc owner-go lui-même porteront l'autorisation
spécifique, authentifiée par son propre SHA-256 externe. La ligne
`PROVIDER_DERIVED_REAL_POST_AUTHORIZED=YES` ne pourra apparaître que dans un futur bloc propriétaire
lié au manifeste WO-046 gelé. Elle n'est ni accordée ni consommée sous WO-047.

### 4.3 Migration V32

V31 reste immuable. V32 ajoutera un discriminant de format et les colonnes V2 nécessaires. Les
colonnes physiques V31 seront conservées pour la lecture des lignes historiques ; le bloc
canonique V2 utilisera les noms `PROVIDER_PERMISSION_AUDIT_*` et
`J7_TRANSFER_GOVERNANCE_BASIS_*` sans réinterpréter le bloc canonique V1.

Les contraintes conditionnelles devront garantir :

```text
V1=STRICT_LEGACY_EVIDENCED_COMPATIBLE_ONLY
V2=NOT_EVIDENCED_OR_EVIDENCED_COMPATIBLE_PLUS_EXACT_GOVERNANCE_BASIS_AND_OWNER_GO
```

`EVIDENCED_INCOMPATIBLE`, toute valeur inconnue et toute divergence resteront refusées. La fonction
canonique V1, les lignes V1 et leurs empreintes resteront byte-identiques. V32 ajoutera une fonction
canonique V2 et un dispatch strict par format ; aucune ligne historique ne sera réécrite.

## 5. Invariants préservés

```text
EXPERIMENTAL
LOCAL_ONLY
NOT_PRODUCTION_APPROVED
NO_CRITICAL_DEPENDENCY
SERVER_ADDRESS=127.0.0.1
RECEIVER_ORIGIN=https://127.0.0.1:8444
DELIVERY_TRIGGER=MANUAL_AFTER_HUMAN_VALIDATED
OWNER_GO_USE=ONE_TIME
MAXIMUM_DIRECT_IMPORT_CALLS=1
AUTOMATIC_RETRY=0
MAXIMUM_CONCURRENCY=1
PROVIDER_ACQUISITION_TRIGGERED_BY_DELIVERY=NO
PROVIDER_NETWORK_AUTHORIZED=NO
REMOTE_RECEIVER_NETWORK_AUTHORIZED=NO
VPS_DEPLOYMENT_AUTHORIZED=NO
PRODUCTION_AUTHORIZED=NO
```

WO-047 n'ajoute aucun endpoint, URI, payload, secret, certificat, appel fournisseur, receiver
distant, déploiement VPS, production, polling, scheduler, retry ou fallback.

## 6. Hors périmètre

- obtenir, simuler ou envoyer une demande de permission SofaScore ;
- modifier ADR-SS-001 ou les endpoints J3/J4/J5 ;
- changer le contrat receiver INT-001 ;
- sélectionner l'export ou l'identité mTLS de WO-046 ;
- créer, geler ou committer le manifeste WO-046 ;
- créer, enregistrer ou consommer un go WO-046 ;
- démarrer le receiver ou le Local Lab pour la campagne réelle ;
- effectuer un POST WO-046 ou transporter un export dérivé fournisseur ;
- publier une branche ou fusionner vers `main`.

## 7. Qualification autorisée

Les critères minimaux sont :

- `NOT_EVIDENCED` accepté par configuration, policy, grant Java et contrainte V32 lorsque toutes
  les autres portes J7 sont exactes ;
- `EVIDENCED_INCOMPATIBLE` et toute valeur inconnue refusées avant certificat, claim et transport ;
- receiver/sender non qualifiés, origine incorrecte, mTLS absent, retry, grant absent/divergent,
  export non `HUMAN_VALIDATED`, provenance indéterminée, fenêtre ou ordinal invalides toujours
  refusés fail-closed ;
- consommation atomique unique, concurrence, révocation et absence de remboursement inchangées ;
- migration neuve et V31 vers V32 sur PostgreSQL Testcontainers isolé, sans perte ni réécriture ;
- contrôles de version J6 V32 vérifiés par tests hors ligne, sans exécution du pipeline natif ;
- `mvnw.cmd clean verify`, profil `integration-tests`, `git diff --check`, contrôle UTF-8,
  secrets, loopback et flags bloquants verts ;
- aucun accès fournisseur, POST réel ou réseau non-loopback durant la qualification.

Si et seulement si le bloc propriétaire le porte à `YES`, des POST synthétiques sur
`127.0.0.1` pourront qualifier le format V2, l'atomicité et l'absence de retry. Ils ne pourront
utiliser ni un export fournisseur, ni la base primaire, ni le manifeste ou le go WO-046.

## 8. Décision propriétaire reçue et portes maintenues

La décision suivante a été reçue sans placeholder et vérifiée contre la proposition immuable.
Elle autorise l'implémentation, les tests hors ligne, PostgreSQL isolé et le loopback synthétique ;
elle n'autorise ni base primaire, ni pipeline J6 natif, ni export réel, ni réseau fournisseur ou
distant :

```text
ADR_SS_003_V0_2_OWNER_DECISION=ACCEPT
ADR_SS_003_V0_2_PROPOSAL_COMMIT=e1ec9936467dd570f7ed00c51227c8e7d5a35945
ADR_SS_003_V0_2_FILE_SHA256=ded6a4da8a3161caae491f62919f4f5c3569c69c821be5a807772cc542cede3f
ADR_SS_003_V0_2_SELECTED_TOPOLOGY=OPTIONAL_LOCAL_PUSH
ADR_SS_003_V0_2_NOT_EVIDENCED_AUDIT_STATUS_PRESERVED=YES
ADR_SS_003_V0_2_NOT_EVIDENCED_EFFECT=AUDIT_ONLY_NON_BLOCKING_FOR_LOCAL_J7_TRANSFER
ADR_SS_003_V0_2_EVIDENCED_INCOMPATIBLE_REMAINS_BLOCKING=YES
ADR_SS_003_V0_2_J3_J4_J5_OFFICIAL_PERMISSION_GATE_CHANGE=NO
ADR_SS_003_V0_2_OWNER_GO_FORMAT=J7_PROVIDER_DERIVED_OWNER_GO_V2
ADR_SS_003_V0_2_V1_COMPATIBILITY=STRICT_LEGACY_UNCHANGED
ADR_SS_003_V0_2_MIGRATION_POLICY=V32_APPEND_ONLY_FORMAT_DISCRIMINATED_V31_IMMUTABLE
ADR_SS_003_V0_2_ACCEPTANCE_AUTHORIZES_REAL_POST=NO
ADR_SS_003_V0_2_PROVIDER_NETWORK_AUTHORIZED=NO
ADR_SS_003_V0_2_REMOTE_RECEIVER_NETWORK_AUTHORIZED=NO
ADR_SS_003_V0_2_VPS_DEPLOYMENT_AUTHORIZED=NO
ADR_SS_003_V0_2_PRODUCTION_AUTHORIZED=NO
J9_WO047_OWNER_DECISION=AUTHORIZE_IMPLEMENTATION
J9_WO047_WORK_ORDER=WO-SS-20260904-047-j9-j7-delivery-governance-separation
J9_WO047_BRANCH=codex/j9-wo047-j7-delivery-governance-separation
J9_WO047_BASE_COMMIT=0338821cc07130f5d200a70db10209bb898a59ae
J9_WO047_PROPOSAL_COMMIT=e1ec9936467dd570f7ed00c51227c8e7d5a35945
J9_WO047_WORK_ORDER_SHA256=8b6f1e6e0f6835d124e97741607424b923654b2dd112ab4627d4f2688c99215e
J9_WO047_SCOPE=SEPARATE_J7_LOCAL_TRANSFER_FROM_SOFASCORE_OFFICIAL_PERMISSION_GATE
J9_WO047_NOT_EVIDENCED_AUDIT_STATUS_PRESERVED=YES
J9_WO047_EVIDENCED_INCOMPATIBLE_REMAINS_BLOCKING=YES
J9_WO047_J3_J4_J5_OFFICIAL_PERMISSION_GATE_CHANGE=NO
J9_WO047_OWNER_GO_FORMAT=J7_PROVIDER_DERIVED_OWNER_GO_V2
J9_WO047_V1_COMPATIBILITY=STRICT_LEGACY_UNCHANGED
J9_WO047_MIGRATION_POLICY=V32_APPEND_ONLY_FORMAT_DISCRIMINATED_V31_IMMUTABLE
J9_WO047_OFFLINE_AND_SYNTHETIC_LOOPBACK_QUALIFICATION_AUTHORIZED=YES
J9_WO047_SYNTHETIC_LOOPBACK_POST_AUTHORIZED=YES
J9_WO047_ISOLATED_POSTGRES_QUALIFICATION_AUTHORIZED=YES
J9_WO047_NATIVE_BACKUP_RESTORE_AUTHORIZED=NO
J9_WO047_PRIMARY_DATABASE_SCHEMA_MIGRATION_AUTHORIZED=NO
J9_WO047_PRIMARY_DATABASE_TOUCH_AUTHORIZED=NO
J9_WO047_PRIMARY_DATABASE_BACKUP_AUTHORIZED=NO
J9_WO047_PRIMARY_DATABASE_PURGE_AUTHORIZED=NO
J9_WO047_PROVIDER_NETWORK_AUTHORIZED=NO
J9_WO047_REAL_POST_AUTHORIZED=NO
J9_WO047_REMOTE_RECEIVER_NETWORK_AUTHORIZED=NO
J9_WO047_VPS_DEPLOYMENT_AUTHORIZED=NO
J9_WO047_PRODUCTION_AUTHORIZED=NO
J9_WO046_RESUME_AFTER_WO047_VALIDATION=REQUIRES_SEPARATE_OWNER_DECISION
```

## 9. État effectif

```text
J9_WO047_STATUS=IN_PROGRESS
J9_WO047_IMPLEMENTATION_AUTHORIZED=YES
ADR_SS_003_V0_2_STATUS=ACCEPTED
J9_WO046_STATUS=PAUSED_PENDING_WO047
J9_WO046_ORDERED_STEP_2=COMPLETE_NEGATIVE_NOT_A_J7_TRANSFER_GATE_UNDER_ACCEPTED_V0_2
J9_WO046_ORDERED_STEP_3=NOT_STARTED
J9_WO046_MANIFEST_STATUS=NOT_CREATED
J9_WO046_OWNER_GO_STATUS=NOT_GRANTED
J9_WO046_REAL_POST_AUTHORIZED=NO
J9_WO046_DIRECT_IMPORT_ATTEMPTS=0
J9_PROVIDER_NETWORK_AUTHORIZED=NO
J9_LOCAL_RECEIVER_LOOPBACK_AUTHORIZED=NO
J9_REMOTE_RECEIVER_NETWORK_AUTHORIZED=NO
J9_VPS_DEPLOYMENT_AUTHORIZED=NO
J9_PRODUCTION_AUTHORIZED=NO
```

# WO-SS-20260901-028 — Clarification de l’identité du schéma J7 dans ADR-SS-003

- **Statut :** `VALIDATED`
- **Ouvert le :** 2026-09-01
- **Ouverture UTC :** `2026-09-01T12:04:50.5292633Z`
- **Ouverture Europe/Paris :** `2026-09-01T14:04:50.5391678+02:00`
- **Branche :** `codex/j9-wo028-adr003-schema-identity`
- **Worktree :** `.tmp/j9-wo028-adr003-schema-identity`
- **Base exacte :** `aedb5f424883c9e7a1839fd50aa2c2689fa65418`
- **Pull request cible :** `#20`
- **Discussion corrective :** `discussion_r3903661577`
- **Work Order parent :** `WO-SS-20260901-026`
- **ADR concernée :** ADR-SS-003 v0.1 `ACCEPTED`
- **Type :** clarification contractuelle documentaire, sans effet runtime ou réseau
- **Qualification UTC :** `2026-09-01T12:12:27Z`
- **Clôture Europe/Paris :** `2026-09-01T14:13:12.6810122+02:00`

## 1. Autorisation et séquencement

Le propriétaire autorise explicitement les travaux correctifs visant la PR #20, leur résolution et
la fusion de cette PR avant tout correctif de PR #21. Cette instruction ouvre le présent Work Order
et autorise, sous réserve de qualification verte, l’ajout append-only du correctif à la branche de
PR #20, la résolution de la discussion et une fusion par merge commit.

```text
OWNER_CORRECTIVE_SEQUENCE_AUTHORIZATION=RECEIVED
PR20_FIX_AUTHORIZED=YES
PR20_REVIEW_THREAD_RESOLUTION_AUTHORIZED=AFTER_QUALIFICATION
PR20_MERGE_AUTHORIZED=AFTER_QUALIFICATION_AND_RESOLUTION
PR21_MUTATION_BEFORE_PR20_MERGE=NO
SQUASH_OR_REBASE_AUTHORIZED=NO
FORCE_PUSH_AUTHORIZED=NO
```

## 2. Défaut factuel

ADR-SS-003 v0.1 contient :

```text
PAYLOAD_SCHEMA_VERSION=J7_CANONICAL_EXPORT_V1
```

Cette valeur mélange un nom logique de contrat avec la version portée par les manifestes J7. Le
contrat applicatif versionné définit déjà la paire canonique suivante :

```text
J7ExportContract.SCHEMA_ID=urn:betting-project:sofascore-local-lab:j7:canonical-event-export:v1
J7ExportContract.SCHEMA_VERSION=1.0.0
```

Un futur receiver suivant littéralement l’ancienne ligne pourrait donc valider un identifiant qui
ne correspond pas au manifeste exporté.

## 3. Correctif autorisé

Remplacer la valeur ambiguë par la paire canonique portée par les manifestes J7 :

```text
PAYLOAD_SCHEMA_ID=urn:betting-project:sofascore-local-lab:j7:canonical-event-export:v1
PAYLOAD_SCHEMA_VERSION=1.0.0
```

`PAYLOAD_SCHEMA_ID` et `PAYLOAD_SCHEMA_VERSION` sont les deux dimensions normatives de l’enveloppe
J7. La version du protocole de livraison reste distincte et `NOT_DEFINED` dans ADR-SS-003. La
correction n’introduit aucun alias de contrat ou de protocole supplémentaire.

## 4. Préservation de la décision acceptée

Le draft v0.1 accepté au commit `ca789a3a40ea5fc6c16312bd73f675bc9fd32650` et au SHA-256
`0edcc1e7db2ffc268d1560342d8c2f7c8ea9e1f91c65148e0504c1217f5982be` reste immuable dans
l’historique Git. La correction courante est enregistrée comme clarification factuelle de v0.1,
sans nouvelle
décision de version, sans réinterpréter la sélection `OPTIONAL_LOCAL_PUSH` et sans modifier les
non-autorisations.

## 5. Invariants

```text
ADR_SS_003_SELECTED_TOPOLOGY=OPTIONAL_LOCAL_PUSH
DOCUMENTARY_CONTRACT_CLARIFICATION=YES
ADR_VERSION_CHANGE=NO
ARCHITECTURE_DECISION_CHANGE=NO
J7_SCHEMA_CHANGE=NO
DELIVERY_PROTOCOL_DECISION_CHANGE=NO
RUNTIME_CHANGE=NO
J9_OFFICIAL_PERMISSION_STATUS=NOT_EVIDENCED
INTEGRATION_IMPLEMENTATION_AUTHORIZED=NO
BETTING_PROJECT_RECEIVER_IMPLEMENTATION_AUTHORIZED=NO
PROVIDER_NETWORK_AUTHORIZED=NO
LIVE_DELIVERY_AUTHORIZED=NO
VPS_DEPLOYMENT_AUTHORIZED=NO
PRODUCTION_AUTHORIZED=NO
HISTORY_REWRITE=NO
```

## 6. Vérifications

1. comparer littéralement les valeurs ADR aux constantes `J7ExportContract` ;
2. vérifier l’absence de l’ancienne affectation ambiguë ;
3. exécuter `mvnw.cmd --offline clean verify` ;
4. exécuter `git diff --check` ;
5. contrôler les liens Markdown locaux et les secrets ;
6. confirmer zéro modification Java, SQL, endpoint, configuration ou réseau.

Résultats observés :

| Contrôle | Résultat factuel |
|---|---|
| Identité J7 | `PASS` : ADR, `J7ExportContract` et schéma JSON concordent littéralement sur `urn:betting-project:sofascore-local-lab:j7:canonical-event-export:v1` / `1.0.0` |
| Ancienne affectation | `PASS` : `PAYLOAD_SCHEMA_VERSION=J7_CANONICAL_EXPORT_V1` est absente du bloc contractuel ADR courant |
| `mvnw.cmd --offline "-Dmaven.repo.local=..." clean verify` | `PASS` hors sandbox à `2026-09-01T12:12:27Z` : 946 tests, 0 échec, 0 erreur, 5 skips prévus |
| Tentatives sandbox initiales | première tentative sans dépôt explicite arrêtée à la résolution du parent ; seconde tentative avec dépôt explicite arrêtée pendant la compilation sur `spring-orm-7.0.8.jar` ; la commande identique hors sandbox a réussi, sans téléchargement ni modification de dépendance |
| `git diff --check` | `PASS` |
| Liens Markdown locaux | `PASS` : 11 cibles contrôlées, 0 absente |
| Hygiène documentaire | `PASS` : 0 affectation potentielle de credential dans le diff |
| Invariants | `PASS` : `server.address=127.0.0.1`, fournisseur/Playwright/qualifications à `false` par défaut |
| Portée | `PASS` : ADR, changelog et WO-028 seulement ; zéro Java, SQL, endpoint, configuration ou migration |
| Préservation Git | `PASS` : `ca789a3a…` et `aedb5f42…` restent ancêtres, aucun amend, rebase, squash ou force-push |
| ADR corrigée | SHA-256 `1e0c7b44ddf7742f829f0870c4366dec706011dc0f18ec3ba6a4722fb6fc39ad` ; champ distinct du SHA-256 historique accepté |

```text
WORK_ORDER_STATUS=VALIDATED
WO028_QUALIFICATION_RESULT=PASS_DOCUMENTARY
ADR_SS_003_CORRECTED_RECORD_FILE_SHA256=1e0c7b44ddf7742f829f0870c4366dec706011dc0f18ec3ba6a4722fb6fc39ad
PR20_REVIEW_THREAD_RESOLUTION_AUTHORIZED=YES_AFTER_PUSH
PR20_MERGE_GATE=OPEN_AFTER_PUSH_AND_CLEAN_REVIEW
```

## 7. Définition de fini

- [x] clarification factuelle de v0.1 versionnée et historique accepté préservé ;
- [x] vérifications vertes ;
- [x] Work Order déplacé vers `completed` ;
- [ ] commit correctif poussé sans force sur la tête de PR #20 après le commit de clôture ;
- [ ] discussion `discussion_r3903661577` résolue seulement lorsque ce commit sera visible ;
- [ ] PR #20 fusionnée par merge commit après résolution ;
- [x] aucune mutation PR #21 pendant la préparation et la qualification de ce lot.

Les trois cases GitHub restent volontairement non cochées dans l’archive : leur exécution est
postérieure au commit qui rend cette preuve visible dans la PR. Elles ne remettent pas en cause le
verdict local `VALIDATED` et seront vérifiées directement sur GitHub avant l’ouverture de WO-029.

## 8. Registre

| Étape | État | Preuve |
|---|---|---|
| Branche/worktree dédiés | `PASS` | base `aedb5f424883c9e7a1839fd50aa2c2689fa65418` |
| Clarification ADR | `PASS` | paire canonique versionnée dans `ee9c497…` |
| Qualification | `PASS` | 946 tests et contrôles de la section 6 |
| Résolution PR #20 | `AUTHORIZED_AFTER_PUSH` | après visibilité du commit de clôture |
| Fusion PR #20 | `AUTHORIZED_AFTER_RESOLUTION` | merge commit uniquement |

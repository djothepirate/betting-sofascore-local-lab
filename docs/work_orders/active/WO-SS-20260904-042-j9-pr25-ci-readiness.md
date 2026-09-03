# WO-SS-20260904-042 — Readiness CI de la PR de remplacement WO-036

- **Statut :** `IN_PROGRESS`
- **Jalon :** après J9 — préparation de fusion de WO-036
- **Ouvert le :** 2026-09-04
- **Ouverture UTC :** `2026-09-03T22:36:17.9770318Z`
- **Ouverture Europe/Paris :** `2026-09-04T00:36:17.9770318+02:00`
- **Branche :** `codex/ss-20260904-042-j9-pr25-ci-readiness`
- **Worktree :** `.tmp/j9-wo042-pr25-ci-readiness`
- **Base qualifiée exacte :** `ad343d5f1ed131b9a766c60ffd0086dc354ee839`
- **Pull Request historique :** `#25`
- **Run CI diagnostiqué :** `33812551196`
- **Permission officielle :** `NOT_EVIDENCED`

## 1. Autorisation propriétaire

Le propriétaire autorise l'ouverture et la réalisation de WO-042 afin de produire une branche
conforme à la Convention 1A, qualifier une deadline J6 bornée à 180 secondes et préparer une Pull
Request de remplacement. La PR `#25` doit rester ouverte jusqu'à l'obtention de checks verts sur la
remplaçante.

```text
J9_WO042_OWNER_DECISION=AUTHORIZE_IMPLEMENTATION
J9_WO042_WORK_ORDER=WO-SS-20260904-042-j9-pr25-ci-readiness
J9_WO042_SCOPE=CREATE_CONVENTION_1A_COMPLIANT_REPLACEMENT_BRANCH_AND_QUALIFY_BOUNDED_J6_OFFLINE_CI_DEADLINE
J9_WO042_BASE_COMMIT=ad343d5f1ed131b9a766c60ffd0086dc354ee839
J9_WO042_BRANCH=codex/ss-20260904-042-j9-pr25-ci-readiness

J9_WO042_PRESERVE_EXISTING_57_COMMIT_HASHES=YES
J9_WO042_REBASE_OR_SQUASH_AUTHORIZED=NO
J9_WO042_GLOBAL_BRANCH_POLICY_CHANGE_AUTHORIZED=NO
J9_WO042_J6_OFFLINE_OUTER_DEADLINE_SECONDS=180
J9_WO042_J6_INTERNAL_RUNTIME_TIMEOUT_CHANGE_AUTHORIZED=NO
J9_WO042_J6_FAIL_CLOSED_AND_PROCESS_OWNERSHIP_INVARIANTS=PRESERVE

J9_WO042_LOCAL_AND_CI_QUALIFICATION_AUTHORIZED=YES
J9_WO042_REPLACEMENT_PULL_REQUEST_PREPARATION_AUTHORIZED=YES
J9_WO042_PR25_CLOSE_AUTHORIZED=NO_PENDING_GREEN_REPLACEMENT
J9_WO042_MERGE_AUTHORIZED=NO

J9_OFFICIAL_PERMISSION_STATUS=NOT_EVIDENCED
J9_REAL_J7_EXPORT_TEST_AUTHORIZED=NO
J9_PROVIDER_DERIVED_REAL_DELIVERY_AUTHORIZED=NO
J9_PROVIDER_NETWORK_AUTHORIZED=NO
REAL_RECEIVER_NETWORK_AUTHORIZED=NO
REMOTE_RECEIVER_NETWORK_AUTHORIZED=NO
LIVE_DELIVERY_AUTHORIZED=NO
J9_VPS_DEPLOYMENT_AUTHORIZED=NO
J9_PRODUCTION_AUTHORIZED=NO
INT001_PULL_REQUEST_AUTHORIZED=NO
INT001_VALIDATION_AUTHORIZED=NO
```

## 2. Préflight et diagnostic factuel

Le worktree WO-036 est propre et son HEAD local est identique au HEAD distant :
`ad343d5f1ed131b9a766c60ffd0086dc354ee839`. La branche WO-042 et son worktree n'existaient ni
localement ni sur `origin` au préflight. La nouvelle branche a été créée directement sur ce commit,
sans rebase, squash, cherry-pick ou réécriture des 57 commits de la PR `#25`.

Le run GitHub Actions `33812551196` établit deux échecs indépendants :

1. Linux s'arrête avant Maven dans `ci/check-branch-name.sh` parce que
   `codex/j9-wo036-j7-local-e2e` ne satisfait pas la Convention 1A ;
2. Windows exécute 1 136 tests puis arrête
   `J6NativeBinaryPipelineQualificationTest.syntheticNativePipelineFailsClosedWithoutHumanPassphraseInput`
   sur sa deadline externe de 90 secondes. Le journal atteint les marqueurs de cleanup fail-closed
   avant cette expiration. La même qualification passe localement en `61.270 s` sur le commit de
   base.

La branche `codex/ss-20260904-042-j9-pr25-ci-readiness` satisfait l'expression régulière exacte de
la Convention 1A. Modifier ou relâcher la politique globale de nommage n'est ni nécessaire ni
autorisé.

## 3. Périmètre de correction

WO-042 peut uniquement :

1. porter les 57 commits hérités sur la nouvelle branche conforme sans modifier leurs hashes ;
2. porter la deadline externe hors ligne du test J6 de `90` à `180` secondes ;
3. qualifier le test J6 sous Windows et les vérifications standard du dépôt ;
4. mettre à jour la documentation et produire un rapport expurgé ;
5. pousser la nouvelle branche et ouvrir une PR de remplacement vers `main` ;
6. observer les checks de cette PR sans fusionner ni fermer la PR `#25` prématurément.

Sont hors périmètre : les timeouts internes de `Backup-Restore-J6.ps1`, les superviseurs de
processus, les règles de propriété et de cleanup, le runtime applicatif, les migrations, le contrat
J7, toute politique CI globale et toute opération réseau réelle.

## 4. Invariants

```text
J6_OFFLINE_TEST_OUTER_DEADLINE_SECONDS=180
J6_INTERNAL_PIPELINE_TIMEOUTS=UNCHANGED
J6_FAIL_CLOSED_BEHAVIOR=UNCHANGED
J6_EXACT_PROCESS_OWNERSHIP=UNCHANGED
J6_PID_ONLY_TERMINATION=FORBIDDEN
J6_PROVIDER_CALLS=0
J6_REMOTE_RECEIVER_CALLS=0
SERVER_ADDRESS=127.0.0.1
SOFASCORE_ENABLED_DEFAULT=false
PLAYWRIGHT_AUTOSTART=NO
AUTOMATIC_RETRY=0
```

Les statuts `EXPERIMENTAL`, `LOCAL_ONLY`, `NOT_PRODUCTION_APPROVED` et
`NO_CRITICAL_DEPENDENCY` restent inchangés.

## 5. Qualification attendue

- validation exacte du nom de branche par `ci/check-branch-name.sh` ;
- plusieurs exécutions successives de la qualification J6 hors ligne sous la borne de 180 s ;
- `mvnw.cmd clean verify` vert ;
- `mvnw.cmd -Pintegration-tests verify` vert ;
- `git diff --check` vert ;
- contrôle des secrets et des garde-fous loopback/réseau bloqué ;
- absence de processus, listener ou artefact temporaire J6 résiduel ;
- PR de remplacement sans conflit et checks GitHub verts ;
- PR `#25` laissée ouverte jusqu'au constat des checks verts de la remplaçante.

## 6. État initial

```text
WO042_STATUS=IN_PROGRESS
WO042_IMPLEMENTATION_COMMIT=NOT_CREATED
WO042_QUALIFICATION_RESULT=NOT_RUN
WO042_REPLACEMENT_PULL_REQUEST=NOT_CREATED
WO042_REPLACEMENT_PULL_REQUEST_CHECKS=NOT_RUN
PR25_STATUS=OPEN
PR25_CLOSE_AUTHORIZED=NO_PENDING_GREEN_REPLACEMENT
MERGE_AUTHORIZED=NO
```


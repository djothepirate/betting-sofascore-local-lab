# J9 — WO-042 — Qualification de readiness CI de la PR de remplacement WO-036

## 1. Identification

```text
REPORT_ID=J9-WO042-PR25-CI-READINESS-QUALIFICATION-20260904
WORK_ORDER=WO-SS-20260904-042-j9-pr25-ci-readiness
BRANCH=codex/ss-20260904-042-j9-pr25-ci-readiness
BASE_COMMIT=ad343d5f1ed131b9a766c60ffd0086dc354ee839
OPENING_COMMIT=ec3def5f03c0390289308fe899eafc08cb03893e
IMPLEMENTATION_COMMIT=63cfa6eeb8b21bf185390622fe524014104b0b31
QUALIFIED_AT_UTC=2026-09-03T23:06:00.7298959Z
QUALIFIED_AT_EUROPE_PARIS=2026-09-04T01:06:00.7298959+02:00
QUALIFICATION_RESULT=PASS_LOCAL_CI_READY
HISTORICAL_PULL_REQUEST=25
HISTORICAL_CI_RUN=33812551196
```

WO-042 qualifie uniquement la nouvelle branche Convention 1A et la borne externe du test J6 hors
ligne. Il ne modifie aucun code runtime, script J6, timeout de pipeline, endpoint, contrat J7,
migration, configuration réseau ou politique CI globale.

## 2. Diagnostic qualifié

Le run GitHub Actions historique `33812551196` de la PR `#25` a produit deux échecs indépendants :

| Job | Point d'arrêt | Fait observé | Qualification |
|---|---|---|---|
| Linux | `ci/check-branch-name.sh` | `codex/j9-wo036-j7-local-e2e` est refusée par la Convention 1A avant Maven | `DETERMINISTIC_NAMING_FAILURE` |
| Windows | `J6NativeBinaryPipelineQualificationTest` | 1 136 tests lancés, puis expiration de la borne externe J6 à 90 s après des marqueurs de cleanup réussis | `BOUNDED_TEST_HARNESS_DEADLINE` |

Le même test J6 avait passé localement en `61.270 s` sur le commit de base. Aucun élément du run ne
désignait une violation d'un timeout interne J6, un appel fournisseur, une fuite de secret ou un
échec fonctionnel J7.

## 3. Correction minimale

La branche `codex/ss-20260904-042-j9-pr25-ci-readiness` a été créée directement depuis
`ad343d5f1ed131b9a766c60ffd0086dc354ee839`. L'expression régulière exacte de
`ci/check-branch-name.sh` l'accepte.

Le commit `63cfa6eeb8b21bf185390622fe524014104b0b31` change une seule ligne dans un test :

```text
J6_OFFLINE_TEST_OUTER_DEADLINE_BEFORE_SECONDS=90
J6_OFFLINE_TEST_OUTER_DEADLINE_AFTER_SECONDS=180
```

Cette borne reste fail-closed : si elle expire, le harnais détruit les descendants, détruit le
processus racine, attend sa terminaison et refuse la qualification. La borne Docker séparée reste à
10 minutes. Les scripts `Backup-Restore-J6.ps1`, `J6-NativeBinaryPipeline.psm1`,
`J6-NativeProcessHost.ps1` et `Invoke-J6BackupRestoreLoopbackQualification.ps1` sont byte-identiques
au commit de base.

```text
J6_INTERNAL_PIPELINE_TIMEOUTS=UNCHANGED
J6_FAIL_CLOSED_CLEANUP=UNCHANGED
J6_EXACT_PROCESS_OWNERSHIP=UNCHANGED
J6_PID_ONLY_TERMINATION=FORBIDDEN
J6_PROVIDER_CALLS=0
J6_REMOTE_RECEIVER_CALLS=0
```

## 4. Mesure répétée de la borne J6

Trois exécutions ciblées successives du seul scénario hors ligne ont été réalisées après le
changement :

| Exécution | Résultat | Durée Surefire |
|---:|---|---:|
| 1 | `PASS` | `64.055 s` |
| 2 | `PASS` | `61.918 s` |
| 3 | `PASS` | `60.258 s` |

Le parcours Maven standard a ensuite exécuté la suite J6 en `63.42 s`. Le parcours avec profil
d'intégration a exécuté le scénario ciblé en `64.825 s`. Le maximum mesuré, `64.825 s`, représente
`36.01 %` de la borne de 180 secondes. Les cinq observations post-correction sont sous la borne et
ne nécessitent aucun retry du scénario au sein d'une même invocation Maven.

## 5. Vérifications du dépôt

```text
COMMAND=mvnw.cmd clean verify
RESULT=PASS
TOTAL_TIME=04:11
SUREFIRE_TESTS=1136
SUREFIRE_FAILURES=0
SUREFIRE_ERRORS=0
SUREFIRE_SKIPPED=5
FAILSAFE_TESTS=89
FAILSAFE_FAILURES=0
FAILSAFE_ERRORS=0
FAILSAFE_SKIPPED=0

COMMAND=mvnw.cmd -Pintegration-tests verify
RESULT=PASS
TOTAL_TIME=04:03
SUREFIRE_TESTS=1136
SUREFIRE_FAILURES=0
SUREFIRE_ERRORS=0
SUREFIRE_SKIPPED=5
FAILSAFE_TESTS=89
FAILSAFE_FAILURES=0
FAILSAFE_ERRORS=0
FAILSAFE_SKIPPED=0

COMMAND=ci/check-branch-name.sh codex/ss-20260904-042-j9-pr25-ci-readiness
RESULT=BRANCH_NAME_PASS

COMMAND=ci/assert-local-only.sh
RESULT=LOCAL_ONLY_POLICY_PASS
SOFASCORE_NETWORK_AUTHORIZED=NO
VPS_DEPLOYMENT_AUTHORIZED=NO
PRODUCTION_AUTHORIZED=NO

COMMAND=ci/check-no-secrets.sh ad343d5f1ed131b9a766c60ffd0086dc354ee839
RESULT=SECRET_SCAN_PASS_HIGH_CONFIDENCE

COMMAND=ci/test-package-guards.sh
RESULT=PASS
RELEASE_REPRODUCIBILITY=PASS_LOCAL_ONLY
PACKAGE_GIT_GUARDS=PASS_LOCAL_ONLY

COMMAND=ci/test-distribution-launchers.ps1
RESULT=DISTRIBUTION_LAUNCHERS_PASS_DIRECT_JAR

COMMAND=docker compose --env-file ..\..\.env config --quiet
RESULT=PASS

COMMAND=git diff --check ad343d5f1ed131b9a766c60ffd0086dc354ee839
RESULT=PASS
```

Les tentatives initiales lancées sans les chemins hôte explicites de Maven, Git for Windows ou
Docker ont échoué avant le contrôle visé : résolution Maven interdite par le bac à sable, utilitaires
POSIX absents du `PATH` minimal, puis `jar` absent du premier `PATH` Git Bash. Elles ne sont pas
comptées comme qualifications. Toutes les relances retenues ci-dessus utilisent les exécutables
hôte résolus, se terminent avec le code `0` et n'assouplissent aucune garde.

## 6. Préservation de la pile Git

```text
ORIGIN_MAIN_TO_BASE_COMMIT_COUNT=57
BASE_IS_ANCESTOR_OF_WO042=YES
OPENING_COMMIT_ADDED=1
IMPLEMENTATION_COMMIT_ADDED=1
REBASE_USED=NO
SQUASH_USED=NO
CHERRY_PICK_USED=NO
QUALIFIED_WO036_COMMIT_HASHES_PRESERVED=YES
GLOBAL_BRANCH_POLICY_CHANGED=NO
```

Avant le commit documentaire final, la différence fonctionnelle entre la base et WO-042 est
strictement la valeur `90` vers `180` du test J6. Les autres différences sont le Work Order, le
présent rapport, le README et le changelog.

## 7. Cleanup et état hôte

```text
J6_RESIDUAL_OWNED_PROCESS_COUNT=0
LOOPBACK_LISTENER_8087_COUNT=0
LOOPBACK_LISTENER_8444_COUNT=0
TESTCONTAINERS_RESIDUAL_COUNT=0
PRIMARY_POSTGRES_CONTAINER_ID_PREFIX=e7b218117df3
PRIMARY_POSTGRES_STATUS=RUNNING_HEALTHY
PRIMARY_POSTGRES_PUBLISHED_PORT=127.0.0.1:5432
```

Le conteneur PostgreSQL primaire n'a été ni arrêté, ni purgé, ni remplacé par WO-042. Les tests
PostgreSQL utilisent des conteneurs isolés nettoyés après les suites.

## 8. Conclusion et portes

```text
WO042_LOCAL_TECHNICAL_READINESS=PASS_LOCAL_CI_READY
WO042_REPLACEMENT_PULL_REQUEST=NOT_CREATED_AT_REPORT_FREEZE
WO042_REMOTE_CHECKS=NOT_RUN_AT_REPORT_FREEZE
WO042_STATUS=IN_PROGRESS_PENDING_REPLACEMENT_PULL_REQUEST
PR25_STATUS=OPEN
PR25_CLOSE_AUTHORIZED=NO_PENDING_GREEN_REPLACEMENT
MERGE_AUTHORIZED=NO

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

La preuve locale autorise la publication de la branche et la création de la PR de remplacement. La
readiness finale de WO-042 exige encore les checks verts de cette PR. La PR `#25` ne doit pas être
fermée avant ce constat et aucune fusion n'est autorisée par WO-042.

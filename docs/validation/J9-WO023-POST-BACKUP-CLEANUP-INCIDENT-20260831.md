# J9 — incident fail-closed après chiffrement de la sauvegarde WO-023

## 1. Portée et verdict

Cette preuve consigne la tentative unique de sauvegarde/restauration V28 autorisée après validation
de WO-024. Elle est distincte de la readiness hors ligne, qui reste factuellement réussie dans
`J9-PROVIDER-ROBUSTNESS-V11-READINESS-20260831.md`.

```text
WORK_ORDER=WO-SS-20260831-023-j9-provider-robustness-v11
EXECUTED_WORKTREE_HEAD=1e1abdfe0d67341345c325bac9915461f6ea1cb2
EXECUTED_RUNTIME_CODE_COMMIT=8b91bf86c624ebff0ddf5c4c9bf454e83142b578
INCIDENT_RECORDED_AT_UTC=2026-08-31T19:33:32.6553012Z
POST_INCIDENT_AUDIT_COMPLETED_AT_UTC=2026-08-31T19:59:31.7876611Z
ATTEMPT_STARTED_AT_UTC=NOT_CAPTURED_IN_SANITIZED_TERMINAL_OUTPUT
ATTEMPT_RESULT=STOPPED_FAIL_CLOSED
BACKUP_QUALIFIED=NO
RESTORE_QUALIFIED=NO
PROVIDER_ACCESS_PERFORMED=NO
PRIMARY_DATABASE_PURGE=NO
```

La capture terminal fournie par l'opérateur reste hors Git. Elle mesure `69 920` octets et son
SHA-256 est
`0fd10685cad33fb1e01384161be6984b8e013843d6015106c3d5a692fc3bb18c`. Le chemin local de la
capture et le nom exact de session éphémère ne sont pas consignés.

## 2. Faits observés dans le terminal

Le producteur `pg_dump` et le consommateur `age` ont tous deux terminé avec le code `0`. La copie
binaire a atteint EOF et le nettoyage local de leurs arbres a été déclaré réussi :

```text
J6_NATIVE_PIPELINE_PHASE=BACKUP_ENCRYPTION
J6_NATIVE_PIPELINE_RESULT=SUCCESS
J6_NATIVE_PIPELINE_PRODUCER_STATUS=EXIT_0
J6_NATIVE_PIPELINE_PRODUCER_EXIT_CODE=0
J6_NATIVE_PIPELINE_CONSUMER_STATUS=EXIT_0
J6_NATIVE_PIPELINE_CONSUMER_EXIT_CODE=0
J6_NATIVE_PIPELINE_COPY_STATUS=COMPLETED_TO_EOF
J6_NATIVE_PIPELINE_LOCAL_CLEANUP=PASS
```

L'exécution s'est ensuite arrêtée fail-closed à la confirmation de nettoyage de la session
PostgreSQL exactement possédée. Le message terminal nommait une application éphémère conforme au
patron fermé `j6_backup_<32 caractères hexadécimaux>` ; sa valeur n'est pas reproduite ici.

Ce point d'arrêt précède le calcul de qualification, la création du manifeste, la création d'une
base temporaire et le déchiffrement/restauration. La publication finale n'est possible qu'après
`qualificationDataReady`, aux lignes 592 à 596 de `Backup-Restore-J6.ps1`, chemin qui n'a pas été
atteint.

```text
BACKUP_ENCRYPTION_PIPELINE=COMPLETED_NOT_QUALIFIED
EXACT_POSTGRES_SESSION_CLEANUP_CONFIRMATION=FAILED_OR_UNVERIFIABLE
FINAL_ARCHIVE_PUBLICATION=NO_BY_CODE_PATH
MANIFEST_CREATION=NOT_REACHED
RESTORE_PHASE=NOT_STARTED
TEMPORARY_RESTORE_DATABASE_CREATION=NOT_REACHED
```

## 3. Audit de confinement post-incident

L'audit a été effectué en lecture seule après retour du terminal à l'invite. Trois observations
successives ont interrogé à la fois l'application éphémère exacte et toutes les applications
conformes au patron fermé J6. Les durées ci-dessous ne concernent que la requête exacte ; elles ne
mesurent pas la totalité de la boucle de nettoyage en incident.

| Observation | Session exacte | Toutes sessions J6 possédées | Durée requête exacte |
|---:|---:|---:|---:|
| 1 | 0 | 0 | 433 ms |
| 2 | 0 | 0 | 424 ms |
| 3 | 0 | 0 | 418 ms |

Les contrôles complémentaires donnent :

```text
EXACT_ATTEMPT_PROCESS_COUNT=0
TEMPORARY_RESTORE_DATABASE_COUNT=0
BACKUP_DIRECTORY_FILE_COUNT=0
PARTIAL_FILE_COUNT=0
LISTENER_127_0_0_1_8087_COUNT=0
```

Ces zéros prouvent le confinement au moment de l'audit. Ils ne démontrent pas quelle condition
interne a fait échouer la confirmation antérieure et ne transforment pas rétroactivement la
tentative en sauvegarde/restauration qualifiée.

## 4. Écart de qualification établi

Le script exécuté utilise par défaut :

```text
PipelineCleanupTimeoutMilliseconds=5000
```

`Confirm-J6OwnedPostgresSessionCleanup` réserve jusqu'à la moitié de cette fenêtre, plafonnée à
`2 500 ms`, avant de comptabiliser un zéro stable, puis exige trois observations successives à
zéro. Chaque observation démarre une commande bornée `docker compose exec -> sh -> psql`.

En revanche, les quatre parcours Docker de
`Invoke-J6BackupRestoreLoopbackQualification.ps1` passent tous explicitement :

```text
-PipelineCleanupTimeoutMilliseconds 10000
```

aux lignes 1305, 1341, 1370 et 1409. La borne par défaut réellement utilisée par l'opérateur n'a
donc pas été qualifiée par ces parcours. Les fichiers audités sont identifiés par :

```text
BACKUP_RESTORE_J6_SHA256=1e29e891adc703bcb9cf1987bd896572166c161cb647042fc745a88b45ea37aa
LOOPBACK_QUALIFICATION_SHA256=36be8fba989d639970dc01614253bf83221837048d4606afdeb0f21490e14c90
DEFAULT_CLEANUP_TIMEOUT_MS=5000
DOCKER_QUALIFIED_CLEANUP_TIMEOUT_MS=10000
DEFAULT_PATH_DOCKER_QUALIFIED=NO
```

Le `finally` réessaie la confirmation, capture ensuite l'exception interne et ne conserve à la
ligne 581 qu'un message générique de nettoyage non confirmé. Le terminal ne permet donc plus de
distinguer de façon certaine une session encore présente, l'expiration de la fenêtre, l'échec
d'une commande Docker/`psql`, une sortie scalaire invalide ou un nettoyage de processus non
confirmé.

```text
ROOT_CAUSE=INCONCLUSIVE_WITH_PROVEN_QUALIFICATION_MISMATCH
PROVEN_GAP=REAL_DEFAULT_5000_MS_NOT_COVERED_BY_DOCKER_QUALIFICATION_AT_10000_MS
LIKELY_TRIGGER=OBSERVATION_BUDGET_EXHAUSTED_OR_INNER_PROBE_FAILURE
LIKELY_TRIGGER_CONFIDENCE=MEDIUM
PERSISTENT_SESSION_SURVIVAL_PROVEN=NO
GENERIC_RETHROW_PRESERVES_INNER_CLASSIFICATION=NO
```

Aucune affirmation de faux négatif précis n'est possible sans une cause interne préservée. La
combinaison de l'écart de borne et des comptages postérieurs à zéro justifie un Work Order runtime
ciblé avant tout nouvel essai réel.

## 5. Contre-validation locale post-incident

La validation standard documentaire a d'abord été lancée hors ligne dans le profil restreint ; le
cache Maven remappé n'y contenait pas le parent Spring Boot, donc cette première commande s'est
arrêtée avant lecture complète du projet et avant tout test. La même commande a ensuite été lancée
hors ligne sur l'hôte avec le cache Maven déjà qualifié.

Cette exécution hôte a lancé `945` tests et s'est terminée avec un seul échec :

```text
POST_INCIDENT_CLEAN_VERIFY=FAIL_945_TESTS_1_FAILURE_0_ERRORS_4_SKIPPED
FAILED_TEST=J6NativeBinaryPipelineQualificationTest.syntheticNativePipelineFailsClosedWithoutHumanPassphraseInput
FAILED_SCENARIO=BOUNDED_NATIVE_COMMAND_PID_EVIDENCE_NOT_CREATED
FAILURE_OBSERVED_AT_UTC=2026-08-31T19:45:32.4046529Z
DOCKER_POSTGRES_AGE_PATH_REACHED_BY_FAILED_SCENARIO=NO
PROVIDER_ACCESS_PERFORMED=NO
```

Le harnais avait franchi ses scénarios précédents jusqu'à
`J6_PIPELINE_TIMEOUT=PASS_FAIL_CLOSED`. Son scénario suivant accorde `1 500 ms` à une commande
PowerShell qui doit démarrer, écrire `bounded-command.pid`, puis dormir. Le helper a classé son
timeout, mais le fichier PID attendu n'existait pas lorsque l'assertion suivante a été évaluée.
Le rapport texte non versionné mesure `1 809` octets et porte le SHA-256
`cf5bc2ae5e9afb819d77bf78e9f346604eb4a86bb32c30841bd27c48a68ce43a`.

Aucun code runtime ou de test ne diffère entre le commit WO-024 validé et le HEAD exécuté. Une
course de démarrage/handshake sous charge est plausible, mais ni un démarrage trop lent, ni un
défaut de marqueur, ni une autre cause ne sont prouvés. Cette contre-preuve ne partage pas de lien
causal établi avec la confirmation PostgreSQL de la sauvegarde réelle ; elle établit séparément que
la qualification runtime courante n'est pas reproductible sur ce passage.

Un audit Windows a en outre retrouvé une entrée synthétique antérieure dont la commande exacte est
un `pwsh` non interactif exécutant uniquement `Start-Sleep -Seconds 30`, avec parent absent. Cette
entrée est visible par `tasklist` et CIM avec l'état `Unknown`, mais n'est pas ouvrable par
`Get-Process` ; `taskkill` répond qu'aucune instance n'est en cours et l'appel WMI de terminaison
retourne `2`. Son attribution à la validation qui vient d'échouer n'est pas établie et elle ne
correspond à aucun processus de la tentative réelle de sauvegarde. Elle démontre néanmoins que la
preuve d'absence synthétique doit couvrir les vues Windows contradictoires et ne peut pas dépendre
d'une seule API.

Un répertoire synthétique temporaire antérieur subsiste également hors dépôt. Il a été créé à
`2026-08-31T15:11:50.7970836Z`, plus de quatre heures avant cet échec, et contient seulement deux
fichiers binaires synthétiques de `65 536` octets ainsi qu'un faux exécutable de `24` octets. Son
nom aléatoire et son chemin local ne sont pas versionnés. Il n'est donc pas attribué au verify
courant, mais contredit lui aussi une preuve globale de nettoyage reproductible.

```text
CURRENT_RUNTIME_QUALIFICATION_REPRODUCIBLE=NO
PID_EVIDENCE_FAILURE_ROOT_CAUSE=INCONCLUSIVE
SYNTHETIC_UNKNOWN_STATE_PROCESS_ENTRY_COUNT=1
SYNTHETIC_ENTRY_EXACT_BACKUP_ATTEMPT_OWNED=NO
SYNTHETIC_ENTRY_ATTRIBUTION_TO_CURRENT_VERIFY=NOT_ESTABLISHED
SYNTHETIC_PROCESS_ABSENCE_PROOF=FAILED_CROSS_API_CORROBORATION
SYNTHETIC_TEMP_DIRECTORY_RESIDUAL_COUNT=1
SYNTHETIC_TEMP_DIRECTORY_ATTRIBUTION_TO_CURRENT_VERIFY=NO_PREDATES_FAILURE
TARGETED_RERUN_PERFORMED=NO
TARGETED_RERUN_REASON=AVOID_ACCUMULATING_NEW_SYNTHETIC_PROCESSES_BEFORE_RUNTIME_DIAGNOSIS
```

Les réussites de readiness antérieures restent des photographies factuelles datées. Elles ne
peuvent plus être présentées comme une qualification reproductible courante avant diagnostic et
correction sous un Work Order runtime distinct.

## 6. Conséquences et portes

L'autorisation propriétaire précédente portait sur une nouvelle tentative au singulier. Cette
tentative a été consommée par l'exécution arrêtée. WO-023 §6 et le runbook J6 imposent l'arrêt,
l'audit ciblé et une nouvelle décision après correction ; un audit postérieur propre ne recrée pas
l'autorisation.

```text
WORK_ORDER_STATUS=BLOCKED_AFTER_BACKUP_CLEANUP_UNCONFIRMED
EVIDENCE_STATUS=DRAFT
OFFLINE_READINESS=PASS_AFTER_VALIDATED_WO024
POST_STOP_V28_BACKUP_RESTORE=FAILED_CLEANUP_UNCONFIRMED
OWNER_BACKUP_RETRY_AUTHORIZATION=CONSUMED_BY_FAILED_ATTEMPT
WO023_BACKUP_RETRY_AUTHORIZED_NOW=NO
NEXT_GATE=OWNER_DECISION_ON_DISTINCT_RUNTIME_CORRECTIVE_WORK_ORDER

POST_INCIDENT_CLEAN_VERIFY=FAIL_1_J6_SYNTHETIC_QUALIFICATION
J6_BOUNDED_NATIVE_COMMAND_PID_EVIDENCE=NOT_CREATED
CURRENT_RUNTIME_QUALIFICATION_REPRODUCIBLE=NO
SYNTHETIC_PROCESS_ABSENCE_PROOF=FAILED_CROSS_API_CORROBORATION
SYNTHETIC_TEMP_DIRECTORY_RESIDUAL_COUNT=1

MANIFEST_STATUS=NOT_CREATED
GLOBAL_OWNER_GO=NOT_GRANTED
OWNER_GO_CONSUMED=NO
NEW_GLOBAL_OWNER_GO_GRANTED=NO
NEW_SERIES_DIRECT_ATTEMPTS=0
AUDIT_CUMULATIVE_DIRECT_ATTEMPTS=20
CODEX_LOCAL_UI_PROVIDER_ACTIONS_ALLOWED_NOW=NO
CAMPAIGN_EXECUTION_AUTHORIZED=NO
WO023_PROVIDER_CAMPAIGN_RESUME_AUTHORIZED=NO
PROVIDER_NETWORK_AUTHORIZED=NO
INTEGRATION_OR_PRODUCTION_AUTHORIZED=NO
J9_FINAL_DECISION=NOT_TAKEN
```

Le prochain lot recommandé doit rester hors fournisseur et distinguer les causes de nettoyage,
qualifier la vraie borne par défaut, rendre le parsing scalaire strict, préserver une classification
sanitée de la cause interne et durcir la preuve PID/absence multi-API des commandes natives bornées.
WO-024 est validé et gelé ; il ne doit pas absorber rétroactivement ces nouveaux écarts réels.

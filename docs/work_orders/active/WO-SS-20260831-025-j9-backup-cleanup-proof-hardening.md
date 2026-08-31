# WO-SS-20260831-025 — Durcissement de la preuve de nettoyage J6/J9

- **Statut :** `IN_DEVELOPMENT`
- **Date d'ouverture :** 2026-08-31
- **Décision propriétaire observée à :** 2026-08-31T20:07:12.5287020Z
- **Jalon :** J9 — prérequis runtime de la nouvelle preuve fournisseur
- **Base immuable d'ouverture :** `501490233df5d29719e19d70dabbfc1e37ac4ce7`
- **Branche :** `codex/j9-backup-cleanup-proof-hardening`
- **Worktree :** `.tmp/j9-backup-cleanup-proof-hardening`
- **Work Order parent :** `WO-SS-20260831-023-j9-provider-robustness-v11`
- **Work Order antérieur gelé :** WO-024 `VALIDATED_AND_FROZEN`
- **Réseau fournisseur :** `NOT_AUTHORIZED`
- **Nouvelle sauvegarde/restauration WO-023 :** `NOT_AUTHORIZED`
- **Intégration, production ou VPS courant :** `NOT_AUTHORIZED`

## 1. Décision propriétaire et effet exécutoire

Le propriétaire autorise l'ouverture, l'implémentation et les qualifications hors ligne/loopback de
WO-025. La décision ne rouvre ni WO-024 ni WO-023, ne recrée pas l'autorisation de sauvegarde
consommée et ne constitue aucun go fournisseur.

```text
J9_WO025_OWNER_DECISION=AUTHORIZE_IMPLEMENTATION
J9_WO025_WORK_ORDER=WO-SS-20260831-025-j9-backup-cleanup-proof-hardening
J9_WO025_SCOPE=DIAGNOSE_CORRECT_AND_QUALIFY_J6_EXACT_POSTGRES_CLEANUP_EFFECTIVE_DEFAULT_SANITIZED_INNER_CAUSE_STRICT_SCALAR_PARSING_BOUNDED_NATIVE_PID_EVIDENCE_RELIABILITY_MULTI_API_PROCESS_STATE_CORROBORATION_GHOST_STATE_CLASSIFICATION_AND_EXACT_SYNTHETIC_TEMP_ROOT_CLEANUP
J9_WO025_LOOPBACK_AND_OFFLINE_QUALIFICATION_AUTHORIZED=YES
J9_WO025_FAIL_CLOSED_AND_EXACT_OWNERSHIP_INVARIANTS=PRESERVE
J9_WO025_PID_ONLY_TERMINATION_AUTHORIZED=NO
J9_WO025_EXACT_SYNTHETIC_TEMP_ROOT_CLEANUP=AUTHORIZED_ONLY_AFTER_CANONICAL_PATH_CONFINEMENT_AND_OWNERSHIP_VALIDATION
J9_WO025_CURRENT_VERIFY_RESIDUAL_ATTRIBUTION=NOT_ESTABLISHED
J9_WO024_STATUS=VALIDATED_AND_FROZEN
J9_WO023_BACKUP_RETRY_AFTER_WO025_VALIDATION=REQUIRES_SEPARATE_OWNER_DECISION
J9_WO023_PROVIDER_CAMPAIGN_RESUME_AUTHORIZED=NO
J9_PROVIDER_NETWORK_AUTHORIZED=NO
J9_NEW_PROVIDER_GLOBAL_GO_GRANTED=NO
J9_PRIMARY_DATABASE_PURGE=NO
J9_ENDPOINT_TRANSPORT_PROTOCOL_SCHEMA_MIGRATION_SCOPE_EXPANSION_AUTHORIZED=NO
J9_INTEGRATION_OR_PRODUCTION_AUTHORIZED=NO
```

## 2. Contre-preuves à corriger

### 2.1 Confirmation PostgreSQL réelle

La tentative réelle WO-023 a terminé la pipeline `pg_dump -> age`, puis s'est arrêtée avant
publication et restauration sur une confirmation de nettoyage PostgreSQL échouée ou invérifiable.
L'audit ultérieur a produit trois observations exactes à zéro, sans permettre d'identifier la cause
interne masquée.

L'écart de qualification est établi :

```text
BACKUP_RESTORE_RUNTIME_DEFAULT_CLEANUP_TIMEOUT_MS=5000
WO024_DOCKER_QUALIFICATION_TIMEOUT_MS=10000
DEFAULT_5000_MS_DOCKER_QUALIFIED=NO
INNER_FAILURE_CLASSIFICATION_PRESERVED=NO
POST_INCIDENT_EXACT_SESSION_OBSERVATIONS=0,0,0
POST_INCIDENT_BACKUP_QUALIFIED=NO
```

### 2.2 Commande native bornée et états Windows divergents

Le `clean verify` post-incident a exécuté `945` tests avec un seul échec : la cible synthétique de
commande bornée n'a pas créé son marqueur PID dans sa fenêtre de `1 500 ms`. Aucun changement de
code n'existait depuis la validation WO-024 ; la cause exacte reste indéterminée.

Une entrée synthétique antérieure est visible par `tasklist` et CIM avec l'état `Unknown`, mais ne
peut pas être ouverte par `Get-Process` et n'est pas reconnue comme instance active par `taskkill`.
Un ancien répertoire temporaire synthétique subsiste également. Leur attribution au verify rouge
n'est pas établie.

```text
POST_INCIDENT_CLEAN_VERIFY=FAIL_945_TESTS_1_FAILURE_0_ERRORS_4_SKIPPED
FAILED_SCENARIO=BOUNDED_NATIVE_COMMAND_PID_EVIDENCE_NOT_CREATED
AMBIGUOUS_CROSS_API_GHOST_VISIBILITY=OBSERVED
CURRENT_VERIFY_RESIDUAL_ATTRIBUTION=NOT_ESTABLISHED
SYNTHETIC_TEMP_ROOT_RESIDUAL=OBSERVED_PREEXISTING
PID_ONLY_TERMINATION_AUTHORIZED=NO
```

La preuve source est
[`J9-WO023-POST-BACKUP-CLEANUP-INCIDENT-20260831.md`](../../validation/J9-WO023-POST-BACKUP-CLEANUP-INCIDENT-20260831.md),
SHA-256 `e9bba6abb1c21b2660b27438717b3187f4bfa8dabd23fdaac9b730e6c2505762`.

## 3. Objectifs d'implémentation

### 3.1 Nettoyage PostgreSQL exact

Le correctif doit :

1. séparer explicitement le budget de nettoyage des processus natifs du budget d'observation de la
   session PostgreSQL ;
2. rendre la borne effective livrée et documentée identique à celle qualifiée dans les parcours
   Docker, sans boucle indéfinie ;
3. exécuter une observation/terminaison exacte immédiate, puis trois observations exactes à zéro
   après une stabilisation bornée ;
4. cibler exclusivement un `PGAPPNAME` conforme à
   `^j6_(backup|restore)_[a-f0-9]{32}$` ;
5. exiger un scalaire canonique non vide correspondant à `^[0-9]+$` avant conversion ;
6. lancer `psql` avec arrêt sur erreur SQL et classifier séparément :
   `SESSION_REMAINING`, `OBSERVATION_TIMEOUT`, `DOCKER_COMMAND_FAILURE`,
   `MALFORMED_SCALAR_OUTPUT`, `PROCESS_TREE_CLEANUP_UNCONFIRMED` et succès ;
7. préserver une cause interne sanitée dans l'erreur finale sans journaliser stderr brut, secret,
   `.env`, phrase `age` ou payload ;
8. rendre une seconde confirmation idempotente sans recréer une fenêtre fragile après une preuve
   déjà acquise ;
9. distinguer sessions ciblées, terminaisons réussies et sessions restantes ;
10. maintenir l'échec fail-closed si une seule preuve manque.

### 3.2 Preuve native Windows et répertoire temporaire

Le correctif doit :

1. séparer la readiness de démarrage du délai d'exécution de la commande synthétique ;
2. établir un handshake déterministe avant de mesurer le timeout intentionnel ;
3. conserver l'identité `PID + StartTime` et l'appartenance au Job Object avant toute terminaison ;
4. corroborer l'état entre Job Object, API .NET, snapshot Toolhelp et, seulement comme observation,
   CIM/`tasklist` ;
5. classer une divergence non ouvrable comme `AMBIGUOUS_CROSS_API_GHOST_VISIBILITY`, jamais comme
   processus vivant possédé ni comme absence prouvée ;
6. interdire toute terminaison par PID seul ou par préfixe large ;
7. corriger les chemins d'erreur qui utilisent une identité inexistante ou le mauvais paramètre ;
8. valider le chemin canonique du répertoire de qualification, son confinement sous le répertoire
   temporaire système et son appartenance au harness avant suppression ;
9. vérifier explicitement l'absence du répertoire exact après suppression et préserver une cause
   sanitée en cas d'échec ;
10. ne jamais supprimer par glob ou récursivement le répertoire temporaire parent.

## 4. Non-périmètre

WO-025 ne modifie pas :

- les endpoints, transports, protocoles Playwright, allowlists ou schémas fournisseur ;
- le schéma SQL, Flyway, les tables, migrations ou formats d'export ;
- ADR-SS-001, ADR-SS-002 ou le rapport historique WO-019 ;
- les Work Orders WO-023 ou WO-024, sauf références documentaires factuelles ;
- le déclenchement manuel de la future sauvegarde réelle ;
- l'acquisition SofaScore, le go global, l'intégration, la production ou une topologie VPS ;
- les garanties `EXPERIMENTAL`, `LOCAL_ONLY`, `NOT_PRODUCTION_APPROVED` et
  `NO_CRITICAL_DEPENDENCY`.

## 5. Fichiers candidats

Le périmètre de code est limité aux fichiers nécessaires parmi :

```text
scripts/Backup-Restore-J6.ps1
scripts/J6-NativeBinaryPipeline.psm1
scripts/J6-NativeProcessHost.ps1
scripts/Invoke-J6BackupRestoreLoopbackQualification.ps1
src/test/java/com/bettingproject/sofascorelocal/build/J6NativeBinaryPipelineQualificationTest.java
docs/runbooks/J6-BACKUP-RESTORE-AND-RETENTION.md
```

Tout besoin de modification hors de cette liste sera consigné et arrêté avant élargissement.

## 6. Qualifications obligatoires

### 6.1 PostgreSQL et Docker

- parcours nominal Docker avec les valeurs par défaut effectives, sans override cachant le défaut ;
- session déjà absente avec commandes Docker volontairement lentes ;
- session présente puis terminée naturellement ;
- session réellement persistante ou terminaison refusée, toujours fail-closed ;
- trois zéros proches de la borne sans faux `SESSION_REMAINING` ;
- timeout Docker/`psql`, erreur SQL et erreur de nettoyage de processus classifiés séparément ;
- sorties vide, blanche, multiligne, bruitée ou non numérique refusées ;
- deux confirmations idempotentes successives du même nom exact ;
- aucune archive finale ou manifeste favorable après contre-épreuve ;
- zéro session J6, base temporaire et fichier partiel après chaque scénario.

### 6.2 Processus natifs et temp

- handshake cible acquis avant le timeout intentionnel ;
- cible non prête dans sa borne dédiée, refusée et nettoyée fail-closed ;
- timeout après readiness avec preuve PID et identité complète ;
- PID réutilisé ou `StartTime` divergent, aucune terminaison ;
- Job Object vide corroboré par identité .NET/Toolhelp ;
- état non ouvrable visible par une autre API, classé ambigu sans terminaison PID-only ;
- suppression d'un temp root exact et possédé avec preuve d'absence ;
- refus d'un chemin hors temp, d'un reparse point, d'un parent ou d'un glob ;
- échec de suppression classifié et conservé fail-closed ;
- zéro terminal, wrapper, worker, navigateur ou processus synthétique possédé résiduel.

### 6.3 Validation de dépôt

```text
.\mvnw.cmd clean verify
.\mvnw.cmd -Pintegration-tests verify
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\Verify-Local.ps1 -WithIntegrationTests
docker compose --env-file .env config --quiet
pwsh -NoProfile -File .\scripts\Invoke-J6BackupRestoreLoopbackQualification.ps1
git diff --check
```

Les parcours Docker restent locaux. Aucun test standard ou d'intégration ne doit appeler le
fournisseur. Les flags réseau par défaut et `server.address=127.0.0.1` doivent rester inchangés.

## 7. Critères de fini et porte propriétaire

WO-025 pourra atteindre `READY_FOR_OWNER_REVIEW` uniquement si :

- les deux contre-preuves sont reproduites ou déterministement classifiées avant correction ;
- les qualifications ciblées passent plusieurs fois sans recherche opportuniste d'un résultat vert ;
- les valeurs par défaut réellement livrées sont couvertes ;
- les audits multi-API n'attribuent aucune propriété par présomption ;
- aucun résidu exactement possédé, secret, payload, phrase `age` ou artefact sensible ne subsiste ;
- Maven standard, intégration, Verify-Local et Compose sont verts ;
- le Work Order, README, CHANGELOG, runbook et rapport de validation sont à jour.

La revue propriétaire de WO-025 restera distincte de toute autorisation de nouvelle
sauvegarde/restauration WO-023. Même après validation de WO-025 :

```text
WO023_BACKUP_RETRY_AUTHORIZED=NO_PENDING_SEPARATE_OWNER_DECISION
WO023_PROVIDER_CAMPAIGN_RESUME_AUTHORIZED=NO
PROVIDER_NETWORK_AUTHORIZED=NO
NEW_PROVIDER_GLOBAL_GO_GRANTED=NO
```

## 8. État initial

```text
WORK_ORDER_STATUS=IN_DEVELOPMENT
IMPLEMENTATION_AUTHORIZED=YES
LOOPBACK_AND_OFFLINE_QUALIFICATION_AUTHORIZED=YES

POSTGRES_CLEANUP_CORRECTION=NOT_IMPLEMENTED
EFFECTIVE_DEFAULT_QUALIFICATION=NOT_EXECUTED
STRICT_SCALAR_PARSING=NOT_IMPLEMENTED
SANITIZED_INNER_CAUSE=NOT_IMPLEMENTED
BOUNDED_NATIVE_PID_EVIDENCE_RELIABILITY=NOT_IMPLEMENTED
MULTI_API_PROCESS_STATE_CORROBORATION=NOT_IMPLEMENTED
GHOST_STATE_CLASSIFICATION=NOT_IMPLEMENTED
EXACT_SYNTHETIC_TEMP_ROOT_CLEANUP=NOT_IMPLEMENTED

STANDARD_VERIFY=NOT_EXECUTED_UNDER_WO025
INTEGRATION_VERIFY=NOT_EXECUTED_UNDER_WO025
DOCKER_LOOPBACK_QUALIFICATION=NOT_EXECUTED_UNDER_WO025
PROVIDER_ACCESS_PERFORMED=NO

WO024_STATUS=VALIDATED_AND_FROZEN
WO023_STATUS=BLOCKED_AFTER_BACKUP_CLEANUP_UNCONFIRMED
WO023_BACKUP_RETRY_AUTHORIZED=NO
WO023_PROVIDER_CAMPAIGN_RESUME_AUTHORIZED=NO
PROVIDER_NETWORK_AUTHORIZED=NO
NEW_PROVIDER_GLOBAL_GO_GRANTED=NO
PRIMARY_DATABASE_PURGE=NO
INTEGRATION_OR_PRODUCTION_AUTHORIZED=NO
```

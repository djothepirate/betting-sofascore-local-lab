# WO-SS-20260831-020 — Diagnostic et correction de la fermeture gracieuse Playwright

- **Statut :** `READY_FOR_OWNER_REVIEW`
- **Date d'ouverture :** 2026-08-31
- **Jalon :** J9 — prérequis runtime de WO-019
- **Base locale :** `542f35246ae7680d6c66d05fb73fb634b5dc1165`
- **Branche :** `codex/j9-playwright-graceful-close`
- **Worktree dédié :** `.tmp/j9-playwright-graceful-close`
- **Work Orders liés :** WO-013 `VALIDATED`, WO-018 `IN_DEVELOPMENT`, WO-019 `OPEN_AWAITING_PREREQUISITES`
- **ADR applicables :** ADR-SS-001 v1.4 et ADR-SS-002 v1.0
- **Réseau fournisseur :** `NOT_AUTHORIZED`
- **Reprise de la campagne WO-019 :** `NOT_AUTHORIZED`
- **Intégration ou production :** `NOT_AUTHORIZED`
- **Nouvel endpoint, allowlist, parseur, schéma ou migration :** `NONE`
- **Cause racine :** `ESTABLISHED`
- **Correction locale :** `IMPLEMENTED_AND_QUALIFIED`
- **Validation propriétaire :** `REQUIRED`

## 1. Objectif

Établir le prédicat exact qui provoque les erreurs `RUNTIME_FAILURE` pendant la fermeture normale
du worker Playwright, corriger le défaut au plus petit périmètre et requalifier le nettoyage
synchrone de l'arbre de processus sous les bornes existantes.

La correction qualifiée produit une fermeture qui :

- authentifie le frame terminal du worker sans affaiblir le protocole IPC ;
- permet au worker, à Chromium, au contexte et à Playwright de se fermer normalement ;
- conserve l'inventaire exact du processus racine et de ses descendants ;
- termine l'annulation en vol en deux secondes au plus et le nettoyage complet en cinq secondes au
  plus ;
- ne laisse aucun processus possédé, listener ou artefact navigateur ;
- reste fail-closed lorsque l'identité, le frame terminal ou le nettoyage est inconclusif.

Le présent Work Order ne qualifie ni SofaScore, ni la campagne multi-dossier, ni une topologie VPS.

## 2. Autorisation propriétaire

L'autorisation propriétaire reçue le 2026-08-31 est enregistrée exactement comme suit :

```text
J9_RUNTIME_WORK_ORDER_OPENING=AUTHORIZE
J9_RUNTIME_SCOPE=DIAGNOSE_AND_CORRECT_PLAYWRIGHT_GRACEFUL_CLOSE_AND_PROCESS_TREE_CLEANUP
J9_PROVIDER_NETWORK_AUTHORIZED=NO
J9_WO019_PROVIDER_CAMPAIGN_RESUME_AUTHORIZED=NO
J9_INTEGRATION_OR_PRODUCTION_AUTHORIZED=NO
```

Cette autorisation couvre le diagnostic, les tests de régression et la correction runtime locale.
Elle n'est ni un go réseau, ni une reprise implicite de WO-019, ni une autorisation de modifier les
bornes de sécurité ou le protocole sans preuve et revue explicites.

## 3. Preuve d'entrée faisant autorité

WO-019 a arrêté sa readiness hors ligne après deux exécutions identiques de la qualification J3
Playwright loopback, dont une contre-qualification hors sandbox :

```text
BASE_COMMIT=542f35246ae7680d6c66d05fb73fb634b5dc1165
LOOPBACK_J3=FAIL_14_TESTS_0_FAILURE_12_ERRORS_0_SKIPPED_2_PASS
ERROR_CLASS=RUNTIME_FAILURE_AT_GRACEFUL_CLOSE
OUT_OF_SANDBOX_COUNTER_QUALIFICATION=IDENTICAL_FAILURE
OPERATOR_STOP_SCENARIOS=PASS_2
REPORT_XML_SHA256=6567dbdf590dc5cdd76cfa9c80452324c92fbb00234151c537d943267ad94f37
REPORT_TEXT_SHA256=a9358021bf2299a954138ba69982f98628e4b4f2fcda72038d77c4f7806b62a3
PROVIDER_CALLS=0
RESIDUAL_OWNED_PROCESS_COUNT=0
LISTENER_127_0_0_1_8087_COUNT=0
FORBIDDEN_BROWSER_ARTIFACTS=NONE_FOUND
```

Les douze erreurs surviennent à la sortie des scénarios, dans `campaign.close()` puis
`closeCampaign()` et `terminateSynchronously()`. Les assertions de transport loopback ont été
franchies avant la fermeture. Le code d'entrée agrège néanmoins quatre conditions sous le même
`RUNTIME_FAILURE` : identité exacte, annulation dans la borne, nettoyage dans la borne et absence de
résidu. Aucune cause unique n'est déclarée avant un test discriminant.

Les bornes héritées de WO-013 restent :

```text
STOP_ACKNOWLEDGEMENT_MAX=500ms
IN_FLIGHT_CANCELLATION_MAX=2s
PROCESS_TREE_CLEANUP_MAX=5s
RESIDUAL_OWNED_PROCESS_COUNT=0
```

## 4. Diagnostic causal et correction

### 4.1 Cause établie

Un test discriminant ajouté avant la correction a reproduit le défaut avec exactement une erreur.
Il a établi la séquence circulaire suivante :

1. le worker reçoit `CLOSE`, ferme `WorkerRuntime`, émet exactement `CLOSED`, puis attend l'EOF du
   parent avant de quitter sa JVM ;
2. le parent authentifie `CLOSED`, mais entrait directement dans l'attente puis la terminaison de
   l'arbre possédé sans fermer sa sortie IPC ;
3. le worker restait donc vivant en attente de cet EOF, tandis que le parent attendait sa sortie ou
   finissait par signaler l'arbre ;
4. la fermeture normale devenait une terminaison forcée et remontait `RUNTIME_FAILURE` malgré les
   assertions de transport déjà franchies.

Le diagnostic a aussi établi que le timeout de fermeture gracieuse configuré à `5 s` était plafonné
à tort par `IN_FLIGHT_CANCELLATION_MAX=2s`. Ce plafond confondait le budget du handshake normal avec
la borne d'un arrêt opérateur.

```text
DISCRIMINANT_BEFORE_CORRECTION=RED_1_ERROR
ROOT_CAUSE=CLOSED_ACKNOWLEDGED_WORKER_WAITING_FOR_PARENT_EOF
PARENT_SEQUENCE_DEFECT=PROCESS_TREE_WAIT_OR_TERMINATION_BEFORE_PARENT_EOF
GRACEFUL_TIMEOUT_DEFECT=INCORRECTLY_CAPPED_AT_2S
```

### 4.2 Correction minimale

Le superviseur conserve le worker, le protocole et les endpoints existants, mais sépare maintenant
explicitement les modes `GRACEFUL` et `OPERATOR_STOP` :

- après `CLOSED`, il capture un inventaire frais et exact avant que la sortie du worker puisse
  reparenter un descendant ;
- il exécute ensuite `shutdownOutput()` pour produire l'EOF attendu par le worker ;
- il laisse `250 ms` à la sortie naturelle, puis conserve les replis bornés à `1 s` pour le signal
  souple, `2 s` pour l'annulation opérateur et `5 s` pour le nettoyage total ;
- le timeout du handshake gracieux utilise sa valeur configurée dans le budget de nettoyage restant
  et n'est plus ramené à la borne d'annulation opérateur ;
- le verrou d'I/O est un `ReentrantLock` interrogé par tranches de `20 ms`, afin qu'un arrêt
  opérateur puisse préempter une fermeture gracieuse en cours ;
- l'instant de la première demande d'arrêt opérateur est immuable et reste l'origine de ses bornes,
  y compris pendant l'inventaire et la transition de mode ;
- une nouvelle tentative de nettoyage après un échec strictement pré-mutation reçoit un nouveau
  budget ; un échec après mutation reste terminal et fail-closed.

```text
NATURAL_PROCESS_EXIT_MAX=250ms
SOFT_PROCESS_TERMINATION_MAX=1s
IN_FLIGHT_CANCELLATION_MAX=2s
PROCESS_TREE_CLEANUP_MAX=5s
STOP_PREEMPTION_POLL_INTERVAL=20ms
WORKER_IMPLEMENTATION_CHANGED=NO
IPC_PROTOCOL_CHANGED=NO
ENDPOINTS_CHANGED=NO
```

## 5. Contrat à préserver

- aucune terminaison par nom de processus, glob ou famille de navigateurs ;
- le PID et l'heure de création du worker restent authentifiés avant toute mutation ;
- les descendants sont retenus par identité exacte, y compris après reparentage ;
- `CLOSE`/`CLOSED` reste le seul handshake de fermeture gracieuse ;
- un frame absent, incorrect ou tardif ne devient jamais un succès supposé ;
- le double `close()` reste idempotent ;
- la lease n'est libérée qu'après nettoyage confirmé ;
- un échec après mutation reste terminal et observable ;
- les bornes 500 ms, 2 s et 5 s ne sont ni augmentées, ni contournées silencieusement ;
- chaque campagne conserve un worker, un Chromium et un contexte frais non persistant ;
- aucun profil, cookie, `storageState`, HAR, trace, vidéo, capture ou téléchargement n'est conservé ;
- `server.address=127.0.0.1` et tous les flags fournisseur restent bloquants par défaut.

## 6. Périmètre inclus

- `ChildJvmPlaywrightProviderSupervisor` et son contrat de nettoyage exact ;
- le worker Playwright seulement si le diagnostic démontre un défaut côté worker ;
- tests unitaires discriminants du handshake, des horloges et de l'arbre possédé ;
- qualification Chromium réelle exclusivement contre un serveur `127.0.0.1` ;
- requalification loopback J3, J4 et J5, sans accès fournisseur ;
- documentation technique, README, changelog et Work Orders directement concernés.

## 7. Hors périmètre

- tout appel SofaScore ou autre accès fournisseur ;
- toute reprise, sauvegarde/restauration V28 ou demande de go sous WO-019 ;
- endpoint, URI, allowlist, transport métier, parseur, schéma SQL ou migration ;
- retry, fallback, polling, scheduler, live, cache forcé ou contournement ;
- suppression de l'exception ou transformation d'un nettoyage inconclusif en succès ;
- nettoyage asynchrone après retour réussi de `close()` ;
- kill global ou arrêt d'un processus non authentifié ;
- intégration, production, déploiement VPS ou dépendance critique.

Si la correction exige une augmentation de borne, une nouvelle version du protocole IPC ou un
élargissement fonctionnel, l'implémentation s'arrête avec `BLOCKED_REQUIRES_SCOPE_AMENDMENT`.

## 8. Lots d'exécution

1. ouvrir WO-020 sur un worktree propre et vérifier la baseline standard — `COMPLETED` ;
2. reproduire et discriminer le défaut sans réseau fournisseur — `COMPLETED` ;
3. ajouter un test de régression rouge couvrant le mécanisme causal — `COMPLETED_RED_1_ERROR` ;
4. appliquer la correction minimale — `COMPLETED` ;
5. exécuter les tests unitaires ciblés et les contrats worker — `PASS` ;
6. exécuter les qualifications loopback J3, puis J4, puis J5 — `PASS_14_OF_14_EACH` ;
7. exécuter les suites standard et intégration ainsi que Compose — `PASS` ;
8. auditer processus, listeners et artefacts — `PASS` ;
9. soumettre le résultat à la revue propriétaire sans reprendre WO-019 — `CURRENT_STEP`.

## 9. Matrice de validation

| Cas | Résultat requis |
|---|---|
| Fermeture nominale J3/J4/J5 | aucune exception, sortie authentifiée, zéro résidu |
| `2xx`, `404` et incidents loopback | sémantiques existantes inchangées |
| `CLOSED` valide puis sortie différée | canal libéré et arbre disparu sous les bornes |
| `CLOSED` absent ou incorrect | échec fermé, nettoyage exact, aucun succès implicite |
| Identité racine ou descendante incomplète | échec fermé sans kill global |
| Arrêt opérateur en vol | ACK ≤ 500 ms, annulation ≤ 2 s, nettoyage ≤ 5 s |
| Double `close()` | idempotent, aucun second signal ou second worker |
| Worker déjà sorti ou erreur d'initialisation | attribution exacte et état terminal cohérent |
| Campagne suivante | nouveau worker et nouveau contexte, aucun état réutilisé |
| Hygiène | zéro réseau fournisseur, secret, brut ou artefact navigateur |

### 9.1 Résultats mesurés

```text
DISCRIMINANT_BEFORE_CORRECTION=RED_1_ERROR
SUPERVISOR_TESTS=PASS_31_OF_31
WORKER_PROTOCOL_TESTS=PASS_10_OF_10
WORKER_SECURITY_TESTS=PASS_1_OF_1
J3_LOOPBACK_QUALIFICATION=PASS_14_OF_14
J4_LOOPBACK_QUALIFICATION=PASS_14_OF_14
J5_LOOPBACK_QUALIFICATION=PASS_14_OF_14
PROVIDER_ACCESS_PERFORMED=NO
```

Le premier lancement du script J5 depuis Windows PowerShell 5.1 s'est arrêté avant Maven, car cette
version ne fournit pas `ResolveLinkTarget`. La même commande et le même diff ont ensuite été lancés
sans adaptation sous PowerShell `7.6` (`pwsh`) et ont réussi. Cet incident local n'a lancé ni test,
ni worker, ni accès fournisseur.

Les derniers rapports de la qualification J5 sont identifiés sans contenu brut :

```text
J5_QUALIFICATION_REPORT_SHA256=a4f3acd1433cf502718dae41999c554d013909764f01ae864be8ac341addcc6d
J5_QUALIFICATION_REPORT_BYTES=43755
J5_PROTOCOL_REPORT_SHA256=954f449c1e7be87276a51f294f729b9616e59fe79cfba1a8a6732fa4ec3fce00
J5_PROTOCOL_REPORT_BYTES=42304
J5_SECURITY_REPORT_SHA256=ce90c1077ad817b1726f1e8b5c70f1e7eb8d7e077fdf4a3bcc30fcaef7bc4f23
J5_SECURITY_REPORT_BYTES=40569
```

## 10. Vérifications obligatoires

```powershell
.\mvnw.cmd clean verify
.\mvnw.cmd -Pintegration-tests verify
powershell.exe -NoProfile -ExecutionPolicy Bypass `
  -File .\scripts\Verify-Local.ps1 -WithIntegrationTests
docker compose --env-file .env config
pwsh -NoProfile -File .\scripts\Invoke-J3PlaywrightLoopbackQualification.ps1
pwsh -NoProfile -File .\scripts\Invoke-J4PlaywrightLoopbackQualification.ps1
pwsh -NoProfile -File .\scripts\Invoke-J5PlaywrightLoopbackQualification.ps1
git diff --check
```

Chaque qualification Playwright reste explicitement locale et loopback. Les flags fournisseur du
`.env` restent à `false`, les origines/allowlists fournisseur restent vides et aucun lancement
standard ne crée de worker.

Résultats finaux :

```text
STANDARD_CLEAN_VERIFY=PASS_931_TESTS_0_FAILURE_0_ERROR_4_SKIPPED
INTEGRATION_VERIFY=PASS_67_TESTS_0_FAILURE_0_ERROR
VERIFY_LOCAL_WITH_INTEGRATION=PASS
TESTCONTAINERS_FLYWAY_SCHEMA=V28_CONFIRMED
WO019_V28_ENCRYPTED_BACKUP_RESTORE=NOT_EXECUTED
DOCKER_COMPOSE_CONFIG=PASS
POST_LOOPBACK_RESIDUAL_OWNED_PROCESS_COUNT=0
POST_LOOPBACK_LISTENER_127_0_0_1_8087_COUNT=0
POST_FINAL_VERIFY_RESIDUAL_OWNED_PROCESS_COUNT=0
POST_FINAL_VERIFY_LISTENER_127_0_0_1_8087_COUNT=0
POST_FINAL_VERIFY_FORBIDDEN_RUNTIME_ARTIFACT_COUNT=0_EXCLUDING_IMMUTABLE_BROWSER_CACHE
J5_FORBIDDEN_BROWSER_ARTIFACT_SCANNER=PASS
PROVIDER_ACCESS_PERFORMED=NO
```

La confirmation V28 provient exclusivement de la cible PostgreSQL isolée Testcontainers. Elle ne
constitue pas la sauvegarde chiffrée et la restauration isolée exigées séparément par WO-019.
Le premier lancement final de `Verify-Local.ps1` s'est arrêté au préflight, avant Maven, car la
commande `docker` n'était pas exposée dans le `PATH` du sous-processus. Le même script a ensuite
réussi avec le binaire Docker Desktop local résolu explicitement, sans modification du dépôt.

## 11. Fichiers candidats

Le diagnostic a limité les fichiers runtime et de test aux éléments suivants :

```text
src/main/java/com/bettingproject/sofascorelocal/application/network/playwright/
  ChildJvmPlaywrightProviderSupervisor.java
src/test/java/.../ChildJvmPlaywrightProviderSupervisorTest.java
src/provider-playwright-qualification-test/java/.../ProviderPlaywrightLocalQualificationIT.java
src/provider-playwright-test/java/.../ProviderPlaywrightWorkerProtocolTest.java
docs/architecture/J3-PLAYWRIGHT-PROVIDER-TRANSPORT.md
README.md
CHANGELOG.md
docs/work_orders/active/WO-SS-20260831-018-decision-j9.md
docs/work_orders/active/WO-SS-20260831-019-j9-provider-robustness.md
docs/work_orders/active/WO-SS-20260831-020-j9-playwright-graceful-close.md
```

Le worker de production, le protocole IPC, les scripts, la configuration, les endpoints, les
allowlists et la persistance ne sont pas modifiés. Le test de protocole ajoute seulement la preuve
explicite que le worker émet `CLOSED` avant d'attendre l'EOF parent.

## 12. Livraison Git

Les principaux commits locaux de réalisation sont :

```text
95bd13c docs(j9): open Playwright graceful close runtime work order
f969419 fix(playwright): close worker gracefully before tree cleanup
d62e46a docs(j9): record WO-020 runtime readiness
```

Aucun push, PR, merge vers `main`, payload brut ou artefact navigateur n'est autorisé. Après
validation technique, l'intégration dans `codex/j9-decision` reste linéaire ou fast-forward.

## 13. Portes de statut

```text
IN_DEVELOPMENT
  -> ROOT_CAUSE_ESTABLISHED
  -> CORRECTION_IMPLEMENTED
  -> LOCAL_READINESS_PASS
  -> READY_FOR_OWNER_REVIEW
  -> VALIDATED
```

Terminaux alternatifs :

```text
BLOCKED_REQUIRES_SCOPE_AMENDMENT
FAILED_SAFETY_REGRESSION
CANCELLED
```

Même `LOCAL_READINESS_PASS` n'autorise pas la reprise de WO-019. Une décision propriétaire
distincte restera nécessaire.

État soumis au propriétaire :

```text
WORK_ORDER_STATUS=READY_FOR_OWNER_REVIEW
ROOT_CAUSE_STATUS=ESTABLISHED
IMPLEMENTATION_STATUS=COMPLETED_LOCAL
LOCAL_READINESS=PASS
OWNER_REVIEW_REQUIRED=YES
OWNER_VALIDATION=NOT_RECEIVED
NETWORK_AUTHORIZED=NO
WO019_CAMPAIGN_RESUME_AUTHORIZED=NO
INTEGRATION_OR_PRODUCTION_AUTHORIZED=NO
```

## 14. Journal d'exécution

```text
OPENING_BASE=542f35246ae7680d6c66d05fb73fb634b5dc1165
OPENING_BRANCH=codex/j9-playwright-graceful-close
OPENING_WORKTREE=.tmp/j9-playwright-graceful-close
WORKTREE_CLEAN_AT_OPENING=YES
WO020_NUMBER_AVAILABLE=YES
ECLIPSE_CONCURRENT_EDIT=NONE_OBSERVED
BASELINE_STANDARD_VERIFY=PASS_928_TESTS_0_FAILURE_0_ERROR_4_SKIPPED
ROOT_CAUSE_STATUS=ESTABLISHED
ROOT_CAUSE=CLOSED_ACKNOWLEDGED_WORKER_WAITING_FOR_PARENT_EOF
GRACEFUL_TIMEOUT_DEFECT=INCORRECTLY_CAPPED_AT_2S
DISCRIMINANT_BEFORE_CORRECTION=RED_1_ERROR
IMPLEMENTATION_STATUS=COMPLETED_LOCAL
WORKER_IMPLEMENTATION_CHANGED=NO
IPC_PROTOCOL_CHANGED=NO
ENDPOINTS_CHANGED=NO
SUPERVISOR_TESTS=PASS_31_OF_31
WORKER_PROTOCOL_TESTS=PASS_10_OF_10
WORKER_SECURITY_TESTS=PASS_1_OF_1
LOOPBACK_J3=PASS_14_OF_14
LOOPBACK_J4=PASS_14_OF_14
LOOPBACK_J5=PASS_14_OF_14
STANDARD_CLEAN_VERIFY=PASS_931_TESTS_0_FAILURE_0_ERROR_4_SKIPPED
INTEGRATION_VERIFY=PASS_67_TESTS_0_FAILURE_0_ERROR
VERIFY_LOCAL_WITH_INTEGRATION=PASS
TESTCONTAINERS_FLYWAY_SCHEMA=V28_CONFIRMED_NOT_WO019_BACKUP_RESTORE
DOCKER_COMPOSE_CONFIG=PASS
POST_LOOPBACK_RESIDUAL_OWNED_PROCESS_COUNT=0
POST_LOOPBACK_LISTENER_127_0_0_1_8087_COUNT=0
POST_FINAL_VERIFY_RESIDUAL_OWNED_PROCESS_COUNT=0
POST_FINAL_VERIFY_LISTENER_127_0_0_1_8087_COUNT=0
POST_FINAL_VERIFY_FORBIDDEN_RUNTIME_ARTIFACT_COUNT=0_EXCLUDING_IMMUTABLE_BROWSER_CACHE
J5_FORBIDDEN_BROWSER_ARTIFACT_SCANNER=PASS
WORK_ORDER_STATUS=READY_FOR_OWNER_REVIEW
OWNER_VALIDATION=NOT_RECEIVED
NETWORK_AUTHORIZED=NO
WO019_CAMPAIGN_RESUME_AUTHORIZED=NO
INTEGRATION_OR_PRODUCTION_AUTHORIZED=NO
PROVIDER_CALLS_UNDER_WO020=0
```

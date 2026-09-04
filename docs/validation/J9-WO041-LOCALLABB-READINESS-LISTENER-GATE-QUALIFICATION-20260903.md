# J9 — WO-041 — Qualification de la porte readiness/listener de LocalLabB

## 1. Identification

```text
WORK_ORDER=WO-SS-20260903-041-j9-local-labb-readiness-listener-gate
BRANCH=codex/j9-wo041-local-labb-readiness-listener-gate
BASE_COMMIT=62bb8d126d28da2aedefb8845ee3817492229a71
OPENING_COMMIT=92e7548783596e2a7519a3486f801b33b616e815
IMPLEMENTATION_COMMIT=7893136664c5fc6c162da844735e36cb7829814c
QUALIFIED_AT_UTC=2026-09-03T20:36:05.4456232Z
QUALIFIED_AT_EUROPE_PARIS=2026-09-03T22:36:05.4456232+02:00
QUALIFICATION_RESULT=PASS_LOCAL_FAIL_CLOSED
```

La qualification porte exclusivement sur l'outillage de campagne WO-036 qui attend et attribue le
listener de LocalLabB. Elle ne reprend pas WO-036, ne réarme aucune claim, n'émet aucun POST et ne
modifie ni le sender Java, ni le receiver INT-001, ni le protocole HTTP, ni une migration.

## 2. Diagnostic qualifié

Avant WO-041, une tentative de `Wait-WO036LoopbackListener` observait deux états différents du même
port :

1. `Test-WO036ExactLoopbackListener` lisait une première fois les listeners ;
2. après un résultat négatif, la fonction d'attente lisait immédiatement les listeners une seconde
   fois et classait toute présence comme conflit.

Le test de régression reproduit déterministement la séquence suivante : premier instantané vide,
puis listener exact `127.0.0.1:8087` possédé par le PID enregistré au second instantané. L'ancien
algorithme retourne alors `false`, bien que le second état soit précisément l'état attendu.

```text
ROOT_CAUSE_DEFECT=INCOHERENT_DOUBLE_LISTENER_SNAPSHOT_TOCTOU_FALSE_CONFLICT
ROOT_CAUSE_DEFECT_REPRODUCED=YES
R5_SYMPTOM_COMPATIBILITY=HIGH
R5_EXACT_INTERLEAVING_RETROACTIVELY_OBSERVED=NO
```

Le défaut est donc qualifié. Il est fortement compatible avec le constat R5 — journal Spring/Tomcat
indiquant le démarrage sur `8087`, puis refus de la porte — mais R5 n'avait pas conservé les deux
instantanés bruts. Le présent rapport ne transforme pas cette compatibilité en observation historique
directe.

## 3. Correction

La fonction d'attente prend désormais un seul instantané par tentative. Ce même instantané sert à
reconnaître le listener exact ou à refuser un conflit. Un instantané vide autorise uniquement la
tentative suivante après le délai borné.

Les invariants restent inchangés :

```text
EXPECTED_LOCAL_ADDRESS=127.0.0.1
EXPECTED_LOCAL_PORT=8087
EXPECTED_LISTENER_COUNT=1
EXPECTED_OWNING_PROCESS=EXACT_REGISTERED_PID
READINESS_MAXIMUM_ATTEMPTS=60
READINESS_DELAY_MILLISECONDS=500
MISSING_PROCESS_FAILS_IMMEDIATELY=YES
CONFLICTING_LISTENER_FAILS_IMMEDIATELY=YES
AUTOMATIC_CAMPAIGN_ACTION_RETRY=0
```

Les listeners `0.0.0.0`, `::1`, multiples ou possédés par un autre processus restent refusés. La
disparition du processus attendu et l'expiration des 60 observations restent fail-closed.

## 4. Qualification automatisée

Les quatre suites Pester de l'outillage WO-036/WO-037/WO-041 ont été rejouées ensemble :

```text
PESTER_TOTAL=87
PESTER_PASSED=87
PESTER_FAILED=0
PESTER_SKIPPED=0
```

Les cas WO-041 couvrent : reproduction de l'ancien faux conflit, transition vide vers listener
exact, succès dès le premier instantané exact, autre PID, adresse non-loopback, listeners multiples,
processus disparu, expiration bornée et inspection statique du harness hôte.

Le harness hôte a créé dix processus PowerShell possédés, chacun ouvrant avec un retard distinct un
listener synthétique sur `127.0.0.1:8087`. Chaque processus était identifié par son PID, son instant
de démarrage, son exécutable, le chemin exact du helper et un jeton GUID privé à l'exécution. Aucun
arrêt fondé sur le seul PID n'était permis.

```text
WO041_HOST_QUALIFICATION=PASS_LOCAL_FAIL_CLOSED
WO041_DELAYED_EXACT_LISTENER_ITERATIONS=10
WO041_DELAYED_EXACT_LISTENER_SUCCESSES=10
WO041_CONFLICTING_OWNER_REJECTED=YES
WO041_RESIDUAL_PROCESS_COUNT=0
WO041_RESIDUAL_LISTENER_COUNT=0
WO041_NETWORK_SCOPE=127.0.0.1_ONLY
WO041_PROVIDER_CALLS=0
WO041_RECEIVER_CALLS=0
```

## 5. Vérifications du dépôt

```text
COMMAND=mvnw.cmd clean verify
RESULT=PASS
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
SUREFIRE_TESTS=1136
SUREFIRE_FAILURES=0
SUREFIRE_ERRORS=0
SUREFIRE_SKIPPED=5
FAILSAFE_TESTS=89
FAILSAFE_FAILURES=0
FAILSAFE_ERRORS=0
FAILSAFE_SKIPPED=0

COMMAND=docker compose --env-file ..\..\.env config --quiet
RESULT=PASS
```

Le fichier d'environnement n'a pas été imprimé. Aucun secret, payload J7 réel, certificat privé ou
donnée de session n'est inclus dans le rapport ou le diff.

## 6. État hôte après qualification

```text
PRIMARY_CONTAINER_ID=e7b218117df39b7cbfbffb3b1223647568f149912a84592e506b5690f9686c1f
PRIMARY_CONTAINER_STATUS=RUNNING_HEALTHY
PRIMARY_CONTAINER_PUBLISHED_PORT=127.0.0.1:5432
PRIMARY_CONTAINER_VOLUME=betting-sofascore-local-lab-postgres-data:/var/lib/postgresql
TESTCONTAINERS_RESIDUAL_COUNT=0
BOUNDARY_LISTENER_COUNT=1
BOUNDARY_LISTENER_5432=127.0.0.1
BOUNDARY_LISTENER_8087=ABSENT
BOUNDARY_LISTENER_8444=ABSENT
BOUNDARY_LISTENER_5433=ABSENT
```

Le conteneur PostgreSQL primaire n'a été ni arrêté, ni purgé, ni remplacé. Les conteneurs temporaires
des tests d'intégration ont été nettoyés.

## 7. Conclusion et portes

```text
WO041_TECHNICAL_READINESS=PASS_LOCAL_FAIL_CLOSED
WO041_STATUS=READY_FOR_OWNER_REVIEW
WO041_MOVE_TO_COMPLETED=NO_PENDING_OWNER_REVIEW

WO036_STATUS=STOPPED_AFTER_FIRST_IMPORT_AND_CONSUMED_B_START_CLAIM
WO036_RESUME_AUTHORIZED=NO
WO036_NEXT_FRESH_RUN=R6
WO036_R6_MANIFEST=REQUIRED_NEW_AND_FROZEN_BEFORE_FIRST_POST

J9_OFFICIAL_PERMISSION_STATUS=NOT_EVIDENCED
J9_PROVIDER_DERIVED_REAL_DELIVERY_AUTHORIZED=NO
J9_PROVIDER_NETWORK_AUTHORIZED=NO
REAL_RECEIVER_NETWORK_AUTHORIZED=NO
REMOTE_RECEIVER_NETWORK_AUTHORIZED=NO
LIVE_DELIVERY_AUTHORIZED=NO
J9_VPS_DEPLOYMENT_AUTHORIZED=NO
J9_PRODUCTION_AUTHORIZED=NO
```

WO-041 peut être soumis à la revue propriétaire. Sa validation éventuelle n'autorisera pas la
reprise de WO-036 : R6 exigera une décision distincte et un nouveau manifeste gelé avant son premier
POST.

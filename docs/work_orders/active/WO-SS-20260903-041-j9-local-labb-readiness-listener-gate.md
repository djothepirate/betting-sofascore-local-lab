# WO-SS-20260903-041 — Porte de readiness/listener de LocalLabB

- **Statut :** `READY_FOR_OWNER_REVIEW`
- **Jalon :** après J9 — correction runtime de l'outillage avant une éventuelle reprise R6 de WO-036
- **Ouvert le :** 2026-09-03
- **Ouverture UTC :** `2026-09-03T20:18:18.1820646Z`
- **Ouverture Europe/Paris :** `2026-09-03T22:18:18.1820646+02:00`
- **Prêt pour revue UTC :** `2026-09-03T20:36:05.4456232Z`
- **Prêt pour revue Europe/Paris :** `2026-09-03T22:36:05.4456232+02:00`
- **Branche :** `codex/j9-wo041-local-labb-readiness-listener-gate`
- **Worktree :** `.tmp/w41`
- **Base locale d'ouverture vérifiée :** `62bb8d126d28da2aedefb8845ee3817492229a71`
- **Commit d'ouverture :** `92e7548783596e2a7519a3486f801b33b616e815`
- **Commit d'implémentation :** `7893136664c5fc6c162da844735e36cb7829814c`
- **Résultat de qualification :** `PASS_LOCAL_FAIL_CLOSED`
- **Rapport :**
  `docs/validation/J9-WO041-LOCALLABB-READINESS-LISTENER-GATE-QUALIFICATION-20260903.md`
- **SHA-256 du rapport :**
  `ed650e2d9631f39b442769315fd50d47e5ee85129fb9e4549bb7093110a2976c`
- **Work Order bloqué :** `WO-SS-20260902-036-j9-j7-local-e2e-qualification`
- **Rapport du constat R5 :**
  `docs/validation/J9-WO036-J7-LOCAL-E2E-CAMPAIGN-RESUME-R5-STOP-20260903.md`
- **SHA-256 du rapport R5 :**
  `1bee325424931a8ecc6e0b445fbd06efd2e80364a2dad8e038245dd2012c8053`
- **Permission officielle :** `NOT_EVIDENCED`

## 1. Autorisation propriétaire

Le propriétaire demande :

```text
Faire le Work Order runtime distinct pour diagnostiquer et qualifier la porte de
readiness/listener de LocalLabB, puis sa validation.
```

Cette instruction autorise l'ouverture, le diagnostic, la correction et la qualification du seul
outillage de campagne qui observe la readiness et l'identité du listener LocalLabB. Elle ne reprend
pas WO-036, ne réarme pas la claim R5 consommée et n'autorise aucun POST vers INT-001.

```text
J9_WO041_OWNER_DECISION=AUTHORIZE_IMPLEMENTATION
J9_WO041_WORK_ORDER=WO-SS-20260903-041-j9-local-labb-readiness-listener-gate
J9_WO041_SCOPE=DIAGNOSE_CORRECT_AND_QUALIFY_LOCAL_LAB_B_READINESS_AND_EXACT_LISTENER_GATE
J9_WO041_ALLOWED_CHANGE=WO036_CAMPAIGN_TOOLING_AND_TESTS_ONLY
J9_WO041_OFFLINE_QUALIFICATION_AUTHORIZED=YES
J9_WO041_WINDOWS_LOOPBACK_LISTENER_QUALIFICATION_AUTHORIZED=YES
J9_WO041_NETWORK_SCOPE=127.0.0.1_ONLY
J9_WO041_LOCAL_LAB_JAVA_SENDER_CHANGE_AUTHORIZED=NO
J9_WO041_RECEIVER_RUNTIME_CHANGE_AUTHORIZED=NO
J9_WO041_PROTOCOL_RELAXATION_AUTHORIZED=NO
J9_WO041_AUTOMATIC_RETRY_AUTHORIZED=NO
J9_WO041_PRIMARY_DATABASE_TOUCH_AUTHORIZED=NO
J9_WO041_PRIMARY_DATABASE_PURGE_AUTHORIZED=NO

J9_WO036_STATUS=STOPPED_AFTER_FIRST_IMPORT_AND_CONSUMED_B_START_CLAIM
J9_WO036_RESUME_AUTHORIZED=NO
J9_WO036_NEXT_FRESH_RUN=R6
J9_WO036_R6_MANIFEST=REQUIRED_NEW_AND_FROZEN_BEFORE_FIRST_POST
J9_WO036_WORK_ORDER_MOVE_TO_COMPLETED=NO

J9_OFFICIAL_PERMISSION_STATUS=NOT_EVIDENCED
J9_PROVIDER_DERIVED_REAL_DELIVERY_AUTHORIZED=NO
J9_PROVIDER_NETWORK_AUTHORIZED=NO
REAL_RECEIVER_NETWORK_AUTHORIZED=NO
REMOTE_RECEIVER_NETWORK_AUTHORIZED=NO
LIVE_DELIVERY_AUTHORIZED=NO
J9_VPS_DEPLOYMENT_AUTHORIZED=NO
J9_PRODUCTION_AUTHORIZED=NO
INT001_PULL_REQUEST_AUTHORIZED_BY_THIS_WORK_ORDER=NO
INT001_VALIDATION_AUTHORIZED_BY_THIS_WORK_ORDER=NO
```

## 2. Constat factuel R5

R5 a terminé le premier import sous `201/IMPORTED`, puis a arrêté LocalLabA gracieusement. Le
lanceur de LocalLabB a créé sa preuve d'identité de processus avant d'attendre le listener. Son
journal privé expurgé indique ensuite un démarrage Spring/Tomcat sur `8087`, mais la fonction
`Wait-WO036LoopbackListener` a retourné `false` et le processus a été nettoyé. Aucun deuxième POST
n'a été émis.

Le code antérieur à WO-041 prenait deux instantanés distincts par tentative : le premier dans
`Test-WO036ExactLoopbackListener`, le second immédiatement après pour détecter tout listener
conflictuel. Si le listener exact apparaît entre ces deux lectures, le premier instantané est vide
et le second, pourtant valide, est classé comme conflit. WO-041 reproduit déterministement ce faux
conflit et qualifie le défaut
`INCOHERENT_DOUBLE_LISTENER_SNAPSHOT_TOCTOU_FALSE_CONFLICT`. Le défaut est fortement compatible avec
R5, mais les deux instantanés bruts de R5 n'avaient pas été conservés : l'interleaving historique
exact ne peut donc pas être affirmé comme observé rétroactivement.

## 3. Objectif et périmètre

WO-041 doit :

1. reproduire de manière déterministe la transition « absent puis exact » entre les deux lectures ;
2. établir si l'algorithme actuel retourne prématurément `false` dans cette transition ;
3. corriger la porte pour qu'une tentative utilise un seul instantané cohérent des listeners ;
4. conserver l'exigence exacte d'un unique listener `127.0.0.1`, sur le port attendu et possédé par
   le PID enregistré ;
5. conserver l'arrêt immédiat sur un listener conflictuel, multiple, non-loopback ou possédé par un
   autre PID ;
6. conserver l'arrêt si le processus disparaît et le timeout borné si aucun listener n'apparaît ;
7. qualifier hors ligne les transitions et effectuer une qualification hôte strictement loopback
   avec des processus synthétiques possédés et nettoyés ;
8. produire un rapport autonome expurgé et soumettre le résultat à la validation propriétaire.

Le sender Java, le receiver INT-001, le protocole HTTP, les migrations, les bases et les données
J7 sont hors périmètre. Aucun processus applicatif Local Lab ou receiver n'est requis pour la
qualification hôte : elle doit utiliser uniquement un listener synthétique sur loopback.

## 4. Invariants

```text
EXPECTED_LOCAL_ADDRESS=127.0.0.1
EXPECTED_LOCAL_PORT=8087
EXPECTED_LISTENER_COUNT=1
EXPECTED_OWNING_PROCESS=EXACT_REGISTERED_PID
READINESS_MAXIMUM_ATTEMPTS=60
READINESS_DELAY_MILLISECONDS=500
CONFLICTING_LISTENER_FAILS_IMMEDIATELY=YES
MISSING_PROCESS_FAILS_IMMEDIATELY=YES
AUTOMATIC_RETRY_OF_CAMPAIGN_ACTION=0
PROVIDER_CALLS=0
REMOTE_RECEIVER_CALLS=0
PRIMARY_DATABASE_TOUCH=NO
```

Les statuts `EXPERIMENTAL`, `LOCAL_ONLY`, `NOT_PRODUCTION_APPROVED` et
`NO_CRITICAL_DEPENDENCY` restent inchangés.

## 5. Qualification attendue

Les cas obligatoires sont :

- transition déterministe entre un premier instantané vide et un second listener exact :
  reproduction de l'ancien faux conflit ;
- instantané exact unique : `PASS` ;
- aucun listener puis apparition exacte à une tentative ultérieure : `PASS` ;
- listener possédé par un autre PID : refus immédiat ;
- listener sur une autre adresse, plusieurs listeners ou processus disparu : refus ;
- absence persistante : expiration bornée, sans boucle illimitée ;
- processus synthétique retardé écoutant réellement sur `127.0.0.1:8087` : succès répété sans faux
  négatif ;
- cleanup après succès et échec : zéro processus et listener synthétique résiduel ;
- Pester, Maven standard et profil d'intégration, UTF-8, secrets, loopback et flags bloquants verts.

## 6. Critères de sortie

WO-041 atteint `READY_FOR_OWNER_REVIEW` : la cause est reproduite, le correctif reste limité à
l'outillage WO-036, les cas hostiles restent fail-closed et la qualification hôte loopback ne
laisse aucun résidu. Le Work Order restera actif jusqu'à la validation propriétaire.

Même validé, WO-041 ne reprend pas WO-036. R6 exigera une décision propriétaire distincte, un run
neuf et un manifeste R6 gelé avant son premier POST.

## 7. Réalisation

Le commit `7893136664c5fc6c162da844735e36cb7829814c` :

- extrait l'évaluation exacte d'un instantané dans
  `Test-WO036ExactLoopbackListenerSnapshot` ;
- fait lire à `Wait-WO036LoopbackListener` un seul instantané par tentative ;
- emploie ce même instantané pour accepter l'unique listener exact ou refuser immédiatement un
  conflit ;
- conserve le contrôle préalable de l'existence du processus et la borne de 60 tentatives espacées
  de 500 ms ;
- ajoute une régression Pester déterministe et un harness Windows n'ouvrant qu'un listener
  synthétique loopback possédé et nettoyé avec preuve d'identité exacte.

Aucun fichier Java, migration, contrat receiver ou configuration réseau applicative n'a été
modifié.

## 8. Preuves de qualification

```text
ROOT_CAUSE_DEFECT_REPRODUCED=YES
R5_SYMPTOM_COMPATIBILITY=HIGH
R5_EXACT_INTERLEAVING_RETROACTIVELY_OBSERVED=NO

PESTER_TOTAL=87
PESTER_PASSED=87
PESTER_FAILED=0
PESTER_SKIPPED=0

WO041_DELAYED_EXACT_LISTENER_ITERATIONS=10
WO041_DELAYED_EXACT_LISTENER_SUCCESSES=10
WO041_CONFLICTING_OWNER_REJECTED=YES
WO041_RESIDUAL_PROCESS_COUNT=0
WO041_RESIDUAL_LISTENER_COUNT=0

MAVEN_CLEAN_VERIFY=PASS
MAVEN_INTEGRATION_TESTS_VERIFY=PASS
SUREFIRE_TESTS=1136
SUREFIRE_FAILURES=0
SUREFIRE_ERRORS=0
SUREFIRE_SKIPPED=5
FAILSAFE_TESTS=89
FAILSAFE_FAILURES=0
FAILSAFE_ERRORS=0
FAILSAFE_SKIPPED=0

DOCKER_COMPOSE_CONFIG=PASS
TESTCONTAINERS_RESIDUAL_COUNT=0
PRIMARY_POSTGRES_CONTAINER_UNCHANGED=YES
PRIMARY_POSTGRES_STATUS=RUNNING_HEALTHY
PRIMARY_POSTGRES_LISTENER=127.0.0.1:5432
LISTENER_8087_AFTER_QUALIFICATION=ABSENT
LISTENER_8444_AFTER_QUALIFICATION=ABSENT
LISTENER_5433_AFTER_QUALIFICATION=ABSENT
PROVIDER_CALLS=0
RECEIVER_CALLS=0
```

Le rapport autonome contient les commandes, la matrice de cas, les bornes de nettoyage et les
restrictions de gouvernance. Son empreinte est celle enregistrée en tête du présent Work Order.

## 9. État soumis à la revue propriétaire

```text
J9_WO041_TECHNICAL_READINESS=PASS_LOCAL_FAIL_CLOSED
J9_WO041_STATUS=READY_FOR_OWNER_REVIEW
J9_WO041_WORK_ORDER_MOVE_TO_COMPLETED=NO_PENDING_OWNER_REVIEW

J9_WO036_STATUS=STOPPED_AFTER_FIRST_IMPORT_AND_CONSUMED_B_START_CLAIM
J9_WO036_RESUME_AFTER_WO041_VALIDATION=REQUIRES_SEPARATE_OWNER_DECISION
J9_WO036_WORK_ORDER_MOVE_TO_COMPLETED=NO
J9_WO036_NEXT_FRESH_RUN=R6
J9_WO036_R6_MANIFEST=REQUIRED_NEW_AND_FROZEN_BEFORE_FIRST_POST

J9_OFFICIAL_PERMISSION_STATUS=NOT_EVIDENCED
J9_PROVIDER_DERIVED_REAL_DELIVERY_AUTHORIZED=NO
J9_PROVIDER_NETWORK_AUTHORIZED=NO
REAL_RECEIVER_NETWORK_AUTHORIZED=NO
REMOTE_RECEIVER_NETWORK_AUTHORIZED=NO
LIVE_DELIVERY_AUTHORIZED=NO
J9_VPS_DEPLOYMENT_AUTHORIZED=NO
J9_PRODUCTION_AUTHORIZED=NO
```

Le Work Order demeure dans `active` jusqu'à une décision propriétaire explicite. Une validation de
WO-041 autorisera uniquement son classement documentaire ; elle ne vaudra pas reprise de WO-036.

# WO-SS-20260904-044 — Frontière d'argument du chemin JAR WO-036

- **Statut :** `IN_PROGRESS`
- **Jalon :** après J9 — résolution du second P2 de la PR `#26`
- **Ouvert le :** 2026-09-04
- **Ouverture UTC :** `2026-09-04T08:49:46.7534713Z`
- **Ouverture Europe/Paris :** `2026-09-04T10:49:46.7534713+02:00`
- **Branche :** `codex/ss-20260904-044-j9-wo036-java-jar-path-argument-boundary`
- **Worktree :** `.tmp/j9-wo044-java-jar-path-argument-boundary`
- **Base exacte :** `96dec8492dbc043c7d6e4310195421f4411722df`
- **Pull Request concernée :** `#26`
- **Remarque concernée :** `P2 — Quote the JAR path passed to Start-Process`
- **Fil GitHub :** `https://github.com/djothepirate/betting-sofascore-local-lab/pull/26#discussion_r3932363097`
- **Permission officielle :** `NOT_EVIDENCED`

## 1. Autorisation propriétaire

Le propriétaire autorise explicitement l'ouverture et l'implémentation de WO-044 :

```text
J9_WO044_OWNER_DECISION=AUTHORIZE_IMPLEMENTATION
J9_WO044_OPEN_WORK_ORDER_AUTHORIZED=YES
J9_WO044_WORK_ORDER=WO-SS-20260904-044-j9-wo036-java-jar-path-argument-boundary
J9_WO044_SCOPE=DIAGNOSE_CORRECT_AND_QUALIFY_WO036_JAVA_JAR_PATH_ARGUMENT_BOUNDARY_UNDER_WINDOWS_PATHS_WITH_SPACES

J9_WO044_ALLOWED_CHANGE=WO036_CAMPAIGN_PROCESS_LAUNCH_TOOLING_TESTS_AND_DOCUMENTATION_ONLY
J9_WO044_WINDOWS_PATH_WITH_SPACES_QUALIFICATION_AUTHORIZED=YES
J9_WO044_OFFLINE_HOST_PROCESS_QUALIFICATION_AUTHORIZED=YES
J9_WO044_ARGUMENT_BOUNDARY_INVARIANT=PRESERVE_EXACT_JAVA_ARGUMENTS_INCLUDING_SINGLE_JAR_PATH_ARGUMENT
J9_WO044_PROTOCOL_RELAXATION_AUTHORIZED=NO
J9_WO044_LOCAL_LAB_JAVA_APPLICATION_CHANGE_AUTHORIZED=NO
J9_WO044_RECEIVER_RUNTIME_CHANGE_AUTHORIZED=NO
J9_WO044_DATABASE_START_AUTHORIZED=NO
J9_WO044_PRIMARY_DATABASE_TOUCH_AUTHORIZED=NO
J9_WO044_PRIMARY_DATABASE_PURGE_AUTHORIZED=NO
J9_WO044_PROVIDER_CALLS_AUTHORIZED=NO
J9_WO044_RECEIVER_HTTP_CALLS_AUTHORIZED=NO
J9_WO044_REMOTE_NETWORK_AUTHORIZED=NO
J9_WO044_PR26_UPDATE_AFTER_VALIDATION=REQUIRES_SEPARATE_OWNER_DECISION

J9_PR26_STATUS=OPEN_PENDING_WO044
J9_PR26_MERGE_AUTHORIZED=NO
J9_PR25_STATUS=OPEN
J9_PR25_CLOSE_AUTHORIZED=NO
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

## 2. Constat factuel

La fonction de démarrage des composants Java dans
`scripts/wo036/WO036-CampaignTools.psm1` appelle actuellement :

```powershell
Start-Process -FilePath $state.JavaPath -ArgumentList @(
    "-Dwo036.instance=$instance",
    $script:JdkHttpClientRetryGuards[0],
    $script:JdkHttpClientRetryGuards[1],
    $script:JdkHttpClientRetryGuards[2],
    '-jar', $jar)
```

Sous Windows, `Start-Process` recompose `ArgumentList` en une ligne native séparée par des espaces.
Le chemin `$jar` ne porte aucune protection explicite. Un chemin contenant un espace peut donc
être découpé en plusieurs arguments et ne plus être reçu comme l'unique valeur suivant `-jar`.

Les qualifications existantes utilisent un worktree sans espace et ne prouvent pas cette
frontière. Le lanceur qualifié WO-041 protège déjà son chemin helper transmis à `-File` par des
guillemets explicites ; ce précédent ne constitue toutefois pas à lui seul une preuve pour le
lancement Java WO-036.

## 3. Objectif

WO-044 doit :

1. reproduire hors ligne la frontière fautive avec un exécutable de capture contrôlé et un JAR
   synthétique placé sous un chemin Windows contenant des espaces ;
2. transmettre le chemin JAR comme un argument natif unique, byte-for-byte identique au chemin
   canonique attendu ;
3. préserver séparément les trois garde-fous JDK et l'identité d'instance ;
4. ne pas élargir le contrat, les flags, les timeouts, les retries, le réseau ou la propriété des
   processus ;
5. refuser fail-closed un chemin JAR non qualifiable ou une sérialisation ambiguë ;
6. qualifier le comportement corrigé et un ensemble de cas limites hors ligne ;
7. produire un rapport expurgé et demander une validation propriétaire avant tout push vers la
   branche de la PR `#26`.

## 4. Périmètre autorisé

Fichiers modifiables :

- `scripts/wo036/WO036-CampaignTools.psm1` ;
- les tests Pester WO-036 concernés ;
- un harnais hôte WO-044 strictement hors ligne si nécessaire ;
- ce Work Order, le rapport, `README.md` et `CHANGELOG.md`.

Sont hors périmètre : code Java applicatif, receiver INT-001, contrat HTTP, migrations, Docker
Compose, bases, payload J7, fournisseur, réseau receiver, réseau distant, VPS, production, fusion
de `#26` et fermeture de `#25`.

## 5. Invariants

```text
PROCESS_EXECUTABLE=EXACT_QUALIFIED_JAVA_PATH
JAR_ARGUMENT=ONE_EXACT_NATIVE_ARGUMENT
INSTANCE_ARGUMENT=ONE_EXACT_NATIVE_ARGUMENT
JDK_RETRY_GUARDS=THREE_EXACT_NATIVE_ARGUMENTS
AUTOMATIC_RETRY=0_UNCHANGED
PROCESS_OWNERSHIP=UNCHANGED
STANDARD_OUTPUT_REDIRECTION=UNCHANGED
STANDARD_ERROR_REDIRECTION=UNCHANGED
WORKING_DIRECTORY=UNCHANGED
DATABASE_START=NO
PROVIDER_CALLS=0
RECEIVER_HTTP_CALLS=0
REMOTE_NETWORK=NO
```

Les statuts `EXPERIMENTAL`, `LOCAL_ONLY`, `NOT_PRODUCTION_APPROVED` et
`NO_CRITICAL_DEPENDENCY` restent inchangés.

## 6. Qualification attendue

- preuve de reproduction avant correction ou fixture équivalente déterministe ;
- chemin synthétique contenant au moins un espace réellement reçu comme un argument unique ;
- identité d'instance, trois garde-fous JDK, `-jar` et chemin JAR capturés dans l'ordre exact ;
- cas sans espace inchangé ;
- guillemets ou caractères de contrôle dans les valeurs refusés fail-closed s'ils rendent la ligne
  native ambiguë ;
- aucune base démarrée et aucun appel HTTP ;
- parse PowerShell, Pester ciblé et complet, Maven standard et intégration verts ;
- UTF-8, secrets, loopback, flags bloquants et `git diff --check` verts.

## 7. État initial

```text
WO044_STATUS=IN_PROGRESS
WO044_IMPLEMENTATION_COMMIT=NOT_CREATED
WO044_QUALIFICATION_RESULT=NOT_RUN
WO044_REPORT=NOT_CREATED
PR26_REVIEW_FINDING=P2_OPEN
PR26_MERGE_AUTHORIZED=NO
PR25_STATUS=OPEN
PR25_CLOSE_AUTHORIZED=NO
```

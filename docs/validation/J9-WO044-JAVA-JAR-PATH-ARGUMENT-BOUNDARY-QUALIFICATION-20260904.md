# J9 — qualification WO-044 de la frontière d'argument du chemin JAR WO-036

## 1. Identification

```text
WORK_ORDER=WO-SS-20260904-044-j9-wo036-java-jar-path-argument-boundary
BRANCH=codex/ss-20260904-044-j9-wo036-java-jar-path-argument-boundary
BASE_COMMIT=96dec8492dbc043c7d6e4310195421f4411722df
OPENING_COMMIT=ffbb41ccd64b27d103b7ef0caac85095ef26b9a9
IMPLEMENTATION_COMMIT=0b903b71ae70ccfb9b55f8d89bc8ac57eab0cbe3
QUALIFIED_AT_UTC=2026-09-04T09:23:49.3394323Z
QUALIFIED_AT_EUROPE_PARIS=2026-09-04T11:23:49.3439242+02:00
IMPLEMENTATION_QUALIFICATION=PASS_LOCAL_FAIL_CLOSED
OWNER_REVIEW_STATUS=READY_WITH_DISCLOSED_EXECUTION_DEVIATION
PR26_REVIEW_FINDING=P2_RESOLVED_LOCALLY_PENDING_OWNER_VALIDATION
```

Cette preuve porte exclusivement sur la sérialisation des arguments du processus Java dans
l'outillage de campagne synthétique WO-036, ses tests et sa documentation. Elle ne modifie ni le
code Java du Local Lab, ni le receiver INT-001, ni le contrat HTTP, ni une migration ou une
configuration Docker. Elle n'autorise aucun push, changement ou merge de PR, fermeture de PR,
accès à la base primaire, appel fournisseur/receiver, donnée J7 réelle, réseau distant, VPS ou
production.

## 2. Défaut reproduit

Avant WO-044, `Start-WO036ComponentCore` transmettait à `Start-Process` les deux valeurs
`'-jar', $jar`. Sous Windows, `Start-Process` recompose les éléments de `ArgumentList` en une ligne
de commande native. Le chemin canonique n'était pas explicitement protégé et un chemin tel que
`C:\...\WO044 Java JAR argument boundary ...\synthetic.jar` était reçu par le processus enfant
comme plusieurs arguments.

Le harnais hôte reproduit ce comportement avec un exécutable PowerShell de capture contrôlé et un
fichier JAR synthétique placé sous une racine temporaire réelle contenant des espaces. Cette
reproduction intervient sans lancer Java, une application, une base ou une requête HTTP.

```text
WO044_PRE_FIX_SPLIT_ARGUMENT_REPRODUCED=YES
```

## 3. Correction qualifiée

Le commit `0b903b71ae70ccfb9b55f8d89bc8ac57eab0cbe3` :

1. ajoute `ConvertTo-WO036JavaJarStartProcessArgument` dans le module de campagne WO-036 ;
2. exige une valeur non vide, un chemin Windows absolu et canonique et l'extension exacte `.jar` ;
3. refuse les guillemets, caractères de contrôle et chemins non canoniques susceptibles de rendre
   la ligne native ambiguë ;
4. protège explicitement le chemin qualifié par une paire de guillemets destinée à la ligne de
   commande native recomposée par `Start-Process` ;
5. conserve inchangés l'exécutable Java qualifié, l'identité d'instance, les trois garde-fous JDK,
   l'ordre de `-jar`, le répertoire de travail, les redirections et la propriété du processus ;
6. ajoute un enfant de capture strict et un harnais Windows qui vérifient les arguments réellement
   reçus par un processus distinct ;
7. ajoute cinq tests Pester couvrant le parse, l'appel de production, les cas avec et sans espace,
   les refus fail-closed et le parcours processus natif.

Le helper ne construit pas une commande shell et n'accepte aucun argument supplémentaire. La
correction se limite à la frontière de l'unique valeur située immédiatement après `-jar`.

## 4. Qualification hôte Windows

Commande qualifiée :

```text
pwsh -NoLogo -NoProfile -File scripts/wo044/Invoke-WO044JavaJarArgumentBoundaryQualification.ps1
```

Le harnais crée une racine temporaire canonique dont le nom contient des espaces, exécute cinq
captures réelles successives avec `Start-Process`, puis qualifie séparément un chemin sans espace
et quatre entrées ambiguës. Le cleanup ne vise que les processus dont l'identité, le PID, l'heure
de démarrage et la ligne de commande correspondent à la propriété enregistrée, puis uniquement la
racine synthétique exacte après validation de son confinement.

```text
WO044_HOST_QUALIFICATION=PASS_LOCAL_FAIL_CLOSED
WO044_PRE_FIX_SPLIT_ARGUMENT_REPRODUCED=YES
WO044_WINDOWS_PATH_WITH_SPACES_RECEIVED_AS_ONE_ARGUMENT=YES
WO044_QUALIFIED_ITERATIONS=5
WO044_NO_SPACE_PATH_UNCHANGED=YES
WO044_INSTANCE_ARGUMENT=PASS
WO044_JDK_RETRY_GUARDS=3_OF_3_PASS
WO044_JAR_SWITCH_AND_PATH_ORDER=PASS
WO044_AMBIGUOUS_PATH_CASES_REJECTED=4_OF_4
WO044_RESIDUAL_PROCESS_COUNT=0
WO044_RESIDUAL_TEMP_ROOT_COUNT=0
WO044_DATABASES_STARTED=NO
WO044_PROVIDER_CALLS=0
WO044_RECEIVER_HTTP_CALLS=0
WO044_REMOTE_NETWORK_OPENED=NO
```

`WO044_RESIDUAL_PROCESS_COUNT=0` décrit les processus exacts détenus par le harnais WO-044 ; il ne
prétend pas inventorier ou arrêter des processus tiers du poste Windows.

## 5. Tests et garde-fous

| Contrôle | Résultat |
|---|---|
| Parse des scripts PowerShell WO-044 | `PASS`, zéro erreur |
| Harnais hôte direct | `PASS_LOCAL_FAIL_CLOSED`, `5/5` itérations |
| Pester ciblé `WO044JavaJarArgumentBoundary.Tests.ps1` | `5/5 PASS` |
| Pester complet `scripts/Tests` | `95/95 PASS` |
| `mvnw.cmd --offline -DskipITs clean verify` | `BUILD SUCCESS` en `01:49`, Surefire `1136/0/0/5` |
| Failsafe du parcours Maven retenu | `SKIPPED_BY_EXPLICIT_DATABASE_GATE`, zéro rapport Failsafe produit |
| `mvnw.cmd -Pintegration-tests verify` | `NOT_RUN`, démarrage de base explicitement interdit par WO-044 |
| Garde Convention 1A de la branche | `PASS` |
| Garde `LOCAL_ONLY` et flags bloquants | `PASS` |
| Scan expurgé des blobs changés depuis la base | `PASS`, `8` blobs, zéro signal de secret à haute confiance |
| UTF-8 sans BOM des fichiers changés | `PASS`, zéro BOM |
| `git diff --check` | `PASS` |

La première tentative `mvnw.cmd clean verify` dans l'environnement restreint a échoué avant
compilation : le cache Maven de l'hôte n'y était pas accessible en écriture et Maven Central était
bloqué. Ce résultat ne qualifie pas le code. La commande retenue a donc été exécutée hors ligne à
partir du cache déjà présent sur l'hôte.

## 6. Écart d'exécution contenu et déclaré

Une première relance hôte a utilisé par erreur `mvnw.cmd --offline clean verify`, sans
`-DskipITs`. Dans ce dépôt, le cycle par défaut a alors atteint Failsafe et Testcontainers avant que
la contradiction avec `J9_WO044_DATABASE_START_AUTHORIZED=NO` soit visible. Deux conteneurs isolés
ont été démarrés :

```text
RYUK_CONTAINER_ID=9d518d7f65275904421f3a6d4421634b703a8c1b0565d26d9da3c3f736c96473
POSTGRES_CONTAINER_ID=fc935d3b32687dd6f04edb7902b0131a26b385d6b979d4378881ed767fcbc910
POSTGRES_RANDOM_LOOPBACK_PORT=51742
```

L'exécution a été interrompue dès l'observation du démarrage. Le postflight Docker, fondé sur les
deux identifiants exacts, a trouvé zéro conteneur résiduel. Le conteneur primaire exact
`betting-sofascore-local-lab-postgres` est resté `running`, `healthy`, avec un compteur de restart à
`0`. WO-044 n'a ni interrogé, ni arrêté, ni purgé la base primaire.

Cet événement constitue un écart à l'autorisation d'exécution, pas une réussite à masquer dans le
résultat. Les mesures sont donc distinguées explicitement :

```text
WO044_HARNESS_DATABASE_STARTS=0
WO044_ACCIDENTAL_ISOLATED_TESTCONTAINERS_DATABASE_STARTS=1
WO044_PRIMARY_DATABASE_TOUCHES=0
WO044_PRIMARY_DATABASE_PURGES=0
WO044_EXACT_TESTCONTAINERS_RESIDUAL_COUNT=0
WO044_PROVIDER_CALLS=0
WO044_RECEIVER_HTTP_CALLS=0
WO044_REMOTE_APPLICATION_NETWORK_CALLS=0
WO044_EXECUTION_DEVIATION=DISCLOSED_CONTAINED_ZERO_RESIDUE
```

Après ce postflight, seule la commande Maven munie de `-DskipITs` a été retenue ; elle a terminé en
`BUILD SUCCESS` sans démarrer une nouvelle base. La qualification d'implémentation demeure
`PASS_LOCAL_FAIL_CLOSED`, tandis que la readiness est volontairement présentée au propriétaire
avec cet écart déclaré.

## 7. Conclusion et portes maintenues

La frontière relevée dans la PR `#26` est reproduite puis corrigée : sur les cinq exécutions
hôte, le chemin JAR Windows contenant des espaces est reçu comme une valeur unique et exacte
immédiatement après `-jar`. Les valeurs ambiguës sont refusées avant démarrage et le harnais ne
laisse aucun processus détenu ni racine temporaire résiduelle.

WO-044 reste actif jusqu'à la revue propriétaire. La remarque P2 reste seulement résolue
localement ; le commit n'est pas poussé et la PR `#26` n'est ni mise à jour ni fusionnée.

```text
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
PR26_MERGE_AUTHORIZED=NO
PR25_CLOSE_AUTHORIZED=NO
```

# WO-061 — qualification de la CI locale et inventaire des quotas

Date : 15 septembre 2026. Base canonique : `59b4daedd089b778d1fd2560799f20dd16658cf1`.
Branche : `feature/V0.1.0-RC01-CODEX-WO-SS-20260915-061`, cible `feature/V0.1.0-RC01`.
Version Maven : `0.1.0-rc.1-SNAPSHOT`. Le rapport porte les changements de politique CI et leurs
gardes, sans changement applicatif, de persistance, de migration ou de collecte.

## État distant observé

Le [run GitHub 34950465172](https://github.com/djothepirate/betting-sofascore-local-lab/actions/runs/34950465172)
du train a réussi les tests Windows et Linux/PostgreSQL. Son job Linux `104319936308` a échoué
à l'étape d'upload du snapshot, après réussite du packaging, sur l'épuisement du quota Actions.
Ce run historique explique le problème ; il ne qualifie pas le nouveau candidat.

L'inventaire GitHub comportait 22 bundles intermédiaires, total **1 202 214 595 octets**.
Après consentement explicite du propriétaire dans cette session, les 22 identifiants ont été
relus via l'API ; noms et tailles correspondaient à l'inventaire avant suppression. Chaque
suppression a réussi. L'inventaire final indique `total_count=0`, `bytes=0`.
Les identifiants supprimés sont :

```text
9987853293 9974157367 9972784034 9972537714 9968568221 9932448781
9853706297 9824366913 9816424151 9816066373 9813869945 9813863467
9812717214 9812701370 9811604239 9811174298 9811144948 9810850135
9810198704 9810197969 9806789473 9806392285
```

Ils portaient tous le nom `sofascore-local-only-<run>-<SHA complet>`. Aucun rapport de test ou
distribution finale taguée ne figurait dans cette liste. Le recalcul du quota de facturation
n'est pas déclaré instantané. L'inventaire des caches compte séparément deux entrées,
18 249 921 octets, sans purge dans ce lot.

GitHub refuse l'accès aux règles/protections du dépôt privé avec un HTTP 403 mentionnant le
plan du compte. Aucune protection serveur nouvelle n'est donc revendiquée : les jobs échouent
sur erreur et la revue/fusion doit exiger les checks et preuves, conformément à AGENTS.

Le projet GitLab `85990106` présentait initialement `keep_latest_artifact=true`, environ 67 Mo d'artefacts,
`only_allow_merge_if_pipeline_succeeds=true`, `allow_merge_on_skipped_pipeline=false`,
`merge_method=ff`, `squash_option=never`, `auto_devops_enabled=false`. Ces observations sont
issues du connecteur GitLab. Les fonctionnalités de déploiement exposées par défaut ne sont
utilisées par aucun job du Lab.

Le réglage **Keep artifacts from most recent successful jobs** a été désactivé dans les
paramètres CI/CD GitLab après connexion par le bouton GitHub indiqué par le propriétaire.
L'interface et une relecture API confirment `keep_latest_artifact=false` ; les expirations déclarées ne seront plus prolongées
par cette conservation automatique. Aucune purge historique GitLab n'a été effectuée.

## Distinction de validation

- Tests Windows, Linux/PostgreSQL, gardes Git/localité/secrets et tests des lanceurs : obligatoires.
- XML Surefire/Failsafe, résumé Failsafe et rapport Secret Detection : preuves obligatoires
  dans les parcours qui les utilisent ; présence contrôlée et aucune tolérance de leur échec.
- Javadoc manuelle : seul export facultatif, sans tests dans son job ; une exigence explicite
  de rapport dans un WO impose un parcours de preuve bloquant distinct.
- Bundles intermédiaires : fabrication/archivage automatiques retirés des deux forges.
- Distribution finale taguée GitLab : conservée avec validation du tag protégé, des références
  exactes, de la version Maven, du SBOM, de la provenance et des SHA-256.

## Qualification locale

Environnement : Windows, Java 25.0.4, Maven Wrapper 3.9.16 ; Docker Desktop 29.7.2 disponible.
Les gardes Git/shell ont été exécutés avec Git Bash, qui résout le worktree Windows.
WSL Ubuntu fournit PyYAML 6.0.3 pour la lecture YAML ; son Git Linux ne résout pas le chemin
Windows enregistré dans ce worktree, donc ses commandes Git ne servent pas de preuve ici.

| Contrôle | Résultat |
| --- | --- |
| `powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/Verify-Local.ps1` | PASS ; exécute `mvnw.cmd -DskipITs clean verify`, 2 440 tests, 0 échec, 0 erreur, 5 ignorés ; 4 min 03 s |
| `java ci/VerifyTestReports.java standard` | PASS ; 2 435 tests effectivement exécutés, compteurs XML cohérents |
| `mvnw.cmd -Pintegration-tests verify` et garde `integration` | PASS ; standard 2 440 tests/5 ignorés, intégration 286 tests/0 ignoré, aucun échec ni erreur ; résumé Failsafe cohérent ; 13 min 33 s |
| `java ci/TestVerifyTestReports.java` | PASS, 26 scénarios dont rapports absents/vides/malformés, résultats contradictoires, erreurs, suites entièrement ignorées et XML externes interdits |
| `sh ci/test-branch-version.sh` | PASS ; contextes GitHub/GitLab, stable/RC/snapshot, refus des branches/version erronées |
| `sh ci/test-package-guards.sh` | PASS sur la version finale ; topologie PR/MR/tag, secrets, règles explicites MR, absence de bundles automatiques, reproductibilité et fixtures Dependency-Check |
| `pwsh -NoProfile -File ci/test-distribution-launchers.ps1` | PASS, `DISTRIBUTION_LAUNCHERS=PASS_DIRECT_JAR`, fixtures synthétiques |
| `pwsh -NoProfile -File scripts/wo056/Test-WO056WorkerArtifact.ps1` | PASS, `WO056_WORKER_ARTIFACT=PASS_OFFLINE` |
| Chargement YAML des deux workflows | PASS, PyYAML 6.0.3 |
| Validateur GitLab, éditeur de pipeline | `Pipeline syntax is correct` sur le candidat collé dans l'éditeur, configuration complète résolue avec le template Secret Detection ; aucun commit ni pipeline lancé par cet éditeur |
| Diff, localité et références documentaires | PASS, `git diff --check`, `LOCAL_ONLY_POLICY=PASS`, liens Markdown locaux et UTF-8 ; scan des blobs du commit à consigner après création |

Le premier lancement standard dans le bac à sable a échoué à résoudre le POM parent depuis
Maven Central, avant les tests (accès réseau interdit). La réexécution autorisée hors de cette
restriction a réussi. Aucun échec de test n'a été masqué pour obtenir ce résultat.

## Revue indépendante

Trois sous-tâches ont audité GitHub, GitLab et les preuves. Les points suivants ont été corrigés :

1. Les jobs Maven GitLab héritent de règles explicites incluant les MR ; sans cela le défaut
   GitLab `except: merge_requests` pouvait omettre les tests et le garde de promotion.
2. Le résumé Failsafe exigé figure aussi dans les chemins conservés.
3. Le rapport Secret Detection est exigé après `/analyzer run` dans `script`, avec `test -s`.
   L'absence d'un artefact GitLab n'étant pas une preuve d'échec du job, le contrôle est explicite.
4. Les lancements manuels conservent un garde branche/version Maven malgré le retrait du packaging.
5. Les tests des gardes sont exécutés par la CI ; Javadoc ne peut absorber aucun échec de tests.

La règle MR est documentée dans les [règles de jobs GitLab](https://docs.gitlab.com/ci/jobs/job_rules/).
La vérification de preuve est placée dans `script`, car une erreur d'`after_script` ne change
pas le résultat du script réussi : [référence GitLab](https://docs.gitlab.com/ci/yaml/#after_script).

## Fichiers du lot et activation

Workflows GitHub/GitLab ; gardes et tests dans `ci/` ; AGENTS, ADR-SS-004, README, changelog,
modèle de PR, guide CI, présent rapport et Work Order. Aucun fichier applicatif ni migration.

Les nouveaux déclenchements s'appliquent aux références qui intègrent ces fichiers. La branche
du candidat seule n'actualise pas `main`, le train ou le miroir GitLab. La PR de WO cible le
train exact ; revue humaine puis fusion restent nécessaires. La validation distante du
candidat doit être distinguée de la qualification locale et des runs historiques.

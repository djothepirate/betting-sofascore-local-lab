# WO-SS-20260906-056 — Snapshots reconstruisibles du premier train RC01

- **Statut :** READY_FOR_REVIEW — validation locale réalisée, checks distants et revue humaine du candidat requis
- **Date :** 2026-09-06
- **Branche :** `feature/V0.1.0-RC01-CODEX-WO-SS-20260906-056`
- **Cible de PR :** `feature/V0.1.0-RC01`
- **SHA de départ exact du train :** `3d2f9da1479898e19df7fcfe8ccacdaf17ce8452`
- **Base qualifiée :** merge commit de la PR #32, run GitHub Actions `34025248352` vert
- **Autorité :** décision propriétaire après revue humaine des PR #13/#32 ; premier train RC01,
  Maven `0.1.0-rc.1-SNAPSHOT`, rebuilds autorisés, POM finalisé par PR GitHub avant la MR GitLab
- **Réseau fournisseur :** `NO` ; **VPS / production :** `FORBIDDEN`

## Objectif et contrat

Développer le premier train RC01 sous Maven `0.1.0-rc.1-SNAPSHOT` et reconstruire cette même
version autant de fois que nécessaire depuis la branche d'intégration exacte. Les bundles
durables gardent leur IID de pipeline, leur SHA et la classification locale.

Une PR de finalisation ultérieure vers la feature retirera `-SNAPSHOT` dans le POM versionné,
avec `0.1.0-rc.1`. La PR finale `feature/V0.1.0-RC01 -> main` et la MR GitLab fast-forward
`feature/V0.1.0-RC01 -> release/V0.1.0-RC01` exigent cette version finalisée. Aucun changement de
POM à la volée dans le build GitLab ni commit de finalisation direct dans la release.

Le nouveau mapping `rc.N-SNAPSHOT` ou `rc.N` est limité aux features RC simples et à leurs WO.
Les branches `RCnn-SNAPSHOT` gardent leur mapping snapshot strict et restent non taguables ; les
gardes de PR finale, MR, release RC simple et tag restent stricts. Un rebuild normal ne dépend
pas de l'amorçage (`source.train.seed=false`) ni d'une égalité persistante avec `main`.

## Périmètre et limites

- Version du POM et trois métadonnées runtime affichant encore l'ancienne version ; gardes PR WO
  et packaging, contre-épreuves, README, AGENTS, ADR-SS-004 et changelog.
- Résolution exacte des JAR worker J3/J4/J5 depuis `project.build.finalName` du même profil Maven,
  avec refus d'un artefact absent et d'un nom ambigu, au lieu du nom `0.1.0-SNAPSHOT` figé.
- Tests des résolutions sur un chemin contenant des espaces, d'un ancien worker résiduel et des
  noms invalides, raccordés à la CI Windows ; garde d'isolation owner-go WO-050 réexécutée hors
  ligne, déjà incluse dans le standard Windows par son test Java.
- Correction bornée des canaris synthétiques historiques qui bloquent le scan complet au seed,
  et de la voie NVD API sans clé de Dependency-Check, révélées pendant la qualification.
- Pas de modification des versions de fixtures ou rapports historiques, des outils WO-036,
  d'ADR-SS-001, de migration, de données, de campagne, de lancement fournisseur ou de permission.
- `EXPERIMENTAL`, `LOCAL_ONLY`, `NOT_PRODUCTION_APPROVED`, `NO_CRITICAL_DEPENDENCY`,
  `production.approved=false` et `vps.deployable=false` demeurent applicables.

## Critères et preuves attendues

- [x] La feature RC01 et ses WO acceptent Maven `0.1.0-rc.1-SNAPSHOT` ou `0.1.0-rc.1`.
- [x] Une version d'un autre RC/cœur, malformée, de casse incorrecte, ou à double `-SNAPSHOT`
  est refusée ; PR finale, MR, release RC simple et tag refusent la version snapshot.
- [x] Les rebuilds GitHub `created=false` et GitLab à before-SHA non nul sont reproductibles,
  locaux et portent `source.train.seed=false`, même avec une feature au-delà de `main`.
- [x] Le worker exact est résolu ; un worker d'ancienne version ne remplace jamais un JAR absent.
- [x] Les 72 scénarios hors ligne de l'isolation owner-go WO-050 réussissent.
- [x] Le standard Windows et la matrice shell finale réussissent.
- [ ] Le scan historique complet et les checks distants réussissent sur le commit candidat publié.
- [ ] La PR du candidat vers la feature reçoit ses checks Windows/Linux et sa revue humaine.

## Commandes et résultats

- `scripts/wo056/Test-WO056WorkerArtifact.ps1` : `WO056_WORKER_ARTIFACT=PASS_OFFLINE`.
- `scripts/wo050/Test-WO050LauncherIsolation.ps1` : 72 scénarios, succès ; aucun appel métier
  réel, Maven, Docker ou fournisseur, uniquement les doubles et environnements synthétiques.
- `scripts/Verify-Local.ps1` : `VERIFY_RESULT=PASS`, 1193 tests, zéro échec/erreur et 5 skips,
  durée 2 min 03 s. Commande standard réelle `mvnw.cmd -DskipITs clean verify`, Failsafe
  explicitement omis ; intégration non requise car aucune persistance modifiée. Le premier essai
  a été bloqué par le bac à sable lors de la résolution Maven ; reprise autorisée réussie.
- `sh ci/test-package-guards.sh` sous WSL : `RELEASE_REPRODUCIBILITY=PASS_LOCAL_ONLY` et
  `PACKAGE_GIT_GUARDS=PASS_LOCAL_ONLY`, fixtures Git hors réseau et Maven simulé pour les
  versions, provenance, tags et graphes de PR/MR. La dernière exécution inclut le correctif
  scanner, le garde `cache: []` et le helper NVD avec
  `DEPENDENCY_CHECK_ARGUMENTS=PASS_LOCAL_ONLY_OBSERVATION` ; sortie `0`.
- Évaluation Maven réelle `--offline -q -Pprovider-playwright-runtime -DforceStdout
  -Dexpression=project.build.finalName help:evaluate` : une ligne exacte
  `betting-sofascore-local-lab-0.1.0-rc.1-SNAPSHOT`. Lecture seule du modèle avec le cache
  utilisateur, aucun packaging ou lancement du worker.
- `sh -n` de chaque script `ci/*.sh`, parseur des cinq fichiers PowerShell ajoutés/modifiés et
  `git diff --check` : succès sur le diff courant.

Le helper Dependency-Check conserve la version 13.0.0, le seuil CVSS 11 et le job
`allow_failure=true`. Il sélectionne le flux officiel NVD JSON 2.0 sans option d'authentification,
avec `failOnError=true`, des rapports HTML/JSON/GITLAB et une base éphémère propre au job.
L'unique déclaration de cache admise est `cache: []` dans ce job ; aucun cache partagé n'est
réutilisé. Sa qualification porte sur clés absente/vide/synthétique, arguments exacts, chemins
avec espaces, priorités de répertoire et propagation du code d'échec 42 ; la preuve d'une analyse
NVD réelle sur le candidat restera à obtenir dans GitLab.
Le choix du flux sélectionne la voie datafeed de la version 13, comme le montre le
[code officiel de NvdApiDataSource](https://raw.githubusercontent.com/dependency-check/DependencyCheck/v13.0.0/core/src/main/java/org/owasp/dependencycheck/data/update/NvdApiDataSource.java).
Le chargement initial de cette base peut être long. La rétention des snapshots reste 14 jours
sur GitHub et 30 jours sur GitLab, sans modification dans ce lot.

## États distants distincts du candidat

Le `main` de départ est qualifié par GitHub. Le seed de la feature (run `34025499922`) a échoué
au scan historique : un blob synthétique ancien de la qualification Playwright J5 apparaît dans
trois commits et était hors de l'exception. Après comparaison des deux lignes canaris avec le
blob déjà audité, le scanner ajoute seulement le blob exact
`ba6e319cc14b05417157522b8dd90e7abd3e2528` au chemin exact
`scripts/Invoke-J5PlaywrightLoopbackQualification.ps1`. Les mutations, même transitoires, et le
déplacement vers un autre chemin restent refusés ; les contre-épreuves dédiées passent.
Le run seed initial n'est pas déclaré vert et la feature n'est pas encore synchronisée vers GitLab.
Le scan complet local des 387 commits atteignables depuis le HEAD de départ est encore en cours
au gel de ce document ; il n'est pas revendiqué vert. La publication d'une nouvelle branche
déclenchera également le scan complet GitHub sur le commit candidat exact, preuve nécessaire
avant toute fusion ou synchronisation de la feature.

Le pipeline GitLab du `main`, `2824036681`, passe la validation locale, le standard/intégration
Linux et les scanners secrets/SAST selon le contrôle courant. Dependency-Check échoue avec une
erreur NVD de clé vide sous `allow_failure=true` ; cette limite ne vaut pas qualification positive
de l'analyse de dépendances. L'état global final et le pipeline du candidat restent distincts.

## Suite

Proposer le candidat par PR vers sa feature exacte après qualification. Sa clôture sera effective
après la fusion revue de cette PR. Qualifier le nouveau sommet feature avant sa synchronisation
contrôlée GitLab et son snapshot durable. La finalisation Maven `0.1.0-rc.1`, la PR vers `main`,
la MR de release et le tag seront des opérations ultérieures portant leurs propres preuves.

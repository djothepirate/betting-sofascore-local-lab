# Note de diagnostic — incident CI WO-061

**Cas : CS-H01 — état documentaire au 15 septembre 2026**

## Conclusion

L’incident documenté est un **échec d’archivage d’un snapshot dû au quota GitHub Actions**, survenu après la réussite des tests et du packaging. Il ne démontre pas une régression applicative. Le run historique reste néanmoins en échec : le retrait ultérieur des bundles intermédiaires automatiques ne transforme pas son résultat en succès.

La qualification disponible comprend un **succès historique des tests distants rapporté dans WO-061**, une **qualification locale détaillée**, et une **validation de configuration**. Elle ne suffit pas à qualifier une nouvelle révision : aucun candidat nouveau, run associé, SHA effectivement extrait ou ensemble de rapports bruts n’est fourni.

Les configurations actuelles réduisent la consommation de stockage tout en conservant les tests et leurs preuves obligatoires. **Un nouvel échec d’upload des XML GitHub resterait bloquant**, même avec des tests verts.

### Périmètre et sources

Le [skill demandé][S] a été lu intégralement avec un outil local. L’analyse repose exclusivement sur [l’entrée du cas][I], le [rapport WO-061][H], l’[ADR-SS-004][A], le [runbook CI][R] et les configurations [GitHub][G] et [GitLab][L] autorisées. Aucun réseau, build, test, scanner, service ou changement de fichier n’a été exécuté.

Les scripts des gardes, `AGENTS.md`, les rapports bruts et le template GitLab inclus ne font pas partie du corpus accessible. Les commandes et opérations relatées ci-dessous sont des **faits historiques rapportés ou des configurations lues**, sans réexécution.

Les invariants restent : **EXPERIMENTAL · LOCAL_ONLY · NOT_PRODUCTION_APPROVED · NO_CRITICAL_DEPENDENCY**.

## 1. Incident, remédiation et conséquences

### Échec GitHub établi par le rapport

Le [rapport WO-061, section « État distant observé »][H] rattache l’incident au :

- **Run GitHub : `34950465172`**, décrit comme un run du train.
- **Job Linux : `104319936308`**.
- **Résultat des tests :** Windows et Linux/PostgreSQL réussis.
- **Packaging :** réussi.
- **Étape défaillante :** upload du snapshot, sur épuisement du quota Actions.

Le rapport ne fournit pas le SHA testé, l’événement déclencheur exact, la tentative, l’identifiant du job Windows, les versions détaillées de ces environnements distants ou leurs totaux de tests. Le 15 septembre 2026 est la date du rapport ; la date précise du run n’y est pas explicitée.

**Conséquence :** les tests réussis et l’échec de conservation doivent être comptés séparément. L’upload défaillant identifié concernait le **snapshot** ; le corpus ne permet pas de le requalifier en échec d’upload des XML de tests.

### Stockage GitHub : purge historique précisément délimitée

WO-061 rapporte :

| Catégorie | État et opération historiques | Portée de la preuve |
|---|---|---|
| Bundles intermédiaires | **22 bundles**, soit **1 202 214 595 octets** ; identifiants, noms et tailles relus avant suppression ; chaque suppression réussie | Inventaire final rapporté : `total_count=0`, `bytes=0` |
| Nature des éléments supprimés | Noms `sofascore-local-only-<run>-<SHA complet>` | Aucun rapport de test ni distribution finale taguée dans cette liste |
| Caches Actions | **2 entrées**, **18 249 921 octets** | Inventaire distinct ; aucune purge des caches dans ce lot |
| Quota de facturation | Recalcul potentiellement différé | La suppression ne prouve pas une capacité d’upload immédiatement rétablie |

La purge était autorisée dans la session historique relatée. Elle n’établit ni l’état actuel du stockage, ni une autorisation pour une autre purge. L’entrée du cas exclut toute récupération des artefacts supprimés. [Source : WO-061][H]

### GitLab : modification de rétention, sans purge

Pour le projet **`85990106`**, le rapport observe initialement environ **67 Mo d’artefacts** et `keep_latest_artifact=true`. La désactivation de la conservation automatique du dernier pipeline réussi est ensuite confirmée, historiquement, par l’interface et une relecture API : **`keep_latest_artifact=false`**. **Aucune purge historique GitLab n’a été effectuée.**

Les paramètres suivants sont également rapportés :

- `only_allow_merge_if_pipeline_succeeds=true` ;
- `allow_merge_on_skipped_pipeline=false` ;
- `merge_method=ff` ;
- `squash_option=never` ;
- `auto_devops_enabled=false`.

Ce sont des observations datées, sans pipeline ni SHA associé dans le rapport. Elles ne prouvent pas l’état serveur actuel. Côté GitHub, l’accès aux règles/protections avait échoué avec **HTTP 403 lié au plan du compte** : aucune nouvelle protection serveur n’est revendiquée. [Source : WO-061][H]

### Changement de politique issu de WO-061

L’[ADR amendé][A] et le [runbook][R] établissent :

- le retrait de la fabrication et de l’archivage automatiques des bundles intermédiaires sur les deux forges ;
- le maintien des tests, gardes et preuves exigées comme contrôles bloquants ;
- une rétention courte des XML de tests de **trois jours** ;
- une Javadoc manuelle facultative, conservée **un jour** ;
- une conservation distincte des distributions finales taguées GitLab ;
- l’absence de nouveau cache Maven Actions dans cette CI ;
- le retrait des observations automatiques SAST, JaCoCo/PMD/CPD et Dependency-Check comme exigences générales du Lab.

Ces changements réduisent les uploads volumineux et les exécutions redondantes. Ils ne suppriment pas le risque de quota sur les preuves obligatoires.

## 2. Révisions et qualification effectivement disponibles

### Références à ne pas confondre

| Référence | Valeur | Signification établie |
|---|---|---|
| Base canonique de WO-061 | `59b4daedd089b778d1fd2560799f20dd16658cf1` | Base du lot et argument du contrôle historique de secrets ; **pas le SHA démontré du run GitHub** |
| Dernier changement fonctionnel du lot | `a3ed5e7c854369f82d74291255fdcefdb3727792` | Commit d’implémentation cité dans WO-061 ; le bilan suivant ne modifie que le rapport |
| Provenance de politique déclarée par `input.json` | `57c0614627e628077b0a7775eb66a7c490b422e0` | Identifiant de provenance fourni par l’entrée ; aucune résolution Git effectuée |
| SHA testé par le run `34950465172` | **Non fourni** | Aucun rattachement exact possible |
| Nouvelle révision à qualifier | **Non fournie** | Aucun résultat historique transférable automatiquement |

Le lot documenté porte la branche `feature/V0.1.0-RC01-CODEX-WO-SS-20260915-061`, avec pour cible `feature/V0.1.0-RC01`. Sa **version de projet Maven** est `0.1.0-rc.1-SNAPSHOT`. Elle est distincte de la version de l’outil Maven.

### Tableau des preuves historiques

**Lecture des états :** `PASS` et `FAIL` désignent ici les résultats consignés par WO-061, dans leur portée propre. Ils ne constituent pas une nouvelle vérification des fichiers ou des exécutions.

L’environnement local déclaré est **Windows, Java 25.0.4, Maven Wrapper 3.9.16**, avec **Docker Desktop 29.7.2 disponible**. Les gardes Git/shell utilisent **Git Bash**. WSL Ubuntu fournit **PyYAML 6.0.3**, mais son Git ne résout pas ce worktree Windows et n’est pas retenu comme preuve Git.

| Contrôle historique | État | Run, SHA et environnement | Preuve disponible | Limite |
|---|---|---|---|---|
| Tests distants Windows et Linux/PostgreSQL | **PASS** | GitHub `34950465172` ; SHA non fourni ; environnements connus seulement par ces familles | Succès explicitement rapporté | Aucun XML, journal brut ou total distant dans le corpus |
| Upload du snapshot | **FAIL** | Même run ; job Linux `104319936308` | Quota Actions épuisé après packaging réussi | Ne démontre pas un échec applicatif ou un échec d’upload XML |
| Premier lancement standard local | **FAIL** pour la résolution de dépendance ; tests **NOT_EXECUTED** | Bac à sable avec réseau interdit ; SHA exact non consigné | POM parent non résolu depuis Maven Central avant les tests | Incident d’accès aux dépendances ; aucun résultat de test issu de cette tentative |
| Réexécution standard locale | **PASS** | Environnement Windows ci-dessus ; SHA exact de l’exécution non consigné | `Verify-Local.ps1` exécute `mvnw.cmd -DskipITs clean verify` : **2 440 tests, 0 échec, 0 erreur, 5 ignorés**, durée **4 min 03 s** | Réexécution historiquement autorisée hors restriction réseau ; rapports bruts absents |
| Vérification XML standard | **PASS** | Même qualification locale ; SHA exact non consigné | `VerifyTestReports.java standard` : **2 435 tests effectivement exécutés**, compteurs cohérents | Résultat du garde rapporté, sans inspection actuelle du code ni des XML |
| Intégration locale | **PASS** | Windows, profil `integration-tests` ; SHA exact non consigné | `mvnw.cmd -Pintegration-tests verify` et garde `integration` : standard **2 440/5 ignorés**, intégration **286/0 ignoré**, aucun échec ni erreur, résumé Failsafe cohérent ; **13 min 33 s** | Ne constitue pas un run Linux distant ; version PostgreSQL et rapports bruts non fournis |
| Gardes et fixtures | **PASS** | Git Bash pour les gardes shell ; commandes `pwsh` pour les lanceurs ; SHA exact par exécution non consigné | **26 scénarios** du validateur XML ; tests branche/version, packaging/provenance, lanceurs et worker hors ligne | Les fixtures synthétiques, notamment de reproductibilité et Dependency-Check, ne prouvent ni un nouveau bundle réel ni un scan réel |
| Contrôle de secrets | **PASS** — libellé historique `PASS_HIGH_CONFIDENCE` | Base `59b4…`; commit d’implémentation `a3ed…` explicitement cité | Scan rapporté des **1 769 fichiers versionnés** et du commit d’implémentation | Portée historique ; script, détails des exemptions et sortie brute non accessibles |
| YAML et validation GitLab | **PASS**, limité à la configuration | PyYAML 6.0.3 ; candidat collé dans l’éditeur GitLab, sans SHA exact indiqué | Chargement des deux YAML ; message `Pipeline syntax is correct`, template Secret Detection résolu historiquement | Aucun commit ni pipeline lancé par cet éditeur ; aucune exécution démontrée |
| Diff, localité et documentation | **PASS** | Qualification locale du lot | `git diff --check`, `LOCAL_ONLY_POLICY=PASS`, liens Markdown et UTF-8 | Résultats consignés, sans réexécution présente |

Source de l’ensemble des résultats : [WO-061, sections « Qualification locale » et « Fichiers du lot et activation »][H]. Les codes de sortie natifs numériques et journaux complets ne sont pas fournis.

## 3. Confrontation aux contrôles actuels

### Ce que montrent les YAML autorisés

**GitHub :**

- Déclenchements `pull_request` — `opened`, `synchronize`, `reopened`, `edited` — et `workflow_dispatch`, sans déclenchement général sur push ou tag.
- Deux jobs sur **`windows-2022`** et **`ubuntu-24.04`**, avec Java 25 configuré.
- Gardes XML et uploads sous `always()`, absence de fichiers traitée comme erreur, rétention de trois jours.
- Aucun packaging de bundle intermédiaire, malgré le nom conservé du job Linux **« Linux — sécurité, PostgreSQL et distribution locale »**.
- Annulation des exécutions remplacées via `cancel-in-progress: true`. Un run annulé n’est pas une qualification réussie. [Configuration GitHub][G]

**GitLab :**

- Pipelines admis pour MR, push de tag et Web ; les autres sources sont refusées par les règles du workflow.
- Règles MR explicitement présentes dans le socle des jobs Maven, avec `allow_failure: false`.
- Résumé `failsafe-summary.xml` inclus dans les chemins conservés.
- Secret Detection exécute `/analyzer run`, puis **`test -s gl-secret-detection-report.json` dans `script`**, avec `allow_failure: false`.
- Javadoc seule tolérante, manuelle, sans suite de tests dans son job.
- Seul job concret de distribution conditionné à un tag SemVer protégé ; rétention `never`. Le packaging utilise `-DskipTests`, tandis que les tests obligatoires demeurent dans le stage précédent. [Configuration GitLab][L]

Les cinq corrections de la revue WO-061 ont donc une traduction visible : règles MR, conservation du résumé Failsafe, contrôle du rapport Secret Detection, gardes branche/version des lancements manuels et exécution CI des tests des gardes.

**Limite de cette concordance :** lire les appels aux gardes ne prouve pas leur implémentation. De même, l’inclusion du template Secret Detection ne permet pas de reconstituer aujourd’hui sa configuration complète avec ce corpus.

### Grille de qualification d’une nouvelle révision

Pour les contrôles requis ci-dessous, **`NOT_EXECUTED` signifie qu’aucune preuve d’exécution rattachée au nouveau candidat n’est fournie**. Cela ne constitue pas l’affirmation qu’aucune exécution distante n’existe.

| Contrôle | Exigence actuelle | État | SHA | Preuve disponible | Limite | Travail restant |
|---|---|---|---|---|---|---|
| Identité du candidat et activation de la politique | Révision et références exactes intégrant les fichiers CI | **NOT_EXECUTED** | Non fourni | Routes documentées ; YAML lisibles | La branche WO seule n’actualise ni le train, ni `main`, ni GitLab | Identifier tête, base, checkout, événement, run/job/tentative et références concernées |
| Routes PR/MR et version | WO → train exact ; train finalisé → `main` ; promotion GitLab conforme | **NOT_EXECUTED** | Non fourni | Appels aux gardes PR, MR, référence et version | Scripts et graphe absents ; protections serveur actuelles non établies | Vérifier la route, le graphe et la version avec les preuves du candidat |
| Tests standards et intégration | Deux jobs GitHub sur PR/manuelle ; Linux/PostgreSQL sur parcours GitLab applicable | **NOT_EXECUTED** | Non fourni | Commandes et environnements configurés | Aucun résultat du candidat ; versions effectives à relever | Obtenir les résultats bloquants et les commandes effectives avec codes natifs |
| Validité des rapports | Surefire ; Failsafe et résumé pour l’intégration ; tests réellement exécutés | **NOT_EXECUTED** | Non fourni | Gardes invoqués ; exigences documentées | Aucun XML fourni ; implémentation du validateur non lue | Vérifier présence, lisibilité, compteurs, absence d’erreurs/échecs et exécution non nulle ; contrôler résumé, timeout et résultat Failsafe |
| Conservation des preuves | Upload XML GitHub obligatoire ; rapports GitLab effectivement accessibles ; trois jours | **NOT_EXECUTED** | Non fourni | Chemins et rétentions explicites | Disponibilité des rapports, capacité d’upload et réglage GitLab actuels inconnus | Vérifier l’upload et l’accès ; organiser la conservation nécessaire avant expiration |
| Localité, secrets et fixtures obligatoires | Gardes et tests des lanceurs bloquants dans leurs parcours | **NOT_EXECUTED** | Non fourni | Appels visibles ; succès historiques documentés | Aucun résultat du candidat ; exemption GitLab par chemin insuffisante pour valider un nouveau blob | Vérifier le périmètre exact du scan, les exemptions auditées et les résultats du candidat |
| Secret Detection GitLab | Analyseur réussi et rapport non vide dans le parcours GitLab applicable | **NOT_EXECUTED** | Non fourni | `test -s` dans `script`, `allow_failure: false` | Aucun rapport ni configuration incluse résolue actuelle | Obtenir et examiner le rapport, ses signaux et sa disponibilité |
| Bundles intermédiaires automatiques | Obligation supprimée par WO-061 | **NOT_APPLICABLE** | — | Absence de job concret correspondant dans les YAML | L’absence d’un bundle intermédiaire n’est pas une preuve manquante | Ne pas en rétablir implicitement pour qualifier la révision |
| Scans automatiques d’observation retirés | Aucune obligation générale SAST/JaCoCo/PMD/CPD/Dependency-Check | **NOT_APPLICABLE** | — | Retrait explicite dans ADR/runbook | Ne vaut pas approbation de sécurité ; aucun scan réel fourni | Si une analyse explicite est exigée ultérieurement, établir exécution, données utilisées, rapport et traitement des signaux |
| Javadoc facultative | Export manuel, sauf exigence explicite d’un WO | **NOT_APPLICABLE** | — | Aucun besoin Javadoc dans ce cas | Un rapport explicitement exigé reste bloquant, même si un job facultatif existe | Définir les preuves supplémentaires propres au futur WO |
| Distribution finale | Non demandée dans cet audit | **NOT_APPLICABLE** | — | Parcours tagué conservé dans GitLab | Aucun nouveau JAR, SBOM, manifeste, contrôle de sommes ou preuve de reproductibilité | En cas de demande, qualifier séparément références, version, provenance, SBOM, SHA-256 et reproductibilité réelle |

## 4. Travail restant pour obtenir une qualification complète

Pour une qualification ultérieure, hors de cet audit documentaire, les éléments nécessaires sont les suivants :

1. **Fixer l’identité de la révision.** Fournir le SHA candidat, les références source/cible et le SHA effectivement extrait. Une PR GitHub peut tester un commit de merge distinct de sa tête : établir ses parents et sa relation au candidat. Ne remplacer aucun SHA manquant par la base de WO-061.

2. **Prouver la route et l’activation des fichiers CI.** Pour une promotion GitLab, établir l’historique complet, l’égalité du checkout avec le SHA source canonique, l’alignement de feature et `origin/main`, la cible release ancêtre, le même train/projet et les protections requises. Pour le train RC01, la version finale attendue est `rc.1` : sa finalisation doit être versionnée dans la route canonique.

3. **Produire les résultats et rapports propres au candidat.** Conserver plateforme, événement, run/job/tentative, versions effectives, commandes, codes natifs et totaux. Les anciens succès et les fixtures ne remplacent pas cette preuve.

4. **Vérifier la conservation avant de conclure.** Constater l’accès réel aux XML, au résumé Failsafe et au rapport Secret Detection applicable. Préparer la synthèse durable du WO et, si nécessaire, la conservation des XML au-delà des trois jours. En cas d’échec de génération ou d’upload, corriger sa cause avant une relance utile.

5. **Compléter la revue avec les preuves absentes du corpus.** Examiner les gardes exacts, la configuration GitLab résolue, les paramètres serveur applicables et les éventuels signaux de sécurité. Pour une distribution, distinguer intégrité, reproductibilité et analyse de vulnérabilités : un SBOM présent ou un fichier `SHA256SUMS` écrit ne prouve pas à lui seul leur vérification.

**Décision finale :** incident historique expliqué ; succès des tests et qualification locale documentés ; conservation historique du snapshot en échec ; corrections de configuration concordantes avec WO-061. **La qualification globale d’une nouvelle révision reste `NOT_EXECUTED` dans le corpus fourni.** Aucune promotion, distribution, publication ou installation nouvelle n’est attestée.

[S]: <C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-ci-security/run-01/CS-H01/.agents/skills/ss-ci-security/SKILL.md>
[I]: <C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-ci-security/run-01/CS-H01/input.json>
[H]: <C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-ci-security/run-01/CS-H01/docs/validation/WO061-CI-LOCAL-QUOTAS-20260915.md>
[A]: <C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-ci-security/run-01/CS-H01/ADR-SS-004-integration-continue-et-distribution-locale-uniquement.md>
[R]: <C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-ci-security/run-01/CS-H01/docs/runbooks/CI-LOCAL-QUOTAS.md>
[G]: <C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-ci-security/run-01/CS-H01/.github/workflows/ci.yml>
[L]: <C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-ci-security/run-01/CS-H01/.gitlab-ci.yml>
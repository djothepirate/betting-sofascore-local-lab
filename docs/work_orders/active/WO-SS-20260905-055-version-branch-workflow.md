# WO-SS-20260905-055 — Workflow Git par trains de version

- **Statut :** READY_FOR_REVIEW — fusion humaine requise
- **Date d'ouverture :** 2026-09-05
- **Branche :** `codex/ss-20260905-055-version-branch-workflow`
- **Base exacte :** `054fa4ca9301224aa5f96f478136208d2327d7f0`
- **Autorité :** décision propriétaire de remplacer la convention main-only par des trains de
  version et de borner les PR GitHub et MR GitLab.
- **Réseau fournisseur :** `NO`
- **VPS :** `FORBIDDEN`
- **Production :** `FORBIDDEN`

## Objectif

Installer, dans le Local Lab, une convention Git versionnée commune aux deux dépôts : travaux de
Work Order intégrés par PR GitHub dans un train feature, consolidation du train dans `main`, puis
promotion contrôlée par MR GitLab vers une release protégée. Autoriser des snapshots exécutables
locaux depuis le seul train qualifié sans créer de canal VPS ou production.

## État de départ établi

- `main` GitHub et local désignent la base exacte ci-dessus ; son run GitHub Actions
  `33982273545` est vert sous Windows et Linux.
- Le projet Maven porte `0.1.0-SNAPSHOT`.
- Aucune PR GitHub n'est ouverte et aucune branche `feature/V0.1.0` ou `release/V0.1.0` n'est créée
  par ce lot.
- Les anciennes branches restent des références historiques non protégées ; elles ne sont ni
  renommées, ni rebasées, ni supprimées.
- GitHub autorise uniquement les merge commits (`allow_merge_commit=true`, squash et rebase
  désactivés) ; la suppression automatique des branches reste désactivée.
- GitLab est déjà configuré avec `merge_method=ff`, `squash_option=never`, pipeline vert et
  discussions résolues obligatoires. La protection effective de `release/V*` reste une preuve
  administrative à contrôler lors de la création des nouvelles références, comme la règle
  distincte de tags protégés `v*` requise par les promotions.

## Décisions applicables

1. GitHub est canonique pour `main`, `feature/*`, les branches WO, PR et tags. `release/V*` reste
   GitLab-only et protégée.
2. Le train commun aux branches feature et release vaut exactement `VX.Y.Z`, `VX.Y.Z-RCnn` ou
   `VX.Y.Z-RCnn-SNAPSHOT`, avec cœur sans zéro initial et `nn` compris entre `01` et `99`.
3. Une branche WO porte exactement
   `feature/<TRAIN>-(CODEX|HUMAN)-WO-SS-YYYYMMDD-NNN` et part du train de même version.
4. Une PR WO cible son train exact. La clôture d'un ou plusieurs WO listés devient effective après
   fusion, jamais par le seul classement documentaire préalable.
5. La PR finale `feature -> main` exige la version Maven finale du train et utilise un merge commit.
   Le train avance ensuite en fast-forward au merge commit de `main` ; les deux références exactes
   sont synchronisées vers GitLab.
6. La MR GitLab interne `feature -> release` porte le même train, exige une cible protégée et la
   version Maven correspondante, un sommet source identique à `origin/main` et au SHA source
   canonique, une cible ancêtre et un historique complet ; elle reste fast-forward-only et interdit
   squash/rebase.
7. Seul un push du train feature exact conserve un snapshot durable. Tout autre bundle reste
   éphémère.
8. Le bootstrap courant est la seule exception de nommage : il cible `main` sur la base exacte de
   ce WO avec Maven `0.1.0-SNAPSHOT`, et son SHA source doit en descendre avec la même merge-base
   exacte. Les branches historiques sont read-only et inéligibles aux nouveaux travaux, snapshots
   et promotions. Tout pipeline de branche GitLab refuse également toute branche WO, bootstrap ou
   historique ; les routes MR et tag restent distinctes et tout contexte indéterminé échoue fermé.
9. La notation de branche `RCnn` se convertit en préversion SemVer Maven/tag `rc.N` : par exemple
   `RC01` devient `rc.1`. Un train `RCnn-SNAPSHOT` exige Maven `rc.N-SNAPSHOT` et ne produit aucun
   tag ; seuls les trains `RC01` à `RC99` sont promouvables.
10. Seules les branches `release/V*` sont protégées. La règle de tags protégés `v*`, distincte des
    protections de branches, est obligatoire avant toute promotion taguée GitLab.

## Périmètre

- amendement ADR-SS-004, AGENTS, README, changelog et modèle de PR ;
- gardes et contre-épreuves de noms de branche, PR GitHub et MR GitLab ;
- triggers GitHub et règles GitLab de snapshot ;
- concordance branche/version Maven et tag/main/feature/release dans le packaging local ;
- maintien des contrôles de secrets, SAST, PostgreSQL/Testcontainers, SBOM et reproductibilité.

## Exclusions

- aucune création des trains distants `feature/V*` ou `release/V*`, aucune protection,
  synchronisation ou promotion de ces trains ; seule la publication de la branche de bootstrap et
  de sa Pull Request vers `main` relève de ce lot ;
- aucune fusion, MR GitLab ou création de tag dans ce lot de bootstrap ;
- aucun changement applicatif, migration, base, endpoint, Playwright ou campagne ;
- aucun secret, cache partagé, runner VPS, artefact VPS ou déploiement ;
- aucune réécriture des Work Orders et preuves historiques.

## Critères d'acceptation

- [x] La grammaire accepte les trains stable, RC01..RC99 et RC01..RC99-SNAPSHOT, les WO
  CODEX/HUMAN et le seul bootstrap, et refuse casse, zéros initiaux, RC invalide, anciens noms et
  identifiants incomplets.
- [x] Le garde PR accepte uniquement WO vers feature identique, feature vers `main` avec version
  Maven finale exacte et le bootstrap Maven `0.1.0-SNAPSHOT` lié par graphe à sa base et à sa tête
  exactes.
- [x] Le garde MR accepte uniquement feature vers release de train identique, dans le même projet,
  avec cible protégée, conversion Maven `RCnn -> rc.N` exacte, source/main/SHA alignés, release
  ancêtre, historique complet et release non scellée.
- [x] GitHub et GitLab conservent un snapshot uniquement depuis le train feature exact.
- [x] Les pipelines de branche GitLab acceptent seulement `main`, les trains feature exacts et les
  releases exactes ; ils refusent les branches WO, bootstrap et historiques, quel que soit leur
  déclencheur, tandis que MR et tags suivent leurs contrôles dédiés.
- [x] Le packaging refuse une divergence branche/version ; pour une promotion taguée GitLab, il
  exige que `origin/main`, `origin/feature/<TRAIN>` et `origin/release/<TRAIN>` existent et désignent
  exactement le commit tagué et extrait. Il refuse explicitement tout tag `rc.100` ou supérieur
  faute de train canonique.
- [ ] Les tests shell ciblés, le scan local-only, le contrôle de secrets du diff et
  `git diff --check` réussissent.
- [x] Les quatre statuts du Lab et `production.approved=false` / `vps.deployable=false` restent
  présents et bloquants.
- [x] La règle de tags protégés `v*` est documentée et contrôlée sans protéger `main` ni
  `feature/*`.

## Commandes prévues

```text
sh ci/test-branch-name.sh
sh ci/test-gitlab-pipeline-ref.sh
sh ci/test-github-pull-request.sh
sh ci/test-gitlab-merge-request.sh
sh ci/test-durable-snapshot-source.sh
sh ci/test-package-guards.sh
sh ci/assert-local-only.sh
sh ci/check-no-secrets.sh 054fa4ca9301224aa5f96f478136208d2327d7f0
git diff --check
```

## Résultats d'exécution

Sur l'état de travail du 2026-09-05, avant publication de la branche de bootstrap :

- `sh ci/test-branch-name.sh` : `BRANCH_NAMING_VERSIONED=PASS`, y compris le contexte
  `gitlab-branch` qui refuse bootstrap, branches WO et références historiques ;
- `sh ci/test-gitlab-pipeline-ref.sh` : `GITLAB_PIPELINE_REF_TESTS=PASS` pour les déclencheurs
  push, Web, schedule, API, trigger et pipeline, avec routes MR/tag séparées et contextes
  indéterminés refusés ;
- `sh ci/test-github-pull-request.sh` : `GITHUB_PR_POLICY_TESTS=PASS`, dont version Maven
  `0.1.0-SNAPSHOT`, base, tête, ascendance et merge-base exactes du bootstrap ;
- `sh ci/test-gitlab-merge-request.sh` : `GITLAB_MR_POLICY_TESTS=PASS` sur les graphes alignés et
  divergents ;
- `sh ci/test-durable-snapshot-source.sh` : `DURABLE_SNAPSHOT_SOURCE_TESTS=PASS` ;
- `sh ci/test-package-guards.sh` : succès avec code de sortie `0` après le dernier durcissement du
  routeur GitLab, jusqu'aux marqueurs `RELEASE_REPRODUCIBILITY=PASS_LOCAL_ONLY` et
  `PACKAGE_GIT_GUARDS=PASS_LOCAL_ONLY` ;
- syntaxe des scripts shell modifiés et `git diff --check` : succès.

Le scan de secrets sera exécuté sur le commit candidat afin d'examiner ses blobs Git. Les suites
Maven et PostgreSQL/Testcontainers restent à prouver sur ce même commit par les checks GitHub
Windows/Linux ; elles ne sont pas revendiquées comme exécutées localement.

## Preuves restantes hors de ce worktree

- création de `feature/V0.1.0` depuis `main` et de `release/V0.1.0` au même SHA ;
- protection GitLab effective de `release/V*`, absence de protection des autres branches et règle
  distincte de tags protégés `v*` ;
- CI GitHub du bootstrap, revue humaine et fusion ;
- synchronisation et qualification GitLab des SHA exacts ;
- première PR WO conforme, snapshot du train et MR de promotion réelle.

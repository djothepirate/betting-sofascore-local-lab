# WO-062 — Inventaire préparatoire de ss-ci-security

- Statut : `PREPARED_NOT_RUN` ; préparation B2, avant rédaction.
- Date : 15 septembre 2026.
- Racine lue : `C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2`.
- Commit de référence : `57c0614627e628077b0a7775eb66a7c490b422e0`.
- Branche : `feature/V0.1.0-RC01-CODEX-WO-SS-20260915-062`.
- Version observée du POM : `0.1.0-rc.1-SNAPSHOT`.
- 25 sources ciblées : norme, cadrage, procédure, implémentation, tests et expérience historique.
- Le présent inventaire sert à la conception et à la revue. Il ne fait pas partie des entrées
  transmises aux sessions évaluées. Celles-ci reçoivent uniquement les `source_ids` de leur cas,
  le fragment d’entrée correspondant et le candidat exact.

## Provenance et fraîcheur

Le [manifeste](manifest.json) identifie chaque source par son chemin, sa taille,
son SHA-256 des octets du worktree et son OID de blob Git au commit ci-dessus.
Le blob Git permet une matérialisation portable lorsque les fins de ligne diffèrent ;
le SHA-256 décrit la copie Windows effectivement inspectée. Ces deux identités ne sont
pas interchangeables. Les copies de run enregistreront leurs propres octets et empreintes.

CS-N02 (WO-062) fixe le cadrage et reste identifié par son blob au commit indiqué.
Il n’est dans l’allowlist d’aucun cas : les mises à jour ultérieures du WO ne modifient
pas les entrées métier gelées. Aucun fichier source ne doit être lu depuis une copie
future silencieusement différente. Toute source exécutée différente ouvre un nouveau gel.

| ID | Classe | Source et localisation | Utilité |
| --- | --- | --- | --- |
| CS-N01 | normative | [AGENTS.md](../../../../../AGENTS.md) — Invariants ; Workflow Git | Statuts, localité, réseau CI, Git et définition de fini. |
| CS-N02 | scoping | [docs/work_orders/active/WO-SS-20260915-062-skills-lot2.md](../../../../../docs/work_orders/active/WO-SS-20260915-062-skills-lot2.md) — Sections 4.B2 et 6 | Contrat B2, huit cas et séparation préparation/évaluation ; source de cadrage seulement. |
| CS-N03 | normative | [ADR-SS-004-integration-continue-et-distribution-locale-uniquement.md](../../../../../ADR-SS-004-integration-continue-et-distribution-locale-uniquement.md) — Décision et critères de conformité | Décision courante amendée WO-061 ; tests, scans, distribution et promotion. |
| CS-P01 | procedure | [docs/runbooks/CI-LOCAL-QUOTAS.md](../../../../../docs/runbooks/CI-LOCAL-QUOTAS.md) — Déclenchements ; Ce qui reste bloquant ; Conservation | Applicabilité des événements, preuves bloquantes, documentation et rétention. |
| CS-H01 | historical | [docs/validation/WO061-CI-LOCAL-QUOTAS-20260915.md](../../../../../docs/validation/WO061-CI-LOCAL-QUOTAS-20260915.md) — État distant observé ; Qualification locale ; Revue indépendante | Incident réel quota et qualification datée, jamais verdict du candidat courant. |
| CS-I01 | implementation | [.github/workflows/ci.yml](../../../../../.github/workflows/ci.yml) — on ; linux ; windows ; always ; upload-artifact | Jobs GitHub, SHA PR/source/extrait, upload obligatoire et absence de bundle/cache. |
| CS-I02 | implementation | [.gitlab-ci.yml](../../../../../.gitlab-ci.yml) — workflow ; .maven-java25 ; secret_detection ; package:release-local-only | Règles MR/tag/Web, Secret Detection, Javadoc manuelle et package tagué. |
| CS-I03 | implementation | [ci/VerifyTestReports.java](../../../../../ci/VerifyTestReports.java) — reports ; summary ; xml | Rapports XML obligatoires, compteurs/testcase, intégration et sécurité XML. |
| CS-T01 | test | [ci/TestVerifyTestReports.java](../../../../../ci/TestVerifyTestReports.java) — check ; fixtures de rapports absents, zéro, incohérents et DOCTYPE | 26 fixtures locales du garde de preuves ; preuve du comportement prévu sans les exécuter. |
| CS-I04 | implementation | [ci/check-branch-version.sh](../../../../../ci/check-branch-version.sh) — context ; expected_version | Mapping Maven manuel sans exception seed. |
| CS-I05 | implementation | [ci/check-github-pull-request.sh](../../../../../ci/check-github-pull-request.sh) — train_maven_version ; route WO ; bootstrap | WO vers même train, train vers main, bootstrap borné. |
| CS-I06 | implementation | [ci/check-gitlab-merge-request.sh](../../../../../ci/check-gitlab-merge-request.sh) — source_sha ; shallow ; source_commit ; main_commit ; ancestry | Même projet, protection, version, graphe complet et SHA canonique exact. |
| CS-I07 | implementation | [ci/check-gitlab-pipeline-ref.sh](../../../../../ci/check-gitlab-pipeline-ref.sh) — pipeline_source ; tag_name ; merge_request_source | Contexte exclusif tag/MR/branche et références déterminées. |
| CS-I08 | implementation | [ci/check-durable-snapshot-source.sh](../../../../../ci/check-durable-snapshot-source.sh) — feature exacte | Garde autonome conservé : ne prouve pas l’existence de bundles automatiques. |
| CS-I09 | implementation | [ci/package-local-only.sh](../../../../../ci/package-local-only.sh) — accept_train_seed ; PROMOTION_TAG_PROOF ; generate_sbom ; SHA256SUMS | SHA extrait, seed borné, tag/ref/version, SBOM et provenance locale. |
| CS-I10 | implementation | [ci/check-no-secrets.sh](../../../../../ci/check-no-secrets.sh) — is_vetted_synthetic_fixture ; scan_blob ; revision_range | Scan HEAD et historique, exceptions par blobs, sorties sans valeur secrète. |
| CS-I11 | implementation | [ci/assert-local-only.sh](../../../../../ci/assert-local-only.sh) — assert_false ; application_config ; ci_files | Localité, profils fournisseur désactivés et refus déploiement. |
| CS-P02 | procedure_implementation | [ci/run-dependency-check.sh](../../../../../ci/run-dependency-check.sh) — data_directory ; failOnError ; failBuildOnCVSS ; exec | Analyse OWASP explicite, datafeed public, erreurs bloquantes et seuil 11 d’observation. |
| CS-T02 | test | [ci/test-dependency-check.sh](../../../../../ci/test-dependency-check.sh) — assert_argument ; assert_no_key ; failure_status | Fixture arguments/clé/cache/code Maven ; aucun scan réel. |
| CS-I12 | implementation | [pom.xml](../../../../../pom.xml) — parent ; properties ; build ; profiles | Versions, source Java et profils effectifs ; ne pas supposer des outils installés. |
| CS-T03 | test | [ci/test-release-reproducibility.sh](../../../../../ci/test-release-reproducibility.sh) — FIXTURE_SBOM_MODE ; seed ; rc ; release ; byte comparison | Reproductibilité testée par fixtures avec Maven stub ; portée bornée. |
| CS-T04 | test | [ci/test-package-guards.sh](../../../../../ci/test-package-guards.sh) — workflow checks ; mandatory jobs ; dependency-check fixtures | Garde actuel d’événements, packaging, secret, rapports, documentation. |
| CS-P03 | routing | [docs/skills/local-lab/ss-verify/SKILL.md](../../../../../docs/skills/local-lab/ss-verify/SKILL.md) — Choisir l’exécution ; Diagnostic et résultats | Frontière avec l’exécution de vérification du lot 1. |
| CS-I13 | implementation | [ci/check-branch-name.sh](../../../../../ci/check-branch-name.sh) — train_version ; accept_branch | Syntaxe et contexte GitHub versus GitLab. |
| CS-P04 | procedure_implementation | [scripts/Verify-Local.ps1](../../../../../scripts/Verify-Local.ps1) — Push-Location ; mvnw.cmd ; WithIntegrationTests | Commande Windows standard effective et code natif ; pas de supposition sur intégration. |

## Règles non évidentes qui doivent changer les décisions

1. **Le déclenchement fait partie de la preuve.** GitHub qualifie les PR et demandes
   manuelles ; ses pushs de branches/tags ne lancent pas la CI générale. GitLab admet MR,
   push de tag et Web ; ni synchronisation de branche ni scheduler ne qualifie automatiquement.
   Les noms de checks conservés peuvent encore mentionner « distribution locale » sans
   fabrication intermédiaire. Sources CS-N03, CS-P01, CS-I01, CS-I02, CS-I07.

2. **Le SHA testé doit être explicite.** Une PR peut être extraite sur une référence de
   merge ; distinguer SHA de tête, base et commit extrait, sans exiger une égalité artificielle
   tête/merge. Un ancien vert ou une annulation n’établit pas la réussite du dossier courant.
   La MR de promotion impose en revanche l’égalité explicite source canonique, sommet
   feature et origin/main, avec cible ancêtre et historique complet. Sources CS-I01,
   CS-I05, CS-I06 ; l’égalité tag/main/feature/release est imposée par CS-I09.

3. **Présence de fichiers, XML valide et exécution effective sont trois contrôles.**
   Le garde exige des `TEST-*.xml`, des compteurs cohérents avec les testcase, aucun
   échec/erreur et au moins un test exécuté par famille requise. Le résumé Failsafe
   doit être présent, cohérent, sans timeout ni message d’échec. Un résumé seul,
   zéro test, tous ignorés ou un code Maven zéro ne suffisent pas. Le parseur interdit
   DTD et entités externes et ne réimprime pas le contenu sensible. Sources CS-I03, CS-T01.

4. **L’upload obligatoire peut échouer après les tests.** Sur GitHub, l’échec d’upload
   XML bloque la conservation de la preuve et la qualification globale, sans prouver
   une régression applicative. Sur GitLab, le garde teste les rapports avant publication ;
   leur disponibilité effective doit aussi être vérifiée. Une absence d’artefact n’est
   pas automatiquement un échec de job. Sources CS-P01, CS-I01, CS-I02, CS-H01.

5. **Javadoc est le seul export tolérant du workflow courant.** Cette tolérance concerne
   le job documentaire manuel ; une preuve expressément requise par un WO conserve
   son caractère obligatoire. L’option `allow_failure` n’annule pas l’exigence.
   Aucun bundle intermédiaire automatique, ratchet 3A général, SAST, JaCoCo/PMD/CPD
   ou Dependency-Check automatique hérité n’est à rétablir. Sources CS-N03, CS-P01,
   CS-I01, CS-I02, CS-T04.

6. **Scanner disponible ne signifie ni scanner exécuté ni résultat de sécurité satisfaisant.**
   Dependency-Check reste un script d’analyse explicite, version 13.0.0, avec datafeed NVD
   public, aucune option de clé NVD, `failOnError=true` et `failBuildOnCVSS=11`.
   Ce seuil d’observation ne bloque aucune CVSS usuelle : un code zéro peut coexister avec
   un constat de vulnérabilité. Le stockage est propre à l’analyse ; un cache ancien
   d’origine inconnue ne prouve ni fraîcheur ni couverture. Ses fixtures ne réalisent
   aucun scan. Trivy n’est défini ni dans les deux workflows inspectés ni dans les
   scripts de packaging/scans retenus : ne pas inventer une obligation. Sources
   CS-P02, CS-T02, CS-N03, CS-I01, CS-I02, CS-I09.

7. **Un scan de secrets vert a une portée définie.** Le script strict parcourt HEAD puis
   la plage de commits, ou l’historique atteignable sans base exploitable. Il produit
   chemin/règle, sans la valeur. L’exception du canari J5 est conditionnée par les blobs
   audités ; l’exclusion par chemin du scanner GitLab ne vaut pas exemption du script
   strict pour un nouveau blob. Un signal est une valeur sensible potentielle à examiner,
   pas une confirmation qu’un secret réel est compromis. Source CS-I10, confrontée à CS-I02.

8. **SBOM et reproductibilité sont conditionnels à la distribution concernée.**
   Le package demandé/tagué exige les gardes, son unique JAR applicatif, la version
   attendue, le SBOM CycloneDX contrôlé, provenance et SHA-256. Le script génère deux
   SBOM et compare leurs octets ; il vérifie les métadonnées racine, pas seulement
   l’existence de bom.json. Une fixture de reproductibilité avec Maven simulé ne prouve
   pas à elle seule qu’un bundle réel nouvellement construit est reproductible.
   Sources CS-N03, CS-I09, CS-T03.

9. **Seed ne s’étend pas aux demandes manuelles.** Le garde autonome conserve
   l’exception de création exacte d’un train depuis main, identifiée comme telle.
   Un Web/dispatch, un WO, un deuxième push ou une divergence ne bénéficie pas du seed.
   La présence de ce chemin historique/autonome n’autorise aucune fabrication automatique.
   RC01 correspond à rc.1 ; une version finale est nécessaire pour la promotion RC.
   Sources CS-N03, CS-I04, CS-I08, CS-I09, CS-I13.

## Portée de l’expérience réelle

CS-H01 conserve le run GitHub `34950465172` et le job Linux `104319936308` :
tests et packaging réussis puis upload du snapshot refusé par le quota. Le rapport ne
donne pas le SHA de ce run incident ; sa base de WO `59b4dae…` ne doit pas être
substituée au SHA absent. Il donne séparément une qualification locale du changement
WO-061, son environnement, ses totaux, son commit fonctionnel et les limites distantes.
La purge autorisée de 22 bundles, l’inventaire de caches et la modification du réglage
GitLab sont des observations datées, sans autorisation actuelle de purge ou de mutation.

Les totaux historiques (2 440 tests standards dont 5 ignorés et 286 tests d’intégration)
ne sont ni un seuil permanent ni une preuve du candidat WO-062. Le laboratoire reste
EXPERIMENTAL, LOCAL_ONLY, NOT_PRODUCTION_APPROVED et NO_CRITICAL_DEPENDENCY.

## Limites et frontières du futur skill

Le skill B2 expliquera ce que les contrôles prouvent et leurs omissions. L’exécution
Windows/Linux demeure le rôle de `ss-verify` (CS-P03, CS-P04) lorsque la demande porte
uniquement sur cette exécution. Aucun workflow, application, migration, profil,
paramètre serveur, ADR, copie personnelle ou livrable de distribution n’est modifié ici.
La préparation ne lance ni suites applicatives, ni scanners, ni CI, ni réseau.

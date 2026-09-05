#!/usr/bin/env sh
set -eu

repository=$(git rev-parse --show-toplevel)
cd "$repository"

guard_fixture=$(mktemp -d "${TMPDIR:-/tmp}/package-guard-repository.XXXXXX")

cleanup() {
    rm -rf "$guard_fixture" || :
}
trap cleanup 0
trap 'exit 129' HUP
trap 'exit 130' INT
trap 'exit 143' TERM

assert_rejected() {
    expected=$1
    shift
    output_file=$(mktemp "${TMPDIR:-/tmp}/package-guard.XXXXXX")
    if "$@" >"$output_file" 2>&1; then
        echo "FAIL: le scénario devait être refusé : $expected" >&2
        rm -f "$output_file"
        exit 1
    fi
    if ! grep -Fq "$expected" "$output_file"; then
        echo "FAIL: motif de refus absent : $expected" >&2
        cat "$output_file" >&2
        rm -f "$output_file"
        exit 1
    fi
    rm -f "$output_file"
}

mkdir -p "$guard_fixture/ci"
cp ci/package-local-only.sh ci/check-branch-name.sh \
    ci/check-durable-snapshot-source.sh "$guard_fixture/ci/"
git init -q -b main "$guard_fixture"
git -C "$guard_fixture" config user.name ci-fixture
git -C "$guard_fixture" config user.email ci-fixture.invalid@example.test
git -C "$guard_fixture" add ci/package-local-only.sh
git -C "$guard_fixture" commit -qm 'package guard fixture base'
git -C "$guard_fixture" commit --allow-empty -qm 'package guard fixture head'

(
    cd "$guard_fixture"
    head_commit=$(git rev-parse 'HEAD^{commit}')
    parent_commit=$(git rev-parse 'HEAD^{commit}^')
    canonical_main_ref=refs/remotes/origin/main
    canonical_feature_ref=refs/remotes/origin/feature/V999999.999999.999999-RC01
    canonical_release_ref=refs/remotes/origin/release/V999999.999999.999999-RC01
    test_tag=v999999.999999.999999-rc.1
    test_tag_ref="refs/tags/$test_tag"

    assert_rejected 'aucun train canonique' \
        env CI_COMMIT_SHA="$head_commit" CI_COMMIT_TAG=v999999.999999.999999-rc.100 \
        CI_PIPELINE_IID=1 sh ci/package-local-only.sh
    assert_rejected 'tag hors convention SemVer' \
        env CI_COMMIT_SHA="$head_commit" CI_COMMIT_TAG=v999999.999999.999999-RC01 \
        CI_PIPELINE_IID=1 sh ci/package-local-only.sh

    assert_rejected 'ne correspond pas au commit extrait' \
        env CI_COMMIT_SHA="$parent_commit" CI_PIPELINE_IID=1 \
        sh ci/package-local-only.sh

    assert_rejected "le tag $test_tag est absent du checkout" \
        env CI_COMMIT_SHA="$head_commit" CI_COMMIT_TAG="$test_tag" CI_PIPELINE_IID=1 \
        sh ci/package-local-only.sh

    git update-ref "$test_tag_ref" "$head_commit"
    git update-ref -d "$canonical_main_ref"
    assert_rejected 'référence canonique main introuvable' \
        env CI_COMMIT_SHA="$head_commit" CI_COMMIT_TAG="$test_tag" CI_PIPELINE_IID=1 \
        sh ci/package-local-only.sh

    git update-ref "$canonical_main_ref" "$parent_commit"
    assert_rejected 'sommet canonique exact' \
        env CI_COMMIT_SHA="$head_commit" CI_COMMIT_TAG="$test_tag" CI_PIPELINE_IID=1 \
        sh ci/package-local-only.sh

    git update-ref "$test_tag_ref" "$parent_commit"
    assert_rejected 'ne désigne pas le commit source' \
        env CI_COMMIT_SHA="$head_commit" CI_COMMIT_TAG="$test_tag" CI_PIPELINE_IID=1 \
        sh ci/package-local-only.sh

    git update-ref "$test_tag_ref" "$head_commit"
    git update-ref "$canonical_main_ref" "$head_commit"
    assert_rejected 'référence feature canonique introuvable' \
        env CI_COMMIT_SHA="$head_commit" CI_COMMIT_TAG="$test_tag" CI_PIPELINE_IID=1 \
        PROMOTION_TAG_PROOF=true FEATURE_BRANCH_REF="$canonical_feature_ref" \
        RELEASE_BRANCH_REF="$canonical_release_ref" sh ci/package-local-only.sh
    git update-ref "$canonical_feature_ref" "$parent_commit"
    assert_rejected 'sommet canonique exact' \
        env CI_COMMIT_SHA="$head_commit" CI_COMMIT_TAG="$test_tag" CI_PIPELINE_IID=1 \
        PROMOTION_TAG_PROOF=true FEATURE_BRANCH_REF="$canonical_feature_ref" \
        RELEASE_BRANCH_REF="$canonical_release_ref" sh ci/package-local-only.sh

    git update-ref "$canonical_feature_ref" "$head_commit"
    assert_rejected 'exige PROMOTION_TAG_PROOF=true' \
        env CI_COMMIT_SHA="$head_commit" CI_COMMIT_TAG="$test_tag" CI_PIPELINE_IID=1 \
        sh ci/package-local-only.sh
    assert_rejected 'référence feature de promotion inattendue' \
        env CI_COMMIT_SHA="$head_commit" CI_COMMIT_TAG="$test_tag" CI_PIPELINE_IID=1 \
        PROMOTION_TAG_PROOF=true RELEASE_BRANCH_REF="$canonical_release_ref" \
        sh ci/package-local-only.sh
    assert_rejected 'référence release de promotion inattendue' \
        env CI_COMMIT_SHA="$head_commit" CI_COMMIT_TAG="$test_tag" CI_PIPELINE_IID=1 \
        PROMOTION_TAG_PROOF=true FEATURE_BRANCH_REF="$canonical_feature_ref" \
        sh ci/package-local-only.sh
    assert_rejected 'référence release GitLab introuvable' \
        env CI_COMMIT_SHA="$head_commit" CI_COMMIT_TAG="$test_tag" CI_PIPELINE_IID=1 \
        PROMOTION_TAG_PROOF=true FEATURE_BRANCH_REF="$canonical_feature_ref" \
        RELEASE_BRANCH_REF="$canonical_release_ref" sh ci/package-local-only.sh
    git update-ref "$canonical_release_ref" "$parent_commit"
    assert_rejected 'doivent désigner le même commit exact' \
        env CI_COMMIT_SHA="$head_commit" CI_COMMIT_TAG="$test_tag" CI_PIPELINE_IID=1 \
        PROMOTION_TAG_PROOF=true FEATURE_BRANCH_REF="$canonical_feature_ref" \
        RELEASE_BRANCH_REF="$canonical_release_ref" sh ci/package-local-only.sh
    assert_rejected 'PROMOTION_TAG_PROOF doit valoir true ou false' \
        env CI_COMMIT_SHA="$head_commit" CI_COMMIT_TAG="$test_tag" CI_PIPELINE_IID=1 \
        PROMOTION_TAG_PROOF=invalid FEATURE_BRANCH_REF="$canonical_feature_ref" \
        RELEASE_BRANCH_REF="$canonical_release_ref" sh ci/package-local-only.sh

    assert_rejected "un snapshot durable exige une branche d'intégration" \
        env CI_COMMIT_SHA="$head_commit" CI_COMMIT_BRANCH=main CI_PIPELINE_IID=1 \
        DURABLE_SNAPSHOT_SOURCE=true sh ci/package-local-only.sh
)

if grep -Fq 'build.pipeline.iid=' ci/package-local-only.sh; then
    echo 'FAIL: une provenance immuable ne doit pas contenir l’IID de la forge.' >&2
    exit 1
fi
if ! grep -Fq 'artifact.version=$artifact_version' ci/package-local-only.sh; then
    echo 'FAIL: la provenance doit porter la version canonique de l’artefact.' >&2
    exit 1
fi
if grep -Eq 'MAVEN_CACHE_POLICY|^[[:space:]]*cache:' .gitlab-ci.yml; then
    echo 'FAIL: le cache GitLab partagé reste interdit sans isolation serveur qualifiée.' >&2
    exit 1
fi
excluded_path_count=$(awk '
    /^[[:space:]]*SECRET_DETECTION_EXCLUDED_PATHS[[:space:]]*:/ { count++ }
    END { print count + 0 }
' .gitlab-ci.yml)
if [ "$excluded_path_count" -ne 1 ] \
   || ! grep -Fq \
    'SECRET_DETECTION_EXCLUDED_PATHS: "scripts/Invoke-J5PlaywrightLoopbackQualification.ps1"' \
    .gitlab-ci.yml \
   || ! grep -Fq 'expected_blob=62c2eba3fb980d68589a9804d2fa81bcc865a039' \
       ci/check-no-secrets.sh; then
    echo 'FAIL: l’exception GitLab des canaris doit rester limitée au blob synthétique audité.' >&2
    exit 1
fi
if ! grep -Fq 'AST_ENABLE_MR_PIPELINES: "true"' .gitlab-ci.yml; then
    echo 'FAIL: les scanners GitLab stables doivent s’exécuter dans les pipelines MR.' >&2
    exit 1
fi
if ! grep -Fq \
    'secret_scan_base="${CI_MERGE_REQUEST_DIFF_BASE_SHA:-${CI_COMMIT_BEFORE_SHA:-}}"' \
    .gitlab-ci.yml; then
    echo 'FAIL: le scan historique d’une MR doit partir de CI_MERGE_REQUEST_DIFF_BASE_SHA.' >&2
    exit 1
fi
if grep -Fq 'target/*.jar' .gitlab-ci.yml; then
    echo 'FAIL: un JAR exécutable ne doit pas être conservé depuis les tests GitLab de branches ou MR.' >&2
    exit 1
fi
if ! grep -Fq 'sh ci/check-durable-snapshot-source.sh "$BRANCH_NAME"' \
    .github/workflows/ci.yml ||
   ! grep -Fq "steps.durable-snapshot.outputs.eligible == 'true'" \
    .github/workflows/ci.yml; then
    echo 'FAIL: GitHub doit borner le snapshot durable au train feature exact.' >&2
    exit 1
fi
if ! grep -Fq 'check-branch-name.sh "$BRANCH_NAME" github-branch' \
    .github/workflows/ci.yml ||
   ! grep -Fq "github.event_name != 'pull_request' && github.ref_type == 'branch'" \
    .github/workflows/ci.yml; then
    echo 'FAIL: tout pipeline de branche GitHub doit refuser les releases et noms hors convention.' >&2
    exit 1
fi
if ! grep -Fq 'SOURCE_REF_CREATED: ${{ github.event.created }}' \
    .github/workflows/ci.yml ||
   ! grep -Fq 'SOURCE_REF_CREATED' ci/package-local-only.sh ||
   ! grep -Fq 'CI_COMMIT_BEFORE_SHA' ci/package-local-only.sh ||
   ! grep -Fq 'source.train.seed=$train_seed' ci/package-local-only.sh; then
    echo 'FAIL: l’amorçage d’un train doit rester borné à sa création exacte depuis main.' >&2
    exit 1
fi
if grep -Fq "github.ref == 'refs/heads/main'" .github/workflows/ci.yml; then
    echo 'FAIL: main ne doit plus publier de snapshot durable.' >&2
    exit 1
fi
if ! grep -Fq "github.event_name == 'pull_request' && github.head_ref" \
    .github/workflows/ci.yml ||
   ! grep -Fq "github.ref_type == 'branch' && github.ref_name" \
    .github/workflows/ci.yml; then
    echo 'FAIL: SOURCE_BRANCH_NAME doit rester vide sur les événements tag GitHub.' >&2
    exit 1
fi
if ! grep -Fq '"$SOURCE_BRANCH" "$TARGET_BRANCH" "$BASE_SHA" "$HEAD_SHA" "$project_version"' \
    .github/workflows/ci.yml; then
    echo 'FAIL: le garde PR GitHub doit recevoir base, tête et version Maven exactes.' >&2
    exit 1
fi
if grep -Fq "!startsWith(github.ref, 'refs/tags/')" .github/workflows/ci.yml; then
    echo 'FAIL: GitHub doit valider le bundle local d’un tag sans le téléverser.' >&2
    exit 1
fi
if ! grep -Fq '$CI_PIPELINE_SOURCE == "push" && $CI_COMMIT_BRANCH =~ /^feature\/V' \
    .gitlab-ci.yml ||
   ! grep -Fq 'DURABLE_SNAPSHOT_SOURCE: "true"' .gitlab-ci.yml; then
    echo 'FAIL: GitLab doit conserver un snapshot uniquement depuis un train feature exact.' >&2
    exit 1
fi
if ! grep -Fq 'sh ci/check-gitlab-pipeline-ref.sh' .gitlab-ci.yml ||
   ! grep -Fq 'sh "$script_dir/check-branch-name.sh" "$branch_name" gitlab-branch' \
       ci/check-gitlab-pipeline-ref.sh; then
    echo 'FAIL: tout pipeline de branche GitLab doit refuser les branches WO, bootstrap et historiques.' >&2
    exit 1
fi
if ! grep -Fq '$CI_COMMIT_REF_PROTECTED == "true" && $CI_COMMIT_TAG =~' \
    .gitlab-ci.yml; then
    echo 'FAIL: une release locale GitLab exige un tag protégé.' >&2
    exit 1
fi
if ! grep -Fq 'sh ci/check-gitlab-merge-request.sh "$project_version"' \
    .gitlab-ci.yml; then
    echo 'FAIL: la topologie des MR GitLab doit être contrôlée avant promotion.' >&2
    exit 1
fi
if ! grep -Fq '"+refs/heads/$source_branch:$source_ref"' .gitlab-ci.yml ||
   ! grep -Fq '"+refs/heads/$target_branch:$target_ref"' .gitlab-ci.yml ||
   ! grep -Fq '"+refs/tags/*:refs/tags/*"' .gitlab-ci.yml ||
   ! grep -Fq 'sh ci/check-gitlab-merge-request.sh "$project_version" "$source_sha"' \
    .gitlab-ci.yml; then
    echo 'FAIL: le garde MR GitLab exige les sommets main/source/cible, les tags et le SHA source.' >&2
    exit 1
fi
if ! grep -Fq 'doit correspondre à la règle de tags protégés v*' .gitlab-ci.yml; then
    echo 'FAIL: un tag de promotion GitLab doit être couvert par la règle de tags protégés v*.' >&2
    exit 1
fi
if ! grep -Fq '"+refs/heads/$feature_branch:$feature_ref"' .gitlab-ci.yml ||
   ! grep -Fq 'PROMOTION_TAG_PROOF=true' .gitlab-ci.yml ||
   ! grep -Fq 'FEATURE_BRANCH_REF="$feature_ref"' .gitlab-ci.yml ||
   ! grep -Fq 'RELEASE_BRANCH_REF="$release_ref"' .gitlab-ci.yml; then
    echo 'FAIL: le tag GitLab doit prouver les sommets exacts main, feature et release.' >&2
    exit 1
fi
cat >"$guard_fixture/workflow.expected" <<'YAML'
workflow:
  rules:
    - if: '$CI_PIPELINE_SOURCE == "merge_request_event"'
    - if: '$CI_PIPELINE_SOURCE == "push" && $CI_COMMIT_BRANCH && $CI_OPEN_MERGE_REQUESTS'
      when: never
    - if: '$CI_COMMIT_BRANCH == $CI_DEFAULT_BRANCH'
    - if: '$CI_COMMIT_BRANCH'
    - if: '$CI_COMMIT_TAG'
    - if: '$CI_PIPELINE_SOURCE == "web"'
    - when: never
YAML
awk '
    /^workflow:$/ { capture = 1 }
    capture && /^stages:$/ { exit }
    capture && $0 !~ /^[[:space:]]*$/ { print }
' .gitlab-ci.yml >"$guard_fixture/workflow.actual"
if ! cmp -s "$guard_fixture/workflow.expected" "$guard_fixture/workflow.actual"; then
    echo 'FAIL: la matrice workflow GitLab ne respecte plus le contrat branche/MR/tag/web.' >&2
    cat "$guard_fixture/workflow.actual" >&2
    exit 1
fi
cat >"$guard_fixture/secret-detection.expected" <<'YAML'
secret_detection:
  allow_failure: false
  variables:
    GIT_DEPTH: "0"
  rules:
    - if: '$SECRET_DETECTION_DISABLED == "true" || $SECRET_DETECTION_DISABLED == "1"'
      when: never
    - if: '$CI_COMMIT_TAG'
    - if: '$AST_ENABLE_MR_PIPELINES == "true" && $CI_PIPELINE_SOURCE == "merge_request_event"'
    - if: '$AST_ENABLE_MR_PIPELINES == "true" && $CI_OPEN_MERGE_REQUESTS'
      when: never
    - if: '$CI_COMMIT_BRANCH'
YAML
awk '
    /^secret_detection:$/ { capture = 1 }
    capture && NR > 1 && /^[^[:space:]#]/ && $0 !~ /^secret_detection:$/ { exit }
    capture && $0 !~ /^[[:space:]]*$/ { print }
' .gitlab-ci.yml >"$guard_fixture/secret-detection.actual"
if ! cmp -s "$guard_fixture/secret-detection.expected" \
    "$guard_fixture/secret-detection.actual"; then
    echo 'FAIL: Secret Detection doit couvrir MR, branches et tags sans doublon.' >&2
    cat "$guard_fixture/secret-detection.actual" >&2
    exit 1
fi
sh ci/test-check-no-secrets-signals.sh
sh ci/test-branch-name.sh
sh ci/test-gitlab-pipeline-ref.sh
sh ci/test-github-pull-request.sh
sh ci/test-gitlab-merge-request.sh
sh ci/test-durable-snapshot-source.sh
sh ci/test-release-reproducibility.sh

printf 'PACKAGE_GIT_GUARDS=PASS_LOCAL_ONLY\n'

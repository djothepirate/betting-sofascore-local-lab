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
cp ci/package-local-only.sh "$guard_fixture/ci/package-local-only.sh"
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
    test_tag=v999999.999999.999999-rc.1
    test_tag_ref="refs/tags/$test_tag"

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
    assert_rejected "n'est pas atteignable depuis" \
        env CI_COMMIT_SHA="$head_commit" CI_COMMIT_TAG="$test_tag" CI_PIPELINE_IID=1 \
        sh ci/package-local-only.sh

    git update-ref "$test_tag_ref" "$parent_commit"
    assert_rejected 'ne désigne pas le commit source' \
        env CI_COMMIT_SHA="$head_commit" CI_COMMIT_TAG="$test_tag" CI_PIPELINE_IID=1 \
        sh ci/package-local-only.sh
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
if grep -Fq 'target/*.jar' .gitlab-ci.yml; then
    echo 'FAIL: un JAR exécutable ne doit pas être conservé depuis les tests GitLab de branches ou MR.' >&2
    exit 1
fi
if ! grep -Fq "github.event_name == 'push' && github.ref == 'refs/heads/main'" \
    .github/workflows/ci.yml; then
    echo 'FAIL: GitHub ne doit conserver un snapshot exécutable que depuis un push de main.' >&2
    exit 1
fi
if grep -Fq "!startsWith(github.ref, 'refs/tags/')" .github/workflows/ci.yml; then
    echo 'FAIL: GitHub doit valider le bundle local d’un tag sans le téléverser.' >&2
    exit 1
fi
if ! grep -Fq '$CI_PIPELINE_SOURCE == "push" && $CI_COMMIT_BRANCH == $CI_DEFAULT_BRANCH' \
    .gitlab-ci.yml; then
    echo 'FAIL: GitLab ne doit conserver un snapshot que depuis un push de la branche par défaut.' >&2
    exit 1
fi
if ! grep -Fq '$CI_COMMIT_REF_PROTECTED == "true" && $CI_COMMIT_TAG =~' \
    .gitlab-ci.yml; then
    echo 'FAIL: une release locale GitLab exige un tag protégé.' >&2
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
sh ci/test-check-no-secrets-signals.sh
sh ci/test-branch-name.sh
sh ci/test-release-reproducibility.sh

printf 'PACKAGE_GIT_GUARDS=PASS_LOCAL_ONLY\n'

#!/usr/bin/env sh
set -eu

repository=$(git rev-parse --show-toplevel)
script="$repository/ci/check-github-pull-request.sh"
bootstrap_branch=codex/ss-20260905-055-version-branch-workflow
bootstrap_base=054fa4ca9301224aa5f96f478136208d2327d7f0

assert_allowed() {
    source_branch=$1
    target_branch=$2
    project_version=$3
    if ! sh "$script" "$source_branch" "$target_branch" unused-base unused-head \
        "$project_version" >/dev/null; then
        echo "FAIL: PR GitHub valide refusée : $source_branch -> $target_branch ($project_version)" >&2
        exit 1
    fi
}

assert_rejected() {
    source_branch=$1
    target_branch=$2
    project_version=$3
    if sh "$script" "$source_branch" "$target_branch" unused-base unused-head \
        "$project_version" >/dev/null 2>&1; then
        echo "FAIL: PR GitHub hors politique acceptée : $source_branch -> $target_branch ($project_version)" >&2
        exit 1
    fi
}

assert_allowed feature/V0.1.0-CODEX-WO-SS-20260905-056 feature/V0.1.0 0.1.0-SNAPSHOT
assert_allowed feature/V0.1.0-HUMAN-WO-SS-20260905-057 feature/V0.1.0 0.1.0
assert_allowed feature/V1.2.3-RC04-HUMAN-WO-SS-20260906-057 feature/V1.2.3-RC04 1.2.3-rc.4
assert_allowed feature/V0.1.0-RC01-CODEX-WO-SS-20260906-056 feature/V0.1.0-RC01 0.1.0-rc.1-SNAPSHOT
assert_allowed feature/V1.2.3-RC99-HUMAN-WO-SS-20260906-057 feature/V1.2.3-RC99 1.2.3-rc.99-SNAPSHOT
assert_allowed feature/V1.2.3-RC99-SNAPSHOT-CODEX-WO-SS-20260906-058 feature/V1.2.3-RC99-SNAPSHOT 1.2.3-rc.99-SNAPSHOT
assert_allowed feature/V0.1.0 main 0.1.0
assert_allowed feature/V1.2.3-RC04 main 1.2.3-rc.4
assert_allowed feature/V1.2.3-RC99-SNAPSHOT main 1.2.3-rc.99-SNAPSHOT

assert_rejected feature/V0.1.0-CODEX-WO-SS-20260905-056 main 0.1.0-SNAPSHOT
assert_rejected feature/V0.1.0-CODEX-WO-SS-20260905-056 feature/V0.2.0 0.1.0-SNAPSHOT
assert_rejected feature/V0.1.0-HUMAN-WO-SS-20260905-056 release/V0.1.0 0.1.0-SNAPSHOT
assert_rejected feature/V0.1.0 release/V0.1.0 0.1.0
assert_rejected feature/V0.1.0 main 0.1.0-SNAPSHOT
assert_rejected feature/V1.2.3-RC04 main 1.2.3-RC04
assert_rejected feature/V0.1.0-RC01 main 0.1.0-rc.1-SNAPSHOT
for invalid_version in 0.1.0-SNAPSHOT 0.1.0-rc.2-SNAPSHOT 0.1.1-rc.1-SNAPSHOT \
    0.1.0-RC01-SNAPSHOT 0.1.0-rc.01-SNAPSHOT 0.1.0-rc.1-SNAPSHOT-SNAPSHOT; do
    assert_rejected feature/V0.1.0-RC01-CODEX-WO-SS-20260906-056 \
        feature/V0.1.0-RC01 "$invalid_version"
done
assert_rejected feature/V1.2.3-RC04-SNAPSHOT main 1.2.3-rc.4
assert_rejected feature/V1.2.3-RC04-SNAPSHOT-CODEX-WO-SS-20260906-058 feature/V1.2.3-RC04 1.2.3-rc.4-SNAPSHOT
assert_rejected feature/V1.2.3-rc.4-HUMAN-WO-SS-20260906-057 feature/V1.2.3-rc.4 1.2.3-rc.4
assert_rejected release/V0.1.0 main 0.1.0
assert_rejected main feature/V0.1.0 0.1.0

fixture=$(mktemp -d "${TMPDIR:-/tmp}/github-bootstrap-policy.XXXXXX")
cleanup() {
    rm -rf "$fixture"
}
trap cleanup EXIT HUP INT TERM

git clone -q --no-local "$repository" "$fixture"
(
    cd "$fixture"
    git config user.name ci-fixture
    git config user.email ci-fixture.invalid@example.test
    git checkout -q --detach "$bootstrap_base"
    mkdir -p ci
    cp "$repository/ci/check-branch-name.sh" \
       "$repository/ci/check-github-pull-request.sh" ci/
    git commit --allow-empty -qm 'bootstrap descendant'
    bootstrap_head=$(git rev-parse HEAD)

    if ! sh ci/check-github-pull-request.sh "$bootstrap_branch" main \
        "$bootstrap_base" "$bootstrap_head" 0.1.0-SNAPSHOT >/dev/null; then
        echo 'FAIL: le bootstrap descendant de sa base exacte a été refusé.' >&2
        exit 1
    fi
    if sh ci/check-github-pull-request.sh "$bootstrap_branch" main \
        "$bootstrap_base" "$bootstrap_head" 0.1.0 >/dev/null 2>&1; then
        echo 'FAIL: un bootstrap avec une version Maven finale a été accepté.' >&2
        exit 1
    fi

    tree=$(git rev-parse "${bootstrap_base}^{tree}")
    unrelated_head=$(printf 'bootstrap unrelated\n' | git commit-tree "$tree")
    if sh ci/check-github-pull-request.sh "$bootstrap_branch" main \
        "$bootstrap_base" "$unrelated_head" 0.1.0-SNAPSHOT >/dev/null 2>&1; then
        echo 'FAIL: un bootstrap sans merge-base exacte a été accepté.' >&2
        exit 1
    fi
    if sh ci/check-github-pull-request.sh "$bootstrap_branch" main \
        deadbeef "$bootstrap_head" 0.1.0-SNAPSHOT >/dev/null 2>&1; then
        echo 'FAIL: un bootstrap sur une autre base a été accepté.' >&2
        exit 1
    fi
    if sh ci/check-github-pull-request.sh "$bootstrap_branch" main \
        "$bootstrap_base" deadbeef 0.1.0-SNAPSHOT >/dev/null 2>&1; then
        echo 'FAIL: un bootstrap sans tête source résolue a été accepté.' >&2
        exit 1
    fi
    if sh ci/check-github-pull-request.sh "$bootstrap_branch" feature/V0.1.0 \
        "$bootstrap_base" "$bootstrap_head" 0.1.0-SNAPSHOT >/dev/null 2>&1; then
        echo 'FAIL: le bootstrap vers une autre cible que main a été accepté.' >&2
        exit 1
    fi
)

printf 'GITHUB_PR_POLICY_TESTS=PASS\n'

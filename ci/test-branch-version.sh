#!/usr/bin/env sh
set -eu

script_dir=$(CDPATH= cd -- "$(dirname "$0")" && pwd)
fixture=$(mktemp "${TMPDIR:-/tmp}/local-lab-branch-version.XXXXXX")
trap 'rm -f "$fixture"' 0
trap 'exit 129' HUP
trap 'exit 130' INT
trap 'exit 143' TERM

assert_accepted() {
    if ! sh "$script_dir/check-branch-version.sh" "$1" "$2" "${3:-github-branch}" >"$fixture" 2>&1 ||
       ! grep -Fq "BRANCH_VERSION_POLICY=PASS:$1:$2" "$fixture"; then
        echo "FAIL: mapping branche/version valide refusé : $1 / $2." >&2
        cat "$fixture" >&2
        exit 1
    fi
}

assert_rejected() {
    if sh "$script_dir/check-branch-version.sh" "$1" "$2" "${3:-github-branch}" >"$fixture" 2>&1; then
        echo "FAIL: mapping branche/version invalide accepté : $1 / $2." >&2
        exit 1
    fi
    if ! grep -Fq 'FAIL:' "$fixture"; then
        echo 'FAIL: le refus branche/version ne porte pas de diagnostic.' >&2
        cat "$fixture" >&2
        exit 1
    fi
}

assert_accepted main 0.1.0-SNAPSHOT
assert_accepted main 1.2.3-rc.1
for suffix in '' -CODEX-WO-SS-20260915-061 -HUMAN-WO-SS-20260915-061; do
    assert_accepted "feature/V1.2.3$suffix" 1.2.3
    assert_accepted "feature/V1.2.3$suffix" 1.2.3-SNAPSHOT
    assert_accepted "feature/V1.2.3-RC01$suffix" 1.2.3-rc.1
    assert_accepted "feature/V1.2.3-RC01$suffix" 1.2.3-rc.1-SNAPSHOT
    assert_accepted "feature/V1.2.3-RC99$suffix" 1.2.3-rc.99-SNAPSHOT
    assert_accepted "feature/V1.2.3-RC01-SNAPSHOT$suffix" 1.2.3-rc.1-SNAPSHOT
    assert_rejected "feature/V1.2.3$suffix" 1.2.4-SNAPSHOT
    assert_rejected "feature/V1.2.3-RC01$suffix" 1.2.3-SNAPSHOT
    assert_rejected "feature/V1.2.3-RC01$suffix" 1.2.3-rc.2-SNAPSHOT
    assert_rejected "feature/V1.2.3-RC01-SNAPSHOT$suffix" 1.2.3-rc.1
done

assert_rejected feature/V1.2.3-RC00 1.2.3-rc.0-SNAPSHOT
assert_rejected feature/V1.2.3-RC100 1.2.3-rc.100-SNAPSHOT
assert_rejected feature/V1.2.3-RC01 1.2.3-rc.01-SNAPSHOT
assert_rejected feature/V1.2.3-RC01 1.2.3-RC01-SNAPSHOT
assert_rejected release/V1.2.3 1.2.3
assert_rejected v1.2.3 1.2.3
assert_rejected codex/ss-20260905-055-version-branch-workflow 0.1.0-SNAPSHOT
assert_rejected main ''
assert_rejected main 01.2.3-SNAPSHOT
assert_rejected main arbitrary-version
# Même si l'appelant fournit un contexte de création, le lancement manuel
# n'accepte jamais une ancienne version Maven empruntée au sommet de main.
SOURCE_REF_CREATED=true GITHUB_EVENT_NAME=push \
    assert_rejected feature/V1.2.4 1.2.3-SNAPSHOT

assert_accepted main 1.2.3-SNAPSHOT gitlab-branch
assert_accepted feature/V1.2.3 1.2.3-SNAPSHOT gitlab-branch
assert_accepted feature/V1.2.3-RC01 1.2.3-rc.1-SNAPSHOT gitlab-branch
assert_accepted release/V1.2.3 1.2.3 gitlab-branch
assert_accepted release/V1.2.3-RC01 1.2.3-rc.1 gitlab-branch
# Le train explicitement suffixé SNAPSHOT garde son mapping strict et non taguable.
assert_accepted release/V1.2.3-RC01-SNAPSHOT 1.2.3-rc.1-SNAPSHOT gitlab-branch
assert_rejected release/V1.2.3 1.2.3-SNAPSHOT gitlab-branch
assert_rejected release/V1.2.3 1.2.4 gitlab-branch
assert_rejected release/V1.2.3-RC01 1.2.3-rc.1-SNAPSHOT gitlab-branch
assert_rejected release/V1.2.3-RC01 1.2.3-rc.2 gitlab-branch
assert_rejected release/V1.2.3-RC01-SNAPSHOT 1.2.3-rc.1 gitlab-branch
assert_rejected feature/V1.2.3-CODEX-WO-SS-20260915-061 1.2.3-SNAPSHOT gitlab-branch
assert_rejected codex/ss-20260905-055-version-branch-workflow 0.1.0-SNAPSHOT gitlab-branch
assert_rejected feature/V1.2.3 1.2.3 general
assert_rejected feature/V1.2.3 1.2.3 unknown

printf 'BRANCH_VERSION_GUARDS=PASS_NO_DISPATCH_SEED_EXCEPTION\n'

#!/usr/bin/env sh
set -eu

for branch_name in \
    main \
    codex/ss-20260905-055-version-branch-workflow \
    feature/V0.1.0 \
    feature/V1.20.300-RC04 \
    feature/V1.20.300-RC99-SNAPSHOT \
    feature/V0.1.0-CODEX-WO-SS-20260905-056 \
    feature/V1.20.300-RC04-HUMAN-WO-SS-20260906-057 \
    feature/V1.20.300-RC99-SNAPSHOT-CODEX-WO-SS-20260906-058 \
    release/V0.1.0 \
    release/V1.20.300-RC04 \
    release/V1.20.300-RC99-SNAPSHOT; do
    if ! sh ci/check-branch-name.sh "$branch_name" >/dev/null; then
        echo "FAIL: branche versionnée valide refusée : $branch_name" >&2
        exit 1
    fi
done

for branch_name in \
    '' \
    develop \
    release/1.2.3 \
    release/v1.2.3 \
    release/V01.2.3 \
    release/V1.02.3 \
    release/V1.2.03 \
    release/V1.2.3-rc.1 \
    release/V1.2.3-RC00 \
    release/V1.2.3-RC1 \
    release/V1.2.3-RC001 \
    release/V1.2.3-RC100 \
    release/V1.2.3-RC01-snapshot \
    feature/v1.2.3 \
    feature/V1.2.3-rc1 \
    feature/V1.2.3-rc.1 \
    feature/V1.2.3-RC00-SNAPSHOT \
    feature/V1.2.3-SNAPSHOT \
    feature/V1.2.3-CODEX-WO-SS-20260905-056-extra \
    feature/V1.2.3-Codex-WO-SS-20260905-056 \
    feature/V1.2.3-CODEX-WO-BP-20260905-056 \
    feature/V1.2.3-CODEX-WO-SS-2026095-056 \
    feature/V1.2.3-CODEX-WO-SS-20260905-56 \
    codex/ \
    codex/ci-001 \
    codex/ss-20260905-056-version-branch-workflow \
    codex/CI-001-gitlab \
    bot/ci-001-gitlab \
    hotfix/123-database; do
    if sh ci/check-branch-name.sh "$branch_name" >/dev/null 2>&1; then
        echo "FAIL: branche hors convention versionnée acceptée : $branch_name" >&2
        exit 1
    fi
done

for branch_name in \
    main \
    feature/V0.1.0 \
    feature/V0.1.0-RC01 \
    feature/V0.1.0-RC01-SNAPSHOT \
    feature/V1.20.300-RC04 \
    feature/V1.20.300-RC99-SNAPSHOT \
    release/V0.1.0 \
    release/V1.20.300-RC04 \
    release/V1.20.300-RC99-SNAPSHOT; do
    if ! sh ci/check-branch-name.sh "$branch_name" gitlab-branch >/dev/null; then
        echo "FAIL: branche de pipeline GitLab valide refusée : $branch_name" >&2
        exit 1
    fi
done

for branch_name in \
    codex/ss-20260905-055-version-branch-workflow \
    feature/V0.1.0-CODEX-WO-SS-20260905-056 \
    feature/V1.20.300-RC04-HUMAN-WO-SS-20260906-057 \
    codex/ci-001 \
    develop; do
    if sh ci/check-branch-name.sh "$branch_name" gitlab-branch >/dev/null 2>&1; then
        echo "FAIL: branche interdite acceptée dans un pipeline de branche GitLab : $branch_name" >&2
        exit 1
    fi
done

for branch_name in \
    main \
    feature/V0.1.0 \
    feature/V0.1.0-RC01 \
    feature/V0.1.0-RC01-SNAPSHOT \
    feature/V1.20.300-RC04 \
    feature/V1.20.300-RC99-SNAPSHOT \
    feature/V0.1.0-CODEX-WO-SS-20260905-056 \
    feature/V1.20.300-RC04-HUMAN-WO-SS-20260906-057; do
    if ! sh ci/check-branch-name.sh "$branch_name" github-branch >/dev/null; then
        echo "FAIL: branche GitHub valide refusée : $branch_name" >&2
        exit 1
    fi
done

for branch_name in \
    release/V0.1.0 \
    release/V1.20.300-RC04 \
    release/V1.20.300-RC99-SNAPSHOT \
    feature/V1.2.3-RC00 \
    codex/ss-20260905-055-version-branch-workflow \
    codex/ci-001; do
    if sh ci/check-branch-name.sh "$branch_name" github-branch >/dev/null 2>&1; then
        echo "FAIL: branche interdite acceptée par GitHub : $branch_name" >&2
        exit 1
    fi
done

if sh ci/check-branch-name.sh main contexte-inconnu >/dev/null 2>&1; then
    echo 'FAIL: un contexte de validation de branche inconnu a été accepté.' >&2
    exit 1
fi

printf 'BRANCH_NAMING_VERSIONED=PASS\n'

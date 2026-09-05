#!/usr/bin/env sh
set -eu

for branch_name in feature/V0.1.0 feature/V1.20.300-RC04 feature/V1.20.300-RC99-SNAPSHOT; do
    if ! sh ci/check-durable-snapshot-source.sh "$branch_name" >/dev/null; then
        echo "FAIL: source de snapshot durable valide refusée : $branch_name" >&2
        exit 1
    fi
done

for branch_name in \
    '' \
    main \
    release/V0.1.0 \
    feature/v0.1.0 \
    feature/V0.1.0-rc.1 \
    feature/V0.1.0-RC00 \
    feature/V0.1.0-SNAPSHOT \
    feature/V0.1.0-CODEX-WO-SS-20260905-056 \
    codex/ss-20260905-055-version-branch-workflow; do
    if sh ci/check-durable-snapshot-source.sh "$branch_name" >/dev/null 2>&1; then
        echo "FAIL: source de snapshot durable interdite acceptée : $branch_name" >&2
        exit 1
    fi
done

printf 'DURABLE_SNAPSHOT_SOURCE_TESTS=PASS\n'

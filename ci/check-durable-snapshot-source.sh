#!/usr/bin/env sh
set -eu

branch_name=${1:-}
train_version='(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)(-RC(0[1-9]|[1-9][0-9])(-SNAPSHOT)?)?'

if printf '%s' "$branch_name" | grep -Eq "^feature/V${train_version}$"; then
    printf 'DURABLE_SNAPSHOT_SOURCE=PASS:%s\n' "$branch_name"
    exit 0
fi

echo "FAIL: un snapshot durable exige une branche d'intégration feature/V<train> exacte, reçue : ${branch_name:-<vide>}." >&2
exit 1

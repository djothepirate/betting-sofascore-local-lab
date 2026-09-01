#!/usr/bin/env sh
set -eu

repository=$(git rev-parse --show-toplevel)
cd "$repository"

fixture=$(mktemp -d "${TMPDIR:-/tmp}/lab-secret-signal-test.XXXXXX")
cleanup_fixture() {
    rm -rf "$fixture" || :
}
trap cleanup_fixture 0
trap 'exit 129' HUP
trap 'exit 130' INT
trap 'exit 143' TERM

for signal_case in HUP:129 INT:130 TERM:143; do
    signal_name=${signal_case%%:*}
    expected_status=${signal_case#*:}
    signal_tmp="$fixture/$signal_name"
    mkdir -p "$signal_tmp"

    actual_status=0
    env TMPDIR="$signal_tmp" CI_SECRET_SCAN_SELF_SIGNAL="$signal_name" \
        sh ci/check-no-secrets.sh >"$fixture/$signal_name.log" 2>&1 || actual_status=$?
    if [ "$actual_status" -ne "$expected_status" ]; then
        echo "FAIL: statut $actual_status reçu pour $signal_name, attendu $expected_status." >&2
        cat "$fixture/$signal_name.log" >&2
        exit 1
    fi
    if find "$signal_tmp" -mindepth 1 -print -quit | grep -q .; then
        echo "FAIL: fichiers temporaires restants après $signal_name." >&2
        find "$signal_tmp" -mindepth 1 -print >&2
        exit 1
    fi
done

printf 'SECRET_SCAN_SIGNALS=PASS\n'

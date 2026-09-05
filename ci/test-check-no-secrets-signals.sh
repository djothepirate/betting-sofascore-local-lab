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

history_repository="$fixture/history"
mkdir -p "$history_repository/ci"
cp "$repository/ci/check-no-secrets.sh" "$history_repository/ci/check-no-secrets.sh"
git init -q -b main "$history_repository"
git -C "$history_repository" config user.name ci-fixture
git -C "$history_repository" config user.email ci-fixture.invalid@example.test
git -C "$history_repository" add ci/check-no-secrets.sh
git -C "$history_repository" commit -qm 'secret history fixture base'
history_base=$(git -C "$history_repository" rev-parse HEAD)

token_prefix=ghp_
token_suffix=abcdefghijklmnopqrstuvwxyz0123456789
printf '%s%s\n' "$token_prefix" "$token_suffix" \
    >"$history_repository/synthetic-secret.txt"
git -C "$history_repository" add synthetic-secret.txt
git -C "$history_repository" commit -qm 'add synthetic historical secret'
git -C "$history_repository" rm -q synthetic-secret.txt
git -C "$history_repository" commit -qm 'remove synthetic historical secret'

history_status=0
(
    cd "$history_repository"
    sh ci/check-no-secrets.sh "$history_base"
) >"$fixture/history.log" 2>&1 || history_status=$?
if [ "$history_status" -ne 1 ]; then
    echo "FAIL: le secret historique supprimé a produit le statut $history_status au lieu de 1." >&2
    cat "$fixture/history.log" >&2
    exit 1
fi
if ! grep -Fq 'github-token' "$fixture/history.log" \
   || ! grep -Fq 'synthetic-secret.txt [commit ' "$fixture/history.log"; then
    echo 'FAIL: le secret ajouté puis supprimé n’est pas attribué à son commit historique.' >&2
    cat "$fixture/history.log" >&2
    exit 1
fi

printf 'SECRET_SCAN_HISTORY=PASS_DELETED_COMMIT\n'

for full_history_case in empty zero; do
    full_history_status=0
    if [ "$full_history_case" = empty ]; then
        (
            cd "$history_repository"
            sh ci/check-no-secrets.sh
        ) >"$fixture/history-$full_history_case.log" 2>&1 || full_history_status=$?
    else
        (
            cd "$history_repository"
            sh ci/check-no-secrets.sh 0000000000000000000000000000000000000000
        ) >"$fixture/history-$full_history_case.log" 2>&1 || full_history_status=$?
    fi
    if [ "$full_history_status" -ne 1 ] \
       || ! grep -Fq 'synthetic-secret.txt [commit ' \
           "$fixture/history-$full_history_case.log"; then
        echo "FAIL: la base $full_history_case n’a pas déclenché le scan historique complet." >&2
        cat "$fixture/history-$full_history_case.log" >&2
        exit 1
    fi
done

printf 'SECRET_SCAN_FULL_HISTORY=PASS_EMPTY_AND_ZERO_BASE\n'

git_shim="$fixture/git-shim"
mkdir -p "$git_shim"
real_git=$(command -v git)
cat >"$git_shim/git" <<'GIT'
#!/usr/bin/env sh
if [ "${FAIL_GIT_OPERATION:-}" = cat-file ] \
   && [ "${1:-}" = cat-file ] && [ "${2:-}" = blob ]; then
    exit 77
fi
if [ "${FAIL_GIT_OPERATION:-}" = diff-tree ]; then
    case " $* " in *' diff-tree '*) exit 78 ;; esac
fi
exec "$REAL_GIT" "$@"
GIT
chmod +x "$git_shim/git"

for git_failure_case in cat-file diff-tree; do
    git_failure_status=0
    (
        cd "$history_repository"
        env PATH="$git_shim:$PATH" REAL_GIT="$real_git" \
            FAIL_GIT_OPERATION="$git_failure_case" \
            sh ci/check-no-secrets.sh "$history_base"
    ) >"$fixture/git-$git_failure_case.log" 2>&1 || git_failure_status=$?
    if [ "$git_failure_status" -ne 2 ]; then
        echo "FAIL: l’erreur Git $git_failure_case a produit $git_failure_status au lieu de 2." >&2
        cat "$fixture/git-$git_failure_case.log" >&2
        exit 1
    fi
done

printf 'SECRET_SCAN_GIT_ERRORS=PASS_FAIL_CLOSED\n'

canary_repository="$fixture/canary"
canary_path=scripts/Invoke-J5PlaywrightLoopbackQualification.ps1
mkdir -p "$canary_repository/ci" "$canary_repository/scripts"
private_prefix='-----BEGIN '
private_suffix='PRIVATE KEY-----'
printf '%s%s\n' "$private_prefix" "$private_suffix" >"$canary_repository/$canary_path"
canary_blob=$(git hash-object "$canary_repository/$canary_path")
sed "s/62c2eba3fb980d68589a9804d2fa81bcc865a039/$canary_blob/" \
    "$repository/ci/check-no-secrets.sh" >"$canary_repository/ci/check-no-secrets.sh"
git init -q -b main "$canary_repository"
git -C "$canary_repository" config user.name ci-fixture
git -C "$canary_repository" config user.email ci-fixture.invalid@example.test
git -C "$canary_repository" add ci/check-no-secrets.sh "$canary_path"
git -C "$canary_repository" commit -qm 'audited synthetic canary'
audited_canary_commit=$(git -C "$canary_repository" rev-parse HEAD)

if ! (
    cd "$canary_repository"
    sh ci/check-no-secrets.sh HEAD
) >"$fixture/canary-exact.log" 2>&1; then
    echo 'FAIL: le blob exact du canari synthétique audité a été refusé.' >&2
    cat "$fixture/canary-exact.log" >&2
    exit 1
fi

printf '%s%s\n' "$token_prefix" "$token_suffix" \
    >>"$canary_repository/$canary_path"
git -C "$canary_repository" add "$canary_path"
git -C "$canary_repository" commit -qm 'modify synthetic canary'
modified_canary_status=0
(
    cd "$canary_repository"
    sh ci/check-no-secrets.sh "$audited_canary_commit"
) >"$fixture/canary-modified.log" 2>&1 || modified_canary_status=$?
if [ "$modified_canary_status" -ne 1 ] \
   || ! grep -Fq "$canary_path [HEAD]" "$fixture/canary-modified.log"; then
    echo 'FAIL: un blob modifié du fichier canari est resté exempté.' >&2
    cat "$fixture/canary-modified.log" >&2
    exit 1
fi

printf 'SECRET_SCAN_CANARY_ALLOWLIST=PASS_EXACT_BLOB_ONLY\n'

git -C "$canary_repository" checkout -q "$audited_canary_commit" -- "$canary_path"
git -C "$canary_repository" commit -qm 'restore audited synthetic canary'
restored_canary_status=0
(
    cd "$canary_repository"
    sh ci/check-no-secrets.sh 0000000000000000000000000000000000000000
) >"$fixture/canary-restored.log" 2>&1 || restored_canary_status=$?
if [ "$restored_canary_status" -ne 1 ] \
   || ! grep -Fq "$canary_path [commit " "$fixture/canary-restored.log"; then
    echo 'FAIL: la modification transitoire du canari restauré a échappé à l’historique complet.' >&2
    cat "$fixture/canary-restored.log" >&2
    exit 1
fi

printf 'SECRET_SCAN_CANARY_HISTORY=PASS_TRANSIENT_MODIFICATION\n'

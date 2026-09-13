#!/usr/bin/env sh
set -eu

repository=$(git rev-parse --show-toplevel)
cd "$repository"
base_ref=${1:-}

candidate_file=$(mktemp "${TMPDIR:-/tmp}/lab-secret-candidates.XXXXXX")
blob_file=$(mktemp "${TMPDIR:-/tmp}/lab-secret-blob.XXXXXX")
commit_file=$(mktemp "${TMPDIR:-/tmp}/lab-secret-commits.XXXXXX")
cleanup() {
    rm -f "$candidate_file" "$blob_file" "$commit_file" || :
}
trap cleanup 0
trap 'exit 129' HUP
trap 'exit 130' INT
trap 'exit 143' TERM

# Point d'injection strictement réservé au test comportemental des traps ci-dessous.
case ${CI_SECRET_SCAN_SELF_SIGNAL:-} in
    '') ;;
    HUP|INT|TERM) kill "-${CI_SECRET_SCAN_SELF_SIGNAL}" "$$" ;;
    *) echo 'FAIL: signal de test du scanner invalide.' >&2; exit 2 ;;
esac

private_key_pattern='-----BEGIN[[:space:]]+((RSA|EC|DSA|OPENSSH|PGP|ENCRYPTED)[[:space:]]+)?PRIVATE[[:space:]]+KEY([[:space:]]+BLOCK)?-----'
aws_pattern='(^|[^A-Z0-9])(AKIA|ASIA)[A-Z0-9]{16}([^A-Z0-9]|$)'
github_pattern='(^|[^A-Za-z0-9_])(gh[pousr]_[A-Za-z0-9]{36,255}|github_pat_[A-Za-z0-9_]{80,255})([^A-Za-z0-9_]|$)'
gitlab_pattern='(^|[^A-Za-z0-9_-])glpat-[A-Za-z0-9_-]{20,255}([^A-Za-z0-9_-]|$)'
google_pattern='(^|[^A-Za-z0-9_-])AIza[A-Za-z0-9_-]{35}([^A-Za-z0-9_-]|$)'
slack_pattern='(^|[^A-Za-z0-9-])xox[baprs]-[A-Za-z0-9-]{10,255}([^A-Za-z0-9-]|$)'
stripe_pattern='(^|[^A-Za-z0-9_])sk_(live|test)_[A-Za-z0-9]{16,255}([^A-Za-z0-9]|$)'
sendgrid_pattern='(^|[^A-Za-z0-9_.-])SG\.[A-Za-z0-9_-]{20,}\.[A-Za-z0-9_-]{20,}([^A-Za-z0-9_.-]|$)'
jwt_pattern='(^|[^A-Za-z0-9_-])eyJ[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}([^A-Za-z0-9_-]|$)'

findings=0
is_vetted_synthetic_fixture() {
    path=$1
    revision=$2
    case "$path" in
        scripts/Invoke-J5PlaywrightLoopbackQualification.ps1)
            expected_blob=62c2eba3fb980d68589a9804d2fa81bcc865a039
            # Version historique J5 : les mêmes canaris synthétiques, avant
            # l'extension des tests de délai et d'observation réseau.
            historical_blob=ba6e319cc14b05417157522b8dd90e7abd3e2528
            # WO-058 : la régression de plage TCP puis sa note de périmètre
            # n'ont pas modifié les canaris synthétiques audités ci-dessus.
            port_range_blob=07f3a3230f9ad5b03f2d66fcc16dff2528bf0dcb
            scope_note_blob=50376283613e5070c03684b0179b21e257826ed2
            ;;
        *)
            return 1
            ;;
    esac
    actual_blob=$(git rev-parse "$revision:$path" 2>/dev/null || true)
    [ "$actual_blob" = "$expected_blob" ] || [ "$actual_blob" = "$historical_blob" ] \
        || [ "$actual_blob" = "$port_range_blob" ] || [ "$actual_blob" = "$scope_note_blob" ]
}

scan_blob() {
    path=$1
    label=$2
    matched=
    for definition in \
        "private-key:$private_key_pattern" \
        "aws-access-key:$aws_pattern" \
        "github-token:$github_pattern" \
        "gitlab-token:$gitlab_pattern" \
        "google-api-key:$google_pattern" \
        "slack-token:$slack_pattern" \
        "stripe-secret-key:$stripe_pattern" \
        "sendgrid-api-key:$sendgrid_pattern" \
        "jwt:$jwt_pattern"
    do
        rule=${definition%%:*}
        pattern=${definition#*:}
        if LC_ALL=C grep -Eiq -e "$pattern" "$path" 2>/dev/null; then
            if [ -z "$matched" ]; then matched=$rule; else matched=$matched,$rule; fi
        fi
    done
    if [ -n "$matched" ]; then
        printf 'FAIL: valeur sensible potentielle (%s) dans %s.\n' "$matched" "$label" >&2
        findings=1
    fi
}

git -c core.quotepath=false ls-files >"$candidate_file"
while IFS= read -r path; do
    [ -n "$path" ] || continue
    case "$path" in target/*|exports/*|docs/reference/*) continue ;; esac
    # Ces deux blobs audités contiennent les valeurs synthétiques du test de fuite J5.
    # La moindre modification change son SHA et réactive le scan fail-closed.
    if is_vetted_synthetic_fixture "$path" HEAD; then continue; fi
    if ! git cat-file blob "HEAD:$path" >"$blob_file" 2>/dev/null; then
        echo "FAIL: lecture Git impossible pour $path [HEAD]." >&2
        exit 2
    fi
    scan_blob "$blob_file" "$path [HEAD]"
done <"$candidate_file"

if [ -n "$base_ref" ] && ! printf '%s' "$base_ref" | grep -Eq '^0+$'; then
    if ! git cat-file -e "${base_ref}^{commit}" 2>/dev/null; then
        echo 'FAIL: base Git explicite introuvable pendant le contrôle de secrets.' >&2
        exit 2
    fi
    revision_range="${base_ref}..HEAD"
else
    # Tags, premiers pushs et pipelines manuels n'ont pas toujours de base exploitable.
    # Dans ce cas, le contrôle strict parcourt tout l'historique atteignable.
    revision_range=HEAD
fi
if ! git rev-list --reverse "$revision_range" >"$commit_file"; then
    echo 'FAIL: impossible d’énumérer l’historique Git pendant le contrôle de secrets.' >&2
    exit 2
fi
while IFS= read -r commit; do
    [ -n "$commit" ] || continue
    if ! git -c core.quotepath=false diff-tree --root -m --no-commit-id --name-only -r \
        --diff-filter=ACMR "$commit" -- >"$candidate_file"; then
        echo "FAIL: impossible d’énumérer les fichiers du commit $commit." >&2
        exit 2
    fi
    if ! LC_ALL=C sort -u -o "$candidate_file" "$candidate_file"; then
        echo "FAIL: impossible d’ordonner les fichiers du commit $commit." >&2
        exit 2
    fi
    while IFS= read -r path; do
        [ -n "$path" ] || continue
        case "$path" in target/*|exports/*|docs/reference/*) continue ;; esac
        if is_vetted_synthetic_fixture "$path" "$commit"; then continue; fi
        if ! git cat-file blob "$commit:$path" >"$blob_file" 2>/dev/null; then
            echo "FAIL: lecture Git impossible pour $path [commit $commit]." >&2
            exit 2
        fi
        scan_blob "$blob_file" "$path [commit $(printf '%.12s' "$commit")]"
    done <"$candidate_file"
done <"$commit_file"

if [ "$findings" -ne 0 ]; then
    exit 1
fi
printf 'SECRET_SCAN=PASS_HIGH_CONFIDENCE\n'

#!/usr/bin/env sh
set -eu

repository=$(git rev-parse --show-toplevel)
cd "$repository"
base_ref=${1:-}

candidate_file=$(mktemp "${TMPDIR:-/tmp}/lab-secret-candidates.XXXXXX")
blob_file=$(mktemp "${TMPDIR:-/tmp}/lab-secret-blob.XXXXXX")
commit_file=$(mktemp "${TMPDIR:-/tmp}/lab-secret-commits.XXXXXX")
trap 'rm -f "$candidate_file" "$blob_file" "$commit_file"' EXIT HUP INT TERM

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
    if git cat-file blob "HEAD:$path" >"$blob_file" 2>/dev/null; then
        scan_blob "$blob_file" "$path [HEAD]"
    fi
done <"$candidate_file"

if [ -n "$base_ref" ] && ! printf '%s' "$base_ref" | grep -Eq '^0+$'; then
    if ! git cat-file -e "${base_ref}^{commit}" 2>/dev/null; then
        echo 'FAIL: base Git explicite introuvable pendant le contrôle de secrets.' >&2
        exit 2
    fi
    git rev-list --reverse "${base_ref}..HEAD" >"$commit_file"
    while IFS= read -r commit; do
        [ -n "$commit" ] || continue
        git -c core.quotepath=false diff-tree --root -m --no-commit-id --name-only -r \
            --diff-filter=ACMR "$commit" -- | LC_ALL=C sort -u >"$candidate_file"
        while IFS= read -r path; do
            [ -n "$path" ] || continue
            case "$path" in target/*|exports/*|docs/reference/*) continue ;; esac
            if git cat-file blob "$commit:$path" >"$blob_file" 2>/dev/null; then
                scan_blob "$blob_file" "$path [commit $(printf '%.12s' "$commit")]"
            fi
        done <"$candidate_file"
    done <"$commit_file"
fi

if [ "$findings" -ne 0 ]; then
    exit 1
fi
printf 'SECRET_SCAN=PASS_HIGH_CONFIDENCE\n'

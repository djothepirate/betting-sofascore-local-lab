#!/usr/bin/env sh
set -eu

branch_name=${1:-}
context=${2:-general}

train_version='(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)(-RC(0[1-9]|[1-9][0-9])(-SNAPSHOT)?)?'

case "$context" in
    general|gitlab-branch) ;;
    *)
        echo "FAIL: contexte de validation de branche inconnu : $context." >&2
        exit 1
        ;;
esac

accept_branch() {
    kind=$1
    if [ "$context" = gitlab-branch ]; then
        case "$kind" in
            MAIN|FEATURE_INTEGRATION|RELEASE_GITLAB_ONLY) ;;
            *)
                echo "FAIL: la branche $branch_name de type $kind est interdite dans un pipeline de branche GitLab." >&2
                exit 1
                ;;
        esac
    fi
    printf 'BRANCH_NAME=PASS:%s\n' "$branch_name"
    printf 'BRANCH_KIND=%s\n' "$kind"
    exit 0
}

if [ "$branch_name" = main ]; then
    accept_branch MAIN
fi

# Exception à usage unique : elle permet à WO-055 d'installer la nouvelle
# convention depuis la dernière base relevant encore de la convention 1A.
if [ "$branch_name" = codex/ss-20260905-055-version-branch-workflow ]; then
    accept_branch BOOTSTRAP_WO055
fi

if printf '%s' "$branch_name" | grep -Eq "^feature/V${train_version}$"; then
    accept_branch FEATURE_INTEGRATION
fi

if printf '%s' "$branch_name" | grep -Eq \
    "^feature/V${train_version}-(CODEX|HUMAN)-WO-SS-[0-9]{8}-[0-9]{3}$"; then
    accept_branch WORK_ORDER
fi

if printf '%s' "$branch_name" | grep -Eq "^release/V${train_version}$"; then
    accept_branch RELEASE_GITLAB_ONLY
fi

echo "FAIL: branche hors convention versionnée : $branch_name" >&2
exit 1

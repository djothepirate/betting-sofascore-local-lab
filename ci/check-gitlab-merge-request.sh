#!/usr/bin/env sh
set -eu

pipeline_source=${CI_PIPELINE_SOURCE:-}
if [ "$pipeline_source" != merge_request_event ]; then
    printf 'GITLAB_MR_POLICY=SKIP:%s\n' "${pipeline_source:-unset}"
    exit 0
fi

source_branch=${CI_MERGE_REQUEST_SOURCE_BRANCH_NAME:-}
target_branch=${CI_MERGE_REQUEST_TARGET_BRANCH_NAME:-}
project_id=${CI_PROJECT_ID:-}
source_project_id=${CI_MERGE_REQUEST_SOURCE_PROJECT_ID:-}
merge_request_project_id=${CI_MERGE_REQUEST_PROJECT_ID:-}
target_branch_protected=${CI_MERGE_REQUEST_TARGET_BRANCH_PROTECTED:-}

if [ -z "$project_id" ] || [ -z "$source_project_id" ] ||
   [ -z "$merge_request_project_id" ] ||
   [ "$source_project_id" != "$merge_request_project_id" ] ||
   [ "$project_id" != "$merge_request_project_id" ]; then
    echo "FAIL: une MR GitLab de promotion doit rester dans le projet cible, IDs reçus : source=${source_project_id:-<vide>}, cible=${merge_request_project_id:-<vide>}, pipeline=${project_id:-<vide>}." >&2
    exit 1
fi
if [ "$target_branch_protected" != true ]; then
    echo "FAIL: la cible GitLab ${target_branch:-<vide>} doit être protégée." >&2
    exit 1
fi

script_dir=$(CDPATH= cd -- "$(dirname "$0")" && pwd)
if ! sh "$script_dir/check-branch-name.sh" "$source_branch" >/dev/null 2>&1 ||
   ! sh "$script_dir/check-branch-name.sh" "$target_branch" >/dev/null 2>&1; then
    echo "FAIL: branches de MR GitLab hors convention : ${source_branch:-<vide>} -> ${target_branch:-<vide>}." >&2
    exit 1
fi

case "$source_branch" in
    feature/V*) release_version=${source_branch#feature/V} ;;
    *)
        echo "FAIL: une MR GitLab doit partir de feature/V<train>, source reçue : ${source_branch:-<vide>}." >&2
        exit 1
        ;;
esac
expected_target="release/V$release_version"
if [ "$target_branch" != "$expected_target" ]; then
    echo "FAIL: la MR GitLab $source_branch doit cibler $expected_target, cible reçue : ${target_branch:-<vide>}." >&2
    exit 1
fi

case "$release_version" in
    *-RC??-SNAPSHOT)
        release_core=${release_version%%-RC*}
        release_rc=${release_version#*-RC}
        release_rc=${release_rc%-SNAPSHOT}
        release_rc=${release_rc#0}
        expected_project_version="${release_core}-rc.${release_rc}-SNAPSHOT"
        ;;
    *-RC??)
        release_core=${release_version%%-RC*}
        release_rc=${release_version#*-RC}
        release_rc=${release_rc#0}
        expected_project_version="${release_core}-rc.${release_rc}"
        ;;
    *)
        expected_project_version=$release_version
        ;;
esac

project_version=${1:-}
source_sha=${2:-${CI_MERGE_REQUEST_SOURCE_BRANCH_SHA:-${CI_COMMIT_SHA:-}}}
if [ "$project_version" != "$expected_project_version" ]; then
    echo "FAIL: la promotion $source_branch exige la version Maven $expected_project_version, reçue : ${project_version:-<vide>}." >&2
    exit 1
fi

repository=$(CDPATH= cd -- "$script_dir/.." && pwd)
case "$source_sha" in
    *[!0-9a-f]*|'')
        echo "FAIL: SHA source canonique de MR invalide : ${source_sha:-<vide>}." >&2
        exit 1
        ;;
esac
if ! canonical_source_commit=$(git -C "$repository" rev-parse --verify "${source_sha}^{commit}" 2>/dev/null); then
    echo "FAIL: SHA source canonique de MR introuvable : $source_sha." >&2
    exit 1
fi
if ! checkout_commit=$(git -C "$repository" rev-parse --verify 'HEAD^{commit}' 2>/dev/null); then
    echo 'FAIL: checkout GitLab de MR introuvable.' >&2
    exit 1
fi
if [ "$checkout_commit" != "$canonical_source_commit" ]; then
    echo "FAIL: le checkout GitLab $checkout_commit doit être le SHA source canonique exact $canonical_source_commit." >&2
    exit 1
fi
if ! shallow=$(git -C "$repository" rev-parse --is-shallow-repository 2>/dev/null); then
    echo 'FAIL: impossible de déterminer si le dépôt MR est shallow.' >&2
    exit 1
fi
if [ "$shallow" != false ]; then
    echo 'FAIL: un historique Git complet est requis pour qualifier une MR de promotion.' >&2
    exit 1
fi

main_ref=refs/remotes/origin/main
source_ref="refs/remotes/origin/$source_branch"
target_ref="refs/remotes/origin/$target_branch"
if ! main_commit=$(git -C "$repository" rev-parse --verify "${main_ref}^{commit}" 2>/dev/null); then
    echo "FAIL: référence main canonique introuvable : $main_ref." >&2
    exit 1
fi
if ! source_commit=$(git -C "$repository" rev-parse --verify "${source_ref}^{commit}" 2>/dev/null); then
    echo "FAIL: référence feature source introuvable : $source_ref." >&2
    exit 1
fi
if ! target_commit=$(git -C "$repository" rev-parse --verify "${target_ref}^{commit}" 2>/dev/null); then
    echo "FAIL: référence release cible introuvable : $target_ref." >&2
    exit 1
fi
if [ "$source_commit" != "$canonical_source_commit" ]; then
    echo "FAIL: $source_ref doit désigner le SHA source canonique exact $canonical_source_commit, reçu : $source_commit." >&2
    exit 1
fi
if [ "$main_commit" != "$canonical_source_commit" ]; then
    echo "FAIL: $main_ref doit désigner le SHA source canonique exact $canonical_source_commit, reçu : $main_commit." >&2
    exit 1
fi
if git -C "$repository" merge-base --is-ancestor "$target_commit" "$canonical_source_commit"; then
    :
else
    ancestry_status=$?
    if [ "$ancestry_status" -eq 1 ]; then
        echo "FAIL: $target_ref doit être un ancêtre du SHA source $canonical_source_commit pour une MR fast-forward." >&2
    else
        echo "FAIL: impossible de vérifier l'ascendance de $target_ref vers $canonical_source_commit." >&2
    fi
    exit 1
fi

case "$release_version" in
    *-SNAPSHOT) ;;
    *)
        release_tag="v$expected_project_version"
        if git -C "$repository" show-ref --verify --quiet "refs/tags/$release_tag"; then
            echo "FAIL: la branche $target_branch est scellée par le tag $release_tag et ne peut plus avancer." >&2
            exit 1
        fi
        ;;
esac

printf 'GITLAB_MR_POLICY=PASS:%s->%s\n' "$source_branch" "$target_branch"

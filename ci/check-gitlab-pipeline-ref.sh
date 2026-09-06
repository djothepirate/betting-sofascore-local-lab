#!/usr/bin/env sh
set -eu

pipeline_source=${CI_PIPELINE_SOURCE:-}
branch_name=${CI_COMMIT_BRANCH:-}
tag_name=${CI_COMMIT_TAG:-}
merge_request_source=${CI_MERGE_REQUEST_SOURCE_BRANCH_NAME:-}
merge_request_target=${CI_MERGE_REQUEST_TARGET_BRANCH_NAME:-}

if [ -z "$pipeline_source" ]; then
    echo 'FAIL: source du pipeline GitLab indéterminée.' >&2
    exit 1
fi

if [ -n "$tag_name" ]; then
    if [ -n "$branch_name" ] || [ "$pipeline_source" = merge_request_event ] ||
       [ -n "$merge_request_source" ] || [ -n "$merge_request_target" ]; then
        echo 'FAIL: contexte GitLab ambigu entre tag, branche et Merge Request.' >&2
        exit 1
    fi
    printf 'GITLAB_REF_CONTEXT=TAG:%s\n' "$tag_name"
    exit 0
fi

if [ "$pipeline_source" = merge_request_event ]; then
    if [ -n "$branch_name" ] || [ -z "$merge_request_source" ] ||
       [ -z "$merge_request_target" ]; then
        echo 'FAIL: une Merge Request GitLab exige ses branches source et cible dédiées, sans CI_COMMIT_BRANCH.' >&2
        exit 1
    fi
    printf 'GITLAB_REF_CONTEXT=MERGE_REQUEST:%s->%s\n' \
        "$merge_request_source" "$merge_request_target"
    exit 0
fi

if [ -z "$branch_name" ]; then
    echo "FAIL: le pipeline GitLab $pipeline_source ne possède ni tag, ni Merge Request, ni branche déterminée." >&2
    exit 1
fi
if [ -n "$merge_request_source" ] || [ -n "$merge_request_target" ]; then
    echo 'FAIL: un pipeline de branche GitLab ne doit pas porter de contexte Merge Request.' >&2
    exit 1
fi

script_dir=$(CDPATH= cd -- "$(dirname "$0")" && pwd)
sh "$script_dir/check-branch-name.sh" "$branch_name" gitlab-branch >/dev/null
printf 'GITLAB_REF_CONTEXT=BRANCH:%s:%s\n' "$pipeline_source" "$branch_name"

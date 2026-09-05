#!/usr/bin/env sh
set -eu

script_dir=$(CDPATH= cd -- "$(dirname "$0")" && pwd)
checker="$script_dir/check-gitlab-pipeline-ref.sh"

run_branch_context() {
    pipeline_source=$1
    branch_name=$2
    env \
        CI_PIPELINE_SOURCE="$pipeline_source" \
        CI_COMMIT_BRANCH="$branch_name" \
        CI_COMMIT_TAG= \
        CI_MERGE_REQUEST_SOURCE_BRANCH_NAME= \
        CI_MERGE_REQUEST_TARGET_BRANCH_NAME= \
        sh "$checker"
}

for pipeline_source in push web schedule api trigger pipeline; do
    for branch_name in \
        main \
        feature/V0.1.0 \
        feature/V1.2.3-RC04 \
        feature/V1.2.3-RC99-SNAPSHOT \
        release/V0.1.0 \
        release/V1.2.3-RC04 \
        release/V1.2.3-RC99-SNAPSHOT; do
        if ! run_branch_context "$pipeline_source" "$branch_name" >/dev/null; then
            echo "FAIL: pipeline GitLab valide refusé : $pipeline_source sur $branch_name." >&2
            exit 1
        fi
    done

    for branch_name in \
        codex/ss-20260905-055-version-branch-workflow \
        feature/V0.1.0-CODEX-WO-SS-20260905-056 \
        feature/V1.2.3-RC04-HUMAN-WO-SS-20260906-057 \
        codex/ci-001 \
        develop; do
        if run_branch_context "$pipeline_source" "$branch_name" >/dev/null 2>&1; then
            echo "FAIL: pipeline GitLab interdit accepté : $pipeline_source sur $branch_name." >&2
            exit 1
        fi
    done
done

if ! env \
    CI_PIPELINE_SOURCE=merge_request_event \
    CI_COMMIT_BRANCH= \
    CI_COMMIT_TAG= \
    CI_MERGE_REQUEST_SOURCE_BRANCH_NAME=feature/V1.2.3 \
    CI_MERGE_REQUEST_TARGET_BRANCH_NAME=release/V1.2.3 \
    sh "$checker" >/dev/null; then
    echo 'FAIL: le contexte dédié d’une MR GitLab valide a été refusé.' >&2
    exit 1
fi

if ! env \
    CI_PIPELINE_SOURCE=push \
    CI_COMMIT_BRANCH= \
    CI_COMMIT_TAG=v1.2.3 \
    CI_MERGE_REQUEST_SOURCE_BRANCH_NAME= \
    CI_MERGE_REQUEST_TARGET_BRANCH_NAME= \
    sh "$checker" >/dev/null; then
    echo 'FAIL: le contexte dédié d’un tag GitLab valide a été refusé.' >&2
    exit 1
fi

if env CI_PIPELINE_SOURCE=web CI_COMMIT_BRANCH= CI_COMMIT_TAG= \
    CI_MERGE_REQUEST_SOURCE_BRANCH_NAME= CI_MERGE_REQUEST_TARGET_BRANCH_NAME= \
    sh "$checker" >/dev/null 2>&1; then
    echo 'FAIL: un pipeline GitLab sans référence déterminée a été accepté.' >&2
    exit 1
fi
if env CI_PIPELINE_SOURCE= CI_COMMIT_BRANCH=main CI_COMMIT_TAG= \
    CI_MERGE_REQUEST_SOURCE_BRANCH_NAME= CI_MERGE_REQUEST_TARGET_BRANCH_NAME= \
    sh "$checker" >/dev/null 2>&1; then
    echo 'FAIL: un pipeline GitLab sans source déterminée a été accepté.' >&2
    exit 1
fi
if env CI_PIPELINE_SOURCE=merge_request_event CI_COMMIT_BRANCH= CI_COMMIT_TAG= \
    CI_MERGE_REQUEST_SOURCE_BRANCH_NAME=feature/V1.2.3 \
    CI_MERGE_REQUEST_TARGET_BRANCH_NAME= \
    sh "$checker" >/dev/null 2>&1; then
    echo 'FAIL: une MR GitLab sans cible déterminée a été acceptée.' >&2
    exit 1
fi
if env CI_PIPELINE_SOURCE=merge_request_event CI_COMMIT_BRANCH=feature/V1.2.3 \
    CI_COMMIT_TAG= CI_MERGE_REQUEST_SOURCE_BRANCH_NAME=feature/V1.2.3 \
    CI_MERGE_REQUEST_TARGET_BRANCH_NAME=release/V1.2.3 \
    sh "$checker" >/dev/null 2>&1; then
    echo 'FAIL: une MR GitLab ambiguë avec CI_COMMIT_BRANCH a été acceptée.' >&2
    exit 1
fi
if env CI_PIPELINE_SOURCE=push CI_COMMIT_BRANCH=main CI_COMMIT_TAG=v1.2.3 \
    CI_MERGE_REQUEST_SOURCE_BRANCH_NAME= CI_MERGE_REQUEST_TARGET_BRANCH_NAME= \
    sh "$checker" >/dev/null 2>&1; then
    echo 'FAIL: un contexte GitLab ambigu entre tag et branche a été accepté.' >&2
    exit 1
fi
if env CI_PIPELINE_SOURCE=api CI_COMMIT_BRANCH=main CI_COMMIT_TAG= \
    CI_MERGE_REQUEST_SOURCE_BRANCH_NAME=feature/V1.2.3 \
    CI_MERGE_REQUEST_TARGET_BRANCH_NAME=release/V1.2.3 \
    sh "$checker" >/dev/null 2>&1; then
    echo 'FAIL: un pipeline de branche portant un contexte MR a été accepté.' >&2
    exit 1
fi

printf 'GITLAB_PIPELINE_REF_TESTS=PASS\n'

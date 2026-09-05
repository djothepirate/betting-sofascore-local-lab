#!/usr/bin/env sh
set -eu

repository=$(git rev-parse --show-toplevel)
fixture=$(mktemp -d "${TMPDIR:-/tmp}/gitlab-mr-policy.XXXXXX")
shallow_fixture="${fixture}-shallow"
cleanup() {
    rm -rf "$fixture" "$shallow_fixture"
}
trap cleanup EXIT HUP INT TERM

mkdir -p "$fixture/ci"
cp "$repository/ci/check-branch-name.sh" \
   "$repository/ci/check-gitlab-merge-request.sh" "$fixture/ci/"
(
    cd "$fixture"
    git init -q -b fixture
    git config user.name ci-fixture
    git config user.email ci-fixture.invalid@example.test
    git commit --allow-empty -qm 'release base'
    release_base=$(git rev-parse HEAD)
    git commit --allow-empty -qm 'feature source aligned with main'
    source_commit=$(git rev-parse HEAD)
    source_tree=$(git rev-parse "${source_commit}^{tree}")
    divergent_target=$(printf 'divergent release\n' | \
        git commit-tree "$source_tree" -p "$release_base")

    run_policy() {
        policy_source_branch=$1
        policy_target_branch=$2
        policy_project_version=$3
        policy_source_sha=$4
        policy_source_project_id=${5:-100}
        policy_merge_request_project_id=${6:-100}
        policy_pipeline_project_id=${7:-100}
        policy_target_protected=${8:-true}
        env \
            CI_PIPELINE_SOURCE=merge_request_event \
            CI_PROJECT_ID="$policy_pipeline_project_id" \
            CI_MERGE_REQUEST_PROJECT_ID="$policy_merge_request_project_id" \
            CI_MERGE_REQUEST_SOURCE_PROJECT_ID="$policy_source_project_id" \
            CI_MERGE_REQUEST_TARGET_BRANCH_PROTECTED="$policy_target_protected" \
            CI_MERGE_REQUEST_SOURCE_BRANCH_NAME="$policy_source_branch" \
            CI_MERGE_REQUEST_TARGET_BRANCH_NAME="$policy_target_branch" \
            CI_COMMIT_SHA="$policy_source_sha" \
            sh ci/check-gitlab-merge-request.sh \
                "$policy_project_version" "$policy_source_sha"
    }

    assert_allowed() {
        description=$1
        shift
        if ! run_policy "$@" >/dev/null; then
            echo "FAIL: MR GitLab valide refusée : $description" >&2
            exit 1
        fi
    }

    assert_rejected() {
        description=$1
        shift
        if run_policy "$@" >/dev/null 2>&1; then
            echo "FAIL: MR GitLab hors politique acceptée : $description" >&2
            exit 1
        fi
    }

    set_train_refs() {
        train=$1
        git update-ref refs/remotes/origin/main "$source_commit"
        git update-ref "refs/remotes/origin/feature/V${train}" "$source_commit"
        git update-ref "refs/remotes/origin/release/V${train}" "$release_base"
    }

    env CI_PIPELINE_SOURCE=push sh ci/check-gitlab-merge-request.sh >/dev/null

    set_train_refs 1.2.3
    assert_allowed 'stable alignée' feature/V1.2.3 release/V1.2.3 1.2.3 "$source_commit"
    git checkout -q --detach "$release_base"
    assert_rejected 'checkout différent du tip source canonique' \
        feature/V1.2.3 release/V1.2.3 1.2.3 "$source_commit"
    git checkout -q --detach "$source_commit"
    set_train_refs 1.2.3-RC04
    assert_allowed 'RC04 alignée' feature/V1.2.3-RC04 release/V1.2.3-RC04 \
        1.2.3-rc.4 "$source_commit"
    set_train_refs 1.2.3-RC99-SNAPSHOT
    assert_allowed 'RC99-SNAPSHOT alignée' \
        feature/V1.2.3-RC99-SNAPSHOT release/V1.2.3-RC99-SNAPSHOT \
        1.2.3-rc.99-SNAPSHOT "$source_commit"

    set_train_refs 1.2.3
    assert_rejected 'source main interdite' main release/V1.2.3 1.2.3 "$source_commit"
    assert_rejected 'cible main interdite' feature/V1.2.3 main 1.2.3 "$source_commit"
    assert_rejected 'train cible différent' feature/V1.2.3 release/V1.2.4 1.2.3 "$source_commit"
    assert_rejected 'branche WO interdite' \
        feature/V1.2.3-CODEX-WO-SS-20260905-056 release/V1.2.3 1.2.3 "$source_commit"
    assert_rejected 'version stable snapshot interdite' \
        feature/V1.2.3 release/V1.2.3 1.2.3-SNAPSHOT "$source_commit"
    assert_rejected 'version différente' feature/V1.2.3 release/V1.2.3 1.2.4 "$source_commit"
    assert_rejected 'projet source externe' \
        feature/V1.2.3 release/V1.2.3 1.2.3 "$source_commit" 200 100 100 true
    assert_rejected 'pipeline dans un autre projet' \
        feature/V1.2.3 release/V1.2.3 1.2.3 "$source_commit" 200 100 200 true
    assert_rejected 'cible non protégée' \
        feature/V1.2.3 release/V1.2.3 1.2.3 "$source_commit" 100 100 100 false

    if env \
        CI_PIPELINE_SOURCE=merge_request_event \
        CI_MERGE_REQUEST_SOURCE_BRANCH_NAME=feature/V1.2.3 \
        CI_MERGE_REQUEST_TARGET_BRANCH_NAME=release/V1.2.3 \
        sh ci/check-gitlab-merge-request.sh 1.2.3 "$source_commit" >/dev/null 2>&1; then
        echo 'FAIL: une MR sans identité de projet a été acceptée.' >&2
        exit 1
    fi

    git update-ref refs/remotes/origin/main "$release_base"
    assert_rejected 'main non aligné sur la feature' \
        feature/V1.2.3 release/V1.2.3 1.2.3 "$source_commit"
    git update-ref refs/remotes/origin/main "$source_commit"

    git update-ref refs/remotes/origin/feature/V1.2.3 "$release_base"
    assert_rejected 'feature différente du SHA source canonique' \
        feature/V1.2.3 release/V1.2.3 1.2.3 "$source_commit"
    git update-ref refs/remotes/origin/feature/V1.2.3 "$source_commit"
    assert_rejected 'SHA source canonique différent de la feature' \
        feature/V1.2.3 release/V1.2.3 1.2.3 "$release_base"

    git update-ref refs/remotes/origin/release/V1.2.3 "$divergent_target"
    assert_rejected 'release divergente, non fast-forward' \
        feature/V1.2.3 release/V1.2.3 1.2.3 "$source_commit"
    git update-ref -d refs/remotes/origin/release/V1.2.3
    assert_rejected 'release absente' \
        feature/V1.2.3 release/V1.2.3 1.2.3 "$source_commit"
    git update-ref refs/remotes/origin/release/V1.2.3 "$release_base"

    git tag v1.2.3 "$source_commit"
    assert_rejected 'release stable déjà scellée' \
        feature/V1.2.3 release/V1.2.3 1.2.3 "$source_commit"
    git tag -d v1.2.3 >/dev/null

    set_train_refs 1.2.3-RC01
    git tag v1.2.3-rc.1 "$source_commit"
    assert_rejected 'release RC01 déjà scellée par rc.1' \
        feature/V1.2.3-RC01 release/V1.2.3-RC01 1.2.3-rc.1 "$source_commit"
)

git clone -q --depth 1 "file://$fixture" "$shallow_fixture"
mkdir -p "$shallow_fixture/ci"
cp "$repository/ci/check-branch-name.sh" \
   "$repository/ci/check-gitlab-merge-request.sh" "$shallow_fixture/ci/"
(
    cd "$shallow_fixture"
    shallow_head=$(git rev-parse HEAD)
    if env \
        CI_PIPELINE_SOURCE=merge_request_event \
        CI_PROJECT_ID=100 \
        CI_MERGE_REQUEST_PROJECT_ID=100 \
        CI_MERGE_REQUEST_SOURCE_PROJECT_ID=100 \
        CI_MERGE_REQUEST_TARGET_BRANCH_PROTECTED=true \
        CI_MERGE_REQUEST_SOURCE_BRANCH_NAME=feature/V1.2.3 \
        CI_MERGE_REQUEST_TARGET_BRANCH_NAME=release/V1.2.3 \
        CI_COMMIT_SHA="$shallow_head" \
        sh ci/check-gitlab-merge-request.sh 1.2.3 "$shallow_head" >/dev/null 2>&1; then
        echo 'FAIL: une MR qualifiée depuis un historique shallow a été acceptée.' >&2
        exit 1
    fi
)

printf 'GITLAB_MR_POLICY_TESTS=PASS\n'

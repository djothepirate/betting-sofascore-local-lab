#!/usr/bin/env sh
set -eu

repository=$(git rev-parse --show-toplevel)
fixture=$(mktemp -d /tmp/sofascore-release-fixture.XXXXXX)

cleanup() {
    rm -rf "$fixture" || :
}
trap cleanup 0
trap 'exit 129' HUP
trap 'exit 130' INT
trap 'exit 143' TERM

mkdir -p "$fixture/ci/distribution" "$fixture/target" "$fixture/scripts"
cp "$repository/ci/package-local-only.sh" "$fixture/ci/package-local-only.sh"
cp "$repository/ci/check-branch-name.sh" "$fixture/ci/check-branch-name.sh"
cp "$repository/ci/check-durable-snapshot-source.sh" \
    "$fixture/ci/check-durable-snapshot-source.sh"
cp "$repository/ci/distribution/README.md" "$fixture/ci/distribution/README.md"
cp "$repository/ci/distribution/Preflight-Local.ps1" \
    "$fixture/ci/distribution/Preflight-Local.ps1"
cp "$repository/ci/distribution/Start-Local.ps1" \
    "$fixture/ci/distribution/Start-Local.ps1"
cp "$repository/ci/distribution/Stop-Local.ps1" \
    "$fixture/ci/distribution/Stop-Local.ps1"
cp "$repository/scripts/Initialize-LocalConfig.ps1" \
    "$fixture/scripts/Initialize-LocalConfig.ps1"
printf '#!/usr/bin/env sh\nexit 0\n' >"$fixture/ci/assert-local-only.sh"
printf 'fixture-jar\n' >"$fixture/target/application.jar"
for file in .env.example compose.yaml SECURITY.md; do
    printf 'release fixture\n' >"$fixture/$file"
done

cat >"$fixture/mvnw" <<'MVNW'
#!/usr/bin/env sh
set -eu

for arg in "$@"; do
    if [ "$arg" = help:evaluate ]; then
        printf '%s\n' "${FIXTURE_MAVEN_VERSION:-1.2.3}"
        exit 0
    fi
done

output_dir=
project_type=
for arg in "$@"; do
    case "$arg" in
        -DoutputDirectory=*)
            output_dir=$(printf '%s' "$arg" | sed 's/^-DoutputDirectory=//')
            ;;
        -DprojectType=application)
            project_type=application
            ;;
    esac
done
if [ -z "$output_dir" ]; then
    echo 'FAIL: invocation Maven factice inattendue.' >&2
    exit 1
fi
if [ "$project_type" != application ]; then
    echo 'FAIL: projectType CycloneDX application absent.' >&2
    exit 1
fi
mkdir -p "$output_dir"
if [ "${FIXTURE_SBOM_MODE:-valid}" = invalid ]; then
    cat >"$output_dir/bom.json" <<'JSON'
{
  "bomFormat" : "CycloneDX",
  "specVersion" : "1.6",
  "metadata" : {
    "component" : {
      "type" : "library",
      "name" : "betting-sofascore-local-lab",
      "licenses" : [ { "license" : { "id" : "Apache-2.0" } } ],
      "externalReferences" : [
        { "type" : "website", "url" : "https://spring.io/projects/spring-boot/betting-sofascore-local-lab" },
        { "type" : "vcs", "url" : "https://github.com/spring-projects/spring-boot/betting-sofascore-local-lab" }
      ]
    },
    "properties" : [ {
      "name" : "cdx:reproducible",
      "value" : "enabled"
    } ]
  }
}
JSON
else
cat >"$output_dir/bom.json" <<'JSON'
{
  "bomFormat" : "CycloneDX",
  "specVersion" : "1.6",
  "metadata" : {
    "component" : {
      "type" : "application",
      "name" : "betting-sofascore-local-lab",
      "licenses" : [ { "license" : { "name" : "Proprietary" } } ],
      "externalReferences" : [
        { "type" : "website", "url" : "https://github.com/djothepirate/betting-sofascore-local-lab" },
        { "type" : "vcs", "url" : "https://github.com/djothepirate/betting-sofascore-local-lab" }
      ]
    },
    "properties" : [ {
      "name" : "cdx:reproducible",
      "value" : "enabled"
    } ]
  }
}
JSON
fi
MVNW
chmod +x "$fixture/mvnw"

(
    cd "$fixture"
    git init -q
    git add .
    git -c user.name=ci-fixture -c user.email=ci-fixture.invalid@example.test \
        commit -qm 'release fixture'
    git -c user.name=ci-fixture -c user.email=ci-fixture.invalid@example.test \
        commit --allow-empty -qm 'release fixture head'
    head_commit=$(git rev-parse HEAD)
    parent_commit=$(git rev-parse HEAD^)
    git update-ref refs/remotes/origin/main "$head_commit"
    git update-ref refs/remotes/origin/feature/V1.2.3 "$head_commit"
    git update-ref refs/remotes/origin/release/V1.2.3 "$head_commit"

    invalid_version_output=$(mktemp)
    if env -i PATH="$PATH" FIXTURE_MAVEN_VERSION=foo-SNAPSHOT \
        SOURCE_COMMIT_SHA="$head_commit" GITHUB_RUN_NUMBER=99 \
        GITHUB_REF_TYPE=branch GITHUB_REF_NAME=codex/invalid-version \
        sh ci/package-local-only.sh >"$invalid_version_output" 2>&1; then
        echo 'FAIL: une version Maven snapshot non SemVer a été acceptée.' >&2
        exit 1
    fi
    if ! grep -Fq 'version Maven snapshot hors convention SemVer' "$invalid_version_output"; then
        echo 'FAIL: le refus du snapshot Maven non SemVer est ambigu.' >&2
        cat "$invalid_version_output" >&2
        exit 1
    fi
    rm -f "$invalid_version_output"

    branch_version_output=$(mktemp)
    if env -i PATH="$PATH" FIXTURE_MAVEN_VERSION=1.2.3-SNAPSHOT \
        SOURCE_COMMIT_SHA="$head_commit" GITHUB_RUN_NUMBER=99 \
        GITHUB_REF_TYPE=branch GITHUB_REF_NAME=feature/V1.2.4 \
        sh ci/package-local-only.sh >"$branch_version_output" 2>&1; then
        echo 'FAIL: une version Maven différente du train feature a été acceptée.' >&2
        exit 1
    fi
    if ! grep -Fq 'exige la version Maven 1.2.4' "$branch_version_output"; then
        echo 'FAIL: le refus de divergence branche/version Maven est ambigu.' >&2
        cat "$branch_version_output" >&2
        exit 1
    fi
    rm -f "$branch_version_output"

    # Seul le push qui crée une feature d'intégration exactement au sommet de
    # main peut conserver temporairement la version Maven héritée.
    env -i PATH="$PATH" FIXTURE_MAVEN_VERSION=1.2.3-SNAPSHOT \
        SOURCE_COMMIT_SHA="$head_commit" GITHUB_RUN_NUMBER=95 \
        GITHUB_EVENT_NAME=push SOURCE_REF_CREATED=true \
        GITHUB_REF_TYPE=branch GITHUB_REF_NAME=feature/V1.3.0 \
        DURABLE_SNAPSHOT_SOURCE=true sh ci/package-local-only.sh >/dev/null
    seed_bundle=$(pwd)/target/distribution/betting-sofascore-local-lab-1.2.3-snapshot.p95.g$(printf '%.12s' "$head_commit")-local-only.zip
    if [ ! -f "$seed_bundle" ]; then
        echo 'FAIL: l’amorçage exact du nouveau train GitHub ne produit pas son snapshot local.' >&2
        exit 1
    fi
    mkdir inspect-train-seed
    (
        cd inspect-train-seed
        jar --extract --file "$seed_bundle"
    )
    if ! grep -Fxq 'artifact.channel=snapshot-local-only' \
        inspect-train-seed/provenance.properties ||
       ! grep -Fxq 'source.train.seed=true' \
        inspect-train-seed/provenance.properties; then
        echo 'FAIL: le snapshot local d’amorçage n’est pas identifié explicitement.' >&2
        exit 1
    fi

    for seed_branch in feature/V1.3.0-RC01 feature/V1.3.0-RC01-SNAPSHOT; do
        seed_output=$(mktemp)
        env -i PATH="$PATH" FIXTURE_MAVEN_VERSION=1.2.3-SNAPSHOT \
            SOURCE_COMMIT_SHA="$head_commit" GITHUB_RUN_NUMBER=94 \
            GITHUB_EVENT_NAME=push SOURCE_REF_CREATED=true \
            GITHUB_REF_TYPE=branch GITHUB_REF_NAME="$seed_branch" \
            DURABLE_SNAPSHOT_SOURCE=true \
            sh ci/package-local-only.sh >"$seed_output"
        if ! grep -Fq "PACKAGE_VERSION_POLICY=PASS:exact-train-seed:${seed_branch}@${head_commit}" \
            "$seed_output"; then
            echo "FAIL: l’amorçage GitHub du train $seed_branch n’a pas emprunté la voie bornée." >&2
            cat "$seed_output" >&2
            exit 1
        fi
        rm -f "$seed_output"
    done

    env -i PATH="$PATH" FIXTURE_MAVEN_VERSION=1.2.3-SNAPSHOT \
        CI_COMMIT_SHA="$head_commit" CI_COMMIT_BRANCH=feature/V1.3.0 \
        CI_PIPELINE_IID=95 CI_PIPELINE_SOURCE=push \
        CI_COMMIT_BEFORE_SHA=0000000000000000000000000000000000000000 \
        DURABLE_SNAPSHOT_SOURCE=true sh ci/package-local-only.sh >/dev/null
    for seed_branch in feature/V1.3.0-RC01 feature/V1.3.0-RC01-SNAPSHOT; do
        seed_output=$(mktemp)
        env -i PATH="$PATH" FIXTURE_MAVEN_VERSION=1.2.3-SNAPSHOT \
            CI_COMMIT_SHA="$head_commit" CI_COMMIT_BRANCH="$seed_branch" \
            CI_PIPELINE_IID=94 CI_PIPELINE_SOURCE=push \
            CI_COMMIT_BEFORE_SHA=0000000000000000000000000000000000000000 \
            DURABLE_SNAPSHOT_SOURCE=true \
            sh ci/package-local-only.sh >"$seed_output"
        if ! grep -Fq "PACKAGE_VERSION_POLICY=PASS:exact-train-seed:${seed_branch}@${head_commit}" \
            "$seed_output"; then
            echo "FAIL: l’amorçage GitLab du train $seed_branch n’a pas emprunté la voie bornée." >&2
            cat "$seed_output" >&2
            exit 1
        fi
        rm -f "$seed_output"
    done

    github_replay_output=$(mktemp)
    if env -i PATH="$PATH" FIXTURE_MAVEN_VERSION=1.2.3-SNAPSHOT \
        SOURCE_COMMIT_SHA="$head_commit" GITHUB_RUN_NUMBER=95 \
        GITHUB_EVENT_NAME=push SOURCE_REF_CREATED=false \
        GITHUB_REF_TYPE=branch GITHUB_REF_NAME=feature/V1.3.0 \
        DURABLE_SNAPSHOT_SOURCE=true \
        sh ci/package-local-only.sh >"$github_replay_output" 2>&1; then
        echo 'FAIL: un push GitHub ultérieur a usurpé l’amorçage du train.' >&2
        exit 1
    fi
    if ! grep -Fq 'exige la version Maven 1.3.0' "$github_replay_output"; then
        echo 'FAIL: le refus du rejeu GitHub est ambigu.' >&2
        cat "$github_replay_output" >&2
        exit 1
    fi
    rm -f "$github_replay_output"

    gitlab_replay_output=$(mktemp)
    if env -i PATH="$PATH" FIXTURE_MAVEN_VERSION=1.2.3-SNAPSHOT \
        CI_COMMIT_SHA="$head_commit" CI_COMMIT_BRANCH=feature/V1.3.0 \
        CI_PIPELINE_IID=95 CI_PIPELINE_SOURCE=push \
        CI_COMMIT_BEFORE_SHA="$parent_commit" DURABLE_SNAPSHOT_SOURCE=true \
        sh ci/package-local-only.sh >"$gitlab_replay_output" 2>&1; then
        echo 'FAIL: un push GitLab ultérieur a usurpé l’amorçage du train.' >&2
        exit 1
    fi
    if ! grep -Fq 'exige la version Maven 1.3.0' "$gitlab_replay_output"; then
        echo 'FAIL: le refus du rejeu GitLab est ambigu.' >&2
        cat "$gitlab_replay_output" >&2
        exit 1
    fi
    rm -f "$gitlab_replay_output"

    git update-ref refs/remotes/origin/main "$parent_commit"
    divergent_seed_output=$(mktemp)
    if env -i PATH="$PATH" FIXTURE_MAVEN_VERSION=1.2.3-SNAPSHOT \
        SOURCE_COMMIT_SHA="$head_commit" GITHUB_RUN_NUMBER=95 \
        GITHUB_EVENT_NAME=push SOURCE_REF_CREATED=true \
        GITHUB_REF_TYPE=branch GITHUB_REF_NAME=feature/V1.3.0 \
        DURABLE_SNAPSHOT_SOURCE=true \
        sh ci/package-local-only.sh >"$divergent_seed_output" 2>&1; then
        echo 'FAIL: un amorçage divergent du sommet canonique de main a été accepté.' >&2
        exit 1
    fi
    if ! grep -Fq 'sommet canonique exact de refs/remotes/origin/main' \
        "$divergent_seed_output"; then
        echo 'FAIL: le refus de l’amorçage divergent est ambigu.' >&2
        cat "$divergent_seed_output" >&2
        exit 1
    fi
    rm -f "$divergent_seed_output"
    git update-ref refs/remotes/origin/main "$head_commit"

    git update-ref -d refs/remotes/origin/main
    missing_seed_main_output=$(mktemp)
    if env -i PATH="$PATH" FIXTURE_MAVEN_VERSION=1.2.3-SNAPSHOT \
        SOURCE_COMMIT_SHA="$head_commit" GITHUB_RUN_NUMBER=95 \
        GITHUB_EVENT_NAME=push SOURCE_REF_CREATED=true \
        GITHUB_REF_TYPE=branch GITHUB_REF_NAME=feature/V1.3.0 \
        DURABLE_SNAPSHOT_SOURCE=true \
        sh ci/package-local-only.sh >"$missing_seed_main_output" 2>&1; then
        echo 'FAIL: un amorçage sans référence canonique main a été accepté.' >&2
        exit 1
    fi
    if ! grep -Fq "exige la référence canonique refs/remotes/origin/main" \
        "$missing_seed_main_output"; then
        echo 'FAIL: le refus de l’amorçage sans main est ambigu.' >&2
        cat "$missing_seed_main_output" >&2
        exit 1
    fi
    rm -f "$missing_seed_main_output"
    git update-ref refs/remotes/origin/main "$head_commit"

    dispatch_seed_output=$(mktemp)
    if env -i PATH="$PATH" FIXTURE_MAVEN_VERSION=1.2.3-SNAPSHOT \
        SOURCE_COMMIT_SHA="$head_commit" GITHUB_RUN_NUMBER=95 \
        GITHUB_EVENT_NAME=workflow_dispatch SOURCE_REF_CREATED=true \
        GITHUB_REF_TYPE=branch GITHUB_REF_NAME=feature/V1.3.0 \
        DURABLE_SNAPSHOT_SOURCE=true \
        sh ci/package-local-only.sh >"$dispatch_seed_output" 2>&1; then
        echo 'FAIL: un workflow_dispatch GitHub a usurpé l’amorçage du train.' >&2
        exit 1
    fi
    if ! grep -Fq 'exige la version Maven 1.3.0' "$dispatch_seed_output"; then
        echo 'FAIL: le refus de l’amorçage GitHub manuel est ambigu.' >&2
        cat "$dispatch_seed_output" >&2
        exit 1
    fi
    rm -f "$dispatch_seed_output"

    web_seed_output=$(mktemp)
    if env -i PATH="$PATH" FIXTURE_MAVEN_VERSION=1.2.3-SNAPSHOT \
        CI_COMMIT_SHA="$head_commit" CI_COMMIT_BRANCH=feature/V1.3.0 \
        CI_PIPELINE_IID=95 CI_PIPELINE_SOURCE=web \
        CI_COMMIT_BEFORE_SHA=0000000000000000000000000000000000000000 \
        DURABLE_SNAPSHOT_SOURCE=true \
        sh ci/package-local-only.sh >"$web_seed_output" 2>&1; then
        echo 'FAIL: un pipeline Web GitLab a usurpé l’amorçage du train.' >&2
        exit 1
    fi
    if ! grep -Fq 'exige la version Maven 1.3.0' "$web_seed_output"; then
        echo 'FAIL: le refus de l’amorçage GitLab hors push est ambigu.' >&2
        cat "$web_seed_output" >&2
        exit 1
    fi
    rm -f "$web_seed_output"

    work_order_seed_output=$(mktemp)
    if env -i PATH="$PATH" FIXTURE_MAVEN_VERSION=2.0.0-SNAPSHOT \
        SOURCE_COMMIT_SHA="$head_commit" GITHUB_RUN_NUMBER=95 \
        GITHUB_EVENT_NAME=push SOURCE_REF_CREATED=true \
        GITHUB_REF_TYPE=branch \
        GITHUB_REF_NAME=feature/V1.2.3-HUMAN-WO-SS-20260906-059 \
        sh ci/package-local-only.sh >"$work_order_seed_output" 2>&1; then
        echo 'FAIL: une branche de Work Order a usurpé l’amorçage du train.' >&2
        exit 1
    fi
    if ! grep -Fq 'exige la version Maven 1.2.3' \
        "$work_order_seed_output"; then
        echo 'FAIL: le refus de l’amorçage par une branche Work Order est ambigu.' >&2
        cat "$work_order_seed_output" >&2
        exit 1
    fi
    rm -f "$work_order_seed_output"

    rc_version_output=$(mktemp)
    if env -i PATH="$PATH" FIXTURE_MAVEN_VERSION=1.2.3-RC01-SNAPSHOT \
        SOURCE_COMMIT_SHA="$head_commit" GITHUB_RUN_NUMBER=99 \
        GITHUB_REF_TYPE=branch GITHUB_REF_NAME=feature/V1.2.3-RC01-SNAPSHOT \
        sh ci/package-local-only.sh >"$rc_version_output" 2>&1; then
        echo 'FAIL: une version Maven RC en casse branche a été acceptée.' >&2
        exit 1
    fi
    if ! grep -Fq 'version Maven snapshot hors convention SemVer' "$rc_version_output"; then
        echo 'FAIL: le refus de la casse RC non SemVer est ambigu.' >&2
        cat "$rc_version_output" >&2
        exit 1
    fi
    rm -f "$rc_version_output"

    umask 077
    env -i PATH="$PATH" FIXTURE_MAVEN_VERSION=1.2.3-rc.1-SNAPSHOT \
        SOURCE_COMMIT_SHA="$head_commit" GITHUB_RUN_NUMBER=97 \
        GITHUB_REF_TYPE=branch GITHUB_REF_NAME=feature/V1.2.3-RC01-SNAPSHOT \
        DURABLE_SNAPSHOT_SOURCE=true sh ci/package-local-only.sh >/dev/null
    rc_snapshot_bundle=$(pwd)/target/distribution/betting-sofascore-local-lab-1.2.3-rc.1-snapshot.p97.g$(printf '%.12s' "$head_commit")-local-only.zip
    if [ ! -f "$rc_snapshot_bundle" ]; then
        echo 'FAIL: le mapping du train RC01-SNAPSHOT vers Maven rc.1-SNAPSHOT est absent.' >&2
        exit 1
    fi

    # Un RC simple développe la même version Maven snapshot autant de fois que
    # nécessaire. Ce chemin normal ne dépend ni du seed, ni du sommet de main.
    git update-ref refs/remotes/origin/main "$parent_commit"
    for build in 1 2; do
        env -i PATH="$PATH" FIXTURE_MAVEN_VERSION=1.2.3-rc.1-SNAPSHOT \
            SOURCE_COMMIT_SHA="$head_commit" GITHUB_RUN_NUMBER=96 \
            GITHUB_EVENT_NAME=push SOURCE_REF_CREATED=false \
            GITHUB_REF_TYPE=branch GITHUB_REF_NAME=feature/V1.2.3-RC01 \
            DURABLE_SNAPSHOT_SOURCE=true sh ci/package-local-only.sh >/dev/null
        rebuild_bundle=$(pwd)/target/distribution/betting-sofascore-local-lab-1.2.3-rc.1-snapshot.p96.g$(printf '%.12s' "$head_commit")-local-only.zip
        if [ "$build" = 1 ]; then
            cp "$rebuild_bundle" first-rc-snapshot.zip
        elif ! cmp -s first-rc-snapshot.zip "$rebuild_bundle"; then
            echo 'FAIL: le rebuild de la même snapshot RC n’est pas reproductible.' >&2
            exit 1
        fi
    done
    env -i PATH="$PATH" FIXTURE_MAVEN_VERSION=1.2.3-rc.1-SNAPSHOT \
        CI_COMMIT_SHA="$head_commit" CI_COMMIT_BRANCH=feature/V1.2.3-RC01 \
        CI_PIPELINE_IID=96 CI_PIPELINE_SOURCE=push CI_COMMIT_BEFORE_SHA="$parent_commit" \
        DURABLE_SNAPSHOT_SOURCE=true sh ci/package-local-only.sh >/dev/null
    if ! cmp -s first-rc-snapshot.zip "$rebuild_bundle"; then
        echo 'FAIL: les snapshots RC GitHub et GitLab divergent à source et IID identiques.' >&2
        exit 1
    fi
    mkdir inspect-rc-rebuild
    (cd inspect-rc-rebuild && jar --extract --file "$rebuild_bundle")
    if ! grep -Fxq 'source.train.seed=false' inspect-rc-rebuild/provenance.properties ||
       ! grep -Fxq 'artifact.channel=snapshot-local-only' inspect-rc-rebuild/provenance.properties; then
        echo 'FAIL: le rebuild RC utilise le seed ou se présente comme une release.' >&2
        exit 1
    fi
    git update-ref refs/remotes/origin/main "$head_commit"

    # Les bundles de WO restent éphémères ; rc.1 final reste accepté sur feature.
    env -i PATH="$PATH" FIXTURE_MAVEN_VERSION=1.2.3-rc.1-SNAPSHOT \
        SOURCE_COMMIT_SHA="$head_commit" GITHUB_RUN_NUMBER=96 \
        GITHUB_REF_TYPE=branch GITHUB_REF_NAME=feature/V1.2.3-RC01-CODEX-WO-SS-20260906-056 \
        sh ci/package-local-only.sh >/dev/null
    env -i PATH="$PATH" FIXTURE_MAVEN_VERSION=1.2.3-rc.1 \
        SOURCE_COMMIT_SHA="$head_commit" GITHUB_RUN_NUMBER=96 \
        GITHUB_REF_TYPE=branch GITHUB_REF_NAME=feature/V1.2.3-RC01 \
        sh ci/package-local-only.sh >/dev/null
    for invalid_version in 1.2.3-SNAPSHOT 1.2.3-rc.2-SNAPSHOT 1.2.4-rc.1-SNAPSHOT \
        1.2.3-rc.01-SNAPSHOT 1.2.3-RC01-SNAPSHOT 1.2.3-rc.1-SNAPSHOT-SNAPSHOT; do
        if env -i PATH="$PATH" FIXTURE_MAVEN_VERSION="$invalid_version" \
            SOURCE_COMMIT_SHA="$head_commit" GITHUB_RUN_NUMBER=96 \
            GITHUB_EVENT_NAME=push SOURCE_REF_CREATED=false \
            GITHUB_REF_TYPE=branch GITHUB_REF_NAME=feature/V1.2.3-RC01 \
            sh ci/package-local-only.sh >invalid-rc.log 2>&1; then
            echo "FAIL: une version RC incompatible a été acceptée : $invalid_version." >&2
            exit 1
        fi
    done
    if env -i PATH="$PATH" FIXTURE_MAVEN_VERSION=1.2.3-rc.1-SNAPSHOT \
        CI_COMMIT_SHA="$head_commit" CI_COMMIT_BRANCH=release/V1.2.3-RC01 \
        CI_PIPELINE_IID=96 CI_PIPELINE_SOURCE=push \
        sh ci/package-local-only.sh >release-rc-snapshot.log 2>&1; then
        echo 'FAIL: une release RC simple accepte encore Maven SNAPSHOT.' >&2
        exit 1
    fi
    if ! grep -Fq 'exige la version Maven 1.2.3-rc.1' release-rc-snapshot.log; then
        echo 'FAIL: la preuve de refus release RC snapshot est ambiguë.' >&2
        exit 1
    fi
    git tag v1.2.3-rc.1
    git update-ref refs/remotes/origin/feature/V1.2.3-RC01 "$head_commit"
    git update-ref refs/remotes/origin/release/V1.2.3-RC01 "$head_commit"
    if env -i PATH="$PATH" FIXTURE_MAVEN_VERSION=1.2.3-rc.1-SNAPSHOT \
        SOURCE_COMMIT_SHA="$head_commit" GITHUB_RUN_NUMBER=96 \
        GITHUB_REF_TYPE=tag GITHUB_REF_NAME=v1.2.3-rc.1 \
        sh ci/package-local-only.sh >tag-rc-snapshot.log 2>&1; then
        echo 'FAIL: un tag RC accepte Maven SNAPSHOT.' >&2
        exit 1
    fi
    if ! grep -Fq 'ne correspond pas à la version Maven' tag-rc-snapshot.log; then
        echo 'FAIL: la preuve de refus du tag RC snapshot est ambiguë.' >&2
        exit 1
    fi
    git update-ref -d refs/tags/v1.2.3-rc.1

    invalid_sbom_output=$(mktemp)
    if env -i PATH="$PATH" FIXTURE_SBOM_MODE=invalid \
        SOURCE_COMMIT_SHA="$head_commit" GITHUB_RUN_NUMBER=98 \
        GITHUB_REF_TYPE=branch GITHUB_REF_NAME=codex/invalid-sbom \
        sh ci/package-local-only.sh >"$invalid_sbom_output" 2>&1; then
        echo 'FAIL: des métadonnées SBOM héritées ont été acceptées.' >&2
        exit 1
    fi
    if ! grep -Fq 'métadonnées racine du SBOM' "$invalid_sbom_output"; then
        echo 'FAIL: le refus des métadonnées SBOM héritées est ambigu.' >&2
        cat "$invalid_sbom_output" >&2
        exit 1
    fi
    rm -f "$invalid_sbom_output"

    # Une préparation de release porte déjà la version Maven finale, mais
    # reste un snapshot LOCAL_ONLY tant qu'aucun tag ne désigne le commit.
    umask 077
    env -i PATH="$PATH" SOURCE_COMMIT_SHA="$head_commit" GITHUB_RUN_NUMBER=100 \
        GITHUB_REF_TYPE=branch GITHUB_REF_NAME=feature/V1.2.3 \
        sh ci/package-local-only.sh >/dev/null
    untagged_bundle=$(pwd)/target/distribution/betting-sofascore-local-lab-1.2.3-snapshot.p100.g$(printf '%.12s' "$head_commit")-local-only.zip
    if [ ! -f "$untagged_bundle" ]; then
        echo 'FAIL: la préparation locale non taguée ne produit pas son snapshot.' >&2
        exit 1
    fi
    mkdir inspect-preparation
    (
        cd inspect-preparation
        jar --extract --file "$untagged_bundle"
    )
    preparation_provenance=inspect-preparation/provenance.properties
    if ! grep -Fxq 'artifact.channel=snapshot-local-only' "$preparation_provenance" ||
       ! grep -Fxq 'source.tag=' "$preparation_provenance" ||
       ! grep -Fxq "artifact.version=1.2.3-snapshot.p100.g$(printf '%.12s' "$head_commit")" "$preparation_provenance" ||
       ! grep -Fxq 'artifact.classification=EXPERIMENTAL_LOCAL_ONLY' "$preparation_provenance" ||
       ! grep -Fxq 'production.approved=false' "$preparation_provenance" ||
       ! grep -Fxq 'vps.deployable=false' "$preparation_provenance"; then
        echo 'FAIL: la préparation locale non taguée est présentée comme une release.' >&2
        exit 1
    fi
    if [ -e target/distribution/betting-sofascore-local-lab-1.2.3-local-only.zip ]; then
        echo 'FAIL: une version Maven finale sans tag a produit une release locale.' >&2
        exit 1
    fi

    git tag v1.2.3

    shallow_checkout="$fixture/shallow-checkout"
    git clone -q --depth 1 --no-tags "file://$fixture" "$shallow_checkout"
    (
        cd "$shallow_checkout"
        shallow_head=$(git rev-parse HEAD)
        git tag v1.2.3
        git update-ref refs/remotes/origin/main "$shallow_head"
        shallow_output=$(mktemp)
        if env -i PATH="$PATH" SOURCE_COMMIT_SHA="$shallow_head" \
            GITHUB_RUN_NUMBER=100 GITHUB_REF_TYPE=tag GITHUB_REF_NAME=v1.2.3 \
            sh ci/package-local-only.sh >"$shallow_output" 2>&1; then
            echo 'FAIL: une release locale issue d’un historique shallow a été acceptée.' >&2
            exit 1
        fi
        if ! grep -Fq 'historique Git complet est requis' "$shallow_output"; then
            echo 'FAIL: le refus de l’historique shallow est ambigu.' >&2
            cat "$shallow_output" >&2
            exit 1
        fi
        rm -f "$shallow_output"
    )

    umask 002
    env -i PATH="$PATH" SOURCE_COMMIT_SHA="$head_commit" GITHUB_RUN_NUMBER=101 \
        GITHUB_REF_TYPE=tag GITHUB_REF_NAME=v1.2.3 SOURCE_BRANCH_NAME= \
        sh ci/package-local-only.sh >/dev/null
    cp target/distribution/betting-sofascore-local-lab-1.2.3-local-only.zip \
        release-github.zip

    umask 077
    env -i PATH="$PATH" CI_COMMIT_SHA="$head_commit" CI_COMMIT_TAG=v1.2.3 \
        CI_COMMIT_BRANCH= CI_PIPELINE_IID=909 PROMOTION_TAG_PROOF=true \
        FEATURE_BRANCH_REF=refs/remotes/origin/feature/V1.2.3 \
        RELEASE_BRANCH_REF=refs/remotes/origin/release/V1.2.3 \
        sh ci/package-local-only.sh >/dev/null
    cp target/distribution/betting-sofascore-local-lab-1.2.3-local-only.zip \
        release-gitlab.zip

    git tag v1.2.3-rc.1
    git update-ref refs/remotes/origin/feature/V1.2.3-RC01 "$head_commit"
    git update-ref refs/remotes/origin/release/V1.2.3-RC01 "$head_commit"
    env -i PATH="$PATH" FIXTURE_MAVEN_VERSION=1.2.3-rc.1 \
        CI_COMMIT_SHA="$head_commit" CI_COMMIT_TAG=v1.2.3-rc.1 CI_PIPELINE_IID=910 \
        PROMOTION_TAG_PROOF=true \
        FEATURE_BRANCH_REF=refs/remotes/origin/feature/V1.2.3-RC01 \
        RELEASE_BRANCH_REF=refs/remotes/origin/release/V1.2.3-RC01 \
        sh ci/package-local-only.sh >/dev/null
    if [ ! -f target/distribution/betting-sofascore-local-lab-1.2.3-rc.1-local-only.zip ]; then
        echo 'FAIL: le tag rc.1 n’est pas lié au train release RC01.' >&2
        exit 1
    fi

    git tag v1.2.3-rc.99
    git update-ref refs/remotes/origin/feature/V1.2.3-RC99 "$head_commit"
    git update-ref refs/remotes/origin/release/V1.2.3-RC99 "$head_commit"
    env -i PATH="$PATH" FIXTURE_MAVEN_VERSION=1.2.3-rc.99 \
        CI_COMMIT_SHA="$head_commit" CI_COMMIT_TAG=v1.2.3-rc.99 CI_PIPELINE_IID=911 \
        PROMOTION_TAG_PROOF=true \
        FEATURE_BRANCH_REF=refs/remotes/origin/feature/V1.2.3-RC99 \
        RELEASE_BRANCH_REF=refs/remotes/origin/release/V1.2.3-RC99 \
        sh ci/package-local-only.sh >/dev/null
    if [ ! -f target/distribution/betting-sofascore-local-lab-1.2.3-rc.99-local-only.zip ]; then
        echo 'FAIL: le tag rc.99 n’est pas lié au train feature/release RC99.' >&2
        exit 1
    fi
)

if ! cmp -s "$fixture/release-github.zip" "$fixture/release-gitlab.zip"; then
    echo 'FAIL: le payload local de release dépend encore de l’identifiant de forge.' >&2
    exit 1
fi
if jar --list --file "$fixture/release-github.zip" |
   grep -Eq '(^|/)META-INF/MANIFEST[.]MF$'; then
    echo 'FAIL: le bundle local contient un manifeste dépendant du JDK.' >&2
    exit 1
fi
if ! grep -Fq -- '--no-compress' "$repository/ci/package-local-only.sh"; then
    echo 'FAIL: le bundle local doit éviter une compression dépendante du JDK.' >&2
    exit 1
fi

mkdir "$fixture/inspect"
(
    cd "$fixture/inspect"
    jar --extract --file "$fixture/release-github.zip"
)
provenance=$(find "$fixture/inspect" -type f -name provenance.properties -print)
if [ -z "$provenance" ] || [ ! -f "$provenance" ]; then
    echo 'FAIL: provenance absente du bundle local de fixture.' >&2
    exit 1
fi
if grep -Eq '^build\.pipeline\.' "$provenance"; then
    echo 'FAIL: la provenance locale contient un identifiant de pipeline.' >&2
    exit 1
fi
if ! grep -Fxq 'artifact.version=1.2.3' "$provenance"; then
    echo 'FAIL: la version canonique locale est absente de la provenance.' >&2
    exit 1
fi
if ! grep -Fxq 'source.branch=' "$provenance"; then
    echo 'FAIL: un événement tag ne doit pas être présenté comme une branche dans la provenance.' >&2
    exit 1
fi
if ! cmp -s "$repository/ci/distribution/README.md" "$fixture/inspect/README.md" ||
   ! cmp -s "$repository/scripts/Initialize-LocalConfig.ps1" \
       "$fixture/inspect/scripts/Initialize-LocalConfig.ps1" ||
   ! cmp -s "$repository/ci/distribution/Preflight-Local.ps1" \
       "$fixture/inspect/scripts/Preflight-Local.ps1" ||
   ! cmp -s "$repository/ci/distribution/Start-Local.ps1" \
       "$fixture/inspect/scripts/Start-Local.ps1" ||
   ! cmp -s "$repository/ci/distribution/Stop-Local.ps1" \
       "$fixture/inspect/scripts/Stop-Local.ps1"; then
    echo 'FAIL: le bundle ne contient pas les launchers et le guide de distribution qualifiés.' >&2
    exit 1
fi
if grep -Eiq 'mvnw([.]cmd)?|pom[.]xml|spring-boot:run' \
       "$fixture/inspect/scripts/Preflight-Local.ps1" \
       "$fixture/inspect/scripts/Start-Local.ps1" \
       "$fixture/inspect/scripts/Stop-Local.ps1" \
       "$fixture/inspect/README.md"; then
    echo 'FAIL: le bundle dépend encore des sources ou du Maven Wrapper.' >&2
    exit 1
fi
for argument in \
    '-Djdk.httpclient.disableRetryConnect=true' \
    '-Djdk.httpclient.redirects.retrylimit=1' \
    '-Djdk.httpclient.enableAllMethodRetry=false' \
    "'-jar'" \
    '--spring.profiles.active=local' \
    '--server.address=127.0.0.1' \
    '--sofascore.enabled=false' \
    '--sofascore.playwright.enabled=false' \
    '--optional-integration.enabled=false' \
    '--optional-integration.execution-mode=DISABLED' \
    '--optional-integration.remote-delivery-authorized=false' \
    '--optional-integration.official-permission-status=NOT_EVIDENCED' \
    '--optional-integration.receiver-qualification=NOT_QUALIFIED' \
    '--optional-integration.sender-qualification=NOT_QUALIFIED' \
    '--optional-integration.receiver-origin=' \
    '--optional-integration.loopback-qualification=false' \
    '--optional-integration.loopback-origin=' \
    '--optional-integration.automatic-retry-enabled=false' \
    '--optional-integration.mtls.client-certificate-sha256='; do
    if ! grep -Fq -- "$argument" "$fixture/inspect/scripts/Start-Local.ps1"; then
        echo "FAIL: argument Java absent du launcher distribué : $argument" >&2
        exit 1
    fi
done
for launcher in Preflight-Local.ps1 Start-Local.ps1 Stop-Local.ps1; do
    if ! grep -Fq -- '--project-name' "$fixture/inspect/scripts/$launcher" ||
       ! grep -Fq -- 'betting-sofascore-local-lab' "$fixture/inspect/scripts/$launcher"; then
        echo "FAIL: identité Docker Compose absente du launcher distribué : $launcher" >&2
        exit 1
    fi
done
for launcher in Preflight-Local.ps1 Stop-Local.ps1; do
    if ! grep -Fq 'DOCKER_HOST must target a local Windows named pipe' \
        "$fixture/inspect/scripts/$launcher"; then
        echo "FAIL: garde Docker locale absente du launcher distribué : $launcher" >&2
        exit 1
    fi
done

printf 'RELEASE_REPRODUCIBILITY=PASS_LOCAL_ONLY\n'

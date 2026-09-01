#!/usr/bin/env sh
set -eu

repository=$(git rev-parse --show-toplevel)
fixture=$(mktemp -d /tmp/sofascore-release-fixture.XXXXXX)

cleanup() {
    rm -rf "$fixture"
}
trap cleanup EXIT
trap 'exit 129' HUP
trap 'exit 130' INT
trap 'exit 143' TERM

mkdir -p "$fixture/ci/distribution" "$fixture/target" "$fixture/scripts"
cp "$repository/ci/package-local-only.sh" "$fixture/ci/package-local-only.sh"
cp "$repository/ci/distribution/README.md" "$fixture/ci/distribution/README.md"
cp "$repository/ci/distribution/Preflight-Local.ps1" \
    "$fixture/ci/distribution/Preflight-Local.ps1"
cp "$repository/ci/distribution/Start-Local.ps1" \
    "$fixture/ci/distribution/Start-Local.ps1"
cp "$repository/ci/distribution/Stop-Local.ps1" \
    "$fixture/ci/distribution/Stop-Local.ps1"
printf '#!/usr/bin/env sh\nexit 0\n' >"$fixture/ci/assert-local-only.sh"
printf 'fixture-jar\n' >"$fixture/target/application.jar"
for file in .env.example compose.yaml SECURITY.md; do
    printf 'release fixture\n' >"$fixture/$file"
done
for file in Initialize-LocalConfig.ps1; do
    printf '# release fixture\n' >"$fixture/scripts/$file"
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
    git update-ref refs/remotes/origin/main "$head_commit"

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
        GITHUB_REF_TYPE=branch GITHUB_REF_NAME=release/1.2.3 \
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

    real_git=$(command -v git)
    git_shim="$fixture/git-shim"
    mkdir -p "$git_shim"
    cat >"$git_shim/git" <<'GIT'
#!/usr/bin/env sh
if [ "$1" = merge-base ] && [ "${2:-}" = --is-ancestor ]; then
    exit 2
fi
exec "$REAL_GIT" "$@"
GIT
    chmod +x "$git_shim/git"
    git_error_output=$(mktemp)
    if env -i PATH="$git_shim:$PATH" REAL_GIT="$real_git" \
        SOURCE_COMMIT_SHA="$head_commit" GITHUB_RUN_NUMBER=100 \
        GITHUB_REF_TYPE=tag GITHUB_REF_NAME=v1.2.3 \
        sh ci/package-local-only.sh >"$git_error_output" 2>&1; then
        echo 'FAIL: une erreur interne Git a été acceptée pendant la qualification du tag local.' >&2
        exit 1
    fi
    if ! grep -Fq "impossible de vérifier l'appartenance" "$git_error_output"; then
        echo 'FAIL: le refus de l’erreur interne Git est ambigu.' >&2
        cat "$git_error_output" >&2
        exit 1
    fi
    rm -f "$git_error_output"

    umask 002
    env -i PATH="$PATH" SOURCE_COMMIT_SHA="$head_commit" GITHUB_RUN_NUMBER=101 \
        GITHUB_REF_TYPE=tag GITHUB_REF_NAME=v1.2.3 \
        sh ci/package-local-only.sh >/dev/null
    cp target/distribution/betting-sofascore-local-lab-1.2.3-local-only.zip \
        release-github.zip

    umask 077
    env -i PATH="$PATH" CI_COMMIT_SHA="$head_commit" CI_COMMIT_TAG=v1.2.3 \
        CI_PIPELINE_IID=909 sh ci/package-local-only.sh >/dev/null
    cp target/distribution/betting-sofascore-local-lab-1.2.3-local-only.zip \
        release-gitlab.zip
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
if ! cmp -s "$repository/ci/distribution/README.md" "$fixture/inspect/README.md" ||
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
    '--optional-integration.remote-delivery-authorized=false'; do
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

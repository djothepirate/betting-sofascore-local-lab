#!/usr/bin/env sh
set -eu

repository=$(git rev-parse --show-toplevel)
fixture=$(mktemp -d /tmp/sofascore-release-fixture.XXXXXX)

cleanup() {
    rm -rf "$fixture"
}
trap cleanup EXIT HUP INT TERM

mkdir -p "$fixture/ci" "$fixture/target" "$fixture/scripts"
cp "$repository/ci/package-local-only.sh" "$fixture/ci/package-local-only.sh"
printf '#!/usr/bin/env sh\nexit 0\n' >"$fixture/ci/assert-local-only.sh"
printf 'fixture-jar\n' >"$fixture/target/application.jar"
for file in .env.example compose.yaml README.md SECURITY.md; do
    printf 'release fixture\n' >"$fixture/$file"
done
for file in Initialize-LocalConfig.ps1 Preflight-Local.ps1 Start-Local.ps1 Stop-Local.ps1; do
    printf '# release fixture\n' >"$fixture/scripts/$file"
done

cat >"$fixture/mvnw" <<'MVNW'
#!/usr/bin/env sh
set -eu

for arg in "$@"; do
    if [ "$arg" = help:evaluate ]; then
        printf '1.2.3\n'
        exit 0
    fi
done

output_dir=
for arg in "$@"; do
    case "$arg" in
        -DoutputDirectory=*)
            output_dir=$(printf '%s' "$arg" | sed 's/^-DoutputDirectory=//')
            ;;
    esac
done
if [ -z "$output_dir" ]; then
    echo 'FAIL: invocation Maven factice inattendue.' >&2
    exit 1
fi
mkdir -p "$output_dir"
cat >"$output_dir/bom.json" <<'JSON'
{
  "bomFormat" : "CycloneDX",
  "specVersion" : "1.6",
  "metadata" : {
    "properties" : [ {
      "name" : "cdx:reproducible",
      "value" : "enabled"
    } ]
  }
}
JSON
MVNW
chmod +x "$fixture/mvnw"

(
    cd "$fixture"
    git init -q
    git add .
    git -c user.name=ci-fixture -c user.email=ci-fixture.invalid@example.test \
        commit -qm 'release fixture'
    head_commit=$(git rev-parse HEAD)
    git update-ref refs/remotes/origin/main "$head_commit"

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

printf 'RELEASE_REPRODUCIBILITY=PASS_LOCAL_ONLY\n'

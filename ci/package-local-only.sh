#!/usr/bin/env sh
set -eu

repository=$(git rev-parse --show-toplevel)
cd "$repository"
sh ci/assert-local-only.sh

version=$(./mvnw -q -DforceStdout help:evaluate -Dexpression=project.version)
commit_sha=${CI_COMMIT_SHA:-${GITHUB_SHA:-}}
pipeline_iid=${CI_PIPELINE_IID:-${GITHUB_RUN_NUMBER:-0}}
tag=${CI_COMMIT_TAG:-}

if [ -z "$commit_sha" ]; then
    commit_sha=$(git rev-parse HEAD)
fi
if [ -z "$tag" ] && [ "${GITHUB_REF_TYPE:-}" = tag ]; then
    tag=${GITHUB_REF_NAME:-}
fi

case "$commit_sha" in
    *[!0-9a-f]*|'')
        echo 'FAIL: SHA source invalide.' >&2
        exit 1
        ;;
esac
case "$pipeline_iid" in
    *[!0-9]*)
        echo 'FAIL: identifiant de pipeline invalide.' >&2
        exit 1
        ;;
esac

short_sha=$(printf '%.12s' "$commit_sha")
channel=snapshot-local-only
if [ -n "$tag" ]; then
    if ! printf '%s' "$tag" | grep -Eq '^v(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)(-rc\.[1-9][0-9]*)?$'; then
        echo "FAIL: tag hors convention SemVer : $tag" >&2
        exit 1
    fi
    expected_version=${tag#v}
    if [ "$version" != "$expected_version" ]; then
        echo "FAIL: le tag $tag ne correspond pas à la version Maven $version." >&2
        exit 1
    fi
    artifact_version=$version
    channel=release-local-only
else
    case "$version" in
        *-SNAPSHOT) ;;
        *)
            echo "FAIL: un build sans tag exige une version Maven -SNAPSHOT, reçu $version." >&2
            exit 1
            ;;
    esac
    base_version=${version%-SNAPSHOT}
    artifact_version="${base_version}-snapshot.p${pipeline_iid}.g${short_sha}"
fi

source_jar=
jar_count=0
for candidate in target/*.jar; do
    [ -f "$candidate" ] || continue
    case "$candidate" in
        *.original|*-sources.jar|*-javadoc.jar|*-tests.jar|*-provider-playwright-worker.jar) continue ;;
    esac
    source_jar=$candidate
    jar_count=$((jar_count + 1))
done
if [ "$jar_count" -ne 1 ]; then
    echo "FAIL: exactement un JAR local est attendu, reçu $jar_count." >&2
    exit 1
fi

./mvnw -B -ntp -DskipTests \
    org.cyclonedx:cyclonedx-maven-plugin:2.9.2:makeAggregateBom
test -f target/bom.json

distribution=target/distribution
stage=$distribution/stage
rm -rf "$distribution"
mkdir -p "$stage/scripts"

jar_name="betting-sofascore-local-lab-${artifact_version}.jar"
bundle_name="betting-sofascore-local-lab-${artifact_version}-local-only.zip"
cp "$source_jar" "$stage/$jar_name"
cp target/bom.json "$stage/sbom.cdx.json"
cp .env.example compose.yaml README.md SECURITY.md "$stage/"
cp scripts/Initialize-LocalConfig.ps1 scripts/Preflight-Local.ps1 \
    scripts/Start-Local.ps1 scripts/Stop-Local.ps1 "$stage/scripts/"

cat >"$stage/provenance.properties" <<EOF
artifact.classification=EXPERIMENTAL_LOCAL_ONLY
artifact.channel=$channel
production.approved=false
vps.deployable=false
sofascore.network.used=false
optional.integration.authorized=false
source.repository=djothepirate/betting-sofascore-local-lab
source.commit=$commit_sha
source.tag=$tag
maven.version=$version
build.pipeline.iid=$pipeline_iid
java.target=25
sbom.format=CycloneDX-JSON
EOF

(
    cd "$stage"
    sha256sum "$jar_name" sbom.cdx.json provenance.properties >SHA256SUMS
)

(
    cd "$stage"
    jar --create --file "../$bundle_name" .
)
(
    cd "$distribution"
    sha256sum "$bundle_name" >"$bundle_name.sha256"
)
rm -rf "$stage"

printf 'PACKAGE_RESULT=PASS_LOCAL_ONLY\n'
printf 'PACKAGE_FILE=%s\n' "$distribution/$bundle_name"
printf 'SOFASCORE_NETWORK_USED=NO\n'
printf 'VPS_DEPLOYABLE=NO\n'

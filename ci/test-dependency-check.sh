#!/usr/bin/env sh
set -eu

repository=$(git rev-parse --show-toplevel)
cd "$repository"

# La fixture ne dépend pas du cache configuré par le runner GitLab qui la lance.
unset MAVEN_USER_HOME DEPENDENCY_CHECK_DATA_DIRECTORY
fixture=$(mktemp -d "${TMPDIR:-/tmp}/local-lab-dependency-check.XXXXXX")
cleanup() {
    rm -f "$fixture/ci/run-dependency-check.sh" "$fixture/mvnw" \
        "$fixture/unset.log" "$fixture/empty.log" "$fixture/present.log" \
        "$fixture/maven-home.log" "$fixture/override.log" "$fixture/failure.log"
    rmdir "$fixture/ci" "$fixture" 2>/dev/null || true
}
trap cleanup EXIT HUP INT TERM
mkdir -p "$fixture/ci"
cp ci/run-dependency-check.sh "$fixture/ci/run-dependency-check.sh"
cat >"$fixture/mvnw" <<'MVNW'
#!/usr/bin/env sh
set -eu
: "${ARGUMENT_LOG:?journal de fixture requis}"
printf '%s\n' "$@" >"$ARGUMENT_LOG"
exit "${FIXTURE_MAVEN_EXIT:-0}"
MVNW
chmod +x "$fixture/mvnw"

assert_argument() {
    if ! grep -Fxq -- "$1" "$2"; then
        printf 'FAIL: argument Dependency-Check absent : %s\n' "$1" >&2
        exit 1
    fi
}
assert_no_key() {
    if grep -Eq -- '-Dnvd(Api|User|Password|BearerToken|DatafeedServerId)' "$1"; then
        echo 'FAIL: le flux NVD public ne doit transmettre aucune option API ou authentification.' >&2
        exit 1
    fi
}

(
    unset NVD_API_KEY
    ARGUMENT_LOG="$fixture/unset.log" sh "$fixture/ci/run-dependency-check.sh"
)
NVD_API_KEY='' ARGUMENT_LOG="$fixture/empty.log" sh "$fixture/ci/run-dependency-check.sh"
NVD_API_KEY=fixture-nvd-not-a-real-key ARGUMENT_LOG="$fixture/present.log" \
    sh "$fixture/ci/run-dependency-check.sh"
for log in "$fixture/unset.log" "$fixture/empty.log" "$fixture/present.log"; do
    assert_no_key "$log"
    assert_argument "-DdataDirectory=$fixture/target/dependency-check-data" "$log"
    assert_argument '-DnvdDatafeedUrl=https://nvd.nist.gov/feeds/json/cve/2.0/nvdcve-2.0-{0}.json.gz' "$log"
    assert_argument '-Dformats=HTML,JSON,GITLAB' "$log"
    assert_argument '-DfailOnError=true' "$log"
    assert_argument '-DfailBuildOnCVSS=11' "$log"
    assert_argument 'org.owasp:dependency-check-maven:13.0.0:check' "$log"
    if grep -Fq 'fixture-nvd-not-a-real-key' "$log"; then
        echo 'FAIL: la valeur synthétique de clé ne doit pas entrer dans les arguments.' >&2
        exit 1
    fi
done

MAVEN_USER_HOME="$fixture/maven cache" ARGUMENT_LOG="$fixture/maven-home.log" \
    sh "$fixture/ci/run-dependency-check.sh"
assert_argument "-DdataDirectory=$fixture/maven cache/dependency-check-data" "$fixture/maven-home.log"
MAVEN_USER_HOME="$fixture/maven cache" DEPENDENCY_CHECK_DATA_DIRECTORY="$fixture/explicit cache" \
    ARGUMENT_LOG="$fixture/override.log" sh "$fixture/ci/run-dependency-check.sh"
assert_argument "-DdataDirectory=$fixture/explicit cache" "$fixture/override.log"

failure_status=0
FIXTURE_MAVEN_EXIT=42 ARGUMENT_LOG="$fixture/failure.log" \
    sh "$fixture/ci/run-dependency-check.sh" || failure_status=$?
if [ "$failure_status" -ne 42 ]; then
    echo 'FAIL: le lanceur doit propager un échec Maven sans succès artificiel.' >&2
    exit 1
fi

dependency_job=$(awk '
    /^security:dependencies:/ { in_job = 1 }
    in_job && /^\.package-local-only:/ { exit }
    in_job { print }
' .gitlab-ci.yml)
if ! printf '%s\n' "$dependency_job" | grep -Fxq '  cache: []' ||
   ! printf '%s\n' "$dependency_job" | grep -Fxq '  allow_failure: true' ||
   ! printf '%s\n' "$dependency_job" | grep -Fxq '    - sh ci/run-dependency-check.sh'; then
    echo 'FAIL: le job doit utiliser le lanceur et conserver son niveau observation explicite.' >&2
    exit 1
fi
if printf '%s\n' "$dependency_job" | grep -Eq -- '-DnvdApi|failOnError=false|dependency-check.skip'; then
    echo 'FAIL: le job ne doit ni revenir à la voie API ni neutraliser le scan.' >&2
    exit 1
fi

printf 'DEPENDENCY_CHECK_ARGUMENTS=PASS_LOCAL_ONLY_OBSERVATION\n'

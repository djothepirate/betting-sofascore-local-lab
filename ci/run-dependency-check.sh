#!/usr/bin/env sh
set -eu

script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
repository=$(CDPATH= cd -- "$script_dir/.." && pwd)
cd "$repository"

# Base propre à ce job, jamais restaurée ou publiée dans un cache GitLab.
data_directory=${DEPENDENCY_CHECK_DATA_DIRECTORY:-${MAVEN_USER_HOME:-"$repository/target"}/dependency-check-data}

set -- \
    -B \
    -ntp \
    -DskipTests \
    "-DdataDirectory=$data_directory" \
    "-DnvdDatafeedUrl=https://nvd.nist.gov/feeds/json/cve/2.0/nvdcve-2.0-{0}.json.gz" \
    -Dformats=HTML,JSON,GITLAB \
    -DprettyPrint=true \
    -DfailOnError=true \
    -DfailBuildOnCVSS=11

# Le flux public sélectionne la voie datafeed de Dependency-Check 13, sans API NVD
# ni option de clé. Le seuil 11 reste celui du job d'observation, sans gate CVSS.
set -- "$@" org.owasp:dependency-check-maven:13.0.0:check
exec ./mvnw "$@"

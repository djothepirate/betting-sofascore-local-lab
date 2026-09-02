#!/usr/bin/env sh
set -eu

repository=$(git rev-parse --show-toplevel)
cd "$repository"

if [ -e .env ]; then
    echo 'FAIL: .env ne doit jamais être présent dans un workspace CI.' >&2
    exit 1
fi

assert_false() {
    name=$1
    value=$2
    normalized=$(printf '%s' "$value" | tr '[:upper:]' '[:lower:]')
    case "$normalized" in
        ''|0|false|no|off) ;;
        *)
            echo "FAIL: $name doit rester désactivé en CI." >&2
            exit 1
            ;;
    esac
}

assert_exact() {
    name=$1
    value=$2
    expected=$3
    if [ "$value" != "$expected" ]; then
        echo "FAIL: $name doit rester fixé à $expected en CI." >&2
        exit 1
    fi
}

assert_empty() {
    name=$1
    value=$2
    if [ -n "$value" ]; then
        echo "FAIL: $name doit rester vide en CI." >&2
        exit 1
    fi
}

assert_false SOFASCORE_ENABLED "${SOFASCORE_ENABLED:-false}"
assert_false SOFASCORE_PLAYWRIGHT_ENABLED "${SOFASCORE_PLAYWRIGHT_ENABLED:-false}"
assert_false SOFASCORE_J3_QUALIFICATION_ENABLED "${SOFASCORE_J3_QUALIFICATION_ENABLED:-false}"
assert_false SOFASCORE_J4_EVENT_DETAILS_QUALIFICATION_ENABLED "${SOFASCORE_J4_EVENT_DETAILS_QUALIFICATION_ENABLED:-false}"
assert_false SOFASCORE_J4_EVENT_DETAILS_PHASE2_ENABLED "${SOFASCORE_J4_EVENT_DETAILS_PHASE2_ENABLED:-false}"
assert_false SOFASCORE_J5_EVENT_DATA_QUALIFICATION_ENABLED "${SOFASCORE_J5_EVENT_DATA_QUALIFICATION_ENABLED:-false}"
assert_false SOFASCORE_TOURNAMENT_EVENT_DISCOVERY_ENABLED "${SOFASCORE_TOURNAMENT_EVENT_DISCOVERY_ENABLED:-false}"
assert_false OPTIONAL_INTEGRATION_ENABLED "${OPTIONAL_INTEGRATION_ENABLED:-false}"
assert_false OPTIONAL_INTEGRATION_REMOTE_DELIVERY_AUTHORIZED "${OPTIONAL_INTEGRATION_REMOTE_DELIVERY_AUTHORIZED:-false}"
assert_false OPTIONAL_INTEGRATION_LOOPBACK_QUALIFICATION "${OPTIONAL_INTEGRATION_LOOPBACK_QUALIFICATION:-false}"
assert_false OPTIONAL_INTEGRATION_AUTOMATIC_RETRY_ENABLED "${OPTIONAL_INTEGRATION_AUTOMATIC_RETRY_ENABLED:-false}"
assert_exact OPTIONAL_INTEGRATION_EXECUTION_MODE "${OPTIONAL_INTEGRATION_EXECUTION_MODE:-DISABLED}" DISABLED
assert_exact OPTIONAL_INTEGRATION_OFFICIAL_PERMISSION_STATUS \
    "${OPTIONAL_INTEGRATION_OFFICIAL_PERMISSION_STATUS:-NOT_EVIDENCED}" NOT_EVIDENCED
assert_exact OPTIONAL_INTEGRATION_RECEIVER_QUALIFICATION \
    "${OPTIONAL_INTEGRATION_RECEIVER_QUALIFICATION:-NOT_QUALIFIED}" NOT_QUALIFIED
assert_exact OPTIONAL_INTEGRATION_SENDER_QUALIFICATION \
    "${OPTIONAL_INTEGRATION_SENDER_QUALIFICATION:-NOT_QUALIFIED}" NOT_QUALIFIED
assert_empty OPTIONAL_INTEGRATION_RECEIVER_ORIGIN "${OPTIONAL_INTEGRATION_RECEIVER_ORIGIN:-}"
assert_empty OPTIONAL_INTEGRATION_LOOPBACK_ORIGIN "${OPTIONAL_INTEGRATION_LOOPBACK_ORIGIN:-}"
assert_empty OPTIONAL_INTEGRATION_MTLS_CLIENT_CERTIFICATE_SHA256 \
    "${OPTIONAL_INTEGRATION_MTLS_CLIENT_CERTIFICATE_SHA256:-}"

application_config=src/main/resources/application.yml
grep -Eq '^[[:space:]]+address:[[:space:]]+127\.0\.0\.1[[:space:]]*$' "$application_config"
grep -Fq 'enabled: ${SOFASCORE_ENABLED:false}' "$application_config"
grep -Fq 'automatic-refresh-enabled: false' "$application_config"
grep -Fq 'live-polling-enabled: false' "$application_config"
grep -Fq 'remote-delivery-authorized: false' "$application_config"
grep -Fq 'execution-mode: DISABLED' "$application_config"
grep -Fq 'official-permission-status: NOT_EVIDENCED' "$application_config"
grep -Fq 'receiver-qualification: NOT_QUALIFIED' "$application_config"
grep -Fq 'sender-qualification: NOT_QUALIFIED' "$application_config"
grep -Fq 'receiver-origin: ""' "$application_config"
grep -Fq 'loopback-qualification: false' "$application_config"
grep -Fq 'loopback-origin: ""' "$application_config"
grep -Fq 'automatic-retry-enabled: false' "$application_config"
grep -Fq 'client-certificate-sha256: ""' "$application_config"

ci_files='.github/workflows/ci.yml .gitlab-ci.yml'
if grep -Eiq 'sofascore-live-test|provider-playwright-runtime|provider-playwright-local-qualification' $ci_files; then
    echo 'FAIL: un profil fournisseur ou Playwright est référencé par la CI.' >&2
    exit 1
fi
if grep -Eiq '(^|[[:space:]-])(deploy|environment)[^#]*(production|vps)|(^|[[:space:]])(ssh|scp|rsync)([[:space:]]|$)' $ci_files; then
    echo 'FAIL: une primitive de déploiement ou d’accès VPS est présente dans la CI.' >&2
    exit 1
fi

printf 'LOCAL_ONLY_POLICY=PASS\n'
printf 'SOFASCORE_NETWORK_AUTHORIZED=NO\n'
printf 'VPS_DEPLOYMENT_AUTHORIZED=NO\n'
printf 'PRODUCTION_AUTHORIZED=NO\n'

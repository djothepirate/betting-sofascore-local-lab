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

assert_false SOFASCORE_ENABLED "${SOFASCORE_ENABLED:-false}"
assert_false SOFASCORE_PLAYWRIGHT_ENABLED "${SOFASCORE_PLAYWRIGHT_ENABLED:-false}"
assert_false SOFASCORE_J3_QUALIFICATION_ENABLED "${SOFASCORE_J3_QUALIFICATION_ENABLED:-false}"
assert_false SOFASCORE_J4_EVENT_DETAILS_QUALIFICATION_ENABLED "${SOFASCORE_J4_EVENT_DETAILS_QUALIFICATION_ENABLED:-false}"
assert_false SOFASCORE_J4_EVENT_DETAILS_PHASE2_ENABLED "${SOFASCORE_J4_EVENT_DETAILS_PHASE2_ENABLED:-false}"
assert_false SOFASCORE_J5_EVENT_DATA_QUALIFICATION_ENABLED "${SOFASCORE_J5_EVENT_DATA_QUALIFICATION_ENABLED:-false}"
assert_false SOFASCORE_TOURNAMENT_EVENT_DISCOVERY_ENABLED "${SOFASCORE_TOURNAMENT_EVENT_DISCOVERY_ENABLED:-false}"
assert_false OPTIONAL_INTEGRATION_ENABLED "${OPTIONAL_INTEGRATION_ENABLED:-false}"
assert_false OPTIONAL_INTEGRATION_REMOTE_DELIVERY_AUTHORIZED "${OPTIONAL_INTEGRATION_REMOTE_DELIVERY_AUTHORIZED:-false}"

application_config=src/main/resources/application.yml
grep -Eq '^[[:space:]]+address:[[:space:]]+127\.0\.0\.1[[:space:]]*$' "$application_config"
grep -Fq 'enabled: ${SOFASCORE_ENABLED:false}' "$application_config"
grep -Fq 'automatic-refresh-enabled: false' "$application_config"
grep -Fq 'live-polling-enabled: false' "$application_config"
grep -Fq 'remote-delivery-authorized: false' "$application_config"

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

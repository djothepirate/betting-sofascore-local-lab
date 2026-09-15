#!/usr/bin/env sh
set -eu

branch_name=${1:-}
project_version=${2:-}
context=${3:-github-branch}
script_dir=$(CDPATH= cd -- "$(dirname "$0")" && pwd)

# La qualification manuelle ne produit plus de bundle ; elle conserve le même
# mapping Maven, sans l'exception réservée au push initial de création du train.
case "$context" in
    github-branch|gitlab-branch) ;;
    *)
        echo "FAIL: contexte branche/version inconnu : $context." >&2
        exit 1
        ;;
esac
sh "$script_dir/check-branch-name.sh" "$branch_name" "$context"
if ! printf '%s' "$project_version" | grep -Eq \
    '^(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)(-rc\.[1-9][0-9]*)?(-SNAPSHOT)?$'; then
    echo "FAIL: version Maven hors convention SemVer : ${project_version:-<vide>}." >&2
    exit 1
fi

if [ "$branch_name" = main ]; then
    printf 'BRANCH_VERSION_POLICY=PASS:%s:%s\n' "$branch_name" "$project_version"
    exit 0
fi

train=${branch_name#feature/V}
allow_development_snapshot=true
case "$branch_name" in
    release/V*)
        train=${branch_name#release/V}
        allow_development_snapshot=false
        ;;
esac
case "$train" in
    *-CODEX-WO-SS-*) train=${train%%-CODEX-WO-SS-*} ;;
    *-HUMAN-WO-SS-*) train=${train%%-HUMAN-WO-SS-*} ;;
esac
case "$train" in
    *-RC??-SNAPSHOT)
        core=${train%%-RC*}
        rc=${train#*-RC}
        rc=${rc%-SNAPSHOT}
        rc=${rc#0}
        expected_version="${core}-rc.${rc}-SNAPSHOT"
        ;;
    *-RC??)
        core=${train%%-RC*}
        rc=${train#*-RC}
        rc=${rc#0}
        expected_version="${core}-rc.${rc}"
        if [ "$allow_development_snapshot" = true ] &&
           [ "$project_version" = "${expected_version}-SNAPSHOT" ]; then
            expected_version=$project_version
        fi
        ;;
    *)
        expected_version=$train
        if [ "$allow_development_snapshot" = true ] &&
           [ "$project_version" = "${expected_version}-SNAPSHOT" ]; then
            expected_version=$project_version
        fi
        ;;
esac
if [ "$project_version" != "$expected_version" ]; then
    echo "FAIL: la branche $branch_name exige la version Maven $expected_version, reçue : $project_version." >&2
    exit 1
fi
printf 'BRANCH_VERSION_POLICY=PASS:%s:%s\n' "$branch_name" "$project_version"

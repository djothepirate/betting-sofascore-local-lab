#!/usr/bin/env sh
set -eu

source_branch=${1:-}
target_branch=${2:-}
base_sha=${3:-}
head_sha=${4:-}
project_version=${5:-}
train_version='(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)(-RC(0[1-9]|[1-9][0-9])(-SNAPSHOT)?)?'
bootstrap_branch=codex/ss-20260905-055-version-branch-workflow
bootstrap_base=054fa4ca9301224aa5f96f478136208d2327d7f0

script_dir=$(CDPATH= cd -- "$(dirname "$0")" && pwd)
repository=$(git rev-parse --show-toplevel 2>/dev/null) || {
    echo 'FAIL: le garde de PR GitHub exige un dépôt Git.' >&2
    exit 1
}

train_maven_version() {
    train=$1
    case "$train" in
        *-RC??-SNAPSHOT)
            core=${train%%-RC*}
            rc=${train#*-RC}
            rc=${rc%-SNAPSHOT}
            rc=${rc#0}
            printf '%s-rc.%s-SNAPSHOT\n' "$core" "$rc"
            ;;
        *-RC??)
            core=${train%%-RC*}
            rc=${train#*-RC}
            rc=${rc#0}
            printf '%s-rc.%s\n' "$core" "$rc"
            ;;
        *)
            printf '%s\n' "$train"
            ;;
    esac
}

if ! sh "$script_dir/check-branch-name.sh" "$source_branch" >/dev/null 2>&1; then
    echo "FAIL: source de PR GitHub hors convention : ${source_branch:-<vide>}." >&2
    exit 1
fi

if [ "$source_branch" = "$bootstrap_branch" ]; then
    if [ "$target_branch" != main ] || [ "$base_sha" != "$bootstrap_base" ]; then
        echo "FAIL: le bootstrap WO-055 exige ${bootstrap_branch} -> main sur la base exacte ${bootstrap_base}." >&2
        exit 1
    fi
    if [ "$project_version" != 0.1.0-SNAPSHOT ]; then
        echo "FAIL: le bootstrap WO-055 exige la version Maven 0.1.0-SNAPSHOT, reçue : ${project_version:-<vide>}." >&2
        exit 1
    fi
    case "$head_sha" in
        *[!0-9a-f]*|'')
            echo "FAIL: le bootstrap WO-055 exige le SHA Git complet de la tête source, reçu : ${head_sha:-<vide>}." >&2
            exit 1
            ;;
    esac
    if ! resolved_base=$(git -C "$repository" rev-parse --verify "${base_sha}^{commit}" 2>/dev/null); then
        echo "FAIL: la base bootstrap est absente du graphe Git : $base_sha." >&2
        exit 1
    fi
    if ! resolved_head=$(git -C "$repository" rev-parse --verify "${head_sha}^{commit}" 2>/dev/null); then
        echo "FAIL: la tête bootstrap est absente du graphe Git : $head_sha." >&2
        exit 1
    fi
    if ! merge_base=$(git -C "$repository" merge-base "$resolved_base" "$resolved_head" 2>/dev/null); then
        echo 'FAIL: impossible de calculer la merge-base du bootstrap WO-055.' >&2
        exit 1
    fi
    if [ "$merge_base" != "$resolved_base" ]; then
        echo "FAIL: la merge-base du bootstrap doit être exactement $resolved_base, reçue : $merge_base." >&2
        exit 1
    fi
    if ! git -C "$repository" merge-base --is-ancestor "$resolved_base" "$resolved_head"; then
        echo "FAIL: la tête bootstrap $resolved_head doit descendre de la base exacte $resolved_base." >&2
        exit 1
    fi
    printf 'GITHUB_PR_POLICY=PASS:BOOTSTRAP_WO055:%s->%s\n' \
        "$source_branch" "$target_branch"
    exit 0
fi

if printf '%s' "$source_branch" | grep -Eq "^feature/V${train_version}$"; then
    if [ "$target_branch" != main ]; then
        echo "FAIL: une PR finale feature/V<train> doit cibler main, cible reçue : ${target_branch:-<vide>}." >&2
        exit 1
    fi
    train=${source_branch#feature/V}
    expected_version=$(train_maven_version "$train")
    if [ "$project_version" != "$expected_version" ]; then
        echo "FAIL: la PR finale $source_branch -> main exige la version Maven $expected_version, reçue : ${project_version:-<vide>}." >&2
        exit 1
    fi
    printf 'GITHUB_PR_POLICY=PASS:FEATURE_TO_MAIN:%s->%s\n' \
        "$source_branch" "$target_branch"
    exit 0
fi

if printf '%s' "$source_branch" | grep -Eq \
    "^feature/V${train_version}-(CODEX|HUMAN)-WO-SS-[0-9]{8}-[0-9]{3}$"; then
    expected_target=$(printf '%s' "$source_branch" | sed -E \
        's#-(CODEX|HUMAN)-WO-SS-[0-9]{8}-[0-9]{3}$##')
    if [ "$target_branch" != "$expected_target" ]; then
        echo "FAIL: le Work Order $source_branch doit cibler $expected_target, cible reçue : ${target_branch:-<vide>}." >&2
        exit 1
    fi
    train=${expected_target#feature/V}
    expected_version=$(train_maven_version "$train")
    case "$train" in
        *-RC*) valid_version=$expected_version ;;
        *)
            valid_version=$expected_version
            if [ "$project_version" = "${expected_version}-SNAPSHOT" ]; then
                valid_version=$project_version
            fi
            ;;
    esac
    if [ "$project_version" != "$valid_version" ]; then
        echo "FAIL: la PR de Work Order vers $expected_target exige une version Maven compatible avec $expected_version, reçue : ${project_version:-<vide>}." >&2
        exit 1
    fi
    printf 'GITHUB_PR_POLICY=PASS:WORK_ORDER_TO_FEATURE:%s->%s\n' \
        "$source_branch" "$target_branch"
    exit 0
fi

echo "FAIL: route de PR GitHub interdite : ${source_branch:-<vide>} -> ${target_branch:-<vide>}." >&2
exit 1

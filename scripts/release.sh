#!/usr/bin/env bash
# Build a signed Appfredo release and everything its GitHub release must carry for the store: appfredo.json,
# the full APK, and Wharf patches from recent releases, so installed copies (Appfredo updating itself among
# them) download only what changed.
#
#   scripts/release.sh vMAJOR.MINOR.PATCH [--previous-apk FILE]...
#
# versionCode is MAJOR*1000000 + MINOR*1000 + PATCH, so it rises with every release. Without --previous-apk,
# the APKs of the newest $APPFREDO_PATCH_RELEASES (default 3; 0 for none) stable GitHub releases are fetched
# with gh.
# Signing needs APPFREDO_KEYSTORE, the PKCS12 keystore holding the key aliased appfredo, and
# APPFREDO_KEYSTORE_PASSWORD; butler is $BUTLER or on PATH. The assets land in build/release/<tag>/, ready to upload as they are.
set -euo pipefail
root="$(cd "$(dirname "$0")/.." && pwd)"
tag="${1:?Usage: scripts/release.sh vMAJOR.MINOR.PATCH [--previous-apk FILE]...}"
shift
if [[ ! "$tag" =~ ^v([0-9]+)\.([0-9]+)\.([0-9]+)$ ]]; then
    echo "Release tags look like v1.2.3, not $tag" >&2; exit 1
fi
major=$((10#${BASH_REMATCH[1]})) minor=$((10#${BASH_REMATCH[2]})) patch=$((10#${BASH_REMATCH[3]}))
if (( major > 2000 || minor > 999 || patch > 999 )); then
    echo "$tag does not fit the versionCode scheme (minor and patch up to 999)" >&2; exit 1
fi
version="$major.$minor.$patch"
code=$(( major * 1000000 + minor * 1000 + patch ))
previous=()
while (( $# )); do
    case "$1" in
        --previous-apk) previous+=("$(cd "$(dirname "$2")" && pwd)/$(basename "$2")"); shift 2 ;;
        *) echo "Unknown argument: $1" >&2; exit 1 ;;
    esac
done
: "${APPFREDO_KEYSTORE:?Set APPFREDO_KEYSTORE and APPFREDO_KEYSTORE_PASSWORD: a release must be signed with the release key}"
sdk="${ANDROID_HOME:-$HOME/Android/Sdk}"
build_tools="${APPFREDO_BUILD_TOOLS:-$sdk/build-tools/37.0.0}"
out="$root/build/release/$tag"
work="$root/build/release/.work-$tag"
rm -rf "$out" "$work"
mkdir -p "$work"
cd "$root"

./gradlew :app:assembleRelease -Pappfredo.versionCode="$code" -Pappfredo.versionName="$version"
apk="$root/app/build/outputs/apk/release/app-release.apk"
[[ -f "$apk" ]] || { echo "No signed release APK at $apk" >&2; exit 1; }

if (( ${#previous[@]} == 0 && ${APPFREDO_PATCH_RELEASES:-3} > 0 )); then
    # Newest first. A re-run never patches from the release it is replacing.
    releases=$(gh release list --exclude-drafts --exclude-pre-releases --limit 20 --json tagName --jq '.[].tagName') \
        || { echo "Could not list earlier releases with gh; pass --previous-apk to build patches without it" >&2; exit 1; }
    for earlier in $(grep -vx -- "$tag" <<<"$releases" | head -n "${APPFREDO_PATCH_RELEASES:-3}"); do
        dir="$work/previous/$earlier"
        mkdir -p "$dir"
        if ! gh release download "$earlier" --pattern appfredo.json --dir "$dir"; then
            echo "$earlier has no appfredo.json, so there is no patch from it" >&2
            continue
        fi
        asset=$(python3 -c 'import json, sys; print(json.load(open(sys.argv[1]))["apk"]["asset"])' "$dir/appfredo.json")
        gh release download "$earlier" --pattern "$asset" --dir "$dir"
        previous+=("$dir/$asset")
    done
fi

patches=()
for earlier_apk in "${previous[@]}"; do patches+=(--previous-apk "$earlier_apk"); done
python3 "$root/scripts/prepare-release.py" --apk "$apk" --name Appfredo --out "$out" "${patches[@]}" \
    --butler "${BUTLER:-butler}" --aapt "$build_tools/aapt" --apksigner "$build_tools/apksigner"
rm -rf "$work"
ls -l "$out"

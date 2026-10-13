#!/usr/bin/env bash
set -euo pipefail
root="$(cd "$(dirname "$0")/.." && pwd)"
if [[ -x "$root/.tools/go/bin/go" ]]; then export PATH="$root/.tools/go/bin:$PATH"; fi
export GOPATH="${APPFREDO_GOPATH:-$root/.tools/gopath}"
export GOCACHE="${APPFREDO_GOCACHE:-$root/.tools/go-cache}"
export GOFLAGS="${GOFLAGS:-} -buildvcs=false"
export PATH="$GOPATH/bin:$PATH"
export ANDROID_HOME="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}}"
if [[ -d "$root/.tools/android-sdk/ndk/29.0.14206865" ]]; then
    export ANDROID_NDK_HOME="$root/.tools/android-sdk/ndk/29.0.14206865"
fi
: "${ANDROID_NDK_HOME:?Set ANDROID_NDK_HOME to Android NDK r29 or newer}"
cd "$root/native/wharf"
go install golang.org/x/mobile/cmd/gomobile golang.org/x/mobile/cmd/gobind
gomobile init
mkdir -p "$root/app/libs"
gomobile bind -target=android/arm64,android/amd64 -androidapi 26 -ldflags='-s -w' -o "$root/app/libs/wharf.aar" .

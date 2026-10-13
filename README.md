# Appfredo

Your app butler. A native Android store for a personal collection of apps distributed through public GitHub Releases, with optional **Wharf delta updates**.

## What works in this first version

- Add/remove a GitHub repository by URL or `owner/repository`; keep the library and last release metadata across restarts.
- Read the latest stable release's `appfredo.json`, show release notes, filter the library, and compare installed Android version codes.
- Download a complete signed APK, or apply a smaller `.pwr` patch to an exactly matching previous APK.
- Verify download lengths and SHA-256 hashes, the reconstructed APK hash, and its Android package/version/signing metadata.
- Fall back to the full APK when the old APK does not match or a patch fails.
- Submit the APK through `PackageInstaller` and let the user review Android's installation confirmation.
- Keep a verified APK for future updates; remove superseded cached APKs after successful installation.

Kotlin/Jetpack Compose provides the app. A small Go wrapper embeds the existing Wharf library through `gomobile bind`. There is no butler daemon, downloaded executable, or itch.io account on the phone. Publishers use desktop butler to produce patches.

The visual identity uses forest green, warm ivory, and a serving cloche with a bow tie.

## Build

Requirements: JDK 21, Go 1.27.2, Android SDK platform 36, build-tools 36.0.0, and NDK r29 (`29.0.14206865`). Dependencies and the Gradle distribution are pinned. The native build targets **ARM64 and x86_64**, Android 8/API 26 or later.

```sh
export ANDROID_HOME="$HOME/Android/Sdk"
export ANDROID_NDK_HOME="$ANDROID_HOME/ndk/29.0.14206865"
# If needed: sdkmanager 'platforms;android-36' 'build-tools;36.0.0' 'ndk;29.0.14206865'
bash scripts/build-wharf.sh
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

The APK is `app/build/outputs/apk/debug/app-debug.apk`. It is a development build signed with a local debug key. Set up a persistent release signing key before distributing Appfredo itself. Generated native libraries live in `app/libs/` and are intentionally ignored; build them before Gradle.

On this development machine, Go and NDK were installed under `.tools/`; the build script also discovers those local copies. No global Go/NDK installation was required.

## Publish an app for Appfredo

Build and sign a **standalone APK** first. Appfredo preserves its exact bytes. Create the release assets locally:

```sh
export PATH="$ANDROID_HOME/build-tools/36.0.0:$PATH"
python3 scripts/prepare-release.py \
  --apk path/to/new-signed.apk \
  --previous-apk path/to/previous-signed.apk \
  --name 'My App' \
  --out release-assets
```

Omit `--previous-apk` for the first release. Put `butler` on `PATH` or supply `--butler /path/to/butler`. The helper reads the package and version from the APK, verifies APK signatures, checks package/signing-key continuity, and runs `butler diff --verify`. The output directory must be new or empty.

Upload **all files in `release-assets` to the same public, stable GitHub Release**. The helper prepares files only; it never uploads or publishes anything. Add that repository in Appfredo, refresh, and tap **Get app** or **Get update**. Once ready, tap **Install with Android**, enable installation from Appfredo if requested, return, and proceed to **Review installation**.

See [the release protocol](docs/release-protocol.md) for the manifest and patch layout.

## Tests

```sh
# The build script pins tools and creates the local Go module cache.
export PATH="$PWD/.tools/go/bin:$PATH"
export GOPATH="$PWD/.tools/gopath" GOCACHE="$PWD/.tools/go-cache"
go -C native/wharf test ./...
./gradlew :app:testDebugUnitTest :app:lintDebug
```

For Android integration tests, create signed disposable fixture APKs (requires desktop butler), boot a **disposable emulator**, then run:

```sh
BUTLER=/path/to/butler bash scripts/make-test-releases.sh
./gradlew :app:connectedDebugAndroidTest
```

These tests install/remove only `app.appfredo.fixture` and allow Appfredo's installation permission on the emulator. They cover full preparation, cached reuse, delta reconstruction from the installed APK, mismatched-base fallback, bad-patch fallback, and a user-confirmed installation. Do not run them on a personal device. To regenerate fixtures, remove `build/fixtures/releases` first; release outputs are never overwritten.

The small native test data is synthetic binary content, not an installable APK. The Android integration fixtures are actual signed, resource-only APKs. See [validation](docs/validation.md) for recorded results and remaining boundaries.

## Current scope

- Public github.com repositories, stable releases, one standalone APK per release. No GitHub sign-in, prereleases, split APKs/AABs, or ABI-specific asset selection yet.
- Refresh and installation are manual. Downloads run while Appfredo is open. Process death restarts an incomplete transfer; it does not resume partial bytes. A completed verified APK can be reused after reopening.
- Direct patches only, from an installed version to the latest release; no multi-release patch chains yet.
- Patch sizes and old hashes are publisher-provided hints until checked. A `.sig` is Wharf content verification data, not a publisher identity signature. Trust comes from the repository you chose over HTTPS and Android's APK signing enforcement. Initial installation still requires choosing a publisher you trust.
- Unauthenticated GitHub API rate limits apply. No production signing key, public repository, or published release has been configured in this workspace.

## Implementation references

- [Butler offline diffing and patching](https://itch.io/docs/butler/offline.html)
- [Wharf source](https://github.com/itchio/wharf)
- [gomobile Android library bindings](https://pkg.go.dev/golang.org/x/mobile/cmd/gomobile)
- [Android PackageInstaller](https://developer.android.com/reference/android/content/pm/PackageInstaller)
- [APK signatures and exact file contents](https://source.android.com/docs/security/features/apksigning/v2)

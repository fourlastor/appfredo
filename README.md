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

The visual identity is banana yellow and tailcoat black: a butler, a serving cloche, and the banana under it. The illustrations and icons are vector artwork drawn in Compose (`app/src/main/java/app/appfredo/ui/art`); the theme follows the system's light or dark setting. Headlines are set in Fraunces and everything else in Figtree, both bundled under the SIL Open Font License (see `licenses/`).

## Build

Requirements: JDK 21, Go 1.27.2, Android SDK platform 37, build-tools 36.0.0, and NDK r29 (`29.0.14206865`). The app compiles against API 37 for Material 3 Expressive (`material3` 1.5.0-beta01) and still targets API 36. Dependencies and the Gradle distribution are pinned. The native build targets **ARM64 and x86_64**, Android 8/API 26 or later.

```sh
export ANDROID_HOME="$HOME/Android/Sdk"
export ANDROID_NDK_HOME="$ANDROID_HOME/ndk/29.0.14206865"
# If needed: sdkmanager 'platforms;android-37.0' 'build-tools;36.0.0' 'ndk;29.0.14206865'
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

Omit `--previous-apk` for the first release, or repeat it to ship a patch from each of several earlier releases; Appfredo picks the one matching the installed version. Put `butler` on `PATH` or supply `--butler /path/to/butler`. The helper reads the package and version from the APK, verifies APK signatures, checks package/signing-key continuity, and runs `butler diff --verify`. The output directory must be new or empty.

Upload **all files in `release-assets` to the same public, stable GitHub Release**. The helper prepares files only; it never uploads or publishes anything. Add that repository in Appfredo, then tap **Get** or **Update**. Once the app is served, tap **Install**. The first time, Appfredo sends you to Android's settings to allow it to install apps and carries on when you return; Android's own confirmation follows. If you leave that prompt without answering, **Show prompt** brings it back.

See [the release protocol](docs/release-protocol.md) for the manifest and patch layout.

## Release Appfredo itself

Pushing a version tag publishes a GitHub release that Appfredo can install, and update itself from, like any other app in its library:

```sh
git tag v0.2.0
git push origin v0.2.0
```

After the usual build, tests and lint, the `release` job in `.github/workflows/build.yml` runs `scripts/release.sh` and attaches its output to a new stable release: `appfredo.json`, the signed `app.apk`, and a Wharf patch (with butler's `.sig`) from each of the three newest earlier releases. It downloads those releases' APKs to diff against, and uses butler 15.32.0, pinned by checksum. Patches that would not be smaller than the APK are left out. The release is created as a draft and published only once every asset is attached.

Tags must look like `vMAJOR.MINOR.PATCH`. The versionCode is `MAJOR × 1,000,000 + MINOR × 1,000 + PATCH` (minor and patch up to 999), so every new tag outranks the last; `v0.2.0` is versionCode 2000.

Before the first tag, create a release key and store it in the repository's Actions secrets. The key's alias must be `appfredo`:

```sh
keytool -genkeypair -v -keystore appfredo-release.jks -storetype PKCS12 -alias appfredo -keyalg RSA -keysize 4096 -validity 10000
base64 -w0 appfredo-release.jks
```

| Secret | Value |
| --- | --- |
| `SIGNING_KEYSTORE` | The `base64` output above |
| `SIGNING_PASSWORD` | The keystore's password, which a PKCS12 keystore also uses for the key |

Keep the keystore backed up. Every release must be signed with the same key, or Android refuses the update and no patch can be built from earlier releases.

To let Appfredo update itself, install it from a release rather than from a local debug build (which is signed with the debug key), then add this repository to its library.

The script also runs locally, with `APPFREDO_KEYSTORE` set to the keystore file, `APPFREDO_KEYSTORE_PASSWORD` to its password, and either `gh` signed in or the earlier APKs given explicitly:

```sh
BUTLER=/path/to/butler scripts/release.sh v0.2.0 --previous-apk v0.1.0/app.apk
```

Set `APPFREDO_PATCH_RELEASES=0` to build without patches. The assets land in `build/release/<tag>/`.

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

To look at the interface without a repository or a network, debug builds include a gallery that runs every screen state on sample data with a pretend store behind it:

```sh
adb shell am start -n app.appfredo/.debug.GalleryActivity -e scene served -e theme dark
```

Scenes are `empty`, `library`, `fresh`, `checking`, `working`, `stitching`, `served`, `cancelled`, `confirming`, `done` and `trouble`; `theme` is `light` or `dark` and defaults to the system's. Add `--ez permission true` to see the step that asks for permission to install.

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

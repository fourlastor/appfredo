# First-build validation

Validated on 13–14 October 2026, on Linux with JDK 21, Go 1.27.2, Android SDK 36, Gradle 9.8.0, AGP 9.4.1 and NDK 29.0.14206865.

## Results

- Wharf compiled for `arm64-v8a` and `x86_64` through gomobile, with no modifications to upstream Wharf. The Appfredo wrapper restricts patches to one opaque `app.apk` and verifies complete input/output hashes.
- Four Go test functions pass, including three invalid-input subcases: genuine butler patch reconstruction, incorrect base/output hash and output size, unexpected container paths, and preservation of existing output.
- Eight JVM tests cover repository URL validation, manifest validation, patch eligibility, exact download size/hash checks, failed download cleanup, HTTP errors/HTTPS enforcement, GitHub manifest discovery/cache persistence, missing manifest errors, and release asset consistency.
- Six Android instrumentation tests pass on a dedicated **API 35 x86_64 emulator**. These include bundled-native reconstruction, actual signed APK delta preparation, full APK/cache use, corrupt-patch fallback, mismatched-base fallback, and installation through Android's user-confirmation UI.
- The installation test started with a signed version-1 fixture, reconstructed version 2, confirmed Android's Update dialog, and verified that Android's installed APK SHA-256 equaled the publisher's new APK hash.
- `assembleDebug`, `testDebugUnitTest`, and `lintDebug` pass. Lint reports dependency-update recommendations and minor platform/style warnings; it reports no errors.
- The app's empty library and add-repository UI were visually inspected on the emulator. A debug APK is available at `app/build/outputs/apk/debug/app-debug.apk`.

The signed, resource-only test APK was 532,888 bytes; its butler v15.31.0 patch was 135,258 bytes (about 25% of the full APK). This is a controlled fixture, **not an estimate of savings on real applications**.

## Boundaries

The Android tests use local fixture assets through an injected downloader. GitHub discovery and HTTPS transfer validation are tested separately with controlled HTTP connection responses. No end-to-end install from a live GitHub release containing an Appfredo manifest has been performed yet; no such repository was supplied or published during this work.

ARM64 was compiled, not exercised on physical hardware. Android 8/minimum-SDK and Android 16/target-SDK devices were not runtime-tested. Interrupted transfer resumption, background scheduling, private repositories, split APKs, key rotation, and Appfredo self-update have not been validated.

The GitHub Actions workflow is included but has not run remotely. No production release was signed or published.

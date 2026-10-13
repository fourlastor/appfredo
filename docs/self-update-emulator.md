# Appfredo self-update test — 14 October 2026

**Result: passed.** The running Appfredo v1 downloaded a Wharf patch over HTTP from the development computer, reconstructed its own v2 APK, and submitted it to Android's installer. After confirmation, Android replaced Appfredo with v2. The installed APK exactly matched the locally compiled v2, and the saved library survived.

## Setup

- Source baseline: `afbe8d6fd448780aa6709882117ee93095abe459`.
- Dedicated `Appfredo_Test` emulator, Android API 35, x86_64.
- Both builds use application ID `app.appfredo` and the same local debug signing key.
- V1: version code `1`, version name `0.1.0-selftest-v1`, visible text `Running version 1.`
- V2: version code `2`, version name `0.1.0-selftest-v2`, visible text `Running version 2.`
- The user's downloaded `butler-linux-amd64/butler`: v15.32.0, revision `336f95416289de8fd17589000d48de5526c1a8f2`.
- Host server bound to `127.0.0.1:38307`; the emulator accessed it at `http://10.0.2.2:38307/`.

The temporary `appfredo/self-test` repository entry loaded a manifest from that endpoint. The temporary HTTP exception was restricted to the emulator host address and chosen port, with a debug-only Android network-security configuration. The downloader, hash checks, patch selection, bundled Go/Wharf library, `UpdateEngine`, `Installer`, and Android confirmation flow were otherwise unchanged. All temporary source/version/network changes were removed after the test.

## Actual sequence

1. Compile v1, install it with ADB on the disposable emulator, and launch it.
2. Change the visible text and version metadata; compile v2 with the same signing key.
3. Run `scripts/prepare-release.py` with both APKs and the downloaded butler. Its `butler diff --verify` succeeded. Serve the resulting manifest and assets from the local port.
4. In v1, add `appfredo/self-test` through the normal UI. Appfredo detects the v1 → v2 update.
5. Tap **Get update**. Appfredo fetches the patch, verifies it, reconstructs v2, and displays **Prepared from a patch**.
6. Independently hash the installed v1 and Appfredo's prepared v2 file through ADB; both match the manifest.
7. Tap **Install with Android**, grant installation from Appfredo in Android Settings, return, then tap **Install with Android** and **Review installation**.
8. Confirm **Update** in Android's “Do you want to update this app?” dialog.
9. Android returns to the launcher during replacement. Reopen Appfredo: the changed v2 text appears, the repository remains in the library, and there are zero available updates. The install-success message was also received.

V2 was **not installed with ADB**. ADB was used to install v1, drive the test UI, and inspect the result; Appfredo submitted the v2 installation itself.

## Evidence

| Measurement | Result |
| --- | --- |
| Complete v2 APK | 38,570,804 bytes (36.78 MiB) |
| Wharf patch | 259,678 bytes (253.59 KiB) |
| Patch as a fraction of the APK | 0.6733% |
| Full APK requested by Appfredo | No |
| Version after replacement | `2` / `0.1.0-selftest-v2` |
| Android installer package | `app.appfredo` |
| Android initiating package | `app.appfredo` |
| App process before/after replacement | `3310` → `3911` |

The HTTP server recorded exactly two successful requests: `/appfredo.json` and `/from-1-to-2.pwr`. No `/app.apk` request occurred.

```text
v1 SHA-256:
4357e1c1c472519d57f68f4441256b0b19f6ff581d4de0da8aab38f9e539fa97

compiled v2 = reconstructed v2 = installed v2 SHA-256:
a18e9198146019ae56ab98fe6af74b99bb491f72bafc2a287b41e4cfae565701

patch SHA-256:
67b0c9f0fa639036d10bbc741db9787d39151d1f6e1bf95a4028a64dcb72a397
```

Local artifacts are retained in the ignored `build/self-update-test/` directory: both APKs, the manifest/patch, source-change patches, build logs, HTTP log, before/after package dumps, screenshots, Android logs, and `result.json`. The temporary server and emulator were stopped after validation. The normal debug APK was rebuilt from restored source.

## What this establishes

The existing installation path can update Appfredo itself with a Wharf delta and user confirmation on this emulator. No installer or process-restart fix was needed for this successful run. Manual reopening was necessary; automatic reopening is not implemented.

This remains a local-source, same-signing-key, single-update test. It does not validate public GitHub self-update discovery, production signing, physical ARM64 hardware, interrupted self-updates, or guaranteed completion-state recovery after arbitrary process death. Patch savings reflect a small change between these two debug APKs, not an estimate for all releases.

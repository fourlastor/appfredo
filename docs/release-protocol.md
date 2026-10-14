# Appfredo release protocol, version 1

A public GitHub repository's latest stable release contains an asset named `appfredo.json`. Appfredo obtains asset URLs from GitHub's release response. The manifest references filenames, never arbitrary download URLs.

```json
{
  "schemaVersion": 1,
  "packageName": "com.example.myapp",
  "name": "My App",
  "versionCode": 2,
  "versionName": "1.1.0",
  "apk": {
    "asset": "app.apk",
    "sha256": "<64 lowercase hexadecimal characters>",
    "size": 12345678
  },
  "patches": [
    {
      "fromVersionCode": 1,
      "fromSha256": "<SHA-256 of the exact previous signed APK>",
      "asset": "from-1-to-2.pwr",
      "sha256": "<SHA-256 of the patch file>",
      "size": 123456
    }
  ]
}
```

`patches` may list one entry per earlier release the publisher wants to support; Appfredo uses the one whose source matches the installed version. All sizes are bytes. The placeholder hashes above must be replaced with real hashes. `patches` may be omitted or empty. Version codes are positive Android version codes; every patch's source code must be lower than the new one. The manifest package name, version code, and version name must match the signed APK. Artifact sizes must match GitHub's asset metadata and downloaded byte count; maximum supported size is 4 GiB.

Patches must be generated over directories containing exactly one file named `app.apk`:

```text
old/app.apk  # exact, signed old APK
new/app.apk  # exact, signed new APK
```

```sh
butler diff --verify old/ new/ from-1-to-2.pwr
```

Do **not** give butler an APK as an archive to unpack. APKs are opaque files within these directories. Do not zip, recompress, or sign the new APK after computing its manifest/patch. The wrapper rejects extra files, directories, symlinks, unexpected names, and inconsistent container sizes. It writes to a new directory, checks the output hash, and moves the APK into the cache only after verification.

Butler also produces `from-1-to-2.pwr.sig`. Uploading it preserves compatibility with desktop butler verification; Appfredo itself uses the manifest's complete APK SHA-256, so it does not need to download the `.sig`.

Appfredo selects a patch only if its source version matches the installed single APK and it is smaller than the full download. Before patching, it hashes the cached previous APK or Android's installed base APK. A missing/mismatched base, failed patch transfer, or reconstruction failure uses the complete APK instead. APK signing and the existing package's signing-key compatibility are finally enforced by Android's installer.

The release helper currently accepts the same signing certificate set across versions. Legitimate key rotation needs a later extension to that helper. Publishers should keep their release signing key stable.

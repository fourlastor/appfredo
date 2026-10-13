#!/usr/bin/env bash
# Create two signed, resource-only Android fixture APKs. Never publish these.
set -euo pipefail
root="$(cd "$(dirname "$0")/.." && pwd)"
sdk="${ANDROID_HOME:-$HOME/Android/Sdk}"
build_tools="${APPFREDO_BUILD_TOOLS:-$sdk/build-tools/36.0.0}"
fixture="$root/build/fixtures"
mkdir -p "$fixture"
if [[ ! -f "$fixture/test.keystore" ]]; then
    keytool -genkeypair -keystore "$fixture/test.keystore" -storepass android -keypass android \
        -alias fixture -keyalg RSA -keysize 2048 -validity 30 -dname 'CN=Appfredo Test Only'
fi
for version in 1 2; do
    work="$fixture/work-$version"
    mkdir -p "$work/assets"
    python3 - "$work" "$version" <<'PY'
from pathlib import Path
import sys,random
work, version = Path(sys.argv[1]), int(sys.argv[2])
payload = bytearray(random.Random(4721).randbytes(512 * 1024))
payload[65536:65540] = version.to_bytes(4, 'little')
(work/'assets/payload.bin').write_bytes(payload)
(work/'AndroidManifest.xml').write_text(f'''<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="app.appfredo.fixture" android:versionCode="{version}" android:versionName="{version}.0">
<uses-sdk android:minSdkVersion="26" android:targetSdkVersion="36"/>
<application android:label="Appfredo Fixture" android:hasCode="false" android:debuggable="true"/>
</manifest>''')
PY
    "$build_tools/aapt2" link -I "$sdk/platforms/android-36/android.jar" --manifest "$work/AndroidManifest.xml" -A "$work/assets" -0 bin -o "$work/unsigned.apk"
    "$build_tools/zipalign" -f -P 16 4 "$work/unsigned.apk" "$work/aligned.apk"
    "$build_tools/apksigner" sign --ks "$fixture/test.keystore" --ks-key-alias fixture --ks-pass pass:android --key-pass pass:android \
        --out "$fixture/v$version.apk" "$work/aligned.apk"
done
# Fresh invocation refuses to overwrite a release. Tests can remove build/fixtures/releases first.
mkdir -p "$fixture/releases"
python3 "$root/scripts/prepare-release.py" --apk "$fixture/v2.apk" --previous-apk "$fixture/v1.apk" \
    --name 'Appfredo Fixture' --out "$fixture/releases/v2" --butler "${BUTLER:-butler}" \
    --aapt "$build_tools/aapt" --apksigner "$build_tools/apksigner"
mkdir -p "$fixture/assets/fixture"
cp "$fixture/releases/v2/"* "$fixture/assets/fixture/"
cp "$fixture/v1.apk" "$fixture/assets/fixture/old.apk"

#!/usr/bin/env bash
# Builds the installable watch face APK without Gradle / Android Studio.
#
# Pipeline: generate WFF XML -> render preview -> validate (official Google WFF
# XSD validator) -> automated tests -> aapt2 compile/link -> zipalign -> sign.
#
# Requirements (Ubuntu 24.04): python3 + fonttools uharfbuzz cairosvg playwright,
#   aapt2 apksigner zipalign android-sdk-platform-23 (apt), a JDK, and the WFF
#   validator built from github.com/google/watchface (path in $WFF_VALIDATOR).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
MAIN="$ROOT/NoorWatchFace/watchface/src/main"
GEN="$ROOT/generator"
OUT="$ROOT/out"
VERSION_CODE="${VERSION_CODE:-1}"
VERSION_NAME="${VERSION_NAME:-1.0.0}"
export VERSION_CODE VERSION_NAME          # gen_watchface.py tags font file names with the code
ANDROID_JAR="${ANDROID_JAR:-/usr/lib/android-sdk/platforms/android-23/android.jar}"
WFF_VALIDATOR="${WFF_VALIDATOR:-/home/claude/tools/wffvalidator/wffvalidate}"
KS="$ROOT/signing/noor-release.jks"
KS_PASS="${NOOR_KS_PASS:-noorwatchface}"

rm -rf "$OUT" && mkdir -p "$OUT"

echo "== 1/6 generate Watch Face Format resources (icons -> PNG + icon font, XML, strings)"
python3 "$GEN/icons.py" "$GEN/../build_assets/icons" > /dev/null
python3 "$GEN/icon_font.py" "$GEN/../build_assets/font/icons.ttf"
python3 "$GEN/gen_watchface.py" "$MAIN"

echo "== 2/6 render picker preview + review images (simulated); icon font vs PNG icons"
python3 "$GEN/render.py" "$MAIN/res" "$ROOT/renders" > /dev/null
python3 "$GEN/check_icons.py" "$MAIN/res/font/icons_v$VERSION_CODE.ttf" "$GEN/../build_assets/icons" "$ROOT/docs/icon_font_check.json"

echo "== 3/6 validate against the official WFF v4 schema"
"$WFF_VALIDATOR" 4 "$MAIN/res/raw/watchface.xml" 2>&1 | grep -E "PASSED|FAILED|SEVERE|cvc-" | tee "$OUT/validation.txt"
grep -q PASSED "$OUT/validation.txt"

echo "== 4/6 automated tests"
python3 "$GEN/tests.py" "$MAIN/res/raw/watchface.xml" | tail -3

echo "== 5/6 aapt2 compile + link"
aapt2 compile --dir "$MAIN/res" -o "$OUT/res.zip"
aapt2 link -o "$OUT/unaligned.apk" -I "$ANDROID_JAR" \
    --manifest "$MAIN/AndroidManifest.xml" \
    --min-sdk-version 36 --target-sdk-version 36 \
    --version-code "$VERSION_CODE" --version-name "$VERSION_NAME" \
    --no-auto-version "$OUT/res.zip"

echo "== 6/6 zipalign + sign (APK Signature Scheme v2/v3)"
zipalign -p -f 4 "$OUT/unaligned.apk" "$OUT/aligned.apk"
apksigner sign --ks "$KS" --ks-key-alias noor --ks-pass "pass:$KS_PASS" --key-pass "pass:$KS_PASS" \
    --v1-signing-enabled false --v2-signing-enabled true --v3-signing-enabled true \
    --out "$OUT/NoorPrayerWatchFace-$VERSION_NAME.apk" "$OUT/aligned.apk"
apksigner verify --verbose "$OUT/NoorPrayerWatchFace-$VERSION_NAME.apk" | head -6
rm -f "$OUT/unaligned.apk" "$OUT/aligned.apk" "$OUT/res.zip"
ls -la "$OUT"

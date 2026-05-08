#!/bin/bash
set -e

SDK="$HOME/Library/Android/sdk"
BUILD_TOOLS="$SDK/build-tools/35.0.0"
PLATFORM="$SDK/platforms/android-36"
PROJECT="$(cd "$(dirname "$0")"; pwd)"
OUT="$PROJECT/out"

rm -rf "$OUT"
mkdir -p "$OUT/obj" "$OUT/dex" "$OUT/apk-raw"

echo "==> Compiling R.java..."
"$BUILD_TOOLS/aapt" package -f -m \
    -J "$OUT/obj" \
    -S "$PROJECT/app/src/main/res" \
    -M "$PROJECT/app/src/main/AndroidManifest.xml" \
    -I "$PLATFORM/android.jar"

echo "==> Compiling Java..."
javac -source 8 -target 8 \
    -classpath "$PLATFORM/android.jar" \
    -d "$OUT/obj" \
    "$OUT/obj/com/test/nfc/R.java" \
    "$PROJECT/app/src/main/java/com/test/nfc/MainActivity.java"

echo "==> Dexing..."
"$BUILD_TOOLS/d8" \
    --output "$OUT/dex" \
    --lib "$PLATFORM/android.jar" \
    $(find "$OUT/obj" -name "*.class")

echo "==> Packaging APK..."
"$BUILD_TOOLS/aapt" package -f \
    -F "$OUT/apk-raw/nfc-test.apk" \
    -S "$PROJECT/app/src/main/res" \
    -M "$PROJECT/app/src/main/AndroidManifest.xml" \
    -I "$PLATFORM/android.jar"

cd "$OUT/dex"
"$BUILD_TOOLS/aapt" add "$OUT/apk-raw/nfc-test.apk" classes.dex

echo "==> Signing APK..."
if [ ! -f "$OUT/test.keystore" ]; then
    keytool -genkeypair -v \
        -keystore "$OUT/test.keystore" \
        -alias testkey \
        -keyalg RSA -keysize 2048 \
        -validity 10000 \
        -storepass android \
        -keypass android \
        -dname "CN=Test, O=Test, C=US"
fi

"$BUILD_TOOLS/apksigner" sign \
    --ks "$OUT/test.keystore" \
    --ks-key-alias testkey \
    --ks-pass pass:android \
    --key-pass pass:android \
    --out "$OUT/nfc-test.apk" \
    "$OUT/apk-raw/nfc-test.apk"

echo ""
echo "==> APK ready: $OUT/nfc-test.apk"
echo ""
echo "==> Installing on connected device..."
adb install -r "$OUT/nfc-test.apk"
echo ""
echo "==> Launching..."
adb shell am start -n com.test.nfc/.MainActivity

#!/bin/bash
# Builds HeartSounds.apk. Needs ANDROID_SDK_ROOT and signing.env (see README.md).
set -e
cd "$(dirname "$0")"
S=${ANDROID_SDK_ROOT:?Set ANDROID_SDK_ROOT to your Android SDK}
[ -f signing.env ] || { echo "signing.env is missing, see README.md"; exit 1; }
. ./signing.env
BT=$S/build-tools/36.0.0; J=$S/platforms/android-36/android.jar; O=out; rm -rf $O; mkdir -p $O/classes $O/res
$BT/aapt2 compile --dir app/res -o $O/res/res.zip
$BT/aapt2 link -o $O/base.apk -I $J --manifest app/AndroidManifest.xml --min-sdk-version 29 --target-sdk-version 36 -A app/assets $O/res/res.zip
javac -nowarn --release 11 -classpath $J -d $O/classes app/src/pl/heartsounds/*.java app/src/android/print/*.java 2>&1 | grep -v "^Picked up\|^Note" || true
$BT/d8 --release --lib $J --min-api 29 --output $O $(find $O/classes -name '*.class') 2>&1 | grep -v "^Picked up" || true
cp $O/base.apk $O/u.apk; (cd $O && zip -q u.apk classes.dex)
$BT/zipalign -f -p 4 $O/u.apk $O/a.apk
$BT/apksigner sign --ks "$KS_FILE" --ks-pass "pass:$KS_PASS" --out HeartSounds.apk $O/a.apk 2>&1 | grep -v "^Picked up" || true
$BT/apksigner verify HeartSounds.apk 2>&1 | grep -v "^Picked up\|WARNING" || true
ls -la HeartSounds.apk; sha256sum HeartSounds.apk

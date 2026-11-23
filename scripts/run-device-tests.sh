#!/usr/bin/env bash
# Runs the instrumented suite on a connected device/emulator, keeps the app installed so
# screenshots can be pulled, then smoke-tests the minified release APK.
set -uo pipefail
PKG=io.github.goldengate.desktop.debug
OUT=device-output
mkdir -p "$OUT"
adb wait-for-device
adb shell settings put global window_animation_scale 1
# Silence one-time system UI that would cover the app on a fresh emulator.
adb shell settings put secure immersive_mode_confirmations confirmed
adb shell settings put global hide_error_dialogs 1 || true
sleep 20
adb shell am broadcast -a android.intent.action.CLOSE_SYSTEM_DIALOGS || true
adb install -r -g dist/GoldenGateDesktop-debug.apk
adb install -r -g dist/androidTest.apk
ARGS=""
if [ "${SUITE:-all}" != "all" ]; then ARGS="-e class $SUITE"; fi
adb shell am instrument -w -r $ARGS $PKG.test/androidx.test.runner.AndroidJUnitRunner | tee "$OUT/instrumentation.txt"
adb exec-out run-as $PKG tar c files/screenshots > "$OUT/shots.tar" || true
(cd "$OUT" && tar xf shots.tar && mv files/screenshots/* . 2>/dev/null; rm -rf files shots.tar) || true
adb logcat -d > "$OUT/logcat-tests.txt" || true

# Release (R8-minified) smoke test: install, launch, make sure the process stays alive.
adb install -r dist/GoldenGateDesktop.apk
adb logcat -c
adb shell am start -W -n io.github.goldengate.desktop/.MainActivity
sleep 12
adb exec-out screencap -p > "$OUT/release-smoke.png" || true
PID=$(adb shell pidof io.github.goldengate.desktop || true)
adb logcat -d > "$OUT/logcat-release.txt" || true
ls -la "$OUT"

FAIL=0
if ! grep -q "^OK (" "$OUT/instrumentation.txt"; then echo "::error::Instrumented tests failed"; FAIL=1; fi
if [ -z "$PID" ]; then echo "::error::Release APK is not running after launch"; FAIL=1; fi
if grep -q "FATAL EXCEPTION" "$OUT/logcat-release.txt"; then echo "::error::Release APK crashed"; FAIL=1; fi
exit $FAIL

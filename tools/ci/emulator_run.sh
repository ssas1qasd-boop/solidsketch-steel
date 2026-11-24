#!/usr/bin/env bash
# Runs inside reactivecircus/android-emulator-runner (emulator booted, adb connected).
# The owner's phone: 1080 x 2340 at 420 dpi, dark mode, 3-button navigation. Records the screen while the tests run.
set -u
OUT=ci-out; mkdir -p "$OUT"
adb shell wm size 1080x2340
adb shell wm density 420
adb shell cmd uimode night yes
adb shell cmd overlay enable-exclusive --category com.android.internal.systemui.navbar.threebutton || adb shell cmd overlay enable com.android.internal.systemui.navbar.threebutton
adb shell settings put secure navigation_mode 0 || true
sleep 3
adb shell wm size > "$OUT/device.txt"; adb shell wm density >> "$OUT/device.txt"; adb shell cmd uimode night >> "$OUT/device.txt"
adb shell cmd overlay list | grep -i navbar >> "$OUT/device.txt" || true
adb logcat -c || true

# screen recording: screenrecord stops after 180 s, so record in segments until the tests end
rm -f "$OUT/.stop"
( i=0; while [ ! -f "$OUT/.stop" ]; do i=$((i+1)); adb shell screenrecord --bit-rate 4000000 --time-limit 175 "/sdcard/ci_rec_$i.mp4" || sleep 2; done ) &
REC=$!

START=$(date +%s)
./gradlew --no-daemon :app:connectedDebugAndroidTest -Pabis=x86_64 -PskipUiGolden --stacktrace > "$OUT/connected.log" 2>&1
RC=$?
echo "$(( $(date +%s) - START ))" > "$OUT/connected_seconds.txt"
echo "$RC" > "$OUT/connected_rc.txt"

touch "$OUT/.stop"; adb shell pkill -INT screenrecord || true; sleep 4; kill $REC 2>/dev/null || true; wait $REC 2>/dev/null || true
for f in $(adb shell ls /sdcard/ | tr -d '\r' | grep '^ci_rec_.*\.mp4$'); do adb pull "/sdcard/$f" "$OUT/$f" || true; done
adb logcat -d > "$OUT/logcat.txt" || true
tail -n 60 "$OUT/connected.log"
exit 0   # the result is read from connected_rc.txt (the summary step fails the job)

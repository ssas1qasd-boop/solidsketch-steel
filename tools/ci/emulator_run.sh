#!/usr/bin/env bash
# Runs inside reactivecircus/android-emulator-runner (emulator booted, adb connected).
# The owner's phone: 1080 x 2340 at 420 dpi, dark mode, 3-button navigation. Records the screen while the tests run.
set -u
OUT=ci-out; mkdir -p "$OUT"
# the 2-core CI emulator's System UI is slow: no "isn't responding" dialogs over the app (they are not the app's)
adb shell settings put global hide_error_dialogs 1 || true
adb shell am broadcast -a android.intent.action.CLOSE_SYSTEM_DIALOGS > /dev/null 2>&1 || true
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
./gradlew --no-daemon :app:connectedDebugAndroidTest -Pabis=x86_64 -PskipUiGolden -Pandroid.testInstrumentationRunnerArguments.notClass=com.solidsketchapp.steel.C04RecordingTest,com.solidsketchapp.steel.C14RecordingTest,com.solidsketchapp.steel.C02C09RecordingTest,com.solidsketchapp.steel.C13RecordingTest,com.solidsketchapp.steel.C20RecordingTest,com.solidsketchapp.steel.C05RecordingTest,com.solidsketchapp.steel.C06RecordingTest,com.solidsketchapp.steel.C07RecordingTest,com.solidsketchapp.steel.C01RecordingTest,com.solidsketchapp.steel.C18RecordingTest,com.solidsketchapp.steel.C19RecordingTest,com.solidsketchapp.steel.C03RecordingTest,com.solidsketchapp.steel.C17RecordingTest,com.solidsketchapp.steel.X02RecordingTest,com.solidsketchapp.steel.C08RecordingTest,com.solidsketchapp.steel.T144V1RecordingTest,com.solidsketchapp.steel.T144V2RecordingTest --stacktrace > "$OUT/connected.log" 2>&1
RC=$?
echo "$(( $(date +%s) - START ))" > "$OUT/connected_seconds.txt"
echo "$RC" > "$OUT/connected_rc.txt"

touch "$OUT/.stop"; adb shell pkill -INT screenrecord || true; sleep 4; kill $REC 2>/dev/null || true; wait $REC 2>/dev/null || true
for f in $(adb shell ls /sdcard/ | tr -d '\r' | grep '^ci_rec_.*\.mp4$'); do adb pull "/sdcard/$f" "$OUT/$f" || true; done

# Parity video (docs/steel/PARITY.md, standing rule): the connection's own recording, in a second pass with no other
# screenrecord running (C04RecordingTest starts and stops its own). Pass 1's screenshots and reports are kept.
A=app/build/outputs/connected_android_test_additional_output; R1="$OUT/pass1"; mkdir -p "$R1"
cp -r "$A" "$R1/additional" 2>/dev/null || true; cp -r app/build/reports/androidTests "$R1/reports" 2>/dev/null || true; cp -r app/build/outputs/androidTest-results "$R1/results" 2>/dev/null || true
# The connection being delivered now (C04 and C14 recordings were delivered with their own runs; set REC_CLASSES to run them again)
REC_CLASSES="${REC_CLASSES:-T144V1RecordingTest T144V2RecordingTest C02C09RecordingTest C13RecordingTest}"
for CLS in $REC_CLASSES; do
  adb shell rm -f /sdcard/c02_rec.mp4 /sdcard/c09_rec.mp4 /sdcard/c04_rec.mp4 /sdcard/c14_rec.mp4 /sdcard/c13_rec.mp4 /sdcard/c20_rec.mp4 /sdcard/c05_rec.mp4 /sdcard/c06_rec.mp4 /sdcard/c07_rec.mp4 /sdcard/c01_rec.mp4 /sdcard/c18_rec.mp4 /sdcard/c19_rec.mp4 /sdcard/c03_rec.mp4 /sdcard/c17_rec.mp4 /sdcard/x02_rec.mp4 /sdcard/c08_rec.mp4 /sdcard/t144v1_rec.mp4 /sdcard/t144v2_rec.mp4 || true
  ./gradlew --no-daemon :app:connectedDebugAndroidTest -Pabis=x86_64 -PskipUiGolden -Pandroid.testInstrumentationRunnerArguments.class=com.solidsketchapp.steel.$CLS --stacktrace > "$OUT/${CLS}.log" 2>&1
  echo $? > "$OUT/${CLS}_rc.txt"
  for v in c02 c09 c04 c14 c13 c20 c05 c06 c07 c01 c18 c19 c03 c17 x02 c08 t144v1 t144v2; do adb pull /sdcard/${v}_rec.mp4 "$OUT/$(echo $v | tr a-z A-Z)_recording.mp4" 2>/dev/null || true; done
  mkdir -p "$R1/${CLS}_additional"; cp -r "$A/." "$R1/${CLS}_additional/" 2>/dev/null || true
done
for CLS in $REC_CLASSES; do cp -rn "$R1/${CLS}_additional/." "$A/" 2>/dev/null || true; done
mkdir -p "$A"; cp -rn "$R1/additional/." "$A/" 2>/dev/null || true
rm -rf app/build/reports/androidTests app/build/outputs/androidTest-results
cp -r "$R1/reports" app/build/reports/androidTests 2>/dev/null || true; cp -r "$R1/results" app/build/outputs/androidTest-results 2>/dev/null || true
for CLS in $REC_CLASSES; do tail -n 30 "$OUT/${CLS}.log"; done
adb logcat -d > "$OUT/logcat.txt" || true
tail -n 60 "$OUT/connected.log"
exit 0   # the result is read from connected_rc.txt (the summary step fails the job)

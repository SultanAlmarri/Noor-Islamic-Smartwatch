#!/usr/bin/env bash
# The home sky and the Qibla accuracy box on a real Android system (an emulator in CI): the GPU shaders are compiled by
# Android itself and drawn by its renderer, which the JVM tests cannot do.
#
#   tools/devicetest/home_sky.sh <debug apk> <output dir>
#
# - Sets the device clock (UTC) to moments through one day in Dubai (dunes) and Istanbul (hills) and photographs the
#   home screen after the opening has settled.
# - Records the opening, and a drag of the Sun through the day.
# - Opens the Qibla screen and changes the emulator's magnetometer between a normal field and one 90 % too strong
#   (as near metal), photographing the accuracy box straight after each change.
# - Saves the log lines of the sky renderer (it says whether the GPU shaders were built).
#
# The emulator draws with SwiftShader (a software GPU): pictures are faithful, speed is not that of a phone.
set -u
APK="$1"; OUT="$2"
PKG=com.noor.smartwatch
ACT=$PKG/com.noor.app.MainActivity
HOOK=$PKG/com.noor.app.debug.TestHookReceiver
mkdir -p "$OUT"
log() { echo "[home_sky] $*" | tee -a "$OUT/steps.txt"; }

adb wait-for-device
adb root >/dev/null 2>&1; sleep 3; adb wait-for-device
adb shell settings put global auto_time 0
adb shell settings put global auto_time_zone 0
adb shell settings put system screen_off_timeout 1800000
adb shell svc power stayon true
adb shell wm size | tee -a "$OUT/steps.txt"
adb shell wm density | tee -a "$OUT/steps.txt"
adb install -r -g "$APK" | tail -1
adb logcat -c

city() { adb shell am broadcast -a com.noor.smartwatch.debug.CITY --es city "$1" -n $HOOK >/dev/null; sleep 2; }
# setclock MMDDhhmmYYYY.ss in UTC
setclock() { adb shell "date -u $1" >/dev/null; }
launch() { adb shell am force-stop $PKG; sleep 1; adb shell am start -W -n $ACT >/dev/null; }
shot() { adb exec-out screencap -p > "$OUT/$1.png"; log "shot $1"; }

# Dubai is UTC+4, Istanbul UTC+3. 7 November 2026.
DXB="0000:night 0115:fajr 0200:dawn 0228:sunrise 0315:morning 0800:noon 1130:asr 1315:golden 1336:sunset 1352:dusk 1420:isha 1800:late"
IST="0215:fajr 0335:dawn 0405:sunrise 0600:morning 0930:noon 1230:asr 1405:golden 1428:sunset 1445:dusk 1530:isha 2000:night"

run_city() {
  local id=$1 list=$2
  city "$id"
  for item in $list; do
    local hhmm=${item%%:*} name=${item##*:}
    setclock "1107${hhmm}2026.00"
    launch
    sleep 9
    shot "home_${id}_${hhmm}_${name}"
  done
}

run_city dxb "$DXB"
run_city ist "$IST"

# The opening, recorded: a fresh launch an hour after the last one (the full opening plays after half an hour).
city dxb
setclock "110712302026.00"; launch; sleep 6
setclock "110713402026.00"
adb shell am force-stop $PKG; sleep 1
adb shell screenrecord --time-limit 7 --bit-rate 8000000 /sdcard/opening.mp4 &
REC=$!; sleep 1
adb shell am start -n $ACT >/dev/null
wait $REC; sleep 1
adb pull /sdcard/opening.mp4 "$OUT/opening_dxb_1740.mp4" >/dev/null && log "recorded opening"

# Dragging the Sun across the day, slowly, then letting go.
SIZE=$(adb shell wm size | sed -n 's/.*: \([0-9]*\)x\([0-9]*\).*/\1 \2/p' | tail -1)
W=${SIZE% *}; H=${SIZE#* }
Y=$((H * 22 / 100))
adb shell screenrecord --time-limit 12 --bit-rate 8000000 /sdcard/drag.mp4 &
REC=$!; sleep 1
adb shell input swipe $((W * 3 / 100)) $Y $((W * 97 / 100)) $Y 9000
wait $REC; sleep 1
adb pull /sdcard/drag.mp4 "$OUT/drag_dxb.mp4" >/dev/null && log "recorded drag"

# Frame timing while dragging (SwiftShader: indicative only).
adb shell dumpsys gfxinfo $PKG reset >/dev/null
adb shell input swipe $((W * 5 / 100)) $Y $((W * 95 / 100)) $Y 4000
adb shell dumpsys gfxinfo $PKG > "$OUT/gfxinfo_drag.txt"

# Qibla: open the tab, lay the phone flat, then change the magnetic field.
adb shell uiautomator dump /sdcard/ui.xml >/dev/null; adb pull /sdcard/ui.xml "$OUT/ui.xml" >/dev/null
BOUNDS=$(python3 - "$OUT/ui.xml" <<'PY'
import re, sys
x = open(sys.argv[1], encoding='utf-8').read()
for m in re.finditer(r'<node[^>]*>', x):
    n = m.group(0)
    if re.search(r'(text|content-desc)="Qibla"', n):
        b = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', n)
        if b:
            x1, y1, x2, y2 = map(int, b.groups()); print((x1 + x2) // 2, (y1 + y2) // 2); break
PY
)
log "qibla tab at: $BOUNDS"
if [ -n "$BOUNDS" ]; then
  adb shell input tap $BOUNDS; sleep 3
  adb emu sensor set acceleration 0:0:9.81
  # About 45 µT, as the World Magnetic Model expects around Dubai.
  adb emu sensor set magnetic-field 2:28:-35; sleep 3
  shot qibla_normal_field
  # Metal nearby: nearly twice the field.
  adb emu sensor set magnetic-field 4:52:-66; sleep 0.4
  shot qibla_strong_field_after_0_4s
  adb emu sensor set magnetic-field 2:28:-35; sleep 0.4
  shot qibla_normal_again_after_0_4s
  adb emu sensor set magnetic-field 3:33:-41; sleep 0.4
  shot qibla_slightly_off_after_0_4s
fi

adb logcat -d | grep -E "NoorSky|RuntimeShader|AGSL|SkSL|AndroidRuntime|FATAL" > "$OUT/sky_log.txt" || true
log "done"

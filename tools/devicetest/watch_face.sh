#!/bin/bash
# The Noor watch face on a Wear OS 6 emulator (0.9.8), end to end, as on a watch:
#   install the watch app -> set it up with a city -> open its face screen (the phone's "Show on my watch" opens the
#   same) -> the face is added and made active -> photograph the watch face -> switch designs and colours the way the
#   phone's editor does (in place) -> photograph each -> the activation is asked for at most once.
# usage: watch_face.sh <wear-debug.apk> <out dir>
set -u
APK=$1; OUT=$2; mkdir -p "$OUT"
PKG=com.noor.smartwatch
HOOK=$PKG/com.noor.wear.debug.WatchTestHook
log() { echo "[$(date -u +%T)] $*" | tee -a "$OUT/steps.txt"; }
adb() { timeout 120 "$(type -P adb)" "$@"; }
shot() { adb exec-out screencap -p > "$OUT/$1.png"; log "shot $1 ($(stat -c %s "$OUT/$1.png") bytes)"; }
hook() { adb shell am broadcast -a "com.noor.smartwatch.debug.$1" "${@:2}" -n $HOOK >/dev/null; }
state() { hook FACE_STATE; sleep 2; adb logcat -d -s NoorWatchTest:I | tail -1 | tee -a "$OUT/steps.txt"; }
home() { adb shell input keyevent KEYCODE_HOME; sleep 1; adb shell input keyevent KEYCODE_WAKEUP; sleep "${1:-4}"; }

timeout 1500 "$(type -P adb)" logcat -v time > "$OUT/logcat.txt" 2>&1 &
adb shell settings put system screen_off_timeout 1800000 >/dev/null 2>&1
adb shell svc power stayon true >/dev/null 2>&1
adb shell getprop ro.build.version.release | sed 's/^/Android /' | tee -a "$OUT/steps.txt"
home 6; shot 00_after_boot

log "install"
adb install -r -g "$APK" 2>&1 | tail -1 | tee -a "$OUT/steps.txt"
sleep 5
adb shell pm list packages | grep -i watchface | tee -a "$OUT/steps.txt"
state

log "set up the watch with Dubai"
hook WATCH_CITY --es city dxb; sleep 4

log "open the face screen (noor://face/activate)"
adb shell am start -W -a android.intent.action.VIEW -d noor://face/activate $PKG >/dev/null; sleep 12
shot 01_face_screen
state
# If Wear OS asks to confirm the change of watch face, accept it.
adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1; adb pull /sdcard/ui.xml "$OUT/ui_face.xml" >/dev/null 2>&1
xy=$(python3 - "$OUT/ui_face.xml" <<'PY'
import re, sys
try: x = open(sys.argv[1], encoding='utf-8').read()
except OSError: sys.exit()
for m in re.finditer(r'<node[^>]*>', x):
    n = m.group(0)
    if re.search(r'text="(Allow|OK|Set|Yes|Use)"', n, re.I):
        b = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', n)
        x1, y1, x2, y2 = map(int, b.groups()); print((x1 + x2) // 2, (y1 + y2) // 2); break
PY
)
[ -n "$xy" ] && { log "confirm dialog at $xy"; adb shell input tap $xy; sleep 6; shot 02_after_confirm; state; }

home 8; shot 10_face_horizon_gold

for v in dial_gold minimal_gold horizon_emerald dial_rose minimal_turquoise horizon_silver original horizon_gold; do
  log "face $v"
  hook FACE --es face $v; sleep 10
  home 8; shot "20_face_$v"
done
state

# The activation is asked for once: opening the face screen again must not ask again, nor show an error.
log "open the face screen again"
adb shell am start -W -a android.intent.action.VIEW -d noor://face/activate $PKG >/dev/null; sleep 8
shot 30_face_screen_again
adb logcat -d | grep -iE "NoorFacePush|NoorWatchTest|watchface|WatchFacePush" | grep -v "^--" > "$OUT/face_log.txt" || true
log "done"

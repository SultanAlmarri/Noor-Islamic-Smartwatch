#!/bin/bash
# 0.9.8 on a real Android system (an emulator in CI), as Android draws it:
#   tools/devicetest/phone_098.sh <debug apk> <output dir>
# - The phone's language set to Arabic (Arabic-Indic digits in the status bar), Noor in English with Western digits:
#   the persistent notification's countdown must use Noor's digits (the owner's report), collapsed and expanded, with
#   "Next prayer" said once when expanded. Then Noor in Arabic with Arabic-Indic digits on an English phone.
# - Noor in Arabic: the Settings title (its last letter used to drop to a second line), Home scenery.
# The emulator draws with a software GPU: pictures are faithful, speed is not a phone's.
set -u
APK="$1"; OUT="$2"
PKG=com.noor.smartwatch
ACT=$PKG/com.noor.app.MainActivity
HOOK=$PKG/com.noor.app.debug.TestHookReceiver
mkdir -p "$OUT"
log() { echo "[phone_098 $(date -u +%H:%M:%S)] $*" | tee -a "$OUT/steps.txt"; }
adb() { timeout 90 "$(type -P adb)" "$@"; }
ready() {
  adb wait-for-device
  for _ in $(seq 1 150); do
    [ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = 1 ] && adb shell pm path android >/dev/null 2>&1 && return 0
    sleep 2
  done
  return 1
}
no_anr() {
  adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1; adb pull /sdcard/ui.xml "$OUT/ui.xml" >/dev/null 2>&1
  if grep -q "isn't responding\|لا يستجيب" "$OUT/ui.xml" 2>/dev/null; then
    local xy; xy=$(python3 - "$OUT/ui.xml" <<'PY'
import re, sys
x = open(sys.argv[1], encoding='utf-8').read()
for m in re.finditer(r'<node[^>]*>', x):
    n = m.group(0)
    if re.search(r'text="(Wait|Close app|انتظار|إغلاق التطبيق)"', n):
        b = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', n)
        x1, y1, x2, y2 = map(int, b.groups()); print((x1 + x2) // 2, (y1 + y2) // 2); break
PY
)
    [ -n "$xy" ] && adb shell input tap $xy && log "closed an 'isn't responding' dialog"; sleep 1
  fi
}
shot() { no_anr; adb exec-out screencap -p > "$OUT/$1.png"; log "shot $1 ($(stat -c %s "$OUT/$1.png") bytes)"; }
# The notification's text as Android holds it (uiautomator), for the record.
shade_text() { adb shell rm -f /sdcard/ui.xml; adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1; adb pull /sdcard/ui.xml "$OUT/$1.xml" >/dev/null 2>&1
  python3 - "$OUT/$1.xml" <<'PY' | tee -a "$OUT/steps.txt"
import re, sys
x = open(sys.argv[1], encoding='utf-8').read()
texts = [t for t in re.findall(r' text="([^"]+)"', x) if t.strip()]
print("  shade:", " | ".join(texts[:40]))
PY
}
system_locale() {
  # As Settings does it, without restarting Android (a restart loses the emulator in CI).
  adb shell am broadcast -a com.noor.smartwatch.debug.SYSTEM_LOCALE --es locale "$1" -n $HOOK >/dev/null; sleep 6
  log "system language now: $(adb shell getprop persist.sys.locale | tr -d '\r') / $(adb shell cmd activity get-config 2>/dev/null | grep -o 'config: [^ ]* [^ ]*' | head -1) / $(adb logcat -d -s NoorE2E:I | grep SYSTEM_LOCALE | tail -1)"
}
notification() {
  adb shell am force-stop $PKG; sleep 1; adb shell am start -W -n $ACT >/dev/null; sleep 6
  adb shell am broadcast -a com.noor.smartwatch.debug.STATUS -n $HOOK >/dev/null; sleep 4
  adb shell input keyevent KEYCODE_HOME; sleep 2
  adb shell cmd statusbar expand-notifications; sleep 4
  shot "$1"; shade_text "$1"
  adb shell cmd statusbar collapse; sleep 1
}

log "start"; ready || { log "device not ready"; exit 1; }
adb shell settings put system screen_off_timeout 1800000
adb shell svc power stayon true
adb install -r -g "$APK" | tail -1
adb shell pm grant $PKG android.permission.POST_NOTIFICATIONS 2>/dev/null
adb shell pm grant $PKG android.permission.CHANGE_CONFIGURATION 2>&1 | tee -a "$OUT/steps.txt"
adb shell appops set $PKG WRITE_SETTINGS allow 2>&1 | tee -a "$OUT/steps.txt"
adb shell settings put global hidden_api_policy 1
timeout 3000 "$(type -P adb)" logcat -v time > "$OUT/logcat.txt" 2>&1 &
adb shell am broadcast -a com.noor.smartwatch.debug.CITY --es city dxb -n $HOOK >/dev/null; sleep 2

# ---- Phone in Arabic (Saudi Arabia: Arabic-Indic digits), Noor in English with Western digits.
system_locale ar-SA
adb shell am broadcast -a com.noor.smartwatch.debug.LANG --es lang en --es numerals western -n $HOOK >/dev/null; sleep 2
notification 01_notification_phone_ar_noor_en

# ---- Noor in Arabic with Arabic-Indic digits, the phone in English.
system_locale en-US
adb shell am broadcast -a com.noor.smartwatch.debug.LANG --es lang ar --es numerals arabic -n $HOOK >/dev/null; sleep 2
notification 02_notification_phone_en_noor_ar_indic

# ---- Noor in Arabic: Settings (the title on one line) and Home scenery.
adb shell am force-stop $PKG; sleep 1; adb shell am start -W -n $ACT --es noor.route settings >/dev/null; sleep 6
shot 03_settings_ar
adb shell am force-stop $PKG; sleep 1; adb shell am start -W -n $ACT --es noor.route scenery >/dev/null; sleep 6
shot 04_scenery_ar
adb shell am broadcast -a com.noor.smartwatch.debug.LANG --es lang en --es numerals western -n $HOOK >/dev/null; sleep 2
adb shell am force-stop $PKG; sleep 1; adb shell am start -W -n $ACT --es noor.route scenery >/dev/null; sleep 6
shot 05_scenery_en
log "done"

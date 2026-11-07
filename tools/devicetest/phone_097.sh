#!/usr/bin/env bash
# 0.9.7 on a real Android system (an emulator in CI): pictures and recordings of what changed, as Android draws them.
#
#   tools/devicetest/phone_097.sh <debug apk> <output dir>
#
# - The countdown: the device clock is set to 20 s before the next prayer; the home screen and the notification shade
#   (persistent notification, expanded) are photographed with "−", then again a few seconds after the prayer with "+".
# - Choosing a location: the new screen, the satellite map (real Esri imagery over the network), a district found
#   through the map's search, the confirmation with today's times there.
# - The Qibla accuracy box with the emulator's magnetic field held right at the Good/Fair limit (15 % off what the
#   World Magnetic Model expects) with noise, recorded: the label must not flicker.
# - The Adhan's full-screen alert over the lock screen (a test alert with the screen off).
# The emulator draws with a software GPU: pictures are faithful, speed is not a phone's.
set -u
APK="$1"; OUT="$2"
PKG=com.noor.smartwatch
ACT=$PKG/com.noor.app.MainActivity
HOOK=$PKG/com.noor.app.debug.TestHookReceiver
mkdir -p "$OUT"
log() { echo "[phone_097 $(date -u +%H:%M:%S)] $*" | tee -a "$OUT/steps.txt"; }
adb() { timeout 90 "$(type -P adb)" "$@"; }
shot() { no_anr; adb exec-out screencap -p > "$OUT/$1.png"; log "shot $1 ($(stat -c %s "$OUT/$1.png") bytes)"; }
ready() {
  adb wait-for-device
  for _ in $(seq 1 150); do
    [ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = 1 ] && adb shell pm path android >/dev/null 2>&1 && return 0
    sleep 2
  done
  return 1
}
# The emulator's own launcher sometimes stops answering under the software GPU; its "isn't responding" dialog would
# cover Noor. Background ANR dialogs are turned off, and any that still shows is closed.
no_anr() {
  adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1; adb pull /sdcard/ui.xml "$OUT/ui.xml" >/dev/null 2>&1
  if grep -q "isn't responding" "$OUT/ui.xml" 2>/dev/null; then
    local xy; xy=$(python3 - "$OUT/ui.xml" <<'PY'
import re, sys
x = open(sys.argv[1], encoding='utf-8').read()
for m in re.finditer(r'<node[^>]*>', x):
    n = m.group(0)
    if re.search(r'text="(Wait|Close app)"', n):
        b = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', n)
        x1, y1, x2, y2 = map(int, b.groups()); print((x1 + x2) // 2, (y1 + y2) // 2); break
PY
)
    [ -n "$xy" ] && adb shell input tap $xy && log "closed an 'isn't responding' dialog"; sleep 1
  fi
}
# Taps the centre of the first element whose text or description contains $1 (uiautomator).
tap_text() {
  no_anr
  for _ in 1 2 3; do
    rm -f "$OUT/ui.xml"
    adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1; adb pull /sdcard/ui.xml "$OUT/ui.xml" >/dev/null 2>&1
    grep -q "$1" "$OUT/ui.xml" 2>/dev/null && break; sleep 2
  done
  local xy; xy=$(python3 - "$OUT/ui.xml" "$1" <<'PY'
import re, sys
x = open(sys.argv[1], encoding='utf-8').read(); want = sys.argv[2]
for m in re.finditer(r'<node[^>]*>', x):
    n = m.group(0)
    t = re.search(r' text="([^"]*)"', n); d = re.search(r'content-desc="([^"]*)"', n)
    if (t and want in t.group(1)) or (d and want in d.group(1)):
        b = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', n)
        if b:
            x1, y1, x2, y2 = map(int, b.groups()); print((x1 + x2) // 2, (y1 + y2) // 2); break
PY
)
  if [ -n "$xy" ]; then adb shell input tap $xy; log "tapped '$1' at $xy"; return 0; fi
  log "not found: '$1'"; return 1
}

log "start"; ready || { log "device not ready"; exit 1; }
adb root >/dev/null 2>&1; sleep 3; ready
adb shell settings put global auto_time 0
adb shell settings put system screen_off_timeout 1800000
adb shell svc power stayon true
adb shell settings put secure anr_show_background 0
adb shell settings put global anr_show_background 0
adb install -r -g "$APK" | tail -1
adb shell pm grant $PKG android.permission.POST_NOTIFICATIONS 2>/dev/null
adb shell appops set $PKG SCHEDULE_EXACT_ALARM allow 2>/dev/null
timeout 3000 "$(type -P adb)" logcat -v time > "$OUT/logcat.txt" 2>&1 &
for k in animator_duration_scale transition_animation_scale window_animation_scale; do adb shell settings put global $k 1; done
adb shell am broadcast -a com.noor.smartwatch.debug.CITY --es city dxb -n $HOOK >/dev/null; sleep 2

# ---- The countdown, before and after a prayer, on the home screen and in the notification.
adb logcat -c
adb shell am broadcast -a com.noor.smartwatch.debug.STATUS -n $HOOK >/dev/null
NEXT=""
for _ in $(seq 1 20); do NEXT=$(adb logcat -d -s NoorE2E:I | sed -n 's/.*STATUS done next=\([0-9]*\).*/\1/p' | tail -1); [ -n "$NEXT" ] && break; sleep 1; done
log "next prayer at $NEXT ($(adb logcat -d -s NoorE2E:I | grep -o 'prayer=[A-Z]*' | tail -1))"
if [ -n "$NEXT" ]; then
  t=$(( NEXT / 1000 - 25 ))
  adb shell "date -u $(date -u -d @$t +%m%d%H%M%Y.%S)" >/dev/null
  adb shell am force-stop $PKG; sleep 1; adb shell am start -W -n $ACT >/dev/null; sleep 6
  shot countdown_home_minus
  adb shell cmd statusbar expand-notifications; sleep 3
  shot countdown_notification_minus
  adb shell cmd statusbar collapse; sleep 1
  sleep 22
  shot countdown_home_plus
  adb shell cmd statusbar expand-notifications; sleep 3
  shot countdown_notification_plus
  adb shell cmd statusbar collapse
  # Back to the real time.
  adb shell "date -u $(date -u +%m%d%H%M%Y.%S)" >/dev/null
fi

# ---- Choosing a location.
# (The animated home screen never settles, so uiautomator cannot read it: the debug build opens the page directly.)
adb shell am force-stop $PKG; sleep 1; adb shell am start -W -n $ACT --es noor.route cities >/dev/null; sleep 8
shot location_home
tap_text "Browse by country"; sleep 4; shot location_countries
adb shell input keyevent KEYCODE_BACK; sleep 2
tap_text "Choose on the map"; sleep 20
shot location_map_satellite_dubai
tap_text "Search the map"; sleep 1
adb shell input text "Dubai%sMarina"; sleep 4
shot location_map_search
tap_text "District, Dubai"; sleep 25
shot location_map_marina
tap_text "Map"; sleep 15; shot location_map_streets
tap_text "Satellite"; sleep 10
tap_text "Use this location"; sleep 15
shot location_confirm_marina
adb shell input keyevent KEYCODE_BACK; sleep 2
adb shell input keyevent KEYCODE_BACK; sleep 2

# ---- Qibla: the field held at the Good/Fair limit, with noise, recorded.
adb shell am force-stop $PKG; sleep 1; adb shell am start -W -n $ACT --es noor.route qibla >/dev/null; sleep 6
adb emu sensor set acceleration 0:0:9.81
# The emulator's normal field (2:28:-35) is 44.9 µT. Readings alternate between 12.6 % and 15.8 % stronger (50.5 and
# 51.9 µT): judged one by one they cross the Good/Fair limit (15 %) every 0.45 s; the label must stay put.
adb emu sensor set magnetic-field 2:28:-35; sleep 3
shot qibla_normal_field
adb shell screenrecord --time-limit 14 --bit-rate 8000000 /sdcard/qibla.mp4 &
REC=$!; sleep 1
for i in $(seq 1 26); do
  if [ $((i % 2)) = 0 ]; then f="3:39:-32"; else f="3:40:-33"; fi
  adb emu sensor set magnetic-field $f >/dev/null; sleep 0.45
  [ $i = 13 ] && shot qibla_at_the_limit_mid
done
shot qibla_at_the_limit
wait $REC; sleep 1
adb pull /sdcard/qibla.mp4 "$OUT/qibla_at_the_limit.mp4" >/dev/null && log "recorded qibla"

# ---- The Adhan's full-screen alert (a 20-second test alert, screen off, exact alarms allowed as on the owner's phone).
for _ in 1 2 3 4 5; do
  adb shell appops set $PKG SCHEDULE_EXACT_ALARM allow
  adb shell appops get $PKG SCHEDULE_EXACT_ALARM | grep -q allow && break; sleep 2
done
adb shell appops set $PKG USE_FULL_SCREEN_INTENT allow 2>/dev/null
adb shell am force-stop $PKG; sleep 1; adb shell am start -W -n $ACT >/dev/null; sleep 4
adb logcat -c
adb shell am broadcast -a com.noor.smartwatch.debug.SETUP --ei seconds 20 --ei max 30 --es prayer MAGHRIB -n $HOOK >/dev/null; sleep 3
adb logcat -d -s NoorE2E:I | grep SETUP | tee -a "$OUT/steps.txt"
adb shell input keyevent KEYCODE_SLEEP
for _ in $(seq 1 40); do adb shell dumpsys activity activities | grep -q "AdhanAlertActivity" && break; sleep 1; done
adb shell dumpsys activity activities | grep -m3 "AdhanAlertActivity" | tee -a "$OUT/steps.txt"
sleep 3
shot adhan_full_screen
adb shell dumpsys audio | grep -E "USAGE_ALARM" | grep -m2 "state:started" | tee -a "$OUT/steps.txt"
sleep 30
adb shell input keyevent KEYCODE_WAKEUP; sleep 2

adb logcat -d | grep -E "AndroidRuntime|FATAL|NoorE2E" > "$OUT/app_log.txt" || true
log "done"

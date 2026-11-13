#!/usr/bin/env bash
# The real sky (1.0) on a real Android system (an emulator in CI): the OpenGL ES 3 renderer, its shaders compiled by
# Android's own driver, photographed for each scenery at several times of day.
#
#   tools/devicetest/real_sky.sh <debug apk> <output dir> [graphics tier, default LOW]
#
# The emulator draws with SwiftShader (a software GPU): the pictures are what the renderer draws, the speed is not that
# of a phone (a picture that a phone draws in milliseconds takes it seconds), so the lowest tier is used by default and
# each picture waits until the renderer says it has been shown. The renderer's log lines are saved (real_sky_log.txt).
set -u
APK="$1"; OUT="$2"; TIER="${3:-LOW}"
PKG=com.noor.smartwatch
ACT=$PKG/com.noor.app.MainActivity
HOOK=$PKG/com.noor.app.debug.TestHookReceiver
mkdir -p "$OUT"
log() { echo "[real_sky $(date -u +%H:%M:%S)] $*" | tee -a "$OUT/steps.txt"; }
adb() { timeout 60 "$(type -P adb)" "$@"; }

log "start"
adb wait-for-device
adb root >/dev/null 2>&1; sleep 3; adb wait-for-device
adb shell settings put global auto_time 0
adb shell settings put global auto_time_zone 0
adb shell settings put system screen_off_timeout 1800000
adb shell svc power stayon true
adb shell wm size | tee -a "$OUT/steps.txt"
adb install -r -g "$APK" | tail -1
adb logcat -c
timeout 3000 "$(type -P adb)" logcat -v time > "$OUT/logcat.txt" 2>&1 &
for k in animator_duration_scale transition_animation_scale window_animation_scale; do adb shell settings put global $k 0; done
adb shell settings put secure anr_show_background 0 >/dev/null 2>&1

setclock() { adb shell "date -u $1" >/dev/null; }
shot() { adb exec-out screencap -p > "$OUT/$1.png"; log "shot $1 ($(stat -c %s "$OUT/$1.png") bytes)"; }
# Waits (up to $1 seconds) until the renderer has shown a picture since the last launch.
shown() {
  local n=0
  while [ $n -lt "$1" ]; do
    if grep -q "Real sky shown" "$OUT/logcat.txt" 2>/dev/null && [ "$(grep -c 'Real sky shown' "$OUT/logcat.txt")" -gt "$SEEN" ]; then return 0; fi
    if grep -q "Real sky unavailable\|Real sky failed" "$OUT/logcat.txt" 2>/dev/null; then return 1; fi
    sleep 3; n=$((n + 3))
  done
  return 2
}

adb shell am broadcast -a com.noor.smartwatch.debug.CITY --es city dxb -n $HOOK >/dev/null; sleep 2

# Dubai is UTC+4. 14 November 2026: Fajr 05:14, sunrise 06:30, noon 12:06, Asr 15:11, sunset 17:35, Isha 18:52.
TIMES="0235:sunrise 0700:midday 1305:golden 1338:sunset 1356:dusk 1900:night"
SCENERIES="${SCENERIES:-DUNES RAINFOREST ARCTIC SEA SAVANNA CANYON}"
for sc in $SCENERIES; do
  adb shell am broadcast -a com.noor.smartwatch.debug.SKY --es scenery "$sc" --es graphics "$TIER" -n $HOOK >/dev/null; sleep 2
  for item in $TIMES; do
    hhmm=${item%%:*}; name=${item##*:}
    setclock "1114${hhmm}2026.00"
    SEEN=$(grep -c "Real sky shown" "$OUT/logcat.txt" 2>/dev/null || echo 0)
    adb shell am force-stop $PKG; sleep 1
    adb shell am start -W -n $ACT >/dev/null
    shown 420; r=$?
    sleep 6
    shot "real_${sc}_${hhmm}_${name}"
    [ $r -eq 1 ] && { log "the real sky is unavailable on this device"; break 2; }
    [ $r -eq 2 ] && log "no picture reported within the time for $sc $name"
    [ "$(adb get-state 2>/dev/null)" = "device" ] || { log "device gone"; break 2; }
  done
done

grep -E "NoorRealSky|AndroidRuntime|FATAL|libc    :|DEBUG   :" "$OUT/logcat.txt" | head -400 > "$OUT/real_sky_log.txt" 2>/dev/null
log "done"

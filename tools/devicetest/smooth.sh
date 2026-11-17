#!/usr/bin/env bash
# How smooth Minhad is, measured the same way every time on an Android emulator (0.18.2): the bench build (the release
# build exactly as the owner installs it, plus the test hooks, StrictMode and profileable; app/build.gradle.kts) goes
# through a fixed set of moves - cold starts, the home screen left alone, the sky dragged, the page scrolled, the tabs
# switched, the Adhan and Settings pages scrolled - and Android's own frame statistics are summarised for each
# (tools/devicetest/framestats.py): the main thread's time per frame (what Minhad's code decides), the render thread's
# and how many frames came late. StrictMode's log lists every disk access on the main thread.
#
#   tools/devicetest/smooth.sh <bench apk> <output dir> [rounds, default 2]
#
# An emulator draws with a software GPU (SwiftShader): its render-thread times and late frames are far worse than a
# phone's and say little. Compare the main-thread times between builds, run on the same kind of machine.
set -u
APK="$1"; OUT="$2"; ROUNDS="${3:-2}"
PKG=com.noor.smartwatch
ACT=$PKG/com.noor.app.MainActivity
HOOK=$PKG/com.noor.app.debug.TestHookReceiver
HERE="$(cd "$(dirname "$0")" && pwd)"
mkdir -p "$OUT"
log() { echo "[smooth $(date -u +%H:%M:%S)] $*" | tee -a "$OUT/steps.txt"; }
adb() { timeout 120 "$(type -P adb)" "$@"; }

log "start"
adb wait-for-device
adb root >/dev/null 2>&1; sleep 3; adb wait-for-device
adb shell settings put system screen_off_timeout 1800000
adb shell svc power stayon true
adb shell wm size 720x1600; adb shell wm density 280
# Animations at their normal speed: they are part of what is measured.
for k in animator_duration_scale transition_animation_scale window_animation_scale; do adb shell settings put global $k 1; done
adb install -r -g "$APK" | tail -1
adb logcat -c
timeout 3000 "$(type -P adb)" logcat -v time > "$OUT/logcat.txt" 2>&1 &
adb shell am broadcast -a com.noor.smartwatch.debug.CITY --es city dxb -n $HOOK >/dev/null; sleep 3
# A fixed moment (14 November 2026, 10:00 in Dubai: the morning sky, Dhuhr next) so every run draws the same thing.
adb shell "date -u 111406002026.00" >/dev/null
JSON="$OUT/results.jsonl"; : > "$JSON"

measure() {   # <name> then the moves as a function name
  local name="$1"; shift
  adb shell dumpsys gfxinfo $PKG reset >/dev/null
  "$@"
  sleep 2
  adb shell dumpsys gfxinfo $PKG framestats > "$OUT/gfx_$name.txt"
  python3 "$HERE/framestats.py" "$name" "$OUT/gfx_$name.txt" "$JSON" | tee -a "$OUT/summary.txt"
}

swipe() { adb shell input swipe "$@"; }
tap() { adb shell input tap "$1" "$2"; sleep "${3:-2.5}"; }
# The tab bar at 720x1600, density 280: Today, Qibla, Adhan, Settings.
TODAY="117 1484"; QIBLA="278 1484"; ADHAN="440 1484"; SETTINGS="600 1484"

idle() { sleep 10; }
drag_sky() { for i in 1 2 3; do swipe 120 560 620 560 1500; sleep 0.5; swipe 620 560 120 560 1500; sleep 0.5; done; }
scroll_home() { for i in 1 2; do swipe 360 1300 360 600 400; sleep 1.2; swipe 360 600 360 1300 400; sleep 1.2; done; }
tabs() { for i in 1 2; do tap $QIBLA; tap $ADHAN; tap $SETTINGS; tap $TODAY; done; }
scroll_page() { for i in 1 2; do swipe 360 1250 360 450 350; sleep 1.2; swipe 360 450 360 1250 350; sleep 1.2; done; }
adhan_page() { tap $ADHAN 3; scroll_page; }
settings_page() { tap $SETTINGS 3; scroll_page; tap $TODAY 3; }

for round in $(seq 1 "$ROUNDS"); do
  log "round $round"
  # Cold starts: the time to the first frame as Android reports it (TotalTime, ms).
  for i in 1 2 3; do
    adb shell am force-stop $PKG; sleep 2
    adb shell am start -W -n $ACT | grep -E "TotalTime|WaitTime" | tr '\n' ' ' | sed "s/^/cold start $round.$i: /" | tee -a "$OUT/summary.txt"; echo | tee -a "$OUT/summary.txt"
    sleep 6
  done
  measure "r${round}_home_idle" idle
  measure "r${round}_drag_sky" drag_sky
  measure "r${round}_scroll_home" scroll_home
  measure "r${round}_tabs" tabs
  measure "r${round}_adhan" adhan_page
  measure "r${round}_settings" settings_page
  [ "$(adb get-state 2>/dev/null)" = "device" ] || { log "device gone"; break; }
done

# Main-thread disk access and slow calls, as StrictMode logged them: the count, and the place each first happened.
grep -E "StrictMode" "$OUT/logcat.txt" > "$OUT/strictmode.txt" 2>/dev/null
grep -cE "StrictMode policy violation" "$OUT/strictmode.txt" | sed 's/^/StrictMode violations: /' | tee -a "$OUT/summary.txt"
grep -A12 "StrictMode policy violation" "$OUT/strictmode.txt" | grep -E "at com\.noor|policy violation" | sed 's/.*StrictMode[^:]*: //' \
  | awk '/policy violation/{v=$0; next} {print v " <- " $0}' | sed 's/~duration=[0-9]* ms//' | sort | uniq -c | sort -rn | head -40 > "$OUT/strictmode_places.txt"
adb exec-out screencap -p > "$OUT/last.png"
log "done"

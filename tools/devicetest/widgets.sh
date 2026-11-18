#!/usr/bin/env bash
# Minhad's home-screen widgets as a real Android system draws them (an emulator in CI: .github/workflows/widgets.yml).
#
#   tools/devicetest/widgets.sh <app debug apk> <widget host apk> <output dir>
#
# Robolectric's renders of the widgets have differed from the owner's phone more than once: a home screen inflates a
# widget in a restricted mode that ignores an app's fonts (docs/HANDOVER.md sections 16, 18, 36). Here the widgets are
# shown by a separate host app (tools/widgethost, package com.noor.widgethost) exactly as a launcher shows them: it binds
# real widget ids to Minhad's widget providers (allowed by `appwidget grantbind`, the documented developer path), gives
# each its size, and Android sends the providers their own updates (the app's real onUpdate → Widgets.refresh). Each page
# of widgets is photographed with screencap: what the system drew, through its own RemoteViews inflation.
#
# What it sets up: Dubai as a chosen place (manual), the time zone Asia/Dubai, a fixed clock for each picture; then for
# each "pass" the phone's language and Minhad's language and digits, for each system theme (light, dark) and each
# background (the system's wallpaper; a neutral grey), every page of the host.
#
# Settings (environment variables; defaults in brackets):
#   PASSES  system-locale:app-language:digits, space separated
#           [en-US:en:western ar-SA:en:western ar-SA:ar:arabic]
#           ar-SA writes Arabic-Indic digits in the system (as the owner's phone in Arabic does). The owner's phone is in
#           Arabic with Minhad in English and Western digits: the second pass.
#   THEMES  [light dark]          BGS  [wallpaper neutral]
#   TIMES   MMDDhhmm:name in UTC, year $YEAR [2026], space separated [11180700:day] (Dubai is UTC+4: 07:00 UTC is 11:00
#           in Dubai, an hour before Dhuhr; 11181640:night is 20:40, Isha past)
#   SPEC    which widgets at which sizes [auto: every widget the app declares, a few sizes each]; or pages of
#           Provider=WxH,WxH;Provider=WxH (dp), a size optionally with +key=value options (HostActivity.kt)
#   ON_BOUND  a shell command run for each widget the host binds, with $ID $PROVIDER $SIZE $OPTIONS set (for example a
#           debug broadcast that saves a widget's own options for that id); the host then waits HOLD ms [15000] before a
#           page counts as ready
#
# Output: <pass>_<theme>_<bg>_p<page>.png (the whole screen), .txt (the host's lines for it: what each widget received,
# where it is, every visible text with its size and colour), steps.txt, widgets_log.txt (the host's, the app's and the
# widget service's lines), logcat.txt. tools/devicetest/widget_crops.py cuts each widget out of the screens.
set -u
APK="$1"; HOSTAPK="$2"; OUT="$3"
PKG=com.noor.smartwatch
HOST=com.noor.widgethost
HACT=$HOST/.HostActivity
HOOK=$PKG/com.noor.app.debug.TestHookReceiver
PASSES="${PASSES:-en-US:en:western ar-SA:en:western ar-SA:ar:arabic}"
THEMES="${THEMES:-light dark}"
BGS="${BGS:-wallpaper neutral}"
TIMES="${TIMES:-11180700:day}"
YEAR="${YEAR:-2026}"
SPEC="${SPEC:-auto}"
ON_BOUND="${ON_BOUND:-}"
HOLD="${HOLD:-15000}"
mkdir -p "$OUT"
log() { echo "[widgets $(date -u +%H:%M:%S)] $*" | tee -a "$OUT/steps.txt"; }
# Every adb call is bounded, so one stuck command cannot use up the job's time.
adb() { timeout 90 "$(type -P adb)" "$@"; }
ready() {
  adb wait-for-device
  for _ in $(seq 1 150); do
    [ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = 1 ] && adb shell pm path android >/dev/null 2>&1 && return 0
    sleep 2
  done
  return 1
}
hook() { local a="$1"; shift; adb shell am broadcast -a "com.noor.smartwatch.debug.$a" "$@" -n $HOOK >/dev/null; }
PIDS=""
note_pid() { local p; p=$(adb shell pidof $PKG 2>/dev/null | tr -d '\r'); [ -n "$p" ] && case " $PIDS " in *" $p "*) ;; *) PIDS="$PIDS $p";; esac; }

log "start"; ready || { log "device not ready"; exit 1; }
adb root >/dev/null 2>&1; sleep 3; ready || { log "device not ready after root"; exit 1; }
adb shell settings put global auto_time 0
adb shell settings put global auto_time_zone 0
adb shell settings put system screen_off_timeout 1800000
adb shell svc power stayon true
adb shell input keyevent KEYCODE_WAKEUP; adb shell wm dismiss-keyguard >/dev/null 2>&1
# The time zone, as Settings sets it (the alarm service's shell command), else the property.
adb shell cmd alarm set-timezone Asia/Dubai >/dev/null 2>&1 || adb shell setprop persist.sys.timezone Asia/Dubai
log "time zone $(adb shell getprop persist.sys.timezone | tr -d '\r'); screen $(adb shell wm size | tr -d '\r' | tail -1), $(adb shell wm density | tr -d '\r' | tail -1); Android $(adb shell getprop ro.build.version.release | tr -d '\r') (SDK $(adb shell getprop ro.build.version.sdk | tr -d '\r'))"
adb install -r -g "$APK" | tail -1 | tee -a "$OUT/steps.txt"
adb install -r -g "$HOSTAPK" | tail -1 | tee -a "$OUT/steps.txt"
# The host may bind widgets (the developer path; a launcher holds this right from the system).
adb shell appwidget grantbind --package $HOST 2>&1 | tee -a "$OUT/steps.txt"
# For the phone's language without a restart (TestHookReceiver's SYSTEM_LOCALE, as in phone_098.sh).
adb shell pm grant $PKG android.permission.CHANGE_CONFIGURATION 2>&1 | tee -a "$OUT/steps.txt"
adb shell appops set $PKG WRITE_SETTINGS allow 2>&1 | tee -a "$OUT/steps.txt"
adb shell settings put global hidden_api_policy 1
for k in animator_duration_scale transition_animation_scale window_animation_scale; do adb shell settings put global $k 0; done
adb logcat -c
timeout 5000 "$(type -P adb)" logcat -v threadtime > "$OUT/logcat.txt" 2>&1 &

hook CITY --es city dxb; sleep 3
note_pid

# Waits (up to $2 s) for the host's line that ends the page of run $1; prints READY, TIMEOUT, NOPAGE or NONE.
wait_page() {
  local n=0 r
  while [ $n -lt "$2" ]; do
    r=$(grep -oE "NoorWidgetHost: $1 (READY|TIMEOUT|NOPAGE)" "$OUT/logcat.txt" 2>/dev/null | head -1 | awk '{print $3}')
    if [ -n "$r" ]; then
      # READY and TIMEOUT are followed by the description of every widget, which ends with DONE.
      [ "$r" = NOPAGE ] || for _ in $(seq 1 20); do grep -q "NoorWidgetHost: $1 DONE" "$OUT/logcat.txt" && break; sleep 1; done
      echo "$r"; return 0
    fi
    sleep 2; n=$((n + 2))
  done
  echo NONE
}
# Runs $ON_BOUND for each widget the host has bound in run $1.
on_bound() {
  [ -n "$ON_BOUND" ] || return 0
  sleep 4
  grep "NoorWidgetHost: $1 BOUND" "$OUT/logcat.txt" | while read -r line; do
    ID=$(echo "$line" | grep -oE 'id=[0-9]+' | head -1 | cut -d= -f2)
    PROVIDER=$(echo "$line" | grep -oE 'provider=[^ ]+' | cut -d= -f2)
    SIZE=$(echo "$line" | grep -oE 'size=[^ ]+' | cut -d= -f2)
    OPTIONS=$(echo "$line" | grep -oE 'options=[^ ]+' | cut -d= -f2-)
    export ID PROVIDER SIZE OPTIONS
    log "on bound: id=$ID $PROVIDER $SIZE $OPTIONS"
    eval "$ON_BOUND"
  done
}
shot() {
  local i
  for i in 1 2 3; do
    adb exec-out screencap -p > "$OUT/$1.png"
    [ "$(stat -c %s "$OUT/$1.png")" -gt 20000 ] && break
    adb shell input keyevent KEYCODE_WAKEUP >/dev/null 2>&1; sleep 5
  done
  log "shot $1 ($(stat -c %s "$OUT/$1.png") bytes)"
}

for t in $TIMES; do
  when=${t%%:*}; tname=${t##*:}
  for pass in $PASSES; do
    sys=$(echo "$pass" | cut -d: -f1); lang=$(echo "$pass" | cut -d: -f2); digits=$(echo "$pass" | cut -d: -f3)
    hook SYSTEM_LOCALE --es locale "$sys"; sleep 12
    hook LANG --es lang "$lang" --es numerals "$digits"; sleep 4
    log "pass: phone $sys ($(adb shell getprop persist.sys.locale | tr -d '\r')), Minhad $lang $digits; $(adb logcat -d -s NoorE2E:I | grep -E 'SYSTEM_LOCALE|LANG' | tail -2 | tr '\n' ' ')"
    for theme in $THEMES; do
      if [ "$theme" = dark ]; then adb shell cmd uimode night yes >/dev/null; else adb shell cmd uimode night no >/dev/null; fi
      sleep 3
      for bg in $BGS; do
        page=0
        while [ $page -lt 12 ]; do
          name="${tname}_phone-${sys}_app-${lang}-${digits}_${theme}_${bg}_p${page}"
          RUN="r$(date +%s%N | cut -c8-16)"
          adb shell "date -u ${when}${YEAR}.00" >/dev/null
          adb shell am force-stop $HOST; sleep 1
          extra=""; [ -n "$ON_BOUND" ] && extra="--el hold $HOLD"
          adb shell "am start -W -n $HACT --es run $RUN --es spec '$SPEC' --ei page $page --es bg $bg $extra" >/dev/null
          on_bound "$RUN"
          r=$(wait_page "$RUN" 160)
          note_pid
          grep "NoorWidgetHost: $RUN " "$OUT/logcat.txt" | sed -E 's/^.*NoorWidgetHost: //' > "$OUT/$name.txt"
          if [ "$r" = NOPAGE ]; then rm -f "$OUT/$name.txt"; break; fi
          sleep 2
          shot "$name"
          log "$name: $r; $(grep -c ' UPDATE ' "$OUT/$name.txt") updates, $(grep -c ' ERROR ' "$OUT/$name.txt") errors"
          if [ "$r" = NONE ]; then
            log "no answer from the host for $name (it may have crashed: widgets_log.txt); the next background"
            grep -E "AndroidRuntime|FATAL" "$OUT/logcat.txt" | tail -5 | tee -a "$OUT/steps.txt"
            break
          fi
          [ "$(adb get-state 2>/dev/null)" = "device" ] || { log "device gone"; break 5; }
          page=$((page + 1))
        done
      done
    done
  done
done
adb shell cmd uimode night no >/dev/null 2>&1
adb shell am force-stop $HOST

# The host's lines, the widget service's, the app's (by its process ids) and any crash.
awk -v pids="$PIDS" 'BEGIN { n = split(pids, a, " "); for (i = 1; i <= n; i++) keep[a[i]] = 1 }
  ($3 in keep) || /NoorWidgetHost|AppWidget|RemoteViews|AndroidRuntime|FATAL|NoorE2E/' "$OUT/logcat.txt" > "$OUT/widgets_log.txt" 2>/dev/null
log "app processes:$PIDS"
log "done"

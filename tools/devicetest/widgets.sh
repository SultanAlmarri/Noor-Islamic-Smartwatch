#!/usr/bin/env bash
# Noor's home-screen widgets on a real Android system (an emulator in CI), 0.9.12.
#
#   tools/devicetest/widgets.sh <debug apk> <output dir>
#
# The widgets are hosted as a launcher hosts them (the debug app's WidgetHostActivity: Android's widget service and
# its AppWidgetHostView, which load a widget's layout in the restricted mode where an app's own fonts are refused — what
# broke 0.9.11's countdown on phones and what the JVM tests cannot see). The phone is set to Arabic, as the owner's is.
#
# - Noor in English with Western digits on the Arabic phone: pictures second after second (the seconds must tick by
#   themselves, in Western digits), and across the start of a minute (the minutes must change then).
# - Noor in Arabic with Arabic-Indic digits, then with Western digits.
# - Logs: where each countdown is (NoorWidgetHost), how each update went (NoorWidgets: on the GPU or not, each minute).
# - analysis.txt: the seconds window changes from second to second, its minutes picture does not until the minute.
set -u
APK="$1"; OUT="$2"
PKG=com.noor.smartwatch
HOOK=$PKG/com.noor.app.debug.TestHookReceiver
HOSTACT=$PKG/com.noor.app.debug.WidgetHostActivity
mkdir -p "$OUT"
log() { echo "[widgets $(date -u +%H:%M:%S)] $*" | tee -a "$OUT/steps.txt"; }
adb() { timeout 60 "$(type -P adb)" "$@"; }

log "start"
adb wait-for-device
adb root >/dev/null 2>&1; sleep 3; adb wait-for-device
adb shell settings put system screen_off_timeout 1800000
adb shell svc power stayon true
adb shell input keyevent 82 >/dev/null 2>&1
for k in animator_duration_scale transition_animation_scale window_animation_scale; do adb shell settings put global $k 0; done
adb install -r -g "$APK" | tail -1
log "installed"
adb shell pm grant $PKG android.permission.CHANGE_CONFIGURATION 2>&1 | tee -a "$OUT/steps.txt"
adb shell settings put global hidden_api_policy 1
adb shell appops set $PKG SCHEDULE_EXACT_ALARM allow 2>&1 | tee -a "$OUT/steps.txt"
adb shell appwidget grantbind --package $PKG --user 0 2>&1 | tee -a "$OUT/steps.txt"
adb shell getprop ro.build.version.sdk | tee -a "$OUT/steps.txt"
adb logcat -c
timeout 1500 "$(type -P adb)" logcat -v time > "$OUT/logcat.txt" 2>&1 &

hook() { adb shell am broadcast -a "com.noor.smartwatch.debug.$1" "${@:2}" -n $HOOK >/dev/null; }
hook CITY --es city ist; sleep 2
hook LANG --es lang en --es numerals western; sleep 2
hook SYSTEM_LOCALE --es locale ar-SA; sleep 25
log "phone language: $(adb shell cmd activity get-config 2>/dev/null | grep -o 'config: [^ ]* [^ ]*' | head -1)"

adb shell am start -W -n $HOSTACT >/dev/null
log "widgets placed"
sleep 30

# Pictures: N in a row, about a second apart, each named with the phone's clock (seconds) when taken.
shots() {
  local name="$1" n="$2"
  for i in $(seq 1 "$n"); do
    local t; t=$(adb shell date +%s | tr -d '\r')
    adb exec-out screencap -p > "$OUT/${name}_${i}_t${t}.png"
    sleep 0.6
  done
}
# Until the phone's clock reaches second [s] of a minute.
until_second() {
  for _ in $(seq 1 80); do
    local s; s=$(adb shell date +%S | tr -d '\r')
    [ "$((10#$s))" -ge "$1" ] && [ "$((10#$s))" -le "$(( $1 + 1 ))" ] && return 0
    sleep 0.5
  done
}

shots en_western_on_arabic_phone 5
log "across the start of a minute"
until_second 55
shots minute 8

hook LANG --es lang ar --es numerals arabic; sleep 15
shots ar_arabic_digits 3
hook LANG --es lang ar --es numerals western; sleep 15
shots ar_western_digits 3
hook LANG --es lang en --es numerals western; sleep 15
shots en_western_again 2

adb logcat -d -s NoorWidgetHost:I NoorWidgets:I NoorSky:W NoorSky:I AndroidRuntime:E > "$OUT/widget_log.txt" 2>&1
log "done"
python3 tools/devicetest/widgets_check.py "$OUT" | tee "$OUT/analysis.txt"

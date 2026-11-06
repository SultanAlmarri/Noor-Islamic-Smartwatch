#!/usr/bin/env bash
# End-to-end Adhan test on a real Android system (an emulator in CI, or a phone over adb).
#
# For each scenario it installs the debug app, sets it up through the debug-only hook (Dubai, alerts on, the Adhan
# limited to 30 s), arms a real test alarm, then kills Noor's process, turns the screen off and (where the scenario
# allows) forces Doze. It passes only if Android itself reports the Adhan playing on the alarm stream from Noor's
# process (dumpsys audio), keeps playing, and Noor's own history records how long it played.
#
#   normal    exact alarms allowed, ringer normal          → the chosen Adhan must play ≥ 25 s
#   vibrate   exact alarms allowed, ringer on vibrate      → the chosen Adhan must still play ≥ 25 s (the 0.9 fix)
#   no-exact  "Alarms & reminders" denied                  → Android refuses Noor's player; the backup notification
#                                                            (a short Adhan played by Android) must be posted
#
# Usage: alert_e2e.sh <app-debug.apk> [normal|vibrate|no-exact|all]
# What this does not prove: that a person hears it (emulators have no speaker), or how a specific phone maker's battery
# manager behaves. Those need a real phone: see docs/ALERTS.md, "Testing on a phone".
set -u
APK="$1"; WHICH="${2:-all}"
# E2E_LOG=<file>: also write everything to that file (CI keeps it as an artifact).
if [ -n "${E2E_LOG:-}" ]; then exec > >(tee -a "$E2E_LOG") 2>&1; fi
PKG=com.noor.smartwatch
HOOK="$PKG/com.noor.app.debug.TestHookReceiver"
FAILURES=0

say() { echo "[alert-e2e:$S] $*"; }
records() {
  adb logcat -c
  adb shell am broadcast -a com.noor.smartwatch.debug.DUMP -n "$HOOK" >/dev/null
  for _ in $(seq 1 20); do adb logcat -d -s NoorE2E:I | grep -q "DUMP done" && break; sleep 1; done
  adb logcat -d -s NoorE2E:I | grep "RECORD" | sed 's/^.*RECORD /  /'
}
alarm_players() { adb shell dumpsys audio | grep -E "u/pid:$APP_UID/" | grep "state:started" | grep -c "USAGE_ALARM"; }

run() {
  S="$1"
  say "---- scenario $S ----"
  adb install -r -g "$APK" >/dev/null || { say "install failed"; return 1; }
  adb shell pm grant $PKG android.permission.POST_NOTIFICATIONS 2>/dev/null
  if [ "$S" = no-exact ]; then adb shell appops set $PKG SCHEDULE_EXACT_ALARM deny; else adb shell appops set $PKG SCHEDULE_EXACT_ALARM allow; fi
  adb shell cmd media_session volume --stream 4 --set 5 >/dev/null 2>&1 || adb shell media volume --stream 4 --set 5 >/dev/null 2>&1 || true
  adb shell input keyevent KEYCODE_WAKEUP; adb shell wm dismiss-keyguard 2>/dev/null
  APP_UID=$(adb shell cmd package list packages -U $PKG | tr -d '\r' | sed -n 's/.*uid:\([0-9]*\).*/\1/p')
  VIB=false; [ "$S" = vibrate ] && VIB=true
  adb logcat -c
  adb shell am broadcast -a com.noor.smartwatch.debug.SETUP -n "$HOOK" --ei seconds 45 --ei max 30 --ez vibrate $VIB >/dev/null
  for _ in $(seq 1 30); do adb logcat -d -s NoorE2E:I | grep -q "SETUP done" && break; sleep 1; done
  say "$(adb logcat -d -s NoorE2E:I | grep 'SETUP done' | sed 's/^.*SETUP/SETUP/')"
  # Noor in the background, its process killed, the screen off, the device in Doze: as at a real prayer time at night.
  adb shell input keyevent KEYCODE_HOME; sleep 3
  adb shell am kill $PKG; sleep 2
  [ -z "$(adb shell pidof $PKG | tr -d '\r')" ] && say "Noor's process is gone" || say "warning: Noor's process is still alive"
  adb shell input keyevent KEYCODE_SLEEP
  [ "$S" != no-exact ] && adb shell dumpsys deviceidle force-idle >/dev/null && say "device forced into Doze"

  local ok=1
  if [ "$S" = no-exact ]; then
    # Inexact alarms may come late; wait up to 10 minutes for the alarm and the backup notification.
    ok=0
    for _ in $(seq 1 120); do
      if adb shell dumpsys notification --noredact | grep -q "channel=noor_adhan_backup"; then ok=1; break; fi
      sleep 5
    done
    [ $ok = 1 ] && say "backup notification posted (Android plays its short Adhan)" || say "FAIL: no backup notification"
  else
    local seen=0
    for _ in $(seq 1 60); do
      if [ "$(alarm_players)" -gt 0 ]; then seen=1; break; fi
      sleep 2
    done
    if [ $seen = 1 ]; then
      say "Android reports Noor playing on the alarm stream"
      sleep 15
      [ "$(alarm_players)" -gt 0 ] && say "still playing 15 s later" || { say "FAIL: playback stopped within 15 s"; ok=0; }
    else
      say "FAIL: Android never reported Noor playing on the alarm stream"; ok=0
      adb shell dumpsys audio | grep -E "u/pid:$APP_UID/" | head -5
    fi
  fi
  adb shell dumpsys deviceidle unforce >/dev/null; adb shell input keyevent KEYCODE_WAKEUP
  sleep 20 # let a 30 s Adhan finish and the history be written
  local list; list="$(records)"
  echo "$list"
  case "$S" in
    normal|vibrate)
      echo "$list" | grep -q "outcome=PLAYED " || { say "FAIL: history does not say PLAYED"; ok=0; }
      local ms; ms=$(echo "$list" | grep -m1 "outcome=PLAYED " | sed -n 's/.*playedMs=\([0-9]*\).*/\1/p')
      [ "${ms:-0}" -ge 25000 ] && say "played ${ms} ms" || { say "FAIL: played only ${ms:-0} ms"; ok=0; } ;;
    no-exact)
      echo "$list" | grep -q "outcome=BACKUP_SOUND" || { say "FAIL: history does not say BACKUP_SOUND"; ok=0; } ;;
  esac
  [ "$S" = vibrate ] && adb shell cmd audio set-ringer-mode NORMAL >/dev/null 2>&1
  if [ $ok = 1 ]; then say "PASS"; else say "FAILED"; FAILURES=$((FAILURES + 1)); fi
}

adb wait-for-device
for _ in $(seq 1 60); do [ "$(adb shell getprop sys.boot_completed | tr -d '\r')" = 1 ] && break; sleep 2; done
echo "Device: $(adb shell getprop ro.product.model | tr -d '\r'), Android $(adb shell getprop ro.build.version.release | tr -d '\r') (API $(adb shell getprop ro.build.version.sdk | tr -d '\r'))"
if [ "$WHICH" = all ]; then for s in normal vibrate no-exact; do run "$s"; done; else run "$WHICH"; fi
echo "Scenarios failed: $FAILURES"
exit $FAILURES

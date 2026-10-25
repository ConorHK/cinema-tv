#!/usr/bin/env bash
# Installs the CI build of Cinema on the Chromecast with Google TV beside the owner's own copy (it has the
# applicationId suffix ".ci"), opens each setup page, and fails if the app crashes. Leaves the TV as it found it.
#
#   chromecast-smoke.sh <app-debug.apk> [output-dir]
#
# CHROMECAST is the adb address (default 192.168.1.220:5555). The Chromecast only listens on port 5555 after
# `adb tcpip 5555`, and forgets that when it restarts; see docs/ci-devices.md.
set -euo pipefail

APK=${1:?usage: chromecast-smoke.sh <apk> [output-dir]}
OUT=${2:-out/chromecast}
TARGET=${CHROMECAST:-192.168.1.220:5555}
ADB=${ADB:-adb}
PKG=com.danielportelabyrne.cinema.ci
ACTIVITY=$PKG/com.danielportelabyrne.cinema.CinemaActivity
mkdir -p "$OUT"

tv() { "$ADB" -s "$TARGET" "$@"; }

"$ADB" start-server >/dev/null
if ! "$ADB" connect "$TARGET" 2>&1 | tee "$OUT/connect.txt" | grep -q "connected to"; then
  echo "::error::Cannot reach the Chromecast at $TARGET. It forgets adb-over-network when it restarts: run 'adb tcpip 5555' once from a machine that can already reach it."
  exit 1
fi
tv wait-for-device

# Never take the screen from someone who is watching: only run when the TV is asleep or on its home screen.
awake=$(tv shell dumpsys power | grep -c "mWakefulness=Awake" || true)
top=$(tv shell dumpsys activity activities | grep -m1 topResumedActivity | tr -d '\r' || true)
if [ "$awake" -gt 0 ] && ! echo "$top" | grep -qE "tvlauncher|systemui|settings"; then
  echo "::notice::The Chromecast is in use ($top); skipping the live test so nobody's viewing is interrupted."
  echo "skipped: in use ($top)" > "$OUT/skipped.txt"
  exit 0
fi

was_asleep=0
if [ "$awake" -eq 0 ]; then was_asleep=1; fi
cleanup() {
  tv uninstall "$PKG" >/dev/null 2>&1 || true
  tv shell input keyevent KEYCODE_HOME >/dev/null 2>&1 || true
  if [ "$was_asleep" = 1 ]; then tv shell input keyevent KEYCODE_SLEEP >/dev/null 2>&1 || true; fi
}
trap cleanup EXIT

tv shell input keyevent KEYCODE_WAKEUP
tv uninstall "$PKG" >/dev/null 2>&1 || true
tv install -r -t -d "$APK" | tee "$OUT/install.txt"
tv logcat -c

fail=0
check() {   # $1 = label
  sleep 4
  if [ -z "$(tv shell pidof "$PKG" | tr -d '\r')" ]; then echo "::error::Cinema CI is not running after '$1'"; fail=1; fi
  tv exec-out screencap -p > "$OUT/$1.png"
}

# Single-quote each value for the device shell, so a generated password can't break the command.
sq() { printf "'%s'" "$(printf '%s' "$1" | sed "s/'/'\\\\''/g")"; }
if [ -n "${JELLYFIN_TEST_URL:-}" ] && [ -n "${JELLYFIN_TEST_USERNAME:-}" ] && [ -n "${JELLYFIN_TEST_PASSWORD:-}" ]; then
  # Signs the CI copy in to the test account (a debug-only path, see CinemaActivity.debugTestAccount). The Home
  # screenshot then shows the Continue watching row for that account.
  tv shell "am start -n $ACTIVITY --es debug_jellyfin_server $(sq "$JELLYFIN_TEST_URL") --es debug_jellyfin_username $(sq "$JELLYFIN_TEST_USERNAME") --es debug_jellyfin_password $(sq "$JELLYFIN_TEST_PASSWORD")" >/dev/null
  sleep 15; check signed-in
else
  echo "::notice::No test account secrets; skipping the signed-in check."
  tv shell am start -n "$ACTIVITY" >/dev/null; check launch
fi
for step in welcome jellyfin signin seerr arr client region advanced settings; do
  tv shell am start -n "$ACTIVITY" --es debug_setup_step "$step" >/dev/null; check "setup-$step"
done

# The launcher's Continue watching row reads this provider. From the shell it is refused, because the permission is the
# app's own, and that still shows the provider is registered and enforcing it. Not registered, or crashing, is a fault.
# (Rows themselves need a signed-in copy; the CI build has no sign-in, so this checks the wiring, not the list.)
rows=$(tv shell content query --uri "content://$PKG.continuewatching/items" 2>&1 | tr -d '\r' || true)
echo "$rows" > "$OUT/continue-watching.txt"
if echo "$rows" | grep -qE "Could not find provider|Error while accessing provider"; then
  echo "::error::The Continue watching provider is not answering on the Chromecast; see continue-watching.txt"
  fail=1
fi

tv logcat -d > "$OUT/logcat.txt" || true
if tv logcat -d -b crash | grep -q "$PKG"; then
  tv logcat -d -b crash | grep -B2 -A12 "$PKG" > "$OUT/crash.txt" || true
  echo "::error::Cinema CI crashed on the Chromecast; see crash.txt"
  fail=1
fi
exit $fail

#!/usr/bin/env bash
# Installs the CI Flatpak on the Steam Deck in its own Flatpak installation (so the owner's own Cinema, its settings
# and its sign-in are untouched), runs its self-test (mpv, seeking, the game controller), and removes the app again.
#
#   steamdeck-smoke.sh <Cinema.flatpak> [output-dir]
set -euo pipefail

BUNDLE=${1:?usage: steamdeck-smoke.sh <bundle> [output-dir]}
OUT=${2:-out/steamdeck}
APP=io.github.danielportelabyrne.Cinema
mkdir -p "$OUT"

# A separate "user" installation: runtimes are kept in $FLATPAK_USER_DIR, so the first run downloads them once.
export FLATPAK_USER_DIR="$HOME/.cinema-ci/flatpak"
mkdir -p "$FLATPAK_USER_DIR"

flatpak --user remote-add --if-not-exists flathub https://flathub.org/repo/flathub.flatpakrepo
flatpak --user install --noninteractive --assumeyes --bundle "$BUNDLE" 2>&1 | tee "$OUT/install.txt"
flatpak --user info "$APP" | tee "$OUT/info.txt"

trap 'flatpak --user uninstall --noninteractive --assumeyes "$APP" >/dev/null 2>&1 || true' EXIT

# CINEMA_HOME keeps the self-test away from any real settings.
set +e
timeout 180 flatpak --user run --env=CINEMA_HOME=/tmp/cinema-ci "$APP" --self-test 2>&1 | tee "$OUT/selftest.txt"
status=${PIPESTATUS[0]}
set -e
if [ "$status" -ne 0 ] || ! grep -q "All checks passed" "$OUT/selftest.txt"; then
  echo "::error::The Cinema self-test failed on the Steam Deck (exit $status); see selftest.txt"
  exit 1
fi

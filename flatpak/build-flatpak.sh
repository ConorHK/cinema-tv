#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."

./gradlew :desktop:test :desktop:linuxBundle -Pcinema.linux=true -Pcinema.desktopOnly=true --console=plain
mkdir -p build/flatpak
flatpak run org.flatpak.Builder --user --force-clean \
  --repo=build/flatpak/repo --state-dir=build/flatpak/state \
  build/flatpak/app flatpak/io.github.danielportelabyrne.Cinema.yml
flatpak build-bundle --runtime-repo=https://flathub.org/repo/flathub.flatpakrepo \
  build/flatpak/repo build/flatpak/Cinema.flatpak io.github.danielportelabyrne.Cinema master
echo "Created build/flatpak/Cinema.flatpak"

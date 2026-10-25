{ pkgs ? import <nixpkgs> {} }:

pkgs.mkShell {
  buildInputs = [
    pkgs.jdk17
    pkgs.flatpak
    pkgs.appstream
    pkgs.fuse
    pkgs.fuse3
    pkgs.openssh
    pkgs.gradle
    pkgs.rsync
    pkgs.patch
    pkgs.coreutils
    pkgs.sshpass
    pkgs.gh
  ];

  shellHook = ''
    # Use Nix's resolved store paths; shell variables do not expand globs.
    export JAVA_HOME="${pkgs.jdk17}"
    # Fix FUSE permissions for flatpak builds in nix.
    export FUSERMOUNT_DIR="${pkgs.fuse}/bin"
    # Set XDG data dirs for Flatpak integration
    export XDG_DATA_DIRS="/var/lib/flatpak/exports/share:/usr/share"
    # Optional: Set Steam Deck defaults (change as needed)
    export STEAM_DECK_HOST="deck@steamdeck.local"
    export STEAM_DECK_PATH="/home/deck/flatpaks"
    echo "Cinema Flatpak Build Environment Loaded"
    echo "  Java: $JAVA_HOME"
    echo "  Steam Deck: $STEAM_DECK_HOST"
  '';
}

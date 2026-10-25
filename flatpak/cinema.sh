#!/bin/sh
# Starts Cinema inside the Flatpak: its own Java (/app/jre), every jar in /app/lib/cinema on the class path.
# Java's window toolkit draws through X11 (Xwayland on a Wayland desktop, and in Steam's Game Mode).
export JAVA_HOME=/app/jre
mkdir -p "${XDG_CACHE_HOME:-$HOME/.cache}/cinema/tmp"
exec /app/jre/bin/java \
  -Xmx768m \
  -Djava.io.tmpdir="${XDG_CACHE_HOME:-$HOME/.cache}/cinema/tmp" \
  -Dawt.useSystemAAFontSettings=on \
  -Dskiko.renderApi=OPENGL \
  -cp "/app/lib/cinema/*" \
  com.danielportelabyrne.cinema.desktop.MainKt "$@"

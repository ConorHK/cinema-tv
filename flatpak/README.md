# Cinema for Steam Deck and Linux

The desktop app includes the streaming catalogue, Jellyfin library and playback, Seerr requests, Downloads,
and public Letterboxd profiles and lists. It uses a game controller, a keyboard, or touch. Films play in mpv;
Jellyfin receives playback progress. Optional services are configured inside Cinema.

## Install on a Steam Deck

1. Switch to **Desktop Mode** and copy `Cinema.flatpak` to the Deck.
2. Open it with **Discover** and install it. The first installation needs internet access for the runtime.
   From a terminal, the equivalent is:
   ```bash
   flatpak install --user ./Cinema.flatpak
   ```
3. Launch Cinema from the application menu. Set up your own Jellyfin server using Quick Connect or a username
   and password. Add Seerr and any other services you use. No server details are included in the bundle.
4. In Steam, choose **Games → Add a Non-Steam Game**, select Cinema, and add it. Return to Game Mode,
   launch Cinema from your library, and use the **Gamepad With Joystick Trackpad** controller layout.

Install a later `Cinema.flatpak` the same way to update. Your settings are retained. The package is a local
build, so updates are installed manually; it is not currently published on Flathub.

## Controls

| Button | Action |
| --- | --- |
| D-pad / left stick | Move focus |
| A / Enter | Select |
| B / Escape | Back |
| X | Alternate action on the focused card |
| Y | Search |
| LB / RB | Previous / next main page |
| Menu (≡) / F1 | Settings |

During playback, Cinema shows the player controls behind mpv. A pauses; D-pad left/right seeks; the other
buttons control subtitles, audio and chapters. A keyboard works for text entry, and the on-screen keyboard
works entirely with the controller.

## Build a bundle

On an x86-64 Linux machine, install JDK 17, Flatpak, and these Flathub tools:

```bash
flatpak remote-add --user --if-not-exists flathub https://flathub.org/repo/flathub.flatpakrepo
flatpak install --user flathub org.flatpak.Builder org.freedesktop.Platform//25.08 \
  org.freedesktop.Sdk//25.08 org.freedesktop.Sdk.Extension.openjdk17//25.08
bash flatpak/build-flatpak.sh
```

The result is `build/flatpak/Cinema.flatpak`. The bundle contains Cinema, Java, controller support and mpv.
The Freedesktop runtime supplies the system libraries and codecs. The first build downloads dependencies
and compiles mpv; subsequent builds reuse the Flatpak Builder cache. An Android SDK is not needed with
`-Pcinema.desktopOnly=true`.

The Kotlin/Linux jars can also be built on Windows, then copied with `flatpak/` to a Linux builder:

```powershell
.\gradlew.bat :desktop:linuxBundle '-Pcinema.linux=true' '-Pcinema.desktopOnly=true'
```

Preserve `desktop/build/linux/lib/` relative to `flatpak/`, then run the Flatpak Builder and `build-bundle`
commands from `build-flatpak.sh` on Linux.

## Check a build

```bash
./gradlew :desktop:test -Pcinema.desktopOnly=true
flatpak run io.github.danielportelabyrne.Cinema --self-test
```

The self-test exercises mpv loading, playback position, seeking, shutdown, and SDL controller support.
It uses generated media and needs no server. For a real playback test, pass a local media file or URL.

`./gradlew :desktop:deckScreens` renders sample screens to `desktop/build/screens/`. For a window on a desktop,
use `CINEMA_WINDOWED=1 ./gradlew :desktop:run`. Settings and cache live under
`~/.var/app/io.github.danielportelabyrne.Cinema/` in a Flatpak. Never include that folder when sharing a build.

Letterboxd may require a browser check. Cinema tries a normal page request, then an isolated JavaFX browser
with a time limit. If the service refuses the request, cached content stays available and Cinema remains open.
Debug key logging and remote-control hooks are off in normal launches.

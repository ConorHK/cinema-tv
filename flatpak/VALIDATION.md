# Flatpak validation — 24 October 2026

- 31 desktop tests passed, including keyboard routing, arrow-key handling, modal input, and Letterboxd fallbacks.
- 257 Android tests passed; the debug APK built successfully.
- The Linux jar bundle built with `-Pcinema.desktopOnly=true`, without configuring the Android app module.
- Sample screens rendered at 1280×800 and 1920×1080.
- The exported `Cinema.flatpak` itself was reinstalled and run on a Steam Deck in Desktop Mode. Its self-test passed after that installation; Cinema was then left running normally without debug key logging or remote-control hooks.
- Its self-test passed mpv startup, IPC, generated-media playback, position reporting, seeking and shutdown.
- SDL loaded and detected Steam Virtual Gamepad.
- Controller navigation through Letterboxd Follow, public profile lookup, recent watches and the diary was checked on the Deck. The temporary test follow was removed afterward.
- Physical-keyboard search retained its first character; arrows moved focus without entering invalid characters.
- Downloads focused its setup action and opened the Seerr setup screen.

## Limits of this check

Seerr was not signed in on the test installation, so live requesting, cancellation and download-priority operations were not exercised against a real server. Their shared clients have unit-test coverage. No new download requests were sent.

Some public Letterboxd pages return a browser check that the embedded browser cannot complete. Profile lookup falls back to the public film-page header, and diaries can fall back to RSS. Favourites and profile statistics are absent when only the smaller header is available. Other refused pages show an availability message; the isolated browser cannot crash the main app.

The Deck was tested in Desktop Mode. Game Mode launch and a full film played through Jellyfin were not exercised in this pass; playback transport and SDL were verified by the installed app’s self-test.

## Installer

`build/flatpak/Cinema.flatpak` — 135,145,736 bytes (about 129 MiB).

SHA-256: `40a7956943eeadda81e0a66937fa107bd7960a0698a905a03d41f375cbc7775c`.

The local file checksum matches the installer exported on the Deck. The bundle contains the application and libraries; the test installation’s preferences and sign-in data are outside the bundle.

# Contributing

Thanks for helping. Here is how to get a change in.

## Before you start

- For anything bigger than a small fix, open an issue first saying what you want to change, so nobody builds the same thing twice.
- Set up the project as described in the [README](README.md#building), and check that the tests pass:
  ```bash
  ./gradlew :core:test testDebugUnitTest
  ```

## Making a change

1. Branch from `main`, for example `fix/deluge-login` or `feature/rtorrent-client`.
2. Keep the change focused. One feature or fix per pull request.
3. Add or update unit tests for any logic you touch: parsing, network clients, settings. Network code is tested against fake transports, so tests never need a real server.
4. Check it on a TV or the Android TV emulator, using only the remote's D-pad, Back and OK. Every screen must be fully usable without a touchscreen or mouse.
5. Run the tests and a debug build before you push:
   ```bash
   ./gradlew :core:test testDebugUnitTest assembleDebug
   ```
   Code in `core/` is shared with the Linux desktop app, so it must not use Android classes. If you touch it, also run `./gradlew :desktop:test`. Tests for shared logic go in `core/src/test`.

## Rules the app keeps

- **No one's setup is built in.** No server addresses, usernames, API keys or Letterboxd accounts in code, tests or resources. Use made-up values in tests (`192.168.1.20`, `someone`).
- **Secrets stay secret.** Passwords and API keys go through `SecretStore` only. Never log them, put them in a URL, or commit them. `keystore.properties` and `*.jks` are git-ignored; keep it that way.
- **Optional things stay optional.** Seerr, Sonarr/Radarr, the torrent client and the helper services can each be missing. Hide what can't work instead of showing an error.
- **The remote is the only input.** Focus must always land somewhere sensible, and Back must always go somewhere.
- **Plain words.** On-screen text and code comments should be short and easy to read.

## Opening the pull request

- Write a title that says what changes for the viewer, for example "Deluge: download controls now work after a daemon restart".
- In the description, cover:
  - what changed and why
  - how you tested it: unit tests, plus which device or emulator
  - screenshots for anything on screen; debug builds can open any page directly (see the README)
- GitHub Actions runs the tests and a debug build on the PR; it must pass. A maintainer will then review, and may ask for changes before merging.
- By opening a PR you agree your change is released under the project's [MIT licence](LICENSE).

## Reporting a bug

Open an issue and include:
- the TV or streaming device, and its Android version
- what you expected, and what happened instead
- which services are set up: Jellyfin version, Seerr, which torrent client

Leave out addresses, usernames and keys.

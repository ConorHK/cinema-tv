# Testing pull requests on real devices

Heavy work runs on GitHub's own servers. The two real devices only install a finished build and look at it.

| Where | What | Workflow |
|---|---|---|
| GitHub (`ubuntu-latest`) | Unit tests, APK, desktop jars | `ci.yml` |
| GitHub (Flatpak container) | The Flatpak bundle, with mpv compiled and cached | `ci.yml`, job `flatpak` |
| GitHub (`ubuntu-latest` + Nix) | `nix build .#cinema-tv`, which catches a stale `nix/deps.json` | `nix.yml` (only when build files change) |
| Steam Deck (self-hosted runner) | Installs the Flatpak, runs `--self-test` (mpv, seek, game controller) | `device-tests.yml` |
| Chromecast with Google TV, driven by the Deck over adb | Installs the APK as a separate `.ci` app, opens every setup page, fails on a crash | `device-tests.yml` |

## What a device run does and does not touch

- The Chromecast gets `com.danielportelabyrne.cinema.ci`, beside the real Cinema, and the script removes it again. It
  does not run if someone is watching something, and it puts the TV back to sleep if it was asleep.
- The Deck uses its own Flatpak installation in `~/.cinema-ci/flatpak` and an empty `CINEMA_HOME`, so the real
  Cinema, its settings and its sign-in are not touched. Its runtimes are downloaded once.
- Screenshots and logs are attached to the run.

## Who can run code on the devices

The devices are on a home network and the repository is public, so:

- **Outside contributors** need approval before any workflow runs (Settings → Actions → General).
- `device-tests.yml` runs only after CI passed on a branch **of this repository**, never a fork.
- It always uses the workflow and scripts from `main`, and only installs the build CI produced.
- The `devices` environment needs the owner's approval for each run. Approve it on the run's page.
- Anyone with **write access** to the repository can still add a workflow that targets the runner, so give write
  access only to people you would trust with the Deck.

## Setting it up again

On the Deck (user `deck`, no root needed): the runner is in `~/actions-runner` and runs as the user service
`cinema-ci-runner` (`systemctl --user status cinema-ci-runner`). `adb` is in `~/.cinema-ci/platform-tools`. To
register a runner again, get a token with `gh api -X POST repos/<owner>/<repo>/actions/runners/registration-token`
and run `./config.sh --url ... --token ... --labels cinema-devices,steamdeck,chromecast`.

The Chromecast only accepts adb over the network on port 5555 after `adb tcpip 5555`, and forgets that when it
restarts. If the Chromecast job says it cannot reach the TV, run that once from any machine that can already adb into
it (Settings → System → Developer options → USB debugging must be on), then run `adb connect <ip>:5555` once on the
Deck and choose "Always allow" if the TV asks. Its address is set by the `CHROMECAST` variable in the script
(default `192.168.1.220:5555`).

To run it by hand for any CI run: Actions → Device tests → Run workflow, with that run's id.

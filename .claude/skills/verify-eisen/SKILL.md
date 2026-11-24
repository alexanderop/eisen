---
name: verify-eisen
description: Drive the real Eisen Android app (Kotlin, Compose) on the emulator and prove a change works - launch a fresh or existing install, tap through a feature the way a lifter would, and capture screenshots, on-screen texts, the saved eisen.json and logcat. Use before calling any change under apps/android done, when asked to verify, test, screenshot or run the app, and whenever tests pass but nobody has looked at the app.
---

# Verify Eisen on the emulator

Eisen is a workout tracker. The app is `apps/android` (the PWA in `apps/web` is frozen: do not
verify it). A user touches five tabs: Home, Workouts, Timer, History, Settings. Unit tests cover the
pure program; this skill covers what they cannot: the running app, its services, its notifications
and its saved file.

Everything goes through one helper, run from the repo root:

```sh
.claude/skills/verify-eisen/bin/eisenctl <command>
```

Below it is written `eisenctl`. It needs the Android SDK at `/opt/homebrew/share/android-commandlinetools`
(or `ANDROID_HOME`), JDK 21 at `/opt/homebrew/opt/openjdk@21` (or `JAVA_HOME`) and an AVD named
`eisen`. No other tool.

## Launch

```sh
eisenctl launch --fresh      # build the debug APK, install it, wipe the app's data, start it
eisenctl launch              # the same, on the data the emulator's app already has
eisenctl launch --no-build   # reuse the APK that is already built
```

- Starts the emulator (`-no-audio`) only when none is attached, and waits for it to boot.
- Saves the emulator's current `eisen.json`, `timer-run.json` and `wod-draft.json`; `cleanup` puts
  them back. A run never costs the emulator its data.
- `--fresh` clears the app's data: the app then starts with its two built-in workouts, kilograms,
  no history. Use it for every journey in `features/` unless the journey says otherwise. Without
  `--fresh` you verify an **update**: the new build on old data.
- Grants the notification permission, turns system animations off and mutes the media volume.
  **The owner wants every test muted. Never raise the volume.**
- Ready means the five tabs are on screen, not that the process started. It prints
  `ready: <evidence directory>`. If it cannot get there in 30 seconds it fails and says so.
- One run at a time: a second `launch` refuses while a run is open.

## Doctor

```sh
eisenctl doctor
```

Read-only, changes nothing, exit 0 only when every line is `ok`: one emulator attached, it is the
AVD `eisen`, booted, a run is open, the app is installed, **the installed APK is byte-for-byte the
one built in this checkout**, the app is on screen, media is muted, no `FATAL EXCEPTION` since
launch. Run it first whenever something looks off, and again before you report, so a crash during
the journey cannot go unnoticed.

## Drive

The app is Compose without test tags. The stable handles are the texts and content descriptions a
user reads, and `eisenctl` matches them **whole** (a regular expression against the entire value:
`Timer` does not match `Timer voice`; `Start.*` matches `Start Fran`).

```sh
eisenctl texts                 # everything on screen: text=... / content-desc=... and bounds
eisenctl tap "Start workout"   # tap the first match; a third argument picks the n-th
eisenctl wait "Rest" 10        # until it is on screen, at most 10 s (default 15); fails otherwise
eisenctl has "Completed"       # exit code only
eisenctl type "murph"          # into the focused field
eisenctl back                  # the system back key
eisenctl scroll down           # one screen; it waits for the list to settle
eisenctl adb shell ...         # plain adb when nothing else fits
```

Chain a journey with `&&`: a `tap` that finds nothing fails, and a journey that carries on after a
miss proves nothing. When a handle is missing, run `eisenctl texts` and read what the screen really
says before changing the recipe. The journeys with their real handles are in `features/`; read
`features/README.md` first.

## Evidence

```sh
eisenctl shot 03-rest-running   # <run>/03-rest-running.png and .txt (the screen's texts)
eisenctl state 04-after-set     # <run>/04-after-set.json: the eisen.json the app wrote
eisenctl log                    # <run>/logcat.txt since launch
```

Evidence goes to `verify/android/<date-time>/` in the repo (ignored by git). Standards for a proof:

- Drive the path a lifter takes. Never send a broadcast, call an activity with extras or write
  `eisen.json` to get into a state, unless the journey is about exactly that (an update on old data).
- Capture the action **and** the result: a shot before the tap, one after, numbered in order.
- A screen is a claim. Confirm it from a second view: `eisenctl state` for anything saved (a logged
  set, a finished workout, a timer session), `eisenctl adb shell dumpsys notification --noredact`
  for a notification, `eisenctl adb shell dumpsys activity services io.github.alexanderop.eisen`
  for a running service, `eisenctl log` and its `EisenTimer: said <sound>` lines for what the coach
  said (the emulator is muted, so the log is the only witness of sound).
- Nothing is mocked. Time is real: a 20 second interval takes 20 seconds.
- Look at your own screenshots (Read the PNG) before you report. A screenshot nobody looked at is
  not evidence.
- Report per feature id from `features/` what was verified, and what was not and why.

## Cleanup

```sh
eisenctl cleanup
```

Stops the app, puts the emulator's saved files back, turns animations on again, and shuts the
emulator down **only if this run started it**. It never kills by process name. It removes its own
state directory and keeps the evidence, printing where it is. Run it after every run, also a failed
one: an open run blocks the next `launch`.

## What this does not cover

- **Sound itself.** Muted by the owner's rule. The log says which recording was started, not how
  it sounds.
- **A real phone**, TalkBack, Health Connect with real data, and the signed release build (the
  emulator's app is debug-signed; a release APK cannot be installed over it).
- **Two runs side by side.** There is one emulator and one app on it. Do not start a second
  emulator to work around the lock.
- The SharedPreferences (a running rest, the Health Connect switch, the last Timer page) are not
  saved and restored by `launch`/`cleanup`; only the three files are.

## Keeping it honest

When a journey here stops matching the app, fix the feature file in the same change that changed
the app. `/maintain-verification-skill` goes over the whole map.

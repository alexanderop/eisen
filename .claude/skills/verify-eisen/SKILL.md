---
name: verify-eisen
description: Drive the real Eisen Android app (Kotlin, Compose) on the emulator and prove a change works - launch a fresh or existing install, tap through a feature the way a lifter would, and capture screenshots, on-screen texts, the saved eisen.json and logcat. Use before calling any change under apps/android done, when asked to verify, test, screenshot or run the app, and whenever tests pass but nobody has looked at the app.
---

# Verify Eisen on the emulator

Eisen is a workout tracker. The app is `apps/android` (the PWA in `apps/web` is frozen: do not
verify it). A user touches five tabs: Home, Workouts, Timer, Food, History, and Settings behind the gear on Home. Unit tests cover the
pure program; this skill covers what they cannot: the running app, its services, its notifications
and its saved file.

Everything goes through one helper, run from the repo root:

```sh
.claude/skills/verify-eisen/bin/eisenctl <command>
```

Below it is written `eisenctl`. It needs the Android SDK at `/opt/homebrew/share/android-commandlinetools`
(or `ANDROID_HOME`), JDK 21 at `/opt/homebrew/opt/openjdk@21` (or `JAVA_HOME`) and an AVD named
`eisen`. The recipes read the saved JSON with `jq`, which macOS ships in `/usr/bin`. No other tool.

## Launch

```sh
eisenctl launch --fresh      # build the debug APK, install it, wipe the app's data, start it
eisenctl launch              # the same, on the data the emulator's app already has
eisenctl launch --no-build   # reuse the APK that is already built
eisenctl launch --state lifter   # fresh data, then fixtures/lifter.json as the app's eisen.json
eisenctl launch --fresh --animations   # the same with the animation scales left at 1, as on a phone
```

- Starts the emulator (`-no-audio`) only when none is attached, and waits for it to boot.
- Saves the emulator's current `eisen.json`, `timer-run.json`, `wod-draft.json` and `plate.json` (what is on the
  plate of the Food tab's add screen); `cleanup` puts them back. A run never costs the emulator its data.
- `--fresh` clears the app's data: the app then starts with its two built-in workouts, kilograms,
  no history. Without `--fresh` you verify an **update**: the new build on old data.
- `--state <name>` implies `--fresh`, then writes `fixtures/<name>.json` to the app's `files/`
  before it starts, so a journey that needs earlier data or a short timer does not tap it together.
  The file's days are moved by whole weeks so its newest entry is in the last seven days; its food log
  is moved by whole weeks so its newest day is one of the seven days before the phone's today (today
  starts empty, the times of day stay); its weights (and a check-in answered) move by the same days. The moved copy is the
  run's `00-fixture-<name>.json`. Each journey in `features/` says which start it needs.
- Grants the notification permission, turns system animations off (`--animations` leaves them at 1),
  wakes the screen and mutes the media volume. **The owner wants every test muted. Never raise the volume.**
  A run with the animations off is faster and steadier, and misses what only breaks while something
  moves: a tap during a transition, a screen reopened while it leaves. Run the recipes that say so with
  `--animations`.
- Ready means the app's first screen is drawn (the five tabs, or a workout that runs), not that the
  process started. It prints `ready: <evidence directory>`. If it cannot get there in 30 seconds it
  fails and says so.
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

## Release smoke

```sh
eisenctl release-smoke            # the signed release APK that is built (apps/android/app/build/outputs/apk/release)
eisenctl release-smoke --build    # builds it first (EISEN_KEYSTORE, EISEN_KEYSTORE_PASSWORD, EISEN_VERSION_CODE,
                                  # else ~/.eisen/eisen-release.jks and ~/.eisen/keystore-password.txt)
eisenctl release-smoke --apk <file>
```

Every journey runs the debug build; builds 22 and 23 broke only under R8. This runs the release build: it keeps the
debug app's files, uninstalls it (the release key is another signature), installs the release APK muted with the
animations off, and drives it: Home, a workout started and discarded, Workouts, a timer's setup, the Food tab, its add
screen, Scan (the scanner's own screen, or the typed barcode while Play services fetches the scanner), a typed barcode
looked up in airplane mode ("No network": a release build reads no local answers), a new own food (its picture following its name, the grid of pictures, one picked)
put on the plate and logged, a photo of a nutrition table (`fixtures/labels/vanille-eis.jpg`, pushed to the pictures) chosen in the
system's photo picker and read on the phone by ML Kit (the form must show its 200 kcal, 1.6 g protein, 25.5 g carbs and 10.1 g
fat, each marked read), Settings behind the gear, History › Progress and a weight logged in its Weight section. Before it installs anything it counts the pictures of the foods in the APK's resource table (`aapt2 dump resources`): one
missing, as resource shrinking could drop it, fails. It **fails on a crash, an ANR, or a `ClassNotFoundException`,
`NoSuchMethodException` or `ComponentDiscovery` warning of the app's process in logcat**, and on any step that does
not show. Then, failed or not, the debug build goes back on with its files, airplane mode off, animations and font as
found. Evidence: `verify/android/<date-time>-release/`. It needs no open run and leaves none; it stamps nothing. About
two minutes. Proven on the R8 bug of build 23: without the keep rule in `app/proguard-rules.pro` it fails on
`ComponentDiscovery: Could not instantiate ...CommonComponentRegistrar`; with that rule leaving out ML Kit's
`TextRegistrar` (build 27) it fails on the label: `Could not instantiate ...TextRegistrar`, and the form is never filled.

## Drive

The app is Compose without test tags. The stable handles are the texts and content descriptions a
user reads, and `eisenctl` matches them **whole** (a regular expression against the entire value:
`Timer` does not match `Timer voice`; `Start.*` matches `Start Fran`).

```sh
eisenctl texts                 # everything on screen: text=... / content-desc=... and bounds
eisenctl tap "Start workout"   # tap the first match; a third argument picks the n-th
eisenctl doubletap "5" 1 150   # two taps from one dump, 150 ms apart (the default): a bouncing thumb
eisenctl hold "Hold to finish" # press it for a second
eisenctl wait "Rest" 10        # until it is on screen, at most 10 s (default 15); fails otherwise
eisenctl has "Completed" "2 rounds"   # each one on screen, checked on one dump
eisenctl gone "Undo"                  # none of them on screen, on a fresh read; never `! eisenctl has`
eisenctl type "murph"          # into the focused field
eisenctl back                  # the system back key
eisenctl scroll down           # one screen; it waits for the list to settle
eisenctl font 2.0              # the system font scale; cleanup puts back the one the run found
eisenctl visible "Next set" "Rest 15 seconds less"   # on screen, not cut off, tapped on at least 48 dp
eisenctl small                 # fails listing every clickable node on screen under 48 dp
eisenctl adb shell ...         # plain adb when nothing else fits
```

A read of the screen is a `uiautomator` dump that takes about two seconds, so `eisenctl` keeps the
last one and uses it again while nothing was sent to the phone since and it is at most two seconds
old: in `tap A && wait B && has C && visible D && shot e` only the `wait` reads. A check that fails
on a kept read is made again on a fresh one, so a kept read never fails a check. It could pass one
that asks for something to be absent, which is why that is `gone` (always a fresh read) and a
journey refuses `! eisenctl has`. Give `wait` real seconds. Chain a journey with `&&`: a `tap` that finds
nothing fails, and a journey that carries on after a miss proves nothing. When a handle is missing, run `eisenctl texts` and read what the screen really
says before changing the recipe. The journeys with their real handles are in `features/`; read
`features/README.md` first.

The emulator has no Gemini Nano, so the photo of a dish cannot ask a model there. `eisenctl dish "rice, cooked; 180\negg, fried; 50"`
writes the answer a debug build gives instead (`files/dish-ai.txt`; a release build never reads it, and `cleanup` clears
it with the app's data). It proves the screens, the reading of the answer and the matching, not the model's judgement:
that needs a phone that has the model.

## Evidence

```sh
eisenctl shot 03-rest-running   # <run>/03-rest-running.png and .txt (the screen's texts)
eisenctl state 04-after-set     # <run>/04-after-set.json: the eisen.json the app wrote
eisenctl log                    # <run>/logcat.txt since launch
eisenctl said round-1 complete  # fails unless the log has "EisenTimer: said <sound>" for each
eisenctl buzzed                 # fails unless a vibration of the app played since launch and none was dropped
```

Evidence goes to `verify/android/<date-time>/` in the repo (ignored by git). Standards for a proof:

- Drive the path a lifter takes. Never send a broadcast, call an activity with extras or write
  `eisen.json` to get into a state, unless the journey is about exactly that (an update on old data).
  The one way in is `launch --state` with a fixture `FixturesTest` checks; the journey then starts
  on that data and drives everything after it.
- Capture the action **and** the result: a shot before the tap, one after, numbered in order.
- A screen is a claim. Confirm it from a second view: `eisenctl state` for anything saved (a logged
  set, a finished workout, a timer session), `eisenctl adb shell dumpsys notification --noredact`
  for a notification, `eisenctl adb shell dumpsys activity services io.github.alexanderop.eisen`
  for a running service, `eisenctl log` and its `EisenTimer: said <sound>` lines for what the coach
  said (the emulator is muted, so the log is the only witness of sound), and `eisenctl buzzed` for a
  vibration (an emulator does not shake: `dumpsys vibrator_manager` is the witness; a vibration of an app
  in the background without an alarm usage is logged `ignored_background` and nobody feels it).
- Nothing is mocked. Time is real: a 20 second interval takes 20 seconds. There is no fast clock on
  purpose: alarms, the timer's service and the notification's chronometer would run a path the
  release does not have. Speed comes from the fixtures' short timers and prepared data instead.
- Look at your own screenshots (Read the PNG) before you report. A screenshot nobody looked at is
  not evidence.
- Report per feature id from `features/` what was verified, and what was not and why.

## Journeys

```sh
eisenctl journey workout        # every recipe of features/workout.md
eisenctl journey workout 2      # one recipe, by number or by a regex on its bold headings
```

Runs a feature file's `sh` blocks as written, recipe by recipe, and stops at the first command that fails,
also one in the middle of `a && b && c` (under `bash -e` such a failure is hidden). On a failure it runs
`doctor` and `cleanup` and says which command failed. It prints each recipe's time. A journey whose every
recipe passed on APKs built from the current sources stamps them; it is the only thing that does.

### How much to run, and when

**Screen tests first.** A flow through the screens (tap here, this shows there) is a screen test in
`apps/android/app/src/test/kotlin/io/github/alexanderop/eisen/screens/`, not a journey: the real activity under
Robolectric, about a second a flow, run by `bin/check` on every change. The journeys here are for what only a
phone shows:

| What | Where |
|---|---|
| A tap opens a screen, a sheet, a list shows a row, a value is logged | screen test (`EisenApp("food")`, `tap`, `tab`, `has`) |
| The camera app, the photo picker, a real barcode scan | journey |
| Notifications, the lock screen, a killed process, alarms | journey |
| Gemini Nano (AICore) on a real model | journey (and the Pixel) |
| Every font scale, 48 dp targets (`small`), what fits a phone's screen | journey (`layout`) |
| The release build under R8 | `release-smoke` |

A screen test reads the same fixtures as `launch --state` (`fixtures/*.json`).

**Which recipes the screen tests cover.** Each test runs at a fixed moment (Wednesday 25 November 2026, 10:00 in
Berlin, `NOW` in `Screens.kt`) on Robolectric's clock, so `later(seconds)` runs a rest, a lead-in or a whole timer in
no time; `@Fixture("food-weight")` picks another fixture per test, `app.answers(...)` the saved Open Food Facts answers,
`app.restart()` reads the files again as a new process would. Run a journey only for what is left in the last column.

| Feature file | Screen test (`screens/`) | Still a journey, and why |
|---|---|---|
| `workout.md` | `WorkoutScreenTest` | the rest's notification, the vibration (`buzzed`), the rest that ends while the process is killed, the alarm put back after a force-stop |
| `food.md` | `FoodScreenTest` (all fixtures) | the photo of a label read by ML Kit and the photo of a dish (the photo picker, Gemini Nano), the dish sheet at font scale 2.0; the real scanner is `release-smoke`'s |
| `history-and-data.md` | `HistoryScreenTest` | `data-update` (a new build over old data), the backup's file pickers |
| `wods.md` | `WodsScreenTest` | nothing |
| `interval-timers.md` | `TimerScreenTest` | the screen going off, the foreground service and its notification, the killed app picking its run up again |
| `boxing.md` | `BoxingScreenTest` | nothing but the sound itself |
| `awards.md` | `AwardsScreenTest` (and `AwardsTest` for the numbers) | the drawing only: medal shapes, the confetti and the flip (`--animations`), font scales |
| `layout.md` | none | all of it: the font scales, 48 dp targets (`small`), what fits a phone's screen (`visible`) |

A full run of everything takes most of an hour. Alexander does not want to wait for it: not every change needs
every journey end to end. The default, also before a push:

- **Run what the change touches.** `bin/check`, then the recipes of the features the change touches:
  `eisenctl journey food 11`, or a regex on the recipe's heading. A recipe takes one to two minutes. Click
  through the changed screens by hand (`tap`, `shot`) where no recipe covers them yet.
- **Not the rest.** A journey of a feature the change does not touch is not run, and a push does not wait
  for one. The push gate then warns that the sources lack the `journeys` stamp; that is expected, say so in
  the report and push.
- **Everything only when asked**, or for a change that reaches every screen at once (the theme, the app's
  bar, the saved state's format) — and even then, offer it with its time ("about 60 minutes") instead of
  starting it.
- `eisenctl journeys` runs everything: it spreads the feature files over two emulators with the same screen
  (`eisen` on port 5554, `eisen361` on 5556; `EISEN_AVD=eisen361 eisenctl …` drives the second one by hand),
  each taking the next feature that is free, and writes one log per feature. A pass of all of them stamps the
  sources `journeys`.
- `eisenctl journeys food layout` runs just those two side by side.

The second emulator is started by `journeys` when it does not run and stopped by it at the end; one
that already ran is left running.

**`journeys` has not passed a full run yet.** Its two tries ended with recipes failing at their first
check on both emulators (the second try was cut off from outside, so its failures may be that); a single
`journey` on either emulator passes. When a full run is asked for, run the features one after the other,
and treat the push gate's stamp as not reachable.

## Cleanup

```sh
eisenctl cleanup
```

Stops the app, puts the emulator's saved files back, turns animations on again, puts back the font
scale, and shuts the emulator down **only if this run started it**. It stamps nothing: only `journey` does. It never kills by process name. It removes its own
state directory and keeps the evidence, printing where it is. Run it after every run, also a failed
one: an open run blocks the next `launch`.

## Fixtures

Prepared `eisen.json` files in `fixtures/`, for `launch --state <name>`. `FixturesTest` (part of
`bin/check`) fails when one no longer loads, is not at the current state version, changes when the
app saves it again, holds a timer the setup wheels cannot show, or lost what its name promises. A
change to the state that breaks one is fixed in the same change: load it and save it again.

- `short-timers`: fresh data plus six saved timers, each over within 40 seconds with the 10 second
  lead-in: `Quick Tabata` (2 × 10s / 5s), `Quick EMOM` (2 × 10s), `Quick AMRAP` (30s),
  `Quick For Time` (cap 1:00, complete when `Hold to finish` is held), `Quick boxing` (one round of
  0:30, `Jab only`, `Fast`); and `Boxing 3:00 calls` (one round of 3:00, `2-punch combos`) for the
  time left in the round. The voice says the time left every minute.
- `lifter`: four weeks of history: twelve workouts of A and B with weights that went up, a missed
  bench, ratings and a deadlift raised by hand; a Tabata, an EMOM, a full boxing session (6 × 3:00),
  an AMRAP stopped early, Fran in 7:42, Cindy with 14 rounds, and the lifter's own AMRAP
  `Garage burner` (10:00) with a best of 7 rounds.

- `food`: fresh data, a goal of 2,400 kcal and 160 g protein, and `Skyr Natur` found before by its barcode.
- `food-history`: the same, plus two weeks of food: eight foods, each eaten every day at its own time three hours
  apart (Milk 01:30, Banana 04:30, Oats 07:30, Skyr 10:30, Rice 13:30, Apple 16:30, Chicken 19:30, Cottage cheese
  22:30, Berlin time), so whatever the hour a journey runs at, one of them is "usual at this time".
- `food-weight`: the `food` fixture with four weeks, Monday to Sunday, of three meals a day (two days without) and most
  mornings' weights (84.2 kg down to 82.4 kg), a goal of 2,400 kcal and to lose 0.5 kg a week with the weekly check-in on.
  Moved so its last day is the Sunday before today: the check-in is due, with the same numbers on any day.

Every value in them is one the app's own screens can produce: they were made by sending `update`
the messages the screens send, with wheel values from the setup's own lists.

## What this does not cover

- **Sound itself.** Muted by the owner's rule. The log says which recording was started, not how
  it sounds.
- **A real phone**, TalkBack and Health Connect with real data. The camera app taking a photo of a label (the emulator's
  photographs a virtual room; the journeys use the photo picker). The signed release build only through
  `release-smoke` (a release APK cannot be installed over the debug one, so the journeys cannot run it).
- **Two runs side by side.** There is one emulator and one app on it. Do not start a second
  emulator to work around the lock.
- The SharedPreferences (a running rest, the Health Connect switch, the last Timer page) are not
  saved and restored by `launch`/`cleanup`; only the three files are.

## Keeping it honest

When a journey here stops matching the app, fix the feature file in the same change that changed
the app. `/maintain-verification-skill` goes over the whole map.

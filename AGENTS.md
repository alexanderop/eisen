# Eisen: notes for coding agents

## Android is the app. The PWA is frozen.

- Build features for `apps/android` only. Architecture: `apps/android/ARCHITECTURE.md`.
- Ignore `apps/web` (the Vue PWA). Do not port features to it, do not keep it in sync, and do not
  spend time keeping it green. The owner has said it may be deleted.
- The Kotlin code is the source of truth: the pure packages under
  `apps/android/app/src/main/kotlin/io/github/alexanderop/eisen/` (`core`, `feature/*` without `ui` and
  `platform`, `app/elm`, `app/state`). Older comments that call `apps/web/src/domain/*.ts` or the
  `domain` package the contract are history.
- Code is sorted by feature (`core`, `feature/<name>/{,ui,platform}`, `app/{elm,state,runtime}`).
  `ArchitectureTest` enforces which package may use which; the rules are in `ARCHITECTURE.md`.
- One thing in `apps/web` is still live: the Android build reads its pictures, motion strips and
  voice recordings from `apps/web/src/assets`. New assets go there until they are moved. Whoever
  deletes `apps/web` moves that directory and the voice scripts in `apps/web/scripts` first, and
  removes `.github/workflows/deploy.yml`.

## Checks

```sh
bin/check     # unit tests with the architecture and purity rules, then the debug build
```

- **Cheap checks first.** A Stop hook (`.claude/hooks/stop-check.sh`) runs `bin/check` when the
  Android sources changed and refuses to end the turn on a failure.
- **Three rounds, then a person.** If the checks still fail after three fixes, stop and tell the
  owner which check fails, what you tried and what you think the cause is.
- **A flaky test is a bug.** Do not retry it, sleep past it or skip it. Find the cause, or delete the
  test and write down why.
- **Run the app before calling a change done**; tests alone are not proof. Two ways, cheapest first:
  - **Screen tests** (`src/test/.../screens/`): the real `MainActivity`, `update` and saved state under
    Robolectric, on the JVM. A flow takes about a second and runs in `bin/check`. A change to a screen or
    a flow gets one here: `EisenApp("<fixture>")`, then `tap`, `tab`, `has`, `gone` (`screens/Screens.kt`).
  - **Emulator journeys** (`.claude/skills/verify-eisen`): only for what the JVM cannot show — the camera
    app and the photo picker, notifications and the lock screen, Gemini Nano, the font scales of
    `features/layout.md`, and the release build (`release-smoke`). `eisenctl launch`, a recipe of the
    feature the change touches, `eisenctl cleanup`. Test muted, always.
- **A miss becomes a test.** A bug in a flow gets a screen test, not a journey, unless only a phone sees it.
- **Review** with `REVIEW.md`: a checklist whose items trigger on the diff. Read it from the last
  commit, not from the working copy.
- **A miss becomes a test.** A bug gets a test that fails before its fix, at the cheapest layer that
  sees it: a unit test, then a rule over the sources (`RulesTest`), then a step in a feature file.
  Then ask what kind of bug it was and check the kind, not the case: a rule the random walk in
  `UpdateTest` holds after every message, a limit `LimitsTest` sends the largest number through,
  what `init` puts back after a restart, what `features/layout.md` must see at every font scale.
  Only what no check can hold becomes a `REVIEW.md` item.

## Commits say how they were verified

`.githooks` (switched on by the SessionStart hook, or `git config core.hooksPath .githooks`) adds a
trailer to every commit that changes the app:

```
Verified: emulator, tests     # eisenctl ran these sources, and bin/check passed on them
Verified: tests               # bin/check passed, nobody ran the app
Verified: none                # neither
```

A stamp only counts for the exact sources it was made on: edit a file and it is gone. Unverified
commits: `git log --grep='^Verified: none'`. The gate (`git config eisen.gate off|observe|enforce`,
default `observe`) logs or blocks a `none` commit unless its message says why in a trailer
`Unverified: <reason>`; the log is `.git/eisen/gate.log`.

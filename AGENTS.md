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
cd apps/android
JAVA_HOME=/opt/homebrew/opt/openjdk@21 ANDROID_HOME=/opt/homebrew/share/android-commandlinetools \
  ./gradlew testDebugUnitTest assembleDebug
```

Run the app on the emulator before calling a change done; tests alone are not proof. The skill
`.claude/skills/verify-eisen` does it: `bin/eisenctl launch --fresh`, a journey from its `features/`,
evidence under `verify/android/`, `bin/eisenctl cleanup`. Test muted, always.

# Eisen: notes for coding agents

## Android is the app. The PWA is frozen.

- Build features for `apps/android` only. Architecture: `apps/android/ARCHITECTURE.md`.
- Ignore `apps/web` (the Vue PWA). Do not port features to it, do not keep it in sync, and do not
  spend time keeping it green. The owner has said it may be deleted.
- The Kotlin domain (`apps/android/app/src/main/kotlin/io/github/alexanderop/eisen/domain`) is the
  source of truth. Older comments that call `apps/web/src/domain/*.ts` the contract are history.
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

Run the app on an emulator before calling a change done; tests alone are not proof. Test muted
(`-no-audio`, then `adb shell cmd audio set-volume 3 0`).

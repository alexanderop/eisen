# Eisen 5×5

An offline 5×5 barbell workout tracker that installs as a PWA.

**Live:** https://alexanderop.github.io/eisen/

- Starts with a 5×5 program (Workout A and B) that you can change freely
- Build your own workouts from 45 exercises, or add your own exercises
- Sets, reps, weight and the step per exercise are yours to set
- Tap a set to log it, tap again for every missed rep
- Rest timer after each set
- Weight goes up after a completed exercise and drops 10% after three misses in a row
- Warmup and plate calculator, history, progress chart, kg and lb
- Share a finished workout as a picture, as a link that shows it, or as Markdown
- All data stays on your device, with JSON backup and restore

## Two apps, one repository

| Folder | What | How it ships |
| --- | --- | --- |
| `apps/web` | The PWA: Vue 3, Vite, Tailwind, shadcn-vue | GitHub Pages on every push to `main` |
| `apps/android` | The native Android app: Kotlin, Jetpack Compose, Material 3 | A signed APK on the [`android-latest` release](https://github.com/alexanderop/eisen/releases/tag/android-latest) on every push to `main` |

Both apps store the same JSON. A backup from one restores in the other.
The rules of the program live in `apps/web/src/domain` and, ported file by file, in
`apps/android/app/src/main/kotlin/io/github/alexanderop/eisen/domain`. The same test cases run
against both, so a change to one has to be made in the other.
The exercise pictures and spoken cues in `apps/web/src/assets` are packed into the APK as they are.

## Develop the PWA

```sh
pnpm install
pnpm dev
pnpm test
pnpm build
```

## Develop the Android app

Needs JDK 21 and the Android SDK (platform 37.2). `apps/android/local.properties` points to the SDK:
`sdk.dir=/path/to/android-sdk`.

```sh
pnpm android:test   # unit tests of the domain rules
pnpm android:apk    # debug APK in apps/android/app/build/outputs/apk/debug
```

The app follows The Elm Architecture: one immutable `Model`, a pure `update(msg, model, env)` that is the
only place it changes, and side effects as plain `Cmd` data that a small runtime (`runtime/`) carries out.
Screens are functions of the model and `dispatch`, so every rule is tested through `update` alone.
See [`apps/android/ARCHITECTURE.md`](apps/android/ARCHITECTURE.md).

A release build is signed with the key named by `EISEN_KEYSTORE` and `EISEN_KEYSTORE_PASSWORD`.
Android only installs an update over the app when it is signed with the same key, so that key must not get lost.

## Design system

The UI is built on [shadcn-vue](https://www.shadcn-vue.com) (Reka UI and Tailwind CSS 4).
Its components are copied into `apps/web/src/components/ui/<name>/` and adjusted for a phone in a gym:
larger tap targets, a dark-only palette and lime as the primary colour.

- From shadcn-vue: `Button`, `Card`, `Badge`, `Input`, `Drawer`, `Tabs`, `ToggleGroup`.
- Composed from them in `apps/web/src/components/ui/*.vue`: `Sheet` (a Drawer), `Segmented` (Tabs),
  `ChipGroup` (a ToggleGroup), `Stepper`, `ConfirmButton`, plus the app's own `SetButton` and `Field`.
- Add another one with `pnpm dlx shadcn-vue@latest add <name>`.

Colours use the shadcn variable names (`--primary`, `--card`, `--muted-foreground`) in `apps/web/src/style.css`.

```sh
pnpm storybook        # browse and try every component at http://localhost:6006
pnpm build-storybook  # also runs in CI, so a broken story fails the build
```

Views import from the barrel: `import { Button, Card } from '../components/ui'`.

Built with Vue 3, Vite, Tailwind CSS and vite-plugin-pwa. Pushing to `main` deploys to GitHub Pages.

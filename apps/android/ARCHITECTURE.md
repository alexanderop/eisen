# Eisen Android: architecture

The app follows The Elm Architecture. One immutable `Model`, one `update` function that is the only
place it changes, side effects as plain data (`Cmd`), and a small runtime that carries them out.

```
            Msg (a tap, an alarm, a widget intent, an answer from Health Connect)
             │
             ▼
   update(msg, model, env) ──────────────► Cmds ──► runtime adapters ──► may send a Msg back
   app/elm/Update.kt                                (file, alarms, cues, Health Connect,
   pure, no Android                                  widget, shortcuts, snackbars, UI events)
             │
             ▼
          Model ──► view(model, dispatch)      MainActivity → screens in feature/*/ui/
             │
             └──► subscriptions(model): Set<Sub> ──► runtime starts/stops sources (the rest and hold clocks)
```

## Packages

Code is sorted by feature, under `io.github.alexanderop.eisen`:

```
core/               the pure kernel every feature uses: units, typed numbers, the exercise catalogue, motions
core/ui/            the shared Compose kit: components, formats, haptics, motion tiles, reorder, theme, wheel
feature/<name>/     a feature's pure code         workout, timer (+ boxing, wod), progress, share
feature/<name>/ui/  its screens                   workout, timer, progress, share, history, home, settings, health
feature/<name>/platform/  its Android side        workout (rest), timer, home (shortcuts), health
app/elm/            the program: Model, Msg, Cmd, Sub, update, selectors, the workout machine
app/state/          AppState, its JSON, migrations and loadState
app/runtime/        Eisen (the runtime), storage, snackbars, EisenApp
```

`MainActivity` (root package) and `widget/NextWorkoutWidget` keep their old class names on purpose:
Android stores them outside the app (the launcher icon and pinned shortcuts point at the activity, a
placed widget at its provider), and a new name would drop the lifter's home-screen widget on update.

A package segment `ui`, `platform` or `runtime` is Android code; everything else under `core`,
`feature` and `app` is pure. `ArchitectureTest` checks on the compiled classes:

1. `core` depends on nothing else of the app; `core.ui` only on `core`.
2. Pure code uses no `ui`, `platform` or `runtime` package and nothing from `android..`, `androidx..`,
   `java.io..`, `java.net..`.
3. A feature's pure code uses `core`, its own feature and the features in the test's `featureEdges`
   table, never `app`.
4. A feature's `ui` uses its own feature, `core`, `core.ui`, `app.elm`, `app.state`, other features'
   pure code through `featureEdges` and their `ui` through `uiEdges`. Never another feature's
   `platform` or `runtime`.
5. Only `app.elm` and `app.state` among the pure code know the features together.
6. No cycles between features.

Every table entry carries its reason, and a test fails when one is no longer used. Adding an edge is
a decision: first ask whether the code belongs in `core` or in the program. `RulesTest` checks the
source of every pure package as well: no `!!`, no `throw`, no clock, no dice, no Android or IO import.

## The four types

| Type | File | What it is |
| --- | --- | --- |
| `Model` | `app/elm/Model.kt` | Everything that decides behaviour: the saved `AppState` (its JSON and `STATE_VERSION` are shared with the PWA), the workout `Session`, the rest and its full screen, the hold timer, `lastChanges`, ticked warmups, the reps question, the settling exercise, navigation, Undo data, Health Connect, notifications, `dataNotice`, `visible` and `now`. `Flags` and `init` make the first one. |
| `Msg` | `app/elm/Msg.kt` | Everything that can happen, grouped: `Workout(event)`, `WorkoutScreen`, `Nav`, `Home`, `Routines`, `Exercises`, `History`, `Settings`, `Data`, `Hold`, `Rest`, `Health`, `App`. Named after what happened, not what to do. |
| `Cmd` | `app/elm/Cmd.kt` | A side effect as data: `Save`, the rest's alarms, notifications and cues, the hold timer's `Beep`, `After(millis, msg)`, `Health(job)`, `RefreshHomeSurfaces(widget, shortcuts)`, backups, and `Ui(event)` for what the screen does once (snackbar, haptic, permission dialog, share sheet). |
| `Sub` | `app/elm/Sub.kt` | A source that sends Msgs while the model asks for it. `RestClock`, a tick per second while a rest runs and the app shows, and `HoldClock`, a tick per second while the hold timer of a timed set is open. |

The rest of `app/elm/`:

- `Update.kt`, `UpdateData.kt`, `UpdateTimer.kt`, `UpdateWod.kt`: `update` and its sub-updates, plus `follow`, which adds what every
  change brings along: `Save` when the saved state changed, Health Connect following the history,
  `RefreshHomeSurfaces` when the widget or the shortcuts would look different.
- `Selectors.kt`: what screens read besides fields (`nextRoutine`, `progressOf`, `showsWorkout`, ...).
- `HomeSurfaces.kt`: `widgetContent(state)` and `shortcutsOf(state)`, what the widget and the launcher
  shortcuts show.
- `WorkoutMachine.kt`: the workout state machine. `update` delegates `Msg.Workout(event)` to it
  (Elm's nested update). Every state lists every event, so a new event does not compile until each state
  says what it does with it.

`Env(now, newId)` is passed into `update` with every Msg. Elm would ask for the time and new ids with a
Cmd; one value is simpler here and keeps `update` pure, since a test passes its own.

## The runtime and the adapters

`app/runtime/Eisen.kt` is the only impure part of the logic. It holds the model as Compose state, runs
`update` on the main thread for each Msg passed to `dispatch`, carries out the Cmds, and starts or stops
the `Sub`s that `subscriptions` asks for. The clock and `UUID` are read only here.

| Where | Adapter |
| --- | --- |
| `app/runtime/Storage.kt` | the state file, Android preferences, backup files |
| `app/runtime/Snackbars.kt` | the one snackbar queue |
| `feature/workout/platform/` | alarms, the countdown service and notifications (`RestSystem`, `RestService`), cues and vibration (`Cues`), the alarm receiver |
| `feature/timer/platform/` | the interval timer's service, notification, voice and beeps (`TimerSystem`, `TimerSounds`) |
| `feature/health/platform/HealthSync.kt` | Health Connect |
| `feature/home/platform/`, `widget/` | the launcher shortcuts and the debounce of widget and shortcuts (`HomeSurfaces`); the widget itself (`widget/NextWorkoutWidget`) |
| `MainActivity` | the one-shot `UiEvent`s: haptics, permission dialogs, the share sheet |

An adapter does what its Cmd says and nothing else. If it needs an `if` that decides behaviour (which
shortcuts, whether a cue may beep, what an unreadable file means), the decision belongs in `update` and
travels in the Cmd. Plumbing stays in the adapter: the widget's 600 ms debounce, the phone's shortcut
limit, comparing the shortcuts against what the launcher already has.

Process entry points only translate an Android callback into a Msg: `MainActivity`, `EisenApp`,
`RestReceiver`, `RestService`, `NextWorkoutWidget`, `HealthRationaleActivity`. They are the only places
outside `app/runtime/` that touch `Eisen.model` or `Eisen.dispatch`.

## Views

A composable is a function of the model (or the slice it needs) and `dispatch: (Msg) -> Unit`, or of
narrower lambdas for leaf components. It reads no global and no clock: the time comes from `model.now`,
which `update` refreshes on `App.Foreground`, on every change of screen (`Nav`, a workout that starts,
asks to discard, or ends) and every second during a rest. Frame clocks for animations are fine.

### Model or `remember`?

- **Model**: anything that decides behaviour or has to survive. The saved data, the workout session,
  the rest, what an Undo can bring back, the tab and whether the workout shows, notices that stay
  on screen, the exercise that holds its place on the workout screen for a moment (`settling`: it
  decides the order of the list, and its timer is a `Cmd.After`).
- **`remember` / `rememberSaveable`**: pure presentation. Scroll position, animations, the text being
  typed before it is sent, a sheet whose opening has no other consequence.

If you are unsure: would a test of the feature need it? Then it belongs in the Model.

### `LocalHapticsEnabled`, the one environment value

Small presentation haptics (a stepper tick, a drag tick) fire from the view with `rememberHaptics()`.
They need the lifter's "Vibrate on taps" setting, deep inside sheets and rows that otherwise only need
a number and a callback. `MainActivity` provides it once from `model.state.settings.haptics`, the way a
theme is provided, instead of handing it through every layer. It is read-only and comes from the model,
so the view still reads no global. Haptics that belong to a behaviour (a logged set, a finished workout,
the discard warning) do not use it: they come from `update` as `UiEvent.Haptic`.

## Adding a feature

Example: a "Skip warmups" button on the workout screen.

1. **Msg**: `data object SkipWarmups : WorkoutScreen` in `Msg.kt`. Carry only what the screen knows; time
   and new ids come from `Env`.
2. **update**: in `workoutScreen` (`Update.kt`), return the new model and the Cmds:
   ```kotlin
   Msg.WorkoutScreen.SkipWarmups -> model.copy(warmupDone = allWarmupKeys(model)).only()
   ```
   (`allWarmupKeys` would be a new selector in `Selectors.kt`.)
   A Msg that makes no sense right now returns the same model and no Cmds. Saving, Health Connect and
   the widget follow by themselves in `follow`: do not add those Cmds by hand.
3. **Cmd and adapter** (only if Android has to do something): add a `Cmd` (or a `UiEvent` for something
   the screen does once) and handle it in `Eisen.execute`. The `when` is exhaustive, so the compiler
   points there. If the adapter has an answer, it dispatches a Msg.
4. **view**: `onClick = { dispatch(Msg.WorkoutScreen.SkipWarmups) }`.
5. **test**: send the Msg through `update` and assert the model and the Cmds (below).

## Testing

Everything testable goes through `update` alone. `app/src/test/.../Steps.kt` has a small driver:

```kotlin
val step = started().send(WorkoutEvent.TapSet(0, 0))      // Msg.Workout(...) through update
assertEquals(RestState(LATER, LATER + 90_000, "All reps done."), step.model.rest)
assertTrue(Cmd.ScheduleRestAlarms(LATER + 80_000, LATER + 90_000) in step.cmds)
assertIgnored(stepOf(), Msg.Rest.Extend)                  // same model back, no Cmds
```

- `WorkoutMachineTest`: the workout machine.
- `UpdateTest`: every other Msg group, `init` and `subscriptions`. It ends with a property test that
  sends thousands of random Msgs (sensible and stale ones) and checks the rules after each: `update` is
  pure, the workout belongs to the session, `Save` comes exactly when the saved state changed,
  `RefreshHomeSurfaces` exactly when the widget or the shortcuts changed, there is always a next
  workout, and routines only hold known exercises, each once.
- `HomeSurfacesTest`: what the widget and the shortcuts show.

Run `./gradlew :app:testDebugUnitTest` from `apps/android`.

## Threads

`Eisen.dispatch` may be called from any thread. It hops to the main thread and takes Msgs one after
the other in the order they arrive. On the main thread it runs at once, so the model is new when it
returns. The process can start without an activity (an alarm, the countdown service, the widget):
`EisenApp` calls `Eisen.init` before any of them, and `init` sends the first widget content, so the
widget can draw right away.

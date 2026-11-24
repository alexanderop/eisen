# Workout

A lifter starts the next workout, taps a set when it is done, rests while a clock counts down,
says how many reps were left in the tank, and finishes. The set, the rating and the finished
workout are saved to `eisen.json` as they happen.

## Sub-features

- `workout-start` opens the next workout as a running one.
- `workout-log` logs a set with a tap; the rest screen opens with the countdown.
- `workout-rest` shows the rest as a full screen, a notification and (minimised) a bar.
- `workout-rate` stores the reps in reserve for the set just logged.
- `workout-finish` files the workout in the history.

## How to get to it (user POV)

- Home tab: the button `Start workout` under the next workout. With one running: `Resume`.
- Workouts tab: a workout's own start button (not mapped yet).
- The home-screen widget and the launcher shortcut "Resume workout" (not mapped yet).

## Driving it with eisenctl

Preconditions:

- Baseline (`eisenctl launch --fresh`). No workout is running: Home shows `Start workout`.

- **Start.** `eisenctl shot 01-home && eisenctl tap "Start workout" && eisenctl wait "Finish"`. The
  screen is titled `Workout A` with `Back`, `Finish`, the tabs `Warmup` / `Workout`, and per exercise
  a line like `5×5 80kg` over five circles that each read `5`.
- **Log the first set.** `eisenctl tap "5" 1 && eisenctl wait "Set 1 of 5 done"`. The rest screen
  opens: `Hack Squat`, `Set 1 of 5 done`, `Rest`, `All reps done.`, a countdown under `of 1:30`,
  `−15s` / `+15s`, the question `How many more could you have done?` and `Up next: set 2 of 5`.
  `eisenctl shot 02-rest`.
- **The rest is real.** `eisenctl adb shell dumpsys notification --noredact | grep -A1 "android.title=String (Rest)"`
  shows the title `Rest` with the text `All reps done.`. Wait 3 seconds, `eisenctl texts`: the
  countdown is lower than in `02-rest.txt`.
- **Rate it.** `eisenctl tap "2 reps in reserve" && eisenctl shot 03-rated`.
- **Second view.** `eisenctl state 04-after-set`, then in that JSON `active.exercises[0]` has
  `"id": "hackSquat"`, `"sets": [5, null, null, null, null]` and `"rir": [2]`.
- **Minimise.** `eisenctl tap "Minimise rest timer" && eisenctl wait "Finish"`. The workout is back
  with the rest as a bar.
- **Finish early.** `eisenctl tap "Finish" && eisenctl wait "Finish early\?"`. The sheet reads
  `Finish early?`, explains that started exercises keep their weight, and offers `Finish workout`
  and `Keep going`. `eisenctl shot 05-finish-sheet && eisenctl tap "Finish workout"`.
- **Second view.** `eisenctl state 06-finished`: `active` is `null`, `history[0].name` is
  `Workout A`, and its first exercise has `"sets": [5, null, null, null, null]` and `"rir": [2]`.

## Gotchas

- All open set circles of an exercise read the target (`5`), so `tap "5" 1` is the first set of the
  first exercise only on a fresh workout. After a set is logged the indices shift: read `texts`.
- Tapping a logged set again changes its reps instead of logging the next one.
- The rest screen covers the workout. `tap "Finish"` fails until the rest is minimised or over.
- Dips are `3×max`: their circles are empty, not `5`, and a tap prefills last time's reps.
- `Finish` asks only while sets are open. With every set logged it files the workout at once.

# Workout

A lifter starts the next workout, taps a set when it is done, rests while a clock counts down,
says how many reps were left in the tank, and finishes. The set, the rating and the finished
workout are saved to `eisen.json` as they happen.

## Sub-features

- `workout-start` opens the next workout as a running one.
- `workout-log` logs a set with a tap; the rest screen opens with the countdown.
- `workout-rest` shows the rest as a full screen, a notification and (minimised) a bar.
- `workout-rate` stores the reps in reserve for the set just logged.
- `workout-finish` files the workout in the history, and Home shows `Workout saved`.

## How to get to it (user POV)

- Home tab: the button `Start workout` under the next workout. With one running: `Resume`.
- Workouts tab: a workout's own start button (not mapped yet).
- The home-screen widget and the launcher shortcut "Resume workout" (not mapped yet).

## Driving it with eisenctl

Preconditions: none. The recipe starts on fresh data (`launch --fresh`): Home offers `Workout A`.
The whole file takes about a minute.

```sh
eisenctl launch --fresh
```

**Start** (`workout-start`). The screen is titled `Workout A` with `Back`, `Finish`, the tabs
`Warmup` / `Workout`, and per exercise a line like `5×5 80kg` over five circles that each read `5`.

```sh
eisenctl shot 01-home && eisenctl tap "Start workout" && eisenctl wait "Finish"
eisenctl has "Workout A" "Warmup" "Hack Squat" "5×5 80kg"
```

**Log the first set** (`workout-log`, `workout-rest`). The rest screen opens: `Hack Squat`,
`Set 1 of 5 done`, `Rest`, `All reps done.`, a countdown under `of 1:30`, `−15s` / `+15s`, the
question `How many more could you have done?` and `Up next: set 2 of 5`. The notification is titled
`Rest` with the text `All reps done.`, and three seconds later the countdown is lower.

```sh
eisenctl tap "5" 1 && eisenctl wait "Set 1 of 5 done" && eisenctl has "Rest" "All reps done\." "of 1:30" "How many more could you have done\?" "Up next: set 2 of 5"
rest="$(eisenctl shot 02-rest)"
notice="$(eisenctl adb shell dumpsys notification --noredact)" && echo "$notice" | grep -q "android.title=String (Rest)" && echo "$notice" | grep -q "android.text=String (All reps done.)"
sleep 3 && later="$(eisenctl shot 03-rest-later)"
clock() { sed -nE 's/^text=([0-9]:[0-9]{2})[[:space:]].*/\1/p' "${1%.png}.txt" | head -1; }; [[ "$(clock "$later")" < "$(clock "$rest")" ]]
```

**Rate it** (`workout-rate`), then check the saved file: `active.exercises[0]` is the hack squat
with `"sets": [5, null, null, null, null]` and `"rir": [2]`.

```sh
eisenctl tap "2 reps in reserve" && eisenctl shot 04-rated
jq -e '.active.exercises[0] | .id == "hackSquat" and .sets == [5, null, null, null, null] and .rir == [2]' "$(eisenctl state 05-after-set)"
```

**Minimise, finish early** (`workout-rest`, `workout-finish`). The workout is back with the rest as
a bar. `Finish` asks `Finish early?`, explains that started exercises keep their weight, and offers
`Finish workout` and `Keep going`. Finished, Home shows `Workout saved` with `1 of 18 sets`, and the
history has the workout with the set and the rating.

```sh
eisenctl tap "Minimise rest timer" && eisenctl wait "Finish" && eisenctl shot 06-minimised
eisenctl tap "Finish" && eisenctl wait "Finish early\?" && eisenctl shot 07-finish-sheet && eisenctl tap "Finish workout"
eisenctl wait "Workout saved" && eisenctl has "1 of 18 sets" && eisenctl shot 08-saved
jq -e '.active == null and (.history[0] | .name == "Workout A" and .exercises[0].sets == [5, null, null, null, null] and .exercises[0].rir == [2])' "$(eisenctl state 09-finished)"
eisenctl doctor && eisenctl cleanup
```

## Gotchas

- All open set circles of an exercise read the target (`5`), so `tap "5" 1` is the first set of the
  first exercise only on a fresh workout. After a set is logged the indices shift: read `texts`.
- Tapping a logged set again changes its reps instead of logging the next one.
- The rest screen covers the workout. `tap "Finish"` fails until the rest is minimised or over.
- Dips are `3×max`: their circles are empty, not `5`, and a tap prefills last time's reps.
- `Finish` asks only while sets are open. With every set logged it files the workout at once. A
  full workout (18 sets, each covered by a rest screen) is not in the recipe.
- The rest notification's text is not on the line after its title in `dumpsys`: grep for both.

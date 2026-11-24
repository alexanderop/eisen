# Workout

A lifter starts the next workout, taps a set when it is done (or holds it to pick the reps straight
away), rests while a clock counts down, says how many reps were left in the tank, and finishes. The set, the rating and the finished
workout are saved to `eisen.json` as they happen.

## Sub-features

- `workout-start` opens the next workout as a running one.
- `workout-log` logs a set with a tap; the rest screen opens with the countdown.
- `workout-pick` holds a set to pick its reps: a row of numbers (target down to 0) pops up over it.
  Slide to one and let go, or lift and tap one. On an open set it logs that count in one step (the
  rest for missed reps below the target); on a logged set it corrects the count and leaves a running
  rest as it is. A tap outside or Back closes the row without logging. The line
  `Tap: all reps. Hold: pick the reps.` shows under the first open exercise until the first pick.
- `workout-rest` shows the rest as a full screen, a notification and (minimised) a bar. On the full
  screen a set with a target is corrected with number keys (`5 reps` … `0 reps`) beside the weight
  wheel; a max-reps set keeps its reps wheel.
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

**Pick the reps** (`workout-pick`, its own fresh run; about 40 seconds). The first card says
`Tap: all reps. Hold: pick the reps.`. Held, a set shows six radio buttons `5 reps`, `4 reps`,
`3 reps`, `2 reps`, `1 rep`, `0 reps` over it; while they are open `eisenctl texts` lists only them.
Back closes them and nothing is logged. Hold, lift, tap `3 reps`: set 1 is 3 and the rest is the one
for missed reps (`Missed reps. Rest longer.`, `of 5:00`). The rest screen's number keys correct it to
4 with one tap and the rest runs on. The hint is gone after that pick, also after the app restarts.
A slide from set 2 to `4 reps` and letting go logs 4 in one step.

```sh
eisenctl launch --fresh
eisenctl tap "Start workout" && eisenctl wait "Finish" && eisenctl has "Tap: all reps\. Hold: pick the reps\." && eisenctl shot 10-hint
eisenctl hold "5" 1 && eisenctl has "5 reps" "4 reps" "3 reps" "2 reps" "1 rep" "0 reps" && eisenctl shot 11-picker
eisenctl back && ! eisenctl has "4 reps" && jq -e '.active.exercises[0].sets == [null, null, null, null, null]' "$(eisenctl state 12-closed)"
eisenctl hold "5" 1 && eisenctl tap "3 reps" && eisenctl wait "Set 1 of 5 done" && eisenctl has "Missed reps\. Rest longer\." "of 5:00" && eisenctl shot 13-picked-3
jq -e '.pickedReps and .active.exercises[0].sets == [3, null, null, null, null]' "$(eisenctl state 14-picked-3)"
eisenctl tap "4 reps" && eisenctl has "80kg × 4" "Missed reps\. Rest longer\." && eisenctl shot 15-corrected
jq -e '.active.exercises[0].sets == [4, null, null, null, null]' "$(eisenctl state 16-corrected)"
eisenctl adb shell am force-stop io.github.alexanderop.eisen && eisenctl adb shell am start -n io.github.alexanderop.eisen/.MainActivity
eisenctl wait "Finish" && ! eisenctl has "Tap: all reps\. Hold: pick the reps\." && eisenctl shot 17-no-hint
# The slide: a press that stays down, moves over the numbers and lifts (see Gotchas).
at() { eisenctl texts | grep -E "^(text|content-desc)=($1)$(printf '\t')" | sed -n "${2:-1}p" | grep -oE '\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]$' | grep -oE '[0-9]+' | tr '\n' ' ' | awk '{ print int(($1 + $3) / 2), int(($2 + $4) / 2) }'; }
read -r x y <<< "$(at 5 1)" && eisenctl adb shell input motionevent DOWN "$x" "$y" && sleep 1
read -r kx ky <<< "$(at "4 reps")" && for i in 1 2 3 4; do eisenctl adb shell input motionevent MOVE $(( x + (kx - x) * i / 4 )) $(( y + (ky - y) * i / 4 )); done
eisenctl shot 18-sliding && eisenctl adb shell input motionevent UP "$kx" "$ky"
eisenctl wait "Set 2 of 5 done" && eisenctl has "Missed reps\. Rest longer\." && eisenctl shot 19-slid-4
jq -e '.active.exercises[0].sets == [4, 4, null, null, null]' "$(eisenctl state 20-slid-4)"
eisenctl doctor && eisenctl cleanup
```

## Gotchas

- All open set circles of an exercise read the target (`5`), so `tap "5" 1` is the first set of the
  first exercise only on a fresh workout. After a set is logged the indices shift: read `texts`.
- Tapping a logged set again changes its reps instead of logging the next one.
- `eisenctl hold` presses for a second and lifts without moving: the numbers stay open for a tap.
  A slide needs a press that stays down; `eisenctl` has no command for it, so the recipe sends
  `input motionevent DOWN`, a few `MOVE`s and `UP` through `eisenctl adb shell`, at the centres of
  the bounds `texts` prints. `texts` works while the finger is down.
- The numbers are radio buttons whose description is the handle: `4 reps`, but `1 rep`. They do not
  clash with the rest screen's `4 reps in reserve`: handles match whole.
- The hint goes with the first pick for good (`pickedReps` in `eisen.json`): only `--fresh` brings it back.
- Holding a max-reps set opens the reps question (`Save set`); holding a timed set is a tap (the hold timer).
  TalkBack offers `Set reps` as a custom action on a set, and the long press says `Pick the reps`.
- The rest screen covers the workout. `tap "Finish"` fails until the rest is minimised or over.
- Dips are `3×max`: their circles are empty, not `5`, and a tap prefills last time's reps.
- `Finish` asks only while sets are open. With every set logged it files the workout at once. A
  full workout (18 sets, each covered by a rest screen) is not in the recipe.
- The rest notification's text is not on the line after its title in `dumpsys`: grep for both.

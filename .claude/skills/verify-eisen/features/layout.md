# Layout at the extremes

What a lifter needs on each main screen is on screen and big enough to hit, from the default font to the
largest (and the chips "What was it?" never push the result of a timer off its screen): the system font scale at 1.0, 1.3 and 2.0, on the emulator's 1080 × 2400 at 420 dpi. Bugs of this
kind only show on a phone set up that way (QA build 19: at 1.8 and 2.0 the rest screen lost its countdown
and its −15s / +15s; at 1.5 the countdown was smaller than the text around it).

## Sub-features

- `layout-visible` every screen shows what it is for: its title or main action, its controls, the tabs.
- `layout-targets` nothing on a screen can be tapped on less than 48 dp either way (`eisenctl small`).
- `layout-food-icons` the pictures of the foods next to their names (the add screen's rows, the plate, the day's entries,
  the "Again" chips) and the 64 dp picture on top of an item's add sheet, which is a button of 48 dp or more: no name loses
  its room to a picture.
- `layout-food` the "Food today" card under the workout, which never pushes "Start workout" off the first screen,
  the Food tab with its week strip, its summary (calories, protein, carbs, fat) and its buttons, the add screen with
  its common foods and the plate pinned at the bottom, the add sheet of an item of the plate with its chips, grams and
  "Save" button, the sheet of a new meal ("Save as meal"), and the Food section of History › Progress with its seven
  bars, each a tap target. Settings is reached by the gear on Home.
- `layout-weight` the weekly check-in on top of the Food tab with Take and Keep, the weight card with `Log weight`, the
  weight sheet with its day, its number and Save, and the Weight section of History › Progress with its range, chart, three
  numbers and `Log weight`.
- `layout-food-label` the add screen's `NO BARCODE?` with `Photo of the label`, the sheet with `Take a photo` and `Choose a
  photo`, and the own food form a photo filled: each value with its mark (`read`) beside its label, the steppers, the serving
  and `Save food`.
- `layout-rest-clock` the rest screen's countdown keeps its room and stays at least three times as tall as
  the text beside it; what is below it scrolls, "Next set" stays at the bottom.

## How to get to it (user POV)

Android Settings › Display › Font size, or `eisenctl font <scale>` (put back by `cleanup`). The screens:
Home with the gear to Settings, the "Food today" card below the workout, the Food tab, the add screen with a banana on
its plate, the add sheet of that item of the plate, the new meal sheet of it once logged, the week of food in History › Progress, a running workout, the rest screen, the hold timer (a plank added to the workout), an interval timer
running, a boxing run, the WOD builder, History, Settings, the result of an interval timer with its chips
"What was it?", and the sheet with the same chips over History's log.

## Driving it with eisenctl

Preconditions: `--state short-timers` (its quick timers). One recipe per font scale; each walks all eleven
screens and checks each with `visible` (the handles on screen, not cut off, tapped on at least 48 dp) and
`small` (no clickable node of the screen under 48 dp). About four minutes each. The weight log has a recipe of its own on
`--state food-weight` (a check-in due), which goes through the three scales one after the other.

**At font scale 1.0** (`layout-visible`, `layout-targets`, `layout-rest-clock`, `layout-food`, `layout-food-icons`).

```sh
eisenctl launch --state short-timers
eisenctl font 1.0
eisenctl visible "Start workout" "Settings" "Home" "Workouts" "Timer" "Food" "History" && eisenctl small && eisenctl shot 11-home
for i in 1 2 3 4; do eisenctl visible "FOOD TODAY" "\+ Add" 2> /dev/null && break; eisenctl scroll down; done
eisenctl visible "FOOD TODAY" "0 kcal eaten" "Scan" "\+ Add" && eisenctl small && eisenctl shot 11b-home-food
eisenctl tap "FOOD TODAY" && eisenctl wait "0 kcal eaten" && eisenctl visible "Today" "Previous week" ".*, today" "Food data" "0 kcal eaten" "Scan" "\+ Add" "Quick kcal" "Food" && eisenctl small && eisenctl shot 11c-food
for i in 1 2 3; do eisenctl visible "Set a goal" 2> /dev/null && break; eisenctl scroll down; done
eisenctl visible "Set a goal" "Carbs 0 g" "Fat 0 g" && eisenctl small
eisenctl tap "\+ Add" && eisenctl wait "Search foods" && eisenctl visible "Back" "Add food" "Search foods" "Scan" "COMMON FOODS" "Put Oats, rolled on the plate" && eisenctl small && eisenctl shot 11d-add
for i in 1 2 3; do eisenctl visible "Put Banana on the plate" 2> /dev/null && break; eisenctl scroll down; done
eisenctl tap "Put Banana on the plate" && eisenctl wait "Log 105 kcal" && eisenctl visible "Plate, 1 food · 105 kcal" "Clear" "Log 105 kcal" && eisenctl has "Take Banana off the plate" && eisenctl small && eisenctl shot 11e-add-plate
eisenctl tap "Plate, 1 food · 105 kcal" && eisenctl wait "Banana, 118 g, 105 kcal" && eisenctl visible "Banana, 118 g, 105 kcal" "Log 105 kcal" && eisenctl small
eisenctl tap "Banana, 118 g, 105 kcal" && eisenctl wait "Save 105 kcal" && eisenctl visible "Banana" "Picture: Banana" "89 kcal · 1.1 g protein" "½ = 59 g" "1 serving 118 g" "2 = 236 g" "amount 118 g" "Save 105 kcal" && eisenctl small && eisenctl shot 11f-plate-item
eisenctl tap "Save 105 kcal" && eisenctl wait "Log 105 kcal" && eisenctl tap "Log 105 kcal" && eisenctl wait "Logged 1 food"
for i in 1 2 3 4; do eisenctl visible "Save as meal" 2> /dev/null && break; eisenctl scroll down; done
eisenctl tap "Save as meal" && eisenctl wait "Save meal"
eisenctl visible "New meal" "Name of the meal" "Banana" "1 food · 105 kcal · 1 g protein" "Save meal" "Close" && eisenctl small && eisenctl shot 11g-meal-sheet
eisenctl tap "Close" && eisenctl wait "Save as meal" && eisenctl tap "Home" && eisenctl wait "Start workout" && eisenctl scroll up
eisenctl tap "Start workout" && eisenctl wait "Finish" && eisenctl visible "Back" "Finish" "More options" "Hack Squat" && eisenctl small && eisenctl shot 12-workout
eisenctl tap "5" 1 && eisenctl wait "Set 1 of 5 done" && eisenctl visible "[0-9]:[0-9]{2}" "Rest 15 seconds less" "Rest 15 seconds longer" "Minimise rest timer" "Next set" && eisenctl small
eisenctl texts | awk -F'\t' '/^text=[0-9]:[0-9][0-9]\t/ { split($2, c, /[^0-9]+/); clock = c[5] - c[3] } /^text=of [0-9]:[0-9][0-9]\t/ { split($2, c, /[^0-9]+/); body = c[5] - c[3] } END { exit !(clock >= 3 * body) }'
eisenctl shot 13-rest && eisenctl tap "Minimise rest timer" && eisenctl wait "Finish"
for i in 1 2 3 4 5 6; do eisenctl has "Add exercise" 2> /dev/null && break; eisenctl scroll down; done
eisenctl tap "Add exercise" && eisenctl tap "Search exercises" && eisenctl type "plank" && eisenctl wait "Plank" && eisenctl tap "Plank"
for i in 1 2 3 4; do eisenctl has "1:00" 2> /dev/null && break; eisenctl scroll down; done
eisenctl tap "1:00" 1 && eisenctl wait "GET READY" && eisenctl visible "Plank" "Set 1 of 3" "Tap to start now" "Cancel" && eisenctl small && eisenctl shot 14-hold-timer
eisenctl tap "Cancel" && eisenctl tap "More options" && eisenctl tap "Discard workout" && eisenctl wait "Discard this workout\?" && eisenctl tap "Discard workout" && eisenctl wait "Start workout"
eisenctl tap "Timer" && eisenctl tap "Timers" && for i in 1 2 3 4; do eisenctl has "Start Quick Tabata" 2> /dev/null && break; eisenctl scroll down; done
eisenctl tap "Start Quick Tabata" && eisenctl wait "WORK" 15 && eisenctl visible "End" "WORK" "[0-9]:[0-9]{2}" "Pause" && eisenctl small && eisenctl shot 15-timer-run
eisenctl tap "End" && eisenctl wait "Discard" && eisenctl tap "Discard" && eisenctl wait "Back to the timers" && eisenctl tap "Back to the timers"
for i in 1 2 3 4; do eisenctl has "Start Quick boxing" 2> /dev/null && break; eisenctl scroll down; done
eisenctl tap "Start Quick boxing" && eisenctl wait "ROUND 1/1.*" 15 && eisenctl visible "End" "ROUND 1/1.*" && eisenctl small && eisenctl shot 16-boxing-run
eisenctl tap "End" && eisenctl wait "Discard" && eisenctl tap "Discard" && eisenctl wait "Back to the timers" && eisenctl tap "Back to the timers"
eisenctl tap "WODs" && eisenctl tap "New WOD" && eisenctl wait "Name your WOD" && eisenctl visible "Name your WOD" "For Time" "AMRAP" "EMOM" "Tabata" && eisenctl small && eisenctl shot 17-wod-builder
eisenctl back && eisenctl tap "History" && eisenctl wait "Log" && eisenctl visible "Log" "Progress" "History" && eisenctl small && eisenctl shot 18-history
eisenctl tap "Progress" && eisenctl wait "Last 7 days" && for i in 1 2 3; do eisenctl has "Avg kcal" 2> /dev/null && break; eisenctl scroll down; done
eisenctl visible "Food" "Last 7 days" "[A-Z][a-z]{2}, 105 kcal, 1 g protein" "Avg kcal" "105" && eisenctl small && eisenctl shot 18b-progress-food && eisenctl scroll up && eisenctl tap "Log"
eisenctl tap "Home" && eisenctl wait "Start workout" && eisenctl tap "Settings" && eisenctl wait "Weights" && eisenctl visible "Back" "Unit" "Bar weight" "Settings" && eisenctl small && eisenctl shot 19-settings && eisenctl back && eisenctl wait "Start workout"
eisenctl tap "Timer" && eisenctl tap "Timers" && for i in 1 2 3 4; do eisenctl has "Start Quick Tabata" 2> /dev/null && break; eisenctl scroll down; done
eisenctl tap "Start Quick Tabata" && eisenctl wait "Completed" 45 && eisenctl visible "Quick Tabata" "Completed" "What was it\?" "Boxing" "Running" "Again" "Done" "Don't count this run" && eisenctl small && eisenctl shot 20-timer-result
sleep 1 && eisenctl tap "Done" && eisenctl tap "History" && eisenctl wait "Log" && eisenctl tap "Quick Tabata" && eisenctl wait "What was it\?" && eisenctl visible "What was it\?" "Boxing" "Running" "Done" "Close" && eisenctl small && eisenctl shot 20b-history-sheet
eisenctl back && eisenctl wait "Log"
eisenctl doctor && eisenctl cleanup
```

**At font scale 1.3** (`layout-visible`, `layout-targets`, `layout-rest-clock`, `layout-food`, `layout-food-icons`).

```sh
eisenctl launch --state short-timers
eisenctl font 1.3
eisenctl visible "Start workout" "Settings" "Home" "Workouts" "Timer" "Food" "History" && eisenctl small && eisenctl shot 21-home
for i in 1 2 3 4; do eisenctl visible "FOOD TODAY" "\+ Add" 2> /dev/null && break; eisenctl scroll down; done
eisenctl visible "FOOD TODAY" "0 kcal eaten" "Scan" "\+ Add" && eisenctl small && eisenctl shot 21b-home-food
eisenctl tap "FOOD TODAY" && eisenctl wait "0 kcal eaten" && eisenctl visible "Today" "Previous week" ".*, today" "Food data" "0 kcal eaten" "Scan" "\+ Add" "Quick kcal" "Food" && eisenctl small && eisenctl shot 21c-food
for i in 1 2 3; do eisenctl visible "Set a goal" 2> /dev/null && break; eisenctl scroll down; done
eisenctl visible "Set a goal" "Carbs 0 g" "Fat 0 g" && eisenctl small
eisenctl tap "\+ Add" && eisenctl wait "Search foods" && eisenctl visible "Back" "Add food" "Search foods" "Scan" "COMMON FOODS" "Put Oats, rolled on the plate" && eisenctl small && eisenctl shot 21d-add
for i in 1 2 3; do eisenctl visible "Put Banana on the plate" 2> /dev/null && break; eisenctl scroll down; done
eisenctl tap "Put Banana on the plate" && eisenctl wait "Log 105 kcal" && eisenctl visible "Plate, 1 food · 105 kcal" "Clear" "Log 105 kcal" && eisenctl has "Take Banana off the plate" && eisenctl small && eisenctl shot 21e-add-plate
eisenctl tap "Plate, 1 food · 105 kcal" && eisenctl wait "Banana, 118 g, 105 kcal" && eisenctl visible "Banana, 118 g, 105 kcal" "Log 105 kcal" && eisenctl small
eisenctl tap "Banana, 118 g, 105 kcal" && eisenctl wait "Save 105 kcal" && eisenctl visible "Banana" "Picture: Banana" "89 kcal · 1.1 g protein" "½ = 59 g" "1 serving 118 g" "2 = 236 g" "amount 118 g" "Save 105 kcal" && eisenctl small && eisenctl shot 21f-plate-item
eisenctl tap "Save 105 kcal" && eisenctl wait "Log 105 kcal" && eisenctl tap "Log 105 kcal" && eisenctl wait "Logged 1 food"
for i in 1 2 3 4; do eisenctl visible "Save as meal" 2> /dev/null && break; eisenctl scroll down; done
eisenctl tap "Save as meal" && eisenctl wait "Save meal"
eisenctl visible "New meal" "Name of the meal" "Banana" "1 food · 105 kcal · 1 g protein" "Save meal" "Close" && eisenctl small && eisenctl shot 21g-meal-sheet
eisenctl tap "Close" && eisenctl wait "Save as meal" && eisenctl tap "Home" && eisenctl wait "Start workout" && eisenctl scroll up
eisenctl tap "Start workout" && eisenctl wait "Finish" && eisenctl visible "Back" "Finish" "More options" "Hack Squat" && eisenctl small && eisenctl shot 22-workout
eisenctl tap "5" 1 && eisenctl wait "Set 1 of 5 done" && eisenctl visible "[0-9]:[0-9]{2}" "Rest 15 seconds less" "Rest 15 seconds longer" "Minimise rest timer" "Next set" && eisenctl small
eisenctl texts | awk -F'\t' '/^text=[0-9]:[0-9][0-9]\t/ { split($2, c, /[^0-9]+/); clock = c[5] - c[3] } /^text=of [0-9]:[0-9][0-9]\t/ { split($2, c, /[^0-9]+/); body = c[5] - c[3] } END { exit !(clock >= 3 * body) }'
eisenctl shot 23-rest && eisenctl tap "Minimise rest timer" && eisenctl wait "Finish"
for i in 1 2 3 4 5 6; do eisenctl has "Add exercise" 2> /dev/null && break; eisenctl scroll down; done
eisenctl tap "Add exercise" && eisenctl tap "Search exercises" && eisenctl type "plank" && eisenctl wait "Plank" && eisenctl tap "Plank"
for i in 1 2 3 4; do eisenctl has "1:00" 2> /dev/null && break; eisenctl scroll down; done
eisenctl tap "1:00" 1 && eisenctl wait "GET READY" && eisenctl visible "Plank" "Set 1 of 3" "Tap to start now" "Cancel" && eisenctl small && eisenctl shot 24-hold-timer
eisenctl tap "Cancel" && eisenctl tap "More options" && eisenctl tap "Discard workout" && eisenctl wait "Discard this workout\?" && eisenctl tap "Discard workout" && eisenctl wait "Start workout"
eisenctl tap "Timer" && eisenctl tap "Timers" && for i in 1 2 3 4; do eisenctl has "Start Quick Tabata" 2> /dev/null && break; eisenctl scroll down; done
eisenctl tap "Start Quick Tabata" && eisenctl wait "WORK" 15 && eisenctl visible "End" "WORK" "[0-9]:[0-9]{2}" "Pause" && eisenctl small && eisenctl shot 25-timer-run
eisenctl tap "End" && eisenctl wait "Discard" && eisenctl tap "Discard" && eisenctl wait "Back to the timers" && eisenctl tap "Back to the timers"
for i in 1 2 3 4; do eisenctl has "Start Quick boxing" 2> /dev/null && break; eisenctl scroll down; done
eisenctl tap "Start Quick boxing" && eisenctl wait "ROUND 1/1.*" 15 && eisenctl visible "End" "ROUND 1/1.*" && eisenctl small && eisenctl shot 26-boxing-run
eisenctl tap "End" && eisenctl wait "Discard" && eisenctl tap "Discard" && eisenctl wait "Back to the timers" && eisenctl tap "Back to the timers"
eisenctl tap "WODs" && eisenctl tap "New WOD" && eisenctl wait "Name your WOD" && eisenctl visible "Name your WOD" "For Time" "AMRAP" "EMOM" "Tabata" && eisenctl small && eisenctl shot 27-wod-builder
eisenctl back && eisenctl tap "History" && eisenctl wait "Log" && eisenctl visible "Log" "Progress" "History" && eisenctl small && eisenctl shot 28-history
eisenctl tap "Progress" && eisenctl wait "Last 7 days" && for i in 1 2 3; do eisenctl has "Avg kcal" 2> /dev/null && break; eisenctl scroll down; done
eisenctl visible "Food" "Last 7 days" "[A-Z][a-z]{2}, 105 kcal, 1 g protein" "Avg kcal" "105" && eisenctl small && eisenctl shot 28b-progress-food && eisenctl scroll up && eisenctl tap "Log"
eisenctl tap "Home" && eisenctl wait "Start workout" && eisenctl tap "Settings" && eisenctl wait "Weights" && eisenctl visible "Back" "Unit" "Bar weight" "Settings" && eisenctl small && eisenctl shot 29-settings && eisenctl back && eisenctl wait "Start workout"
eisenctl tap "Timer" && eisenctl tap "Timers" && for i in 1 2 3 4; do eisenctl has "Start Quick Tabata" 2> /dev/null && break; eisenctl scroll down; done
eisenctl tap "Start Quick Tabata" && eisenctl wait "Completed" 45 && eisenctl visible "Quick Tabata" "Completed" "What was it\?" "Boxing" "Running" "Again" "Done" "Don't count this run" && eisenctl small && eisenctl shot 30-timer-result
sleep 1 && eisenctl tap "Done" && eisenctl tap "History" && eisenctl wait "Log" && eisenctl tap "Quick Tabata" && eisenctl wait "What was it\?" && eisenctl visible "What was it\?" "Boxing" "Running" "Done" "Close" && eisenctl small && eisenctl shot 30b-history-sheet
eisenctl back && eisenctl wait "Log"
eisenctl doctor && eisenctl cleanup
```

**At font scale 2.0** (`layout-visible`, `layout-targets`, `layout-rest-clock`, `layout-food`, `layout-food-icons`), the largest Android offers.

```sh
eisenctl launch --state short-timers
eisenctl font 2.0
eisenctl visible "Start workout" "Settings" "Home" "Workouts" "Timer" "Food" "History" && eisenctl small && eisenctl shot 31-home
for i in 1 2 3 4; do eisenctl visible "FOOD TODAY" "\+ Add" 2> /dev/null && break; eisenctl scroll down; done
eisenctl visible "FOOD TODAY" "0 kcal eaten" "Scan" "\+ Add" && eisenctl small && eisenctl shot 31b-home-food
eisenctl tap "FOOD TODAY" && eisenctl wait "0 kcal eaten" && eisenctl visible "Today" "Previous week" ".*, today" "Food data" "0 kcal eaten" "Scan" "\+ Add" "Quick kcal" "Food" && eisenctl small && eisenctl shot 31c-food
for i in 1 2 3; do eisenctl visible "Set a goal" 2> /dev/null && break; eisenctl scroll down; done
eisenctl visible "Set a goal" "Carbs 0 g" "Fat 0 g" && eisenctl small
eisenctl tap "\+ Add" && eisenctl wait "Search foods" && eisenctl visible "Back" "Add food" "Search foods" "Scan" "COMMON FOODS" "Put Oats, rolled on the plate" && eisenctl small && eisenctl shot 31d-add
for i in 1 2 3; do eisenctl visible "Put Banana on the plate" 2> /dev/null && break; eisenctl scroll down; done
eisenctl tap "Put Banana on the plate" && eisenctl wait "Log 105 kcal" && eisenctl visible "Plate, 1 food · 105 kcal" "Clear" "Log 105 kcal" && eisenctl has "Take Banana off the plate" && eisenctl small && eisenctl shot 31e-add-plate
eisenctl tap "Plate, 1 food · 105 kcal" && eisenctl wait "Banana, 118 g, 105 kcal" && eisenctl visible "Banana, 118 g, 105 kcal" "Log 105 kcal" && eisenctl small
eisenctl tap "Banana, 118 g, 105 kcal" && eisenctl wait "Save 105 kcal" && eisenctl visible "Banana" "Picture: Banana" "89 kcal · 1.1 g protein" "½ = 59 g" "1 serving 118 g" "2 = 236 g" "amount 118 g" "Save 105 kcal" && eisenctl small && eisenctl shot 31f-plate-item
eisenctl tap "Save 105 kcal" && eisenctl wait "Log 105 kcal" && eisenctl tap "Log 105 kcal" && eisenctl wait "Logged 1 food"
for i in 1 2 3 4; do eisenctl visible "Save as meal" 2> /dev/null && break; eisenctl scroll down; done
eisenctl tap "Save as meal" && eisenctl wait "Save meal"
eisenctl visible "New meal" "Name of the meal" "Banana" "1 food · 105 kcal · 1 g protein" "Save meal" "Close" && eisenctl small && eisenctl shot 31g-meal-sheet
eisenctl tap "Close" && eisenctl wait "Save as meal" && eisenctl tap "Home" && eisenctl wait "Start workout" && eisenctl scroll up
eisenctl tap "Start workout" && eisenctl wait "Finish" && eisenctl visible "Back" "Finish" "More options" "Hack Squat" && eisenctl small && eisenctl shot 32-workout
eisenctl tap "5" 1 && eisenctl wait "Set 1 of 5 done" && eisenctl visible "[0-9]:[0-9]{2}" "Rest 15 seconds less" "Rest 15 seconds longer" "Minimise rest timer" "Next set" && eisenctl small
eisenctl texts | awk -F'\t' '/^text=[0-9]:[0-9][0-9]\t/ { split($2, c, /[^0-9]+/); clock = c[5] - c[3] } /^text=of [0-9]:[0-9][0-9]\t/ { split($2, c, /[^0-9]+/); body = c[5] - c[3] } END { exit !(clock >= 3 * body) }'
eisenctl shot 33-rest && eisenctl tap "Minimise rest timer" && eisenctl wait "Finish"
for i in 1 2 3 4 5 6; do eisenctl has "Add exercise" 2> /dev/null && break; eisenctl scroll down; done
eisenctl tap "Add exercise" && eisenctl tap "Search exercises" && eisenctl type "plank" && eisenctl wait "Plank" && eisenctl tap "Plank"
for i in 1 2 3 4; do eisenctl has "1:00" 2> /dev/null && break; eisenctl scroll down; done
eisenctl tap "1:00" 1 && eisenctl wait "GET READY" && eisenctl visible "Plank" "Set 1 of 3" "Tap to start now" "Cancel" && eisenctl small && eisenctl shot 34-hold-timer
eisenctl tap "Cancel" && eisenctl tap "More options" && eisenctl tap "Discard workout" && eisenctl wait "Discard this workout\?" && eisenctl tap "Discard workout" && eisenctl wait "Start workout"
eisenctl tap "Timer" && eisenctl tap "Timers" && for i in 1 2 3 4; do eisenctl has "Start Quick Tabata" 2> /dev/null && break; eisenctl scroll down; done
eisenctl tap "Start Quick Tabata" && eisenctl wait "WORK" 15 && eisenctl visible "End" "WORK" "[0-9]:[0-9]{2}" "Pause" && eisenctl small && eisenctl shot 35-timer-run
eisenctl tap "End" && eisenctl wait "Discard" && eisenctl tap "Discard" && eisenctl wait "Back to the timers" && eisenctl tap "Back to the timers"
for i in 1 2 3 4; do eisenctl has "Start Quick boxing" 2> /dev/null && break; eisenctl scroll down; done
eisenctl tap "Start Quick boxing" && eisenctl wait "ROUND 1/1.*" 15 && eisenctl visible "End" "ROUND 1/1.*" && eisenctl small && eisenctl shot 36-boxing-run
eisenctl tap "End" && eisenctl wait "Discard" && eisenctl tap "Discard" && eisenctl wait "Back to the timers" && eisenctl tap "Back to the timers"
eisenctl tap "WODs" && eisenctl tap "New WOD" && eisenctl wait "Name your WOD" && eisenctl visible "Name your WOD" "For Time" "AMRAP" "EMOM" "Tabata" && eisenctl small && eisenctl shot 37-wod-builder
eisenctl back && eisenctl tap "History" && eisenctl wait "Log" && eisenctl visible "Log" "Progress" "History" && eisenctl small && eisenctl shot 38-history
eisenctl tap "Progress" && eisenctl wait "Last 7 days" && for i in 1 2 3; do eisenctl has "Avg kcal" 2> /dev/null && break; eisenctl scroll down; done
eisenctl visible "Food" "Last 7 days" "[A-Z][a-z]{2}, 105 kcal, 1 g protein" "Avg kcal" "105" && eisenctl small && eisenctl shot 38b-progress-food && eisenctl scroll up && eisenctl tap "Log"
eisenctl tap "Home" && eisenctl wait "Start workout" && eisenctl tap "Settings" && eisenctl wait "Weights" && eisenctl visible "Back" "Unit" "Bar weight" "Settings" && eisenctl small && eisenctl shot 39-settings && eisenctl back && eisenctl wait "Start workout"
eisenctl tap "Timer" && eisenctl tap "Timers" && for i in 1 2 3 4; do eisenctl has "Start Quick Tabata" 2> /dev/null && break; eisenctl scroll down; done
eisenctl tap "Start Quick Tabata" && eisenctl wait "Completed" 45 && eisenctl visible "Quick Tabata" "Completed" "What was it\?" "Boxing" "Running" "Again" "Done" "Don't count this run" && eisenctl small && eisenctl shot 40-timer-result
sleep 1 && eisenctl tap "Done" && eisenctl tap "History" && eisenctl wait "Log" && eisenctl tap "Quick Tabata" && eisenctl wait "What was it\?" && eisenctl visible "What was it\?" "Boxing" "Running" "Done" "Close" && eisenctl small && eisenctl shot 40b-history-sheet
eisenctl back && eisenctl wait "Log"
eisenctl doctor && eisenctl cleanup
```

**The weight log at font scale 1.0, 1.3 and 2.0** (`layout-weight`, `layout-visible`, `layout-targets`). The check-in
is shown once and stays unanswered, so it shows at every scale; at 2.0 the screen scrolls to its buttons.

```sh
eisenctl launch --state food-weight
# At 1.0
eisenctl font 1.0
eisenctl tap "Food" && eisenctl wait "Food data" && eisenctl scroll up && eisenctl scroll up && eisenctl visible "WEEKLY CHECK-IN" && eisenctl small && eisenctl shot 61a-check-in-1.0
for i in 1 2 3; do eisenctl visible "New target: 2,200 kcal \(was 2,400\)" "Take 2,200 kcal" "Keep 2,400 kcal" 2> /dev/null && break; eisenctl scroll down; done
eisenctl visible "New target: 2,200 kcal \(was 2,400\)" "Take 2,200 kcal" "Keep 2,400 kcal" && eisenctl small && eisenctl shot 61b-check-in-buttons-1.0
for i in 1 2 3 4 5; do eisenctl visible "Log weight" 2> /dev/null && break; eisenctl scroll down; done
eisenctl visible "WEIGHT" "Weight trend 83.3 kg.*" "Log weight" && eisenctl small && eisenctl shot 61c-weight-card-1.0
eisenctl tap "Log weight" && eisenctl wait "Save 82.4 kg" && eisenctl visible "Weight" "Previous day" "Today" "weight 82.4 kg" "Save 82.4 kg" && eisenctl small && eisenctl shot 61d-weight-sheet-1.0
eisenctl tap "Close" && eisenctl wait "Log weight" && eisenctl tap "History" && eisenctl tap "Progress"
for i in 1 2 3 4; do eisenctl visible "Log weight" 2> /dev/null && break; eisenctl scroll down; done
eisenctl visible "Log weight" && eisenctl has "Trend" "Change" "You burn" && eisenctl small && eisenctl shot 61e-progress-weight-1.0
for i in 1 2 3; do eisenctl visible "3 months" "1 year" "All" 2> /dev/null && break; eisenctl scroll up; done
eisenctl visible "3 months" "1 year" "All" && eisenctl has "Weight over time, 22 weights, .*" && eisenctl small && eisenctl shot 61f-progress-chart-1.0
eisenctl tap "Home" && eisenctl wait "Start workout"
# At 1.3
eisenctl font 1.3
eisenctl tap "Food" && eisenctl wait "Food data" && eisenctl scroll up && eisenctl scroll up && eisenctl visible "WEEKLY CHECK-IN" && eisenctl small && eisenctl shot 62a-check-in-1.3
for i in 1 2 3; do eisenctl visible "New target: 2,200 kcal \(was 2,400\)" "Take 2,200 kcal" "Keep 2,400 kcal" 2> /dev/null && break; eisenctl scroll down; done
eisenctl visible "New target: 2,200 kcal \(was 2,400\)" "Take 2,200 kcal" "Keep 2,400 kcal" && eisenctl small && eisenctl shot 62b-check-in-buttons-1.3
for i in 1 2 3 4 5; do eisenctl visible "Log weight" 2> /dev/null && break; eisenctl scroll down; done
eisenctl visible "WEIGHT" "Weight trend 83.3 kg.*" "Log weight" && eisenctl small && eisenctl shot 62c-weight-card-1.3
eisenctl tap "Log weight" && eisenctl wait "Save 82.4 kg" && eisenctl visible "Weight" "Previous day" "Today" "weight 82.4 kg" "Save 82.4 kg" && eisenctl small && eisenctl shot 62d-weight-sheet-1.3
eisenctl tap "Close" && eisenctl wait "Log weight" && eisenctl tap "History" && eisenctl tap "Progress"
for i in 1 2 3 4; do eisenctl visible "Log weight" 2> /dev/null && break; eisenctl scroll down; done
eisenctl visible "Log weight" && eisenctl has "Trend" "Change" "You burn" && eisenctl small && eisenctl shot 62e-progress-weight-1.3
for i in 1 2 3; do eisenctl visible "3 months" "1 year" "All" 2> /dev/null && break; eisenctl scroll up; done
eisenctl visible "3 months" "1 year" "All" && eisenctl has "Weight over time, 22 weights, .*" && eisenctl small && eisenctl shot 62f-progress-chart-1.3
eisenctl tap "Home" && eisenctl wait "Start workout"
# At 2.0
eisenctl font 2.0
eisenctl tap "Food" && eisenctl wait "Food data" && eisenctl scroll up && eisenctl scroll up && eisenctl visible "WEEKLY CHECK-IN" && eisenctl small && eisenctl shot 63a-check-in-2.0
for i in 1 2 3; do eisenctl visible "New target: 2,200 kcal \(was 2,400\)" "Take 2,200 kcal" "Keep 2,400 kcal" 2> /dev/null && break; eisenctl scroll down; done
eisenctl visible "New target: 2,200 kcal \(was 2,400\)" "Take 2,200 kcal" "Keep 2,400 kcal" && eisenctl small && eisenctl shot 63b-check-in-buttons-2.0
for i in 1 2 3 4 5; do eisenctl visible "Log weight" 2> /dev/null && break; eisenctl scroll down; done
eisenctl visible "WEIGHT" "Weight trend 83.3 kg.*" "Log weight" && eisenctl small && eisenctl shot 63c-weight-card-2.0
eisenctl tap "Log weight" && eisenctl wait "Save 82.4 kg" && eisenctl visible "Weight" "Previous day" "Today" "weight 82.4 kg" "Save 82.4 kg" && eisenctl small && eisenctl shot 63d-weight-sheet-2.0
eisenctl tap "Close" && eisenctl wait "Log weight" && eisenctl tap "History" && eisenctl tap "Progress"
for i in 1 2 3 4; do eisenctl visible "Log weight" 2> /dev/null && break; eisenctl scroll down; done
eisenctl visible "Log weight" && eisenctl has "Trend" "Change" "You burn" && eisenctl small && eisenctl shot 63e-progress-weight-2.0
for i in 1 2 3; do eisenctl visible "3 months" "1 year" "All" 2> /dev/null && break; eisenctl scroll up; done
eisenctl visible "3 months" "1 year" "All" && eisenctl has "Weight over time, 22 weights, .*" && eisenctl small && eisenctl shot 63f-progress-chart-2.0
eisenctl tap "Home" && eisenctl wait "Start workout"
eisenctl doctor && eisenctl cleanup
```

**The own food form filled from a photo of a label, at font scale 1.0, 1.3 and 2.0** (`layout-food-label`,
`layout-visible`, `layout-targets`). The ice cream's label (`fixtures/labels/vanille-eis.jpg`) picked through the photo picker
at each scale, as in the `food-label` recipe of `food.md`; the keyboard put away, the form's top and its bottom checked.

```sh
eisenctl launch --state food
eisenctl adb shell "rm -f /sdcard/Pictures/eisen-label-*.jpg" && eisenctl adb push .claude/skills/verify-eisen/fixtures/labels/vanille-eis.jpg /sdcard/Pictures/eisen-label-vanille-eis.jpg > /dev/null && eisenctl adb shell content call --uri content://media --method scan_volume --arg external_primary > /dev/null
# At 1.0
eisenctl font 1.0
eisenctl tap "Food" && eisenctl wait "2,400 kcal left" && eisenctl tap "\+ Add" && eisenctl wait "Search foods"
for i in 1 2 3 4 5 6; do eisenctl visible "Photo of the label" 2> /dev/null && break; eisenctl scroll down; done
eisenctl visible "NO BARCODE\?" "Photo of the label" && eisenctl small && eisenctl shot 64a-label-entry-1.0
eisenctl tap "Photo of the label" && eisenctl wait "Choose a photo" && eisenctl visible "Take a photo" "Choose a photo" "Close" && eisenctl small && eisenctl shot 64b-label-choice-1.0
eisenctl tap "Choose a photo" && eisenctl wait "Photo taken on .*" && eisenctl tap "Photo taken on .*" 1 && eisenctl wait "Calories read from the photo" 20 && eisenctl back
eisenctl visible "New food" "Close" "Read another photo" "Calories read from the photo" "calories per 100 g 200 kcal" && eisenctl small && eisenctl shot 64c-label-form-1.0
for i in 1 2 3 4; do eisenctl visible "Fat read from the photo" "fat per 100 g 10.1 g" "Save food" 2> /dev/null && break; eisenctl scroll down; done
eisenctl visible "Protein read from the photo|Carbs read from the photo" "Fat read from the photo" "fat per 100 g 10.1 g" "serving 50 g" "Save food" && eisenctl small && eisenctl shot 64d-label-form-below-1.0
eisenctl tap "Close" && eisenctl wait "Search foods" && eisenctl tap "Back" && eisenctl tap "Home" && eisenctl wait "Start workout"
# At 1.3
eisenctl font 1.3
eisenctl tap "Food" && eisenctl wait "2,400 kcal left" && eisenctl tap "\+ Add" && eisenctl wait "Search foods"
for i in 1 2 3 4 5 6; do eisenctl visible "Photo of the label" 2> /dev/null && break; eisenctl scroll down; done
eisenctl visible "NO BARCODE\?" "Photo of the label" && eisenctl small && eisenctl shot 65a-label-entry-1.3
eisenctl tap "Photo of the label" && eisenctl wait "Choose a photo" && eisenctl visible "Take a photo" "Choose a photo" "Close" && eisenctl small && eisenctl shot 65b-label-choice-1.3
eisenctl tap "Choose a photo" && eisenctl wait "Photo taken on .*" && eisenctl tap "Photo taken on .*" 1 && eisenctl wait "Calories read from the photo" 20 && eisenctl back
eisenctl visible "New food" "Close" "Read another photo" "Calories read from the photo" "calories per 100 g 200 kcal" && eisenctl small && eisenctl shot 65c-label-form-1.3
for i in 1 2 3 4; do eisenctl visible "Fat read from the photo" "fat per 100 g 10.1 g" "Save food" 2> /dev/null && break; eisenctl scroll down; done
eisenctl visible "Protein read from the photo|Carbs read from the photo" "Fat read from the photo" "fat per 100 g 10.1 g" "serving 50 g" "Save food" && eisenctl small && eisenctl shot 65d-label-form-below-1.3
eisenctl tap "Close" && eisenctl wait "Search foods" && eisenctl tap "Back" && eisenctl tap "Home" && eisenctl wait "Start workout"
# At 2.0
eisenctl font 2.0
eisenctl tap "Food" && eisenctl wait "2,400 kcal left" && eisenctl tap "\+ Add" && eisenctl wait "Search foods"
for i in 1 2 3 4 5 6; do eisenctl visible "Photo of the label" 2> /dev/null && break; eisenctl scroll down; done
eisenctl visible "NO BARCODE\?" "Photo of the label" && eisenctl small && eisenctl shot 66a-label-entry-2.0
eisenctl tap "Photo of the label" && eisenctl wait "Choose a photo" && eisenctl visible "Take a photo" "Choose a photo" "Close" && eisenctl small && eisenctl shot 66b-label-choice-2.0
eisenctl tap "Choose a photo" && eisenctl wait "Photo taken on .*" && eisenctl tap "Photo taken on .*" 1 && eisenctl wait "Calories read from the photo" 20 && eisenctl back
eisenctl visible "New food" "Close" "Read another photo" "Calories read from the photo" "calories per 100 g 200 kcal" && eisenctl small && eisenctl shot 66c-label-form-2.0
for i in 1 2 3 4; do eisenctl visible "Fat read from the photo" "fat per 100 g 10.1 g" "Save food" 2> /dev/null && break; eisenctl scroll down; done
eisenctl visible "Protein read from the photo|Carbs read from the photo" "Fat read from the photo" "fat per 100 g 10.1 g" "serving 50 g" "Save food" && eisenctl small && eisenctl shot 66d-label-form-below-2.0
eisenctl tap "Close" && eisenctl wait "Search foods" && eisenctl tap "Back" && eisenctl tap "Home" && eisenctl wait "Start workout"
eisenctl adb shell "rm -f /sdcard/Pictures/eisen-label-*.jpg" && eisenctl adb shell content call --uri content://media --method scan_volume --arg external_primary > /dev/null
eisenctl doctor && eisenctl cleanup
```

## Gotchas

- `visible` fails for a handle at the edge of a list that scrolls, which may cut it off: the recipes check
  what a screen shows without scrolling, and scroll (one screen at a time, `has` first) to what is below.
- `small` measures the clickable node a tap reaches, not the drawn shape: a Material button drawn 40 dp
  tall takes touches on 48 dp, and that is what it counts. Nodes cut by the edge of a list are left out.
- The Home card sits below the workout: `Start workout` is checked on the first screen, the card after scrolling
  to it. The Food tab is a tab: the bar stays; its add screen covers the tabs like the workout, with the plate at its
  bottom. Settings is no tab: the gear on Home (`Settings`) opens it, `back` returns home.
- The week strip's seven days take the width of the phone: on the emulator's 411 dp each is 54 dp wide; on a 320 dp
  phone each is 43 dp wide, under the 48 dp the recipes ask for (a known gap, not checked by the recipes).
- The awk line on the rest screen compares the heights of the countdown's text and of "of 1:30" as
  uiautomator reports them.
- The weight recipe walks the three scales in one run: the Food tab keeps its scroll, so each scale scrolls up first, and
  it goes Home before tapping `Food`, which History › Progress also has as the title of its Food section.
  "Take" and "Keep" are below the fold at 2.0; the recipe scrolls to them.
- A picture next to a name is 40 dp up to a font scale of 1.3 and 32 dp above it; on a screen narrower than 200 dp per font
  scale (320 dp at 2.0, checked by hand with `wm density 540`) the rows leave it out so the name keeps its room. On the
  emulator's 411 dp it shows at every scale. The pictures have no handle (TalkBack reads the name once); the add sheet's
  `Picture: Banana` is the one the recipes check, the shots show the rest.
- `font` changes a system setting; `cleanup` puts back the scale the run found. A run that stops half-way
  must still be cleaned up, or the emulator keeps the large font.
- The result's middle scrolls when it does not fit (a narrow phone at 2.0: 320 dp, checked by hand with
  `wm density 540`), so its name, time, outcome and the buttons below it stay; the chips may then sit below
  the fold. On the emulator's 411 dp all of them show at every scale, which is what the recipes check.
- Discarding the workout with a set logged asks first; the menu's `Discard workout` and the sheet's share a
  handle, and the menu is closed by the time the sheet shows, so both taps take the first match.
- The form a photo filled opens with the keyboard on the name: the recipe puts it away (`back`) before it checks. It does
  not scroll the sheet up: at its top a swipe down would put the sheet away. The mark of a value sits beside its label and
  wraps under it when the row is too narrow.


# Layout at the extremes

What a lifter needs on each main screen is on screen and big enough to hit, from the default font to the
largest (and the chips "What was it?" never push the result of a timer off its screen): the system font scale at 1.0, 1.3 and 2.0, on the emulator's 1080 × 2400 at 420 dpi. Bugs of this
kind only show on a phone set up that way (QA build 19: at 1.8 and 2.0 the rest screen lost its countdown
and its −15s / +15s; at 1.5 the countdown was smaller than the text around it).

## Sub-features

- `layout-visible` every screen shows what it is for: its title or main action, its controls, the tabs.
- `layout-targets` nothing on a screen can be tapped on less than 48 dp either way (`eisenctl small`).
- `layout-rest-clock` the rest screen's countdown keeps its room and stays at least three times as tall as
  the text beside it; what is below it scrolls, "Next set" stays at the bottom.

## How to get to it (user POV)

Android Settings › Display › Font size, or `eisenctl font <scale>` (put back by `cleanup`). The screens:
Home, a running workout, the rest screen, the hold timer (a plank added to the workout), an interval timer
running, a boxing run, the WOD builder, History, Settings, the result of an interval timer with its chips
"What was it?", and the sheet with the same chips over History's log.

## Driving it with eisenctl

Preconditions: `--state short-timers` (its quick timers). One recipe per font scale; each walks all eleven
screens and checks each with `visible` (the handles on screen, not cut off, tapped on at least 48 dp) and
`small` (no clickable node of the screen under 48 dp). About four minutes each.

**At font scale 1.0** (`layout-visible`, `layout-targets`, `layout-rest-clock`).

```sh
eisenctl launch --state short-timers
eisenctl font 1.0
eisenctl visible "Start workout" "Home" "Workouts" "Timer" "History" "Settings" && eisenctl small && eisenctl shot 11-home
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
eisenctl tap "Settings" && eisenctl wait "Weights" && eisenctl visible "Unit" "Bar weight" "Settings" && eisenctl small && eisenctl shot 19-settings
eisenctl tap "Timer" && eisenctl tap "Timers" && for i in 1 2 3 4; do eisenctl has "Start Quick Tabata" 2> /dev/null && break; eisenctl scroll down; done
eisenctl tap "Start Quick Tabata" && eisenctl wait "Completed" 45 && eisenctl visible "Quick Tabata" "Completed" "What was it\?" "Boxing" "Running" "Again" "Done" "Don't count this run" && eisenctl small && eisenctl shot 20-timer-result
sleep 1 && eisenctl tap "Done" && eisenctl tap "History" && eisenctl wait "Log" && eisenctl tap "Quick Tabata" && eisenctl wait "What was it\?" && eisenctl visible "What was it\?" "Boxing" "Running" "Done" "Close" && eisenctl small && eisenctl shot 20b-history-sheet
eisenctl back && eisenctl wait "Log"
eisenctl doctor && eisenctl cleanup
```

**At font scale 1.3** (`layout-visible`, `layout-targets`, `layout-rest-clock`).

```sh
eisenctl launch --state short-timers
eisenctl font 1.3
eisenctl visible "Start workout" "Home" "Workouts" "Timer" "History" "Settings" && eisenctl small && eisenctl shot 21-home
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
eisenctl tap "Settings" && eisenctl wait "Weights" && eisenctl visible "Unit" "Bar weight" "Settings" && eisenctl small && eisenctl shot 29-settings
eisenctl tap "Timer" && eisenctl tap "Timers" && for i in 1 2 3 4; do eisenctl has "Start Quick Tabata" 2> /dev/null && break; eisenctl scroll down; done
eisenctl tap "Start Quick Tabata" && eisenctl wait "Completed" 45 && eisenctl visible "Quick Tabata" "Completed" "What was it\?" "Boxing" "Running" "Again" "Done" "Don't count this run" && eisenctl small && eisenctl shot 30-timer-result
sleep 1 && eisenctl tap "Done" && eisenctl tap "History" && eisenctl wait "Log" && eisenctl tap "Quick Tabata" && eisenctl wait "What was it\?" && eisenctl visible "What was it\?" "Boxing" "Running" "Done" "Close" && eisenctl small && eisenctl shot 30b-history-sheet
eisenctl back && eisenctl wait "Log"
eisenctl doctor && eisenctl cleanup
```

**At font scale 2.0** (`layout-visible`, `layout-targets`, `layout-rest-clock`), the largest Android offers.

```sh
eisenctl launch --state short-timers
eisenctl font 2.0
eisenctl visible "Start workout" "Home" "Workouts" "Timer" "History" "Settings" && eisenctl small && eisenctl shot 31-home
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
eisenctl tap "Settings" && eisenctl wait "Weights" && eisenctl visible "Unit" "Bar weight" "Settings" && eisenctl small && eisenctl shot 39-settings
eisenctl tap "Timer" && eisenctl tap "Timers" && for i in 1 2 3 4; do eisenctl has "Start Quick Tabata" 2> /dev/null && break; eisenctl scroll down; done
eisenctl tap "Start Quick Tabata" && eisenctl wait "Completed" 45 && eisenctl visible "Quick Tabata" "Completed" "What was it\?" "Boxing" "Running" "Again" "Done" "Don't count this run" && eisenctl small && eisenctl shot 40-timer-result
sleep 1 && eisenctl tap "Done" && eisenctl tap "History" && eisenctl wait "Log" && eisenctl tap "Quick Tabata" && eisenctl wait "What was it\?" && eisenctl visible "What was it\?" "Boxing" "Running" "Done" "Close" && eisenctl small && eisenctl shot 40b-history-sheet
eisenctl back && eisenctl wait "Log"
eisenctl doctor && eisenctl cleanup
```

## Gotchas

- `visible` fails for a handle at the edge of a list that scrolls, which may cut it off: the recipes check
  what a screen shows without scrolling, and scroll (one screen at a time, `has` first) to what is below.
- `small` measures the clickable node a tap reaches, not the drawn shape: a Material button drawn 40 dp
  tall takes touches on 48 dp, and that is what it counts. Nodes cut by the edge of a list are left out.
- The awk line on the rest screen compares the heights of the countdown's text and of "of 1:30" as
  uiautomator reports them.
- `font` changes a system setting; `cleanup` puts back the scale the run found. A run that stops half-way
  must still be cleaned up, or the emulator keeps the large font.
- The result's middle scrolls when it does not fit (a narrow phone at 2.0: 320 dp, checked by hand with
  `wm density 540`), so its name, time, outcome and the buttons below it stay; the chips may then sit below
  the fold. On the emulator's 411 dp all of them show at every scale, which is what the recipes check.
- Discarding the workout with a set logged asks first; the menu's `Discard workout` and the sheet's share a
  handle, and the menu is closed by the time the sheet shows, so both taps take the first match.

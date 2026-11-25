# Interval timers

Four timers on the Timer tab: Tabata (work and rest in rounds), EMOM, AMRAP and For Time. The
lifter sets them with wheels or starts a saved one, hears beeps and a voice, and gets a result that
is filed in the history, where it counts as training like a workout. The result asks "What was it?"
(jump rope, cross trainer, ...), which the history shows and can change. A run survives the screen
going off and the app being killed.

## Sub-features

- `timer-setup` shows a mode's wheels, the total and the voice summary.
- `timer-saved` starts a saved timer from `Saved timers` with its play button.
- `timer-run` counts a 10 second lead-in, then work and rest by the clock, with the voice.
- `timer-result` shows the result of a run that ran to its end (`Completed`) and files a session.
- `timer-taps` counts AMRAP rounds with a tap; `Finish` completes a For Time.
- `timer-end` ends a run early from the header, saving or discarding it.
- `timer-activity` "What was it?": chips on the result (not for a WOD) set what the run was, saved at
  once; a tap on the lit chip clears it. A boxing run is filed as `Boxing`, any other as what the last
  run of the same timer (name and mode) was. The history row then starts with it (`Jump rope · ...`),
  a tap on the row opens a sheet with the same chips, and the Progress calendar's day names it.
- `timer-background` keeps running in a foreground service with a notification, with the screen off,
  and after the process was killed.

## How to get to it (user POV)

- Timer tab, page `Timers` (the tab opens on `WODs` after fresh data): a card per mode.
- A saved timer under `Saved timers`: the row opens its setup, the play button starts it.
- `Again` on a result.
- "What was it?" on a result; a tap on a finished timer in History › Log for its sheet.

## Driving it with eisenctl

Preconditions: none. The recipe starts on the `short-timers` fixture: fresh data plus six saved
timers (`Quick Tabata` 2 × 10s / 5s, `Quick EMOM` 2 × 10s, `Quick AMRAP` 30s, `Quick For Time`
cap 1:00, and two boxing ones). Every block runs as written; the whole file takes about 5:20.

```sh
eisenctl launch --state short-timers
```

**Setup** (`timer-setup`). The Tabata card opens on the defaults: wheels `Work`, `Rest`, `Rounds`,
`Total 3:50`, a `Voice` row, `Save as preset`, `Start`.

```sh
eisenctl tap "Timer" && eisenctl tap "Timers" && eisenctl tap "Tabata" && eisenctl wait "Start"
eisenctl has "Total 3:50" "Save as preset" && eisenctl shot 01-tabata-setup
eisenctl tap "Back to the timers" && eisenctl wait "Saved timers"
```

**A saved timer, run to its end** (`timer-saved`, `timer-run`, `timer-result`, `timer-background`).
The play button described `Start Quick Tabata` goes straight into the lead-in (`GET READY`,
`Round 1 of 2`, a countdown, `Next: Work 10s`). Then `WORK` with `Next: Rest 5s`, `REST`, and after
25 seconds the result: `Quick Tabata`, `0:25`, `2 rounds`, `Completed`, `Again`, `Done`,
`Don't count this run`. While it runs the service is up and its notification is on the channel
`timer_running`.

```sh
eisenctl scroll down && eisenctl shot 02-saved-timers
eisenctl tap "Start Quick Tabata" && eisenctl wait "GET READY" && eisenctl shot 03-lead-in
eisenctl wait "WORK" 12 && eisenctl has "Round 1 of 2" && eisenctl shot 04-work
eisenctl adb shell dumpsys activity services io.github.alexanderop.eisen | grep -q "TimerService"
eisenctl adb shell dumpsys notification --noredact | grep -q "channel=timer_running"
eisenctl wait "REST" 12 && eisenctl shot 05-rest
eisenctl wait "Completed" 20 && eisenctl has "2 rounds" && eisenctl shot 06-tabata-completed
jq -e '.timerSessions[0] | .name == "Quick Tabata" and .completed and .rounds == 2 and .seconds == 25' "$(eisenctl state 07-tabata-session)"
eisenctl said get-ready round-1 rest last-round complete
```

**The screen goes off** (`timer-background`). `Quick EMOM` runs its 30 seconds with the screen off;
the voice goes on, and the screen shows the result when it wakes. In `logcat.txt` the lines
`said round-1`, `said last-round` and `said complete` are between the times printed as
`screen off` and `screen on`.

```sh
eisenctl tap "Done" && eisenctl wait "Saved timers"
eisenctl scroll down && eisenctl tap "Start Quick EMOM" && eisenctl wait "GET READY"
eisenctl adb shell input keyevent KEYCODE_SLEEP && date "+screen off %T"
sleep 30
eisenctl adb shell dumpsys power | grep -q "mWakefulness=Asleep" && date "+screen on %T" && eisenctl adb shell input keyevent KEYCODE_WAKEUP && eisenctl wait "Completed" 10 && eisenctl shot 08-emom-completed
eisenctl said round-1 last-round complete
jq -e '.timerSessions[0] | .name == "Quick EMOM" and .completed and .rounds == 2' "$(eisenctl state 09-emom-session)"
```

**AMRAP counts taps** (`timer-taps`). The big button is described `0 rounds. Tap to count a round.`
and counts up with each tap; a tap within 0.8 seconds of the last one is a bounce and does not
count, so the second tap waits a second. The result says `2 rounds`.

```sh
eisenctl tap "Done" && eisenctl wait "Saved timers"
eisenctl scroll down && eisenctl tap "Start Quick AMRAP" && eisenctl wait "WORK" 15
eisenctl tap "0 rounds\. Tap to count a round\." && eisenctl wait "1 round\. Tap to count a round\." 3
sleep 1 && eisenctl tap "1 round\. Tap to count a round\." && eisenctl wait "2 rounds\. Tap to count a round\." 3 && eisenctl shot 10-amrap-taps
eisenctl wait "Completed" 30 && eisenctl shot 11-amrap-completed
jq -e '.timerSessions[0] | .name == "Quick AMRAP" and .completed and .rounds == 2 and .seconds == 30' "$(eisenctl state 12-amrap-session)"
```

**For Time is done when the lifter says so** (`timer-taps`). The run shows `WORK`, the clock,
`Cap 1:00`, the round button and `Hold to finish`: a tap only says `Keep holding to finish`, a
press of 0.7 seconds completes the run.

```sh
eisenctl tap "Done" && eisenctl wait "Saved timers"
eisenctl scroll down && eisenctl tap "Start Quick For Time" && eisenctl wait "WORK" 15 && eisenctl shot 13-fortime-run
eisenctl hold "Hold to finish" && eisenctl wait "Completed" && eisenctl shot 14-fortime-completed
jq -e '.timerSessions[0] | .name == "Quick For Time" and .completed and .config.mode == "forTime"' "$(eisenctl state 15-fortime-session)"
```

**The app is killed** (`timer-background`). The process is killed the way Android kills it for
memory (`kill -9` as the app's user; `am force-stop` would be a different thing: the lifter
stopping the app in the settings). The run lives in `files/timer-run.json`; the app opened from the
launcher is back on the run at the time the clock says, the new process says the rest of the
lines, and the run completes.

```sh
eisenctl tap "Done" && eisenctl wait "Saved timers"
eisenctl scroll down && eisenctl tap "Start Quick Tabata" && eisenctl wait "WORK" 15 && eisenctl shot 16-before-kill
eisenctl adb shell "run-as io.github.alexanderop.eisen ls files" | grep -q "timer-run.json"
before="$(eisenctl adb shell pidof io.github.alexanderop.eisen | tr -d '\r')"
eisenctl adb shell "run-as io.github.alexanderop.eisen kill -9 $before"
eisenctl adb shell am start -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -n io.github.alexanderop.eisen/.MainActivity > /dev/null
eisenctl wait "WORK|REST" 10 && eisenctl shot 17-after-kill
eisenctl wait "Completed" 25 && eisenctl shot 18-killed-completed
after="$(eisenctl adb shell pidof io.github.alexanderop.eisen | tr -d '\r')"; [ "$after" != "$before" ] && grep -q " $after .* EisenTimer: said complete\$" "$(eisenctl log)"
jq -e '.timerSessions[0] | .name == "Quick Tabata" and .completed' "$(eisenctl state 19-killed-session)"
```

**End early, save, then discard** (`timer-end`). The Tabata card now opens on what was started
last (`2 × 10s / 5s`). `End` asks `End timer?` with `Keep going`, `Save and end`, `Discard`. Saved,
the result reads `Stopped early` and the session is not complete; `Again` starts the same timer, and
`Discard` brings back its setup with no new session.

```sh
eisenctl tap "Done" && eisenctl wait "Saved timers"
eisenctl tap "Tabata" && eisenctl wait "Start" && eisenctl tap "Start" && eisenctl wait "WORK" 15
eisenctl tap "End" && eisenctl wait "Save and end" && eisenctl shot 20-end-sheet && eisenctl tap "Save and end"
eisenctl wait "Stopped early" && eisenctl shot 21-stopped-early
jq -e '.timerSessions | length == 6 and (.[0] | .name == "Tabata" and (.completed | not))' "$(eisenctl state 22-stopped-session)"
sleep 1 && eisenctl tap "Again" && eisenctl wait "WORK" 15
eisenctl tap "End" && eisenctl wait "Discard" && eisenctl tap "Discard" && eisenctl wait "Back to the timers" && eisenctl shot 23-discarded
jq -e '.timerSessions | length == 6' "$(eisenctl state 24-after-discard)"
eisenctl tap "History" && eisenctl wait "Log" && eisenctl has "Stopped early" && eisenctl shot 25-history
eisenctl doctor && eisenctl cleanup
```

**What was it?** (`timer-activity`). The result of `Quick Tabata` shows `What was it?` and the chips
`Boxing`, `Jump rope`, `Battle rope`, `Cross trainer`, `Rowing`, `Bike`, `Running`, none lit: the
session has no `activity`. `Jump rope` saves `"activity": "jumpRope"` at once. In History the row
reads `Jump rope · <day> · 2 rounds · 25s`, and the lit day of the Progress calendar says
`Quick Tabata (Jump rope), 25s`. A tap on the row opens the sheet (`Quick Tabata`, `What was it?`, the
chips, `Done`): `Battle rope` changes it, `Battle rope` again clears it (the key leaves the file), and
Back closes the sheet onto the log, whose row no longer names an activity.

```sh
eisenctl launch --state short-timers
eisenctl tap "Timer" && eisenctl tap "Timers" && eisenctl scroll down
eisenctl tap "Start Quick Tabata" && eisenctl wait "GET READY"
eisenctl wait "Completed" 45 && eisenctl has "What was it\?" "Boxing" "Jump rope" "Battle rope" "Cross trainer" "Rowing" "Bike" "Running" && eisenctl shot 31-result-chips
jq -e '.timerSessions[0] | .name == "Quick Tabata" and (has("activity") | not)' "$(eisenctl state 32-filed-without)"
sleep 1 && eisenctl tap "Jump rope" && eisenctl shot 33-jump-rope
jq -e '.timerSessions[0] | .name == "Quick Tabata" and .activity == "jumpRope"' "$(eisenctl state 34-jump-rope)"
eisenctl tap "Done" && eisenctl wait "Saved timers"
eisenctl tap "History" && eisenctl wait "Log" && eisenctl has "Jump rope · .* · 2 rounds · 25s" && eisenctl shot 35-history-row
eisenctl tap "Progress" && eisenctl wait "Consistency" && eisenctl tap ".*: Quick Tabata \(Jump rope\), 25s" && eisenctl wait "Quick Tabata \(Jump rope\), 25s" && eisenctl shot 36-calendar-day
eisenctl tap "Log" && eisenctl wait "Jump rope · .*" && eisenctl tap "Quick Tabata" && eisenctl wait "What was it\?" && eisenctl has "Done" "Running" && eisenctl shot 37-sheet
eisenctl tap "Battle rope" && eisenctl shot 38-battle-rope
jq -e '.timerSessions[0].activity == "battleRope"' "$(eisenctl state 39-battle-rope)"
eisenctl tap "Battle rope" && eisenctl shot 40-cleared
jq -e '.timerSessions[0] | has("activity") | not' "$(eisenctl state 41-cleared)"
eisenctl back && eisenctl wait "Log" && eisenctl has "[A-Z][a-z]{2}, .* · 2 rounds · 25s" && eisenctl shot 42-log-after
! eisenctl texts | grep -q "What was it?"
eisenctl doctor && eisenctl cleanup
```

## Gotchas

- Starting a timer makes its numbers the ones its mode card opens with: after `Quick Tabata` the
  Tabata card reads `2 × 10s / 5s`. Take the setup shot before.
- The end sheet ignores taps for 0.6 seconds after it opens: `wait "Save and end"` before tapping.
- The result ignores taps for one second after it shows, its chips too. A `tap "Done"` that follows a `state` and a
  `log` is late enough; one right after the result shows needs `sleep 1` first, as `Again` does.
- `uiautomator` sees nothing while the screen is off: `wait` and `texts` only work after
  `KEYCODE_WAKEUP`. The emulator has no lock screen, so waking shows the app.
- The wheels cannot be set by a handle; `adb shell input swipe` over a wheel's bounds moves it.
  The fixture's saved timers take their place: a full default Tabata takes 3:50 plus the lead-in.
- A For Time that runs into its cap ends as `Time cap`, not `Completed`: `Quick For Time` reaches it
  after 1:10. Not in the recipe.

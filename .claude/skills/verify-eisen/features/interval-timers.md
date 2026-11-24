# Interval timers

Four timers on the Timer tab: Tabata (work and rest in rounds), EMOM, AMRAP and For Time. The
lifter sets them with wheels, starts, hears beeps and a voice, and gets a result that is filed in
the history. A run survives the screen going off and the app being killed.

## Sub-features

- `timer-setup` shows a mode's wheels, the total and the voice summary.
- `timer-run` counts a 10 second lead-in, then work and rest by the clock.
- `timer-end` ends a run early from the header, saving or discarding it.
- `timer-result` shows the result and files a session.
- `timer-background` keeps running in a foreground service with a notification.

## How to get to it (user POV)

- Timer tab, page `Timers` (the tab opens on `WODs` after fresh data): a card per mode.
- A saved timer under `Saved timers` (none on fresh data).
- `Again` on a result.

## Driving it with eisenctl

Preconditions:

- Baseline. No timer is running.

- **Open Tabata.** `eisenctl tap "Timer" && eisenctl tap "Timers" && eisenctl tap "Tabata" && eisenctl wait "Start"`.
  The setup shows wheels described `Work`, `Rest`, `Rounds`, `Total 3:50`, a `Voice` row,
  `Save as preset` and `Start`. `eisenctl shot 01-tabata-setup`.
- **Start.** `eisenctl tap "Start" && eisenctl wait "End"`. For 10 seconds a lead-in, then after
  `sleep 12`: `WORK`, `Round 1 of 8`, a countdown, `Total left 3:4x`, `Next: Rest 10s`, `Pause`.
  `eisenctl shot 02-tabata-work`.
- **The service runs.** `eisenctl adb shell dumpsys activity services io.github.alexanderop.eisen | grep TimerService`
  prints a service record, and `dumpsys notification --noredact` has a notification of the channel
  `timer_running`.
- **Rest follows work.** `sleep 20`, then `eisenctl has "REST"` or read `texts`; `eisenctl shot 03-tabata-rest`.
- **End and save.** `eisenctl tap "End" && eisenctl wait "Save and end" && eisenctl tap "Save and end"`.
  The sheet `End timer?` offers `Keep going`, `Save and end`, `Discard`. The result shows the time
  run, the rounds, `Stopped early`, `Again`, `Done`, `Don't count this run`. `eisenctl shot 04-result`.
- **Second view.** `eisenctl state 05-session`: `timerSessions[0]` has `config.mode` `"tabata"`,
  `completed` `false` and `seconds` near the time run. Then `eisenctl tap "Done" && eisenctl tap "History"`:
  the first row is `Tabata` with `Stopped early`.
- **Discard** (other path). Start again, `eisenctl tap "End" && eisenctl tap "Discard"`: the setup is
  back and `timerSessions` has no new entry.

## Gotchas

- `End` appears twice in no screen, but the end sheet ignores taps for 0.6 seconds after it opens:
  `wait "Save and end"` before tapping, as the recipe does.
- The result ignores taps for one second after it shows. Sleep a second before `tap "Done"`.
- The wheels cannot be set by a handle; `adb shell input swipe` over a wheel's bounds moves it.
  Journeys here use the defaults. A full default Tabata takes 3:50 plus the lead-in.
- After `Discard` the app is on the mode's setup, not on the Timer tab: `tap "Back to the timers"`.
- A completed run (not ended early) needs the full time. Budget it, or verify `Completed` on a
  boxing or Tabata timer with fewer rounds set by swiping the `Rounds` wheel.

# Boxing timer

Rounds with a coach who calls combinations. The lifter plans each round (a theme, a pace, speed and
power, reminders, a non-stop finish), starts, and sees the combination called last in huge type.
The result counts combinations and estimates punches.

## Sub-features

- `boxing-setup` shows the plan as a fight card, templates, "My combos" and the call style.
- `boxing-run` shows the round, its theme, the time left and the combination called last.
- `boxing-voice` plays the recordings the schedule says (witnessed in the log).
- `boxing-result` shows rounds, combinations, punches and the plan as a strip.

## How to get to it (user POV)

- Timer tab, page `Timers`: the wide `Boxing` card (`6 × 3:00 / 1:00` on fresh data).
- A saved boxing timer under `Saved timers` (none on fresh data).

## Driving it with eisenctl

Preconditions:

- Baseline. No timer is running.

- **Open.** `eisenctl tap "Timer" && eisenctl tap "Timers" && eisenctl tap "Boxing" && eisenctl wait "Start"`.
  The setup has wheels `Rounds`, `Round`, `Rest`, `TEMPLATES` (chips described like
  `Ladder template. One punch more every round`), `6 rounds · 22:00 total`, and a tile per round
  (`1` `Jab only` `Fast`, ...). `eisenctl shot 01-boxing-setup`.
- **Start.** `eisenctl tap "Start" && eisenctl wait "GET READY"`. The lead-in shows `GET READY`,
  a countdown `to the first round`, `UP NEXT · ROUND 1` and `Jab only`. `eisenctl shot 02-lead-in`.
- **A combination is called.** `sleep 18`, then `eisenctl texts`: `ROUND 1/6  Jab only`, a countdown
  `left in the round`, and a combination as a description and a text (`Double jab`) with the other
  style small under it (`1 – 1`). `eisenctl shot 03-round-1`.
- **What was said is what is shown.** `eisenctl log`, then in `logcat.txt` a line
  `EisenTimer: said box-<id>-n` from the seconds before the shot names the combination on screen
  (`box-jab-body-n` for `Jab to the body`). Later lines are calls that came after the shot.
- **End and save.** `eisenctl tap "End" && eisenctl wait "Save and end" && eisenctl tap "Save and end"`.
  The result shows `Boxing`, the time, `0 rounds`, `<n> combinations called`, `About <m> punches`,
  a strip with one segment per round (described `Round 1: Jab only · Fast`), `Stopped early`.
  `eisenctl shot 04-result`.
- **Second view.** `eisenctl state 05-session`: `timerSessions[0]` has `config.mode` `"boxing"` and
  `combos` and `punches` equal to the numbers on screen. `sleep 1 && eisenctl tap "Done" && eisenctl tap "History"`:
  the first row reads `Boxing` and `... · about <m> punches`.

## Gotchas

- The combinations are drawn from a seed that is new for every run: the combination and the counts
  differ from run to run. Assert that screen, log and saved session agree, not a fixed value.
- A round's first call comes about 6 seconds into the round, after the round and its theme are
  said; before it the screen reads the theme with `listen for the call`.
- The emulator is muted. `said <sound>` in the log is the witness; a recording that is missing is
  skipped, and then there is no `said` line for it.
- A new call flashes the whole combination area in the theme colour for a quarter of a second. A
  screenshot taken in the flash looks wrong and is not.
- A template, a round's sheet (theme grid, pace, reminders, non-stop finish) and "My combos" are
  not mapped yet. Their handles are on screen; add them here when you drive them.

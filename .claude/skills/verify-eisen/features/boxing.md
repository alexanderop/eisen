# Boxing timer

Rounds with a coach who calls combinations. The lifter plans each round (a theme, a pace, speed and
power, reminders, a non-stop finish), starts, and sees the combination called last in huge type.
The result counts combinations and estimates punches.

## Sub-features

- `boxing-setup` shows the plan as a fight card, templates, "My combos" and the call style.
- `boxing-run` shows the round, its theme, the time left and the combination called last.
- `boxing-voice` plays the recordings the schedule says (witnessed in the log).
- `boxing-result` shows rounds, combinations, punches and the plan as a strip, and files a session;
  `Completed` when the run went to the bell, `Stopped early` when it was ended.
- `boxing-time-calls` says the minutes left **in the round** ("Two minutes left", "One minute
  left"), then the last thirty and ten seconds.

## How to get to it (user POV)

- Timer tab, page `Timers`: the wide `Boxing` card (`6 × 3:00 / 1:00` on fresh data).
- A saved boxing timer under `Saved timers`: its play button starts it.

## Driving it with eisenctl

Preconditions: none. The recipe starts on the `short-timers` fixture: fresh data plus the saved
timers `Quick boxing` (one round of 0:30, `Jab only`, `Fast`) and `Boxing 3:00 calls` (one round of
3:00, `2-punch combos`); the voice says the time left every minute. The whole file takes about
5:25, 3:10 of it the round of three minutes.

```sh
eisenctl launch --state short-timers
```

**Setup** (`boxing-setup`). The card opens the default plan: wheels `Rounds`, `Round`, `Rest`,
`TEMPLATES` (chips described like `Ladder template. One punch more every round`),
`6 rounds · 23:00 total`, and a tile per round (`1` `Jab only` `Fast`, ...).

```sh
eisenctl tap "Timer" && eisenctl tap "Timers" && eisenctl tap "Boxing" && eisenctl wait "Start"
eisenctl has "6 rounds · 23:00 total" "Jab only" && eisenctl shot 01-boxing-setup
```

**A call, then end early** (`boxing-run`, `boxing-voice`, `boxing-result`). The lead-in shows
`GET READY`, a countdown `to the first round`, `UP NEXT · ROUND 1` and `Jab only`. In the round:
`ROUND 1/6  Jab only`, a countdown `left in the round`, and from about 6 seconds in the combination
called last as a description and a text (`Double jab`), with the other style small under it
(`1 – 1`): the recipe waits for that small line. Ended with `Save and end`, the result reads
`Boxing`, the time, `0 rounds`, `<n> combinations called`, `About <m> punches`, a strip described
`Round 1: Jab only · Fast` and `Stopped early`; the session has the same `combos` and `punches`.

```sh
eisenctl tap "Start" && eisenctl wait "GET READY" && eisenctl shot 02-lead-in
eisenctl wait "[1-6]B?( – [1-6]B?)*" 25 && eisenctl has "ROUND 1/6  Jab only" && eisenctl shot 03-round-1
eisenctl said get-ready round-1 box-theme-jab
eisenctl tap "End" && eisenctl wait "Save and end" && eisenctl tap "Save and end" && eisenctl wait "Stopped early" && shot="$(eisenctl shot 04-stopped)"
combos="$(sed -nE 's/^text=([0-9]+) combinations? called.*/\1/p' "${shot%.png}.txt")" && punches="$(sed -nE 's/^text=About ([0-9]+) punch(es)?.*/\1/p' "${shot%.png}.txt")"
jq -e --argjson c "$combos" --argjson p "$punches" '.timerSessions[0] | .config.mode == "boxing" and (.completed | not) and .combos == $c and .punches == $p and $c > 0' "$(eisenctl state 05-stopped-session)"
```

In `logcat.txt` the last `EisenTimer: said box-<id>-n` before the time of `03-round-1` names the
combination on that shot (`box-double-jab-n` for `Double jab`). Later lines are calls that came
after the shot.

**A saved timer to the bell** (`boxing-result`). `Quick boxing` runs its one round of 30 seconds:
the result reads `1 round`, the counts, and `Completed`; the coach said `complete`.

```sh
sleep 1 && eisenctl tap "Done" && eisenctl wait "Saved timers" && eisenctl scroll down
eisenctl tap "Start Quick boxing" && eisenctl wait "GET READY"
eisenctl wait "Completed" 45 && eisenctl has "1 round" && shot="$(eisenctl shot 06-quick-completed)"
combos="$(sed -nE 's/^text=([0-9]+) combinations? called.*/\1/p' "${shot%.png}.txt")"
jq -e --argjson c "$combos" '.timerSessions[0] | .name == "Quick boxing" and .completed and .rounds == 1 and .combos == $c' "$(eisenctl state 07-quick-session)"
eisenctl said complete
```

**The minutes left in the round** (`boxing-time-calls`). `Boxing 3:00 calls` is one round of 3:00.
When the round clock shows `1:5x`, `minutes-left-2` was said a moment before; then
`minutes-left-1` at 2:00 into the round, `thirty-seconds`, `ten-seconds`, and `complete` at the
bell. Counted for the round, so each says what the round clock shows.

```sh
sleep 1 && eisenctl tap "Done" && eisenctl wait "Saved timers" && eisenctl scroll down
eisenctl tap "Start Boxing 3:00 calls" && eisenctl wait "GET READY"
eisenctl wait "1:5[0-9]" 80 && eisenctl has "ROUND 1/1  2-punch combos" && eisenctl shot 08-two-minutes-left
eisenctl said minutes-left-2
eisenctl wait "Completed" 140 && eisenctl shot 09-time-calls-completed
eisenctl said minutes-left-2 minutes-left-1 thirty-seconds ten-seconds complete
grep -E "EisenTimer: said (round-1|minutes-left-.|thirty-seconds|ten-seconds|complete)$" "$(eisenctl log)" | tail -6
jq -e '.timerSessions[0] | .name == "Boxing 3:00 calls" and .completed and .seconds == 180' "$(eisenctl state 10-time-calls-session)"
```

The printed lines are 60, 120, 150 and 170 seconds after `round-1` (give or take the 0.7 s the
round call waits for the bell's long beep): the time left in the round, never in the whole timer.

**History.** The first row reads `Boxing 3:00 calls` and `... · about <m> punches`.

```sh
sleep 1 && eisenctl tap "Done" && eisenctl tap "History" && eisenctl wait "Boxing 3:00 calls" && eisenctl shot 11-history
eisenctl doctor && eisenctl cleanup
```

## Gotchas

- The combinations are drawn from a seed that is new for every run: the combination and the counts
  differ from run to run. Assert that screen, log and saved session agree, not a fixed value.
- A round's first call comes about 6 seconds into the round, after the round and its theme are
  said; before it the screen reads the theme with `listen for the call`.
- The emulator is muted. `said <sound>` in the log is the witness; a recording that is missing is
  skipped, and then there is no `said` line for it.
- A new call flashes the whole combination area in the theme colour for a quarter of a second. A
  screenshot taken in the flash looks wrong and is not.
- Starting a timer makes its plan the one the `Boxing` card opens with: after `Quick boxing` the
  card reads `1 × 30s / 10s`. The setup shot comes first.
- A template, a round's sheet (theme grid, pace, reminders, non-stop finish) and "My combos" are
  not mapped yet. Their handles are on screen; add them here when you drive them.
- The list of a round's combinations (the row `Combinations: all 7` in a round's sheet) says what the
  round calls: `All 7 are called`, `2 of 7 are called`, and after `None` (every box unchecked)
  `None checked yet: all 7 are called`. A round never calls none: closed with nothing checked, it
  calls all of them.

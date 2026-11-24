# WODs

CrossFit-style workouts on the Timer tab: 21 built-in benchmarks (Fran, Cindy, Murph, Venus, ...)
and the lifter's own. A WOD runs on a timer; a For Time WOD is ticked off item by item, and a later
run is measured against the best one while it goes.

## Sub-features

- `wod-list` lists the lifter's own WODs and the benchmarks with their format, movements and best.
- `wod-search` filters by text and suggests WODs and movements.
- `wod-detail` shows a WOD with its movements, best and last run, and voice.
- `wod-run` runs a For Time WOD with one big button per item, to the end: the tap of the last item
  completes it.
- `wod-pace` measures a run against the best one while it goes (`On pace`, `▲ 1:23 ahead`), and the
  result says by how much.
- `wod-own` shows a WOD the lifter built, with its best.
- `wod-end` ends a run without saving it.

## How to get to it (user POV)

- Timer tab, page `WODs` (the page the tab opens on with fresh data).
- A card opens the detail; the play button described `Start <name>` starts at once.
- `New WOD` opens the builder (not mapped yet).

## Driving it with eisenctl

Preconditions: none. The recipe starts on the `lifter` fixture: weeks of history with a Fran in
7:42, a Cindy of 14 rounds, and the lifter's own AMRAP `Garage burner` with a best of 7 rounds.
The whole file takes about 2:45.

```sh
eisenctl launch --state lifter
```

**List** (`wod-list`, `wod-own`). Chips `All`, `No weights`, `For Time`, `AMRAP`; a field described
`Search WODs`; `New WOD`; `My WODs` with `Garage burner`, `AMRAP 10:00`, its movements and
`Best 7 rounds`; then `Benchmarks` with cards such as `Fran`, `21-15-9 · For Time`,
`Thruster 43kg · Pull-ups`, `Best 7:42`.

```sh
eisenctl tap "Timer" && eisenctl wait "Benchmarks"
eisenctl has "My WODs" "Best 7 rounds" "Best 7:42" && eisenctl shot 01-wods
eisenctl tap "Garage burner" && eisenctl wait "Start" && eisenctl has "10 × Kettlebell Swing 16kg" "BEST" && eisenctl shot 02-own-wod
eisenctl tap "Back to the WODs" && eisenctl wait "Benchmarks"
```

**Search** (`wod-search`). Typing opens suggestions under the field; the WOD suggestion `Fran` with
`21-15-9 · For Time` opens its detail and clears the search.

```sh
eisenctl tap "Search WODs" && eisenctl type "fr" && eisenctl wait "21-15-9 · For Time" && eisenctl shot 03-search
eisenctl tap "Fran" && eisenctl wait "Start"
```

**Detail** (`wod-detail`). `Back to the WODs`, `Fran`, `21-15-9 · For Time`, `Cap 10:00`,
`Thruster 43kg`, `Pull-ups`, `BEST 7:42` and `LAST 7:42` with the day, a `Voice` row, `Start`.

```sh
eisenctl has "Cap 10:00" "BEST" "7:42" && eisenctl shot 04-fran
```

**Run against the best, to the end** (`wod-run`, `wod-pace`). After the lead-in: `WORK · 1/3`, a
clock counting up, `Cap 10:00`, `Best 7:42`, `On pace`, the round's lines `21 × Thruster 43kg` and
`21 × Pull-ups`, and a big button described `21 × Thruster 43kg done. Next: 21 × Pull-ups.`. A tick
moves the button to the next item and the pace chip to `▲ <time> ahead`. A tap within 0.8 seconds of
the last one is a bounce and is ignored, so each tick waits a second. The sixth tick
(`9 × Pull-ups done. That finishes the WOD.`) ends the run: `3 of 3 rounds`,
`<time> faster than your best`, `Previous best 7:42`, `Completed`, `New record`, and
`Not finished? Undo`.

```sh
eisenctl tap "Start" && eisenctl wait "WORK · 1/3" 15 && eisenctl has "Best 7:42" "On pace" && eisenctl shot 05-run
eisenctl tap "21 × Thruster 43kg done\. Next: 21 × Pull-ups\." && eisenctl wait "21 × Pull-ups done\. Next: round 2\." 3 && eisenctl wait "▲ .* ahead" 3 && eisenctl shot 06-ticked
sleep 1 && eisenctl tap "21 × Pull-ups done\. Next: round 2\." && eisenctl wait "WORK · 2/3" 3
sleep 1 && eisenctl tap "15 × Thruster 43kg done\. Next: 15 × Pull-ups\." && eisenctl wait "15 × Pull-ups done\. Next: round 3\." 3
sleep 1 && eisenctl tap "15 × Pull-ups done\. Next: round 3\." && eisenctl wait "WORK · 3/3" 3
sleep 1 && eisenctl tap "9 × Thruster 43kg done\. Next: 9 × Pull-ups\." && eisenctl wait "9 × Pull-ups done\. That finishes the WOD\." 3
sleep 1 && eisenctl tap "9 × Pull-ups done\. That finishes the WOD\." && eisenctl wait "Completed" && eisenctl has "New record" "Previous best 7:42" && eisenctl shot 07-record
jq -e '.timerSessions[0] | .wodId == "wod-fran" and .completed and .rounds == 3 and (.splits | length == 6)' "$(eisenctl state 08-fran-session)"
```

**A first run** (`wod-run`). Grace has no run yet: the run reads `First run: this sets your best`,
and its one item ends it.

```sh
sleep 1 && eisenctl tap "Done" && eisenctl wait "Fran"
eisenctl tap "Back to the WODs" && eisenctl wait "Benchmarks" && eisenctl scroll down && eisenctl tap "Start Grace" && eisenctl wait "WORK.*" 15
eisenctl has "First run: this sets your best" && eisenctl shot 09-first-run
eisenctl tap "30 × Clean and Jerk 61kg done\. That finishes the WOD\." && eisenctl wait "Completed" && eisenctl shot 10-grace-completed
jq -e '.timerSessions[0] | .wodId == "wod-grace" and .completed and (.splits | length == 1)' "$(eisenctl state 11-grace-session)"
```

**End without saving** (`wod-end`). `Again` starts Grace once more; `End` asks `End WOD?`, and
`Discard` goes back to where the run was started from (here the list, since Grace was started with
its play button) with no new session.

```sh
sleep 1 && eisenctl tap "Again" && eisenctl wait "WORK.*" 15
eisenctl tap "End" && eisenctl wait "Discard" && eisenctl shot 12-end-sheet && eisenctl tap "Discard" && eisenctl wait "Benchmarks"
jq -e '[.timerSessions[] | select(.wodId == "wod-grace")] | length == 1' "$(eisenctl state 13-after-discard)"
eisenctl doctor && eisenctl cleanup
```

## Gotchas

- A For Time WOD is complete only with every item tapped: Fran is 6 taps. Ending early saves a
  session that is not a best time.
- A tap is ignored when it follows the one before within the tap guard (0.8 s); the recipe sleeps a
  second before each tick.
- The result ignores taps for one second after it shows: `sleep 1` before `Done` or `Again`.
- The fixture's days move with `--state` (see `SKILL.md`), so `BEST` and `LAST` carry a day that is
  not in this file. Assert the times, not the days.
- After fresh data nothing has a best; the `lifter` fixture has Fran, Cindy and `Garage burner`.
- The search field takes text with `eisenctl tap "Search WODs" && eisenctl type "fr"`. The
  no-match state (`Show all`) and movement suggestions are not mapped yet.

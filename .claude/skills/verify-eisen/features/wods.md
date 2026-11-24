# WODs

CrossFit-style workouts on the Timer tab: 21 built-in benchmarks (Fran, Cindy, Murph, Venus, ...)
and the lifter's own. A WOD runs on a timer; a For Time WOD is ticked off item by item, and a later
run is measured against the best one while it goes.

## Sub-features

- `wod-list` lists the benchmarks with their format and movements, filtered by chips.
- `wod-search` filters by text and suggests WODs and movements.
- `wod-detail` shows a WOD with its banner, movements and voice.
- `wod-run` runs a For Time WOD with one big button per item.

## How to get to it (user POV)

- Timer tab, page `WODs` (the page the tab opens on with fresh data).
- A card opens the detail; the play button described `Start <name>` starts at once.
- `New WOD` opens the builder (not mapped yet).

## Driving it with eisenctl

Preconditions:

- Baseline. No timer is running.

- **List.** `eisenctl tap "Timer" && eisenctl wait "Benchmarks"`. Chips `All`, `No weights`,
  `For Time`, `AMRAP`; a field described `Search WODs`; `New WOD`; cards such as `Fran`,
  `21-15-9 · For Time`, `Thruster 43kg · Pull-ups`. `eisenctl shot 01-wods`.
- **Detail.** `eisenctl tap "Fran" && eisenctl wait "Start"`. `Back to the WODs`, `Fran`,
  `21-15-9 · For Time`, `Cap 10:00`, `Thruster 43kg`, `Pull-ups`, a `Voice` row, `Edit a copy`,
  `Start`. `eisenctl shot 02-fran`.
- **Run.** `eisenctl tap "Start" && eisenctl wait "End"`, `sleep 12`. The screen reads `WORK · 1/3`,
  a clock counting up, `Cap 10:00`, `First run: this sets your best`, the round's lines
  `21 × Thruster 43kg` and `21 × Pull-ups`, and a big button described
  `21 × Thruster 43kg done. Next: 21 × Pull-ups.`. `eisenctl shot 03-run`.
- **Tick an item off.** `eisenctl tap "21 × Thruster 43kg done\. Next: 21 × Pull-ups\."`, then
  `eisenctl texts`: the button now names `21 × Pull-ups`. `eisenctl shot 04-ticked`.
- **End without saving.** `eisenctl tap "End" && eisenctl wait "Discard" && eisenctl tap "Discard"`.
  The detail of Fran is back; `eisenctl state 05-none` has no new entry in `timerSessions`.

## Gotchas

- A For Time WOD is complete only with every item tapped: Fran is 6 taps. Ending early saves a
  session that is not a best time.
- A tap is ignored when it follows the one before within the tap guard; sleep a second between
  taps on the big button.
- The search field takes text with `eisenctl tap "Search WODs" && eisenctl type "fr"`. Suggestions
  open under the field while it has focus and text. The suggestion and no-match states are not
  mapped yet.
- After fresh data nothing has a best: the pace chip (`▲ 12s ahead`) only shows on a second run of
  the same WOD. A journey for it must first finish one full run.

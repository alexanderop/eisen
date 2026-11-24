# Eisen verification map

The maintained source for verifying what a lifter can do in the Android app. Read this, then the
file of the feature you changed. A proof that drives one entry point is incomplete when the file
lists others.

| File | Starts on | Takes (measured) | Feature |
|---|---|---|---|
| `workout.md` | `--fresh` (twice) | 1:35 | Start a workout, log sets, pick the reps by holding a set, the rest timer, rating a set, finishing |
| `interval-timers.md` | `--state short-timers` | 5:20 | Tabata, EMOM, AMRAP, For Time: setup, saved timers, runs to `Completed`, screen off, a killed app, ending early |
| `boxing.md` | `--state short-timers` | 5:25 | The boxing timer with the coach: setup, run, result with punches, time left in the round |
| `wods.md` | `--state lifter` | 2:45 | The WODs page: own WODs, benchmarks, search, a run against the best, a first run |
| `history-and-data.md` | `--state lifter`, then the emulator's data | 0:45 | The history log, progress, the backup entry points, an update on old data |

Not mapped yet: the Workouts tab (editing routines, the exercise picker), sharing a workout, the
home-screen widget and shortcuts, Health Connect, the hold timer for timed exercises, the WOD
builder, own boxing combinations, saving a preset, the backup file pickers. The five files in a row
take about 15 minutes.

## Baseline preconditions

- Each recipe starts with its own `eisenctl launch` (`--fresh` or `--state <fixture>`, see
  `SKILL.md`) and ends with `eisenctl doctor && eisenctl cleanup`.
- Fresh data means: workouts "Workout A" and "Workout B", kilograms, Hack Squat 80kg, Bench Press
  80kg, Barbell Row 60kg, Dips at bodyweight, an empty history, no saved timers, no own WODs.
- The app opens on the Home tab. The five tabs are the texts `Home`, `Workouts`, `Timer`,
  `History`, `Settings` at the bottom, on every top-level screen.

## Driving conventions

- Commands are literal; `eisenctl` stands for `.claude/skills/verify-eisen/bin/eisenctl` from the
  repo root. Chain with `&&`.
- The commands are in `sh` blocks, in order, and run as written: every line succeeds or the recipe
  is wrong (or the app is). A line that checks something (`has`, `jq -e`, `said`, `grep -q`) is the
  proof; the prose around it says what the shots show.
- A handle is the whole text or description. Where several nodes share one (the set circles of a
  workout all read `5`), the recipe gives the index.
- A journey that needs earlier data starts on a fixture instead of running another journey first.
- Time is real. Recipes give the seconds to wait; do not shorten them. Where a run must take time
  (a lead-in, a tap guard of 0.8 s, a result that ignores taps for a second) the recipe sleeps or
  waits for it and the file's Gotchas say why. No other sleeps.

## Proof and skip reporting

- Shots are numbered in the order taken; each recipe names them.
- A saved thing is proven by `eisenctl state` and a look into the JSON, not by the screen alone.
- Record the feature id (`workout-log`, `boxing-run`, ...) with every artifact you cite.
- When a step cannot be reached, report the command, what `eisenctl texts` showed, and the unmet
  precondition. Do not report a skipped sub-feature as verified through another path.

## Feature entry contract

Each feature file has an H1, one paragraph on what the lifter can do, and four H2 sections in this
order: `Sub-features`, `How to get to it (user POV)`, `Driving it with eisenctl`, `Gotchas`.

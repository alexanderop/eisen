# Eisen verification map

The maintained source for verifying what a lifter can do in the Android app. Read this, then the
file of the feature you changed. A proof that drives one entry point is incomplete when the file
lists others.

| File | Feature |
|---|---|
| `workout.md` | Start a workout, log sets, the rest timer, rating a set, finishing |
| `interval-timers.md` | Tabata, EMOM, AMRAP, For Time: setup, run, end, result |
| `boxing.md` | The boxing timer with the coach: setup, run, result with punches |
| `wods.md` | The WODs page: benchmarks, search, a WOD run ticked off item by item |
| `history-and-data.md` | The history log, and saving and restoring a backup |

Not mapped yet: the Workouts tab (editing routines, the exercise picker), Progress charts, sharing
a workout, the home-screen widget and shortcuts, Health Connect, the hold timer for timed exercises,
own WODs (the builder), own boxing combinations and presets.

## Baseline preconditions

- `eisenctl launch --fresh` printed `ready:` and `eisenctl doctor` is all `ok`.
- Fresh data means: workouts "Workout A" and "Workout B", kilograms, Hack Squat 80kg, Bench Press
  80kg, Barbell Row 60kg, Dips at bodyweight, an empty history, no saved timers, no own WODs.
- The app opens on the Home tab. The five tabs are the texts `Home`, `Workouts`, `Timer`,
  `History`, `Settings` at the bottom, on every top-level screen.

## Driving conventions

- Commands are literal; `eisenctl` stands for `.claude/skills/verify-eisen/bin/eisenctl` from the
  repo root. Chain with `&&`.
- A handle is the whole text or description. Where several nodes share one (the set circles of a
  workout all read `5`), the recipe gives the index.
- Start each recipe from the baseline unless its preconditions say otherwise. A journey that needs
  earlier data says which journey to run first.
- Time is real. Recipes give the seconds to wait; do not shorten them.

## Proof and skip reporting

- Shots are numbered in the order taken; each recipe names them.
- A saved thing is proven by `eisenctl state` and a look into the JSON, not by the screen alone.
- Record the feature id (`workout-log`, `boxing-run`, ...) with every artifact you cite.
- When a step cannot be reached, report the command, what `eisenctl texts` showed, and the unmet
  precondition. Do not report a skipped sub-feature as verified through another path.

## Feature entry contract

Each feature file has an H1, one paragraph on what the lifter can do, and four H2 sections in this
order: `Sub-features`, `How to get to it (user POV)`, `Driving it with eisenctl`, `Gotchas`.

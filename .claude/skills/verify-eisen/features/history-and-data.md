# History and data

The History tab lists finished workouts and timer sessions, newest first, and shows progress as a
training calendar and charts. Settings can save all data to a file, send it, and restore it; a file
that is not a backup, is from a newer version, or is damaged is refused with the reason and changes
nothing.

## Sub-features

- `history-log` lists what was done, with a row per workout and per timer session.
- `history-progress` shows the training calendar and the charts (second view of the tab).
- `data-save` writes a backup through the system file picker.
- `data-restore` reads one, or refuses it and says why.
- `data-update` opens old data with a new build (an update, not a fresh install).

## How to get to it (user POV)

- History tab: `Log` and `Progress` at the top.
- Settings (the gear `Settings` at the top right of Home), scrolled to `Your data`: `Save backup`, `Send backup`,
  `Restore backup`.
- Installing a new build over the old app.

## Driving it with eisenctl

Preconditions: none for the first recipe; it starts on the `lifter` fixture (twelve workouts over
four weeks, seven timer sessions, the newest a Cindy of 14 rounds). `data-update` needs an emulator
whose app already has data (see there). The whole file takes about 45 seconds.

```sh
eisenctl launch --state lifter
```

**Log** (`history-log`). Newest first: `Cindy` with `<day> · 14 rounds · 20:00`, `AMRAP` with
`<day> · 2 rounds · 5:02` and `Stopped early`, then `Workout B` with its exercises, weights, sets
(described `Sets: 5, 5, 5, 5, 5`), `Record` tags and its `RIR` line. The rows are the saved
sessions and workouts.

```sh
eisenctl tab Progress && eisenctl wait "Log"
eisenctl has "Cindy" ".* · 14 rounds · 20:00" "AMRAP" "Stopped early" "Workout B" "Sets: 5, 5, 5, 5, 5" && eisenctl shot 01-history
jq -e '(.timerSessions[0] | .name == "Cindy" and .rounds == 14 and .seconds == 1200) and (.timerSessions[1] | .name == "AMRAP" and (.completed | not)) and (.history | length == 12) and .history[0].name == "Workout B"' "$(eisenctl state 02-state)"
```

**Progress** (`history-progress`). `Consistency` over the `Last 12 weeks` with a lit day per
workout or session (described like `<day>: Fran, 7:42` and `<day>: Workout A, 47 min`),
`This week`, `Weeks in a row`, `Per week`, then `All lifts` with a chart described
`Weight of every lift over time`.

```sh
eisenctl tap "Charts" && eisenctl wait "Consistency"
eisenctl has "Last 12 weeks" ".*: Fran, 7:42" ".*: Boxing, 23:00" ".*: Workout A, 47 min" "Weeks in a row" "All lifts" && eisenctl shot 03-progress
```

**Settings, data** (`data-save`, `data-restore`: entry points only). `Your data` lists
`Save backup`, `Send backup`, `Restore backup`.

```sh
eisenctl tab Today && eisenctl wait "Settings" && eisenctl tap "Settings" && eisenctl wait "Weights" && eisenctl scroll down && eisenctl scroll down && eisenctl wait "Restore backup"
eisenctl has "Your data" "Save backup" "Send backup" && eisenctl shot 04-data
eisenctl doctor && eisenctl cleanup
```

**An update keeps the data** (`data-update`). Without `--fresh` or `--state`, `launch` installs the
new build over the data the emulator's app has; `eisenctl backup eisen.json` prints where that
file as it was before the install. Every workout and session is still there, nothing is running
that was not, and the app wrote no `files/eisen.unreadable.json`. Skip it, and say so, when the
emulator's app has no data (`backup/eisen.json` is missing).

```sh
eisenctl launch && test -s "$(eisenctl backup eisen.json)" && eisenctl shot 05-after-update
after="$(eisenctl state 06-after-update)" && jq -e --slurpfile before "$(eisenctl backup eisen.json)" '([.history[].id] == [$before[0].history[].id]) and ([.timerSessions[].id] == [$before[0].timerSessions[].id]) and (.active.id == $before[0].active.id) and (.version == 9)' "$after"
! eisenctl adb shell "run-as io.github.alexanderop.eisen ls files" | grep -q "eisen.unreadable.json"
eisenctl doctor && eisenctl cleanup
```

## Gotchas

- `--state` moves the fixture's days by whole weeks so its newest entry is in the last seven days:
  the days in the rows change, the weekdays and times do not. Assert the rest of the line.
- `Save backup` and `Restore backup` open Android's file picker, which is another app: its handles
  are not Eisen's and change with the system image. They are not mapped; a refused import is covered
  by unit tests (`UpdateTest`, `DomainTest`), not by a journey here.
- Settings is no tab: the gear on Home (described `Settings`) opens it, `Back` at its top or the system back goes
  home. It is long. `Your data` needs two `scroll down`; a tap right after a swipe only
  stops the list, which is why `scroll` waits.
- With a workout running, `Unit` in Settings reads `Finish the current workout to change it.`
- `data-update` is only as good as the old data on the emulator. `cleanup` restores that data, so
  it stays available for the next run. A fixture is no stand-in: it is at the current version, so
  it proves no migration.

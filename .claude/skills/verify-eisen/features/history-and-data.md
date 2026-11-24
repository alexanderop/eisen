# History and data

The History tab lists finished workouts and timer sessions, newest first. Settings can save all
data to a file, send it, and restore it; a file that is not a backup, is from a newer version, or is
damaged is refused with the reason and changes nothing.

## Sub-features

- `history-log` lists what was done, with a row per workout and per timer session.
- `history-progress` shows charts and the training calendar (second view of the tab).
- `data-save` writes a backup through the system file picker.
- `data-restore` reads one, or refuses it and says why.
- `data-update` opens old data with a new build (an update, not a fresh install).

## How to get to it (user POV)

- History tab: `Log` and `Progress` at the top.
- Settings tab, scrolled to `Your data`: `Save backup`, `Send backup`, `Restore backup`.
- Installing a new build over the old app.

## Driving it with eisenctl

Preconditions:

- Baseline, then one journey that files something: `boxing.md` up to its result, or `workout.md`
  to the end.

- **Log.** `eisenctl tap "History" && eisenctl wait "Log"`. With fresh data and nothing done the
  list is empty; after the boxing journey its first row is `Boxing` with a line like
  `Tue, Nov 24 · 0 rounds · 30s · about 12 punches` and `Stopped early`. `eisenctl shot 01-history`.
- **Second view.** `eisenctl state 02-state`: the row's numbers are `timerSessions[0]`.
- **Settings, data.** `eisenctl tap "Settings" && eisenctl scroll down && eisenctl scroll down && eisenctl wait "Restore backup"`.
  `Your data` lists `Save backup`, `Send backup`, `Restore backup`. `eisenctl shot 03-data`.
- **An update keeps the data** (`data-update`). Do not use `--fresh`: run `eisenctl launch` on an
  emulator whose app has data, then `eisenctl state 04-after-update` and compare with
  `verify/android/.state/backup/eisen.json` (the file as it was before the install). Every workout
  and session is still there, and the app wrote no `files/eisen.unreadable.json`
  (`eisenctl adb shell run-as io.github.alexanderop.eisen ls files`).

## Gotchas

- `Save backup` and `Restore backup` open Android's file picker, which is another app: its handles
  are not Eisen's and change with the system image. They are not mapped; a refused import is covered
  by unit tests (`UpdateTest`, `DomainTest`), not by a journey here.
- The Settings tab is long. `Your data` needs two `scroll down`; a tap right after a swipe only
  stops the list, which is why `scroll` waits.
- With a workout running, `Unit` in Settings reads `Finish the current workout to change it.`
- `data-update` is only as good as the old data on the emulator. `cleanup` restores that data, so
  it stays available for the next run.

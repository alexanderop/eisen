# Awards

XP, levels and medals, worked out from the history every time (nothing but the medals already seen is saved, as
`seenMedals` in `eisen.json`). The numbers (XP per workout, set, record, timer, day of food and weigh-in; the level
cost `400 + level × 120`; the ranks; what each medal asks for) are in `feature/awards/Awards.kt`, the working out in
`app/elm/AwardsSelectors.kt`.

## Sub-features

- `awards-chip`: a shield and `Lv N` next to the gear on Today; a tap opens Progress on `Medals`.
- `awards-case`: Progress › `Medals`: the level card, then `RECORDS`, `CONSISTENCY`, `MILESTONES`, three to a row; a
  locked medal is grey with a lock and says how far it is ("4 of 12"); a tap opens its sheet (how it is earned, the day).
  What was new when the case opened shines until it opens again.
- `awards-record`: a lift with every set done heavier than ever (bodyweight: more reps in all) during the workout: a
  short burst of confetti, no stop, `New record: <exercise>` for TalkBack, a `New record` badge on the card. No confetti
  with the system's animations off.
- `awards-saved`: the `Workout saved` card flips in the medals not seen before and the records of this workout
  (`You earned`, three at most, `and N more medals`), then `+N XP` with the level's bar.

## Driving it with eisenctl

On the JVM: `AwardsScreenTest` (all four, on `lifter`) and `AwardsTest` (the numbers). On the emulator only what is
drawing: the shapes, the confetti and the flip, and the font scales. The `lifter` fixture is level 7 of Copper with
11 of 18 medals, none seen.

**Medal case**

```sh
eisenctl launch --state lifter
eisenctl has "Level 7, Copper. Medals"
eisenctl shot 01-today-chip
eisenctl tap "Level 7, Copper. Medals"
eisenctl wait "Lv 7 · Copper" 10
eisenctl has "Hack Squat 140kg" "3 levels to Bronze · 11 of 18 medals"
eisenctl shot 02-medals
eisenctl scroll down
eisenctl has "12 weeks in a row" "Perfect week"
eisenctl tap "12 weeks in a row"
eisenctl wait "Consistency · Silver" 10
eisenctl shot 03-medal-sheet
eisenctl doctor && eisenctl cleanup
```

**A record, then the saved card** (with animations, to see the confetti in `05-confetti`)

```sh
eisenctl launch --state lifter --animations
eisenctl tap "Start workout"
eisenctl wait "Hack Squat" 10
eisenctl tap "5" 1
eisenctl wait "Minimise rest timer" 10
eisenctl tap "Minimise rest timer"
eisenctl tap "5" 2
eisenctl wait "Minimise rest timer" 10
eisenctl tap "Minimise rest timer"
eisenctl tap "5" 3
eisenctl wait "Minimise rest timer" 10
eisenctl tap "Minimise rest timer"
eisenctl tap "5" 4
eisenctl wait "Minimise rest timer" 10
eisenctl tap "Minimise rest timer"
eisenctl tap "5" 5
eisenctl shot 05-confetti
eisenctl wait "New record: Hack Squat" 5
eisenctl tap "Done"
eisenctl tap "Finish"
eisenctl wait "Finish workout" 10
eisenctl tap "Finish workout"
eisenctl wait "Workout saved" 10
eisenctl has "You earned" "Hack Squat 145kg" "New record" "\\+200 XP"
eisenctl shot 06-saved
eisenctl doctor && eisenctl cleanup
```

The font scales: the same screens at `eisenctl font 1.3` were looked at by hand (build of 28 Nov 2026); `layout.md`
does not cover them yet.

# Food

The lifter logs what he eats in as few taps as a phone in one hand allows, on the Food tab. The add screen stays open
while foods are collected on a plate: `+` puts a food on it at its usual amount, a tap on the row picks the amount first,
a run of barcodes (scanned, or typed when the scanner gives none) goes onto it one after the other, and one button logs it
all. The foods usual at this time of day come first. The day shows what is left of the goal, the calories and the three
macros (calm past a goal: "120 over"), a timeline of what was eaten together, and a week strip on top. Foods eaten often
come back as "Again" chips that log with one tap, meals log in one tap, an empty day copies the day before, and History ›
Progress shows the last seven days. A weight a day gives a smoothed trend and, with the food, what the lifter burns; once a
week a check-in offers a new calorie target from it, which changes nothing until it is taken. A product without a barcode
is added from a photo of its nutrition table, read on the phone. Nothing but a barcode or a search leaves the phone.

## Sub-features

- `food-card` the "Food today" card on Home: the kcal line of the day, the three macros in one line, Scan and Add; a tap
  switches to the Food tab.
- `food-tab` the Food tab (between Timer and History): the day's title, the week strip, the summary, the timeline, Scan,
  `+ Add` and quick kcal at the bottom; a waiting plate shows above them.
- `food-strip` the week strip: the seven days of the week of the day shown as bars against the goal, today marked, a tap
  opens a day, a day to come is not tappable, the arrows (or a swipe) move a week, never into the future.
- `food-macros` calories, protein, carbs and fat against their goals; past a goal "N over" in the same colours.
- `food-goal` the goal sheet: calories, protein, carbs and fat (the last two optional), "Fill carbs and fat from the rest".
- `food-add` the add screen: search, Scan, `USUAL AT THIS TIME`, `RECENT` (or `COMMON FOODS` on a new phone), `MEALS`,
  `Type a barcode`, `NO BARCODE?` with `Photo of the label`, `New food`, `Quick kcal`; `+` on a row, a check on a row that
  is on the plate.
- `food-plate` the plate pinned at the bottom: `N foods · X kcal`, opened to its items (a tap changes one's amount, the
  cross takes it off), `Clear`, `Log X kcal`; Undo for the whole plate; kept when the screen or the app is left.
- `food-usual` the foods usual around this time of day, the most often logged first.
- `food-run` a run of barcodes: each found food lands on the plate and the scanner (or the typed barcode sheet) comes
  again until it is put away; not found leads to the own food form and back to the plate; no network fails with
  "Try again".
- `food-lookup` a barcode the phone knows needs no network; one Open Food Facts knows is kept.
- `food-own` an own food from its form.
- `food-quick` quick kcal: on the day logged at once, on the add screen onto the plate.
- `food-timeline` the day's entries grouped when eaten within 30 minutes; a group opens to its entries; "Save these as a
  meal"; an entry's time changed from its sheet.
- `food-again` one tap logs a food again, with Undo.
- `food-edit` a tap on an entry changes its amount (and time); a swipe deletes it, with Undo.
- `food-days` earlier days: logged there, not today.
- `food-search` what the phone knows filters as it is typed; "Search Open Food Facts" asks Search-a-licious once; a hit
  without its calories is looked up by its barcode.
- `food-meals` "Save as meal" makes a meal; its chip logs every food of it with Undo; "Edit meals" changes and deletes.
- `food-copy` "Copy yesterday" on an empty day logs what the day before holds, with Undo.
- `food-week` History › Progress › Food: the last seven days; a bar opens its day on the Food tab.
- `food-credit` where the data comes from, and what leaves the phone.
- `food-weight` the weight card on the Food tab (the trend, its arrow and change a week, `Log weight`) and its sheet: one
  weight a day in the unit of the settings, an earlier day by its arrow, a second weight replaces the first, Delete with
  Undo.
- `food-burn` what the lifter burns: calories eaten a day minus the trend's change at 7,700 kcal per kg over 21 days, once
  14 days with food logged count ("Learning: 9 of 14 days" before).
- `food-pace` the goal sheet's weight goal: lose, maintain or gain at most 1% of the trend a week, the calories that pace
  takes, protein from the weight, "Adjust my target each week".
- `food-check-in` the weekly check-in on the Food tab: last week's numbers and "New target: X kcal (was Y)", Take or Keep;
  gone for the week once answered, back after a restart while it is not.
- `food-weight-progress` History › Progress › Weight: the weights as dots, the trend as a line, the range, the trend, its
  change a week and what the lifter burns, `Log weight`.
- `food-label` "Photo of the label" under `NO BARCODE?` on the add screen, and "Read from a photo" in a new food's form: the
  camera app or the system's photo picker (no permission for either), the photo read on the phone by ML Kit and thrown
  away, and the own food form filled per 100 g or ml with the serving the label names, the name empty and focused, each
  value marked `read` or `check` (calm, no warning colour); "No nutrition table found" with `Try again` and `Type it in`.
  Nothing is saved or logged until `Save food`.
- `food-icons` a picture of what each food is, left of its name: in the day's entries, the add screen's rows, the plate,
  the "Again" chips, and 64 dp on top of the add sheet. A scanned product takes the picture of its Open Food Facts category,
  a plain food its own, anything else the guess from its name (English and German), else a plate. A tap on the add sheet's
  picture (or the own food form's) opens a grid of all 42; the choice is kept.

## How to get to it (user POV)

The `Food` tab, or Home › the "Food today" card (`FOOD TODAY`, `Scan`, `+ Add`). On the Food tab: the day at the top
with `Food data`; `Previous week`, the days of the week (described `Wed 25, 1,366 kcal`, today `..., today`), `Next week`;
the summary with `Change goal` (or `Set a goal`); the timeline (a group described `07:30, 3 foods, 546 kcal`, an entry
`Skyr Natur, 450 g, 270 kcal`); `Save as meal`, `MEALS · ONE TAP ADDS` with `Edit meals`, `AGAIN · ONE TAP ADDS`; at
the bottom `Scan`, `+ Add`, `kcal` (described `Quick kcal`) and, with something on the plate, `On the plate: N foods · X
kcal`. The add screen: `Back`, `Add food`, `Search foods`, `Scan`, the sections, a row's `Put <name> on the plate` (or
`Take <name> off the plate`), the plate `Plate, N foods · X kcal` with `Clear` and `Log X kcal`. History › `Progress`
has the `Food` section once anything was ever logged. Under the day's entries the `WEIGHT` card (described `Weight trend 83.3
kg, down 0.3 kg a week`) with `Log weight` (`Change today's weight` once today has one); its sheet `Weight` with `Previous day`,
the day, `Next day`, the number (described `weight 82.4 kg`, a tap types it), `Save 82.4 kg` and, for a day with a weight,
`Delete`. On or after a Monday with enough data, `WEEKLY CHECK-IN` on top of the tab with `Take X kcal` and `Keep Y kcal`. The
goal sheet (`Change goal`) has `WEIGHT GOAL` below the macros: `Lose`, `Maintain`, `Gain`, the pace (described `pace 0.5 kg a
week`), `Adjust my target each week`. History › `Progress` › `Weight`: `3 months`, `1 year`, `All`, the chart (described
`Weight over time, N weights, trend ...`), `Trend`, `Change`, `You burn`, `Log weight`.

The photo of a label: on the add screen, under `Type a barcode`, `NO BARCODE?` and `Photo of the label`; the sheet
`Photo of the label` with `Take a photo` and `Choose a photo`; the picker's photos described `Photo taken on Nov 27,
2026 4:20 PM`; then the form `New food` with "Read from the photo of the label.", `Read another photo` beside `PER 100
G`, and each value read marked by a chip described `Calories read from the photo` (or `..., check it`) above its number
(`calories per 100 g 200 kcal`). A new food's form offers `Read from a photo` beside `PER 100 G`; with a barcode nobody
knew above it, `Save food` is below the fold. A photo without a table: "No nutrition table found on this photo. ..."
with `Try again` and `Type it in`.

The pictures are decoration next to a name and have no description of their own; the add sheet's picture and the own food
form's are buttons described `Picture: Yogurt`, and open the sheet `Picture` with one cell per picture, each described by its
name (`Milk`, `Protein shake`, `Oats and muesli`, ...) and selected or not.

## Driving it with eisenctl

Preconditions: `--state food`: fresh data, a goal of 2,400 kcal and 160 g protein, and `Skyr Natur` (Milbona, 60 kcal
and 11 g protein per 100 g, a pack of 450 g) found before by its barcode `4056489000006`, so typing it needs no network.
Nothing is logged. `--state food-history` adds two weeks of eight foods at fixed times of day (see `SKILL.md`).
`--state food-weight` holds four weeks, Monday to Sunday, of three meals a day (two days without any) and most mornings'
weights (84.2 kg down to 82.4 kg, six mornings without, one 1.5 kg high), a goal of 2,400 kcal and to lose 0.5 kg a week; the
driver moves it so its last day is the Sunday before today, so the check-in is due and its numbers are the same whatever
day the journey runs: last week 2,456 kcal a day, the trend 83.7 kg to 83.3 kg, 2,740 kcal burnt, a target of 2,200.

Open Food Facts is not asked: a debug build answers from the app's `files/off/` when that folder exists
(`product/<barcode>.json`, `search/<query>.json`; a missing file is no network). The recipes copy the real answers saved
for the tests (`apps/android/app/src/test/resources/off`) there. No camera either: the barcodes are typed, the way the
lifter types them when the scanner gives none, and a typed run goes onto the plate one barcode after the other exactly as
a scanned run does (the scanner's own screen is driven by `eisenctl release-smoke`, not here).

**One food in two taps, and a typed barcode onto the plate** (`food-card`, `food-tab`, `food-add`, `food-plate`,
`food-lookup`). Home shows the card under the workout. `+ Add` on the card opens the add screen; a new phone offers common
foods; `+` and `Log` log a banana. The Skyr's barcode lands on the plate without a request (the log has no `EisenFood`
line), the sheet stays for the next one; `Log 270 kcal` logs it.

```sh
eisenctl launch --state food
eisenctl visible "Start workout" "Settings" "FOOD TODAY" "2,400 kcal left" "0 / 2,400 kcal" "Protein 0 / 160 g, carbs 0 g, fat 0 g" "Scan" "\+ Add" && eisenctl small && eisenctl shot 01-home-card
eisenctl tap "\+ Add" && eisenctl wait "COMMON FOODS" && eisenctl has "Add food" "Search foods" "Scan" "Banana" && eisenctl tap "Put Banana on the plate" && eisenctl wait "Log 105 kcal" && eisenctl has "Take Banana off the plate" && eisenctl tap "Log 105 kcal" && eisenctl wait "Logged 1 food"
eisenctl has "2,295 kcal left" "Banana, 118 g, 105 kcal" "Food" && eisenctl shot 02-banana
eisenctl tap "\+ Add" && eisenctl wait "Search foods" && eisenctl tap "Type a barcode" && eisenctl wait "Look up"
eisenctl type 4056489000006 && eisenctl tap "Look up" && eisenctl wait "Skyr Natur is on the plate, 1 food now. Type the next barcode, or close this." && eisenctl shot 03-typed-run
! grep -q "EisenFood" "$(eisenctl log)"
eisenctl tap "Close" && eisenctl wait "Log 270 kcal" && eisenctl visible "Plate, 1 food · 270 kcal" "Clear" "Log 270 kcal" && eisenctl tap "Log 270 kcal" && eisenctl wait "2,025 kcal left" && eisenctl has "Skyr Natur, 450 g, 270 kcal|[0-9]{2}:[0-9]{2}, 2 foods, 375 kcal" && eisenctl shot 04-day
jq -e '(.foodLog | map([.name, .amount, .kcal]) == [["Banana", 118, 105], ["Skyr Natur", 450, 270]]) and (.foodLog[1].foodId == "off:4056489000006") and (.foodLog[1].protein == 49.5)' "$(eisenctl state 05-state)"
eisenctl tap "Home" && eisenctl wait "2,025 kcal left" && eisenctl has "375 / 2,400 kcal" "Protein 51 / 160 g, carbs [0-9]+ g, fat [0-9]+ g" && eisenctl shot 06-home-after
eisenctl doctor && eisenctl cleanup
```

**A run of typed barcodes: known, found, not found and no network, onto the plate** (`food-run`, `food-lookup`,
`food-own`, `food-plate`). Four barcodes one after the other without leaving the sheet: the Skyr the phone knows, Nutella
found and kept, `4260999999990` not found (its own food form, saved, and the run goes on), and a Coca-Cola first without
an answer, then with it. The plate opens; Nutella goes to half, the Coca-Cola comes off; one button logs the rest.

```sh
eisenctl launch --state food
eisenctl adb shell "rm -rf /data/local/tmp/eisen-off" && eisenctl adb push apps/android/app/src/test/resources/off /data/local/tmp/eisen-off > /dev/null
eisenctl adb shell "run-as io.github.alexanderop.eisen sh -c 'mkdir -p files/off/product && cp /data/local/tmp/eisen-off/product/4260999999990.json /data/local/tmp/eisen-off/product/3017620422003.json files/off/product/'"
eisenctl tap "Food" && eisenctl wait "2,400 kcal left" && eisenctl tap "\+ Add" && eisenctl wait "Search foods" && eisenctl tap "Type a barcode" && eisenctl wait "Look up"
eisenctl type 4056489000006 && eisenctl tap "Look up" && eisenctl wait "Skyr Natur is on the plate, 1 food now.*"
eisenctl type 3017620422003 && eisenctl tap "Look up" && eisenctl wait "Nutella is on the plate, 2 foods now.*"
eisenctl type 4260999999990 && eisenctl tap "Look up" && eisenctl wait "Open Food Facts does not know this barcode. Add it as your own food." && eisenctl has "Barcode 4260999999990" "Read from a photo" && eisenctl shot 07-own-food
eisenctl tap "Name" && eisenctl type "Tante Emma Quark" && eisenctl back && eisenctl tap "calories per 100 g 0 kcal" && eisenctl type 70 && eisenctl back
for i in 1 2 3; do eisenctl visible "Save food" 2> /dev/null && break; eisenctl scroll down; done
eisenctl tap "Save food" && eisenctl wait "Tante Emma Quark is on the plate, 3 foods now.*"
eisenctl type 5449000000996 && eisenctl tap "Look up" && eisenctl wait "No network. Check the connection and try again." && eisenctl visible "Try again" && eisenctl shot 08-no-network
eisenctl adb shell "run-as io.github.alexanderop.eisen cp /data/local/tmp/eisen-off/product/5449000000996.json files/off/product/"
eisenctl tap "Try again" && eisenctl wait "Coca-Cola Original is on the plate, 4 foods now.*" && eisenctl tap "Close" && eisenctl wait "Log 2,641 kcal"
eisenctl tap "Plate, 4 foods · 2,641 kcal" && eisenctl wait "Nutella, 400 g, 2,156 kcal" && eisenctl shot 09-plate-open
eisenctl tap "Nutella, 400 g, 2,156 kcal" && eisenctl wait "Save 2,156 kcal" && eisenctl has "Take off the plate" "½ = 200 g" && eisenctl shot 10-plate-item && eisenctl tap "½ = 200 g" && eisenctl wait "Save 1,078 kcal" && eisenctl tap "Save 1,078 kcal"
eisenctl wait "Log 1,563 kcal" && eisenctl tap "Take Coca-Cola Original off the plate" && eisenctl wait "Log 1,418 kcal" && eisenctl tap "Log 1,418 kcal" && eisenctl wait "Logged 3 foods" && eisenctl has "982 kcal left"
jq -e '(.foods | map(select(.source == "own" and .barcode == "4260999999990" and .kcal == 70)) | length == 1) and (.foods | map(.id) | index("off:3017620422003") != null) and (.foodLog | map(.kcal) == [270, 1078, 70]) and ([.foodLog[].at] | unique | length == 1)' "$(eisenctl state 11-state)"
eisenctl adb shell "rm -r /data/local/tmp/eisen-off"
eisenctl doctor && eisenctl cleanup
```

**Usual at this time, a breakfast of usual foods in five taps, and the timeline** (`food-usual`, `food-add`,
`food-plate`, `food-timeline`, `food-edit`). On two weeks of history `USUAL AT THIS TIME` holds the food eaten nearest
this time of day (the fixture has one every three hours, so the recipe works it out from the phone's clock; the one
before or after it may be there too, the most recent first). `+ Add`, `+` on the first three rows, `Log`: one group of
three in the timeline, which opens to its entries. An entry's hour is typed in its sheet; it leaves the group.

```sh
eisenctl launch --state food-history
t=$(( 10#$(eisenctl adb shell date +%H | tr -d '\r') * 60 + 10#$(eisenctl adb shell date +%M | tr -d '\r') )) && usual="$(echo 'Milk, 2% fat|Banana|Oats, rolled|Skyr Natur|Rice, white, cooked|Apple|Chicken breast, grilled|Cottage cheese, 2% fat' | cut -d'|' -f$(( t / 180 + 1 )))" && echo "now $t, usual $usual"
eisenctl tap "Food" && eisenctl wait "2,400 kcal left" && eisenctl tap "\+ Add" && eisenctl wait "USUAL AT THIS TIME"
eisenctl texts | sed -n '/^text=USUAL AT THIS TIME/,/^text=RECENT/p' | grep -q "^text=$usual	" && eisenctl has "RECENT" && eisenctl shot 12-usual
eisenctl tap "Put .* on the plate" 1 && eisenctl wait "Plate, 1 food · .*" && eisenctl wait "Put .* on the plate" && eisenctl tap "Put .* on the plate" 1 && eisenctl wait "Plate, 2 foods · .*"
eisenctl wait "Put .* on the plate" && eisenctl tap "Put .* on the plate" 1 && eisenctl wait "Plate, 3 foods · .*" && eisenctl shot 13-breakfast-plate
eisenctl tap "Log .* kcal" && eisenctl wait "Logged 3 foods" && eisenctl tap "[0-9]{2}:[0-9]{2}, 3 foods, .* kcal"
eisenctl has "$usual, [0-9.]+ (g|ml), [0-9,]+ kcal" "[0-9]{2}:[0-9]{2}, 3 foods, .* kcal" && eisenctl shot 14-timeline
for i in 1 2 3; do eisenctl visible "Save these as a meal" 2> /dev/null && break; eisenctl scroll down; done
eisenctl visible "Save these as a meal" && eisenctl scroll up
jq -e --arg usual "$usual" '(.foodLog | length == 115) and (.foodLog[112:] | map(.name) | index($usual) != null) and ([.foodLog[112:][].at] | unique | length == 1)' "$(eisenctl state 15-state)"
nh=$(( t < 720 ? 20 : 3 )) && eisenctl tap "$usual, [0-9.]+ (g|ml), [0-9,]+ kcal" && eisenctl wait "Save [0-9,]+ kcal" && eisenctl wait "hour [0-9]{2}"
eisenctl tap "hour [0-9]{2}" && eisenctl type $nh && eisenctl back && eisenctl has "hour $(printf %02d $nh)" && eisenctl shot 16-entry-time && eisenctl tap "Save [0-9,]+ kcal"
eisenctl wait "[0-9]{2}:[0-9]{2}, 2 foods, .* kcal" && jq -e '[.foodLog[112:][].at] | unique | length == 2' "$(eisenctl state 17-state)"
eisenctl doctor && eisenctl cleanup
```

**Macros, the goal and its fill, the week strip and yesterday, quick kcal, the credit** (`food-macros`, `food-goal`,
`food-strip`, `food-days`, `food-quick`, `food-credit`). "Fill carbs and fat from the rest" makes 289 g carbs and 67 g fat
of 2,400 kcal and 160 g protein. A quick 2,520 kcal goes past the goal: "120 kcal over", in the same colours. Yesterday,
a tap on the strip, takes 500 kcal that today does not count; the next week is not to be had; the Food tab tapped again
is today.

```sh
eisenctl launch --state food
eisenctl tap "Food" && eisenctl wait "2,400 kcal left" && eisenctl visible "Today" "Previous week" "Food data" "Calories 0 / 2,400 kcal" "Protein 0 / 160 g" "Carbs 0 g" "Fat 0 g" "Change goal" && eisenctl small
eisenctl tap "Change goal" && eisenctl wait "Goal per day" && eisenctl scroll down && eisenctl tap "Fill carbs and fat from the rest" && eisenctl has "carbs goal 289 g" "fat goal 67 g" && eisenctl shot 18-goal
for i in 1 2 3; do eisenctl visible "Save goal" 2> /dev/null && break; eisenctl scroll down; done
eisenctl tap "Save goal"
eisenctl wait "Carbs 0 / 289 g" && eisenctl has "Fat 0 / 67 g" && eisenctl tap "Quick kcal" && eisenctl wait "Add 0 kcal" && eisenctl type 2520 && eisenctl wait "Add 2,520 kcal" && eisenctl tap "Add 2,520 kcal"
eisenctl wait "120 kcal over" && eisenctl has "Calories 2,520 / 2,400 kcal · 120 over" && eisenctl shot 19-over
y="$(date -v-1d '+%a %-d')" && { eisenctl has "$y(, .*)?" 2> /dev/null || eisenctl tap "Previous week"; }
eisenctl tap "$y(, .*)?" && eisenctl wait "Yesterday" && eisenctl has "Nothing logged on this day yet." "2,400 kcal left" && eisenctl tap "Quick kcal" && eisenctl wait "Add 0 kcal" && eisenctl type 500 && eisenctl tap "Add 500 kcal" && eisenctl wait "1,900 kcal left" && eisenctl shot 20-yesterday
eisenctl has "$y, 500 kcal" && eisenctl tap "Food" && eisenctl wait "Today" && eisenctl has "120 kcal over" ".*, 2,520 kcal, today" && eisenctl shot 21-week-strip
eisenctl tap "Next week" && eisenctl has "Today"
jq -e '(.foodGoal == {"kcal": 2400, "protein": 160, "carbs": 289, "fat": 67}) and (.foodLog | map(.kcal) == [2520, 500]) and (.foodLog[1].day < .foodLog[0].day)' "$(eisenctl state 22-state)"
eisenctl tap "Food data" && eisenctl wait "Food data: Open Food Facts \(ODbL\) and USDA FoodData Central" && eisenctl shot 23-credit && eisenctl tap "Close"
eisenctl doctor && eisenctl cleanup
```

**Search, Again, change and delete with Undo** (`food-search`, `food-again`, `food-edit`). Typing filters the phone's
foods; the button asks Open Food Facts once; a hit without calories is looked up by its barcode when tapped, its amount
picked for the plate. After the Skyr is logged its chip logs it again with one tap; Undo takes that back. A tap on an
entry and `½` make it 225 g; a swipe deletes it and Undo brings it back.

```sh
eisenctl launch --state food
eisenctl adb shell "rm -rf /data/local/tmp/eisen-off" && eisenctl adb push apps/android/app/src/test/resources/off /data/local/tmp/eisen-off > /dev/null
eisenctl adb shell "run-as io.github.alexanderop.eisen sh -c 'mkdir -p files/off/search files/off/product && cp /data/local/tmp/eisen-off/search/skyr.json files/off/search/ && cp /data/local/tmp/eisen-off/product/4337185761638.json files/off/product/'"
eisenctl tap "Food" && eisenctl wait "2,400 kcal left" && eisenctl tap "\+ Add" && eisenctl wait "Search foods" && eisenctl tap "Search foods" && eisenctl type skyr && eisenctl wait "Skyr Natur" && eisenctl back
eisenctl tap "Search Open Food Facts" && eisenctl wait "Skyr Vanille" && eisenctl has "Skyr Natur" "OPEN FOOD FACTS" "K Classic · tap to look up its calories" && eisenctl shot 24-search
grep -q "answered search skyr from files/off (found)" "$(eisenctl log)"
eisenctl tap "K Classic · tap to look up its calories" && eisenctl wait "Add 64 kcal to the plate" && eisenctl has "Skyr natur" "K-Classic" "64 kcal · 11 g protein" "1 serving 100 g" "1 pack 500 g" && eisenctl shot 25-hit-looked-up && eisenctl tap "Close"
eisenctl tap "Put Skyr Natur on the plate" && eisenctl wait "Log 270 kcal" && eisenctl tap "Log 270 kcal" && eisenctl wait "2,130 kcal left"
for i in 1 2 3; do eisenctl visible "Add Skyr Natur 450 g again" 2> /dev/null && break; eisenctl scroll down; done
eisenctl visible "Add Skyr Natur 450 g again" && eisenctl tap "Add Skyr Natur 450 g again" && eisenctl wait "Added Skyr Natur, 450 g"
grep -qE "^content-desc=[0-9]{2}:[0-9]{2}, 2 foods, 540 kcal" "$(eisenctl shot 26-again | sed 's/png$/txt/')" && eisenctl tap "Undo" && eisenctl wait "Skyr Natur, 450 g, 270 kcal"
eisenctl tap "Skyr Natur, 450 g, 270 kcal" && eisenctl wait "Save 270 kcal" && eisenctl tap "½ = 225 g" && eisenctl wait "Save 135 kcal" && eisenctl tap "Save 135 kcal" && eisenctl wait "Skyr Natur, 225 g, 135 kcal"
set -- $(eisenctl texts | grep -E '^content-desc=Skyr Natur, 225 g, 135 kcal' | grep -oE '[0-9]+') && eisenctl adb shell input swipe 960 $(( ($4 + $6) / 2 )) 120 $(( ($4 + $6) / 2 )) 250
eisenctl wait "Deleted Skyr Natur" && grep -q "^text=Nothing logged on this day yet." "$(eisenctl shot 27-deleted | sed 's/png$/txt/')" && eisenctl tap "Undo" && eisenctl wait "Skyr Natur, 225 g, 135 kcal"
jq -e '.foodLog | map([.amount, .kcal]) == [[225, 135]]' "$(eisenctl state 28-state)"
eisenctl adb shell "rm -r /data/local/tmp/eisen-off"
eisenctl doctor && eisenctl cleanup
```

**The plate is kept: left, and across a restart** (`food-plate`). A banana on the plate, the add screen left: the Food
tab says what waits on the plate, Home asks nothing. The app is stopped and started again: the plate is still there,
in its file; `Clear` empties it without logging.

```sh
eisenctl launch --state food
eisenctl tap "Food" && eisenctl wait "2,400 kcal left" && eisenctl tap "\+ Add" && eisenctl wait "COMMON FOODS" && eisenctl tap "Put Banana on the plate" && eisenctl wait "Log 105 kcal"
eisenctl tap "Back" && eisenctl wait "On the plate: 1 food · 105 kcal" && eisenctl shot 29-plate-waiting && eisenctl tap "Home" && eisenctl wait "Start workout"
eisenctl adb shell "run-as io.github.alexanderop.eisen cat files/plate.json" | grep -q '"name":"Banana"'
eisenctl adb shell am force-stop io.github.alexanderop.eisen && eisenctl adb shell am start -n io.github.alexanderop.eisen/.MainActivity > /dev/null && eisenctl wait "Start workout"
eisenctl tap "Food" && eisenctl wait "On the plate: 1 food · 105 kcal" && eisenctl tap "On the plate: 1 food · 105 kcal" && eisenctl wait "Log 105 kcal" && eisenctl has "Take Banana off the plate" && eisenctl shot 30-plate-after-restart
eisenctl tap "Clear" && eisenctl wait "COMMON FOODS" && ! eisenctl has "Log 105 kcal" 2> /dev/null
! eisenctl adb shell "run-as io.github.alexanderop.eisen ls files" | grep -q plate.json
jq -e '.foodLog == []' "$(eisenctl state 31-state)"
eisenctl doctor && eisenctl cleanup
```

**Meals, copy yesterday and the week** (`food-meals`, `food-copy`, `food-week`). Yesterday, opened on the strip, gets the
Skyr and the oats from the plate; "Save as meal" makes them a meal under the name of the time of day (Breakfast, Lunch,
Snack or Dinner, so the recipe matches any). Today is empty: "Copy yesterday" logs both, Undo takes them back, and the
meal's chip logs both again with one tap. The meal is changed (the oats to 40 g, the Skyr out) and deleted; the log keeps
its numbers. Progress shows the two days; the bar of yesterday opens it on the Food tab.

```sh
eisenctl launch --state food
y="$(date -v-1d '+%a %-d')" && eisenctl tap "Food" && eisenctl wait "2,400 kcal left" && { eisenctl has "$y" 2> /dev/null || eisenctl tap "Previous week"; }
eisenctl tap "$y" && eisenctl wait "Yesterday" && eisenctl tap "\+ Add" && eisenctl wait "Search foods" && eisenctl tap "Type a barcode" && eisenctl wait "Look up" && eisenctl type 4056489000006 && eisenctl tap "Look up" && eisenctl wait "Skyr Natur is on the plate.*" && eisenctl tap "Close"
eisenctl tap "Search foods" && eisenctl type oats && eisenctl wait "Oats, rolled" && eisenctl back && eisenctl tap "Put Oats, rolled on the plate" && eisenctl wait "Log 573 kcal" && eisenctl tap "Log 573 kcal" && eisenctl wait "1,827 kcal left"
for i in 1 2 3; do eisenctl visible "Save as meal" 2> /dev/null && break; eisenctl scroll down; done
eisenctl tap "Save as meal" && eisenctl wait "Save meal" && eisenctl visible "New meal" "Name of the meal" "Breakfast|Lunch|Snack|Dinner" "2 foods · 573 kcal · 60 g protein" "Save meal" && eisenctl shot 32-new-meal
eisenctl tap "Save meal" && eisenctl wait "MEALS · ONE TAP ADDS" && eisenctl has "Add meal (Breakfast|Lunch|Snack|Dinner), 573 kcal" "Edit meals"
eisenctl tap "Food" && eisenctl wait "Today" && eisenctl visible "Nothing logged on this day yet." "Copy yesterday" "2 foods · 573 kcal" && eisenctl shot 33-copy-offer
eisenctl tap "Copy yesterday" && eisenctl wait "Copied 2 foods" && grep -q "^text=1,827 kcal left" "$(eisenctl shot 34-copied | sed 's/png$/txt/')" && eisenctl tap "Undo" && eisenctl wait "2,400 kcal left"
for i in 1 2 3; do eisenctl visible "Add meal .*, 573 kcal" 2> /dev/null && break; eisenctl scroll down; done
eisenctl tap "Add meal .*, 573 kcal" && eisenctl wait "Added .*, 2 foods" && grep -q "^text=1,827 kcal left" "$(eisenctl shot 35-meal-added | sed 's/png$/txt/')"
jq -e '(.meals | length == 1 and (.[0].items | map([.name, .amount, .kcal]) == [["Skyr Natur", 450, 270], ["Oats, rolled", 80, 303]])) and (.foodLog | length == 4) and ([.foodLog[2:][] | [.name, .amount, .kcal]] == [["Skyr Natur", 450, 270], ["Oats, rolled", 80, 303]])' "$(eisenctl state 36-state)"
for i in 1 2 3; do eisenctl visible "Edit meals" 2> /dev/null && break; eisenctl scroll down; done
eisenctl tap "Edit meals" && eisenctl wait "Meals" && eisenctl tap "Breakfast|Lunch|Snack|Dinner" && eisenctl wait "Change meal"
eisenctl tap "amount of Oats, rolled 80 g" && eisenctl type 40 && eisenctl back && eisenctl wait "152 kcal · 5 g protein" && eisenctl tap "Remove Skyr Natur" && eisenctl wait "1 food · 152 kcal · 5 g protein" && eisenctl shot 37-meal-changed
eisenctl tap "Save meal" && eisenctl wait "1 food · 152 kcal"
jq -e '(.meals[0].items | map([.name, .amount, .kcal]) == [["Oats, rolled", 40, 152]]) and (.foodLog | map(.kcal) == [270, 303, 270, 303])' "$(eisenctl state 38-state)"
eisenctl tap "Breakfast|Lunch|Snack|Dinner" && eisenctl wait "Change meal" && eisenctl tap "Delete meal" && eisenctl tap "Tap again to delete" && eisenctl wait "Meals"
jq -e '(.meals == []) and (.foodLog | map(.kcal) == [270, 303, 270, 303])' "$(eisenctl state 39-state)"
eisenctl tap "Close" && eisenctl wait "1,827 kcal left" && eisenctl tap "History" && eisenctl tap "Progress" && eisenctl wait "Last 7 days"
eisenctl visible "Food" "Goal 2,400 kcal" "[A-Z][a-z]{2}, 573 kcal, 60 g protein" "Avg kcal" "573" "60 g" "0 of 7" && eisenctl small && eisenctl shot 40-week
eisenctl tap "[A-Z][a-z]{2}, 573 kcal, 60 g protein" && eisenctl wait "Yesterday" && eisenctl has "1,827 kcal left" "Food" && eisenctl shot 41-bar-opens-day
eisenctl doctor && eisenctl cleanup
```

**Log a weight, correct it, an earlier day, delete with Undo; what the lifter burns is still learning** (`food-weight`,
`food-burn`, `food-weight-progress`). The card asks for a first weight; the sheet starts at 80 kg and takes 82.4 typed. A
second weight today replaces the first. Yesterday gets 82.8 by the arrow: the trend starts at 82.8 and today's 82.1 moves
it a tenth of the way, to 82.73. Today's weight deleted and brought back by Undo. Progress shows both weights and "Learning",
no day with food yet.

```sh
eisenctl launch --state food
eisenctl tap "Food" && eisenctl wait "2,400 kcal left"
for i in 1 2 3; do eisenctl visible "Log weight" 2> /dev/null && break; eisenctl scroll down; done
eisenctl visible "WEIGHT" "Log your weight on a few mornings to see your trend." "Log weight" && eisenctl small && eisenctl shot 42-weight-card-empty
eisenctl tap "Log weight" && eisenctl wait "Save 80 kg" && eisenctl visible "Weight" "Previous day" "Today" "weight 80 kg" "Save 80 kg" && eisenctl small
eisenctl tap "weight 80 kg" && eisenctl type 82.4 && eisenctl back && eisenctl wait "Save 82.4 kg" && eisenctl shot 43-logging && eisenctl tap "Save 82.4 kg"
eisenctl wait "Weight trend 82.4 kg" && eisenctl visible "Change today's weight" && eisenctl shot 44-weight-card
eisenctl tap "Change today's weight" && eisenctl wait "Logged 82.4 kg on this day. Save replaces it." && eisenctl tap "weight 82.4 kg" && eisenctl type 82.1 && eisenctl back && eisenctl wait "Save 82.1 kg" && eisenctl tap "Save 82.1 kg" && eisenctl wait "Weight trend 82.1 kg"
eisenctl tap "Change today's weight" && eisenctl wait "Save 82.1 kg" && eisenctl tap "Previous day" && eisenctl wait "Yesterday" && eisenctl tap "weight 82.1 kg" && eisenctl type 82.8 && eisenctl back && eisenctl wait "Save 82.8 kg" && eisenctl tap "Save 82.8 kg"
eisenctl wait "Weight trend 82.7 kg"
jq -e '(.weights | map(.kg) == [82.8, 82.1]) and (.weights[0].day < .weights[1].day)' "$(eisenctl state 45-state)"
eisenctl tap "Change today's weight" && eisenctl wait "Delete" && eisenctl tap "Delete" && eisenctl wait "Deleted the weight of today"
grep -q "^content-desc=Weight trend 82.8 kg" "$(eisenctl shot 46-deleted | sed 's/png$/txt/')" && eisenctl tap "Undo" && eisenctl wait "Weight trend 82.7 kg"
jq -e '.weights | map(.kg) == [82.8, 82.1]' "$(eisenctl state 47-state)"
eisenctl tap "History" && eisenctl tap "Progress" && for i in 1 2 3; do eisenctl visible "You burn" "Log weight" 2> /dev/null && break; eisenctl scroll down; done
eisenctl visible "Weight" "Trend" "82.7 kg" "You burn" "Learning" "0 of 14 days" "Log weight" && eisenctl has "Weight over time, 2 weights, trend 82.7 kg, from 82.8 kg yesterday" && eisenctl small && eisenctl shot 48-progress-learning
eisenctl doctor && eisenctl cleanup
```

**The weekly check-in taken; the weights and the trend in Progress** (`food-check-in`, `food-burn`, `food-weight-progress`).
Four weeks of food and weights: the Food tab shows the check-in with last week's numbers. Take makes 2,200 kcal the goal
and the card goes, also after a restart. Progress draws 22 weights as dots under the trend line.

```sh
eisenctl launch --state food-weight
eisenctl tap "Food" && eisenctl wait "WEEKLY CHECK-IN"
eisenctl visible "WEEKLY CHECK-IN" "Last week: 2,456 kcal a day, 7 days logged." "Trend −0.4 kg: 83.7 kg to 83.3 kg." "You burn about 2,740 kcal." "New target: 2,200 kcal \(was 2,400\)" "Take 2,200 kcal" "Keep 2,400 kcal" && eisenctl small && eisenctl shot 49-check-in
eisenctl tap "Take 2,200 kcal" && eisenctl wait "Target this week: 2,200 kcal" && eisenctl has "2,200 kcal left" && eisenctl shot 50-taken
! eisenctl has "WEEKLY CHECK-IN" 2> /dev/null
jq -e '(.foodGoal.kcal == 2200) and (.weightGoal.checkIn | .took and .target == 2200 and .was == 2400)' "$(eisenctl state 51-state)"
eisenctl adb shell am force-stop io.github.alexanderop.eisen && eisenctl adb shell am start -n io.github.alexanderop.eisen/.MainActivity > /dev/null && eisenctl wait "Start workout"
eisenctl tap "Food" && eisenctl wait "2,200 kcal left" && ! eisenctl has "WEEKLY CHECK-IN" 2> /dev/null
for i in 1 2 3 4; do eisenctl visible "Log weight" 2> /dev/null && break; eisenctl scroll down; done
eisenctl visible "WEIGHT" "Weight trend 83.3 kg, (down|up) 0\.[0-9] kg a week|Weight trend 83.3 kg, steady" "Log weight" && eisenctl shot 52-weight-card
eisenctl tap "History" && eisenctl tap "Progress" && for i in 1 2 3; do eisenctl visible "You burn" "Log weight" 2> /dev/null && break; eisenctl scroll down; done
eisenctl visible "Weight" "3 months" "1 year" "All" "Weight over time, 22 weights, trend 83.3 kg, .*" "Trend" "83.3 kg" "Change" "You burn" "[0-9],[0-9]{3}" "kcal a day" "Log weight" && eisenctl small && eisenctl shot 53-progress-weight
eisenctl tap "All" && eisenctl has "Weight over time, 22 weights, trend 83.3 kg, .*, from 84.2 kg on .*"
eisenctl doctor && eisenctl cleanup
```

**The check-in after a restart, the goal pace, the switch off and on, and the check-in kept** (`food-check-in`, `food-pace`).
The card comes back after the app is stopped with it open. The goal sheet's pace goes to 0.6 kg a week and the switch
off: the card goes and nothing of the goal changes. Switched on again the card is back with the target of the new pace
(2,740 − 0.6 × 1,100 = 2,080, to 50: 2,100); Keep leaves 2,400.

```sh
eisenctl launch --state food-weight
eisenctl tap "Food" && eisenctl wait "WEEKLY CHECK-IN"
eisenctl adb shell am force-stop io.github.alexanderop.eisen && eisenctl adb shell am start -n io.github.alexanderop.eisen/.MainActivity > /dev/null && eisenctl wait "Start workout"
eisenctl tap "Food" && eisenctl wait "WEEKLY CHECK-IN" && eisenctl has "New target: 2,200 kcal \(was 2,400\)"
for i in 1 2 3; do eisenctl visible "Change goal" 2> /dev/null && break; eisenctl scroll down; done
eisenctl tap "Change goal" && eisenctl wait "Goal per day" && eisenctl has "Protein from your weight: 167 g"
for i in 1 2 3 4; do eisenctl visible "Save goal" 2> /dev/null && break; eisenctl scroll down; done
eisenctl visible "WEIGHT GOAL" "Lose" "Maintain" "Gain" "pace 0.5 kg a week" "Adjust my target each week" "Save goal" && eisenctl has "At most 1% of your weight a week: 0.8 kg." "You burn about [0-9],[0-9]{3} kcal. For this goal: [0-9],[0-9]{3} kcal a day." && eisenctl small && eisenctl shot 54-goal-pace
eisenctl tap "Increase pace" && eisenctl wait "pace 0.6 kg a week" && eisenctl tap "Adjust my target each week" && eisenctl tap "Save goal" && eisenctl wait "Change goal"
eisenctl scroll up && eisenctl scroll up && eisenctl wait "2,400 kcal left" && ! eisenctl has "WEEKLY CHECK-IN" 2> /dev/null
eisenctl shot 55-switch-off
jq -e '(.weightGoal == {"direction": "lose", "pace": 0.6, "adjust": false, "checkIn": null}) and (.foodGoal == {"kcal": 2400, "protein": 160, "carbs": null, "fat": null})' "$(eisenctl state 56-state)"
eisenctl tap "Change goal" && eisenctl wait "Goal per day" && for i in 1 2 3 4; do eisenctl visible "Save goal" 2> /dev/null && break; eisenctl scroll down; done
eisenctl tap "Adjust my target each week" && eisenctl tap "Save goal" && eisenctl wait "Food data" && eisenctl scroll up && eisenctl scroll up
eisenctl wait "WEEKLY CHECK-IN" && eisenctl has "New target: 2,100 kcal \(was 2,400\)" "Keep 2,400 kcal" && eisenctl tap "Keep 2,400 kcal"
eisenctl wait "2,400 kcal left" && ! eisenctl has "WEEKLY CHECK-IN" 2> /dev/null
jq -e '(.foodGoal.kcal == 2400) and (.weightGoal.adjust) and (.weightGoal.checkIn | (.took | not) and .target == 2100 and .was == 2400)' "$(eisenctl state 57-state)"
eisenctl doctor && eisenctl cleanup
```

**Pictures: a category's, a name's, and one chosen that stays** (`food-icons`, `food-lookup`, `food-own`). A typed barcode
takes its category's picture, an own food's follows its name and changes, a wrong one corrected stays after a restart. Edeka's Milch (`4311501065525`, a saved real answer filed
under `en:milks`) is answered from the local file and shows a milk carton; the Skyr the phone knew from build 25 has no
picture stored and shows the yogurt its name gives. The Skyr's yogurt becomes ice cream from its plate item (Back first
puts the grid away without a choice). A new own food starts on the plate and follows the name typed ("Hafermilch" is
milk); the grid makes it oats. After a restart the Skyr still shows ice cream.

```sh
eisenctl launch --state food
eisenctl adb shell "rm -rf /data/local/tmp/eisen-off" && eisenctl adb push apps/android/app/src/test/resources/off /data/local/tmp/eisen-off > /dev/null
eisenctl adb shell "run-as io.github.alexanderop.eisen sh -c 'mkdir -p files/off/product && cp /data/local/tmp/eisen-off/product/4311501065525.json files/off/product/'"
eisenctl tap "Food" && eisenctl wait "2,400 kcal left" && eisenctl tap "\+ Add" && eisenctl wait "Search foods" && eisenctl tap "Type a barcode" && eisenctl wait "Look up"
eisenctl type 4311501065525 && eisenctl tap "Look up" && eisenctl wait "Milch is on the plate, 1 food now.*"
eisenctl type 4056489000006 && eisenctl tap "Look up" && eisenctl wait "Skyr Natur is on the plate, 2 foods now.*" && eisenctl tap "Close" && eisenctl wait "Log 910 kcal"
eisenctl tap "Plate, 2 foods · 910 kcal" && eisenctl wait "Milch, 1000 ml, 640 kcal" && eisenctl shot 58-plate-pictures
eisenctl tap "Milch, 1000 ml, 640 kcal" && eisenctl wait "Save 640 kcal" && eisenctl visible "Picture: Milk" "Save 640 kcal" && eisenctl shot 59-milk-sheet && eisenctl tap "Close"
eisenctl wait "Skyr Natur, 450 g, 270 kcal" && eisenctl tap "Skyr Natur, 450 g, 270 kcal" && eisenctl wait "Save 270 kcal" && eisenctl has "Picture: Yogurt"
eisenctl tap "Picture: Yogurt" && eisenctl wait "Ice cream" && eisenctl visible "Picture" "Close" "Ice cream" && eisenctl has "Milk" "Yogurt" "Cookie" && eisenctl small && eisenctl shot 60-picker
eisenctl back && eisenctl wait "Save 270 kcal" && eisenctl has "Picture: Yogurt" && eisenctl tap "Picture: Yogurt" && eisenctl wait "Ice cream" && eisenctl tap "Ice cream"
eisenctl wait "Picture: Ice cream" && eisenctl has "Save 270 kcal" && eisenctl shot 61-picked && eisenctl tap "Close"
jq -e '(.foods | map(select(.id == "off:4311501065525"))[0].icon == "milk") and (.foods | map(select(.id == "off:4056489000006"))[0].icon == "ice_cream")' "$(eisenctl state 62-state)"
eisenctl wait "Log 910 kcal" && eisenctl tap "Plate, 2 foods · 910 kcal" && eisenctl wait "Clear"
for i in 1 2 3 4; do eisenctl visible "New food" 2> /dev/null && break; eisenctl scroll down; done
eisenctl tap "New food" && eisenctl wait "Save food" && eisenctl has "Picture: Plate"
eisenctl tap "Name" && eisenctl type "Hafermilch" && eisenctl back && eisenctl wait "Picture: Milk" && eisenctl shot 63-own-follows-name
eisenctl tap "Picture: Milk" && eisenctl wait "Oats and muesli" && eisenctl tap "Oats and muesli" && eisenctl wait "Picture: Oats and muesli"
eisenctl tap "calories per 100 g 0 kcal" && eisenctl type 45 && eisenctl back && eisenctl tap "Save food" && eisenctl wait "Add 45 kcal to the plate" && eisenctl has "Picture: Oats and muesli" && eisenctl tap "Close"
eisenctl wait "Log 910 kcal" && eisenctl tap "Log 910 kcal" && eisenctl wait "Logged 2 foods"
jq -e '.foods | map(select(.source == "own" and .name == "Hafermilch"))[0].icon == "cereal"' "$(eisenctl state 64-state)"
eisenctl adb shell am force-stop io.github.alexanderop.eisen && eisenctl adb shell am start -n io.github.alexanderop.eisen/.MainActivity > /dev/null && eisenctl wait "Start workout"
eisenctl tap "Food" && eisenctl wait "1,490 kcal left" && eisenctl tap "[0-9]{2}:[0-9]{2}, 2 foods, 910 kcal" && eisenctl wait "Skyr Natur, 450 g, 270 kcal" && eisenctl shot 65-day-after-restart
eisenctl tap "Skyr Natur, 450 g, 270 kcal" && eisenctl wait "Save 270 kcal" && eisenctl has "Picture: Ice cream" && eisenctl tap "Close"
eisenctl adb shell "rm -r /data/local/tmp/eisen-off"
eisenctl doctor && eisenctl cleanup
```

**A food from a photo of its label: read, marked, saved and logged; a label to check; a photo with no table**
(`food-label`, `food-own`, `food-plate`, `food-credit`). The photos are real ones of Open Food Facts (CC BY-SA), in
`fixtures/labels/`: Gut & Günstig's Vanille Eis (a grid of 100 g and a portion of 50 g), Spaghetti with the head of its table
cut off (so every value is to check), and the front of a Nutella jar. Each is pushed to the emulator's pictures and picked in
the system's photo picker, as the lifter picks one from the gallery; the camera app is not driven (the emulator's is a virtual
scene). The ice cream fills the form with 200 kcal, 1.6 g protein, 25.5 g carbs, 10.1 g fat and a serving of 50 g, each
`read`; named and saved, its amount for the plate starts at the serving, and the plate logs it.

```sh
eisenctl launch --state food
eisenctl adb shell "rm -f /sdcard/Pictures/eisen-label-*.jpg" && eisenctl adb push .claude/skills/verify-eisen/fixtures/labels/vanille-eis.jpg /sdcard/Pictures/eisen-label-vanille-eis.jpg > /dev/null && eisenctl adb shell content call --uri content://media --method scan_volume --arg external_primary > /dev/null
eisenctl tap "Food" && eisenctl wait "2,400 kcal left" && eisenctl tap "\+ Add" && eisenctl wait "Search foods"
for i in 1 2 3 4 5; do eisenctl visible "Photo of the label" "New food" 2> /dev/null && break; eisenctl scroll down; done
eisenctl visible "Type a barcode" "NO BARCODE\?" "Photo of the label" "New food" && eisenctl small && eisenctl shot 66-label-entry
eisenctl tap "Photo of the label" && eisenctl wait "Choose a photo" && eisenctl visible "Photo of the label" "Take a photo" "Choose a photo" "Close" && eisenctl small && eisenctl shot 67-label-choice
eisenctl tap "Choose a photo" && eisenctl wait "Photo taken on .*" && eisenctl tap "Photo taken on .*" 1 && eisenctl wait "Calories read from the photo" 20
eisenctl has "New food" "Read from the photo of the label." "Read another photo" "Calories read from the photo" "Protein read from the photo" "calories per 100 g 200 kcal" "protein per 100 g 1.6 g" && eisenctl shot 68-label-read
grep -q "EisenFood: label photo read" "$(eisenctl log)"
eisenctl type "Vanille Eis" && eisenctl back && eisenctl wait "Picture: Ice cream" && for i in 1 2 3; do eisenctl visible "Save food" 2> /dev/null && break; eisenctl scroll down; done
eisenctl has "Carbs read from the photo" "Fat read from the photo" "carbs per 100 g 25.5 g" "fat per 100 g 10.1 g" "serving 50 g" && eisenctl small && eisenctl shot 69-label-read-below
jq -e '(.foods | length == 1) and (.foodLog == [])' "$(eisenctl state 70-state-unsaved)"
eisenctl tap "Save food" && eisenctl wait "Add 100 kcal to the plate" && eisenctl has "200 kcal · 1.6 g protein" "1 serving 50 g" && eisenctl tap "Add 100 kcal to the plate"
eisenctl wait "Log 100 kcal" && eisenctl tap "Log 100 kcal" && eisenctl wait "Logged 1 food" && eisenctl has "Vanille Eis, 50 g, 100 kcal" && eisenctl shot 71-label-logged
jq -e '(.foods | map(select(.source == "own" and .name == "Vanille Eis" and .kcal == 200 and .protein == 1.6 and .carbs == 25.5 and .fat == 10.1 and .serving == 50 and .unit == "g")) | length == 1) and (.foodLog | map([.name, .amount, .kcal]) == [["Vanille Eis", 50, 100]])' "$(eisenctl state 72-state)"
eisenctl adb shell "rm -f /sdcard/Pictures/eisen-label-*.jpg" && eisenctl adb push .claude/skills/verify-eisen/fixtures/labels/spaghetti.jpg /sdcard/Pictures/eisen-label-spaghetti.jpg > /dev/null && eisenctl adb shell content call --uri content://media --method scan_volume --arg external_primary > /dev/null
eisenctl tap "\+ Add" && eisenctl wait "Search foods" && for i in 1 2 3 4 5; do eisenctl visible "New food" 2> /dev/null && break; eisenctl scroll down; done
eisenctl tap "New food" && eisenctl wait "Read from a photo" && eisenctl tap "Read from a photo" && eisenctl wait "Choose a photo" && eisenctl tap "Choose a photo" && eisenctl wait "Photo taken on .*" && eisenctl tap "Photo taken on .*" 1
eisenctl wait "Calories read from the photo, check it" 20 && eisenctl has "Protein read from the photo, check it" "calories per 100 g 368 kcal" "protein per 100 g 12 g" && eisenctl shot 73-label-check
eisenctl back && eisenctl tap "Close" && eisenctl wait "Search foods"
eisenctl adb shell "rm -f /sdcard/Pictures/eisen-label-*.jpg" && eisenctl adb push .claude/skills/verify-eisen/fixtures/labels/nutella-front.jpg /sdcard/Pictures/eisen-label-nutella-front.jpg > /dev/null && eisenctl adb shell content call --uri content://media --method scan_volume --arg external_primary > /dev/null
for i in 1 2 3 4 5; do eisenctl visible "Photo of the label" 2> /dev/null && break; eisenctl scroll down; done
eisenctl tap "Photo of the label" && eisenctl wait "Choose a photo" && eisenctl tap "Choose a photo" && eisenctl wait "Photo taken on .*" && eisenctl tap "Photo taken on .*" 1
eisenctl wait "No nutrition table found on this photo.*" 20 && eisenctl visible "Try again" "Type it in" && eisenctl small && eisenctl shot 74-label-nothing
eisenctl tap "Type it in" && eisenctl wait "Read from a photo" && eisenctl has "calories per 100 g 0 kcal" && eisenctl tap "Close" && eisenctl wait "Search foods"
jq -e '(.foods | length == 2) and (.foodLog | length == 1)' "$(eisenctl state 75-state-after)"
eisenctl tap "Back" && eisenctl wait "Food data" && eisenctl tap "Food data" && eisenctl wait "A photo of a label is read on this phone and then thrown away: it is not kept, and not sent anywhere." && eisenctl tap "Close"
eisenctl adb shell "rm -f /sdcard/Pictures/eisen-label-*.jpg" && eisenctl adb shell content call --uri content://media --method scan_volume --arg external_primary > /dev/null
eisenctl doctor && eisenctl cleanup
```

## Gotchas

- `--state` moves the food log of a fixture by whole weeks so its newest day is one of the seven days before the phone's
  today: today starts empty, the weekdays and the times of day stay. The `food` fixture holds no entries, so every recipe
  but the one on `food-history` logs on the day it runs, and "yesterday" is filled from the screen.
- `food-history` has a food every three hours; `t / 180` (minutes since midnight) picks the one nearest now, which is
  always within the two hours of "usual at this time". The emulator's clock is not set: the recipe reads it.
- Yesterday on the week strip is described by its weekday and date (`Wed 25`, with `, 500 kcal` once something is
  logged). On a Monday it is in the week before: the recipes tap `Previous week` first when it is not on screen.
- `eisenctl` reads no stdin, so the saved answers go to the app with `adb push` and a `cp` under `run-as`.
  `launch --state` clears the app's data, `files/off` with it.
- The Undo snackbars of the food log last 8 seconds and a dump takes about two: the recipes `wait` for the
  snackbar, check the screen in the dump of the shot (its `.txt`) and tap `Undo` right after.
- An entry is one node for TalkBack, described "name, amount, kcal" (`Skyr Natur, 450 g, 270 kcal`): that is the
  handle to tap or swipe. Entries within 30 minutes of each other form a group (`16:50, 2 foods, 375 kcal`) whose
  entries show once it is tapped; an entry alone is its own row. The chip `½` is described `½ = 225 g`.
- The typed barcode sheet of a run says what landed and how many foods are on the plate ("Skyr Natur is on the plate,
  2 foods now. ..."): each note is a new one, which empties the field for the next barcode.
- A new meal is named by the hour it is made at (Breakfast before 11, Lunch before 15, a Snack before 18, then
  Dinner): the meal recipe matches any of them. The bars of the week in Progress are named by weekday; a tap takes the
  first match, and the first bar with 573 kcal is yesterday's.
- Search-a-licious leaves `nutriments` out of many hits (all of the German "skyr" ones in November 2026): such a hit
  says "tap to look up its calories" and a tap is a product lookup by its barcode, the one request it costs.
- `food-weight`'s check-in reads up to the Sunday before today and the driver moves the fixture so that is its last day: its
  numbers are fixed. What the lifter burns on the card of the goal sheet and in Progress reads up to yesterday, so it moves
  with the weekday a journey runs on; the recipes match it by its shape. The trend is the same any day (a day without a
  weight carries it), its change a week is not.
- The weight sheet starts at the day's weight, else the last one before it, else 80 kg; its number is typed by tapping it
  (`weight 80 kg`), like the add sheet's grams. "Protein from your weight" is 2 g per kg of the trend (83.3 kg: 167 g).
- The weight card sits under the day's timeline: a list that is longer by it scrolls further, so the timeline recipe checks
  the opened group's first entry before it scrolls to "Save these as a meal", and scrolls back up to tap it.
- The goal sheet has the weight goal under the macros: "Save goal" is below the fold, the recipes scroll to it.
- The check-in card is the first item of the Food tab: after the goal sheet, scrolled down to `Change goal`, the recipes
  scroll up before they look for it.
- The pictures have no handle in a row (TalkBack reads the name once): the recipe checks them on the sheets
  (`Picture: Milk`) and in the saved JSON (`"icon": "milk"`), and the shots show them. A food without a picture of its own
  (the Skyr of the `food` fixture, saved by build 25) has no `icon` in the file at all.
- The grid of pictures is a second sheet over the add sheet: `back` puts away the grid only; the sheet stays.
- The real scanner is not driven here: on the emulator Google Play services first has to download its module
  ("Waiting for the Barcode UI module to be downloaded"), and then it needs a camera. `Scan` then opens
  `Type a barcode` with "Google Play services is downloading the scanner, once." (the app asks for the module; a second
  Scan a minute later opens the camera). `eisenctl release-smoke` taps it and puts away whichever shows.
- The photo of a label goes in through the system's photo picker: the recipe pushes the picture to `/sdcard/Pictures/`, has
  the media store scan it (`content call --uri content://media --method scan_volume`), and taps the first `Photo taken on
  ...`, the newest. It deletes its pictures again (also before pushing, so only one of them is ever there). `launch` and
  `cleanup` do not touch the pictures. The camera app is not driven: on the emulator it photographs a virtual room, which
  reads as "No nutrition table found" (tried by hand).
- The form a photo fills opens with the keyboard on the name, which covers the lower fields: the recipe checks the upper ones,
  types the name, puts the keyboard away and scrolls. A value keeps its mark only while it is the value read.
- Reading the ice cream takes about a quarter of a second on the emulator; the recipe gives it 20 seconds.


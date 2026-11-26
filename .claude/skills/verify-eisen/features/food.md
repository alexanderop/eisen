# Food

The lifter logs what he eats in as few taps as a phone in one hand allows, on the Food tab. The add screen stays open
while foods are collected on a plate: `+` puts a food on it at its usual amount, a tap on the row picks the amount first,
a run of barcodes (scanned, or typed when the scanner gives none) goes onto it one after the other, and one button logs it
all. The foods usual at this time of day come first. The day shows what is left of the goal, the calories and the three
macros (calm past a goal: "120 over"), a timeline of what was eaten together, and a week strip on top. Foods eaten often
come back as "Again" chips that log with one tap, meals log in one tap, an empty day copies the day before, and History ›
Progress shows the last seven days. Nothing but a barcode or a search leaves the phone.

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
  `Type a barcode`, `New food`, `Quick kcal`; `+` on a row, a check on a row that is on the plate.
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

## How to get to it (user POV)

The `Food` tab, or Home › the "Food today" card (`FOOD TODAY`, `Scan`, `+ Add`). On the Food tab: the day at the top
with `Food data`; `Previous week`, the days of the week (described `Wed 25, 1,366 kcal`, today `..., today`), `Next week`;
the summary with `Change goal` (or `Set a goal`); the timeline (a group described `07:30, 3 foods, 546 kcal`, an entry
`Skyr Natur, 450 g, 270 kcal`); `Save as meal`, `MEALS · ONE TAP ADDS` with `Edit meals`, `AGAIN · ONE TAP ADDS`; at
the bottom `Scan`, `+ Add`, `kcal` (described `Quick kcal`) and, with something on the plate, `On the plate: N foods · X
kcal`. The add screen: `Back`, `Add food`, `Search foods`, `Scan`, the sections, a row's `Put <name> on the plate` (or
`Take <name> off the plate`), the plate `Plate, N foods · X kcal` with `Clear` and `Log X kcal`. History › `Progress`
has the `Food` section once anything was ever logged.

## Driving it with eisenctl

Preconditions: `--state food`: fresh data, a goal of 2,400 kcal and 160 g protein, and `Skyr Natur` (Milbona, 60 kcal
and 11 g protein per 100 g, a pack of 450 g) found before by its barcode `4056489000006`, so typing it needs no network.
Nothing is logged. `--state food-history` adds two weeks of eight foods at fixed times of day (see `SKILL.md`).

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
eisenctl type 4260999999990 && eisenctl tap "Look up" && eisenctl wait "Open Food Facts does not know this barcode. Add it as your own food." && eisenctl has "Barcode 4260999999990" "Save food" && eisenctl shot 07-own-food
eisenctl tap "Name" && eisenctl type "Tante Emma Quark" && eisenctl back && eisenctl tap "calories per 100 g 0 kcal" && eisenctl type 70 && eisenctl back
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
for i in 1 2 3; do eisenctl has "Save these as a meal" 2> /dev/null && break; eisenctl scroll down; done
eisenctl visible "$usual, [0-9.]+ (g|ml), [0-9,]+ kcal" "Save these as a meal" && eisenctl has "[0-9]{2}:[0-9]{2}, 3 foods, .* kcal" && eisenctl shot 14-timeline
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
eisenctl tap "Change goal" && eisenctl wait "Save goal" && eisenctl scroll down && eisenctl tap "Fill carbs and fat from the rest" && eisenctl has "carbs goal 289 g" "fat goal 67 g" && eisenctl shot 18-goal && eisenctl tap "Save goal"
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
eisenctl tap "Save as meal" && eisenctl wait "Save meal" && eisenctl visible "New meal" "Name of the meal" "Breakfast|Lunch|Snack|Dinner" "2 foods · 573 kcal · 60 g protein" "Save meal" && eisenctl shot 32-new-meal
eisenctl tap "Save meal" && eisenctl wait "MEALS · ONE TAP ADDS" && eisenctl has "Add meal (Breakfast|Lunch|Snack|Dinner), 573 kcal" "Edit meals"
eisenctl tap "Food" && eisenctl wait "Today" && eisenctl visible "Nothing logged on this day yet." "Copy yesterday" "2 foods · 573 kcal" && eisenctl shot 33-copy-offer
eisenctl tap "Copy yesterday" && eisenctl wait "Copied 2 foods" && grep -q "^text=1,827 kcal left" "$(eisenctl shot 34-copied | sed 's/png$/txt/')" && eisenctl tap "Undo" && eisenctl wait "2,400 kcal left"
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
- The real scanner is not driven here: on the emulator Google Play services first has to download its module
  ("Waiting for the Barcode UI module to be downloaded"), and then it needs a camera. `Scan` then opens
  `Type a barcode` with "Google Play services is downloading the scanner, once." (the app asks for the module; a second
  Scan a minute later opens the camera). `eisenctl release-smoke` taps it and puts away whichever shows.

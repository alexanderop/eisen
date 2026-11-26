# Food

The lifter logs what he eats in as few taps as a phone in one hand allows: a barcode (scanned, or typed when
the scanner gives none) looks the product up at Open Food Facts once and keeps it, the add sheet starts at the
amount he logs, foods he eats often come back as "Again" chips that log with one tap, and a meal out is one
number. Foods eaten together become a meal that logs in one tap, and an empty day copies the day before. A day
shows the calories left of the goal and the protein, earlier days can be looked at and filled in, and History ›
Progress shows the last seven days. Nothing but a barcode or a search leaves the phone.

## Sub-features

- `food-card` the "Food today" card on Home: left of the goal, protein, Scan and Add; a tap opens the day.
- `food-day` the Food screen: headline, a bar for the calories and one for protein, the entries, the arrows to
  earlier days (never past today), Back.
- `food-lookup` a typed barcode: one the phone knows needs no network; one Open Food Facts knows is kept; one it
  does not know becomes an own food with the barcode filled in; no network fails with "Try again".
- `food-add` the add sheet: per 100, chips of the pack or serving, grams, "Add 270 kcal".
- `food-own` an own food from its form, then logged.
- `food-quick` quick kcal for a meal out.
- `food-again` one tap logs a food again, with Undo.
- `food-edit` a tap on an entry changes its amount; a swipe deletes it, with Undo.
- `food-goal` the goal of calories and protein, from the screen.
- `food-days` yesterday: logged there, not today.
- `food-search` what the phone knows filters as it is typed; "Search Open Food Facts" asks Search-a-licious once
  (products sold in the phone's country); a hit without its calories is looked up by its barcode when tapped.
- `food-meals` "Save as meal" makes a meal of the day's entries; its chip logs every food of it with Undo; "Edit
  meals" changes an amount, takes a food out, deletes the meal; what was logged keeps its numbers.
- `food-copy` "Copy yesterday" on an empty day logs what the day before holds, with Undo.
- `food-week` History › Progress › Food: the last seven days as bars against the goal, the averages over the days
  logged, the days the protein goal was reached; a bar opens its day.
- `food-credit` where the data comes from, and what leaves the phone.

## How to get to it (user POV)

Home › the "Food today" card (`FOOD TODAY`, `Scan`, `+ Add`). On the Food screen: `Back`, `Previous day`,
`Next day`, `Food data` at the top, `Change goal` under the bars, the entries, `Save as meal` under them (or
`Copy yesterday` on an empty day), `MEALS · ONE TAP ADDS` with `Edit meals`, `AGAIN · ONE TAP ADDS`, and at
the bottom `Scan`, `Search`, `kcal` (described `Quick kcal`). The search has the meals, Again, `Type a barcode`,
`New food` and `Quick kcal` while nothing is typed. History › `Progress` has the `Food` section once anything was
ever logged.

## Driving it with eisenctl

Preconditions: `--state food`: fresh data, a goal of 2,400 kcal and 160 g protein, and `Skyr Natur` (Milbona,
60 kcal and 11 g protein per 100 g, a pack of 450 g) found before by its barcode `4056489000006`, so typing it
needs no network. Nothing is logged: the recipes log what they need on the day they run.

Open Food Facts is not asked: a debug build answers from the app's `files/off/` when that folder exists
(`product/<barcode>.json`, `search/<query>.json`; a missing file is no network). The recipes copy the real
answers saved for the tests (`apps/android/app/src/test/resources/off`) there: `search/skyr.json` is what Search-a-licious
answered for "skyr" in Germany, whose hits carry no calories, and `product/4337185761638.json` the product one
of them is. No camera either: the barcode is
typed, the way the lifter types it when the scanner gives none.

**A known barcode, typed, and added** (`food-card`, `food-lookup`, `food-add`, `food-day`). Home shows the card
under the workout; the barcode of the Skyr opens its add sheet without a request (the log says no `EisenFood`
line), starting at its pack; `Add 270 kcal` logs it.

```sh
eisenctl launch --state food
eisenctl visible "Start workout" "FOOD TODAY" "2,400 left of 2,400 kcal" "Protein 0 / 160 g" "Scan" "\+ Add" && eisenctl small && eisenctl shot 01-home-card
eisenctl tap "\+ Add" && eisenctl wait "Search foods" && eisenctl tap "Type a barcode" && eisenctl wait "Look up"
eisenctl type 4056489000006 && eisenctl tap "Look up" && eisenctl wait "Add 270 kcal" && eisenctl has "Skyr Natur" "Milbona" "60 kcal · 11 g protein" "per 100 g" "1 pack 450 g" "450 g" && eisenctl shot 02-add-sheet
! grep -q "EisenFood" "$(eisenctl log)"
eisenctl tap "Add 270 kcal" && eisenctl wait "2,130 kcal left" && eisenctl visible "Back" "Today" "Skyr Natur, 450 g, 270 kcal" "Change goal" "Scan" "Search" "Quick kcal" && eisenctl small && eisenctl shot 03-day
jq -e '.foodLog | length == 1 and .[0].kcal == 270 and .[0].amount == 450 and .[0].foodId == "off:4056489000006" and .[0].protein == 49.5' "$(eisenctl state 04-state)"
eisenctl back && eisenctl wait "2,130 left of 2,400 kcal" && eisenctl has "Protein 50 / 160 g" && eisenctl shot 05-home-after
eisenctl doctor && eisenctl cleanup
```

**Barcodes Open Food Facts knows, does not know, and no network** (`food-lookup`, `food-own`, `food-add`).
`4260999999990` is not found: the own food form opens with the barcode, is saved and logged, and typing the
barcode again finds it on the phone. Nutella is found and kept. Without an answer the sheet says so; once the
answer is there `Try again` finds the Coca-Cola, a drink counted per 100 ml with its can as the serving.

```sh
eisenctl launch --state food
eisenctl adb shell "rm -rf /data/local/tmp/eisen-off" && eisenctl adb push apps/android/app/src/test/resources/off /data/local/tmp/eisen-off > /dev/null
eisenctl adb shell "run-as io.github.alexanderop.eisen sh -c 'mkdir -p files/off/product && cp /data/local/tmp/eisen-off/product/4260999999990.json /data/local/tmp/eisen-off/product/3017620422003.json files/off/product/'"
eisenctl tap "FOOD TODAY" && eisenctl wait "2,400 kcal left" && eisenctl tap "Search" && eisenctl wait "Type a barcode" && eisenctl tap "Type a barcode" && eisenctl wait "Look up"
eisenctl type 4260999999990 && eisenctl tap "Look up" && eisenctl wait "Open Food Facts does not know this barcode. Add it as your own food." && eisenctl has "Barcode 4260999999990" "Save food" && eisenctl shot 06-own-food
eisenctl tap "Name" && eisenctl type "Tante Emma Quark" && eisenctl back && eisenctl tap "calories per 100 g 0 kcal" && eisenctl type 70 && eisenctl back
eisenctl tap "protein per 100 g 0 g" && eisenctl type 12 && eisenctl back && eisenctl has "Tante Emma Quark" && eisenctl shot 07-own-food-filled
eisenctl tap "Save food" && eisenctl wait "Add 70 kcal" && eisenctl tap "Add 70 kcal" && eisenctl wait "Tante Emma Quark, 100 g, 70 kcal"
eisenctl tap "Search" && eisenctl wait "Type a barcode" && eisenctl tap "Type a barcode" && eisenctl wait "Look up" && eisenctl type 4260999999990 && eisenctl tap "Look up" && eisenctl wait "Add 70 kcal" && eisenctl tap "Close"
eisenctl tap "Search" && eisenctl wait "Type a barcode" && eisenctl tap "Type a barcode" && eisenctl wait "Look up" && eisenctl type 3017620422003 && eisenctl tap "Look up" && eisenctl wait "Add 2,156 kcal" && eisenctl has "Nutella" "Ferrero" "539 kcal · 6.3 g protein" && eisenctl shot 08-found && eisenctl tap "Close"
eisenctl tap "Search" && eisenctl wait "Type a barcode" && eisenctl tap "Type a barcode" && eisenctl wait "Look up" && eisenctl type 5449000000996 && eisenctl tap "Look up" && eisenctl wait "No network. Check the connection and try again." && eisenctl visible "Try again" && eisenctl shot 09-no-network
eisenctl adb shell "run-as io.github.alexanderop.eisen cp /data/local/tmp/eisen-off/product/5449000000996.json files/off/product/"
eisenctl tap "Try again" && eisenctl wait "Add 145 kcal" && eisenctl has "Coca-Cola Original" "per 100 ml" "1 serving 330 ml" && eisenctl shot 10-drink && eisenctl tap "Add 145 kcal" && eisenctl wait "Coca-Cola Original, 330 ml, 145 kcal"
jq -e '(.foods | map(select(.source == "own" and .barcode == "4260999999990" and .kcal == 70)) | length == 1) and (.foods | map(.id) | index("off:3017620422003") != null) and (.foodLog | map(.kcal) == [70, 145])' "$(eisenctl state 11-state)"
eisenctl adb shell "rm -r /data/local/tmp/eisen-off"
eisenctl doctor && eisenctl cleanup
```

**Search, quick kcal, the goal and yesterday** (`food-search`, `food-quick`, `food-goal`, `food-days`,
`food-credit`). Typing filters the phone's foods (the Skyr, and plain foods such as Oats); the button asks Open
Food Facts once. A quick 850 kcal, the goal raised to 2,600, then 500 kcal on yesterday, which today does not count.

```sh
eisenctl launch --state food
eisenctl adb shell "rm -rf /data/local/tmp/eisen-off" && eisenctl adb push apps/android/app/src/test/resources/off /data/local/tmp/eisen-off > /dev/null
eisenctl adb shell "run-as io.github.alexanderop.eisen sh -c 'mkdir -p files/off/search files/off/product && cp /data/local/tmp/eisen-off/search/skyr.json files/off/search/ && cp /data/local/tmp/eisen-off/product/4337185761638.json files/off/product/'"
eisenctl tap "FOOD TODAY" && eisenctl wait "2,400 kcal left" && eisenctl tap "Search" && eisenctl wait "Search foods" && eisenctl type skyr && eisenctl wait "Skyr Natur" && eisenctl back
eisenctl tap "Search Open Food Facts" && eisenctl wait "Skyr Vanille" && eisenctl has "Skyr Natur" "OPEN FOOD FACTS" "K Classic · tap to look up its calories" && eisenctl shot 12-search
grep -q "answered search skyr from files/off (found)" "$(eisenctl log)"
eisenctl tap "K Classic · tap to look up its calories" && eisenctl wait "Add 64 kcal" && eisenctl has "Skyr natur" "K-Classic" "64 kcal · 11 g protein" "1 serving 100 g" "1 pack 500 g" && eisenctl shot 12b-search-looked-up
eisenctl tap "Close" && eisenctl tap "Quick kcal" && eisenctl wait "Add 0 kcal" && eisenctl type 850 && eisenctl wait "Add 850 kcal" && eisenctl shot 13-quick && eisenctl tap "Add 850 kcal" && eisenctl wait "1,550 kcal left"
eisenctl tap "Change goal" && eisenctl wait "Save goal" && eisenctl tap "calorie goal 2,400 kcal" && eisenctl type 2600 && eisenctl back && eisenctl tap "Save goal" && eisenctl wait "1,750 kcal left" && eisenctl has "850 / 2,600 kcal"
eisenctl tap "Previous day" && eisenctl wait "Yesterday" && eisenctl has "Nothing logged on this day yet." "2,600 kcal left" && eisenctl tap "Quick kcal" && eisenctl wait "Add 0 kcal" && eisenctl type 500 && eisenctl tap "Add 500 kcal" && eisenctl wait "2,100 kcal left" && eisenctl shot 14-yesterday
eisenctl tap "Next day" && eisenctl wait "Today" && eisenctl has "1,750 kcal left" "Quick kcal, 850 kcal"
jq -e '(.foodGoal == {"kcal": 2600, "protein": 160}) and (.foodLog | length == 2) and (((.foodLog[0].day | strptime("%Y-%m-%d") | mktime) - (.foodLog[1].day | strptime("%Y-%m-%d") | mktime)) == 86400) and (.foodLog[1].kcal == 500)' "$(eisenctl state 15-state)"
eisenctl tap "Food data" && eisenctl wait "Food data: Open Food Facts \(ODbL\) and USDA FoodData Central" && eisenctl shot 16-credit && eisenctl tap "Close"
eisenctl adb shell "rm -r /data/local/tmp/eisen-off"
eisenctl doctor && eisenctl cleanup
```

**Again, change and delete with Undo** (`food-again`, `food-edit`). After the Skyr is logged its chip logs it
again with one tap; Undo takes that back. A tap on an entry and `½` make it 225 g; a swipe deletes it and Undo
brings it back.

```sh
eisenctl launch --state food
eisenctl tap "FOOD TODAY" && eisenctl wait "2,400 kcal left" && eisenctl tap "Search" && eisenctl wait "Type a barcode" && eisenctl tap "Type a barcode" && eisenctl wait "Look up"
eisenctl type 4056489000006 && eisenctl tap "Look up" && eisenctl wait "Add 270 kcal" && eisenctl tap "Add 270 kcal" && eisenctl wait "2,130 kcal left"
eisenctl visible "Add Skyr Natur 450 g again" && eisenctl tap "Add Skyr Natur 450 g again" && eisenctl wait "Added Skyr Natur, 450 g"
grep -q "^text=1,860 kcal left" "$(eisenctl shot 17-again | sed 's/png$/txt/')" && eisenctl tap "Undo" && eisenctl wait "2,130 kcal left" && eisenctl tap "Add Skyr Natur 450 g again" && eisenctl wait "1,860 kcal left"
eisenctl tap "Skyr Natur, 450 g, 270 kcal" && eisenctl wait "Save 270 kcal" && eisenctl tap "½ = 225 g" && eisenctl wait "Save 135 kcal" && eisenctl tap "Save 135 kcal" && eisenctl wait "1,995 kcal left" && eisenctl has "Skyr Natur, 225 g, 135 kcal"
set -- $(eisenctl texts | grep -E '^content-desc=Skyr Natur, 225 g, 135 kcal' | grep -oE '[0-9]+') && eisenctl adb shell input swipe 960 $(( ($4 + $6) / 2 )) 120 $(( ($4 + $6) / 2 )) 250
eisenctl wait "Deleted Skyr Natur" && grep -q "^text=2,130 kcal left" "$(eisenctl shot 18-deleted | sed 's/png$/txt/')" && eisenctl tap "Undo" && eisenctl wait "1,995 kcal left" && eisenctl has "Skyr Natur, 225 g, 135 kcal"
jq -e '.foodLog | map([.amount, .kcal]) | sort == [[225, 135], [450, 270]]' "$(eisenctl state 19-state)"
eisenctl doctor && eisenctl cleanup
```

**Meals, copy yesterday and the week** (`food-meals`, `food-copy`, `food-week`). Yesterday the Skyr and the oats;
"Save as meal" makes them a meal under the name of the time of day (Breakfast, Lunch, Snack or Dinner, so the
recipe matches any). Today is empty: "Copy yesterday" logs both, Undo takes them back, and the meal's chip logs
both again with one tap. The meal is changed (the oats to 40 g, the Skyr out) and deleted; the log keeps its
numbers. Progress shows the two days; the bar of yesterday opens it.

```sh
eisenctl launch --state food
eisenctl tap "FOOD TODAY" && eisenctl wait "2,400 kcal left" && eisenctl tap "Previous day" && eisenctl wait "Yesterday"
eisenctl tap "Search" && eisenctl wait "Type a barcode" && eisenctl tap "Type a barcode" && eisenctl wait "Look up" && eisenctl type 4056489000006 && eisenctl tap "Look up" && eisenctl wait "Add 270 kcal" && eisenctl tap "Add 270 kcal" && eisenctl wait "2,130 kcal left"
eisenctl tap "Search" && eisenctl wait "Search foods" && eisenctl type oats && eisenctl wait "Oats, rolled" && eisenctl back && eisenctl tap "Oats, rolled" && eisenctl wait "Add 303 kcal" && eisenctl tap "Add 303 kcal" && eisenctl wait "1,827 kcal left"
eisenctl tap "Save as meal" && eisenctl wait "Save meal" && eisenctl visible "New meal" "Name of the meal" "Breakfast|Lunch|Snack|Dinner" "2 foods · 573 kcal · 60 g protein" "Save meal" && eisenctl shot 20-new-meal
eisenctl tap "Save meal" && eisenctl wait "MEALS · ONE TAP ADDS" && eisenctl has "Add meal (Breakfast|Lunch|Snack|Dinner), 573 kcal" "Edit meals"
eisenctl tap "Next day" && eisenctl wait "Today" && eisenctl visible "Nothing logged on this day yet." "Copy yesterday" "2 foods · 573 kcal" && eisenctl shot 21-copy-offer
eisenctl tap "Copy yesterday" && eisenctl wait "Copied 2 foods" && grep -q "^text=1,827 kcal left" "$(eisenctl shot 22-copied | sed 's/png$/txt/')" && eisenctl tap "Undo" && eisenctl wait "2,400 kcal left"
eisenctl tap "Add meal .*, 573 kcal" && eisenctl wait "Added .*, 2 foods" && grep -q "^text=1,827 kcal left" "$(eisenctl shot 23-meal-added | sed 's/png$/txt/')"
jq -e '(.meals | length == 1 and (.[0].items | map([.name, .amount, .kcal]) == [["Skyr Natur", 450, 270], ["Oats, rolled", 80, 303]])) and (.foodLog | length == 4) and ([.foodLog[2:][] | [.name, .amount, .kcal]] == [["Skyr Natur", 450, 270], ["Oats, rolled", 80, 303]])' "$(eisenctl state 24-state)"
eisenctl tap "Edit meals" && eisenctl wait "Meals" && eisenctl tap "Breakfast|Lunch|Snack|Dinner" && eisenctl wait "Change meal"
eisenctl tap "amount of Oats, rolled 80 g" && eisenctl type 40 && eisenctl back && eisenctl wait "152 kcal · 5 g protein" && eisenctl tap "Remove Skyr Natur" && eisenctl wait "1 food · 152 kcal · 5 g protein" && eisenctl shot 25-meal-changed
eisenctl tap "Save meal" && eisenctl wait "1 food · 152 kcal"
jq -e '(.meals[0].items | map([.name, .amount, .kcal]) == [["Oats, rolled", 40, 152]]) and (.foodLog | map(.kcal) == [270, 303, 270, 303])' "$(eisenctl state 26-state)"
eisenctl tap "Breakfast|Lunch|Snack|Dinner" && eisenctl wait "Change meal" && eisenctl tap "Delete meal" && eisenctl tap "Tap again to delete" && eisenctl wait "Meals"
jq -e '(.meals == []) and (.foodLog | map(.kcal) == [270, 303, 270, 303])' "$(eisenctl state 27-state)"
eisenctl tap "Close" && eisenctl wait "1,827 kcal left" && eisenctl back && eisenctl wait "Start workout" && eisenctl tap "History" && eisenctl tap "Progress" && eisenctl wait "Last 7 days"
eisenctl visible "Food" "Goal 2,400 kcal" "[A-Z][a-z]{2}, 573 kcal, 60 g protein" "Avg kcal" "573" "60 g" "0 of 7" && eisenctl small && eisenctl shot 28-week
eisenctl tap "[A-Z][a-z]{2}, 573 kcal, 60 g protein" && eisenctl wait "Yesterday" && eisenctl has "1,827 kcal left" "Skyr Natur, 450 g, 270 kcal" && eisenctl shot 29-bar-opens-day
eisenctl back && eisenctl wait "Last 7 days"
eisenctl doctor && eisenctl cleanup
```

## Gotchas

- `--state` moves only `startedAt` and `finishedAt`: the food fixture holds no entries, so every recipe logs on
  the day it runs, and "yesterday" is filled from the screen.
- `eisenctl` reads no stdin, so the saved answers go to the app with `adb push` and a `cp` under `run-as`.
  `launch --state` clears the app's data, `files/off` with it.
- The Undo snackbars of the food log last 8 seconds and a dump takes about two: the recipes `wait` for the
  snackbar, check the screen in the dump of the shot (its `.txt`) and tap `Undo` right after.
- An entry is one node for TalkBack, described "name, amount, kcal" (`Skyr Natur, 450 g, 270 kcal`): that is the
  handle to tap or swipe. The chip `½` is described `½ = 225 g`.
- A new meal is named by the hour it is made at (Breakfast before 11, Lunch before 15, a Snack before 18, then
  Dinner): the meal recipe matches any of them. The bars of the week are named by weekday; a tap takes the first
  match, and the first bar with 573 kcal is yesterday's.
- Search-a-licious leaves `nutriments` out of many hits (all of the German "skyr" ones in November 2026): such a hit
  says "tap to look up its calories" and a tap is a product lookup by its barcode, the one request it costs.
- The real scanner is not driven: on the emulator Google Play services first has to download its module
  ("Waiting for the Barcode UI module to be downloaded"), and then it needs a camera. `Scan` then opens
  `Type a barcode` with "Google Play services is downloading the scanner, once." (the app asks for the module; a second Scan a minute later opens the camera)

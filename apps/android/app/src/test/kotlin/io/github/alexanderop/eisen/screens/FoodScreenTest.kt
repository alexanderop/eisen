package io.github.alexanderop.eisen.screens

import android.content.ClipboardManager
import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog
import java.io.File

/**
 * `features/food.md` on the `food` fixture, on the JVM: the card on Today, the add screen and its plate, a run of typed
 * barcodes, the goal and its fill, the week strip and yesterday, quick kcal, search, Again, change and delete with Undo,
 * the plate across a restart, meals, copy yesterday, the week in Progress, the weight log, the pictures, the label's way
 * in and the day as Markdown. It is always Wednesday the 25th, 10:00 ([NOW]): yesterday is "Tue 24" in the same week.
 * The photo of a label read by ML Kit and the photo of a dish need the photo picker and stay with the journey.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class FoodScreenTest {
    @get:Rule val app = EisenApp("food")
    private val screen get() = app.screen

    /** The lines the app logged as `EisenFood` (the requests to Open Food Facts, the dish model). */
    private fun foodLog(): List<String> = ShadowLog.getLogsForTag("EisenFood").map { it.msg }

    private fun barcode(code: String) {
        screen.type(code)
        screen.tap("Look up")
    }

    @Test fun `one food in two taps, and a typed barcode the phone knows onto the plate`() {
        screen.has("Settings", "0 of 2,400 kcal today. Opens the Food diary.")
        screen.has("FOOD TODAY", "2,400 kcal left", "0 / 2,400 kcal", "Protein 0 / 160 g, carbs 0 g, fat 0 g", "Scan", "+ Add")
        screen.tap("+ Add")
        screen.has("COMMON FOODS", "Add food", "Search foods", "Scan", "Banana")
        screen.tap("Put Banana on the plate")
        screen.has("Log 105 kcal", "Take Banana off the plate")
        screen.tap("Log 105 kcal")
        screen.has("Logged 1 food", "2,295 kcal left", "Banana, 118 g, 105 kcal")

        screen.tap("+ Add")
        screen.tap("Type a barcode")
        barcode("4056489000006")
        screen.has("Skyr Natur is on the plate, 1 food now. Type the next barcode, or close this.")
        // The phone knew it: no request.
        assertEquals(emptyList<String>(), foodLog().filterNot { it.startsWith("dish model: ") })
        screen.tap("Close")
        screen.has("Plate, 1 food · 270 kcal", "Clear", "Log 270 kcal")
        screen.tap("Log 270 kcal")
        screen.has("2,025 kcal left", "10:00, 2 foods, 375 kcal")
        assertEquals(json("""[["Banana", 118, 105], ["Skyr Natur", 450, 270]]"""), savedRows("foodLog", "name", "amount", "kcal"))
        assertEquals(json("\"off:4056489000006\""), saved("foodLog.1.foodId"))
        assertEquals(json("49.5"), saved("foodLog.1.protein"))

        screen.tab("Today")
        screen.has("Settings", "2,025 kcal left", "375 / 2,400 kcal")
        screen.has(Regex("Protein 51 / 160 g, carbs [0-9]+ g, fat [0-9]+ g"))
    }

    @Test fun `a run of typed barcodes - known, found, not found and no network - onto the plate`() {
        app.answers("product/4260999999990", "product/3017620422003")
        screen.tab("Food")
        screen.has("2,400 kcal left")
        screen.tap("+ Add")
        screen.tap("Type a barcode")
        barcode("4056489000006")
        screen.has(Regex("Skyr Natur is on the plate, 1 food now.*"))
        barcode("3017620422003")
        screen.await(Regex("Nutella is on the plate, 2 foods now.*"))
        barcode("4260999999990")
        screen.await("Open Food Facts does not know this barcode. Add it as your own food.")
        screen.has("Barcode 4260999999990", "Read from a photo")
        screen.tap("Name")
        screen.type("Tante Emma Quark")
        screen.tap("calories per 100 g 0 kcal")
        screen.type("70")
        screen.tap("Save food")
        screen.has(Regex("Tante Emma Quark is on the plate, 3 foods now.*"))
        barcode("5449000000996")
        screen.await("No network. Check the connection and try again.")
        screen.has("Try again")
        app.answers("product/5449000000996")
        screen.tap("Try again")
        screen.await(Regex("Coca-Cola Original is on the plate, 4 foods now.*"))
        screen.tap("Close")
        screen.tap("Plate, 4 foods · 2,641 kcal")
        screen.tap("Nutella, 400 g, 2,156 kcal")
        screen.has("Save 2,156 kcal", "Take off the plate", "½ = 200 g")
        screen.tap("½ = 200 g")
        screen.tap("Save 1,078 kcal")
        screen.has("Log 1,563 kcal")
        screen.tap("Take Coca-Cola Original off the plate")
        screen.tap("Log 1,418 kcal")
        screen.has("Logged 3 foods", "982 kcal left")
        val own = saved("foods").let { it as kotlinx.serialization.json.JsonArray }.filter { saved -> saved.toString().let { "\"source\":\"own\"" in it && "\"barcode\":\"4260999999990\"" in it } }
        assertEquals(1, own.size)
        assertEquals(json("70"), json(own.single().let { (it as kotlinx.serialization.json.JsonObject)["kcal"].toString() }))
        assertTrue(saved("foods").toString().contains("\"id\":\"off:3017620422003\""))
        assertEquals(json("[[270], [1078], [70]]"), savedRows("foodLog", "kcal"))
        assertEquals(1, savedRows("foodLog", "at").toString().let { json(it) as kotlinx.serialization.json.JsonArray }.toSet().size)
    }

    @Test fun `the plate is kept when the add screen is left, and across a restart`() {
        screen.tab("Food")
        screen.tap("+ Add")
        screen.tap("Put Banana on the plate")
        screen.has("Log 105 kcal")
        screen.tap("Back")
        screen.has("On the plate: 1 food · 105 kcal")
        screen.tab("Today")
        screen.has("Settings")
        val plate = File(app.files, "plate.json")
        screen.waitUntil(5_000) { plate.isFile && "\"name\":\"Banana\"" in plate.readText() }

        app.restart()
        screen.has("Start workout")
        screen.tab("Food")
        screen.tap("On the plate: 1 food · 105 kcal")
        screen.has("Log 105 kcal", "Take Banana off the plate")
        screen.tap("Clear")
        screen.has("COMMON FOODS")
        screen.gone("Log 105 kcal")
        screen.waitUntil(5_000) { !plate.exists() }
        assertEquals(json("[]"), saved("foodLog"))
    }

    @Test fun `macros, the goal and its fill, quick kcal past the goal, yesterday on the strip, the credit`() {
        screen.tab("Food")
        screen.has("2,400 kcal left", "Today", "Previous week", "Food data", "Calories 0 / 2,400 kcal", "Protein 0 / 160 g", "Carbs 0 g", "Fat 0 g", "Change goal")
        screen.tap("Change goal")
        screen.has("Goal per day")
        screen.tap("Fill carbs and fat from the rest")
        screen.has("carbs goal 289 g", "fat goal 67 g")
        screen.tap("Save goal")
        screen.has("Carbs 0 / 289 g", "Fat 0 / 67 g")
        screen.tap("Quick kcal")
        screen.has("Add 0 kcal")
        screen.type("2520")
        screen.tap("Add 2,520 kcal")
        screen.has("120 kcal over", "Calories 2,520 / 2,400 kcal · 120 over")

        screen.tap("Tue 24")
        screen.has("Yesterday", "Nothing logged on this day yet.", "2,400 kcal left")
        screen.tap("Quick kcal")
        screen.type("500")
        screen.tap("Add 500 kcal")
        screen.has("1,900 kcal left", "Tue 24, 500 kcal")
        screen.tab("Food")
        screen.has("Today", "120 kcal over", "Wed 25, 2,520 kcal, today")
        screen.tap("Next week")
        screen.has("Today")
        assertEquals(json("""{"kcal": 2400, "protein": 160, "carbs": 289, "fat": 67}"""), saved("foodGoal"))
        assertEquals(json("[[2520, \"2026-11-25\"], [500, \"2026-11-24\"]]"), savedRows("foodLog", "kcal", "day"))

        screen.tap("Food data")
        screen.has("Food data: Open Food Facts (ODbL) and USDA FoodData Central")
        screen.tap("Close")
    }

    @Test fun `search, a hit looked up by its barcode, Again with Undo, an entry changed and deleted with Undo`() {
        app.answers("search/skyr", "product/4337185761638")
        screen.tab("Food")
        screen.tap("+ Add")
        screen.tap("Search foods")
        screen.type("skyr")
        screen.has("Skyr Natur")
        screen.tap("Search Open Food Facts")
        screen.await("Skyr Vanille")
        screen.has("Skyr Natur", "OPEN FOOD FACTS", "K Classic · tap to look up its calories")
        assertTrue(foodLog().toString(), "answered search skyr from files/off (found)" in foodLog())
        screen.tap("K Classic · tap to look up its calories")
        screen.await("Add 64 kcal to the plate")
        screen.has("Skyr natur", "K-Classic", "64 kcal · 11 g protein", "1 serving 100 g", "1 pack 500 g")
        screen.tap("Close")

        screen.tap("Put Skyr Natur on the plate")
        screen.tap("Log 270 kcal")
        screen.has("2,130 kcal left")
        // The snackbar of the log sits over the chips at the end of the list until it times out.
        screen.later(10)
        screen.tap("Add Skyr Natur 450 g again")
        screen.has("Added Skyr Natur, 450 g", "10:00, 2 foods, 540 kcal")
        screen.tap("Undo")
        screen.has("Skyr Natur, 450 g, 270 kcal")

        screen.tap("Skyr Natur, 450 g, 270 kcal")
        screen.has("Save 270 kcal")
        screen.tap("½ = 225 g")
        screen.tap("Save 135 kcal")
        screen.swipeAway("Skyr Natur, 225 g, 135 kcal")
        screen.has("Deleted Skyr Natur", "Nothing logged on this day yet.")
        screen.tap("Undo")
        screen.has("Skyr Natur, 225 g, 135 kcal")
        assertEquals(json("[[225, 135]]"), savedRows("foodLog", "amount", "kcal"))
    }

    @Test fun `meals, copy yesterday and the week in Progress`() {
        screen.tab("Food")
        screen.tap("Tue 24")
        screen.has("Yesterday")
        screen.tap("+ Add")
        screen.tap("Type a barcode")
        barcode("4056489000006")
        screen.has(Regex("Skyr Natur is on the plate.*"))
        screen.tap("Close")
        screen.tap("Search foods")
        screen.type("oats")
        screen.tap("Put Oats, rolled on the plate")
        screen.tap("Log 573 kcal")
        screen.has("1,827 kcal left")
        // The snackbar of the log sits over the end of the list until it times out.
        screen.later(10)
        screen.tap("Save as meal")
        screen.has("New meal", "Name of the meal", "Breakfast", "2 foods · 573 kcal · 60 g protein", "Save meal")
        screen.tap("Save meal")
        screen.has("MEALS · ONE TAP ADDS", "Add meal Breakfast, 573 kcal", "Edit meals")

        screen.tab("Food")
        screen.has("Today", "Nothing logged on this day yet.", "Copy yesterday", "2 foods · 573 kcal")
        screen.tap("Copy yesterday")
        screen.has("Copied 2 foods", "1,827 kcal left")
        screen.tap("Undo")
        screen.has("2,400 kcal left")
        screen.tap("Add meal Breakfast, 573 kcal")
        screen.has(Regex("Added Breakfast, 2 foods.*"))
        screen.has("1,827 kcal left")
        assertEquals(json("""[["Skyr Natur", 450, 270], ["Oats, rolled", 80, 303]]"""), savedRows("meals.0.items", "name", "amount", "kcal"))
        assertEquals(json("""[["Skyr Natur", 450, 270], ["Oats, rolled", 80, 303], ["Skyr Natur", 450, 270], ["Oats, rolled", 80, 303]]"""), savedRows("foodLog", "name", "amount", "kcal"))

        screen.later(10)
        screen.tap("Edit meals")
        screen.has("Meals")
        screen.tap("Breakfast")
        screen.has("Change meal")
        screen.tap("amount of Oats, rolled 80 g")
        screen.type("40")
        screen.has("152 kcal · 5 g protein")
        screen.tap("Remove Skyr Natur")
        screen.has("1 food · 152 kcal · 5 g protein")
        screen.tap("Save meal")
        screen.has("1 food · 152 kcal")
        assertEquals(json("""[["Oats, rolled", 40, 152]]"""), savedRows("meals.0.items", "name", "amount", "kcal"))
        assertEquals(json("[[270], [303], [270], [303]]"), savedRows("foodLog", "kcal"))
        screen.tap("Breakfast")
        screen.tap("Delete meal")
        screen.tap("Tap again to delete")
        screen.has("Meals")
        assertEquals(json("[]"), saved("meals"))
        assertEquals(json("[[270], [303], [270], [303]]"), savedRows("foodLog", "kcal"))

        screen.tap("Close")
        screen.has("1,827 kcal left")
        screen.tab("Progress")
        screen.tap("Charts")
        screen.has("Last 7 days", "Food", "Goal 2,400 kcal", "Tue, 573 kcal, 60 g protein", "Avg kcal", "573", "60 g", "0 of 7")
        screen.tap("Tue, 573 kcal, 60 g protein")
        screen.has("Yesterday", "1,827 kcal left")
    }

    @Test fun `a weight logged, corrected, an earlier day, deleted with Undo, and what the lifter burns still learning`() {
        screen.tab("Food")
        screen.has("WEIGHT", "Log your weight on a few mornings to see your trend.", "Log weight")
        screen.tap("Log weight")
        screen.has("Save 80 kg", "Weight", "Previous day", "Today", "weight 80 kg")
        screen.tap("weight 80 kg")
        screen.type("82.4")
        screen.tap("Save 82.4 kg")
        screen.has("Weight trend 82.4 kg", "Change today's weight")

        screen.tap("Change today's weight")
        screen.has("Logged 82.4 kg on this day. Save replaces it.")
        screen.tap("weight 82.4 kg")
        screen.type("82.1")
        screen.tap("Save 82.1 kg")
        screen.has("Weight trend 82.1 kg")

        screen.tap("Change today's weight")
        screen.tap("Previous day")
        screen.has("Yesterday")
        screen.tap("weight 82.1 kg")
        screen.type("82.8")
        screen.tap("Save 82.8 kg")
        screen.has("Weight trend 82.7 kg")
        assertEquals(json("""[[82.8, "2026-11-24"], [82.1, "2026-11-25"]]"""), savedRows("weights", "kg", "day"))

        screen.tap("Change today's weight")
        screen.tap("Delete")
        screen.has("Deleted the weight of today", "Weight trend 82.8 kg")
        screen.tap("Undo")
        screen.has("Weight trend 82.7 kg")
        assertEquals(json("[[82.8], [82.1]]"), savedRows("weights", "kg"))

        screen.tab("Progress")
        screen.tap("Charts")
        screen.has("Weight", "Trend", "82.7 kg", "You burn", "Learning", "0 of 14 days", "Log weight")
        screen.has("Weight over time, 2 weights, trend 82.7 kg, from 82.8 kg yesterday")
    }

    @Test fun `pictures - a category's, a name's, and one chosen that stays after a restart`() {
        app.answers("product/4311501065525")
        screen.tab("Food")
        screen.tap("+ Add")
        screen.tap("Type a barcode")
        barcode("4311501065525")
        screen.await(Regex("Milch is on the plate, 1 food now.*"))
        barcode("4056489000006")
        screen.has(Regex("Skyr Natur is on the plate, 2 foods now.*"))
        screen.tap("Close")
        screen.tap("Plate, 2 foods · 910 kcal")
        screen.tap("Milch, 1000 ml, 640 kcal")
        screen.has("Save 640 kcal", "Picture: Milk")
        screen.tap("Close")
        screen.tap("Skyr Natur, 450 g, 270 kcal")
        screen.has("Save 270 kcal", "Picture: Yogurt")
        screen.tap("Picture: Yogurt")
        screen.has("Picture", "Close", "Ice cream", "Milk", "Yogurt", "Cookie")
        // Back puts the grid away without a choice; the sheet stays.
        screen.back()
        screen.has("Save 270 kcal", "Picture: Yogurt")
        screen.tap("Picture: Yogurt")
        screen.tap("Ice cream")
        screen.has("Picture: Ice cream", "Save 270 kcal")
        screen.tap("Close")
        assertTrue(saved("foods").toString(), Regex("\"id\":\"off:4311501065525\"[^}]*\"icon\":\"milk\"").containsMatchIn(saved("foods").toString()))
        assertTrue(saved("foods").toString(), Regex("\"id\":\"off:4056489000006\"[^}]*\"icon\":\"ice_cream\"").containsMatchIn(saved("foods").toString()))

        screen.tap("Plate, 2 foods · 910 kcal")
        screen.has("Clear")
        screen.tap("New food")
        screen.has("Save food", "Picture: Plate")
        screen.tap("Name")
        screen.type("Hafermilch")
        screen.has("Picture: Milk")
        screen.tap("Picture: Milk")
        screen.tap("Oats and muesli")
        screen.has("Picture: Oats and muesli")
        screen.tap("calories per 100 g 0 kcal")
        screen.type("45")
        screen.tap("Save food")
        screen.has("Add 45 kcal to the plate", "Picture: Oats and muesli")
        screen.tap("Close")
        screen.tap("Log 910 kcal")
        screen.has("Logged 2 foods")
        assertTrue(saved("foods").toString(), Regex("\"name\":\"Hafermilch\"[^}]*\"icon\":\"cereal\"|\"icon\":\"cereal\"[^}]*\"name\":\"Hafermilch\"").containsMatchIn(saved("foods").toString()))

        app.restart()
        screen.tab("Food")
        screen.has("1,490 kcal left")
        screen.tap("10:00, 2 foods, 910 kcal")
        screen.tap("Skyr Natur, 450 g, 270 kcal")
        screen.has("Save 270 kcal", "Picture: Ice cream")
        screen.tap("Close")
    }

    @Test fun `the photo of a label - its way in from the add screen and a new food, and the credit`() {
        // The photo itself (the picker, ML Kit reading it) is the journey's: Robolectric has neither.
        screen.tab("Food")
        screen.tap("+ Add")
        screen.has("Type a barcode", "Label", "New food")
        screen.tap("Label")
        screen.has("Photo of the label", "Take a photo", "Choose a photo", "Close")
        screen.tap("Close")
        screen.tap("New food")
        screen.has("Read from a photo")
        screen.tap("Read from a photo")
        screen.has("Choose a photo")
        screen.tap("Close")
        screen.has("Search foods")
        screen.tap("Back")
        screen.tap("Food data")
        screen.has("A photo of a label is read on this phone and then thrown away: it is not kept, and not sent anywhere.")
        screen.has(Regex("A photo of a dish is shown to Gemini Nano.*"))
        screen.tap("Close")
        assertEquals(json("1"), json(saved("foods").let { (it as kotlinx.serialization.json.JsonArray).size.toString() }))
    }

    @Test fun `the day copied as Markdown, and shared`() {
        screen.tab("Food")
        screen.tap("Copy day as Markdown")
        screen.has("Nothing is logged on this day.")
        screen.tap("+ Add")
        screen.tap("Put Oats, rolled on the plate")
        screen.tap("Put Banana on the plate")
        screen.tap("Log 408 kcal")
        screen.has("1,992 kcal left")
        screen.tap("Copy day as Markdown")
        screen.has("Copied as Markdown", "Share")
        val clipboard = RuntimeEnvironment.getApplication().getSystemService(ClipboardManager::class.java)
        val copied = clipboard.primaryClip?.getItemAt(0)?.text.toString()
        val day = Regex(
            "## Food · Wed, Nov 25, 2026\n\n\\*\\*408 kcal\\*\\* of 2,400 · Protein 12\\.1 of 160 g · Carbs 81\\.9 g · Fat 5\\.1 g\n.*" +
                "\\| Oats, rolled \\| 80 g \\| 303 \\| 10\\.8 g \\| 55 g \\| 4\\.7 g \\|\n.*" +
                "\\| \\*\\*Total\\*\\* \\|  \\| \\*\\*408\\*\\* \\| \\*\\*12\\.1 g\\*\\* \\| \\*\\*81\\.9 g\\*\\* \\| \\*\\*5\\.1 g\\*\\* \\|\n.*",
            RegexOption.DOT_MATCHES_ALL,
        )
        assertTrue(copied, day.matches(copied))
        screen.tap("Share")
        val chooser = shadowOf(RuntimeEnvironment.getApplication()).nextStartedActivity
        val shared = chooser.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)?.getStringExtra(Intent.EXTRA_TEXT)
        assertEquals(copied, shared)
        assertFalse(copied.isBlank())
    }

    @Test @Fixture("food-history") fun `usual at this time, a breakfast of usual foods in five taps, the timeline and an entry's time`() {
        screen.tab("Food")
        screen.has("2,400 kcal left")
        screen.tap("+ Add")
        screen.has("USUAL AT THIS TIME", "RECENT")
        // At 10:00 the food nearest the time of day is the Skyr of 10:30.
        val shown = screen.texts()
        val usual = shown.subList(shown.indexOf("USUAL AT THIS TIME"), shown.indexOf("RECENT"))
        assertTrue(usual.toString(), "Skyr Natur" in usual)
        val put = Regex("Put .* on the plate")
        screen.tap(put)
        screen.has(Regex("Plate, 1 food · .*"))
        screen.tap(put)
        screen.has(Regex("Plate, 2 foods · .*"))
        screen.tap(put)
        screen.has(Regex("Plate, 3 foods · .*"))
        screen.tap(Regex("Log .* kcal"))
        screen.has("Logged 3 foods")
        screen.tap(Regex("10:00, 3 foods, .* kcal"))
        val skyr = Regex("Skyr Natur, [0-9.]+ (g|ml), [0-9,]+ kcal")
        screen.has(skyr)
        screen.has("Save these as a meal")
        assertEquals(115, (saved("foodLog") as kotlinx.serialization.json.JsonArray).size)
        val logged = (savedRows("foodLog", "name", "at") as kotlinx.serialization.json.JsonArray).takeLast(3)
        assertTrue(logged.toString(), logged.any { "Skyr Natur" in it.toString() })
        assertEquals(1, logged.map { (it as kotlinx.serialization.json.JsonArray)[1] }.toSet().size)

        screen.tap(skyr)
        screen.has(Regex("Save [0-9,]+ kcal"), Regex("hour 10"))
        screen.tap("hour 10")
        screen.type("20")
        screen.done()
        screen.has("hour 20")
        screen.tap(Regex("Save [0-9,]+ kcal"))
        screen.has(Regex("[0-9]{2}:[0-9]{2}, 2 foods, .* kcal"))
        val after = (savedRows("foodLog", "at") as kotlinx.serialization.json.JsonArray).takeLast(3)
        assertEquals(2, after.toSet().size)
    }

    @Test @Fixture("food-weight") fun `the weekly check-in taken, gone after a restart, and the weights in Progress`() {
        screen.tab("Food")
        screen.has(
            "WEEKLY CHECK-IN", "Last week: 2,456 kcal a day, 7 days logged.", "Trend −0.4 kg: 83.7 kg to 83.3 kg.", "You burn about 2,740 kcal.",
            "New target: 2,200 kcal (was 2,400)", "Take 2,200 kcal", "Keep 2,400 kcal",
        )
        screen.tap("Take 2,200 kcal")
        screen.has("Target this week: 2,200 kcal", "2,200 kcal left")
        screen.gone("WEEKLY CHECK-IN")
        assertEquals(json("2200"), saved("foodGoal.kcal"))
        assertEquals(json("[true, 2200, 2400]"), json(listOf("took", "target", "was").map { saved("weightGoal.checkIn.$it") }.toString()))

        app.restart()
        screen.has("Start workout")
        screen.tab("Food")
        screen.has("2,200 kcal left")
        screen.gone("WEEKLY CHECK-IN")
        screen.has(Regex("Weight trend 83.3 kg, (down|up) 0\\.[0-9] kg a week|Weight trend 83.3 kg, steady"))
        screen.has("WEIGHT", "Log weight")

        screen.tab("Progress")
        screen.tap("Charts")
        screen.has("Weight", "3 months", "1 year", "All", "Trend", "83.3 kg", "Change", "You burn", "kcal a day", "Log weight")
        screen.has(Regex("Weight over time, 22 weights, trend 83.3 kg, .*"), Regex("[0-9],[0-9]{3}"))
        screen.tap("All")
        screen.has(Regex("Weight over time, 22 weights, trend 83.3 kg, .*, from 84.2 kg on .*"))
    }

    @Test @Fixture("food-weight") fun `the check-in after a restart, the goal's pace, its switch off and on, and the check-in kept`() {
        screen.tab("Food")
        screen.has("WEEKLY CHECK-IN")
        app.restart()
        screen.tab("Food")
        screen.has("WEEKLY CHECK-IN", "New target: 2,200 kcal (was 2,400)")
        screen.tap("Change goal")
        screen.has("Goal per day", "Protein from your weight: 167 g")
        screen.has("WEIGHT GOAL", "Lose", "Maintain", "Gain", "pace 0.5 kg a week", "Adjust my target each week", "Save goal", "At most 1% of your weight a week: 0.8 kg.")
        screen.has(Regex("You burn about [0-9],[0-9]{3} kcal. For this goal: [0-9],[0-9]{3} kcal a day."))
        screen.tap("Increase pace")
        screen.has("pace 0.6 kg a week")
        screen.tap("Adjust my target each week")
        screen.tap("Save goal")
        screen.has("Change goal", "2,400 kcal left")
        screen.gone("WEEKLY CHECK-IN")
        assertEquals(json("""{"direction": "lose", "pace": 0.6, "adjust": false, "checkIn": null}"""), saved("weightGoal"))
        assertEquals(json("""{"kcal": 2400, "protein": 160, "carbs": null, "fat": null}"""), saved("foodGoal"))

        screen.tap("Change goal")
        screen.has("Goal per day")
        screen.tap("Adjust my target each week")
        screen.tap("Save goal")
        screen.has("WEEKLY CHECK-IN", "New target: 2,100 kcal (was 2,400)", "Keep 2,400 kcal")
        screen.tap("Keep 2,400 kcal")
        screen.has("2,400 kcal left")
        screen.gone("WEEKLY CHECK-IN")
        assertEquals(json("2400"), saved("foodGoal.kcal"))
        assertEquals(json("true"), saved("weightGoal.adjust"))
        assertEquals(json("[false, 2100, 2400]"), json(listOf("took", "target", "was").map { saved("weightGoal.checkIn.$it") }.toString()))
    }
}

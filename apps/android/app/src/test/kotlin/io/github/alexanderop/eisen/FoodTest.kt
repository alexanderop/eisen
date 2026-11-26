package io.github.alexanderop.eisen

import io.github.alexanderop.eisen.app.state.Loaded
import io.github.alexanderop.eisen.app.state.encodeState
import io.github.alexanderop.eisen.app.state.freshState
import io.github.alexanderop.eisen.app.state.loadState
import io.github.alexanderop.eisen.core.WeightUnit
import io.github.alexanderop.eisen.feature.food.AmountChip
import io.github.alexanderop.eisen.feature.food.Food
import io.github.alexanderop.eisen.feature.food.FoodEntry
import io.github.alexanderop.eisen.feature.food.FoodGoal
import io.github.alexanderop.eisen.feature.food.FoodSource
import io.github.alexanderop.eisen.feature.food.FoodUnit
import io.github.alexanderop.eisen.feature.food.MAX_AMOUNT
import io.github.alexanderop.eisen.feature.food.MAX_ENTRY_KCAL
import io.github.alexanderop.eisen.feature.food.MAX_GOAL_KCAL
import io.github.alexanderop.eisen.feature.food.MAX_KCAL_PER_100
import io.github.alexanderop.eisen.feature.food.MAX_MACRO_PER_100
import io.github.alexanderop.eisen.feature.food.QUICK_NAME
import io.github.alexanderop.eisen.feature.food.again
import io.github.alexanderop.eisen.feature.food.amountChips
import io.github.alexanderop.eisen.feature.food.barcodeProblem
import io.github.alexanderop.eisen.feature.food.dayAfter
import io.github.alexanderop.eisen.feature.food.dayBefore
import io.github.alexanderop.eisen.feature.food.dayTitle
import io.github.alexanderop.eisen.feature.food.entriesOf
import io.github.alexanderop.eisen.feature.food.entryOf
import io.github.alexanderop.eisen.feature.food.entryWithin
import io.github.alexanderop.eisen.feature.food.foodOfEntry
import io.github.alexanderop.eisen.feature.food.foodWithin
import io.github.alexanderop.eisen.feature.food.goalWithin
import io.github.alexanderop.eisen.feature.food.isPastOrToday
import io.github.alexanderop.eisen.feature.food.kcalFromKj
import io.github.alexanderop.eisen.feature.food.kcalText
import io.github.alexanderop.eisen.feature.food.localDay
import io.github.alexanderop.eisen.feature.food.normalBarcode
import io.github.alexanderop.eisen.feature.food.quickEntry
import io.github.alexanderop.eisen.feature.food.searchFoods
import io.github.alexanderop.eisen.feature.food.startAmount
import io.github.alexanderop.eisen.feature.food.totalsOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

/** The pure part of the food log: the maths, the days, the limits, the file and the selectors of the screens. */
class FoodTest {
    private val skyr = Food("off:4056489000006", "Skyr Natur", "Milbona", "4056489000006", kcal = 60.0, protein = 11.0, carbs = 4.0, fat = 0.2, pack = 450.0, source = FoodSource.SCANNED)
    private val oats = Food("fdc:173904", "Oats", kcal = 379.0, protein = 13.2, serving = 80.0, source = FoodSource.PLAIN)
    private val cola = Food("off:5449000000996", "Coca-Cola", kcal = 44.0, protein = 0.0, unit = FoodUnit.ML, serving = 330.0, pack = 330.0, source = FoodSource.SCANNED)
    private val berlin = ZoneId.of("Europe/Berlin")
    private val newYork = ZoneId.of("America/New_York")

    private fun at(text: String): Long = ZonedDateTime.parse(text).toInstant().toEpochMilli()

    @Test fun `an entry counts its amount per 100, kcal whole and protein to a tenth, halves up`() {
        val entry = entryOf(skyr, 450.0, "e1", 5, "2026-11-26")
        assertEquals(270, entry.kcal)
        assertEquals(49.5, entry.protein, 0.0)
        assertEquals(18.0, entry.carbs!!, 0.0)
        assertEquals(0.9, entry.fat!!, 0.0)
        // 539 kcal per 100 g, 15 g: 80.85 rounds up to 81; 59.9 per 100, 450 g: 269.55 to 270.
        assertEquals(81, entryOf(skyr.copy(kcal = 539.0), 15.0, "e", 0, "d").kcal)
        assertEquals(270, entryOf(skyr.copy(kcal = 59.9), 450.0, "e", 0, "d").kcal)
        assertEquals(269, entryOf(skyr.copy(kcal = 59.8), 450.0, "e", 0, "d").kcal)
        // Unknown carbs and fat stay unknown.
        assertNull(entryOf(oats, 80.0, "e", 0, "d").carbs)
    }

    @Test fun `a drink counts per 100 ml and its entry says ml`() {
        val entry = entryOf(cola, 330.0, "e", 0, "d")
        assertEquals(145, entry.kcal)
        assertEquals(FoodUnit.ML, entry.unit)
        assertEquals(330.0, entry.amount!!, 0.0)
    }

    @Test fun `kilojoules turn into kilocalories by 4,184`() {
        assertEquals(538.24, kcalFromKj(2252.0), 0.01)
        assertEquals(100.0, kcalFromKj(418.4), 1e-9)
    }

    @Test fun `a quick entry has a name, no food and no amount`() {
        val entry = quickEntry(850, null, "  ", "q", 0, "d")
        assertEquals(QUICK_NAME, entry.name)
        assertNull(entry.foodId)
        assertNull(entry.amount)
        assertEquals(850, entry.kcal)
        assertEquals(0.0, entry.protein, 0.0)
        assertEquals("Burger", quickEntry(850, 40, " Burger ", "q", 0, "d").name)
    }

    @Test fun `an entry whose food is gone gives back its numbers per 100`() {
        val entry = entryOf(skyr, 450.0, "e", 0, "d")
        val back = foodOfEntry(entry)!!
        assertEquals(60.0, back.kcal, 1e-9)
        assertEquals(11.0, back.protein, 1e-9)
        assertEquals(270, entryOf(back, 450.0, "e", 0, "d").kcal)
        assertNull(foodOfEntry(quickEntry(500, null, "", "q", 0, "d")))
    }

    @Test fun `a day is the local calendar day, midnight belongs to the next one`() {
        assertEquals("2026-11-26", localDay(at("2026-11-26T23:59:59.999+01:00[Europe/Berlin]"), berlin))
        assertEquals("2026-11-27", localDay(at("2026-11-27T00:00:00+01:00[Europe/Berlin]"), berlin))
        // The night the clocks go back has 25 hours: 00:30 after it is still the next day.
        assertEquals("2026-10-25", localDay(at("2026-10-25T23:30:00+01:00[Europe/Berlin]"), berlin))
        assertEquals("2026-10-26", localDay(at("2026-10-26T00:30:00+01:00[Europe/Berlin]"), berlin))
    }

    @Test fun `what was eaten stays on its day after a flight to another time zone`() {
        // Half past midnight in Berlin is still the evening before in New York.
        val eaten = at("2026-11-27T00:30:00+01:00[Europe/Berlin]")
        assertEquals("2026-11-27", localDay(eaten, berlin))
        assertEquals("2026-11-26", localDay(eaten, newYork))
        val entry = entryOf(skyr, 450.0, "e", eaten, localDay(eaten, berlin))
        assertEquals(listOf(entry), entriesOf(listOf(entry), "2026-11-27"))
        assertEquals(emptyList<FoodEntry>(), entriesOf(listOf(entry), "2026-11-26"))
    }

    @Test fun `days step by one, never into a date that is not one`() {
        assertEquals("2026-11-25", dayBefore("2026-11-26"))
        assertEquals("2026-03-01", dayAfter("2026-02-28"))
        assertEquals("2028-02-29", dayAfter("2028-02-28"))
        assertTrue(isPastOrToday("2026-11-26", "2026-11-26"))
        assertTrue(!isPastOrToday("2026-11-27", "2026-11-26"))
        assertTrue(!isPastOrToday("nope", "2026-11-26"))
        assertEquals("Today", dayTitle("2026-11-26", "2026-11-26"))
        assertEquals("Yesterday", dayTitle("2026-11-25", "2026-11-26"))
        assertEquals("Tue, Nov 24", dayTitle("2026-11-24", "2026-11-26"))
    }

    @Test fun `a day adds up its entries in the order they were eaten`() {
        val first = entryOf(skyr, 450.0, "a", 10, "d")
        val second = entryOf(oats, 80.0, "b", 5, "d")
        val other = entryOf(oats, 80.0, "c", 7, "e")
        assertEquals(listOf(second, first), entriesOf(listOf(first, other, second), "d"))
        val totals = totalsOf(entriesOf(listOf(first, other, second), "d"))
        assertEquals(270 + 303, totals.kcal)
        assertEquals(49.5 + 10.6, totals.protein, 1e-9)
        assertEquals("1,240", kcalText(1240))
        assertEquals("45,000", kcalText(MAX_ENTRY_KCAL))
    }

    @Test fun `every number of a food, an entry and a goal stays in its limit`() {
        val wild = Food("x", "n".repeat(200), "b".repeat(200), "12345", kcal = Double.MAX_VALUE, protein = Double.NaN, carbs = -5.0, fat = Double.POSITIVE_INFINITY, serving = 1e12, pack = -3.0, source = FoodSource.OWN)
        val kept = foodWithin(wild)
        assertEquals(MAX_KCAL_PER_100, kept.kcal, 0.0)
        assertEquals(0.0, kept.protein, 0.0)
        assertEquals(0.0, kept.carbs!!, 0.0)
        assertEquals(0.0, kept.fat!!, 0.0)
        assertEquals(MAX_AMOUNT, kept.serving!!, 0.0)
        assertNull(kept.pack)
        assertNull("a barcode that cannot be one goes", kept.barcode)
        assertEquals(60, kept.name.length)
        val huge = entryOf(kept.copy(kcal = MAX_KCAL_PER_100, protein = MAX_MACRO_PER_100), Double.MAX_VALUE, "e", 0, "d")
        assertEquals(MAX_ENTRY_KCAL, huge.kcal)
        assertEquals(MAX_AMOUNT, huge.amount!!, 0.0)
        assertEquals(MAX_ENTRY_KCAL, entryWithin(huge.copy(kcal = Int.MAX_VALUE)).kcal)
        assertEquals(FoodGoal(MAX_GOAL_KCAL, null), goalWithin(FoodGoal(Int.MAX_VALUE, 0)))
        assertEquals(FoodGoal(), goalWithin(FoodGoal(-5, -1)))
    }

    @Test fun `a backup carries the foods, the log and the goal, and an old file loads without them`() {
        val state = freshState(WeightUnit.KG).copy(
            foods = listOf(skyr, cola),
            foodLog = listOf(entryOf(skyr, 450.0, "e1", 5, "2026-11-26"), quickEntry(800, 30, "Pizza", "q1", 9, "2026-11-26")),
            foodGoal = FoodGoal(2400, 160),
        )
        assertEquals(Loaded.Ok(state), loadState(encodeState(state)))
        assertEquals(Loaded.Ok(state), loadState(encodeState(state, pretty = true)))
        // A file from before the feature: no field of it, everything else as it was.
        val old = encodeState(freshState(WeightUnit.KG)).replace(Regex(""","foods":\[\],"foodLog":\[\],"foodGoal":\{[^}]*\}"""), "")
        assertTrue(old, "food" !in old)
        val loaded = loadState(old) as Loaded.Ok
        assertEquals(freshState(WeightUnit.KG), loaded.state)
    }

    @Test fun `a hand-edited file is brought into the limits, a repeated id kept once`() {
        val state = freshState(WeightUnit.KG).copy(
            foods = listOf(skyr.copy(kcal = 5000.0), skyr),
            foodLog = listOf(entryOf(skyr, 450.0, "e1", 5, "d").copy(kcal = 99_999_999)),
            foodGoal = FoodGoal(99_999, 0),
        )
        val loaded = (loadState(encodeState(state)) as Loaded.Ok).state
        assertEquals(listOf(skyr.copy(kcal = MAX_KCAL_PER_100)), loaded.foods)
        assertEquals(MAX_ENTRY_KCAL, loaded.foodLog.single().kcal)
        assertEquals(FoodGoal(MAX_GOAL_KCAL, null), loaded.foodGoal)
    }

    @Test fun `the add sheet starts at the last amount, else the serving, else the pack, else 100`() {
        assertEquals(450.0, startAmount(skyr, emptyList()), 0.0)
        assertEquals(80.0, startAmount(oats, emptyList()), 0.0)
        assertEquals(100.0, startAmount(oats.copy(serving = null), emptyList()), 0.0)
        val log = listOf(entryOf(skyr, 200.0, "a", 1, "d"), entryOf(skyr, 300.0, "b", 3, "d"), entryOf(skyr, 250.0, "c", 2, "d"))
        assertEquals(300.0, startAmount(skyr, log), 0.0)
    }

    @Test fun `the chips are half, one and two of the serving, or of the pack`() {
        assertEquals(listOf(AmountChip("½", 225.0), AmountChip("1 pack 450 g", 450.0), AmountChip("2", 900.0)), amountChips(skyr))
        assertEquals(listOf(AmountChip("½", 40.0), AmountChip("1 serving 80 g", 80.0), AmountChip("2", 160.0)), amountChips(oats))
        assertEquals(
            listOf(AmountChip("½", 15.0), AmountChip("1 serving 30 g", 30.0), AmountChip("2", 60.0), AmountChip("1 pack 400 g", 400.0)),
            amountChips(oats.copy(serving = 30.0, pack = 400.0)),
        )
        // A drink's pack that is its serving gets no chip of its own.
        assertEquals(listOf("½", "1 serving 330 ml", "2"), amountChips(cola).map { it.label })
        assertEquals(emptyList<AmountChip>(), amountChips(oats.copy(serving = null)))
    }

    @Test fun `again offers the foods logged most often lately, each with its last amount`() {
        val day = 24 * 60 * 60 * 1000L
        val now = 100 * day
        val log = listOf(
            entryOf(oats, 80.0, "1", now - 3 * day, "d"),
            entryOf(skyr, 450.0, "2", now - 2 * day, "d"),
            entryOf(oats, 60.0, "3", now - day, "d"),
            entryOf(cola, 330.0, "4", now - 40 * day, "d"),
            quickEntry(800, null, "", "5", now - day, "d"),
        )
        val foods = listOf(skyr, oats, cola).associateBy { it.id }
        val items = again(log, foods::get, now)
        assertEquals(listOf(oats.id to 60.0, skyr.id to 450.0), items.map { it.food.id to it.amount })
        // A food the phone no longer knows is not offered.
        assertEquals(listOf(skyr.id), again(log, { id -> foods[id]?.takeIf { it != oats } }, now).map { it.food.id })
        // Equal counts: the newest first.
        assertEquals(listOf(oats.id, skyr.id), again(log.filter { it.id != "1" }, foods::get, now).map { it.food.id })
        assertEquals(listOf(skyr.id, oats.id), again(log.filter { it.id != "1" && it.id != "3" } + entryOf(oats, 60.0, "6", now - 5 * day, "d"), foods::get, now).map { it.food.id })
    }

    @Test fun `the search finds every word, starts of words first, the lifter's own before the plain ones`() {
        val own = Food("own:1", "Overnight oats", kcal = 150.0, source = FoodSource.OWN)
        val boat = Food("fdc:2", "Goat cheese", kcal = 300.0, source = FoodSource.PLAIN)
        val foods = listOf(oats, boat, own, skyr)
        assertEquals(listOf(own, oats), searchFoods(foods, "oats", emptyList()))
        assertEquals(listOf(own, oats, boat), searchFoods(foods, "oat", emptyList()))
        assertEquals(listOf(skyr), searchFoods(foods, "milbona sky", emptyList()))
        assertEquals(emptyList<Food>(), searchFoods(foods, "  ", emptyList()))
        // Logged more often comes first among equal matches.
        assertEquals(listOf(oats, own), searchFoods(foods, "oats", listOf(entryOf(oats, 80.0, "e", 0, "d"))))
    }

    @Test fun `a typed barcode is checked before it is looked up`() {
        assertNull(barcodeProblem("3017620422003"))
        assertNull(barcodeProblem("4056489000006"))
        assertNull(barcodeProblem("96385074"))
        assertNull(barcodeProblem("036000291452"))
        assertEquals("The last digit does not match: check for a typo.", barcodeProblem("3017620422004"))
        assertEquals("A barcode has 8, 12 or 13 digits.", barcodeProblem("12345"))
        assertEquals("A barcode has digits only.", barcodeProblem("30176a0422003"))
        assertEquals("Type the digits under the bars.", barcodeProblem(" "))
        assertEquals("0036000291452", normalBarcode("036000291452"))
        assertEquals("3017620422003", normalBarcode("3017620422003"))
    }
}

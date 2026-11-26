package io.github.alexanderop.eisen

import io.github.alexanderop.eisen.app.elm.AmountFor
import io.github.alexanderop.eisen.app.elm.Env
import io.github.alexanderop.eisen.app.elm.FoodSheet
import io.github.alexanderop.eisen.app.elm.MealSheet
import io.github.alexanderop.eisen.app.elm.Model
import io.github.alexanderop.eisen.app.elm.Msg
import io.github.alexanderop.eisen.app.elm.openFoodDay
import io.github.alexanderop.eisen.app.elm.update
import io.github.alexanderop.eisen.app.state.freshState
import io.github.alexanderop.eisen.core.WeightUnit
import io.github.alexanderop.eisen.feature.food.Food
import io.github.alexanderop.eisen.feature.food.FoodEntry
import io.github.alexanderop.eisen.feature.food.FoodSource
import io.github.alexanderop.eisen.feature.food.atOfDay
import io.github.alexanderop.eisen.feature.food.clockText
import io.github.alexanderop.eisen.feature.food.entryOf
import io.github.alexanderop.eisen.feature.food.quickEntry
import io.github.alexanderop.eisen.feature.food.timeline
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

/** The day as a timeline: entries grouped by when they were eaten, and the time of an entry changed from its sheet. */
class TimelineTest {
    private val berlin = ZoneId.of("Europe/Berlin")
    private val oats = Food("fdc:2", "Oats", kcal = 379.0, protein = 13.2, serving = 80.0, source = FoodSource.PLAIN)
    private val skyr = Food("off:1", "Skyr", kcal = 60.0, protein = 11.0, pack = 450.0, source = FoodSource.SCANNED)

    private fun at(date: String, time: String): Long = ZonedDateTime.parse("${date}T$time:00+01:00[Europe/Berlin]").toInstant().toEpochMilli()

    private fun eaten(food: Food, time: String, id: String, date: String = "2026-11-26", day: String = date): FoodEntry = entryOf(food, 100.0, id, at(date, time), day)

    private fun groups(entries: List<FoodEntry>) = timeline(entries, berlin).map { group -> group.minute?.let(::clockText) to group.entries.map { it.id } }

    @Test fun `entries within 30 minutes of the first of a group form one group with its time and its sum`() {
        val day = listOf(eaten(oats, "07:30", "a"), eaten(skyr, "07:45", "b"), eaten(skyr, "08:00", "c"), eaten(oats, "08:01", "d"), eaten(oats, "12:45", "e"), eaten(skyr, "16:00", "f"))
        assertEquals(listOf("07:30" to listOf("a", "b", "c"), "08:01" to listOf("d"), "12:45" to listOf("e"), "16:00" to listOf("f")), groups(day))
        val first = timeline(day, berlin).first()
        assertEquals(379 + 60 + 60, first.totals.kcal)
        // In the order of the day, whatever order they were logged in.
        assertEquals(groups(day), groups(day.reversed()))
    }

    @Test fun `the groups add up to the day`() {
        val day = listOf(eaten(oats, "07:30", "a"), eaten(skyr, "09:00", "b"), eaten(oats, "23:59", "c"), eaten(skyr, "00:00", "d"))
        val all = timeline(day, berlin)
        assertEquals(day.sumOf { it.kcal }, all.sumOf { it.totals.kcal })
        assertEquals(day.map { it.id }.toSet(), all.flatMap { group -> group.entries.map { it.id } }.toSet())
        assertEquals("00:00", clockText(all.first().minute!!))
    }

    @Test fun `entries filled in later for an earlier day sit in one group without a time, at the end`() {
        val later = eaten(oats, "09:00", "late", date = "2026-11-27", day = "2026-11-26")
        val quick = quickEntry(500, null, "", "q", at("2026-11-28", "10:00"), "2026-11-26")
        assertEquals(listOf("07:30" to listOf("a"), null to listOf("late", "q")), groups(listOf(quick, eaten(skyr, "07:30", "a"), later)))
        assertEquals(emptyList<Any>(), timeline(emptyList(), berlin))
    }

    @Test fun `a time of day becomes a moment of that day`() {
        assertEquals(at("2026-11-24", "19:05"), atOfDay("2026-11-24", 19 * 60 + 5, berlin))
        assertNull(atOfDay("not a day", 0, berlin))
        assertEquals("23:59", clockText(5_000))
    }

    // Through update

    private val morning = at("2026-11-26", "09:00")

    private fun Step.food(msg: Msg, now: Long = morning, id: String = "n1"): Step =
        update(msg, model, Env(now, zone = berlin) { id }).let { (next, cmds) -> Step(next, cmds) }

    private fun logged(log: List<FoodEntry>) = Step(Model(freshState(WeightUnit.KG).copy(foods = listOf(skyr), foodLog = log), plainFoods = listOf(oats), now = morning, zone = berlin)).food(Msg.Food.Open)

    @Test fun `the day screen groups its entries`() {
        val day = openFoodDay(logged(listOf(eaten(oats, "07:30", "a"), eaten(skyr, "07:40", "b"))).model)!!
        assertEquals(listOf(listOf("a", "b")), day.groups.map { group -> group.entries.map { it.id } })
    }

    @Test fun `an entry's time changes from its sheet, saved with its amount`() {
        val open = logged(listOf(eaten(skyr, "07:30", "a"))).food(Msg.Food.OpenEntry("a"))
        assertEquals(FoodSheet.Amount(skyr, 100.0, AmountFor.Entry("a"), minute = 7 * 60 + 30), open.model.food.sheet)
        val moved = open.food(Msg.Food.SetTime(12 * 60 + 45))
        assertEquals(12 * 60 + 45, (moved.model.food.sheet as FoodSheet.Amount).minute)
        // Nothing changes before the save.
        assertEquals(at("2026-11-26", "07:30"), moved.state.foodLog.single().at)
        val saved = moved.food(Msg.Food.ConfirmAmount).state.foodLog.single()
        assertEquals(at("2026-11-26", "12:45"), saved.at)
        assertEquals("a", saved.id)
        // Within the day, whatever is sent.
        assertEquals(24 * 60 - 1, (open.food(Msg.Food.SetTime(Int.MAX_VALUE)).model.food.sheet as FoodSheet.Amount).minute)
        // Only an entry's sheet has a time: a new food's has none.
        val forPlate = logged(emptyList()).food(Msg.Food.OpenAdd).food(Msg.Food.Pick(oats.id))
        assertSame(forPlate.model, forPlate.food(Msg.Food.SetTime(60)).model)
    }

    @Test fun `an entry without a time gets one on its own day`() {
        val later = eaten(oats, "08:00", "late", date = "2026-11-26", day = "2026-11-24")
        val open = logged(listOf(later)).food(Msg.Food.OpenDay("2026-11-24")).food(Msg.Food.OpenEntry("late"))
        assertNull((open.model.food.sheet as FoodSheet.Amount).minute)
        val saved = open.food(Msg.Food.SetTime(19 * 60)).food(Msg.Food.ConfirmAmount).state.foodLog.single()
        assertEquals(at("2026-11-24", "19:00"), saved.at)
        assertEquals("2026-11-24", saved.day)
        // Saved without a time set, it keeps the one it had.
        assertEquals(later.at, open.food(Msg.Food.ConfirmAmount).state.foodLog.single().at)
    }

    @Test fun `a quick entry's time changes with it`() {
        val quick = quickEntry(500, null, "", "q", at("2026-11-26", "08:00"), "2026-11-26")
        val saved = logged(listOf(quick)).food(Msg.Food.OpenEntry("q")).food(Msg.Food.SaveQuick(600, null, "", minute = 13 * 60)).state.foodLog.single()
        assertEquals(at("2026-11-26", "13:00") to 600, saved.at to saved.kcal)
    }

    @Test fun `Save as meal works on a group`() {
        val day = logged(listOf(eaten(oats, "07:30", "a"), eaten(skyr, "07:40", "b"), eaten(oats, "12:00", "c")))
        assertEquals(MealSheet.Pick(listOf("a", "b")), (day.food(Msg.Food.NewMealOf(listOf("b", "a"))).model.food.sheet as FoodSheet.Meal).sheet)
        val gone = day.food(Msg.Food.NewMealOf(listOf("x")))
        assertSame(day.model, gone.model)
    }
}

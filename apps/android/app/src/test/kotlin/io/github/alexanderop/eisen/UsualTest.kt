package io.github.alexanderop.eisen

import io.github.alexanderop.eisen.app.elm.Model
import io.github.alexanderop.eisen.app.elm.addSuggestions
import io.github.alexanderop.eisen.app.state.freshState
import io.github.alexanderop.eisen.core.WeightUnit
import io.github.alexanderop.eisen.feature.food.COMMON_FOODS
import io.github.alexanderop.eisen.feature.food.Food
import io.github.alexanderop.eisen.feature.food.FoodEntry
import io.github.alexanderop.eisen.feature.food.FoodSource
import io.github.alexanderop.eisen.feature.food.USUAL_SIZE
import io.github.alexanderop.eisen.feature.food.entryOf
import io.github.alexanderop.eisen.feature.food.localDay
import io.github.alexanderop.eisen.feature.food.minutesApart
import io.github.alexanderop.eisen.feature.food.usualAt
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

/** "Usual at this time": the foods logged around this time of day lately, the most often first. */
class UsualTest {
    private val berlin = ZoneId.of("Europe/Berlin")
    private val skyr = Food("off:1", "Skyr", kcal = 60.0, protein = 11.0, pack = 450.0, source = FoodSource.SCANNED)
    private val oats = Food("fdc:2", "Oats, rolled", kcal = 379.0, protein = 13.2, serving = 80.0, source = FoodSource.PLAIN)
    private val rice = Food("fdc:3", "Rice, white, cooked", kcal = 130.0, protein = 2.7, serving = 150.0, source = FoodSource.PLAIN)
    private val banana = Food("fdc:4", "Banana", kcal = 89.0, protein = 1.1, serving = 118.0, source = FoodSource.PLAIN)
    private val foods = listOf(skyr, oats, rice, banana).associateBy { it.id }

    private fun at(date: String, time: String): Long = ZonedDateTime.parse("${date}T$time:00+01:00[Europe/Berlin]").toInstant().toEpochMilli()

    /** [food] at [amount], eaten on [date] at [time], logged then. */
    private fun eaten(food: Food, date: String, time: String, amount: Double = 100.0, id: String = "$date $time ${food.id}"): FoodEntry {
        val when_ = at(date, time)
        return entryOf(food, amount, id, when_, localDay(when_, berlin))
    }

    private fun usual(log: List<FoodEntry>, now: Long) = usualAt(log, foods::get, now, berlin).map { it.food.name }

    @Test fun `foods logged within two hours of now in the last weeks come first, the most often logged first`() {
        val log = listOf(
            eaten(oats, "2026-11-20", "07:30"), eaten(oats, "2026-11-21", "07:45"), eaten(oats, "2026-11-23", "08:00"),
            eaten(skyr, "2026-11-23", "07:30"), eaten(skyr, "2026-11-24", "09:15"),
            eaten(rice, "2026-11-24", "12:45"), eaten(rice, "2026-11-25", "13:00"), eaten(rice, "2026-11-25", "19:00"),
            eaten(banana, "2026-11-25", "16:00"),
        )
        assertEquals(listOf("Oats, rolled", "Skyr"), usual(log, at("2026-11-26", "08:00")))
        assertEquals(listOf("Rice, white, cooked"), usual(log, at("2026-11-26", "12:30")))
        assertEquals(listOf("Banana", "Rice, white, cooked"), usual(log, at("2026-11-26", "15:00")))
        // Two hours either way, and not a minute more.
        assertEquals(listOf("Rice, white, cooked", "Banana"), usual(log, at("2026-11-26", "18:00")))
        assertEquals(emptyList<String>(), usual(log, at("2026-11-26", "03:00")))
        assertEquals(listOf("Skyr", "Oats, rolled"), usual(log, at("2026-11-26", "05:30")))
        assertEquals(listOf("Rice, white, cooked", "Skyr"), usual(log, at("2026-11-26", "11:15")))
    }

    @Test fun `the window wraps around midnight`() {
        assertEquals(60, minutesApart(23 * 60 + 30, 30))
        assertEquals(0, minutesApart(0, 24 * 60))
        val log = listOf(eaten(skyr, "2026-11-24", "23:30"), eaten(banana, "2026-11-25", "00:45"))
        assertEquals(setOf("Skyr", "Banana"), usual(log, at("2026-11-26", "00:15")).toSet())
        assertEquals(setOf("Skyr", "Banana"), usual(log, at("2026-11-25", "22:50")).toSet())
    }

    @Test fun `the usual amount is the one logged last, and only the last four weeks count`() {
        val log = listOf(eaten(skyr, "2026-11-20", "07:30", 450.0), eaten(skyr, "2026-11-25", "18:00", 200.0), eaten(oats, "2026-10-20", "07:30"))
        val items = usualAt(log, foods::get, at("2026-11-26", "08:00"), berlin)
        assertEquals(listOf("Skyr" to 200.0), items.map { it.food.name to it.amount })
    }

    @Test fun `a food filled in for an earlier day has no time of its own, and a food the phone forgot is left out`() {
        val later = eaten(skyr, "2026-11-26", "07:30").copy(day = "2026-11-24")
        val gone = eaten(skyr, "2026-11-25", "07:30").copy(foodId = "off:gone")
        assertEquals(emptyList<String>(), usual(listOf(later, gone), at("2026-11-26", "08:00")))
    }

    @Test fun `at most USUAL_SIZE foods`() {
        val many = (1..10).map { i -> Food("x:$i", "Food $i", kcal = 100.0, source = FoodSource.OWN) }
        val log = many.mapIndexed { i, food -> entryOf(food, 100.0, "e$i", at("2026-11-25", "08:00") + i, "2026-11-25") }
        assertEquals(USUAL_SIZE, usualAt(log, { id -> many.firstOrNull { it.id == id } }, at("2026-11-26", "08:00"), berlin).size)
    }

    @Test fun `an empty history offers nothing usual`() {
        assertEquals(emptyList<String>(), usual(emptyList(), at("2026-11-26", "08:00")))
    }

    @Test fun `the add screen shows the usual foods, then the other recent ones, and a new phone the common ones`() {
        val now = at("2026-11-26", "08:00")
        val log = listOf(eaten(oats, "2026-11-25", "07:30"), eaten(rice, "2026-11-25", "13:00"))
        val state = freshState(WeightUnit.KG).copy(foods = listOf(skyr), foodLog = log)
        val model = Model(state, plainFoods = listOf(oats, rice, banana), now = now, zone = berlin)
        val suggested = addSuggestions(model)
        assertEquals(listOf("Oats, rolled"), suggested.usual.map { it.food.name })
        assertEquals(listOf("Rice, white, cooked"), suggested.recent.map { it.food.name })
        assertEquals(emptyList<Any>(), suggested.common)
        // A new user: nothing logged, so the common plain foods the phone has, at their servings.
        val fresh = addSuggestions(Model(freshState(WeightUnit.KG), plainFoods = listOf(oats, rice, banana), now = now, zone = berlin))
        assertEquals(COMMON_FOODS.filter { it in setOf("Oats, rolled", "Rice, white, cooked", "Banana") }, fresh.common.map { it.food.name })
        assertEquals(listOf(80.0, 118.0, 150.0), fresh.common.map { it.amount })
    }
}

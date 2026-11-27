package io.github.alexanderop.eisen

import io.github.alexanderop.eisen.core.WeightUnit
import io.github.alexanderop.eisen.feature.food.Estimate
import io.github.alexanderop.eisen.feature.food.EstimateNote
import io.github.alexanderop.eisen.feature.food.FoodEntry
import io.github.alexanderop.eisen.feature.food.GoalDirection
import io.github.alexanderop.eisen.feature.food.MAX_BODY_KG
import io.github.alexanderop.eisen.feature.food.MAX_EXPENDITURE
import io.github.alexanderop.eisen.feature.food.MAX_PACE_KG
import io.github.alexanderop.eisen.feature.food.MIN_BODY_KG
import io.github.alexanderop.eisen.feature.food.MIN_TARGET_KCAL
import io.github.alexanderop.eisen.feature.food.WeightEntry
import io.github.alexanderop.eisen.feature.food.WeightGoal
import io.github.alexanderop.eisen.feature.food.bodyFrom
import io.github.alexanderop.eisen.feature.food.bodyIn
import io.github.alexanderop.eisen.feature.food.bodyKg
import io.github.alexanderop.eisen.feature.food.bodyText
import io.github.alexanderop.eisen.feature.food.changeText
import io.github.alexanderop.eisen.feature.food.estimate
import io.github.alexanderop.eisen.feature.food.expenditureOf
import io.github.alexanderop.eisen.feature.food.maxPace
import io.github.alexanderop.eisen.feature.food.mondayOf
import io.github.alexanderop.eisen.feature.food.plusDays
import io.github.alexanderop.eisen.feature.food.proteinFor
import io.github.alexanderop.eisen.feature.food.round1
import io.github.alexanderop.eisen.feature.food.signedPace
import io.github.alexanderop.eisen.feature.food.targetOf
import io.github.alexanderop.eisen.feature.food.trendAt
import io.github.alexanderop.eisen.feature.food.trendChange
import io.github.alexanderop.eisen.feature.food.trendOf
import io.github.alexanderop.eisen.feature.food.weightGoalWithin
import io.github.alexanderop.eisen.feature.food.weightsWithin
import io.github.alexanderop.eisen.feature.food.withWeight
import io.github.alexanderop.eisen.app.state.Loaded
import io.github.alexanderop.eisen.app.state.encodeState
import io.github.alexanderop.eisen.app.state.freshState
import io.github.alexanderop.eisen.app.state.loadState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The weight log's arithmetic: the trend, what the lifter burns, the pace and the target, each with its numbers worked out by hand. */
class WeightTest {
    private fun w(day: String, kg: Double) = WeightEntry(day, kg, 0)

    private fun eaten(day: String, kcal: Int) = FoodEntry("$day-$kcal", name = "Food", kcal = kcal, at = 0, day = day)

    // The trend

    @Test fun `the first weight starts the trend, and each weight after moves it a tenth of the way`() {
        // 80, then 81: 80 + 0.1 × (81 − 80) = 80.1; then 81 again: 80.1 + 0.1 × 0.9 = 80.19.
        val trend = trendOf(listOf(w("2026-11-01", 80.0), w("2026-11-02", 81.0), w("2026-11-03", 81.0)), "2026-11-03")
        assertEquals(listOf(80.0, 80.1, 80.19), trend.map { round(it.trend) })
        assertEquals(listOf(80.0, 81.0, 81.0), trend.map { it.weight })
    }

    @Test fun `a day without a weight carries the trend, a gap of days too`() {
        // 80 on the 1st, nothing on the 2nd and 3rd (80 carried), 82 on the 4th: 80 + 0.1 × 2 = 80.2, carried to the 6th.
        val trend = trendOf(listOf(w("2026-11-01", 80.0), w("2026-11-04", 82.0)), "2026-11-06")
        assertEquals(listOf("2026-11-01", "2026-11-02", "2026-11-03", "2026-11-04", "2026-11-05", "2026-11-06"), trend.map { it.day })
        assertEquals(listOf(80.0, 80.0, 80.0, 80.2, 80.2, 80.2), trend.map { round(it.trend) })
        assertEquals(listOf(80.0, null, null, 82.0, null, null), trend.map { it.weight })
    }

    @Test fun `one outlier moves the trend a tenth of its jump, and the next normal weight pulls it back`() {
        // 80, 80, 80, then 83 after a salty evening: 80.3. Then 80: 80.3 − 0.03 = 80.27. The weight jumped 3 kg, the trend 0.3.
        val trend = trendOf(listOf(w("2026-11-01", 80.0), w("2026-11-02", 80.0), w("2026-11-03", 80.0), w("2026-11-04", 83.0), w("2026-11-05", 80.0)), "2026-11-05")
        assertEquals(listOf(80.0, 80.0, 80.0, 80.3, 80.27), trend.map { round(it.trend) })
    }

    @Test fun `a single weight is the trend until the day asked for, and nothing before it`() {
        val trend = trendOf(listOf(w("2026-11-10", 82.4)), "2026-11-13")
        assertEquals(4, trend.size)
        assertTrue(trend.all { it.trend == 82.4 })
        assertEquals(82.4, trendAt(trend, "2026-11-12")!!, 0.0)
        assertEquals(null, trendAt(trend, "2026-11-09"))
        assertEquals(emptyList<Any>(), trendOf(listOf(w("2026-11-10", 82.4)), "2026-11-09"))
        assertEquals(emptyList<Any>(), trendOf(emptyList(), "2026-11-09"))
        // A week of trend is needed for its change a week.
        assertEquals(null, trendChange(trend, "2026-11-13"))
        assertEquals(0.0, trendChange(trendOf(listOf(w("2026-11-10", 82.4)), "2026-11-17"), "2026-11-17")!!, 0.0)
    }

    // What the lifter burns

    @Test fun `the worked example of MacroFactor's FAQ, about 3,000 kcal eaten with a surplus of 200 a day, burns 2,800`() {
        // Calories in − change in stored energy = calories out: 3,000 − 200 = 2,800. A surplus of 200 kcal a day is
        // 200 / 7,700 kg a day of trend.
        assertEquals(2_800.0, expenditureOf(3_000.0, 200.0 / 7_700), 1e-9)
        // The same through the window: a trend that climbs exactly 200 / 7,700 kg a day (each weight 2.597... kg above
        // the trend of the day before, a tenth of which it takes), 3,000 kcal on each of the 21 days.
        val end = "2026-11-22"
        val start = plusDays(end, -25)
        val climb = 200.0 / 7_700
        var trend = 80.0
        val weights = mutableListOf(w(start, 80.0))
        for (i in 1..25) {
            weights += w(plusDays(start, i.toLong()), trend + climb / 0.1)
            trend += climb
        }
        val log = (0 until 21).map { eaten(plusDays(end, -it.toLong()), 3_000) }
        val burn = estimate(log, weights, end) as Estimate.Ready
        assertEquals(2_800, burn.kcal)
        assertEquals(3_000, burn.intake)
        assertEquals(21, burn.days)
        // The weights are kept to a gram, so the trend climbs to within a gram of it.
        assertEquals(climb * 7, burn.kgPerWeek, 1e-3)
        assertEquals(null, burn.note)
    }

    @Test fun `a steady weight burns what was eaten, and only days with food logged count`() {
        // 80 kg every day for four weeks, 2,500 kcal on 15 of the last 21 days and nothing on 6: the average is of the 15.
        val end = "2026-11-22"
        val weights = (0..27).map { w(plusDays(end, -it.toLong()), 80.0) }
        val log = (0 until 21).filter { it % 7 !in setOf(2, 5) }.map { eaten(plusDays(end, -it.toLong()), 2_500) }
        assertEquals(15, log.map { it.day }.toSet().size)
        assertEquals(Estimate.Ready(2_500, 2_500, 0.0, 15, 22, null), estimate(log, weights, end))
    }

    @Test fun `with fewer than 14 logged days it is still learning, and says how far it is`() {
        val end = "2026-11-22"
        val weights = (0..27).map { w(plusDays(end, -it.toLong()), 80.0) }
        val nine = (0 until 9).map { eaten(plusDays(end, -it.toLong()), 2_500) }
        assertEquals(Estimate.Learning(9, 14), estimate(nine, weights, end))
        // Food before the window, or before the first weight, does not count.
        val old = (21 until 40).map { eaten(plusDays(end, -it.toLong()), 2_500) }
        assertEquals(Estimate.Learning(9, 14), estimate(nine + old, weights, end))
        // Weights only on the last nine days: the trend starts on the first of them, and only the eight days after it count.
        assertEquals(Estimate.Learning(8, 14), estimate(nine + (0 until 21).map { eaten(plusDays(end, -it.toLong()), 2_500) }, weights.take(9), end))
        // No weight at all: nothing to learn from yet.
        assertEquals(Estimate.Learning(0, 14), estimate(nine, emptyList(), end))
    }

    @Test fun `a number past the sane range is shown at its edge and says so, and few weights say so too`() {
        val end = "2026-11-22"
        val steady = (0..27).map { w(plusDays(end, -it.toLong()), 80.0) }
        val feast = (0 until 21).map { eaten(plusDays(end, -it.toLong()), 9_000) }
        assertEquals(Estimate.Ready(MAX_EXPENDITURE, 9_000, 0.0, 21, 22, EstimateNote.CLAMPED), estimate(feast, steady, end))
        // Two weights 21 days apart: the trend moved 0.1 × (79 − 80) = −0.1 kg, −0.1 / 21 kg a day, 2,500 + 0.1 × 7,700 / 21 = 2,536.7.
        val two = listOf(w(plusDays(end, -21), 80.0), w(end, 79.0))
        val log = (0 until 21).map { eaten(plusDays(end, -it.toLong()), 2_500) }
        val thin = estimate(log, two, end) as Estimate.Ready
        assertEquals(2_540, thin.kcal)
        assertEquals(EstimateNote.FEW_WEIGHTS, thin.note)
        assertEquals(2, thin.weighIns)
    }

    // The pace and the target

    @Test fun `the target is what is burnt plus the pace in kcal a day, rounded to 50`() {
        // 2,750 − 0.5 × 7,700 / 7 = 2,750 − 550 = 2,200.
        assertEquals(2_200, targetOf(2_750, -0.5))
        // 2,800 + 0.25 × 1,100 = 3,075: halfway, up to 3,100.
        assertEquals(3_100, targetOf(2_800, 0.25))
        assertEquals(2_800, targetOf(2_800, 0.0))
        // Never under 1,200.
        assertEquals(MIN_TARGET_KCAL, targetOf(1_500, -1.0))
        assertEquals(-0.5, signedPace(WeightGoal(GoalDirection.LOSE, 0.5)), 0.0)
        assertEquals(0.5, signedPace(WeightGoal(GoalDirection.GAIN, 0.5)), 0.0)
        assertEquals(0.0, signedPace(WeightGoal(GoalDirection.MAINTAIN, 0.5)), 0.0)
    }

    @Test fun `a pace is at most 1 percent of the trend weight a week and never more than a kilogram`() {
        assertEquals(0.82, maxPace(82.4), 0.0)
        assertEquals(MAX_PACE_KG, maxPace(150.0), 0.0)
        assertEquals(0.5, maxPace(null), 0.0)
        assertEquals(WeightGoal(GoalDirection.LOSE, 0.82), weightGoalWithin(WeightGoal(GoalDirection.LOSE, 5.0), maxPace(82.4)))
        assertEquals(WeightGoal(GoalDirection.LOSE, 0.0), weightGoalWithin(WeightGoal(GoalDirection.LOSE, -3.0)))
        assertEquals(WeightGoal(GoalDirection.MAINTAIN, 0.0), weightGoalWithin(WeightGoal(GoalDirection.MAINTAIN, 0.5)))
        assertEquals(WeightGoal(GoalDirection.GAIN, MAX_PACE_KG), weightGoalWithin(WeightGoal(GoalDirection.GAIN, Double.MAX_VALUE), Double.MAX_VALUE))
        assertEquals(WeightGoal(GoalDirection.GAIN, 0.0), weightGoalWithin(WeightGoal(GoalDirection.GAIN, Double.NaN)))
    }

    @Test fun `protein from the weight is 2 g per kilogram`() {
        // 82.4 × 2 = 164.8: 165 g.
        assertEquals(165, proteinFor(82.4))
    }

    // The log

    @Test fun `one weight per day, a second one on the same day replaces the first, and the days stay in order`() {
        val log = withWeight(listOf(w("2026-11-20", 82.0), w("2026-11-22", 81.6)), w("2026-11-21", 81.8))
        assertEquals(listOf("2026-11-20", "2026-11-21", "2026-11-22"), log.map { it.day })
        assertEquals(listOf(82.0, 81.5, 81.6), withWeight(log, w("2026-11-21", 81.5)).map { it.kg })
        // A file written by hand: the same day twice (the last wins), a day that is not a date, a number past the limits.
        assertEquals(
            listOf(w("2026-11-20", MIN_BODY_KG), w("2026-11-21", 81.0), w("2026-11-22", MAX_BODY_KG)),
            weightsWithin(listOf(w("2026-11-21", 80.0), w("2026-11-22", 9e9), w("someday", 80.0), w("2026-11-20", -1.0), w("2026-11-21", 81.0))),
        )
    }

    @Test fun `weights are kept in kilograms and shown in the unit of the settings`() {
        // 180.0 lb is 81.647 kg, which comes back as 180.0 lb.
        val kg = bodyKg(bodyFrom(180.0, WeightUnit.LB))
        assertEquals(81.647, kg, 0.0)
        assertEquals(180.0, round1(bodyIn(kg, WeightUnit.LB)), 0.0)
        assertEquals("82.4 kg", bodyText(82.4, WeightUnit.KG))
        assertEquals("181.7 lb", bodyText(82.4, WeightUnit.LB))
        assertEquals("−0.3 kg", changeText(-0.3, WeightUnit.KG))
        assertEquals("+0.2 kg", changeText(0.24, WeightUnit.KG))
        assertEquals("0 kg", changeText(0.04, WeightUnit.KG))
        assertEquals("2026-11-23", mondayOf("2026-11-29"))
        assertEquals("2026-11-23", mondayOf("2026-11-23"))
    }

    @Test fun `a backup carries the weights and the goal pace, and a file of build 25 loads without them`() {
        val state = freshState(WeightUnit.KG).copy(
            weights = listOf(w("2026-11-25", 82.6), w("2026-11-26", 82.4)),
            weightGoal = WeightGoal(GoalDirection.LOSE, 0.5, adjust = false, checkIn = io.github.alexanderop.eisen.feature.food.CheckInAnswer("2026-11-23", true, 2_450, 2_500)),
            foodGoal = io.github.alexanderop.eisen.feature.food.FoodGoal(2_450, 160),
        )
        assertEquals(Loaded.Ok(state), loadState(encodeState(state)))
        assertEquals(Loaded.Ok(state), loadState(encodeState(state, pretty = true)))
        // A file from before the weight log: neither field, everything else as it was, and the check-in on by default.
        val old = encodeState(state).replace(Regex(""","weights":\[[^\]]*\],"weightGoal":\{[^{}]*(\{[^}]*\})?[^}]*\}"""), "")
        assertTrue(old, "weight" !in old.substringAfter("foodGoal"))
        val loaded = (loadState(old) as Loaded.Ok).state
        assertEquals(state.copy(weights = emptyList(), weightGoal = WeightGoal()), loaded)
        assertTrue(loaded.weightGoal.adjust)
    }

    private fun round(value: Double) = Math.round(value * 1000) / 1000.0
}

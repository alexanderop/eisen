package io.github.alexanderop.eisen

import io.github.alexanderop.eisen.app.elm.Env
import io.github.alexanderop.eisen.app.elm.Model
import io.github.alexanderop.eisen.app.elm.Msg
import io.github.alexanderop.eisen.app.elm.foodToday
import io.github.alexanderop.eisen.app.elm.kcalHeadline
import io.github.alexanderop.eisen.app.elm.kcalLine
import io.github.alexanderop.eisen.app.elm.update
import io.github.alexanderop.eisen.app.state.Loaded
import io.github.alexanderop.eisen.app.state.encodeState
import io.github.alexanderop.eisen.app.state.freshState
import io.github.alexanderop.eisen.app.state.loadState
import io.github.alexanderop.eisen.core.WeightUnit
import io.github.alexanderop.eisen.feature.food.FoodGoal
import io.github.alexanderop.eisen.feature.food.MAX_GOAL_CARBS
import io.github.alexanderop.eisen.feature.food.MAX_GOAL_FAT
import io.github.alexanderop.eisen.feature.food.carbsAndFatFor
import io.github.alexanderop.eisen.feature.food.goalWithin
import io.github.alexanderop.eisen.feature.food.macroText
import io.github.alexanderop.eisen.feature.food.quickEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The three macros: their goals, the fill from the calories left, and texts that never alarm. */
class MacrosTest {
    @Test fun `carbs and fat fill the calories left after protein, fat a quarter of them`() {
        // 2,400 kcal: fat 600 kcal = 67 g; carbs (2,400 - 160 × 4 - 67 × 9) / 4 = 289 g.
        assertEquals(289 to 67, carbsAndFatFor(2400, 160))
        assertEquals(374 to 56, carbsAndFatFor(2000, 0))
        // Protein that takes it all leaves no carbs, never fewer than none.
        assertEquals(0 to 28, carbsAndFatFor(1000, 400))
        assertNull(carbsAndFatFor(0, 160))
        assertEquals(MAX_GOAL_CARBS to MAX_GOAL_FAT, carbsAndFatFor(Int.MAX_VALUE / 2, 0))
    }

    @Test fun `over a goal reads how far over, in the same words as under it`() {
        assertEquals("96 g", macroText(96.4, null))
        assertEquals("96 / 160 g", macroText(96.4, 160))
        assertEquals("160 / 160 g", macroText(160.0, 160))
        assertEquals("170 / 160 g · 10 over", macroText(170.0, 160))
        assertEquals("1,160 / 2,400 kcal", kcalLine(1160, 2400))
        assertEquals("2,520 / 2,400 kcal · 120 over", kcalLine(2520, 2400))
        assertEquals("850 kcal", kcalLine(850, null))
    }

    @Test fun `the goal takes calories, protein, carbs and fat, each optional and in its limits`() {
        assertEquals(FoodGoal(2400, 160, 289, 67), goalWithin(FoodGoal(2400, 160, 289, 67)))
        assertEquals(FoodGoal(2400, 160), goalWithin(FoodGoal(2400, 160, 0, -3)))
        assertEquals(FoodGoal(null, null, MAX_GOAL_CARBS, MAX_GOAL_FAT), goalWithin(FoodGoal(0, 0, Int.MAX_VALUE, Int.MAX_VALUE)))
        val step = Step(Model(freshState(WeightUnit.KG), now = LATER))
        fun Step.food(msg: Msg) = update(msg, model, Env(LATER) { "q" }).let { (next, cmds) -> Step(next, cmds) }
        val saved = step.food(Msg.Food.Open).food(Msg.Food.OpenGoal).food(Msg.Food.SaveGoal(2400, 160, 289, 67))
        assertEquals(FoodGoal(2400, 160, 289, 67), saved.state.foodGoal)
        // The day shows what is eaten of each against its goal.
        val eaten = saved.food(Msg.Food.OpenQuick).food(Msg.Food.SaveQuick(2520, 170, ""))
        assertEquals("120 kcal over", kcalHeadline(foodToday(eaten.model)))
    }

    @Test fun `a goal saved by build 24, without carbs and fat, loads as it was`() {
        val build24 = encodeState(freshState(WeightUnit.KG).copy(foodGoal = FoodGoal(2400, 160), foodLog = listOf(quickEntry(500, 30, "", "q", 1, "2026-11-20"))))
            .replace(""","carbs":null,"fat":null}""", "}")
        assertEquals(false, build24.contains("\"carbs\":null,\"fat\":null}"))
        val loaded = (loadState(build24) as Loaded.Ok).state
        assertEquals(FoodGoal(2400, 160), loaded.foodGoal)
        assertEquals(1, loaded.foodLog.size)
    }
}

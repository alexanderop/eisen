package io.github.alexanderop.eisen

import io.github.alexanderop.eisen.app.elm.Env
import io.github.alexanderop.eisen.app.elm.Model
import io.github.alexanderop.eisen.app.elm.Msg
import io.github.alexanderop.eisen.app.elm.todayRings
import io.github.alexanderop.eisen.app.elm.update
import io.github.alexanderop.eisen.app.state.freshState
import io.github.alexanderop.eisen.core.WeightUnit
import org.junit.Assert.assertEquals
import org.junit.Test

/** The two rings at the top of Today: the week's workouts and today's calories, left, over or only eaten. */
class TodayRingsTest {
    private fun Step.food(msg: Msg) = update(msg, model, Env(LATER) { "q" }).let { (next, cmds) -> Step(next, cmds) }
    private val fresh = Step(Model(freshState(WeightUnit.KG).copy(foodGoal = io.github.alexanderop.eisen.feature.food.FoodGoal()), now = LATER))

    @Test fun `without a goal the calorie ring tells what was eaten and stays empty`() {
        val rings = todayRings(fresh.food(Msg.Food.Open).food(Msg.Food.OpenQuick).food(Msg.Food.SaveQuick(850, 0, "")).model)
        assertEquals("0 / 3", rings.week.value)
        assertEquals(0f, rings.week.fraction)
        assertEquals(listOf("850", "kcal eaten"), listOf(rings.kcal.value, rings.kcal.label))
        assertEquals(0f, rings.kcal.fraction)
    }

    @Test fun `with a goal it counts down what is left, and over it how far over`() {
        val goal = fresh.food(Msg.Food.Open).food(Msg.Food.OpenGoal).food(Msg.Food.SaveGoal(2400, 160, 289, 67))
        val under = todayRings(goal.food(Msg.Food.OpenQuick).food(Msg.Food.SaveQuick(1240, 0, "")).model).kcal
        assertEquals(listOf("1,160", "kcal left"), listOf(under.value, under.label))
        assertEquals(1240 / 2400f, under.fraction)
        assertEquals(false, under.over)
        val over = todayRings(goal.food(Msg.Food.OpenQuick).food(Msg.Food.SaveQuick(2520, 0, "")).model).kcal
        assertEquals(listOf("120", "kcal over"), listOf(over.value, over.label))
        assertEquals(true, over.over)
    }
}

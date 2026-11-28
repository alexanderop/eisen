package io.github.alexanderop.eisen.app.elm

import io.github.alexanderop.eisen.feature.food.kcalText

/** The workouts a week of 5×5 asks for: the ring of the week is full at three. */
const val WEEK_GOAL = 3

/** One ring at the top of Today: how full it is (0 to 1, more is over), the number in it and the words under it. */
data class TodayRing(val fraction: Float, val value: String, val label: String, val over: Boolean, val description: String)

/** The two rings of Today: the workouts of the last 7 days (an interval timer counts too) and today's calories. */
data class TodayRings(val week: TodayRing, val kcal: TodayRing)

fun todayRings(model: Model): TodayRings {
    val done = trainingStats(model.state, model.now)?.lastWeek ?: 0
    val week = TodayRing(
        fraction = done / WEEK_GOAL.toFloat(),
        value = "$done / $WEEK_GOAL",
        label = "workouts, 7 days",
        over = false,
        description = "$done of $WEEK_GOAL workouts in the last 7 days. Opens Progress.",
    )
    val day = foodToday(model)
    val eaten = day.totals.kcal
    val goal = day.goal.kcal?.takeIf { it > 0 }
    val kcal = if (goal == null) {
        TodayRing(0f, kcalText(eaten), "kcal eaten", false, "${kcalText(eaten)} kcal eaten today. Opens the Food diary.")
    } else {
        val over = eaten > goal
        TodayRing(
            fraction = eaten / goal.toFloat(),
            value = kcalText(kotlin.math.abs(goal - eaten)),
            label = if (over) "kcal over" else "kcal left",
            over = over,
            description = "${kcalText(eaten)} of ${kcalText(goal)} kcal today. Opens the Food diary.",
        )
    }
    return TodayRings(week, kcal)
}

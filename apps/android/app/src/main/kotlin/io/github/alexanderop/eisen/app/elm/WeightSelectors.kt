package io.github.alexanderop.eisen.app.elm

import io.github.alexanderop.eisen.core.WeightUnit
import io.github.alexanderop.eisen.feature.food.Estimate
import io.github.alexanderop.eisen.feature.food.EstimateNote
import io.github.alexanderop.eisen.feature.food.MAX_BODY_KG
import io.github.alexanderop.eisen.feature.food.MIN_BODY_KG
import io.github.alexanderop.eisen.feature.food.TrendDay
import io.github.alexanderop.eisen.feature.food.bodyIn
import io.github.alexanderop.eisen.feature.food.bodyText
import io.github.alexanderop.eisen.feature.food.changeText
import io.github.alexanderop.eisen.feature.food.dayBefore
import io.github.alexanderop.eisen.feature.food.dayTitle
import io.github.alexanderop.eisen.feature.food.estimate
import io.github.alexanderop.eisen.feature.food.kcalText
import io.github.alexanderop.eisen.feature.food.maxPace
import io.github.alexanderop.eisen.feature.food.plusDays
import io.github.alexanderop.eisen.feature.food.proteinFor
import io.github.alexanderop.eisen.feature.food.round1
import io.github.alexanderop.eisen.feature.food.signedPace
import io.github.alexanderop.eisen.feature.food.targetOf
import io.github.alexanderop.eisen.feature.food.trendAt
import io.github.alexanderop.eisen.feature.food.trendChange
import io.github.alexanderop.eisen.feature.food.trendOf
import io.github.alexanderop.eisen.feature.food.weightOf
import io.github.alexanderop.eisen.feature.food.WeightGoal
import kotlin.math.abs

/*
 * What the weight card, the weight sheet, the goal sheet and History › Progress read from the model besides its fields.
 * Pure, so a screen and a test see the same. The numbers come as texts in the unit of the settings.
 */

/** The trend from the first weight to today. */
fun trendToday(model: Model): List<TrendDay> = trendOf(model.state.weights, today(model))

/** The trend weight of today in kilograms, null before the first weight. */
fun trendNow(model: Model): Double? = trendToday(model).lastOrNull()?.trend

/** What the lifter burns, from the food and the weights up to yesterday: today is still being eaten. */
fun burnNow(model: Model): Estimate = estimate(model.state.foodLog, model.state.weights, dayBefore(today(model)))

/** Which way the trend went over the last week. */
enum class Direction { DOWN, STEADY, UP }

/** A change of the trend under this, in kilograms a week, reads as steady. */
const val STEADY_KG = 0.05

/**
 * The weight card of the Food tab: [trend] as "82.4 kg", how much it moved over the last seven days ([change] "0.3 kg", its
 * way in [direction],
 * null with less than a week of trend), and whether today has a weight. Null before the first weight: the card asks
 * for one.
 */
data class WeightCard(val trend: String, val change: String?, val direction: Direction?, val loggedToday: Boolean, val label: String)

fun weightCard(model: Model): WeightCard? {
    val trend = trendToday(model)
    val now = trend.lastOrNull()?.trend ?: return null
    val unit = model.state.settings.unit
    val change = trendChange(trend, today(model))
    val direction = change?.let { directionOf(it) }
    val said = listOfNotNull("Weight trend ${bodyText(now, unit)}", change?.let { changeWords(it, unit) }).joinToString(", ")
    // The arrow says which way: the change is without its sign ("↓ 0.3 kg / week").
    return WeightCard(bodyText(now, unit), change?.let { bodyText(abs(it), unit) }, direction, weightOf(model.state.weights, today(model)) != null, said)
}

private fun directionOf(kgPerWeek: Double): Direction = when {
    kgPerWeek <= -STEADY_KG -> Direction.DOWN
    kgPerWeek >= STEADY_KG -> Direction.UP
    else -> Direction.STEADY
}

/** "down 0.3 kg a week", as TalkBack says the arrow. */
private fun changeWords(kgPerWeek: Double, unit: WeightUnit): String = when (directionOf(kgPerWeek)) {
    Direction.DOWN -> "down ${bodyText(abs(kgPerWeek), unit)} a week"
    Direction.UP -> "up ${bodyText(kgPerWeek, unit)} a week"
    Direction.STEADY -> "steady"
}

/** The weight sheet as it shows: its day's title, the limits of the number in the unit, and whether the day has a weight to delete. */
data class WeightSheetView(val title: String, val min: Double, val max: Double, val unit: WeightUnit, val logged: String?, val canGoForward: Boolean)

fun weightSheetView(model: Model): WeightSheetView? {
    val sheet = model.weight.sheet ?: return null
    val unit = model.state.settings.unit
    val now = today(model)
    return WeightSheetView(
        title = dayTitle(sheet.day, now),
        min = round1(bodyIn(MIN_BODY_KG, unit)),
        max = round1(bodyIn(MAX_BODY_KG, unit)),
        unit = unit,
        logged = weightOf(model.state.weights, sheet.day)?.let { bodyText(it.kg, unit) },
        canGoForward = sheet.day < now,
    )
}

/** "About 2,750 kcal", or "Learning: 9 of 14 days". */
fun burnText(estimate: Estimate): String = when (estimate) {
    is Estimate.Learning -> "Learning: ${estimate.days} of ${io.github.alexanderop.eisen.core.counted(estimate.needed, "day")}"
    is Estimate.Ready -> "About ${kcalText(estimate.kcal)} kcal"
}

/** What a note on an estimate says, calmly. */
fun noteText(note: EstimateNote?): String? = when (note) {
    null -> null
    EstimateNote.CLAMPED -> "This looks off. Check for days logged only in part."
    EstimateNote.FEW_WEIGHTS -> "Based on few weights. Log a few more mornings."
}

/** The one line that says what a logged day means, wherever what the lifter burns shows. */
const val LOGGED_DAY_NOTE = "Counts a day with food logged as fully logged."

/**
 * The goal sheet's part about the weight: the trend, the fastest pace it allows (in the unit), what the lifter burns,
 * and the protein the trend suggests. [target] works out the calories for a pace in kilograms a week.
 */
data class GoalWeightView(val trendKg: Double?, val maxPace: Double, val unit: WeightUnit, val burn: Estimate, val protein: Int?) {
    fun target(goal: WeightGoal): Int? = (burn as? Estimate.Ready)?.let { targetOf(it.kcal, signedPace(goal)) }
}

fun goalWeightView(model: Model): GoalWeightView {
    val trend = trendNow(model)
    val unit = model.state.settings.unit
    // Down to a tenth, so the stepper's largest number is never past the limit.
    val most = kotlin.math.floor(bodyIn(maxPace(trend), unit) * 10 + 1e-9) / 10
    return GoalWeightView(trend, most, unit, burnNow(model), trend?.let(::proteinFor))
}

/** History › Progress › Weight over a range: the weights as dots, the trend as a line, and its three numbers. */
data class WeightChart(
    /** Each logged weight: local noon of its day, in the unit. */
    val weights: List<Pair<Long, Double>>,
    /** The trend, a point a day. */
    val trend: List<Pair<Long, Double>>,
    val trendText: String?,
    val changeText: String?,
    val burn: Estimate,
    /** What the lifter burns as Progress shows it: "2,750" and "kcal a day", or "Learning" and "9 of 14 days", and a note to read it with. */
    val burnValue: String,
    val burnUnit: String,
    val burnNote: String?,
    /** What TalkBack says for the chart. */
    val summary: String,
)

/** The weight section over the last [days] days (all of them when null); null before the first weight: the empty state. */
fun weightChart(model: Model, days: Long?): WeightChart? {
    val trend = trendToday(model)
    if (trend.isEmpty()) return null
    val unit = model.state.settings.unit
    val now = today(model)
    val from = days?.let { plusDays(now, -it) } ?: trend.first().day
    fun noon(day: String): Long = runCatching { java.time.LocalDate.parse(day).atTime(12, 0).atZone(model.zone).toInstant().toEpochMilli() }.getOrDefault(0L)
    val shown = trend.filter { it.day >= from }
    val weights = shown.mapNotNull { day -> day.weight?.let { noon(day.day) to bodyIn(it, unit) } }
    val line = shown.map { noon(it.day) to bodyIn(it.trend, unit) }
    val trendNow = trendAt(trend, now)
    val change = trendChange(trend, now)
    val burn = burnNow(model)
    val summary = listOfNotNull(
        "Weight over time, ${io.github.alexanderop.eisen.core.counted(weights.size, "weight")}",
        trendNow?.let { "trend ${bodyText(it, unit)}" },
        change?.let { changeWords(it, unit) },
        shown.firstOrNull()?.let { first ->
            val title = dayTitle(first.day, now)
            "from ${bodyText(first.trend, unit)} ${if (title == "Today" || title == "Yesterday") title.lowercase() else "on $title"}"
        },
    ).joinToString(", ")
    val (value, per) = when (burn) {
        is Estimate.Ready -> kcalText(burn.kcal) to "kcal a day"
        is Estimate.Learning -> "Learning" to "${burn.days} of ${io.github.alexanderop.eisen.core.counted(burn.needed, "day")}"
    }
    return WeightChart(weights, line, trendNow?.let { bodyText(it, unit) }, change?.let { changeText(it, unit) }, burn, value, per, noteText((burn as? Estimate.Ready)?.note), summary)
}

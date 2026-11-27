package io.github.alexanderop.eisen.feature.food

import io.github.alexanderop.eisen.core.WeightUnit
import io.github.alexanderop.eisen.core.convert
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/*
 * Body weight: one weight per day, the smoothed trend of it, what the lifter burns (calories eaten minus the change in
 * stored energy) and the calorie target that follows from a goal pace. Pure: the day and the time come in as values.
 * Weights are kept in kilograms; the screens show them in the unit of the settings.
 */

/** The weight of [day] (`2026-11-26`, the local calendar day), in kilograms. [at] is when it was logged. */
@Serializable
data class WeightEntry(val day: String, val kg: Double, val at: Long)

/** Lose, keep or gain: the direction of the goal pace. */
@Serializable
enum class GoalDirection(val label: String) {
    @SerialName("lose") LOSE("Lose"),
    @SerialName("maintain") MAINTAIN("Maintain"),
    @SerialName("gain") GAIN("Gain"),
}

/**
 * The weekly check-in that was answered: for the week starting on Monday [week], [took] the new [target] or kept [was]
 * (null: there was no calorie goal). Kept so the card does not come back that week, also after a restart.
 */
@Serializable
data class CheckInAnswer(val week: String, val took: Boolean, val target: Int, val was: Int? = null)

/**
 * Where the weight should go: [direction] at [pace] kilograms a week (0 for maintain). [adjust]: the weekly check-in offers
 * a new calorie target; off, the goal stays as the lifter set it. [checkIn]: the check-in answered last.
 * Files from before build 26 have none of it and load with these defaults.
 */
@Serializable
data class WeightGoal(
    val direction: GoalDirection = GoalDirection.MAINTAIN,
    val pace: Double = 0.0,
    val adjust: Boolean = true,
    val checkIn: CheckInAnswer? = null,
)

// Limits: every number of the weight log has its maximum here.

/** The lightest and heaviest weight that can be logged. Health Connect takes 0 to 1,000 kg. */
const val MIN_BODY_KG = 20.0
const val MAX_BODY_KG = 400.0

/** What the weight sheet starts at when nothing was ever logged. */
const val START_BODY_KG = 80.0

/** The fastest pace a goal can have, whatever the weight: a kilogram a week. */
const val MAX_PACE_KG = 1.0

/** And never more than this share of the body weight a week (about 1 %, the usual healthy limit). */
const val MAX_PACE_SHARE = 0.01

/** The pace without a weight to take 1 % of. */
const val PACE_WITHOUT_WEIGHT_KG = 0.5

/** Energy in a kilogram of body weight change, fat and lean together: the rule of thumb MacroFactor-style trackers use. */
const val KCAL_PER_KG = 7_700.0

/** Grams of protein a day per kilogram of body weight that the goal sheet offers (2 g/kg, for a lifter). */
const val PROTEIN_PER_KG = 2.0

/** What a day burns is never shown outside this range: a number past it says more about the log than about the body. */
const val MIN_EXPENDITURE = 1_000
const val MAX_EXPENDITURE = 6_000

/** The lowest calorie target the check-in or the goal sheet offers from a pace. */
const val MIN_TARGET_KCAL = 1_200

private fun finite(value: Double): Double = if (value.isFinite()) value else 0.0

private fun round(value: Double, scale: Int): Double = BigDecimal.valueOf(finite(value)).setScale(scale, RoundingMode.HALF_UP).toDouble()

private fun date(day: String): LocalDate? = runCatching { LocalDate.parse(day) }.getOrNull()

/** [kg] within [MIN_BODY_KG]..[MAX_BODY_KG], to a gram: enough that 180.0 lb comes back as 180.0 lb. */
fun bodyKg(kg: Double): Double = round(finite(kg).coerceIn(MIN_BODY_KG, MAX_BODY_KG), 3)

/** [entry] in its limits, or null when its day is not a date (a hand-edited file). */
fun weightWithin(entry: WeightEntry): WeightEntry? {
    val day = date(entry.day)?.toString() ?: return null
    return entry.copy(day = day, kg = bodyKg(entry.kg))
}

/** A weight log in its limits: one weight per day (the one listed last wins, as a second entry replaces the first), in the order of the days. */
fun weightsWithin(weights: List<WeightEntry>): List<WeightEntry> =
    weights.mapNotNull(::weightWithin).associateBy { it.day }.values.sortedBy { it.day }

/** [weights] with [entry] as the weight of its day: a second weight on the same day replaces the first. */
fun withWeight(weights: List<WeightEntry>, entry: WeightEntry): List<WeightEntry> = weightsWithin(weights.filter { it.day != entry.day } + entry)

/** The weight of [day], if one was logged. */
fun weightOf(weights: List<WeightEntry>, day: String): WeightEntry? = weights.firstOrNull { it.day == day }

/** The last weight logged on [day] or before it. */
fun weightUpTo(weights: List<WeightEntry>, day: String): WeightEntry? = weights.filter { it.day <= day }.maxByOrNull { it.day }

/** [kg] in [unit]. */
fun bodyIn(kg: Double, unit: WeightUnit): Double = convert(kg, WeightUnit.KG, unit)

/** A weight typed in [unit], in kilograms. */
fun bodyFrom(value: Double, unit: WeightUnit): Double = convert(finite(value), unit, WeightUnit.KG)

/** "82.4 kg" or "181.7 lb": one decimal. */
fun bodyText(kg: Double, unit: WeightUnit): String = "${numberText(bodyIn(kg, unit))} ${unit.label}"

/** A change of weight with its sign: "−0.3 kg", "+0.2 lb", "0 kg". */
fun changeText(kg: Double, unit: WeightUnit): String {
    val shown = round(bodyIn(kg, unit), 1)
    return when {
        shown > 0 -> "+${numberText(shown)} ${unit.label}"
        shown < 0 -> "−${numberText(-shown)} ${unit.label}"
        else -> "0 ${unit.label}"
    }
}

// The trend

/**
 * How far the trend moves towards each new weight: 10 % a day. It is the exponentially smoothed average of The Hacker's
 * Diet (John Walker), which trend-weight apps follow; a smoothing of 0.1 weighs the days like a moving average of about
 * 19 days (2 / (n + 1)), the "20-day" trend MacroFactor users describe. A day without a weight carries the trend.
 */
const val TREND_SMOOTHING = 0.1

/** The trend on [day], and the weight logged that day if there is one. */
data class TrendDay(val day: String, val trend: Double, val weight: Double?)

/**
 * The trend of [weights], a day at a time from the first weight to [through]: the first weight starts it, each weight
 * after moves it [TREND_SMOOTHING] of the way to itself, a day without one keeps it. Empty before the first weight.
 */
fun trendOf(weights: List<WeightEntry>, through: String): List<TrendDay> {
    val sorted = weightsWithin(weights)
    val first = sorted.firstOrNull()?.let { date(it.day) } ?: return emptyList()
    val last = date(through) ?: return emptyList()
    if (last.isBefore(first)) return emptyList()
    val byDay = sorted.associate { it.day to it.kg }
    val days = mutableListOf<TrendDay>()
    var trend = sorted.first().kg
    var at = first
    while (!at.isAfter(last)) {
        val day = at.toString()
        val weight = byDay[day]
        if (weight != null && days.isNotEmpty()) trend += TREND_SMOOTHING * (weight - trend)
        days += TrendDay(day, trend, weight)
        at = at.plusDays(1)
    }
    return days
}

/** The trend on [day]: carried from the last weight before it; null before the first weight. */
fun trendAt(trend: List<TrendDay>, day: String): Double? = trend.lastOrNull { it.day <= day }?.takeIf { day >= trend.first().day }?.trend

/** How many days [from] is before [to]; 0 for a day that is not a date. */
fun daysBetween(from: String, to: String): Long {
    val a = date(from) ?: return 0
    val b = date(to) ?: return 0
    return ChronoUnit.DAYS.between(a, b)
}

/** How much the trend moved over the [days] days up to [day], in kilograms; null without a trend that long ago. */
fun trendChange(trend: List<TrendDay>, day: String, days: Long = 7): Double? {
    val now = trendAt(trend, day) ?: return null
    val before = trendAt(trend, plusDays(day, -days)) ?: return null
    return now - before
}

// What the lifter burns

/** The days what the lifter burns is worked out over, the last of them included. */
const val EXPENDITURE_DAYS = 21

/** The fewest days with food logged (and a trend) it needs. */
const val MIN_EXPENDITURE_DAYS = 14

/** Fewer weights than this over the window: the estimate rests on little and says so. */
const val FEW_WEIGH_INS = 6

/** Why an estimate is to be read with care. */
enum class EstimateNote {
    /** It came out past [MIN_EXPENDITURE]..[MAX_EXPENDITURE] and is shown at the edge. */
    CLAMPED,

    /** Fewer than [FEW_WEIGH_INS] weights over the window. */
    FEW_WEIGHTS,
}

/** What the lifter burns, or how far the app is from knowing it. */
sealed interface Estimate {
    /** [days] of the [needed] days with food logged and a weight trend. */
    data class Learning(val days: Int, val needed: Int = MIN_EXPENDITURE_DAYS) : Estimate

    /**
     * About [kcal] a day (to ten), from [intake] eaten on average over [days] logged days while the trend moved
     * [kgPerWeek], with [weighIns] weights over the window.
     */
    data class Ready(val kcal: Int, val intake: Int, val kgPerWeek: Double, val days: Int, val weighIns: Int, val note: EstimateNote? = null) : Estimate
}

/** Calories out = calories in − change in stored energy: [intake] a day minus [kgPerDay] of trend change at [KCAL_PER_KG]. */
fun expenditureOf(intake: Double, kgPerDay: Double): Double = intake - kgPerDay * KCAL_PER_KG

/**
 * What the lifter burns over the [EXPENDITURE_DAYS] days up to [end]: the average eaten on the days with food logged,
 * minus the trend's change per day at [KCAL_PER_KG]. A day counts as logged when it has entries (all or nothing, the
 * way the owner logs). Only days after the trend started count; the change is the trend's from the day before the
 * window (or its first day) to [end]. Fewer than [MIN_EXPENDITURE_DAYS] such days: [Estimate.Learning].
 */
fun estimate(log: List<FoodEntry>, weights: List<WeightEntry>, end: String): Estimate {
    val trend = trendOf(weights, end)
    val first = trend.firstOrNull()?.day ?: return Estimate.Learning(0)
    val before = plusDays(end, -EXPENDITURE_DAYS.toLong())
    val base = if (first > before) first else before
    val eaten = log.filter { it.day > base && it.day <= end }.groupBy { it.day }.mapValues { (_, entries) -> entries.sumOf { it.kcal } }
    val days = eaten.size
    val span = daysBetween(base, end)
    if (days < MIN_EXPENDITURE_DAYS || span <= 0) return Estimate.Learning(days)
    val change = (trendAt(trend, end) ?: return Estimate.Learning(days)) - (trendAt(trend, base) ?: return Estimate.Learning(days))
    val perDay = change / span
    val intake = eaten.values.sum().toDouble() / days
    val raw = expenditureOf(intake, perDay)
    val kcal = roundToStep(raw, 10).coerceIn(MIN_EXPENDITURE, MAX_EXPENDITURE)
    val weighIns = weightsWithin(weights).count { it.day >= base && it.day <= end }
    val note = when {
        raw < MIN_EXPENDITURE || raw > MAX_EXPENDITURE -> EstimateNote.CLAMPED
        weighIns < FEW_WEIGH_INS -> EstimateNote.FEW_WEIGHTS
        else -> null
    }
    return Estimate.Ready(kcal, roundKcal(intake), perDay * 7, days, weighIns, note)
}

/** [value] to the nearest multiple of [step], halves up. */
fun roundToStep(value: Double, step: Int): Int = roundKcal(finite(value) / step) * step

// The goal pace and the target

/** The fastest pace [trendKg] allows: [MAX_PACE_SHARE] of it a week, never past [MAX_PACE_KG], to 10 g. */
fun maxPace(trendKg: Double?): Double {
    val share = trendKg?.let { it * MAX_PACE_SHARE } ?: PACE_WITHOUT_WEIGHT_KG
    return BigDecimal.valueOf(finite(share).coerceIn(0.0, MAX_PACE_KG)).setScale(2, RoundingMode.DOWN).toDouble()
}

/** [goal] in its limits: a pace from 0 to [most] kilograms a week (the sheet passes [maxPace] of the trend), 0 to maintain. */
fun weightGoalWithin(goal: WeightGoal, most: Double = MAX_PACE_KG): WeightGoal {
    val pace = if (goal.direction == GoalDirection.MAINTAIN) 0.0 else round(finite(goal.pace).coerceIn(0.0, most.coerceIn(0.0, MAX_PACE_KG)), 2)
    val answer = goal.checkIn?.let { it.copy(target = it.target.coerceIn(0, MAX_GOAL_KCAL), was = it.was?.coerceIn(0, MAX_GOAL_KCAL)) }
    return goal.copy(pace = pace, checkIn = answer?.takeIf { date(it.week) != null })
}

/** The pace with its sign, in kilograms a week: negative to lose. */
fun signedPace(goal: WeightGoal): Double = when (goal.direction) {
    GoalDirection.LOSE -> -goal.pace
    GoalDirection.MAINTAIN -> 0.0
    GoalDirection.GAIN -> goal.pace
}

/** The calories a day that reach [kgPerWeek] from [expenditure]: rounded to 50, from [MIN_TARGET_KCAL] to [MAX_GOAL_KCAL]. */
fun targetOf(expenditure: Int, kgPerWeek: Double): Int =
    roundToStep(expenditure + finite(kgPerWeek) * KCAL_PER_KG / 7, 50).coerceIn(MIN_TARGET_KCAL, MAX_GOAL_KCAL)

/** Grams of protein a day for [kg] of body weight at [PROTEIN_PER_KG]. */
fun proteinFor(kg: Double): Int = roundKcal(finite(kg) * PROTEIN_PER_KG).coerceIn(1, MAX_GOAL_PROTEIN)

/** The Monday of the week [day] is in, as `2026-11-23`. */
fun mondayOf(day: String): String = weekOf(day).firstOrNull() ?: day

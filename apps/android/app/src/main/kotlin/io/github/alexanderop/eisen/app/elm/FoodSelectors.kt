package io.github.alexanderop.eisen.app.elm

import io.github.alexanderop.eisen.feature.food.AgainItem
import io.github.alexanderop.eisen.feature.food.Food
import io.github.alexanderop.eisen.feature.food.FoodEntry
import io.github.alexanderop.eisen.feature.food.FoodGoal
import io.github.alexanderop.eisen.feature.food.SearchHit
import io.github.alexanderop.eisen.feature.food.Totals
import io.github.alexanderop.eisen.feature.food.again
import io.github.alexanderop.eisen.feature.food.dayAfter
import io.github.alexanderop.eisen.feature.food.dayTitle
import io.github.alexanderop.eisen.feature.food.entriesOf
import io.github.alexanderop.eisen.feature.food.isPastOrToday
import io.github.alexanderop.eisen.feature.food.kcalText
import io.github.alexanderop.eisen.feature.food.localDay
import io.github.alexanderop.eisen.feature.food.MAX_MEALS
import io.github.alexanderop.eisen.feature.food.dayBefore
import io.github.alexanderop.eisen.feature.food.mealName
import io.github.alexanderop.eisen.feature.food.roundKcal
import io.github.alexanderop.eisen.feature.food.searchFoods
import io.github.alexanderop.eisen.feature.food.totalsOf

/*
 * What the Food screen and the Home card read from the model besides its fields. Pure, so a screen and a test see the same.
 */

/** Today in the phone's time zone, as of [Model.now]. */
fun today(model: Model): String = localDay(model.now, model.zone)

/**
 * A day of food: its [entries] in the order they were eaten, what they add up to, the goal, and whether the arrow
 * to the next day goes anywhere (never past today).
 */
data class FoodDay(
    val day: String,
    val title: String,
    val entries: List<FoodEntry>,
    val totals: Totals,
    val goal: FoodGoal,
    val canGoForward: Boolean,
)

fun foodDay(model: Model, day: String): FoodDay {
    val now = today(model)
    val entries = entriesOf(model.state.foodLog, day)
    return FoodDay(day, dayTitle(day, now), entries, totalsOf(entries), model.state.foodGoal, isPastOrToday(dayAfter(day), now))
}

/** The day the Food screen shows, while it is open. */
fun openFoodDay(model: Model): FoodDay? = model.food.day?.let { foodDay(model, it) }

/** Today's food, for the Home card. */
fun foodToday(model: Model): FoodDay = foodDay(model, today(model))

/** Every food the phone knows: its own and scanned ones, then the plain ones. */
fun knownFoods(model: Model): List<Food> = model.state.foods + model.plainFoods

/** "Again": the foods logged most often lately, with their last amount. */
fun againItems(model: Model): List<AgainItem> = again(model.state.foodLog, { knownFood(model, it) }, model.now)

/** What the search finds on the phone for what is typed, best first. */
fun foodResults(model: Model): List<Food> {
    val search = model.food.sheet as? FoodSheet.Search ?: return emptyList()
    return searchFoods(knownFoods(model), search.query, model.state.foodLog)
}

/** What Open Food Facts found that the phone does not already list. */
fun onlineResults(model: Model): List<SearchHit> {
    val results = ((model.food.sheet as? FoodSheet.Search)?.online as? Online.Results)?.hits ?: return emptyList()
    val local = foodResults(model).map { it.id }.toSet()
    return results.filter { it.id !in local }
}

/** The headline of a day: what is left of the goal, how far over it, or what was eaten without one. */
fun kcalHeadline(day: FoodDay): String {
    val goal = day.goal.kcal ?: return "${kcalText(day.totals.kcal)} kcal eaten"
    val left = goal - day.totals.kcal
    return if (left >= 0) "${kcalText(left)} kcal left" else "${kcalText(-left)} kcal over"
}

/** "Copy yesterday" on an empty day: what the day before holds. [label] is the button's text. */
data class CopyOffer(val label: String, val foods: Int, val kcal: Int)

/** What the empty day shown can copy from the day before it; null when the day has entries or the day before has none. */
fun copyOffer(model: Model): CopyOffer? {
    val day = openFoodDay(model) ?: return null
    if (day.entries.isNotEmpty()) return null
    val before = foodDay(model, dayBefore(day.day))
    if (before.entries.isEmpty()) return null
    val label = if (before.title == "Yesterday") "Copy yesterday" else "Copy ${before.title}"
    return CopyOffer(label, before.entries.size, before.totals.kcal)
}

/** Whether the entries of the day shown can become a meal: there are some, and room for one more meal. */
fun canSaveMeal(model: Model): Boolean = openFoodDay(model)?.entries?.isNotEmpty() == true && model.state.meals.size < MAX_MEALS

/** The name a new meal starts with, by the hour it is made at, see [mealName]. */
fun suggestedMealName(model: Model): String =
    mealName(java.time.Instant.ofEpochMilli(model.now).atZone(model.zone).hour, model.state.meals.map { it.name })

/** How many days the week of food in Progress shows, today the last. */
const val FOOD_WEEK_DAYS = 7

/** A day of the week of food: its calories and protein, whether anything was logged, and its short name ("Thu"). */
data class WeekDay(val day: String, val name: String, val kcal: Int, val protein: Double, val logged: Boolean)

/**
 * The last [FOOD_WEEK_DAYS] days of food for Progress, oldest first. The averages count only the days with something
 * logged (a day not logged is not a day of nothing eaten); null when none was. [proteinDays] is how many of them reached
 * the protein goal, null without one.
 */
data class FoodWeek(
    val days: List<WeekDay>,
    val goalKcal: Int?,
    val averageKcal: Int?,
    val averageProtein: Int?,
    val proteinDays: Int?,
    val loggedDays: Int,
)

/** The week of food as of [Model.now], or null when nothing was ever logged: then Progress shows no Food section. */
fun foodWeek(model: Model): FoodWeek? {
    val log = model.state.foodLog
    if (log.isEmpty()) return null
    val today = today(model)
    val dates = generateSequence(today, ::dayBefore).take(FOOD_WEEK_DAYS).toList().reversed()
    val days = dates.map { date ->
        val entries = entriesOf(log, date)
        val totals = totalsOf(entries)
        WeekDay(date, weekdayName(date), totals.kcal, totals.protein, entries.isNotEmpty())
    }
    val logged = days.filter { it.logged }
    val goal = model.state.foodGoal
    return FoodWeek(
        days = days,
        goalKcal = goal.kcal,
        averageKcal = logged.takeIf { it.isNotEmpty() }?.let { roundKcal(it.sumOf { day -> day.kcal }.toDouble() / it.size) },
        averageProtein = logged.takeIf { it.isNotEmpty() }?.let { roundKcal(it.sumOf { day -> day.protein } / it.size) },
        proteinDays = goal.protein?.let { target -> logged.count { it.protein >= target } },
        loggedDays = logged.size,
    )
}

/** "Thu" for `2026-11-26`. */
private fun weekdayName(day: String): String =
    runCatching { java.time.LocalDate.parse(day).dayOfWeek.name.take(3).lowercase().replaceFirstChar(Char::uppercase) }.getOrDefault(day)

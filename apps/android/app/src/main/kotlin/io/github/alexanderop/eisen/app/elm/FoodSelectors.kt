package io.github.alexanderop.eisen.app.elm

import io.github.alexanderop.eisen.feature.food.AgainItem
import io.github.alexanderop.eisen.feature.food.Food
import io.github.alexanderop.eisen.feature.food.FoodEntry
import io.github.alexanderop.eisen.feature.food.FoodGoal
import io.github.alexanderop.eisen.feature.food.Totals
import io.github.alexanderop.eisen.feature.food.again
import io.github.alexanderop.eisen.feature.food.dayAfter
import io.github.alexanderop.eisen.feature.food.dayTitle
import io.github.alexanderop.eisen.feature.food.entriesOf
import io.github.alexanderop.eisen.feature.food.isPastOrToday
import io.github.alexanderop.eisen.feature.food.kcalText
import io.github.alexanderop.eisen.feature.food.localDay
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
fun onlineResults(model: Model): List<Food> {
    val results = ((model.food.sheet as? FoodSheet.Search)?.online as? Online.Results)?.foods ?: return emptyList()
    val local = foodResults(model).map { it.id }.toSet()
    return results.filter { it.id !in local }
}

/** The headline of a day: what is left of the goal, how far over it, or what was eaten without one. */
fun kcalHeadline(day: FoodDay): String {
    val goal = day.goal.kcal ?: return "${kcalText(day.totals.kcal)} kcal eaten"
    val left = goal - day.totals.kcal
    return if (left >= 0) "${kcalText(left)} kcal left" else "${kcalText(-left)} kcal over"
}

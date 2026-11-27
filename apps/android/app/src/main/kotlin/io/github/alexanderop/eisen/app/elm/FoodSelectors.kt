package io.github.alexanderop.eisen.app.elm

import io.github.alexanderop.eisen.feature.food.AgainItem
import io.github.alexanderop.eisen.feature.food.Food
import io.github.alexanderop.eisen.feature.food.FoodIcon
import io.github.alexanderop.eisen.feature.food.iconOfEntry
import io.github.alexanderop.eisen.feature.food.FoodEntry
import io.github.alexanderop.eisen.feature.food.FoodGoal
import io.github.alexanderop.eisen.feature.food.SearchHit
import io.github.alexanderop.eisen.feature.food.PlateItem
import io.github.alexanderop.eisen.feature.food.TimeGroup
import io.github.alexanderop.eisen.feature.food.timeline
import io.github.alexanderop.eisen.feature.food.weekOf
import io.github.alexanderop.eisen.feature.food.dayOfMonth
import io.github.alexanderop.eisen.feature.food.commonFoods
import io.github.alexanderop.eisen.feature.food.usualAt
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
    /** The entries grouped by when they were eaten, see [timeline]. */
    val groups: List<TimeGroup> = emptyList(),
)

fun foodDay(model: Model, day: String): FoodDay {
    val now = today(model)
    val entries = entriesOf(model.state.foodLog, day)
    return FoodDay(day, dayTitle(day, now), entries, totalsOf(entries), model.state.foodGoal, isPastOrToday(dayAfter(day), now), timeline(entries, model.zone))
}

/** The day the Food screen shows, while it is open. */
fun openFoodDay(model: Model): FoodDay? = model.food.day?.let { foodDay(model, it) }

/** Today's food, for the Home card. */
fun foodToday(model: Model): FoodDay = foodDay(model, today(model))

/** Every food the phone knows: its own and scanned ones, then the plain ones. */
fun knownFoods(model: Model): List<Food> = model.state.foods + plainFoodsOf(model)

/** "Again": the foods logged most often lately, with their last amount. */
fun againItems(model: Model): List<AgainItem> = again(model.state.foodLog, { knownFood(model, it) }, model.now)

/**
 * What the add screen offers while nothing is typed: the foods usually logged around this time of day, the other ones
 * logged lately, and for a phone that has logged nothing yet a few common plain foods. Each with its usual amount.
 */
data class AddSuggestions(val usual: List<AgainItem>, val recent: List<AgainItem>, val common: List<AgainItem>)

fun addSuggestions(model: Model): AddSuggestions {
    val usual = usualAt(model.state.foodLog, { knownFood(model, it) }, model.now, model.zone)
    val ids = usual.map { it.food.id }.toSet()
    val recent = againItems(model).filter { it.food.id !in ids }
    val common = if (usual.isEmpty() && recent.isEmpty()) commonFoods(plainFoodsOf(model), model.state.foodLog) else emptyList()
    return AddSuggestions(usual, recent, common)
}

/** What the search of the add screen finds on the phone for what is typed, best first. */
fun foodResults(model: Model): List<Food> {
    val add = model.food.add ?: return emptyList()
    return searchFoods(knownFoods(model), add.query, model.state.foodLog)
}

/** What Open Food Facts found that the phone does not already list. */
fun onlineResults(model: Model): List<SearchHit> {
    val results = (model.food.add?.online as? Online.Results)?.hits ?: return emptyList()
    val local = foodResults(model).map { it.id }.toSet()
    return results.filter { it.id !in local }
}

/** The item of the plate that holds [foodId], if one does: its row on the add screen shows it is on the plate. */
fun plateItemOfFood(model: Model, foodId: String): PlateItem? = model.food.plate.firstOrNull { it.entry.foodId == foodId }

/** The headline of a day: what is left of the goal, how far over it, or what was eaten without one. */
fun kcalHeadline(day: FoodDay): String {
    val goal = day.goal.kcal ?: return "${kcalText(day.totals.kcal)} kcal eaten"
    val left = goal - day.totals.kcal
    return if (left >= 0) "${kcalText(left)} kcal left" else "${kcalText(-left)} kcal over"
}

/** The calories against their goal, calm either way: "1,160 / 2,400 kcal", "2,520 / 2,400 kcal · 120 over", "1,160 kcal". */
fun kcalLine(kcal: Int, goal: Int?): String = when {
    goal == null -> "${kcalText(kcal)} kcal"
    kcal > goal -> "${kcalText(kcal)} / ${kcalText(goal)} kcal · ${kcalText(kcal - goal)} over"
    else -> "${kcalText(kcal)} / ${kcalText(goal)} kcal"
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

/**
 * A day of the week strip over the Food tab: its calories, whether anything was logged, whether it is today, still to
 * come (not tappable) or the day shown.
 */
data class StripDay(val day: String, val name: String, val date: Int, val kcal: Int, val logged: Boolean, val today: Boolean, val future: Boolean, val shown: Boolean)

/** The week of the day shown, Monday first, against the calorie goal. [canGoForward]: a later week has a day that is not to come. */
data class WeekStrip(val days: List<StripDay>, val goalKcal: Int?, val canGoForward: Boolean)

/** The week strip of the Food tab, or null while the tab does not show. */
fun weekStrip(model: Model): WeekStrip? {
    val shown = model.food.day ?: return null
    val now = today(model)
    val days = weekOf(shown).map { day ->
        val entries = entriesOf(model.state.foodLog, day)
        StripDay(day, weekdayName(day), dayOfMonth(day), totalsOf(entries).kcal, entries.isNotEmpty(), day == now, !isPastOrToday(day, now), day == shown)
    }
    return WeekStrip(days, model.state.foodGoal.kcal, days.none { it.today || it.future })
}

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

/** The grid of pictures is open over the sheet of the Food tab. */
fun iconPickerOpen(model: Model): Boolean = when (val sheet = model.food.sheet) {
    is FoodSheet.Amount -> sheet.picker == IconPicker.Open
    is FoodSheet.Own -> sheet.picker == IconPicker.Open
    else -> false
}

/** The picture of an entry of the log: its food's while the phone knows it, else the guess from its name, else the plate. */
fun entryIcon(model: Model, entry: FoodEntry): FoodIcon = iconOfEntry(entry, entry.foodId?.let { knownFood(model, it) })

/** The picture of an item of the plate: as an entry's, or the food it was put on the plate with (a hit of the search). */
fun plateIcon(model: Model, item: PlateItem): FoodIcon = iconOfEntry(item.entry, item.entry.foodId?.let { knownFood(model, it) } ?: item.food)

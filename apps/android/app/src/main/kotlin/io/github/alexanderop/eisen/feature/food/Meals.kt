package io.github.alexanderop.eisen.feature.food

import kotlinx.serialization.Serializable

/*
 * Meals: a named list of foods with their amounts ("Breakfast": oats 80 g, skyr 450 g, a banana), logged in one tap.
 * Pure. A meal keeps the numbers of each item, like an entry: editing or deleting a food, or the meal, changes no
 * entry, and a meal whose food is gone still logs.
 */

/**
 * One food of a meal with its amount and the numbers of that amount, as an entry has them. [amount] is null for a
 * quick entry of calories, which has none and whose amount cannot be changed.
 */
@Serializable
data class MealItem(
    val foodId: String? = null,
    val name: String,
    val amount: Double? = null,
    val unit: FoodUnit = FoodUnit.G,
    val kcal: Int,
    val protein: Double = 0.0,
    val carbs: Double? = null,
    val fat: Double? = null,
)

@Serializable
data class Meal(val id: String, val name: String, val items: List<MealItem>)

/** The most meals the lifter keeps. */
const val MAX_MEALS = 30

/** The most foods in one meal. */
const val MAX_MEAL_ITEMS = 20

const val MAX_MEAL_NAME = 40

/** The item an [entry] becomes in a meal: its food, its amount and its numbers. */
fun mealItemOf(entry: FoodEntry): MealItem = itemWithin(
    MealItem(entry.foodId, entry.name, entry.amount, entry.unit, entry.kcal, entry.protein, entry.carbs, entry.fat),
)

/** [item] in the limits of an entry. */
fun itemWithin(item: MealItem): MealItem {
    val entry = entryWithin(FoodEntry("", item.foodId, item.name, item.amount, item.unit, item.kcal, item.protein, item.carbs, item.fat, 0, ""))
    return MealItem(entry.foodId, entry.name, entry.amount, entry.unit, entry.kcal, entry.protein, entry.carbs, entry.fat)
}

/** [meal] in its limits: a name, at most [MAX_MEAL_ITEMS] items, each in the limits of an entry. Null without items. */
fun mealWithin(meal: Meal): Meal? {
    val items = meal.items.take(MAX_MEAL_ITEMS).map(::itemWithin)
    if (items.isEmpty()) return null
    return meal.copy(name = meal.name.trim().take(MAX_MEAL_NAME).ifEmpty { "Meal" }, items = items)
}

/**
 * [item] at another [amount]: its numbers scaled from what it holds, as the add sheet scales an entry. A quick item has
 * no amount and stays as it is.
 */
fun itemWithAmount(item: MealItem, amount: Double): MealItem {
    val old = item.amount ?: return item
    val per100 = foodOfEntry(FoodEntry("", item.foodId, item.name, old, item.unit, item.kcal, item.protein, item.carbs, item.fat, 0, "")) ?: return item
    val entry = entryOf(per100, amount, "", 0, "")
    return item.copy(amount = entry.amount, kcal = entry.kcal, protein = entry.protein, carbs = entry.carbs, fat = entry.fat)
}

/** What a meal adds up to. */
fun mealTotals(meal: Meal): Totals = totalsOf(meal.items.map { entryOfItem(it, "", 0, "") })

/** The entry an [item] logs: its own numbers, on [day]. */
fun entryOfItem(item: MealItem, id: String, at: Long, day: String): FoodEntry =
    FoodEntry(id, item.foodId, item.name, item.amount, item.unit, item.kcal, item.protein, item.carbs, item.fat, at, day)

/**
 * The ids of the entries one Msg logs: [first] for the first, then `first.2`, `first.3`, ... Unique while [first] is,
 * so one new id from the runtime is enough for a meal or a copied day.
 */
fun entryIds(first: String, count: Int): List<String> = List(count) { index -> if (index == 0) first else "$first.${index + 1}" }

/** One entry per item of [meal], logged at [at] on [day], in the meal's order. */
fun entriesOfMeal(meal: Meal, first: String, at: Long, day: String): List<FoodEntry> =
    meal.items.zip(entryIds(first, meal.items.size)) { item, id -> entryOfItem(item, id, at, day) }

/** The entries of another day copied onto [day]: the same foods and numbers, new ids, logged [at]. */
fun copiedEntries(entries: List<FoodEntry>, first: String, at: Long, day: String): List<FoodEntry> =
    entries.zip(entryIds(first, entries.size)) { entry, id -> entry.copy(id = id, at = at, day = day) }

/**
 * A name for a new meal by the [hour] it is made at (0 to 23): Breakfast before 11, Lunch before 15, a Snack before
 * 18, then Dinner. "Breakfast 2" when a meal of that name is [taken].
 */
fun mealName(hour: Int, taken: List<String>): String {
    val base = when (hour) {
        in 4..10 -> "Breakfast"
        in 11..14 -> "Lunch"
        in 15..17 -> "Snack"
        else -> "Dinner"
    }
    fun free(name: String) = taken.none { it.trim().equals(name, ignoreCase = true) }
    return if (free(base)) base else (2..MAX_MEALS + 1).map { "$base $it" }.firstOrNull(::free) ?: base
}

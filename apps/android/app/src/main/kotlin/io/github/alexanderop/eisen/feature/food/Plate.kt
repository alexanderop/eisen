package io.github.alexanderop.eisen.feature.food

import kotlinx.serialization.Serializable

/*
 * The plate: the foods collected on the add screen, logged together with one button, each as an entry of its own.
 * Pure. Nothing of the plate is in the log until it is logged.
 */

/**
 * A food on the plate. [entry] is what it will log (its id is the item's own; its time and day are given when it is
 * logged). [food] is the food per 100 it was worked out from, so a new amount is worked out from the food again and
 * never drifts; null for quick kcal and for a food of a meal, whose numbers are the meal's own.
 */
@Serializable
data class PlateItem(val entry: FoodEntry, val food: Food? = null)

/** The most foods on one plate: more than any meal ([MAX_MEAL_ITEMS]), less than a screen can still sum up. */
const val MAX_PLATE_ITEMS = 30

/** [amount] of [food] on the plate as item [id]. */
fun plateItemOf(food: Food, amount: Double, id: String): PlateItem = PlateItem(entryOf(food, amount, id, 0, ""), food)

/** Quick kcal on the plate as item [id]. */
fun quickPlateItem(kcal: Int, protein: Int?, name: String, id: String): PlateItem = PlateItem(quickEntry(kcal, protein, name, id, 0, ""))

/** An item of a meal on the plate as item [id], with the meal's numbers. */
fun plateItemOfMeal(item: MealItem, id: String): PlateItem = PlateItem(entryOfItem(item, id, 0, ""))

/**
 * [item] at another [amount], worked out from [food] (its own, or the per-100 of its entry). Its name and food stay
 * the ones it was put on the plate with. A quick item has no amount and stays as it is.
 */
fun plateItemWithAmount(item: PlateItem, food: Food, amount: Double): PlateItem {
    if (item.entry.amount == null) return item
    val changed = entryOf(food, amount, item.entry.id, 0, "").copy(name = item.entry.name, foodId = item.entry.foodId, unit = item.entry.unit)
    return item.copy(entry = changed)
}

/** [item] in the limits of an entry and of a food, for a plate file written by an older build or by hand. */
fun plateItemWithin(item: PlateItem): PlateItem = PlateItem(entryWithin(item.entry), item.food?.let(::foodWithin))

/**
 * The entries the plate logs: one per item, in the plate's order, logged [at] on [day], each with the numbers the plate
 * shows. Their ids come from [first] like a meal's (see [entryIds]).
 */
fun entriesOfPlate(plate: List<PlateItem>, first: String, at: Long, day: String): List<FoodEntry> =
    plate.zip(entryIds(first, plate.size)) { item, id -> item.entry.copy(id = id, at = at, day = day) }

/** What the plate adds up to. */
fun plateTotals(plate: List<PlateItem>): Totals = totalsOf(plate.map { it.entry })

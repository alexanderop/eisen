package io.github.alexanderop.eisen.app.elm

import io.github.alexanderop.eisen.feature.food.FoodEntry
import io.github.alexanderop.eisen.feature.food.MAX_AMOUNT
import io.github.alexanderop.eisen.feature.food.MAX_MEALS
import io.github.alexanderop.eisen.feature.food.MAX_MEAL_ITEMS
import io.github.alexanderop.eisen.feature.food.MIN_AMOUNT
import io.github.alexanderop.eisen.feature.food.Meal
import io.github.alexanderop.eisen.feature.food.copiedEntries
import io.github.alexanderop.eisen.core.counted
import io.github.alexanderop.eisen.feature.food.dayBefore
import io.github.alexanderop.eisen.feature.food.entriesOf
import io.github.alexanderop.eisen.feature.food.entriesOfMeal
import io.github.alexanderop.eisen.feature.food.itemWithAmount
import io.github.alexanderop.eisen.feature.food.mealItemOf
import io.github.alexanderop.eisen.feature.food.mealWithin
import io.github.alexanderop.eisen.feature.food.round1

/*
 * Meals and "Copy yesterday": a few entries logged in one tap, with Undo. The sheet of the meals changes only in
 * [mealAfter]. Nothing here changes an entry that is already logged: a meal keeps its own numbers, like an entry.
 */

/** The sheet of the meals after [msg], with the meals and the log it changes. The one place it changes, see [MealSheet]. */
internal fun mealAfter(msg: Msg.Food, model: Model, env: Env): Next {
    val day = model.food.day ?: return model.only()
    val current = (model.food.sheet as? FoodSheet.Meal)?.sheet
    val meals = model.state.meals
    fun sheet(next: MealSheet?) = model.food { it.copy(sheet = next?.let(FoodSheet::Meal)) }
    return when (msg) {
        Msg.Food.OpenMeals -> if (current == MealSheet.All) model.only() else sheet(MealSheet.All).only()
        Msg.Food.NewMeal -> {
            val entries = entriesOf(model.state.foodLog, day)
            if (entries.isEmpty() || meals.size >= MAX_MEALS) model.only() else sheet(MealSheet.Pick(entries.take(MAX_MEAL_ITEMS).map { it.id })).only()
        }
        is Msg.Food.TogglePick -> {
            val pick = current as? MealSheet.Pick ?: return model.only()
            val order = entriesOf(model.state.foodLog, day).map { it.id }
            if (msg.entryId !in order) return model.only()
            val picked = if (msg.entryId in pick.picked) pick.picked - msg.entryId else pick.picked + msg.entryId
            if (picked.size > MAX_MEAL_ITEMS) return model.only()
            sheet(MealSheet.Pick(order.filter { it in picked })).only()
        }
        is Msg.Food.SaveNewMeal -> {
            val pick = current as? MealSheet.Pick ?: return model.only()
            val entries = entriesOf(model.state.foodLog, day).filter { it.id in pick.picked }
            if (msg.name.isBlank() || entries.isEmpty() || meals.size >= MAX_MEALS) return model.only()
            val meal = mealWithin(Meal("meal:${env.newId()}", msg.name, entries.map(::mealItemOf))) ?: return model.only()
            sheet(null).withState { it.copy(meals = it.meals + meal) }.only()
        }
        is Msg.Food.EditMeal -> {
            val meal = meals.firstOrNull { it.id == msg.id } ?: return model.only()
            val edit = MealSheet.Edit(meal.id, meal.items.map { MealRow(it, it.amount) })
            if (current == edit) model.only() else sheet(edit).only()
        }
        is Msg.Food.SetMealAmount -> {
            val edit = current as? MealSheet.Edit ?: return model.only()
            val row = edit.rows.getOrNull(msg.index) ?: return model.only()
            if (row.amount == null) return model.only()
            val amount = if (msg.amount.isFinite()) round1(msg.amount.coerceIn(MIN_AMOUNT, MAX_AMOUNT)) else row.amount
            if (amount == row.amount) model.only() else sheet(edit.copy(rows = edit.rows.mapIndexed { i, it -> if (i == msg.index) it.copy(amount = amount) else it })).only()
        }
        is Msg.Food.RemoveMealItem -> {
            val edit = current as? MealSheet.Edit ?: return model.only()
            if (msg.index !in edit.rows.indices) model.only() else sheet(edit.copy(rows = edit.rows.filterIndexed { i, _ -> i != msg.index })).only()
        }
        is Msg.Food.SaveMeal -> {
            val edit = current as? MealSheet.Edit ?: return model.only()
            val old = meals.firstOrNull { it.id == edit.id } ?: return sheet(null).only()
            if (msg.name.isBlank() || edit.rows.isEmpty()) return model.only()
            val items = edit.rows.map { row -> row.amount?.let { itemWithAmount(row.saved, it) } ?: row.saved }
            val meal = mealWithin(old.copy(name = msg.name, items = items)) ?: return model.only()
            sheet(MealSheet.All).withState { state -> state.copy(meals = state.meals.map { if (it.id == meal.id) meal else it }) }.only()
        }
        is Msg.Food.DeleteMeal -> {
            if (meals.none { it.id == msg.id }) return model.only()
            val stays = !(current is MealSheet.Edit && current.id == msg.id)
            val deleted = model.withState { state -> state.copy(meals = state.meals.filter { it.id != msg.id }) }
            if (stays) deleted.only() else deleted.food { it.copy(sheet = FoodSheet.Meal(MealSheet.All)) }.only()
        }
        is Msg.Food.AddMeal -> {
            val meal = meals.firstOrNull { it.id == msg.id } ?: return model.only()
            val entries = entriesOfMeal(meal, env.newId(), env.now, day)
            added(model.food { it.copy(sheet = null) }, entries.map { it.id }, "Added ${meal.name}, ${counted(entries.size, "food")}") { log -> log + entries }
        }
        Msg.Food.CopyDayBefore -> {
            if (entriesOf(model.state.foodLog, day).isNotEmpty()) return model.only()
            val before = entriesOf(model.state.foodLog, dayBefore(day))
            if (before.isEmpty()) return model.only()
            val entries = copiedEntries(before, env.newId(), env.now, day)
            added(model, entries.map { it.id }, "Copied ${counted(entries.size, "food")}") { log -> log + entries }
        }
        Msg.Food.UndoAdded -> {
            val ids = model.undo.addedEntries.toSet()
            if (ids.isEmpty()) return model.only()
            model.undo { it.copy(addedEntries = emptyList()) }.withState { state -> state.copy(foodLog = state.foodLog.filter { it.id !in ids }) }.only()
        }
        else -> model.only()
    }
}

/** Entries [ids] logged by one tap: the log [change]d, Undo offered in a snackbar that says [text]. */
private fun added(model: Model, ids: List<String>, text: String, change: (List<FoodEntry>) -> List<FoodEntry>): Next {
    val felt = if (model.state.settings.haptics) listOf(Cmd.Ui(UiEvent.Haptic(Feel.TAP))) else emptyList()
    return model.withState { it.copy(foodLog = change(it.foodLog)) }.undo { it.copy(addedEntries = ids) } and
        felt + Cmd.Ui(UiEvent.Snackbar(text, actionLabel = "Undo", onAction = Msg.Food.UndoAdded, millis = FOOD_UNDO_MS, tag = FOOD_SNACKBAR))
}

package io.github.alexanderop.eisen.feature.food.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.alexanderop.eisen.app.elm.MealSheet
import io.github.alexanderop.eisen.app.elm.Model
import io.github.alexanderop.eisen.app.elm.Msg
import io.github.alexanderop.eisen.app.elm.openFoodDay
import io.github.alexanderop.eisen.app.elm.suggestedMealName
import io.github.alexanderop.eisen.core.counted
import io.github.alexanderop.eisen.core.ui.ConfirmButton
import io.github.alexanderop.eisen.core.ui.EisenColors
import io.github.alexanderop.eisen.core.ui.Eyebrow
import io.github.alexanderop.eisen.core.ui.Icons
import io.github.alexanderop.eisen.core.ui.Stepper
import io.github.alexanderop.eisen.feature.food.MAX_AMOUNT
import io.github.alexanderop.eisen.feature.food.MAX_MEAL_NAME
import io.github.alexanderop.eisen.feature.food.MIN_AMOUNT
import io.github.alexanderop.eisen.feature.food.Meal
import io.github.alexanderop.eisen.feature.food.MealItem
import io.github.alexanderop.eisen.feature.food.amountText
import io.github.alexanderop.eisen.feature.food.gramsText
import io.github.alexanderop.eisen.feature.food.itemWithAmount
import io.github.alexanderop.eisen.feature.food.kcalText
import io.github.alexanderop.eisen.feature.food.mealTotals
import io.github.alexanderop.eisen.feature.food.totalsOf

/** "Breakfast · 646 kcal". */
private fun mealText(meal: Meal): String = "${meal.name} · ${kcalText(mealTotals(meal).kcal)} kcal"

/** The meals as chips: one tap logs every food of one, with Undo. [onEdit] adds "Edit meals" after them. */
@Composable
internal fun MealChips(meals: List<Meal>, modifier: Modifier = Modifier, onEdit: (() -> Unit)? = null, onAdd: (Meal) -> Unit) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Eyebrow("Meals · one tap adds")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (meal in meals) {
                SuggestionChip(
                    onClick = { onAdd(meal) },
                    label = { Text(mealText(meal), fontWeight = FontWeight.SemiBold) },
                    modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = "Add meal ${meal.name}, ${kcalText(mealTotals(meal).kcal)} kcal" },
                    colors = SuggestionChipDefaults.suggestionChipColors(containerColor = EisenColors.Muted, labelColor = EisenColors.Foreground),
                    border = BorderStroke(1.dp, EisenColors.Border),
                )
            }
            if (onEdit != null) {
                SuggestionChip(
                    onClick = onEdit,
                    label = { Text("Edit meals", fontWeight = FontWeight.SemiBold) },
                    modifier = Modifier.heightIn(min = 48.dp),
                    colors = SuggestionChipDefaults.suggestionChipColors(labelColor = EisenColors.Primary),
                    border = null,
                )
            }
        }
    }
}

/** The sheet of the meals, as the model says. */
@Composable
internal fun MealSheets(model: Model, sheet: MealSheet, dispatch: (Msg) -> Unit, close: () -> Unit) {
    when (sheet) {
        is MealSheet.Pick -> PickSheet(model, sheet, dispatch, close)
        MealSheet.All -> AllMealsSheet(model.state.meals, dispatch, close)
        is MealSheet.Edit -> model.state.meals.firstOrNull { it.id == sheet.id }?.let { EditMealSheet(it, sheet, dispatch, close) }
    }
}

/** A row that ticks: the whole line is the target, the box only shows it. */
@Composable
private fun TickRow(checked: Boolean, onToggle: () -> Unit, content: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).toggleable(value = checked, role = Role.Checkbox, onValueChange = { onToggle() }),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Checkbox(checked = checked, onCheckedChange = null, colors = CheckboxDefaults.colors(checkedColor = EisenColors.Primary, checkmarkColor = EisenColors.OnPrimary))
        Column(Modifier.weight(1f)) { content() }
    }
}

@Composable
private fun NameField(name: String, onName: (String) -> Unit) {
    OutlinedTextField(
        value = name,
        onValueChange = { onName(it.take(MAX_MEAL_NAME)) },
        label = { Text("Name of the meal") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
        modifier = Modifier.fillMaxWidth(),
    )
}

/** A new meal from the day: its entries, all ticked, and a name by the time of day. */
@Composable
private fun PickSheet(model: Model, sheet: MealSheet.Pick, dispatch: (Msg) -> Unit, close: () -> Unit) {
    val entries = openFoodDay(model)?.entries.orEmpty()
    var name by rememberSaveable { mutableStateOf(suggestedMealName(model)) }
    val picked = entries.filter { it.id in sheet.picked }
    FoodSheet("New meal", close) {
        NameField(name) { name = it }
        Eyebrow("Foods of this day")
        Column {
            for (entry in entries) {
                TickRow(entry.id in sheet.picked, onToggle = { dispatch(Msg.Food.TogglePick(entry.id)) }) {
                    Text(entry.name, fontWeight = FontWeight.SemiBold)
                    Text(listOfNotNull(entry.amount?.let { amountText(it, entry.unit) }, "${kcalText(entry.kcal)} kcal").joinToString(" · "), color = EisenColors.MutedForeground, fontSize = 13.sp)
                }
            }
        }
        val totals = totalsOf(picked)
        Text("${counted(picked.size, "food")} · ${kcalText(totals.kcal)} kcal · ${gramsText(totals.protein)} protein", color = EisenColors.MutedForeground)
        PrimaryButton("Save meal", enabled = name.isNotBlank() && picked.isNotEmpty()) { dispatch(Msg.Food.SaveNewMeal(name)) }
    }
}

/** Every meal, each to change or delete. */
@Composable
private fun AllMealsSheet(meals: List<Meal>, dispatch: (Msg) -> Unit, close: () -> Unit) {
    FoodSheet("Meals", close) {
        if (meals.isEmpty()) Text("No meals yet. Log what you eat together, then \"Save as meal\" under it.", color = EisenColors.MutedForeground)
        Column {
            meals.forEachIndexed { index, meal ->
                if (index > 0) HorizontalDivider(color = EisenColors.Border)
                Column(
                    Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(role = Role.Button, onClickLabel = "Change") { dispatch(Msg.Food.EditMeal(meal.id)) }.padding(vertical = 8.dp),
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(meal.name, fontWeight = FontWeight.SemiBold)
                    Text("${counted(meal.items.size, "food")} · ${kcalText(mealTotals(meal).kcal)} kcal", color = EisenColors.MutedForeground, fontSize = 13.sp)
                }
            }
        }
    }
}

/** A meal changed: its name, the amount of each food, a food taken out; or the meal deleted. */
@Composable
private fun EditMealSheet(meal: Meal, sheet: MealSheet.Edit, dispatch: (Msg) -> Unit, close: () -> Unit) {
    var name by rememberSaveable(meal.id) { mutableStateOf(meal.name) }
    val items: List<MealItem> = sheet.rows.map { row -> row.amount?.let { itemWithAmount(row.saved, it) } ?: row.saved }
    FoodSheet("Change meal", close) {
        NameField(name) { name = it }
        sheet.rows.forEachIndexed { index, row ->
            val item = items[index]
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(item.name, fontWeight = FontWeight.SemiBold)
                        Text("${kcalText(item.kcal)} kcal · ${gramsText(item.protein)} protein", color = EisenColors.MutedForeground, fontSize = 13.sp)
                    }
                    IconButton(onClick = { dispatch(Msg.Food.RemoveMealItem(index)) }) {
                        Icon(Icons.Trash, contentDescription = "Remove ${item.name}")
                    }
                }
                val amount = row.amount
                if (amount != null) {
                    Stepper(
                        value = amount,
                        onValueChange = { dispatch(Msg.Food.SetMealAmount(index, it)) },
                        step = 10.0,
                        snap = 1.0,
                        min = MIN_AMOUNT,
                        max = MAX_AMOUNT,
                        label = "amount of ${item.name}",
                        format = { amountText(it, item.unit) },
                    )
                }
            }
        }
        if (sheet.rows.isEmpty()) Text("No food left. Delete the meal, or close to keep it as it was.", color = EisenColors.MutedForeground)
        val total = mealTotals(Meal(meal.id, name, items))
        Text("${counted(items.size, "food")} · ${kcalText(total.kcal)} kcal · ${gramsText(total.protein)} protein", color = EisenColors.MutedForeground)
        PrimaryButton("Save meal", enabled = name.isNotBlank() && sheet.rows.isNotEmpty()) { dispatch(Msg.Food.SaveMeal(name)) }
        ConfirmButton("Delete meal", "Tap again to delete", onConfirm = { dispatch(Msg.Food.DeleteMeal(meal.id)) }, modifier = Modifier.fillMaxWidth())
        Text("What you logged of it keeps its numbers.", color = EisenColors.MutedForeground, fontSize = 13.sp)
    }
}

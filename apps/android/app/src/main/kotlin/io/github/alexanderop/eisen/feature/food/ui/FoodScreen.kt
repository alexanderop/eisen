package io.github.alexanderop.eisen.feature.food.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxState
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.alexanderop.eisen.app.elm.FoodDay
import io.github.alexanderop.eisen.app.elm.Model
import io.github.alexanderop.eisen.app.elm.Msg
import io.github.alexanderop.eisen.app.elm.CopyOffer
import io.github.alexanderop.eisen.app.elm.againItems
import io.github.alexanderop.eisen.app.elm.canSaveMeal
import io.github.alexanderop.eisen.app.elm.copyOffer
import io.github.alexanderop.eisen.core.counted
import io.github.alexanderop.eisen.app.elm.kcalHeadline
import io.github.alexanderop.eisen.app.elm.openFoodDay
import io.github.alexanderop.eisen.core.ui.EisenCard
import io.github.alexanderop.eisen.core.ui.EisenColors
import io.github.alexanderop.eisen.core.ui.Eyebrow
import io.github.alexanderop.eisen.core.ui.Icons
import io.github.alexanderop.eisen.core.ui.SwipeHaptics
import io.github.alexanderop.eisen.core.ui.pathIcon
import io.github.alexanderop.eisen.core.ui.pressShapes
import io.github.alexanderop.eisen.feature.food.AgainItem
import io.github.alexanderop.eisen.feature.food.FoodEntry
import io.github.alexanderop.eisen.feature.food.amountText
import io.github.alexanderop.eisen.feature.food.gramsText
import io.github.alexanderop.eisen.feature.food.kcalText

private val ChevronLeft = pathIcon("ChevronLeft", "m15 18-6-6 6-6")
private val Info = pathIcon("Info", "M12 2a10 10 0 1 0 0 20a10 10 0 1 0 0-20z", "M12 16v-4", "M12 8h.01")
private val ScanIcon = pathIcon("Scan", "M3 7V5a2 2 0 0 1 2-2h2", "M17 3h2a2 2 0 0 1 2 2v2", "M21 17v2a2 2 0 0 1-2 2h-2", "M7 21H5a2 2 0 0 1-2-2v-2", "M7 8v8", "M11 8v8", "M14 8v8", "M17 8v8")
private val RowShape = RoundedCornerShape(14.dp)
private val OnDestructive = Color(0xFF2B0A0A)

/**
 * A day of food, over the tabs like the workout, with Back: what is left of the goal, a bar for the calories and
 * one for protein, what was eaten (a swipe deletes it with Undo, a tap changes its amount), the foods to log
 * again with one tap, and Scan, Search and quick kcal at the bottom, in reach of the thumb.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoodScreen(model: Model, dispatch: (Msg) -> Unit) {
    val day = openFoodDay(model) ?: return
    val again = againItems(model)
    val meals = model.state.meals
    val copy = copyOffer(model)
    // Under an open sheet the screen is out of reach, for TalkBack too.
    val covered = model.food.sheet != null
    Column(Modifier.fillMaxSize().background(EisenColors.Background).then(if (covered) Modifier.clearAndSetSemantics { } else Modifier)) {
        CenterAlignedTopAppBar(
            // Between Back and three arrows a large font makes the day smaller instead of cutting it ("Yesterday", "Tue, Nov 24").
            title = {
                Text(
                    day.title,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    softWrap = false,
                    autoSize = TextAutoSize.StepBased(minFontSize = 9.sp, maxFontSize = 17.sp),
                    modifier = Modifier.semantics { heading() },
                )
            },
            navigationIcon = {
                TextButton(onClick = { dispatch(Msg.Food.Close) }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Icon(ChevronLeft, contentDescription = null, modifier = Modifier.size(20.dp))
                    Text("Back", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }
            },
            actions = {
                IconButton(onClick = { dispatch(Msg.Food.PreviousDay) }) { Icon(ChevronLeft, contentDescription = "Previous day") }
                IconButton(onClick = { dispatch(Msg.Food.NextDay) }, enabled = day.canGoForward) { Icon(Icons.ChevronRight, contentDescription = "Next day") }
                IconButton(onClick = { dispatch(Msg.Food.OpenCredit) }) { Icon(Info, contentDescription = "Food data") }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = EisenColors.Background),
        )
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "summary") { DaySummary(day, onGoal = { dispatch(Msg.Food.OpenGoal) }) }
            if (day.entries.isEmpty()) {
                item(key = "empty") {
                    Text("Nothing logged on this day yet.", color = EisenColors.MutedForeground, modifier = Modifier.padding(vertical = 12.dp))
                }
                copy?.let { offer -> item(key = "copy") { CopyButton(offer) { dispatch(Msg.Food.CopyDayBefore) } } }
            }
            items(day.entries, key = { it.id }) { entry ->
                SwipeToDelete(entry.name, onDelete = { dispatch(Msg.Food.DeleteEntry(entry.id)) }, modifier = Modifier.animateItem()) {
                    EntryRow(entry, onOpen = { dispatch(Msg.Food.OpenEntry(entry.id)) })
                }
            }
            if (canSaveMeal(model)) {
                item(key = "save-meal") {
                    TextButton(onClick = { dispatch(Msg.Food.NewMeal) }, modifier = Modifier.heightIn(min = 48.dp), contentPadding = PaddingValues(horizontal = 4.dp)) {
                        Text("Save as meal", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            if (meals.isNotEmpty()) {
                item(key = "meals") { MealChips(meals, Modifier.padding(top = 8.dp), onEdit = { dispatch(Msg.Food.OpenMeals) }) { dispatch(Msg.Food.AddMeal(it.id)) } }
            }
            if (again.isNotEmpty()) {
                item(key = "again") { AgainChips(again, Modifier.padding(top = 8.dp)) { dispatch(Msg.Food.AddAgain(it.food.id, it.amount)) } }
            }
        }
        BottomActions(dispatch)
    }
    FoodSheets(model, dispatch)
}

/** The headline, the calories against the goal and the protein, and the goal's button. */
@Composable
private fun DaySummary(day: FoodDay, onGoal: () -> Unit) {
    EisenCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            val over = day.goal.kcal?.let { day.totals.kcal > it } == true
            Text(
                kcalHeadline(day),
                fontSize = 28.sp,
                fontWeight = FontWeight.ExtraBold,
                color = if (over) EisenColors.Warn else EisenColors.Foreground,
                modifier = Modifier.semantics { heading() },
            )
            Bar("Calories", day.totals.kcal.toDouble(), day.goal.kcal?.toDouble(), "${kcalText(day.totals.kcal)}${day.goal.kcal?.let { " / ${kcalText(it)}" } ?: ""} kcal")
            Bar("Protein", day.totals.protein, day.goal.protein?.toDouble(), "${gramsText(day.totals.protein).removeSuffix(" g")}${day.goal.protein?.let { " / $it" } ?: ""} g")
            TextButton(onClick = onGoal, modifier = Modifier.heightIn(min = 48.dp), contentPadding = PaddingValues(horizontal = 4.dp)) {
                Text(if (day.goal.kcal == null && day.goal.protein == null) "Set a goal" else "Change goal", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/** A labelled bar: what was eaten against the goal, full and lime at the goal. Without a goal only the number. */
@Composable
private fun Bar(label: String, value: Double, goal: Double?, text: String) {
    Column(
        Modifier.fillMaxWidth().semantics(mergeDescendants = true) {
            contentDescription = "$label $text"
            if (goal != null && goal > 0) progressBarRangeInfo = ProgressBarRangeInfo((value / goal).toFloat().coerceIn(0f, 1f), 0f..1f)
        },
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = EisenColors.MutedForeground)
            Text(text, fontWeight = FontWeight.Bold, textAlign = TextAlign.End, color = EisenColors.Foreground)
        }
        if (goal != null && goal > 0) {
            val fraction = (value / goal).toFloat().coerceIn(0f, 1f)
            Box(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)).background(EisenColors.Muted)) {
                if (fraction > 0f) Box(Modifier.fillMaxWidth(fraction).fillMaxHeight().clip(RoundedCornerShape(5.dp)).background(if (value > goal) EisenColors.Warn else EisenColors.Primary))
            }
        }
    }
}

/** An entry: its name, its amount and its calories. A tap changes the amount. */
@Composable
private fun EntryRow(entry: FoodEntry, onOpen: () -> Unit) {
    val amount = entry.amount?.let { amountText(it, entry.unit) }
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(RowShape)
            .background(EisenColors.Card)
            .clickable(role = Role.Button, onClickLabel = "Change") { onOpen() }
            .semantics(mergeDescendants = true) { contentDescription = listOfNotNull(entry.name, amount, "${kcalText(entry.kcal)} kcal").joinToString(", ") }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(entry.name, fontWeight = FontWeight.SemiBold, color = EisenColors.Foreground)
            if (amount != null) Text(amount, color = EisenColors.MutedForeground, fontSize = 14.sp)
        }
        Text(kcalText(entry.kcal), fontWeight = FontWeight.Bold, color = EisenColors.Foreground)
    }
}

/** "Copy yesterday" on an empty day, with what it copies. */
@Composable
private fun CopyButton(offer: CopyOffer, onCopy: () -> Unit) {
    FilledTonalButton(
        onClick = onCopy,
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
        shapes = pressShapes(14.dp),
        colors = ButtonDefaults.filledTonalButtonColors(containerColor = EisenColors.Muted, contentColor = EisenColors.Foreground),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(offer.label, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text("${counted(offer.foods, "food")} · ${kcalText(offer.kcal)} kcal", color = EisenColors.MutedForeground, fontSize = 13.sp)
        }
    }
}

/** The foods logged most often lately: one tap logs one again, with Undo. */
@Composable
internal fun AgainChips(items: List<AgainItem>, modifier: Modifier = Modifier, onAdd: (AgainItem) -> Unit) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Eyebrow("Again · one tap adds")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (item in items) {
                val text = "${item.food.name} ${amountText(item.amount, item.food.unit)}"
                SuggestionChip(
                    onClick = { onAdd(item) },
                    label = { Text(text, fontWeight = FontWeight.SemiBold) },
                    modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = "Add $text again" },
                    colors = SuggestionChipDefaults.suggestionChipColors(containerColor = EisenColors.Muted, labelColor = EisenColors.Foreground),
                    border = BorderStroke(1.dp, EisenColors.Border),
                )
            }
        }
    }
}

/** Scan, Search and quick kcal, at the bottom where the thumb is. */
@Composable
private fun BottomActions(dispatch: (Msg) -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(EisenColors.Card).windowInsetsPadding(WindowInsets.navigationBars).padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Button(onClick = { dispatch(Msg.Food.Scan) }, modifier = Modifier.weight(1f).height(52.dp), shapes = pressShapes(14.dp), contentPadding = PaddingValues(horizontal = 8.dp)) {
            Icon(ScanIcon, contentDescription = null, modifier = Modifier.size(18.dp))
            ButtonLabel("Scan", Modifier.padding(start = 6.dp))
        }
        val tonal = ButtonDefaults.filledTonalButtonColors(containerColor = EisenColors.Muted, contentColor = EisenColors.Foreground)
        FilledTonalButton(onClick = { dispatch(Msg.Food.OpenSearch) }, modifier = Modifier.weight(1f).height(52.dp), shapes = pressShapes(14.dp), colors = tonal, contentPadding = PaddingValues(horizontal = 8.dp)) {
            ButtonLabel("Search")
        }
        FilledTonalButton(
            onClick = { dispatch(Msg.Food.OpenQuick) },
            modifier = Modifier.weight(1f).height(52.dp).semantics { contentDescription = "Quick kcal" },
            shapes = pressShapes(14.dp),
            colors = tonal,
            contentPadding = PaddingValues(horizontal = 8.dp),
        ) {
            ButtonLabel("kcal")
        }
    }
}

/** A button's text on one line: a large font makes it smaller instead of breaking it. */
@Composable
internal fun ButtonLabel(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false, autoSize = TextAutoSize.StepBased(minFontSize = 10.sp, maxFontSize = 16.sp))
}

/**
 * Swiping an entry to the left deletes it, with Undo in the snackbar. It takes 40% of the width or a fling, so a
 * sloppy scroll deletes nothing. TalkBack gets the same as an action.
 */
@Composable
private fun SwipeToDelete(name: String, onDelete: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    // Not saved: an entry brought back by Undo comes back as a row, not as a swiped-away one.
    val state = remember { SwipeToDismissBoxState(SwipeToDismissBoxValue.Settled, positionalThreshold = { it * 0.4f }) }
    SwipeHaptics(state)
    SwipeToDismissBox(
        state = state,
        backgroundContent = {
            if (state.dismissDirection == SwipeToDismissBoxValue.EndToStart) {
                Row(
                    Modifier.fillMaxSize().clip(RowShape).background(EisenColors.Destructive).padding(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Trash, contentDescription = null, tint = OnDestructive)
                    Text("Delete", color = OnDestructive, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }
        },
        modifier = modifier.semantics { customActions = listOf(CustomAccessibilityAction("Delete $name") { onDelete(); true }) },
        enableDismissFromStartToEnd = false,
        enableDismissFromEndToStart = true,
        onDismiss = { onDelete() },
    ) {
        content()
    }
}

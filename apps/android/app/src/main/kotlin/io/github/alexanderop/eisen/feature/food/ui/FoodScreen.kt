package io.github.alexanderop.eisen.feature.food.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import io.github.alexanderop.eisen.app.elm.StripDay
import io.github.alexanderop.eisen.app.elm.WeekStrip
import io.github.alexanderop.eisen.app.elm.weekStrip
import io.github.alexanderop.eisen.feature.food.FoodGoal
import io.github.alexanderop.eisen.feature.food.MAX_MEALS
import io.github.alexanderop.eisen.feature.food.TimeGroup
import io.github.alexanderop.eisen.feature.food.clockText
import io.github.alexanderop.eisen.feature.food.dayOfMonth
import io.github.alexanderop.eisen.feature.food.macroText
import io.github.alexanderop.eisen.app.elm.kcalLine
import io.github.alexanderop.eisen.app.elm.CheckIn
import io.github.alexanderop.eisen.app.elm.checkIn
import io.github.alexanderop.eisen.app.elm.weightCard
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
import io.github.alexanderop.eisen.app.elm.entryIcon
import io.github.alexanderop.eisen.feature.food.FoodIcon
import io.github.alexanderop.eisen.feature.food.iconOf
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
import io.github.alexanderop.eisen.feature.food.plateTotals

private val ChevronLeft = pathIcon("ChevronLeft", "m15 18-6-6 6-6")
private val Copy = pathIcon("Copy", "M10 8h10a2 2 0 0 1 2 2v10a2 2 0 0 1-2 2H10a2 2 0 0 1-2-2V10a2 2 0 0 1 2-2z", "M4 16c-1.1 0-2-.9-2-2V4c0-1.1.9-2 2-2h10c1.1 0 2 .9 2 2")
private val Info = pathIcon("Info", "M12 2a10 10 0 1 0 0 20a10 10 0 1 0 0-20z", "M12 16v-4", "M12 8h.01")
internal val ScanIcon = pathIcon("Scan", "M3 7V5a2 2 0 0 1 2-2h2", "M17 3h2a2 2 0 0 1 2 2v2", "M21 17v2a2 2 0 0 1-2 2h-2", "M7 21H5a2 2 0 0 1-2-2v-2", "M7 8v8", "M11 8v8", "M14 8v8", "M17 8v8")
private val RowShape = RoundedCornerShape(14.dp)
private val OnDestructive = Color(0xFF2B0A0A)

/**
 * The Food tab: a day of food. The week strip on top (a tap opens a day, the arrows or a swipe move a week), what is left
 * of the goal with the calories and the three macros, the day as a timeline of what was eaten together (a group opens
 * to its entries: a tap changes one, a swipe deletes it with Undo), the meals and the foods to log again with one tap,
 * and Scan, Add and quick kcal at the bottom, in reach of the thumb.
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
    // Which groups of the timeline are open: presentation only, by the id of their first entry.
    var open by rememberSaveable { mutableStateOf(emptyList<String>()) }
    Column(Modifier.fillMaxSize().background(EisenColors.Background).then(if (covered) Modifier.clearAndSetSemantics { } else Modifier)) {
        CenterAlignedTopAppBar(
            // A large font makes the day smaller instead of cutting it ("Yesterday", "Tue, Nov 24").
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
            actions = {
                // 48 dp each way at any font size, the whole height of the bar.
                IconButton(onClick = { dispatch(Msg.Food.CopyDay) }, modifier = Modifier.size(48.dp)) { Icon(Copy, contentDescription = "Copy day as Markdown") }
                IconButton(onClick = { dispatch(Msg.Food.OpenCredit) }, modifier = Modifier.size(48.dp)) { Icon(Info, contentDescription = "Food data") }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = EisenColors.Background),
            // A tab: the bar of the app already keeps it clear of the status bar.
            windowInsets = WindowInsets(0),
        )
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 0.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            weekStrip(model)?.let { strip -> item(key = "week") { WeekStripRow(strip, dispatch) } }
            (checkIn(model) as? CheckIn.Shown)?.let { shown -> item(key = "check-in") { CheckInCard(shown, model.state.settings.unit, dispatch) } }
            item(key = "summary") { DaySummary(day, onGoal = { dispatch(Msg.Food.OpenGoal) }) }
            if (day.entries.isEmpty()) {
                item(key = "empty") {
                    Text("Nothing logged on this day yet.", color = EisenColors.MutedForeground, modifier = Modifier.padding(vertical = 12.dp))
                }
                copy?.let { offer -> item(key = "copy") { CopyButton(offer) { dispatch(Msg.Food.CopyDayBefore) } } }
            }
            for (group in day.groups) {
                val key = group.entries.first().id
                val single = group.entries.singleOrNull()
                if (single != null && group.minute != null) {
                    item(key = "one-${single.id}") {
                        SwipeToDelete(single.name, onDelete = { dispatch(Msg.Food.DeleteEntry(single.id)) }, modifier = Modifier.animateItem()) {
                            EntryRow(single, entryIcon(model, single), time = clockText(group.minute), onOpen = { dispatch(Msg.Food.OpenEntry(single.id)) })
                        }
                    }
                } else {
                    val shown = key in open
                    item(key = "group-$key") {
                        GroupRow(group, shown, Modifier.animateItem()) { open = if (shown) open - key else open + key }
                    }
                    if (shown) {
                        items(group.entries, key = { it.id }) { entry ->
                            SwipeToDelete(entry.name, onDelete = { dispatch(Msg.Food.DeleteEntry(entry.id)) }, modifier = Modifier.animateItem().padding(start = 16.dp)) {
                                EntryRow(entry, entryIcon(model, entry), time = null, onOpen = { dispatch(Msg.Food.OpenEntry(entry.id)) })
                            }
                        }
                        if (model.state.meals.size < MAX_MEALS) {
                            item(key = "group-meal-$key") {
                                TextButton(onClick = { dispatch(Msg.Food.NewMealOf(group.entries.map { it.id })) }, modifier = Modifier.padding(start = 16.dp).heightIn(min = 48.dp), contentPadding = PaddingValues(horizontal = 4.dp)) {
                                    Text("Save these as a meal", fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }
            }
            // Under the day's timeline, which keeps the first screen: the weight is one tap a morning, food many.
            item(key = "weight") { WeightCardRow(weightCard(model)) { dispatch(Msg.Weight.Open()) } }
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
        if (model.food.plate.isNotEmpty()) PlateWaiting(plateTotals(model.food.plate), model.food.plate.size) { dispatch(Msg.Food.OpenAdd) }
        BottomActions(dispatch)
    }
    FoodSheets(model, dispatch)
}

/**
 * The week of the day shown: a small bar per day against the goal (or against the fullest day without one), today in
 * the colour of the app, the day shown on a tile. A tap opens a day; a day to come is not tappable. The arrows and a
 * swipe move a week, never into the weeks to come.
 */
@Composable
private fun WeekStripRow(strip: WeekStrip, dispatch: (Msg) -> Unit) {
    val first = strip.days.first()
    val last = strip.days.last()
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { dispatch(Msg.Food.PreviousWeek) }) { Icon(ChevronLeft, contentDescription = "Previous week") }
            Text(
                "${monthDay(first.day)} – ${if (monthOf(first.day) == monthOf(last.day)) last.date.toString() else monthDay(last.day)}",
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                color = EisenColors.MutedForeground,
                fontWeight = FontWeight.SemiBold,
            )
            IconButton(onClick = { dispatch(Msg.Food.NextWeek) }, enabled = strip.canGoForward) { Icon(Icons.ChevronRight, contentDescription = "Next week") }
        }
        val scale = (strip.goalKcal ?: strip.days.maxOf { it.kcal }).coerceAtLeast(1)
        var dragged by remember { mutableFloatStateOf(0f) }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            if (maxWidth >= StripCellWidth * strip.days.size) {
                Row(
                    Modifier.fillMaxWidth().pointerInput(Unit) {
                        detectHorizontalDragGestures(
                            onDragStart = { dragged = 0f },
                            onDragEnd = {
                                if (dragged > 80.dp.toPx()) dispatch(Msg.Food.PreviousWeek) else if (dragged < -80.dp.toPx()) dispatch(Msg.Food.NextWeek)
                            },
                        ) { _, amount -> dragged += amount }
                    },
                ) {
                    for (day in strip.days) StripCell(day, scale, Modifier.weight(1f)) { dispatch(Msg.Food.OpenDay(day.day)) }
                }
            } else {
                // A narrow phone: each day keeps a 48 dp target and the week scrolls under the thumb; the arrows move a week.
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                    for (day in strip.days) StripCell(day, scale, Modifier.width(StripCellWidth)) { dispatch(Msg.Food.OpenDay(day.day)) }
                }
            }
        }
    }
}

@Composable
private fun StripCell(day: StripDay, scale: Int, modifier: Modifier, onOpen: () -> Unit) {
    val label = listOfNotNull("${day.name} ${day.date}", if (day.logged) "${kcalText(day.kcal)} kcal" else null, if (day.today) "today" else null).joinToString(", ")
    Column(
        modifier
            .heightIn(min = 72.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (day.shown) EisenColors.Muted else Color.Transparent)
            .then(if (day.future) Modifier else Modifier.clickable(role = Role.Button, onClickLabel = "Show") { onOpen() })
            .semantics(mergeDescendants = true) {
                contentDescription = label
                selected = day.shown
            }
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(day.name.take(1), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (day.today) EisenColors.Primary else EisenColors.MutedForeground)
        Box(Modifier.width(10.dp).height(28.dp).clip(RoundedCornerShape(5.dp)).background(EisenColors.Muted), contentAlignment = Alignment.BottomCenter) {
            val fraction = (day.kcal.toFloat() / scale).coerceIn(0f, 1f)
            if (fraction > 0f) Box(Modifier.fillMaxWidth().fillMaxHeight(fraction).clip(RoundedCornerShape(5.dp)).background(if (day.future) EisenColors.Muted else EisenColors.Primary))
        }
        Text(day.date.toString(), fontSize = 13.sp, fontWeight = if (day.shown) FontWeight.Bold else FontWeight.Normal, color = if (day.future) EisenColors.MutedForeground else EisenColors.Foreground)
    }
}

/** The narrowest a day of the week strip gets: a target of 48 dp. */
private val StripCellWidth = 48.dp

/** "Nov 23". */
private fun monthDay(day: String): String = "${monthOf(day)} ${dayOfMonth(day)}"

private fun monthOf(day: String): String =
    runCatching { java.time.LocalDate.parse(day).month.name.take(3).lowercase().replaceFirstChar(Char::uppercase) }.getOrDefault("")

/**
 * The headline, the calories and the three macros against their goals, and the goal's button. Over a goal reads
 * "120 over" in the same colours: nothing here warns.
 */
@Composable
private fun DaySummary(day: FoodDay, onGoal: () -> Unit) {
    EisenCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(kcalHeadline(day), fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = EisenColors.Foreground, modifier = Modifier.semantics { heading() })
            val goal = day.goal
            val kcal = day.totals.kcal
            Bar("Calories", kcal.toDouble(), goal.kcal?.toDouble(), kcalLine(kcal, goal.kcal))
            // The three macros side by side, so the timeline starts on the first screen.
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MacroCell("Protein", day.totals.protein, goal.protein, Modifier.weight(1f))
                MacroCell("Carbs", day.totals.carbs, goal.carbs, Modifier.weight(1f))
                MacroCell("Fat", day.totals.fat, goal.fat, Modifier.weight(1f))
            }
            TextButton(onClick = onGoal, modifier = Modifier.heightIn(min = 48.dp), contentPadding = PaddingValues(horizontal = 4.dp)) {
                Text(if (goal == FoodGoal()) "Set a goal" else "Change goal", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/** A labelled bar: what was eaten against the goal, full at the goal and past it. Without a goal only the number. */
@Composable
private fun Bar(label: String, value: Double, goal: Double?, text: String) {
    Column(
        Modifier.fillMaxWidth().semantics(mergeDescendants = true) {
            contentDescription = "$label $text"
            if (goal != null && goal > 0) progressBarRangeInfo = ProgressBarRangeInfo((value / goal).toFloat().coerceIn(0f, 1f), 0f..1f)
        },
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = EisenColors.MutedForeground, modifier = Modifier.padding(end = 8.dp))
            Text(text, fontWeight = FontWeight.Bold, textAlign = TextAlign.End, color = EisenColors.Foreground)
        }
        if (goal != null && goal > 0) {
            val fraction = (value / goal).toFloat().coerceIn(0f, 1f)
            Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(EisenColors.Muted)) {
                if (fraction > 0f) Box(Modifier.fillMaxWidth(fraction).fillMaxHeight().clip(RoundedCornerShape(4.dp)).background(EisenColors.Primary))
            }
        }
    }
}

/** A macro eaten against its goal: its name, "96 / 160 g" (and "10 over" past it, in the same colours), a small bar. */
@Composable
private fun MacroCell(label: String, eaten: Double, goal: Int?, modifier: Modifier) {
    val text = macroText(eaten, goal)
    Column(
        modifier.semantics(mergeDescendants = true) {
            contentDescription = "$label $text"
            if (goal != null && goal > 0) progressBarRangeInfo = ProgressBarRangeInfo((eaten / goal).toFloat().coerceIn(0f, 1f), 0f..1f)
        },
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(label, color = EisenColors.MutedForeground, fontSize = 14.sp)
        val (main, over) = text.split(" · ").let { it.first() to it.getOrNull(1) }
        Text(main, fontWeight = FontWeight.Bold, color = EisenColors.Foreground)
        if (goal != null && goal > 0) {
            val fraction = (eaten / goal).toFloat().coerceIn(0f, 1f)
            Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(EisenColors.Muted)) {
                if (fraction > 0f) Box(Modifier.fillMaxWidth(fraction).fillMaxHeight().clip(RoundedCornerShape(3.dp)).background(EisenColors.Primary))
            }
        }
        if (over != null) Text(over, color = EisenColors.MutedForeground, fontSize = 13.sp)
    }
}

/**
 * A group of the timeline: its time, its foods and what they add up to. A tap opens it to its entries, and closes it.
 * The group without a time holds what was filled in later for this day.
 */
@Composable
private fun GroupRow(group: TimeGroup, open: Boolean, modifier: Modifier, onToggle: () -> Unit) {
    val time = group.minute?.let(::clockText) ?: "No time"
    val names = group.entries.joinToString(", ") { it.name }
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(RowShape)
            .background(EisenColors.Card)
            .clickable(role = Role.Button, onClickLabel = if (open) "Hide its foods" else "Show its foods") { onToggle() }
            .semantics(mergeDescendants = true) {
                contentDescription = "$time, ${counted(group.entries.size, "food")}, ${kcalText(group.totals.kcal)} kcal"
                stateDescription = if (open) "Open" else "Closed"
            }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(time, fontWeight = FontWeight.Bold, color = EisenColors.MutedForeground)
        Text(names, Modifier.weight(1f), fontWeight = FontWeight.SemiBold, color = EisenColors.Foreground, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(kcalText(group.totals.kcal), fontWeight = FontWeight.Bold, color = EisenColors.Foreground)
        Icon(if (open) Icons.ChevronUp else Icons.ChevronDown, contentDescription = null, tint = EisenColors.MutedForeground)
    }
}

/**
 * An entry: its time when it stands alone in the timeline, its picture, its name, its amount and its calories. A tap
 * changes it. The picture is decoration: the row reads the name.
 */
@Composable
private fun EntryRow(entry: FoodEntry, icon: FoodIcon, time: String?, onOpen: () -> Unit) {
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
        if (time != null) Text(time, fontWeight = FontWeight.Bold, color = EisenColors.MutedForeground)
        RowIcon(icon)
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

/** The picture in a chip of "Again". */
private val ChipIconSize = 24.dp

/** The foods logged most often lately, each with its picture: one tap logs one again, with Undo. */
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
                    // A small picture: at a font of 1.3 the chips of usual names still keep one line.
                    icon = { FoodIconImage(iconOf(item.food), size = ChipIconSize) },
                    modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = "Add $text again" },
                    colors = SuggestionChipDefaults.suggestionChipColors(containerColor = EisenColors.Muted, labelColor = EisenColors.Foreground),
                    border = BorderStroke(1.dp, EisenColors.Border),
                )
            }
        }
    }
}

/** A plate left on the add screen: what is on it, and a tap goes back to it. */
@Composable
private fun PlateWaiting(totals: io.github.alexanderop.eisen.feature.food.Totals, foods: Int, onOpen: () -> Unit) {
    TextButton(onClick = onOpen, modifier = Modifier.fillMaxWidth().background(EisenColors.Card).heightIn(min = 48.dp), contentPadding = PaddingValues(horizontal = 16.dp)) {
        Text("On the plate: ${counted(foods, "food")} · ${kcalText(totals.kcal)} kcal", fontWeight = FontWeight.SemiBold)
    }
}

/** Scan, the add screen and quick kcal, at the bottom where the thumb is. */
@Composable
private fun BottomActions(dispatch: (Msg) -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(EisenColors.Card).padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Button(onClick = { dispatch(Msg.Food.Scan) }, modifier = Modifier.weight(1f).height(52.dp), shapes = pressShapes(14.dp), contentPadding = PaddingValues(horizontal = 8.dp)) {
            Icon(ScanIcon, contentDescription = null, modifier = Modifier.size(18.dp))
            ButtonLabel("Scan", Modifier.padding(start = 6.dp))
        }
        val tonal = ButtonDefaults.filledTonalButtonColors(containerColor = EisenColors.Muted, contentColor = EisenColors.Foreground)
        FilledTonalButton(onClick = { dispatch(Msg.Food.OpenAdd) }, modifier = Modifier.weight(1f).height(52.dp), shapes = pressShapes(14.dp), colors = tonal, contentPadding = PaddingValues(horizontal = 8.dp)) {
            ButtonLabel("+ Add")
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

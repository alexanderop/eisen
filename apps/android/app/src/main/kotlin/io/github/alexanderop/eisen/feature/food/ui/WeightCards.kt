package io.github.alexanderop.eisen.feature.food.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.alexanderop.eisen.app.elm.CheckIn
import io.github.alexanderop.eisen.app.elm.Direction
import io.github.alexanderop.eisen.app.elm.LOGGED_DAY_NOTE
import io.github.alexanderop.eisen.app.elm.Model
import io.github.alexanderop.eisen.app.elm.Msg
import io.github.alexanderop.eisen.app.elm.WeightCard
import io.github.alexanderop.eisen.app.elm.noteText
import io.github.alexanderop.eisen.app.elm.weightSheetView
import io.github.alexanderop.eisen.core.WeightUnit
import io.github.alexanderop.eisen.core.counted
import io.github.alexanderop.eisen.core.ui.EisenCard
import io.github.alexanderop.eisen.core.ui.EisenColors
import io.github.alexanderop.eisen.core.ui.Eyebrow
import io.github.alexanderop.eisen.core.ui.Icons
import io.github.alexanderop.eisen.core.ui.Stepper
import io.github.alexanderop.eisen.core.ui.pathIcon
import io.github.alexanderop.eisen.feature.food.bodyText
import io.github.alexanderop.eisen.feature.food.changeText
import io.github.alexanderop.eisen.feature.food.kcalText
import io.github.alexanderop.eisen.feature.food.numberText

private val ChevronLeft = pathIcon("ChevronLeft", "m15 18-6-6 6-6")

private fun arrow(direction: Direction?): String = when (direction) {
    Direction.DOWN -> "↓"
    Direction.UP -> "↑"
    Direction.STEADY -> "→"
    null -> ""
}

/**
 * The weight card of the Food tab, compact: the trend, its arrow and its change a week, and "Log weight". Before the
 * first weight it says what to do.
 */
@Composable
internal fun WeightCardRow(card: WeightCard?, onLog: () -> Unit) {
    EisenCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Eyebrow("Weight")
            if (card == null) {
                Text("Log your weight on a few mornings to see your trend.", color = EisenColors.MutedForeground)
            } else {
                FlowRow(
                    Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = card.label },
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text("Trend ${card.trend}", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = EisenColors.Foreground)
                    val change = card.change
                    Text(
                        if (change == null) "A week of weights shows the change" else "${arrow(card.direction)} $change / week",
                        modifier = Modifier.align(Alignment.CenterVertically),
                        fontWeight = FontWeight.SemiBold,
                        color = EisenColors.MutedForeground,
                    )
                }
            }
            SecondaryButton(if (card?.loggedToday == true) "Change today's weight" else "Log weight", onLog)
        }
    }
}

/**
 * The weekly check-in: last week's numbers, what the lifter burns and the new target against the goal of now, with Take
 * and Keep. Adherence neutral: it says what the numbers were, never whether a plan was kept, and nothing is red.
 */
@Composable
internal fun CheckInCard(checkIn: CheckIn.Shown, unit: WeightUnit, dispatch: (Msg) -> Unit) {
    val offer = checkIn.offer
    EisenCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Eyebrow("Weekly check-in")
            Column(Modifier.semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                val intake = offer.intake
                Text(
                    if (intake == null) "No food logged last week." else "Last week: ${kcalText(intake)} kcal a day, ${counted(offer.loggedDays, "day")} logged.",
                    color = EisenColors.Foreground,
                )
                val from = offer.trendFrom
                val to = offer.trendTo
                if (from != null && to != null) Text("Trend ${changeText(to - from, unit)}: ${bodyText(from, unit)} to ${bodyText(to, unit)}.", color = EisenColors.Foreground)
                Text("You burn about ${kcalText(offer.expenditure)} kcal.", color = EisenColors.Foreground)
                noteText(offer.note)?.let { Text(it, color = EisenColors.MutedForeground, fontSize = 13.sp) }
                Text(
                    "New target: ${kcalText(offer.target)} kcal (${offer.was?.let { "was ${kcalText(it)}" } ?: "no goal yet"})",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = EisenColors.Foreground,
                    modifier = Modifier.semantics { heading() },
                )
                Text(LOGGED_DAY_NOTE, color = EisenColors.MutedForeground, fontSize = 13.sp)
            }
            PrimaryButton("Take ${kcalText(offer.target)} kcal") { dispatch(Msg.Weight.TakeCheckIn) }
            SecondaryButton(offer.was?.let { "Keep ${kcalText(it)} kcal" } ?: "Keep no goal") { dispatch(Msg.Weight.KeepCheckIn) }
        }
    }
}

/**
 * The sheet that logs a weight: the day (an earlier one by the arrow, never one to come), the number in the unit of the
 * settings, Save, and Delete for a day that has a weight. Over the Food tab or History › Progress.
 */
@Composable
fun WeightSheet(model: Model, dispatch: (Msg) -> Unit) {
    val sheet = model.weight.sheet ?: return
    val view = weightSheetView(model) ?: return
    val unit = view.unit
    FoodSheet("Weight", { dispatch(Msg.Weight.Close) }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { dispatch(Msg.Weight.PreviousDay) }) { Icon(ChevronLeft, contentDescription = "Previous day") }
            Text(view.title, Modifier.weight(1f), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            IconButton(onClick = { dispatch(Msg.Weight.NextDay) }, enabled = view.canGoForward) { Icon(Icons.ChevronRight, contentDescription = "Next day") }
        }
        Stepper(
            value = sheet.value,
            onValueChange = { dispatch(Msg.Weight.SetValue(it)) },
            step = 0.1,
            snap = 0.1,
            min = view.min,
            max = view.max,
            label = "weight",
            format = { "${numberText(it)} ${unit.label}" },
            large = true,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        view.logged?.let { Text("Logged $it on this day. Save replaces it.", color = EisenColors.MutedForeground) }
        Text("One weight a day. The same time each morning reads best.", color = EisenColors.MutedForeground, fontSize = 13.sp)
        PrimaryButton("Save ${numberText(sheet.value)} ${unit.label}") { dispatch(Msg.Weight.Save) }
        if (view.logged != null) SecondaryButton("Delete") { dispatch(Msg.Weight.Delete) }
    }
}

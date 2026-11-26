package io.github.alexanderop.eisen.feature.food.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.alexanderop.eisen.app.elm.Model
import io.github.alexanderop.eisen.app.elm.Msg
import io.github.alexanderop.eisen.app.elm.foodToday
import io.github.alexanderop.eisen.app.elm.kcalHeadline
import io.github.alexanderop.eisen.app.elm.kcalLine
import io.github.alexanderop.eisen.feature.food.macroText
import io.github.alexanderop.eisen.feature.food.roundKcal
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import io.github.alexanderop.eisen.core.ui.EisenCard
import io.github.alexanderop.eisen.core.ui.EisenColors
import io.github.alexanderop.eisen.core.ui.Eyebrow
import io.github.alexanderop.eisen.core.ui.pressShapes
import io.github.alexanderop.eisen.feature.food.gramsText
import io.github.alexanderop.eisen.feature.food.kcalText

/**
 * "Food today" on Home: the same kcal line as the Food tab, the calories and the three macros in one line, and Scan and
 * Add, one tap from logging. A tap on the card switches to the Food tab. Below the workout, so "Start workout" stays on
 * the first screen.
 */
@Composable
fun FoodTodayCard(model: Model, dispatch: (Msg) -> Unit, modifier: Modifier = Modifier) {
    val day = foodToday(model)
    val totals = day.totals
    val goal = day.goal
    EisenCard(modifier.fillMaxWidth(), onClick = { dispatch(Msg.Food.Open) }) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Eyebrow("Food today")
            Text(kcalHeadline(day), fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = EisenColors.Foreground, modifier = Modifier.semantics { heading() })
            Text(kcalLine(totals.kcal, goal.kcal), color = EisenColors.MutedForeground)
            val macros = listOf("P" to macroShort(totals.protein, goal.protein), "C" to macroShort(totals.carbs, goal.carbs), "F" to macroShort(totals.fat, goal.fat))
            Text(
                macros.joinToString("  ·  ") { (letter, text) -> "$letter $text" },
                color = EisenColors.MutedForeground,
                // TalkBack reads the words, not the letters.
                modifier = Modifier.clearAndSetSemantics {
                    contentDescription = "Protein ${macroText(totals.protein, goal.protein)}, carbs ${macroText(totals.carbs, goal.carbs)}, fat ${macroText(totals.fat, goal.fat)}"
                },
            )
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { dispatch(Msg.Food.OpenScan) },
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    shapes = pressShapes(14.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp),
                ) { ButtonLabel("Scan") }
                FilledTonalButton(
                    onClick = { dispatch(Msg.Food.OpenAdd) },
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    shapes = pressShapes(14.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(containerColor = EisenColors.Muted, contentColor = EisenColors.Foreground),
                    contentPadding = PaddingValues(horizontal = 8.dp),
                ) { ButtonLabel("+ Add") }
            }
        }
    }
}

/** A macro in a few characters: "96/160", or "96 g" without a goal. */
private fun macroShort(eaten: Double, goal: Int?): String = if (goal == null) gramsText(eaten) else "${roundKcal(eaten)}/$goal"

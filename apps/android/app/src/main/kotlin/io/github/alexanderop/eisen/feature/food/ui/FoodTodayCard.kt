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
import io.github.alexanderop.eisen.core.ui.EisenCard
import io.github.alexanderop.eisen.core.ui.EisenColors
import io.github.alexanderop.eisen.core.ui.Eyebrow
import io.github.alexanderop.eisen.core.ui.pressShapes
import io.github.alexanderop.eisen.feature.food.gramsText
import io.github.alexanderop.eisen.feature.food.kcalText

/**
 * "Food today" on Home: what is left of the goal (or what was eaten), the protein, and Scan and Add, one tap
 * from logging. A tap on the card opens the day.
 */
@Composable
fun FoodTodayCard(model: Model, dispatch: (Msg) -> Unit, modifier: Modifier = Modifier) {
    val day = foodToday(model)
    val totals = day.totals
    EisenCard(modifier.fillMaxWidth(), onClick = { dispatch(Msg.Food.Open) }) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Eyebrow("Food today")
            val goal = day.goal.kcal
            Text(
                if (goal == null) "${kcalText(totals.kcal)} kcal eaten"
                else if (totals.kcal <= goal) "${kcalText(goal - totals.kcal)} left of ${kcalText(goal)} kcal"
                else "${kcalText(totals.kcal - goal)} over ${kcalText(goal)} kcal",
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                color = if (goal != null && totals.kcal > goal) EisenColors.Warn else EisenColors.Foreground,
                modifier = Modifier.semantics { heading() },
            )
            val protein = gramsText(totals.protein).removeSuffix(" g")
            Text("Protein $protein${day.goal.protein?.let { " / $it" } ?: ""} g", color = EisenColors.MutedForeground)
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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

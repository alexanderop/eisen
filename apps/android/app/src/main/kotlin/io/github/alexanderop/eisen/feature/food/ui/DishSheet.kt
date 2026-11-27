package io.github.alexanderop.eisen.feature.food.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.alexanderop.eisen.app.elm.DishFailure
import io.github.alexanderop.eisen.app.elm.DishStep
import io.github.alexanderop.eisen.app.elm.Model
import io.github.alexanderop.eisen.app.elm.Msg
import io.github.alexanderop.eisen.app.elm.PhotoSource
import io.github.alexanderop.eisen.app.elm.plateIcon
import io.github.alexanderop.eisen.core.counted
import io.github.alexanderop.eisen.core.ui.EisenColors
import io.github.alexanderop.eisen.core.ui.Eyebrow
import io.github.alexanderop.eisen.feature.food.DishAi
import io.github.alexanderop.eisen.feature.food.amountText
import io.github.alexanderop.eisen.feature.food.kcalText
import io.github.alexanderop.eisen.feature.food.plateTotals

/**
 * The photo of a dish: the download of the phone's model when it is not there yet, camera or photo, the wait, and what
 * the model saw: the foods the phone knows, for the plate, and the names it knows no food for, each to search.
 */
@Composable
internal fun DishSheet(model: Model, step: DishStep, dispatch: (Msg) -> Unit, close: () -> Unit) {
    FoodSheet("Photo of a dish", close) {
        when (step) {
            DishStep.Choose -> when (model.food.dishAi) {
                DishAi.READY -> {
                    Text("The whole plate from above, in good light. This phone names the foods and guesses their amounts; you correct them on the plate. The photo is thrown away.")
                    PrimaryButton("Take a photo") { dispatch(Msg.Food.ChoosePhoto(PhotoSource.CAMERA)) }
                    SecondaryButton("Choose a photo") { dispatch(Msg.Food.ChoosePhoto(PhotoSource.PICKER)) }
                }
                DishAi.DOWNLOADING -> {
                    Waiting("Downloading Gemini Nano…")
                    Text("Android downloads it once. You can close this and come back.", color = EisenColors.MutedForeground)
                }
                DishAi.DOWNLOADABLE, DishAi.MISSING -> {
                    Text("This needs Gemini Nano, the model Android runs on this phone. Android downloads it once, best on Wi-Fi; after that a photo never leaves the phone.")
                    PrimaryButton("Download", enabled = model.food.dishAi == DishAi.DOWNLOADABLE) { dispatch(Msg.Food.DownloadDishAi) }
                }
            }
            is DishStep.Waiting -> Waiting(if (step.source == PhotoSource.CAMERA) "Waiting for the camera…" else "Waiting for the photo…")
            DishStep.Reading -> Waiting("Looking at the photo…")
            is DishStep.Found -> Found(model, step, dispatch)
            DishStep.NothingFound -> {
                Text("No food found on this photo. The whole plate from above, in good light, reads best.")
                PrimaryButton("Try again") { dispatch(Msg.Food.RetryPhoto) }
            }
            is DishStep.Failed -> {
                Text(
                    when (step.why) {
                        DishFailure.NO_APP -> "This phone has no app for that. Choose a photo instead."
                        DishFailure.UNREADABLE -> "This photo could not be opened."
                        DishFailure.MODEL -> "The phone's model gave no answer. It may be busy: try again in a moment."
                    },
                )
                PrimaryButton("Try again") { dispatch(Msg.Food.RetryPhoto) }
            }
        }
    }
}

@Composable
private fun Found(model: Model, found: DishStep.Found, dispatch: (Msg) -> Unit) {
    if (found.items.isNotEmpty()) {
        Text("A guess: check each food and its amount on the plate before you log it.", color = EisenColors.MutedForeground)
        Column {
            found.items.forEachIndexed { index, item ->
                if (index > 0) HorizontalDivider(color = EisenColors.Border)
                val entry = item.entry
                val detail = listOfNotNull(entry.amount?.let { amountText(it, entry.unit) }, "${kcalText(entry.kcal)} kcal")
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(vertical = 6.dp).semantics(mergeDescendants = true) { contentDescription = (listOf(entry.name) + detail).joinToString(", ") },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    RowIcon(plateIcon(model, item))
                    Column(Modifier.weight(1f)) {
                        Text(entry.name, fontWeight = FontWeight.SemiBold)
                        Text(detail.joinToString(" · "), color = EisenColors.MutedForeground, fontSize = 13.sp)
                    }
                }
            }
        }
    }
    if (found.missing.isNotEmpty()) {
        Eyebrow("Not on this phone")
        Text(
            if (found.items.isEmpty()) "The phone knows no food by these names. Search for one."
            else "Search for one: the foods above go onto the plate first.",
            color = EisenColors.MutedForeground,
        )
        Column {
            found.missing.forEach { name ->
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(name, Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                    TextButton(onClick = { dispatch(Msg.Food.SearchDish(name)) }, modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = "Search $name" }) {
                        Text("Search", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
    if (found.items.isNotEmpty()) PrimaryButton("Put ${counted(found.items.size, "food")} on the plate · ${kcalText(plateTotals(found.items).kcal)} kcal") { dispatch(Msg.Food.PutDish) }
    SecondaryButton("Another photo") { dispatch(Msg.Food.RetryPhoto) }
}

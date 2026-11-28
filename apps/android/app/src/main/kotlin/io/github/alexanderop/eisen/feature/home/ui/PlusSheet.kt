package io.github.alexanderop.eisen.feature.home.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.alexanderop.eisen.app.elm.Model
import io.github.alexanderop.eisen.app.elm.Msg
import io.github.alexanderop.eisen.app.elm.Tab
import io.github.alexanderop.eisen.app.elm.WorkoutEvent
import io.github.alexanderop.eisen.core.ui.EisenColors
import io.github.alexanderop.eisen.core.ui.Eyebrow
import io.github.alexanderop.eisen.core.ui.Icons

/**
 * The bar's `+`: the things done every day first (train, eat, weigh), then the screens that are no tab any more.
 * A row closes the sheet and then does its one thing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlusSheet(model: Model, dispatch: (Msg) -> Unit, close: () -> Unit) {
    ModalBottomSheet(onDismissRequest = close, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = EisenColors.Card) {
        Column(Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, bottom = 16.dp)) {
            fun go(msg: Msg): () -> Unit = { close(); dispatch(msg) }
            val active = model.state.active != null
            PlusRow(Icons.Workouts, if (active) "Resume workout" else "Start workout", go(if (active) Msg.Nav.OpenWorkout else Msg.Workout(WorkoutEvent.Start)))
            PlusRow(Icons.Plus, "Add food", go(Msg.Food.OpenAdd))
            PlusRow(Icons.Progress, "Log weight", go(Msg.Weight.Open()))
            HorizontalDivider(Modifier.padding(vertical = 8.dp, horizontal = 8.dp), color = EisenColors.Border)
            Eyebrow("Open", Modifier.padding(start = 12.dp, bottom = 4.dp))
            PlusRow(Icons.Workouts, "Workouts", go(Msg.Nav.SelectTab(Tab.WORKOUTS)))
            PlusRow(Icons.Timer, "Timer", go(Msg.Nav.SelectTab(Tab.TIMER)))
            PlusRow(Icons.Food, "Food diary", go(Msg.Food.Open))
        }
    }
}

@Composable
private fun PlusRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(role = Role.Button, onClick = onClick).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(icon, contentDescription = null, tint = EisenColors.MutedForeground, modifier = Modifier.size(24.dp))
        Text(label, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
    }
}

package io.github.alexanderop.eisen.feature.timer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.alexanderop.eisen.feature.timer.MINUTE_CALLS
import io.github.alexanderop.eisen.feature.timer.TimerCalls
import io.github.alexanderop.eisen.app.elm.Msg
import io.github.alexanderop.eisen.core.ui.EisenColors
import io.github.alexanderop.eisen.core.ui.Haptic
import io.github.alexanderop.eisen.core.ui.Icons
import io.github.alexanderop.eisen.core.ui.pathIcon
import io.github.alexanderop.eisen.core.ui.rememberFullSheetState
import io.github.alexanderop.eisen.core.ui.rememberHaptics
import kotlinx.coroutines.launch

private val ClockIcon = pathIcon("Clock", "M2 12a10 10 0 1 0 20 0a10 10 0 1 0-20 0", "M12 6v6l4 2")
private val HalfIcon = pathIcon("Halfway", "M2 12a10 10 0 1 0 20 0a10 10 0 1 0-20 0", "M12 2v20")
private val FlagIcon = pathIcon("Final", "M4 22V4", "M4 15s1-1 4-1 5 2 8 2 4-1 4-1V3s-1 1-4 1-5-2-8-2-4 1-4 1z")
private val RepeatIcon = pathIcon("Rounds", "m17 2 4 4-4 4", "M3 11v-1a4 4 0 0 1 4-4h14", "m7 22-4-4 4-4", "M21 13v1a4 4 0 0 1-4 4H3")
private val BeepIcon = pathIcon("Beeps", "M2 12h3l3-8 4 16 3-8h7")
private val PlayIcon = pathIcon("Play", "M7 4.5v15l12-7.5z", filled = true)

/** "Off", "1 min", ...: the segmented choice of how often the minutes left are said. */
private fun minutesLabel(minutes: Int): String = if (minutes == 0) "Off" else "$minutes min"

/**
 * What the voice says while a timer runs, one setting for all timers: the sheet behind "Voice" in a
 * setup and the "Timer voice" section of Settings show the same controls.
 */
@Composable
fun TimerCallsControls(calls: TimerCalls, dispatch: (Msg) -> Unit, modifier: Modifier = Modifier, boxing: Boolean = false) {
    val haptics = rememberHaptics()
    fun set(next: TimerCalls) {
        dispatch(Msg.Timer.SetCalls(next))
        haptics.perform(Haptic.TAP)
    }
    Column(modifier.fillMaxWidth()) {
        // Boxing has its coach: halfway through a round is not said there.
        MinutesChoice(calls, ::set)
        if (!boxing) CallSwitch(HalfIcon, "Halfway", calls.halfway) { set(calls.copy(halfway = it)) }
        CallSwitch(FlagIcon, "Last 30 and 10 seconds", calls.final) { set(calls.copy(final = it)) }
        CallSwitch(RepeatIcon, "Rounds and rest", calls.phases) { set(calls.copy(phases = it)) }
        CallSwitch(BeepIcon, "3-2-1 beeps", calls.beeps) { set(calls.copy(beeps = it)) }
        Text(
            if (boxing) "The coach always calls the combinations. Time left is the time left in the round. Applies to all timers."
            else "Time left is the time left in the round. Applies to all timers.",
            Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            fontSize = 13.sp,
            color = EisenColors.MutedForeground,
        )
        FilledTonalButton(
            onClick = { dispatch(Msg.Timer.PlaySample) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).heightIn(min = 52.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.filledTonalButtonColors(containerColor = EisenColors.Muted, contentColor = EisenColors.Foreground),
        ) {
            Icon(PlayIcon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Play sample", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
    }
}

/** "Time left, every" and its segmented choice. */
@Composable
private fun MinutesChoice(calls: TimerCalls, set: (TimerCalls) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(ClockIcon, contentDescription = null, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(16.dp))
            Text("Time left, every", fontSize = 16.sp, modifier = Modifier.semantics { heading() })
        }
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 8.dp)) {
            MINUTE_CALLS.forEachIndexed { index, minutes ->
                SegmentedButton(
                    selected = calls.minutes == minutes,
                    onClick = { set(calls.copy(minutes = minutes)) },
                    shape = SegmentedButtonDefaults.itemShape(index, MINUTE_CALLS.size),
                    icon = {},
                    colors = SegmentedButtonDefaults.colors(
                        activeContainerColor = EisenColors.Primary,
                        activeContentColor = EisenColors.OnPrimary,
                        inactiveContainerColor = EisenColors.Card,
                        inactiveContentColor = EisenColors.MutedForeground,
                        activeBorderColor = EisenColors.Primary,
                        inactiveBorderColor = EisenColors.Border,
                    ),
                    label = { Text(minutesLabel(minutes), fontWeight = FontWeight.Bold, maxLines = 1) },
                )
            }
        }
    }
}

/** The row itself is the switch, like in Settings. */
@Composable
private fun CallSwitch(icon: ImageVector, label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
            .heightIn(min = 56.dp)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(16.dp))
        Text(label, fontSize = 16.sp, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = null)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun VoiceSheet(calls: TimerCalls, dispatch: (Msg) -> Unit, onClose: () -> Unit, boxing: Boolean = false) {
    val sheetState = rememberFullSheetState()
    val scope = rememberCoroutineScope()
    ModalBottomSheet(onDismissRequest = onClose, sheetState = sheetState, containerColor = EisenColors.Card) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Voice", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f).semantics { heading() })
            IconButton(onClick = { scope.launch { sheetState.hide() }.invokeOnCompletion { onClose() } }) {
                Icon(Icons.Close, contentDescription = "Close")
            }
        }
        Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(0.dp)) {
            TimerCallsControls(calls, dispatch, boxing = boxing)
        }
    }
}

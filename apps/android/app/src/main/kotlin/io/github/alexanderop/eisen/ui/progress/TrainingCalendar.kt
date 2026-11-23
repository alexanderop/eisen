package io.github.alexanderop.eisen.ui.progress

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.alexanderop.eisen.domain.CalendarDay
import io.github.alexanderop.eisen.domain.holdText
import io.github.alexanderop.eisen.domain.durationMinutes
import io.github.alexanderop.eisen.ui.EisenColors
import io.github.alexanderop.eisen.ui.Haptic
import io.github.alexanderop.eisen.ui.formatDate
import io.github.alexanderop.eisen.ui.rememberHaptics
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val LABEL = 18.dp
private val GAP = 4.dp
private val CORNER = 5.dp

private fun local(pattern: String, time: Long): String =
    DateTimeFormatter.ofPattern(pattern, Locale.getDefault()).format(Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault()))

/** One line per workout or timer of the day: "Workout A, 50 min", "Tabata, 4:00". */
private fun entries(day: CalendarDay): List<String> =
    day.workouts.map { workout ->
        val minutes = durationMinutes(workout)
        if (minutes == null) workout.name else "${workout.name}, $minutes min"
    } + day.timers.map { "${it.name}, ${holdText(it.seconds)}" }

private fun describe(day: CalendarDay): String = entries(day).joinToString(", ")

/** One square per day, a column per week, Monday on top. Days with a workout are lit. */
@Composable
fun TrainingCalendar(weeks: List<List<CalendarDay>>, modifier: Modifier = Modifier) {
    val haptics = rememberHaptics()
    var picked by rememberSaveable { mutableStateOf<Long?>(null) }
    val selected = weeks.flatten().firstOrNull { it.time == picked && it.trained }

    // A month name over the first column of every month.
    val months = weeks.mapIndexed { index, week ->
        val month = local("M", week[0].time)
        val before = if (index == 0) null else local("M", weeks[index - 1][0].time)
        if (month == before) "" else local("LLL", week[0].time)
    }

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth().clearAndSetSemantics {}) {
            Spacer(Modifier.width(LABEL + 6.dp))
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(GAP)) {
                for (month in months) {
                    Box(Modifier.weight(1f)) {
                        // A month name is wider than a column: it runs over the empty ones after it.
                        Text(
                            month,
                            modifier = Modifier.wrapContentWidth(Alignment.Start, unbounded = true),
                            color = EisenColors.MutedForeground,
                            fontSize = 11.sp,
                            softWrap = false,
                            maxLines = 1,
                        )
                    }
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(GAP)) {
            for (weekday in 0 until 7) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.width(LABEL).clearAndSetSemantics {}) {
                        if (weekday % 2 == 0) {
                            Text(local("ccccc", weeks[0][weekday].time), color = EisenColors.MutedForeground, fontSize = 11.sp)
                        }
                    }
                    Spacer(Modifier.width(6.dp))
                    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(GAP)) {
                        for (week in weeks) {
                            val day = week[weekday]
                            Day(
                                day = day,
                                picked = day.time == picked,
                                onPick = {
                                    picked = if (day.time == picked) null else day.time
                                    haptics.perform(Haptic.TAP)
                                },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        }

        FlowRow(
            Modifier
                .fillMaxWidth()
                .padding(top = 6.dp)
                .heightIn(min = 20.dp)
                .semantics { liveRegion = LiveRegionMode.Polite },
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (selected != null) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(formatDate(selected.time), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    for (line in entries(selected)) Text(line, fontSize = 13.sp, color = EisenColors.MutedForeground)
                }
            } else {
                Text("Tap a lit day to see the workout.", fontSize = 13.sp, color = EisenColors.MutedForeground)
            }
        }
    }
}

@Composable
private fun Day(day: CalendarDay, picked: Boolean, onPick: () -> Unit, modifier: Modifier) {
    val trained = day.trained
    val square = modifier.aspectRatio(1f)
    if (!trained) {
        Box(square.drawBehind { drawDay(day, picked = false, trained = false) })
        return
    }
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.88f else 1f, tween(80), label = "press")
    val description = "${formatDate(day.time)}: ${describe(day)}"
    Box(
        square
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .drawBehind { drawDay(day, picked, trained = true) }
            .semantics {
                contentDescription = description
                selected = picked
            }
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onPick),
    )
}

/** The rings sit outside the square with a gap in the card colour, like the web's box-shadows. */
private fun DrawScope.drawDay(day: CalendarDay, picked: Boolean, trained: Boolean) {
    val corner = CORNER.toPx()
    fun ring(spread: Float, color: Color) = drawRoundRect(
        color,
        topLeft = Offset(-spread, -spread),
        size = Size(size.width + spread * 2, size.height + spread * 2),
        cornerRadius = CornerRadius(corner + spread),
    )
    when {
        picked -> {
            ring(4.dp.toPx(), EisenColors.Primary)
            ring(2.dp.toPx(), EisenColors.Card)
        }
        day.today -> {
            ring(3.5.dp.toPx(), EisenColors.Foreground)
            ring(2.dp.toPx(), EisenColors.Card)
        }
    }
    when {
        trained -> drawRoundRect(EisenColors.Primary, cornerRadius = CornerRadius(corner))
        day.future -> {
            val line = 1.dp.toPx()
            drawRoundRect(
                EisenColors.Muted,
                topLeft = Offset(line / 2, line / 2),
                size = Size(size.width - line, size.height - line),
                cornerRadius = CornerRadius(corner - line / 2),
                style = Stroke(line),
            )
        }
        else -> drawRoundRect(EisenColors.Muted, cornerRadius = CornerRadius(corner))
    }
}

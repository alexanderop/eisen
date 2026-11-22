package io.github.alexanderop.eisen.ui.progress

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import io.github.alexanderop.eisen.data.Store
import io.github.alexanderop.eisen.domain.AppState
import io.github.alexanderop.eisen.domain.Equipment
import io.github.alexanderop.eisen.domain.calendarWeeks
import io.github.alexanderop.eisen.domain.formatNumber
import io.github.alexanderop.eisen.domain.recordOf
import io.github.alexanderop.eisen.domain.totalReps
import io.github.alexanderop.eisen.domain.weekStreak
import io.github.alexanderop.eisen.ui.EisenColors
import io.github.alexanderop.eisen.ui.fmt
import io.github.alexanderop.eisen.ui.formatDate
import io.github.alexanderop.eisen.ui.formatShortDate
import java.util.Locale

private const val WEEKS = 12

/** Colours checked for colour-blind separation on the card surface. A lift keeps its colour. */
private val SERIES_COLORS = listOf(
    Color(0xFF3987E5),
    Color(0xFFD95926),
    Color(0xFF199E70),
    Color(0xFFC98500),
    Color(0xFFD55181),
    Color(0xFF0CA30C),
    Color(0xFF9085E9),
    Color(0xFFE66767),
)

private val Gutter = Modifier.padding(horizontal = 16.dp)

@Composable
fun ProgressScreen() {
    val state = Store.state
    // Fixed while the screen is open, like the web view, so the calendar does not shift under a finger.
    val now = remember { System.currentTimeMillis() }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = 20.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            "Progress",
            modifier = Gutter.semantics { heading() },
            fontSize = 30.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = (-0.02).em,
        )

        if (state.history.isEmpty()) {
            Section(Gutter) {
                Text("Finish your first workout and your progress shows up here.", color = EisenColors.MutedForeground)
            }
            return@Column
        }

        Consistency(state, now)

        var range by rememberSaveable { mutableStateOf(Range.THREE_MONTHS) }
        RangePicker(range, onChange = { range = it })

        val options = remember(state.routines, state.history, state.customExercises) { optionsOf(state) }
        val entries = remember(state.history, state.settings.unit, range) { entriesOf(state, range, now) }

        AllLifts(state, options, entries, range)
        ExerciseCard(state, options, entries, range)
    }
}

@Composable
private fun Consistency(state: AppState, now: Long) {
    val weeks = remember(state.history) { calendarWeeks(state.history, now, WEEKS) }
    val counts = weeks.map { week -> week.sumOf { it.workouts.size } }
    val firstTrained = counts.indexOfFirst { it > 0 }
    val streak = remember(state.history) { weekStreak(state.history.map { it.startedAt }, now) }
    // Weeks before the first workout do not pull the average down.
    val perWeek = if (firstTrained == -1) 0.0 else counts.sum().toDouble() / (counts.size - firstTrained)

    Section(Gutter) {
        SectionHead("Consistency", "Last $WEEKS weeks")
        TrainingCalendar(weeks)
        Stats(
            listOf(
                Stat("This week", counts.last().toString()),
                Stat("Weeks in a row", streak.toString()),
                Stat("Per week", String.format(Locale.ROOT, "%.1f", perWeek)),
            ),
        )
    }
}

@Composable
private fun RangePicker(range: Range, onChange: (Range) -> Unit) {
    SingleChoiceSegmentedButtonRow(Gutter.fillMaxWidth()) {
        Range.entries.forEachIndexed { index, option ->
            SegmentedButton(
                selected = range == option,
                onClick = { onChange(option) },
                shape = SegmentedButtonDefaults.itemShape(index, Range.entries.size),
                colors = SegmentedButtonDefaults.colors(
                    inactiveContainerColor = EisenColors.Card,
                    inactiveContentColor = EisenColors.MutedForeground,
                    activeBorderColor = EisenColors.Border,
                    inactiveBorderColor = EisenColors.Border,
                ),
                label = { Text(option.label, fontWeight = FontWeight.SemiBold) },
            )
        }
    }
}

/** Every weighted exercise as its own line on one axis. */
@Composable
private fun AllLifts(state: AppState, options: List<Option>, entries: List<Entry>, range: Range) {
    val weighted = options.filter { it.equipment != Equipment.BODYWEIGHT }
    val lifts = remember(weighted.map { it.id }, entries) {
        weighted.take(SERIES_COLORS.size)
            .mapIndexed { index, option ->
                ChartSeries(
                    id = option.id,
                    name = option.name,
                    color = SERIES_COLORS[index],
                    points = pointsOf(option.id, entries, state).map { ChartPoint(it.time, it.value) },
                )
            }
            .filter { it.points.isNotEmpty() }
    }
    if (lifts.size <= 1) return

    // A day from another range would sit outside the chart, so a new range starts at rest.
    var liftsAt by rememberSaveable(range) { mutableStateOf<Long?>(null) }

    Section(Gutter) {
        SectionHead("All lifts", liftsAt?.let(::formatDate) ?: "Latest")
        TimeChart(
            series = lifts,
            active = liftsAt,
            onActiveChange = { liftsAt = it },
            format = { fmt(it) },
            formatTime = ::formatShortDate,
            label = "Weight of every lift over time",
        )
        Column(Modifier.padding(top = 12.dp)) {
            for (line in lifts) {
                // Each lift's weight on the day under the crosshair: the last one logged up to then.
                val upTo = liftsAt ?: Long.MAX_VALUE
                val value = line.points.lastOrNull { it.time <= upTo }?.let { fmt(it.value) } ?: "–"
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 6.dp).semantics(mergeDescendants = true) {},
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(Modifier.width(16.dp).height(3.dp).background(line.color, RoundedCornerShape(2.dp)))
                    Text(line.name, Modifier.weight(1f), color = EisenColors.MutedForeground, fontSize = 15.sp)
                    Text(value, fontSize = 15.sp, fontWeight = FontWeight.Bold, style = LocalTextStyle.current.copy(fontFeatureSettings = "tnum"))
                }
            }
        }
        if (weighted.size > SERIES_COLORS.size) {
            Text(
                "Showing the first ${SERIES_COLORS.size} exercises.",
                Modifier.padding(top = 4.dp),
                color = EisenColors.MutedForeground,
                fontSize = 13.sp,
            )
        }
    }
}

/** One exercise, with what happened in every workout. */
@Composable
private fun ExerciseCard(state: AppState, options: List<Option>, entries: List<Entry>, range: Range) {
    var picked by rememberSaveable { mutableStateOf<String?>(null) }
    val selected = options.firstOrNull { it.id == picked } ?: options.firstOrNull() ?: return
    val bodyweight = selected.equipment == Equipment.BODYWEIGHT
    val points = remember(selected.id, entries) { pointsOf(selected.id, entries, state) }
    var pointAt by rememberSaveable(selected.id, range) { mutableStateOf<Long?>(null) }

    fun format(value: Double): String = if (bodyweight) "${formatNumber(value)} reps" else fmt(value)

    ExerciseChips(options, selected.id, onPick = { picked = it })

    Section(Gutter) {
        SectionHead(
            selected.name,
            if (bodyweight) "Total reps per workout" else "Now ${fmt(Store.progressOf(selected.id).weight)}",
        )

        // The workout under the crosshair, or the latest one when the chart is at rest.
        val shown = points.firstOrNull { it.time == pointAt } ?: points.lastOrNull()
        if (shown == null) {
            Text(
                "No logged sets for this exercise in this time.",
                Modifier.fillMaxWidth().padding(vertical = 32.dp),
                color = EisenColors.MutedForeground,
                textAlign = TextAlign.Center,
            )
            return@Section
        }

        Readout(
            value = format(shown.value),
            sets = shown.log.sets.joinToString(" ") { it?.toString() ?: "–" },
            date = (
                listOf(formatDate(shown.time)) + listOfNotNull(
                    if (shown.best) "Best ever" else if (shown.record) "Record that day" else null,
                    if (shown.missed) "Missed reps" else null,
                    if (shown.deload) "Deload" else null,
                )
                ).joinToString(" · "),
        )

        val series = remember(selected, points) {
            listOf(
                ChartSeries(
                    id = selected.id,
                    name = selected.name,
                    color = EisenColors.Primary,
                    points = points.map { ChartPoint(it.time, it.value, it.missed, it.deload, record = it.best) },
                ),
            )
        }
        TimeChart(
            series = series,
            active = pointAt,
            onActiveChange = { pointAt = it },
            format = { if (bodyweight) formatNumber(it) else fmt(it) },
            formatTime = ::formatShortDate,
            label = "${selected.name} over time",
            events = true,
        )

        Marks(bodyweight)

        // The best fully completed workout of the exercise ever, and the day it happened.
        val record = recordOf(state.history, selected.id)
        val gained = points.last().value - points.first().value
        Stats(
            listOf(
                Stat("Workouts", points.size.toString()),
                Stat(
                    "Best ever",
                    record?.let { found ->
                        val log = found.log
                        format(
                            if (log.equipment == Equipment.BODYWEIGHT) {
                                totalReps(log).toDouble()
                            } else {
                                inCurrentUnit(log.weight, found.workout.unit, log.equipment, state.settings.unit)
                            },
                        )
                    } ?: "–",
                    record?.let { formatShortDate(it.workout.startedAt) },
                ),
                Stat("Gained", (if (gained >= 0) "+" else "") + format(gained)),
            ),
        )
    }
}

@Composable
private fun ExerciseChips(options: List<Option>, selected: String, onPick: (String) -> Unit) {
    // Runs to the screen edges, so a chip scrolls out under the edge instead of being cut at the gutter.
    LazyRow(
        Modifier
            .fillMaxWidth()
            .selectableGroup()
            .semantics { contentDescription = "Exercise" },
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(options, key = { it.id }) { option ->
            val on = option.id == selected
            FilterChip(
                selected = on,
                // One chip is always on: tapping the active chip again changes nothing.
                onClick = { if (!on) onPick(option.id) },
                label = { Text(option.name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold) },
                modifier = Modifier.height(40.dp),
                shape = CircleShape,
                border = null,
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = EisenColors.Muted,
                    labelColor = EisenColors.MutedForeground,
                    selectedContainerColor = EisenColors.Primary,
                    selectedLabelColor = EisenColors.OnPrimary,
                ),
            )
        }
    }
}

@Composable
private fun Readout(value: String, sets: String, date: String) {
    Column(Modifier.padding(bottom = 8.dp).semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                value,
                Modifier.alignByBaseline(),
                fontSize = 30.sp,
                lineHeight = 33.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-0.02).em,
            )
            Text(
                sets,
                Modifier.weight(1f).alignByBaseline(),
                fontWeight = FontWeight.SemiBold,
                style = LocalTextStyle.current.copy(fontFeatureSettings = "tnum"),
                textAlign = TextAlign.End,
            )
        }
        Text(date, color = EisenColors.MutedForeground, fontSize = 13.sp)
    }
}

/** What the chart's markers mean. The legend only lists what this exercise can show. */
@Composable
private fun Marks(bodyweight: Boolean) {
    FlowRow(
        Modifier.fillMaxWidth().padding(top = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Mark("All reps", MarkKind.DONE)
        if (!bodyweight) Mark("Missed reps", MarkKind.MISSED)
        if (!bodyweight) Mark("Deload", MarkKind.DELOAD)
        Mark("Best ever", MarkKind.BEST)
    }
}

private enum class MarkKind { DONE, MISSED, DELOAD, BEST }

@Composable
private fun Mark(text: String, mark: MarkKind) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        // Drawn on the web's 16-unit grid so the shapes match the chart markers.
        Canvas(Modifier.size(14.dp)) {
            val unit = size.width / 16f
            val center = Offset(8 * unit, 8 * unit)
            when (mark) {
                MarkKind.DONE -> drawCircle(EisenColors.Primary, 4.5f * unit, center)
                MarkKind.MISSED -> drawCircle(EisenColors.Warn, 4.5f * unit, center, style = Stroke(2 * unit))
                MarkKind.DELOAD -> drawPath(
                    Path().apply {
                        moveTo(2.5f * unit, 3.5f * unit)
                        lineTo(13.5f * unit, 3.5f * unit)
                        lineTo(8 * unit, 13.5f * unit)
                        close()
                    },
                    EisenColors.Primary,
                )
                MarkKind.BEST -> {
                    drawCircle(EisenColors.Primary, 3 * unit, center)
                    drawCircle(EisenColors.Primary, 6.5f * unit, center, alpha = 0.7f, style = Stroke(1.5f * unit))
                }
            }
        }
        Text(text, color = EisenColors.MutedForeground, fontSize = 12.sp)
    }
}

@Composable
private fun Section(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = EisenColors.Card, contentColor = EisenColors.Foreground),
        border = BorderStroke(1.dp, EisenColors.Border),
    ) {
        Column(Modifier.padding(16.dp), content = content)
    }
}

@Composable
private fun SectionHead(title: String, aside: String) {
    Row(
        Modifier.fillMaxWidth().padding(bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            title,
            Modifier.weight(1f).semantics { heading() },
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(aside, color = EisenColors.MutedForeground, fontSize = 15.sp)
    }
}

private class Stat(val label: String, val value: String, val note: String? = null)

@Composable
private fun Stats(stats: List<Stat>) {
    Column(Modifier.padding(top = 16.dp)) {
        HorizontalDivider(color = EisenColors.Border)
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (stat in stats) {
                Column(Modifier.weight(1f).semantics(mergeDescendants = true) {}) {
                    Text(stat.label, color = EisenColors.MutedForeground, fontSize = 12.sp)
                    Text(stat.value, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    stat.note?.let { Text(it, color = EisenColors.MutedForeground, fontSize = 12.sp) }
                }
            }
        }
    }
}

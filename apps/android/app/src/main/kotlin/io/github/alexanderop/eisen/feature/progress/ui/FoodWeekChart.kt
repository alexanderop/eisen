package io.github.alexanderop.eisen.feature.progress.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.alexanderop.eisen.app.elm.FoodWeek
import io.github.alexanderop.eisen.app.elm.WeekDay
import io.github.alexanderop.eisen.core.ui.EisenColors
import io.github.alexanderop.eisen.feature.progress.niceTicks
import java.util.Locale

private val BAR_AREA = 132.dp
private val BarShape = RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp)

private fun kcal(value: Int): String = "%,d".format(Locale.US, value)

/**
 * The last seven days of food as bars against the calorie goal (a dashed line), today on the right. A bar is the
 * whole column: a tap opens that day's Food screen. A day over the goal is drawn like an over day on the Food screen.
 */
@Composable
internal fun FoodWeekBars(week: FoodWeek, onOpen: (String) -> Unit) {
    val most = maxOf(week.days.maxOf { it.kcal }, week.goalKcal ?: 0).coerceAtLeast(1)
    // The top of the scale is a round number above the highest bar and the goal, as the other charts do it.
    val top = niceTicks(0.0, most * 1.05, target = 3).last().coerceAtLeast(1.0)
    val goal = week.goalKcal
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (goal != null) Text("Goal ${kcal(goal)} kcal", color = EisenColors.MutedForeground, fontSize = 12.sp)
        Row(
            Modifier
                .fillMaxWidth()
                .drawWithContent {
                    drawContent()
                    if (goal != null) {
                        val y = BAR_AREA.toPx() * (1f - (goal / top).toFloat())
                        drawLine(
                            EisenColors.Primary,
                            Offset(0f, y),
                            Offset(size.width, y),
                            strokeWidth = 2.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 6.dp.toPx())),
                        )
                    }
                },
        ) {
            for (day in week.days) Bar(day, top, goal, Modifier.weight(1f)) { onOpen(day.day) }
        }
    }
}

@Composable
private fun Bar(day: WeekDay, top: Double, goal: Int?, modifier: Modifier, onOpen: () -> Unit) {
    val over = goal != null && day.kcal > goal
    val said = if (day.logged) "${kcal(day.kcal)} kcal, ${Math.round(day.protein)} g protein" else "nothing logged"
    Column(
        modifier
            .heightIn(min = 48.dp)
            .clickable(role = Role.Button, onClickLabel = "Open the day") { onOpen() }
            .semantics(mergeDescendants = true) { contentDescription = "${day.name}, $said" },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.fillMaxWidth().height(BAR_AREA).padding(horizontal = 5.dp), contentAlignment = Alignment.BottomCenter) {
            val fraction = (day.kcal / top).toFloat().coerceIn(0f, 1f)
            if (fraction > 0f) {
                Box(
                    Modifier.fillMaxWidth().fillMaxHeight(fraction).clip(BarShape)
                        .background(if (over) EisenColors.Warn else EisenColors.Primary),
                )
            } else {
                // An empty day still shows where its bar would stand.
                Box(Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)).background(EisenColors.Muted))
            }
        }
        Text(
            day.name,
            // A little room on each side, so a large font makes the names smaller instead of running them together.
            Modifier.padding(start = 3.dp, end = 3.dp, top = 6.dp, bottom = 4.dp),
            color = EisenColors.MutedForeground,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            softWrap = false,
            autoSize = TextAutoSize.StepBased(minFontSize = 8.sp, maxFontSize = 12.sp),
        )
    }
}

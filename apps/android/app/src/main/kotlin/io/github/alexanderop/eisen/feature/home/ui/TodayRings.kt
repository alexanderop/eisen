package io.github.alexanderop.eisen.feature.home.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.alexanderop.eisen.app.elm.Model
import io.github.alexanderop.eisen.app.elm.Msg
import io.github.alexanderop.eisen.app.elm.Tab
import io.github.alexanderop.eisen.app.elm.TodayRing
import io.github.alexanderop.eisen.app.elm.todayRings
import io.github.alexanderop.eisen.core.ui.EisenCard
import io.github.alexanderop.eisen.core.ui.EisenColors

/**
 * The top of Today: two rings, the workouts of the last 7 days and the calories of today. A tap on one opens what it
 * counts (Progress, the Food diary).
 */
@Composable
fun TodayRings(model: Model, dispatch: (Msg) -> Unit) {
    val rings = todayRings(model)
    EisenCard(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(vertical = 16.dp, horizontal = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            Ring(rings.week, EisenColors.Primary) { dispatch(Msg.Nav.SelectTab(Tab.HISTORY)) }
            Ring(rings.kcal, if (rings.kcal.over) EisenColors.Warn else EisenColors.Rest) { dispatch(Msg.Food.Open) }
        }
    }
}

@Composable
private fun Ring(ring: TodayRing, color: Color, onClick: () -> Unit) {
    val description = ring.description
    Column(
        Modifier
            .clickable(onClick = onClick)
            .padding(8.dp)
            .clearAndSetSemantics {
                contentDescription = description
                role = Role.Button
                onClick { onClick(); true }
            },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(112.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(112.dp)) {
                val stroke = 10.dp.toPx()
                val inset = stroke / 2
                val arc = Size(size.width - stroke, size.height - stroke)
                drawArc(EisenColors.Muted, 0f, 360f, false, Offset(inset, inset), arc, style = Stroke(stroke))
                val sweep = 360f * ring.fraction.coerceIn(0f, 1f)
                if (sweep > 0f) drawArc(color, -90f, sweep, false, Offset(inset, inset), arc, style = Stroke(stroke, cap = StrokeCap.Round))
            }
            // A large font shrinks the number instead of pushing it out of the ring.
            Text(
                ring.value,
                Modifier.width(84.dp),
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center,
                maxLines = 1,
                softWrap = false,
                autoSize = TextAutoSize.StepBased(minFontSize = 10.sp, maxFontSize = 20.sp),
            )
        }
        Text(ring.label, color = EisenColors.MutedForeground, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
    }
}

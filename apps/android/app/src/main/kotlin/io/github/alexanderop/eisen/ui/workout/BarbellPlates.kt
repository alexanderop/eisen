package io.github.alexanderop.eisen.ui.workout

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import io.github.alexanderop.eisen.domain.formatNumber
import io.github.alexanderop.eisen.ui.EisenColors
import kotlin.math.max
import kotlin.math.sqrt

/**
 * One side of the bar with its plates, so the lifter sees what to load at a glance.
 * Bigger plates are taller and thicker; heights grow with the square root so small plates stay visible.
 * Each plate carries its weight, like the print on a real plate.
 */
@Composable
fun BarbellPlates(plates: List<Double>, largest: Double, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    // In dp, not sp: the label has to fit the plate, which does not grow with the font size setting.
    val labelStyle = TextStyle(
        color = EisenColors.OnPrimary,
        fontSize = with(LocalDensity.current) { 12.dp.toSp() },
        fontWeight = FontWeight.Bold,
        fontFeatureSettings = "tnum",
    )
    Canvas(modifier.fillMaxWidth().height(88.dp)) {
        val unit = 1.dp.toPx()
        val sized = plates.map { plate ->
            val ratio = plate / largest
            Size((if (ratio > 0.3) 22 else 18) * unit, (26 + sqrt(ratio) * 54).toFloat() * unit)
        }
        val gap = 2 * unit
        val shaftWidth = 96 * unit
        val collarWidth = 8 * unit
        val platesWidth = sized.sumOf { it.width.toDouble() }.toFloat() + gap * sized.size
        val sleeveWidth = max(96 * unit, platesWidth + gap)
        val left = (size.width - shaftWidth - collarWidth - sleeveWidth) / 2
        val middle = size.height / 2

        roundRect(EisenColors.MutedForeground, left, middle, shaftWidth, 8 * unit, leftRadius = 4 * unit, rightRadius = 0f)
        roundRect(EisenColors.MutedForeground, left + shaftWidth, middle, collarWidth, 22 * unit, leftRadius = 2 * unit, rightRadius = 2 * unit)
        val sleeveLeft = left + shaftWidth + collarWidth
        roundRect(EisenColors.Border, sleeveLeft, middle, sleeveWidth, 12 * unit, leftRadius = 0f, rightRadius = 4 * unit)

        var x = sleeveLeft + gap
        sized.forEachIndexed { index, plate ->
            val outline = roundRect(EisenColors.Primary, x, middle, plate.width, plate.height, leftRadius = 4 * unit, rightRadius = 4 * unit)
            val label = measurer.measure(formatNumber(plates[index]), labelStyle)
            val centre = Offset(x + plate.width / 2, middle)
            // Reads bottom to top, like the print on a real plate seen from the side. A label too long for its plate is cut off.
            clipPath(outline) {
                rotate(-90f, pivot = centre) {
                    drawText(label, topLeft = Offset(centre.x - label.size.width / 2f, centre.y - label.size.height / 2f))
                }
            }
            x += plate.width + gap
        }
    }
}

/** A rectangle centred on [middle], with its own corner radius on each end. Returns its outline. */
private fun DrawScope.roundRect(color: Color, left: Float, middle: Float, width: Float, height: Float, leftRadius: Float, rightRadius: Float): Path {
    val top = middle - height / 2
    val l = CornerRadius(leftRadius)
    val r = CornerRadius(rightRadius)
    val path = Path().apply {
        addRoundRect(RoundRect(left, top, left + width, top + height, topLeftCornerRadius = l, topRightCornerRadius = r, bottomRightCornerRadius = r, bottomLeftCornerRadius = l))
    }
    drawPath(path, color)
    return path
}

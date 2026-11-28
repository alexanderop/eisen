package io.github.alexanderop.eisen.feature.awards.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.alexanderop.eisen.core.ui.EisenColors
import io.github.alexanderop.eisen.feature.awards.Medal
import io.github.alexanderop.eisen.feature.awards.MedalGroup
import io.github.alexanderop.eisen.feature.awards.Rank
import io.github.alexanderop.eisen.feature.awards.Tier

/*
 * The shield of a level and the three medal shapes, drawn on a 64 × 64 grid as the prototype's SVGs are and scaled
 * to the size asked for. No pictures: paths and gradients only.
 */

private const val GRID = 64f

/** The light and the dark end of a metal's gradient. */
private class Metal(val light: Color, val dark: Color)

private fun metalOf(rank: Rank): Metal = when (rank) {
    Rank.TIN -> Metal(Color(0xFFD4D8DE), Color(0xFF6B7280))
    Rank.COPPER -> Metal(Color(0xFFF2A36B), Color(0xFF8F3F18))
    Rank.BRONZE -> Metal(Color(0xFFE3B27A), Color(0xFF7A4E22))
    Rank.IRON -> Metal(Color(0xFFB7C0C9), Color(0xFF2F343B))
    Rank.STEEL -> Metal(Color(0xFFE6EEF7), Color(0xFF4F6276))
    Rank.TITANIUM -> Metal(Color(0xFFEFEAFF), Color(0xFF6C5FA6))
}

private fun metalOf(tier: Tier): Metal = when (tier) {
    Tier.BRONZE -> Metal(Color(0xFFF0C08A), Color(0xFF8A5A2B))
    Tier.SILVER -> Metal(Color(0xFFF4F6F8), Color(0xFF7D8794))
    Tier.GOLD -> Metal(Color(0xFFFFF1A8), Color(0xFFB8860B))
    Tier.IRON -> Metal(Color(0xFFD9F57A), Color(0xFF3D4A12))
}

/** A medal not earned yet: dark grey, a lock where the mark goes. */
private val Locked = Metal(Color(0xFF3A3F47), Color(0xFF1D2026))
private val LockedEdge = Color(0xFF2B2F37)
private val LockedMark = Color(0xFF6A717C)
private val Ink = Color(0xFF0B0C0E)
private val MedalInk = Color(0xFF14110A)

private fun path(data: String): Path = PathParser().parsePathString(data).toPath()

private val ShieldOuter = path("M32 4 L56 12 V30 C56 45 45 55 32 60 C19 55 8 45 8 30 V12 Z")
private val ShieldInner = path("M32 10 L50 16 V30 C50 41 42 49 32 53 C22 49 14 41 14 30 V16 Z")
private val Ribbon = path("M20 4 L28 16 L36 16 L44 4")
private val HexOuter = path("M32 9 L53 21 L53 47 L32 59 L11 47 L11 21 Z")
private val HexInner = path("M32 16 L47 25 L47 43 L32 52 L17 43 L17 25 Z")
private val BannerOuter = path("M14 12 H50 V48 L32 58 L14 48 Z")
private val BannerInner = path("M19 17 H45 V45 L32 52 L19 45 Z")
private val Shackle = path("M28 34 V30.5 A4 4 0 0 1 36 30.5 V34")

private fun gradient(metal: Metal): Brush =
    Brush.linearGradient(listOf(metal.light, metal.dark), start = Offset.Zero, end = Offset(GRID, GRID))

/** Draws on the 64 grid, scaled to the canvas. */
private inline fun DrawScope.onGrid(block: DrawScope.() -> Unit) {
    val scale = size.minDimension / GRID
    withTransform({ scale(scale, scale, pivot = Offset.Zero) }) { block() }
}

/** [text] centred on x = 32 with its baseline at [baseline], both on the grid, [fontSize] grid units high. */
private fun DrawScope.mark(measurer: TextMeasurer, text: String, baseline: Float, fontSize: Float, color: Color) {
    val scale = size.minDimension / GRID
    val layout = measurer.measure(text, TextStyle(color = color, fontSize = (fontSize * scale).toSp(), fontWeight = FontWeight.Black))
    drawText(layout, topLeft = Offset(32f * scale - layout.size.width / 2f, baseline * scale - layout.firstBaseline))
}

/** The level's shield: the rank's metal with the level number on it. */
@Composable
fun ShieldEmblem(level: Int, rank: Rank, size: Dp, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    Canvas(modifier.size(size)) {
        onGrid {
            drawPath(ShieldOuter, gradient(metalOf(rank)))
            drawPath(ShieldOuter, Ink, style = Stroke(2f))
            drawPath(ShieldInner, Color.White.copy(alpha = 0.35f), style = Stroke(1.5f))
        }
        val fontSize = when {
            level > 99 -> 16f
            level > 9 -> 20f
            else -> 24f
        }
        mark(measurer, level.toString(), 40f, fontSize, Ink)
    }
}

/** A medal in its group's shape and its tier's metal, or grey with a lock while it is not earned. */
@Composable
fun MedalArt(medal: Medal, size: Dp, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    val earned = medal.earned
    val metal = if (earned) metalOf(medal.tier) else Locked
    val edge = if (earned) metal.dark else LockedEdge
    Canvas(modifier.size(size)) {
        val (outer, inner, innerAlpha) = when (medal.group) {
            MedalGroup.RECORDS -> Triple(null, null, 0.35f)
            MedalGroup.CONSISTENCY -> Triple(HexOuter, HexInner, 0.3f)
            MedalGroup.MILESTONES -> Triple(BannerOuter, BannerInner, 0.3f)
        }
        onGrid {
            if (outer == null || inner == null) {
                drawPath(Ribbon, if (earned) EisenColors.Primary else LockedEdge, style = Stroke(6f, join = StrokeJoin.Round))
                drawCircle(gradient(metal), radius = 22f, center = Offset(32f, 34f))
                drawCircle(edge, radius = 22f, center = Offset(32f, 34f), style = Stroke(3f))
                drawCircle(Color.White.copy(alpha = innerAlpha), radius = 16f, center = Offset(32f, 34f), style = Stroke(1.5f))
            } else {
                drawPath(outer, gradient(metal))
                drawPath(outer, edge, style = Stroke(3f, join = StrokeJoin.Round))
                drawPath(inner, Color.White.copy(alpha = innerAlpha), style = Stroke(1.5f))
            }
            if (!earned) {
                // A padlock where the mark goes.
                drawRoundRect(LockedMark, topLeft = Offset(26f, 33.5f), size = Size(12f, 9.5f), cornerRadius = CornerRadius(1.8f))
                drawPath(Shackle, LockedMark, style = Stroke(2.2f))
            }
        }
        if (earned) {
            val glyph = medal.glyph
            val fontSize = when {
                glyph.length > 3 -> 11f
                glyph.length > 2 -> 13f
                else -> 15f
            }
            mark(measurer, glyph, if (medal.group == MedalGroup.MILESTONES) 37f else 39f, fontSize, MedalInk)
        }
    }
}

/** A thin bar of XP in the app's lime, [fraction] full. */
@Composable
fun XpBar(fraction: Float, modifier: Modifier = Modifier, height: Dp = 8.dp) {
    val shape = RoundedCornerShape(height / 2)
    Box(modifier.fillMaxWidth().height(height).clip(shape).background(EisenColors.Muted)) {
        Box(
            Modifier.fillMaxHeight().fillMaxWidth(fraction.coerceIn(0f, 1f)).clip(shape)
                .background(Brush.horizontalGradient(listOf(Color(0xFF9BD11C), EisenColors.Primary))),
        )
    }
}

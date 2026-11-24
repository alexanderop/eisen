package io.github.alexanderop.eisen.core.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt

/*
 * Picking the reps of a set straight away, like a long press on a key of the phone's keyboard: hold a
 * set and its counts pop up over it, target first (5 4 3 2 1 0). Slide to one and let go to pick it;
 * lift without sliding and the row stays for a tap. A tap outside or Back closes it.
 *
 * The same row of numbers ([NumberRow]) corrects a set on the rest screen.
 */

/** How TalkBack says a count: "1 rep", "4 reps". */
fun repsText(reps: Int): String = if (reps == 1) "1 rep" else "$reps reps"

/** The counts a set with [target] reps can be logged at, the target first: 5 4 3 2 1 0. */
fun pickableReps(target: Int): List<Int> = (target downTo 0).toList()

/** Room between two numbers. */
private val KeyGap = 6.dp

/** A number of the picker: a thumb's width, more than the 48 dp a touch target needs. */
private val PickerKey = 52.dp
private val PickerPadding = 6.dp

/** Space between the picker and the set it belongs to, and the edges of the screen. */
private val PickerGap = 8.dp
private val PickerMargin = 8.dp

/** How far beside the numbers a sliding finger still points at the nearest one. */
private val SlideReach = 24.dp

private val KeyShape = RoundedCornerShape(14.dp)
private val PickerShape = RoundedCornerShape(20.dp)

/**
 * Counts to choose one from, as number keys in rows of [columns]: radio buttons that TalkBack reads
 * as "4 reps". [selected] is the count logged now, lime. [highlighted] is the number under a sliding
 * finger: lime while the finger is on it, and the logged one keeps a lime ring meanwhile.
 * [keyWidth] null shares the width out between the keys of a row; otherwise every key is that wide.
 * [onKeyPlaced] gets each key's bounds in the root, for a finger that slides onto them. Keys stand [gap]
 * apart; with no gap, [inset] keeps their faces apart while the whole key still takes the touch.
 */
@Composable
fun NumberRow(
    numbers: List<Int>,
    selected: Int?,
    onPick: (Int) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    columns: Int = numbers.size,
    keyWidth: Dp? = null,
    keyHeight: Dp = 48.dp,
    highlighted: Int? = null,
    onKeyPlaced: ((Int, Rect) -> Unit)? = null,
    gap: Dp = KeyGap,
    inset: Dp = 0.dp,
) {
    val perRow = columns.coerceIn(1, max(1, numbers.size))
    Column(
        modifier.selectableGroup().semantics { contentDescription = label },
        verticalArrangement = Arrangement.spacedBy(gap),
    ) {
        for (row in numbers.chunked(perRow)) {
            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                for (number in row) {
                    val lit = number == highlighted || (highlighted == null && number == selected)
                    val ringed = number == selected && !lit
                    // The whole key takes the touch; its face is drawn [inset] inside, so keys that touch still look apart.
                    Box(
                        (if (keyWidth == null) Modifier.weight(1f) else Modifier.width(keyWidth))
                            .heightIn(min = maxOf(keyHeight, 48.dp))
                            .then(if (onKeyPlaced != null) Modifier.onGloballyPositioned { onKeyPlaced(number, it.boundsInRoot()) } else Modifier)
                            .selectable(selected = number == selected, interactionSource = null, indication = null, role = Role.RadioButton) { onPick(number) }
                            .semantics { contentDescription = repsText(number) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            Modifier
                                .matchParentSize()
                                .padding(inset)
                                .clip(KeyShape)
                                .background(if (lit) EisenColors.Primary else EisenColors.Muted)
                                .then(if (ringed) Modifier.border(2.dp, EisenColors.Primary, KeyShape) else Modifier),
                        )
                        Text(
                            number.toString(),
                            color = if (lit) EisenColors.OnPrimary else EisenColors.Foreground,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            softWrap = false,
                        )
                    }
                }
                // A short last row keeps the grid of the full rows above it.
                if (keyWidth == null) repeat(perRow - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** A set whose reps are being picked: its [numbers], the count logged ([selected], null for an open set), and where it sits in the root. */
@Immutable
class RepsPick(
    val numbers: List<Int>,
    val selected: Int?,
    val anchor: Rect,
    val onPick: (Int) -> Unit,
)

/**
 * Which set's numbers are open, and the number under the finger while it slides from the set.
 * Presentation only: picking sends what a tap would, through [RepsPick.onPick].
 */
@Stable
class RepsPickerState {
    var pick: RepsPick? by mutableStateOf(null)
        private set

    /** The number the sliding finger points at, null when it points at none or does not slide. */
    var under: Int? by mutableStateOf(null)
        private set

    private val keys = mutableMapOf<Int, Rect>()
    private var reach = 0f

    fun open(pick: RepsPick) {
        keys.clear()
        under = null
        this.pick = pick
    }

    fun close() {
        pick = null
        under = null
        keys.clear()
    }

    /** The finger slid to [at] (root). True when it came onto another number, for the tick that goes with it. */
    fun slide(at: Offset): Boolean {
        if (pick == null) return false
        val number = numberAt(at)
        if (number == under) return false
        under = number
        return number != null
    }

    /**
     * The finger lifted at [at] (root). After a slide it picks the number there and closes; lifted
     * without sliding, or away from the numbers, the row stays open for a tap.
     */
    fun lift(at: Offset, slid: Boolean) {
        under = null
        if (!slid) return
        choose(numberAt(at) ?: return)
    }

    fun choose(number: Int) {
        val shown = pick ?: return
        close()
        shown.onPick(number)
    }

    internal fun place(number: Int, bounds: Rect, reach: Float) {
        keys[number] = bounds
        this.reach = reach
    }

    /** The key under [at], or the nearest one when the finger is just beside the numbers. */
    private fun numberAt(at: Offset): Int? {
        val nearest = keys.minByOrNull { (_, bounds) -> distance(bounds, at) } ?: return null
        return nearest.key.takeIf { distance(nearest.value, at) <= reach }
    }

    private fun distance(bounds: Rect, at: Offset): Float {
        val dx = maxOf(bounds.left - at.x, 0f, at.x - bounds.right)
        val dy = maxOf(bounds.top - at.y, 0f, at.y - bounds.bottom)
        return kotlin.math.sqrt(dx * dx + dy * dy)
    }
}

/**
 * The open numbers of [state], over the whole screen: right above their set, or below it when there
 * is no room above, and never past the left or right edge. The screen behind is dimmed; a tap on it
 * or Back closes the numbers without logging. Put it last in a Box that fills the screen, so it lies
 * on top. As many numbers to a row as fit, in rows of even length.
 */
@Composable
fun RepsPicker(state: RepsPickerState, modifier: Modifier = Modifier) {
    val pick = state.pick ?: return
    BackHandler { state.close() }
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    val insets = WindowInsets.safeDrawing
    val reach = with(density) { SlideReach.toPx() }
    var origin by remember { mutableStateOf(Offset.Zero) }
    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .onGloballyPositioned { origin = it.positionInRoot() }
            .background(Color.Black.copy(alpha = 0.45f))
            .clickable(interactionSource = null, indication = null, onClickLabel = "Close") { state.close() },
    ) {
        val sides = with(density) { (insets.getLeft(this, direction) + insets.getRight(this, direction)).toDp() }
        val room = maxWidth - sides - PickerMargin * 2 - PickerPadding * 2
        val fit = max(1, floor(((room + KeyGap) / (PickerKey + KeyGap)).toDouble()).toInt())
        val rows = (pick.numbers.size + fit - 1) / fit
        val columns = (pick.numbers.size + rows - 1) / rows
        Layout(
            content = {
                NumberRow(
                    numbers = pick.numbers,
                    selected = pick.selected,
                    onPick = state::choose,
                    label = "Reps",
                    modifier = Modifier
                        .shadow(12.dp, PickerShape)
                        .clip(PickerShape)
                        .background(EisenColors.Card)
                        .border(1.dp, EisenColors.Border, PickerShape)
                        // A touch between the numbers is not a touch outside: it keeps them open.
                        .pointerInput(Unit) {
                            awaitEachGesture {
                                awaitFirstDown(requireUnconsumed = false)
                                do {
                                    val event = awaitPointerEvent()
                                    event.changes.forEach { it.consume() }
                                } while (event.changes.any { it.pressed })
                            }
                        }
                        .padding(PickerPadding),
                    columns = columns,
                    keyWidth = PickerKey,
                    keyHeight = PickerKey,
                    highlighted = state.under,
                    onKeyPlaced = { number, bounds -> state.place(number, bounds, reach) },
                )
            },
        ) { measurables, constraints ->
            val row = measurables.single().measure(constraints.copy(minWidth = 0, minHeight = 0))
            val anchor = pick.anchor.translate(-origin)
            val gap = PickerGap.toPx()
            val margin = PickerMargin.toPx()
            val top = insets.getTop(this) + margin
            val bottom = constraints.maxHeight - insets.getBottom(this) - margin
            val left = insets.getLeft(this, layoutDirection) + margin
            val right = constraints.maxWidth - insets.getRight(this, layoutDirection) - margin
            val above = anchor.top - gap - row.height
            val below = anchor.bottom + gap
            // Never over the set being held: above it, else below it, else as close as the screen allows.
            val y = when {
                above >= top -> above
                below + row.height <= bottom -> below
                else -> above.coerceIn(top, maxOf(top, bottom - row.height))
            }
            val x = (anchor.center.x - row.width / 2f).coerceIn(left, maxOf(left, right - row.width))
            layout(constraints.maxWidth, constraints.maxHeight) { row.place(x.roundToInt(), y.roundToInt()) }
        }
    }
}

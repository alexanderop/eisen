package io.github.alexanderop.eisen.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val MarkShape = RoundedCornerShape(2.dp)

/** From this many rows a wheel is one that spins fast. */
private const val FAST_WHEEL_ROWS = 20

/**
 * A vertical scroll wheel like the picker of an alarm clock: three rows show, the middle one between
 * two lime marks is the value. It snaps to a row, ticks once per row passed, and a tap on the row
 * above or below moves there. [onSettle] gets the index of the new value once the wheel comes to
 * rest, not on every scrolled pixel. TalkBack reads it as an adjustable value named [label], spoken as
 * [spoken], and changes it one row at a time.
 */
@Composable
fun Wheel(
    labels: List<String>,
    selected: Int,
    onSettle: (Int) -> Unit,
    label: String,
    spoken: (Int) -> String,
    rowHeight: Dp,
    modifier: Modifier = Modifier,
) {
    // A blank row above the first value and below the last, so each value can reach the middle.
    val state = rememberLazyListState(initialFirstVisibleItemIndex = selected)
    val fling = rememberSnapFlingBehavior(state)
    val scope = rememberCoroutineScope()
    val haptics by rememberUpdatedState(rememberHaptics())
    val settle by rememberUpdatedState(onSettle)
    val current by rememberUpdatedState(selected)
    val rowPx by rememberUpdatedState(with(LocalDensity.current) { rowHeight.roundToPx() })
    val last = labels.lastIndex
    val lastIndex by rememberUpdatedState(last)
    // One state for the whole life of the wheel: the collectors below keep reading this one.
    val middle by remember(state) { derivedStateOf { middleOf(state, rowPx).coerceIn(0, maxOf(0, lastIndex)) } }

    LaunchedEffect(state) {
        snapshotFlow { state.isScrollInProgress }.collect { scrolling ->
            if (!scrolling && middle != current) settle(middle)
        }
    }
    // A long wheel (seconds, minutes) is flung through many rows at once: a lighter tick per row.
    val tick by rememberUpdatedState(if (labels.size >= FAST_WHEEL_ROWS) Haptic.FAST_STEP else Haptic.STEP)
    LaunchedEffect(state) {
        snapshotFlow { middle }.drop(1).collect { if (state.isScrollInProgress) haptics.perform(tick) }
    }
    // A value changed elsewhere, or the list around it, turns the wheel to it.
    LaunchedEffect(selected, labels.size) {
        if (!state.isScrollInProgress && middle != selected) state.scrollToItem(selected)
    }

    fun step(by: Int): Boolean {
        val next = current + by
        if (next !in 0..last) return false
        settle(next)
        return true
    }

    Box(
        modifier
            .height(rowHeight * 3)
            .clearAndSetSemantics {
                contentDescription = label
                stateDescription = spoken(middle)
                progressBarRangeInfo = ProgressBarRangeInfo(middle.toFloat(), 0f..maxOf(0, last).toFloat(), steps = maxOf(0, last - 1))
                setProgress { value ->
                    val index = value.roundToInt().coerceIn(0, maxOf(0, last))
                    if (index != current) settle(index)
                    true
                }
                customActions = listOf(
                    CustomAccessibilityAction("Increase $label") { step(1) },
                    CustomAccessibilityAction("Decrease $label") { step(-1) },
                )
            },
    ) {
        Box(
            Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .height(rowHeight)
                .clip(RoundedCornerShape(12.dp))
                .background(EisenColors.Muted),
        ) {
            Mark(Modifier.align(Alignment.CenterStart).padding(start = 10.dp))
            Mark(Modifier.align(Alignment.CenterEnd).padding(end = 10.dp))
        }
        LazyColumn(Modifier.fillMaxWidth().height(rowHeight * 3), state = state, flingBehavior = fling) {
            item { Spacer(Modifier.height(rowHeight)) }
            items(labels.size) { index ->
                val centre = index == middle
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(rowHeight)
                        .clickable(interactionSource = null, indication = null) { scope.launch { state.animateScrollToItem(index) } },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        labels[index],
                        color = if (centre) EisenColors.Foreground else EisenColors.MutedForeground,
                        fontSize = (rowHeight.value * if (centre) 0.66f else 0.5f).sp,
                        fontWeight = if (centre) FontWeight.ExtraBold else FontWeight.Bold,
                        maxLines = 1,
                    )
                }
            }
            item { Spacer(Modifier.height(rowHeight)) }
        }
    }
}

/** The value in the middle row: the blank row on top makes the first visible item the one in the middle. */
private fun middleOf(state: LazyListState, rowPx: Int): Int =
    state.firstVisibleItemIndex + if (rowPx > 0 && state.firstVisibleItemScrollOffset * 2 >= rowPx) 1 else 0

@Composable
private fun Mark(modifier: Modifier) {
    Box(modifier.size(width = 4.dp, height = 22.dp).clip(MarkShape).background(EisenColors.Primary))
}

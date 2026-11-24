package io.github.alexanderop.eisen.core.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.awaitVerticalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs

/*
 * Drag to reorder, built on Compose foundation only. Works for a LazyColumn and for a plain
 * Column, as long as every item has a stable key.
 *
 * The pieces:
 * - A state that knows the order: `rememberLazyReorderState(listState, keys, onMove)` or
 *   `rememberColumnReorderState(keys, onMove, scrollState)`. `keys` are the keys of the items
 *   that can be dragged, in the order they show. Other items in the same list (headers, buttons)
 *   are simply not in `keys`: they never move and nothing can be dropped on them.
 * - A wrapper around each draggable item: `ReorderableItem` inside a LazyColumn item,
 *   `ReorderableColumnItem` inside a Column. It lifts the dragged item (a little larger, with a
 *   shadow, drawn above the others), makes it follow the finger and moves the neighbours out of
 *   the way. Do not add `animateItem()` yourself; the wrapper does it.
 * - What starts a drag, on the item or anywhere inside it: `Modifier.longPressToReorder(state, key)`
 *   (hold, then drag) or `Modifier.reorderHandle(state, key)` (drag a visible handle at once).
 *   Neither fights scrolling: a long press is cancelled the moment the list scrolls, and a handle
 *   only takes vertical drags that start on it. Once a drag runs it takes every touch, so buttons
 *   inside the item do not fire when the finger lifts.
 *
 * onMove(from, to) gets indices into `keys` and must apply the move to the list right away
 * (send the Msg). In a LazyColumn it is called live, one place at a time (`to` is `from ± 1`),
 * each time the dragged item passes a neighbour, and the list's own item animation moves the
 * neighbour. In a Column it is called once, on drop, and `to` can be several places away; the
 * neighbours move aside with an offset while dragging. Either way, each call is one whole move.
 *
 * Near the top or bottom edge of a scrolling container the list scrolls by itself while
 * dragging. A Column needs `Modifier.reorderContainer(state)` on the scrolling viewport for this,
 * placed before `verticalScroll`.
 *
 * Haptics: a tap when the item is picked up and when it is dropped, a tick for every new place.
 *
 * A drag is a gesture; give every list the same moves without one (buttons, a menu, TalkBack
 * `customActions` such as "Move up" and "Move down").
 *
 * LazyColumn:
 *
 *     val listState = rememberLazyListState()
 *     val reorder = rememberLazyReorderState(listState, keys = rows.map { it.id }, onMove = { from, to -> dispatch(Msg.Routines.Move(from, to)) })
 *     LazyColumn(state = listState) {
 *         item(key = "header") { Header() }
 *         items(rows, key = { it.id }) { row ->            // the lazy key must be the same as in `keys`
 *             ReorderableItem(reorder, key = row.id, shape = RoundedCornerShape(16.dp)) { dragging ->
 *                 RowCard(row, Modifier.longPressToReorder(reorder, row.id))
 *             }
 *         }
 *     }
 *
 * Column:
 *
 *     val scroll = rememberScrollState()
 *     val reorder = rememberColumnReorderState(keys = slots.map { it.id }, onMove = { from, to -> … }, scrollState = scroll)
 *     Column(Modifier.reorderContainer(reorder).verticalScroll(scroll)) {
 *         for (slot in slots) key(slot.id) {
 *             ReorderableColumnItem(reorder, key = slot.id) { dragging ->
 *                 Row {
 *                     Icon(GripIcon, contentDescription = null, modifier = Modifier.reorderHandle(reorder, slot.id))
 *                     Text(slot.name)
 *                 }
 *             }
 *         }
 *     }
 */

/** How close to the edge of the viewport the finger has to be before the list scrolls by itself. */
private val AutoScrollEdge = 72.dp

/** Scrolling per frame with the finger right at the edge. Slower further in. */
private val AutoScrollSpeed = 14.dp

/** The finger must have moved this far before the edge scrolls, so picking up an item near it does not. */
private val AutoScrollAfter = 12.dp

private val LiftElevation = 8.dp
private const val LIFT_SCALE = 0.03f

private val Settle = spring<Float>(stiffness = Spring.StiffnessMediumLow)

private fun ClosedFloatingPointRange<Float>.center(): Float = (start + endInclusive) / 2f

private fun ClosedFloatingPointRange<Float>.size(): Float = endInclusive - start

/** What a reorderable list knows while an item is dragged. Made by `rememberLazyReorderState` or `rememberColumnReorderState`. */
@Stable
abstract class ReorderState internal constructor(
    private val scope: CoroutineScope,
    internal val haptics: Haptics,
    private val density: Density,
) {
    internal var keys: List<Any> = emptyList()
        set(value) {
            if (value == field) return
            field = value
            onKeysChanged()
        }
    internal var onMove: (from: Int, to: Int) -> Unit = { _, _ -> }
    internal var onDragStart: (key: Any) -> Unit = {}
    internal var onDragEnd: (key: Any) -> Unit = {}

    /** Where each item is laid out, without the offset this state draws it at. */
    internal val itemCoordinates = mutableMapOf<Any, LayoutCoordinates>()

    /** The key of the item under the finger, or null when nothing is dragged. */
    var draggingKey: Any? by mutableStateOf(null)
        private set

    /** The item gliding into its place after the finger lifted. */
    internal var droppingKey: Any? by mutableStateOf(null)
        private set
    internal val dropOffset = Animatable(0f)

    /** How far the finger moved since the pick-up, in pixels. */
    internal var dragDelta by mutableFloatStateOf(0f)
        private set

    /** Where the finger holds the item, from its top edge. */
    private var grab = 0f
    private var autoScroll: Job? = null

    val isDragging: Boolean get() = draggingKey != null

    fun isDragging(key: Any): Boolean = draggingKey == key

    internal fun isLifted(key: Any): Boolean = key == draggingKey || key == droppingKey

    /** Takes a snapshot of the layout at pick-up. False when the item cannot be dragged now. */
    internal abstract fun begin(key: Any): Boolean

    /** Checks whether the dragged item passed a neighbour, after the finger or the list moved. */
    internal abstract fun afterDrag()

    /** The offset the dragged item is drawn at, from its place in the layout. */
    internal abstract fun draggedOffset(key: Any): Float

    /** Where the dragged item is drawn, in the same space as [viewport]. */
    internal abstract fun draggedSpan(): ClosedFloatingPointRange<Float>?

    internal abstract fun viewport(): ClosedFloatingPointRange<Float>?

    internal abstract suspend fun scrollBy(pixels: Float): Float

    /** Glides the dropped item into its place, starting at [dropOffset], and applies what is left of the move. */
    internal abstract suspend fun settle(key: Any)

    /** Forgets the drag, also when the drop was interrupted. */
    internal open fun reset() {}

    internal open fun onKeysChanged() {}

    /** The offset an item is drawn at while it is lifted. */
    internal fun offsetOf(key: Any): Float = when (key) {
        draggingKey -> draggedOffset(key)
        droppingKey -> dropOffset.value
        else -> 0f
    }

    internal fun start(key: Any, fingerInRoot: Float): Boolean {
        if (draggingKey != null || droppingKey != null || key !in keys) return false
        if (!begin(key)) return false
        grab = itemCoordinates[key]?.takeIf { it.isAttached }?.let { fingerInRoot - it.positionInRoot().y } ?: 0f
        dragDelta = 0f
        draggingKey = key
        haptics.perform(Haptic.THRESHOLD)
        onDragStart(key)
        autoScroll = scope.launch { scrollAtEdges() }
        return true
    }

    internal fun drag(delta: Float) {
        if (draggingKey == null) return
        dragDelta += delta
        afterDrag()
    }

    internal fun stop() {
        val key = draggingKey ?: return
        autoScroll?.cancel()
        val offset = draggedOffset(key)
        droppingKey = key
        draggingKey = null
        haptics.perform(Haptic.TAP)
        // Undispatched, so the snap below lands before the next frame. Otherwise that frame draws
        // the dropped item at the old drop offset: back in its first place, or for a Column at the
        // place the previous drop ended, and it flickers.
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                dropOffset.snapTo(offset)
                settle(key)
            } finally {
                reset()
                droppingKey = null
                dragDelta = 0f
                onDragEnd(key)
            }
        }
    }

    private suspend fun scrollAtEdges() {
        val edge = with(density) { AutoScrollEdge.toPx() }
        val speed = with(density) { AutoScrollSpeed.toPx() }
        val after = with(density) { AutoScrollAfter.toPx() }
        while (draggingKey != null) {
            withFrameNanos { }
            if (abs(dragDelta) < after) continue
            val span = draggedSpan() ?: continue
            val view = viewport() ?: continue
            val finger = span.start + grab
            val step = when {
                finger > view.endInclusive - edge -> speed * ((finger - (view.endInclusive - edge)) / edge).coerceAtMost(1f)
                finger < view.start + edge -> -speed * (((view.start + edge) - finger) / edge).coerceAtMost(1f)
                else -> 0f
            }
            if (step != 0f && scrollBy(step) != 0f) afterDrag()
        }
    }
}

/**
 * Reordering in a LazyColumn. The list reorders live, so the lazy item animation moves the
 * neighbours; this state only keeps the dragged item under the finger.
 */
@Stable
class LazyReorderState internal constructor(
    private val listState: LazyListState,
    scope: CoroutineScope,
    haptics: Haptics,
    density: Density,
) : ReorderState(scope, haptics, density) {
    private var startTop = 0f
    private var lastOffset = 0f

    /** True from a move until the new order arrives, so one pass over a neighbour moves only once. */
    private var waiting = false

    override fun onKeysChanged() {
        waiting = false
    }

    private fun span(key: Any): ClosedFloatingPointRange<Float>? =
        listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == key }?.let { it.offset.toFloat()..(it.offset + it.size).toFloat() }

    override fun begin(key: Any): Boolean {
        val span = span(key) ?: return false
        startTop = span.start
        lastOffset = 0f
        waiting = false
        return true
    }

    override fun draggedOffset(key: Any): Float {
        // Gone from the layout for a moment (scrolled away): keep drawing it where it was.
        val span = span(key) ?: return lastOffset
        return (startTop + dragDelta - span.start).also { lastOffset = it }
    }

    override fun draggedSpan(): ClosedFloatingPointRange<Float>? {
        val key = draggingKey ?: return null
        val height = span(key)?.size() ?: return null
        val top = startTop + dragDelta
        return top..(top + height)
    }

    override fun viewport(): ClosedFloatingPointRange<Float> =
        listState.layoutInfo.let { it.viewportStartOffset.toFloat()..it.viewportEndOffset.toFloat() }

    override suspend fun scrollBy(pixels: Float): Float = listState.scrollBy(pixels)

    override fun afterDrag() {
        val key = draggingKey ?: return
        if (waiting) return
        val from = keys.indexOf(key)
        if (from == -1) return
        val center = draggedSpan()?.center() ?: return
        // Past the neighbour's middle, not just touching it: items of very different heights do not swap back and forth.
        val to = when {
            from + 1 < keys.size && span(keys[from + 1])?.let { center > it.center() } == true -> from + 1
            from > 0 && span(keys[from - 1])?.let { center < it.center() } == true -> from - 1
            else -> return
        }
        // The list keeps its first visible item in view by key. When that item is one of the two
        // that swap, it would scroll after it; holding the position by index keeps the list still.
        val first = listState.layoutInfo.visibleItemsInfo.firstOrNull()
        if (first != null && (first.key == key || first.key == keys[to])) {
            listState.requestScrollToItem(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset)
        }
        waiting = true
        haptics.perform(Haptic.STEP)
        onMove(from, to)
    }

    override suspend fun settle(key: Any) {
        dropOffset.animateTo(0f, Settle)
    }
}

/**
 * Reordering in a plain Column. The order changes once, on drop: while dragging, the neighbours
 * are drawn moved aside, so nothing is laid out again under the finger.
 */
@Stable
class ColumnReorderState internal constructor(
    private val scrollState: ScrollState?,
    scope: CoroutineScope,
    haptics: Haptics,
    density: Density,
) : ReorderState(scope, haptics, density) {
    internal var viewportCoordinates: LayoutCoordinates? = null

    /** Each item's place at pick-up, in root coordinates, in the order of `keys`. */
    private var spans: List<ClosedFloatingPointRange<Float>> = emptyList()
    private var from = -1
    private var scrollAtStart = 0

    /** Where the dragged item would land now. */
    private var target by mutableIntStateOf(-1)

    /** True while neighbours may be drawn moved aside. */
    internal var shifting by mutableStateOf(false)
        private set

    private val scrolled: Float get() = ((scrollState?.value ?: 0) - scrollAtStart).toFloat()

    override fun begin(key: Any): Boolean {
        val all = keys.map { item ->
            val coordinates = itemCoordinates[item]?.takeIf { it.isAttached } ?: return false
            val top = coordinates.positionInRoot().y
            top..(top + coordinates.size.height)
        }
        spans = all
        from = keys.indexOf(key)
        target = from
        scrollAtStart = scrollState?.value ?: 0
        shifting = true
        return true
    }

    // The finger stays put while the content scrolls under it, so the scrolled distance is added.
    override fun draggedOffset(key: Any): Float = dragDelta + scrolled

    override fun draggedSpan(): ClosedFloatingPointRange<Float>? =
        spans.getOrNull(from)?.let { (it.start + dragDelta)..(it.endInclusive + dragDelta) }

    override fun viewport(): ClosedFloatingPointRange<Float>? =
        viewportCoordinates?.takeIf { it.isAttached }?.boundsInRoot()?.let { it.top..it.bottom }

    override suspend fun scrollBy(pixels: Float): Float = scrollState?.scrollBy(pixels) ?: 0f

    override fun afterDrag() {
        val dragged = spans.getOrNull(from) ?: return
        val center = dragged.center() + dragDelta + scrolled
        var to = from
        for (i in from + 1 until spans.size) if (center > spans[i].center()) to = i else break
        if (to == from) for (i in from - 1 downTo 0) if (center < spans[i].center()) to = i else break
        if (to != target) {
            target = to
            haptics.perform(Haptic.STEP)
        }
    }

    private fun gap(): Float = when {
        from + 1 < spans.size -> spans[from + 1].start - spans[from].endInclusive
        from > 0 -> spans[from].start - spans[from - 1].endInclusive
        else -> 0f
    }

    /** How far an item that is not dragged is drawn moved aside, to make room. */
    internal fun shiftOf(key: Any): Float {
        if (!shifting || from !in spans.indices) return 0f
        val index = keys.indexOf(key)
        val step = spans[from].size() + gap()
        return when {
            target > from && index in (from + 1)..target -> -step
            target < from && index in target until from -> step
            else -> 0f
        }
    }

    override suspend fun settle(key: Any) {
        val slot = when {
            target > from -> spans[target].endInclusive - spans[from].endInclusive
            target < from -> spans[target].start - spans[from].start
            else -> 0f
        }
        dropOffset.animateTo(slot, Settle)
        val (moved, to) = from to target
        // In the same frame as the new order, so nothing is drawn twice moved.
        reset()
        if (moved != to && moved != -1 && to != -1) onMove(moved, to)
    }

    override fun reset() {
        shifting = false
        target = -1
        from = -1
    }
}

/** Remembers a [LazyReorderState]. See the top of `ui/Reorder.kt` for how the pieces fit. */
@Composable
fun rememberLazyReorderState(
    listState: LazyListState,
    keys: List<Any>,
    onMove: (from: Int, to: Int) -> Unit,
    onDragStart: (key: Any) -> Unit = {},
    onDragEnd: (key: Any) -> Unit = {},
): LazyReorderState {
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    val density = LocalDensity.current
    val state = remember(listState, scope, haptics, density) { LazyReorderState(listState, scope, haptics, density) }
    SideEffect {
        state.keys = keys
        state.onMove = onMove
        state.onDragStart = onDragStart
        state.onDragEnd = onDragEnd
    }
    return state
}

/** Remembers a [ColumnReorderState]. Pass the Column's [scrollState] for scrolling at the edges. */
@Composable
fun rememberColumnReorderState(
    keys: List<Any>,
    onMove: (from: Int, to: Int) -> Unit,
    scrollState: ScrollState? = null,
    onDragStart: (key: Any) -> Unit = {},
    onDragEnd: (key: Any) -> Unit = {},
): ColumnReorderState {
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    val density = LocalDensity.current
    val state = remember(scrollState, scope, haptics, density) { ColumnReorderState(scrollState, scope, haptics, density) }
    SideEffect {
        state.keys = keys
        state.onMove = onMove
        state.onDragStart = onDragStart
        state.onDragEnd = onDragEnd
    }
    return state
}

/** Marks the scrolling viewport of a reorderable Column. Put it before `verticalScroll`. */
fun Modifier.reorderContainer(state: ColumnReorderState): Modifier = onPlaced { state.viewportCoordinates = it }

/** One draggable item of a LazyColumn. [shape] is the item's outline, for the shadow while lifted. */
@Composable
fun LazyItemScope.ReorderableItem(
    state: LazyReorderState,
    key: Any,
    modifier: Modifier = Modifier,
    shape: Shape = RectangleShape,
    content: @Composable (dragging: Boolean) -> Unit,
) {
    // The lifted item places itself; the list's animation would pull it away from the finger.
    val placement = if (state.isLifted(key)) null else spring(stiffness = Spring.StiffnessMediumLow, visibilityThreshold = IntOffset.VisibilityThreshold)
    Box(modifier.animateItem(placementSpec = placement).lifted(state, key, shape) { state.offsetOf(key) }) {
        CompositionLocalProvider(LocalLongPressClaim provides remember { LongPressClaim() }) {
            content(state.isDragging(key))
        }
    }
}

/**
 * Lets an element inside a reorderable item keep its own long press, such as holding a set to pick its
 * reps: the element calls [claim] as the finger lands on it, and [longPressToReorder] leaves that touch
 * alone, so one hold does not open the element and lift the whole item at once. [ReorderableItem]
 * provides one per item as [LocalLongPressClaim].
 */
@Stable
class LongPressClaim internal constructor() {
    private var pointer: PointerId? = null

    fun claim(id: PointerId) {
        pointer = id
    }

    internal fun claimed(id: PointerId): Boolean = pointer == id
}

/** The [LongPressClaim] of the reorderable item around, null outside one. */
val LocalLongPressClaim = staticCompositionLocalOf<LongPressClaim?> { null }

/** One draggable item of a Column. Wrap it in `key(itemKey) { }` when the Column is built in a loop. */
@Composable
fun ReorderableColumnItem(
    state: ColumnReorderState,
    key: Any,
    modifier: Modifier = Modifier,
    shape: Shape = RectangleShape,
    content: @Composable (dragging: Boolean) -> Unit,
) {
    val shift = remember { Animatable(0f) }
    val goal = state.shiftOf(key)
    val shifting = state.shifting
    LaunchedEffect(goal, shifting) {
        if (shifting) shift.animateTo(goal, Settle) else shift.snapTo(0f)
    }
    Box(
        modifier.lifted(state, key, shape) {
            when {
                state.isLifted(key) -> state.offsetOf(key)
                state.shifting -> shift.value
                else -> 0f
            }
        },
    ) {
        content(state.isDragging(key))
    }
}

/** Records where the item is laid out and draws it lifted and moved while it is dragged. */
@Composable
private fun Modifier.lifted(state: ReorderState, key: Any, shape: Shape, offset: () -> Float): Modifier {
    val lift = animateFloatAsState(if (state.isDragging(key)) 1f else 0f, label = "lift")
    val holder = remember(state, key) { arrayOfNulls<LayoutCoordinates>(1) }
    DisposableEffect(state, key) {
        onDispose {
            // A new item with the same key may have registered already.
            if (state.itemCoordinates[key] === holder[0]) state.itemCoordinates.remove(key)
        }
    }
    return this
        // Before the offset below, so the recorded place is the one in the layout.
        .onPlaced {
            holder[0] = it
            state.itemCoordinates[key] = it
        }
        .zIndex(if (state.isLifted(key)) 1f else 0f)
        .graphicsLayer {
            translationY = offset()
            val scale = 1f + LIFT_SCALE * lift.value
            scaleX = scale
            scaleY = scale
            shadowElevation = LiftElevation.toPx() * lift.value
            this.shape = shape
            clip = false
        }
}

/** Hold the item, then drag it. Taps and scrolling inside it keep working. */
fun Modifier.longPressToReorder(state: ReorderState, key: Any, enabled: Boolean = true): Modifier = composed {
    val allowed by rememberUpdatedState(enabled)
    val holder = remember { arrayOfNulls<LayoutCoordinates>(1) }
    val claim = LocalLongPressClaim.current
    onPlaced { holder[0] = it }.pointerInput(state, key) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            // A child with a long press of its own (holding a set) took this touch as it landed.
            if (!allowed || claim?.claimed(down.id) == true) return@awaitEachGesture
            // Null when the finger lifted, or a scroll or swipe took the touch first.
            val press = awaitLongPressOrCancellation(down.id) ?: return@awaitEachGesture
            followDrag(state, key, press, holder)
        }
    }
}

/** Drag the item by this handle straight away. A tap on the handle does nothing. */
fun Modifier.reorderHandle(state: ReorderState, key: Any, enabled: Boolean = true): Modifier = composed {
    val allowed by rememberUpdatedState(enabled)
    val holder = remember { arrayOfNulls<LayoutCoordinates>(1) }
    onPlaced { holder[0] = it }.pointerInput(state, key) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            if (!allowed) return@awaitEachGesture
            val moved = awaitVerticalTouchSlopOrCancellation(down.id) { change, _ -> change.consume() } ?: return@awaitEachGesture
            followDrag(state, key, moved, holder)
        }
    }
}

/**
 * Feeds the finger's movement to [state] until it lifts. Positions are taken in root coordinates:
 * the item moves with the finger, so its own coordinates would hardly change.
 * Every touch is consumed before the item's children see it, so a button under the finger does
 * not fire on release and the list does not scroll.
 */
private suspend fun AwaitPointerEventScope.followDrag(
    state: ReorderState,
    key: Any,
    start: PointerInputChange,
    holder: Array<LayoutCoordinates?>,
) {
    val coordinates = holder[0]?.takeIf { it.isAttached } ?: return
    var last = coordinates.localToRoot(start.position).y
    if (!state.start(key, last)) return
    start.consume()
    try {
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val change = event.changes.firstOrNull { it.id == start.id } ?: break
            event.changes.forEach { it.consume() }
            if (!change.pressed) break
            val now = holder[0]?.takeIf { it.isAttached }?.localToRoot(change.position)?.y ?: continue
            state.drag(now - last)
            last = now
        }
    } finally {
        state.stop()
    }
}

package io.github.alexanderop.eisen.ui

import android.graphics.BitmapFactory
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.KeyboardActionHandler
import androidx.compose.foundation.text.input.TextFieldDecorator
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.maxLengthReject
import androidx.compose.foundation.text.input.then
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.alexanderop.eisen.domain.Equipment
import io.github.alexanderop.eisen.domain.TypedAffixes
import io.github.alexanderop.eisen.domain.WeightUnit
import io.github.alexanderop.eisen.domain.stepFor
import io.github.alexanderop.eisen.domain.typedWeightSnap
import io.github.alexanderop.eisen.domain.formatNumber
import io.github.alexanderop.eisen.domain.typedAffixes
import io.github.alexanderop.eisen.domain.typedValue
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * An icon from SVG path data on a 24×24 grid. `filled` paths are filled, the others are
 * drawn as 2px round strokes, which is how the Lucide icons of the PWA are made.
 */
fun pathIcon(name: String, vararg paths: String, filled: Boolean = false): ImageVector =
    ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
        for (data in paths) {
            val nodes = PathParser().parsePathString(data).toNodes()
            if (filled) addPath(nodes, fill = SolidColor(Color.Black))
            else addPath(
                nodes,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round,
                strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round,
            )
        }
    }.build()

/** Icons used on more than one screen. A screen keeps its own next to its code. */
object Icons {
    val Home = pathIcon("Home", "M3 11.5 12 4l9 7.5V20a1 1 0 0 1-1 1h-5v-6H9v6H4a1 1 0 0 1-1-1z", filled = true)
    val Workouts = pathIcon("Workouts", "M2 10h2v4H2zM5 7h3v10H5zM9 11h6v2H9zM16 7h3v10h-3zM20 10h2v4h-2z", filled = true)
    val History = pathIcon("History", "M5 4h14v3H5zM5 10.5h14v3H5zM5 17h14v3H5z", filled = true)
    val Progress = pathIcon("Progress", "M4 20V10h3.5v10zM10.25 20V4h3.5v16zM16.5 20v-7H20v7z", filled = true)
    val Timer = pathIcon(
        "Timer",
        "M12 5a8.5 8.5 0 1 1 0 17a8.5 8.5 0 1 1 0-17zM12 7.5a6 6 0 1 0 0 12a6 6 0 1 0 0-12z",
        "M9.5 1.5h5v2.2h-5z",
        "M11 9.5h2v4.5h-2z",
        filled = true,
    )
    val Settings = pathIcon("Settings", "M4 6h9v2H4zM17 6h3v2h-3zM13 4.5h3v5h-3zM4 16h3v2H4zM11 16h9v2h-9zM7 14.5h3v5H7z", filled = true)
    val Minus = pathIcon("Minus", "M5 12h14")
    val Plus = pathIcon("Plus", "M5 12h14", "M12 5v14")
    val Close = pathIcon("Close", "M18 6 6 18", "m6 6 12 12")
    val Search = pathIcon("Search", "m21 21-4.34-4.34", "M11 3a8 8 0 1 0 0 16 8 8 0 1 0 0-16z")
    // Lucide's hourglass.
    val Hourglass = pathIcon(
        "Hourglass",
        "M5 22h14",
        "M5 2h14",
        "M17 22v-4.172a2 2 0 0 0-.586-1.414L12 12l-4.414 4.414A2 2 0 0 0 7 17.828V22",
        "M7 2v4.172a2 2 0 0 0 .586 1.414L12 12l4.414-4.414A2 2 0 0 0 17 6.172V2",
    )
    val Back = pathIcon("Back", "m12 19-7-7 7-7", "M19 12H5")
    val ChevronRight = pathIcon("ChevronRight", "m9 18 6-6-6-6")
    val ChevronUp = pathIcon("ChevronUp", "m18 15-6-6-6 6")
    val ChevronDown = pathIcon("ChevronDown", "m6 9 6 6 6-6")
    val Check = pathIcon("Check", "M20 6 9 17l-5-5")
    val Trash = pathIcon("Trash", "M3 6h18", "M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6", "M8 6V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2")
    val Pencil = pathIcon("Pencil", "M21.174 6.812a1 1 0 0 0-3.986-3.987L3.842 16.174a2 2 0 0 0-.5.83l-1.321 4.352a.5.5 0 0 0 .623.622l4.353-1.32a2 2 0 0 0 .83-.497z", "m15 5 4 4")
}

private val images = HashMap<String, ImageBitmap?>()

/** The picture of an exercise from the shared assets. Null when it has none. */
@Composable
fun exerciseBitmap(image: String?): ImageBitmap? {
    if (image == null) return null
    val context = LocalContext.current
    return remember(image) {
        images.getOrPut(image) {
            runCatching { context.assets.open("exercises/$image.webp").use { BitmapFactory.decodeStream(it)?.asImageBitmap() } }.getOrNull()
        }
    }
}

/**
 * A small square picture of an exercise for lists. The images are dark equipment on a
 * transparent background, so they sit on a pale tile. Without an image it shows the first letter.
 */
@Composable
fun ExerciseTile(image: String?, name: String, modifier: Modifier = Modifier, size: Dp = 44.dp) {
    val bitmap = exerciseBitmap(image)
    Box(
        modifier.size(size).clip(RoundedCornerShape(10.dp)).background(if (bitmap != null) EisenColors.Tile else EisenColors.Muted),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(bitmap, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.padding(3.dp))
        } else {
            Text(name.take(1).uppercase(), color = EisenColors.MutedForeground, fontWeight = FontWeight.Bold, fontSize = (size.value * 0.4f).sp)
        }
    }
}

/** A wide picture of an exercise as the header of a card. Draws nothing without an image. */
@Composable
fun ExerciseBanner(image: String?, modifier: Modifier = Modifier) {
    val bitmap = exerciseBitmap(image) ?: return
    Box(
        modifier.fillMaxWidth().aspectRatio(16f / 7f).clip(RoundedCornerShape(12.dp)).background(EisenColors.Tile),
        contentAlignment = Alignment.Center,
    ) {
        Image(bitmap, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.padding(8.dp))
    }
}

/**
 * Changes a number with thumb-sized buttons: weights, sets, reps, seconds. Like the number
 * controls of other apps it also takes a typed value (tap the number), repeats while + or − is
 * held, and moves step by step when the number is dragged sideways.
 *
 * - [snap]: the grid a typed number is rounded to. The buttons always move by [step].
 * - [large]: the big number of the settings sheets.
 * - [typedSuffix]: shown after the typed number when [format] does not show the plain number,
 *   such as a time "1:30" that is typed in seconds.
 * - [autoFocus]: starts with the keyboard open. [onDone] gets the final value after the keyboard's Done key.
 */
@Composable
fun Stepper(
    value: Double,
    onValueChange: (Double) -> Unit,
    step: Double,
    label: String,
    modifier: Modifier = Modifier,
    min: Double = 0.0,
    max: Double = Double.POSITIVE_INFINITY,
    format: (Double) -> String = { formatNumber(it) },
    snap: Double = step,
    large: Boolean = false,
    typedSuffix: String? = null,
    autoFocus: Boolean = false,
    onDone: ((Double) -> Unit)? = null,
) = NumberStepper(value, onValueChange, step, label, modifier, min, max, format, snap, large, typedSuffix, autoFocus, onDone, whole = false)

/** A stepper for whole numbers: sets, reps, seconds. */
@Composable
fun IntStepper(
    value: Int,
    onValueChange: (Int) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    step: Int = 1,
    min: Int = 0,
    max: Int = Int.MAX_VALUE,
    format: (Int) -> String = { it.toString() },
    snap: Int = step,
    large: Boolean = false,
    typedSuffix: String? = null,
    autoFocus: Boolean = false,
    onDone: ((Int) -> Unit)? = null,
) = NumberStepper(
    value = value.toDouble(),
    onValueChange = { onValueChange(it.roundToInt()) },
    step = step.toDouble(),
    label = label,
    modifier = modifier,
    min = min.toDouble(),
    max = max.toDouble(),
    format = { format(it.roundToInt()) },
    snap = snap.toDouble(),
    large = large,
    typedSuffix = typedSuffix,
    autoFocus = autoFocus,
    onDone = onDone?.let { done -> { done(it.roundToInt()) } },
    whole = true,
)

const val ADDED_WEIGHT_HINT = "For a dip belt or a weight vest. Counts on top of your bodyweight."

/** The weight added to a bodyweight exercise. 0 is "None": the lifter adds nothing by default. */
@Composable
fun AddedWeightStepper(
    value: Double,
    onValueChange: (Double) -> Unit,
    unit: WeightUnit,
    modifier: Modifier = Modifier,
    label: String = "added weight",
    large: Boolean = false,
) =
    Stepper(
        value = value,
        onValueChange = onValueChange,
        step = stepFor(Equipment.BODYWEIGHT, unit),
        label = label,
        modifier = modifier,
        min = 0.0,
        format = { fmtAdded(it, unit) },
        snap = typedWeightSnap(Equipment.BODYWEIGHT, unit),
        large = large,
    )

private const val FIRST_REPEAT_MS = 400L
private const val SECOND_REPEAT_MS = 250L
private const val FASTEST_REPEAT_MS = 60L
private const val MAX_TYPED_LENGTH = 8
private val ScrubDistance = 24.dp
private val NumberShape = RoundedCornerShape(12.dp)
private val SmallNumber = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
private val LargeNumber = TextStyle(fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.68).sp, textAlign = TextAlign.Center)

/** The value as last sent, so steps that come faster than the next recomposition still add up. */
private class Latest(var value: Double)

private fun typedText(value: Double, whole: Boolean): String = if (whole) value.roundToLong().toString() else formatNumber(value)

@Composable
private fun NumberStepper(
    value: Double,
    onValueChange: (Double) -> Unit,
    step: Double,
    label: String,
    modifier: Modifier,
    min: Double,
    max: Double,
    format: (Double) -> String,
    snap: Double,
    large: Boolean,
    typedSuffix: String?,
    autoFocus: Boolean,
    onDone: ((Double) -> Unit)?,
    whole: Boolean,
) {
    val haptics = rememberHaptics()
    val latest = remember { Latest(value) }
    latest.value = value
    val change by rememberUpdatedState(onValueChange)
    var editing by remember { mutableStateOf(autoFocus) }
    // The number before typing started, put back when the typed text is not a number.
    var before by remember { mutableDoubleStateOf(value) }
    var text by remember { mutableStateOf(typedText(value, whole)) }

    fun send(next: Double) {
        latest.value = next
        change(next)
    }

    // Every valid keystroke is sent right away. Tapping "Save" does not take the focus from the
    // field, so a value kept back until the keyboard closes would be lost on the way.
    fun type(typed: String) {
        text = typed
        val next = typedValue(typed, min, max, snap) ?: return
        if (next != latest.value) send(next)
    }

    fun finishTyping() {
        if (!editing) return
        editing = false
        if (typedValue(text, min, max, snap) == null && latest.value != before) send(before)
    }

    fun startTyping() {
        before = latest.value
        text = typedText(latest.value, whole)
        editing = true
    }

    /** One step from the latest value. False at the limit, so a held button stops. */
    fun stepBy(direction: Int): Boolean {
        finishTyping()
        val next = (latest.value + direction * step).coerceIn(min, max)
        if (next == latest.value) return false
        send(next)
        haptics.perform(Haptic.STEP)
        return true
    }
    val stepNow by rememberUpdatedState<(Int) -> Boolean> { stepBy(it) }

    val shown = format(value)
    // "+5kg" is typed as 5 between "+" and "kg". A value shown as a word ("None") borrows them from one step away.
    val probe = if (value + step <= max) value + step else value - step
    val (prefix, suffix) = if (typedSuffix != null) TypedAffixes("", typedSuffix)
    else typedAffixes(value, probe, format) { typedText(it, whole) }
    val style = if (large) LargeNumber else SmallNumber
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl

    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        RepeatButton(Icons.Minus, "Decrease $label", enabled = value > min) { stepNow(-1) }
        Box(
            Modifier
                .widthIn(min = if (large) 132.dp else 84.dp)
                .heightIn(min = 48.dp)
                .clip(NumberShape)
                .then(
                    if (editing) {
                        Modifier.background(EisenColors.Muted)
                    } else {
                        Modifier
                            .semantics {
                                contentDescription = "$label $shown"
                                customActions = listOf(
                                    CustomAccessibilityAction("Increase $label") { stepNow(1) },
                                    CustomAccessibilityAction("Decrease $label") { stepNow(-1) },
                                )
                            }
                            .clickable(role = Role.Button, onClickLabel = "Edit value") { startTyping() }
                            // Sideways only, so a vertical drag still scrolls the page or pulls the sheet down.
                            .pointerInput(rtl) {
                                val distance = ScrubDistance.toPx()
                                var travelled = 0f
                                detectHorizontalDragGestures(onDragStart = { travelled = 0f }) { change, dx ->
                                    change.consume()
                                    travelled += if (rtl) -dx else dx
                                    while (abs(travelled) >= distance) {
                                        val direction = if (travelled > 0) 1 else -1
                                        travelled -= direction * distance
                                        stepNow(direction)
                                    }
                                }
                            }
                    },
                )
                .padding(horizontal = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (editing) {
                NumberField(
                    initial = text,
                    prefix = prefix,
                    suffix = suffix,
                    style = style,
                    label = label,
                    whole = whole,
                    allowMinus = min < 0,
                    onType = ::type,
                    onFinish = ::finishTyping,
                    onDone = { typed ->
                        type(typed)
                        finishTyping()
                        onDone?.invoke(latest.value)
                    },
                )
            } else {
                Text(shown, style = style, maxLines = 1)
            }
        }
        RepeatButton(Icons.Plus, "Increase $label", enabled = value < max) { stepNow(1) }
    }
}

/** The number as a text field, with the whole text selected so typing replaces it. */
@Composable
private fun NumberField(
    initial: String,
    prefix: String,
    suffix: String,
    style: TextStyle,
    label: String,
    whole: Boolean,
    allowMinus: Boolean,
    onType: (String) -> Unit,
    onFinish: () -> Unit,
    onDone: (String) -> Unit,
) {
    val state = remember { TextFieldState(initial, TextRange(0, initial.length)) }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val type by rememberUpdatedState(onType)
    val finish by rememberUpdatedState(onFinish)
    val done by rememberUpdatedState(onDone)

    LaunchedEffect(Unit) {
        focus.requestFocus()
        keyboard?.show()
    }
    LaunchedEffect(state) { snapshotFlow { state.text.toString() }.collect { type(it) } }
    // Back closes the keyboard but leaves the field focused, so a closed keyboard ends the typing too.
    val keyboardOpen = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    var keyboardSeen by remember { mutableStateOf(false) }
    LaunchedEffect(keyboardOpen) {
        if (keyboardOpen) keyboardSeen = true else if (keyboardSeen) finish()
    }
    // A sheet closed while typing keeps the typed number, or puts back the old one for garbage.
    DisposableEffect(Unit) { onDispose { finish() } }
    var hadFocus by remember { mutableStateOf(false) }

    val content = LocalContentColor.current
    val affix = style.copy(color = EisenColors.MutedForeground)
    BasicTextField(
        state = state,
        modifier = Modifier
            .focusRequester(focus)
            .onFocusChanged { if (it.isFocused) hadFocus = true else if (hadFocus) finish() }
            .semantics { contentDescription = label },
        textStyle = style.copy(color = content),
        keyboardOptions = KeyboardOptions(
            keyboardType = if (whole) KeyboardType.Number else KeyboardType.Decimal,
            imeAction = ImeAction.Done,
            autoCorrectEnabled = false,
        ),
        onKeyboardAction = KeyboardActionHandler {
            keyboard?.hide()
            // The text as it is now: the last keystroke may not have reached onType yet.
            done(state.text.toString())
        },
        lineLimits = TextFieldLineLimits.SingleLine,
        inputTransformation = InputTransformation.maxLengthReject(MAX_TYPED_LENGTH).then(numberCharacters(whole, allowMinus)),
        cursorBrush = SolidColor(EisenColors.Primary),
        decorator = TextFieldDecorator { inner ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (prefix.isNotEmpty()) Text(prefix, style = affix, maxLines = 1)
                inner()
                if (suffix.isNotEmpty()) Text(suffix, style = affix, maxLines = 1)
            }
        },
    )
}

/** Keeps out what cannot be part of a number, such as letters from a pasted text or a hardware keyboard. */
private fun numberCharacters(whole: Boolean, allowMinus: Boolean) = InputTransformation {
    val fits = asCharSequence().all { char ->
        char in '0'..'9' || (!whole && (char == '.' || char == ',')) || (allowMinus && char == '-')
    }
    if (!fits) revertAllChanges()
}

/** Set while a held button is repeating, so letting go does not add the step of a tap on top. */
private class Hold(var repeated: Boolean = false)

/**
 * A round + or − button. A tap is one step. Held, it repeats after [FIRST_REPEAT_MS], faster and
 * faster down to [FASTEST_REPEAT_MS], until the finger lifts, slides away or the limit is reached.
 */
@Composable
private fun RepeatButton(icon: ImageVector, description: String, enabled: Boolean, onStep: () -> Boolean) {
    val colors = IconButtonDefaults.filledTonalIconButtonColors()
    val step by rememberUpdatedState(onStep)
    val hold = remember { Hold() }
    Box(
        Modifier
            .size(52.dp)
            // Watches the press next to the click below, which keeps tap, keyboard and TalkBack working as usual.
            .pointerInput(Unit) {
                coroutineScope {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        hold.repeated = false
                        val repeat = launch {
                            delay(FIRST_REPEAT_MS)
                            var gap = SECOND_REPEAT_MS
                            while (step()) {
                                hold.repeated = true
                                delay(gap)
                                gap = (gap * 4 / 5).coerceAtLeast(FASTEST_REPEAT_MS)
                            }
                        }
                        waitForUpOrCancellation()
                        repeat.cancel()
                    }
                }
            }
            .clip(CircleShape)
            .background(if (enabled) colors.containerColor else colors.disabledContainerColor)
            .clickable(enabled = enabled, role = Role.Button, interactionSource = null, indication = ripple()) {
                if (hold.repeated) hold.repeated = false else step()
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, tint = if (enabled) colors.contentColor else colors.disabledContentColor)
    }
}

/**
 * Bottom padding for the part of the keyboard that covers this layout. A tab sits above the bottom
 * bar, which the keyboard covers as well, so plain imePadding() would leave a gap of the bar's height.
 */
@Composable
fun Modifier.keyboardPadding(): Modifier {
    val density = LocalDensity.current
    val keyboard = WindowInsets.ime.getBottom(density)
    var below by remember { mutableIntStateOf(0) }
    return onGloballyPositioned { layout ->
        val window = layout.findRootCoordinates().size.height
        below = (window - layout.positionInRoot().y - layout.size.height).roundToInt().coerceAtLeast(0)
    }.padding(bottom = with(density) { (keyboard - below).coerceAtLeast(0).toDp() })
}

enum class SetState { EMPTY, DONE, MISSED }

/**
 * One set of an exercise. Lime when all reps were done, an amber ring when reps were missed.
 * [badge] sits on its top-right edge: the reps in reserve of a rated set.
 */
@Composable
fun SetButton(text: String, state: SetState, onClick: () -> Unit, modifier: Modifier = Modifier, description: String = text, badge: String? = null) {
    val background by animateColorAsState(
        when (state) {
            SetState.DONE -> EisenColors.Primary
            SetState.MISSED -> Color.Transparent
            SetState.EMPTY -> EisenColors.Muted
        },
        label = "set",
    )
    val content = when (state) {
        SetState.DONE -> EisenColors.OnPrimary
        SetState.MISSED -> EisenColors.Warn
        SetState.EMPTY -> EisenColors.MutedForeground
    }
    Box(modifier.widthIn(max = 68.dp).fillMaxWidth().aspectRatio(1f)) {
        Box(
            Modifier
                .matchParentSize()
                .clip(CircleShape)
                .background(background)
                .then(if (state == SetState.MISSED) Modifier.border(2.dp, EisenColors.Warn, CircleShape) else Modifier)
                .clickable(role = Role.Button, onClickLabel = description, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            // A hold such as "10:00" is longer than any count of reps.
            Text(text, color = content, fontWeight = FontWeight.Bold, fontSize = if (text.length > 4) 15.sp else 20.sp, style = MaterialTheme.typography.titleLarge, maxLines = 1)
        }
        if (badge != null) {
            // Read as part of the description, not on its own.
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 4.dp, y = (-2).dp)
                    .clearAndSetSemantics { }
                    .heightIn(min = 20.dp)
                    .widthIn(min = 20.dp)
                    .clip(CircleShape)
                    .background(EisenColors.Background)
                    .border(1.5.dp, EisenColors.Primary, CircleShape)
                    .padding(horizontal = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(badge, color = EisenColors.Primary, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 11.sp, maxLines = 1)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
private val FULL_SHEET = setOf(SheetValue.Hidden, SheetValue.Expanded)

/**
 * The state of the app's bottom sheets: they open all the way and close all the way, never half.
 * What Material's deprecated `rememberModalBottomSheetState(skipPartiallyExpanded = true)` did: without
 * the half-open stop, the new flag about that stop changes nothing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun rememberFullSheetState(): SheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden, enabledValues = FULL_SHEET)

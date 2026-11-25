package io.github.alexanderop.eisen.core

import java.math.BigDecimal
import java.math.RoundingMode

/** One number with an optional sign. Both "." and "," are a decimal point: a German keyboard only offers the comma. */
private val TYPED_NUMBER = Regex("[+-]?(\\d+([.,]\\d*)?|[.,]\\d+)")

/** A typed number as an exact decimal, or null when the text is empty or not one number. */
fun parseTypedNumber(text: String): BigDecimal? {
    val clean = text.trim()
    if (!TYPED_NUMBER.matches(clean)) return null
    return clean.replace(',', '.').toBigDecimal()
}

/**
 * Rounds [value] to the nearest multiple of [snap], halves going up. The maths runs on decimals,
 * so 0.1 + 0.2 on a 0.1 grid is 0.3 and not 0.30000000000000004.
 */
fun snapTo(value: Double, snap: Double): Double = snapDecimal(BigDecimal.valueOf(value), snap).toDouble()

private fun snapDecimal(value: BigDecimal, snap: Double): BigDecimal {
    if (snap <= 0.0) return value
    val grid = BigDecimal.valueOf(snap)
    return value.divide(grid, 0, RoundingMode.HALF_UP).multiply(grid)
}

/**
 * What a typed [text] stands for: put on the [snap] grid, then kept within [min]..[max].
 * Null for empty or garbage text, so the caller keeps the value it had.
 */
fun typedValue(text: String, min: Double, max: Double, snap: Double): Double? {
    val number = parseTypedNumber(text) ?: return null
    return snapDecimal(number, snap).toDouble().coerceIn(min, max)
}

/**
 * The finest working weight a typed number keeps. Plate-loaded equipment changes by the smallest
 * plate, which is also where a 1.25kg increment leads. Dumbbells and stacks follow the smallest increment.
 */
fun typedWeightSnap(equipment: Equipment, unit: WeightUnit): Double =
    if (isPlateLoaded(equipment)) unitDefaults(unit).plates.last() else incrementStep(unit)

/** Increments in kg include 1.25, which the 0.5 grid of the buttons cannot hold. */
fun typedIncrementSnap(unit: WeightUnit): Double = if (unit == WeightUnit.KG) 0.25 else incrementStep(unit)

/** The text shown before and after a typed number, such as "+" and "kg" around the 5 of "+5kg". */
data class TypedAffixes(val prefix: String, val suffix: String)

/**
 * The text around the number in the formatted [value]. A value the format writes as a word ("None"
 * for 0) has no number to find, so the text around [probe], a value the format writes as a number,
 * is used instead. That way the unit stays next to the field while 0 is typed.
 */
fun typedAffixes(value: Double, probe: Double, format: (Double) -> String, plain: (Double) -> String): TypedAffixes {
    for (candidate in listOf(value, probe)) {
        val shown = format(candidate)
        val number = plain(candidate)
        val at = shown.indexOf(number)
        if (at >= 0) return TypedAffixes(shown.substring(0, at), shown.substring(at + number.length))
    }
    return TypedAffixes("", "")
}

/*
 * A number of a stepper being shown or typed over, as one value. Typing ends from several places (the
 * keyboard's Done, the keyboard closing, the field losing focus or leaving the screen, a step of the
 * buttons); all of them send [NumberEntryEvent.Finish] and [numberEntryAfter] decides what it does.
 *
 *              Start (a tap on the number)
 *   ┌─────────┐ ─────────────────────────▶ ┌──────────────────────┐  Type(text): a number in
 *   │ Showing │                            │ Typing(before, text) │  the limits is sent at once
 *   └─────────┘ ◀───────────────────────── └──────────────────────┘
 *        Finish: the text is no number, so the number from before typing is sent back
 */
sealed interface NumberEntry {
    data object Showing : NumberEntry

    /** [before]: the number when typing started, put back when [text] is no number. [text]: as typed. */
    data class Typing(val before: Double, val text: String) : NumberEntry
}

sealed interface NumberEntryEvent {
    /** Typing starts over the number [latest], shown as [text]. */
    data class Start(val latest: Double, val text: String) : NumberEntryEvent

    /** The field holds [text] now. */
    data class Type(val text: String) : NumberEntryEvent

    data object Finish : NumberEntryEvent
}

/** The entry after an event, and the number to [send] (null: none). [latest] is the number as last sent. */
data class NumberEntryStep(val entry: NumberEntry, val send: Double? = null)

fun numberEntryAfter(entry: NumberEntry, event: NumberEntryEvent, latest: Double, min: Double, max: Double, snap: Double): NumberEntryStep =
    when (entry) {
        NumberEntry.Showing -> when (event) {
            is NumberEntryEvent.Start -> NumberEntryStep(NumberEntry.Typing(event.latest, event.text))
            // A keystroke or a finish that comes after typing ended changes nothing.
            is NumberEntryEvent.Type, NumberEntryEvent.Finish -> NumberEntryStep(entry)
        }
        is NumberEntry.Typing -> when (event) {
            is NumberEntryEvent.Start -> NumberEntryStep(entry)
            is NumberEntryEvent.Type -> NumberEntryStep(entry.copy(text = event.text), typedValue(event.text, min, max, snap)?.takeIf { it != latest })
            NumberEntryEvent.Finish ->
                NumberEntryStep(NumberEntry.Showing, entry.before.takeIf { typedValue(entry.text, min, max, snap) == null && latest != it })
        }
    }

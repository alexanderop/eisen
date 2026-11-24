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

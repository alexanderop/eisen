package io.github.alexanderop.eisen.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.math.BigDecimal
import java.math.RoundingMode

@Serializable
enum class WeightUnit(val label: String) {
    @SerialName("kg") KG("kg"),
    @SerialName("lb") LB("lb"),
}

private const val LB_PER_KG = 2.20462

fun roundTo(value: Double, step: Double): Double = Math.round(value / step) * step

fun convert(value: Double, from: WeightUnit, to: WeightUnit): Double = when {
    from == to -> value
    from == WeightUnit.KG -> value * LB_PER_KG
    else -> value / LB_PER_KG
}

/** At most two decimals, none when they are zero: 80, 82.5, 1.25. */
fun formatNumber(value: Double): String {
    val rounded = BigDecimal(value).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros()
    return if (rounded.signum() == 0) "0" else rounded.toPlainString()
}

fun formatWeight(value: Double, unit: WeightUnit): String = formatNumber(value) + unit.label

/** A hold as text: "45s" under a minute, "1:30" from a minute on. */
fun holdText(seconds: Int): String =
    if (seconds < 60) "${seconds}s" else "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"

class UnitDefaults(
    val bar: Double,
    /** Smallest jump the bar can make: two of the smallest plates. */
    val step: Double,
    val plates: List<Double>,
)

private val KG_DEFAULTS = UnitDefaults(20.0, 2.5, listOf(20.0, 15.0, 10.0, 5.0, 2.5, 1.25))
private val LB_DEFAULTS = UnitDefaults(45.0, 5.0, listOf(45.0, 35.0, 25.0, 10.0, 5.0, 2.5))

fun unitDefaults(unit: WeightUnit): UnitDefaults = if (unit == WeightUnit.KG) KG_DEFAULTS else LB_DEFAULTS

/**
 * Smallest weight change: plate pairs on a bar, finer for dumbbells and weight stacks.
 * Weight added to a bodyweight exercise is a dumbbell or a plate on a belt, so it moves like a dumbbell.
 */
fun stepFor(equipment: Equipment, unit: WeightUnit): Double =
    if (equipment == Equipment.WEIGHT || equipment == Equipment.BODYWEIGHT) (if (unit == WeightUnit.KG) 1.0 else 2.5)
    else unitDefaults(unit).step

fun incrementStep(unit: WeightUnit): Double = if (unit == WeightUnit.KG) 0.5 else 2.5

fun isPlateLoaded(equipment: Equipment): Boolean = equipment == Equipment.BARBELL || equipment == Equipment.MACHINE

/** How heavy a log is, as text: "80kg", "BW", or "BW + 10kg" with weight added to a bodyweight exercise. */
fun loadText(bodyweight: Boolean, weight: Double, unit: WeightUnit, label: String = "BW"): String = when {
    !bodyweight -> formatWeight(weight, unit)
    weight > 0 -> "$label + ${formatWeight(weight, unit)}"
    else -> label
}

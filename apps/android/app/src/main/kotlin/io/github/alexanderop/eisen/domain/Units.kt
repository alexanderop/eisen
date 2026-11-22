package io.github.alexanderop.eisen.domain

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

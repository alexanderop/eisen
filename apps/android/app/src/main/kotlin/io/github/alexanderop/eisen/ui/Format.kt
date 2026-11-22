package io.github.alexanderop.eisen.ui

import io.github.alexanderop.eisen.data.Store
import io.github.alexanderop.eisen.domain.Equipment
import io.github.alexanderop.eisen.domain.PlateLoad
import io.github.alexanderop.eisen.domain.WeightUnit
import io.github.alexanderop.eisen.domain.emptyWeight
import io.github.alexanderop.eisen.domain.formatNumber
import io.github.alexanderop.eisen.domain.formatWeight
import io.github.alexanderop.eisen.domain.loadText
import io.github.alexanderop.eisen.domain.platesPerSide
import io.github.alexanderop.eisen.domain.unitDefaults
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** "82.5kg" in the lifter's unit, or in the unit a finished workout was logged in. */
fun fmt(value: Double, unit: WeightUnit = Store.state.settings.unit): String = formatWeight(value, unit)

/** How heavy an exercise is: "80kg", "BW", or "BW + 10kg" with weight added to a bodyweight exercise. */
fun fmtLoad(equipment: Equipment, weight: Double, unit: WeightUnit = Store.state.settings.unit): String =
    loadText(equipment == Equipment.BODYWEIGHT, weight, unit)

/** "None" or "+10kg": the added weight of a bodyweight exercise, as its stepper shows it. */
fun fmtAdded(weight: Double, unit: WeightUnit = Store.state.settings.unit): String = if (weight == 0.0) "None" else "+${fmt(weight, unit)}"

private fun format(pattern: String, timestamp: Long): String =
    DateTimeFormatter.ofPattern(pattern, Locale.getDefault()).format(Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()))

/** "Thu, Nov 19" */
fun formatDate(timestamp: Long): String = format("EEE, MMM d", timestamp)

/** "Nov 19" */
fun formatShortDate(timestamp: Long): String = format("MMM d", timestamp)

/** "1:30" */
fun formatClock(totalSeconds: Int): String = "${totalSeconds / 60}:${(totalSeconds % 60).toString().padStart(2, '0')}"

fun emptyWeightFor(equipment: Equipment): Double = emptyWeight(equipment, Store.state.settings.bar)

fun loadFor(weight: Double, equipment: Equipment): PlateLoad =
    platesPerSide(weight, emptyWeightFor(equipment), unitDefaults(Store.state.settings.unit).plates)

/** "20 + 2.5 per side", "Empty bar", or a note when the plates cannot make the weight. */
fun platesText(weight: Double, equipment: Equipment): String {
    if (weight < emptyWeightFor(equipment)) return "Lighter than the bar"
    val load = loadFor(weight, equipment)
    if (load.plates.isEmpty() && load.remainder == 0.0) {
        return if (equipment == Equipment.MACHINE) "Empty sled" else "Empty bar"
    }
    val text = if (load.plates.isNotEmpty()) load.plates.joinToString(" + ") { formatNumber(it) } + " per side" else "No plates fit"
    return if (load.remainder > 0) "$text (${fmt(load.remainder)} short)" else text
}

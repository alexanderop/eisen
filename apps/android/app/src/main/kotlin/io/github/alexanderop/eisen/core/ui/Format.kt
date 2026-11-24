package io.github.alexanderop.eisen.core.ui

import io.github.alexanderop.eisen.core.Equipment
import io.github.alexanderop.eisen.core.WeightUnit
import io.github.alexanderop.eisen.core.formatWeight
import io.github.alexanderop.eisen.core.loadText
import io.github.alexanderop.eisen.feature.workout.loadText
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** "82.5kg" in the lifter's unit, or in the unit a finished workout was logged in. */
fun fmt(value: Double, unit: WeightUnit): String = formatWeight(value, unit)

/** How heavy an exercise is: "80kg", "BW", or "BW + 10kg" with weight added to a bodyweight exercise. */
fun fmtLoad(equipment: Equipment, weight: Double, unit: WeightUnit): String =
    loadText(equipment == Equipment.BODYWEIGHT, weight, unit)

/** "None" or "+10kg": the added weight of a bodyweight exercise, as its stepper shows it. */
fun fmtAdded(weight: Double, unit: WeightUnit): String = if (weight == 0.0) "None" else "+${fmt(weight, unit)}"

private fun format(pattern: String, timestamp: Long): String =
    DateTimeFormatter.ofPattern(pattern, Locale.getDefault()).format(Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()))

/** "Thu, Nov 19" */
fun formatDate(timestamp: Long): String = format("EEE, MMM d", timestamp)

/** "Nov 19" */
fun formatShortDate(timestamp: Long): String = format("MMM d", timestamp)

/** "1:30" */
fun formatClock(totalSeconds: Int): String = "${totalSeconds / 60}:${(totalSeconds % 60).toString().padStart(2, '0')}"

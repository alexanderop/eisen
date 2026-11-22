package io.github.alexanderop.eisen.domain

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.math.BigDecimal
import java.math.RoundingMode
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.util.Base64
import kotlin.math.floor
import kotlin.math.max

fun durationMinutes(workout: Workout): Long? {
    val finished = workout.finishedAt ?: return null
    return max(1, Math.round((finished - workout.startedAt) / 60000.0))
}

/**
 * A workout as Markdown for pasting into a chat or a note.
 * A list, not a table: chat apps show lists fine and tables as pipes.
 * Exercises without a logged set are left out.
 */
fun workoutMarkdown(workout: Workout, date: String): String {
    val minutes = durationMinutes(workout)
    val lines = mutableListOf("## ${workout.name}", "", if (minutes == null) date else "$date · $minutes min", "")
    for (log in workout.exercises) {
        if (log.sets.all { it == null }) continue
        val load = if (log.equipment == Equipment.BODYWEIGHT) "BW" else formatWeight(log.weight, workout.unit)
        lines += "- **${log.name}** $load: ${repsText(log.sets, log.targetReps)}"
    }
    return lines.joinToString("\n")
}

data class SharedExercise(
    val name: String,
    val bodyweight: Boolean,
    /** 0 for bodyweight exercises. */
    val weight: Double,
    /** null = as many reps as possible. */
    val targetReps: Int?,
    /** null = set not logged. */
    val sets: List<Int?>,
    /** Set a record on that day. */
    val record: Boolean,
)

/**
 * What a shared workout carries: enough to show it, nothing else from the app.
 * It travels inside a link and is drawn on the share image.
 */
data class SharedWorkout(
    val name: String,
    val unit: WeightUnit,
    val startedAt: Long,
    val minutes: Long?,
    val exercises: List<SharedExercise>,
)

/** Exercises without a logged set are left out, as in the Markdown. */
fun toShared(workout: Workout, records: Set<String> = emptySet()): SharedWorkout = SharedWorkout(
    name = workout.name,
    unit = workout.unit,
    startedAt = workout.startedAt,
    minutes = durationMinutes(workout),
    exercises = workout.exercises
        .filter { log -> log.sets.any { it != null } }
        .map { log ->
            val bodyweight = log.equipment == Equipment.BODYWEIGHT
            SharedExercise(
                name = log.name,
                bodyweight = bodyweight,
                weight = if (bodyweight) 0.0 else log.weight,
                targetReps = log.targetReps,
                sets = log.sets,
                record = log.id in records,
            )
        },
)

data class SharedStats(
    /** Weight times reps over all weighted exercises, in the workout's unit. */
    val volume: Double,
    val sets: Int,
    val reps: Int,
)

fun sharedStats(shared: SharedWorkout): SharedStats {
    var volume = 0.0
    var sets = 0
    var reps = 0
    for (exercise in shared.exercises) {
        for (count in exercise.sets) {
            if (count == null) continue
            sets += 1
            reps += count
            volume += exercise.weight * count
        }
    }
    return SharedStats(volume, sets, reps)
}

/** "5×5" when every set hit the target, otherwise each set: "5, 5, 4, –, –". */
fun repsText(sets: List<Int?>, targetReps: Int?): String {
    val full = targetReps != null && sets.all { it == targetReps }
    return if (full) "${sets.size}×$targetReps" else sets.joinToString(", ") { it?.toString() ?: "–" }
}

private const val SHARE_VERSION = 1
private const val MINUTE_MS = 60000L
/** Limits for a link someone else made: it is input from a stranger. */
private const val MAX_SHARED_EXERCISES = 40
private const val MAX_NAME = 80

/*
 * The link format is the PWA's (`apps/web/src/domain/share.ts`), byte for byte, so a link made
 * here opens in the PWA. That is why the JSON is written by hand: it has to come out exactly
 * as the browser's `JSON.stringify` writes it, and parsing is as strict as `JSON.parse`.
 */

/** A string the way `JSON.stringify` quotes it: only quotes, backslashes and control characters are escaped. */
private fun StringBuilder.appendJsonString(text: String) {
    append('"')
    for ((index, char) in text.withIndex()) {
        when {
            char == '"' -> append("\\\"")
            char == '\\' -> append("\\\\")
            char == '\b' -> append("\\b")
            char == '\u000C' -> append("\\f")
            char == '\n' -> append("\\n")
            char == '\r' -> append("\\r")
            char == '\t' -> append("\\t")
            char < ' ' || isLoneSurrogate(text, index) -> append("\\u").append(char.code.toString(16).padStart(4, '0'))
            else -> append(char)
        }
    }
    append('"')
}

private fun isLoneSurrogate(text: String, index: Int): Boolean {
    val char = text[index]
    return when {
        char.isHighSurrogate() -> text.getOrNull(index + 1)?.isLowSurrogate() != true
        char.isLowSurrogate() -> text.getOrNull(index - 1)?.isHighSurrogate() != true
        else -> false
    }
}

/** `Number(value.toFixed(2))` as JavaScript writes it: 80, 82.5, 1.25. */
private fun twoDecimals(value: Double): String {
    val rounded = BigDecimal(value).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros()
    return if (rounded.signum() == 0) "0" else rounded.toPlainString()
}

/** JavaScript's `Math.round`: halves round up, also below zero. */
private fun jsRound(value: Double): Long = floor(value + 0.5).toLong()

/**
 * A workout as text for a link. Arrays instead of named fields keep the link short:
 * [version, name, unit, start minute, minutes, [name, bodyweight, weight, target, record, sets][]].
 * 0 stands for "max reps" as a target and -1 for a set that was not logged.
 */
fun encodeShared(shared: SharedWorkout): String {
    val json = buildString {
        append('[').append(SHARE_VERSION).append(',')
        appendJsonString(shared.name)
        append(',').append(if (shared.unit == WeightUnit.LB) 1 else 0)
        append(',').append(jsRound(shared.startedAt.toDouble() / MINUTE_MS))
        append(',').append(shared.minutes ?: 0)
        append(",[")
        shared.exercises.forEachIndexed { index, exercise ->
            if (index > 0) append(',')
            append('[')
            appendJsonString(exercise.name)
            append(',').append(if (exercise.bodyweight) 1 else 0)
            append(',').append(twoDecimals(exercise.weight))
            append(',').append(exercise.targetReps ?: 0)
            append(',').append(if (exercise.record) 1 else 0)
            append(",[").append(exercise.sets.joinToString(",") { (it ?: -1).toString() }).append("]]")
        }
        append("]]")
    }
    return Base64.getUrlEncoder().withoutPadding().encodeToString(json.encodeToByteArray())
}

/** `atob` skips these, so a link broken over lines by a mail program still reads. */
private val ASCII_SPACE = Regex("[\\t\\n\\u000C\\r ]")

/** `atob` followed by a strict UTF-8 `TextDecoder`. Throws on anything that is not both. */
private fun fromBase64Url(code: String): String {
    val base64 = code.replace(ASCII_SPACE, "").replace('-', '+').replace('_', '/')
    var bytes = Base64.getDecoder().decode(base64)
    // TextDecoder drops a byte order mark at the start.
    if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) {
        bytes = bytes.copyOfRange(3, bytes.size)
    }
    return Charsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
        .decode(ByteBuffer.wrap(bytes))
        .toString()
}

private val JSON_NUMBER = Regex("-?(0|[1-9][0-9]*)(\\.[0-9]+)?([eE][+-]?[0-9]+)?")

/** The kotlinx parser lets bare words through as values. `JSON.parse` does not, so neither does this. */
private fun isStrictJson(element: JsonElement): Boolean = when (element) {
    is JsonNull -> true
    is JsonArray -> element.all(::isStrictJson)
    is JsonObject -> element.values.all(::isStrictJson)
    is JsonPrimitive -> element.isString || element.content == "true" || element.content == "false" || JSON_NUMBER.matches(element.content)
}

/** The number a JSON value holds, or null when it is not a number: `typeof value === 'number'`. */
private fun numberOf(element: JsonElement?): Double? {
    if (element !is JsonPrimitive || element.isString || element is JsonNull) return null
    if (!JSON_NUMBER.matches(element.content)) return null
    return element.content.toDouble()
}

private fun isCount(value: Double?, max: Double): Boolean =
    value != null && value.isFinite() && value == floor(value) && value >= 0 && value <= max

/** What `String.prototype.trim` removes, which is not quite what Kotlin's `trim` removes. */
private fun isJsSpace(char: Char): Boolean =
    char in "\t\n\u000B\u000C\r       　﻿" || char in ' '..' '

private fun nameOf(element: JsonElement?): String? {
    if (element !is JsonPrimitive || !element.isString) return null
    val name = element.content
    return if (name.any { !isJsSpace(it) } && name.length <= MAX_NAME) name else null
}

private fun decodeExercise(value: JsonElement): SharedExercise? {
    if (value !is JsonArray) return null
    val name = nameOf(value.getOrNull(0)) ?: return null
    val bodyweight = numberOf(value.getOrNull(1)) == 1.0
    val weight = numberOf(value.getOrNull(2))
    val target = numberOf(value.getOrNull(3))
    val record = numberOf(value.getOrNull(4)) == 1.0
    val sets = value.getOrNull(5)
    if (!isCount(target, MAX_REPS.toDouble()) || sets !is JsonArray) return null
    if (weight == null || !weight.isFinite() || weight < 0 || weight > 10000) return null
    if (sets.isEmpty() || sets.size > MAX_SETS) return null
    val reps = sets.map(::numberOf)
    if (!reps.all { it == -1.0 || isCount(it, 999.0) }) return null
    return SharedExercise(
        name = name,
        bodyweight = bodyweight,
        weight = if (bodyweight) 0.0 else weight,
        targetReps = if (target == 0.0) null else target!!.toInt(),
        sets = reps.map { if (it == -1.0) null else it!!.toInt() },
        record = record,
    )
}

/** Reads a workout back out of a link. Anything that is not one gives null. */
fun decodeShared(code: String): SharedWorkout? {
    val data = runCatching { Json.parseToJsonElement(fromBase64Url(code)) }.getOrNull() ?: return null
    if (!isStrictJson(data) || data !is JsonArray) return null
    val version = numberOf(data.getOrNull(0))
    val name = nameOf(data.getOrNull(1))
    val unit = numberOf(data.getOrNull(2))
    val startMinute = numberOf(data.getOrNull(3))
    val minutes = numberOf(data.getOrNull(4))
    val list = data.getOrNull(5)
    if (version != SHARE_VERSION.toDouble() || name == null || list !is JsonArray) return null
    if (!isCount(startMinute, 1e8) || !isCount(minutes, 100000.0)) return null
    if (list.isEmpty() || list.size > MAX_SHARED_EXERCISES) return null
    val exercises = list.map(::decodeExercise)
    if (exercises.any { it == null }) return null
    return SharedWorkout(
        name = name,
        unit = if (unit == 1.0) WeightUnit.LB else WeightUnit.KG,
        startedAt = startMinute!!.toLong() * MINUTE_MS,
        minutes = if (minutes == 0.0) null else minutes!!.toLong(),
        exercises = exercises.filterNotNull(),
    )
}

private const val HASH_KEY = "#w="

/** The link that opens the app showing this workout. `base` is the app's address without a hash. */
fun sharedUrl(base: String, shared: SharedWorkout): String = base.substringBefore('#') + HASH_KEY + encodeShared(shared)

fun sharedFromHash(hash: String): SharedWorkout? =
    if (hash.startsWith(HASH_KEY)) decodeShared(hash.substring(HASH_KEY.length)) else null

package io.github.alexanderop.eisen.domain

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.intOrNull

@Serializable
data class Settings(
    val unit: WeightUnit,
    val bar: Double,
    /** Rest in seconds after a set with all reps. */
    val restDone: Int,
    /** Rest in seconds after a set with missed reps. */
    val restMissed: Int,
    val sound: Boolean,
    /** Short vibration when a set, a stepper or a finished workout is tapped. */
    val haptics: Boolean,
)

/** The same shape the PWA stores and exports, so a backup moves between the two apps. */
@Serializable
data class AppState(
    val version: Int,
    val settings: Settings,
    /** Working weight per exercise id. Bodyweight exercises have no entry. */
    val progress: Progress,
    val routines: List<Routine>,
    /** The workout the home screen offers next. */
    val nextRoutineId: String,
    val customExercises: List<Exercise>,
    val active: Workout?,
    /** Finished workouts, newest first. */
    val history: List<Workout>,
)

const val STATE_VERSION = 6

private val ORIGINAL_IDS = listOf("hackSquat", "bench", "row", "ohp", "deadlift")

private fun catalogProgress(ids: List<String>, unit: WeightUnit): Progress {
    val progress = linkedMapOf<String, ExerciseProgress>()
    for (id in ids) {
        val exercise = catalogExercise(id)
        if (exercise != null && exercise.equipment != Equipment.BODYWEIGHT) progress[id] = startProgress(exercise, unit)
    }
    return progress
}

fun freshState(unit: WeightUnit = WeightUnit.KG): AppState {
    val routines = defaultRoutines()
    return AppState(
        version = STATE_VERSION,
        settings = Settings(unit, unitDefaults(unit).bar, restDone = 90, restMissed = 300, sound = true, haptics = true),
        progress = catalogProgress(routines.flatMap { routine -> routine.slots.map { it.exerciseId } }, unit),
        routines = routines,
        nextRoutineId = routines[0].id,
        customExercises = emptyList(),
        active = null,
        history = emptyList(),
    )
}

val StateJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

private val PrettyJson = Json(StateJson) { prettyPrint = true }

fun encodeState(state: AppState, pretty: Boolean = false): String =
    (if (pretty) PrettyJson else StateJson).encodeToString(AppState.serializer(), state)

/** Reads a stored state or a backup of any version. Null when it is not one. */
fun decodeState(json: String): AppState? = try {
    val migrated = migrate(StateJson.parseToJsonElement(json))
    if (isAppState(migrated)) StateJson.decodeFromJsonElement<AppState>(migrated) else null
} catch (_: Exception) {
    null
}

private fun JsonElement?.obj(): JsonObject? = this as? JsonObject
private fun JsonElement?.arr(): JsonArray? = this as? JsonArray
private fun JsonElement?.prim(): JsonPrimitive? = (this as? JsonPrimitive)?.takeIf { it !is JsonNull }
private fun JsonElement?.int(): Int? = prim()?.takeIf { !it.isString }?.let { it.intOrNull ?: it.doubleOrNull?.takeIf { d -> d % 1.0 == 0.0 }?.toInt() }
private fun JsonElement?.num(): Double? = prim()?.takeIf { !it.isString }?.doubleOrNull
private fun JsonElement?.str(): String? = prim()?.takeIf { it.isString }?.content
/** What JavaScript's `String(value)` gives for the values an id can be. */
private fun JsonElement?.text(): String = prim()?.content ?: "undefined"

private fun JsonObject.with(vararg entries: Pair<String, JsonElement>): JsonObject = JsonObject(this + entries)
private fun JsonObject.without(vararg keys: String): JsonObject = JsonObject(this - keys.toSet())

/** Starting weights that version 2 shipped with, to tell an untouched install from an edited one. */
private val V2_START = mapOf(
    WeightUnit.KG to mapOf("hackSquat" to 20.0, "bench" to 20.0, "row" to 30.0, "ohp" to 20.0, "deadlift" to 40.0),
    WeightUnit.LB to mapOf("hackSquat" to 50.0, "bench" to 45.0, "row" to 65.0, "ohp" to 45.0, "deadlift" to 95.0),
)

/** Version 4 workouts carry their own name and copy each exercise's name and equipment. */
private fun upgradeWorkout(workout: JsonElement): JsonElement {
    val source = workout.obj() ?: return workout
    val type = source["type"]
    val exercises = (source["exercises"].arr() ?: JsonArray(emptyList())).mapNotNull { it.obj() }.map { log ->
        val id = log["id"].text()
        val exercise = catalogExercise(id)
        log.with(
            "name" to JsonPrimitive(exercise?.name ?: id),
            "equipment" to StateJson.encodeToJsonElement(exercise?.equipment ?: Equipment.BARBELL),
        )
    }
    return source.without("type", "exercises").with(
        "routineId" to (type ?: JsonNull),
        "name" to JsonPrimitive("Workout ${type.text()}"),
        "exercises" to JsonArray(exercises),
    )
}

private fun fiveDeadliftSets(routine: JsonElement): JsonElement {
    val source = routine.obj() ?: return routine
    val slots = source["slots"].arr() ?: return routine
    return source.with(
        "slots" to JsonArray(
            slots.map { slot ->
                val entry = slot.obj()
                if (entry != null && entry["exerciseId"].str() == "deadlift" && entry["sets"].int() == 1 && entry["reps"].int() == 5) {
                    entry.with("sets" to JsonPrimitive(5))
                } else {
                    slot
                }
            },
        ),
    )
}

/** Brings a stored state or backup from any older version up to the current one. */
fun migrate(value: JsonElement): JsonElement {
    var data = value.obj() ?: return value

    // Version 1 had the barbell squat where the hack squat is now. Its logs carry over.
    if (data["version"].int() == 1) {
        val text = StateJson.encodeToString(JsonObject.serializer(), data.with("version" to JsonPrimitive(2)))
            .replace("\"id\":\"squat\"", "\"id\":\"hackSquat\"")
            .replace("\"squat\":{", "\"hackSquat\":{")
        data = StateJson.parseToJsonElement(text) as JsonObject
    }

    val unit = if (data["settings"].obj()?.get("unit").str() == "lb") WeightUnit.LB else WeightUnit.KG

    // Version 3 changed the starting weights. An install still on the old ones gets the new ones.
    if (data["version"].int() == 2) {
        val progress = data["progress"].obj()
        val untouched = data["history"].arr()?.isEmpty() == true &&
            data["active"] is JsonNull &&
            ORIGINAL_IDS.all { id -> progress?.get(id).obj()?.get("weight").num() == V2_START.getValue(unit)[id] }
        data = data.with("version" to JsonPrimitive(3))
        if (untouched) data = data.with("progress" to StateJson.encodeToJsonElement(catalogProgress(ORIGINAL_IDS, unit)))
    }

    // Version 4 made workouts editable: the fixed A/B program became two ordinary workouts.
    if (data["version"].int() == 3) {
        val defaults = catalogProgress(ORIGINAL_IDS, unit)
        val stored = data["progress"].obj()
        val progress = ORIGINAL_IDS.associateWith { id ->
            val entry = stored?.get(id).obj()
            val base = defaults.getValue(id)
            base.copy(
                weight = entry?.get("weight").num() ?: base.weight,
                fails = entry?.get("fails").int() ?: base.fails,
            )
        }
        val active = data["active"]
        data = data.without("nextType").with(
            "version" to JsonPrimitive(4),
            "progress" to StateJson.encodeToJsonElement(progress),
            "routines" to StateJson.encodeToJsonElement(defaultRoutines()),
            "nextRoutineId" to JsonPrimitive(if (data["nextType"].str() == "B") "B" else "A"),
            "customExercises" to JsonArray(emptyList()),
            "active" to (if (active == null || active is JsonNull) JsonNull else upgradeWorkout(active)),
            "history" to JsonArray((data["history"].arr() ?: JsonArray(emptyList())).map(::upgradeWorkout)),
        )
    }

    // Version 5 made the deadlift 5×5 too. A workout still on the old single set gets all five.
    if (data["version"].int() == 4) {
        data = data.with("version" to JsonPrimitive(5))
        data["routines"].arr()?.let { routines -> data = data.with("routines" to JsonArray(routines.map(::fiveDeadliftSets))) }
    }

    // Version 6 added vibration on taps. It starts switched on.
    if (data["version"].int() == 5) {
        data = data.with("version" to JsonPrimitive(6))
        data["settings"].obj()?.let { settings -> data = data.with("settings" to settings.with("haptics" to JsonPrimitive(true))) }
    }

    return data
}

fun isAppState(value: JsonElement): Boolean {
    val data = value.obj() ?: return false
    return data["version"].int() == STATE_VERSION &&
        data["history"] is JsonArray &&
        data["routines"].arr()?.isNotEmpty() == true &&
        data["customExercises"] is JsonArray &&
        data["settings"] is JsonObject &&
        data["progress"] is JsonObject
}

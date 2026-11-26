package io.github.alexanderop.eisen.app.state

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
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
import io.github.alexanderop.eisen.core.Equipment
import io.github.alexanderop.eisen.core.Exercise
import io.github.alexanderop.eisen.core.WeightUnit
import io.github.alexanderop.eisen.core.catalogExercise
import io.github.alexanderop.eisen.core.unitDefaults
import io.github.alexanderop.eisen.feature.workout.ExerciseProgress
import io.github.alexanderop.eisen.feature.workout.Progress
import io.github.alexanderop.eisen.feature.workout.Routine
import io.github.alexanderop.eisen.feature.workout.Workout
import io.github.alexanderop.eisen.feature.workout.defaultRoutines
import io.github.alexanderop.eisen.feature.workout.nextRoutineId
import io.github.alexanderop.eisen.feature.workout.startProgress
import io.github.alexanderop.eisen.feature.timer.DEFAULT_TIMERS
import io.github.alexanderop.eisen.feature.timer.DEFAULT_TIMER_CALLS
import io.github.alexanderop.eisen.feature.timer.TimerCalls
import io.github.alexanderop.eisen.feature.timer.TimerConfig
import io.github.alexanderop.eisen.feature.timer.TimerMode
import io.github.alexanderop.eisen.feature.timer.TimerPreset
import io.github.alexanderop.eisen.feature.timer.TimerSession
import io.github.alexanderop.eisen.feature.timer.timerConfig
import io.github.alexanderop.eisen.feature.timer.wod.Wod
import io.github.alexanderop.eisen.feature.timer.wod.isLibraryWod
import io.github.alexanderop.eisen.feature.timer.wod.wod
import io.github.alexanderop.eisen.feature.food.Food
import io.github.alexanderop.eisen.feature.food.FoodEntry
import io.github.alexanderop.eisen.feature.food.FoodGoal
import io.github.alexanderop.eisen.feature.food.entryWithin
import io.github.alexanderop.eisen.feature.food.foodWithin
import io.github.alexanderop.eisen.feature.food.goalWithin
import io.github.alexanderop.eisen.feature.food.MAX_MEALS
import io.github.alexanderop.eisen.feature.food.Meal
import io.github.alexanderop.eisen.feature.food.mealWithin

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
    /** The rest opens as a full screen with the wheels. Off: only the small bar, which still opens it on a tap. */
    val restFullScreen: Boolean = true,
    /** What the voice of the interval timers says. One setting for all of them. */
    val timerCalls: TimerCalls = DEFAULT_TIMER_CALLS,
    /** The rest screen asks for the reps in reserve after a set, and a high rating doubles the next weight jump. */
    val trackRir: Boolean = true,
)

/** The same shape the PWA stores and exports, so a backup moves between the two apps. */
@Serializable
data class AppState(
    val version: Int,
    val settings: Settings,
    /**
     * Working weight per exercise id. A bodyweight exercise has an entry only while weight is added
     * to it (fails and increment 0). Older builds of both apps ignore such an entry.
     */
    val progress: Progress,
    val routines: List<Routine>,
    /** The workout the home screen offers next. */
    val nextRoutineId: String,
    val customExercises: List<Exercise>,
    val active: Workout?,
    /** Finished workouts, newest first. */
    val history: List<Workout>,
    /**
     * The lifter has picked the reps of a set by holding it once, so the workout screen no longer explains
     * how. Older files have no such field and older builds ignore it: no migration.
     */
    val pickedReps: Boolean = false,
    /**
     * The foods the phone knows besides the plain ones shipped in the app: the ones found by their barcode or
     * the search of Open Food Facts, kept so they need no network again, and the lifter's own. Older files have
     * no such field and older builds ignore it: no migration.
     */
    val foods: List<Food> = emptyList(),
    /** What was eaten, in the order it was logged. Each entry keeps its own numbers. */
    val foodLog: List<FoodEntry> = emptyList(),
    /** The calories and protein a day should reach; either may be missing. */
    val foodGoal: FoodGoal = FoodGoal(),
    /**
     * The lifter's meals: named lists of foods with their amounts, each logged in one tap. Older files have no such
     * field and older builds ignore it: no migration.
     */
    val meals: List<Meal> = emptyList(),
    /** Saved interval timers, in the order the Timer tab shows them. */
    val timerPresets: List<TimerPreset> = emptyList(),
    /** Finished interval timers, newest first. */
    val timerSessions: List<TimerSession> = emptyList(),
    /** The config each mode was last started with, so it opens the way the lifter left it. */
    val timerConfigs: Map<TimerMode, TimerConfig> = DEFAULT_TIMERS,
    /** The lifter's own WODs, in the order the Timer tab shows them. The benchmarks are code, see [WOD_LIBRARY]. */
    val wods: List<Wod> = emptyList(),
)

const val STATE_VERSION = 9

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
        timerPresets = emptyList(),
        timerSessions = emptyList(),
        timerConfigs = DEFAULT_TIMERS,
        wods = emptyList(),
    )
}

val StateJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

private val PrettyJson = Json(StateJson) { prettyPrint = true }

fun encodeState(state: AppState, pretty: Boolean = false): String =
    (if (pretty) PrettyJson else StateJson).encodeToString(AppState.serializer(), state)

/** What reading a stored state or a backup came to: the state, or why there is none. */
sealed interface Loaded {
    data class Ok(val state: AppState) : Loaded

    /** Not JSON at all: some other file. */
    data object NotJson : Loaded

    /** JSON, but not a state of this app. */
    data object NotEisen : Loaded

    /** Written by a newer build than this one. */
    data class TooNew(val version: Int) : Loaded

    /** A state of this app that cannot be read: what the reader stumbled over. */
    data class Broken(val reason: String) : Loaded
}

/** Reads a stored state or a backup of any version, and says why when it cannot. */
fun loadState(json: String): Loaded {
    val element = try {
        StateJson.parseToJsonElement(json)
    } catch (_: SerializationException) {
        return Loaded.NotJson
    }
    val version = element.obj()?.get("version").int() ?: return Loaded.NotEisen
    if (version > STATE_VERSION) return Loaded.TooNew(version)
    return try {
        val migrated = migrate(element)
        if (isAppState(migrated)) Loaded.Ok(inLimits(StateJson.decodeFromJsonElement<AppState>(migrated))) else Loaded.NotEisen
    } catch (error: Exception) {
        Loaded.Broken(error.message.orEmpty().lineSequence().first().take(REASON_LENGTH))
    }
}

private const val REASON_LENGTH = 140

/** The state of a stored state or a backup of any version. Null when [loadState] has none. */
fun decodeState(json: String): AppState? = (loadState(json) as? Loaded.Ok)?.state

/** What a file holds that has limits of its own: the WODs, boxing plans with one round per round, and the foods, what was eaten, the goal and the meals. */
private fun inLimits(state: AppState): AppState {
    fun boxing(config: TimerConfig) = if (config.mode == TimerMode.BOXING) timerConfig(config) else config
    return state.copy(
        wods = ownWods(state.wods),
        timerConfigs = state.timerConfigs.mapValues { (_, config) -> boxing(config) },
        timerPresets = state.timerPresets.map { it.copy(config = boxing(it.config)) },
        timerSessions = state.timerSessions.map { it.copy(config = boxing(it.config)) },
        foods = state.foods.map(::foodWithin).distinctBy { it.id },
        foodLog = state.foodLog.map(::entryWithin).distinctBy { it.id },
        foodGoal = goalWithin(state.foodGoal),
        meals = state.meals.mapNotNull(::mealWithin).distinctBy { it.id }.take(MAX_MEALS),
    )
}

/**
 * The lifter's WODs from a file, brought into their limits. A hand-edited one with the id of a
 * benchmark or of an earlier one would hide that one, so it is left out.
 */
private fun ownWods(wods: List<Wod>): List<Wod> = wods.map(::wod).filterNot { isLibraryWod(it.id) }.distinctBy { it.id }

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

    // Version 7 added the interval timers, their voice and the last config of each mode.
    if (data["version"].int() == 6) {
        data = data.with(
            "version" to JsonPrimitive(7),
            "timerPresets" to JsonArray(emptyList()),
            "timerSessions" to JsonArray(emptyList()),
            "timerConfigs" to StateJson.encodeToJsonElement(DEFAULT_TIMERS),
        )
        data["settings"].obj()?.let { settings ->
            data = data.with("settings" to settings.with("timerCalls" to StateJson.encodeToJsonElement(DEFAULT_TIMER_CALLS)))
        }
    }

    // Version 8 added the lifter's own WODs.
    if (data["version"].int() == 7) {
        data = data.with("version" to JsonPrimitive(8), "wods" to JsonArray(emptyList()))
    }

    // Version 9 added the boxing timer.
    if (data["version"].int() == 8) {
        val configs = data["timerConfigs"].obj() ?: JsonObject(emptyMap())
        data = data.with(
            "version" to JsonPrimitive(9),
            "timerConfigs" to configs.with("boxing" to StateJson.encodeToJsonElement(DEFAULT_TIMERS.getValue(TimerMode.BOXING))),
        )
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

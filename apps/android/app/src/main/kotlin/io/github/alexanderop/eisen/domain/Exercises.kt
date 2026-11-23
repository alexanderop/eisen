package io.github.alexanderop.eisen.domain

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * barbell: the bar counts toward the weight, plates go on both sides.
 * machine: plate-loaded, no bar. The weight is only the plates.
 * weight: dumbbells, cables, weight stacks and eGym machines. A number, no plates to work out.
 * bodyweight: no load, only reps.
 */
@Serializable
enum class Equipment(val label: String) {
    @SerialName("barbell") BARBELL("Barbell"),
    @SerialName("machine") MACHINE("Plate machine"),
    @SerialName("weight") WEIGHT("Machine / dumbbell"),
    @SerialName("bodyweight") BODYWEIGHT("Bodyweight"),
}

val GROUPS = listOf("Legs", "Chest", "Back", "Shoulders", "Arms", "Core", "Conditioning")
const val CUSTOM_GROUP = "Custom"

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class Exercise(
    val id: String,
    val name: String,
    val group: String,
    val equipment: Equipment,
    /** Starting weight in kg. 0 for bodyweight. */
    val start: Double,
    /** Weight in kg that a fully completed workout adds. */
    val increment: Double,
    /** File name in the shared `exercises` assets, without extension. The lifter's own have none. */
    val image: String? = null,
    /**
     * Held for time, like a plank. Every reps number of a timed exercise counts seconds instead.
     * Left out of the file when false, as the PWA leaves it out.
     */
    @EncodeDefault(EncodeDefault.Mode.NEVER) val timed: Boolean = false,
)

/** What the lists show next to an exercise. eGym machines are named, not filed under dumbbells. */
fun kindLabel(id: String, equipment: Equipment): String =
    if (id.startsWith("egym")) "eGym" else equipment.label

private fun row(group: String, id: String, name: String, equipment: Equipment, start: Number, increment: Number, image: String, timed: Boolean = false) =
    Exercise(id, name, group, equipment, start.toDouble(), increment.toDouble(), image, timed)

private const val TIMED = true

private val B = Equipment.BARBELL
private val M = Equipment.MACHINE
private val W = Equipment.WEIGHT
private val BW = Equipment.BODYWEIGHT

val CATALOG: List<Exercise> = listOf(
    row("Legs", "hackSquat", "Hack Squat", M, 80, 5, "hack-squat"),
    row("Legs", "squat", "Squat", B, 60, 5, "squat"),
    row("Legs", "frontSquat", "Front Squat", B, 40, 2.5, "squat"),
    row("Legs", "legPress", "Leg Press", M, 100, 5, "leg-press"),
    row("Legs", "romanianDeadlift", "Romanian Deadlift", B, 60, 5, "deadlift"),
    row("Legs", "hipThrust", "Hip Thrust", B, 60, 5, "hip-thrust"),
    row("Legs", "lunge", "Lunge", W, 10, 1, "walking-lunge"),
    row("Legs", "splitSquat", "Bulgarian Split Squat", W, 10, 1, "bulgarian-split-squat"),
    row("Legs", "gobletSquat", "Goblet Squat", W, 16, 2, "goblet-squat"),
    row("Legs", "legExtension", "Leg Extension", W, 30, 2.5, "leg-extension"),
    row("Legs", "legCurl", "Leg Curl", W, 25, 2.5, "seated-leg-curl"),
    row("Legs", "calfRaise", "Calf Raise", W, 40, 2.5, "standing-calf-raise"),
    row("Legs", "egymSquat", "eGym Squat", W, 60, 2, "egym-squat"),
    row("Legs", "wallSit", "Wall Sit", BW, 0, 0, "wall-sit", TIMED),
    row("Legs", "gluteBridgeHold", "Glute Bridge Hold", BW, 0, 0, "glute-bridge", TIMED),
    row("Legs", "airSquat", "Air Squat", BW, 0, 0, "air-squat"),
    row("Legs", "pistolSquat", "Pistol Squat", BW, 0, 0, "air-squat"),
    row("Legs", "bodyweightLunge", "Bodyweight Lunge", BW, 0, 0, "walking-lunge"),
    row("Chest", "bench", "Bench Press", B, 80, 2.5, "bench-press"),
    row("Chest", "inclineBench", "Incline Bench Press", B, 50, 2.5, "incline-bench-press"),
    row("Chest", "closeGripBench", "Close-Grip Bench Press", B, 50, 2.5, "bench-press"),
    row("Chest", "dumbbellBench", "Dumbbell Bench Press", W, 20, 2, "dumbbell-bench-press"),
    row("Chest", "inclineDumbbellPress", "Incline Dumbbell Press", W, 16, 2, "incline-dumbbell-press"),
    row("Chest", "chestFly", "Chest Fly", W, 10, 1, "dumbbell-fly"),
    row("Chest", "egymChestPress", "eGym Chest Press", W, 40, 2, "egym-chest-press"),
    row("Chest", "dips", "Dips", BW, 0, 0, "chest-dip"),
    row("Chest", "pushups", "Push-ups", BW, 0, 0, "push-up"),
    row("Back", "deadlift", "Deadlift", B, 100, 5, "deadlift"),
    row("Back", "row", "Barbell Row", B, 60, 2.5, "barbell-row"),
    row("Back", "tBarRow", "T-Bar Row", M, 30, 2.5, "t-bar-row"),
    row("Back", "dumbbellRow", "Dumbbell Row", W, 20, 2, "row"),
    row("Back", "latPulldown", "Lat Pulldown", W, 40, 2.5, "lat-pulldown"),
    row("Back", "cableRow", "Seated Cable Row", W, 40, 2.5, "seated-row"),
    row("Back", "facePull", "Face Pull", W, 15, 2.5, "face-pull"),
    row("Back", "egymLatPulldown", "eGym Lat Pulldown", W, 40, 2, "egym-lat-pulldown"),
    row("Back", "egymSeatedRow", "eGym Seated Row", W, 40, 2, "egym-seated-row"),
    row("Back", "pullups", "Pull-ups", BW, 0, 0, "pull-up"),
    row("Back", "chinups", "Chin-ups", BW, 0, 0, "chin-up"),
    row("Back", "backExtension", "Back Extension", BW, 0, 0, "back-extension"),
    row("Back", "deadHang", "Dead Hang", BW, 0, 0, "pull-up", TIMED),
    row("Back", "supermanHold", "Superman Hold", BW, 0, 0, "plank", TIMED),
    row("Back", "cleanAndJerk", "Clean and Jerk", B, 40, 2.5, "clean-and-jerk"),
    row("Back", "snatch", "Snatch", B, 30, 2.5, "clean-and-jerk"),
    row("Back", "hangPowerClean", "Hang Power Clean", B, 40, 2.5, "clean-and-jerk"),
    row("Back", "kettlebellSwing", "Kettlebell Swing", W, 16, 4, "kettlebell-swing"),
    row("Shoulders", "ohp", "Overhead Press", B, 40, 2.5, "overhead-press"),
    row("Shoulders", "dumbbellShoulderPress", "Dumbbell Shoulder Press", W, 14, 2, "shoulder-press"),
    row("Shoulders", "lateralRaise", "Lateral Raise", W, 6, 1, "lateral-raise"),
    row("Shoulders", "rearDeltFly", "Rear Delt Fly", W, 6, 1, "reverse-fly"),
    row("Shoulders", "egymShoulderPress", "eGym Shoulder Press", W, 25, 2, "egym-shoulder-press"),
    row("Shoulders", "shrug", "Shrug", B, 60, 5, "dumbbell-shrug"),
    row("Shoulders", "thruster", "Thruster", B, 30, 2.5, "thruster"),
    row("Shoulders", "pushJerk", "Push Jerk", B, 40, 2.5, "clean-and-jerk"),
    row("Shoulders", "handstandPushup", "Handstand Push-up", BW, 0, 0, "handstand-push-up"),
    row("Arms", "barbellCurl", "Barbell Curl", B, 25, 2.5, "barbell-curl"),
    row("Arms", "dumbbellCurl", "Dumbbell Curl", W, 10, 1, "biceps-curl"),
    row("Arms", "hammerCurl", "Hammer Curl", W, 10, 1, "hammer-curl"),
    row("Arms", "skullCrusher", "Skull Crusher", W, 20, 2.5, "skull-crusher"),
    row("Arms", "tricepsPushdown", "Triceps Pushdown", W, 20, 2.5, "rope-triceps-pushdown"),
    row("Arms", "overheadTricepsExtension", "Overhead Triceps Extension", W, 12, 1, "overhead-triceps-extension"),
    row("Arms", "farmersCarry", "Farmer's Carry", W, 20, 2, "farmers-carry", TIMED),
    row("Core", "hangingLegRaise", "Hanging Leg Raise", BW, 0, 0, "hanging-leg-raise"),
    row("Core", "situps", "Sit-ups", BW, 0, 0, "crunch"),
    row("Core", "abWheel", "Ab Wheel Rollout", BW, 0, 0, "ab-wheel-rollout"),
    row("Core", "cableCrunch", "Cable Crunch", W, 25, 2.5, "cable-crunch"),
    row("Core", "egymAbdominalCrunch", "eGym Abdominal Crunch", W, 30, 2, "egym-abdominal-crunch"),
    row("Core", "egymRotaryTorso", "eGym Rotary Torso", W, 25, 2, "egym-rotary-torso"),
    row("Core", "plank", "Plank", BW, 0, 0, "plank", TIMED),
    row("Core", "sidePlank", "Side Plank", BW, 0, 0, "side-plank", TIMED),
    row("Core", "hollowHold", "Hollow Hold", BW, 0, 0, "hollow-hold", TIMED),
    row("Core", "lSit", "L-Sit", BW, 0, 0, "l-sit", TIMED),
    row("Core", "toesToBar", "Toes-to-Bar", BW, 0, 0, "hanging-leg-raise"),
    row("Conditioning", "burpee", "Burpee", BW, 0, 0, "burpee"),
    row("Conditioning", "boxJump", "Box Jump", BW, 0, 0, "box-jump"),
    row("Conditioning", "doubleUnder", "Double-Under", BW, 0, 0, "double-under"),
    row("Conditioning", "wallBall", "Wall Ball", W, 9, 1, "wall-ball"),
    row("Conditioning", "run", "Run", BW, 0, 0, "run"),
    row("Conditioning", "rowErg", "Row", BW, 0, 0, "rower"),
    row("Conditioning", "climber", "Climbers", BW, 0, 0, "burpee"),
    row("Conditioning", "jumpingJack", "Jumping Jacks", BW, 0, 0, "run"),
    row("Conditioning", "jump", "Jumps", BW, 0, 0, "box-jump"),
    row("Conditioning", "standup", "Standups", BW, 0, 0, "plank"),
    row("Conditioning", "sprint", "Sprint", BW, 0, 0, "run"),
    row("Conditioning", "rest", "Rest", BW, 0, 0, "hollow-hold"),
)

private val BY_ID = CATALOG.associateBy { it.id }

fun catalogExercise(id: String): Exercise? = BY_ID[id]

fun customExercise(id: String, name: String, equipment: Equipment, timed: Boolean = false): Exercise {
    val (start, increment) = when (equipment) {
        Equipment.BARBELL -> 20.0 to 2.5
        Equipment.MACHINE -> 20.0 to 5.0
        Equipment.WEIGHT -> 10.0 to 1.0
        Equipment.BODYWEIGHT -> 0.0 to 0.0
    }
    return Exercise(id, name, CUSTOM_GROUP, equipment, start, increment, timed = timed)
}

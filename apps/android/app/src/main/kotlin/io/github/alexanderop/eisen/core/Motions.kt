package io.github.alexanderop.eisen.core

/*
 * Moving pictures of exercises: a figure doing one repetition, for the screens of a WOD. Each is a
 * strip of MOTION_FRAMES square frames side by side in the shared `motions` assets, drawn on the dark
 * colour MOTION_BACKGROUND. The same as the PWA's `domain/motions.ts`.
 */

const val MOTION_FRAMES = 6

/** How long one frame shows. One repetition takes six of these. */
const val MOTION_FRAME_MS = 260L

const val MOTION_BACKGROUND = 0xFF0B0C0E

/** The frame shown when the picture must not move: the middle of the movement. */
const val MOTION_STILL_FRAME = 3

/** Exercise id to the file name of its strip, without extension. Some exercises share one. */
val MOTIONS: Map<String, String> = mapOf(
    "burpee" to "burpee",
    "airSquat" to "air-squat",
    "pushups" to "push-up",
    "pullups" to "pull-up",
    "chinups" to "pull-up",
    "situps" to "sit-up",
    "run" to "run",
    "sprint" to "run",
    "thruster" to "thruster",
    "kettlebellSwing" to "kettlebell-swing",
    "cleanAndJerk" to "clean-and-jerk",
    "snatch" to "snatch",
    "hangPowerClean" to "hang-power-clean",
    "pushJerk" to "push-jerk",
    "deadlift" to "deadlift",
    "doubleUnder" to "double-under",
    "wallBall" to "wall-ball",
    "handstandPushup" to "handstand-push-up",
    "pistolSquat" to "pistol-squat",
    "rowErg" to "row",
    "bodyweightLunge" to "lunge",
    "climber" to "climbers",
    "jumpingJack" to "jumping-jack",
    "jump" to "tuck-jump",
    "standup" to "standup",
    "boxJump" to "box-jump",
    "toesToBar" to "toes-to-bar",
)

/** The strip of an exercise, or null when it has none. */
fun motionOf(exerciseId: String): String? = MOTIONS[exerciseId]

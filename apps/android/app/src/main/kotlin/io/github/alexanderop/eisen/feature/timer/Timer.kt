package io.github.alexanderop.eisen.feature.timer

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.nullable
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlin.math.abs
import kotlin.math.roundToLong
import io.github.alexanderop.eisen.core.REST_EXERCISE
import io.github.alexanderop.eisen.core.holdText
import io.github.alexanderop.eisen.feature.timer.boxing.BoxingPlan
import io.github.alexanderop.eisen.feature.timer.boxing.BoxingRound
import io.github.alexanderop.eisen.feature.timer.boxing.BoxingStyle
import io.github.alexanderop.eisen.feature.timer.boxing.BoxingTheme
import io.github.alexanderop.eisen.feature.timer.boxing.MAX_BOXING_REST_SECONDS
import io.github.alexanderop.eisen.feature.timer.boxing.MAX_BOXING_ROUNDS
import io.github.alexanderop.eisen.feature.timer.boxing.MAX_BOXING_ROUND_SECONDS
import io.github.alexanderop.eisen.feature.timer.boxing.MIN_BOXING_REST_SECONDS
import io.github.alexanderop.eisen.feature.timer.boxing.MIN_BOXING_ROUND_SECONDS
import io.github.alexanderop.eisen.feature.timer.boxing.boxingCues
import io.github.alexanderop.eisen.feature.timer.boxing.boxingPlan
import io.github.alexanderop.eisen.feature.timer.wod.Wod
import io.github.alexanderop.eisen.feature.timer.wod.wod

/*
 * The port of the PWA's `domain/timer.ts`. Both apps must behave the same: the cue schedules are
 * the contract between them, and `TimerTest` checks them with the same inputs as `timer.test.ts`.
 */

/**
 * Interval timers next to the strength workouts.
 * tabata: `rounds` times `work`, with `rest` between them (none after the last).
 * emom: `rounds` intervals of `work` back to back, no rest.
 * amrap: one block of `work`. The lifter counts rounds by tapping.
 * forTime: counts up to a cap of `work`, 0 for no cap. The lifter stops it when done.
 * boxing: `rounds` rounds of `work` with `rest` between them, and a coach who calls combinations.
 */
@Serializable
enum class TimerMode(val title: String) {
    @SerialName("tabata") TABATA("Tabata"),
    @SerialName("emom") EMOM("EMOM"),
    @SerialName("amrap") AMRAP("AMRAP"),
    @SerialName("forTime") FOR_TIME("For Time"),
    @SerialName("boxing") BOXING("Boxing"),
}

val TIMER_MODES: List<TimerMode> = TimerMode.entries

/** The modes a WOD can be built on. Boxing has its own plan instead of a list of movements. */
val WOD_MODES: List<TimerMode> = listOf(TimerMode.FOR_TIME, TimerMode.AMRAP, TimerMode.EMOM, TimerMode.TABATA)

/**
 * Seconds. What each number means depends on the mode, see [TimerMode]. [boxing]: what each round
 * is about, boxing only. Left out of the file in the other modes, as the PWA leaves it out.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class TimerConfig(
    val mode: TimerMode,
    val work: Int,
    val rest: Int,
    val rounds: Int,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val boxing: BoxingPlan? = null,
)

/** What the voice says while a timer runs. */
@Serializable
data class TimerCalls(
    /** "Five minutes left" every this many minutes, one of [MINUTE_CALLS]. 0 = never. */
    val minutes: Int,
    /** "Halfway" at half of the whole timer. */
    val halfway: Boolean,
    /** "Thirty seconds" and "Ten seconds" before the end. */
    val final: Boolean,
    /** "Get ready", "Round three", "Rest", "Last round", "Workout complete". */
    val phases: Boolean,
    /** Three short beeps and a long one into every change of phase. */
    val beeps: Boolean,
)

val MINUTE_CALLS: List<Int> = listOf(0, 1, 2, 5)

val DEFAULT_TIMER_CALLS = TimerCalls(minutes = 1, halfway = true, final = true, phases = true, beeps = true)

val DEFAULT_TIMERS: Map<TimerMode, TimerConfig> = mapOf(
    TimerMode.TABATA to TimerConfig(TimerMode.TABATA, work = 20, rest = 10, rounds = 8),
    TimerMode.EMOM to TimerConfig(TimerMode.EMOM, work = 60, rest = 0, rounds = 10),
    TimerMode.AMRAP to TimerConfig(TimerMode.AMRAP, work = 600, rest = 0, rounds = 1),
    TimerMode.FOR_TIME to TimerConfig(TimerMode.FOR_TIME, work = 1200, rest = 0, rounds = 1),
    TimerMode.BOXING to TimerConfig(
        TimerMode.BOXING,
        work = 180,
        rest = 60,
        rounds = 6,
        boxing = BoxingPlan(
            listOf(
                BoxingRound(BoxingTheme.JAB, 4, 0),
                BoxingRound(BoxingTheme.TWO, 4, 0),
                BoxingRound(BoxingTheme.THREE, 6, 0),
                BoxingRound(BoxingTheme.FOUR, 6, 0),
                BoxingRound(BoxingTheme.MIX, 6, 30),
                BoxingRound(BoxingTheme.FREESTYLE, 6, 0),
            ),
            BoxingStyle.NAMES,
        ),
    ),
)

const val TIMER_LEAD_IN_SECONDS = 10
const val MIN_TIMER_SECONDS = 5
const val MAX_TIMER_SECONDS = 3600
const val MAX_TIMER_REST_SECONDS = 600
const val MAX_TIMER_ROUNDS = 60

/** The most rounds an AMRAP or For Time counts by tap, and its result can be corrected to. */
const val MAX_COUNTED_ROUNDS = 999

/** An open For Time stops itself here, and the voice counts minutes up to it. */
const val MAX_OPEN_TIMER_SECONDS = 3600

/** The highest number the voice has a recording for. */
const val MAX_SPOKEN_NUMBER = 60

private const val SECOND = 1000L
private const val MINUTE = 60 * SECOND
private const val LEAD_IN = TIMER_LEAD_IN_SECONDS * SECOND

/** A spoken line waits for the long beep at a change of phase to end. */
const val VOICE_AFTER_BEEP = 700L

/** Room one spoken line needs before the next one starts. */
private const val VOICE_GAP = 2500L

/** JavaScript's `Math.round`: halves go up. */
private fun clamp(value: Double, min: Int, max: Int): Int = Math.floor(value + 0.5).toInt().coerceIn(min, max)

/** A config from a form, a preset or a backup, brought into the limits of its mode. */
fun timerConfig(config: TimerConfig): TimerConfig =
    timerConfig(config.mode, config.work.toDouble(), config.rest.toDouble(), config.rounds.toDouble(), config.boxing)

/** [timerConfig] for numbers that may not be whole yet. */
fun timerConfig(mode: TimerMode, work: Double, rest: Double, rounds: Double, boxing: BoxingPlan? = null): TimerConfig = when (mode) {
    TimerMode.TABATA -> TimerConfig(
        mode,
        work = clamp(work, MIN_TIMER_SECONDS, MAX_TIMER_SECONDS),
        rest = clamp(rest, MIN_TIMER_SECONDS, MAX_TIMER_REST_SECONDS),
        rounds = clamp(rounds, 1, MAX_TIMER_ROUNDS),
    )
    TimerMode.EMOM -> TimerConfig(mode, work = clamp(work, MIN_TIMER_SECONDS, MAX_TIMER_SECONDS), rest = 0, rounds = clamp(rounds, 1, MAX_TIMER_ROUNDS))
    TimerMode.AMRAP -> TimerConfig(mode, work = clamp(work, MIN_TIMER_SECONDS, MAX_TIMER_SECONDS), rest = 0, rounds = 1)
    TimerMode.FOR_TIME -> TimerConfig(mode, work = if (work <= 0) 0 else clamp(work, MIN_TIMER_SECONDS, MAX_TIMER_SECONDS), rest = 0, rounds = 1)
    TimerMode.BOXING -> {
        val count = clamp(rounds, 1, MAX_BOXING_ROUNDS)
        TimerConfig(
            mode,
            work = clamp(work, MIN_BOXING_ROUND_SECONDS, MAX_BOXING_ROUND_SECONDS),
            rest = clamp(rest, MIN_BOXING_REST_SECONDS, MAX_BOXING_REST_SECONDS),
            rounds = count,
            boxing = boxingPlan(boxing, count),
        )
    }
}

/** True for the modes where the lifter counts rounds by tapping. */
fun countsRoundsByTap(mode: TimerMode): Boolean = mode == TimerMode.AMRAP || mode == TimerMode.FOR_TIME

/** Length of the timer without the lead-in, in seconds. Null for a For Time without a cap. */
fun timerSeconds(config: TimerConfig): Int? = when (config.mode) {
    TimerMode.TABATA, TimerMode.BOXING -> config.rounds * config.work + (config.rounds - 1) * config.rest
    TimerMode.EMOM -> config.rounds * config.work
    TimerMode.AMRAP -> config.work
    TimerMode.FOR_TIME -> config.work.takeIf { it > 0 }
}

/** "8 × 20s / 10s", "10 × 1:00", "10:00", "Cap 20:00", "No cap". */
fun timerSummary(config: TimerConfig): String = when (config.mode) {
    TimerMode.TABATA, TimerMode.BOXING -> "${config.rounds} × ${holdText(config.work)} / ${holdText(config.rest)}"
    TimerMode.EMOM -> "${config.rounds} × ${holdText(config.work)}"
    TimerMode.AMRAP -> holdText(config.work)
    TimerMode.FOR_TIME -> if (config.work > 0) "Cap ${holdText(config.work)}" else "No cap"
}

private enum class SegmentKind { WORK, REST }

/** [start] and [end] in milliseconds from the start of the timer, after the lead-in. A rest belongs to the round before it. */
private class Segment(val kind: SegmentKind, val start: Long, val end: Long, val round: Int)

/** The work and rest blocks in order. An open For Time is one block up to the limit. */
private fun segments(config: TimerConfig): List<Segment> {
    if (countsRoundsByTap(config.mode)) {
        val seconds = timerSeconds(config) ?: MAX_OPEN_TIMER_SECONDS
        return listOf(Segment(SegmentKind.WORK, 0, seconds * SECOND, 1))
    }
    val list = mutableListOf<Segment>()
    var at = 0L
    for (round in 1..config.rounds) {
        list += Segment(SegmentKind.WORK, at, at + config.work * SECOND, round)
        at += config.work * SECOND
        if (config.rest > 0 && round < config.rounds) {
            list += Segment(SegmentKind.REST, at, at + config.rest * SECOND, round)
            at += config.rest * SECOND
        }
    }
    return list
}

enum class TimerPhase { READY, WORK, REST, DONE }

/** One block of a timer: work or rest, [millis] long. */
data class TimerSpan(val phase: TimerPhase, val millis: Long)

/**
 * The work and rest blocks of [config] in order, without the lead-in. AMRAP and For Time are one
 * block of work, an open For Time up to the limit it stops at.
 */
fun timerSpans(config: TimerConfig): List<TimerSpan> =
    segments(config).map { TimerSpan(if (it.kind == SegmentKind.REST) TimerPhase.REST else TimerPhase.WORK, it.end - it.start) }

/** Where a timer stands. All times in milliseconds. */
data class TimerView(
    val phase: TimerPhase,
    /** The round the lifter is in, 1 for the first. In AMRAP and For Time always 1: they count by tapping. */
    val round: Int,
    /** Rounds the timer has, null where the lifter counts them. */
    val rounds: Int?,
    /** Rounds of tabata or emom whose work is over. 0 in the tapped modes. */
    val roundsDone: Int,
    /** Left in the lead-in, the work or the rest. Null in an open For Time. 0 when done. */
    val phaseLeft: Long?,
    val phaseElapsed: Long,
    /** Length of the current phase. Null in an open For Time. */
    val phaseTotal: Long?,
    /** Since the lead-in ended. 0 during the lead-in, the full length when done. */
    val elapsed: Long,
    /** Left of the whole timer. Null in an open For Time. */
    val totalLeft: Long?,
    /** 0 to 1 through the current phase, for the ring. 0 in an open For Time. */
    val progress: Double,
)

/**
 * Where the timer stands [active] milliseconds after Start was tapped, pauses not counted.
 * The first [TIMER_LEAD_IN_SECONDS] of that are the lead-in.
 */
fun timerView(config: TimerConfig, active: Long): TimerView {
    val list = segments(config)
    val structured = !countsRoundsByTap(config.mode)
    val rounds = if (structured) config.rounds else null
    val seconds = timerSeconds(config)
    val total = (seconds ?: MAX_OPEN_TIMER_SECONDS) * SECOND
    if (active < LEAD_IN) {
        val into = maxOf(0, active)
        return TimerView(
            phase = TimerPhase.READY,
            round = 1,
            rounds = rounds,
            roundsDone = 0,
            phaseLeft = LEAD_IN - into,
            phaseElapsed = into,
            phaseTotal = LEAD_IN,
            elapsed = 0,
            totalLeft = if (seconds == null) null else total,
            progress = into.toDouble() / LEAD_IN,
        )
    }
    val elapsed = active - LEAD_IN
    val segment = list.firstOrNull { elapsed < it.end }
        ?: return TimerView(
            phase = TimerPhase.DONE,
            round = if (structured) config.rounds else 1,
            rounds = rounds,
            roundsDone = if (structured) config.rounds else 0,
            phaseLeft = 0,
            phaseElapsed = 0,
            phaseTotal = 0,
            elapsed = total,
            totalLeft = 0,
            progress = 1.0,
        )
    val open = seconds == null
    val length = segment.end - segment.start
    return TimerView(
        phase = if (segment.kind == SegmentKind.REST) TimerPhase.REST else TimerPhase.WORK,
        round = segment.round,
        rounds = rounds,
        roundsDone = if (!structured) 0 else if (segment.kind == SegmentKind.REST) segment.round else segment.round - 1,
        phaseLeft = if (open) null else segment.end - elapsed,
        phaseElapsed = elapsed - segment.start,
        phaseTotal = if (open) null else length,
        elapsed = elapsed,
        totalLeft = if (open) null else total - elapsed,
        progress = if (open) 0.0 else (elapsed - segment.start).toDouble() / length,
    )
}

/**
 * A sound the timer makes. [at] is in milliseconds after Start was tapped, pauses not counted,
 * the same clock as [timerView]. [sound] is "beep", "beep-long" or the name of a recording
 * in `assets/voice/timer`, without extension. [combo]: on the first move of a boxing combination of
 * the lifter's own, its id, as that combination is said move by move.
 */
data class TimerCue(val at: Long, val sound: String, val combo: String? = null)

/**
 * Every sound of a timer from Start to the end, in order.
 * Worked out once up front, so a phone can play them with the screen off and a test can read them.
 *
 * The time left is always the time left in the round, never in the whole timer.
 * Spoken lines never talk over each other: a time call that would land on a line about the
 * phase waits until that one is over, and of two time calls at the same moment only one is said.
 * In an EMOM a time call on a round start is left out: the round number says the same.
 * A boxing timer has its own coach, see [boxingCues]; [seed] picks its combinations.
 */
fun timerCues(config: TimerConfig, calls: TimerCalls, seed: Int = 1): List<TimerCue> {
    if (config.mode == TimerMode.BOXING) return boxingCues(config, calls, seed)
    val list = segments(config)
    val seconds = timerSeconds(config)
    val total = seconds?.let { it * SECOND }
    val beeps = mutableListOf<TimerCue>()
    val phases = mutableListOf<TimerCue>()
    val times = mutableListOf<TimerCue>()

    val changes = list.map { it.start } + listOfNotNull(total)
    if (calls.beeps) {
        for (change in changes) {
            for (before in listOf(3, 2, 1)) beeps += TimerCue(LEAD_IN + change - before * SECOND, "beep")
            beeps += TimerCue(LEAD_IN + change, "beep-long")
        }
    }

    if (calls.phases) {
        phases += TimerCue(0, "get-ready")
        for (segment in list) {
            val at = LEAD_IN + segment.start + VOICE_AFTER_BEEP
            when {
                segment.kind == SegmentKind.REST -> phases += TimerCue(at, "rest")
                countsRoundsByTap(config.mode) || config.rounds == 1 -> phases += TimerCue(at, "go")
                segment.round == config.rounds -> phases += TimerCue(at, "last-round")
                segment.round <= MAX_SPOKEN_NUMBER -> phases += TimerCue(at, "round-${segment.round}")
            }
        }
        if (total != null) phases += TimerCue(LEAD_IN + total + VOICE_AFTER_BEEP, "complete")
    }

    // The time calls are about the round the lifter is in: with rounds, how long the whole timer
    // still runs is of no use. A timer of one block has one round, the whole of it.
    // Most important first: of two calls at the same moment the earlier one in this list is said.
    if (total != null) {
        val rounds = list.filter { it.kind == SegmentKind.WORK }
        // Halfway through every single minute of an EMOM would be noise: with rounds it takes a long one.
        val halfwayFrom = if (rounds.size > 1) 2 * MINUTE else MINUTE
        for (round in rounds) {
            val length = round.end - round.start
            val over = LEAD_IN + round.end
            if (calls.halfway && length >= halfwayFrom) times += TimerCue(LEAD_IN + round.start + length / 2, "halfway")
            if (calls.final && length >= 90 * SECOND) times += TimerCue(over - 30 * SECOND, "thirty-seconds")
            if (calls.final && length >= 30 * SECOND) times += TimerCue(over - 10 * SECOND, "ten-seconds")
            if (calls.minutes > 0) {
                var left = calls.minutes
                while (left * MINUTE < length && left <= MAX_SPOKEN_NUMBER) {
                    times += TimerCue(over - left * MINUTE, "minutes-left-$left")
                    left += calls.minutes
                }
            }
        }
    } else if (calls.minutes > 0) {
        var done = calls.minutes
        while (done <= MAX_SPOKEN_NUMBER) {
            if (done * MINUTE < MAX_OPEN_TIMER_SECONDS * SECOND) times += TimerCue(LEAD_IN + done * MINUTE, "minutes-$done")
            done += calls.minutes
        }
    }

    val spoken = mutableListOf<TimerCue>()
    for (call in times) {
        val phase = phases.firstOrNull { abs(it.at - call.at) < VOICE_GAP }
        if (phase != null && config.mode == TimerMode.EMOM) continue
        val moved = if (phase != null) TimerCue(phase.at + VOICE_GAP, call.sound) else call
        if (spoken.none { abs(it.at - moved.at) < VOICE_GAP }) spoken += moved
    }

    val end = if (total == null) Long.MAX_VALUE else LEAD_IN + total
    // Stable like the JavaScript sort: at the same moment beeps come before phases before time calls.
    return (beeps + phases + spoken.filter { it.at < end }).sortedBy { it.at }
}

/** A timer the lifter saved to start again. */
@Serializable
data class TimerPreset(val id: String, val name: String, val config: TimerConfig)

/** A finished timer in the history. */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class TimerSession(
    val id: String,
    val name: String,
    val config: TimerConfig,
    val startedAt: Long,
    val finishedAt: Long,
    /** Seconds the timer ran, without the lead-in and without pauses. */
    val seconds: Int,
    /** Rounds done: finished work blocks in tabata and emom, taps in the other two, rounds fully ticked off in a For Time WOD. */
    val rounds: Int,
    /** False when the lifter stopped it before the end. A For Time the lifter finished is complete. */
    val completed: Boolean,
    /** The WOD this was a run of. Left out of the file for a plain timer, as the PWA leaves it out. */
    @EncodeDefault(EncodeDefault.Mode.NEVER) val wodId: String? = null,
    /**
     * Seconds into the WOD at each tap, to one decimal, pauses and lead-in not counted.
     * A later run is measured against them while it is still going. Left out when nothing was tapped.
     */
    @EncodeDefault(EncodeDefault.Mode.NEVER) val splits: List<Double>? = null,
    /** Boxing: about how many punches the coach called for up to where the run ended. Left out of the file for the other modes. */
    @EncodeDefault(EncodeDefault.Mode.NEVER) val punches: Int? = null,
    /** Boxing: the combinations the coach called up to where the run ended. Left out of the file for the other modes. */
    @EncodeDefault(EncodeDefault.Mode.NEVER) val combos: Int? = null,
    /** What the lifter did to the timer: jump rope, cross trainer, ... Left out of the file when not said. */
    @EncodeDefault(EncodeDefault.Mode.NEVER) @Serializable(with = ActivityOrNone::class) val activity: Activity? = null,
)

/**
 * What a finished timer was. The name in the file is the [SerialName]; the label is what the lifter reads.
 * Health Connect maps each one to an exercise type of its own (`feature/health/platform/HealthMapping.kt`).
 */
@Serializable
enum class Activity(val label: String) {
    @SerialName("boxing") BOXING("Boxing"),
    @SerialName("jumpRope") JUMP_ROPE("Jump rope"),
    @SerialName("battleRope") BATTLE_ROPE("Battle rope"),
    @SerialName("crossTrainer") CROSS_TRAINER("Cross trainer"),
    @SerialName("rowing") ROWING("Rowing"),
    @SerialName("bike") BIKE("Bike"),
    @SerialName("running") RUNNING("Running"),
}

/** The chips "What was it?" offers, in this order. */
val ACTIVITIES: List<Activity> = listOf(
    Activity.BOXING,
    Activity.JUMP_ROPE,
    Activity.BATTLE_ROPE,
    Activity.CROSS_TRAINER,
    Activity.ROWING,
    Activity.BIKE,
    Activity.RUNNING,
)

/** The name an activity has in the file. */
private val ACTIVITY_NAMES: Map<String, Activity> = Activity.entries.associateBy { Activity.serializer().descriptor.getElementName(it.ordinal) }

/**
 * An activity in the file, or none. A name this build does not know (written by a newer one) is read as
 * none, so one session never makes the whole file unreadable.
 */
object ActivityOrNone : KSerializer<Activity?> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("io.github.alexanderop.eisen.ActivityOrNone", PrimitiveKind.STRING).nullable

    override fun serialize(encoder: Encoder, value: Activity?) {
        val name = ACTIVITY_NAMES.entries.firstOrNull { it.value == value }?.key
        if (encoder is JsonEncoder) encoder.encodeJsonElement(if (name == null) JsonNull else JsonPrimitive(name)) else encoder.encodeString(name.orEmpty())
    }

    override fun deserialize(decoder: Decoder): Activity? {
        val element = if (decoder is JsonDecoder) decoder.decodeJsonElement() else JsonPrimitive(decoder.decodeString())
        val name = (element as? JsonPrimitive)?.takeIf { it.isString }?.content ?: return null
        return ACTIVITY_NAMES[name]
    }
}

/**
 * The activity where it adds to the name: "Jump rope" for a "Tabata", nothing for a "Boxing" run that
 * was boxing. For the history row and the calendar.
 */
fun activityNote(session: TimerSession): String? =
    session.activity?.label?.takeIf { !it.equals(session.name.trim(), ignoreCase = true) }

/**
 * What a run is filed with before the lifter says anything: boxing for the boxing timer, else what the
 * newest earlier run of the same timer (same name and mode) was, so a "Tabata" used for jump rope stays
 * jump rope until it is changed. None for a WOD: the WOD is what it was. [earlier] is newest first.
 */
fun presetActivity(session: TimerSession, earlier: List<TimerSession>): Activity? = when {
    session.wodId != null -> null
    session.config.mode == TimerMode.BOXING -> Activity.BOXING
    else -> earlier.firstOrNull { it.id != session.id && it.wodId == null && it.name == session.name && it.config.mode == session.config.mode }?.activity
}

/** A timer that is running or paused. Timestamps, so it stays right while the app sleeps. */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class TimerRun(
    val id: String,
    val name: String,
    val config: TimerConfig,
    val calls: TimerCalls,
    val startedAt: Long,
    /** When it was paused. Null while it runs. */
    val pausedAt: Long? = null,
    /** Milliseconds spent in pauses that are over. */
    val paused: Long = 0,
    /** Rounds the lifter tapped, in AMRAP and For Time. In a For Time WOD every item of every round is a tap. */
    val taps: Int = 0,
    /** The WOD being done, copied in so the run still reads right when the WOD is edited or deleted. */
    val wod: Wod? = null,
    /** Seconds into the WOD at each tap so far, one entry per tap. Only kept for a WOD. */
    val splits: List<Double> = emptyList(),
    /** "Skip rest" was tapped for the rest that came with this many taps. Back forgets it. */
    val restSkipped: Int? = null,
    /** Picks the combinations of a boxing run, so a reload calls the same ones. */
    @EncodeDefault(EncodeDefault.Mode.NEVER) val seed: Int? = null,
)

/** Seconds into the work of a run, to one decimal: the time a tap at [now] is filed under. */
fun splitTime(run: TimerRun, now: Long): Double =
    Math.floor(timerView(run.config, activeTime(run, now)).elapsed / 100.0 + 0.5) / 10

/** Milliseconds the run has been going, pauses not counted. */
fun activeTime(run: TimerRun, now: Long): Long = maxOf(0, (run.pausedAt ?: now) - run.startedAt - run.paused)

/** True once the timer has run out by itself. An open For Time runs out at the limit. */
fun isTimerOver(run: TimerRun, now: Long): Boolean = timerView(run.config, activeTime(run, now)).phase == TimerPhase.DONE

/**
 * Items of one round that are ticked off with a tap: all but the rests, which the clock counts.
 * At least 1, so a WOD of nothing but rests still has an end.
 */
fun tapsPerRound(workout: Wod): Int = maxOf(1, workout.items.count { it.exerciseId != REST_EXERCISE })

/**
 * Taps a For Time WOD takes from start to finish: one per item of every round, ticked off in
 * order. Null where the clock ends the run or the lifter counts freely.
 */
fun wodSteps(workout: Wod): Int? {
    if (workout.timer.mode != TimerMode.FOR_TIME) return null
    return workout.rounds * tapsPerRound(workout)
}

/**
 * The history entry of a run that ends at [now]. [finished] is true when the timer ran out or the
 * lifter finished a For Time, false when it was stopped early. Null when it ends in the lead-in:
 * nothing was done yet. A For Time WOD is only complete with every step tapped off: a clock stopped
 * after one of three rounds is not a time to beat.
 */
fun timerSession(run: TimerRun, now: Long, finished: Boolean): TimerSession? {
    val view = timerView(run.config, activeTime(run, now))
    if (view.phase == TimerPhase.READY) return null
    val steps = run.wod?.let(::wodSteps)
    val allDone = steps == null || run.taps >= steps
    // In a For Time WOD the taps count items: a round is done with its last item.
    val perRound = if (steps == null) 0 else run.wod?.let(::tapsPerRound) ?: 0
    val tapped = if (countsRoundsByTap(run.config.mode)) run.taps else view.roundsDone
    // A For Time ended before its cap keeps a time under the cap, also within the half second that would
    // round up to it: the cap is how [timerOutcome] tells a run that ran into it.
    val rounded = (view.elapsed.toDouble() / SECOND).roundToLong().toInt()
    val cap = run.config.work.takeIf { run.config.mode == TimerMode.FOR_TIME && it > 0 }
    val seconds = if (cap != null && view.elapsed < cap * SECOND) minOf(rounded, cap - 1) else rounded
    return TimerSession(
        id = run.id,
        name = run.name,
        config = run.config,
        startedAt = run.startedAt,
        finishedAt = now,
        seconds = seconds,
        rounds = if (perRound > 0) minOf(run.taps, steps ?: 0) / perRound else tapped,
        completed = finished && allDone,
        wodId = run.wod?.id,
        splits = run.splits.takeIf { run.wod != null && it.isNotEmpty() }?.toList(),
    )
}

enum class TimerOutcome { COMPLETED, CAPPED, STOPPED }

val OUTCOME_LABELS: Map<TimerOutcome, String> = mapOf(
    TimerOutcome.COMPLETED to "Completed",
    TimerOutcome.CAPPED to "Time cap",
    TimerOutcome.STOPPED to "Stopped early",
)

/**
 * How a timer ended. A For Time that ran into its cap is its own case:
 * the clock ran out, but the work was not finished.
 *
 * The one reader of the session's `completed` flag: every screen, Health Connect and the comparisons ask
 * this (`RulesTest` holds it), so they never disagree about a run.
 */
fun timerOutcome(session: TimerSession): TimerOutcome {
    val config = session.config
    if (config.mode == TimerMode.FOR_TIME && config.work > 0 && session.seconds >= config.work) return TimerOutcome.CAPPED
    return if (session.completed) TimerOutcome.COMPLETED else TimerOutcome.STOPPED
}

/** "8 rounds · 4:00" for the history and the result. */
fun sessionSummary(session: TimerSession): String {
    val time = holdText(session.seconds)
    if (session.rounds == 0 && countsRoundsByTap(session.config.mode)) return time
    return "${session.rounds} ${if (session.rounds == 1) "round" else "rounds"} · $time"
}

/** A row of the history: [sessionSummary], and for a boxing run that counted them its punches: "6 rounds · 22:00 · about 480 punches". */
fun sessionLine(session: TimerSession): String {
    val punches = session.punches?.let { " · about $it ${if (it == 1) "punch" else "punches"}" }.orEmpty()
    return "${sessionSummary(session)}$punches"
}

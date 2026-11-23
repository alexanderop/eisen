package io.github.alexanderop.eisen.domain.elm

import io.github.alexanderop.eisen.domain.DEFAULT_TIMERS
import io.github.alexanderop.eisen.domain.Env
import io.github.alexanderop.eisen.domain.Feel
import io.github.alexanderop.eisen.domain.MAX_OPEN_TIMER_SECONDS
import io.github.alexanderop.eisen.domain.MINUTE_CALLS
import io.github.alexanderop.eisen.domain.TIMER_LEAD_IN_SECONDS
import io.github.alexanderop.eisen.domain.TimerCalls
import io.github.alexanderop.eisen.domain.TimerConfig
import io.github.alexanderop.eisen.domain.TimerMode
import io.github.alexanderop.eisen.domain.TimerPhase
import io.github.alexanderop.eisen.domain.TimerPreset
import io.github.alexanderop.eisen.domain.TimerRun
import io.github.alexanderop.eisen.domain.TimerSession
import io.github.alexanderop.eisen.domain.activeTime
import io.github.alexanderop.eisen.domain.countsRoundsByTap
import io.github.alexanderop.eisen.domain.isTimerOver
import io.github.alexanderop.eisen.domain.timerConfig
import io.github.alexanderop.eisen.domain.timerCues
import io.github.alexanderop.eisen.domain.timerSeconds
import io.github.alexanderop.eisen.domain.timerSession
import io.github.alexanderop.eisen.domain.timerSummary
import io.github.alexanderop.eisen.domain.timerView
import io.github.alexanderop.eisen.domain.TimerView
import io.github.alexanderop.eisen.domain.holdText

/*
 * The interval timers (Tabata, EMOM, AMRAP, For Time). What a run sounds like is worked out once,
 * by `timerCues` in domain/Timer.kt; here it becomes a schedule on the wall clock that the timer's
 * service plays, also with the screen off. Everything is derived from the run's timestamps and `now`.
 */

const val TIMER_SNACKBAR = "timer"
const val PRESET_NAME_LENGTH = 40

/** A second "+1 round" this soon after the last one is a bounce of the thumb, not a round. */
const val TAP_GUARD_MILLIS = 800L

/** Long enough for "Workout complete" after the last beep before the service lets go. */
const val TIMER_TAIL_MILLIS = 3_000L

/** A short pulse when work starts, two when a rest starts, a long one at the end. */
val TIMER_WORK_VIBRATION: List<Long> = listOf(0, 200)
val TIMER_REST_VIBRATION: List<Long> = listOf(0, 150, 120, 150)
val TIMER_END_VIBRATION: List<Long> = listOf(0, 700)

private const val LEAD_IN_MILLIS = TIMER_LEAD_IN_SECONDS * 1000L

/** The config a mode opens with: the one it was last started with, or its default. */
fun lastTimerConfig(model: Model, mode: TimerMode): TimerConfig =
    timerConfig(model.state.timerConfigs[mode] ?: DEFAULT_TIMERS.getValue(mode))

/** The run that is going, paused or not. */
val Model.timerRun: TimerRun? get() = (timer as? TimerState.Running)?.run

/** When [run] runs out by itself on the wall clock, if it keeps going from now. An open For Time stops at its limit. */
fun timerEndsAt(run: TimerRun): Long =
    run.startedAt + run.paused + LEAD_IN_MILLIS + (timerSeconds(run.config) ?: MAX_OPEN_TIMER_SECONDS) * 1000L

internal fun timer(msg: Msg.Timer, model: Model, env: Env): Next {
    val running = model.timer as? TimerState.Running
    // A run that ran out before this Msg came ends first, whatever the Msg was.
    if (running != null && isTimerOver(running.run, env.now) && msg !is Msg.Timer.Release) {
        val (ended, cmds) = finishNaturally(model.copy(now = env.now), running.run, env)
        return ended and cmds
    }
    return when (msg) {
        is Msg.Timer.Open -> model.copy(timerSetup = TimerSetup(lastTimerConfig(model, msg.mode)), nav = model.nav.copy(tab = Tab.TIMER), now = env.now).only()
        is Msg.Timer.OpenPreset -> {
            val preset = model.state.timerPresets.firstOrNull { it.id == msg.id } ?: return model.only()
            model.copy(timerSetup = TimerSetup(timerConfig(preset.config), preset.id, preset.name), nav = model.nav.copy(tab = Tab.TIMER), now = env.now).only()
        }
        is Msg.Timer.StartPreset -> {
            if (model.timer is TimerState.Running) return model.only()
            val opened = timer(Msg.Timer.OpenPreset(msg.id), model, env).first
            if (opened.timerSetup?.presetId != msg.id) return model.only()
            timer(Msg.Timer.Start, opened, env)
        }
        is Msg.Timer.EditName -> {
            val setup = model.timerSetup
            if (setup?.presetId == null) return model.only()
            val name = msg.name.take(PRESET_NAME_LENGTH)
            if (name == setup.name) model.only() else model.copy(timerSetup = setup.copy(name = name)).only()
        }
        is Msg.Timer.Edit -> {
            val setup = model.timerSetup
            if (setup == null || setup.config.mode != msg.config.mode) return model.only()
            val config = timerConfig(msg.config)
            if (config == setup.config) model.only() else model.copy(timerSetup = setup.copy(config = config)).only()
        }
        Msg.Timer.CloseSetup -> if (model.timerSetup == null) model.only() else model.copy(timerSetup = null, now = env.now).only()
        is Msg.Timer.SetCalls -> {
            // The segmented choice only offers these; anything else from a backup becomes the nearest one below.
            val minutes = MINUTE_CALLS.last { it <= maxOf(0, msg.calls.minutes) }
            model.withState { it.copy(settings = it.settings.copy(timerCalls = msg.calls.copy(minutes = minutes))) }.only()
        }
        Msg.Timer.PlaySample -> model and Cmd.PlayTimerSound("halfway")
        is Msg.Timer.SavePreset -> savePreset(model, msg.name, env)
        Msg.Timer.UpdatePreset -> {
            val setup = model.timerSetup ?: return model.only()
            val preset = presetOf(model, setup) ?: return model.only()
            // A name cleared to nothing keeps the one it had.
            val name = setup.name.trim().ifEmpty { preset.name }
            val updated = preset.copy(name = name, config = setup.config)
            if (updated == preset) return (if (name == setup.name) model else model.copy(timerSetup = setup.copy(name = name))).only()
            // Confirmed where it was tapped ("Saved"), not by a snackbar over the Start button.
            model.withState { state -> state.copy(timerPresets = state.timerPresets.map { if (it.id == preset.id) updated else it }) }
                .copy(timerSetup = setup.copy(name = name)).only()
        }
        is Msg.Timer.DeletePreset -> {
            val index = model.state.timerPresets.indexOfFirst { it.id == msg.id }
            if (index == -1) return model.only()
            val preset = model.state.timerPresets[index]
            val setup = model.timerSetup?.let { if (it.presetId == msg.id) it.copy(presetId = null) else it }
            model.withState { it.copy(timerPresets = it.timerPresets.filter { saved -> saved.id != msg.id }) }
                .copy(timerSetup = setup, undo = model.undo.copy(deletedPreset = DeletedPreset(preset, index))) and
                Cmd.Ui(UiEvent.Snackbar("Deleted ${preset.name}", actionLabel = "Undo", onAction = Msg.Timer.UndoDeletePreset, tag = TIMER_SNACKBAR))
        }
        Msg.Timer.UndoDeletePreset -> {
            val deleted = model.undo.deletedPreset ?: return model.only()
            val cleared = model.copy(undo = model.undo.copy(deletedPreset = null))
            if (model.state.timerPresets.any { it.id == deleted.preset.id }) return cleared.only()
            cleared.withState { state ->
                state.copy(timerPresets = state.timerPresets.toMutableList().apply { add(deleted.index.coerceIn(0, size), deleted.preset) })
            }.only()
        }
        Msg.Timer.Start -> {
            val setup = model.timerSetup ?: return model.only()
            if (model.timer is TimerState.Running) return model.only()
            val name = setup.presetId?.let { id -> model.state.timerPresets.firstOrNull { it.id == id }?.name } ?: setup.config.mode.title
            startTimer(model, setup.config, name, env)
        }
        Msg.Timer.Again -> {
            val finished = model.timer as? TimerState.Finished ?: return model.only()
            startTimer(model, timerConfig(finished.session.config), finished.session.name, env)
        }
        Msg.Timer.Pause -> {
            val run = running?.run ?: return model.only()
            if (run.pausedAt != null) return model.only()
            val paused = run.copy(pausedAt = env.now)
            model.copy(timer = running.copy(run = paused), now = env.now) and
                listOf(Cmd.SaveTimer(paused), Cmd.ScheduleTimer(emptyList(), emptyList(), awakeUntil = null), Cmd.ShowTimer(timerNotice(paused, env.now)))
        }
        Msg.Timer.Resume -> {
            val run = running?.run ?: return model.only()
            val pausedAt = run.pausedAt ?: return model.only()
            val resumed = run.copy(pausedAt = null, paused = run.paused + maxOf(0, env.now - pausedAt))
            model.copy(timer = running.copy(run = resumed), now = env.now) and
                listOf(Cmd.SaveTimer(resumed), schedule(model, resumed, env.now), Cmd.ShowTimer(timerNotice(resumed, env.now)))
        }
        Msg.Timer.End -> {
            if (running == null || running.ending) return model.only()
            // Nothing was done yet: it closes without a question and without a history entry.
            if (timerView(running.run.config, activeTime(running.run, env.now)).phase == TimerPhase.READY) closeRun(model, env)
            else model.copy(timer = running.copy(ending = true), now = env.now).only()
        }
        Msg.Timer.KeepGoing -> if (running?.ending == true) model.copy(timer = running.copy(ending = false), now = env.now).only() else model.only()
        Msg.Timer.SaveAndEnd -> {
            val run = running?.run ?: return model.only()
            val session = timerSession(run, env.now, finished = false) ?: return closeRun(model, env)
            fileRun(model, session, env.now) and listOf(Cmd.SaveTimer(null), Cmd.HideTimer)
        }
        Msg.Timer.Discard -> if (running == null) model.only() else closeRun(model, env)
        Msg.Timer.Finish -> {
            val run = running?.run ?: return model.only()
            if (run.config.mode != TimerMode.FOR_TIME) return model.only()
            val session = timerSession(run, env.now, finished = true) ?: return model.only()
            fileRun(model, session, env.now) and listOf(Cmd.SaveTimer(null), Cmd.HideTimer)
        }
        Msg.Timer.Tap -> {
            val run = running?.run ?: return model.only()
            val phase = timerView(run.config, activeTime(run, env.now)).phase
            if (!countsRoundsByTap(run.config.mode) || run.pausedAt != null || phase == TimerPhase.READY) return model.only()
            val last = running.tappedAt
            if (last != null && env.now - last in 0 until TAP_GUARD_MILLIS) return model.only()
            val tapped = run.copy(taps = run.taps + 1)
            val felt = if (model.state.settings.haptics) listOf(Cmd.Ui(UiEvent.Haptic(Feel.TAP))) else emptyList()
            model.copy(timer = running.copy(run = tapped, tappedAt = env.now), now = env.now) and listOf<Cmd>(Cmd.SaveTimer(tapped)) + felt
        }
        Msg.Timer.Untap -> {
            val run = running?.run ?: return model.only()
            if (!countsRoundsByTap(run.config.mode) || run.taps == 0) return model.only()
            val untapped = run.copy(taps = run.taps - 1)
            model.copy(timer = running.copy(run = untapped), now = env.now) and Cmd.SaveTimer(untapped)
        }
        is Msg.Timer.CorrectRounds -> {
            val finished = model.timer as? TimerState.Finished ?: return model.only()
            val session = finished.session
            if (!countsRoundsByTap(session.config.mode)) return model.only()
            val corrected = session.copy(rounds = maxOf(0, session.rounds + msg.by))
            if (corrected == session) return model.only()
            // The result and the history entry are the same session.
            model.withState { state -> state.copy(timerSessions = state.timerSessions.map { if (it.id == session.id) corrected else it }) }
                .copy(timer = TimerState.Finished(corrected)).only()
        }
        Msg.Timer.Tick -> {
            val run = running?.run ?: return model.only()
            val ticked = model.copy(now = env.now)
            // The phase or the round changed: the notification says so.
            val notice = timerNotice(run, env.now)
            if (notice == timerNotice(run, model.now)) ticked.only() else ticked and Cmd.ShowTimer(notice)
        }
        Msg.Timer.Done -> if (model.timer !is TimerState.Finished) model.only()
        else model.copy(timer = null, timerSetup = null, nav = model.nav.copy(tab = Tab.TIMER), now = env.now).only()
        // Another run started meanwhile and owns the service now.
        is Msg.Timer.Release -> if (model.timerRun != null) model.only() else model and Cmd.HideTimer
        is Msg.Timer.DeleteSession -> {
            val index = model.state.timerSessions.indexOfFirst { it.id == msg.id }
            if (index == -1) return model.only()
            val session = model.state.timerSessions[index]
            model.withState { it.copy(timerSessions = it.timerSessions.filter { saved -> saved.id != msg.id }) }
                .copy(undo = model.undo.copy(deletedTimer = DeletedTimerSession(session, index))) and
                Cmd.Ui(UiEvent.Snackbar("Timer deleted", actionLabel = "Undo", onAction = Msg.Timer.UndoDeleteSession, tag = HISTORY_SNACKBAR))
        }
        Msg.Timer.UndoDeleteSession -> {
            val deleted = model.undo.deletedTimer ?: return model.only()
            val cleared = model.copy(undo = model.undo.copy(deletedTimer = null))
            if (model.state.timerSessions.any { it.id == deleted.session.id }) return cleared.only()
            cleared.withState { state ->
                state.copy(timerSessions = state.timerSessions.toMutableList().apply { add(deleted.index.coerceIn(0, size), deleted.session) })
            }.only()
        }
    }
}

/** The saved timer [setup] belongs to, if it still exists. */
fun presetOf(model: Model, setup: TimerSetup): TimerPreset? = setup.presetId?.let { id -> model.state.timerPresets.firstOrNull { it.id == id } }

/**
 * The setup is saved as it is: "Save as preset" reads "Saved" until the numbers change, and
 * "Update preset" only shows once the numbers or the name differ from the saved timer.
 */
fun setupSaved(model: Model, setup: TimerSetup): Boolean = presetOf(model, setup)?.config == setup.config
fun setupChanged(model: Model, setup: TimerSetup): Boolean =
    presetOf(model, setup)?.let { it.config != setup.config || (setup.name.trim().isNotEmpty() && setup.name.trim() != it.name) } == true

private fun savePreset(model: Model, name: String, env: Env): Next {
    val setup = model.timerSetup ?: return model.only()
    val typed = name.trim().take(PRESET_NAME_LENGTH)
    val preset = TimerPreset(env.newId(), typed.ifEmpty { defaultPresetName(setup.config.mode, model.state.timerPresets) }, setup.config)
    // Confirmed where it was tapped ("Saved"), not by a snackbar over the Start button.
    return model.withState { it.copy(timerPresets = it.timerPresets + preset) }.copy(timerSetup = setup.copy(presetId = preset.id, name = preset.name)).only()
}

/** What "Save as preset" offers as the name: "Tabata", or "Tabata 2", "Tabata 3"... when taken. The numbers are in its second line. */
fun defaultPresetName(mode: TimerMode, presets: List<TimerPreset>): String {
    val taken = presets.map { it.name }.toSet()
    if (mode.title !in taken) return mode.title
    return generateSequence(2) { it + 1 }.map { "${mode.title} $it" }.first { it !in taken }
}

/**
 * A new run of [config] from now: a rest that runs ends and a hold timer closes (the run covers the
 * workout, which stays as it is underneath), and the config becomes the one its mode opens with.
 */
private fun startTimer(model: Model, config: TimerConfig, name: String, env: Env): Next {
    val run = TimerRun(id = env.newId(), name = name, config = config, calls = model.state.settings.timerCalls, startedAt = env.now)
    val (stopped, stopCmds) = stopRest(model)
    val started = stopped.copy(timer = TimerState.Running(run), hold = null, restScreen = null, now = env.now)
        .withState { it.copy(timerConfigs = it.timerConfigs + (config.mode to config)) }
    val cmds = stopCmds + listOf(Cmd.SaveTimer(run), schedule(model, run, env.now), Cmd.ShowTimer(timerNotice(run, env.now)))
    // Like the first rest: the countdown on the lock screen needs notifications. The run starts whatever the answer.
    if (started.notifications.granted || started.notifications.asked) return started and cmds
    return started.copy(notifications = started.notifications.copy(asked = true)) and
        listOf(Cmd.MarkNotificationsAsked, Cmd.Ui(UiEvent.RequestNotificationPermission)) + cmds
}

/** The run goes without a history entry: ended in the lead-in, or discarded. The setup it came from shows again. */
private fun closeRun(model: Model, env: Env): Next =
    model.copy(timer = null, now = env.now) and listOf(Cmd.SaveTimer(null), Cmd.HideTimer)

/** [session] goes into the history and its result shows. */
private fun fileRun(model: Model, session: TimerSession, now: Long): Model =
    model.withState { state ->
        if (state.timerSessions.any { it.id == session.id }) state else state.copy(timerSessions = listOf(session) + state.timerSessions)
    }.copy(timer = TimerState.Finished(session), now = now)

/**
 * The run ran out by itself: it is filed as completed with the full time, at the moment it ran out.
 * The service plays the last beep and "Workout complete" before it goes, unless that moment is long past.
 */
private fun finishNaturally(model: Model, run: TimerRun, env: Env): Next {
    val endsAt = timerEndsAt(run)
    val session = timerSession(run, endsAt, finished = true) ?: return closeRun(model, env)
    val filed = fileRun(model, session, env.now)
    val late = env.now - endsAt
    return if (late > TIMER_TAIL_MILLIS) filed and listOf(Cmd.SaveTimer(null), Cmd.HideTimer)
    else filed and listOf(Cmd.SaveTimer(null), Cmd.After(TIMER_TAIL_MILLIS - late, Msg.Timer.Release(run.id)))
}

/** When the phases change, after Start in milliseconds, with the vibration each one gets. */
private fun phaseChanges(config: TimerConfig): List<Pair<Long, List<Long>>> {
    val seconds = timerSeconds(config)
    val changes = mutableListOf<Pair<Long, List<Long>>>()
    when (config.mode) {
        TimerMode.TABATA, TimerMode.EMOM -> {
            var at = 0L
            for (round in 1..config.rounds) {
                changes += at to TIMER_WORK_VIBRATION
                at += config.work * 1000L
                if (config.rest > 0 && round < config.rounds) {
                    changes += at to TIMER_REST_VIBRATION
                    at += config.rest * 1000L
                }
            }
        }
        TimerMode.AMRAP, TimerMode.FOR_TIME -> changes += 0L to TIMER_WORK_VIBRATION
    }
    if (seconds != null) changes += seconds * 1000L to TIMER_END_VIBRATION
    return changes.map { (at, pattern) -> LEAD_IN_MILLIS + at to pattern }
}

/**
 * What the service plays of [run] from [now] on: the cues still ahead, on the wall clock, and the
 * vibrations at the changes of phase when the haptics setting is on. The phone stays awake until the
 * run would end, plus the time its last words need.
 */
internal fun schedule(model: Model, run: TimerRun, now: Long): Cmd.ScheduleTimer {
    val active = activeTime(run, now)
    val zero = run.startedAt + run.paused
    val sounds = timerCues(run.config, run.calls).filter { it.at >= active }.map { TimerSound(zero + it.at, it.sound) }
    val vibrations = if (!model.state.settings.haptics) emptyList()
    else phaseChanges(run.config).filter { it.first >= active }.map { (at, pattern) -> TimerVibration(zero + at, pattern) }
    return Cmd.ScheduleTimer(sounds, vibrations, awakeUntil = timerEndsAt(run) + TIMER_TAIL_MILLIS)
}

/** "Work", "Round 3 of 8", "Paused": what a phase is called on the notification and the run screen. */
fun phaseName(phase: TimerPhase): String = when (phase) {
    TimerPhase.READY -> "Get ready"
    TimerPhase.WORK -> "Work"
    TimerPhase.REST -> "Rest"
    TimerPhase.DONE -> "Complete"
}

/**
 * What comes next, the same text as the PWA: during the lead-in the first phase with its length
 * ("Next: Work 20s", an open For Time "Next: Go"); during work the rest ("Next: Rest 10s") or, without
 * one, the next round; during a rest the next round. "Next: Last round" when the next round is the
 * last, "Last round" inside its work. Nothing in AMRAP and For Time once they run.
 */
fun nextText(run: TimerRun, view: TimerView): String? {
    val config = run.config
    if (view.phase == TimerPhase.READY) {
        return if (config.mode == TimerMode.FOR_TIME && config.work == 0) "Next: Go" else "Next: Work ${holdText(config.work)}"
    }
    if (countsRoundsByTap(config.mode)) return null
    val nextRound = if (view.round + 1 == config.rounds) "Next: Last round" else "Next: Round ${view.round + 1}"
    return when (view.phase) {
        TimerPhase.WORK -> when {
            view.round == config.rounds -> "Last round"
            config.rest > 0 -> "Next: Rest ${holdText(config.rest)}"
            else -> nextRound
        }
        TimerPhase.REST -> nextRound
        else -> null
    }
}

/** "Round 3 of 8" for Tabata and EMOM, null where the lifter counts the rounds. */
fun roundText(run: TimerRun, round: Int): String? = if (countsRoundsByTap(run.config.mode)) null else "Round $round of ${run.config.rounds}"

/**
 * The run's notification at [now]: the timer's name, the phase and round, and a chronometer that counts
 * the phase down (an open For Time counts up). It only changes at a change of phase or a pause, so a tick
 * sends it only then.
 */
fun timerNotice(run: TimerRun, now: Long): TimerNotice {
    val view = timerView(run.config, activeTime(run, now))
    val paused = run.pausedAt != null
    val where = listOfNotNull(phaseName(view.phase), roundText(run, view.round).takeIf { view.phase != TimerPhase.READY }).joinToString(" · ")
    val left = view.phaseLeft
    val base = when {
        paused -> null
        left != null -> now + left
        else -> run.startedAt + run.paused + LEAD_IN_MILLIS
    }
    return TimerNotice(
        title = run.name,
        text = if (paused) "Paused · $where" else where,
        base = base,
        countDown = left != null,
        paused = paused,
        toggleLabel = if (paused) "Resume" else "Pause",
        endLabel = "End",
    )
}

/**
 * The run kept in its file when the process ended. One that ran out meanwhile is filed in the history
 * and its result shows; one that still goes comes back with its sounds from now on.
 */
internal fun restoreTimer(model: Model, saved: TimerRun?, now: Long): Next {
    if (saved == null) return model.only()
    if (isTimerOver(saved, now)) {
        val session = timerSession(saved, timerEndsAt(saved), finished = true) ?: return model and Cmd.SaveTimer(null)
        val filed = fileRun(model, session, now)
        val cmds = mutableListOf<Cmd>(Cmd.SaveTimer(null))
        if (filed.state != model.state) {
            cmds += Cmd.Save(filed.state)
            if (filed.health.enabled) cmds += healthFollows(model.state, filed.state)
        }
        return filed and cmds
    }
    val restored = model.copy(timer = TimerState.Running(saved))
    val sounds = if (saved.pausedAt == null) listOf(schedule(model, saved, now)) else emptyList()
    return restored and sounds + Cmd.ShowTimer(timerNotice(saved, now))
}

/** The calls in one line for the "Voice" row: "Every minute, halfway, last 30 s, rounds and rest, 3-2-1 beeps". */
fun callsSummary(calls: TimerCalls): String {
    val parts = buildList {
        when (calls.minutes) {
            1 -> add("Every minute")
            0 -> Unit
            else -> add("Every ${calls.minutes} min")
        }
        if (calls.halfway) add("halfway")
        if (calls.final) add("last 30 s")
        if (calls.phases) add("rounds and rest")
        if (calls.beeps) add("3-2-1 beeps")
    }
    if (parts.isEmpty()) return "Voice off"
    return parts.joinToString(", ").replaceFirstChar { it.uppercase() }
}

/**
 * The seconds a wheel of the setup offers from [min] to [max]: every 5 s up to a minute, every 15 s up
 * to five, then whole minutes. [current] is always among them, also when it is off that grid.
 */
fun timerWheelSeconds(min: Int, max: Int, current: Int): List<Int> {
    val grid = buildList {
        var at = min
        while (at <= max) {
            add(at)
            at += when {
                at < 60 -> 5
                at < 300 -> 15
                else -> 60
            }
        }
    }
    return (grid + current).distinct().sorted()
}

/** The time cap of a For Time: off, or whole minutes up to an hour. [current] is always among them. */
fun timerCapSeconds(current: Int): List<Int> = (listOf(0) + (1..60).map { it * 60 } + current).distinct().sorted()

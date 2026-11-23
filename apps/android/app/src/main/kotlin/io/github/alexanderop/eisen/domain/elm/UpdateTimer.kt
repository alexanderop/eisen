package io.github.alexanderop.eisen.domain.elm

import io.github.alexanderop.eisen.domain.AppState
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
import io.github.alexanderop.eisen.domain.Wod
import io.github.alexanderop.eisen.domain.WodItem
import io.github.alexanderop.eisen.domain.WodUnit
import io.github.alexanderop.eisen.domain.FindExercise
import io.github.alexanderop.eisen.domain.WeightUnit
import io.github.alexanderop.eisen.domain.itemAmount
import io.github.alexanderop.eisen.domain.itemText
import io.github.alexanderop.eisen.domain.wodDone
import io.github.alexanderop.eisen.domain.wodName
import io.github.alexanderop.eisen.domain.wodStep
import io.github.alexanderop.eisen.domain.findExercise
import io.github.alexanderop.eisen.domain.findWod
import io.github.alexanderop.eisen.domain.holdText
import io.github.alexanderop.eisen.domain.roundItems
import io.github.alexanderop.eisen.domain.roundLines
import io.github.alexanderop.eisen.domain.wodRound
import io.github.alexanderop.eisen.domain.wodRounds
import io.github.alexanderop.eisen.domain.TimerOutcome
import io.github.alexanderop.eisen.domain.bestText
import io.github.alexanderop.eisen.domain.gapText
import io.github.alexanderop.eisen.domain.liveGap
import io.github.alexanderop.eisen.domain.roundsGap
import io.github.alexanderop.eisen.domain.roundsGapText
import io.github.alexanderop.eisen.domain.splitTime
import io.github.alexanderop.eisen.domain.timerOutcome
import io.github.alexanderop.eisen.domain.wodSteps
import io.github.alexanderop.eisen.domain.WodRest
import io.github.alexanderop.eisen.domain.isRest
import io.github.alexanderop.eisen.domain.tapsPerRound
import io.github.alexanderop.eisen.domain.wodRest
import io.github.alexanderop.eisen.domain.TimerCue
import io.github.alexanderop.eisen.domain.VOICE_AFTER_BEEP

/*
 * The interval timers (Tabata, EMOM, AMRAP, For Time). What a run sounds like is worked out once,
 * by `timerCues` in domain/Timer.kt; here it becomes a schedule on the wall clock that the timer's
 * service plays, also with the screen off. Everything is derived from the run's timestamps and `now`.
 */

const val TIMER_SNACKBAR = "timer"
const val PRESET_NAME_LENGTH = 40

/** A second "+1 round" this soon after the last one is a bounce of the thumb, not a round. */
const val TAP_GUARD_MILLIS = 800L

/** The result ignores taps this long after it shows: the thumb that made the last tap may still be coming down. */
const val RESULT_GUARD_MILLIS = 1_000L

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
    val result = model.timer as? TimerState.Finished
    val answer = msg is Msg.Timer.CorrectRounds || msg == Msg.Timer.Again || msg == Msg.Timer.UndoFinish || msg == Msg.Timer.Done || msg == Msg.Timer.DontCount
    if (result != null && answer && env.now - result.at in 0 until RESULT_GUARD_MILLIS) return model.only()
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
            // A WOD starts again as it is now, or as it ran when it was deleted meanwhile.
            val wod = finished.wod?.let { findWod(model.state.wods, it.id) ?: it }
            if (wod != null) startWod(model, wod, env)
            else startTimer(model, timerConfig(finished.session.config), finished.session.name, env)
        }
        Msg.Timer.Pause -> {
            val run = running?.run ?: return model.only()
            if (run.pausedAt != null) return model.only()
            val paused = run.copy(pausedAt = env.now)
            model.copy(timer = running.copy(run = paused), now = env.now) and
                listOf(Cmd.SaveTimer(paused), Cmd.ScheduleTimer(emptyList(), emptyList(), awakeUntil = null), Cmd.ShowTimer(timerNotice(paused, env.now, model.state)))
        }
        Msg.Timer.Resume -> {
            val run = running?.run ?: return model.only()
            val pausedAt = run.pausedAt ?: return model.only()
            val resumed = run.copy(pausedAt = null, paused = run.paused + maxOf(0, env.now - pausedAt))
            model.copy(timer = running.copy(run = resumed), now = env.now) and
                listOf(Cmd.SaveTimer(resumed), schedule(model, resumed, env.now), Cmd.ShowTimer(timerNotice(resumed, env.now, model.state)))
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
            fileRun(model, session, run.wod, env.now) and listOf(Cmd.SaveTimer(null), Cmd.HideTimer)
        }
        Msg.Timer.Discard -> if (running == null) model.only() else closeRun(model, env)
        Msg.Timer.Finish -> {
            val run = running?.run ?: return model.only()
            // A WOD finishes with the tap of its last step, or ends early through "End".
            if (run.config.mode != TimerMode.FOR_TIME || run.wod != null) return model.only()
            val session = timerSession(run, env.now, finished = true) ?: return model.only()
            fileRun(model, session, run.wod, env.now) and listOf(Cmd.SaveTimer(null), Cmd.HideTimer)
        }
        Msg.Timer.Tap -> {
            val run = running?.run ?: return model.only()
            val phase = timerView(run.config, activeTime(run, env.now)).phase
            if (!countsRoundsByTap(run.config.mode) || run.pausedAt != null || phase == TimerPhase.READY) return model.only()
            val wod = run.wod
            if (wod != null && wodDone(wod, run.taps)) return model.only()
            // A rest is counted by the clock: its button is "Skip rest".
            if (runRest(run, env.now) != null) return model.only()
            val last = running.tappedAt
            if (last != null && env.now - last in 0 until TAP_GUARD_MILLIS) return model.only()
            // A WOD keeps the time of every tap, so a later run can be measured against this one.
            val tapped = run.copy(taps = run.taps + 1, splits = if (wod != null) run.splits + splitTime(run, env.now) else run.splits)
            // The tap that ends a round of a For Time is felt more.
            val roundDone = wod != null && wod.timer.mode == TimerMode.FOR_TIME && tapped.taps % tapsPerRound(wod) == 0
            val felt = if (model.state.settings.haptics) listOf(Cmd.Ui(UiEvent.Haptic(if (roundDone) Feel.SUCCESS else Feel.TAP))) else emptyList()
            // The tap of the last step stops the clock: the run is complete now, and its result can take that tap back.
            if (wod != null && wodDone(wod, tapped.taps)) {
                val session = timerSession(tapped, env.now, finished = true) ?: return model.only()
                return fileRun(model, session, wod, env.now, undo = tapped) and listOf<Cmd>(Cmd.SaveTimer(null), Cmd.HideTimer) + felt
            }
            model.copy(timer = running.copy(run = tapped, tappedAt = env.now), now = env.now) and
                listOf<Cmd>(Cmd.SaveTimer(tapped)) + restCuesAfter(model, run, tapped, env.now) + noticeAfterTap(model, run, tapped, env.now) + felt
        }
        Msg.Timer.SkipRest -> {
            val run = running?.run ?: return model.only()
            if (runRest(run, env.now) == null) return model.only()
            val skipped = run.copy(restSkipped = run.taps)
            model.copy(timer = running.copy(run = skipped), now = env.now) and
                listOf<Cmd>(Cmd.SaveTimer(skipped)) + restCuesAfter(model, run, skipped, env.now) + noticeAfterTap(model, run, skipped, env.now)
        }
        Msg.Timer.UndoFinish -> {
            val finished = model.timer as? TimerState.Finished ?: return model.only()
            val ended = finished.undo ?: return model.only()
            // The run carries on from its timestamps, so the clock shows the time that really passed.
            val back = undoneRun(ended)
            // Like a start: the run covers a rest begun meanwhile and a hold timer.
            val (stopped, stopCmds) = stopRest(model)
            stopped.withState { state -> state.copy(timerSessions = state.timerSessions.filter { it.id != finished.session.id }) }
                .copy(timer = TimerState.Running(back), hold = null, restScreen = null, now = env.now) and
                stopCmds + listOf(Cmd.SaveTimer(back), schedule(model, back, env.now), Cmd.ShowTimer(timerNotice(back, env.now, model.state)))
        }
        Msg.Timer.Untap -> {
            val run = running?.run ?: return model.only()
            if (!countsRoundsByTap(run.config.mode) || run.taps == 0) return model.only()
            val untapped = undoneRun(run)
            model.copy(timer = running.copy(run = untapped), now = env.now) and
                listOf<Cmd>(Cmd.SaveTimer(untapped)) + restCuesAfter(model, run, untapped, env.now) + noticeAfterTap(model, run, untapped, env.now)
        }
        is Msg.Timer.CorrectRounds -> {
            val finished = model.timer as? TimerState.Finished ?: return model.only()
            val session = finished.session
            // Erased or replaced by a backup meanwhile: there is no history entry left to correct.
            if (!countsRoundsByTap(session.config.mode) || model.state.timerSessions.none { it.id == session.id }) return model.only()
            val corrected = session.copy(rounds = maxOf(0, session.rounds + msg.by))
            if (corrected == session) return model.only()
            // The result and the history entry are the same session.
            model.withState { state -> state.copy(timerSessions = state.timerSessions.map { if (it.id == session.id) corrected else it }) }
                .copy(timer = finished.copy(session = corrected)).only()
        }
        Msg.Timer.Tick -> {
            val run = running?.run ?: return model.only()
            val ticked = model.copy(now = env.now)
            // The phase or the round changed: the notification says so.
            val notice = timerNotice(run, env.now, model.state)
            if (notice == timerNotice(run, model.now, model.state)) ticked.only() else ticked and Cmd.ShowTimer(notice)
        }
        Msg.Timer.Done -> if (model.timer !is TimerState.Finished) model.only()
        else model.copy(timer = null, timerSetup = null, nav = model.nav.copy(tab = Tab.TIMER), now = env.now).only()
        Msg.Timer.DontCount -> {
            val finished = result ?: return model.only()
            val closed = timer(Msg.Timer.Done, model, env).first
            // Back to the WOD it was a run of, while it still exists.
            val back = finished.wod?.let { findWod(model.state.wods, it.id) }
                ?.let { closed.copy(wods = closed.wods.copy(detail = it.id)) } ?: closed
            val index = model.state.timerSessions.indexOfFirst { it.id == finished.session.id }
            if (index == -1) return back.only()
            back.withState { it.copy(timerSessions = it.timerSessions.filter { saved -> saved.id != finished.session.id }) }
                .copy(undo = back.undo.copy(deletedTimer = DeletedTimerSession(finished.session, index))) and
                Cmd.Ui(UiEvent.Snackbar("Run not counted", actionLabel = "Undo", onAction = Msg.Timer.UndoDeleteSession, tag = TIMER_SNACKBAR))
        }
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
 * A run of a [wod] carries it and leaves that config alone: a WOD is not how the lifter set the mode.
 */
private fun startTimer(model: Model, config: TimerConfig, name: String, env: Env, wod: Wod? = null): Next {
    val run = TimerRun(id = env.newId(), name = name, config = config, calls = model.state.settings.timerCalls, startedAt = env.now, wod = wod)
    val (stopped, stopCmds) = stopRest(model)
    val running = stopped.copy(timer = TimerState.Running(run), hold = null, restScreen = null, now = env.now)
    val started = if (wod != null) running else running.withState { it.copy(timerConfigs = it.timerConfigs + (config.mode to config)) }
    val cmds = stopCmds + listOf(Cmd.SaveTimer(run), schedule(model, run, env.now), Cmd.ShowTimer(timerNotice(run, env.now, model.state)))
    // Like the first rest: the countdown on the lock screen needs notifications. The run starts whatever the answer.
    if (started.notifications.granted || started.notifications.asked) return started and cmds
    return started.copy(notifications = started.notifications.copy(asked = true)) and
        listOf(Cmd.MarkNotificationsAsked, Cmd.Ui(UiEvent.RequestNotificationPermission)) + cmds
}

/** A run of [wod] under its name, straight into the lead-in. Nothing happens while another run goes. */
internal fun startWod(model: Model, wod: Wod, env: Env): Next =
    if (model.timer is TimerState.Running) model.only() else startTimer(model, timerConfig(wod.timer), wodName(wod), env, wod)

/** "Not finished? Undo" and "Back": the run as it was before its last tap, with that tap's time gone too, and no rest skipped. */
fun undoneRun(run: TimerRun): TimerRun {
    val taps = maxOf(0, run.taps - 1)
    return run.copy(taps = taps, splits = run.splits.take(taps), restSkipped = null)
}

/** The rest a For Time WOD is in at [now], unless the lifter skipped it. Paused, it stands still. */
fun runRest(run: TimerRun, now: Long): WodRest? = runRest(run, timerView(run.config, activeTime(run, now)))

fun runRest(run: TimerRun, view: TimerView): WodRest? {
    val wod = run.wod ?: return null
    if (view.phase != TimerPhase.WORK || run.restSkipped == run.taps) return null
    return wodRest(wod, run.splits, view.elapsed / 1000.0)
}

/** When the rest [run] is in at [now] ends, in milliseconds after Start, pauses not counted. */
private fun restEndsAt(run: TimerRun, now: Long): Long? {
    val rest = runRest(run, now) ?: return null
    return LEAD_IN_MILLIS + ((run.splits.last() + rest.total) * 1000).toLong()
}

/** A tap, Back or "Skip rest" that starts or ends a rest plays its end, or no longer: the schedule follows. */
private fun restCuesAfter(model: Model, before: TimerRun, after: TimerRun, now: Long): List<Cmd> =
    if (after.pausedAt != null || restEndsAt(before, now) == restEndsAt(after, now)) emptyList() else listOf(schedule(model, after, now))

/** A tap moves a WOD's round card, and the notification with it. */
private fun noticeAfterTap(model: Model, before: TimerRun, after: TimerRun, now: Long): List<Cmd> {
    val notice = timerNotice(after, now, model.state)
    return if (notice == timerNotice(before, now, model.state)) emptyList() else listOf(Cmd.ShowTimer(notice))
}

/** The run goes without a history entry: ended in the lead-in, or discarded. The setup it came from shows again. */
private fun closeRun(model: Model, env: Env): Next =
    model.copy(timer = null, now = env.now) and listOf(Cmd.SaveTimer(null), Cmd.HideTimer)

/** [session] goes into the history and its result shows, with the WOD it was a run of. */
private fun fileRun(model: Model, session: TimerSession, wod: Wod?, now: Long, undo: TimerRun? = null): Model =
    model.withState { state ->
        if (state.timerSessions.any { it.id == session.id }) state else state.copy(timerSessions = listOf(session) + state.timerSessions)
    }.copy(timer = TimerState.Finished(session, wod, undo, at = now), now = now)

/**
 * The run ran out by itself: it is filed as completed with the full time, at the moment it ran out.
 * The service plays the last beep and "Workout complete" before it goes, unless that moment is long past.
 */
private fun finishNaturally(model: Model, run: TimerRun, env: Env): Next {
    val endsAt = timerEndsAt(run)
    val session = timerSession(run, endsAt, finished = true) ?: return closeRun(model, env)
    val filed = fileRun(model, session, run.wod, env.now)
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
    // The end of a rest sounds like the start of work: three short beeps and a long one, then "Go".
    val restEnd = restEndsAt(run, now)
    val restCues = if (restEnd == null) emptyList() else buildList {
        if (run.calls.beeps) {
            for (before in listOf(3, 2, 1)) add(TimerCue(restEnd - before * 1000L, "beep"))
            add(TimerCue(restEnd, "beep-long"))
        }
        if (run.calls.phases) add(TimerCue(restEnd + VOICE_AFTER_BEEP, "go"))
    }
    val sounds = (timerCues(run.config, run.calls) + restCues).filter { it.at >= active }.sortedBy { it.at }.map { TimerSound(zero + it.at, it.sound) }
    val changes = phaseChanges(run.config) + listOfNotNull(restEnd?.let { it to TIMER_WORK_VIBRATION })
    val vibrations = if (!model.state.settings.haptics) emptyList()
    else changes.filter { it.first >= active }.sortedBy { it.first }.map { (at, pattern) -> TimerVibration(zero + at, pattern) }
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
 * What a WOD's run shows under the ring: [heading], the one count of the round where there is one
 * ("Round 2 of 3"; an AMRAP counts on its button), and the [lines] of what to do. [current]: a For Time
 * ticks the items of its round off in order; the lines before it are done. [upNext]: a rest shows the
 * coming round, "Up next · Round 2 of 8". [exercises]: the exercise of each line, for its picture.
 */
data class RoundCard(
    val heading: String?,
    val lines: List<String>,
    val current: Int? = null,
    val upNext: Boolean = false,
    val exercises: List<String> = emptyList(),
)

/** The round card of [run] at [view], null for a plain timer. Exercises and the unit come from [state]. */
fun roundCard(run: TimerRun, view: TimerView, state: AppState): RoundCard? {
    val wod = run.wod ?: return null
    val find = { id: String -> state.findExercise(id) }
    val unit = state.settings.unit
    val round = wodRound(wod, run.taps, view.round)
    return when (wod.timer.mode) {
        TimerMode.AMRAP -> RoundCard(null, roundLines(wod, round, find, unit), exercises = roundExercises(wod, round, find))
        // Every item keeps its line, so the one to do now is found by its place. The round is in the
        // phase word; a rest shows what comes after it.
        TimerMode.FOR_TIME ->
            if (runRest(run, view) != null) RoundCard(
                if (wod.rounds > 1) "Up next · Round $round of ${wod.rounds}" else "Up next",
                wod.items.map { stepLine(wod, it, round, find, unit) },
                upNext = true,
                exercises = wod.items.map { it.exerciseId },
            )
            else RoundCard(null, wod.items.map { stepLine(wod, it, round, find, unit) }, current = wodStep(wod, run.taps), exercises = wod.items.map { it.exerciseId })
        else ->
            if (view.phase == TimerPhase.REST) {
                val next = view.round + 1
                RoundCard("Up next · Round $next of ${wod.timer.rounds}", roundLines(wod, next, find, unit), upNext = true, exercises = roundExercises(wod, next, find))
            } else RoundCard("Round $round of ${wod.timer.rounds}", roundLines(wod, round, find, unit), exercises = roundExercises(wod, round, find))
    }
}

/** The exercises of [roundLines], line for line. */
private fun roundExercises(wod: Wod, round: Int, find: FindExercise): List<String> =
    roundItems(wod, round).map { it.exerciseId }.filter { find(it) != null }

private fun stepLine(wod: Wod, item: WodItem, round: Int, find: FindExercise, unit: WeightUnit): String =
    itemText(item, itemAmount(wod, item, round), find(item.exerciseId)?.name ?: "Deleted exercise", unit)

/**
 * The big button of a For Time WOD, which says what a tap means: [big] the item to do now, [small]
 * what comes after it ("tap when done · then 20 × Sit-ups", "then rest 2:00", "then round 3", or
 * "finishes the WOD"), [spoken] both for a screen reader.
 */
data class WodButton(val big: String, val small: String, val spoken: String)

fun wodButton(wod: Wod, taps: Int, state: AppState): WodButton {
    if (wod.items.isEmpty()) return WodButton("Done", "tap when finished", "Done")
    val find = { id: String -> state.findExercise(id) }
    val round = wodRound(wod, taps, 1)
    val at = wodStep(wod, taps)
    val line = stepLine(wod, wod.items[at], round, find, state.settings.unit)
    if (taps >= (wodSteps(wod) ?: 0) - 1) return WodButton(line, "tap when done · finishes the WOD", "$line done. That finishes the WOD.")
    val rest = wod.items.drop(at + 1).takeWhile(::isRest).sumOf { if (it.unit == WodUnit.SECONDS) it.amount else 0 }
    val next = when {
        rest > 0 -> "rest ${holdText(rest)}"
        (taps + 1) % tapsPerRound(wod) == 0 -> "round ${round + 1}"
        else -> stepLine(wod, wod.items[wodStep(wod, taps + 1)], round, find, state.settings.unit)
    }
    return WodButton(line, "tap when done · then $next", "$line done. Next: $next.")
}

enum class PaceTone { AHEAD, BEHIND, LEVEL }

const val FIRST_RUN = "First run: this sets your best"

/**
 * The line between the clock and the round card of a For Time or an AMRAP. [best]: "Best 12:40",
 * "Best 18 rounds", or "First run: this sets your best". [gap]: "12s ahead", "2 rounds behind", null
 * while there is nothing to measure against.
 */
data class PaceLine(val best: String, val gap: String? = null, val tone: PaceTone = PaceTone.LEVEL)

/**
 * How [run] at [view] stands against [best], live. Null in an EMOM or a Tabata, which have nothing
 * to beat. In the lead-in only the best: nothing has started yet. Paused, the view and so the gap stand still.
 */
fun paceLine(run: TimerRun, best: TimerSession?, view: TimerView): PaceLine? {
    val mode = run.wod?.timer?.mode
    if (mode != TimerMode.FOR_TIME && mode != TimerMode.AMRAP) return null
    if (best == null) return PaceLine(FIRST_RUN)
    val line = PaceLine("Best ${bestText(best)}")
    if (view.phase == TimerPhase.READY) return line
    val elapsed = view.elapsed / 1000.0
    if (mode == TimerMode.FOR_TIME) {
        val gap = liveGap(best, run.splits, elapsed) ?: return line
        return line.copy(gap = gapText(gap), tone = if (gap < 0) PaceTone.AHEAD else if (gap > 0) PaceTone.BEHIND else PaceTone.LEVEL)
    }
    val rounds = roundsGap(best, run.taps, elapsed) ?: return line
    return line.copy(gap = roundsGapText(rounds), tone = if (rounds > 0) PaceTone.AHEAD else if (rounds < 0) PaceTone.BEHIND else PaceTone.LEVEL)
}

/** A line under the result: [better] when it beat the best. */
data class ResultGap(val text: String, val better: Boolean)

/**
 * Under the result of a finished run, measured against the best before it: "12s faster than your
 * best", "1 round fewer than your best". Null without a best, or for a run that was not finished.
 */
fun resultGap(result: TimerSession, before: TimerSession?): ResultGap? {
    if (before == null || timerOutcome(result) != TimerOutcome.COMPLETED) return null
    if (result.config.mode == TimerMode.FOR_TIME) {
        val diff = result.seconds - before.seconds
        if (diff == 0) return ResultGap("Same time as your best", better = false)
        return ResultGap("${holdText(kotlin.math.abs(diff))} ${if (diff < 0) "faster" else "slower"} than your best", better = diff < 0)
    }
    if (result.config.mode != TimerMode.AMRAP) return null
    val diff = result.rounds - before.rounds
    if (diff == 0) return ResultGap("As many rounds as your best", better = false)
    val count = kotlin.math.abs(diff)
    return ResultGap("$count ${if (count == 1) "round" else "rounds"} ${if (diff > 0) "more" else "fewer"} than your best", better = diff > 0)
}

/**
 * The run's notification at [now]: the timer's name, the phase and round, and a chronometer that counts
 * the phase down (an open For Time counts up). A WOD's adds what to do in this round. It only changes
 * at a change of phase or round or a pause, so a tick sends it only then.
 */
fun timerNotice(run: TimerRun, now: Long, state: AppState): TimerNotice {
    val view = timerView(run.config, activeTime(run, now))
    val paused = run.pausedAt != null
    val card = roundCard(run, view, state)
    val wod = run.wod
    val rest = runRest(run, view)
    val round = when {
        card == null -> roundText(run, view.round)
        card.heading == null && wod != null && wod.timer.mode == TimerMode.FOR_TIME && wod.rounds > 1 -> "Round ${wodRound(wod, run.taps, 1)} of ${wod.rounds}"
        else -> card.heading
    }
    val phase = if (rest != null) "Rest" else phaseName(view.phase)
    val where = listOfNotNull(phase, round.takeIf { view.phase != TimerPhase.READY }).joinToString(" · ")
    // A rest counts down to its end, the For Time around it keeps counting up.
    val restEnd = restEndsAt(run, now)
    val left = view.phaseLeft
    val base = when {
        paused -> null
        restEnd != null -> run.startedAt + run.paused + restEnd
        left != null -> now + left
        else -> run.startedAt + run.paused + LEAD_IN_MILLIS
    }
    return TimerNotice(
        title = run.name,
        text = if (paused) "Paused · $where" else where,
        base = base,
        countDown = restEnd != null || left != null,
        paused = paused,
        toggleLabel = if (paused) "Resume" else "Pause",
        endLabel = "End",
        // Ticking items off, the notification names the one to do now.
        lines = card?.let { it.current?.let { at -> listOfNotNull(it.lines.getOrNull(at)) } ?: it.lines }.orEmpty(),
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
        val filed = fileRun(model, session, saved.wod, now)
        val cmds = mutableListOf<Cmd>(Cmd.SaveTimer(null))
        if (filed.state != model.state) {
            cmds += Cmd.Save(filed.state)
            if (filed.health.enabled) cmds += healthFollows(model.state, filed.state)
        }
        return filed and cmds
    }
    val restored = model.copy(timer = TimerState.Running(saved))
    val sounds = if (saved.pausedAt == null) listOf(schedule(model, saved, now)) else emptyList()
    return restored and sounds + Cmd.ShowTimer(timerNotice(saved, now, model.state))
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

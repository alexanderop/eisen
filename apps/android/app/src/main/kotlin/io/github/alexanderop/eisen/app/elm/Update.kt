package io.github.alexanderop.eisen.app.elm

import io.github.alexanderop.eisen.app.state.AppState
import io.github.alexanderop.eisen.feature.workout.HOLD_LEAD_IN_SECONDS
import io.github.alexanderop.eisen.feature.workout.MAX_HELD_SECONDS
import io.github.alexanderop.eisen.feature.workout.canRate
import io.github.alexanderop.eisen.feature.timer.wod.findWod
import io.github.alexanderop.eisen.feature.workout.rirOf

/*
 * The one place where the model changes. Pure and total: the same Msg on the same Model with the same
 * Env gives the same result, and a Msg that makes no sense right now gives the model back with no Cmds.
 * Each group of Msgs has its own function; the workout delegates to the machine in WorkoutMachine.kt
 * (Elm's nested update) and turns what it asks for into model changes and Cmds.
 */

/** The new model and what the runtime has to do about it. */
typealias Next = Pair<Model, List<Cmd>>

internal fun Model.only(): Next = this to emptyList()

internal infix fun Model.and(cmds: List<Cmd>): Next = this to cmds

internal infix fun Model.and(cmd: Cmd): Next = this to listOf(cmd)

fun update(msg: Msg, model: Model, env: Env): Next {
    val (next, cmds) = when (msg) {
        is Msg.Workout -> touch(msg.event, model, env)
        is Msg.WorkoutScreen -> workoutScreen(msg, model, env)
        is Msg.Nav -> nav(msg, model, env)
        is Msg.Home -> when (msg) {
            Msg.Home.DismissSummary -> model.copy(lastChanges = null).only()
        }
        is Msg.Routines -> routines(msg, model, env)
        is Msg.Exercises -> exercises(msg, model, env)
        is Msg.History -> history(msg, model)
        is Msg.Settings -> settings(msg, model)
        is Msg.Data -> data(msg, model, env)
        is Msg.Hold -> hold(msg, model, env)
        is Msg.Timer -> timer(msg, model, env)
        is Msg.Wods -> wods(msg, model, env)
        is Msg.Rest -> {
            val (answered, cmds) = rest(msg, model, env)
            // A run that waited for the notification question starts with its answer.
            if (msg is Msg.Rest.NotificationPermission && answered.pendingStart != null) {
                val (started, more) = startPending(answered, env)
                started to cmds + more
            } else {
                answered to cmds
            }
        }
        is Msg.Health -> health(msg, model)
        is Msg.App -> app(msg, model, env)
    }
    return follow(model, next, cmds)
}

/** The tag of the "Workout discarded" snackbar, so it goes away when the discard can no longer be undone. */
const val DISCARD_SNACKBAR = "discard"
const val HISTORY_SNACKBAR = "history"
const val SLOT_SNACKBAR = "slot"

/** How long "Workout discarded" offers Undo. Longer than a plain snackbar: a whole workout is at stake. */
const val DISCARD_UNDO_MS = 6_000L

/**
 * What every change brings along, decided once here instead of in each branch: the file is written
 * when the saved state changed, Health Connect follows the history, the widget and the shortcuts
 * follow when what they show changed, the "Workout discarded" snackbar goes once the discard
 * can no longer be undone, and what lies over the workout goes once it no longer belongs to it
 * (see [fitting]).
 */
private fun follow(before: Model, after: Model, cmds: List<Cmd>): Next {
    // The workout belongs to the session. Nothing else may put a different one on disk.
    var model = if (after.state.active == after.session.active) after else after.withState { it.copy(active = after.session.active) }
    val extra = mutableListOf<Cmd>()
    // A backup, an erase, a finished or discarded workout, a closed workout screen, a removed or moved
    // exercise, an ended rest: what lay over the workout goes (a hold timer without logging).
    val fits = fitting(model, model.overlay)
    if (fits != model.overlay) model = model.copy(overlay = fits)
    // A backup or an erase took the WOD away whose detail showed.
    val detail = model.wods.detail
    if (detail != null && findWod(model.state.wods, detail) == null) model = model.wods { it.copy(detail = null) }
    // The draft file holds the builder's draft and the ones kept from before the process ended.
    val kept = draftFile(model.wods.builder)
    if (kept != draftFile(before.wods.builder)) extra += Cmd.SaveDraft(kept)
    if (before.session is Session.Discarded && model.session !is Session.Discarded) {
        extra += Cmd.Ui(UiEvent.DismissSnackbar(DISCARD_SNACKBAR))
    }
    if (model.state !== before.state && model.state != before.state) {
        extra += Cmd.Save(model.state)
        val followed = model.state.history !== before.state.history || model.state.timerSessions !== before.state.timerSessions
        if (model.health.enabled && followed) extra += healthFollows(before.state, model.state)
        val surfaces = refreshHomeSurfaces(model.state)
        if (surfaces != refreshHomeSurfaces(before.state)) extra += surfaces
    }
    return model to cmds + extra
}

/**
 * Health Connect follows the history: a new or edited workout is written, a deleted one removed.
 * A finished workout is new in the history, so it is written here too. So is a restored backup.
 * Finished interval timers are followed the same way.
 */
internal fun healthFollows(before: AppState, after: AppState): List<Cmd> {
    val old = before.history.associateBy { it.id }
    val now = after.history.map { it.id }.toSet()
    val oldTimers = before.timerSessions.associateBy { it.id }
    val nowTimers = after.timerSessions.map { it.id }.toSet()
    val removed = before.history.map { it.id }.filter { it !in now } + before.timerSessions.map { it.id }.filter { it !in nowTimers }
    val changed = after.history.filter { old[it.id] != it }
    val changedTimers = after.timerSessions.filter { oldTimers[it.id] != it }
    return buildList {
        if (removed.isNotEmpty()) add(Cmd.Health(HealthJob.Delete(removed)))
        if (changed.isNotEmpty() || changedTimers.isNotEmpty()) add(Cmd.Health(HealthJob.Upsert(changed, changedTimers)))
    }
}

// The workout

internal fun workout(event: WorkoutEvent, model: Model, env: Env): Next {
    val result = transition(model.session, model.state, event, env)
    if (result.effects.isEmpty() && result.session == model.session && result.state == model.state) return model.only()
    var next = model.copy(session = result.session, state = result.state)
    // Another stage of the workout shows something else (the discard question with its "N min", the home
    // screen after a finish), so the clock is read again for it.
    if (result.session::class != model.session::class) next = next.copy(now = env.now)
    // Undo brings the discarded workout back on screen, where it was.
    if (model.session is Session.Discarded && result.session is Session.Running) next = next.nav { it.copy(workoutOpen = true) }
    if (event is WorkoutEvent.LogReps) next = next.overlay(OverlayEvent.Answered)
    if (event is WorkoutEvent.Move || event is WorkoutEvent.Defer || event is WorkoutEvent.RemoveExercise) next = next.overlay(OverlayEvent.Reordered)
    if (next.settling != null && next.session.active == null) next = next.copy(settling = null)

    val cmds = mutableListOf<Cmd>()
    // The rest goes with the exercise that started it: there is no set left whose screen it could open.
    val restOf = next.rest?.exerciseId
    if (event is WorkoutEvent.RemoveExercise && restOf != null && next.session.active?.exercises?.none { it.id == restOf } == true) {
        val (stopped, more) = stopRest(next)
        next = stopped
        cmds += more
    }
    for (effect in result.effects) {
        when (effect) {
            // The interval timer covers the workout: no rest starts under it.
            is Effect.StartRest -> if (next.timer !is TimerState.Running) {
                val id = next.session.active?.exercises?.getOrNull(effect.exercise)?.id
                val (rested, more) = startRest(next, effect.seconds, effect.note, env, id, effect.set)
                // Every rest opens its screen, also after one that was minimised.
                val screen = id?.let {
                    if (rested.state.settings.restFullScreen) RestScreen.Shown(effect.exercise, effect.set, it) else RestScreen.Minimised(effect.exercise, effect.set, it)
                }
                next = if (screen == null) rested else rested.overlay(OverlayEvent.Rest(screen))
                cmds += more
            }
            Effect.StopRest -> {
                val (stopped, more) = stopRest(next)
                next = stopped
                cmds += more
            }
            // No rest after the last set of the workout. The screen still opens, without a clock, to rate that set.
            is Effect.LastSet -> {
                val log = next.session.active?.exercises?.getOrNull(effect.exercise)
                val settings = next.state.settings
                if (log != null && settings.trackRir && settings.restFullScreen && canRate(log, effect.set)) {
                    next = next.overlay(OverlayEvent.Rest(RestScreen.SetDone(effect.exercise, effect.set, log.id)))
                }
            }
            is Effect.Haptic -> if (next.state.settings.haptics) cmds += Cmd.Ui(UiEvent.Haptic(effect.kind))
            is Effect.AskReps -> {
                val log = next.session.active?.exercises?.getOrNull(effect.exercise)
                if (log != null) next = next.overlay(OverlayEvent.AskReps(RepsQuestion(effect.exercise, effect.set, log.id, effect.reps)))
            }
            is Effect.OpenHold -> {
                val log = next.session.active?.exercises?.getOrNull(effect.exercise)
                if (log != null && next.timer !is TimerState.Running) {
                    val hold = HoldTimer(effect.exercise, effect.set, log.id, log.targetReps, startsAt = env.now + HOLD_LEAD_IN_SECONDS * 1000L)
                    next = next.nav { it.copy(workoutOpen = true) }.overlay(OverlayEvent.Hold(hold)).copy(now = env.now)
                }
            }
            // A new workout forgets the last summary and the ticked warmups, and shows itself.
            is Effect.Started -> next = next.nav { it.copy(workoutOpen = true) }.copy(lastChanges = null, warmupDone = emptySet())
            is Effect.Saved -> next = next.nav { it.copy(workoutOpen = false) }.copy(lastChanges = effect.changes)
            is Effect.DiscardedWorkout -> {
                next = next.nav { it.copy(workoutOpen = false) }
                cmds += Cmd.Ui(
                    UiEvent.Snackbar(
                        text = "Workout discarded",
                        actionLabel = "Undo",
                        onAction = Msg.Workout(WorkoutEvent.Undo),
                        // Gone without Undo: the discard is final and the unit can change again.
                        onDismiss = Msg.Workout(WorkoutEvent.Forget),
                        millis = DISCARD_UNDO_MS,
                        tag = DISCARD_SNACKBAR,
                    ),
                )
            }
        }
    }
    // A chip on the "set done" screen closes it a moment later, so the lifter sees the choice land.
    val screen = next.restScreen
    if (event is WorkoutEvent.RateSet && event.rir != null && screen is RestScreen.SetDone && screen.exercise == event.exercise && screen.set == event.set) {
        cmds += Cmd.After(RATED_CLOSE_MS, Msg.Rest.Rated(screen, event.rir))
    }
    return next and cmds
}

/** How long the "set done" screen stays after a rating. */
const val RATED_CLOSE_MS = 400L

/**
 * How long the exercise tapped last holds its place before it rises to the top (or sinks, once
 * finished). The tap after a full set is how missed reps are entered: the card must not jump away
 * under the thumb. A manual order wins over the rising, see `order` in `app/elm/WorkoutMachine.kt`.
 */
const val SETTLE_MS = 2_500L

/**
 * [workout], plus the settling of the exercise the event worked on: a tap on a set with a target, or
 * the count of a max-reps set (its tap only opens the entry). The exercise settling before takes its
 * place at once, so only one card holds still at a time. Each tap starts the wait again.
 */
private fun touch(event: WorkoutEvent, model: Model, env: Env): Next {
    val (worked, cmds) = workout(event, model, env)
    val logs = (model.session as? Session.Running)?.run?.workout?.exercises
    val touched = when (event) {
        // A timed set settles when the timer logs it or a tap clears it, not when the tap opens the timer.
        is WorkoutEvent.TapSet -> logs?.getOrNull(event.exercise)?.takeIf {
            if (it.timed) it.sets.getOrNull(event.set) != null else it.targetReps != null
        }
        is WorkoutEvent.LogReps -> logs?.getOrNull(event.exercise)
        is WorkoutEvent.LogHold -> logs?.getOrNull(event.exercise)
        // Listed one by one, so a new event does not compile until it says whether it settles an exercise.
        // A correction or a rating from the rest screen changes no set from open to logged, so nothing moves.
        // Asking for the count of a max-reps set logs nothing yet.
        WorkoutEvent.Start, is WorkoutEvent.EnterReps, is WorkoutEvent.CorrectReps, is WorkoutEvent.RateSet, is WorkoutEvent.SetWeight, is WorkoutEvent.SetWorkingWeight, is WorkoutEvent.AddExercise,
        is WorkoutEvent.RemoveExercise, is WorkoutEvent.Defer, is WorkoutEvent.Move, is WorkoutEvent.Rise,
        WorkoutEvent.RequestFinish, WorkoutEvent.RequestDiscard, WorkoutEvent.Confirm, WorkoutEvent.Cancel,
        WorkoutEvent.Undo, WorkoutEvent.Forget,
        -> null
    }
    if (touched == null || worked === model || worked.session !is Session.Running) return worked and cmds
    val id = touched.id
    val (settled, risen) = if (worked.settling?.id != id) settle(worked, env) else worked.only()
    return settled.copy(settling = Settling(id, env.now)) and cmds + risen + Cmd.After(SETTLE_MS, Msg.WorkoutScreen.Settle(id, env.now))
}

/** The exercise tapped last takes its place: it rises to the top when it is started and not finished. */
private fun settle(model: Model, env: Env): Next {
    val id = model.settling?.id ?: return model.only()
    return workout(WorkoutEvent.Rise(id), model.copy(settling = null), env)
}

private fun workoutScreen(msg: Msg.WorkoutScreen, model: Model, env: Env): Next = when (msg) {
    is Msg.WorkoutScreen.ToggleWarmup -> {
        val done = model.warmupDone
        model.copy(warmupDone = if (msg.key in done) done - msg.key else done + msg.key).only()
    }
    Msg.WorkoutScreen.CloseRepsEntry -> model.overlay(OverlayEvent.CloseReps).only()
    is Msg.WorkoutScreen.PickReps -> pickReps(msg, model, env)
    is Msg.WorkoutScreen.Settle ->
        if (model.settling == Settling(msg.id, msg.since)) settle(model, env) else model.only()
    Msg.WorkoutScreen.SettleNow -> settle(model, env)
    is Msg.WorkoutScreen.MoveBy -> moveBy(msg, model, env)
}

/**
 * A number picked on a set: [WorkoutEvent.LogReps] does the logging. The first pick also tells the
 * workout screen that the lifter knows the gesture, so its hint goes, for good.
 */
private fun pickReps(msg: Msg.WorkoutScreen.PickReps, model: Model, env: Env): Next {
    if (model.session !is Session.Running) return model.only()
    val (picked, cmds) = touch(WorkoutEvent.LogReps(msg.exercise, msg.set, msg.reps), model, env)
    return (if (picked.state.pickedReps) picked else picked.withState { it.copy(pickedReps = true) }) and cmds
}

/** Moves an open exercise one place among the open ones, counted in the order the screen shows once settled. */
private fun moveBy(msg: Msg.WorkoutScreen.MoveBy, model: Model, env: Env): Next {
    if (model.session !is Session.Running) return model.only()
    val (settled, risen) = settle(model, env)
    val run = (settled.session as? Session.Running)?.run ?: return settled and risen
    val open = order(run, settling = null).filterNot { it.done }
    val from = open.indexOfFirst { it.log.id == msg.id }
    if (from == -1) return settled and risen
    val (moved, cmds) = workout(WorkoutEvent.Move(from, from + msg.step), settled, env)
    return moved and risen + cmds
}

// Navigation

private fun nav(msg: Msg.Nav, model: Model, env: Env): Next {
    val nav = model.nav
    // The Timer tab tapped again while it shows: back to the top of its page. A builder with changes asks first.
    if (msg is Msg.Nav.SelectTab && msg.tab == Tab.TIMER && nav.tab == Tab.TIMER && model.timer == null) {
        val top = model.wods { it.copy(detail = null) }.nav { it.copy(timerTop = it.timerTop + 1) }.copy(timerSetup = null, now = env.now)
        return if (model.wods.draft != null && nav.timerPage == TimerPage.WODS) wods(Msg.Wods.Leave, top, env) else top.only()
    }
    if (msg is Msg.Nav.ShowTimerPage) {
        if (msg.page == nav.timerPage) return model.only()
        return model.nav { it.copy(timerPage = msg.page) }.copy(now = env.now) and Cmd.SaveTimerPage(msg.page)
    }
    val next = when (msg) {
        is Msg.Nav.ShowTimerPage -> nav
        is Msg.Nav.SelectTab -> nav.copy(tab = msg.tab)
        is Msg.Nav.ShowHistory -> nav.copy(history = msg.view)
        Msg.Nav.OpenWorkout -> nav.copy(workoutOpen = true)
        Msg.Nav.CloseWorkout -> nav.copy(workoutOpen = false)
        is Msg.Nav.OpenRoutine -> if (model.state.routines.any { it.id == msg.id }) nav.copy(routine = msg.id) else nav
        Msg.Nav.CloseRoutine -> nav.copy(routine = null)
        // The interval timer over everything asks before it ends, its result goes back to the Timer tab.
        // A hold timer closes first, without logging, and the rest screen goes down to the bar. Then the
        // history's sheet "What was it?", the workout (it keeps running), then the routine editor, a boxing round's sheet or the timer's setup, then any tab goes home.
        Msg.Nav.Back -> when {
            model.timer is TimerState.Running -> return timer(if ((model.timer as TimerState.Running).ending) Msg.Timer.KeepGoing else Msg.Timer.End, model, env)
            model.timer is TimerState.Finished -> return timer(Msg.Timer.Done, model, env)
            model.hold != null -> return model.overlay(OverlayEvent.HoldClosed).only()
            showsRestScreen(model) -> return rest(Msg.Rest.Minimise, model, env)
            model.activitySheet != null -> return history(Msg.History.CloseActivity, model)
            showsWorkout(model) -> nav.copy(workoutOpen = false)
            nav.tab == Tab.WORKOUTS && openRoutine(model) != null -> nav.copy(routine = null)
            nav.tab == Tab.TIMER && model.timerSetup?.sheet != null -> return timer(Msg.Timer.CloseRound, model, env)
            nav.tab == Tab.TIMER && model.timerSetup != null -> return timer(Msg.Timer.CloseSetup, model, env)
            // The builder asks before it loses changes; the detail goes back to the list.
            nav.tab == Tab.TIMER && nav.timerPage == TimerPage.WODS && model.wods.draft != null -> return wods(Msg.Wods.Leave, model, env)
            nav.tab == Tab.TIMER && nav.timerPage == TimerPage.WODS && model.wods.detail != null -> return wods(Msg.Wods.Close, model, env)
            nav.tab != Tab.HOME -> nav.copy(tab = Tab.HOME)
            else -> nav
        }
        is Msg.Nav.Restore -> Navigation(
            tab = msg.tab,
            history = msg.history,
            workoutOpen = msg.workoutOpen,
            routine = msg.routine?.takeIf { id -> model.state.routines.any { it.id == id } },
            timerPage = nav.timerPage,
        )
    }
    // Whatever comes on screen reads the clock, so Home and Progress date themselves with today even
    // when nothing ticked. Selecting the tab that shows does too. A move that changes nothing is ignored.
    return when {
        next != nav -> model.copy(nav = next, now = env.now)
        msg is Msg.Nav.SelectTab -> model.copy(now = env.now)
        else -> model
    }.only()
}

// The hold timer

/** A cue this late is not given: the hold started or ended while the app was away. */
private const val HOLD_CUE_LATE_MILLIS = 2_000L

/** One short pulse when the hold starts. */
val HOLD_START_VIBRATION: List<Long> = listOf(0, 150)

/** Three pulses when a countdown ends, like the end of a rest. */
val HOLD_END_VIBRATION: List<Long> = listOf(0, 300, 120, 300, 120, 300)

private fun holdCues(model: Model, beeps: Int, vibration: List<Long>): List<Cmd> = buildList {
    if (model.state.settings.sound) add(Cmd.Beep(beeps))
    if (model.state.settings.haptics) add(Cmd.Vibrate(vibration))
}

private fun hold(msg: Msg.Hold, model: Model, env: Env): Next {
    val hold = model.hold ?: return model.only()
    return when (msg) {
        Msg.Hold.SkipLeadIn ->
            if (hold.started || env.now >= hold.startsAt) model.only()
            else holdClock(model.hold { it.copy(startsAt = env.now) }.copy(now = env.now), env)
        Msg.Hold.Stop -> when {
            // Before the hold there is only Cancel and the skip.
            env.now < hold.startsAt -> model.only()
            // Its end passed before the tick that logs it: the whole target counts.
            env.now >= holdEndsAt(hold) -> holdClock(model.copy(now = env.now), env)
            else -> {
                val seconds = heldSeconds(hold, env.now)
                val closed = model.overlay(OverlayEvent.HoldClosed).copy(now = env.now)
                if (seconds < 1) closed.only() else touch(WorkoutEvent.LogHold(hold.exercise, hold.set, seconds), closed, env)
            }
        }
        Msg.Hold.Cancel -> model.overlay(OverlayEvent.HoldClosed).copy(now = env.now).only()
        Msg.Hold.Tick -> holdClock(model.copy(now = env.now), env)
    }
}

/**
 * Moves the hold timer to [Env.now]: the start of the hold is signalled once, and a hold whose end
 * has come is logged in full and signalled. Late signals are left out, so a hold that ended while
 * the app was away is logged without a beep on return.
 */
private fun holdClock(model: Model, env: Env): Next {
    val hold = model.hold ?: return model.only()
    val cmds = mutableListOf<Cmd>()
    var next = model
    if (!hold.started && env.now >= hold.startsAt) {
        next = next.hold { it.copy(started = true) }
        if (env.now - hold.startsAt <= HOLD_CUE_LATE_MILLIS) cmds += holdCues(model, beeps = 1, HOLD_START_VIBRATION)
    }
    val end = holdEndsAt(hold)
    if (env.now < end) return next and cmds
    if (env.now - end <= HOLD_CUE_LATE_MILLIS) cmds += holdCues(model, beeps = 3, HOLD_END_VIBRATION)
    val (logged, more) = touch(WorkoutEvent.LogHold(hold.exercise, hold.set, hold.target ?: MAX_HELD_SECONDS), next.overlay(OverlayEvent.HoldClosed), env)
    return logged and cmds + more
}

// The rest timer

/** Ten seconds before the end the lifter hears a warning. */
const val WARN_SECONDS = 10

/** A shorter rest would get the warning right after it started. */
const val WARN_MIN_REST_SECONDS = 20
const val EXTEND_SECONDS = 30

/** What "−15s" and "+15s" on the rest screen take away and add. */
const val ADJUST_SECONDS = 15

/** Long enough for the spoken cue to finish before the countdown service lets go. */
const val CUE_MILLIS = 4_000L

/** An "over" alarm this much early still counts: the system may fire a little before the time. */
private const val OVER_EARLY_MILLIS = 500L

/** The "ten seconds" alarm may come a little late; after this it is no warning any more. */
private const val TEN_LATE_MILLIS = 2_000L

/** Three strong pulses: felt in a pocket, different from any tap. */
val REST_OVER_VIBRATION: List<Long> = listOf(0, 300, 120, 300, 120, 300)

private fun startRest(model: Model, seconds: Int, note: String, env: Env, exerciseId: String?, set: Int): Next {
    val rest = RestState(env.now, env.now + seconds * 1000L, note, exerciseId, set.takeIf { exerciseId != null })
    val (started, cmds) = beginRest(model, rest, warn = seconds >= WARN_MIN_REST_SECONDS, env)
    // The first rest asks to allow notifications, so the next ones count down on the lock screen.
    // The rest starts whatever the answer is.
    if (started.notifications.granted || started.notifications.asked) return started and cmds
    return started.notifications { it.copy(asked = true) } and
        listOf(Cmd.MarkNotificationsAsked, Cmd.Ui(UiEvent.RequestNotificationPermission)) + cmds
}

/** Runs [rest]: kept for after the process ends, alarms set, countdown shown, an old "over" notice gone. */
private fun beginRest(model: Model, rest: RestState, warn: Boolean, env: Env): Next {
    val tenAt = rest.endsAt - WARN_SECONDS * 1000L
    return model.copy(rest = rest, now = env.now) and listOf(
        Cmd.SaveRest(rest),
        Cmd.ScheduleRestAlarms(tenAt = tenAt.takeIf { warn && it > env.now }, overAt = rest.endsAt),
        Cmd.HideRestOver,
        Cmd.ShowRestCountdown(restCountdown(rest, env.now)),
    )
}

internal fun stopRestCmds(): List<Cmd> = listOf(Cmd.SaveRest(null), Cmd.CancelRestAlarms, Cmd.HideRestCountdown, Cmd.HideRestOver)

internal fun stopRest(model: Model): Next = if (model.rest == null) model.only() else model.copy(rest = null) and stopRestCmds()

private fun rest(msg: Msg.Rest, model: Model, env: Env): Next {
    val current = model.rest
    val sound = model.state.settings.sound
    return when (msg) {
        Msg.Rest.Extend -> {
            if (current == null) return model.only()
            beginRest(model, current.copy(endsAt = maxOf(current.endsAt, env.now) + EXTEND_SECONDS * 1000L), warn = true, env)
        }
        Msg.Rest.Dismiss -> stopRest(model)
        // Moved like an extend: the alarms and the notification follow the new end.
        Msg.Rest.Shorten -> {
            if (current == null || env.now >= current.endsAt) return model.only()
            beginRest(model, current.copy(endsAt = maxOf(env.now, current.endsAt - ADJUST_SECONDS * 1000L)), warn = true, env)
        }
        Msg.Rest.Lengthen -> {
            if (current == null) return model.only()
            beginRest(model, current.copy(endsAt = maxOf(current.endsAt, env.now) + ADJUST_SECONDS * 1000L), warn = true, env)
        }
        Msg.Rest.Minimise -> model.overlay(OverlayEvent.Minimise).only()
        is Msg.Rest.Rated -> {
            // Only while the chip that sent it is still the rating: another choice made since keeps the screen.
            val log = model.session.active?.exercises?.getOrNull(msg.screen.exercise)
            if (log != null && rirOf(log, msg.screen.set) == msg.rir) model.overlay(OverlayEvent.Rated(msg.screen)).only() else model.only()
        }
        Msg.Rest.Open -> {
            val opened = model.overlay(OverlayEvent.Open)
            if (opened === model) model.only() else opened.copy(now = env.now).only()
        }
        Msg.Rest.AlarmTen -> {
            val left = (current ?: return model.only()).endsAt - env.now
            if (sound && left in 1..(WARN_SECONDS * 1000L + TEN_LATE_MILLIS)) model and Cmd.PlayCue(Cue.TEN_SECONDS) else model.only()
        }
        Msg.Rest.AlarmOver -> {
            if (current == null) return model.only()
            // Extended meanwhile: the old alarm is not the end any more.
            if (env.now < current.endsAt - OVER_EARLY_MILLIS) return model.only()
            val cmds = buildList {
                if (sound) add(Cmd.PlayCue(Cue.REST_OVER))
                add(Cmd.Vibrate(REST_OVER_VIBRATION))
                if (model.notifications.granted) add(Cmd.ShowRestOver(restOver(current)))
                add(Cmd.After(CUE_MILLIS, Msg.Rest.CueFinished(current)))
            }
            model.copy(now = env.now) and cmds
        }
        // Still the same rest: nothing counts down any more, so the service can go.
        is Msg.Rest.CueFinished -> if (model.rest == msg.rest) model and Cmd.HideRestCountdown else model.only()
        Msg.Rest.Tick -> if (current == null) model.only() else model.copy(now = env.now).only()
        is Msg.Rest.NotificationPermission -> {
            val allowed = model.notifications { it.copy(granted = msg.granted) }
            // The rest started before the answer came, so it has no notification yet. Running it again
            // with nothing added shows one. Not for short or ending rests, whose cues it would change.
            if (msg.granted && current != null && current.endsAt - current.startedAt >= WARN_MIN_REST_SECONDS * 1000L &&
                current.endsAt - env.now > (WARN_SECONDS + 1) * 1000L
            ) {
                beginRest(allowed, current.copy(endsAt = maxOf(current.endsAt, env.now)), warn = true, env)
            } else {
                allowed.only()
            }
        }
    }
}

// The app and its ways in

private fun app(msg: Msg.App, model: Model, env: Env): Next = when (msg) {
    // Health Connect may have been installed, updated or removed while the app was away.
    // A hold that ended while the app was away is logged now.
    is Msg.App.Foreground -> {
        val shown = model.notifications { it.copy(granted = msg.notificationsGranted) }.copy(visible = true, now = env.now)
        val (checked, cmds) = holdClock(shown, env)
        checked and listOf<Cmd>(Cmd.Health(HealthJob.CheckAvailability)) + cmds
    }
    Msg.App.Background -> model.copy(visible = false).only()
    is Msg.App.LaunchWorkout -> launch(msg, model, env)
}

private fun launch(msg: Msg.App.LaunchWorkout, model: Model, env: Env): Next {
    if (msg.restored || msg.fromHistory) return model.only()
    // Resume: the running workout just shows.
    if (model.session.active != null) return model.nav { it.copy(workoutOpen = true) }.only()
    val routines = model.state.routines
    if (routines.isEmpty()) return model.only()
    val id = msg.routineId
    val selected = if (id != null && routines.any { it.id == id }) model.withState { it.copy(nextRoutineId = id) } else model
    // A workout whose exercises were all deleted cannot start. The app opens on the home screen.
    return workout(WorkoutEvent.Start, selected, env)
}

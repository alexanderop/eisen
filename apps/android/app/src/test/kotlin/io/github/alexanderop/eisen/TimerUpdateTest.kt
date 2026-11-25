package io.github.alexanderop.eisen

import io.github.alexanderop.eisen.app.elm.restScreen
import io.github.alexanderop.eisen.feature.timer.DEFAULT_TIMERS
import io.github.alexanderop.eisen.feature.timer.DEFAULT_TIMER_CALLS
import io.github.alexanderop.eisen.app.state.STATE_VERSION
import io.github.alexanderop.eisen.app.elm.Session
import io.github.alexanderop.eisen.feature.timer.TimerConfig
import io.github.alexanderop.eisen.feature.timer.TimerMode
import io.github.alexanderop.eisen.feature.timer.TimerRun
import io.github.alexanderop.eisen.feature.timer.TimerSession
import io.github.alexanderop.eisen.app.elm.WorkoutEvent
import io.github.alexanderop.eisen.feature.progress.calendarWeeks
import io.github.alexanderop.eisen.app.state.decodeState
import io.github.alexanderop.eisen.app.state.encodeState
import io.github.alexanderop.eisen.app.state.freshState
import io.github.alexanderop.eisen.feature.timer.timerCues
import io.github.alexanderop.eisen.app.elm.Cmd
import io.github.alexanderop.eisen.app.elm.Flags
import io.github.alexanderop.eisen.app.elm.HealthJob
import io.github.alexanderop.eisen.app.elm.HealthModel
import io.github.alexanderop.eisen.app.elm.HistoryView
import io.github.alexanderop.eisen.app.elm.Msg
import io.github.alexanderop.eisen.app.elm.Notifications
import io.github.alexanderop.eisen.app.elm.Sub
import io.github.alexanderop.eisen.app.elm.TIMER_END_VIBRATION
import io.github.alexanderop.eisen.app.elm.TIMER_REST_VIBRATION
import io.github.alexanderop.eisen.app.elm.TIMER_TAIL_MILLIS
import io.github.alexanderop.eisen.app.elm.TIMER_WORK_VIBRATION
import io.github.alexanderop.eisen.app.elm.Tab
import io.github.alexanderop.eisen.app.elm.TimerSetup
import io.github.alexanderop.eisen.app.elm.TimerSound
import io.github.alexanderop.eisen.app.elm.TimerState
import io.github.alexanderop.eisen.app.elm.TimerVibration
import io.github.alexanderop.eisen.app.elm.UiEvent
import io.github.alexanderop.eisen.app.elm.callsSummary
import io.github.alexanderop.eisen.app.elm.handlesBack
import io.github.alexanderop.eisen.app.elm.init
import io.github.alexanderop.eisen.app.elm.subscriptions
import io.github.alexanderop.eisen.app.elm.timerCapSeconds
import io.github.alexanderop.eisen.app.elm.timerNotice
import io.github.alexanderop.eisen.app.elm.timerRun
import io.github.alexanderop.eisen.app.elm.timerWheelSeconds
import io.github.alexanderop.eisen.feature.timer.TimerPreset
import io.github.alexanderop.eisen.feature.timer.timerView
import io.github.alexanderop.eisen.app.elm.TAP_GUARD_MILLIS
import io.github.alexanderop.eisen.app.elm.defaultPresetName
import io.github.alexanderop.eisen.app.elm.nextText
import io.github.alexanderop.eisen.app.elm.setupChanged
import io.github.alexanderop.eisen.app.elm.setupSaved
import androidx.health.connect.client.records.ExerciseSessionRecord
import io.github.alexanderop.eisen.feature.health.platform.timerSessionFields
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

/** The interval timers through `update`: setup, run, result, presets, history and the run kept across the end of the process. */
class TimerUpdateTest {
    private val short = TimerConfig(TimerMode.TABATA, work = 20, rest = 10, rounds = 3)

    /** The Tabata setup with [short] open, as the clock reads [STARTED_AT]. */
    private fun setup(): Step = stepOf().send(Msg.Timer.Open(TimerMode.TABATA), now = STARTED_AT).send(Msg.Timer.Edit(short), now = STARTED_AT)

    /** [short] started at [STARTED_AT] with the id "t1". */
    private fun started(): Step = setup().send(Msg.Timer.Start, now = STARTED_AT, id = "t1")

    private val Step.timed: TimerRun get() = model.timerRun!!
    private val Step.schedule: Cmd.ScheduleTimer get() = cmds.filterIsInstance<Cmd.ScheduleTimer>().single()

    @Test fun `a mode opens with the config it was last started with`() {
        val open = stepOf().send(Msg.Timer.Open(TimerMode.EMOM))
        assertEquals(TimerSetup(DEFAULT_TIMERS.getValue(TimerMode.EMOM)), open.model.timerSetup)
        assertEquals(Tab.TIMER, open.model.nav.tab)
        val ran = started()
        assertEquals(short, ran.state.timerConfigs[TimerMode.TABATA])
        assertTrue(Cmd.Save(ran.state) in ran.cmds)
        val back = ran.send(Msg.Timer.Discard).send(Msg.Timer.CloseSetup).send(Msg.Timer.Open(TimerMode.TABATA))
        assertEquals(short, back.model.timerSetup?.config)
    }

    @Test fun `a wheel is brought into the limits of its mode and cannot change the mode`() {
        val edited = setup().send(Msg.Timer.Edit(short.copy(work = 1, rounds = 99)))
        assertEquals(short.copy(work = 5, rounds = 60), edited.model.timerSetup?.config)
        assertIgnored(edited, Msg.Timer.Edit(DEFAULT_TIMERS.getValue(TimerMode.EMOM)))
        assertIgnored(stepOf(), Msg.Timer.Edit(short))
    }

    @Test fun `Start runs the timer, plays its schedule on the wall clock and shows its notification`() {
        val step = started()
        val run = step.timed
        assertEquals(TimerRun("t1", "Tabata", short, DEFAULT_TIMER_CALLS, startedAt = STARTED_AT), run)
        assertTrue(Cmd.SaveTimer(run) in step.cmds)
        assertTrue(Cmd.ShowTimer(timerNotice(run, STARTED_AT, freshState())) in step.cmds)
        val schedule = step.schedule
        assertEquals(timerCues(short, DEFAULT_TIMER_CALLS).map { TimerSound(STARTED_AT + it.at, it.sound) }, schedule.sounds)
        // Work, rest, work, rest, work, the end: each felt.
        assertEquals(
            listOf(
                TimerVibration(STARTED_AT + 10_000, TIMER_WORK_VIBRATION),
                TimerVibration(STARTED_AT + 30_000, TIMER_REST_VIBRATION),
                TimerVibration(STARTED_AT + 40_000, TIMER_WORK_VIBRATION),
                TimerVibration(STARTED_AT + 60_000, TIMER_REST_VIBRATION),
                TimerVibration(STARTED_AT + 70_000, TIMER_WORK_VIBRATION),
                TimerVibration(STARTED_AT + 90_000, TIMER_END_VIBRATION),
            ),
            schedule.vibrations,
        )
        assertEquals(STARTED_AT + 90_000 + TIMER_TAIL_MILLIS, schedule.awakeUntil)
        assertEquals(setOf<Sub>(Sub.TimerClock(STARTED_AT)), subscriptions(step.model))
        assertEquals("Get ready", timerNotice(run, STARTED_AT, freshState()).text)
        assertEquals("Rest · Round 1 of 3", timerNotice(run, STARTED_AT + 35_000, freshState()).text)
    }

    @Test fun `without haptics a run does not vibrate`() {
        val quiet = setup().send(Msg.Settings.SetHaptics(false)).send(Msg.Timer.Start)
        assertTrue(quiet.schedule.vibrations.isEmpty())
    }

    @Test fun `Start ends a running rest and closes the hold timer, and no rest starts under the run`() {
        val resting = started(freshState()).let { it.send(WorkoutEvent.TapSet(0, 0)) }
        assertNotNull(resting.model.rest)
        val timed = resting.send(Msg.Timer.Open(TimerMode.AMRAP)).send(Msg.Timer.Start)
        assertNull(timed.model.rest)
        assertNull(timed.model.restScreen)
        assertTrue(timed.cmds.containsAll(listOf(Cmd.SaveRest(null), Cmd.CancelRestAlarms, Cmd.HideRestCountdown)))
        // The workout stays as it is underneath.
        assertTrue(timed.session is Session.Running)
        val tapped = timed.send(WorkoutEvent.TapSet(1, 0))
        assertEquals(5, tapped.workout.exercises[1].sets[0])
        assertNull(tapped.model.rest)
    }

    private fun started(state: io.github.alexanderop.eisen.app.state.AppState): Step =
        stepOf(state).send(Msg.Workout(WorkoutEvent.Start), now = STARTED_AT, id = "w1")

    @Test fun `pause silences the run and resume plays what is left, shifted by the pause`() {
        val paused = started().send(Msg.Timer.Pause, now = STARTED_AT + 35_000)
        assertEquals(STARTED_AT + 35_000, paused.timed.pausedAt)
        assertEquals(Cmd.ScheduleTimer(emptyList(), emptyList(), null), paused.schedule)
        assertTrue(subscriptions(paused.model).isEmpty())
        assertTrue(timerNotice(paused.timed, STARTED_AT + 50_000, freshState()).paused)
        assertIgnored(paused, Msg.Timer.Pause)
        assertIgnored(paused, Msg.Timer.Tap)

        val resumed = paused.send(Msg.Timer.Resume, now = STARTED_AT + 95_000)
        assertNull(resumed.timed.pausedAt)
        assertEquals(60_000L, resumed.timed.paused)
        // 35 seconds in when it was paused: the rest of the schedule, a minute later.
        val ahead = timerCues(short, DEFAULT_TIMER_CALLS).filter { it.at >= 35_000 }.map { TimerSound(STARTED_AT + 60_000 + it.at, it.sound) }
        assertEquals(ahead, resumed.schedule.sounds)
        assertEquals(setOf<Sub>(Sub.TimerClock(STARTED_AT + 60_000)), subscriptions(resumed.model))
        assertTrue(Cmd.SaveTimer(resumed.timed) in resumed.cmds)
    }

    @Test fun `End during the lead-in closes without a question and without a history entry`() {
        val closed = started().send(Msg.Timer.End, now = STARTED_AT + 5_000)
        assertNull(closed.model.timer)
        assertEquals(listOf(Cmd.SaveTimer(null), Cmd.HideTimer), closed.cmds)
        assertTrue(closed.state.timerSessions.isEmpty())
        // Back on the setup it came from.
        assertEquals(short, closed.model.timerSetup?.config)
    }

    @Test fun `End asks, and Keep going goes on`() {
        val asking = started().send(Msg.Timer.End, now = STARTED_AT + 20_000)
        assertEquals(true, (asking.model.timer as TimerState.Running).ending)
        assertTrue(asking.cmds.isEmpty())
        val going = asking.send(Msg.Timer.KeepGoing, now = STARTED_AT + 21_000)
        assertEquals(false, (going.model.timer as TimerState.Running).ending)
        // The back gesture asks too, and answers "Keep going" while asking.
        assertTrue(handlesBack(going.model))
        val back = going.send(Msg.Nav.Back, now = STARTED_AT + 22_000)
        assertEquals(true, (back.model.timer as TimerState.Running).ending)
        assertEquals(false, (back.send(Msg.Nav.Back).model.timer as TimerState.Running).ending)
    }

    @Test fun `Save and end files what was done as not completed and shows the result`() {
        val saved = started().send(Msg.Timer.End, now = STARTED_AT + 65_000).send(Msg.Timer.SaveAndEnd, now = STARTED_AT + 65_000)
        val session = TimerSession("t1", "Tabata", short, STARTED_AT, STARTED_AT + 65_000, seconds = 55, rounds = 2, completed = false)
        assertEquals(listOf(session), saved.state.timerSessions)
        assertEquals(TimerState.Finished(session, at = STARTED_AT + 65_000), saved.model.timer)
        assertTrue(saved.cmds.containsAll(listOf(Cmd.SaveTimer(null), Cmd.HideTimer, Cmd.Save(saved.state))))
        assertTrue(subscriptions(saved.model).isEmpty())
        // Done goes back to the Timer tab, Again straight into the lead-in of the same timer.
        val done = saved.send(Msg.Timer.Done)
        assertNull(done.model.timer)
        assertNull(done.model.timerSetup)
        assertEquals(Tab.TIMER, done.model.nav.tab)
        val again = saved.send(Msg.Timer.Again, now = STARTED_AT + 70_000, id = "t2")
        assertEquals(TimerRun("t2", "Tabata", short, DEFAULT_TIMER_CALLS, startedAt = STARTED_AT + 70_000), again.timed)
    }

    @Test fun `the notification's End saves what was done, and in the lead-in just closes`() {
        val inLeadIn = started().send(Msg.Timer.SaveAndEnd, now = STARTED_AT + 3_000)
        assertNull(inLeadIn.model.timer)
        assertTrue(inLeadIn.state.timerSessions.isEmpty())
        val later = started().send(Msg.Timer.SaveAndEnd, now = STARTED_AT + 45_000)
        assertEquals(1, later.state.timerSessions.size)
    }

    @Test fun `Discard ends the run without a history entry`() {
        val discarded = started().send(Msg.Timer.End, now = STARTED_AT + 40_000).send(Msg.Timer.Discard, now = STARTED_AT + 40_000)
        assertNull(discarded.model.timer)
        assertTrue(discarded.state.timerSessions.isEmpty())
        assertEquals(listOf(Cmd.SaveTimer(null), Cmd.HideTimer), discarded.cmds)
    }

    @Test fun `a run that ends by itself is filed as completed and lets its last words play`() {
        val ticking = started().send(Msg.Timer.Tick, now = STARTED_AT + 35_005)
        assertEquals(listOf(Cmd.ShowTimer(timerNotice(ticking.timed, STARTED_AT + 35_005, freshState()))), ticking.cmds)
        // The same phase a second later: nothing to tell the notification.
        assertTrue(ticking.send(Msg.Timer.Tick, now = STARTED_AT + 36_005).cmds.isEmpty())

        val over = ticking.send(Msg.Timer.Tick, now = STARTED_AT + 90_005)
        val session = TimerSession("t1", "Tabata", short, STARTED_AT, STARTED_AT + 90_000, seconds = 80, rounds = 3, completed = true)
        assertEquals(TimerState.Finished(session, at = STARTED_AT + 90_005), over.model.timer)
        assertEquals(listOf(session), over.state.timerSessions)
        assertTrue(Cmd.After(TIMER_TAIL_MILLIS - 5, Msg.Timer.Release("t1")) in over.cmds)
        assertFalse(Cmd.HideTimer in over.cmds)
        assertEquals(listOf(Cmd.HideTimer), over.send(Msg.Timer.Release("t1")).cmds)
        // A new run started meanwhile keeps its service.
        val again = over.send(Msg.Timer.Again, id = "t2")
        assertIgnored(again, Msg.Timer.Release("t1"))
    }

    @Test fun `any Msg after the end files the run first`() {
        val late = started().send(Msg.Timer.Pause, now = STARTED_AT + 200_000)
        assertTrue(late.model.timer is TimerState.Finished)
        assertTrue(Cmd.HideTimer in late.cmds)
    }

    @Test fun `AMRAP counts tapped rounds, not during the lead-in`() {
        val amrap = stepOf().send(Msg.Timer.Open(TimerMode.AMRAP)).send(Msg.Timer.Start, now = STARTED_AT, id = "a1")
        assertEquals(0, amrap.send(Msg.Timer.Tap, now = STARTED_AT + 5_000).timed.taps)
        val tapped = amrap.send(Msg.Timer.Tap, now = STARTED_AT + 20_000).send(Msg.Timer.Tap, now = STARTED_AT + 40_000)
        assertEquals(2, tapped.timed.taps)
        assertTrue(Cmd.SaveTimer(tapped.timed) in tapped.cmds)
        assertTrue(UiEvent.Haptic(io.github.alexanderop.eisen.app.elm.Feel.TAP) in tapped.uiEvents)
        val saved = tapped.send(Msg.Timer.SaveAndEnd, now = STARTED_AT + 70_000)
        assertEquals(2, saved.state.timerSessions.single().rounds)
        // Tabata counts its rounds itself.
        assertIgnored(started().send(Msg.Timer.Tick, now = STARTED_AT + 20_000), Msg.Timer.Tap)
    }

    @Test fun `For Time finishes as completed when the lifter says so`() {
        val forTime = stepOf().send(Msg.Timer.Open(TimerMode.FOR_TIME)).send(Msg.Timer.Start, now = STARTED_AT, id = "f1")
        // Nothing to finish during the lead-in.
        assertTrue(forTime.send(Msg.Timer.Finish, now = STARTED_AT + 5_000).model.timer is TimerState.Running)
        val done = forTime.send(Msg.Timer.Tap, now = STARTED_AT + 100_000).send(Msg.Timer.Finish, now = STARTED_AT + 382_000)
        val session = done.state.timerSessions.single()
        assertEquals(Triple(372, 1, true), Triple(session.seconds, session.rounds, session.completed))
        assertIgnored(started().send(Msg.Timer.Tick, now = STARTED_AT + 20_000), Msg.Timer.Finish)
    }

    @Test fun `a Finish in the half second before the cap is a time under the cap, not a capped run`() {
        // The default For Time has a cap of 20 minutes. Work starts after the ten seconds of lead-in.
        val forTime = stepOf().send(Msg.Timer.Open(TimerMode.FOR_TIME)).send(Msg.Timer.Start, now = STARTED_AT, id = "f1")
        val workStart = STARTED_AT + 10_000
        val finished = forTime.send(Msg.Timer.Finish, now = workStart + 1_199_600).state.timerSessions.single()
        assertEquals(1199, finished.seconds)
        assertEquals(io.github.alexanderop.eisen.feature.timer.TimerOutcome.COMPLETED, io.github.alexanderop.eisen.feature.timer.timerOutcome(finished))
        // One that runs into the cap is capped, with the full time.
        val capped = forTime.send(Msg.Timer.Tick, now = workStart + 1_200_000).state.timerSessions.single()
        assertEquals(1200, capped.seconds)
        assertEquals(io.github.alexanderop.eisen.feature.timer.TimerOutcome.CAPPED, io.github.alexanderop.eisen.feature.timer.timerOutcome(capped))
    }

    @Test fun `Health Connect names how a run ended the way the app does, a For Time at its cap too`() {
        val config = TimerConfig(TimerMode.FOR_TIME, work = 600, rest = 0, rounds = 1)
        fun notes(seconds: Int, completed: Boolean, wodId: String? = null) =
            io.github.alexanderop.eisen.feature.health.platform.timerSessionFields(TimerSession("f1", "For Time", config, 0, 700_000, seconds, 0, completed, wodId), java.time.ZoneOffset.UTC)!!.notes
        assertEquals("For Time Cap 10:00: 7:00", notes(420, completed = true))
        assertEquals("For Time Cap 10:00: 7:00, stopped early", notes(420, completed = false))
        // At the cap the plain timer (filed as completed) and the WOD (filed as not) both say so, as the result does.
        assertEquals("For Time Cap 10:00: 10:00, time cap", notes(600, completed = true))
        assertEquals("For Time Cap 10:00: 10:00, time cap", notes(600, completed = false, wodId = "w1"))
    }

    @Test fun `a run kept in its file comes back at the right time, or is filed when it ran out meanwhile`() {
        val run = TimerRun("t1", "Tabata", short, DEFAULT_TIMER_CALLS, startedAt = STARTED_AT)
        val flags = Flags(freshState(), savedRest = null, healthEnabled = false, notifications = Notifications(true, true), savedTimer = run)
        val (going, cmds) = init(flags.copy(now = STARTED_AT + 50_000))
        assertEquals(TimerState.Running(run), going.timer)
        val sounds = cmds.filterIsInstance<Cmd.ScheduleTimer>().single().sounds
        assertEquals(timerCues(short, DEFAULT_TIMER_CALLS).filter { it.at >= 50_000 }.map { TimerSound(STARTED_AT + it.at, it.sound) }, sounds)
        assertTrue(Cmd.ShowTimer(timerNotice(run, STARTED_AT + 50_000, freshState())) in cmds)

        val (over, filed) = init(flags.copy(now = STARTED_AT + 600_000, healthEnabled = true))
        val session = TimerSession("t1", "Tabata", short, STARTED_AT, STARTED_AT + 90_000, seconds = 80, rounds = 3, completed = true)
        assertEquals(TimerState.Finished(session, at = STARTED_AT + 600_000), over.timer)
        assertEquals(listOf(session), over.state.timerSessions)
        assertTrue(filed.containsAll(listOf(Cmd.SaveTimer(null), Cmd.Save(over.state), Cmd.Health(HealthJob.Upsert(emptyList(), listOf(session))))))

        // A paused one stays paused and silent.
        val (paused, quiet) = init(flags.copy(savedTimer = run.copy(pausedAt = STARTED_AT + 20_000), now = STARTED_AT + 900_000))
        assertTrue(paused.timer is TimerState.Running)
        assertTrue(quiet.none { it is Cmd.ScheduleTimer })
    }

    @Test fun `a rest and a run both kept when the process ended, the run goes on and the rest is stale`() {
        // A workout runs, so a kept rest alone would go on. A run still going is newer: starting it ended the rest.
        val state = stepOf().send(WorkoutEvent.Start, now = STARTED_AT).state
        val rest = io.github.alexanderop.eisen.app.elm.RestState(STARTED_AT, STARTED_AT + 90_000, "All reps done.")
        val run = TimerRun("t1", "Tabata", short, DEFAULT_TIMER_CALLS, startedAt = STARTED_AT + 5_000)
        val flags = Flags(state, savedRest = rest, healthEnabled = false, notifications = Notifications(true, true), savedTimer = run, now = STARTED_AT + 20_000)
        val (model, cmds) = init(flags)
        assertEquals(TimerState.Running(run), model.timer)
        assertNull(model.rest)
        assertTrue(cmds.containsAll(io.github.alexanderop.eisen.app.elm.stopRestCmds()))
        // Without a run the rest goes on, as before.
        val (resting, restCmds) = init(flags.copy(savedTimer = null))
        assertEquals("without a run", rest, resting.rest)
        assertTrue(restCmds.none { it is Cmd.CancelRestAlarms })
        // A run that ran out meanwhile is filed and covers nothing: the rest goes on.
        val (filed, _) = init(flags.copy(now = STARTED_AT + 200_000))
        assertTrue(filed.timer is TimerState.Finished)
        assertEquals("ran out", rest, filed.rest)
    }

    @Test fun `presets are saved, opened, renamed, updated and deleted with Undo, without a snackbar over Start`() {
        val saved = setup().send(Msg.Timer.SavePreset("  "), id = "p1")
        val preset = saved.state.timerPresets.single()
        assertEquals("Tabata", preset.name)
        assertEquals(short, preset.config)
        assertEquals(TimerSetup(short, "p1", "Tabata"), saved.model.timerSetup)
        assertTrue(setupSaved(saved.model, saved.model.timerSetup!!))
        assertFalse(setupChanged(saved.model, saved.model.timerSetup!!))
        assertTrue(saved.uiEvents.none { it is UiEvent.Snackbar })
        // A second one of the same mode gets the next free number; the numbers are in its second line.
        assertEquals("Tabata 2", defaultPresetName(TimerMode.TABATA, saved.state.timerPresets))
        assertEquals("Tabata 2", saved.send(Msg.Timer.SavePreset(""), id = "p2").state.timerPresets.last().name)

        val named = setup().send(Msg.Timer.SavePreset("Finisher"), id = "p1").send(Msg.Timer.CloseSetup).send(Msg.Timer.OpenPreset("p1"))
        assertEquals(TimerSetup(short, "p1", "Finisher"), named.model.timerSetup)
        // A run of a preset carries its name.
        assertEquals("Finisher", named.send(Msg.Timer.Start).timed.name)
        assertIgnored(named, Msg.Timer.UpdatePreset)
        val changed = named.send(Msg.Timer.Edit(short.copy(rounds = 8)))
        assertFalse(setupSaved(changed.model, changed.model.timerSetup!!))
        assertTrue(setupChanged(changed.model, changed.model.timerSetup!!))
        val renamed = changed.send(Msg.Timer.EditName("Burner")).send(Msg.Timer.UpdatePreset)
        assertEquals(TimerPreset("p1", "Burner", short.copy(rounds = 8)), renamed.state.timerPresets.single())
        assertTrue(renamed.uiEvents.none { it is UiEvent.Snackbar })
        assertTrue(setupSaved(renamed.model, renamed.model.timerSetup!!))
        // A name cleared to nothing keeps the one it had.
        val cleared = renamed.send(Msg.Timer.EditName("")).send(Msg.Timer.UpdatePreset)
        assertEquals("Burner", cleared.state.timerPresets.single().name)
        assertIgnored(stepOf().send(Msg.Timer.Open(TimerMode.TABATA)), Msg.Timer.EditName("x"))

        val deleted = renamed.send(Msg.Timer.DeletePreset("p1"))
        assertTrue(deleted.state.timerPresets.isEmpty())
        assertNull(deleted.model.timerSetup?.presetId)
        assertEquals("Undo", deleted.uiEvents.filterIsInstance<UiEvent.Snackbar>().single().actionLabel)
        val undone = deleted.send(Msg.Timer.UndoDeletePreset)
        assertEquals(renamed.state.timerPresets, undone.state.timerPresets)
        assertIgnored(undone, Msg.Timer.UndoDeletePreset)
        assertIgnored(stepOf(), Msg.Timer.OpenPreset("gone"))
    }

    @Test fun `the play button of a saved timer starts it at once, from its setup`() {
        val saved = setup().send(Msg.Timer.SavePreset("Finisher"), id = "p1").send(Msg.Timer.CloseSetup).send(Msg.Nav.SelectTab(Tab.HOME))
        val started = saved.send(Msg.Timer.StartPreset("p1"), now = STARTED_AT, id = "t1")
        assertEquals(TimerRun("t1", "Finisher", short, DEFAULT_TIMER_CALLS, startedAt = STARTED_AT), started.timed)
        assertTrue(Cmd.SaveTimer(started.timed) in started.cmds)
        // Ended in the lead-in, the lifter is on its setup.
        assertEquals("p1", started.send(Msg.Timer.End, now = STARTED_AT + 2_000).model.timerSetup?.presetId)
        assertIgnored(saved, Msg.Timer.StartPreset("gone"))
        assertIgnored(started, Msg.Timer.StartPreset("p1"))
    }

    @Test fun `a second tap within 800 ms is a bounce, and -1 takes a round back`() {
        val amrap = stepOf().send(Msg.Timer.Open(TimerMode.AMRAP)).send(Msg.Timer.Start, now = STARTED_AT, id = "a1")
        val once = amrap.send(Msg.Timer.Tap, now = STARTED_AT + 20_000)
        assertEquals(1, once.timed.taps)
        val bounced = once.send(Msg.Timer.Tap, now = STARTED_AT + 20_000 + TAP_GUARD_MILLIS - 1)
        assertEquals(once.model, bounced.model)
        assertTrue(bounced.cmds.isEmpty())
        val twice = once.send(Msg.Timer.Tap, now = STARTED_AT + 20_000 + TAP_GUARD_MILLIS)
        assertEquals(2, twice.timed.taps)
        val back = twice.send(Msg.Timer.Untap, now = STARTED_AT + 21_000)
        assertEquals(1, back.timed.taps)
        assertTrue(Cmd.SaveTimer(back.timed) in back.cmds)
        // Not below 0, and not where the timer counts the rounds itself.
        val zero = back.send(Msg.Timer.Untap, now = STARTED_AT + 22_000)
        assertEquals(0, zero.timed.taps)
        assertIgnored(Step(zero.model), Msg.Timer.Untap)
        assertIgnored(Step(started().send(Msg.Timer.Tick, now = STARTED_AT + 20_000).model), Msg.Timer.Untap)
    }

    @Test fun `the rounds of a result are corrected in the saved session`() {
        val amrap = stepOf().send(Msg.Timer.Open(TimerMode.AMRAP)).send(Msg.Timer.Start, now = STARTED_AT, id = "a1")
            .send(Msg.Timer.Tap, now = STARTED_AT + 20_000)
        val saved = amrap.send(Msg.Timer.SaveAndEnd, now = STARTED_AT + 30_000)
        val more = saved.send(Msg.Timer.CorrectRounds(1)).send(Msg.Timer.CorrectRounds(1))
        val session = (more.model.timer as TimerState.Finished).session
        assertEquals(3, session.rounds)
        assertEquals(listOf(session), more.state.timerSessions)
        assertTrue(Cmd.Save(more.state) in more.cmds)
        val none = more.send(Msg.Timer.CorrectRounds(-5))
        assertEquals(0, none.state.timerSessions.single().rounds)
        assertIgnored(Step(none.model), Msg.Timer.CorrectRounds(-1))
        // Tabata and EMOM count their rounds themselves.
        val tabata = started().send(Msg.Timer.SaveAndEnd, now = STARTED_AT + 45_000)
        assertIgnored(Step(tabata.model), Msg.Timer.CorrectRounds(1))
    }

    @Test fun `the Next line says what comes, the same as the PWA`() {
        fun next(config: TimerConfig, at: Long) = nextText(TimerRun("t", "T", config, DEFAULT_TIMER_CALLS, startedAt = 0), timerView(config, at))
        val tabata = TimerConfig(TimerMode.TABATA, work = 20, rest = 10, rounds = 4)
        assertEquals("Next: Work 20s", next(tabata, 1_000))
        assertEquals("Next: Rest 10s", next(tabata, 15_000))
        assertEquals("Next: Round 2", next(tabata, 35_000))
        assertEquals("Next: Rest 10s", next(tabata, 75_000))
        assertEquals("Next: Last round", next(tabata, 95_000))
        assertEquals("Last round", next(tabata, 105_000))
        val emom = TimerConfig(TimerMode.EMOM, work = 60, rest = 0, rounds = 3)
        assertEquals("Next: Work 1:00", next(emom, 1_000))
        assertEquals("Next: Round 2", next(emom, 20_000))
        assertEquals("Next: Last round", next(emom, 80_000))
        assertEquals("Last round", next(emom, 140_000))
        assertEquals("Next: Work 10:00", next(DEFAULT_TIMERS.getValue(TimerMode.AMRAP), 1_000))
        assertNull(next(DEFAULT_TIMERS.getValue(TimerMode.AMRAP), 20_000))
        assertEquals("Next: Go", next(TimerConfig(TimerMode.FOR_TIME, 0, 0, 1), 1_000))
        assertEquals("Next: Work 20:00", next(DEFAULT_TIMERS.getValue(TimerMode.FOR_TIME), 1_000))
    }

    @Test fun `a finished timer is deleted from the history with Undo, and Health Connect follows`() {
        val filed = started().send(Msg.Timer.SaveAndEnd, now = STARTED_AT + 45_000).send(Msg.Timer.Done)
        val synced = filed.copy(model = filed.model.copy(health = HealthModel(enabled = true)))
        val deleted = synced.send(Msg.Timer.DeleteSession("t1"))
        assertTrue(deleted.state.timerSessions.isEmpty())
        assertTrue(Cmd.Health(HealthJob.Delete(listOf("t1"))) in deleted.cmds)
        val undone = deleted.send(Msg.Timer.UndoDeleteSession)
        assertEquals(filed.state.timerSessions, undone.state.timerSessions)
        assertTrue(Cmd.Health(HealthJob.Upsert(emptyList(), filed.state.timerSessions)) in undone.cmds)
    }

    @Test fun `the voice is one setting for all timers, and a run keeps what it started with`() {
        val calls = DEFAULT_TIMER_CALLS.copy(minutes = 5, beeps = false)
        val set = stepOf().send(Msg.Timer.SetCalls(calls))
        assertEquals(calls, set.state.settings.timerCalls)
        assertEquals(0, stepOf().send(Msg.Timer.SetCalls(calls.copy(minutes = -1))).state.settings.timerCalls.minutes)
        assertEquals(2, stepOf().send(Msg.Timer.SetCalls(calls.copy(minutes = 3))).state.settings.timerCalls.minutes)
        val running = started().send(Msg.Timer.SetCalls(calls))
        assertEquals(DEFAULT_TIMER_CALLS, running.timed.calls)
        assertEquals(listOf(Cmd.PlayTimerSound("halfway")), stepOf().send(Msg.Timer.PlaySample).cmds)
        assertEquals("Every minute, halfway, last 30 s, rounds and rest, 3-2-1 beeps", callsSummary(DEFAULT_TIMER_CALLS))
        assertEquals("Halfway", callsSummary(DEFAULT_TIMER_CALLS.copy(minutes = 0, final = false, phases = false, beeps = false)))
        assertEquals("Every 2 min", callsSummary(DEFAULT_TIMER_CALLS.copy(minutes = 2, halfway = false, final = false, phases = false, beeps = false)))
        assertEquals("Voice off", callsSummary(DEFAULT_TIMER_CALLS.copy(minutes = 0, halfway = false, final = false, phases = false, beeps = false)))
    }

    @Test fun `Progress is a switch in History, and the Timer tab takes its place`() {
        assertEquals(listOf(Tab.HOME, Tab.WORKOUTS, Tab.TIMER, Tab.HISTORY, Tab.SETTINGS), Tab.entries)
        val history = stepOf().send(Msg.Nav.SelectTab(Tab.HISTORY))
        assertEquals(HistoryView.LOG, history.model.nav.history)
        val progress = history.send(Msg.Nav.ShowHistory(HistoryView.PROGRESS), now = 8_000)
        assertEquals(HistoryView.PROGRESS, progress.model.nav.history)
        assertEquals(8_000, progress.model.now)
        assertEquals(HistoryView.PROGRESS, stepOf().send(Msg.Nav.Restore(Tab.HISTORY, false, null, HistoryView.PROGRESS)).model.nav.history)
        // The choice stays while the lifter goes elsewhere and comes back.
        val away = progress.send(Msg.Nav.SelectTab(Tab.TIMER)).send(Msg.Nav.Back).send(Msg.Nav.SelectTab(Tab.HISTORY))
        assertEquals(HistoryView.PROGRESS, away.model.nav.history)
        // Back closes a timer's setup before it goes home.
        val open = stepOf().send(Msg.Timer.Open(TimerMode.EMOM))
        val list = open.send(Msg.Nav.Back)
        assertNull(list.model.timerSetup)
        assertEquals(Tab.TIMER, list.model.nav.tab)
        assertEquals(Tab.HOME, list.send(Msg.Nav.Back).model.nav.tab)
    }

    @Test fun `the wheels step 5 s up to a minute, 15 s up to five, then minutes, and keep a value off that grid`() {
        val work = timerWheelSeconds(5, 600, 20)
        assertEquals(listOf(5, 10, 15, 20, 25, 30, 35, 40, 45, 50, 55, 60, 75, 90), work.take(14))
        assertEquals(listOf(285, 300, 360, 420, 480, 540, 600), work.takeLast(7))
        assertTrue(37 in timerWheelSeconds(5, 600, 37))
        assertEquals(listOf(0, 60, 120), timerCapSeconds(1200).take(3))
        assertEquals(3600, timerCapSeconds(1200).last())
        assertTrue(90 in timerCapSeconds(90))
    }

    // The saved state

    @Test fun `version 6 gets the timers with their defaults, and version 7 keeps them`() {
        val v6 = encodeState(freshState()).replace("\"version\":$STATE_VERSION", "\"version\":6").replace(",\"wods\":[]", "")
            .replace(Regex(",\"timerCalls\":\\{[^}]*\\}"), "")
            .replace(Regex(",\"timerPresets\":\\[\\],\"timerSessions\":\\[\\],\"timerConfigs\":\\{.*\\}\\}\\}$"), "}")
        assertFalse(v6.contains("timer"))
        val migrated = decodeState(v6)!!
        assertEquals(STATE_VERSION, migrated.version)
        assertEquals(freshState(), migrated)

        val session = TimerSession("t1", "Finisher", short, 1_000, 92_000, 80, 3, true)
        val state = freshState().copy(timerSessions = listOf(session), timerConfigs = DEFAULT_TIMERS + (TimerMode.TABATA to short))
        val json = encodeState(state)
        // The shape the PWA writes: modes by their names in the JSON.
        assertTrue(json.contains("\"timerConfigs\":{\"tabata\":{\"mode\":\"tabata\",\"work\":20,\"rest\":10,\"rounds\":3}"))
        assertTrue(json.contains("\"timerCalls\":{\"minutes\":1,\"halfway\":true,\"final\":true,\"phases\":true,\"beeps\":true}"))
        assertEquals(state, decodeState(json))
    }

    @Test fun `a day with a timer is a training day on the calendar`() {
        val zone = ZoneOffset.UTC
        val now = LocalDate.of(2026, 11, 23).atStartOfDay(zone).toInstant().toEpochMilli() + 3_600_000
        val session = TimerSession("t1", "Finisher", short, now - 86_400_000, now - 86_300_000, 80, 3, true)
        val days = calendarWeeks(emptyList(), now, 2, zone, timers = listOf(session)).flatten()
        assertEquals(listOf(session), days.single { it.trained }.timers)
    }

    @Test fun `a finished timer goes to Health Connect as a HIIT session named like it`() {
        val session = TimerSession("t1", "Finisher", short, 1_000, 92_000, 80, 3, false)
        val fields = timerSessionFields(session, ZoneOffset.UTC)!!
        assertEquals("Finisher", fields.title)
        assertEquals(ExerciseSessionRecord.EXERCISE_TYPE_HIGH_INTENSITY_INTERVAL_TRAINING, fields.exerciseType)
        assertEquals("Tabata 3 × 20s / 10s: 3 rounds · 1:20, stopped early", fields.notes)
        assertTrue(fields.segments.isEmpty())
        assertNull(timerSessionFields(session.copy(finishedAt = 1_000), ZoneOffset.UTC))
    }
}

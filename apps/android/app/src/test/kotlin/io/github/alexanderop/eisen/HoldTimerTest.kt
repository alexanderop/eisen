package io.github.alexanderop.eisen

import io.github.alexanderop.eisen.app.elm.repsQuestion
import io.github.alexanderop.eisen.app.elm.hold
import io.github.alexanderop.eisen.app.state.AppState
import io.github.alexanderop.eisen.app.elm.Feel
import io.github.alexanderop.eisen.feature.workout.MAX_HELD_SECONDS
import io.github.alexanderop.eisen.app.elm.Session
import io.github.alexanderop.eisen.feature.workout.Slot
import io.github.alexanderop.eisen.core.WeightUnit
import io.github.alexanderop.eisen.app.elm.WorkoutEvent
import io.github.alexanderop.eisen.app.elm.Cmd
import io.github.alexanderop.eisen.app.elm.HOLD_END_VIBRATION
import io.github.alexanderop.eisen.app.elm.HOLD_START_VIBRATION
import io.github.alexanderop.eisen.app.elm.HoldPhase
import io.github.alexanderop.eisen.app.elm.HoldTimer
import io.github.alexanderop.eisen.app.elm.Msg
import io.github.alexanderop.eisen.app.elm.RestState
import io.github.alexanderop.eisen.app.elm.Sub
import io.github.alexanderop.eisen.app.elm.handlesBack
import io.github.alexanderop.eisen.app.elm.holdPhase
import io.github.alexanderop.eisen.app.elm.holdSecondsShown
import io.github.alexanderop.eisen.app.elm.subscriptions
import io.github.alexanderop.eisen.app.state.freshState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The hold timer of timed sets, through `update` alone: lead-in, countdown, max hold, cues, rest and closing. */
class HoldTimerTest {
    /** Workout A as a plank 3×1:00, a dead hang 2×max and the bench press, in that order. */
    private fun holdState(sound: Boolean = true, haptics: Boolean = true): AppState {
        val fresh = freshState(WeightUnit.KG)
        val slots = listOf(Slot("plank", 3, 60), Slot("deadHang", 2, null), Slot("bench", 5, 5))
        return fresh.copy(
            routines = fresh.routines.map { if (it.id == "A") it.copy(slots = slots) else it },
            settings = fresh.settings.copy(sound = sound, haptics = haptics),
        )
    }

    private val leadIn = 5_000L
    private val startsAt = LATER + leadIn
    private val endsAt = startsAt + 60_000

    /** Set 1 of the plank tapped at [LATER]. */
    private fun opened(state: AppState = holdState()): Step = started(state).send(WorkoutEvent.TapSet(0, 0))

    private fun Step.tick(now: Long): Step = send(Msg.Hold.Tick, now = now)

    private val Step.hold: HoldTimer? get() = model.hold

    @Test fun `tapping an open set of a timed exercise opens the hold timer with a lead-in`() {
        val step = opened()
        assertEquals(HoldTimer(0, 0, "plank", 60, startsAt), step.hold)
        assertEquals(listOf<Int?>(null, null, null), step.workout.exercises[0].sets)
        assertEquals(HoldPhase.LEAD_IN, holdPhase(step.hold!!, LATER))
        assertEquals(5, holdSecondsShown(step.hold!!, LATER))
        assertEquals(setOf(Sub.HoldClock(startsAt)), subscriptions(step.model))
        // Opening the timer is no logged set: the exercise does not settle yet.
        assertNull(step.model.settling)
        assertEquals(listOf(Feel.TAP), step.feels)
    }

    @Test fun `opening the timer ends a running rest`() {
        val resting = started(holdState()).send(WorkoutEvent.TapSet(2, 0))
        assertNotNull(resting.model.rest)
        val step = resting.send(WorkoutEvent.TapSet(0, 0), now = LATER + 10_000)
        assertNull(step.model.rest)
        assertTrue(Cmd.CancelRestAlarms in step.cmds)
        assertNotNull(step.hold)
    }

    @Test fun `the start of the hold beeps and shakes once`() {
        val step = opened()
        assertTrue(step.tick(LATER + 2_000).cmds.isEmpty())
        val holding = step.tick(startsAt + 5)
        assertEquals(listOf(Cmd.Beep(1), Cmd.Vibrate(HOLD_START_VIBRATION)), holding.cmds)
        assertTrue(holding.hold!!.started)
        assertEquals(60, holdSecondsShown(holding.hold!!, startsAt + 5))
        assertTrue(holding.tick(startsAt + 1_005).cmds.isEmpty())
        assertEquals(startsAt + 1_005, holding.tick(startsAt + 1_005).model.now)
    }

    @Test fun `a tap on the lead-in starts the hold at once`() {
        val skipped = opened().send(Msg.Hold.SkipLeadIn, now = LATER + 1_000)
        assertEquals(HoldTimer(0, 0, "plank", 60, LATER + 1_000, started = true), skipped.hold)
        assertEquals(listOf(Cmd.Beep(1), Cmd.Vibrate(HOLD_START_VIBRATION)), skipped.cmds)
        assertIgnored(skipped, Msg.Hold.SkipLeadIn)
    }

    @Test fun `a countdown that runs out logs the target, beeps three times and starts the rest`() {
        val holding = opened().tick(startsAt + 5)
        val done = holding.tick(endsAt + 5)
        assertNull(done.hold)
        assertEquals(listOf<Int?>(60, null, null), done.workout.exercises[0].sets)
        assertEquals(listOf(Cmd.Beep(3), Cmd.Vibrate(HOLD_END_VIBRATION)), done.cmds.take(2))
        assertEquals(RestState(endsAt + 5, endsAt + 5 + 90_000, "Held 1:00.", "plank", 0), done.model.rest)
        assertEquals("plank", done.model.settling?.id)
        assertTrue(subscriptions(done.model).none { it is Sub.HoldClock })
    }

    @Test fun `Stop during a countdown logs the whole seconds held and rests longer`() {
        val stopped = opened().tick(startsAt + 5).send(Msg.Hold.Stop, now = startsAt + 42_700)
        assertNull(stopped.hold)
        assertEquals(listOf<Int?>(42, null, null), stopped.workout.exercises[0].sets)
        assertEquals(RestState(startsAt + 42_700, startsAt + 42_700 + 300_000, "Hold cut short. Rest longer.", "plank", 0), stopped.model.rest)
        assertEquals(listOf(Feel.WARNING), stopped.feels)
        assertFalse(stopped.cmds.any { it is Cmd.Beep })
    }

    @Test fun `Stop under a second logs nothing, and is not there during the lead-in`() {
        val opened = opened()
        assertIgnored(opened, Msg.Hold.Stop)
        val early = opened.tick(startsAt + 5).send(Msg.Hold.Stop, now = startsAt + 900)
        assertNull(early.hold)
        assertEquals(listOf<Int?>(null, null, null), early.workout.exercises[0].sets)
        assertNull(early.model.rest)
    }

    @Test fun `Stop after the end logs the whole target`() {
        val late = opened().tick(startsAt + 5).send(Msg.Hold.Stop, now = endsAt + 300)
        assertEquals(listOf<Int?>(60, null, null), late.workout.exercises[0].sets)
        assertEquals("Held 1:00.", late.model.rest?.note)
    }

    @Test fun `Cancel closes the timer without logging, in either phase`() {
        val opened = opened()
        for (step in listOf(opened, opened.tick(startsAt + 5).tick(startsAt + 20_005))) {
            val cancelled = step.send(Msg.Hold.Cancel, now = startsAt + 20_100)
            assertNull(cancelled.hold)
            assertEquals(listOf<Int?>(null, null, null), cancelled.workout.exercises[0].sets)
            assertNull(cancelled.model.rest)
            assertTrue(subscriptions(cancelled.model).isEmpty())
        }
    }

    @Test fun `a max hold counts up, Done logs the seconds held and rests as after a full set`() {
        val opened = started(holdState()).send(WorkoutEvent.TapSet(1, 0))
        assertEquals(HoldTimer(1, 0, "deadHang", null, startsAt), opened.hold)
        assertNull(opened.model.repsQuestion)
        val holding = opened.tick(startsAt + 5).tick(startsAt + 37_005)
        assertEquals(37, holdSecondsShown(holding.hold!!, startsAt + 37_005))
        val done = holding.send(Msg.Hold.Stop, now = startsAt + 37_500)
        assertEquals(listOf<Int?>(37, null), done.workout.exercises[1].sets)
        assertEquals("Held 37s.", done.model.rest?.note)
        assertEquals(90, done.model.rest!!.let { ((it.endsAt - it.startedAt) / 1000).toInt() })
    }

    @Test fun `a max hold stops itself after an hour`() {
        val holding = started(holdState()).send(WorkoutEvent.TapSet(1, 0)).tick(startsAt + 5)
        val stopped = holding.tick(startsAt + MAX_HELD_SECONDS * 1000L + 5)
        assertNull(stopped.hold)
        assertEquals(listOf<Int?>(MAX_HELD_SECONDS, null), stopped.workout.exercises[1].sets)
        assertTrue(Cmd.Beep(3) in stopped.cmds)
    }

    @Test fun `the last hold of the exercise rests before the next exercise`() {
        var step = started(holdState())
        for (set in 0..2) {
            val at = LATER + set * 200_000L
            step = step.send(WorkoutEvent.TapSet(0, set), now = at).send(Msg.Hold.SkipLeadIn, now = at).tick(at + 60_005)
        }
        assertEquals(listOf<Int?>(60, 60, 60), step.workout.exercises[0].sets)
        assertEquals("Held 1:00.", step.model.rest?.note)
        assertEquals(2, step.model.rest?.set)
        assertEquals(listOf(Feel.SUCCESS), step.feels)
    }

    @Test fun `a hold that ended while the app was away is logged on return, without a beep`() {
        val away = opened().tick(startsAt + 5).send(Msg.App.Background, now = startsAt + 10_000)
        // The hold clock keeps going while the app is away.
        assertEquals(setOf(Sub.HoldClock(startsAt)), subscriptions(away.model))
        val back = away.send(Msg.App.Foreground(notificationsGranted = true), now = endsAt + 30_000)
        assertNull(back.hold)
        assertEquals(listOf<Int?>(60, null, null), back.workout.exercises[0].sets)
        assertFalse(back.cmds.any { it is Cmd.Beep || it is Cmd.Vibrate })
        assertEquals("Held 1:00.", back.model.rest?.note)
        // A late tick does the same.
        assertEquals(listOf<Int?>(60, null, null), away.tick(endsAt + 30_000).workout.exercises[0].sets)
    }

    @Test fun `the cues follow the sound and haptics settings`() {
        val quiet = opened(holdState(sound = false, haptics = false)).tick(startsAt + 5)
        assertTrue(quiet.cmds.isEmpty())
        assertFalse(quiet.tick(endsAt + 5).cmds.any { it is Cmd.Beep || it is Cmd.Vibrate })
        val silent = opened(holdState(sound = false)).tick(startsAt + 5)
        assertEquals(listOf(Cmd.Vibrate(HOLD_START_VIBRATION)), silent.cmds)
    }

    @Test fun `tapping a logged hold clears it and opens no timer`() {
        val logged = opened().send(Msg.Hold.SkipLeadIn).tick(LATER + 60_005)
        val cleared = logged.send(WorkoutEvent.TapSet(0, 0), now = LATER + 70_000)
        assertNull(cleared.hold)
        assertEquals(listOf<Int?>(null, null, null), cleared.workout.exercises[0].sets)
        assertNull(cleared.model.rest)
    }

    @Test fun `removing, moving or putting off an exercise closes the timer without logging`() {
        val opened = opened()
        for (event in listOf(WorkoutEvent.RemoveExercise(2), WorkoutEvent.Move(0, 1), WorkoutEvent.Defer(0))) {
            val closed = opened.send(event)
            assertNull("$event", closed.hold)
            assertTrue("$event", closed.workout.exercises.all { log -> log.sets.all { it == null } })
        }
    }

    @Test fun `finishing or discarding the workout closes the timer without logging`() {
        val opened = opened()
        assertNull(opened.send(WorkoutEvent.RequestFinish).hold)
        assertNull(opened.send(WorkoutEvent.RequestDiscard).hold)
        assertTrue(opened.send(WorkoutEvent.RequestDiscard).session is Session.Discarded)
        assertNull(opened.send(Msg.Data.Reset).hold)
    }

    @Test fun `back closes the timer first, then the workout`() {
        val opened = opened()
        assertTrue(handlesBack(opened.model))
        val back = opened.send(Msg.Nav.Back)
        assertNull(back.hold)
        assertTrue(back.model.nav.workoutOpen)
        assertFalse(back.send(Msg.Nav.Back).model.nav.workoutOpen)
        assertNull(opened.send(Msg.Nav.CloseWorkout).hold)
    }

    @Test fun `hold Msgs without an open timer change nothing`() {
        val step = started(holdState())
        for (msg in listOf(Msg.Hold.SkipLeadIn, Msg.Hold.Stop, Msg.Hold.Cancel, Msg.Hold.Tick)) assertIgnored(step, msg)
    }

    @Test fun `LogHold only logs a timed set with a held time`() {
        val step = started(holdState())
        assertIgnored(step, WorkoutEvent.LogHold(2, 0, 30))
        assertIgnored(step, WorkoutEvent.LogHold(0, 0, 0))
        assertIgnored(step, WorkoutEvent.LogHold(0, 3, 30))
        assertEquals(listOf<Int?>(30, null, null), step.send(WorkoutEvent.LogHold(0, 0, 30)).workout.exercises[0].sets)
    }
}

package io.github.alexanderop.eisen

import io.github.alexanderop.eisen.domain.Equipment
import io.github.alexanderop.eisen.domain.Session
import io.github.alexanderop.eisen.domain.WeightUnit
import io.github.alexanderop.eisen.domain.WorkoutEvent
import io.github.alexanderop.eisen.domain.elm.Cmd
import io.github.alexanderop.eisen.domain.elm.Msg
import io.github.alexanderop.eisen.domain.elm.RestScreen
import io.github.alexanderop.eisen.domain.elm.RestState
import io.github.alexanderop.eisen.domain.elm.restCountdown
import io.github.alexanderop.eisen.domain.elm.showsRestScreen
import io.github.alexanderop.eisen.domain.elm.showsWorkout
import io.github.alexanderop.eisen.domain.repsChoices
import io.github.alexanderop.eisen.domain.weightChoices
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The full-screen rest timer and its wheels, through `update` alone. Workout A: hackSquat, bench, row, dips. */
class RestScreenTest {
    /** Set 1 of the bench press logged at [LATER]: a rest of 90 seconds. */
    private fun resting(): Step = started().send(WorkoutEvent.TapSet(1, 0))

    private val benchRest = RestState(LATER, LATER + 90_000, "All reps done.")

    private val restCmds = setOf(Cmd.SaveRest::class, Cmd.ScheduleRestAlarms::class, Cmd.CancelRestAlarms::class, Cmd.ShowRestCountdown::class, Cmd.HideRestCountdown::class)

    private fun Step.restCmds(): List<Cmd> = cmds.filter { it::class in restCmds }

    @Test fun `a logged set opens the rest screen for that set`() {
        val step = resting()
        assertEquals(benchRest, step.model.rest)
        assertEquals(RestScreen(1, 0, "bench"), step.model.restScreen)
        assertTrue(showsRestScreen(step.model))
    }

    @Test fun `with the setting off a rest starts as the bar, and a tap on it still opens the screen`() {
        val step = started().send(Msg.Settings.SetRestFullScreen(false)).send(WorkoutEvent.TapSet(1, 0))
        assertEquals(benchRest, step.model.rest)
        assertEquals(RestScreen(1, 0, "bench", minimised = true), step.model.restScreen)
        assertFalse(showsRestScreen(step.model))
        assertTrue(showsRestScreen(step.send(Msg.Rest.Open).model))
    }

    @Test fun `every way a rest starts opens the screen`() {
        assertEquals(RestScreen(1, 0, "bench"), resting().send(WorkoutEvent.TapSet(1, 0)).model.restScreen)
        // A count entered for a max-reps set.
        assertEquals(RestScreen(3, 1, "dips"), started().send(WorkoutEvent.LogReps(3, 1, 8)).model.restScreen)
        // One tap on a max-reps set with a last time to go by.
        val again = started().send(WorkoutEvent.LogReps(3, 0, 8)).send(WorkoutEvent.RequestFinish).send(WorkoutEvent.Confirm)
            .send(Msg.Routines.Select("A")).send(WorkoutEvent.Start)
        val tapped = again.send(WorkoutEvent.TapSet(3, 0))
        assertEquals(8, tapped.workout.exercises[3].sets[0])
        assertEquals(RestScreen(3, 0, "dips"), tapped.model.restScreen)
        // A hold the timer logged.
        val holding = started().send(WorkoutEvent.AddExercise("plank")).send(WorkoutEvent.TapSet(4, 0))
        val held = holding.send(Msg.Hold.Tick, now = LATER + 5_000 + 60_000)
        assertEquals(60, held.workout.exercises[4].sets[0])
        assertEquals(RestScreen(4, 0, "plank"), held.model.restScreen)
    }

    @Test fun `no rest after the last set of an exercise, so no screen with a clock`() {
        var step = started()
        for (set in 0..3) step = step.send(WorkoutEvent.TapSet(1, set))
        assertEquals(RestScreen(1, 3, "bench"), step.model.restScreen)
        val last = step.send(WorkoutEvent.TapSet(1, 4))
        assertNull(last.model.rest)
        // Only the screen that rates the set, see RirTest.
        assertEquals(RestScreen(1, 4, "bench", setDone = true), last.model.restScreen)
        val untracked = step.send(Msg.Settings.SetTrackRir(false)).send(WorkoutEvent.TapSet(1, 4))
        assertNull(untracked.model.rest)
        assertNull(untracked.model.restScreen)
    }

    @Test fun `minimised it leaves the rest running in the bar, a tap on the bar brings it back`() {
        val down = resting().send(Msg.Rest.Minimise, now = LATER + 5_000)
        assertEquals(RestScreen(1, 0, "bench", minimised = true), down.model.restScreen)
        assertEquals(benchRest, down.model.rest)
        assertTrue(down.cmds.isEmpty())
        assertFalse(showsRestScreen(down.model))
        assertIgnored(down, Msg.Rest.Minimise)

        val up = down.send(Msg.Rest.Open, now = LATER + 7_000)
        assertEquals(RestScreen(1, 0, "bench"), up.model.restScreen)
        assertEquals(LATER + 7_000, up.model.now)
        assertTrue(up.cmds.isEmpty())
        assertIgnored(resting(), Msg.Rest.Open)
        assertIgnored(started(), Msg.Rest.Open)
        assertIgnored(started(), Msg.Rest.Minimise)

        // The next rest opens its screen again, for its own set.
        assertEquals(RestScreen(1, 1, "bench"), down.send(WorkoutEvent.TapSet(1, 1)).model.restScreen)
    }

    @Test fun `back puts the screen away before it closes the workout`() {
        val down = resting().send(Msg.Nav.Back)
        assertTrue(down.model.restScreen!!.minimised)
        assertTrue(showsWorkout(down.model))
        assertEquals(benchRest, down.model.rest)
        assertFalse(down.send(Msg.Nav.Back).model.nav.workoutOpen)
    }

    @Test fun `next set ends the rest and closes the screen`() {
        val next = resting().send(Msg.Rest.Dismiss)
        assertNull(next.model.rest)
        assertNull(next.model.restScreen)
        assertTrue(Cmd.CancelRestAlarms in next.cmds)
        assertTrue(Cmd.HideRestCountdown in next.cmds)
    }

    @Test fun `correcting the reps rewrites the set and leaves the rest alone`() {
        val step = resting().send(WorkoutEvent.CorrectReps(1, 0, 3), now = LATER + 20_000)
        assertEquals(listOf(3, null, null, null, null), step.workout.exercises[1].sets)
        assertEquals(benchRest, step.model.rest)
        assertEquals(RestScreen(1, 0, "bench"), step.model.restScreen)
        assertTrue(step.restCmds().isEmpty())
        assertTrue(step.uiEvents.isEmpty())
        assertNull(step.model.repsQuestion)
        assertTrue(Cmd.Save(step.state) in step.cmds)

        // 0 is a set with no reps, not an open set.
        val none = step.send(WorkoutEvent.CorrectReps(1, 0, 0))
        assertEquals(0, none.workout.exercises[1].sets[0])
        assertNotNull(none.model.restScreen)
        assertEquals(benchRest, none.model.rest)
    }

    @Test fun `a max-reps set is corrected up to 100 without asking`() {
        val step = started().send(WorkoutEvent.LogReps(3, 0, 8)).send(WorkoutEvent.CorrectReps(3, 0, 100))
        assertEquals(100, step.workout.exercises[3].sets[0])
        assertNull(step.model.repsQuestion)
        assertIgnored(step, WorkoutEvent.CorrectReps(3, 0, 101))
    }

    @Test fun `a correction that makes no sense changes nothing`() {
        val step = resting()
        assertIgnored(step, WorkoutEvent.CorrectReps(1, 0, 5))
        assertIgnored(step, WorkoutEvent.CorrectReps(1, 0, 6))
        assertIgnored(step, WorkoutEvent.CorrectReps(1, 0, -1))
        assertIgnored(step, WorkoutEvent.CorrectReps(1, 1, 3))
        assertIgnored(step, WorkoutEvent.CorrectReps(9, 0, 3))
        assertIgnored(stepOf(), WorkoutEvent.CorrectReps(1, 0, 3))
        // A timed set is logged by its timer only.
        val held = started().send(WorkoutEvent.AddExercise("plank")).send(WorkoutEvent.LogHold(4, 0, 40))
        assertIgnored(held, WorkoutEvent.CorrectReps(4, 0, 30))
    }

    @Test fun `the weight wheel changes the whole exercise and leaves the rest alone`() {
        val step = resting().send(WorkoutEvent.SetWeight(1, 82.5), now = LATER + 20_000)
        assertEquals(82.5, step.workout.exercises[1].weight, 0.0)
        assertEquals(82.5, step.state.progress.getValue("bench").weight, 0.0)
        assertEquals(benchRest, step.model.rest)
        assertEquals(RestScreen(1, 0, "bench"), step.model.restScreen)
        assertTrue(step.restCmds().isEmpty())
    }

    @Test fun `minus 15 seconds moves the end, the alarms and the notification`() {
        val step = resting().send(Msg.Rest.Shorten, now = LATER + 10_000)
        val rest = benchRest.copy(endsAt = LATER + 75_000)
        assertEquals(rest, step.model.rest)
        assertTrue(Cmd.SaveRest(rest) in step.cmds)
        assertTrue(Cmd.ScheduleRestAlarms(tenAt = LATER + 65_000, overAt = LATER + 75_000) in step.cmds)
        assertTrue(Cmd.ShowRestCountdown(restCountdown(rest)) in step.cmds)
        assertEquals(RestScreen(1, 0, "bench"), step.model.restScreen)
    }

    @Test fun `minus 15 seconds never goes past now and does nothing once the rest is over`() {
        val almost = resting().send(Msg.Rest.Shorten, now = LATER + 80_000)
        assertEquals(LATER + 80_000, almost.model.rest!!.endsAt)
        assertTrue(Cmd.ScheduleRestAlarms(tenAt = null, overAt = LATER + 80_000) in almost.cmds)
        // The alarm at the new end plays the cue as at any end.
        assertTrue(almost.send(Msg.Rest.AlarmOver, now = LATER + 80_000).cmds.any { it is Cmd.Vibrate })
        val over = resting().send(Msg.Rest.Shorten, now = LATER + 95_000)
        assertEquals(benchRest, over.model.rest)
        assertTrue(over.cmds.isEmpty())
        assertIgnored(started(), Msg.Rest.Shorten)
    }

    @Test fun `plus 15 seconds adds to the end, counted from now once the rest is over`() {
        val longer = resting().send(Msg.Rest.Lengthen, now = LATER + 10_000)
        assertEquals(LATER + 105_000, longer.model.rest!!.endsAt)
        assertTrue(Cmd.ScheduleRestAlarms(tenAt = LATER + 95_000, overAt = LATER + 105_000) in longer.cmds)
        assertTrue(Cmd.HideRestOver in longer.cmds)
        val late = resting().send(Msg.Rest.Lengthen, now = LATER + 100_000)
        assertEquals(LATER + 115_000, late.model.rest!!.endsAt)
        assertNotNull(late.model.restScreen)
        assertIgnored(started(), Msg.Rest.Lengthen)
    }

    @Test fun `the screen closes when its set or its workout is gone, the rest follows today's rules`() {
        val step = resting()
        // Its exercise removed, or another one taking its place.
        assertNull(step.send(WorkoutEvent.RemoveExercise(1)).model.restScreen)
        assertNull(step.send(WorkoutEvent.RemoveExercise(0)).model.restScreen)
        assertNull(step.send(WorkoutEvent.Defer(0)).model.restScreen)
        assertNotNull(step.send(WorkoutEvent.RemoveExercise(0)).model.rest)
        // A change behind it leaves it where it is.
        assertEquals(RestScreen(1, 0, "bench"), step.send(WorkoutEvent.RemoveExercise(2)).model.restScreen)
        assertEquals(RestScreen(1, 0, "bench"), step.send(WorkoutEvent.Defer(2)).model.restScreen)
        // Finishing asks first; the question still shows the workout.
        val asking = step.send(WorkoutEvent.RequestFinish)
        assertTrue(asking.session is Session.ConfirmingFinish)
        assertNotNull(asking.model.restScreen)
        assertNull(asking.send(WorkoutEvent.Confirm).model.restScreen)
        assertNull(step.send(WorkoutEvent.RequestDiscard).send(WorkoutEvent.Confirm).model.restScreen)
        assertNull(step.send(Msg.Data.Reset).model.restScreen)
    }

    @Test fun `opening a hold timer ends the rest and its screen`() {
        val step = started().send(WorkoutEvent.AddExercise("plank")).send(WorkoutEvent.TapSet(1, 0))
        assertNotNull(step.model.restScreen)
        val holding = step.send(WorkoutEvent.TapSet(4, 0))
        assertNotNull(holding.model.hold)
        assertNull(holding.model.rest)
        assertNull(holding.model.restScreen)
    }

    @Test fun `the weight wheel runs from the empty bar to 400kg and always holds the weight`() {
        val bar = weightChoices(Equipment.BARBELL, WeightUnit.KG, bar = 20.0, current = 80.0)
        assertEquals(20.0, bar.first(), 0.0)
        assertEquals(400.0, bar.last(), 0.0)
        assertEquals(153, bar.size)
        assertTrue(80.0 in bar && 82.5 in bar)
        // Off the grid and above the top.
        val odd = weightChoices(Equipment.BARBELL, WeightUnit.KG, bar = 20.0, current = 81.0)
        assertEquals(listOf(80.0, 81.0, 82.5), odd.filter { it in 80.0..82.5 })
        assertEquals(402.5, weightChoices(Equipment.BARBELL, WeightUnit.KG, 20.0, current = 402.5).last(), 0.0)
        // A machine and a weight start one step up, a bodyweight exercise with nothing added.
        assertEquals(2.5, weightChoices(Equipment.MACHINE, WeightUnit.KG, 20.0, 50.0).first(), 0.0)
        assertEquals(listOf(1.0, 2.0, 3.0), weightChoices(Equipment.WEIGHT, WeightUnit.KG, 20.0, 10.0).take(3))
        assertEquals(listOf(0.0, 1.0, 2.0), weightChoices(Equipment.BODYWEIGHT, WeightUnit.KG, 20.0, 0.0).take(3))
        // Pounds: from the 45lb bar to 900lb.
        val pounds = weightChoices(Equipment.BARBELL, WeightUnit.LB, 45.0, 135.0)
        assertEquals(listOf(45.0, 50.0), pounds.take(2))
        assertEquals(900.0, pounds.last(), 0.0)
        assertEquals(listOf(2.5, 5.0), weightChoices(Equipment.BODYWEIGHT, WeightUnit.LB, 45.0, 0.0).drop(1).take(2))
    }

    @Test fun `the reps wheel runs to the target, or to 100 for a max-reps set`() {
        assertEquals(0..5, repsChoices(5, 4))
        assertEquals(0..100, repsChoices(null, 8))
        assertEquals(0..120, repsChoices(null, 120))
    }
}

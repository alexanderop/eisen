package io.github.alexanderop.eisen

import io.github.alexanderop.eisen.app.elm.ActivitySheet
import io.github.alexanderop.eisen.app.elm.HoldTimer
import io.github.alexanderop.eisen.app.elm.Msg
import io.github.alexanderop.eisen.app.elm.Overlay
import io.github.alexanderop.eisen.app.elm.OverlayEvent
import io.github.alexanderop.eisen.app.elm.RepsQuestion
import io.github.alexanderop.eisen.app.elm.RestScreen
import io.github.alexanderop.eisen.app.elm.WorkoutEvent
import io.github.alexanderop.eisen.app.elm.overlayAfter
import io.github.alexanderop.eisen.app.elm.repsQuestion
import io.github.alexanderop.eisen.app.elm.restScreen
import io.github.alexanderop.eisen.feature.timer.TimerMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * What lies over the workout screen and the history's log (`app/elm/Overlay.kt`): one test per transition of [overlayAfter],
 * then the gaps the one value closed, through `update`. Workout A: hackSquat, bench, row, dips (max reps).
 */
class OverlayTest {
    private val shown = RestScreen.Shown(1, 0, "bench")
    private val minimised = RestScreen.Minimised(1, 0, "bench")
    private val done = RestScreen.SetDone(1, 4, "bench")
    private val hold = HoldTimer(4, 0, "plank", 60, startsAt = 1_000)
    private val question = RepsQuestion(3, 0, "dips", 8)
    private val sheet = ActivitySheet("t1")
    private val all: List<Overlay?> = listOf(null, shown, minimised, done, hold, question, question.copy(under = minimised), sheet)

    private fun after(overlay: Overlay?, event: OverlayEvent) = overlayAfter(overlay, event)

    @Test fun `a rest or the last set opens its screen over a screen or a question, never under a hold timer`() {
        for (screen in listOf(shown, minimised, done)) {
            for (before in all) assertEquals("$before", if (before is HoldTimer) before else screen, after(before, OverlayEvent.Rest(screen)))
        }
    }

    @Test fun `a question goes over the rest screen and keeps it put away, replaces a question, never a hold timer`() {
        val next = RepsQuestion(3, 1, "dips", 10)
        assertEquals(next, after(null, OverlayEvent.AskReps(next)))
        assertEquals(next.copy(under = minimised), after(minimised, OverlayEvent.AskReps(next)))
        // QA build 19: a question over the full screen dropped it, and the rest bar no longer opened anything.
        assertEquals(next.copy(under = minimised), after(shown, OverlayEvent.AskReps(next)))
        assertEquals(next, after(done, OverlayEvent.AskReps(next)))
        assertEquals(next, after(sheet, OverlayEvent.AskReps(next)))
        assertEquals(next.copy(under = minimised), after(question.copy(under = minimised), OverlayEvent.AskReps(next)))
        assertEquals(hold, after(hold, OverlayEvent.AskReps(next)))
    }

    @Test fun `an answer or a closed question gives back what it covered`() {
        for (event in listOf(OverlayEvent.Answered, OverlayEvent.CloseReps)) {
            assertNull(after(question, event))
            assertEquals(minimised, after(question.copy(under = minimised), event))
            for (before in listOf(null, shown, minimised, done, hold, sheet)) assertEquals("$before $event", before, after(before, event))
        }
    }

    @Test fun `a hold timer opens over anything and closes to nothing`() {
        for (before in all) assertEquals("$before", hold, after(before, OverlayEvent.Hold(hold)))
        assertNull(after(hold, OverlayEvent.HoldClosed))
        for (before in all - hold) assertEquals("$before", before, after(before, OverlayEvent.HoldClosed))
    }

    @Test fun `a full rest screen minimises to the bar, the set done screen goes, the bar opens again`() {
        assertEquals(minimised, after(shown, OverlayEvent.Minimise))
        assertNull(after(done, OverlayEvent.Minimise))
        for (before in listOf(null, minimised, hold, question, sheet)) assertEquals("$before", before, after(before, OverlayEvent.Minimise))
        assertEquals(shown, after(minimised, OverlayEvent.Open))
        for (before in listOf(null, shown, done, hold, question, sheet)) assertEquals("$before", before, after(before, OverlayEvent.Open))
    }

    @Test fun `a rating closes the screen it was chosen on, and only that one`() {
        assertNull(after(done, OverlayEvent.Rated(done)))
        assertNull(after(shown, OverlayEvent.Rated(shown)))
        assertEquals(done, after(done, OverlayEvent.Rated(done.copy(set = 3))))
        for (before in listOf(null, hold, question, sheet)) assertEquals("$before", before, after(before, OverlayEvent.Rated(done)))
    }

    @Test fun `a tapped timer of the history opens its sheet over anything, and closing it closes only the sheet`() {
        val other = ActivitySheet("t2")
        for (before in all) assertEquals("$before", other, after(before, OverlayEvent.PickActivity(other)))
        assertNull(after(sheet, OverlayEvent.ActivityClosed))
        for (before in all - sheet) assertEquals("$before", before, after(before, OverlayEvent.ActivityClosed))
    }

    @Test fun `a reorder closes a hold timer only, an erase or a backup everything`() {
        assertNull(after(hold, OverlayEvent.Reordered))
        for (before in all - hold) assertEquals("$before", before, after(before, OverlayEvent.Reordered))
        for (before in all) assertNull("$before", after(before, OverlayEvent.Cleared))
    }

    // The gaps of the loose fields, through update. Each of these held on to the reps question before.

    private fun asked(): Step = started().send(WorkoutEvent.TapSet(3, 0))

    @Test fun `an interval timer that starts takes the reps question away`() {
        val step = asked()
        assertEquals(question, step.model.repsQuestion)
        val running = step.send(Msg.Timer.Open(TimerMode.TABATA)).send(Msg.Timer.Start)
        assertNull(running.model.overlay)
    }

    @Test fun `closing the workout takes the reps question away, so it does not come back stale`() {
        val closed = asked().send(Msg.Nav.CloseWorkout)
        assertNull(closed.model.overlay)
        assertNull(closed.send(Msg.Nav.OpenWorkout).model.repsQuestion)
    }

    @Test fun `the question names its exercise, so another one in its place does not inherit it, and it follows its own`() {
        // The first exercise goes and a new one comes in at the end: place 3 is the lat pulldown now, not dips.
        val moved = asked().send(WorkoutEvent.RemoveExercise(0)).send(WorkoutEvent.AddExercise("latPulldown"))
        assertEquals("latPulldown", moved.workout.exercises[3].id)
        val question = moved.model.repsQuestion
        assertEquals("dips", question?.exerciseId)
        assertEquals(2, question?.exercise)
        // Its own exercise gone, it goes too.
        assertNull(moved.send(WorkoutEvent.RemoveExercise(2)).model.repsQuestion)
    }

    @Test fun `a question asked over the rest bar gives the bar back its screen when it closes`() {
        val bar = started().send(WorkoutEvent.TapSet(1, 0)).send(Msg.Rest.Minimise)
        assertEquals(minimised, bar.model.restScreen)
        val asking = bar.send(WorkoutEvent.TapSet(3, 0))
        assertEquals(question.copy(under = minimised), asking.model.overlay)
        assertEquals(minimised, asking.send(Msg.WorkoutScreen.CloseRepsEntry).model.overlay)
        // Answered, a new rest opens its own screen.
        assertEquals(RestScreen.Shown(3, 0, "dips"), asking.send(WorkoutEvent.LogReps(3, 0, 9)).model.overlay)
    }
}

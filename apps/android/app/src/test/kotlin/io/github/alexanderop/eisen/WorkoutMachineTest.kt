package io.github.alexanderop.eisen

import io.github.alexanderop.eisen.domain.AppState
import io.github.alexanderop.eisen.domain.Effect
import io.github.alexanderop.eisen.domain.Env
import io.github.alexanderop.eisen.domain.ExerciseProgress
import io.github.alexanderop.eisen.domain.FinishReason
import io.github.alexanderop.eisen.domain.Feel
import io.github.alexanderop.eisen.domain.Routine
import io.github.alexanderop.eisen.domain.Run
import io.github.alexanderop.eisen.domain.Session
import io.github.alexanderop.eisen.domain.Stage
import io.github.alexanderop.eisen.domain.Transition
import io.github.alexanderop.eisen.domain.WeightUnit
import io.github.alexanderop.eisen.domain.Workout
import io.github.alexanderop.eisen.domain.WorkoutEvent
import io.github.alexanderop.eisen.domain.WorkoutEvent.AddExercise
import io.github.alexanderop.eisen.domain.WorkoutEvent.Cancel
import io.github.alexanderop.eisen.domain.WorkoutEvent.Confirm
import io.github.alexanderop.eisen.domain.WorkoutEvent.Defer
import io.github.alexanderop.eisen.domain.WorkoutEvent.Forget
import io.github.alexanderop.eisen.domain.WorkoutEvent.LogReps
import io.github.alexanderop.eisen.domain.WorkoutEvent.Move
import io.github.alexanderop.eisen.domain.WorkoutEvent.RemoveExercise
import io.github.alexanderop.eisen.domain.WorkoutEvent.RequestDiscard
import io.github.alexanderop.eisen.domain.WorkoutEvent.RequestFinish
import io.github.alexanderop.eisen.domain.WorkoutEvent.Rise
import io.github.alexanderop.eisen.domain.WorkoutEvent.SetWeight
import io.github.alexanderop.eisen.domain.WorkoutEvent.SetWorkingWeight
import io.github.alexanderop.eisen.domain.WorkoutEvent.Start
import io.github.alexanderop.eisen.domain.WorkoutEvent.TapSet
import io.github.alexanderop.eisen.domain.WorkoutEvent.Undo
import io.github.alexanderop.eisen.domain.active
import io.github.alexanderop.eisen.domain.canDefer
import io.github.alexanderop.eisen.domain.editProgress
import io.github.alexanderop.eisen.domain.freshState
import io.github.alexanderop.eisen.domain.order
import io.github.alexanderop.eisen.domain.stageOf
import io.github.alexanderop.eisen.domain.transition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The workout state machine, one test per transition of the diagram in `WorkoutMachine.kt`,
 * plus the ignored events and the effects of each kind of tap.
 * Workout A: Hack Squat 5×5, Bench Press 5×5, Barbell Row 5×5, Dips 3×max.
 */
class WorkoutMachineTest {
    private val startedAt = 1_000L
    private val env = Env(startedAt) { "w1" }

    private fun Transition.send(event: WorkoutEvent, now: Long = startedAt + 60_000): Transition =
        transition(session, state, event, Env(now) { "w2" })

    private fun started(state: AppState = freshState(WeightUnit.KG)): Transition = transition(Session.Idle, state, Start, env)

    private val Transition.run: Run get() = (session as Session.WithRun).run
    private val Transition.workout: Workout get() = run.workout

    /** Taps every set of an exercise once: all reps. */
    private fun Transition.full(exercise: Int): Transition =
        workout.exercises[exercise].sets.indices.fold(this) { at, set -> at.send(TapSet(exercise, set)) }

    /** Every set of the workout logged, max-reps sets with 10. */
    private fun Transition.everything(): Transition = workout.exercises.indices.fold(this) { at, index ->
        val log = at.workout.exercises[index]
        if (log.targetReps != null) at.full(index) else log.sets.indices.fold(at) { inner, set -> inner.send(LogReps(index, set, 10)) }
    }

    private fun assertIgnored(before: Transition, event: WorkoutEvent) {
        val after = before.send(event)
        assertSame(before.session, after.session)
        assertEquals(before.state, after.state)
        assertTrue(after.effects.isEmpty())
    }

    // Idle

    @Test fun `Start turns the routine offered next into a running workout`() {
        val result = started()
        val running = result.session as Session.Running
        assertEquals(listOf("hackSquat", "bench", "row", "dips"), running.workout.exercises.map { it.id })
        assertEquals("w1", running.workout.id)
        assertEquals(startedAt, running.workout.startedAt)
        assertEquals(running.workout, result.state.active)
        assertEquals(result.state.progress, running.run.baseline)
        assertEquals(listOf(Effect.Started(running.workout)), result.effects)
        assertEquals(Stage.FRESH, stageOf(running.workout))
    }

    @Test fun `Start does nothing when the routine has no exercise left`() {
        val empty = freshState(WeightUnit.KG).let { it.copy(routines = listOf(Routine("A", "Empty", emptyList())), nextRoutineId = "A") }
        val result = transition(Session.Idle, empty, Start, env)
        assertEquals(Session.Idle, result.session)
        assertNull(result.state.active)
        assertTrue(result.effects.isEmpty())
    }

    // Taps

    @Test fun `a full set starts the rest for done sets`() {
        val result = started().send(TapSet(0, 0))
        assertEquals(5, result.workout.exercises[0].sets[0])
        assertEquals(listOf(Effect.Haptic(Feel.TAP), Effect.StartRest(90, "All reps done.")), result.effects)
        assertEquals(result.workout, result.state.active)
    }

    @Test fun `a missed rep warns and starts the longer rest`() {
        val result = started().send(TapSet(0, 0)).send(TapSet(0, 0))
        assertEquals(4, result.workout.exercises[0].sets[0])
        assertEquals(listOf(Effect.Haptic(Feel.WARNING), Effect.StartRest(300, "Missed reps. Rest longer.")), result.effects)
    }

    @Test fun `tapping cycles target down to 0 and clearing stops the rest`() {
        var at = started()
        val seen = mutableListOf<Int?>()
        repeat(7) {
            at = at.send(TapSet(0, 0))
            seen += at.workout.exercises[0].sets[0]
        }
        assertEquals(listOf(5, 4, 3, 2, 1, 0, null), seen)
        assertEquals(listOf(Effect.Haptic(Feel.TAP), Effect.StopRest), at.effects)
    }

    @Test fun `the last set of an exercise feels like a success and leaves no rest behind`() {
        val four = (0 until 4).fold(started()) { at, set -> at.send(TapSet(0, set)) }
        assertEquals(Effect.StartRest(90, "All reps done."), four.effects.last())
        val result = four.send(TapSet(0, 4))
        assertEquals(listOf(Effect.Haptic(Feel.SUCCESS), Effect.StopRest), result.effects)
        // The workout itself is far from done: the timer goes because the exercise is.
        assertEquals(Stage.UNDERWAY, stageOf(result.workout))
    }

    @Test fun `a last set with missed reps leaves no rest behind either`() {
        val done = (0 until 5).fold(started()) { at, set -> at.send(TapSet(0, set)) }
        val missed = done.send(TapSet(0, 4))
        assertEquals(4, missed.workout.exercises[0].sets[4])
        assertEquals(listOf(Effect.Haptic(Feel.WARNING), Effect.StopRest), missed.effects)
    }

    @Test fun `the last max-reps set of an exercise leaves no rest behind`() {
        val two = started().send(LogReps(3, 0, 12)).send(LogReps(3, 1, 10))
        assertEquals(Effect.StartRest(90, "Max set logged."), two.effects.last())
        assertEquals(listOf(Effect.Haptic(Feel.TAP), Effect.StopRest), two.send(LogReps(3, 2, 8)).effects)
    }

    @Test fun `the set that completes the workout stops the rest`() {
        // Everything logged, then the last hack squat set tapped round to empty again.
        val almost = (1..6).fold(started().everything()) { at, _ -> at.send(TapSet(0, 4)) }
        assertNull(almost.workout.exercises[0].sets[4])
        val result = almost.send(TapSet(0, 4))
        assertEquals(Stage.COMPLETE, stageOf(result.workout))
        assertEquals(listOf(Effect.Haptic(Feel.SUCCESS), Effect.StopRest), result.effects)
    }

    @Test fun `a max-reps set asks for the count, starting at last time`() {
        val fresh = started().send(TapSet(3, 0))
        assertEquals(listOf(Effect.AskReps(3, 0, 8)), fresh.effects)
        assertEquals(started().session, fresh.session)

        val last = started().workout.copy(id = "old", exercises = started().workout.exercises.map { if (it.id == "dips") it.copy(sets = listOf(12, 10, 9)) else it })
        val withHistory = started(freshState(WeightUnit.KG).copy(history = listOf(last)))
        assertEquals(listOf(Effect.AskReps(3, 1, 10)), withHistory.send(TapSet(3, 1)).effects)
    }

    @Test fun `logging a count starts the rest and clearing it stops it`() {
        val logged = started().send(LogReps(3, 0, 12))
        assertEquals(12, logged.workout.exercises[3].sets[0])
        assertEquals(listOf(Effect.Haptic(Feel.TAP), Effect.StartRest(90, "Max set logged.")), logged.effects)
        val cleared = logged.send(LogReps(3, 0, null))
        assertNull(cleared.workout.exercises[3].sets[0])
        assertEquals(listOf(Effect.Haptic(Feel.TAP), Effect.StopRest), cleared.effects)
    }

    // Editing the running workout

    @Test fun `SetWeight changes the log and the working weight`() {
        val result = started().send(SetWeight(1, 42.5))
        assertEquals(42.5, result.workout.exercises[1].weight, 0.0)
        assertEquals(42.5, result.state.progress.getValue("bench").weight, 0.0)
        // Dips take a weight too now (added weight, see below); only an exercise that is not there is ignored.
        assertIgnored(started(), SetWeight(9, 10.0))
    }

    @Test fun `AddExercise adds it once, with a working weight`() {
        val added = started().send(AddExercise("dumbbellRow"))
        assertEquals("dumbbellRow", added.workout.exercises.last().id)
        assertTrue("dumbbellRow" in added.state.progress)
        assertIgnored(added, AddExercise("dumbbellRow"))
        assertIgnored(added, AddExercise("nope"))
    }

    @Test fun `RemoveExercise keeps the last exercise`() {
        val removed = started().send(RemoveExercise(1))
        assertEquals(listOf("hackSquat", "row", "dips"), removed.workout.exercises.map { it.id })
        val one = removed.send(RemoveExercise(0)).send(RemoveExercise(0))
        assertEquals(listOf("dips"), one.workout.exercises.map { it.id })
        assertIgnored(one, RemoveExercise(0))
    }

    @Test fun `Defer moves the exercise behind the others and out of the ranking`() {
        val worked = started().send(TapSet(1, 0)).send(Rise("bench"))
        assertEquals(listOf("bench"), worked.run.ranking)
        val deferred = worked.send(Defer(1))
        assertEquals(listOf("hackSquat", "row", "dips", "bench"), deferred.workout.exercises.map { it.id })
        assertTrue(deferred.run.ranking.isEmpty())
    }

    // Order

    @Test fun `Rise puts the exercise worked on first, unless it is untouched or finished`() {
        val worked = started().send(TapSet(2, 0)).send(Rise("row"))
        assertEquals(listOf("row", "hackSquat", "bench", "dips"), order(worked.run, null).map { it.log.id })
        assertEquals(listOf("row"), worked.send(Rise("bench")).run.ranking)
        val finished = worked.full(2).send(Rise("row"))
        assertTrue(finished.run.ranking.isEmpty())
        assertEquals(listOf("hackSquat", "bench", "dips", "row"), order(finished.run, null).map { it.log.id })
    }

    @Test fun `the exercise tapped last counts as open while it settles`() {
        val done = started().full(0)
        assertEquals("hackSquat", order(done.run, settling = "hackSquat").first().log.id)
        val placed = order(done.run, settling = null)
        assertEquals("hackSquat", placed.last().log.id)
        assertTrue(placed.last().done)
    }

    @Test fun `Move reorders the open exercises and keeps finished ones in their places`() {
        val result = started().full(1).send(Move(0, 2))
        // Open in view: hackSquat, row, dips. hackSquat goes last; bench (done) keeps index 1.
        assertEquals(listOf("row", "bench", "dips", "hackSquat"), result.workout.exercises.map { it.id })
        assertTrue(result.run.arranged)
        assertEquals(listOf("row", "dips", "hackSquat", "bench"), order(result.run, null).map { it.log.id })
        assertEquals(result.workout, result.state.active)
    }

    @Test fun `Move starts from the order on screen, ranking included`() {
        val ranked = started().send(TapSet(2, 0)).send(Rise("row"))
        val result = ranked.send(Move(1, 0))
        assertEquals(listOf("hackSquat", "row", "bench", "dips"), order(result.run, null).map { it.log.id })
    }

    @Test fun `after a manual move the exercise worked on no longer rises`() {
        val moved = started().send(Move(0, 1))
        val worked = moved.send(TapSet(3, 0)).send(LogReps(3, 0, 5))
        assertIgnored(worked, Rise("dips"))
        assertEquals(listOf("bench", "hackSquat", "row", "dips"), order(worked.run, null).map { it.log.id })
    }

    @Test fun `Move outside the open exercises is ignored`() {
        val done = started().full(0)
        assertIgnored(done, Move(0, 3))
        assertIgnored(done, Move(1, 1))
        assertIgnored(done, Move(-1, 0))
    }

    @Test fun `Do later needs another open exercise and an unfinished one`() {
        val placed = order(started().full(0).run, null)
        assertTrue(canDefer(placed, placed[0]))
        assertFalse(canDefer(placed, placed.last()))
        val single = started().send(RemoveExercise(0)).send(RemoveExercise(0)).send(RemoveExercise(0))
        val alone = order(single.run, null)
        assertFalse(canDefer(alone, alone[0]))
    }

    // Finishing

    @Test fun `RequestFinish on a complete workout saves it at once`() {
        val done = started().everything()
        val saved = done.send(RequestFinish, now = 30 * 60_000)
        assertEquals(Session.Idle, saved.session)
        assertNull(saved.state.active)
        val finished = saved.state.history.single()
        assertEquals(30L * 60_000, finished.finishedAt)
        // applyProgression: hack squat 80 kg + 5 kg after all reps.
        assertEquals(85.0, saved.state.progress.getValue("hackSquat").weight, 0.0)
        assertEquals("B", saved.state.nextRoutineId)
        val savedEffect = saved.effects[2] as Effect.Saved
        assertEquals(finished, savedEffect.workout)
        assertEquals(3, savedEffect.changes.size)
        assertEquals(listOf(Effect.Haptic(Feel.DONE), Effect.StopRest), saved.effects.take(2))
    }

    @Test fun `RequestFinish with sets open asks first, Confirm saves`() {
        val underway = started().send(TapSet(0, 0))
        val asking = underway.send(RequestFinish)
        assertEquals(Session.ConfirmingFinish(underway.run, FinishReason.EARLY), asking.session)
        assertEquals(underway.workout, asking.state.active)
        assertTrue(asking.effects.isEmpty())
        val saved = asking.send(Confirm)
        assertEquals(Session.Idle, saved.session)
        assertEquals(1, saved.state.history.size)
        assertTrue(saved.effects.last() is Effect.Saved)
    }

    @Test fun `Cancel on the finish question keeps the workout running`() {
        val underway = started().send(TapSet(0, 0))
        val back = underway.send(RequestFinish).send(Cancel)
        assertEquals(Session.Running(underway.run), back.session)
        assertEquals(underway.state, back.state)
    }

    @Test fun `finishing with nothing logged discards instead of saving`() {
        val asking = started().send(RequestFinish)
        assertEquals(FinishReason.NOTHING_LOGGED, (asking.session as Session.ConfirmingFinish).reason)
        val discarded = asking.send(Confirm)
        assertTrue(discarded.session is Session.Discarded)
        assertTrue(discarded.state.history.isEmpty())
        assertNull(discarded.state.active)
        assertEquals(listOf(Effect.StopRest, Effect.DiscardedWorkout(asking.workout)), discarded.effects)
    }

    // Discarding

    @Test fun `RequestDiscard with nothing logged discards at once`() {
        val fresh = started()
        val discarded = fresh.send(RequestDiscard)
        assertEquals(Session.Discarded(fresh.run, undo = emptyMap()), discarded.session)
        assertNull(discarded.state.active)
        assertEquals(listOf(Effect.StopRest, Effect.DiscardedWorkout(fresh.workout)), discarded.effects)
    }

    @Test fun `RequestDiscard with sets logged asks first, with a warning`() {
        val underway = started().send(TapSet(0, 0))
        val asking = underway.send(RequestDiscard)
        assertEquals(Session.ConfirmingDiscard(underway.run), asking.session)
        assertEquals(underway.workout, asking.state.active)
        assertEquals(listOf(Effect.Haptic(Feel.WARNING)), asking.effects)
    }

    @Test fun `Cancel on the discard question keeps the workout running`() {
        val underway = started().send(TapSet(0, 0))
        val back = underway.send(RequestDiscard).send(Cancel)
        assertEquals(Session.Running(underway.run), back.session)
    }

    @Test fun `Confirm on the discard question discards and stops the rest`() {
        val underway = started().send(TapSet(0, 0))
        val discarded = underway.send(RequestDiscard).send(Confirm)
        assertTrue(discarded.session is Session.Discarded)
        assertNull(discarded.state.active)
        assertTrue(discarded.state.history.isEmpty())
        assertEquals(listOf(Effect.StopRest, Effect.DiscardedWorkout(underway.workout)), discarded.effects)
    }

    @Test fun `Undo brings back exactly the workout, without its rest`() {
        val underway = started().send(TapSet(0, 0)).send(TapSet(2, 0)).send(Rise("row")).send(Move(0, 1))
        val restored = underway.send(RequestDiscard).send(Confirm).send(Undo)
        assertEquals(Session.Running(underway.run), restored.session)
        assertEquals(underway.state, restored.state)
        assertTrue(restored.effects.isEmpty())
    }

    @Test fun `Forget drops the discarded workout for good`() {
        val forgotten = started().send(RequestDiscard).send(Forget)
        assertEquals(Session.Idle, forgotten.session)
        assertIgnored(forgotten, Undo)
    }

    @Test fun `Start after a discard begins a new workout and forgets the old one`() {
        val discarded = started().send(RequestDiscard)
        val again = discarded.send(Start)
        val running = again.session as Session.Running
        assertEquals("w2", running.workout.id)
        assertEquals(listOf(Effect.Started(running.workout)), again.effects)
        assertIgnored(again, Undo)
    }

    @Test fun `discarding puts back the weights and increments changed during the workout`() {
        val start = started()
        val before = start.state.progress
        val edited = start.send(SetWeight(1, 100.0)).send(AddExercise("dumbbellRow")).send(SetWorkingWeight("row", 70.0))
            .let { it.copy(state = it.state.editProgress("hackSquat") { entry -> entry.copy(increment = 10.0) }) }
            .send(TapSet(0, 0))
        assertEquals(100.0, edited.state.progress.getValue("bench").weight, 0.0)

        val discarded = edited.send(RequestDiscard).send(Confirm)
        assertEquals(before, discarded.state.progress)

        val restored = discarded.send(Undo)
        assertEquals(edited.state.progress, restored.state.progress)
        assertEquals(edited.workout, restored.workout)
    }

    @Test fun `a working weight set in Settings follows into the running workout only`() {
        val running = started().send(SetWorkingWeight("bench", 55.0))
        assertEquals(55.0, running.workout.exercises[1].weight, 0.0)
        assertEquals(55.0, running.state.active!!.exercises[1].weight, 0.0)

        val idle = transition(Session.Idle, freshState(WeightUnit.KG), SetWorkingWeight("bench", 55.0), env)
        assertEquals(Session.Idle, idle.session)
        assertEquals(55.0, idle.state.progress.getValue("bench").weight, 0.0)
    }

    // Added weight on a bodyweight exercise (Dips, the 4th exercise)

    private fun added(weight: Double) = ExerciseProgress(weight, fails = 0, increment = 0.0)

    @Test fun `starting a workout makes no entry for a bodyweight exercise`() {
        val result = started()
        assertFalse("dips" in result.state.progress)
        assertEquals(0.0, result.workout.exercises[3].weight, 0.0)
    }

    @Test fun `SetWeight on a bodyweight exercise sets its added weight for this and the next workouts`() {
        val result = started().send(SetWeight(3, 10.0))
        assertEquals(10.0, result.workout.exercises[3].weight, 0.0)
        assertEquals(10.0, result.state.active!!.exercises[3].weight, 0.0)
        assertEquals(added(10.0), result.state.progress["dips"])
    }

    @Test fun `an added weight set back to none removes its entry`() {
        val start = started()
        val result = start.send(SetWeight(3, 10.0)).send(SetWeight(3, 0.0))
        assertEquals(0.0, result.workout.exercises[3].weight, 0.0)
        assertEquals(start.state.progress, result.state.progress)
    }

    @Test fun `an added weight never goes below none`() {
        val result = started().send(SetWeight(3, -1.0))
        assertEquals(0.0, result.workout.exercises[3].weight, 0.0)
        assertFalse("dips" in result.state.progress)
    }

    @Test fun `finishing keeps the added weight as it is and the next workout starts with it`() {
        val saved = started().send(SetWeight(3, 10.0)).everything().send(RequestFinish)
        assertEquals(Session.Idle, saved.session)
        assertEquals(added(10.0), saved.state.progress["dips"])
        assertEquals(10.0, saved.state.history[0].exercises[3].weight, 0.0)
        assertTrue((saved.effects.last() as Effect.Saved).changes.none { it.id == "dips" })

        val nextA = saved.state.copy(nextRoutineId = "A")
        assertEquals(10.0, started(nextA).workout.exercises[3].weight, 0.0)
    }

    @Test fun `discarding removes an added weight set during the workout and Undo brings it back`() {
        val start = started()
        val edited = start.send(SetWeight(3, 10.0)).send(TapSet(0, 0))
        val discarded = edited.send(RequestDiscard).send(Confirm)
        assertEquals(start.state.progress, discarded.state.progress)
        assertFalse("dips" in discarded.state.progress)

        val restored = discarded.send(Undo)
        assertEquals(added(10.0), restored.state.progress["dips"])
        assertEquals(edited.state, restored.state)
    }

    @Test fun `discarding brings back an added weight cleared during the workout and Undo clears it again`() {
        val before = freshState(WeightUnit.KG).let { it.copy(progress = it.progress + ("dips" to added(5.0))) }
        val start = started(before)
        assertEquals(5.0, start.workout.exercises[3].weight, 0.0)
        val cleared = start.send(SetWeight(3, 0.0)).send(TapSet(0, 0))
        assertFalse("dips" in cleared.state.progress)

        val discarded = cleared.send(RequestDiscard).send(Confirm)
        assertEquals(start.state.progress, discarded.state.progress)

        val restored = discarded.send(Undo)
        assertEquals(cleared.state, restored.state)
    }

    @Test fun `an added weight set in Settings follows into the running workout`() {
        val running = started().send(SetWorkingWeight("dips", 5.0))
        assertEquals(5.0, running.workout.exercises[3].weight, 0.0)
        assertEquals(added(5.0), running.state.progress["dips"])

        val none = running.send(SetWorkingWeight("dips", 0.0))
        assertEquals(0.0, none.workout.exercises[3].weight, 0.0)
        assertFalse("dips" in none.state.progress)
    }

    // Ignored events

    @Test fun `events a state does not take are ignored`() {
        val idle = Transition(Session.Idle, freshState(WeightUnit.KG))
        for (event in listOf(TapSet(0, 0), LogReps(0, 0, 5), Confirm, Cancel, Undo, Forget, RequestFinish, RequestDiscard, Move(0, 1), Defer(0), Rise("bench"))) {
            assertIgnored(idle, event)
        }
        val running = started()
        for (event in listOf(Start, Confirm, Cancel, Undo, Forget, TapSet(9, 0), TapSet(0, 9))) assertIgnored(running, event)

        val confirming = running.send(TapSet(0, 0)).send(RequestFinish)
        for (event in listOf(Start, TapSet(0, 1), RequestDiscard, RequestFinish, Undo, Move(0, 1))) assertIgnored(confirming, event)

        val discarding = running.send(TapSet(0, 0)).send(RequestDiscard)
        for (event in listOf(Start, TapSet(0, 1), RequestFinish, Undo, Forget)) assertIgnored(discarding, event)

        val discarded = running.send(RequestDiscard)
        for (event in listOf(TapSet(0, 0), RequestFinish, RequestDiscard, Confirm, Cancel)) assertIgnored(discarded, event)
    }

    // Saved state

    @Test fun `only a running or confirming workout is saved`() {
        val run = started().run
        assertEquals(run.workout, Session.Running(run).active)
        assertEquals(run.workout, Session.ConfirmingFinish(run, FinishReason.EARLY).active)
        assertEquals(run.workout, Session.ConfirmingDiscard(run).active)
        assertNull(Session.Discarded(run, emptyMap()).active)
        assertNull(Session.Idle.active)
    }

    @Test fun `a saved workout comes back running, with started exercises ranked`() {
        val underway = started().send(TapSet(2, 0))
        val restored = Session.restore(underway.state) as Session.Running
        assertEquals(underway.workout, restored.workout)
        assertEquals(listOf("row"), restored.run.ranking)
        assertEquals(underway.state.progress, restored.run.baseline)
        assertEquals(Session.Idle, Session.restore(freshState(WeightUnit.KG)))
    }
}

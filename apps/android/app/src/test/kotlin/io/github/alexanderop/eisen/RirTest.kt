package io.github.alexanderop.eisen

import io.github.alexanderop.eisen.domain.*
import io.github.alexanderop.eisen.domain.elm.Cmd
import io.github.alexanderop.eisen.domain.elm.Msg
import io.github.alexanderop.eisen.domain.elm.RATED_CLOSE_MS
import io.github.alexanderop.eisen.domain.elm.RestScreen
import io.github.alexanderop.eisen.domain.elm.RestState
import io.github.alexanderop.eisen.domain.elm.showsRestScreen
import io.github.alexanderop.eisen.health.workoutNotes
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Reps in reserve: the helpers and the progression vectors of the shared contract with the PWA, then
 * rating through `update` alone. Workout A: hackSquat, bench, row 5×5, dips 3×max.
 */
class RirTest {
    private val kg = WeightUnit.KG

    private fun bench(sets: List<Int?> = List(5) { 5 }, rir: List<Int?> = emptyList()) =
        ExerciseLog("bench", "Bench Press", Equipment.BARBELL, 80.0, 5, sets, rir = rir)

    private fun workout(vararg logs: ExerciseLog) = Workout("w1", "A", "Workout A", kg, 0, 50 * 60000L, logs.toList())

    private val benchProgress: Progress = mapOf("bench" to ExerciseProgress(80.0, 0, 2.5))

    // The helpers

    @Test fun `rirText shows 0 to 3 and 4 or more as 4+`() {
        assertEquals(listOf("0", "1", "2", "3", "4+"), (0..4).map(::rirText))
    }

    @Test fun `withRir fills up with null to the set and drops the list once nothing is rated`() {
        val rated = withRir(bench(), 2, 3)
        assertEquals(listOf(null, null, 3), rated.rir)
        assertEquals(3, rirOf(rated, 2))
        assertNull(rirOf(rated, 0))
        assertNull(rirOf(rated, 4))
        assertEquals(listOf(null, null, 3, null, 1), withRir(rated, 4, 1).rir)
        assertEquals(emptyList<Int?>(), withRir(rated, 2, null).rir)
        assertEquals(emptyList<Int?>(), withRir(bench(), 4, null).rir)
    }

    @Test fun `only a logged set with a target count of reps can be rated`() {
        assertTrue(canRate(bench(), 0))
        assertTrue(canRate(bench(listOf(0, null, null, null, null)), 0))
        assertFalse(canRate(bench(listOf(5, null, null, null, null)), 1))
        assertFalse(canRate(bench(), 5))
        assertFalse(canRate(bench().copy(targetReps = null), 0))
        assertFalse(canRate(bench().copy(timed = true, targetReps = 60), 0))
    }

    @Test fun `a cleared set loses its rating, a corrected count keeps it`() {
        val rated = bench(rir = listOf(2, null, 1))
        assertEquals(listOf(2, null, 1), withReps(rated, 0, 4).rir)
        assertEquals(listOf(4, 5, 5, 5, 5), withReps(rated, 0, 4).sets)
        assertEquals(listOf(null, null, 1), withReps(rated, 0, null).rir)
        assertEquals(emptyList<Int?>(), withReps(withReps(rated, 0, null), 2, null).rir)
    }

    @Test fun `RIR after the reps only when a set is rated`() {
        assertEquals("", rirSuffix(bench()))
        assertEquals(" · RIR 2 2 1 0 4+", rirSuffix(bench(rir = listOf(2, 2, 1, 0, 4))))
        assertEquals(" · RIR – – – – 3", rirSuffix(bench(rir = listOf(null, null, null, null, 3))))
        assertEquals("RIR 2 – – – –", rirLine(bench(rir = listOf(2))))
    }

    // The JSON shared with the PWA

    @Test fun `a log without ratings is saved without the field, a rated one with it`() {
        val plain = StateJson.encodeToJsonElement(ExerciseLog.serializer(), bench()).jsonObject
        assertFalse("rir" in plain)
        val rated = StateJson.encodeToJsonElement(ExerciseLog.serializer(), bench(rir = listOf(null, 2))).jsonObject
        assertEquals("[null,2]", rated["rir"].toString())
        assertEquals(bench(rir = listOf(null, 2)), StateJson.decodeFromJsonElement(ExerciseLog.serializer(), rated))
    }

    @Test fun `an old state without ratings or the setting loads with tracking on`() {
        val state = freshState(kg).copy(history = listOf(workout(bench())))
        val json = StateJson.encodeToJsonElement(AppState.serializer(), state).jsonObject
        val settings = json.getValue("settings").jsonObject
        val old = JsonObject(json + ("settings" to JsonObject(settings - "trackRir")))
        val loaded = decodeState(old.toString())!!
        assertEquals(STATE_VERSION, loaded.version)
        assertTrue(loaded.settings.trackRir)
        assertEquals(state, loaded)
    }

    // Progression: bench press, increment 2.5 kg, weight 80, target 5

    private fun next(log: ExerciseLog, trackRir: Boolean = true): Pair<Double, Change> {
        val result = applyProgression(benchProgress, workout(log), 20.0, trackRir)
        return result.progress.getValue("bench").weight to result.changes.single()
    }

    @Test fun `3 or more in reserve on the last set doubles the jump`() {
        assertEquals(85.0 to true, next(bench(rir = listOf(null, null, null, null, 3))).let { it.first to it.second.boosted })
        assertEquals(85.0 to true, next(bench(rir = listOf(4, 4, 4, 4, 4))).let { it.first to it.second.boosted })
        assertEquals(Change("bench", "Bench Press", ChangeKind.UP, 80.0, 85.0, boosted = true), next(bench(rir = listOf(4, 4, 4, 4, 4))).second)
    }

    @Test fun `less in reserve on the last set, no rating, or tracking off is one increment`() {
        for (rir in listOf(listOf(3, 3, 3, 3, 2), listOf(0, 0, 0, 0, 0), emptyList())) {
            assertEquals("$rir", 82.5 to false, next(bench(rir = rir)).let { it.first to it.second.boosted })
        }
        assertEquals(82.5 to false, next(bench(rir = listOf(4, 4, 4, 4, 4)), trackRir = false).let { it.first to it.second.boosted })
    }

    @Test fun `a miss is a miss however the sets were rated`() {
        val (weight, change) = next(bench(listOf(5, 5, 5, 5, 4), rir = listOf(4, 4, 4, 4, 4)))
        assertEquals(80.0, weight, 0.0)
        assertEquals(ChangeKind.REPEAT, change.kind)
        assertFalse(change.boosted)
    }

    @Test fun `a timed weighted exercise is never boosted`() {
        val carry = ExerciseLog("farmersCarry", "Farmer's Carry", Equipment.WEIGHT, 20.0, 60, listOf(60, 60, 60), timed = true, rir = listOf(4, 4, 4))
        val result = applyProgression(mapOf("farmersCarry" to ExerciseProgress(20.0, 0, 2.0)), workout(carry), 20.0)
        assertEquals(22.0, result.progress.getValue("farmersCarry").weight, 0.0)
        assertFalse(result.changes.single().boosted)
    }

    // What is shown and shared

    @Test fun `the Markdown and the health notes carry the ratings, the share link does not`() {
        val rated = workout(bench(rir = listOf(2, 2, 1, 0, null)))
        assertEquals("- **Bench Press** 80kg: 5×5 · RIR 2 2 1 0 –", workoutMarkdown(rated, "Thu, Nov 19").lines().last())
        assertEquals("Bench Press 80kg: 5×5 · RIR 2 2 1 0 –", workoutNotes(rated))
        assertEquals(encodeShared(toShared(workout(bench()))), encodeShared(toShared(rated)))
        assertEquals("- **Bench Press** 80kg: 5×5", workoutMarkdown(workout(bench()), "Thu, Nov 19").lines().last())
    }

    // Rating through `update`

    /** Set 1 of the bench press logged at [LATER]: a rest of 90 seconds with its screen. */
    private fun resting(state: AppState = freshState(kg)): Step = started(state).send(WorkoutEvent.TapSet(1, 0))

    private val benchRest = RestState(LATER, LATER + 90_000, "All reps done.")

    /** Four sets of the bench press logged, the fifth is the last one. */
    private fun beforeLast(state: AppState = freshState(kg)): Step = (0..3).fold(started(state)) { at, set -> at.send(WorkoutEvent.TapSet(1, set)) }

    private val setDone = RestScreen(1, 4, "bench", setDone = true)

    private fun Step.restCmds(): List<Cmd> =
        cmds.filter { it is Cmd.SaveRest || it is Cmd.ScheduleRestAlarms || it is Cmd.CancelRestAlarms || it is Cmd.ShowRestCountdown || it is Cmd.HideRestCountdown }

    @Test fun `rating a set leaves the rest, its screen and its alarms alone`() {
        val rated = resting().send(WorkoutEvent.RateSet(1, 0, 2), now = LATER + 20_000)
        assertEquals(listOf<Int?>(2), rated.workout.exercises[1].rir)
        assertEquals(benchRest, rated.model.rest)
        assertEquals(RestScreen(1, 0, "bench"), rated.model.restScreen)
        assertTrue(rated.restCmds().isEmpty())
        assertTrue(rated.uiEvents.isEmpty())
        assertTrue(rated.cmds.none { it is Cmd.After })
        assertTrue(Cmd.Save(rated.state) in rated.cmds)

        val changed = rated.send(WorkoutEvent.RateSet(1, 0, 4))
        assertEquals(listOf<Int?>(4), changed.workout.exercises[1].rir)
        val removed = changed.send(WorkoutEvent.RateSet(1, 0, null))
        assertEquals(emptyList<Int?>(), removed.workout.exercises[1].rir)
        assertEquals(benchRest, removed.model.rest)
        assertIgnored(rated, WorkoutEvent.RateSet(1, 0, 2))
    }

    @Test fun `only a logged fixed-reps set takes a rating, and only while ratings are tracked`() {
        val step = resting().send(WorkoutEvent.LogReps(3, 0, 8))
        assertIgnored(step, WorkoutEvent.RateSet(3, 0, 2))
        assertIgnored(step, WorkoutEvent.RateSet(1, 1, 2))
        assertIgnored(step, WorkoutEvent.RateSet(1, 0, 5))
        assertIgnored(step, WorkoutEvent.RateSet(1, 0, -1))
        assertIgnored(step, WorkoutEvent.RateSet(9, 0, 2))
        assertIgnored(step.send(Msg.Settings.SetTrackRir(false)), WorkoutEvent.RateSet(1, 0, 2))
        assertIgnored(stepOf(), WorkoutEvent.RateSet(1, 0, 2))
    }

    @Test fun `a tap on a rated set keeps the rating until it clears the set`() {
        val rated = resting().send(WorkoutEvent.RateSet(1, 0, 2))
        val four = rated.send(WorkoutEvent.TapSet(1, 0))
        assertEquals(4, four.workout.exercises[1].sets[0])
        assertEquals(listOf<Int?>(2), four.workout.exercises[1].rir)
        assertEquals(listOf<Int?>(2), rated.send(WorkoutEvent.CorrectReps(1, 0, 3)).workout.exercises[1].rir)
        val cleared = (0..4).fold(four) { at, _ -> at.send(WorkoutEvent.TapSet(1, 0)) }
        assertNull(cleared.workout.exercises[1].sets[0])
        assertEquals(emptyList<Int?>(), cleared.workout.exercises[1].rir)
    }

    @Test fun `the last set of an exercise opens the set done screen, without a rest`() {
        val last = beforeLast().send(WorkoutEvent.TapSet(1, 4))
        assertNull(last.model.rest)
        assertEquals(setDone, last.model.restScreen)
        assertTrue(showsRestScreen(last.model))
        assertTrue(Cmd.CancelRestAlarms in last.cmds)
    }

    @Test fun `no set done screen without the full-screen rest, without tracking, or for a max-reps set`() {
        val noScreen = freshState(kg).let { it.copy(settings = it.settings.copy(restFullScreen = false)) }
        assertNull(beforeLast(noScreen).send(WorkoutEvent.TapSet(1, 4)).model.restScreen)
        val noRir = freshState(kg).let { it.copy(settings = it.settings.copy(trackRir = false)) }
        assertNull(beforeLast(noRir).send(WorkoutEvent.TapSet(1, 4)).model.restScreen)
        val dips = started().send(WorkoutEvent.LogReps(3, 0, 8)).send(WorkoutEvent.LogReps(3, 1, 8)).send(WorkoutEvent.LogReps(3, 2, 8))
        assertNull(dips.model.rest)
        assertNull(dips.model.restScreen)
    }

    @Test fun `with the full-screen rest off, the screen from the bar still rates`() {
        val noScreen = freshState(kg).let { it.copy(settings = it.settings.copy(restFullScreen = false)) }
        val open = resting(noScreen).send(Msg.Rest.Open)
        assertTrue(showsRestScreen(open.model))
        assertEquals(listOf<Int?>(1), open.send(WorkoutEvent.RateSet(1, 0, 1)).workout.exercises[1].rir)
    }

    @Test fun `a chip on the set done screen closes it a moment later`() {
        val last = beforeLast().send(WorkoutEvent.TapSet(1, 4))
        val rated = last.send(WorkoutEvent.RateSet(1, 4, 4))
        assertEquals(listOf(null, null, null, null, 4), rated.workout.exercises[1].rir)
        assertEquals(setDone, rated.model.restScreen)
        assertTrue(Cmd.After(RATED_CLOSE_MS, Msg.Rest.Rated(setDone, 4)) in rated.cmds)
        val closed = rated.send(Msg.Rest.Rated(setDone, 4))
        assertNull(closed.model.restScreen)
        assertNull(closed.model.rest)
        assertTrue(closed.cmds.isEmpty())

        // Changed meanwhile: the wait of the new rating counts, not the old one.
        val changed = rated.send(WorkoutEvent.RateSet(1, 4, 2))
        assertIgnored(changed, Msg.Rest.Rated(setDone, 4))
        assertNull(changed.send(Msg.Rest.Rated(setDone, 2)).model.restScreen)
        // Taken back: it stays open.
        val removed = rated.send(WorkoutEvent.RateSet(1, 4, null))
        assertTrue(removed.cmds.none { it is Cmd.After })
        assertIgnored(removed, Msg.Rest.Rated(setDone, 4))
    }

    @Test fun `done, a swipe down and back close the set done screen and leave nothing behind`() {
        val last = beforeLast().send(WorkoutEvent.TapSet(1, 4))
        for (close in listOf(Msg.Rest.Minimise, Msg.Nav.Back)) {
            val closed = last.send(close)
            assertNull("$close", closed.model.restScreen)
            assertNull("$close", closed.model.rest)
            assertTrue("$close", closed.model.nav.workoutOpen)
        }
        assertIgnored(last, Msg.Rest.Open)
        assertIgnored(last, Msg.Rest.Dismiss)
    }

    @Test fun `the set done screen goes with its exercise, the workout and the workout screen`() {
        val last = beforeLast().send(WorkoutEvent.TapSet(1, 4))
        assertNull(last.send(WorkoutEvent.RemoveExercise(1)).model.restScreen)
        assertNull(last.send(WorkoutEvent.RemoveExercise(0)).model.restScreen)
        assertEquals(setDone, last.send(WorkoutEvent.RemoveExercise(2)).model.restScreen)
        assertNull(last.send(WorkoutEvent.RequestDiscard).send(WorkoutEvent.Confirm).model.restScreen)
        assertNull(last.send(WorkoutEvent.RequestFinish).send(WorkoutEvent.Confirm).model.restScreen)
        assertNull(last.send(Msg.Nav.CloseWorkout).model.restScreen)
        assertNull(last.send(Msg.Settings.SetTrackRir(false)).model.restScreen)
        // A hold timer opening takes its place.
        val holding = beforeLast().send(WorkoutEvent.AddExercise("plank")).send(WorkoutEvent.TapSet(1, 4)).send(WorkoutEvent.TapSet(4, 0))
        assertTrue(holding.model.hold != null)
        assertNull(holding.model.restScreen)
        // A rest that starts takes its place too.
        assertEquals(RestScreen(0, 0, "hackSquat"), last.send(WorkoutEvent.TapSet(0, 0)).model.restScreen)
    }

    @Test fun `a last set rated 4+ doubles the jump of the next workout`() {
        val state = started().state
        val before = state.progress.getValue("bench")
        val rated = beforeLast().send(WorkoutEvent.TapSet(1, 4)).send(WorkoutEvent.RateSet(1, 4, 4))
        val finished = rated.send(WorkoutEvent.RequestFinish).send(WorkoutEvent.Confirm)
        val change = finished.model.lastChanges!!.first { it.id == "bench" }
        assertTrue(change.boosted)
        assertEquals(before.weight + 2 * before.increment, change.to, 0.0)
        assertEquals(change.to, finished.state.progress.getValue("bench").weight, 0.0)
        assertEquals(listOf(null, null, null, null, 4), finished.state.history.first().exercises[1].rir)
        val next = finished.send(Msg.Routines.Select("A")).send(WorkoutEvent.Start)
        assertEquals(change.to, next.workout.exercises[1].weight, 0.0)
    }
}

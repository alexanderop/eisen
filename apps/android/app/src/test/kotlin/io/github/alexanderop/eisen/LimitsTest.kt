package io.github.alexanderop.eisen

import io.github.alexanderop.eisen.app.elm.Msg
import io.github.alexanderop.eisen.app.elm.WorkoutEvent
import io.github.alexanderop.eisen.app.elm.repsQuestion
import io.github.alexanderop.eisen.app.elm.timerCapSeconds
import io.github.alexanderop.eisen.app.elm.timerWheelSeconds
import io.github.alexanderop.eisen.app.elm.wodAmounts
import io.github.alexanderop.eisen.app.elm.wodLoads
import io.github.alexanderop.eisen.app.state.AppState
import io.github.alexanderop.eisen.app.state.Loaded
import io.github.alexanderop.eisen.app.state.encodeState
import io.github.alexanderop.eisen.app.state.freshState
import io.github.alexanderop.eisen.app.state.loadState
import io.github.alexanderop.eisen.core.Equipment
import io.github.alexanderop.eisen.core.MAX_WEIGHT
import io.github.alexanderop.eisen.core.WeightUnit
import io.github.alexanderop.eisen.feature.timer.MAX_COUNTED_ROUNDS
import io.github.alexanderop.eisen.feature.timer.TimerConfig
import io.github.alexanderop.eisen.feature.timer.TimerMode
import io.github.alexanderop.eisen.feature.timer.boxing.BoxingRound
import io.github.alexanderop.eisen.feature.timer.boxing.BoxingTheme
import io.github.alexanderop.eisen.feature.timer.timerConfig
import io.github.alexanderop.eisen.feature.timer.wod.WodItem
import io.github.alexanderop.eisen.feature.timer.wod.WodUnit
import io.github.alexanderop.eisen.feature.timer.wod.wod
import io.github.alexanderop.eisen.feature.workout.MAX_BAR
import io.github.alexanderop.eisen.feature.workout.MAX_HELD_SECONDS
import io.github.alexanderop.eisen.feature.workout.MAX_HOLD_SECONDS
import io.github.alexanderop.eisen.feature.workout.MAX_INCREMENT
import io.github.alexanderop.eisen.feature.workout.MAX_LOGGED_REPS
import io.github.alexanderop.eisen.feature.workout.MAX_REPS
import io.github.alexanderop.eisen.feature.workout.MAX_REST_SECONDS
import io.github.alexanderop.eisen.feature.workout.MAX_RIR
import io.github.alexanderop.eisen.feature.workout.MAX_SETS
import io.github.alexanderop.eisen.feature.workout.MIN_HOLD_SECONDS
import io.github.alexanderop.eisen.feature.workout.Slot
import io.github.alexanderop.eisen.feature.workout.repsChoices
import io.github.alexanderop.eisen.feature.workout.weightChoices
import io.github.alexanderop.eisen.app.elm.findExercise
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A number without a limit (QA build 19: 9999999 reps typed into the reps question were saved, and the
 * rest screen built a wheel row for every number up to them and ran out of memory). Every Msg that carries
 * a count, a weight, seconds or rounds gets the largest number there is, and what is saved stays within its
 * field's limit. `RulesTest` checks that every number entry on screen names its maximum and every wheel is
 * built from a bounded list; the builders are checked here.
 */
class LimitsTest {
    private val big = Int.MAX_VALUE
    private val heavy = Double.MAX_VALUE

    /** Workout A with a plank (a hold) at the end: Hack Squat, Bench Press, Barbell Row, Dips (max reps), Plank. */
    private val withPlank = freshState(WeightUnit.KG).let { fresh ->
        fresh.copy(routines = fresh.routines.map { if (it.id == "A") it.copy(slots = it.slots + Slot("plank", 3, 30)) else it })
    }

    /** Every number of [state] that is outside its field's limit. */
    private fun outOfLimits(state: AppState): List<String> = buildList {
        val settings = state.settings
        if (settings.bar !in 0.0..MAX_BAR) add("bar ${settings.bar}")
        if (settings.restDone !in 0..MAX_REST_SECONDS) add("restDone ${settings.restDone}")
        if (settings.restMissed !in 0..MAX_REST_SECONDS) add("restMissed ${settings.restMissed}")
        for ((id, entry) in state.progress) {
            if (entry.weight !in 0.0..MAX_WEIGHT) add("$id weight ${entry.weight}")
            if (entry.increment !in 0.0..MAX_INCREMENT) add("$id increment ${entry.increment}")
        }
        for (routine in state.routines) for (slot in routine.slots) {
            val timed = state.findExercise(slot.exerciseId)?.timed == true
            if (slot.sets !in 1..MAX_SETS) add("${routine.id} ${slot.exerciseId} sets ${slot.sets}")
            val reps = slot.reps
            if (reps != null && reps !in (if (timed) MIN_HOLD_SECONDS..MAX_HOLD_SECONDS else 1..MAX_REPS)) add("${routine.id} ${slot.exerciseId} reps $reps")
        }
        for (workout in listOfNotNull(state.active) + state.history) for (log in workout.exercises) {
            if (log.weight !in 0.0..MAX_WEIGHT) add("${workout.id} ${log.id} weight ${log.weight}")
            val most = if (log.timed) MAX_HELD_SECONDS else MAX_LOGGED_REPS
            for (count in log.sets.filterNotNull()) if (count !in 0..most) add("${workout.id} ${log.id} set $count")
            for (rir in log.rir.filterNotNull()) if (rir !in 0..MAX_RIR) add("${workout.id} ${log.id} rir $rir")
        }
        val configs = state.timerConfigs.values + state.timerPresets.map { it.config } + state.timerSessions.map { it.config }
        for (config in configs) if (config != timerConfig(config)) add("timer $config")
        for (session in state.timerSessions) if (session.rounds !in 0..MAX_COUNTED_ROUNDS) add("${session.name} rounds ${session.rounds}")
        for (saved in state.wods) if (saved != wod(saved)) add("wod $saved")
    }

    private fun assertWithin(step: Step, sent: Any) {
        val broken = outOfLimits(step.state)
        assertTrue("after $sent:\n${broken.joinToString("\n")}", broken.isEmpty())
        step.model.repsQuestion?.let { assertTrue("after $sent: the question starts at ${it.reps}", it.reps in 0..MAX_LOGGED_REPS) }
        step.model.timerSetup?.let { assertEquals("after $sent", timerConfig(it.config), it.config) }
    }

    @Test fun `the largest number a Msg carries is saved within its field's limit`() {
        val running = started(withPlank)
        val tapped = running.send(WorkoutEvent.TapSet(0, 0))
        val dips = running.send(WorkoutEvent.LogReps(3, 0, 10))
        val finished = tapped.send(WorkoutEvent.RequestFinish).send(WorkoutEvent.Confirm)
        val history = finished.state.history.first()
        val sends: List<Pair<Step, Msg>> = listOf(
            running to Msg.Workout(WorkoutEvent.LogReps(3, 0, big)),
            running to Msg.WorkoutScreen.PickReps(3, 0, big),
            running to Msg.Workout(WorkoutEvent.LogReps(0, 0, big)),
            running to Msg.WorkoutScreen.PickReps(0, 0, big),
            running to Msg.Workout(WorkoutEvent.LogHold(4, 0, big)),
            running to Msg.Workout(WorkoutEvent.SetWeight(0, heavy)),
            running to Msg.Workout(WorkoutEvent.SetWeight(3, heavy)),
            running to Msg.Workout(WorkoutEvent.SetWorkingWeight("bench", heavy)),
            running to Msg.Workout(WorkoutEvent.SetWorkingWeight("dips", heavy)),
            tapped to Msg.Workout(WorkoutEvent.RateSet(0, 0, big)),
            tapped to Msg.Workout(WorkoutEvent.CorrectReps(0, 0, big)),
            dips to Msg.Workout(WorkoutEvent.CorrectReps(3, 0, big)),
            running to Msg.Settings.SetBar(heavy),
            running to Msg.Settings.SetRestDone(big),
            running to Msg.Settings.SetRestMissed(big),
            running to Msg.Settings.SetIncrement("bench", heavy),
            running to Msg.Routines.UpdateSlot("A", 0, Slot("hackSquat", big, big)),
            running to Msg.Routines.UpdateSlot("A", 4, Slot("plank", big, big)),
            running to Msg.Routines.InsertSlot("B", 0, Slot("bench", big, big)),
            running to Msg.Routines.InsertSlot("B", 0, Slot("plank", big, big)),
            finished to Msg.History.Edit(history.id, history.exercises.map { log -> log.copy(weight = heavy, sets = log.sets.map { big }) }),
        )
        for ((step, msg) in sends) assertWithin(step.send(msg, now = LATER + 5_000), msg)

        // A max-reps set whose last time is above the limit (a file from build 19) asks from the limit, not from it.
        val old = started(withPlank).workout.copy(id = "old", exercises = started(withPlank).workout.exercises.map { if (it.id == "dips") it.copy(sets = listOf(9_999_999, null, null)) else it })
        val withOld = started(withPlank.copy(history = listOf(old)))
        for (sent in listOf(WorkoutEvent.TapSet(3, 0), WorkoutEvent.EnterReps(3, 0))) {
            val after = withOld.send(sent)
            assertTrue("$sent: ${after.workout.exercises[3].sets}", after.workout.exercises[3].sets.filterNotNull().all { it in 0..MAX_LOGGED_REPS })
            after.model.repsQuestion?.let { assertTrue("$sent: the question starts at ${it.reps}", it.reps in 0..MAX_LOGGED_REPS) }
        }

        // The timers: every mode's setup, a saved preset, a boxing round, and a result corrected by the largest number.
        for (mode in TimerMode.entries) {
            val open = stepOf().send(Msg.Timer.Open(mode))
            val edited = open.send(Msg.Timer.Edit(TimerConfig(mode, big, big, big)))
            assertWithin(edited, "Edit $mode")
            assertWithin(edited.send(Msg.Timer.SavePreset("Big")), "SavePreset $mode")
        }
        val boxing = stepOf().send(Msg.Timer.Open(TimerMode.BOXING))
        assertWithin(boxing.send(Msg.Timer.EditRound(0, BoxingRound(BoxingTheme.JAB, big, big))), "EditRound")
        val amrap = stepOf().send(Msg.Timer.Open(TimerMode.AMRAP)).send(Msg.Timer.Start, now = STARTED_AT)
            .send(Msg.Timer.Tap, now = STARTED_AT + 15_000).send(Msg.Timer.SaveAndEnd, now = STARTED_AT + 16_000)
        assertTrue(amrap.state.timerSessions.isNotEmpty())
        assertWithin(amrap.send(Msg.Timer.CorrectRounds(big), now = STARTED_AT + 20_000), "CorrectRounds")

        // A WOD in the builder, then saved.
        val built = stepOf().send(Msg.Wods.New).send(Msg.Wods.AddItem("pushups")).send(Msg.Wods.SetMode(TimerMode.FOR_TIME))
        val bigWod = listOf(
            Msg.Wods.SetTimer(TimerConfig(TimerMode.FOR_TIME, big, big, big)),
            Msg.Wods.SetRounds(big),
            Msg.Wods.SetScheme(listOf(big, big)),
            Msg.Wods.UpdateItem(0, WodItem("pushups", big, WodUnit.REPS, heavy)),
            Msg.Wods.Save,
        ).fold(built) { at, msg -> at.send(msg).also { assertWithin(it, msg) } }
        assertTrue("the WOD was saved", bigWod.state.wods.isNotEmpty())
    }

    /** What an older build may have saved loads, and the wheels that show it stay short. */
    @Test fun `a saved count or weight above its limit still loads and shows without a giant wheel`() {
        val logged = started().send(WorkoutEvent.LogReps(3, 0, 10))
        val damaged = logged.state.copy(active = logged.workout.copy(exercises = logged.workout.exercises.map { if (it.id == "dips") it.copy(sets = listOf(9_999_999, null, null), weight = 1e9) else it }))
        val loaded = loadState(encodeState(damaged))
        assertTrue("$loaded", loaded is Loaded.Ok)
        // The rest screen shows it: its wheels are built from these.
        val dips = (loaded as Loaded.Ok).state.active!!.exercises[3]
        assertEquals(9_999_999, dips.sets[0])
        assertEquals(MAX_LOGGED_REPS + 2, repsChoices(dips.targetReps, 9_999_999).size)
        assertEquals(9_999_999, repsChoices(dips.targetReps, 9_999_999).last())
        assertTrue(weightChoices(Equipment.BODYWEIGHT, WeightUnit.KG, 20.0, 1e9).size <= MAX_WEIGHT.toInt() + 2)
        assertEquals(1e9, weightChoices(Equipment.BODYWEIGHT, WeightUnit.KG, 20.0, 1e9).last(), 0.0)
    }

    /** The builders `RulesTest` lets wheels use: short for any value they show. */
    @Test fun `every wheel builder stays short whatever the value it shows`() {
        for (current in listOf(0, 7, 9_999_999, big)) {
            assertTrue(repsChoices(null, current).size <= MAX_LOGGED_REPS + 2)
            assertTrue(repsChoices(5, current).size <= 7)
            assertTrue(timerWheelSeconds(5, 3600, current).size <= 200)
            assertTrue(timerCapSeconds(current).size <= 63)
            for (unit in WodUnit.entries) assertTrue(wodAmounts(unit, current).size <= 200)
        }
        for (current in listOf(0.0, 1e9, heavy)) {
            for (equipment in Equipment.entries) for (unit in WeightUnit.entries) assertTrue("$equipment $unit $current", weightChoices(equipment, unit, 20.0, current).size <= MAX_WEIGHT.toInt() + 2)
            for (unit in WeightUnit.entries) assertTrue(wodLoads(Equipment.BARBELL, unit, current).size <= 200)
        }
    }
}

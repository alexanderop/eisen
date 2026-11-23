package io.github.alexanderop.eisen

import io.github.alexanderop.eisen.domain.AppState
import io.github.alexanderop.eisen.domain.Env
import io.github.alexanderop.eisen.domain.Feel
import io.github.alexanderop.eisen.domain.Run
import io.github.alexanderop.eisen.domain.Session
import io.github.alexanderop.eisen.domain.WeightUnit
import io.github.alexanderop.eisen.domain.Workout
import io.github.alexanderop.eisen.domain.WorkoutEvent
import io.github.alexanderop.eisen.domain.elm.Cmd
import io.github.alexanderop.eisen.domain.elm.Model
import io.github.alexanderop.eisen.domain.elm.Msg
import io.github.alexanderop.eisen.domain.elm.UiEvent
import io.github.alexanderop.eisen.domain.elm.update
import io.github.alexanderop.eisen.domain.freshState
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue

/*
 * A small driver for `update`, shared by the tests: a Step is the model after a Msg and the Cmds
 * that Msg asked for. Every test goes through `update` alone, like the app does.
 */

const val STARTED_AT = 1_000L

/** One minute into the workout, when the tests tap. */
const val LATER = STARTED_AT + 60_000

data class Step(val model: Model, val cmds: List<Cmd> = emptyList()) {
    val session: Session get() = model.session
    val state: AppState get() = model.state
    val run: Run get() = (session as Session.WithRun).run
    val workout: Workout get() = run.workout

    /** The haptics this step asked the UI for. */
    val feels: List<Feel> get() = uiEvents.filterIsInstance<UiEvent.Haptic>().map { it.feel }
    val uiEvents: List<UiEvent> get() = cmds.filterIsInstance<Cmd.Ui>().map { it.event }

    fun send(msg: Msg, now: Long = LATER, id: String = "w2"): Step =
        update(msg, model, Env(now) { id }).let { (next, cmds) -> Step(next, cmds) }

    fun send(event: WorkoutEvent, now: Long = LATER): Step = send(Msg.Workout(event), now)
}

fun stepOf(state: AppState = freshState(WeightUnit.KG)): Step = Step(Model(state))

/** Workout A started at [STARTED_AT] with the id "w1". */
fun started(state: AppState = freshState(WeightUnit.KG)): Step = stepOf(state).send(Msg.Workout(WorkoutEvent.Start), now = STARTED_AT, id = "w1")

/** Nothing changed and nothing is asked for: the same model comes back. */
fun assertIgnored(before: Step, msg: Msg) {
    val after = before.send(msg)
    assertSame(before.model, after.model)
    assertTrue("expected no Cmds for $msg, got ${after.cmds}", after.cmds.isEmpty())
}

fun assertIgnored(before: Step, event: WorkoutEvent) = assertIgnored(before, Msg.Workout(event))

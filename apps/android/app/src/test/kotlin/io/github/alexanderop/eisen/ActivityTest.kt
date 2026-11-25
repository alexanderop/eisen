package io.github.alexanderop.eisen

import androidx.health.connect.client.records.ExerciseSessionRecord
import io.github.alexanderop.eisen.app.elm.ActivitySheet
import io.github.alexanderop.eisen.app.elm.Cmd
import io.github.alexanderop.eisen.app.elm.Flags
import io.github.alexanderop.eisen.app.elm.HealthJob
import io.github.alexanderop.eisen.app.elm.HealthModel
import io.github.alexanderop.eisen.app.elm.HistoryView
import io.github.alexanderop.eisen.app.elm.Model
import io.github.alexanderop.eisen.app.elm.Msg
import io.github.alexanderop.eisen.app.elm.Notifications
import io.github.alexanderop.eisen.app.elm.RESULT_GUARD_MILLIS
import io.github.alexanderop.eisen.app.elm.RestScreen
import io.github.alexanderop.eisen.app.elm.Tab
import io.github.alexanderop.eisen.app.elm.TimerState
import io.github.alexanderop.eisen.app.elm.TrainingStats
import io.github.alexanderop.eisen.app.elm.WorkoutEvent
import io.github.alexanderop.eisen.app.elm.activitySheet
import io.github.alexanderop.eisen.app.elm.init
import io.github.alexanderop.eisen.app.elm.restScreen
import io.github.alexanderop.eisen.app.elm.timerEndsAt
import io.github.alexanderop.eisen.app.elm.timerRun
import io.github.alexanderop.eisen.app.elm.trainingStats
import io.github.alexanderop.eisen.app.state.AppState
import io.github.alexanderop.eisen.app.state.Loaded
import io.github.alexanderop.eisen.app.state.encodeState
import io.github.alexanderop.eisen.app.state.freshState
import io.github.alexanderop.eisen.app.state.loadState
import io.github.alexanderop.eisen.feature.health.platform.exerciseTypeOf
import io.github.alexanderop.eisen.feature.health.platform.timerSessionFields
import io.github.alexanderop.eisen.feature.timer.ACTIVITIES
import io.github.alexanderop.eisen.feature.timer.Activity
import io.github.alexanderop.eisen.feature.timer.DEFAULT_TIMERS
import io.github.alexanderop.eisen.feature.timer.TimerConfig
import io.github.alexanderop.eisen.feature.timer.TimerMode
import io.github.alexanderop.eisen.feature.timer.TimerSession
import io.github.alexanderop.eisen.feature.timer.activityNote
import io.github.alexanderop.eisen.feature.timer.presetActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneOffset

/**
 * What a finished timer was (jump rope, cross trainer, ...): the chips on the result and in the history's
 * sheet, the guess it is filed with, the file, Health Connect, and the sheet in the overlay machine.
 */
class ActivityTest {
    private val tabata = TimerConfig(TimerMode.TABATA, work = 20, rest = 10, rounds = 3)

    /** A run of [config] started at [at] with the id [id] and left to run out: its result shows, the session is filed. */
    private fun ran(before: Step, id: String, at: Long, config: TimerConfig = tabata): Step {
        val started = before.send(Msg.Timer.Open(config.mode), now = at).send(Msg.Timer.Edit(config), now = at).send(Msg.Timer.Start, now = at, id = id)
        val run = started.model.timerRun ?: error("no run started")
        return started.send(Msg.Timer.Tick, now = timerEndsAt(run) + 5_000)
    }

    private val Step.result: TimerSession get() = (model.timer as TimerState.Finished).session
    private val Step.shownAt: Long get() = (model.timer as TimerState.Finished).at

    private fun session(id: String, name: String = "Tabata", mode: TimerMode = TimerMode.TABATA, activity: Activity? = null, wodId: String? = null) =
        TimerSession(id, name, DEFAULT_TIMERS.getValue(mode), 0, 60_000, 60, 1, true, wodId = wodId, activity = activity)

    // The domain

    @Test fun `the chips are the seven activities in the order asked for, each with its label`() {
        assertEquals(
            listOf("Boxing", "Jump rope", "Battle rope", "Cross trainer", "Rowing", "Bike", "Running"),
            ACTIVITIES.map { it.label },
        )
        assertEquals(Activity.entries.toSet(), ACTIVITIES.toSet())
    }

    @Test fun `a run is filed as boxing in the boxing mode, as the last run of the same timer was, or as nothing`() {
        val earlier = listOf(
            session("3", name = "Cardio", mode = TimerMode.EMOM, activity = Activity.BIKE),
            session("2", activity = Activity.JUMP_ROPE),
            session("1", activity = Activity.ROWING),
        )
        assertEquals(Activity.BOXING, presetActivity(session("new", "Sparring", TimerMode.BOXING), earlier))
        // The newest run with the same name and mode counts, not an older one.
        assertEquals(Activity.JUMP_ROPE, presetActivity(session("new"), earlier))
        // The same name in another mode is another timer.
        assertNull(presetActivity(session("new", name = "Cardio"), earlier))
        assertNull(presetActivity(session("new", name = "Quick Tabata"), earlier))
        assertNull(presetActivity(session("new"), emptyList()))
        // A newest run whose activity was cleared says nothing, though an older one did.
        assertNull(presetActivity(session("new"), listOf(session("2"), session("1", activity = Activity.ROWING))))
        // A WOD is what it was, and earlier WOD runs say nothing about a plain timer.
        assertNull(presetActivity(session("new", wodId = "fran"), earlier))
        assertNull(presetActivity(session("new"), listOf(session("w", activity = Activity.BIKE, wodId = "fran"))))
    }

    @Test fun `the history and the calendar name the activity only where it adds to the name`() {
        assertEquals("Jump rope", activityNote(session("1", activity = Activity.JUMP_ROPE)))
        assertNull(activityNote(session("1", name = "Boxing", mode = TimerMode.BOXING, activity = Activity.BOXING)))
        assertEquals("Boxing", activityNote(session("1", name = "Sparring", mode = TimerMode.BOXING, activity = Activity.BOXING)))
        assertNull(activityNote(session("1")))
    }

    // Through update

    @Test fun `a finished run carries its guess on the result and in the history, which are the same session`() {
        val first = ran(stepOf(), "t1", STARTED_AT)
        assertNull(first.result.activity)
        val set = first.send(Msg.Timer.SetActivity("t1", Activity.JUMP_ROPE), now = first.shownAt + RESULT_GUARD_MILLIS)
        assertEquals(Activity.JUMP_ROPE, set.result.activity)
        assertEquals(set.result, set.state.timerSessions.single())
        assertTrue(Cmd.Save(set.state) in set.cmds)
        // Done works without choosing anything; the next run of the same timer is jump rope until it is changed.
        val done = set.send(Msg.Timer.Done, now = set.shownAt + 2_000)
        val second = ran(done, "t2", STARTED_AT + 600_000)
        assertEquals(Activity.JUMP_ROPE, second.result.activity)
        assertEquals(second.result, second.state.timerSessions.first())
        // Another mode with its own name is not jump rope.
        val emom = ran(second.send(Msg.Timer.Done, now = second.shownAt + 2_000), "t3", STARTED_AT + 1_200_000, TimerConfig(TimerMode.EMOM, 10, 0, 2))
        assertNull(emom.result.activity)
    }

    @Test fun `a boxing run is filed as boxing`() {
        val boxed = ran(stepOf(), "b1", STARTED_AT, DEFAULT_TIMERS.getValue(TimerMode.BOXING).copy(rounds = 1, work = 60))
        assertEquals(Activity.BOXING, boxed.result.activity)
        assertEquals(Activity.BOXING, boxed.state.timerSessions.single().activity)
    }

    @Test fun `a chip sets, changes and clears the activity, from the result and from the history`() {
        val shown = ran(stepOf(), "t1", STARTED_AT)
        val late = shown.shownAt + RESULT_GUARD_MILLIS
        val set = shown.send(Msg.Timer.SetActivity("t1", Activity.CROSS_TRAINER), now = late)
        val changed = set.send(Msg.Timer.SetActivity("t1", Activity.BATTLE_ROPE), now = late)
        assertEquals(Activity.BATTLE_ROPE, changed.state.timerSessions.single().activity)
        val cleared = changed.send(Msg.Timer.SetActivity("t1", null), now = late)
        assertNull(cleared.state.timerSessions.single().activity)
        assertNull(cleared.result.activity)
        // From the history, with the result gone.
        val history = cleared.send(Msg.Timer.Done, now = late).send(Msg.Timer.SetActivity("t1", Activity.RUNNING))
        assertEquals(Activity.RUNNING, history.state.timerSessions.single().activity)
        assertNull(history.model.timer)
        // The same again changes nothing.
        assertIgnored(history, Msg.Timer.SetActivity("t1", Activity.RUNNING))
    }

    @Test fun `an id that is not filed, a WOD, and the thumb of the last tap change nothing`() {
        val shown = ran(stepOf(), "t1", STARTED_AT)
        val late = shown.shownAt + RESULT_GUARD_MILLIS
        val after = shown.send(Msg.Timer.SetActivity("gone", Activity.BIKE), now = late)
        assertEquals(shown.model.copy(now = after.model.now), after.model)
        assertTrue(after.cmds.isEmpty())
        // Within a second of the result showing, a chip is where the big button was: not counted.
        val early = shown.send(Msg.Timer.SetActivity("t1", Activity.BIKE), now = shown.shownAt + 200)
        assertEquals(shown.model, early.model)
        val wod = stepOf(freshState().copy(timerSessions = listOf(session("w1", wodId = "fran"))))
        assertIgnored(wod, Msg.Timer.SetActivity("w1", Activity.BIKE))
        assertIgnored(stepOf(), Msg.Timer.SetActivity("t1", Activity.BIKE))
    }

    @Test fun `a changed activity is written to Health Connect again`() {
        val filed = session("t1")
        val on = Step(Model(freshState().copy(timerSessions = listOf(filed)), health = HealthModel(enabled = true)))
        val set = on.send(Msg.Timer.SetActivity("t1", Activity.ROWING))
        assertTrue(Cmd.Health(HealthJob.Upsert(emptyList(), listOf(filed.copy(activity = Activity.ROWING)))) in set.cmds)
        val cleared = set.send(Msg.Timer.SetActivity("t1", null))
        assertTrue(Cmd.Health(HealthJob.Upsert(emptyList(), listOf(filed))) in cleared.cmds)
    }

    // The file

    @Test fun `the activity is saved by its name, left out when there is none, and read back`() {
        val with = freshState().copy(timerSessions = listOf(session("t1", activity = Activity.CROSS_TRAINER), session("t2")))
        val json = encodeState(with)
        assertTrue(json, "\"activity\":\"crossTrainer\"" in json)
        assertEquals(1, Regex("\"activity\"").findAll(json).count())
        assertEquals(with, (loadState(json) as Loaded.Ok).state)
        for (activity in Activity.entries) {
            val one = freshState().copy(timerSessions = listOf(session("t", activity = activity)))
            assertEquals(one, (loadState(encodeState(one)) as Loaded.Ok).state)
        }
    }

    @Test fun `a file from before the activity loads with none, and one from a newer build with an unknown one loads too`() {
        val old = encodeState(freshState().copy(timerSessions = listOf(session("t1"))))
        assertFalse("activity" in old)
        assertNull((loadState(old) as Loaded.Ok).state.timerSessions.single().activity)
        // A newer build may know more activities: the session loads with none, the rest of the file as it was.
        for (written in listOf("\"pogoStick\"", "null", "3", "{\"x\":1}")) {
            val newer = old.replace("\"completed\":true", "\"completed\":true,\"activity\":$written")
            assertTrue(newer != old)
            val loaded = loadState(newer)
            assertTrue("$written: $loaded", loaded is Loaded.Ok)
            assertEquals(session("t1"), (loaded as Loaded.Ok).state.timerSessions.single())
        }
    }

    // Health Connect

    @Test fun `every activity has a Health Connect type, the ropes stay interval training`() {
        val expected = mapOf(
            Activity.BOXING to ExerciseSessionRecord.EXERCISE_TYPE_BOXING,
            Activity.JUMP_ROPE to ExerciseSessionRecord.EXERCISE_TYPE_HIGH_INTENSITY_INTERVAL_TRAINING,
            Activity.BATTLE_ROPE to ExerciseSessionRecord.EXERCISE_TYPE_HIGH_INTENSITY_INTERVAL_TRAINING,
            Activity.CROSS_TRAINER to ExerciseSessionRecord.EXERCISE_TYPE_ELLIPTICAL,
            Activity.ROWING to ExerciseSessionRecord.EXERCISE_TYPE_ROWING_MACHINE,
            Activity.BIKE to ExerciseSessionRecord.EXERCISE_TYPE_BIKING_STATIONARY,
            Activity.RUNNING to ExerciseSessionRecord.EXERCISE_TYPE_RUNNING,
        )
        // A new activity fails here until it is given its type.
        assertEquals(Activity.entries.toSet(), expected.keys)
        for (activity in Activity.entries) {
            assertEquals("$activity", expected.getValue(activity), exerciseTypeOf(activity))
            val fields = timerSessionFields(session("t1", activity = activity), ZoneOffset.UTC) ?: error("no record")
            assertEquals("$activity", expected.getValue(activity), fields.exerciseType)
            assertTrue(fields.notes, fields.notes.startsWith("${activity.label}. Tabata "))
        }
        // Without one: boxing for the boxing timer, interval training for the rest, as before.
        assertEquals(ExerciseSessionRecord.EXERCISE_TYPE_HIGH_INTENSITY_INTERVAL_TRAINING, timerSessionFields(session("t1"), ZoneOffset.UTC)?.exerciseType)
        assertEquals(ExerciseSessionRecord.EXERCISE_TYPE_BOXING, timerSessionFields(session("b", "Boxing", TimerMode.BOXING), ZoneOffset.UTC)?.exerciseType)
        assertTrue(timerSessionFields(session("t1"), ZoneOffset.UTC)?.notes.orEmpty().startsWith("Tabata "))
    }

    // The sheet in the history

    private fun log(state: AppState = freshState().copy(timerSessions = listOf(session("t1"), session("w1", wodId = "fran")))): Step =
        stepOf(state).send(Msg.Nav.SelectTab(Tab.HISTORY))

    @Test fun `a tap on a finished timer of the log opens its sheet, and a chip in it keeps it open`() {
        val open = log().send(Msg.History.OpenActivity("t1"))
        assertEquals(ActivitySheet("t1"), open.model.activitySheet)
        val set = open.send(Msg.Timer.SetActivity("t1", Activity.JUMP_ROPE))
        assertEquals(ActivitySheet("t1"), set.model.activitySheet)
        assertEquals(Activity.JUMP_ROPE, set.state.timerSessions.first().activity)
        assertIgnored(open, Msg.History.OpenActivity("t1"))
    }

    @Test fun `no sheet for a WOD, a timer that is gone, or anywhere but the log`() {
        assertIgnored(log(), Msg.History.OpenActivity("w1"))
        assertIgnored(log(), Msg.History.OpenActivity("gone"))
        assertIgnored(log().send(Msg.Nav.ShowHistory(HistoryView.PROGRESS)), Msg.History.OpenActivity("t1"))
        assertIgnored(stepOf(log().state), Msg.History.OpenActivity("t1"))
    }

    @Test fun `the sheet closes with its button, Back, another tab, Progress, or its timer gone`() {
        val open = log().send(Msg.History.OpenActivity("t1"))
        assertNull(open.send(Msg.History.CloseActivity).model.overlay)
        val back = open.send(Msg.Nav.Back)
        assertNull(back.model.overlay)
        // Back closes the sheet only: the log still shows.
        assertEquals(Tab.HISTORY, back.model.nav.tab)
        assertNull(open.send(Msg.Nav.SelectTab(Tab.HOME)).model.overlay)
        assertNull(open.send(Msg.Nav.ShowHistory(HistoryView.PROGRESS)).model.overlay)
        assertNull(open.send(Msg.Timer.DeleteSession("t1")).model.overlay)
        assertNull(open.send(Msg.Data.Reset).model.overlay)
        assertIgnored(log(), Msg.History.CloseActivity)
    }

    @Test fun `the sheet over a running rest puts the rest screen away, and the bar is back when it closes`() {
        val resting = stepOf(freshState().copy(timerSessions = listOf(session("t1")))).send(Msg.Workout(WorkoutEvent.Start), now = STARTED_AT, id = "w1")
            .send(WorkoutEvent.TapSet(1, 0)).send(Msg.Nav.CloseWorkout).send(Msg.Nav.SelectTab(Tab.HISTORY))
        assertTrue(resting.model.restScreen is RestScreen)
        val open = resting.send(Msg.History.OpenActivity("t1"))
        assertEquals(ActivitySheet("t1"), open.model.overlay)
        assertEquals(resting.model.rest, open.model.rest)
        assertEquals(RestScreen.Minimised(1, 0, "bench"), open.send(Msg.History.CloseActivity).model.overlay)
        // Opening the workout takes the sheet away and gives the rest its bar.
        assertEquals(RestScreen.Minimised(1, 0, "bench"), open.send(Msg.Nav.OpenWorkout).model.overlay)
    }

    @Test fun `after a restart the sheet is closed`() {
        val open = log().send(Msg.History.OpenActivity("t1"))
        val (restarted, _) = init(Flags(open.state, savedRest = null, healthEnabled = false, notifications = Notifications(granted = true, asked = true), now = LATER))
        assertNull(restarted.overlay)
    }

    // Home

    @Test fun `a finished timer counts on the home screen like a workout`() {
        assertNull(trainingStats(freshState(), LATER))
        val week = 7L * 24 * 60 * 60 * 1000
        val timers = listOf(session("t2").copy(startedAt = LATER - 1_000), session("t1").copy(startedAt = LATER - 2 * week))
        assertEquals(TrainingStats(total = 2, lastWeek = 1, last = LATER - 1_000), trainingStats(freshState().copy(timerSessions = timers), LATER))
        val lifted = started().send(WorkoutEvent.TapSet(0, 0)).send(WorkoutEvent.RequestFinish).send(WorkoutEvent.Confirm)
        assertEquals(1, lifted.state.history.size)
        val both = trainingStats(lifted.state.copy(timerSessions = timers), LATER)
        assertEquals(3, both?.total)
        assertEquals(2, both?.lastWeek)
    }
}

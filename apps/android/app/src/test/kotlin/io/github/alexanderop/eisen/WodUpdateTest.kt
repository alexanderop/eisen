package io.github.alexanderop.eisen

import io.github.alexanderop.eisen.domain.DEFAULT_TIMERS
import io.github.alexanderop.eisen.domain.Equipment
import io.github.alexanderop.eisen.domain.STATE_VERSION
import io.github.alexanderop.eisen.domain.TimerConfig
import io.github.alexanderop.eisen.domain.TimerMode
import io.github.alexanderop.eisen.domain.WOD_LIBRARY
import io.github.alexanderop.eisen.domain.WeightUnit
import io.github.alexanderop.eisen.domain.Wod
import io.github.alexanderop.eisen.domain.WodItem
import io.github.alexanderop.eisen.domain.WodUnit
import io.github.alexanderop.eisen.domain.WorkoutEvent
import io.github.alexanderop.eisen.domain.catalogExercise
import io.github.alexanderop.eisen.domain.decodeState
import io.github.alexanderop.eisen.domain.encodeState
import io.github.alexanderop.eisen.domain.freshState
import io.github.alexanderop.eisen.domain.timerView
import io.github.alexanderop.eisen.domain.activeTime
import io.github.alexanderop.eisen.domain.wod
import io.github.alexanderop.eisen.domain.wodBest
import io.github.alexanderop.eisen.domain.wodName
import io.github.alexanderop.eisen.domain.elm.WOD_EXERCISES
import io.github.alexanderop.eisen.domain.elm.draftEditsOwn
import io.github.alexanderop.eisen.domain.elm.matchRank
import io.github.alexanderop.eisen.domain.elm.maxHint
import io.github.alexanderop.eisen.domain.elm.AddTarget
import io.github.alexanderop.eisen.domain.elm.Cmd
import io.github.alexanderop.eisen.domain.elm.DeletedWod
import io.github.alexanderop.eisen.domain.elm.Flags
import io.github.alexanderop.eisen.domain.elm.Msg
import io.github.alexanderop.eisen.domain.elm.Notifications
import io.github.alexanderop.eisen.domain.elm.RoundCard
import io.github.alexanderop.eisen.domain.elm.WodButton
import io.github.alexanderop.eisen.domain.elm.bannerMotions
import io.github.alexanderop.eisen.domain.elm.typedParts
import io.github.alexanderop.eisen.domain.elm.searchedWods
import io.github.alexanderop.eisen.domain.elm.searching
import io.github.alexanderop.eisen.domain.elm.wodSuggestions
import io.github.alexanderop.eisen.domain.elm.noMatchText
import io.github.alexanderop.eisen.domain.elm.ownWods
import io.github.alexanderop.eisen.domain.elm.runRest
import io.github.alexanderop.eisen.domain.elm.RESULT_GUARD_MILLIS
import io.github.alexanderop.eisen.domain.elm.UiEvent as Ui
import io.github.alexanderop.eisen.domain.Feel
import io.github.alexanderop.eisen.domain.WodRest
import io.github.alexanderop.eisen.domain.elm.TimerSound
import io.github.alexanderop.eisen.domain.SuggestionKind
import io.github.alexanderop.eisen.domain.elm.wodButton
import io.github.alexanderop.eisen.domain.elm.FIRST_RUN
import io.github.alexanderop.eisen.domain.elm.PaceLine
import io.github.alexanderop.eisen.domain.elm.PaceTone
import io.github.alexanderop.eisen.domain.elm.ResultGap
import io.github.alexanderop.eisen.domain.elm.paceLine
import io.github.alexanderop.eisen.domain.elm.resultGap
import io.github.alexanderop.eisen.domain.elm.earlierRuns
import io.github.alexanderop.eisen.domain.TimerSession
import io.github.alexanderop.eisen.domain.TimerRun
import io.github.alexanderop.eisen.domain.StateJson
import io.github.alexanderop.eisen.domain.elm.Tab
import io.github.alexanderop.eisen.domain.elm.TimerPage
import io.github.alexanderop.eisen.domain.elm.TimerState
import io.github.alexanderop.eisen.domain.elm.UiEvent
import io.github.alexanderop.eisen.domain.elm.WodFilter
import io.github.alexanderop.eisen.domain.elm.benchmarks
import io.github.alexanderop.eisen.domain.elm.init
import io.github.alexanderop.eisen.domain.elm.lastWodRun
import io.github.alexanderop.eisen.domain.elm.openWod
import io.github.alexanderop.eisen.domain.elm.parseScheme
import io.github.alexanderop.eisen.domain.elm.roundCard
import io.github.alexanderop.eisen.domain.elm.startItem
import io.github.alexanderop.eisen.domain.elm.timerNotice
import io.github.alexanderop.eisen.domain.elm.timerRun
import io.github.alexanderop.eisen.domain.elm.wasWodRecord
import io.github.alexanderop.eisen.domain.elm.withMax
import io.github.alexanderop.eisen.domain.elm.withUnit
import io.github.alexanderop.eisen.domain.elm.wodAmounts
import io.github.alexanderop.eisen.domain.elm.wodLoads
import io.github.alexanderop.eisen.health.timerSessionFields
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneOffset

/** The WODs through `update`: the list, the detail, the builder, a run with its round card, the result and the saved state. */
class WodUpdateTest {
    private fun lib(id: String): Wod = WOD_LIBRARY.first { it.id == id }

    /** [id] started at [STARTED_AT] as the run "r1". */
    private fun startedWod(id: String, from: Step = stepOf()): Step = from.send(Msg.Wods.Start(id), now = STARTED_AT, id = "r1")

    /** The round card at [now]. */
    private fun Step.card(now: Long): RoundCard? = model.timerRun?.let { roundCard(it, timerView(it.config, activeTime(it, now)), state) }
    private val franIds = listOf("thruster", "pullups")
    private val cindyIds = listOf("pullups", "pushups", "airSquat")
    private val murphIds = listOf("run", "pullups", "pushups", "airSquat", "run")

    /** The line between the clock and the round card at [now], measured against the best run so far. */
    private fun Step.pace(now: Long): PaceLine? = model.timerRun?.let { run ->
        paceLine(run, wodBest(state.timerSessions, run.wod!!.id), timerView(run.config, activeTime(run, now)))
    }

    /** [taps] at these seconds into the WOD of a run started at [startedAt]. */
    private fun Step.tapAt(startedAt: Long, vararg seconds: Double): Step =
        seconds.fold(this) { step, at -> step.send(Msg.Timer.Tap, now = startedAt + 10_000 + (at * 1000).toLong()) }

    /** The open builder with a new draft "d1". */
    private fun building(): Step = stepOf().send(Msg.Wods.New, id = "d1")

    private val Step.draft: Wod get() = model.wods.draft!!.wod

    // The list

    @Test fun `the Timer tab opens on the WODs, and the page is kept in a preference`() {
        assertEquals(TimerPage.WODS, stepOf().model.nav.timerPage)
        val timers = stepOf().send(Msg.Nav.ShowTimerPage(TimerPage.TIMERS))
        assertEquals(TimerPage.TIMERS, timers.model.nav.timerPage)
        assertEquals(listOf(Cmd.SaveTimerPage(TimerPage.TIMERS)), timers.cmds)
        assertIgnored(timers, Msg.Nav.ShowTimerPage(TimerPage.TIMERS))
        // Back from the end of the process, and after the activity was recreated.
        val (model, _) = init(Flags(freshState(), savedRest = null, healthEnabled = false, notifications = Notifications(true, true), timerPage = TimerPage.TIMERS))
        assertEquals(TimerPage.TIMERS, model.nav.timerPage)
        assertEquals(TimerPage.TIMERS, timers.send(Msg.Nav.Restore(Tab.TIMER, false, null)).model.nav.timerPage)
    }

    @Test fun `the chips filter the benchmarks`() {
        fun names(filter: WodFilter) = benchmarks(stepOf().send(Msg.Wods.Filter(filter)).model).map { it.name }
        assertEquals(21, names(WodFilter.ALL).size)
        assertEquals(listOf("Cindy", "Annie", "Angie", "Barbara", "Chelsea", "Mary", "Murph", "Venus", "Aphrodite", "Helios", "Zeus", "Ares", "Gaia"), names(WodFilter.NO_WEIGHTS))
        assertEquals(listOf("Cindy", "Mary"), names(WodFilter.AMRAP))
        assertEquals(18, names(WodFilter.FOR_TIME).size)
        assertIgnored(stepOf(), Msg.Wods.Filter(WodFilter.ALL))
    }

    @Test fun `a card opens the detail, and back goes to the list`() {
        val open = stepOf().send(Msg.Wods.Open("wod-fran"))
        assertEquals("Fran", openWod(open.model)?.name)
        assertEquals(Tab.TIMER, open.model.nav.tab)
        assertNull(open.send(Msg.Nav.Back).model.wods.detail)
        assertNull(open.send(Msg.Wods.Close).model.wods.detail)
        assertIgnored(stepOf(), Msg.Wods.Open("gone"))
    }

    // A run

    @Test fun `the play button starts the WOD under its name and keeps the timer setups as they were`() {
        val step = startedWod("wod-fran")
        val run = step.model.timerRun!!
        assertEquals(lib("wod-fran"), run.wod)
        assertEquals("Fran", run.name)
        assertEquals(lib("wod-fran").timer, run.config)
        assertEquals(DEFAULT_TIMERS, step.state.timerConfigs)
        assertTrue(step.cmds.none { it is Cmd.Save })
        val notice = timerNotice(run, STARTED_AT, step.state)
        assertTrue(Cmd.ShowTimer(notice) in step.cmds)
        // The notification names the one to do now.
        assertEquals(listOf("21 × Thruster 43kg"), notice.lines)
        // One run at a time.
        assertIgnored(step, Msg.Wods.Start("wod-cindy"))
        assertIgnored(stepOf(), Msg.Wods.Start("gone"))
    }

    @Test fun `starting a WOD ends a running rest and closes a hold timer`() {
        val resting = started().send(WorkoutEvent.TapSet(0, 0))
        assertNotNull(resting.model.rest)
        val step = resting.send(Msg.Wods.Start("wod-cindy"))
        assertNull(step.model.rest)
        assertTrue(Cmd.CancelRestAlarms in step.cmds)
        assertNull(step.model.hold)
    }

    @Test fun `Fran moves on an item per tap, round after round, and the tap of the last item stops the clock`() {
        var step = startedWod("wod-fran")
        assertEquals(RoundCard(null, listOf("21 × Thruster 43kg", "21 × Pull-ups"), current = 0, exercises = franIds), step.card(STARTED_AT))
        // Nothing counts in the lead-in.
        assertSame(step.model, step.send(Msg.Timer.Tap, now = STARTED_AT + 5_000).model)
        step = step.send(Msg.Timer.Tap, now = STARTED_AT + 40_000)
        assertEquals(RoundCard(null, listOf("21 × Thruster 43kg", "21 × Pull-ups"), current = 1, exercises = franIds), step.card(STARTED_AT + 40_000))
        assertEquals(listOf("21 × Pull-ups"), step.cmds.filterIsInstance<Cmd.ShowTimer>().single().notice.lines)
        // The tap on the round's last item moves the card on to the next round.
        step = step.send(Msg.Timer.Tap, now = STARTED_AT + 60_000)
        assertEquals(RoundCard(null, listOf("15 × Thruster 43kg", "15 × Pull-ups"), current = 0, exercises = franIds), step.card(STARTED_AT + 60_000))
        val notice = step.cmds.filterIsInstance<Cmd.ShowTimer>().single().notice
        assertEquals("Work · Round 2 of 3", notice.text)
        assertEquals(listOf("15 × Thruster 43kg"), notice.lines)
        for (tap in 3..5) step = step.send(Msg.Timer.Tap, now = STARTED_AT + tap * 30_000L)
        assertEquals(RoundCard(null, listOf("9 × Thruster 43kg", "9 × Pull-ups"), current = 1, exercises = franIds), step.card(STARTED_AT + 150_000))
        // A WOD has no "Finish": it ends with its last tap, or early through "End".
        assertIgnored(step, Msg.Timer.Finish)
        val done = step.send(Msg.Timer.Tap, now = STARTED_AT + 302_000)
        val finished = done.model.timer as TimerState.Finished
        assertEquals("wod-fran", finished.session.wodId)
        assertEquals(292, finished.session.seconds)
        assertEquals(3, finished.session.rounds)
        assertTrue(finished.session.completed)
        assertEquals(lib("wod-fran"), finished.wod)
        assertEquals(6, finished.undo?.taps)
        assertEquals(listOf(30.0, 50.0, 80.0, 110.0, 140.0, 292.0), finished.session.splits)
        assertEquals(finished.session, done.state.timerSessions.single())
        assertTrue(Cmd.HideTimer in done.cmds && Cmd.SaveTimer(null) in done.cmds)
    }

    @Test fun `Venus ticks off every exercise of every round, and Back goes across a round boundary`() {
        val venusIds = listOf("pushups", "situps", "airSquat")
        val lines = listOf("50 × Push-ups", "20 × Sit-ups", "50 × Air Squat")
        var step = startedWod("wod-venus")
        for (tap in 1..3) step = step.send(Msg.Timer.Tap, now = STARTED_AT + 10_000 + tap * 20_000L)
        assertEquals(RoundCard(null, lines, current = 0, exercises = venusIds), step.card(STARTED_AT + 70_000))
        assertEquals(listOf(20.0, 40.0, 60.0), step.model.timerRun!!.splits)
        val back = step.send(Msg.Timer.Untap, now = STARTED_AT + 75_000)
        assertEquals(RoundCard(null, lines, current = 2, exercises = venusIds), back.card(STARTED_AT + 75_000))
        assertEquals(listOf(20.0, 40.0), back.model.timerRun!!.splits)
        assertTrue(Cmd.SaveTimer(back.model.timerRun) in back.cmds)
        assertEquals("Work · Round 1 of 4", back.cmds.filterIsInstance<Cmd.ShowTimer>().single().notice.text)
        // Tapped again, the item is filed under the new time.
        step = back.send(Msg.Timer.Tap, now = STARTED_AT + 80_000)
        assertEquals(listOf(20.0, 40.0, 70.0), step.model.timerRun!!.splits)
        // The big button says what the tap ticks off and what comes after it.
        assertEquals(
            WodButton("50 × Push-ups", "tap when done · then 20 × Sit-ups", "50 × Push-ups done. Next: 20 × Sit-ups."),
            wodButton(lib("wod-venus"), 3, step.state),
        )
        assertEquals(WodButton("50 × Air Squat", "tap when done · then round 2", "50 × Air Squat done. Next: round 2."), wodButton(lib("wod-venus"), 2, step.state))
        for (tap in 4..11) step = step.send(Msg.Timer.Tap, now = STARTED_AT + 80_000 + tap * 10_000L)
        assertEquals(RoundCard(null, lines, current = 2, exercises = venusIds), step.card(STARTED_AT + 200_000))
        assertEquals(
            WodButton("50 × Air Squat", "tap when done · finishes the WOD", "50 × Air Squat done. That finishes the WOD."),
            wodButton(lib("wod-venus"), 11, step.state),
        )
        val finished = step.send(Msg.Timer.Tap, now = STARTED_AT + 300_000).model.timer as TimerState.Finished
        assertEquals(Triple(true, 4, 290), Triple(finished.session.completed, finished.session.rounds, finished.session.seconds))
        assertEquals(12, finished.session.splits?.size)
    }

    @Test fun `Undo on the result takes the last tap back and the run goes on from the real clock`() {
        var step = startedWod("wod-fran")
        for (tap in 1..6) step = step.send(Msg.Timer.Tap, now = STARTED_AT + tap * 30_000L)
        assertEquals(1, step.state.timerSessions.size)
        val back = step.send(Msg.Timer.UndoFinish, now = STARTED_AT + 200_000)
        val run = back.model.timerRun!!
        assertEquals(5, run.taps)
        // The time of the tap that was taken back goes with it.
        assertEquals(listOf(20.0, 50.0, 80.0, 110.0, 140.0), run.splits)
        assertEquals(STARTED_AT, run.startedAt)
        assertTrue(back.state.timerSessions.isEmpty())
        assertTrue(Cmd.SaveTimer(run) in back.cmds)
        assertTrue(back.cmds.any { it is Cmd.ScheduleTimer } && back.cmds.any { it is Cmd.ShowTimer })
        // The clock never stopped: the time that passed counts.
        assertEquals(RoundCard(null, listOf("9 × Thruster 43kg", "9 × Pull-ups"), current = 1, exercises = franIds), back.card(STARTED_AT + 200_000))
        val finished = back.send(Msg.Timer.Tap, now = STARTED_AT + 250_000).model.timer as TimerState.Finished
        assertEquals(240, finished.session.seconds)
        assertEquals(listOf(20.0, 50.0, 80.0, 110.0, 140.0, 240.0), finished.session.splits)
        // Only a run its last tap ended can be undone.
        assertIgnored(back, Msg.Timer.UndoFinish)
        val ranOut = startedWod("wod-cindy").send(Msg.Timer.Tick, now = STARTED_AT + 1_300_000)
        assertNull((ranOut.model.timer as TimerState.Finished).undo)
        assertIgnored(ranOut, Msg.Timer.UndoFinish)
    }

    @Test fun `a second run of a For Time sees live how far it is from the best, and the result says by how much`() {
        val first = startedWod("wod-fran")
        assertEquals(PaceLine(FIRST_RUN), first.pace(STARTED_AT + 30_000))
        val filed = first.tapAt(STARTED_AT, 40.0, 75.0, 120.0, 150.0, 190.0, 212.5)
        assertEquals(listOf(40.0, 75.0, 120.0, 150.0, 190.0, 212.5), filed.state.timerSessions.single().splits)
        // A first run has nothing to measure against.
        assertNull(resultGap((filed.model.timer as TimerState.Finished).session, null))

        val again = STARTED_AT + 1_000_000
        val second = filed.send(Msg.Timer.Done).send(Msg.Wods.Start("wod-fran"), now = again, id = "r2")
        // In the lead-in only the best.
        assertEquals(PaceLine("Best 3:33"), second.pace(again + 5_000))
        assertEquals(PaceLine("Best 3:33", "On pace", PaceTone.LEVEL), second.pace(again + 30_000))
        // The first item takes longer than in the best run: the gap grows with the clock.
        assertEquals(PaceLine("Best 3:33", "7s behind", PaceTone.BEHIND), second.pace(again + 57_000))
        val early = second.tapAt(again, 35.0)
        assertEquals(PaceLine("Best 3:33", "5s ahead", PaceTone.AHEAD), early.pace(again + 60_000))
        assertEquals(PaceLine("Best 3:33", "8s behind", PaceTone.BEHIND), early.pace(again + 93_000))
        // Paused, it stands still.
        val paused = early.send(Msg.Timer.Pause, now = again + 60_000)
        assertEquals(PaceLine("Best 3:33", "5s ahead", PaceTone.AHEAD), paused.pace(again + 400_000))

        val done = early.tapAt(again, 70.0, 110.0, 140.0, 180.0, 200.0)
        val session = (done.model.timer as TimerState.Finished).session
        val best = wodBest(earlierRuns(done.state.timerSessions, session), "wod-fran")
        assertEquals(ResultGap("13s faster than your best", better = true), resultGap(session, best))
        assertEquals(ResultGap("13s slower than your best", better = false), resultGap(best!!.copy(seconds = 226), best.copy(seconds = 213)))
        assertEquals(ResultGap("Same time as your best", better = false), resultGap(best, best))
        // A run that was not finished is no time.
        assertNull(resultGap(session.copy(completed = false), best))
        // A best from before taps had times: only the best.
        val old = filed.state.timerSessions.map { it.copy(splits = null) }
        val withoutSplits = Step(second.model.copy(state = second.state.copy(timerSessions = old)))
        assertEquals(PaceLine("Best 3:33"), withoutSplits.pace(again + 57_000))
    }

    @Test fun `a second AMRAP counts against the rounds the best had by now, and EMOM has no line`() {
        val first = startedWod("wod-cindy").tapAt(STARTED_AT, 60.0, 125.0, 200.0).send(Msg.Timer.Tick, now = STARTED_AT + 1_300_000)
        val best = (first.model.timer as TimerState.Finished).session
        assertEquals(listOf(60.0, 125.0, 200.0), best.splits)
        val again = STARTED_AT + 2_000_000
        val second = first.send(Msg.Timer.Done).send(Msg.Wods.Start("wod-cindy"), now = again, id = "r2")
        assertEquals(PaceLine("Best 3 rounds", "On pace", PaceTone.LEVEL), second.pace(again + 40_000))
        assertEquals(PaceLine("Best 3 rounds", "1 round behind", PaceTone.BEHIND), second.pace(again + 71_000))
        val ahead = second.tapAt(again, 50.0, 100.0)
        assertEquals(PaceLine("Best 3 rounds", "1 round ahead", PaceTone.AHEAD), ahead.pace(again + 115_000))
        val ended = ahead.tapAt(again, 150.0, 190.0).send(Msg.Timer.Tick, now = again + 1_300_000)
        val session = (ended.model.timer as TimerState.Finished).session
        assertEquals(ResultGap("1 round more than your best", better = true), resultGap(session, best))
        assertEquals(ResultGap("2 rounds fewer than your best", better = false), resultGap(best.copy(rounds = 1), best))
        assertEquals(ResultGap("As many rounds as your best", better = false), resultGap(best, best))

        assertNull(startedWod("wod-chelsea").pace(STARTED_AT + 30_000))
        assertNull(resultGap(TimerSession("e", "Chelsea", lib("wod-chelsea").timer, 0, 1, 1800, 30, true, "wod-chelsea"), best.copy(config = lib("wod-chelsea").timer)))
    }

    @Test fun `a WOD run kept in its file comes back with the times of its taps`() {
        val venusIds = listOf("pushups", "situps", "airSquat")
        val running = startedWod("wod-venus").tapAt(STARTED_AT, 20.0, 40.0)
        val kept = running.cmds.filterIsInstance<Cmd.SaveTimer>().single().run!!
        assertEquals(listOf(20.0, 40.0), kept.splits)
        val read = StateJson.decodeFromString(TimerRun.serializer(), StateJson.encodeToString(TimerRun.serializer(), kept))
        assertEquals(kept, read)
        val flags = Flags(freshState(), savedRest = null, healthEnabled = false, notifications = Notifications(true, true), savedTimer = read, now = STARTED_AT + 80_000)
        val restored = Step(init(flags).first)
        assertEquals(
            RoundCard(null, listOf("50 × Push-ups", "20 × Sit-ups", "50 × Air Squat"), current = 2, exercises = venusIds),
            restored.card(STARTED_AT + 80_000),
        )
        assertEquals(listOf(20.0, 40.0, 80.0), restored.send(Msg.Timer.Tap, now = STARTED_AT + 90_000).model.timerRun!!.splits)
        // An AMRAP that ran out meanwhile is filed with them.
        val cindy = startedWod("wod-cindy").tapAt(STARTED_AT, 60.0).model.timerRun!!
        val (over, _) = init(flags.copy(savedTimer = cindy, now = STARTED_AT + 1_300_000))
        assertEquals(listOf(60.0), (over.timer as TimerState.Finished).session.splits)
    }

    @Test fun `a rest runs by itself after the item before it, plays its end, and can be skipped without a tap`() {
        val zeusIds = listOf("handstandPushup", "pullups", "pushups", "situps", "airSquat", "rest")
        // Five taps, a second apart from 10 s into the WOD: the fifth starts the two minutes.
        var step = startedWod("wod-zeus")
        for (tap in 1..4) step = step.send(Msg.Timer.Tap, now = STARTED_AT + 10_000 + tap * 2_000L)
        assertEquals(WodButton("50 × Air Squat", "tap when done · then rest 2:00", "50 × Air Squat done. Next: rest 2:00."), wodButton(lib("wod-zeus"), 4, step.state))
        step = step.send(Msg.Timer.Tap, now = STARTED_AT + 20_000)
        val run = step.model.timerRun!!
        assertEquals(listOf(2.0, 4.0, 6.0, 8.0, 10.0), run.splits)
        // The tap that ends a round is felt more.
        assertEquals(listOf(Feel.SUCCESS), step.feels)
        assertEquals(WodRest(120, 120), runRest(run, STARTED_AT + 20_000))
        assertEquals(WodRest(60, 120), runRest(run, STARTED_AT + 80_000))
        // What comes after it, nothing ticked; the notification says so.
        val lines = listOf("10 × Handstand Push-up", "20 × Pull-ups", "30 × Push-ups", "40 × Sit-ups", "50 × Air Squat", "2:00 Rest")
        assertEquals(RoundCard("Up next · Round 2 of 4", lines, upNext = true, exercises = zeusIds), step.card(STARTED_AT + 30_000))
        assertEquals("Rest · Up next · Round 2 of 4", timerNotice(run, STARTED_AT + 30_000, step.state).text)
        // Its end sounds like the start of work, on the run's clock.
        val end = STARTED_AT + 10_000 + 130_000
        val sounds = step.cmds.filterIsInstance<Cmd.ScheduleTimer>().single().sounds
        assertTrue(sounds.containsAll(listOf(TimerSound(end - 3_000, "beep"), TimerSound(end, "beep-long"), TimerSound(end + 700, "go"))))
        // The rest takes no tap: the clock counts it.
        assertSame(step.model, step.send(Msg.Timer.Tap, now = STARTED_AT + 40_000).model)

        val skipped = step.send(Msg.Timer.SkipRest, now = STARTED_AT + 40_000)
        assertEquals(run.splits, skipped.model.timerRun!!.splits)
        assertEquals(5, skipped.model.timerRun!!.restSkipped)
        assertNull(runRest(skipped.model.timerRun!!, STARTED_AT + 40_000))
        assertTrue(skipped.cmds.filterIsInstance<Cmd.ScheduleTimer>().single().sounds.none { it.at == end + 700 })
        assertEquals(RoundCard(null, lines, current = 0, exercises = zeusIds), skipped.card(STARTED_AT + 40_000))
        assertIgnored(skipped, Msg.Timer.SkipRest)
        // Back takes the tap back and forgets the skip; the tap again brings the rest again.
        val back = skipped.send(Msg.Timer.Untap, now = STARTED_AT + 41_000)
        assertNull(back.model.timerRun!!.restSkipped)
        assertNotNull(runRest(back.send(Msg.Timer.Tap, now = STARTED_AT + 42_000).model.timerRun!!, STARTED_AT + 42_000))
        // Left alone it ends by itself, and the next round is to do.
        assertNull(runRest(run, end + 1))
        assertEquals(RoundCard(null, lines, current = 0, exercises = zeusIds), step.card(end + 1))
    }

    @Test fun `the result ignores taps for a second, so a double tap on the last item does not undo the finish`() {
        val done = startedWod("wod-grace").send(Msg.Timer.Tap, now = STARTED_AT + 60_000)
        assertTrue(done.model.timer is TimerState.Finished)
        // The second tap of a double tap, right on the first.
        assertIgnored(done, Msg.Timer.UndoFinish)
        for (msg in listOf(Msg.Timer.UndoFinish, Msg.Timer.Done, Msg.Timer.Again, Msg.Timer.DontCount, Msg.Timer.CorrectRounds(1))) {
            val early = done.send(msg, now = STARTED_AT + 60_000 + RESULT_GUARD_MILLIS - 1)
            assertSame(msg.toString(), done.model, early.model)
        }
        val undone = done.send(Msg.Timer.UndoFinish, now = STARTED_AT + 60_000 + RESULT_GUARD_MILLIS)
        assertTrue(undone.model.timer is TimerState.Running)
    }

    @Test fun `a run that is not a real result leaves the history with Undo and goes back to its WOD`() {
        val done = startedWod("wod-grace").send(Msg.Timer.Tap, now = STARTED_AT + 60_000)
        val session = (done.model.timer as TimerState.Finished).session
        val gone = done.send(Msg.Timer.DontCount, now = STARTED_AT + 70_000)
        assertNull(gone.model.timer)
        assertTrue(gone.state.timerSessions.isEmpty())
        assertEquals("wod-grace", gone.model.wods.detail)
        val snackbar = gone.uiEvents.filterIsInstance<Ui.Snackbar>().single()
        assertEquals("Run not counted" to Msg.Timer.UndoDeleteSession, snackbar.text to snackbar.onAction)
        assertEquals(listOf(session), gone.send(Msg.Timer.UndoDeleteSession).state.timerSessions)
        assertIgnored(gone, Msg.Timer.DontCount)
    }

    @Test fun `ending a For Time early is saved as stopped and is never a best`() {
        val step = startedWod("wod-fran").send(Msg.Timer.Tap, now = STARTED_AT + 40_000).send(Msg.Timer.Tap, now = STARTED_AT + 60_000)
            .send(Msg.Timer.SaveAndEnd, now = STARTED_AT + 100_000)
        val session = (step.model.timer as TimerState.Finished).session
        assertFalse(session.completed)
        assertEquals(1, session.rounds)
        assertNull(wodBest(step.state.timerSessions, "wod-fran"))
    }

    @Test fun `Murph ticks its items off one by one, and the tap on the last ends it`() {
        var step = startedWod("wod-murph")
        val lines = listOf("1.6 km Run", "100 × Pull-ups", "200 × Push-ups", "300 × Air Squat", "1.6 km Run")
        assertEquals(RoundCard(null, lines, current = 0, exercises = murphIds), step.card(STARTED_AT + 20_000))
        for (tap in 1..2) step = step.send(Msg.Timer.Tap, now = STARTED_AT + tap * 60_000L)
        assertEquals(RoundCard(null, lines, current = 2, exercises = murphIds), step.card(STARTED_AT + 120_000))
        // The notification names the one to do now.
        assertEquals(listOf("200 × Push-ups"), timerNotice(step.model.timerRun!!, STARTED_AT + 120_000, step.state).lines)
        step = step.send(Msg.Timer.Untap, now = STARTED_AT + 130_000)
        assertEquals(1, step.card(STARTED_AT + 130_000)?.current)
        for (tap in 1..3) step = step.send(Msg.Timer.Tap, now = STARTED_AT + 200_000 + tap * 60_000L)
        assertNotNull(step.model.timerRun)
        val done = step.send(Msg.Timer.Tap, now = STARTED_AT + 500_000).model.timer as TimerState.Finished
        assertEquals(true to 1, done.session.completed to done.session.rounds)
    }

    @Test fun `Cindy counts rounds on its button only, Chelsea goes by the clock`() {
        var cindy = startedWod("wod-cindy")
        for (tap in 1..7) cindy = cindy.send(Msg.Timer.Tap, now = STARTED_AT + tap * 60_000L)
        assertEquals(RoundCard(null, listOf("5 × Pull-ups", "10 × Push-ups", "15 × Air Squat"), exercises = cindyIds), cindy.card(STARTED_AT + 420_000))
        assertEquals(7, cindy.model.timerRun!!.taps)

        val chelsea = startedWod("wod-chelsea")
        // The twelfth minute after the lead-in.
        assertEquals(RoundCard("Round 12 of 30", listOf("5 × Pull-ups", "10 × Push-ups", "15 × Air Squat"), exercises = cindyIds), chelsea.card(STARTED_AT + 10_000 + 11 * 60_000 + 5_000))
        assertIgnored(chelsea.send(Msg.Timer.Tick, now = STARTED_AT + 20_000), Msg.Timer.Tap)
    }

    @Test fun `a Tabata takes its items in turns and shows the coming round during the rest`() {
        val turns = wod(
            Wod(
                "own-tabata", "Turns", TimerConfig(TimerMode.TABATA, work = 20, rest = 10, rounds = 4), 1, emptyList(),
                listOf(WodItem("burpee", 0, WodUnit.REPS, 0.0), WodItem("plank", 20, WodUnit.SECONDS, 0.0)),
            ),
        )
        val step = startedWod("own-tabata", stepOf(freshState().copy(wods = listOf(turns))))
        assertEquals(RoundCard("Round 1 of 4", listOf("Max Burpee"), exercises = listOf("burpee")), step.card(STARTED_AT + 15_000))
        assertEquals(RoundCard("Up next · Round 2 of 4", listOf("20s Plank"), upNext = true, exercises = listOf("plank")), step.card(STARTED_AT + 35_000))
        assertEquals(RoundCard("Round 2 of 4", listOf("20s Plank"), exercises = listOf("plank")), step.card(STARTED_AT + 45_000))
        assertEquals(RoundCard("Round 3 of 4", listOf("Max Burpee"), exercises = listOf("burpee")), step.card(STARTED_AT + 75_000))
        assertEquals("Rest · Up next · Round 2 of 4", timerNotice(step.model.timerRun!!, STARTED_AT + 35_000, step.state).text)
    }

    @Test fun `an exercise that no longer exists leaves the round card with its picture`() {
        val gone = wod(
            Wod(
                "own-gone", "Gone", TimerConfig(TimerMode.AMRAP, work = 600, rest = 0, rounds = 1), 1, emptyList(),
                listOf(WodItem("custom-gone", 10, WodUnit.REPS, 0.0), WodItem("burpee", 10, WodUnit.REPS, 0.0)),
            ),
        )
        val step = startedWod("own-gone", stepOf(freshState().copy(wods = listOf(gone))))
        assertEquals(RoundCard(null, listOf("10 × Burpee"), exercises = listOf("burpee")), step.card(STARTED_AT + 15_000))
    }

    @Test fun `Grace is one movement and one tap ends it`() {
        val grace = startedWod("wod-grace")
        assertEquals(RoundCard(null, listOf("30 × Clean and Jerk 61kg"), current = 0, exercises = listOf("cleanAndJerk")), grace.card(STARTED_AT + 20_000))
        assertEquals("Work", timerNotice(grace.model.timerRun!!, STARTED_AT + 20_000, grace.state).text)
        val done = grace.send(Msg.Timer.Tap, now = STARTED_AT + 210_000).model.timer as TimerState.Finished
        assertEquals(Triple(true, 1, 200), Triple(done.session.completed, done.session.rounds, done.session.seconds))
    }

    @Test fun `the second faster run is a record, and Again starts the WOD once more`() {
        val first = startedWod("wod-grace").send(Msg.Timer.Tap, now = STARTED_AT + 310_000)
        val firstSession = (first.model.timer as TimerState.Finished).session
        assertFalse(wasWodRecord(first.state.timerSessions, firstSession))

        val again = first.send(Msg.Timer.Again, now = STARTED_AT + 400_000, id = "r2")
        assertEquals(lib("wod-grace"), again.model.timerRun!!.wod)
        val second = again.send(Msg.Timer.Tap, now = STARTED_AT + 400_000 + 250_000)
        val session = (second.model.timer as TimerState.Finished).session
        assertTrue(wasWodRecord(second.state.timerSessions, session))
        // In the history the first run stays without a record, and the newest run is the last one.
        assertFalse(wasWodRecord(second.state.timerSessions, firstSession))
        assertEquals("r2", lastWodRun(second.state.timerSessions, "wod-grace")?.id)
        // Health Connect gets it like any timer, named after the WOD.
        assertEquals("Grace", timerSessionFields(session, ZoneOffset.UTC)!!.title)
    }

    @Test fun `a run of an unnamed WOD goes by its format`() {
        val unnamed = wod(lib("wod-grace").copy(id = "mine", name = ""))
        val run = startedWod("mine", stepOf(freshState().copy(wods = listOf(unnamed)))).model.timerRun!!
        assertEquals("For Time", run.name)
    }

    @Test fun `correcting the rounds of an AMRAP result keeps its WOD and decides the record anew`() {
        val first = startedWod("wod-cindy")
        var step = first
        for (tap in 1..3) step = step.send(Msg.Timer.Tap, now = STARTED_AT + tap * 60_000L)
        step = step.send(Msg.Timer.Tick, now = STARTED_AT + 1_300_000)
        step = step.send(Msg.Timer.Done).send(Msg.Wods.Start("wod-cindy"), now = STARTED_AT + 2_000_000, id = "r2")
        for (tap in 1..3) step = step.send(Msg.Timer.Tap, now = STARTED_AT + 2_000_000 + tap * 60_000L)
        step = step.send(Msg.Timer.Tick, now = STARTED_AT + 3_300_000)
        val finished = step.model.timer as TimerState.Finished
        assertFalse(wasWodRecord(step.state.timerSessions, finished.session))
        val corrected = step.send(Msg.Timer.CorrectRounds(1))
        val after = corrected.model.timer as TimerState.Finished
        assertEquals(lib("wod-cindy"), after.wod)
        assertTrue(wasWodRecord(corrected.state.timerSessions, after.session))
    }

    // The banner and the search

    @Test fun `the banner shows each movement with a strip once, in order, at most four`() {
        assertEquals(listOf("thruster", "pull-up"), bannerMotions(lib("wod-fran")))
        assertEquals(listOf("push-up", "sit-up", "air-squat"), bannerMotions(lib("wod-venus")))
        // Murph runs twice: once in the banner.
        assertEquals(listOf("run", "pull-up", "push-up", "air-squat"), bannerMotions(lib("wod-murph")))
        // Zeus has five movements and a rest: the first four.
        assertEquals(listOf("handstand-push-up", "pull-up", "push-up", "sit-up"), bannerMotions(lib("wod-zeus")))
        // Two sprints share the run strip; the rest has none.
        assertEquals(listOf("pull-up", "sit-up", "run"), bannerMotions(lib("wod-ares")))
        val plain = lib("wod-grace").copy(items = listOf(WodItem("legExtension", 10, WodUnit.REPS, 0.0)))
        assertEquals(emptyList<String>(), bannerMotions(plain))
        assertEquals(emptyList<String>(), bannerMotions(plain.copy(items = emptyList())))
    }

    @Test fun `a suggestion is cut around what was typed`() {
        assertEquals(Triple("", "Pu", "sh-ups"), typedParts("Push-ups", "pu"))
        assertEquals(Triple("Handstand ", "Pu", "sh-up"), typedParts("Handstand Push-up", "pu"))
        assertEquals(Triple("Pistol S", "qua", "t"), typedParts("Pistol Squat", "qua"))
        assertEquals(Triple("", "Fr", "an"), typedParts("Fran", " FR "))
        assertEquals(Triple("Fran", "", ""), typedParts("Fran", "xyz"))
        assertEquals(Triple("Fran", "", ""), typedParts("Fran", ""))
    }

    @Test fun `typing searches the WODs at once and opens the suggestions, the search key closes them and keeps the search`() {
        val own = wod(lib("wod-fran").copy(id = "mine", name = "Pull day"))
        val page = stepOf(freshState().copy(wods = listOf(own)))
        assertFalse(searching(page.model))
        val typed = page.send(Msg.Wods.Search("pu"))
        assertTrue(typed.model.wods.suggesting)
        assertTrue(typed.cmds.isEmpty())
        // The lifter's own and the benchmarks together, best first.
        assertEquals(listOf("Pull day", "Fran", "Cindy"), searchedWods(typed.send(Msg.Wods.Search("pull")).model).map { it.name }.take(3))
        assertEquals(
            listOf("Pull day", "Pull-ups", "Push-ups", "Push Jerk", "Handstand Push-up"),
            wodSuggestions(typed.model).map { it.text },
        )
        val closed = typed.send(Msg.Wods.CloseSuggestions)
        assertFalse(closed.model.wods.suggesting)
        assertEquals("pu", closed.model.wods.query)
        assertTrue(wodSuggestions(closed.model).isEmpty())
        assertIgnored(closed, Msg.Wods.CloseSuggestions)
        // Focus back on the field with text: they open again.
        assertTrue(closed.send(Msg.Wods.Search("pu")).model.wods.suggesting)
        // Spaces alone are no search and open nothing.
        val blank = page.send(Msg.Wods.Search("  "))
        assertFalse(blank.model.wods.suggesting || searching(blank.model))
        assertEquals(60, page.send(Msg.Wods.Search("x".repeat(90))).model.wods.query.length)
    }

    @Test fun `a movement suggestion becomes the search, a WOD suggestion opens it, and the search outlives the detail`() {
        val typed = stepOf().send(Msg.Wods.Search("ket"))
        val movement = wodSuggestions(typed.model).single { it.kind == SuggestionKind.MOVEMENT }
        val picked = typed.send(Msg.Wods.SearchMovement(movement.text))
        assertEquals("Kettlebell Swing", picked.model.wods.query)
        assertFalse(picked.model.wods.suggesting)
        assertEquals(listOf("Helen"), searchedWods(picked.model).map { it.name })

        // A card opened from the results keeps the search for the way back.
        val fran = stepOf().send(Msg.Wods.Search("fr"))
        val card = fran.send(Msg.Wods.CloseSuggestions).send(Msg.Wods.Open("wod-fran"))
        assertEquals("fr", card.send(Msg.Wods.Close).model.wods.query)
        // A WOD picked among the suggestions is what was searched for: the search is done.
        val opened = fran.send(Msg.Wods.Open(wodSuggestions(fran.model).single().id, suggested = true))
        assertEquals("wod-fran", opened.model.wods.detail)
        assertFalse(opened.model.wods.suggesting)
        assertEquals("", opened.send(Msg.Wods.Close).model.wods.query)
    }

    @Test fun `the suggestions go through the chip like the results, and nothing found offers Show all`() {
        val bodyweight = stepOf().send(Msg.Wods.Filter(WodFilter.NO_WEIGHTS)).send(Msg.Wods.Search("fra"))
        // Fran needs a barbell: neither a result nor a suggestion under "No weights".
        assertTrue(searchedWods(bodyweight.model).isEmpty())
        assertTrue(wodSuggestions(bodyweight.model).none { it.text == "Fran" })
        assertEquals("No WODs match \u201cfra\u201d in No weights", noMatchText(bodyweight.model))
        // "Show all" lifts the chip and keeps the text.
        val all = bodyweight.send(Msg.Wods.Filter(WodFilter.ALL))
        assertEquals("fra", all.model.wods.query)
        assertEquals(listOf("Fran"), searchedWods(all.model).map { it.name })
        assertEquals("No WODs match \u201czzz\u201d", noMatchText(all.send(Msg.Wods.Search("zzz")).model))
        // The chips sit over the whole page: the lifter's own WODs go through them too.
        val own = wod(lib("wod-fran").copy(id = "mine", name = "My Fran"))
        val page = stepOf(freshState().copy(wods = listOf(own)))
        assertEquals(listOf("My Fran"), ownWods(page.model).map { it.name })
        assertTrue(ownWods(page.send(Msg.Wods.Filter(WodFilter.NO_WEIGHTS)).model).isEmpty())
    }

    @Test fun `a search goes through the chip, and Clear search brings every WOD back`() {
        val chipped = stepOf().send(Msg.Wods.Filter(WodFilter.AMRAP)).send(Msg.Wods.Search("pull"))
        assertEquals(listOf("Cindy", "Mary"), searchedWods(chipped.model).map { it.name })
        val none = chipped.send(Msg.Wods.Search("zzz"))
        assertTrue(searchedWods(none.model).isEmpty())
        val cleared = none.send(Msg.Wods.ClearSearch)
        assertEquals("", cleared.model.wods.query)
        assertFalse(searching(cleared.model))
        assertIgnored(cleared, Msg.Wods.ClearSearch)
        assertIgnored(cleared, Msg.Wods.Search(""))
    }

    // The builder

    @Test fun `New opens an empty For Time and leaving it untouched asks nothing`() {
        val step = building()
        assertEquals(Wod("d1", "", DEFAULT_TIMERS.getValue(TimerMode.FOR_TIME), 1, emptyList(), emptyList()), step.draft)
        assertNull(step.send(Msg.Nav.Back).model.wods.draft)
        assertIgnored(step, Msg.Wods.Save)
    }

    @Test fun `added movements start sensibly, several at once in the order they were ticked`() {
        val step = building().send(Msg.Wods.AddItems(listOf("thruster", "pullups", "run", "nope", "plank", "rowErg")))
        assertEquals(
            listOf(
                WodItem("thruster", 10, WodUnit.REPS, 30.0),
                WodItem("pullups", 10, WodUnit.REPS, 0.0),
                WodItem("run", 400, WodUnit.METERS, 0.0),
                WodItem("plank", 30, WodUnit.SECONDS, 0.0),
                WodItem("rowErg", 400, WodUnit.METERS, 0.0),
            ),
            step.draft.items,
        )
        assertIgnored(step, Msg.Wods.AddItem("nope"))
        assertEquals(startItem(catalogExercise("wallBall")!!), WodItem("wallBall", 10, WodUnit.REPS, 9.0))
        // No more than a WOD can hold.
        assertEquals(12, step.send(Msg.Wods.AddItems(List(20) { "burpee" })).draft.items.size)
    }

    @Test fun `a WOD built from scratch with a scheme is saved unnamed and opens its detail`() {
        val built = building()
            .send(Msg.Wods.AddItem("thruster"))
            .send(Msg.Wods.AddItem("pullups"))
            .send(Msg.Wods.SetScheme(listOf(21, 15, 9)))
        assertEquals(3, built.draft.rounds)
        // With a scheme the rounds wheel has nothing to say.
        assertIgnored(built, Msg.Wods.SetRounds(5))
        val saved = built.send(Msg.Wods.SetTimer(TimerConfig(TimerMode.FOR_TIME, work = 600, rest = 0, rounds = 1))).send(Msg.Wods.Save)
        val wod = saved.state.wods.single()
        // The name may stay empty: it goes by its format.
        assertEquals("", wod.name)
        assertEquals("21-15-9 · For Time", wodName(wod))
        assertEquals(wod(wod), wod)
        assertEquals(600, wod.timer.work)
        assertEquals("d1", saved.model.wods.detail)
        assertNull(saved.model.wods.draft)
        assertTrue(Cmd.Save(saved.state) in saved.cmds)
        // Back to every round the same: the three rounds stay.
        assertEquals(3, built.send(Msg.Wods.SetScheme(emptyList())).draft.rounds)
    }

    @Test fun `metres and a load are edited in place, by index, and moved and removed`() {
        var step = building().send(Msg.Wods.AddItem("run")).send(Msg.Wods.AddItem("kettlebellSwing")).send(Msg.Wods.Rename("  Engine  "))
        step = step.send(Msg.Wods.UpdateItem(0, WodItem("run", 800, WodUnit.METERS, 0.0)))
        step = step.send(Msg.Wods.UpdateItem(1, WodItem("kettlebellSwing", 21, WodUnit.REPS, 24.26)))
        assertEquals(WodItem("kettlebellSwing", 21, WodUnit.REPS, 24.5), step.draft.items[1])
        // A stale index from a sheet that is gone does not overwrite the neighbour.
        assertIgnored(step, Msg.Wods.UpdateItem(0, WodItem("kettlebellSwing", 5, WodUnit.REPS, 0.0)))
        // The name keeps what was typed while editing.
        assertEquals("  Engine  ", step.draft.name)
        val moved = step.send(Msg.Wods.MoveItem(1, 0))
        assertEquals(listOf("kettlebellSwing", "run"), moved.draft.items.map { it.exerciseId })
        assertIgnored(moved, Msg.Wods.RemoveItem(0, "run"))
        assertEquals(listOf("run"), moved.send(Msg.Wods.RemoveItem(0, "kettlebellSwing")).draft.items.map { it.exerciseId })
        val saved = moved.send(Msg.Wods.SetMode(TimerMode.AMRAP)).send(Msg.Wods.Save).state.wods.single()
        assertEquals("Engine", saved.name)
        assertEquals(DEFAULT_TIMERS.getValue(TimerMode.AMRAP), saved.timer)
    }

    @Test fun `switching the type keeps the items and brings back the numbers set before`() {
        val forTime = building().send(Msg.Wods.AddItem("burpee")).send(Msg.Wods.SetRounds(5))
            .send(Msg.Wods.SetTimer(TimerConfig(TimerMode.FOR_TIME, work = 900, rest = 0, rounds = 1)))
        val tabata = forTime.send(Msg.Wods.SetMode(TimerMode.TABATA))
        assertEquals(DEFAULT_TIMERS.getValue(TimerMode.TABATA), tabata.draft.timer)
        assertEquals(1, tabata.draft.rounds)
        assertEquals(listOf("burpee"), tabata.draft.items.map { it.exerciseId })
        assertIgnored(tabata, Msg.Wods.SetMode(TimerMode.TABATA))
        // A wheel of another mode is stale.
        assertIgnored(tabata, Msg.Wods.SetTimer(DEFAULT_TIMERS.getValue(TimerMode.EMOM)))
        val tabataSet = tabata.send(Msg.Wods.SetTimer(TimerConfig(TimerMode.TABATA, work = 30, rest = 15, rounds = 6)))
        val back = tabataSet.send(Msg.Wods.SetMode(TimerMode.FOR_TIME))
        assertEquals(5, back.draft.rounds)
        assertEquals(900, back.draft.timer.work)
        assertEquals(TimerConfig(TimerMode.TABATA, work = 30, rest = 15, rounds = 6), back.send(Msg.Wods.SetMode(TimerMode.TABATA)).draft.timer)
        // A scheme comes back too.
        val schemed = forTime.send(Msg.Wods.SetScheme(listOf(21, 15, 9))).send(Msg.Wods.SetMode(TimerMode.AMRAP)).send(Msg.Wods.SetMode(TimerMode.FOR_TIME))
        assertEquals(listOf(21, 15, 9), schemed.draft.scheme)
    }

    @Test fun `Max belongs to a Tabata, and leaving one turns it into a count`() {
        val tabata = building().send(Msg.Wods.SetMode(TimerMode.TABATA)).send(Msg.Wods.AddItems(listOf("burpee", "plank", "run")))
        assertEquals(listOf(0, 0, 0), tabata.draft.items.map { it.amount })
        val emom = tabata.send(Msg.Wods.SetMode(TimerMode.EMOM))
        assertEquals(listOf(10, 30, 400), emom.draft.items.map { it.amount })
        assertEquals("As many reps as possible each round", maxHint(WodUnit.REPS))
    }

    @Test fun `leaving with changes asks first`() {
        val changed = building().send(Msg.Wods.AddItem("burpee"))
        val asking = changed.send(Msg.Nav.Back)
        assertTrue(asking.model.wods.draft!!.asking)
        assertIgnored(asking, Msg.Wods.Leave)
        assertFalse(asking.send(Msg.Wods.KeepEditing).model.wods.draft!!.asking)
        val gone = asking.send(Msg.Wods.Discard)
        assertNull(gone.model.wods.draft)
        assertTrue(gone.state.wods.isEmpty())
    }

    @Test fun `Edit a copy opens a new WOD that is only stored on Save`() {
        val copied = stepOf().send(Msg.Wods.Open("wod-fran")).send(Msg.Wods.Copy("wod-fran"), id = "c1")
        assertEquals(lib("wod-fran").copy(id = "c1", name = "Fran (mine)"), copied.draft)
        assertTrue(copied.state.wods.isEmpty())
        assertFalse(draftEditsOwn(copied.model, copied.model.wods.draft!!))
        // Backing out leaves nothing behind and asks nothing.
        val left = copied.send(Msg.Wods.Leave)
        assertNull(left.model.wods.draft)
        assertTrue(left.state.wods.isEmpty())
        assertEquals("wod-fran", left.model.wods.detail)
        val saved = copied.send(Msg.Wods.Save)
        assertEquals(listOf("c1"), saved.state.wods.map { it.id })
        assertEquals("c1", saved.model.wods.detail)
        assertIgnored(stepOf(), Msg.Wods.Edit("wod-fran"))
    }

    @Test fun `an edit that changes the work gets a new id, a new name or cap keeps it`() {
        val saved = stepOf().send(Msg.Wods.Copy("wod-fran"), id = "c1").send(Msg.Wods.Save)
        val ran = saved.send(Msg.Wods.Start("c1"), now = STARTED_AT, id = "r1").let { step ->
            (1..6).fold(step) { at, tap -> at.send(Msg.Timer.Tap, now = STARTED_AT + tap * 60_000L) }
        }.send(Msg.Timer.Done)
        assertNotNull(wodBest(ran.state.timerSessions, "c1"))
        val renamed = ran.send(Msg.Wods.Edit("c1")).send(Msg.Wods.Rename("Short Fran"))
            .send(Msg.Wods.SetTimer(TimerConfig(TimerMode.FOR_TIME, work = 300, rest = 0, rounds = 1))).send(Msg.Wods.Save, id = "c2")
        assertEquals(listOf("c1"), renamed.state.wods.map { it.id })
        val heavier = renamed.send(Msg.Wods.Edit("c1"))
        val item = heavier.draft.items[0]
        val changed = heavier.send(Msg.Wods.UpdateItem(0, item.copy(load = 50.0))).send(Msg.Wods.Save, id = "c3")
        // Same place in the list, a new id: the old runs stay in the history but are no best of it.
        assertEquals(listOf("c3"), changed.state.wods.map { it.id })
        assertEquals("c3", changed.model.wods.detail)
        assertEquals(1, changed.state.timerSessions.size)
        assertNull(wodBest(changed.state.timerSessions, "c3"))
    }

    @Test fun `deleting an own WOD offers Undo and keeps its runs`() {
        val saved = building().send(Msg.Wods.AddItem("burpee")).send(Msg.Wods.Save)
        val ran = saved.send(Msg.Wods.Start("d1"), now = STARTED_AT, id = "r1").send(Msg.Timer.Tap, now = STARTED_AT + 100_000).send(Msg.Timer.Done)
        val deleted = ran.send(Msg.Wods.Delete("d1"))
        assertTrue(deleted.state.wods.isEmpty())
        assertNull(deleted.model.wods.detail)
        assertEquals(1, deleted.state.timerSessions.size)
        assertEquals(DeletedWod(saved.state.wods.single(), 0), deleted.model.undo.deletedWod)
        val snackbar = deleted.uiEvents.filterIsInstance<UiEvent.Snackbar>().single()
        assertEquals("Deleted For Time" to Msg.Wods.UndoDelete, snackbar.text to snackbar.onAction)
        val back = deleted.send(Msg.Wods.UndoDelete)
        assertEquals(saved.state.wods, back.state.wods)
        assertIgnored(stepOf(), Msg.Wods.Delete("wod-fran"))
    }

    @Test fun `an exercise created in the picker goes into the draft`() {
        val step = building().send(Msg.Exercises.AddCustom("Sled Push", Equipment.WEIGHT, AddTarget.Wod), id = "x")
        assertEquals(WodItem("custom-x", 10, WodUnit.REPS, 10.0), step.draft.items.single())
    }

    @Test fun `a running run stays with its copy when the WOD is deleted`() {
        val saved = building().send(Msg.Wods.AddItem("burpee")).send(Msg.Wods.Save)
        val running = saved.send(Msg.Wods.Start("d1"), now = STARTED_AT, id = "r1").send(Msg.Wods.Delete("d1"))
        assertEquals(saved.state.wods.single(), running.model.timerRun!!.wod)
    }

    // The draft file

    @Test fun `the draft is kept in its file as it changes, and comes back as Continue after the process ended`() {
        val opened = building()
        assertEquals(listOf(Cmd.SaveDraft(opened.model.wods.draft)), opened.cmds.filterIsInstance<Cmd.SaveDraft>())
        val named = opened.send(Msg.Wods.Rename("Half"))
        assertEquals(named.model.wods.draft, named.cmds.filterIsInstance<Cmd.SaveDraft>().single().draft)
        // The question is not part of the draft: asking writes nothing.
        val asking = named.send(Msg.Wods.Leave)
        assertTrue(asking.cmds.none { it is Cmd.SaveDraft })
        assertTrue(Cmd.SaveDraft(null) in asking.send(Msg.Wods.Discard).cmds)
        // A filter tap writes nothing.
        assertTrue(named.send(Msg.Wods.Filter(WodFilter.AMRAP)).cmds.none { it is Cmd.SaveDraft })

        val kept = named.model.wods.draft!!
        val (model, _) = init(Flags(freshState(), savedRest = null, healthEnabled = false, notifications = Notifications(true, true), savedDraft = kept))
        val restarted = Step(model)
        assertNull(restarted.model.wods.draft)
        assertEquals(kept, restarted.model.wods.pending)
        val continued = restarted.send(Msg.Wods.ContinueDraft)
        assertEquals(kept, continued.model.wods.draft)
        assertNull(continued.model.wods.pending)
        assertTrue(continued.cmds.none { it is Cmd.SaveDraft })
        val saved = continued.send(Msg.Wods.AddItem("burpee")).send(Msg.Wods.Save)
        assertTrue(Cmd.SaveDraft(null) in saved.cmds)
        val discarded = restarted.send(Msg.Wods.DiscardDraft)
        assertNull(discarded.model.wods.pending)
        assertEquals(listOf(Cmd.SaveDraft(null)), discarded.cmds)
        assertIgnored(discarded, Msg.Wods.ContinueDraft)
    }

    @Test fun `a tap on the Timer tab while it shows goes back to the top of its page`() {
        val detail = stepOf().send(Msg.Nav.SelectTab(Tab.TIMER)).send(Msg.Wods.Open("wod-fran"))
        val top = detail.send(Msg.Nav.SelectTab(Tab.TIMER))
        assertNull(top.model.wods.detail)
        assertEquals(detail.model.nav.timerTop + 1, top.model.nav.timerTop)
        // A builder with changes asks first.
        val asking = stepOf().send(Msg.Nav.SelectTab(Tab.TIMER)).send(Msg.Wods.New, id = "d1").send(Msg.Wods.AddItem("burpee")).send(Msg.Nav.SelectTab(Tab.TIMER))
        assertTrue(asking.model.wods.draft!!.asking)
    }

    // The picker

    @Test fun `the picker starts on what WODs are made of and ranks hits that start a word first`() {
        assertTrue("thruster" in WOD_EXERCISES && "rowErg" in WOD_EXERCISES && "burpee" in WOD_EXERCISES)
        assertFalse("legPress" in WOD_EXERCISES)
        assertEquals(0, matchRank("Run", "run"))
        assertEquals(1, matchRank("Cable Crunch", "run"))
        assertEquals(0, matchRank("Box Jump", " jump"))
        assertNull(matchRank("Squat", "run"))
        val hits = listOf("Cable Crunch", "Run").sortedBy { matchRank(it, "run") }
        assertEquals(listOf("Run", "Cable Crunch"), hits)
    }

    // The editor's values

    @Test fun `the item editor offers sensible values and keeps the current one`() {
        val reps = wodAmounts(WodUnit.REPS, 21)
        assertEquals((1..30).toList(), reps.take(30))
        assertEquals(listOf(35, 40), reps.subList(30, 32))
        assertEquals(1000, reps.last())
        assertTrue(150 in reps && 600 in reps && 37 !in reps)
        assertTrue(37 in wodAmounts(WodUnit.REPS, 37))
        assertEquals(listOf(100, 200, 400, 500, 800, 1000, 1600, 2000, 5000), wodAmounts(WodUnit.METERS, 400))
        assertTrue(42 in wodAmounts(WodUnit.SECONDS, 42))
        // Kettlebells by the kilo up to 40 kg, then 2.5 kg steps; bars in 2.5 kg steps; pounds in 5 lb steps.
        val bell = wodLoads(Equipment.WEIGHT, WeightUnit.KG, 16.0)
        assertEquals(listOf(0.0, 1.0, 2.0), bell.take(3))
        assertTrue(23.0 in bell && 40.0 in bell && 42.5 in bell && 41.0 !in bell)
        assertEquals(200.0, bell.last(), 0.0)
        val bar = wodLoads(Equipment.BARBELL, WeightUnit.KG, 43.0)
        assertEquals(listOf(0.0, 2.5, 5.0), bar.take(3))
        assertTrue(43.0 in bar && 41.0 !in bar)
        assertEquals(200.0, bar.last(), 0.0)
        val pounds = wodLoads(Equipment.WEIGHT, WeightUnit.LB, 43.0)
        assertEquals(listOf(0.0, 5.0, 10.0), pounds.take(3))
        assertEquals(440.0, pounds.last(), 0.0)
        assertTrue(95.0 in pounds)
        val thruster = WodItem("thruster", 21, WodUnit.REPS, 43.0)
        assertEquals(WodItem("thruster", 400, WodUnit.METERS, 43.0), withUnit(thruster, WodUnit.METERS))
        assertEquals(0, withUnit(withMax(thruster, true), WodUnit.SECONDS).amount)
        assertEquals(10, withMax(withMax(thruster, true), false).amount)
        assertEquals(listOf(30, 20, 10), parseScheme("30-20-10"))
        assertEquals(listOf(30, 20, 10), parseScheme(" 30 20  10 "))
        assertEquals(emptyList<Int>(), parseScheme("-"))
    }

    // The saved state

    @Test fun `version 7 gets an empty list of WODs, and a backup's WODs are brought into their limits`() {
        val v7 = encodeState(freshState()).replace("\"version\":$STATE_VERSION", "\"version\":7").replace(",\"wods\":[]", "")
        assertFalse(v7.contains("wods"))
        val migrated = decodeState(v7)!!
        assertEquals(8, migrated.version)
        assertEquals(freshState(), migrated)

        val own = wod(lib("wod-fran").copy(id = "mine", name = "My Fran"))
        val json = encodeState(freshState().copy(wods = listOf(own)))
        assertTrue(json.contains("\"wods\":[{\"id\":\"mine\",\"name\":\"My Fran\",\"timer\":{\"mode\":\"forTime\",\"work\":600,\"rest\":0,\"rounds\":1},\"rounds\":3,\"scheme\":[21,15,9],\"items\":[{\"exerciseId\":\"thruster\",\"amount\":21,\"unit\":\"reps\",\"load\":43.0}"))
        assertEquals(listOf(own), decodeState(json)!!.wods)

        // A hand-edited backup: an unknown unit, a load out of range, a benchmark's id and a second one with the same id.
        val fake = lib("wod-cindy").copy(name = "Fake", id = "wod-fran")
        val twice = lib("wod-cindy").copy(name = "Twice", id = "mine")
        val edited = encodeState(freshState().copy(wods = listOf(fake, own, twice)))
            .replace("\"unit\":\"reps\",\"load\":43.0", "\"unit\":\"laps\",\"load\":900")
        val read = decodeState(edited)!!.wods
        assertEquals(listOf("mine"), read.map { it.id })
        assertEquals(WodItem("thruster", 21, WodUnit.REPS, 500.0), read.single().items[0])
    }

    @Test fun `an erase takes the detail of an own WOD away`() {
        val saved = building().send(Msg.Wods.AddItem("burpee")).send(Msg.Wods.Save)
        assertEquals("d1", saved.model.wods.detail)
        val erased = saved.send(Msg.Data.Reset)
        assertNull(erased.model.wods.detail)
    }
}

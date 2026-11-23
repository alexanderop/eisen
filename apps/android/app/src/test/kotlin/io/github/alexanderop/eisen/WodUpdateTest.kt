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
        assertEquals(listOf("21 × Thruster 43kg", "21 × Pull-ups"), notice.lines)
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

    @Test fun `Fran moves on a round per tap, and the tap of the last round stops the clock`() {
        var step = startedWod("wod-fran")
        assertEquals(RoundCard("Round 1 of 3", listOf("21 × Thruster 43kg", "21 × Pull-ups")), step.card(STARTED_AT))
        // Nothing counts in the lead-in.
        assertSame(step.model, step.send(Msg.Timer.Tap, now = STARTED_AT + 5_000).model)
        step = step.send(Msg.Timer.Tap, now = STARTED_AT + 60_000)
        assertEquals(RoundCard("Round 2 of 3", listOf("15 × Thruster 43kg", "15 × Pull-ups")), step.card(STARTED_AT + 60_000))
        val notice = step.cmds.filterIsInstance<Cmd.ShowTimer>().single().notice
        assertEquals("Work · Round 2 of 3", notice.text)
        assertEquals(listOf("15 × Thruster 43kg", "15 × Pull-ups"), notice.lines)
        step = step.send(Msg.Timer.Tap, now = STARTED_AT + 120_000)
        assertEquals("Round 3 of 3", step.card(STARTED_AT + 120_000)?.heading)
        // A WOD has no "Finish": it ends with its last tap, or early through "End".
        assertIgnored(step, Msg.Timer.Finish)
        val done = step.send(Msg.Timer.Tap, now = STARTED_AT + 302_000)
        val finished = done.model.timer as TimerState.Finished
        assertEquals("wod-fran", finished.session.wodId)
        assertEquals(292, finished.session.seconds)
        assertEquals(3, finished.session.rounds)
        assertTrue(finished.session.completed)
        assertEquals(lib("wod-fran"), finished.wod)
        assertEquals(3, finished.undo?.taps)
        assertEquals(finished.session, done.state.timerSessions.single())
        assertTrue(Cmd.HideTimer in done.cmds && Cmd.SaveTimer(null) in done.cmds)
    }

    @Test fun `Undo on the result takes the last tap back and the run goes on from the real clock`() {
        var step = startedWod("wod-fran")
        for (tap in 1..3) step = step.send(Msg.Timer.Tap, now = STARTED_AT + tap * 60_000L)
        assertEquals(1, step.state.timerSessions.size)
        val back = step.send(Msg.Timer.UndoFinish, now = STARTED_AT + 200_000)
        val run = back.model.timerRun!!
        assertEquals(2, run.taps)
        assertEquals(STARTED_AT, run.startedAt)
        assertTrue(back.state.timerSessions.isEmpty())
        assertTrue(Cmd.SaveTimer(run) in back.cmds)
        assertTrue(back.cmds.any { it is Cmd.ScheduleTimer } && back.cmds.any { it is Cmd.ShowTimer })
        // The clock never stopped: the time that passed counts.
        assertEquals("Round 3 of 3", back.card(STARTED_AT + 200_000)?.heading)
        val finished = back.send(Msg.Timer.Tap, now = STARTED_AT + 250_000).model.timer as TimerState.Finished
        assertEquals(240, finished.session.seconds)
        // Only a run its last tap ended can be undone.
        assertIgnored(back, Msg.Timer.UndoFinish)
        val ranOut = startedWod("wod-cindy").send(Msg.Timer.Tick, now = STARTED_AT + 1_300_000)
        assertNull((ranOut.model.timer as TimerState.Finished).undo)
        assertIgnored(ranOut, Msg.Timer.UndoFinish)
    }

    @Test fun `ending a For Time early is saved as stopped and is never a best`() {
        val step = startedWod("wod-fran").send(Msg.Timer.Tap, now = STARTED_AT + 60_000).send(Msg.Timer.SaveAndEnd, now = STARTED_AT + 100_000)
        val session = (step.model.timer as TimerState.Finished).session
        assertFalse(session.completed)
        assertEquals(1, session.rounds)
        assertNull(wodBest(step.state.timerSessions, "wod-fran"))
    }

    @Test fun `Murph ticks its items off one by one, and the tap on the last ends it`() {
        var step = startedWod("wod-murph")
        val lines = listOf("1.6 km Run", "100 × Pull-ups", "200 × Push-ups", "300 × Air Squat", "1.6 km Run")
        assertEquals(RoundCard(null, lines, current = 0), step.card(STARTED_AT + 20_000))
        for (tap in 1..2) step = step.send(Msg.Timer.Tap, now = STARTED_AT + tap * 60_000L)
        assertEquals(RoundCard(null, lines, current = 2), step.card(STARTED_AT + 120_000))
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
        assertEquals(RoundCard(null, listOf("5 × Pull-ups", "10 × Push-ups", "15 × Air Squat")), cindy.card(STARTED_AT + 420_000))
        assertEquals(7, cindy.model.timerRun!!.taps)

        val chelsea = startedWod("wod-chelsea")
        // The twelfth minute after the lead-in.
        assertEquals(RoundCard("Round 12 of 30", listOf("5 × Pull-ups", "10 × Push-ups", "15 × Air Squat")), chelsea.card(STARTED_AT + 10_000 + 11 * 60_000 + 5_000))
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
        assertEquals(RoundCard("Round 1 of 4", listOf("Max Burpee")), step.card(STARTED_AT + 15_000))
        assertEquals(RoundCard("Up next · Round 2 of 4", listOf("20s Plank"), upNext = true), step.card(STARTED_AT + 35_000))
        assertEquals(RoundCard("Round 2 of 4", listOf("20s Plank")), step.card(STARTED_AT + 45_000))
        assertEquals(RoundCard("Round 3 of 4", listOf("Max Burpee")), step.card(STARTED_AT + 75_000))
        assertEquals("Rest · Up next · Round 2 of 4", timerNotice(step.model.timerRun!!, STARTED_AT + 35_000, step.state).text)
    }

    @Test fun `Grace is one movement and one tap ends it`() {
        val grace = startedWod("wod-grace")
        assertEquals(RoundCard(null, listOf("30 × Clean and Jerk 61kg"), current = 0), grace.card(STARTED_AT + 20_000))
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
            (1..3).fold(step) { at, tap -> at.send(Msg.Timer.Tap, now = STARTED_AT + tap * 60_000L) }
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

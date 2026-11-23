package io.github.alexanderop.eisen

import androidx.health.connect.client.records.ExerciseSessionRecord
import io.github.alexanderop.eisen.domain.BoxingPlan
import io.github.alexanderop.eisen.domain.BoxingRound
import io.github.alexanderop.eisen.domain.BoxingStyle
import io.github.alexanderop.eisen.domain.BoxingTheme
import io.github.alexanderop.eisen.domain.DEFAULT_TIMERS
import io.github.alexanderop.eisen.domain.DEFAULT_TIMER_CALLS
import io.github.alexanderop.eisen.domain.Env
import io.github.alexanderop.eisen.domain.sessionSummary
import io.github.alexanderop.eisen.domain.STATE_VERSION
import io.github.alexanderop.eisen.domain.StateJson
import io.github.alexanderop.eisen.domain.TimerConfig
import io.github.alexanderop.eisen.domain.TimerMode
import io.github.alexanderop.eisen.domain.TimerPreset
import io.github.alexanderop.eisen.domain.TimerRun
import io.github.alexanderop.eisen.domain.TimerSession
import io.github.alexanderop.eisen.domain.comboCount
import io.github.alexanderop.eisen.domain.decodeState
import io.github.alexanderop.eisen.domain.encodeState
import io.github.alexanderop.eisen.domain.freshState
import io.github.alexanderop.eisen.domain.soundCombo
import io.github.alexanderop.eisen.domain.themeCombos
import io.github.alexanderop.eisen.domain.timerConfig
import io.github.alexanderop.eisen.domain.timerCues
import io.github.alexanderop.eisen.domain.elm.TEMPLATE_UNDO_MILLIS
import io.github.alexanderop.eisen.domain.elm.TIMER_CALL_VIBRATION
import io.github.alexanderop.eisen.domain.elm.Cmd
import io.github.alexanderop.eisen.domain.elm.Flags
import io.github.alexanderop.eisen.domain.elm.Msg
import io.github.alexanderop.eisen.domain.elm.Notifications
import io.github.alexanderop.eisen.domain.elm.Model
import io.github.alexanderop.eisen.domain.elm.callsText
import io.github.alexanderop.eisen.domain.elm.comboGroups
import io.github.alexanderop.eisen.domain.elm.combosText
import io.github.alexanderop.eisen.domain.elm.pickText
import io.github.alexanderop.eisen.domain.elm.planTotal
import io.github.alexanderop.eisen.domain.elm.roundDetail
import io.github.alexanderop.eisen.domain.elm.themeExample
import io.github.alexanderop.eisen.domain.elm.toggledPick
import io.github.alexanderop.eisen.domain.elm.Sub
import io.github.alexanderop.eisen.domain.elm.subscriptions
import io.github.alexanderop.eisen.domain.elm.TimerState
import io.github.alexanderop.eisen.domain.elm.UiEvent
import io.github.alexanderop.eisen.domain.elm.init
import io.github.alexanderop.eisen.domain.elm.timerEndsAt
import io.github.alexanderop.eisen.domain.elm.timerNotice
import io.github.alexanderop.eisen.domain.elm.timerRun
import io.github.alexanderop.eisen.domain.elm.update
import io.github.alexanderop.eisen.health.timerSessionFields
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneOffset

/** The boxing timer through `update`: its setup and round sheet, the run with its seed, the result and the saved state. */
class BoxingUpdateTest {
    private val default = DEFAULT_TIMERS.getValue(TimerMode.BOXING)
    private val plan get() = default.boxing!!

    /** The boxing setup as it opens on a fresh install. */
    private fun setup(): Step = stepOf().send(Msg.Timer.Open(TimerMode.BOXING), now = STARTED_AT)

    private val Step.config: TimerConfig get() = model.timerSetup!!.config
    private val Step.rounds: List<BoxingRound> get() = config.boxing!!.rounds

    /** Two short rounds, so a test runs through work, rest and the end. */
    private val short = timerConfig(
        TimerConfig(TimerMode.BOXING, 30, 10, 2, BoxingPlan(listOf(BoxingRound(BoxingTheme.JAB, 4, 0), BoxingRound(BoxingTheme.TWO, 5, 15)), BoxingStyle.NAMES)),
    )

    /** [short] started at [STARTED_AT] with the id "b1", drawing [seed]. */
    private fun started(seed: Int = 1234): Step {
        val open = setup().send(Msg.Timer.Edit(short), now = STARTED_AT)
        val (model, cmds) = update(Msg.Timer.Start, open.model, Env(STARTED_AT, newSeed = { seed }) { "b1" })
        return Step(model, cmds)
    }

    @Test fun `the setup opens with the default plan, one round per round`() {
        val open = setup()
        assertEquals(default, open.config)
        assertEquals(6, open.rounds.size)
        assertNull(open.model.timerSetup!!.round)
    }

    @Test fun `changing the number of rounds keeps the rounds and repeats the last one`() {
        val more = setup().send(Msg.Timer.Edit(default.copy(rounds = 8)))
        assertEquals(plan.rounds + List(2) { plan.rounds.last() }, more.rounds)
        val fewer = more.send(Msg.Timer.Edit(more.config.copy(rounds = 3)))
        assertEquals(plan.rounds.take(3), fewer.rounds)
        // Lowering the number loses nothing: the rounds come back when it goes up again.
        assertEquals(more.rounds, fewer.send(Msg.Timer.Edit(fewer.config.copy(rounds = 8))).rounds)
        val four = setup().send(Msg.Timer.Edit(default.copy(rounds = 4)))
        assertEquals(plan.rounds.drop(4), four.model.timerSetup!!.removed)
        assertEquals(plan.rounds, four.send(Msg.Timer.Edit(four.config.copy(rounds = 6))).rounds)
        // Out of the limits of a boxing round: 15 rounds at most, 30 s to 10 min, a rest of 10 s to 5 min.
        val wild = fewer.send(Msg.Timer.Edit(fewer.config.copy(rounds = 99, work = 5, rest = 0)))
        assertEquals(listOf(15, 30, 10, 15), listOf(wild.config.rounds, wild.config.work, wild.config.rest, wild.rounds.size))
    }

    @Test fun `a round's sheet opens, steps through the rounds, edits, and Back closes it before the setup`() {
        val open = setup().send(Msg.Timer.OpenRound(2))
        assertEquals(2, open.model.timerSetup!!.round)
        assertIgnored(open, Msg.Timer.OpenRound(6))
        assertIgnored(open, Msg.Timer.OpenRound(2))
        val stepped = open.send(Msg.Timer.OpenRound(3))
        assertEquals(3, stepped.model.timerSetup!!.round)
        // A pace and a split out of range come back into the limits of a round.
        val edited = stepped.send(Msg.Timer.EditRound(3, BoxingRound(BoxingTheme.BODY, 99, 30)))
        assertEquals(BoxingRound(BoxingTheme.BODY, 15, 30), edited.rounds[3])
        assertEquals(BoxingRound(BoxingTheme.FREESTYLE, 5, 0), edited.send(Msg.Timer.EditRound(3, BoxingRound(BoxingTheme.FREESTYLE, 5, 7))).rounds[3])
        assertIgnored(edited, Msg.Timer.EditRound(3, BoxingRound(BoxingTheme.BODY, 15, 30)))
        assertIgnored(edited, Msg.Timer.EditRound(6, BoxingRound(BoxingTheme.BODY, 5, 0)))
        // Fewer rounds than the open one: its sheet closes.
        assertNull(edited.send(Msg.Timer.Edit(edited.config.copy(rounds = 2))).model.timerSetup!!.round)
        val back = edited.send(Msg.Nav.Back)
        assertNull(back.model.timerSetup!!.round)
        assertEquals(edited.config, back.config)
        assertNull(back.send(Msg.Nav.Back).model.timerSetup)
        assertIgnored(back, Msg.Timer.CloseRound)
    }

    @Test fun `a template is laid over the rounds in the chosen style, with Undo beside the chips for a few seconds`() {
        val numbers = setup().send(Msg.Timer.SetStyle(BoxingStyle.NUMBERS))
        val applied = numbers.send(Msg.Timer.ApplyTemplate(1), now = LATER)
        assertEquals(List(6) { BoxingRound(BoxingTheme.MIX, 6, 30) }, applied.rounds)
        assertEquals(BoxingStyle.NUMBERS, applied.config.boxing!!.style)
        // No snackbar over Start: the Undo stands next to the chips and goes after six seconds.
        assertTrue(applied.uiEvents.isEmpty())
        assertTrue(Cmd.After(TEMPLATE_UNDO_MILLIS, Msg.Timer.ForgetTemplate(LATER)) in applied.cmds)
        assertEquals(numbers.config.boxing, applied.model.timerSetup!!.beforeTemplate)
        val undone = applied.send(Msg.Timer.UndoTemplate)
        assertEquals(numbers.config, undone.config)
        assertIgnored(undone, Msg.Timer.UndoTemplate)
        assertIgnored(numbers, Msg.Timer.ApplyTemplate(9))
        val forgotten = applied.send(Msg.Timer.ForgetTemplate(LATER))
        assertNull(forgotten.model.timerSetup!!.beforeTemplate)
        assertEquals(applied.config, forgotten.config)
        // A later template has its own six seconds.
        val again = applied.send(Msg.Timer.ApplyTemplate(3), now = LATER + 2_000)
        assertIgnored(again, Msg.Timer.ForgetTemplate(LATER))
        // The Ladder of four rounds on 8 rounds climbs twice.
        val ladder = setup().send(Msg.Timer.Edit(default.copy(rounds = 8))).send(Msg.Timer.ApplyTemplate(0))
        assertEquals(
            listOf(BoxingTheme.JAB, BoxingTheme.TWO, BoxingTheme.THREE, BoxingTheme.FOUR, BoxingTheme.JAB, BoxingTheme.TWO, BoxingTheme.THREE, BoxingTheme.FOUR),
            ladder.rounds.map { it.theme },
        )
        // Another change ends the Undo.
        val changed = applied.send(Msg.Timer.EditRound(0, BoxingRound(BoxingTheme.JAB, 4, 0)))
        assertIgnored(changed, Msg.Timer.UndoTemplate)
    }

    @Test fun `every edit is kept as the mode's last config, so leaving and coming back shows the same plan`() {
        val edited = setup().send(Msg.Timer.Edit(default.copy(rounds = 4))).send(Msg.Timer.EditRound(0, BoxingRound(BoxingTheme.BODY, 8, 0)))
        assertEquals(edited.config, edited.state.timerConfigs[TimerMode.BOXING])
        assertTrue(Cmd.Save(edited.state) in edited.cmds)
        val back = edited.send(Msg.Timer.CloseSetup).send(Msg.Timer.Open(TimerMode.BOXING))
        assertEquals(edited.config, back.config)
        // The rounds taken off the end are still there while the app runs.
        assertEquals(plan.rounds.drop(4), back.model.timerSetup!!.removed)
        assertEquals(plan.rounds.drop(4), back.send(Msg.Timer.Edit(back.config.copy(rounds = 6))).rounds.drop(4))
    }

    @Test fun `a round calls only the combinations picked for it, and a new theme starts with all`() {
        val picked = setup().send(Msg.Timer.EditRound(2, plan.rounds[2].copy(pick = listOf("jab-cross-hook", "jab-jab-cross"))))
        assertEquals(listOf("jab-cross-hook", "jab-jab-cross"), picked.rounds[2].pick)
        // All of them picked is the same as none.
        val all = themeCombos(BoxingTheme.THREE).map { it.id }
        assertEquals(emptyList<String>(), picked.send(Msg.Timer.EditRound(2, plan.rounds[2].copy(pick = all))).rounds[2].pick)
        // Another theme clears the picks.
        val rethemed = picked.send(Msg.Timer.EditRound(2, picked.rounds[2].copy(theme = BoxingTheme.FOUR)))
        assertEquals(BoxingRound(BoxingTheme.FOUR, 6, 0), rethemed.rounds[2])
        // A run of the picked round calls nothing else.
        val config = timerConfig(picked.config.copy(rounds = 3))
        val third = timerCues(config, DEFAULT_TIMER_CALLS, 9).filter { it.at in 10_000L + 2 * 240_000 until 10_000L + 2 * 240_000 + 180_000 }
        assertEquals(setOf("box-jab-cross-hook-n", "box-jab-jab-cross-n"), third.mapNotNull { cue -> soundCombo(cue.sound)?.let { cue.sound } }.toSet())
    }

    @Test fun `rounds are moved, duplicated and removed, the timer follows, and the sheet stays with its round`() {
        val open = setup().send(Msg.Timer.OpenRound(1))
        val later = open.send(Msg.Timer.MoveRound(1, 2))
        assertEquals(listOf(BoxingTheme.JAB, BoxingTheme.THREE, BoxingTheme.TWO), later.rounds.take(3).map { it.theme })
        assertEquals(2, later.model.timerSetup!!.round)
        // A drag over several places.
        val dragged = setup().send(Msg.Timer.MoveRound(5, 0))
        assertEquals(listOf(BoxingTheme.FREESTYLE, BoxingTheme.JAB, BoxingTheme.TWO, BoxingTheme.THREE, BoxingTheme.FOUR, BoxingTheme.MIX), dragged.rounds.map { it.theme })
        assertIgnored(open, Msg.Timer.MoveRound(0, 6))
        val copy = open.send(Msg.Timer.DuplicateRound(1))
        assertEquals(7, copy.config.rounds)
        assertEquals(listOf(BoxingTheme.JAB, BoxingTheme.TWO, BoxingTheme.TWO), copy.rounds.take(3).map { it.theme })
        assertEquals(2, copy.model.timerSetup!!.round)
        val removed = open.send(Msg.Timer.RemoveRound(1))
        assertEquals(5, removed.config.rounds)
        assertEquals(BoxingTheme.THREE, removed.rounds[1].theme)
        assertEquals(1, removed.model.timerSetup!!.round)
        assertEquals(removed.config, removed.state.timerConfigs[TimerMode.BOXING])
        // One round stays, fifteen is the most.
        val one = setup().send(Msg.Timer.Edit(default.copy(rounds = 1)))
        assertIgnored(one, Msg.Timer.RemoveRound(0))
        val full = setup().send(Msg.Timer.Edit(default.copy(rounds = 15)))
        assertIgnored(full, Msg.Timer.DuplicateRound(0))
    }

    @Test fun `a round is copied to all rounds, and the sheet says so until something else changes`() {
        val copied = setup().send(Msg.Timer.OpenRound(1)).send(Msg.Timer.CopyToAll(1))
        assertEquals(List(6) { plan.rounds[1] }, copied.rounds)
        assertTrue(copied.model.timerSetup!!.copied)
        assertFalse(copied.send(Msg.Timer.EditRound(1, BoxingRound(BoxingTheme.HOOKS, 5, 0))).model.timerSetup!!.copied)
        assertFalse(copied.send(Msg.Timer.OpenRound(2)).model.timerSetup!!.copied)
        assertIgnored(copied, Msg.Timer.CopyToAll(1))
        assertIgnored(copied, Msg.Timer.CopyToAll(6))
    }

    @Test fun `the calls are names or numbers, and Listen steps through the round's combinations`() {
        val numbers = setup().send(Msg.Timer.SetStyle(BoxingStyle.NUMBERS))
        assertEquals(BoxingStyle.NUMBERS, numbers.config.boxing!!.style)
        assertIgnored(numbers, Msg.Timer.SetStyle(BoxingStyle.NUMBERS))
        val first = numbers.send(Msg.Timer.Listen(1))
        val sound = first.cmds.single() as Cmd.PlayTimerSound
        assertEquals("box-jab-cross-d", sound.sound)
        assertEquals("jab-cross", first.model.timerSetup!!.heard)
        assertEquals("box-jab-hook-d", (first.send(Msg.Timer.Listen(1)).cmds.single() as Cmd.PlayTimerSound).sound)
        // Without a recording it beeps, so the button is never silent.
        assertEquals("beep", sound.fallback)
        assertEquals(Cmd.PlayTimerSound("box-six-d", "beep"), numbers.send(Msg.Timer.PlayCombo("six")).cmds.single())
        // A freestyle round has nothing to call.
        assertIgnored(numbers, Msg.Timer.Listen(5))
        // The other timers have no plan to edit.
        val tabata = stepOf().send(Msg.Timer.Open(TimerMode.TABATA))
        assertIgnored(tabata, Msg.Timer.OpenRound(0))
        assertIgnored(tabata, Msg.Timer.ApplyTemplate(0))
        assertIgnored(tabata, Msg.Timer.SetStyle(BoxingStyle.NUMBERS))
    }

    @Test fun `Start gives the run a seed and plays the combinations it picks`() {
        val run = started(seed = 77)
        val timed = run.model.timerRun!!
        assertEquals(77, timed.seed)
        assertEquals("Boxing", timed.name)
        val schedule = run.cmds.filterIsInstance<Cmd.ScheduleTimer>().single()
        assertEquals(timerCues(short, DEFAULT_TIMER_CALLS, 77).map { it.at + STARTED_AT to it.sound }, schedule.sounds.map { it.at to it.sound })
        assertTrue(schedule.sounds.any { soundCombo(it.sound) != null })
        // The plain timers carry none.
        assertNull(stepOf().send(Msg.Timer.Open(TimerMode.TABATA)).send(Msg.Timer.Start).model.timerRun!!.seed)
        // Each call is felt, when the haptics are on.
        assertEquals(comboCount(timerCues(short, DEFAULT_TIMER_CALLS, 77)), schedule.vibrations.count { it.pattern == TIMER_CALL_VIBRATION })
        // Its clock ticks on the half second, when the combinations are called, so the screen shows each as it is said.
        assertEquals(setOf<Sub>(Sub.TimerClock(STARTED_AT, 500)), subscriptions(run.model))
    }

    @Test fun `a restored run calls the same combinations, and Again draws new ones`() {
        val run = started(seed = 99).model.timerRun!!
        val flags = Flags(freshState(), savedRest = null, healthEnabled = false, notifications = Notifications(true, true), savedTimer = run)
        val (restored, cmds) = init(flags.copy(now = STARTED_AT + 20_000))
        assertEquals(run, (restored.timer as TimerState.Running).run)
        val sounds = cmds.filterIsInstance<Cmd.ScheduleTimer>().single().sounds
        val expected = timerCues(short, DEFAULT_TIMER_CALLS, 99).filter { it.at >= 20_000 }.map { it.at + STARTED_AT to it.sound }
        assertEquals(expected, sounds.map { it.at to it.sound })

        val over = timerEndsAt(run) + 500
        val result = started(seed = 99).send(Msg.Timer.Tick, now = over)
        val finished = result.model.timer as TimerState.Finished
        assertEquals(comboCount(timerCues(short, DEFAULT_TIMER_CALLS, 99)), finished.combos)
        val again = result.send(Msg.Timer.Again, now = over + 5_000, id = "b2").model.timerRun!!
        assertNotNull(again.seed)
        assertNotEquals(run.seed, again.seed)
        assertEquals(short, again.config)
    }

    @Test fun `the result counts the combinations called up to where the run ended`() {
        val ended = started(seed = 5).send(Msg.Timer.End, now = STARTED_AT + 25_000).send(Msg.Timer.SaveAndEnd, now = STARTED_AT + 25_000)
        val finished = ended.model.timer as TimerState.Finished
        val called = timerCues(short, DEFAULT_TIMER_CALLS, 5).filter { it.at <= 25_000 && soundCombo(it.sound) != null }
        assertEquals(called.size, finished.combos)
        assertTrue(called.isNotEmpty())
        assertEquals("0 rounds · 15s", sessionSummary(finished.session))
        // The other timers have no count of combinations.
        val tabata = stepOf().send(Msg.Timer.Open(TimerMode.TABATA)).send(Msg.Timer.Start, now = STARTED_AT)
            .send(Msg.Timer.SaveAndEnd, now = STARTED_AT + 15_000).model.timer as TimerState.Finished
        assertNull(tabata.combos)
    }

    @Test fun `the notification names the round's theme, and in a rest the one that comes next`() {
        val run = started().model.timerRun!!
        val state = freshState()
        assertEquals("Round 1 of 2 · Jab only", timerNotice(run, STARTED_AT + 12_000, state).text)
        assertEquals("Rest · next: 2-punch combos", timerNotice(run, STARTED_AT + 45_000, state).text)
        assertEquals("Round 2 of 2 · 2-punch combos", timerNotice(run, STARTED_AT + 55_000, state).text)
        assertEquals("Get ready", timerNotice(run, STARTED_AT + 2_000, state).text)
        assertEquals("Paused · Round 1 of 2 · Jab only", timerNotice(run.copy(pausedAt = STARTED_AT + 12_000), STARTED_AT + 30_000, state).text)
    }

    @Test fun `a preset keeps its plan and starts with it`() {
        val saved = setup().send(Msg.Timer.ApplyTemplate(2)).send(Msg.Timer.SavePreset("Body day"))
        val preset = saved.state.timerPresets.single()
        assertEquals(saved.config, preset.config)
        val run = saved.send(Msg.Timer.CloseSetup).send(Msg.Timer.StartPreset(preset.id), now = STARTED_AT).model.timerRun!!
        assertEquals(preset.config, run.config)
        assertEquals("Body day", run.name)
        assertNotNull(run.seed)
    }

    @Test fun `version 8 gets the boxing timer, and a file's plans get one round per round`() {
        val current = StateJson.parseToJsonElement(encodeState(freshState())).jsonObject
        val configs = current.getValue("timerConfigs").jsonObject
        val v8 = JsonObject(current + mapOf("version" to JsonPrimitive(8), "timerConfigs" to JsonObject(configs - "boxing")))
        assertFalse(v8.toString().contains("boxing"))
        val migrated = decodeState(v8.toString())!!
        assertEquals(STATE_VERSION, migrated.version)
        assertEquals(freshState(), migrated)
        assertEquals(default, migrated.timerConfigs[TimerMode.BOXING])

        // The shape the PWA writes: the plan inside the boxing config, none in the others.
        val json = encodeState(freshState())
        assertTrue(json.contains("\"boxing\":{\"mode\":\"boxing\",\"work\":180,\"rest\":60,\"rounds\":6,\"boxing\":{\"rounds\":[{\"theme\":\"jab\",\"every\":4,\"split\":0,\"pick\":[]},"))
        assertTrue(json.contains("\"tabata\":{\"mode\":\"tabata\",\"work\":20,\"rest\":10,\"rounds\":8}"))

        // A hand-edited preset with fewer rounds in its plan than on its timer.
        val odd = TimerConfig(TimerMode.BOXING, 120, 30, 4, BoxingPlan(listOf(BoxingRound(BoxingTheme.HOOKS, 2, 0)), BoxingStyle.NUMBERS))
        val read = decodeState(encodeState(freshState().copy(timerPresets = listOf(TimerPreset("p1", "Hooks", odd)))))!!
        assertEquals(List(4) { BoxingRound(BoxingTheme.HOOKS, 3, 0) }, read.timerPresets.single().config.boxing!!.rounds)
        // A run in its file keeps its seed.
        val run = TimerRun("r1", "Boxing", short, DEFAULT_TIMER_CALLS, STARTED_AT, seed = 42)
        assertEquals(run, StateJson.decodeFromString(TimerRun.serializer(), StateJson.encodeToString(TimerRun.serializer(), run)))
    }

    @Test fun `a boxing session goes to Health Connect as boxing`() {
        val session = TimerSession("b1", "Boxing", default, 1_000, 1_400_000, 1380, 6, true)
        val fields = timerSessionFields(session, ZoneOffset.UTC)!!
        assertEquals(ExerciseSessionRecord.EXERCISE_TYPE_BOXING, fields.exerciseType)
        assertEquals("Boxing 6 × 3:00 / 1:00: 6 rounds · 23:00", fields.notes)
    }

    @Test fun `the notification question comes before the run, which starts with the answer`() {
        val never = Step(Model(freshState(), notifications = Notifications(granted = false, asked = false)))
        val asked = never.send(Msg.Timer.Open(TimerMode.BOXING)).send(Msg.Timer.Start, now = STARTED_AT)
        assertNull(asked.model.timerRun)
        assertEquals(listOf(Cmd.MarkNotificationsAsked, Cmd.Ui(UiEvent.RequestNotificationPermission)), asked.cmds.filter { it is Cmd.MarkNotificationsAsked || it is Cmd.Ui })
        val answered = asked.send(Msg.Rest.NotificationPermission(false), now = STARTED_AT + 4_000)
        val run = answered.model.timerRun!!
        assertEquals(STARTED_AT + 4_000, run.startedAt)
        assertNull(answered.model.pendingStart)
        assertTrue(answered.cmds.any { it is Cmd.ScheduleTimer })
        // Asked once: the next Start runs at once.
        val next = answered.send(Msg.Timer.Discard).send(Msg.Timer.Start)
        assertNotNull(next.model.timerRun)
        assertTrue(next.uiEvents.none { it == UiEvent.RequestNotificationPermission })
    }

    @Test fun `the setup says what the plan is in the same words as the PWA`() {
        assertEquals("6 rounds · 23:00 total", planTotal(default))
        assertEquals("Fast", roundDetail(plan.rounds[0]))
        assertEquals("Steady · speed and power 30s", roundDetail(plan.rounds[4]))
        assertEquals("no calls", roundDetail(plan.rounds[5]))
        assertEquals("Double jab", themeExample(BoxingTheme.JAB, BoxingStyle.NAMES))
        assertEquals("1 – 2 – 3", themeExample(BoxingTheme.THREE, BoxingStyle.NUMBERS))
        assertEquals("Any combination", themeExample(BoxingTheme.MIX, BoxingStyle.NAMES))
        assertEquals("No calls", themeExample(BoxingTheme.FREESTYLE, BoxingStyle.NAMES))
        assertEquals("About 28 calls this round, one every 6s or so", callsText(28, 180))
        assertEquals("One call this round", callsText(1, 30))
        assertEquals("166 combinations called", combosText(166))
        val three = plan.rounds[2]
        assertEquals("Combinations: all 7", pickText(three))
        // Switching one off from all keeps the other six; switching it on again is all, which is none.
        val six = toggledPick(three, "jab-cross-hook")
        assertEquals(6, six.size)
        assertEquals(emptyList<String>(), toggledPick(three.copy(pick = six), "jab-cross-hook"))
        assertEquals("Combinations: 2 of 7", pickText(three.copy(pick = listOf("jab-cross-hook", "jab-jab-cross"))))
        assertEquals(7, comboGroups(BoxingTheme.MIX).size)
        assertEquals(43, comboGroups(BoxingTheme.MIX).sumOf { it.second.size })
    }

    @Test fun `the WOD builder offers only the WOD modes`() {
        val building = stepOf().send(Msg.Wods.New)
        assertIgnored(building, Msg.Wods.SetMode(TimerMode.BOXING))
        assertIgnored(building, Msg.Wods.SetTimer(default))
    }
}

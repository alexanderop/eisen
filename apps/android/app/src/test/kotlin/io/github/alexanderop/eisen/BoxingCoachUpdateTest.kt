package io.github.alexanderop.eisen

import io.github.alexanderop.eisen.feature.timer.boxing.BOXING_TEMPLATES
import io.github.alexanderop.eisen.feature.timer.boxing.BoxingCues
import io.github.alexanderop.eisen.feature.timer.boxing.BoxingPlan
import io.github.alexanderop.eisen.feature.timer.boxing.BoxingRoom
import io.github.alexanderop.eisen.feature.timer.boxing.BoxingRound
import io.github.alexanderop.eisen.feature.timer.boxing.BoxingStance
import io.github.alexanderop.eisen.feature.timer.boxing.BoxingStyle
import io.github.alexanderop.eisen.feature.timer.boxing.BoxingTheme
import io.github.alexanderop.eisen.feature.timer.DEFAULT_TIMERS
import io.github.alexanderop.eisen.feature.timer.DEFAULT_TIMER_CALLS
import io.github.alexanderop.eisen.app.elm.Env
import io.github.alexanderop.eisen.feature.timer.boxing.OwnCombo
import io.github.alexanderop.eisen.feature.timer.TimerConfig
import io.github.alexanderop.eisen.feature.timer.TimerMode
import io.github.alexanderop.eisen.feature.timer.TimerSession
import io.github.alexanderop.eisen.feature.timer.boxing.boxingTotals
import io.github.alexanderop.eisen.app.state.freshState
import io.github.alexanderop.eisen.feature.timer.boxing.isCoachCue
import io.github.alexanderop.eisen.feature.timer.timerConfig
import io.github.alexanderop.eisen.feature.timer.timerCues
import io.github.alexanderop.eisen.app.elm.Cmd
import io.github.alexanderop.eisen.feature.timer.boxing.Drill
import io.github.alexanderop.eisen.app.elm.Msg
import io.github.alexanderop.eisen.feature.timer.boxing.NUMBERS_KEY
import io.github.alexanderop.eisen.app.elm.Sub
import io.github.alexanderop.eisen.app.elm.TimerState
import io.github.alexanderop.eisen.app.elm.UiEvent
import io.github.alexanderop.eisen.feature.timer.boxing.burnoutText
import io.github.alexanderop.eisen.feature.timer.boxing.comboGroups
import io.github.alexanderop.eisen.feature.timer.boxing.drillOf
import io.github.alexanderop.eisen.feature.timer.boxing.pickText
import io.github.alexanderop.eisen.feature.timer.boxing.punchesGap
import io.github.alexanderop.eisen.feature.timer.boxing.punchesText
import io.github.alexanderop.eisen.feature.timer.boxing.remindersText
import io.github.alexanderop.eisen.feature.timer.boxing.roundDetail
import io.github.alexanderop.eisen.feature.timer.boxing.stanceKey
import io.github.alexanderop.eisen.app.elm.subscriptions
import io.github.alexanderop.eisen.feature.timer.boxing.themeExample
import io.github.alexanderop.eisen.app.elm.timerEndsAt
import io.github.alexanderop.eisen.app.elm.timerNotice
import io.github.alexanderop.eisen.app.elm.update
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The coach's second round through `update`: stance, room, the lifter's own combinations, reminders and non-stop, and the punches of a run. */
class BoxingCoachUpdateTest {
    private val default = DEFAULT_TIMERS.getValue(TimerMode.BOXING)

    private fun setup(): Step = stepOf().send(Msg.Timer.Open(TimerMode.BOXING), now = STARTED_AT)

    private val Step.plan: BoxingPlan get() = model.timerSetup!!.config.boxing!!

    @Test fun `stance and room are set like the style and kept as the mode's last config`() {
        val southpaw = setup().send(Msg.Timer.SetStance(BoxingStance.SOUTHPAW))
        assertEquals(BoxingStance.SOUTHPAW, southpaw.plan.stance)
        assertEquals(southpaw.plan, southpaw.state.timerConfigs.getValue(TimerMode.BOXING).boxing)
        assertIgnored(southpaw, Msg.Timer.SetStance(BoxingStance.SOUTHPAW))
        val roomy = southpaw.send(Msg.Timer.SetRoom(BoxingRoom.ROOMY))
        assertEquals(BoxingRoom.ROOMY, roomy.plan.room)
        assertIgnored(roomy, Msg.Timer.SetRoom(BoxingRoom.ROOMY))
        // The room changes the calls: roomy calls fewer than tight in the same rounds.
        fun calls(step: Step) = timerCues(step.model.timerSetup!!.config, DEFAULT_TIMER_CALLS, 1).count { it.sound.startsWith("box-") && it.sound.endsWith("-n") }
        assertTrue(calls(roomy) < calls(roomy.send(Msg.Timer.SetRoom(BoxingRoom.TIGHT))))
        // A template keeps them, and the lifter's own combinations.
        val own = roomy.send(Msg.Timer.SaveOwn(null, listOf("1", "2", "3b")))
        val padded = own.send(Msg.Timer.ApplyTemplate(BOXING_TEMPLATES.indexOfFirst { it.name == "Pad work" }))
        assertEquals(List(6) { BoxingRound(BoxingTheme.BUILD, 6, 0) }, padded.plan.rounds)
        assertEquals(Triple(BoxingStance.SOUTHPAW, BoxingRoom.ROOMY, own.plan.own), Triple(padded.plan.stance, padded.plan.room, padded.plan.own))
        // The other timers have none.
        val tabata = stepOf().send(Msg.Timer.Open(TimerMode.TABATA))
        assertIgnored(tabata, Msg.Timer.SetStance(BoxingStance.SOUTHPAW))
        assertIgnored(tabata, Msg.Timer.SetRoom(BoxingRoom.TIGHT))
        assertIgnored(tabata, Msg.Timer.SaveOwn(null, listOf("1", "2")))
    }

    @Test fun `the combo builder saves, edits and deletes own combinations, with Undo`() {
        val saved = setup().send(Msg.Timer.SaveOwn(null, listOf("1", "2", "3b", "slip", "2")))
        assertEquals(listOf(OwnCombo("own-1", listOf("1", "2", "3b", "slip", "2"))), saved.plan.own)
        assertTrue(Cmd.Save(saved.state) in saved.cmds)
        // "Save" needs two moves; seven are too many; unknown moves are not saved.
        assertIgnored(saved, Msg.Timer.SaveOwn(null, listOf("1")))
        assertIgnored(saved, Msg.Timer.SaveOwn(null, List(7) { "1" }))
        assertIgnored(saved, Msg.Timer.SaveOwn(null, listOf("1", "kick")))
        val edited = saved.send(Msg.Timer.SaveOwn("own-1", listOf("1", "1", "2")))
        assertEquals(listOf("1", "1", "2"), edited.plan.own.single().moves)
        assertIgnored(edited, Msg.Timer.SaveOwn("own-1", listOf("1", "1", "2")))
        assertIgnored(edited, Msg.Timer.SaveOwn("own-9", listOf("1", "2")))
        // Made from the "My combos" tile of round 3: that round calls it.
        val fromTile = edited.send(Msg.Timer.SaveOwn(null, listOf("3", "2"), round = 2))
        assertEquals(BoxingTheme.OWN, fromTile.plan.rounds[2].theme)
        assertEquals(listOf("own-1", "own-2"), fromTile.plan.own.map { it.id })
        // Up to twelve.
        var full = fromTile
        repeat(12) { full = full.send(Msg.Timer.SaveOwn(null, listOf("1", "2"))) }
        assertEquals(12, full.plan.own.size)
        assertIgnored(full, Msg.Timer.SaveOwn(null, listOf("1", "2")))

        // Deleting both empties round 3's "My combos": it becomes a mix. Undo brings them back.
        val one = fromTile.send(Msg.Timer.DeleteOwn("own-2"))
        assertEquals(listOf("own-1"), one.plan.own.map { it.id })
        assertEquals(BoxingTheme.OWN, one.plan.rounds[2].theme)
        val snackbar = one.uiEvents.single() as UiEvent.Snackbar
        assertEquals("Undo", snackbar.actionLabel)
        assertEquals(Msg.Timer.UndoDeleteOwn, snackbar.onAction)
        val none = one.send(Msg.Timer.DeleteOwn("own-1"))
        assertEquals(BoxingTheme.MIX, none.plan.rounds[2].theme)
        val undone = none.send(Msg.Timer.UndoDeleteOwn)
        assertEquals(one.plan, undone.plan)
        assertEquals(undone.plan, undone.state.timerConfigs.getValue(TimerMode.BOXING).boxing)
        assertIgnored(undone, Msg.Timer.UndoDeleteOwn)
        assertIgnored(none, Msg.Timer.DeleteOwn("own-1"))
        // An Undo after another edit takes nothing back.
        val later = none.send(Msg.Timer.SetStyle(BoxingStyle.NUMBERS)).send(Msg.Timer.UndoDeleteOwn)
        assertEquals(emptyList<OwnCombo>(), later.plan.own)
        assertEquals(BoxingStyle.NUMBERS, later.plan.style)
    }

    @Test fun `an own combination is heard move by move, with the offsets of the run`() {
        val saved = setup().send(Msg.Timer.SaveOwn(null, listOf("1", "2", "3b", "slip", "2")))
        val played = saved.send(Msg.Timer.PlayCombo("own-1")).cmds.single() as Cmd.PlayTimerSounds
        assertEquals(listOf(0L, 950L, 2000L, 3500L, 4250L), played.sounds.map { it.at })
        assertEquals(listOf("box-move-1-n", "box-move-2-n", "box-move-3b-n", "box-move-slip-n", "box-move-2-n"), played.sounds.map { it.sound })
        assertEquals("beep", played.fallback)
        // The builder's Listen says the moves so far, in the plan's style.
        val numbers = saved.send(Msg.Timer.SetStyle(BoxingStyle.NUMBERS))
        val listened = numbers.send(Msg.Timer.ListenMoves(listOf("4", "back"))).cmds.single() as Cmd.PlayTimerSounds
        assertEquals(listOf(0L to "box-move-4-d", 1100L to "box-move-back-d"), listened.sounds.map { it.at to it.sound })
        assertEquals(Cmd.PlayTimerSound("box-move-6-d", "beep"), numbers.send(Msg.Timer.ListenMoves(listOf("6"))).cmds.single())
        assertIgnored(numbers, Msg.Timer.ListenMoves(emptyList()))
        assertIgnored(numbers, Msg.Timer.ListenMoves(listOf("kick")))
        // Listen in a "My combos" round plays them in turn.
        val round = numbers.send(Msg.Timer.EditRound(0, BoxingRound(BoxingTheme.OWN, 6, 0)))
        val heard = round.send(Msg.Timer.Listen(0))
        assertEquals("own-1", heard.model.timerSetup!!.heard)
        assertTrue(heard.cmds.single() is Cmd.PlayTimerSounds)
    }

    @Test fun `reminders and the non-stop finish are set per round and come out of a run`() {
        val round = BoxingRound(BoxingTheme.MIX, 4, 0, cues = BoxingCues.MANY, burnout = 30)
        val edited = setup().send(Msg.Timer.EditRound(0, round))
        assertEquals(round, edited.plan.rounds[0])
        // A freestyle round keeps its reminders but has no non-stop finish.
        val free = edited.send(Msg.Timer.EditRound(0, round.copy(theme = BoxingTheme.FREESTYLE)))
        assertEquals(BoxingRound(BoxingTheme.FREESTYLE, 4, 0, cues = BoxingCues.MANY), free.plan.rounds[0])
        // A 60-second round run to its end: the session keeps its combinations and punches.
        val config = timerConfig(TimerConfig(TimerMode.BOXING, 60, 10, 2, BoxingPlan(listOf(round, round.copy(burnout = 15)), BoxingStyle.NAMES)))
        val opened = setup().send(Msg.Timer.Edit(config), now = STARTED_AT)
        val (model, cmds) = update(Msg.Timer.Start, opened.model, Env(STARTED_AT, newSeed = { 11 }) { "b1" })
        val run = Step(model, cmds)
        val timed = run.model.timer as TimerState.Running
        val cues = timerCues(config, DEFAULT_TIMER_CALLS, 11)
        // The screen gets a tick of its own for a reminder.
        val reminder = cues.first { isCoachCue(it.sound) }
        assertTrue(Sub.TimerCue(STARTED_AT + reminder.at) in subscriptions(run.model.copy(now = STARTED_AT + reminder.at - 100)))
        // The notification says when the round goes non-stop.
        assertEquals("Round 1 of 2 · Mix", timerNotice(timed.run, STARTED_AT + 35_000, freshState()).text)
        assertEquals("Round 1 of 2 · non-stop", timerNotice(timed.run, STARTED_AT + 45_000, freshState()).text)
        val tick = run.send(Msg.Timer.Tick, now = STARTED_AT + 39_500).send(Msg.Timer.Tick, now = STARTED_AT + 40_000)
        assertTrue(tick.cmds.any { it is Cmd.ShowTimer && it.notice.text == "Round 1 of 2 · non-stop" })

        val over = run.send(Msg.Timer.Tick, now = timerEndsAt(timed.run) + 500)
        val finished = over.model.timer as TimerState.Finished
        val (combos, punches) = boxingTotals(cues, emptyList(), Long.MAX_VALUE)
        assertEquals(combos, finished.session.combos)
        assertEquals(punches, finished.session.punches)
        assertEquals(combos, finished.combos)
        assertEquals(finished.session, over.state.timerSessions.first())
        // Ended early, it counts up to where it ended.
        val early = run.send(Msg.Timer.End, now = STARTED_AT + 40_000).send(Msg.Timer.SaveAndEnd, now = STARTED_AT + 40_000).model.timer as TimerState.Finished
        assertEquals(boxingTotals(cues, emptyList(), 40_000).second, early.session.punches)
        // The other timers count none.
        val tabata = stepOf().send(Msg.Timer.Open(TimerMode.TABATA)).send(Msg.Timer.Start, now = STARTED_AT)
            .send(Msg.Timer.SaveAndEnd, now = STARTED_AT + 15_000).model.timer as TimerState.Finished
        assertNull(tabata.session.punches)
        assertNull(tabata.session.combos)
    }

    @Test fun `the result says how the punches compare with the boxing run before`() {
        fun session(id: String, punches: Int?, mode: TimerMode = TimerMode.BOXING) =
            TimerSession(id, "Boxing", if (mode == TimerMode.BOXING) default else DEFAULT_TIMERS.getValue(mode), 0, 1, 60, 1, true, punches = punches)
        val now = session("s3", 480)
        assertEquals("62 more than last time", punchesGap(now, listOf(now, session("t", null, TimerMode.TABATA), session("s2", 418), session("s1", 999))))
        assertEquals("40 fewer than last time", punchesGap(now, listOf(now, session("s2", 520))))
        assertEquals("Same as last time", punchesGap(now, listOf(now, session("s2", 480))))
        // Nothing to compare with: the run before did not count them, or there is none.
        assertNull(punchesGap(now, listOf(now, session("s2", null), session("s1", 300))))
        assertNull(punchesGap(now, listOf(now)))
        assertNull(punchesGap(session("s4", null), listOf(session("s4", null), session("s2", 300))))
        assertEquals("About 480 punches", punchesText(480))
    }

    @Test fun `the round sheet and the setup say what the new options do`() {
        assertEquals("1 jab · 2 cross · 3 hook · 4 rear hook · 5 and 6 uppercut · B to the body", NUMBERS_KEY)
        assertEquals("Jab and lead hook: left hand. Cross: right hand.", stanceKey(BoxingStance.ORTHODOX))
        assertEquals("Jab and lead hook: right hand. Cross: left hand.", stanceKey(BoxingStance.SOUTHPAW))
        assertEquals("Jab", themeExample(BoxingTheme.WARMUP, BoxingStyle.NAMES))
        assertEquals("1", themeExample(BoxingTheme.WARMUP, BoxingStyle.NUMBERS))
        assertEquals("Jab → Jab · Cross → …", themeExample(BoxingTheme.BUILD, BoxingStyle.NAMES))
        assertEquals("1 → 1 – 2 → …", themeExample(BoxingTheme.BUILD, BoxingStyle.NUMBERS))
        assertEquals("Make one first", themeExample(BoxingTheme.OWN, BoxingStyle.NAMES))
        val own = listOf(OwnCombo("own-1", listOf("1", "2", "3b")))
        assertEquals("1 – 2 – 3B", themeExample(BoxingTheme.OWN, BoxingStyle.NUMBERS, own))
        assertEquals("Between combinations the coach reminds you: Move! Hands up! Breathe!", remindersText(BoxingTheme.MIX))
        assertEquals("A warm-up always has them", remindersText(BoxingTheme.WARMUP))
        assertEquals("The only thing the coach says in a freestyle round", remindersText(BoxingTheme.FREESTYLE))
        assertEquals("The end of the round: short combinations back to back", burnoutText(BoxingRound(BoxingTheme.MIX, 6, 0), 180))
        assertEquals("The last 15 seconds: short combinations back to back", burnoutText(BoxingRound(BoxingTheme.MIX, 6, 0, burnout = 15), 180))
        assertEquals("Needs a round of at least 1:00", burnoutText(BoxingRound(BoxingTheme.MIX, 6, 0, burnout = 30), 45))
        assertEquals("Needs a round of at least 0:30", burnoutText(BoxingRound(BoxingTheme.MIX, 6, 0, burnout = 15), 25))
        assertEquals("punch by punch", roundDetail(BoxingRound(BoxingTheme.BUILD, 6, 0)))
        assertEquals("punch by punch · reminders · 15s non-stop", roundDetail(BoxingRound(BoxingTheme.BUILD, 6, 0, cues = BoxingCues.SOME, burnout = 15)))
        assertEquals("no calls · reminders", roundDetail(BoxingRound(BoxingTheme.FREESTYLE, 6, 0, cues = BoxingCues.MANY)))
        assertEquals("Easy", roundDetail(BoxingRound(BoxingTheme.WARMUP, 8, 0)))
        assertEquals(Drill.ALL, drillOf(BoxingRound(BoxingTheme.BUILD, 6, 0)))
        assertEquals(Drill.NONE, drillOf(BoxingRound(BoxingTheme.WARMUP, 6, 0)))
        assertEquals(Drill.ALL, drillOf(BoxingRound(BoxingTheme.OWN, 6, 0), own))
        // The mix lists the lifter's own last; "My combos" lists only them.
        assertEquals(BoxingTheme.OWN, comboGroups(BoxingTheme.MIX, own).last().first)
        assertEquals(44, comboGroups(BoxingTheme.MIX, own).sumOf { it.second.size })
        assertEquals(listOf("own-1"), comboGroups(BoxingTheme.OWN, own).single().second.map { it.id })
        assertEquals("Combinations: all 44", pickText(BoxingRound(BoxingTheme.MIX, 6, 0), own))
    }
}

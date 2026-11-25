package io.github.alexanderop.eisen

import io.github.alexanderop.eisen.app.elm.Builder
import io.github.alexanderop.eisen.app.elm.BuilderEvent
import io.github.alexanderop.eisen.app.elm.Cmd
import io.github.alexanderop.eisen.app.elm.Flags
import io.github.alexanderop.eisen.app.elm.Msg
import io.github.alexanderop.eisen.app.elm.Notifications
import io.github.alexanderop.eisen.app.elm.WodDraft
import io.github.alexanderop.eisen.app.elm.builderAfter
import io.github.alexanderop.eisen.app.elm.decodeDrafts
import io.github.alexanderop.eisen.app.elm.draft
import io.github.alexanderop.eisen.app.elm.draftFile
import io.github.alexanderop.eisen.app.elm.encodeDrafts
import io.github.alexanderop.eisen.app.elm.init
import io.github.alexanderop.eisen.app.elm.pending
import io.github.alexanderop.eisen.app.state.StateJson
import io.github.alexanderop.eisen.app.state.freshState
import io.github.alexanderop.eisen.feature.timer.DEFAULT_TIMERS
import io.github.alexanderop.eisen.feature.timer.TimerConfig
import io.github.alexanderop.eisen.feature.timer.TimerMode
import io.github.alexanderop.eisen.feature.timer.wod.Wod
import io.github.alexanderop.eisen.feature.timer.wod.WodItem
import io.github.alexanderop.eisen.feature.timer.wod.WodUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The WOD builder's states (`Builder` in `Model.kt`): one test per transition of [builderAfter], the draft
 * file on every path, and what build 18 wrote.
 */
class WodBuilderTest {
    private val forTime = DEFAULT_TIMERS.getValue(TimerMode.FOR_TIME)
    private fun draftOf(id: String, name: String = ""): WodDraft {
        val wod = Wod(id, name, forTime, 1, emptyList(), emptyList())
        return WodDraft(wod.copy(items = listOf(WodItem("burpee", 10, WodUnit.REPS, 0.0))), wod)
    }

    private val a = draftOf("a", "Kept A")
    private val b = draftOf("b", "Kept B")
    private val d = draftOf("d")

    @Test fun `a closed builder opens a draft over the kept ones, and an open one opens nothing`() {
        assertEquals(Builder.Editing(d, listOf(a)), builderAfter(Builder.Closed(listOf(a)), BuilderEvent.Open(d)))
        assertEquals(Builder.Editing(d), builderAfter(Builder.Closed(), BuilderEvent.Open(d)))
        assertEquals(Builder.Editing(a), builderAfter(Builder.Editing(a), BuilderEvent.Open(d)))
        assertEquals(Builder.Asking(a), builderAfter(Builder.Asking(a), BuilderEvent.Open(d)))
    }

    @Test fun `Continue opens the newest kept draft, Discard drops it, both only while closed with one kept`() {
        assertEquals(Builder.Editing(a, listOf(b)), builderAfter(Builder.Closed(listOf(a, b)), BuilderEvent.Continue))
        assertEquals(Builder.Closed(listOf(b)), builderAfter(Builder.Closed(listOf(a, b)), BuilderEvent.DropKept))
        for (event in listOf(BuilderEvent.Continue, BuilderEvent.DropKept)) {
            assertEquals(Builder.Closed(), builderAfter(Builder.Closed(), event))
            assertEquals(Builder.Editing(d, listOf(a)), builderAfter(Builder.Editing(d, listOf(a)), event))
        }
    }

    @Test fun `leaving with changes asks, Keep editing goes back`() {
        assertEquals(Builder.Asking(d, listOf(a)), builderAfter(Builder.Editing(d, listOf(a)), BuilderEvent.Ask))
        assertEquals(Builder.Editing(d, listOf(a)), builderAfter(Builder.Asking(d, listOf(a)), BuilderEvent.KeepEditing))
        assertEquals(Builder.Closed(listOf(a)), builderAfter(Builder.Closed(listOf(a)), BuilderEvent.Ask))
        assertEquals(Builder.Editing(d), builderAfter(Builder.Editing(d), BuilderEvent.KeepEditing))
    }

    @Test fun `closing drops the open draft and offers the kept ones again`() {
        assertEquals(Builder.Closed(listOf(a)), builderAfter(Builder.Editing(d, listOf(a)), BuilderEvent.Close))
        assertEquals(Builder.Closed(listOf(a)), builderAfter(Builder.Asking(d, listOf(a)), BuilderEvent.Close))
        assertEquals(Builder.Closed(listOf(a)), builderAfter(Builder.Closed(listOf(a)), BuilderEvent.Close))
    }

    /** The model after the process started with [file] as its draft file. */
    private fun restarted(file: List<WodDraft>): Step =
        Step(init(Flags(freshState(), savedRest = null, healthEnabled = false, notifications = Notifications(true, true), savedDrafts = file)).first)

    /** What the draft file holds after [step]: what its last SaveDraft wrote, or [before] when none came. */
    private fun fileAfter(step: Step, before: List<WodDraft>): List<WodDraft> = step.cmds.filterIsInstance<Cmd.SaveDraft>().lastOrNull()?.drafts ?: before

    @Test fun `New over a kept draft keeps both in the file, so the kept one survives the end of the process`() {
        val kept = restarted(listOf(a))
        assertEquals(a, kept.model.wods.pending)
        val opened = kept.send(Msg.Wods.New, id = "d1")
        val file = fileAfter(opened, listOf(a))
        assertEquals(listOf(opened.model.wods.draft, a), file)
        // The process ends with the builder open: both come back, the newer one offered first.
        val again = restarted(file)
        assertEquals(file.first(), again.model.wods.pending)
        assertEquals(a, again.send(Msg.Wods.DiscardDraft).model.wods.pending)
        // Without the process ending, closing the new builder offers the kept one again.
        assertEquals(a, opened.send(Msg.Wods.Discard).model.wods.pending)
    }

    @Test fun `the draft file holds the open draft and every kept one after every Msg`() {
        val msgs = listOf(
            Msg.Wods.New, Msg.Wods.Edit("own"), Msg.Wods.Copy("wod-fran"), Msg.Wods.ContinueDraft, Msg.Wods.DiscardDraft,
            Msg.Wods.AddItem("burpee"), Msg.Wods.Rename("Engine"), Msg.Wods.SetMode(TimerMode.AMRAP), Msg.Wods.Leave, Msg.Wods.KeepEditing,
            Msg.Wods.Discard, Msg.Wods.Save, Msg.Wods.Delete("own"), Msg.Nav.Back, Msg.Nav.SelectTab(io.github.alexanderop.eisen.app.elm.Tab.TIMER),
        )
        var opened = 0
        for (seed in 1L..60L) {
            val random = java.util.Random(seed)
            var file = listOf(a, b)
            var step = restarted(file).send(Msg.Nav.SelectTab(io.github.alexanderop.eisen.app.elm.Tab.TIMER))
            repeat(60) { turn ->
                val msg = msgs[random.nextInt(msgs.size)]
                val before = step
                step = step.send(msg, id = "n$seed-$turn")
                if (before.model.wods.draft == null && step.model.wods.draft != null) opened++
                file = fileAfter(step, file)
                val where = "seed $seed, turn $turn, $msg"
                assertEquals(where, draftFile(step.model.wods.builder), file)
                assertEquals(where, listOfNotNull(step.model.wods.draft) + step.model.wods.builder.kept, file)
                // A process that ends now offers each of them again.
                assertEquals(where, Builder.Closed(file), restarted(file).model.wods.builder)
            }
        }
        assertTrue("only $opened builders opened", opened >= 100)
    }

    @Test fun `one draft is written as build 18 wrote it, more go under it, and the file of build 18 still loads`() {
        val one = draftOf("x", "Burner").copy(kept = mapOf(TimerMode.AMRAP to TimerConfig(TimerMode.AMRAP, 720, 0, 1)), keptRounds = 3, keptScheme = listOf(21, 15, 9))
        assertEquals(StateJson.encodeToString(WodDraft.serializer(), one), encodeDrafts(listOf(one)))
        assertEquals(listOf(one, a, b), decodeDrafts(encodeDrafts(listOf(one, a, b))!!))
        assertNull(encodeDrafts(emptyList()))
        // Build 18's wod-draft.json: one draft, no list.
        val build18 = """{"wod":{"id":"d1","name":"Burner","timer":{"mode":"forTime","work":600,"rest":0,"rounds":1},"rounds":3,"scheme":[],""" +
            """"items":[{"exerciseId":"burpee","amount":10,"unit":"reps","load":0.0}]},"start":{"id":"d1","name":"","timer":{"mode":"forTime",""" +
            """"work":600,"rest":0,"rounds":1},"rounds":1,"scheme":[],"items":[]},"kept":{"amrap":{"mode":"amrap","work":720,"rest":0,"rounds":1}},""" +
            """"keptRounds":3,"keptScheme":[21,15,9]}"""
        val loaded = decodeDrafts(build18).single()
        assertEquals("Burner", loaded.wod.name)
        assertEquals(listOf(WodItem("burpee", 10, WodUnit.REPS, 0.0)), loaded.wod.items)
        assertEquals(TimerConfig(TimerMode.AMRAP, 720, 0, 1), loaded.kept[TimerMode.AMRAP])
        assertEquals(listOf(21, 15, 9), loaded.keptScheme)
        assertEquals(build18, encodeDrafts(listOf(loaded)))
    }
}

package io.github.alexanderop.eisen

import io.github.alexanderop.eisen.app.elm.Cmd
import io.github.alexanderop.eisen.app.elm.Msg
import io.github.alexanderop.eisen.app.elm.SchemeChoice
import io.github.alexanderop.eisen.app.elm.WodDraft
import io.github.alexanderop.eisen.app.elm.customText
import io.github.alexanderop.eisen.app.elm.draft
import io.github.alexanderop.eisen.app.elm.schemeChoice
import io.github.alexanderop.eisen.feature.timer.TimerMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The reps per round of a For Time WOD in the builder (`WodDraft` in `Model.kt`): the lit chip is
 * [schemeChoice] of the scheme, only the custom field's text is kept. One test per transition, then the
 * bugs of the view's own `custom` flag, which lit a chip that was not what Save saved.
 */
class WodSchemeTest {
    private val builder: Step = stepOf().send(Msg.Wods.New, id = "wod-1").send(Msg.Wods.AddItem("burpee"))
    private val Step.draft: WodDraft get() = model.wods.draft!!
    private val Step.choice: SchemeChoice get() = schemeChoice(draft.wod.scheme)

    @Test fun `the chip follows the scheme`() {
        assertEquals(SchemeChoice.Same, schemeChoice(emptyList()))
        assertEquals(SchemeChoice.Preset(listOf(21, 15, 9)), schemeChoice(listOf(21, 15, 9)))
        assertEquals(SchemeChoice.Custom(listOf(30, 20, 10)), schemeChoice(listOf(30, 20, 10)))
    }

    @Test fun `a preset chip sets its scheme and Same each round takes it away`() {
        val fran = builder.send(Msg.Wods.SetScheme(listOf(21, 15, 9)))
        assertEquals(SchemeChoice.Preset(listOf(21, 15, 9)), fran.choice)
        assertNull(customText(fran.draft))
        assertEquals(SchemeChoice.Same, fran.send(Msg.Wods.SetScheme(emptyList())).choice)
        assertIgnored(fran, Msg.Wods.SetScheme(listOf(21, 15, 9)))
    }

    @Test fun `Custom opens the field, typing sets the scheme to the numbers in it`() {
        val custom = builder.send(Msg.Wods.ChooseCustom)
        assertEquals("", customText(custom.draft))
        val typed = custom.send(Msg.Wods.TypeScheme("30-20-"))
        assertEquals("30-20-", customText(typed.draft))
        assertEquals(listOf(30, 20), typed.draft.wod.scheme)
        assertEquals(SchemeChoice.Custom(listOf(30, 20)), typed.choice)
        // Already open: the chip again changes nothing.
        assertIgnored(typed, Msg.Wods.ChooseCustom)
    }

    @Test fun `the field is cut to 48 characters`() {
        val long = builder.send(Msg.Wods.ChooseCustom).send(Msg.Wods.TypeScheme("1-".repeat(30)))
        assertEquals(48, customText(long.draft)!!.length)
    }

    @Test fun `typing needs the field open`() {
        assertIgnored(builder, Msg.Wods.TypeScheme("30-20-10"))
    }

    @Test fun `a chip closes the field, and Custom brings back what it held`() {
        val typed = builder.send(Msg.Wods.ChooseCustom).send(Msg.Wods.TypeScheme("30-20-10"))
        val fran = typed.send(Msg.Wods.SetScheme(listOf(21, 15, 9)))
        assertNull(customText(fran.draft))
        val back = fran.send(Msg.Wods.ChooseCustom)
        assertEquals("30-20-10", customText(back.draft))
        assertEquals(listOf(30, 20, 10), back.draft.wod.scheme)
    }

    @Test fun `a custom scheme shows its field, also after the type changed and came back`() {
        val typed = builder.send(Msg.Wods.ChooseCustom).send(Msg.Wods.TypeScheme("30 20 10"))
        val back = typed.send(Msg.Wods.SetMode(TimerMode.AMRAP)).send(Msg.Wods.SetMode(TimerMode.FOR_TIME))
        assertNull(back.draft.typed)
        assertEquals("30-20-10", customText(back.draft))
    }

    @Test fun `what is typed is not written to the draft file`() {
        val typed = builder.send(Msg.Wods.ChooseCustom).send(Msg.Wods.TypeScheme("30-20-"))
        val drafts = typed.cmds.filterIsInstance<Cmd.SaveDraft>().single().drafts
        assertEquals(listOf(30, 20), drafts.single().wod.scheme)
        assertNull(drafts.single().typed)
        // A keystroke that changes no number writes no file.
        assertEquals(emptyList<Cmd>(), typed.send(Msg.Wods.TypeScheme("30-20")).cmds)
    }

    // The bugs of the view's `custom` flag.

    @Test fun `bug - Custom with nothing typed does not light Custom over an empty scheme`() {
        // Old: "Custom" lit while the scheme was empty; saved and opened again it showed "Same each round".
        val custom = builder.send(Msg.Wods.ChooseCustom)
        assertEquals(SchemeChoice.Same, custom.choice)
        val reopened = custom.send(Msg.Wods.Save).send(Msg.Wods.Edit("wod-1"))
        assertEquals(SchemeChoice.Same, reopened.choice)
    }

    @Test fun `bug - a typed preset lights the preset, the same as when the WOD is opened again`() {
        // Old: typing "21-15-9" left "Custom" lit; opened again the WOD showed the "21-15-9" chip.
        val typed = builder.send(Msg.Wods.ChooseCustom).send(Msg.Wods.TypeScheme("21-15-9"))
        assertEquals(SchemeChoice.Preset(listOf(21, 15, 9)), typed.choice)
        assertEquals("21-15-9", customText(typed.draft))
        val reopened = typed.send(Msg.Wods.Save).send(Msg.Wods.Edit("wod-1"))
        assertEquals(SchemeChoice.Preset(listOf(21, 15, 9)), reopened.choice)
        assertNull(customText(reopened.draft))
    }
}

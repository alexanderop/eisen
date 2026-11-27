package io.github.alexanderop.eisen

import io.github.alexanderop.eisen.app.elm.Cmd
import io.github.alexanderop.eisen.app.elm.Env
import io.github.alexanderop.eisen.app.elm.Model
import io.github.alexanderop.eisen.app.elm.Msg
import io.github.alexanderop.eisen.app.elm.UiEvent
import io.github.alexanderop.eisen.app.elm.update
import io.github.alexanderop.eisen.app.state.AppState
import io.github.alexanderop.eisen.app.state.freshState
import io.github.alexanderop.eisen.core.WeightUnit
import io.github.alexanderop.eisen.feature.food.FoodEntry
import io.github.alexanderop.eisen.feature.food.FoodGoal
import io.github.alexanderop.eisen.feature.food.FoodUnit
import io.github.alexanderop.eisen.feature.food.WeightEntry
import io.github.alexanderop.eisen.feature.food.dayDate
import io.github.alexanderop.eisen.feature.food.dayMarkdown
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneOffset
import java.time.ZonedDateTime

/** A day of food as Markdown, and the two messages that copy and share it. */
class DayTextTest {
    private val day = "2026-11-27"
    private fun at(clock: String) = ZonedDateTime.parse("${day}T$clock:00Z").toInstant().toEpochMilli()
    private val skyr = FoodEntry("e1", "off:1", "Skyr Natur", 450.0, FoodUnit.G, kcal = 270, protein = 49.5, carbs = 18.0, fat = 0.9, at = at("07:30"), day = day)
    private val milk = FoodEntry("e2", "off:2", "Milch 1,5 %", 300.0, FoodUnit.ML, kcal = 141, protein = 10.2, carbs = 14.4, fat = 4.5, at = at("07:35"), day = day)
    private val pizza = FoodEntry("e3", null, "Pizza", null, kcal = 850, protein = 40.0, at = at("19:10"), day = day)
    private val goal = FoodGoal(kcal = 2400, protein = 160)

    @Test fun `a day is a heading, the totals against the goal, a table with a total, and the weight`() {
        val text = dayMarkdown(day, listOf(skyr, milk, pizza), goal, ZoneOffset.UTC, WeightEntry(day, 82.4, at("06:00")), WeightUnit.KG)
        assertEquals(
            """
            ## Food · Fri, Nov 27, 2026

            **1,261 kcal** of 2,400 · Protein 99.7 of 160 g · Carbs 32.4 g · Fat 5.4 g

            | Time | Food | Amount | kcal | Protein | Carbs | Fat |
            |---|---|--:|--:|--:|--:|--:|
            | 07:30 | Skyr Natur | 450 g | 270 | 49.5 g | 18 g | 0.9 g |
            | 07:35 | Milch 1,5 % | 300 ml | 141 | 10.2 g | 14.4 g | 4.5 g |
            | 19:10 | Pizza |  | 850 | 40 g |  |  |
            |  | **Total** |  | **1,261** | **99.7 g** | **32.4 g** | **5.4 g** |

            Weight: 82.4 kg

            """.trimIndent(),
            text,
        )
    }

    @Test fun `without a goal and a weight only what was eaten is said, and the weight is in the lifter's unit`() {
        val plain = dayMarkdown(day, listOf(skyr), FoodGoal(), ZoneOffset.UTC)
        assertTrue(plain, plain.contains("**270 kcal** · Protein 49.5 g · Carbs 18 g · Fat 0.9 g"))
        assertTrue(plain, !plain.contains("Weight"))
        val pounds = dayMarkdown(day, listOf(skyr), FoodGoal(), ZoneOffset.UTC, WeightEntry(day, 80.0, at("06:00")), WeightUnit.LB)
        assertTrue(pounds, pounds.trimEnd().endsWith("Weight: 176.4 lb"))
    }

    @Test fun `a name cannot break the table`() {
        val odd = skyr.copy(name = "Rice | beans\nand \\ more")
        val row = dayMarkdown(day, listOf(odd), goal, ZoneOffset.UTC).lines().first { it.startsWith("| 07:30") }
        assertEquals("| 07:30 | Rice \\| beans and \\\\ more | 450 g | 270 | 49.5 g | 18 g | 0.9 g |", row)
    }

    @Test fun `the date is the date, also for today`() {
        assertEquals("Fri, Nov 27, 2026", dayDate(day))
        assertEquals("not a day", dayDate("not a day"))
    }

    private val noon = at("12:00")
    private fun model(state: AppState) = update(Msg.Food.Open, Model(state, now = noon), Env(noon, zone = ZoneOffset.UTC) { "n" }).first
    private fun send(msg: Msg, model: Model) = update(msg, model, Env(noon, zone = ZoneOffset.UTC) { "n" })

    @Test fun `Copy puts the day on the clipboard and offers to share it, and changes nothing`() {
        val open = model(freshState(WeightUnit.KG).copy(foodLog = listOf(skyr, milk), foodGoal = goal))
        val (after, cmds) = send(Msg.Food.CopyDay, open)
        assertSame(open, after)
        val text = dayMarkdown(day, listOf(skyr, milk), goal, ZoneOffset.UTC)
        assertEquals(Cmd.Ui(UiEvent.CopyText(text)), cmds[0])
        val snackbar = (cmds[1] as Cmd.Ui).event as UiEvent.Snackbar
        assertEquals("Copied as Markdown", snackbar.text)
        assertEquals("Share", snackbar.actionLabel)
        assertEquals(Msg.Food.ShareDay, snackbar.onAction)
        val (shared, share) = send(Msg.Food.ShareDay, open)
        assertSame(open, shared)
        assertEquals(listOf(Cmd.Ui(UiEvent.ShareText(text))), share)
    }

    @Test fun `an empty day copies nothing and says so, and away from the Food screen nothing happens`() {
        val empty = model(freshState(WeightUnit.KG))
        val (after, cmds) = send(Msg.Food.CopyDay, empty)
        assertSame(empty, after)
        assertEquals("Nothing is logged on this day.", ((cmds.single() as Cmd.Ui).event as UiEvent.Snackbar).text)
        val home = Model(freshState(WeightUnit.KG).copy(foodLog = listOf(skyr)), now = noon)
        assertEquals(home to emptyList<Cmd>(), send(Msg.Food.CopyDay, home))
        assertEquals(home to emptyList<Cmd>(), send(Msg.Food.ShareDay, home))
    }
}

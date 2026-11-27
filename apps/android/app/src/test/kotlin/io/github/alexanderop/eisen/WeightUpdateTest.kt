package io.github.alexanderop.eisen

import io.github.alexanderop.eisen.app.elm.CheckIn
import io.github.alexanderop.eisen.app.elm.CheckInOffer
import io.github.alexanderop.eisen.app.elm.Cmd
import io.github.alexanderop.eisen.app.elm.Flags
import io.github.alexanderop.eisen.app.elm.FoodSheet
import io.github.alexanderop.eisen.app.elm.HealthJob
import io.github.alexanderop.eisen.app.elm.HealthModel
import io.github.alexanderop.eisen.app.elm.Model
import io.github.alexanderop.eisen.app.elm.Msg
import io.github.alexanderop.eisen.app.elm.Notifications
import io.github.alexanderop.eisen.app.elm.PermissionCheck
import io.github.alexanderop.eisen.app.elm.Tab
import io.github.alexanderop.eisen.app.elm.UiEvent
import io.github.alexanderop.eisen.app.elm.WeightSheet
import io.github.alexanderop.eisen.app.elm.checkIn
import io.github.alexanderop.eisen.app.elm.goalWeightView
import io.github.alexanderop.eisen.app.elm.init
import io.github.alexanderop.eisen.app.elm.weightCard
import io.github.alexanderop.eisen.app.elm.Direction
import io.github.alexanderop.eisen.app.state.AppState
import io.github.alexanderop.eisen.app.state.freshState
import io.github.alexanderop.eisen.core.WeightUnit
import io.github.alexanderop.eisen.feature.food.CheckInAnswer
import io.github.alexanderop.eisen.feature.food.FoodEntry
import io.github.alexanderop.eisen.feature.food.FoodGoal
import io.github.alexanderop.eisen.feature.food.GoalDirection
import io.github.alexanderop.eisen.feature.food.MAX_BODY_KG
import io.github.alexanderop.eisen.feature.food.MIN_BODY_KG
import io.github.alexanderop.eisen.feature.food.WeightEntry
import io.github.alexanderop.eisen.feature.food.WeightGoal
import io.github.alexanderop.eisen.feature.food.plusDays
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

/** Every transition of the weight sheet and of the weekly check-in, through `update` alone. */
class WeightUpdateTest {
    /** Thursday 2026-11-26; its week started on Monday the 23rd, last week ended on Sunday the 22nd. */
    private val thursday = "2026-11-26"

    private fun at(day: String, hour: Int = 10, minute: Int = 0): Long = LocalDate.parse(day).atTime(hour, minute).toInstant(ZoneOffset.UTC).toEpochMilli()

    private val now = at(thursday)

    private fun stepAt(state: AppState, time: Long = now) = Step(Model(state, now = time))

    private fun Step.at(msg: Msg, time: Long = now, id: String = "w") = send(msg, now = time, id = id)

    /** Nothing changed and nothing is asked for, at the time of these tests. */
    private fun assertIgnored(before: Step, msg: Msg) {
        val after = before.at(msg)
        assertSame(before.model, after.model)
        assertTrue("expected no Cmds for $msg, got ${after.cmds}", after.cmds.isEmpty())
    }

    private fun eaten(day: String, kcal: Int, id: String = "e-$day") = FoodEntry(id, name = "Food", kcal = kcal, at = at(day, 12), day = day)

    /**
     * Three weeks before this Monday: 80 kg every day from Nov 1 to Nov 22, 2,500 kcal on each of the 21 days after the
     * first weight, a goal of 2,400 kcal and 160 g protein, and to lose 0.5 kg a week. As of Sunday the 22nd: the trend
     * stayed at 80, so what the lifter burns is what was eaten, 2,500; the target is 2,500 − 0.5 × 7,700 / 7 = 1,950.
     */
    private val threeWeeks: AppState = freshState(WeightUnit.KG).copy(
        weights = (0..21).map { WeightEntry(plusDays("2026-11-01", it.toLong()), 80.0, at(plusDays("2026-11-01", it.toLong()), 7)) },
        foodLog = (1..21).map { eaten(plusDays("2026-11-01", it.toLong()), 2_500) },
        foodGoal = FoodGoal(2_400, 160),
        weightGoal = WeightGoal(GoalDirection.LOSE, 0.5, adjust = true),
    )

    private val offer = CheckInOffer(intake = 2_500, loggedDays = 7, trendFrom = 80.0, trendTo = 80.0, expenditure = 2_500, target = 1_950, was = 2_400)

    // The sheet

    @Test fun `Log weight opens today at the last weight, or 80 kg before the first one`() {
        val fresh = stepAt(freshState(WeightUnit.KG)).at(Msg.Weight.Open())
        assertEquals(WeightSheet(thursday, 80.0), fresh.model.weight.sheet)
        val logged = stepAt(threeWeeks).at(Msg.Weight.Open())
        assertEquals(WeightSheet(thursday, 80.0), logged.model.weight.sheet)
        // In pounds: 80 kg is 176.4 lb.
        val pounds = stepAt(threeWeeks.copy(settings = threeWeeks.settings.copy(unit = WeightUnit.LB))).at(Msg.Weight.Open())
        assertEquals(WeightSheet(thursday, 176.4), pounds.model.weight.sheet)
        // An earlier day opens at its own weight; a day to come does not open.
        val earlier = stepAt(threeWeeks.copy(weights = threeWeeks.weights + WeightEntry("2026-11-24", 79.5, 0))).at(Msg.Weight.Open("2026-11-24"))
        assertEquals(WeightSheet("2026-11-24", 79.5), earlier.model.weight.sheet)
        assertIgnored(stepAt(threeWeeks), Msg.Weight.Open("2026-11-27"))
        assertIgnored(logged, Msg.Weight.Open())
    }

    @Test fun `the number is kept in its limits, to a tenth, and a number that is none changes nothing`() {
        val open = stepAt(freshState(WeightUnit.KG)).at(Msg.Weight.Open())
        assertEquals(82.4, open.at(Msg.Weight.SetValue(82.43)).model.weight.sheet?.value)
        assertEquals(MAX_BODY_KG, open.at(Msg.Weight.SetValue(Double.MAX_VALUE)).model.weight.sheet?.value)
        assertEquals(MIN_BODY_KG, open.at(Msg.Weight.SetValue(-5.0)).model.weight.sheet?.value)
        assertIgnored(open, Msg.Weight.SetValue(Double.NaN))
        assertIgnored(open, Msg.Weight.SetValue(80.0))
        assertIgnored(stepAt(freshState(WeightUnit.KG)), Msg.Weight.SetValue(82.0))
        // Pounds have their own limits: 400 kg is 881.8 lb.
        val pounds = stepAt(freshState(WeightUnit.LB)).at(Msg.Weight.Open())
        assertEquals(881.8, pounds.at(Msg.Weight.SetValue(9_999.0)).model.weight.sheet?.value)
    }

    @Test fun `a unit changed while the sheet is open shows its number in the new unit`() {
        // Found by the random walk: 20 kg stayed "20" and read as 20 lb, under the limit. 82.4 kg is 181.7 lb.
        val open = stepAt(freshState(WeightUnit.KG)).at(Msg.Weight.Open()).at(Msg.Weight.SetValue(82.4))
        assertEquals(WeightSheet(thursday, 181.7), open.at(Msg.Settings.SetUnit(WeightUnit.LB)).model.weight.sheet)
        assertEquals(listOf(82.418), open.at(Msg.Settings.SetUnit(WeightUnit.LB)).at(Msg.Weight.Save).state.weights.map { it.kg })
    }

    @Test fun `the arrows move a day, never past today, and keep the number unless that day has a weight`() {
        val state = freshState(WeightUnit.KG).copy(weights = listOf(WeightEntry("2026-11-24", 81.0, 0)))
        val open = stepAt(state).at(Msg.Weight.Open()).at(Msg.Weight.SetValue(80.6))
        assertIgnored(open, Msg.Weight.NextDay)
        val wednesday = open.at(Msg.Weight.PreviousDay)
        assertEquals(WeightSheet("2026-11-25", 80.6), wednesday.model.weight.sheet)
        assertEquals(WeightSheet("2026-11-24", 81.0), wednesday.at(Msg.Weight.PreviousDay).model.weight.sheet)
        assertEquals(WeightSheet(thursday, 80.6), wednesday.at(Msg.Weight.NextDay).model.weight.sheet)
        assertIgnored(stepAt(state), Msg.Weight.PreviousDay)
    }

    @Test fun `Save keeps the weight of the day in kilograms, and a second one that day replaces it`() {
        val saved = stepAt(freshState(WeightUnit.KG)).at(Msg.Weight.Open()).at(Msg.Weight.SetValue(82.4)).at(Msg.Weight.Save)
        assertNull(saved.model.weight.sheet)
        assertEquals(listOf(WeightEntry(thursday, 82.4, now)), saved.state.weights)
        assertTrue(Cmd.Save(saved.state) in saved.cmds)
        val later = now + 3_600_000
        val again = saved.at(Msg.Weight.Open(), later).at(Msg.Weight.SetValue(82.1), later).at(Msg.Weight.Save, later)
        assertEquals(listOf(WeightEntry(thursday, 82.1, later)), again.state.weights)
        // The same number again changes nothing on disk.
        val same = saved.at(Msg.Weight.Open(), later).at(Msg.Weight.Save, later)
        assertSame(saved.state, same.state)
        // An earlier day, typed in pounds: 180.0 lb is 81.647 kg.
        val pounds = stepAt(freshState(WeightUnit.LB)).at(Msg.Weight.Open()).at(Msg.Weight.PreviousDay).at(Msg.Weight.SetValue(180.0)).at(Msg.Weight.Save)
        assertEquals(listOf(WeightEntry("2026-11-25", 81.647, now)), pounds.state.weights)
        assertIgnored(stepAt(freshState(WeightUnit.KG)), Msg.Weight.Save)
    }

    @Test fun `Delete takes the day's weight away with Undo, which brings it back unless the day has a new one`() {
        val state = freshState(WeightUnit.KG).copy(weights = listOf(WeightEntry("2026-11-25", 81.0, 5), WeightEntry(thursday, 80.8, 6)))
        val open = stepAt(state).at(Msg.Weight.Open())
        val deleted = open.at(Msg.Weight.Delete)
        assertEquals(listOf("2026-11-25"), deleted.state.weights.map { it.day })
        assertNull(deleted.model.weight.sheet)
        val snackbar = deleted.uiEvents.filterIsInstance<UiEvent.Snackbar>().single()
        assertEquals("Deleted the weight of today", snackbar.text)
        assertEquals(Msg.Weight.UndoDelete, snackbar.onAction)
        assertEquals(state.weights, deleted.at(Msg.Weight.UndoDelete).state.weights)
        assertIgnored(deleted.at(Msg.Weight.UndoDelete), Msg.Weight.UndoDelete)
        val relogged = deleted.at(Msg.Weight.Open()).at(Msg.Weight.SetValue(80.2)).at(Msg.Weight.Save).at(Msg.Weight.UndoDelete)
        assertEquals(listOf(81.0, 80.2), relogged.state.weights.map { it.kg })
        // A day without a weight has nothing to delete.
        assertIgnored(open.at(Msg.Weight.PreviousDay).at(Msg.Weight.PreviousDay), Msg.Weight.Delete)
    }

    @Test fun `the sheet goes with Close, Back, another tab, a backup and a Reset`() {
        val open = stepAt(threeWeeks).at(Msg.Nav.SelectTab(Tab.HISTORY)).at(Msg.Weight.Open())
        assertTrue(open.model.weight.sheet != null)
        for (msg in listOf(Msg.Weight.Close, Msg.Nav.Back, Msg.Nav.SelectTab(Tab.HOME), Msg.Data.Reset, Msg.Data.Import(io.github.alexanderop.eisen.app.state.encodeState(threeWeeks)))) {
            assertNull("$msg", open.at(msg).model.weight.sheet)
        }
        assertIgnored(stepAt(threeWeeks), Msg.Weight.Close)
        // A Reset erases the weights and the goal pace.
        assertEquals(emptyList<WeightEntry>(), open.at(Msg.Data.Reset).state.weights)
        assertEquals(WeightGoal(), open.at(Msg.Data.Reset).state.weightGoal)
    }

    @Test fun `the card shows the trend and its change over the last week`() {
        // 81.0 a week ago, 80.0 every day since: the trend fell by 1 − 0.9^7 = 0.522 kg.
        val week = (0..7).map { WeightEntry(plusDays(thursday, (it - 7).toLong()), if (it == 0) 81.0 else 80.0, 0) }
        val card = weightCard(stepAt(freshState(WeightUnit.KG).copy(weights = week)).model)!!
        assertEquals("80.5 kg", card.trend)
        assertEquals("0.5 kg", card.change)
        assertEquals(Direction.DOWN, card.direction)
        assertEquals("Weight trend 80.5 kg, down 0.5 kg a week", card.label)
        assertTrue(card.loggedToday)
        assertNull(weightCard(stepAt(freshState(WeightUnit.KG)).model))
    }

    // The weekly check-in

    @Test fun `not enough data, then due once last week makes 14 logged days`() {
        val short = threeWeeks.copy(foodLog = threeWeeks.foodLog.takeLast(9))
        assertEquals(CheckIn.NotEnoughData("2026-11-23", 9, 14), checkIn(stepAt(short).model))
        assertEquals(CheckIn.Due("2026-11-23", offer), checkIn(stepAt(threeWeeks).model))
        // Food logged this week changes nothing the card says: it reads up to Sunday.
        assertEquals(CheckIn.Due("2026-11-23", offer), checkIn(stepAt(threeWeeks.copy(foodLog = threeWeeks.foodLog + eaten(thursday, 4_000))).model))
    }

    @Test fun `the Food tab shows a card that is due, and Take makes its target the goal`() {
        val due = stepAt(threeWeeks)
        assertIgnored(due, Msg.Weight.TakeCheckIn)
        assertIgnored(due, Msg.Weight.KeepCheckIn)
        val shown = due.at(Msg.Food.Open)
        assertEquals(CheckIn.Shown("2026-11-23", offer), checkIn(shown.model))
        // Shown is not saved: nothing is written for it.
        assertTrue(shown.cmds.none { it is Cmd.Save })
        val taken = shown.at(Msg.Weight.TakeCheckIn)
        assertEquals(FoodGoal(1_950, 160), taken.state.foodGoal)
        assertEquals(CheckInAnswer("2026-11-23", took = true, target = 1_950, was = 2_400), taken.state.weightGoal.checkIn)
        assertEquals(CheckIn.Taken("2026-11-23", 1_950), checkIn(taken.model))
        assertEquals("Target this week: 1,950 kcal", taken.uiEvents.filterIsInstance<UiEvent.Snackbar>().single().text)
        assertIgnored(taken, Msg.Weight.TakeCheckIn)
        assertIgnored(taken, Msg.Weight.KeepCheckIn)
    }

    @Test fun `Keep leaves the goal as it was and the card goes for the week`() {
        val kept = stepAt(threeWeeks).at(Msg.Food.Open).at(Msg.Weight.KeepCheckIn)
        assertEquals(FoodGoal(2_400, 160), kept.state.foodGoal)
        assertEquals(CheckIn.Kept("2026-11-23", 2_400), checkIn(kept.model))
        assertIgnored(kept, Msg.Weight.TakeCheckIn)
    }

    @Test fun `taking a target fills carbs and fat from the rest again when both were set`() {
        // 1,950 kcal and 160 g protein: fat 1,950 × 0.25 / 9 = 54 g, carbs (1,950 − 640 − 486) / 4 = 206 g.
        val withMacros = threeWeeks.copy(foodGoal = FoodGoal(2_400, 160, 289, 67))
        assertEquals(FoodGoal(1_950, 160, 206, 54), stepAt(withMacros).at(Msg.Food.Open).at(Msg.Weight.TakeCheckIn).state.foodGoal)
    }

    @Test fun `a restart with the card open shows it again, and nothing was taken`() {
        val shown = stepAt(threeWeeks).at(Msg.Food.Open)
        val (restarted, _) = init(Flags(shown.state, null, healthEnabled = false, notifications = Notifications(true, true), now = now + 60_000))
        assertEquals(CheckIn.Due("2026-11-23", offer), checkIn(restarted))
        assertEquals(CheckIn.Shown("2026-11-23", offer), checkIn(Step(restarted).at(Msg.Food.Open, now + 120_000).model))
    }

    @Test fun `two weeks missed bring one card, for this week, from last week's numbers`() {
        val answered = threeWeeks.copy(weightGoal = threeWeeks.weightGoal.copy(checkIn = CheckInAnswer("2026-11-02", took = true, target = 2_400, was = 2_500)))
        assertEquals(CheckIn.Due("2026-11-23", offer), checkIn(stepAt(answered).model))
        // The Monday after a taken week, the next card is due.
        val taken = stepAt(threeWeeks).at(Msg.Food.Open).at(Msg.Weight.TakeCheckIn)
        val nextMonday = at("2026-11-30", 7)
        assertTrue(checkIn(taken.at(Msg.Food.Open, nextMonday).model) is CheckIn.Shown)
    }

    @Test fun `a week without food says what the numbers were`() {
        // Nothing logged from Nov 16 to 22, but the 14 days before it: enough to know what the lifter burns.
        val missed = threeWeeks.copy(foodLog = threeWeeks.foodLog.filter { it.day < "2026-11-16" })
        assertEquals(CheckIn.Due("2026-11-23", offer.copy(intake = null, loggedDays = 0)), checkIn(stepAt(missed).model))
    }

    @Test fun `switched off, there is no card and nothing changes the goal`() {
        val off = threeWeeks.copy(weightGoal = threeWeeks.weightGoal.copy(adjust = false))
        val open = stepAt(off).at(Msg.Food.Open)
        assertEquals(CheckIn.Off, checkIn(open.model))
        assertIgnored(open, Msg.Weight.TakeCheckIn)
        assertIgnored(open, Msg.Weight.KeepCheckIn)
    }

    @Test fun `the goal sheet saves the pace within 1 percent of the trend and the switch`() {
        val sheet = stepAt(threeWeeks).at(Msg.Food.Open).at(Msg.Food.OpenGoal)
        assertEquals(FoodSheet.Goal, sheet.model.food.sheet)
        // 1 % of 80 kg is 0.8 kg a week.
        assertEquals(0.8, goalWeightView(sheet.model).maxPace, 0.0)
        val saved = sheet.at(Msg.Food.SaveGoal(2_400, 160, 0, 0, WeightGoal(GoalDirection.GAIN, 3.0, adjust = false)))
        assertEquals(WeightGoal(GoalDirection.GAIN, 0.8, adjust = false), saved.state.weightGoal)
        // Without the weight part the goal pace stays as it was.
        assertEquals(threeWeeks.weightGoal, sheet.at(Msg.Food.SaveGoal(2_000, 160)).state.weightGoal)
        // A check-in answered this week stays answered.
        val answered = sheet.at(Msg.Food.CloseSheet).at(Msg.Weight.KeepCheckIn).at(Msg.Food.OpenGoal)
        assertEquals(CheckInAnswer("2026-11-23", false, 1_950, 2_400), answered.at(Msg.Food.SaveGoal(2_400, 160, 0, 0, WeightGoal(GoalDirection.LOSE, 0.4))).state.weightGoal.checkIn)
    }

    // Health Connect

    @Test fun `weights follow into Health Connect only once weight may be written`() {
        val writing = HealthModel(enabled = true, food = true, weight = true)
        val open = Step(Model(freshState(WeightUnit.KG), now = now, health = writing)).at(Msg.Weight.Open())
        val saved = open.at(Msg.Weight.SetValue(82.4)).at(Msg.Weight.Save)
        val entry = WeightEntry(thursday, 82.4, now)
        assertTrue(Cmd.Health(HealthJob.Upsert(emptyList(), emptyList(), emptyList(), listOf(entry))) in saved.cmds)
        val deleted = saved.at(Msg.Weight.Open()).at(Msg.Weight.Delete)
        assertTrue(Cmd.Health(HealthJob.Delete(emptyList(), emptyList(), listOf(thursday))) in deleted.cmds)
        val notYet = Step(Model(freshState(WeightUnit.KG), now = now, health = writing.copy(weight = false))).at(Msg.Weight.Open()).at(Msg.Weight.Save)
        assertTrue(notYet.cmds.none { it is Cmd.Health })
    }

    @Test fun `a lifter who allowed workouts and food is asked for weight in Settings, never at the start`() {
        val synced = Step(Model(freshState(WeightUnit.KG), health = HealthModel(enabled = true, food = true)))
        val checked = synced.send(Msg.Health.PermissionChecked(PermissionCheck.STARTUP, granted = true, food = true, weight = false))
        assertTrue(checked.model.health.enabled)
        assertTrue(checked.cmds.none { it is Cmd.Ui })
        val asking = checked.send(Msg.Health.AllowWeight)
        assertEquals(listOf(UiEvent.RequestHealthPermission), asking.uiEvents)
        assertTrue(asking.send(Msg.Health.PermissionResult(granted = true, food = true, weight = true)).model.health.weight)
        val refused = asking.send(Msg.Health.PermissionResult(granted = true, food = true, weight = false))
        assertEquals("Health Connect did not allow Eisen to write weight. Workouts and food are still synced.", refused.model.health.notice)
        assertIgnored(checked.send(Msg.Health.PermissionResult(granted = true, food = true, weight = true)), Msg.Health.AllowWeight)
        assertTrue(checked.send(Msg.Health.PermissionChecked(PermissionCheck.ROWS, granted = true, food = true, weight = true)).model.health.weight)
        // The switch asks for all three at once.
        assertEquals(listOf(UiEvent.RequestHealthPermission), stepOf().send(Msg.Health.PermissionChecked(PermissionCheck.TOGGLE, granted = true, food = true, weight = false)).uiEvents)
        // A write that finds it taken away notes it; workouts and food go on.
        val revoked = checked.send(Msg.Health.PermissionResult(granted = true, food = true, weight = true)).send(Msg.Health.WeightRevoked)
        assertTrue(revoked.model.health.enabled && revoked.model.health.food && !revoked.model.health.weight)
    }
}

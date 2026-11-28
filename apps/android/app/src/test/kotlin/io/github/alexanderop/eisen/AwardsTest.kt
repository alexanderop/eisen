package io.github.alexanderop.eisen

import io.github.alexanderop.eisen.app.elm.HistoryView
import io.github.alexanderop.eisen.app.elm.Msg
import io.github.alexanderop.eisen.app.elm.Tab
import io.github.alexanderop.eisen.app.elm.WorkoutEvent
import io.github.alexanderop.eisen.app.elm.awardsOf
import io.github.alexanderop.eisen.app.elm.earnedMedalIds
import io.github.alexanderop.eisen.app.elm.liveRecords
import io.github.alexanderop.eisen.app.elm.xpOf
import io.github.alexanderop.eisen.app.state.AppState
import io.github.alexanderop.eisen.app.state.Loaded
import io.github.alexanderop.eisen.app.state.encodeState
import io.github.alexanderop.eisen.app.state.freshState
import io.github.alexanderop.eisen.app.state.loadState
import io.github.alexanderop.eisen.core.Equipment
import io.github.alexanderop.eisen.core.WeightUnit
import io.github.alexanderop.eisen.feature.awards.Awards
import io.github.alexanderop.eisen.feature.awards.Level
import io.github.alexanderop.eisen.feature.awards.Medal
import io.github.alexanderop.eisen.feature.awards.Rank
import io.github.alexanderop.eisen.feature.awards.Tier
import io.github.alexanderop.eisen.feature.awards.Xp
import io.github.alexanderop.eisen.feature.awards.levelCost
import io.github.alexanderop.eisen.feature.awards.levelOf
import io.github.alexanderop.eisen.feature.awards.nextRankText
import io.github.alexanderop.eisen.feature.awards.rankOf
import io.github.alexanderop.eisen.feature.awards.recordTier
import io.github.alexanderop.eisen.feature.food.FoodEntry
import io.github.alexanderop.eisen.feature.food.WeightEntry
import io.github.alexanderop.eisen.feature.workout.ExerciseLog
import io.github.alexanderop.eisen.feature.workout.Workout
import io.github.alexanderop.eisen.feature.workout.recordEvents
import io.github.alexanderop.eisen.feature.workout.recordIds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.ZoneId
import java.time.ZonedDateTime

/** XP, levels and medals, worked out from the history: `feature/awards` and `app/elm/AwardsSelectors.kt`. */
class AwardsTest {
    private val berlin: ZoneId = ZoneId.of("Europe/Berlin")

    /** Wednesday, 25 November 2026, 10:00 in Berlin: the screen tests' day, which the `lifter` fixture fits. */
    private val now = ZonedDateTime.of(2026, 11, 25, 10, 0, 0, 0, berlin).toInstant().toEpochMilli()
    private val day = 24 * 60 * 60 * 1000L

    private fun lifter(): AppState {
        val loaded = loadState(File("../../../.claude/skills/verify-eisen/fixtures/lifter.json").readText())
        return (loaded as Loaded.Ok).state
    }

    private fun squat(weight: Double, sets: List<Int?> = List(5) { 5 }, id: String = "hackSquat") =
        ExerciseLog(id, "Hack Squat", Equipment.MACHINE, weight, 5, sets)

    /** A workout [daysAgo] days before [now], with these logs. */
    private fun workout(daysAgo: Int, vararg logs: ExerciseLog, unit: WeightUnit = WeightUnit.KG): Workout {
        val start = now - daysAgo * day
        return Workout("w$daysAgo", "A", "Workout A", unit, start, start + 45 * 60_000L, logs.toList())
    }

    /** Workouts are saved newest first. */
    private fun withHistory(vararg oldestFirst: Workout): AppState = freshState().copy(history = oldestFirst.reversed())

    private fun awards(state: AppState): Awards = awardsOf(state, now, berlin)

    private fun Awards.medal(id: String): Medal = medals.first { it.id == id }

    @Test fun `an empty history is level 1 of Tin, with every medal locked`() {
        val awards = awards(freshState())
        assertEquals(0L, awards.xp)
        assertEquals(Level(1, 0, 520), awards.level)
        assertEquals(Rank.TIN, awards.rank)
        assertTrue(awards.earned.isEmpty())
        // One record medal per exercise of the two workouts, in their order.
        assertEquals(
            listOf("record:hackSquat", "record:bench", "record:row", "record:dips", "record:ohp", "record:deadlift", "record:pullups"),
            awards.medals.filter { it.id.startsWith("record:") }.map { it.id },
        )
        assertEquals("No record yet", awards.medal("record:bench").progressText)
        assertEquals("0 of 4", awards.medal("weeks:4").progressText)
        assertEquals("0 of 10", awards.medal("workouts:10").progressText)
        assertEquals("0 of 10 t", awards.medal("tonnes:10").progressText)
        // Without a weight logged there is nothing to squat against.
        assertTrue(awards.medals.none { it.id == "bodyweight-squat" })
    }

    @Test fun `a level costs 400 plus 120 per level, and the ranks start at 1, 5, 10, 20, 35 and 50`() {
        assertEquals(520L, levelCost(1))
        assertEquals(1_840L, levelCost(12))
        assertEquals(Level(1, 519, 520), levelOf(519))
        assertEquals(Level(2, 0, 640), levelOf(520))
        assertEquals(Level(3, 0, 760), levelOf(520 + 640))
        assertEquals(Level(2, 639, 640), levelOf(520 + 639))
        assertEquals(1, levelOf(-5).level)
        assertEquals(0.5f, Level(2, 320, 640).fraction)
        assertEquals(listOf(Rank.TIN, Rank.TIN, Rank.COPPER, Rank.COPPER, Rank.BRONZE, Rank.IRON, Rank.STEEL, Rank.TITANIUM, Rank.TITANIUM), listOf(0, 4, 5, 9, 10, 20, 35, 50, 400).map(::rankOf))
        assertEquals("8 levels to Iron", nextRankText(12))
        assertEquals("1 level to Bronze", nextRankText(9))
        assertEquals("Top rank", nextRankText(50))
    }

    @Test fun `the largest XP stops at the top level instead of counting on`() {
        val top = levelOf(Long.MAX_VALUE)
        assertEquals(999, top.level)
        assertEquals(1f, top.fraction)
    }

    @Test fun `XP comes from workouts, sets, records, timers, days of food and weigh-ins`() {
        val first = workout(10, squat(100.0))
        assertEquals(100L + 5 * 10, xpOf(withHistory(first)))
        // Heavier with every set done: a record on top.
        val second = workout(8, squat(105.0))
        assertEquals(2 * 100L + 10 * 10 + 50, xpOf(withHistory(first, second)))
        // A set of 0 reps was no work.
        val missed = workout(6, squat(105.0, listOf(5, 5, 0, null, null)))
        assertEquals(3 * 100L + 12 * 10 + 50, xpOf(withHistory(first, second, missed)))
        val fed = freshState().copy(
            foodLog = listOf(FoodEntry("a", name = "Oats", kcal = 300, at = now, day = "2026-11-24"), FoodEntry("b", name = "Milk", kcal = 100, at = now, day = "2026-11-24"), FoodEntry("c", name = "Rice", kcal = 400, at = now, day = "2026-11-25")),
            weights = listOf(WeightEntry("2026-11-24", 80.0, now), WeightEntry("2026-11-25", 79.8, now)),
        )
        assertEquals(2L * Xp.FOOD_DAY + 2 * Xp.WEIGHT, xpOf(fed))
    }

    @Test fun `a record has to beat the best before it, and a tie, rounding noise or missed reps are none`() {
        val history = withHistory(
            workout(20, squat(100.0)),
            workout(18, squat(100.0)),
            workout(16, squat(100.004)),
            workout(14, squat(102.5, listOf(5, 5, 5, 5, 4))),
            workout(12, squat(102.5)),
            workout(10, squat(102.5)),
        ).history
        // The first log has nothing to beat; only 102.5 with every set done beats 100.
        assertEquals(listOf("w12"), recordEvents(history).map { it.workout.id })
        val medal = awards(withHistory(*history.reversed().toTypedArray())).medal("record:hackSquat")
        assertEquals("Hack Squat 102.5kg", medal.name)
        assertEquals(Tier.BRONZE, medal.tier)
        assertEquals(history.first { it.id == "w12" }.finishedAt, medal.earnedAt)
    }

    @Test fun `a lift logged once holds no record yet`() {
        val awards = awards(withHistory(workout(3, squat(100.0))))
        assertFalse(awards.medal("record:hackSquat").earned)
    }

    @Test fun `a record medal gets a better metal with more records, and a pound record counts in kilograms`() {
        assertEquals(listOf(Tier.BRONZE, Tier.BRONZE, Tier.SILVER, Tier.SILVER, Tier.GOLD, Tier.IRON), listOf(0, 1, 3, 5, 6, 12).map(::recordTier))
        // 225 lb is 102.06 kg: more than 100 kg.
        val history = withHistory(workout(5, squat(100.0)), workout(3, squat(225.0), unit = WeightUnit.LB))
        assertEquals("Hack Squat 225lb", awards(history).medal("record:hackSquat").name)
    }

    @Test fun `one pass over the history finds the records each workout's recordIds finds`() {
        val history = lifter().history
        val byPass = recordEvents(history).map { it.workout.id to it.log.id }.toSet()
        val byWorkout = history.flatMap { workout -> recordIds(history, history.indexOf(workout)).map { workout.id to it } }.toSet()
        assertEquals(byWorkout, byPass)
        assertEquals(40, byPass.size)
    }

    @Test fun `the lifter is level 7 of Copper with 11 of 18 medals`() {
        val awards = awards(lifter())
        // 12 workouts, 216 sets, 40 records, 7 timers.
        assertEquals(12 * 100L + 216 * 10 + 40 * 50 + 7 * 50, awards.xp)
        assertEquals(Level(7, 790, 1_240), awards.level)
        assertEquals(Rank.COPPER, awards.rank)
        assertEquals(18, awards.medals.size)
        assertEquals(
            setOf("record:hackSquat", "record:bench", "record:row", "record:dips", "record:ohp", "record:deadlift", "record:pullups", "weeks:4", "perfect-week", "workouts:10", "tonnes:10"),
            awards.earned.map { it.id }.toSet(),
        )
        assertEquals(Tier.GOLD, awards.medal("record:hackSquat").tier)
        assertEquals("Hack Squat 140kg", awards.medal("record:hackSquat").name)
        assertEquals("Dips 35 reps", awards.medal("record:dips").name)
        // The current week has nothing yet, so the run is the four weeks before it.
        assertEquals("4 of 12", awards.medal("weeks:12").progressText)
        assertEquals("0 of 3 this week", awards.medal("perfect-week").progressText)
        assertEquals("12 of 50", awards.medal("workouts:50").progressText)
        assertEquals("80 of 100 t", awards.medal("tonnes:100").progressText)
        assertEquals("0 of 7", awards.medal("food:7").progressText)
    }

    @Test fun `weeks in a row and a perfect week count Monday to Sunday`() {
        // Monday the 9th, Wednesday the 11th and Sunday the 15th: three in one week. Then nothing the week after.
        val history = withHistory(workout(16, squat(100.0)), workout(14, squat(100.0)), workout(10, squat(100.0)))
        val awards = awards(history)
        assertTrue(awards.medal("perfect-week").earned)
        assertEquals(history.history.first().finishedAt, awards.medal("perfect-week").earnedAt)
        // The week of the 16th has nothing: the run is broken.
        assertEquals("0 of 4", awards.medal("weeks:4").progressText)
        // Sunday the 15th and Monday the 16th are two weeks.
        val twoWeeks = awards(withHistory(workout(10, squat(100.0)), workout(9, squat(100.0))))
        assertFalse(twoWeeks.medal("perfect-week").earned)
        assertEquals("2 of 4", twoWeeks.medal("weeks:4").progressText)
        val thisAndLast = awards(withHistory(workout(9, squat(100.0)), workout(2, squat(100.0)), workout(0, squat(100.0))))
        assertEquals("2 of 4", thisAndLast.medal("weeks:4").progressText)
        assertEquals("2 of 3 this week", thisAndLast.medal("perfect-week").progressText)
    }

    @Test fun `food logged seven days in a row, and today not logged yet keeps the run`() {
        fun fed(vararg days: String) = freshState().copy(foodLog = days.mapIndexed { i, d -> FoodEntry("f$i", name = "Oats", kcal = 300, at = now - (days.size - i) * day, day = d) })
        val six = awards(fed("2026-11-19", "2026-11-20", "2026-11-21", "2026-11-22", "2026-11-23", "2026-11-24"))
        assertFalse(six.medal("food:7").earned)
        assertEquals("6 of 7", six.medal("food:7").progressText)
        val seven = awards(fed("2026-11-18", "2026-11-19", "2026-11-20", "2026-11-21", "2026-11-22", "2026-11-23", "2026-11-24"))
        assertTrue(seven.medal("food:7").earned)
        val gap = awards(fed("2026-11-10", "2026-11-11", "2026-11-12", "2026-11-14", "2026-11-15", "2026-11-16", "2026-11-17", "2026-11-18"))
        assertFalse(gap.medal("food:7").earned)
        assertEquals("0 of 7", gap.medal("food:7").progressText)
    }

    @Test fun `the bodyweight squat needs every set at the weight logged on that day or before`() {
        val weights = listOf(WeightEntry("2026-11-01", 80.0, now), WeightEntry("2026-11-20", 82.0, now))
        val under = awards(withHistory(workout(10, squat(77.5))).copy(weights = weights))
        assertFalse(under.medal("bodyweight-squat").earned)
        assertEquals("77.5kg of 82kg", under.medal("bodyweight-squat").progressText)
        // 80 kg on the 15th: the weight of the 1st counts, not the later 82.
        assertTrue(awards(withHistory(workout(10, squat(80.0))).copy(weights = weights)).medal("bodyweight-squat").earned)
        assertFalse(awards(withHistory(workout(10, squat(80.0, listOf(5, 5, 5, 5, 4)))).copy(weights = weights)).medal("bodyweight-squat").earned)
        // A bench at bodyweight is no squat.
        val bench = ExerciseLog("bench", "Bench Press", Equipment.BARBELL, 90.0, 5, List(5) { 5 })
        assertFalse(awards(withHistory(workout(10, bench)).copy(weights = weights)).medal("bodyweight-squat").earned)
    }

    @Test fun `milestones count workouts and tonnes`() {
        val ten = (19 downTo 10).map { workout(it, squat(100.0)) }.toTypedArray()
        val awards = awards(withHistory(*ten))
        assertTrue(awards.medal("workouts:10").earned)
        assertEquals(ten.last().finishedAt, awards.medal("workouts:10").earnedAt)
        // 10 workouts × 25 reps × 100 kg = 25 t.
        assertTrue(awards.medal("tonnes:10").earned)
        assertEquals(ten[3].finishedAt, awards.medal("tonnes:10").earnedAt)
        assertEquals("25 of 100 t", awards.medal("tonnes:100").progressText)
        assertEquals("2.5 of 10 t", awards(withHistory(ten[0])).medal("tonnes:10").progressText)
    }

    @Test fun `a saved workout shows its XP and the medals not seen yet, which are seen from then on`() {
        val state = lifter()
        val start = stepOf(state).send(Msg.Workout(WorkoutEvent.Start), now = now, id = "w9")
        var step = start
        // Every set of the Hack Squat at 145 kg: heavier than the 140 kg of its record.
        for (set in 0 until 5) step = step.send(WorkoutEvent.TapSet(0, set), now = now + set * 1000L)
        assertEquals(setOf("hackSquat"), liveRecords(step.model))
        step = step.send(WorkoutEvent.RequestFinish, now = now + 10_000).send(WorkoutEvent.Confirm, now = now + 10_000)
        val saved = requireNotNull(step.model.lastAwards)
        assertEquals(100L + 5 * Xp.SET + Xp.RECORD, saved.xp)
        assertEquals("Hack Squat 145kg", saved.medals.first().name)
        // Only the Hack Squat's record is new; the others were earned before and only never seen.
        assertEquals(setOf("record:hackSquat"), saved.records)
        // Everything earned before was never seen in this file: the card shows all of it once.
        assertEquals(earnedMedalIds(step.model.state, berlin), saved.medals.map { it.id }.toSet())
        assertEquals(saved.medals.map { it.id }.toSet(), step.model.state.seenMedals.toSet())
        assertNull(step.send(Msg.Home.DismissSummary).model.lastAwards)
        // The next workout's card shows no medal it already showed.
        var next = step.send(Msg.Workout(WorkoutEvent.Start), now = now + day, id = "w10")
        next = next.send(WorkoutEvent.TapSet(1, 0), now = now + day).send(WorkoutEvent.RequestFinish, now = now + day).send(WorkoutEvent.Confirm, now = now + day)
        assertEquals(emptyList<String>(), next.model.lastAwards?.medals?.map { it.id })
        // A new record of a lift whose medal was seen shows it again, with the new best.
        var third = next.send(Msg.Workout(WorkoutEvent.Start), now = now + 2 * day, id = "w11")
        for (set in 0 until 5) third = third.send(WorkoutEvent.TapSet(0, set), now = now + 2 * day + set * 1000L)
        third = third.send(WorkoutEvent.RequestFinish, now = now + 2 * day + 10_000).send(WorkoutEvent.Confirm, now = now + 2 * day + 10_000)
        assertEquals(listOf("Hack Squat 150kg"), third.model.lastAwards?.medals?.map { it.name })
    }

    @Test fun `a lift short of a set is no record yet, during the workout as on the medal`() {
        var step = stepOf(lifter()).send(Msg.Workout(WorkoutEvent.Start), now = now, id = "w9")
        for (set in 0 until 4) step = step.send(WorkoutEvent.TapSet(0, set), now = now)
        assertEquals(emptySet<String>(), liveRecords(step.model))
    }

    @Test fun `the medal case marks the new medals when it opens, and they are seen from then on`() {
        val opened = stepOf(lifter()).send(Msg.Nav.OpenMedals, now = now)
        assertEquals(Tab.HISTORY, opened.model.nav.tab)
        assertEquals(HistoryView.MEDALS, opened.model.nav.history)
        assertEquals(11, opened.model.medalShine.size)
        assertEquals(opened.model.medalShine, opened.state.seenMedals.toSet())
        assertTrue(opened.cmds.any { it is io.github.alexanderop.eisen.app.elm.Cmd.Save })
        // Open again: nothing is new any more.
        val again = opened.send(Msg.Nav.ShowHistory(HistoryView.LOG)).send(Msg.Nav.ShowHistory(HistoryView.MEDALS))
        assertEquals(emptySet<String>(), again.model.medalShine)
        assertTrue(again.cmds.none { it is io.github.alexanderop.eisen.app.elm.Cmd.Save })
    }

    @Test fun `the seen medals are saved, and a file without them loads as before and is written without them`() {
        val fresh = encodeState(freshState())
        assertFalse(fresh.contains("seenMedals"))
        val seen = freshState().copy(seenMedals = listOf("weeks:4", "weeks:4", "", "x".repeat(200)))
        val back = (loadState(encodeState(seen)) as Loaded.Ok).state
        assertEquals(listOf("weeks:4"), back.seenMedals)
    }
}

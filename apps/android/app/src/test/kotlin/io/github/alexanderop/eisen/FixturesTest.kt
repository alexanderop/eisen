package io.github.alexanderop.eisen

import io.github.alexanderop.eisen.app.elm.timerCapSeconds
import io.github.alexanderop.eisen.app.elm.timerWheelSeconds
import io.github.alexanderop.eisen.app.state.AppState
import io.github.alexanderop.eisen.app.state.Loaded
import io.github.alexanderop.eisen.app.state.STATE_VERSION
import io.github.alexanderop.eisen.app.state.StateJson
import io.github.alexanderop.eisen.app.state.encodeState
import io.github.alexanderop.eisen.app.state.freshState
import io.github.alexanderop.eisen.app.state.loadState
import io.github.alexanderop.eisen.core.WeightUnit
import io.github.alexanderop.eisen.feature.timer.TIMER_LEAD_IN_SECONDS
import io.github.alexanderop.eisen.feature.timer.TimerMode
import io.github.alexanderop.eisen.feature.timer.TimerPreset
import io.github.alexanderop.eisen.feature.timer.boxing.BoxingTheme
import io.github.alexanderop.eisen.feature.timer.boxing.MAX_BOXING_REST_SECONDS
import io.github.alexanderop.eisen.feature.timer.boxing.MAX_BOXING_ROUND_SECONDS
import io.github.alexanderop.eisen.feature.timer.boxing.MIN_BOXING_REST_SECONDS
import io.github.alexanderop.eisen.feature.timer.boxing.MIN_BOXING_ROUND_SECONDS
import io.github.alexanderop.eisen.feature.timer.timerConfig
import io.github.alexanderop.eisen.feature.timer.timerSeconds
import io.github.alexanderop.eisen.feature.timer.wod.isLibraryWod
import io.github.alexanderop.eisen.feature.food.FoodGoal
import io.github.alexanderop.eisen.feature.food.FoodSource
import io.github.alexanderop.eisen.feature.food.barcodeProblem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File

/**
 * The prepared `eisen.json` files that `eisenctl launch --state <name>` starts the app on. A state
 * change that forgets them fails here, not on the emulator: each one loads, is at the current
 * version, and still holds what its name promises. Their numbers are ones the setup wheels offer.
 */
class FixturesTest {
    // Unit tests run in `apps/android/app`.
    private val folder = File("../../../.claude/skills/verify-eisen/fixtures")

    private fun fixtures(): Map<String, File> =
        folder.listFiles { file -> file.extension == "json" }.orEmpty().associateBy { it.nameWithoutExtension }

    /** What each fixture is for. A fixture without a promise here is one nobody checks. */
    private val promises: Map<String, (AppState) -> Unit> = mapOf(
        "short-timers" to ::shortTimers,
        "lifter" to ::lifter,
        "food" to ::food,
        "food-history" to ::foodHistory,
        "food-weight" to ::foodWeight,
    )

    @Test fun `every fixture loads, is at the current version, and saving it again changes nothing`() {
        assertTrue("no fixtures in ${folder.absolutePath}", fixtures().isNotEmpty())
        for ((name, file) in fixtures()) {
            val text = file.readText()
            val loaded = loadState(text)
            if (loaded !is Loaded.Ok) fail("$name.json does not load: $loaded")
            val state = (loaded as Loaded.Ok).state
            assertEquals("$name.json is not at the current version: load it and save it again", STATE_VERSION, state.version)
            assertEquals(
                "$name.json changes when the app saves it: write it as `encodeState(loadState(it), pretty = true)`",
                StateJson.parseToJsonElement(text),
                StateJson.parseToJsonElement(encodeState(state)),
            )
        }
    }

    @Test fun `every fixture holds what its name promises`() {
        assertEquals(promises.keys, fixtures().keys)
        for ((name, file) in fixtures()) {
            val state = (loadState(file.readText()) as Loaded.Ok).state
            promises.getValue(name)(state)
            state.timerPresets.forEach(::onTheWheels)
        }
    }

    /**
     * Fresh data and a saved timer per mode that is over within 40 seconds with the lead-in (a For Time
     * when Finish is tapped), plus a boxing round of 3:00 for the minutes left in the round.
     */
    private fun shortTimers(state: AppState) {
        val fresh = freshState(WeightUnit.KG)
        assertEquals(fresh.copy(timerPresets = state.timerPresets), state)
        // A For Time is complete when Finish is tapped; the shortest cap there is (1:00) is there to run into.
        fun quick(preset: TimerPreset) = if (preset.config.mode == TimerMode.FOR_TIME) preset.config.work in 1..60
        else TIMER_LEAD_IN_SECONDS + (timerSeconds(preset.config) ?: Int.MAX_VALUE) <= 40
        val quick = state.timerPresets.filter(::quick)
        assertEquals(TimerMode.entries.toSet(), quick.map { it.config.mode }.toSet())
        val coached = quick.filter { it.config.mode == TimerMode.BOXING }.flatMap { it.config.boxing!!.rounds }
        assertTrue("no quick boxing timer with a coach", coached.any { it.theme != BoxingTheme.FREESTYLE })
        val long = state.timerPresets.single { it.config.mode == TimerMode.BOXING && it.config.work == 180 && it.config.rounds == 1 }
        assertTrue("${long.name} calls no combinations", long.config.boxing!!.rounds.single().theme != BoxingTheme.FREESTYLE)
        assertTrue("the minutes left are not said", state.settings.timerCalls.minutes > 0)
    }

    /** Weeks of workouts, a session of every kind, a WOD run, an own WOD and weights that moved. */
    private fun lifter(state: AppState) {
        val days = state.history.map { it.startedAt / DAY }.toSet()
        assertTrue("fewer than 8 workouts", state.history.size >= 8)
        assertTrue("the workouts span less than three weeks", (days.max() - days.min()) >= 21)
        assertTrue("no workout with a rating", state.history.any { log -> log.exercises.any { it.rir.any { rir -> rir != null } } })
        val modes = state.timerSessions.map { it.config.mode }.toSet()
        assertTrue("sessions of $modes only", modes.containsAll(TimerMode.entries - TimerMode.FOR_TIME))
        assertTrue("no boxing session with punches", state.timerSessions.any { it.config.mode == TimerMode.BOXING && (it.punches ?: 0) > 0 })
        assertTrue("no benchmark WOD result", state.timerSessions.any { it.wodId?.let(::isLibraryWod) == true })
        val own = state.wods.singleOrNull() ?: error("no own WOD")
        assertTrue("the own WOD was never run", state.timerSessions.any { it.wodId == own.id })
        val fresh = freshState(WeightUnit.KG).progress
        assertTrue("the weights are the fresh ones", state.progress.any { (id, progress) -> fresh[id]?.weight != progress.weight })
        assertEquals("a workout is running", null, state.active)
    }

    /**
     * Fresh data, a goal of 2,400 kcal and 160 g protein, and a product found before by its barcode (so typing that
     * barcode needs no network), with nothing eaten yet: the journeys log what they need on the day they run.
     */
    private fun food(state: AppState) {
        assertEquals(freshState(WeightUnit.KG).copy(foods = state.foods, foodGoal = state.foodGoal), state)
        assertEquals(FoodGoal(2400, 160), state.foodGoal)
        val scanned = state.foods.single()
        assertEquals(FoodSource.SCANNED, scanned.source)
        assertEquals(null, scanned.barcode?.let(::barcodeProblem))
        assertTrue("no pack for the chips", scanned.pack != null)
    }

    /**
     * The food fixture with two weeks of history: eight foods, each eaten every day at its own time, three hours apart
     * (01:30, 04:30, ... 22:30 in Berlin), so whatever the hour a journey runs at, one of them is "usual at this time".
     * Every entry has its own time and is what the add sheet logs of its food at its usual amount.
     */
    private fun foodHistory(state: AppState) {
        val log = state.foodLog
        assertEquals(freshState(WeightUnit.KG).copy(foods = state.foods, foodGoal = state.foodGoal, foodLog = log), state)
        assertEquals(FoodGoal(2400, 160), state.foodGoal)
        val berlin = java.time.ZoneId.of("Europe/Berlin")
        assertTrue("an entry without a time of its own", log.all { io.github.alexanderop.eisen.feature.food.hasTime(it, berlin) })
        val times = log.groupBy { io.github.alexanderop.eisen.feature.food.minuteOfDay(it.at, berlin) }
        assertEquals((0 until 8).map { 90 + it * 180 }, times.keys.sorted())
        for ((_, entries) in times) {
            assertEquals("one food per time", 1, entries.map { it.foodId }.toSet().size)
            assertTrue("fewer than 14 days", entries.map { it.day }.toSet().size >= 14)
        }
        // Moved so its newest day is in the last week, its oldest is still within the weeks "usual at this time" looks back.
        val days = log.map { java.time.LocalDate.parse(it.day) }.toSet()
        val span = java.time.temporal.ChronoUnit.DAYS.between(days.min(), days.max())
        assertTrue("the history spans $span days", span in 13..(io.github.alexanderop.eisen.feature.food.USUAL_DAYS - 8).toLong())
    }

    /**
     * The food fixture with four weeks, Monday to Sunday, of food and weights: most mornings a weight (a few without one,
     * one high), three meals a day but on two days nothing, a goal of 2,400 kcal and to lose 0.5 kg a week with the
     * check-in on and never answered. The driver moves its days by whole weeks, so its last day is always the Sunday
     * before the day a journey runs: the check-in is due, and the trend and what the lifter burns are known.
     */
    private fun foodWeight(state: AppState) {
        assertEquals(freshState(WeightUnit.KG).copy(foods = state.foods, foodGoal = state.foodGoal, foodLog = state.foodLog, weights = state.weights, weightGoal = state.weightGoal), state)
        assertEquals(FoodGoal(2400, 160), state.foodGoal)
        assertEquals(io.github.alexanderop.eisen.feature.food.WeightGoal(io.github.alexanderop.eisen.feature.food.GoalDirection.LOSE, 0.5, adjust = true), state.weightGoal)
        val days = state.foodLog.map { it.day }.toSet() + state.weights.map { it.day }
        val last = java.time.LocalDate.parse(days.max())
        assertEquals("its last day is not a Sunday", java.time.DayOfWeek.SUNDAY, last.dayOfWeek)
        assertEquals("not four weeks", 27L, java.time.temporal.ChronoUnit.DAYS.between(java.time.LocalDate.parse(days.min()), last))
        assertEquals("one weight per day", state.weights.map { it.day }.toSet().size, state.weights.size)
        assertTrue("fewer than 20 weights", state.weights.size >= 20)
        // Whatever weekday a journey runs on, the 21 days before yesterday hold 14 logged days.
        val monday = last.plusDays(1)
        for (offset in 0L..6L) {
            val today = monday.plusDays(offset)
            val model = io.github.alexanderop.eisen.app.elm.Model(state, now = today.atTime(10, 0).toInstant(java.time.ZoneOffset.UTC).toEpochMilli())
            assertTrue("$today: ${io.github.alexanderop.eisen.app.elm.burnNow(model)}", io.github.alexanderop.eisen.app.elm.burnNow(model) is io.github.alexanderop.eisen.feature.food.Estimate.Ready)
            val due = io.github.alexanderop.eisen.app.elm.checkIn(model)
            assertTrue("$today: $due", due is io.github.alexanderop.eisen.app.elm.CheckIn.Due && due.offer.note == null && due.offer.target != 2400)
        }
    }

    /** A saved timer whose numbers the setup's wheels can show: nothing a lifter could not have saved. */
    private fun onTheWheels(preset: TimerPreset) {
        val config = preset.config
        // Within the limits of its mode (rounds, a rest only where there is one), as the setup keeps it.
        assertEquals("${preset.name} is outside the limits of its mode", timerConfig(config), config)
        fun on(value: Int, choices: List<Int>, what: String) = assertTrue("${preset.name}: $what $value is not on its wheel", value in choices)
        when (config.mode) {
            TimerMode.TABATA -> {
                on(config.work, timerWheelSeconds(5, 600, -1), "work")
                on(config.rest, timerWheelSeconds(5, 300, -1), "rest")
            }
            TimerMode.EMOM -> on(config.work, timerWheelSeconds(10, 600, -1), "every")
            TimerMode.AMRAP -> on(config.work, timerWheelSeconds(30, 3600, -1), "time")
            TimerMode.FOR_TIME -> on(config.work, timerCapSeconds(-1), "cap")
            TimerMode.BOXING -> {
                on(config.work, timerWheelSeconds(MIN_BOXING_ROUND_SECONDS, MAX_BOXING_ROUND_SECONDS, -1), "round")
                on(config.rest, timerWheelSeconds(MIN_BOXING_REST_SECONDS, MAX_BOXING_REST_SECONDS, -1), "rest")
            }
        }
    }

    private companion object {
        const val DAY = 24 * 60 * 60 * 1000L
    }
}

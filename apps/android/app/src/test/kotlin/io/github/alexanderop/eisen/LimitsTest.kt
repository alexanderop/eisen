package io.github.alexanderop.eisen

import io.github.alexanderop.eisen.app.elm.Msg
import io.github.alexanderop.eisen.app.elm.WorkoutEvent
import io.github.alexanderop.eisen.app.elm.repsQuestion
import io.github.alexanderop.eisen.app.elm.timerCapSeconds
import io.github.alexanderop.eisen.app.elm.timerWheelSeconds
import io.github.alexanderop.eisen.app.elm.wodAmounts
import io.github.alexanderop.eisen.app.elm.wodLoads
import io.github.alexanderop.eisen.app.state.AppState
import io.github.alexanderop.eisen.app.state.Loaded
import io.github.alexanderop.eisen.app.state.encodeState
import io.github.alexanderop.eisen.app.state.freshState
import io.github.alexanderop.eisen.app.state.loadState
import io.github.alexanderop.eisen.core.Equipment
import io.github.alexanderop.eisen.core.MAX_WEIGHT
import io.github.alexanderop.eisen.core.WeightUnit
import io.github.alexanderop.eisen.feature.timer.MAX_COUNTED_ROUNDS
import io.github.alexanderop.eisen.feature.timer.TimerConfig
import io.github.alexanderop.eisen.feature.timer.TimerMode
import io.github.alexanderop.eisen.feature.timer.boxing.BoxingRound
import io.github.alexanderop.eisen.feature.timer.boxing.BoxingTheme
import io.github.alexanderop.eisen.feature.timer.timerConfig
import io.github.alexanderop.eisen.feature.timer.wod.WodItem
import io.github.alexanderop.eisen.feature.timer.wod.WodUnit
import io.github.alexanderop.eisen.feature.timer.wod.wod
import io.github.alexanderop.eisen.feature.workout.MAX_BAR
import io.github.alexanderop.eisen.feature.workout.MAX_HELD_SECONDS
import io.github.alexanderop.eisen.feature.workout.MAX_HOLD_SECONDS
import io.github.alexanderop.eisen.feature.workout.MAX_INCREMENT
import io.github.alexanderop.eisen.feature.workout.MAX_LOGGED_REPS
import io.github.alexanderop.eisen.feature.workout.MAX_REPS
import io.github.alexanderop.eisen.feature.workout.MAX_REST_SECONDS
import io.github.alexanderop.eisen.feature.workout.MAX_RIR
import io.github.alexanderop.eisen.feature.workout.MAX_SETS
import io.github.alexanderop.eisen.feature.workout.MIN_HOLD_SECONDS
import io.github.alexanderop.eisen.feature.workout.Slot
import io.github.alexanderop.eisen.feature.workout.repsChoices
import io.github.alexanderop.eisen.feature.workout.weightChoices
import io.github.alexanderop.eisen.app.elm.findExercise
import io.github.alexanderop.eisen.app.elm.FoodSheet
import io.github.alexanderop.eisen.app.elm.Model
import io.github.alexanderop.eisen.feature.food.Food
import io.github.alexanderop.eisen.feature.food.FoodSource
import io.github.alexanderop.eisen.feature.food.MAX_AMOUNT
import io.github.alexanderop.eisen.feature.food.MAX_ENTRY_GRAMS
import io.github.alexanderop.eisen.feature.food.MAX_ENTRY_KCAL
import io.github.alexanderop.eisen.feature.food.MAX_FOOD_NAME
import io.github.alexanderop.eisen.feature.food.MAX_FOOD_QUERY
import io.github.alexanderop.eisen.feature.food.MAX_GOAL_KCAL
import io.github.alexanderop.eisen.feature.food.MAX_GOAL_PROTEIN
import io.github.alexanderop.eisen.feature.food.MAX_KCAL_PER_100
import io.github.alexanderop.eisen.feature.food.MAX_MACRO_PER_100
import io.github.alexanderop.eisen.feature.food.MAX_QUICK_KCAL
import io.github.alexanderop.eisen.feature.food.MIN_AMOUNT
import io.github.alexanderop.eisen.feature.food.ProductAnswer
import io.github.alexanderop.eisen.feature.food.SearchAnswer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A number without a limit (QA build 19: 9999999 reps typed into the reps question were saved, and the
 * rest screen built a wheel row for every number up to them and ran out of memory). Every Msg that carries
 * a count, a weight, seconds or rounds gets the largest number there is, and what is saved stays within its
 * field's limit. `RulesTest` checks that every number entry on screen names its maximum and every wheel is
 * built from a bounded list; the builders are checked here.
 */
class LimitsTest {
    private val big = Int.MAX_VALUE
    private val heavy = Double.MAX_VALUE

    /** Workout A with a plank (a hold) at the end: Hack Squat, Bench Press, Barbell Row, Dips (max reps), Plank. */
    private val withPlank = freshState(WeightUnit.KG).let { fresh ->
        fresh.copy(routines = fresh.routines.map { if (it.id == "A") it.copy(slots = it.slots + Slot("plank", 3, 30)) else it })
    }

    /** Every number of [state] that is outside its field's limit. */
    private fun outOfLimits(state: AppState): List<String> = buildList {
        val settings = state.settings
        if (settings.bar !in 0.0..MAX_BAR) add("bar ${settings.bar}")
        if (settings.restDone !in 0..MAX_REST_SECONDS) add("restDone ${settings.restDone}")
        if (settings.restMissed !in 0..MAX_REST_SECONDS) add("restMissed ${settings.restMissed}")
        for ((id, entry) in state.progress) {
            if (entry.weight !in 0.0..MAX_WEIGHT) add("$id weight ${entry.weight}")
            if (entry.increment !in 0.0..MAX_INCREMENT) add("$id increment ${entry.increment}")
        }
        for (routine in state.routines) for (slot in routine.slots) {
            val timed = state.findExercise(slot.exerciseId)?.timed == true
            if (slot.sets !in 1..MAX_SETS) add("${routine.id} ${slot.exerciseId} sets ${slot.sets}")
            val reps = slot.reps
            if (reps != null && reps !in (if (timed) MIN_HOLD_SECONDS..MAX_HOLD_SECONDS else 1..MAX_REPS)) add("${routine.id} ${slot.exerciseId} reps $reps")
        }
        for (workout in listOfNotNull(state.active) + state.history) for (log in workout.exercises) {
            if (log.weight !in 0.0..MAX_WEIGHT) add("${workout.id} ${log.id} weight ${log.weight}")
            val most = if (log.timed) MAX_HELD_SECONDS else MAX_LOGGED_REPS
            for (count in log.sets.filterNotNull()) if (count !in 0..most) add("${workout.id} ${log.id} set $count")
            for (rir in log.rir.filterNotNull()) if (rir !in 0..MAX_RIR) add("${workout.id} ${log.id} rir $rir")
        }
        val configs = state.timerConfigs.values + state.timerPresets.map { it.config } + state.timerSessions.map { it.config }
        for (config in configs) if (config != timerConfig(config)) add("timer $config")
        for (session in state.timerSessions) if (session.rounds !in 0..MAX_COUNTED_ROUNDS) add("${session.name} rounds ${session.rounds}")
        for (saved in state.wods) if (saved != wod(saved)) add("wod $saved")
        addAll(foodOutOfLimits(state))
        addAll(weightOutOfLimits(state))
    }

    private fun assertWithin(step: Step, sent: Any) {
        val broken = outOfLimits(step.state)
        assertTrue("after $sent:\n${broken.joinToString("\n")}", broken.isEmpty())
        step.model.repsQuestion?.let { assertTrue("after $sent: the question starts at ${it.reps}", it.reps in 0..MAX_LOGGED_REPS) }
        step.model.timerSetup?.let { assertEquals("after $sent", timerConfig(it.config), it.config) }
        (step.model.food.sheet as? FoodSheet.Amount)?.let { assertTrue("after $sent: the add sheet is at ${it.amount}", it.amount in MIN_AMOUNT..MAX_AMOUNT) }
        step.model.food.add?.let { assertTrue("after $sent: the search is ${it.query.length} long", it.query.length <= MAX_FOOD_QUERY) }
        assertTrue("after $sent: ${step.model.food.plate.size} foods on the plate", step.model.food.plate.size <= io.github.alexanderop.eisen.feature.food.MAX_PLATE_ITEMS)
        val plate = plateOutOfLimits(step.model.food.plate)
        assertTrue("after $sent:\n${plate.joinToString("\n")}", plate.isEmpty())
        step.model.weight.sheet?.let { sheet ->
            val unit = step.state.settings.unit
            val kg = io.github.alexanderop.eisen.feature.food.bodyFrom(sheet.value, unit)
            assertTrue("after $sent: the weight sheet is at ${sheet.value} $unit", kg in io.github.alexanderop.eisen.feature.food.MIN_BODY_KG - 0.05..io.github.alexanderop.eisen.feature.food.MAX_BODY_KG + 0.05)
        }
    }

    @Test fun `the largest number a weight Msg carries is saved within its field's limit`() {
        for (unit in WeightUnit.entries) {
            val open = Step(Model(freshState(unit), now = LATER)).send(Msg.Weight.Open())
            for (value in listOf(heavy, -heavy, Double.NaN, Double.POSITIVE_INFINITY, Double.MIN_VALUE)) {
                val set = open.send(Msg.Weight.SetValue(value))
                assertWithin(set, "SetValue($value) in $unit")
                assertWithin(set.send(Msg.Weight.Save, id = "w1"), "Save of $value in $unit")
            }
        }
        // A goal pace beyond every limit, with and without a weight to take 1 % of.
        val heavyPace = io.github.alexanderop.eisen.feature.food.WeightGoal(io.github.alexanderop.eisen.feature.food.GoalDirection.GAIN, heavy)
        val goal = Step(Model(freshState(WeightUnit.KG), now = LATER)).send(Msg.Food.Open).send(Msg.Food.OpenGoal)
        assertWithin(goal.send(Msg.Food.SaveGoal(big, big, big, big, heavyPace)), "SaveGoal with a pace")
        val weighed = Step(Model(freshState(WeightUnit.KG).copy(weights = listOf(io.github.alexanderop.eisen.feature.food.WeightEntry("1970-01-01", io.github.alexanderop.eisen.feature.food.MAX_BODY_KG, 0))), now = LATER))
        assertWithin(weighed.send(Msg.Food.Open).send(Msg.Food.OpenGoal).send(Msg.Food.SaveGoal(big, big, big, big, heavyPace.copy(pace = -heavy))), "SaveGoal with a negative pace")
        // A check-in over the largest entries there are: the target it offers is a goal the sheet could set.
        val days = (1..28).map { java.time.LocalDate.parse("2026-10-26").plusDays(it.toLong()).toString() }
        val feast = freshState(WeightUnit.KG).copy(
            weights = days.map { io.github.alexanderop.eisen.feature.food.WeightEntry(it, io.github.alexanderop.eisen.feature.food.MIN_BODY_KG, 0) },
            foodLog = days.map { io.github.alexanderop.eisen.feature.food.FoodEntry("f$it", foodId = "own:feast", name = "Feast", amount = MAX_AMOUNT, kcal = MAX_ENTRY_KCAL, at = 0, day = it) },
            weightGoal = heavyPace.copy(pace = io.github.alexanderop.eisen.feature.food.MAX_PACE_KG),
        )
        val monday = java.time.LocalDate.parse("2026-11-30").atTime(10, 0).toInstant(java.time.ZoneOffset.UTC).toEpochMilli()
        val taken = Step(Model(feast, now = monday)).send(Msg.Food.Open, now = monday).send(Msg.Weight.TakeCheckIn, now = monday)
        assertTrue("the check-in was not taken", taken.state.weightGoal.checkIn?.took == true)
        assertWithin(taken, "TakeCheckIn")
    }

    @Test fun `the largest number a food Msg carries is saved within its field's limit`() {
        val huge = Food("off:3017620422003", "n".repeat(500), "b".repeat(500), "3017620422003", kcal = heavy, protein = heavy, carbs = heavy, fat = heavy, serving = heavy, pack = heavy, source = FoodSource.SCANNED)
        val open = Step(Model(freshState(WeightUnit.KG), now = LATER)).send(Msg.Food.Open)
        val looking = open.send(Msg.Food.Scanned("3017620422003"))
        val found = looking.send(Msg.Food.Looked("3017620422003", ProductAnswer.Found(huge)))
        assertWithin(found, "Looked")
        val amounts = listOf(heavy, -heavy, Double.NaN, Double.POSITIVE_INFINITY).map { found.send(Msg.Food.SetAmount(it)) }
        for (at in amounts) assertWithin(at.send(Msg.Food.ConfirmAmount), "SetAmount and ConfirmAmount")
        val logged = amounts.first().send(Msg.Food.ConfirmAmount, id = "e1")
        assertWithin(logged.send(Msg.Food.AddAgain(huge.id, heavy), id = "e2"), "AddAgain")
        assertWithin(logged.send(Msg.Food.OpenEntry("e1")).send(Msg.Food.SetAmount(heavy)).send(Msg.Food.ConfirmAmount), "an entry's amount")
        val quick = open.send(Msg.Food.OpenQuick)
        assertWithin(quick.send(Msg.Food.SaveQuick(big, big, "q".repeat(500))), "SaveQuick")
        val own = open.send(Msg.Food.NewOwn("x".repeat(500)))
        val savedOwn = own.send(Msg.Food.SaveOwn("x".repeat(500), heavy, heavy, heavy, heavy, heavy), id = "o1")
        assertWithin(savedOwn, "SaveOwn")
        assertWithin(savedOwn.send(Msg.Food.ConfirmAmount, id = "e3"), "SaveOwn and ConfirmAmount")
        // A photo of a label with the largest numbers: what fills the form stays within its limits, or stays empty.
        fun line(text: String, left: Int, top: Int) = io.github.alexanderop.eisen.feature.food.LabelLine(text, io.github.alexanderop.eisen.feature.food.TextBox(left, top, left + 20 * text.length, top + 40))
        val giant = io.github.alexanderop.eisen.feature.food.LabelText(
            listOf(line("pro 100 g", 600, 0), line("Brennwert", 0, 60), line("99999 kJ / 99999 kcal", 600, 60), line("Fett", 0, 120), line("99999 g", 600, 120),
                line("Kohlenhydrate", 0, 180), line("99999 g", 600, 180), line("Eiweiß", 0, 240), line("99,9 g", 600, 240), line("Portion (9999 g)", 0, 300)),
        )
        val photo = open.send(Msg.Food.OpenAdd).send(Msg.Food.ReadLabel()).send(Msg.Food.ChoosePhoto(io.github.alexanderop.eisen.app.elm.PhotoSource.PICKER))
            .send(Msg.Food.PhotoChosen).send(Msg.Food.LabelRecognized(giant))
        val read = (photo.model.food.sheet as? io.github.alexanderop.eisen.app.elm.FoodSheet.Own)?.read
        if (read != null) {
            assertTrue(read.values.filterNotNull().all { it.value in 0.0..io.github.alexanderop.eisen.feature.food.MAX_KCAL_PER_100 })
            assertTrue(listOfNotNull(read.protein, read.carbs, read.fat).all { it.value <= io.github.alexanderop.eisen.feature.food.MAX_MACRO_PER_100 })
            assertTrue((read.serving ?: 0.0) <= io.github.alexanderop.eisen.feature.food.MAX_AMOUNT)
        }
        assertWithin(photo, "LabelRecognized")
        assertWithin(open.send(Msg.Food.OpenGoal).send(Msg.Food.SaveGoal(big, big, big, big)), "SaveGoal")
        val search = open.send(Msg.Food.OpenAdd).send(Msg.Food.Search("s".repeat(500)))
        assertWithin(search, "Search")
        val answered = search.send(Msg.Food.SubmitSearch).send(Msg.Food.Searched("s".repeat(MAX_FOOD_QUERY), SearchAnswer.Found(listOf(io.github.alexanderop.eisen.feature.food.hitOf(huge)))))
        assertWithin(answered.send(Msg.Food.Pick(huge.id)).send(Msg.Food.ConfirmAmount, id = "e4").send(Msg.Food.LogPlate, id = "e5"), "a search result")
        // The plate at its largest: the heaviest foods and quick kcal, as many as fit, and not one more; then logged.
        val fullPlate = (1..io.github.alexanderop.eisen.feature.food.MAX_PLATE_ITEMS + 3).fold(answered) { at, i -> at.send(Msg.Food.PlateAdd(huge.id), id = "p$i") }
        assertEquals(io.github.alexanderop.eisen.feature.food.MAX_PLATE_ITEMS, fullPlate.model.food.plate.size)
        assertWithin(fullPlate, "PlateAdd")
        val quickOnPlate = answered.send(Msg.Food.OpenQuick).send(Msg.Food.SaveQuick(big, big, "q".repeat(500)), id = "pq")
        assertWithin(quickOnPlate, "SaveQuick onto the plate")
        assertWithin(quickOnPlate.send(Msg.Food.OpenPlateItem("pq")).send(Msg.Food.SaveQuick(big, big, "q".repeat(500))), "a quick item changed")
        val heavyItem = fullPlate.send(Msg.Food.OpenPlateItem("p1")).send(Msg.Food.SetAmount(heavy)).send(Msg.Food.ConfirmAmount)
        assertWithin(heavyItem, "a plate item's amount")
        assertWithin(heavyItem.send(Msg.Food.LogPlate, id = "l1"), "LogPlate")
        // A meal of the largest entries, its amounts at the largest number, logged; and the day copied.
        val meal = logged.send(Msg.Food.NewMeal).send(Msg.Food.SaveNewMeal("m".repeat(500)), id = "m1")
        assertWithin(meal, "SaveNewMeal")
        val changed = meal.send(Msg.Food.EditMeal("meal:m1")).send(Msg.Food.SetMealAmount(0, heavy)).send(Msg.Food.SaveMeal("m".repeat(500)))
        assertWithin(changed, "SetMealAmount and SaveMeal")
        assertWithin(changed.send(Msg.Food.AddMeal("meal:m1"), id = "e5"), "AddMeal")
        val yesterday = open.send(Msg.Food.OpenDay(io.github.alexanderop.eisen.feature.food.dayBefore(io.github.alexanderop.eisen.app.elm.today(open.model)))).send(Msg.Food.OpenQuick).send(Msg.Food.SaveQuick(big, big, "q".repeat(500)), id = "y1")
        val copied = yesterday.send(Msg.Food.OpenDay(io.github.alexanderop.eisen.app.elm.today(open.model))).send(Msg.Food.CopyDayBefore, id = "c1")
        assertEquals(2, copied.state.foodLog.size)
        assertWithin(copied, "CopyDayBefore")
        // As many meals as the limit allows, and not one more.
        val full = (1..io.github.alexanderop.eisen.feature.food.MAX_MEALS + 3).fold(logged) { at, i -> at.send(Msg.Food.NewMeal).send(Msg.Food.SaveNewMeal("Meal $i"), id = "m$i") }
        assertEquals(io.github.alexanderop.eisen.feature.food.MAX_MEALS, full.state.meals.size)
        assertWithin(full, "every meal")
    }

    @Test fun `the largest number a Msg carries is saved within its field's limit`() {
        val running = started(withPlank)
        val tapped = running.send(WorkoutEvent.TapSet(0, 0))
        val dips = running.send(WorkoutEvent.LogReps(3, 0, 10))
        val finished = tapped.send(WorkoutEvent.RequestFinish).send(WorkoutEvent.Confirm)
        val history = finished.state.history.first()
        val sends: List<Pair<Step, Msg>> = listOf(
            running to Msg.Workout(WorkoutEvent.LogReps(3, 0, big)),
            running to Msg.WorkoutScreen.PickReps(3, 0, big),
            running to Msg.Workout(WorkoutEvent.LogReps(0, 0, big)),
            running to Msg.WorkoutScreen.PickReps(0, 0, big),
            running to Msg.Workout(WorkoutEvent.LogHold(4, 0, big)),
            running to Msg.Workout(WorkoutEvent.SetWeight(0, heavy)),
            running to Msg.Workout(WorkoutEvent.SetWeight(3, heavy)),
            running to Msg.Workout(WorkoutEvent.SetWorkingWeight("bench", heavy)),
            running to Msg.Workout(WorkoutEvent.SetWorkingWeight("dips", heavy)),
            tapped to Msg.Workout(WorkoutEvent.RateSet(0, 0, big)),
            tapped to Msg.Workout(WorkoutEvent.CorrectReps(0, 0, big)),
            dips to Msg.Workout(WorkoutEvent.CorrectReps(3, 0, big)),
            running to Msg.Settings.SetBar(heavy),
            running to Msg.Settings.SetRestDone(big),
            running to Msg.Settings.SetRestMissed(big),
            running to Msg.Settings.SetIncrement("bench", heavy),
            running to Msg.Routines.UpdateSlot("A", 0, Slot("hackSquat", big, big)),
            running to Msg.Routines.UpdateSlot("A", 4, Slot("plank", big, big)),
            running to Msg.Routines.InsertSlot("B", 0, Slot("bench", big, big)),
            running to Msg.Routines.InsertSlot("B", 0, Slot("plank", big, big)),
            finished to Msg.History.Edit(history.id, history.exercises.map { log -> log.copy(weight = heavy, sets = log.sets.map { big }) }),
        )
        for ((step, msg) in sends) assertWithin(step.send(msg, now = LATER + 5_000), msg)

        // A max-reps set whose last time is above the limit (a file from build 19) asks from the limit, not from it.
        val old = started(withPlank).workout.copy(id = "old", exercises = started(withPlank).workout.exercises.map { if (it.id == "dips") it.copy(sets = listOf(9_999_999, null, null)) else it })
        val withOld = started(withPlank.copy(history = listOf(old)))
        for (sent in listOf(WorkoutEvent.TapSet(3, 0), WorkoutEvent.EnterReps(3, 0))) {
            val after = withOld.send(sent)
            assertTrue("$sent: ${after.workout.exercises[3].sets}", after.workout.exercises[3].sets.filterNotNull().all { it in 0..MAX_LOGGED_REPS })
            after.model.repsQuestion?.let { assertTrue("$sent: the question starts at ${it.reps}", it.reps in 0..MAX_LOGGED_REPS) }
        }

        // The timers: every mode's setup, a saved preset, a boxing round, and a result corrected by the largest number.
        for (mode in TimerMode.entries) {
            val open = stepOf().send(Msg.Timer.Open(mode))
            val edited = open.send(Msg.Timer.Edit(TimerConfig(mode, big, big, big)))
            assertWithin(edited, "Edit $mode")
            assertWithin(edited.send(Msg.Timer.SavePreset("Big")), "SavePreset $mode")
        }
        val boxing = stepOf().send(Msg.Timer.Open(TimerMode.BOXING))
        assertWithin(boxing.send(Msg.Timer.EditRound(0, BoxingRound(BoxingTheme.JAB, big, big))), "EditRound")
        val amrap = stepOf().send(Msg.Timer.Open(TimerMode.AMRAP)).send(Msg.Timer.Start, now = STARTED_AT)
            .send(Msg.Timer.Tap, now = STARTED_AT + 15_000).send(Msg.Timer.SaveAndEnd, now = STARTED_AT + 16_000)
        assertTrue(amrap.state.timerSessions.isNotEmpty())
        assertWithin(amrap.send(Msg.Timer.CorrectRounds(big), now = STARTED_AT + 20_000), "CorrectRounds")

        // A WOD in the builder, then saved.
        val built = stepOf().send(Msg.Wods.New).send(Msg.Wods.AddItem("pushups")).send(Msg.Wods.SetMode(TimerMode.FOR_TIME))
        val bigWod = listOf(
            Msg.Wods.SetTimer(TimerConfig(TimerMode.FOR_TIME, big, big, big)),
            Msg.Wods.SetRounds(big),
            Msg.Wods.SetScheme(listOf(big, big)),
            Msg.Wods.UpdateItem(0, WodItem("pushups", big, WodUnit.REPS, heavy)),
            Msg.Wods.Save,
        ).fold(built) { at, msg -> at.send(msg).also { assertWithin(it, msg) } }
        assertTrue("the WOD was saved", bigWod.state.wods.isNotEmpty())
    }

    /** What an older build may have saved loads, and the wheels that show it stay short. */
    @Test fun `a saved count or weight above its limit still loads and shows without a giant wheel`() {
        val logged = started().send(WorkoutEvent.LogReps(3, 0, 10))
        val damaged = logged.state.copy(active = logged.workout.copy(exercises = logged.workout.exercises.map { if (it.id == "dips") it.copy(sets = listOf(9_999_999, null, null), weight = 1e9) else it }))
        val loaded = loadState(encodeState(damaged))
        assertTrue("$loaded", loaded is Loaded.Ok)
        // The rest screen shows it: its wheels are built from these.
        val dips = (loaded as Loaded.Ok).state.active!!.exercises[3]
        assertEquals(9_999_999, dips.sets[0])
        assertEquals(MAX_LOGGED_REPS + 2, repsChoices(dips.targetReps, 9_999_999).size)
        assertEquals(9_999_999, repsChoices(dips.targetReps, 9_999_999).last())
        assertTrue(weightChoices(Equipment.BODYWEIGHT, WeightUnit.KG, 20.0, 1e9).size <= MAX_WEIGHT.toInt() + 2)
        assertEquals(1e9, weightChoices(Equipment.BODYWEIGHT, WeightUnit.KG, 20.0, 1e9).last(), 0.0)
    }

    /** The builders `RulesTest` lets wheels use: short for any value they show. */
    @Test fun `every wheel builder stays short whatever the value it shows`() {
        for (current in listOf(0, 7, 9_999_999, big)) {
            assertTrue(repsChoices(null, current).size <= MAX_LOGGED_REPS + 2)
            assertTrue(repsChoices(5, current).size <= 7)
            assertTrue(timerWheelSeconds(5, 3600, current).size <= 200)
            assertTrue(timerCapSeconds(current).size <= 63)
            for (unit in WodUnit.entries) assertTrue(wodAmounts(unit, current).size <= 200)
        }
        for (current in listOf(0.0, 1e9, heavy)) {
            for (equipment in Equipment.entries) for (unit in WeightUnit.entries) assertTrue("$equipment $unit $current", weightChoices(equipment, unit, 20.0, current).size <= MAX_WEIGHT.toInt() + 2)
            for (unit in WeightUnit.entries) assertTrue(wodLoads(Equipment.BARBELL, unit, current).size <= 200)
        }
    }
}

/** Every number of the food log outside its limit. The random walk of `UpdateTest` checks it after every Msg too. */
/** What on the plate is beyond a limit: each item in the limits of an entry, and of a food. */
internal fun plateOutOfLimits(plate: List<io.github.alexanderop.eisen.feature.food.PlateItem>): List<String> =
    plate.flatMap { item -> foodOutOfLimits(freshState(WeightUnit.KG).copy(foods = listOfNotNull(item.food), foodLog = listOf(item.entry))) }

/** Every number of the weight log outside its limit: one weight a day, in order, each a body weight; a pace it can have. */
internal fun weightOutOfLimits(state: AppState): List<String> = buildList {
    val days = state.weights.map { it.day }
    if (days != days.distinct().sorted()) add("weights not one a day in order: $days")
    for (entry in state.weights) if (entry.kg !in io.github.alexanderop.eisen.feature.food.MIN_BODY_KG..io.github.alexanderop.eisen.feature.food.MAX_BODY_KG) add("weight ${entry.day} ${entry.kg}")
    val goal = state.weightGoal
    if (goal.pace !in 0.0..io.github.alexanderop.eisen.feature.food.MAX_PACE_KG) add("pace ${goal.pace}")
    if (goal.direction == io.github.alexanderop.eisen.feature.food.GoalDirection.MAINTAIN && goal.pace != 0.0) add("a pace of ${goal.pace} to maintain")
    goal.checkIn?.let { if (it.target !in 0..MAX_GOAL_KCAL) add("check-in target ${it.target}") }
}

internal fun foodOutOfLimits(state: AppState): List<String> = buildList {
    for (food in state.foods) {
        if (food.kcal !in 0.0..MAX_KCAL_PER_100) add("${food.id} kcal ${food.kcal}")
        for (macro in listOfNotNull(food.protein, food.carbs, food.fat)) if (macro !in 0.0..MAX_MACRO_PER_100) add("${food.id} macro $macro")
        for (amount in listOfNotNull(food.serving, food.pack)) if (amount !in MIN_AMOUNT..MAX_AMOUNT) add("${food.id} amount $amount")
        if (food.name.length > MAX_FOOD_NAME) add("${food.id} name ${food.name.length}")
    }
    for (entry in state.foodLog) {
        if (entry.kcal !in 0..MAX_ENTRY_KCAL) add("${entry.id} kcal ${entry.kcal}")
        for (grams in listOfNotNull(entry.protein, entry.carbs, entry.fat)) if (grams !in 0.0..MAX_ENTRY_GRAMS) add("${entry.id} grams $grams")
        entry.amount?.let { if (it !in MIN_AMOUNT..MAX_AMOUNT) add("${entry.id} amount $it") }
        if (entry.foodId == null && entry.kcal > MAX_QUICK_KCAL) add("${entry.id} quick kcal ${entry.kcal}")
        if (entry.name.length > MAX_FOOD_NAME) add("${entry.id} name ${entry.name.length}")
    }
    if (state.meals.size > io.github.alexanderop.eisen.feature.food.MAX_MEALS) add("${state.meals.size} meals")
    for (meal in state.meals) {
        if (meal.items.size !in 1..io.github.alexanderop.eisen.feature.food.MAX_MEAL_ITEMS) add("${meal.id} ${meal.items.size} items")
        if (meal.name.isBlank() || meal.name.length > io.github.alexanderop.eisen.feature.food.MAX_MEAL_NAME) add("${meal.id} name ${meal.name.length}")
        for (item in meal.items) {
            if (item.kcal !in 0..MAX_ENTRY_KCAL) add("${meal.id} ${item.name} kcal ${item.kcal}")
            for (grams in listOfNotNull(item.protein, item.carbs, item.fat)) if (grams !in 0.0..MAX_ENTRY_GRAMS) add("${meal.id} ${item.name} grams $grams")
            item.amount?.let { if (it !in MIN_AMOUNT..MAX_AMOUNT) add("${meal.id} ${item.name} amount $it") }
            if (item.name.length > MAX_FOOD_NAME) add("${meal.id} item name ${item.name.length}")
        }
    }
    state.foodGoal.kcal?.let { if (it !in 1..MAX_GOAL_KCAL) add("goal kcal $it") }
    state.foodGoal.protein?.let { if (it !in 1..MAX_GOAL_PROTEIN) add("goal protein $it") }
    state.foodGoal.carbs?.let { if (it !in 1..io.github.alexanderop.eisen.feature.food.MAX_GOAL_CARBS) add("goal carbs $it") }
    state.foodGoal.fat?.let { if (it !in 1..io.github.alexanderop.eisen.feature.food.MAX_GOAL_FAT) add("goal fat $it") }
}

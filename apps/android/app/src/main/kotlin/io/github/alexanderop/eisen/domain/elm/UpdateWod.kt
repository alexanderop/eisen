package io.github.alexanderop.eisen.domain.elm

import io.github.alexanderop.eisen.domain.DEFAULT_TIMERS
import io.github.alexanderop.eisen.domain.Env
import io.github.alexanderop.eisen.domain.Equipment
import io.github.alexanderop.eisen.domain.Exercise
import io.github.alexanderop.eisen.domain.MAX_WOD_ITEMS
import io.github.alexanderop.eisen.domain.MAX_WOD_NAME
import io.github.alexanderop.eisen.domain.TimerMode
import io.github.alexanderop.eisen.domain.WOD_LIBRARY
import io.github.alexanderop.eisen.domain.WeightUnit
import io.github.alexanderop.eisen.domain.Wod
import io.github.alexanderop.eisen.domain.WodItem
import io.github.alexanderop.eisen.domain.WodUnit
import io.github.alexanderop.eisen.domain.findExercise
import io.github.alexanderop.eisen.domain.findWod
import io.github.alexanderop.eisen.domain.loadIn
import io.github.alexanderop.eisen.domain.wod
import io.github.alexanderop.eisen.domain.sameWork
import io.github.alexanderop.eisen.domain.wodName

/*
 * The WODs: the list with its filter, a WOD's detail, starting one, and the builder. The builder edits a
 * draft in the model; the state only changes on Save, Copy and Delete. A run of a WOD is an interval
 * timer run with the WOD copied in, see `startTimer` in UpdateTimer.kt.
 */

const val WOD_SNACKBAR = "wod"

/** What a copy of a benchmark is called. */
fun copyName(name: String): String = "$name (mine)"

internal fun wods(msg: Msg.Wods, model: Model, env: Env): Next {
    val wods = model.wods
    val draft = wods.draft
    return when (msg) {
        is Msg.Wods.Filter -> if (msg.filter == wods.filter) model.only() else model.copy(wods = wods.copy(filter = msg.filter)).only()
        is Msg.Wods.Open -> {
            if (findWod(model.state.wods, msg.id) == null || wods.detail == msg.id) return model.only()
            model.copy(wods = wods.copy(detail = msg.id), nav = model.nav.copy(tab = Tab.TIMER), now = env.now).only()
        }
        Msg.Wods.Close -> if (wods.detail == null) model.only() else model.copy(wods = wods.copy(detail = null), now = env.now).only()
        is Msg.Wods.Start -> {
            val wod = findWod(model.state.wods, msg.id) ?: return model.only()
            startWod(model, wod, env)
        }
        // A builder that is open keeps its draft: these only come from the list and the detail.
        Msg.Wods.New -> {
            if (draft != null) return model.only()
            val blank = Wod(env.newId(), "", DEFAULT_TIMERS.getValue(TimerMode.FOR_TIME), 1, emptyList(), emptyList())
            model.copy(wods = wods.copy(draft = WodDraft(blank, blank)), nav = model.nav.copy(tab = Tab.TIMER), now = env.now).only()
        }
        is Msg.Wods.Edit -> {
            val own = model.state.wods.firstOrNull { it.id == msg.id }
            if (draft != null || own == null) return model.only()
            model.copy(wods = wods.copy(detail = own.id, draft = WodDraft(own, own)), now = env.now).only()
        }
        // A new WOD like the benchmark: nothing is stored until Save, so backing out leaves nothing behind.
        is Msg.Wods.Copy -> {
            val source = WOD_LIBRARY.firstOrNull { it.id == msg.id }
            if (draft != null || source == null) return model.only()
            val copy = wod(source.copy(id = env.newId(), name = copyName(wodName(source))))
            model.copy(wods = wods.copy(draft = WodDraft(copy, copy)), now = env.now).only()
        }
        Msg.Wods.ContinueDraft -> {
            val pending = wods.pending
            if (draft != null || pending == null) return model.only()
            val detail = pending.wod.id.takeIf { id -> model.state.wods.any { it.id == id } } ?: wods.detail
            model.copy(wods = wods.copy(draft = pending, pending = null, detail = detail), nav = model.nav.copy(tab = Tab.TIMER), now = env.now).only()
        }
        Msg.Wods.DiscardDraft -> if (wods.pending == null) model.only() else model.copy(wods = wods.copy(pending = null)).only()
        is Msg.Wods.Delete -> {
            val index = model.state.wods.indexOfFirst { it.id == msg.id }
            if (index == -1) return model.only()
            val gone = model.state.wods[index]
            val left = wods.copy(detail = wods.detail.takeIf { it != msg.id }, draft = draft?.takeIf { it.wod.id != msg.id })
            model.withState { state -> state.copy(wods = state.wods.filter { it.id != msg.id }) }
                .copy(wods = left, undo = model.undo.copy(deletedWod = DeletedWod(gone, index)), now = env.now) and
                Cmd.Ui(UiEvent.Snackbar("Deleted ${wodName(gone)}", actionLabel = "Undo", onAction = Msg.Wods.UndoDelete, tag = WOD_SNACKBAR))
        }
        Msg.Wods.UndoDelete -> {
            val deleted = model.undo.deletedWod ?: return model.only()
            val cleared = model.copy(undo = model.undo.copy(deletedWod = null))
            if (model.state.wods.any { it.id == deleted.wod.id }) return cleared.only()
            cleared.withState { state -> state.copy(wods = state.wods.toMutableList().apply { add(deleted.index.coerceIn(0, size), deleted.wod) }) }.only()
        }
        is Msg.Wods.Rename -> editDraft(model) { it.copy(name = msg.name.take(MAX_WOD_NAME)) }
        is Msg.Wods.SetMode -> if (draft == null || draft.wod.timer.mode == msg.mode) model.only() else setMode(model, draft, msg.mode)
        is Msg.Wods.SetTimer ->
            if (draft == null || draft.wod.timer.mode != msg.config.mode) model.only()
            else editDraft(model) { it.copy(timer = msg.config) }
        // With a scheme the scheme says how many rounds there are.
        is Msg.Wods.SetRounds -> if (draft == null || draft.wod.scheme.isNotEmpty()) model.only() else editDraft(model) { it.copy(rounds = msg.rounds) }
        is Msg.Wods.SetScheme -> editDraft(model) { it.copy(scheme = msg.scheme) }
        is Msg.Wods.AddItem -> wods(Msg.Wods.AddItems(listOf(msg.exerciseId)), model, env)
        is Msg.Wods.AddItems -> {
            if (draft == null) return model.only()
            val tabata = draft.wod.timer.mode == TimerMode.TABATA
            val added = msg.exerciseIds.mapNotNull { id -> model.state.findExercise(id)?.let { startItem(it, max = tabata) } }
            editDraft(model) { it.copy(items = (it.items + added).take(MAX_WOD_ITEMS)) }
        }
        is Msg.Wods.UpdateItem ->
            if (draft?.wod?.items?.getOrNull(msg.index)?.exerciseId != msg.item.exerciseId) model.only()
            else editDraft(model) { it.copy(items = it.items.mapIndexed { at, item -> if (at == msg.index) msg.item else item }) }
        is Msg.Wods.MoveItem -> editDraft(model) { it.copy(items = it.items.moved(msg.from, msg.to)) }
        is Msg.Wods.RemoveItem ->
            if (draft?.wod?.items?.getOrNull(msg.index)?.exerciseId != msg.exerciseId) model.only()
            else editDraft(model) { it.copy(items = it.items.filterIndexed { at, _ -> at != msg.index }) }
        Msg.Wods.Save -> {
            if (draft == null || draft.wod.items.isEmpty()) return model.only()
            val built = wod(draft.wod)
            val old = model.state.wods.firstOrNull { it.id == built.id }
            // Other work is another WOD: its old times are not something to beat any more.
            val saved = if (old != null && !sameWork(old, built)) built.copy(id = env.newId()) else built
            model.withState { state -> state.copy(wods = if (old != null) state.wods.map { if (it.id == old.id) saved else it } else state.wods + saved) }
                .copy(wods = wods.copy(detail = saved.id, draft = null), now = env.now).only()
        }
        Msg.Wods.Leave -> when {
            draft == null || draft.asking -> model.only()
            draftChanged(draft) -> model.copy(wods = wods.copy(draft = draft.copy(asking = true))).only()
            else -> model.copy(wods = wods.copy(draft = null), now = env.now).only()
        }
        Msg.Wods.KeepEditing -> if (draft?.asking != true) model.only() else model.copy(wods = wods.copy(draft = draft.copy(asking = false))).only()
        Msg.Wods.Discard -> if (draft == null) model.only() else model.copy(wods = wods.copy(draft = null), now = env.now).only()
    }
}

/**
 * The draft after [change], in the limits of a WOD except for its name, which keeps what was typed:
 * `wod` would trim it and fill in a blank one. The same model when nothing changed.
 */
private fun editDraft(model: Model, change: (Wod) -> Wod): Next {
    val draft = model.wods.draft ?: return model.only()
    val changed = change(draft.wod)
    val next = wod(changed).copy(name = changed.name)
    if (next == draft.wod) return model.only()
    return model.copy(wods = model.wods.copy(draft = draft.copy(wod = next))).only()
}

/**
 * Another type for the draft. The numbers of the one it leaves are kept and the ones of the type it
 * goes to come back (or start at their defaults). Max only exists in a Tabata: leaving one, an item
 * done as often as possible starts at its unit's start.
 */
private fun setMode(model: Model, draft: WodDraft, mode: TimerMode): Next {
    val current = draft.wod
    val leaving = current.timer.mode
    val forTime = leaving == TimerMode.FOR_TIME
    val kept = draft.kept + (leaving to current.timer)
    val keptRounds = if (forTime) current.rounds else draft.keptRounds
    val keptScheme = if (forTime) current.scheme else draft.keptScheme
    val items = if (leaving == TimerMode.TABATA) current.items.map { if (it.amount == 0) withMax(it, false) else it } else current.items
    val next = current.copy(
        timer = kept[mode] ?: DEFAULT_TIMERS.getValue(mode),
        rounds = if (mode == TimerMode.FOR_TIME) keptRounds else 1,
        scheme = if (mode == TimerMode.FOR_TIME) keptScheme else emptyList(),
        items = items,
    )
    val changed = draft.copy(wod = wod(next).copy(name = current.name), kept = kept, keptRounds = keptRounds, keptScheme = keptScheme)
    return model.copy(wods = model.wods.copy(draft = changed)).only()
}

/** Leaving the builder would lose something. */
fun draftChanged(draft: WodDraft): Boolean = draft.wod != draft.start

/** The ids that count distance: running and rowing start at 400 metres. */
private val DISTANCE_EXERCISES = setOf("run", "rowErg")

/** How much an item starts with in [unit]: ten reps, 30 seconds, 400 metres. */
fun startAmount(unit: WodUnit): Int = when (unit) {
    WodUnit.REPS -> 10
    WodUnit.SECONDS -> 30
    WodUnit.METERS -> 400
}

/**
 * An exercise added in the builder: counted the way it is done, with its starting weight unless it is
 * a bodyweight one. [max]: in a Tabata it is done as often as possible in each round.
 */
fun startItem(exercise: Exercise, max: Boolean = false): WodItem {
    val unit = when {
        exercise.id in DISTANCE_EXERCISES -> WodUnit.METERS
        exercise.timed -> WodUnit.SECONDS
        else -> WodUnit.REPS
    }
    return WodItem(exercise.id, if (max) 0 else startAmount(unit), unit, if (exercise.equipment == Equipment.BODYWEIGHT) 0.0 else exercise.start)
}

/** The item counted in [unit]: a number starts over at that unit's start, "Max" stays max. */
fun withUnit(item: WodItem, unit: WodUnit): WodItem =
    if (unit == item.unit) item else item.copy(unit = unit, amount = if (item.amount == 0) 0 else startAmount(unit))

/** "Max" on or off: 0 is as many as possible, off starts at the unit's start. */
fun withMax(item: WodItem, max: Boolean): WodItem = item.copy(amount = if (max) 0 else startAmount(item.unit))

private val METERS = listOf(100, 200, 400, 500, 800, 1000, 1600, 2000, 5000)

/**
 * The amounts the item editor's wheel offers: reps one by one to 30, then by 5 to 100, by 25 to 500 and
 * by 100 to 1000; seconds like the timer wheels; the usual distances. [current] is always among them.
 */
fun wodAmounts(unit: WodUnit, current: Int): List<Int> {
    val grid = when (unit) {
        WodUnit.REPS -> (1..30) + (35..100 step 5) + (125..500 step 25) + (600..1000 step 100)
        WodUnit.SECONDS -> timerWheelSeconds(5, 600, maxOf(5, current))
        WodUnit.METERS -> METERS
    }
    return (grid + current).filter { it > 0 }.distinct().sorted()
}

/**
 * The loads the item editor's wheel offers in the app's unit, 0 for none: kettlebells and dumbbells by
 * the kilo up to 40 kg, bars and plates in 2.5 kg steps, 5 lb steps in pounds. [opened] (in kg), the
 * load when the sheet opened, stays among them, so turning the wheel can always go back to it.
 */
fun wodLoads(equipment: Equipment, unit: WeightUnit, opened: Double): List<Double> {
    val grid = when {
        unit == WeightUnit.LB -> (0..88).map { it * 5.0 }
        equipment == Equipment.WEIGHT -> (0..40).map { it.toDouble() } + (17..80).map { it * 2.5 }
        else -> (0..80).map { it * 2.5 }
    }
    return (grid + loadIn(opened, unit)).distinct().sorted()
}

/** "As many reps as possible each round": what Max means for the item's unit. */
fun maxHint(unit: WodUnit): String = when (unit) {
    WodUnit.REPS -> "As many reps as possible each round"
    WodUnit.SECONDS -> "As many seconds as possible each round"
    WodUnit.METERS -> "As many metres as possible each round"
}

/** A custom scheme as typed, "30-20-10" or "30 20 10": the numbers in it, in order. */
fun parseScheme(text: String): List<Int> = text.split(Regex("[^0-9]+")).mapNotNull { it.toIntOrNull() }.filter { it > 0 }

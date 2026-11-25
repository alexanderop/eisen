package io.github.alexanderop.eisen.app.elm

import io.github.alexanderop.eisen.feature.timer.wod.MAX_SCHEME_LENGTH
import io.github.alexanderop.eisen.feature.timer.wod.MAX_SCHEME_REPS
import io.github.alexanderop.eisen.feature.timer.WOD_MODES
import io.github.alexanderop.eisen.feature.timer.DEFAULT_TIMERS
import io.github.alexanderop.eisen.core.Equipment
import io.github.alexanderop.eisen.core.Exercise
import io.github.alexanderop.eisen.feature.timer.wod.MAX_WOD_ITEMS
import io.github.alexanderop.eisen.feature.timer.wod.MAX_WOD_NAME
import io.github.alexanderop.eisen.feature.timer.wod.SCHEMES
import io.github.alexanderop.eisen.feature.timer.TimerMode
import io.github.alexanderop.eisen.feature.timer.wod.WOD_LIBRARY
import io.github.alexanderop.eisen.core.WeightUnit
import io.github.alexanderop.eisen.feature.timer.wod.Wod
import io.github.alexanderop.eisen.feature.timer.wod.WodItem
import io.github.alexanderop.eisen.feature.timer.wod.WodUnit
import io.github.alexanderop.eisen.feature.timer.wod.findWod
import io.github.alexanderop.eisen.feature.timer.wod.loadIn
import io.github.alexanderop.eisen.feature.timer.wod.wod
import io.github.alexanderop.eisen.feature.timer.wod.sameWork
import io.github.alexanderop.eisen.feature.timer.wod.wodName
import io.github.alexanderop.eisen.app.state.StateJson
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/*
 * The WODs: the list with its filter, a WOD's detail, starting one, and the builder. The builder edits a
 * draft in the model; the state only changes on Save, Copy and Delete. A run of a WOD is an interval
 * timer run with the WOD copied in, see `startTimer` in UpdateTimer.kt.
 */

const val WOD_SNACKBAR = "wod"

/** The longest search the field takes: longer than any name it could find. */
const val MAX_QUERY = 60

/** What a copy of a benchmark is called. */
fun copyName(name: String): String = "$name (mine)"

internal fun wods(msg: Msg.Wods, model: Model, env: Env): Next {
    val wods = model.wods
    val draft = wods.draft
    return when (msg) {
        is Msg.Wods.Filter -> if (msg.filter == wods.filter) model.only() else model.wods { it.copy(filter = msg.filter) }.only()
        is Msg.Wods.Open -> {
            if (findWod(model.state.wods, msg.id) == null || wods.detail == msg.id) return model.only()
            val searched = if (msg.suggested) wods.copy(query = "") else wods
            model.nav { it.copy(tab = Tab.TIMER) }.copy(wods = searched.copy(detail = msg.id, suggesting = false), now = env.now).only()
        }
        is Msg.Wods.Search -> {
            val query = msg.query.take(MAX_QUERY)
            val next = wods.copy(query = query, suggesting = query.isNotBlank())
            if (next == wods) model.only() else model.copy(wods = next).only()
        }
        Msg.Wods.CloseSuggestions -> if (!wods.suggesting) model.only() else model.wods { it.copy(suggesting = false) }.only()
        is Msg.Wods.SearchMovement -> {
            val next = wods.copy(query = msg.name.take(MAX_QUERY), suggesting = false)
            if (next == wods) model.only() else model.copy(wods = next).only()
        }
        Msg.Wods.ClearSearch -> if (wods.query.isEmpty() && !wods.suggesting) model.only() else model.wods { it.copy(query = "", suggesting = false) }.only()
        Msg.Wods.Close -> if (wods.detail == null) model.only() else model.wods { it.copy(detail = null) }.copy(now = env.now).only()
        is Msg.Wods.Start -> {
            val wod = findWod(model.state.wods, msg.id) ?: return model.only()
            startWod(model, wod, env)
        }
        // A builder that is open keeps its draft: these only come from the list and the detail. Drafts kept
        // from before wait under the new one.
        Msg.Wods.New -> {
            if (draft != null) return model.only()
            val blank = Wod(env.newId(), "", DEFAULT_TIMERS.getValue(TimerMode.FOR_TIME), 1, emptyList(), emptyList())
            model.build(BuilderEvent.Open(WodDraft(blank, blank))).nav { it.copy(tab = Tab.TIMER) }.copy(now = env.now).only()
        }
        is Msg.Wods.Edit -> {
            val own = model.state.wods.firstOrNull { it.id == msg.id }
            if (draft != null || own == null) return model.only()
            model.build(BuilderEvent.Open(WodDraft(own, own))).wods { it.copy(detail = own.id) }.copy(now = env.now).only()
        }
        // A new WOD like the benchmark: nothing is stored until Save, so backing out leaves nothing behind.
        is Msg.Wods.Copy -> {
            val source = WOD_LIBRARY.firstOrNull { it.id == msg.id }
            if (draft != null || source == null) return model.only()
            val copy = wod(source.copy(id = env.newId(), name = copyName(wodName(source))))
            model.build(BuilderEvent.Open(WodDraft(copy, copy))).copy(now = env.now).only()
        }
        Msg.Wods.ContinueDraft -> {
            val pending = wods.pending ?: return model.only()
            val detail = pending.wod.id.takeIf { id -> model.state.wods.any { it.id == id } } ?: wods.detail
            model.build(BuilderEvent.Continue).wods { it.copy(detail = detail) }.nav { it.copy(tab = Tab.TIMER) }.copy(now = env.now).only()
        }
        Msg.Wods.DiscardDraft -> model.build(BuilderEvent.DropKept).only()
        is Msg.Wods.Delete -> {
            val index = model.state.wods.indexOfFirst { it.id == msg.id }
            if (index == -1) return model.only()
            val gone = model.state.wods[index]
            // A builder that edited the deleted WOD closes with it.
            val builder = if (draft?.wod?.id == msg.id) builderAfter(wods.builder, BuilderEvent.Close) else wods.builder
            val left = wods.copy(detail = wods.detail.takeIf { it != msg.id }, builder = builder)
            model.withState { state -> state.copy(wods = state.wods.filter { it.id != msg.id }) }
                .undo { it.copy(deletedWod = DeletedWod(gone, index)) }.copy(wods = left, now = env.now) and
                Cmd.Ui(UiEvent.Snackbar("Deleted ${wodName(gone)}", actionLabel = "Undo", onAction = Msg.Wods.UndoDelete, tag = WOD_SNACKBAR))
        }
        Msg.Wods.UndoDelete -> {
            val deleted = model.undo.deletedWod ?: return model.only()
            val cleared = model.undo { it.copy(deletedWod = null) }
            if (model.state.wods.any { it.id == deleted.wod.id }) return cleared.only()
            cleared.withState { state -> state.copy(wods = state.wods.toMutableList().apply { add(deleted.index.coerceIn(0, size), deleted.wod) }) }.only()
        }
        is Msg.Wods.Rename -> editDraft(model) { it.copy(name = msg.name.take(MAX_WOD_NAME)) }
        // A WOD is built on one of the WOD modes only: boxing has its own plan instead of movements.
        is Msg.Wods.SetMode -> if (draft == null || draft.wod.timer.mode == msg.mode || msg.mode !in WOD_MODES) model.only() else setMode(model, draft, msg.mode)
        is Msg.Wods.SetTimer ->
            if (draft == null || draft.wod.timer.mode != msg.config.mode || msg.config.mode !in WOD_MODES) model.only()
            else editDraft(model) { it.copy(timer = msg.config) }
        // With a scheme the scheme says how many rounds there are.
        is Msg.Wods.SetRounds -> if (draft == null || draft.wod.scheme.isNotEmpty()) model.only() else editDraft(model) { it.copy(rounds = msg.rounds) }
        // A chip: the custom field closes and keeps its text for the next time it opens.
        is Msg.Wods.SetScheme -> if (draft == null) model.only() else editScheme(model, draft, typed = null, msg.scheme)
        Msg.Wods.ChooseCustom -> {
            if (draft == null || draft.typed != null) return model.only()
            val text = customText(draft) ?: draft.keptTyped
            editScheme(model, draft, typed = text, parseScheme(text))
        }
        is Msg.Wods.TypeScheme ->
            if (draft == null || customText(draft) == null) model.only()
            else editScheme(model, draft, typed = msg.text.take(MAX_TYPED_SCHEME), parseScheme(msg.text.take(MAX_TYPED_SCHEME)))
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
                .build(BuilderEvent.Close).wods { it.copy(detail = saved.id) }.copy(now = env.now).only()
        }
        Msg.Wods.Leave -> when (val builder = wods.builder) {
            is Builder.Closed, is Builder.Asking -> model.only()
            is Builder.Editing -> if (draftChanged(builder.open)) model.build(BuilderEvent.Ask).only() else model.build(BuilderEvent.Close).copy(now = env.now).only()
        }
        Msg.Wods.KeepEditing -> model.build(BuilderEvent.KeepEditing).only()
        Msg.Wods.Discard -> if (draft == null) model.only() else model.build(BuilderEvent.Close).copy(now = env.now).only()
    }
}

/** What happens to the builder, see [Builder]. Edits of the draft are not events: they change the draft in its state. */
sealed interface BuilderEvent {
    /** New, Edit or Copy opened [draft]. Only a closed builder opens; the kept drafts wait under it. */
    data class Open(val draft: WodDraft) : BuilderEvent

    /** "Continue editing": the newest kept draft opens. */
    data object Continue : BuilderEvent

    /** The newest kept draft is discarded. */
    data object DropKept : BuilderEvent

    /** "Discard changes?" is asked. */
    data object Ask : BuilderEvent

    data object KeepEditing : BuilderEvent

    /** Saved, discarded, left without changes, or its WOD was deleted: the draft goes, the kept ones are offered again. */
    data object Close : BuilderEvent
}

/** The builder after [event]: the one place its state changes. An event a state does not take leaves it as it is. */
fun builderAfter(builder: Builder, event: BuilderEvent): Builder = when (event) {
    is BuilderEvent.Open -> if (builder is Builder.Closed) Builder.Editing(event.draft, builder.kept) else builder
    BuilderEvent.Continue ->
        if (builder is Builder.Closed && builder.kept.isNotEmpty()) Builder.Editing(builder.kept.first(), builder.kept.drop(1)) else builder
    BuilderEvent.DropKept ->
        if (builder is Builder.Closed && builder.kept.isNotEmpty()) Builder.Closed(builder.kept.drop(1)) else builder
    BuilderEvent.Ask -> if (builder is Builder.Editing) Builder.Asking(builder.open, builder.kept) else builder
    BuilderEvent.KeepEditing -> if (builder is Builder.Asking) Builder.Editing(builder.open, builder.kept) else builder
    BuilderEvent.Close -> when (builder) {
        is Builder.Closed -> builder
        is Builder.Editing -> Builder.Closed(builder.kept)
        is Builder.Asking -> Builder.Closed(builder.kept)
    }
}

/** [model] with [event] applied to its builder. */
private fun Model.build(event: BuilderEvent): Model {
    val next = builderAfter(wods.builder, event)
    return if (next == wods.builder) this else wods { it.copy(builder = next) }
}

/** The builder with its draft changed, in the state it is in: open, or asking. */
private fun Builder.withDraft(draft: WodDraft): Builder = when (this) {
    is Builder.Closed -> this
    is Builder.Editing -> copy(open = draft)
    is Builder.Asking -> copy(open = draft)
}

/**
 * What the draft file holds for [builder]: the open draft first, then every kept one, newest first. Empty
 * deletes the file. Written whenever it changes (see `follow`), so a draft is never only in memory.
 */
fun draftFile(builder: Builder): List<WodDraft> = (listOfNotNull(builder.draft) + builder.kept).map { it.copy(typed = null, keptTyped = "") }

/** The key under which the draft file keeps the drafts after the first. Build 18 wrote one draft and no key. */
private const val UNDER = "under"

/**
 * The draft file's text for [drafts]: the first draft as build 18 wrote its one draft, the others in a
 * list under it. One draft is written exactly as before. Null for none: the file goes.
 */
fun encodeDrafts(drafts: List<WodDraft>): String? {
    val first = drafts.firstOrNull() ?: return null
    val top = StateJson.encodeToJsonElement(WodDraft.serializer(), first).jsonObject
    val under = drafts.drop(1)
    if (under.isEmpty()) return top.toString()
    return JsonObject(top + (UNDER to StateJson.encodeToJsonElement(ListSerializer(WodDraft.serializer()), under))).toString()
}

/** The drafts in the draft file's [text], written by [encodeDrafts] or by build 18. Fails on a file that is no draft. */
fun decodeDrafts(text: String): List<WodDraft> {
    val element = StateJson.parseToJsonElement(text)
    val first = StateJson.decodeFromJsonElement(WodDraft.serializer(), element)
    val under = element.jsonObject[UNDER]?.let { StateJson.decodeFromJsonElement(ListSerializer(WodDraft.serializer()), it) }.orEmpty()
    return listOf(first) + under
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
    return model.wods { it.copy(builder = it.builder.withDraft(draft.copy(wod = next))) }.only()
}

/**
 * The draft with [scheme] and the custom field open with [typed] (null: closed). A field that closes keeps
 * its text in [WodDraft.keptTyped]. The same model when nothing changed.
 */
private fun editScheme(model: Model, draft: WodDraft, typed: String?, scheme: List<Int>): Next {
    val next = wod(draft.wod.copy(scheme = scheme)).copy(name = draft.wod.name)
    val changed = draft.copy(wod = next, typed = typed, keptTyped = if (typed == null) draft.typed ?: draft.keptTyped else draft.keptTyped)
    if (changed == draft) return model.only()
    return model.wods { it.copy(builder = it.builder.withDraft(changed)) }.only()
}

/** The chip a scheme lights, see [WodDraft]. */
fun schemeChoice(scheme: List<Int>): SchemeChoice = when (scheme) {
    emptyList<Int>() -> SchemeChoice.Same
    in SCHEMES -> SchemeChoice.Preset(scheme)
    else -> SchemeChoice.Custom(scheme)
}

/**
 * The chip that is lit. While the custom field is open it is "Custom": the lifter is typing a scheme, and
 * [schemeNote] says under the field what Save saves while that is not what the field holds.
 */
fun schemeChip(draft: WodDraft): SchemeChoice = if (draft.typed != null) SchemeChoice.Custom(draft.wod.scheme) else schemeChoice(draft.wod.scheme)

/**
 * What the custom field says under it when the numbers in it are not the scheme Save saves (QA build 19:
 * "21-0-15-0-9" showed while 21-15-9 was saved, "0-0-99999" saved 999). Null while they agree.
 */
fun schemeNote(draft: WodDraft): String? {
    val typed = draft.typed ?: return null
    val numbers = typed.split(Regex("[^0-9]+")).filter { it.isNotEmpty() }.map { it.trimStart('0').ifEmpty { "0" } }
    val saved = draft.wod.scheme
    if (numbers == saved.map { it.toString() }) return null
    val limits = "1 to $MAX_SCHEME_REPS reps a round, $MAX_SCHEME_LENGTH rounds at most"
    return if (saved.isEmpty()) "Saves the same reps each round: $limits" else "Saves ${saved.joinToString("-")}: $limits"
}

/** The text of the custom field, null while it is closed: as typed, or a custom scheme as the WOD has it. */
fun customText(draft: WodDraft): String? =
    draft.typed ?: draft.wod.scheme.takeIf { schemeChoice(it) is SchemeChoice.Custom }?.joinToString("-")

/** The custom field takes this many characters. */
private const val MAX_TYPED_SCHEME = 48

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
    // The custom field closes; a custom scheme that comes back with For Time opens it again with its numbers.
    val changed = draft.copy(
        wod = wod(next).copy(name = current.name),
        kept = kept,
        keptRounds = keptRounds,
        keptScheme = keptScheme,
        typed = null,
        keptTyped = draft.typed ?: draft.keptTyped,
    )
    return model.wods { it.copy(builder = it.builder.withDraft(changed)) }.only()
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

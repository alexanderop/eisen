package io.github.alexanderop.eisen.app.elm

import io.github.alexanderop.eisen.app.state.StateJson
import io.github.alexanderop.eisen.core.counted
import io.github.alexanderop.eisen.feature.food.FoodSource
import io.github.alexanderop.eisen.feature.food.MAX_PLATE_ITEMS
import io.github.alexanderop.eisen.feature.food.PlateItem
import io.github.alexanderop.eisen.feature.food.entriesOfPlate
import io.github.alexanderop.eisen.feature.food.plateItemWithin
import kotlinx.serialization.Serializable

/*
 * The add screen and the plate: foods collected one after the other, logged with one button. The add screen and its
 * run of barcodes change only in [addAfter], the plate only in [plateAfter].
 */

/** What happens to the add screen, see [AddScreen]. */
internal sealed interface AddEvent {
    data object Open : AddEvent

    data object Close : AddEvent

    /** "Scan" on the add screen: the scanner runs, one food after the other. */
    data object Scan : AddEvent

    /** "Type a barcode" on the add screen: the barcodes are typed, one after the other. */
    data object Type : AddEvent

    /** The scanner gave nothing, for [why]; [note] says it on the sheet to type the barcode. */
    data class ScanFailed(val why: ScanFailure, val note: String) : AddEvent

    /** A food of a run, [name], landed on the plate. */
    data class Landed(val name: String) : AddEvent

    /** A sheet over the add screen was put away. */
    data object SheetClosed : AddEvent
}

/** The add screen after [event]. The one place it changes, see [AddScreen]. Without the Food tab nothing opens. */
internal fun addAfter(event: AddEvent, model: Model): Next {
    val add = model.food.add
    fun screen(next: AddScreen?, sheet: FoodSheet? = model.food.sheet) = model.food { it.copy(add = next, sheet = sheet) }
    return when (event) {
        AddEvent.Open -> when {
            model.food.day == null -> model.only()
            add != null -> screen(add, sheet = null).only()
            else -> screen(AddScreen(), sheet = null).only()
        }
        AddEvent.Close -> if (add == null) model.only() else screen(null, sheet = null).only()
        AddEvent.Scan -> if (add == null) model.only() else screen(add.copy(loop = Loop.SCANNING), sheet = null) and Cmd.Ui(UiEvent.ScanBarcode)
        AddEvent.Type -> if (add == null) model.only() else screen(add.copy(loop = Loop.TYPING), sheet = FoodSheet.Barcode()).only()
        is AddEvent.ScanFailed -> when {
            add == null -> model.only()
            // A scan put away on purpose ends the run: back to the add screen, with the plate as it is.
            event.why == ScanFailure.CANCELLED && add.loop == Loop.SCANNING -> screen(add.copy(loop = null), sheet = null).only()
            else -> screen(add.copy(loop = Loop.TYPING), sheet = FoodSheet.Barcode(event.note)).only()
        }
        is AddEvent.Landed -> when (add?.loop) {
            null -> screen(add, sheet = null).only()
            Loop.SCANNING -> screen(add, sheet = null) and Cmd.Ui(UiEvent.ScanBarcode)
            // The count makes each note a new one, so the sheet starts empty for the next barcode.
            Loop.TYPING -> screen(add, sheet = FoodSheet.Barcode("${event.name} is on the plate, ${counted(model.food.plate.size, "food")} now. Type the next barcode, or close this.")).only()
        }
        AddEvent.SheetClosed -> if (add?.loop == null) model.only() else screen(add.copy(loop = null)).only()
    }
}

/** What happens to the plate, see [plateAfter]. */
internal sealed interface PlateEvent {
    /** [items] onto the plate, after what is there: all of them, or none when they do not fit. */
    data class Put(val items: List<PlateItem>) : PlateEvent

    /** The item with the id of [item] becomes [item]. */
    data class Change(val item: PlateItem) : PlateEvent

    data class Remove(val id: String) : PlateEvent

    data object Clear : PlateEvent

    /** Every item becomes an entry of the day shown; the plate is empty and the add screen closes. */
    data object Log : PlateEvent
}

/**
 * The plate after [event]. The one place it changes: only [PlateEvent.Log], [PlateEvent.Clear] and
 * [PlateEvent.Remove] make it smaller, and nothing puts more than [MAX_PLATE_ITEMS] on it.
 *
 *   ┌───────┐ Put (fits) ┌─────────────┐ Put, Change, Remove (of more than one)
 *   │ empty │ ─────────▶ │ N items     │ ◀──┘
 *   └───────┘ ◀───────── └─────────────┘   Put that does not fit: the same plate, and a snackbar says so
 *        Log (N entries, Undo), Clear, Remove of the last one
 */
internal fun plateAfter(event: PlateEvent, model: Model, env: Env): Next {
    val plate = model.food.plate
    fun plated(next: List<PlateItem>) = model.food { it.copy(plate = next) }
    return when (event) {
        is PlateEvent.Put -> when {
            event.items.isEmpty() -> model.only()
            plate.size + event.items.size > MAX_PLATE_ITEMS ->
                model and Cmd.Ui(UiEvent.Snackbar("The plate holds ${counted(MAX_PLATE_ITEMS, "food")}. Log it first.", tag = FOOD_SNACKBAR))
            else -> plated(plate + event.items.map(::plateItemWithin)).only()
        }
        is PlateEvent.Change -> {
            val changed = plateItemWithin(event.item)
            if (plate.none { it.entry.id == changed.entry.id }) model.only() else plated(plate.map { if (it.entry.id == changed.entry.id) changed else it }).only()
        }
        is PlateEvent.Remove -> {
            if (plate.none { it.entry.id == event.id }) return model.only()
            // The sheet of the item that went goes with it.
            val sheet = model.food.sheet
            val open = (sheet as? FoodSheet.Amount)?.target ?: (sheet as? FoodSheet.Quick)?.target
            plated(plate.filter { it.entry.id != event.id }).food { if (open == AmountFor.OnPlate(event.id)) it.copy(sheet = null) else it }.only()
        }
        PlateEvent.Clear -> if (plate.isEmpty()) model.only() else plated(emptyList()).food { it.copy(sheet = null) }.only()
        PlateEvent.Log -> {
            val day = model.food.day
            if (plate.isEmpty() || day == null) return model.only()
            val entries = entriesOfPlate(plate, env.newId(), env.now, day)
            // A product found online is kept once it is logged, as from its add sheet; a food the phone knows stays as it is.
            val kept = plate.mapNotNull { it.food }.filter { it.source == FoodSource.SCANNED && knownFood(model, it.id) == null }
                .fold(model) { at, food -> at.keeping(food) }
            val felt = if (model.state.settings.haptics) listOf(Cmd.Ui(UiEvent.Haptic(Feel.SUCCESS))) else emptyList()
            kept.food { it.copy(plate = emptyList(), add = null, sheet = null) }
                .withState { it.copy(foodLog = it.foodLog + entries) }
                .undo { it.copy(addedEntries = entries.map { entry -> entry.id }) } and
                felt + Cmd.Ui(UiEvent.Snackbar("Logged ${counted(entries.size, "food")}", actionLabel = "Undo", onAction = Msg.Food.UndoAdded, millis = FOOD_UNDO_MS, tag = FOOD_SNACKBAR))
        }
    }
}

/** The plate file: the items, in their order. */
@Serializable
private data class PlateFile(val items: List<PlateItem>)

/** The plate file's text for [plate]. Null for an empty plate: the file goes. */
fun encodePlate(plate: List<PlateItem>): String? =
    if (plate.isEmpty()) null else StateJson.encodeToString(PlateFile.serializer(), PlateFile(plate))

/** The plate in the plate file's [text], in its limits. Fails on a file that is no plate. */
fun decodePlate(text: String): List<PlateItem> =
    StateJson.decodeFromString(PlateFile.serializer(), text).items.map(::plateItemWithin).distinctBy { it.entry.id }.take(MAX_PLATE_ITEMS)

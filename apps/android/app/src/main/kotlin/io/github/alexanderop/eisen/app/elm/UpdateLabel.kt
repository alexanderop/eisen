package io.github.alexanderop.eisen.app.elm

import io.github.alexanderop.eisen.feature.food.readLabel

/*
 * The photo of a label: the choice of camera or photo, the wait for it, its reading, and the own food form it fills. The
 * one place [LabelStep] changes. The photo itself never reaches the model: the runtime reads it and sends the text.
 */

/** The photo flow after [msg], see [LabelStep]. A Msg that does not fit the step it finds changes nothing. */
internal fun labelAfter(msg: Msg.Food, model: Model): Next {
    val sheet = model.food.sheet
    val photo = sheet as? FoodSheet.Photo
    val step = photo?.step
    fun to(next: FoodSheet?) = model.food { it.copy(sheet = next) }
    fun at(next: LabelStep) = to(photo?.copy(step = next))
    return when (msg) {
        is Msg.Food.ReadLabel -> when {
            // On the add screen, with no sheet over it.
            sheet == null && model.food.add != null -> to(FoodSheet.Photo(LabelStep.Choose)).only()
            // From the form of a new food: it comes back as it was, with the name typed so far.
            sheet is FoodSheet.Own && sheet.id == null && sheet.picker == IconPicker.Closed ->
                to(FoodSheet.Photo(LabelStep.Choose, sheet.copy(name = msg.name.trim(), read = null))).only()
            else -> model.only()
        }
        is Msg.Food.ChoosePhoto -> if (step != LabelStep.Choose) model.only() else at(LabelStep.Waiting(msg.source)) and Cmd.Ui(UiEvent.TakePhoto(msg.source))
        Msg.Food.PhotoChosen -> if (step !is LabelStep.Waiting) model.only() else at(LabelStep.Reading).only()
        // Nothing changed: back to where it began.
        Msg.Food.PhotoCancelled -> if (step !is LabelStep.Waiting) model.only() else to(photo.form).only()
        is Msg.Food.LabelRecognized -> {
            if (step != LabelStep.Reading) return model.only()
            val reading = readLabel(msg.text) ?: return at(LabelStep.NothingFound).only()
            // The form, filled; the name is still to be typed (or kept, from the form it came from).
            to((photo.form ?: FoodSheet.Own(direct = false)).copy(read = reading, picker = IconPicker.Closed)).only()
        }
        is Msg.Food.LabelFailed -> if (step is LabelStep.Waiting || step == LabelStep.Reading) at(LabelStep.Failed(msg.why)).only() else model.only()
        Msg.Food.RetryPhoto -> if (step == LabelStep.NothingFound || step is LabelStep.Failed) at(LabelStep.Choose).only() else model.only()
        Msg.Food.TypeItIn -> if (step == LabelStep.NothingFound || step is LabelStep.Failed) to(photo.form ?: FoodSheet.Own()).only() else model.only()
        else -> model.only()
    }
}

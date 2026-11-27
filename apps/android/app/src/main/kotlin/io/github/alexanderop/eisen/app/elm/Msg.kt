package io.github.alexanderop.eisen.app.elm

import io.github.alexanderop.eisen.feature.timer.boxing.BOXING_TEMPLATES
import io.github.alexanderop.eisen.feature.timer.boxing.BoxingRoom
import io.github.alexanderop.eisen.feature.timer.boxing.BoxingRound
import io.github.alexanderop.eisen.feature.timer.boxing.BoxingStance
import io.github.alexanderop.eisen.feature.timer.boxing.BoxingStyle
import io.github.alexanderop.eisen.core.Equipment
import io.github.alexanderop.eisen.feature.workout.ExerciseLog
import io.github.alexanderop.eisen.feature.workout.Slot
import io.github.alexanderop.eisen.feature.timer.Activity
import io.github.alexanderop.eisen.feature.timer.TimerCalls
import io.github.alexanderop.eisen.feature.timer.TimerConfig
import io.github.alexanderop.eisen.feature.timer.TimerMode
import io.github.alexanderop.eisen.core.WeightUnit
import io.github.alexanderop.eisen.feature.timer.wod.WodItem
import io.github.alexanderop.eisen.feature.food.ProductAnswer
import io.github.alexanderop.eisen.feature.food.SearchAnswer
import io.github.alexanderop.eisen.feature.workout.Workout as FinishedWorkout

/**
 * Everything that can happen, from the lifter or from outside the app. [update] takes each one.
 * A Msg that makes no sense in the current model (an index that is gone, a second Undo) changes nothing.
 */
sealed interface Msg {
    /**
     * The running workout. The machine in `app/elm/WorkoutMachine.kt` decides; [update] adds the rest
     * timer, haptics, navigation and Undo around it. Start, SetWorkingWeight (from Settings) and
     * Undo/Forget of a discard all come through here.
     */
    data class Workout(val event: WorkoutEvent) : Msg

    /** The parts of the workout screen that are not the machine's business. */
    sealed interface WorkoutScreen : Msg {
        /** Ticks a warmup set off, or back on. [key] is [warmupKey]. */
        data class ToggleWarmup(val key: String) : WorkoutScreen

        /** The reps entry was closed without logging a count. */
        data object CloseRepsEntry : WorkoutScreen

        /** A number picked on a set with a target, by holding the set: logs it, see [WorkoutEvent.LogReps]. */
        data class PickReps(val exercise: Int, val set: Int, val reps: Int) : WorkoutScreen

        /**
         * [SETTLE_MS] after the tap at [since] on [id] ([Model.settling]): the exercise takes its place.
         * A later tap started a new wait, so a Settle of an older one changes nothing.
         */
        data class Settle(val id: String, val since: Long) : WorkoutScreen

        /** A drag starts: the exercise tapped last takes its place now, so the order under the finger holds still. */
        data object SettleNow : WorkoutScreen

        /** TalkBack's "Move up" (-1) and "Move down" (1) of an open exercise. */
        data class MoveBy(val id: String, val step: Int) : WorkoutScreen
    }

    sealed interface Nav : Msg {
        data class SelectTab(val tab: Tab) : Nav

        /** The "Log" and "Progress" switch at the top of the History tab. */
        data class ShowHistory(val view: HistoryView) : Nav

        /** The "WODs" and "Timers" switch at the top of the Timer tab. */
        data class ShowTimerPage(val page: TimerPage) : Nav
        data object OpenWorkout : Nav
        data object CloseWorkout : Nav
        data class OpenRoutine(val id: String) : Nav
        data object CloseRoutine : Nav

        /** The system back gesture. Only sent while [handlesBack] is true; otherwise Android closes the app. */
        data object Back : Nav

        /**
         * What the activity saved before Android ended the process. [food]: the Food screen showed, as build 24 saved
         * it when food was a screen over Home; it comes back as the Food tab.
         */
        data class Restore(val tab: Tab, val workoutOpen: Boolean, val routine: String?, val history: HistoryView = HistoryView.LOG, val food: Boolean = false) : Nav
    }

    sealed interface Home : Msg {
        /** Hides the "Workout saved" card. */
        data object DismissSummary : Home
    }

    sealed interface Routines : Msg {
        /** Chooses the workout the home screen offers next. Ignored while one runs. */
        data class Select(val id: String) : Routines

        /** Adds "Workout N" at the end. [openEditor] opens it in the Workouts tab. */
        data class Add(val openEditor: Boolean = true) : Routines

        /** Cut to [ROUTINE_NAME_LENGTH]. May be blank while typing, see [CommitName]. */
        data class Rename(val id: String, val name: String) : Routines

        /** The name field was left: a blank name becomes "Workout". */
        data class CommitName(val id: String) : Routines

        /** Changes the rotation order. The workout offered next stays the same workout. */
        data class Move(val from: Int, val to: Int) : Routines

        /** The last workout stays: the home screen always needs one to offer. */
        data class Remove(val id: String) : Routines

        data class AddSlot(val routineId: String, val exerciseId: String) : Routines
        data class UpdateSlot(val routineId: String, val index: Int, val slot: Slot) : Routines

        /** Any distance as one change. One place up is `MoveSlot(id, i, i - 1)`. */
        data class MoveSlot(val routineId: String, val from: Int, val to: Int) : Routines

        /**
         * By exercise, not by index: a swipe and a button can fire for the same card, and the second
         * must not take the slot that moved into its place. [offerUndo] shows "Removed X" with Undo.
         */
        data class RemoveSlot(val routineId: String, val exerciseId: String, val offerUndo: Boolean = true) : Routines

        /** Puts a slot back at [index]. Nothing happens when the exercise is in the routine again. */
        data class InsertSlot(val routineId: String, val index: Int, val slot: Slot) : Routines

        /** The Undo of the last [RemoveSlot]. */
        data object UndoRemoveSlot : Routines
    }

    sealed interface Exercises : Msg {
        /**
         * Creates the lifter's own exercise, or finds the one with that name. An empty name does nothing.
         * [addTo] also adds it where the picker was opened. See [exerciseNamed] for the result.
         * [timed]: held for time, like a plank.
         */
        data class AddCustom(val name: String, val equipment: Equipment, val addTo: AddTarget? = null, val timed: Boolean = false) : Exercises

        /** Removes it from every routine. Finished workouts keep their logs of it. */
        data class DeleteCustom(val id: String) : Exercises
    }

    sealed interface History : Msg {
        /** Corrects what a finished workout logged. Working weights stay as they are. */
        data class Edit(val id: String, val exercises: List<ExerciseLog>) : History

        /** [offerUndo] shows "Workout deleted" with Undo (a swipe); the dialog asks first and offers none. */
        data class Delete(val id: String, val offerUndo: Boolean = false) : History

        /** Puts a deleted workout back at [index]. Nothing happens when it is still there. */
        data class Restore(val workout: FinishedWorkout, val index: Int) : History

        /** The Undo of the last [Delete] with `offerUndo`. */
        data object UndoDelete : History

        /** A tap on a finished timer: the sheet "What was it?" opens over the log, see [ActivitySheet]. Not for a WOD. */
        data class OpenActivity(val id: String) : History

        /** The sheet is put away: its close button, a swipe down, a tap beside it or Back. */
        data object CloseActivity : History
    }

    sealed interface Settings : Msg {
        /** Converts the working weights. Ignored during a workout and while a discard can be undone. */
        data class SetUnit(val unit: WeightUnit) : Settings
        data class SetBar(val bar: Double) : Settings
        data class SetRestDone(val seconds: Int) : Settings
        data class SetRestMissed(val seconds: Int) : Settings

        /** Switching the sound on plays the end-of-rest cue once, so the lifter hears what it does. */
        data class SetSound(val on: Boolean) : Settings
        data class SetHaptics(val on: Boolean) : Settings
        data class SetRestFullScreen(val on: Boolean) : Settings
        data class SetTrackRir(val on: Boolean) : Settings

        /** Discarding a workout puts an increment changed during it back. */
        data class SetIncrement(val id: String, val increment: Double) : Settings
    }

    /** Backups and erasing. The file pickers stay in the view; reading and writing are Cmds. */
    sealed interface Data : Msg {
        /** A backup's text. Not one of this app or the PWA: nothing changes and [Model.dataNotice] says so. */
        data class Import(val json: String) : Data

        /** A file the lifter picked to restore. The runtime reads it and answers with [BackupRead]. */
        data class Restore(val uri: String) : Data

        /** Null when the file could not be read. */
        data class BackupRead(val text: String?) : Data

        /** A file the lifter created with the "Save backup" picker, named [backupFileName]. */
        data class WriteBackup(val uri: String) : Data
        data class BackupWritten(val ok: Boolean) : Data

        /** Hands the backup to the share sheet as a file. */
        data object SendBackup : Data
        data object SendFailed : Data

        /** Erases everything but the unit. */
        data object Reset : Data
    }

    /** The hold timer of a timed set, see [HoldTimer]. A Msg without an open timer changes nothing. */
    sealed interface Hold : Msg {
        /** A tap on the "Get ready" countdown: the hold starts now. */
        data object SkipLeadIn : Hold

        /** "Stop" during a countdown, "Done" while holding as long as possible: logs the whole seconds held. */
        data object Stop : Hold

        /** Closes the timer without logging anything. The back gesture does the same. */
        data object Cancel : Hold

        /** A second of the hold passed, see [subscriptions]. */
        data object Tick : Hold
    }

    /**
     * The interval timers: their setup, the run over everything else and its result, the saved timers and
     * the finished ones in the history. The buttons on the run's notification end up here too.
     * A Msg about a run, setup or result that is not there changes nothing.
     */
    sealed interface Timer : Msg {
        /** A mode card: its setup opens with the config it was last started with. */
        data class Open(val mode: TimerMode) : Timer

        /** A saved timer: the setup opens filled in, and "Update preset" can overwrite it. */
        data class OpenPreset(val id: String) : Timer

        /** The play button of a saved timer: its setup opens and it starts at once. */
        data class StartPreset(val id: String) : Timer

        /** The name field of a setup that belongs to a saved timer. Saved by [UpdatePreset]. */
        data class EditName(val name: String) : Timer

        /** A wheel of the setup settled. Brought into the limits of the mode. */
        data class Edit(val config: TimerConfig) : Timer
        data object CloseSetup : Timer

        /** The voice sheet or the "Timer voice" settings. A run that is going keeps what it started with. */
        data class SetCalls(val calls: TimerCalls) : Timer

        /** "Play sample": says "Halfway" so the lifter can set the volume. */
        data object PlaySample : Timer

        /** A round of the boxing setup, its row or its piece of the timeline: its sheet opens. The arrows step with it. */
        data class OpenRound(val index: Int) : Timer

        /** "Done" or a swipe down on the round sheet. */
        data object CloseRound : Timer

        /** A theme tile, the pace or a speed and power chip of the round sheet. Brought into the limits of a round. */
        data class EditRound(val index: Int, val round: BoxingRound) : Timer

        /** "Copy to all rounds": every round becomes a copy of round [index]. */
        data class CopyToAll(val index: Int) : Timer

        /** A template chip: [BOXING_TEMPLATES] at [index], laid over the number of rounds, with Undo beside the chips. */
        data class ApplyTemplate(val index: Int) : Timer
        data object UndoTemplate : Timer

        /** "Names" or "Numbers": how the coach calls the combinations. */
        data class SetStyle(val style: BoxingStyle) : Timer

        /** "Listen" in the round sheet: the next combination of round [index], in order, said in the chosen style. */
        data class Listen(val index: Int) : Timer

        /** The play button of a combination in the round sheet's list or of the lifter's own: says it in the chosen style. */
        data class PlayCombo(val id: String) : Timer

        /** "Orthodox" or "Southpaw": which hand the key of the calls says leads. */
        data class SetStance(val stance: BoxingStance) : Timer

        /** "Roomy", "Normal" or "Tight": how long the coach waits for a combination to be thrown. */
        data class SetRoom(val room: BoxingRoom) : Timer

        /** "Listen" in the combo builder: the moves so far, said the way the coach will say them. */
        data class ListenMoves(val moves: List<String>) : Timer

        /**
         * "Save" in the combo builder: a new combination of the lifter's own when [id] is null, else new
         * moves for that one. Opened from the "My combos" tile of round [round], a new one makes that
         * round a "My combos" round.
         */
        data class SaveOwn(val id: String?, val moves: List<String>, val round: Int? = null) : Timer

        /** The "My combos" tile of round [round] without any combinations (it opens the builder), or "New combination" (null). */
        data class NewCombo(val round: Int?) : Timer

        /** One of the lifter's own combinations, tapped to change its moves. */
        data class EditOwn(val id: String) : Timer

        /** The combo builder is put away without saving. */
        data object CloseCombo : Timer

        /** "Choose the combinations of this round" in the open round sheet: their list opens. */
        data object OpenPicks : Timer

        /** "Done" or a swipe down on the list of combinations. */
        data object ClosePicks : Timer

        /** "All" in the list: the round calls every combination of its theme. */
        data object PickAll : Timer

        /** "None" in the list: every box unchecked, to check a few. The round calls all of them until one is. */
        data object PickNone : Timer

        /** A combination in the list checked or unchecked. */
        data class TogglePick(val id: String) : Timer

        /** "Delete" in the combo builder: the combination goes from the plan and every round, with Undo. */
        data class DeleteOwn(val id: String) : Timer
        data object UndoDeleteOwn : Timer

        /** A drag of a round's tile, or "Move earlier" / "Move later": round [from] goes to place [to]. */
        data class MoveRound(val from: Int, val to: Int) : Timer

        /** "Duplicate": a copy of round [index] right after it, up to the most rounds a workout has. */
        data class DuplicateRound(val index: Int) : Timer

        /** "Remove": round [index] goes. The last round stays. */
        data class RemoveRound(val index: Int) : Timer

        /** The template applied at [at] can no longer be undone, unless another one was applied since. */
        data class ForgetTemplate(val at: Long) : Timer

        /** Saves the setup as a new timer. A blank [name] gets the mode and its summary. */
        data class SavePreset(val name: String) : Timer

        /** Overwrites the saved timer the setup was opened from: its numbers and its name. */
        data object UpdatePreset : Timer

        /** A swipe: gone at once, with Undo. */
        data class DeletePreset(val id: String) : Timer
        data object UndoDeletePreset : Timer

        /** "Start" in the setup: ends a running rest and closes a hold timer. */
        data object Start : Timer

        /** "Again" on the result: the same timer straight into its lead-in. */
        data object Again : Timer
        data object Pause : Timer
        data object Resume : Timer

        /** "End" and the back gesture: asks first, except during the lead-in, where it just closes. */
        data object End : Timer

        /** The answers to "End timer?". [SaveAndEnd] is also the "End" on the notification, which cannot ask. */
        data object KeepGoing : Timer
        data object SaveAndEnd : Timer
        data object Discard : Timer

        /** "Finish" in For Time: the lifter is done, the run counts as completed. */
        data object Finish : Timer

        /**
         * "+1 round" in AMRAP and For Time. A second one within [TAP_GUARD_MILLIS] is a bounce and does not count.
         * In a For Time WOD it ticks off the item to do now, one tap per item of every round but the rests,
         * and the tap of the last ends the run as completed. During a rest it does nothing: the clock counts that.
         * A tap of a WOD keeps its time in the run's splits.
         */
        data object Tap : Timer

        /** "Skip rest" while a rest of a For Time WOD runs: it ends early. No tap, no split. */
        data object SkipRest : Timer

        /**
         * "Not finished? Undo" on the result of a WOD its last tap ended: that tap is taken back and the run goes on.
         * Like every answer on the result, it does not count within [RESULT_GUARD_MILLIS] of the result showing.
         */
        data object UndoFinish : Timer

        /** "−1" next to the count, "Back" in a For Time WOD: takes back a tap made by mistake, and its split. */
        data object Untap : Timer

        /** "−" and "+" on the result of AMRAP and For Time: corrects the rounds of the saved session by [by]. */
        data class CorrectRounds(val by: Int) : Timer

        /** A second of the run passed, see [subscriptions]. Ends the run once it ran out. */
        data object Tick : Timer

        /** "Done" on the result: back to the Timer tab. */
        data object Done : Timer

        /** "Don't count this run" on the result: its history entry goes, with Undo, and the WOD it was a run of shows. */
        data object DontCount : Timer

        /** The last cues of run [id] had time to play. Lets the timer's service go if no other run started. */
        data class Release(val id: String) : Timer

        /**
         * A chip of "What was it?", on the result or in the history's sheet: the finished timer [id] was
         * [activity], or nothing said (the lit chip tapped again). A WOD and an id that is not filed change nothing.
         */
        data class SetActivity(val id: String, val activity: Activity?) : Timer

        /** A swipe on a finished timer in the history: gone at once, with Undo. */
        data class DeleteSession(val id: String) : Timer
        data object UndoDeleteSession : Timer
    }

    /**
     * The WODs page of the Timer tab: the list, a WOD's detail and the builder. The builder's Msgs
     * change the draft in [WodsModel.draft]; only [Save] puts it into the state. A Msg about a WOD,
     * a draft or an item that is not there changes nothing.
     */
    sealed interface Wods : Msg {
        /** A chip over the benchmarks. */
        data class Filter(val filter: WodFilter) : Wods

        /**
         * A card, or a WOD among the suggestions ([suggested]): its detail opens, and the suggestions close.
         * From a suggestion the search is done with, so it is cleared.
         */
        data class Open(val id: String, val suggested: Boolean = false) : Wods

        /** Typed in the search field: the page filters at once, and the suggestions open while there is text. */
        data class Search(val query: String) : Wods

        /** The keyboard's search key, or a tap outside the field: the suggestions close, the search stays. */
        data object CloseSuggestions : Wods

        /** A movement among the suggestions: its name becomes the search, and the suggestions close. */
        data class SearchMovement(val name: String) : Wods

        /** The field's clear button, or "Clear search" when nothing matched. */
        data object ClearSearch : Wods

        /** Back from the detail to the list. */
        data object Close : Wods

        /**
         * The play button of a card or "Start" in the detail: straight into the lead-in. Follows the
         * timer's rules, and leaves the config its mode opens with as it is.
         */
        data class Start(val id: String) : Wods

        /** "New WOD": the builder opens empty. */
        data object New : Wods

        /** "Edit" of one of the lifter's own. */
        data class Edit(val id: String) : Wods

        /** "Edit a copy" of a benchmark: the builder opens on "<Name> (mine)", a new WOD that is only stored on Save. */
        data class Copy(val id: String) : Wods

        /** One of the lifter's own: gone at once, with Undo. Its runs stay in the history. */
        data class Delete(val id: String) : Wods
        data object UndoDelete : Wods

        /** The name field. Cut to [io.github.alexanderop.eisen.feature.timer.wod.MAX_WOD_NAME], may be blank while typing. */
        data class Rename(val name: String) : Wods

        /** The type: keeps the items, the numbers start over at the mode's defaults. */
        data class SetMode(val mode: TimerMode) : Wods

        /** A wheel of the numbers settled. */
        data class SetTimer(val config: TimerConfig) : Wods
        data class SetRounds(val rounds: Int) : Wods

        /** "Same each round" (empty: every round as the items say) or a preset chip. Closes the custom field. */
        data class SetScheme(val scheme: List<Int>) : Wods

        /** The "Custom" chip: its field opens with what it held last. See [WodDraft]. */
        data object ChooseCustom : Wods

        /** The custom field: [text] as typed, its numbers the scheme. */
        data class TypeScheme(val text: String) : Wods

        /** An exercise from the picker, with a sensible start, see [startItem]. */
        data class AddItem(val exerciseId: String) : Wods

        /** The picker's "Add 3": the exercises in the order they were ticked, each with its start. */
        data class AddItems(val exerciseIds: List<String>) : Wods

        /** "Continue" on the card of a draft from before the process ended: the builder opens on it. */
        data object ContinueDraft : Wods

        /** "Discard" on that card: the draft is gone. */
        data object DiscardDraft : Wods

        /** The item editor. By index, checked against the exercise, since a WOD may hold one exercise twice. */
        data class UpdateItem(val index: Int, val item: WodItem) : Wods
        data class MoveItem(val from: Int, val to: Int) : Wods
        data class RemoveItem(val index: Int, val exerciseId: String) : Wods

        /**
         * Enabled with at least one item. Goes on to the detail of the saved WOD. An edit that changes
         * the work gives it a new id, so its earlier runs stay in the history but are no longer its best.
         */
        data object Save : Wods

        /** Back or the close button of the builder: asks "Discard changes?" when something would be lost. */
        data object Leave : Wods

        /** The answers to "Discard changes?". */
        data object KeepEditing : Wods
        data object Discard : Wods
    }

    /**
     * The food log: the Food screen, its sheets, the scanner and Open Food Facts. A Msg about a food, an entry
     * or a sheet that is not there changes nothing. What is logged goes to the day the screen shows.
     */
    sealed interface Food : Msg {
        /** The "Food today" card on Home: the Food tab opens on today. */
        data object Open : Food

        /** "Scan" on the Home card: the Food tab opens on today and the scanner starts. */
        data object OpenScan : Food

        /** "Add" on the Home card or the Food tab: the add screen opens, on the day the Food tab shows (today from Home). */
        data object OpenAdd : Food

        /** The arrows of the week strip: the same weekday a week earlier, or a week later (never after today). */
        data object PreviousWeek : Food
        data object NextWeek : Food

        /**
         * "Scan": the scanner starts. Answers with [Scanned] or [ScanFailed]. On the add screen it opens again after each
         * food that lands on the plate, until it is cancelled (see [AddScreen]).
         */
        data object Scan : Food
        data class Scanned(val barcode: String) : Food

        /** The scanner gave nothing: the barcode can be typed instead. */
        data class ScanFailed(val why: ScanFailure) : Food

        /** "Type a barcode": its sheet opens. On the add screen it opens again after each food that lands on the plate. */
        data object TypeBarcode : Food

        /** A typed barcode, sent with "Look up". One that cannot be a product's changes nothing. */
        data class LookUp(val barcode: String) : Food

        /** What Open Food Facts answered for [barcode]. */
        data class Looked(val barcode: String, val answer: ProductAnswer) : Food

        /** "Try again" after a lookup that got no answer. */
        data object Retry : Food

        /** A tap on a food of the add screen, the phone's or one Open Food Facts found: its add sheet opens, for the plate. */
        data class Pick(val foodId: String) : Food

        /** The chips and the grams of the add sheet. Brought into the limits. */
        data class SetAmount(val amount: Double) : Food

        /** The time of day of the entry whose add sheet is open, as minutes from midnight (450 is 07:30). Saved with its amount. */
        data class SetTime(val minute: Int) : Food

        /** "Add 270 kcal" (or "Save" of an entry's new amount). */
        data object ConfirmAmount : Food

        /** A tap on an entry: its amount can change, or a quick entry its numbers. */
        data class OpenEntry(val id: String) : Food

        /** A swipe: gone at once, with Undo. */
        data class DeleteEntry(val id: String) : Food
        data object UndoDeleteEntry : Food

        /** A chip of "Again": one tap logs [foodId] at [amount] again, with Undo. */
        data class AddAgain(val foodId: String, val amount: Double) : Food
        data object UndoAgain : Food

        /** "kcal": the quick entry for a meal out. */
        data object OpenQuick : Food

        /**
         * Saves a quick entry: new, or the one the sheet was opened for, then at [minute] of its day when given. No
         * calories: nothing.
         */
        data class SaveQuick(val kcal: Int, val protein: Int?, val name: String, val minute: Int? = null) : Food

        /** "New food" in the search: the own food form, with what was typed as its name. */
        data class NewOwn(val name: String = "") : Food

        /** "Edit food" on the add sheet of an own food. */
        data class EditOwn(val id: String) : Food

        /** Saves the own food form, per 100 g. A blank name changes nothing. Then its add sheet opens. */
        data class SaveOwn(val name: String, val kcal: Double, val protein: Double, val carbs: Double?, val fat: Double?, val serving: Double?) : Food

        /** Deletes an own food. What was logged of it keeps its numbers. */
        data class DeleteOwn(val id: String) : Food

        data object OpenGoal : Food

        /**
         * Calories and grams a day should reach. 0 is no goal. [weight]: the goal pace and the weekly check-in's switch as
         * the goal sheet has them (pace in kilograms a week), null to leave them as they are.
         */
        data class SaveGoal(val kcal: Int, val protein: Int, val carbs: Int = 0, val fat: Int = 0, val weight: io.github.alexanderop.eisen.feature.food.WeightGoal? = null) : Food

        /** Typed into the search of the add screen: what the phone knows is filtered at once. No request. */
        data class Search(val query: String) : Food

        /** The keyboard's search key or "Search Open Food Facts": one request. */
        data object SubmitSearch : Food

        /** What Open Food Facts found for [query]. */
        data class Searched(val query: String, val answer: SearchAnswer) : Food

        /** "Food data" on the Food screen. */
        data object OpenCredit : Food

        /** A sheet is put away: its close button, a swipe down, a tap beside it or Back. */
        data object CloseSheet : Food

        // Meals, see [MealSheet]

        /** "Edit meals": the meals, each to change or delete. */
        data object OpenMeals : Food

        /** "Save as meal" under the entries of the day shown: a new meal of them, every one picked. */
        data object NewMeal : Food

        /** "Save these as a meal" under a group of the timeline: a new meal of the day's entries, only [ids] picked. */
        data class NewMealOf(val ids: List<String>) : Food

        /** An entry of the day ticked or unticked for the new meal. */
        data class TogglePick(val entryId: String) : Food

        /** Saves the new meal of the picked entries as [name]. A blank name or nothing picked: nothing. */
        data class SaveNewMeal(val name: String) : Food

        /** A meal in the list: its sheet to change it opens. */
        data class EditMeal(val id: String) : Food

        /** The amount of item [index] of the meal being changed. A quick item has none: nothing. */
        data class SetMealAmount(val index: Int, val amount: Double) : Food

        /** Item [index] taken out of the meal being changed. */
        data class RemoveMealItem(val index: Int) : Food

        /** Saves the meal being changed as [name]. Entries already logged of it keep their numbers. */
        data class SaveMeal(val name: String) : Food

        /** Deletes a meal. Entries already logged of it keep their numbers. */
        data class DeleteMeal(val id: String) : Food

        /** A chip of the meals: one tap logs each of its foods on the day shown, with Undo. */
        data class AddMeal(val id: String) : Food

        /** "Copy yesterday" on an empty day: what was eaten the day before, logged on this one, with Undo. */
        data object CopyDayBefore : Food

        /** Undo of [AddMeal] and [CopyDayBefore]: the entries they logged go again. */
        data object UndoAdded : Food

        /** A bar of the week in Progress: the Food tab opens on that day. */
        data class OpenDay(val day: String) : Food

        // The add screen and the plate, see [AddScreen]

        /** Back or the close button of the add screen. What is on the plate stays. */
        data object CloseAdd : Food

        /** `+` on a food of the add screen ([foodId] of the phone's or of a hit online): onto the plate at its usual amount. */
        data class PlateAdd(val foodId: String) : Food

        /** `+` on a meal of the add screen: each of its foods onto the plate. */
        data class PlateMeal(val id: String) : Food

        /** A tap on an item of the plate: its amount (or its quick kcal) can change. */
        data class OpenPlateItem(val id: String) : Food

        /** An item taken off the plate. */
        data class RemovePlateItem(val id: String) : Food

        /** "Clear": the plate is emptied, nothing is logged. */
        data object ClearPlate : Food

        /** "Log 671 kcal": every item of the plate becomes an entry of the day shown, with one Undo for all of them. */
        data object LogPlate : Food
    }

    /**
     * The weight log and the weekly check-in. A weight is logged from the Food tab's card or from History › Progress,
     * for today or an earlier day; the check-in's card is on the Food tab, see [CheckIn].
     */
    sealed interface Weight : Msg {
        /** "Log weight": the sheet opens on [day], today when null, at that day's weight or the last one before it. */
        data class Open(val day: String? = null) : Weight

        /** The number on the sheet, in the unit of the settings. Brought into the limits. */
        data class SetValue(val value: Double) : Weight

        /** The arrows of the sheet: the day before, the day after (never after today). */
        data object PreviousDay : Weight
        data object NextDay : Weight

        /** "Save": the weight of the sheet's day, replacing one logged that day. */
        data object Save : Weight

        /** "Delete" on the sheet of a day with a weight: gone at once, with Undo. */
        data object Delete : Weight
        data object UndoDelete : Weight

        /** The sheet is put away without saving. */
        data object Close : Weight

        /** The check-in's "Take": its new target becomes the calorie goal. */
        data object TakeCheckIn : Weight

        /** The check-in's "Keep": the goal stays, the card goes for this week. */
        data object KeepCheckIn : Weight
    }

    /** The rest timer. Its alarms and the buttons on its notification end up here too. */
    sealed interface Rest : Msg {
        /** "+30s": in the app and on the notification. */
        data object Extend : Rest

        /**
         * Close, swipe, "Next set" on the rest screen, "Skip" on the notification, or swiping away the
         * "Rest is over" notification.
         */
        data object Dismiss : Rest

        /** "−15s" on the rest screen. Never past now: a rest that is over has nothing left to take. */
        data object Shorten : Rest

        /** "+15s" on the rest screen, counted from now once the rest is over, like [Extend]. */
        data object Lengthen : Rest

        /**
         * The rest screen is swiped down, closed with its chevron or the back gesture. The rest goes on in the bar.
         * The "set done" screen has no rest to go on: it closes, as with its "Done".
         */
        data object Minimise : Rest

        /** A tap on the rest bar brings the minimised rest screen back. */
        data object Open : Rest

        /**
         * [RATED_CLOSE_MS] after a chip on the "set done" [screen] rated its set [rir]: it closes. Not when the
         * lifter changed the rating meanwhile, whose own wait counts, or the screen is gone.
         */
        data class Rated(val screen: RestScreen, val rir: Int) : Rest

        /** The "ten seconds left" alarm. */
        data object AlarmTen : Rest

        /** The "rest over" alarm. An alarm left over from before an extend is ignored. */
        data object AlarmOver : Rest

        /** The cue of [rest] had time to finish. Lets the countdown service go if nothing else started. */
        data class CueFinished(val rest: RestState) : Rest

        /** A second of the countdown passed while the app shows, see [subscriptions]. */
        data object Tick : Rest

        /** The answer to the notification permission request. */
        data class NotificationPermission(val granted: Boolean) : Rest
    }

    sealed interface Health : Msg {
        /** The switch: off at once, on only with Health Connect's permission. */
        data object Toggle : Health
        /**
         * [granted]: workouts may be written. [food]: food may be written too (`WRITE_NUTRITION`, asked since food came).
         * [weight]: weights may be written (`WRITE_WEIGHT`, asked since the weight log came).
         */
        data class PermissionChecked(val purpose: PermissionCheck, val granted: Boolean, val food: Boolean = false, val weight: Boolean = false) : Health

        /** The answer of Health Connect's permission screen: workouts, food and weight. */
        data class PermissionResult(val granted: Boolean, val food: Boolean = false, val weight: Boolean = false) : Health

        /** "Write weight too" in Settings, shown while sync is on without the permission for weight: Health Connect asks. */
        data object AllowWeight : Health

        /** A write of a weight found its permission taken away. Workouts and food go on. */
        data object WeightRevoked : Health

        /** "Write food too" in Settings, shown while sync is on without the permission for food: Health Connect asks. */
        data object AllowFood : Health

        /** A write of food found its permission taken away. Workouts go on. */
        data object FoodRevoked : Health

        /** Health Connect's permission screen could not be opened. */
        data object PermissionRequestFailed : Health

        /** Writes the whole history once. */
        data object SyncPast : Health

        /** [count]: workouts and timers written, null when Health Connect could not be reached. [foods]: food entries written, [weights]: weights. */
        data class SyncedPast(val count: Int?, val foods: Int = 0, val weights: Int = 0) : Health

        /** A write found the permission taken away. */
        data object Revoked : Health

        /**
         * The Health Connect rows came on screen or back to it. The Play Store may only cover the app
         * without stopping it, so [App.Foreground] alone would miss an install made from there.
         */
        data object RowsShown : Health

        /** What Android says about Health Connect on this phone, see [HealthJob.CheckAvailability]. */
        data class Availability(val availability: HealthAvailability) : Health
    }

    sealed interface App : Msg {
        /** The activity is on screen. Notifications may have been allowed in the system settings meanwhile. */
        data class Foreground(val notificationsGranted: Boolean) : App
        data object Background : App

        /**
         * The widget or a launcher shortcut asked to start [routineId], or to resume the running workout
         * when it is null. Android hands the same intent back after a configuration change or the end of
         * the process ([restored]) and when the app is reopened from recents ([fromHistory]); it counts once.
         */
        data class LaunchWorkout(val routineId: String?, val restored: Boolean, val fromHistory: Boolean) : App
    }
}

/** Where a newly created exercise goes, besides the list of exercises. */
sealed interface AddTarget {
    data class Routine(val id: String) : AddTarget

    /** The running workout only, like [WorkoutEvent.AddExercise]. */
    data object Workout : AddTarget

    /** The WOD in the builder, like [Msg.Wods.AddItem]. */
    data object Wod : AddTarget
}

/** Why Health Connect's permission is checked, so the answer goes to the right place. */
/** Why the permission is checked: at the start, for the switch, or because the Health Connect rows came on screen. */
enum class PermissionCheck { STARTUP, TOGGLE, ROWS }

const val ROUTINE_NAME_LENGTH = 30
const val EXERCISE_NAME_LENGTH = 40

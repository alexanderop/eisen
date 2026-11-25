package io.github.alexanderop.eisen.core.ui

/*
 * Holding a set to pick its reps, as a state machine. The set's gesture only turns touches into
 * [RepsPickEvent]s and [RepsPickerState] turns positions into numbers; which number is picked, if any,
 * is decided in [repsPickAfter] and nowhere else. Pure Kotlin, so every transition has a unit test
 * (`RepsPickTest`). [P] is what is being picked (`RepsPick` on the screen), so the tests need no layout.
 *
 *                Open (a hold)                Move beyond the slop          Move: the number under
 *   ┌────────┐ ─────────────────▶ ┌──────┐ ───────────────────────▶ ┌─────────────────┐  the finger
 *   │ Closed │                    │ Held │                          │ Sliding(under)  │ ◀─┐
 *   └────────┘ ◀───── Cancel ──── └──────┘                          └─────────────────┘ ──┘
 *     ▲  ▲  ▲                        │ Lift                    Lift on a  │   │ Lift beside the
 *     │  │  └──── Cancel ────────────┼──────────────────────── number:    │   │ numbers
 *     │  │                           ▼                         picks it   │   ▼
 *     │  │  Open without a finger ┌────────┐ ◀─────────────────────────────┼───┘
 *     │  │  (TalkBack) ─────────▶ │ Lifted │  waits for a tap             │
 *     │  │                        └────────┘                              │
 *     │  └── Tap(n): picks n, from every open state; Close (a tap outside, Back, the cards moved) ──
 *     └───── picked: closes
 *
 * A cancel is the system taking the touch (a gesture from the edge, a call): nothing is picked, the
 * numbers close. Any event a state does not list changes nothing.
 */

/** Where the numbers of a set are. [P] is the set being picked for. */
sealed interface RepsPickPhase<out P> {
    data object Closed : RepsPickPhase<Nothing>

    /** Opened by holding the set. The finger is still down and has not slid yet. */
    data class Held<P>(val pick: P) : RepsPickPhase<P>

    /** The finger slides over the numbers. [under] is the one it points at, null for none. */
    data class Sliding<P>(val pick: P, val under: Int?) : RepsPickPhase<P>

    /** Open with no finger down: a tap on a number picks it. */
    data class Lifted<P>(val pick: P) : RepsPickPhase<P>
}

/** What is being picked, null while closed. */
val <P> RepsPickPhase<P>.pick: P?
    get() = when (this) {
        RepsPickPhase.Closed -> null
        is RepsPickPhase.Held -> pick
        is RepsPickPhase.Sliding -> pick
        is RepsPickPhase.Lifted -> pick
    }

sealed interface RepsPickEvent<out P> {
    /** A hold on a set opened its numbers: with the finger still on it, or from TalkBack without one. */
    data class Open<P>(val pick: P, val fingerDown: Boolean) : RepsPickEvent<P>

    /** The finger moved. [under]: the number it points at; [slid]: it is past the touch slop from where the hold began. */
    data class Move(val under: Int?, val slid: Boolean) : RepsPickEvent<Nothing>

    /** The finger lifted over the number [under], or beside the numbers (null). */
    data class Lift(val under: Int?) : RepsPickEvent<Nothing>

    /** The system took the touch. */
    data object Cancel : RepsPickEvent<Nothing>

    /** A number was tapped, or chosen with TalkBack. */
    data class Tap(val number: Int) : RepsPickEvent<Nothing>

    /** A tap outside the numbers, Back, or the set moved away under them. */
    data object Close : RepsPickEvent<Nothing>
}

/** The phase after an event, the number it [picked] (sent as a tap of the set would be), and whether the finger came onto another number ([tick]). */
data class RepsPickStep<out P>(val phase: RepsPickPhase<P>, val picked: Int? = null, val tick: Boolean = false)

fun <P> repsPickAfter(phase: RepsPickPhase<P>, event: RepsPickEvent<P>): RepsPickStep<P> {
    when (event) {
        // Another hold opens another set's numbers in place of the ones open.
        is RepsPickEvent.Open -> return RepsPickStep(if (event.fingerDown) RepsPickPhase.Held(event.pick) else RepsPickPhase.Lifted(event.pick))
        is RepsPickEvent.Tap -> return if (phase.pick == null) RepsPickStep(phase) else RepsPickStep(RepsPickPhase.Closed, picked = event.number)
        RepsPickEvent.Close -> return RepsPickStep(RepsPickPhase.Closed)
        is RepsPickEvent.Move, is RepsPickEvent.Lift, RepsPickEvent.Cancel -> Unit
    }
    return when (phase) {
        RepsPickPhase.Closed -> RepsPickStep(phase)
        is RepsPickPhase.Held -> when (event) {
            is RepsPickEvent.Move ->
                if (event.slid) RepsPickStep(RepsPickPhase.Sliding(phase.pick, event.under), tick = event.under != null) else RepsPickStep(phase)
            // Held and let go without sliding: the numbers stay for a tap.
            is RepsPickEvent.Lift -> RepsPickStep(RepsPickPhase.Lifted(phase.pick))
            RepsPickEvent.Cancel -> RepsPickStep(RepsPickPhase.Closed)
            is RepsPickEvent.Open, is RepsPickEvent.Tap, RepsPickEvent.Close -> RepsPickStep(phase)
        }
        is RepsPickPhase.Sliding -> when (event) {
            is RepsPickEvent.Move -> RepsPickStep(phase.copy(under = event.under), tick = event.under != null && event.under != phase.under)
            is RepsPickEvent.Lift ->
                if (event.under != null) RepsPickStep(RepsPickPhase.Closed, picked = event.under) else RepsPickStep(RepsPickPhase.Lifted(phase.pick))
            RepsPickEvent.Cancel -> RepsPickStep(RepsPickPhase.Closed)
            is RepsPickEvent.Open, is RepsPickEvent.Tap, RepsPickEvent.Close -> RepsPickStep(phase)
        }
        // No finger is down: what a touch that already ended still sends changes nothing.
        is RepsPickPhase.Lifted -> RepsPickStep(phase)
    }
}

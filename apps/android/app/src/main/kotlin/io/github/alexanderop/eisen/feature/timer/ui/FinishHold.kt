package io.github.alexanderop.eisen.feature.timer.ui

/*
 * For Time's "Hold to finish" as a state machine. The button only translates touches into
 * [FinishHoldEvent]s; whether the run finishes is decided here, in [finishHoldAfter], and nowhere else.
 * Pure Kotlin, so every transition has a unit test (`FinishHoldTest`).
 *
 *            Down                  Full (the bar is full)
 *   ┌──────┐ ─────▶ ┌─────────┐ ─────────────────────────▶ ┌───────┐
 *   │ Idle │        │ Filling │                            │ Armed │  finger down on a full bar
 *   └──────┘        └─────────┘                            └───────┘
 *      ▲  ▲            │ Release on the button: the hint      │ Release on the button: finish
 *      │  └────────────┘ Release off it, Cancel: quietly      │ Release off it, Cancel: backed out, no finish
 *      └──────────────────────────────────────────────────────┘
 *
 * Idle is also where a finish leaves it, so a screen that stays (the program ignored the finish) has an
 * empty bar and the next tap starts over. Any event a state does not list changes nothing.
 */

/** Where the hold of "Hold to finish" is. */
internal sealed interface FinishHold {
    data object Idle : FinishHold

    /** The finger is down and the bar fills. */
    data object Filling : FinishHold

    /** The bar is full and the finger still down: lifting it on the button finishes. */
    data object Armed : FinishHold
}

/** What the finger and the bar did. */
internal sealed interface FinishHoldEvent {
    data object Down : FinishHoldEvent

    /** The bar filled up while the finger was down. */
    data object Full : FinishHoldEvent

    /** The finger lifted, [inside] the button or off it. */
    data class Release(val inside: Boolean) : FinishHoldEvent

    /** The touch is gone without a lift: it slid off the button, or the system took it. */
    data object Cancel : FinishHoldEvent
}

/** The hold after an event, and what it does: [finish] the run, or show the [hint] to keep holding. */
internal data class FinishHoldStep(val hold: FinishHold, val finish: Boolean = false, val hint: Boolean = false)

internal fun finishHoldAfter(hold: FinishHold, event: FinishHoldEvent): FinishHoldStep = when (hold) {
    FinishHold.Idle -> when (event) {
        FinishHoldEvent.Down -> FinishHoldStep(FinishHold.Filling)
        FinishHoldEvent.Full, is FinishHoldEvent.Release, FinishHoldEvent.Cancel -> FinishHoldStep(hold)
    }
    FinishHold.Filling -> when (event) {
        FinishHoldEvent.Full -> FinishHoldStep(FinishHold.Armed)
        // Let go too early on the button: it says how it works. Off the button it was not meant as a press.
        is FinishHoldEvent.Release -> FinishHoldStep(FinishHold.Idle, hint = event.inside)
        FinishHoldEvent.Cancel -> FinishHoldStep(FinishHold.Idle)
        FinishHoldEvent.Down -> FinishHoldStep(hold)
    }
    FinishHold.Armed -> when (event) {
        is FinishHoldEvent.Release -> FinishHoldStep(FinishHold.Idle, finish = event.inside)
        FinishHoldEvent.Cancel -> FinishHoldStep(FinishHold.Idle)
        FinishHoldEvent.Down, FinishHoldEvent.Full -> FinishHoldStep(hold)
    }
}

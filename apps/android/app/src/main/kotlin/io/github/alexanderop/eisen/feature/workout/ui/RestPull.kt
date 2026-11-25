package io.github.alexanderop.eisen.feature.workout.ui

/*
 * Pulling the rest screen down to put it away, as a state machine. Presentation only: what it sends is
 * `Msg.Rest.Minimise`, the program decides the rest. The drag, the spring back and the screen coming
 * back all go through [restPullAfter], so only one of them moves the screen at a time. Pure Kotlin,
 * tested in `RestPullTest`.
 *
 *              Drag                          Drag
 *   ┌───────┐ ──────▶ ┌────────────────┐ ◀──────────── ┌───────────────────┐
 *   │ Still │         │ Pulled(offset) │               │ Returning(offset) │ Frame: the spring moves it
 *   └───────┘         └────────────────┘ ─────────────▶└───────────────────┘
 *     ▲  ▲               │            Let go short of the line, slowly   │ Settled
 *     │  └───────────────┼───────────────────────────────────────────────┘
 *     │                  │ Let go past the line, or flicked: Minimise is sent
 *     │                  ▼
 *     │   Shown     ┌──────────────┐  the screen leaves from where it was let go;
 *     └──────────── │ Away(offset) │  a drag while it leaves does nothing
 *                   └──────────────┘
 *
 * Shown: the screen is the one over the workout again (the rest bar was tapped, a new rest started)
 * while its exit animation still held it, or the program did not put it away. It comes back in place.
 */

/** How far the rest screen is pulled down, and why. */
internal sealed interface RestPull {
    /** Pixels down from its place. */
    val offset: Float

    data object Still : RestPull {
        override val offset = 0f
    }

    /** Under the finger. */
    data class Pulled(override val offset: Float) : RestPull

    /** Let go short of the line: springing back to its place. */
    data class Returning(override val offset: Float) : RestPull

    /** Let go past the line: put away, and leaving. */
    data class Away(override val offset: Float) : RestPull
}

internal sealed interface RestPullEvent {
    /** The finger moved the screen by [delta] pixels, down positive. */
    data class Drag(val delta: Float) : RestPullEvent

    /** The finger let go at [velocity] (px/s, down positive). Past [line] pixels or faster than [flick] puts the screen away. */
    data class LetGo(val velocity: Float, val line: Float, val flick: Float) : RestPullEvent

    /** A frame of the spring back. */
    data class Frame(val offset: Float) : RestPullEvent

    /** The spring back arrived. */
    data object Settled : RestPullEvent

    /** The screen shows over the workout. */
    data object Shown : RestPullEvent
}

/** The pull after an event, and whether the screen is to be put away now ([minimise]). */
internal data class RestPullStep(val pull: RestPull, val minimise: Boolean = false)

internal fun restPullAfter(pull: RestPull, event: RestPullEvent): RestPullStep = when (event) {
    // A drag catches the screen wherever it is, also while it springs back; not while it leaves.
    is RestPullEvent.Drag ->
        if (pull is RestPull.Away) RestPullStep(pull) else RestPullStep(RestPull.Pulled((pull.offset + event.delta).coerceAtLeast(0f)))
    is RestPullEvent.LetGo -> when {
        pull !is RestPull.Pulled -> RestPullStep(pull)
        pull.offset > event.line || event.velocity > event.flick -> RestPullStep(RestPull.Away(pull.offset), minimise = true)
        pull.offset == 0f -> RestPullStep(RestPull.Still)
        else -> RestPullStep(RestPull.Returning(pull.offset))
    }
    // Only the spring moves a returning screen: a frame that comes after a drag caught it changes nothing.
    is RestPullEvent.Frame -> if (pull is RestPull.Returning) RestPullStep(RestPull.Returning(event.offset)) else RestPullStep(pull)
    RestPullEvent.Settled -> if (pull is RestPull.Returning) RestPullStep(RestPull.Still) else RestPullStep(pull)
    RestPullEvent.Shown -> if (pull is RestPull.Away) RestPullStep(RestPull.Still) else RestPullStep(pull)
}

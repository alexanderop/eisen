package io.github.alexanderop.eisen.core.ui

/*
 * Where a reorderable list is with its drag, as one value (see `Reorder.kt`). [reorderPhaseAfter] is the
 * one place it changes. Pure Kotlin, tested in `ReorderPhaseTest`.
 *
 *            Start(key, grab)                   Drop                   Settled
 *   ┌──────┐ ───────────────▶ ┌──────────────┐ ─────▶ ┌───────────────┐ ───────▶ Idle
 *   │ Idle │                  │ Dragging(key)│        │ Dropping(key) │
 *   └──────┘                  └──────────────┘        └───────────────┘
 *                              Move(delta): follows     Start: refused, the item still glides into its
 *                              the finger               place. The handle asks [ReorderPhase.canStart]
 *                                                       before it takes the touch, so the list scrolls instead.
 */

internal sealed interface ReorderPhase {
    data object Idle : ReorderPhase

    /** [key] follows the finger, which holds it [grab] pixels below its top edge and moved [delta] since the pick-up. */
    data class Dragging(val key: Any, val grab: Float, val delta: Float = 0f) : ReorderPhase

    /** [key] glides into its place after the finger lifted. */
    data class Dropping(val key: Any) : ReorderPhase

    /** Only a list at rest takes a new drag. */
    val canStart: Boolean get() = this == Idle
}

internal sealed interface ReorderEvent {
    data class Start(val key: Any, val grab: Float) : ReorderEvent

    data class Move(val delta: Float) : ReorderEvent

    data object Drop : ReorderEvent

    data object Settled : ReorderEvent
}

internal fun reorderPhaseAfter(phase: ReorderPhase, event: ReorderEvent): ReorderPhase = when (phase) {
    ReorderPhase.Idle -> if (event is ReorderEvent.Start) ReorderPhase.Dragging(event.key, event.grab) else phase
    is ReorderPhase.Dragging -> when (event) {
        is ReorderEvent.Move -> phase.copy(delta = phase.delta + event.delta)
        ReorderEvent.Drop -> ReorderPhase.Dropping(phase.key)
        is ReorderEvent.Start, ReorderEvent.Settled -> phase
    }
    is ReorderPhase.Dropping -> if (event == ReorderEvent.Settled) ReorderPhase.Idle else phase
}

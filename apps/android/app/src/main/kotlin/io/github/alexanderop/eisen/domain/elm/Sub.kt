package io.github.alexanderop.eisen.domain.elm

import io.github.alexanderop.eisen.domain.TimerMode

/**
 * A source outside the app that sends Msgs while the model asks for it. The runtime compares the
 * set after every update and starts the new ones and stops the old ones.
 */
sealed interface Sub {
    /**
     * Sends [Msg.Rest.Tick] each time a whole second of the rest at [endsAt] passes, also counting
     * up after it ended. One tick per second is enough for a display in whole seconds.
     */
    data class RestClock(val endsAt: Long) : Sub

    /** Sends [Msg.Hold.Tick] each time a whole second counted from [startsAt] passes. */
    data class HoldClock(val startsAt: Long) : Sub

    /**
     * Sends [Msg.Timer.Tick] each time [every] milliseconds of the run counted from [anchor] pass. A boxing
     * run ticks every half second: its combinations are called on the half second, and the screen shows each as it is said.
     */
    data class TimerClock(val anchor: Long, val every: Long = 1000) : Sub
}

/**
 * Which sources run for [model]: the countdown clock while a rest runs and the app shows, the hold
 * clock while a hold timer is open. That one also runs while the app is away, so a hold that ends
 * meanwhile is logged and heard on time as long as the process lives. So does the interval timer's
 * clock while a run goes (not while it is paused): its service keeps the process and the phone awake.
 */
fun subscriptions(model: Model): Set<Sub> = buildSet {
    val rest = model.rest
    if (rest != null && model.visible) add(Sub.RestClock(rest.endsAt))
    model.hold?.let { add(Sub.HoldClock(it.startsAt)) }
    val run = (model.timer as? TimerState.Running)?.run
    if (run != null && run.pausedAt == null) add(Sub.TimerClock(run.startedAt + run.paused, if (run.config.mode == TimerMode.BOXING) 500 else 1000))
}

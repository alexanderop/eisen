package io.github.alexanderop.eisen.domain.elm

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
}

/**
 * Which sources run for [model]: the countdown clock while a rest runs and the app shows, the hold
 * clock while a hold timer is open. That one also runs while the app is away, so a hold that ends
 * meanwhile is logged and heard on time as long as the process lives.
 */
fun subscriptions(model: Model): Set<Sub> = buildSet {
    val rest = model.rest
    if (rest != null && model.visible) add(Sub.RestClock(rest.endsAt))
    model.hold?.let { add(Sub.HoldClock(it.startsAt)) }
}

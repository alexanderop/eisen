package io.github.alexanderop.eisen.feature.workout.platform

/*
 * What the app knows about a foreground service that shows a running clock: the rest's countdown
 * (`RestService`) and the interval timer's run (`TimerService`). Android starts one on request, may
 * refuse to while the app is not on screen, and may end it on its own. Pure Kotlin, so every
 * transition is a unit test (`ServiceHandleTest`); the adapters only carry out the [ServiceCall].
 *
 *                        Show, Android takes it (ASK)
 *        ┌──────┐ ─────────────────────────────────────▶ ┌───────┐   Show: the notice is updated in place (NOTIFY)
 *        │ Idle │                                        │ Asked │   Started: stays
 *        └──────┘ ◀──────────── Hide (STOP) ──────────── └───────┘
 *          │  ▲                                           │    ▲
 *   Show,  │  │ Hide                    Refusal: Android  │    │ Show (ASK again)
 *   ASK    │  │                         refused the ask,  │    │ Started: a start Android had
 *          ▼  │                         Lost: the service │    │ queued came through anyway
 *        ┌─────────┐ ◀──────────────── ended by itself ───┘    │
 *        │ Refused │ ───────────────────────────────────────────┘
 *        └─────────┘
 *
 * Show from Idle goes to Asked first and asks; a [ServiceEvent.Refusal] right after takes it to Refused.
 * Any event a state does not list leaves it as it is and calls nothing.
 */

/** The service of one notice of type [T]. */
sealed interface ServiceHandle<out T> {
    /** Nothing to show: no service is wanted. */
    data object Idle : ServiceHandle<Nothing>

    /** [notice] should show, but no service runs for it: Android refused it, or it ended. The next Show asks again. */
    data class Refused<T>(val notice: T) : ServiceHandle<T>

    /** The service was asked for and not stopped since: it runs, or is about to, and shows [notice]. */
    data class Asked<T>(val notice: T) : ServiceHandle<T>
}

/** What should show, null when nothing should. The service reads it when Android starts it. */
val <T> ServiceHandle<T>.notice: T?
    get() = when (this) {
        ServiceHandle.Idle -> null
        is ServiceHandle.Refused -> notice
        is ServiceHandle.Asked -> notice
    }

sealed interface ServiceEvent<out T> {
    /** [notice] is what should show now. */
    data class Show<T>(val notice: T) : ServiceEvent<T>

    /** Nothing should show any more. */
    data object Hide : ServiceEvent<Nothing>

    /** Android refused to start the service that was just asked for. */
    data object Refusal : ServiceEvent<Nothing>

    /** The service came up (`onStartCommand` with a start, not a stop). */
    data object Started : ServiceEvent<Nothing>

    /** The service ended (`onDestroy`), asked to or not. */
    data object Lost : ServiceEvent<Nothing>
}

/** What the adapter does for a transition. */
enum class ServiceCall {
    /** `startForegroundService`: the service shows the notice when it comes up. */
    ASK,

    /** The service runs: its notification is posted again with the new notice. */
    NOTIFY,

    /** Asks the running service to stop itself. */
    STOP,
    NONE,
}

/** The one place the handle changes. */
fun <T> transition(handle: ServiceHandle<T>, event: ServiceEvent<T>): Pair<ServiceHandle<T>, ServiceCall> = when (event) {
    is ServiceEvent.Show -> when (handle) {
        is ServiceHandle.Asked -> ServiceHandle.Asked(event.notice) to ServiceCall.NOTIFY
        ServiceHandle.Idle, is ServiceHandle.Refused -> ServiceHandle.Asked(event.notice) to ServiceCall.ASK
    }
    ServiceEvent.Hide -> when (handle) {
        is ServiceHandle.Asked -> ServiceHandle.Idle to ServiceCall.STOP
        ServiceHandle.Idle, is ServiceHandle.Refused -> ServiceHandle.Idle to ServiceCall.NONE
    }
    ServiceEvent.Refusal, ServiceEvent.Lost -> when (handle) {
        is ServiceHandle.Asked -> ServiceHandle.Refused(handle.notice) to ServiceCall.NONE
        ServiceHandle.Idle, is ServiceHandle.Refused -> handle to ServiceCall.NONE
    }
    ServiceEvent.Started -> when (handle) {
        is ServiceHandle.Refused -> ServiceHandle.Asked(handle.notice) to ServiceCall.NONE
        ServiceHandle.Idle, is ServiceHandle.Asked -> handle to ServiceCall.NONE
    }
}

/**
 * Holds the handle of one service and carries out its calls. [ask] is `startForegroundService` and
 * returns false when Android refuses it; [notify] posts the running service's notification again;
 * [stop] asks the service to stop itself. Main thread only, like the runtime that calls it.
 */
class ForegroundService<T>(
    private val ask: () -> Boolean,
    private val notify: () -> Unit,
    private val stop: () -> Unit,
) {
    var handle: ServiceHandle<T> = ServiceHandle.Idle
        private set

    val notice: T? get() = handle.notice

    fun show(notice: T) = send(ServiceEvent.Show(notice))

    fun hide() = send(ServiceEvent.Hide)

    fun started() = send(ServiceEvent.Started)

    fun lost() = send(ServiceEvent.Lost)

    private fun send(event: ServiceEvent<T>) {
        val (next, call) = transition(handle, event)
        handle = next
        when (call) {
            ServiceCall.ASK -> if (!ask()) send(ServiceEvent.Refusal)
            ServiceCall.NOTIFY -> notify()
            ServiceCall.STOP -> stop()
            ServiceCall.NONE -> Unit
        }
    }
}

package io.github.alexanderop.eisen.runtime

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.alexanderop.eisen.domain.Env
import io.github.alexanderop.eisen.domain.elm.Cmd
import io.github.alexanderop.eisen.domain.elm.Flags
import io.github.alexanderop.eisen.domain.elm.Model
import io.github.alexanderop.eisen.domain.elm.Msg
import io.github.alexanderop.eisen.domain.elm.Notifications
import io.github.alexanderop.eisen.domain.elm.Sub
import io.github.alexanderop.eisen.domain.elm.UiEvent
import io.github.alexanderop.eisen.domain.elm.subscriptions
import io.github.alexanderop.eisen.domain.elm.update
import io.github.alexanderop.eisen.domain.freshState
import io.github.alexanderop.eisen.health.HealthSync
import io.github.alexanderop.eisen.rest.Cues
import io.github.alexanderop.eisen.rest.RestSystem
import io.github.alexanderop.eisen.timer.TimerSounds
import io.github.alexanderop.eisen.timer.TimerSystem
import io.github.alexanderop.eisen.widget.HomeSurfaces
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import java.util.UUID
import io.github.alexanderop.eisen.domain.elm.init as initialModel

/**
 * The runtime of The Elm Architecture, and the only impure part of the app's logic:
 *
 * ```
 * Msg ──► update(msg, model, env) ──► Model ──► view(model, dispatch)
 *                                 └─► Cmds  ──► adapters (file, alarms, cues, Health Connect, widget, UI)
 * subscriptions(model) ──► Subs ──► sources that send Msgs (the rest, hold and interval timer clocks)
 * ```
 *
 * It holds the [model] as Compose state, runs [update] for every Msg passed to [dispatch], carries
 * out the Cmds through small adapters and starts or stops the [Sub]s that [subscriptions] asks for.
 * The adapters only do what a Cmd says: an `if` that decides behaviour belongs in `update`.
 *
 * What lives where in a screen:
 * - In the [Model]: everything that decides behaviour or has to survive (the saved data, the workout,
 *   the rest, what Undo can bring back, which tab and whether the workout shows).
 * - In `remember` / `rememberSaveable`: what is only presentation. A scroll position, an animation,
 *   the text being typed before it is sent, a sheet whose opening has no other consequence.
 *
 * Screens never read this object. `MainActivity` passes them the model (or the part they need) and
 * [dispatch]. Entry points without a screen (the rest alarms, the countdown service, the widget,
 * the launcher shortcuts) call [init] and then only [dispatch].
 */
object Eisen {
    /** The current model. Only [dispatch] changes it, always on the main thread. */
    var model: Model by mutableStateOf(Model(freshState()))
        private set

    private val events = Channel<UiEvent>(capacity = 64, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    /**
     * One-shot UI work the activity carries out: haptics, permission dialogs, the share sheet.
     * Snackbars are not here: [snackbars] shows them, so they outlive a recreated activity.
     */
    val uiEvents: Flow<UiEvent> = events.receiveAsFlow()

    /** The app's one snackbar queue. `MainActivity` puts its host on screen. */
    lateinit var snackbars: Snackbars
        private set

    private lateinit var app: Context
    private val main = Handler(Looper.getMainLooper())
    private val queue = ArrayDeque<Msg>()
    private var running = false
    private val sources = mutableMapOf<Sub, () -> Unit>()

    /** The same function every time, so a composable that takes it is skipped when nothing else changed. */
    val dispatch: (Msg) -> Unit = { msg -> dispatch(msg) }

    /** Safe to call again: a broadcast, the service or the widget can start the process without the activity. */
    fun init(context: Context) {
        if (::app.isInitialized) return
        app = context.applicationContext
        snackbars = Snackbars(app, dispatch)
        RestSystem.init(app)
        TimerSystem.init(app)
        HealthSync.start(app, dispatch)
        val flags = Flags(
            state = StateFile.read(app),
            savedRest = Prefs.rest(app),
            healthEnabled = Prefs.healthEnabled(app),
            notifications = Notifications(granted = Prefs.notificationsGranted(app), asked = Prefs.notificationsAsked(app)),
            now = System.currentTimeMillis(),
            savedTimer = TimerFile.read(app),
        )
        val (first, cmds) = initialModel(flags)
        model = first
        cmds.forEach(::execute)
        syncSources()
    }

    /**
     * Runs [update] for [msg]. May be called from any thread: it hops to the main thread, and Msgs
     * are taken one after the other in the order they arrive. On the main thread it runs at once,
     * so the model is new when this returns, unless it was called from inside another update's Cmds;
     * then it runs right after that one.
     */
    fun dispatch(msg: Msg) {
        if (Looper.myLooper() !== Looper.getMainLooper()) {
            main.post { dispatch(msg) }
            return
        }
        queue.addLast(msg)
        if (running) return
        running = true
        try {
            while (true) {
                val next = queue.removeFirstOrNull() ?: break
                val (updated, cmds) = update(next, model, Env(System.currentTimeMillis(), ::newId))
                model = updated
                cmds.forEach(::execute)
                syncSources()
            }
        } finally {
            running = false
        }
    }

    private fun newId(): String = UUID.randomUUID().toString()

    private fun execute(cmd: Cmd) {
        // One failing adapter must not take the others, or the app, down with it.
        runCatching {
            when (cmd) {
                is Cmd.Save -> StateFile.write(app, cmd.state)
                is Cmd.SaveRest -> Prefs.saveRest(app, cmd.rest)
                is Cmd.ScheduleRestAlarms -> RestSystem.schedule(cmd.tenAt, cmd.overAt)
                Cmd.CancelRestAlarms -> RestSystem.cancelAlarms()
                is Cmd.ShowRestCountdown -> RestSystem.showCountdown(cmd.notice)
                Cmd.HideRestCountdown -> RestSystem.hideCountdown()
                is Cmd.ShowRestOver -> RestSystem.showOver(cmd.notice)
                Cmd.HideRestOver -> RestSystem.hideOver()
                is Cmd.PlayCue -> Cues.play(app, cmd.cue)
                is Cmd.Vibrate -> Cues.vibrate(app, cmd.pattern)
                is Cmd.Beep -> Cues.beep(cmd.times)
                is Cmd.After -> main.postDelayed({ dispatch(cmd.msg) }, cmd.millis)
                Cmd.MarkNotificationsAsked -> Prefs.markNotificationsAsked(app)
                is Cmd.SaveHealthEnabled -> Prefs.saveHealthEnabled(app, cmd.enabled)
                is Cmd.Health -> HealthSync.run(cmd.job)
                is Cmd.RefreshHomeSurfaces -> HomeSurfaces.refresh(app, cmd)
                is Cmd.ReadBackup -> Backups.read(app, cmd.uri) { dispatch(Msg.Data.BackupRead(it)) }
                is Cmd.WriteBackup -> Backups.write(app, cmd.uri, cmd.json) { dispatch(Msg.Data.BackupWritten(it)) }
                is Cmd.SaveTimer -> TimerFile.write(app, cmd.run)
                is Cmd.ScheduleTimer -> TimerSystem.schedule(cmd)
                is Cmd.ShowTimer -> TimerSystem.show(cmd.notice)
                Cmd.HideTimer -> TimerSystem.hide()
                is Cmd.PlayTimerSound -> TimerSounds.play(app, cmd.sound)
                // Listed one by one, so a new UiEvent does not compile until it says where it goes.
                is Cmd.Ui -> when (val event = cmd.event) {
                    is UiEvent.Snackbar -> snackbars.show(event)
                    is UiEvent.DismissSnackbar -> snackbars.dismiss(event.tag)
                    is UiEvent.Haptic,
                    UiEvent.RequestNotificationPermission,
                    UiEvent.RequestHealthPermission,
                    is UiEvent.ShareBackup,
                    -> events.trySend(event)
                }
            }
        }.onFailure { Log.w("Eisen", "Command failed: ${cmd::class.simpleName}", it) }
    }

    /** Starts the sources the model asks for and stops the ones it no longer does. */
    private fun syncSources() {
        val wanted = subscriptions(model)
        val gone = sources.keys - wanted
        for (sub in gone) sources.remove(sub)?.invoke()
        for (sub in wanted - sources.keys) sources[sub] = start(sub)
    }

    /** Starts [sub] and returns what stops it. */
    private fun start(sub: Sub): () -> Unit = when (sub) {
        is Sub.RestClock -> everySecond(sub.endsAt, Msg.Rest.Tick)
        is Sub.HoldClock -> everySecond(sub.startsAt, Msg.Hold.Tick)
        is Sub.TimerClock -> everySecond(sub.anchor, Msg.Timer.Tick)
    }

    /** Sends [msg] each time a whole second counted from [anchor] passes. Returns what stops it. */
    private fun everySecond(anchor: Long, msg: Msg): () -> Unit {
        val tick = object : Runnable {
            override fun run() {
                dispatch(msg)
                main.postDelayed(this, untilNextSecond(anchor))
            }
        }
        main.postDelayed(tick, untilNextSecond(anchor))
        return { main.removeCallbacks(tick) }
    }

    /**
     * Time to the next whole second counted from [anchor], when the shown seconds change.
     * A few milliseconds late, so the tick lands just after the change and not just before it.
     */
    private fun untilNextSecond(anchor: Long): Long = Math.floorMod(anchor - System.currentTimeMillis(), 1000L) + 5
}

package io.github.alexanderop.eisen.feature.timer.platform

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import io.github.alexanderop.eisen.MainActivity
import io.github.alexanderop.eisen.R
import io.github.alexanderop.eisen.app.elm.Cmd
import io.github.alexanderop.eisen.app.elm.Msg
import io.github.alexanderop.eisen.app.elm.TIMER_CUE_LATE_MILLIS
import io.github.alexanderop.eisen.app.elm.TimerNotice
import io.github.alexanderop.eisen.feature.workout.platform.Cues
import io.github.alexanderop.eisen.app.runtime.Eisen

private const val ACTION_PAUSE = "io.github.alexanderop.eisen.timer.PAUSE"
private const val ACTION_RESUME = "io.github.alexanderop.eisen.timer.RESUME"
private const val ACTION_END = "io.github.alexanderop.eisen.timer.END"
private const val ACTION_STOP = "io.github.alexanderop.eisen.timer.STOP"

private const val CHANNEL = "timer_running"
private const val NOTICE_ID = 3

/**
 * The interval timer's part of Android: it plays the schedule `update` worked out (sounds and
 * vibrations at their times on the wall clock) with the phone kept awake, and shows the run's
 * notification through [TimerService]. Which sounds, when, and what the notification says is decided
 * in `update` (see `Msg.Timer`); this only does it.
 */
internal object TimerSystem {
    private const val TAG = "EisenTimer"

    private lateinit var app: Context
    private val main = Handler(Looper.getMainLooper())
    private val pending = mutableListOf<Runnable>()
    private var awake: PowerManager.WakeLock? = null
    private var serviceAsked = false

    /** What the notification shows. Null once it should go. [TimerService] reads it. */
    var notice: TimerNotice? = null
        private set

    fun init(context: Context) {
        app = context.applicationContext
        app.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "Interval timer", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Shows the running interval timer and lets you pause or end it"
            },
        )
    }

    /** Replaces what plays. The old sounds are dropped and a line being said stops. */
    fun schedule(cmd: Cmd.ScheduleTimer) {
        clear()
        val now = System.currentTimeMillis()
        for (cue in cmd.sounds) later(cue.at - now) {
            val late = System.currentTimeMillis() - cue.at
            if (late > TIMER_CUE_LATE_MILLIS) Log.d(TAG, "skipped ${cue.sound}: ${late}ms late") else TimerSounds.play(app, cue.sound)
        }
        for (buzz in cmd.vibrations) later(buzz.at - now) {
            if (System.currentTimeMillis() - buzz.at <= TIMER_CUE_LATE_MILLIS) Cues.vibrate(app, buzz.pattern)
        }
        val until = cmd.awakeUntil
        if (until != null && until > now) {
            // The screen may go off: the sounds still have to come on the second.
            awake = app.getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "eisen:timer").apply {
                setReferenceCounted(false)
                acquire(until - now)
            }
        }
    }

    fun show(next: TimerNotice) {
        notice = next
        if (serviceAsked) {
            // The service runs: the notification it shows is updated in place.
            runCatching { app.getSystemService(NotificationManager::class.java).notify(NOTICE_ID, notification()) }
            return
        }
        // Android refuses the service when the app is not visible, for example after the process was killed.
        runCatching { ContextCompat.startForegroundService(app, Intent(app, TimerService::class.java)) }
            .onSuccess { serviceAsked = true }
    }

    /** The run is over: nothing more plays, the phone may sleep, the service and its notification go. */
    fun hide() {
        clear()
        notice = null
        if (!serviceAsked) return
        serviceAsked = false
        runCatching { app.startService(Intent(app, TimerService::class.java).setAction(ACTION_STOP)) }
            .onFailure { app.stopService(Intent(app, TimerService::class.java)) }
    }

    private fun later(delay: Long, block: () -> Unit) {
        val task = Runnable { block() }
        pending += task
        main.postDelayed(task, maxOf(0, delay))
    }

    private fun clear() {
        for (task in pending) main.removeCallbacks(task)
        pending.clear()
        TimerSounds.stop()
        awake?.let { if (it.isHeld) it.release() }
        awake = null
    }

    /** The notification of [notice], or a stand-in when the run ended before the service came up. */
    fun notification(): Notification {
        val current = notice ?: TimerNotice("Timer", "", null, countDown = false, paused = false, toggleLabel = "Pause", endLabel = "End")
        val builder = NotificationCompat.Builder(app, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_eisen)
            .setContentTitle(current.title)
            .setContentText(current.text)
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
            .setOngoing(true)
            // Android 16 shows it as a live chip in the status bar and on the always-on display, like the rest.
            .setRequestPromotedOngoing(true)
            .setOnlyAlertOnce(true)
            .setColor(app.getColor(R.color.lime))
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(openApp())
            .addAction(0, current.toggleLabel, broadcast(if (current.paused) ACTION_RESUME else ACTION_PAUSE))
            .addAction(0, current.endLabel, broadcast(ACTION_END))
        // A WOD says what to do in this round: in one line, and a line each when the notification is open.
        if (current.lines.isNotEmpty()) {
            builder.setContentText(listOf(current.text, current.lines.joinToString(", ")).joinToString(" · "))
                .setStyle(NotificationCompat.BigTextStyle().bigText((listOf(current.text) + current.lines).joinToString("\n")))
        }
        val base = current.base
        if (base != null) {
            builder.setShowWhen(true).setWhen(base).setUsesChronometer(true).setChronometerCountDown(current.countDown)
        } else {
            builder.setShowWhen(false)
        }
        return builder.build()
    }

    private fun broadcast(action: String): PendingIntent = PendingIntent.getBroadcast(
        app, 0, Intent(app, TimerReceiver::class.java).setAction(action),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun openApp(): PendingIntent = PendingIntent.getActivity(
        app, 0, Intent(app, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    internal fun isStop(intent: Intent?): Boolean = intent?.action == ACTION_STOP

    internal fun message(action: String?): Msg? = when (action) {
        ACTION_PAUSE -> Msg.Timer.Pause
        ACTION_RESUME -> Msg.Timer.Resume
        // The notification cannot ask "End timer?": it saves what was done, like "Save and end".
        ACTION_END -> Msg.Timer.SaveAndEnd
        else -> null
    }

    internal const val ID = NOTICE_ID
}

/**
 * Runs while an interval timer runs or is paused, and owns its notification. Its work is in
 * [TimerSystem]: Android only lets an app with a running foreground service play its sounds and
 * keep going from the background. `update` starts and stops it through `Cmd.ShowTimer` / `Cmd.HideTimer`.
 */
class TimerService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Eisen.init(applicationContext)
        if (TimerSystem.isStop(intent)) {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        // Always, also when the run ended meanwhile: a service asked for as foreground has to become one.
        ServiceCompat.startForeground(this, TimerSystem.ID, TimerSystem.notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        if (TimerSystem.notice == null) stopSelf()
        return START_NOT_STICKY
    }
}

/** The Pause, Resume and End buttons of the timer's notification end up here, as Msgs. */
class TimerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Eisen.init(context.applicationContext)
        TimerSystem.message(intent.action)?.let(Eisen.dispatch)
    }
}

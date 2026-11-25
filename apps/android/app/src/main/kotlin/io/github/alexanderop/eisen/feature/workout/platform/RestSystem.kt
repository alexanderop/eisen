package io.github.alexanderop.eisen.feature.workout.platform

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import android.util.Log
import io.github.alexanderop.eisen.BuildConfig
import io.github.alexanderop.eisen.MainActivity
import io.github.alexanderop.eisen.R
import io.github.alexanderop.eisen.app.elm.RestCountdown
import io.github.alexanderop.eisen.app.elm.RestOver
import io.github.alexanderop.eisen.app.elm.RestState
import io.github.alexanderop.eisen.app.elm.restCountdown

const val ACTION_TEN = "io.github.alexanderop.eisen.rest.TEN"
const val ACTION_OVER = "io.github.alexanderop.eisen.rest.OVER"
const val ACTION_EXTEND = "io.github.alexanderop.eisen.rest.EXTEND"
const val ACTION_STOP = "io.github.alexanderop.eisen.rest.STOP"

private const val CHANNEL_RUNNING = "rest_running"
private const val CHANNEL_OVER = "rest_over"

/** The countdown. It belongs to [RestService] and goes away with it. */
internal const val RUNNING_ID = 1
private const val OVER_ID = 2

/**
 * The rest timer's part of Android: two system alarms ("ten seconds left" and "over") so the cues come
 * on time with the screen off or the app closed, the countdown notification that [RestService] shows
 * on the lock screen, and the "Rest is over" notification. `update` decides when each is needed
 * (see `Msg.Rest`); this only does it.
 */
internal object RestSystem {
    private lateinit var app: Context

    /** The countdown service and what its notification shows, see [ForegroundService]. */
    private val service = ForegroundService<RestCountdown>(
        // Android refuses the service when the app is not visible. The alarms still fire then.
        ask = { runCatching { ContextCompat.startForegroundService(app, Intent(app, RestService::class.java)) }.isSuccess },
        // The service runs: the countdown it shows is updated in place.
        notify = { runCatching { app.getSystemService(NotificationManager::class.java).notify(RUNNING_ID, countdownNotification()) } },
        // Stopping it from outside can beat its start, and Android ends an app whose foreground service
        // was asked for but never showed up: it is asked to stop itself.
        stop = {
            runCatching { app.startService(Intent(app, RestService::class.java).setAction(ACTION_STOP)) }
                .onFailure { app.stopService(Intent(app, RestService::class.java)) }
        },
    )

    /** What the countdown notification shows. Null once it should go. [RestService] reads it. */
    val countdown: RestCountdown? get() = service.notice

    fun init(context: Context) {
        app = context.applicationContext
        createChannels()
    }

    fun schedule(tenAt: Long?, overAt: Long) {
        val alarms = app.getSystemService(AlarmManager::class.java)
        if (tenAt != null) schedule(alarms, tenAt, broadcast(ACTION_TEN)) else alarms.cancel(broadcast(ACTION_TEN))
        schedule(alarms, overAt, broadcast(ACTION_OVER))
    }

    fun cancelAlarms() {
        val alarms = app.getSystemService(AlarmManager::class.java)
        alarms.cancel(broadcast(ACTION_TEN))
        alarms.cancel(broadcast(ACTION_OVER))
    }

    fun showCountdown(notice: RestCountdown) = service.show(notice)

    fun hideCountdown() = service.hide()

    /** [RestService] came up, or ended: the next countdown asks for it again when it is gone. */
    internal fun started() = service.started()
    internal fun lost() = service.lost()

    /** Android drops it by itself when notifications are not allowed. */
    fun showOver(notice: RestOver) {
        val notification = NotificationCompat.Builder(app, CHANNEL_OVER)
            .setSmallIcon(R.drawable.ic_stat_eisen)
            .setContentTitle(notice.title)
            .setContentText(notice.text)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setShowWhen(true)
            .setWhen(notice.endedAt)
            .setUsesChronometer(true)
            .setColor(app.getColor(R.color.lime))
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setContentIntent(openApp())
            .setDeleteIntent(broadcast(ACTION_STOP))
            .build()
        app.getSystemService(NotificationManager::class.java).notify(OVER_ID, notification)
    }

    fun hideOver() {
        app.getSystemService(NotificationManager::class.java).cancel(OVER_ID)
    }

    /**
     * The countdown notification, or a stand-in when the rest ended before the service came up. It
     * qualifies for Android 16's Live Update like the timer's (see `TimerSystem.notification`): the
     * chip in the status bar is its chronometer, the bar one segment of the rest.
     */
    fun countdownNotification(): Notification {
        val notice = countdown ?: System.currentTimeMillis().let { now -> restCountdown(RestState(now, now, ""), now) }
        return NotificationCompat.Builder(app, CHANNEL_RUNNING)
            .setSmallIcon(R.drawable.ic_stat_eisen)
            .setContentTitle(notice.title)
            .setContentText(notice.text)
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
            .setOngoing(true)
            .setRequestPromotedOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(true)
            .setWhen(notice.endsAt)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setColor(app.getColor(R.color.lime))
            .setColorized(false)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(openApp())
            .addAction(0, notice.extendLabel, broadcast(ACTION_EXTEND))
            .addAction(0, notice.skipLabel, broadcast(ACTION_STOP))
            .setStyle(
                NotificationCompat.ProgressStyle()
                    .setProgressSegments(notice.progress.segments.map { NotificationCompat.ProgressStyle.Segment(it.seconds.coerceAtLeast(1)).setColor(app.getColor(R.color.rest)) })
                    .setProgress(notice.progress.at),
            )
            .build()
            .also { if (BuildConfig.DEBUG) Log.d("EisenRest", "countdown promotable=${NotificationCompat.hasPromotableCharacteristics(it)}") }
    }

    private fun schedule(alarms: AlarmManager, at: Long, intent: PendingIntent) {
        if (alarms.canScheduleExactAlarms()) alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, intent)
        else alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, intent)
    }

    private fun broadcast(action: String): PendingIntent = PendingIntent.getBroadcast(
        app, 0, Intent(app, RestReceiver::class.java).setAction(action),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun openApp(): PendingIntent = PendingIntent.getActivity(
        app, 0, Intent(app, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun createChannels() {
        val manager = app.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_RUNNING, "Rest timer", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Counts down the rest between sets"
            },
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_OVER, "Rest over", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Tells you when the rest is over"
                // The app plays its own cue and vibrates itself, so the setting in the app decides.
                setSound(null, null)
                enableVibration(false)
            },
        )
    }
}

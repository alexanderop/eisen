package io.github.alexanderop.eisen.rest

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import io.github.alexanderop.eisen.MainActivity
import io.github.alexanderop.eisen.R
import io.github.alexanderop.eisen.data.Store

/** A running rest. Based on timestamps, so it stays right when the app is closed. */
data class RestState(val startedAt: Long, val endsAt: Long, val note: String)

const val ACTION_TEN = "io.github.alexanderop.eisen.rest.TEN"
const val ACTION_OVER = "io.github.alexanderop.eisen.rest.OVER"
const val ACTION_EXTEND = "io.github.alexanderop.eisen.rest.EXTEND"
const val ACTION_STOP = "io.github.alexanderop.eisen.rest.STOP"

private const val PREFS = "rest"
private const val CHANNEL_RUNNING = "rest_running"
private const val CHANNEL_OVER = "rest_over"
/** The countdown. It belongs to [RestService] and goes away with it. */
internal const val RUNNING_ID = 1
private const val OVER_ID = 2
/** Long enough for the spoken cue to finish before the service lets go. */
private const val CUE_MILLIS = 4000L
private const val WARN_SECONDS = 10
/** A shorter rest would get the warning right after it started. */
private const val WARN_MIN_REST_SECONDS = 20
const val EXTEND_SECONDS = 30

/**
 * Rest timer between sets. The system holds two alarms, "ten seconds left" and "over",
 * so the cues come on time with the screen off or the app closed. While the rest counts
 * down, [RestService] shows the countdown on the lock screen with the same buttons as the app.
 * It also keeps the app in the state in which Android lets it play the cues.
 */
object Rest {
    /** Null when no rest is running. A rest that is over stays until it is dismissed. */
    var state: RestState? by mutableStateOf(null)
        private set

    private lateinit var app: Context
    private var serviceAsked = false

    /** Safe to call again: an alarm can start the process without the activity. */
    fun init(context: Context) {
        if (::app.isInitialized) return
        app = context.applicationContext
        createChannels()
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val endsAt = prefs.getLong("endsAt", 0)
        if (endsAt > 0 && Store.state.active != null) {
            state = RestState(prefs.getLong("startedAt", 0), endsAt, prefs.getString("note", "") ?: "")
        } else if (endsAt > 0) {
            stop()
        }
    }

    fun start(seconds: Int, note: String) {
        val now = System.currentTimeMillis()
        begin(RestState(now, now + seconds * 1000L, note), warn = seconds >= WARN_MIN_REST_SECONDS)
    }

    fun extend(seconds: Int = EXTEND_SECONDS) {
        val current = state ?: return
        begin(current.copy(endsAt = maxOf(current.endsAt, System.currentTimeMillis()) + seconds * 1000L), warn = true)
    }

    fun stop() {
        state = null
        app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
        val alarms = app.getSystemService(AlarmManager::class.java)
        alarms.cancel(broadcast(ACTION_TEN))
        alarms.cancel(broadcast(ACTION_OVER))
        stopService()
        app.getSystemService(NotificationManager::class.java).cancel(OVER_ID)
    }

    private fun begin(next: RestState, warn: Boolean) {
        state = next
        app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putLong("startedAt", next.startedAt).putLong("endsAt", next.endsAt).putString("note", next.note).apply()
        val alarms = app.getSystemService(AlarmManager::class.java)
        val tenAt = next.endsAt - WARN_SECONDS * 1000L
        if (warn && tenAt > System.currentTimeMillis()) schedule(alarms, tenAt, broadcast(ACTION_TEN)) else alarms.cancel(broadcast(ACTION_TEN))
        schedule(alarms, next.endsAt, broadcast(ACTION_OVER))
        app.getSystemService(NotificationManager::class.java).cancel(OVER_ID)
        // Android refuses the service when the app is not visible. The alarms still fire then.
        runCatching { ContextCompat.startForegroundService(app, Intent(app, RestService::class.java)) }
            .onSuccess { serviceAsked = true }
    }

    /**
     * Asks the service to stop itself. Stopping it from outside can beat its start,
     * and Android ends an app whose foreground service was asked for but never showed up.
     */
    private fun stopService() {
        if (!serviceAsked) return
        serviceAsked = false
        runCatching { app.startService(Intent(app, RestService::class.java).setAction(ACTION_STOP)) }
            .onFailure { app.stopService(Intent(app, RestService::class.java)) }
    }

    private fun schedule(alarms: AlarmManager, at: Long, intent: PendingIntent) {
        val exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms()
        if (exact) alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, intent)
        else alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, intent)
    }

    /** Called by the alarm when the rest is over. */
    internal fun onOver() {
        val current = state ?: return
        // Extended meanwhile: the old alarm is not the end any more.
        if (System.currentTimeMillis() < current.endsAt - 500) return
        Cues.over(app)
        showOver(current)
        Handler(Looper.getMainLooper()).postDelayed({
            // Still the same rest: nothing counts down any more, so the service can go.
            if (state == current) stopService()
        }, CUE_MILLIS)
    }

    internal fun onTen() {
        val current = state ?: return
        val left = current.endsAt - System.currentTimeMillis()
        if (left in 1..(WARN_SECONDS * 1000L + 2000)) Cues.ten(app)
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

    private fun canNotify(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(app, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /** The countdown notification of the running rest. */
    internal fun runningNotification(): Notification {
        val current = state ?: RestState(0, System.currentTimeMillis(), "")
        return NotificationCompat.Builder(app, CHANNEL_RUNNING)
            .setSmallIcon(R.drawable.ic_stat_eisen)
            .setContentTitle("Rest")
            .setContentText(current.note)
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(true)
            .setWhen(current.endsAt)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setColor(app.getColor(R.color.lime))
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(openApp())
            .addAction(0, "+${EXTEND_SECONDS}s", broadcast(ACTION_EXTEND))
            .addAction(0, "Skip", broadcast(ACTION_STOP))
            .build()
    }

    private fun showOver(current: RestState) {
        if (!canNotify()) return
        val notification = NotificationCompat.Builder(app, CHANNEL_OVER)
            .setSmallIcon(R.drawable.ic_stat_eisen)
            .setContentTitle("Rest is over")
            .setContentText("Start your next set.")
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setShowWhen(true)
            .setWhen(current.endsAt)
            .setUsesChronometer(true)
            .setColor(app.getColor(R.color.lime))
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setContentIntent(openApp())
            .setDeleteIntent(broadcast(ACTION_STOP))
            .build()
        app.getSystemService(NotificationManager::class.java).notify(OVER_ID, notification)
    }
}

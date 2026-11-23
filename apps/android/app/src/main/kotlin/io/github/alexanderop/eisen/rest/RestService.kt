package io.github.alexanderop.eisen.rest

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.ServiceCompat
import io.github.alexanderop.eisen.runtime.Eisen

/**
 * Runs while a rest counts down and owns the countdown notification.
 * It has no work of its own: Android only lets an app with a running foreground
 * service start a sound from the background, and the cues are exactly that.
 * `update` starts and stops it through `Cmd.ShowRestCountdown` / `Cmd.HideRestCountdown`.
 */
class RestService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Eisen.init(applicationContext)
        if (intent?.action == ACTION_STOP) {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        // Always, also when the rest ended meanwhile: a service asked for as foreground has to become one.
        ServiceCompat.startForeground(this, RUNNING_ID, RestSystem.countdownNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        if (RestSystem.countdown == null) stopSelf()
        return START_NOT_STICKY
    }
}

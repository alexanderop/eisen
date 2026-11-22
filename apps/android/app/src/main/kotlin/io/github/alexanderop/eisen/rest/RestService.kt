package io.github.alexanderop.eisen.rest

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.ServiceCompat
import io.github.alexanderop.eisen.data.Store

/**
 * Runs while a rest counts down and owns the countdown notification.
 * It has no work of its own: Android only lets an app with a running foreground
 * service start a sound from the background, and the cues are exactly that.
 */
class RestService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Store.init(applicationContext)
        Rest.init(applicationContext)
        if (intent?.action == ACTION_STOP) {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        // Always, also when the rest ended meanwhile: a service asked for as foreground has to become one.
        ServiceCompat.startForeground(this, RUNNING_ID, Rest.runningNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        if (Rest.state == null) stopSelf()
        return START_NOT_STICKY
    }
}

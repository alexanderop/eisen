package io.github.alexanderop.eisen.rest

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.alexanderop.eisen.data.Store

/** The alarms of the rest timer and the buttons on its notification end up here. */
class RestReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Store.init(context.applicationContext)
        Rest.init(context.applicationContext)
        when (intent.action) {
            ACTION_TEN -> Rest.onTen()
            ACTION_OVER -> Rest.onOver()
            ACTION_EXTEND -> Rest.extend()
            ACTION_STOP -> Rest.stop()
        }
    }
}

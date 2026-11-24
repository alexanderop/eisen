package io.github.alexanderop.eisen.feature.workout.platform

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.alexanderop.eisen.app.elm.Msg
import io.github.alexanderop.eisen.app.runtime.Eisen

/** The alarms of the rest timer and the buttons on its notifications end up here, as Msgs. */
class RestReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Eisen.init(context.applicationContext)
        val msg = when (intent.action) {
            ACTION_TEN -> Msg.Rest.AlarmTen
            ACTION_OVER -> Msg.Rest.AlarmOver
            ACTION_EXTEND -> Msg.Rest.Extend
            ACTION_STOP -> Msg.Rest.Dismiss
            else -> return
        }
        Eisen.dispatch(msg)
    }
}

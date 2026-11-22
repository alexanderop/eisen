package io.github.alexanderop.eisen

import android.app.Application
import io.github.alexanderop.eisen.data.Store
import io.github.alexanderop.eisen.health.HealthSync
import io.github.alexanderop.eisen.rest.Rest
import io.github.alexanderop.eisen.widget.HomeSurfaces

class EisenApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Store.init(this)
        Rest.init(this)
        // After Store.init, so Health Connect sync starts from the loaded history.
        HealthSync.init(this, Store.state)
        Store.finishListeners += HealthSync::onFinished
        Store.changeListeners += HealthSync::onChanged
        // The home-screen widget and the launcher shortcuts follow every change.
        HomeSurfaces.install(this)
    }
}

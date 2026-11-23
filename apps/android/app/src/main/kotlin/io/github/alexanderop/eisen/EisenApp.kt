package io.github.alexanderop.eisen

import android.app.Application
import io.github.alexanderop.eisen.runtime.Eisen

class EisenApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Before any receiver, service or activity: they all read and dispatch to the same model.
        Eisen.init(this)
    }
}

package io.github.alexanderop.eisen.app.runtime

import android.app.Application

class EisenApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Before any receiver, service or activity: they all read and dispatch to the same model.
        Eisen.init(this)
    }
}

package io.github.alexanderop.eisen

import android.app.Application
import io.github.alexanderop.eisen.data.Store
import io.github.alexanderop.eisen.rest.Rest

class EisenApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Store.init(this)
        Rest.init(this)
    }
}

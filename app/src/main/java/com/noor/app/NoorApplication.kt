package com.noor.app

import android.app.Application
import com.noor.app.data.LocationRepository
import com.noor.app.data.LocationWorker
import com.noor.app.data.SettingsRepository
import com.noor.app.data.WearSync
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Simple manual dependency container; the app is small enough not to need a DI framework. */
class AppContainer(app: Application) {
    val settings = SettingsRepository(app)
    val location = LocationRepository(app, settings)
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val wear = WearSync(app, settings)
}

class NoorApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.wear.start(container.scope)
        container.scope.launch {
            val s = container.settings.current()
            LocationWorker.sync(this@NoorApplication, s.backgroundUpdates && container.location.hasBackgroundPermission())
        }
    }
}

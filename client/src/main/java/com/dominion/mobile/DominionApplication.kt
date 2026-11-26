package com.dominion.mobile

import android.app.Application

class DominionApplication : Application() {
    private val appContainer = lazy(LazyThreadSafetyMode.SYNCHRONIZED) { AppContainer(this) }
    val container: AppContainer get() = appContainer.value
    val containerReady: Boolean get() = appContainer.isInitialized()
}

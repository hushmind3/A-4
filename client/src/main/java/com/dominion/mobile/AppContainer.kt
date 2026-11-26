package com.dominion.mobile

import android.content.Context
import com.dominion.mobile.data.DemoRepository
import com.dominion.mobile.data.DominionRepository
import com.dominion.mobile.data.local.LocalStore
import com.dominion.mobile.data.remote.ApiFactory
import com.dominion.mobile.data.remote.RemoteBackedRepository

class AppContainer(context: Context) {

    // Separate demo fixtures from each real server's private cache.
    private val cacheScope = if (BuildConfig.USE_DEMO_DATA) "demo" else {
        java.security.MessageDigest.getInstance("SHA-256")
            .digest(BuildConfig.API_BASE_URL.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }
    val store = LocalStore(context, cacheScope)
    val cloudAccount by lazy { com.dominion.mobile.data.remote.CloudAccountRepository(context.applicationContext) }

    val repository: DominionRepository = if (BuildConfig.USE_DEMO_DATA) {
        DemoRepository(store)
    } else {
        RemoteBackedRepository(ApiFactory.create(store), store)
    }
}

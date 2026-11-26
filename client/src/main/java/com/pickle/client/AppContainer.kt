package com.pickle.client

import android.content.Context
import com.pickle.client.data.DemoRepository
import com.pickle.client.data.PickleRepository
import com.pickle.client.data.local.LocalStore
import com.pickle.client.data.remote.ApiFactory
import com.pickle.client.data.remote.RemoteBackedRepository

class AppContainer(context: Context) {

    val store = LocalStore(context)

    val repository: PickleRepository = if (BuildConfig.USE_DEMO_DATA) {
        DemoRepository(store)
    } else {
        RemoteBackedRepository(ApiFactory.create(store), store)
    }
}

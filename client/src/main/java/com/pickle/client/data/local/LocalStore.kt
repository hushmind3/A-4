package com.pickle.client.data.local

import android.content.Context
import com.google.gson.GsonBuilder
import com.pickle.client.data.model.AppState

/**
 * Small local cache. The real client stores the session token securely and keeps a
 * last-known-good snapshot of the server state so the app can open offline.
 */
class LocalStore(context: Context) {

    private val prefs = context.getSharedPreferences("pickle_client", Context.MODE_PRIVATE)
    private val gson = GsonBuilder().serializeNulls().create()

    fun loadState(): AppState? = runCatching {
        prefs.getString(KEY_STATE, null)?.let { gson.fromJson(it, AppState::class.java) }
    }.getOrNull()

    fun saveState(state: AppState) {
        runCatching { prefs.edit().putString(KEY_STATE, gson.toJson(state)).apply() }
    }

    fun clearState() {
        prefs.edit().remove(KEY_STATE).apply()
    }

    var authToken: String?
        get() = prefs.getString(KEY_TOKEN, null)
        set(value) = prefs.edit().putString(KEY_TOKEN, value).apply()

    var accountId: String?
        get() = prefs.getString(KEY_ACCOUNT, null)
        set(value) = prefs.edit().putString(KEY_ACCOUNT, value).apply()

    private companion object {
        const val KEY_STATE = "state_v1"
        const val KEY_TOKEN = "auth_token"
        const val KEY_ACCOUNT = "account_id"
    }
}

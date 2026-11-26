package com.dominion.mobile.data.local

import android.content.Context
import com.google.gson.GsonBuilder
import com.dominion.mobile.data.model.AppState

/**
 * Small local cache. Session tokens require migration to Android Keystore before production. Keeps a
 * last-known-good snapshot of the server state so the app can open offline.
 */
class LocalStore(context: Context, scope: String = "demo") {

    private val prefs = context.getSharedPreferences("dominion_mobile_$scope", Context.MODE_PRIVATE)
    private val gson = GsonBuilder().serializeNulls().create()

    fun loadState(): AppState? = runCatching {
        prefs.getString(KEY_STATE, null)?.let { gson.fromJson(it, AppState::class.java)?.copy(call = null, typingConversationId = null, vaultUnlocked = false) }
    }.getOrNull()

    fun saveState(state: AppState) {
        runCatching { prefs.edit().putString(KEY_STATE, gson.toJson(state.copy(call = null, typingConversationId = null, vaultUnlocked = false))).apply() }
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

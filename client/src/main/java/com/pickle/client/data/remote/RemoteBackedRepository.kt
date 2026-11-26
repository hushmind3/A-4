package com.pickle.client.data.remote

import com.pickle.client.data.DemoData
import com.pickle.client.data.PickleRepository
import com.pickle.client.data.local.LocalStore
import com.pickle.client.data.model.AppState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Repository for a real backend. It keeps an offline snapshot and merges server
 * responses over it. Enable it with USE_DEMO_DATA = false in client/build.gradle.kts
 * and set API_BASE_URL to your server.
 */
class RemoteBackedRepository(
    private val api: PickleApi,
    private val store: LocalStore
) : PickleRepository {

    private val _state = MutableStateFlow(store.loadState() ?: DemoData.initial(onboarded = true))
    override val state: StateFlow<AppState> = _state.asStateFlow()

    override fun update(transform: (AppState) -> AppState) {
        _state.update(transform)
        store.saveState(_state.value)
    }

    override suspend fun refresh() {
        val account = runCatching { api.me().data?.toDomain() }.getOrNull()
        val characters = runCatching { api.characters().data.map { it.toDomain() } }.getOrNull()
        val conversations = runCatching { api.conversations().data.map { it.toDomain() } }.getOrNull()
        val tasks = runCatching { api.tasks().data.map { it.toDomain() } }.getOrNull()
        val schedules = runCatching { api.schedules().data.map { it.toDomain() } }.getOrNull()
        val memories = runCatching { api.memories().data.map { it.toDomain() } }.getOrNull()
        val friends = runCatching { api.friends().data.map { it.toDomain() } }.getOrNull()
        val connections = runCatching { api.connections().data.map { it.toDomain() } }.getOrNull()
        val explore = runCatching { api.explore().data.map { it.toDomain() } }.getOrNull()
        val vault = runCatching { api.vault().data.map { it.toDomain() } }.getOrNull()

        _state.update { current ->
            current.copy(
                account = account ?: current.account,
                characters = characters ?: current.characters,
                conversations = conversations ?: current.conversations,
                tasks = tasks ?: current.tasks,
                schedules = schedules ?: current.schedules,
                memories = memories ?: current.memories,
                friends = friends ?: current.friends,
                connections = connections ?: current.connections,
                explore = explore ?: current.explore,
                vault = vault ?: current.vault
            )
        }
        store.saveState(_state.value)
    }

    override fun reset() {
        store.clearState()
        _state.value = DemoData.initial()
    }
}

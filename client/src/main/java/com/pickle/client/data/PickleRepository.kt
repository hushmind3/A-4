package com.pickle.client.data

import com.pickle.client.data.local.LocalStore
import com.pickle.client.data.model.AppState
import com.pickle.client.data.model.Message
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * The single data boundary used by the UI. Swapping the demo implementation for
 * [com.pickle.client.data.remote.RemoteBackedRepository] does not change any screen.
 */
interface PickleRepository {
    val state: StateFlow<AppState>
    fun update(transform: (AppState) -> AppState)
    suspend fun refresh() = Unit
    suspend fun sendRemote(conversationId: String, text: String): List<Message> = emptyList()
    fun reset()
}

class DemoRepository(
    private val store: LocalStore? = null
) : PickleRepository {

    private val _state = MutableStateFlow((store?.loadState() ?: DemoData.initial()).copy(onboarded = true))
    override val state: StateFlow<AppState> = _state.asStateFlow()

    override fun update(transform: (AppState) -> AppState) {
        _state.update(transform)
        store?.saveState(_state.value)
    }

    override fun reset() {
        _state.value = DemoData.initial(onboarded = true)
        store?.saveState(_state.value)
    }
}

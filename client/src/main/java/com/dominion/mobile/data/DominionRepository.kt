package com.dominion.mobile.data

import com.dominion.mobile.data.local.LocalStore
import com.dominion.mobile.data.model.AppState
import com.dominion.mobile.data.model.Message
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * The single data boundary used by the UI. Swapping the demo implementation for
 * [com.dominion.mobile.data.remote.RemoteBackedRepository] does not change any screen.
 */
interface DominionRepository {
    val state: StateFlow<AppState>
    val isDemo: Boolean get() = false

    /** Acknowledge one outbound message; only the remote implementation performs HTTP. */
    suspend fun deliverMessage(conversationId: String, message: Message): Message = message

    /** Fetch messages after a successful send; null means not supported. */
    suspend fun loadMessages(conversationId: String): List<Message>? = null

    /** Wait for an asynchronous server reply, without sending the message again. */
    suspend fun pollForReply(conversationId: String, knownIds: Set<String>): List<Message> =
        emptyList()

    fun update(transform: (AppState) -> AppState)
    suspend fun refresh() = Unit
    fun reset()
}

class DemoRepository(
    private val store: LocalStore? = null
) : DominionRepository {

    private val _state = MutableStateFlow((store?.loadState() ?: DemoData.initial()).copy(onboarded = true))
    override val state: StateFlow<AppState> = _state.asStateFlow()
    override val isDemo: Boolean = true

    override fun update(transform: (AppState) -> AppState) {
        _state.update(transform)
        store?.saveState(_state.value)
    }

    override fun reset() {
        _state.value = DemoData.initial(onboarded = true)
        store?.saveState(_state.value)
    }
}

package com.dominion.mobile.data.remote

import com.dominion.mobile.data.model.Account
import com.dominion.mobile.data.model.Plan
import com.dominion.mobile.data.DominionRepository
import com.dominion.mobile.data.local.LocalStore
import com.dominion.mobile.data.model.AppState
import com.dominion.mobile.data.model.Message
import com.dominion.mobile.data.model.MessageSender
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class RemoteBackedRepository(
    private val api: DominionApi,
    private val store: LocalStore
) : DominionRepository {

    private fun emptyRemoteState() = AppState(
        onboarded = true,
        account = Account(
            name = "DOMINION", birthday = "", phone = "", email = "",
            plan = Plan.FREE, jelly = 0, energy = 0, energyMax = 0,
            tickets = 0, credits = 0, inviteCode = "", level = 1, xp = 0
        )
    )

    private val _state = MutableStateFlow((store.loadState() ?: emptyRemoteState()).copy(onboarded = true))
    override val state: StateFlow<AppState> = _state.asStateFlow()

    override suspend fun deliverMessage(conversationId: String, message: Message): Message {
        // Keep the stable client id across retries so cooperating servers can deduplicate.
        val dto = api.sendMessage(
            conversationId,
            SendMessageRequest(text = message.text, client_message_id = message.id)
        ).data
        return requireNotNull(dto?.toDomain()?.takeIf { it.id.isNotBlank() }) {
            "Server did not acknowledge the message with an ID"
        }
    }

    override suspend fun loadMessages(conversationId: String): List<Message> =
        api.messages(conversationId).data.map { it.toDomain() }

    override suspend fun pollForReply(conversationId: String, knownIds: Set<String>): List<Message> {
        // Progressive polling: roughly one minute in total, no duplicate POST.
        for (waitMs in listOf(1500L, 2500L, 4000L, 6500L, 10000L, 15000L, 20000L)) {
            delay(waitMs)
            val rows = try {
                loadMessages(conversationId)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            }
            if (rows != null && rows.any {
                it.sender == MessageSender.ASSISTANT && it.id.isNotBlank() && it.id !in knownIds
            }) return rows
        }
        return emptyList()
    }

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
                conversations = conversations?.map { serverConv ->
                    val existing = current.conversation(serverConv.id)
                    if (serverConv.messages.isEmpty() && existing != null) {
                        serverConv.copy(messages = existing.messages, updatedAt = maxOf(serverConv.updatedAt, existing.updatedAt))
                    } else {
                        serverConv
                    }
                } ?: current.conversations,
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
        _state.value = emptyRemoteState()
    }
}
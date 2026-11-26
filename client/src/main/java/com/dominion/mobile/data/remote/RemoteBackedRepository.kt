package com.dominion.mobile.data.remote

import com.dominion.mobile.data.model.Account
import com.dominion.mobile.data.model.Plan
import com.dominion.mobile.data.DominionRepository
import com.dominion.mobile.data.AssistantCatalog
import com.dominion.mobile.data.ConnectionState
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import retrofit2.HttpException
import java.io.IOException
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
    private val endpoint: DominionApi?,
    private val store: LocalStore
) : DominionRepository {

    private val api: DominionApi get() = requireNotNull(endpoint) { "Connect DOMINION first" }

    private fun emptyRemoteState() = AppState(
        onboarded = true,
        characters = emptyList(),
        explore = AssistantCatalog.explore(),
        connections = AssistantCatalog.connections(),
        account = Account(
            name = "DOMINION", birthday = "", phone = "", email = "",
            plan = Plan.FREE, jelly = 0, energy = 0, energyMax = 0,
            tickets = 0, credits = 0, inviteCode = "", level = 1, xp = 0
        )
    )

    private val _state = MutableStateFlow((store.loadState() ?: emptyRemoteState()).let { cached ->
        cached.copy(onboarded = true,
            explore = (AssistantCatalog.explore() + cached.explore).distinctBy { it.id },
            connections = (AssistantCatalog.connections() + cached.connections).distinctBy { it.id }
                .map { it.copy(connected = false, lastUsed = null) })
    })
    override val state: StateFlow<AppState> = _state.asStateFlow()
    private val _connection = MutableStateFlow(if (endpoint == null) ConnectionState.AUTH_REQUIRED else ConnectionState.CONNECTING)
    override val connection = _connection.asStateFlow()
    private val refreshLock = Mutex()

    init { store.saveState(_state.value) }

    override suspend fun searchMessages(query: String): List<Message> = store.searchMessages(query)

    override suspend fun deliverMessage(conversationId: String, message: Message): Message {
        // Use the native room endpoint, which persists a job and deduplicates retries.
        var room = api.engineRoom(conversationId)
        // Only the dedicated mobile assistant is initialized automatically. Other
        // existing rooms retain the owner's explicit execution settings.
        if (!room.toolsEnabled && conversationId == "assistant") {
            api.updateRoom(conversationId, RoomConfigUpdate(toolsEnabled = true))
            room = api.engineRoom(conversationId)
        }
        check(room.toolsEnabled) { "대화방 설정에서 도구를 활성화하세요." }
        val submitted = api.submitRoomMessage(conversationId, RoomInput(message.text, message.clientMessageId ?: message.id,
            if (state.value.settings.lang == com.dominion.mobile.data.model.Lang.KO) "ko" else "default",
            message.attachmentIds.orEmpty()))
        val row = requireNotNull(submitted.message)
        require(row.id.isNotBlank() && submitted.jobId.isNotBlank())
        return message.copy(id = row.id, clientMessageId = message.clientMessageId ?: message.id,
            status = com.dominion.mobile.data.model.MessageStatus.SENT, jobId = submitted.jobId)
    }

    override suspend fun engineTools(): List<EngineTool> = api.engineTools()

    override suspend fun roomExecution(roomId: String): RoomExecution = coroutineScope {
        val snapshot = api.roomExecution(roomId)
        require(snapshot.room?.id == roomId)
        val session = requireNotNull(snapshot.sessionId).also { require(it.isNotBlank()) }
        // The room state includes a bounded historical approval list. Read the
        // live pending queue separately and never show another room's requests.
        val pending = api.pendingApprovals().filter { it.sessionId == session }
        snapshot.copy(approvals = pending,
            jobs = snapshot.jobs.orEmpty().filter { it.sessionId == session && it.input?.roomId == roomId })
    }

    override suspend fun decideApproval(roomId: String, approval: EngineApproval,
        allow: Boolean): ApprovalResult {
        val current = roomExecution(roomId)
        val session = requireNotNull(current.sessionId)
        require(approval.actionableIn(session))
        val latest = current.approvals.orEmpty().firstOrNull { it.id == approval.id }
            ?: error("Approval no longer pending")
        require(latest.actionableIn(session) && latest.argsDigest == approval.argsDigest &&
            latest.tool == approval.tool && latest.args == approval.args && latest.status == approval.status)
        val result = api.decideApproval(ApprovalDecision(requireNotNull(latest.id), allow))
        check(result.resolved && result.status == if (allow) "approved" else "denied")
        return result
    }

    override suspend fun cancelJob(roomId: String, jobId: String): EngineJob {
        val current = roomExecution(roomId)
        val job = current.jobs.orEmpty().firstOrNull { it.id == jobId }
            ?: error("Job does not belong to this room")
        check(job.status in setOf("queued", "running"))
        val result = api.cancelEngineJob(jobId)
        require(result.id == jobId)
        return result
    }

    override suspend fun uploadAttachment(roomId: String, name: String, mimeType: String, bytes: ByteArray): EngineAttachment {
        require(bytes.isNotEmpty() && bytes.size <= 8 * 1024 * 1024)
        require(name.isNotBlank() && name.length <= 240 && name.none { it.isISOControl() })
        require(state.value.conversation(roomId) != null)
        val uploaded = api.uploadAttachment(AttachmentUpload(roomId, name, mimeType,
            java.util.Base64.getEncoder().encodeToString(bytes)))
        require(uploaded.roomId == roomId && uploaded.size == bytes.size.toLong())
        java.util.UUID.fromString(requireNotNull(uploaded.id))
        return uploaded
    }

    override suspend fun downloadAttachment(roomId: String, id: String): ByteArray {
        java.util.UUID.fromString(id)
        require(state.value.conversation(roomId)?.messages?.any { id in it.attachmentIds.orEmpty() } == true)
        val body = api.attachmentContent(id)
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            body.use { response ->
                require(response.contentLength() <= com.dominion.mobile.platform.attachments.LocalAttachment.MAX_BYTES)
                response.byteStream().use(com.dominion.mobile.platform.attachments.LocalAttachment.Companion::readBounded)
            }
        }
    }

    override suspend fun createConversation(title: String, characterId: String): String {
        require(state.value.character(characterId) != null)
        val id = java.util.UUID.randomUUID().toString()
        // Use the owner's existing assistant model pool if configured.
        val existing = api.engineRooms().firstOrNull { it.id == "assistant" }
        val room = api.createRoom(RoomCreate(id, title.ifBlank { "새 대화" }.take(160),
            listOf(characterId), models = existing?.models.orEmpty()))
        check(room.id == id && room.toolsEnabled)
        update { current ->
            current.copy(conversations = listOf(com.dominion.mobile.data.model.Conversation(
                id = room.id, characterId = characterId, titleOverride = room.name,
                kind = com.dominion.mobile.data.model.ConversationKind.DIRECT
            )) + current.conversations.filterNot { it.id == room.id })
        }
        return room.id
    }

    override suspend fun requestVoiceCall(conversationId: String): CallTokenResponse =
        requireNotNull(api.startCall(conversationId, StartCallRequest(video = false)).data) {
            "Server did not issue a call session"
        }.requireUsable()

    override suspend fun loadMessages(conversationId: String): List<Message> =
        api.nativeRoomMessages(conversationId).mapNotNull { it.toDomain(conversationId) }

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
        if (endpoint == null) { _connection.value = ConnectionState.AUTH_REQUIRED; return }
        if (!refreshLock.tryLock()) return
        try {
            if (_connection.value != ConnectionState.CONNECTED) {
                _connection.value = ConnectionState.CONNECTING
            }
            coroutineScope {
                // Essential server data first; preserve cached state when the server is unreachable.
                val accountRequest = async { requireNotNull(api.me().data) { "Missing account" }.toDomain() }
                val agentsRequest = async { api.characters().data.map { it.toDomain() } }
                val roomsRequest = async { api.conversations().data.map { it.toDomain() } }
                val account = accountRequest.await()
                val characters = agentsRequest.await()
                val conversations = roomsRequest.await()
                update { current ->
                    current.copy(
                        account = account,
                        characters = characters.map { character ->
                            current.characterPreferences.orEmpty()[character.id]?.applyTo(character) ?: character
                        },
                        conversations = conversations.map { serverConv ->
                            val existing = current.conversation(serverConv.id)
                            if (existing != null) serverConv.copy(
                                messages = com.dominion.mobile.data.MessageSync.reconcile(existing.messages, serverConv.messages),
                                draft = existing.draft,
                                pinned = existing.pinned,
                                muted = existing.muted,
                                updatedAt = maxOf(serverConv.updatedAt, existing.updatedAt)
                            ) else serverConv
                        }
                    )
                }
                _connection.value = ConnectionState.CONNECTED
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: HttpException) {
            _connection.value = if (failure.code() == 401 || failure.code() == 403) {
                ConnectionState.AUTH_REQUIRED
            } else {
                ConnectionState.UNAVAILABLE
            }
        } catch (_: IOException) {
            _connection.value = ConnectionState.OFFLINE
        } catch (_: Exception) {
            _connection.value = ConnectionState.UNAVAILABLE
        } finally {
            refreshLock.unlock()
        }
    }

    override fun reset() {
        _connection.value = ConnectionState.CONNECTING
        store.clearState()
        _state.value = emptyRemoteState()
    }
}
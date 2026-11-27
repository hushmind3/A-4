package com.dominion.mobile.data.remote

import com.dominion.mobile.data.model.Account
import com.dominion.mobile.data.model.Plan
import com.dominion.mobile.data.DominionRepository
import com.dominion.mobile.data.AssistantCatalog
import com.dominion.mobile.data.ConnectionState
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import retrofit2.HttpException
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
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
    private val store: LocalStore,
    private val onRemindersChanged: (List<com.dominion.mobile.data.model.ScheduleItem>) -> Unit = {},
    private val onJobAccepted: (String, String) -> Unit = { _, _ -> }
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

    private val remindersLock = Mutex()

    override suspend fun changeSchedules(transform: (List<com.dominion.mobile.data.model.ScheduleItem>) ->
        List<com.dominion.mobile.data.model.ScheduleItem>) = remindersLock.withLock {
        _state.update { current -> current.copy(schedules = transform(current.schedules)) }
        store.saveStateNow(_state.value)
        onRemindersChanged(state.value.schedules)
    }

    override suspend fun searchMessages(query: String): List<Message> = store.searchMessages(query)

    override suspend fun engineSkills(): List<EngineSkillState> = api.engineSkills()

    private val skillChangeLock = Mutex()

    override suspend fun inspectEngineSkill(id: String): SkillReview {
        require(id.isNotBlank() && id.length <= 200 && id.none { it.isISOControl() })
        return SkillReview.fromRecord(api.engineSkill(id)).also { check(it.id == id) }
    }

    override suspend fun changeEngineSkill(review: SkillReview, enabled: Boolean) = skillChangeLock.withLock {
        val record = api.engineSkill(review.id)
        check(SkillReview.fromRecord(record) == review) { "Skill changed; review again" }
        // Retain all server fields, including skill instructions and path.
        val next = record.deepCopy().apply {
            addProperty("enabled", enabled)
            if (enabled && (review.status.isNullOrBlank() || review.status == "draft")) addProperty("status", "active")
        }
        if (enabled) check(SkillReview.fromRecord(next).status == "active")
        val saved = SkillReview.fromRecord(api.saveEngineSkill(review.id, next))
        check(saved.id == review.id && saved.enabled == enabled &&
            saved.instructions == review.instructions && saved.name == review.name)
        val confirmed = SkillReview.fromRecord(api.engineSkill(review.id))
        check(confirmed.id == review.id && confirmed.enabled == enabled &&
            confirmed.instructions == review.instructions && (!enabled || confirmed.status == "active"))
    }

    override suspend fun dispatchScheduledTask(id: String, occurrence: Long) {
        val task = remindersLock.withLock {
            val item = state.value.schedules.firstOrNull { it.id == id } ?: return
            check(item.enabled && com.dominion.mobile.data.ScheduledDispatch.due(item, System.currentTimeMillis()) == occurrence)
            val proposed = com.dominion.mobile.data.ScheduledDispatch.task(item, occurrence)
            val existing = state.value.tasks.firstOrNull { it.id == proposed.id }
            check(existing == null || existing.requestDetails == proposed.requestDetails)
            val reserved = item.copy(pendingDispatchAt = occurrence, dispatchStatus = "접수 확인 중")
            update { current -> current.copy(
                schedules = current.schedules.map { if (it.id == id) reserved else it },
                tasks = if (existing == null) listOf(proposed) + current.tasks else current.tasks) }
            store.saveStateNow(state.value)
            existing ?: proposed
        }
        // The saved task nonce is stable across network errors and process restarts.
        try {
            val live = state.value.schedules.firstOrNull { it.id == id }
            if (live?.enabled != true || live.pendingDispatchAt != occurrence) return
            submitTask(task.id)
            val confirmed = state.value.tasks.firstOrNull { it.id == task.id }
            check(!confirmed?.engineJobId.isNullOrBlank() && !confirmed?.engineSessionId.isNullOrBlank())
            changeSchedules { rows -> rows.map { row ->
                if (row.id == id && row.pendingDispatchAt == occurrence)
                    row.copy(pendingDispatchAt = null, lastDispatchedAt = occurrence, dispatchStatus = "본체 접수 확인")
                else row
            } }
        } catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (e: Exception) {
            changeSchedules { rows -> rows.map { row ->
                if (row.id == id && row.pendingDispatchAt == occurrence)
                    row.copy(dispatchStatus = "연결·접수 재확인 대기")
                else row
            } }
            throw e
        }
    }

    private val taskLock = Mutex()

    private suspend fun saveTaskLink(item: com.dominion.mobile.data.model.TaskItem) {
        update { current -> current.copy(tasks = current.tasks.map { existing ->
            if (existing.id != item.id) existing else existing.copy(
                engineRoomId = item.engineRoomId, engineJobId = item.engineJobId,
                engineSessionId = item.engineSessionId, engineMessageId = item.engineMessageId,
                engineStatus = item.engineStatus, enginePrompt = item.enginePrompt,
                engineLanguage = item.engineLanguage)
        }) }
        store.saveStateNow(state.value)
    }

    override suspend fun submitTask(id: String) = taskLock.withLock {
        var task = state.value.tasks.firstOrNull { it.id == id } ?: error("Task not found")
        if (task.engineJobId != null) {
            verifyTask(task)
            return@withLock
        }
        require(state.value.character(task.characterId) != null)
        // A dedicated one-agent room avoids assigning a personal task to a group.
        // The deterministic room ID also survives a lost create response.
        val roomId = task.engineRoomId ?: TaskDispatch.roomId(id)
        if (task.enginePrompt == null || task.engineRoomId == null) {
            task = task.copy(engineRoomId = roomId, enginePrompt = TaskDispatch.prompt(task),
                engineLanguage = if (state.value.settings.lang == com.dominion.mobile.data.model.Lang.KO) "ko" else "default",
                engineStatus = "submitting")
            saveTaskLink(task)
        }
        val existing = api.engineRooms().firstOrNull { it.id == roomId }
        val room = existing ?: api.createRoom(RoomCreate(roomId, task.title.take(160),
            listOf(task.characterId), models = api.engineRooms().firstOrNull { it.id == "assistant" }?.models.orEmpty()))
        check(room.id == roomId && room.toolsEnabled && room.agentIds == listOf(task.characterId))
        update { current ->
            if (current.conversation(roomId) != null) current
            else current.copy(conversations = listOf(com.dominion.mobile.data.model.Conversation(
                roomId, task.characterId, titleOverride = room.name,
                kind = com.dominion.mobile.data.model.ConversationKind.TASK)) + current.conversations)
        }
        val submitted = api.submitRoomMessage(roomId, RoomInput(requireNotNull(task.enginePrompt),
            TaskDispatch.messageNonce(id), requireNotNull(task.engineLanguage)))
        val session = requireNotNull(submitted.sessionId).also { require(it.isNotBlank()) }
        val messageId = requireNotNull(submitted.message?.id).also { require(it.isNotBlank()) }
        require(submitted.jobId.isNotBlank())
        task = task.copy(engineJobId = submitted.jobId, engineSessionId = session,
            engineMessageId = messageId, engineStatus = submitted.status)
        // Save the receipt before any read-back, so a failed GET cannot replay the request.
        saveTaskLink(task)
        runCatching { onJobAccepted(roomId, submitted.jobId) }
        verifyTask(task)
    }

    override suspend fun refreshTask(id: String) = taskLock.withLock {
        val task = state.value.tasks.firstOrNull { it.id == id } ?: return@withLock
        if (task.engineJobId != null) verifyTask(task)
    }

    private suspend fun verifyTask(task: com.dominion.mobile.data.model.TaskItem) {
        val job = api.engineJob(requireNotNull(task.engineJobId))
        check(TaskDispatch.matches(task, job)) { "Task receipt mismatch" }
        saveTaskLink(task.copy(engineStatus = requireNotNull(job.status)))
    }

    private val memoryLock = Mutex()
    private val renameLock = Mutex()

    override suspend fun refreshMemories() = memoryLock.withLock {
        val rows = api.engineMemories().mapNotNull(EngineMemory::toDomain)
        update { current -> current.copy(memories = mergeMemories(current.memories, rows)) }
    }

    override suspend fun syncMemory(id: String) = memoryLock.withLock {
        val item = state.value.memories.firstOrNull { it.id == id } ?: error("Memory not found")
        if (item.serverBacked == true) return@withLock
        require(state.value.character(item.characterId) != null)
        val request = MemoryWrite.from(item)
        // Reconcile a lost POST response with the same durable ID; never overwrite
        // a different record that happens to have the same ID.
        val previous = api.engineMemories().firstOrNull { it.id == id }
        if (previous != null) check(request.matches(previous))
        else check(request.matches(api.saveEngineMemory(request)))
        val verified = api.engineMemories().firstOrNull { it.id == id }
        check(verified != null && request.matches(verified) && verified.retrievalEligible == true)
        val row = requireNotNull(verified.toDomain())
        update { current -> current.copy(memories = current.memories.map { if (it.id == id) row else it }) }
    }

    override suspend fun deleteMemory(id: String) = memoryLock.withLock {
        val item = state.value.memories.firstOrNull { it.id == id } ?: return@withLock
        if (item.serverBacked == true) {
            check(api.deleteEngineMemory(id).get("ok")?.asBoolean == true)
            check(api.engineMemories().none { it.id == id })
        } else if (endpoint != null) {
            // A prior save might have reached the server without its response.
            val existing = api.engineMemories().firstOrNull { it.id == id }
            if (existing != null) {
                check(MemoryWrite.from(item).matches(existing))
                check(api.deleteEngineMemory(id).get("ok")?.asBoolean == true)
                check(api.engineMemories().none { it.id == id })
            }
        }
        update { current -> current.copy(memories = current.memories.filterNot { it.id == id }) }
    }

    override suspend fun renameRoom(id: String, title: String) = renameLock.withLock {
        val clean = title.trim()
        require(clean.isNotBlank() && clean.codePointCount(0, clean.length) <= 160)
        val result = api.renameEngineRoom(id, RoomNameUpdate(clean))
        check(result.id == id && result.name == clean)
        val confirmed = api.engineRoom(id)
        check(confirmed.id == id && confirmed.name == clean)
        update { current -> current.copy(conversations = current.conversations.map {
            if (it.id == id) it.copy(titleOverride = confirmed.name) else it
        }) }
    }

    override suspend fun saveOwnerProfile(name: String, birthday: String): Boolean = refreshLock.withLock {
        val clean = name.trim()
        require(clean.isNotBlank() && clean.codePointCount(0, clean.length) <= 60)
        // The existing native engine owns the name and avatar; birthday is a mobile
        // personalization field because the engine profile schema has no birthday.
        if (endpoint != null) {
            val previous = api.ownerProfile()
            require(previous.id == "global" && previous.avatar != null)
            val saved = api.saveOwnerProfile(OwnerProfileUpdate(clean, previous.avatar))
            check(saved.id == "global" && saved.name == clean)
            val confirmed = api.ownerProfile()
            check(confirmed.id == "global" && confirmed.name == clean)
        }
        update { it.copy(account = it.account.copy(name = clean, birthday = birthday)) }
        store.saveStateNow(state.value)
        endpoint != null
    }

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
        // Local scheduling failure must not cause a duplicate server submission.
        runCatching { onJobAccepted(conversationId, submitted.jobId) }
        return message.copy(id = row.id, clientMessageId = message.clientMessageId ?: message.id,
            status = com.dominion.mobile.data.model.MessageStatus.SENT, jobId = submitted.jobId,
            jobStatus = submitted.status)
    }

    private val roomEventCursor = java.util.concurrent.ConcurrentHashMap<Pair<String, String>, Long>()

    override fun roomSignals(roomId: String, sessionId: String): kotlinx.coroutines.flow.Flow<Unit> =
        if (endpoint == null) kotlinx.coroutines.flow.emptyFlow()
        else roomEventSignals(api, roomId, sessionId, roomEventCursor)

    override suspend fun engineTools(): List<EngineTool> = api.engineTools()

    override suspend fun roomExecution(roomId: String): RoomExecution = coroutineScope {
        val snapshot = api.roomExecution(roomId)
        require(snapshot.room?.id == roomId)
        val session = requireNotNull(snapshot.sessionId).also { require(it.isNotBlank()) }
        // The room state includes a bounded historical approval list. Read the
        // live pending queue separately and never show another room's requests.
        val pending = api.pendingApprovals().filter { it.sessionId == session }
        val jobs = snapshot.jobs.orEmpty().filter { it.sessionId == session && it.input?.roomId == roomId }
        update { current ->
            current.copy(conversations = current.conversations.map { conversation ->
                if (conversation.id == roomId)
                    conversation.copy(messages = correlateRoomJobs(conversation.messages, roomId, session, jobs))
                else conversation
            })
        }
        snapshot.copy(approvals = pending, jobs = jobs)
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

    private val connectorLock = Mutex()
    private val connectorGson = com.google.gson.Gson()

    override suspend fun serviceAccounts(): List<ServiceAccount> = api.serviceAccounts().also { accounts ->
        update { current ->
            current.copy(connections = current.connections.map { connection ->
                if (connection.builtIn) connection else connection.copy(
                    connected = accounts.any { it.authorizes(connection.provider) })
            })
        }
    }

    override suspend fun serviceConnectors(): List<ServiceConnector> =
        api.serviceConnectors().map { connectorGson.fromJson(it, ServiceConnector::class.java) }

    override suspend fun openServiceLogin(id: String) {
        val account = api.serviceAccounts().firstOrNull { it.id == id }
            ?: error("본체에 등록되지 않은 서비스")
        require(account.configured)
        // The existing engine owns the callback (often desktop loopback).
        // Start authorization on that computer, never pass the owner token to an external URL.
        val result = api.openServiceLogin(id)
        check(result.get("opened")?.asBoolean == true)
    }

    override suspend fun disconnectServiceAccount(id: String) {
        require(api.serviceAccounts().any { it.id == id && it.connected })
        val result = api.disconnectServiceAccount(id)
        require(result.get("connected")?.asBoolean == false)
        check(serviceAccounts().any { it.id == id && !it.connected })
    }

    override suspend fun registerServiceConnector(registration: ConnectorRegistration, token: String): ServiceConnector {
        connectorLock.lock()
        try {
            registration.validate()
            val before = serviceConnectors()
            val existing = before.firstOrNull { it.id == registration.id }
            // A retry may update the SAME record only. Never send an existing bearer to another host.
            require(existing == null || (existing.url == registration.url && existing.name == registration.name.trim()))
            require(before.none { it.id != registration.id && it.name.equals(registration.name.trim(), true) })
            val saved = api.saveServiceConnector(registration.record(token))
            check(saved.id == registration.id && saved.url == registration.url &&
                saved.enabled == true && saved.transport == "http")
            val verified = serviceConnectors().firstOrNull { it.id == registration.id } ?: error("등록 확인 실패")
            check(verified.url == registration.url && verified.enabled != false && verified.name == registration.name.trim())
            return verified
        } finally { connectorLock.unlock() }
    }

    override suspend fun setServiceConnectorEnabled(id: String, enabled: Boolean) {
        connectorLock.lock()
        try {
            val record = api.serviceConnectors().firstOrNull { it.get("id")?.asString == id }
                ?: error("외부 서비스가 없습니다.")
            require(record.get("transport")?.asString == "http")
            val patch = record.deepCopy().apply {
                remove("apiKey")
                addProperty("enabled", enabled)
                if (enabled) addProperty("approval", true)
            }
            val saved = api.saveServiceConnector(patch)
            check(saved.id == id && saved.enabled == enabled)
            check(serviceConnectors().any { it.id == id && it.enabled == enabled })
        } finally { connectorLock.unlock() }
    }

    override suspend fun openServiceWebsite(url: String) {
        val uri = java.net.URI(url)
        require(uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.userInfo == null)
        // Manual user navigation only. No automatic password entry or credential export.
        val result = api.browserAction(mapOf("action" to "open", "url" to url))
        check(result.get("error") == null || result.get("error").isJsonNull)
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

    override suspend fun voiceStatus(): EngineVoiceStatus = api.voiceStatus()

    override suspend fun transcribeVoice(wav: ByteArray): String {
        require(com.dominion.mobile.data.WaveAudio.isWave(wav) && wav.size <= 2_000_044)
        check(api.voiceStatus().whisper?.available == true) { "Whisper unavailable" }
        return api.transcribe(wav.toRequestBody("audio/wav".toMediaType())).text.orEmpty().trim()
            .also { require(it.isNotBlank() && it.length <= 32000) }
    }

    override suspend fun synthesizeVoice(text: String): ByteArray {
        require(text.isNotBlank() && text.codePointCount(0, text.length) <= 2000)
        check(api.voiceStatus().piper?.available == true) { "Piper unavailable" }
        val response = api.synthesize(mapOf("text" to text))
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            response.use { body ->
                require(body.contentType()?.toString()?.startsWith("audio/wav") == true)
                body.byteStream().use(com.dominion.mobile.platform.attachments.LocalAttachment.Companion::readBounded)
                    .also { require(com.dominion.mobile.data.WaveAudio.isWave(it)) }
            }
        }
    }

    override suspend fun requestVoiceCall(conversationId: String, video: Boolean): CallTokenResponse =
        requireNotNull(api.startCall(conversationId, StartCallRequest(video = video)).data) {
            "Server did not issue a call session"
        }.requireUsable()

    private val roomMutationLock = Mutex()

    override suspend fun refreshRoomHistory(id: String) = roomMutationLock.withLock {
        val before = api.roomExecution(id)
        val session = requireNotNull(before.sessionId).also { require(it.isNotBlank()) }
        check(before.room?.id == id)
        val messages = loadMessages(id)
        val after = api.engineRoom(id)
        check(after.id == id && after.sessionId == session)
        update { current -> current.copy(conversations = current.conversations.map { room ->
            if (room.id != id) room else com.dominion.mobile.data.RoomHistory.reconcile(room, session, messages)
        }) }
    }

    override suspend fun resetRoom(id: String, restoreLastRequest: Boolean) = roomMutationLock.withLock {
        refreshLock.withLock {
            val local = requireNotNull(state.value.conversation(id))
            check(local.messages.none { it.status != com.dominion.mobile.data.model.MessageStatus.SENT }) {
                "Resolve pending messages before reset"
            }
            val before = api.roomExecution(id)
            check(before.room?.id == id)
            val previous = requireNotNull(before.sessionId).also { require(it.isNotBlank()) }
            val draft = if (restoreLastRequest) loadMessages(id).lastOrNull {
                it.sender == MessageSender.USER && it.text.isNotBlank()
            }?.text ?: error("No previous request") else ""
            val receipt = api.resetEngineRoom(id)
            check(receipt.ok && receipt.roomId == id && receipt.previousSessionId == previous &&
                !receipt.sessionId.isNullOrBlank() && receipt.sessionId != previous)
            val session = requireNotNull(receipt.sessionId)
            val confirmed = api.engineRoom(id)
            check(confirmed.id == id && confirmed.sessionId == session)
            val messages = loadMessages(id)
            check(api.engineRoom(id).sessionId == session)
            update { current -> current.copy(conversations = current.conversations.map { room ->
                if (room.id != id) room else room.copy(sessionId = session, messages = messages, draft = draft)
            }) }
            store.saveStateNow(state.value)
        }
    }

    override suspend fun removeRoom(id: String) = roomMutationLock.withLock {
        refreshLock.withLock {
            val local = requireNotNull(state.value.conversation(id))
            check(local.messages.none { it.status != com.dominion.mobile.data.model.MessageStatus.SENT })
            // The legacy mobile list automatically recreates its assistant room.
            // Leaving that default room is a local hide; do not delete it remotely.
            if (id != "assistant") {
                check(api.removeEngineRoom(id).get("ok")?.asBoolean == true)
                check(api.engineRooms().none { it.id == id })
            }
            update { current -> current.copy(hiddenRooms = current.hiddenRooms.orEmpty() + id,
                conversations = current.conversations.filterNot { it.id == id }) }
            store.saveStateNow(state.value)
        }
    }

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
                val accountRequest = async {
                    val account = requireNotNull(api.me().data) { "Missing account" }.toDomain()
                    // /v1/me in branch1 reads the legacy profile ID; the native settings
                    // endpoint is authoritative for the actual shared owner name.
                    val profile = try { api.ownerProfile() }
                    catch (e: HttpException) {
                        if (e.code() in listOf(404, 405)) null else throw e
                    }
                    if (profile != null) {
                        require(profile.id == "global" && !profile.name.isNullOrBlank())
                        account.copy(name = profile.name)
                    } else account
                }
                val agentsRequest = async { api.characters().data.map { it.toDomain() } }
                val roomsRequest = async { api.conversations().data.map { it.toDomain() } }
                val account = accountRequest.await()
                val characters = agentsRequest.await()
                val conversations = roomsRequest.await()
                update { current ->
                    current.copy(
                        account = account.copy(birthday = current.account.birthday),
                        characters = characters.map { character ->
                            current.characterPreferences.orEmpty()[character.id]?.applyTo(character) ?: character
                        },
                        conversations = conversations.filterNot { it.id in current.hiddenRooms.orEmpty() }.map { serverConv ->
                            val existing = current.conversation(serverConv.id)
                            if (existing != null) serverConv.copy(
                                messages = com.dominion.mobile.data.MessageSync.reconcile(existing.messages, serverConv.messages),
                                draft = existing.draft,
                                sessionId = existing.sessionId,
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
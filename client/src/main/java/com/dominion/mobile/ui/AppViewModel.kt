package com.dominion.mobile.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.dominion.mobile.data.MessageSync
import com.dominion.mobile.data.DominionRepository
import com.dominion.mobile.data.model.Account
import com.dominion.mobile.data.model.ActivityItem
import com.dominion.mobile.data.model.ActivityType
import com.dominion.mobile.data.model.AppState
import com.dominion.mobile.data.model.CallSession
import com.dominion.mobile.data.model.CardKind
import com.dominion.mobile.data.model.CardStep
import com.dominion.mobile.data.model.Character
import com.dominion.mobile.data.model.CharacterPreference
import com.dominion.mobile.data.model.CharacterShape
import com.dominion.mobile.data.model.Connection
import com.dominion.mobile.data.model.Conversation
import com.dominion.mobile.data.model.ConversationKind
import com.dominion.mobile.data.model.CreditTransaction
import com.dominion.mobile.data.model.Friend
import com.dominion.mobile.data.model.Lang
import com.dominion.mobile.data.model.MemoryItem
import com.dominion.mobile.data.model.Message
import com.dominion.mobile.data.model.MessageCard
import com.dominion.mobile.data.model.MessageKind
import com.dominion.mobile.data.model.MessageSender
import com.dominion.mobile.data.model.MessageStatus
import com.dominion.mobile.data.model.ProductKind
import com.dominion.mobile.data.model.Repeat
import com.dominion.mobile.data.model.ScheduleItem
import com.dominion.mobile.data.model.SettingsState
import com.dominion.mobile.data.model.StoreProduct
import com.dominion.mobile.data.model.TabId
import com.dominion.mobile.data.model.ThemeMode
import com.dominion.mobile.data.model.TaskItem
import com.dominion.mobile.data.model.TxKind
import com.dominion.mobile.data.model.VaultItem
import com.dominion.mobile.data.model.VaultType
import com.dominion.mobile.data.model.VoiceTone
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.StateFlow
import com.dominion.mobile.data.model.CallPhase
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.TimeUnit

class AppViewModel(
    private val repo: DominionRepository
) : ViewModel() {

    val state: StateFlow<AppState> = repo.state

    val connection = repo.connection
    val localExecution get()=repo.localExecution
    fun selectMobileModel(model:String){
        require(localExecution&&model.matches(Regex("[A-Za-z0-9@._:/-]{1,160}")))
        mutate{it.copy(settings=it.settings.copy(mobileModel=model))}
    }

    private val _quickEntry = MutableStateFlow<Boolean?>(null)
    val quickEntry = _quickEntry.asStateFlow()
    fun requestQuickEntry(voice: Boolean) { _quickEntry.value = voice }
    fun consumeQuickEntry() { _quickEntry.value = null }

    private val _notificationRoom = MutableStateFlow<String?>(null)
    val notificationRoom = _notificationRoom.asStateFlow()
    fun openRoomFromNotification(roomId: String) {
        if (roomId.matches(Regex("[A-Za-z0-9_-]{1,160}"))) _notificationRoom.value = roomId
    }
    fun consumeNotificationRoom() { _notificationRoom.value = null }

    suspend fun refreshServer() = repo.refresh()
    suspend fun synchronizeServer() = repo.synchronizeEngine()
    fun engineChangeSignals()=repo.engineChangeSignals()
    suspend fun synchronizeChange(change:com.dominion.mobile.data.remote.EngineChange)=repo.synchronizeChange(change)

    suspend fun searchMessages(query: String) = repo.searchMessages(query)

    fun retryServerConnection() {
        viewModelScope.launch { refreshServer() }
    }

    private fun mutate(transform: (AppState) -> AppState) = repo.update(transform)

    private fun AppState.withConversation(id: String, transform: (Conversation) -> Conversation): AppState =
        copy(conversations = conversations.map { if (it.id == id) transform(it) else it })

    private fun id(prefix: String) = "${prefix}_${UUID.randomUUID()}"

    // --------------------------------------------------------------------- shell

    fun selectTab(tab: TabId) = mutate { it.copy(currentTab = tab) }

    private var profileSave: kotlinx.coroutines.Job? = null

    fun updateAccountProfile(name: String, birthday: String, onSaved: (Boolean) -> Unit = {}) {
        if (profileSave?.isActive == true) return
        profileSave = viewModelScope.launch {
            try {
                val serverVerified = repo.saveOwnerProfile(name, birthday)
                onSaved(serverVerified)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { _notice.value = "프로필 저장을 확인하지 못했습니다. 이름과 본체 연결을 확인하세요." }
        }
    }

    suspend fun saveCharacterPhoto(id: String, photo: String?) = repo.saveCharacterPhoto(id, photo)

    fun updateCharacter(
        characterId: String,
        name: String? = null,
        color: Long? = null,
        shape: CharacterShape? = null,
        voice: VoiceTone? = null,
        bio: String? = null
    ) {
        mutate { s ->
            val previous = s.characterPreferences.orEmpty()[characterId] ?: CharacterPreference()
            val preference = previous.copy(
                name = name ?: previous.name, color = color ?: previous.color,
                shape = shape ?: previous.shape, voice = voice ?: previous.voice, bio = bio ?: previous.bio
            )
            s.copy(characters = s.characters.map { if (it.id == characterId) preference.applyTo(it) else it },
                characterPreferences = s.characterPreferences.orEmpty() + (characterId to preference))
        }
    }

    // -------------------------------------------------------------------- chats

    fun sendMessage(conversationId: String, text: String, card: MessageCard? = null,
        attachmentIds: List<String> = emptyList()): String? {
        val trimmed = text.trim()
        if (trimmed.isEmpty() && card == null) return null
        if (repo.state.value.conversation(conversationId) == null || conversationId in _roomChanges.value) return null
        val now = System.currentTimeMillis()
        val message = Message(
            id = id("msg"),
            conversationId = conversationId,
            sender = MessageSender.USER,
            text = trimmed,
            kind = when (card?.kind) {
                CardKind.TASK -> MessageKind.TASK
                CardKind.CALL -> MessageKind.CALL
                CardKind.FILE -> MessageKind.FILE
                CardKind.LINK -> MessageKind.LINK
                CardKind.SCHEDULE -> MessageKind.SCHEDULE
                CardKind.IMAGE -> MessageKind.IMAGE
                else -> MessageKind.TEXT
            },
            createdAt = now,
            card = card,
            attachmentIds = attachmentIds,
            status = MessageStatus.SENDING
        )
        mutate { s ->
            s.withConversation(conversationId) {
                it.copy(messages = it.messages + message, updatedAt = now, unread = 0, draft = "")
            }.copy(typingConversationId = null)
        }

        deliverRemoteMessage(message)
        return message.id
    }

    suspend fun serviceAccounts() = repo.serviceAccounts()
    suspend fun serviceConnectors() = repo.serviceConnectors()
    suspend fun openServiceLogin(id: String) = repo.openServiceLogin(id)
    suspend fun disconnectServiceAccount(id: String) = repo.disconnectServiceAccount(id)
    suspend fun registerServiceConnector(registration: com.dominion.mobile.data.remote.ConnectorRegistration,
        token: String) = repo.registerServiceConnector(registration, token)
    suspend fun setServiceConnectorEnabled(id: String, enabled: Boolean) = repo.setServiceConnectorEnabled(id, enabled)
    suspend fun openServiceWebsite(url: String) = repo.openServiceWebsite(url)

    suspend fun engineTools() = repo.engineTools()
    suspend fun roomExecution(roomId: String) = repo.roomExecution(roomId)
    fun roomSignals(roomId: String, sessionId: String) = repo.roomSignals(roomId, sessionId)
    fun roomLiveUpdates(reducer: com.dominion.mobile.data.remote.RoomLiveReducer) = repo.roomLiveUpdates(reducer)
    suspend fun decideApproval(roomId: String, approval: com.dominion.mobile.data.remote.EngineApproval,
        allow: Boolean) = repo.decideApproval(roomId, approval, allow)
    suspend fun cancelJob(roomId: String, jobId: String) = repo.cancelJob(roomId, jobId)

    suspend fun downloadAttachment(roomId: String, attachmentId: String) = repo.downloadAttachment(roomId, attachmentId)
    suspend fun messengerThreads()=repo.messengerThreads()
    suspend fun messengerMessages(thread:String,query:String?=null,before:String?=null)=repo.messengerMessages(thread,query,before)
    suspend fun messengerUpload(thread:String,name:String,mime:String,bytes:ByteArray)=repo.messengerUpload(thread,name,mime,bytes)
    suspend fun messengerDownload(thread:String,attachment:com.dominion.mobile.data.remote.MessengerAttachment)=
        repo.messengerDownload(thread,attachment)
    suspend fun messengerOperation(action:com.dominion.mobile.data.remote.MobileAction,target:String,
        payload:com.dominion.mobile.data.remote.MessengerPayload)=repo.messengerOperation(action,target,payload)
    suspend fun mobileCapabilities() = repo.mobileCapabilities()
    suspend fun mobilePeers() = repo.mobilePeers()
    suspend fun mobilePeerHistory(peer: String) = repo.mobilePeerHistory(peer)
    suspend fun mobileJournal() = repo.mobileJournal()
    suspend fun beginMobileOperation(action: com.dominion.mobile.data.remote.MobileAction, target: String,
        text: String? = null) = repo.beginMobileOperation(action, target, text)
    suspend fun refreshMobileOperation(nonce: String) = repo.refreshMobileOperation(nonce)
    suspend fun decideMobileOperation(nonce: String, digest: String, allow: Boolean) =
        repo.decideMobileOperation(nonce, digest, allow)

    suspend fun voiceStatus() = repo.voiceStatus()
    suspend fun transcribeVoice(wav: ByteArray): String {
        check(state.value.call == null)
        return repo.transcribeVoice(wav)
    }
    suspend fun synthesizeVoice(text: String): ByteArray {
        check(state.value.call == null)
        return repo.synthesizeVoice(text)
    }

    private fun verifyCallVoice(callId: String, roomId: String, session: String) {
        val call = state.value.call
        check(call?.sessionId == callId && call.conversationId == roomId &&
            call.phase in setOf(CallPhase.WAITING_PEER, CallPhase.PEER_PRESENT))
        check(state.value.conversation(roomId)?.sessionId == session)
    }

    suspend fun transcribeCallVoice(callId: String, roomId: String, session: String, wav: ByteArray): String {
        verifyCallVoice(callId, roomId, session)
        val result = repo.transcribeVoice(wav)
        verifyCallVoice(callId, roomId, session)
        return result
    }

    suspend fun synthesizeCallVoice(callId: String, roomId: String, session: String, text: String): ByteArray {
        verifyCallVoice(callId, roomId, session)
        val result = repo.synthesizeVoice(text)
        verifyCallVoice(callId, roomId, session)
        return result
    }

    suspend fun sendCallFrame(callId: String, roomId: String, expectedRoomSession: String,
        prompt: String, jpeg: ByteArray): String {
        fun verify() {
            val call = state.value.call
            check(call?.sessionId == callId && call.conversationId == roomId)
            check(state.value.conversation(roomId)?.sessionId == expectedRoomSession)
        }
        require(prompt.isNotBlank() && prompt.length <= 4000)
        verify()
        val file = repo.uploadAttachment(roomId, "call-frame-" + java.util.UUID.randomUUID() + ".jpg", "image/jpeg", jpeg)
        check(file.supportedForModel == "image" && file.roomId == roomId)
        verify()
        return requireNotNull(sendMessage(roomId, prompt,
            MessageCard(CardKind.IMAGE, file.name ?: "call-frame.jpg", "통화 프레임", "${file.size} bytes"),
            listOf(requireNotNull(file.id))))
    }

    suspend fun sendAttachment(roomId: String, text: String,
        name: String, mimeType: String, bytes: ByteArray) {
        val file = repo.uploadAttachment(roomId, name, mimeType, bytes)
        val card = MessageCard(
            kind = if (file.mimeType?.startsWith("image/") == true) CardKind.IMAGE else CardKind.FILE,
            title = file.name ?: name,
            subtitle = when (file.supportedForModel) {
                "image" -> "이미지"
                "text" -> "텍스트 문서"
                else -> "파일 · 모델 직접 읽기 미지원"
            },
            meta = "${file.size} bytes"
        )
        require(repo.state.value.conversation(roomId) != null)
        sendMessage(roomId, text, card, listOf(requireNotNull(file.id)))
    }

    fun retryMessage(conversationId: String, messageId: String) {
        val message = repo.state.value.conversation(conversationId)?.messages?.firstOrNull {
            it.id == messageId && it.sender == MessageSender.USER && it.status == MessageStatus.FAILED
        } ?: return
        mutate { s ->
            s.withConversation(conversationId) { conv ->
                conv.copy(messages = conv.messages.map {
                    if (it.id == messageId && it.status == MessageStatus.FAILED)
                        it.copy(status = MessageStatus.SENDING) else it
                })
            }
        }
        deliverRemoteMessage(message)
    }

    private fun deliverRemoteMessage(message: Message) {
        val conversationId = message.conversationId
        viewModelScope.launch {
            val acknowledgement = try {
                repo.deliverMessage(conversationId, message)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _notice.value = if(localExecution)"Google 계정 연결·모델 응답·무료 이용 한도를 확인하세요." else "본체 연결, 모델 및 도구 설정을 확인하세요."
                mutate { s ->
                    s.withConversation(conversationId) { conv ->
                        conv.copy(messages = conv.messages.map {
                            if (it.id == message.id && it.status == MessageStatus.SENDING)
                                it.copy(status = MessageStatus.FAILED) else it
                        })
                    }
                }
                return@launch
            }

            // A history refresh can win the race with POST acknowledgement.
            // Merge the durable job ID into the already reconciled row as well.
            if (state.value.conversation(conversationId)?.messages?.any {
                it.id == message.id || it.clientMessageId == message.id || it.id == acknowledgement.id
            } == true) applyServerMessages(conversationId, listOf(acknowledgement))
            // The room execution controller tracks actual server job state and
            // refreshes history. Never infer this job is complete from another
            // assistant message, and never impose the old one-minute cutoff.
            refreshConversation(conversationId)
        }
    }

    private fun applyServerMessages(conversationId: String, server: List<Message>) {
        mutate { s ->
            s.withConversation(conversationId) { conv ->
                val merged = MessageSync.reconcile(conv.messages, server)
                if (merged == conv.messages) conv
                else conv.copy(messages = merged, updatedAt = System.currentTimeMillis())
            }
        }
    }

    /** Refresh when entering a room, including messages received while the app was closed. */
    private val historyLocks = mutableMapOf<String, kotlinx.coroutines.sync.Mutex>()

    fun refreshConversation(conversationId: String) {
        viewModelScope.launch { refreshConversationNow(conversationId) }
    }

    suspend fun refreshConversationNow(conversationId: String): Boolean {
        val lock = historyLocks.getOrPut(conversationId) { kotlinx.coroutines.sync.Mutex() }
        if (!lock.tryLock()) return false
        try {
            repo.refreshRoomHistory(conversationId)
            return true
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { return false }
        finally { lock.unlock() }
    }

    suspend fun readArchiveAttachment(item: com.dominion.mobile.data.model.ArchiveItem) = repo.readArchiveAttachment(item)
    suspend fun pinArchiveAttachment(item: com.dominion.mobile.data.model.ArchiveItem) = repo.pinArchiveAttachment(item)
    suspend fun unpinArchiveAttachment(item: com.dominion.mobile.data.model.ArchiveItem) = repo.unpinArchiveAttachment(item)
    suspend fun offlineArchiveIds() = repo.offlineArchiveIds()

    fun archiveMessage(message: Message) {
        val items = com.dominion.mobile.data.ArchiveIndex.fromMessage(message)
        if (items.isEmpty()) { _notice.value = "보관할 원본 파일 또는 웹 링크가 없습니다."; return }
        mutate { current -> current.copy(archive = (items + current.archive).distinctBy { it.id }) }
        _notice.value = "보관함에 " + items.size + "개 항목을 추가했습니다."
    }

    fun addCardToConversation(conversationId: String, card: MessageCard, intro: String = "") {
        sendMessage(conversationId, intro, card)
    }

    fun addExploreToChat(conversationId: String, title: String, steps: List<String>, prompt: String) {
        val card = MessageCard(
            kind = CardKind.TASK,
            title = title,
            subtitle = prompt,
            meta = "탐색에서 추가",
            steps = steps.map { CardStep(it, false) }
        )
        if (prompt.isNotBlank()) sendMessage(conversationId, prompt, card)
    }

    suspend fun saveMiniAppState(roomId: String, session: String, messageId: String,
        documentKey: String, values: Map<String, String>) =
        repo.saveMiniAppState(roomId, session, messageId, documentKey, values)

    fun setDraft(conversationId: String, draft: String) = mutate { s ->
        s.withConversation(conversationId) { it.copy(draft = draft) }
    }

    fun markConversationRead(conversationId: String) = mutate { s ->
        s.withConversation(conversationId) { it.copy(unread = 0) }
    }

    fun setPinned(conversationId: String, pinned: Boolean) = mutate { s ->
        s.withConversation(conversationId) { it.copy(pinned = pinned) }
    }

    fun setMuted(conversationId: String, muted: Boolean) = mutate { s ->
        s.withConversation(conversationId) { it.copy(muted = muted) }
    }

    fun renameConversation(conversationId: String, title: String) {
        viewModelScope.launch {
            try { repo.renameRoom(conversationId, title) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { _notice.value = "본체 대화방 이름 변경을 확인하지 못했습니다." }
        }
    }

    private val _roomChanges = MutableStateFlow<Set<String>>(emptySet())
    val roomChanges = _roomChanges.asStateFlow()

    private fun changeRoom(id: String, action: suspend () -> Unit, success: String) {
        if (id in _roomChanges.value) return
        _roomChanges.value = _roomChanges.value + id
        viewModelScope.launch {
            try {
                action()
                _notice.value = success
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                _notice.value = "본체 처리 결과를 확인하지 못했습니다. 미전송 메시지와 연결을 확인하세요."
                refreshConversation(id)
            } finally { _roomChanges.value = _roomChanges.value - id }
        }
    }

    fun clearConversation(conversationId: String) = changeRoom(conversationId,
        { repo.resetRoom(conversationId, false) }, "이전 기록을 본체에 보존하고 새 대화 세션을 열었습니다.")

    fun rewindConversation(conversationId: String) = changeRoom(conversationId,
        { repo.resetRoom(conversationId, true) }, "새 세션에 마지막 요청을 복원했습니다. 검토 후 다시 보내세요.")

    fun deleteConversation(conversationId: String) = changeRoom(conversationId,
        { repo.removeRoom(conversationId) }, "대화방을 목록에서 제거했습니다. 본체의 기존 기록은 보존됩니다.")

    fun restoreHiddenRooms() {
        mutate { it.copy(hiddenRooms = emptySet()) }
        retryServerConnection()
    }

    private val _notice = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = _notice
    fun clearNotice() { _notice.value = null }

    fun createExtraConversation(title: String, onCreated: (String) -> Unit) {
        val characterId = repo.state.value.myCharacter()?.id
        if (characterId == null) {
            _notice.value = "본체를 연결하고 에이전트를 먼저 확인하세요."
            return
        }
        viewModelScope.launch {
            try { onCreated(repo.createConversation(title, characterId)) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { _notice.value = "본체에서 대화방을 생성하지 못했습니다." }
        }
    }

    fun openConversationFor(characterId: String, onCreated: (String) -> Unit) {
        val character = repo.state.value.character(characterId) ?: return
        repo.state.value.conversationFor(characterId)?.let { onCreated(it.id); return }
        viewModelScope.launch {
            try { onCreated(repo.createConversation(character.name, characterId)) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { _notice.value = "본체에서 대화방을 생성하지 못했습니다." }
        }
    }

    fun directConversationFor(characterId: String): String? {
        val existing = repo.state.value.conversationFor(characterId)
        if (existing == null) _notice.value = "새 대화방에서 본체 에이전트를 연결하세요."
        return existing?.id
    }

    // -------------------------------------------------------------------- calls

    fun startCall(conversationId: String, video: Boolean, realtimeAgent: Boolean = false): Boolean {
        val snapshot = repo.state.value
        val conversation = snapshot.conversation(conversationId) ?: return false
        if (repo.connection.value != com.dominion.mobile.data.ConnectionState.CONNECTED ||
            snapshot.call != null || com.dominion.mobile.platform.audio.AssistantVoiceService.state.value.active) return false
        mutate { it.copy(call = CallSession(
            conversationId = conversationId,
            characterId = conversation.characterId,
            video = false, requestVideo = video, realtimeAgent = realtimeAgent
        )) }
        return true
    }

    private val _openCallRequest = MutableStateFlow(false)
    val openCallRequest = _openCallRequest.asStateFlow()

    fun requestOpenCall() { if (state.value.call != null) _openCallRequest.value = true }
    fun consumeOpenCall() { _openCallRequest.value = false }

    fun sendCallInstruction(sessionId: String, text: String): String? {
        val call = state.value.call ?: return null
        if (call.sessionId != sessionId || call.phase !in setOf(
            com.dominion.mobile.data.model.CallPhase.WAITING_PEER,
            com.dominion.mobile.data.model.CallPhase.PEER_PRESENT,
            com.dominion.mobile.data.model.CallPhase.RECONNECTING)) return null
        return sendMessage(call.conversationId, text.take(32000))
    }

    fun toggleCallMute() = mutate { s ->
        s.call?.takeIf { it.phase == com.dominion.mobile.data.model.CallPhase.WAITING_PEER ||
            it.phase == com.dominion.mobile.data.model.CallPhase.PEER_PRESENT }
            ?.let { s.copy(call = it.copy(muted = !it.muted)) } ?: s
    }

    fun failCall(sessionId: String, message: String) = mutate { s ->
        s.call?.takeIf { it.sessionId == sessionId }?.let {
            s.copy(call = it.copy(phase = com.dominion.mobile.data.model.CallPhase.FAILED, error = message))
        } ?: s
    }

    fun endCall() = mutate { com.dominion.mobile.data.CallHistory.finish(it) }

    fun minimizeCall(minimized: Boolean) = mutate { s ->
        s.call?.let { s.copy(call = it.copy(minimized = minimized)) } ?: s
    }

    // ---------------------------------------------------------------- tasks etc.

    fun addTask(title: String, characterId: String, due: String, steps: List<String>) {
        if (title.isBlank()) return
        val task = TaskItem(
            id = id("task"),
            title = title.trim(),
            characterId = characterId,
            steps = steps.map { CardStep(it, false) },
            due = due
        )
        mutate { it.copy(tasks = listOf(task) + it.tasks) }
    }

    private val _taskBusy = MutableStateFlow<Set<String>>(emptySet())
    val taskBusy = _taskBusy.asStateFlow()

    suspend fun loadEngineSkills() = repo.engineSkills()
    suspend fun inspectEngineSkill(id: String) = repo.inspectEngineSkill(id)
    suspend fun changeEngineSkill(review: com.dominion.mobile.data.remote.SkillReview, enabled: Boolean) =
        repo.changeEngineSkill(review, enabled)

    fun createServiceTask(title: String, characterId: String, details: String, steps: List<String>,
        onAccepted: (String) -> Unit) {
        if (title.isBlank() || details.isBlank() || state.value.character(characterId) == null) {
            _notice.value = "본체 에이전트와 처리할 업무를 지정하세요."
            return
        }
        val task = TaskItem(id("task"), title.trim(), characterId,
            steps = steps.map { CardStep(it, false) }, requestDetails = details)
        mutate { it.copy(tasks = listOf(task) + it.tasks) }
        dispatchTask(task.id, onAccepted)
    }

    fun dispatchTask(taskId: String, onAccepted: (String) -> Unit = {}) {
        if (taskId in _taskBusy.value) return
        _taskBusy.value = _taskBusy.value + taskId
        viewModelScope.launch {
            try {
                repo.submitTask(taskId)
                _notice.value = "AI 작업 접수와 상태를 확인했습니다."
                state.value.tasks.firstOrNull { it.id == taskId }?.engineRoomId?.let(onAccepted)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { _notice.value = "접수 결과를 확인하지 못했습니다. 같은 할 일에서 다시 확인하세요." }
            finally { _taskBusy.value = _taskBusy.value - taskId }
        }
    }

    suspend fun cancelCloudTask(id:String) {
        val task=state.value.tasks.single{it.id==id}
        val job=requireNotNull(task.engineJobId)
        require(job.startsWith("cloud:"))
        repo.cancelJob(requireNotNull(task.engineRoomId),job)
    }

    suspend fun taskReceipt(id: String) = repo.taskReceipt(id)
    suspend fun taskEvidence(id: String, replyId: String) = repo.taskEvidence(id, replyId)

    suspend fun refreshDispatchedTasks() {
        state.value.tasks.filter { it.engineJobId != null }.forEach { task ->
            try { repo.refreshTask(task.id) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { /* Preserve the last confirmed receipt. */ }
        }
    }

    fun toggleTaskStep(taskId: String, index: Int) = mutate { s ->
        s.copy(tasks = s.tasks.map { task ->
            if (task.id != taskId) task else {
                val steps = task.steps.mapIndexed { i, step -> if (i == index) step.copy(done = !step.done) else step }
                task.copy(steps = steps, done = steps.isNotEmpty() && steps.all { it.done })
            }
        })
    }

    fun toggleTaskDone(taskId: String) = mutate { s ->
        s.copy(tasks = s.tasks.map { task ->
            if (task.id == taskId) task.copy(
                done = !task.done,
                steps = task.steps.map { it.copy(done = !task.done) }
            ) else task
        })
    }

    fun deleteTask(taskId: String) {
        val task=state.value.tasks.firstOrNull{it.id==taskId}?:return
        if(taskId in _taskBusy.value || task.engineStatus=="submitting" ||
            task.engineJobId?.startsWith("cloud:")==true&&task.engineStatus in setOf("queued","running")){
            _notice.value="실행 중인 작업은 먼저 상태를 확인하고 중단해 주세요."
            return
        }
        mutate{it.copy(tasks=it.tasks.filterNot{row->row.id==taskId})}
    }

    fun saveSchedule(existingId: String?, title: String, whenText: String, characterId: String,
        repeat: Repeat, scheduledAt: Long, zoneId: String, workRequest: String? = null,
        onFailure: () -> Unit = {}, watchCondition: String? = null, onSaved: () -> Unit = {}) {
        if (title.isBlank() || scheduledAt <= System.currentTimeMillis()) {
            _notice.value = "제목과 미래 날짜·시간을 지정하세요."
            onFailure()
            return
        }
        viewModelScope.launch {
            try {
                val previous = state.value.schedules.firstOrNull { it.id == existingId }
                require(previous?.pendingDispatchAt == null) { "Resolve pending work first" }
                if (!workRequest.isNullOrBlank()) require(state.value.character(characterId) != null && workRequest.length <= 3000)
                require(watchCondition.isNullOrBlank() || (!workRequest.isNullOrBlank() && watchCondition.length <= 600))
                val item = ScheduleItem(previous?.id ?: id("sch"), title.trim(), characterId, whenText,
                    repeat, enabled = true, createdAt = previous?.createdAt ?: System.currentTimeMillis(),
                    scheduledAt = scheduledAt, timeZone = zoneId, lastNotifiedAt = null,
                    workRequest = workRequest?.trim()?.takeIf { it.isNotBlank() },
                    watchCondition = watchCondition?.trim()?.takeIf { it.isNotBlank() })
                repo.changeSchedules { listOf(item) + it.filterNot { old -> old.id == item.id } }
                onSaved()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { _notice.value = "일정 예약을 저장하지 못했습니다."; onFailure() }
        }
    }

    fun toggleSchedule(id: String) {
        viewModelScope.launch {
            try {
                val item = state.value.schedules.firstOrNull { it.id == id } ?: return@launch
                if (!item.enabled && item.scheduledAt == null) {
                    _notice.value = "날짜와 시간을 먼저 지정하세요."
                    return@launch
                }
                if (!item.enabled && item.repeat == Repeat.NONE &&
                    (item.scheduledAt ?: 0) <= System.currentTimeMillis()) {
                    _notice.value = "지난 일회성 일정은 날짜와 시간을 다시 지정하세요."
                    return@launch
                }
                repo.changeSchedules { rows -> rows.map { if (it.id == id) it.copy(enabled = !it.enabled) else it } }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { _notice.value = "알림 설정을 저장하지 못했습니다." }
        }
    }

    fun deleteSchedule(id: String) {
        viewModelScope.launch {
            try { repo.changeSchedules { rows -> rows.filterNot { it.id == id } } }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { _notice.value = "일정을 삭제하지 못했습니다." }
        }
    }

    private val _memoryStatus = MutableStateFlow<String?>(null)
    val memoryStatus = _memoryStatus.asStateFlow()

    fun refreshMemories() {
        viewModelScope.launch {
            try { repo.refreshMemories(); _memoryStatus.value = null }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { _memoryStatus.value = "본체 기억 조회 실패 · 기기에 저장된 기록을 표시합니다." }
        }
    }

    fun addMemory(characterId: String, text: String, source: String = "대화에서 저장") {
        if (text.isBlank()) return
        val memory = MemoryItem(id = id("mem"), characterId = characterId, text = text.trim(),
            source = source, serverBacked = false)
        mutate { it.copy(memories = listOf(memory) + it.memories) }
        syncMemory(memory.id)
    }

    fun syncMemory(id: String) {
        viewModelScope.launch {
            try {
                repo.syncMemory(id)
                _notice.value = if(localExecution)"기기 기억 저장 완료" else "본체 기억 저장 및 조회 확인 완료"
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { _notice.value = "기기에 기억을 보관했습니다. 기억 메뉴에서 본체 동기화를 다시 시도하세요." }
        }
    }

    fun deleteMemory(id: String) {
        viewModelScope.launch {
            try { repo.deleteMemory(id) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { _notice.value = "기억 삭제를 확인하지 못했습니다. 기록을 유지합니다." }
        }
    }

    // ------------------------------------------------------------------ friends

    fun acceptFriendRequest(requestId: String) = mutate { s ->
        val request = s.friendRequests.firstOrNull { it.id == requestId } ?: return@mutate s
        s.copy(
            friendRequests = s.friendRequests.filterNot { it.id == requestId },
            friends = s.friends + Friend(
                id = request.id,
                name = request.name,
                handle = request.handle,
                color = request.color,
                relation = "새 친구"
            ),
            activity = listOf(
                ActivityItem(
                    id = id("act"),
                    type = ActivityType.FRIEND,
                    title = "${request.name}님과 친구가 됐어요",
                    body = "${request.handle} · 이제 도미니온을 나눌 수 있어요.",
                    createdAt = System.currentTimeMillis()
                )
            ) + s.activity
        )
    }

    fun declineFriendRequest(requestId: String) = mutate { s ->
        s.copy(friendRequests = s.friendRequests.filterNot { it.id == requestId })
    }

    fun saveLocalContact(card: com.dominion.mobile.data.LocalContactCard): Boolean {
        val verified = runCatching { card.validated() }.getOrElse { return false }
        val identifier = verified.phone ?: verified.email ?: return false
        mutate { current ->
            val existing = current.friends.firstOrNull { it.localContact &&
                ((verified.phone != null && it.phone == verified.phone) ||
                 (verified.email != null && it.email.equals(verified.email, true))) }
            val saved = Friend(existing?.id ?: id("fr"), verified.name, identifier,
                existing?.color ?: 0xFF79C7A5, relation = "기기 연락처",
                favorite = existing?.favorite ?: false,
                phone = verified.phone ?: existing?.phone, email = verified.email ?: existing?.email, localContact = true)
            current.copy(friends = listOf(saved) + current.friends.filterNot { it.id == saved.id })
        }
        return true
    }

    fun addFriendByHandle(handle: String) {
        val cleaned = handle.trim().removePrefix("@")
        if (cleaned.isBlank()) return
        mutate { s ->
            if (s.friends.any { it.handle.removePrefix("@") == cleaned }) return@mutate s
            s.copy(
                friends = s.friends + Friend(
                    id = id("fr"),
                    name = cleaned,
                    handle = "@$cleaned",
                    color = 0xFF79C7A5,
                    relation = "미확인 아이디 · 기기 메모",
                    localContact = true
                )
            )
        }
    }

    fun toggleFavoriteFriend(friendId: String) = mutate { s ->
        s.copy(friends = s.friends.map { if (it.id == friendId) it.copy(favorite = !it.favorite) else it })
    }

    fun removeFriend(friendId: String) = mutate { s ->
        s.copy(friends = s.friends.filterNot { it.id == friendId })
    }

    fun blockUser(id: String) = mutate { s ->
        s.copy(settings = s.settings.copy(blockedUserIds = (s.settings.blockedUserIds + id).distinct()))
    }

    fun unblockUser(id: String) = mutate { s ->
        s.copy(settings = s.settings.copy(blockedUserIds = s.settings.blockedUserIds - id))
    }

    // -------------------------------------------------------------- connections

    fun toggleConnection(connectionId: String) = mutate { s ->
        val target = s.connections.firstOrNull { it.id == connectionId } ?: return@mutate s
        val nowConnected = !target.connected
        s.copy(
            connections = s.connections.map {
                if (it.id == connectionId) it.copy(
                    connected = nowConnected,
                    lastUsed = if (nowConnected) "방금" else it.lastUsed
                ) else it
            },
            activity = if (nowConnected && !target.builtIn) listOf(
                ActivityItem(
                    id = id("act"),
                    type = ActivityType.SYSTEM,
                    title = "${target.name} 연결됨",
                    body = "도미니온이 이 도구를 사용할 수 있어요.",
                    createdAt = System.currentTimeMillis()
                )
            ) + s.activity else s.activity
        )
    }

    fun saveConnectionScopes(connectionId: String, scopes: List<String>) = mutate { s ->
        s.copy(connections = s.connections.map {
            if (it.id == connectionId) it.copy(scopes = scopes) else it
        })
    }

    // -------------------------------------------------------------------- vault

    fun unlockVault() = mutate { it.copy(vaultUnlocked = true) }
    fun lockVault() = mutate { it.copy(vaultUnlocked = false) }

    fun addVaultItem(type: VaultType, title: String, username: String, secret: String, note: String) {
        if (title.isBlank()) return
        mutate {
            it.copy(
                vault = listOf(
                    VaultItem(
                        id = id("vault"),
                        type = type,
                        title = title.trim(),
                        username = username.trim(),
                        secret = secret.trim(),
                        note = note.trim()
                    )
                ) + it.vault
            )
        }
    }

    fun deleteVaultItem(id: String) = mutate { s ->
        s.copy(vault = s.vault.filterNot { it.id == id })
    }

    // ------------------------------------------------------------------ credits

    // The Cloud external-app grant cannot charge or mint entitlements.
    // Retain legacy entry points without mutating real balances or issuing receipts.
    fun purchase(product: StoreProduct) {
        _notice.value = product.title + " · Cloud 외부 앱 결제 권한이 없습니다. PC에서 결제를 관리하세요."
    }

    fun claimDailyJelly(): Boolean {
        _notice.value = "Cloud 무료 요청 잔여량은 계정 화면에서 실제 조회합니다."
        return false
    }

    // Legacy ticket action now invokes the native session restart, not a fabricated purchase.
    fun useResetTicket(conversationId: String): Boolean {
        if (state.value.conversation(conversationId) == null) return false
        rewindConversation(conversationId)
        return true
    }

    // ----------------------------------------------------------------- settings

    fun setTheme(theme: ThemeMode) = mutate { it.copy(settings = it.settings.copy(theme = theme)) }
    fun setLanguage(lang: Lang) = mutate { it.copy(settings = it.settings.copy(lang = lang)) }
    fun setNotifications(on: Boolean) = mutate { it.copy(settings = it.settings.copy(notifications = on)) }
    fun setMessagePreview(on: Boolean) = mutate { it.copy(settings = it.settings.copy(messagePreview = on)) }
    fun setRoomSounds(on: Boolean) = mutate { it.copy(settings = it.settings.copy(roomSounds = on)) }
    fun setAppLock(on: Boolean) = mutate { it.copy(settings = it.settings.copy(appLock = on)) }
    fun setHideContent(on: Boolean) = mutate { it.copy(settings = it.settings.copy(hideContent = on)) }
    fun setReduceMotion(on: Boolean) = mutate { it.copy(settings = it.settings.copy(reduceMotion = on)) }
    fun dismissGuide() = mutate { it.copy(settings = it.settings.copy(guideDismissed = true)) }

    fun moveTab(tab: TabId, up: Boolean) = mutate { s ->
        val list = s.settings.tabOrder.toMutableList()
        val index = list.indexOf(tab)
        if (index < 0) return@mutate s
        val target = if (up) index - 1 else index + 1
        if (target !in list.indices) return@mutate s
        val value = list.removeAt(index)
        list.add(target, value)
        s.copy(settings = s.settings.copy(tabOrder = list))
    }

    fun resetTabOrder() = mutate {
        it.copy(settings = it.settings.copy(tabOrder = SettingsState().tabOrder))
    }

    // ---------------------------------------------------------------- activity

    fun markActivityRead(id: String) = mutate { s ->
        s.copy(activity = s.activity.map { if (it.id == id) it.copy(read = true) else it })
    }

    fun markAllActivityRead() = mutate { s ->
        s.copy(activity = s.activity.map { it.copy(read = true) })
    }

    // ------------------------------------------------------------------ sharing

    private val _sharedFiles = MutableStateFlow<List<com.dominion.mobile.platform.attachments.LocalAttachment>>(emptyList())
    val sharedFiles = _sharedFiles.asStateFlow()
    private val _sharedCaption = MutableStateFlow<String?>(null)
    val sharedCaption = _sharedCaption.asStateFlow()
    private val _shareBusy = MutableStateFlow(false)
    val shareBusy = _shareBusy.asStateFlow()
    private val _shareArrival = MutableStateFlow(0L)
    val shareArrival = _shareArrival.asStateFlow()

    fun sharedImportFailed() {
        _notice.value = "공유 파일을 읽지 못했습니다. 파일 5개 이하, 개별 8MiB 이하로 다시 공유하세요."
    }

    fun importSharedFiles(context: android.content.Context,
        incoming: com.dominion.mobile.platform.attachments.IncomingShare) {
        if (_shareBusy.value || _sharedFiles.value.isNotEmpty() || state.value.sharedText != null) {
            _notice.value = "이미 대기 중인 공유 항목이 있습니다. 먼저 보내거나 닫아주세요."
            return
        }
        _shareBusy.value = true
        viewModelScope.launch {
            try {
                val files = incoming.uris.map { uri ->
                    require(uri.authority != context.packageName + ".archive")
                    require(context.checkUriPermission(uri, android.os.Process.myPid(), android.os.Process.myUid(),
                        android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) == android.content.pm.PackageManager.PERMISSION_GRANTED)
                    com.dominion.mobile.platform.attachments.LocalAttachment.fromUri(context, uri)
                }
                _sharedFiles.value = files
                _sharedCaption.value = incoming.text
                _shareArrival.value++
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { sharedImportFailed() }
            finally { _shareBusy.value = false }
        }
    }

    fun receiveSharedText(text: String?) {
        if (text.isNullOrBlank()) return
        if (_shareBusy.value || _sharedFiles.value.isNotEmpty() || state.value.sharedText != null) {
            _notice.value = "기존 공유 항목을 먼저 처리하세요."
            return
        }
        mutate { it.copy(sharedText = text.take(32000)) }
        _shareArrival.value++
    }

    fun clearSharedText() {
        if (_shareBusy.value) return
        _sharedFiles.value = emptyList()
        _sharedCaption.value = null
        mutate { it.copy(sharedText = null) }
    }

    fun shareToConversation(conversationId: String, onQueued: () -> Unit = {}) {
        if (_shareBusy.value || conversationId in _roomChanges.value ||
            state.value.conversation(conversationId) == null) return
        val files = _sharedFiles.value
        val text = if (files.isNotEmpty()) _sharedCaption.value.orEmpty() else state.value.sharedText.orEmpty()
        if (files.isEmpty() && text.isBlank()) return
        _shareBusy.value = true
        viewModelScope.launch {
            try {
                val attachments = files.map { file ->
                    repo.uploadAttachment(conversationId, file.name, file.mimeType, file.bytes)
                }
                check(state.value.conversation(conversationId) != null && conversationId !in _roomChanges.value)
                val card = attachments.firstOrNull()?.let { file ->
                    MessageCard(kind = if (file.mimeType?.startsWith("image/") == true) CardKind.IMAGE else CardKind.FILE,
                        title = file.name ?: "attachment", subtitle = "첨부 " + attachments.size + "개",
                        meta = file.size.toString() + " bytes")
                }
                sendMessage(conversationId, text, card, attachments.map { requireNotNull(it.id) })
                _sharedFiles.value = emptyList()
                _sharedCaption.value = null
                mutate { it.copy(sharedText = null) }
                onQueued()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { _notice.value = "공유 파일 전송 준비에 실패했습니다. 원본 공유 항목을 유지합니다." }
            finally { _shareBusy.value = false }
        }
    }

    class Factory(private val repo: DominionRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = AppViewModel(repo) as T
    }
}

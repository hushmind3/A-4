package com.dominion.mobile.data

import com.dominion.mobile.data.model.AppState
import com.dominion.mobile.data.model.Message
import kotlinx.coroutines.flow.StateFlow

/** Repository contract shared by the native UI and execution server. */
enum class ConnectionState { CONNECTING, CONNECTED, OFFLINE, AUTH_REQUIRED, UNAVAILABLE }

interface DominionRepository {
    val state: StateFlow<AppState>
    val connection: StateFlow<ConnectionState>
    val localExecution:Boolean get()=false

    /** Acknowledge one outbound message; only the remote implementation performs HTTP. */
    suspend fun deliverMessage(conversationId: String, message: Message): Message = error("Execution server not connected")
    suspend fun submitAssistantVoice(roomId:String,session:String,nonce:String,text:String):Message =
        error("Execution server not connected")
    /** Approved semantic controls on the handset. This path never requires a PC or screenshot. */
    suspend fun replanPhoneControls(context:com.dominion.mobile.data.PhoneReplanContext,
        controls:List<String>,results:List<String> = emptyList(),feedback:String = ""):com.dominion.mobile.data.PhoneNavigationPlan? =
        error("Phone controls replanning unavailable")
    suspend fun replanPhoneNavigation(context:com.dominion.mobile.data.PhoneReplanContext,
        digest:String,png:ByteArray,width:Int,height:Int):com.dominion.mobile.data.PhoneNavigationPlan? =
        error("Execution server not connected")
    suspend fun reserveNotificationReply(roomId: String, session: String, nonce: String, text: String): Boolean =
        error("Execution server not connected")
    suspend fun dispatchNotificationReply(roomId: String, session: String, nonce: String): Boolean =
        error("Execution server not connected")

    /** Fetch messages after a successful send; null means not supported. */
    suspend fun loadMessages(conversationId: String): List<Message>? = null

    /** Wait for an asynchronous server reply, without sending the message again. */
    suspend fun pollForReply(conversationId: String, knownIds: Set<String>): List<Message> =
        emptyList()

    suspend fun messengerThreads(): com.dominion.mobile.data.remote.MessengerDirectory = error("Messenger unavailable")
    suspend fun messengerMessages(thread:String,query:String?=null,before:String?=null):
        com.dominion.mobile.data.remote.MessengerPage = error("Messenger unavailable")
    suspend fun messengerUpload(thread:String,name:String,mime:String,bytes:ByteArray):
        com.dominion.mobile.data.remote.MessengerAttachment = error("Messenger unavailable")
    suspend fun messengerDownload(thread:String,attachment:com.dominion.mobile.data.remote.MessengerAttachment):
        ByteArray = error("Messenger unavailable")
    suspend fun messengerOperation(action:com.dominion.mobile.data.remote.MobileAction,target:String,
        payload:com.dominion.mobile.data.remote.MessengerPayload):
        com.dominion.mobile.data.remote.MobileJournalEntry = error("Messenger unavailable")

    suspend fun mobileCapabilities(): com.dominion.mobile.data.remote.MobileCapabilities = error("Mobile extension unavailable")
    suspend fun mobilePeers(): List<com.dominion.mobile.data.remote.MobilePeer> = error("Mobile extension unavailable")
    suspend fun mobilePeerHistory(peer: String): List<com.dominion.mobile.data.remote.MobilePeerMessage> = error("Mobile extension unavailable")
    suspend fun mobileJournal(): List<com.dominion.mobile.data.remote.MobileJournalEntry> = emptyList()
    suspend fun beginMobileOperation(action: com.dominion.mobile.data.remote.MobileAction, target: String,
        text: String? = null): com.dominion.mobile.data.remote.MobileJournalEntry = error("Mobile extension unavailable")
    suspend fun refreshMobileOperation(nonce: String): com.dominion.mobile.data.remote.MobileJournalEntry = error("Mobile extension unavailable")
    suspend fun decideMobileOperation(nonce: String, digest: String, allow: Boolean):
        com.dominion.mobile.data.remote.MobileJournalEntry = error("Mobile extension unavailable")

    suspend fun synthesizeAlignedVoice(text: String): com.dominion.mobile.data.remote.AlignedSpeech? = null
    suspend fun voiceStatus(): com.dominion.mobile.data.remote.EngineVoiceStatus = error("Engine not connected")
    suspend fun transcribeVoice(wav: ByteArray): String = error("Engine not connected")
    suspend fun synthesizeVoice(text: String): ByteArray = error("Engine not connected")

    suspend fun requestRealtimeCall(conversationId: String, callId: String, video: Boolean):
        com.dominion.mobile.data.remote.RealtimeCallGrant = error("Realtime agent unavailable")

    suspend fun requestVoiceCall(conversationId: String, video: Boolean = false): com.dominion.mobile.data.remote.CallTokenResponse =
        error("Voice calls require a connected server")

    suspend fun searchMessages(query: String): List<Message> = state.value.conversations
        .flatMap { it.messages }.filter { query.isNotBlank() && it.text.contains(query, ignoreCase = true) }
        .sortedByDescending { it.createdAt }.take(30)

    suspend fun createConversation(title: String, characterId: String): String = error("Execution server not connected")

    fun roomLiveUpdates(reducer: com.dominion.mobile.data.remote.RoomLiveReducer):
        kotlinx.coroutines.flow.Flow<com.dominion.mobile.data.remote.RoomLiveUpdate> = kotlinx.coroutines.flow.emptyFlow()
    fun roomSignals(roomId: String, sessionId: String): kotlinx.coroutines.flow.Flow<Unit> =
        kotlinx.coroutines.flow.emptyFlow()
    suspend fun engineTools(): List<com.dominion.mobile.data.remote.EngineTool> = error("Engine not connected")
    suspend fun roomExecution(roomId: String): com.dominion.mobile.data.remote.RoomExecution = error("Engine not connected")
    suspend fun decideApproval(roomId: String, approval: com.dominion.mobile.data.remote.EngineApproval,
        allow: Boolean): com.dominion.mobile.data.remote.ApprovalResult = error("Engine not connected")
    suspend fun cancelJob(roomId: String, jobId: String): com.dominion.mobile.data.remote.EngineJob = error("Engine not connected")
    suspend fun uploadAttachment(roomId: String, name: String, mimeType: String, bytes: ByteArray):
        com.dominion.mobile.data.remote.EngineAttachment = error("Engine not connected")

    suspend fun downloadAttachment(roomId: String, id: String): ByteArray = error("Engine not connected")
    suspend fun readArchiveAttachment(item: com.dominion.mobile.data.model.ArchiveItem): ByteArray =
        downloadAttachment(requireNotNull(item.conversationId), requireNotNull(item.attachmentId))
    suspend fun pinArchiveAttachment(item: com.dominion.mobile.data.model.ArchiveItem): Unit = error("Archive storage unavailable")
    suspend fun unpinArchiveAttachment(item: com.dominion.mobile.data.model.ArchiveItem): Unit = error("Archive storage unavailable")
    suspend fun offlineArchiveIds(): Set<String> = emptySet()

    suspend fun serviceAccounts(): List<com.dominion.mobile.data.remote.ServiceAccount> = error("본체 연결 필요")
    suspend fun serviceConnectors(): List<com.dominion.mobile.data.remote.ServiceConnector> = error("본체 연결 필요")
    suspend fun openServiceLogin(id: String): Unit = error("본체 연결 필요")
    suspend fun disconnectServiceAccount(id: String): Unit = error("본체 연결 필요")
    suspend fun registerServiceConnector(registration: com.dominion.mobile.data.remote.ConnectorRegistration,
        token: String): com.dominion.mobile.data.remote.ServiceConnector = error("본체 연결 필요")
    suspend fun setServiceConnectorEnabled(id: String, enabled: Boolean): Unit = error("본체 연결 필요")
    suspend fun openServiceWebsite(url: String): Unit = error("본체 연결 필요")

    /** True only when the name has been persisted and read back from the engine. */
    suspend fun saveOwnerProfile(name: String, birthday: String): Boolean {
        error("Profile storage unavailable")
    }

    suspend fun refreshMemories(): Unit = error("Engine not connected")
    suspend fun syncMemory(id: String): Unit = error("Engine not connected")
    suspend fun deleteMemory(id: String): Unit = error("Memory storage unavailable")
    suspend fun renameRoom(id: String, title: String): Unit = error("Engine not connected")
    suspend fun refreshRoomHistory(id: String): Unit = error("Engine not connected")
    suspend fun resetRoom(id: String, restoreLastRequest: Boolean): Unit = error("Engine not connected")
    suspend fun removeRoom(id: String): Unit = error("Engine not connected")

    suspend fun engineSkills(): List<com.dominion.mobile.data.remote.EngineSkillState> = error("Engine not connected")
    suspend fun inspectEngineSkill(id: String): com.dominion.mobile.data.remote.SkillReview = error("Engine not connected")
    suspend fun changeEngineSkill(review: com.dominion.mobile.data.remote.SkillReview, enabled: Boolean): Unit = error("Engine not connected")
    suspend fun createPhoneErrand(title:String,characterId:String,request:String,pkg:String,
        kakaoMessage:KakaoMessageRequest?=null,publicRead:Boolean=false,
        transaction:PhoneTransactionRequest?=null,privateRead:PhonePrivateRead?=null):String =
        error("Native phone errand unavailable")
    suspend fun recordPhoneErrand(context:PhoneReplanContext,status:String,detail:String):Boolean = false
    suspend fun recordMessageReply(pkg:String,title:String,body:String,at:Long):Boolean = false
    suspend fun confirmMessageReply(id:String,observedAt:Long):Unit = error("Reply review unavailable")
    suspend fun continueNegotiation(id:String,observedAt:Long,body:String):String = error("Negotiation unavailable")
    suspend fun resolveNegotiation(id:String,observedAt:Long,agreed:Boolean):Unit = error("Negotiation unavailable")
    suspend fun draftMessageReply(id:String,observedAt:Long,goal:String):String = error("Reply drafting unavailable")
    suspend fun suggestReplyAppointment(id:String,observedAt:Long):ReplyAppointment? = error("Appointment extraction unavailable")
    suspend fun saveReplyAppointment(id:String,observedAt:Long,draft:ReplyAppointment):Unit = error("Appointment storage unavailable")
    suspend fun planErrandWorkflow(goal:String):ErrandWorkflowDraft = error("Workflow planner unavailable")
    suspend fun acceptErrandWorkflow(draft:ErrandWorkflowDraft,finalTaskIds:List<String> = emptyList()):Unit = error("Workflow planner unavailable")
    suspend fun linkTaskWorkflow(ids:List<String>):Unit = error("Workflow unavailable")
    suspend fun advanceTaskWorkflows():Unit = Unit
    suspend fun submitTask(id: String): Unit = error("Engine not connected")
    suspend fun dispatchScheduledTask(id: String, occurrence: Long): Unit = error("Engine not connected")
    suspend fun refreshTask(id: String): Unit = error("Engine not connected")
    /** Persist a verified server observation without submitting any work. */
    suspend fun recordObservedJob(roomId: String, jobId: String,
        job: com.dominion.mobile.data.remote.EngineJob): Unit = error("Engine not connected")
    suspend fun taskReceipt(id: String): com.dominion.mobile.data.remote.TaskReceipt = error("Engine not connected")
    suspend fun taskEvidence(id: String, replyId: String): com.dominion.mobile.data.remote.TaskEvidence = error("Engine not connected")

    suspend fun changeSchedules(transform: (List<com.dominion.mobile.data.model.ScheduleItem>) ->
        List<com.dominion.mobile.data.model.ScheduleItem>): Unit = error("Reminder storage unavailable")

    suspend fun saveMiniAppState(roomId: String, session: String, messageId: String,
        documentKey: String, values: Map<String, String>): Unit = error("Mini app storage unavailable")

    suspend fun saveCharacterPhoto(id: String, photo: String?): Unit = error("Photo storage unavailable")

    fun update(transform: (AppState) -> AppState)
    fun engineChangeSignals():kotlinx.coroutines.flow.Flow<com.dominion.mobile.data.remote.EngineChange> =
        kotlinx.coroutines.flow.emptyFlow()
    suspend fun synchronizeChange(change:com.dominion.mobile.data.remote.EngineChange):Unit = Unit
    suspend fun synchronizeEngine(): Boolean = false
    suspend fun refresh() = Unit
    fun reset()
}

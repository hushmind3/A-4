package com.dominion.mobile.data.remote

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

data class DataResponse<T>(val data: T? = null)

data class OwnerProfile(val id: String?, val name: String?, val avatar: String?)
data class OwnerProfileUpdate(val name: String, val avatar: String)
data class AuthResponse(val token: String = "", val account: AccountDto? = null)
data class SignInRequest(val provider_token: String = "", val invite_code: String? = null)
data class PhoneStartRequest(val phone: String = "", val region: String = "KR")
data class PhoneVerifyRequest(val phone: String = "", val code: String = "")
data class CallTokenResponse(
    val room: String = "",
    val token: String = "",
    val url: String = "",
    val expires_at: Long = 0
) {
    fun requireUsable(now: Long = System.currentTimeMillis()): CallTokenResponse {
        val endpoint = java.net.URI(url)
        require(endpoint.scheme == "wss" && !endpoint.host.isNullOrBlank() && endpoint.userInfo == null) {
            "Voice endpoint requires secure WebSocket (wss)"
        }
        require(room.isNotBlank() && token.isNotBlank() && expires_at > now) { "Invalid or expired call session" }
        return this
    }
}
data class NotificationDto(
    val id: String = "",
    val type: String = "",
    val title: String = "",
    val body: String = "",
    val created_at: Long = 0,
    val read: Boolean = false
)

/** Existing DOMINION mobile and native engine contracts. */
interface DominionApi {
    // Negotiated extension contract. Existing engines may respond 404; no fallback to demo data.
    @POST("v1/mobile/realtime/calls")
    suspend fun openRealtimeCall(@Body request: RealtimeCallRequest): RealtimeCallGrant
    @GET("v1/mobile/capabilities")
    suspend fun mobileCapabilities(): MobileCapabilities
    @GET("v1/mobile/messenger/threads")
    suspend fun messengerThreads(): MessengerDirectory
    @GET("v1/mobile/messenger/threads/{id}/messages")
    suspend fun messengerMessages(@Path("id")id:String,@Query("q")query:String?=null,
        @Query("before")before:String?=null): MessengerPage
    @POST("v1/mobile/messenger/threads/{id}/attachments")
    suspend fun messengerUpload(@Path("id")id:String,@Body body:MessengerUpload): MessengerAttachment
    @retrofit2.http.Streaming
    @GET("v1/mobile/messenger/threads/{id}/attachments/{attachment}")
    suspend fun messengerDownload(@Path("id")id:String,@Path("attachment")attachment:String):okhttp3.ResponseBody
    @GET("v1/mobile/peers")
    suspend fun mobilePeers(): MobileDirectory
    @GET("v1/mobile/peers/{id}/messages")
    suspend fun mobilePeerHistory(@Path("id") id: String): MobilePeerHistory
    @POST("v1/mobile/operations")
    suspend fun createMobileOperation(@Body intent: MobileIntent): MobileReceipt
    @GET("v1/mobile/operations/{nonce}")
    suspend fun mobileOperation(@Path("nonce") nonce: String): MobileReceipt
    @POST("v1/mobile/operations/{nonce}/decision")
    suspend fun decideMobileOperation(@Path("nonce") nonce: String,
        @Body decision: MobileReviewDecision): MobileReceipt


    @POST("v1/mobile/voice/aligned")
    suspend fun alignedVoice(@Body request: AlignedVoiceRequest): AlignedVoiceResponse

    @GET("api/voice/status")
    suspend fun voiceStatus(): EngineVoiceStatus
    @POST("api/voice/transcribe")
    suspend fun transcribe(@Body wav: okhttp3.RequestBody): VoiceTranscript
    @retrofit2.http.Streaming
    @POST("api/voice/speak")
    suspend fun synthesize(@Body body: Map<String, String>): okhttp3.ResponseBody

    @retrofit2.http.Streaming
    @retrofit2.http.Headers("Accept: text/event-stream")
    @GET("api/events")
    fun eventStream(@Query("after") after: Long): retrofit2.Call<okhttp3.ResponseBody>

    @GET("api/memories")
    suspend fun engineMemories(): List<EngineMemory>
    @POST("api/memories")
    suspend fun saveEngineMemory(@Body body: MemoryWrite): EngineMemory
    @retrofit2.http.DELETE("api/memories/{id}")
    suspend fun deleteEngineMemory(@Path("id") id: String): com.google.gson.JsonObject
    @POST("api/rooms/{id}")
    suspend fun renameEngineRoom(@Path("id") id: String, @Body body: RoomNameUpdate): EngineRoom

    @GET("api/settings/owner-profile")
    suspend fun ownerProfile(): OwnerProfile
    @POST("api/settings/owner-profile")
    suspend fun saveOwnerProfile(@Body body: OwnerProfileUpdate): OwnerProfile

    @GET("api/oauth/providers")
    suspend fun serviceAccounts(): List<ServiceAccount>
    @POST("api/oauth/{id}/open")
    suspend fun openServiceLogin(@Path("id") id: String): com.google.gson.JsonObject
    @retrofit2.http.DELETE("api/oauth/{id}")
    suspend fun disconnectServiceAccount(@Path("id") id: String): com.google.gson.JsonObject
    @GET("api/mcp")
    suspend fun serviceConnectors(): List<com.google.gson.JsonObject>
    @POST("api/mcp")
    suspend fun saveServiceConnector(@Body body: com.google.gson.JsonObject): ServiceConnector
    @POST("api/browser/action")
    suspend fun browserAction(@Body body: Map<String, String>): com.google.gson.JsonObject

    @GET("api/rooms/{id}/state")
    suspend fun roomExecution(@Path("id") id: String): RoomExecution
    @GET("api/approvals")
    suspend fun pendingApprovals(): List<EngineApproval>
    @POST("api/approvals")
    suspend fun decideApproval(@Body body: ApprovalDecision): ApprovalResult
    @GET("api/jobs/{id}")
    suspend fun engineJob(@Path("id") id: String): EngineJob
    @GET("api/sessions/{id}/messages")
    suspend fun sessionTranscript(@Path("id") id: String): List<NativeRoomMessage>
    @GET("api/executions/{id}")
    suspend fun nativeExecution(@Path("id") id: String): NativeExecutionIdentity
    @GET("api/executions/{id}/operations")
    suspend fun executionOperations(@Path("id") id: String): List<EngineOperation>
    @POST("api/jobs/{id}/cancel")
    suspend fun cancelEngineJob(@Path("id") id: String): EngineJob
    @retrofit2.http.Streaming
    @GET("api/attachments/{id}/content")
    suspend fun attachmentContent(@Path("id") id: String): okhttp3.ResponseBody
    @POST("api/attachments")
    suspend fun uploadAttachment(@Body body: AttachmentUpload): EngineAttachment
    @GET("api/rooms")
    suspend fun engineRooms(): List<EngineRoom>
    @POST("api/rooms")
    suspend fun createRoom(@Body body: RoomCreate): EngineRoom
    @GET("api/rooms/{id}")
    suspend fun engineRoom(@Path("id") id: String): EngineRoom
    @POST("api/rooms/{id}")
    suspend fun updateRoom(@Path("id") id: String, @Body body: RoomConfigUpdate): EngineRoom
    @POST("api/rooms/{id}/reset")
    suspend fun resetEngineRoom(@Path("id") id: String): RoomResetReceipt
    @retrofit2.http.DELETE("api/rooms/{id}")
    suspend fun removeEngineRoom(@Path("id") id: String): com.google.gson.JsonObject
    @GET("api/rooms/{id}/messages")
    suspend fun nativeRoomMessages(@Path("id") id: String): List<NativeRoomMessage>
    @POST("api/rooms/{id}/messages")
    suspend fun submitRoomMessage(@Path("id") id: String, @Body body: RoomInput): RoomSubmission
    @GET("api/providers")
    suspend fun engineProviders(): List<EngineProvider>
    @GET("api/providers/{id}/models")
    suspend fun engineModels(@Path("id") id: String): List<EngineModel>
    @GET("api/tools")
    suspend fun engineTools(): List<EngineTool>
    @GET("api/skills")
    suspend fun engineSkills(): List<EngineSkillState>
    @GET("api/skills/{id}")
    suspend fun engineSkill(@Path("id") id: String): com.google.gson.JsonObject
    @POST("api/skills/{id}")
    suspend fun saveEngineSkill(@Path("id") id: String, @Body record: com.google.gson.JsonObject): com.google.gson.JsonObject
    @POST("api/skills/import-repo")
    suspend fun importSkillRepository(@Body body: Map<String, String>): SkillImportResult


    @GET("v1/me")
    suspend fun me(): MeResponse

    @PATCH("v1/me")
    suspend fun updateMe(@Body body: Map<String, String>): MeResponse

    @GET("v1/characters")
    suspend fun characters(): ListResponse<CharacterDto>

    @GET("v1/conversations")
    suspend fun conversations(): ListResponse<ConversationDto>

    @GET("v1/conversations/{id}")
    suspend fun conversation(@Path("id") id: String): DataResponse<ConversationDto>

    @GET("v1/conversations/{id}/messages")
    suspend fun messages(
        @Path("id") id: String,
        @Query("before") before: String? = null
    ): ListResponse<MessageDto>

    @POST("v1/conversations/{id}/messages")
    suspend fun sendMessage(
        @Path("id") id: String,
        @Body body: SendMessageRequest
    ): DataResponse<MessageDto>

    @POST("v1/conversations/{id}/calls")
    suspend fun startCall(
        @Path("id") id: String,
        @Body body: StartCallRequest
    ): DataResponse<CallTokenResponse>

    @GET("v1/tasks")
    suspend fun tasks(): ListResponse<TaskDto>

    @GET("v1/schedules")
    suspend fun schedules(): ListResponse<ScheduleDto>

    @GET("v1/memories")
    suspend fun memories(@Query("character_id") characterId: String? = null): ListResponse<MemoryDto>

    @GET("v1/friends")
    suspend fun friends(): ListResponse<FriendDto>

    @GET("v1/friends/requests")
    suspend fun friendRequests(): ListResponse<FriendDto>

    @POST("v1/friends/requests/{id}/accept")
    suspend fun acceptFriend(@Path("id") id: String): DataResponse<FriendDto>

    @GET("v1/connections")
    suspend fun connections(): ListResponse<ConnectionDto>

    @POST("v1/connections/{id}/connect")
    suspend fun connect(@Path("id") id: String): DataResponse<ConnectionDto>

    @POST("v1/connections/{id}/disconnect")
    suspend fun disconnect(@Path("id") id: String): DataResponse<ConnectionDto>

    @GET("v1/explore")
    suspend fun explore(@Query("category") category: String? = null): ListResponse<ExploreDto>

    @GET("v1/vault")
    suspend fun vault(): ListResponse<VaultItemDto>

    @GET("v1/credits/transactions")
    suspend fun transactions(): ListResponse<Map<String, Any>>

    @GET("v1/inbox")
    suspend fun notifications(): ListResponse<NotificationDto>

    @POST("v1/auth/{provider}")
    suspend fun signIn(
        @Path("provider") provider: String,
        @Body body: SignInRequest
    ): AuthResponse

    @POST("v1/auth/phone/start")
    suspend fun startPhoneSignIn(@Body body: PhoneStartRequest): DataResponse<String>

    @POST("v1/auth/phone/verify")
    suspend fun verifyPhoneSignIn(@Body body: PhoneVerifyRequest): AuthResponse
}

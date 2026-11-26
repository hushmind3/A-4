package com.dominion.mobile.data.remote

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

data class DataResponse<T>(val data: T? = null)
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
    @GET("api/rooms")
    suspend fun engineRooms(): List<EngineRoom>
    @POST("api/rooms")
    suspend fun createRoom(@Body body: RoomCreate): EngineRoom
    @GET("api/rooms/{id}")
    suspend fun engineRoom(@Path("id") id: String): EngineRoom
    @POST("api/rooms/{id}")
    suspend fun updateRoom(@Path("id") id: String, @Body body: RoomConfigUpdate): EngineRoom
    @POST("api/rooms/{id}/messages")
    suspend fun submitRoomMessage(@Path("id") id: String, @Body body: RoomInput): RoomSubmission
    @GET("api/providers")
    suspend fun engineProviders(): List<EngineProvider>
    @GET("api/providers/{id}/models")
    suspend fun engineModels(@Path("id") id: String): List<EngineModel>
    @GET("api/tools")
    suspend fun engineTools(): List<EngineTool>
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

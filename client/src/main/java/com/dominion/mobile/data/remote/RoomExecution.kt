package com.dominion.mobile.data.remote

import com.google.gson.JsonElement

/** Existing engine room-state contract. No locally fabricated execution states. */
data class RoomExecution(
    val room: EngineRoom?,
    val sessionId: String?,
    val status: String?,
    val queueDepth: Int = 0,
    val jobs: List<EngineJob>? = null,
    val operations: List<EngineOperation>? = null,
    val approvals: List<EngineApproval>? = null
)
/** A job from the global endpoint may only be displayed for its original room. */
fun EngineJob.belongsToRoom(roomId: String, expectedJobId: String): Boolean =
    id == expectedJobId && input?.roomId == roomId && !sessionId.isNullOrBlank()

data class EngineJobInput(val roomId: String? = null, val input: String? = null,
    val messageId: String? = null, val sourceMessageId: String? = null)
data class EngineJob(val id: String?, val status: String?, val sessionId: String?,
    val input: EngineJobInput?, val stepIndex: Int = 0, val stepTotal: Int = 0,
    val error: JsonElement? = null, val result: NativeJobResult? = null)
data class EngineOperation(val id: String?, val tool: String?, val status: String?,
    val arguments: JsonElement?, val result: JsonElement?)
data class EngineApproval(val id: String?, val sessionId: String?, val tool: String?,
    val args: JsonElement?, val argsDigest: String?, val status: String?) {
    fun actionableIn(session: String) = !id.isNullOrBlank() && sessionId == session &&
        status in setOf("pending", "interrupted") && !argsDigest.isNullOrBlank() &&
        args != null && !args.isJsonNull && !tool.isNullOrBlank()
}
data class ApprovalDecision(val id: String, val allow: Boolean)
data class ApprovalResult(val resolved: Boolean = false, val status: String? = null,
    val resumed: Boolean = false)

data class AttachmentUpload(val roomId: String, val name: String, val mimeType: String, val data: String)
data class EngineAttachment(val id: String?, val roomId: String?, val name: String?,
    val mimeType: String?, val size: Long, val supportedForModel: String?)

/** Full room transcript retains the attachment and idempotency metadata dropped by /v1/messages. */
data class NativeRoomMessage(
    val id: String?, val role: String?, val content: String?, val created_at: String?,
    val metadata: NativeRoomMetadata?,
    val agent_id: String? = null
) {
    fun toDomain(roomId: String): com.dominion.mobile.data.model.Message? {
        val identifier = id?.takeIf { it.isNotBlank() } ?: return null
        val sender = when (role) {
            "user" -> com.dominion.mobile.data.model.MessageSender.USER
            "assistant" -> com.dominion.mobile.data.model.MessageSender.ASSISTANT
            "system" -> com.dominion.mobile.data.model.MessageSender.SYSTEM
            else -> return null
        }
        val attachments = metadata?.attachments.orEmpty().filter {
            it.roomId == roomId && !it.id.isNullOrBlank() && runCatching { java.util.UUID.fromString(it.id) }.isSuccess
        }
        val first = attachments.firstOrNull()
        if (content.isNullOrBlank() && first == null) return null
        val card = first?.let {
            com.dominion.mobile.data.model.MessageCard(
                kind = if (it.mimeType?.startsWith("image/") == true) com.dominion.mobile.data.model.CardKind.IMAGE
                    else com.dominion.mobile.data.model.CardKind.FILE,
                title = it.name ?: "attachment",
                subtitle = if (it.supportedForModel == "download") "파일 · 모델 직접 읽기 미지원" else "",
                meta = "${it.size} bytes"
            )
        }
        val timestamp = runCatching { java.time.Instant.parse(created_at).toEpochMilli() }.getOrNull()
            ?: runCatching { java.time.LocalDateTime.parse(created_at?.replace(' ', 'T')).toInstant(java.time.ZoneOffset.UTC).toEpochMilli() }.getOrDefault(0)
        return com.dominion.mobile.data.model.Message(id = identifier, conversationId = roomId,
            sender = sender, text = content.orEmpty(), createdAt = timestamp, card = card,
            kind = when (card?.kind) {
                com.dominion.mobile.data.model.CardKind.IMAGE -> com.dominion.mobile.data.model.MessageKind.IMAGE
                com.dominion.mobile.data.model.CardKind.FILE -> com.dominion.mobile.data.model.MessageKind.FILE
                else -> com.dominion.mobile.data.model.MessageKind.TEXT
            },
            clientMessageId = metadata?.clientMessageId,
            replyTo = metadata?.replyToMessageId,
            authorAgentId = agent_id?.takeIf { sender == com.dominion.mobile.data.model.MessageSender.ASSISTANT },
            attachmentIds = attachments.mapNotNull { it.id }.takeIf { it.isNotEmpty() },
            attachments = attachments.map { item ->
                com.dominion.mobile.data.model.MessageAttachment(requireNotNull(item.id),
                    item.name ?: "attachment", item.mimeType ?: "application/octet-stream", item.size)
            }.takeIf { it.isNotEmpty() })
    }
}
data class NativeRoomMetadata(val clientMessageId: String?, val attachments: List<EngineAttachment>?,
    val replyToMessageId: String? = null)

/** Attach actual job status only to its originating owner message in this room. */
fun correlateRoomJobs(messages: List<com.dominion.mobile.data.model.Message>,
    roomId: String, sessionId: String, jobs: List<EngineJob>): List<com.dominion.mobile.data.model.Message> {
    val valid = jobs.filter { !it.id.isNullOrBlank() && it.sessionId == sessionId &&
        it.input?.roomId == roomId && !it.status.isNullOrBlank() }
    return messages.map { message ->
        if (message.sender != com.dominion.mobile.data.model.MessageSender.USER ||
            message.conversationId != roomId) return@map message
        val job = valid.firstOrNull {
            val source = it.input?.messageId ?: it.input?.sourceMessageId
            if (message.jobId != null) it.id == message.jobId && (source == null || source == message.id)
            else source != null && source == message.id
        } ?: return@map message
        message.copy(jobId = job.id, jobStatus = job.status)
    }
}

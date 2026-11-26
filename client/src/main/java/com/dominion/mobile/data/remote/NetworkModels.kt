package com.dominion.mobile.data.remote

import com.dominion.mobile.data.model.Account
import com.dominion.mobile.data.model.AppState
import com.dominion.mobile.data.model.CardKind
import com.dominion.mobile.data.model.CardStep
import com.dominion.mobile.data.model.Character
import com.dominion.mobile.data.model.CharacterShape
import com.dominion.mobile.data.model.Connection
import com.dominion.mobile.data.model.Conversation
import com.dominion.mobile.data.model.ConversationKind
import com.dominion.mobile.data.model.ExploreCategory
import com.dominion.mobile.data.model.ExploreTemplate
import com.dominion.mobile.data.model.Friend
import com.dominion.mobile.data.model.MemoryItem
import com.dominion.mobile.data.model.Message
import com.dominion.mobile.data.model.MessageCard
import com.dominion.mobile.data.model.MessageKind
import com.dominion.mobile.data.model.MessageSender
import com.dominion.mobile.data.model.Plan
import com.dominion.mobile.data.model.ScheduleItem
import com.dominion.mobile.data.model.TaskItem
import com.dominion.mobile.data.model.VaultItem
import com.dominion.mobile.data.model.VaultType
import com.dominion.mobile.data.model.VoiceTone

/*
 * Wire models for the Dominion service. Field names mirror the JSON contract so a
 * backend implementation only needs to keep these DTOs in sync.
 */

data class AccountDto(
    val id: String = "",
    val name: String = "",
    val birthday: String = "",
    val phone: String = "",
    val email: String = "",
    val plan: String = "free",
    val jelly: Int = 0,
    val energy: Int = 0,
    val energy_max: Int = 10,
    val tickets: Int = 0,
    val credits: Int = 0,
    val invite_code: String = "",
    val level: Int = 1,
    val xp: Int = 0,
    val xp_goal: Int = 100
) {
    fun toDomain(): Account = Account(
        name = name.ifBlank { "민지" },
        birthday = birthday,
        phone = phone,
        email = email,
        plan = runCatching { Plan.valueOf(plan.uppercase()) }.getOrDefault(Plan.FREE),
        jelly = jelly,
        energy = energy,
        energyMax = energy_max,
        tickets = tickets,
        credits = credits,
        inviteCode = invite_code,
        level = level,
        xp = xp,
        xpGoal = xp_goal
    )
}

data class CharacterDto(
    val id: String = "",
    val name: String = "",
    val tagline: String = "",
    val bio: String = "",
    val color: String? = null,
    val shape: String? = null,
    val level: Int = 1,
    val xp: Int = 0,
    val xp_goal: Int = 100,
    val mine: Boolean = false,
    val online: Boolean = false,
    val voice: String? = null,
    val relation: String? = null
) {
    fun toDomain(): Character = Character(
        id = id,
        name = name,
        tagline = tagline,
        bio = bio,
        color = parseHex(color) ?: 0xFF4797EF,
        shape = runCatching { CharacterShape.valueOf(shape.orEmpty().uppercase()) }.getOrDefault(CharacterShape.BLOB),
        level = level,
        xp = xp,
        xpGoal = xp_goal,
        mine = mine,
        online = online,
        voice = runCatching { VoiceTone.valueOf(voice.orEmpty().uppercase()) }.getOrDefault(VoiceTone.SOFT),
        relation = relation
    )
}

data class CardDto(
    val kind: String = "none",
    val title: String = "",
    val subtitle: String = "",
    val meta: String = "",
    val steps: List<StepDto> = emptyList(),
    val accent: String? = null
) {
    fun toDomain(): MessageCard = MessageCard(
        kind = runCatching { CardKind.valueOf(kind.uppercase()) }.getOrDefault(CardKind.NONE),
        title = title,
        subtitle = subtitle,
        meta = meta,
        steps = steps.map { CardStep(it.text, it.done) },
        accent = parseHex(accent)
    )
}

data class StepDto(val text: String = "", val done: Boolean = false)

data class MessageDto(
    val id: String = "",
    val conversation_id: String = "",
    val sender: String = "character",
    val text: String = "",
    val kind: String = "text",
    val created_at: Long = 0,
    val card: CardDto? = null,
    val reply_to: String? = null,
    val client_message_id: String? = null
) {
    fun toDomain(): Message = Message(
        id = id,
        conversationId = conversation_id,
        sender = when (sender.lowercase()) {
            "me", "user" -> MessageSender.USER
            "system" -> MessageSender.SYSTEM
            else -> MessageSender.ASSISTANT
        },
        text = text,
        kind = runCatching { MessageKind.valueOf(kind.uppercase()) }.getOrDefault(MessageKind.TEXT),
        createdAt = if (created_at > 0) created_at else System.currentTimeMillis(),
        card = card?.toDomain(),
        replyTo = reply_to,
        clientMessageId = client_message_id
    )
}

data class ConversationDto(
    val id: String = "",
    val character_id: String = "",
    val title: String? = null,
    val kind: String = "direct",
    val pinned: Boolean = false,
    val muted: Boolean = false,
    val unread: Int = 0,
    val updated_at: Long = 0,
    val request: Boolean = false,
    val messages: List<MessageDto> = emptyList()
) {
    fun toDomain(): Conversation = Conversation(
        id = id,
        characterId = character_id,
        titleOverride = title,
        kind = runCatching { ConversationKind.valueOf(kind.uppercase()) }.getOrDefault(ConversationKind.DIRECT),
        messages = messages.map { it.toDomain() },
        pinned = pinned,
        muted = muted,
        unread = unread,
        updatedAt = if (updated_at > 0) updated_at else System.currentTimeMillis(),
        request = request
    )
}

data class TaskDto(
    val id: String = "",
    val title: String = "",
    val character_id: String = "",
    val steps: List<StepDto> = emptyList(),
    val due: String = "",
    val done: Boolean = false
) {
    fun toDomain(): TaskItem = TaskItem(
        id = id,
        title = title,
        characterId = character_id,
        steps = steps.map { CardStep(it.text, it.done) },
        due = due,
        done = done
    )
}

data class ScheduleDto(
    val id: String = "",
    val title: String = "",
    val character_id: String = "",
    val when_text: String = "",
    val repeat: String = "none",
    val enabled: Boolean = true
) {
    fun toDomain(): ScheduleItem = ScheduleItem(
        id = id,
        title = title,
        characterId = character_id,
        whenText = when_text,
        repeat = com.dominion.mobile.data.model.Repeat.entries.firstOrNull { it.name.equals(repeat, true) }
            ?: com.dominion.mobile.data.model.Repeat.NONE,
        enabled = enabled
    )
}

data class MemoryDto(
    val id: String = "",
    val character_id: String = "",
    val text: String = "",
    val created_at: Long = 0,
    val source: String = ""
) {
    fun toDomain(): MemoryItem = MemoryItem(
        id = id,
        characterId = character_id,
        text = text,
        createdAt = if (created_at > 0) created_at else System.currentTimeMillis(),
        source = source
    )
}

data class FriendDto(
    val id: String = "",
    val name: String = "",
    val handle: String = "",
    val color: String? = null,
    val is_assistant: Boolean = false,
    val relation: String = "",
    val favorite: Boolean = false,
    val character_id: String? = null
) {
    fun toDomain(): Friend = Friend(
        id = id,
        name = name,
        handle = handle,
        color = parseHex(color) ?: 0xFF4797EF,
        isAssistant = is_assistant,
        relation = relation,
        favorite = favorite,
        characterId = character_id
    )
}

data class ConnectionDto(
    val id: String = "",
    val name: String = "",
    val provider: String = "",
    val category: String = "",
    val connected: Boolean = false,
    val scopes: List<String> = emptyList(),
    val last_used: String? = null,
    val built_in: Boolean = false,
    val color: String? = null
) {
    fun toDomain(): Connection = Connection(
        id = id,
        name = name,
        provider = provider,
        category = category,
        connected = connected,
        scopes = scopes,
        lastUsed = last_used,
        builtIn = built_in,
        color = parseHex(color) ?: 0xFF4797EF
    )
}

data class VaultItemDto(
    val id: String = "",
    val type: String = "login",
    val title: String = "",
    val username: String = "",
    val secret: String = "",
    val note: String = "",
    val updated_at: Long = 0
) {
    fun toDomain(): VaultItem = VaultItem(
        id = id,
        type = runCatching { VaultType.valueOf(type.uppercase()) }.getOrDefault(VaultType.LOGIN),
        title = title,
        username = username,
        secret = secret,
        note = note,
        updatedAt = if (updated_at > 0) updated_at else System.currentTimeMillis()
    )
}

data class ExploreDto(
    val id: String = "",
    val title: String = "",
    val title_en: String = "",
    val subtitle: String = "",
    val category: String = "recommended",
    val accent: String? = null,
    val steps: List<String> = emptyList(),
    val prompt: String = ""
) {
    fun toDomain(): ExploreTemplate = ExploreTemplate(
        id = id,
        titleKo = title,
        titleEn = title_en.ifBlank { title },
        subtitleKo = subtitle,
        subtitleEn = subtitle,
        category = ExploreCategory.entries.firstOrNull { it.name.equals(category, true) } ?: ExploreCategory.RECOMMENDED,
        accent = parseHex(accent) ?: 0xFF4797EF,
        steps = steps,
        prompt = prompt
    )
}

data class SendMessageRequest(val text: String, val client_message_id: String? = null)
data class StartCallRequest(val video: Boolean)
data class UpdateSettingsRequest(
    val theme: String? = null,
    val language: String? = null,
    val notifications: Boolean? = null,
    val message_preview: Boolean? = null
)

data class MeResponse(val data: AccountDto? = null)
data class ListResponse<T>(val data: List<T> = emptyList())

fun parseHex(value: String?): Long? {
    if (value.isNullOrBlank()) return null
    val cleaned = value.removePrefix("#")
    return runCatching {
        if (cleaned.length == 6) 0xFF000000L or cleaned.toLong(16)
        else if (cleaned.length == 8) cleaned.toLong(16)
        else null
    }.getOrNull()
}

// Native room contract; the server owns tools, model selection, persona and execution.
data class EngineModel(val id: String, val name: String? = null)
data class EngineProvider(val id: String, val name: String, val kind: String? = null, val enabled: Boolean? = null)
data class RoomModel(val providerId: String, val model: String, val reasoningEffort: String? = null)
data class RoomCreate(val id: String, val name: String, val agentIds: List<String>,
    val toolsEnabled: Boolean = true, val autoModels: Boolean = true, val rounds: Int = 1,
    val models: List<RoomModel> = emptyList())
data class EngineRoom(val id: String, val name: String, val toolsEnabled: Boolean = false,
    val autoModels: Boolean = false, val models: List<RoomModel> = emptyList())
data class RoomConfigUpdate(val toolsEnabled: Boolean = true, val autoModels: Boolean? = null,
    val models: List<RoomModel>? = null)
data class RoomInput(val input: String, val clientMessageId: String, val reasoningLanguage: String)
data class RoomMessage(val id: String)
data class RoomSubmission(val jobId: String, val message: RoomMessage?)
data class EngineTool(val name: String, val description: String? = null, val approval: Boolean = false)
data class SkillImportResult(val added: Int, val skills: List<String>)

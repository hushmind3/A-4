package com.dominion.mobile.data.model

typealias Id = String

enum class Lang(val code: String) {
    KO("ko"),
    EN("en");

    companion object {
        fun from(code: String?): Lang = entries.firstOrNull { it.code == code } ?: KO
    }
}

enum class ThemeMode { SYSTEM, LIGHT, DARK }

enum class TabId(val ko: String, val en: String) {
    CHATS("채팅", "Chats"),
    EXPLORE("탐색", "Explore"),
    FRIENDS("친구", "Friends"),
    ARCHIVE("보관함", "Archive"),
    PROFILE("프로필", "Profile");

    fun label(lang: Lang) = if (lang == Lang.KO) ko else en
}

enum class Plan(val ko: String, val en: String, val priceKo: String, val priceEn: String) {
    FREE("무료", "Free", "₩0", "$0"),
    PLUS("플러스", "Plus", "₩12,000 / 월", "$8.99 / mo"),
    PRO("프로", "Pro", "₩29,000 / 월", "$19.99 / mo");

    fun label(lang: Lang) = if (lang == Lang.KO) ko else en
    fun price(lang: Lang) = if (lang == Lang.KO) priceKo else priceEn
}

enum class CharacterShape { BLOB, BALLOON, DONUT, STAR, CLOUD, GEM }

enum class VoiceTone(val ko: String, val en: String) {
    SOFT("다정한", "Warm"),
    BRIGHT("밝은", "Bright"),
    CALM("차분한", "Calm");

    fun label(lang: Lang) = if (lang == Lang.KO) ko else en
}

data class Character(
    val id: Id,
    val name: String,
    val tagline: String = "",
    val taglineEn: String = "",
    val bio: String = "",
    val color: Long = 0xFF4797EF,
    val shape: CharacterShape = CharacterShape.BLOB,
    val level: Int = 1,
    val xp: Int = 30,
    val xpGoal: Int = 100,
    val mine: Boolean = false,
    val online: Boolean = false,
    val voice: VoiceTone = VoiceTone.SOFT,
    val relation: String? = null,
    val avatarPhoto: String? = null
)

data class CharacterPreference(
    val name: String? = null, val color: Long? = null,
    val shape: CharacterShape? = null, val voice: VoiceTone? = null, val bio: String? = null,
    val avatarPhoto: String? = null
) {
    fun applyTo(character: Character) = character.copy(
        name = name ?: character.name, color = color ?: character.color,
        shape = shape ?: character.shape, voice = voice ?: character.voice,
        bio = bio ?: character.bio, avatarPhoto = avatarPhoto?.takeIf { it.isNotBlank() }
    )
}

enum class MessageSender {
    @com.google.gson.annotations.SerializedName(value = "USER", alternate = ["ME"])
    USER,
    @com.google.gson.annotations.SerializedName(value = "ASSISTANT", alternate = ["CHARACTER"])
    ASSISTANT,
    SYSTEM
}

enum class MessageKind { TEXT, TASK, CALL, FILE, LINK, SCHEDULE, IMAGE, VOICE, SYSTEM }

enum class CardKind { NONE, TASK, CALL, FILE, LINK, SCHEDULE, CONNECTION, IMAGE }

data class CardStep(val text: String, val done: Boolean = false)

data class MessageCard(
    val kind: CardKind = CardKind.NONE,
    val title: String = "",
    val subtitle: String = "",
    val meta: String = "",
    val steps: List<CardStep> = emptyList(),
    val accent: Long? = null
)

enum class MessageStatus { SENDING, SENT, FAILED }

data class Message(
    val id: Id,
    val conversationId: Id,
    val sender: MessageSender,
    val text: String = "",
    val kind: MessageKind = MessageKind.TEXT,
    val createdAt: Long = System.currentTimeMillis(),
    val card: MessageCard? = null,
    val status: MessageStatus = MessageStatus.SENT,
    val replyTo: Id? = null,
    val reactions: List<String> = emptyList(),
    val clientMessageId: String? = null,
    val modelId: String? = null,
    val jobId: String? = null,
    val attachmentIds: List<String>? = null,
    val authorAgentId: String? = null,
    val jobStatus: String? = null,
    val attachments: List<MessageAttachment>? = null
)

data class MessageAttachment(val id: String, val name: String, val mimeType: String, val size: Long)

enum class ConversationKind { DIRECT, EXTRA, GROUP, TASK }

data class Conversation(
    val id: Id,
    val characterId: Id,
    val titleOverride: String? = null,
    val kind: ConversationKind = ConversationKind.DIRECT,
    val messages: List<Message> = emptyList(),
    val pinned: Boolean = false,
    val muted: Boolean = false,
    val unread: Int = 0,
    val draft: String = "",
    val updatedAt: Long = System.currentTimeMillis(),
    val request: Boolean = false,
    val tab: String = "all",
    val sessionId: String? = null
) {
    fun title(characters: List<Character>): String =
        titleOverride ?: characters.firstOrNull { it.id == characterId }?.name ?: "Dominion"

    fun lastMessage(): Message? = messages.lastOrNull { it.kind != MessageKind.SYSTEM }
}

data class TaskItem(
    val id: Id,
    val title: String,
    val characterId: Id,
    val steps: List<CardStep> = emptyList(),
    val due: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val done: Boolean = false,
    val engineRoomId: String? = null,
    val engineJobId: String? = null,
    val engineSessionId: String? = null,
    val engineMessageId: String? = null,
    val engineStatus: String? = null,
    val enginePrompt: String? = null,
    val engineLanguage: String? = null,
    val requestDetails: String? = null,
    val watchCondition: String? = null,
    val koreanLookup: com.dominion.mobile.platform.network.KoreanLookup? = null,
    val phonePackage: String? = null,
    val kakaoMessage: com.dominion.mobile.data.KakaoMessageRequest? = null,
    val phonePublicRead:Boolean=false,
    val phoneTransaction:com.dominion.mobile.data.PhoneTransactionRequest?=null,
    val phonePrivateRead:com.dominion.mobile.data.PhonePrivateRead?=null,
    val workflowId:String?=null,
    val workflowAfter:String?=null,
    val workflowState:String?=null,
    val workflowEvidence:String?=null,
    val replyWatchFrom:Long?=null,
    val replyWatchUntil:Long?=null,
    val replyCandidate:String?=null,
    val replyCandidateAt:Long?=null,
    val replyReviewedAt:Long?=null,
    val negotiationParent:String?=null,
    val negotiationNextRoom:String?=null,
    val negotiationResolution:String?=null
) {
    val progress: Float
        get() = if (steps.isEmpty()) (if (done) 1f else 0f)
        else steps.count { it.done }.toFloat() / steps.size
}

enum class Repeat(val ko: String, val en: String) {
    NONE("반복 없음", "Once"),
    DAILY("매일", "Daily"),
    WEEKLY("매주", "Weekly"),
    MONTHLY("매월", "Monthly");

    fun label(lang: Lang) = if (lang == Lang.KO) ko else en
}

data class ScheduleItem(
    val id: Id,
    val title: String,
    val characterId: Id,
    val whenText: String,
    val repeat: Repeat = Repeat.NONE,
    val enabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val scheduledAt: Long? = null,
    val timeZone: String? = null,
    val lastNotifiedAt: Long? = null,
    val workRequest: String? = null,
    val lastDispatchedAt: Long? = null,
    val pendingDispatchAt: Long? = null,
    val dispatchStatus: String? = null,
    val watchCondition: String? = null
)

data class MemoryItem(
    val id: Id,
    val characterId: Id,
    val text: String,
    val createdAt: Long = System.currentTimeMillis(),
    val source: String = "대화에서 저장",
    val serverBacked: Boolean? = null,
    val retrievalEligible: Boolean? = null
)

data class Friend(
    val id: Id,
    val name: String,
    val handle: String,
    val color: Long,
    val isAssistant: Boolean = false,
    val relation: String = "친구",
    val favorite: Boolean = false,
    val characterId: Id? = null,
    val phone: String? = null,
    val email: String? = null,
    val localContact: Boolean = false
)

data class FriendRequest(
    val id: Id,
    val name: String,
    val handle: String,
    val color: Long,
    val message: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

data class Connection(
    val id: Id,
    val name: String,
    val provider: String,
    val category: String,
    val connected: Boolean = false,
    val scopes: List<String> = emptyList(),
    val lastUsed: String? = null,
    val builtIn: Boolean = false,
    val color: Long = 0xFF4797EF
)

enum class VaultType(val ko: String, val en: String) {
    LOGIN("로그인", "Login"),
    CARD("카드", "Card"),
    ADDRESS("주소", "Address"),
    PHONE("전화번호", "Phone");

    fun label(lang: Lang) = if (lang == Lang.KO) ko else en
}

data class VaultItem(
    val id: Id,
    val type: VaultType,
    val title: String,
    val username: String = "",
    val secret: String = "",
    val note: String = "",
    val updatedAt: Long = System.currentTimeMillis()
)

enum class ArchiveType { IMAGE, VIDEO, FILE, LINK }

data class ArchiveItem(
    val id: Id,
    val type: ArchiveType,
    val title: String,
    val meta: String,
    val conversationId: Id? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val color: Long = 0xFF4797EF,
    val messageId: String? = null,
    val attachmentId: String? = null,
    val mimeType: String? = null,
    val url: String? = null
)

enum class ActivityType { FRIEND, TASK, CREDIT, SYSTEM, CALL }

data class ActivityItem(
    val id: Id,
    val type: ActivityType,
    val title: String,
    val body: String,
    val createdAt: Long = System.currentTimeMillis(),
    val read: Boolean = false
)

enum class TxKind { CHARGE, USE, REWARD }

data class CreditTransaction(
    val id: Id,
    val title: String,
    val amount: Int,
    val createdAt: Long = System.currentTimeMillis(),
    val kind: TxKind = TxKind.USE
)

enum class ProductKind { JELLY, ENERGY, TICKET, PLAN }

data class StoreProduct(
    val id: Id,
    val title: String,
    val subtitle: String,
    val price: String,
    val amount: Int,
    val kind: ProductKind
)

enum class ExploreCategory(val ko: String, val en: String) {
    RECOMMENDED("추천", "For you"),
    WORK("일", "Work"),
    MONEY("돈", "Money"),
    HOME("집", "Home"),
    HEALTH("건강", "Health"),
    SHOPPING("쇼핑", "Shopping"),
    TRAVEL("여행", "Travel"),
    FUN("놀거리", "Fun"),
    PEOPLE("사람", "People"),
    KOREA("생활·행정", "Korean daily services");

    fun label(lang: Lang) = if (lang == Lang.KO) ko else en
}

data class ExploreTemplate(
    val id: Id,
    val titleKo: String,
    val titleEn: String,
    val subtitleKo: String,
    val subtitleEn: String,
    val category: ExploreCategory,
    val accent: Long,
    val steps: List<String> = emptyList(),
    val prompt: String = ""
) {
    fun title(lang: Lang) = if (lang == Lang.KO) titleKo else titleEn
    fun subtitle(lang: Lang) = if (lang == Lang.KO) subtitleKo else subtitleEn
}

data class Account(
    val name: String = "민지",
    val birthday: String = "1998-04-12",
    val phone: String = "+82 10 1234 5678",
    val email: String = "minji@example.com",
    val plan: Plan = Plan.PLUS,
    val jelly: Int = 240,
    val energy: Int = 7,
    val energyMax: Int = 10,
    val tickets: Int = 2,
    val credits: Int = 1200,
    val inviteCode: String = "MINJI-4F2K",
    val level: Int = 12,
    val xp: Int = 62,
    val xpGoal: Int = 100,
    val lastClaimAt: Long = 0
)

data class SettingsState(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val lang: Lang = Lang.KO,
    val notifications: Boolean = true,
    val messagePreview: Boolean = true,
    val roomSounds: Boolean = true,
    val appLock: Boolean = false,
    val hideContent: Boolean = false,
    val reduceMotion: Boolean = false,
    val guideDismissed: Boolean = false,
    val tabOrder: List<TabId> = listOf(TabId.CHATS, TabId.EXPLORE, TabId.FRIENDS, TabId.ARCHIVE, TabId.PROFILE),
    val blockedUserIds: List<String> = emptyList(),
    val legalVersion: String = "2026.11",
    val mobileModel:String?=null
)

enum class CallPhase { PERMISSION, CONNECTING, WAITING_PEER, PEER_PRESENT, RECONNECTING, FAILED }

data class CallSession(
    val conversationId: Id,
    val characterId: Id,
    val video: Boolean,
    val startedAt: Long = System.currentTimeMillis(),
    val muted: Boolean = false,
    val speaker: Boolean = true,
    val minimized: Boolean = false,
    val phase: CallPhase = CallPhase.PERMISSION,
    val error: String? = null,
    val sessionId: String = java.util.UUID.randomUUID().toString(),
    val requestVideo: Boolean = false,
    val connectedAt: Long? = null,
    val hadVideo: Boolean = false,
    val realtimeAgent: Boolean = false
)

data class AppState(
    val version: Int = 1,
    val onboarded: Boolean = false,
    val account: Account = Account(),
    val characters: List<Character> = emptyList(),
    val conversations: List<Conversation> = emptyList(),
    val tasks: List<TaskItem> = emptyList(),
    val schedules: List<ScheduleItem> = emptyList(),
    val memories: List<MemoryItem> = emptyList(),
    val friends: List<Friend> = emptyList(),
    val friendRequests: List<FriendRequest> = emptyList(),
    val connections: List<Connection> = emptyList(),
    val vault: List<VaultItem> = emptyList(),
    val archive: List<ArchiveItem> = emptyList(),
    val activity: List<ActivityItem> = emptyList(),
    val transactions: List<CreditTransaction> = emptyList(),
    val products: List<StoreProduct> = emptyList(),
    val explore: List<ExploreTemplate> = emptyList(),
    val settings: SettingsState = SettingsState(),
    val currentTab: TabId = TabId.CHATS,
    val call: CallSession? = null,
    val typingConversationId: Id? = null,
    val sharedText: String? = null,
    val vaultUnlocked: Boolean = false,
    val characterPreferences: Map<Id, CharacterPreference>? = emptyMap(),
    val hiddenRooms: Set<Id>? = emptySet(),
    val miniAppStates: Map<String, Map<String, String>>? = emptyMap(),
    val mobileActions:List<com.dominion.mobile.data.harness.MobileActionRecord>?=emptyList()
) {
    fun character(id: Id): Character? = characters.firstOrNull { it.id == id }
    fun myCharacter(): Character? = characters.firstOrNull { it.mine }
    fun conversation(id: Id): Conversation? = conversations.firstOrNull { it.id == id }
    fun conversationFor(characterId: Id): Conversation? =
        conversations.firstOrNull { it.characterId == characterId && it.kind == ConversationKind.DIRECT }

    fun unreadChats(): Int = conversations.sumOf { it.unread }
    fun unreadActivity(): Int = activity.count { !it.read }
    fun activeTasks(): Int = tasks.count { !it.done }
    fun enabledSchedules(): Int = schedules.count { it.enabled }
}

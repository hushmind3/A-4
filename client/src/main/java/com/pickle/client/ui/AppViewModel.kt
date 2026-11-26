package com.pickle.client.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.pickle.client.data.DemoData
import com.pickle.client.data.DemoReplies
import com.pickle.client.data.PickleRepository
import com.pickle.client.data.model.Account
import com.pickle.client.data.model.ActivityItem
import com.pickle.client.data.model.ActivityType
import com.pickle.client.data.model.AppState
import com.pickle.client.data.model.CallSession
import com.pickle.client.data.model.CardKind
import com.pickle.client.data.model.CardStep
import com.pickle.client.data.model.Character
import com.pickle.client.data.model.CharacterShape
import com.pickle.client.data.model.Connection
import com.pickle.client.data.model.Conversation
import com.pickle.client.data.model.ConversationKind
import com.pickle.client.data.model.CreditTransaction
import com.pickle.client.data.model.Friend
import com.pickle.client.data.model.Lang
import com.pickle.client.data.model.MemoryItem
import com.pickle.client.data.model.Message
import com.pickle.client.data.model.MessageCard
import com.pickle.client.data.model.MessageKind
import com.pickle.client.data.model.MessageSender
import com.pickle.client.data.model.ProductKind
import com.pickle.client.data.model.Repeat
import com.pickle.client.data.model.ScheduleItem
import com.pickle.client.data.model.SettingsState
import com.pickle.client.data.model.StoreProduct
import com.pickle.client.data.model.TabId
import com.pickle.client.data.model.ThemeMode
import com.pickle.client.data.model.TaskItem
import com.pickle.client.data.model.TxKind
import com.pickle.client.data.model.VaultItem
import com.pickle.client.data.model.VaultType
import com.pickle.client.data.model.VoiceTone
import com.pickle.client.util.formatDuration
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

data class OnboardingProfile(
    val name: String,
    val birthday: String,
    val characterName: String,
    val color: Long,
    val shape: CharacterShape,
    val voice: VoiceTone,
    val lang: Lang
)

class AppViewModel(
    private val repo: PickleRepository
) : ViewModel() {

    val state: StateFlow<AppState> = repo.state

    init {
        viewModelScope.launch { runCatching { repo.refresh() } }
    }

    private fun mutate(transform: (AppState) -> AppState) = repo.update(transform)

    private fun AppState.withConversation(id: String, transform: (Conversation) -> Conversation): AppState =
        copy(conversations = conversations.map { if (it.id == id) transform(it) else it })

    private fun id(prefix: String) = "${prefix}_${System.currentTimeMillis()}"

    // ---------------------------------------------------------------- onboarding

    fun completeOnboarding(profile: OnboardingProfile) {
        mutate { s ->
            val mine = s.myCharacter()
            val updatedMine = if (mine == null) {
                Character(
                    id = DemoData.MY_ID,
                    name = profile.characterName,
                    tagline = "나의 피클",
                    bio = "나만의 피클. 함께 이야기하고 일을 맡겨보세요.",
                    color = profile.color,
                    shape = profile.shape,
                    level = 1,
                    xp = 0,
                    mine = true,
                    voice = profile.voice
                )
            } else {
                mine.copy(
                    name = profile.characterName,
                    color = profile.color,
                    shape = profile.shape,
                    voice = profile.voice
                )
            }
            val characters = if (mine == null) s.characters + updatedMine
            else s.characters.map { if (it.id == updatedMine.id) updatedMine else it }
            var next = s.copy(
                onboarded = true,
                account = s.account.copy(name = profile.name, birthday = profile.birthday),
                characters = characters,
                settings = s.settings.copy(lang = profile.lang)
            )
            if (next.conversations.isEmpty()) {
                next = next.copy(
                    conversations = listOf(
                        Conversation(
                            id = "conv_welcome",
                            characterId = updatedMine.id,
                            messages = listOf(
                                Message(
                                    id = "welcome_1",
                                    conversationId = "conv_welcome",
                                    sender = MessageSender.CHARACTER,
                                    text = "안녕하세요, ${profile.characterName}이에요. 오늘은 무엇을 함께 해볼까요?",
                                    createdAt = System.currentTimeMillis()
                                )
                            ),
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                )
            }
            next
        }
    }

    fun enterDemo() {
        mutate { s ->
            val mine = s.myCharacter()
            var next = s.copy(onboarded = true)
            if (next.conversations.isEmpty() && mine != null) {
                next = next.copy(
                    conversations = listOf(
                        Conversation(
                            id = "conv_welcome",
                            characterId = mine.id,
                            messages = listOf(
                                Message(
                                    id = "welcome_1",
                                    conversationId = "conv_welcome",
                                    sender = MessageSender.CHARACTER,
                                    text = "데모 모드예요. 채팅, 통화, 일정, 금고까지 자유롭게 둘러보세요.",
                                    createdAt = System.currentTimeMillis()
                                )
                            ),
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                )
            }
            next
        }
    }

    fun resetAllData() {
        repo.reset()
    }

    fun resetDemoContent() {
        mutate { DemoData.initial(onboarded = true).copy(settings = it.settings) }
    }

    fun deleteAccount() {
        mutate { DemoData.freshState() }
    }

    // --------------------------------------------------------------------- shell

    fun selectTab(tab: TabId) = mutate { it.copy(currentTab = tab) }

    fun updateAccountProfile(name: String, birthday: String) {
        mutate { s ->
            s.copy(
                account = s.account.copy(name = name, birthday = birthday),
                characters = s.characters.map { if (it.mine) it.copy(name = it.name) else it }
            )
        }
    }

    fun updateCharacter(
        characterId: String,
        name: String? = null,
        color: Long? = null,
        shape: CharacterShape? = null,
        voice: VoiceTone? = null,
        bio: String? = null
    ) {
        mutate { s ->
            s.copy(characters = s.characters.map {
                if (it.id == characterId) it.copy(
                    name = name ?: it.name,
                    color = color ?: it.color,
                    shape = shape ?: it.shape,
                    voice = voice ?: it.voice,
                    bio = bio ?: it.bio
                ) else it
            })
        }
    }

    // -------------------------------------------------------------------- chats

    fun sendMessage(conversationId: String, text: String, card: MessageCard? = null) {
        val trimmed = text.trim()
        if (trimmed.isEmpty() && card == null) return
        val conversation = repo.state.value.conversation(conversationId) ?: return
        val now = System.currentTimeMillis()
        val message = Message(
            id = id("msg"),
            conversationId = conversationId,
            sender = MessageSender.ME,
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
            card = card
        )
        mutate { s ->
            s.withConversation(conversationId) {
                it.copy(messages = it.messages + message, updatedAt = now, unread = 0, draft = "")
            }.copy(typingConversationId = conversationId)
        }

        viewModelScope.launch {
            delay(850)
            mutate { s ->
                val conv = s.conversation(conversationId) ?: return@mutate s
                val character = s.character(conv.characterId) ?: return@mutate s
                val reply = DemoReplies.replyTo(trimmed, character, lang = s.settings.lang)
                val replyMessage = Message(
                    id = id("msg"),
                    conversationId = conversationId,
                    sender = MessageSender.CHARACTER,
                    text = reply,
                    createdAt = System.currentTimeMillis()
                )
                s.withConversation(conversationId) {
                    it.copy(messages = it.messages + replyMessage, updatedAt = System.currentTimeMillis())
                }.copy(typingConversationId = null)
            }
        }
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
        sendMessage(conversationId, "이걸 같이 해볼까요?", card)
    }

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

    fun renameConversation(conversationId: String, title: String) = mutate { s ->
        s.withConversation(conversationId) { it.copy(titleOverride = title) }
    }

    fun clearConversation(conversationId: String) = mutate { s ->
        s.withConversation(conversationId) {
            it.copy(messages = listOf(
                Message(
                    id = id("sys"),
                    conversationId = conversationId,
                    sender = MessageSender.SYSTEM,
                    text = "대화가 비워졌어요.",
                    kind = MessageKind.SYSTEM,
                    createdAt = System.currentTimeMillis()
                )
            ))
        }
    }

    fun rewindConversation(conversationId: String): Boolean {
        val conv = repo.state.value.conversation(conversationId) ?: return false
        val keep = conv.messages.filter { it.sender == MessageSender.ME }.takeLast(2)
        mutate { s ->
            s.withConversation(conversationId) {
                it.copy(messages = keep + Message(
                    id = id("sys"),
                    conversationId = conversationId,
                    sender = MessageSender.SYSTEM,
                    text = "마지막 두 번의 요청으로 되감았어요.",
                    kind = MessageKind.SYSTEM,
                    createdAt = System.currentTimeMillis()
                ))
            }
        }
        return true
    }

    fun deleteConversation(conversationId: String) = mutate { s ->
        s.copy(conversations = s.conversations.filterNot { it.id == conversationId })
    }

    fun createExtraConversation(title: String): String {
        val s = repo.state.value
        val mine = s.myCharacter() ?: return ""
        val convId = id("conv")
        val conversation = Conversation(
            id = convId,
            characterId = mine.id,
            titleOverride = title.ifBlank { "새 대화" },
            kind = ConversationKind.EXTRA,
            messages = listOf(
                Message(
                    id = id("sys"),
                    conversationId = convId,
                    sender = MessageSender.SYSTEM,
                    text = "'${title.ifBlank { "새 대화" }}' 대화를 시작했어요.",
                    kind = MessageKind.SYSTEM,
                    createdAt = System.currentTimeMillis()
                )
            ),
            updatedAt = System.currentTimeMillis()
        )
        mutate { it.copy(conversations = listOf(conversation) + it.conversations) }
        return convId
    }

    fun directConversationFor(characterId: String): String? {
        val s = repo.state.value
        s.conversationFor(characterId)?.let { return it.id }
        val character = s.character(characterId) ?: return null
        val convId = id("conv")
        mutate {
            it.copy(
                conversations = listOf(
                    Conversation(
                        id = convId,
                        characterId = characterId,
                        messages = listOf(
                            Message(
                                id = id("msg"),
                                conversationId = convId,
                                sender = MessageSender.CHARACTER,
                                text = "안녕하세요! ${character.name}이에요. 무엇을 도와드릴까요?",
                                createdAt = System.currentTimeMillis()
                            )
                        ),
                        updatedAt = System.currentTimeMillis()
                    )
                ) + it.conversations
            )
        }
        return convId
    }

    // -------------------------------------------------------------------- calls

    fun startCall(conversationId: String, video: Boolean): Boolean {
        val s = repo.state.value
        val conv = s.conversation(conversationId) ?: return false
        if (s.account.energy <= 0) return false
        val session = CallSession(
            conversationId = conversationId,
            characterId = conv.characterId,
            video = video,
            startedAt = System.currentTimeMillis()
        )
        mutate {
            it.copy(
                call = session,
                account = it.account.copy(energy = (it.account.energy - 1).coerceAtLeast(0)),
                conversations = it.conversations.map { c -> if (c.id == conversationId) c.copy(unread = 0) else c }
            )
        }
        return true
    }

    fun toggleCallMute() = mutate { s -> s.call?.let { s.copy(call = it.copy(muted = !it.muted)) } ?: s }
    fun toggleCallSpeaker() = mutate { s -> s.call?.let { s.copy(call = it.copy(speaker = !it.speaker)) } ?: s }
    fun toggleCallVideo() = mutate { s -> s.call?.let { s.copy(call = it.copy(video = !it.video)) } ?: s }

    fun endCall() {
        val s = repo.state.value
        val call = s.call ?: return
        val elapsed = System.currentTimeMillis() - call.startedAt
        val character = s.character(call.characterId)
        val card = MessageCard(
            kind = CardKind.CALL,
            title = if (call.video) "영상 통화" else "음성 통화",
            subtitle = formatDuration(elapsed),
            meta = "방금 전"
        )
        val message = Message(
            id = id("msg"),
            conversationId = call.conversationId,
            sender = MessageSender.CHARACTER,
            kind = MessageKind.CALL,
            createdAt = System.currentTimeMillis(),
            card = card
        )
        mutate { st ->
            st.copy(
                call = null,
                conversations = st.conversations.map { c ->
                    if (c.id == call.conversationId) c.copy(
                        messages = c.messages + message,
                        updatedAt = System.currentTimeMillis()
                    ) else c
                },
                activity = listOf(
                    ActivityItem(
                        id = id("act"),
                        type = ActivityType.CALL,
                        title = "${character?.name ?: "피클"}와 통화했어요",
                        body = "${formatDuration(elapsed)} 동안 이야기를 나눴어요.",
                        createdAt = System.currentTimeMillis()
                    )
                ) + st.activity
            )
        }
    }

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

    fun deleteTask(taskId: String) = mutate { s ->
        s.copy(tasks = s.tasks.filterNot { it.id == taskId })
    }

    fun addSchedule(title: String, whenText: String, characterId: String, repeat: Repeat) {
        if (title.isBlank()) return
        mutate {
            it.copy(
                schedules = listOf(
                    ScheduleItem(
                        id = id("sch"),
                        title = title.trim(),
                        whenText = whenText,
                        characterId = characterId,
                        repeat = repeat
                    )
                ) + it.schedules
            )
        }
    }

    fun toggleSchedule(id: String) = mutate { s ->
        s.copy(schedules = s.schedules.map { if (it.id == id) it.copy(enabled = !it.enabled) else it })
    }

    fun deleteSchedule(id: String) = mutate { s ->
        s.copy(schedules = s.schedules.filterNot { it.id == id })
    }

    fun addMemory(characterId: String, text: String) {
        if (text.isBlank()) return
        mutate {
            it.copy(
                memories = listOf(
                    MemoryItem(id = id("mem"), characterId = characterId, text = text.trim())
                ) + it.memories
            )
        }
    }

    fun deleteMemory(id: String) = mutate { s ->
        s.copy(memories = s.memories.filterNot { it.id == id })
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
                    body = "${request.handle} · 이제 피클을 나눌 수 있어요.",
                    createdAt = System.currentTimeMillis()
                )
            ) + s.activity
        )
    }

    fun declineFriendRequest(requestId: String) = mutate { s ->
        s.copy(friendRequests = s.friendRequests.filterNot { it.id == requestId })
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
                    relation = "새 친구"
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
                    body = "피클이 이 도구를 사용할 수 있어요.",
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

    fun purchase(product: StoreProduct) = mutate { s ->
        val account = when (product.kind) {
            ProductKind.JELLY -> s.account.copy(jelly = s.account.jelly + product.amount)
            ProductKind.ENERGY -> s.account.copy(energy = (s.account.energy + product.amount).coerceAtMost(s.account.energyMax))
            ProductKind.TICKET -> s.account.copy(tickets = s.account.tickets + product.amount)
            ProductKind.PLAN -> s.account.copy(plan = com.pickle.client.data.model.Plan.PLUS, jelly = s.account.jelly + 300)
        }
        s.copy(
            account = account,
            transactions = listOf(
                CreditTransaction(
                    id = id("tx"),
                    title = product.title,
                    amount = if (product.kind == ProductKind.PLAN) 0 else product.amount,
                    kind = TxKind.CHARGE
                )
            ) + s.transactions,
            activity = listOf(
                ActivityItem(
                    id = id("act"),
                    type = ActivityType.CREDIT,
                    title = "${product.title} 구매 완료",
                    body = "결제가 완료됐어요. (데모 결제)",
                    createdAt = System.currentTimeMillis()
                )
            ) + s.activity
        )
    }

    fun claimDailyJelly(): Boolean {
        val s = repo.state.value
        val elapsed = System.currentTimeMillis() - s.account.lastClaimAt
        if (elapsed < TimeUnit.HOURS.toMillis(24)) return false
        mutate {
            it.copy(
                account = it.account.copy(jelly = it.account.jelly + 30, lastClaimAt = System.currentTimeMillis()),
                transactions = listOf(
                    CreditTransaction(id = id("tx"), title = "출석 보상", amount = 30, kind = TxKind.REWARD)
                ) + it.transactions,
                activity = listOf(
                    ActivityItem(
                        id = id("act"),
                        type = ActivityType.CREDIT,
                        title = "젤리 30개를 받았어요",
                        body = "오늘의 출석 보상이에요.",
                        createdAt = System.currentTimeMillis()
                    )
                ) + it.activity
            )
        }
        return true
    }

    fun useResetTicket(conversationId: String): Boolean {
        val s = repo.state.value
        if (s.account.tickets <= 0) return false
        mutate { st ->
            st.copy(
                account = st.account.copy(tickets = st.account.tickets - 1),
                transactions = listOf(
                    CreditTransaction(id = id("tx"), title = "리셋 티켓 사용", amount = -1, kind = TxKind.USE)
                ) + st.transactions
            )
        }
        return rewindConversation(conversationId)
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

    fun receiveSharedText(text: String?) {
        if (!text.isNullOrBlank()) mutate { it.copy(sharedText = text) }
    }

    fun clearSharedText() = mutate { it.copy(sharedText = null) }

    fun shareToConversation(conversationId: String) {
        val text = repo.state.value.sharedText ?: return
        sendMessage(conversationId, text)
        mutate { it.copy(sharedText = null) }
    }

    class Factory(private val repo: PickleRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = AppViewModel(repo) as T
    }
}

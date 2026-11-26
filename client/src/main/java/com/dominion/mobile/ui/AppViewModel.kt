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
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.TimeUnit

class AppViewModel(
    private val repo: DominionRepository
) : ViewModel() {

    val state: StateFlow<AppState> = repo.state

    val connection = repo.connection

    suspend fun refreshServer() = repo.refresh()

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
        if (repo.state.value.conversation(conversationId) == null) return
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
            status = MessageStatus.SENDING
        )
        mutate { s ->
            s.withConversation(conversationId) {
                it.copy(messages = it.messages + message, updatedAt = now, unread = 0, draft = "")
            }.copy(typingConversationId = null)
        }

        deliverRemoteMessage(message)
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
                _notice.value = "본체 연결, 모델 및 도구 설정을 확인하세요."
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

            mutate { s ->
                val conversation = s.conversation(conversationId) ?: return@mutate s
                if (conversation.messages.none { it.id == message.id }) return@mutate s
                s.withConversation(conversationId) { conv ->
                    val acknowledged = conv.messages.map { local ->
                        if (local.id != message.id) local
                        else if (acknowledgement.sender == MessageSender.USER && acknowledgement.id.isNotBlank()) {
                            acknowledgement.copy(
                                status = MessageStatus.SENT,
                                card = acknowledgement.card ?: local.card
                            )
                        } else local.copy(status = MessageStatus.SENT)
                    }
                    val withReply = if (
                        acknowledgement.sender != MessageSender.USER &&
                        acknowledgement.id.isNotBlank() &&
                        acknowledged.none { it.id == acknowledgement.id }
                    ) acknowledged + acknowledgement else acknowledged
                    conv.copy(messages = withReply, updatedAt = System.currentTimeMillis())
                }
            }

            // Server may acknowledge a send before the assistant response is ready.
            // Save the pre-fetch IDs so the reply watcher can recognize a new response.
            if (acknowledgement.sender != MessageSender.USER &&
                acknowledgement.id.isNotBlank()) return@launch
            val knownIds = repo.state.value.conversation(conversationId)
                ?.messages?.map { it.id }?.toSet() ?: return@launch
            var replied = false
            val latest = try {
                repo.loadMessages(conversationId)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            }
            if (latest != null) {
                replied = latest.any {
                    it.sender == MessageSender.ASSISTANT && it.id.isNotBlank() && it.id !in knownIds
                }
                applyServerMessages(conversationId, latest)
            }
            if (replied) return@launch

            mutate { s -> s.copy(typingConversationId = conversationId) }
            try {
                val delayed = repo.pollForReply(conversationId, knownIds)
                if (delayed.isNotEmpty()) applyServerMessages(conversationId, delayed)
            } finally {
                mutate { s ->
                    if (s.typingConversationId == conversationId)
                        s.copy(typingConversationId = null) else s
                }
            }
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
    fun refreshConversation(conversationId: String) {
        viewModelScope.launch {
            val messages = try {
                repo.loadMessages(conversationId)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            }
            if (messages != null) applyServerMessages(conversationId, messages)
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
        val keep = conv.messages.filter { it.sender == MessageSender.USER }.takeLast(2)
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

    fun directConversationFor(characterId: String): String? {
        val existing = repo.state.value.conversationFor(characterId)
        if (existing == null) _notice.value = "새 대화방에서 본체 에이전트를 연결하세요."
        return existing?.id
    }

    // -------------------------------------------------------------------- calls

    fun startCall(conversationId: String, video: Boolean): Boolean {
        val snapshot = repo.state.value
        val conversation = snapshot.conversation(conversationId) ?: return false
        if (video || repo.connection.value != com.dominion.mobile.data.ConnectionState.CONNECTED ||
            snapshot.call != null) return false
        mutate { it.copy(call = CallSession(
            conversationId = conversationId,
            characterId = conversation.characterId,
            video = false
        )) }
        return true
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

    fun endCall() = mutate { it.copy(call = null) }

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
                    body = "${request.handle} · 이제 도미니온을 나눌 수 있어요.",
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

    fun purchase(product: StoreProduct) = mutate { s ->
        val account = when (product.kind) {
            ProductKind.JELLY -> s.account.copy(jelly = s.account.jelly + product.amount)
            ProductKind.ENERGY -> s.account.copy(energy = (s.account.energy + product.amount).coerceAtMost(s.account.energyMax))
            ProductKind.TICKET -> s.account.copy(tickets = s.account.tickets + product.amount)
            ProductKind.PLAN -> s.account.copy(plan = com.dominion.mobile.data.model.Plan.PLUS, jelly = s.account.jelly + 300)
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

    class Factory(private val repo: DominionRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = AppViewModel(repo) as T
    }
}

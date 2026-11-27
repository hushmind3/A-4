package com.dominion.mobile.data.remote

import java.util.UUID

/** Proposed engine extension, disabled unless the authenticated server advertises it.
 * Never infer permission from v1 compatibility/demo endpoints. */
data class MobileCapabilities(val protocol: Int = 0, val accountId: String? = null,
    val features: List<String>? = null) {
    fun requireFeature(feature: String): String {
        check(protocol == 1 && feature in features.orEmpty()) { "Server capability unavailable: $feature" }
        return requireNotNull(accountId).also { require(validMobileId(it)) }
    }
}

fun validMobileId(value: String): Boolean =
    value.isNotBlank() && value.length <= 200 && value.none { it.isISOControl() || it.isWhitespace() }

data class MobilePeer(val id: String? = null, val handle: String? = null,
    val displayName: String? = null, val relationship: String? = null)
data class MobileDirectory(val accountId: String? = null, val peers: List<MobilePeer>? = null) {
    fun verified(owner: String): List<MobilePeer> {
        check(accountId == owner)
        val rows = peers.orEmpty()
        require(rows.size <= 2000 && rows.all { row ->
            row.id?.let(::validMobileId) == true && row.id != owner &&
                row.relationship in setOf("friend", "incoming", "outgoing", "blocked") &&
                !row.displayName.isNullOrBlank() && row.displayName.length <= 200 &&
                (row.handle?.length ?: 0) <= 200
        } && rows.distinctBy { it.id }.size == rows.size)
        return rows
    }
}
data class MobilePeerMessage(val id: String? = null, val from: String? = null,
    val to: String? = null, val text: String? = null, val createdAt: Long = 0,
    val nonce: String? = null)
data class MobilePeerHistory(val accountId: String? = null, val peerId: String? = null,
    val messages: List<MobilePeerMessage>? = null) {
    fun verified(owner: String, peer: String): List<MobilePeerMessage> {
        check(accountId == owner && peerId == peer)
        val rows = messages.orEmpty()
        require(rows.size <= 200 && rows.all { row ->
            row.id?.let(::validMobileId) == true && row.createdAt > 0 &&
                !row.text.isNullOrBlank() && row.text.length <= 32000 &&
                ((row.from == owner && row.to == peer) || (row.from == peer && row.to == owner))
        } && rows.distinctBy { it.id }.size == rows.size)
        return rows.sortedWith(compareBy<MobilePeerMessage> { it.createdAt }.thenBy { it.id })
    }
}

enum class MobileAction(val feature: String, val wire: String, val review: Boolean = false) {
    INVITE("friends", "friend.invite"), ACCEPT("friends", "friend.accept"),
    DECLINE("friends", "friend.decline"), REMOVE("friends", "friend.remove"),
    BLOCK("friends", "friend.block"), MESSAGE("peer_messages", "peer.message"),
    ACCOUNT_DELETE("account_delete", "account.delete", true),
    INSTITUTION("institution_actions", "institution.prepare", true),
    GROUP_CREATE("messenger_v2","messenger.group.create"),
    THREAD_SEND("messenger_v2","messenger.thread.send"),
    THREAD_READ("messenger_v2","messenger.thread.read")
}

data class MobileIntent(val accountId: String, val nonce: String, val action: String,
    val target: String, val text: String? = null, val messenger: MessengerPayload? = null) {
    fun validate() {
        require(validMobileId(accountId) && UUID.fromString(nonce).toString() == nonce &&
            validMobileId(target))
        val kind = MobileAction.entries.single { it.wire == action }
        when (kind) {
            MobileAction.MESSAGE, MobileAction.INSTITUTION ->
                require(!text.isNullOrBlank() && text.length <= 8000)
            MobileAction.GROUP_CREATE, MobileAction.THREAD_SEND, MobileAction.THREAD_READ -> {
                require(text == null)
                requireNotNull(messenger).validate(kind,accountId,target)
            }
            else -> require(text == null)
        }
        if (kind !in setOf(MobileAction.GROUP_CREATE,MobileAction.THREAD_SEND,MobileAction.THREAD_READ))
            require(messenger==null)
        if (kind == MobileAction.ACCOUNT_DELETE) require(target == accountId)
        if (kind in setOf(MobileAction.ACCEPT, MobileAction.DECLINE, MobileAction.REMOVE,
                MobileAction.BLOCK, MobileAction.MESSAGE)) require(target != accountId)
    }
    companion object {
        fun create(owner: String, action: MobileAction, target: String, text: String? = null,
            messenger:MessengerPayload?=null) =
            MobileIntent(owner, UUID.randomUUID().toString(), action.wire, target, text,messenger)
                .also { it.validate() }
    }
}

data class MobileReview(val digest: String? = null, val summary: String? = null,
    val expiresAt: Long = 0, val requiresFreshAuth: Boolean = false)
data class MobileReceipt(val accountId: String? = null, val nonce: String? = null,
    val action: String? = null, val target: String? = null, val status: String? = null,
    val review: MobileReview? = null, val evidenceId: String? = null,
    val evidenceUrl: String? = null, val detail: String? = null) {
    fun verified(intent: MobileIntent): MobileReceipt {
        check(accountId == intent.accountId && nonce == intent.nonce && action == intent.action &&
            target == intent.target && status in setOf("accepted","review","running","completed","failed","cancelled"))
        if (status == "review") {
            val r = requireNotNull(review)
            require(r.digest?.matches(Regex("[a-f0-9]{64}")) == true &&
                !r.summary.isNullOrBlank() && r.summary.length <= 8000 && r.expiresAt > 0)
        }
        if (status == "completed") require(evidenceId?.let(::validMobileId) == true)
        require((detail?.length ?: 0) <= 8000)
        // Optional official references are data, not automatically opened URLs.
        evidenceUrl?.let {
            val uri = java.net.URI(it)
            require(it.length <= 2048 && uri.scheme == "https" && !uri.host.isNullOrBlank() &&
                uri.userInfo == null)
        }
        return this
    }
    fun terminal() = status in setOf("completed","failed","cancelled")
}
data class MobileReviewDecision(val digest: String, val allow: Boolean)
data class MobileJournalEntry(val intent: MobileIntent, val receipt: MobileReceipt? = null,
    val decisionSent: Boolean = false, val decisionAllowed: Boolean? = null) {
    fun merge(next: MobileReceipt): MobileJournalEntry {
        next.verified(intent)
        val old = receipt
        if (old?.terminal() == true) {
            check(old.status == next.status && old.evidenceId == next.evidenceId)
            return this
        }
        if (old?.status == "running") check(next.status !in setOf("accepted","review"))
        val changedReview = next.status == "review" && old?.review?.digest != next.review?.digest
        return copy(receipt = next,
            decisionSent = if (changedReview) false else decisionSent,
            decisionAllowed = if (changedReview) null else decisionAllowed)
    }
}
data class MobileJournal(val entries: List<MobileJournalEntry> = emptyList())

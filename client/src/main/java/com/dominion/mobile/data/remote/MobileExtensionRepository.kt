package com.dominion.mobile.data.remote

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** One journal per engine origin, scoped again by authenticated account.
 * Persist before network writes; ambiguous writes are reconciled by nonce, never replayed. */
internal class MobileExtensionRepository(private val api: DominionApi,
    private val load: suspend () -> MobileJournal,
    private val save: suspend (MobileJournal) -> Unit) {
    private val gate = Mutex()
    private suspend fun owner(feature: String): String = api.mobileCapabilities().requireFeature(feature)
    suspend fun peers(): List<MobilePeer> {
        val account = owner("friends")
        return api.mobilePeers().verified(account)
    }
    suspend fun history(peer: String): List<MobilePeerMessage> {
        require(validMobileId(peer))
        val account = owner("peer_messages")
        require(peer != account)
        return api.mobilePeerHistory(peer).verified(account, peer)
    }
    private fun update(journal: MobileJournal, entry: MobileJournalEntry): MobileJournal =
        journal.copy(entries = listOf(entry) + journal.entries.filterNot { it.intent.nonce == entry.intent.nonce })
    suspend fun journal(): List<MobileJournalEntry> = gate.withLock {
        val capabilities = api.mobileCapabilities()
        val account = capabilities.accountId ?: return@withLock emptyList()
        check(capabilities.protocol == 1 && validMobileId(account))
        load().entries.filter { it.intent.accountId == account }
    }
    suspend fun begin(action: MobileAction, target: String, text: String?): MobileJournalEntry = gate.withLock {
        val account = owner(action.feature)
        val current = load()
        // If a write may have reached the server, do not generate a second nonce.
        check(current.entries.none { it.intent.accountId == account && it.intent.action == action.wire &&
            it.intent.target == target && it.receipt?.terminal() != true }) { "Reconcile existing operation first" }
        // Never discard an unresolved operation to make room for a new one.
        val retained = current.entries.filter { it.receipt?.terminal() != true } +
            current.entries.filter { it.receipt?.terminal() == true }.take(100)
        check(retained.size < 200)
        val entry = MobileJournalEntry(MobileIntent.create(account, action, target, text))
        val reserved = update(MobileJournal(retained), entry)
        save(reserved)
        val accepted = api.createMobileOperation(entry.intent)
        // Review-required actions cannot claim a completed mutation before a decision.
        check(!action.review || accepted.status !in setOf("completed")) { "Unreviewed mutation" }
        val result = entry.merge(accepted)
        save(update(reserved, result))
        result
    }
    suspend fun refresh(nonce: String): MobileJournalEntry = gate.withLock {
        val current = load()
        val entry = current.entries.single { it.intent.nonce == nonce }
        val kind = MobileAction.entries.single { it.wire == entry.intent.action }
        check(owner(kind.feature) == entry.intent.accountId)
        val response = api.mobileOperation(nonce)
        check(!kind.review || entry.decisionSent || response.status != "completed") { "Unreviewed mutation" }
        val next = entry.merge(response)
        save(update(current, next))
        next
    }
    suspend fun decide(nonce: String, digest: String, allow: Boolean): MobileJournalEntry = gate.withLock {
        val current = load()
        val entry = current.entries.single { it.intent.nonce == nonce }
        val kind = MobileAction.entries.single { it.wire == entry.intent.action }
        check(owner(kind.feature) == entry.intent.accountId)
        val live = entry.merge(api.mobileOperation(nonce))
        val receipt = requireNotNull(live.receipt)
        val review = requireNotNull(receipt.review)
        check(receipt.status == "review" && !live.decisionSent &&
            review.digest == digest && review.expiresAt > System.currentTimeMillis())
        // Fresh authentication must occur on the server; local biometric is not a substitute.
        check(!allow || !review.requiresFreshAuth) { "Complete server authentication and refresh review" }
        val reserved = update(current, live.copy(decisionSent = true))
        save(reserved)
        val next = live.copy(decisionSent = true).merge(
            api.decideMobileOperation(nonce, MobileReviewDecision(digest, allow)))
        save(update(reserved, next))
        next
    }
}

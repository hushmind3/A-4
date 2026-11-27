package com.dominion.mobile.data.remote

data class RealtimeCallRequest(val accountId: String, val roomId: String, val sessionId: String,
    val callId: String, val video: Boolean)

data class RealtimeCallGrant(val accountId: String? = null, val roomId: String? = null,
    val sessionId: String? = null, val callId: String? = null, val agentIdentity: String? = null,
    val videoAllowed: Boolean = false, val media: CallTokenResponse? = null) {
    fun verified(request: RealtimeCallRequest): RealtimeCallGrant {
        check(accountId == request.accountId && roomId == request.roomId &&
            sessionId == request.sessionId && callId == request.callId)
        require(agentIdentity?.let(::validMobileId) == true && (!request.video || videoAllowed))
        requireNotNull(media).requireUsable()
        return this
    }
    companion object {
        fun matchesParticipants(agent: String, peers: List<String>, requirePresent: Boolean): Boolean =
            if (peers.isEmpty()) !requirePresent else peers.size == 1 && peers.single() == agent
    }
}

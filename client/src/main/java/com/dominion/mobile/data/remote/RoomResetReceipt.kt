package com.dominion.mobile.data.remote

data class RoomResetReceipt(val ok: Boolean = false, val roomId: String?,
    val previousSessionId: String?, val sessionId: String?)

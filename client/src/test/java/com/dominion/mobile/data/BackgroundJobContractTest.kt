package com.dominion.mobile.data

import com.dominion.mobile.data.remote.EngineApproval
import com.dominion.mobile.data.remote.EngineJob
import com.dominion.mobile.data.remote.belongsToRoom
import com.google.gson.Gson
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Server DTO checks: a global job lookup must never create cross-room alerts. */
class BackgroundJobContractTest {
    private val gson = Gson()

    @Test fun acceptsOnlyTheSubmittedJobInItsOwnRoom() {
        val job = gson.fromJson(
            """{"id":"job-1","kind":"room","status":"completed","sessionId":"session-1",
               "input":{"roomId":"room-1","input":"summarize"}}""",
            EngineJob::class.java
        )
        assertTrue(job.belongsToRoom("room-1", "job-1"))
        assertFalse(job.belongsToRoom("room-2", "job-1"))
        assertFalse(job.belongsToRoom("room-1", "job-2"))
    }

    @Test fun rejectsMalformedSessionAndMissingRoomInput() {
        val noSession = gson.fromJson(
            """{"id":"job-1","status":"completed","input":{"roomId":"room-1"}}""",
            EngineJob::class.java
        )
        assertFalse(noSession.belongsToRoom("room-1", "job-1"))
        val noInput = gson.fromJson(
            """{"id":"job-1","status":"completed","sessionId":"session-1"}""",
            EngineJob::class.java
        )
        assertFalse(noInput.belongsToRoom("room-1", "job-1"))
    }

    @Test fun pendingApprovalRequiresExactSessionAndDigest() {
        val approval = gson.fromJson(
            """{"id":"approval-1","sessionId":"session-1","tool":"filesystem",
               "args":{"path":"notes.txt"},"argsDigest":"digest-1","status":"pending"}""",
            EngineApproval::class.java
        )
        assertTrue(approval.actionableIn("session-1"))
        assertFalse(approval.actionableIn("other-session"))
        val decided = gson.fromJson(
            """{"id":"approval-1","sessionId":"session-1","tool":"filesystem",
               "args":{"path":"notes.txt"},"argsDigest":"digest-1","status":"approved"}""",
            EngineApproval::class.java
        )
        assertFalse(decided.actionableIn("session-1"))
    }
}

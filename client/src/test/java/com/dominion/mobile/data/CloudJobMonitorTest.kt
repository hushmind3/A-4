package com.dominion.mobile.data

import com.dominion.mobile.platform.background.CloudJobMonitor
import org.junit.Assert.*
import org.junit.Test

class CloudJobMonitorTest {
    @Test fun accountScopesCannotCollide(){
        assertEquals(64,CloudJobMonitor.accountHash("google:owner").length)
        assertNotEquals(CloudJobMonitor.accountHash("google:owner"),
            CloudJobMonitor.accountHash("google:other"))
    }
    @Test fun onlyTerminalServerStatesGetNotifications(){
        assertTrue(CloudJobMonitor.isActive("queued"))
        assertTrue(CloudJobMonitor.isActive("running"))
        assertFalse(CloudJobMonitor.isActive("needs_review"))
        assertTrue(CloudJobMonitor.isTerminal("done"))
        assertTrue(CloudJobMonitor.isTerminal("failed"))
        assertTrue(CloudJobMonitor.isTerminal("cancelled"))
        assertFalse(CloudJobMonitor.isTerminal("running"))
    }
    @Test fun fastWatchIsBoundedAndStopsWithoutActiveWork(){
        assertTrue(CloudJobMonitor.shouldContinueFast(0,true))
        assertTrue(CloudJobMonitor.shouldContinueFast(CloudJobMonitor.MAX_FAST_ATTEMPTS-1,true))
        assertFalse(CloudJobMonitor.shouldContinueFast(CloudJobMonitor.MAX_FAST_ATTEMPTS,true))
        assertFalse(CloudJobMonitor.shouldContinueFast(0,false))
    }
    @Test fun notificationRoomRequiresTheSameCloudAccount(){
        val hash=CloudJobMonitor.accountHash("google:owner")
        assertTrue(CloudJobMonitor.matchesAccount("google:owner",hash))
        assertFalse(CloudJobMonitor.matchesAccount("google:other",hash))
        assertFalse(CloudJobMonitor.matchesAccount(null,hash))
        assertFalse(CloudJobMonitor.matchesAccount("google:owner",null))
    }
}

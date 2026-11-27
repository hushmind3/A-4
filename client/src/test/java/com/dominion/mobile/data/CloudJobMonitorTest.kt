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
}

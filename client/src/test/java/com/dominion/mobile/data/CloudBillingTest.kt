package com.dominion.mobile.data

import com.dominion.mobile.data.remote.CloudBilling
import org.junit.Assert.*
import org.junit.Test

class CloudBillingTest {
    @Test fun actualAccountAndMicroKrwAreVerified() {
        val row = CloudBilling("free", 12, 20, account_id = "account-a", credits_micro_krw = 1234567)
        assertEquals("1234.567원", row.verifiedFor("account-a").balanceKrw())
        assertTrue(runCatching { row.verifiedFor("account-b") }.isFailure)
        assertTrue(runCatching { row.copy(free_daily_remaining = 21).verifiedFor("account-a") }.isFailure)
    }
}

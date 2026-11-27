package com.dominion.mobile.data.remote

import org.junit.Assert.*
import org.junit.Test

class MobileExtensionContractTest {
    @Test fun capabilityRequiresExplicitOwnerAndFeature() {
        assertEquals("owner", MobileCapabilities(1,"owner",listOf("friends")).requireFeature("friends"))
        assertTrue(runCatching { MobileCapabilities(1,"owner",emptyList()).requireFeature("friends") }.isFailure)
        assertTrue(runCatching { MobileCapabilities(0,"owner",listOf("friends")).requireFeature("friends") }.isFailure)
    }
    @Test fun receiptsCannotCrossOwnerOrNonceOrInventCompletion() {
        val intent = MobileIntent.create("owner",MobileAction.MESSAGE,"peer","hello")
        val receipt = MobileReceipt("owner",intent.nonce,intent.action,intent.target,"completed",evidenceId="message-1")
        val entry = MobileJournalEntry(intent).merge(receipt)
        assertTrue(entry.receipt!!.terminal())
        assertTrue(runCatching { entry.merge(receipt.copy(accountId="other")) }.isFailure)
        assertTrue(runCatching { entry.merge(receipt.copy(evidenceId=null)) }.isFailure)
        assertTrue(runCatching { entry.merge(receipt.copy(status="running")) }.isFailure)
        assertTrue(runCatching { entry.merge(receipt.copy(nonce="different")) }.isFailure)
    }
    @Test fun peerHistoryRejectsUnrelatedMessages() {
        val row = MobilePeerMessage("id","peer","owner","hello",1)
        val history = MobilePeerHistory("owner","peer",listOf(row))
        assertEquals(listOf(row),history.verified("owner","peer"))
        assertTrue(runCatching { history.copy(messages=listOf(row.copy(to="third"))).verified("owner","peer") }.isFailure)
    }
}

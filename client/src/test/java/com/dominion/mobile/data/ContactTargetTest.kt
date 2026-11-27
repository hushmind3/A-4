package com.dominion.mobile.data
import org.junit.Assert.*
import org.junit.Test

class ContactTargetTest {
    @Test fun phoneNormalizationRejectsControlAndMultipleRecipients() {
        assertEquals("+821012345678", ContactTarget.phone("+82 10-1234-5678"))
        assertTrue(runCatching { ContactTarget.phone("01012345678;01000000000") }.isFailure)
        assertTrue(runCatching { ContactTarget.phone("*123#") }.isFailure)
    }
    @Test fun emailCannotInjectHeaders() {
        assertEquals("user+tag@example.com", ContactTarget.email("user+tag@example.com"))
        assertTrue(runCatching { ContactTarget.email("a@example.com?bcc=b@example.com") }.isFailure)
        assertTrue(runCatching { ContactTarget.email("a@example.com\nBcc:b@example.com") }.isFailure)
    }
}

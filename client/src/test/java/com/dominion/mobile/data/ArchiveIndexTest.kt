package com.dominion.mobile.data

import com.dominion.mobile.data.model.*
import org.junit.Assert.*
import org.junit.Test

class ArchiveIndexTest {
    @Test fun realAttachmentIdsAndMimeTypesArePreserved() {
        val id = "d04bac7c-2803-4bea-b1b2-166f2bf6654b"
        val message = Message("m", "r", MessageSender.USER, attachmentIds = listOf(id, "invalid"),
            attachments = listOf(MessageAttachment(id, "photo.jpg", "image/jpeg", 123)))
        val item = ArchiveIndex.fromMessage(message).single()
        assertEquals(id, item.attachmentId)
        assertEquals("r", item.conversationId)
        assertEquals("m", item.messageId)
        assertEquals("photo.jpg", item.title)
        assertEquals(ArchiveType.IMAGE, item.type)
        assertEquals(1, ArchiveIndex.roomItems(listOf(message, message)).size)
    }
    @Test fun linksAreExplicitAndUnsafeSchemesAreNotIndexed() {
        val message = Message("m", "r", MessageSender.ASSISTANT, text = "https://example.com/path https://example.com/path")
        assertEquals("https://example.com/path", ArchiveIndex.fromMessage(message).single().url)
        assertNull(ArchiveIndex.safeUrl("file:///data/private"))
        assertNull(ArchiveIndex.safeUrl("https://user:pass@example.com"))
    }
}

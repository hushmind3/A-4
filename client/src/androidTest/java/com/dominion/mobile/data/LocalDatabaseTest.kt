package com.dominion.mobile.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dominion.mobile.data.local.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalDatabaseTest {
    private lateinit var db: MobileDatabase
    private lateinit var dao: LocalDao

    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), MobileDatabase::class.java).build()
        dao = db.local()
    }

    @After fun tearDown() { db.close() }

    @Test fun sameMessageIdInDifferentConversationsIsIsolated() {
        dao.putConversations(listOf(ConversationRow("a", 0, 1L, "A", "{}"), ConversationRow("b", 1, 2L, "B", "{}")))
        dao.putMessages(listOf(MessageRow("a", "m", 0, 1L, "한국어 100%_검색", "{}"),
            MessageRow("b", "m", 0, 2L, "different", "{}")))
        assertEquals(2, dao.messages().size)
        assertEquals("a", dao.searchMessages("100%_", 30).single().conversationId)
        assertEquals("a", dao.searchMessages("한국어", 30).single().conversationId)
    }

    @Test fun conversationUpdatePreservesMessagesAndDeleteCascades() {
        val row = ConversationRow("a", 0, 1L, "A", "{}")
        dao.putConversations(listOf(row))
        dao.putMessages(listOf(MessageRow("a", "m", 0, 1L, "hello", "{}")))
        dao.putConversations(listOf(row.copy(title = "Renamed")))
        assertEquals(1, dao.messages().size)
        dao.deleteConversations(listOf(row))
        assertTrue(dao.messages().isEmpty())
    }

    @Test fun recordsAreIndependentAndTransactionRollsBack() {
        dao.putRecords(listOf(RecordRow("task", "same", 0, "{}"), RecordRow("memory", "same", 0, "{}")))
        assertEquals(2, dao.records().size)
        try {
            db.runInTransaction {
                dao.clearRecords()
                error("rollback")
            }
        } catch (_: IllegalStateException) {}
        assertEquals(2, dao.records().size)
    }
}

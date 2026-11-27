package com.dominion.mobile.data

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import android.content.Context
import com.dominion.mobile.data.local.LocalStore
import com.dominion.mobile.data.model.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class PrivateRoomDatabaseTest {
    @Test fun messagesAndMemoriesAreEncryptedAndSearchableAfterReopen()=runBlocking{
        val context=ApplicationProvider.getApplicationContext<Context>()
        val scope="private-room-"+UUID.randomUUID()
        val room="room-"+UUID.randomUUID()
        val sensitive="secret-never-appear-in-sqlite-"+UUID.randomUUID()
        val message=Message("message-1",room,MessageSender.USER,sensitive)
        val conversation=Conversation(room,"persona",messages=listOf(message),titleOverride="private")
        val memory=MemoryItem("memory-1","persona",sensitive)
        val first=LocalStore(context,scope)
        first.saveStateNow(AppState(conversations=listOf(conversation),memories=listOf(memory)))
        first.flush()
        val raw=context.getDatabasePath("dominion_mobile_"+scope+".db").readBytes()
        assertFalse(String(raw,Charsets.ISO_8859_1).contains(sensitive))
        val reopened=LocalStore(context,scope)
        val restored=requireNotNull(reopened.loadState())
        assertEquals(sensitive,restored.conversation(room)?.messages?.single()?.text)
        assertEquals(sensitive,restored.memories.single().text)
        assertEquals(1,reopened.searchMessages(sensitive.take(18)).size)
        assertFalse(String(context.getDatabasePath("dominion_mobile_"+scope+".db")
            .readBytes(),Charsets.ISO_8859_1).contains(sensitive))
    }
}

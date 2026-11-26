package com.dominion.mobile.data

import com.dominion.mobile.data.model.ArchiveType
import com.dominion.mobile.data.model.CardKind
import com.dominion.mobile.data.model.ConversationKind
import com.dominion.mobile.data.model.MessageKind
import com.dominion.mobile.data.model.VaultType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UiFixtureDataTest {

    @Test
    fun fixtureStateCoversEveryMajorSurface() {
        val state = UiFixtureData.initial()
        assertTrue(state.characters.isNotEmpty())
        assertTrue(state.conversations.isNotEmpty())
        assertTrue(state.tasks.isNotEmpty())
        assertTrue(state.schedules.isNotEmpty())
        assertTrue(state.memories.isNotEmpty())
        assertTrue(state.friends.isNotEmpty())
        assertTrue(state.connections.isNotEmpty())
        assertTrue(state.vault.isNotEmpty())
        assertTrue(state.archive.isNotEmpty())
        assertTrue(state.explore.isNotEmpty())
        assertTrue(state.products.isNotEmpty())
    }

    @Test
    fun conversationsContainTheCardKindsTheChatUiCanRender() {
        val messages = UiFixtureData.conversations().flatMap { it.messages }
        assertTrue(messages.any { it.kind == MessageKind.TASK })
        assertTrue(messages.any { it.kind == MessageKind.CALL })
        assertTrue(messages.any { it.kind == MessageKind.FILE })
        assertTrue(messages.any { it.kind == MessageKind.LINK })
        val cards = messages.mapNotNull { it.card }.map { it.kind }.toSet()
        assertTrue(CardKind.TASK in cards)
        assertTrue(CardKind.CALL in cards)
        assertTrue(CardKind.FILE in cards)
        assertTrue(CardKind.LINK in cards)
    }

    @Test
    fun extraTabsAndVaultCategoriesArePresent() {
        val state = UiFixtureData.initial()
        assertTrue(state.conversations.any { it.kind == ConversationKind.EXTRA })
        assertTrue(state.vault.any { it.type == VaultType.LOGIN })
        assertTrue(state.vault.any { it.type == VaultType.CARD })
        assertTrue(state.vault.any { it.type == VaultType.ADDRESS })
        assertTrue(state.vault.any { it.type == VaultType.PHONE })
        assertTrue(state.archive.any { it.type == ArchiveType.IMAGE })
        assertTrue(state.archive.any { it.type == ArchiveType.FILE })
        assertTrue(state.archive.any { it.type == ArchiveType.LINK })
    }

    @Test
    fun taskProgressIsDerivedFromSteps() {
        val task = UiFixtureData.tasks().first()
        assertEquals(2f / 3f, task.progress, 0.001f)
    }
}

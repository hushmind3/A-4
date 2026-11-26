package com.dominion.mobile.data

import com.dominion.mobile.data.model.AppState
import com.dominion.mobile.data.model.Account
import com.dominion.mobile.data.model.VaultItem
import com.dominion.mobile.data.model.VaultType
import com.dominion.mobile.platform.export.LocalDataExport
import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test

class LocalDataExportTest {
    @Test fun localExportDoesNotContainVaultOrRuntimeSecrets() {
        val state = AppState(account = Account(name = "테스트"),
            vault = listOf(VaultItem("vault-id", VaultType.LOGIN, "private title", secret = "not-for-export")),
            sharedText = "ephemeral-private-text")
        val json = LocalDataExport.json(state)
        val root = JsonParser.parseString(json).asJsonObject
        assertEquals("dominion-mobile-local-export-v1", root.get("format").asString)
        assertEquals("테스트", root.getAsJsonObject("account").get("name").asString)
        assertTrue(root.has("conversations"))
        assertTrue(root.has("tasks"))
        assertFalse(root.has("vault"))
        assertFalse(root.has("sharedText"))
        assertFalse(json.contains("not-for-export"))
        assertFalse(json.contains("ephemeral-private-text"))
    }
}

package com.dominion.mobile.data

import com.dominion.mobile.data.remote.EngineSkillState
import org.junit.Assert.*
import org.junit.Test

class EngineSkillStateTest {
    @Test fun importedDraftIsNotActive() {
        val item = EngineSkillState("1", "korea-weather", true, "draft")
        assertFalse(item.isActive())
        assertFalse(item.copy(enabled = false, status = "active").isActive())
        assertTrue(item.copy(status = "active").isActive())
    }
}

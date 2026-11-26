package com.dominion.mobile.data

import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class AgendaHandoffTest {
    @Test fun selectedOnlyAndNoRecordIdentifiers() {
        val rows = listOf(AgendaEntry("secret-id","선택 일정",0,3600000,false),
            AgendaEntry("other-id","미선택 비밀 일정",0,3600000,false))
        val prompt = AgendaHandoff.request(rows, setOf("secret-id"), "준비물 확인", ZoneId.of("Asia/Seoul"))
        assertTrue(prompt.contains("선택 일정"))
        assertTrue(prompt.contains("준비물 확인"))
        assertFalse(prompt.contains("미선택 비밀 일정"))
        assertFalse(prompt.contains("secret-id"))
    }
}

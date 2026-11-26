package com.dominion.mobile.data

import org.junit.Assert.*
import org.junit.Test

class KoreanServicesTest {
    @Test fun officialSourcesAndTaskInputsAreExplicit() {
        assertEquals(KoreanServices.entries.size, KoreanServices.entries.map { it.id }.distinct().size)
        KoreanServices.entries.forEach { service ->
            assertTrue(KoreanServices.officialUrl(service).startsWith("https://"))
            val request = service.request("서울", "신청 준비물 확인")
            assertTrue(request.contains(service.url))
            assertTrue(request.contains("서울"))
            assertTrue(request.contains("신청 준비물 확인"))
            assertTrue(request.contains("승인"))
        }
    }
    @Test fun catalogRejectsSubstitutedUrl() {
        assertTrue(runCatching { KoreanServices.officialUrl(KoreanServices.entries.first().copy(url = "https://example.com")) }.isFailure)
    }
}

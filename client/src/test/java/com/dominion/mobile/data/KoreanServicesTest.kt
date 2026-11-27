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
    @Test fun publicPrivateServiceAdaptersUseRealContracts() {
        val query=com.dominion.mobile.platform.network.KoreanLookup("danawa","무선 청소기")
        val source=com.dominion.mobile.platform.network.KoreanPublicTools
        assertTrue(source.endpoint(query).startsWith("https://search.danawa.com/dsearch.php?query="))
        val html="""<li class="prod_item" id="productItem123"><p class="prod_name"><a href="/info/?pcode=123">청소기</a></p><input id="min_price_123" value="199000"></li>"""
        val result=source.extract(query,html)
        assertTrue(result.contains("199000원"))
        assertTrue(result.contains("https://prod.danawa.com/info/?pcode=123"))
        val news=com.dominion.mobile.platform.network.KoreanLookup("naver-news","공개 뉴스")
        val data="""{"items":[{"title":"기사","pub_date_iso":"2026-11-27T01:00:00Z","original_link":"https://example.com/news"}]}"""
        assertTrue(source.extract(news,data).contains("2026-11-27T01:00:00Z"))
        assertTrue(runCatching{query.copy(query="010-1234-5678").checked()}.isFailure)
        assertTrue(runCatching{source.extract(news,"{\"error\":\"provider failed\"}")}.isFailure)
    }
}

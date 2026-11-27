package com.dominion.mobile.data

import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal

class MiniAppDocumentTest {
    private val json = """{"schema":"dominion-miniapp/v1","title":"예산","blocks":[{"id":"b","kind":"budget","title":"합계","items":["교통","식사"]},{"id":"c","kind":"checklist","title":"준비","items":["표"]}]}"""
    @Test fun nativeGrammarAndExactIntegerCalculation() {
        val doc = MiniAppDocument.parse(json)
        val values = mapOf("b:0" to "5000","b:1" to "12000","c:0" to "true")
        assertEquals(BigDecimal("17000"), doc.total(doc.blocks.first(),values))
        assertTrue(doc.result(values).contains("17000"))
        assertNotEquals(doc.storageKey("r","s","m"),doc.storageKey("r","other","m"))
        assertEquals(doc,MiniAppDocument.fromMessage(json))
    }
    @Test fun rejectsDynamicCodeAndForeignValues() {
        assertNull(MiniAppDocument.parseOrNull(json.replace("\"budget\"","\"script\"")))
        assertNull(MiniAppDocument.parseOrNull(json.replace("\"title\":\"예산\"","\"title\":\"예산\",\"script\":\"alert(1)\"")))
        val doc = MiniAppDocument.parse(json)
        assertTrue(runCatching { doc.validateValues(mapOf("foreign:0" to "1")) }.isFailure)
        assertTrue(runCatching { doc.validateValues(mapOf("b:0" to "NaN")) }.isFailure)
        assertTrue(runCatching { doc.validateValues(mapOf("c:0" to "1")) }.isFailure)
    }
}

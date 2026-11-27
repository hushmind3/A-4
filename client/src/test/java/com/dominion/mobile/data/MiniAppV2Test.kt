package com.dominion.mobile.data
import org.junit.Assert.*
import org.junit.Test

class MiniAppV2Test {
    private val json="""{"schema":"dominion-miniapp/v2","title":"견적","blocks":[{"id":"input","kind":"number","title":"입력","items":["수량","가격"]},{"id":"cost","kind":"metric","title":"금액","operation":"multiply","operands":["input:0","input:1"],"unit":"원"},{"id":"tax","kind":"metric","title":"세금","operation":"multiply","operands":["cost","0.1"]},{"id":"choice","kind":"choice","title":"선택","items":["A","B"]},{"id":"table","kind":"editable_table","title":"기록","columns":["메모"],"rows":[[""]]}]}"""
    @Test fun calculatesChainedMetricsAndValidatesInputs() {
        val doc=MiniAppDocument.parse(json)
        val values=mapOf("input:0" to "2.5","input:1" to "1000","choice:0" to "B","table:0:0" to "기록")
        assertEquals("2500",doc.calculated(values)["cost"]?.toPlainString())
        assertEquals("250",doc.calculated(values)["tax"]?.toPlainString())
        assertEquals(values,doc.validateValues(values))
        assertTrue(doc.result(values).contains("2500"))
        assertNull(doc.calculated(emptyMap())["tax"])
        assertTrue(runCatching { doc.validateValues(mapOf("choice:0" to "C")) }.isFailure)
    }
    @Test fun rejectsCyclesAndUnknownReferences() {
        assertNull(MiniAppDocument.parseOrNull(json.replace("\"input:0\"","\"tax\"")))
        assertNull(MiniAppDocument.parseOrNull(json.replace("\"cost\",\"0.1\"","\"missing\",\"0.1\"")))
        assertNull(MiniAppDocument.parseOrNull(json.replace("dominion-miniapp/v2","dominion-miniapp/v1")))
    }
}

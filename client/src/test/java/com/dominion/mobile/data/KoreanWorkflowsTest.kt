package com.dominion.mobile.data
import org.junit.Assert.*
import org.junit.Test
class KoreanWorkflowsTest {
    @Test fun allServicesHaveWorkflowAndRequiredFieldsAreValidated() {
        KoreanServices.entries.forEach { service ->
            val workflows = KoreanWorkflows.forService(service.id)
            assertTrue(workflows.isNotEmpty())
            assertTrue(workflows.all { it.fields.isNotEmpty() })
        }
        val route = KoreanWorkflows.forService("transit").first()
        assertTrue(runCatching { KoreanWorkflows.compose(route, emptyMap(), "") }.isFailure)
        val values = route.fields.associate { it.label to it.example } + ("unknown" to "private")
        val result = KoreanWorkflows.compose(route,values,"")
        assertTrue(result.contains("출발지"))
        assertFalse(result.contains("private"))
    }
    @Test fun trackingIsNotAutomaticallyRecurring() {
        assertFalse(KoreanWorkflows.forService("delivery").first().repeatable)
    }
}

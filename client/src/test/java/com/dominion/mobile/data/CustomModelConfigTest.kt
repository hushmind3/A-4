package com.dominion.mobile.data

import com.dominion.mobile.data.remote.CustomModelConfig
import org.junit.Assert.*
import org.junit.Test

class CustomModelConfigTest{
    @Test fun acceptsHttpsAndNormalizesBase(){
        assertEquals("https://example.com/v1",CustomModelConfig("https://example.com/v1/","model","token").checked().baseUrl)
    }
    @Test fun rejectsUnsafeEndpointAndCredentials(){
        listOf("http://example.com/v1","https://user:secret@example.com/v1","https://example.com/v1?token=x","https://example.com/v1#x").forEach{
            assertTrue(runCatching{CustomModelConfig(it,"model").checked()}.isFailure)
        }
        assertTrue(runCatching{CustomModelConfig("https://example.com/v1","model","a\nb").checked()}.isFailure)
        assertTrue(runCatching{CustomModelConfig("https://example.com/v1",CustomModelConfig.SELECTION).checked()}.isFailure)
    }
}

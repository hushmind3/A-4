package com.dominion.mobile.data.remote

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

class CloudTaskFallbackTest {
    private fun failure(code:Int,message:String)=HttpException(
        Response.error<Any>(code,message.toResponseBody("application/json".toMediaType()))
    )
    @Test fun aMissingServerEndpointKeepsTheExistingMobileHarnessAvailable(){
        assertTrue(cloudJobFeatureUnavailable(failure(404,"Not found")))
        assertTrue(cloudJobFeatureUnavailable(failure(503,"Cloud jobs not configured")))
    }
    @Test fun doNotHideAuthenticationQuotaOrRealServerFailures(){
        assertFalse(cloudJobFeatureUnavailable(failure(401,"Unauthorized")))
        assertFalse(cloudJobFeatureUnavailable(failure(403,"Revoked")))
        assertFalse(cloudJobFeatureUnavailable(failure(429,"Quota exhausted")))
        assertFalse(cloudJobFeatureUnavailable(failure(502,"Model unavailable")))
        assertFalse(cloudJobFeatureUnavailable(failure(503,"Inference temporarily unavailable")))
    }
}

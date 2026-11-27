package com.dominion.mobile.data
import com.dominion.mobile.data.remote.*
import org.junit.Assert.*
import org.junit.Test
class CloudInferenceTest {
 @Test fun responseMustBeRealAndMatchRequestedModel(){
  val answer=CloudCompletionResponse("id","@cf/model",listOf(CloudCompletionChoice(CloudChatMessage("assistant","ok"))))
  assertEquals("ok",answer.verified("@cf/model").content)
  assertEquals("@cf/model",answer.verified("dominion/free-auto").model)
  assertTrue(runCatching{answer.verified("other")}.isFailure)
  assertTrue(runCatching{answer.copy(choices=emptyList()).verified("@cf/model")}.isFailure)
 }
 @Test fun providerUsesOnlyAppTokenAndFixedGateway(){
  val id=CloudModelProtocol.providerId("pair","@cf/model")
  val token=CloudPairingProtocol.token()
  val payload=CloudModelProtocol.providerBody(id,"@cf/model",token)
  assertEquals(CLOUD_ORIGIN+"/v1",payload.get("baseUrl").asString)
  assertEquals("compat",payload.get("toolMode").asString)
  assertTrue(runCatching{CloudModelProtocol.providerBody(id,"@cf/model","owner-token")}.isFailure)
 }
}

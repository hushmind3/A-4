package com.dominion.mobile.data.remote

import com.google.gson.JsonObject
import java.security.MessageDigest

data class CloudChatMessage(val role:String,val content:String)
data class CloudCompletionRequest(val model:String,val messages:List<CloudChatMessage>,
    val max_tokens:Int=512,val stream:Boolean=false) {
    fun validated():CloudCompletionRequest {
        require(model.matches(Regex("[A-Za-z0-9@._:/-]{1,160}")) && max_tokens in 1..1024 && !stream)
        require(messages.size in 1..12 && messages.last().role=="user" &&
            messages.all{it.role in setOf("user","assistant") && it.content.isNotBlank() && it.content.length<=16000} &&
            messages.sumOf{it.content.toByteArray(Charsets.UTF_8).size}<=24000)
        return this
    }
}
data class CloudCompletionChoice(val message:CloudChatMessage?=null)
data class CloudCompletionResponse(val id:String?=null,val model:String?=null,val choices:List<CloudCompletionChoice>?=null) {
    fun verified(requested:String):CloudCompletion {
        val answer=requireNotNull(choices?.singleOrNull()?.message)
        require(!id.isNullOrBlank() && answer.role=="assistant" && answer.content.isNotBlank() && answer.content.length<=16000)
        val actual=requireNotNull(model)
        require(actual.matches(Regex("[A-Za-z0-9@._:/-]{1,160}")) &&
            (actual==requested || requested=="dominion/free-auto"))
        return CloudCompletion(actual,answer.content)
    }
}
data class CloudCompletion(val model:String,val content:String)
object CloudModelProtocol {
    fun append(history:List<CloudChatMessage>,text:String,role:String="user"):List<CloudChatMessage> {
        require(role in setOf("user","assistant") && text.isNotBlank())
        val result=(history+CloudChatMessage(role,text.take(16000))).takeLast(12).toMutableList()
        while(result.size>1 && result.sumOf{it.content.toByteArray(Charsets.UTF_8).size}>24000)result.removeAt(0)
        while(result.size>1 && result.first().role!="user")result.removeAt(0)
        return result
    }
    fun providerId(pair:String,model:String):String="mobile-cloud-"+MessageDigest.getInstance("SHA-256")
        .digest((pair+"\n"+model).toByteArray()).joinToString(""){"%02x".format(it.toInt() and 255)}.take(32)
    fun providerBody(id:String,model:String,token:String):JsonObject {
        require(id.matches(Regex("mobile-cloud-[a-f0-9]{32}")) && CloudPairingProtocol.validToken(token) &&
            model.matches(Regex("[A-Za-z0-9@._:/-]{1,160}")))
        return JsonObject().apply{
            addProperty("id",id);addProperty("name","DOMINION Cloud · "+model)
            addProperty("baseUrl","$CLOUD_ORIGIN/v1");addProperty("kind","openai")
            addProperty("apiKey",token);addProperty("model",model);addProperty("enabled",true)
            addProperty("local",false);addProperty("toolMode","compat");addProperty("integration","dominion-mobile-cloud")
        }
    }
}

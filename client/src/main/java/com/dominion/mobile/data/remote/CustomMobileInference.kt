package com.dominion.mobile.data.remote

import android.content.Context
import com.dominion.mobile.data.local.EncryptedRecordStore
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import java.security.MessageDigest

/** User-selected OpenAI-compatible endpoint. Credentials never enter the model prompt,
 * the cloud account token, or AppState. No fallback to another provider on error. */
data class CustomModelConfig(val baseUrl:String,val model:String,val apiKey:String=""){
    fun checked():CustomModelConfig{
        val url=baseUrl.trim().toHttpUrl()
        require(url.isHttps&&url.username.isEmpty()&&url.password.isEmpty()&&
            url.query==null&&url.fragment==null)
        require(model.trim().matches(Regex("[A-Za-z0-9@._:/-]{1,160}"))&&model.trim()!=SELECTION)
        require(apiKey.length<=4096&&apiKey.none{it.isISOControl()||it.isWhitespace()})
        return copy(baseUrl=url.toString().trimEnd('/'),model=model.trim())
    }
    companion object{const val SELECTION="mobile/custom-api"}
}

internal class CustomMobileInference(private val context:Context){
    private val gson=Gson()
    private val http=OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
        .retryOnConnectionFailure(false).connectTimeout(15,TimeUnit.SECONDS)
        .readTimeout(130,TimeUnit.SECONDS).callTimeout(140,TimeUnit.SECONDS).build()
    private fun store(account:String):EncryptedRecordStore<CustomModelConfig>{
        val id=MessageDigest.getInstance("SHA-256").digest(account.toByteArray(Charsets.UTF_8))
            .joinToString(""){"%02x".format(it.toInt() and 255)}.take(24)
        return EncryptedRecordStore(context,"dominion_custom_inference_"+id,CustomModelConfig::class.java)
    }
    fun load(account:String)=store(account).load()?.checked()
    fun save(account:String,value:CustomModelConfig)=store(account).save(value.checked())
    suspend fun complete(config:CustomModelConfig,messages:List<CloudChatMessage>,maxTokens:Int):CloudCompletion=
        withContext(Dispatchers.IO){
            val cfg=config.checked()
            val payload=CloudCompletionRequest(cfg.model,messages,maxTokens).validated()
            val request=Request.Builder().url(cfg.baseUrl+"/chat/completions")
                .apply{if(cfg.apiKey.isNotEmpty())header("Authorization","Bearer "+cfg.apiKey)}
                .post(gson.toJson(payload).toRequestBody("application/json".toMediaType())).build()
            http.newCall(request).execute().use{response->
                check(response.isSuccessful){"Custom model HTTP "+response.code}
                val body=requireNotNull(response.body)
                require(body.contentLength() in -1..262144)
                val source=body.source()
                source.request(262145)
                require(source.buffer.size<=262144)
                val result=gson.fromJson(source.readUtf8(),CloudCompletionResponse::class.java)
                // Compatible servers may return their canonical model alias.
                require(result.model?.matches(Regex("[A-Za-z0-9@._:/-]{1,160}"))==true)
                val answer=requireNotNull(result.choices?.singleOrNull()?.message)
                require(!result.id.isNullOrBlank()&&answer.role=="assistant"&&answer.content.isNotBlank()&&answer.content.length<=16000)
                CloudCompletion(requireNotNull(result.model),answer.content)
            }
        }
}

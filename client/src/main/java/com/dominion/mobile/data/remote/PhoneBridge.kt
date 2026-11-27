package com.dominion.mobile.data.remote

import android.content.Context
import com.dominion.mobile.data.local.EncryptedRecordStore
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.util.concurrent.TimeUnit

internal data class PhoneBridgeConfig(val origin:String,val token:String){
    fun checked():PhoneBridgeConfig{
        val url=origin.trim().toHttpUrl()
        require(url.isHttps&&url.username.isEmpty()&&url.password.isEmpty()&&
            url.encodedPath=="/"&&url.query==null&&url.fragment==null)
        require(token.length in 40..256&&token.none{it.isISOControl()||it.isWhitespace()})
        return copy(origin=url.toString().removeSuffix("/"))
    }
}
internal data class PhoneBridgeCall(val id:String,val number:String,val goal:String,val approved:Boolean=true)
internal data class PhoneBridgeHealth(val status:String="",val approved_numbers:Int=0) {
    fun checked():PhoneBridgeHealth {
        require(status=="ok"&&approved_numbers>=0)
        return this
    }
}
internal data class PhoneBridgeState(val config:PhoneBridgeConfig,val pending:PhoneBridgeCall?=null)
internal data class PhoneBridgeReceipt(val id:String,val status:String,val answered:Int=0,val consent:Int=0,
    val summary:String?=null,val evidence:String?=null,val reason:String?=null){
    val terminal get()=status in setOf("reported","ended","failed","cancelled","unknown","expired")
}
internal class PhoneBridge(private val context:Context,account:String){
    private val accountKey=com.dominion.mobile.data.VisualGesture.digest(account.toByteArray(Charsets.UTF_8)).take(24)
    private val store=EncryptedRecordStore(context,"dominion_phone_bridge_"+accountKey,PhoneBridgeState::class.java)
    companion object{private val journalLock=Any()}
    private fun journal(id:String):EncryptedRecordStore<PhoneBridgeState>{
        require(java.util.UUID.fromString(id).toString()==id)
        return EncryptedRecordStore(context,"dominion_phone_call_"+accountKey+"_"+id,PhoneBridgeState::class.java)
    }
    private val gson=Gson()
    private val http=OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
        .connectTimeout(8,TimeUnit.SECONDS).readTimeout(25,TimeUnit.SECONDS).callTimeout(30,TimeUnit.SECONDS)
        .retryOnConnectionFailure(false).build()
    fun load()=synchronized(journalLock){store.load()}
    fun save(value:PhoneBridgeState)=synchronized(journalLock){
        store.save(value.copy(config=value.config.checked()))
    }
    fun saveConfig(config:PhoneBridgeConfig)=synchronized(journalLock){
        require(store.load()?.pending==null){"기존 전화 업무를 먼저 확인하세요"}
        store.save(PhoneBridgeState(config.checked()))
    }
    fun reserve(call:PhoneBridgeCall,config:PhoneBridgeConfig?=null):PhoneBridgeConfig=synchronized(journalLock){
        val saved=store.load()
        require(saved?.pending==null){"기존 전화 업무를 먼저 확인하세요"}
        val cfg=(config?:requireNotNull(saved?.config){"전화 대행 서버를 먼저 설정하세요"}).checked()
        require(journal(call.id).load()==null){"동일 전화 업무 재발신 금지"}
        val entry=PhoneBridgeState(cfg,call)
        journal(call.id).save(entry)
        store.save(entry)
        cfg
    }
    fun reserved(id:String):PhoneBridgeState?=synchronized(journalLock){
        journal(id).load()?.also{check(it.pending?.id==id)}
    }
    fun clearPending(id:String)=synchronized(journalLock){
        val current=requireNotNull(store.load())
        check(current.pending?.id==id)
        store.save(current.copy(pending=null))
    }
    suspend fun health(config:PhoneBridgeConfig):PhoneBridgeHealth=withContext(Dispatchers.IO){
        val checked=config.checked()
        val request=Request.Builder().url(checked.origin+"/v1/health")
            .header("Authorization","Bearer "+checked.token).get().build()
        http.newCall(request).execute().use{response->
            check(response.isSuccessful){"전화 어댑터 응답 "+response.code}
            val body=requireNotNull(response.body)
            check(body.contentLength() in -1..2048)
            val source=body.source()
            source.request(2049)
            check(source.buffer.size<=2048)
            gson.fromJson(source.readUtf8(),PhoneBridgeHealth::class.java).checked()
        }
    }
    suspend fun request(config:PhoneBridgeConfig,call:PhoneBridgeCall,method:String):PhoneBridgeReceipt?=withContext(Dispatchers.IO){
        val checked=config.checked()
        require(call.id.matches(Regex("[a-f0-9-]{36}"))&&call.number.matches(Regex("\\+82[1-9][0-9]{7,10}"))&&call.goal.length in 3..800)
        val endpoint=checked.origin+"/v1/calls"+when(method){"start"->"";"cancel"->"/"+call.id+"/cancel";"status"->"/"+call.id;else->error("Invalid action")}
        val request=Request.Builder().url(endpoint).header("Authorization","Bearer "+checked.token).apply{
            if(method!="status")post((if(method=="start")gson.toJson(call) else "{}").toRequestBody("application/json".toMediaType()))
        }.build()
        http.newCall(request).execute().use{response->
            if(method=="status"&&response.code==404)return@withContext null
            check(response.isSuccessful){"전화 서버 응답 "+response.code}
            val body=requireNotNull(response.body)
            check(body.contentLength() in -1..32768)
            val source=body.source()
            source.request(32769)
            check(source.buffer.size<=32768)
            val receipt=gson.fromJson(source.readUtf8(),PhoneBridgeReceipt::class.java)
            check(receipt.id==call.id&&receipt.status in setOf("queued","dialing","connected","cancel_requested",
                "reported","ended","failed","cancelled","unknown","expired"))
            receipt
        }
    }
}

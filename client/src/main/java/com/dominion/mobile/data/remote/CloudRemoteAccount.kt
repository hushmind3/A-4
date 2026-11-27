package com.dominion.mobile.data.remote

import android.content.Context
import com.dominion.mobile.data.local.EncryptedRecordStore
import com.dominion.mobile.data.local.EngineConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*

data class RemotePc(val id:String,val name:String,val online:Boolean,val last_seen:Long=0) {
    val native:Boolean get()=name.startsWith("DOMINION 4317 · ") &&
        runCatching{java.util.UUID.fromString(id).toString()==id}.getOrDefault(false)
}
data class RemoteNotice(val id:String,val deviceId:String,val text:String,val time:Long)
data class RemotePcState(val status:String="disconnected",val approvalUrl:String?=null,
    val devices:List<RemotePc> = emptyList(),val inbox:List<RemoteNotice> = emptyList(),val nativePushReady:Boolean=false)
private data class PcPairRequest(val app_name:String,val token_hash:String,val capability:String="assistant_remote")
private data class PcPairResponse(val id:String,val expires_in:Int,val status:String,val verification_uri:String)
private data class PcIdentity(val account_id:String,val scope:String,val app_session_id:String,val capability:String?)
private data class PcList(val devices:List<RemotePc>)
private data class PcInbox(val messages:List<RemoteNotice>)
private data class PcPushStatus(val nativeReady:Boolean)
private data class PcPair(val id:String,val token:String,val url:String,val account:String)
private interface PcCloudApi {
    @POST("v1/apps/start") suspend fun start(@Body body:PcPairRequest):PcPairResponse
    @GET("v1/apps/status") suspend fun status(@Header("Authorization") auth:String):CloudStatus
    @GET("v1/me") suspend fun me(@Header("Authorization") auth:String):PcIdentity
    @GET("v1/assistant/devices") suspend fun devices(@Header("Authorization") auth:String):PcList
    @GET("v1/assistant/messages") suspend fun inbox(@Header("Authorization") auth:String):PcInbox
    @GET("v1/assistant/push/status") suspend fun pushStatus(@Header("Authorization") auth:String):PcPushStatus
    @POST("v1/apps/disconnect") suspend fun disconnect(@Header("Authorization") auth:String,@Body body:Map<String,String>):com.google.gson.JsonObject
    @POST("v1/assistant/push/register") suspend fun registerPush(@Header("Authorization") auth:String,@Body body:Map<String,String>):com.google.gson.JsonObject
    @POST("v1/assistant/push/unregister") suspend fun unregisterPush(@Header("Authorization") auth:String,@Body body:Map<String,String>):com.google.gson.JsonObject
}
class CloudRemoteAccount(context:Context) {
    private val store=EncryptedRecordStore(context.applicationContext,"dominion_cloud_remote",PcPair::class.java)
    private val lock=Mutex()
    private val api=Retrofit.Builder().baseUrl(CLOUD_ORIGIN+"/")
        .client(OkHttpClient.Builder().followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false).build())
        .addConverterFactory(GsonConverterFactory.create()).build().create(PcCloudApi::class.java)
    private fun pair():PcPair?=store.load()?.also{
        require(CloudPairingProtocol.validToken(it.token)&&CloudPairingProtocol.approvalUrl(it.id,it.url)==it.url)
    }
    private suspend fun verified(p:PcPair,account:String):String{
        require(p.account==account)
        val auth="Bearer "+p.token
        val status=api.status(auth)
        check(status.id==p.id&&status.status=="approved"&&status.expires_at>System.currentTimeMillis()/1000)
        val id=api.me(auth)
        check(id.account_id==account&&id.app_session_id==p.id&&id.scope=="free_inference"&&id.capability=="assistant_remote")
        return auth
    }
    suspend fun begin(account:String):RemotePcState=withContext(Dispatchers.IO){lock.withLock{
        require(account.isNotBlank()&&account.length<=256)
        check(pair()==null){"Disconnect existing remote grant first"}
        val token=CloudPairingProtocol.token()
        val r=api.start(PcPairRequest("DOMINION A-4 PC Remote",CloudPairingProtocol.hash(token)))
        check(r.status=="pending"&&r.expires_in in 1..600)
        val url=CloudPairingProtocol.approvalUrl(r.id,r.verification_uri)
        store.save(PcPair(r.id,token,url,account))
        RemotePcState("pending",url)
    }}
    suspend fun refresh(account:String):RemotePcState=withContext(Dispatchers.IO){lock.withLock{
        val p=pair()?:return@withLock RemotePcState()
        if(p.account!=account)return@withLock RemotePcState("account_mismatch")
        val status=try{api.status("Bearer "+p.token)}catch(e:retrofit2.HttpException){
            if(e.code()==404){store.clear();return@withLock RemotePcState("expired")}
            throw e
        }
        check(status.id==p.id)
        when(status.status){
            "pending"->RemotePcState("pending",p.url)
            "expired","revoked"->{store.clear();RemotePcState(status.status)}
            "approved"->{
                val auth=verified(p,account)
                val devices=api.devices(auth).devices.filter{it.native}.distinctBy{it.id}
                val inbox=api.inbox(auth).messages.takeLast(100)
                val push=api.pushStatus(auth).nativeReady
                RemotePcState("approved",devices=devices,inbox=inbox,nativePushReady=push)
            }
            else->error("Unknown remote state")
        }
    }}
    suspend fun connection(account:String,device:String):EngineConnection=withContext(Dispatchers.IO){lock.withLock{
        val p=requireNotNull(pair());val auth=verified(p,account)
        check(api.devices(auth).devices.any{it.id==device&&it.native&&it.online})
        val c=EngineConnection.relay(device,account,p.token)
        val ready=probeEngine(ApiFactory.create(c))
        check(ready.authenticated==ProbeState.OK&&ready.rooms==ProbeState.OK)
        verified(p,account)
        c
    }}
    suspend fun setPush(account:String,token:String,enabled:Boolean)=withContext(Dispatchers.IO){lock.withLock{
        require(token.matches(Regex("fcm:[A-Za-z0-9_:.~\\-]{40,400}")))
        val auth=verified(requireNotNull(pair()),account)
        val response=if(enabled){check(api.pushStatus(auth).nativeReady);api.registerPush(auth,mapOf("token" to token))}
            else api.unregisterPush(auth,mapOf("token" to token))
        check(response.get("ok")?.asBoolean==true)
    }}
    suspend fun disconnect()=withContext(Dispatchers.IO){lock.withLock{
        val p=pair()?:return@withLock
        try{check(api.disconnect("Bearer "+p.token,emptyMap()).get("status")?.asString=="revoked")}
        catch(e:retrofit2.HttpException){if(e.code()!=404&&e.code()!=401)throw e}
        store.clear()
    }}
}

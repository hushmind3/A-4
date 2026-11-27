package com.dominion.mobile.data.remote

import android.content.Context
import com.dominion.mobile.data.local.EncryptedRecordStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*
import java.net.URI
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.TimeUnit

const val CLOUD_ORIGIN = "https://dominion-cloud.dominion-hushmind.workers.dev"

internal object CloudPairingProtocol {
    private val idPattern = Regex("[A-Za-z0-9_-]{43}")
    private fun encode(bytes: ByteArray) = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    fun token() = "dapp_" + encode(ByteArray(32).also { SecureRandom().nextBytes(it) })
    fun hash(token: String) = encode(MessageDigest.getInstance("SHA-256").digest(token.toByteArray(Charsets.UTF_8)))
    fun validToken(token: String) = token.startsWith("dapp_") && idPattern.matches(token.removePrefix("dapp_"))
    fun approvalUrl(id: String, supplied: String): String {
        require(idPattern.matches(id)) { "Invalid pairing ID" }
        val expected = "$CLOUD_ORIGIN/connect?code=$id"
        require(supplied == expected && URI(supplied).scheme == "https") { "Invalid approval URL" }
        return expected
    }
}

data class CloudPairing(val id: String, val token: String, val verificationUrl: String, val verifiedAccountId: String? = null)
data class CloudStatus(val id: String, val status: String, val expires_at: Long)
data class CloudIdentity(val account_id: String, val scope: String, val app_session_id: String)
data class CloudBilling(val plan: String, val free_daily_remaining: Int, val free_daily_limit: Int,
    val account_id: String? = null, val credits_micro_krw: Long? = null,
    val free_monthly_remaining: Int? = null, val free_monthly_limit: Int? = null,
    val free_quota_reset_utc: String? = null, val valid_until: Long? = null,
    val pro_price_krw: Long? = null, val checkout_enabled: Boolean? = null) {
    fun verifiedFor(accountId: String): CloudBilling {
        require(account_id == accountId && accountId.isNotBlank())
        require(plan.isNotBlank() && free_daily_limit >= 0 && free_daily_remaining in 0..free_daily_limit)
        require(credits_micro_krw != null && credits_micro_krw >= 0)
        return this
    }
    fun balanceKrw(): String? = credits_micro_krw?.let {
        java.math.BigDecimal.valueOf(it, 3).stripTrailingZeros().toPlainString() + "원"
    }
}
data class CloudModel(val id: String, val name: String, val free: Boolean)
data class CloudModels(val data: List<CloudModel>)
private data class PairRequest(val app_name: String, val token_hash: String)
private data class PairResponse(val id: String, val expires_in: Int, val status: String, val verification_uri: String)
private data class DisconnectResponse(val status: String)
data class CloudVoiceCapabilities(val stt:Boolean=false, val tts_en:Boolean=false,
    val tts_ko:Boolean=false,val mode:String="cloud_only",val max_audio_seconds:Int=20)
data class CloudTranscript(val text:String,val language:String,val remaining:Int)
private data class CloudSpeechText(val text:String,val language:String)
private data class CloudTaskRequest(val client_id:String,val prompt:String)
data class CloudToolCapabilities(val tools:List<String> = emptyList(),val direct_tools:Boolean=false)
data class CloudDirectObservation(val tool:String,val status:String,val source:String,
    val observed_at:Long,val observation:String)
data class CloudToolReceipt(val step:Int,val tool:String,val status:String,
    val observed_at:Long,val source:String,val observation:String)
data class CloudTask(val id:String,val status:String,val steps:Int=0,
    val result:String?=null,val error:String?=null,val updated_at:Long=0,
    val evidence:List<CloudToolReceipt>?=null) {
    fun checked(expectedId:String?=null):CloudTask {
        require(runCatching{java.util.UUID.fromString(id).toString()==id}.getOrDefault(false))
        require(expectedId==null||expectedId==id)
        require(status in setOf("queued","running","done","failed","cancelled"))
        require(steps in 0..5)
        require(result==null||result.length<=5000)
        evidence?.let { rows ->
            require(rows.size<=5 && rows.map{it.step}.distinct().size==rows.size)
            require(rows.all{it.step in 1..steps && it.tool in setOf("clock","web.read","web.search","browser.read","browser.links") &&
                it.status in setOf("ok","failed") && it.observed_at>0 &&
                it.source.length<=1000 && it.observation.length<=4000})
        }
        return this
    }
}
private interface CloudApi {
    @POST("v1/apps/start") suspend fun start(@Body request: PairRequest): PairResponse
    @GET("v1/apps/status") suspend fun status(@Header("Authorization") auth: String): CloudStatus
    @GET("v1/me") suspend fun me(@Header("Authorization") auth: String): CloudIdentity
    @GET("v1/billing") suspend fun billing(@Header("Authorization") auth: String): CloudBilling
    @GET("v1/models") suspend fun models(@Header("Authorization") auth: String): CloudModels
    @POST("v1/chat/completions") suspend fun complete(@Header("Authorization") auth:String,
        @Body request:CloudCompletionRequest):CloudCompletionResponse
    @GET("v1/agent/jobs/capabilities") suspend fun agentCapabilities(@Header("Authorization") auth:String):CloudToolCapabilities
    @POST("v1/agent/jobs/tools") suspend fun runAgentTool(@Header("Authorization") auth:String,
        @Body body:com.google.gson.JsonObject):CloudDirectObservation
    @POST("v1/agent/jobs") suspend fun startCloudJob(@Header("Authorization") auth:String,
        @Body body:CloudTaskRequest):CloudTask
    @GET("v1/agent/jobs/{id}") suspend fun cloudJob(@Header("Authorization") auth:String,
        @Path("id") id:String):CloudTask
    @POST("v1/agent/jobs/{id}/cancel") suspend fun cancelCloudJob(@Header("Authorization") auth:String,
        @Path("id") id:String):CloudTask
    @GET("v1/voice/capabilities") suspend fun voiceCapabilities(@Header("Authorization") auth:String):CloudVoiceCapabilities
    @POST("v1/voice/transcriptions") suspend fun transcribeCloud(
        @Header("Authorization") auth:String,@Header("x-dominion-language") language:String,
        @Body body:okhttp3.RequestBody):CloudTranscript
    @retrofit2.http.Streaming
    @POST("v1/voice/speech") suspend fun synthesizeCloud(
        @Header("Authorization") auth:String,@Body body:CloudSpeechText):ResponseBody
    @POST("v1/apps/disconnect") suspend fun disconnect(@Header("Authorization") auth: String, @Body body: Map<String, String>): DisconnectResponse
}

/** Cloud credentials stay here except for explicitly approved free-only provider registration
 * in the user's already authenticated DOMINION engine. No owner/cloud admin bearer is exported. */

data class CloudAccountSnapshot(
    val pairingId: String? = null,
    val approvalUrl: String? = null,
    val status: String = "disconnected",
    val expiresAt: Long = 0,
    val accountId: String? = null,
    val billing: CloudBilling? = null,
    val models: List<CloudModel> = emptyList(),
    val linkedEngineOrigin:String?=null,
    val autoReconnect:Boolean=false
)

class CloudAccountRepository(context: Context) {
    private val appContext = context.applicationContext
    private val store = EncryptedRecordStore(appContext, "dominion_cloud_session", CloudPairing::class.java)
    private val lock = Mutex()
    private val customInference=CustomMobileInference(appContext)
    suspend fun customModelConfig():CustomModelConfig?=withContext(Dispatchers.IO){
        savedAccountId()?.let{customInference.load(it)}
    }
    suspend fun configureCustomModel(config:CustomModelConfig):CloudCompletion=withContext(Dispatchers.IO){
        lock.withLock{
            val account=requireNotNull(savedAccountId()){"DOMINION account required"}
            val checked=config.checked()
            val result=customInference.complete(checked,listOf(CloudChatMessage("user","Reply with OK.")),64)
            check(savedAccountId()==account)
            customInference.save(account,checked)
            result
        }
    }
    val remote=CloudRemoteAccount(appContext)
    /** Verified account from encrypted pairing; no network or token is exposed. */
    fun savedAccountId():String? = runCatching{
        loadPairing()?.verifiedAccountId?.takeIf{it.isNotBlank()}
    }.getOrNull()
    private val engineLinks=EncryptedRecordStore(appContext,"dominion_cloud_engine_links",CloudEngineLinks::class.java)
    private fun links()=engineLinks.load()?:CloudEngineLinks()
    private fun saveLinks(value:CloudEngineLinks)=engineLinks.save(value)
    private val api = Retrofit.Builder().baseUrl("$CLOUD_ORIGIN/")
        .client(OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
            .retryOnConnectionFailure(false).connectTimeout(15,TimeUnit.SECONDS)
            .readTimeout(120,TimeUnit.SECONDS).callTimeout(130, TimeUnit.SECONDS).build())
        .addConverterFactory(GsonConverterFactory.create()).build().create(CloudApi::class.java)

    private fun loadPairing(): CloudPairing? = store.load()?.also {
        require(CloudPairingProtocol.validToken(it.token))
        CloudPairingProtocol.approvalUrl(it.id, it.verificationUrl)
    }

    suspend fun begin(): CloudAccountSnapshot = withContext(Dispatchers.IO) {
        lock.withLock {
            check(loadPairing() == null) { "Disconnect existing session first" }
            val token = CloudPairingProtocol.token()
            val response = api.start(PairRequest("DOMINION Mobile", CloudPairingProtocol.hash(token)))
            require(response.status == "pending" && response.expires_in in 1..600)
            val pair = CloudPairing(response.id, token, CloudPairingProtocol.approvalUrl(response.id, response.verification_uri))
            store.save(pair)
            CloudAccountSnapshot(pair.id, pair.verificationUrl, "pending", System.currentTimeMillis()/1000 + response.expires_in)
        }
    }

    suspend fun refresh(): CloudAccountSnapshot = withContext(Dispatchers.IO) {
        lock.withLock {
            val pair = loadPairing() ?: return@withLock CloudAccountSnapshot()
            val auth = "Bearer ${pair.token}"
            val status = try { api.status(auth) } catch (e: retrofit2.HttpException) {
                if (e.code() == 404) {
                    store.clear()
                    return@withLock CloudAccountSnapshot(status = "expired")
                }
                throw e
            }
            require(status.id == pair.id)
            when (status.status) {
                "pending" -> CloudAccountSnapshot(pair.id, pair.verificationUrl, status.status, status.expires_at)
                "approved" -> {
                    val identity = api.me(auth)
                    require(identity.app_session_id == pair.id && identity.scope == "free_inference")
                    require(identity.account_id.isNotBlank())
                    require(pair.verifiedAccountId == null || pair.verifiedAccountId == identity.account_id)
                    val billing = api.billing(auth).verifiedFor(identity.account_id)
                    if (pair.verifiedAccountId == null) store.save(pair.copy(verifiedAccountId = identity.account_id))
                    val link=links().forAccount(identity.account_id)
                    CloudAccountSnapshot(pair.id, status = "approved", expiresAt = status.expires_at,
                        accountId = identity.account_id, billing = billing,
                        models = api.models(auth).data.filter { it.free },
                        linkedEngineOrigin=link?.connection?.origin,autoReconnect=link?.autoReconnect==true)
                }
                "revoked", "expired" -> {
                    store.clear()
                    CloudAccountSnapshot(status = status.status)
                }
                else -> error("Unknown cloud session state")
            }
        }
    }

    private suspend fun approved(): Pair<CloudPairing,CloudIdentity> {
        val pair=requireNotNull(loadPairing()){"Google account connection required"}
        val auth="Bearer "+pair.token
        val status=api.status(auth)
        check(status.id==pair.id && status.status=="approved" && status.expires_at>System.currentTimeMillis()/1000)
        val identity=api.me(auth)
        check(identity.scope=="free_inference" && identity.app_session_id==pair.id &&
            identity.account_id.isNotBlank() && (pair.verifiedAccountId==null || pair.verifiedAccountId==identity.account_id))
        if(pair.verifiedAccountId==null)store.save(pair.copy(verifiedAccountId=identity.account_id))
        return pair to identity
    }

    /** Local association of existing owner credentials with an approved cloud account. */
    suspend fun bindEngine(connection:com.dominion.mobile.data.local.EngineConnection,
        stillSelected:()->Boolean) = withContext(Dispatchers.IO) { lock.withLock {
        val account=approved().second.account_id
        check(stillSelected())
        val probe=probeEngine(ApiFactory.create(connection))
        check(probe.authenticated==ProbeState.OK && probe.rooms==ProbeState.OK)
        check(stillSelected() && approved().second.account_id==account)
        saveLinks(links().bind(account,connection))
    } }

    suspend fun restoreLinkedEngine(automatic:Boolean=true):com.dominion.mobile.data.local.EngineConnection? =
        withContext(Dispatchers.IO) { lock.withLock {
            if(loadPairing()==null)return@withLock null
            val account=approved().second.account_id
            val link=links().forAccount(account)?:return@withLock null
            if(automatic && !link.autoReconnect)return@withLock null
            val raw=link.connection
            val connection=raw.validated()
            val result=probeEngine(ApiFactory.create(connection))
            check(result.authenticated==ProbeState.OK && result.rooms==ProbeState.OK)
            check(approved().second.account_id==account)
            if(!automatic)saveLinks(links().bind(account,connection))
            connection
        } }

    suspend fun pauseEngineReconnect(connection:com.dominion.mobile.data.local.EngineConnection) =
        withContext(Dispatchers.IO) { lock.withLock { saveLinks(links().pause(connection)) } }

    suspend fun unlinkEngine()=withContext(Dispatchers.IO) { lock.withLock {
        saveLinks(links().forget(approved().second.account_id))
    } }

    suspend fun complete(model:String,messages:List<CloudChatMessage>):CloudCompletion =
        withContext(Dispatchers.IO) { lock.withLock {
            val (pair,identity)=approved()
            val auth="Bearer "+pair.token
            check(api.models(auth).data.any{it.free && it.id==model})
            val request=CloudCompletionRequest(model,messages).validated()
            val result=api.complete(auth,request).verified(model)
            check(approved().second.account_id==identity.account_id)
            result
        } }

    /** A previously verified inference grant is authorized by the Worker on
     * every completion. Do not perform redundant status/me/catalog requests per
     * model step; refresh/selection still check the real account and model list. */
    suspend fun completeForMobile(model:String,messages:List<CloudChatMessage>,maxTokens:Int=512):CloudCompletion =
        withContext(Dispatchers.IO){lock.withLock{
            if(model==CustomModelConfig.SELECTION){
                val account=requireNotNull(savedAccountId()){"DOMINION account required"}
                val config=requireNotNull(customInference.load(account)){"Configure custom API first"}
                val result=customInference.complete(config,messages,maxTokens)
                check(savedAccountId()==account)
                return@withLock result
            }
            val saved=requireNotNull(loadPairing()){"Google account connection required"}
            val pair=if(saved.verifiedAccountId.isNullOrBlank())approved().first else saved
            val request=CloudCompletionRequest(model,messages,max_tokens=maxTokens).validated()
            api.complete("Bearer "+pair.token,request).verified(model)
        }}

    /** The phone transmits only bounded microphone PCM. STT/TTS models run in
     * DOMINION Cloud and cannot receive the device pairing token from callers. */
    /** Read-only cloud research job. State persists after app process death.
     * A stable client UUID makes submit/retry idempotent per verified owner. */
    suspend fun submitCloudTask(prompt:String,clientId:String):CloudTask=withContext(Dispatchers.IO){
        require(prompt.isNotBlank()&&prompt.length<=3500)
        require(java.util.UUID.fromString(clientId).toString()==clientId)
        lock.withLock{
            val pair=if(savedAccountId()==null)approved().first
                else requireNotNull(loadPairing()){"DOMINION Cloud login required"}
            api.startCloudJob("Bearer "+pair.token,CloudTaskRequest(clientId,prompt)).checked()
        }
    }

    suspend fun cloudTask(id:String):CloudTask=withContext(Dispatchers.IO){
        require(java.util.UUID.fromString(id).toString()==id)
        lock.withLock{
            val pair=if(savedAccountId()==null)approved().first
                else requireNotNull(loadPairing()){"DOMINION Cloud login required"}
            api.cloudJob("Bearer "+pair.token,id).checked(id)
        }
    }

    suspend fun cancelCloudTask(id:String):CloudTask=withContext(Dispatchers.IO){
        require(java.util.UUID.fromString(id).toString()==id)
        lock.withLock{
            val pair=if(savedAccountId()==null)approved().first
                else requireNotNull(loadPairing()){"DOMINION Cloud login required"}
            api.cancelCloudJob("Bearer "+pair.token,id).checked(id)
        }
    }

    suspend fun directToolNames():Set<String> = withContext(Dispatchers.IO){
        lock.withLock{
            val pair=if(savedAccountId()==null)approved().first
                else requireNotNull(loadPairing())
            val caps=try{api.agentCapabilities("Bearer "+pair.token)}
                catch(e:retrofit2.HttpException){if(e.code() in setOf(404,503))return@withLock emptySet<String>() else throw e}
            if(caps.direct_tools)caps.tools.toSet().intersect(setOf("web.search","browser.read","browser.links"))
            else emptySet()
        }
    }

    suspend fun directTool(tool:String,args:com.google.gson.JsonObject):String=withContext(Dispatchers.IO){
        require(tool in setOf("web.search","browser.read","browser.links"))
        val body=com.google.gson.JsonObject().apply{addProperty("tool",tool);add("args",args)}
        lock.withLock{
            val pair=if(savedAccountId()==null)approved().first
                else requireNotNull(loadPairing())
            val result=api.runAgentTool("Bearer "+pair.token,body)
            require(result.tool==tool && result.status in setOf("ok","failed") &&
                result.source==(if(tool=="web.search")args["source"].asString else args["url"].asString) &&
                result.observed_at>0 && result.observation.length in 1..4000)
            check(result.status=="ok"){result.observation}
            result.observation
        }
    }

    suspend fun mobileVoiceCapabilities():CloudVoiceCapabilities=withContext(Dispatchers.IO){
        lock.withLock{
            val pair=if(savedAccountId()==null)approved().first
                else requireNotNull(loadPairing()){"DOMINION Cloud login required"}
            api.voiceCapabilities("Bearer "+pair.token)
        }
    }

    suspend fun transcribeForMobile(wav:ByteArray,language:String):String=withContext(Dispatchers.IO){
        require(com.dominion.mobile.data.WaveAudio.isWave(wav)&&wav.size in 8044..640044)
        require(language in setOf("ko","en"))
        lock.withLock{
            val pair=if(savedAccountId()==null)approved().first
                else requireNotNull(loadPairing()){"DOMINION Cloud login required"}
            val result=api.transcribeCloud("Bearer "+pair.token,language,
                wav.toRequestBody("audio/wav".toMediaType()))
            result.text.trim().also{require(it.isNotBlank()&&it.length<=5000)}
        }
    }

    suspend fun speakForMobile(text:String,language:String):ByteArray=withContext(Dispatchers.IO){
        require(text.isNotBlank()&&text.length<=700&&language in setOf("ko","en"))
        lock.withLock{
            val pair=if(savedAccountId()==null)approved().first
                else requireNotNull(loadPairing()){"DOMINION Cloud login required"}
            val response=api.synthesizeCloud("Bearer "+pair.token,CloudSpeechText(text,language))
            response.use{body->
                val length=body.contentLength()
                require(length<0||length in 64..3000000){"Cloud voice audio too large"}
                body.byteStream().use{input->
                    val output=java.io.ByteArrayOutputStream()
                    val buffer=ByteArray(8192)
                    while(true){
                        val n=input.read(buffer)
                        if(n<0)break
                        require(output.size()+n<=3000000){"Cloud voice audio too large"}
                        output.write(buffer,0,n)
                    }
                    output.toByteArray().also{require(it.size>=64)}
                }
            }
        }
    }

    suspend fun registerEngineModel(model:String,connection:com.dominion.mobile.data.local.EngineConnection,
        stillSelected:()->Boolean):String = withContext(Dispatchers.IO) { lock.withLock {
        val (pair,identity)=approved()
        check(api.models("Bearer "+pair.token).data.any{it.free && it.id==model})
        check(stillSelected())
        val engine=ApiFactory.create(connection)
        // Separate mobile-scoped record; never overwrite a desktop account/provider.
        val id=CloudModelProtocol.providerId(pair.id,model)
        val existing=engine.cloudProviderRecords().firstOrNull{it.get("id")?.asString==id}
        if(existing!=null)check(existing.get("baseUrl")?.asString=="$CLOUD_ORIGIN/v1" &&
            existing.get("model")?.asString==model && existing.get("integration")?.asString=="dominion-mobile-cloud")
        check(stillSelected())
        val saved=engine.saveCloudProvider(CloudModelProtocol.providerBody(id,model,pair.token))
        check(saved.get("id")?.asString==id && saved.get("baseUrl")?.asString=="$CLOUD_ORIGIN/v1" &&
            saved.get("model")?.asString==model && saved.get("hasKey")?.asBoolean==true)
        check(stillSelected() && approved().second.account_id==identity.account_id)
        id
    } }

    suspend fun disconnect(): CloudAccountSnapshot = withContext(Dispatchers.IO) {
        lock.withLock {
            val pair = loadPairing() ?: return@withLock CloudAccountSnapshot()
            remote.disconnect()
            check(api.disconnect("Bearer ${pair.token}", emptyMap()).status == "revoked")
            store.clear()
            CloudAccountSnapshot()
        }
    }
}

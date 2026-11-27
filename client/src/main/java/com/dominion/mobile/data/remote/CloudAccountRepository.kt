package com.dominion.mobile.data.remote

import android.content.Context
import com.dominion.mobile.data.local.EncryptedRecordStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
private interface CloudApi {
    @POST("v1/apps/start") suspend fun start(@Body request: PairRequest): PairResponse
    @GET("v1/apps/status") suspend fun status(@Header("Authorization") auth: String): CloudStatus
    @GET("v1/me") suspend fun me(@Header("Authorization") auth: String): CloudIdentity
    @GET("v1/billing") suspend fun billing(@Header("Authorization") auth: String): CloudBilling
    @GET("v1/models") suspend fun models(@Header("Authorization") auth: String): CloudModels
    @POST("v1/chat/completions") suspend fun complete(@Header("Authorization") auth:String,
        @Body request:CloudCompletionRequest):CloudCompletionResponse
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
    val remote=CloudRemoteAccount(appContext)
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

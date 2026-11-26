package com.dominion.mobile.data.remote

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*
import java.net.URI
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

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

data class CloudPairing(val id: String, val token: String, val verificationUrl: String)
data class CloudStatus(val id: String, val status: String, val expires_at: Long)
data class CloudIdentity(val account_id: String, val scope: String, val app_session_id: String)
data class CloudBilling(val plan: String, val free_daily_remaining: Int, val free_daily_limit: Int)
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
    @POST("v1/apps/disconnect") suspend fun disconnect(@Header("Authorization") auth: String, @Body body: Map<String, String>): DisconnectResponse
}

/** Cloud-only credentials. The local engine never receives this bearer. */
private class CloudCredentialStore(context: Context) {
    private val prefs = context.getSharedPreferences("dominion_cloud_session", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val alias = "dominion.cloud.session.aes.v1"
    private val codec = Base64.getEncoder()
    private val decoder = Base64.getDecoder()
    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setKeySize(256).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    fun load(): CloudPairing? {
        val encoded = prefs.getString("sealed", null) ?: return null
        val parts = encoded.split(':', limit = 2)
        require(parts.size == 2)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, decoder.decode(parts[0])))
        val pair = gson.fromJson(String(cipher.doFinal(decoder.decode(parts[1])), Charsets.UTF_8), CloudPairing::class.java)
        require(CloudPairingProtocol.validToken(pair.token))
        CloudPairingProtocol.approvalUrl(pair.id, pair.verificationUrl)
        return pair
    }
    fun save(pair: CloudPairing) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val data = cipher.doFinal(gson.toJson(pair).toByteArray(Charsets.UTF_8))
        check(prefs.edit().putString("sealed", codec.encodeToString(cipher.iv) + ":" + codec.encodeToString(data)).commit())
    }
    fun clear() { check(prefs.edit().remove("sealed").commit()) }
}

data class CloudAccountSnapshot(
    val pairingId: String? = null,
    val approvalUrl: String? = null,
    val status: String = "disconnected",
    val expiresAt: Long = 0,
    val accountId: String? = null,
    val billing: CloudBilling? = null,
    val models: List<CloudModel> = emptyList()
)

class CloudAccountRepository(context: Context) {
    private val store = CloudCredentialStore(context.applicationContext)
    private val lock = Mutex()
    private val api = Retrofit.Builder().baseUrl("$CLOUD_ORIGIN/")
        .client(OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
            .retryOnConnectionFailure(false).callTimeout(20, TimeUnit.SECONDS).build())
        .addConverterFactory(GsonConverterFactory.create()).build().create(CloudApi::class.java)

    suspend fun begin(): CloudAccountSnapshot = withContext(Dispatchers.IO) {
        lock.withLock {
            check(store.load() == null) { "Disconnect existing session first" }
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
            val pair = store.load() ?: return@withLock CloudAccountSnapshot()
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
                    CloudAccountSnapshot(pair.id, status = "approved", expiresAt = status.expires_at,
                        accountId = identity.account_id, billing = api.billing(auth),
                        models = api.models(auth).data.filter { it.free })
                }
                "revoked", "expired" -> {
                    store.clear()
                    CloudAccountSnapshot(status = status.status)
                }
                else -> error("Unknown cloud session state")
            }
        }
    }

    suspend fun disconnect(): CloudAccountSnapshot = withContext(Dispatchers.IO) {
        lock.withLock {
            val pair = store.load() ?: return@withLock CloudAccountSnapshot()
            check(api.disconnect("Bearer ${pair.token}", emptyMap()).status == "revoked")
            store.clear()
            CloudAccountSnapshot()
        }
    }
}

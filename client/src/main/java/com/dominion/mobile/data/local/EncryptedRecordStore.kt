package com.dominion.mobile.data.local

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.google.gson.Gson
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal class EncryptedRecordStore<T : Any>(context: Context, name: String, private val type: Class<T>) {
    private val prefs = context.getSharedPreferences(name, Context.MODE_PRIVATE)
    private val gson = Gson()
    private val alias = if (name == "dominion_cloud_session") "dominion.cloud.session.aes.v1" else "$name.aes.v1"
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
    fun load(): T? {
        val encoded = prefs.getString("sealed", null) ?: return null
        val parts = encoded.split(':', limit = 2)
        require(parts.size == 2)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, decoder.decode(parts[0])))
        return gson.fromJson(String(cipher.doFinal(decoder.decode(parts[1])), Charsets.UTF_8), type)
    }
    fun save(pair: T) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val data = cipher.doFinal(gson.toJson(pair).toByteArray(Charsets.UTF_8))
        check(prefs.edit().putString("sealed", codec.encodeToString(cipher.iv) + ":" + codec.encodeToString(data)).commit())
    }
    fun clear() { check(prefs.edit().remove("sealed").commit()) }
}

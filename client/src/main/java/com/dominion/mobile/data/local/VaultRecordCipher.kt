package com.dominion.mobile.data.local

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Row-specific AES-GCM. Never generate a replacement key to decrypt old ciphertext. */
internal class VaultRecordCipher(private val scope: String) {
    private val alias = "dominion.vault.$scope.aes.v1"
    private val prefix = "vault-gcm-v1:"
    private val encoder = Base64.getEncoder()
    private val decoder = Base64.getDecoder()

    private fun key(create: Boolean): SecretKey = synchronized(keyLock) {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return@synchronized it }
        check(create) { "Vault encryption key is unavailable" }
        KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setKeySize(256).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }

    private fun aad(id: String) = ("dominion-vault-v1:" + scope + ":" + id).toByteArray(Charsets.UTF_8)

    fun isSealed(value: String) = value.startsWith(prefix)

    fun seal(id: String, json: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key(true))
        cipher.updateAAD(aad(id))
        return prefix + encoder.encodeToString(cipher.iv) + ":" +
            encoder.encodeToString(cipher.doFinal(json.toByteArray(Charsets.UTF_8)))
    }

    fun open(id: String, value: String): String {
        require(isSealed(value))
        val parts = value.removePrefix(prefix).split(':', limit = 2)
        require(parts.size == 2)
        val iv = decoder.decode(parts[0])
        require(iv.size == 12)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(false), GCMParameterSpec(128, iv))
        cipher.updateAAD(aad(id))
        return String(cipher.doFinal(decoder.decode(parts[1])), Charsets.UTF_8)
    }
    private companion object { val keyLock = Any() }
}

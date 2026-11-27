package com.dominion.mobile.data.local

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import com.dominion.mobile.data.model.ArchiveItem
import com.dominion.mobile.platform.attachments.LocalAttachment
import java.io.File
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Explicit offline copies, origin-scoped and authenticated with a device Keystore key. */
internal class OfflineArchiveStore(context: Context, private val scope: String) {
    init { require(scope.matches(Regex("[a-zA-Z0-9_-]{1,128}"))) }
    private val root = File(context.noBackupFilesDir, "offline-archive/$scope")
    private val alias = "dominion.archive.$scope.aes.v1"
    private val limit = 128L * 1024 * 1024
    private fun binding(item: ArchiveItem): ByteArray {
        val attachment = requireNotNull(item.attachmentId)
        java.util.UUID.fromString(attachment)
        val room = requireNotNull(item.conversationId)
        require(room.isNotBlank())
        return ("archive-v1\u0000" + scope + "\u0000" + room + "\u0000" + attachment + "\u0000" + item.id)
            .toByteArray(Charsets.UTF_8)
    }
    private fun path(item: ArchiveItem): File {
        val name = MessageDigest.getInstance("SHA-256").digest(binding(item)).joinToString("") {
            "%02x".format(it.toInt() and 255)
        }
        return File(root, "$name.bin")
    }

    private fun key(create: Boolean): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        check(create) { "Offline archive key unavailable" }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setKeySize(256).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }

    @Synchronized fun has(item: ArchiveItem): Boolean = path(item).isFile
    @Synchronized fun write(item: ArchiveItem, bytes: ByteArray) {
        require(bytes.isNotEmpty() && bytes.size <= LocalAttachment.MAX_BYTES)
        check(root.isDirectory || root.mkdirs())
        val target = path(item)
        val used = root.listFiles().orEmpty().sumOf { it.length() }
        require(used - target.length() + bytes.size + 64 <= limit) { "Offline archive quota exceeded" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.ENCRYPT_MODE, key(true))
            updateAAD(binding(item))
        }
        val iv = cipher.iv
        require(iv.size == 12)
        val sealed = byteArrayOf(1) + iv + cipher.doFinal(bytes)
        val atomic = AtomicFile(target)
        val out = atomic.startWrite()
        try { out.write(sealed); atomic.finishWrite(out) }
        catch (e: Exception) { atomic.failWrite(out); throw e }
        check(read(item)?.contentEquals(bytes) == true)
    }

    @Synchronized fun read(item: ArchiveItem): ByteArray? {
        val target = path(item)
        if (!target.exists() && !File(target.path + ".bak").exists()) return null
        val sealed = AtomicFile(target).openRead().use { stream ->
            val output = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            var n = stream.read(buffer)
            while (n != -1) {
                require(output.size() + n <= LocalAttachment.MAX_BYTES + 64)
                output.write(buffer, 0, n)
                n = stream.read(buffer)
            }
            output.toByteArray()
        }
        require(sealed.size >= 30 && sealed[0] == 1.toByte())
        return Cipher.getInstance("AES/GCM/NoPadding").run {
            init(Cipher.DECRYPT_MODE, key(false), GCMParameterSpec(128, sealed.copyOfRange(1,13)))
            updateAAD(binding(item))
            doFinal(sealed, 13, sealed.size - 13)
        }.also { require(it.isNotEmpty() && it.size <= LocalAttachment.MAX_BYTES) }
    }

    @Synchronized fun remove(item: ArchiveItem) {
        val target = path(item)
        AtomicFile(target).delete()
        check(!target.exists())
    }

    @Synchronized fun clear() {
        check(!root.exists() || root.deleteRecursively())
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (store.containsAlias(alias)) store.deleteEntry(alias)
    }
}

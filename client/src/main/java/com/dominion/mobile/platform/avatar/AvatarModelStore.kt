package com.dominion.mobile.platform.avatar

import android.util.AtomicFile
import com.dominion.mobile.data.ObjAvatarMesh
import java.io.File
import java.io.FileNotFoundException
import java.io.InputStream
import java.security.MessageDigest

/** Private, per-engine/per-character OBJ storage. No external URI permission is retained. */
internal class AvatarModelStore(private val root: File) {
    private fun file(key: String): AtomicFile {
        require(key.matches(Regex("[a-f0-9]{64}")))
        return AtomicFile(File(File(root, "avatar_models"), "$key.obj"))
    }

    data class Scene(val mesh: FloatArray, val mouth: FloatArray? = null)
    private fun bytes(key: String): ByteArray? =
        try { file(key).openRead().use(::boundedRead) }
        catch (_: FileNotFoundException) { null }
    private fun mouthKey(key: String, base: ByteArray): String =
        Companion.key("mouth:" + key, MessageDigest.getInstance("SHA-256").digest(base)
            .joinToString("") { "%02x".format(it.toInt() and 255) })

    fun load(key: String): FloatArray? = synchronized(lock) {
        bytes(key)?.let { ObjAvatarMesh.parse(it.toString(Charsets.UTF_8)) }
    }

    fun loadScene(key: String): Scene? = synchronized(lock) {
        val base = bytes(key) ?: return@synchronized null
        val neutral = ObjAvatarMesh.parse(base.toString(Charsets.UTF_8))
        val target = bytes(mouthKey(key, base)) ?: return@synchronized Scene(neutral)
        val morph = runCatching { ObjAvatarMesh.morph(base.toString(Charsets.UTF_8),
            target.toString(Charsets.UTF_8)) }.getOrNull()
        if (morph == null) Scene(neutral) else Scene(morph.base, morph.open)
    }

    fun saveMouth(key: String, target: ByteArray): Scene = synchronized(lock) {
        require(target.size in 1..ObjAvatarMesh.MAX_BYTES)
        val base = requireNotNull(bytes(key))
        val morph = ObjAvatarMesh.morph(base.toString(Charsets.UTF_8), target.toString(Charsets.UTF_8))
        write(mouthKey(key, base), target)
        Scene(morph.base, morph.open)
    }

    fun save(key: String, bytes: ByteArray): FloatArray = synchronized(lock) {
        require(bytes.size in 1..ObjAvatarMesh.MAX_BYTES)
        // Parse before replacing the previous valid model.
        val mesh = ObjAvatarMesh.parse(bytes.toString(Charsets.UTF_8))
        write(key, bytes)
        mesh
    }

    private fun write(key: String, bytes: ByteArray) {
        val target = file(key)
        val directory = requireNotNull(target.baseFile.parentFile)
        check(directory.isDirectory || directory.mkdirs())
        if (!target.baseFile.exists()) {
            check(directory.listFiles().orEmpty().count { it.name.endsWith(".obj") } < 50) {
                "Local avatar model limit reached"
            }
        }
        val stream = target.startWrite()
        try {
            stream.write(bytes)
            target.finishWrite(stream)
        } catch (failure: Throwable) {
            target.failWrite(stream)
            throw failure
        }
        check(target.openRead().use(::boundedRead).contentEquals(bytes)) { "OBJ persistence not verified" }
    }

    private fun boundedRead(input: InputStream): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        val chunk = ByteArray(8192)
        while (true) {
            val count = input.read(chunk)
            if (count < 0) break
            require(output.size() + count <= ObjAvatarMesh.MAX_BYTES)
            output.write(chunk, 0, count)
        }
        return output.toByteArray()
    }

    companion object {
        private val lock = Any()
        fun key(scope: String, character: String): String {
            require(scope.isNotBlank() && character.isNotBlank())
            val input = "${scope.length}:$scope$character"
            return MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it.toInt() and 255) }
        }
    }
}

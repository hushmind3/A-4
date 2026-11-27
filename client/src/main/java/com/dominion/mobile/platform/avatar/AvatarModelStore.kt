package com.dominion.mobile.platform.avatar

import android.util.AtomicFile
import com.dominion.mobile.data.ObjAvatarMesh
import com.dominion.mobile.data.AvatarRig
import com.dominion.mobile.data.AvatarRigSpec
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

    data class Scene(val mesh: FloatArray, val mouth: FloatArray? = null, val rig: AvatarRig? = null,
        val visemes: Map<String, FloatArray> = emptyMap())
    private fun bytes(key: String): ByteArray? =
        try { file(key).openRead().use(::boundedRead) }
        catch (_: FileNotFoundException) { null }
    private fun mouthKey(key: String, base: ByteArray): String =
        Companion.key("mouth:" + key, MessageDigest.getInstance("SHA-256").digest(base)
            .joinToString("") { "%02x".format(it.toInt() and 255) })

    private fun visemeKey(key: String, base: ByteArray, shape: String): String {
        require(shape in com.dominion.mobile.data.remote.SpeechCueTimeline.shapes)
        return Companion.key("viseme:" + key + ":" + shape, AvatarRigSpec.sha256(base))
    }
    fun saveViseme(key: String, shape: String, target: ByteArray): Scene = synchronized(lock) {
        require(target.size in 1..ObjAvatarMesh.MAX_BYTES)
        val base = requireNotNull(bytes(key))
        ObjAvatarMesh.morph(base.toString(Charsets.UTF_8), target.toString(Charsets.UTF_8))
        write(visemeKey(key,base,shape),target)
        requireNotNull(loadScene(key)).also { check(shape in it.visemes) }
    }
    private fun rigKey(key: String, base: ByteArray): String =
        Companion.key("rig:" + key, AvatarRigSpec.sha256(base))

    private fun parseRig(base: ByteArray, vertices: Int, spec: ByteArray): AvatarRig {
        require(spec.size in 1..ObjAvatarMesh.MAX_BYTES)
        return requireNotNull(com.google.gson.Gson().fromJson(spec.toString(Charsets.UTF_8),
            AvatarRigSpec::class.java)).validated(base, vertices)
    }

    fun saveRig(key: String, spec: ByteArray): Scene = synchronized(lock) {
        val base = requireNotNull(bytes(key))
        val scene = requireNotNull(loadScene(key))
        parseRig(base, scene.mesh.size / 6, spec)
        write(rigKey(key, base), spec)
        requireNotNull(loadScene(key)).also { check(it.rig != null) }
    }

    fun load(key: String): FloatArray? = synchronized(lock) {
        bytes(key)?.let { ObjAvatarMesh.parse(it.toString(Charsets.UTF_8)) }
    }

    fun loadScene(key: String): Scene? = synchronized(lock) {
        val base = bytes(key) ?: return@synchronized null
        val neutral = ObjAvatarMesh.parse(base.toString(Charsets.UTF_8))
        val target = bytes(mouthKey(key, base))
        val morph = target?.let { runCatching { ObjAvatarMesh.morph(base.toString(Charsets.UTF_8),
            it.toString(Charsets.UTF_8)) }.getOrNull() }
        val mesh = morph?.base ?: neutral
        val rig = bytes(rigKey(key, base))?.let { runCatching { parseRig(base, mesh.size / 6, it) }.getOrNull() }
        val visemes = com.dominion.mobile.data.remote.SpeechCueTimeline.shapes.mapNotNull { shape ->
            bytes(visemeKey(key,base,shape))?.let { targetBytes ->
                runCatching {
                    val pair = ObjAvatarMesh.morph(base.toString(Charsets.UTF_8),targetBytes.toString(Charsets.UTF_8))
                    require(pair.base.contentEquals(mesh))
                    shape to pair.open
                }.getOrNull()
            }
        }.toMap()
        Scene(mesh, morph?.open, rig, visemes)
    }

    fun saveMouth(key: String, target: ByteArray): Scene = synchronized(lock) {
        require(target.size in 1..ObjAvatarMesh.MAX_BYTES)
        val base = requireNotNull(bytes(key))
        val morph = ObjAvatarMesh.morph(base.toString(Charsets.UTF_8), target.toString(Charsets.UTF_8))
        write(mouthKey(key, base), target)
        requireNotNull(loadScene(key))
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

package com.dominion.mobile.data

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.ZipInputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/** Offline-only glTF package. No absolute paths, network fetches or unbounded ZIP expansion. */
class StandardAvatarAsset private constructor(val source: ByteArray, val binary: Boolean,
    private val resources: Map<String,ByteArray>, private val prefix: String,
    val vrm: Boolean) {
    val expressionRig: VrmExpressions? by lazy { if(vrm) VrmExpressions.parse(source,binary) else null }
    fun resource(uri: String): ByteBuffer? {
        if (uri.startsWith("data:")) {
            val comma=uri.indexOf(',')
            if (comma < 0 || !uri.substring(0,comma).endsWith(";base64")) return null
            val bytes=runCatching { java.util.Base64.getDecoder().decode(uri.substring(comma+1)) }.getOrNull() ?: return null
            return bytes.takeIf { it.size <= MAX_BYTES }?.let(ByteBuffer::wrap)
        }
        val path=runCatching { safePath(prefix+uri) }.getOrNull() ?: return null
        return resources[path]?.let(ByteBuffer::wrap)
    }
    companion object {
        const val MAX_BYTES = 32 * 1024 * 1024
        private fun safePath(path: String): String {
            require(path.length in 1..300 && !path.startsWith("/") &&
                path.none { it.isISOControl() || it in "\\:%?#" } &&
                path.split('/').all { it.isNotBlank() && it != "." && it != ".." })
            return path
        }
        private fun json(bytes: ByteArray): JsonObject {
            require(bytes.size in 2..MAX_BYTES)
            return JsonParser.parseString(bytes.toString(Charsets.UTF_8).trimEnd(' ','\u0000','\n','\r','\t')).asJsonObject
        }
        private fun validate(doc: JsonObject, resolve: (String) -> Boolean): Boolean {
            require(doc.getAsJsonObject("asset")?.get("version")?.asString == "2.0")
            for (field in listOf("buffers","images")) {
                doc.getAsJsonArray(field)?.forEach { item ->
                    val uri = item.asJsonObject.get("uri")
                    if (uri != null) require(uri.isJsonPrimitive && uri.asJsonPrimitive.isString && resolve(uri.asString))
                }
            }
            val ext=doc.getAsJsonObject("extensions")
            return ext?.has("VRM") == true || ext?.has("VRMC_vrm") == true
        }
        private fun glb(bytes: ByteArray): StandardAvatarAsset {
            require(bytes.size >= 20)
            val b=ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            require(b.int == 0x46546c67 && b.int == 2 && b.int == bytes.size)
            val size=b.int
            require(size in 2..b.remaining()-4 && b.int == 0x4e4f534a)
            val metadata=ByteArray(size); b.get(metadata)
            while (b.hasRemaining()) {
                require(b.remaining() >= 8)
                val length=b.int; b.int
                require(length >= 0 && length <= b.remaining())
                b.position(b.position()+length)
            }
            val vrm=validate(json(metadata)) { it.startsWith("data:") && it.contains(";base64,") }
            return StandardAvatarAsset(bytes,true,emptyMap(),"",vrm)
        }
        fun parse(bytes: ByteArray): StandardAvatarAsset {
            require(bytes.size in 2..MAX_BYTES)
            if (bytes.size >= 4 && String(bytes,0,4,Charsets.US_ASCII) == "glTF") return glb(bytes)
            if (bytes[0] == 0x50.toByte() && bytes[1] == 0x4b.toByte()) {
                val entries=linkedMapOf<String,ByteArray>()
                var total=0
                ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
                    var count=0
                    while (true) {
                        val entry=zip.nextEntry ?: break
                        require(++count <= 128)
                        if (entry.isDirectory) { zip.closeEntry(); continue }
                        val path=safePath(entry.name)
                        require(path !in entries)
                        val out=ByteArrayOutputStream()
                        val chunk=ByteArray(8192)
                        while (true) {
                            val n=zip.read(chunk)
                            if (n < 0) break
                            total+=n;require(total <= MAX_BYTES)
                            out.write(chunk,0,n)
                        }
                        entries[path]=out.toByteArray()
                        zip.closeEntry()
                    }
                }
                val model=entries.keys.filter { it.endsWith(".gltf",true) || it.endsWith(".glb",true) || it.endsWith(".vrm",true) }.single()
                val content=entries.getValue(model)
                if (!model.endsWith(".gltf",true)) return glb(content)
                val prefix=model.substringBeforeLast('/',"").let { if(it.isEmpty()) "" else "$it/" }
                val draft=StandardAvatarAsset(content,false,entries,prefix,false)
                val vrm=validate(json(content)) { draft.resource(it) != null }
                return StandardAvatarAsset(content,false,entries,prefix,vrm)
            }
            val draft=StandardAvatarAsset(bytes,false,emptyMap(),"",false)
            val vrm=validate(json(bytes)) { draft.resource(it) != null }
            return StandardAvatarAsset(bytes,false,emptyMap(),"",vrm)
        }
    }
}

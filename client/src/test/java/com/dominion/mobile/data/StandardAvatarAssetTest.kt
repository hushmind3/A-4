package com.dominion.mobile.data
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.*
import org.junit.Test

class StandardAvatarAssetTest {
    @Test fun embeddedGltfAndZipResources() {
        val json="""{"asset":{"version":"2.0"},"buffers":[{"uri":"data:application/octet-stream;base64,AAAA","byteLength":3}]}"""
        assertFalse(StandardAvatarAsset.parse(json.toByteArray()).binary)
        assertEquals(3,StandardAvatarAsset.parse(json.toByteArray()).resource("data:application/octet-stream;base64,AAAA")!!.remaining())
        val out=ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            fun put(name:String,bytes:ByteArray) { zip.putNextEntry(ZipEntry(name));zip.write(bytes);zip.closeEntry() }
            put("model/model.gltf",json.replace("data:application/octet-stream;base64,AAAA","mesh.bin").toByteArray())
            put("model/mesh.bin",byteArrayOf(1,2,3))
        }
        val model=StandardAvatarAsset.parse(out.toByteArray())
        assertEquals(3,model.resource("mesh.bin")!!.remaining())
        assertNull(model.resource("../mesh.bin"))
        assertTrue(runCatching { StandardAvatarAsset.parse(json.replace("data:application/octet-stream;base64,AAAA","https://example.org/file").toByteArray()) }.isFailure)
    }
}

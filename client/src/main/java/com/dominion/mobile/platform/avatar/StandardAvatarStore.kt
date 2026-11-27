package com.dominion.mobile.platform.avatar

import android.util.AtomicFile
import com.dominion.mobile.data.StandardAvatarAsset
import java.io.File
import java.io.FileNotFoundException

internal class StandardAvatarStore(private val root: File) {
    private fun file(key: String): AtomicFile {
        require(key.matches(Regex("[a-f0-9]{64}")))
        return AtomicFile(File(File(root,"standard_avatars"),"$key.asset"))
    }
    fun load(key: String): StandardAvatarAsset? = synchronized(lock) {
        val bytes = try { file(key).openRead().use { stream ->
            val out=java.io.ByteArrayOutputStream(); val chunk=ByteArray(8192)
            while (true) {
                val n=stream.read(chunk); if(n<0)break
                require(out.size()+n<=StandardAvatarAsset.MAX_BYTES); out.write(chunk,0,n)
            }
            out.toByteArray()
        } } catch (_: FileNotFoundException) { return@synchronized null }
        StandardAvatarAsset.parse(bytes)
    }
    fun save(key: String, bytes: ByteArray): StandardAvatarAsset = synchronized(lock) {
        val model=StandardAvatarAsset.parse(bytes)
        val target=file(key); val dir=requireNotNull(target.baseFile.parentFile)
        check(dir.isDirectory || dir.mkdirs())
        require(dir.listFiles().orEmpty().filter { it != target.baseFile }.sumOf { it.length() } +
            bytes.size <= 160L*1024*1024)
        val out=target.startWrite()
        try { out.write(bytes); target.finishWrite(out) }
        catch(e: Throwable) { target.failWrite(out); throw e }
        check(target.baseFile.length()==bytes.size.toLong())
        model
    }
    fun remove(key: String) = synchronized(lock) { file(key).delete() }
    companion object { private val lock=Any() }
}

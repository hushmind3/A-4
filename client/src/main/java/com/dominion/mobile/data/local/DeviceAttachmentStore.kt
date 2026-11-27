package com.dominion.mobile.data.local
import android.content.Context
import com.dominion.mobile.data.model.ArchiveItem
import com.dominion.mobile.data.model.ArchiveType
import com.dominion.mobile.platform.attachments.LocalAttachment
import java.util.UUID

/** Account-bound encrypted mobile attachment bytes, not PC uploads. */
internal class DeviceAttachmentStore(context:Context,scope:String){
    private val files=OfflineArchiveStore(context,scope)
    private fun entry(room:String,id:String):ArchiveItem{
        require(room.matches(Regex("[A-Za-z0-9_:-]{1,180}")))
        require(UUID.fromString(id).toString()==id)
        return ArchiveItem(id="device-attachment:"+id,type=ArchiveType.FILE,title="",meta="",
            conversationId=room,attachmentId=id)
    }
    fun save(room:String,id:String,bytes:ByteArray){
        require(bytes.isNotEmpty()&&bytes.size<=LocalAttachment.MAX_BYTES)
        val item=entry(room,id)
        files.write(item,bytes)
        check(files.read(item)?.contentEquals(bytes)==true)
    }
    fun read(room:String,id:String):ByteArray?=files.read(entry(room,id))
    fun remove(room:String,id:String)=files.remove(entry(room,id))
}

internal object DeviceDocumentText{
    fun readable(mime:String,name:String):Boolean{
        val type=mime.substringBefore(';').lowercase()
        val ext=name.substringAfterLast('.', "").lowercase()
        return type in setOf("text/plain","text/markdown","text/csv","text/html","text/xml",
            "application/json","application/xml","application/yaml","text/yaml",
            "application/x-yaml") || ext in setOf("txt","md","csv","json","xml","yaml","yml","log")
    }
    fun extract(bytes:ByteArray):String{
        require(bytes.isNotEmpty()&&bytes.size<=LocalAttachment.MAX_BYTES)
        val sample=bytes.copyOfRange(0,minOf(bytes.size,12000))
        require(sample.none{it==0.toByte()}){"Binary data is not text"}
        val decoder=Charsets.UTF_8.newDecoder()
            .onMalformedInput(java.nio.charset.CodingErrorAction.REPLACE)
            .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPLACE)
        return decoder.decode(java.nio.ByteBuffer.wrap(sample)).toString()
            .take(8000).trim().also{require(it.isNotBlank()){"Empty text document"}}
    }
}

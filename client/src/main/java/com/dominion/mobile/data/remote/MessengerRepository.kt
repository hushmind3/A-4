package com.dominion.mobile.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class MessengerRepository(private val api:DominionApi,private val journal:MobileExtensionRepository) {
    private suspend fun owner()=api.mobileCapabilities().requireFeature("messenger_v2")
    suspend fun threads():MessengerDirectory { val account=owner();return api.messengerThreads().verified(account) }
    private suspend fun thread(account:String,id:String)=api.messengerThreads().verified(account).threads.orEmpty()
        .single { it.id==id }
    suspend fun messages(id:String,query:String?,before:String?):MessengerPage {
        require(validMobileId(id) && (query?.length?:0)<=200 && (before==null || validMobileId(before)))
        val account=owner()
        val actual=thread(account,id)
        return api.messengerMessages(id,query,before).verified(account,actual)
    }
    suspend fun upload(id:String,name:String,mime:String,bytes:ByteArray):MessengerAttachment {
        val account=owner();thread(account,id)
        require(bytes.size in 1..8*1024*1024)
        val hash=com.dominion.mobile.data.VisualGesture.digest(bytes)
        val clean=name.substringAfterLast('/').substringAfterLast('\\').take(240)
        val meta=MessengerAttachment("upload",account,id,clean,mime,bytes.size.toLong(),hash).verified(account,id)
        val result=api.messengerUpload(id,MessengerUpload(account,requireNotNull(meta.name),mime,
            bytes.size.toLong(),hash,java.util.Base64.getEncoder().encodeToString(bytes))).verified(account,id)
        check(result.name==meta.name && result.size==meta.size && result.sha256==hash && owner()==account)
        return result
    }
    suspend fun download(id:String,attachment:MessengerAttachment):ByteArray {
        val account=owner();thread(account,id);attachment.verified(account,id)
        val body=api.messengerDownload(id,requireNotNull(attachment.id))
        val bytes=withContext(Dispatchers.IO) {
            body.use {
                val out=java.io.ByteArrayOutputStream()
                val buffer=ByteArray(8192)
                val input=it.byteStream()
                while(true){val n=input.read(buffer);if(n<0)break;require(out.size()+n<=attachment.size);out.write(buffer,0,n)}
                out.toByteArray()
            }
        }
        check(bytes.size.toLong()==attachment.size && com.dominion.mobile.data.VisualGesture.digest(bytes)==attachment.sha256 &&
            owner()==account)
        return bytes
    }
    suspend fun operation(action:MobileAction,target:String,payload:MessengerPayload):MobileJournalEntry {
        val account=owner()
        payload.validate(action,account,target)
        if(action==MobileAction.GROUP_CREATE) {
            val friends=api.mobilePeers().verified(account).filter{it.relationship=="friend"}.mapNotNull{it.id}.toSet()
            check(payload.members.orEmpty().all{it in friends})
        } else thread(account,target)
        check(owner()==account)
        return journal.begin(action,target,null,payload)
    }
}

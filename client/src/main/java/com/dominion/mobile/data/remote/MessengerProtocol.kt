package com.dominion.mobile.data.remote

data class MessengerPayload(val title:String?=null,val members:List<String>?=null,
    val text:String?=null,val attachments:List<String>?=null,val messageId:String?=null) {
    fun validate(kind:MobileAction,owner:String,target:String) {
        when(kind) {
            MobileAction.GROUP_CREATE -> {
                require(target=="group" && !title.isNullOrBlank() && title.length<=120 &&
                    members!=null && members.size in 2..30 && members.distinct().size==members.size &&
                    members.all { validMobileId(it) && it!=owner })
                require(text==null && attachments==null && messageId==null)
            }
            MobileAction.THREAD_SEND -> {
                val ids=attachments.orEmpty()
                require(!text.isNullOrBlank() || ids.isNotEmpty())
                require((text?.length?:0)<=8000 && ids.size<=5 && ids.distinct().size==ids.size && ids.all(::validMobileId))
                require(title==null && members==null && messageId==null)
            }
            MobileAction.THREAD_READ -> require(messageId?.let(::validMobileId)==true &&
                title==null && members==null && text==null && attachments==null)
            else -> error("Not a messenger operation")
        }
    }
}

data class MessengerThread(val id:String?=null,val title:String?=null,val kind:String?=null,
    val members:List<String>?=null) {
    fun verified(owner:String):MessengerThread {
        require(id?.let(::validMobileId)==true && !title.isNullOrBlank() && title.length<=120 &&
            kind in setOf("direct","group"))
        val ids=members.orEmpty()
        require(owner in ids && ids.size in 2..31 && ids.distinct().size==ids.size && ids.all(::validMobileId))
        if(kind=="direct")require(ids.size==2)
        return this
    }
}
data class MessengerDirectory(val accountId:String?=null,val threads:List<MessengerThread>?=null) {
    fun verified(owner:String):MessengerDirectory {
        check(accountId==owner)
        val list=threads.orEmpty()
        require(list.size<=1000 && list.distinctBy{it.id}.size==list.size)
        list.forEach{it.verified(owner)}
        return this
    }
}
data class MessengerAttachment(val id:String?=null,val accountId:String?=null,val threadId:String?=null,
    val name:String?=null,val mime:String?=null,val size:Long=0,val sha256:String?=null) {
    fun verified(owner:String,thread:String):MessengerAttachment {
        check(accountId==owner && threadId==thread)
        require(id?.let(::validMobileId)==true && !name.isNullOrBlank() && name.length<=240 &&
            name.none{it.isISOControl() || it=='/' || it=='\\'} &&
            !mime.isNullOrBlank() && mime.length<=100 && mime.none(Char::isISOControl) &&
            size in 1..(8L*1024*1024) && sha256?.matches(Regex("[a-f0-9]{64}"))==true)
        return this
    }
}
data class MessengerMessage(val id:String?=null,val senderId:String?=null,val text:String?=null,
    val createdAt:Long=0,val readBy:List<String>?=null,val attachments:List<MessengerAttachment>?=null)
data class MessengerPage(val accountId:String?=null,val threadId:String?=null,
    val messages:List<MessengerMessage>?=null,val nextBefore:String?=null) {
    fun verified(owner:String,thread:MessengerThread):MessengerPage {
        check(accountId==owner && threadId==thread.id)
        val members=thread.verified(owner).members.orEmpty()
        val list=messages.orEmpty()
        require(list.size<=200 && list.distinctBy{it.id}.size==list.size)
        for(row in list) {
            require(row.id?.let(::validMobileId)==true && row.senderId in members && row.createdAt>0 &&
                (row.text?.length?:0)<=32000 && (!row.text.isNullOrBlank() || !row.attachments.isNullOrEmpty()))
            val read=row.readBy.orEmpty()
            require(read.distinct().size==read.size && read.all{it in members})
            val files=row.attachments.orEmpty()
            require(files.size<=5 && files.distinctBy{it.id}.size==files.size)
            files.forEach{it.verified(owner,requireNotNull(thread.id))}
        }
        require(nextBefore==null || validMobileId(nextBefore))
        return this
    }
}
data class MessengerUpload(val accountId:String,val name:String,val mime:String,val size:Long,val sha256:String,val base64:String)

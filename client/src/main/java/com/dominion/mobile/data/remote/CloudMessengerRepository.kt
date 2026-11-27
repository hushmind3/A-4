package com.dominion.mobile.data.remote

import com.google.gson.Gson
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

data class CloudMessengerProfile(val accountId:String,val handle:String,val displayName:String)
data class CloudMessengerFriends(val accepted:List<CloudMessengerProfile> = emptyList(),
    val outgoing:List<CloudMessengerProfile> = emptyList(),
    val incoming:List<CloudMessengerProfile> = emptyList())
data class CloudMessengerMember(val accountId:String,val handle:String?=null,val displayName:String?=null)
data class CloudMessengerThread(val id:String,val kind:String,val title:String,val createdBy:String,
    val createdAt:Long,val updatedAt:Long,val lastText:String?=null,val lastSender:String?=null,
    val lastAt:Long?=null,val unread:Int=0,val members:List<CloudMessengerMember> = emptyList())
data class CloudMessengerMessage(val id:String,val threadId:String,val senderId:String,
    val senderName:String?=null,val text:String,val createdAt:Long)
data class CloudMessengerPage(val threadId:String,val messages:List<CloudMessengerMessage>,
    val nextBefore:String?=null)

/**
 * D1-backed consumer messenger. Text and metadata only; no local server and no
 * PC engine is involved. The bearer is copied only for the duration of a call.
 */
class CloudMessengerRepository(private val account: CloudAccountRepository) {
    private val gson=Gson()
    private val client=OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
        .connectTimeout(12,TimeUnit.SECONDS).readTimeout(30,TimeUnit.SECONDS)
        .callTimeout(35,TimeUnit.SECONDS).retryOnConnectionFailure(false).build()
    private val sseClient=OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
        .connectTimeout(12,TimeUnit.SECONDS).readTimeout(0,TimeUnit.MILLISECONDS)
        .callTimeout(0,TimeUnit.MILLISECONDS).retryOnConnectionFailure(false).build()

    private suspend fun call(path:String,method:String="GET",body:String?=null):String {
        val token=account.withSessionToken{it}
        val builder=Request.Builder().url(CLOUD_ORIGIN+path).header("Authorization","Bearer "+token)
            .header("Accept","application/json").header("X-Client-Platform","android")
        val request=when(method){
            "GET"->builder.get().build()
            "POST"->builder.post((body?:"{}").toRequestBody("application/json".toMediaType())).build()
            "PUT"->builder.put((body?:"{}").toRequestBody("application/json".toMediaType())).build()
            else->error("Unsupported method")
        }
        return withContext(Dispatchers.IO){
            client.newCall(request).execute().use{response->
                val text=response.body?.string().orEmpty()
                if(!response.isSuccessful){
                    val detail=runCatching{JsonParser.parseString(text).asJsonObject.get("error")?.asString}
                        .getOrNull()?.take(160).orEmpty()
                    error("DOMINION 메신저 오류 "+(response.code)+(if(detail.isBlank())"" else " · "+detail))
                }
                require(text.length<=200_000){"메신저 응답이 너무 큽니다"}
                text
            }
        }
    }

    private fun objectAt(raw:String,key:String)=
        JsonParser.parseString(raw).asJsonObject.getAsJsonObject(key)

    suspend fun profile():CloudMessengerProfile?{
        val root=JsonParser.parseString(call("/v1/messenger/profile")).asJsonObject
        val row=root.get("profile")
        if(row==null||row.isJsonNull)return null
        return gson.fromJson(row,CloudMessengerProfile::class.java).also(::validProfile)
    }

    suspend fun saveProfile(handle:String,displayName:String):CloudMessengerProfile{
        val cleanHandle=handle.trim().lowercase()
        val cleanName=displayName.trim()
        require(cleanHandle.matches(Regex("[a-z0-9_]{3,24}")))
        require(cleanName.length in 1..40)
        val body=gson.toJson(mapOf("handle" to cleanHandle,"display_name" to cleanName))
        val response=objectAt(call("/v1/messenger/profile","PUT",body),"profile")
        return gson.fromJson(response,CloudMessengerProfile::class.java).also(::validProfile)
    }

    suspend fun search(query:String):List<CloudMessengerProfile>{
        require(query.length<=40)
        val root=JsonParser.parseString(call("/v1/messenger/directory?q="+
            java.net.URLEncoder.encode(query,"UTF-8"))).asJsonObject
        return root.getAsJsonArray("profiles").map{
            gson.fromJson(it,CloudMessengerProfile::class.java).also(::validProfile)
        }
    }

    suspend fun friends():CloudMessengerFriends{
        val root=JsonParser.parseString(call("/v1/messenger/friends")).asJsonObject
        fun rows(key:String)=root.getAsJsonArray(key)?.map{
            gson.fromJson(it,CloudMessengerProfile::class.java).also(::validProfile)
        }.orEmpty()
        return CloudMessengerFriends(rows("accepted"),rows("outgoing"),rows("incoming"))
    }

    suspend fun requestFriend(accountId:String):String{
        require(accountId.length in 3..200)
        val body=gson.toJson(mapOf("account_id" to accountId))
        val root=JsonParser.parseString(call("/v1/messenger/friends/request","POST",body)).asJsonObject
        return root.get("status")?.asString?.also{require(it in setOf("pending","accepted"))}
            ?:error("Invalid friend status")
    }

    suspend fun respondFriend(accountId:String,accept:Boolean):String?{
        require(accountId.length in 3..200)
        val body=gson.toJson(mapOf("account_id" to accountId,"accept" to accept))
        val root=JsonParser.parseString(call("/v1/messenger/friends/respond","POST",body)).asJsonObject
        require(root.get("status")?.asString in setOf("accepted","declined"))
        return root.get("threadId")?.takeUnless{it.isJsonNull}?.asString
    }

    suspend fun threads():List<CloudMessengerThread>{
        val root=JsonParser.parseString(call("/v1/messenger/threads")).asJsonObject
        return root.getAsJsonArray("threads").map{
            gson.fromJson(it,CloudMessengerThread::class.java).also(::validThread)
        }
    }

    suspend fun createThread(kind:String,title:String,members:List<String>):CloudMessengerThread{
        require(kind in setOf("direct","group"))
        require(members.isNotEmpty()&&members.size<=29&&members.distinct().size==members.size)
        val body=gson.toJson(mapOf("kind" to kind,"title" to title.take(80),"members" to members))
        val response=objectAt(call("/v1/messenger/threads","POST",body),"thread")
        return gson.fromJson(response,CloudMessengerThread::class.java).also(::validThread)
    }

    suspend fun messages(thread:String,before:String?=null):CloudMessengerPage{
        require(thread.matches(Regex("[a-f0-9-]{36}")))
        val suffix=before?.let{"?before="+java.net.URLEncoder.encode(it,"UTF-8")}.orEmpty()
        val page=gson.fromJson(call("/v1/messenger/threads/"+thread+"/messages"+suffix),
            CloudMessengerPage::class.java)
        require(page.threadId==thread&&page.messages.size<=100)
        page.messages.forEach(::validMessage)
        page.nextBefore?.let{require(it.matches(Regex("[a-f0-9-]{36}")))}
        return page
    }

    suspend fun send(thread:String,text:String,clientId:String):CloudMessengerMessage{
        require(thread.matches(Regex("[a-f0-9-]{36}")))
        require(text.isNotBlank()&&text.length<=4000)
        require(clientId.matches(Regex("[a-f0-9-]{36}")))
        val body=gson.toJson(mapOf("text" to text,"client_id" to clientId))
        val message=gson.fromJson(call("/v1/messenger/threads/"+thread+"/messages","POST",body),
            CloudMessengerMessage::class.java)
        validMessage(message)
        require(message.threadId==thread)
        return message
    }

    suspend fun read(thread:String){
        require(thread.matches(Regex("[a-f0-9-]{36}")))
        call("/v1/messenger/threads/"+thread+"/read","POST","{}")
    }

    /** SSE tail; reconnects with the last observed timestamp. D1 remains authoritative. */
    fun events(after:Long):Flow<CloudMessengerMessage> = flow {
        var cursor=after.coerceAtLeast(0)
        while(currentCoroutineContext().isActive){
            val token=account.withSessionToken{it}
            val request=Request.Builder().url(CLOUD_ORIGIN+"/v1/messenger/events?after="+cursor)
                .header("Authorization","Bearer "+token).header("Accept","text/event-stream").get().build()
            val call=sseClient.newCall(request)
            val cancellation=currentCoroutineContext()[kotlinx.coroutines.Job]
                ?.invokeOnCompletion{call.cancel()}
            try{
                call.execute().use{response->
                    if(!response.isSuccessful)error("메신저 실시간 연결 실패 "+response.code)
                    val source=requireNotNull(response.body).source()
                    while(currentCoroutineContext().isActive){
                        val line=source.readUtf8Line()?:break
                        if(!line.startsWith("data: "))continue
                        val message=runCatching{
                            gson.fromJson(line.removePrefix("data: ").trim(),CloudMessengerMessage::class.java)
                        }.getOrNull()?:continue
                        validMessage(message)
                        // The server cursor has one-second resolution; emit equal
                        // timestamps and let the UI deduplicate by message ID.
                        cursor=maxOf(cursor,message.createdAt)
                        emit(message)
                    }
                }
            }catch(cancelled:kotlinx.coroutines.CancellationException){throw cancelled}
            catch(_:Exception){/* reconnect below */}
            finally{cancellation?.dispose();call.cancel()}
            delay(2000)
        }
    }

    private fun validProfile(p:CloudMessengerProfile):CloudMessengerProfile{
        require(p.accountId.isNotBlank()&&p.handle.matches(Regex("[a-z0-9_]{3,24}"))&&
            p.displayName.isNotBlank()&&p.displayName.length<=40)
        return p
    }
    private fun validThread(t:CloudMessengerThread):CloudMessengerThread{
        require(t.id.matches(Regex("[a-f0-9-]{36}"))&&t.kind in setOf("direct","group")&&
            t.title.isNotBlank()&&t.title.length<=80&&t.createdAt>0)
        require(t.members.size<=30&&t.members.distinctBy{it.accountId}.size==t.members.size)
        return t
    }
    private fun validMessage(m:CloudMessengerMessage):CloudMessengerMessage{
        require(m.id.matches(Regex("[a-f0-9-]{36}"))&&m.threadId.matches(Regex("[a-f0-9-]{36}"))&&
            m.senderId.isNotBlank()&&m.text.isNotBlank()&&m.text.length<=4000&&m.createdAt>0)
        return m
    }
}

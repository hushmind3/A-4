package com.dominion.mobile.data.remote

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong

/** SSE is only an invalidation signal; native room/memory GETs supply authoritative data. */
data class EngineChange(val roomId:String?=null,val sessionId:String?=null,val memory:Boolean=false,val catalog:Boolean=false)

internal fun engineChange(event:JsonObject):EngineChange? {
    fun target(row:JsonObject?,room:String?=null):EngineChange? {
        if(row==null)return null
        val session=row.string("sessionId")?:row.string("session_id")
        val id=room?:row.string("roomId")?:row.obj("metadata")?.string("roomId")?:row.obj("input")?.string("roomId")
        return if(!id.isNullOrBlank()||!session.isNullOrBlank())EngineChange(id,session)else null
    }
    return when(event.string("type")){
        "message"->target(event.obj("message"))
        "approval"->target(event.obj("approval"))
        "record"->when(event.string("kind")){
            "room","agent","provider","owner_profile"->EngineChange(catalog=true)
            "memory"->EngineChange(memory=true)
            "job","execution","execution_operation"->target(event.obj("record"))
            else->null
        }
        else->null
    }
}

internal fun engineChangeSignals(api:DominionApi,cursor:AtomicLong)=callbackFlow<EngineChange>{
    val call=api.eventStream(cursor.get())
    val reader=launch(Dispatchers.IO){
        try{
            val response=call.execute()
            if(!response.isSuccessful){response.errorBody()?.close();error("SSE unavailable")}
            requireNotNull(response.body()).use{body->
                require(body.contentType()?.toString()?.startsWith("text/event-stream")==true)
                trySend(EngineChange(catalog=true))
                val source=body.source()
                while(!source.exhausted()){
                    val line=source.readUtf8LineStrict(2L*1024*1024)
                    if(!line.startsWith("data:"))continue
                    val event=JsonParser.parseString(line.substring(5).trim()).asJsonObject
                    val seq=event.get("seq")?.asLong?:continue
                    if(seq<=cursor.get())continue
                    engineChange(event)?.let{trySend(it)}
                    cursor.set(seq)
                }
            }
            close(IllegalStateException("SSE disconnected"))
        }catch(e:CancellationException){throw e}
        catch(e:Exception){close(e)}
    }
    awaitClose{call.cancel();reader.cancel()}
}.buffer(32,BufferOverflow.DROP_OLDEST).retryWhen{cause,attempt->
    if(cause is CancellationException)false else{
        delay(minOf(30_000L,1000L*(1L shl attempt.coerceAtMost(5).toInt())))
        true
    }
}

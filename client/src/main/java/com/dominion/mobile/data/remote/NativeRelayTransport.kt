package com.dominion.mobile.data.remote

import com.dominion.mobile.data.local.EngineConnection
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import java.io.IOException
import java.util.Base64
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/** Retrofit-compatible bounded native RPC. No HTTP credential to the virtual origin,
 * no retry of an uncertain side effect, no public port on the PC. */
internal object NativeRelayTransport {
    private const val MAX=585_000
    private val client=OkHttpClient.Builder().connectTimeout(15,TimeUnit.SECONDS)
        .readTimeout(0,TimeUnit.MILLISECONDS).pingInterval(20,TimeUnit.SECONDS)
        .followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false).build()
    fun execute(chain:Interceptor.Chain,connection:EngineConnection):Response {
        val req=chain.request()
        fun response(code:Int,bytes:ByteArray,mime:String)=Response.Builder().request(req)
            .protocol(Protocol.HTTP_1_1).code(code).message("DOMINION relay")
            .header("Cache-Control","no-store").body(bytes.toResponseBody(mime.toMediaTypeOrNull())).build()
        fun error(code:Int,text:String)=response(code,JsonObject().apply{addProperty("error",text)}.toString().toByteArray(),"application/json")
        // SSE uses direct engine transport; the app's periodic snapshots remain active on relay.
        if(req.url.encodedPath=="/api/events")return error(501,"SSE not supported by bounded RPC")
        val path="/"+req.url.pathSegments.joinToString("/")+(req.url.encodedQuery?.let{"?"+it}.orEmpty())
        require(path.length<=8192 && path.all{it.code in 32..126} && !path.substringBefore('?').contains('%'))
        val body=req.body
        val bytes=if(body==null)ByteArray(0) else {
            val count=body.contentLength()
            if(count !in 0L..MAX.toLong())return error(413,"Relay request exceeds size limit")
            val buf=Buffer();body.writeTo(buf)
            if(buf.size>MAX)return error(413,"Relay request exceeds size limit")
            buf.readByteArray()
        }
        val id=UUID.randomUUID().toString()
        val payload=JsonObject().apply{
            addProperty("type","rpc");addProperty("id",id);addProperty("deviceId",requireNotNull(connection.relayDeviceId))
            addProperty("route","native");add("body",JsonObject().apply{
                addProperty("method",req.method);addProperty("path",path)
                addProperty("contentType",body?.contentType()?.toString().orEmpty())
                addProperty("base64",Base64.getEncoder().encodeToString(bytes))
            })
        }.toString()
        if(payload.toByteArray().size>840000)return error(413,"Relay message too large")
        val signal=CountDownLatch(1);val sent=AtomicBoolean(false)
        val result=AtomicReference<Response?>();val failure=AtomicReference<Throwable?>()
        val socket=client.newWebSocket(Request.Builder().url(CLOUD_ORIGIN.replace("https://","wss://")+"/v1/assistant/socket")
            .header("Authorization","Bearer "+requireNotNull(connection.relayToken)).build(),object:WebSocketListener(){
            override fun onMessage(webSocket:WebSocket,text:String){
                try{
                    val message=JsonParser.parseString(text).asJsonObject
                    when(message.get("type")?.asString){
                        "ready"->if(sent.compareAndSet(false,true)&&!webSocket.send(payload)){
                            failure.set(IOException("Relay send failed"));signal.countDown()
                        }
                        "result"->if(message.get("id")?.asString==id){
                            val code=message.get("status").asInt
                            require(code in 200..599)
                            val data=message.get("data")?.takeIf{it.isJsonObject}?.asJsonObject
                            val encoded=data?.get("base64")?.takeIf{it.isJsonPrimitive}?.asString
                            if(encoded!=null){
                                require(encoded.length<=780000)
                                val raw=Base64.getDecoder().decode(encoded);require(raw.size<=MAX)
                                val mime=data.get("contentType")?.asString.orEmpty().ifBlank{"application/octet-stream"}
                                result.set(response(code,raw,mime))
                            }else{
                                require(code>=400)
                                result.set(error(code,data?.get("error")?.asString.orEmpty().take(300)))
                            }
                            signal.countDown()
                        }
                    }
                }catch(e:Exception){failure.set(IOException("Invalid relay response",e));signal.countDown()}
            }
            override fun onFailure(webSocket:WebSocket,t:Throwable,response:Response?){
                if(response?.code in listOf(401,403))result.set(error(response!!.code,"Remote account approval required"))
                else failure.set(IOException("Cloud relay unavailable",t))
                response?.close();signal.countDown()
            }
            override fun onClosed(webSocket:WebSocket,code:Int,reason:String){signal.countDown()}
        })
        try{
            val deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(165)
            while(!signal.await(200,TimeUnit.MILLISECONDS)){
                if(chain.call().isCanceled()||System.nanoTime()>deadline)
                    throw IOException("Relay interrupted; inspect native room history before retrying")
            }
            return result.get()?:throw IOException("No confirmed native response",failure.get())
        }finally{socket.cancel()}
    }
}

package com.dominion.mobile.data.remote

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

internal fun JsonObject.string(name: String): String? =
    get(name)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString

internal fun JsonObject.obj(name: String): JsonObject? =
    get(name)?.takeIf { it.isJsonObject }?.asJsonObject

/** Never display global event payloads: they merely invalidate verified room reads. */
internal fun affectsRoom(event: JsonObject, roomId: String, sessionId: String): Boolean {
    fun sameSession(row: JsonObject): Boolean =
        (row.string("sessionId") ?: row.string("session_id")) == sessionId
    return when (event.string("type")) {
        "message" -> event.obj("message")?.let { row ->
            val room = row.obj("metadata")?.string("roomId")
            sameSession(row) && (room == null || room == roomId)
        } == true
        "approval" -> event.obj("approval")?.let(::sameSession) == true
        "record" -> event.obj("record")?.let { row ->
            when (event.string("kind")) {
                "room" -> row.string("id") == roomId
                "job" -> sameSession(row) && row.obj("input")?.string("roomId") == roomId
                "execution", "execution_operation" -> sameSession(row) &&
                    (row.string("roomId") == null || row.string("roomId") == roomId)
                else -> false
            }
        } == true
        else -> false
    }
}

/** Reconnect to the Rust journal cursor; cancellation closes the blocking HTTP read. */
internal fun roomEventSignals(api: DominionApi, roomId: String, sessionId: String,
    cursors: ConcurrentHashMap<Pair<String, String>, Long>): Flow<Unit> = callbackFlow {
    val key = roomId to sessionId
    var last = cursors[key] ?: 0L
    val call = api.eventStream(last)
    val reader = launch(Dispatchers.IO) {
        try {
            val response = call.execute()
            if (!response.isSuccessful) {
                response.errorBody()?.close()
                error("Event stream unavailable")
            }
            requireNotNull(response.body()).use { body ->
                require(body.contentType()?.toString()?.startsWith("text/event-stream") == true)
                trySend(Unit) // connection established; reconcile snapshot even without new events
                val source = body.source()
                while (!source.exhausted()) {
                    // The native server emits one JSON data line per SSE frame.
                    val line = source.readUtf8LineStrict(2L * 1024 * 1024)
                    if (!line.startsWith("data:")) continue
                    val event = JsonParser.parseString(line.substring(5).trim()).asJsonObject
                    val seq = event.get("seq")?.asLong ?: continue
                    if (seq <= last) continue
                    if (affectsRoom(event, roomId, sessionId)) trySend(Unit)
                    last = seq
                    cursors[key] = last
                }
            }
            close()
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (e: Exception) { close(e) }
    }
    awaitClose { call.cancel(); reader.cancel() }
}.conflate()

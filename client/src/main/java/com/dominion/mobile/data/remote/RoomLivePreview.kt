package com.dominion.mobile.data.remote

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.launch

data class LiveTurn(val turnId: String, val sourceMessageId: String, val agentId: String?,
    val text: String, val tool: String? = null)
data class RoomLiveUpdate(val connected: Boolean, val refreshVersion: Long, val turns: List<LiveTurn>)

/** Keep snapshots, not lossy raw token events. Only a known turn_start can open a preview. */
class RoomLiveReducer(private val roomId: String, private val sessionId: String) {
    var cursor: Long = 0
        private set
    private var refreshVersion = 0L
    private val turns = linkedMapOf<String, LiveTurn>()
    fun snapshot(connected: Boolean) = RoomLiveUpdate(connected, refreshVersion, turns.values.toList())
    fun connected(): RoomLiveUpdate { refreshVersion++; return snapshot(true) }

    fun accept(event: JsonObject): RoomLiveUpdate? {
        val seq = event.get("seq")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }
            ?.asLong ?: return null
        if (seq <= cursor) return null
        cursor = seq
        var changed = false
        if (affectsRoom(event, roomId, sessionId)) { refreshVersion++; changed = true }
        if (event.string("type") != "room_event" || event.string("roomId") != roomId ||
            event.string("sessionId") != sessionId) return if (changed) snapshot(true) else null
        val turnId = event.string("turnId")?.takeIf { it.isNotBlank() } ?: return null
        val nested = event.obj("event") ?: return null
        val current = turns[turnId]
        when (nested.string("type")) {
            "turn_start" -> {
                val source = nested.string("sourceMessageId")?.takeIf { it.isNotBlank() } ?: return null
                if (turns.size >= 32) turns.remove(turns.keys.first())
                turns[turnId] = LiveTurn(turnId, source, event.string("agentId"), "")
            }
            "text" -> {
                if (current == null || current.agentId != event.string("agentId")) return null
                val text = nested.string("text") ?: return null
                turns[turnId] = current.copy(text = (current.text + text).take(32000), tool = null)
            }
            "tool_start" -> {
                if (current == null) return null
                // Text preceding a tool round is transient, not the final response.
                turns[turnId] = current.copy(text = "", tool = nested.string("name"))
            }
            "tool_end", "tool_error" -> {
                if (current == null) return null
                turns[turnId] = current.copy(tool = null)
            }
            "assistant_saved", "done" -> {
                turns.remove(turnId)
                refreshVersion++
            }
            "error" -> {
                if (nested.get("terminal")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isBoolean }?.asBoolean != true)
                    return null
                turns.remove(turnId)
                refreshVersion++
            }
            else -> return if (changed) snapshot(true) else null
        }
        return snapshot(true)
    }
}

internal fun roomLiveUpdates(api: DominionApi, reducer: RoomLiveReducer): Flow<RoomLiveUpdate> = callbackFlow {
    val call = api.eventStream(reducer.cursor)
    val reader = launch(Dispatchers.IO) {
        try {
            val response = call.execute()
            if (!response.isSuccessful) { response.errorBody()?.close(); error("Event stream unavailable") }
            requireNotNull(response.body()).use { body ->
                require(body.contentType()?.toString()?.startsWith("text/event-stream") == true)
                trySend(reducer.connected())
                val source = body.source()
                while (!source.exhausted()) {
                    val line = source.readUtf8LineStrict(2L * 1024 * 1024)
                    if (!line.startsWith("data:")) continue
                    val event = JsonParser.parseString(line.substring(5).trim()).asJsonObject
                    reducer.accept(event)?.let { trySend(it) }
                }
            }
            close()
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { close(e) }
    }
    awaitClose { call.cancel(); reader.cancel() }
}.conflate()

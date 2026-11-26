package com.dominion.mobile.data.remote

import com.dominion.mobile.data.model.MemoryItem

/** Native memory collection. The mobile v1 projection is not used. */
data class EngineMemory(
    val id: String?, val text: String?, val agentId: String?,
    val scope: String?, val source: String?, val status: String?,
    val createdAt: String?, val retrievalEligible: Boolean?,
    val shareWithRooms: Boolean?, val shareWithChannels: Boolean?
) {
    fun toDomain(): MemoryItem? {
        val key = id?.takeIf { it.isNotBlank() } ?: return null
        val body = text?.takeIf { it.isNotBlank() } ?: return null
        if (status in setOf("deleted", "superseded", "archived")) return null
        return MemoryItem(key, agentId.orEmpty(), body,
            runCatching { java.time.Instant.parse(createdAt).toEpochMilli() }.getOrDefault(0),
            (source ?: "engine") + " · " + (scope ?: "shared"),
            serverBacked = true, retrievalEligible = retrievalEligible)
    }
}

data class MemoryWrite(
    val id: String, val text: String, val agentId: String,
    val createdAt: String, val type: String = "fact", val source: String = "owner",
    val scope: String = "agent", val status: String = "active",
    val shareWithRooms: Boolean = true, val shareWithChannels: Boolean = false
) {
    fun matches(row: EngineMemory) = row.id == id && row.text == text &&
        row.agentId == agentId && row.scope == scope && row.source == source &&
        row.status == status && row.shareWithRooms == shareWithRooms &&
        row.shareWithChannels == shareWithChannels

    companion object {
        fun from(item: MemoryItem): MemoryWrite {
            require(item.id.isNotBlank() && item.characterId.isNotBlank() && item.text.isNotBlank())
            return MemoryWrite(item.id, item.text, item.characterId,
                java.time.Instant.ofEpochMilli(item.createdAt).toString())
        }
    }
}

/** A server refresh must retain unsynced legacy and offline memories. */
fun mergeMemories(local: List<MemoryItem>, remote: List<MemoryItem>): List<MemoryItem> {
    val remoteIds = remote.map { it.id }.toSet()
    return (remote + local.filter { it.serverBacked != true && it.id !in remoteIds })
        .distinctBy { it.id }.sortedByDescending { it.createdAt }
}

data class RoomNameUpdate(val name: String)

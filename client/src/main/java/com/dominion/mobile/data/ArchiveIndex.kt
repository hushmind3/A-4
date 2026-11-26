package com.dominion.mobile.data

import com.dominion.mobile.data.model.*
import java.net.URI
import java.nio.charset.StandardCharsets
import java.util.UUID

object ArchiveIndex {
    fun safeUrl(value: String?): String? = value?.trim()?.takeIf {
        runCatching {
            val uri = URI(it)
            uri.scheme in setOf("https", "http") && !uri.host.isNullOrBlank() && uri.userInfo == null
        }.getOrDefault(false)
    }

    private fun key(room: String, kind: String, value: String) =
        UUID.nameUUIDFromBytes((room + "|" + kind + "|" + value).toByteArray(StandardCharsets.UTF_8)).toString()

    fun fromMessage(message: Message): List<ArchiveItem> {
        val known = message.attachments.orEmpty().associateBy { it.id }
        val files = message.attachmentIds.orEmpty().distinct().mapIndexedNotNull { index, id ->
            if (runCatching { UUID.fromString(id) }.isFailure) return@mapIndexedNotNull null
            val actual = known[id]
            val mime = actual?.mimeType ?: if (index == 0 && message.card?.kind == CardKind.IMAGE) "image/*"
                else "application/octet-stream"
            val type = when {
                mime.startsWith("image/") -> ArchiveType.IMAGE
                mime.startsWith("video/") -> ArchiveType.VIDEO
                else -> ArchiveType.FILE
            }
            ArchiveItem(key(message.conversationId, "file", id), type,
                actual?.name ?: message.card?.title?.takeIf { index == 0 && it.isNotBlank() } ?: "attachment-" + id,
                actual?.let { it.size.toString() + " bytes" } ?: "DOMINION attachment",
                message.conversationId, message.createdAt, messageId = message.id,
                attachmentId = id, mimeType = mime)
        }
        val textLinks = Regex("https?://[^\\s<>\"\\[\\]]+").findAll(message.text).map {
            it.value.trimEnd('.', ',', ')', ';', '!', '?')
        }.toList()
        val cardLinks = if (message.card?.kind == CardKind.LINK)
            listOf(message.card.title, message.card.subtitle, message.card.meta) else emptyList()
        val links = (textLinks + cardLinks).mapNotNull(::safeUrl).distinct().map { url ->
            ArchiveItem(key(message.conversationId, "link", url), ArchiveType.LINK,
                URI(url).host, url, message.conversationId, message.createdAt,
                messageId = message.id, url = url)
        }
        return files + links
    }

    fun roomItems(messages: List<Message>): List<ArchiveItem> =
        messages.asReversed().flatMap(::fromMessage).distinctBy { it.id }.sortedByDescending { it.createdAt }
}

package com.dominion.mobile.data.remote

import com.google.gson.JsonObject
import com.google.gson.JsonElement
import java.net.URI
import java.util.UUID

data class ServiceAccount(val id: String?, val name: String?, val configured: Boolean = false,
    val connected: Boolean = false, val account: String? = null, val scopes: String? = null,
    val grantedScopes: JsonElement? = null, val callback: String? = null) {
    fun hasScope(scope: String): Boolean {
        val value = grantedScopes ?: return false
        val granted = if (value.isJsonArray) value.asJsonArray.mapNotNull {
            it.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString
        } else if (value.isJsonPrimitive && value.asJsonPrimitive.isString)
            value.asString.split(Regex("[,\\s]+")) else emptyList()
        return scope in granted
    }
}
data class ServiceConnector(val id: String?, val name: String?, val url: String?,
    val enabled: Boolean? = null, val transport: String? = null, val approval: Boolean? = null,
    val mobileConnectionId: String? = null)

data class ConnectorRegistration(val id: String, val name: String, val url: String,
    val mobileConnectionId: String? = null) {
    fun validate() {
        require(UUID.fromString(id).toString() == id)
        require(name.trim().isNotEmpty() && name.length <= 100 && name.none { it.isISOControl() })
        val uri = URI(url)
        require(uri.scheme == "https" && !uri.host.isNullOrBlank() &&
            uri.userInfo == null && uri.fragment == null && uri.rawQuery == null &&
            (uri.port == -1 || uri.port in 1..65535))
        require(url.length <= 2048)
    }
    fun record(token: String): JsonObject {
        validate()
        require(token.length <= 8192 && token.none { it.isWhitespace() || it.isISOControl() })
        return JsonObject().apply {
            addProperty("id", id)
            addProperty("name", name.trim())
            addProperty("url", url)
            addProperty("transport", "http")
            addProperty("enabled", true)
            addProperty("approval", true)
            mobileConnectionId?.let { addProperty("mobileConnectionId", it) }
            if (token.isNotEmpty()) addProperty("apiKey", token)
        }
    }
}

/** The account registry is authoritative. A Google identity alone does not grant mail/drive/calendar access. */
fun ServiceAccount.authorizes(provider: String): Boolean {
    if (!connected) return false
    val prefix = "https://www.googleapis.com/auth/"
    return when (provider) {
        "github" -> id == "github"
        "gmail" -> id == "google" && listOf("gmail.readonly", "gmail.modify", "gmail.compose", "gmail.send")
            .any { hasScope(prefix + it) }
        "google_calendar" -> id == "google" && listOf("calendar", "calendar.events", "calendar.readonly", "calendar.events.readonly")
            .any { hasScope(prefix + it) }
        "google_drive" -> id == "google" && listOf("drive", "drive.readonly", "drive.file")
            .any { hasScope(prefix + it) }
        else -> id == provider
    }
}
fun serviceAccountId(provider: String): String =
    if (provider in setOf("gmail", "google_calendar", "google_drive")) "google" else provider

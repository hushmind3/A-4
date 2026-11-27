package com.dominion.mobile.data

import com.google.gson.JsonParser
import com.google.gson.JsonObject
import java.util.Base64

/** Portable contact details only. Never a server credential, identity proof or account grant. */
data class LocalContactCard(val name: String, val phone: String? = null, val email: String? = null) {
    fun validated(): LocalContactCard {
        val n = name.trim()
        require(n.isNotBlank() && n.length <= 80 && n.none { it.isISOControl() })
        val p = phone?.takeIf { it.isNotBlank() }?.let(ContactTarget::phone)
        val e = email?.takeIf { it.isNotBlank() }?.let(ContactTarget::email)
        require(p != null || e != null)
        return LocalContactCard(n, p, e)
    }
    fun encode(): String {
        val v = validated()
        val json = JsonObject().apply {
            addProperty("name", v.name)
            v.phone?.let { addProperty("phone", it) }
            v.email?.let { addProperty("email", it) }
        }
        return PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(json.toString().toByteArray(Charsets.UTF_8))
    }
    companion object {
        const val PREFIX = "dominion-contact:v1:"
        fun decode(value: String): LocalContactCard {
            require(value.startsWith(PREFIX) && value.length <= 2048)
            val bytes = Base64.getUrlDecoder().decode(value.removePrefix(PREFIX))
            require(bytes.size <= 1400)
            val row = JsonParser.parseString(bytes.toString(Charsets.UTF_8)).asJsonObject
            require(row.keySet().all { it in setOf("name", "phone", "email") })
            fun field(name: String): String? {
                val element = row.get(name) ?: return null
                require(element.isJsonPrimitive && element.asJsonPrimitive.isString)
                return element.asString
            }
            return LocalContactCard(requireNotNull(field("name")), field("phone"), field("email")).validated()
        }
    }
}

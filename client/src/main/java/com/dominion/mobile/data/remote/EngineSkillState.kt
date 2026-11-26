package com.dominion.mobile.data.remote

/** Only display actual stored activation. Does not imply an executable CLI, token or API key. */
data class EngineSkillState(val id: String?, val name: String?, val enabled: Boolean?, val status: String?) {
    fun isActive(): Boolean = enabled == true && status == "active"
}

data class SkillReview(val id: String, val name: String, val instructions: String,
    val status: String?, val enabled: Boolean, val revision: String) {
    companion object {
        fun fromRecord(row: com.google.gson.JsonObject): SkillReview {
            fun value(key: String): String? = row.get(key)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString
            val id = requireNotNull(value("id")).also { require(it.isNotBlank()) }
            val name = requireNotNull(value("name"))
            val instructions = requireNotNull(value("instructions")).also { require(it.isNotBlank()) }
            val digest = java.security.MessageDigest.getInstance("SHA-256")
                .digest(row.toString().toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it.toInt() and 255) }
            return SkillReview(id, name, instructions, value("status"),
                row.get("enabled")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isBoolean }?.asBoolean == true,
                digest)
        }
    }
}

package com.dominion.mobile.data.remote

/** Only display actual stored activation. Does not imply an executable CLI, token or API key. */
data class EngineSkillState(val id: String?, val name: String?, val enabled: Boolean?, val status: String?) {
    fun isActive(): Boolean = enabled == true && status == "active"
}

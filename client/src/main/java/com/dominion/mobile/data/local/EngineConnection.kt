package com.dominion.mobile.data.local

import java.net.URI

class EngineConnection(val origin: String, val ownerToken: String,
    val accessClientId: String? = null, val accessClientSecret: String? = null) {
    companion object {
        fun parse(origin: String, token: String, accessClientId: String? = null,
            accessClientSecret: String? = null): EngineConnection {
            val uri = URI(origin.trim())
            require(uri.scheme == "https" && !uri.host.isNullOrBlank() &&
                uri.userInfo == null && uri.query == null && uri.fragment == null &&
                (uri.path.isNullOrEmpty() || uri.path == "/") &&
                (uri.port == -1 || uri.port in 1..65535)) { "HTTPS origin required" }
            require(uri.host.lowercase() != "dominion-cloud.dominion-hushmind.workers.dev") {
                "Cloud inference gateway is not an engine server"
            }
            require(token.isNotBlank() && token.length <= 4096 &&
                token.all { it.code in 33..126 } && !token.startsWith("dapp_")) { "Engine owner token required" }
            val id = accessClientId?.trim()?.takeIf { it.isNotBlank() }
            val secret = accessClientSecret?.trim()?.takeIf { it.isNotBlank() }
            require((id == null) == (secret == null)) { "Both Cloudflare Access credentials are required" }
            require(listOfNotNull(id, secret).all { it.length <= 4096 && it.all { ch -> ch.code in 33..126 } }) {
                "Invalid Cloudflare Access credential"
            }
            return EngineConnection(URI("https", null, uri.host.lowercase(), uri.port, "/", null, null).toString(),
                token, id, secret)
        }
    }
    override fun toString() = "EngineConnection(origin=$origin)"
}

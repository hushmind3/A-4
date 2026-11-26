package com.dominion.mobile.data.local

import java.net.URI

class EngineConnection(val origin: String, val ownerToken: String) {
    companion object {
        fun parse(origin: String, token: String): EngineConnection {
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
            return EngineConnection(URI("https", null, uri.host.lowercase(), uri.port, "/", null, null).toString(), token)
        }
    }
    override fun toString() = "EngineConnection(origin=$origin)"
}

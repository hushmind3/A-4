package com.dominion.mobile.data.local

import java.net.URI

class EngineConnection(val origin: String, val ownerToken: String,
    val accessClientId: String? = null, val accessClientSecret: String? = null,
    val relayDeviceId:String?=null,val relayAccountId:String?=null,val relayToken:String?=null) {
    val isRelay:Boolean get()=relayDeviceId!=null
    fun validated():EngineConnection =
        if(isRelay)relay(requireNotNull(relayDeviceId),requireNotNull(relayAccountId),requireNotNull(relayToken)).also{
            require(it.origin==origin && it.ownerToken==ownerToken && accessClientId==null && accessClientSecret==null)
        } else {
            require(relayAccountId==null && relayToken==null)
            parse(origin,ownerToken,accessClientId,accessClientSecret)
        }
    companion object {
        fun relay(device:String,account:String,token:String):EngineConnection {
            require(java.util.UUID.fromString(device).toString()==device)
            require(account.isNotBlank() && account.length<=256 && account.none(Char::isISOControl))
            require(token.matches(Regex("dapp_[A-Za-z0-9_-]{43}")))
            val digest=java.security.MessageDigest.getInstance("SHA-256").digest((account+"\u0000"+token).toByteArray())
                .joinToString(""){"%02x".format(it.toInt() and 255)}
            // Virtual origin is never sent over HTTP. Only the fixed relay origin is contacted.
            return EngineConnection("https://"+device+".relay.invalid/","relay:"+digest,
                relayDeviceId=device,relayAccountId=account,relayToken=token)
        }
        fun parse(origin: String, token: String, accessClientId: String? = null,
            accessClientSecret: String? = null): EngineConnection {
            val uri = URI(origin.trim())
            require(uri.scheme == "https" && !uri.host.isNullOrBlank() &&
                uri.userInfo == null && uri.query == null && uri.fragment == null &&
                (uri.path.isNullOrEmpty() || uri.path == "/") &&
                (uri.port == -1 || uri.port in 1..65535)) { "HTTPS origin required" }
            require(uri.host.lowercase() != "dominion-cloud.dominion-hushmind.workers.dev" &&
                !uri.host.lowercase().endsWith(".relay.invalid")) {
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

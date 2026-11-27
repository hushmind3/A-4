package com.dominion.mobile.data.local

import java.security.MessageDigest

internal data class EngineScopeRegistry(val entries:Map<String,String> = emptyMap()){
    fun resolve(engine:EngineConnection,legacy:Boolean):Pair<EngineScopeRegistry,String>{
        val key=digest(engine.origin+"\u0000"+engine.ownerToken+"\u0000"+engine.accessClientId.orEmpty()+"\u0000"+engine.accessClientSecret.orEmpty())
        entries[key]?.let{
            require(it.matches(Regex("[a-f0-9]{64}")))
            return this to it
        }
        // Only the active credential already saved during upgrade gets the old
        // per-origin cache. New/different credentials never open that cache.
        val old=digest(engine.origin)
        val scope=if(legacy && old !in entries.values)old else digest("dominion-credential-cache:"+key)
        require(entries.size<128 && scope !in entries.values)
        return copy(entries=entries+(key to scope)) to scope
    }
    private fun digest(s:String)=MessageDigest.getInstance("SHA-256").digest(s.toByteArray(Charsets.UTF_8))
        .joinToString(""){"%02x".format(it.toInt() and 255)}
}

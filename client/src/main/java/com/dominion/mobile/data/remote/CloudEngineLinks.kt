package com.dominion.mobile.data.remote

import com.dominion.mobile.data.local.EngineConnection

/** Device-only encrypted association; not a server directory or OAuth authorization grant. */
internal class CloudEngineLink(val accountId:String,val connection:EngineConnection,val autoReconnect:Boolean=true)
internal data class CloudEngineLinks(val entries:List<CloudEngineLink> = emptyList()) {
    fun forAccount(account:String):CloudEngineLink? {
        require(account.isNotBlank()&&account.length<=256)
        return entries.singleOrNull{it.accountId==account}
    }
    fun bind(account:String,connection:EngineConnection):CloudEngineLinks {
        require(account.isNotBlank()&&account.length<=256)
        val checked=connection.validated()
        return CloudEngineLinks((entries.filterNot{it.accountId==account}+CloudEngineLink(account,checked)).takeLast(8))
    }
    fun forget(account:String)=CloudEngineLinks(entries.filterNot{it.accountId==account})
    fun pause(connection:EngineConnection)=CloudEngineLinks(entries.map{link->
        if(same(link.connection,connection))CloudEngineLink(link.accountId,link.connection,false) else link
    })
    private fun same(a:EngineConnection,b:EngineConnection)=a.origin==b.origin && a.ownerToken==b.ownerToken &&
        a.accessClientId==b.accessClientId && a.accessClientSecret==b.accessClientSecret
}

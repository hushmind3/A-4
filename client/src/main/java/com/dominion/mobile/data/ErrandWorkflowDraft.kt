package com.dominion.mobile.data

import com.google.gson.JsonParser
import com.dominion.mobile.data.model.AppState
import com.dominion.mobile.platform.DeviceActions

data class ErrandStage(val title:String,val agentId:String,val request:String,val app:String?,
    val transaction:PhoneTransactionRequest?=null,val message:KakaoMessageRequest?=null){
    val external:Boolean get()=transaction!=null||message!=null
    fun checked(agents:Set<String>,apps:Set<String>):ErrandStage{
        require(title.isNotBlank()&&title.length<=100&&agentId in agents&&request.isNotBlank()&&request.length<=1300)
        require(transaction==null||message==null)
        if(external){
            val pkg=requireNotNull(app)
            require(pkg in apps)
            transaction?.let{
                require(!PhoneTransactions.deviceOnly(it.kind))
                it.checked(pkg)
            }
            message?.let{require(it.app==pkg&&it.negotiationGoal==null&&!it.waitForReply);it.checked()}
        }else require(app==null||app in apps&&app in PhonePublicResult.packages)
        return this
    }
}
data class ErrandWorkflowDraft(val id:String,val goal:String,val stages:List<ErrandStage>){
    fun checked(agents:Set<String>,apps:Set<String>):ErrandWorkflowDraft{
        require(id.matches(Regex("[a-f0-9-]{36}"))&&goal.length in 3..1500&&stages.size in 2..4)
        stages.forEach{it.checked(agents,apps)}
        // A model cannot authorize the first stage to make an external change.
        // Research must precede any generated external operation.
        require(!stages.first().external)
        require(stages.distinct().size==stages.size)
        return this
    }
    companion object{
        // Runtime-owned message; the UI binds the proposal to the original user turn.
        const val CHAT_OFFER="복합 업무로 나누어 실행 계획을 검토할 수 있습니다."
        fun parse(goal:String,raw:String,agents:Set<String>,apps:Set<String>):ErrandWorkflowDraft{
            require(raw.length<=12000)
            val o=JsonParser.parseString(raw.trim().removePrefix("```json").removeSuffix("```").trim()).asJsonObject
            require(o.keySet()==setOf("stages"))
            val stages=o.getAsJsonArray("stages").map{item->
                val s=item.asJsonObject
                require(s.keySet() in setOf(setOf("title","agentId","request","app"),
                    setOf("title","agentId","request","app","transaction","message")))
                fun str(o:com.google.gson.JsonObject,k:String)=o.get(k).also{require(it.isJsonPrimitive&&it.asJsonPrimitive.isString)}.asString.trim()
                val pkg=if(s.get("app").isJsonNull)null else str(s,"app")
                val tx=s.get("transaction")?.takeUnless{it.isJsonNull}?.asJsonObject?.let{t->
                    require(t.keySet()==setOf("kind","target","condition","maxWon"))
                    val n=t.get("maxWon").asJsonPrimitive
                    require(n.isNumber&&n.asString.matches(Regex("[0-9]{1,9}")))
                    PhoneTransactionRequest(str(t,"kind"),str(t,"target"),str(t,"condition"),n.asLong)
                }
                val message=s.get("message")?.takeUnless{it.isJsonNull}?.asJsonObject?.let{m->
                    require(m.keySet()==setOf("recipient","body","listing"))
                    KakaoMessageRequest(str(m,"recipient"),str(m,"body"),requireNotNull(pkg),
                        if(m.get("listing").isJsonNull)null else str(m,"listing"))
                }
                ErrandStage(str(s,"title"),str(s,"agentId"),str(s,"request"),pkg,tx,message)
            }
            return ErrandWorkflowDraft(java.util.UUID.randomUUID().toString(),goal,stages).checked(agents,apps)
        }
    }
}

/** Learn routing statistics only from recorded outcomes. No personal screen,
 * text, arguments, message content or financial data are reused as experience. */
internal object ExecutionExperience{
    fun tools(state:AppState):String=state.mobileActions.orEmpty().takeLast(100)
        .filter{it.tool in setOf("web.read","web.search","browser.read","browser.links","korea.lookup","memory.search","schedule.list")
            &&it.status in setOf("completed","failed")}
        .groupBy{it.tool}.map{(tool,rows)->Triple(tool,rows.count{it.status=="completed"},rows.size)}
        .filter{it.third>=2}.sortedByDescending{it.third}.take(8)
        .joinToString("; "){(name,ok,total)->"$name $ok/$total successful observations"}
    /** Transfer actual successful fallback routes between assistants in this
     * account. Never transfer arguments, output text, or authorization. */
    fun recoveryRoutes(state:AppState):String{
        val allowed=setOf("web.read","web.search","browser.read","browser.links","korea.lookup")
        val groups=state.mobileActions.orEmpty().takeLast(100).filter{it.tool in allowed}
            .groupBy{Triple(it.roomId,it.session,it.messageId)}
        val edges=mutableMapOf<Pair<String,String>,Pair<Int,Int>>()
        for(rows in groups.values){
            val ordered=rows.sortedWith(compareBy<com.dominion.mobile.data.harness.MobileActionRecord>{it.createdAt}.thenBy{it.id})
            for((failed,next) in ordered.zipWithNext()){
                if(failed.status!="failed"||failed.tool==next.tool||next.status !in setOf("completed","failed"))continue
                val edge=failed.tool to next.tool
                val counts=edges[edge]?: (0 to 0)
                edges[edge]=(counts.first+(if(next.status=="completed")1 else 0)) to (counts.second+1)
            }
        }
        return edges.entries.filter{it.value.first>=1}.sortedWith(
            compareByDescending<Map.Entry<Pair<String,String>,Pair<Int,Int>>>{(it.value.first+1.0)/(it.value.second+2.0)}
                .thenByDescending{it.value.first}).take(6).joinToString("; "){
            "${it.key.first} failed -> ${it.key.second}: ${it.value.first}/${it.value.second} successful fallback observations"
        }
    }
    /** Adapt the actual recovery action, not just the prompt. Only equivalent
     * read-only URL tools are interchangeable; never synthesize new queries or
     * copy old arguments into a new task. Host-local evidence is weighted more. */
    fun urlFallback(state:AppState,failed:String,url:String,available:Set<String>):String?{
        val compatible=setOf("web.read","browser.read")
        if(failed !in compatible)return null
        val host=runCatching{java.net.URI(url).host?.lowercase(java.util.Locale.ROOT)}.getOrNull()?:return null
        val history=state.mobileActions.orEmpty().takeLast(100)
        fun score(tool:String):Double{
            var success=1.0
            var total=2.0
            history.filter{it.tool==tool&&it.status in setOf("completed","failed")}.forEach{row->
                val oldHost=runCatching{java.net.URI(row.args["url"].asString).host?.lowercase(java.util.Locale.ROOT)}.getOrNull()
                val weight=if(oldHost==host)3.0 else 1.0
                total+=weight
                if(row.status=="completed")success+=weight
            }
            return success/total
        }
        return (compatible-failed).filter{it in available}.maxByOrNull(::score)?.takeIf{score(it)>=0.25}
    }

    fun app(state:AppState,pkg:String):String{
        val rows=state.tasks.filter{it.phonePackage==pkg&&it.phonePrivateRead==null&&
            it.engineStatus in setOf("completed","failed","paused","cancelled")}.sortedByDescending{it.createdAt}.take(20)
        if(rows.size<2)return ""
        val ok=rows.count{it.engineStatus=="completed"}
        return "Prior same-app tasks: $ok/${rows.size} completed with recorded evidence; "+
            "use fresh controls, do not repeat a failed action on the unchanged screen."
    }
}

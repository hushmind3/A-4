package com.dominion.mobile.data

import com.google.gson.JsonParser
import com.dominion.mobile.data.model.AppState
import com.dominion.mobile.platform.DeviceActions

data class ErrandStage(val title:String,val agentId:String,val request:String,val app:String?)
data class ErrandWorkflowDraft(val id:String,val goal:String,val stages:List<ErrandStage>){
    fun checked(agents:Set<String>,apps:Set<String>):ErrandWorkflowDraft{
        require(id.matches(Regex("[a-f0-9-]{36}"))&&goal.length in 3..1500&&stages.size in 2..4)
        require(stages.all{it.title.isNotBlank()&&it.title.length<=100&&it.agentId in agents&&
            it.request.isNotBlank()&&it.request.length<=1300&&
            (it.app==null||it.app in apps&&it.app in PhonePublicResult.packages)})
        require(stages.distinct().size==stages.size)
        return this
    }
    companion object{
        fun parse(goal:String,raw:String,agents:Set<String>,apps:Set<String>):ErrandWorkflowDraft{
            require(raw.length<=12000)
            val o=JsonParser.parseString(raw.trim().removePrefix("```json").removeSuffix("```").trim()).asJsonObject
            require(o.keySet()==setOf("stages"))
            val stages=o.getAsJsonArray("stages").map{item->
                val s=item.asJsonObject
                require(s.keySet()==setOf("title","agentId","request","app"))
                fun str(k:String)=s.get(k).also{require(it.isJsonPrimitive&&it.asJsonPrimitive.isString)}.asString.trim()
                ErrandStage(str("title"),str("agentId"),str("request"),if(s.get("app").isJsonNull)null else str("app"))
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
    fun app(state:AppState,pkg:String):String{
        val rows=state.tasks.filter{it.phonePackage==pkg&&it.phonePrivateRead==null&&
            it.engineStatus in setOf("completed","failed","paused","cancelled")}.sortedByDescending{it.createdAt}.take(20)
        if(rows.size<2)return ""
        val ok=rows.count{it.engineStatus=="completed"}
        return "Prior same-app tasks: $ok/${rows.size} completed with recorded evidence; "+
            "use fresh controls, do not repeat a failed action on the unchanged screen."
    }
}

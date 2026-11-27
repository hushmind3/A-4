package com.dominion.mobile.data

import com.google.gson.JsonParser
import java.time.OffsetDateTime

/** Candidate appointment extracted from a reply that the owner has reviewed.
 * Model output cannot directly create or send a calendar invitation. */
data class ReplyAppointment(val title:String,val at:String,val evidence:String){
    fun checked(reply:String,now:Long=System.currentTimeMillis()):ReplyAppointment{
        require(title.length in 2..100&&title.none(Char::isISOControl))
        require(evidence.length in 2..300&&reply.contains(evidence))
        val time=OffsetDateTime.parse(at).toInstant().toEpochMilli()
        require(time>now&&time<=now+366L*86400000)
        return this
    }
    companion object{
        fun parse(raw:String,reply:String):ReplyAppointment?{
            val objectValue=JsonParser.parseString(raw.trim().removePrefix("```json").removeSuffix("```").trim()).asJsonObject
            if(objectValue.keySet()==setOf("unavailable")&&objectValue.get("unavailable").asBoolean)return null
            require(objectValue.keySet()==setOf("title","at","evidence"))
            fun str(key:String)=objectValue.get(key).also{require(it.isJsonPrimitive&&it.asJsonPrimitive.isString)}.asString.trim()
            return ReplyAppointment(str("title"),str("at"),str("evidence")).checked(reply)
        }
    }
}

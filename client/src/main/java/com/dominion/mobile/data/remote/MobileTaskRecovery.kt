package com.dominion.mobile.data.remote

import com.dominion.mobile.data.harness.MobileActionRecord
import com.dominion.mobile.data.model.Message
import com.dominion.mobile.data.model.MessageKind
import com.dominion.mobile.data.model.MessageSender
import com.dominion.mobile.data.model.MessageStatus
import java.util.UUID

/** Stable, bounded retries of a phone-local AI task after a process interruption.
 * Never replay a proposed or completed external action or a device write.
 */
internal object MobileTaskRecovery {
    private val effectTools=setOf("memory.save","schedule.create","phone.dial","sms.draft",
        "email.draft","calendar.draft","maps.open","korea.open","apps.open")
    fun attempts(messages:List<Message>):List<Message> = messages.filter{
        it.sender==MessageSender.USER && it.kind==MessageKind.TASK &&
            it.jobId?.startsWith("mobile:")==true
    }
    fun canRetry(owner:Message?,attempts:List<Message>,
        actions:List<MobileActionRecord>):Boolean {
        if(owner?.status!=MessageStatus.FAILED ||
            owner.jobStatus !in setOf("failed","interrupted") ||
            attempts.size !in 1..2 || owner!=attempts.lastOrNull())return false
        // Any prior write/launch/approval must be inspected by the user,
        // regardless of whether the Android intent returned successfully.
        return actions.none{it.tool in effectTools || it.status in setOf("pending","launching")}
    }
    fun nextNonce(taskId:String,attempt:Int):String {
        require(attempt in 2..3)
        return UUID.nameUUIDFromBytes(
            ("mobile-task-attempt:"+taskId+":"+attempt).toByteArray(Charsets.UTF_8)).toString()
    }
}

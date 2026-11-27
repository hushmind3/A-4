package com.dominion.mobile.data

import com.dominion.mobile.data.remote.MobileTaskRecovery
import com.dominion.mobile.data.harness.MobileActionRecord
import com.dominion.mobile.data.model.Message
import com.dominion.mobile.data.model.MessageKind
import com.dominion.mobile.data.model.MessageSender
import com.dominion.mobile.data.model.MessageStatus
import com.google.gson.JsonObject
import org.junit.Assert.*
import org.junit.Test

class MobileTaskRecoveryTest{
    private fun message(id:String,status:MessageStatus=MessageStatus.FAILED,jobStatus:String="interrupted")=
        Message(id,"room",MessageSender.USER,"do task",kind=MessageKind.TASK,status=status,
            jobId="mobile:"+id,jobStatus=jobStatus)
    private fun action(tool:String)=MobileActionRecord("action","room","session","old",
        "character",tool,JsonObject(),"digest",status="completed")
    @Test fun readsMayRetryTwiceButNotForever(){
        val first=message("old")
        assertTrue(MobileTaskRecovery.canRetry(first,listOf(first),emptyList()))
        val second=message("second")
        assertTrue(MobileTaskRecovery.canRetry(second,listOf(first,second),listOf(action("clock"))))
        val third=message("third")
        assertFalse(MobileTaskRecovery.canRetry(third,listOf(first,second,third),emptyList()))
        assertEquals(MobileTaskRecovery.nextNonce("task",2),MobileTaskRecovery.nextNonce("task",2))
        assertNotEquals(MobileTaskRecovery.nextNonce("task",2),MobileTaskRecovery.nextNonce("task",3))
    }
    @Test fun sideEffectsOrApprovalsMustNeverAutoReplay(){
        val first=message("old")
        for(tool in listOf("memory.save","phone.dial","email.draft","schedule.create","calendar.draft")){
            assertFalse(MobileTaskRecovery.canRetry(first,listOf(first),listOf(action(tool))))
        }
        assertFalse(MobileTaskRecovery.canRetry(first,listOf(first),
            listOf(action("clock").copy(status="pending"))))
        assertFalse(MobileTaskRecovery.canRetry(message("old",MessageStatus.SENT,"completed"),
            listOf(message("old",MessageStatus.SENT,"completed")),emptyList()))
    }
}

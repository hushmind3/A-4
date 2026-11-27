package com.dominion.mobile.data

import com.dominion.mobile.data.remote.CloudToolReceipt
import com.dominion.mobile.data.remote.NativeJobOutput
import com.dominion.mobile.data.remote.TaskReceipt
import org.junit.Assert.*
import org.junit.Test

class TaskExportTest {
    private val receipt=TaskReceipt(
        jobId="cloud:job",status="done",checkedAt=1_700_000_000_000,
        reason=null,error=null,
        outputs=listOf(NativeJobOutput("assistant","DOMINION","확인된 결과","reply:1",1,null)),
        cloudEvidence=listOf(CloudToolReceipt(1,"browser.read","ok",1_700_000_000,
            "https://example.com","관측된 내용"))
    )

    @Test fun exportContainsOnlyObservedResultAndNoCredentials(){
        val text=TaskExport.document("민원 준비",receipt)
        assertTrue(text.contains("민원 준비"))
        assertTrue(text.contains("확인된 결과"))
        assertTrue(text.contains("관측된 내용"))
        assertFalse(text.contains("Bearer",ignoreCase=true))
        assertTrue(text.contains("자동으로 증명하지 않습니다"))
    }

    @Test fun filenameIsSafeForAndroidDocumentProviders(){
        assertEquals("a_b_c_d_e_f.txt",TaskExport.filename("a/b\\c:d*e?f"))
        assertTrue(TaskExport.filename("").endsWith(".txt"))
        assertTrue(TaskExport.filename("x".repeat(200)).length<=84)
    }
}

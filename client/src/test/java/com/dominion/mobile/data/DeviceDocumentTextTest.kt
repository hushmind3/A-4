package com.dominion.mobile.data

import com.dominion.mobile.data.local.DeviceDocumentText
import org.junit.Assert.*
import org.junit.Test

class DeviceDocumentTextTest{
    @Test fun onlyExplicitDocumentFormatsAreAllowed(){
        assertTrue(DeviceDocumentText.readable("text/plain","note.txt"))
        assertTrue(DeviceDocumentText.readable("application/json","notes"))
        assertTrue(DeviceDocumentText.readable("application/octet-stream","todo.md"))
        assertFalse(DeviceDocumentText.readable("image/jpeg","camera.jpg"))
        assertFalse(DeviceDocumentText.readable("application/pdf","document.pdf"))
    }
    @Test fun realUtf8DocumentIsExtractedAndBounded(){
        val text="내일 아산 시청 예약\n할 일은 2개입니다"
        assertEquals(text,DeviceDocumentText.extract(text.toByteArray(Charsets.UTF_8)))
        assertTrue(DeviceDocumentText.extract("가".repeat(10000).toByteArray(Charsets.UTF_8)).length<=8000)
    }
    @Test fun rejectsBinaryAndBlank(){
        assertTrue(runCatching{DeviceDocumentText.extract(byteArrayOf(0,1,2))}.isFailure)
        assertTrue(runCatching{DeviceDocumentText.extract(" ".toByteArray())}.isFailure)
    }
}

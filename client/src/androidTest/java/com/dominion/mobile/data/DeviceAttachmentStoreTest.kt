package com.dominion.mobile.data

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dominion.mobile.data.local.DeviceAttachmentStore
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import android.content.Context
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class DeviceAttachmentStoreTest {
    @Test fun encryptedAttachmentSurvivesStoreReopenButNotOtherAccountOrRoom() {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val scope="device-attachment-test-"+UUID.randomUUID()
        val id=UUID.randomUUID().toString()
        val room="mobile-assistant"
        val payload="테스트 개인정보는 기기에만 저장".toByteArray(Charsets.UTF_8)
        val owner=DeviceAttachmentStore(context,scope)
        try{
            owner.save(room,id,payload)
            assertArrayEquals(payload,DeviceAttachmentStore(context,scope).read(room,id))
            assertNull(owner.read("other-room",id))
            assertNull(DeviceAttachmentStore(context,scope+"-other").read(room,id))
        }finally{owner.remove(room,id)}
    }
}

package com.dominion.mobile.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dominion.mobile.data.remote.MobileIntent
import com.dominion.mobile.data.remote.MobileAction
import com.dominion.mobile.data.remote.MobileJournal
import com.dominion.mobile.data.remote.MobileJournalEntry
import com.dominion.mobile.data.local.EncryptedRecordStore
import com.dominion.mobile.platform.avatar.AvatarModelStore
import java.io.File
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MobileFeatureDeviceTest {
    @Test fun encryptedOperationJournalSurvivesReopen() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val key = "dominion-test-" + java.util.UUID.randomUUID()
        val value = MobileJournal(listOf(MobileJournalEntry(
            MobileIntent.create("owner", MobileAction.MESSAGE,"peer","실제 저장 테스트"))))
        try {
            EncryptedRecordStore(context,key,MobileJournal::class.java).save(value)
            assertEquals(value,EncryptedRecordStore(context,key,MobileJournal::class.java).load())
            assertFalse(context.getSharedPreferences(key,Context.MODE_PRIVATE)
                .getString("sealed","").orEmpty().contains("실제 저장 테스트"))
        } finally {
            EncryptedRecordStore(context,key,MobileJournal::class.java).clear()
            context.deleteSharedPreferences(key)
            java.security.KeyStore.getInstance("AndroidKeyStore").apply { load(null); deleteEntry("$key.aes.v1") }
        }
    }

    @Test fun authoredMouthAndRigPersistTogether() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dir=File(context.cacheDir,"rig-test-"+java.util.UUID.randomUUID())
        val key=AvatarModelStore.key("engine","character")
        val base="v 0 0 0\nv 1 0 0\nv 0 1 0\nf 1 2 3\n".toByteArray()
        try {
            val store=AvatarModelStore(dir)
            store.save(key,base)
            val mouth=base.decodeToString().replace("v 0 1 0","v 0 0.5 0").toByteArray()
            store.saveMouth(key,mouth)
            val spec=AvatarRigSpec("dominion-avatar-rig/v1",AvatarRigSpec.sha256(base),
                floatArrayOf(0f,0f,0f),floatArrayOf(0f,0f,0f),
                floatArrayOf(1f,0.5f,0f),floatArrayOf(0f,0.5f,1f))
            store.saveRig(key,com.google.gson.Gson().toJson(spec).toByteArray())
            store.saveViseme(key,"a",mouth)
            val restored=AvatarModelStore(dir).loadScene(key)!!
            assertNotNull(restored.rig)
            assertNotNull(restored.mouth)
            assertTrue("a" in restored.visemes)
            assertArrayEquals(restored.mouth!!,restored.visemes.getValue("a"),0f)
        } finally { dir.deleteRecursively() }
    }
}

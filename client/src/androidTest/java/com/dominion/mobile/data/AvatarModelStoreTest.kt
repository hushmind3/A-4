package com.dominion.mobile.data

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dominion.mobile.platform.avatar.AvatarModelStore
import java.io.File
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AvatarModelStoreTest {
    @Test fun modelSurvivesNewStoreAndInvalidReplacementPreservesOriginal() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val dir = File(context.cacheDir, "avatar-test-" + java.util.UUID.randomUUID())
        try {
            val key = AvatarModelStore.key("engine-a", "character-a")
            val other = AvatarModelStore.key("engine-b", "character-a")
            val obj = "v 0 0 0\nv 1 0 0\nv 0 1 0\nf 1 2 3\n".toByteArray()
            val original = AvatarModelStore(dir).save(key, obj)
            assertArrayEquals(original, AvatarModelStore(dir).load(key)!!, 0f)
            assertNull(AvatarModelStore(dir).load(other))
            assertTrue(runCatching { AvatarModelStore(dir).save(key, "invalid".toByteArray()) }.isFailure)
            assertArrayEquals(original, AvatarModelStore(dir).load(key)!!, 0f)
        } finally { dir.deleteRecursively() }
    }
}

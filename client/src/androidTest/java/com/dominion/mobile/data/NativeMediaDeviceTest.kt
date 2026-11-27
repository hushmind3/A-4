package com.dominion.mobile.data

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelCopy
import android.os.Handler
import android.os.Looper
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dominion.mobile.platform.avatar.AvatarModelStore
import com.dominion.mobile.platform.avatar.StandardAvatarActivity
import com.dominion.mobile.platform.avatar.StandardAvatarStore
import com.dominion.mobile.platform.audio.EngineVoiceAudio
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class NativeMediaDeviceTest {
    /** Exercise real Android MediaPlayer, not just the WAV parser. */
    @Test fun wavPlaybackCompletesAndReportsActualPosition() {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val samples=ShortArray(WaveAudio.SAMPLE_RATE){i->
            (kotlin.math.sin(2.0*Math.PI*440*i/WaveAudio.SAMPLE_RATE)*2000).toInt().toShort()
        }
        val positions=mutableListOf<Int>()
        val levels=mutableListOf<Float>()
        runBlocking {
            withTimeout(15000L){
                EngineVoiceAudio.play(context,WaveAudio.mono16(samples),communication=true,
                    existingCallFocus=true,onPositionMs={positions.add(it)},onSpeechLevel={levels.add(it)})
            }
        }
        assertTrue(positions.any{it>=100})
        assertEquals(-1,positions.last())
        assertTrue(levels.any{it>0f})
        assertEquals(0f,levels.last(),0f)
    }

    /** Render a valid embedded glTF triangle through the same activity used by imported avatars,
     * then verify actual surface pixels with PixelCopy. No network or downloaded asset. */
    @Test fun importedGltfProducesRealSurfacePixels() {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val id="media-"+UUID.randomUUID()
        val scope="instrumentation"
        val key=AvatarModelStore.key(scope,id)
        val store=StandardAvatarStore(context.noBackupFilesDir)
        val data=ByteBuffer.allocate(36).order(ByteOrder.LITTLE_ENDIAN)
        floatArrayOf(-1f,-1f,0f,1f,-1f,0f,0f,1f,0f).forEach{data.putFloat(it)}
        val base64=java.util.Base64.getEncoder().encodeToString(data.array())
        val json="""{"asset":{"version":"2.0"},"extensionsUsed":["KHR_materials_unlit"],"scene":0,"scenes":[{"nodes":[0]}],"nodes":[{"mesh":0}],"meshes":[{"primitives":[{"attributes":{"POSITION":0},"material":0}]}],"materials":[{"doubleSided":true,"extensions":{"KHR_materials_unlit":{}},"pbrMetallicRoughness":{"baseColorFactor":[0,1,0,1]}}],"buffers":[{"byteLength":36,"uri":"data:application/octet-stream;base64,$base64"}],"bufferViews":[{"buffer":0,"byteOffset":0,"byteLength":36,"target":34962}],"accessors":[{"bufferView":0,"componentType":5126,"count":3,"type":"VEC3","min":[-1,-1,0],"max":[1,1,0]}]}"""
        store.save(key,json.toByteArray())
        try {
            val intent=Intent(context,StandardAvatarActivity::class.java)
                .putExtra("model_scope",scope).putExtra("character_id",id).putExtra("name","Instrumented")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ActivityScenario.launch<StandardAvatarActivity>(intent).use { scenario ->
                fun surface(view:View):SurfaceView? {
                    if(view is SurfaceView)return view
                    if(view is ViewGroup)for(i in 0 until view.childCount)surface(view.getChildAt(i))?.let{return it}
                    return null
                }
                var rendered=false
                repeat(30) {
                    if(rendered)return@repeat
                    val signal=CountDownLatch(1)
                    var bitmap:Bitmap?=null
                    var success=false
                    scenario.onActivity{activity->
                        val view=surface(activity.window.decorView)
                        if(view!=null && view.holder.surface.isValid && view.width>0 && view.height>0){
                            val image=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888)
                            bitmap=image
                            PixelCopy.request(view,image,{result->success=result==PixelCopy.SUCCESS;signal.countDown()},Handler(Looper.getMainLooper()))
                        }else signal.countDown()
                    }
                    if(signal.await(2,TimeUnit.SECONDS)){
                        bitmap?.let { image ->
                            try {
                                if(success)loop@ for(y in 0 until image.height step 4)for(x in 0 until image.width step 4){
                                    val p=image.getPixel(x,y)
                                    val g=android.graphics.Color.green(p)
                                    if(g>80 && g>android.graphics.Color.red(p)+35 && g>android.graphics.Color.blue(p)+35){rendered=true;break@loop}
                                }
                            }finally{image.recycle()}
                        }
                    }
                    if(!rendered)Thread.sleep(300)
                }
                assertTrue("Expected actual green model pixels on Filament SurfaceView",rendered)
            }
        }finally{store.remove(key)}
    }
}

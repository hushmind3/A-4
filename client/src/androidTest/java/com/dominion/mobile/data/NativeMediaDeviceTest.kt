package com.dominion.mobile.data

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
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
     * then verify real TextureView pixels. No network or downloaded asset. */
    @Test fun importedGltfProducesRealTexturePixels() {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val id="media-"+UUID.randomUUID()
        val scope="instrumentation"
        val key=AvatarModelStore.key(scope,id)
        val store=StandardAvatarStore(context.noBackupFilesDir)
        val data=ByteBuffer.allocate(72).order(ByteOrder.LITTLE_ENDIAN)
        floatArrayOf(-1f,-1f,0f,1f,-1f,0f,0f,1f,0f).forEach{data.putFloat(it)}
        // Matte glTF surface with +Z normals: no environment cubemap required.
        repeat(3){floatArrayOf(0f,0f,1f).forEach{data.putFloat(it)}}
        val base64=java.util.Base64.getEncoder().encodeToString(data.array())
        val json="""{"asset":{"version":"2.0"},"scene":0,"scenes":[{"nodes":[0]}],"nodes":[{"mesh":0}],"meshes":[{"primitives":[{"attributes":{"POSITION":0,"NORMAL":1},"material":0}]}],"materials":[{"doubleSided":true,"pbrMetallicRoughness":{"baseColorFactor":[0,1,0,1],"metallicFactor":0,"roughnessFactor":1}}],"buffers":[{"byteLength":72,"uri":"data:application/octet-stream;base64,$base64"}],"bufferViews":[{"buffer":0,"byteOffset":0,"byteLength":36,"target":34962},{"buffer":0,"byteOffset":36,"byteLength":36,"target":34962}],"accessors":[{"bufferView":0,"componentType":5126,"count":3,"type":"VEC3","min":[-1,-1,0],"max":[1,1,0]},{"bufferView":1,"componentType":5126,"count":3,"type":"VEC3","min":[0,0,1],"max":[0,0,1]}]}"""
        store.save(key,json.toByteArray())
        try {
            val intent=Intent(context,StandardAvatarActivity::class.java)
                .putExtra("model_scope",scope).putExtra("character_id",id).putExtra("name","Instrumented")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ActivityScenario.launch<StandardAvatarActivity>(intent).use { scenario ->
                fun texture(view:View):android.view.TextureView? {
                    if(view is android.view.TextureView)return view
                    if(view is ViewGroup)for(i in 0 until view.childCount)
                        texture(view.getChildAt(i))?.let{return it}
                    return null
                }
                var rendered=false
                var availableTextures=0
                var capturedFrames=0
                var maxGreenSeen=0
                var maxGreenDominance=Int.MIN_VALUE
                var lastCenterPixel=0
                repeat(45) {
                    if(rendered)return@repeat
                    var bitmap:Bitmap?=null
                    scenario.onActivity{activity->
                        val view=texture(activity.window.decorView)
                        if(view!=null && view.isAvailable && view.width>0 && view.height>0){
                            availableTextures++
                            bitmap=view.bitmap
                            if(bitmap!=null)capturedFrames++
                        }
                    }
                    bitmap?.let { image ->
                        try {
                            loop@ for(y in 0 until image.height step 4)
                                for(x in 0 until image.width step 4){
                                    val p=image.getPixel(x,y)
                                    val g=android.graphics.Color.green(p)
                                    maxGreenSeen=maxOf(maxGreenSeen,g)
                                    maxGreenDominance=maxOf(maxGreenDominance,g-
                                        maxOf(android.graphics.Color.red(p),android.graphics.Color.blue(p)))
                                    if(x==image.width/2 || y==image.height/2)lastCenterPixel=p
                                    if(g>80 && g>android.graphics.Color.red(p)+35 &&
                                        g>android.graphics.Color.blue(p)+35){rendered=true;break@loop}
                                }
                        }finally{image.recycle()}
                    }
                    if(!rendered)Thread.sleep(250)
                }
                assertTrue("Expected actual green model pixels on Filament TextureView; "+
                    "availableTextures="+availableTextures+", capturedFrames="+capturedFrames+
                    ", maxGreen="+maxGreenSeen+
                    ", maxGreenDominance="+maxGreenDominance+", sampleColor="+
                    Integer.toHexString(lastCenterPixel)+
                    " (inspect DOMINION-Avatar logcat for model/renderer state)",rendered)
            }
        }finally{store.remove(key)}
    }
}

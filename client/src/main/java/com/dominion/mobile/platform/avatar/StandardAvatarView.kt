package com.dominion.mobile.platform.avatar

import android.content.Context
import android.view.Choreographer
import android.view.SurfaceView
import android.widget.FrameLayout
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.dominion.mobile.data.StandardAvatarAsset
import com.google.android.filament.IndirectLight
import com.google.android.filament.utils.ModelViewer
import com.google.android.filament.utils.Utils
import java.nio.ByteBuffer

/** Real Filament renderable/skin/texture pipeline; ModelViewer owns and destroys its engine. */
private class StandardAvatarSurface(context: Context): FrameLayout(context) {
    private val child = SurfaceView(context)
    private var viewer: ModelViewer?=null
    private var indirect: IndirectLight?=null
    private var source: StandardAvatarAsset?=null
    private var loaded: StandardAvatarAsset?=null
    private var active=false
    private var scheduled=false
    private var frameStart=0L
    private var animationIndex=0
    private var previousIndex=-1
    private var mouth=0f
    private var failure: ()->Unit={}
    private var animationInfo: (List<String>)->Unit={}
    private var morphs=emptyList<Triple<Int,Int,Int>>()

    init {
        addView(child,LayoutParams(LayoutParams.MATCH_PARENT,LayoutParams.MATCH_PARENT))
    }

    fun update(asset: StandardAvatarAsset, level: Float, index: Int,
        onAnimations: (List<String>)->Unit, onFailure: ()->Unit) {
        source=asset
        mouth=level.coerceIn(0f,1f)
        animationIndex=index
        animationInfo=onAnimations;failure=onFailure
        if (isAttachedToWindow) ensureViewer()
    }

    private fun ensureViewer() {
        try {
            val data=source ?: return
            if (viewer==null) {
                Utils.init()
                val created=ModelViewer(child)
                viewer=created
                child.setOnTouchListener(created)
                val engine=created.engine
                val light=engine.lightManager
                light.setDirection(light.getInstance(created.light),0.2f,-0.6f,-1f)
                indirect=IndirectLight.Builder().irradiance(1,floatArrayOf(0.8f,0.8f,0.8f))
                    .intensity(25_000f).build(engine)
                created.scene.indirectLight=indirect
            }
            val v=requireNotNull(viewer)
            if (loaded!==data) {
                if(data.binary) v.loadModelGlb(ByteBuffer.wrap(data.source))
                else v.loadModelGltf(ByteBuffer.wrap(data.source), data::resource)
                val asset=requireNotNull(v.asset) { "Filament asset unavailable" }
                v.transformToUnitCube()
                val manager=v.engine.renderableManager
                morphs=asset.renderableEntities.asIterable().mapNotNull { entity ->
                    val ri=manager.getInstance(entity)
                    val names=asset.getMorphTargetNames(entity)
                    val index=names.indexOfFirst { name ->
                        name.lowercase().replace("_","").replace("-","") in
                            setOf("a","aa","visemeaa","mouthopen","jawopen","fclmtha","vrm aa")
                    }
                    if(index>=0 && index<manager.getMorphTargetCount(ri))
                        Triple(ri,index,manager.getMorphTargetCount(ri)) else null
                }
                loaded=data
                frameStart=0L;previousIndex=-1
                animationInfo(v.animator?.let { animator ->
                    (0 until animator.animationCount).map { animator.getAnimationName(it).ifBlank { "Animation ${it+1}" } }
                }.orEmpty())
            }
            if(active) schedule()
        } catch (_:Exception) { failure() }
        catch (_:LinkageError) { failure() }
    }
    private val callback=Choreographer.FrameCallback { now ->
        scheduled=false
        if(!active || !isAttachedToWindow) return@FrameCallback
        val v=viewer ?: return@FrameCallback
        try {
            if(frameStart==0L || previousIndex!=animationIndex) { frameStart=now;previousIndex=animationIndex }
            v.animator?.let { a ->
                if(animationIndex in 0 until a.animationCount) {
                    val duration=a.getAnimationDuration(animationIndex)
                    val elapsed=(now-frameStart)/1_000_000_000f
                    a.applyAnimation(animationIndex,if(duration>0f) elapsed%duration else 0f)
                }
                a.updateBoneMatrices()
            }
            val manager=v.engine.renderableManager
            // Use a named authored jaw/open-vowel target only; preserve other animation weights.
            morphs.forEach { (instance,index,_) -> manager.setMorphWeights(instance,floatArrayOf(mouth),index) }
            v.render(now)
            schedule()
        } catch (_:Exception) { failure() }
        catch (_:LinkageError) { failure() }
    }
    private fun schedule() {
        if (!scheduled && active && isAttachedToWindow && viewer!=null) {
            scheduled=true; Choreographer.getInstance().postFrameCallback(callback)
        }
    }
    fun setActive(value:Boolean) {
        active=value
        if(value) { if(isAttachedToWindow) ensureViewer() }
        else { Choreographer.getInstance().removeFrameCallback(callback);scheduled=false }
    }
    override fun onAttachedToWindow() { super.onAttachedToWindow();ensureViewer() }
    override fun onDetachedFromWindow() {
        Choreographer.getInstance().removeFrameCallback(callback);scheduled=false
        viewer?.let { v -> indirect?.let { light ->
            runCatching { v.scene.indirectLight=null;v.engine.destroyIndirectLight(light) }
        } }
        indirect=null;viewer=null;loaded=null;morphs=emptyList()
        super.onDetachedFromWindow()
    }
}

@Composable
fun StandardAvatarView(asset: StandardAvatarAsset, modifier: Modifier,
    speechLevel: Float=0f, animationIndex: Int=0,
    onAnimations: (List<String>)->Unit={}, onFailure: ()->Unit={}) {
    val context=LocalContext.current
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    val view=remember(context,asset) { StandardAvatarSurface(context) }
    DisposableEffect(view,lifecycle) {
        val observer=LifecycleEventObserver { _,_ ->
            view.setActive(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
        }
        lifecycle.addObserver(observer)
        view.setActive(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
        onDispose { lifecycle.removeObserver(observer); view.setActive(false) }
    }
    key(asset) {
        AndroidView(factory={view}, modifier=modifier,update={
            it.update(asset,speechLevel,animationIndex,onAnimations,onFailure)
        })
    }
}

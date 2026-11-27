package com.dominion.mobile.platform.avatar

import android.opengl.GLES11Ext
import android.opengl.GLES20.*
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import com.dominion.mobile.data.AvatarMesh
import com.dominion.mobile.data.AvatarRig
import com.dominion.mobile.data.model.CharacterShape
import com.google.ar.core.Anchor
import com.google.ar.core.Coordinates2d
import com.google.ar.core.Plane
import com.google.ar.core.Session
import com.google.ar.core.TrackingState
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.concurrent.atomic.AtomicReference
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

internal class AvatarSceneRenderer(private val ar: Boolean, shape: CharacterShape, private val color: Int,
    private val rotation: () -> Int, private val status: (String) -> Unit) : GLSurfaceView.Renderer {
    private val originalMesh = AvatarMesh.build(shape)
    private var mesh = originalMesh
    private var vertices = buffer(mesh)
    private data class MeshChange(val base: FloatArray, val mouth: FloatArray?, val rig: AvatarRig?)
    private var rig: AvatarRig? = null
    private var rigWeights: FloatBuffer? = null
    private val headTransform = FloatArray(16)
    private val jawTransform = FloatArray(16)
    private val jawLocal = FloatArray(16)
    @Volatile private var headAngle = 0f
    @Volatile private var jawAngle = 0f
    fun jointPose(head: Float, jaw: Float) {
        headAngle = head.coerceIn(-35f,35f)
        jawAngle = jaw.coerceIn(0f,30f)
    }
    private val pendingMesh = AtomicReference<MeshChange?>(null)
    private var mouthVertices: FloatBuffer? = null
    @Volatile private var previewLevel = 0f
    fun morphPreview(value: Float) { previewLevel = value.coerceIn(0f, 1f) }
    private val featureMesh = AvatarMesh.build(CharacterShape.BLOB)
    private val featureVertices = buffer(featureMesh)
    @Volatile private var faceMode = "idle"
    @Volatile private var voiceLevel = 0f
    fun speechLevel(value: Float) { voiceLevel = value.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 0f }
    private var imported = false
    private val faceDepths = listOf(-0.29f to 0.23f,0.29f to 0.23f,0f to -0.30f).map { (x,y) ->
        frontDepth(originalMesh,x,y)
    }
    private val faceMatrix = FloatArray(16)
    private val faceMv = FloatArray(16)
    private val faceMvp = FloatArray(16)
    private val quad = buffer(floatArrayOf(-1f,-1f,1f,-1f,-1f,1f,1f,1f))
    private val uv = buffer(FloatArray(8))
    private var modelProgram = 0
    private var cameraProgram = 0
    private var texture = 0
    private var width = 1
    private var height = 1
    @Volatile private var session: Session? = null
    @Volatile private var resetRequested = false
    @Volatile private var yaw = 25f
    @Volatile private var pitch = -10f
    @Volatile private var size = 1f
    private var x = 0f
    private var y = 0f
    private var anchor: Anchor? = null
    private val tap = AtomicReference<Pair<Float,Float>?>(null)
    private val model = FloatArray(16)
    private val view = FloatArray(16)
    private val projection = FloatArray(16)
    private val mv = FloatArray(16)
    private val mvp = FloatArray(16)
    private var lastStatus = ""
    private var failure = false
    private var textureSession: Session? = null

    fun replaceMesh(value: FloatArray, mouth: FloatArray? = null, rig: AvatarRig? = null) {
        require(mouth == null || mouth.size == value.size)
        require(rig == null || rig.weights.size == value.size / 3)
        pendingMesh.set(MeshChange(value, mouth, rig))
    }
    fun restoreMesh() { pendingMesh.set(MeshChange(originalMesh, null, null)) }
    fun expression(mode: String) { if (mode in setOf("idle","happy","talk","voice")) faceMode = mode }
    fun setSession(value: Session?) { session = value }
    fun touchDown(px:Float,py:Float) { x=px;y=py }
    fun drag(px:Float,py:Float) {
        yaw=(yaw+(px-x)*0.5f)%360f
        pitch=(pitch+(py-y)*0.35f).coerceIn(-80f,80f)
        x=px;y=py
    }
    fun place(px:Float,py:Float) { tap.set(px to py) }
    fun changeSize(direction:Int) { size=(size+direction*0.15f).coerceIn(0.4f,2.0f) }
    fun reset() { resetRequested=true }
    private fun report(message:String) {
        if (lastStatus != message) { lastStatus=message;status(message) }
    }

    override fun onSurfaceCreated(gl:GL10?,config:EGLConfig?) {
        try {
            glClearColor(0.07f,0.09f,0.14f,1f)
            modelProgram=program(
                "uniform mat4 uMvp; uniform mat4 uModel; uniform mat4 uHead; uniform mat4 uJaw; uniform float uMorph; attribute vec3 aPos; attribute vec3 aNormal; attribute vec3 aMouthPos; attribute vec3 aMouthNormal; attribute vec2 aRig; varying float vLight; void main(){vec4 p=vec4(mix(aPos,aMouthPos,uMorph),1.0); vec3 n0=mix(aNormal,aMouthNormal,uMorph); float w=1.0-aRig.x-aRig.y; vec4 q=w*p+aRig.x*(uHead*p)+aRig.y*(uJaw*p); vec3 skin=w*n0+aRig.x*(mat3(uHead)*n0)+aRig.y*(mat3(uJaw)*n0); vec3 n=normalize(mat3(uModel)*skin); vLight=0.32+0.68*max(dot(n,normalize(vec3(0.4,0.8,0.7))),0.0);gl_Position=uMvp*q;}",
                "precision mediump float; uniform vec3 uColor; varying float vLight; void main(){gl_FragColor=vec4(uColor*vLight,1.0);}")
            if(ar) {
                cameraProgram=program(
                    "attribute vec2 aPos; attribute vec2 aUv; varying vec2 vUv; void main(){vUv=aUv;gl_Position=vec4(aPos,0.0,1.0);}",
                    "#extension GL_OES_EGL_image_external : require\nprecision mediump float; uniform samplerExternalOES uCamera; varying vec2 vUv; void main(){gl_FragColor=texture2D(uCamera,vUv);}")
                val names=IntArray(1);glGenTextures(1,names,0);texture=names[0]
                glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,texture)
                glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,GL_TEXTURE_MIN_FILTER,GL_LINEAR)
                glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,GL_TEXTURE_MAG_FILTER,GL_LINEAR)
                glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,GL_TEXTURE_WRAP_S,GL_CLAMP_TO_EDGE)
                glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,GL_TEXTURE_WRAP_T,GL_CLAMP_TO_EDGE)
                textureSession=null
            }
            failure=false
        } catch (_:Exception) { failure=true;report("3D 그래픽 초기화 실패") }
    }

    override fun onSurfaceChanged(gl:GL10?,w:Int,h:Int) {
        width=w.coerceAtLeast(1);height=h.coerceAtLeast(1);glViewport(0,0,width,height)
    }

    override fun onDrawFrame(gl:GL10?) {
        glClear(GL_COLOR_BUFFER_BIT or GL_DEPTH_BUFFER_BIT)
        if(failure || modelProgram==0)return
        try {
            pendingMesh.getAndSet(null)?.let {
                mesh = it.base
                vertices = buffer(it.base)
                mouthVertices = it.mouth?.let(::buffer)
                rig = it.rig
                rigWeights = it.rig?.weights?.let(::buffer)
                imported = it.base !== originalMesh
            }
            if(resetRequested) {
                resetRequested=false
                anchor?.detach();anchor=null;tap.set(null)
                yaw=25f;pitch=-10f;size=1f
            }
            if(ar) {
                val active=session?:return
                if(textureSession!==active) {
                    active.setCameraTextureName(texture)
                    textureSession=active
                }
                active.setDisplayGeometry(rotation(),width,height)
                val frame=active.update()
                if(frame.timestamp!=0L) {
                    quad.position(0);uv.position(0)
                    frame.transformCoordinates2d(Coordinates2d.OPENGL_NORMALIZED_DEVICE_COORDINATES,
                        quad,Coordinates2d.TEXTURE_NORMALIZED,uv)
                    drawBackground()
                }
                val camera=frame.camera
                if(camera.trackingState!=TrackingState.TRACKING) {
                    report("공간 추적 중 · 천천히 기기를 움직이세요.")
                    return
                }
                tap.getAndSet(null)?.let { (px,py) ->
                    val hit=frame.hitTest(px,py).firstOrNull {
                        val plane=it.trackable as? Plane
                        plane!=null && plane.trackingState==TrackingState.TRACKING &&
                            plane.type==Plane.Type.HORIZONTAL_UPWARD_FACING && plane.isPoseInPolygon(it.hitPose)
                    }
                    if(hit!=null) { val next=hit.createAnchor();anchor?.detach();anchor=next }
                }
                val current=anchor
                if(current==null) { report("탐색한 바닥·책상을 터치해 캐릭터를 배치하세요.");return }
                if(current.trackingState!=TrackingState.TRACKING) {
                    report("배치 위치 추적 대기 중");return
                }
                report("실제 AR 앵커 추적 중 · 화면 이동·크기 변경 가능")
                camera.getViewMatrix(view,0)
                camera.getProjectionMatrix(projection,0,0.05f,50f)
                current.pose.toMatrix(model,0)
                Matrix.translateM(model,0,0f,0.14f*size,0f)
                Matrix.scaleM(model,0,0.14f*size,0.14f*size,0.14f*size)
            } else {
                Matrix.perspectiveM(projection,0,45f,width.toFloat()/height,0.1f,100f)
                Matrix.setLookAtM(view,0,0f,0f,4.5f,0f,0f,0f,0f,1f,0f)
                Matrix.setIdentityM(model,0)
                Matrix.rotateM(model,0,pitch,1f,0f,0f)
                Matrix.rotateM(model,0,yaw,0f,1f,0f)
                Matrix.scaleM(model,0,size,size,size)
            }
            Matrix.multiplyMM(mv,0,view,0,model,0)
            Matrix.multiplyMM(mvp,0,projection,0,mv,0)
            drawMesh(vertices, mesh.size/6, model, mvp, color, mouthVertices,
                if (faceMode == "voice") voiceLevel else previewLevel, rig, rigWeights)
            if (!imported) drawFace()
        } catch (_:Exception) {
            failure=true;report("그래픽 또는 AR 추적 오류 · 화면을 다시 여세요.")
        }
    }

    private fun drawBackground() {
        glDisable(GL_DEPTH_TEST);glDepthMask(false)
        glUseProgram(cameraProgram)
        val p=glGetAttribLocation(cameraProgram,"aPos")
        val t=glGetAttribLocation(cameraProgram,"aUv")
        quad.position(0);uv.position(0)
        glActiveTexture(GL_TEXTURE0)
        glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,texture)
        glUniform1i(glGetUniformLocation(cameraProgram,"uCamera"),0)
        glEnableVertexAttribArray(p);glEnableVertexAttribArray(t)
        glVertexAttribPointer(p,2,GL_FLOAT,false,0,quad)
        glVertexAttribPointer(t,2,GL_FLOAT,false,0,uv)
        glDrawArrays(GL_TRIANGLE_STRIP,0,4)
        glDisableVertexAttribArray(p);glDisableVertexAttribArray(t)
        glDepthMask(true);glEnable(GL_DEPTH_TEST)
    }

    private fun drawFace() {
        val time = android.os.SystemClock.elapsedRealtime() / 1000.0
        val blink = if (time % 4.2 < 0.13) 0.12f else 1f
        val mouth = when (faceMode) {
            "talk" -> (0.035 + 0.075 * kotlin.math.abs(kotlin.math.sin(time*10))).toFloat()
            "voice" -> 0.022f + 0.13f * voiceLevel
            "happy" -> 0.055f
            else -> 0.022f
        }
        fun feature(x: Float,y: Float,z: Float,sx: Float,sy: Float) {
            model.copyInto(faceMatrix)
            Matrix.translateM(faceMatrix,0,x,y,z+0.022f)
            Matrix.scaleM(faceMatrix,0,sx,sy,0.075f)
            Matrix.multiplyMM(faceMv,0,view,0,faceMatrix,0)
            Matrix.multiplyMM(faceMvp,0,projection,0,faceMv,0)
            drawMesh(featureVertices,featureMesh.size/6,faceMatrix,faceMvp,0xFF151923.toInt())
        }
        faceDepths[0]?.let { feature(-0.29f,0.23f,it,0.085f,0.13f*blink) }
        faceDepths[1]?.let { feature(0.29f,0.23f,it,0.085f,0.13f*blink) }
        faceDepths[2]?.let { feature(0f,-0.30f,it,if(faceMode=="happy") 0.23f else 0.14f,mouth) }
    }

    private fun drawMesh(vertices:FloatBuffer, count:Int, model:FloatArray, mvp:FloatArray, color:Int,
        mouth: FloatBuffer? = null, weight: Float = 0f,
        skeleton: AvatarRig? = null, skin: FloatBuffer? = null) {
        glEnable(GL_DEPTH_TEST)
        glUseProgram(modelProgram)
        glUniformMatrix4fv(glGetUniformLocation(modelProgram,"uMvp"),1,false,mvp,0)
        glUniformMatrix4fv(glGetUniformLocation(modelProgram,"uModel"),1,false,model,0)
        glUniform3f(glGetUniformLocation(modelProgram,"uColor"),
            ((color ushr 16) and 255)/255f,((color ushr 8) and 255)/255f,(color and 255)/255f)
        val p=glGetAttribLocation(modelProgram,"aPos");val n=glGetAttribLocation(modelProgram,"aNormal")
        glEnableVertexAttribArray(p);glEnableVertexAttribArray(n)
        vertices.position(0);glVertexAttribPointer(p,3,GL_FLOAT,false,24,vertices)
        vertices.position(3);glVertexAttribPointer(n,3,GL_FLOAT,false,24,vertices)
        val mp = glGetAttribLocation(modelProgram, "aMouthPos")
        val mn = glGetAttribLocation(modelProgram, "aMouthNormal")
        val target = mouth ?: vertices
        glUniform1f(glGetUniformLocation(modelProgram, "uMorph"), if (mouth == null) 0f else weight)
        glEnableVertexAttribArray(mp); glEnableVertexAttribArray(mn)
        target.position(0); glVertexAttribPointer(mp,3,GL_FLOAT,false,24,target)
        target.position(3); glVertexAttribPointer(mn,3,GL_FLOAT,false,24,target)
        Matrix.setIdentityM(headTransform,0)
        Matrix.setIdentityM(jawTransform,0)
        if (skeleton != null) {
            fun around(matrix: FloatArray, pivot: FloatArray, angle: Float, x: Float, y: Float) {
                Matrix.setIdentityM(matrix,0)
                Matrix.translateM(matrix,0,pivot[0],pivot[1],pivot[2])
                Matrix.rotateM(matrix,0,angle,x,y,0f)
                Matrix.translateM(matrix,0,-pivot[0],-pivot[1],-pivot[2])
            }
            around(headTransform,skeleton.headPivot,headAngle,0f,1f)
            // If an authored mouth morph exists it drives speech instead of doubling jaw movement.
            val jaw = if (faceMode == "voice" && mouth == null) voiceLevel*30f else jawAngle
            around(jawLocal,skeleton.jawPivot,jaw,1f,0f)
            Matrix.multiplyMM(jawTransform,0,headTransform,0,jawLocal,0)
        }
        glUniformMatrix4fv(glGetUniformLocation(modelProgram,"uHead"),1,false,headTransform,0)
        glUniformMatrix4fv(glGetUniformLocation(modelProgram,"uJaw"),1,false,jawTransform,0)
        val ri=glGetAttribLocation(modelProgram,"aRig")
        if (skin != null) {
            glEnableVertexAttribArray(ri)
            skin.position(0);glVertexAttribPointer(ri,2,GL_FLOAT,false,8,skin)
        } else {
            glDisableVertexAttribArray(ri);glVertexAttrib2f(ri,0f,0f)
        }
        glDrawArrays(GL_TRIANGLES,0,count)
        glDisableVertexAttribArray(ri)
        glDisableVertexAttribArray(p);glDisableVertexAttribArray(n)
        glDisableVertexAttribArray(mp);glDisableVertexAttribArray(mn)
    }

    private fun shader(type:Int,source:String):Int {
        val shader=glCreateShader(type)
        glShaderSource(shader,source);glCompileShader(shader)
        val success=IntArray(1);glGetShaderiv(shader,GL_COMPILE_STATUS,success,0)
        if(success[0]==0){glDeleteShader(shader);error("GL shader failed")}
        return shader
    }
    private fun program(vertex:String,fragment:String):Int {
        val vs=shader(GL_VERTEX_SHADER,vertex);val fs=shader(GL_FRAGMENT_SHADER,fragment)
        val id=glCreateProgram();glAttachShader(id,vs);glAttachShader(id,fs);glLinkProgram(id)
        glDeleteShader(vs);glDeleteShader(fs)
        val ok=IntArray(1);glGetProgramiv(id,GL_LINK_STATUS,ok,0)
        if(ok[0]==0){glDeleteProgram(id);error("GL link failed")}
        return id
    }
    companion object {
        // Intersect the front-facing ray with the actual surface, including non-spherical shapes.
        private fun frontDepth(mesh:FloatArray,x:Float,y:Float):Float? {
            var result:Float?=null
            for (i in mesh.indices step 18) {
                val ax=mesh[i];val ay=mesh[i+1]
                val bx=mesh[i+6];val by=mesh[i+7]
                val cx=mesh[i+12];val cy=mesh[i+13]
                val d=(by-cy)*(ax-cx)+(cx-bx)*(ay-cy)
                if(kotlin.math.abs(d)<0.000001f)continue
                val u=((by-cy)*(x-cx)+(cx-bx)*(y-cy))/d
                val v=((cy-ay)*(x-cx)+(ax-cx)*(y-cy))/d
                val w=1f-u-v
                if(u>=0f&&v>=0f&&w>=0f){
                    val z=u*mesh[i+2]+v*mesh[i+8]+w*mesh[i+14]
                    if(result==null||z>result)result=z
                }
            }
            return result
        }
        private fun buffer(data:FloatArray):FloatBuffer =
            ByteBuffer.allocateDirect(data.size*4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
                put(data);position(0)
            }
    }
}

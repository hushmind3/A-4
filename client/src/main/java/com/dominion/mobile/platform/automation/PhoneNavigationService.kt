package com.dominion.mobile.platform.automation

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Bitmap
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import com.dominion.mobile.data.VisualGesture
import kotlin.coroutines.resume
import android.app.KeyguardManager
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Bundle
import android.os.PowerManager
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Button
import com.dominion.mobile.data.PhoneNavigationPlan
import com.dominion.mobile.data.PhoneNavigationStep
import com.dominion.mobile.platform.DeviceActions
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Explicit foreground, finite device navigation. No event inspection outside an approved
 * run, no screen contents leave the device, and no generic gestures or system actions.
 */
class PhoneNavigationService : AccessibilityService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var work: Job? = null
    private var stopView: android.widget.LinearLayout? = null
    private var approveButton: Button? = null
    private var stepLabel: android.widget.TextView? = null
    private var approval: CompletableDeferred<Boolean>? = null
    override fun onServiceConnected() { super.onServiceConnected(); instance = this; _connected.value = true }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) { /* Never collect event payloads. */ }
    override fun onInterrupt() { work?.cancel() }
    override fun onUnbind(intent: Intent?): Boolean {
        shutdown()
        return super.onUnbind(intent)
    }
    override fun onDestroy() { shutdown(); scope.cancel(); super.onDestroy() }
    private fun shutdown() {
        work?.cancel()
        removeStop()
        if (instance === this) { instance = null; _connected.value = false }
    }
    private fun removeStop() {
        stopView?.let { runCatching { getSystemService(WindowManager::class.java).removeViewImmediate(it) } }
        stopView = null
        approveButton = null
        stepLabel = null
        approval?.cancel()
        approval = null
    }
    private fun showStop() {
        val panel = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(12,8,12,8)
            setBackgroundColor(android.graphics.Color.rgb(25,30,42))
        }
        val label = android.widget.TextView(this).apply {
            setTextColor(android.graphics.Color.WHITE)
            text = "DOMINION · 앱 탐색"
            maxWidth = (resources.displayMetrics.widthPixels * 0.75f).toInt()
        }
        val approve = Button(this).apply {
            text = "이 단계 실행"
            isEnabled = false
            setOnClickListener { approval?.complete(true) }
        }
        val stop = Button(this).apply {
            text = "중지"
            setOnClickListener { work?.cancel() }
        }
        panel.addView(label)
        panel.addView(approve)
        panel.addView(stop)
        getSystemService(WindowManager::class.java).addView(panel, WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT).apply { gravity = Gravity.TOP or Gravity.END })
        stopView = panel
        approveButton = approve
        stepLabel = label
    }
    private suspend fun approveStep(step:PhoneNavigationStep,pkg:String,index:Int,total:Int) {
        val deferred = CompletableDeferred<Boolean>()
        approval = deferred
        stepLabel?.text = "단계 " + index + "/" + total + " · " + step.action + " · " + step.label +
            (step.value?.let { "\n입력: " + it } ?: "")
        approveButton?.isEnabled = true
        try {
            withTimeout(30_000L) {
                while (!deferred.isCompleted) {
                    check(unlocked())
                    val root = rootInActiveWindow
                    try { check(root?.packageName?.toString() == pkg) { "App changed" } }
                    finally { root?.recycle() }
                    delay(150)
                }
                check(deferred.await())
            }
        } finally {
            approval = null
            approveButton?.isEnabled = false
        }
    }
    private fun unlocked(): Boolean =
        getSystemService(PowerManager::class.java).isInteractive &&
            !getSystemService(KeyguardManager::class.java).isKeyguardLocked

    private fun begin(plan: PhoneNavigationPlan, reviewedBatch: Boolean): Boolean {
        if (work?.isActive == true || !unlocked() || !PhoneNavigationPlan.valid(plan) ||
            !DeviceActions.isLaunchable(this, plan.app) ||
            (reviewedBatch && !PhoneNavigationPlan.permitsReviewedBatch(plan))) return false
        // Limit the dynamically configured accessibility event scope to the reviewed app.
        val originalPackages = serviceInfo.packageNames
        val configured = serviceInfo
        configured.packageNames = arrayOf(plan.app)
        try { serviceInfo = configured } catch (_: Exception) { return false }
        _observed.value = null
        _visual.value = null
        _status.value = "대상 앱 열기 요청"
        work = scope.launch {
            var completed = 0
            try {
                showStop()
                check(DeviceActions.openInstalledApp(this@PhoneNavigationService,plan.app))
                withTimeout(300_000L) {
                    // Startup may show the previous window briefly. Never read that window.
                    var entered = false
                    repeat(40) {
                        check(unlocked())
                        if (!entered) {
                            val root = rootInActiveWindow
                            entered = root?.packageName?.toString() == plan.app
                            root?.recycle()
                            if (!entered) delay(150)
                        }
                    }
                    check(entered) { "Target app unavailable" }
                    for (step in plan.steps) {
                        _status.value = "기기 단계 " + (completed + 1) + "/" + plan.steps.size + " · " + step.action
                        // Show the real target on the foreground app and require a fresh tap for every step.
                        if (reviewedBatch) {
                            stepLabel?.text = "승인한 일괄 실행 · " + (completed + 1) + "/" + plan.steps.size +
                                " · " + step.action + " · " + step.label
                            approveButton?.isEnabled = false
                            // Give the user an opportunity to stop before each operation.
                            delay(600)
                        } else approveStep(step,plan.app,completed+1,plan.steps.size)
                        if (step.action in setOf("visual_inspect","tap_point","swipe_point")) {
                            check(Build.VERSION.SDK_INT >= 34) { "Android 14 required" }
                            val frame = captureWindow(plan.app)
                            if (step.action == "visual_inspect") {
                                _visual.value = frame
                            } else {
                                val gesture = requireNotNull(VisualGesture.parse(step.action,step.value))
                                if (gesture.frame != frame.digest) {
                                    _visual.value = frame
                                    error("Screen changed; replan")
                                }
                                check(performVisualGesture(frame,gesture))
                            }
                            completed++
                            delay(500)
                            continue
                        }
                        val deadline = android.os.SystemClock.elapsedRealtime() + 4000
                        var success = false
                        while (!success && android.os.SystemClock.elapsedRealtime() < deadline) {
                            check(unlocked())
                            val root = rootInActiveWindow ?: error("No active window")
                            try {
                                check(root.packageName?.toString() == plan.app) { "App changed" }
                                success = apply(root,plan.app,step)
                            } finally { root.recycle() }
                            if (!success) delay(180)
                        }
                        check(success) { "Expected unique accessible control missing" }
                        completed++
                        delay(500)
                    }
                }
                _status.value = "기기 실행 확인 " + completed + "/" + plan.steps.size +
                    if (plan.steps.last().action in setOf("inspect","visual_inspect")) " · 실제 화면 조작 요소 확인"
                    else " · 마지막 화면 문구 확인. 외부 거래·신청 완료 증명은 아닙니다."
            } catch (e: CancellationException) {
                _status.value = "중단 · 확인된 단계 " + completed + "/" + plan.steps.size
                if (e !is TimeoutCancellationException) throw e
            } catch (_: Exception) {
                _status.value = "중단 · 화면/권한/대상 불일치. 확인된 단계 " + completed + "/" + plan.steps.size
            } finally {
                removeStop()
                runCatching {
                    val restored = serviceInfo
                    restored.packageNames = originalPackages
                    serviceInfo = restored
                }
            }
        }
        return true
    }

    private fun checkedWindow(pkg: String): Pair<Int, Rect> {
        check(unlocked())
        val root = requireNotNull(rootInActiveWindow)
        try {
            check(root.packageName?.toString() == pkg)
            val queue = java.util.ArrayDeque<AccessibilityNodeInfo>()
            queue.add(AccessibilityNodeInfo.obtain(root))
            var inspected = 0
            try {
                while (queue.isNotEmpty()) {
                    val node = queue.removeFirst()
                    try {
                        check(++inspected <= 600 && !node.isPassword)
                        if (node.packageName?.toString() == pkg) {
                            check(node.childCount <= 80)
                            for (i in 0 until node.childCount) node.getChild(i)?.let(queue::addLast)
                        }
                    } finally { node.recycle() }
                }
            } finally { while (queue.isNotEmpty()) queue.removeFirst().recycle() }
            val win = requireNotNull(root.window)
            try {
                check(win.type == android.view.accessibility.AccessibilityWindowInfo.TYPE_APPLICATION)
                val bounds = Rect()
                win.getBoundsInScreen(bounds)
                check(bounds.width() > 0 && bounds.height() > 0)
                return win.id to bounds
            } finally { win.recycle() }
        } finally { root.recycle() }
    }

    @androidx.annotation.RequiresApi(34)
    private suspend fun captureWindow(pkg: String): VisualFrame {
        val (id,bounds) = checkedWindow(pkg)
        val bytes = withTimeout(4500L) {
            suspendCancellableCoroutine<ByteArray> { continuation ->
                takeScreenshotOfWindow(id, mainExecutor, object : TakeScreenshotCallback {
                    override fun onSuccess(result: ScreenshotResult) {
                        val hardware = result.hardwareBuffer
                        val bitmap = try {
                            if (continuation.isActive) Bitmap.wrapHardwareBuffer(hardware,result.colorSpace) else null
                        } finally { hardware.close() }
                        if (bitmap == null) {
                            if (continuation.isActive) continuation.resumeWith(Result.failure(IllegalStateException("No frame")))
                            return
                        }
                        try {
                            val copy = requireNotNull(bitmap.copy(Bitmap.Config.ARGB_8888,false))
                            try {
                                require(copy.width == bounds.width() && copy.height == bounds.height())
                                val output = java.io.ByteArrayOutputStream()
                                check(copy.compress(Bitmap.CompressFormat.PNG,100,output))
                                val image = output.toByteArray()
                                require(image.size in 1..(5*1024*1024))
                                if (continuation.isActive) continuation.resume(image)
                            } finally { copy.recycle() }
                        } catch (e: Exception) {
                            if (continuation.isActive) continuation.resumeWith(Result.failure(e))
                        } finally { bitmap.recycle() }
                    }
                    override fun onFailure(errorCode: Int) {
                        if (continuation.isActive) continuation.resumeWith(Result.failure(IllegalStateException("Window capture failed: $errorCode")))
                    }
                })
            }
        }
        check(checkedWindow(pkg) == (id to bounds))
        return VisualFrame(pkg, id, bounds, bytes, VisualGesture.digest(bytes))
    }

    private suspend fun performVisualGesture(frame: VisualFrame, command: VisualGesture): Boolean {
        check(checkedWindow(frame.packageName) == (frame.windowId to frame.bounds))
        fun point(x: Int,y: Int) = (frame.bounds.left + frame.bounds.width()*x/1000f) to
            (frame.bounds.top + frame.bounds.height()*y/1000f)
        val (x,y) = point(command.x,command.y)
        val (ex,ey) = point(command.endX ?: command.x,command.endY ?: command.y)
        val overlay = Rect()
        stopView?.getGlobalVisibleRect(overlay)
        // Refuse paths intersecting the stop/approval panel.
        for (i in 0..40) {
            val t = i/40f
            check(!overlay.contains((x+(ex-x)*t).toInt(),(y+(ey-y)*t).toInt()))
        }
        val path = Path().apply { moveTo(x,y); if (command.endX != null) lineTo(ex,ey) }
        val gesture = GestureDescription.Builder().addStroke(
            GestureDescription.StrokeDescription(path,0,if(command.endX == null) 80 else 450)).build()
        return withTimeout(3000L) {
            suspendCancellableCoroutine { continuation ->
                val sent = dispatchGesture(gesture,object : GestureResultCallback() {
                    override fun onCompleted(gestureDescription: GestureDescription?) {
                        if (continuation.isActive) continuation.resume(true)
                    }
                    override fun onCancelled(gestureDescription: GestureDescription?) {
                        if (continuation.isActive) continuation.resume(false)
                    }
                },null)
                if (!sent && continuation.isActive) continuation.resume(false)
            }
        }
    }

    private fun apply(root: AccessibilityNodeInfo, pkg: String, step: PhoneNavigationStep): Boolean {
        val nodes = ArrayList<AccessibilityNodeInfo>()
        val queue = java.util.ArrayDeque<AccessibilityNodeInfo>()
        queue.add(AccessibilityNodeInfo.obtain(root))
        try {
            while (queue.isNotEmpty() && nodes.size < 600) {
                val node = queue.removeFirst()
                nodes.add(node)
                if (node.packageName?.toString() != pkg || node.isPassword) continue
                for (i in 0 until minOf(node.childCount, 80)) {
                    if (nodes.size + queue.size < 600) node.getChild(i)?.let(queue::addLast)
                }
            }
            // Reject a password screen even within an allowed map app.
            check(nodes.none { it.isPassword })
            // Only visible action labels; never collect editable text, passwords, or screenshots.
            // Keep observations in process memory until the owner explicitly selects what to share.
            val controls = nodes.asSequence().filter { node ->
                node.packageName?.toString() == pkg && node.isVisibleToUser && node.isEnabled &&
                    !node.isPassword && !node.isEditable && node.isClickable
            }.flatMap { node -> sequenceOf(node.contentDescription, node.text) }
                .mapNotNull { it?.toString()?.trim()?.takeIf { label ->
                    label.isNotBlank() && label.length <= 80 && label.none(Char::isISOControl)
                } }.distinct().take(24).toList()
            if (step.action == "inspect") {
                _observed.value = ObservedControls(pkg, controls)
                return controls.isNotEmpty()
            }
            val scrollAction = when (step.action) {
                "scroll_forward" -> AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
                "scroll_backward" -> AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
                else -> null
            }
            val matches = nodes.filter { node ->
                node.packageName?.toString() == pkg && node.isVisibleToUser && node.isEnabled &&
                    !node.isPassword && (scrollAction != null || listOf(node.text,node.contentDescription,node.hintText).any {
                        it?.toString()?.trim() == step.label
                    }) && when (step.action) {
                        "click" -> node.isClickable
                        "search_text" -> node.isEditable &&
                            node.actionList.any { it.id == AccessibilityNodeInfo.ACTION_SET_TEXT }
                        "scroll_forward", "scroll_backward" -> node.isScrollable &&
                            node.actionList.any { it.id == scrollAction }
                        "expect" -> true
                        else -> false
                    }
            }
            val target = matches.singleOrNull() ?: run {
                _observed.value = ObservedControls(pkg, controls)
                return false
            }
            return when (step.action) {
                "click" -> target.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                "search_text" -> target.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,Bundle().apply {
                    putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,requireNotNull(step.value))
                })
                "scroll_forward", "scroll_backward" -> target.performAction(requireNotNull(scrollAction))
                "expect" -> true
                else -> false
            }
        } finally {
            nodes.forEach { it.recycle() }
            while(queue.isNotEmpty()) queue.removeFirst().recycle()
        }
    }
    data class ObservedControls(val packageName: String, val labels: List<String>)

    data class VisualFrame(val packageName: String, val windowId: Int, val bounds: Rect,
        val png: ByteArray, val digest: String)

    companion object {
        private val _visual = MutableStateFlow<VisualFrame?>(null)
        val visual = _visual.asStateFlow()
        private var instance: PhoneNavigationService? = null
        private val _observed = MutableStateFlow<ObservedControls?>(null)
        val observed = _observed.asStateFlow()
        fun clearObserved() { _observed.value = null; _visual.value = null }
        private val _connected = MutableStateFlow(false)
        val connected = _connected.asStateFlow()
        private val _status = MutableStateFlow("")
        val status = _status.asStateFlow()
        fun start(plan: PhoneNavigationPlan, reviewedBatch: Boolean = false): Boolean =
            instance?.begin(plan, reviewedBatch) ?: false
        fun stop() { instance?.work?.cancel() }
    }
}

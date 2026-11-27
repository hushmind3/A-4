package com.dominion.mobile.platform.automation

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Bitmap
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import com.dominion.mobile.data.VisualGesture
import com.dominion.mobile.data.PhoneReplanContext
import com.dominion.mobile.data.PhoneReplanBudget
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
 * run, approved semantic labels may reach the selected cloud model; image bytes do not,
 * and no generic system actions execute without explicit steps.
 */
class PhoneNavigationService : AccessibilityService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var work: Job? = null
    private var activeErrand:PhoneReplanContext?=null
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

    private fun begin(plan: PhoneNavigationPlan, reviewedBatch: Boolean, replan:PhoneReplanContext?,delegatedRead:Boolean): Boolean {
        if (work?.isActive == true || !unlocked() || !PhoneNavigationPlan.valid(plan) ||
            !DeviceActions.isLaunchable(this, plan.app) ||
            (reviewedBatch && !PhoneNavigationPlan.permitsReviewedBatch(plan)) ||
            (delegatedRead&&(reviewedBatch||replan==null||plan.version!=2)) ||
            (replan!=null && (reviewedBatch || plan.version!=2 || plan.app!=replan.app ||
                (Build.VERSION.SDK_INT<34 &&
                (application as com.dominion.mobile.DominionApplication).container.engine!=null)))) return false
        // Limit the dynamically configured accessibility event scope to the reviewed app.
        val originalPackages = serviceInfo.packageNames
        val configured = serviceInfo
        configured.packageNames = arrayOf(plan.app)
        try { serviceInfo = configured } catch (_: Exception) { return false }
        _observed.value = null
        _visual.value = null
        _status.value = "대상 앱 열기 요청"
        activeErrand=replan
        work = scope.launch {
            var completed = 0
            val initialRepo=(application as com.dominion.mobile.DominionApplication).container.repository
            var recorded=false
            var endStatus="paused"
            var sendAttempted=false
            var transactionAttempted=false
            var transactionVerified=false
            var resultVerified=false
            var privateVerified=false
            var approvedResults=emptyList<String>()
            var feedback=""
            val verified=mutableListOf<String>()
            try {
                if(replan!=null)recorded=initialRepo.recordPhoneErrand(replan,"running","기기 업무 실행 시작 · "+plan.app)
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
                    val container=(application as com.dominion.mobile.DominionApplication).container
                    val engine=container.engine
                    val repo=container.repository
                    val budget=PhoneReplanBudget()
                    fun validRoom() {
                        if(replan!=null)check(container.engine===engine && container.repository===repo &&
                            repo.state.value.conversation(replan.roomId)?.sessionId==replan.sessionId)
                    }
                    val localScope=replan?.privateRead
                    if(localScope!=null){
                        check(recorded&&engine==null)
                        val result=runPrivateAccountRead(plan.app,localScope,::validRoom)
                        if(result!=null){
                            verified.add(localScope.field+": "+result)
                            privateVerified=true;endStatus="completed";completed++
                            _status.value="기기 전용 조회 완료 · 선택한 항목 저장"
                        }else _status.value="선택한 항목을 확인하지 못했습니다 · 직접 조회 필요"
                        return@withTimeout
                    }
                    var activePlan=plan
                    while(true) {
                    validRoom()
                    if(replan?.publicRead==true)check(com.dominion.mobile.data.PhonePublicResult.permits(activePlan,replan.initialRequest))
                    replan?.transaction?.let{check(com.dominion.mobile.data.PhoneTransactions.validPlan(activePlan,it,replan.initialRequest))}
                    var nextFrame:VisualFrame?=null
                    var needsSemanticReplan=false
                    var localCompleted=0
                    for (step in activePlan.steps) {
                        validRoom()
                        if(step.action=="commit_transaction"){
                            val intent=requireNotNull(replan?.transaction)
                            check(recorded&&!transactionAttempted)
                            val review=transactionScreen(plan.app,step.label,intent)
                            if(review==null){
                                feedback="Final review missing exact target, condition, unambiguous total, or button; no transaction occurred."
                                needsSemanticReplan=true
                                break
                            }
                            approveStep(PhoneNavigationStep("최종 거래 실행",step.label,review.summary(intent.kind)),plan.app,1,1)
                            validRoom()
                            // Recompute the entire screen and click only the exact
                            // unique control in this same fresh accessibility tree.
                            val clicked=withTransactionNodes(plan.app){nodes,lines->
                                val fresh=com.dominion.mobile.data.PhoneTransactions.review(intent,plan.app,step.label,lines)
                                if(fresh!=review)return@withTransactionNodes false
                                val button=nodes.singleOrNull{it.isClickable&&!it.isEditable&&nodeNames(it).contains(step.label)}
                                    ?:return@withTransactionNodes false
                                transactionAttempted=true
                                button.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                            }
                            if(!clicked){
                                if(transactionAttempted){
                                    _status.value="거래 버튼 실행 결과 불명 · 재실행 금지"
                                    break
                                }
                                feedback="The reviewed screen changed before approval; inspect again without submitting."
                                needsSemanticReplan=true
                                break
                            }
                            val deadline=android.os.SystemClock.elapsedRealtime()+15000
                            var receipt=emptyList<String>()
                            while(receipt.isEmpty()&&android.os.SystemClock.elapsedRealtime()<deadline){
                                validRoom()
                                receipt=runCatching{withTransactionNodes(plan.app){_,lines->
                                    com.dominion.mobile.data.PhoneTransactions.receipt(intent.kind,lines)
                                }}.getOrDefault(emptyList())
                                if(receipt.isEmpty())delay(500)
                            }
                            verified.add(review.summary(intent.kind))
                            if(receipt.isNotEmpty()){
                                verified.addAll(receipt)
                                transactionVerified=true;endStatus="completed";completed++
                                _status.value="거래 실행 · 실제 완료 화면 확인"
                            }else _status.value="거래 실행 요청 후 완료 화면 미확인 · 재실행 금지"
                            break
                        }
                        if(step.action=="transaction_next"){
                            val intent=requireNotNull(replan?.transaction)
                            check(recorded&&!transactionAttempted&&com.dominion.mobile.data.PhoneTransactions.canAdvance(step.label))
                            approveStep(PhoneNavigationStep("거래 준비",step.label),plan.app,localCompleted+1,activePlan.steps.size)
                            validRoom()
                            // Reversible navigation: exact, unique observed control. Never re-click a failed action.
                            val ok=withTransactionNodes(plan.app){nodes,lines->
                                check(!com.dominion.mobile.data.PhoneTransactions.authScreen(lines))
                                val target=nodes.singleOrNull{it.isClickable&&!it.isEditable&&nodeNames(it).contains(step.label)}
                                target?.performAction(AccessibilityNodeInfo.ACTION_CLICK)==true
                            }
                            if(!ok){feedback="Preparation control missing; inspect another path.";needsSemanticReplan=true;break}
                            completed++;localCompleted++;delay(500);continue
                        }
                        if(step.action=="report_result"){
                            check(recorded&&replan?.publicRead==true&&step.label in approvedResults)
                            validRoom()
                            val root=requireNotNull(rootInActiveWindow)
                            try{
                                check(root.packageName?.toString()==plan.app)
                                check(apply(root,plan.app,PhoneNavigationStep("inspect","화면")))
                            }finally{root.recycle()}
                            val fresh=_observed.value?.takeIf{it.packageName==plan.app}?.results.orEmpty()
                            if(step.label !in fresh){
                                feedback="Previously observed result changed; use fresh screen evidence."
                                needsSemanticReplan=true
                                break
                            }
                            verified.add("검증한 결과: "+step.label)
                            verified.addAll(approvedResults.filter{it in fresh}.distinct().take(20))
                            resultVerified=true
                            completed++;endStatus="completed"
                            _status.value="공개 검색 결과 확인 · 실제 화면 근거 저장"
                            break
                        }
                        if(step.action=="send_message"){
                            val intent=requireNotNull(replan?.kakaoMessage){"No explicit message payload"}
                            check(recorded&&plan.app==intent.app&&!sendAttempted)
                            intent.checked()
                            approveStep(PhoneNavigationStep("메시지 준비",intent.recipient,intent.body),
                                plan.app,1,2)
                            validRoom()
                            val priorCount=withKakaoChat(intent){editor,_,count->
                                check(editor.text.isNullOrBlank()||editor.text?.toString()==editor.hintText?.toString()){"기존 작성 내용이 있습니다"}
                                check(editor.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,Bundle().apply{
                                    putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,intent.body)
                                })){"메시지 입력 실패"}
                                count
                            }
                            delay(350)
                            approveStep(PhoneNavigationStep("실제 메시지 발송",intent.recipient,intent.body),plan.app,2,2)
                            validRoom()
                            withKakaoChat(intent){editor,send,_->
                                check(editor.text?.toString()==intent.body){"작성 내용 변경"}
                                val button=requireNotNull(send){"전송 버튼 없음"}
                                sendAttempted=true
                                check(button.performAction(AccessibilityNodeInfo.ACTION_CLICK)){"전송 버튼 실행 확인 실패"}
                            }
                            val deadline=android.os.SystemClock.elapsedRealtime()+6000
                            var sent=false
                            while(!sent&&android.os.SystemClock.elapsedRealtime()<deadline){
                                validRoom()
                                sent=runCatching{withKakaoChat(intent){editor,_,count->
                                    (editor.text.isNullOrBlank()||editor.text?.toString()==editor.hintText?.toString())&&count>priorCount
                                }}.getOrDefault(false)
                                if(!sent)delay(250)
                            }
                            if(sent){
                                verified.add("새 메시지 표시 · "+intent.recipient)
                                completed++;endStatus="completed"
                                _status.value="메시지 발송 화면 확인 · 새 메시지 표시됨 · 상대 수신/읽음 미확인"
                            }else{
                                endStatus="paused"
                                _status.value="발송 버튼 실행 후 결과 확인 필요 · 자동 재발송 금지"
                            }
                            break
                        }
                        _status.value = "기기 단계 " + (localCompleted + 1) + "/" + activePlan.steps.size + " · " + step.action
                        // Show the real target on the foreground app and require a fresh tap for every step.
                        if (reviewedBatch) {
                            stepLabel?.text = "승인한 일괄 실행 · " + (localCompleted + 1) + "/" + activePlan.steps.size +
                                " · " + step.action + " · " + step.label
                            approveButton?.isEnabled = false
                            // Give the user an opportunity to stop before each operation.
                            delay(600)
                        } else if(delegatedRead&&replan!=null&&
                            PhoneNavigationPlan.permitsDelegatedRead(activePlan,step,replan.initialRequest)){
                            stepLabel?.text="위임한 탐색 · "+step.action+" · "+step.label+
                                (step.value?.let{" → "+it}?:"")
                            approveButton?.isEnabled=false
                            delay(600)
                        } else approveStep(step,plan.app,localCompleted+1,activePlan.steps.size)
                        validRoom()
                        if (step.action in setOf("visual_inspect","tap_point","swipe_point")) {
                            check(Build.VERSION.SDK_INT >= 34) { "Android 14 required" }
                            val frame = captureWindow(plan.app)
                            if (step.action == "visual_inspect") {
                                _visual.value = frame
                                nextFrame=frame
                            } else {
                                val gesture = requireNotNull(VisualGesture.parse(step.action,step.value))
                                if (gesture.frame != frame.digest) {
                                    _visual.value = frame
                                    if(replan!=null){nextFrame=frame;needsSemanticReplan=true;break}
                                    error("Screen changed; replan")
                                }
                                check(performVisualGesture(frame,gesture))
                            }
                            completed++;localCompleted++
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
                        if(!success&&replan?.publicRead==true&&engine==null&&Build.VERSION.SDK_INT>=34&&
                            step.action=="click"&&step.label in ScreenTextNavigation.allowed){
                            validRoom()
                            // The semantic click never succeeded. Use only a uniquely
                            // recognized safe label, with a separate visible approval.
                            success=try{visualTextClick(plan.app,step.label,::validRoom)}
                            catch(e:CancellationException){throw e}
                            catch(_:Exception){false}
                        }
                        if(!success && replan!=null) {
                            feedback="Failed "+step.action+" on "+step.label+"; inspect and choose a different observed control."
                            // A failed target is not success: require a fresh observation.
                            if(engine==null){
                                approveStep(PhoneNavigationStep("inspect","실패한 앱 화면 조작 요소 관찰"),
                                    plan.app,localCompleted+1,activePlan.steps.size)
                                val view=requireNotNull(rootInActiveWindow)
                                try{check(view.packageName?.toString()==plan.app)
                                    apply(view,plan.app,PhoneNavigationStep("inspect","화면"))
                                }finally{view.recycle()}
                                needsSemanticReplan=true
                            }else{
                                approveStep(PhoneNavigationStep("visual_inspect","실패 화면 캡처"),
                                    plan.app,localCompleted+1,activePlan.steps.size)
                                nextFrame=captureWindow(plan.app);_visual.value=nextFrame
                            }
                            break
                        }
                        check(success) { "Expected unique accessible control missing" }
                        completed++;localCompleted++
                        if(step.action=="expect")verified.add(step.label)
                        if(engine==null && replan!=null && step.action=="inspect")
                            needsSemanticReplan=true
                        delay(500)
                    }
                    if(sendAttempted||transactionAttempted||resultVerified)break
                    val frame=nextFrame
                    val mobileReplan=engine==null && replan!=null &&
                        (needsSemanticReplan || frame!=null || activePlan.steps.last().action=="inspect" ||
                            activePlan.steps.last().action=="visual_inspect")
                    if(replan==null || (frame==null && !mobileReplan)) {
                        _status.value="기기 실행 확인 · 누적 "+completed+"단계 · "+
                            if(activePlan.steps.last().action=="expect")"마지막 화면 문구 확인" else "화면 관찰 완료"
                        break
                    }
                    if(!budget.canRequest()){
                        _status.value="재계획 한도 도달 · 실행 "+completed+"단계 · 결과 확인 필요"
                        break
                    }
                    // Every observation shared with a model needs a fresh visible approval.
                    val proposal:PhoneNavigationPlan?
                    val evidence:String
                    if(mobileReplan){
                        approveStep(PhoneNavigationStep("inspect",
                            if(replan.publicRead)"현재 공개 검색 결과와 버튼 문구를 AI에 전달 (개인 화면이면 중지)"
                            else "현재 앱의 조작 버튼 문구만 클라우드 AI에 전달하고 재계획"),plan.app,1,1)
                        validRoom()
                        val view=requireNotNull(rootInActiveWindow)
                        try{check(view.packageName?.toString()==plan.app)
                            apply(view,plan.app,PhoneNavigationStep("inspect","화면"))
                        }finally{view.recycle()}
                        val observation=_observed.value?.takeIf{it.packageName==plan.app}
                        var controls=observation?.labels.orEmpty()
                        approvedResults=if(replan.publicRead)observation?.results.orEmpty() else emptyList()
                        if(replan.publicRead&&Build.VERSION.SDK_INT>=34&&(controls.isEmpty()||feedback.startsWith("Failed click"))){
                            // Only the allowlisted OCR navigation labels are exported;
                            // no screenshot or arbitrary OCR text is sent to inference.
                            approveStep(PhoneNavigationStep("화면 문자 탐색","검색용 버튼 문구만 기기에서 인식"),plan.app,1,1)
                            validRoom()
                            val found=try{ScreenTextNavigation.targets(captureWindow(plan.app).png)}
                                catch(e:CancellationException){throw e}
                                catch(_:Exception){emptyList()}
                            controls=(found.map{it.label}+controls).distinct().take(24)
                        }
                        if(controls.isEmpty()&&approvedResults.isEmpty()){
                            _status.value="읽을 수 있는 조작 버튼 없음 · 실행 "+completed+"단계"
                            break
                        }
                        evidence=VisualGesture.digest((controls+approvedResults).joinToString("\n").toByteArray())
                        _status.value="모바일 클라우드에서 다음 조작 판단 중"
                        proposal=repo.replanPhoneControls(replan,controls,approvedResults,feedback)
                        feedback=""
                    }else{
                        val shot=requireNotNull(frame)
                        approveStep(PhoneNavigationStep("visual_inspect",
                            "현재 화면을 본체에 전달하고 재계획"),plan.app,1,1)
                        validRoom()
                        check(checkedWindow(plan.app)==(shot.windowId to shot.bounds))
                        _status.value="본체 화면 분석·재계획 대기"
                        evidence=shot.digest
                        proposal=coroutineScope {
                            val guard=launch {
                                while(isActive){validRoom();checkedWindow(plan.app);delay(300)}
                            }
                            try{
                                repo.replanPhoneNavigation(replan,shot.digest,shot.png,
                                    shot.bounds.width(),shot.bounds.height())
                            }finally{guard.cancelAndJoin()}
                        }
                    }
                    validRoom()
                    if(proposal==null){
                        _status.value="다음 실행 계획 없음 · 확인된 "+completed+"단계"
                        break
                    }
                    check(budget.admit(evidence,proposal,plan.app)){"Repeated or out-of-scope plan"}
                    activePlan=proposal
                    }
                }
            } catch (e: CancellationException) {
                endStatus=if(sendAttempted||transactionAttempted)"paused" else "cancelled"
                _status.value = if(transactionAttempted)"거래 시도 후 중단 · 결과 확인 필요 · 재실행 금지" else if(sendAttempted)"발송 시도 후 중단 · 결과 확인 필요 · 재발송 금지" else "중단 · 확인된 단계 " + completed
                if (e !is TimeoutCancellationException) throw e
            } catch (_: Exception) {
                endStatus=if(sendAttempted||transactionAttempted)"paused" else "failed"
                _status.value = if(transactionAttempted)"거래 시도 결과 불명 · 재실행 금지" else if(sendAttempted)"발송 시도 결과 불명 · 재발송 금지" else "중단 · 화면/권한/대상 불일치. 확인된 단계 " + completed
            } finally {
                if(recorded&&replan!=null)withContext(NonCancellable){
                    val detail="실제 앱: "+plan.app+"\n"+_status.value+
                        "\n확인한 화면 문구: "+verified.distinct().joinToString(", ").ifBlank{"없음"}+
                        (if(privateVerified)"\n선택한 항목의 실제 화면 수치만 기기에 저장했습니다. 외부 모델에 전송하지 않았습니다."
                         else if(transactionVerified)"\n실제 앱의 새 완료 문구를 확인했습니다. 공급자 영수증·변경 조건은 별도 확인할 수 있습니다."
                         else if(transactionAttempted)"\n거래 요청 이후 완료 근거 없음. 중복 실행하지 마세요."
                         else if(resultVerified)"\n실제 화면에 있는 조회 결과만 기록했습니다. 거래·예약·결제는 수행하지 않았습니다."
                         else if(endStatus=="completed")"\n기기 화면에서 새 메시지를 확인했습니다. 서버 수신·읽음은 확인하지 않았습니다."
                         else "\n최종 외부 결과는 확인되지 않았습니다. 발송을 시도했다면 재발송 전에 직접 확인하세요.")
                    try{initialRepo.recordPhoneErrand(replan,endStatus,detail)}
                    catch(_:Exception){_status.value+=" · 업무 기록 저장 실패"}
                }
                activeErrand=null
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

    @androidx.annotation.RequiresApi(34)
    private suspend fun visualTextClick(pkg:String,label:String,validRoom:()->Unit):Boolean{
        require(label in ScreenTextNavigation.allowed)
        val first=captureWindow(pkg)
        val target=ScreenTextNavigation.targets(first.png).singleOrNull{it.label==label}?:return false
        approveStep(PhoneNavigationStep("화면에서 확인한 검색 버튼",label),pkg,1,1)
        validRoom()
        val fresh=captureWindow(pkg)
        if(fresh.digest!=first.digest||fresh.windowId!=first.windowId||fresh.bounds!=first.bounds)return false
        val current=ScreenTextNavigation.targets(fresh.png).singleOrNull{it==target}?:return false
        validRoom()
        // OCR is asynchronous; bind the actual dispatch to a last fresh frame.
        val last=captureWindow(pkg)
        if(last.digest!=fresh.digest||last.windowId!=fresh.windowId||last.bounds!=fresh.bounds)return false
        validRoom()
        return performVisualGesture(last,VisualGesture(last.digest,current.x,current.y))
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

    /** Fresh, unique semantic controls in the foreground Kakao conversation.
     * No passwords, coordinate guesses, clipboard or recipient inference. */
    private fun <T> withKakaoChat(intent:com.dominion.mobile.data.KakaoMessageRequest,
        block:(AccessibilityNodeInfo,AccessibilityNodeInfo?,Int)->T):T{
        check(unlocked())
        val pkg=intent.app
        val root=requireNotNull(rootInActiveWindow)
        val nodes=ArrayList<AccessibilityNodeInfo>()
        val queue=java.util.ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        try{
            check(root.packageName?.toString()==pkg)
            val bounds=Rect().also{root.getBoundsInScreen(it)}
            check(bounds.height()>0)
            while(queue.isNotEmpty()&&nodes.size<600){
                val n=queue.removeFirst();nodes.add(n)
                if(n.packageName?.toString()!=pkg||n.isPassword)continue
                for(i in 0 until minOf(n.childCount,80))if(nodes.size+queue.size<600)n.getChild(i)?.let(queue::addLast)
            }
            check(nodes.none{it.isPassword})
            val visible=nodes.filter{it.packageName?.toString()==pkg&&it.isVisibleToUser&&it.isEnabled}
            fun names(n:AccessibilityNodeInfo)=listOfNotNull(n.text?.toString(),n.contentDescription?.toString(),n.hintText?.toString()).map{it.trim()}
            fun rect(n:AccessibilityNodeInfo)=Rect().also{n.getBoundsInScreen(it)}
            val titles=visible.filter{!it.isEditable&&intent.recipient in names(it)&&rect(it).centerY()<=bounds.top+bounds.height()/3}
            check(titles.size==1){"대화방 이름을 유일하게 확인하지 못했습니다"}
            intent.listing?.let{listing->
                check(visible.any{!it.isEditable&&listing in names(it)&&rect(it).centerY()<=bounds.top+bounds.height()/2}){
                    "거래 상품을 실제 화면에서 확인하지 못했습니다"
                }
            }
            val hints=setOf("메시지 입력","메시지를 입력하세요","메시지","채팅 입력","채팅 메시지 입력","메시지를 입력해 주세요","메시지를 입력해주세요","채팅을 입력하세요","Message","Type a message")
            val editor=visible.singleOrNull{it.isEditable&&!it.isPassword&&rect(it).centerY()>bounds.centerY()&&
                it.actionList.any{a->a.id==AccessibilityNodeInfo.ACTION_SET_TEXT}&&
                listOf(it.hintText?.toString(),it.contentDescription?.toString()).any{h->h in hints}}
                ?:error("메시지 입력란을 유일하게 확인하지 못했습니다")
            val sends=visible.filter{it.isClickable&&!it.isEditable&&rect(it).centerY()>bounds.centerY()&&
                names(it).any{label->label in setOf("전송","보내기","메시지 보내기","Send")}}
            check(sends.size<=1)
            val count=visible.filter{!it.isEditable&&it.text?.toString()==intent.body&&rect(it).centerY()>bounds.top+bounds.height()/3}
                .map{rect(it).toShortString()}.distinct().size
            return block(editor,sends.singleOrNull(),count)
        }finally{nodes.forEach{it.recycle()};while(queue.isNotEmpty())queue.removeFirst().recycle()}
    }

    private suspend fun runPrivateAccountRead(pkg:String,intent:com.dominion.mobile.data.PhonePrivateRead,
        validRoom:()->Unit):String?{
        intent.checked(pkg)
        val tried=mutableSetOf<String>()
        repeat(8){
            validRoom()
            val observation=runCatching{withTransactionNodes(pkg){nodes,lines->
                if(com.dominion.mobile.data.PhoneTransactions.authScreen(lines))null
                else {
                    val result=com.dominion.mobile.data.PrivateAccountRead.extract(intent,lines)
                    val menu=nodes.filter{it.isClickable&&!it.isEditable}.flatMap(::nodeNames)
                        .firstOrNull{it in com.dominion.mobile.data.PrivateAccountRead.menus(intent.kind)&&it !in tried}
                    result to menu
                }
            }}.getOrNull()
            val result=observation?.first
            if(result!=null){
                // Show only the selected field, never a private screen dump.
                approveStep(PhoneNavigationStep("조회 결과 기기에 저장",intent.field,result),pkg,1,1)
                validRoom()
                val fresh=runCatching{withTransactionNodes(pkg){_,lines->
                    if(com.dominion.mobile.data.PhoneTransactions.authScreen(lines))null
                    else com.dominion.mobile.data.PrivateAccountRead.extract(intent,lines)
                }}.getOrNull()
                if(fresh==result)return result
                delay(250)
            }else{
                val menu=observation?.second
                if(menu!=null){
                    tried.add(menu)
                    approveStep(PhoneNavigationStep("조회 메뉴 이동",menu),pkg,1,1)
                    validRoom()
                    withTransactionNodes(pkg){nodes,lines->
                        check(!com.dominion.mobile.data.PhoneTransactions.authScreen(lines))
                        nodes.singleOrNull{it.isClickable&&!it.isEditable&&menu in nodeNames(it)}
                            ?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    }
                    delay(700)
                }else{
                    approveStep(PhoneNavigationStep("직접 로그인·조회 화면 이동 후 확인",intent.field),pkg,1,1)
                    validRoom();delay(300)
                }
            }
        }
        return null
    }

    private fun nodeNames(node:AccessibilityNodeInfo)=listOfNotNull(node.text?.toString(),
        node.contentDescription?.toString(),node.hintText?.toString()).map{it.trim()}.filter{it.isNotBlank()}

    private fun <T> withTransactionNodes(pkg:String,block:(List<AccessibilityNodeInfo>,List<String>)->T):T {
        check(unlocked())
        val root=requireNotNull(rootInActiveWindow)
        val nodes=ArrayList<AccessibilityNodeInfo>()
        val queue=java.util.ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        try{
            check(root.packageName?.toString()==pkg)
            while(queue.isNotEmpty()&&nodes.size<600){
                val n=queue.removeFirst();nodes.add(n)
                if(n.packageName?.toString()!=pkg||n.isPassword)continue
                for(i in 0 until minOf(n.childCount,80))if(nodes.size+queue.size<600)n.getChild(i)?.let(queue::addLast)
            }
            check(nodes.none{it.isPassword}){"인증 화면은 사용자가 직접 처리"}
            val visible=nodes.filter{it.packageName?.toString()==pkg&&it.isVisibleToUser&&it.isEnabled}
            val lines=visible.filter{!it.isEditable}.flatMap(::nodeNames).distinct().take(350)
            return block(visible,lines)
        }finally{nodes.forEach{it.recycle()};while(queue.isNotEmpty())queue.removeFirst().recycle()}
    }
    private fun transactionScreen(pkg:String,label:String,intent:com.dominion.mobile.data.PhoneTransactionRequest):
        com.dominion.mobile.data.PhoneTransactions.Review?=withTransactionNodes(pkg){nodes,lines->
            if(nodes.count{it.isClickable&&!it.isEditable&&nodeNames(it).contains(label)}!=1)null
            else com.dominion.mobile.data.PhoneTransactions.review(intent,pkg,label,lines)
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
                } }.distinct().take(100).toList()
            if (step.action == "inspect") {
                // Only add the exact user-entered recipient and recognized input hints,
                // not private conversation text. A fresh approval still gates transmission.
                val intent=activeErrand?.kakaoMessage?.takeIf{pkg==it.app}
                val title=if(intent!=null&&nodes.any{it.packageName?.toString()==pkg&&it.isVisibleToUser&&!it.isEditable&&
                    listOf(it.text?.toString(),it.contentDescription?.toString()).contains(intent.recipient)})listOf(intent.recipient)else emptyList()
                val searchHints=nodes.filter{it.packageName?.toString()==pkg&&it.isVisibleToUser&&it.isEnabled&&it.isEditable&&!it.isPassword}
                    .flatMap{listOfNotNull(it.hintText?.toString(),it.contentDescription?.toString())}
                    .map{it.trim()}.filter(com.dominion.mobile.data.PhoneTransactions::isSearchField).distinct()
                val scope=activeErrand?.transaction
                val priority=if(scope!=null)controls.filter{
                    com.dominion.mobile.data.PhoneTransactions.finalAction(scope.kind,it)||
                        com.dominion.mobile.data.PhoneTransactions.canAdvance(it)
                }else emptyList()
                val listing=intent?.listing?.takeIf{name->nodes.any{it.packageName?.toString()==pkg&&it.isVisibleToUser&&!it.isEditable&&name in nodeNames(it)}}
                val observed=(title+listOfNotNull(listing)+searchHints+priority+controls).distinct().take(24)
                val publicRead=activeErrand?.publicRead==true&&pkg in com.dominion.mobile.data.PhonePublicResult.packages
                val results=if(publicRead){
                    val raw=nodes.filter{it.packageName?.toString()==pkg&&it.isVisibleToUser&&!it.isPassword&&!it.isEditable}
                        .mapNotNull{it.text?.toString()?.trim()}.filter{it.isNotBlank()}
                    check(!com.dominion.mobile.data.PhonePublicResult.sensitive(raw)){"개인정보가 포함될 수 있는 화면"}
                    raw.mapNotNull(com.dominion.mobile.data.PhonePublicResult::text)
                        .filter{it !in observed}.distinct().take(30)
                }else emptyList()
                _observed.value = ObservedControls(pkg, observed,results)
                return observed.isNotEmpty()||results.isNotEmpty()
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
                        "search_submit" -> Build.VERSION.SDK_INT>=30&&node.isEditable&&
                            listOfNotNull(node.hintText?.toString(),node.contentDescription?.toString()).any{
                                it.trim()==step.label&&com.dominion.mobile.data.PhoneTransactions.isSearchField(it.trim())
                            }&&node.actionList.any{it.id==AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id}
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
                "search_submit" -> Build.VERSION.SDK_INT>=30&&
                    target.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id)
                "scroll_forward", "scroll_backward" -> target.performAction(requireNotNull(scrollAction))
                "expect" -> true
                else -> false
            }
        } finally {
            nodes.forEach { it.recycle() }
            while(queue.isNotEmpty()) queue.removeFirst().recycle()
        }
    }
    data class ObservedControls(val packageName: String, val labels: List<String>,val results:List<String> = emptyList())

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
        fun start(plan: PhoneNavigationPlan, reviewedBatch: Boolean = false,
            replan:PhoneReplanContext?=null,delegatedRead:Boolean=false): Boolean =
            instance?.begin(plan, reviewedBatch,replan,delegatedRead) ?: false
        fun stop() { instance?.work?.cancel() }
        suspend fun stopErrand(room:String,session:String):Boolean=withContext(Dispatchers.Main.immediate){
            val service=instance?:return@withContext false
            val ctx=service.activeErrand?:return@withContext false
            if(ctx.roomId!=room||ctx.sessionId!=session)return@withContext false
            val running=service.work?:return@withContext false
            running.cancelAndJoin()
            true
        }
    }
}

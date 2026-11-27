package com.dominion.mobile.data
/** Only an explicitly entered recipient and message may reach the Kakao sender.
 * The model never supplies or rewrites this payload. */
data class KakaoMessageRequest(val recipient:String,val body:String){
    fun checked():KakaoMessageRequest{
        require(recipient.isNotBlank()&&recipient.length<=60&&recipient==recipient.trim()&&recipient.none(Char::isISOControl))
        require(body.isNotBlank()&&body.length<=500&&body==body.trim()&&body.none{it.isISOControl()&&it!='\n'})
        return this
    }
}

/** Public lookup is deliberately separate from reservation, payment and messaging.
 * Only selected public-search apps may return non-editable visible result text. */
object PhonePublicResult {
    val packages=setOf("com.nhn.android.search","com.nhn.android.nmap","net.daum.android.map",
        "com.coupang.mobile","com.sampleapp","com.fineapp.yogiyo","com.coupang.mobile.eats",
        "com.skt.tmap.ku","com.towneers.www")
    private val sensitiveScreen=listOf("비밀번호","인증번호","주민등록번호","계좌번호","카드번호",
        "결제수단","배송지 관리","주문내역","주문 내역","채팅방","내 계정","개인정보",
        "password","verification code","credit card","my account")
    private val transaction=listOf("결제","구매","송금","삭제","발송","전송","로그인","인증","제출","가입",
        "주문하기","주문 확정","주문하기","예약하기","예약 확정","호출하기","호출 확정","장바구니",
        "checkout","purchase","book now","confirm","send","buy","pay","order now","login")
    fun sensitive(labels:List<String>):Boolean=labels.any{label->sensitiveScreen.any{label.contains(it,true)}}
    fun safeLabel(label:String):Boolean=transaction.none{label.contains(it,true)}
    fun text(value:String):String? {
        val clean=value.trim()
        if(clean.length !in 2..100||clean.any(Char::isISOControl)||sensitive(listOf(clean)))return null
        if(clean.contains('@')||Regex("[0-9][0-9 .-]{6,}[0-9]").containsMatchIn(clean))return null
        return clean
    }
    fun permits(plan:PhoneNavigationPlan,request:String):Boolean =
        plan.app in packages&&plan.version==2&&PhoneNavigationPlan.valid(plan)&&plan.steps.all{step->
            when(step.action){
                "inspect","expect","report_result","scroll_forward","scroll_backward"->true
                "search_submit"->PhoneTransactions.isSearchField(step.label)
                "click"->safeLabel(step.label)
                "search_text"->safeLabel(step.label)&&step.value?.let{request.contains(it)&&text(it)!=null}==true
                else->false
            }
        }
}

/** Bound one user-approved device task to one existing DOMINION room. */
data class PhoneReplanContext(val roomId:String,val sessionId:String,val app:String,
    val initialMessageId:String,val initialRequest:String,val kakaoMessage:KakaoMessageRequest?=null,
    val publicRead:Boolean=false,val transaction:PhoneTransactionRequest?=null) {
    init { require(roomId.isNotBlank() && sessionId.isNotBlank() && PhoneNavigationPlan.eligiblePackage(app) &&
        initialMessageId.isNotBlank() && initialRequest.isNotBlank() && initialRequest.length<=4000)
        if(kakaoMessage!=null){require(app=="com.kakao.talk");kakaoMessage.checked()}
        if(publicRead)require(kakaoMessage==null&&transaction==null&&app in PhonePublicResult.packages)
        if(transaction!=null){require(kakaoMessage==null&&!publicRead);transaction.checked(app)}
    }

}
internal class PhoneReplanBudget(private val maximum:Int=6) {
    private val seen=mutableSetOf<String>()
    private var rounds=0
    fun canRequest():Boolean=rounds<maximum
    fun admit(frame:String,plan:PhoneNavigationPlan,app:String):Boolean {
        if(rounds>=maximum || plan.app!=app || plan.version!=2 || !PhoneNavigationPlan.valid(plan))return false
        val gesture=plan.steps.firstOrNull { it.action in setOf("tap_point","swipe_point") }
        if(gesture!=null && VisualGesture.parse(gesture.action,gesture.value)?.frame!=frame)return false
        val key=frame+"|"+plan.steps.joinToString("|")
        if(!seen.add(key))return false
        rounds++
        return true
    }
}

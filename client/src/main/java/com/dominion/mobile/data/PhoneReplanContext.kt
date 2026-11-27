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

/** Bound one user-approved device task to one existing DOMINION room. */
data class PhoneReplanContext(val roomId:String,val sessionId:String,val app:String,
    val initialMessageId:String,val initialRequest:String,val kakaoMessage:KakaoMessageRequest?=null) {
    init { require(roomId.isNotBlank() && sessionId.isNotBlank() && PhoneNavigationPlan.eligiblePackage(app) &&
        initialMessageId.isNotBlank() && initialRequest.isNotBlank() && initialRequest.length<=4000)
        if(kakaoMessage!=null){require(app=="com.kakao.talk");kakaoMessage.checked()}
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

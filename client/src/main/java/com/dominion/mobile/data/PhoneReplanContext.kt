package com.dominion.mobile.data
/** Bound one user-approved device task to one existing DOMINION room. */
data class PhoneReplanContext(val roomId:String,val sessionId:String,val app:String,
    val initialMessageId:String,val initialRequest:String) {
    init { require(roomId.isNotBlank() && sessionId.isNotBlank() && PhoneNavigationPlan.eligiblePackage(app) &&
        initialMessageId.isNotBlank() && initialRequest.isNotBlank() && initialRequest.length<=4000) }

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

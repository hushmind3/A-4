package com.dominion.mobile.data

/** Explicit user scope, not a model-created purchase authorization. The final
 * approval is separate and bound to fresh foreground app evidence. */
data class PhoneTransactionRequest(val kind:String,val target:String,val condition:String,val maxWon:Long){
    fun checked(pkg:String):PhoneTransactionRequest{
        require(PhoneTransactions.supports(kind,pkg))
        require(target==target.trim()&&target.length in 2..100&&target.none(Char::isISOControl))
        require(condition==condition.trim()&&condition.length in 2..160&&condition.none(Char::isISOControl))
        require(maxWon in 0..100_000_000L && (kind=="clinic"||maxWon>0))
        return this
    }
}

object PhoneTransactions {
    private val apps=mapOf(
        "shopping" to setOf("com.coupang.mobile","com.navercorp.navershopping"),
        "delivery" to setOf("com.sampleapp","com.fineapp.yogiyo","com.coupang.mobile.eats"),
        "transport" to setOf("com.kakao.taxi"),
        "stay" to setOf("com.cultsotry.yanolja.nativeapp","kr.goodchoice.abouthere"),
        "clinic" to setOf("com.bbros.sayup")
    )
    fun supports(kind:String,pkg:String)=pkg in apps[kind].orEmpty()
    fun supportedApp(pkg:String)=apps.values.any{pkg in it}
    private val finalLabels=mapOf(
        "shopping" to setOf("결제하기","주문하기","주문 확정","결제"),
        "delivery" to setOf("결제하기","주문하기","주문 확정","배달 주문하기"),
        "transport" to setOf("호출하기","택시 호출하기","택시 호출","호출 확정"),
        "stay" to setOf("결제하기","예약 확정","예약하기","예약 및 결제"),
        "clinic" to setOf("예약하기","예약 확정","접수하기","접수 신청","진료 예약하기")
    )
    private val nextLabels=setOf("장바구니","장바구니 담기","장바구니 보기","주문서 확인","주문서",
        "옵션 선택","객실 선택","예약 정보 확인","예약 정보 입력","택시 선택","차량 선택",
        "예약 시간 선택","진료시간 선택","일정 선택")
    private val receiptLabels=mapOf(
        "shopping" to setOf("주문 완료","주문이 완료되었습니다","결제 완료","결제가 완료되었습니다"),
        "delivery" to setOf("주문 완료","주문이 완료되었습니다","주문 접수 완료","주문이 접수되었습니다"),
        "transport" to setOf("호출 완료","호출 요청 완료","배차 완료","택시가 배차되었습니다"),
        "stay" to setOf("예약 완료","예약이 완료되었습니다","예약이 확정되었습니다"),
        "clinic" to setOf("예약 완료","예약이 완료되었습니다","접수 완료","접수가 완료되었습니다")
    )
    private val money=Regex("(?<![0-9])([0-9]{1,3}(?:,[0-9]{3})+|[0-9]{1,9})\\s*원")
    private val pricePrefix=Regex("^[0-9,]+\\s*원\\s*")
    private val totalLabels=listOf("총 결제금액","총 결제 금액","최종 결제금액","최종 결제 금액",
        "결제 예정 금액","총 주문금액","총 주문 금액","결제금액","결제 금액","예상 운임","예상 요금","총 예약금액")
    private val authLabels=setOf("비밀번호 입력","인증번호 입력","본인 인증","본인인증","카드번호 입력","결제 비밀번호")
    fun authScreen(labels:List<String>)=labels.any{v->authLabels.any{v.contains(it,true)}}
    fun canAdvance(label:String)=label in nextLabels
    fun isSearchField(label:String)=label in setOf("검색","Search","검색어 입력","검색어를 입력하세요","통합 검색",
        "장소 검색","장소, 주소 검색","상품 검색","음식점 검색","숙소 검색","병원 검색","출발지","도착지","목적지",
        "출발지 검색","도착지 검색","목적지 검색","장소를 입력하세요","어디로 갈까요?","어디로 가시나요?")
    fun finalAction(kind:String,label:String)=label.replace(pricePrefix,"") in finalLabels[kind].orEmpty()
    fun anyFinalAction(label:String)=finalLabels.keys.any{finalAction(it,label)}
    fun receipt(kind:String,labels:List<String>)=labels.map{it.trim().trimEnd('.','!')}
        .filter{it in receiptLabels[kind].orEmpty()}.distinct()
    fun validPlan(plan:PhoneNavigationPlan,request:PhoneTransactionRequest,original:String):Boolean{
        if(!supports(request.kind,plan.app)||!PhoneNavigationPlan.valid(plan)||plan.version!=2)return false
        return plan.steps.all{step->when(step.action){
            "transaction_next"->canAdvance(step.label)
            "commit_transaction"->finalAction(request.kind,step.label)
            "click"->PhonePublicResult.safeLabel(step.label)&&!anyFinalAction(step.label)
            "search_text"->step.value?.let{original.contains(it)&&PhonePublicResult.text(it)!=null}==true
            "inspect","expect","scroll_forward","scroll_backward"->true
            "search_submit"->isSearchField(step.label)
            else->false
        }}
    }
    data class Review(val target:String,val condition:String,val amount:Long,val button:String,val evidence:String){
        fun summary(kind:String)="대상: "+target+"\n조건: "+condition+"\n"+
            (if(kind=="transport")"화면 예상 운임 (실제 변동 가능): " else "화면 결제금액: ")+amount+"원\n실행: "+button
    }
    fun review(request:PhoneTransactionRequest,pkg:String,button:String,lines:List<String>):Review?{
        request.checked(pkg)
        if(!finalAction(request.kind,button)||authScreen(lines)||receipt(request.kind,lines).isNotEmpty())return null
        if(lines.none{it.contains(request.target)}||lines.none{it.contains(request.condition)})return null
        // Only explicitly labeled totals or the price printed on the final button.
        // Never mistake an individual item, crossed-out price, coupon, or range for a total.
        val candidates=mutableSetOf<Long>()
        fun add(s:String):Boolean{
            val matches=money.findAll(s).toList()
            if(matches.size!=1||s.contains("~")||s.contains("∼")||s.contains("부터")||s.contains("최대"))return false
            val amount=matches.single().groupValues[1].replace(",","").toLongOrNull()?:return false
            candidates.add(amount);return true
        }
        for(i in lines.indices){
            val line=lines[i]
            val prefix=totalLabels.firstOrNull{line.startsWith(it)}?:continue
            val remainder=line.removePrefix(prefix).trim().trimStart(':').trim()
            if(remainder.isNotEmpty()){
                if(!add(remainder))return null
            }else{
                val next=lines.getOrNull(i+1)?:return null
                if(!Regex("^[0-9,]+\\s*원$").matches(next)||!add(next))return null
            }
        }
        if(pricePrefix.containsMatchIn(button)&&!add(button))return null
        if(request.kind=="clinic"&&candidates.isEmpty()&&request.maxWon==0L&&
            lines.any{it=="무료"||it=="결제금액 0원"||it=="총 결제금액 0원"})candidates.add(0)
        val amount=candidates.singleOrNull()?:return null
        if(amount>request.maxWon)return null
        // Hash all visible text/labels so changes to options, address, charges,
        // account or confirmation button invalidate the prior approval.
        val evidence=VisualGesture.digest(lines.joinToString("\n").toByteArray(Charsets.UTF_8))
        return Review(request.target,request.condition,amount,button,evidence)
    }
}

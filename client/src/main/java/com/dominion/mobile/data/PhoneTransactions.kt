package com.dominion.mobile.data

/** Explicit user scope, not a model-created purchase authorization. The final
 * approval is separate and bound to fresh foreground app evidence. */
data class PhoneTransactionRequest(val kind:String,val target:String,val condition:String,val maxWon:Long){
    fun checked(pkg:String):PhoneTransactionRequest{
        require(PhoneTransactions.supports(kind,pkg))
        require(target==target.trim()&&target.length in 2..100&&target.none(Char::isISOControl))
        require(condition==condition.trim()&&condition.length in 2..160&&condition.none(Char::isISOControl))
        require(maxWon in 0..100_000_000L && (kind in setOf("clinic","telecom")||maxWon>0))
        if(kind in setOf("finance","finance_bill")){
            // No full account numbers, passwords or recipient inference. The
            // user enters destination details inside the official bank app.
            require(condition.matches(Regex("[0-9]{4}"))&&maxWon in 1..10_000_000L)
            require(target.none(Char::isDigit))
        }
        return this
    }
}

object PhoneTransactions {
    private val apps=mapOf(
        "shopping" to setOf("com.coupang.mobile","com.navercorp.navershopping"),
        "delivery" to setOf("com.sampleapp","com.fineapp.yogiyo","com.coupang.mobile.eats"),
        "transport" to setOf("com.kakao.taxi"),
        "stay" to setOf("com.cultsotry.yanolja.nativeapp","kr.goodchoice.abouthere"),
        "clinic" to setOf("com.bbros.sayup"),
        "telecom" to PrivateAccountRead.carrierApps,
        "finance" to PrivateAccountRead.bankApps,
        "finance_bill" to PrivateAccountRead.bankApps
    )
    fun supports(kind:String,pkg:String)=pkg in apps[kind].orEmpty()
    fun supportedApp(pkg:String)=apps.values.any{pkg in it}
    fun deviceOnly(kind:String)=kind in setOf("finance","finance_bill","telecom")
    private val finalLabels=mapOf(
        "shopping" to setOf("결제하기","주문하기","주문 확정","결제"),
        "delivery" to setOf("결제하기","주문하기","주문 확정","배달 주문하기"),
        "transport" to setOf("호출하기","택시 호출하기","택시 호출","호출 확정"),
        "stay" to setOf("결제하기","예약 확정","예약하기","예약 및 결제"),
        "clinic" to setOf("예약하기","예약 확정","접수하기","접수 신청","진료 예약하기"),
        "telecom" to setOf("요금제 변경 신청","변경 신청","변경하기","부가서비스 신청","부가서비스 해지"),
        "finance" to setOf("송금하기","이체하기","송금 확인","이체 확인","이체 실행"),
        "finance_bill" to setOf("납부하기","납부 확인","납부 실행","공과금 납부")
    )
    private val nextLabels=setOf("장바구니","장바구니 담기","장바구니 보기","주문서 확인","주문서",
        "옵션 선택","객실 선택","예약 정보 확인","예약 정보 입력","택시 선택","차량 선택",
        "예약 시간 선택","진료시간 선택","일정 선택")
    private val receiptLabels=mapOf(
        "shopping" to setOf("주문 완료","주문이 완료되었습니다","결제 완료","결제가 완료되었습니다"),
        "delivery" to setOf("주문 완료","주문이 완료되었습니다","주문 접수 완료","주문이 접수되었습니다"),
        "transport" to setOf("호출 완료","호출 요청 완료","배차 완료","택시가 배차되었습니다"),
        "stay" to setOf("예약 완료","예약이 완료되었습니다","예약이 확정되었습니다"),
        "clinic" to setOf("예약 완료","예약이 완료되었습니다","접수 완료","접수가 완료되었습니다"),
        "telecom" to setOf("요금제 변경 완료","요금제가 변경되었습니다","부가서비스 신청 완료","부가서비스 해지 완료"),
        "finance" to setOf("송금 완료","송금이 완료되었습니다","이체 완료","이체가 완료되었습니다"),
        "finance_bill" to setOf("납부 완료","납부가 완료되었습니다","공과금 납부 완료")
    )
    private val money=Regex("(?<![0-9])([0-9]{1,3}(?:,[0-9]{3})+|[0-9]{1,9})\\s*원")
    private val pricePrefix=Regex("^[0-9,]+\\s*원\\s*")
    private val totalLabels=listOf("총 결제금액","총 결제 금액","최종 결제금액","최종 결제 금액",
        "결제 예정 금액","총 주문금액","총 주문 금액","결제금액","결제 금액","예상 운임","예상 요금","총 예약금액")
    private val authLabels=setOf("비밀번호 입력","인증번호 입력","본인 인증","본인인증","카드번호 입력","결제 비밀번호")
    fun authScreen(labels:List<String>)=labels.any{v->authLabels.any{v.contains(it,true)}}
    fun canAdvance(label:String)=label in nextLabels
    fun localMenus(kind:String)=when(kind){
        "telecom"->setOf("요금제","요금제 조회","요금제 변경","요금제 목록","부가서비스","부가서비스 조회","MY","전체메뉴")
        // Navigation only: no credentials, destination account selection,
        // authentication, or final action can be inferred from a menu name.
        "finance"->setOf("전체메뉴","이체 메뉴","송금 메뉴","이체 서비스","계좌이체 메뉴","이체/출금","이체/송금")
        "finance_bill"->setOf("전체메뉴","공과금","공과금 메뉴","공과금 조회","공과금 조회/납부","지로","세금/공과금")
        else->emptySet()
    }
    /** Only pre-authentication amount entry after the user selected the
     * official recipient/bill. Existing or ambiguous amounts are not replaced. */
    fun amountField(kind:String,hint:String):Boolean=when(kind){
        "finance"->hint in setOf("이체 금액","이체금액","송금 금액","송금금액","보낼 금액","이체할 금액")
        "finance_bill"->hint in setOf("납부 금액","납부금액","납부할 금액","총 납부금액")
        else->false
    }
    fun selectedDestination(request:PhoneTransactionRequest,lines:List<String>):Boolean{
        if(request.kind !in setOf("finance","finance_bill")||authScreen(lines)||receipt(request.kind,lines).isNotEmpty())return false
        val target=if(request.kind=="finance")listOf("받는 분: ","받는 분 ","수취인: ","수취인 ")
            else listOf("납부처: ","납부처 ")
        val matchingTarget=lines.any{it.trim()==request.target||target.any{prefix->it.trim()==prefix+request.target}}
        val masked=Regex("[*＊•●Xx]{2,}[ -]*"+Regex.escape(request.condition)+"(?![0-9])")
        val prefix=if(request.kind=="finance")listOf("계좌","받는 계좌","수취 계좌")
            else listOf("납부자번호","전자납부번호","고지서번호","고객번호")
        return matchingTarget&&lines.any{line->prefix.any{line.startsWith(it)}&&masked.containsMatchIn(line)}
    }
    fun localFinal(kind:String,labels:List<String>)=labels.distinct().filter{finalAction(kind,it)}.singleOrNull()
    fun isSearchField(label:String)=label in setOf("검색","Search","검색어 입력","검색어를 입력하세요","통합 검색",
        "장소 검색","장소, 주소 검색","상품 검색","음식점 검색","숙소 검색","병원 검색","출발지","도착지","목적지",
        "출발지 검색","도착지 검색","목적지 검색","장소를 입력하세요","어디로 갈까요?","어디로 가시나요?")
    fun finalAction(kind:String,label:String)=label.replace(pricePrefix,"") in finalLabels[kind].orEmpty()
    fun anyFinalAction(label:String)=finalLabels.keys.any{finalAction(it,label)}
    fun receipt(kind:String,labels:List<String>)=labels.map{it.trim().trimEnd('.','!')}
        .filter{it in receiptLabels[kind].orEmpty()}.distinct()
    fun completedReceipt(request:PhoneTransactionRequest,lines:List<String>):List<String>{
        val result=receipt(request.kind,lines)
        if(request.kind !in setOf("finance","finance_bill"))return result
        val target=lines.any{it.trim()==request.target||
            listOf("받는 분: ","받는 분 ","납부처: ","납부처 ").any{prefix->it.trim()==prefix+request.target}}
        val amount=lines.any{line->money.findAll(line).any{
            it.groupValues[1].replace(",","").toLongOrNull()==request.maxWon
        }}
        return if(target&&amount)result else emptyList()
    }
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
            (when(kind){
                "transport"->"화면 예상 운임 (실제 변동 가능): "
                "telecom"->"화면 월 이용료 또는 변경 비용: "
                "finance"->"화면 이체 금액: "
                "finance_bill"->"화면 납부 총액: "
                else->"화면 결제금액: "
            })+amount+"원\n실행: "+button
    }
    fun review(request:PhoneTransactionRequest,pkg:String,button:String,lines:List<String>):Review?{
        request.checked(pkg)
        if(!finalAction(request.kind,button)||authScreen(lines)||receipt(request.kind,lines).isNotEmpty())return null
        if(request.kind=="finance"){
            // Require exact recipient and an explicitly masked destination
            // suffix; a random number elsewhere on the page is not evidence.
            if(lines.none{it.trim()==request.target||it.trim()=="받는 분 "+request.target||it.trim()=="받는 분: "+request.target})return null
            if(lines.none{line->Regex("[*＊•●Xx]{2,}[ -]*"+Regex.escape(request.condition)+"(?![0-9])").containsMatchIn(line)})return null
        }else if(request.kind=="finance_bill"){
            if(lines.none{it.trim()==request.target||it.trim()=="납부처 "+request.target||it.trim()=="납부처: "+request.target})return null
            val prefix=listOf("납부자번호","전자납부번호","고지서번호","고객번호")
            if(lines.none{line->prefix.any{line.startsWith(it)}&&
                Regex("[*＊•●Xx]{2,}[ -]*"+Regex.escape(request.condition)+"(?![0-9])").containsMatchIn(line)})return null
        }else if(lines.none{it.contains(request.target)}||lines.none{it.contains(request.condition)})return null
        // Only explicitly labeled totals or the price printed on the final button.
        // Never mistake an individual item, crossed-out price, coupon, or range for a total.
        val candidates=mutableSetOf<Long>()
        fun add(s:String):Boolean{
            val matches=money.findAll(s).toList()
            if(matches.size!=1||s.contains("~")||s.contains("∼")||s.contains("부터")||s.contains("최대"))return false
            val amount=matches.single().groupValues[1].replace(",","").toLongOrNull()?:return false
            candidates.add(amount);return true
        }
        val totals=when(request.kind){
            "telecom"->listOf("변경 후 월 요금","변경 후 월정액","월 이용료","월정액","월 요금","월 기본료")
            "finance"->listOf("이체 금액","이체금액","송금 금액","송금금액","보낼 금액")
            "finance_bill"->listOf("총 납부금액","총 납부 금액","납부 금액","납부금액","납부할 금액")
            else->totalLabels
        }
        for(i in lines.indices){
            val line=lines[i]
            val prefix=totals.firstOrNull{line.startsWith(it)}?:continue
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
        if(request.kind in setOf("finance","finance_bill")){
            if(amount!=request.maxWon)return null
            // Require an explicit zero transfer fee; never silently exceed the
            // exact amount the owner authorized.
            val fees=lines.mapIndexedNotNull{i,line->
                val prefix=listOf("이체 수수료","송금 수수료","납부 수수료","수수료").firstOrNull{line.startsWith(it)}
                    ?:return@mapIndexedNotNull null
                line.removePrefix(prefix).trim().trimStart(':').trim().ifBlank{lines.getOrNull(i+1).orEmpty()}
            }
            if(fees.isEmpty()||fees.any{it !in setOf("0원","무료","면제")})return null
        }
        // Hash all visible text/labels so changes to options, address, charges,
        // account or confirmation button invalidate the prior approval.
        val evidence=VisualGesture.digest(lines.joinToString("\n").toByteArray(Charsets.UTF_8))
        return Review(request.target,request.condition,amount,button,evidence)
    }
}

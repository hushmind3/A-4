package com.dominion.mobile.data

/** Account-scoped read-only operation. No cloud model, account credentials,
 * transaction action, account number or raw screen dump is involved. */
data class PhonePrivateRead(val kind:String,val field:String){
    fun checked(pkg:String):PhonePrivateRead{
        require(PrivateAccountRead.supports(kind,pkg)&&field in PrivateAccountRead.fields(kind))
        return this
    }
}
object PrivateAccountRead {
    val bankApps=setOf("viva.republica.toss","com.kbstar.kbbank","com.wooribank.smart.npib",
        "com.nonghyup.nhallonebank","com.shinhan.sbanking","com.kakaobank.channel")
    val carrierApps=setOf("Com.sktelecom.minit","com.sktelecom.minit","com.ktshow.cs","com.lguplus.mobile.cs")
    fun supports(kind:String,pkg:String)=when(kind){
        "finance"->pkg in bankApps
        "telecom"->pkg in carrierApps
        else->false
    }
    fun fields(kind:String):List<String> =when(kind){
        "finance"->listOf("총 자산","총 잔액","출금가능금액","예금 잔액","화면 거래내역 집계")
        "telecom"->listOf("이번 달 청구금액","이번 달 요금","미납 요금","남은 데이터","사용한 데이터")
        else->emptyList()
    }
    private val names=mapOf(
        "총 자산" to setOf("총 자산","총자산","내 자산","보유 자산"),
        "총 잔액" to setOf("총 잔액","총잔액","전체 잔액"),
        "출금가능금액" to setOf("출금가능금액","출금 가능 금액"),
        "예금 잔액" to setOf("예금 잔액","예금잔액","예금 합계"),
        "이번 달 청구금액" to setOf("이번 달 청구금액","이번달 청구금액","청구금액","청구 금액"),
        "이번 달 요금" to setOf("이번 달 요금","이번달 요금","이번 달 예상 요금","실시간 요금"),
        "미납 요금" to setOf("미납 요금","미납요금","미납금액"),
        "남은 데이터" to setOf("남은 데이터","잔여 데이터","데이터 잔여량"),
        "사용한 데이터" to setOf("사용한 데이터","데이터 사용량","사용 데이터")
    )
    fun menus(kind:String)=when(kind){
        "finance"->setOf("자산","내 자산","자산 조회","계좌 조회","전체 계좌","계좌","예금","전체메뉴","조회",
            "거래내역","거래 내역","입출금 내역","입출금내역")
        "telecom"->setOf("MY","마이","나의 정보","요금조회","요금 조회","요금","이용량","사용량","데이터 사용량",
            "청구요금","청구 요금","이번 달 요금","고객센터","전체메뉴")
        else->emptySet()
    }
    private val won=Regex("^[0-9]{1,3}(?:,[0-9]{3})+\\s*원$|^[0-9]{1,12}\\s*원$")
    private val data=Regex("^[0-9]{1,6}(?:\\.[0-9]{1,3})?\\s*(?:GB|MB|KB|TB)$",RegexOption.IGNORE_CASE)
    private fun value(field:String,raw:String):String? {
        val clean=raw.trim().trimStart(':').trim()
        val valid=if(field in setOf("남은 데이터","사용한 데이터"))data.matches(clean) else won.matches(clean)
        return clean.takeIf{valid}
    }
    /** Aggregate only individually identifiable date/direction/amount rows
     * actually visible on the screen; identical displayed rows are collapsed.
     * Never infer direction from a balance or claim the full account history. */
    fun aggregateVisibleTransactions(lines:List<String>):String?{
        val date=Regex("(?<![0-9])(?:20[0-9]{2}[./-])?[0-9]{1,2}[./-][0-9]{1,2}(?![0-9])")
        val direction=Regex("(?<![가-힣])(입금|출금)(?![가-힣])")
        val amount=Regex("(?<![0-9])([0-9]{1,3}(?:,[0-9]{3})+|[0-9]{1,12})\\s*원")
        val rows=lines.distinct().mapNotNull{line->
            if(line.length !in 6..250||!date.containsMatchIn(line))return@mapNotNull null
            val type=direction.findAll(line).toList().singleOrNull()?.value?:return@mapNotNull null
            val value=amount.findAll(line).toList().singleOrNull()?.groupValues?.get(1)
                ?.replace(",","")?.toLongOrNull()?:return@mapNotNull null
            if(value !in 1..1_000_000_000_000L)return@mapNotNull null
            type to value
        }.take(100)
        if(rows.isEmpty())return null
        val inbound=rows.filter{it.first=="입금"}.sumOf{it.second}
        val outbound=rows.filter{it.first=="출금"}.sumOf{it.second}
        return "현재 화면 고유 표시 행 "+rows.size+"건 · 입금 "+inbound+"원 · 출금 "+outbound+
            "원 · 차액 "+(inbound-outbound)+"원 (전체 내역 아님, 동일 표시 중복 제외)"
    }
    fun extract(scope:PhonePrivateRead,lines:List<String>):String?{
        if(scope.kind=="finance"&&scope.field=="화면 거래내역 집계")return aggregateVisibleTransactions(lines)
        val aliases=names[scope.field].orEmpty()
        val found=mutableSetOf<String>()
        lines.forEachIndexed{i,line->
            val trimmed=line.trim()
            for(alias in aliases){
                if(trimmed==alias)lines.getOrNull(i+1)?.let{value(scope.field,it)}?.let(found::add)
                else if(trimmed.startsWith(alias+" ")||trimmed.startsWith(alias+":"))
                    value(scope.field,trimmed.removePrefix(alias))?.let(found::add)
            }
        }
        return found.singleOrNull()
    }
}

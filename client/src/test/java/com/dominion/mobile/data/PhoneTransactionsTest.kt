package com.dominion.mobile.data

import org.junit.Assert.*
import org.junit.Test

class PhoneTransactionsTest {
    private val app="com.coupang.mobile"
    private val request=PhoneTransactionRequest("shopping","무선 청소기","화이트 1개",20000)
    private val lines=listOf("무선 청소기","화이트 1개","총 결제금액","19,900원","결제하기")
    @Test fun exactTargetConditionsAndTotalAreRequired(){
        assertEquals(19900L,PhoneTransactions.review(request,app,"결제하기",lines)?.amount)
        assertNull(PhoneTransactions.review(request.copy(maxWon=19000),app,"결제하기",lines))
        assertNull(PhoneTransactions.review(request,app,"결제하기",lines.filterNot{it=="화이트 1개"}))
        assertNull(PhoneTransactions.review(request,app,"결제하기",lines+"결제 금액 21,900원"))
        assertNull(PhoneTransactions.review(request,app,"결제하기",lines+"비밀번호 입력"))
        assertNull(PhoneTransactions.review(request,app,"결제하기",lines+"주문 완료"))
    }
    @Test fun finalActionCannotBeSmuggledAsNavigation(){
        val commit=PhoneNavigationPlan(app,listOf(PhoneNavigationStep("commit_transaction","결제하기")),2)
        assertTrue(PhoneTransactions.validPlan(commit,request,"무선 청소기"))
        assertFalse(PhoneNavigationPlan.valid(commit.copy(app="com.kakao.talk")))
        assertFalse(PhoneNavigationPlan.valid(commit.copy(steps=listOf(
            PhoneNavigationStep("click","주문하기"),PhoneNavigationStep("inspect","화면")))))
        assertTrue(PhoneTransactions.receipt("shopping",listOf("주문 완료")).isNotEmpty())
        assertTrue(PhoneTransactions.receipt("shopping",listOf("완료","장바구니")).isEmpty())
    }
}

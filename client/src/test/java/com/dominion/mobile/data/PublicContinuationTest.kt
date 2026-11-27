package com.dominion.mobile.data

import org.junit.Assert.*
import org.junit.Test

class PublicContinuationTest {
    private val app="com.coupang.mobile"
    private val goal="무선청소기 검색"
    private fun plan(vararg steps:PhoneNavigationStep)=PhoneNavigationPlan(app,steps.toList(),2)
    private val inspect=PhoneNavigationStep("inspect","화면")
    @Test fun oneObservedActionThenInspect(){
        val click=PhoneNavigationStep("click","검색")
        assertTrue(PhonePublicResult.continuation(plan(click,inspect),goal,listOf("검색"),emptyList()))
        assertFalse(PhonePublicResult.continuation(plan(click,click,inspect),goal,listOf("검색"),emptyList()))
        assertFalse(PhonePublicResult.continuation(plan(click,inspect),goal,listOf("홈"),emptyList()))
        assertFalse(PhonePublicResult.continuation(plan(PhoneNavigationStep("expect","검색")),goal,listOf("검색"),emptyList()))
    }
    @Test fun resultMustBeObserved(){
        val result=plan(PhoneNavigationStep("report_result","무선청소기 100,000원"))
        assertFalse(PhonePublicResult.continuation(result,goal,emptyList(),emptyList()))
        assertTrue(PhonePublicResult.continuation(result,goal,emptyList(),listOf("무선청소기 100,000원")))
    }
    @Test fun observationExcludesSensitiveAndTransactionLabels(){
        assertEquals(listOf("검색","상품 목록"),PhonePublicResult.observationControls(
            listOf("검색","주문하기","비밀번호","010-1234-5678","상품 목록","검색")))
    }
    @Test fun searchSubmitCanBeDelegatedOnlyForKnownSearchField(){
        val search=PhoneNavigationStep("search_submit","검색")
        assertTrue(PhoneNavigationPlan.permitsDelegatedRead(plan(search,inspect),search,goal))
        val send=PhoneNavigationStep("search_submit","메시지 전송")
        assertFalse(PhoneNavigationPlan.permitsDelegatedRead(plan(send,inspect),send,goal))
    }
}

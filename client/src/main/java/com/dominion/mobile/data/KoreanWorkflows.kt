package com.dominion.mobile.data

data class ServiceField(val label: String, val example: String, val required: Boolean = true)
data class ServiceWorkflow(val id: String, val title: String, val fields: List<ServiceField>,
    val repeatable: Boolean = false)
data class ServicePortal(val label: String, val url: String)

object KoreanWorkflows {
    private fun field(label: String, example: String, required: Boolean = true) = ServiceField(label, example, required)
    private fun flow(id: String, title: String, vararg fields: ServiceField, repeatable: Boolean = false) =
        ServiceWorkflow(id, title, fields.toList(), repeatable)

    private val entries = mapOf(
        "gov" to listOf(flow("certificate", "증명서·민원 준비", field("민원명", "주민등록등본"), field("제출 용도", "학교 제출", false))),
        "welfare" to listOf(flow("benefits", "지원제도 조사", field("지원 분야", "주거·아동·장애"), field("가구 조건", "개인식별정보 제외", false), repeatable = true)),
        "notice" to listOf(flow("deadline", "행정 기한 확인", field("알림 종류", "자동차 검사·납부"), repeatable = true)),
        "petition" to listOf(flow("draft", "민원서 초안", field("문제 요약", "처리 지연 및 요청 사항"), field("담당기관", "알고 있는 경우만", false))),
        "law" to listOf(flow("law", "법령·개정 조사", field("법령 또는 주제", "국민기초생활 보장법"), field("기준일", "오늘 또는 YYYY-MM-DD", false), repeatable = true)),
        "school" to listOf(
            flow("meal", "급식 식단", field("학교명", "학교 정식 명칭"), field("조회 기간", "이번 주"), repeatable = true),
            flow("calendar", "학사 일정", field("학교명", "학교 정식 명칭"), field("조회 기간", "이번 달"), repeatable = true)),
        "employment" to listOf(flow("jobs", "채용·훈련 공고", field("희망 직무", "사무·개발"), field("조건", "근무지·시간", false), repeatable = true)),
        "tax" to listOf(flow("tax", "국세 신고 준비", field("세목", "종합소득세"), field("귀속연도", "2026"))),
        "localtax" to listOf(flow("localtax", "지방세 일정", field("세목", "자동차세"), repeatable = true)),
        "health" to listOf(flow("provider", "공개 의료기관 검색", field("기관 종류", "검진·치과·약국"), field("희망 조건", "운영시간 등", false))),
        "consumer" to listOf(flow("refund", "환불·리콜 확인", field("상품 또는 계약", "상품명"), field("거래 상황", "구입 후 문제"))),
        "transit" to listOf(flow("route", "교통 경로·시간표", field("출발지", "역·지역"), field("도착지", "역·지역"), field("출발 희망일시", "YYYY-MM-DD HH:mm"))),
        "housing" to listOf(
            flow("lh", "공공임대 공고", field("희망 유형", "국민임대·매입임대"), repeatable = true),
            flow("price", "실거래·공시가격", field("주택 유형·지역", "아파트·시군구"), field("비교 기간", "최근 3개월"), repeatable = true)),
        "food" to listOf(flow("prices", "장보기 가격 비교", field("품목", "쌀·계란"), field("규격·수량", "1kg·30개"), repeatable = true)),
        "weather" to listOf(flow("forecast", "날씨·미세먼지", field("조회 지역", "시군구"), field("기간", "오늘·내일"), repeatable = true)),
        "waste" to listOf(flow("waste", "배출 시간·방법", field("시군구·동", "행정동"), field("품목", "음식물·재활용"), repeatable = true)),
        "delivery" to listOf(flow("tracking", "배송 상태", field("배송사", "택배사"), field("운송장", "사용자가 제공한 번호")))
    )
    fun forService(id: String): List<ServiceWorkflow> = entries[id] ?: listOf(flow("custom", "직접 요청"))
    fun compose(workflow: ServiceWorkflow, values: Map<String, String>, extra: String): String {
        require(workflow.fields.all { !it.required || !values[it.label].isNullOrBlank() })
        return buildString {
            append("업무 유형: ").append(workflow.title)
            workflow.fields.forEach { field ->
                values[field.label]?.trim()?.takeIf { it.isNotBlank() }?.let {
                    append("\n").append(field.label).append(": ").append(it.take(300))
                }
            }
            if (extra.isNotBlank()) append("\n추가 요청: ").append(extra.trim().take(1200))
        }
    }

    fun portals(id: String): List<ServicePortal> = when (id) {
        "school" -> listOf(ServicePortal("나이스 공개 교육데이터", "https://open.neis.go.kr/"))
        "housing" -> listOf(ServicePortal("LH 임대주택 공고", "https://apply.lh.or.kr/lhapply/apply/wt/wrtanc/selectWrtancList.do?mi=1026"))
        "food" -> listOf(ServicePortal("KAMIS 소매가격", "https://www.kamis.or.kr/customer/price/agricultureRetail/catalogue.do"))
        "weather" -> listOf(ServicePortal("기상청 날씨누리", "https://www.weather.go.kr/w/index.do"),
            ServicePortal("에어코리아", "https://www.airkorea.or.kr/web/dustForecast"))
        "waste" -> listOf(ServicePortal("전국 생활쓰레기 배출정보", "https://www.data.go.kr/data/15025450/standard.do"))
        "transit" -> listOf(ServicePortal("국가대중교통정보센터", "https://main.kotsa.or.kr/portal/contents.do?menuCode=01080800"))
        else -> emptyList()
    }
}

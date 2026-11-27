package com.dominion.mobile.data

import com.dominion.mobile.data.model.ExploreCategory
import com.dominion.mobile.data.model.ExploreTemplate
import java.net.URI

data class KoreanService(
    val id: String, val title: String, val summary: String, val url: String,
    val hotline: String? = null, val mapQuery: String? = null,
    val steps: List<String>, val instruction: String,
    val skillNames: List<String> = emptyList()
) {
    fun template() = ExploreTemplate("kr_" + id, title, title, summary, summary,
        ExploreCategory.KOREA, 0xFF3FB6A8, steps, instruction)

    fun request(region: String, details: String): String = buildString {
        append("대한민국 생활·행정 업무: ").append(title).append("\n조회 출발점: ").append(url)
        KoreanWorkflows.portals(id).forEach { append("\n공식 참고: ").append(it.label).append(" ").append(it.url) }
        if (region.isNotBlank()) append("\n사용자가 입력한 지역: ").append(region.trim().take(100))
        append("\n사용자 요청: ").append(details.trim().take(3000))
        append("\n수행 범위: ").append(instruction)
        if (skillNames.isNotEmpty()) {
            append("\n관련 k-skill 후보: ").append(skillNames.joinToString(", "))
            append(". 현재 실행 환경에서 활성 스킬과 실제 도구 가용성을 먼저 확인하고 사용 가능한 것만 실행하세요. 스킬 설치만으로 조회가 성공했다고 주장하지 마세요. 외부 프록시를 사용해야 하면 개인정보 전송 없이 가능한 범위를 우선하세요.")
        }
        append("\n최신 출처를 실제 조회하고 링크·조회시각·핵심 결과를 보고하세요. 행정 업무는 시행일, 조건, 준비물, 기한, 문의처를 추가하고 조회 업무는 비교 기준과 데이터 갱신시각을 표시하세요.")
        append("\n접근 실패나 인증 필요는 명시하세요. 신청·발급·제출·결제·개인정보 제공은 사용자 검토와 승인 전에는 실행하지 마세요. 인증번호, 비밀번호, 주민등록번호를 요구하지 마세요. 접수번호나 증명서를 생성했다고 추정하지 마세요.")
    }
}

object KoreanServices {
    val entries = listOf(
        KoreanService("gov", "정부24 · 민원·증명서", "등본, 초본, 가족·이사 관련 민원 안내", "https://www.gov.kr/", "110", "행정복지센터",
            listOf("민원 종류와 관할 확인", "공식 발급 조건·준비물 확인", "온라인 신청 경로 안내"),
            "요청 민원의 실제 담당기관과 발급 방법, 본인 인증 요건을 조회하고 안내하세요."),
        KoreanService("welfare", "복지로 · 지원금·복지", "복지급여, 한부모·아동·장애·주거 지원", "https://www.bokjiro.go.kr/", "129", "행정복지센터",
            listOf("지원제도 검색", "가구 조건과 신청기간 확인", "필요 서류 및 관할기관 정리"),
            "중앙정부와 해당 지자체 제도를 함께 조사하고 확정된 자격과 추가 확인이 필요한 항목을 분리하세요."),
        KoreanService("notice", "국민비서 · 행정 알림", "행정 기한, 민원 처리, 검사·납부 알림", "https://www.ips.go.kr/",
            steps = listOf("공식 알림 종류 확인", "신청 및 수신 채널 안내", "사용자가 지정한 기한 정리"),
            instruction = "국민비서에서 신청 가능한 알림과 사용자가 직접 등록해야 할 절차를 확인하세요. 실제 가입했다고 주장하지 마세요."),
        KoreanService("petition", "국민신문고 · 민원·제안", "담당기관 확인, 민원서 초안 준비", "https://www.epeople.go.kr/", "110",
            steps = listOf("요청 내용과 담당기관 정리", "근거 및 첨부 목록 확인", "제출 전 민원 초안 작성"),
            instruction = "민원 문안을 작성하고 공식 접수 방법과 처리 확인 절차를 안내하세요. 민원 자체를 자동 제출하지 마세요."),
        KoreanService("law", "국가법령정보센터", "법령, 시행일, 행정규칙 확인", "https://law.go.kr/",
            steps = listOf("관련 법령 조회", "현행 시행일 확인", "조문 링크와 핵심 정리"),
            instruction = "현행 법령, 시행 예정 개정, 법령이 아닌 안내자료를 구분하여 공식 근거를 정리하세요.", skillNames = listOf("korean-law-search")),
        KoreanService("school", "나이스 · 학교·학부모", "학사일정, 급식, 학부모 서비스", "https://parents.neis.go.kr/",
            steps = listOf("학교·교육기관 확인", "공개 일정과 안내 확인", "학부모 인증이 필요한 항목 구분"),
            instruction = "공개 학교 안내를 확인하고 개인 학생 정보는 사용자가 공식 로그인 화면에서 직접 조회하도록 하세요."),
        KoreanService("employment", "고용24 · 일자리·실업급여", "채용·직업훈련·취업지원·실업급여", "https://www.work24.go.kr/",
            steps = listOf("희망 조건 확인", "공고와 지원 조건 조회", "기한 및 신청 경로 정리"),
            instruction = "실제 공개 공고와 고용24 제도를 비교하고 개인 자격은 확정하지 마세요."),
        KoreanService("tax", "홈택스 · 국세", "연말정산, 세금 신고 및 증명서", "https://www.hometax.go.kr/",
            steps = listOf("세목·귀속연도 확인", "신고 기한과 공식 안내 확인", "준비 서류 정리"),
            instruction = "국세청 공식 자료를 근거로 준비 절차를 조사하고 신고·납부는 하지 마세요."),
        KoreanService("localtax", "위택스 · 지방세", "자동차세, 재산세, 지방세 조회 절차", "https://www.wetax.go.kr/",
            steps = listOf("세목과 지자체 확인", "공식 납부 기한 확인", "조회·납부 경로 안내"),
            instruction = "실제 부과세액은 개인 인증 없이 추정하지 말고 공식 조회 경로를 안내하세요."),
        KoreanService("health", "건강보험 · 검진·요양", "검진기관, 장기요양기관, 보험 민원", "https://www.nhis.or.kr/",
            mapQuery = "건강검진기관", steps = listOf("지역과 목적 확인", "기관 공개 정보 조회", "예약·인증 절차 확인"),
            instruction = "공개 기관 정보와 확인시각을 제공하고 의료 진단이나 개인 검진 자격을 확정하지 마세요.",
            skillNames = listOf("nhis-care-checkup-search")),
        KoreanService("consumer", "한국소비자원 · 환불·피해구제", "가격 비교, 제품 리콜, 환불 상담", "https://www.kca.go.kr/odr/pg/ma/cnsutInfo.do", "1372",
            steps = listOf("거래 조건 확인", "공식 소비자 정보 조회", "문의 초안 작성"),
            instruction = "상품과 계약 조건을 검토하고 공식 리콜·분쟁 기준을 확인하세요.",
            skillNames = listOf("consumer-price-safety-search")),
        KoreanService("transit", "교통 · 철도·버스·길찾기", "출발지·목적지·날짜별 이동 후보 비교", "https://github.com/NomaDamas/k-skill/blob/main/docs/features/korean-transit-route.md",
            mapQuery = "대중교통", steps = listOf("출발지·목적지 확인", "실제 시간표·이동 후보 조회", "환승·비용·출처 정리"),
            instruction = "대중교통 및 철도 조회 스킬을 사용하고 예매나 결제는 실행하지 마세요.",
            skillNames = listOf("korean-transit-route", "railway-timetable")),
        KoreanService("housing", "주거 · 공시가격·임대주택", "실거래가, 공시가격, LH 공고", "https://github.com/NomaDamas/k-skill/blob/main/docs/features/lh-notice-search.md",
            steps = listOf("지역과 주택 유형 확인", "공개 거래·공고 조회", "공고 마감일과 조건 확인"),
            instruction = "공개 데이터와 현재 공고를 비교하고 개인 자격이나 가격을 단정하지 마세요.",
            skillNames = listOf("real-estate-search", "housing-official-price", "lh-notice-search")),
        KoreanService("food", "장보기 · 농수산물·가격", "공식 농산물 가격 및 제품 가격 비교", "https://github.com/NomaDamas/k-skill/blob/main/docs/features/kamis-food-price.md",
            steps = listOf("품목·지역·단위 확인", "공개 가격 조회", "동일 단위 비교"),
            instruction = "같은 규격과 중량으로 가격을 비교하고 구매하지 마세요.",
            skillNames = listOf("kamis-food-price", "danawa-price-search")),
        KoreanService("weather", "날씨 · 미세먼지", "지역별 예보와 대기질 확인", "https://github.com/NomaDamas/k-skill/blob/main/docs/features/korea-weather.md",
            steps = listOf("지역과 시간 확인", "기상·대기질 조회", "갱신시각과 생활 참고사항 정리"),
            instruction = "공식 기상·대기질 데이터를 실제 조회하세요. 현재 데이터가 없으면 없다고 알리세요.",
            skillNames = listOf("korea-weather", "fine-dust-location")),
        KoreanService("waste", "분리수거 · 생활쓰레기", "배출 요일·시간·장소 및 담당부서", "https://github.com/NomaDamas/k-skill/blob/main/docs/features/household-waste-info.md",
            steps = listOf("시군구 및 배출 품목 확인", "지자체 공개 정보 조회", "배출 방법과 담당기관 안내"),
            instruction = "해당 지역의 실제 배출정보를 확인하고 최신 지자체 안내와 비교하세요.",
            skillNames = listOf("household-waste-info")),
        KoreanService("delivery", "택배 · 배송조회", "운송장과 배송사 기준 조회", "https://github.com/NomaDamas/k-skill/blob/main/docs/features/delivery-tracking.md",
            steps = listOf("배송사와 송장 확인", "배송 상태 조회", "갱신시각 및 문의 경로 안내"),
            instruction = "사용자가 명시적으로 제공한 운송장만 배송사 공식 조회에 사용하고 외부 프록시에 전달하지 마세요.",
            skillNames = listOf("delivery-tracking"))
    )

    fun sourceLabel(service: KoreanService): String =
        if (URI(service.url).host == "github.com") "스킬 원문" else "공식 사이트"

    fun officialUrl(service: KoreanService): String {
        require(entries.any { it.id == service.id && it.url == service.url })
        val uri = URI(service.url)
        require(uri.scheme == "https" && uri.userInfo == null && !uri.host.isNullOrBlank())
        return uri.toString()
    }
}

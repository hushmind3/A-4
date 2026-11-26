package com.dominion.mobile.data

import com.dominion.mobile.data.model.ExploreCategory
import com.dominion.mobile.data.model.ExploreTemplate
import java.net.URI

data class KoreanService(
    val id: String, val title: String, val summary: String, val url: String,
    val hotline: String? = null, val mapQuery: String? = null,
    val steps: List<String>, val instruction: String
) {
    fun template() = ExploreTemplate("kr_" + id, title, title, summary, summary,
        ExploreCategory.KOREA, 0xFF3FB6A8, steps, instruction)

    fun request(region: String, details: String): String = buildString {
        append("대한민국 생활·행정 업무: ").append(title).append("\n공식 출발점: ").append(url)
        if (region.isNotBlank()) append("\n사용자가 입력한 지역: ").append(region.trim().take(100))
        append("\n사용자 요청: ").append(details.trim().take(3000))
        append("\n수행 범위: ").append(instruction)
        append("\n최신 공식 기관 원문을 실제 조회하고 링크, 시행일/기준일, 신청 조건, 준비물, 기한, 문의처를 구분해 보고하세요.")
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
            instruction = "현행 법령, 시행 예정 개정, 법령이 아닌 안내자료를 구분하여 공식 근거를 정리하세요."),
        KoreanService("school", "나이스 · 학교·학부모", "학사일정, 급식, 학부모 서비스", "https://parents.neis.go.kr/",
            steps = listOf("학교·교육기관 확인", "공개 일정과 안내 확인", "학부모 인증이 필요한 항목 구분"),
            instruction = "공개 학교 안내를 확인하고 개인 학생 정보는 사용자가 공식 로그인 화면에서 직접 조회하도록 하세요.")
    )

    fun officialUrl(service: KoreanService): String {
        require(entries.any { it.id == service.id && it.url == service.url })
        val uri = URI(service.url)
        require(uri.scheme == "https" && uri.userInfo == null && !uri.host.isNullOrBlank())
        return uri.toString()
    }
}

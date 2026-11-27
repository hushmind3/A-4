package com.dominion.mobile.data

import com.google.gson.JsonObject
import com.google.gson.JsonParser

data class PhoneNavigationStep(val action: String, val label: String, val value: String? = null)
data class PhoneNavigationPlan(val app: String, val steps: List<PhoneNavigationStep>) {
    companion object {
        val apps = mapOf("com.nhn.android.nmap" to "네이버 지도", "net.daum.android.map" to "카카오맵",
            "com.google.android.youtube" to "YouTube", "com.google.android.calendar" to "Google 캘린더",
            "com.google.android.apps.nbu.files" to "Files by Google", "com.sec.android.app.myfiles" to "삼성 내 파일")
        private val navigationLabels = setOf("검색","장소 검색","통합 검색","장소, 주소 검색","검색어 입력",
            "길찾기","도보","자동차","대중교통","Search","Directions","지도","출발지","도착지")
        private val searchLabels = setOf("검색","장소 검색","통합 검색","장소, 주소 검색","검색어 입력","Search","출발지","도착지")
        private val commonSearch = setOf("검색","Search","검색어 입력","검색어를 입력하세요","Search files","파일 검색",
            "YouTube 검색","Search YouTube","일정 검색","Search events")
        private val appNavigation = mapOf(
            "com.google.android.youtube" to setOf("홈","Home","구독","Subscriptions","Shorts"),
            "com.google.android.calendar" to setOf("오늘","Today","일정","Schedule","월","Month","주","Week","일","Day"),
            "com.google.android.apps.nbu.files" to setOf("찾아보기","Browse","다운로드","Downloads","이미지","Images","문서","Documents"),
            "com.sec.android.app.myfiles" to setOf("다운로드","Downloads","이미지","Images","문서","Documents","내장 저장공간","Internal storage")
        )
        fun labels(app:String, action:String):Set<String> = when(action) {
            "click" -> if(app in appNavigation) commonSearch + appNavigation.getValue(app) else navigationLabels
            "search_text" -> if(app in appNavigation) commonSearch else searchLabels
            "scroll_forward", "scroll_backward" -> if (app in apps) setOf("화면", "Screen") else emptySet()
            else -> emptySet()
        }
        fun valid(plan: PhoneNavigationPlan): Boolean = plan.app in apps &&
            plan.steps.size in 1..8 && plan.steps.last().action == "expect" &&
            plan.steps.all { step ->
                step.label.isNotBlank() && step.label.length <= 100 && step.label.none(Char::isISOControl) &&
                    when (step.action) {
                        "expect" -> step.value == null
                        "click", "scroll_forward", "scroll_backward" ->
                            step.value == null && step.label in labels(plan.app, step.action)
                        "search_text" -> step.label in labels(plan.app, step.action) &&
                            step.value?.let { it.isNotBlank() && it.length <= 250 && it.none(Char::isISOControl) } == true
                        else -> false
                    }
            }
        fun fromMessage(text: String): PhoneNavigationPlan? = runCatching {
            require(text.length <= 16000)
            val fence = 96.toChar().toString().repeat(3)
            val matches = Regex("(?s)" + fence + "dominion-phone-plan[ \\t]*\\r?\\n(.*?)\\r?\\n" + fence)
                .findAll(text).take(2).toList()
            val json = if (matches.size == 1) matches.single().groupValues[1]
                else if (matches.isEmpty()) text.trim() else error("Multiple plans")
            parse(json)
        }.getOrNull()

        fun parse(json: String): PhoneNavigationPlan {
            require(json.length in 1..12000)
            val row = JsonParser.parseString(json).asJsonObject
            require(row.keySet() == setOf("schema","app","steps"))
            fun read(o: JsonObject, name: String, max: Int): String {
                val v = requireNotNull(o.get(name))
                require(v.isJsonPrimitive && v.asJsonPrimitive.isString)
                return v.asString.also { require(it.isNotBlank() && it.length <= max && it.none(Char::isISOControl)) }
            }
            require(read(row,"schema",40) == "dominion-phone-plan/v1")
            val app = read(row,"app",100)
            require(app in apps)
            val items = row.getAsJsonArray("steps")
            require(items.size() in 1..8)
            val steps = items.map {
                val step = it.asJsonObject
                val action = read(step,"action",20)
                val label = read(step,"label",100)
                when (action) {
                    "click", "scroll_forward", "scroll_backward" -> {
                        require(step.keySet() == setOf("action","label") && label in labels(app,action))
                        PhoneNavigationStep(action,label)
                    }
                    "search_text" -> {
                        require(step.keySet() == setOf("action","label","value") && label in labels(app,action))
                        PhoneNavigationStep(action,label,read(step,"value",250))
                    }
                    "expect" -> {
                        require(step.keySet() == setOf("action","label"))
                        PhoneNavigationStep(action,label)
                    }
                    else -> error("Unsupported device operation")
                }
            }
            return PhoneNavigationPlan(app,steps).also { require(valid(it)) }
        }
        fun prompt(request: String): String = "사용자 요청: " + request.take(2000) + "\n" + """
            A-4의 실제 휴대폰 앱 탐색 계획을 작성하세요. 도구 실행이나 원격 코드가 아닙니다. 사용자가 실제 화면에서 각 단계를 승인합니다.
            dominion-phone-plan 코드 펜스 하나로 아래 형식의 JSON을 반환하세요.
            {"schema":"dominion-phone-plan/v1","app":"com.nhn.android.nmap","steps":[{"action":"click","label":"검색"},{"action":"search_text","label":"검색어 입력","value":"사용자가 요청한 장소"},{"action":"expect","label":"실제 확인할 화면 문구"}]}
            지원 앱: com.nhn.android.nmap, net.daum.android.map, com.google.android.youtube,
            com.google.android.calendar, com.google.android.apps.nbu.files, com.sec.android.app.myfiles.
            최대 8단계, 마지막은 expect. 지도 외 앱은 검색·홈·일정·다운로드·이미지·문서 등의 탐색 메뉴만 지원합니다.
            앱별 실제 허용 클릭 문구:
            ${apps.keys.joinToString("\n") { app -> app + ": " + labels(app, "click").sorted().joinToString(", ") }}
            search_text는 실제 검색·출발지·도착지 입력란에만 사용합니다.
            scroll_forward/scroll_backward는 label을 "화면"으로 지정합니다. 유일하게 식별된 스크롤 영역에서만 한 단계 이동하고, 마지막 expect로 목표 문구를 확인합니다.
            화면 문구를 모르면 사용자에게 물어보세요. 로그인, 결제, 공유, 제출, 문자 발송, 계정 변경은 계획에 넣지 마세요.
        """.trimIndent()
    }
}

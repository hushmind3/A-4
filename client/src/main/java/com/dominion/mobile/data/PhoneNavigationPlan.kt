package com.dominion.mobile.data

import com.google.gson.JsonObject
import com.google.gson.JsonParser

data class PhoneNavigationStep(val action: String, val label: String, val value: String? = null)
data class PhoneNavigationPlan(val app: String, val steps: List<PhoneNavigationStep>, val version: Int = 1) {
    companion object {
        val apps = mapOf("com.nhn.android.nmap" to "네이버 지도", "net.daum.android.map" to "카카오맵",
            "com.google.android.youtube" to "YouTube", "com.google.android.calendar" to "Google 캘린더",
            "com.google.android.apps.nbu.files" to "Files by Google", "com.sec.android.app.myfiles" to "삼성 내 파일")
        private val navigationLabels = setOf("검색","장소 검색","통합 검색","장소, 주소 검색","검색어 입력",
            "길찾기","도보","자동차","대중교통","Search","Directions","지도","출발지","도착지")
        private val searchLabels = setOf("검색","장소 검색","통합 검색","장소, 주소 검색","검색어 입력","Search","출발지","도착지")
        private val commonSearch = setOf("검색","Search","검색어 입력","검색어를 입력하세요","Search files","파일 검색",
            "YouTube 검색","Search YouTube","일정 검색","Search events","상품 검색","음식점 검색","숙소 검색",
            "병원 검색","출발지","도착지","목적지","출발지 검색","도착지 검색","목적지 검색","장소를 입력하세요","어디로 갈까요?","어디로 가시나요?")
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
        fun eligiblePackage(app: String): Boolean =
            app.matches(Regex("[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z0-9_]+)+")) &&
                app.length <= 100 && app !in setOf("com.android.settings", "com.android.systemui",
                    "com.android.permissioncontroller", "com.google.android.permissioncontroller",
                    "com.android.packageinstaller", "com.google.android.packageinstaller", "com.android.vending")

        private fun permittedLabel(plan: PhoneNavigationPlan, action: String, label: String): Boolean {
            if (plan.version == 1 || plan.app in apps) return label in labels(plan.app, action)
            return when (action) {
                "scroll_forward", "scroll_backward" -> label in setOf("화면", "Screen")
                "search_text" -> label in commonSearch
                "click" -> {
                    val normalized = label.lowercase(java.util.Locale.ROOT)
                    // A new app can have arbitrary navigation labels, but never use this
                    // generic adapter for obvious account/transaction/send/delete operations.
                    listOf("결제","구매","송금","삭제","발송","전송","로그인","인증","제출","가입","허용",
                        "pay","purchase","buy","transfer","delete","send","sign in","login","submit",
                        "allow","authorize","install","uninstall","confirm order").none { normalized.contains(it) } &&
                        !PhoneTransactions.anyFinalAction(label)
                }
                else -> false
            }
        }

        fun valid(plan: PhoneNavigationPlan): Boolean =
            (plan.version == 1 && plan.app in apps || plan.version == 2 && eligiblePackage(plan.app)) &&
            plan.steps.size in 1..8 &&
            (plan.steps.last().action == "expect" || plan.version == 2 && plan.steps.last().action in setOf("inspect","visual_inspect","send_message","report_result","commit_transaction")) &&
            (plan.steps.none{it.action=="send_message"} || plan.version==2&&plan.app=="com.kakao.talk"&&plan.steps.size==1) &&
            (plan.steps.none{it.action=="report_result"} || plan.version==2&&plan.app in PhonePublicResult.packages&&plan.steps.size==1) &&
            (plan.steps.none{it.action=="commit_transaction"} || plan.version==2&&PhoneTransactions.supportedApp(plan.app)&&plan.steps.size==1) &&
            (plan.steps.none{it.action=="transaction_next"} || plan.version==2&&PhoneTransactions.supportedApp(plan.app)) &&
            plan.steps.dropLast(1).none { it.action in setOf("inspect","visual_inspect") } &&
            plan.steps.count { it.action in setOf("tap_point","swipe_point") } <= 1 &&
            (plan.steps.none { it.action in setOf("tap_point","swipe_point") } ||
                plan.steps.size == 2 && plan.steps.last().action == "visual_inspect") &&
            plan.steps.all { step ->
                step.label.isNotBlank() && step.label.length <= 100 && step.label.none(Char::isISOControl) &&
                    when (step.action) {
                        "expect" -> step.value == null
                        "send_message" -> step.label=="메시지 전송"&&step.value==null
                        "report_result" -> step.value==null&&PhonePublicResult.text(step.label)==step.label
                        "commit_transaction" -> step.value==null&&PhoneTransactions.anyFinalAction(step.label)
                        "transaction_next" -> step.value==null&&PhoneTransactions.canAdvance(step.label)
                        "search_submit" -> plan.version==2&&step.value==null&&PhoneTransactions.isSearchField(step.label)
                        "inspect", "visual_inspect" -> plan.version == 2 && step.value == null && step.label in setOf("화면", "Screen")
                        "tap_point", "swipe_point" -> plan.version == 2 &&
                            VisualGesture.parse(step.action,step.value) != null
                        "click", "scroll_forward", "scroll_backward" ->
                            step.value == null && permittedLabel(plan, step.action, step.label)
                        "search_text" -> permittedLabel(plan, step.action, step.label) &&
                            step.value?.let { it.isNotBlank() && it.length <= 250 && it.none(Char::isISOControl) } == true
                        else -> false
                    }
            }
        /** Only the original narrow navigation vocabulary can run as a reviewed batch.
         * Generic app-specific controls always require individual review. */
        /** One approved search scope never authorizes transaction controls or model-invented input.
         * Only these exact navigation labels can skip repeated step prompts. */
        private val delegatedLabels=setOf("검색","Search","검색어 입력","검색어를 입력하세요","통합 검색",
            "장소 검색","장소, 주소 검색","길찾기","대중교통","도보","자동차","지도","홈","Home",
            "검색 결과","상품 검색","음식점 검색","숙소 검색","병원 검색","고객센터","도움말")
        fun permitsDelegatedRead(plan:PhoneNavigationPlan,step:PhoneNavigationStep,request:String):Boolean {
            if(!valid(plan)||plan.version!=2||step !in plan.steps)return false
            return when(step.action){
                "click"->step.label in delegatedLabels
                "search_text"->step.label in delegatedLabels && step.value?.let{value->
                    value.length in 2..100 && request.contains(value) &&
                    !value.contains('@')&&!value.contains("://")&&
                    !Regex("[0-9][0-9 .-]{5,}[0-9]").containsMatchIn(value)
                }==true
                "scroll_forward","scroll_backward","expect"->true
                else->false
            }
        }

        fun permitsReviewedBatch(plan: PhoneNavigationPlan): Boolean =
            valid(plan) && plan.version == 1 && plan.app in apps &&
                plan.steps.all { it.action in setOf("click","search_text","scroll_forward","scroll_backward","expect") }

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
            val version = when (read(row,"schema",40)) {
                "dominion-phone-plan/v1" -> 1
                "dominion-phone-plan/v2" -> 2
                else -> error("Unsupported schema")
            }
            val app = read(row,"app",100)
            require(if (version == 1) app in apps else eligiblePackage(app))
            val items = row.getAsJsonArray("steps")
            require(items.size() in 1..8)
            val steps = items.map {
                val step = it.asJsonObject
                val action = read(step,"action",20)
                val label = read(step,"label",100)
                when (action) {
                    "click", "scroll_forward", "scroll_backward", "send_message", "report_result", "commit_transaction", "transaction_next", "search_submit" -> {
                        require(step.keySet() == setOf("action","label"))
                        PhoneNavigationStep(action,label)
                    }
                    "search_text", "tap_point", "swipe_point" -> {
                        require(step.keySet() == setOf("action","label","value"))
                        PhoneNavigationStep(action,label,read(step,"value",250))
                    }
                    "expect", "inspect", "visual_inspect" -> {
                        require(step.keySet() == setOf("action","label"))
                        PhoneNavigationStep(action,label)
                    }
                    else -> error("Unsupported device operation")
                }
            }
            return PhoneNavigationPlan(app,steps,version).also { require(valid(it)) }
        }
        fun selectedAppPrompt(request: String, app: String, title: String): String {
            require(eligiblePackage(app))
            val safeTitle = title.filterNot(Char::isISOControl).take(100)
            return "사용자가 선택한 설치 앱: $safeTitle ($app)\n사용자 요청: " + request.take(2000) + "\n" + """
                dominion-phone-plan 코드 펜스 하나에 실제 기기 탐색 JSON을 반환하세요.
                {"schema":"dominion-phone-plan/v2","app":"$app","steps":[{"action":"click","label":"검색"},{"action":"search_text","label":"검색","value":"사용자 검색어"},{"action":"expect","label":"확인할 화면 문구"}]}
                앱 패키지는 변경하지 마세요. 최대 8단계, 마지막은 expect 또는 inspect입니다.
                click은 사용자가 실제 화면에서 확인할 정확한 탐색 버튼 문구, search_text는 검색 입력란만 가능합니다.
                스크롤은 scroll_forward/scroll_backward와 label "화면"입니다.
                실제 화면 문구가 불명확하면 마지막 단계 {"action":"inspect","label":"화면"}으로 관찰을 요청하세요.
                사용자가 검토하여 전달한 실제 화면 조작 요소만 다음 계획의 근거로 사용하세요.
                시각적 정보가 필요하면 마지막 단계 {"action":"visual_inspect","label":"화면"}을 사용하세요(Android 14 이상).
                실제 첨부 이미지의 frame SHA256을 받은 경우에만 tap_point (value="SHA256:x:y") 또는 swipe_point (value="SHA256:x:y:x2:y2") 한 단계와 마지막 visual_inspect를 제안할 수 있습니다.
                x/y는 해당 이미지 가로/세로의 0~1000 비율 좌표입니다. 새 화면이 달라지면 실행하지 않습니다. 좌표 조작도 직접 승인해야 합니다.
                로그인·인증·결제·발송·삭제·제출은 사용자가 직접 처리합니다.
                """.trimIndent()
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

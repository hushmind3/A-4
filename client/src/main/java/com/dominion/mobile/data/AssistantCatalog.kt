package com.dominion.mobile.data

import com.dominion.mobile.data.model.*

/** Original product presets and task templates. These do not assert live
 * accounts, tool permissions, completed work or synthetic conversations. */
object AssistantCatalog {
    fun assistantPresets(): List<Character> = listOf(
        Character(
            id = "preset_my_id",
            name = "몽글",
            tagline = "나의 도미니온",
            taglineEn = "My Dominion",
            bio = "궁금한 걸 함께 찾아보고, 매일의 작은 일을 챙겨주는 나만의 도미니온이에요.",
            color = 0xFF4797EF,
            shape = CharacterShape.BLOB,
            level = 1,
            xp = 0,
            xpGoal = 100,
            mine = false,
            voice = VoiceTone.SOFT
        ),
        Character(
            id = "preset_momo",
            name = "모모",
            tagline = "다정한 하루 도우미",
            taglineEn = "A warm everyday helper",
            bio = "일정과 마음을 함께 챙겨요. 말을 걸면 오늘 필요한 일을 먼저 찾아봐요.",
            color = 0xFFFF8FB1,
            shape = CharacterShape.BALLOON,
            level = 1,
            xp = 0,
            xpGoal = 100,
            online = false,
            voice = VoiceTone.BRIGHT,
            relation = "자주 대화하는 도미니온"
        ),
        Character(
            id = "preset_luna",
            name = "루나",
            tagline = "차분한 여행 친구",
            taglineEn = "A calm travel companion",
            bio = "여행과 휴식을 좋아해요. 선택지를 차분히 비교해서 알려줘요.",
            color = 0xFF8B7CF6,
            shape = CharacterShape.DONUT,
            level = 1,
            xp = 0,
            xpGoal = 100,
            voice = VoiceTone.CALM,
            relation = "여행 친구"
        ),
        Character(
            id = "preset_teo",
            name = "테오",
            tagline = "일 잘하는 비서",
            taglineEn = "A focused work partner",
            bio = "리포트와 일정을 정리해요. 일이 끝나면 조용히 결과만 알려줘요.",
            color = 0xFF3FB6A8,
            shape = CharacterShape.GEM,
            level = 1,
            xp = 0,
            xpGoal = 100,
            voice = VoiceTone.CALM,
            relation = "업무 파트너"
        ),
        Character(
            id = "preset_haru",
            name = "하루",
            tagline = "집과 건강을 챙겨요",
            taglineEn = "Keeps home and health on track",
            bio = "장보기, 물 마시기, 저녁 메뉴처럼 작은 생활을 기억해요.",
            color = 0xFFF5A524,
            shape = CharacterShape.CLOUD,
            level = 1,
            xp = 0,
            xpGoal = 100,
            online = false,
            voice = VoiceTone.SOFT,
            relation = "생활 친구"
        )
    )

    fun connections(): List<Connection> = listOf(
        Connection("con_gcal", "Google 캘린더", "google_calendar", "일정", true, listOf("일정 읽기", "일정 추가"), "오늘", color = 0xFF4285F4),
        Connection("con_gmail", "Gmail", "gmail", "메일", true, listOf("메일 읽기", "초안 작성"), "어제", color = 0xFFEA4335),
        Connection("con_gdrive", "Google 드라이브", "google_drive", "문서", false, listOf("파일 읽기"), color = 0xFF34A853),
        Connection("con_notion", "Notion", "notion", "문서", true, listOf("페이지 읽기", "페이지 추가"), "3일 전", color = 0xFF222222),
        Connection("con_slack", "Slack", "slack", "메시지", false, listOf("채널 읽기", "메시지 보내기"), color = 0xFF611F69),
        Connection("con_github", "GitHub", "github", "개발", false, listOf("저장소 읽기"), color = 0xFF24292F),
        Connection("con_x", "X", "x", "소셜", false, listOf("게시물 읽기"), color = 0xFF111111),
        Connection("con_music", "Apple Music", "apple_music", "음악", false, listOf("재생 목록 읽기"), color = 0xFFFA243C),
        Connection("con_ms", "Microsoft", "microsoft", "문서", false, listOf("파일 읽기"), color = 0xFF00A4EF),
        Connection("con_mercury", "Mercury", "mercury", "금융", false, listOf("계좌 읽기"), color = 0xFF5B5BD6),
        Connection("con_location", "위치", "location", "기기", true, listOf("현재 위치"), "방금", color = 0xFF4797EF),
        Connection("con_link", "링크", "link", "기기", true, listOf("링크 열기"), color = 0xFF666666),
        Connection("builtin_browser", "브라우저", "browser", "기본 도구", true, builtIn = true, color = 0xFF4797EF),
        Connection("builtin_maps", "지도", "maps", "기본 도구", true, builtIn = true, color = 0xFF34A853),
        Connection("builtin_weather", "날씨", "weather", "기본 도구", true, builtIn = true, color = 0xFFF5A524),
        Connection("builtin_docs", "문서", "documents", "기본 도구", true, builtIn = true, color = 0xFF8B7CF6),
        Connection("builtin_call", "전화", "phone_call", "기본 도구", true, builtIn = true, color = 0xFF3FB6A8)
    ).map { it.copy(connected = false, lastUsed = null) }

    fun explore(): List<ExploreTemplate> = listOf(
        ExploreTemplate("ex_brief", "아침 브리핑", "Morning briefing", "오늘 일정과 날씨를 한 번에", "Today's schedule and weather", ExploreCategory.RECOMMENDED, 0xFF4797EF,
            listOf("캘린더 확인", "날씨 확인", "중요한 일 3개 정리"), "내일 아침 브리핑을 준비해줘"),
        ExploreTemplate("ex_email", "이메일 정리", "Inbox cleanup", "답장할 메일만 골라줘요", "Pick the emails that need a reply", ExploreCategory.WORK, 0xFF3FB6A8,
            listOf("받은편지함 읽기", "답장 필요 표시", "초안 만들기"), "답장해야 할 메일을 정리해줘"),
        ExploreTemplate("ex_refund", "환불 챙기기", "Track a refund", "주문 내역과 환불 상태 확인", "Check orders and refund status", ExploreCategory.MONEY, 0xFFFFB86B,
            listOf("주문 내역 확인", "환불 규정 확인", "문의 문구 작성"), "환불이 안 된 주문을 찾아줘"),
        ExploreTemplate("ex_subs", "구독 점검", "Subscription check", "쓰지 않는 구독을 찾아요", "Find subscriptions you no longer use", ExploreCategory.MONEY, 0xFF8B7CF6,
            listOf("결제 내역 확인", "사용 빈도 정리", "해지 후보 고르기"), "이번 달 구독 결제를 정리해줘"),
        ExploreTemplate("ex_chores", "집안일 루틴", "Home routine", "이번 주 집안일을 나눠요", "Split this week's chores", ExploreCategory.HOME, 0xFF79C7A5,
            listOf("할 일 목록", "요일별 배치", "알림 설정"), "이번 주 집안일 루틴을 만들어줘"),
        ExploreTemplate("ex_fridge", "냉장고 정리", "Fridge clean-out", "남은 재료로 메뉴 추천", "Meal ideas from what's left", ExploreCategory.HOME, 0xFFF5A524,
            listOf("재료 확인", "레시피 추천", "장보기 목록 추가"), "냉장고 재료로 저녁 메뉴를 골라줘"),
        ExploreTemplate("ex_clinic", "병원 예약", "Book a clinic", "가까운 병원과 시간 찾기", "Find a nearby clinic and time", ExploreCategory.HEALTH, 0xFFFF8FB1,
            listOf("증상 정리", "병원 검색", "예약 시간 확인"), "이번 주에 피부과 예약을 잡아줘"),
        ExploreTemplate("ex_workout", "운동 계획", "Workout plan", "내 일정에 맞는 3일 루틴", "A 3-day routine that fits my week", ExploreCategory.HEALTH, 0xFF3FB6A8,
            listOf("가능한 시간 확인", "운동 종류 고르기", "캘린더에 추가"), "이번 주 운동 계획을 짜줘"),
        ExploreTemplate("ex_cart", "장바구니 비교", "Compare carts", "같은 상품의 가격을 비교", "Compare prices for the same item", ExploreCategory.SHOPPING, 0xFF4797EF,
            listOf("상품 검색", "가격 비교", "최저가 링크 저장"), "생수 최저가를 비교해줘"),
        ExploreTemplate("ex_flight", "항공권 추적", "Watch a flight", "가격이 내려가면 알려줘", "Tell me when the price drops", ExploreCategory.TRAVEL, 0xFF8B7CF6,
            listOf("노선 설정", "가격 확인", "알림 만들기"), "제주 항공권 가격을 추적해줘"),
        ExploreTemplate("ex_weekend", "주말 나들이", "Weekend outing", "날씨 좋은 근교 코스", "A nearby trip for good weather", ExploreCategory.FUN, 0xFFF5A524,
            listOf("날씨 확인", "이동 시간 계산", "장소 3곳 추천"), "이번 주말 근교 나들이를 추천해줘"),
        ExploreTemplate("ex_meet", "친구 약속 잡기", "Make a plan", "모두 가능한 시간 찾기", "Find a time that works for everyone", ExploreCategory.PEOPLE, 0xFFFF8FB1,
            listOf("친구 목록", "가능한 시간 묻기", "일정 만들기"), "친구들과 저녁 약속을 잡아줘")
    ) + KoreanServices.entries.map { it.template() }
}

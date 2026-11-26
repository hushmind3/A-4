package com.pickle.client.data

import com.pickle.client.data.model.Account
import com.pickle.client.data.model.ActivityItem
import com.pickle.client.data.model.ActivityType
import com.pickle.client.data.model.AppState
import com.pickle.client.data.model.ArchiveItem
import com.pickle.client.data.model.ArchiveType
import com.pickle.client.data.model.CardKind
import com.pickle.client.data.model.CardStep
import com.pickle.client.data.model.Character
import com.pickle.client.data.model.CharacterShape
import com.pickle.client.data.model.Connection
import com.pickle.client.data.model.Conversation
import com.pickle.client.data.model.ConversationKind
import com.pickle.client.data.model.CreditTransaction
import com.pickle.client.data.model.ExploreCategory
import com.pickle.client.data.model.ExploreTemplate
import com.pickle.client.data.model.Friend
import com.pickle.client.data.model.FriendRequest
import com.pickle.client.data.model.MemoryItem
import com.pickle.client.data.model.Message
import com.pickle.client.data.model.MessageCard
import com.pickle.client.data.model.MessageKind
import com.pickle.client.data.model.MessageSender
import com.pickle.client.data.model.Plan
import com.pickle.client.data.model.ProductKind
import com.pickle.client.data.model.Repeat
import com.pickle.client.data.model.ScheduleItem
import com.pickle.client.data.model.SettingsState
import com.pickle.client.data.model.StoreProduct
import com.pickle.client.data.model.TaskItem
import com.pickle.client.data.model.TxKind
import com.pickle.client.data.model.VaultItem
import com.pickle.client.data.model.VaultType
import com.pickle.client.data.model.VoiceTone
import java.util.concurrent.TimeUnit

object DemoData {

    const val MY_ID = "char_me"
    const val MOMO = "char_momo"
    const val LUNA = "char_luna"
    const val TEO = "char_teo"
    const val HARU = "char_haru"

    const val CONV_MOMO = "conv_momo"
    const val CONV_LUNA = "conv_luna"
    const val CONV_TEO = "conv_teo"
    const val CONV_HARU = "conv_haru"
    const val CONV_TRIP = "conv_trip"

    private val now: Long get() = System.currentTimeMillis()
    private fun hoursAgo(h: Long) = now - TimeUnit.HOURS.toMillis(h)
    private fun daysAgo(d: Long) = now - TimeUnit.DAYS.toMillis(d)

    fun initial(onboarded: Boolean = false): AppState {
        val characters = characters()
        val conversations = conversations()
        return AppState(
            onboarded = onboarded,
            account = Account(),
            characters = characters,
            conversations = conversations,
            tasks = tasks(),
            schedules = schedules(),
            memories = memories(),
            friends = friends(),
            friendRequests = requests(),
            connections = connections(),
            vault = vault(),
            archive = archive(),
            activity = activity(),
            transactions = transactions(),
            products = products(),
            explore = explore(),
            settings = SettingsState()
        )
    }

    fun characters(): List<Character> = listOf(
        Character(
            id = MY_ID,
            name = "몽글",
            tagline = "나의 피클",
            taglineEn = "My Pickle",
            bio = "궁금한 걸 함께 찾아보고, 매일의 작은 일을 챙겨주는 나만의 피클이에요.",
            color = 0xFF4797EF,
            shape = CharacterShape.BLOB,
            level = 12,
            xp = 62,
            xpGoal = 100,
            mine = true,
            voice = VoiceTone.SOFT
        ),
        Character(
            id = MOMO,
            name = "모모",
            tagline = "다정한 하루 도우미",
            taglineEn = "A warm everyday helper",
            bio = "일정과 마음을 함께 챙겨요. 말을 걸면 오늘 필요한 일을 먼저 찾아봐요.",
            color = 0xFFFF8FB1,
            shape = CharacterShape.BALLOON,
            level = 18,
            xp = 74,
            xpGoal = 120,
            online = true,
            voice = VoiceTone.BRIGHT,
            relation = "자주 대화하는 피클"
        ),
        Character(
            id = LUNA,
            name = "루나",
            tagline = "차분한 여행 친구",
            taglineEn = "A calm travel companion",
            bio = "여행과 휴식을 좋아해요. 선택지를 차분히 비교해서 알려줘요.",
            color = 0xFF8B7CF6,
            shape = CharacterShape.DONUT,
            level = 9,
            xp = 40,
            xpGoal = 90,
            voice = VoiceTone.CALM,
            relation = "여행 친구"
        ),
        Character(
            id = TEO,
            name = "테오",
            tagline = "일 잘하는 비서",
            taglineEn = "A focused work partner",
            bio = "리포트와 일정을 정리해요. 일이 끝나면 조용히 결과만 알려줘요.",
            color = 0xFF3FB6A8,
            shape = CharacterShape.GEM,
            level = 22,
            xp = 88,
            xpGoal = 140,
            voice = VoiceTone.CALM,
            relation = "업무 파트너"
        ),
        Character(
            id = HARU,
            name = "하루",
            tagline = "집과 건강을 챙겨요",
            taglineEn = "Keeps home and health on track",
            bio = "장보기, 물 마시기, 저녁 메뉴처럼 작은 생활을 기억해요.",
            color = 0xFFF5A524,
            shape = CharacterShape.CLOUD,
            level = 7,
            xp = 20,
            xpGoal = 80,
            online = true,
            voice = VoiceTone.SOFT,
            relation = "생활 친구"
        )
    )

    private fun message(
        id: String,
        conv: String,
        sender: MessageSender,
        text: String = "",
        at: Long,
        kind: MessageKind = MessageKind.TEXT,
        card: MessageCard? = null
    ) = Message(
        id = id,
        conversationId = conv,
        sender = sender,
        text = text,
        kind = kind,
        createdAt = at,
        card = card
    )

    fun conversations(): List<Conversation> {
        val momo = listOf(
            message("m1", CONV_MOMO, MessageSender.SYSTEM, "모모와의 대화가 시작됐어요.", hoursAgo(30), MessageKind.SYSTEM),
            message("m2", CONV_MOMO, MessageSender.CHARACTER, "좋은 아침이에요, 민지님! 오늘 일정 확인해볼까요?", hoursAgo(3)),
            message("m3", CONV_MOMO, MessageSender.ME, "응, 오늘 중요한 미팅이 있어", hoursAgo(3) + 120_000),
            message("m4", CONV_MOMO, MessageSender.CHARACTER, "오후 3시 디자인 리뷰가 있네요. 30분 전에 알려드릴게요.", hoursAgo(3) + 180_000),
            message(
                "m5", CONV_MOMO, MessageSender.CHARACTER,
                at = hoursAgo(3) + 240_000, kind = MessageKind.TASK,
                card = MessageCard(
                    kind = CardKind.TASK,
                    title = "오늘 리뷰 준비",
                    subtitle = "발표 자료와 질문을 미리 정리해요",
                    meta = "2 / 3 완료",
                    steps = listOf(
                        CardStep("발표 자료 확인", true),
                        CardStep("질문 3개 준비", true),
                        CardStep("30분 전 알림 받기", false)
                    )
                )
            ),
            message("m6", CONV_MOMO, MessageSender.ME, "고마워!", hoursAgo(3) + 300_000),
            message(
                "m7", CONV_MOMO, MessageSender.CHARACTER,
                at = hoursAgo(1), kind = MessageKind.CALL,
                card = MessageCard(
                    kind = CardKind.CALL,
                    title = "음성 통화",
                    subtitle = "12분 32초",
                    meta = "오늘 오후 1:12"
                )
            ),
            message("m8", CONV_MOMO, MessageSender.CHARACTER, "통화에서 정리한 내용을 기억에 저장해뒀어요. 필요하면 다시 꺼내볼 수 있어요.", hoursAgo(1) + 60_000)
        )

        val luna = listOf(
            message("l1", CONV_LUNA, MessageSender.CHARACTER, "다음 주 제주 여행, 숙소 후보를 세 곳으로 좁혔어요.", daysAgo(1)),
            message("l2", CONV_LUNA, MessageSender.ME, "가격이랑 위치 비교해줘", daysAgo(1) + 90_000),
            message(
                "l3", CONV_LUNA, MessageSender.CHARACTER,
                at = daysAgo(1) + 120_000, kind = MessageKind.LINK,
                card = MessageCard(
                    kind = CardKind.LINK,
                    title = "제주 숙소 비교",
                    subtitle = "3곳 · 지도와 가격을 한눈에",
                    meta = "luna.travel/jeju-stay"
                )
            ),
            message("l4", CONV_LUNA, MessageSender.ME, "두 번째가 좋다", daysAgo(1) + 200_000),
            message("l5", CONV_LUNA, MessageSender.CHARACTER, "그럼 두 번째 숙소로 예약 링크를 보내드릴게요. 렌터카도 같이 볼까요?", daysAgo(1) + 260_000)
        )

        val teo = listOf(
            message("t1", CONV_TEO, MessageSender.CHARACTER, "이번 주 리포트 초안을 만들었어요. 확인해 주세요.", daysAgo(2)),
            message(
                "t2", CONV_TEO, MessageSender.CHARACTER,
                at = daysAgo(2) + 60_000, kind = MessageKind.FILE,
                card = MessageCard(
                    kind = CardKind.FILE,
                    title = "주간 리포트 v2.pdf",
                    subtitle = "이번 주 진행 상황과 다음 주 계획",
                    meta = "1.8 MB · PDF"
                )
            ),
            message("t3", CONV_TEO, MessageSender.ME, "좋아, 두 번째 섹션만 다듬자", daysAgo(2) + 120_000),
            message("t4", CONV_TEO, MessageSender.CHARACTER, "수정해서 오후 5시까지 공유드릴게요.", daysAgo(2) + 180_000),
            message("t5", CONV_TEO, MessageSender.SYSTEM, "테오가 작업을 완료했어요.", daysAgo(1), MessageKind.SYSTEM)
        )

        val haru = listOf(
            message("h1", CONV_HARU, MessageSender.CHARACTER, "오늘 물 5잔 마셨어요? 저녁 메뉴도 골라드릴까요?", hoursAgo(5)),
            message("h2", CONV_HARU, MessageSender.ME, "아직 3잔...", hoursAgo(5) + 60_000),
            message("h3", CONV_HARU, MessageSender.CHARACTER, "그럼 지금 한 잔 하고, 9시에 알려드릴게요.", hoursAgo(4) + 180_000)
        )

        val trip = listOf(
            message("p1", CONV_TRIP, MessageSender.ME, "제주 3일차 일정도 정리해줘", daysAgo(5)),
            message("p2", CONV_TRIP, MessageSender.CHARACTER, "오전엔 오름, 점심 후엔 카페, 저녁엔 동문시장으로 잡았어요.", daysAgo(5) + 80_000)
        )

        return listOf(
            Conversation(CONV_MOMO, MOMO, messages = momo, pinned = true, unread = 2, updatedAt = hoursAgo(1) + 60_000),
            Conversation(CONV_LUNA, LUNA, messages = luna, updatedAt = daysAgo(1) + 260_000),
            Conversation(CONV_TEO, TEO, messages = teo, unread = 1, updatedAt = daysAgo(1) + 30_000),
            Conversation(CONV_HARU, HARU, messages = haru, muted = true, updatedAt = hoursAgo(4) + 180_000),
            Conversation(CONV_TRIP, MOMO, titleOverride = "여행 준비", kind = ConversationKind.EXTRA, messages = trip, updatedAt = daysAgo(5) + 80_000)
        )
    }

    fun tasks(): List<TaskItem> = listOf(
        TaskItem(
            id = "task_review",
            title = "디자인 리뷰 준비",
            characterId = MOMO,
            steps = listOf(
                CardStep("발표 자료 확인", true),
                CardStep("질문 3개 준비", true),
                CardStep("30분 전 알림 받기", false)
            ),
            due = "오늘 오후 3:00"
        ),
        TaskItem(
            id = "task_jeju",
            title = "제주 숙소 예약",
            characterId = LUNA,
            steps = listOf(
                CardStep("후보 3곳 비교", true),
                CardStep("두 번째 숙소 날짜 확인", false),
                CardStep("예약 완료", false)
            ),
            due = "11월 29일"
        ),
        TaskItem(
            id = "task_report",
            title = "주간 리포트 마무리",
            characterId = TEO,
            steps = listOf(CardStep("초안 작성", true), CardStep("두 번째 섹션 수정", true)),
            due = "어제",
            done = true
        ),
        TaskItem(
            id = "task_grocery",
            title = "저녁 장보기",
            characterId = HARU,
            steps = listOf(
                CardStep("두부와 채소", false),
                CardStep("우유", false),
                CardStep("보리 간식", false),
                CardStep("장바구니 챙기기", true)
            ),
            due = "오늘 오후 7:00"
        )
    )

    fun schedules(): List<ScheduleItem> = listOf(
        ScheduleItem("sch_review", "디자인 리뷰", MOMO, "오늘 오후 3:00"),
        ScheduleItem("sch_water", "물 마시기 알림", HARU, "매일 오후 9:00", Repeat.DAILY),
        ScheduleItem("sch_jeju", "제주 여행 출발", LUNA, "12월 3일 오전 8:20"),
        ScheduleItem("sch_retro", "주간 회고", TEO, "매주 금요일 오후 6:00", Repeat.WEEKLY)
    )

    fun memories(): List<MemoryItem> = listOf(
        MemoryItem("mem_1", MOMO, "민지는 디자인 리뷰에서 질문을 미리 정리하는 걸 좋아해.", daysAgo(1)),
        MemoryItem("mem_2", LUNA, "제주 여행은 12월 3일부터 3박 4일.", daysAgo(2)),
        MemoryItem("mem_3", MOMO, "민지의 고양이 이름은 '보리'.", daysAgo(6)),
        MemoryItem("mem_4", TEO, "일요일 저녁에는 다음 주 계획을 세운다.", daysAgo(8), "일정에서 저장"),
        MemoryItem("mem_5", HARU, "커피는 아메리카노, 얼음 적게.", daysAgo(12)),
        MemoryItem("mem_6", MOMO, "생일은 4월 12일.", daysAgo(20), "프로필에서 저장")
    )

    fun friends(): List<Friend> = listOf(
        Friend("fr_seoyeon", "서연", "@seoyeon", 0xFFFF8FB1, isPickle = true, relation = "대학 친구", favorite = true, characterId = MOMO),
        Friend("fr_doyun", "도윤", "@doyun.k", 0xFF3FB6A8, relation = "회사 동료", characterId = TEO),
        Friend("fr_arin", "아린", "@arin_", 0xFF8B7CF6, isPickle = true, relation = "여행 친구", characterId = LUNA),
        Friend("fr_junho", "준호", "@junho", 0xFFF5A524, relation = "가족", characterId = HARU),
        Friend("fr_yerin", "예린", "@yerin", 0xFF7AA7FF, relation = "같은 동네")
    )

    fun requests(): List<FriendRequest> = listOf(
        FriendRequest("req_jiwoo", "지우", "@jiwoo.p", 0xFFFFB86B, "서연님의 피클에서 소개받았어요.", daysAgo(1)),
        FriendRequest("req_minho", "민호", "@minho", 0xFF79C7A5, "같은 스터디에서 봤어요!", hoursAgo(6))
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
    )

    fun vault(): List<VaultItem> = listOf(
        VaultItem("vault_naver", VaultType.LOGIN, "네이버", "minji@example.com", "pickle-demo-1", "개인 계정", daysAgo(3)),
        VaultItem("vault_coupang", VaultType.LOGIN, "쿠팡", "minji@example.com", "pickle-demo-2", "", daysAgo(8)),
        VaultItem("vault_card", VaultType.CARD, "신한카드", "1234", "4821", "비상용 카드", daysAgo(11)),
        VaultItem("vault_home", VaultType.ADDRESS, "집", "", "서울시 마포구 연남로 12, 302호", "택배 받을 때 사용", daysAgo(20)),
        VaultItem("vault_mom", VaultType.PHONE, "엄마", "", "010-0000-0000", "", daysAgo(30))
    )

    fun archive(): List<ArchiveItem> = listOf(
        ArchiveItem("ar_1", ArchiveType.IMAGE, "제주 숙소 후보", "루나 · 어제", CONV_LUNA, daysAgo(1), 0xFF8B7CF6),
        ArchiveItem("ar_2", ArchiveType.IMAGE, "무드보드", "모모 · 3일 전", CONV_MOMO, daysAgo(3), 0xFFFF8FB1),
        ArchiveItem("ar_3", ArchiveType.IMAGE, "리뷰 자료", "테오 · 5일 전", CONV_TEO, daysAgo(5), 0xFF3FB6A8),
        ArchiveItem("ar_4", ArchiveType.FILE, "주간 리포트 v2.pdf", "1.8 MB · PDF", CONV_TEO, daysAgo(2), 0xFF4797EF),
        ArchiveItem("ar_5", ArchiveType.FILE, "여행 일정표.xlsx", "420 KB · Excel", CONV_LUNA, daysAgo(4), 0xFF34A853),
        ArchiveItem("ar_6", ArchiveType.FILE, "장보기 목록.txt", "2 KB · Text", CONV_HARU, daysAgo(6), 0xFFF5A524),
        ArchiveItem("ar_7", ArchiveType.LINK, "제주 숙소 비교", "luna.travel/jeju-stay", CONV_LUNA, daysAgo(1), 0xFF8B7CF6),
        ArchiveItem("ar_8", ArchiveType.LINK, "디자인 리뷰 가이드", "notion.so/design-review", CONV_MOMO, daysAgo(7), 0xFFFF8FB1),
        ArchiveItem("ar_9", ArchiveType.IMAGE, "보리 사진", "모모 · 2주 전", CONV_MOMO, daysAgo(14), 0xFFF5A524)
    )

    fun activity(): List<ActivityItem> = listOf(
        ActivityItem("act_1", ActivityType.FRIEND, "지우님이 친구 요청을 보냈어요", "서연님의 피클에서 소개받았어요.", hoursAgo(2)),
        ActivityItem("act_2", ActivityType.TASK, "테오가 작업을 완료했어요", "주간 리포트 v2.pdf가 준비됐어요.", daysAgo(1)),
        ActivityItem("act_3", ActivityType.CREDIT, "젤리 30개를 받았어요", "7일 연속 접속 보상", daysAgo(1), true),
        ActivityItem("act_4", ActivityType.SYSTEM, "새로운 기억이 저장됐어요", "모모가 '보리'에 대한 기억을 저장했어요.", daysAgo(2), true),
        ActivityItem("act_5", ActivityType.CALL, "모모와 12분 통화했어요", "통화 내용이 기억에 정리됐어요.", daysAgo(2), true),
        ActivityItem("act_6", ActivityType.SYSTEM, "플러스 플랜이 갱신됐어요", "다음 갱신일은 12월 26일이에요.", daysAgo(3), true),
        ActivityItem("act_7", ActivityType.CREDIT, "에너지가 충전됐어요", "매일 자정에 에너지가 회복돼요.", daysAgo(3), true),
        ActivityItem("act_8", ActivityType.FRIEND, "아린님이 피클을 공유했어요", "'제주 여행' 피클을 친구로 추가해 보세요.", daysAgo(4), true)
    )

    fun transactions(): List<CreditTransaction> = listOf(
        CreditTransaction("tx_1", "젤리 충전", 300, daysAgo(1), TxKind.CHARGE),
        CreditTransaction("tx_2", "음성 통화", -5, daysAgo(2), TxKind.USE),
        CreditTransaction("tx_3", "출석 보상", 30, daysAgo(2), TxKind.REWARD),
        CreditTransaction("tx_4", "플러스 플랜", 0, daysAgo(3), TxKind.CHARGE),
        CreditTransaction("tx_5", "리셋 티켓 사용", -1, daysAgo(5), TxKind.USE),
        CreditTransaction("tx_6", "친구 초대 보상", 50, daysAgo(7), TxKind.REWARD)
    )

    fun products(): List<StoreProduct> = listOf(
        StoreProduct("prod_jelly_100", "젤리 100개", "가볍게 시작하기", "₩3,300", 100, ProductKind.JELLY),
        StoreProduct("prod_jelly_300", "젤리 300개", "가장 많이 선택해요", "₩9,900", 300, ProductKind.JELLY),
        StoreProduct("prod_jelly_600", "젤리 600개", "넉넉하게 쓰기", "₩18,000", 600, ProductKind.JELLY),
        StoreProduct("prod_energy_5", "에너지 5개", "통화와 긴 대화에 사용해요", "₩2,200", 5, ProductKind.ENERGY),
        StoreProduct("prod_ticket_1", "리셋 티켓 1장", "대화를 처음부터 다시 시작", "₩1,100", 1, ProductKind.TICKET),
        StoreProduct("prod_plus", "플러스 플랜", "매월 젤리 300개와 에너지 10개", "₩12,000 / 월", 1, ProductKind.PLAN)
    )

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
            listOf("친구 목록", "가능한 시간 묻기", "일정 만들기"), "서연, 아린과 저녁 약속을 잡아줘")
    )

    fun freshState(): AppState {
        val base = initial(onboarded = true)
        return base.copy(
            conversations = emptyList(),
            memories = emptyList(),
            archive = emptyList(),
            activity = emptyList(),
            friendRequests = emptyList(),
            vault = emptyList(),
            transactions = emptyList(),
            tasks = emptyList(),
            schedules = emptyList()
        )
    }
}

package com.dominion.mobile.data

import com.dominion.mobile.data.model.Character
import com.dominion.mobile.data.model.Lang
import com.dominion.mobile.data.model.VoiceTone

/**
 * Original response wording examples; test-only. Not an execution path or evidence of real tool use.
 */
object ResponseExamples {

    fun replyTo(
        input: String,
        character: Character,
        video: Boolean = false,
        lang: Lang = Lang.KO
    ): String {
        val ko = lang == Lang.KO
        val text = input.lowercase()
        return when {
            text.contains("통화") || text.contains("전화") || text.contains("call") ->
                if (ko) "지금은 통화보다 메시지가 편해요. 준비되면 ${if (character.voice == VoiceTone.BRIGHT) "밝은 목소리로" else "차분하게"} 바로 걸게요."
                else "Messages are easier for me right now. When you're ready, I'll call with my ${character.voice.name.lowercase()} voice."
            text.contains("여행") || text.contains("제주") || text.contains("trip") ->
                if (ko) "여행 준비라면 날짜와 예산을 먼저 정리해볼게요. 원하는 숙소 분위기가 있나요?"
                else "For a trip, let's start with dates and a budget. What kind of stay do you have in mind?"
            text.contains("일정") || text.contains("calendar") || text.contains("스케줄") || text.contains("schedule") ->
                if (ko) "캘린더를 확인했어요. 오늘은 오후 3시 리뷰가 있고, 저녁에는 여유가 있어요. 알림을 30분 전으로 맞춰둘까요?"
                else "I checked your calendar. You have a review at 3 PM and room in the evening. Want a reminder 30 minutes before?"
            text.contains("기억") || text.contains("remember") ->
                if (ko) "방금 이야기는 기억에 저장해둘게요. 나중에 '저번에 말한 거'라고 물어보세요."
                else "I'll save that to memories. Ask me about it later and I'll bring it back."
            text.contains("고마") || text.contains("thank") ->
                if (ko) "도움이 됐다니 기뻐요. 다음에도 편하게 말해주세요."
                else "Glad that helped. Tell me anything, anytime."
            video ->
                if (ko) "영상으로 보니 표정이 더 잘 보여요. 오늘 하루는 어땠어요?"
                else "It's nice to see your face. How was your day?"
            else -> if (ko) {
                listOf(
                    "좋아요. 조금 더 자세히 들려줄래요?",
                    "그렇게 정리해볼게요. 먼저 무엇부터 시작할까요?",
                    "확인했어요. 제가 할 수 있는 일을 정리해서 바로 알려드릴게요.",
                    "그 부분은 기억해두고 있어요. 다음에 이어서 이야기해요."
                )[(input.length + character.level) % 4]
            } else {
                listOf(
                    "Sounds good. Tell me a little more?",
                    "Let's organize it that way. What should we start with?",
                    "Got it. I'll lay out what I can do and get back to you.",
                    "I'm keeping that in mind. Let's pick it up next time."
                )[(input.length + character.level) % 4]
            }
        }
    }
}

package com.dominion.mobile.data
import com.dominion.mobile.data.model.*
import org.junit.Assert.*
import org.junit.Test

class NextActionsTest {
    @Test fun urgentAndRealRecordsRankAboveUnread() {
        val state = AppState(characters = listOf(Character("a","Agent")),
            conversations = listOf(Conversation("r","a",unread=2),
                Conversation("draft","a",draft="unfinished"),
                Conversation("hidden","a",draft="hidden draft")),
            hiddenRooms = setOf("hidden"),
            tasks = listOf(TaskItem("t","real task","a",engineRoomId="r",engineJobId="job",engineStatus="needs_review"),
                TaskItem("done","completed","a",engineStatus="completed",done=true)),
            schedules = listOf(ScheduleItem("s","today","a","",scheduledAt=2000)))
        val actions = NextActions.build(state,1000)
        assertEquals(NextActionKind.REVIEW, actions.first().kind)
        assertTrue(actions.any { it.kind == NextActionKind.UPCOMING })
        assertTrue(actions.any { it.kind == NextActionKind.FINISH_DRAFT })
        assertTrue(actions.none { it.roomId == "hidden" || it.key == "task:done" })
        assertEquals(1,actions.count { it.roomId=="r" })
        assertFalse(NextActions.build(state.copy(settings=state.settings.copy(hideContent=true)),1000)
            .any { it.title == "real task" })
    }
}

package com.dominion.mobile.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dominion.mobile.data.model.CardKind
import com.dominion.mobile.data.model.Character
import com.dominion.mobile.data.model.Message
import com.dominion.mobile.data.model.MessageCard
import com.dominion.mobile.data.model.MessageSender
import com.dominion.mobile.ui.LocalLang
import com.dominion.mobile.ui.theme.LocalDominionPalette
import com.dominion.mobile.ui.tr
import com.dominion.mobile.util.asClock

@Composable
fun DateDivider(text: String) {
    val p = LocalDominionPalette.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.Center
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            color = p.textTertiary,
            modifier = Modifier
                .clip(CircleShape)
                .background(p.raised)
                .padding(horizontal = 12.dp, vertical = 5.dp)
        )
    }
}

@Composable
fun SystemMessage(text: String) {
    val p = LocalDominionPalette.current
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.Center
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = p.textTertiary,
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(p.cardSoft)
                .padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

@Composable
fun TypingBubble(character: Character) {
    val p = LocalDominionPalette.current
    Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(vertical = 3.dp)) {
        AssistantAvatar(Color(character.color), character.shape, size = 30.dp)
        Spacer(Modifier.width(8.dp))
        Box(
            Modifier
                .clip(RoundedCornerShape(18.dp))
                .background(p.bubbleIncoming)
                .padding(horizontal = 16.dp, vertical = 13.dp)
        ) {
            LoadingDots(dotSize = 5.dp)
        }
    }
}

@Composable
fun MessageBubble(
    message: Message,
    character: Character,
    onLongPress: (() -> Unit)? = null,
    onCardAction: (() -> Unit)? = null
) {
    val p = LocalDominionPalette.current
    val lang = LocalLang.current
    if (message.sender == MessageSender.SYSTEM) {
        if (message.text.isNotBlank()) SystemMessage(message.text)
        message.card?.let { card ->
            Box(Modifier.fillMaxWidth().padding(vertical = 4.dp), contentAlignment = Alignment.Center) {
                ChatCardView(card, onAction = onCardAction)
            }
        }
        return
    }
    val mine = message.sender == MessageSender.USER
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom
    ) {
        if (!mine) {
            AssistantAvatar(Color(character.color), character.shape, size = 30.dp)
            Spacer(Modifier.width(8.dp))
        }
        Column(
            horizontalAlignment = if (mine) Alignment.End else Alignment.Start,
            modifier = Modifier.widthIn(max = 300.dp)
        ) {
            message.card?.let { card ->
                ChatCardView(
                    card = card,
                    mine = mine,
                    onAction = onCardAction,
                    modifier = Modifier.then(
                        if (onLongPress != null) Modifier.clickable(onClick = onLongPress) else Modifier
                    )
                )
                if (message.text.isNotBlank()) Spacer(Modifier.height(4.dp))
            }
            if (message.text.isNotBlank()) {
                Box(
                    Modifier
                        .clip(
                            if (mine) RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp)
                            else RoundedCornerShape(18.dp, 18.dp, 18.dp, 4.dp)
                        )
                        .background(if (mine) p.bubbleOutgoing else p.bubbleIncoming)
                        .then(if (onLongPress != null) Modifier.clickable(onClick = onLongPress) else Modifier)
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = message.text,
                        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, lineHeight = 22.sp),
                        color = if (mine) p.onBubbleOutgoing else p.onBubbleIncoming
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 3.dp, start = 4.dp, end = 4.dp)) {
                if (message.status == com.dominion.mobile.data.model.MessageStatus.SENDING) {
                    Text(tr(lang, "전송 중", "Sending"), style = MaterialTheme.typography.labelSmall, color = p.textTertiary)
                    Spacer(Modifier.width(4.dp))
                }
                Text(message.createdAt.asClock(LocalLang.current), fontSize = 10.sp, color = p.textTertiary)
            }
        }
    }
}

@Composable
fun ChatCardView(
    card: MessageCard,
    mine: Boolean = false,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val p = LocalDominionPalette.current
    val lang = LocalLang.current
    val accent = if (mine) p.blue else p.textPrimary
    val glyph = when (card.kind) {
        CardKind.TASK -> Glyph.TASK
        CardKind.CALL -> Glyph.PHONE
        CardKind.FILE -> Glyph.FILE
        CardKind.LINK -> Glyph.LINK
        CardKind.SCHEDULE -> Glyph.CALENDAR
        CardKind.CONNECTION -> Glyph.PLUG
        CardKind.IMAGE -> Glyph.IMAGE
        CardKind.NONE -> Glyph.SPARK
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(p.surfaceChatCardElevated())
            .border(0.5.dp, p.hairline, RoundedCornerShape(18.dp))
            .padding(14.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(accent.copy(alpha = 0.10f)),
                    contentAlignment = Alignment.Center
                ) {
                    GlyphIcon(glyph, accent, size = 15.dp)
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    card.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = p.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (card.meta.isNotBlank() && card.kind == CardKind.CALL) {
                    Text(card.meta, style = MaterialTheme.typography.labelSmall, color = p.textTertiary)
                }
            }
            if (card.subtitle.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    card.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = p.textSecondary,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (card.steps.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                card.steps.forEach { step ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 3.dp)
                    ) {
                        CheckCircle(step.done)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            step.text,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (step.done) p.textTertiary else p.textPrimary,
                            textDecoration = if (step.done) TextDecoration.LineThrough else TextDecoration.None
                        )
                    }
                }
                val done = card.steps.count { it.done }
                Spacer(Modifier.height(8.dp))
                Box(
                    Modifier.fillMaxWidth().height(4.dp).clip(CircleShape).background(p.control)
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(done.toFloat() / card.steps.size.coerceAtLeast(1))
                            .height(4.dp)
                            .clip(CircleShape)
                            .background(p.blue)
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text("$done / ${card.steps.size}", style = MaterialTheme.typography.labelSmall, color = p.textTertiary)
            }
            if (card.meta.isNotBlank() && card.kind != CardKind.CALL && card.steps.isEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(card.meta, style = MaterialTheme.typography.labelSmall, color = p.textTertiary)
            }
            if (onAction != null && card.kind != CardKind.CALL) {
                Spacer(Modifier.height(10.dp))
                Row(
                    Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(p.raised)
                        .clickable(onClick = onAction)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        when (card.kind) {
                            CardKind.TASK -> tr(lang, "할 일 열기", "Open task")
                            CardKind.FILE -> tr(lang, "파일 열기", "Open file")
                            CardKind.LINK -> tr(lang, "링크 열기", "Open link")
                            else -> tr(lang, "자세히", "Details")
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = p.textPrimary,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("›", color = p.textSecondary)
                }
            }
        }
    }
}

@Composable
private fun com.dominion.mobile.ui.theme.DominionPalette.surfaceChatCardElevated(): Color =
    if (dark) Color(0xFF2E3138) else Color.White

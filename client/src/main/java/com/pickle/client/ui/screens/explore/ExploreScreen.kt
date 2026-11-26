package com.pickle.client.ui.screens.explore

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pickle.client.data.model.AppState
import com.pickle.client.data.model.ExploreCategory
import com.pickle.client.data.model.ExploreTemplate
import com.pickle.client.ui.AppViewModel
import com.pickle.client.ui.LocalLang
import com.pickle.client.ui.components.Glyph
import com.pickle.client.ui.components.GlyphIcon
import com.pickle.client.ui.components.JaumAvatar
import com.pickle.client.ui.components.PickleBottomSheet
import com.pickle.client.ui.components.PickleChip
import com.pickle.client.ui.components.PrimaryButton
import com.pickle.client.ui.components.SectionLabel
import com.pickle.client.ui.components.SheetTitle
import com.pickle.client.ui.components.toast
import com.pickle.client.ui.theme.LocalPicklePalette
import com.pickle.client.ui.tr
import androidx.compose.ui.platform.LocalContext

@Composable
fun ExploreScreen(
    state: AppState,
    vm: AppViewModel,
    onOpenChat: (String) -> Unit
) {
    val p = LocalPicklePalette.current
    val lang = LocalLang.current
    val context = LocalContext.current
    val categories = remember {
        listOf(ExploreCategory.RECOMMENDED) + ExploreCategory.entries.filterNot { it == ExploreCategory.RECOMMENDED }
    }
    var selectedCategory by remember { mutableIntStateOf(0) }
    var detail by remember { mutableStateOf<ExploreTemplate?>(null) }
    var selectedCharacter by remember { mutableStateOf(state.myCharacter()?.id) }

    val filtered = if (categories[selectedCategory] == ExploreCategory.RECOMMENDED) state.explore
    else state.explore.filter { it.category == categories[selectedCategory] }

    Column(Modifier.fillMaxSize().statusBarsPadding().background(p.screen)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    tr(lang, "탐색", "Explore"),
                    style = MaterialTheme.typography.headlineMedium,
                    color = p.textPrimary
                )
                Text(
                    tr(lang, "피클과 함께 해볼 수 있는 일", "Things you can do with your Pickle"),
                    style = MaterialTheme.typography.bodySmall,
                    color = p.textTertiary
                )
            }
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(p.raised)
                    .clickable { detail = state.explore.randomOrNull() },
                contentAlignment = Alignment.Center
            ) {
                GlyphIcon(Glyph.SPARK, p.textPrimary, size = 19.dp)
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 100.dp)
        ) {
            item {
                Box(
                    Modifier
                        .padding(horizontal = 16.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(22.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(Color(0xFF4797EF).copy(alpha = 0.95f), Color(0xFF8B7CF6).copy(alpha = 0.9f))
                            )
                        )
                        .padding(18.dp)
                ) {
                    Column {
                        Text(
                            tr(lang, "오늘, 무엇을 맡겨볼까요?", "What can we take care of today?"),
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White
                        )
                        Spacer(Modifier.height(5.dp))
                        Text(
                            tr(lang, "일정부터 여행 준비까지 피클이 먼저 찾아볼게요.", "From schedules to trips, your Pickle can start first."),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.82f)
                        )
                        Spacer(Modifier.height(14.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(
                                tr(lang, "일정 정리", "My day"),
                                tr(lang, "환불 챙기기", "Refunds"),
                                tr(lang, "여행 준비", "Travel")
                            ).forEachIndexed { index, label ->
                                Box(
                                    Modifier
                                        .clip(RoundedCornerShape(15.dp))
                                        .background(Color.White.copy(alpha = 0.18f))
                                        .clickable {
                                            detail = when (index) {
                                                0 -> state.explore.firstOrNull { it.id == "ex_brief" }
                                                1 -> state.explore.firstOrNull { it.id == "ex_refund" }
                                                else -> state.explore.firstOrNull { it.id == "ex_flight" }
                                            } ?: state.explore.firstOrNull()
                                        }
                                        .padding(horizontal = 12.dp, vertical = 7.dp)
                                ) {
                                    Text(label, style = MaterialTheme.typography.labelSmall, color = Color.White)
                                }
                            }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(16.dp)) }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(categories.size) { index ->
                        PickleChip(
                            text = categories[index].label(lang),
                            selected = index == selectedCategory,
                            onClick = { selectedCategory = index }
                        )
                    }
                }
            }
            item { SectionLabel(tr(lang, "추천", "For you"), Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) }
            items(filtered.chunked(2)) { row ->
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    row.forEach { template ->
                        ExploreCard(
                            template = template,
                            modifier = Modifier.weight(1f),
                            onClick = { detail = template }
                        )
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
                Spacer(Modifier.height(10.dp))
            }
            item {
                Spacer(Modifier.height(10.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(p.cardSoft)
                        .clickable {
                            val conv = vm.createExtraConversation(tr(lang, "새로운 시도", "New idea"))
                            if (conv.isNotBlank()) onOpenChat(conv)
                        }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier.size(36.dp).clip(CircleShape).background(p.blue.copy(alpha = 0.13f)),
                        contentAlignment = Alignment.Center
                    ) { GlyphIcon(Glyph.PLUS, p.blue, size = 18.dp) }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(tr(lang, "직접 물어보기", "Ask anything"), style = MaterialTheme.typography.titleSmall, color = p.textPrimary)
                        Text(tr(lang, "새 대화를 열고 피클에게 말을 걸어요", "Open a new chat and ask your Pickle"), style = MaterialTheme.typography.bodySmall, color = p.textTertiary)
                    }
                    Text("›", color = p.textTertiary)
                }
            }
        }
    }

    detail?.let { template ->
        PickleBottomSheet(onDismiss = { detail = null }) {
            SheetTitle(template.title(lang), template.subtitle(lang))
            Column(Modifier.padding(horizontal = 24.dp)) {
                Text(
                    tr(lang, "이렇게 진행해요", "How it works"),
                    style = MaterialTheme.typography.labelMedium,
                    color = p.textTertiary
                )
                Spacer(Modifier.height(10.dp))
                template.steps.forEachIndexed { index, step ->
                    Row(Modifier.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(22.dp).clip(CircleShape).background(p.raised),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("${index + 1}", style = MaterialTheme.typography.labelSmall, color = p.textSecondary)
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(step, style = MaterialTheme.typography.bodyMedium, color = p.textPrimary)
                    }
                }
                Spacer(Modifier.height(14.dp))
                Text(
                    tr(lang, "누구와 함께할까요?", "Who should help?"),
                    style = MaterialTheme.typography.labelMedium,
                    color = p.textTertiary
                )
                Spacer(Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(state.characters) { character ->
                        val selected = character.id == selectedCharacter
                        Row(
                            Modifier
                                .clip(RoundedCornerShape(18.dp))
                                .background(if (selected) p.actionPrimary else p.raised)
                                .clickable { selectedCharacter = character.id }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            JaumAvatar(Color(character.color), character.shape, size = 22.dp)
                            Spacer(Modifier.width(6.dp))
                            Text(
                                character.name,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (selected) p.onActionPrimary else p.textSecondary
                            )
                        }
                    }
                }
                Spacer(Modifier.height(18.dp))
                PrimaryButton(
                    text = tr(lang, "대화에 추가하기", "Add to a chat"),
                    onClick = {
                        val characterId = selectedCharacter ?: state.myCharacter()?.id
                        val convId = characterId?.let { vm.directConversationFor(it) }
                        if (convId != null) {
                            vm.addExploreToChat(convId, template.title(lang), template.steps, template.prompt)
                            detail = null
                            context.toast(tr(lang, "대화에 추가했어요", "Added to your chat"))
                            onOpenChat(convId)
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun ExploreCard(
    template: ExploreTemplate,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val p = LocalPicklePalette.current
    val lang = LocalLang.current
    val accent = Color(template.accent)
    Column(
        modifier
            .height(154.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.linearGradient(
                    listOf(accent.copy(alpha = 0.16f), p.card),
                    start = androidx.compose.ui.geometry.Offset.Zero,
                    end = androidx.compose.ui.geometry.Offset(500f, 500f)
                )
            )
            .clickable(onClick = onClick)
            .padding(14.dp)
    ) {
        Box(
            Modifier.size(34.dp).clip(CircleShape).background(accent.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            GlyphIcon(iconFor(template.category), accent, size = 17.dp)
        }
        Spacer(Modifier.weight(1f))
        Text(
            template.title(lang),
            style = MaterialTheme.typography.titleSmall,
            color = p.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(3.dp))
        Text(
            template.subtitle(lang),
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
            color = p.textTertiary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private fun iconFor(category: ExploreCategory): Glyph = when (category) {
    ExploreCategory.WORK -> Glyph.FILE
    ExploreCategory.MONEY -> Glyph.CREDIT
    ExploreCategory.HOME -> Glyph.TASK
    ExploreCategory.HEALTH -> Glyph.SHIELD
    ExploreCategory.SHOPPING -> Glyph.TICKET
    ExploreCategory.TRAVEL -> Glyph.GLOBE
    ExploreCategory.FUN -> Glyph.SPARK
    ExploreCategory.PEOPLE -> Glyph.IMAGE
    ExploreCategory.RECOMMENDED -> Glyph.SPARK
}

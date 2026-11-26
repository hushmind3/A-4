package com.dominion.mobile.ui.screens.profile

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dominion.mobile.data.model.ActivityItem
import com.dominion.mobile.data.model.ActivityType
import com.dominion.mobile.data.model.AppState
import com.dominion.mobile.data.model.Plan
import com.dominion.mobile.data.model.ProductKind
import com.dominion.mobile.data.model.StoreProduct
import com.dominion.mobile.data.model.TxKind
import com.dominion.mobile.ui.AppViewModel
import com.dominion.mobile.ui.LocalLang
import com.dominion.mobile.ui.components.BalanceTile
import com.dominion.mobile.ui.components.CountPill
import com.dominion.mobile.ui.components.Glyph
import com.dominion.mobile.ui.components.GlyphIcon
import com.dominion.mobile.ui.components.Hairline
import com.dominion.mobile.ui.components.AppBottomSheet
import com.dominion.mobile.ui.components.FilterChipItem
import com.dominion.mobile.ui.components.ScreenTopBar
import com.dominion.mobile.ui.components.PrimaryButton
import com.dominion.mobile.ui.components.SectionLabel
import com.dominion.mobile.ui.components.SheetRow
import com.dominion.mobile.ui.components.SheetTitle
import com.dominion.mobile.ui.components.toast
import com.dominion.mobile.ui.theme.LocalDominionPalette
import com.dominion.mobile.ui.tr
import com.dominion.mobile.util.asRelative

@Composable
fun CreditsScreen(
    state: AppState,
    vm: AppViewModel,
    onBack: () -> Unit
) {
    val p = LocalDominionPalette.current
    val lang = LocalLang.current
    val context = LocalContext.current
    var buyTarget by remember { mutableStateOf<StoreProduct?>(null) }
    var showPlans by remember { mutableStateOf(false) }
    var tab by remember { mutableIntStateOf(0) }
    val tabs = listOf(tr(lang, "젤리 충전", "Get jelly"), tr(lang, "이용 내역", "History"), tr(lang, "플랜", "Plans"))

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().background(p.screen)) {
        ScreenTopBar(
            title = tr(lang, "젤리와 플랜", "Jelly & plans"),
            subtitle = tr(lang, "대화와 통화에 사용하는 젤리와 에너지", "Jelly and energy for chats and calls"),
            onBack = onBack
        )
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 34.dp)) {
            item {
                Box(
                    Modifier
                        .padding(horizontal = 16.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(Color(0xFFFFC46B), Color(0xFFFF8FB1), Color(0xFF8B7CF6))
                            )
                        )
                        .padding(18.dp)
                ) {
                    Column {
                        Text(
                            tr(lang, "오늘의 잔액", "Today's balance"),
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "${state.account.jelly} ${tr(lang, "젤리", "jelly")}",
                            style = MaterialTheme.typography.displaySmall,
                            color = Color.White
                        )
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MiniBalance("${state.account.energy}/${state.account.energyMax}", tr(lang, "에너지", "Energy"))
                            MiniBalance("${state.account.tickets}", tr(lang, "리셋 티켓", "Tickets"))
                            MiniBalance("${state.account.credits}", tr(lang, "크레딧", "Credits"))
                        }
                        Spacer(Modifier.height(14.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(Color.White)
                                    .clickable {
                                        if (!vm.claimDailyJelly()) context.toast(tr(lang, "오늘은 이미 받았어요", "Already claimed today"))
                                        else context.toast(tr(lang, "젤리 30개를 받았어요", "30 jelly claimed"))
                                    }
                                    .padding(horizontal = 13.dp, vertical = 8.dp)
                            ) {
                                Text(tr(lang, "출석 보상 받기", "Claim daily"), style = MaterialTheme.typography.labelSmall, color = Color(0xFF1B1D22), fontWeight = FontWeight.Medium)
                            }
                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(Color.White.copy(alpha = 0.18f))
                                    .clickable { showPlans = true }
                                    .padding(horizontal = 13.dp, vertical = 8.dp)
                            ) {
                                Text("${state.account.plan.label(lang)} · ${tr(lang, "플랜 변경", "Change plan")}", style = MaterialTheme.typography.labelSmall, color = Color.White)
                            }
                        }
                    }
                }
            }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(tabs.size) { index ->
                        FilterChipItem(tabs[index], tab == index, onClick = { tab = index })
                    }
                }
            }
            if (tab == 0) {
                item { SectionLabel(tr(lang, "젤리 충전", "Get jelly"), Modifier.padding(horizontal = 20.dp)) }
                item {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        modifier = Modifier.fillMaxWidth().height(((state.products.size + 1) / 2 * 132).dp),
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        userScrollEnabled = false
                    ) {
                        items(state.products, key = { it.id }) { product ->
                            ProductCard(product) { buyTarget = product }
                        }
                    }
                }
                item { SectionLabel(tr(lang, "이용 안내", "Good to know"), Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) }
                item {
                    Box(Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(p.cardSoft).padding(14.dp)) {
                        Text(
                            tr(lang, "젤리는 대화와 기능 사용에 쓰여요. 플랜에 따라 매월 자동 충전돼요.", "Jelly is used for conversations and features. Plans refill monthly."),
                            style = MaterialTheme.typography.bodySmall,
                            color = p.textTertiary
                        )
                    }
                }
            }
            if (tab == 1) {
                item { SectionLabel(tr(lang, "이용 내역", "History"), Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) }
                items(state.transactions, key = { it.id }) { tx ->
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier.size(34.dp).clip(CircleShape).background(
                                if (tx.kind == TxKind.USE) p.raised else p.blue.copy(alpha = 0.12f)
                            ),
                            contentAlignment = Alignment.Center
                        ) {
                            GlyphIcon(
                                if (tx.kind == TxKind.USE) Glyph.BOLT else Glyph.JELLY,
                                if (tx.kind == TxKind.USE) p.textSecondary else p.blue,
                                size = 17.dp
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(tx.title, style = MaterialTheme.typography.titleSmall, color = p.textPrimary)
                            Text(tx.createdAt.asRelative(lang), style = MaterialTheme.typography.labelSmall, color = p.textTertiary)
                        }
                        Text(
                            if (tx.amount > 0) "+${tx.amount}" else if (tx.amount < 0) "${tx.amount}" else "-",
                            style = MaterialTheme.typography.titleSmall,
                            color = if (tx.amount > 0) p.blue else p.textSecondary
                        )
                    }
                    Hairline(start = 62.dp)
                }
            }
            if (tab == 2) {
                item {
                    Column(Modifier.padding(horizontal = 16.dp)) {
                        listOf(Plan.FREE, Plan.PLUS, Plan.PRO).forEach { plan ->
                            PlanCard(plan, current = state.account.plan == plan) {
                                if (plan != Plan.FREE) {
                                    vm.purchase(
                                        StoreProduct(
                                            "plan_${plan.name}",
                                            plan.label(lang) + " " + tr(lang, "플랜", "plan"),
                                            plan.price(lang),
                                            plan.price(lang),
                                            1,
                                            ProductKind.PLAN
                                        )
                                    )
                                    context.toast(tr(lang, "${plan.label(lang)} 플랜으로 바뀌었어요 (데모)", "Switched to ${plan.label(lang)} (demo)"))
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                }
            }
        }
    }

    buyTarget?.let { product ->
        AppBottomSheet(onDismiss = { buyTarget = null }) {
            SheetTitle(product.title, product.subtitle)
            Column(Modifier.padding(horizontal = 24.dp)) {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(p.cardSoft).padding(15.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    GlyphIcon(
                        when (product.kind) {
                            ProductKind.JELLY -> Glyph.JELLY
                            ProductKind.ENERGY -> Glyph.BOLT
                            ProductKind.TICKET -> Glyph.TICKET
                            ProductKind.PLAN -> Glyph.SPARK
                        },
                        p.blue,
                        size = 22.dp
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(product.title, style = MaterialTheme.typography.titleSmall, color = p.textPrimary)
                        Text(product.subtitle, style = MaterialTheme.typography.bodySmall, color = p.textTertiary)
                    }
                    Text(product.price, style = MaterialTheme.typography.titleSmall, color = p.textPrimary)
                }
                Spacer(Modifier.height(16.dp))
                PrimaryButton(
                    text = tr(lang, "${product.price}에 구매", "Buy for ${product.price}"),
                    onClick = {
                        vm.purchase(product)
                        buyTarget = null
                        context.toast(tr(lang, "구매가 완료됐어요 (데모)", "Purchase complete (demo)"))
                    }
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    tr(lang, "실제 결제는 Google Play 연결 후 활성화돼요.", "Real payments activate after Google Play is connected."),
                    style = MaterialTheme.typography.labelSmall,
                    color = p.textTertiary,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            }
        }
    }

    if (showPlans) {
        AppBottomSheet(onDismiss = { showPlans = false }) {
            SheetTitle(tr(lang, "플랜 비교", "Compare plans"), tr(lang, "언제든 바꿀 수 있어요.", "Change anytime."))
            Column(Modifier.padding(horizontal = 24.dp)) {
                PlanCard(Plan.FREE, state.account.plan == Plan.FREE) {}
                Spacer(Modifier.height(8.dp))
                PlanCard(Plan.PLUS, state.account.plan == Plan.PLUS) {
                    vm.purchase(StoreProduct("plan_plus", "플러스", "", "₩12,000 / 월", 1, ProductKind.PLAN))
                    showPlans = false
                }
                Spacer(Modifier.height(8.dp))
                PlanCard(Plan.PRO, state.account.plan == Plan.PRO) {
                    vm.purchase(StoreProduct("plan_pro", "프로", "", "₩29,000 / 월", 1, ProductKind.PLAN))
                    showPlans = false
                }
            }
        }
    }
}

@Composable
private fun MiniBalance(value: String, label: String) {
    Column(
        Modifier.clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.18f)).padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(value, style = MaterialTheme.typography.labelMedium, color = Color.White)
        Text(label, style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), color = Color.White.copy(alpha = 0.7f))
    }
}

@Composable
private fun ProductCard(product: StoreProduct, onClick: () -> Unit) {
    val p = LocalDominionPalette.current
    val lang = LocalLang.current
    Column(
        Modifier
            .fillMaxWidth()
            .height(122.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(p.card)
            .border(0.5.dp, p.hairline, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(13.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(30.dp).clip(CircleShape).background(p.blue.copy(alpha = 0.10f)), contentAlignment = Alignment.Center) {
                GlyphIcon(
                    when (product.kind) {
                        ProductKind.JELLY -> Glyph.JELLY
                        ProductKind.ENERGY -> Glyph.BOLT
                        ProductKind.TICKET -> Glyph.TICKET
                        ProductKind.PLAN -> Glyph.SPARK
                    },
                    p.blue,
                    size = 16.dp
                )
            }
            Spacer(Modifier.weight(1f))
            Text(product.price, style = MaterialTheme.typography.labelMedium, color = p.textPrimary)
        }
        Spacer(Modifier.weight(1f))
        Text(product.title, style = MaterialTheme.typography.titleSmall, color = p.textPrimary)
        Text(product.subtitle, style = MaterialTheme.typography.labelSmall, color = p.textTertiary, maxLines = 1)
    }
}

@Composable
private fun PlanCard(plan: Plan, current: Boolean, onClick: () -> Unit) {
    val p = LocalDominionPalette.current
    val lang = LocalLang.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(if (current) p.selected else p.card)
            .border(1.dp, if (current) p.blue.copy(alpha = 0.5f) else p.hairline, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(15.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(plan.label(lang), style = MaterialTheme.typography.titleSmall, color = p.textPrimary)
                if (current) {
                    Spacer(Modifier.width(6.dp))
                    Text("✓", color = p.blue, fontWeight = FontWeight.Bold)
                }
            }
            Text(
                when (plan) {
                    Plan.FREE -> tr(lang, "기본 대화와 하루 에너지 3개", "Basic chats and 3 energy a day")
                    Plan.PLUS -> tr(lang, "월 젤리 300개 · 에너지 10개", "300 jelly · 10 energy a month")
                    Plan.PRO -> tr(lang, "월 젤리 800개 · 우선 응답", "800 jelly · priority responses")
                },
                style = MaterialTheme.typography.bodySmall,
                color = p.textTertiary
            )
        }
        Text(plan.price(lang), style = MaterialTheme.typography.labelMedium, color = p.textSecondary)
    }
}

@Composable
fun InviteScreen(
    state: AppState,
    onBack: () -> Unit
) {
    val p = LocalDominionPalette.current
    val lang = LocalLang.current
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val link = "dominion://u/${state.account.inviteCode.lowercase()}"

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().background(p.screen)) {
        ScreenTopBar(title = tr(lang, "친구 초대", "Invite friends"), onBack = onBack)
        Column(Modifier.weight(1f)) {
            Column(
                Modifier.fillMaxWidth().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(listOf(Color(0xFF4797EF), Color(0xFF8B7CF6)))),
                    contentAlignment = Alignment.Center
                ) {
                    GlyphIcon(Glyph.GIFT, Color.White, size = 32.dp)
                }
                Spacer(Modifier.height(14.dp))
                Text(tr(lang, "친구와 도미니온을 나눠보세요", "Share Dominion with a friend"), style = MaterialTheme.typography.headlineSmall, color = p.textPrimary, textAlign = TextAlign.Center)
                Spacer(Modifier.height(6.dp))
                Text(
                    tr(lang, "친구가 초대를 수락하면 젤리 50개를 받아요.", "You get 50 jelly when a friend joins."),
                    style = MaterialTheme.typography.bodyMedium,
                    color = p.textTertiary,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(22.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(p.card)
                        .border(0.5.dp, p.hairline, RoundedCornerShape(18.dp))
                        .padding(16.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(tr(lang, "내 초대 코드", "My invite code"), style = MaterialTheme.typography.labelSmall, color = p.textTertiary)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            state.account.inviteCode,
                            style = MaterialTheme.typography.headlineMedium.copy(letterSpacing = 2.sp),
                            color = p.textPrimary
                        )
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(
                                Modifier.clip(RoundedCornerShape(13.dp)).background(p.raised).clickable {
                                    clipboard.setText(AnnotatedString(link))
                                    context.toast(tr(lang, "링크를 복사했어요", "Link copied"))
                                }.padding(horizontal = 12.dp, vertical = 8.dp)
                            ) { Text(tr(lang, "링크 복사", "Copy link"), style = MaterialTheme.typography.labelSmall, color = p.textPrimary) }
                            Box(
                                Modifier.clip(RoundedCornerShape(13.dp)).background(p.actionPrimary).clickable {
                                    val intent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, tr(lang, "도미니온에서 함께 이야기해요! $link", "Let's talk on Dominion! $link"))
                                    }
                                    context.startActivity(Intent.createChooser(intent, "Share"))
                                }.padding(horizontal = 12.dp, vertical = 8.dp)
                            ) { Text(tr(lang, "공유하기", "Share"), style = MaterialTheme.typography.labelSmall, color = p.onActionPrimary) }
                        }
                    }
                }
                Spacer(Modifier.height(18.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(p.cardSoft)
                        .border(0.5.dp, p.hairline, RoundedCornerShape(22.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            Modifier.size(84.dp).clip(RoundedCornerShape(18.dp)).background(p.raised),
                            contentAlignment = Alignment.Center
                        ) { GlyphIcon(Glyph.QR, p.textPrimary, size = 42.dp) }
                        Spacer(Modifier.height(8.dp))
                        Text(tr(lang, "QR로 초대하기", "Invite with QR"), style = MaterialTheme.typography.labelMedium, color = p.textSecondary)
                    }
                }
            }
            SectionLabel(tr(lang, "초대한 친구", "Invited friends"), Modifier.padding(horizontal = 20.dp))
            listOf(
                Triple("서연", "@seoyeon", true),
                Triple("도윤", "@doyun.k", true),
                Triple("지우", "@jiwoo.p", false)
            ).forEach { (name, handle, joined) ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(36.dp).clip(CircleShape).background(p.raised), contentAlignment = Alignment.Center) {
                        Text(name.take(1), color = p.textSecondary, fontWeight = FontWeight.Medium)
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(name, style = MaterialTheme.typography.titleSmall, color = p.textPrimary)
                        Text(handle, style = MaterialTheme.typography.labelSmall, color = p.textTertiary)
                    }
                    Text(
                        if (joined) tr(lang, "함께하는 중", "Joined") else tr(lang, "초대 보냄", "Invited"),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (joined) p.blue else p.textTertiary
                    )
                }
            }
        }
    }
}

@Composable
fun InboxScreen(
    state: AppState,
    vm: AppViewModel,
    onBack: () -> Unit
) {
    val p = LocalDominionPalette.current
    val lang = LocalLang.current

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().background(p.screen)) {
        ScreenTopBar(
            title = tr(lang, "받은 소식", "Inbox"),
            subtitle = tr(lang, "친구와 작업 소식", "Friends and task updates"),
            onBack = onBack,
            actions = {
                if (state.unreadActivity() > 0) {
                    Box(
                        Modifier.clip(RoundedCornerShape(12.dp)).background(p.raised).clickable { vm.markAllActivityRead() }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(tr(lang, "모두 읽음", "Mark all"), style = MaterialTheme.typography.labelSmall, color = p.textSecondary)
                    }
                }
            }
        )
        if (state.activity.isEmpty()) {
            com.dominion.mobile.ui.components.EmptyState(
                glyph = Glyph.SPARK,
                title = tr(lang, "받은 소식이 없어요", "No updates"),
                body = tr(lang, "친구 요청과 작업 완료 소식이 여기에 모여요.", "Friend requests and task updates appear here.")
            )
        } else {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 30.dp)) {
                items(state.activity, key = { it.id }) { item ->
                    ActivityRow(item, onClick = { vm.markActivityRead(item.id) })
                }
            }
        }
    }
}

@Composable
private fun ActivityRow(item: ActivityItem, onClick: () -> Unit) {
    val p = LocalDominionPalette.current
    val lang = LocalLang.current
    val glyph = when (item.type) {
        ActivityType.FRIEND -> Glyph.SPARK
        ActivityType.TASK -> Glyph.TASK
        ActivityType.CREDIT -> Glyph.JELLY
        ActivityType.CALL -> Glyph.PHONE
        ActivityType.SYSTEM -> Glyph.SHIELD
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(if (item.read) Color.Transparent else p.blue.copy(alpha = 0.05f))
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            Modifier.size(36.dp).clip(CircleShape).background(p.raised),
            contentAlignment = Alignment.Center
        ) { GlyphIcon(glyph, p.textSecondary, size = 17.dp) }
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(item.title, style = MaterialTheme.typography.titleSmall, color = p.textPrimary, modifier = Modifier.weight(1f))
                if (!item.read) Box(Modifier.size(6.dp).clip(CircleShape).background(p.blue))
            }
            Spacer(Modifier.height(2.dp))
            Text(item.body, style = MaterialTheme.typography.bodySmall, color = p.textTertiary)
            Spacer(Modifier.height(3.dp))
            Text(item.createdAt.asRelative(lang), style = MaterialTheme.typography.labelSmall, color = p.textTertiary)
        }
    }
    Hairline(start = 65.dp)
}

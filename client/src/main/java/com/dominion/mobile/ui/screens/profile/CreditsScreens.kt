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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.material3.TextButton
import androidx.compose.material3.LinearProgressIndicator
import com.dominion.mobile.DominionApplication
import com.dominion.mobile.data.remote.CloudAccountSnapshot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
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
    onBack: () -> Unit,
    onCloudAccount: () -> Unit
) {
    val p = LocalDominionPalette.current
    val lang = LocalLang.current
    val context = LocalContext.current
    val cloud = remember(context.applicationContext) {
        (context.applicationContext as DominionApplication).container.cloudAccount
    }
    val scope = rememberCoroutineScope()
    var snapshot by remember { mutableStateOf<CloudAccountSnapshot?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    suspend fun refreshBilling() {
        if (busy) return
        busy = true; error = false
        try { snapshot = cloud.refresh() }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { snapshot = null; error = true }
        finally { busy = false }
    }
    LaunchedEffect(cloud) { refreshBilling() }
    val billing = snapshot?.takeIf { it.status == "approved" }?.billing
    var buyTarget by remember { mutableStateOf<StoreProduct?>(null) }
    var showPlans by remember { mutableStateOf(false) }
    var tab by remember { mutableIntStateOf(0) }
    val tabs = listOf(tr(lang, "젤리 충전", "Get jelly"), tr(lang, "이용 내역", "History"), tr(lang, "플랜", "Plans"))

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().background(p.screen)) {
        ScreenTopBar(
            title = tr(lang, "젤리와 플랜", "Jelly & plans"),
            subtitle = tr(lang, "실제 Cloud 이용량과 계정 정보", "Verified Cloud usage and account"),
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
                            tr(lang, "DOMINION Cloud 잔액", "DOMINION Cloud balance"),
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            billing?.balanceKrw() ?: tr(lang, "조회 필요", "Not verified"),
                            style = MaterialTheme.typography.displaySmall,
                            color = Color.White
                        )
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MiniBalance(billing?.let { it.free_daily_remaining.toString() + "/" + it.free_daily_limit } ?: "—",
                                tr(lang, "오늘 무료 요청", "Daily free requests"))
                            MiniBalance(billing?.free_monthly_remaining?.toString() ?: "—", tr(lang, "월 잔여 요청", "Monthly remaining"))
                            MiniBalance(billing?.plan ?: "—", tr(lang, "플랜", "Plan"))
                        }
                        Spacer(Modifier.height(14.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(Color.White)
                                    .clickable {
                                        scope.launch { refreshBilling() }
                                    }
                                    .padding(horizontal = 13.dp, vertical = 8.dp)
                            ) {
                                Text(tr(lang, "잔여량 새로고침", "Refresh quota"), style = MaterialTheme.typography.labelSmall, color = Color(0xFF1B1D22), fontWeight = FontWeight.Medium)
                            }
                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(Color.White.copy(alpha = 0.18f))
                                    .clickable { showPlans = true }
                                    .padding(horizontal = 13.dp, vertical = 8.dp)
                            ) {
                                Text(tr(lang, "플랜 확인", "View plans"), style = MaterialTheme.typography.labelSmall, color = Color.White)
                            }
                        }
                    }
                }
            }
            item {
                Column(Modifier.padding(16.dp)) {
                    if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text(when {
                        error -> tr(lang, "Cloud 조회 실패 · 실제 잔액을 표시하지 않습니다.", "Cloud verification failed")
                        billing == null -> tr(lang, "Cloud 계정 연결이 필요합니다.", "Connect your Cloud account")
                        else -> tr(lang, "서버에서 조회한 이용량입니다. 외부 앱 권한은 무료 추론 전용입니다.", "Verified usage; external app scope is free inference only.")
                    }, color = p.textSecondary)
                    TextButton(onClick = onCloudAccount) { Text(tr(lang, "Cloud 계정 연결·관리", "Manage Cloud account")) }
                }
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
                            tr(lang, "젤리·에너지·티켓은 기존 앱 상품 유형입니다. 현재 Cloud에는 해당 결제 상품이 등록되어 있지 않으며 실제 사용량은 위 서버 조회값을 따릅니다.", "Legacy item types are not configured Cloud products. Actual usage is shown above."),
                            style = MaterialTheme.typography.bodySmall,
                            color = p.textTertiary
                        )
                    }
                }
            }
            if (tab == 1) {
                item {
                    SectionLabel(tr(lang, "기기 기록 (결제 증빙 아님)", "Device history (not payment receipts)"), Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
                    Text(tr(lang, "실제 결제 내역은 PC DOMINION의 Cloud 계정에서 확인하세요.", "View verified payment history in desktop DOMINION Cloud."),
                        modifier = Modifier.padding(horizontal = 20.dp), color = p.textSecondary)
                }
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
                            PlanCard(plan, current = billing?.plan?.equals(plan.name, true) == true,
                                cloudPrice = billing?.pro_price_krw) { onCloudAccount() }
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
                    Text(tr(lang, "상품 미연동", "Not configured"), style = MaterialTheme.typography.titleSmall, color = p.textPrimary)
                }
                Spacer(Modifier.height(16.dp))
                PrimaryButton(
                    text = tr(lang, "실제 결제 설정 확인", "Check payment availability"),
                    onClick = {
                        buyTarget = null
                        onCloudAccount()
                    }
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    tr(lang, "현재 외부 앱 계정에는 결제 권한이 없습니다. PC에서 본인 인증 및 실제 결제를 관리하세요. 이 화면에서는 요금이 청구되지 않습니다.", "External app credentials cannot purchase. Manage identity and payments on desktop; no charge here."),
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
            SheetTitle(tr(lang, "플랜 비교", "Compare plans"), tr(lang, "Cloud에서 확인한 실제 정보", "Verified Cloud information"))
            Column(Modifier.padding(horizontal = 24.dp)) {
                listOf(Plan.FREE, Plan.PLUS, Plan.PRO).forEach { plan ->
                    PlanCard(plan, billing?.plan?.equals(plan.name, true) == true, billing?.pro_price_krw) {
                        showPlans = false
                        onCloudAccount()
                    }
                    Spacer(Modifier.height(8.dp))
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
            Text(tr(lang, "상품 미연동", "Not configured"), style = MaterialTheme.typography.labelMedium, color = p.textPrimary)
        }
        Spacer(Modifier.weight(1f))
        Text(product.title, style = MaterialTheme.typography.titleSmall, color = p.textPrimary)
        Text(product.subtitle, style = MaterialTheme.typography.labelSmall, color = p.textTertiary, maxLines = 1)
    }
}

@Composable
private fun PlanCard(plan: Plan, current: Boolean, cloudPrice: Long?, onClick: () -> Unit) {
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
                    Plan.FREE -> tr(lang, "서버가 정한 무료 요청 한도", "Server-defined free request quota")
                    Plan.PLUS -> tr(lang, "현재 Cloud 상품 미등록", "Not configured in Cloud")
                    Plan.PRO -> tr(lang, "Cloud 구독 · PC에서 관리", "Cloud subscription · manage on desktop")
                },
                style = MaterialTheme.typography.bodySmall,
                color = p.textTertiary
            )
        }
        Text(when (plan) {
            Plan.FREE -> "0원"
            Plan.PLUS -> "—"
            Plan.PRO -> cloudPrice?.takeIf { it > 0 }?.let { it.toString() + "원" } ?: "—"
        }, style = MaterialTheme.typography.labelMedium, color = p.textSecondary)
    }
}

@Composable
fun InviteScreen(state: AppState, onBack: () -> Unit) {
    com.dominion.mobile.ui.screens.friends.ContactInviteScreen(state, onBack)
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

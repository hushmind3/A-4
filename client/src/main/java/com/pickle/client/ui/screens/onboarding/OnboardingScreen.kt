package com.pickle.client.ui.screens.onboarding

import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pickle.client.data.model.AppState
import com.pickle.client.data.model.CharacterShape
import com.pickle.client.data.model.Lang
import com.pickle.client.data.model.VoiceTone
import com.pickle.client.ui.AppViewModel
import com.pickle.client.ui.LocalLang
import com.pickle.client.ui.OnboardingProfile
import com.pickle.client.ui.components.CheckCircle
import com.pickle.client.ui.components.Glyph
import com.pickle.client.ui.components.GlyphIcon
import com.pickle.client.ui.components.JaumAvatar
import com.pickle.client.ui.components.LanguageToggle
import com.pickle.client.ui.components.PickleTextField
import com.pickle.client.ui.components.PrimaryButton
import com.pickle.client.ui.components.SecondaryButton
import com.pickle.client.ui.components.toast
import com.pickle.client.ui.theme.LocalPicklePalette
import com.pickle.client.ui.tr
import kotlinx.coroutines.delay

private const val LAST_STEP = 10

@Composable
fun OnboardingScreen(
    state: AppState,
    vm: AppViewModel,
    onDone: () -> Unit
) {
    val p = LocalPicklePalette.current
    val lang = LocalLang.current
    val context = LocalContext.current

    var step by rememberSaveable { mutableIntStateOf(0) }
    var name by rememberSaveable { mutableStateOf("") }
    var birthday by rememberSaveable { mutableStateOf("") }
    var phone by rememberSaveable { mutableStateOf("") }
    var code by rememberSaveable { mutableStateOf("") }
    var provider by rememberSaveable { mutableStateOf("") }
    var characterName by rememberSaveable { mutableStateOf("몽글") }
    var color by rememberSaveable { mutableStateOf(0xFF4797EF) }
    var shape by rememberSaveable { mutableStateOf(CharacterShape.BLOB.name) }
    var voice by rememberSaveable { mutableStateOf(VoiceTone.SOFT.name) }
    var terms by rememberSaveable { mutableStateOf(false) }
    var privacy by rememberSaveable { mutableStateOf(false) }
    var age by rememberSaveable { mutableStateOf(false) }
    var training by rememberSaveable { mutableStateOf(false) }
    var marketing by rememberSaveable { mutableStateOf(false) }
    var contactsAllowed by rememberSaveable { mutableStateOf(false) }

    val canContinue = when (step) {
        3 -> name.isNotBlank()
        4 -> birthday.replace(".", "").length >= 6
        5 -> terms && privacy && age
        7 -> characterName.isNotBlank()
        else -> true
    }

    fun finish() {
        vm.completeOnboarding(
            OnboardingProfile(
                name = name.ifBlank { "민지" },
                birthday = birthday,
                characterName = characterName.ifBlank { "몽글" },
                color = color,
                shape = runCatching { CharacterShape.valueOf(shape) }.getOrDefault(CharacterShape.BLOB),
                voice = runCatching { VoiceTone.valueOf(voice) }.getOrDefault(VoiceTone.SOFT),
                lang = lang
            )
        )
        onDone()
    }

    Box(Modifier.fillMaxSize().background(p.screen)) {
        if (step == 0) {
            LoginStep(
                lang = lang,
                onChangeLang = vm::setLanguage,
                onPhone = { step = 1 },
                onSocial = { selected ->
                    provider = selected
                    context.toast(tr(lang, "$selected 로그인은 데모로 진행돼요", "$selected sign-in is mocked in this demo"))
                    step = 3
                },
                onDemo = { vm.enterDemo(); onDone() }
            )
            return@Box
        }

        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .imePadding()
                .navigationBarsPadding()
        ) {
            Row(
                Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { if (step > 1) step -= 1 else step = 0 }) {
                    Icon(Icons.Filled.ArrowBack, "Back", tint = p.textPrimary, modifier = Modifier.size(21.dp))
                }
                Spacer(Modifier.weight(1f))
                LanguageToggle(current = lang, onChange = vm::setLanguage)
                Spacer(Modifier.width(6.dp))
            }

            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                repeat(LAST_STEP) { index ->
                    Box(
                        Modifier
                            .weight(1f)
                            .height(3.dp)
                            .clip(CircleShape)
                            .background(if (index < step) p.actionPrimary else p.control)
                    )
                }
            }

            AnimatedContent(
                targetState = step,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "onboarding",
                modifier = Modifier.weight(1f)
            ) { target ->
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 22.dp, vertical = 18.dp)
                ) {
                    when (target) {
                        1 -> PhoneStep(phone = phone, onPhone = { phone = it })
                        2 -> CodeStep(phone = phone, code = code, onCode = { code = it })
                        3 -> NameStep(name = name, onName = { name = it })
                        4 -> BirthdayStep(birthday = birthday, onBirthday = { birthday = it })
                        5 -> ConsentStep(
                            terms = terms, privacy = privacy, age = age, training = training, marketing = marketing,
                            onTerms = { terms = it }, onPrivacy = { privacy = it }, onAge = { age = it },
                            onTraining = { training = it }, onMarketing = { marketing = it }
                        )
                        6 -> GuideStep()
                        7 -> CharacterNameStep(name = characterName, onName = { characterName = it })
                        8 -> VoiceStep(voice = voice, onVoice = { voice = it })
                        9 -> CustomizeStep(
                            characterName = characterName,
                            color = color,
                            shape = shape,
                            onColor = { color = it },
                            onShape = { shape = it }
                        )
                        else -> ContactsStep(
                            characterName = characterName,
                            contactsAllowed = contactsAllowed,
                            onAllow = { contactsAllowed = it }
                        )
                    }
                    Spacer(Modifier.height(28.dp))
                }
            }

            Column(Modifier.padding(horizontal = 20.dp, vertical = 10.dp)) {
                PrimaryButton(
                    text = when (step) {
                        1 -> tr(lang, "인증번호 받기", "Send code")
                        2 -> tr(lang, "확인", "Verify")
                        5 -> tr(lang, "동의하고 계속", "Agree and continue")
                        6 -> tr(lang, "알겠어요", "Got it")
                        10 -> tr(lang, "시작하기", "Get started")
                        else -> tr(lang, "계속", "Continue")
                    },
                    enabled = canContinue,
                    onClick = {
                        when (step) {
                            1 -> {
                                if (phone.filter { it.isDigit() }.length < 9) {
                                    context.toast(tr(lang, "전화번호를 확인해 주세요", "Check your phone number"))
                                } else step = 2
                            }
                            2 -> {
                                if (code.length == 6) step = 3
                                else context.toast(tr(lang, "6자리 인증번호를 입력해 주세요", "Enter the 6-digit code"))
                            }
                            5 -> step = 6
                            6 -> step = 7
                            7 -> step = 8
                            8 -> step = 9
                            9 -> step = 10
                            LAST_STEP -> finish()
                            else -> step += 1
                        }
                    }
                )
                if (step == LAST_STEP) {
                    Spacer(Modifier.height(8.dp))
                    SecondaryButton(
                        text = tr(lang, "주소록 없이 시작", "Start without contacts"),
                        onClick = { finish() }
                    )
                }
                Spacer(Modifier.height(6.dp))
            }
        }
    }
}

@Composable
private fun LoginStep(
    lang: Lang,
    onChangeLang: (Lang) -> Unit,
    onPhone: () -> Unit,
    onSocial: (String) -> Unit,
    onDemo: () -> Unit
) {
    val p = LocalPicklePalette.current
    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    if (p.dark) {
                        listOf(Color(0xFF17233A), Color(0xFF1B1B24), p.screen)
                    } else {
                        listOf(Color(0xFFEAF3FF), Color(0xFFF8F6FF), p.screen)
                    }
                )
            )
    ) {
        Box(
            Modifier
                .size(320.dp)
                .align(Alignment.TopEnd)
                .padding(top = 20.dp)
                .background(
                    Brush.radialGradient(
                        listOf(Color(0xFF4797EF).copy(alpha = 0.22f), Color.Transparent),
                        center = Offset(200f, 160f),
                        radius = 320f
                    )
                )
        )
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
        ) {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End) {
                LanguageToggle(lang, onChangeLang)
            }
            Spacer(Modifier.height(26.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Pickle",
                    fontSize = 46.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-2).sp,
                    color = if (p.dark) Color.White else Color(0xFF0B0C0F)
                )
                Box(Modifier.padding(start = 4.dp, top = 7.dp).size(8.dp).clip(CircleShape).background(Color(0xFF4797EF)))
            }
            Spacer(Modifier.height(8.dp))
            Text(
                tr(lang, "당신의 곁에, 또 하나의 나", "Another you, always beside you"),
                style = MaterialTheme.typography.headlineSmall,
                color = if (p.dark) Color.White else Color(0xFF15171C)
            )
            Spacer(Modifier.height(4.dp))
            Text(
                tr(lang, "말을 걸면 먼저 찾아보고, 함께 해결해요.", "Ask anything — your Pickle looks ahead and helps."),
                style = MaterialTheme.typography.bodyMedium,
                color = if (p.dark) p.textSecondary else Color(0x99000000)
            )
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                JaumAvatar(Color(0xFF4797EF), CharacterShape.BLOB, size = 64.dp)
                JaumAvatar(Color(0xFFFF8FB1), CharacterShape.BALLOON, size = 54.dp)
                Box(Modifier.padding(top = 10.dp)) {
                    JaumAvatar(Color(0xFF8B7CF6), CharacterShape.DONUT, size = 50.dp)
                }
                Box(Modifier.padding(top = 22.dp)) {
                    JaumAvatar(Color(0xFFF5A524), CharacterShape.CLOUD, size = 40.dp)
                }
            }
            Spacer(Modifier.height(26.dp))

            LoginButton(
                label = tr(lang, "카카오로 계속하기", "Continue with Kakao"),
                badge = "K",
                background = Color(0xFFFEE500),
                foreground = Color(0xFF191600),
                onClick = { onSocial("카카오") }
            )
            LoginButton(
                label = tr(lang, "Google로 계속하기", "Continue with Google"),
                badge = "G",
                background = Color.White,
                foreground = Color(0xFF1A1A1A),
                border = Color(0x1F000000),
                onClick = { onSocial("Google") }
            )
            LoginButton(
                label = tr(lang, "Apple로 계속하기", "Continue with Apple"),
                badge = "A",
                background = Color.Black,
                foreground = Color.White,
                onClick = { onSocial("Apple") }
            )
            LoginButton(
                label = tr(lang, "LINE으로 계속하기", "Continue with LINE"),
                badge = "L",
                background = Color(0xFF06C755),
                foreground = Color.White,
                onClick = { onSocial("LINE") }
            )
            LoginButton(
                label = tr(lang, "네이버로 계속하기", "Continue with Naver"),
                badge = "N",
                background = Color(0xFF03C75A),
                foreground = Color.White,
                onClick = { onSocial("네이버") }
            )
            LoginButton(
                label = tr(lang, "전화번호로 계속하기", "Continue with phone"),
                glyph = Glyph.PHONE,
                background = Color(0xFFF2F2F2),
                foreground = Color(0xFF111111),
                onClick = onPhone
            )

            Spacer(Modifier.height(10.dp))
            Text(
                tr(lang, "데모 모드로 둘러보기", "Explore the demo instead"),
                style = MaterialTheme.typography.labelMedium,
                color = p.blue,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().clickable(onClick = onDemo).padding(10.dp)
            )
            Spacer(Modifier.height(8.dp))
            Text(
                tr(
                    lang,
                    "계속하면 이용약관과 개인정보 처리방침에 동의하게 됩니다.",
                    "By continuing you agree to the Terms and Privacy Policy."
                ),
                style = MaterialTheme.typography.labelSmall,
                color = if (p.dark) p.textTertiary else Color(0x80000000),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(26.dp))
        }
    }
}

@Composable
private fun LoginButton(
    label: String,
    badge: String? = null,
    glyph: Glyph? = null,
    background: Color,
    foreground: Color,
    border: Color? = null,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .height(50.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(background)
            .then(if (border != null) Modifier.border(1.dp, border, RoundedCornerShape(16.dp)) else Modifier)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(24.dp).clip(CircleShape).background(foreground.copy(alpha = 0.10f)),
            contentAlignment = Alignment.Center
        ) {
            if (glyph != null) GlyphIcon(glyph, foreground, size = 14.dp)
            else Text(badge ?: "", color = foreground, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(12.dp))
        Text(label, color = foreground, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun StepHeader(title: String, subtitle: String? = null) {
    val p = LocalPicklePalette.current
    Text(title, style = MaterialTheme.typography.headlineMedium, color = p.textPrimary)
    if (subtitle != null) {
        Spacer(Modifier.height(8.dp))
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = p.textTertiary)
    }
    Spacer(Modifier.height(22.dp))
}

@Composable
private fun PhoneStep(phone: String, onPhone: (String) -> Unit) {
    val p = LocalPicklePalette.current
    val lang = LocalLang.current
    StepHeader(
        tr(lang, "전화번호로 시작하기", "Start with your phone"),
        tr(lang, "계정을 안전하게 지키기 위해 한 번만 확인해요.", "We verify once to keep your account safe.")
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier
                .height(56.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(p.input)
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("🇰🇷", fontSize = 17.sp)
            Spacer(Modifier.width(6.dp))
            Text("+82", style = MaterialTheme.typography.bodyLarge, color = p.textPrimary)
            Spacer(Modifier.width(4.dp))
            Text("⌄", color = p.textTertiary)
        }
        Spacer(Modifier.width(8.dp))
        PickleTextField(
            value = phone,
            onValueChange = { input -> onPhone(input.filter { it.isDigit() || it == '-' }.take(13)) },
            placeholder = "010-0000-0000",
            keyboardType = androidx.compose.ui.text.input.KeyboardType.Phone,
            modifier = Modifier.weight(1f)
        )
    }
    Spacer(Modifier.height(16.dp))
    Text(
        tr(lang, "인증번호는 3분 동안 유효해요.", "The code is valid for 3 minutes."),
        style = MaterialTheme.typography.labelSmall,
        color = p.textTertiary
    )
}

@Composable
private fun CodeStep(phone: String, code: String, onCode: (String) -> Unit) {
    val p = LocalPicklePalette.current
    val lang = LocalLang.current
    var seconds by remember { mutableIntStateOf(59) }
    LaunchedEffect(Unit) {
        while (seconds > 0) {
            delay(1000)
            seconds -= 1
        }
    }
    StepHeader(
        tr(lang, "인증번호를 입력해 주세요", "Enter the code"),
        tr(lang, "$phone 로 보낸 6자리 숫자예요.", "6 digits sent to $phone.")
    )
    PickleTextField(
        value = code,
        onValueChange = { onCode(it.filter { c -> c.isDigit() }.take(6)) },
        placeholder = "000000",
        keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
    )
    Spacer(Modifier.height(12.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            if (seconds > 0) "00:${seconds.toString().padStart(2, '0')}" else tr(lang, "시간 만료", "Expired"),
            style = MaterialTheme.typography.labelMedium,
            color = p.textTertiary
        )
        Spacer(Modifier.weight(1f))
        Text(
            tr(lang, "다시 보내기", "Resend"),
            style = MaterialTheme.typography.labelMedium,
            color = p.blue,
            modifier = Modifier.clickable { seconds = 59 }.padding(4.dp)
        )
    }
    Spacer(Modifier.height(18.dp))
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(p.cardSoft).padding(14.dp)
    ) {
        Text(
            tr(lang, "데모에서는 아무 6자리 숫자나 사용할 수 있어요.", "In the demo any 6 digits work."),
            style = MaterialTheme.typography.bodySmall,
            color = p.textTertiary
        )
    }
}

@Composable
private fun NameStep(name: String, onName: (String) -> Unit) {
    val p = LocalPicklePalette.current
    val lang = LocalLang.current
    StepHeader(
        tr(lang, "어떻게 불러드릴까요?", "What should we call you?"),
        tr(lang, "대화와 알림에 사용할 이름이에요.", "This name appears in chats and notifications.")
    )
    PickleTextField(
        value = name,
        onValueChange = { onName(it.take(20)) },
        placeholder = tr(lang, "이름", "Name")
    )
    Spacer(Modifier.height(14.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("민지", "지우", "서연", "도윤").forEach { suggestion ->
            Box(
                Modifier
                    .clip(RoundedCornerShape(15.dp))
                    .background(p.raised)
                    .clickable { onName(suggestion) }
                    .padding(horizontal = 12.dp, vertical = 7.dp)
            ) {
                Text(suggestion, style = MaterialTheme.typography.labelMedium, color = p.textSecondary)
            }
        }
    }
}

@Composable
private fun BirthdayStep(birthday: String, onBirthday: (String) -> Unit) {
    val p = LocalPicklePalette.current
    val lang = LocalLang.current
    StepHeader(
        tr(lang, "생일을 알려주세요", "When is your birthday?"),
        tr(lang, "생일을 기억해두고 특별한 날을 챙겨드려요.", "Your Pickle remembers and celebrates with you.")
    )
    PickleTextField(
        value = birthday,
        onValueChange = { onBirthday(it.filter { c -> c.isDigit() || c == '.' }.take(10)) },
        placeholder = "1998.04.12",
        keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
    )
    Spacer(Modifier.height(12.dp))
    Text(
        tr(lang, "나중에 프로필에서 바꿀 수 있어요.", "You can change this later in your profile."),
        style = MaterialTheme.typography.labelSmall,
        color = p.textTertiary
    )
}

@Composable
private fun ConsentStep(
    terms: Boolean, privacy: Boolean, age: Boolean, training: Boolean, marketing: Boolean,
    onTerms: (Boolean) -> Unit, onPrivacy: (Boolean) -> Unit, onAge: (Boolean) -> Unit,
    onTraining: (Boolean) -> Unit, onMarketing: (Boolean) -> Unit
) {
    val p = LocalPicklePalette.current
    val lang = LocalLang.current
    StepHeader(
        tr(lang, "시작하기 전에 확인해 주세요", "Before we begin"),
        tr(lang, "꼭 필요한 내용만 확인해요.", "Only the essentials.")
    )
    ConsentRow(tr(lang, "이용약관에 동의합니다 (필수)", "I agree to the Terms (required)"), terms, onTerms)
    Hairline()
    ConsentRow(tr(lang, "개인정보 처리방침에 동의합니다 (필수)", "I agree to the Privacy Policy (required)"), privacy, onPrivacy)
    Hairline()
    ConsentRow(tr(lang, "만 14세 이상입니다 (필수)", "I am 14 or older (required)"), age, onAge)
    Hairline()
    ConsentRow(tr(lang, "서비스 개선을 위한 모델 학습에 동의합니다 (선택)", "Model training for improvements (optional)"), training, onTraining)
    Hairline()
    ConsentRow(tr(lang, "마케팅 알림을 받습니다 (선택)", "Marketing notifications (optional)"), marketing, onMarketing)
    Spacer(Modifier.height(18.dp))
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(p.cardSoft).padding(14.dp)) {
        Text(
            tr(lang, "선택 항목은 동의하지 않아도 서비스를 이용할 수 있어요.", "You can use Pickle without the optional items."),
            style = MaterialTheme.typography.bodySmall,
            color = p.textTertiary
        )
    }
}

@Composable
private fun ConsentRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val p = LocalPicklePalette.current
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CheckCircle(checked)
        Spacer(Modifier.width(10.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, color = p.textPrimary, modifier = Modifier.weight(1f))
        Text("›", color = p.textTertiary)
    }
}

@Composable
private fun GuideStep() {
    val p = LocalPicklePalette.current
    val lang = LocalLang.current
    StepHeader(
        tr(lang, "피클과 이렇게 지내요", "How Pickle works"),
        tr(lang, "기억하고, 먼저 챙기고, 안전하게 지켜요.", "It remembers, looks ahead, and keeps things safe.")
    )
    GuideCard(
        glyph = Glyph.MEMORY,
        title = tr(lang, "말한 내용을 기억해요", "It remembers what you say"),
        body = tr(lang, "좋아하는 것, 일정, 중요한 날을 기억에 저장해요.", "Preferences, schedules and important dates are saved as memories.")
    )
    GuideCard(
        glyph = Glyph.SPARK,
        title = tr(lang, "먼저 찾아보고 알려줘요", "It looks ahead"),
        body = tr(lang, "일정과 할 일을 정리해 직접 확인할 수 있게 해요.", "It prepares tasks and schedules you can review.")
    )
    GuideCard(
        glyph = Glyph.LOCK,
        title = tr(lang, "금고는 나만 열 수 있어요", "Your vault stays yours"),
        body = tr(lang, "민감한 정보는 암호화된 금고에 따로 보관해요.", "Sensitive details live in an encrypted vault.")
    )
}

@Composable
private fun GuideCard(glyph: Glyph, title: String, body: String) {
    val p = LocalPicklePalette.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(p.cardSoft)
            .padding(16.dp)
    ) {
        Box(
            Modifier.size(38.dp).clip(CircleShape).background(p.blue.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) { GlyphIcon(glyph, p.blue, size = 19.dp) }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall, color = p.textPrimary)
            Spacer(Modifier.height(3.dp))
            Text(body, style = MaterialTheme.typography.bodySmall, color = p.textTertiary)
        }
    }
}

@Composable
private fun CharacterNameStep(name: String, onName: (String) -> Unit) {
    val p = LocalPicklePalette.current
    val lang = LocalLang.current
    StepHeader(
        tr(lang, "피클의 이름을 지어주세요", "Name your Pickle"),
        tr(lang, "대화할 때 이 이름으로 불러요.", "This is the name you will talk to.")
    )
    PickleTextField(
        value = name,
        onValueChange = { onName(it.take(12)) },
        placeholder = tr(lang, "예: 몽글", "e.g. Mochi")
    )
    Spacer(Modifier.height(14.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("몽글", "보리", "소담", "라라").forEach { suggestion ->
            Box(
                Modifier
                    .clip(RoundedCornerShape(15.dp))
                    .background(p.raised)
                    .clickable { onName(suggestion) }
                    .padding(horizontal = 12.dp, vertical = 7.dp)
            ) {
                Text(suggestion, style = MaterialTheme.typography.labelMedium, color = p.textSecondary)
            }
        }
    }
    Spacer(Modifier.height(22.dp))
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center
    ) {
        JaumAvatar(Color(0xFF4797EF), CharacterShape.BLOB, size = 96.dp, breathing = true)
    }
}

@Composable
private fun VoiceStep(voice: String, onVoice: (String) -> Unit) {
    val p = LocalPicklePalette.current
    val lang = LocalLang.current
    var playing by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(playing) {
        if (playing != null) {
            delay(1600)
            playing = null
        }
    }
    StepHeader(
        tr(lang, "목소리를 골라주세요", "Choose a voice"),
        tr(lang, "통화할 때 들을 목소리예요. 언제든 바꿀 수 있어요.", "You can change it anytime.")
    )
    VoiceTone.entries.forEach { tone ->
        val selected = voice == tone.name
        Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(if (selected) p.selected else p.cardSoft)
                .border(1.dp, if (selected) p.textPrimary.copy(alpha = 0.5f) else Color.Transparent, RoundedCornerShape(16.dp))
                .clickable { onVoice(tone.name) }
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(38.dp).clip(CircleShape).background(p.blue.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) { GlyphIcon(Glyph.SPEAKER, p.blue, size = 18.dp) }
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "${tone.label(lang)} ${tr(lang, "목소리", "voice")}",
                    style = MaterialTheme.typography.titleSmall,
                    color = p.textPrimary
                )
                Text(
                    when (tone) {
                        VoiceTone.SOFT -> tr(lang, "천천히, 다정하게", "Slow and warm")
                        VoiceTone.BRIGHT -> tr(lang, "밝고 또렷하게", "Bright and clear")
                        VoiceTone.CALM -> tr(lang, "낮고 차분하게", "Low and calm")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = p.textTertiary
                )
            }
            Text(
                if (playing == tone.name) tr(lang, "재생 중", "Playing") else tr(lang, "미리 듣기", "Preview"),
                style = MaterialTheme.typography.labelSmall,
                color = p.blue,
                modifier = Modifier.clickable { playing = tone.name }.padding(6.dp)
            )
        }
    }
}

@Composable
private fun CustomizeStep(
    characterName: String,
    color: Long,
    shape: String,
    onColor: (Long) -> Unit,
    onShape: (String) -> Unit
) {
    val p = LocalPicklePalette.current
    val lang = LocalLang.current
    StepHeader(
        tr(lang, "${characterName}의 모습을 골라주세요", "Give $characterName a look"),
        tr(lang, "색과 모양은 나중에 꾸미기에서 바꿀 수 있어요.", "You can change color and shape later.")
    )
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        JaumAvatar(
            Color(color),
            runCatching { CharacterShape.valueOf(shape) }.getOrDefault(CharacterShape.BLOB),
            size = 120.dp,
            breathing = true
        )
    }
    Spacer(Modifier.height(18.dp))
    Text(tr(lang, "색", "Color"), style = MaterialTheme.typography.labelMedium, color = p.textTertiary)
    Spacer(Modifier.height(8.dp))
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        listOf(
            0xFF4797EF, 0xFFFF8FB1, 0xFF8B7CF6, 0xFF3FB6A8, 0xFFF5A524, 0xFFEE5D5D, 0xFF22252C
        ).forEach { swatch ->
            Box(
                Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(Color(swatch))
                    .border(if (color == swatch) 3.dp else 0.dp, if (color == swatch) p.textPrimary else Color.Transparent, CircleShape)
                    .clickable { onColor(swatch) }
            )
        }
    }
    Spacer(Modifier.height(18.dp))
    Text(tr(lang, "모양", "Shape"), style = MaterialTheme.typography.labelMedium, color = p.textTertiary)
    Spacer(Modifier.height(8.dp))
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        CharacterShape.entries.forEach { s ->
            val selected = shape == s.name
            Box(
                Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (selected) p.selected else p.cardSoft)
                    .border(1.dp, if (selected) p.textPrimary.copy(alpha = 0.5f) else Color.Transparent, RoundedCornerShape(16.dp))
                    .clickable { onShape(s.name) }
                    .padding(6.dp)
            ) {
                JaumAvatar(Color(color), s, size = 44.dp)
            }
        }
    }
}

@Composable
private fun ContactsStep(
    characterName: String,
    contactsAllowed: Boolean,
    onAllow: (Boolean) -> Unit
) {
    val p = LocalPicklePalette.current
    val lang = LocalLang.current
    StepHeader(
        tr(lang, "친구와 피클을 나눠보세요", "Share Pickles with friends"),
        tr(lang, "주소록에서 이미 피클을 쓰는 친구를 찾아드려요.", "Find friends already using Pickle.")
    )
    Row(
        Modifier.fillMaxWidth().padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        JaumAvatar(Color(0xFF4797EF), CharacterShape.BLOB, size = 76.dp)
        Spacer(Modifier.width(26.dp))
        Text("···", color = p.textTertiary, fontSize = 20.sp)
        Spacer(Modifier.width(26.dp))
        JaumAvatar(Color(0xFFFF8FB1), CharacterShape.BALLOON, size = 62.dp)
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (contactsAllowed) p.selected else p.cardSoft)
            .clickable { onAllow(!contactsAllowed) }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CheckCircle(contactsAllowed)
        Spacer(Modifier.width(10.dp))
        Text(
            tr(lang, "주소록에서 친구를 찾도록 허용", "Allow finding friends in contacts"),
            style = MaterialTheme.typography.bodyMedium,
            color = p.textPrimary
        )
    }
    Spacer(Modifier.height(14.dp))
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(p.cardSoft).padding(14.dp)) {
        Text(
            tr(lang, "연락처는 친구를 찾는 데만 사용하고 저장하지 않아요.", "Contacts are used only to find friends and are not stored."),
            style = MaterialTheme.typography.bodySmall,
            color = p.textTertiary
        )
    }
    Spacer(Modifier.height(20.dp))
    Text(
        tr(lang, "$characterName, 기다리고 있어요.", "$characterName is waiting for you."),
        style = MaterialTheme.typography.titleSmall,
        color = p.textPrimary,
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Center
    )
}

@Composable
private fun Hairline() {
    val p = LocalPicklePalette.current
    Box(Modifier.fillMaxWidth().height(0.5.dp).background(p.hairline))
}

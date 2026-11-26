package com.dominion.mobile.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

@Immutable
data class DominionPalette(
    val dark: Boolean,
    val screen: Color,
    val page: Color,
    val card: Color,
    val cardSoft: Color,
    val sheet: Color,
    val raised: Color,
    val control: Color,
    val input: Color,
    val selected: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val textDisabled: Color,
    val textInverse: Color,
    val actionPrimary: Color,
    val onActionPrimary: Color,
    val blue: Color,
    val blueStrong: Color,
    val bubbleIncoming: Color,
    val onBubbleIncoming: Color,
    val bubbleOutgoing: Color,
    val onBubbleOutgoing: Color,
    val border: Color,
    val hairline: Color,
    val danger: Color,
    val callEnd: Color,
    val toggleOn: Color,
    val success: Color
)

val LightDominionPalette = DominionPalette(
    dark = false,
    screen = Color(0xFFFFFFFF),
    page = Color(0xFFFAFAFA),
    card = Color(0xFFFFFFFF),
    cardSoft = Color(0xFFFAFAFA),
    sheet = Color(0xFFFFFFFF),
    raised = Color(0xFFF4F4F4),
    control = Color(0xFFEBEBEB),
    input = Color(0xFFF3F3F3),
    selected = Color(0x0D000000),
    textPrimary = Color(0xFF000000),
    textSecondary = Color(0x80000000),
    textTertiary = Color(0x4D000000),
    textDisabled = Color(0x33000000),
    textInverse = Color(0xFFFFFFFF),
    actionPrimary = Color(0xFF000000),
    onActionPrimary = Color(0xFFFFFFFF),
    blue = Color(0xFF4797EF),
    blueStrong = Color(0xFF1169E5),
    bubbleIncoming = Color(0xFFF4F4F4),
    onBubbleIncoming = Color(0xFF000000),
    bubbleOutgoing = Color(0xFF4797EF),
    onBubbleOutgoing = Color(0xFFFFFFFF),
    border = Color(0x1A000000),
    hairline = Color(0x0F000000),
    danger = Color(0xFFF96E6E),
    callEnd = Color(0xFFEE1717),
    toggleOn = Color(0xFF5693F0),
    success = Color(0xFF2FA67A)
)

val DarkDominionPalette = DominionPalette(
    dark = true,
    screen = Color(0xFF101114),
    page = Color(0xFF101114),
    card = Color(0xFF1B1D22),
    cardSoft = Color(0x0DFFFFFF),
    sheet = Color(0xFF33363F),
    raised = Color(0x17FFFFFF),
    control = Color(0x24FFFFFF),
    input = Color(0x17FFFFFF),
    selected = Color(0x26FFFFFF),
    textPrimary = Color(0xFFF4F5F7),
    textSecondary = Color(0xFFB6BAC4),
    textTertiary = Color(0xFF9197A3),
    textDisabled = Color(0xFF6C727E),
    textInverse = Color(0xFF101114),
    actionPrimary = Color(0xFFF4F5F7),
    onActionPrimary = Color(0xFF101114),
    blue = Color(0xFF78AAFF),
    blueStrong = Color(0xFF78AAFF),
    bubbleIncoming = Color(0xFF25272E),
    onBubbleIncoming = Color(0xFFF4F5F7),
    bubbleOutgoing = Color(0xFF2C70D2),
    onBubbleOutgoing = Color(0xFFFFFFFF),
    border = Color(0x24FFFFFF),
    hairline = Color(0x14FFFFFF),
    danger = Color(0xFFF96E6E),
    callEnd = Color(0xFFF96E6E),
    toggleOn = Color(0xFF5693F0),
    success = Color(0xFF4CC699)
)

val LocalDominionPalette = staticCompositionLocalOf { LightDominionPalette }

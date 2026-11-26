package com.dominion.mobile.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dominion.mobile.data.model.CharacterShape
import com.dominion.mobile.ui.theme.LocalDominionPalette
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

private fun Color.lighten(amount: Float = 0.22f): Color = Color(
    red = (red + amount).coerceAtMost(1f),
    green = (green + amount).coerceAtMost(1f),
    blue = (blue + amount).coerceAtMost(1f),
    alpha = alpha
)

private fun Color.darken(amount: Float = 0.12f): Color = Color(
    red = (red - amount).coerceAtLeast(0f),
    green = (green - amount).coerceAtLeast(0f),
    blue = (blue - amount).coerceAtLeast(0f),
    alpha = alpha
)

@Composable
fun AssistantAvatar(
    color: Color,
    shape: CharacterShape,
    modifier: Modifier = Modifier,
    size: Dp = 52.dp,
    selected: Boolean = false,
    level: Int? = null,
    breathing: Boolean = false
) {
    val p = LocalDominionPalette.current
    val infinite = rememberInfiniteTransition(label = "jaum")
    val breath by infinite.animateFloat(
        initialValue = 0.985f,
        targetValue = 1.02f,
        animationSpec = infiniteRepeatable(tween(1800), RepeatMode.Reverse),
        label = "breath"
    )
    val scale = if (breathing) breath else 1f
    val ringColor = if (selected) p.textPrimary else Color.Transparent

    Box(
        modifier = modifier
            .size(size + if (level != null) 10.dp else 0.dp)
            .scale(scale),
        contentAlignment = Alignment.TopStart
    ) {
        Box(
            modifier = Modifier
                .size(size)
                .offset(x = if (level != null) 2.dp else 0.dp, y = 0.dp)
                .clip(CircleShape)
                .then(if (selected) Modifier.border(1.5.dp, ringColor, CircleShape) else Modifier),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.size(size)) {
                val pad = this.size.minDimension * 0.06f
                val w = this.size.width
                val h = this.size.height
                val brush = Brush.linearGradient(
                    colors = listOf(color.lighten(0.28f), color, color.darken(0.1f)),
                    start = Offset(0f, 0f),
                    end = Offset(w, h)
                )
                when (shape) {
                    CharacterShape.BLOB -> {
                        val path = Path().apply {
                            moveTo(w * 0.5f, pad)
                            cubicTo(w * 0.80f, pad, w - pad, h * 0.26f, w - pad, h * 0.56f)
                            cubicTo(w - pad, h * 0.83f, w * 0.74f, h - pad, w * 0.5f, h - pad)
                            cubicTo(w * 0.26f, h - pad, pad, h * 0.83f, pad, h * 0.56f)
                            cubicTo(pad, h * 0.26f, w * 0.20f, pad, w * 0.5f, pad)
                            close()
                        }
                        drawPath(path, brush)
                    }
                    CharacterShape.BALLOON -> {
                        drawOval(brush, topLeft = Offset(pad, pad), size = Size(w - pad * 2, h * 0.82f))
                        val knot = Path().apply {
                            moveTo(w * 0.43f, h * 0.80f)
                            lineTo(w * 0.57f, h * 0.80f)
                            lineTo(w * 0.50f, h - pad)
                            close()
                        }
                        drawPath(knot, color.darken(0.04f))
                    }
                    CharacterShape.DONUT -> {
                        drawCircle(brush, radius = min(w, h) / 2 - pad, center = center)
                        drawCircle(
                            color = p.screen,
                            radius = min(w, h) * 0.16f,
                            center = center
                        )
                    }
                    CharacterShape.STAR -> {
                        val path = Path()
                        val outer = min(w, h) / 2 - pad
                        val inner = outer * 0.48f
                        val cx = w / 2f
                        val cy = h / 2f
                        for (i in 0 until 10) {
                            val r = if (i % 2 == 0) outer else inner
                            val a = Math.PI / 2 + i * Math.PI / 5
                            val x = cx + (r * cos(a)).toFloat()
                            val y = cy - (r * sin(a)).toFloat()
                            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                        }
                        path.close()
                        drawPath(path, brush)
                    }
                    CharacterShape.CLOUD -> {
                        drawCircle(brush, radius = w * 0.24f, center = Offset(w * 0.32f, h * 0.46f))
                        drawCircle(brush, radius = w * 0.29f, center = Offset(w * 0.55f, h * 0.38f))
                        drawCircle(brush, radius = w * 0.23f, center = Offset(w * 0.72f, h * 0.50f))
                        drawRoundRect(
                            brush,
                            topLeft = Offset(w * 0.20f, h * 0.46f),
                            size = Size(w * 0.60f, h * 0.32f),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.16f)
                        )
                    }
                    CharacterShape.GEM -> {
                        val path = Path().apply {
                            moveTo(w * 0.5f, pad)
                            lineTo(w - pad, h * 0.38f)
                            lineTo(w * 0.72f, h - pad)
                            lineTo(w * 0.28f, h - pad)
                            lineTo(pad, h * 0.38f)
                            close()
                        }
                        drawPath(path, brush)
                    }
                }

                // Face
                val ink = when {
                    shape == CharacterShape.DONUT ->
                        if (p.screen.luminance() > 0.5f) Color(0xCC1B1B1B) else Color(0xF2FFFFFF)
                    color.luminance() > 0.62f -> Color(0xCC1B1B1B)
                    else -> Color(0xF2FFFFFF)
                }
                val eyeY = h * 0.47f
                val eyeDx = w * 0.15f
                val eyeR = w * 0.045f
                drawOval(ink, topLeft = Offset(w / 2 - eyeDx - eyeR, eyeY - eyeR * 1.25f), size = Size(eyeR * 2, eyeR * 2.5f))
                drawOval(ink, topLeft = Offset(w / 2 + eyeDx - eyeR, eyeY - eyeR * 1.25f), size = Size(eyeR * 2, eyeR * 2.5f))
                drawCircle(
                    color = Color.White.copy(alpha = 0.85f),
                    radius = eyeR * 0.38f,
                    center = Offset(w / 2 - eyeDx + eyeR * 0.2f, eyeY - eyeR * 0.5f)
                )
                drawCircle(
                    color = Color.White.copy(alpha = 0.85f),
                    radius = eyeR * 0.38f,
                    center = Offset(w / 2 + eyeDx + eyeR * 0.2f, eyeY - eyeR * 0.5f)
                )

                val mouth = Path().apply {
                    val top = h * 0.60f
                    moveTo(w * 0.42f, top)
                    quadraticBezierTo(w * 0.5f, top + h * 0.075f, w * 0.58f, top)
                }
                drawPath(mouth, ink, style = Stroke(width = w * 0.022f, cap = androidx.compose.ui.graphics.StrokeCap.Round))

                drawCircle(
                    color = Color.White.copy(alpha = 0.16f),
                    radius = w * 0.075f,
                    center = Offset(w * 0.27f, h * 0.58f)
                )
                drawCircle(
                    color = Color.White.copy(alpha = 0.16f),
                    radius = w * 0.075f,
                    center = Offset(w * 0.73f, h * 0.58f)
                )
            }
        }
        if (level != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .clip(CircleShape)
                    .background(p.actionPrimary)
                    .border(2.dp, p.screen, CircleShape)
                    .size(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = level.toString(),
                    color = p.onActionPrimary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

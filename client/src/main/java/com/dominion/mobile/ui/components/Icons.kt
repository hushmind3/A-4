package com.dominion.mobile.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dominion.mobile.data.model.TabId
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun TabIcon(
    tab: TabId,
    selected: Boolean,
    tint: Color,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp
) {
    Canvas(modifier = modifier.size(size)) {
        val s = this.size.minDimension
        val stroke = s * (if (selected) 0.095f else 0.085f)
        val style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round)
        when (tab) {
            TabId.CHATS -> {
                val path = Path().apply {
                    moveTo(s * 0.16f, s * 0.16f)
                    lineTo(s * 0.84f, s * 0.16f)
                    lineTo(s * 0.84f, s * 0.68f)
                    lineTo(s * 0.50f, s * 0.68f)
                    lineTo(s * 0.32f, s * 0.88f)
                    lineTo(s * 0.32f, s * 0.68f)
                    lineTo(s * 0.16f, s * 0.68f)
                    close()
                }
                if (selected) drawPath(path, tint) else drawPath(path, tint, style = style)
                if (selected) {
                    drawCircle(Color.White.copy(alpha = 0.9f), s * 0.045f, Offset(s * 0.37f, s * 0.42f))
                    drawCircle(Color.White.copy(alpha = 0.9f), s * 0.045f, Offset(s * 0.50f, s * 0.42f))
                    drawCircle(Color.White.copy(alpha = 0.9f), s * 0.045f, Offset(s * 0.63f, s * 0.42f))
                }
            }
            TabId.EXPLORE -> {
                drawCircle(tint, radius = s * 0.40f, center = center, style = style)
                val needle = Path().apply {
                    moveTo(s * 0.60f, s * 0.34f)
                    lineTo(s * 0.55f, s * 0.55f)
                    lineTo(s * 0.34f, s * 0.60f)
                    lineTo(s * 0.45f, s * 0.45f)
                    close()
                }
                if (selected) drawPath(needle, tint)
                else drawPath(needle, tint, style = Stroke(width = stroke * 0.85f, join = StrokeJoin.Round))
            }
            TabId.FRIENDS -> {
                drawCircle(tint.copy(alpha = 0.55f), s * 0.095f, Offset(s * 0.68f, s * 0.36f))
                drawRoundRect(
                    tint.copy(alpha = 0.55f),
                    topLeft = Offset(s * 0.53f, s * 0.48f),
                    size = Size(s * 0.30f, s * 0.26f),
                    cornerRadius = CornerRadius(s * 0.13f)
                )
                drawCircle(tint, s * 0.125f, Offset(s * 0.40f, s * 0.34f))
                drawRoundRect(
                    tint,
                    topLeft = Offset(s * 0.18f, s * 0.49f),
                    size = Size(s * 0.44f, s * 0.30f),
                    cornerRadius = CornerRadius(s * 0.15f)
                )
            }
            TabId.ARCHIVE -> {
                val box = Path().apply {
                    moveTo(s * 0.17f, s * 0.34f)
                    lineTo(s * 0.83f, s * 0.34f)
                    lineTo(s * 0.80f, s * 0.84f)
                    lineTo(s * 0.20f, s * 0.84f)
                    close()
                }
                drawPath(box, tint, style = if (selected) Stroke(width = stroke) else style)
                drawLine(tint, Offset(s * 0.14f, s * 0.28f), Offset(s * 0.86f, s * 0.28f), strokeWidth = stroke, cap = StrokeCap.Round)
                drawLine(tint, Offset(s * 0.36f, s * 0.50f), Offset(s * 0.64f, s * 0.50f), strokeWidth = stroke, cap = StrokeCap.Round)
            }
            TabId.PROFILE -> {
                drawCircle(tint, s * 0.16f, Offset(s * 0.5f, s * 0.34f), style = if (selected) Stroke(width = stroke) else style)
                drawArc(
                    color = tint,
                    startAngle = 195f,
                    sweepAngle = 150f,
                    useCenter = false,
                    topLeft = Offset(s * 0.19f, s * 0.47f),
                    size = Size(s * 0.62f, s * 0.42f),
                    style = style
                )
            }
        }
    }
}

enum class Glyph {
    BOLT, JELLY, SPARK, TICKET, SHIELD, PLUG, MIC, VIDEO, SPEAKER, SPEAKER_OFF,
    PIN, REWIND, CLOCK, MEMORY, LINK, IMAGE, FILE, CAMERA, CREDIT, GIFT, GLOBE,
    SEND, LOCK, EYE, EYE_OFF, CHECK, PLUS, CLOSE, QR, TASK, CALENDAR, PHONE, BELL, SEARCH, COPY, PERSON
}

@Composable
fun GlyphIcon(
    glyph: Glyph,
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 22.dp
) {
    Canvas(modifier = modifier.size(size)) {
        drawGlyph(glyph, color)
    }
}

private fun DrawScope.drawGlyph(glyph: Glyph, c: Color) {
    val s = size.minDimension
    val sw = s * 0.085f
    val stroke = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round)
    fun p(block: Path.() -> Unit): Path = Path().apply(block)
    when (glyph) {
        Glyph.BOLT -> {
            val path = p {
                moveTo(s * 0.56f, s * 0.08f)
                lineTo(s * 0.24f, s * 0.56f)
                lineTo(s * 0.46f, s * 0.56f)
                lineTo(s * 0.40f, s * 0.92f)
                lineTo(s * 0.78f, s * 0.42f)
                lineTo(s * 0.55f, s * 0.42f)
                close()
            }
            drawPath(path, c)
        }
        Glyph.JELLY -> {
            val path = p {
                moveTo(s * 0.5f, s * 0.14f)
                cubicTo(s * 0.80f, s * 0.32f, s * 0.86f, s * 0.60f, s * 0.72f, s * 0.80f)
                cubicTo(s * 0.62f, s * 0.94f, s * 0.38f, s * 0.94f, s * 0.28f, s * 0.80f)
                cubicTo(s * 0.14f, s * 0.60f, s * 0.20f, s * 0.32f, s * 0.5f, s * 0.14f)
                close()
            }
            drawPath(path, c.copy(alpha = 0.9f))
            drawCircle(Color.White.copy(alpha = 0.5f), s * 0.055f, Offset(s * 0.38f, s * 0.42f))
        }
        Glyph.SPARK -> {
            val path = p {
                moveTo(s * 0.5f, s * 0.06f)
                quadraticBezierTo(s * 0.56f, s * 0.40f, s * 0.92f, s * 0.50f)
                quadraticBezierTo(s * 0.56f, s * 0.58f, s * 0.5f, s * 0.94f)
                quadraticBezierTo(s * 0.44f, s * 0.58f, s * 0.08f, s * 0.50f)
                quadraticBezierTo(s * 0.44f, s * 0.40f, s * 0.5f, s * 0.06f)
                close()
            }
            drawPath(path, c)
        }
        Glyph.TICKET -> {
            drawRoundRect(c, Offset(s * 0.10f, s * 0.26f), Size(s * 0.80f, s * 0.48f), CornerRadius(s * 0.10f), style = stroke)
            drawLine(c, Offset(s * 0.62f, s * 0.30f), Offset(s * 0.62f, s * 0.70f), sw * 0.7f, StrokeCap.Round)
        }
        Glyph.SHIELD -> {
            val path = p {
                moveTo(s * 0.5f, s * 0.08f)
                lineTo(s * 0.84f, s * 0.22f)
                lineTo(s * 0.84f, s * 0.50f)
                cubicTo(s * 0.84f, s * 0.72f, s * 0.68f, s * 0.86f, s * 0.5f, s * 0.94f)
                cubicTo(s * 0.32f, s * 0.86f, s * 0.16f, s * 0.72f, s * 0.16f, s * 0.50f)
                lineTo(s * 0.16f, s * 0.22f)
                close()
            }
            drawPath(path, c, style = stroke)
            drawLine(c, Offset(s * 0.36f, s * 0.50f), Offset(s * 0.47f, s * 0.62f), sw, StrokeCap.Round)
            drawLine(c, Offset(s * 0.47f, s * 0.62f), Offset(s * 0.66f, s * 0.40f), sw, StrokeCap.Round)
        }
        Glyph.PLUG -> {
            drawRoundRect(c, Offset(s * 0.28f, s * 0.38f), Size(s * 0.44f, s * 0.34f), CornerRadius(s * 0.12f), style = stroke)
            drawLine(c, Offset(s * 0.38f, s * 0.38f), Offset(s * 0.38f, s * 0.18f), sw, StrokeCap.Round)
            drawLine(c, Offset(s * 0.62f, s * 0.38f), Offset(s * 0.62f, s * 0.18f), sw, StrokeCap.Round)
            drawLine(c, Offset(s * 0.50f, s * 0.72f), Offset(s * 0.50f, s * 0.90f), sw, StrokeCap.Round)
        }
        Glyph.MIC -> {
            drawRoundRect(c, Offset(s * 0.36f, s * 0.10f), Size(s * 0.28f, s * 0.48f), CornerRadius(s * 0.14f), style = stroke)
            drawArc(c, 0f, 180f, false, Offset(s * 0.24f, s * 0.26f), Size(s * 0.52f, s * 0.42f), style = stroke)
            drawLine(c, Offset(s * 0.50f, s * 0.68f), Offset(s * 0.50f, s * 0.84f), sw, StrokeCap.Round)
            drawLine(c, Offset(s * 0.38f, s * 0.84f), Offset(s * 0.62f, s * 0.84f), sw, StrokeCap.Round)
        }
        Glyph.VIDEO -> {
            drawRoundRect(c, Offset(s * 0.10f, s * 0.28f), Size(s * 0.52f, s * 0.44f), CornerRadius(s * 0.12f), style = stroke)
            val lens = p {
                moveTo(s * 0.66f, s * 0.42f)
                lineTo(s * 0.88f, s * 0.30f)
                lineTo(s * 0.88f, s * 0.70f)
                lineTo(s * 0.66f, s * 0.58f)
                close()
            }
            drawPath(lens, c, style = Stroke(width = sw, join = StrokeJoin.Round))
        }
        Glyph.SPEAKER, Glyph.SPEAKER_OFF -> {
            val body = p {
                moveTo(s * 0.10f, s * 0.40f)
                lineTo(s * 0.32f, s * 0.40f)
                lineTo(s * 0.54f, s * 0.20f)
                lineTo(s * 0.54f, s * 0.80f)
                lineTo(s * 0.32f, s * 0.60f)
                lineTo(s * 0.10f, s * 0.60f)
                close()
            }
            drawPath(body, c, style = Stroke(width = sw, join = StrokeJoin.Round))
            drawArc(c, -55f, 110f, false, Offset(s * 0.48f, s * 0.32f), Size(s * 0.28f, s * 0.36f), style = stroke)
            drawArc(c, -50f, 100f, false, Offset(s * 0.52f, s * 0.20f), Size(s * 0.42f, s * 0.60f), style = stroke)
            if (glyph == Glyph.SPEAKER_OFF) {
                drawLine(c, Offset(s * 0.18f, s * 0.14f), Offset(s * 0.84f, s * 0.86f), sw * 1.1f, StrokeCap.Round)
            }
        }
        Glyph.PIN -> {
            drawCircle(c, s * 0.13f, Offset(s * 0.50f, s * 0.38f), style = stroke)
            drawLine(c, Offset(s * 0.50f, s * 0.52f), Offset(s * 0.50f, s * 0.88f), sw, StrokeCap.Round)
            drawLine(c, Offset(s * 0.30f, s * 0.20f), Offset(s * 0.70f, s * 0.20f), sw, StrokeCap.Round)
        }
        Glyph.REWIND -> {
            val left = p {
                moveTo(s * 0.46f, s * 0.26f)
                lineTo(s * 0.46f, s * 0.74f)
                lineTo(s * 0.18f, s * 0.50f)
                close()
            }
            val right = p {
                moveTo(s * 0.82f, s * 0.26f)
                lineTo(s * 0.82f, s * 0.74f)
                lineTo(s * 0.54f, s * 0.50f)
                close()
            }
            drawPath(left, c)
            drawPath(right, c)
        }
        Glyph.CLOCK, Glyph.CALENDAR -> {
            if (glyph == Glyph.CLOCK) {
                drawCircle(c, s * 0.38f, center, style = stroke)
                drawLine(c, Offset(s * 0.50f, s * 0.28f), Offset(s * 0.50f, s * 0.52f), sw, StrokeCap.Round)
                drawLine(c, Offset(s * 0.50f, s * 0.52f), Offset(s * 0.66f, s * 0.60f), sw, StrokeCap.Round)
            } else {
                drawRoundRect(c, Offset(s * 0.12f, s * 0.20f), Size(s * 0.76f, s * 0.66f), CornerRadius(s * 0.10f), style = stroke)
                drawLine(c, Offset(s * 0.12f, s * 0.38f), Offset(s * 0.88f, s * 0.38f), sw, StrokeCap.Round)
                drawLine(c, Offset(s * 0.34f, s * 0.12f), Offset(s * 0.34f, s * 0.28f), sw, StrokeCap.Round)
                drawLine(c, Offset(s * 0.66f, s * 0.12f), Offset(s * 0.66f, s * 0.28f), sw, StrokeCap.Round)
            }
        }
        Glyph.MEMORY -> {
            val path = p {
                moveTo(s * 0.5f, s * 0.18f)
                cubicTo(s * 0.24f, s * 0.18f, s * 0.14f, s * 0.36f, s * 0.20f, s * 0.52f)
                cubicTo(s * 0.10f, s * 0.66f, s * 0.22f, s * 0.84f, s * 0.38f, s * 0.82f)
                cubicTo(s * 0.46f, s * 0.94f, s * 0.54f, s * 0.94f, s * 0.62f, s * 0.82f)
                cubicTo(s * 0.78f, s * 0.84f, s * 0.90f, s * 0.66f, s * 0.80f, s * 0.52f)
                cubicTo(s * 0.86f, s * 0.36f, s * 0.76f, s * 0.18f, s * 0.5f, s * 0.18f)
                close()
            }
            drawPath(path, c, style = stroke)
            drawLine(c, Offset(s * 0.50f, s * 0.24f), Offset(s * 0.50f, s * 0.82f), sw * 0.8f, StrokeCap.Round)
            drawLine(c, Offset(s * 0.34f, s * 0.40f), Offset(s * 0.44f, s * 0.46f), sw * 0.8f, StrokeCap.Round)
            drawLine(c, Offset(s * 0.66f, s * 0.40f), Offset(s * 0.56f, s * 0.46f), sw * 0.8f, StrokeCap.Round)
        }
        Glyph.LINK -> {
            drawRoundRect(c, Offset(s * 0.10f, s * 0.36f), Size(s * 0.44f, s * 0.28f), CornerRadius(s * 0.14f), style = stroke)
            drawRoundRect(c, Offset(s * 0.46f, s * 0.36f), Size(s * 0.44f, s * 0.28f), CornerRadius(s * 0.14f), style = stroke)
        }
        Glyph.IMAGE -> {
            drawRoundRect(c, Offset(s * 0.10f, s * 0.16f), Size(s * 0.80f, s * 0.68f), CornerRadius(s * 0.10f), style = stroke)
            drawCircle(c, s * 0.06f, Offset(s * 0.34f, s * 0.36f))
            val path = p {
                moveTo(s * 0.14f, s * 0.78f)
                lineTo(s * 0.40f, s * 0.50f)
                lineTo(s * 0.58f, s * 0.68f)
                lineTo(s * 0.70f, s * 0.56f)
                lineTo(s * 0.86f, s * 0.76f)
            }
            drawPath(path, c, style = stroke)
        }
        Glyph.FILE -> {
            val path = p {
                moveTo(s * 0.22f, s * 0.08f)
                lineTo(s * 0.60f, s * 0.08f)
                lineTo(s * 0.80f, s * 0.28f)
                lineTo(s * 0.80f, s * 0.92f)
                lineTo(s * 0.22f, s * 0.92f)
                close()
            }
            drawPath(path, c, style = stroke)
            drawLine(c, Offset(s * 0.60f, s * 0.08f), Offset(s * 0.60f, s * 0.30f), sw, StrokeCap.Round)
            drawLine(c, Offset(s * 0.60f, s * 0.30f), Offset(s * 0.80f, s * 0.30f), sw, StrokeCap.Round)
        }
        Glyph.CAMERA -> {
            drawRoundRect(c, Offset(s * 0.08f, s * 0.28f), Size(s * 0.84f, s * 0.56f), CornerRadius(s * 0.12f), style = stroke)
            drawCircle(c, s * 0.15f, Offset(s * 0.5f, s * 0.56f), style = stroke)
            drawRoundRect(c, Offset(s * 0.34f, s * 0.18f), Size(s * 0.32f, s * 0.12f), CornerRadius(s * 0.04f), style = stroke)
        }
        Glyph.CREDIT -> {
            drawRoundRect(c, Offset(s * 0.08f, s * 0.24f), Size(s * 0.84f, s * 0.54f), CornerRadius(s * 0.10f), style = stroke)
            drawLine(c, Offset(s * 0.08f, s * 0.42f), Offset(s * 0.92f, s * 0.42f), sw * 1.4f, StrokeCap.Butt)
            drawLine(c, Offset(s * 0.22f, s * 0.62f), Offset(s * 0.44f, s * 0.62f), sw, StrokeCap.Round)
        }
        Glyph.GIFT -> {
            drawRoundRect(c, Offset(s * 0.14f, s * 0.38f), Size(s * 0.72f, s * 0.48f), CornerRadius(s * 0.06f), style = stroke)
            drawLine(c, Offset(s * 0.10f, s * 0.30f), Offset(s * 0.90f, s * 0.30f), sw * 1.3f, StrokeCap.Round)
            drawLine(c, Offset(s * 0.50f, s * 0.30f), Offset(s * 0.50f, s * 0.86f), sw, StrokeCap.Round)
        }
        Glyph.GLOBE -> {
            drawCircle(c, s * 0.38f, center, style = stroke)
            drawOval(c, Offset(s * 0.34f, s * 0.12f), Size(s * 0.32f, s * 0.76f), style = Stroke(width = sw * 0.8f))
            drawLine(c, Offset(s * 0.12f, s * 0.50f), Offset(s * 0.88f, s * 0.50f), sw * 0.8f, StrokeCap.Round)
        }
        Glyph.SEND -> {
            val path = p {
                moveTo(s * 0.12f, s * 0.46f)
                lineTo(s * 0.88f, s * 0.14f)
                lineTo(s * 0.60f, s * 0.88f)
                lineTo(s * 0.46f, s * 0.58f)
                close()
            }
            drawPath(path, c, style = Stroke(width = sw, join = StrokeJoin.Round))
        }
        Glyph.LOCK -> {
            drawRoundRect(c, Offset(s * 0.18f, s * 0.44f), Size(s * 0.64f, s * 0.46f), CornerRadius(s * 0.10f), style = stroke)
            drawArc(c, 180f, 180f, false, Offset(s * 0.30f, s * 0.16f), Size(s * 0.40f, s * 0.52f), style = stroke)
        }
        Glyph.EYE, Glyph.EYE_OFF -> {
            val eye = p {
                moveTo(s * 0.08f, s * 0.50f)
                quadraticBezierTo(s * 0.50f, s * 0.12f, s * 0.92f, s * 0.50f)
                quadraticBezierTo(s * 0.50f, s * 0.88f, s * 0.08f, s * 0.50f)
                close()
            }
            drawPath(eye, c, style = stroke)
            drawCircle(c, s * 0.13f, Offset(s * 0.5f, s * 0.5f), style = stroke)
            if (glyph == Glyph.EYE_OFF) drawLine(c, Offset(s * 0.14f, s * 0.14f), Offset(s * 0.86f, s * 0.86f), sw, StrokeCap.Round)
        }
        Glyph.CHECK -> {
            drawLine(c, Offset(s * 0.18f, s * 0.52f), Offset(s * 0.42f, s * 0.74f), sw * 1.2f, StrokeCap.Round)
            drawLine(c, Offset(s * 0.42f, s * 0.74f), Offset(s * 0.84f, s * 0.26f), sw * 1.2f, StrokeCap.Round)
        }
        Glyph.PLUS -> {
            drawLine(c, Offset(s * 0.5f, s * 0.16f), Offset(s * 0.5f, s * 0.84f), sw, StrokeCap.Round)
            drawLine(c, Offset(s * 0.16f, s * 0.5f), Offset(s * 0.84f, s * 0.5f), sw, StrokeCap.Round)
        }
        Glyph.CLOSE -> {
            drawLine(c, Offset(s * 0.20f, s * 0.20f), Offset(s * 0.80f, s * 0.80f), sw, StrokeCap.Round)
            drawLine(c, Offset(s * 0.80f, s * 0.20f), Offset(s * 0.20f, s * 0.80f), sw, StrokeCap.Round)
        }
        Glyph.QR -> {
            drawRoundRect(c, Offset(s * 0.10f, s * 0.10f), Size(s * 0.28f, s * 0.28f), CornerRadius(s * 0.03f), style = stroke)
            drawRoundRect(c, Offset(s * 0.62f, s * 0.10f), Size(s * 0.28f, s * 0.28f), CornerRadius(s * 0.03f), style = stroke)
            drawRoundRect(c, Offset(s * 0.10f, s * 0.62f), Size(s * 0.28f, s * 0.28f), CornerRadius(s * 0.03f), style = stroke)
            drawCircle(c, s * 0.035f, Offset(s * 0.24f, s * 0.24f))
            drawCircle(c, s * 0.035f, Offset(s * 0.76f, s * 0.24f))
            drawCircle(c, s * 0.035f, Offset(s * 0.24f, s * 0.76f))
            drawCircle(c, s * 0.05f, Offset(s * 0.72f, s * 0.72f))
        }
        Glyph.TASK -> {
            drawRoundRect(c, Offset(s * 0.08f, s * 0.12f), Size(s * 0.84f, s * 0.76f), CornerRadius(s * 0.14f), style = stroke)
            drawLine(c, Offset(s * 0.26f, s * 0.48f), Offset(s * 0.44f, s * 0.66f), sw, StrokeCap.Round)
            drawLine(c, Offset(s * 0.44f, s * 0.66f), Offset(s * 0.72f, s * 0.32f), sw, StrokeCap.Round)
        }
        Glyph.PHONE -> {
            val path = p {
                moveTo(s * 0.25f, s * 0.12f)
                lineTo(s * 0.42f, s * 0.30f)
                lineTo(s * 0.32f, s * 0.46f)
                cubicTo(s * 0.42f, s * 0.62f, s * 0.56f, s * 0.72f, s * 0.72f, s * 0.76f)
                lineTo(s * 0.82f, s * 0.60f)
                lineTo(s * 0.92f, s * 0.78f)
                lineTo(s * 0.82f, s * 0.90f)
                cubicTo(s * 0.52f, s * 0.90f, s * 0.18f, s * 0.60f, s * 0.12f, s * 0.26f)
                close()
            }
            drawPath(path, c)
        }
        Glyph.BELL -> {
            val bell = p {
                moveTo(s * 0.20f, s * 0.68f)
                lineTo(s * 0.28f, s * 0.56f)
                lineTo(s * 0.28f, s * 0.40f)
                cubicTo(s * 0.28f, s * 0.24f, s * 0.38f, s * 0.14f, s * 0.50f, s * 0.14f)
                cubicTo(s * 0.62f, s * 0.14f, s * 0.72f, s * 0.24f, s * 0.72f, s * 0.40f)
                lineTo(s * 0.72f, s * 0.56f)
                lineTo(s * 0.80f, s * 0.68f)
                close()
            }
            drawPath(bell, c, style = Stroke(width = sw, join = StrokeJoin.Round))
            drawLine(c, Offset(s * 0.42f, s * 0.80f), Offset(s * 0.58f, s * 0.80f), sw, StrokeCap.Round)
        }
        Glyph.SEARCH -> {
            drawCircle(c, s * 0.27f, Offset(s * 0.44f, s * 0.42f), style = stroke)
            drawLine(c, Offset(s * 0.64f, s * 0.62f), Offset(s * 0.86f, s * 0.84f), sw * 1.2f, StrokeCap.Round)
        }
        Glyph.COPY -> {
            drawRoundRect(c, Offset(s * 0.30f, s * 0.12f), Size(s * 0.52f, s * 0.58f), CornerRadius(s * 0.08f), style = stroke)
            drawRoundRect(c, Offset(s * 0.16f, s * 0.30f), Size(s * 0.52f, s * 0.58f), CornerRadius(s * 0.08f), style = stroke)
        }
        Glyph.PERSON -> {
            drawCircle(c, s * 0.16f, Offset(s * 0.5f, s * 0.33f), style = stroke)
            drawArc(c, 195f, 150f, false, Offset(s * 0.20f, s * 0.48f), Size(s * 0.60f, s * 0.40f), style = stroke)
        }
    }
}



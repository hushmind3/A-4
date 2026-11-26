@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.dominion.mobile.ui.components

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dominion.mobile.data.model.Character
import com.dominion.mobile.data.model.Lang
import com.dominion.mobile.ui.LocalLang
import com.dominion.mobile.ui.theme.LocalDominionPalette
import com.dominion.mobile.ui.tr
import kotlin.math.roundToInt

@Composable
fun DominionTopBar(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    centerTitle: Boolean = false,
    actions: @Composable RowScope.() -> Unit = {}
) {
    val p = LocalDominionPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onBack != null) {
            CircleButton(icon = Icons.Filled.ArrowBack, onClick = onBack, contentDescription = "Back")
        } else {
            Spacer(Modifier.width(8.dp))
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 6.dp),
            horizontalAlignment = if (centerTitle) Alignment.CenterHorizontally else Alignment.Start
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = p.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = p.textTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        actions()
    }
}

@Composable
fun CircleButton(
    icon: ImageVector,
    onClick: () -> Unit,
    contentDescription: String? = null,
    tint: Color = LocalDominionPalette.current.textPrimary,
    background: Color = Color.Transparent,
    size: Dp = 40.dp
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(background)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription, tint = tint, modifier = Modifier.size(21.dp))
    }
}

@Composable
fun GlyphButton(
    glyph: Glyph,
    onClick: () -> Unit,
    contentDescription: String? = null,
    tint: Color = LocalDominionPalette.current.textPrimary,
    background: Color = Color.Transparent,
    size: Dp = 40.dp
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(background)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        GlyphIcon(glyph, tint, size = size * 0.52f)
    }
}

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false
) {
    val p = LocalDominionPalette.current
    val bg = if (enabled) p.actionPrimary else p.control
    val fg = if (enabled) p.onActionPrimary else p.textDisabled
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(54.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(bg)
            .clickable(enabled = enabled && !loading, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (loading) {
            LoadingDots(color = fg)
        } else {
            Text(text, color = fg, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val p = LocalDominionPalette.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(50.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(p.raised)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = p.textPrimary, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun DominionCard(
    modifier: Modifier = Modifier,
    color: Color = LocalDominionPalette.current.card,
    content: @Composable ColumnScope.() -> Unit
) {
    val p = LocalDominionPalette.current
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .shadow(10.dp, RoundedCornerShape(20.dp), clip = false,
                ambientColor = Color.Black.copy(alpha = 0.04f),
                spotColor = Color.Black.copy(alpha = 0.06f)),
        shape = RoundedCornerShape(20.dp),
        color = color,
        border = androidx.compose.foundation.BorderStroke(0.5.dp, p.hairline)
    ) {
        Column(content = content)
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier, action: String? = null, onAction: (() -> Unit)? = null) {
    val p = LocalDominionPalette.current
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text, style = MaterialTheme.typography.labelMedium, color = p.textSecondary)
        Spacer(Modifier.weight(1f))
        if (action != null && onAction != null) {
            Text(
                action,
                style = MaterialTheme.typography.labelSmall,
                color = p.blue,
                modifier = Modifier.clickable(onClick = onAction).padding(4.dp)
            )
        }
    }
}

@Composable
fun SettingsRow(
    title: String,
    subtitle: String? = null,
    leading: (@Composable () -> Unit)? = null,
    trailingText: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    showChevron: Boolean = true,
    danger: Boolean = false,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null
) {
    val p = LocalDominionPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled && onClick != null) { onClick?.invoke() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (leading != null) {
            Box(Modifier.width(38.dp), contentAlignment = Alignment.CenterStart) { leading() }
        }
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                color = if (danger) p.danger else if (enabled) p.textPrimary else p.textDisabled
            )
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = p.textTertiary)
            }
        }
        if (trailingText != null) {
            Text(trailingText, style = MaterialTheme.typography.bodyMedium, color = p.textTertiary)
            Spacer(Modifier.width(4.dp))
        }
        if (trailing != null) trailing()
        if (showChevron && onClick != null) {
            Icon(Icons.Filled.KeyboardArrowRight, null, tint = p.textTertiary, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
fun dominionSwitchColors() = SwitchDefaults.colors(
    checkedThumbColor = Color.White,
    checkedTrackColor = LocalDominionPalette.current.toggleOn,
    uncheckedThumbColor = if (LocalDominionPalette.current.dark) Color(0xFFB6BAC4) else Color.White,
    uncheckedTrackColor = if (LocalDominionPalette.current.dark) Color(0xFF3A3E48) else Color(0xFFEBEBEB),
    uncheckedBorderColor = Color.Transparent
)

@Composable
fun SwitchRow(
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    leading: (@Composable () -> Unit)? = null
) {
    val p = LocalDominionPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (leading != null) {
            Box(Modifier.width(38.dp), contentAlignment = Alignment.CenterStart) { leading() }
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = p.textPrimary)
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = p.textTertiary)
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = dominionSwitchColors()
        )
    }
}

@Composable
fun Hairline(modifier: Modifier = Modifier, start: Dp = 16.dp) {
    val p = LocalDominionPalette.current
    Box(
        modifier
            .fillMaxWidth()
            .padding(start = start)
            .height(0.5.dp)
            .background(p.hairline)
    )
}

@Composable
fun DominionTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    label: String? = null,
    singleLine: Boolean = true,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Done,
    onImeAction: (() -> Unit)? = null,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    isPassword: Boolean = false,
    maxLines: Int = if (singleLine) 1 else 5
) {
    val p = LocalDominionPalette.current
    Column(modifier = modifier) {
        if (label != null) {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = p.textTertiary,
                modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
            )
        }
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { if (placeholder.isNotEmpty()) Text(placeholder, color = p.textTertiary, style = MaterialTheme.typography.bodyLarge) },
            label = null,
            singleLine = singleLine,
            maxLines = maxLines,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = p.textPrimary),
            shape = RoundedCornerShape(14.dp),
            visualTransformation = if (isPassword) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
            keyboardActions = KeyboardActions(onDone = { onImeAction?.invoke() }, onSend = { onImeAction?.invoke() }),
            leadingIcon = leadingIcon,
            trailingIcon = trailingIcon,
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = p.input,
                unfocusedContainerColor = p.input,
                focusedBorderColor = p.textPrimary.copy(alpha = 0.22f),
                unfocusedBorderColor = Color.Transparent,
                cursorColor = p.textPrimary
            )
        )
    }
}

@Composable
fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier
) {
    val p = LocalDominionPalette.current
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        placeholder = { Text(placeholder, color = p.textTertiary, style = MaterialTheme.typography.bodyMedium) },
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyMedium.copy(color = p.textPrimary),
        shape = RoundedCornerShape(14.dp),
        leadingIcon = { Icon(Icons.Filled.Search, null, tint = p.textTertiary, modifier = Modifier.size(19.dp)) },
        trailingIcon = if (value.isNotEmpty()) {
            {
                IconButton(onClick = { onValueChange("") }) {
                    Icon(Icons.Filled.Close, null, tint = p.textTertiary, modifier = Modifier.size(18.dp))
                }
            }
        } else null,
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = p.input,
            unfocusedContainerColor = p.input,
            focusedBorderColor = Color.Transparent,
            unfocusedBorderColor = Color.Transparent,
            cursorColor = p.textPrimary
        )
    )
}

@Composable
fun DominionChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    leading: (@Composable () -> Unit)? = null
) {
    val p = LocalDominionPalette.current
    Row(
        modifier = Modifier
            .height(34.dp)
            .clip(RoundedCornerShape(17.dp))
            .background(if (selected) p.actionPrimary else p.raised)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        leading?.invoke()
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) p.onActionPrimary else p.textSecondary
        )
    }
}

@Composable
fun ChipScroller(
    items: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        itemsIndexed(items) { index, item ->
            DominionChip(item, index == selectedIndex, onClick = { onSelect(index) })
        }
    }
}

@Composable
fun UnreadBadge(count: Int) {
    val p = LocalDominionPalette.current
    if (count <= 0) return
    Box(
        modifier = Modifier
            .height(20.dp)
            .clip(CircleShape)
            .background(p.blue)
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = if (count > 99) "99+" else count.toString(),
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun CountPill(count: Int, color: Color = LocalDominionPalette.current.blue) {
    Box(
        modifier = Modifier
            .height(20.dp)
            .clip(CircleShape)
            .background(color)
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(if (count > 99) "99+" else count.toString(), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun LoadingDots(color: Color = LocalDominionPalette.current.textTertiary, dotSize: Dp = 5.dp) {
    val transition = rememberInfiniteTransition(label = "dots")
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(3) { i ->
            val alpha by transition.animateFloat(
                initialValue = 0.25f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(600, delayMillis = i * 140),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "dot$i"
            )
            Box(
                Modifier
                    .size(dotSize)
                    .clip(CircleShape)
                    .background(color.copy(alpha = alpha))
            )
        }
    }
}

@Composable
fun EmptyState(
    glyph: Glyph,
    title: String,
    body: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val p = LocalDominionPalette.current
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier
                .size(68.dp)
                .clip(CircleShape)
                .background(p.raised),
            contentAlignment = Alignment.Center
        ) {
            GlyphIcon(glyph, p.textTertiary, size = 30.dp)
        }
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, color = p.textPrimary, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = p.textTertiary, textAlign = TextAlign.Center)
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(18.dp))
            Box(
                Modifier
                    .clip(RoundedCornerShape(22.dp))
                    .background(p.actionPrimary)
                    .clickable(onClick = onAction)
                    .padding(horizontal = 20.dp, vertical = 11.dp)
            ) {
                Text(actionLabel, color = p.onActionPrimary, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
fun PrimaryDialog(
    title: String,
    body: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    dismissLabel: String,
    onDismiss: () -> Unit,
    destructive: Boolean = false
) {
    val p = LocalDominionPalette.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = p.sheet,
        title = { Text(title, style = MaterialTheme.typography.titleMedium, color = p.textPrimary) },
        text = { Text(body, style = MaterialTheme.typography.bodyMedium, color = p.textSecondary) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmLabel, color = if (destructive) p.danger else p.blue, style = MaterialTheme.typography.labelLarge)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(dismissLabel, color = p.textSecondary, style = MaterialTheme.typography.labelLarge)
            }
        }
    )
}

@Composable
fun TextPromptDialog(
    title: String,
    label: String,
    initial: String = "",
    placeholder: String = "",
    confirmLabel: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    singleLine: Boolean = true
) {
    val p = LocalDominionPalette.current
    var value by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = p.sheet,
        title = { Text(title, style = MaterialTheme.typography.titleMedium, color = p.textPrimary) },
        text = {
            DominionTextField(
                value = value,
                onValueChange = { value = it },
                label = label,
                placeholder = placeholder,
                singleLine = singleLine,
                maxLines = if (singleLine) 1 else 4
            )
        },
        confirmButton = {
            TextButton(onClick = { if (value.isNotBlank()) onConfirm(value.trim()) }) {
                Text(confirmLabel, color = if (value.isNotBlank()) p.blue else p.textDisabled, style = MaterialTheme.typography.labelLarge)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(tr(LocalLang.current, "취소", "Cancel"), color = p.textSecondary) }
        }
    )
}

@Composable
fun DominionBottomSheet(
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    val p = LocalDominionPalette.current
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = state,
        containerColor = p.sheet,
        contentColor = p.textPrimary,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        dragHandle = {
            Box(Modifier.padding(vertical = 10.dp).size(width = 36.dp, height = 4.dp).clip(CircleShape).background(p.border))
        }
    ) {
        Column(Modifier.padding(bottom = 28.dp), content = content)
    }
}

@Composable
fun SheetTitle(title: String, subtitle: String? = null) {
    val p = LocalDominionPalette.current
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = p.textPrimary)
        if (subtitle != null) {
            Spacer(Modifier.height(4.dp))
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = p.textTertiary)
        }
    }
}

@Composable
fun SheetRow(
    label: String,
    glyph: Glyph? = null,
    tint: Color = LocalDominionPalette.current.textPrimary,
    danger: Boolean = false,
    onClick: () -> Unit
) {
    val p = LocalDominionPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (glyph != null) {
            GlyphIcon(glyph, if (danger) p.danger else tint, size = 20.dp)
            Spacer(Modifier.width(14.dp))
        }
        Text(label, style = MaterialTheme.typography.bodyLarge, color = if (danger) p.danger else p.textPrimary)
    }
}

@Composable
fun FriendAvatar(name: String, color: Color, size: Dp = 48.dp) {
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(Color(color.value).copy(alpha = 0.18f))
            .border(2.dp, Color(color.value).copy(alpha = 0.28f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = name.take(1),
            color = Color(color.value),
            fontSize = (size.value * 0.38f).sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
fun CharacterRow(
    character: Character,
    subtitle: String? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
    onClick: (() -> Unit)? = null
) {
    val p = LocalDominionPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AssistantAvatar(color = Color(character.color), shape = character.shape, size = 48.dp, level = null)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(character.name, style = MaterialTheme.typography.titleSmall, color = p.textPrimary)
            Text(
                subtitle ?: character.tagline,
                style = MaterialTheme.typography.bodySmall,
                color = p.textTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        trailing?.invoke(this)
    }
}

@Composable
fun LevelBar(level: Int, xp: Int, goal: Int, modifier: Modifier = Modifier) {
    val p = LocalDominionPalette.current
    val ratio = (xp.toFloat() / goal.coerceAtLeast(1)).coerceIn(0f, 1f)
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(tr(LocalLang.current, "Lv.$level", "Lv.$level"), style = MaterialTheme.typography.labelSmall, color = p.textSecondary)
            Spacer(Modifier.weight(1f))
            Text("$xp / $goal", style = MaterialTheme.typography.labelSmall, color = p.textTertiary)
        }
        Spacer(Modifier.height(6.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(CircleShape)
                .background(p.control)
        ) {
            Box(
                Modifier
                    .fillMaxWidth(ratio)
                    .height(6.dp)
                    .clip(CircleShape)
                    .background(p.blue)
            )
        }
    }
}

@Composable
fun BalanceTile(
    glyph: Glyph,
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val p = LocalDominionPalette.current
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(p.cardSoft)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(accent.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            GlyphIcon(glyph, accent, size = 17.dp)
        }
        Spacer(Modifier.width(9.dp))
        Column {
            Text(label, style = MaterialTheme.typography.bodySmall, color = p.textTertiary)
            Text(value, style = MaterialTheme.typography.titleSmall, color = p.textPrimary)
        }
    }
}

@Composable
fun SettingGlyph(glyph: Glyph, tint: Color = LocalDominionPalette.current.textPrimary) {
    Box(
        Modifier
            .size(30.dp)
            .clip(CircleShape)
            .background(tint.copy(alpha = 0.08f)),
        contentAlignment = Alignment.Center
    ) {
        GlyphIcon(glyph, tint, size = 17.dp)
    }
}

@Composable
fun CheckCircle(checked: Boolean, onClick: (() -> Unit)? = null) {
    val p = LocalDominionPalette.current
    Box(
        Modifier
            .size(24.dp)
            .clip(CircleShape)
            .background(if (checked) p.actionPrimary else Color.Transparent)
            .border(1.5.dp, if (checked) p.actionPrimary else p.border, CircleShape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center
    ) {
        if (checked) Icon(Icons.Filled.Check, null, tint = p.onActionPrimary, modifier = Modifier.size(14.dp))
    }
}

@Composable
fun ProgressRing(progress: Float, size: Dp = 44.dp, stroke: Dp = 5.dp, color: Color = LocalDominionPalette.current.blue) {
    val p = LocalDominionPalette.current
    androidx.compose.foundation.Canvas(Modifier.size(size)) {
        drawCircle(p.control, radius = (this.size.minDimension - stroke.toPx()) / 2, style = androidx.compose.ui.graphics.drawscope.Stroke(stroke.toPx()))
        drawArc(
            color = color,
            startAngle = -90f,
            sweepAngle = 360f * progress.coerceIn(0f, 1f),
            useCenter = false,
            style = androidx.compose.ui.graphics.drawscope.Stroke(stroke.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
        )
    }
}

fun Context.toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

fun percent(a: Int, b: Int): Int = if (b <= 0) 0 else ((a.toFloat() / b) * 100).roundToInt()

@Composable
fun TruncatedText(
    text: String,
    style: androidx.compose.ui.text.TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    maxLines: Int = 1
) {
    Text(
        text = text,
        style = style,
        color = color,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
    )
}

@Composable
fun LanguageToggle(current: Lang, onChange: (Lang) -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(LocalDominionPalette.current.raised)
            .padding(3.dp)
    ) {
        listOf(Lang.KO to "한국어", Lang.EN to "English").forEach { (lang, label) ->
            val selected = current == lang
            Box(
                Modifier
                    .clip(RoundedCornerShape(17.dp))
                    .background(if (selected) LocalDominionPalette.current.actionPrimary else Color.Transparent)
                    .clickable { onChange(lang) }
                    .padding(horizontal = 14.dp, vertical = 7.dp)
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (selected) LocalDominionPalette.current.onActionPrimary else LocalDominionPalette.current.textSecondary
                )
            }
        }
    }
}

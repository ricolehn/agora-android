package org.agora.app.ui.components

import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownTypography
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.agora.app.R
import org.agora.app.ui.theme.Agora

// Building blocks that reproduce the PWA's look (assets/style.css) on top of Material 3.

/** Brand name in the cyan → emerald gradient (`.header-left h1`, `.login-brand h2`). */
@Composable
fun BrandName(text: String, modifier: Modifier = Modifier, fontSize: androidx.compose.ui.unit.TextUnit = 27.sp) {
    Text(
        text,
        modifier = modifier,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        style = TextStyle(brush = Agora.colors.brandGradient, fontSize = fontSize, fontWeight = FontWeight.Black, letterSpacing = (-0.5).sp)
    )
}

/** Page heading like "Termine & Events" (`.page-title`, weight 800). */
@Composable
fun PageTitle(text: String, modifier: Modifier = Modifier, trailing: @Composable RowScope.() -> Unit = {}) {
    Row(modifier.fillMaxWidth().padding(top = 4.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.headlineSmall, color = Agora.colors.heading, modifier = Modifier.weight(1f))
        trailing()
    }
}

/** Small uppercase label ("OFFENER BETRAG", "TERMIN", "WO / ADRESSE"). */
@Composable
fun CapsLabel(text: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Text(
        text.uppercase(),
        modifier = modifier,
        color = color,
        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = 0.8.sp),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

/** Segmented pill tabs (`.pill-tabs`): grey track, white raised thumb. */
@Composable
fun PillTabs(options: List<Pair<String, ImageVector?>>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(CircleShape)
            .background(Agora.colors.tabTrack)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f), CircleShape)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        options.forEachIndexed { i, (label, icon) ->
            val active = i == selected
            val color by animateColorAsState(if (active) Agora.colors.heading else MaterialTheme.colorScheme.onSurfaceVariant, label = "tab")
            Row(
                Modifier
                    .weight(1f)
                    .then(if (active) Modifier.softShadow(Color.Black.copy(alpha = 0.14f), blur = 4.dp, shape = CircleShape, offsetY = 1.dp) else Modifier)
                    .clip(CircleShape)
                    .background(if (active) Agora.colors.tabThumb else Color.Transparent)
                    .clickable(role = Role.Tab) { onSelect(i) }
                    .padding(horizontal = 10.dp, vertical = 7.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (icon != null) {
                    Icon(icon, null, Modifier.size(17.dp), tint = color)
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    label, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = if (active) FontWeight.Bold else FontWeight.SemiBold)
                )
            }
        }
    }
}

private val ButtonShape = RoundedCornerShape(12.dp)

/** `.btn-primary`: gradient, white bold text, soft shadow; `danger` makes it solid red (confirm delete). */
@Composable
fun PrimaryButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    danger: Boolean = false,
    shape: Shape = ButtonShape,
    contentPadding: PaddingValues = PaddingValues(horizontal = 18.dp, vertical = 9.dp),
    content: @Composable RowScope.() -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val glow = if (danger) Agora.colors.danger else Color(0xFF06B6D4)
    Box(
        modifier
            .defaultMinSize(minHeight = 42.dp)
            .scale(if (pressed) 0.97f else 1f)
            .alpha(if (enabled) 1f else 0.5f)
            .softShadow(glow.copy(alpha = if (enabled) 0.30f else 0f), blur = 10.dp, shape = shape, offsetY = 3.dp)
            .clip(shape)
            .then(if (danger) Modifier.background(Agora.colors.danger) else Modifier.background(Agora.colors.brandGradient))
            .clickable(interaction, androidx.compose.material3.ripple(color = Color.White), enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(contentPadding),
        contentAlignment = Alignment.Center
    ) {
        CompositionLocalProvider(LocalContentColor provides Color.White) {
            ProvideTextStyle(MaterialTheme.typography.labelLarge.copy(color = Color.White)) {
                Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically, content = content)
            }
        }
    }
}

enum class SecondaryStyle { Neutral, Danger, Success }

/** `.btn-secondary` / `.btn-danger`: flat surface with a thin border. */
@Composable
fun SecondaryButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    style: SecondaryStyle = SecondaryStyle.Neutral,
    shape: Shape = ButtonShape,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
    content: @Composable RowScope.() -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val (container, contentColor, border) = when (style) {
        SecondaryStyle.Neutral -> Triple(scheme.surface, Agora.colors.heading, scheme.outline)
        SecondaryStyle.Danger -> Triple(Agora.colors.danger.copy(alpha = 0.08f), Agora.colors.danger, Agora.colors.danger.copy(alpha = 0.3f))
        SecondaryStyle.Success -> Triple(Agora.colors.success.copy(alpha = 0.1f), Agora.colors.success, Agora.colors.success.copy(alpha = 0.35f))
    }
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = shape,
        color = container,
        contentColor = contentColor,
        border = BorderStroke(1.dp, border),
        interactionSource = interaction,
        modifier = modifier.defaultMinSize(minHeight = 40.dp).scale(if (pressed) 0.97f else 1f).alpha(if (enabled) 1f else 0.5f)
    ) {
        ProvideTextStyle(MaterialTheme.typography.labelLarge.copy(color = contentColor)) {
            Row(Modifier.padding(contentPadding), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically, content = content)
        }
    }
}

/** Label + optional leading icon for buttons, spaced like the PWA's `gap: 8px`. */
@Composable
fun ButtonLabel(text: String, icon: ImageVector? = null) {
    if (icon != null) {
        Icon(icon, null, Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
    }
    Text(text, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

/**
 * Chip on top of a cover image, like the PWA's frosted badges (`.has-hero-image .event-detail-type-badge`):
 * translucent slate with a light rim; highlights in amber. (Compose can't blur what lies behind a view,
 * so this is the PWA's look without backdrop blur.)
 */
@Composable
fun GlassChip(text: String, modifier: Modifier = Modifier, icon: ImageVector? = null, highlight: Boolean = false) {
    val shape = CircleShape
    Row(
        modifier
            .softShadow(Color.Black.copy(alpha = 0.30f), blur = 8.dp, shape = shape, offsetY = 2.dp)
            .clip(shape)
            .background(if (highlight) Color(0xBFB45309) else Color(0xA60F172A))
            .border(1.dp, if (highlight) Color(0x80FDE047) else Color.White.copy(alpha = 0.3f), shape)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val color = if (highlight) Color(0xFFFEF08A) else Color.White
        if (icon != null) {
            Icon(icon, null, Modifier.size(13.dp), tint = if (highlight) color else Color(0xFF38BDF8))
            Spacer(Modifier.width(5.dp))
        }
        Text(
            text.uppercase(), color = color, maxLines = 1, overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = 0.6.sp)
        )
    }
}

/** Markdown text sized like the PWA's rendered descriptions (h1 ≈ 1.15rem instead of Material's display size). */
@Composable
fun AgoraMarkdown(content: String, modifier: Modifier = Modifier) {
    val t = MaterialTheme.typography
    val heading = Agora.colors.heading
    Markdown(
        content,
        modifier = modifier,
        typography = markdownTypography(
            h1 = t.titleLarge.copy(color = heading),
            h2 = t.titleMedium.copy(fontSize = 17.sp, color = heading),
            h3 = t.titleMedium.copy(color = heading),
            h4 = t.titleSmall.copy(color = heading),
            h5 = t.titleSmall.copy(color = heading),
            h6 = t.titleSmall.copy(color = heading),
            text = t.bodyLarge,
            paragraph = t.bodyLarge
        )
    )
}

/** Stretches a list item over the list's horizontal content padding (full-bleed cover image). */
fun Modifier.fullBleed(horizontal: Dp): Modifier = layout { measurable, constraints ->
    val extra = (horizontal * 2).roundToPx()
    val width = constraints.maxWidth + extra
    val placeable = measurable.measure(constraints.copy(minWidth = width, maxWidth = width))
    layout(constraints.maxWidth, placeable.height) { placeable.place(-horizontal.roundToPx(), 0) }
}

/** Round frosted back button over a cover image. */
@Composable
fun GlassBackButton(onBack: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(40.dp)
            .softShadow(Color.Black.copy(alpha = 0.30f), blur = 8.dp, shape = CircleShape, offsetY = 2.dp)
            .clip(CircleShape)
            .background(Color(0xA60F172A))
            .border(1.dp, Color.White.copy(alpha = 0.3f), CircleShape)
            .clickable(role = Role.Button, onClick = onBack),
        contentAlignment = Alignment.Center
    ) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.back), Modifier.size(21.dp), tint = Color.White) }
}

/** Round gradient send button of the chat inputs (`.chat-send-btn`). */
@Composable
fun SendButton(enabled: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .size(44.dp)
            .alpha(if (enabled) 1f else 0.5f)
            .softShadow(Color(0xFF06B6D4).copy(alpha = if (enabled) 0.32f else 0f), blur = 10.dp, shape = CircleShape, offsetY = 3.dp)
            .clip(CircleShape)
            .background(Agora.colors.heroGradient)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Icon(Icons.AutoMirrored.Outlined.Send, stringResource(R.string.send), Modifier.size(22.dp), tint = Color.White) }
}

/** Pill-shaped search input (`.events-search-input`). */
@Composable
fun SearchField(value: String, onValueChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = scheme.onSurface),
        cursorBrush = SolidColor(scheme.primary),
        keyboardOptions = KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search),
        keyboardActions = KeyboardActions(),
        modifier = modifier.fillMaxWidth(),
        decorationBox = { inner ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(CircleShape)
                    .background(Agora.colors.surfaceAlt)
                    .border(1.dp, scheme.outlineVariant, CircleShape)
                    .padding(horizontal = 16.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Outlined.Search, null, Modifier.size(20.dp), tint = scheme.onSurfaceVariant)
                Spacer(Modifier.width(10.dp))
                Box(Modifier.weight(1f)) {
                    if (value.isEmpty()) Text(placeholder, style = MaterialTheme.typography.bodyLarge, color = scheme.onSurfaceVariant.copy(alpha = 0.8f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    inner()
                }
                if (value.isNotEmpty()) Icon(
                    Icons.Outlined.Close, stringResource(R.string.clear),
                    Modifier.size(20.dp).clip(CircleShape).clickable { onValueChange("") }, tint = scheme.onSurfaceVariant
                )
            }
        }
    )
}

/** Input colours of the PWA: light filled field, 14px radius, cyan focus ring. */
@Composable
fun agoraFieldColors(): TextFieldColors = OutlinedTextFieldDefaults.colors(
    unfocusedContainerColor = Agora.colors.surfaceAlt,
    focusedContainerColor = MaterialTheme.colorScheme.surface,
    disabledContainerColor = Agora.colors.surfaceAlt,
    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
    focusedBorderColor = MaterialTheme.colorScheme.primary,
    unfocusedLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
    focusedLeadingIconColor = MaterialTheme.colorScheme.primary
)

val FieldShape = RoundedCornerShape(14.dp)

/** [OutlinedTextField] in the PWA style; same parameters as the Material one. */
@Composable
fun AgoraTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    textStyle: TextStyle = LocalTextStyle.current,
    label: @Composable (() -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    prefix: @Composable (() -> Unit)? = null,
    suffix: @Composable (() -> Unit)? = null,
    supportingText: @Composable (() -> Unit)? = null,
    isError: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = false,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    minLines: Int = 1,
    interactionSource: MutableInteractionSource? = null,
    shape: Shape = FieldShape
) = OutlinedTextField(
    value = value, onValueChange = onValueChange, modifier = modifier, enabled = enabled, readOnly = readOnly, textStyle = textStyle,
    label = label, placeholder = placeholder, leadingIcon = leadingIcon, trailingIcon = trailingIcon, prefix = prefix, suffix = suffix,
    supportingText = supportingText, isError = isError, visualTransformation = visualTransformation, keyboardOptions = keyboardOptions,
    keyboardActions = keyboardActions, singleLine = singleLine, maxLines = maxLines, minLines = minLines,
    interactionSource = interactionSource, shape = shape, colors = agoraFieldColors()
)

/** Toggle with the PWA's gradient-coloured "on" state. */
@Composable
fun AgoraSwitch(checked: Boolean, onCheckedChange: ((Boolean) -> Unit)?, modifier: Modifier = Modifier, enabled: Boolean = true) =
    Switch(
        checked, onCheckedChange, modifier, enabled = enabled,
        colors = SwitchDefaults.colors(
            checkedTrackColor = Color(0xFF0FB6A6),
            checkedThumbColor = Color.White,
            checkedBorderColor = Color.Transparent,
            uncheckedTrackColor = MaterialTheme.colorScheme.outlineVariant,
            uncheckedThumbColor = Color.White,
            uncheckedBorderColor = Color.Transparent
        )
    )

/** Rounded tinted icon tile used in detail rows (`.detail-info-icon`). */
@Composable
fun IconTile(icon: ImageVector, tint: Color, modifier: Modifier = Modifier, size: Dp = 36.dp) {
    Box(
        modifier.size(size).clip(RoundedCornerShape(12.dp)).background(tint.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center
    ) { Icon(icon, null, Modifier.size(size * 0.5f), tint = tint) }
}

/** Info tile with icon, caps label and value (event detail "TERMIN", "WO / ADRESSE"). */
@Composable
fun InfoTile(icon: ImageVector, tint: Color, label: String, value: String, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, trailing: @Composable () -> Unit = {}) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(FieldShape)
            .background(Agora.colors.surfaceAlt)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, FieldShape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconTile(icon, tint)
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            CapsLabel(label)
            Text(value, style = MaterialTheme.typography.titleSmall, color = Agora.colors.heading)
        }
        trailing()
    }
}

/** Header of the main screens: logo + gradient app name, avatar on the right (`.app-header`). */
@Composable
fun AgoraHeader(appName: String, logo: @Composable () -> Unit, trailing: @Composable RowScope.() -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .padding(start = 20.dp, end = 12.dp, top = 8.dp, bottom = 4.dp)
            .heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        logo()
        // The box takes the free width; the text keeps its own width so the gradient spans just the name
        Box(Modifier.weight(1f).padding(start = 12.dp)) { BrandName(appName) }
        trailing()
    }
}

/** Top bar of sub pages: round back button like the PWA's modal close button, heavy title. */
@Composable
fun SubPageHeader(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leading: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    Row(
        modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            onClick = onBack,
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.size(40.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.back), Modifier.size(21.dp), tint = Agora.colors.heading)
            }
        }
        if (leading != null) Box(Modifier.padding(start = 12.dp)) { leading() }
        Column(Modifier.weight(1f).padding(start = if (leading != null) 10.dp else 14.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = Agora.colors.heading, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
        actions()
    }
}

/** Round icon button with surface background (PWA `.btn-icon`). */
@Composable
fun RoundIconButton(icon: ImageVector, contentDescription: String?, onClick: () -> Unit, tint: Color = Agora.colors.heading) {
    IconButton(onClick = onClick) { Icon(icon, contentDescription, tint = tint) }
}

data class BottomNavItem(val label: String, val icon: ImageVector, val selectedIcon: ImageVector, val badge: Int = 0)

/** Bottom navigation of the PWA: white bar, emerald active item without indicator pill. */
@Composable
fun AgoraBottomBar(items: List<BottomNavItem>, selected: Int, onSelect: (Int) -> Unit) {
    // Faint upward shadow like the PWA (`box-shadow: 0 -4px 16px rgba(0,0,0,.06)`)
    Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.softShadow(Color.Black.copy(alpha = 0.07f), blur = 16.dp, shape = RectangleShape, offsetY = (-4).dp)) {
        Column {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(Modifier.fillMaxWidth().navigationBarsPadding().height(60.dp), verticalAlignment = Alignment.CenterVertically) {
                items.forEachIndexed { i, item ->
                    val active = i == selected
                    val color by animateColorAsState(if (active) MaterialTheme.colorScheme.secondary else Agora.colors.navInactive, label = "nav")
                    val interaction = remember { MutableInteractionSource() }
                    val pressed by interaction.collectIsPressedAsState()
                    Column(
                        Modifier
                            .weight(1f)
                            .height(60.dp)
                            .clickable(interaction, null, role = Role.Tab) { onSelect(i) }
                            .scale(if (pressed) 0.94f else 1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        BadgedBox(badge = {
                            if (item.badge > 0) Badge(containerColor = Agora.colors.danger, contentColor = Color.White) { Text("${item.badge}") }
                        }) {
                            Icon(if (active) item.selectedIcon else item.icon, null, Modifier.size(24.dp), tint = color)
                        }
                        Spacer(Modifier.height(3.dp))
                        Text(
                            item.label, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp, fontWeight = if (active) FontWeight.Bold else FontWeight.Medium)
                        )
                    }
                }
            }
        }
    }
}

/** Dark rounded toast like the PWA's `.toast`. */
@Composable
fun AgoraSnackbar(data: SnackbarData) = Snackbar(
    data,
    shape = RoundedCornerShape(16.dp),
    containerColor = Color(0xFF1E293B),
    contentColor = Color.White,
    actionColor = Color(0xFF22D3EE)
)

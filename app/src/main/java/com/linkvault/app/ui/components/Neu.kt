package com.linkvault.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.linkvault.app.ui.theme.LocalNeu
import com.linkvault.app.ui.theme.Neon
import com.linkvault.app.ui.theme.Radius

/* ------------------------------------------------------------------------------------------
 * Soft-UI ("neumorphic") building blocks.
 *
 * Every surface has the same colour as the screen; depth comes only from a light shadow on the
 * top-left and a dark shadow on the bottom-right. Pressed controls flip to an inset look.
 * ------------------------------------------------------------------------------------------ */

private fun Outline.toPath(): Path = Path().apply {
    when (val o = this@toPath) {
        is Outline.Rectangle -> addRect(o.rect)
        is Outline.Rounded -> addRoundRect(o.roundRect)
        is Outline.Generic -> addPath(o.path)
    }
}

private fun DrawScope.shadowPath(path: Path, color: Color, blur: Float, dx: Float, dy: Float) {
    if (blur <= 0f) return
    drawIntoCanvas { canvas ->
        val paint = android.graphics.Paint().apply {
            isAntiAlias = true
            this.color = android.graphics.Color.TRANSPARENT
            setShadowLayer(blur, dx, dy, color.toArgb())
        }
        canvas.nativeCanvas.drawPath(path.asAndroidPath(), paint)
    }
}

/** Raised surface: light shadow top-left, dark shadow bottom-right, then the filled shape on top. */
@Composable
fun Modifier.neuRaised(
    shape: Shape = Radius.card,
    elevation: Dp = 6.dp,
    fill: Color = MaterialTheme.colorScheme.surface
): Modifier {
    val p = LocalNeu.current
    return this.drawBehind {
        val path = shape.createOutline(size, layoutDirection, this).toPath()
        val blur = elevation.toPx() * 1.6f
        val off = elevation.toPx()
        shadowPath(path, p.shadow, blur, off, off)
        shadowPath(path, p.highlight, blur, -off, -off)
        drawPath(
            path,
            Brush.linearGradient(
                listOf(lerp(fill, p.highlight, if (p.dark) 0.10f else 0.35f), lerp(fill, p.shadowSolid, if (p.dark) 0.18f else 0.07f)),
                start = androidx.compose.ui.geometry.Offset.Zero,
                end = androidx.compose.ui.geometry.Offset(size.width, size.height)
            )
        )
    }
}

/** Sunken surface (text fields, switch tracks, pressed buttons). */
@Composable
fun Modifier.neuInset(
    shape: Shape = Radius.card,
    depth: Dp = 4.dp,
    fill: Color = MaterialTheme.colorScheme.surface
): Modifier {
    val p = LocalNeu.current
    return this.drawBehind {
        val path = shape.createOutline(size, layoutDirection, this).toPath()
        val blur = depth.toPx() * 1.6f
        val off = depth.toPx()
        drawPath(path, lerp(fill, p.shadowSolid, if (p.dark) 0.20f else 0.07f))
        val ring = Path().apply {
            fillType = PathFillType.EvenOdd
            addRect(Rect(-size.width, -size.height, size.width * 2, size.height * 2))
            addPath(path)
        }
        clipPath(path) {
            shadowPath(ring, p.shadow, blur, off, off)
            shadowPath(ring, p.highlight, blur, -off, -off)
        }
    }
}

/** A raised card. Use for grouped content. */
@Composable
fun NeuCard(
    modifier: Modifier = Modifier,
    shape: Shape = Radius.card,
    elevation: Dp = 6.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier
            .neuRaised(shape, elevation)
            .clip(shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        content = content
    )
}

/** Shared pressable surface: raised at rest, sunken while pressed. */
@Composable
private fun NeuPressable(
    onClick: () -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    shape: Shape,
    fill: Color,
    elevation: Dp,
    content: @Composable BoxScope.() -> Unit
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val depth by animateDpAsState(if (pressed) 0.dp else elevation, spring(stiffness = 700f), label = "neu-depth")
    Box(
        modifier
            .then(if (pressed) Modifier.neuInset(shape, 3.dp, fill) else Modifier.neuRaised(shape, depth.coerceAtLeast(2.dp), fill))
            .clip(shape)
            .clickable(interactionSource = source, indication = null, enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
        content = content
    )
}

private fun defaultButtonShape(): Shape = RoundedCornerShape(50)

/** Primary button: accent fill, white label. Same call shape as material3's Button. */
@Composable
fun NeuButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = defaultButtonShape(),
    colors: ButtonColors? = null,
    content: @Composable RowScope.() -> Unit
) {
    val container = colors?.containerColor ?: MaterialTheme.colorScheme.primary
    val contentColor = colors?.contentColor ?: Color.White
    val fill = if (enabled) container else MaterialTheme.colorScheme.surfaceVariant
    val fg = if (enabled) contentColor else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
    NeuPressable(onClick, modifier.defaultMinSize(minHeight = 44.dp), enabled, shape, fill, 6.dp) {
        CompositionLocalProvider(LocalContentColor provides fg, LocalTextStyle provides MaterialTheme.typography.labelLarge) {
            Row(Modifier.padding(horizontal = 22.dp, vertical = 8.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                content()
            }
        }
    }
}

/** Secondary button: same colour as the screen, accent label. Replaces FilledTonalButton. */
@Composable
fun NeuTonalButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = defaultButtonShape(),
    colors: ButtonColors? = null,
    content: @Composable RowScope.() -> Unit
) {
    val fg = if (!enabled) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
    else colors?.contentColor ?: MaterialTheme.colorScheme.onSurface
    NeuPressable(onClick, modifier.defaultMinSize(minHeight = 44.dp), enabled, shape, MaterialTheme.colorScheme.surface, 6.dp) {
        CompositionLocalProvider(LocalContentColor provides fg, LocalTextStyle provides MaterialTheme.typography.labelLarge) {
            Row(Modifier.padding(horizontal = 22.dp, vertical = 8.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                content()
            }
        }
    }
}

/** Replaces OutlinedButton; a raised button whose label can be tinted (e.g. red for Delete). */
@Composable
fun NeuOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = defaultButtonShape(),
    colors: ButtonColors? = null,
    content: @Composable RowScope.() -> Unit
) = NeuTonalButton(onClick, modifier, enabled, shape, colors, content)

/** Round raised icon button. Same call shape as material3's IconButton. */
@Composable
fun NeuIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit
) {
    NeuPressable(onClick, modifier.defaultMinSize(minWidth = 40.dp, minHeight = 40.dp), enabled, CircleShape, MaterialTheme.colorScheme.surface, 4.dp) {
        val fg = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
        CompositionLocalProvider(LocalContentColor provides fg) { content() }
    }
}

/** Floating action button in the soft style: accent, rounded square, white icon. */
@Composable
fun NeuFab(onClick: () -> Unit, contentDescription: String, content: @Composable () -> Unit) {
    NeuPressable(
        onClick, Modifier.size(58.dp).semantics { this.contentDescription = contentDescription },
        true, RoundedCornerShape(20.dp), MaterialTheme.colorScheme.primary, 7.dp
    ) { CompositionLocalProvider(LocalContentColor provides Color.White) { content() } }
}

/** Soft toggle: sunken track, raised thumb. Pass `onCheckedChange = null` when a parent row handles the tap. */
@Composable
fun NeuSwitch(checked: Boolean, onCheckedChange: ((Boolean) -> Unit)?, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val trackShape = RoundedCornerShape(50)
    val thumbX by animateDpAsState(if (checked) 24.dp else 0.dp, spring(dampingRatio = 0.75f, stiffness = 500f), label = "neu-thumb")
    val track by animateColorAsState(if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface, label = "neu-track")
    val base = modifier
        .size(width = 56.dp, height = 32.dp)
        .neuInset(trackShape, 3.dp, track)
        .then(
            if (onCheckedChange != null) Modifier.clickable(
                interactionSource = remember { MutableInteractionSource() }, indication = null, enabled = enabled, role = Role.Switch
            ) { onCheckedChange(!checked) } else Modifier
        )
    Box(base.alpha(if (enabled) 1f else 0.5f), contentAlignment = Alignment.CenterStart) {
        Box(
            Modifier.padding(start = 4.dp).offset(x = thumbX).size(24.dp)
                .neuRaised(CircleShape, 3.dp, MaterialTheme.colorScheme.surface)
        )
    }
}

/** Soft checkbox: raised square, accent tick when checked. */
@Composable
fun NeuCheckbox(checked: Boolean, onCheckedChange: ((Boolean) -> Unit)?, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier
            .size(28.dp)
            .then(if (checked) Modifier.neuInset(shape, 2.dp) else Modifier.neuRaised(shape, 3.dp))
            .clip(shape)
            .then(
                if (onCheckedChange != null) Modifier.clickable(enabled = enabled, role = Role.Checkbox) { onCheckedChange(!checked) } else Modifier
            ),
        contentAlignment = Alignment.Center
    ) {
        if (checked) Icon(Icons.Filled.Check, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
    }
}

/** Soft chip: raised when idle, accent when selected. */
@Composable
fun NeuChip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, fixedHeight: Boolean = false) {
    val shape = RoundedCornerShape(50)
    val fill = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
    Box(
        modifier
            .then(if (fixedHeight) Modifier.height(38.dp) else Modifier.defaultMinSize(minHeight = 38.dp))
            .then(if (selected) Modifier.neuRaised(shape, 3.dp, fill) else Modifier.neuRaised(shape, 4.dp, fill))
            .clip(shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label, maxLines = 1, style = MaterialTheme.typography.labelLarge,
            color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
        )
    }
}

/** Round badge holding an icon: used for card headers and the tools grid. */
@Composable
fun NeuBadge(color: Color, modifier: Modifier = Modifier, size: Dp = 40.dp, content: @Composable () -> Unit) {
    Box(
        modifier.size(size).clip(CircleShape).background(color.copy(alpha = 0.18f)),
        contentAlignment = Alignment.Center
    ) { CompositionLocalProvider(LocalContentColor provides color) { content() } }
}

/** Soft text field for the new tool screens. */
@Composable
fun NeuTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    keyboardOptions: androidx.compose.foundation.text.KeyboardOptions = androidx.compose.foundation.text.KeyboardOptions.Default,
    visualTransformation: androidx.compose.ui.text.input.VisualTransformation = androidx.compose.ui.text.input.VisualTransformation.None,
    enabled: Boolean = true
) {
    Box(
        modifier.fillMaxWidth().height(50.dp).neuInset(Radius.small, 3.dp).padding(horizontal = 16.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        if (value.isEmpty()) Text(placeholder, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
        androidx.compose.foundation.text.BasicTextField(
            value = value, onValueChange = onValueChange, singleLine = true, enabled = enabled,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
            cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = keyboardOptions, visualTransformation = visualTransformation,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/** Accent used for icons on the soft palette. */
val NeuAccent: Color get() = Neon.lime
